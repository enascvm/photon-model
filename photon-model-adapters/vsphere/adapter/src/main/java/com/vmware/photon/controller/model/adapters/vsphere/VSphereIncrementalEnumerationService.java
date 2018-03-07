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

import static com.vmware.photon.controller.model.adapters.vsphere.VsphereEnumerationHelper.withTaskResults;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.vmware.pbm.PbmProfile;
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
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.StorageDescriptionService;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.util.PhotonModelUriUtils;
import com.vmware.vim25.AboutInfo;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.ObjectUpdate;
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
    private String vcUuid;

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

        URI parentUri = ComputeService.ComputeStateWithDescription.buildUri(PhotonModelUriUtils.createInventoryUri(getHost(),
                request.resourceReference));

        Operation.createGet(parentUri)
                .setCompletion(o -> {
                    thenWithParentState(request, o.getBody(ComputeService.ComputeStateWithDescription.class), mgr);
                }, mgr)
                .sendWith(this);
    }

    private void thenWithParentState(ComputeEnumerateResourceRequest request,
                                     ComputeService.ComputeStateWithDescription parent, TaskManager mgr) {
        collectAllEndpointResources(request, parent.documentSelfLink).thenAccept(resourceLinks -> {
            VSphereIOThreadPool pool = VSphereIOThreadPoolAllocator.getPool(this);
            pool.submit(this, parent.adapterManagementReference,
                    parent.description.authCredentialsLink,
                    (connection, e) -> {
                        if (e != null) {
                            String msg = String.format("Cannot establish connection to %s",
                                    parent.adapterManagementReference);
                            logWarning(msg);
                            mgr.patchTaskToFailure(msg, e);
                        } else {
                            // create an un-managed connection and set it to instance variable.
                            this.connection = connection.createUnmanagedCopy();
                            refreshResourcesOnce(resourceLinks, request, this.connection, parent, mgr);
                        }
                    });
        });
    }

    @Override
    public void handlePatch(Operation patch) {
        // complete the patch immediately.
        patch.complete();
        VsphereEnumerationHelper.submitWorkToVSpherePool(this, () -> {
            VSphereIncrementalEnumerationRequest enumerationRequest = patch
                    .getBody(VSphereIncrementalEnumerationRequest.class);

            ComputeEnumerateResourceRequest request = enumerationRequest.request;
            URI parentUri = ComputeService.ComputeStateWithDescription.buildUri(
                    PhotonModelUriUtils.createInventoryUri(getHost(), request.resourceReference));

            TaskManager mgr = new TaskManager(this, request.taskReference, request.resourceLink());
            Operation.createGet(parentUri)
                    .setCompletion(o -> {
                        ComputeStateWithDescription computeStateWithDesc =
                                o.getBody(ComputeStateWithDescription.class);
                        VapiConnection vapiConnection = VapiConnection.createFromVimConnection(this.connection);

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
                            for (CollectorDetails collectorDetails : this.collectors) {
                                logInfo("Retrieving  resources incremental data for data center: %s",
                                        collectorDetails.datacenter);

                                EnumerationProgress enumerationProgress = new EnumerationProgress(
                                        new HashSet<>(), request, computeStateWithDesc, vapiConnection, collectorDetails.datacenter);

                                EnumerationClient client = new EnumerationClient(this.connection, computeStateWithDesc,
                                        VimUtils.convertStringToMoRef(collectorDetails.datacenter));

                                List<ObjectUpdate> resourcesUpdates = collectResourcesData(collectorDetails);

                                List<ObjectUpdate> vmUpdates = collectVMData(collectorDetails);
                                logInfo("Received resources updates for datacenter: %s : %s",
                                        collectorDetails.datacenter, resourcesUpdates.size());


                                if (!resourcesUpdates.isEmpty()) {
                                    SegregatedOverlays segregatedOverlays =
                                            segregateObjectUpdates(enumerationProgress, resourcesUpdates);

                                    VSphereNetworkEnumerationHelper
                                            .handleNetworkChanges(this, segregatedOverlays.networks, enumerationProgress, client);
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
                    }, mgr).setReferer(this.getHost().getUri()).sendWith(this);
        });
    }

    @Override
    public void handleDelete(Operation delete) {
        cleanupConnection();
        super.handleDelete(delete);
    }

    private void selfDeleteService() {
        this.sendRequest(Operation.createDelete(this.getUri()).setReferer(this.getHost().getUri()));
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

        // Get instanceUuid of the vCenter
        // TODO: vcUuid is required by CI to form the Functional Key. For now just logging it
        AboutInfo about = connection.getServiceContent().getAbout();
        this.vcUuid = about.getInstanceUuid();
        logInfo("vcUuid %s", this.vcUuid);

        DatacenterLister lister = new DatacenterLister(connection);

        try {
            for (Element element : lister.listAllDatacenters()) {
                ManagedObjectReference datacenter = element.object;
                log(Level.INFO, "Processing datacenter %s (%s)", element.path,
                        VimUtils.convertMoRefToString(element.object));

                EnumerationClient client = new EnumerationClient(connection, parent, datacenter);

                EnumerationProgress enumerationProgress = new EnumerationProgress(resourceLinks, request,
                        parent, vapiConnection, VimUtils.convertMoRefToString(datacenter));

                try {
                    refreshResourcesOnDatacenter(client, enumerationProgress, mgr);
                    VsphereDatacenterEnumerationHelper.processDatacenterInfo(this, element, enumerationProgress);
                } catch (Exception e) {
                    logWarning(() -> String.format("Error during enumeration: %s", Utils.toString(e)));
                }
            }
        } catch (Exception e) {
            mgr.patchTaskToFailure(e);
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
                                              TaskManager mgr) {
        Set<String> sharedDatastores = new HashSet<>();
        List<StoragePolicyOverlay> storagePolicies = new ArrayList<>();

        // put results in different buckets by type
        PropertyFilterSpec spec = client.createResourcesFilterSpec();
        CollectorDetails collectorDetails = new CollectorDetails();
        EnumerationClient.ObjectUpdateIterator resourcesIterator;
        SegregatedOverlays segregatedOverlays;
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
            mgr.patchTaskToFailure(msg, e);
            return;
        }

        // Process Folder list
        ctx.expectFolderCount(segregatedOverlays.folders.size());
        for (FolderOverlay folder : segregatedOverlays.folders) {
            try {
                VsphereFolderEnumerationHelper.processFoundFolder(this, ctx, folder);
            } catch (Exception e) {
                logWarning(() -> "Error processing folder information" + ": " + e.toString());
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
            logWarning(() -> "Error processing datastore host mount information" + ": " + e.toString());
        }

        try {
            List<PbmProfile> pbmProfiles = client.retrieveStoragePolicies();
            if (!pbmProfiles.isEmpty()) {
                for (PbmProfile profile : pbmProfiles) {
                    List<String> datastoreNames = client.getDatastores(profile.getProfileId());
                    StoragePolicyOverlay spOverlay = new StoragePolicyOverlay(profile, datastoreNames);
                    storagePolicies.add(spOverlay);
                }
            }
        } catch (Exception e) {
            // vSphere throws exception even if there are no storage policies found on the server.
            // Hence we can just log the message and continue, as with the datastore selection
            // still provisioning can proceed. Not marking the task to failure here.
            String msg = "Error processing Storage policy ";
            logWarning(() -> msg + ": " + e.toString());
        }

        // process results in topological order
        ctx.expectNetworkCount(segregatedOverlays.networks.size());
        for (NetworkOverlay net : segregatedOverlays.networks.values()) {
            VSphereNetworkEnumerationHelper
                    .processFoundNetwork(this, ctx, net, segregatedOverlays.networks);
        }

        ctx.expectDatastoreCount(segregatedOverlays.datastores.size());
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

        // Process found storage policy, it is related to datastore. Hence process it after
        // datastore processing is complete.
        if (storagePolicies.size() > 0) {
            ctx.expectStoragePolicyCount(storagePolicies.size());
            for (StoragePolicyOverlay sp : storagePolicies) {
                VsphereStoragePolicyEnumerationHelper.processFoundStoragePolicy(this, ctx, sp);
            }

            // checkpoint for storage policy
            try {
                ctx.getStoragePolicyTracker().await();
            } catch (InterruptedException e) {
                threadInterrupted(mgr, e);
                return;
            }
        }

        ctx.expectComputeResourceCount(segregatedOverlays.clusters.size());
        for (ComputeResourceOverlay cluster : segregatedOverlays.clusters) {
            ctx.track(cluster);
            cluster.markHostAsClustered(segregatedOverlays.hosts);
            VsphereComputeResourceEnumerationHelper.processFoundComputeResource(this, ctx, cluster);
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
        for (HostSystemOverlay hs : segregatedOverlays.hosts) {
            ctx.track(hs);
            VSphereHostSystemEnumerationHelper.processFoundHostSystem(this, ctx, hs);
        }

        // exclude all root resource pools
        // no need to collect the root resource pool
        segregatedOverlays.resourcePools.removeIf(rp -> !VimNames.TYPE_RESOURCE_POOL.equals(rp.getParent().getType()));

        MoRefKeyedMap<String> computeResourceNamesByMoref = collectComputeNames(segregatedOverlays.hosts, segregatedOverlays.clusters);
        ctx.expectResourcePoolCount(segregatedOverlays.resourcePools.size());
        for (ResourcePoolOverlay rp : segregatedOverlays.resourcePools) {
            String ownerName = computeResourceNamesByMoref.get(rp.getOwner());
            VSphereResourcePoolEnumerationHelper.processFoundResourcePool(this, ctx, rp, ownerName);
        }

        // checkpoint compute
        try {
            ctx.getHostSystemTracker().await();
            ctx.getResourcePoolTracker().await();
        } catch (InterruptedException e) {
            threadInterrupted(mgr, e);
            return;
        }

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
            String msg = "Error processing PropertyCollector results";
            logWarning(() -> msg + ": " + e.toString());
            mgr.patchTaskToFailure(msg, e);
            return;
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
                if (cr.isDrsEnabled()) {
                    // when DRS is enabled add the cluster itself and skip the hosts
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
        withTaskResults(this, task, result -> {
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
