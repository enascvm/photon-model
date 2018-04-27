/*
 * Copyright (c) 2018-2019 VMware, Inc. All Rights Reserved.
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

import static com.vmware.photon.controller.model.adapters.vsphere.VsphereEnumerationHelper.convertOnlyResultToDocument;
import static com.vmware.photon.controller.model.adapters.vsphere.VsphereEnumerationHelper.withTaskResults;
import static com.vmware.photon.controller.model.adapters.vsphere.util.VimNames.TYPE_SERVER_DISK;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.adapters.vsphere.EnumerationClient.ObjectUpdateIterator;
import com.vmware.photon.controller.model.adapters.vsphere.VsphereResourceCleanerService.ResourceCleanRequest;
import com.vmware.photon.controller.model.adapters.vsphere.tagging.TagCache;
import com.vmware.photon.controller.model.adapters.vsphere.util.MoRefKeyedMap;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.Connection;
import com.vmware.photon.controller.model.adapters.vsphere.util.finders.DatacenterLister;
import com.vmware.photon.controller.model.adapters.vsphere.util.finders.Element;
import com.vmware.photon.controller.model.adapters.vsphere.vapi.RpcException;
import com.vmware.photon.controller.model.adapters.vsphere.vapi.VapiConnection;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.StorageDescriptionService;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.util.PhotonModelUriUtils;
import com.vmware.vim25.AboutInfo;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.ObjectUpdate;
import com.vmware.vim25.ObjectUpdateKind;
import com.vmware.vim25.PropertyFilterSpec;
import com.vmware.vim25.PropertyFilterUpdate;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.vim25.UpdateSet;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;

/**
 * Stateless service responsible for a vsphere endpoint enumeration.
 * There are two types of enumeration - full enumeration and incremental enumeration.
 * A full enumeration collects the vsphere state and syncs photon model with the state.
 * An incremental enumeration performs a full enumeration and keeps incrementally querying
 * the endpoint to sync changes.
 */
public class VSphereIncrementalEnumerationService extends StatelessService {

    public static final String FACTORY_LINK = VSphereUriPaths.INCREMENTAL_ENUMERATION_SERVICE;

    public static final String PREFIX_NETWORK = "network";
    public static final String PREFIX_DATASTORE = "datastore";

    public enum InterfaceStateMode {
        INTERFACE_STATE_WITH_OPAQUE_NETWORK,
        INTERFACE_STATE_WITH_DISTRIBUTED_VIRTUAL_PORT
    }

    /**
     * The enumeration request.
     */
    public static class VSphereIncrementalEnumerationRequest extends ServiceDocument {
        public ComputeEnumerateResourceRequest request;
    }

    public class CollectorDetails {
        private ManagedObjectReference resourcesPropertyCollector;
        private ManagedObjectReference vmPropertyCollector;
        private String resourcesCollectorVersion;
        private String vmCollectorVersion;
        private String datacenter;
    }

    private static class SegregatedOverlays {
        MoRefKeyedMap<NetworkOverlay> networks = new MoRefKeyedMap<>();
        List<HostSystemOverlay> hosts = new ArrayList<>();
        List<DatastoreOverlay> datastores = new ArrayList<>();
        List<ComputeResourceOverlay> clusters = new ArrayList<>();
        List<ResourcePoolOverlay> resourcePools = new ArrayList<>();
        List<FolderOverlay> folders = new ArrayList<>();
    }

    /**
     * Connection to vSphere endpoint.
     */
    private Connection connection;

    /**
     *  compute host
     */
    private ComputeStateWithDescription parent;

    /**
     * Stores property collector and version information for each data center in this endpoint.
     */
    private final List<CollectorDetails> collectors = new ArrayList<>();

    private final TagCache tagCache = new TagCache();

    public TagCache getTagCache() {
        return this.tagCache;
    }

    @Override
    public void handleStart(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        VSphereIncrementalEnumerationRequest enumerationRequest = op.getBody(VSphereIncrementalEnumerationRequest.class);

        ComputeEnumerateResourceRequest request = enumerationRequest.request;

        validate(request);

        op.setStatusCode(Operation.STATUS_CODE_CREATED);
        op.complete();

        TaskManager mgr = new TaskManager(this, request.taskReference, request.resourceLink());

        Operation.createGet(PhotonModelUriUtils.createInventoryUri(getHost(), request.endpointLink))
                .setCompletion(o -> {
                    thenWithParentState(request, o.getBody(EndpointState.class), mgr);
                }, mgr)
                .sendWith(this);

    }

    private void thenWithParentState(ComputeEnumerateResourceRequest request, EndpointState endpoint, TaskManager mgr) {

        VsphereEnumerationHelper.getComputeStateDescription(getHost(), request.resourceReference)
                .thenCompose(computeState -> {
                    this.parent = computeState;
                    return collectAllEndpointResources(request, computeState.documentSelfLink);
                })
                .thenAccept(resourceLinks -> {
                    VSphereIOThreadPool pool = VSphereIOThreadPoolAllocator.getPool(this);
                    this.logInfo("Submitting enumeration job to thread pool for endpoint %s", request.endpointLink);
                    pool.submit(this, this.parent.adapterManagementReference, endpoint.authCredentialsLink,
                            (connection, e) -> {
                                if (e != null) {
                                    String msg = String.format("Cannot establish connection to %s", this.parent.adapterManagementReference);
                                    logWarning(msg);
                                    mgr.patchTaskToFailure(msg, e);
                                } else {
                                    // create an un-managed connection and set it to instance variable.
                                    this.connection = connection.createUnmanagedCopy();
                                    refreshResourcesOnce(resourceLinks, request, this.connection, this.parent, mgr);
                                }
                            });
                });
    }

    @Override
    public void handlePatch(Operation patch) {
        // complete the patch immediately.
        patch.complete();
        logInfo("Received PATCH for incremental enumeration.");
        VSphereIncrementalEnumerationRequest enumerationRequest = patch
                .getBody(VSphereIncrementalEnumerationRequest.class);
        ComputeEnumerateResourceRequest request = enumerationRequest.request;
        URI parentUri = ComputeService.ComputeStateWithDescription.buildUri(
                PhotonModelUriUtils.createInventoryUri(getHost(), request.resourceReference));
        logInfo("Creating task manager.");
        TaskManager mgr = new TaskManager(this, request.taskReference, request.resourceLink());
        logInfo(" Requesting GET on compute state with description.");
        Operation.createGet(parentUri)
                .setCompletion(o -> {
                    logInfo("Submitting job to threadpool.");
                    VsphereEnumerationHelper.submitWorkToVSpherePool(this, () -> {
                        logInfo("Incremental enumeration job started for endpoint %s", enumerationRequest.request.endpointLink);

                        ComputeStateWithDescription computeStateWithDesc =
                                o.getBody(ComputeStateWithDescription.class);
                        VapiConnection vapiConnection = VapiConnection.createFromVimConnection(this.connection);
                        logInfo("Establishing VAPI connection for endpoint %s", enumerationRequest.request.endpointLink);
                        try {
                            vapiConnection.login();
                        } catch (IOException | RpcException rpce) {
                            logWarning(() -> String.format("Cannot login into vAPI endpoint: %s",
                                    Utils.toString(rpce)));
                            mgr.patchTaskToFailure(rpce);
                            // self delete service so that full enumeration kicks in next invocation.
                            selfDeleteService();
                            return;
                        }
                        try {
                            // Get instanceUuid of the vCenter
                            AboutInfo vCenter = this.connection.getServiceContent().getAbout();
                            EnumerationClient enumerationClient = new EnumerationClient(this.connection);
                            EnumerationProgress ctx = new EnumerationProgress(Collections.emptySet(), request,
                                    computeStateWithDesc, vapiConnection, null, vCenter.getInstanceUuid());
                            //sync storage profiles
                            logInfo("Syncing storage profiles for vcenter [%s]", vCenter.getInstanceUuid());
                            VsphereStoragePolicyEnumerationHelper.syncStorageProfiles(this, enumerationClient, ctx);

                            for (CollectorDetails collectorDetails : this.collectors) {
                                logInfo("Retrieving  resources incremental data for data center: %s",
                                        collectorDetails.datacenter);

                                EnumerationProgress enumerationProgress = new EnumerationProgress(new HashSet<>(), request,
                                        computeStateWithDesc, vapiConnection, collectorDetails.datacenter,
                                        vCenter.getInstanceUuid());

                                enumerationProgress.setDisksToStoragePolicyMap(ctx.getDiskToStoragePolicyAssociationMap());
                                enumerationProgress.setDataStoresToStoragePolicyMap(ctx.getDataStoresToStoragePolicyMap());

                                EnumerationClient client = new EnumerationClient(this.connection, computeStateWithDesc,
                                        VimUtils.convertStringToMoRef(collectorDetails.datacenter));

                                List<ObjectUpdate> resourcesUpdates = collectResourcesData(collectorDetails);

                                List<ObjectUpdate> vmUpdates = collectVMData(collectorDetails);
                                logInfo("Received resources updates for datacenter: %s : %s",
                                        collectorDetails.datacenter, resourcesUpdates.size());
                                logInfo("Received vm updates for datacenter: %s : %s",
                                        collectorDetails.datacenter, vmUpdates.size());

                                logInfo("Resources Updates: %s", Utils.toJson(resourcesUpdates));

                                logInfo("VM Updates: %s", Utils.toJson(vmUpdates));

                                if (!resourcesUpdates.isEmpty()) {
                                    SegregatedOverlays segregatedOverlays =
                                            segregateObjectUpdates(enumerationProgress, resourcesUpdates);
                                    this.logInfo("Processing incremental changes for folders for datacenter [%s]", collectorDetails.datacenter);
                                    VsphereFolderEnumerationHelper.handleFolderChanges(this, segregatedOverlays.folders, enumerationProgress, client);
                                    logInfo("Processing incremental changes for networks for datacenter [%s]", collectorDetails.datacenter);
                                    VSphereNetworkEnumerationHelper
                                            .handleNetworkChanges(this, segregatedOverlays.networks, enumerationProgress, client);
                                    logInfo("Processing incremental changes for Datastores for datacenter [%s]", collectorDetails.datacenter);
                                    VsphereDatastoreEnumerationHelper
                                            .handleDatastoreChanges(this, segregatedOverlays.datastores, enumerationProgress);
                                    logInfo("Processing incremental changes for compute resource for datacenter [%s]", collectorDetails.datacenter);
                                    VsphereComputeResourceEnumerationHelper
                                            .handleComputeResourceChanges(this, segregatedOverlays.clusters, enumerationProgress, client, segregatedOverlays.hosts);
                                    logInfo("Processing incremental changes for host system for datacenter [%s]", collectorDetails.datacenter);
                                    VSphereHostSystemEnumerationHelper
                                            .handleHostSystemChanges(this, segregatedOverlays.hosts, enumerationProgress, client);
                                    logInfo("Processing incremental changes for resource pool for datacenter [%s]", collectorDetails.datacenter);
                                    VSphereResourcePoolEnumerationHelper
                                            .handleResourcePoolChanges(this, segregatedOverlays.resourcePools, enumerationProgress, client);
                                }
                                if (!vmUpdates.isEmpty()) {
                                    logInfo("Processing incremental changes for virtual machines for datacenter [%s]", collectorDetails.datacenter);
                                    VSphereVirtualMachineEnumerationHelper.handleVMChanges(this, vmUpdates, enumerationProgress, client);
                                }
                            }
                            mgr.patchTask(TaskStage.FINISHED);
                        } catch (Exception exception) {
                            String msg = "Error processing PropertyCollector results during incremental retrieval";
                            logWarning(() -> msg + ": " + exception.toString());
                            mgr.patchTaskToFailure(exception);
                            // self delete service so that full enumeration kicks in next invocation.
                            //TODO: This is not complete. We need to enable owner selection on this service.
                            selfDeleteService();
                            return;
                        } finally {
                            vapiConnection.close();
                        }
                    });
                }, mgr).sendWith(this);
    }

    private void selfDeleteService() {
        this.sendRequest(Operation.createDelete(this.getUri()));
    }

    private void cleanupConnection() {
        logFine("Destroying property collectors for endpoint : " + this.getUri().getPath());
        for (CollectorDetails collector : this.collectors) {
            try {
                this.connection.getVimPort().destroyPropertyCollector(collector.resourcesPropertyCollector);
                this.connection.getVimPort().destroyPropertyCollector(collector.vmPropertyCollector);
            } catch (RuntimeFaultFaultMsg runtimeFaultFaultMsg) {
                logSevere("Error while destroying property collectors for endpoint : " + this.getUri().getPath());
                logSevere(runtimeFaultFaultMsg);
            }
        }
        logFine("Closing connection for endpoint : " + this.getUri().getPath());
        this.connection.closeQuietly();
    }

    private DeferredResult<Set<String>> collectAllEndpointResources(ComputeEnumerateResourceRequest req,
                                                                    String parentLink) {
        QueryTask.Query combinedClause = new QueryTask.Query()
                .setOccurance(Occurance.MUST_OCCUR);

        // The next clauses are the boolean clauses that will be added to the
        // combinedClause. At the top, all further queries should have SHOULD_OCCUR
        QueryTask.Query resourceClause = QueryTask.Query.Builder.create()
                .addFieldClause(ResourceState.FIELD_NAME_ENDPOINT_LINK, req.endpointLink)
                .addFieldClause(ComputeService.ComputeState.FIELD_NAME_LIFECYCLE_STATE, ComputeService.LifecycleState.PROVISIONING.toString(),
                        QueryTask.QueryTerm.MatchType.TERM, QueryTask.Query.Occurance.MUST_NOT_OCCUR)
                .addFieldClause(
                        ServiceDocument.FIELD_NAME_SELF_LINK, parentLink, QueryTask.Query.Occurance.MUST_NOT_OCCUR)
                .addInClause(ServiceDocument.FIELD_NAME_KIND, Arrays.asList(
                        Utils.buildKind(ComputeService.ComputeState.class),
                        Utils.buildKind(NetworkService.NetworkState.class),
                        Utils.buildKind(StorageDescriptionService.StorageDescription.class),
                        Utils.buildKind(SubnetService.SubnetState.class))).build()
                .setOccurance(Occurance.SHOULD_OCCUR);

        // The below two queries are added to get the Folders and Datacenters that are enumerated
        // They are persisted as ResourceGroupState documents, and there are other documents of the same
        // kind (which have a different lifecycle), we're filtering on the "__computeType" property
        // Adding these documents here will enable automatic deletion of "untouched" resources in
        // the further logic

        QueryTask.Query folderClause = QueryTask.Query.Builder.create()
                .addCompositeFieldClause(ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                        CustomProperties.TYPE, VimNames.TYPE_FOLDER)
                .addKindFieldClause(ResourceGroupState.class).build()
                .setOccurance(Occurance.SHOULD_OCCUR);

        QueryTask.Query datacenterClause = QueryTask.Query.Builder.create()
                .addCompositeFieldClause(ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                        CustomProperties.TYPE, VimNames.TYPE_DATACENTER)
                .addKindFieldClause(ResourceGroupState.class).build()
                .setOccurance(Occurance.SHOULD_OCCUR);

        // Add all the clauses to the combined clause
        // The query structure is now --> MUST(SHOULD A + SHOULD B + SHOULD C)
        // where A, B, C are independent queries
        combinedClause
                .addBooleanClause(resourceClause)
                .addBooleanClause(folderClause)
                .addBooleanClause(datacenterClause);

        QueryTask task = QueryTask.Builder.createDirectTask()
                .setQuery(combinedClause)
                .setResultLimit(QueryUtils.DEFAULT_RESULT_LIMIT)
                .build();

        DeferredResult<Set<String>> res = new DeferredResult<>();
        Set<String> links = new ConcurrentSkipListSet<>();

        QueryUtils.startInventoryQueryTask(this, task)
                .whenComplete((result, e) -> {
                    if (e != null) {
                        res.complete(new HashSet<>());
                        return;
                    }

                    if (result.results.nextPageLink == null) {
                        res.complete(links);
                        return;
                    }

                    Operation.createGet(PhotonModelUriUtils
                            .createInventoryUri(getHost(), result.results.nextPageLink))
                            .setCompletion(makeCompletion(links, res))
                            .sendWith(this);
                });

        return res;
    }

    private Operation.CompletionHandler makeCompletion(Set<String> imageLinks, DeferredResult<Set<String>> res) {
        return (o, e) -> {
            if (e != null) {
                res.complete(imageLinks);
                return;
            }

            QueryTask qt = o.getBody(QueryTask.class);
            imageLinks.addAll(qt.results.documentLinks);

            if (qt.results.nextPageLink == null) {
                res.complete(imageLinks);
            } else {
                Operation.createGet(PhotonModelUriUtils.createInventoryUri(getHost(), qt.results.nextPageLink))
                        .setCompletion(makeCompletion(imageLinks, res))
                        .sendWith(this);
            }
        };
    }

    /**
     * This method executes in a thread managed by {@link VSphereIOThreadPoolAllocator}
     */
    private void refreshResourcesOnce(
            Set<String> resourceLinks, ComputeEnumerateResourceRequest request,
            Connection connection,
            ComputeService.ComputeStateWithDescription parent,
            TaskManager mgr) {

        VapiConnection vapiConnection = VapiConnection.createFromVimConnection(connection);

        try {
            vapiConnection.login();
        } catch (IOException | RpcException e) {
            logWarning(() -> String.format("Cannot login into vAPI endpoint: %s", Utils.toString(e)));
            mgr.patchTaskToFailure(e);
            return;
        }

        DatacenterLister lister = new DatacenterLister(connection);
        try {
            // Get instanceUuid of the vCenter
            AboutInfo vCenter = this.connection.getServiceContent().getAbout();
            EnumerationClient enumerationClient = new EnumerationClient(connection);
            EnumerationProgress ctx = new EnumerationProgress(resourceLinks, request,
                    parent, vapiConnection, null, vCenter.getInstanceUuid());
            List<StoragePolicyOverlay> storagePolicies = VsphereStoragePolicyEnumerationHelper
                    .createStorageProfileOverlays(this, enumerationClient);
            // Process found storage policy, it is related to datastore. Hence process it after
            // datastore processing is complete.
            if (storagePolicies.size() > 0) {
                ctx.expectStoragePolicyCount(storagePolicies.size());
                for (StoragePolicyOverlay sp : storagePolicies) {
                    VsphereStoragePolicyEnumerationHelper.processFoundStoragePolicy(this, ctx, sp, enumerationClient);
                }

                // checkpoint for storage policy
                try {
                    ctx.getStoragePolicyTracker().await();
                } catch (InterruptedException e) {
                    threadInterrupted(mgr, e);
                    return;
                }
            }

            for (Element element : lister.listAllDatacenters()) {
                ManagedObjectReference datacenter = element.object;
                log(Level.INFO, "Processing datacenter %s (%s)", element.path,
                        VimUtils.convertMoRefToString(element.object));

                EnumerationClient client = new EnumerationClient(connection, parent, datacenter);

                EnumerationProgress enumerationProgress = new EnumerationProgress(resourceLinks, request,
                        parent, vapiConnection, VimUtils.convertMoRefToString(datacenter), vCenter.getInstanceUuid());
                // set the storage policy associations built above to every enumeration progress object.
                enumerationProgress.setDataStoresToStoragePolicyMap(ctx.getDataStoresToStoragePolicyMap());
                enumerationProgress.setDisksToStoragePolicyMap(ctx.getDiskToStoragePolicyAssociationMap());

                enumerationProgress.expectDatacenterCount(1); //since we are processing DC sequentially one at a time
                VsphereDatacenterEnumerationHelper.processDatacenterInfo(this, element, enumerationProgress);
                try {
                    enumerationProgress.getDcTracker().await();
                } catch (InterruptedException e) {
                    threadInterrupted(mgr, e);
                    return;
                }
                logInfo("Proceeding to refresh resources on datacenter: %s", enumerationProgress.getDcLink());
                refreshResourcesOnDatacenter(client, enumerationProgress, mgr);
            }
        } catch (Exception e) {
            logWarning(String.format("Error during enumeration: %s", Utils.toString(e)));
            mgr.patchTaskToFailure(e);
            return;
        }

        try {
            vapiConnection.close();
        } catch (Exception e) {
            logWarning(() -> String.format("Error occurred when closing vAPI connection: %s",
                    Utils.toString(e)));
        }

        // after all dc's are enumerated untouched resource links are the only ones left
        // in resourceLinks
        garbageCollectUntouchedComputeResources(request, resourceLinks, mgr);
        // cleanup the connection if the enumeration action is REFRESH.
        if (EnumerationAction.REFRESH.equals(request.enumerationAction)) {
            cleanupConnection();
        }
    }

    private List<ObjectUpdate> collectResourcesData(CollectorDetails collectorDetails) {
        EnumerationClient.ObjectUpdateIterator resourcesIterator =
                new EnumerationClient.ObjectUpdateIterator(
                        collectorDetails.resourcesPropertyCollector, this.connection.getVimPort(),
                        collectorDetails.resourcesCollectorVersion);
        List<ObjectUpdate> updates = collectUpdates(resourcesIterator);
        //update the version number soon after iterating
        collectorDetails.resourcesCollectorVersion = resourcesIterator.getVersion();
        return updates;
    }

    private List<ObjectUpdate> collectUpdates(ObjectUpdateIterator objectUpdateIterator) {
        List<ObjectUpdate> updates = new ArrayList<>();
        while (objectUpdateIterator.hasNext()) {
            UpdateSet page = objectUpdateIterator.next();
            if (null != page) {
                for (PropertyFilterUpdate propertyFilterUpdate : page.getFilterSet()) {
                    updates.addAll(propertyFilterUpdate.getObjectSet());
                }
            }
        }
        return updates;
    }

    private List<ObjectUpdate> collectVMData(CollectorDetails collectorDetails) {
        EnumerationClient.ObjectUpdateIterator vmIterator =
                new EnumerationClient.ObjectUpdateIterator(
                        collectorDetails.vmPropertyCollector, this.connection.getVimPort(),
                        collectorDetails.vmCollectorVersion);
        List<ObjectUpdate> updates = collectUpdates(vmIterator);
        //update the version number soon after iterating
        collectorDetails.vmCollectorVersion = vmIterator.getVersion();
        return updates;
    }

    private void refreshResourcesOnDatacenter(EnumerationClient client, EnumerationProgress ctx,
                                              TaskManager mgr) throws Exception {
        Set<String> sharedDatastores = new HashSet<>();

        // put results in different buckets by type
        PropertyFilterSpec spec = client.createResourcesFilterSpec();
        CollectorDetails collectorDetails = new CollectorDetails();
        EnumerationClient.ObjectUpdateIterator resourcesIterator;
        SegregatedOverlays segregatedOverlays;
        logInfo("Processing resources on datacenter: %s", ctx.getDcLink());
        try {
            ManagedObjectReference resourcesPropertyCollector = client.createPropertyCollectorWithFilter(spec);
            // remove getObjectIterator API
            resourcesIterator = new ObjectUpdateIterator(resourcesPropertyCollector, this.connection.getVimPort(), "");
            List<ObjectUpdate> updates = new ArrayList<>();
            while (resourcesIterator.hasNext()) {
                UpdateSet page = resourcesIterator.next();
                if (null != page) {
                    for (PropertyFilterUpdate propertyFilterUpdate : page.getFilterSet()) {
                        updates.addAll(propertyFilterUpdate.getObjectSet());
                    }
                }
            }
            segregatedOverlays = segregateObjectUpdates(ctx, updates);
        } catch (Exception e) {
            String msg = "Error processing PropertyCollector results";
            logWarning(() -> msg + ": " + e.toString());
            throw new Exception(e);
        }

        // This will split the folders into two lists. Default folders (vm, host, network, datastore) have parent as datacenter
        // and are not visible in vCenter. These need not be persisted. Any other folders will have another folder as a parent
        // and will be persisted with  appropriate parent.
        // partitioningBy will always return a map with two entries, one for where the predicate is true and one for
        // where it is false. Even though the entries can be empty, they will be present in the map , i.e. map size will be
        // always be two

        Map<Boolean, List<FolderOverlay>> folderMap = new HashMap<>(segregatedOverlays.folders.stream()
                .collect(Collectors.partitioningBy(s -> s.getParent().getType().equals(VimNames.TYPE_DATACENTER))));

        // Process true Folder and root folder list
        List<FolderOverlay> trueFolders = folderMap.get(Boolean.FALSE);
        List<FolderOverlay> rootFolders = folderMap.get(Boolean.TRUE);
        ctx.expectFolderCount(trueFolders.size());
        logInfo("Processing folders on datacenter: %s", ctx.getDcLink());
        for (FolderOverlay folder : trueFolders) {
            try {
                // The parent list will be passed along. This is to achieve the below
                // Folder A is root folder and has datacenter as the parent.
                // Folder Ac has parent as A. Since 'A' is not persisted anymore, 'Ac'
                // should have the datacenter as its parent.
                VsphereFolderEnumerationHelper.processFoundFolder(this, ctx, folder, rootFolders, client);
            } catch (Exception e) {
                logWarning(() -> "Error processing folder information : " + e.toString());
            }
        }


        // Process HostSystem list to get the datastore access level whether local / shared
        try {
            for (HostSystemOverlay hs : segregatedOverlays.hosts) {
                sharedDatastores.addAll(client.getDatastoresHostMountInfo(hs));
            }
        } catch (Exception e) {
            // We can continue as we will not know whether the datastore is local or shared which
            // is ok to proceed.
            logWarning(() -> "Error processing datastore host mount information : " + e.toString());
        }

        // process results in topological order
        ctx.expectNetworkCount(segregatedOverlays.networks.size());
        logInfo("Processing network on datacenter: %s", ctx.getDcLink());
        for (NetworkOverlay net : segregatedOverlays.networks.values()) {
            VSphereNetworkEnumerationHelper
                    .processFoundNetwork(this, ctx, net, segregatedOverlays.networks);
        }

        ctx.expectDatastoreCount(segregatedOverlays.datastores.size());
        logInfo("Processing datastore on datacenter: %s", ctx.getDcLink());
        for (DatastoreOverlay ds : segregatedOverlays.datastores) {
            ds.setMultipleHostAccess(sharedDatastores.contains(ds.getName()));
            VsphereDatastoreEnumerationHelper
                    .processFoundDatastore(this, ctx, ds);
        }

        // checkpoint net & storage, they are not related currently
        try {
            ctx.getDatastoreTracker().await();
            ctx.getNetworkTracker().await();
            ctx.getFolderTracker().await();
        } catch (InterruptedException e) {
            threadInterrupted(mgr, e);
            return;
        }

        ctx.expectComputeResourceCount(segregatedOverlays.clusters.size());
        for (ComputeResourceOverlay cluster : segregatedOverlays.clusters) {
            ctx.track(cluster);
            cluster.markHostAsClustered(segregatedOverlays.hosts);
            VsphereComputeResourceEnumerationHelper.processFoundComputeResource(this, ctx, cluster, client);
        }

        // checkpoint compute
        try {
            ctx.getComputeResourceTracker().await();
        } catch (InterruptedException e) {
            threadInterrupted(mgr, e);
            return;
        }

        // process clustered as well as non-clustered hosts
        ctx.expectHostSystemCount(segregatedOverlays.hosts.size());
        logInfo("Processing hosts on datacenter: %s", ctx.getDcLink());
        for (HostSystemOverlay hs : segregatedOverlays.hosts) {
            ctx.track(hs);
            VSphereHostSystemEnumerationHelper.processFoundHostSystem(this, ctx, hs, client);
        }

        // exclude all root resource pools
        // no need to collect the root resource pool
        segregatedOverlays.resourcePools.removeIf(rp -> !VimNames.TYPE_RESOURCE_POOL.equals(rp.getParent().getType()));

        MoRefKeyedMap<String> computeResourceNamesByMoref = collectComputeNames(segregatedOverlays.hosts, segregatedOverlays.clusters);
        ctx.expectResourcePoolCount(segregatedOverlays.resourcePools.size());
        logInfo("Processing resource pools on datacenter: %s", ctx.getDcLink());
        for (ResourcePoolOverlay rp : segregatedOverlays.resourcePools) {
            String ownerName = computeResourceNamesByMoref.get(rp.getOwner());
            VSphereResourcePoolEnumerationHelper.processFoundResourcePool(this, ctx, rp, ownerName, client);
        }

        // checkpoint compute
        try {
            ctx.getHostSystemTracker().await();
            ctx.getResourcePoolTracker().await();
        } catch (InterruptedException e) {
            threadInterrupted(mgr, e);
            return;
        }

        logInfo("Updating server disks on datacenter: %s", ctx.getDcLink());
        //update server disks with selfLinks of HostSystem
        for (HostSystemOverlay hostSystemOverlay : segregatedOverlays.hosts) {
            updateServerDisks(ctx, hostSystemOverlay);
        }


        logInfo("Processing VMs on datacenter: %s", ctx.getDcLink());
        spec = client.createVmFilterSpec(client.getDatacenter());
        List<VmOverlay> vmOverlayList = new ArrayList<>();
        EnumerationClient.ObjectUpdateIterator vmIterator;
        try {
            ManagedObjectReference vmPropertyCollector = client.createPropertyCollectorWithFilter(spec);
            vmIterator = new ObjectUpdateIterator(vmPropertyCollector, this.connection.getVimPort(), "");
            while (vmIterator.hasNext()) {
                UpdateSet page = vmIterator.next();
                if (null != page) {
                    for (PropertyFilterUpdate propertyFilterUpdate : page.getFilterSet()) {
                        ctx.resetVmTracker();
                        for (ObjectUpdate cont : propertyFilterUpdate.getObjectSet()) {
                            if (!VimUtils.isVirtualMachine(cont.getObj())) {
                                continue;
                            }
                            VmOverlay vm = new VmOverlay(cont);
                            if (vm.isTemplate()) {
                                // templates are skipped, enumerated as "images" instead
                                continue;
                            }
                            if (vm.getInstanceUuid() == null) {
                                logWarning(() -> String.format("Cannot process a VM without"
                                                + " instanceUuid: %s",
                                        VimUtils.convertMoRefToString(vm.getId())));
                            } else {
                                ctx.getVmTracker().register();
                                vmOverlayList.add(vm);
                                VSphereVirtualMachineEnumerationHelper.processFoundVm(this, ctx, vm);
                            }
                        }
                        ctx.getVmTracker().arriveAndAwaitAdvance();
                        VSphereVMSnapshotEnumerationHelper.enumerateSnapshots(this, ctx, vmOverlayList);
                    }
                }
            }
        } catch (Exception e) {
            String msg = "Error processing PropertyCollector results during vm enumeration";
            logWarning(() -> msg + ": " + e.toString());
            throw new Exception(e);
        }

        // Sync disks deleted in vSphere
        deleteIndependentDisksUnavailableInVSphere(ctx, client);
        try {
            ctx.getDeleteDiskTracker().await();
        } catch (InterruptedException e) {
            threadInterrupted(mgr, e);
            return;
        }

        // if enumeration action is start and this is the initial enumeration, then store the property collectors and versions.
        if (EnumerationAction.START == ctx.getRequest().enumerationAction) {
            collectorDetails.vmCollectorVersion = vmIterator.getVersion();
            collectorDetails.vmPropertyCollector = vmIterator.getPropertyCollector();
            collectorDetails.resourcesCollectorVersion = resourcesIterator.getVersion();
            collectorDetails.resourcesPropertyCollector = resourcesIterator.getPropertyCollector();
            collectorDetails.datacenter = ctx.getRegionId();
            this.collectors.add(collectorDetails);
        }
    }

    private void updateServerDisks(EnumerationProgress ctx, HostSystemOverlay hostSystemOverlay) {
        QueryTask queryTask = queryForServerDisks(ctx, hostSystemOverlay);
        withTaskResults(this, queryTask, null, serviceDocumentQueryResult -> {
            if (!serviceDocumentQueryResult.documentLinks.isEmpty()) {
                DiskService.DiskState oldDocument = convertOnlyResultToDocument(serviceDocumentQueryResult, DiskService.DiskState.class);
                updateDiskState(ctx, oldDocument, hostSystemOverlay);
            }
        });
    }

    private void updateDiskState(EnumerationProgress ctx, DiskState oldDocument, HostSystemOverlay hostSystemOverlay) {
        CustomProperties.of(oldDocument)
                .put(ComputeProperties.COMPUTE_HOST_LINK_PROP_NAME, ctx.getHostSystemTracker().getSelfLink(hostSystemOverlay.getId()));
        Operation.createPatch(PhotonModelUriUtils.createInventoryUri(this.getHost(), oldDocument.documentSelfLink))
                .setBody(oldDocument)
                .sendWith(this);
    }

    /**
     * Queries the server disk states which have give host as server.
     *
     * @param ctx               the enumeration context
     * @param hostSystemOverlay the host system whose server disks need to be queried
     * @return query task object.
     */
    private static QueryTask queryForServerDisks(EnumerationProgress ctx, HostSystemOverlay hostSystemOverlay) {
        QueryTask.Query.Builder builder = QueryTask.Query.Builder.create()
                .addKindFieldClause(DiskService.DiskState.class)
                .addCompositeFieldClause(ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                        CustomProperties.TYPE, TYPE_SERVER_DISK)
                .addCompositeFieldClause(ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                        CustomProperties.PARENT_ID, VimUtils.convertMoRefToString(hostSystemOverlay.getId()));
        QueryUtils.addEndpointLink(builder, DiskService.DiskState.class,
                ctx.getRequest().endpointLink);

        QueryUtils.addTenantLinks(builder, ctx.getTenantLinks());
        return QueryTask.Builder.createDirectTask()
                .setQuery(builder.build())
                .build();
    }

    private SegregatedOverlays segregateObjectUpdates(
            EnumerationProgress ctx,
            List<ObjectUpdate> updates) {
        SegregatedOverlays segregatedOverlays = new SegregatedOverlays();
        for (ObjectUpdate cont : updates) {
            if (VimUtils.isNetwork(cont.getObj())) {
                NetworkOverlay net = new NetworkOverlay(cont);
                ctx.track(net);
                String nameOrNull = net.getNameOrNull();
                /*add overlay if name is null or name doesn't contain dvuplinks
                When a DV port group is removed, we do not get name but we have to process it
                i.e. remove the subnet document from photon-model
                for that we need to add it to segregatedOverlays.*/
                if ((null == nameOrNull) || (!nameOrNull.toLowerCase().contains("dvuplinks"))) {
                    // TODO starting with 6.5 query the property config.uplink instead
                    segregatedOverlays.networks.put(net.getId(), net);
                }
            } else if (VimUtils.isHost(cont.getObj())) {
                // this includes all standalone and clustered hosts
                HostSystemOverlay hs = new HostSystemOverlay(cont);
                segregatedOverlays.hosts.add(hs);
            } else if (VimUtils.isComputeResource(cont.getObj())) {
                ComputeResourceOverlay cr = new ComputeResourceOverlay(cont);
                if ((ObjectUpdateKind.ENTER.equals(cr.getObjectUpdateKind()) && cr.isDrsEnabled())
                        || (!ObjectUpdateKind.ENTER.equals(cr.getObjectUpdateKind()))) {
                    // when DRS is enabled add the cluster itself and skip the hosts
                    // if a cluster is modified or deleted, add it to overlays
                    segregatedOverlays.clusters.add(cr);
                } else {
                    // ignore non-clusters and non-drs cluster: they are handled as hosts
                    continue;
                }
            } else if (VimUtils.isDatastore(cont.getObj())) {
                DatastoreOverlay ds = new DatastoreOverlay(cont);
                segregatedOverlays.datastores.add(ds);
            } else if (VimUtils.isResourcePool(cont.getObj())) {
                ResourcePoolOverlay rp = new ResourcePoolOverlay(cont);
                segregatedOverlays.resourcePools.add(rp);
            } else if (VimUtils.isFolder(cont.getObj())) {
                FolderOverlay folder = new FolderOverlay(cont);
                segregatedOverlays.folders.add(folder);
            }
        }

        return segregatedOverlays;
    }

    /**
     * Collect the names of all hosts and cluster indexed by the string representation
     * of their moref.
     * Used to construct user-friendly resource pool names without fetching their owner's name again.
     *
     * @param hosts            list of host overlays
     * @param computeResources list of compute resource overlays
     * @return a MoRefKeyedMap containing ID as key and overlay object as value for both host and compute resources.
     */
    private MoRefKeyedMap<String> collectComputeNames(List<HostSystemOverlay> hosts,
                                                      List<ComputeResourceOverlay> computeResources) {
        MoRefKeyedMap<String> computeResourceNamesByMoref = new MoRefKeyedMap<>();
        for (HostSystemOverlay host : hosts) {
            computeResourceNamesByMoref.put(host.getId(), host.getName());
        }
        for (ComputeResourceOverlay cr : computeResources) {
            computeResourceNamesByMoref.put(cr.getId(), cr.getName());
        }

        return computeResourceNamesByMoref;
    }

    private void garbageCollectUntouchedComputeResources(ComputeEnumerateResourceRequest request,
                                                         Set<String> untouchedResources, TaskManager mgr) {
        if (untouchedResources.isEmpty()) {
            mgr.patchTask(TaskStage.FINISHED);
            return;
        }

        if (!request.preserveMissing) {
            // delete dependent resources without waiting for response
            for (String resourceLink : untouchedResources) {
                Operation.createDelete(
                        PhotonModelUriUtils.createInventoryUri(getHost(), resourceLink))
                        .sendWith(this);
            }
            mgr.patchTask(TaskStage.FINISHED);
            return;
        }

        List<Operation> deleteOps = new ArrayList<>();
        for (String resourceLink : untouchedResources) {
            if (resourceLink.startsWith(ComputeService.FACTORY_LINK)) {
                ResourceCleanRequest patch = new ResourceCleanRequest();
                patch.resourceLink = resourceLink;
                deleteOps.add(Operation.createPatch(this, VSphereUriPaths.RESOURCE_CLEANER)
                        .setBody(patch));
            } else {
                deleteOps.add(Operation.createDelete(
                        PhotonModelUriUtils.createInventoryUri(getHost(), resourceLink)));
            }
        }

        OperationJoin.create(deleteOps)
                .setCompletion((os, es) -> mgr.patchTask(TaskStage.FINISHED))
                .sendWith(this);
    }

    private void threadInterrupted(TaskManager mgr, InterruptedException e) {
        String msg = "Enumeration thread was interrupted";
        logWarning(msg);
        mgr.patchTaskToFailure(msg, e);
    }

    private void validate(ComputeEnumerateResourceRequest request) {
        // assume all request are REFRESH requests
        if (request.enumerationAction == null) {
            request.enumerationAction = EnumerationAction.REFRESH;
        }
    }

    /**
     * Checks the existence of the independent disks in vSphere and updates the local DiskService states.
     */
    private void deleteIndependentDisksUnavailableInVSphere(EnumerationProgress ctx, EnumerationClient client) {
        QueryTask task = queryAvailableDisks(ctx);
        withTaskResults(this, task, ctx.getDeleteDiskTracker(), result -> {
            if (result.documentLinks.isEmpty()) {
                // no independent disks
                ctx.getDeleteDiskTracker().track();
                return;
            }
            List<String> unAvailableDisks = client.queryDisksAvailabilityinVSphere(result.documents);
            if (unAvailableDisks.isEmpty()) {
                // no unavailable disks to delete
                ctx.getDeleteDiskTracker().track();
                return;
            }
            deleteDisks(ctx, unAvailableDisks);
        }, 0);
    }

    private QueryTask queryAvailableDisks(EnumerationProgress ctx) {
        QueryTask.Query.Builder builder = QueryTask.Query.Builder.create()
                .addKindFieldClause(DiskService.DiskState.class)
                .addFieldClause(DiskService.DiskState.FIELD_NAME_STATUS, DiskService.DiskStatus.AVAILABLE)
                .addFieldClause(DiskService.DiskState.FIELD_NAME_REGION_ID, ctx.getRegionId());
        QueryUtils.addEndpointLink(builder, DiskService.DiskState.class, ctx.getRequest().endpointLink);
        QueryUtils.addTenantLinks(builder, ctx.getTenantLinks());
        return QueryTask.Builder.createDirectTask()
                .setQuery(builder.build())
                .addOption(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT)
                .build();
    }

    private void deleteDisks(EnumerationProgress ctx, List<String> unavailableDisks) {
        List<DeferredResult<Operation>> ops = unavailableDisks
                .stream()
                .map(uri -> this.sendWithDeferredResult(
                        Operation.createDelete(PhotonModelUriUtils.createInventoryUri(getHost(), uri))))
                .collect(Collectors.toList());
        DeferredResult.allOf(ops).whenComplete((o, e) -> {
            if (e != null) {
                logWarning(() -> String.format("Failed syncing disks deleted in vSphere %s", e.getMessage()));
            } else {
                logFine("Success syncing disks deleted in vSphere");
            }
            ctx.getDeleteDiskTracker().track();
        });
    }
}
