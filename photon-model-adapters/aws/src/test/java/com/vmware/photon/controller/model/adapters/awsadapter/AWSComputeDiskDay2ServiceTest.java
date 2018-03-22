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

package com.vmware.photon.controller.model.adapters.awsadapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static com.vmware.photon.controller.model.ComputeProperties.PLACEMENT_LINK;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWSStorageType;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWSSupportedOS;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWSSupportedVirtualizationTypes;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.DEVICE_NAME;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.DEVICE_TYPE;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.AWS_VM_REQUEST_TIMEOUT_MINUTES;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.avalabilityZoneIdentifier;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSAuthentication;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSComputeHost;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSResourcePool;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSVMResource;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.deleteSecurityGroupUsingEC2Client;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.getAwsDisksByIds;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.getAwsInstancesByIds;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.getResourceState;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.getSecurityGroupsIdUsingEC2Client;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.regionId;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.setAwsClientMockInfo;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.setUpTestVpc;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.tearDownTestVpc;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.verifyRemovalOfResourceState;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestProvisionAWSDisk.createAWSDiskState;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestUtils.getExecutor;
import static com.vmware.photon.controller.model.util.StartServicesHelper.ServiceMetadata.factoryService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.GroupIdentifier;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.Volume;
import com.google.gson.Gson;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.vmware.photon.controller.model.PhotonModelMetricServices;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapterapi.ResourceOperationResponse;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSBlockDeviceNameMapper;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperation;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationRequest;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.SecurityGroupService;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionDiskTaskService;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.photon.controller.model.util.StartServicesHelper;
import com.vmware.photon.controller.model.util.StartServicesHelper.ServiceMetadata;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.common.test.TestRequestSender;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.TaskFactoryService;
import com.vmware.xenon.services.common.TaskService;

public class AWSComputeDiskDay2ServiceTest {

    public static final int AWS_DISK_REQUEST_TIMEOUT_MINUTES = 5;

    private VerificationHost host;

    public boolean isMock = true;
    public boolean isAwsClientMock = false;
    public String awsMockEndpointReference = null;

    public String accessKey = "accessKey";
    public String secretKey = "secretKey";
    //Flag to indicate if networking resources created from the test should be deleted.
    public boolean deleteResourcesFlag = false;

    private AmazonEC2AsyncClient client;
    private Map<String, Object> awsTestContext;

    private ComputeState vmState;

    private List<String> disksToCleanUp = new ArrayList<>();

    //The disks added to this list are deleted with AWSDiskService.Delete
    private List<DiskState> diskStates = new ArrayList<>();

    //This volume is deleted directly with amazonEc2Client.deleteVolume().
    private String volumeId;

    private ComputeState computeHost;
    private EndpointState endpointState;

    private String sgToCleanUp = null;

    private TestAWSSetupUtils.AwsNicSpecs singleNicSpec;

    private String zoneId = TestAWSSetupUtils.regionId + avalabilityZoneIdentifier;
    private String diskZoneId = TestAWSSetupUtils.regionId + "b";

    public static class DiskTaskService
            extends TaskService<DiskTaskService.DiskTaskState> {

        public static final String FACTORY_LINK = UriPaths.PROVISIONING + "/disk-op-tasks";

        public static FactoryService createFactory() {
            return TaskFactoryService.create(DiskTaskService.class);
        }

        public DiskTaskService() {
            super(DiskTaskState.class);
            super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        }

        public static class DiskTaskState extends TaskService.TaskServiceState {
            public static enum SubStage {
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
            DiskTaskState diskTaskState = getState(patch);

            if (TaskState.isFailed(response.taskInfo)) {
                diskTaskState.taskSubStage = DiskTaskState.SubStage.FAILED;
            } else if (TaskState.isFinished(response.taskInfo)) {
                diskTaskState.taskSubStage = DiskTaskState.SubStage.FINISHED;
            }
            patch.complete();
        }
    }

    @Rule
    public TestName currentTestName = new TestName();

    @Before
    public void setUp() throws Exception {
        CommandLineArgumentParser.parseFromProperties(this);

        setAwsClientMockInfo(this.isAwsClientMock, this.awsMockEndpointReference);
        AuthCredentialsService.AuthCredentialsServiceState creds = new AuthCredentialsService.AuthCredentialsServiceState();
        creds.privateKey = this.secretKey;
        creds.privateKeyId = this.accessKey;

        TestContext ec2WaitContext = new TestContext(1,  Duration.ofSeconds(30L));
        AWSUtils.getEc2AsyncClient(creds, TestAWSSetupUtils.regionId, getExecutor())
                .exceptionally(t -> {
                    ec2WaitContext.fail(t);
                    throw new CompletionException(t);
                })
                .thenAccept(ec2Client -> {
                    this.client = ec2Client;
                    ec2WaitContext.complete();
                });
        ec2WaitContext.await();

        this.awsTestContext = new HashMap<>();
        setUpTestVpc(this.client, this.awsTestContext, this.isMock, this.zoneId);
        this.singleNicSpec = (TestAWSSetupUtils.AwsNicSpecs) this.awsTestContext
                .get(TestAWSSetupUtils.NIC_SPECS_KEY);

        this.host = VerificationHost.create(0);
        try {
            this.host.setMaintenanceIntervalMicros(TimeUnit.MILLISECONDS.toMicros(250));
            this.host.start();
            PhotonModelServices.startServices(this.host);
            PhotonModelAdaptersRegistryAdapters.startServices(this.host);
            PhotonModelMetricServices.startServices(this.host);
            PhotonModelTaskServices.startServices(this.host);

            ServiceMetadata[] serviceMetadata = {
                factoryService(DiskTaskService.class, DiskTaskService::createFactory)
            };

            StartServicesHelper.startServices(this.host, serviceMetadata);

            AWSAdaptersTestUtils.startServicesSynchronously(this.host);

            this.host.setTimeoutSeconds(1200);

            this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelMetricServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
            initResourcePoolAndComputeHost(this.zoneId);
        } catch (Throwable e) {
            throw new Exception(e);
        }
    }

    @After
    public void tearDown() throws Throwable {
        if (this.host == null) {
            return;
        }
        try {
            deleteProvisionedResources();
        } catch (Throwable deleteEx) {
            // just log and move on
            this.host.log(Level.WARNING, "Exception deleting VM - %s", deleteEx.getMessage());
        }
        this.host.tearDownInProcessPeers();
        this.host.toggleNegativeTestMode(false);
        this.host.tearDown();

        setAwsClientMockInfo(false, null);
        if (this.deleteResourcesFlag) {
            this.awsTestContext.put(TestAWSSetupUtils.DELETE_RESOURCES_KEY,
                    TestAWSSetupUtils.DELETE_RESOURCES_KEY);
        }
        tearDownTestVpc(this.client, this.host, this.awsTestContext, this.isMock);
    }

    private void deleteProvisionedResources() throws Throwable {

        // add deleted disk links
        List<String> deletedResources = new ArrayList<>();

        //add deleted network interface links
        if (this.vmState.networkInterfaceLinks != null) {
            deletedResources.addAll(this.vmState.networkInterfaceLinks);
        }

        TestAWSSetupUtils.deleteDisks(this.disksToCleanUp, this.isMock, this.host,
                this.endpointState.tenantLinks);

        //add the disk link of the detached disk which is not yet deleted.
        deletedResources.addAll(this.disksToCleanUp);

        deleteSecurityGroupUsingEC2Client(this.client, this.host, this.sgToCleanUp);
        verifyRemovalOfResourceState(this.host, deletedResources);

        this.vmState = null;
        this.sgToCleanUp = null;
    }

    /**
     * This test performs the following steps in sequence.
     * 1. provision a VM with one bootdisk in us-east-1a.
     * 2. Create an independent disk in us-east-1b and then attach it to the VM in us-east-1a.
     * 3. Attach operation should fail when run in real mode because aws throws an error. In mock mode
     *  attach should be successful because there is no validation in DiskDay2 service that checks the
     *  zone mismatch.
     */
    @Test
    public void tesDiskOperationsNegativeScenarios() throws Throwable {

        //provisioning a vm
        provisionVM(this.zoneId);

        DiskState diskspec = createAWSDiskState(this.host, this.endpointState,
                this.currentTestName.getMethodName() + "_disk1",
                Boolean.TRUE, this.diskZoneId, regionId);

        //create a disk
        provisionSingleDisk(diskspec);

        DiskTaskService.DiskTaskState.SubStage expectedTerminalState =
                DiskTaskService.DiskTaskState.SubStage.FINISHED;

        if (!this.isMock) {
            //attaching a disk in us-east-1b to a vm in us-east-1a should fail.
            expectedTerminalState = DiskTaskService.DiskTaskState.SubStage.FAILED;
        }

        performDiskOperationAndVerify(this.vmState.documentSelfLink,
                Arrays.asList(diskspec.documentSelfLink),
                ResourceOperation.ATTACH_DISK.operation,
                expectedTerminalState);

        this.disksToCleanUp.add(diskspec.documentSelfLink);

        //VM has only boot disk.
        deleteVMAndVerifyDisks(this.vmState.documentSelfLink, this.vmState.diskLinks);
    }

    /**
     * This test performs the following steps in sequence.
     * 1. provision a VM with one bootdisk, two new inline disks and one existing disk.
     * 2. Create three independent disks and then explicitly attach all of them to the VM
     * 3. Detach the first two disks that are explicitly attached.
     * 4. Delete VM(all the attached disks which are marked are not marked to persist will also be deleted).
     */
    @Test
    public void testDiskOperations() throws Throwable {
        Gson gson = new Gson();

        DiskState diskspec1 = createAWSDiskState(this.host, this.endpointState,
                this.currentTestName.getMethodName() + "_disk1",
                Boolean.FALSE, this.zoneId, regionId);

        //create a disk
        provisionSingleDisk(diskspec1);

        assertEquals(1, diskspec1.endpointLinks.size());

        //attach a disk while provisioning the vm
        provisionVMAndAttachDisk(this.zoneId, diskspec1.documentSelfLink, true);

        // check that the VM has been created
        ServiceDocumentQueryResult computeQueryResult = ProvisioningUtils
                .queryComputeInstances(this.host, 2);

        ComputeState vmStateAfterAttach1 = gson.fromJson(
                computeQueryResult.documents.get(this.vmState.documentSelfLink).toString(),
                ComputeState.class);
        Instance instance = null;
        if (!this.isMock) {

            List<Instance> instances = getAwsInstancesByIds(this.client, this.host,
                    Collections.singletonList(vmStateAfterAttach1.id));
            instance = instances.get(0);

            ComputeState vm = this.host.getServiceState(null, ComputeState.class,
                    UriUtils.buildUri(this.host, this.vmState.documentSelfLink));

            assertAndSetVMSecurityGroupsToBeDeleted(instance, vm);

            //verify that the disk is attached while provisioning the vm.
            DiskState attachedDisk1 = this.host.getServiceState(null, DiskState.class,
                    UriUtils.buildUri(this.host, diskspec1.documentSelfLink));

            assertEquals("disk status not matching", DiskService.DiskStatus.ATTACHED,
                    attachedDisk1.status);
            assertEquals(1, attachedDisk1.endpointLinks.size());

            this.volumeId = attachedDisk1.id;
        }

        ServiceDocumentQueryResult initialDiskQueryResult = ProvisioningUtils
                .queryDiskInstances(this.host, vmStateAfterAttach1.diskLinks.size());

        List<String> existingNames = new ArrayList<>();
        for (String diskLink : initialDiskQueryResult.documentLinks) {
            DiskState diskState = Utils.fromJson(initialDiskQueryResult.documents.get(diskLink),
                    DiskState.class);
            existingNames.add(diskState.customProperties.get(AWSConstants.DEVICE_NAME));
        }

        ComputeState vmStateBeforeAttach = vmStateAfterAttach1;

        int numExternalDisks = 3;
        List<String> externallyProvisionedDisks = new ArrayList<>();
        ComputeState vmAfterExternalDiskAttach = createAndAttachExternalDisks(instance, existingNames,
                vmStateBeforeAttach, externallyProvisionedDisks, numExternalDisks);

        List<String> diskLinksToDetach = externallyProvisionedDisks.stream()
                .filter(diskLink -> !diskLink
                        .equals(externallyProvisionedDisks.get(numExternalDisks - 1)))
                .collect(Collectors.toList());

        //detach disks from the vm and verify the details of the detached disks
        ComputeState vmStateAfterExplicitDetach = detachDiskAndVerify(vmAfterExternalDiskAttach,
                diskLinksToDetach, this.disksToCleanUp);

        assertEquals(5, vmStateAfterExplicitDetach.diskLinks.size());

        //On VM delete, two inline(Test_Volume_1 and Test_Volume_2), one non-inline external(*_disk1),
        // one external-disk should be deleted. Only one of the attached disks should be persisted.
        deleteVMAndVerifyDisks(vmStateAfterExplicitDetach.documentSelfLink,
                vmStateAfterExplicitDetach.diskLinks);
    }

    private ComputeState createAndAttachExternalDisks(Instance instance, List<String> existingNames,
            ComputeState vmStateBeforeAttach, List<String> externallyProvisionedDisks, int numDisks)
            throws Throwable {

        for (int i = 2, j = 1; i < numDisks + 2; i++, j++) {

            //create disk
            DiskState diskSpec = createAWSDiskState(this.host, this.endpointState,
                    this.currentTestName.getMethodName() + "_disk" + i,
                    !(j == numDisks), this.zoneId, regionId);

            provisionSingleDisk(diskSpec);

            assertEquals(1, diskSpec.endpointLinks.size());

            ServiceDocumentQueryResult diskQueryResult = ProvisioningUtils
                    .queryDiskInstances(this.host, vmStateBeforeAttach.diskLinks.size() + 1);

            DiskState provisionedDisk = new Gson().fromJson(
                    diskQueryResult.documents.get(diskSpec.documentSelfLink).toString(),
                    DiskState.class);

            //assert that the disk is available
            assertEquals("disk status not matching", DiskService.DiskStatus.AVAILABLE,
                    provisionedDisk.status);

            assertNotNull("Disk creation time cannot be empty", provisionedDisk.creationTimeMicros);

            //collect external disks into a list
            externallyProvisionedDisks.add(provisionedDisk.documentSelfLink);

            //attach disk to the vm.
            performDiskOperationAndVerify(vmStateBeforeAttach.documentSelfLink,
                    Arrays.asList(externallyProvisionedDisks.get(i - 2)),
                    ResourceOperation.ATTACH_DISK.operation,
                    DiskTaskService.DiskTaskState.SubStage.FINISHED);

            ComputeState vmStateAfterAttach = this.host.getServiceState(null, ComputeState.class,
                    UriUtils.buildUri(this.host, this.vmState.documentSelfLink));

            //assert the number of disklinks have increased by 1.
            assertEquals(vmStateBeforeAttach.diskLinks.size() + 1,
                    vmStateAfterAttach.diskLinks.size());

            DiskState attachedDisk = this.host.getServiceState(null, DiskState.class,
                    UriUtils.buildUri(this.host, provisionedDisk.documentSelfLink));

            //assert that the attached disk has one endpoint in its endpointLinks.
            assertEquals(1, attachedDisk.endpointLinks.size());

            //assert that the disk is attached.
            assertEquals("disk status not matching", DiskService.DiskStatus.ATTACHED,
                    attachedDisk.status);

            //assert the used device name is same as expected returned by AWS Device name utility.
            assertDeviceName(instance, attachedDisk, existingNames);

            vmStateBeforeAttach = vmStateAfterAttach;
        }

        return vmStateBeforeAttach;
    }

    protected void assertDeviceName(Instance awsInstance, DiskState diskState, List<String> existingNames) {
        if (!this.isMock) {
            AWSSupportedOS os = AWSSupportedOS.get(awsInstance.getPlatform());
            AWSSupportedVirtualizationTypes virtualizationType =
                    AWSSupportedVirtualizationTypes.get(awsInstance.getVirtualizationType());
            AWSStorageType storageType =
                    AWSStorageType.get(diskState.customProperties.get(DEVICE_TYPE));
            List<String> expectedNames =
                    AWSBlockDeviceNameMapper.getAvailableNames(os, virtualizationType, storageType,
                            awsInstance.getInstanceType(), existingNames);
            String expectedName = expectedNames.get(0);
            assertEquals(expectedName, diskState.customProperties.get(DEVICE_NAME));
            existingNames.add(expectedName);
        }
    }

    private ComputeState detachDiskAndVerify(ComputeState vmStateAfterAttach,
            List<String> detachedDiskLinks, List<String> availableDiskLinks) throws Throwable {

        performDiskOperationAndVerify(vmStateAfterAttach.documentSelfLink, detachedDiskLinks,
                ResourceOperation.DETACH_DISK.operation,
                DiskTaskService.DiskTaskState.SubStage.FINISHED);

        ComputeState vmStateAfterDetach = this.host.getServiceState(null, ComputeState.class,
                UriUtils.buildUri(this.host, this.vmState.documentSelfLink));

        assertEquals(vmStateAfterAttach.diskLinks.size() - detachedDiskLinks.size(),
                vmStateAfterDetach.diskLinks.size());

        detachedDiskLinks.forEach(detachedDiskLink -> {
            DiskState detachedDisk = this.host.getServiceState(null, DiskState.class,
                    UriUtils.buildUri(this.host, detachedDiskLink));

            assertEquals("disk status not matching", DiskService.DiskStatus.AVAILABLE,
                    detachedDisk.status);
            availableDiskLinks.add(detachedDisk.documentSelfLink);
        });

        return vmStateAfterDetach;
    }

    private void performDiskOperationAndVerify(String computeStateLink, List<String> diskLinks,
            String requestType, DiskTaskService.DiskTaskState.SubStage expectedTerminalState) throws Throwable {
        List<String> taskServiceLinks = new ArrayList<>();

        diskLinks.forEach(diskLink -> {
            DiskTaskService.DiskTaskState diskOpTask = new DiskTaskService.DiskTaskState();
            diskOpTask.taskSubStage = DiskTaskService.DiskTaskState.SubStage.STARTED;

            try {
                diskOpTask = TestUtils
                        .doPost(this.host, diskOpTask, DiskTaskService.DiskTaskState.class,
                                UriUtils.buildUri(this.host, DiskTaskService.FACTORY_LINK));
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }

            ResourceOperationRequest request = new ResourceOperationRequest();

            request.isMockRequest = this.isMock;
            request.operation = requestType;
            request.payload = new HashMap<>();
            request.payload.put(PhotonModelConstants.DISK_LINK, diskLink);
            request.resourceReference = UriUtils.buildUri(this.host, computeStateLink);
            request.taskReference = UriUtils.buildUri(this.host, diskOpTask.documentSelfLink);

            Operation diskOp = Operation
                    .createPatch(UriUtils.buildUri(this.host, AWSComputeDiskDay2Service.SELF_LINK))
                    .setBody(request)
                    .setReferer(this.host.getReferer());

            TestRequestSender sender = new TestRequestSender(this.host);
            sender.sendRequest(diskOp);

            this.host.log("Waiting for disk operation to complete");
            taskServiceLinks.add(diskOpTask.documentSelfLink);
        });

        taskServiceLinks.forEach(taskServiceLink -> {
            this.host.waitFor("disk Operation failed.", () -> {
                DiskTaskService.DiskTaskState diskTaskState = this.host
                        .getServiceState(null, DiskTaskService.DiskTaskState.class,
                                UriUtils.buildUri(this.host, taskServiceLink));

                // Check if the disk operation is successful or not.
                if (diskTaskState.taskSubStage == DiskTaskService.DiskTaskState.SubStage.FINISHED
                        || diskTaskState.taskSubStage == DiskTaskService.DiskTaskState.SubStage.FAILED) {
                    assertEquals("diskOperation Task did not terminate as expected.",
                            expectedTerminalState, diskTaskState.taskSubStage);
                    return true;
                } else {
                    return false;
                }
            });
        });
    }

    private void assertAndSetVMSecurityGroupsToBeDeleted(Instance instance, ComputeState vm) {
        // This assert is only suitable for real (non-mocking env).
        if (this.isMock) {
            return;
        }

        this.host.log(Level.INFO, "%s: Assert security groups configuration for [%s] VM",
                this.currentTestName.getMethodName(), this.vmState.name);

        // Get the SecurityGroupStates that were provided in the request ComputeState
        Collector<SecurityGroupService.SecurityGroupState, ?, Map<String, SecurityGroupService.SecurityGroupState>> convertToMap =
                Collectors.<SecurityGroupService.SecurityGroupState, String, SecurityGroupService.SecurityGroupState>toMap(
                        sg -> sg.name, sg -> sg);
        Map<String, SecurityGroupService.SecurityGroupState> currentSGNamesToStates = vm.networkInterfaceLinks
                .stream()
                // collect all NIC states in a List
                .map(nicLink -> this.host.getServiceState(null,
                        NetworkInterfaceService.NetworkInterfaceState.class,
                        UriUtils.buildUri(this.host, nicLink)))
                //collect all SecurityGroup States from all NIC states
                .<SecurityGroupService.SecurityGroupState>flatMap(
                        nicState -> nicState.securityGroupLinks.stream()
                                // obtain SecurityGroupState from each SG link
                                .map(sgLink -> {
                                    SecurityGroupService.SecurityGroupState sgState = this.host
                                            .getServiceState(null,
                                                    SecurityGroupService.SecurityGroupState.class,
                                                    UriUtils.buildUri(this.host, sgLink));
                                    return sgState;
                                }))
                // collect security group states in a map with key = SG name
                .collect(convertToMap);

        // Compare ComputeState after provisioning to the ComputeState in the request
        assertNotNull("Instance should have security groups attached.",
                instance.getSecurityGroups());
        // Provisioned Instance should have the same number of SecurityGroups as requested
        assertEquals(instance.getSecurityGroups().size(), currentSGNamesToStates.size());

        for (SecurityGroupService.SecurityGroupState currentSGState : currentSGNamesToStates
                .values()) {
            // Get corresponding requested state
            GroupIdentifier provisionedGroupIdentifier = null;
            for (GroupIdentifier awsGroupIdentifier : instance.getSecurityGroups()) {
                if (awsGroupIdentifier.getGroupId().equals(currentSGState.id)) {
                    provisionedGroupIdentifier = awsGroupIdentifier;
                    break;
                }
            }

            // Ensure that the requested SecurityGroup was actually provisioned
            assertNotNull(provisionedGroupIdentifier);

            if (currentSGState.name.contains(TestAWSSetupUtils.AWS_NEW_GROUP_PREFIX)) {

                this.sgToCleanUp = currentSGState.id;

                SecurityGroup awsSecurityGroup = getSecurityGroupsIdUsingEC2Client(this.client,
                        provisionedGroupIdentifier.getGroupId());

                assertNotNull(awsSecurityGroup);
                // Validate rules are correctly created as requested
                IpPermission awsIngressRule = awsSecurityGroup.getIpPermissions().get(0);
                IpPermission awsEgressRule = awsSecurityGroup.getIpPermissionsEgress().get(1);
                assertNotNull(awsIngressRule);
                assertNotNull(awsEgressRule);
                assertEquals("Error in created ingress rule", awsIngressRule.getIpProtocol(),
                        currentSGState.ingress.get(0).protocol);
                assertEquals("Error in created ingress rule",
                        awsIngressRule.getIpv4Ranges().get(0).getCidrIp(),
                        currentSGState.ingress.get(0).ipRangeCidr);
                assertEquals("Error in created egress rule", awsEgressRule.getIpProtocol(),
                        currentSGState.egress.get(0).protocol);
                assertEquals("Error in created egress rule",
                        awsEgressRule.getIpv4Ranges().get(0).getCidrIp(),
                        currentSGState.egress.get(0).ipRangeCidr);
            }
        }
    }

    private void provisionVM(String zoneId) throws Throwable {
        provisionVMAndAttachDisk(zoneId, null, false);
    }

    private void provisionVMAndAttachDisk(String zoneId, String existingDiskLink,
            boolean withAdditionalDisks) throws Throwable {
        // create a AWS VM compute resource
        boolean addNonExistingSecurityGroup = true;
        this.vmState = createAWSVMResource(this.host, this.computeHost, this.endpointState,
                this.getClass(), this.currentTestName.getMethodName() + "_vm1", zoneId,
                regionId, null, this.singleNicSpec, addNonExistingSecurityGroup,
                this.awsTestContext, true, withAdditionalDisks);

        // set placement link
        if (this.vmState.customProperties == null) {
            this.vmState.customProperties = new HashMap<>();
        }

        this.vmState.customProperties.put(PLACEMENT_LINK, this.computeHost.documentSelfLink);

        if (existingDiskLink != null) {
            this.vmState.diskLinks.add(existingDiskLink);
            TestUtils
                    .doPatch(this.host, this.vmState, ComputeState.class,
                            UriUtils.buildUri(this.host,
                                    this.vmState.documentSelfLink));
        }

        // kick off a provision task to do the actual VM creation
        ProvisionComputeTaskService.ProvisionComputeTaskState provisionTask = new ProvisionComputeTaskService.ProvisionComputeTaskState();

        provisionTask.computeLink = this.vmState.documentSelfLink;
        provisionTask.isMockRequest = this.isMock;
        provisionTask.taskSubStage = ProvisionComputeTaskService.ProvisionComputeTaskState.SubStage.CREATING_HOST;

        // Wait for default request timeout in minutes for the machine to be powered ON before
        // reporting failure to the parent task.
        provisionTask.documentExpirationTimeMicros = Utils.getNowMicrosUtc()
                + TimeUnit.MINUTES.toMicros(AWS_VM_REQUEST_TIMEOUT_MINUTES);

        provisionTask.tenantLinks = this.endpointState.tenantLinks;

        provisionTask = com.vmware.photon.controller.model.tasks.TestUtils.doPost(this.host,
                provisionTask, ProvisionComputeTaskService.ProvisionComputeTaskState.class,
                UriUtils.buildUri(this.host, ProvisionComputeTaskService.FACTORY_LINK));

        this.host.waitForFinishedTask(ProvisionComputeTaskService.ProvisionComputeTaskState.class,
                provisionTask.documentSelfLink);
    }

    private void provisionSingleDisk(DiskState diskState) throws Throwable {

        // start provision task to do the actual disk creation
        ProvisionDiskTaskService.ProvisionDiskTaskState provisionTask = new ProvisionDiskTaskService.ProvisionDiskTaskState();
        provisionTask.taskSubStage = ProvisionDiskTaskService.ProvisionDiskTaskState.SubStage.CREATING_DISK;

        provisionTask.diskLink = diskState.documentSelfLink;
        provisionTask.isMockRequest = this.isMock;

        provisionTask.documentExpirationTimeMicros =
                Utils.getNowMicrosUtc() + TimeUnit.MINUTES.toMicros(
                        AWS_DISK_REQUEST_TIMEOUT_MINUTES);
        provisionTask.tenantLinks = this.endpointState.tenantLinks;

        provisionTask = com.vmware.photon.controller.model.tasks.TestUtils.doPost(this.host,
                provisionTask, ProvisionDiskTaskService.ProvisionDiskTaskState.class,
                UriUtils.buildUri(this.host, ProvisionDiskTaskService.FACTORY_LINK));

        this.host.waitForFinishedTask(ProvisionDiskTaskService.ProvisionDiskTaskState.class,
                provisionTask.documentSelfLink);

    }

    /**
     * Creates the state associated with the resource pool, compute host and the VM to be created.
     */
    private void initResourcePoolAndComputeHost(String zoneId) throws Throwable {
        // Create a resource pool where the VM will be housed
        ResourcePoolService.ResourcePoolState resourcePool = createAWSResourcePool(this.host);

        AuthCredentialsService.AuthCredentialsServiceState auth = createAWSAuthentication(this.host,
                this.accessKey, this.secretKey);

        this.endpointState = TestAWSSetupUtils
                .createAWSEndpointState(this.host, auth.documentSelfLink,
                        resourcePool.documentSelfLink);

        // create a compute host for the AWS EC2 VM
        this.computeHost = createAWSComputeHost(this.host,this.endpointState, zoneId, regionId,
                this.isAwsClientMock, this.awsMockEndpointReference, null /*tags*/);
    }

    private void deleteVMAndVerifyDisks(String computeStateLink, List<String> totalAttachedDisks)
            throws Throwable {

        TestAWSSetupUtils.deleteVMs(computeStateLink, this.isMock, this.host);

        List<DiskState> persistedDisks = new ArrayList<>();
        List<DiskState> deletedDisks = new ArrayList<>();

        //divide all disklinks into persisted ones and deleted ones.
        partitionDisks(totalAttachedDisks, persistedDisks, deletedDisks);

        //add all persisted disks to detached disk list
        persistedDisks.forEach(diskState -> this.disksToCleanUp.add(diskState.documentSelfLink));

        //verify the properties of persisted disk.
        verifyPersistedDisks(this.isMock, persistedDisks);

        assertEquals(totalAttachedDisks.size() - deletedDisks.size(), persistedDisks.size());
    }

    private void partitionDisks(List<String> allAttachedDiskLinks,
            List<DiskState> persistedDisks, List<DiskState> deletedDisks) throws Throwable {
        for (String resourceLink : allAttachedDiskLinks) {
            DiskState diskState = getResourceState(this.host, resourceLink, DiskState.class);
            if (diskState.documentSelfLink != null) {
                persistedDisks.add(diskState);
            } else {
                deletedDisks.add(diskState);
            }
        }
    }

    private void verifyPersistedDisks(boolean isMock, List<DiskState> persistedDisks)
            throws Throwable {
        for (DiskState diskState : persistedDisks) {
            assertNotNull(diskState);
            assertNotNull(diskState.persistent);
            assertTrue(diskState.persistent);
            assertNotNull(diskState.id);
            if (!isMock) {
                assertTrue(diskState.id.startsWith("vol-"));
                List<Volume> volumes = getAwsDisksByIds(this.client, this.host,
                        Collections.singletonList(diskState.id));
                Volume volume = volumes.get(0);
                assertNotNull(volume);
            }
        }
    }
}
