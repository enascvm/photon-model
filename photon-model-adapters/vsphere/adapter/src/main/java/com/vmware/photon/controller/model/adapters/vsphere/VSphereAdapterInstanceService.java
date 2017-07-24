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

import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.DISK_CONTROLLER_NUMBER;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.LIMIT_IOPS;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.PROVIDER_DISK_UNIQUE_ID;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.SHARES;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.SHARES_LEVEL;

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
import com.vmware.photon.controller.model.resources.DiskService.DiskStateExpanded;
import com.vmware.photon.controller.model.resources.DiskService.DiskType;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.IpAssignment;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.vim25.CustomizationSpec;
import com.vmware.vim25.InvalidPropertyFaultMsg;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.vim25.StorageIOAllocationInfo;
import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.TaskInfoState;
import com.vmware.vim25.VirtualDeviceFileBackingInfo;
import com.vmware.vim25.VirtualDisk;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationSequence;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;


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

        TaskManager mgr = new TaskManager(this, request.taskReference, request.resourceLink());

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
        ProvisionContext initialContext = new ProvisionContext(this, request);

        initialContext.pool = VSphereIOThreadPoolAllocator.getPool(this);
        return initialContext;
    }

    private void handleCreateInstance(ProvisionContext ctx) {

        ctx.pool.submit(this, ctx.getAdapterManagementReference(), ctx.vSphereCredentials,
                (connection, ce) -> {
                    if (ctx.fail(ce)) {
                        return;
                    }

                    try {
                        InstanceClient client = new InstanceClient(connection, ctx);

                        ComputeState state;

                        if (ctx.templateMoRef != null) {
                            state = client.createInstanceFromTemplate(ctx.templateMoRef);
                        } else if (ctx.image != null) {
                            ManagedObjectReference moRef = CustomProperties.of(ctx.image)
                                    .getMoRef(CustomProperties.MOREF);
                            if (moRef != null) {
                                // the image is backed by a template VM
                                state = client.createInstanceFromTemplate(moRef);
                            } else {
                                // library item
                                state = client.createInstanceFromLibraryItem(ctx.image);
                            }
                        } else if (ctx.snapshotMoRef != null) {
                            state = client.createInstanceFromSnapshot();
                        } else {
                            state = client.createInstance();
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

                            SubnetState subnet = nic.subnet;
                            if (subnet != null && nic.description != null
                                    && nic.description.assignment == IpAssignment.STATIC) {
                                CustomizationClient cc = new CustomizationClient(connection,
                                        ctx.child, vmOverlay.getGuestId());
                                CustomizationSpec template = new CustomizationSpec();
                                cc.customizeNic(vmOverlay.getPrimaryMac(), ctx.child.hostName, nic.address, subnet, template);
                                cc.customizeDns(subnet.dnsServerAddresses, subnet.dnsSearchDomains,
                                        template);
                                ManagedObjectReference task = cc
                                        .customizeGuest(client.getVm(), template);

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

                            Operation op = ctx.mgr.createTaskPatch(TaskStage.FINISHED);

                            Runnable runnable = createCheckForIpTask(ctx.pool, op, client.getVm(),
                                    connection.createUnmanagedCopy(),
                                    ctx.child.documentSelfLink);

                            ctx.pool.schedule(runnable,
                                    IP_CHECK_INTERVAL_SECONDS,
                                    TimeUnit.SECONDS);
                        } else {
                            // only finish the task without waiting for IP
                            finishTask = ctx.mgr.createTaskPatch(TaskStage.FINISHED);
                        }

                        updateDiskLinksAfterProvisionSuccess(state, vmOverlay.getDisks(), ctx);

                        state.lifecycleState = LifecycleState.READY;
                        Operation patchResource = createComputeResourcePatch(state,
                                ctx.computeReference);

                        OperationSequence seq = OperationSequence.create(patchResource);
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
                    log(Level.INFO, "IP of %s not ready, giving up", computeLink);
                    taskFinisher.sendWith(VSphereAdapterInstanceService.this);
                    connection.close();
                }
            }
        };
    }

    /**
     * Update the details of the disk into compute state after the provisioning is successful
     */
    private void updateDiskLinksAfterProvisionSuccess(ComputeState state, List<VirtualDisk> disks,
            ProvisionContext ctx) {
        ArrayList<String> diskLinks = new ArrayList<>(disks.size());
        // Fill in the disk links from the input to the ComputeState, as it may contain non hdd
        // disk as well. For ex, Floppy or CD-Rom
        ctx.disks.stream().forEach(ds -> diskLinks.add(ds.documentSelfLink));

        for (VirtualDisk disk : disks) {
            if (!(disk.getBacking() instanceof VirtualDeviceFileBackingInfo)) {
                continue;
            }
            VirtualDeviceFileBackingInfo backing = (VirtualDeviceFileBackingInfo) disk.getBacking();
            DiskStateExpanded matchedDs = findMatchingDiskState(disk, ctx.disks);
            if (matchedDs == null) {
                // This is the new disk, hence add it to the list
                DiskState ds = new DiskState();
                ds.documentSelfLink = UriUtils.buildUriPath(
                        DiskService.FACTORY_LINK, getHost().nextUUID());

                ds.name = disk.getDeviceInfo().getLabel();
                ds.creationTimeMicros = Utils.getNowMicrosUtc();
                ds.type = DiskType.HDD;
                ds.regionId = ctx.parent.description.regionId;
                ds.capacityMBytes = disk.getCapacityInKB() / 1024;
                ds.sourceImageReference = VimUtils.datastorePathToUri(backing.getFileName());
                updateDiskStateFromVirtualDisk(disk, ds);
                if (disk.getStorageIOAllocation() != null) {
                    StorageIOAllocationInfo storageInfo = disk.getStorageIOAllocation();
                    CustomProperties.of(ds)
                            .put(SHARES, storageInfo.getShares().getShares())
                            .put(LIMIT_IOPS, storageInfo.getLimit())
                            .put(SHARES_LEVEL, storageInfo.getShares().getLevel().value());
                }
                createDiskOnDemand(ds);
                diskLinks.add(ds.documentSelfLink);
            } else {
                // This is known disk, hence update with the provisioned attributes.
                matchedDs.sourceImageReference = VimUtils.datastorePathToUri(backing.getFileName());
                updateDiskStateFromVirtualDisk(disk, matchedDs);
                createDiskPatch(matchedDs);
            }

        }
        state.diskLinks = diskLinks;
    }

    /**
     * Capture virtual disk attributes in the disk state for reference.
     */
    private void updateDiskStateFromVirtualDisk(VirtualDisk vd, DiskState disk) {
        CustomProperties.of(disk)
                .put(PROVIDER_DISK_UNIQUE_ID, vd.getDiskObjectId());
    }

    private DiskStateExpanded findMatchingDiskState(VirtualDisk vd, List<DiskStateExpanded> disks) {
        // Step 1: Match if there are matching bootOrder number with Unit number of disk
        // Step 2: If not, then find a custom property to match the bootOrder with the unit number
        // Step 3: If no match found then this is the new disk so return null
        if (disks == null || disks.isEmpty()) {
            return null;
        }

        return disks.stream().filter(ds -> {
            boolean isFound = (ds.bootOrder != null && (ds.bootOrder - 1) == vd
                    .getUnitNumber());
            if (!isFound) {
                // Now check custom properties for controller unit number
                if (ds.customProperties != null && ds.customProperties.get
                        (DISK_CONTROLLER_NUMBER) != null) {
                    int unitNumber = Integer.parseInt(ds.customProperties.get
                            (DISK_CONTROLLER_NUMBER));
                    isFound = unitNumber == vd.getUnitNumber();
                }
            }
            return isFound;
        }).findFirst().orElse(null);
    }

    private void createDiskOnDemand(DiskState ds) {
        Operation.createPost(this, DiskService.FACTORY_LINK)
                .setBody(ds)
                .sendWith(this);
    }

    private void createDiskPatch(DiskState ds) {
        Operation.createPatch(this, ds.documentSelfLink)
                .setBody(ds)
                .sendWith(this);
    }

    private Operation createComputeResourcePatch(ComputeState state, URI computeReference) {
        return Operation.createPatch(computeReference)
                .setBody(state);
    }

    private void handleDeleteInstance(ProvisionContext ctx) {

        ctx.pool.submit(this, ctx.getAdapterManagementReference(), ctx.vSphereCredentials,
                (conn, ce) -> {
                    if (ctx.fail(ce)) {
                        return;
                    }

                    try {
                        InstanceClient client = new InstanceClient(conn, ctx);
                        client.deleteInstance();

                        ctx.mgr.finishTask();
                    } catch (Exception e) {
                        ctx.fail(e);
                    }
                });
    }

    private void handleMockRequest(TaskManager mgr, ComputeInstanceRequest req) {
        mgr.patchTask(TaskStage.FINISHED);
    }
}
