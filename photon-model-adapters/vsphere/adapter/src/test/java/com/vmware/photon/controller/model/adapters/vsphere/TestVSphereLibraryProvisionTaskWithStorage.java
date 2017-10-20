/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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
import static org.junit.Assert.assertNotNull;

import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.PROVIDER_DISK_UNIQUE_ID;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import com.vmware.photon.controller.model.adapters.vsphere.util.connection.BasicConnection;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.GetMoRef;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.vim25.VirtualDisk;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;

/**
 * Test cases for provisioning through library item with disks.
 */
public class TestVSphereLibraryProvisionTaskWithStorage extends TestVSphereLibraryProvisionTaskBase {

    @Test
    public void deployFromLibraryWithAdditionalDisks() throws Throwable {
        ComputeService.ComputeState vm = provisionVMAndGetState(true, true);
        try {
            if (vm == null) {
                return;
            }
            // Verify that the disk is resized
            BasicConnection connection = createConnection();
            GetMoRef get = new GetMoRef(connection);
            List<VirtualDisk> virtualDisks = fetchAllVirtualDisks(vm, get);
            assertEquals(3, virtualDisks.size());
            assertEquals(3, vm.diskLinks.size());

            List<DeferredResult<DiskService.DiskState>> disks = vm.diskLinks.stream()
                    .map(link -> {
                        Operation getOp = Operation
                                .createGet(this.host, link)
                                .setReferer(this.host.getReferer());
                        return this.host.sendWithDeferredResult(getOp, DiskService.DiskState.class);
                    }).collect(Collectors.toList());
            DeferredResult.allOf(disks).thenAccept(diskStates -> diskStates.stream().forEach(ds -> {
                assertNotNull(ds.customProperties);
                assertNotNull(ds.sourceImageReference);
                assertNotNull(ds.customProperties.get(PROVIDER_DISK_UNIQUE_ID));
            }));
        } finally {
            if (vm != null) {
                deleteVmAndWait(vm);
            }
        }
    }

    @Test
    public void deployFromLibraryWithStoragePolicy() throws Throwable {
        ComputeService.ComputeState vm = provisionVMAndGetState(true, false);
        try {
            if (vm == null) {
                return;
            }
            // Verify that the disk is resized
            BasicConnection connection = createConnection();
            GetMoRef get = new GetMoRef(connection);
            verifyDiskSize(vm, get, HDD_DISK_SIZE);
            verifyDiskProperties(vm, get);
        } finally {
            if (vm != null) {
                deleteVmAndWait(vm);
            }
        }
    }
}
