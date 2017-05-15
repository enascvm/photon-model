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

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.getSecurityGroup;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.AWS_VM_REQUEST_TIMEOUT_MINUTES;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSAuthentication;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSComputeHost;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSResourcePool;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSVMResource;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.deleteSecurityGroupUsingEC2Client;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.getAwsInstancesByIds;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.getCompute;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.getSecurityGroupsIdUsingEC2Client;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.regionId;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.setAwsClientMockInfo;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.setUpTestVpc;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.tearDownTestVpc;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.verifyRemovalOfResourceState;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.vpcIdExists;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.zoneId;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestUtils.getExecutor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.GroupIdentifier;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.SecurityGroup;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.vmware.photon.controller.model.PhotonModelMetricServices;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapterapi.ResourceOperationResponse;
import com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.AwsNicSpecs;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperation;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationRequest;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;
import com.vmware.photon.controller.model.tasks.TestUtils;

import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Test to provision a VM instance on AWS and tear it down The test exercises the AWS instance
 * adapter to create the VM All public fields below can be specified via command line arguments If
 * the 'isMock' flag is set to true the test runs the adapter in mock mode and does not actually
 * create a VM. Minimally the accessKey and secretKey for AWS must be specified.
 *
 */
public class AWSRebootServiceTest {

    private static final String INSTANCEID_PREFIX = "i-";

    private VerificationHost host;

    private ComputeState computeHost;
    private EndpointState endpointState;

    // fields that are used across method calls, stash them as private fields
    private ComputeService.ComputeState vmState;
    private String sgToCleanUp = null;
    public String accessKey = "accessKey";
    public String secretKey = "secretKey";
    public boolean isMock = true;
    public boolean isAwsClientMock = false;
    public String awsMockEndpointReference = null;
    private AmazonEC2AsyncClient client;

    private Map<String, Object> awsTestContext;
    private AwsNicSpecs singleNicSpec;

    @Rule
    public TestName currentTestName = new TestName();

    @Before
    public void setUp() throws Exception {
        CommandLineArgumentParser.parseFromProperties(this);

        setAwsClientMockInfo(this.isAwsClientMock, this.awsMockEndpointReference);
        AuthCredentialsServiceState creds = new AuthCredentialsServiceState();
        creds.privateKey = this.secretKey;
        creds.privateKeyId = this.accessKey;
        this.client = AWSUtils.getAsyncClient(creds, TestAWSSetupUtils.regionId, getExecutor());

        this.awsTestContext = new HashMap<>();
        setUpTestVpc(this.client, this.awsTestContext, this.isMock);
        this.singleNicSpec = (AwsNicSpecs) this.awsTestContext.get(TestAWSSetupUtils.NIC_SPECS_KEY);

        this.host = VerificationHost.create(0);
        try {
            this.host.setMaintenanceIntervalMicros(TimeUnit.MILLISECONDS.toMicros(250));
            this.host.start();
            PhotonModelServices.startServices(this.host);
            PhotonModelAdaptersRegistryAdapters.startServices(this.host);
            PhotonModelMetricServices.startServices(this.host);
            PhotonModelTaskServices.startServices(this.host);
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
        // try to delete the VMs
        deleteProvisionedVMs();
        if (this.vmState != null && this.vmState.id.startsWith(INSTANCEID_PREFIX)) {
            try {
                TestAWSSetupUtils.deleteVMs(this.vmState.documentSelfLink, this.isMock, this.host);

                deleteSecurityGroupUsingEC2Client(this.client, this.host, this.sgToCleanUp);
            } catch (Throwable deleteEx) {
                // just log and move on
                this.host.log(Level.WARNING, "Exception deleting VM - %s", deleteEx.getMessage());
            }
        }
        this.host.tearDownInProcessPeers();
        this.host.toggleNegativeTestMode(false);
        this.host.tearDown();

        setAwsClientMockInfo(false, null);
        tearDownTestVpc(this.client, this.host, this.awsTestContext, this.isMock);
    }

    /**
     * Creates the state associated with the resource pool, compute host and the VM to be created.
     *
     * @throws Throwable
     */
    private void initResourcePoolAndComputeHost() throws Throwable {
        // Create a resource pool where the VM will be housed
        ResourcePoolState resourcePool = createAWSResourcePool(this.host);

        AuthCredentialsServiceState auth = createAWSAuthentication(this.host, this.accessKey, this.secretKey);

        this.endpointState = TestAWSSetupUtils.createAWSEndpointState(this.host, auth.documentSelfLink, resourcePool.documentSelfLink);

        // create a compute host for the AWS EC2 VM
        this.computeHost = createAWSComputeHost(this.host,
                this.endpointState,
                zoneId, regionId,
                this.isAwsClientMock,
                this.awsMockEndpointReference, null /*tags*/);
    }

    // Creates a AWS instance via a provision task.
    @Test
    public void testReboot() throws Throwable {
        provisionSingleAWS();

        // check that the VM has been created
        ProvisioningUtils.queryComputeInstances(this.host, 2);
        ComputeState compute = getCompute(this.host, this.vmState.documentSelfLink);
        if (!this.isMock) {

            List<Instance> instances = getAwsInstancesByIds(this.client, this.host,
                    Collections.singletonList(compute.id));
            Instance instance = instances.get(0);
            ComputeState vm = this.host.getServiceState(null,
                    ComputeState.class,
                    UriUtils.buildUri(this.host, this.vmState.documentSelfLink));
            assertAndSetVMSecurityGroupsToBeDeleted(instance,vm);
        }

        String taskLink = UUID.randomUUID().toString();
        ResourceOperationRequest request = new ResourceOperationRequest();
        request.isMockRequest = this.isMock;
        request.operation = ResourceOperation.REBOOT.operation;
        request.payload = new HashMap<>();
        request.resourceReference =  UriUtils.buildUri(this.host, compute.documentSelfLink);
        request.taskReference = UriUtils.buildUri(this.host, taskLink);
        TestContext ctx = this.host.testCreate(2);

        createTaskResultListener(this.host, taskLink, (u) -> {
            if (u.getAction() != Service.Action.PATCH) {
                return false;
            }
            ResourceOperationResponse response = u.getBody(ResourceOperationResponse.class);
            if (TaskState.isFailed(response.taskInfo)) {
                ctx.failIteration(
                        new IllegalStateException(response.taskInfo.failure.message));
            } else if (TaskState.isFinished(response.taskInfo)) {
                ctx.completeIteration();
            }
            return true;
        });

        Operation rebootOp = Operation.createPatch(UriUtils.buildUri(this.host,AWSRebootService.SELF_LINK))
                .setBody(request)
                .setReferer(this.host.getReferer())
                .setCompletion((o,e) -> {
                    if (e != null) {
                        ctx.failIteration(e);
                        return;
                    }
                    ctx.completeIteration();
                });
        this.host.send(rebootOp);
        ctx.await();
        ComputeState vm = this.host.getServiceState(null,
                ComputeState.class,
                UriUtils.buildUri(this.host, this.vmState.documentSelfLink));
        assertEquals(ComputeService.PowerState.ON, vm.powerState);
    }

    private void provisionSingleAWS() throws Throwable {

        initResourcePoolAndComputeHost();

        // create a AWS VM compute resource
        boolean addNonExistingSecurityGroup = true;
        this.vmState = createAWSVMResource(this.host, this.computeHost, this.endpointState,
                this.getClass(),
                this.currentTestName.getMethodName() + "_vm1", zoneId, regionId,
                null /* tagLinks */, this.singleNicSpec, addNonExistingSecurityGroup);

        // kick off a provision task to do the actual VM creation
        ProvisionComputeTaskState provisionTask = new ProvisionComputeTaskService.ProvisionComputeTaskState();

        provisionTask.computeLink = this.vmState.documentSelfLink;
        provisionTask.isMockRequest = this.isMock;
        provisionTask.taskSubStage = ProvisionComputeTaskState.SubStage.CREATING_HOST;
        // Wait for default request timeout in minutes for the machine to be powered ON before
        // reporting failure to the parent task.
        provisionTask.documentExpirationTimeMicros = Utils.getNowMicrosUtc()
                + TimeUnit.MINUTES.toMicros(AWS_VM_REQUEST_TIMEOUT_MINUTES);

        provisionTask.tenantLinks = this.endpointState.tenantLinks;

        provisionTask = TestUtils.doPost(this.host,
                provisionTask,
                ProvisionComputeTaskState.class,
                UriUtils.buildUri(this.host, ProvisionComputeTaskService.FACTORY_LINK));

        this.host.waitForFinishedTask(ProvisionComputeTaskState.class,
                provisionTask.documentSelfLink);
    }

    private void deleteProvisionedVMs() throws Throwable  {

        // store the network links and disk links for removal check later
        List<String> resourcesToDelete = new ArrayList<>();
        if (this.vmState.diskLinks != null) {
            resourcesToDelete.addAll(this.vmState.diskLinks);
        }
        if (this.vmState.networkInterfaceLinks != null) {
            resourcesToDelete.addAll(this.vmState.networkInterfaceLinks);
        }
        TestAWSSetupUtils.deleteVMs(this.vmState.documentSelfLink, this.isMock, this.host);

        if (!this.isMock && !vpcIdExists(this.client, TestAWSSetupUtils.AWS_DEFAULT_VPC_ID)) {
            SecurityGroup securityGroup = getSecurityGroup(this.client, TestAWSSetupUtils.AWS_DEFAULT_GROUP_NAME,
                    (String) this.awsTestContext.get(TestAWSSetupUtils.VPC_KEY));
            if (securityGroup != null) {
                deleteSecurityGroupUsingEC2Client(this.client, this.host, securityGroup.getGroupId());
            }
            deleteSecurityGroupUsingEC2Client(this.client, this.host, this.sgToCleanUp);
        }

        verifyRemovalOfResourceState(this.host, resourcesToDelete);

        this.vmState = null;
        this.sgToCleanUp = null;
    }

    private void assertAndSetVMSecurityGroupsToBeDeleted(Instance instance, ComputeState vm) {
        // This assert is only suitable for real (non-mocking env).
        if (this.isMock) {
            return;
        }

        this.host.log(Level.INFO, "%s: Assert security groups configuration for [%s] VM",
                this.currentTestName.getMethodName(), this.vmState.name);

        // Get the SecurityGroupStates that were provided in the request ComputeState
        Collector<SecurityGroupState, ?, Map<String, SecurityGroupState>> convertToMap =
                Collectors.<SecurityGroupState, String, SecurityGroupState> toMap(sg -> sg.name, sg -> sg);
        Map<String, SecurityGroupState> currentSGNamesToStates = vm.networkInterfaceLinks.stream()
                // collect all NIC states in a List
                .map(nicLink -> this.host.getServiceState(null,
                        NetworkInterfaceState.class,
                        UriUtils.buildUri(this.host, nicLink)))
                //collect all SecurityGroup States from all NIC states
                .<SecurityGroupState> flatMap(nicState -> nicState.securityGroupLinks.stream()
                        // obtain SecurityGroupState from each SG link
                        .map(sgLink -> {
                            SecurityGroupState sgState = this.host.getServiceState(null,
                                    SecurityGroupState.class,
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

        for (SecurityGroupState currentSGState : currentSGNamesToStates.values()) {
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

                SecurityGroup awsSecurityGroup = getSecurityGroupsIdUsingEC2Client(this.client, provisionedGroupIdentifier.getGroupId());

                assertNotNull(awsSecurityGroup);
                // Validate rules are correctly created as requested
                IpPermission awsIngressRule = awsSecurityGroup.getIpPermissions().get(0);
                IpPermission awsEgressRule = awsSecurityGroup.getIpPermissionsEgress().get(1);
                assertNotNull(awsIngressRule);
                assertNotNull(awsEgressRule);
                assertEquals("Error in created ingress rule", awsIngressRule.getIpProtocol(), currentSGState.ingress.get(0).protocol);
                assertEquals("Error in created ingress rule", awsIngressRule.getIpv4Ranges().get(0).getCidrIp(), currentSGState.ingress.get(0).ipRangeCidr);
                assertEquals("Error in created egress rule", awsEgressRule.getIpProtocol(), currentSGState.egress.get(0).protocol);
                assertEquals("Error in created egress rule", awsEgressRule.getIpv4Ranges().get(0).getCidrIp(), currentSGState.egress.get(0).ipRangeCidr);
            }
        }
    }

    private void createTaskResultListener(VerificationHost host, String taskLink,
                                          Function<Operation, Boolean> h) {
        StatelessService service = new StatelessService() {
            @Override
            public void handleRequest(Operation update) {
                if (!h.apply(update)) {
                    super.handleRequest(update);
                }
            }
        };

        Operation startOp = Operation
                .createPost(host, taskLink)
                .setCompletion(this.host.getCompletion())
                .setReferer(this.host.getReferer());
        this.host.startService(startOp, service);

    }
}
