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

import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.AWS_VM_REQUEST_TIMEOUT_MINUTES;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSAuthentication;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSComputeHost;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSResourcePool;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSVMResource;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.deleteSecurityGroupUsingEC2Client;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.getAwsInstancesByIds;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.getSecurityGroupsIdUsingEC2Client;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.regionId;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.setAwsClientMockInfo;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.setUpTestVpc;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.tearDownTestVpc;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.verifyRemovalOfResourceState;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.vpcIdExists;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.zoneId;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestProvisionAWSDisk.createAWSDiskState;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestUtils.getExecutor;
import static com.vmware.photon.controller.model.util.StartServicesHelper.ServiceMetadata.factoryService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.GroupIdentifier;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.SecurityGroup;
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
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSSecurityGroupClient;
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
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
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

    private AmazonEC2AsyncClient client;
    private Map<String, Object> awsTestContext;

    private ComputeState vmState;
    private DiskState diskState;

    private ComputeState computeHost;
    private EndpointState endpointState;

    private String sgToCleanUp = null;

    private TestAWSSetupUtils.AwsNicSpecs singleNicSpec;

    public static class AttachDiskTaskService
            extends TaskService<AttachDiskTaskService.AttachDiskTaskState> {

        public static final String FACTORY_LINK = UriPaths.PROVISIONING + "/attach-disk-tasks";

        public AttachDiskTaskService() {
            super(AttachDiskTaskState.class);
            super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        }

        public static class AttachDiskTaskState extends TaskService.TaskServiceState {
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
            AttachDiskTaskState attachDiskTaskState = getState(patch);

            if (TaskState.isFailed(response.taskInfo)) {
                attachDiskTaskState.taskSubStage = AttachDiskTaskState.SubStage.FAILED;
            } else if (TaskState.isFinished(response.taskInfo)) {
                attachDiskTaskState.taskSubStage = AttachDiskTaskState.SubStage.FINISHED;
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
        this.client = AWSUtils.getAsyncClient(creds, TestAWSSetupUtils.regionId, getExecutor());

        this.awsTestContext = new HashMap<>();
        setUpTestVpc(this.client, this.awsTestContext, this.isMock);
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

            factoryService(AttachDiskTaskService.class,
                    () -> TaskFactoryService.create(AttachDiskTaskService.class)).start(this.host);

            AWSAdapters.startServices(this.host);

            this.host.setTimeoutSeconds(1200);

            this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelMetricServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelAdaptersRegistryAdapters.LINKS);
            this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
            this.host.waitForServiceAvailable(AWSAdapters.LINKS);
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
        tearDownTestVpc(this.client, this.host, this.awsTestContext, this.isMock);
    }

    private void deleteProvisionedResources() throws Throwable {

        // store the network links and disk links for removal check later
        List<String> resourcesToDelete = new ArrayList<>();
        if (this.vmState.diskLinks != null) {
            resourcesToDelete.addAll(this.vmState.diskLinks);
        }
        if (this.vmState.networkInterfaceLinks != null) {
            resourcesToDelete.addAll(this.vmState.networkInterfaceLinks);
        }

        TestAWSSetupUtils.deleteVMs(this.vmState.documentSelfLink, this.isMock, this.host);

        if (!this.isMock) {
            //TODO: When delete disk is implemented the below call will be replaced.
            TestAWSSetupUtils
                    .deleteEbsVolumeUsingEC2Client(this.client, this.host, this.diskState.id);

            if (!vpcIdExists(this.client, TestAWSSetupUtils.AWS_DEFAULT_VPC_ID)) {
                SecurityGroup securityGroup = new AWSSecurityGroupClient(this.client)
                        .getSecurityGroup(TestAWSSetupUtils.AWS_DEFAULT_GROUP_NAME,
                                (String) this.awsTestContext.get(TestAWSSetupUtils.VPC_KEY));
                if (securityGroup != null) {
                    deleteSecurityGroupUsingEC2Client(this.client, this.host,
                            securityGroup.getGroupId());
                }
                deleteSecurityGroupUsingEC2Client(this.client, this.host, this.sgToCleanUp);
            }
        }

        verifyRemovalOfResourceState(this.host, resourcesToDelete);

        this.vmState = null;
        this.sgToCleanUp = null;
    }

    @Test
    public void testAttachDisk() throws Throwable {
        Gson gson = new Gson();

        provisionSingleAWS();

        // check that the VM has been created
        ServiceDocumentQueryResult computeQueryResult = ProvisioningUtils
                .queryComputeInstances(this.host, 2);

        ComputeState compute = gson.fromJson(
                computeQueryResult.documents.get(this.vmState.documentSelfLink).toString(),
                ComputeState.class);

        String instanceZoneId = zoneId;

        if (!this.isMock) {

            List<Instance> instances = getAwsInstancesByIds(this.client, this.host,
                    Collections.singletonList(compute.id));
            Instance instance = instances.get(0);
            instanceZoneId = instance.getPlacement().getAvailabilityZone();

            ComputeState vm = this.host.getServiceState(null, ComputeState.class,
                    UriUtils.buildUri(this.host, this.vmState.documentSelfLink));

            assertAndSetVMSecurityGroupsToBeDeleted(instance, vm);
        }

        provisionSingleDisk(instanceZoneId);

        ServiceDocumentQueryResult diskQueryResult = ProvisioningUtils
                .queryDiskInstances(this.host, compute.diskLinks.size() + 1);

        DiskState availableDisk = gson
                .fromJson(diskQueryResult.documents.get(this.diskState.documentSelfLink).toString(),
                        DiskState.class);

        assertEquals("disk status not matching", DiskService.DiskStatus.AVAILABLE,
                availableDisk.status);

        AttachDiskTaskService.AttachDiskTaskState attachTask = new AttachDiskTaskService.AttachDiskTaskState();
        attachTask.taskSubStage = AttachDiskTaskService.AttachDiskTaskState.SubStage.STARTED;

        attachTask = TestUtils
                .doPost(this.host, attachTask, AttachDiskTaskService.AttachDiskTaskState.class,
                        UriUtils.buildUri(this.host, AttachDiskTaskService.FACTORY_LINK));

        ResourceOperationRequest request = new ResourceOperationRequest();

        request.isMockRequest = this.isMock;
        request.operation = ResourceOperation.ATTACH_DISK.operation;
        request.payload = new HashMap<>();
        request.payload.put(PhotonModelConstants.DISK_LINK, this.diskState.documentSelfLink);
        request.resourceReference = UriUtils.buildUri(this.host, compute.documentSelfLink);
        request.taskReference = UriUtils.buildUri(this.host, attachTask.documentSelfLink);

        Operation attachDiskOp = Operation
                .createPatch(UriUtils.buildUri(this.host, AWSComputeDiskDay2Service.SELF_LINK))
                .setBody(request)
                .setReferer(this.host.getReferer());

        TestRequestSender sender = new TestRequestSender(this.host);
        sender.sendRequest(attachDiskOp);

        this.host.log("Waiting for disk attach to complete");
        final String attachTaskServiceLink = attachTask.documentSelfLink;
        this.host.waitFor("Attach disk failed.", () -> {
            AttachDiskTaskService.AttachDiskTaskState attachDiskTaskState = this.host
                    .getServiceState(null, AttachDiskTaskService.AttachDiskTaskState.class,
                            UriUtils.buildUri(this.host, attachTaskServiceLink));

            // Check for the disk is attached to a vm or not.
            if (attachDiskTaskState.taskSubStage
                    == AttachDiskTaskService.AttachDiskTaskState.SubStage.FINISHED) {
                return true;
            } else {
                return false;
            }
        });

        ComputeState vm = this.host.getServiceState(null, ComputeState.class,
                UriUtils.buildUri(this.host, this.vmState.documentSelfLink));

        assertEquals(compute.diskLinks.size() + 1, vm.diskLinks.size());

        DiskState attachedDisk = this.host.getServiceState(null, DiskState.class,
                UriUtils.buildUri(this.host, this.diskState.documentSelfLink));

        assertEquals("disk status not matching", DiskService.DiskStatus.ATTACHED,
                attachedDisk.status);

        this.vmState = vm;
        this.diskState = attachedDisk;
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

    private void provisionSingleAWS() throws Throwable {

        initResourcePoolAndComputeHost();

        // create a AWS VM compute resource
        boolean addNonExistingSecurityGroup = true;
        this.vmState = createAWSVMResource(this.host, this.computeHost, this.endpointState,
                this.getClass(), this.currentTestName.getMethodName() + "_vm1", zoneId,
                regionId, null, this.singleNicSpec, addNonExistingSecurityGroup);

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
                provisionTask,
                ProvisionComputeTaskService.ProvisionComputeTaskState.class,
                UriUtils.buildUri(this.host, ProvisionComputeTaskService.FACTORY_LINK));

        this.host.waitForFinishedTask(ProvisionComputeTaskService.ProvisionComputeTaskState.class,
                provisionTask.documentSelfLink);
    }

    private void provisionSingleDisk(String zoneId) throws Throwable {

        this.diskState = createAWSDiskState(this.host, this.endpointState,
                this.currentTestName.getMethodName() + "_disk1", zoneId, regionId);

        // start provision task to do the actual disk creation
        ProvisionDiskTaskService.ProvisionDiskTaskState provisionTask = new ProvisionDiskTaskService.ProvisionDiskTaskState();
        provisionTask.taskSubStage = ProvisionDiskTaskService.ProvisionDiskTaskState.SubStage.CREATING_DISK;

        provisionTask.diskLink = this.diskState.documentSelfLink;
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
    private void initResourcePoolAndComputeHost() throws Throwable {
        // Create a resource pool where the VM will be housed
        ResourcePoolService.ResourcePoolState resourcePool = createAWSResourcePool(this.host);

        AuthCredentialsService.AuthCredentialsServiceState auth = createAWSAuthentication(this.host,
                this.accessKey, this.secretKey);

        this.endpointState = TestAWSSetupUtils
                .createAWSEndpointState(this.host, auth.documentSelfLink,
                        resourcePool.documentSelfLink);

        // create a compute host for the AWS EC2 VM
        this.computeHost = createAWSComputeHost(this.host,
                this.endpointState,
                zoneId, regionId,
                this.isAwsClientMock,
                this.awsMockEndpointReference, null /*tags*/);
    }
}