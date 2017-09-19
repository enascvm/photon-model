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

package com.vmware.photon.controller.model.adapters.azure.instance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.DISK_CONTROLLER_NUMBER;

import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.DEFAULT_NIC_SPEC;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.IMAGE_REFERENCE;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createImageSource;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createVMResourceFromSpec;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.deleteVMs;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.generateName;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.verifyRemovalOfResourceState;
import static com.vmware.photon.controller.model.util.StartServicesHelper.ServiceMetadata.factoryService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import com.microsoft.azure.management.compute.VirtualMachine;
import com.microsoft.azure.management.resources.fluentcore.arm.Region;
import com.microsoft.azure.management.resources.fluentcore.utils.SdkContext;

import org.junit.After;
import org.junit.Test;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapterapi.ResourceOperationResponse;
import com.vmware.photon.controller.model.adapters.azure.base.AzureBaseTest;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperation;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationRequest;
import com.vmware.photon.controller.model.adapters.util.instance.BaseComputeInstanceContext;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState;
import com.vmware.photon.controller.model.tasks.ProvisionDiskTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionDiskTaskService.ProvisionDiskTaskState;
import com.vmware.photon.controller.model.tasks.TestUtils;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.TestRequestSender;
import com.vmware.xenon.services.common.TaskFactoryService;
import com.vmware.xenon.services.common.TaskService;

/**
 * This class implement tests for the {@link AzureComputeDiskDay2Service}
 * class.
 */
public class AzureComputeDiskDay2ServiceTest extends AzureBaseTest {

    private static String azureVMName = generateName("test_");
    public static final String DISK_NAME_PREFIX = "azuredisk";
    public static final long DISK_SIZE = 20 * 1024; // 20 GBs

    private DiskService.DiskState diskState;
    private ComputeService.ComputeState vmState;
    private ComputeService.ComputeState computeVM;

    /**
     * Task service to check the status of attach operation
     */
    public static class AttachDiskTaskTestService
            extends TaskService<AttachDiskTaskTestService.AttachDiskTaskTestState> {

        public static final String FACTORY_LINK = UriPaths.PROVISIONING + "/attach-disk-tasks";

        public AttachDiskTaskTestService() {
            super(AttachDiskTaskTestState.class);
            super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        }

        public static class AttachDiskTaskTestState extends TaskService.TaskServiceState {
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
            AttachDiskTaskTestState attachDiskTaskTestState = getState(patch);

            if (TaskState.isFailed(response.taskInfo)) {
                attachDiskTaskTestState.taskSubStage = AttachDiskTaskTestState.SubStage.FAILED;
            } else if (TaskState.isFinished(response.taskInfo)) {
                attachDiskTaskTestState.taskSubStage = AttachDiskTaskTestState.SubStage.FINISHED;
            }
            patch.complete();
        }
    }

    @Override
    protected void startRequiredServices() throws Throwable {
        super.startRequiredServices();
        getHost().setTimeoutSeconds(1200);

        factoryService(AttachDiskTaskTestService.class,
                () -> TaskFactoryService.create(AttachDiskTaskTestService.class)).start(this.host);
    }

    @Test
    public void testDiskAttachRequest() throws Throwable {

        createComputeStateDesc();
        kickOffComputeVmProvisioning();

        // Save the originally provisioned VM's details before attaching disk
        this.computeVM = this.host.getServiceState(null,
                ComputeService.ComputeState.class, UriUtils.buildUri(this.host, this.vmState.documentSelfLink));

        createDiskStateDesc();
        kickOffDiskProvisioning();

        attachDiskToVM();
        assertAttachDiskToVM();

        // Create and attach another additional disk
        createDiskStateDesc();
        kickOffDiskProvisioning();

        // Save the VM's details after attaching one disk
        this.computeVM = this.host.getServiceState(null,
                ComputeService.ComputeState.class, UriUtils.buildUri(this.host, this.vmState.documentSelfLink));

        attachDiskToVM();
        assertAttachDiskToVM();
    }

    @After
    public void tearDown() throws Exception {
        List<String> resourcesToDelete = new ArrayList<>();
        ComputeService.ComputeState provisionedVM = this.host.getServiceState(null,
                ComputeService.ComputeState.class, UriUtils.buildUri(this.host, this.vmState.documentSelfLink));
        // Save all disk links for removal checks
        if (provisionedVM.diskLinks != null) {
            resourcesToDelete.addAll(provisionedVM.diskLinks);
        }

        if (this.vmState != null) {
            try {
                this.host.log(Level.INFO, "%s: Deleting VM - [%s]",
                        this.currentTestName.getMethodName(), this.vmState.name);

                int computeStatesToRemain = 1;

                deleteVMs(getHost(), this.vmState.documentSelfLink, this.isMock,
                        computeStatesToRemain);

                verifyRemovalOfResourceState(this.host, resourcesToDelete);
            } catch (Throwable deleteExc) {
                // just log and move on
                getHost().log(Level.WARNING, "%s: Deleting [%s] VM: FAILED. Details: %s",
                        this.currentTestName.getMethodName(), this.vmState.name,
                        deleteExc.getMessage());
            }
        }
    }

    private void createComputeStateDesc() throws Throwable {
        BaseComputeInstanceContext.ImageSource imageSource = createImageSource(getHost(),
                this.endpointState, IMAGE_REFERENCE);
        AzureTestUtil.VMResourceSpec vmResourceSpec = new AzureTestUtil.VMResourceSpec(getHost(),
                this.computeHost, this.endpointState, azureVMName)
                .withNicSpecs(DEFAULT_NIC_SPEC)
                .withImageSource(imageSource)
                .withManagedDisk(true);
        this.vmState = createVMResourceFromSpec(vmResourceSpec);
    }

    private void kickOffComputeVmProvisioning() throws Throwable {
        ProvisionComputeTaskState provisionComputeTaskState = new ProvisionComputeTaskState();
        provisionComputeTaskState.computeLink = this.vmState.documentSelfLink;
        provisionComputeTaskState.bootAdapterReference = this.vmState.bootAdapterReference;
        provisionComputeTaskState.instanceAdapterReference = this.vmState.instanceAdapterReference;
        provisionComputeTaskState.taskSubStage = ProvisionComputeTaskState.SubStage.CREATING_HOST;
        provisionComputeTaskState.isMockRequest = this.isMock;

        provisionComputeTaskState = TestUtils.doPost(this.host,
                provisionComputeTaskState, ProvisionComputeTaskState.class,
                UriUtils.buildUri(this.host, ProvisionComputeTaskService.FACTORY_LINK));

        this.host.waitForFinishedTask(ProvisionComputeTaskState.class,
                provisionComputeTaskState.documentSelfLink);
    }

    private void createDiskStateDesc() throws Throwable {

        DiskService.DiskState diskDesc = new DiskService.DiskState();
        diskDesc.name = SdkContext.randomResourceName(DISK_NAME_PREFIX, DISK_NAME_PREFIX.length() + 5);
        diskDesc.capacityMBytes = DISK_SIZE;
        diskDesc.regionId = Region.US_WEST.toString();
        diskDesc.endpointLink = endpointState.documentSelfLink;
        diskDesc.tenantLinks = endpointState.tenantLinks;
        diskDesc.authCredentialsLink = endpointState.authCredentialsLink;

        diskDesc.diskAdapterReference = UriUtils.buildUri(this.host, AzureDiskService.SELF_LINK);

        diskDesc.customProperties = new HashMap<>();
        // create disk in same resourceGroup of VM
        String resourceGroupName = this.vmState.customProperties.get(ComputeProperties.RESOURCE_GROUP_NAME);
        diskDesc.customProperties.put(AzureConstants.AZURE_RESOURCE_GROUP_NAME, resourceGroupName);

        this.diskState = TestUtils.doPost(this.host, diskDesc, DiskService
                .DiskState.class, UriUtils.buildUri(this.host, DiskService.FACTORY_LINK));

    }

    private void kickOffDiskProvisioning() throws Throwable {

        // start provision task to do the actual disk creation
        ProvisionDiskTaskState provisionDiskTaskState = new ProvisionDiskTaskState();
        provisionDiskTaskState.taskSubStage = ProvisionDiskTaskState.SubStage.CREATING_DISK;

        provisionDiskTaskState.diskLink = this.diskState.documentSelfLink;
        provisionDiskTaskState.isMockRequest = this.isMock;

        provisionDiskTaskState.documentExpirationTimeMicros =
                Utils.getNowMicrosUtc() + TimeUnit.MINUTES.toMicros(20);
        provisionDiskTaskState.tenantLinks = this.endpointState.tenantLinks;

        provisionDiskTaskState = TestUtils.doPost(this.host,
                provisionDiskTaskState, ProvisionDiskTaskState.class,
                UriUtils.buildUri(this.host, ProvisionDiskTaskService.FACTORY_LINK));

        this.host.waitForFinishedTask(ProvisionDiskTaskState.class,
                provisionDiskTaskState.documentSelfLink);
    }

    private void attachDiskToVM() throws Throwable {
        AttachDiskTaskTestService.AttachDiskTaskTestState attachTask = new AttachDiskTaskTestService.AttachDiskTaskTestState();
        attachTask.taskSubStage = AttachDiskTaskTestService.AttachDiskTaskTestState.SubStage.STARTED;

        attachTask = TestUtils
                .doPost(this.host, attachTask, AttachDiskTaskTestService.AttachDiskTaskTestState.class,
                        UriUtils.buildUri(this.host, AttachDiskTaskTestService.FACTORY_LINK));

        ResourceOperationRequest request = new ResourceOperationRequest();

        request.isMockRequest = this.isMock;
        request.operation = ResourceOperation.ATTACH_DISK.operation;
        request.payload = new HashMap<>();
        request.payload.put(PhotonModelConstants.DISK_LINK, this.diskState.documentSelfLink);
        request.resourceReference = UriUtils.buildUri(this.host, this.vmState.documentSelfLink);

        request.taskReference = UriUtils.buildUri(this.host, attachTask.documentSelfLink);

        Operation attachDiskOp = Operation
                .createPatch(UriUtils.buildUri(this.host, AzureComputeDiskDay2Service.SELF_LINK))
                .setBody(request)
                .setReferer(this.host.getReferer());

        TestRequestSender sender = new TestRequestSender(this.host);
        sender.sendRequest(attachDiskOp);

        this.host.log("Waiting for disk attach to complete");

        final String attachTaskServiceLink = attachTask.documentSelfLink;
        this.host.waitFor("Attach disk failed.", () -> {

            AttachDiskTaskTestService.AttachDiskTaskTestState attachDiskTaskTestState = this.host
                    .getServiceState(null, AttachDiskTaskTestService.AttachDiskTaskTestState.class,
                            UriUtils.buildUri(this.host, attachTaskServiceLink));

            // Check for the disk is attached to a vm or not.
            return attachDiskTaskTestState.taskSubStage ==
                    AttachDiskTaskTestService.AttachDiskTaskTestState.SubStage.FINISHED;
        });
    }

    private void assertAttachDiskToVM() {
        ComputeService.ComputeState provisionedVM = this.host.getServiceState(null,
                ComputeService.ComputeState.class, UriUtils.buildUri(this.host, this.vmState.documentSelfLink));
        assertEquals(this.computeVM.diskLinks.size() + 1, provisionedVM.diskLinks.size());

        DiskService.DiskState attachedDisk = this.host.getServiceState(null,
                DiskService.DiskState.class, UriUtils.buildUri(this.host, this.diskState.documentSelfLink));

        assertEquals("Disk status is not matching", DiskService.DiskStatus.ATTACHED, attachedDisk.status);

        if (!this.isMock) {
            assertNotNull(attachedDisk.customProperties.get(DISK_CONTROLLER_NUMBER));

            VirtualMachine vm = this.getAzureSdkClients().getComputeManager()
                    .virtualMachines().getById(provisionedVM.id);

            this.host.log("Number of disks attached to VM is - " + vm.dataDisks().size());
        }
    }
}
