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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.DISK_MODE_INDEPENDENT;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.LIMIT_IOPS;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.PROVISION_TYPE;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.SHARES;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.SHARES_LEVEL;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;

import org.junit.Assert;
import org.junit.Test;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.ImageService;
import com.vmware.photon.controller.model.resources.SnapshotService;
import com.vmware.photon.controller.model.tasks.ImageEnumerationTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.vim25.SharesLevel;
import com.vmware.vim25.VirtualDiskType;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

public class TestVSphereLinkedCloneProvisionTask extends TestVSphereLibraryProvisionTaskBase {

    @Test
    public void testLinkedCloneProvision() throws Throwable {
        // create a snapshot first and then use that for linkedclone
        // provisioning.
        ComputeService.ComputeState vm = this.provisionVMAndGetState();
        ComputeService.ComputeState vmCloneState = null;
        try {
            if (vm == null) {
                return;
            }

            createSnapshotAndWait(vm, false);
            SnapshotService.SnapshotState snapshot = getSnapshots(vm);

            if (snapshot == null) {
                fail("Snapshot creation failed.");
            }

            String snapshotLink = snapshot.documentSelfLink; // __snapshotLink

            ComputeDescriptionService.ComputeDescription desc = createVmDescriptionForClone();
            ComputeService.ComputeState vmClone = createVmStateFromSnapshot(desc, snapshotLink);

            // kick off a provision task to do the actual VM creation
            ProvisionComputeTaskService.ProvisionComputeTaskState outTask = createProvisionTask(vmClone);
            awaitTaskEnd(outTask);

            vmCloneState = getComputeState(vmClone);
            assertNotNull(vmCloneState);
        } finally {
            if (vmCloneState != null) {
                deleteVmAndWait(vmCloneState);
            }
            if (vm != null) {
                deleteVmAndWait(vm);
            }
        }
    }

    private ComputeDescriptionService.ComputeDescription createVmDescription() throws Throwable {
        ComputeDescriptionService.ComputeDescription computeDesc = new ComputeDescriptionService.ComputeDescription();

        computeDesc.id = nextName("vm");
        computeDesc.regionId = this.datacenterId;
        computeDesc.documentSelfLink = computeDesc.id;
        computeDesc.supportedChildren = new ArrayList<>();
        computeDesc.instanceAdapterReference = UriUtils.buildUri(this.host, VSphereUriPaths.INSTANCE_SERVICE);
        computeDesc.authCredentialsLink = this.auth.documentSelfLink;
        computeDesc.name = computeDesc.id;
        computeDesc.dataStoreId = this.dataStoreId;
        computeDesc.cpuCount = 2;
        // 1G
        computeDesc.totalMemoryBytes = 1024 * 1024 * 1024;
        computeDesc.dataStoreId = dataStoreId;
        return TestUtils.doPost(this.host, computeDesc, ComputeDescriptionService.ComputeDescription.class,
                UriUtils.buildUri(this.host, ComputeDescriptionService.FACTORY_LINK));
    }

    private ComputeDescriptionService.ComputeDescription createVmDescriptionForClone() throws Throwable {
        ComputeDescriptionService.ComputeDescription computeDesc = new ComputeDescriptionService.ComputeDescription();

        computeDesc.id = nextName("vm");
        computeDesc.documentSelfLink = computeDesc.id;
        computeDesc.supportedChildren = new ArrayList<>();
        computeDesc.instanceAdapterReference = UriUtils.buildUri(this.host, VSphereUriPaths.INSTANCE_SERVICE);
        computeDesc.enumerationAdapterReference = AdapterUriUtil.buildAdapterUri(this.host,
                VSphereUriPaths.ENUMERATION_SERVICE);
        computeDesc.statsAdapterReference = AdapterUriUtil.buildAdapterUri(this.host, VSphereUriPaths.STATS_SERVICE);
        computeDesc.powerAdapterReference = AdapterUriUtil.buildAdapterUri(this.host, VSphereUriPaths.POWER_SERVICE);

        computeDesc.authCredentialsLink = this.auth.documentSelfLink;
        computeDesc.name = computeDesc.id;
        return TestUtils.doPost(this.host, computeDesc, ComputeDescriptionService.ComputeDescription.class,
                UriUtils.buildUri(this.host, ComputeDescriptionService.FACTORY_LINK));
    }

    private ComputeService.ComputeState createVmStateFromSnapshot(
            ComputeDescriptionService.ComputeDescription vmDescription, String snapshotMoref) throws Throwable {
        ComputeService.ComputeState computeState = new ComputeService.ComputeState();
        computeState.id = vmDescription.name;
        computeState.documentSelfLink = computeState.id;
        computeState.descriptionLink = vmDescription.documentSelfLink;
        computeState.resourcePoolLink = this.resourcePool.documentSelfLink;
        computeState.adapterManagementReference = getAdapterManagementReference();
        computeState.name = vmDescription.name;

        computeState.powerState = ComputeService.PowerState.OFF;

        computeState.parentLink = this.computeHost.documentSelfLink;

        CustomProperties.of(computeState).put(ComputeProperties.RESOURCE_GROUP_NAME, this.vcFolder)
                .put(ComputeProperties.PLACEMENT_LINK, selectPlacement())
                .put(CustomProperties.SNAPSHOT_LINK, snapshotMoref);

        ComputeService.ComputeState returnState = TestUtils.doPost(this.host, computeState,
                ComputeService.ComputeState.class, UriUtils.buildUri(this.host, ComputeService.FACTORY_LINK));
        return returnState;
    }

    private Operation sendOperationSynchronously(Operation op) throws Throwable {
        final Operation[] returnedOp = { null };
        TestContext ctx = this.host.testCreate(1);
        op.setCompletion((operation, throwable) -> {
            returnedOp[0] = operation;
            if (throwable != null) {
                ctx.failIteration(throwable);
            } else {
                ctx.completeIteration();
            }
        });

        this.host.send(op);
        this.host.testWait(ctx);
        return returnedOp[0];
    }

    protected ComputeService.ComputeState provisionVMAndGetState() throws Throwable {
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
        ComputeService.ComputeState vm = createVmState(desc, imageLink);

        // kick off a provision task to do the actual VM creation
        ProvisionComputeTaskService.ProvisionComputeTaskState outTask = createProvisionTask(vm);
        awaitTaskEnd(outTask);

        return getComputeState(vm);
    }

    private void doRefresh() throws Throwable {
        ImageEnumerationTaskService.ImageEnumerationTaskState task = new ImageEnumerationTaskService.ImageEnumerationTaskState();

        if (isMock()) {
            task.options = EnumSet.of(TaskOption.IS_MOCK);
        }
        task.enumerationAction = EnumerationAction.REFRESH;
        task.endpointLink = this.endpoint.documentSelfLink;

        ImageEnumerationTaskService.ImageEnumerationTaskState outTask = TestUtils.doPost(this.host, task,
                ImageEnumerationTaskService.ImageEnumerationTaskState.class,
                UriUtils.buildUri(this.host, ImageEnumerationTaskService.FACTORY_LINK));

        this.host.waitForFinishedTask(ImageEnumerationTaskService.ImageEnumerationTaskState.class,
                outTask.documentSelfLink);
    }

    private String findImage() {
        QueryTask.Query q = QueryTask.Query.Builder.create().addKindFieldClause(ImageService.ImageState.class)
                .addFieldClause(ImageService.ImageState.FIELD_NAME_NAME, "*" + this.libraryItemName,
                        QueryTask.QueryTerm.MatchType.WILDCARD)
                .build();

        QueryTask task = QueryTask.Builder.createDirectTask().setQuery(q).build();

        Operation op = Operation.createPost(this.host, ServiceUriPaths.CORE_LOCAL_QUERY_TASKS).setBody(task);

        Operation result = this.host.waitForResponse(op);

        try {
            return result.getBody(QueryTask.class).results.documentLinks.get(0);
        } catch (Exception e) {
            Assert.fail(e.getMessage());
            return null;
        }
    }

    private ComputeService.ComputeState createVmState(ComputeDescriptionService.ComputeDescription vmDescription,
            String imageLink) throws Throwable {
        ComputeService.ComputeState computeState = new ComputeService.ComputeState();
        computeState.id = vmDescription.name;
        computeState.documentSelfLink = computeState.id;
        computeState.descriptionLink = vmDescription.documentSelfLink;
        computeState.resourcePoolLink = this.resourcePool.documentSelfLink;
        computeState.adapterManagementReference = getAdapterManagementReference();
        computeState.name = vmDescription.name;

        computeState.powerState = ComputeService.PowerState.OFF;

        computeState.parentLink = this.computeHost.documentSelfLink;

        computeState.diskLinks = new ArrayList<>(1);
        computeState.diskLinks.add(createDisk("boot", DiskService.DiskType.HDD, 1, null, HDD_DISK_SIZE,
                buildDiskCustomProperties()).documentSelfLink);

        computeState.networkInterfaceLinks = new ArrayList<>(1);

        CustomProperties.of(computeState).put(ComputeProperties.RESOURCE_GROUP_NAME, this.vcFolder)
                .put(ComputeProperties.PLACEMENT_LINK, selectPlacement())
                .put(CustomProperties.LIBRARY_ITEM_LINK, imageLink);

        ComputeService.ComputeState returnState = TestUtils.doPost(this.host, computeState,
                ComputeService.ComputeState.class, UriUtils.buildUri(this.host, ComputeService.FACTORY_LINK));
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
}
