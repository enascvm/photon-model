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

import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEYID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEY_KEY;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.MOREF;
import static com.vmware.photon.controller.model.adapters.vsphere.VSphereEndpointAdapterService.HOST_NAME_KEY;
import static com.vmware.photon.controller.model.tasks.TestUtils.doPost;

import java.net.URI;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.StorageDescriptionService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionDiskTaskService;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * This class tests the following
 * 1. Create disk
 * 2. Attach disk when provisioning compute as Day 0 operation on Compute
 */
public class TestVsphereComputeDay0AttachDisk extends TestVSphereCloneTaskBase {
    public static final int DISK_REQUEST_TIMEOUT_MINUTES = 5;
    private EndpointService.EndpointState endpointState;
    private ComputeService.ComputeState vm = null;

    @Test
    public void testComputeAttachDisk() throws Throwable {
        DiskService.DiskState diskState =  null;
        try {

            prepareEnvironment();

            // Step 1: Create Disk
            diskState = createDiskWithDatastore("AdditionalDisk1",
                    DiskService.DiskType.HDD, ADDITIONAL_DISK_SIZE, buildCustomProperties());
            // start provision task to do the actual disk creation
            String documentSelfLink = performDiskRequest(diskState,
                    ProvisionDiskTaskService.ProvisionDiskTaskState.SubStage.CREATING_DISK);

            this.host.waitForFinishedTask(ProvisionDiskTaskService.ProvisionDiskTaskState.class,
                    documentSelfLink);

            // Step 2: Create VM
            if (isMock()) {
                createNetwork(networkId);
            }
            ComputeDescriptionService.ComputeDescription vmDescription = createVmDescription();
            this.vm = createVmState(vmDescription, true, null, true, diskState.documentSelfLink);

            // kick off a provision task to do the actual VM creation
            ProvisionComputeTaskService.ProvisionComputeTaskState provisionTask = createProvisionTask(
                    this.vm);
            awaitTaskEnd(provisionTask);

            this.vm = getComputeState(this.vm);

            // put fake moref in the vm
            if (isMock()) {
                ManagedObjectReference moref = new ManagedObjectReference();
                moref.setValue("vm-0");
                moref.setType(VimNames.TYPE_VM);
                CustomProperties.of(this.vm).put(MOREF, moref);
                this.vm = doPost(this.host, this.vm,
                        ComputeService.ComputeState.class,
                        UriUtils.buildUri(this.host, ComputeService.FACTORY_LINK));
            }
        } finally {
            if (!isMock()) {
                cleanUpVm(this.vm, null);
            }
        }
    }

    private void prepareEnvironment() throws Throwable {
        this.auth = createAuth();
        this.resourcePool = createResourcePool();

        this.computeHostDescription = createComputeDescription();
        this.computeHost = createComputeHost(this.computeHostDescription);
        this.endpointState = createEndpointState();

        enumerateComputes();
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

    private String performDiskRequest(DiskService.DiskState diskState,
            ProvisionDiskTaskService.ProvisionDiskTaskState.SubStage subStage) throws Throwable {
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

    private DiskService.DiskState createDiskWithDatastore(String alias, DiskService.DiskType type,
            long capacityMBytes, HashMap<String, String> customProperties) throws Throwable {
        DiskService.DiskState diskState = constructDiskState(alias, type, 0, null,
                capacityMBytes, customProperties);
        StorageDescriptionService.StorageDescription sd = new StorageDescriptionService.StorageDescription();
        sd.name = sd.id = this.dataStoreId != null ? this.dataStoreId : "testDatastore";
        sd = TestUtils.doPost(this.host, sd,
                StorageDescriptionService.StorageDescription.class,
                UriUtils.buildUri(this.host, StorageDescriptionService.FACTORY_LINK));
        diskState.storageDescriptionLink = sd.documentSelfLink;
        return postDiskStateWithDetails(diskState);
    }

    private DiskService.DiskState postDiskStateWithDetails(DiskService.DiskState diskState)
            throws Throwable {
        diskState.authCredentialsLink = this.auth.documentSelfLink;
        diskState.endpointLink = this.endpointState.documentSelfLink;
        AdapterUtils.addToEndpointLinks(diskState, this.endpointState.documentSelfLink);
        diskState.regionId = this.datacenterId;
        diskState.tenantLinks = this.computeHost.tenantLinks;
        diskState.diskAdapterReference = UriUtils.buildUri(host, VSphereDiskService.SELF_LINK);
        return doPost(this.host, diskState, DiskService.DiskState.class,
                UriUtils.buildUri(this.host, DiskService.FACTORY_LINK));
    }
}
