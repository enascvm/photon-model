/*
 * Copyright (c) 2015-2016 VMware, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.vmware.photon.controller.model.adapters.vsphere;

import static com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.codehaus.jackson.node.ObjectNode;

import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.adapters.vsphere.tagging.TagCache;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.Connection;
import com.vmware.photon.controller.model.adapters.vsphere.vapi.RpcException;
import com.vmware.photon.controller.model.adapters.vsphere.vapi.TaggingClient;
import com.vmware.photon.controller.model.adapters.vsphere.vapi.VapiConnection;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.StorageDescriptionService;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;
import com.vmware.photon.controller.model.resources.TagFactoryService;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.ObjectContent;
import com.vmware.vim25.PropertyFilterSpec;
import com.vmware.vim25.UpdateSet;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Handles enumeration for vsphere endpoints. It supports up to
 * {@link #MAX_CONCURRENT_ENUM_PROCESSES} concurrent long-running enumeration processes. Attempts to
 * start more processes than that will result in error.
 */
public class VSphereAdapterResourceEnumerationService extends StatelessService {
    public static final String SELF_LINK = VSphereUriPaths.ENUMERATION_SERVICE;

    private static final int MAX_CONCURRENT_ENUM_PROCESSES = 10;
    private static final String FAKE_SUBNET_CIDR = "0.0.0.0/0";

    /**
     * Stores currently running enumeration processes.
     */
    private final ConcurrentMap<String, ComputeEnumerateResourceRequest> startedEnumProcessesByHost = new ConcurrentHashMap<>();

    /**
     * Bounded theadpool executing the currently running enumeration processes.
     */
    private final ExecutorService enumerationThreadPool;

    private final TagCache tagCache;

    public VSphereAdapterResourceEnumerationService() {
        this.enumerationThreadPool = new ThreadPoolExecutor(MAX_CONCURRENT_ENUM_PROCESSES,
                MAX_CONCURRENT_ENUM_PROCESSES,
                0L, TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(),
                new AbortPolicy());

        this.tagCache = new TagCache();
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }

        ComputeEnumerateResourceRequest request = op.getBody(ComputeEnumerateResourceRequest.class);

        validate(request);

        op.setStatusCode(Operation.STATUS_CODE_CREATED);
        op.complete();

        TaskManager mgr = new TaskManager(this, request.taskReference);

        if (request.isMockRequest) {
            // just finish the mock request
            mgr.patchTask(TaskStage.FINISHED);
            return;
        }

        URI parentUri = ComputeStateWithDescription.buildUri(request.resourceReference);

        Operation.createGet(parentUri)
                .setCompletion(o -> {
                    thenWithParentState(request, o.getBody(ComputeStateWithDescription.class), mgr);
                }, mgr)
                .sendWith(this);
    }

    private void thenWithParentState(ComputeEnumerateResourceRequest request,
            ComputeStateWithDescription parent, TaskManager mgr) {

        if (request.enumerationAction == EnumerationAction.STOP) {
            endEnumerationProcess(parent, mgr);
            return;
        }

        VSphereIOThreadPool pool = VSphereIOThreadPoolAllocator.getPool(this);

        pool.submit(this, parent.adapterManagementReference,
                parent.description.authCredentialsLink,
                (connection, e) -> {
                    if (e != null) {
                        String msg = String.format("Cannot establish connection to %s",
                                parent.adapterManagementReference);
                        logInfo(msg);
                        mgr.patchTaskToFailure(msg, e);
                    } else {
                        if (request.enumerationAction == EnumerationAction.REFRESH) {
                            refreshResourcesOnce(request, connection, parent, mgr);
                        } else if (request.enumerationAction == EnumerationAction.START) {
                            startEnumerationProcess(
                                    connection.createUnmanagedCopy(),
                                    parent,
                                    request,
                                    mgr);
                        }
                    }
                });
    }

    private void endEnumerationProcess(ComputeStateWithDescription parent, TaskManager mgr) {
        // just remove from map, enumeration process checks if it should continue at every step
        ComputeEnumerateResourceRequest old = this.startedEnumProcessesByHost
                .remove(parent.documentSelfLink);

        if (old == null) {
            logInfo("No running enumeration process for %s was found", parent.documentSelfLink);
        }

        mgr.patchTask(TaskStage.FINISHED);
    }

    private void startEnumerationProcess(
            Connection connection,
            ComputeStateWithDescription parent,
            ComputeEnumerateResourceRequest request, TaskManager mgr) {

        ComputeEnumerateResourceRequest old = this.startedEnumProcessesByHost
                .putIfAbsent(parent.documentSelfLink, request);

        if (old != null) {
            logInfo("Enumeration process for %s already started, not starting a new one",
                    parent.documentSelfLink);
            return;
        }

        EnumerationClient client;
        try {
            client = new EnumerationClient(connection, parent);
        } catch (Exception e) {
            String msg = String
                    .format("Error connecting to %s while starting enumeration process for %s",
                            parent.adapterManagementReference,
                            parent.documentSelfLink);
            logInfo(msg);
            mgr.patchTaskToFailure(msg, e);
            return;
        }

        try {
            OperationContext opContext = OperationContext.getOperationContext();
            this.enumerationThreadPool.execute(() -> {
                OperationContext.restoreOperationContext(opContext);
                try {
                    startEnumerationProcess(parent, client);
                } catch (Exception e) {
                    String msg = String.format("Error during enumeration process %s, aborting",
                            parent.documentSelfLink);
                    log(Level.FINE, msg);
                    mgr.patchTaskToFailure(msg, e);
                }
            });
        } catch (RejectedExecutionException e) {
            String msg = String
                    .format("Max number of resource enumeration processes reached: will not start one for %s",
                            parent.documentSelfLink);
            logInfo(msg);
            mgr.patchTaskToFailure(msg, e);
        }
    }

    /**
     * This method executes in a thread managed by {@link #enumerationThreadPool}.
     *
     * @param client
     * @throws Exception
     */
    private void startEnumerationProcess(ComputeStateWithDescription parent,
            EnumerationClient client)
            throws Exception {
        PropertyFilterSpec spec = client.createFullFilterSpec();

        try {
            for (UpdateSet updateSet : client.pollForUpdates(spec)) {
                processUpdates(updateSet);
                if (!this.startedEnumProcessesByHost.containsKey(parent.documentSelfLink)) {
                    break;
                }
            }
        } catch (Exception e) {
            // destroy connection and let global error handler process it further
            client.close();
            throw e;
        }
    }

    /**
     * This method executes in a thread managed by {@link VSphereIOThreadPoolAllocator}
     *
     * @param request
     * @param connection
     * @param parent
     * @param mgr
     */
    private void refreshResourcesOnce(
            ComputeEnumerateResourceRequest request,
            Connection connection,
            ComputeStateWithDescription parent,
            TaskManager mgr) {

        EnumerationClient client;
        try {
            client = new EnumerationClient(connection, parent);
        } catch (Exception e) {
            mgr.patchTaskToFailure(e);
            return;
        }

        PropertyFilterSpec spec = client.createFullFilterSpec();

        try {
            for (List<ObjectContent> page : client.retrieveObjects(spec)) {
                processFoundObjects(request, page, parent, connection);
            }
        } catch (Exception e) {
            String msg = "Error processing PropertyCollector results";
            logWarning(msg);
            mgr.patchTaskToFailure(msg, e);
        }

        mgr.patchTask(TaskStage.FINISHED);
    }

    /**
     * @see #processFoundCluster(ComputeEnumerateResourceRequest, ComputeResourceOverlay, VapiConnection)
     * @param request
     * @param objects
     * @param parent
     * @param connection
     */
    private void processFoundObjects(ComputeEnumerateResourceRequest request,
            List<ObjectContent> objects, ComputeStateWithDescription parent,
            Connection connection) {

        VapiConnection endpoint = new VapiConnection(getVapiUri(connection.getURI()));
        endpoint.setUsername(connection.getUsername());
        endpoint.setPassword(connection.getPassword());

        // TODO managed truststore globally
        endpoint.setClient(VapiConnection.newUnsecureClient());

        try {
            endpoint.login();
        } catch (IOException | RpcException e) {
            logInfo("Cannot login into vAPI endpoint");
        }

        for (ObjectContent cont : objects) {
            if (VimUtils.isVirtualMachine(cont.getObj())) {
                VmOverlay vm = new VmOverlay(cont);
                processFoundVm(request, vm, parent, endpoint, parent.tenantLinks);
            } else if (VimUtils.isResourcePool(cont.getObj())) {
                logInfo("Ignoring ResourcePool %s",
                        VimUtils.convertMoRefToString(cont.getObj()));
            } else if (VimUtils.isHost(cont.getObj())) {
                HostSystemOverlay hs = new HostSystemOverlay(cont);
                processFoundHostSystem(request, hs, endpoint, parent.tenantLinks);
            } else if (VimUtils.isComputeResource(cont.getObj())) {
                ComputeResourceOverlay cr = new ComputeResourceOverlay(cont);
                processFoundCluster(request, cr, endpoint, parent.tenantLinks);
            } else if (VimUtils.isDatastore(cont.getObj())) {
                DatastoreOverlay ds = new DatastoreOverlay(cont);
                processFoundDatastore(request, ds, parent.description.regionId, parent.tenantLinks);
            } else if (VimUtils.isNetwork(cont.getObj())) {
                NetworkOverlay net = new NetworkOverlay(cont);
                processFoundNetwork(request, net, parent, parent.tenantLinks);
            }
        }
    }

    private URI getVapiUri(URI uri) {
        // TODO use lookup service
        return URI.create(uri.toString().replace("/sdk", "/api"));
    }

    private void processFoundNetwork(ComputeEnumerateResourceRequest request, NetworkOverlay net,
            ComputeStateWithDescription parent, List<String> tenantLinks) {
        QueryTask task = queryForNetwork(request.adapterManagementReference, net.getName(),
                parent.description.regionId);
        task.tenantLinks = tenantLinks;

        withTaskResults(task, result -> {
            if (result.documentLinks.isEmpty()) {
                createNewNetwork(request, net, parent, tenantLinks);
            } else {
                ComputeState oldDocument = Utils
                        .fromJson(result.documents.values().iterator().next(), ComputeState.class);
                updateNetwork(oldDocument, request, net, parent, tenantLinks);
            }
        });
    }

    private void updateNetwork(ComputeState oldDocument, ComputeEnumerateResourceRequest request,
            NetworkOverlay net, ComputeStateWithDescription parent, List<String> tenantLinks) {
        NetworkState state = makeNetworkStateFromResults(request, net, parent);
        state.documentSelfLink = oldDocument.documentSelfLink;
        if (oldDocument.tenantLinks == null) {
            state.tenantLinks = tenantLinks;
        }

        logInfo("Syncing Network %s", net.getName());
        Operation.createPatch(UriUtils.buildUri(getHost(), oldDocument.documentSelfLink))
                .setBody(state)
                .sendWith(this);
    }

    private void createNewNetwork(ComputeEnumerateResourceRequest request, NetworkOverlay net,
            ComputeStateWithDescription parent, List<String> tenantLinks) {
        NetworkState state = makeNetworkStateFromResults(request, net, parent);
        state.tenantLinks = tenantLinks;
        Operation.createPost(this, NetworkService.FACTORY_LINK)
                .setBody(state)
                .sendWith(this);

        logInfo("Found new Network %s", net.getName());
    }

    private NetworkState makeNetworkStateFromResults(ComputeEnumerateResourceRequest request,
            NetworkOverlay net, ComputeStateWithDescription parent) {
        NetworkState state = new NetworkState();

        state.id = state.name = net.getName();
        state.subnetCIDR = FAKE_SUBNET_CIDR;
        state.regionId = parent.description.regionId;
        state.resourcePoolLink = request.resourcePoolLink;
        state.instanceAdapterReference = parent.description.instanceAdapterReference;
        state.authCredentialsLink = parent.description.authCredentialsLink;
        state.adapterManagementReference = request.adapterManagementReference;
        CustomProperties.of(state)
                .put(CustomProperties.MOREF, net.getId())
                .put(CustomProperties.TYPE, net.getId().getType());

        return state;
    }

    private QueryTask queryForNetwork(URI adapterManagementReference, String name,
            String regionId) {
        QuerySpecification qs = new QuerySpecification();
        qs.query.addBooleanClause(
                Query.Builder.create()
                        .addFieldClause(NetworkState.FIELD_NAME_ADAPTER_MANAGEMENT_REFERENCE,
                                adapterManagementReference.toString())
                        .build());

        qs.query.addBooleanClause(
                Query.Builder.create()
                        .addFieldClause(NetworkState.FIELD_NAME_NAME,
                                name)
                        .build());

        qs.query.addBooleanClause(Query.Builder.create()
                .addFieldClause(NetworkState.FIELD_NAME_REGION_ID,
                        regionId)
                .build());

        return QueryTask
                .create(qs)
                .setDirect(true);
    }

    private void processFoundDatastore(ComputeEnumerateResourceRequest request,
            DatastoreOverlay ds, String regionId, List<String> tenantLinks) {
        QueryTask task = queryForStorage(request.adapterManagementReference, ds.getName(),
                regionId);
        task.tenantLinks = tenantLinks;

        withTaskResults(task, result -> {
            if (result.documentLinks.isEmpty()) {
                createNewStorageDescription(request, ds, regionId, tenantLinks);
            } else {
                StorageDescription oldDocument = Utils
                        .fromJson(result.documents.values().iterator().next(),
                                StorageDescription.class);
                updateStorageDescription(oldDocument, request, ds, regionId, tenantLinks);
            }
        });
    }

    private void updateStorageDescription(StorageDescription oldDocument,
            ComputeEnumerateResourceRequest request, DatastoreOverlay ds,
            String regionId, List<String> tenantLinks) {
        StorageDescription desc = makeStorageFromResults(request, ds, regionId);
        desc.documentSelfLink = oldDocument.documentSelfLink;
        if (oldDocument.tenantLinks == null) {
            desc.tenantLinks = tenantLinks;
        }
        logInfo("Syncing Storage %s", ds.getName());
        Operation.createPatch(UriUtils.buildUri(getHost(), desc.documentSelfLink))
                .setBody(desc)
                .sendWith(this);
    }

    private void createNewStorageDescription(ComputeEnumerateResourceRequest request,
            DatastoreOverlay ds, String regionId, List<String> tenantLinks) {
        StorageDescription desc = makeStorageFromResults(request, ds, regionId);
        desc.tenantLinks = tenantLinks;
        logInfo("Found new Datastore %s", ds.getName());

        Operation.createPost(this, StorageDescriptionService.FACTORY_LINK)
                .setBody(desc)
                .sendWith(this);
    }

    private StorageDescription makeStorageFromResults(ComputeEnumerateResourceRequest request,
            DatastoreOverlay ds, String regionId) {
        StorageDescription res = new StorageDescription();
        res.id = res.name = ds.getName();
        res.type = ds.getType();
        res.resourcePoolLink = request.resourcePoolLink;
        res.adapterManagementReference = request.adapterManagementReference;
        res.capacityBytes = ds.getCapacityBytes();
        res.regionId = regionId;
        CustomProperties.of(res)
                .put(CustomProperties.MOREF, ds.getId());

        return res;
    }

    private QueryTask queryForStorage(URI adapterManagementReference, String name,
            String regionId) {
        QuerySpecification qs = new QuerySpecification();
        qs.query.addBooleanClause(
                Query.Builder.create().addFieldClause(StorageDescription.FIELD_NAME_NAME, name)
                        .build());

        qs.query.addBooleanClause(Query.Builder.create()
                .addFieldClause(StorageDescription.FIELD_NAME_ADAPTER_REFERENCE,
                        adapterManagementReference.toString())
                .build());

        qs.query.addBooleanClause(Query.Builder.create()
                .addFieldClause(StorageDescription.FIELD_NAME_REGION_ID,
                        regionId)
                .build());
        return QueryTask
                .create(qs)
                .setDirect(true);
    }

    /**
     * Either creates a new Compute or update an already existing one. Existence is checked by
     * querying for a compute with id equals to moref value of a cluster whose parent is the Compute
     * from the request.
     *
     * @param request
     * @param cr
     * @param endpoint
     * @param tenantLinks
     */
    private void processFoundCluster(ComputeEnumerateResourceRequest request,
            ComputeResourceOverlay cr, VapiConnection endpoint, List<String> tenantLinks) {
        QueryTask task = queryForCluster(request.resourceLink(),
                cr.getId().getValue());
        task.tenantLinks = tenantLinks;

        withTaskResults(task, result -> {
            if (result.documentLinks.isEmpty()) {
                createNewCluster(request, cr, endpoint, tenantLinks);
            } else {
                ComputeState oldDocument = Utils
                        .fromJson(result.documents.values().iterator().next(), ComputeState.class);
                updateCluster(oldDocument, request, cr, endpoint, tenantLinks);
            }
        });
    }

    private void updateCluster(ComputeState oldDocument,
            ComputeEnumerateResourceRequest request, ComputeResourceOverlay cr,
            VapiConnection endpoint, List<String> tenantLinks) {
        ComputeState state = makeClusterFromResults(request, cr);
        state.documentSelfLink = oldDocument.documentSelfLink;
        if (oldDocument.tenantLinks == null) {
            state.tenantLinks = tenantLinks;
        }
        populateTags(cr, endpoint, state, tenantLinks);

        logInfo("Syncing ComputeResource %s", oldDocument.documentSelfLink);
        Operation.createPatch(UriUtils.buildUri(getHost(), oldDocument.documentSelfLink))
                .setBody(state)
                .sendWith(this);

        ComputeDescription desc = makeDescriptionForCluster(request, cr);
        desc.documentSelfLink = oldDocument.descriptionLink;
        Operation.createPatch(UriUtils.buildUri(getHost(), desc.documentSelfLink))
                .setBody(desc)
                .sendWith(this);
    }

    private ComputeDescription makeDescriptionForCluster(
            ComputeEnumerateResourceRequest request,
            ComputeResourceOverlay cr) {
        ComputeDescription res = new ComputeDescription();
        res.name = cr.getName();
        res.documentSelfLink = UriUtils
                .buildUriPath(ComputeDescriptionService.FACTORY_LINK, UUID.randomUUID().toString());
        res.cpuCount = cr.getTotalCpuCores();
        if (cr.getTotalCpuCores() != 0) {
            res.cpuMhzPerCore = cr.getTotalCpuMhz() / cr.getTotalCpuCores();
        }
        res.totalMemoryBytes = cr.getTotalMemoryBytes();
        res.supportedChildren = Collections.singletonList(ComputeType.VM_GUEST.name());

        res.instanceAdapterReference = UriUtils.buildUri(getHost(),
                VSphereUriPaths.INSTANCE_SERVICE);
        res.enumerationAdapterReference = UriUtils.buildUri(getHost(),
                VSphereUriPaths.ENUMERATION_SERVICE);
        res.statsAdapterReference = UriUtils.buildUri(getHost(), VSphereUriPaths.STATS_SERVICE);

        return res;
    }

    private void createNewCluster(ComputeEnumerateResourceRequest request,
            ComputeResourceOverlay cr, VapiConnection endpoint, List<String> tenantLinks) {
        ComputeDescription desc = makeDescriptionForCluster(request, cr);
        desc.tenantLinks = tenantLinks;
        Operation.createPost(this, ComputeDescriptionService.FACTORY_LINK)
                .setBody(desc)
                .sendWith(this);

        ComputeState state = makeClusterFromResults(request, cr);
        state.tenantLinks = tenantLinks;
        state.descriptionLink = desc.documentSelfLink;
        populateTags(cr, endpoint, state, tenantLinks);

        logInfo("Found new ComputeResource %s", cr.getId().getValue());
        Operation.createPost(this, ComputeService.FACTORY_LINK)
                .setBody(state)
                .sendWith(this);
    }

    /**
     * After the tags for the ref are retrieved from the endpoint they are posted to the tag service
     * and the selfLinks are collected ready to be used in a {@link ComputeState#tagLinks}.
     *
     * @param endpoint
     * @param ref
     * @param tenantLinks
     * @return
     */
    private Set<String> retrieveTagLinksAndCreateTagsAsync(VapiConnection endpoint,
            ManagedObjectReference ref, List<String> tenantLinks) {
        List<TagState> tags = null;
        try {
            tags = retrieveAttachedTags(endpoint, ref, tenantLinks);
        } catch (IOException | RpcException e) {

        }

        if (tags == null || tags.isEmpty()) {
            return new HashSet<>();
        }

        OperationJoin.create(
                tags.stream()
                        .map(s -> Operation
                                .createPost(UriUtils.buildFactoryUri(getHost(), TagService.class))
                                .setReferer(getUri())
                                .setBody(s)))
                .sendWith(this);

        return tags.stream().map(s -> s.documentSelfLink).collect(Collectors.toSet());
    }

    /**
     * Retreives all tags for a MoRef from an endpoint.
     *
     * @param endpoint
     * @param ref
     * @param tenantLinks
     * @return empty list if no tags found, never null
     */
    private List<TagState> retrieveAttachedTags(VapiConnection endpoint,
            ManagedObjectReference ref, List<String> tenantLinks) throws IOException, RpcException {
        TaggingClient taggingClient = endpoint.newTaggingClient();
        List<String> tagIds = taggingClient.getAttachedTags(ref);

        List<TagState> res = new ArrayList<>();
        for (String id : tagIds) {
            TagState state = this.tagCache.get(id, newTagRetriever(taggingClient));
            if (state != null) {
                if (state.tenantLinks == null) {
                    state.tenantLinks = tenantLinks;
                }
                res.add(state);
            }
        }

        return res;
    }

    /**
     * Builds a function to retrieve tags given and endpoint.
     *
     * @param client
     * @return
     */
    private Function<String, TagState> newTagRetriever(TaggingClient client) {
        return (tagId) -> {
            try {
                ObjectNode tagModel = client.getTagModel(tagId);
                if (tagModel == null) {
                    return null;
                }

                TagState res = new TagState();
                res.value = tagModel.get("name").asText();
                res.key = client.getCategoryName(tagModel.get("category_id").asText());

                res.documentSelfLink = TagFactoryService.generateSelfLink(res);
                return res;
            } catch (IOException | RpcException e) {
                return null;
            }
        };
    }

    private ComputeState makeClusterFromResults(ComputeEnumerateResourceRequest request,
            ComputeResourceOverlay cr) {
        ComputeState state = new ComputeState();
        state.id = cr.getId().getValue();
        state.adapterManagementReference = request.adapterManagementReference;
        state.parentLink = request.resourceLink();
        state.resourcePoolLink = request.resourcePoolLink;
        state.name = cr.getName();
        state.powerState = PowerState.ON;
        CustomProperties.of(state)
                .put(CustomProperties.MOREF, cr.getId())
                .put(CustomProperties.TYPE, cr.getId().getType());
        return state;
    }

    private QueryTask queryForCluster(String parentComputeLink, String moRefId) {
        QuerySpecification qs = new QuerySpecification();
        qs.query.addBooleanClause(
                Query.Builder.create().addFieldClause(ComputeState.FIELD_NAME_ID, moRefId)
                        .build());

        qs.query.addBooleanClause(Query.Builder.create()
                .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK, parentComputeLink).build());

        return QueryTask
                .create(qs)
                .setDirect(true);
    }

    /**
     * @see #processFoundCluster(ComputeEnumerateResourceRequest, ComputeResourceOverlay,
     *      VapiConnection)
     * @param request
     * @param hs
     * @param tenantLinks
     */
    private void processFoundHostSystem(ComputeEnumerateResourceRequest request,
            HostSystemOverlay hs, VapiConnection endpoint, List<String> tenantLinks) {
        QueryTask task = queryForHostSystem(request.resourceLink(),
                hs.getHardwareUuid());
        task.tenantLinks = tenantLinks;
        withTaskResults(task, result -> {
            if (result.documentLinks.isEmpty()) {
                createNewHostSystem(request, hs, endpoint, tenantLinks);
            } else {
                ComputeState oldDocument = Utils
                        .fromJson(result.documents.values().iterator().next(), ComputeState.class);
                updateHostSystem(oldDocument, request, hs, endpoint, tenantLinks);
            }
        });
    }

    private void updateHostSystem(ComputeState oldDocument, ComputeEnumerateResourceRequest request,
            HostSystemOverlay hs, VapiConnection endpoint, List<String> tenantLinks) {
        ComputeState state = makeHostSystemFromResults(request, hs);
        state.documentSelfLink = oldDocument.documentSelfLink;
        if (oldDocument.tenantLinks == null) {
            state.tenantLinks = tenantLinks;
        }
        populateTags(hs, endpoint, state, tenantLinks);

        logInfo("Syncing HostSystem %s", oldDocument.documentSelfLink);
        Operation.createPatch(UriUtils.buildUri(getHost(), state.documentSelfLink))
                .setBody(state)
                .sendWith(this);

        ComputeDescription desc = makeDescriptionForHost(request, hs);
        desc.documentSelfLink = oldDocument.descriptionLink;
        Operation.createPatch(UriUtils.buildUri(getHost(), desc.documentSelfLink))
                .setBody(desc)
                .sendWith(this);
    }

    private void createNewHostSystem(ComputeEnumerateResourceRequest request,
            HostSystemOverlay hs, VapiConnection endpoint, List<String> tenantLinks) {

        ComputeDescription desc = makeDescriptionForHost(request, hs);
        desc.tenantLinks = tenantLinks;
        Operation.createPost(this, ComputeDescriptionService.FACTORY_LINK)
                .setBody(desc)
                .sendWith(this);

        ComputeState state = makeHostSystemFromResults(request, hs);
        state.descriptionLink = desc.documentSelfLink;
        state.tenantLinks = tenantLinks;
        populateTags(hs, endpoint, state, tenantLinks);

        logInfo("Found new HostSystem %s", hs.getName());
        Operation.createPost(this, ComputeService.FACTORY_LINK)
                .setBody(state)
                .sendWith(this);
    }

    private void populateTags(AbstractOverlay obj, VapiConnection endpoint, ComputeState state,
            List<String> tenantLinks) {
        state.tagLinks = retrieveTagLinksAndCreateTagsAsync(endpoint, obj.getId(), tenantLinks);
    }

    private ComputeDescription makeDescriptionForHost(ComputeEnumerateResourceRequest request,
            HostSystemOverlay hs) {
        ComputeDescription res = new ComputeDescription();
        res.name = hs.getName();
        res.documentSelfLink = UriUtils
                .buildUriPath(ComputeDescriptionService.FACTORY_LINK, UUID.randomUUID().toString());
        res.cpuCount = hs.getCoreCount();
        res.cpuMhzPerCore = hs.getCpuMhz();
        res.totalMemoryBytes = hs.getTotalMemoryBytes();
        res.supportedChildren = Collections.singletonList(ComputeType.VM_GUEST.name());
        res.instanceAdapterReference = UriUtils.buildUri(getHost(),
                VSphereUriPaths.INSTANCE_SERVICE);
        res.enumerationAdapterReference = UriUtils.buildUri(getHost(),
                VSphereUriPaths.ENUMERATION_SERVICE);
        res.statsAdapterReference = UriUtils.buildUri(getHost(), VSphereUriPaths.STATS_SERVICE);

        return res;
    }

    private ComputeState makeHostSystemFromResults(ComputeEnumerateResourceRequest request,
            HostSystemOverlay hs) {
        ComputeState state = new ComputeState();
        state.id = hs.getHardwareUuid();
        state.adapterManagementReference = request.adapterManagementReference;
        state.parentLink = request.resourceLink();
        state.resourcePoolLink = request.resourcePoolLink;
        state.name = hs.getName();
        // TODO: retrieve host power state
        state.powerState = PowerState.ON;
        CustomProperties.of(state)
                .put(CustomProperties.MOREF, hs.getId())
                .put(CustomProperties.TYPE, hs.getId().getType());
        return state;
    }

    /**
     * @see #processFoundCluster(ComputeEnumerateResourceRequest, ComputeResourceOverlay,
     *      VapiConnection)
     * @param request
     * @param vm
     * @param parent
     * @param endpoint
     * @param tenantLinks
     */
    private void processFoundVm(ComputeEnumerateResourceRequest request, VmOverlay vm,
            ComputeStateWithDescription parent, VapiConnection endpoint, List<String> tenantLinks) {
        QueryTask task = queryForVm(request.resourceLink(), vm.getInstanceUuid());
        task.tenantLinks = tenantLinks;

        withTaskResults(task, result -> {
            if (result.documentLinks.isEmpty()) {
                createNewVm(request, vm, parent, endpoint, tenantLinks);
            } else {
                ComputeState oldDocument = Utils
                        .fromJson(result.documents.values().iterator().next(),
                                ComputeState.class);
                updateVm(oldDocument, request, vm, parent, endpoint, tenantLinks);
            }
        });
    }

    private void updateVm(ComputeState oldDocument, ComputeEnumerateResourceRequest request,
            VmOverlay vm, ComputeStateWithDescription parent, VapiConnection endpoint,
            List<String> tenantLinks) {
        ComputeState state = makeVmFromResults(request, vm);
        state.documentSelfLink = oldDocument.documentSelfLink;
        if (oldDocument.tenantLinks == null) {
            state.tenantLinks = tenantLinks;
        }
        populateTags(vm, endpoint, state, tenantLinks);

        logInfo("Syncing VM %s", state.documentSelfLink);
        Operation.createPatch(UriUtils.buildUri(getHost(), oldDocument.documentSelfLink))
                .setBody(state)
                .sendWith(this);
    }

    private void createNewVm(ComputeEnumerateResourceRequest request, VmOverlay vm,
            ComputeStateWithDescription parent, VapiConnection endpoint, List<String> tenantLinks) {
        ComputeDescription desc = makeDescriptionForVm(request, vm, parent);
        desc.tenantLinks = tenantLinks;
        Operation.createPost(this, ComputeDescriptionService.FACTORY_LINK)
                .setBody(desc)
                .sendWith(this);

        ComputeState state = makeVmFromResults(request, vm);
        state.descriptionLink = desc.documentSelfLink;
        state.tenantLinks = tenantLinks;
        populateTags(vm, endpoint, state, tenantLinks);

        logInfo("Found new VM %s", vm.getInstanceUuid());
        Operation.createPost(this, ComputeService.FACTORY_LINK)
                .setBody(state)
                .sendWith(this);
    }

    private ComputeDescription makeDescriptionForVm(ComputeEnumerateResourceRequest request,
            VmOverlay vm, ComputeStateWithDescription parent) {
        ComputeDescription res = new ComputeDescription();
        res.name = vm.getName();
        res.documentSelfLink = UriUtils
                .buildUriPath(ComputeDescriptionService.FACTORY_LINK, UUID.randomUUID().toString());
        res.instanceAdapterReference = UriUtils.buildUri(getHost(),
                VSphereUriPaths.INSTANCE_SERVICE);
        res.enumerationAdapterReference = UriUtils.buildUri(getHost(),
                VSphereUriPaths.ENUMERATION_SERVICE);
        res.statsAdapterReference = UriUtils.buildUri(getHost(), VSphereUriPaths.STATS_SERVICE);
        res.powerAdapterReference = UriUtils.buildUri(getHost(), VSphereUriPaths.POWER_SERVICE);

        res.regionId = parent.description.regionId;

        res.cpuCount = vm.getNumCpu();
        res.totalMemoryBytes = vm.getMemoryBytes();
        return res;
    }

    /**
     * Make a ComputeState from the request and a vm found in vsphere.
     *
     * @param request
     * @param vm
     * @return
     */
    private ComputeState makeVmFromResults(ComputeEnumerateResourceRequest request,
            VmOverlay vm) {
        ComputeState state = new ComputeState();
        state.adapterManagementReference = request.adapterManagementReference;
        state.parentLink = request.resourceLink();
        state.resourcePoolLink = request.resourcePoolLink;

        state.powerState = vm.getPowerState();
        state.primaryMAC = vm.getPrimaryMac();
        if (!vm.isTempalte()) {
            state.address = vm.getIpAddressOrHostName();
        }
        state.id = vm.getInstanceUuid();
        state.name = vm.getName();

        CustomProperties.of(state)
                .put(CustomProperties.MOREF, vm.getId())
                .put(CustomProperties.HOST, vm.getHost())
                .put(CustomProperties.TEMPLATE, vm.isTempalte())
                .put(CustomProperties.TYPE, VimNames.TYPE_VM);
        return state;
    }

    /**
     * Executes a direct query and invokes the provided handler with the results.
     *
     * @param task
     * @param handler
     */
    private void withTaskResults(QueryTask task, Consumer<ServiceDocumentQueryResult> handler) {
        task.querySpec.options = EnumSet.of(QueryOption.EXPAND_CONTENT);
        Operation.createPost(UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_QUERY_TASKS))
                .setBody(task)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("Error processing task %s", task.documentSelfLink);
                        return;
                    }

                    QueryTask body = o.getBody(QueryTask.class);
                    handler.accept(body.results);
                })
                .sendWith(this);
    }

    /**
     * Builds a query for finding a ComputeState by instanceUuid from vsphere and parent compute
     * link.
     *
     * @param parentComputeLink
     * @param instanceUuid
     * @return
     */
    private QueryTask queryForVm(String parentComputeLink, String instanceUuid) {
        QuerySpecification qs = new QuerySpecification();
        qs.query.addBooleanClause(
                Query.Builder.create().addFieldClause(ComputeState.FIELD_NAME_ID, instanceUuid)
                        .build());

        qs.query.addBooleanClause(Query.Builder.create()
                .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK, parentComputeLink).build());

        return QueryTask
                .create(qs)
                .setDirect(true);
    }

    /**
     * Builds a query for finding a HostSystems by their hardwareUuid.
     *
     * @param parentComputeLink
     * @param hardwareUuid
     * @return
     */
    private QueryTask queryForHostSystem(String parentComputeLink, String hardwareUuid) {
        QuerySpecification qs = new QuerySpecification();
        qs.query.addBooleanClause(
                Query.Builder.create().addFieldClause(ComputeState.FIELD_NAME_ID, hardwareUuid)
                        .build());

        qs.query.addBooleanClause(Query.Builder.create()
                .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK, parentComputeLink).build());

        // fetch the whole document to extract the description link
        qs.options = EnumSet.of(EXPAND_CONTENT);
        return QueryTask
                .create(qs)
                .setDirect(true);
    }

    private void processUpdates(UpdateSet updateSet) {
        // handle PC updates
        // https://jira-hzn.eng.vmware.com/browse/VCOM-17
    }

    private void validate(ComputeEnumerateResourceRequest request) {
        // assume all request are REFRESH requests
        if (request.enumerationAction == null) {
            request.enumerationAction = EnumerationAction.REFRESH;
        }
    }
}
