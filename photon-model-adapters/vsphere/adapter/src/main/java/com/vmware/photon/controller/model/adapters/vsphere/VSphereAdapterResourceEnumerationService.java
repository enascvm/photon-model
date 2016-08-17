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

import java.net.URI;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
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
import java.util.logging.Level;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.Connection;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.StorageDescriptionService;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;
import com.vmware.vim25.ObjectContent;
import com.vmware.vim25.PropertyFilterSpec;
import com.vmware.vim25.UpdateSet;
import com.vmware.xenon.common.Operation;
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
 * Handles enumeration for vsphere endpoints. It supports up to {@link #MAX_CONCURRENT_ENUM_PROCESSES}
 * concurrent long-running enumeration processes. Attempts to start more processes than that will result
 * in error.
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

    public VSphereAdapterResourceEnumerationService() {
        this.enumerationThreadPool = new ThreadPoolExecutor(MAX_CONCURRENT_ENUM_PROCESSES,
                MAX_CONCURRENT_ENUM_PROCESSES,
                0L, TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(),
                new AbortPolicy());
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
            this.enumerationThreadPool.execute(() -> {
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
                processFoundObjects(request, page, parent);
            }
        } catch (Exception e) {
            String msg = "Error processing PropertyCollector results";
            logWarning(msg);
            mgr.patchTaskToFailure(msg, e);
        }

        mgr.patchTask(TaskStage.FINISHED);
    }

    /**
     * @see #processFoundCluster(ComputeEnumerateResourceRequest, ComputeResourceOverlay)
     * @param request
     * @param objects
     * @param parent
     */
    private void processFoundObjects(ComputeEnumerateResourceRequest request,
            List<ObjectContent> objects, ComputeStateWithDescription parent) {
        for (ObjectContent cont : objects) {
            if (VimUtils.isVirtualMachine(cont.getObj())) {
                VmOverlay vm = new VmOverlay(cont);
                processFoundVm(request, vm, parent);
            } else if (VimUtils.isResourcePool(cont.getObj())) {
                logInfo("Ignoring ResourcePool %s", cont.getObj());
            } else if (VimUtils.isHost(cont.getObj())) {
                HostSystemOverlay hs = new HostSystemOverlay(cont);
                processFoundHostSystem(request, hs);
            } else if (VimUtils.isComputeResource(cont.getObj())) {
                ComputeResourceOverlay cr = new ComputeResourceOverlay(cont);
                processFoundCluster(request, cr);
            } else if (VimUtils.isDatastore(cont.getObj())) {
                DatastoreOverlay ds = new DatastoreOverlay(cont);
                processFoundDatastore(request, ds, parent.description.datacenterId);
            } else if (VimUtils.isNetwork(cont.getObj())) {
                NetworkOverlay net = new NetworkOverlay(cont);
                processFoundNetwork(request, net, parent);
            }
        }
    }

    private void processFoundNetwork(ComputeEnumerateResourceRequest request, NetworkOverlay net,
            ComputeStateWithDescription parent) {
        QueryTask task = queryForNetwork(request.adapterManagementReference, net.getName(),
                parent.description.datacenterId);

        withTaskResults(task, result -> {
            if (result.documentLinks.isEmpty()) {
                createNewNetwork(request, net, parent);
            } else {
                ComputeState oldDocument = Utils
                        .fromJson(result.documents.values().iterator().next(), ComputeState.class);
                updateNetwork(oldDocument, request, net, parent);
            }
        });
    }

    private void updateNetwork(ComputeState oldDocument, ComputeEnumerateResourceRequest request,
            NetworkOverlay net, ComputeStateWithDescription parent) {
        NetworkState state = makeNetworkStateFromResults(request, net, parent);
        state.documentSelfLink = oldDocument.documentSelfLink;

        logInfo("Syncing Network %s", net.getName());
        Operation.createPatch(UriUtils.buildUri(getHost(), oldDocument.documentSelfLink))
                .setBody(state)
                .sendWith(this);
    }

    private void createNewNetwork(ComputeEnumerateResourceRequest request, NetworkOverlay net,
            ComputeStateWithDescription parent) {
        NetworkState state = makeNetworkStateFromResults(request, net, parent);
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
        state.regionId = parent.description.datacenterId;
        state.resourcePoolLink = request.resourcePoolLink;
        state.instanceAdapterReference = parent.description.instanceAdapterReference;
        state.authCredentialsLink = parent.description.authCredentialsLink;
        state.adapterManagementReference = request.adapterManagementReference;
        CustomProperties.of(state)
                .put(ComputeProperties.ON_PREMISE_DATACENTER, parent.description.datacenterId)
                .put(CustomProperties.MOREF, net.getId())
                .put(CustomProperties.TYPE, net.getId().getType());

        return state;
    }

    private QueryTask queryForNetwork(URI adapterManagementReference, String name,
            String datacenterId) {
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
                .addFieldClause(
                        QuerySpecification.buildCompositeFieldName(
                                ResourcePoolState.FIELD_NAME_CUSTOM_PROPERTIES,
                                ComputeProperties.ON_PREMISE_DATACENTER),
                        datacenterId).build());

        return QueryTask
                .create(qs)
                .setDirect(true);
    }

    private void processFoundDatastore(ComputeEnumerateResourceRequest request,
            DatastoreOverlay ds, String datacenterId) {
        QueryTask task = queryForStorage(request.adapterManagementReference, ds.getName(),
                datacenterId);

        withTaskResults(task, result -> {
            if (result.documentLinks.isEmpty()) {
                createNewStorageDescription(request, ds, datacenterId);
            } else {
                StorageDescription oldDocument = Utils
                        .fromJson(result.documents.values().iterator().next(),
                                StorageDescription.class);
                updateStorageDescription(oldDocument, request, ds, datacenterId);
            }
        });
    }

    private void updateStorageDescription(StorageDescription oldDocument,
            ComputeEnumerateResourceRequest request, DatastoreOverlay ds,
            String datacenterId) {
        StorageDescription desc = makeStorageFromResults(request, ds, datacenterId);
        desc.documentSelfLink = oldDocument.documentSelfLink;

        logInfo("Syncing Storage %s", ds.getName());
        Operation.createPatch(UriUtils.buildUri(getHost(), desc.documentSelfLink))
                .setBody(desc)
                .sendWith(this);
    }

    private void createNewStorageDescription(ComputeEnumerateResourceRequest request,
            DatastoreOverlay ds, String datacenterId) {
        StorageDescription desc = makeStorageFromResults(request, ds, datacenterId);

        logInfo("Found new Datastore %s", ds.getName());

        Operation.createPost(this, StorageDescriptionService.FACTORY_LINK)
                .setBody(desc)
                .sendWith(this);
    }

    private StorageDescription makeStorageFromResults(ComputeEnumerateResourceRequest request,
            DatastoreOverlay ds, String datacenterId) {
        StorageDescription res = new StorageDescription();
        res.id = res.name = ds.getName();
        res.type = ds.getType();
        res.resourcePoolLink = request.resourcePoolLink;
        res.adapterManagementReference = request.adapterManagementReference;
        res.capacityBytes = ds.getCapacityBytes();
        CustomProperties.of(res)
                .put(CustomProperties.MOREF, ds.getId())
                .put(ComputeProperties.ON_PREMISE_DATACENTER, datacenterId);

        return res;
    }

    private QueryTask queryForStorage(URI adapterManagementReference, String name,
            String datacenterId) {
        QuerySpecification qs = new QuerySpecification();
        qs.query.addBooleanClause(
                Query.Builder.create().addFieldClause(StorageDescription.FIELD_NAME_NAME, name)
                        .build());

        qs.query.addBooleanClause(Query.Builder.create()
                .addFieldClause(StorageDescription.FIELD_NAME_ADAPTER_REFERENCE,
                        adapterManagementReference.toString()).build());

        qs.query.addBooleanClause(Query.Builder.create()
                .addFieldClause(
                        QuerySpecification.buildCompositeFieldName(
                                ResourcePoolState.FIELD_NAME_CUSTOM_PROPERTIES,
                                ComputeProperties.ON_PREMISE_DATACENTER),
                        datacenterId).build());
        return QueryTask
                .create(qs)
                .setDirect(true);
    }

    /**
     * Either creates a new Compute or update an already existing one. Existence is checked by querying
     * for a compute with id equals to moref value of a cluster whose parent is the Compute from the
     * request.
     * @param request
     * @param cr
     */
    private void processFoundCluster(ComputeEnumerateResourceRequest request,
            ComputeResourceOverlay cr) {
        QueryTask task = queryForCluster(request.resourceLink(),
                cr.getId().getValue());

        withTaskResults(task, result -> {
            if (result.documentLinks.isEmpty()) {
                createNewCluster(request, cr);
            } else {
                ComputeState oldDocument = Utils
                        .fromJson(result.documents.values().iterator().next(), ComputeState.class);
                updateCluster(oldDocument, request, cr);
            }
        });
    }

    private void updateCluster(ComputeState oldDocument,
            ComputeEnumerateResourceRequest request, ComputeResourceOverlay cr) {
        ComputeState state = makeClusterFromResults(request, cr);
        state.documentSelfLink = oldDocument.documentSelfLink;

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
        res.cpuMhzPerCore = cr.getTotalCpuMhz() / cr.getTotalCpuCores();
        res.totalMemoryBytes = cr.getEffectiveMemoryBytes();
        res.supportedChildren = Collections.singletonList(ComputeType.VM_GUEST.name());
        return res;
    }

    private void createNewCluster(ComputeEnumerateResourceRequest request,
            ComputeResourceOverlay cr) {
        ComputeDescription desc = makeDescriptionForCluster(request, cr);
        Operation.createPost(this, ComputeDescriptionService.FACTORY_LINK)
                .setBody(desc)
                .sendWith(this);

        ComputeState state = makeClusterFromResults(request, cr);
        state.descriptionLink = desc.documentSelfLink;

        logInfo("Found new ComputeResource %s", cr.getId().getValue());
        Operation.createPost(this, ComputeService.FACTORY_LINK)
                .setBody(state)
                .sendWith(this);
    }

    private ComputeState makeClusterFromResults(ComputeEnumerateResourceRequest request,
            ComputeResourceOverlay cr) {
        ComputeState state = new ComputeState();
        state.id = cr.getId().getValue();
        state.adapterManagementReference = request.adapterManagementReference;
        state.parentLink = request.resourceLink();
        state.name = cr.getName();
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
     * @see #processFoundCluster(ComputeEnumerateResourceRequest, ComputeResourceOverlay)
     * @param request
     * @param hs
     */
    private void processFoundHostSystem(ComputeEnumerateResourceRequest request,
            HostSystemOverlay hs) {
        QueryTask task = queryForHostSystem(request.resourceLink(),
                hs.getHardwareUuid());
        withTaskResults(task, result -> {
            if (result.documentLinks.isEmpty()) {
                createNewHostSystem(request, hs);
            } else {
                ComputeState oldDocument = Utils
                        .fromJson(result.documents.values().iterator().next(), ComputeState.class);
                updateHostSystem(oldDocument, request, hs);
            }
        });
    }

    private void updateHostSystem(ComputeState oldDocument, ComputeEnumerateResourceRequest request,
            HostSystemOverlay hs) {
        ComputeState state = makeHostSystemFromResults(request, hs);
        state.documentSelfLink = oldDocument.documentSelfLink;

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
            HostSystemOverlay hs) {

        ComputeDescription desc = makeDescriptionForHost(request, hs);
        Operation.createPost(this, ComputeDescriptionService.FACTORY_LINK)
                .setBody(desc)
                .sendWith(this);

        ComputeState state = makeHostSystemFromResults(request, hs);
        state.descriptionLink = desc.documentSelfLink;

        logInfo("Found new HostSystem %s", hs.getName());
        Operation.createPost(this, ComputeService.FACTORY_LINK)
                .setBody(state)
                .sendWith(this);
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
        return res;
    }

    private ComputeState makeHostSystemFromResults(ComputeEnumerateResourceRequest request,
            HostSystemOverlay hs) {
        ComputeState state = new ComputeState();
        state.id = hs.getHardwareUuid();
        state.adapterManagementReference = request.adapterManagementReference;
        state.parentLink = request.resourceLink();
        state.name = hs.getName();
        CustomProperties.of(state)
                .put(CustomProperties.MOREF, hs.getId())
                .put(CustomProperties.TYPE, hs.getId().getType());
        return state;
    }

    /**
     * @see #processFoundCluster(ComputeEnumerateResourceRequest, ComputeResourceOverlay)
     * @param request
     * @param vm
     * @param parent
     */
    private void processFoundVm(ComputeEnumerateResourceRequest request, VmOverlay vm,
            ComputeStateWithDescription parent) {
        QueryTask task = queryForVm(request.resourceLink(), vm.getInstanceUuid());
        withTaskResults(task, result -> {
            if (result.documentLinks.isEmpty()) {
                createNewVm(request, vm, parent);
            } else {
                ComputeState oldDocument = Utils
                        .fromJson(result.documents.values().iterator().next(),
                                ComputeState.class);
                updateVm(oldDocument, request, vm, parent);
            }
        });
    }

    private void updateVm(ComputeState oldDocument, ComputeEnumerateResourceRequest request,
            VmOverlay vm, ComputeStateWithDescription parent) {
        ComputeState state = makeVmFromResults(request, vm);
        state.documentSelfLink = oldDocument.documentSelfLink;

        logInfo("Syncing VM %s", state.documentSelfLink);
        Operation.createPatch(UriUtils.buildUri(getHost(), oldDocument.documentSelfLink))
                .setBody(state)
                .sendWith(this);

        ComputeDescription desc = makeDescriptionForVm(request, vm, parent);
        desc.documentSelfLink = state.descriptionLink;
        Operation.createPatch(UriUtils.buildUri(getHost(), desc.documentSelfLink))
                .setBody(desc)
                .sendWith(this);
    }

    private void createNewVm(ComputeEnumerateResourceRequest request, VmOverlay vm,
            ComputeStateWithDescription parent) {
        ComputeDescription desc = makeDescriptionForVm(request, vm, parent);
        Operation.createPost(this, ComputeDescriptionService.FACTORY_LINK)
                .setBody(desc)
                .sendWith(this);

        ComputeState state = makeVmFromResults(request, vm);
        state.descriptionLink = desc.documentSelfLink;

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
        res.powerAdapterReference = parent.description.powerAdapterReference;
        res.enumerationAdapterReference = parent.description.enumerationAdapterReference;
        res.instanceAdapterReference = parent.description.instanceAdapterReference;
        res.statsAdapterReference = parent.description.statsAdapterReference;

        res.datacenterId = parent.description.datacenterId;

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
        state.id = vm.getInstanceUuid();
        state.name = vm.getName();

        CustomProperties.of(state)
                .put(CustomProperties.MOREF, vm.getId())
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
     * Builds a query for finding a ComputeState by instanceUuid from vsphere and parent compute link.
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
