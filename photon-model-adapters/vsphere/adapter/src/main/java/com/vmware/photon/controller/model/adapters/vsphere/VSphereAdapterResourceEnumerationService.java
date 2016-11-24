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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
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
import com.vmware.vim25.VirtualDeviceBackingInfo;
import com.vmware.vim25.VirtualEthernetCard;
import com.vmware.vim25.VirtualEthernetCardNetworkBackingInfo;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
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

        VapiConnection vapiConnection = new VapiConnection(getVapiUri(connection.getURI()));
        vapiConnection.setUsername(connection.getUsername());
        vapiConnection.setPassword(connection.getPassword());

        // TODO manage trust store globally
        vapiConnection.setClient(VapiConnection.newUnsecureClient());

        try {
            vapiConnection.login();
        } catch (IOException | RpcException e) {
            logInfo("Cannot login into vAPI endpoint");
        }

        EnumerationContext enumerationContext = new EnumerationContext(request, parent,
                vapiConnection);

        List<NetworkOverlay> networks = new ArrayList<>();
        List<VmOverlay> vms = new ArrayList<>();
        List<HostSystemOverlay> hosts = new ArrayList<>();
        List<DatastoreOverlay> datastores = new ArrayList<>();
        List<ComputeResourceOverlay> computeResources = new ArrayList<>();

        Map<String, ComputeResourceOverlay> nonDrsClusters = new HashMap<>();

        // put results in different buckets by type
        try {
            for (List<ObjectContent> page : client.retrieveObjects(spec)) {
                for (ObjectContent cont : page) {
                    if (VimUtils.isNetwork(cont.getObj())) {
                        NetworkOverlay net = new NetworkOverlay(cont);
                        networks.add(net);
                    } else if (VimUtils.isVirtualMachine(cont.getObj())) {
                        VmOverlay vm = new VmOverlay(cont);
                        if (vm.getInstanceUuid() == null) {
                            log(Level.INFO, "Cannot process a VM without instanceUuid: %s",
                                    VimUtils.convertMoRefToString(vm.getId()));
                        } else {
                            vms.add(vm);
                        }
                    } else if (VimUtils.isHost(cont.getObj())) {
                        // this includes all standalone and clustered hosts
                        HostSystemOverlay hs = new HostSystemOverlay(cont);
                        hosts.add(hs);
                    } else if (VimUtils.isComputeResource(cont.getObj())) {
                        ComputeResourceOverlay cr = new ComputeResourceOverlay(cont);
                        if (cr.isDrsEnabled()) {
                            // when DRS is enabled add the cluster itself and skip the hosts
                            computeResources.add(cr);
                        } else if (VimUtils.isClusterComputeResource(cont.getObj())) {
                            // when DRS is not enabled, skip the cluster and then
                            // add the inside hosts instead; when provisioning into a non-DRS
                            // cluster, specifying a host is mandatory (in addition to the target
                            // resource pool which always has to be specified)
                            nonDrsClusters.put(cr.getId().getValue(), cr);
                        } else {
                            // add standalone hosts (by their ComputeResource instance instead of
                            // the inner HostSystem one because the former contains the resource
                            // pool which we need)
                            computeResources.add(cr);
                        }
                    } else if (VimUtils.isDatastore(cont.getObj())) {
                        DatastoreOverlay ds = new DatastoreOverlay(cont);
                        datastores.add(ds);
                    }
                }
            }
        } catch (Exception e) {
            String msg = "Error processing PropertyCollector results";
            logWarning(msg);
            mgr.patchTaskToFailure(msg, e);
            return;
        }

        // process results in topological order
        enumerationContext.expectNetworkCount(networks.size());
        for (NetworkOverlay net : networks) {
            processFoundNetwork(enumerationContext, net, parent.tenantLinks);
        }

        enumerationContext.expectDatastoreCount(datastores.size());
        for (DatastoreOverlay ds : datastores) {
            processFoundDatastore(enumerationContext, ds, parent.tenantLinks);
        }

        // checkpoint net & storage, they are not related currently
        try {
            enumerationContext.getDatastoreTracker().await();
            enumerationContext.getNetworkTracker().await();
        } catch (InterruptedException e) {
            threadInterrupted(mgr, e);
            return;
        }

        // include hosts that are part of a non-DRS enabled cluster
        hosts.removeIf(hs -> nonDrsClusters.get(hs.getParent().getValue()) == null);
        enumerationContext.expectHostSystemCount(hosts.size());
        for (HostSystemOverlay hs : hosts) {
            ComputeResourceOverlay cr = nonDrsClusters.get(hs.getParent().getValue());
            processFoundHostSystem(enumerationContext, hs, cr, parent.tenantLinks);
        }

        enumerationContext.expectComputeResourceCount(computeResources.size());
        for (ComputeResourceOverlay cs : computeResources) {
            processFoundComputeResource(enumerationContext, cs, parent.tenantLinks);
        }

        // checkpoint compute
        try {
            enumerationContext.getHostSystemTracker().await();
            enumerationContext.getComputeResourceTracker().await();
        } catch (InterruptedException e) {
            threadInterrupted(mgr, e);
            return;
        }

        enumerationContext.expectVmCount(vms.size());
        for (VmOverlay vm : vms) {
            processFoundVm(enumerationContext, vm, parent.tenantLinks);
        }
        try {
            enumerationContext.getVmTracker().await();
        } catch (InterruptedException e) {
            threadInterrupted(mgr, e);
            return;
        }

        try {
            vapiConnection.close();
        } catch (Exception ignore) {

        }

        mgr.patchTask(TaskStage.FINISHED);
    }

    private void threadInterrupted(TaskManager mgr, InterruptedException e) {
        String msg = "Enumeration thread was interrupted";
        logWarning(msg);
        mgr.patchTaskToFailure(msg, e);
    }

    private URI getVapiUri(URI uri) {
        // TODO use lookup service
        return URI.create(uri.toString().replace("/sdk", "/api"));
    }

    private void processFoundNetwork(EnumerationContext enumerationContext, NetworkOverlay net,
            List<String> tenantLinks) {
        QueryTask task = queryForNetwork(enumerationContext, net.getName());
        task.tenantLinks = tenantLinks;

        withTaskResults(task, result -> {
            if (result.documentLinks.isEmpty()) {
                createNewNetwork(enumerationContext, net, tenantLinks);
            } else {
                NetworkState oldDocument = convertOnlyResultToComputeState(result,
                        NetworkState.class);
                updateNetwork(oldDocument, enumerationContext, net, tenantLinks);
            }
        });
    }

    private void updateNetwork(NetworkState oldDocument, EnumerationContext enumerationContext,
            NetworkOverlay net, List<String> tenantLinks) {
        NetworkState state = makeNetworkStateFromResults(enumerationContext, net);
        state.documentSelfLink = oldDocument.documentSelfLink;
        if (oldDocument.tenantLinks == null) {
            state.tenantLinks = tenantLinks;
        }

        logInfo("Syncing Network %s", net.getName());
        Operation.createPatch(UriUtils.buildUri(getHost(), oldDocument.documentSelfLink))
                .setBody(state)
                .setCompletion(trackNetwork(enumerationContext, net))
                .sendWith(this);
    }

    private void createNewNetwork(EnumerationContext enumerationContext, NetworkOverlay net,
            List<String> tenantLinks) {
        NetworkState state = makeNetworkStateFromResults(enumerationContext, net);
        state.tenantLinks = tenantLinks;
        Operation.createPost(this, NetworkService.FACTORY_LINK)
                .setBody(state)
                .setCompletion(trackNetwork(enumerationContext, net))
                .sendWith(this);

        logInfo("Found new Network %s", net.getName());
    }

    private String getSelfLinkFromOperation(Operation o) {
        return o.getBody(ServiceDocument.class).documentSelfLink;
    }

    private CompletionHandler trackDatastore(EnumerationContext enumerationContext,
            DatastoreOverlay ds) {
        return (o, e) -> {
            String key = VimUtils.convertMoRefToString(ds.getId());

            if (e == null) {
                enumerationContext.getDatastoreTracker().track(key, getSelfLinkFromOperation(o));
            } else {
                enumerationContext.getDatastoreTracker().track(key, ResourceTracker.ERROR);
            }
        };
    }

    private CompletionHandler trackVm(EnumerationContext enumerationContext,
            VmOverlay vm) {
        return (o, e) -> {
            String key = VimUtils.convertMoRefToString(vm.getId());

            if (e == null) {
                enumerationContext.getVmTracker().track(key, getSelfLinkFromOperation(o));
            } else {
                enumerationContext.getVmTracker().track(key, ResourceTracker.ERROR);
            }
        };
    }

    private CompletionHandler trackComputeResource(EnumerationContext enumerationContext,
            ComputeResourceOverlay cr) {
        return (o, e) -> {
            String key = VimUtils.convertMoRefToString(cr.getId());

            if (e == null) {
                enumerationContext.getComputeResourceTracker()
                        .track(key, getSelfLinkFromOperation(o));
            } else {
                enumerationContext.getComputeResourceTracker().track(key, ResourceTracker.ERROR);
            }
        };
    }

    private CompletionHandler trackHostSystem(EnumerationContext enumerationContext,
            HostSystemOverlay hostSystem) {
        return (o, e) -> {
            String key = VimUtils.convertMoRefToString(hostSystem.getId());

            if (e == null) {
                enumerationContext.getHostSystemTracker().track(key, getSelfLinkFromOperation(o));
            } else {
                enumerationContext.getHostSystemTracker().track(key, ResourceTracker.ERROR);
            }
        };
    }

    private CompletionHandler trackNetwork(EnumerationContext enumerationContext,
            NetworkOverlay net) {
        return (o, e) -> {
            String key = VimUtils.convertMoRefToString(net.getId());

            if (e == null) {
                enumerationContext.getNetworkTracker().track(key, getSelfLinkFromOperation(o));
            } else {
                enumerationContext.getNetworkTracker().track(key, ResourceTracker.ERROR);
            }
        };
    }

    private NetworkState makeNetworkStateFromResults(EnumerationContext enumerationContext,
            NetworkOverlay net) {
        ComputeEnumerateResourceRequest request = enumerationContext.getRequest();
        ComputeStateWithDescription parent = enumerationContext.getParent();

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

    private QueryTask queryForNetwork(EnumerationContext ctx, String name) {
        URI adapterManagementReference = ctx.getParent().adapterManagementReference;
        String regionId = ctx.getParent().description.regionId;

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

    private void processFoundDatastore(EnumerationContext enumerationContext,
            DatastoreOverlay ds, List<String> tenantLinks) {
        ComputeEnumerateResourceRequest request = enumerationContext.getRequest();
        String regionId = enumerationContext.getRegionId();

        QueryTask task = queryForStorage(request.adapterManagementReference, ds.getName(),
                regionId);
        task.tenantLinks = tenantLinks;

        withTaskResults(task, result -> {
            if (result.documentLinks.isEmpty()) {
                createNewStorageDescription(enumerationContext, ds, tenantLinks);
            } else {
                StorageDescription oldDocument = convertOnlyResultToComputeState(result,
                        StorageDescription.class);
                updateStorageDescription(oldDocument, enumerationContext, ds, tenantLinks);
            }
        });
    }

    private void updateStorageDescription(StorageDescription oldDocument,
            EnumerationContext enumerationContext, DatastoreOverlay ds,
            List<String> tenantLinks) {
        ComputeEnumerateResourceRequest request = enumerationContext.getRequest();
        String regionId = enumerationContext.getRegionId();

        StorageDescription desc = makeStorageFromResults(request, ds, regionId);
        desc.documentSelfLink = oldDocument.documentSelfLink;
        if (oldDocument.tenantLinks == null) {
            desc.tenantLinks = tenantLinks;
        }

        logInfo("Syncing Storage %s", ds.getName());
        Operation.createPatch(UriUtils.buildUri(getHost(), desc.documentSelfLink))
                .setBody(desc)
                .setCompletion(trackDatastore(enumerationContext, ds))
                .sendWith(this);
    }

    private void createNewStorageDescription(EnumerationContext enumerationContext,
            DatastoreOverlay ds, List<String> tenantLinks) {
        ComputeEnumerateResourceRequest request = enumerationContext.getRequest();
        String regionId = enumerationContext.getRegionId();
        StorageDescription desc = makeStorageFromResults(request, ds, regionId);
        desc.tenantLinks = tenantLinks;
        logInfo("Found new Datastore %s", ds.getName());

        Operation.createPost(this, StorageDescriptionService.FACTORY_LINK)
                .setBody(desc)
                .setCompletion(trackDatastore(enumerationContext, ds))
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
     * @param enumerationContext
     * @param cr
     * @param tenantLinks
     */
    private void processFoundComputeResource(EnumerationContext enumerationContext,
            ComputeResourceOverlay cr, List<String> tenantLinks) {
        ComputeEnumerateResourceRequest request = enumerationContext.getRequest();
        QueryTask task = queryForCluster(request.resourceLink(),
                cr.getId().getValue());
        task.tenantLinks = tenantLinks;

        withTaskResults(task, result -> {
            if (result.documentLinks.isEmpty()) {
                createNewComputeResource(enumerationContext, cr, tenantLinks);
            } else {
                ComputeState oldDocument = convertOnlyResultToComputeState(result,
                        ComputeState.class);
                updateCluster(oldDocument, enumerationContext, cr, tenantLinks);
            }
        });
    }

    private <T> T convertOnlyResultToComputeState(ServiceDocumentQueryResult result,
            Class<T> type) {
        return Utils.fromJson(result.documents.values().iterator().next(), type);
    }

    private void updateCluster(ComputeState oldDocument,
            EnumerationContext enumerationContext, ComputeResourceOverlay cr,
            List<String> tenantLinks) {
        ComputeState state = makeComputeResourceFromResults(enumerationContext, cr);
        state.documentSelfLink = oldDocument.documentSelfLink;
        if (oldDocument.tenantLinks == null) {
            state.tenantLinks = tenantLinks;
        }
        populateTags(cr, enumerationContext.getEndpoint(), state, tenantLinks);

        logInfo("Syncing ComputeResource %s", oldDocument.documentSelfLink);
        Operation.createPatch(UriUtils.buildUri(getHost(), oldDocument.documentSelfLink))
                .setBody(state)
                .sendWith(this);

        ComputeDescription desc = makeDescriptionForCluster(enumerationContext, cr);
        desc.documentSelfLink = oldDocument.descriptionLink;
        Operation.createPatch(UriUtils.buildUri(getHost(), desc.documentSelfLink))
                .setBody(desc)
                .setCompletion(trackComputeResource(enumerationContext, cr))
                .sendWith(this);
    }

    private ComputeDescription makeDescriptionForCluster(EnumerationContext enumerationContext,
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

        res.instanceAdapterReference = enumerationContext
                .getParent().description.instanceAdapterReference;
        res.enumerationAdapterReference = enumerationContext
                .getParent().description.enumerationAdapterReference;
        res.statsAdapterReference = enumerationContext
                .getParent().description.statsAdapterReference;

        return res;
    }

    private void createNewComputeResource(EnumerationContext enumerationContext,
            ComputeResourceOverlay cr, List<String> tenantLinks) {
        ComputeDescription desc = makeDescriptionForCluster(enumerationContext, cr);
        desc.tenantLinks = tenantLinks;
        Operation.createPost(this, ComputeDescriptionService.FACTORY_LINK)
                .setBody(desc)
                .sendWith(this);

        ComputeState state = makeComputeResourceFromResults(enumerationContext, cr);
        state.tenantLinks = tenantLinks;
        state.descriptionLink = desc.documentSelfLink;
        populateTags(cr, enumerationContext.getEndpoint(), state, tenantLinks);

        logInfo("Found new ComputeResource %s", cr.getId().getValue());
        Operation.createPost(this, ComputeService.FACTORY_LINK)
                .setBody(state)
                .setCompletion(trackComputeResource(enumerationContext, cr))
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

    private ComputeState makeComputeResourceFromResults(EnumerationContext enumerationContext,
            ComputeResourceOverlay cr) {
        ComputeState state = new ComputeState();
        state.id = cr.getId().getValue();
        state.adapterManagementReference = enumerationContext.getRequest().adapterManagementReference;
        state.parentLink = enumerationContext.getRequest().resourceLink();
        state.resourcePoolLink = enumerationContext.getRequest().resourcePoolLink;
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
     * @param enumerationContext
     * @param hs
     * @param cr parent ComputeResource
     * @param tenantLinks
     */
    private void processFoundHostSystem(EnumerationContext enumerationContext,
            HostSystemOverlay hs, ComputeResourceOverlay cr, List<String> tenantLinks) {
        ComputeEnumerateResourceRequest request = enumerationContext.getRequest();
        QueryTask task = queryForHostSystem(request.resourceLink(),
                hs.getHardwareUuid());
        task.tenantLinks = tenantLinks;
        withTaskResults(task, result -> {
            if (result.documentLinks.isEmpty()) {
                createNewHostSystem(enumerationContext, hs, cr, tenantLinks);
            } else {
                ComputeState oldDocument = convertOnlyResultToComputeState(result,
                        ComputeState.class);
                updateHostSystem(oldDocument, enumerationContext, hs, cr, tenantLinks);
            }
        });
    }

    private void updateHostSystem(ComputeState oldDocument, EnumerationContext enumerationContext,
            HostSystemOverlay hs, ComputeResourceOverlay cr, List<String> tenantLinks) {
        ComputeState state = makeHostSystemFromResults(enumerationContext, hs, cr);
        state.documentSelfLink = oldDocument.documentSelfLink;
        if (oldDocument.tenantLinks == null) {
            state.tenantLinks = tenantLinks;
        }
        populateTags(hs, enumerationContext.getEndpoint(), state, tenantLinks);

        logInfo("Syncing HostSystem %s", oldDocument.documentSelfLink);
        Operation.createPatch(UriUtils.buildUri(getHost(), state.documentSelfLink))
                .setBody(state)
                .setCompletion(trackHostSystem(enumerationContext, hs))
                .sendWith(this);

        ComputeDescription desc = makeDescriptionForHost(enumerationContext, hs);
        desc.documentSelfLink = oldDocument.descriptionLink;
        Operation.createPatch(UriUtils.buildUri(getHost(), desc.documentSelfLink))
                .setBody(desc)
                .sendWith(this);
    }

    private void createNewHostSystem(EnumerationContext enumerationContext,
            HostSystemOverlay hs, ComputeResourceOverlay cr, List<String> tenantLinks) {
        ComputeDescription desc = makeDescriptionForHost(enumerationContext, hs);
        desc.tenantLinks = tenantLinks;
        Operation.createPost(this, ComputeDescriptionService.FACTORY_LINK)
                .setBody(desc)
                .sendWith(this);

        ComputeState state = makeHostSystemFromResults(enumerationContext, hs, cr);
        state.descriptionLink = desc.documentSelfLink;
        state.tenantLinks = tenantLinks;
        populateTags(hs, enumerationContext.getEndpoint(), state, tenantLinks);

        logInfo("Found new HostSystem %s", hs.getName());
        Operation.createPost(this, ComputeService.FACTORY_LINK)
                .setBody(state)
                .setCompletion(trackHostSystem(enumerationContext, hs))
                .sendWith(this);
    }

    private void populateTags(AbstractOverlay obj, VapiConnection endpoint, ComputeState state,
            List<String> tenantLinks) {
        state.tagLinks = retrieveTagLinksAndCreateTagsAsync(endpoint, obj.getId(), tenantLinks);
    }

    private ComputeDescription makeDescriptionForHost(EnumerationContext enumerationContext,
            HostSystemOverlay hs) {
        ComputeDescription res = new ComputeDescription();
        res.name = hs.getName();
        res.documentSelfLink = UriUtils
                .buildUriPath(ComputeDescriptionService.FACTORY_LINK, UUID.randomUUID().toString());
        res.cpuCount = hs.getCoreCount();
        res.cpuMhzPerCore = hs.getCpuMhz();
        res.totalMemoryBytes = hs.getTotalMemoryBytes();
        res.supportedChildren = Collections.singletonList(ComputeType.VM_GUEST.name());
        res.instanceAdapterReference = enumerationContext
                .getParent().description.instanceAdapterReference;
        res.enumerationAdapterReference = enumerationContext
                .getParent().description.enumerationAdapterReference;
        res.statsAdapterReference = enumerationContext
                .getParent().description.statsAdapterReference;

        return res;
    }

    private ComputeState makeHostSystemFromResults(EnumerationContext enumerationContext,
            HostSystemOverlay hs, ComputeResourceOverlay cr) {
        ComputeState state = new ComputeState();
        state.id = hs.getHardwareUuid();
        state.adapterManagementReference = enumerationContext.getRequest().adapterManagementReference;
        state.parentLink = enumerationContext.getRequest().resourceLink();
        state.resourcePoolLink = enumerationContext.getRequest().resourcePoolLink;
        state.name = hs.getName();
        // TODO: retrieve host power state
        state.powerState = PowerState.ON;
        CustomProperties.of(state)
                .put(CustomProperties.MOREF, hs.getId())
                .put(CustomProperties.TYPE, hs.getId().getType());
        return state;
    }

    /**
     * @param enumerationContext
     * @param vm
     * @param tenantLinks
     */
    private void processFoundVm(EnumerationContext enumerationContext, VmOverlay vm,
            List<String> tenantLinks) {
        ComputeEnumerateResourceRequest request = enumerationContext.getRequest();
        QueryTask task = queryForVm(request.resourceLink(), vm.getInstanceUuid());
        task.tenantLinks = tenantLinks;

        withTaskResults(task, result -> {
            if (result.documentLinks.isEmpty()) {
                createNewVm(enumerationContext, vm, tenantLinks);
            } else {
                ComputeState oldDocument = convertOnlyResultToComputeState(result,
                        ComputeState.class);
                updateVm(oldDocument, enumerationContext, vm, tenantLinks);
            }
        });
    }

    private void updateVm(ComputeState oldDocument, EnumerationContext enumerationContext,
            VmOverlay vm, List<String> tenantLinks) {
        ComputeState state = makeVmFromResults(enumerationContext, vm);
        state.documentSelfLink = oldDocument.documentSelfLink;
        if (oldDocument.tenantLinks == null) {
            state.tenantLinks = tenantLinks;
        }
        populateTags(vm, enumerationContext.getEndpoint(), state, tenantLinks);

        logInfo("Syncing VM %s", state.documentSelfLink);
        Operation.createPatch(UriUtils.buildUri(getHost(), oldDocument.documentSelfLink))
                .setBody(state)
                .setCompletion(trackVm(enumerationContext, vm))
                .sendWith(this);
    }

    private void createNewVm(EnumerationContext enumerationContext, VmOverlay vm,
            List<String> tenantLinks) {
        ComputeDescription desc = makeDescriptionForVm(enumerationContext, vm);
        desc.tenantLinks = tenantLinks;
        Operation.createPost(this, ComputeDescriptionService.FACTORY_LINK)
                .setBody(desc)
                .sendWith(this);

        ComputeState state = makeVmFromResults(enumerationContext, vm);
        state.descriptionLink = desc.documentSelfLink;
        state.tenantLinks = tenantLinks;
        populateTags(vm, enumerationContext.getEndpoint(), state, tenantLinks);

        state.networkInterfaceLinks = new ArrayList<>();
        for (VirtualEthernetCard nic : vm.getNics()) {
            VirtualDeviceBackingInfo backing = nic.getBacking();

            if (backing instanceof VirtualEthernetCardNetworkBackingInfo) {
                VirtualEthernetCardNetworkBackingInfo veth = (VirtualEthernetCardNetworkBackingInfo) backing;
                NetworkInterfaceState iface = new NetworkInterfaceState();
                iface.networkLink = enumerationContext.getNetworkTracker()
                        .getSelfLink(veth.getNetwork());
                iface.name = nic.getDeviceInfo().getLabel();
                iface.documentSelfLink = UriUtils.buildUriPath(NetworkInterfaceService.FACTORY_LINK,
                        UUID.randomUUID().toString());

                Operation.createPost(this, NetworkInterfaceService.FACTORY_LINK)
                        .setBody(iface)
                        .sendWith(this);

                state.networkInterfaceLinks.add(iface.documentSelfLink);
            } else {
                //TODO add support for DVS
                logInfo("Will not add nic of type %s", backing.getClass().getName());
            }
        }

        logInfo("Found new VM %s", vm.getInstanceUuid());
        Operation.createPost(this, ComputeService.FACTORY_LINK)
                .setBody(state)
                .setCompletion(trackVm(enumerationContext, vm))
                .sendWith(this);
    }

    private ComputeDescription makeDescriptionForVm(EnumerationContext enumerationContext,
            VmOverlay vm) {
        ComputeDescription res = new ComputeDescription();
        res.name = vm.getName();
        res.documentSelfLink = UriUtils
                .buildUriPath(ComputeDescriptionService.FACTORY_LINK, UUID.randomUUID().toString());
        res.instanceAdapterReference = enumerationContext
                .getParent().description.instanceAdapterReference;
        res.enumerationAdapterReference = enumerationContext
                .getParent().description.enumerationAdapterReference;
        res.statsAdapterReference = enumerationContext
                .getParent().description.statsAdapterReference;
        res.powerAdapterReference = enumerationContext
                .getParent().description.powerAdapterReference;

        res.regionId = enumerationContext.getRegionId();

        res.cpuCount = vm.getNumCpu();
        res.totalMemoryBytes = vm.getMemoryBytes();
        return res;
    }

    /**
     * Make a ComputeState from the request and a vm found in vsphere.
     *
     * @param enumerationContext
     * @param vm
     * @return
     */
    private ComputeState makeVmFromResults(EnumerationContext enumerationContext, VmOverlay vm) {
        ComputeEnumerateResourceRequest request = enumerationContext.getRequest();

        ComputeState state = new ComputeState();
        state.adapterManagementReference = request.adapterManagementReference;
        state.parentLink = request.resourceLink();
        state.resourcePoolLink = request.resourcePoolLink;

        state.powerState = vm.getPowerState();
        state.primaryMAC = vm.getPrimaryMac();
        if (!vm.isTemplate()) {
            state.address = vm.guessPublicIpV4Address();
        }
        state.id = vm.getInstanceUuid();
        state.name = vm.getName();

        CustomProperties.of(state)
                .put(CustomProperties.MOREF, vm.getId())
                .put(CustomProperties.TEMPLATE_FLAG, vm.isTemplate())
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
