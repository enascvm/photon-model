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
import static org.junit.Assert.assertTrue;

import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.DISK_MODE_INDEPENDENT;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.LIMIT_IOPS;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.PROVISION_TYPE;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.SHARES;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.SHARES_LEVEL;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;


import org.junit.Assert;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.GetMoRef;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.ImageService;
import com.vmware.photon.controller.model.tasks.ImageEnumerationTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.photon.controller.model.util.ClusterUtil.ServiceTypeCluster;
import com.vmware.vim25.InvalidPropertyFaultMsg;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.vim25.SharesLevel;
import com.vmware.vim25.VirtualDisk;
import com.vmware.vim25.VirtualDiskFlatVer2BackingInfo;
import com.vmware.vim25.VirtualDiskType;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;

/**
 * Base class for provisioning through library item
 */
public class TestVSphereLibraryProvisionTaskBase extends BaseVSphereAdapterTest {

    // fields that are used across method calls, stash them as private fields
    protected ComputeDescriptionService.ComputeDescription computeHostDescription;
    protected ComputeService.ComputeState computeHost;

    protected String libraryItemName = System.getProperty("vc.libItemName");

    protected static final long HDD_DISK_SIZE = 9216;
    protected EndpointService.EndpointState endpoint;

    protected void verifyDiskProperties(ComputeService.ComputeState vm, GetMoRef get)
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        VirtualDisk vd = fetchVirtualDisk(vm, get);
        assertEquals(SharesLevel.CUSTOM.value(), vd.getStorageIOAllocation().getShares().getLevel().value());
        Long limitIops = 50L;
        assertEquals(limitIops, vd.getStorageIOAllocation().getLimit());
        VirtualDiskFlatVer2BackingInfo backing = (VirtualDiskFlatVer2BackingInfo) vd.getBacking();
        assertTrue(backing.isThinProvisioned());
    }

    private ComputeService.ComputeState createVmState(ComputeDescriptionService.ComputeDescription vmDescription,
                                                      String imageLink, boolean isStoragePolicyBased,
                                                      boolean withAdditionalDisks) throws Throwable {
        ComputeService.ComputeState computeState = new ComputeService.ComputeState();
        computeState.id = vmDescription.name;
        computeState.documentSelfLink = computeState.id;
        computeState.descriptionLink = vmDescription.documentSelfLink;
        computeState.resourcePoolLink = this.resourcePool.documentSelfLink;
        computeState.adapterManagementReference = getAdapterManagementReference();
        computeState.name = vmDescription.name;

        computeState.powerState = ComputeService.PowerState.ON;

        computeState.parentLink = this.computeHost.documentSelfLink;

        computeState.networkInterfaceLinks = new ArrayList<>(1);

        computeState.diskLinks = new ArrayList<>(1);
        if (isStoragePolicyBased) {
            computeState.diskLinks.add(createDiskWithStoragePolicy("boot", DiskService.DiskType.HDD,
                    1, null, HDD_DISK_SIZE, buildDiskCustomProperties()).documentSelfLink);
        } else {
            computeState.diskLinks.add(createDiskWithDatastore("boot", DiskService.DiskType.HDD, 1, null,
                    HDD_DISK_SIZE, buildDiskCustomProperties()).documentSelfLink);
        }

        if (withAdditionalDisks) {
            computeState.diskLinks.add(createDiskWithDatastore("AdditionalDisk1", DiskService
                    .DiskType.HDD, 0, null, ADDITIONAL_DISK_SIZE, buildCustomProperties())
                    .documentSelfLink);
            computeState.diskLinks
                    .add(createDiskWithStoragePolicy("AdditionalDisk2", DiskService.DiskType.HDD, 0,
                            null, ADDITIONAL_DISK_SIZE, buildCustomProperties()).documentSelfLink);
        }

        CustomProperties.of(computeState)
                .put(ComputeProperties.RESOURCE_GROUP_NAME, this.vcFolder)
                .put(ComputeProperties.PLACEMENT_LINK, selectPlacement())
                .put(CustomProperties.LIBRARY_ITEM_LINK, imageLink);

        ComputeService.ComputeState returnState = TestUtils.doPost(this.host, computeState,
                ComputeService.ComputeState.class,
                UriUtils.buildUri(this.host, ComputeService.FACTORY_LINK));
        return returnState;
    }

    private HashMap<String, String> buildDiskCustomProperties() {
        HashMap<String, String> customProperties = new HashMap<>();

        customProperties.put(PROVISION_TYPE, VirtualDiskType.THIN.value());
        customProperties.put(SHARES_LEVEL, SharesLevel.CUSTOM.value());
        customProperties.put(SHARES, "3000");
        customProperties.put(LIMIT_IOPS, "50");
        customProperties.put(DISK_MODE_INDEPENDENT, "true");

        return customProperties;
    }

    private ComputeDescriptionService.ComputeDescription createVmDescription() throws Throwable {
        ComputeDescriptionService.ComputeDescription computeDesc = new ComputeDescriptionService.ComputeDescription();

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
        return TestUtils.doPost(this.host, computeDesc,
                ComputeDescriptionService.ComputeDescription.class,
                UriUtils.buildUri(this.host, ComputeDescriptionService.FACTORY_LINK));
    }

    private String findImage() {
        QueryTask.Query q = QueryTask.Query.Builder.create()
                .addKindFieldClause(ImageService.ImageState.class)
                .addFieldClause(ImageService.ImageState.FIELD_NAME_NAME, "*" + this.libraryItemName, QueryTask.QueryTerm.MatchType.WILDCARD)
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
        ImageEnumerationTaskService.ImageEnumerationTaskState task = new ImageEnumerationTaskService.ImageEnumerationTaskState();

        if (isMock()) {
            task.options = EnumSet.of(TaskOption.IS_MOCK);
        }
        task.enumerationAction = EnumerationAction.REFRESH;
        task.endpointLink = this.endpoint.documentSelfLink;

        ImageEnumerationTaskService.ImageEnumerationTaskState outTask = TestUtils.doPost(this.host,
                task,
                ImageEnumerationTaskService.ImageEnumerationTaskState.class,
                UriUtils.buildUri(this.host,
                        ImageEnumerationTaskService.FACTORY_LINK));

        this.host.waitForFinishedTask(ImageEnumerationTaskService.ImageEnumerationTaskState.class, outTask.documentSelfLink);
    }

    protected ComputeService.ComputeState provisionVMAndGetState() throws Throwable {
        return provisionVMAndGetState(false, false);
    }

    protected ComputeService.ComputeState provisionVMAndGetState(boolean isStoragePolicyBased,
                                                                 boolean withAdditionalDisks) throws Throwable {
        if (isMock()) {
            return null;
        }

        // Create a resource pool where the VM will be housed
        this.resourcePool = createResourcePool();
        this.auth = createAuth();

        this.computeHostDescription = createComputeDescription();
        this.computeHost = createComputeHost(this.computeHostDescription);

        EndpointService.EndpointState ep = new EndpointService.EndpointState();
        ep.id = nextName("endpoint");
        ep.endpointType = PhotonModelConstants.EndpointType.vsphere.name();
        ep.name = ep.id;
        ep.authCredentialsLink = this.auth.documentSelfLink;
        ep.computeLink = this.computeHost.documentSelfLink;
        ep.computeDescriptionLink = this.computeHostDescription.documentSelfLink;
        ep.resourcePoolLink = this.resourcePool.documentSelfLink;

        this.endpoint = TestUtils.doPost(this.host, ep, EndpointService.EndpointState.class,
                UriUtils.buildUri(this.host, EndpointService.FACTORY_LINK));

        enumerateComputes(this.computeHost);
        doRefresh();
        snapshotFactoryState("libDeploy", ImageService.class);

        String imageLink = findImage();
        ComputeDescriptionService.ComputeDescription desc = createVmDescription();
        ComputeService.ComputeState vm = createVmState(desc, imageLink, isStoragePolicyBased, withAdditionalDisks);

        //set timeout for the next step, createProvisionTask() takes time since overall provision and IP assignment sometimes take time
        host.setTimeoutSeconds(60 * 10);

        // kick off a provision task to do the actual VM creation
        ProvisionComputeTaskService.ProvisionComputeTaskState outTask = createProvisionTask(vm);
        awaitTaskEnd(outTask);

        return getComputeState(vm);
    }
}
