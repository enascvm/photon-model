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

import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.MOREF;
import static com.vmware.photon.controller.model.tasks.TestUtils.doPost;

import org.junit.Test;

import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.BasicConnection;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.GetMoRef;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.xenon.common.UriUtils;

/**
 * Deals with all test cases related to storage.
 */
public class TestVSphereCloneTaskWithStorage extends TestVSphereCloneTaskBase {

    @Test
    public void createInstanceFromTemplateWithAdditionDisks() throws Throwable {
        createInstanceFromTemplate(true);
    }

    @Test
    public void verifyBootDiskCustomization() throws Throwable {
        ComputeService.ComputeState vm = null;
        try {
            this.auth = createAuth();
            this.resourcePool = createResourcePool();

            if (isMock()) {
                createNetwork(networkId);
            }
            this.computeHostDescription = createComputeDescription();
            this.computeHost = createComputeHost(this.computeHostDescription);

            EndpointService.EndpointState ep = createEndpointState(this.computeHost, this.computeHostDescription);
            this.endpoint = TestUtils.doPost(this.host, ep, EndpointService.EndpointState.class,
                    UriUtils.buildUri(this.host, EndpointService.FACTORY_LINK));

            doRefresh();

            snapshotFactoryState("clone-refresh", NetworkService.class);
            ComputeDescriptionService.ComputeDescription vmDescription = createVmDescription();
            vm = createVmState(vmDescription, true, null);

            // kick off a provision task to do the actual VM creation
            ProvisionComputeTaskService.ProvisionComputeTaskState provisionTask = createProvisionTask(vm);
            awaitTaskEnd(provisionTask);

            vm = getComputeState(vm);

            // put fake moref in the vm
            if (isMock()) {
                ManagedObjectReference moref = new ManagedObjectReference();
                moref.setValue("vm-0");
                moref.setType(VimNames.TYPE_VM);
                CustomProperties.of(vm).put(MOREF, moref);
                vm = doPost(this.host, vm,
                        ComputeService.ComputeState.class,
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
        ComputeService.ComputeState vm = null;
        ComputeService.ComputeState clonedVm = null;
        try {
            this.auth = createAuth();
            this.resourcePool = createResourcePool();

            if (isMock()) {
                createNetwork(networkId);
            }
            this.computeHostDescription = createComputeDescription();
            this.computeHost = createComputeHost(this.computeHostDescription);

            EndpointService.EndpointState ep = createEndpointState(this.computeHost, this.computeHostDescription);
            this.endpoint = TestUtils.doPost(this.host, ep, EndpointService.EndpointState.class,
                    UriUtils.buildUri(this.host, EndpointService.FACTORY_LINK));

            doRefresh();

            snapshotFactoryState("clone-refresh", NetworkService.class);
            ComputeDescriptionService.ComputeDescription vmDescription = createVmDescription();
            DiskService.DiskState bootDisk = createDiskWithStoragePolicy("boot", DiskService.DiskType.HDD, 1,
                    getDiskUri(), HDD_DISK_SIZE, buildCustomProperties());
            vm = createVmState(vmDescription, true, bootDisk.documentSelfLink);

            // kick off a provision task to do the actual VM creation
            ProvisionComputeTaskService.ProvisionComputeTaskState provisionTask = createProvisionTask(vm);
            awaitTaskEnd(provisionTask);

            vm = getComputeState(vm);

            // put fake moref in the vm
            if (isMock()) {
                ManagedObjectReference moref = new ManagedObjectReference();
                moref.setValue("vm-0");
                moref.setType(VimNames.TYPE_VM);
                CustomProperties.of(vm).put(MOREF, moref);
                vm = doPost(this.host, vm,
                        ComputeService.ComputeState.class,
                        UriUtils.buildUri(this.host, ComputeService.FACTORY_LINK));
            }

            // create state & desc of the clone
            ComputeDescriptionService.ComputeDescription cloneDescription = createCloneDescription(vm.documentSelfLink);
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
}
