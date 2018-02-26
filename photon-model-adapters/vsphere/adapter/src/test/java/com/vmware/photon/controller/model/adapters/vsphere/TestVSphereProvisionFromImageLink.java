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

import static com.vmware.photon.controller.model.tasks.TestUtils.doPost;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;

import org.junit.Assert;
import org.junit.Test;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.DiskService.DiskType;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ImageService;
import com.vmware.photon.controller.model.resources.ImageService.ImageState;
import com.vmware.photon.controller.model.tasks.ImageEnumerationTaskService;
import com.vmware.photon.controller.model.tasks.ImageEnumerationTaskService.ImageEnumerationTaskState;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.photon.controller.model.util.ClusterUtil.ServiceTypeCluster;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;

/**
 */
public class TestVSphereProvisionFromImageLink extends BaseVSphereAdapterTest {

    // fields that are used across method calls, stash them as private fields
    private ComputeDescription computeHostDescription;
    private ComputeState computeHost;

    public String libraryItemName = System.getProperty("vc.libItemName");

    private EndpointState endpoint;

    @Test
    public void deployFromLibrary() throws Throwable {
        ComputeState vm = null;
        try {
            vm = provisionVMAndGetState();
            if (vm == null) {
                return;
            }

            snapshotFactoryState("ready", ComputeService.class);
            snapshotFactoryState("ready", DiskService.class);
        } finally {
            if (vm != null) {
                deleteVmAndWait(vm);
            }
        }
    }

    private ComputeState createVmState(ComputeDescription vmDescription, String imageLink) throws Throwable {
        ComputeState computeState = new ComputeState();
        computeState.id = vmDescription.name;
        computeState.documentSelfLink = computeState.id;
        computeState.descriptionLink = vmDescription.documentSelfLink;
        computeState.resourcePoolLink = this.resourcePool.documentSelfLink;
        computeState.adapterManagementReference = getAdapterManagementReference();
        computeState.name = vmDescription.name;

        computeState.powerState = PowerState.ON;

        computeState.parentLink = this.computeHost.documentSelfLink;

        if (this.endpoint != null) {
            computeState.endpointLink = this.endpoint.documentSelfLink;
            computeState.endpointLinks = new HashSet<>(1);
            computeState.endpointLinks.add(this.endpoint.documentSelfLink);
        }

        computeState.networkInterfaceLinks = new ArrayList<>(1);

        computeState.diskLinks = new ArrayList<>(1);
        DiskState bootDisk = createBootDisk(imageLink);
        computeState.diskLinks.add(bootDisk.documentSelfLink);

        CustomProperties.of(computeState)
                .put(ComputeProperties.RESOURCE_GROUP_NAME, this.vcFolder)
                .put(ComputeProperties.PLACEMENT_LINK, selectPlacement());

        ComputeState returnState = TestUtils.doPost(this.host, computeState,
                ComputeState.class,
                UriUtils.buildUri(this.host, ComputeService.FACTORY_LINK));
        return returnState;
    }

    private DiskState createBootDisk(String imageLink) throws Throwable {
        DiskService.DiskState res = new DiskService.DiskState();
        res.name = nextName("disk");
        res.id = res.name;
        res.capacityMBytes = 1;
        res.bootOrder = 1;
        res.type = DiskType.HDD;
        res.imageLink = imageLink;

        return doPost(this.host, res,
                DiskService.DiskState.class,
                UriUtils.buildUri(this.host, DiskService.FACTORY_LINK));
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
        computeDesc.cpuCount = 2;
        // 1G
        computeDesc.totalMemoryBytes = 1024 * 1024 * 1024;
        computeDesc.dataStoreId = dataStoreId;

        if (this.endpoint != null) {
            computeDesc.endpointLink = this.endpoint.documentSelfLink;
            computeDesc.endpointLinks = new HashSet<>(1);
            computeDesc.endpointLinks.add(this.endpoint.documentSelfLink);
        }
        return TestUtils.doPost(this.host, computeDesc,
                ComputeDescription.class,
                UriUtils.buildUri(this.host, ComputeDescriptionService.FACTORY_LINK));
    }

    private String findImage() {
        Query q = Query.Builder.create()
                .addKindFieldClause(ImageState.class)
                .addFieldClause(ImageState.FIELD_NAME_NAME, "*" + this.libraryItemName, MatchType.WILDCARD)
                .build();

        QueryTask task = QueryTask.Builder.createDirectTask()
                .setQuery(q)
                .build();

        Operation op = QueryUtils.createQueryTaskOperation(this.host, task, ServiceTypeCluster
                .INVENTORY_SERVICE);

        Operation result = this.host.waitForResponse(op);

        try {
            return result.getBody(QueryTask.class).results.documentLinks.get(0);
        } catch (Exception e) {
            Assert.fail(e.getMessage());
            return null;
        }
    }

    private void doRefresh() throws Throwable {
        ImageEnumerationTaskState task = new ImageEnumerationTaskState();

        if (isMock()) {
            task.options = EnumSet.of(TaskOption.IS_MOCK);
        }
        task.enumerationAction = EnumerationAction.REFRESH;
        task.endpointLink = this.endpoint.documentSelfLink;

        ImageEnumerationTaskState outTask = TestUtils.doPost(this.host,
                task,
                ImageEnumerationTaskState.class,
                UriUtils.buildUri(this.host,
                        ImageEnumerationTaskService.FACTORY_LINK));

        this.host.waitForFinishedTask(ImageEnumerationTaskState.class, outTask.documentSelfLink);
    }

    private ComputeState provisionVMAndGetState() throws Throwable {
        if (isMock()) {
            return null;
        }

        // Create a resource pool where the VM will be housed
        this.resourcePool = createResourcePool();
        this.auth = createAuth();

        this.computeHostDescription = createComputeDescription();
        this.computeHost = createComputeHost(this.computeHostDescription);

        EndpointState ep = createEndpointState(this.computeHost, this.computeHostDescription);
        this.endpoint = TestUtils.doPost(this.host, ep, EndpointState.class,
              UriUtils.buildUri(this.host, EndpointService.FACTORY_LINK));

        enumerateComputes(this.computeHost, this.endpoint);
        doRefresh();
        snapshotFactoryState("images", ImageService.class);

        String imageLink = findImage();
        ComputeDescription desc = createVmDescription();
        ComputeState vm = createVmState(desc, imageLink);

        // kick off a provision task to do the actual VM creation
        ProvisionComputeTaskState outTask = createProvisionTask(vm);
        awaitTaskEnd(outTask);

        return getComputeState(vm);
    }
}
