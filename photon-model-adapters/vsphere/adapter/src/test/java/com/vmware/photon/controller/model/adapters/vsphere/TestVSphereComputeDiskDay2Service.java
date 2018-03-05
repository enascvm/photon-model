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

import static com.vmware.photon.controller.model.ComputeProperties.COMPUTE_HOST_LINK_PROP_NAME;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEYID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEY_KEY;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.MOREF;
import static com.vmware.photon.controller.model.adapters.vsphere.VSphereEndpointAdapterService.HOST_NAME_KEY;
import static com.vmware.photon.controller.model.tasks.TestUtils.doPost;
import static com.vmware.photon.controller.model.util.StartServicesHelper.ServiceMetadata.factoryService;

import java.net.URI;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapterapi.ResourceOperationResponse;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperation;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationRequest;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.StorageDescriptionService;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionDiskTaskService;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.photon.controller.model.util.StartServicesHelper;
import com.vmware.photon.controller.model.util.StartServicesHelper.ServiceMetadata;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.TestRequestSender;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.TaskFactoryService;
import com.vmware.xenon.services.common.TaskService;

/**
 * This test does the following - Creates VM with additional disks - Create an independent disk of
 * type HDD - Now attach the created disk to the VM created. - Now attach a CDROM and Floppy disk as
 * well. - Delete the created VM should clean up all disks as well.
 */
public class TestVSphereComputeDiskDay2Service extends TestVSphereCloneTaskBase {
    private EndpointService.EndpointState endpointState;
    public static final int DISK_REQUEST_TIMEOUT_MINUTES = 5;
    private ComputeState vm = null;
    private DiskService.DiskState CDDiskState = null;

    public static class ComputeDiskOperationTaskService
            extends TaskService<ComputeDiskOperationTaskService.ComputeDiskOperationTaskState> {

        public static final String FACTORY_LINK = UriPaths.PROVISIONING + "/attach-disk-tasks";

        public ComputeDiskOperationTaskService() {
            super(ComputeDiskOperationTaskState.class);
            super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        }

        public static class ComputeDiskOperationTaskState extends TaskService.TaskServiceState {
            public enum SubStage {
                STARTED, FINISHED, FAILED
            }

            public SubStage taskSubStage;
        }

        @Override
        public void handleStart(Operation start) {
            start.complete();
        }

        @Override
        public void handlePatch(Operation patch) {
            ResourceOperationResponse response = patch.getBody(ResourceOperationResponse.class);
            ComputeDiskOperationTaskState attachDiskTaskState = getState(patch);

            if (TaskState.isFailed(response.taskInfo)) {
                attachDiskTaskState.taskSubStage = ComputeDiskOperationTaskState.SubStage.FAILED;
            } else if (TaskState.isFinished(response.taskInfo)) {
                attachDiskTaskState.taskSubStage = ComputeDiskOperationTaskState.SubStage.FINISHED;
            }
            patch.complete();
        }
    }

    @Override
    protected void doSetup() {
        ServiceMetadata[] serviceMetadata = {
                factoryService(ComputeDiskOperationTaskService.class,
                        () -> TaskFactoryService.create(ComputeDiskOperationTaskService.class))
        };

        StartServicesHelper.startServices(this.host, serviceMetadata);
    }

    @Test
    /**
     * 1. Create a VM
     * 2. Create a HDD disk
     * 3. Attach HDD disk created in Step 2 to VM.
     * 4. Attach a CD-ROM disk
     * 5. Attach a Floppy disk
     * 6. Detach HDD disk created in step 2
     * 7. Delete VM.
     * 8. Delete HDD disk
     */
    public void testDiskOperationOnCompute() throws Throwable {
        DiskService.DiskState diskState =  null;
        try {
            // Step 1: Create VM
            prepareEnvironment();
            if (isMock()) {
                createNetwork(networkId);
            }
            snapshotFactoryState("clone-refresh", NetworkService.class);
            ComputeDescriptionService.ComputeDescription vmDescription = createVmDescription();
            this.vm = createVmState(vmDescription, true, null, false);

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
                        ComputeState.class,
                        UriUtils.buildUri(this.host, ComputeService.FACTORY_LINK));
                return;
            }

            // Step 2: Create Disk
            diskState = createDiskWithDatastore("AdditionalDisk1",
                    DiskService.DiskType.HDD, ADDITIONAL_DISK_SIZE, buildCustomProperties(), false);
            // start provision task to do the actual disk creation
            String documentSelfLink = performDiskRequest(diskState,
                    ProvisionDiskTaskService.ProvisionDiskTaskState.SubStage.CREATING_DISK);

            this.host.waitForFinishedTask(ProvisionDiskTaskService.ProvisionDiskTaskState.class,
                    documentSelfLink);

            // Step 3: Attach Disk created in step 2
            ResourceOperationRequest request = createResourceOperationRequest(diskState,
                    createComputeDiskTaskService(), ResourceOperation.ATTACH_DISK);
            sendRequest(request, DiskService.DiskType.HDD, computeAttachWaitHandler());
            this.vm = this.host.getServiceState(null, ComputeState.class,
                    UriUtils.buildUri(this.host, this.vm.documentSelfLink));

            // Step 4: Attach a CD-ROM disk
            // prepare a disk with CD-ROM type
            DiskService.DiskState cdDiskState = createCDromWithIso("cdrom-1", DiskService.DiskType
                    .CDROM,
                    0, null, 1024, null, false, true);

            request = createResourceOperationRequest(cdDiskState, createComputeDiskTaskService(),
                    ResourceOperation.ATTACH_DISK);
            sendRequest(request, DiskService.DiskType.CDROM, computeAttachWaitHandler());

            this.vm = this.host.getServiceState(null, ComputeState.class,
                    UriUtils.buildUri(this.host, this.vm.documentSelfLink));

            this.CDDiskState = this.host.getServiceState(null, DiskService.DiskState.class,
                    UriUtils.buildUri(this.host, cdDiskState.documentSelfLink));

            // Step 5: Attach a floppy disk
            DiskService.DiskState floppyDiskState = createDisk("floppy-1", DiskService.DiskType.FLOPPY,
                    0, null, 1024, null);
            request = createResourceOperationRequest(floppyDiskState,
                    createComputeDiskTaskService(), ResourceOperation.ATTACH_DISK);
            sendRequest(request, DiskService.DiskType.FLOPPY, computeAttachWaitHandler());

            this.vm = this.host.getServiceState(null, ComputeState.class,
                    UriUtils.buildUri(this.host, this.vm.documentSelfLink));
            assertNotNull(this.vm.diskLinks);
            assertEquals(6, this.vm.diskLinks.size());

            // Get the latest state of the attached disk
            diskState = this.host.getServiceState(null, DiskService.DiskState.class,
                    UriUtils.buildUri(this.host, diskState.documentSelfLink));
            assertEquals(DiskService.DiskStatus.ATTACHED, diskState.status);

            // Step 6: Detach HDD disk create in Step 2 from VM
            request = createResourceOperationRequest(diskState,
                    createComputeDiskTaskService(), ResourceOperation.DETACH_DISK);
            sendRequest(request, DiskService.DiskType.HDD, computeDetachWaitHandler());
            this.vm = this.host.getServiceState(null, ComputeState.class,
                    UriUtils.buildUri(this.host, this.vm.documentSelfLink));
            assertNotNull(this.vm.diskLinks);
            assertEquals(5, this.vm.diskLinks.size());

            // Get the latest state of the detached disk
            diskState = this.host.getServiceState(null, DiskService.DiskState.class,
                    UriUtils.buildUri(this.host, diskState.documentSelfLink));
            assertEquals(DiskService.DiskStatus.AVAILABLE, diskState.status);
        } finally {
            if (!isMock()) {
                // Step 7: Delete VM
                cleanUpVm(this.vm, null);
                // Step 8: Delete disk
                if (diskState != null) {
                    String documentSelfLink = performDiskRequest(diskState,
                            ProvisionDiskTaskService.ProvisionDiskTaskState.SubStage.DELETING_DISK);

                    this.host.waitForFinishedTask(
                            ProvisionDiskTaskService.ProvisionDiskTaskState.class,
                            documentSelfLink);
                }
            }
        }
    }

    @Test
    /**
     * 1. Create a VM
     * 2. Create a HDD persistent disk
     * 3. Attach HDD disk created in Step 2 to VM.
     * 4. Delete VM.
     * 5. Verify HDD disk still remains
     * 6. Delete HDD disk
     */
    public void testComputePersistentDisk() throws Throwable {
        DiskService.DiskState diskState =  null;
        try {
            // Step 1: Create VM
            prepareEnvironment();
            if (isMock()) {
                createNetwork(networkId);
            }
            snapshotFactoryState("clone-refresh", NetworkService.class);
            ComputeDescriptionService.ComputeDescription vmDescription = createVmDescription();
            this.vm = createVmState(vmDescription, true, null, false);

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
                        ComputeState.class,
                        UriUtils.buildUri(this.host, ComputeService.FACTORY_LINK));
                return;
            }

            // Step 2: Create Disk
            diskState = createDiskWithDatastore("AdditionalDisk1",
                    DiskService.DiskType.HDD, ADDITIONAL_DISK_SIZE, buildCustomProperties(), true);
            // start provision task to do the actual disk creation
            String documentSelfLink = performDiskRequest(diskState,
                    ProvisionDiskTaskService.ProvisionDiskTaskState.SubStage.CREATING_DISK);

            this.host.waitForFinishedTask(ProvisionDiskTaskService.ProvisionDiskTaskState.class,
                    documentSelfLink);

            // Step 3: Attach Disk created in step 2
            ResourceOperationRequest request = createResourceOperationRequest(diskState,
                    createComputeDiskTaskService(), ResourceOperation.ATTACH_DISK);
            sendRequest(request, DiskService.DiskType.HDD, computeAttachWaitHandler());
            this.vm = this.host.getServiceState(null, ComputeState.class,
                    UriUtils.buildUri(this.host, this.vm.documentSelfLink));

            // Step 4: Delete VM
            cleanUpVm(this.vm, null);

            // Get the latest state of the detached disk
            diskState = this.host.getServiceState(null, DiskService.DiskState.class,
                    UriUtils.buildUri(this.host, diskState.documentSelfLink));
            assertEquals(DiskService.DiskStatus.AVAILABLE, diskState.status);
        } finally {
            if (!isMock()) {
                // Step 8: Delete disk
                if (diskState != null) {
                    String documentSelfLink = performDiskRequest(diskState,
                            ProvisionDiskTaskService.ProvisionDiskTaskState.SubStage.DELETING_DISK);

                    this.host.waitForFinishedTask(
                            ProvisionDiskTaskService.ProvisionDiskTaskState.class,
                            documentSelfLink);
                }
            }
        }
    }

    @Test
    public void testCDRomInsertOnCompute() throws Throwable {
        try {
            // Step 1: Create VM
            prepareEnvironment();
            if (isMock()) {
                createNetwork(networkId);
            }
            snapshotFactoryState("clone-refresh", NetworkService.class);
            ComputeDescriptionService.ComputeDescription vmDescription = createVmDescription();
            this.vm = createVmState(vmDescription, true, null, false);

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
                        ComputeState.class,
                        UriUtils.buildUri(this.host, ComputeService.FACTORY_LINK));
                return;
            }

            this.vm = this.host.getServiceState(null, ComputeState.class,
                    UriUtils.buildUri(this.host, this.vm.documentSelfLink));

            assertNotNull(CustomProperties.of(this.vm).getString(COMPUTE_HOST_LINK_PROP_NAME, null));
            assertEquals(3, this.vm.diskLinks.size());

            this.vm.diskLinks.stream().forEach(link -> {
                DiskService.DiskState diskState = this.host
                        .getServiceState(null, DiskService.DiskState.class,
                                UriUtils.buildUri(this.host, link));
                if (diskState.type == DiskService.DiskType.CDROM) {
                    this.CDDiskState = diskState;
                }
            });

            // Step 1: Attach a CD-ROM disk
            // prepare a disk with CD-ROM type
            DiskService.DiskState cdDiskState = createCDromWithIso("cdrom-1", DiskService.DiskType.CDROM,
                    0, null, 1024, null, true, false);

            ResourceOperationRequest request = createResourceOperationRequest(cdDiskState,
                    createComputeDiskTaskService(),
                    ResourceOperation.ATTACH_DISK);
            sendRequest(request, DiskService.DiskType.CDROM, insertCDRomHandler());

            this.vm = this.host.getServiceState(null, ComputeState.class,
                    UriUtils.buildUri(this.host, this.vm.documentSelfLink));

            assertNotNull(this.vm.diskLinks);
            assertEquals(3, this.vm.diskLinks.size());
        } finally {
            if (!isMock()) {
                cleanUpVm(this.vm, null);
            }
        }
    }

    private VerificationHost.WaitHandler computeDetachWaitHandler() {
        return () -> {
            ComputeState vm = this.host.getServiceState(null, ComputeState.class,
                    UriUtils.buildUri(this.host, this.vm.documentSelfLink));

            // Check for the disk is attached to a vm or not.
            if (this.vm.diskLinks.size() - 1 == vm.diskLinks.size()) {
                return true;
            } else {
                return false;
            }
        };
    }

    private VerificationHost.WaitHandler computeAttachWaitHandler() {
        return () -> {
            ComputeState vm = this.host.getServiceState(null, ComputeState.class,
                    UriUtils.buildUri(this.host, this.vm.documentSelfLink));

            // Check for the disk is attached to a vm or not.
            if (this.vm.diskLinks.size() + 1 == vm.diskLinks.size()) {
                return true;
            } else {
                return false;
            }
        };
    }

    private VerificationHost.WaitHandler insertCDRomHandler() {
        return () -> {
            DiskService.DiskState diskState = this.host
                    .getServiceState(null, DiskService.DiskState.class,
                            UriUtils.buildUri(this.host, this.CDDiskState.documentSelfLink));

            if (diskState.sourceImageReference != null) {
                return true;
            } else {
                return false;
            }
        };
    }

    private String createComputeDiskTaskService() throws Throwable {
        ComputeDiskOperationTaskService.ComputeDiskOperationTaskState diskTask =
                new ComputeDiskOperationTaskService.ComputeDiskOperationTaskState();
        diskTask.taskSubStage = ComputeDiskOperationTaskService.ComputeDiskOperationTaskState.SubStage.STARTED;

        diskTask = TestUtils
                .doPost(this.host, diskTask, ComputeDiskOperationTaskService.ComputeDiskOperationTaskState.class,
                        UriUtils.buildUri(this.host, ComputeDiskOperationTaskService.FACTORY_LINK));
        return diskTask.documentSelfLink;
    }

    private void sendRequest(ResourceOperationRequest request, DiskService.DiskType diskType,
            VerificationHost.WaitHandler handler) {
        Operation attachDiskOp = Operation
                .createPatch(UriUtils.buildUri(this.host, VSphereComputeDiskManagementService.SELF_LINK))
                .setBody(request)
                .setReferer(this.host.getReferer());

        TestRequestSender sender = new TestRequestSender(this.host);
        sender.sendRequest(attachDiskOp);

        this.host.log(String.format("Waiting for %s disk attach to complete", diskType.name()));

        this.host.waitFor(String.format("%s %s disk failed.", request.operation, diskType.name()),
                handler);
    }

    private ResourceOperationRequest createResourceOperationRequest(DiskService.DiskState
            diskState, String taskLink, ResourceOperation resourceOperation) {
        ResourceOperationRequest request = new ResourceOperationRequest();

        request.isMockRequest = isMock();
        request.operation = resourceOperation.operation;
        request.payload = new HashMap<>();
        request.payload.put("diskLink", diskState.documentSelfLink);
        request.resourceReference = UriUtils.buildUri(this.host, this.vm.documentSelfLink);
        request.taskReference = UriUtils.buildUri(this.host, taskLink);

        return request;
    }

    private void prepareEnvironment() throws Throwable {
        this.auth = createAuth();
        this.resourcePool = createResourcePool();

        this.computeHostDescription = createComputeDescription();
        this.computeHost = createComputeHost(this.computeHostDescription);
        this.endpointState = createEndpointState();

        enumerateComputes();
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

    private DiskService.DiskState createDiskWithDatastore(String alias, DiskService.DiskType type,
            long capacityMBytes, HashMap<String, String> customProperties, boolean isPersistent)
            throws Throwable {
        DiskService.DiskState diskState = constructDiskState(alias, type, 0, null,
                capacityMBytes, customProperties);
        diskState.persistent = isPersistent;
        StorageDescription sd = new StorageDescription();
        sd.name = sd.id = this.dataStoreId != null ? this.dataStoreId : "testDatastore";
        sd = TestUtils.doPost(this.host, sd,
                StorageDescription.class,
                UriUtils.buildUri(this.host, StorageDescriptionService.FACTORY_LINK));
        diskState.storageDescriptionLink = sd.documentSelfLink;
        return postDiskStateWithDetails(diskState);
    }

    private DiskService.DiskState postDiskStateWithDetails(DiskService.DiskState diskState)
            throws Throwable {
        diskState.authCredentialsLink = this.auth.documentSelfLink;
        diskState.endpointLink = this.endpointState.documentSelfLink;
        diskState.regionId = this.datacenterId;
        diskState.tenantLinks = this.computeHost.tenantLinks;
        diskState.diskAdapterReference = UriUtils.buildUri(host, VSphereDiskService.SELF_LINK);
        return doPost(this.host, diskState, DiskService.DiskState.class,
                UriUtils.buildUri(this.host, DiskService.FACTORY_LINK));
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
}
