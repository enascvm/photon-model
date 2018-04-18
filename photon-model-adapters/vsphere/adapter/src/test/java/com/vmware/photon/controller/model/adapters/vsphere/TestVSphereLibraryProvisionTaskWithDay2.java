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

import org.junit.Test;

import com.vmware.photon.controller.model.adapters.vsphere.util.connection.BasicConnection;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.GetMoRef;
import com.vmware.photon.controller.model.resources.ComputeService;

/**
 * Test case for day 2 operations for provisioning through library item
 */
public class TestVSphereLibraryProvisionTaskWithDay2 extends TestVSphereLibraryProvisionTaskBase {

    @Test
    public void deployFromLibraryWithReboot() throws Throwable {
        ComputeService.ComputeState vm = provisionVMAndGetState();
        try {
            if (vm == null) {
                return;
            }
            // test reboot resource operation
            rebootVSphereVMAndWait(vm);
            // Verify that the disk is resized
            BasicConnection connection = createConnection();
            GetMoRef get = new GetMoRef(connection);
            verifyDiskSize(vm, get, HDD_DISK_SIZE);
        } finally {
            if (vm != null) {
                deleteVmAndWait(vm);
            }
        }
    }

    @Test
    public void deployFromLibraryAndSuspendVM() throws Throwable {
        ComputeService.ComputeState vm = provisionVMAndGetState();
        try {
            if (vm == null) {
                return;
            }
            // test suspend resource operation
            suspendVSphereVM(vm);
        } finally {
            if (vm != null) {
                deleteVmAndWait(vm);
            }
        }
    }

    @Test
    public void deployFromLibraryWithShutdown() throws Throwable {
        ComputeService.ComputeState vm = provisionVMAndGetState();
        try {
            if (vm == null) {
                return;
            }
            // test shutdown Guest OS resource operation
            shutdownGuestOS(vm);
            // Verify that the disk is resized and is reflected even for a POWERED_OFF vm
            BasicConnection connection = createConnection();
            GetMoRef get = new GetMoRef(connection);
            verifyDiskSize(vm, get, HDD_DISK_SIZE);
        } finally {
            if (vm != null) {
                deleteVmAndWait(vm);
            }
        }
    }

    @Test
    public void deployFromLibraryAndResetVM() throws Throwable {
        ComputeService.ComputeState vm = provisionVMAndGetState();
        try {
            if (vm == null) {
                return;
            }
            // test reset VM operation
            resetVSphereVM(vm);

            BasicConnection connection = createConnection();
            GetMoRef get = new GetMoRef(connection);
            verifyDiskSize(vm, get, HDD_DISK_SIZE);
        } finally {
            if (vm != null) {
                deleteVmAndWait(vm);
            }
        }
    }

    @Test
    public void deployFromLibraryAndPerformSnapshotOperations() throws Throwable {
        ComputeService.ComputeState vm = provisionVMAndGetState();
        try {
            if (vm == null) {
                return;
            }
            createSnapshotAndWait(this.endpoint.documentSelfLink, vm, false);
            createSnapshotAndWait(this.endpoint.documentSelfLink, vm, true);
            // this will create a child to the snapshot created in above statement
            revertToSnapshotAndWait(this.endpoint.documentSelfLink, vm);
            deleteSnapshotAndWait(this.endpoint.documentSelfLink, vm);
        } finally {
            if (vm != null) {
                deleteVmAndWait(vm);
            }
        }
    }

    @Test
    public void deployFromLibraryAndResizeVM() throws Throwable {
        ComputeService.ComputeState vm = provisionVMAndGetState();
        try {
            if (vm == null) {
                return;
            }
            // test resize VM operation
            resizeVM(vm);
        } finally {
            if (vm != null) {
                deleteVmAndWait(vm);
            }
        }
    }

    @Test
    public void deployFromLibraryAndTestSnapshotCleanUp() throws Throwable {
        ComputeService.ComputeState vm = provisionVMAndGetState();
        try {
            if (vm == null) {
                return;
            }
            // Creating two snapshots
            createSnapshotAndWait(this.endpoint.documentSelfLink, vm, false);
            createSnapshotAndWait(this.endpoint.documentSelfLink, vm, true);
            // this will create a child to the snapshot created in above statement
            verifySnapshotCleanUpAfterVmDelete(vm);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
