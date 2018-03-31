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

import static com.vmware.photon.controller.model.ComputeProperties.COMPUTE_HOST_LINK_PROP_NAME;
import static com.vmware.photon.controller.model.adapters.vsphere.ClientUtils.findMatchingDiskState;
import static com.vmware.photon.controller.model.adapters.vsphere.ClientUtils.handleVirtualDeviceUpdate;
import static com.vmware.photon.controller.model.adapters.vsphere.ClientUtils.handleVirtualDiskUpdate;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.DATACENTER_SELF_LINK;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;

import io.netty.util.internal.StringUtil;

import org.apache.commons.collections.CollectionUtils;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.adapters.vsphere.ProvisionContext.NetworkInterfaceStateWithDetails;
import com.vmware.photon.controller.model.adapters.vsphere.network.DvsProperties;
import com.vmware.photon.controller.model.adapters.vsphere.network.NsxProperties;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimPath;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.Connection;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.GetMoRef;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.LifecycleState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.DiskService.DiskStateExpanded;
import com.vmware.photon.controller.model.resources.DiskService.DiskType;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.IpAssignment;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.util.ClusterUtil;
import com.vmware.photon.controller.model.util.PhotonModelUriUtils;
import com.vmware.vim25.CustomizationSpec;
import com.vmware.vim25.InvalidPropertyFaultMsg;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.TaskInfoState;
import com.vmware.vim25.VirtualCdrom;
import com.vmware.vim25.VirtualDevice;
import com.vmware.vim25.VirtualDeviceBackingInfo;
import com.vmware.vim25.VirtualDisk;
import com.vmware.vim25.VirtualEthernetCard;
import com.vmware.vim25.VirtualEthernetCardDistributedVirtualPortBackingInfo;
import com.vmware.vim25.VirtualEthernetCardNetworkBackingInfo;
import com.vmware.vim25.VirtualEthernetCardOpaqueNetworkBackingInfo;
import com.vmware.vim25.VirtualFloppy;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceStateCollectionUpdateRequest;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.services.common.QueryTask;

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
            handleMockRequest(mgr);
            return;
        }

        ProvisionContext.populateContextThen(this, createInitialContext(request, op), ctx -> {
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

    private ProvisionContext createInitialContext(ComputeInstanceRequest request, Operation op) {
        ProvisionContext initialContext = new ProvisionContext(this, request, op);

        initialContext.pool = VSphereIOThreadPoolAllocator.getPool(this);
        return initialContext;
    }

    private void handleCreateInstance(ProvisionContext ctx) {

        ctx.pool.submit(ctx.getAdapterManagementReference(), ctx.vSphereCredentials,
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

                        Operation[] finishTask = new Operation[1];

                        for (NetworkInterfaceStateWithDetails nic : ctx.nics) {
                            // request guest customization while vm of powered off

                            SubnetState subnet = nic.subnet;
                            if (subnet != null && nic.description != null
                                    && nic.description.assignment == IpAssignment.STATIC) {
                                CustomizationClient cc = new CustomizationClient(connection,
                                        ctx.child, vmOverlay.getGuestId());
                                CustomizationSpec template = new CustomizationSpec();
                                cc.customizeNic(vmOverlay.getPrimaryMac(), ctx.child.hostName,
                                        nic.address, subnet, template);
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
                            Boolean awaitIp = CustomProperties.of(ctx.child)
                                    .getBoolean(ComputeProperties.CUSTOM_PROP_COMPUTE_AWAIT_IP,
                                            true);
                            if (awaitIp) {
                                Runnable runnable = createCheckForIpTask(ctx.pool, op,
                                        client.getVm(),
                                        connection.createUnmanagedCopy(),
                                        ctx.child.documentSelfLink, ctx);

                                ctx.pool.schedule(runnable,
                                        IP_CHECK_INTERVAL_SECONDS,
                                        TimeUnit.SECONDS);
                            } else {
                                finishTask[0] = op;
                            }
                        } else {
                            // only finish the task without waiting for IP
                            finishTask[0] = ctx.mgr.createTaskPatch(TaskStage.FINISHED);
                        }

                        updateNicsAfterProvisionSuccess(vmOverlay.getNics(), ctx);
                        updateDiskLinksAfterProvisionSuccess(state, vmOverlay.getDisks(), ctx);

                        state.lifecycleState = LifecycleState.READY;

                        // Find the host link where the computed is provisioned and patch the
                        // compute state.
                        queryHostDocumentAndUpdateCompute(ctx, vmOverlay.getHost())
                                .thenCompose(links -> {
                                    ComputeState hostState = links.iterator().next();
                                    CustomProperties.of(state)
                                            // set the datacenter delf link from host state
                                            .put(DATACENTER_SELF_LINK, CustomProperties.of(hostState).getString(DATACENTER_SELF_LINK))
                                            // set the host state self link
                                            .put(COMPUTE_HOST_LINK_PROP_NAME, hostState.documentSelfLink);
                                    return createComputeResourcePatch(state, ctx.computeReference);
                                }).whenComplete((o, e) -> {
                                    if (e != null) {
                                        ctx.fail(e);
                                        return;
                                    }
                                    if (finishTask.length > 0) {
                                        finishTask[0].sendWith(this);
                                    }
                                });
                    } catch (Exception e) {
                        ctx.fail(e);
                    }
                });
    }

    /**
     * Query with the MangedObjectReference of the compute host where the VM is provisioned to
     * get the self link of host ComputeState and update the VM ComputeState with the link.
     */
    private DeferredResult<List<ComputeState>> queryHostDocumentAndUpdateCompute(ProvisionContext ctx,
            ManagedObjectReference hostMoref) {
        String moref = VimUtils.convertMoRefToString(hostMoref);

        QueryTask.Query.Builder builder = QueryTask.Query.Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addCompositeFieldClause(ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                        CustomProperties.MOREF, moref);
        QueryUtils.addEndpointLink(builder, ComputeState.class, ctx.child.endpointLink);

        QueryUtils.QueryTop<ComputeState> computeHostQuery = new QueryUtils.QueryTop<>(
                this.getHost(), builder.build(), ComputeState.class, ctx.parent.tenantLinks);

        computeHostQuery.setMaxResultsLimit(1);
        computeHostQuery.setClusterType(ClusterUtil.ServiceTypeCluster.INVENTORY_SERVICE);

        return computeHostQuery.collectDocuments(Collectors.toList());
    }

    private List<Operation> createUpdateIPOperationsForComputeAndNics(String computeLink,
            String ip, Map<String, List<String>> ipV4Addresses, ProvisionContext ctx) {
        List<Operation> updateIpAddressOperations = new ArrayList<>();

        if (ip != null) {
            ComputeState state = new ComputeState();
            state.address = ip;
            // update compute
            Operation updateIpAddress = Operation
                    .createPatch(PhotonModelUriUtils.createInventoryUri(getHost(), computeLink))
                    .setBody(state);
            updateIpAddressOperations.add(updateIpAddress);
        }

        if (ipV4Addresses != null) {
            int sizeIpV4Addresses = ipV4Addresses.size();
            for (NetworkInterfaceStateWithDetails nic : ctx.nics) {
                String deviceKey = null;
                deviceKey = VmOverlay.getDeviceKey(nic);
                if (deviceKey == null && nic.deviceIndex < sizeIpV4Addresses) {
                    deviceKey = Integer.toString(nic.deviceIndex);
                }
                if (deviceKey != null) {
                    List<String> ipsV4 = ipV4Addresses.containsKey(deviceKey) ? ipV4Addresses.get(deviceKey) : Collections.emptyList();
                    if (ipsV4.size() > 0) {
                        NetworkInterfaceState patchNic = new NetworkInterfaceState();
                        // if nic has multiple ip addresses for ipv4 only pick 1st ip address
                        patchNic.address = ipsV4.get(0);
                        Operation updateAddressNetWorkInterface = Operation
                                .createPatch(PhotonModelUriUtils.createInventoryUri(getHost(),
                                        nic.documentSelfLink)).setBody(patchNic);
                        updateIpAddressOperations.add(updateAddressNetWorkInterface);
                    } else {
                        log(Level.WARNING, "Address is not going to be updated in network "
                                + "interface state: [%], deviceKey: [%s] was not "
                                + "found in "
                                + "ipV4Addresses: "
                                + "[%s]", nic.documentSelfLink, deviceKey, ipV4Addresses.keySet());

                    }
                } else {
                    log(Level.WARNING, "Address is not going to be updated in network interface "
                            + "state: [%s] deviceKey is null", nic.documentSelfLink);
                }
            }
        }
        return updateIpAddressOperations;
    }

    private Runnable createCheckForIpTask(VSphereIOThreadPool pool,
            Operation taskFinisher,
            ManagedObjectReference vmRef,
            Connection connection,
            String computeLink, ProvisionContext ctx) {
        return new Runnable() {
            int attemptsLeft = IP_CHECK_TOTAL_WAIT_SECONDS / IP_CHECK_INTERVAL_SECONDS - 1;

            @Override
            public void run() {
                String ip;
                Map<String, List<String>> ipV4Addresses = null;
                try {
                    GetMoRef get = new GetMoRef(connection);
                    // fetch enough to make guessPublicIpV4Address() work
                    Map<String, Object> props = get.entityProps(vmRef, VimPath.vm_guest_net);
                    VmOverlay vm = new VmOverlay(vmRef, props);
                    ip = vm.findPublicIpV4Address(ctx.nics);
                    ipV4Addresses = vm.getMapNic2IpV4Addresses();
                } catch (InvalidPropertyFaultMsg | RuntimeFaultFaultMsg e) {
                    log(Level.WARNING, "Error getting IP of vm %s, %s, aborting ",
                            VimUtils.convertMoRefToString(vmRef),
                            computeLink);
                    // complete the task, IP could be assigned during next enumeration cycle
                    taskFinisher.sendWith(VSphereAdapterInstanceService.this);
                    connection.close();
                    return;
                }

                if (ip != null && ipV4Addresses.entrySet()
                        .stream()
                        .allMatch(entry -> !entry.getValue().isEmpty())) {
                    connection.close();
                    List<String> ips = ipV4Addresses.values().stream().flatMap(List::stream)
                            .collect(Collectors.toList());
                    List<Operation> updateIpAddressOperations = createUpdateIPOperationsForComputeAndNics(
                            computeLink, ip, ipV4Addresses, ctx);
                    OperationJoin.create(updateIpAddressOperations)
                            .setCompletion((o, e) -> {
                                log(Level.INFO, "Update compute IP [%s] and networkInterfaces ip"
                                                + " addresses [%s] for computeLink [%s]: ", ip, ips, computeLink);
                                // finish task
                                taskFinisher.sendWith(VSphereAdapterInstanceService.this);
                            })
                            .sendWith(VSphereAdapterInstanceService.this);
                } else if (attemptsLeft > 0) {
                    attemptsLeft--;
                    log(Level.INFO, "IP of %s not ready, retrying", computeLink);
                    // reschedule
                    pool.schedule(this, IP_CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
                } else {
                    connection.close();
                    if (ip == null && ipV4Addresses.entrySet().stream()
                            .allMatch(entry -> entry.getValue().isEmpty())) {
                        log(Level.INFO, "IP of %s are not ready, giving up", computeLink);
                        taskFinisher.sendWith(VSphereAdapterInstanceService.this);
                    } else {
                        // not all ips are ready still update the ones that are ready
                        List<Operation> updateIpAddressOperations = createUpdateIPOperationsForComputeAndNics(
                                computeLink, ip, ipV4Addresses, ctx);
                        List<String> ips = ipV4Addresses.values().stream().flatMap(List::stream)
                                .collect(Collectors.toList());
                        OperationJoin.create(updateIpAddressOperations)
                                .setCompletion((o, e) -> {
                                    log(Level.INFO,
                                            "Not all ips are ready. Update compute IP [%s] and "
                                                    + "networkInterfaces ip addresses [%s] for "
                                                    + "computeLink [%s]: ", ip != null ? ip : "",
                                            ips, computeLink);
                                    taskFinisher.sendWith(VSphereAdapterInstanceService.this);
                                })
                                .sendWith(VSphereAdapterInstanceService.this);
                    }
                }
            }
        };
    }

    private NetworkInterfaceStateWithDetails findNic(ProvisionContext ctx, String key, String value) {
        NetworkInterfaceStateWithDetails nic = null;
        nic = ctx.nics.stream().filter(nics -> nics.subnet != null && nics.subnet
                .customProperties.containsKey(key) &&
                nics.subnet.customProperties.get(key).contains(value))
                .findFirst().orElse(null);
        if (nic == null) {
            nic = ctx.nics.stream().filter(nics -> nics.network != null && nics.network
                    .customProperties.containsKey(key) &&
                    nics.network.customProperties.get(key).contains
                            (value)).findFirst().orElse(null);
        }
        return nic;
    }

    /**
     * Update the details of nics into compute state after the provisioning is successful
     */
    private void updateNicsAfterProvisionSuccess(List<VirtualEthernetCard> virtualEthernetCards, ProvisionContext ctx) {
        boolean changed = false;
        NetworkInterfaceState nic = null;

        if (ctx.nics.size() == 0) {
            return;
        }
        for (VirtualEthernetCard virtualEthernetCard: virtualEthernetCards) {
            VirtualDeviceBackingInfo deviceBackingInfo = virtualEthernetCard.getBacking();
            if (deviceBackingInfo instanceof VirtualEthernetCardDistributedVirtualPortBackingInfo) {
                VirtualEthernetCardDistributedVirtualPortBackingInfo info =
                        (VirtualEthernetCardDistributedVirtualPortBackingInfo) deviceBackingInfo;
                String portGroupKey = info.getPort().getPortgroupKey();
                nic = findNic(ctx, DvsProperties.PORT_GROUP_KEY, portGroupKey);

            } else if (deviceBackingInfo instanceof VirtualEthernetCardOpaqueNetworkBackingInfo) {
                VirtualEthernetCardOpaqueNetworkBackingInfo info =
                        (VirtualEthernetCardOpaqueNetworkBackingInfo) deviceBackingInfo;
                String opaqueNetworkId = info.getOpaqueNetworkId();
                nic = findNic(ctx, NsxProperties.OPAQUE_NET_ID, opaqueNetworkId);

            } else if ( deviceBackingInfo instanceof VirtualEthernetCardNetworkBackingInfo) {
                VirtualEthernetCardNetworkBackingInfo info =
                        (VirtualEthernetCardNetworkBackingInfo) deviceBackingInfo;
                String deviceName = info.getDeviceName();
                nic = ctx.nics.stream().filter(nics -> deviceName != null && nics.network != null
                        && nics.network.name != null && nics.network.name.equals(deviceName))
                        .findFirst().orElse(null);
            }

            if (nic != null) {
                if (nic.customProperties == null) {
                    nic.customProperties = new HashMap<>(1);
                }
                if (!StringUtil.isNullOrEmpty(virtualEthernetCard.getExternalId())) {
                    // Update nic external id
                    nic.customProperties.put(CustomProperties.NIC_EXTERNAL_ID,
                            virtualEthernetCard.getExternalId());
                    changed = true;
                }
                if (!StringUtil.isNullOrEmpty(virtualEthernetCard.getMacAddress())) {
                    // Update nic mac address
                    nic.customProperties.put(CustomProperties.NIC_MAC_ADDRESS,
                            virtualEthernetCard.getMacAddress());
                    changed = true;
                }
                if (changed) {
                    NetworkInterfaceState patchNic = new NetworkInterfaceState();
                    patchNic.customProperties = nic.customProperties;
                    Operation.createPatch(
                            PhotonModelUriUtils.createInventoryUri(getHost(), nic.documentSelfLink))
                            .setBody(patchNic)
                            .sendWith(this);
                }
            } else {
                log(Level.WARNING, "NetworkInterfaceState not found for vsphere network adapter "
                                + "with mac address:"
                        + " [%s]. The custom properties NIC_EXTERNAL_ID or NIC_MAC_ADDRESS were "
                        + "not updated for: [%s]", virtualEthernetCard.getMacAddress(),
                        ctx.nics.stream().map(ic -> ic.documentSelfLink).collect(Collectors.toList())
                        .toString());
            }
        }
    }

    /**
     * Update the details of the disk into compute state after the provisioning is successful
     */
    private void updateDiskLinksAfterProvisionSuccess(ComputeState state, List<VirtualDevice> disks,
            ProvisionContext ctx) {
        ArrayList<String> diskLinks = new ArrayList<>(disks.size());
        // Fill in the disk links from the input to the ComputeState, as it may contain non hdd
        // disk as well. For ex, Floppy or CD-Rom
        ctx.disks.stream().forEach(ds -> diskLinks.add(ds.documentSelfLink));

        // Handle all the HDD disk
        for (VirtualDevice disk : disks) {
            DiskStateExpanded matchedDs = findMatchingDiskState(disk, ctx.disks);
            if (disk instanceof VirtualDisk) {
                handleVirtualDiskUpdate(ctx.child.endpointLink, matchedDs, (VirtualDisk) disk,
                        diskLinks, ctx.parent.description.regionId, this, CustomProperties.of(state)
                                .getString(CustomProperties.MOREF), CustomProperties.of(state)
                                .getString(CustomProperties.DATACENTER_SELF_LINK), null, null)
                        .sendWith(this);
            } else if (disk instanceof VirtualCdrom) {
                handleVirtualDeviceUpdate(ctx.child.endpointLink, matchedDs, DiskType.CDROM, disk,
                        diskLinks, ctx.parent.description.regionId, this, true, CustomProperties.of(state)
                                .getString(CustomProperties.DATACENTER_SELF_LINK))
                        .sendWith(this);
            } else if (disk instanceof VirtualFloppy) {
                handleVirtualDeviceUpdate(ctx.child.endpointLink, matchedDs, DiskType.FLOPPY, disk,
                        diskLinks, ctx.parent.description.regionId, this, true, CustomProperties.of(state)
                                .getString(CustomProperties.DATACENTER_SELF_LINK))
                        .sendWith(this);
            }
        }
        state.diskLinks = diskLinks;
    }

    private DeferredResult<Operation> createComputeResourcePatch(ComputeState state, URI
            computeReference) {
        Operation patchOp = Operation
                .createPatch(PhotonModelUriUtils.createInventoryUri(getHost(), computeReference))
                .setBody(state);
        return this.sendWithDeferredResult(patchOp);
    }

    private void handleDeleteInstance(ProvisionContext ctx) {

        ctx.pool.submit(ctx.getAdapterManagementReference(), ctx.vSphereCredentials,
                (conn, ce) -> {
                    if (ctx.fail(ce)) {
                        return;
                    }

                    try {
                        InstanceClient client = new InstanceClient(conn, ctx);
                        client.deleteInstance(this.getHost());
                        if (CollectionUtils.isNotEmpty(ctx.child.diskLinks) && ctx.child
                                .diskLinks.size() == ctx.disks.size()) {
                            // No removal hence finish the operation
                            ctx.mgr.finishTask();
                            return;
                        }
                        // Now update the compute diskLinks with the latest list so that deletion
                        // of compute will not mark disks as deleted.
                        ctx.child.diskLinks.removeAll(ctx.disks.stream().map(disk -> disk.documentSelfLink)
                                .collect(Collectors.toList()));
                        updateComputeStateDiskLinks(ctx, ctx.child);
                    } catch (Exception e) {
                        ctx.fail(e);
                    }
                });
    }

    private void handleMockRequest(TaskManager mgr) {
        mgr.patchTask(TaskStage.FINISHED);
    }

    /**
     * Remove diskLink from ComputeState by sending a ServiceStateCollectionUpdateRequest.
     */
    public void updateComputeStateDiskLinks(ProvisionContext ctx, ComputeState computeState) {
        Map<String, Collection<Object>> collectionsToRemove = Collections
                .singletonMap(ComputeService.ComputeState.FIELD_NAME_DISK_LINKS,
                        new ArrayList<>(computeState.diskLinks));

        ServiceStateCollectionUpdateRequest updateDiskLinksRequest = ServiceStateCollectionUpdateRequest
                .create(null, collectionsToRemove);

        Operation.createPatch(PhotonModelUriUtils
                .createInventoryUri(this.getHost(), computeState.documentSelfLink))
                .setBody(updateDiskLinksRequest)
                .setReferer(this.getUri())
                .setCompletion((op, t) -> {
                    if (t != null) {
                        log(Level.INFO, "Not able to update the compute state with the detached "
                                + "disks %s", t.getMessage());
                    }
                    ctx.mgr.finishTask();
                })
                .sendWith(this.getHost());

    }
}
