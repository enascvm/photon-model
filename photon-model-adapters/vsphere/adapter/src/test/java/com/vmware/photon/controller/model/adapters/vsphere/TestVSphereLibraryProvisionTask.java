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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.Constraint;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.BasicConnection;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.GetMoRef;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ImageService;
import com.vmware.photon.controller.model.resources.ImageService.ImageState;
import com.vmware.photon.controller.model.tasks.ImageEnumerationTaskService;
import com.vmware.photon.controller.model.tasks.ImageEnumerationTaskService.ImageEnumerationTaskState;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.vim25.InvalidPropertyFaultMsg;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.vim25.SharesLevel;
import com.vmware.vim25.VirtualDisk;
import com.vmware.vim25.VirtualDiskFlatVer2BackingInfo;
import com.vmware.vim25.VirtualDiskType;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 */
public class TestVSphereLibraryProvisionTask extends BaseVSphereAdapterTest {

    // fields that are used across method calls, stash them as private fields
    private ComputeDescription computeHostDescription;
    private ComputeState computeHost;

    public String libraryItemName = System.getProperty("vc.libItemName");
    public String placementClusterName = System.getProperty("vc.placementClusterName");

    private static final long HDD_DISK_SIZE = 9216;
    private EndpointState endpoint;

    @Test
    public void deployFromLibrary() throws Throwable {
        if (isMock()) {
            return;
        }

        // Create a resource pool where the VM will be housed
        this.resourcePool = createResourcePool();
        this.auth = createAuth();

        this.computeHostDescription = createComputeDescription();
        this.computeHost = createComputeHost(this.computeHostDescription);

        EndpointState ep = new EndpointState();
        ep.id = nextName("endpoint");
        ep.endpointType = EndpointType.vsphere.name();
        ep.name = ep.id;
        ep.authCredentialsLink = this.auth.documentSelfLink;
        ep.computeLink = this.computeHost.documentSelfLink;
        ep.computeDescriptionLink = this.computeHostDescription.documentSelfLink;
        ep.resourcePoolLink = this.resourcePool.documentSelfLink;

        this.endpoint = TestUtils.doPost(this.host, ep, EndpointState.class,
                UriUtils.buildUri(this.host, EndpointService.FACTORY_LINK));

        enumerateComputes(this.computeHost);
        doRefresh();
        snapshotFactoryState("libDeploy", ImageService.class);

        String imageLink = findImage();
        ComputeDescription desc = createVmDescription();
        ComputeState vm = createVmState(desc, imageLink);

        // kick off a provision task to do the actual VM creation
        ProvisionComputeTaskState outTask = createProvisionTask(vm);
        awaitTaskEnd(outTask);

        vm = getComputeState(vm);

        // Verify that the disk is resized
        BasicConnection connection = createConnection();
        GetMoRef get = new GetMoRef(connection);
        verifyDiskSize(vm, get, HDD_DISK_SIZE);
        verifyDiskProperties(vm, get);

        deleteVmAndWait(vm);
    }

    private void verifyDiskProperties(ComputeState vm, GetMoRef get)
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        VirtualDisk vd = fetchVirtualDisk(vm, get);
        assertEquals(SharesLevel.CUSTOM.value(), vd.getStorageIOAllocation().getShares().getLevel().value());
        Long limitIops = 50L;
        assertEquals(limitIops, vd.getStorageIOAllocation().getLimit());
        VirtualDiskFlatVer2BackingInfo backing = (VirtualDiskFlatVer2BackingInfo) vd.getBacking();
        assertTrue(backing.isThinProvisioned());
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

        computeState.networkInterfaceLinks = new ArrayList<>(1);

        computeState.diskLinks = new ArrayList<>(1);
        Constraint constraint = new Constraint();
        List<Constraint.Condition> conditions = new ArrayList<>();
        conditions.add(Constraint.Condition.forTag("INDEPENDENT", null,
                Constraint.Condition.Enforcement.SOFT, QueryTask.Query.Occurance.MUST_OCCUR));
        constraint.conditions = conditions;
        computeState.diskLinks.add(createDisk("boot", DiskService.DiskType.HDD, null,
                HDD_DISK_SIZE, buildCustomProperties(), constraint).documentSelfLink);

        CustomProperties.of(computeState)
                .put(ComputeProperties.RESOURCE_GROUP_NAME, this.vcFolder)
                .put(ComputeProperties.PLACEMENT_LINK, selectPlacement())
                .put(CustomProperties.LIBRARY_ITEM_LINK, imageLink);

        ComputeState returnState = TestUtils.doPost(this.host, computeState,
                ComputeState.class,
                UriUtils.buildUri(this.host, ComputeService.FACTORY_LINK));
        return returnState;
    }

    private HashMap<String, String> buildCustomProperties() {
        HashMap<String, String> customProperties = new HashMap<>();

        customProperties.put(PROVISION_TYPE, VirtualDiskType.THIN.value());
        customProperties.put(SHARES_LEVEL, SharesLevel.CUSTOM.value());
        customProperties.put(SHARES, "3000");
        customProperties.put(LIMIT_IOPS, "50");

        return customProperties;
    }

    private String selectPlacement() {
        Query q = Query.Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_NAME, this.placementClusterName.toLowerCase())
                .build();
        return findFirstMatching(q, ComputeState.class).documentSelfLink;
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

        Operation op = Operation
                .createPost(this.host, ServiceUriPaths.CORE_LOCAL_QUERY_TASKS)
                .setBody(task);

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
}
