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

import static com.vmware.photon.controller.model.ComputeProperties.CUSTOM_OS_TYPE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_GATEWAY_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_SUBNET_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_TAGS;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_TAG_NAME;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_VPC_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_VPC_ROUTE_TABLE_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.PUBLIC_INTERFACE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.setQueryPageSize;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.setQueryResultLimit;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.tagResourcesWithName;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.EC2_LINUX_AMI;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.EC2_WINDOWS_AMI;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSComputeHost;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSResourcePool;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSVMResource;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.deleteVMsOnThisEndpoint;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.deleteVMsUsingEC2Client;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.enumerateResources;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.getBaseLineInstanceCount;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.getComputeByAWSId;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.instanceType_t2_micro;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.provisionAWSVMWithEC2Client;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.provisionMachine;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.setAwsClientMockInfo;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.stopVMsUsingEC2Client;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.waitForInstancesToBeTerminated;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.waitForProvisioningToComplete;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.zoneId;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestUtils.getExecutor;
import static com.vmware.photon.controller.model.tasks.ProvisioningUtils.getNetworkStates;
import static com.vmware.photon.controller.model.tasks.ProvisioningUtils.queryComputeInstances;
import static com.vmware.photon.controller.model.tasks.ProvisioningUtils.queryDocumentsAndAssertExpectedCount;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.ComputeProperties.OSType;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.BaseLineState;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSComputeStateCreationAdapterService.AWSTags;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;
import com.vmware.xenon.common.BasicTestCase;
import com.vmware.xenon.common.CommandLineArgumentParser;
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
 *
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

    private static List<String> testComputeDescriptions = new ArrayList<String>(
            Arrays.asList(zoneId + "~" + T2_NANO_INSTANCE_TYPE,
                    zoneId + "~" + instanceType_t2_micro));

    private ResourcePoolState outPool;
    private ComputeService.ComputeState outComputeHost;

    private List<String> instancesToCleanUp = new ArrayList<String>();
    private AmazonEC2AsyncClient client;
    public boolean isAwsClientMock = false;
    public String awsMockEndpointReference = null;
    private BaseLineState baseLineState;

    public boolean isMock = true;
    public String accessKey = "accessKey";
    public String secretKey = "secretKey";

    @Before
    public void setUp() throws Throwable {
        CommandLineArgumentParser.parseFromProperties(this);

        setAwsClientMockInfo(this.isAwsClientMock, this.awsMockEndpointReference);
        // create credentials
        AuthCredentialsServiceState creds = new AuthCredentialsServiceState();
        creds.privateKey = this.secretKey;
        creds.privateKeyId = this.accessKey;
        this.client = AWSUtils.getAsyncClient(creds, TestAWSSetupUtils.zoneId, getExecutor());
        try {
            PhotonModelServices.startServices(this.host);
            PhotonModelTaskServices.startServices(this.host);
            AWSAdapters.startServices(this.host);

            // TODO: VSYM-992 - improve test/fix arbitrary timeout
            this.host.setTimeoutSeconds(200);

            this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
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
        teardownAwsVMs();
        this.client.shutdown();
        setAwsClientMockInfo(false, null);
    }

    // Runs the enumeration task on the AWS endpoint to list all the instances on the endpoint.
    @Test
    public void testEnumeration() throws Throwable {
        ComputeState vmState = createAWSVMResource(this.host, this.outComputeHost.documentSelfLink,
                this.outPool.documentSelfLink, TestAWSSetupUtils.class);

        if (this.isMock) {
            // Just make a call to the enumeration service and make sure that the adapter patches
            // the parent with completion.
            enumerateResources(this.host, this.isMock, this.outPool.documentSelfLink,
                    this.outComputeHost.descriptionLink, this.outComputeHost.documentSelfLink,
                    TEST_CASE_MOCK_MODE);
            return;
        }

        List<String> instanceIdsToDeleteFirstTime = new ArrayList<String>();
        List<String> instanceIdsToDeleteSecondTime = new ArrayList<String>();

        this.host.setTimeoutSeconds(600);
        // Overriding the page size to test the pagination logic with limited instances on AWS.
        // This is a functional test
        // so the latency numbers maybe higher from this test due to low page size.
        setQueryPageSize(DEFAULT_TEST_PAGE_SIZE);
        setQueryResultLimit(DEFAULT_TEST_PAGE_SIZE);
        this.baseLineState = getBaseLineInstanceCount(this.host, this.client,
                testComputeDescriptions);
        this.host.log(this.baseLineState.toString());
        // Provision a single VM . Check initial state.
        vmState = provisionMachine(this.host, vmState, this.isMock, this.instancesToCleanUp);
        queryComputeInstances(this.host, count2);
        queryDocumentsAndAssertExpectedCount(this.host, count2,
                ComputeDescriptionService.FACTORY_LINK);

        // CREATION directly on AWS
        instanceIdsToDeleteFirstTime = provisionAWSVMWithEC2Client(this.client, this.host,
                count4, T2_NANO_INSTANCE_TYPE);
        List<String> instanceIds = provisionAWSVMWithEC2Client(this.client, this.host, count1,
                instanceType_t2_micro);
        instanceIdsToDeleteFirstTime.addAll(instanceIds);
        this.instancesToCleanUp.addAll(instanceIdsToDeleteFirstTime);
        waitForProvisioningToComplete(instanceIdsToDeleteFirstTime, this.host, this.client,
                ZERO);

        // Xenon does not know about the new instances.
        ProvisioningUtils.queryComputeInstances(this.host, count2);

        enumerateResources(this.host, this.isMock, this.outPool.documentSelfLink,
                this.outComputeHost.descriptionLink, this.outComputeHost.documentSelfLink,
                TEST_CASE_INITIAL);
        // 5 new resources should be discovered. Mapping to 2 new compute description and 5 new
        // compute states.
        // Even though the "t2.micro" is common to the VM provisioned from Xenon
        // service and the one directly provisioned on EC2, there is no Compute description
        // linking of discovered resources to user defined compute descriptions. So a new system
        // generated compute description will be created for "t2.micro"
        queryDocumentsAndAssertExpectedCount(this.host,
                count4 + this.baseLineState.baselineComputeDescriptionCount,
                ComputeDescriptionService.FACTORY_LINK);
        queryComputeInstances(this.host,
                count7 + this.baseLineState.baselineVMCount);

        // Update Scenario : Check that the tag information is present for the VM tagged above.
        String vpCId = validateTagAndNetworkAndComputeDescriptionInformation(vmState);
        validateVPCInformation(vpCId);
        // Count should be 2 NICs per discovered VM
        int totalNetworkInterfaceStateCount = (count6 + this.baseLineState.baselineVMCount) * 2;
        validateNetworkInterfaceCount(totalNetworkInterfaceStateCount);
        // One VPC should be discovered in the test.
        queryDocumentsAndAssertExpectedCount(this.host, count1,
                NetworkService.FACTORY_LINK);

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
        enumerateResources(this.host, this.isMock, this.outPool.documentSelfLink,
                this.outComputeHost.descriptionLink, this.outComputeHost.documentSelfLink,
                TEST_CASE_STOP_VM);

        // Because one public NIC and its document are removed,
        // the totalNetworkInterfaceStateCount should go down by 1
        validateRemovalOfPublicNetworkInterface(instanceIdsToStop,
                totalNetworkInterfaceStateCount - 1);

        // Provision an additional VM with a different instance type. It should re-use the
        // existing compute description created by the enumeration task above.
        instanceIdsToDeleteSecondTime = provisionAWSVMWithEC2Client(this.client, this.host,
                count1, TestAWSSetupUtils.instanceType_t2_micro);
        this.instancesToCleanUp.addAll(instanceIdsToDeleteSecondTime);
        waitForProvisioningToComplete(instanceIdsToDeleteSecondTime, this.host, this.client,
                ZERO);
        enumerateResources(this.host, this.isMock, this.outPool.documentSelfLink,
                this.outComputeHost.descriptionLink, this.outComputeHost.documentSelfLink,
                TEST_CASE_ADDITIONAL_VM);
        // One additional compute state and and one additional compute description should be
        // created. 1) compute host CD 2) t2.nano-system generated 3) t2.micro-system generated
        // 4) t2.micro-created from test code.
        queryDocumentsAndAssertExpectedCount(this.host,
                count4 + this.baseLineState.baselineComputeDescriptionCount,
                ComputeDescriptionService.FACTORY_LINK);
        queryComputeInstances(this.host,
                count8 + this.baseLineState.baselineVMCount);

        // Verify Deletion flow
        // Delete 5 VMs spawned above of type T2_NANO
        deleteVMsUsingEC2Client(this.client, this.host, instanceIdsToDeleteFirstTime);
        enumerateResources(this.host, this.isMock, this.outPool.documentSelfLink,
                this.outComputeHost.descriptionLink, this.outComputeHost.documentSelfLink,
                TEST_CASE_DELETE_VMS);
        // Counts should go down 5 compute states.
        queryComputeInstances(this.host,
                count3 + this.baseLineState.baselineVMCount);

        // Delete 1 VMs spawned above of type T2_Micro
        deleteVMsUsingEC2Client(this.client, this.host, instanceIdsToDeleteSecondTime);
        enumerateResources(this.host, this.isMock, this.outPool.documentSelfLink,
                this.outComputeHost.descriptionLink, this.outComputeHost.documentSelfLink,
                TEST_CASE_DELETE_VM);
        // Compute state count should go down by 1
        queryComputeInstances(this.host,
                count2 + this.baseLineState.baselineVMCount);
    }

    @Test
    public void testOSTypeEnumeration() throws Throwable {
        if (this.isMock) {
            return;
        }

        String linuxVMId = provisionAWSVMWithEC2Client(this.client, EC2_LINUX_AMI);
        this.instancesToCleanUp.add(linuxVMId);

        String windowsVMId = provisionAWSVMWithEC2Client(this.client, EC2_WINDOWS_AMI);
        this.instancesToCleanUp.add(windowsVMId);

        waitForProvisioningToComplete(this.instancesToCleanUp, this.host, this.client, ZERO);

        enumerateResources(this.host, this.isMock, this.outPool.documentSelfLink,
                this.outComputeHost.descriptionLink, this.outComputeHost.documentSelfLink,
                TEST_CASE_INITIAL);

        ComputeState linuxCompute = getComputeByAWSId(this.host, linuxVMId);
        assertNotNull(linuxCompute);
        assertNotNull(linuxCompute.customProperties);
        String linuxOSType = linuxCompute.customProperties.get(CUSTOM_OS_TYPE);
        assertNotNull(linuxOSType);
        assertEquals(OSType.LINUX.toString(), linuxOSType);

        ComputeState winCompute = getComputeByAWSId(this.host, windowsVMId);
        assertNotNull(winCompute);
        assertNotNull(winCompute.customProperties);
        String winOSType = winCompute.customProperties.get(CUSTOM_OS_TYPE);
        assertNotNull(winOSType);
        assertEquals(OSType.WINDOWS.toString(), winOSType);
    }

    @Test
    public void testDisplayNameEnumeration() throws Throwable {
        if (this.isMock) {
            return;
        }

        String linuxVMId = provisionAWSVMWithEC2Client(this.client, EC2_LINUX_AMI);
        this.instancesToCleanUp.add(linuxVMId);

        waitForProvisioningToComplete(this.instancesToCleanUp, this.host, this.client, ZERO);

        // Tag the first VM with a name
        tagResourcesWithName(this.client, VM_NAME, linuxVMId);

        enumerateResources(this.host, this.isMock, this.outPool.documentSelfLink,
                this.outComputeHost.descriptionLink, this.outComputeHost.documentSelfLink,
                TEST_CASE_INITIAL);

        validateComputeNameAndAWSTags(linuxVMId, VM_NAME);

        // Update the tag on the VM already known to the system
        tagResourcesWithName(this.client, VM_UPDATED_NAME, linuxVMId);

        enumerateResources(this.host, this.isMock, this.outPool.documentSelfLink,
                this.outComputeHost.descriptionLink, this.outComputeHost.documentSelfLink,
                TEST_CASE_PURE_UPDATE);

        validateComputeNameAndAWSTags(linuxVMId, VM_UPDATED_NAME);

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
        assertTrue(taggedComputeState.networkLinks != null);
        assertTrue(taggedComputeState.networkLinks.size() == 2);

        URI[] networkLinkURIs = new URI[2];
        for (int i = 0; i < taggedComputeState.networkLinks.size(); i++) {
            networkLinkURIs[i] = UriUtils.buildUri(this.host,
                    taggedComputeState.networkLinks.get(i));
        }

        // Assert that both the public and private IP addresses have been mapped to separated NICs
        Map<URI, NetworkInterfaceState> NICMap = this.host
                .getServiceState(null, NetworkInterfaceState.class, networkLinkURIs);
        assertNotNull(NICMap.get(networkLinkURIs[0]).address);
        assertNotNull(NICMap.get(networkLinkURIs[1]).address);

        // get the VPC information for the provisioned VM
        assertTrue(taggedComputeState.customProperties.get(AWS_VPC_ID) != null);
        return taggedComputeState.customProperties.get(AWS_VPC_ID);

    }

    /**
     * Validates the tag information on a compute state matches an expected virtual machine name.
     */
    private ComputeState validateComputeNameAndAWSTags(String awsId, String vmName)
            throws Throwable {
        if (this.isAwsClientMock) {
            return null;
        }

        ComputeState computeState = getComputeByAWSId(this.host, awsId);

        // verify conversion from AWS_TAGS to CUSTOM_DISPLAY_NAME
        String tagNameValue = computeState.name;
        assertNotNull("'displayName' property should be present", tagNameValue);
        assertEquals(vmName, tagNameValue);

        // verify aws tags
        String awsTagsStr = computeState.customProperties.get(AWS_TAGS);
        assertNotNull(awsTagsStr);

        AWSTags awsTags = Utils.fromJson(awsTagsStr, AWSTags.class);
        assertNotNull(awsTags);
        assertNotNull(awsTags.awsTags);
        assertEquals(1, awsTags.awsTags.size());

        assertEquals(AWS_TAG_NAME, awsTags.awsTags.get(0).getKey());
        assertEquals(vmName, awsTags.awsTags.get(0).getValue());
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

        Map<String, NetworkState> networkStateMap = getNetworkStates(this.host);
        assertNotNull(networkStateMap);
        NetworkState networkState = networkStateMap.get(vpCId);
        // The network state for the VPC id of the VM should not be null
        assertNotNull(networkState);
        assertNotNull(networkState.subnetCIDR);
        assertNotNull(networkState.instanceAdapterReference);
        // This is assuming that the internet gateway is attached to the VPC by default
        assertNotNull(networkState.customProperties.get(AWS_GATEWAY_ID));
        assertNotNull(networkState.customProperties.get(AWS_SUBNET_ID));
        assertNotNull(networkState.customProperties.get(AWS_VPC_ROUTE_TABLE_ID));
        assertNotNull(networkState.customProperties.get(AWS_VPC_ID));
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
                NetworkInterfaceService.FACTORY_LINK);
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
        // make sure that the stopped instance has no public network interface
        for (String networkLink : stoppedComputeState.networkLinks) {
            assertFalse(networkLink.contains(PUBLIC_INTERFACE));
        }

        validateNetworkInterfaceCount(desiredNetworkInterfaceStateCount);
    }

    /**
     * Creates the state associated with the resource pool, compute host and the VM to be created.
     *
     * @throws Throwable
     */
    private void initResourcePoolAndComputeHost() throws Throwable {
        // Create a resource pool where the VM will be housed
        this.outPool = createAWSResourcePool(this.host);

        // create a compute host for the AWS EC2 VM
        this.outComputeHost = createAWSComputeHost(this.host, this.outPool.documentSelfLink,
                this.accessKey, this.secretKey, this.isAwsClientMock,
                this.awsMockEndpointReference);

    }

    private void teardownAwsVMs() {
        try {
            // Delete all vms from the endpoint that were provisioned from the test.
            this.host.log("Deleting %d instance created from the test ",
                    this.instancesToCleanUp.size());
            if (this.instancesToCleanUp.size() >= 0) {
                deleteVMsOnThisEndpoint(this.host, this.isMock,
                        this.outComputeHost.documentSelfLink, this.instancesToCleanUp);
                // Check that all the instances that are required to be deleted are in
                // terminated state on AWS
                waitForInstancesToBeTerminated(this.client, this.host, this.instancesToCleanUp);
                this.instancesToCleanUp.clear();
            }

        } catch (Throwable deleteEx) {
            // just log and move on
            this.host.log(Level.WARNING, "Exception deleting VMs - %s", deleteEx.getMessage());
        }
    }

}
