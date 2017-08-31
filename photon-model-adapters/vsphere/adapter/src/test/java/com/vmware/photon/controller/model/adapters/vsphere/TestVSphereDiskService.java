/*
 * Copyright (c) 2017-2018 VMware, Inc. All Rights Reserved.
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
import static org.junit.Assert.assertTrue;

import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEYID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEY_KEY;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.DISK_FULL_PATH;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.DISK_PARENT_DIRECTORY;
import static com.vmware.photon.controller.model.adapters.vsphere.VSphereEndpointAdapterService.HOST_NAME_KEY;
import static com.vmware.photon.controller.model.tasks.TestUtils.doPost;

import java.net.URI;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.tasks.ProvisionDiskTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionDiskTaskService.ProvisionDiskTaskState.SubStage;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Test case to verify all disk related operations on vSphere adapter.
 */
public class TestVSphereDiskService extends BaseVSphereAdapterTest {
    public static final int DISK_REQUEST_TIMEOUT_MINUTES = 5;

    private ComputeDescriptionService.ComputeDescription computeHostDescription;
    private ComputeService.ComputeState computeHost;
    private EndpointService.EndpointState endpointState;

    @Test
    public void testDiskCreateAndDelete() throws Throwable {
        this.auth = createAuth();
        this.resourcePool = createResourcePool();

        this.computeHostDescription = createComputeDescription();
        this.computeHost = createComputeHost(this.computeHostDescription);
        this.endpointState = createEndpointState();

        enumerateComputes();

        DiskState diskState = createDiskWithDatastore("AdditionalDisk1",
                DiskService.DiskType.HDD, ADDITIONAL_DISK_SIZE, buildCustomProperties());

        // start provision task to do the actual disk creation
        String documentSelfLink = performDiskRequest(diskState, SubStage.CREATING_DISK);

        this.host.waitForFinishedTask(ProvisionDiskTaskService.ProvisionDiskTaskState.class,
                documentSelfLink);

        // check that the disk has been created
        ServiceDocumentQueryResult result = ProvisioningUtils.queryDiskInstances(this.host, 1);
        if (!isMock()) {
            DiskState disk = Utils.fromJson(result.documents.get(diskState.documentSelfLink),
                    DiskState.class);
            assertNotNull(disk.customProperties);
            assertNotNull(disk.customProperties.get(DISK_FULL_PATH));
            assertEquals(DiskService.DiskStatus.AVAILABLE, disk.status);
            assertNotNull(disk.customProperties.get(DISK_PARENT_DIRECTORY));
            assertTrue(disk.id.startsWith(diskState.name));
        }

        // start provision task to do the disk deletion
        documentSelfLink = performDiskRequest(diskState, SubStage.DELETING_DISK);

        this.host.waitForFinishedTask(ProvisionDiskTaskService.ProvisionDiskTaskState.class,
                documentSelfLink);
        // check that the disk has been deleted
        ProvisioningUtils.queryDiskInstances(this.host, 0);
    }

    private String performDiskRequest(DiskState diskState, SubStage subStage) throws Throwable {
        ProvisionDiskTaskService.ProvisionDiskTaskState provisionTask = new ProvisionDiskTaskService.ProvisionDiskTaskState();
        provisionTask.taskSubStage = subStage;

        provisionTask.diskLink = diskState.documentSelfLink;
        provisionTask.isMockRequest = isMock();

        provisionTask.documentExpirationTimeMicros =
                Utils.getNowMicrosUtc() + TimeUnit.MINUTES.toMicros(
                        DISK_REQUEST_TIMEOUT_MINUTES);
        provisionTask.tenantLinks = this.endpointState.tenantLinks;

        provisionTask = TestUtils.doPost(this.host,
                provisionTask, ProvisionDiskTaskService.ProvisionDiskTaskState.class,
                UriUtils.buildUri(this.host, ProvisionDiskTaskService.FACTORY_LINK));

        return provisionTask.documentSelfLink;
    }

    private EndpointService.EndpointState createEndpointState() throws Throwable {
        EndpointService.EndpointState endpoint = new EndpointService.EndpointState();
        endpoint.endpointType = PhotonModelConstants.EndpointType.vsphere.name();
        endpoint.name = PhotonModelConstants.EndpointType.vsphere.name();
        endpoint.regionId = this.datacenterId;
        endpoint.authCredentialsLink = this.auth.documentSelfLink;
        endpoint.tenantLinks = this.computeHost.tenantLinks;
        endpoint.computeLink = this.computeHost.documentSelfLink;
        endpoint.computeDescriptionLink = this.computeHostDescription.documentSelfLink;

        endpoint.endpointProperties = new HashMap<>();
        endpoint.endpointProperties.put(PRIVATE_KEYID_KEY,
                this.vcUsername != null ? this.vcUsername : "username");
        endpoint.endpointProperties.put(PRIVATE_KEY_KEY,
                this.vcPassword != null ? this.vcPassword : "password");
        endpoint.endpointProperties.put(HOST_NAME_KEY,
                this.vcUrl != null ? URI.create(this.vcUrl).toURL().getHost() : "hostname");

        return TestUtils.doPost(this.host, endpoint, EndpointService.EndpointState.class,
                UriUtils.buildUri(this.host, EndpointService.FACTORY_LINK));
    }

    private void enumerateComputes() throws Throwable {
        ResourceEnumerationTaskService.ResourceEnumerationTaskState task = new ResourceEnumerationTaskService.ResourceEnumerationTaskState();
        task.adapterManagementReference = this.computeHost.adapterManagementReference;

        task.enumerationAction = EnumerationAction.REFRESH;
        task.parentComputeLink = this.computeHost.documentSelfLink;
        task.resourcePoolLink = this.resourcePool.documentSelfLink;
        task.endpointLink = this.endpointState.documentSelfLink;

        if (isMock()) {
            if (task.options == null) {
                task.options = EnumSet.of(TaskOption.IS_MOCK);
            } else {
                task.options.add(TaskOption.IS_MOCK);
            }
        }

        ResourceEnumerationTaskService.ResourceEnumerationTaskState outTask = TestUtils
                .doPost(this.host,
                        task,
                        ResourceEnumerationTaskService.ResourceEnumerationTaskState.class,
                        UriUtils.buildUri(this.host,
                                ResourceEnumerationTaskService.FACTORY_LINK));

        this.host.waitForFinishedTask(
                ResourceEnumerationTaskService.ResourceEnumerationTaskState.class,
                outTask.documentSelfLink);
    }

    private DiskState createDiskWithDatastore(String alias, DiskService.DiskType type,
            long capacityMBytes, HashMap<String, String> customProperties) throws Throwable {
        DiskState diskState = constructDiskState(alias, type, 0, null,
                capacityMBytes, customProperties);
        diskState.authCredentialsLink = this.auth.documentSelfLink;
        diskState.endpointLink = this.endpointState.documentSelfLink;
        diskState.regionId = this.datacenterId;
        diskState.tenantLinks = this.computeHost.tenantLinks;
        diskState.diskAdapterReference = UriUtils.buildUri(host, VSphereDiskService.SELF_LINK);

        diskState.storageDescriptionLink = createStorageDescriptionState().documentSelfLink;
        return doPost(this.host, diskState, DiskState.class,
                UriUtils.buildUri(this.host, DiskService.FACTORY_LINK));
    }
}
