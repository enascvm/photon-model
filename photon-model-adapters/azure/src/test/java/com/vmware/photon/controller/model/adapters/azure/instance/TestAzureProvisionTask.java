/*
 * Copyright (c) 2015-2017 VMware, Inc. All Rights Reserved.
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

import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.DISK_CONTROLLER_NUMBER;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.AZURE_CUSTOM_DATA_DISK_SIZE;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.DEFAULT_NIC_SPEC;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.IMAGE_REFERENCE;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.NO_PUBLIC_IP_NIC_SPEC;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.PRIVATE_IP_NIC_SPEC;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.SHARED_NETWORK_NIC_SPEC;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultResourceGroupState;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultVMResource;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createPrivateImageSource;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createVMResourceFromSpec;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.deleteVMs;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.generateName;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.microsoft.azure.management.compute.DataDisk;
import com.microsoft.azure.management.compute.OSDisk;
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
import com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.AzureNicSpecs;
import com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.AzureNicSpecs.NicSpec;
import com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.VMResourceSpec;
import com.vmware.photon.controller.model.adapters.util.instance.BaseComputeInstanceContext.ImageSource;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.IpAssignment;
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
    private static String azureVMName = generateName("test_");

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

        VMResourceSpec vmResourceSpec = new VMResourceSpec(getHost(), this.computeHost,
                this.endpointState, azureVMName)
                .withNicSpecs(DEFAULT_NIC_SPEC)
                .withImageReferenceId(IMAGE_REFERENCE);

        // create Azure VM compute resource.
        this.vmState = createVMResourceFromSpec(vmResourceSpec);

        kickOffProvisionTask();

        assertVmNetworksConfiguration(DEFAULT_NIC_SPEC);

        assertConfigurationOfDisks(0,0);

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
     * Creates a Azure instance with additional disk via a provision task.
     */
    @Test
    @Ignore("This test does VM provisioning with additional disks. Ignored for timeouts of "
            + "preflights")
    public void testProvisionWithDataDisks() throws Throwable {

        // Create a Azure VM compute resource with 2 additional disks.
        int numberOfAdditionalDisks = 2;

        VMResourceSpec vmResourceSpec = new VMResourceSpec(getHost(), this.computeHost,
                this.endpointState, azureVMName)
                .withImageReferenceId(IMAGE_REFERENCE)
                .withNicSpecs(DEFAULT_NIC_SPEC)
                .withNumberOfAdditionalDisks(numberOfAdditionalDisks);

        // create Azure VM compute resource.
        this.vmState = createVMResourceFromSpec(vmResourceSpec);

        kickOffProvisionTask();

        // Assert if 2 additional disks were created
        assertConfigurationOfDisks(numberOfAdditionalDisks, 0);
    }

    /**
     * Creates a Azure instance via a provision task.
     */
    @Test
    @Ignore("This test does an additional VM provisioning that will cause the total preflight "
            + "time to exceed the limit and timeout the preflight. Only for manual execution.")
    public void testProvisionFromPrivateImage() throws Throwable {

        ImageSource privateImageSource = createPrivateImageSource(getHost(), this.endpointState);

        int numberOfAdditionalDisks = 1;
        // create a Azure VM compute resource.
        this.vmState = createDefaultVMResource(getHost(), azureVMName,
                this.computeHost, this.endpointState, NO_PUBLIC_IP_NIC_SPEC,
                null /* networkRGLink */, privateImageSource, numberOfAdditionalDisks);

        kickOffProvisionTask();

        assertConfigurationOfDisks(numberOfAdditionalDisks, 2);

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

        assertVmNetworksConfiguration(NO_PUBLIC_IP_NIC_SPEC);
    }

    /**
     * Creates Azure instance that uses shared/existing Network via a provision task.
     * <p>
     * It duplicates {@link #testProvision()} and just points to an external/shared Network.
     */
    @Test
    @Ignore("Since azure build timeouts due to the time consuming provision-decommission VM "
            + "executed by the tests. So far we sacrifice this test.")
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
        String vmName = azureVMName + "-SharedNW";

        // create a Azure VM compute resource
        this.vmState = createDefaultVMResource(getHost(), vmName,
                this.computeHost, this.endpointState,
                DEFAULT_NIC_SPEC,
                // In addition to standard provisioning pass the RG of the shared network
                sharedNetworkRGState.documentSelfLink);

        kickOffProvisionTask();

        assertVmNetworksConfiguration(DEFAULT_NIC_SPEC);
    }

    /**
     * Creates a Azure instance via a provision task.
     */
    @Test
    @Ignore("This test does an additional VM provisioning that will cause the total preflight "
            + "time to exceed the limit and timeout the preflight. Only for manual execution.")
    public void testProvisionWitPrivateIP() throws Throwable {

        // create a Azure VM compute resource.
        this.vmState = createDefaultVMResource(this.host, azureVMName,
                computeHost, endpointState, PRIVATE_IP_NIC_SPEC);

        kickOffProvisionTask();

        assertVmNetworksConfiguration(PRIVATE_IP_NIC_SPEC);
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

        this.vmState = getHost().getServiceState(null,
                ComputeState.class,
                UriUtils.buildUri(getHost(), this.vmState.documentSelfLink));
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

    private void assertVmNetworksConfiguration(AzureNicSpecs azureNicSpec) throws Throwable {

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

        // In case that private ip is set explicitly.
        assertStaticPrivateIPAddress(azureNicSpec, primaryNicState.address);

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

    private void assertConfigurationOfDisks(int numberOfAdditionalDisks, int
            numberOfDataDisksOnImage) {

        ComputeState vm = getHost().getServiceState(null,
                ComputeState.class, UriUtils.buildUri(getHost(), this.vmState.documentSelfLink));

        List<DiskState> diskStates = vm.diskLinks.stream()
                .map(diskLink -> getHost().getServiceState(
                        null, DiskState.class, UriUtils.buildUri(getHost(), diskLink)))
                .collect(Collectors.toList());

        if (numberOfDataDisksOnImage == 0) {
            for (DiskState diskState : diskStates) {
                if (diskState.bootOrder == 1) {
                    assertEquals("OS Disk size does not match", AzureTestUtil.AZURE_CUSTOM_OSDISK_SIZE,
                            diskState.capacityMBytes);
                } else {

                    assertEquals("Data Disk size does not match",
                            AzureTestUtil.AZURE_CUSTOM_DATA_DISK_SIZE, diskState.capacityMBytes);
                    assertNotNull(diskState.customProperties);
                    assertNotNull(diskState.customProperties.get(DISK_CONTROLLER_NUMBER));
                }
            }
        }


        if (this.isMock) { // return. Nothing to check on Azure.
            return;
        }

        final String vmRGName = vm.customProperties.get(ComputeProperties.RESOURCE_GROUP_NAME);

        VirtualMachineInner provisionedVM = null;
        try {
            provisionedVM = AzureTestUtil.getAzureVirtualMachine(
                    getAzureSdkClients().getComputeManagementClientImpl(),
                    vmRGName,
                    this.vmState.name.replace('_', '-'));
        } catch (Exception e) {
            fail("Unable to get Azure VM details: " + e.getMessage());
        }

        final Function<String, Optional<DiskState>> findDiskStateByName = diskName -> diskStates
                .stream()
                .filter(dS -> diskName.equals(dS.name))
                .findFirst();

        // Validate boot DiskState against Azure osDisk
        {
            final OSDisk azureOsDisk = provisionedVM.storageProfile().osDisk();
            Optional<DiskState> bootDiskOpt = findDiskStateByName.apply(azureOsDisk.name());

            if (bootDiskOpt.isPresent()) {
                final DiskState bootDiskState = bootDiskOpt.get();
                assertNotNull("Azure OS Disk with name '" + azureOsDisk.name()
                        + "' does not match any DiskState by name", bootDiskState);

                if (bootDiskState.customProperties != null && bootDiskState.customProperties
                        .containsKey(AzureConstants.AZURE_MANAGED_DISK_TYPE)) {
                    assertEquals("Boot DiskState.id does not match Azure managed disk id",
                            azureOsDisk.managedDisk().id(), bootDiskState.id);
                } else {
                    assertEquals("Boot DiskState.id does not match Azure.osDisk.vhd.uri",
                            azureOsDisk.vhd().uri(), bootDiskState.id);
                }

                assertEquals("OS Disk size of the VM in azure does not match with the intended size",
                        AzureTestUtil.AZURE_CUSTOM_OSDISK_SIZE, azureOsDisk.diskSizeGB() * 1024);
            } else {
                fail("Mismatch in boot disk name.");
            }

        }

        // Validate data DiskStates against Azure dataDisks

        for (DataDisk azureDataDisk : provisionedVM.storageProfile().dataDisks()) {

            Optional<DiskState> dataDiskOpt = findDiskStateByName.apply(azureDataDisk.name());

            if (dataDiskOpt.isPresent()) {
                DiskState dataDiskState = dataDiskOpt.get();
                assertNotNull("Azure Data Disk with name '" + azureDataDisk.name()
                        + "' does not match any DiskState by name", dataDiskState);

                if (dataDiskState.customProperties != null && dataDiskState.customProperties
                        .containsKey(AzureConstants.AZURE_MANAGED_DISK_TYPE)) {
                    assertEquals("Boot DiskState.id does not match Azure managed disk id.",
                            azureDataDisk.managedDisk().id(), dataDiskState.id);
                } else {
                    assertEquals("Boot DiskState.id does not match Azure.osDisk.vhd.uri",
                            azureDataDisk.vhd().uri(), dataDiskState.id);
                }

                // assert size of each of the attached disks only in case of public image
                if (numberOfDataDisksOnImage == 0) {
                    assertEquals("Mismatch in intended size of data disks " + azureDataDisk.name(),
                            AZURE_CUSTOM_DATA_DISK_SIZE, azureDataDisk.diskSizeGB().longValue() * 1024);

                }

                assertEquals("LUN of DiskState does not match Azure.dataDisk.lun",
                        String.valueOf(azureDataDisk.lun()), dataDiskState.customProperties.get(
                        DISK_CONTROLLER_NUMBER));

            } else {
                fail("Data Disks not found.");
            }
        }

        assertEquals("Mismatch in number of data disks found on VM in azure",
                numberOfAdditionalDisks + numberOfDataDisksOnImage, provisionedVM.storageProfile().dataDisks().size());

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

    private void assertStaticPrivateIPAddress(AzureNicSpecs azureNicSpec, String privateIp) {
        if (azureNicSpec != null) {
            Optional<NicSpec> nicWithStaticIp = azureNicSpec.nicSpecs.stream()
                    .filter(nic -> nic.getIpAssignment() == IpAssignment.STATIC)
                    .findFirst();
            if (nicWithStaticIp.isPresent()) {
                // This is handled by testProvisionWithPrivateIp()
                assertEquals(privateIp, nicWithStaticIp.get().ip());
            }
        }
    }
}
