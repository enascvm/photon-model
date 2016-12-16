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

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.adapters.vsphere.ProvisionContext.NetworkInterfaceStateWithDetails;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimPath;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.Connection;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.GetMoRef;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.LifecycleState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.DiskService.DiskType;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.IpAssignment;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.vim25.CustomizationSpec;
import com.vmware.vim25.InvalidPropertyFaultMsg;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.TaskInfoState;
import com.vmware.vim25.VirtualDeviceFileBackingInfo;
import com.vmware.vim25.VirtualDisk;
import com.vmware.vim25.VirtualEthernetCard;
import com.vmware.vim25.VirtualEthernetCardNetworkBackingInfo;
import com.vmware.vim25.VirtualMachineGuestOsIdentifier;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.OperationSequence;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 */
public class VSphereAdapterInstanceService extends StatelessService {

    public static final String SELF_LINK = VSphereUriPaths.INSTANCE_SERVICE;
    private static final int IP_CHECK_INTERVAL_SECONDS = 30;
    private static final int IP_CHECK_TOTAL_WAIT_SECONDS = 10 * 60;

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }

        op.setStatusCode(Operation.STATUS_CODE_CREATED);
        op.complete();

        ComputeInstanceRequest request = op.getBody(ComputeInstanceRequest.class);

        TaskManager mgr = new TaskManager(this, request.taskReference);

        // mark task as started
        mgr.patchTask(TaskStage.STARTED);
        if (request.isMockRequest) {
            handleMockRequest(mgr, request);
            return;
        }

        ProvisionContext.populateContextThen(this, createInitialContext(request), ctx -> {
            switch (request.requestType) {
            case CREATE:
                handleCreateInstance(ctx);
                break;
            case DELETE:
                handleDeleteInstance(ctx);
                break;
            default:
                Throwable error = new IllegalStateException(
                        "Unsupported requestType " + request.requestType);
                ctx.fail(error);
            }
        });
    }

    private ProvisionContext createInitialContext(ComputeInstanceRequest request) {
        ProvisionContext initialContext = new ProvisionContext(request);

        // global error handler: it marks the task as failed
        initialContext.errorHandler = failure -> {
            TaskManager mgr = new TaskManager(this, request.taskReference);
            mgr.patchTaskToFailure(failure);
        };

        initialContext.pool = VSphereIOThreadPoolAllocator.getPool(this);
        return initialContext;
    }

    private void handleCreateInstance(ProvisionContext ctx) {
        TaskManager mgr = new TaskManager(this, ctx.provisioningTaskReference);

        ctx.pool.submit(this, ctx.getAdapterManagementReference(), ctx.vSphereCredentials,
                (connection, ce) -> {
                    if (ctx.fail(ce)) {
                        return;
                    }

                    try {
                        InstanceClient client = new InstanceClient(connection, ctx.child,
                                ctx.parent, ctx.disks, ctx.nics, ctx.computeMoRef);

                        ComputeState state;

                        // all sides effect collected, patch model:
                        OperationJoin patchDisks = null;

                        if (ctx.templateMoRef != null) {
                            state = client.createInstanceFromTemplate(ctx.templateMoRef);
                        } else {
                            state = client.createInstance();
                            client.attachDisks(ctx.disks);
                            // attach disks, collecting side effects
                            patchDisks = createDiskPatch(ctx.disks);
                        }

                        if (state == null) {
                            // someone else won the race to create the vim
                            // assume they will patch the task if they have provisioned the vm
                            return;
                        }

                        // populate state, MAC address being very important
                        VmOverlay vmOverlay = client.enrichStateFromVm(state);

                        Operation finishTask = null;

                        for (NetworkInterfaceStateWithDetails nic : ctx.nics) {
                            // request guest customization while vm of powered off
                            NetworkInterfaceDescription desc = nic.description;
                            SubnetState subnet = nic.subnet;
                            if (subnet != null && desc != null && desc.assignment == IpAssignment.STATIC) {
                                CustomizationClient cc = new CustomizationClient(connection, ctx.child,
                                        VirtualMachineGuestOsIdentifier.OTHER_GUEST_64);
                                CustomizationSpec template = new CustomizationSpec();
                                cc.customizeNic(vmOverlay.getPrimaryMac(), desc, subnet, template);
                                cc.customizeDns(subnet.dnsServerAddresses, subnet.dnsSearchDomains,
                                        template);
                                ManagedObjectReference task = cc.customizeGuest(client.getVm(), template);

                                TaskInfo taskInfo = VimUtils.waitTaskEnd(connection, task);
                                if (taskInfo.getState() == TaskInfoState.ERROR) {
                                    VimUtils.rethrow(taskInfo.getError());
                                }
                            }
                        }

                        // power on machine before enrichment
                        if (ctx.child.powerState == PowerState.ON) {
                            new PowerStateClient(connection).changePowerState(client.getVm(),
                                    PowerState.ON, null, 0);
                            state.powerState = PowerState.ON;

                            Operation op = mgr.createTaskPatch(TaskStage.FINISHED);

                            Runnable runnable = createCheckForIpTask(ctx.pool, op, client.getVm(),
                                    connection.createUnmanagedCopy(),
                                    ctx.child.documentSelfLink);

                            ctx.pool.schedule(runnable,
                                    IP_CHECK_INTERVAL_SECONDS,
                                    TimeUnit.SECONDS);
                        } else {
                            // only finish the task without waiting for IP
                            finishTask = mgr.createTaskPatch(TaskStage.FINISHED);
                        }



                        if (ctx.templateMoRef != null) {
                            // as we're cloning, networkInterfaces are ignored and
                            // recreated based on the template
                            addNetworkLinksAfterClone(state, vmOverlay.getNics(), ctx);
                            addDiskLinksAfterClone(state, vmOverlay.getDisks(), ctx);
                        }

                        state.lifecycleState = LifecycleState.READY;
                        Operation patchResource = createComputeResourcePatch(state,
                                ctx.computeReference);

                        OperationSequence seq = OperationSequence
                                .create(patchResource);

                        if (patchDisks != null) {
                            seq = seq.next(patchDisks);
                        }

                        if (finishTask != null) {
                            seq = seq.next(finishTask);
                        }

                        seq.setCompletion(ctx.failTaskOnError())
                                .sendWith(this);
                    } catch (Exception e) {
                        ctx.fail(e);
                    }
                });
    }

    private Runnable createCheckForIpTask(VSphereIOThreadPool pool,
            Operation taskFinisher,
            ManagedObjectReference vmRef,
            Connection connection,
            String computeLink) {
        return new Runnable() {
            int attemptsLeft = IP_CHECK_TOTAL_WAIT_SECONDS / IP_CHECK_INTERVAL_SECONDS - 1;

            @Override
            public void run() {
                String ip;
                try {
                    GetMoRef get = new GetMoRef(connection);
                    // fetch enough to make guessPublicIpV4Address() work
                    Map<String, Object> props = get.entityProps(vmRef, VimPath.vm_guest_net);
                    VmOverlay vm = new VmOverlay(vmRef, props);
                    ip = vm.guessPublicIpV4Address();
                } catch (InvalidPropertyFaultMsg | RuntimeFaultFaultMsg e) {
                    log(Level.WARNING, "Error getting IP of vm %s, %s, aborting ",
                            VimUtils.convertMoRefToString(vmRef),
                            computeLink);
                    // complete the task, IP could be assigned during next enumeration cycle
                    taskFinisher.sendWith(VSphereAdapterInstanceService.this);
                    connection.close();
                    return;
                }

                if (ip != null) {
                    ComputeState state = new ComputeState();
                    state.address = ip;
                    // update compute
                    Operation.createPatch(VSphereAdapterInstanceService.this, computeLink)
                            .setBody(state)
                            .setCompletion((o, e) -> {
                                // finish task
                                taskFinisher.sendWith(VSphereAdapterInstanceService.this);
                            })
                            .sendWith(VSphereAdapterInstanceService.this);
                    connection.close();
                } else if (attemptsLeft > 0) {
                    attemptsLeft--;
                    log(Level.INFO, "IP of %s not ready, retrying", computeLink);
                    // reschedule
                    pool.schedule(this, IP_CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
                } else {
                    // still no IP after all
                    taskFinisher.sendWith(VSphereAdapterInstanceService.this);
                    connection.close();
                }
            }
        };
    }

    private void addDiskLinksAfterClone(ComputeState state, List<VirtualDisk> disks,
            ProvisionContext ctx) {
        if (state.diskLinks == null) {
            state.diskLinks = new ArrayList<>(2);
        } else {
            state.diskLinks.clear();
        }

        for (VirtualDisk disk : disks) {
            if (!(disk.getBacking() instanceof VirtualDeviceFileBackingInfo)) {
                continue;
            }
            VirtualDeviceFileBackingInfo backing = (VirtualDeviceFileBackingInfo) disk.getBacking();

            DiskState ds = new DiskState();
            ds.documentSelfLink = UriUtils.buildUriPath(
                    DiskService.FACTORY_LINK,
                    Utils.buildUUID(getHost().getIdHash()));

            ds.name = disk.getDeviceInfo().getLabel();
            ds.creationTimeMicros = Utils.getNowMicrosUtc();
            ds.type = DiskType.HDD;
            ds.regionId = ctx.parent.description.regionId;
            ds.capacityMBytes = disk.getCapacityInKB() / 1024;
            ds.sourceImageReference = VimUtils.datastorePathToUri(backing.getFileName());
            createDiskOnDemand(ds);
            state.diskLinks.add(ds.documentSelfLink);
        }
    }

    private void createDiskOnDemand(DiskState ds) {
        Operation.createPost(this, DiskService.FACTORY_LINK)
                .setBody(ds)
                .sendWith(this);
    }

    private void addNetworkLinksAfterClone(ComputeState state, List<VirtualEthernetCard> nics,
            ProvisionContext ctx) {
        if (state.networkInterfaceLinks == null) {
            state.networkInterfaceLinks = new ArrayList<>(2);
        } else {
            // ignore network config from compute and store network config produced
            // by vsphere after clone
            state.networkInterfaceLinks.clear();
        }

        for (VirtualEthernetCard nic : nics) {
            if (!(nic.getBacking() instanceof VirtualEthernetCardNetworkBackingInfo)) {
                continue;
            }
            VirtualEthernetCardNetworkBackingInfo backing = (VirtualEthernetCardNetworkBackingInfo) nic
                    .getBacking();

            NetworkInterfaceState iface = new NetworkInterfaceState();
            iface.documentSelfLink = UriUtils.buildUriPath(
                    NetworkInterfaceService.FACTORY_LINK,
                    Utils.buildUUID(getHost().getId()));
            iface.name = nic.getDeviceInfo().getLabel();
            createNetworkInterfaceOnDemand(iface, backing.getNetwork(),
                    ctx.getAdapterManagementReference(),
                    ctx.parent.description.regionId);
            state.networkInterfaceLinks.add(iface.documentSelfLink);
        }
    }

    private void createNetworkInterfaceOnDemand(
            NetworkInterfaceState iface,
            ManagedObjectReference network, URI adapterManagementReference, String regionId) {

        Query query = Query.Builder.create()
                .addFieldClause(ServiceDocument.FIELD_NAME_KIND,
                        Utils.buildKind(NetworkState.class))
                .addFieldClause(NetworkState.FIELD_NAME_ADAPTER_MANAGEMENT_REFERENCE,
                        adapterManagementReference.toString())
                .addFieldClause(NetworkState.FIELD_NAME_REGION_ID, regionId)
                .addFieldClause(QuerySpecification
                                .buildCompositeFieldName(NetworkState.FIELD_NAME_CUSTOM_PROPERTIES,
                                        CustomProperties.MOREF),
                        VimUtils.convertMoRefToString(network))
                .build();

        QueryTask qt = QueryTask.Builder.createDirectTask()
                .setQuery(query)
                .build();

        Operation.createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(qt)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("Error looking up network %s: %s", VimUtils
                                .convertMoRefToString(network), e.getMessage());
                        return;
                    }

                    List<String> links = o.getBody(QueryTask.class).results.documentLinks;
                    if (links.isEmpty()) {
                        logWarning("Could not locate network %s", VimUtils
                                .convertMoRefToString(network));
                        return;
                    }

                    iface.networkLink = links.get(0);
                    Operation.createPost(this, NetworkInterfaceService.FACTORY_LINK)
                            .setBody(iface)
                            .sendWith(this);
                })
                .sendWith(this);
    }

    private Operation selfPatch(ServiceDocument doc) {
        return Operation.createPatch(this, doc.documentSelfLink).setBody(doc);
    }

    private OperationJoin createDiskPatch(List<DiskState> disks) {
        if (disks == null || disks.isEmpty()) {
            return null;
        }

        return OperationJoin.create()
                .setOperations(disks.stream().map(this::selfPatch));
    }

    private Operation createComputeResourcePatch(ComputeState state, URI computeReference) {
        return Operation.createPatch(computeReference)
                .setBody(state);
    }

    private void handleDeleteInstance(ProvisionContext ctx) {
        TaskManager mgr = new TaskManager(this, ctx.provisioningTaskReference);

        ctx.pool.submit(this, ctx.getAdapterManagementReference(), ctx.vSphereCredentials,
                (conn, ce) -> {
                    if (ctx.fail(ce)) {
                        return;
                    }

                    try {
                        InstanceClient client = new InstanceClient(conn, ctx.child, ctx.parent,
                                ctx.disks, ctx.nics, null);
                        client.deleteInstance();

                        Operation finishTask = mgr.createTaskPatch(TaskStage.FINISHED);

                        OperationSequence seq = OperationSequence
                                .create(finishTask);

                        seq.setCompletion(ctx.logOnError())
                                .sendWith(this);
                    } catch (Exception e) {
                        ctx.fail(e);
                    }
                });
    }

    private void handleMockRequest(TaskManager mgr, ComputeInstanceRequest req) {
        mgr.patchTask(TaskStage.FINISHED);
    }
}
