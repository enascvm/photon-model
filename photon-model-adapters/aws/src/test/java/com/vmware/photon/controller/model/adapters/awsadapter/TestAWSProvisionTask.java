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
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.DEVICE_NAME;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.DISK_IOPS;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.VOLUME_TYPE;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.AWS_VM_REQUEST_TIMEOUT_MINUTES;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.avalabilityZoneIdentifier;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.DescribeVolumesRequest;
import com.amazonaws.services.ec2.model.DescribeVolumesResult;
import com.amazonaws.services.ec2.model.GroupIdentifier;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceBlockDeviceMapping;
import com.amazonaws.services.ec2.model.InstanceNetworkInterface;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.Volume;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.vmware.photon.controller.model.PhotonModelMetricServices;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse.ComputeStats;
import com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.AwsNicSpecs;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSSecurityGroupClient;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService.SingleResourceTaskCollectionStage;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Test to provision a VM instance on AWS and tear it down The test exercises the AWS instance
 * adapter to create the VM All public fields below can be specified via command line arguments If
 * the 'isMock' flag is set to true the test runs the adapter in mock mode and does not actually
 * create a VM. Minimally the accessKey and secretKey for AWS must be specified.
 *
 */
public class TestAWSProvisionTask {

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
    // the default collection period is 5 mins; set a value that spans 2 periods
    public int timeElapsedSinceLastCollectionInMinutes = 11;

    private Map<String, Object> awsTestContext;
    private AwsNicSpecs singleNicSpec;
    private static Map<String, Integer> instanceStoreDiskSizeSupportMap = new HashMap<>();

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
            PhotonModelMetricServices.startServices(this.host);
            PhotonModelTaskServices.startServices(this.host);
            PhotonModelAdaptersRegistryAdapters.startServices(this.host);
            AWSAdapters.startServices(this.host);

            this.host.setTimeoutSeconds(600);

            this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelAdaptersRegistryAdapters.LINKS);
            this.host.waitForServiceAvailable(AWSAdapters.LINKS);
        } catch (Throwable e) {
            throw new Exception(e);
        }
    }

    @After
    public void tearDown() throws InterruptedException {
        if (this.host == null) {
            return;
        }
        // try to delete the VMs
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
    public void testProvision() throws Throwable {

        initResourcePoolAndComputeHost();

        // create a AWS VM compute resoruce
        boolean addNonExistingSecurityGroup = true;
        this.vmState = createAWSVMResource(this.host, this.computeHost, this.endpointState,
                this.getClass(),
                this.currentTestName.getMethodName() + "_vm1", zoneId, regionId,
                null /* tagLinks */, this.singleNicSpec, addNonExistingSecurityGroup);


        // set placement link
        String zoneId = TestAWSSetupUtils.zoneId + avalabilityZoneIdentifier;
        ComputeState zoneComputeState = createAWSComputeHost(this.host, this.endpointState,
                zoneId,
                regionId, this.isAwsClientMock, this.awsMockEndpointReference, null);

        zoneComputeState.id = zoneId;

        zoneComputeState = TestUtils
                .doPatch(this.host, zoneComputeState, ComputeState.class, UriUtils.buildUri(this.host,
                        zoneComputeState.documentSelfLink));

        if (this.vmState.customProperties == null) {
            this.vmState.customProperties = new HashMap<>();
        }

        this.vmState.customProperties.put(PLACEMENT_LINK, zoneComputeState.documentSelfLink);
        TestUtils
                .doPatch(this.host, this.vmState, ComputeState.class, UriUtils.buildUri(this.host,
                        this.vmState.documentSelfLink));

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

        // check that the VM has been created
        ProvisioningUtils.queryComputeInstances(this.host, 3);

        if (!this.isMock) {
            ComputeState compute = getCompute(this.host, this.vmState.documentSelfLink);

            List<Instance> instances = getAwsInstancesByIds(this.client, this.host,
                    Collections.singletonList(compute.id));
            Instance instance = instances.get(0);

            assertTags(Collections.emptySet(), instance, this.vmState.name);

            assertVmNetworksConfiguration(instance);

            assertStorageConfiguration(this.client, instance, compute);

            assertEquals(zoneId, instance.getPlacement().getAvailabilityZone());
        }

        this.host.setTimeoutSeconds(600);
        this.host.waitFor("Error waiting for stats with default collection windows", () -> {
            try {
                this.host.log(Level.INFO,
                        "Issuing stats request for VM with default collection window.");
                issueStatsRequest(this.vmState, null);
            } catch (Throwable t) {
                return false;
            }
            return true;
        });

        // store the network links and disk links for removal check later
        List<String> resourcesToDelete = new ArrayList<>();
        if (this.vmState.diskLinks != null) {
            resourcesToDelete.addAll(this.vmState.diskLinks);
        }
        if (this.vmState.networkInterfaceLinks != null) {
            resourcesToDelete.addAll(this.vmState.networkInterfaceLinks);
        }

        // delete vm
        TestAWSSetupUtils.deleteVMs(this.vmState.documentSelfLink, this.isMock, this.host);

        if (!this.isMock && !vpcIdExists(this.client, TestAWSSetupUtils.AWS_DEFAULT_VPC_ID)) {
            SecurityGroup securityGroup = new AWSSecurityGroupClient(this.client)
                    .getSecurityGroup(TestAWSSetupUtils.AWS_DEFAULT_GROUP_NAME,
                    (String) this.awsTestContext.get(TestAWSSetupUtils.VPC_KEY));
            if (securityGroup != null) {
                deleteSecurityGroupUsingEC2Client(this.client, this.host, securityGroup.getGroupId());
            }
        }

        // validates the local documents of network links and disk links have been removed
        verifyRemovalOfResourceState(this.host, resourcesToDelete);

        // create another AWS VM
        List<String> instanceIdList = new ArrayList<>();

        Set<TagState> tags = createTags(null,
                "testProvisionKey1", "testProvisionValue1",
                "testProvisionKey2", "testProvisionValue2");

        Set<String> tagLinks = tags.stream().map(t -> t.documentSelfLink)
                .collect(Collectors.toSet());

        addNonExistingSecurityGroup = false;
        this.vmState = createAWSVMResource(this.host, this.computeHost, this.endpointState,
                this.getClass(),
                this.currentTestName.getMethodName() + "_vm2", TestAWSSetupUtils.zoneId, regionId,
                tagLinks, this.singleNicSpec, addNonExistingSecurityGroup);

        TestAWSSetupUtils.provisionMachine(this.host, this.vmState, this.isMock, instanceIdList);

        if (!this.isMock) {
            ComputeState compute = getCompute(this.host, this.vmState.documentSelfLink);

            List<Instance> instances = getAwsInstancesByIds(this.client, this.host,
                    Collections.singletonList(compute.id));
            assertTags(tags, instances.get(0), this.vmState.name);

            assertVmNetworksConfiguration(instances.get(0));

            assertStorageConfiguration(this.client, instances.get(0), compute);

            // reach out to AWS and get the current state
            TestAWSSetupUtils
                    .getBaseLineInstanceCount(this.host, this.client, null);
        }

        // delete just the local representation of the resource
        TestAWSSetupUtils.deleteVMs(this.vmState.documentSelfLink, this.isMock, this.host, true);
        if (!this.isMock) {
            try {
                TestAWSSetupUtils
                        .getBaseLineInstanceCount(this.host, this.client, null);

            } finally {
                TestAWSSetupUtils.deleteVMsUsingEC2Client(this.client, this.host, instanceIdList);
                deleteSecurityGroupUsingEC2Client(this.client, this.host, this.sgToCleanUp);
            }
        }
        this.vmState = null;
        this.sgToCleanUp = null;
    }

    private void assertVmNetworksConfiguration(Instance awsInstance) throws Throwable {

        // This assert is only suitable for real (non-mocking env).
        if (this.isMock) {
            return;
        }

        this.host.log(Level.INFO, "%s: Assert network configuration for [%s] VM",
                this.currentTestName.getMethodName(), this.vmState.name);

        ComputeState vm = this.host.getServiceState(null,
                ComputeState.class,
                UriUtils.buildUri(this.host, this.vmState.documentSelfLink));

        assertNotNull(
                "ComputeState.address should be set to public IP.",
                vm.address);

        assertEquals("ComputeState.address should be set to AWS Instance public IP.",
                awsInstance.getPublicIpAddress(), vm.address);

        for (String nicLink : vm.networkInterfaceLinks) {

            NetworkInterfaceState nicState = this.host.getServiceState(null,
                    NetworkInterfaceState.class,
                    UriUtils.buildUri(this.host, nicLink));
            // for now validate only the 0 NIC as we are creating single NIC VM
            if (nicState.deviceIndex != 0) {
                continue;
            }

            InstanceNetworkInterface awsNic = null;
            for (InstanceNetworkInterface nic : awsInstance.getNetworkInterfaces()) {
                if (nic.getAttachment().getDeviceIndex() == nicState.deviceIndex) {
                    awsNic = nic;
                    break;
                }
            }

            assertNotNull("Unable to find AWS NIC with device index " + nicState.deviceIndex,
                    awsNic);
            assertEquals(
                    "NetworkInterfaceState[" + nicState.deviceIndex
                            + "].address should be set to AWS NIC private IP.",
                    awsNic.getPrivateIpAddress(), nicState.address);
        }
        assertVMSercurityGroupsConfiguration(awsInstance, vm);
    }

    private void assertVMSercurityGroupsConfiguration(Instance instance, ComputeState vm) {
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

    private void assertTags(Set<TagState> expectedTagStates, Instance instance,
            String instanceName) {
        Set<Tag> expectedTags = expectedTagStates.stream().map(ts -> new Tag(ts.key, ts.value))
                .collect(Collectors.toSet());

        Set<Tag> actualTags = new HashSet<>(instance.getTags());
        // account for the name tag
        assertEquals(expectedTags.size() + 1, actualTags.size());
        assertTrue(actualTags.containsAll(expectedTags));

        Tag nameTag = new Tag(AWSConstants.AWS_TAG_NAME, instanceName);
        assertTrue(actualTags.contains(nameTag));
    }

    private Set<TagState> createTags(List<String> tenantLinks, String... keyValue)
            throws Throwable {

        Set<TagState> result = new HashSet<>();

        for (int i = 0; i <= keyValue.length - 2; i = i + 2) {
            TagState tagState = new TagState();
            tagState.tenantLinks = tenantLinks;
            tagState.key = keyValue[i];
            tagState.value = keyValue[i + 1];

            TagState response = TestUtils.doPost(this.host, tagState, TagState.class,
                    UriUtils.buildUri(this.host, TagService.FACTORY_LINK));

            result.add(response);
        }

        return result;
    }

    private void issueStatsRequest(ComputeState vm, Long lastCollectionTime) throws Throwable {
        // spin up a stateless service that acts as the parent link to patch back to
        StatelessService parentService = new StatelessService() {
            @Override
            public void handleRequest(Operation op) {
                if (op.getAction() == Action.PATCH) {
                    if (!TestAWSProvisionTask.this.isMock) {
                        ComputeStatsResponse resp = op.getBody(ComputeStatsResponse.class);
                        if (resp.statsList.size() != 1) {
                            TestAWSProvisionTask.this.host.failIteration(
                                    new IllegalStateException("response size was incorrect."));
                            return;
                        }
                        // Size == 1, because APICallCount
                        if (resp.statsList.get(0).statValues.size() == 1) {
                            TestAWSProvisionTask.this.host.failIteration(new IllegalStateException(
                                    "incorrect number of metrics received."));
                            return;
                        }
                        if (lastCollectionTime != null) {
                            if (resp.statsList.get(0).statValues
                                    .get(PhotonModelConstants.CPU_UTILIZATION_PERCENT)
                                    .size() < 2) {
                                TestAWSProvisionTask.this.host
                                        .failIteration(new IllegalStateException(
                                                "incorrect number of data points received when collection window is specified."));
                                return;
                            }

                        }
                        if (!resp.statsList.get(0).computeLink.equals(vm.documentSelfLink)) {
                            TestAWSProvisionTask.this.host.failIteration(new IllegalStateException(
                                    "Incorrect resourceReference returned."));
                            return;
                        }
                        verifyCollectedStats(resp, lastCollectionTime);
                    }
                    TestAWSProvisionTask.this.host.completeIteration();
                }
            }
        };
        String servicePath = UUID.randomUUID().toString();
        Operation startOp = Operation.createPost(UriUtils.buildUri(this.host, servicePath));
        this.host.startService(startOp, parentService);
        ComputeStatsRequest statsRequest = new ComputeStatsRequest();
        statsRequest.resourceReference = UriUtils.buildUri(this.host, vm.documentSelfLink);
        statsRequest.isMockRequest = this.isMock;
        statsRequest.nextStage = SingleResourceTaskCollectionStage.UPDATE_STATS.name();
        statsRequest.taskReference = UriUtils.buildUri(this.host, servicePath);
        if (lastCollectionTime != null) {
            statsRequest.lastCollectionTimeMicrosUtc = lastCollectionTime;
        }
        this.host.sendAndWait(Operation.createPatch(UriUtils.buildUri(
                this.host, AWSUriPaths.AWS_STATS_ADAPTER))
                .setBody(statsRequest)
                .setReferer(this.host.getUri()));
    }

    private void verifyCollectedStats(ComputeStatsResponse response, Long lastCollectionTime) {
        ComputeStats computeStats = response.statsList.get(0);
        assertTrue("Compute Link is empty", !computeStats.computeLink.isEmpty());
        // Check that stat values are accompanied with Units.
        for (String key : computeStats.statValues.keySet()) {
            List<ServiceStat> stats = computeStats.statValues.get(key);
            for (ServiceStat stat : stats) {
                assertTrue("Unit is empty", !stat.unit.isEmpty());
                // Check if burn rate values are positive.
                if (key.equalsIgnoreCase(PhotonModelConstants.AVERAGE_BURN_RATE_PER_HOUR)) {
                    assertTrue("Average burn rate is negative", stat.latestValue >= 0);
                }

                if (key.equalsIgnoreCase(PhotonModelConstants.CURRENT_BURN_RATE_PER_HOUR)) {
                    assertTrue("Current burn rate is negative", stat.latestValue >= 0);
                }
            }
            // If the statsCollectionTime was set to sometime in the past, the adapter should be
            // collecting more than one value for the same metric. Using cpu utilization as an
            // representative case as the number
            // of data points can vary across metrics even if the window is set when requesting data
            // from the provider.
            if (lastCollectionTime != null
                    && key.equalsIgnoreCase(PhotonModelConstants.CPU_UTILIZATION_PERCENT)) {
                assertTrue(
                        "incorrect number of data points received when collection window is specified for metric ."
                                + key,
                        stats.size() > 1);
            }

            // Check if the datapoints collected are after the lastCollectionTime.
            if (lastCollectionTime != null
                    && key.equalsIgnoreCase(PhotonModelConstants.ESTIMATED_CHARGES)) {
                for (ServiceStat stat : stats) {
                    assertTrue("The datapoint collected is older than last collection time.",
                            stat.sourceTimeMicrosUtc >= lastCollectionTime);
                }
            }
        }
    }

    private void assertStorageConfiguration(AmazonEC2AsyncClient client, Instance awsInstance, ComputeState compute)
            throws Throwable {
        // This assert is only suitable for real (non-mock) environment.
        if (this.isMock) {
            return;
        }

        this.host.log(Level.INFO, "%s: Assert boot disk size for [%s] VM",
                this.currentTestName.getMethodName(), this.vmState.name);

        ComputeState vm = this.host.getServiceState(null,
                ComputeState.class, UriUtils.buildUri(this.host, this.vmState.documentSelfLink));

        List<String> additionalDiskLinks = new ArrayList<>();
        List<String> existingDataDiskLinks = new ArrayList<>();
        String bootDiskLink = vm.diskLinks.get(0);
        for (int i = 1; i < compute.diskLinks.size(); i++) {
            DiskState disk = getDiskState(vm.diskLinks.get(i));
            if (disk.bootOrder == null) {
                additionalDiskLinks.add(disk.documentSelfLink);
            } else {
                bootDiskLink = disk.documentSelfLink;
            }
        }

        //For now there is a boot disk and one additional disk attached to the compute
        assertBootDiskConfiguration(client, awsInstance, bootDiskLink);

        //assert additional disk configuration
        assertDataDiskConfiguration(client, awsInstance, additionalDiskLinks);
    }


    protected void assertBootDiskConfiguration(AmazonEC2AsyncClient client, Instance awsInstance,
            String diskLink) {
        DiskState diskState = getDiskState(diskLink);

        Volume bootVolume = getVolume(client, awsInstance, awsInstance.getRootDeviceName());

        assertEquals("Boot Disk capacity in diskstate is not matching the boot disk size of the "
                + "vm launched in aws", diskState.capacityMBytes, bootVolume.getSize() * 1024);

        assertEquals(
                "Boot disk type in diskstate is not same as the type of the volume attached to the VM",
                diskState.customProperties.get("volumeType"), bootVolume.getVolumeType());

        assertEquals(
                "Boot disk iops in diskstate is the same as the iops of the volume attached to the VM",
                Integer.parseInt(diskState.customProperties.get("iops")),
                bootVolume.getIops().intValue());
    }

    protected void assertDataDiskConfiguration(AmazonEC2AsyncClient client,
            Instance awsInstance, List<String> diskLinks) {
        for (String diskLink : diskLinks) {
            DiskState diskState = getDiskState(diskLink);
            assertEbsDiskConfiguration(client, awsInstance, diskState);
        }
    }

    protected void assertEbsDiskConfiguration(AmazonEC2AsyncClient client, Instance awsInstance,
            DiskState diskState) {
        assertNotNull("Additional Disk should contain atleast one custom property",
                diskState.customProperties);

        assertTrue("deviceName is missing from the custom properties", diskState
                .customProperties.containsKey(DEVICE_NAME));

        Volume volume = getVolume(client, awsInstance, diskState
                .customProperties.get(DEVICE_NAME));

        assertEquals(
                "Additional disk capacity in diskstate is not matching the volume size in aws",
                diskState.capacityMBytes, volume.getSize() * 1024);

        assertEquals(
                "Additional disk type in diskstate is not same as the type of the volume in aws",
                diskState.customProperties.get(VOLUME_TYPE), volume.getVolumeType());

        //assert encryption status
        assertEquals("Additional disk encryption status is not matching the "
                        + "actual encryption status of the disk on aws", diskState.encrypted,
                volume.getEncrypted());

        if (diskState.customProperties.containsKey(DISK_IOPS)) {
            int requestedIops = Integer.parseInt(diskState.customProperties.get(DISK_IOPS));
            int MAX_SUPPORTED_IOPS = (int) (diskState.capacityMBytes / 1024) * 50;
            int provisionedIops = Math.min(requestedIops, MAX_SUPPORTED_IOPS);
            assertEquals("Disk speeds are not matching", provisionedIops,
                    volume.getIops().intValue());
        }
    }

    protected DiskState getDiskState(String diskLink) {
        return this.host.getServiceState(null, DiskState.class,
                UriUtils.buildUri(this.host, diskLink));
    }

    protected Volume getVolume(AmazonEC2AsyncClient client, Instance awsInstance, String deviceName) {
        InstanceBlockDeviceMapping bootDiskMapping = awsInstance.getBlockDeviceMappings().stream()
                .filter(blockDeviceMapping -> blockDeviceMapping.getDeviceName().equals(deviceName))
                .findAny()
                .orElse(null);

        //The ami used in this test is an ebs-backed AMI
        assertNotNull("Device type should be ebs type", bootDiskMapping.getEbs());

        String bootVolumeId = bootDiskMapping.getEbs().getVolumeId();
        DescribeVolumesRequest describeVolumesRequest = new DescribeVolumesRequest()
                .withVolumeIds(bootVolumeId);
        DescribeVolumesResult describeVolumesResult = client
                .describeVolumes(describeVolumesRequest);

        return describeVolumesResult.getVolumes().get(0);
    }

    protected Integer getSupportedInstanceStoreDiskSize(String instanceType) {
        if (!instanceStoreDiskSizeSupportMap.containsKey(instanceType)) {
            Integer diskSize = TestAWSSetupUtils
                    .getSupportedInstanceStoreDiskSize(this.host, instanceType,
                            this.endpointState.documentSelfLink);
            instanceStoreDiskSizeSupportMap.put(instanceType, diskSize);
        }
        return instanceStoreDiskSizeSupportMap.get(instanceType);
    }
}
