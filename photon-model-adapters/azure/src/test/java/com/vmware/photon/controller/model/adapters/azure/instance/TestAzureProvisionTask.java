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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.DEFAULT_NIC_SPEC;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.NO_PUBLIC_IP_NIC_SPEC;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.SHARED_NETWORK_NIC_SPEC;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultResourceGroupState;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultVMResource;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createPrivateImageSource;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.deleteVMs;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.generateName;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.microsoft.azure.management.compute.implementation.VirtualMachineInner;
import com.microsoft.azure.management.network.implementation.NetworkManagementClientImpl;
import com.microsoft.azure.management.network.implementation.NetworkSecurityGroupInner;
import com.microsoft.azure.management.network.implementation.VirtualNetworkInner;
import com.microsoft.azure.management.resources.implementation.ResourceGroupInner;

import org.junit.After;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.PhotonModelMetricServices;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.base.AzureBaseTest;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.ResourceGroupStateType;
import com.vmware.photon.controller.model.adapters.util.instance.BaseComputeInstanceContext.ImageSource;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceStateWithDescription;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.StorageDescriptionService;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;

public class TestAzureProvisionTask extends AzureBaseTest {

    // Every test in addition might change it.
    private static String azureVMName = generateName("testProv-");

    public boolean skipStats = true;

    private ComputeState vmState;

    @Override
    protected void startRequiredServices() throws Throwable {

        super.startRequiredServices();

        PhotonModelMetricServices.startServices(getHost());

        // TODO: VSYM-992 - improve test/fix arbitrary timeout
        getHost().setTimeoutSeconds(1200);
    }

    @After
    public void tearDown() throws Exception {
        if (this.vmState != null) {
            try {
                getHost().log(Level.INFO, "%s: Deleting [%s] VM",
                        this.currentTestName.getMethodName(), this.vmState.name);

                // ONLY computeHost MUST remain!
                int computeStatesToRemain = 1;

                deleteVMs(getHost(), this.vmState.documentSelfLink, this.isMock,
                        computeStatesToRemain);

            } catch (Throwable deleteExc) {
                // just log and move on
                getHost().log(Level.WARNING, "%s: Deleting [%s] VM: FAILED. Details: %s",
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
        this.vmState = createDefaultVMResource(getHost(), azureVMName,
                this.computeHost, this.endpointState, DEFAULT_NIC_SPEC);

        kickOffProvisionTask();

        assertVmNetworksConfiguration();

        assertConfigurationOfDisks();

        assertStorageDescription();

        // Stats on individual VM is currently broken.
        if (!this.skipStats) {
            getHost().setTimeoutSeconds(60);
            getHost().waitFor("Error waiting for stats", () -> {
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
    public void testProvisionFromPrivateImage() throws Throwable {

        ImageSource privateImageSource = createPrivateImageSource(getHost(), this.endpointState);

        // create a Azure VM compute resource.
        this.vmState = createDefaultVMResource(getHost(), azureVMName,
                this.computeHost, this.endpointState, NO_PUBLIC_IP_NIC_SPEC,
                null /* networkRGLink */, privateImageSource);

        kickOffProvisionTask();

        assertConfigurationOfDisks();

        assertStorageDescription();
    }

    /**
     * Creates a Azure instance via a provision task.
     */
    @Test
    @Ignore("This test does an additional VM provisioning that will cause the total preflight "
            + "time to exceed the limit and timeout the preflight. Only for manual execution.")
    public void testProvisionNoPublicIP() throws Throwable {

        // create a Azure VM compute resource.
        this.vmState = createDefaultVMResource(getHost(), azureVMName,
                this.computeHost, this.endpointState, NO_PUBLIC_IP_NIC_SPEC);

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
        final ResourceGroupInner sharedNetworkRG = AzureTestUtil
                .createResourceGroupWithSharedNetwork(
                        getAzureSdkClients().getResourceManagementClientImpl(),
                        getAzureSdkClients().getNetworkManagementClientImpl(),
                        SHARED_NETWORK_NIC_SPEC);

        // Create corresponding ResourceGroupState
        ResourceGroupState sharedNetworkRGState = createDefaultResourceGroupState(
                getHost(), sharedNetworkRG.name(), this.computeHost,
                this.endpointState,
                ResourceGroupStateType.AzureResourceGroup);

        // END of prepare phase.

        // In this scenario mark the VM name (and its corresponding RG) with "-withSharedNW"
        String vmName = azureVMName + "-withSharedNW";

        // create a Azure VM compute resource
        this.vmState = createDefaultVMResource(getHost(), vmName,
                this.computeHost, this.endpointState,
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

        provisionTask = TestUtils.doPost(getHost(),
                provisionTask,
                ProvisionComputeTaskState.class,
                UriUtils.buildUri(getHost(), ProvisionComputeTaskService.FACTORY_LINK));

        getHost().waitForFinishedTask(
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
        Operation startOp = Operation.createPost(UriUtils.buildUri(getHost(), servicePath));
        getHost().startService(startOp, parentService);
        ComputeStatsRequest statsRequest = new ComputeStatsRequest();
        statsRequest.resourceReference = UriUtils.buildUri(getHost(), vm.documentSelfLink);
        statsRequest.isMockRequest = this.isMock;
        statsRequest.taskReference = UriUtils.buildUri(getHost(), servicePath);
        getHost().sendAndWait(Operation.createPatch(UriUtils.buildUri(
                getHost(), AzureUriPaths.AZURE_STATS_ADAPTER))
                .setBody(statsRequest)
                .setReferer(getHost().getUri()));
    }

    private void assertVmNetworksConfiguration() throws Throwable {

        // This assert is only suitable for real (non-mocking env).
        if (this.isMock) {
            return;
        }

        getHost().log(Level.INFO, "%s: Assert network configuration for [%s] VM",
                this.currentTestName.getMethodName(), this.vmState.name);

        ComputeState vm = getHost().getServiceState(null,
                ComputeState.class,
                UriUtils.buildUri(getHost(), this.vmState.documentSelfLink));

        NetworkInterfaceStateWithDescription primaryNicState = getHost().getServiceState(null,
                NetworkInterfaceStateWithDescription.class,
                NetworkInterfaceStateWithDescription.buildUri(
                        UriUtils.buildUri(getHost(), vm.networkInterfaceLinks.get(0))));

        assertNotNull("Primary NIC private IP should be set.", primaryNicState.address);
        if (primaryNicState.description.assignPublicIpAddress == null ||
                primaryNicState.description.assignPublicIpAddress == Boolean.TRUE) {
            assertNotNull("VM address should be set.", vm.address);
            assertNotEquals("VM address should not be the same as primary NIC private IP.",
                    vm.address,
                    primaryNicState.address);
        } else {
            assertNull("VM address should be empty.", vm.address);
        }

        assertNotNull("Primary NIC security group should be set.",
                primaryNicState.securityGroupLinks != null);

        for (int i = 1; i < vm.networkInterfaceLinks.size(); i++) {
            NetworkInterfaceState nonPrimaryNicState = getHost().getServiceState(null,
                    NetworkInterfaceState.class,
                    UriUtils.buildUri(getHost(), vm.networkInterfaceLinks.get(i)));

            assertNotNull("Non-primary NIC" + i + " IP should not be set to the privatese ip.",
                    nonPrimaryNicState.address);
            assertNull("Non-primary NIC" + i + " security group should not be set.",
                    nonPrimaryNicState.securityGroupLinks);
        }

        // Ensure that from the list of provided network resource groups,
        // and security group resource groups, the one with the correct type has been chosen.
        // For network, and sg, the correct RG is the default one named over the azureVMName.
        // Verifying the resources can be obtained from this RG, ensures they have been placed
        // correctly.
        NetworkManagementClientImpl networkClient = getAzureSdkClients()
                .getNetworkManagementClientImpl();

        final String vmRGName = vm.customProperties.get(ComputeProperties.RESOURCE_GROUP_NAME);

        VirtualNetworkInner provisionedNetwork = AzureTestUtil.getAzureVirtualNetwork(
                networkClient, vmRGName, AzureTestUtil.AZURE_NETWORK_NAME);

        assertNotNull("Azure virtual network object '" + vmRGName + "/"
                + AzureTestUtil.AZURE_NETWORK_NAME + "' is not found.",
                provisionedNetwork);

        NetworkSecurityGroupInner provisionedSG = AzureTestUtil.getAzureSecurityGroup(
                networkClient, vmRGName, AzureTestUtil.AZURE_SECURITY_GROUP_NAME);

        assertNotNull("Azure security group object '" + vmRGName + "/"
                + AzureTestUtil.AZURE_SECURITY_GROUP_NAME + "' is not found.",
                provisionedSG);
    }

    private void assertConfigurationOfDisks() {

        ComputeState vm = getHost().getServiceState(null,
                ComputeState.class, UriUtils.buildUri(getHost(), this.vmState.documentSelfLink));

        final String vmRGName = vm.customProperties.get(ComputeProperties.RESOURCE_GROUP_NAME);

        String diskLink = vm.diskLinks.get(0);
        DiskState diskState = getHost().getServiceState(null, DiskState.class,
                UriUtils.buildUri(getHost(), diskLink));
        assertEquals("OS Disk size does not match", AzureTestUtil.AZURE_CUSTOM_OSDISK_SIZE,
                diskState.capacityMBytes);

        if (this.isMock) { // return. Nothing to check on Azure.
            return;
        }

        int OSDiskSizeInAzure = 0;
        try {
            VirtualMachineInner provisionedVM = AzureTestUtil.getAzureVirtualMachine(
                    getAzureSdkClients().getComputeManagementClientImpl(),
                    vmRGName,
                    azureVMName);
            OSDiskSizeInAzure = provisionedVM.storageProfile().osDisk().diskSizeGB();
        } catch (Exception e) {
            fail("Unable to verify OS Disk Size on Azure. Details: " + e.getMessage());
        }
        assertEquals("OS Disk size of the VM in azure does not match with the intended size",
                AzureTestUtil.AZURE_CUSTOM_OSDISK_SIZE, OSDiskSizeInAzure * 1024);
    }

    /**
     * Ensure that after provisioning, there is one (but only one) StorageDescription created for
     * the shared storage account. This test does not use shared SA, thus provisioning should create
     * a StorageDescription.
     */
    private void assertStorageDescription() {
        if (this.isMock) { // return. Nothing provisioned on Azure so nothing to check
            return;
        }

        try {
            ComputeState vm = getHost().getServiceState(null,
                    ComputeState.class,
                    UriUtils.buildUri(getHost(), this.vmState.documentSelfLink));
            String sharedSAName = vm.customProperties
                    .get(AzureConstants.AZURE_STORAGE_ACCOUNT_NAME);
            if (sharedSAName != null && !sharedSAName.isEmpty()) {
                Map<String, StorageDescription> storageDescriptionsMap = ProvisioningUtils
                        .<StorageDescription> getResourceStates(getHost(),
                                StorageDescriptionService.FACTORY_LINK, StorageDescription.class);
                assertTrue(!storageDescriptionsMap.isEmpty());
                List<StorageDescription> storageDescriptions = storageDescriptionsMap.values()
                        .stream()
                        .filter(name -> name.equals(sharedSAName)).collect(Collectors.toList());
                assertEquals(
                        "More than one storage description was created for the provisinoed storage account.",
                        storageDescriptions.size(), 1);
            }
        } catch (Throwable t) {
            fail("Unable to verify Storage Description documents");
            t.printStackTrace();
        }
    }
}
