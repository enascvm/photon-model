/*
 * Copyright (c) 2017-2018 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.model.adapters.azure.enumeration;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import com.microsoft.azure.management.compute.DataDisk;
import com.microsoft.azure.management.compute.Disk;
import com.microsoft.azure.management.compute.KnownLinuxVirtualMachineImage;
import com.microsoft.azure.management.compute.VirtualMachine;
import com.microsoft.azure.management.resources.fluentcore.arm.Region;
import com.microsoft.azure.management.resources.fluentcore.utils.SdkContext;

import org.junit.After;
import org.junit.Test;

import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.azure.base.AzureBaseTest;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil;
import com.vmware.photon.controller.model.adapters.util.ComputeEnumerateAdapterRequest;
import com.vmware.photon.controller.model.adapters.util.TagsUtil;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;
import com.vmware.xenon.common.UriUtils;

/**
 * Test to check Azure managed disks enumeration
 */
public class AzureDiskEnumerationServiceTest extends AzureBaseTest {

    public static final String RESOURCE_GROUP_NAME = "DiskEnumIT";

    private String resourceGroup;

    private Disk createDisk(String name) {
        this.resourceGroup = SdkContext.randomResourceName(RESOURCE_GROUP_NAME,
                RESOURCE_GROUP_NAME.length() + 5);
        Disk disk = getAzureSdkClients().getComputeManager()
                .disks()
                .define(name)
                .withRegion(Region.US_WEST)
                .withNewResourceGroup(this.resourceGroup)
                .withData()
                .withSizeInGB(5)
                .create();
        return disk;
    }

    private ComputeEnumerateResourceRequest kickOffEnumeration() throws Throwable {
        ComputeEnumerateResourceRequest resourceRequest = new ComputeEnumerateResourceRequest();
        resourceRequest.endpointLink = this.endpointState.documentSelfLink;
        resourceRequest.enumerationAction = EnumerationAction.START;
        resourceRequest.adapterManagementReference = UriUtils
                .buildUri(AzureEnumerationAdapterService.SELF_LINK);
        resourceRequest.resourcePoolLink = this.resourcePool.documentSelfLink;
        resourceRequest.resourceReference = UriUtils.buildUri(getHost(), "");
        resourceRequest.isMockRequest = this.isMock;

        return resourceRequest;
    }

    @Test
    public void testManagedDiskEnumeration() throws Throwable {
        // Exit if it is mock
        if (this.isMock) {
            return;
        }

        // create a disk in Azure
        Disk disk = createDisk("disk");
        assertNotNull(disk);

        ComputeEnumerateResourceRequest resourceRequest = kickOffEnumeration();
        ComputeEnumerateAdapterRequest request = new ComputeEnumerateAdapterRequest(resourceRequest,
                this.authState, this.computeHost);

        // Run disk enumeration
        patchServiceSynchronously(AzureDiskEnumerationAdapterService.SELF_LINK, request);

        // Fetch disks from local store to verify if diskState is created after enumeration
        Map<String, DiskState> diskStateMap = ProvisioningUtils
                .getResourceStates(getHost(), DiskService.FACTORY_LINK, DiskState.class);

        assertTrue("Newly created disk state is not found.", diskStateMap.keySet().stream()
                .anyMatch(s -> s.equalsIgnoreCase(disk.id())));

        // verify internal tag links
        DiskState createdDisk = diskStateMap.entrySet().stream()
                .filter(en -> en.getKey().equalsIgnoreCase(disk.id()))
                .findFirst().get().getValue();
        assertNotNull(createdDisk.tagLinks);
        TagState typeTag = TagsUtil.newTagState(PhotonModelConstants.TAG_KEY_TYPE,
                AzureConstants.AzureResourceType.azure_managed_disk.toString(),
                false, this.computeHost.tenantLinks);
        assertTrue("internal tag not found",
                createdDisk.tagLinks.stream()
                        .anyMatch(s -> s.equalsIgnoreCase(typeTag.documentSelfLink)));
        // verify regionId
        assertNotNull("regionId not found", createdDisk.regionId);

        // Delete disk from Azure
        getAzureSdkClients().getComputeManager()
                .disks()
                .deleteById(disk.id());

        // Run enumeration to sync local disk states with disks in Azure
        patchServiceSynchronously(AzureDiskEnumerationAdapterService.SELF_LINK, request);

        // Need to wait for completion of patch operation on disk states
        TimeUnit.SECONDS.sleep(5);

        // After sync, query local disk states to verify endpoint links of disk state are disassociated
        diskStateMap = ProvisioningUtils
                .getResourceStates(getHost(), DiskService.FACTORY_LINK, DiskState.class);
        diskStateMap.values().forEach(diskState -> {
            if (diskState.id.equalsIgnoreCase(disk.id())) {
                assertTrue("Endpoint link must be null", diskState.endpointLink.isEmpty());
                assertTrue("Endpoint links in DiskState must be empty", diskState.endpointLinks.isEmpty());
            }
        });

    }

    @Test
    public void testVMAndDiskEnumeration() throws Throwable {
        // Exit if it is mock
        if (this.isMock) {
            return;
        }
        this.resourceGroup = SdkContext.randomResourceName(RESOURCE_GROUP_NAME,
                RESOURCE_GROUP_NAME.length() + 5);
        // Create a vm with one additional disk
        VirtualMachine vm = getAzureSdkClients().getComputeManager()
                .virtualMachines()
                .define("TestVM")
                .withRegion(Region.US_WEST)
                .withNewResourceGroup(this.resourceGroup)
                .withNewPrimaryNetwork("10.0.0.0/28")
                .withPrimaryPrivateIPAddressDynamic()
                .withoutPrimaryPublicIPAddress()
                .withPopularLinuxImage(KnownLinuxVirtualMachineImage.UBUNTU_SERVER_16_04_LTS)
                .withRootUsername(AzureTestUtil.AZURE_ADMIN_USERNAME)
                .withRootPassword(AzureTestUtil.AZURE_ADMIN_PASSWORD)
                .withNewDataDisk(5)
                .create();
        assertNotNull(vm);
        DataDisk dataDisk = vm.storageProfile().dataDisks().get(0);
        assertNotNull(dataDisk);

        ComputeEnumerateResourceRequest resourceRequest = kickOffEnumeration();
        ComputeEnumerateAdapterRequest request = new ComputeEnumerateAdapterRequest(resourceRequest,
                this.authState, this.computeHost);
        // Run VM enumeration
        patchServiceSynchronously(AzureComputeEnumerationAdapterService.SELF_LINK, request);
        // Run disk enumeration
        patchServiceSynchronously(AzureDiskEnumerationAdapterService.SELF_LINK, request);

        // Verify disk state is created in local store
        Map<String, DiskState> diskStateMap = ProvisioningUtils
                .getResourceStates(getHost(), DiskService.FACTORY_LINK, DiskState.class);
        assertTrue(diskStateMap.keySet().stream().anyMatch(s ->
                s.equalsIgnoreCase(dataDisk.managedDisk().id())));

        // Detach disk
        vm.update()
                .withoutDataDisk(dataDisk.lun()).apply();
        // Run Disk enumeration
        patchServiceSynchronously(AzureDiskEnumerationAdapterService.SELF_LINK, request);
        // Verify if the status of disk state is updated to Available state or not
        diskStateMap = ProvisioningUtils
                .getResourceStates(getHost(), DiskService.FACTORY_LINK, DiskState.class);
        diskStateMap.values().forEach(diskState -> {
            if (diskState.name.equalsIgnoreCase(dataDisk.name())) {
                assertTrue("Status of disk state should be Available",
                        diskState.status.equals(DiskService.DiskStatus.AVAILABLE));
            }
        });

    }

    @After
    public void tearDown() throws Throwable {
        if (!this.isMock) {
            try {
                // Delete entire resource group
                getAzureSdkClients().getComputeManager()
                        .resourceManager()
                        .resourceGroups()
                        .deleteByName(this.resourceGroup);
            } catch (Exception deleteEx) {
                this.host.log(Level.WARNING, "Exception deleting resource group from Azure %s ", deleteEx.getMessage());
            }
        }
    }

}
