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
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.junit.Test;

import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.StorageDescriptionService;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 *
 */
public class TestVSphereEnumerationTask extends BaseVSphereAdapterTest {

    private ComputeDescription computeHostDescription;

    private ComputeState computeHost;

    @Test
    public void testRefresh() throws Throwable {
        // Create a resource pool where the VM will be housed
        this.resourcePool = createResourcePool();

        this.auth = createAuth();

        this.computeHostDescription = createComputeDescription();
        this.computeHost = createComputeHost(this.computeHostDescription);

        doRefresh();

        if (!isMock()) {
            ComputeState vm = findRandomVm();
            assertInternalPropertiesSet(vm);
            assertNotNull(vm.endpointLink);
            assertNotNull(vm.tenantLinks);
        }

        captureFactoryState("initial");

        String aComputeLink = null;
        if (!isMock()) {
            // clone a random compute and save it under different id
            ComputeState vm = findRandomVm();
            vm.documentSelfLink = null;
            vm.id = "fake-vm-" + vm.id;

            vm = TestUtils.doPost(this.host, vm,
                    ComputeState.class,
                    UriUtils.buildUri(this.host, ComputeService.FACTORY_LINK));
            aComputeLink = vm.documentSelfLink;
        }
        // do a second refresh to test update path
        doRefresh();

        captureFactoryState("updated");

        if (aComputeLink != null) {
            // the second enumeration must have deleted the fake vm
            Operation op = Operation.createGet(this.host, aComputeLink);
            op = this.host.waitForResponse(op);
            assertEquals(Operation.STATUS_CODE_NOT_FOUND, op.getStatusCode());
        }
    }

    protected void captureFactoryState(String marker)
            throws ExecutionException, InterruptedException, IOException {
        snapshotFactoryState(marker, ComputeService.class);
        snapshotFactoryState(marker, ComputeDescriptionService.class);
        snapshotFactoryState(marker, ResourcePoolService.class);
        snapshotFactoryState(marker, StorageDescriptionService.class);
        snapshotFactoryState(marker, NetworkService.class);
        snapshotFactoryState(marker, TagService.class);
        snapshotFactoryState(marker, NetworkInterfaceService.class);
    }

    private ComputeState findRandomVm()
            throws InterruptedException, TimeoutException, ExecutionException {
        Query q = Query.Builder.create()
                .addCompositeFieldClause(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES, CustomProperties.TYPE, VimNames.TYPE_VM)
                .addKindFieldClause(ComputeState.class)
                .build();

        QueryTask qt = QueryTask.Builder.createDirectTask()
                .setQuery(q)
                .addOption(QueryOption.EXPAND_CONTENT)
                .build();

        Operation op = Operation
                .createPost(UriUtils.buildUri(this.host, ServiceUriPaths.CORE_LOCAL_QUERY_TASKS))
                .setBody(qt);

        QueryTask result = this.host.waitForResponse(op).getBody(QueryTask.class);

        Object firstResult = result.results.documents.values().iterator().next();
        return Utils.fromJson(firstResult, ComputeState.class);
    }

    private void doRefresh() throws Throwable {
        enumerateComputes(this.computeHost);
    }
}
