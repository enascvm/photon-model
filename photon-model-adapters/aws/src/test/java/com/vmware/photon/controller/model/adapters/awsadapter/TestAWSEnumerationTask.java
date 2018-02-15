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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static com.vmware.photon.controller.model.ComputeProperties.CUSTOM_OS_TYPE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWSResourceType.ec2_instance;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWSResourceType.ec2_net_interface;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWSResourceType.ec2_subnet;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWSResourceType.ec2_vpc;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_GATEWAY_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_VPC_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_VPC_ROUTE_TABLE_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.setQueryPageSize;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.setQueryResultLimit;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.tagResources;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.tagResourcesWithName;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.unTagResources;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.EC2_LINUX_AMI;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.EC2_WINDOWS_AMI;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.addNICDirectlyWithEC2Client;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSAuthentication;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSComputeHost;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSEndpointState;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSResourcePool;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSVMResource;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createNICDirectlyWithEC2Client;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.deleteLBsUsingLBClient;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.deleteNICDirectlyWithEC2Client;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.deleteVMsOnThisEndpoint;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.deleteVMsUsingEC2Client;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.detachNICDirectlyWithEC2Client;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.enumerateResources;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.enumerateResourcesPreserveMissing;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.getComputeByAWSId;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.getInternalTagsByType;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.getLoadBalancerByAWSId;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.getNICByAWSId;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.instanceType;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.provisionAWSEBSVMWithEC2Client;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.provisionAWSLoadBalancerWithEC2Client;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.provisionAWSVMWithEC2Client;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.provisionMachine;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.regionId;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.setAwsClientMockInfo;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.setUpTestVolume;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.setUpTestVpc;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.stopVMsUsingEC2Client;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.tearDownTestDisk;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.tearDownTestVpc;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.waitForInstancesToBeTerminated;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.waitForProvisioningToComplete;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.zoneId;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestUtils.getExecutor;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestUtils.getSubnetStates;
import static com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSLoadBalancerEnumerationAdapterService.ENABLE_LOAD_BALANCER_PROPERTY;
import static com.vmware.photon.controller.model.adapters.util.TagsUtil.newTagState;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.TAG_KEY_TYPE;
import static com.vmware.photon.controller.model.tasks.ProvisioningUtils.queryAllFactoryResources;
import static com.vmware.photon.controller.model.tasks.ProvisioningUtils.queryComputeInstances;
import static com.vmware.photon.controller.model.tasks.ProvisioningUtils.queryDocumentsAndAssertExpectedCount;
import static com.vmware.photon.controller.model.tasks.TestUtils.doPatch;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.AttachVolumeRequest;
import com.amazonaws.services.ec2.model.BlockDeviceMapping;
import com.amazonaws.services.ec2.model.CreateVolumeRequest;
import com.amazonaws.services.ec2.model.CreateVolumeResult;
import com.amazonaws.services.ec2.model.DeleteVolumeRequest;
import com.amazonaws.services.ec2.model.DetachVolumeRequest;
import com.amazonaws.services.ec2.model.EbsBlockDevice;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.VolumeType;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingAsyncClient;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.BucketTaggingConfiguration;
import com.amazonaws.services.s3.model.TagSet;

import io.netty.util.internal.StringUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.vmware.photon.controller.model.ComputeProperties.OSType;
import com.vmware.photon.controller.model.PhotonModelMetricServices;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapterapi.RegionEnumerationResponse;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWSResourceType;
import com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.AwsNicSpecs;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.adapters.util.TagsUtil;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.LifecycleState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService.LoadBalancerDescription.RouteConfiguration;
import com.vmware.photon.controller.model.resources.LoadBalancerService.LoadBalancerState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.SecurityGroupService;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;
import com.vmware.photon.controller.model.util.ClusterUtil.ServiceTypeCluster;
import com.vmware.xenon.common.BasicTestCase;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Test to enumerate instances on AWS and tear it down. The test creates VM using the Provisioning
 * task as well as directly creating instances on AWS using the EC2 client.It then invokes the AWS
 * enumeration adapter to enumerate all the resources on the AWS endpoint and validates that all the
 * updates to the local state are as expected.If the 'isMock' flag is set to true the test runs the
 * adapter in mock mode and does not actually create a VM. Minimally the accessKey and secretKey for
 * AWS must be specified to run the test.
 */
public class TestAWSEnumerationTask extends BasicTestCase {
    private static final int ZERO = 0;
    private static final int count1 = 1;
    private static final int count2 = 2;
    private static final int count3 = 3;
    private static final int count4 = 4;
    private static final int count6 = 6;
    private static final int count7 = 7;
    private static final int count8 = 8;
    private static final int internalTagsCount1 = 1;

    private static final String TEST_CASE_INITIAL = "Initial Run ";
    private static final String TEST_CASE_ADDITIONAL_VM = "Additional VM ";
    private static final String TEST_CASE_ADDITIONAL_NIC = "Additional NIC";
    private static final String TEST_CASE_REMOVED_NIC = "Removed NIC";
    private static final String TEST_CASE_PURE_UPDATE = "Only Update to existing VM.";
    private static final String TEST_CASE_STOP_VM = "Stop VM ";
    private static final String TEST_CASE_DELETE_VM = "Delete VM ";
    private static final String TEST_CASE_DELETE_VMS = "Delete multiple VMs ";
    private static final String TEST_CASE_MOCK_MODE = "Mock Mode ";
    private static final String TEST_BUCKET_NAME = "enumtest-bucket-" + UUID.randomUUID().toString();
    private static final int DEFAULT_TEST_PAGE_SIZE = 5;
    private static final String T2_MICRO_INSTANCE_TYPE = "t2.micro";
    private static final String VM_NAME = "TestAWSEnumerationTask-create";
    private static final String VM_STOPPED_NAME = "TestAWSEnumerationTask-stop";
    private static final String VM_UPDATED_NAME = "TestAWSEnumerationTask-update";
    private static final int DEFAULT_TIMOUT_SECONDS = 300;

    public static final String VM_TAG_KEY_1 = "key1";
    public static final String VM_TAG_KEY_2 = "key2";
    public static final String VM_TAG_KEY_3 = "key3";
    public static final String VM_TAG_VALUE_1 = "value1";
    public static final String VM_TAG_VALUE_2 = "value2";
    public static final String VM_TAG_VALUE_3 = "value3";

    public static final String S3_TAG_KEY_1 = "s3-enumtest-key1";
    public static final String S3_TAG_KEY_2 = "s3-enumtest-key2";
    public static final String S3_TAG_VALUE_1 = "s3-enumtest-value1";
    public static final String S3_TAG_VALUE_2 = "s3-enumtest-value2";

    public static final String INITIAL_SG_TAG = "initialSGTag";
    public static final String INITIAL_VPC_TAG = "initialVPCTag";
    public static final String INITIAL_SUBNET_TAG = "initialSubnetTag";
    public static final String INITIAL_DISK_TAG = "initialDiskTag";
    public static final String SECONDARY_SG_TAG = "secondarySGTag";
    public static final String SECONDARY_VPC_TAG = "secondaryVPCTag";
    public static final String SECONDARY_SUBNET_TAG = "secondarySubnetTag";
    public static final String SECONDARY_DISK_TAG = "secondaryDiskTag";

    public static final Boolean ENABLE_LOAD_BALANCER_ENUMERATION = Boolean.getBoolean
            (ENABLE_LOAD_BALANCER_PROPERTY);

    private ComputeState computeHost;
    private EndpointState endpointState;

    private List<String> instancesToCleanUp = new ArrayList<>();
    private String bucketToBeDeleted;
    private String nicToCleanUp = null;
    private String lbToCleanUp;
    private AmazonEC2AsyncClient client;
    private AmazonS3Client s3Client;
    private AmazonElasticLoadBalancingAsyncClient lbClient;
    public boolean isAwsClientMock = false;
    public String awsMockEndpointReference = null;

    public boolean isMock = true;
    public String accessKey = "accessKey";
    public String secretKey = "secretKey";
    public boolean useAllRegions = false;
    public int timeoutSeconds = DEFAULT_TIMOUT_SECONDS;
    //Flag to indicate if networking resources created from the test should be deleted.
    public boolean deleteResourcesFlag = false;

    private Map<String, Object> awsTestContext;
    private String vpcId;
    private String subnetId;
    private String securityGroupId;
    private AwsNicSpecs singleNicSpec;

    private BlockDeviceMapping blockDeviceMapping;
    private static final String BLOCK_DEVICE_NAME = "/dev/sdf";
    private EbsBlockDevice ebsBlockDevice;
    private String snapshotId;
    private String diskId;
    private int initialEbsDiskLinkCount;
    private String testEbsId;
    private boolean isTestBucketPatched = false;

    @Rule
    public TestName currentTestName = new TestName();

    @Before
    public void setUp() throws Throwable {
        CommandLineArgumentParser.parseFromProperties(this);

        setAwsClientMockInfo(this.isAwsClientMock, this.awsMockEndpointReference);
        // create credentials
        AuthCredentialsServiceState creds = new AuthCredentialsServiceState();
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

        TestContext s3WaitContext = new TestContext(1,  Duration.ofSeconds(30L));
        AWSUtils.getS3ClientAsync(creds, TestAWSSetupUtils.regionId, getExecutor())
                .exceptionally(t -> {
                    s3WaitContext.fail(t);
                    throw new CompletionException(t);
                })
                .thenAccept(ec2Client -> {
                    this.s3Client = ec2Client;
                    s3WaitContext.complete();
                });
        s3WaitContext.await();

        if (ENABLE_LOAD_BALANCER_ENUMERATION) {
            TestContext lbWaitContext = new TestContext(1,  Duration.ofSeconds(30L));
            AWSUtils.getAwsLoadBalancingAsyncClient(creds, TestAWSSetupUtils.regionId,
                    getExecutor())
                    .exceptionally(t -> {
                        lbWaitContext.fail(t);
                        throw new CompletionException(t);
                    })
                    .thenAccept(ec2Client -> {
                        this.lbClient = ec2Client;
                        lbWaitContext.complete();
                    });
            lbWaitContext.await();
        }

        this.awsTestContext = new HashMap<>();
        setUpTestVpc(this.client, this.awsTestContext, this.isMock);
        this.vpcId = (String) this.awsTestContext.get(TestAWSSetupUtils.VPC_KEY);
        this.subnetId = (String) this.awsTestContext.get(TestAWSSetupUtils.SUBNET_KEY);
        this.securityGroupId = (String) this.awsTestContext.get(TestAWSSetupUtils.SECURITY_GROUP_KEY);
        this.singleNicSpec = (AwsNicSpecs) this.awsTestContext.get(TestAWSSetupUtils.NIC_SPECS_KEY);

        try {
            PhotonModelServices.startServices(this.host);
            PhotonModelMetricServices.startServices(this.host);
            PhotonModelTaskServices.startServices(this.host);
            PhotonModelAdaptersRegistryAdapters.startServices(this.host);
            AWSAdaptersTestUtils.startServicesSynchronously(this.host);

            this.host.setTimeoutSeconds(this.timeoutSeconds);

            this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
        } catch (Throwable e) {
            this.host.log("Error starting up services for the test %s", e.getMessage());
            throw new Exception(e);
        }

        // create the compute host, resource pool and the VM state to be used in the test.
        initResourcePoolAndComputeHost();
    }

    @After
    public void tearDown() throws Throwable {
        if (this.testEbsId != null) {
            DetachVolumeRequest detachVolumeRequest = new DetachVolumeRequest().withVolumeId(this.testEbsId);
            this.client.detachVolume(detachVolumeRequest);
        }
        if (this.host == null) {
            return;
        }
        if (this.bucketToBeDeleted != null) {
            this.s3Client.deleteBucket(this.bucketToBeDeleted);
        }
        tearDownAwsVMs();
        if (this.deleteResourcesFlag) {
            this.awsTestContext.put(TestAWSSetupUtils.DELETE_RESOURCES_KEY,
                    TestAWSSetupUtils.DELETE_RESOURCES_KEY);
        }
        tearDownTestVpc(this.client, this.host, this.awsTestContext, this.isMock);
        this.client.shutdown();
        this.s3Client.shutdown();
        if (ENABLE_LOAD_BALANCER_ENUMERATION) {
            tearDownAwsLoadBalancer();
            this.lbClient.shutdown();
        }
        setAwsClientMockInfo(false, null);
        if (this.testEbsId != null) {
            DeleteVolumeRequest deleteVolumeRequest = new DeleteVolumeRequest().withVolumeId(this.testEbsId);
            this.client.deleteVolume(deleteVolumeRequest);
        }
    }

    // Runs the enumeration task on the AWS endpoint to list all the instances on the endpoint.
    @Test
    public void testEnumeration() throws Throwable {
        this.host.log("Running test: " + this.currentTestName.getMethodName());

        ComputeState vmState = createAWSVMResource(this.host, this.computeHost, this.endpointState,
                TestAWSSetupUtils.class, zoneId, regionId, null, this.singleNicSpec,
                this.awsTestContext);

        if (this.isMock) {
            // Just make a call to the enumeration service and make sure that the adapter patches
            // the parent with completion.
            enumerateResources(this.host, this.computeHost, this.endpointState, this.isMock,
                    TEST_CASE_MOCK_MODE);
            return;
        }

        // Overriding the page size to test the pagination logic with limited instances on AWS.
        // This is a functional test
        // so the latency numbers maybe higher from this test due to low page size.
        setQueryPageSize(DEFAULT_TEST_PAGE_SIZE);
        setQueryResultLimit(DEFAULT_TEST_PAGE_SIZE);

        // Provision a single VM . Check initial state.
        vmState = provisionMachine(this.host, vmState, this.isMock, this.instancesToCleanUp);
        queryComputeInstances(this.host, count2);
        queryDocumentsAndAssertExpectedCount(this.host, count2,
                ComputeDescriptionService.FACTORY_LINK, false);

        if (ENABLE_LOAD_BALANCER_ENUMERATION) {
            this.lbToCleanUp = provisionAWSLoadBalancerWithEC2Client(this.host, this.lbClient, null,
                    this.subnetId, this.securityGroupId, Collections.singletonList(vmState.id));
        }

        // CREATION directly on AWS
        List<String> instanceIdsToDeleteFirstTime = provisionAWSVMWithEC2Client(this.client,
                this.host, count4, T2_MICRO_INSTANCE_TYPE, this.subnetId, this.securityGroupId);
        List<String> instanceIds = provisionAWSVMWithEC2Client(this.client, this.host, count1,
                instanceType, this.subnetId, this.securityGroupId);
        instanceIdsToDeleteFirstTime.addAll(instanceIds);
        this.instancesToCleanUp.addAll(instanceIdsToDeleteFirstTime);
        waitForProvisioningToComplete(instanceIdsToDeleteFirstTime, this.host, this.client, ZERO);

        // Xenon does not know about the new instances.
        ProvisioningUtils.queryComputeInstances(this.host, count2);

        // Create S3 bucket on amazon
        Map<String, String> tags = new HashMap<>();
        tags.put(S3_TAG_KEY_1, S3_TAG_VALUE_1);
        tags.put(S3_TAG_KEY_2, S3_TAG_VALUE_2);

        createS3BucketAndTags(tags);
        this.bucketToBeDeleted = TEST_BUCKET_NAME;

        enumerateResources(this.host, this.computeHost, this.endpointState, this.isMock,
                TEST_CASE_INITIAL);

        // Get a count of how many EBS disks are attached to a VM initially.
        ComputeState csForDiskLinkValidation = getComputeStateFromId(this.instancesToCleanUp.get(0));
        this.initialEbsDiskLinkCount = csForDiskLinkValidation.diskLinks.size();

        // Create a volume to be attached to the VM later.
        CreateVolumeRequest createVolumeRequest = new CreateVolumeRequest()
                .withAvailabilityZone(csForDiskLinkValidation.zoneId)
                .withVolumeType(VolumeType.Gp2)
                .withSize(10);
        CreateVolumeResult createVolumeResult = this.client.createVolume(createVolumeRequest);
        this.testEbsId = createVolumeResult.getVolume().getVolumeId();

        // Validate if the S3 bucket is enumerated.
        validateS3Enumeration(count1, count3);
        // Validate S3 tag state count.
        validateS3TagStatesCreated();

        if (ENABLE_LOAD_BALANCER_ENUMERATION) {
            // Validate Load Balancer State
            validateLoadBalancerState(this.lbToCleanUp, vmState.documentSelfLink);
        }

        // Remove a tag from test S3 bucket.
        tags.clear();
        tags.put(S3_TAG_KEY_1, S3_TAG_VALUE_1);
        createS3BucketAndTags(tags);

        // 5 new resources should be discovered. Mapping to 2 new compute description and 5 new
        // compute states.
        // Even though the "t2.micro" is common to the VM provisioned from Xenon
        // service and the one directly provisioned on EC2, there is no Compute description
        // linking of discovered resources to user defined compute descriptions. So a new system
        // generated compute description will be created for "t2.micro"
        queryDocumentsAndAssertExpectedCount(this.host, count4,
                ComputeDescriptionService.FACTORY_LINK, false);
        queryDocumentsAndAssertExpectedCount(this.host,
                count7, ComputeService.FACTORY_LINK, false);
        queryDocumentsAndAssertExpectedCount(this.host,
                count7, DiskService.FACTORY_LINK, false);

        // Validate at least 4 availability zones were enumerated
        ProvisioningUtils.queryComputeInstancesByType(this.host, count4,
                ComputeType.ZONE.toString(), false);

        // Update Scenario : Check that the tag information is present for the VM tagged above.
        String vpCId = validateTagAndNetworkAndComputeDescriptionInformation(vmState);
        validateVPCInformation(vpCId);
        // Count should be 1 NICs per discovered VM.
        int totalNetworkInterfaceStateCount = count6 * this.singleNicSpec.numberOfNics();
        validateNetworkInterfaceCount(totalNetworkInterfaceStateCount);
        // One VPC should be discovered in the test.
        queryDocumentsAndAssertExpectedCount(this.host, count1,
                NetworkService.FACTORY_LINK, false);

        // Verify that the SecurityGroups of the newly created VM has been enumerated and exists
        // locally
        validateSecurityGroupsInformation(vmState.groupLinks);

        // Verify stop flow
        // The first instance of instanceIdsToDeleteFirstTime will be stopped.
        String instanceIdsToStop = instanceIdsToDeleteFirstTime.get(0);
        tagResourcesWithName(this.client, VM_STOPPED_NAME, instanceIdsToStop);
        // Stop one instance
        stopVMsUsingEC2Client(this.client, this.host, new ArrayList<>(Arrays.asList(
                instanceIdsToStop)));

        // Create stale resources, that later should be deleted by the enumeration
        String staleSubnetDocumentSelfLink = markFirstResourceStateAsStale(host, SubnetState.class, SubnetService.FACTORY_LINK);
        String staleNetworkDocumentSelfLink = markFirstResourceStateAsStale(host, NetworkState.class, NetworkService.FACTORY_LINK);

        // During the enumeration, if one instance is stopped, its public ip address
        // will disappear, then the corresponding link of local ComputeState's public
        // network interface and its document will be removed.
        enumerateResources(this.host, this.computeHost, this.endpointState, this.isMock,
                TEST_CASE_STOP_VM);

        // Validate that test VM still has same number of diskLinks.
        csForDiskLinkValidation = getComputeStateFromId(this.instancesToCleanUp.get(0));
        assertEquals(csForDiskLinkValidation.diskLinks.size(), this.initialEbsDiskLinkCount);

        // Attach volume to test VM.
        AttachVolumeRequest attachVolumeRequest = new AttachVolumeRequest()
                .withVolumeId(createVolumeResult.getVolume().getVolumeId())
                .withInstanceId(csForDiskLinkValidation.id)
                .withDevice("/dev/sdh");
        this.client.attachVolume(attachVolumeRequest);

        // Validate stale resources have been deleted
        validateStaleResourceStateDeletion(staleSubnetDocumentSelfLink, staleNetworkDocumentSelfLink);

        // After two enumeration cycles, validate that we did not create duplicate documents for existing
        // S3 bucket and validate that we did not add duplicate tagLink in diskState and removed the tagLink
        // for tag deleted from AWS.
        validateS3Enumeration(count1, count2);
        // Remove region from S3 bucket DiskState.
        removeS3BucketRegionFromDiskState();
        // Validate that deleted S3 tag's local state is deleted.
        validateS3TagStatesCreated();

        enumerateResources(this.host, this.computeHost, this.endpointState, this.isMock,
                TEST_CASE_ADDITIONAL_VM);

        // Check that newly attached volume got enumerated and the instance now has 1 more diskLink than before.
        csForDiskLinkValidation = getComputeStateFromId(this.instancesToCleanUp.get(0));
        assertEquals(csForDiskLinkValidation.diskLinks.size(), this.initialEbsDiskLinkCount + 1);

        // Validate that diskState of S3 bucket with null region got deleted
        validateBucketStateDeletionForNullRegion();

        // Delete the S3 bucket created in the test
        this.s3Client.deleteBucket(TEST_BUCKET_NAME);
        this.bucketToBeDeleted = null;

        // Because one public NIC and its document are removed,
        // the totalNetworkInterfaceStateCount should go down by 1
        validateRemovalOfPublicNetworkInterface(instanceIdsToStop,
                totalNetworkInterfaceStateCount - 1);

        // Provision an additional VM with a different instance type. It should re-use the
        // existing compute description created by the enumeration task above.
        List<String> instanceIdsToDeleteSecondTime = provisionAWSVMWithEC2Client(this.client,
                this.host, count1, TestAWSSetupUtils.instanceType, this.subnetId, this.securityGroupId);
        this.instancesToCleanUp.addAll(instanceIdsToDeleteSecondTime);
        waitForProvisioningToComplete(instanceIdsToDeleteSecondTime, this.host, this.client,
                ZERO);
        enumerateResources(this.host, this.computeHost, this.endpointState, this.isMock,
                TEST_CASE_ADDITIONAL_VM);

        // Validate that we do not add duplicate diskLinks after multiple enumerations.
        csForDiskLinkValidation = getComputeStateFromId(this.instancesToCleanUp.get(0));
        assertEquals(csForDiskLinkValidation.diskLinks.size(), this.initialEbsDiskLinkCount + 1);

        // Detach and delete test EBS volume.
        DetachVolumeRequest detachVolumeRequest = new DetachVolumeRequest().withVolumeId(this.testEbsId);
        this.client.detachVolume(detachVolumeRequest);

        // One additional compute state and no additional compute description should be
        // created. 1) compute host CD 2) t2.nano-system generated 3) t2.micro-system generated
        // 4) t2.micro-created from test code.
        queryDocumentsAndAssertExpectedCount(this.host, count4,
                ComputeDescriptionService.FACTORY_LINK, false);
        ServiceDocumentQueryResult computesResult1 = queryDocumentsAndAssertExpectedCount(this.host,
                count8, ComputeService.FACTORY_LINK, false);
        // validate the internal tag tor type=ec2_instance is set
        // query for the existing internal tag state for type=ec2_instance.
        // There should be only one internal tag.
        validateTagInEntity(computesResult1, ComputeState.class, ec2_instance.toString());

        ServiceDocumentQueryResult networkInterfaceResult = queryDocumentsAndAssertExpectedCount(
                this.host, totalNetworkInterfaceStateCount - 1,
                NetworkInterfaceService.FACTORY_LINK, false);
        validateTagInEntity(networkInterfaceResult, NetworkInterfaceState.class,
                ec2_net_interface.toString());

        ServiceDocumentQueryResult networkStateResult = queryDocumentsAndAssertExpectedCount(
                this.host, count1, NetworkService.FACTORY_LINK, false);
        validateTagInEntity(networkStateResult, NetworkState.class, ec2_vpc.toString());

        ServiceDocumentQueryResult subnetStateResult = queryDocumentsAndAssertExpectedCount(
                this.host, count1, SubnetService.FACTORY_LINK, false);
        // TODO Remove. This is to help debug an intermittent test failure.
        host.log(Level.INFO, "The subnet result state that I am working with is "
                + Utils.toJsonHtml(subnetStateResult));
        validateTagInEntity(subnetStateResult, SubnetState.class, ec2_subnet.toString());
        queryDocumentsAndAssertExpectedCount(this.host,
                count8, DiskService.FACTORY_LINK, false);

        // Verify Deletion flow
        // Delete 5 VMs spawned above of type T2_NANO
        deleteVMsUsingEC2Client(this.client, this.host, instanceIdsToDeleteFirstTime);
        enumerateResources(this.host, this.computeHost, this.endpointState, this.isMock,
                TEST_CASE_DELETE_VMS);
        // Counts should go down. 5 compute states and 5 disk states.
        ServiceDocumentQueryResult computesResult2 = queryDocumentsAndAssertExpectedCount(this.host,
                count3, ComputeService.FACTORY_LINK, false);
        queryDocumentsAndAssertExpectedCount(this.host,
                count3, DiskService.FACTORY_LINK, false);

        // Validate that detached test EBS is removed from diskLinks of test instance.
        csForDiskLinkValidation = getComputeStateFromId(this.instancesToCleanUp.get(0));
        assertEquals(csForDiskLinkValidation.diskLinks.size(), this.initialEbsDiskLinkCount);

        // validate the internal tag tor type=ec2_instance is set
        // query for the existing internal tag state for type=ec2_instance.
        // There should be only one internal tag.
        validateTagInEntity(computesResult2, ComputeState.class, ec2_instance.toString());

        // Delete 1 VMs spawned above of type T2_Micro
        deleteVMsUsingEC2Client(this.client, this.host, instanceIdsToDeleteSecondTime);
        enumerateResources(this.host, this.computeHost, this.endpointState, this.isMock,
                TEST_CASE_DELETE_VM);
        // Compute state and disk state count should go down by 1
        ServiceDocumentQueryResult computesResult3 = queryDocumentsAndAssertExpectedCount(this.host,
                count2, ComputeService.FACTORY_LINK, false);
        queryDocumentsAndAssertExpectedCount(this.host,
                count2, DiskService.FACTORY_LINK, false);

        // validate the internal tag tor type=ec2_instance is set
        // query for the existing internal tag state for type=ec2_instance.
        // There should be only one internal tag.
        validateTagInEntity(computesResult3, ComputeState.class, ec2_instance.toString());
        // Validate that the document for the deleted S3 bucket is deleted after enumeration.
        validateS3Enumeration(ZERO, ZERO);

        // Delete test EBS volume.
        DeleteVolumeRequest deleteVolumeRequest = new DeleteVolumeRequest().withVolumeId(this.testEbsId);
        this.client.deleteVolume(deleteVolumeRequest);
        this.testEbsId = null;
    }

    private ComputeState getComputeStateFromId(String instanceId) {
        Query query = Query.Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_ID, instanceId)
                .build();

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .setQuery(query)
                .addOption(QueryOption.EXPAND_CONTENT)
                .build();

        Operation queryCompute = Operation.createPost(this.host, ServiceUriPaths.CORE_LOCAL_QUERY_TASKS)
                .setReferer(this.host.getUri())
                .setBody(queryTask);

        Operation compute = this.host.waitForResponse(queryCompute);

        QueryTask response = compute.getBody(QueryTask.class);

        ComputeState computeState = Utils.fromJson(response.results.documents.entrySet()
                .iterator().next().getValue(), ComputeState.class);

        return computeState;
    }

    /**
     * Validates that the given internal tag is present on the passed in entity.
     */
    private void validateTagInEntity(ServiceDocumentQueryResult queryResult, Class<?> documentKind,
            String internalTagType) {
        ServiceDocumentQueryResult tagsResult;
        String tagLink;
        tagsResult = getInternalTagsByType(this.host, internalTagType);
        assertEquals(count1, tagsResult.documentLinks.size());
        tagLink = tagsResult.documentLinks.get(0);
        for (Map.Entry<String, Object> resourceMap : queryResult.documents.entrySet()) {
            if (documentKind == ComputeState.class) {
                ComputeState compute = Utils
                        .fromJson(resourceMap.getValue(), ComputeState.class);
                if (!compute.type.equals(ComputeType.ZONE)
                        && !compute.type.equals(ComputeType.ENDPOINT_HOST)) {
                    assertTrue(compute.tagLinks.contains(tagLink));
                }
            } else {
                ResourceState resourceState = Utils.fromJson(resourceMap.getValue(),
                        ResourceState.class);
                if (resourceState.tagLinks != null) {
                    assertTrue(resourceState.tagLinks.contains(tagLink));
                }
            }
        }
    }

    // Runs the enumeration task after a new nic has been added to a CS and then after it has been
    // removed
    @Test
    public void testEnumerationUpdateNICs() throws Throwable {
        if (this.isMock) {
            return;
        }

        this.host.log("Running test: " + this.currentTestName.getMethodName());

        ComputeState vmState = createAWSVMResource(this.host, this.computeHost, this.endpointState,
                TestAWSSetupUtils.class, zoneId, regionId, null, this.singleNicSpec,
                this.awsTestContext);

        // Overriding the page size to test the pagination logic with limited instances on AWS.
        // This is a functional test
        // so the latency numbers maybe higher from this test due to low page size.
        setQueryPageSize(DEFAULT_TEST_PAGE_SIZE);
        setQueryResultLimit(DEFAULT_TEST_PAGE_SIZE);

        // Provision a single VM . Check initial state.
        vmState = provisionMachine(this.host, vmState, this.isMock, this.instancesToCleanUp);

        // Run enumeration to discover the new VM
        enumerateResources(this.host, this.computeHost, this.endpointState, this.isMock,
                TEST_CASE_INITIAL);

        int numberOfNICsBeforeAdding = vmState.networkInterfaceLinks.size();

        String newNICId = createNICDirectlyWithEC2Client(this.client, this.host, this.subnetId);
        this.nicToCleanUp = newNICId;
        String newAWSNicAttachmentId = addNICDirectlyWithEC2Client(vmState, this.client, this.host, newNICId);

        // Run enumeration to discover the changes in the NICs in the new VM
        enumerateResources(this.host, this.computeHost, this.endpointState, this.isMock,
                TEST_CASE_ADDITIONAL_NIC);

        // validate NICs
        ComputeState updatedComputeState = getComputeByAWSId(this.host, vmState.id);
        //New NIC State link should have been added to the CS
        assertEquals(numberOfNICsBeforeAdding + 1, updatedComputeState.networkInterfaceLinks.size());

        NetworkInterfaceState addedNetworkInterfaceState = getNICByAWSId(this.host, newNICId);
        // Assert that the network interface state has the right internal tag link
        assertTrue(addedNetworkInterfaceState.tagLinks.contains(TagsUtil.newTagState(TAG_KEY_TYPE,
                AWSResourceType.ec2_net_interface.toString(), false,
                this.endpointState.tenantLinks).documentSelfLink));
        //NIC State with the new ID should have been created
        assertNotNull(addedNetworkInterfaceState);

        detachNICDirectlyWithEC2Client(vmState.id, newAWSNicAttachmentId, newNICId, this.client, this.host);

        // Run again enumeration to discover the changes in the NICs in the new VM
        enumerateResources(this.host, this.computeHost, this.endpointState, this.isMock,
                TEST_CASE_REMOVED_NIC);

        // validate NICs
        ComputeState updatedAgainComputeState = getComputeByAWSId(this.host, vmState.id);
        //The link to the removed NIC State should have been removed
        assertEquals(numberOfNICsBeforeAdding, updatedAgainComputeState.networkInterfaceLinks.size());

        NetworkInterfaceState removedNetworkInterfaceState = getNICByAWSId(this.host, newNICId);
        //NIC State with this ID should have been removed
        assertEquals(null, removedNetworkInterfaceState);
    }

    @Test
    public void testEnumerationPreserveLocalStates() throws Throwable {

        this.host.log("Running test: " + this.currentTestName.getMethodName());

        ComputeState vmState = createAWSVMResource(this.host, this.computeHost, this.endpointState,
                TestAWSSetupUtils.class, zoneId, regionId, null, this.singleNicSpec,
                this.awsTestContext);

        if (this.isMock) {
            // Just make a call to the enumeration service and make sure that the adapter patches
            // the parent with completion.
            enumerateResourcesPreserveMissing(this.host, this.computeHost, this.endpointState, this.isMock,
                    TEST_CASE_MOCK_MODE);
            return;
        }

        // Overriding the page size to test the pagination logic with limited instances on AWS.
        // This is a functional test
        // so the latency numbers maybe higher from this test due to low page size.
        setQueryPageSize(DEFAULT_TEST_PAGE_SIZE);
        setQueryResultLimit(DEFAULT_TEST_PAGE_SIZE);

        // Provision a single VM . Check initial state.
        vmState = provisionMachine(this.host, vmState, this.isMock, this.instancesToCleanUp);
        queryComputeInstances(this.host, count2);
        queryDocumentsAndAssertExpectedCount(this.host, count2,
                ComputeDescriptionService.FACTORY_LINK, false);

        // CREATION directly on AWS
        List<String> instanceIdsToDelete = provisionAWSVMWithEC2Client(this.client,
                this.host, count4, T2_MICRO_INSTANCE_TYPE, this.subnetId, this.securityGroupId);
        List<String> instanceIds = provisionAWSVMWithEC2Client(this.client, this.host, count1,
                instanceType, this.subnetId, this.securityGroupId);
        instanceIdsToDelete.addAll(instanceIds);
        this.instancesToCleanUp.addAll(instanceIdsToDelete);
        waitForProvisioningToComplete(instanceIdsToDelete, this.host, this.client, ZERO);

        // Xenon does not know about the new instances.
        ProvisioningUtils.queryComputeInstances(this.host, count2);

        enumerateResourcesPreserveMissing(this.host, this.computeHost, this.endpointState, this.isMock,
                TEST_CASE_INITIAL);
        // 5 new resources should be discovered. Mapping to 2 new compute description and 5 new
        // compute states.
        // Even though the "t2.micro" is common to the VM provisioned from Xenon
        // service and the one directly provisioned on EC2, there is no Compute description
        // linking of discovered resources to user defined compute descriptions. So a new system
        // generated compute description will be created for "t2.micro"
        queryDocumentsAndAssertExpectedCount(this.host,
                count4,
                ComputeDescriptionService.FACTORY_LINK, false);
        queryDocumentsAndAssertExpectedCount(this.host,
                count7, ComputeService.FACTORY_LINK, false);

        // Verify Deletion flow
        // Delete 5 VMs spawned above of type T2_NANO
        deleteVMsUsingEC2Client(this.client, this.host, instanceIdsToDelete);
        enumerateResourcesPreserveMissing(this.host, this.computeHost, this.endpointState, this.isMock,
                TEST_CASE_DELETE_VMS);
        // Counts should go down 5 compute states.
        ServiceDocumentQueryResult queryResult = queryDocumentsAndAssertExpectedCount(this.host,
                count7, ComputeService.FACTORY_LINK, false);

        List<ComputeState> localInstances = queryResult.documents.values().stream()
                .map(d -> Utils.fromJson(d, ComputeState.class))
                .filter(c -> instanceIdsToDelete.contains(c.id)).collect(Collectors.toList());

        assertEquals(instanceIdsToDelete.size(), localInstances.size());
        for (ComputeState c : localInstances) {
            assertEquals(LifecycleState.RETIRED, c.lifecycleState);
        }
    }

    @Test
    public void testOSTypeEnumerationAndDisplayNameEnumeration() throws Throwable {
        if (this.isMock) {
            return;
        }

        this.host.log("Running test: " + this.currentTestName.getMethodName());

        String linuxVMId = provisionAWSVMWithEC2Client(this.host, this.client, EC2_LINUX_AMI,
                this.subnetId, this.securityGroupId);
        this.instancesToCleanUp.add(linuxVMId);

        String windowsVMId = provisionAWSVMWithEC2Client(this.host, this.client, EC2_WINDOWS_AMI,
                this.subnetId, this.securityGroupId);
        this.instancesToCleanUp.add(windowsVMId);

        waitForProvisioningToComplete(this.instancesToCleanUp, this.host, this.client, ZERO);

        // Tag the first VM with a name
        tagResourcesWithName(this.client, VM_NAME, linuxVMId);

        enumerateResources(this.host, this.computeHost, this.endpointState, this.isMock,
                TEST_CASE_INITIAL);

        validateComputeName(linuxVMId, VM_NAME);

        // Validate this instance's host name
        validateHostName(linuxVMId);

        // Update the tag on the VM already known to the system
        tagResourcesWithName(this.client, VM_UPDATED_NAME, linuxVMId);

        enumerateResources(this.host, this.computeHost, this.endpointState, this.isMock,
                TEST_CASE_PURE_UPDATE);

        validateComputeName(linuxVMId, VM_UPDATED_NAME);

        // Validate this instance's host name after update
        validateHostName(linuxVMId);

        ComputeState linuxCompute = getComputeByAWSId(this.host, linuxVMId);
        assertNotNull(linuxCompute);
        assertEquals(ComputeType.VM_GUEST, linuxCompute.type);
        assertEquals(ComputeDescription.ENVIRONMENT_NAME_AWS, linuxCompute.environmentName);
        assertNotNull(linuxCompute.customProperties);
        assertNotNull(linuxCompute.creationTimeMicros);
        String linuxOSType = linuxCompute.customProperties.get(CUSTOM_OS_TYPE);
        assertNotNull(linuxOSType);
        assertEquals(OSType.LINUX.toString(), linuxOSType);

        ComputeState winCompute = getComputeByAWSId(this.host, windowsVMId);
        assertNotNull(winCompute);
        assertEquals(ComputeType.VM_GUEST, winCompute.type);
        assertEquals(ComputeDescription.ENVIRONMENT_NAME_AWS, winCompute.environmentName);
        assertNotNull(winCompute.customProperties);
        assertNotNull(winCompute.creationTimeMicros);
        String winOSType = winCompute.customProperties.get(CUSTOM_OS_TYPE);
        assertNotNull(winOSType);
        assertEquals(OSType.WINDOWS.toString(), winOSType);
    }

    @Test
    public void testTagEnumeration() throws Throwable {
        if (this.isMock) {
            return;
        }
        setUpTestVolume(this.host, this.client, this.awsTestContext, this.isMock);
        this.snapshotId = (String) this.awsTestContext.get(TestAWSSetupUtils.SNAPSHOT_KEY);
        this.ebsBlockDevice = new EbsBlockDevice().withSnapshotId(this.snapshotId);
        this.blockDeviceMapping = new BlockDeviceMapping().withDeviceName(BLOCK_DEVICE_NAME)
                .withEbs(this.ebsBlockDevice);
        this.diskId = (String) this.awsTestContext.get(TestAWSSetupUtils.DISK_KEY);

        this.host.log("Running test: " + this.currentTestName.getMethodName());

        // VM tags
        Tag tag1 = new Tag(VM_TAG_KEY_1, VM_TAG_VALUE_1);
        Tag tag2 = new Tag(VM_TAG_KEY_2, VM_TAG_VALUE_2);
        Tag tag3 = new Tag(VM_TAG_KEY_3, VM_TAG_VALUE_3);
        List<Tag> vmTags = Arrays.asList(tag1, tag2, tag3);
        // SG tag
        List<Tag> sgTags = new ArrayList<>();
        sgTags.add(new Tag(INITIAL_SG_TAG, INITIAL_SG_TAG));
        // Network tag
        List<Tag> networkTags = new ArrayList<>();
        networkTags.add(new Tag(INITIAL_VPC_TAG, INITIAL_VPC_TAG));
        // Subnet tag
        List<Tag> subnetTags = new ArrayList<>();
        subnetTags.add(new Tag(INITIAL_SUBNET_TAG, INITIAL_SUBNET_TAG));
        // Disk tag
        List<Tag> diskTags = new ArrayList<>();
        diskTags.add(new Tag(INITIAL_DISK_TAG, INITIAL_DISK_TAG));

        try {
            String linuxVMId1 = provisionAWSEBSVMWithEC2Client(this.host, this.client, EC2_LINUX_AMI,
                    this.subnetId, this.securityGroupId, this.blockDeviceMapping);
            this.instancesToCleanUp.add(linuxVMId1);
            waitForProvisioningToComplete(this.instancesToCleanUp, this.host, this.client, ZERO);

            // Tag the first VM with a name and add some additional tags
            tagResourcesWithName(this.client, VM_NAME, linuxVMId1);

            List<Tag> linuxVMId1Tags = Arrays.asList(tag1, tag2);
            // tag vm, default SG, VPC, Subnet and Disk
            tagResources(this.client, linuxVMId1Tags, linuxVMId1);
            tagResources(this.client, sgTags, this.securityGroupId);
            tagResources(this.client, networkTags, this.vpcId);
            tagResources(this.client, subnetTags, this.subnetId);
            tagResources(this.client, diskTags, this.diskId);

            enumerateResources(this.host, this.computeHost, this.endpointState, this.isMock,
                    TEST_CASE_INITIAL);

            String linuxVMId2 = provisionAWSEBSVMWithEC2Client(this.host, this.client, EC2_LINUX_AMI,
                    this.subnetId, this.securityGroupId, this.blockDeviceMapping);
            this.instancesToCleanUp.add(linuxVMId2);
            waitForProvisioningToComplete(this.instancesToCleanUp, this.host, this.client, ZERO);

            // Name the second VM and add some tags
            tagResourcesWithName(this.client, VM_UPDATED_NAME, linuxVMId2);
            List<Tag> linuxVMId2Tags = Arrays.asList(tag2, tag3);
            tagResources(this.client, linuxVMId2Tags, linuxVMId2);

            // Un-tag the resources
            unTagResources(this.client, sgTags, this.securityGroupId);
            unTagResources(this.client, networkTags, this.vpcId);
            unTagResources(this.client, subnetTags, this.subnetId);
            unTagResources(this.client, diskTags, this.diskId);

            // re-init tag arrays
            sgTags = new ArrayList<>();
            networkTags = new ArrayList<>();
            subnetTags = new ArrayList<>();
            diskTags = new ArrayList<>();

            // new key-value set remotely should result in a new tag state created locally
            // and a new tag link added to the SecurityGroupState, NetworkState, SubnetState and
            // DiskState
            sgTags.add(new Tag(SECONDARY_SG_TAG, SECONDARY_SG_TAG));
            networkTags.add(new Tag(SECONDARY_VPC_TAG, SECONDARY_VPC_TAG));
            subnetTags.add(new Tag(SECONDARY_SUBNET_TAG, SECONDARY_SUBNET_TAG));
            diskTags.add(new Tag(SECONDARY_DISK_TAG, SECONDARY_DISK_TAG));

            // tag again default SG, VPC, Subnet and Disk
            tagResources(this.client, diskTags, this.diskId);
            tagResources(this.client, sgTags, this.securityGroupId);
            tagResources(this.client, networkTags, this.vpcId);
            tagResources(this.client, subnetTags, this.subnetId);

            enumerateResources(this.host, this.computeHost, this.endpointState, this.isMock,
                    TEST_CASE_INITIAL);

            validateComputeName(linuxVMId1, VM_NAME);
            validateComputeName(linuxVMId2, VM_UPDATED_NAME);

            // Validate tag states number
            int allTagsNumber = vmTags.size() + sgTags.size() + networkTags.size()
                    + subnetTags.size() + diskTags.size();
            queryDocumentsAndAssertExpectedCount(
                    this.host, allTagsNumber, TagService.FACTORY_LINK, false);

            ServiceDocumentQueryResult  serviceDocumentQueryResult = queryAllFactoryResources(this.host,
                    TagService.FACTORY_LINK);
            Map<String, TagState> tagsMap = new HashMap<>();
            for (Entry<String, Object> entry : serviceDocumentQueryResult.documents.entrySet()) {
                tagsMap.put(entry.getKey(), Utils.fromJson(entry.getValue(), TagState.class));
            }

            // validate security group tags
            Map<String, SecurityGroupState> allSecurityGroupStatesMap =
                    ProvisioningUtils.<SecurityGroupState> getResourceStates(this.host,
                            SecurityGroupService.FACTORY_LINK, SecurityGroupState.class);
            SecurityGroupState defaultSgState = allSecurityGroupStatesMap.get(this.securityGroupId);
            // ensure one link is deleted and one new is added to the sg state. One additional
            // link is an internal tag.
            assertNotNull(defaultSgState.tagLinks);
            assertEquals("Wrong number of security-group tag links found.", 1 + internalTagsCount1, defaultSgState.tagLinks.size());

            // validate vpc tags
            Map<String, NetworkState> allNetworkStatesMap =
                    ProvisioningUtils.<NetworkState> getResourceStates(this.host,
                            NetworkService.FACTORY_LINK, NetworkState.class);
            NetworkState defaultNetworkState = allNetworkStatesMap.get(this.vpcId);

            // ensure one link is deleted and one new is added to the network state. One additional
            // link is an internal tag.
            assertEquals("Wrong number of network tag links found.", 1 + internalTagsCount1,
                    defaultNetworkState.tagLinks.size());

            // validate subnet tags
            Map<String, SubnetState> allSubnetStatesMap =
                    ProvisioningUtils.<SubnetState> getResourceStates(this.host,
                            SubnetService.FACTORY_LINK, SubnetState.class);
            SubnetState defaultSubnetState = allSubnetStatesMap.get(this.subnetId);

            // ensure one link is deleted and one new is added to the subnet state. One additional
            // link is an internal tag.
            assertEquals("Wrong number of subnet tag links found.", 1 + internalTagsCount1,
                    defaultSubnetState.tagLinks.size());

            // validate disk tags
            Map<String, DiskState> allDiskStatesMap =
                    ProvisioningUtils.<DiskState> getResourceStates(this.host,
                            DiskService.FACTORY_LINK, DiskState.class);
            DiskState defaultDiskState = allDiskStatesMap.get(this.diskId);
            // ensure one link is deleted and one new is added to the disk state
            assertEquals("Wrong number of disk tag links found.", 1 + internalTagsCount1,
                    defaultDiskState.tagLinks.size());
            // ensure EBS disk has an internal type tag set
            assertTrue(defaultDiskState.tagLinks.contains(TagsUtil.newTagState(TAG_KEY_TYPE,
                    AWSResourceType.ebs_block.toString(), false,
                    this.endpointState.tenantLinks).documentSelfLink));

            // validate vm tags
            Map<Tag, String> vmTagLinks = new HashMap<>();
            for (Tag tag : vmTags) {
                for (TagState tagState : tagsMap.values()) {
                    if (tagState.key.equals(tag.getKey())) {
                        vmTagLinks.put(tag, tagState.documentSelfLink);
                    }
                }
            }

            ComputeState linuxVMId1ComputeState = getComputeByAWSId(this.host, linuxVMId1);
            // compute has 2 remote tags + 1 local tag
            assertEquals(linuxVMId1Tags.size() + internalTagsCount1,
                    linuxVMId1ComputeState.tagLinks.size());
            for (Tag tag : linuxVMId1Tags) {
                assertTrue(linuxVMId1ComputeState.tagLinks.contains(vmTagLinks.get(tag)));
            }

            ComputeState linuxVMId2ComputeState = getComputeByAWSId(this.host, linuxVMId2);
            assertEquals(linuxVMId2Tags.size() + internalTagsCount1,
                    linuxVMId2ComputeState.tagLinks.size());
            for (Tag tag : linuxVMId2Tags) {
                assertTrue(linuxVMId2ComputeState.tagLinks.contains(vmTagLinks.get(tag)));
            }
        } catch (Throwable t) {
            this.host.log("Exception occurred during test execution: %s", t.getMessage());
            if (t instanceof AssertionError) {
                fail("Assert exception occurred during test execution: " + t.getMessage());
            }
        } finally {
            // un-tag default SG
            unTagResources(this.client, sgTags, this.securityGroupId);
            // un-tag default VPC
            unTagResources(this.client, networkTags, this.vpcId);
            // un-tag default Subnet
            unTagResources(this.client, subnetTags, this.subnetId);
            // un-tag default Disk
            unTagResources(this.client, diskTags, this.diskId);
            tearDownTestDisk(this.client, this.host, this.awsTestContext, this.isMock);
        }
    }

    @Test
    public void testGetAvailableRegions() {
        URI uri = UriUtils.buildUri(
                ServiceHost.LOCAL_HOST,
                host.getPort(),
                UriPaths.AdapterTypePath.REGION_ENUMERATION_ADAPTER.adapterLink(
                        PhotonModelConstants.EndpointType.aws.toString().toLowerCase()), null);

        Operation post = Operation.createPost(uri);
        post.setBody(new AuthCredentialsServiceState());

        Operation operation = host.getTestRequestSender().sendAndWait(post);
        RegionEnumerationResponse result = operation.getBody(RegionEnumerationResponse.class);

        assertEquals(Regions.values().length, result.regions.size());
    }

    private void createS3BucketAndTags(Map<String, String> tags) {
        this.s3Client.createBucket(TEST_BUCKET_NAME);

        TagSet tagSet = new TagSet(tags);
        BucketTaggingConfiguration bucketTaggingConfiguration = new
                BucketTaggingConfiguration(Collections.singletonList(tagSet));

        this.s3Client.setBucketTaggingConfiguration(TEST_BUCKET_NAME, bucketTaggingConfiguration);
    }

    // Validate S3 bucket enumeration by querying DiskState and comparing result to expected number of documents
    // and validate the size of tagLinks.
    private void validateS3Enumeration(int expectedDiskCount, int expectedTagsCount) {
        Query s3Query = QueryTask.Query.Builder.create()
                .addKindFieldClause(DiskState.class)
                .addFieldClause(DiskState.FIELD_NAME_NAME, TEST_BUCKET_NAME)
                .build();

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .setQuery(s3Query)
                .addOption(QueryOption.EXPAND_CONTENT)
                .build();

        Operation s3QueryOp = QueryUtils.createQueryTaskOperation(this.host, queryTask,
                ServiceTypeCluster.INVENTORY_SERVICE).setReferer(this.host.getUri());

        Operation s3QueryResponse = this.host.waitForResponse(s3QueryOp);

        QueryTask response = s3QueryResponse.getBody(QueryTask.class);

        if (expectedDiskCount > 0) {
            DiskState diskState = Utils.fromJson(response.results.documents
                    .get(response.results.documentLinks.get(0)), DiskState.class);
            assertEquals(expectedTagsCount, diskState.tagLinks.size());
        }

        DiskState diskState = Utils.fromJson(response.results.documents
                .get(response.results.documentLinks.get(0)), DiskState.class);

        assertEquals(expectedDiskCount, diskState.endpointLinks.size());
    }

    private void validateS3TagStatesCreated() {
        List<String> tags = new ArrayList<>();
        tags.add(S3_TAG_KEY_1);
        tags.add(S3_TAG_KEY_2);

        Query s3TagsQuery = QueryTask.Query.Builder.create()
                .addKindFieldClause(TagState.class)
                .addInClause(TagState.FIELD_NAME_KEY, tags)
                .build();

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .setQuery(s3TagsQuery)
                .build();

        Operation s3QueryOp = QueryUtils.createQueryTaskOperation(this.host, queryTask,
                ServiceTypeCluster.INVENTORY_SERVICE).setReferer(this.host.getUri());

        Operation s3QueryResponse = this.host.waitForResponse(s3QueryOp);

        QueryTask response = s3QueryResponse.getBody(QueryTask.class);

        assertEquals(2, response.results.documentLinks.size());

        // Validate the internal type tag for S3 got created
        Operation internalTagOp = Operation.createGet(this.host, TagsUtil.newTagState(TAG_KEY_TYPE,
                AWSResourceType.s3_bucket.toString(), false,
                this.endpointState.tenantLinks).documentSelfLink)
                .setReferer(this.host.getUri());

        Operation internalTagOpResponse = this.host.waitForResponse(internalTagOp);

        assertEquals(Operation.STATUS_CODE_OK, internalTagOpResponse.getStatusCode());
    }

    private void removeS3BucketRegionFromDiskState() {
        Query query = Query.Builder.create()
                .addKindFieldClause(DiskState.class)
                .addFieldClause(DiskState.FIELD_NAME_ID, TEST_BUCKET_NAME)
                .build();

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .setQuery(query)
                .addOption(QueryOption.EXPAND_CONTENT)
                .build();

        Operation getTestBucketState = QueryUtils.createQueryTaskOperation(this.host, queryTask,
                ServiceTypeCluster.INVENTORY_SERVICE).setReferer(this.host.getUri());

        Operation response = this.host.waitForResponse(getTestBucketState);

        QueryTask queryTaskResponse = response.getBody(QueryTask.class);

        DiskState testBucketDiskState = Utils.fromJson(queryTaskResponse.results.documents.get(queryTaskResponse
                .results.documentLinks.get(0)), DiskState.class);

        testBucketDiskState.regionId = null;

        Operation setNullRegionOp = Operation.createPatch(this.host, testBucketDiskState.documentSelfLink)
                .setBody(testBucketDiskState).setReferer(this.host.getUri());

        response = this.host.waitForResponse(setNullRegionOp);

        //TODO : This test is broken - Setting the regionId to null does not do anything to the underlying state
        if (response.hasPragmaDirective(Operation.PRAGMA_DIRECTIVE_STATE_NOT_MODIFIED)) {
            this.isTestBucketPatched = false;
        } else {
            this.isTestBucketPatched = true;
        }
    }

    private void validateBucketStateDeletionForNullRegion() {
        if (!this.isTestBucketPatched) {
            this.host.log(Level.SEVERE, "Failure patching null region to test bucket diskState");
            return;
        }

        Query query = Query.Builder.create()
                .addKindFieldClause(DiskState.class)
                .addFieldClause(DiskState.FIELD_NAME_ID, TEST_BUCKET_NAME)
                .build();

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .setQuery(query)
                .build();

        Operation getTestBucketState = QueryUtils.createQueryTaskOperation(this.host, queryTask,
                ServiceTypeCluster.INVENTORY_SERVICE).setReferer(this.host.getUri());

        Operation response = this.host.waitForResponse(getTestBucketState);

        QueryTask queryTaskResponse = response.getBody(QueryTask.class);

        assertTrue(queryTaskResponse.results.documentLinks.size() == 0);
    }

    private void validateLoadBalancerState(String lbName, String computeLink) throws Throwable {
        LoadBalancerState loadBalancerState = getLoadBalancerByAWSId(this.host, lbName);

        assertEquals(count1, loadBalancerState.computeLinks.size());
        assertEquals(count1, loadBalancerState.securityGroupLinks.size());
        assertEquals(count1, loadBalancerState.subnetLinks.size());
        assertEquals(computeLink, loadBalancerState.computeLinks.iterator().next());
        assertNotNull(loadBalancerState.routes);
        assertEquals(count1, loadBalancerState.routes.size());

        RouteConfiguration route = loadBalancerState.routes.iterator().next();
        assertNotNull(route.healthCheckConfiguration);
        assertEquals("80", route.port);
        assertEquals("80", route.instancePort);
        assertEquals("HTTP", route.instanceProtocol);
        assertEquals("HTTP", route.protocol);
    }

    /**
     * Verifies if the tag information exists for a given resource. And that private and public IP
     * addresses are mapped to separate NICs.Also, checks that the compute description mapping is
     * not changed in an updated scenario. Currently, this method is being invoked for a VM
     * provisioned from Xenon, so the check is to make sure that during discovery it is not
     * re-mapped to a system generated compute description.
     *
     * @throws Throwable
     */
    private String validateTagAndNetworkAndComputeDescriptionInformation(ComputeState computeState)
            throws Throwable {
        if (this.isAwsClientMock) {
            return null;
        }

        ComputeState taggedComputeState = getComputeByAWSId(this.host, computeState.id);

        assertEquals(taggedComputeState.descriptionLink, computeState.descriptionLink);
        assertTrue(taggedComputeState.networkInterfaceLinks != null);
        assertEquals(this.singleNicSpec.numberOfNics(),
                taggedComputeState.networkInterfaceLinks.size());

        List<URI> networkLinkURIs = new ArrayList<>();
        for (int i = 0; i < taggedComputeState.networkInterfaceLinks.size(); i++) {
            networkLinkURIs.add(UriUtils.buildUri(this.host,
                    taggedComputeState.networkInterfaceLinks.get(i)));
        }

        // Assert that both the public and private IP addresses have been mapped to separated NICs
        Map<URI, NetworkInterfaceState> NICMap = this.host
                .getServiceState(null, NetworkInterfaceState.class, networkLinkURIs);
        for (URI uri : networkLinkURIs) {
            assertNotNull(NICMap.get(uri).address);
        }

        // get the VPC information for the provisioned VM
        assertTrue(taggedComputeState.customProperties.get(AWS_VPC_ID) != null);
        return taggedComputeState.customProperties.get(AWS_VPC_ID);

    }

    /**
     * Validates the tag information on a compute state matches an expected virtual machine name.
     */
    private ComputeState validateComputeName(String awsId, String vmName)
            throws Throwable {
        if (this.isAwsClientMock) {
            return null;
        }

        ComputeState computeState = getComputeByAWSId(this.host, awsId);

        // verify conversion from AWS_TAGS to CUSTOM_DISPLAY_NAME
        String tagNameValue = computeState.name;
        assertNotNull("'displayName' property should be present", tagNameValue);
        assertEquals(vmName, tagNameValue);

        return computeState;
    }

    /**
     * Validates that the VPC information discovered from AWS has all the desired set of fields and
     * the association between a compute state and a network state is established correctly.
     *
     * @throws Throwable
     */
    private void validateVPCInformation(String vpCId) throws Throwable {
        if (this.isAwsClientMock) {
            return;
        }

        // Get the network state that maps to this VPCID. Right now the id field of the network
        // state is set to the VPC ID, so querying the network state based on that.

        Map<String, NetworkState> networkStateMap =
                ProvisioningUtils.<NetworkState> getResourceStates(this.host,
                        NetworkService.FACTORY_LINK, NetworkState.class);
        assertNotNull(networkStateMap);
        NetworkState networkState = networkStateMap.get(vpCId);
        // The network state for the VPC id of the VM should not be null
        assertNotNull(networkState);
        assertNotNull(networkState.subnetCIDR);
        assertNotNull(networkState.instanceAdapterReference);
        // This is assuming that the internet gateway is attached to the VPC by default
        assertNotNull(networkState.customProperties.get(AWS_GATEWAY_ID));
        assertNotNull(networkState.customProperties.get(AWS_VPC_ROUTE_TABLE_ID));
        assertNotNull(networkState.tagLinks);
        assertTrue(networkState.tagLinks.size() > 0);
        assertTrue(networkState.tagLinks.contains(TagsUtil.newTagState(TAG_KEY_TYPE,
                AWSResourceType.ec2_vpc.toString(), false,
                this.endpointState.tenantLinks).documentSelfLink));

        List<SubnetState> subnetStates = getSubnetStates(this.host, networkState);
        assertFalse(subnetStates.isEmpty());
        subnetStates.stream().forEach(subnetState -> {
            assertNotNull(subnetState.subnetCIDR);
            assertNotNull(subnetState.zoneId);
            assertNotNull(subnetState.tagLinks);
            assertTrue(subnetState.tagLinks.size() > 0);
            assertTrue(subnetState.tagLinks.contains(TagsUtil.newTagState(TAG_KEY_TYPE,
                    AWSResourceType.ec2_subnet.toString(), false,
                    this.endpointState.tenantLinks).documentSelfLink));
        });
    }

    /**
     * From all the particular resource states, gets the first one, and patch it with an id, which
     * do not correspond to any actually existing resource
     * on the cloud, in order to test that this resource state will be deleted after the next enumeration run.
     */
    private String markFirstResourceStateAsStale(VerificationHost host,
            Class<? extends ResourceState> resourceClass, String serviceFactoryLink) throws Throwable {
        // get enumerated resources, and change the id of one of them, so that it is deleted on the
        // next enum
        Map<String, ? extends ResourceState> statesMap = ProvisioningUtils
                .getResourceStates(host,
                        serviceFactoryLink, resourceClass);

        assertNotNull("There should be resources enumerated.", statesMap);
        assertFalse("There should be resources enumerated.", statesMap.isEmpty());
        ResourceState existingState = statesMap.values().iterator().next();

        existingState.id = existingState.id + "-stale";
        existingState = doPatch(host, existingState,
                resourceClass,
                UriUtils.buildUri(host,
                        existingState.documentSelfLink));
        return existingState.documentSelfLink;
    }

    private void validateStaleResourceStateDeletion(String... staleResourceSelfLinks) throws Throwable {
        for (String selfLink : staleResourceSelfLinks) {
            ResourceState resourceState = null;
            try {
                resourceState = host.getServiceState(null,
                        ResourceState.class,
                        UriUtils.buildUri(this.host, selfLink));
            } catch (Throwable e) {
                // do nothing, expected is the resource not to be found
            }
            //the resourceState will be deleted by the groomer task after disassociation
            assertTrue("Stale subnet state should have been disassociated.", resourceState
                    .endpointLinks.isEmpty());
        }
    }

    private void validateSecurityGroupsInformation(Set<String> securityGroupLinks) throws Throwable {
        if (this.isAwsClientMock) {
            return;
        }

        // Query all the SGs, enumerated in the system
        Map<String, SecurityGroupState> allSecurityGroupStatesMap =
                ProvisioningUtils.<SecurityGroupState> getResourceStates(this.host,
                        SecurityGroupService.FACTORY_LINK, SecurityGroupState.class);
        // Assert that there are SGs enumerated in the system
        assertNotNull(allSecurityGroupStatesMap);

        if (securityGroupLinks == null) {
            return;
        }
        validateSecurityGroupTagLinks(allSecurityGroupStatesMap);
        List<URI> securityGroupURIs = new ArrayList<>();
        for (String sgLink : securityGroupLinks) {
            securityGroupURIs.add(UriUtils.buildUri(this.host, sgLink));
        }

        // Validate that the SecurityGroups for this VM are correctly described in SGStates
        Map<URI, SecurityGroupState> sgStatesToLinksMap = this.host
                .getServiceState(null, SecurityGroupState.class, securityGroupURIs);
        for (URI uri : securityGroupURIs) {
            // Assert the SG State exist
            assertNotNull(sgStatesToLinksMap.get(uri));
            // Assert that the security group rules are correctly added to the SG State
            // In the test setup there are both ingress and egress rules added
            assertTrue(sgStatesToLinksMap.get(uri).ingress.size() > 0);
            assertTrue(sgStatesToLinksMap.get(uri).egress.size() > 0);
            assertFalse(
                    StringUtil.isNullOrEmpty(
                            sgStatesToLinksMap.get(uri).customProperties.get(AWS_VPC_ID)));
        }
    }

    /**
     * Validates the taglinks for the security group to follow the expected norm
     * i.e. /resources/security-groups/UUID
     */
    private void validateSecurityGroupTagLinks(Map<String, SecurityGroupState> allSecurityGroupStatesMap) {
        for (Map.Entry<String, SecurityGroupState> securityGroupState : allSecurityGroupStatesMap.entrySet()) {
            Set<String> tagLinks = securityGroupState.getValue().tagLinks;
            if (tagLinks != null) {
                for (String tag : tagLinks) {
                    assertTrue(tag.startsWith(TagService.FACTORY_LINK));
                }
            }
            TagService.TagState expectedInternalTypeTag = newTagState(TAG_KEY_TYPE,
                    AWSConstants.AWSResourceType.ec2_security_group.toString(), false,
                    securityGroupState.getValue().tenantLinks);
            assertTrue(tagLinks.contains(expectedInternalTypeTag.documentSelfLink));
        }
    }

    /**
     * Validates the network interface count matches an expected number.
     */
    private void validateNetworkInterfaceCount(int totalNetworkInterfaceStateCount)
            throws Throwable {
        if (this.isAwsClientMock) {
            return;
        }

        queryDocumentsAndAssertExpectedCount(this.host, totalNetworkInterfaceStateCount,
                NetworkInterfaceService.FACTORY_LINK, false);
    }

    /**
     * Validates the public network interface and its document have been removed.
     *
     * @throws Throwable
     */
    private void validateRemovalOfPublicNetworkInterface(String instanceId,
            int desiredNetworkInterfaceStateCount) throws Throwable {
        if (this.isAwsClientMock) {
            return;
        }

        ComputeState stoppedComputeState = getComputeByAWSId(this.host, instanceId);
        assertNotNull(stoppedComputeState);

        validateNetworkInterfaceCount(desiredNetworkInterfaceStateCount);
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

        this.endpointState = createAWSEndpointState(this.host, auth.documentSelfLink, resourcePool.documentSelfLink);

        // create a compute host for the AWS EC2 VM
        this.computeHost = createAWSComputeHost(this.host,
                this.endpointState,
                null /*zoneId*/, this.useAllRegions ? null : regionId,
                this.isAwsClientMock,
                this.awsMockEndpointReference, null /*tags*/);

        this.endpointState.computeHostLink = this.computeHost.documentSelfLink;
        this.host.waitForResponse(Operation.createPatch(this.host, this.endpointState.documentSelfLink)
                .setBody(this.endpointState));
    }

    private void tearDownAwsVMs() {
        try {
            // Delete all vms from the endpoint that were provisioned from the test.
            this.host.log("Deleting %d instance created from the test ",
                    this.instancesToCleanUp.size());
            if (this.instancesToCleanUp.size() >= 0) {
                deleteVMsUsingEC2Client(this.client, this.host, this.instancesToCleanUp);
                deleteVMsOnThisEndpoint(this.host, this.isMock,
                        this.computeHost.documentSelfLink, this.instancesToCleanUp);
                // Check that all the instances that are required to be deleted are in
                // terminated state on AWS
                waitForInstancesToBeTerminated(this.client, this.host, this.instancesToCleanUp);
                this.instancesToCleanUp.clear();
            }
            //Delete newly created NIC
            deleteNICDirectlyWithEC2Client(this.client, this.host, this.nicToCleanUp);
        } catch (Throwable deleteEx) {
            // just log and move on
            this.host.log(Level.WARNING, "Exception deleting VMs - %s, instance ids - %s",
                    deleteEx.getMessage(), this.instancesToCleanUp);
        }
    }

    private void tearDownAwsLoadBalancer() {
        try {
            this.host.log("Deleting %s load balancer created from the test ",
                    this.lbToCleanUp);
            if (this.lbToCleanUp != null) {
                deleteLBsUsingLBClient(this.lbClient, this.host, this.lbToCleanUp);
            }
        } catch (Throwable deleteEx) {
            // just log and move on
            this.host.log(Level.WARNING, "Exception deleting LB - %s, lb name - %s",
                    deleteEx.getMessage(), this.lbToCleanUp);
        }
    }

    /**
     * Validates the hostname on a compute state is present and not null.
     */
    private ComputeState validateHostName(String awsId)
            throws Throwable {
        if (this.isAwsClientMock) {
            return null;
        }

        ComputeState computeState = getComputeByAWSId(this.host, awsId);

        String hostName = computeState.hostName;
        assertNotNull("'hostname' property should be present", hostName);

        return computeState;
    }
}
