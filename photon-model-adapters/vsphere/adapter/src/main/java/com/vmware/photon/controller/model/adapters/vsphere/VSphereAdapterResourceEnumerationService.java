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

import static com.vmware.xenon.common.UriUtils.buildUriPath;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
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
import java.util.stream.Stream;

import org.codehaus.jackson.node.ObjectNode;

import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.adapters.vsphere.InstanceClient.ClientException;
import com.vmware.photon.controller.model.adapters.vsphere.network.DvsProperties;
import com.vmware.photon.controller.model.adapters.vsphere.network.NsxProperties;
import com.vmware.photon.controller.model.adapters.vsphere.tagging.TagCache;
import com.vmware.photon.controller.model.adapters.vsphere.util.MoRefKeyedMap;
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
import com.vmware.photon.controller.model.resources.ComputeService.LifecycleState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.StorageDescriptionService;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.resources.TagFactoryService;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.photon.controller.model.tasks.QueryUtils;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.ObjectContent;
import com.vmware.vim25.OpaqueNetworkSummary;
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
import com.vmware.xenon.common.ServiceHost.ServiceNotFoundException;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Handles enumeration for vsphere endpoints. It supports up to
 * {@link #MAX_CONCURRENT_ENUM_PROCESSES} concurrent long-running enumeration processes. Attempts to
 * start more processes than that will result in error.
 */
public class VSphereAdapterResourceEnumerationService extends StatelessService {
    public static final String SELF_LINK = VSphereUriPaths.ENUMERATION_SERVICE;

    private static final int MAX_CONCURRENT_ENUM_PROCESSES = 10;
    private static final String ALL_IPS_SUBNET_CIDR = "0.0.0.0/0";
    private static final long QUERY_TASK_EXPIRY_MICROS = TimeUnit.MINUTES.toMicros(1);

    /*
     * A VM must "ferment" for a few minutes before being eligible for enumeration. This is the time
     * between a VM is created and its UUID is recorded back in the ComputeState resource. This way
     * a VM being provisioned by photon-model will not be enumerated mid-flight.
     */
    private static final long VM_FERMENTATION_PERIOD_MILLIS = 3 * 60 * 1000;

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

        TaskManager mgr = new TaskManager(this, request.taskReference,request.resourceLink());

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
                        logWarning(msg);
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
            logFine(() -> String.format("No running enumeration process for %s was found",
                    parent.documentSelfLink));
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
            logFine(() -> String.format("Enumeration process for %s already started, not starting a"
                    + " new one", parent.documentSelfLink));
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
            logWarning(msg);
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
                    .format("Max number of resource enumeration processes reached: will not start "
                            + "one for %s", parent.documentSelfLink);
            logWarning(msg);
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
        if (parent.description.regionId == null) {
            // not implemented if no datacenter is provided
            return;
        }

        PropertyFilterSpec spec = client.createResourcesFilterSpec(parent.description.regionId);

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

        VapiConnection vapiConnection = VapiConnection.createFromVimConnection(connection);

        try {
            vapiConnection.login();
        } catch (IOException | RpcException e) {
            logWarning(() -> String.format("Cannot login into vAPI endpoint: %s", Utils.toString(e)));
            // TODO: patchTaskToFailure on each failure?
            return;
        }

        try {
            for (String datacenterPath : client.getDatacenterList()) {
                logFine(() -> String.format("Refreshing datacenter %s", datacenterPath));
                EnumerationContext enumerationContext = new EnumerationContext(request, parent,
                        vapiConnection, datacenterPath);
                refreshResourcesOnDatacenter(client, enumerationContext, mgr);
            }
        } catch (ClientException e) {
            logWarning(() -> String.format("Error during enumeration: %s", Utils.toString(e)));
        }

        try {
            vapiConnection.close();
        } catch (Exception e) {
            logWarning(() -> String.format("Error occurred when closing vAPI connection: %s",
                    Utils.toString(e)));
        }

        garbageCollectUntouchedComputeResources(request, parent, mgr);
    }

    private void refreshResourcesOnDatacenter(EnumerationClient client, EnumerationContext ctx,
            TaskManager mgr) throws ClientException {
        MoRefKeyedMap<NetworkOverlay> networks = new MoRefKeyedMap<>();
        List<HostSystemOverlay> hosts = new ArrayList<>();
        List<DatastoreOverlay> datastores = new ArrayList<>();
        List<ComputeResourceOverlay> clusters = new ArrayList<>();
        List<ResourcePoolOverlay> resourcePools = new ArrayList<>();

        // put results in different buckets by type
        PropertyFilterSpec spec = client.createResourcesFilterSpec(ctx.getDatacenterPath());
        try {
            for (List<ObjectContent> page : client.retrieveObjects(spec)) {
                for (ObjectContent cont : page) {
                    if (VimUtils.isNetwork(cont.getObj())) {
                        NetworkOverlay net = new NetworkOverlay(cont);
                        if (!net.getName().toLowerCase().contains("dvuplinks")) {
                            // skip uplinks altogether,
                            // TODO starting with 6.5 query the property config.uplink instead
                            networks.put(net.getId(), net);
                        }
                    } else if (VimUtils.isHost(cont.getObj())) {
                        // this includes all standalone and clustered hosts
                        HostSystemOverlay hs = new HostSystemOverlay(cont);
                        hosts.add(hs);
                    } else if (VimUtils.isComputeResource(cont.getObj())) {
                        ComputeResourceOverlay cr = new ComputeResourceOverlay(cont);
                        if (cr.isDrsEnabled()) {
                            // when DRS is enabled add the cluster itself and skip the hosts
                            clusters.add(cr);
                        } else {
                            // ignore non-clusters and non-drs cluster: they are handled as hosts
                            continue;
                        }
                    } else if (VimUtils.isDatastore(cont.getObj())) {
                        DatastoreOverlay ds = new DatastoreOverlay(cont);
                        datastores.add(ds);
                    } else if (VimUtils.isResourcePool(cont.getObj())) {
                        ResourcePoolOverlay rp = new ResourcePoolOverlay(cont);
                        resourcePools.add(rp);
                    }
                }
            }
        } catch (Exception e) {
            String msg = "Error processing PropertyCollector results";
            logWarning(() -> msg + ": " + e.toString());
            mgr.patchTaskToFailure(msg, e);
            return;
        }

        // process results in topological order
        ctx.expectNetworkCount(networks.size());
        for (NetworkOverlay net : networks.values()) {
            processFoundNetwork(ctx, net, networks);
        }

        ctx.expectDatastoreCount(datastores.size());
        for (DatastoreOverlay ds : datastores) {
            processFoundDatastore(ctx, ds);
        }

        // checkpoint net & storage, they are not related currently
        try {
            ctx.getDatastoreTracker().await();
            ctx.getNetworkTracker().await();
        } catch (InterruptedException e) {
            threadInterrupted(mgr, e);
            return;
        }

        // exclude hosts part of a cluster
        for (ComputeResourceOverlay cluster : clusters) {
            for (ManagedObjectReference hostRef : cluster.getHosts()) {
                hosts.removeIf(ho -> Objects.equals(ho.getId().getValue(), hostRef.getValue()));
            }
        }

        ctx.expectHostSystemCount(hosts.size());
        for (HostSystemOverlay hs : hosts) {
            processFoundHostSystem(ctx, hs);
        }

        ctx.expectComputeResourceCount(clusters.size());
        for (ComputeResourceOverlay cr : clusters) {
            processFoundComputeResource(ctx, cr);
        }

        // exclude all root resource pools
        for (Iterator<ResourcePoolOverlay> it = resourcePools.iterator(); it.hasNext();) {
            ResourcePoolOverlay rp = it.next();
            if (!VimNames.TYPE_RESOURCE_POOL.equals(rp.getParent().getType())) {
                // no need to collect the root resource pool
                it.remove();
            }
        }

        MoRefKeyedMap<String> computeResourceNamesByMoref = collectComputeNames(hosts, clusters);
        ctx.expectResourcePoolCount(resourcePools.size());
        for (ResourcePoolOverlay rp : resourcePools) {
            String ownerName = computeResourceNamesByMoref.get(rp.getOwner());
            processFoundResourcePool(ctx, rp, ownerName);
        }

        // checkpoint compute
        try {
            ctx.getHostSystemTracker().await();
            ctx.getComputeResourceTracker().await();
            ctx.getResourcePoolTracker().await();
        } catch (InterruptedException e) {
            threadInterrupted(mgr, e);
            return;
        }

        long latestAcceptableModification = System.currentTimeMillis()
                - VM_FERMENTATION_PERIOD_MILLIS;
        spec = client.createVmFilterSpec(ctx.getDatacenterPath());
        try {
            for (List<ObjectContent> page : client.retrieveObjects(spec)) {
                ctx.resetVmTracker();
                for (ObjectContent cont : page) {
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
                    } else if (vm.getLastReconfigureMillis() < latestAcceptableModification) {
                        ctx.getVmTracker().register();
                        processFoundVm(ctx, vm);
                    }
                }
                ctx.getVmTracker().arriveAndAwaitAdvance();
            }
        } catch (Exception e) {
            String msg = "Error processing PropertyCollector results";
            logWarning(() -> msg + ": " + e.toString());
            mgr.patchTaskToFailure(msg, e);
            return;
        }
    }

    /**
     * Collect the names of all hosts and cluster indexed by the string representation
     * of their moref.
     * Used to construct user-friendly resource pool names without fetching their owner's name again.
     * @param hosts
     * @param computeResources
     * @return
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
            ComputeStateWithDescription parent, TaskManager mgr) {
        // find all computes of the parent which are NOT enumerated by this task AND are enumerated
        // at least once.

        String enumerateByFieldName = QuerySpecification
                .buildCompositeFieldName(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                        CustomProperties.ENUMERATED_BY_TASK_LINK);

        Builder builder = Query.Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK, request.resourceLink())
                .addFieldClause(enumerateByFieldName, request.taskLink(), Occurance.MUST_NOT_OCCUR)
                .addFieldClause(enumerateByFieldName, "", MatchType.PREFIX)
                .addInClause(ComputeState.FIELD_NAME_LIFECYCLE_STATE,
                        Arrays.asList(LifecycleState.PROVISIONING.toString(),
                                LifecycleState.RETIRED.toString()),
                        Occurance.MUST_NOT_OCCUR);

        QueryUtils.addEndpointLink(builder, ComputeState.class, request.endpointLink);
        QueryUtils.addTenantLinks(builder, parent.tenantLinks);

        // fetch compute resources with their links
        QueryTask task = QueryTask.Builder.createDirectTask()
                .addLinkTerm(ComputeState.FIELD_NAME_NETWORK_INTERFACE_LINKS)
                .addLinkTerm(ComputeState.FIELD_NAME_DISK_LINKS)
                .addOption(QueryOption.SELECT_LINKS)
                .setQuery(builder.build())
                .build();

        // TODO: add query pagination
        QueryUtils.startQueryTask(this, task).whenComplete((result, e) -> {
            if (e != null) {
                // it's too harsh to fail the task because of failed GC, next time it may pass
                logSevere(e);
                mgr.patchTask(TaskStage.FINISHED);
                return;
            }

            if (result.results.documentLinks == null || result.results.documentLinks.isEmpty()) {
                mgr.patchTask(TaskStage.FINISHED);
                return;
            }

            if (!request.preserveMissing) {
                // delete dependent resources without waiting for response
                for (String diskOrNicLink : result.results.selectedLinks) {
                    Operation.createDelete(this, diskOrNicLink)
                            .sendWith(this);
                }
            }

            Stream<Operation> gcOps = result.results.documentLinks.stream()
                    .map(link -> createComputeRemovalOp(request.preserveMissing, link));

            OperationJoin.create(gcOps)
                    .setCompletion((os, es) -> mgr.patchTask(TaskStage.FINISHED))
                    .sendWith(this);
        });
    }

    private Operation createComputeRemovalOp(boolean preserveMissing, String computeLink) {
        if (preserveMissing) {
            ComputeState body = new ComputeState();
            body.lifecycleState = LifecycleState.RETIRED;
            // set powerState for consistency with other adapters
            body.powerState = PowerState.OFF;
            return Operation.createPatch(this, computeLink)
                    .setBody(body);
        } else {
            return Operation.createDelete(this, computeLink);
        }
    }

    private void threadInterrupted(TaskManager mgr, InterruptedException e) {
        String msg = "Enumeration thread was interrupted";
        logWarning(msg);
        mgr.patchTaskToFailure(msg, e);
    }

    private void processFoundNetwork(EnumerationContext enumerationContext, NetworkOverlay net,
            MoRefKeyedMap<NetworkOverlay> allNetworks) {
        if (net.getParentSwitch() != null) {
            // portgroup: create subnet
            QueryTask task = queryForSubnet(enumerationContext, net);
            withTaskResults(task, result -> {
                if (result.documentLinks.isEmpty()) {
                    createNewSubnet(enumerationContext, net, allNetworks.get(net.getParentSwitch()));
                } else {
                    SubnetState oldDocument = convertOnlyResultToDocument(result, SubnetState.class);
                    updateSubnet(oldDocument, enumerationContext, net);
                }
            });
        } else {
            // DVS or opaque network
            QueryTask task = queryForNetwork(enumerationContext, net.getName());
            withTaskResults(task, result -> {
                if (result.documentLinks.isEmpty()) {
                    createNewNetwork(enumerationContext, net);
                } else {
                    NetworkState oldDocument = convertOnlyResultToDocument(result, NetworkState.class);
                    updateNetwork(oldDocument, enumerationContext, net);
                }
            });
        }
    }

    private void updateSubnet(SubnetState oldDocument, EnumerationContext enumerationContext, NetworkOverlay net) {
        SubnetState state = makeSubnetStateFromResults(enumerationContext, net);
        state.documentSelfLink = oldDocument.documentSelfLink;
        if (oldDocument.tenantLinks == null) {
            state.tenantLinks = enumerationContext.getTenantLinks();
        }

        logFine(() -> String.format("Syncing Subnet(Portgroup) %s", net.getName()));
        Operation.createPatch(UriUtils.buildUri(getHost(), oldDocument.documentSelfLink))
                .setBody(state)
                .setCompletion(trackNetwork(enumerationContext, net))
                .sendWith(this);
    }

    private void createNewSubnet(EnumerationContext enumerationContext, NetworkOverlay net,
            NetworkOverlay parentSwitch) {
        SubnetState state = makeSubnetStateFromResults(enumerationContext, net);
        state.customProperties.put(DvsProperties.DVS_UUID, parentSwitch.getDvsUuid());

        state.tenantLinks = enumerationContext.getTenantLinks();
        Operation.createPost(this, SubnetService.FACTORY_LINK)
                .setBody(state)
                .setCompletion(trackNetwork(enumerationContext, net))
                .sendWith(this);

        logFine(() -> String.format("Found new Subnet(Portgroup) %s", net.getName()));
    }

    private SubnetState makeSubnetStateFromResults(EnumerationContext enumerationContext, NetworkOverlay net) {
        ComputeEnumerateResourceRequest request = enumerationContext.getRequest();

        SubnetState state = new SubnetState();

        state.id = state.name = net.getName();
        state.endpointLink = enumerationContext.getRequest().endpointLink;
        state.subnetCIDR = ALL_IPS_SUBNET_CIDR;

        ManagedObjectReference parentSwitch = net.getParentSwitch();
        state.networkLink = buildStableDvsLink(parentSwitch, request.endpointLink);

        CustomProperties custProp = CustomProperties.of(state)
                .put(CustomProperties.MOREF, net.getId())
                .put(CustomProperties.ENUMERATED_BY_TASK_LINK, enumerationContext.getRequest().taskLink())
                .put(CustomProperties.TYPE, net.getId().getType());

        custProp.put(DvsProperties.PORT_GROUP_KEY, net.getPortgroupKey());

        return state;
    }

    private QueryTask queryForSubnet(EnumerationContext ctx, NetworkOverlay portgroup) {
        String dvsLink = buildStableDvsLink(portgroup.getParentSwitch(), ctx.getRequest().endpointLink);
        String moref = VimUtils.convertMoRefToString(portgroup.getId());

        Builder builder = Query.Builder.create()
                .addKindFieldClause(SubnetState.class)
                .addFieldClause(SubnetState.FIELD_NAME_NETWORK_LINK, dvsLink)
                .addCompositeFieldClause(ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                        CustomProperties.MOREF, moref);
        QueryUtils.addEndpointLink(builder, NetworkState.class, ctx.getRequest().endpointLink);
        QueryUtils.addTenantLinks(builder, ctx.getTenantLinks());

        return QueryTask.Builder.createDirectTask()
                .setQuery(builder.build())
                .build();
    }

    private void updateNetwork(NetworkState oldDocument, EnumerationContext enumerationContext, NetworkOverlay net) {
        NetworkState networkState = makeNetworkStateFromResults(enumerationContext, net);
        networkState.documentSelfLink = oldDocument.documentSelfLink;
        networkState.resourcePoolLink = null;

        if (oldDocument.tenantLinks == null) {
            networkState.tenantLinks = enumerationContext.getTenantLinks();
        }

        logFine(() -> String.format("Syncing Network %s", net.getName()));
        Operation.createPatch(UriUtils.buildUri(getHost(), oldDocument.documentSelfLink))
                .setBody(networkState)
                .setCompletion(trackNetwork(enumerationContext, net))
                .sendWith(this);

        if (!VimNames.TYPE_NETWORK.equals(net.getId().getType())) {
            return;
        }

        SubnetState subnet = new SubnetState();
        subnet.documentSelfLink = UriUtils.buildUriPath(SubnetService.FACTORY_LINK,
                UriUtils.getLastPathSegment(networkState.documentSelfLink));
        subnet.id = subnet.name = net.getName();
        subnet.endpointLink = enumerationContext.getRequest().endpointLink;
        subnet.subnetCIDR = ALL_IPS_SUBNET_CIDR;
        subnet.networkLink = networkState.documentSelfLink;

        CustomProperties.of(subnet)
                .put(CustomProperties.ENUMERATED_BY_TASK_LINK, enumerationContext.getRequest().taskLink())
                .put(CustomProperties.MOREF, net.getId())
                .put(CustomProperties.TYPE, net.getId().getType());

        Operation.createPost(this, SubnetService.FACTORY_LINK)
                .setBody(subnet)
                .sendWith(this);
    }

    private void createNewNetwork(EnumerationContext enumerationContext, NetworkOverlay net) {
        NetworkState networkState = makeNetworkStateFromResults(enumerationContext, net);
        networkState.tenantLinks = enumerationContext.getTenantLinks();
        Operation.createPost(this, NetworkService.FACTORY_LINK)
                .setBody(networkState)
                .setCompletion(trackNetwork(enumerationContext, net))
                .sendWith(this);

        logFine(() -> String.format("Found new Network %s", net.getName()));

        if (!VimNames.TYPE_NETWORK.equals(net.getId().getType())) {
            return;
        }

        SubnetState subnet = new SubnetState();
        subnet.documentSelfLink = UriUtils.buildUriPath(SubnetService.FACTORY_LINK,
                UriUtils.getLastPathSegment(networkState.documentSelfLink));
        subnet.id = subnet.name = net.getName();
        subnet.endpointLink = enumerationContext.getRequest().endpointLink;
        subnet.subnetCIDR = ALL_IPS_SUBNET_CIDR;
        subnet.networkLink = networkState.documentSelfLink;

        CustomProperties.of(subnet)
                .put(CustomProperties.ENUMERATED_BY_TASK_LINK, enumerationContext.getRequest().taskLink())
                .put(CustomProperties.MOREF, net.getId())
                .put(CustomProperties.TYPE, net.getId().getType());

        Operation.createPost(this, SubnetService.FACTORY_LINK)
                .setBody(subnet)
                .sendWith(this);
    }

    private String getSelfLinkFromOperation(Operation o) {
        return o.getBody(ServiceDocument.class).documentSelfLink;
    }

    private CompletionHandler trackDatastore(EnumerationContext enumerationContext,
            DatastoreOverlay ds) {
        return (o, e) -> {
            if (e == null) {
                enumerationContext.getDatastoreTracker().track(ds.getId(), getSelfLinkFromOperation(o));
            } else {
                enumerationContext.getDatastoreTracker().track(ds.getId(), ResourceTracker.ERROR);
            }
        };
    }

    private CompletionHandler trackVm(EnumerationContext enumerationContext) {
        return (o, e) -> {
            enumerationContext.getVmTracker().arrive();
        };
    }

    private CompletionHandler trackComputeResource(EnumerationContext enumerationContext,
            ComputeResourceOverlay cr) {
        return (o, e) -> {
            if (e == null) {
                enumerationContext.getComputeResourceTracker().track(cr.getId(), getSelfLinkFromOperation(o));
            } else {
                enumerationContext.getComputeResourceTracker().track(cr.getId(), ResourceTracker.ERROR);
            }
        };
    }

    private CompletionHandler trackHostSystem(EnumerationContext enumerationContext,
            HostSystemOverlay hs) {
        return (o, e) -> {
            if (e == null) {
                enumerationContext.getHostSystemTracker().track(hs.getId(), getSelfLinkFromOperation(o));
            } else {
                enumerationContext.getHostSystemTracker().track(hs.getParent(), ResourceTracker.ERROR);
            }
        };
    }

    private CompletionHandler trackNetwork(EnumerationContext enumerationContext,
            NetworkOverlay net) {
        return (o, e) -> {
            if (e == null) {
                enumerationContext.getNetworkTracker().track(net.getId(), getSelfLinkFromOperation(o));
            } else {
                enumerationContext.getNetworkTracker().track(net.getId(), ResourceTracker.ERROR);
            }
        };
    }

    private NetworkState makeNetworkStateFromResults(EnumerationContext enumerationContext, NetworkOverlay net) {
        ComputeEnumerateResourceRequest request = enumerationContext.getRequest();
        ComputeStateWithDescription parent = enumerationContext.getParent();

        NetworkState state = new NetworkState();

        state.documentSelfLink = NetworkService.FACTORY_LINK + "/" + this.getHost().nextUUID();
        state.id = state.name = net.getName();
        state.endpointLink = enumerationContext.getRequest().endpointLink;
        state.subnetCIDR = ALL_IPS_SUBNET_CIDR;
        state.regionId = enumerationContext.getRegionId();
        state.resourcePoolLink = request.resourcePoolLink;
        state.adapterManagementReference = request.adapterManagementReference;
        state.authCredentialsLink = parent.description.authCredentialsLink;

        URI ref = parent.description.instanceAdapterReference;
        state.instanceAdapterReference = AdapterUriUtil.buildAdapterUri(ref.getPort(),
                VSphereUriPaths.DVS_NETWORK_SERVICE);

        CustomProperties custProp = CustomProperties.of(state)
                .put(CustomProperties.MOREF, net.getId())
                .put(CustomProperties.ENUMERATED_BY_TASK_LINK, enumerationContext.getRequest().taskLink())
                .put(CustomProperties.TYPE, net.getId().getType());

        if (net.getSummary() instanceof OpaqueNetworkSummary) {
            OpaqueNetworkSummary ons = (OpaqueNetworkSummary) net.getSummary();
            custProp.put(NsxProperties.OPAQUE_NET_ID, ons.getOpaqueNetworkId());
            custProp.put(NsxProperties.OPAQUE_NET_TYPE, ons.getOpaqueNetworkType());
        }

        if (net.getId().getType().equals(VimNames.TYPE_DVS)) {
            // dvs'es have a stable link
            state.documentSelfLink = buildStableDvsLink(net.getId(), request.endpointLink);

            custProp.put(DvsProperties.DVS_UUID, net.getDvsUuid());
        }

        return state;
    }

    private String buildStableDvsLink(ManagedObjectReference ref, String endpointLink) {
        return NetworkService.FACTORY_LINK + "/"
                + VimUtils.buildStableManagedObjectId(ref, endpointLink);
    }

    private QueryTask queryForNetwork(EnumerationContext ctx, String name) {
        URI adapterManagementReference = ctx.getRequest().adapterManagementReference;
        String regionId = ctx.getRegionId();

        Builder builder = Query.Builder.create()
                .addFieldClause(NetworkState.FIELD_NAME_ADAPTER_MANAGEMENT_REFERENCE,
                        adapterManagementReference.toString())
                .addKindFieldClause(NetworkState.class)
                .addCaseInsensitiveFieldClause(NetworkState.FIELD_NAME_NAME, name, MatchType.TERM, Occurance.MUST_OCCUR)
                .addFieldClause(NetworkState.FIELD_NAME_REGION_ID, regionId);
        QueryUtils.addEndpointLink(builder, NetworkState.class, ctx.getRequest().endpointLink);
        QueryUtils.addTenantLinks(builder, ctx.getTenantLinks());

        return QueryTask.Builder.createDirectTask()
                .setQuery(builder.build())
                .build();
    }

    private void processFoundDatastore(EnumerationContext enumerationContext, DatastoreOverlay ds) {
        QueryTask task = queryForStorage(enumerationContext, ds.getName());

        withTaskResults(task, result -> {
            if (result.documentLinks.isEmpty()) {
                createNewStorageDescription(enumerationContext, ds);
            } else {
                StorageDescription oldDocument = convertOnlyResultToDocument(result,
                        StorageDescription.class);
                updateStorageDescription(oldDocument, enumerationContext, ds);
            }
        });
    }

    private void updateStorageDescription(StorageDescription oldDocument,
            EnumerationContext enumerationContext, DatastoreOverlay ds) {
        ComputeEnumerateResourceRequest request = enumerationContext.getRequest();
        String regionId = enumerationContext.getRegionId();

        StorageDescription desc = makeStorageFromResults(request, ds, regionId);
        desc.documentSelfLink = oldDocument.documentSelfLink;
        desc.resourcePoolLink = null;

        if (oldDocument.tenantLinks == null) {
            desc.tenantLinks = enumerationContext.getTenantLinks();
        }

        logFine(() -> String.format("Syncing Storage %s", ds.getName()));
        Operation.createPatch(UriUtils.buildUri(getHost(), desc.documentSelfLink))
                .setBody(desc)
                .setCompletion(trackDatastore(enumerationContext, ds))
                .sendWith(this);
    }

    private void createNewStorageDescription(EnumerationContext enumerationContext,
            DatastoreOverlay ds) {
        ComputeEnumerateResourceRequest request = enumerationContext.getRequest();
        String regionId = enumerationContext.getRegionId();
        StorageDescription desc = makeStorageFromResults(request, ds, regionId);
        desc.tenantLinks = enumerationContext.getTenantLinks();
        logFine(() -> String.format("Found new Datastore %s", ds.getName()));

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
        res.endpointLink = request.endpointLink;
        res.adapterManagementReference = request.adapterManagementReference;
        res.capacityBytes = ds.getCapacityBytes();
        res.regionId = regionId;
        CustomProperties.of(res)
                .put(CustomProperties.MOREF, ds.getId())
                .put(CustomProperties.ENUMERATED_BY_TASK_LINK, request.taskLink());

        return res;
    }

    private QueryTask queryForStorage(EnumerationContext ctx, String name) {
        Builder builder = Query.Builder.create()
                .addFieldClause(StorageDescription.FIELD_NAME_ADAPTER_REFERENCE,
                        ctx.getRequest().adapterManagementReference.toString())
                .addFieldClause(StorageDescription.FIELD_NAME_REGION_ID, ctx.getRegionId())
                .addFieldClause(StorageDescription.FIELD_NAME_NAME, name);
        QueryUtils.addEndpointLink(builder, StorageDescription.class,
                ctx.getRequest().endpointLink);
        QueryUtils.addTenantLinks(builder, ctx.getTenantLinks());

        return QueryTask.Builder.createDirectTask()
                .setQuery(builder.build())
                .build();
    }

    /**
     * Either creates a new Compute or update an already existing one. Existence is checked by
     * querying for a compute with id equals to moref value of a cluster whose parent is the Compute
     * from the request.
     *
     * @param enumerationContext
     * @param cr
     */
    private void processFoundComputeResource(EnumerationContext enumerationContext, ComputeResourceOverlay cr) {
        ComputeEnumerateResourceRequest request = enumerationContext.getRequest();
        QueryTask task = queryForCluster(enumerationContext, request.resourceLink(), cr.getId().getValue());

        withTaskResults(task, result -> {
            if (result.documentLinks.isEmpty()) {
                createNewComputeResource(enumerationContext, cr);
            } else {
                ComputeState oldDocument = convertOnlyResultToDocument(result, ComputeState.class);
                updateCluster(oldDocument, enumerationContext, cr);
            }
        });
    }

    private <T> T convertOnlyResultToDocument(ServiceDocumentQueryResult result, Class<T> type) {
        return Utils.fromJson(result.documents.values().iterator().next(), type);
    }

    private void updateCluster(ComputeState oldDocument,
            EnumerationContext enumerationContext, ComputeResourceOverlay cr) {
        ComputeState state = makeComputeResourceFromResults(enumerationContext, cr);
        state.documentSelfLink = oldDocument.documentSelfLink;
        state.resourcePoolLink = null;

        if (oldDocument.tenantLinks == null) {
            state.tenantLinks = enumerationContext.getTenantLinks();
        }
        populateTags(enumerationContext, cr, state);

        logFine(() -> String.format("Syncing ComputeResource %s", oldDocument.documentSelfLink));
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

    private ComputeDescription makeDescriptionForResourcePool(EnumerationContext enumerationContext,
            ResourcePoolOverlay rp, String rpSelfLink) {
        ComputeDescription res = new ComputeDescription();
        res.name = rp.getName();
        res.documentSelfLink =
                buildUriPath(ComputeDescriptionService.FACTORY_LINK, UriUtils.getLastPathSegment(rpSelfLink));

        res.totalMemoryBytes = rp.getMemoryReservationBytes();
        // resource pools CPU is measured in Mhz
        res.cpuCount = 0;
        res.supportedChildren = Collections.singletonList(ComputeType.VM_GUEST.name());
        res.endpointLink = enumerationContext.getRequest().endpointLink;
        res.instanceAdapterReference = enumerationContext
                .getParent().description.instanceAdapterReference;
        res.enumerationAdapterReference = enumerationContext
                .getParent().description.enumerationAdapterReference;
        res.statsAdapterReference = enumerationContext
                .getParent().description.statsAdapterReference;
        res.regionId = enumerationContext.getRegionId();

        return res;
    }

    private ComputeDescription makeDescriptionForCluster(EnumerationContext enumerationContext,
            ComputeResourceOverlay cr) {
        ComputeDescription res = new ComputeDescription();
        res.name = cr.getName();
        res.documentSelfLink =
                buildUriPath(ComputeDescriptionService.FACTORY_LINK, UUID.randomUUID().toString());
        res.cpuCount = cr.getTotalCpuCores();
        if (cr.getTotalCpuCores() != 0) {
            res.cpuMhzPerCore = cr.getTotalCpuMhz() / cr.getTotalCpuCores();
        }
        res.totalMemoryBytes = cr.getTotalMemoryBytes();
        res.supportedChildren = Collections.singletonList(ComputeType.VM_GUEST.name());
        res.endpointLink = enumerationContext.getRequest().endpointLink;
        res.instanceAdapterReference = enumerationContext
                .getParent().description.instanceAdapterReference;
        res.enumerationAdapterReference = enumerationContext
                .getParent().description.enumerationAdapterReference;
        res.statsAdapterReference = enumerationContext
                .getParent().description.statsAdapterReference;
        res.regionId = enumerationContext.getRegionId();

        return res;
    }

    private void createNewComputeResource(EnumerationContext enumerationContext, ComputeResourceOverlay cr) {
        ComputeDescription desc = makeDescriptionForCluster(enumerationContext, cr);
        desc.tenantLinks = enumerationContext.getTenantLinks();
        Operation.createPost(this, ComputeDescriptionService.FACTORY_LINK)
                .setBody(desc)
                .sendWith(this);

        ComputeState state = makeComputeResourceFromResults(enumerationContext, cr);
        state.tenantLinks = enumerationContext.getTenantLinks();
        state.descriptionLink = desc.documentSelfLink;
        populateTags(enumerationContext, cr, state);

        logFine(() -> String.format("Found new ComputeResource %s", cr.getId().getValue()));
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
        } catch (IOException | RpcException ignore) {

        }

        if (tags == null || tags.isEmpty()) {
            return new HashSet<>();
        }

        Stream<Operation> ops = tags.stream()
                .map(s -> Operation
                        .createPost(UriUtils.buildFactoryUri(getHost(), TagService.class))
                        .setBody(s));

        OperationJoin.create(ops)
                .sendWith(this);

        return tags.stream()
                .map(s -> s.documentSelfLink)
                .collect(Collectors.toSet());
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
                state.documentSelfLink = TagFactoryService.generateSelfLink(state);
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
        state.type = ComputeType.VM_HOST;
        state.endpointLink = enumerationContext.getRequest().endpointLink;
        state.environmentName = ComputeDescription.ENVIRONMENT_NAME_ON_PREMISE;
        state.adapterManagementReference = enumerationContext
                .getRequest().adapterManagementReference;
        state.parentLink = enumerationContext.getRequest().resourceLink();
        state.resourcePoolLink = enumerationContext.getRequest().resourcePoolLink;
        state.name = cr.getName();
        state.powerState = PowerState.ON;
        CustomProperties.of(state)
                .put(CustomProperties.MOREF, cr.getId())
                .put(CustomProperties.ENUMERATED_BY_TASK_LINK,
                        enumerationContext.getRequest().taskLink())
                .put(CustomProperties.TYPE, cr.getId().getType());
        return state;
    }

    private QueryTask queryForCluster(EnumerationContext ctx, String parentComputeLink,
            String moRefId) {
        Builder builder = Query.Builder.create()
                .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK, parentComputeLink)
                .addFieldClause(ComputeState.FIELD_NAME_ID, moRefId);

        QueryUtils.addEndpointLink(builder, ComputeState.class, ctx.getRequest().endpointLink);
        QueryUtils.addTenantLinks(builder, ctx.getTenantLinks());

        return QueryTask.Builder.createDirectTask()
                .setQuery(builder.build())
                .build();
    }

    /**
     * @param enumerationContext
     * @param hs
     */
    private void processFoundHostSystem(EnumerationContext enumerationContext, HostSystemOverlay hs) {
        ComputeEnumerateResourceRequest request = enumerationContext.getRequest();
        QueryTask task = queryForHostSystem(enumerationContext, request.resourceLink(), hs.getId().getValue());

        withTaskResults(task, result -> {
            if (result.documentLinks.isEmpty()) {
                createNewHostSystem(enumerationContext, hs);
            } else {
                ComputeState oldDocument = convertOnlyResultToDocument(result, ComputeState.class);
                updateHostSystem(oldDocument, enumerationContext, hs);
            }
        });
    }

    private void updateHostSystem(ComputeState oldDocument, EnumerationContext enumerationContext,
            HostSystemOverlay hs) {
        ComputeState state = makeHostSystemFromResults(enumerationContext, hs);
        state.documentSelfLink = oldDocument.documentSelfLink;
        state.resourcePoolLink = null;

        if (oldDocument.tenantLinks == null) {
            state.tenantLinks = enumerationContext.getTenantLinks();
        }
        populateTags(enumerationContext, hs, state);

        logFine(() -> String.format("Syncing HostSystem %s", oldDocument.documentSelfLink));
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

    private void createNewHostSystem(EnumerationContext enumerationContext, HostSystemOverlay hs) {
        ComputeDescription desc = makeDescriptionForHost(enumerationContext, hs);
        desc.tenantLinks = enumerationContext.getTenantLinks();
        Operation.createPost(this, ComputeDescriptionService.FACTORY_LINK)
                .setBody(desc)
                .sendWith(this);

        ComputeState state = makeHostSystemFromResults(enumerationContext, hs);
        state.descriptionLink = desc.documentSelfLink;
        state.tenantLinks = enumerationContext.getTenantLinks();
        populateTags(enumerationContext, hs, state);

        logFine(() -> String.format("Found new HostSystem %s", hs.getName()));
        Operation.createPost(this, ComputeService.FACTORY_LINK)
                .setBody(state)
                .setCompletion(trackHostSystem(enumerationContext, hs))
                .sendWith(this);
    }

    private void populateTags(EnumerationContext enumerationContext, AbstractOverlay obj, ComputeState state) {
        state.tagLinks = retrieveTagLinksAndCreateTagsAsync(enumerationContext.getEndpoint(), obj.getId(),
                enumerationContext.getTenantLinks());
    }

    private ComputeDescription makeDescriptionForHost(EnumerationContext enumerationContext,
            HostSystemOverlay hs) {
        ComputeDescription res = new ComputeDescription();
        res.name = hs.getName();
        res.documentSelfLink =
                buildUriPath(ComputeDescriptionService.FACTORY_LINK, UUID.randomUUID().toString());
        res.cpuCount = hs.getCoreCount();
        res.endpointLink = enumerationContext.getRequest().endpointLink;
        res.cpuMhzPerCore = hs.getCpuMhz();
        res.totalMemoryBytes = hs.getTotalMemoryBytes();
        res.supportedChildren = Collections.singletonList(ComputeType.VM_GUEST.name());
        res.instanceAdapterReference = enumerationContext
                .getParent().description.instanceAdapterReference;
        res.enumerationAdapterReference = enumerationContext
                .getParent().description.enumerationAdapterReference;
        res.statsAdapterReference = enumerationContext
                .getParent().description.statsAdapterReference;
        res.regionId = enumerationContext.getRegionId();

        return res;
    }

    private ComputeState makeHostSystemFromResults(EnumerationContext enumerationContext,
            HostSystemOverlay hs) {
        ComputeState state = new ComputeState();
        state.type = ComputeType.VM_HOST;
        state.environmentName = ComputeDescription.ENVIRONMENT_NAME_ON_PREMISE;
        state.endpointLink = enumerationContext.getRequest().endpointLink;
        state.id = hs.getId().getValue();
        state.adapterManagementReference = enumerationContext
                .getRequest().adapterManagementReference;
        state.parentLink = enumerationContext.getRequest().resourceLink();
        state.resourcePoolLink = enumerationContext.getRequest().resourcePoolLink;
        state.name = hs.getName();
        // TODO: retrieve host power state
        state.powerState = PowerState.ON;
        CustomProperties.of(state)
                .put(CustomProperties.MOREF, hs.getId())
                .put(CustomProperties.ENUMERATED_BY_TASK_LINK,
                        enumerationContext.getRequest().taskLink())
                .put(CustomProperties.TYPE, hs.getId().getType());
        return state;
    }

    /**
     * @param enumerationContext
     * @param vm
     */
    private void processFoundVm(EnumerationContext enumerationContext, VmOverlay vm) {
        ComputeEnumerateResourceRequest request = enumerationContext.getRequest();
        QueryTask task = queryForVm(enumerationContext, request.resourceLink(), vm.getInstanceUuid());

        withTaskResults(task, result -> {
            if (result.documentLinks.isEmpty()) {
                createNewVm(enumerationContext, vm);
            } else {
                ComputeState oldDocument = convertOnlyResultToDocument(result, ComputeState.class);
                updateVm(oldDocument, enumerationContext, vm);
            }
        });
    }

    private void updateVm(ComputeState oldDocument, EnumerationContext enumerationContext,
            VmOverlay vm) {
        ComputeState state = makeVmFromResults(enumerationContext, vm);
        state.documentSelfLink = oldDocument.documentSelfLink;
        state.resourcePoolLink = null;

        if (oldDocument.tenantLinks == null) {
            state.tenantLinks = enumerationContext.getTenantLinks();
        }
        populateTags(enumerationContext, vm, state);

        logFine(() -> String.format("Syncing VM %s", state.documentSelfLink));
        Operation.createPatch(UriUtils.buildUri(getHost(), oldDocument.documentSelfLink))
                .setBody(state)
                .setCompletion(trackVm(enumerationContext))
                .sendWith(this);
    }

    private void createNewVm(EnumerationContext enumerationContext, VmOverlay vm) {
        ComputeDescription desc = makeDescriptionForVm(enumerationContext, vm);
        desc.tenantLinks = enumerationContext.getTenantLinks();
        Operation.createPost(this, ComputeDescriptionService.FACTORY_LINK)
                .setBody(desc)
                .sendWith(this);

        ComputeState state = makeVmFromResults(enumerationContext, vm);
        state.descriptionLink = desc.documentSelfLink;
        state.tenantLinks = enumerationContext.getTenantLinks();
        populateTags(enumerationContext, vm, state);

        state.networkInterfaceLinks = new ArrayList<>();
        for (VirtualEthernetCard nic : vm.getNics()) {
            VirtualDeviceBackingInfo backing = nic.getBacking();

            if (backing instanceof VirtualEthernetCardNetworkBackingInfo) {
                VirtualEthernetCardNetworkBackingInfo veth = (VirtualEthernetCardNetworkBackingInfo) backing;
                NetworkInterfaceState iface = new NetworkInterfaceState();
                iface.networkLink = enumerationContext.getNetworkTracker()
                        .getSelfLink(veth.getNetwork());
                iface.name = nic.getDeviceInfo().getLabel();
                iface.documentSelfLink = buildUriPath(NetworkInterfaceService.FACTORY_LINK,
                        UUID.randomUUID().toString());

                Operation.createPost(this, NetworkInterfaceService.FACTORY_LINK)
                        .setBody(iface)
                        .sendWith(this);

                state.networkInterfaceLinks.add(iface.documentSelfLink);
            } else {
                // TODO add support for DVS
                logFine(() -> String.format("Will not add nic of type %s",
                        backing.getClass().getName()));
            }
        }

        logFine(() -> String.format("Found new VM %s", vm.getInstanceUuid()));
        Operation.createPost(this, ComputeService.FACTORY_LINK)
                .setBody(state)
                .setCompletion(trackVm(enumerationContext))
                .sendWith(this);
    }

    private ComputeDescription makeDescriptionForVm(EnumerationContext enumerationContext,
            VmOverlay vm) {
        ComputeDescription res = new ComputeDescription();
        res.name = vm.getName();
        res.endpointLink = enumerationContext.getRequest().endpointLink;
        res.documentSelfLink =
                buildUriPath(ComputeDescriptionService.FACTORY_LINK, UUID.randomUUID().toString());
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

    private void processFoundResourcePool(EnumerationContext enumerationContext, ResourcePoolOverlay rp,
            String ownerName) {
        ComputeEnumerateResourceRequest request = enumerationContext.getRequest();
        String selfLink = buildStableResourcePoolLink(rp.getId(), request.endpointLink);

        Operation.createGet(this, selfLink)
                .setCompletion((o, e) -> {
                    if (e == null) {
                        updateResourcePool(enumerationContext, ownerName, selfLink, rp);
                    } else if (e instanceof ServiceNotFoundException) {
                        createNewResourcePool(enumerationContext, ownerName, selfLink, rp);
                    } else {
                        trackResourcePool(enumerationContext, rp).handle(o, e);
                    }
                })
                .sendWith(this);
    }

    private void updateResourcePool(EnumerationContext enumerationContext, String ownerName, String selfLink,
            ResourcePoolOverlay rp) {
        ComputeState state = makeResourcePoolFromResults(enumerationContext, rp, selfLink);
        state.name = rp.makeUserFriendlyName(ownerName);
        state.tenantLinks = enumerationContext.getTenantLinks();
        state.resourcePoolLink = null;

        ComputeDescription desc = makeDescriptionForResourcePool(enumerationContext, rp, selfLink);
        state.descriptionLink = desc.documentSelfLink;

        logFine(() -> String.format("Refreshed ResourcePool %s", state.name));
        Operation.createPatch(this, selfLink)
                .setBody(state)
                .setCompletion(trackResourcePool(enumerationContext, rp))
                .sendWith(this);

        Operation.createPatch(this, desc.documentSelfLink)
                .setBody(desc)
                .sendWith(this);
    }

    private void createNewResourcePool(EnumerationContext enumerationContext, String ownerName, String selfLink,
            ResourcePoolOverlay rp) {
        ComputeState state = makeResourcePoolFromResults(enumerationContext, rp, selfLink);
        state.name = rp.makeUserFriendlyName(ownerName);
        state.tenantLinks = enumerationContext.getTenantLinks();

        ComputeDescription desc = makeDescriptionForResourcePool(enumerationContext, rp, selfLink);
        desc.tenantLinks = enumerationContext.getTenantLinks();
        state.descriptionLink = desc.documentSelfLink;

        logFine(() -> String.format("Found new ResourcePool %s", state.name));
        Operation.createPost(this, ComputeService.FACTORY_LINK)
                .setBody(state)
                .setCompletion(trackResourcePool(enumerationContext, rp))
                .sendWith(this);

        Operation.createPost(this, ComputeDescriptionService.FACTORY_LINK)
                .setBody(desc)
                .sendWith(this);
    }

    private ComputeState makeResourcePoolFromResults(EnumerationContext enumerationContext, ResourcePoolOverlay rp,
            String selfLink) {
        ComputeEnumerateResourceRequest request = enumerationContext.getRequest();

        ComputeState state = new ComputeState();
        state.documentSelfLink = selfLink;
        state.name = rp.getName();
        state.id = rp.getId().getValue();
        state.type = ComputeType.VM_HOST;
        state.powerState = PowerState.ON;
        state.endpointLink = request.endpointLink;
        state.parentLink = enumerationContext.getRequest().resourceLink();
        state.resourcePoolLink = request.resourcePoolLink;
        state.adapterManagementReference = request.adapterManagementReference;

        CustomProperties.of(state)
                .put(CustomProperties.MOREF, rp.getId())
                .put(CustomProperties.ENUMERATED_BY_TASK_LINK, request.taskLink())
                .put(CustomProperties.TYPE, VimNames.TYPE_RESOURCE_POOL);
        return state;
    }

    private CompletionHandler trackResourcePool(EnumerationContext enumerationContext, ResourcePoolOverlay rp) {
        return (o, e) -> {
            if (e == null) {
                enumerationContext.getResourcePoolTracker().track(rp.getId(), getSelfLinkFromOperation(o));
            } else {
                enumerationContext.getResourcePoolTracker().track(rp.getId(), ResourceTracker.ERROR);
            }
        };
    }

    private String buildStableResourcePoolLink(ManagedObjectReference ref, String adapterManagementReference) {
        return ComputeService.FACTORY_LINK + "/"
                + VimUtils.buildStableManagedObjectId(ref, adapterManagementReference);
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
        state.type = ComputeType.VM_GUEST;
        state.environmentName = ComputeDescription.ENVIRONMENT_NAME_ON_PREMISE;
        state.endpointLink = request.endpointLink;
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
                .put(CustomProperties.ENUMERATED_BY_TASK_LINK, request.taskLink())
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
        task.documentExpirationTimeMicros = Utils.fromNowMicrosUtc(QUERY_TASK_EXPIRY_MICROS);
        Operation.createPost(UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS))
                .setBody(task)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning(() -> String.format("Error processing task %s",
                                task.documentSelfLink));
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
     * @param ctx
     *
     * @param parentComputeLink
     * @param instanceUuid
     * @return
     */
    private QueryTask queryForVm(EnumerationContext ctx, String parentComputeLink,
            String instanceUuid) {
        Builder builder = Query.Builder.create()
                .addFieldClause(ComputeState.FIELD_NAME_ID, instanceUuid)
                .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK, parentComputeLink);

        QueryUtils.addEndpointLink(builder, ComputeState.class, ctx.getRequest().endpointLink);
        QueryUtils.addTenantLinks(builder, ctx.getTenantLinks());

        return QueryTask.Builder.createDirectTask()
                .setQuery(builder.build())
                .build();
    }

    /**
     * Builds a query for finding a HostSystems by its manage object reference.
     *
     * @param ctx
     * @param parentComputeLink
     * @param moRefId
     * @return
     */
    private QueryTask queryForHostSystem(EnumerationContext ctx, String parentComputeLink, String moRefId) {
        Builder builder = Query.Builder.create()
                .addFieldClause(ComputeState.FIELD_NAME_ID, moRefId)
                .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK, parentComputeLink);
        QueryUtils.addEndpointLink(builder, ComputeState.class, ctx.getRequest().endpointLink);
        QueryUtils.addTenantLinks(builder, ctx.getTenantLinks());

        return QueryTask.Builder.createDirectTask()
                .setQuery(builder.build())
                .addOption(QueryOption.EXPAND_CONTENT)
                .build();
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
