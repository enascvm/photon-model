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
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;

public class TestVSphereLibraryProvisionTask extends TestVSphereLibraryProvisionTaskBase {

    @Test
    public void deployFromLibrary() throws Throwable {
        ComputeState vm = provisionVMAndGetState();
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

    @Test
    public void deployFromLibraryCreateSnapshotsAndCheckSnapshotLimit() throws Throwable {
        ComputeState vm = provisionVMWithSnapshotLimitAndGetState();
        try {
            if (vm == null) {
                return;
            }
            createSnapshotAndWait(vm, false); // 1st snapshot will succeed
            createSnapshotAndWaitFailure(vm); // 2nd snapshot will fail, since snapshot limit is set to "1"
        } finally {
            if (vm != null) {
                deleteVmAndWait(vm);
            }
        }
    }
}
