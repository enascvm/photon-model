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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.vmware.photon.controller.model.adapters.vsphere.ovf.ImportOvfRequest;
import com.vmware.photon.controller.model.adapters.vsphere.ovf.OvfImporterService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService;
import com.vmware.xenon.common.Operation;

/**
 * OVF provision test cases with disks.
 */
public class TestVSphereOvfProvisionTaskWithStorage extends TestVSphereOvfProvisionTaskBase {
    @Test
    public void deployOvfWithStoragePolicy() throws Throwable {
        deployOvf(true);
    }

    @Test
    public void deployOvfWithAdditionalDisks() throws Throwable {
        deployOvf(true, true, null);
    }

    @Test
    public void deployOvfWithoutDatastore() throws Throwable {
        if (this.ovfUri == null) {
            return;
        }
        // Re-init the datastore Id to null;
        this.dataStoreId = null;
        ComputeService.ComputeState vm = null;
        try {
            // Create a resource pool where the VM will be housed
            this.resourcePool = createResourcePool();
            this.auth = createAuth();

            this.computeHostDescription = createComputeDescription();
            this.computeHost = createComputeHost(this.computeHostDescription);

            ComputeDescriptionService.ComputeDescription computeDesc = createTemplate();

            ImportOvfRequest req = new ImportOvfRequest();
            req.ovfUri = this.ovfUri;
            req.template = computeDesc;

            Operation op = Operation.createPatch(this.host, OvfImporterService.SELF_LINK)
                    .setBody(req)
                    .setReferer(this.host.getPublicUri());

            CompletableFuture<Operation> f = this.host.sendWithFuture(op);

            // depending on OVF location you may want to increase the timeout
            f.get(300, TimeUnit.SECONDS);

            snapshotFactoryState("ovf", ComputeDescriptionService.class);

            enumerateComputes(this.computeHost);

            String descriptionLink = findFirstOvfDescriptionLink();

            this.bootDisk = createBootDisk(CLOUD_CONFIG_DATA);
            vm = createVmState(descriptionLink);

            // set timeout for the next step, vmdk upload may take some time
            host.setTimeoutSeconds(60 * 5);

            // provision
            ProvisionComputeTaskService.ProvisionComputeTaskState outTask = createProvisionTask(vm);
            awaitTaskEnd(outTask);

            snapshotFactoryState("ovf", ComputeService.class);
        } finally {
            if (vm != null) {
                deleteVmAndWait(vm);
            }
        }
    }
}
