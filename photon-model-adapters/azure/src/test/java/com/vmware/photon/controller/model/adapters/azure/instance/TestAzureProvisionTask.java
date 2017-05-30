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

package com.vmware.photon.controller.model.adapters.azure.instance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.DEFAULT_NIC_SPEC;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.NO_PUBLIC_IP_NIC_SPEC;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.SHARED_NETWORK_NIC_SPEC;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultAuthCredentials;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultComputeHost;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultEndpointState;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultResourceGroupState;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultResourcePool;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultVMResource;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.deleteVMs;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.generateName;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.compute.implementation.ComputeManagementClientImpl;
import com.microsoft.azure.management.compute.implementation.VirtualMachineInner;
import com.microsoft.azure.management.network.implementation.NetworkManagementClientImpl;
import com.microsoft.azure.management.network.implementation.NetworkSecurityGroupInner;
import com.microsoft.azure.management.network.implementation.VirtualNetworkInner;
import com.microsoft.azure.management.resources.implementation.ResourceGroupInner;
import com.microsoft.azure.management.resources.implementation.ResourceManagementClientImpl;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.vmware.photon.controller.model.PhotonModelMetricServices;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse;
import com.vmware.photon.controller.model.adapters.azure.AzureAdapters;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.ResourceGroupStateType;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceStateWithDescription;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.StorageDescriptionService;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class TestAzureProvisionTask extends BasicReusableHostTestCase {

    // SHARED Compute Host / End-point between test runs. {{
    private static ComputeState computeHost;
    private static EndpointState endpointState;
    // Every test in addition might change it.
    private static String azureVMName = generateName("testProv-");
    // }}

    public String clientID = "clientID";
    public String clientKey = "clientKey";
    public String subscriptionId = "subscriptionId";
    public String tenantId = "tenantId";

    public boolean isMock = true;
    public boolean skipStats = true;

    // fields that are used across method calls, stash them as private fields
    private ComputeManagementClientImpl computeManagementClient;
    private ResourceManagementClientImpl resourceManagementClient;
    private NetworkManagementClientImpl networkManagementClient;

    private ComputeState vmState;

    @Rule
    public TestName currentTestName = new TestName();

    @Before
    public void setUp() throws Exception {
        try {
            /*
             * Init Class-specific (shared between test runs) vars.
             *
             * NOTE: Ultimately this should go to @BeforeClass, BUT BasicReusableHostTestCase.HOST
             * is not accessible.
             */
            if (computeHost == null) {
                PhotonModelServices.startServices(this.host);
                PhotonModelMetricServices.startServices(this.host);
                PhotonModelAdaptersRegistryAdapters.startServices(this.host);
                PhotonModelTaskServices.startServices(this.host);
                AzureAdapters.startServices(this.host);

                this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
                this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
                this.host.waitForServiceAvailable(PhotonModelAdaptersRegistryAdapters.LINKS);
                this.host.waitForServiceAvailable(AzureAdapters.LINKS);

                // TODO: VSYM-992 - improve test/fix arbitrary timeout
                this.host.setTimeoutSeconds(1200);

                // Create a resource pool where the VM will be housed
                ResourcePoolState resourcePool = createDefaultResourcePool(this.host);

                AuthCredentialsServiceState authCredentials = createDefaultAuthCredentials(
                        this.host,
                        this.clientID,
                        this.clientKey,
                        this.subscriptionId,
                        this.tenantId);

                endpointState = createDefaultEndpointState(
                        this.host, authCredentials.documentSelfLink);

                // create a compute host for the Azure
                computeHost = createDefaultComputeHost(this.host, resourcePool.documentSelfLink,
                        endpointState);
            }

            if (!this.isMock) {
                ApplicationTokenCredentials credentials = new ApplicationTokenCredentials(
                        this.clientID, this.tenantId, this.clientKey, AzureEnvironment.AZURE);

                this.computeManagementClient = new ComputeManagementClientImpl(credentials)
                        .withSubscriptionId(this.subscriptionId);

                this.resourceManagementClient = new ResourceManagementClientImpl(credentials)
                        .withSubscriptionId(this.subscriptionId);

                this.networkManagementClient = new NetworkManagementClientImpl(credentials)
                        .withSubscriptionId(this.subscriptionId);
            }


        } catch (Throwable e) {
            throw new Exception(e);
        }
    }

    @After
    public void tearDown() throws Exception {
        if (this.vmState != null) {
            try {
                this.host.log(Level.INFO, "%s: Deleting [%s] VM",
                        this.currentTestName.getMethodName(), this.vmState.name);

                // ONLY computeHost MUST remain!
                int computeStatesToRemain = 1;

                deleteVMs(this.host, this.vmState.documentSelfLink, this.isMock,
                        computeStatesToRemain);

            } catch (Throwable deleteExc) {
                // just log and move on
                this.host.log(Level.WARNING, "%s: Deleting [%s] VM: FAILED. Details: %s",
                        this.currentTestName.getMethodName(), this.vmState.name,
                        deleteExc.getMessage());
            }
        }
    }

    /**
     * Creates a Azure instance via a provision task.
     */
    @Test
    public void testProvision() throws Throwable {

        // create a Azure VM compute resource.
        this.vmState = createDefaultVMResource(this.host, azureVMName,
                computeHost, endpointState, DEFAULT_NIC_SPEC);

        kickOffProvisionTask();

        assertVmNetworksConfiguration();

        assertConfigurationOfDisks();

        assertStorageDescription();

        // Stats on individual VM is currently broken.
        if (!this.skipStats) {
            this.host.setTimeoutSeconds(60);
            this.host.waitFor("Error waiting for stats", () -> {
                try {
                    issueStatsRequest(this.vmState);
                } catch (Throwable t) {
                    return false;
                }
                return true;
            });
        }
    }

    /**
     * Creates a Azure instance via a provision task.
     */
    @Test
    @Ignore("This test does an additional VM provisioning that will cause the total preflight "
            + "time to exceed the limit and timeout the preflight. Only for manual execution.")
    public void testProvisionNoPublicIP() throws Throwable {

        // create a Azure VM compute resource.
        this.vmState = createDefaultVMResource(this.host, azureVMName,
                computeHost, endpointState, NO_PUBLIC_IP_NIC_SPEC);

        kickOffProvisionTask();

        assertVmNetworksConfiguration();
    }

    /**
     * Creates Azure instance that uses shared/existing Network via a provision task.
     * <p>
     * It duplicates {@link #testProvision()} and just points to an external/shared Network.
     */
    @Test
    @Ignore("Since azure build timeouts due to the time consuming provision-decomission VM executed"
            + "by the tests. So far we sacrifice this test.")
    public void testProvisionVMUsingSharedNetwork() throws Throwable {

        // The test is only suitable for real (non-mocking env).
        Assume.assumeFalse(this.isMock);

        /*
         * Create SHARED vNet-Subnets in a separate RG.
         *
         * VERY IMPORTANT NOTE1: The names of the vNet and Subnets MUST be equal to the ones set by
         * AzureTestUtil.createDefaultNicStates.
         *
         * NOTE2: Since this is SHARED vNet it's not deleted after the test.
         *
         * The idea here is that we are provisioning a VM and linking it to an already existing
         * subnet/network. That's why the network is with a !!!FIXED!!! name, and that SAME name is
         * being used by standard provisioning!
         */
        final ResourceGroupInner sharedNetworkRG = AzureTestUtil.createResourceGroupWithSharedNetwork
                (this.resourceManagementClient, this.networkManagementClient,
                        SHARED_NETWORK_NIC_SPEC);

        // Create corresponding ResourceGroupState
        ResourceGroupState sharedNetworkRGState = createDefaultResourceGroupState(
                this.host, sharedNetworkRG.name(), computeHost, endpointState,
                ResourceGroupStateType.AzureResourceGroup);

        // END of prepare phase.

        // In this scenario mark the VM name (and its corresponding RG) with "-withSharedNW"
        String vmName = azureVMName + "-withSharedNW";

        // create a Azure VM compute resource
        this.vmState = createDefaultVMResource(this.host, vmName,
                computeHost, endpointState,
                DEFAULT_NIC_SPEC,
                // In addition to standard provisioning pass the RG of the shared network
                sharedNetworkRGState.documentSelfLink);

        kickOffProvisionTask();

        assertVmNetworksConfiguration();
    }

    // kick off a provision task to do the actual VM creation
    private void kickOffProvisionTask() throws Throwable {

        ProvisionComputeTaskState provisionTask = new ProvisionComputeTaskState();

        provisionTask.computeLink = this.vmState.documentSelfLink;
        provisionTask.isMockRequest = this.isMock;
        provisionTask.taskSubStage = ProvisionComputeTaskState.SubStage.CREATING_HOST;

        provisionTask = TestUtils.doPost(this.host,
                provisionTask,
                ProvisionComputeTaskState.class,
                UriUtils.buildUri(this.host, ProvisionComputeTaskService.FACTORY_LINK));

        this.host.waitForFinishedTask(
                ProvisionComputeTaskState.class,
                provisionTask.documentSelfLink);
    }

    private void issueStatsRequest(ComputeState vm) throws Throwable {
        // spin up a stateless service that acts as the parent link to patch back to
        StatelessService parentService = new StatelessService() {
            @Override
            public void handleRequest(Operation op) {
                if (op.getAction() == Action.PATCH) {
                    if (!TestAzureProvisionTask.this.isMock) {
                        ComputeStatsResponse resp = op.getBody(ComputeStatsResponse.class);
                        if (resp.statsList.size() != 1) {
                            TestAzureProvisionTask.this.host.failIteration(
                                    new IllegalStateException("response size was incorrect."));
                            return;
                        }
                        if (resp.statsList.get(0).statValues.size() == 0) {
                            TestAzureProvisionTask.this.host
                                    .failIteration(new IllegalStateException(
                                            "incorrect number of metrics received."));
                            return;
                        }
                        if (!resp.statsList.get(0).computeLink.equals(vm.documentSelfLink)) {
                            TestAzureProvisionTask.this.host.failIteration(
                                    new IllegalStateException(
                                            "Incorrect computeReference returned."));
                            return;
                        }
                    }
                    TestAzureProvisionTask.this.host.completeIteration();
                }
            }
        };
        String servicePath = UUID.randomUUID().toString();
        Operation startOp = Operation.createPost(UriUtils.buildUri(this.host, servicePath));
        this.host.startService(startOp, parentService);
        ComputeStatsRequest statsRequest = new ComputeStatsRequest();
        statsRequest.resourceReference = UriUtils.buildUri(this.host, vm.documentSelfLink);
        statsRequest.isMockRequest = this.isMock;
        statsRequest.taskReference = UriUtils.buildUri(this.host, servicePath);
        this.host.sendAndWait(Operation.createPatch(UriUtils.buildUri(
                this.host, AzureUriPaths.AZURE_STATS_ADAPTER))
                .setBody(statsRequest)
                .setReferer(this.host.getUri()));
    }

    private void assertVmNetworksConfiguration() {

        // This assert is only suitable for real (non-mocking env).
        if (this.isMock) {
            return;
        }

        this.host.log(Level.INFO, "%s: Assert network configuration for [%s] VM",
                this.currentTestName.getMethodName(), this.vmState.name);

        ComputeState vm = this.host.getServiceState(null,
                ComputeState.class,
                UriUtils.buildUri(this.host, this.vmState.documentSelfLink));

        NetworkInterfaceStateWithDescription primaryNicState = this.host.getServiceState(null,
                NetworkInterfaceStateWithDescription.class,
                NetworkInterfaceStateWithDescription.buildUri(
                        UriUtils.buildUri(this.host, vm.networkInterfaceLinks.get(0))));

        if (primaryNicState.description.assignPublicIpAddress == null ||
                primaryNicState.description.assignPublicIpAddress == Boolean.TRUE) {
            assertNotNull("VM address should be set.", vm.address);
            assertNotNull("Primary NIC public IP should be set.", primaryNicState.address);
        } else {
            assertNull("VM address should be empty.", vm.address);
            assertNull("Primary NIC public IP should be set.", primaryNicState.address);
        }

        assertNotNull("Primary NIC security group should be set.",
                primaryNicState.securityGroupLinks != null);

        assertEquals("VM address should be the same as primary NIC public IP.", vm.address,
                primaryNicState.address);

        for (int i = 1; i < vm.networkInterfaceLinks.size(); i++) {
            NetworkInterfaceState nonPrimaryNicState = this.host.getServiceState(null,
                    NetworkInterfaceState.class,
                    UriUtils.buildUri(this.host, vm.networkInterfaceLinks.get(i)));

            assertNull("Non-primary NIC" + i + " public IP should not be set.",
                    nonPrimaryNicState.address);
            assertNull("Non-primary NIC" + i + " security group should not be set.",
                    nonPrimaryNicState.securityGroupLinks);
        }

        // Ensure that from the list of provided network resource groups,
        // and security group resource groups, the one with the correct type has been chosen.
        // For network, and sg, the correct RG is the default one named over the azureVMName.
        // Verifying the resources can be obtained from this RG, ensures they have been placed
        // correctly.
        try {
            // the provisioned resource contains its resource group in its id, preceded by "/resourceGroups/"
            // validations check for the existence of this combination to ensure the resource has been
            // provisioned in the correct RG
            String resourceGroupNameString = "resourceGroups/" + azureVMName;

            VirtualNetworkInner provisionedNetwork = AzureTestUtil.getAzureVirtualNetwork(
                    this.networkManagementClient, azureVMName, AzureTestUtil.AZURE_NETWORK_NAME);
            assertNotNull("Virtual network with correct name and by correct RG should be obtained.",
                    provisionedNetwork);
            assertTrue("Created virtual network should be in the correct RG.",
                    provisionedNetwork.id().contains(resourceGroupNameString));

            NetworkSecurityGroupInner networkSG = AzureTestUtil.getAzureSecurityGroup(
                    this.networkManagementClient, azureVMName,
                    AzureTestUtil.AZURE_SECURITY_GROUP_NAME);
            assertNotNull("Security Group with correct name and by correct RG should be obtained.",
                    networkSG);
            assertTrue("Created security group should be in the correct RG.",
                    provisionedNetwork.id().contains(resourceGroupNameString));
        } catch (Exception e) {
            fail("Unable to verify actually provisioned network on Azure");
        }
    }

    private void assertConfigurationOfDisks() {

        ComputeState vm = this.host.getServiceState(null,
                ComputeState.class, UriUtils.buildUri(this.host, this.vmState.documentSelfLink));
        String diskLink = vm.diskLinks.get(0);
        DiskState diskState = this.host.getServiceState(null, DiskState.class,
                UriUtils.buildUri(this.host, diskLink));
        assertEquals("OS Disk size does not match", AzureTestUtil.AZURE_CUSTOM_OSDISK_SIZE,
                diskState.capacityMBytes);

        if (this.isMock) { // return. Nothing to check on Azure.
            return;
        }

        try {
            VirtualMachineInner provisionedVM = AzureTestUtil.getAzureVirtualMachine(
                    this
                            .computeManagementClient,
                    azureVMName,
                    azureVMName);
            int OSDiskSizeInAzure = provisionedVM.storageProfile().osDisk().diskSizeGB();
            assertEquals("OS Disk size of the VM in azure does not match with the intended size",
                    AzureTestUtil
                            .AZURE_CUSTOM_OSDISK_SIZE,
                    OSDiskSizeInAzure * 1024);
        } catch (Exception e) {
            fail("Unable to verify OS Disk Size on Azure");
        }

    }

    /**
     * Ensure that after provisioning, there is one (but only one) StorageDescription
     * created for the shared storage account. This test does not use shared SA, thus
     * provisioning should create a StorageDescription.
     */
    private void assertStorageDescription() {
        if (this.isMock) { // return. Nothing provisioned on Azure so nothing to check
            return;
        }

        try {
            ComputeState vm = this.host.getServiceState(null,
                    ComputeState.class, UriUtils.buildUri(this.host, this.vmState.documentSelfLink));
            String sharedSAName = vm.customProperties.get(AzureConstants.AZURE_STORAGE_ACCOUNT_NAME);
            if (sharedSAName != null && !sharedSAName.isEmpty()) {
                Map<String, StorageDescription> storageDescriptionsMap =
                        ProvisioningUtils.<StorageDescription> getResourceStates(this.host,
                                StorageDescriptionService.FACTORY_LINK, StorageDescription.class);
                assertTrue(!storageDescriptionsMap.isEmpty());
                List<StorageDescription> storageDescriptions = storageDescriptionsMap.values().stream()
                        .filter(name -> name.equals(sharedSAName)).collect(Collectors.toList());
                assertEquals("More than one storage description was created for the provisinoed storage account.", storageDescriptions.size(), 1);
            }
        } catch (Throwable t) {
            fail("Unable to verify Storage Description documents");
            t.printStackTrace();
        }
    }
}
