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
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.deleteNICDirectlyWithEC2Client;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.deleteVMsOnThisEndpoint;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.deleteVMsUsingEC2Client;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.detachNICDirectlyWithEC2Client;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.enumerateResources;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.enumerateResourcesPreserveMissing;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.getComputeByAWSId;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.getNICByAWSId;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.instanceType_t2_micro;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.provisionAWSEBSVMWithEC2Client;
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
import static com.vmware.photon.controller.model.tasks.ProvisioningUtils.queryComputeInstances;
import static com.vmware.photon.controller.model.tasks.ProvisioningUtils.queryDocumentsAndAssertExpectedCount;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.BlockDeviceMapping;
import com.amazonaws.services.ec2.model.EbsBlockDevice;
import com.amazonaws.services.ec2.model.Tag;

import io.netty.util.internal.StringUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.vmware.photon.controller.model.ComputeProperties.OSType;
import com.vmware.photon.controller.model.PhotonModelMetricServices;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.AwsNicSpecs;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.LifecycleState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.SecurityGroupService;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;
import com.vmware.xenon.common.BasicTestCase;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

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

    private static final String TEST_CASE_INITIAL = "Initial Run ";
    private static final String TEST_CASE_ADDITIONAL_VM = "Additional VM ";
    private static final String TEST_CASE_ADDITIONAL_NIC = "Additional NIC";
    private static final String TEST_CASE_REMOVED_NIC = "Removed NIC";
    private static final String TEST_CASE_PURE_UPDATE = "Only Update to existing VM.";
    private static final String TEST_CASE_STOP_VM = "Stop VM ";
    private static final String TEST_CASE_DELETE_VM = "Delete VM ";
    private static final String TEST_CASE_DELETE_VMS = "Delete multiple VMs ";
    private static final String TEST_CASE_MOCK_MODE = "Mock Mode ";
    private static final int DEFAULT_TEST_PAGE_SIZE = 5;
    private static final String T2_NANO_INSTANCE_TYPE = "t2.nano";
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

    public static final String INITIAL_SG_TAG = "initialSGTag";
    public static final String INITIAL_VPC_TAG = "initialVPCTag";
    public static final String INITIAL_SUBNET_TAG = "initialSubnetTag";
    public static final String INITIAL_DISK_TAG = "initialDiskTag";
    public static final String SECONDARY_SG_TAG = "secondarySGTag";
    public static final String SECONDARY_VPC_TAG = "secondaryVPCTag";
    public static final String SECONDARY_SUBNET_TAG = "secondarySubnetTag";
    public static final String SECONDARY_DISK_TAG = "secondaryDiskTag";

    private ComputeState computeHost;
    private EndpointState endpointState;

    private List<String> instancesToCleanUp = new ArrayList<>();
    private String nicToCleanUp = null;
    private AmazonEC2AsyncClient client;
    public boolean isAwsClientMock = false;
    public String awsMockEndpointReference = null;

    public boolean isMock = true;
    public String accessKey = "accessKey";
    public String secretKey = "secretKey";
    public boolean useAllRegions = false;
    public int timeoutSeconds = DEFAULT_TIMOUT_SECONDS;

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
        this.client = AWSUtils.getAsyncClient(creds, TestAWSSetupUtils.regionId, getExecutor());

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
            AWSAdapters.startServices(this.host);

            this.host.setTimeoutSeconds(this.timeoutSeconds);

            this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelAdaptersRegistryAdapters.LINKS);
            this.host.waitForServiceAvailable(AWSAdapters.LINKS);
        } catch (Throwable e) {
            this.host.log("Error starting up services for the test %s", e.getMessage());
            throw new Exception(e);
        }

        // create the compute host, resource pool and the VM state to be used in the test.
        initResourcePoolAndComputeHost();
    }

    @After
    public void tearDown() throws Throwable {
        if (this.host == null) {
            return;
        }
        tearDownAwsVMs();
        tearDownTestVpc(this.client, this.host, this.awsTestContext, this.isMock);
        this.client.shutdown();
        setAwsClientMockInfo(false, null);
    }

    // Runs the enumeration task on the AWS endpoint to list all the instances on the endpoint.
    @Test
    public void testEnumeration() throws Throwable {

        this.host.log("Running test: " + this.currentTestName.getMethodName());

        ComputeState vmState = createAWSVMResource(this.host, this.computeHost, this.endpointState,
                TestAWSSetupUtils.class, zoneId, regionId, null, this.singleNicSpec);

        if (this.isMock) {
            // Just make a call to the enumeration service and make sure that the adapter patches
            // the parent with completion.
            enumerateResources(this.host, this.computeHost, this.endpointState, this.isMock,
                    TEST_CASE_MOCK_MODE);
            return;
        }

        this.host.setTimeoutSeconds(this.timeoutSeconds);
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
        List<String> instanceIdsToDeleteFirstTime = provisionAWSVMWithEC2Client(this.client,
                this.host, count4, T2_NANO_INSTANCE_TYPE, this.subnetId, this.securityGroupId);
        List<String> instanceIds = provisionAWSVMWithEC2Client(this.client, this.host, count1,
                instanceType_t2_micro, this.subnetId, this.securityGroupId);
        instanceIdsToDeleteFirstTime.addAll(instanceIds);
        this.instancesToCleanUp.addAll(instanceIdsToDeleteFirstTime);
        waitForProvisioningToComplete(instanceIdsToDeleteFirstTime, this.host, this.client, ZERO);

        // Xenon does not know about the new instances.
        ProvisioningUtils.queryComputeInstances(this.host, count2);

        enumerateResources(this.host, this.computeHost, this.endpointState, this.isMock,
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
        queryDocumentsAndAssertExpectedCount(this.host,
                count7, DiskService.FACTORY_LINK, false);

        // Validate at least 4 availability zones were enumerated
        ProvisioningUtils.queryComputeInstancesByType(this.host, count4,
                ComputeType.ZONE.toString(), false);

        // Update Scenario : Check that the tag information is present for the VM tagged above.
        String vpCId = validateTagAndNetworkAndComputeDescriptionInformation(vmState);
        validateVPCInformation(vpCId);
        // Count should be 1 NICs per discovered VM.
        int totalNetworkInterfaceStateCount = count6
                * this.singleNicSpec.numberOfNics();
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
        // During the enumeration, if one instance is stopped, its public ip address
        // will disappear, then the corresponding link of local ComputeState's public
        // network interface and its document will be removed.
        enumerateResources(this.host, this.computeHost, this.endpointState, this.isMock,
                TEST_CASE_STOP_VM);

        // Because one public NIC and its document are removed,
        // the totalNetworkInterfaceStateCount should go down by 1
        validateRemovalOfPublicNetworkInterface(instanceIdsToStop,
                totalNetworkInterfaceStateCount - 1);

        // Provision an additional VM with a different instance type. It should re-use the
        // existing compute description created by the enumeration task above.
        List<String> instanceIdsToDeleteSecondTime = provisionAWSVMWithEC2Client(this.client,
                this.host, count1, TestAWSSetupUtils.instanceType_t2_micro, this.subnetId, this.securityGroupId);
        this.instancesToCleanUp.addAll(instanceIdsToDeleteSecondTime);
        waitForProvisioningToComplete(instanceIdsToDeleteSecondTime, this.host, this.client,
                ZERO);
        enumerateResources(this.host, this.computeHost, this.endpointState, this.isMock,
                TEST_CASE_ADDITIONAL_VM);
        // One additional compute state and no additional compute description should be
        // created. 1) compute host CD 2) t2.nano-system generated 3) t2.micro-system generated
        // 4) t2.micro-created from test code.
        queryDocumentsAndAssertExpectedCount(this.host,
                count4,
                ComputeDescriptionService.FACTORY_LINK, false);
        queryDocumentsAndAssertExpectedCount(this.host,
                count8, ComputeService.FACTORY_LINK, false);
        queryDocumentsAndAssertExpectedCount(this.host,
                count8, DiskService.FACTORY_LINK, false);

        // Verify Deletion flow
        // Delete 5 VMs spawned above of type T2_NANO
        deleteVMsUsingEC2Client(this.client, this.host, instanceIdsToDeleteFirstTime);
        enumerateResources(this.host, this.computeHost, this.endpointState, this.isMock,
                TEST_CASE_DELETE_VMS);
        // Counts should go down. 5 compute states and 5 disk states.
        queryDocumentsAndAssertExpectedCount(this.host,
                count3, ComputeService.FACTORY_LINK, false);
        queryDocumentsAndAssertExpectedCount(this.host,
                count3, DiskService.FACTORY_LINK, false);

        // Delete 1 VMs spawned above of type T2_Micro
        deleteVMsUsingEC2Client(this.client, this.host, instanceIdsToDeleteSecondTime);
        enumerateResources(this.host, this.computeHost, this.endpointState, this.isMock,
                TEST_CASE_DELETE_VM);
        // Compute state and disk state count should go down by 1
        queryDocumentsAndAssertExpectedCount(this.host,
                count2, ComputeService.FACTORY_LINK, false);
        queryDocumentsAndAssertExpectedCount(this.host,
                count2, DiskService.FACTORY_LINK, false);
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
                TestAWSSetupUtils.class, zoneId, regionId, null, this.singleNicSpec);

        this.host.setTimeoutSeconds(this.timeoutSeconds);
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
                TestAWSSetupUtils.class, zoneId, regionId, null, this.singleNicSpec);

        if (this.isMock) {
            // Just make a call to the enumeration service and make sure that the adapter patches
            // the parent with completion.
            enumerateResourcesPreserveMissing(this.host, this.computeHost, this.endpointState, this.isMock,
                    TEST_CASE_MOCK_MODE);
            return;
        }

        this.host.setTimeoutSeconds(this.timeoutSeconds);
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
                this.host, count4, T2_NANO_INSTANCE_TYPE, this.subnetId, this.securityGroupId);
        List<String> instanceIds = provisionAWSVMWithEC2Client(this.client, this.host, count1,
                instanceType_t2_micro, this.subnetId, this.securityGroupId);
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
            ServiceDocumentQueryResult serviceDocumentQueryResult = queryDocumentsAndAssertExpectedCount(
                    this.host, allTagsNumber, TagService.FACTORY_LINK, false);

            Map<String, TagState> tagsMap = new HashMap<>();
            for (Entry<String, Object> entry : serviceDocumentQueryResult.documents.entrySet()) {
                tagsMap.put(entry.getKey(), Utils.fromJson(entry.getValue(), TagState.class));
            }

            // validate security group tags
            Map<String, SecurityGroupState> allSecurityGroupStatesMap =
                    ProvisioningUtils.<SecurityGroupState> getResourceStates(this.host,
                            SecurityGroupService.FACTORY_LINK, SecurityGroupState.class);
            SecurityGroupState defaultSgState = allSecurityGroupStatesMap.get(this.securityGroupId);
            // ensure one link is deleted and one new is added to the sg state
            assertNotNull(defaultSgState.tagLinks);
            assertEquals(1, defaultSgState.tagLinks.size());

            // validate vpc tags
            Map<String, NetworkState> allNetworkStatesMap =
                    ProvisioningUtils.<NetworkState> getResourceStates(this.host,
                            NetworkService.FACTORY_LINK, NetworkState.class);
            NetworkState defaultNetworkState = allNetworkStatesMap.get(this.vpcId);
            // ensure one link is deleted and one new is added to the network state
            assertEquals("Wrong number of network tag links found.", 1, defaultNetworkState.tagLinks.size());

            // validate subnet tags
            Map<String, SubnetState> allSubnetStatesMap =
                    ProvisioningUtils.<SubnetState> getResourceStates(this.host,
                            SubnetService.FACTORY_LINK, SubnetState.class);
            SubnetState defaultSubnetState = allSubnetStatesMap.get(this.subnetId);
            // ensure one link is deleted and one new is added to the subnet state
            assertEquals("Wrong number of subnet tag links found.", 1, defaultSubnetState.tagLinks.size());

            // validate disk tags
            Map<String, DiskState> allDiskStatesMap =
                    ProvisioningUtils.<DiskState> getResourceStates(this.host,
                            DiskService.FACTORY_LINK, DiskState.class);
            DiskState defaultDiskState = allDiskStatesMap.get(this.diskId);
            // ensure one link is deleted and one new is added to the disk state
            assertEquals("Wrong number of disk tag links found.", 1, defaultDiskState.tagLinks.size());

            // validate vm tags
            Map<Tag, String> vmTagLinks = new HashMap<>();
            for (Tag tag : vmTags) {
                for (TagState tagState : tagsMap.values()) {
                    if (tagState.key.equals(tag.getKey())) {
                        vmTagLinks.put(tag, tagState.key);
                        return;
                    }
                }
            }

            ComputeState linuxVMId1ComputeState = getComputeByAWSId(this.host, linuxVMId1);
            assertEquals(linuxVMId1Tags.size(), linuxVMId1ComputeState.tagLinks.size());
            for (Tag tag : linuxVMId1Tags) {
                assertTrue(linuxVMId1ComputeState.tagLinks.contains(vmTagLinks.get(tag)));
            }

            ComputeState linuxVMId2ComputeState = getComputeByAWSId(this.host, linuxVMId2);
            assertEquals(linuxVMId2Tags.size(), linuxVMId2ComputeState.tagLinks.size());
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

        this.host.log("Running test: " + this.currentTestName.getMethodName());

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

        List<SubnetState> subnetStates = TestUtils.getSubnetStates(this.host, networkState);
        assertFalse(subnetStates.isEmpty());
        subnetStates.stream().forEach(subnetState -> {
            assertNotNull(subnetState.subnetCIDR);
            assertNotNull(subnetState.zoneId);
        });
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
