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

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
import java.util.stream.Stream;

import org.codehaus.jackson.node.ObjectNode;

import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.adapters.vsphere.network.DvsProperties;
import com.vmware.photon.controller.model.adapters.vsphere.network.NsxProperties;
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
import com.vmware.photon.controller.model.resources.ComputeService.LifecycleState;
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
    private static final String FAKE_SUBNET_CIDR = "0.0.0.0/0";

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
            logWarning(() -> "Cannot login into vAPI endpoint");
        }

        EnumerationContext enumerationContext = new EnumerationContext(request, parent,
                vapiConnection);

        List<NetworkOverlay> networks = new ArrayList<>();
        List<VmOverlay> vms = new ArrayList<>();
        List<HostSystemOverlay> hosts = new ArrayList<>();
        List<DatastoreOverlay> datastores = new ArrayList<>();
        List<ComputeResourceOverlay> computeResources = new ArrayList<>();
        List<ResourcePoolOverlay> resourcePools = new ArrayList<>();

        Map<String, ComputeResourceOverlay> nonDrsClusters = new HashMap<>();

        // put results in different buckets by type
        long latestAcceptableModification = System.currentTimeMillis()
                - VM_FERMENTATION_PERIOD_MILLIS;
        try {
            for (List<ObjectContent> page : client.retrieveObjects(spec)) {
                for (ObjectContent cont : page) {
                    if (VimUtils.isNetwork(cont.getObj())) {
                        NetworkOverlay net = new NetworkOverlay(cont);
                        networks.add(net);
                    } else if (VimUtils.isVirtualMachine(cont.getObj())) {
                        VmOverlay vm = new VmOverlay(cont);
                        if (vm.getInstanceUuid() == null) {
                            logWarning(() -> String.format("Cannot process a VM without"
                                    + " instanceUuid: %s",
                                    VimUtils.convertMoRefToString(vm.getId())));
                        } else if (vm.getLastReconfigureMillis() < latestAcceptableModification) {
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
                    } else if (VimUtils.isResourcePool(cont.getObj())) {
                        ResourcePoolOverlay rp = new ResourcePoolOverlay(cont);
                        resourcePools.add(rp);
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
            processFoundNetwork(enumerationContext, net, connection);
        }

        enumerationContext.expectDatastoreCount(datastores.size());
        for (DatastoreOverlay ds : datastores) {
            processFoundDatastore(enumerationContext, ds);
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
        hosts.removeIf(hs -> !nonDrsClusters.containsKey(hs.getParent().getValue()));
        enumerationContext.expectHostSystemCount(hosts.size());
        for (HostSystemOverlay hs : hosts) {
            processFoundHostSystem(enumerationContext, hs);
        }

        enumerationContext.expectComputeResourceCount(computeResources.size());
        for (ComputeResourceOverlay cr : computeResources) {
            processFoundComputeResource(enumerationContext, cr);
        }


        // exclude all root resource pools
        for (Iterator<ResourcePoolOverlay> it = resourcePools.iterator(); it.hasNext();) {
            ResourcePoolOverlay rp = it.next();
            if (!VimNames.TYPE_RESOURCE_POOL.equals(rp.getParent().getType())) {
                // no need to collect the root resource pool
                it.remove();
            }
        }

        Map<String, String> computeResourceNamesByMoref = collectComputeNames(hosts, computeResources);
        enumerationContext.expectResourcePoolCount(resourcePools.size());
        for (ResourcePoolOverlay rp : resourcePools) {
            String ownerName = computeResourceNamesByMoref.get(VimUtils.convertMoRefToString(rp.getOwner()));
            processFoundResourcePool(enumerationContext, rp, ownerName);
        }

        // checkpoint compute
        try {
            enumerationContext.getHostSystemTracker().await();
            enumerationContext.getComputeResourceTracker().await();
            enumerationContext.getResourcePoolTracker().await();
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

        garbageCollectUntouchedComputeResources(enumerationContext, mgr, request,
                parent.tenantLinks);
    }

    /**
     * Collect the names of all hosts and cluster indexed by the string representation
     * of their moref.
     * Used to construct user-friendly resource pool names without fetching their owner's name again.
     * @param hosts
     * @param computeResources
     * @return
     */
    private Map<String, String> collectComputeNames(List<HostSystemOverlay> hosts,
            List<ComputeResourceOverlay> computeResources) {
        Map<String, String> computeResourceNamesByMoref = new HashMap<>();
        for (HostSystemOverlay host : hosts) {
            computeResourceNamesByMoref.put(VimUtils.convertMoRefToString(host.getId()), host.getName());
        }
        for (ComputeResourceOverlay cr : computeResources) {
            computeResourceNamesByMoref.put(VimUtils.convertMoRefToString(cr.getId()), cr.getName());
        }
        return computeResourceNamesByMoref;
    }

    private void garbageCollectUntouchedComputeResources(EnumerationContext ctx, TaskManager mgr,
            ComputeEnumerateResourceRequest request, List<String> tenantLinks) {
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
                .addFieldClause(ComputeState.FIELD_NAME_LIFECYCLE_STATE,
                        LifecycleState.RETIRED.toString(),
                        Occurance.MUST_NOT_OCCUR);

        QueryUtils.addEndpointLink(builder, ComputeState.class, ctx.getRequest().endpointLink);
        QueryUtils.addTenantLinks(builder, tenantLinks);

        // fetch compute resources with their links
        QueryTask task = QueryTask.Builder.createDirectTask()
                .addLinkTerm(ComputeState.FIELD_NAME_NETWORK_INTERFACE_LINKS)
                .addLinkTerm(ComputeState.FIELD_NAME_DISK_LINKS)
                .addOption(QueryOption.SELECT_LINKS)
                .setQuery(builder.build())
                .build();

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

    private URI getVapiUri(URI uri) {
        // TODO use lookup service
        return URI.create(uri.toString().replace("/sdk", "/api"));
    }

    private void processFoundNetwork(EnumerationContext enumerationContext, NetworkOverlay net, Connection connection) {
        QueryTask task = queryForNetwork(enumerationContext, net.getName());
        task.tenantLinks = enumerationContext.getTenantLinks();

        withTaskResults(task, result -> {
            if (result.documentLinks.isEmpty()) {
                createNewNetwork(enumerationContext, net);
            } else {
                NetworkState oldDocument = convertOnlyResultToDocument(result, NetworkState.class);
                updateNetwork(oldDocument, enumerationContext, net);
            }
        });
    }

    private void updateNetwork(NetworkState oldDocument, EnumerationContext enumerationContext, NetworkOverlay net) {
        NetworkState state = makeNetworkStateFromResults(enumerationContext, net);
        state.documentSelfLink = oldDocument.documentSelfLink;
        if (oldDocument.tenantLinks == null) {
            state.tenantLinks = enumerationContext.getTenantLinks();
        }

        logFine(() -> String.format("Syncing Network %s", net.getName()));
        Operation.createPatch(UriUtils.buildUri(getHost(), oldDocument.documentSelfLink))
                .setBody(state)
                .setCompletion(trackNetwork(enumerationContext, net))
                .sendWith(this);
    }

    private void createNewNetwork(EnumerationContext enumerationContext, NetworkOverlay net) {
        NetworkState state = makeNetworkStateFromResults(enumerationContext, net);
        state.tenantLinks = enumerationContext.getTenantLinks();
        Operation.createPost(this, NetworkService.FACTORY_LINK)
                .setBody(state)
                .setCompletion(trackNetwork(enumerationContext, net))
                .sendWith(this);

        logFine(() -> String.format("Found new Network %s", net.getName()));
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

    private NetworkState makeNetworkStateFromResults(EnumerationContext enumerationContext, NetworkOverlay net) {
        ComputeEnumerateResourceRequest request = enumerationContext.getRequest();
        ComputeStateWithDescription parent = enumerationContext.getParent();

        NetworkState state = new NetworkState();

        state.id = state.name = net.getName();
        state.endpointLink = enumerationContext.getRequest().endpointLink;
        state.subnetCIDR = FAKE_SUBNET_CIDR;
        state.regionId = parent.description.regionId;
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

        ManagedObjectReference parentSwitch = net.getParentSwitch();
        if (parentSwitch != null) {
            // only if net is portgroup
            String dvsSelfLink = buildStableDvsLink(parentSwitch, request.adapterManagementReference.toString());
            custProp.put(DvsProperties.PARENT_DVS_LINK, dvsSelfLink);
            custProp.put(DvsProperties.PORT_GROUP_KEY, net.getPortgroupKey());
        }

        if (net.getSummary() instanceof OpaqueNetworkSummary) {
            OpaqueNetworkSummary ons = (OpaqueNetworkSummary) net.getSummary();
            custProp.put(NsxProperties.OPAQUE_NET_ID, ons.getOpaqueNetworkId());
            custProp.put(NsxProperties.OPAQUE_NET_TYPE, ons.getOpaqueNetworkType());
        }

        if (net.getId().getType().equals(VimNames.TYPE_DVS)) {
            // dvs'es have a stable link
            state.documentSelfLink = buildStableDvsLink(net.getId(),
                    request.adapterManagementReference.toString());

            custProp.put(DvsProperties.DVS_UUID, net.getDvsUuid());
        }

        return state;
    }

    private String buildStableDvsLink(ManagedObjectReference ref,
            String adapterManagementReference) {
        return NetworkService.FACTORY_LINK + "/"
                + VimUtils.buildStableManagedObjectId(ref, adapterManagementReference);
    }

    private QueryTask queryForNetwork(EnumerationContext ctx, String name) {
        URI adapterManagementReference = ctx.getRequest().adapterManagementReference;
        String regionId = ctx.getParent().description.regionId;

        Builder builder = Query.Builder.create()
                .addFieldClause(NetworkState.FIELD_NAME_ADAPTER_MANAGEMENT_REFERENCE,
                        adapterManagementReference.toString())
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
        task.tenantLinks = enumerationContext.getTenantLinks();

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
        QueryTask task = queryForCluster(enumerationContext, request.resourceLink(),
                cr.getId().getValue());
        task.tenantLinks = enumerationContext.getTenantLinks();

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
        res.documentSelfLink = UriUtils
                .buildUriPath(ComputeDescriptionService.FACTORY_LINK, UriUtils.getLastPathSegment(rpSelfLink));

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

        return res;
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
        res.endpointLink = enumerationContext.getRequest().endpointLink;
        res.instanceAdapterReference = enumerationContext
                .getParent().description.instanceAdapterReference;
        res.enumerationAdapterReference = enumerationContext
                .getParent().description.enumerationAdapterReference;
        res.statsAdapterReference = enumerationContext
                .getParent().description.statsAdapterReference;

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
                        .setReferer(getUri())
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
        QueryTask task = queryForHostSystem(enumerationContext, request.resourceLink(),
                hs.getId().getValue(), enumerationContext.getTenantLinks());
        task.tenantLinks = enumerationContext.getTenantLinks();
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
        res.documentSelfLink = UriUtils
                .buildUriPath(ComputeDescriptionService.FACTORY_LINK, UUID.randomUUID().toString());
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
     * @param tenantLinks
     */
    private void processFoundVm(EnumerationContext enumerationContext, VmOverlay vm,
            List<String> tenantLinks) {
        ComputeEnumerateResourceRequest request = enumerationContext.getRequest();
        QueryTask task = queryForVm(enumerationContext, request.resourceLink(),
                vm.getInstanceUuid(), tenantLinks);
        task.tenantLinks = tenantLinks;

        withTaskResults(task, result -> {
            if (result.documentLinks.isEmpty()) {
                createNewVm(enumerationContext, vm, tenantLinks);
            } else {
                ComputeState oldDocument = convertOnlyResultToDocument(result, ComputeState.class);
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
        populateTags(enumerationContext,  vm, state);

        logFine(() -> String.format("Syncing VM %s", state.documentSelfLink));
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
                iface.documentSelfLink = UriUtils.buildUriPath(NetworkInterfaceService.FACTORY_LINK,
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
                .setCompletion(trackVm(enumerationContext, vm))
                .sendWith(this);
    }

    private ComputeDescription makeDescriptionForVm(EnumerationContext enumerationContext,
            VmOverlay vm) {
        ComputeDescription res = new ComputeDescription();
        res.name = vm.getName();
        res.endpointLink = enumerationContext.getRequest().endpointLink;
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

    private void processFoundResourcePool(EnumerationContext enumerationContext, ResourcePoolOverlay rp,
            String ownerName) {
        ComputeEnumerateResourceRequest request = enumerationContext.getRequest();
        String selfLink = buildStableResourcePoolLink(rp.getId(), request.adapterManagementReference.toString());

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
            String key = VimUtils.convertMoRefToString(rp.getId());

            if (e == null) {
                enumerationContext.getResourcePoolTracker().track(key, getSelfLinkFromOperation(o));
            } else {
                enumerationContext.getResourcePoolTracker().track(key, ResourceTracker.ERROR);
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
        state.endpointLink = enumerationContext.getRequest().endpointLink;
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
                .put(CustomProperties.ENUMERATED_BY_TASK_LINK,
                        enumerationContext.getRequest().taskLink())
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
     * @param tenantLinks
     * @return
     */
    private QueryTask queryForVm(EnumerationContext ctx, String parentComputeLink,
            String instanceUuid, List<String> tenantLinks) {
        Builder builder = Query.Builder.create()
                .addFieldClause(ComputeState.FIELD_NAME_ID, instanceUuid)
                .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK, parentComputeLink);

        QueryUtils.addEndpointLink(builder, ComputeState.class, ctx.getRequest().endpointLink);
        QueryUtils.addTenantLinks(builder, tenantLinks);

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
     * @param tenantLinks
     * @return
     */
    private QueryTask queryForHostSystem(EnumerationContext ctx, String parentComputeLink,
            String moRefId, List<String> tenantLinks) {
        Builder builder = Query.Builder.create()
                .addFieldClause(ComputeState.FIELD_NAME_ID, moRefId)
                .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK, parentComputeLink);
        QueryUtils.addEndpointLink(builder, ComputeState.class, ctx.getRequest().endpointLink);
        QueryUtils.addTenantLinks(builder, tenantLinks);

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
