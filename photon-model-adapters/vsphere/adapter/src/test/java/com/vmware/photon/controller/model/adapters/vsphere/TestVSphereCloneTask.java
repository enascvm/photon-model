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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.MOREF;
import static com.vmware.photon.controller.model.tasks.TestUtils.doPost;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.BasicConnection;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.GetMoRef;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskType;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.vim25.InvalidPropertyFaultMsg;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.vim25.SharesLevel;
import com.vmware.vim25.VirtualDisk;
import com.vmware.vim25.VirtualDiskFlatVer2BackingInfo;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;
import com.vmware.xenon.services.common.ServiceUriPaths;

public class TestVSphereCloneTask extends BaseVSphereAdapterTest {

    private ComputeDescription computeHostDescription;
    private ComputeState computeHost;

    private static final long FLOPPY_DISK_SIZE = 2048;
    private static final long HDD_DISK_SIZE = 62464;
    private static final long CLONE_HDD_DISK_SIZE = 63488;

    @Test
    public void createInstanceFromTemplate() throws Throwable {
        createInstanceFromTemplate(false);
    }

    @Test
    public void createInstanceFromTemplateWithAdditionDisks() throws Throwable {
        createInstanceFromTemplate(true);
    }

    @Test
    public void verifyBootDiskCustomization() throws Throwable {
        ComputeState vm = null;
        try {
            this.auth = createAuth();
            this.resourcePool = createResourcePool();

            if (isMock()) {
                createNetwork(networkId);
            }
            this.computeHostDescription = createComputeDescription();
            this.computeHost = createComputeHost(this.computeHostDescription);

            doRefresh();

            snapshotFactoryState("clone-refresh", NetworkService.class);
            ComputeDescription vmDescription = createVmDescription();
            vm = createVmState(vmDescription, true, null);

            // kick off a provision task to do the actual VM creation
            ProvisionComputeTaskState provisionTask = createProvisionTask(vm);
            awaitTaskEnd(provisionTask);

            vm = getComputeState(vm);

            // put fake moref in the vm
            if (isMock()) {
                ManagedObjectReference moref = new ManagedObjectReference();
                moref.setValue("vm-0");
                moref.setType(VimNames.TYPE_VM);
                CustomProperties.of(vm).put(MOREF, moref);
                vm = doPost(this.host, vm,
                        ComputeState.class,
                        UriUtils.buildUri(this.host, ComputeService.FACTORY_LINK));
            }

            if (!isMock()) {
                // Verify that the disk is resized
                BasicConnection connection = createConnection();
                GetMoRef get = new GetMoRef(connection);
                verifyDiskSize(vm, get, HDD_DISK_SIZE);
                verifyDiskProperties(vm, get);
            }
        } finally {
            if (!isMock() && vm != null) {
                deleteVmAndWait(vm);
            }
        }
    }

    @Test
    public void verifyBootDiskCustomizationWithStoragePolicy() throws Throwable {
        ComputeState vm = null;
        ComputeState clonedVm = null;
        try {
            this.auth = createAuth();
            this.resourcePool = createResourcePool();

            if (isMock()) {
                createNetwork(networkId);
            }
            this.computeHostDescription = createComputeDescription();
            this.computeHost = createComputeHost(this.computeHostDescription);

            doRefresh();

            snapshotFactoryState("clone-refresh", NetworkService.class);
            ComputeDescription vmDescription = createVmDescription();
            DiskService.DiskState bootDisk = createDiskWithStoragePolicy("boot", DiskType.HDD, 1,
                    getDiskUri(), HDD_DISK_SIZE, buildCustomProperties());
            vm = createVmState(vmDescription, true, bootDisk.documentSelfLink);

            // kick off a provision task to do the actual VM creation
            ProvisionComputeTaskState provisionTask = createProvisionTask(vm);
            awaitTaskEnd(provisionTask);

            vm = getComputeState(vm);

            // put fake moref in the vm
            if (isMock()) {
                ManagedObjectReference moref = new ManagedObjectReference();
                moref.setValue("vm-0");
                moref.setType(VimNames.TYPE_VM);
                CustomProperties.of(vm).put(MOREF, moref);
                vm = doPost(this.host, vm,
                        ComputeState.class,
                        UriUtils.buildUri(this.host, ComputeService.FACTORY_LINK));
            }

            // create state & desc of the clone
            ComputeDescription cloneDescription = createCloneDescription(vm.documentSelfLink);
            clonedVm = createCloneVmState(cloneDescription, true, false);

            provisionTask = createProvisionTask(clonedVm);
            awaitTaskEnd(provisionTask);

            clonedVm = getComputeState(clonedVm);

            if (!isMock()) {
                // Verify that the disk is resized
                BasicConnection connection = createConnection();
                GetMoRef get = new GetMoRef(connection);
                verifyDiskSize(vm, get, HDD_DISK_SIZE);
                verifyDiskSize(clonedVm, get, CLONE_HDD_DISK_SIZE);
                verifyDiskProperties(vm, get);
            }
        } finally {
            if (!isMock()) {
                cleanUpVm(vm, clonedVm);
            }
        }
    }

    private void createInstanceFromTemplate(boolean withAdditionalDisks) throws
            Throwable {
        ComputeState vm = null;
        ComputeState clonedVm = null;
        try {
            this.auth = createAuth();
            this.resourcePool = createResourcePool();

            if (isMock()) {
                createNetwork(networkId);
            }
            this.computeHostDescription = createComputeDescription();
            this.computeHost = createComputeHost(this.computeHostDescription);

            doRefresh();

            snapshotFactoryState("clone-refresh", NetworkService.class);
            ComputeDescription vmDescription = createVmDescription();
            vm = createVmState(vmDescription, false, null, withAdditionalDisks);

            // kick off a provision task to do the actual VM creation
            ProvisionComputeTaskState provisionTask = createProvisionTask(vm);
            awaitTaskEnd(provisionTask);

            vm = getComputeState(vm);

            // put fake moref in the vm
            if (isMock()) {
                ManagedObjectReference moref = new ManagedObjectReference();
                moref.setValue("vm-0");
                moref.setType(VimNames.TYPE_VM);
                CustomProperties.of(vm).put(MOREF, moref);
                vm = doPost(this.host, vm,
                        ComputeState.class,
                        UriUtils.buildUri(this.host, ComputeService.FACTORY_LINK));
            }

            // create state & desc of the clone
            ComputeDescription cloneDescription = createCloneDescription(vm.documentSelfLink);
            clonedVm = createCloneVmState(cloneDescription, false, withAdditionalDisks);

            provisionTask = createProvisionTask(clonedVm);
            awaitTaskEnd(provisionTask);

            clonedVm = getComputeState(clonedVm);

            if (!isMock()) {
                // Verify that the disk is resized
                BasicConnection connection = createConnection();
                GetMoRef get = new GetMoRef(connection);
                if (withAdditionalDisks) {
                    List<VirtualDisk> virtualDisks = fetchAllVirtualDisks(vm, get);
                    assertEquals(3, virtualDisks.size());
                } else {
                    verifyDiskSize(clonedVm, get, CLONE_HDD_DISK_SIZE);
                }
            }
        } finally {
            if (!isMock()) {
                cleanUpVm(vm, clonedVm);
            }
        }
    }

    private void cleanUpVm(ComputeState vm, ComputeState clonedVm) {
        if (vm != null) {
            deleteVmAndWait(vm);
        }
        if (clonedVm != null) {
            deleteVmAndWait(clonedVm);
        }
    }

    private void doRefresh() throws Throwable {
        enumerateComputes(this.computeHost);
    }

    private ComputeDescription createCloneDescription(String templateComputeLink) throws Throwable {
        ComputeDescription computeDesc = new ComputeDescription();

        computeDesc.id = nextName("cloned-vm");
        computeDesc.regionId = this.datacenterId;
        computeDesc.documentSelfLink = computeDesc.id;
        computeDesc.supportedChildren = new ArrayList<>();
        computeDesc.instanceAdapterReference = UriUtils
                .buildUri(this.host, VSphereUriPaths.INSTANCE_SERVICE);
        computeDesc.authCredentialsLink = this.auth.documentSelfLink;
        computeDesc.name = computeDesc.id;
        computeDesc.dataStoreId = this.dataStoreId;

        // set default cpu and memory
        computeDesc.cpuCount = 1;
        computeDesc.totalMemoryBytes = 1024 * 1024 * 1024; // 1GB

        CustomProperties.of(computeDesc)
                .put(CustomProperties.TEMPLATE_LINK, templateComputeLink);

        return doPost(this.host, computeDesc,
                ComputeDescription.class,
                UriUtils.buildUri(this.host, ComputeDescriptionService.FACTORY_LINK));
    }

    private ComputeState createVmState(ComputeDescription vmDescription, boolean
            diskCustomization, String bootDiskSelfLink, boolean withAdditionalDisks)
            throws Throwable {
        ComputeState computeState = new ComputeState();
        computeState.id = vmDescription.name;
        computeState.documentSelfLink = computeState.id;
        computeState.descriptionLink = vmDescription.documentSelfLink;
        computeState.resourcePoolLink = this.resourcePool.documentSelfLink;
        computeState.adapterManagementReference = getAdapterManagementReference();
        computeState.name = vmDescription.name;

        computeState.powerState = PowerState.ON;

        computeState.parentLink = this.computeHost.documentSelfLink;

        computeState.networkInterfaceLinks = new ArrayList<>(1);

        NetworkInterfaceState iface = new NetworkInterfaceState();
        iface.networkLink = findFirstNetwork();
        iface.name = "unit test";
        iface = TestUtils.doPost(this.host, iface,
                NetworkInterfaceState.class,
                UriUtils.buildUri(this.host, NetworkInterfaceService.FACTORY_LINK));

        computeState.networkInterfaceLinks.add(iface.documentSelfLink);

        computeState.diskLinks = new ArrayList<>(2);


        if (bootDiskSelfLink != null) {
            computeState.diskLinks.add(bootDiskSelfLink);
        } else {
            computeState.diskLinks.add(createDisk("boot", DiskType.HDD, 1, getDiskUri(),
                    HDD_DISK_SIZE, diskCustomization ? buildCustomProperties() : null).documentSelfLink);
        }
        computeState.diskLinks.add(createDisk("A", DiskType.FLOPPY, 2, null, FLOPPY_DISK_SIZE, null)
                .documentSelfLink);

        if (withAdditionalDisks) {
            computeState.diskLinks.add(createDiskWithDatastore("AdditionalDisk1", DiskType.HDD,
                    3, null, ADDITIONAL_DISK_SIZE, buildCustomProperties()).documentSelfLink);
            computeState.diskLinks
                    .add(createDiskWithStoragePolicy("AdditionalDisk2", DiskType.HDD, 4, null,
                            ADDITIONAL_DISK_SIZE, buildCustomProperties()).documentSelfLink);
        }
        String placementLink = findRandomResourcePoolOwningCompute();

        CustomProperties.of(computeState)
                .put(ComputeProperties.RESOURCE_GROUP_NAME, this.vcFolder)
                .put(ComputeProperties.PLACEMENT_LINK, placementLink);

        CustomProperties.of(computeState)
                .put(ComputeProperties.RESOURCE_GROUP_NAME, this.vcFolder);

        ComputeState returnState = TestUtils.doPost(this.host, computeState,
                ComputeState.class,
                UriUtils.buildUri(this.host, ComputeService.FACTORY_LINK));
        return returnState;
    }

    private ComputeState createVmState(ComputeDescription vmDescription, boolean
            diskCustomization, String bootDiskSelfLink)
            throws Throwable {
        return createVmState(vmDescription, diskCustomization, bootDiskSelfLink,
                false);
    }

    private void verifyDiskProperties(ComputeState vm, GetMoRef get)
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        VirtualDisk vd = fetchVirtualDisk(vm, get);
        assertEquals(SharesLevel.HIGH.value(), vd.getStorageIOAllocation().getShares()
                .getLevel().value());
        int shares = 2000;
        assertEquals(shares, vd.getStorageIOAllocation().getShares().getShares());
        Long limitIops = 100L;
        assertEquals(limitIops, vd.getStorageIOAllocation().getLimit());
        VirtualDiskFlatVer2BackingInfo backing = (VirtualDiskFlatVer2BackingInfo) vd.getBacking();
        assertTrue(backing.isThinProvisioned());
    }

    private String findRandomResourcePoolOwningCompute() {
        // find a random compute that has a resource pool
        String placementLink = "/link/to/nowhere";
        if (!isMock()) {
            Query q = createQueryForComputeResource();
            placementLink = findFirstMatching(q, ComputeState.class).documentSelfLink;
        }

        return placementLink;
    }

    private ComputeState createCloneVmState(ComputeDescription vmDescription,
            boolean isStoragePolicyBased, boolean withAdditionalDisks) throws Throwable {
        ComputeState computeState = new ComputeState();
        computeState.id = vmDescription.name;
        computeState.documentSelfLink = computeState.id;
        computeState.descriptionLink = vmDescription.documentSelfLink;
        computeState.resourcePoolLink = this.resourcePool.documentSelfLink;
        computeState.adapterManagementReference = getAdapterManagementReference();
        computeState.name = vmDescription.name;

        computeState.powerState = PowerState.ON;

        computeState.parentLink = this.computeHost.documentSelfLink;

        computeState.diskLinks = new ArrayList<>(1);
        if (isStoragePolicyBased) {
            computeState.diskLinks.add(createDiskWithStoragePolicy("boot", DiskType.HDD, 1,
                    getDiskUri(), CLONE_HDD_DISK_SIZE, buildCustomProperties()).documentSelfLink);
        } else {
            computeState.diskLinks.add(createDisk("boot", DiskType.HDD, 1, getDiskUri(),
                    CLONE_HDD_DISK_SIZE, null).documentSelfLink);
        }
        if (withAdditionalDisks) {
            computeState.diskLinks.add(createDiskWithDatastore("AdditionalDisk1", DiskType.HDD,
                    2, null, ADDITIONAL_DISK_SIZE, buildCustomProperties()).documentSelfLink);
        }

        CustomProperties.of(computeState)
                .put(ComputeProperties.RESOURCE_GROUP_NAME, this.vcFolder)
                .put(ComputeProperties.PLACEMENT_LINK, findRandomResourcePoolOwningCompute());

        ComputeState returnState = TestUtils.doPost(this.host, computeState,
                ComputeState.class,
                UriUtils.buildUri(this.host, ComputeService.FACTORY_LINK));
        return returnState;
    }

    private String findFirstNetwork() {
        Query q = Builder.create()
                .addFieldClause(ServiceDocument.FIELD_NAME_KIND,
                        Utils.buildKind(NetworkState.class))
                .addCompositeFieldClause(NetworkState.FIELD_NAME_CUSTOM_PROPERTIES, CustomProperties.TYPE,
                        VimNames.TYPE_NETWORK)
                .build();

        QueryTask task = QueryTask.Builder.createDirectTask()
                .setQuery(q)
                .build();

        Operation op = Operation
                .createPost(this.host, ServiceUriPaths.CORE_LOCAL_QUERY_TASKS)
                .setBody(task);

        Operation result = this.host.waitForResponse(op);

        try {
            return result.getBody(QueryTask.class).results.documentLinks.get(0);
        } catch (Exception e) {
            Assert.fail(e.getMessage());
            // never gets here
            return null;
        }
    }

    private ComputeDescription createVmDescription() throws Throwable {
        ComputeDescription computeDesc = new ComputeDescription();

        computeDesc.id = nextName("vm");
        computeDesc.regionId = this.datacenterId;
        computeDesc.documentSelfLink = computeDesc.id;
        computeDesc.supportedChildren = new ArrayList<>();
        computeDesc.instanceAdapterReference = UriUtils
                .buildUri(this.host, VSphereUriPaths.INSTANCE_SERVICE);
        computeDesc.authCredentialsLink = this.auth.documentSelfLink;
        computeDesc.name = computeDesc.id;
        computeDesc.dataStoreId = this.dataStoreId;

        // set default cpu and memory
        computeDesc.cpuCount = 1;
        computeDesc.totalMemoryBytes = 1024 * 1024 * 1024; // 1GB

        return doPost(this.host, computeDesc,
                ComputeDescription.class,
                UriUtils.buildUri(this.host, ComputeDescriptionService.FACTORY_LINK));
    }

    public URI getCdromUri() {
        String cdromUri = System.getProperty("vc.cdromUri");
        if (cdromUri == null) {
            return null;
        } else {
            return URI.create(cdromUri);
        }
    }

    public URI getDiskUri() {
        String diskUri = System.getProperty("vc.diskUri");
        if (diskUri == null) {
            return null;
        } else {
            return URI.create(diskUri);
        }
    }
}