/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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
import static org.junit.Assert.assertNull;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import com.microsoft.azure.management.compute.Disk;
import com.microsoft.azure.management.compute.StorageAccountTypes;
import com.microsoft.azure.management.resources.fluentcore.arm.Region;
import com.microsoft.azure.management.resources.fluentcore.utils.SdkContext;

import org.junit.After;
import org.junit.Test;

import com.vmware.photon.controller.model.adapters.azure.base.AzureBaseTest;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.tasks.ProvisionDiskTaskService;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class AzureDiskServiceTest extends AzureBaseTest {

    public static final String DISK_NAME_PREFIX = "azuredisk";
    public static final long DISK_SIZE = 25 * 1024; // 25 GiBs

    private DiskService.DiskState diskState;


    /**
     * Creates a Azure Disk and attempts to delete it.
     */
    @Test
    public void testDiskDelete() throws Throwable {

        createDiskStateDesc();

        kickOffDiskProvisioning();

        assertDiskProperties();

        kickOffDiskDeletion();

        assertDiskDeletion();

    }

    private void assertDiskProperties() {

        // Get updated disk state
        refreshDiskState();

        assertEquals("Disk status not matching", this.diskState.status, DiskService.DiskStatus
                .AVAILABLE);

        if (!this.isMock) {
            // Get provisioned disk from azure
            Disk provisionedAzureDisk = getAzureSdkClients().getComputeManager().disks().getById
                    (this.diskState.id);

            // Assertions
            assertEquals("Disk name not matching", this.diskState.name, provisionedAzureDisk.name());
            assertEquals("Disk capacity not matching", this.diskState.capacityMBytes, provisionedAzureDisk
                    .sizeInGB() * 1024);
            assertEquals("Disk Region not matching", this.diskState.regionId, provisionedAzureDisk.regionName());
            // assertEquals("Disk Type not matching", this.diskState.customProperties.get
            //                 (AzureConstants.AZURE_MANAGED_DISK_TYPE), provisionedAzureDisk.inner
            //         ().managedBy().accountType().toString());
        }
    }

    private void assertDiskDeletion() {

        if (!this.isMock) {
            // Get provisioned disk from azure
            Disk deletedAzureDisk = getAzureSdkClients().getComputeManager().disks().getById
                    (this.diskState.id);
            assertNull(deletedAzureDisk);
        }


    }

    private void kickOffDiskProvisioning() throws Throwable {

        // start provision task to do the actual disk creation
        ProvisionDiskTaskService.ProvisionDiskTaskState provisionTask = new ProvisionDiskTaskService.ProvisionDiskTaskState();
        provisionTask.taskSubStage = ProvisionDiskTaskService.ProvisionDiskTaskState.SubStage.CREATING_DISK;

        provisionTask.diskLink = this.diskState.documentSelfLink;
        provisionTask.isMockRequest = this.isMock;

        provisionTask.documentExpirationTimeMicros =
                Utils.getNowMicrosUtc() + TimeUnit.MINUTES.toMicros(
                        20);
        provisionTask.tenantLinks = this.endpointState.tenantLinks;

        provisionTask = TestUtils.doPost(this.host,
                provisionTask, ProvisionDiskTaskService.ProvisionDiskTaskState.class,
                UriUtils.buildUri(this.host, ProvisionDiskTaskService.FACTORY_LINK));

        this.host.waitForFinishedTask(ProvisionDiskTaskService.ProvisionDiskTaskState.class,
                provisionTask.documentSelfLink);
    }

    private void kickOffDiskDeletion() throws Throwable {

        // start provision task to do the disk deletion
        ProvisionDiskTaskService.ProvisionDiskTaskState provisionTask = new ProvisionDiskTaskService.ProvisionDiskTaskState();
        provisionTask.taskSubStage = ProvisionDiskTaskService.ProvisionDiskTaskState.SubStage.DELETING_DISK;

        provisionTask.diskLink = this.diskState.documentSelfLink;
        provisionTask.isMockRequest = this.isMock;

        provisionTask.documentExpirationTimeMicros =
                Utils.getNowMicrosUtc() + TimeUnit.MINUTES.toMicros(
                        20);
        provisionTask.tenantLinks = this.endpointState.tenantLinks;

        provisionTask = TestUtils.doPost(this.host,
                provisionTask, ProvisionDiskTaskService.ProvisionDiskTaskState.class,
                UriUtils.buildUri(this.host, ProvisionDiskTaskService.FACTORY_LINK));

        this.host.waitForFinishedTask(ProvisionDiskTaskService.ProvisionDiskTaskState.class,
                provisionTask.documentSelfLink);
    }

    private void createDiskStateDesc() throws Throwable {

        DiskService.DiskState diskDesc = new DiskService.DiskState();
        diskDesc.name = SdkContext.randomResourceName(DISK_NAME_PREFIX, DISK_NAME_PREFIX.length
                () + 5);
        diskDesc.capacityMBytes = DISK_SIZE;
        diskDesc.regionId = Region.US_WEST.toString();
        diskDesc.endpointLink = endpointState.documentSelfLink;
        diskDesc.tenantLinks = endpointState.tenantLinks;
        diskDesc.authCredentialsLink = endpointState.authCredentialsLink;

        diskDesc.diskAdapterReference = UriUtils.buildUri(host, AzureDiskService.SELF_LINK);

        diskDesc.customProperties = new HashMap<>();
        diskDesc.customProperties.put(AzureConstants.AZURE_MANAGED_DISK_TYPE, StorageAccountTypes
                .STANDARD_LRS.toString());
        // Uncomment below to create the disk in a specific ResourceGroup
        // diskDesc.customProperties.put(AzureConstants.AZURE_RESOURCE_GROUP_NAME, "SpecifyYourRG");

        this.diskState = TestUtils.doPost(host, diskDesc, DiskService
                        .DiskState.class, UriUtils.buildUri(host, DiskService.FACTORY_LINK));

    }


    @After
    public final void doDelete() {

        // Try to delete the Azure Disk RG
        String resourceDroupToDelete = "";
        try {
            if (!this.isMock) {

                resourceDroupToDelete = this.diskState.customProperties.get(AzureConstants
                        .AZURE_RESOURCE_GROUP_NAME);
                getAzureSdkClients().getComputeManager().resourceManager().resourceGroups()
                        .deleteByName(resourceDroupToDelete);
                getHost().log(Level.INFO,
                        "[" + this.currentTestName.getMethodName() + "] Deleted resource group "
                                + "[" + resourceDroupToDelete + "]");
            }
        } catch (Exception ex) {
            getHost().log(Level.WARNING,
                    "[" + this.currentTestName.getMethodName() + "] unable to delete resource "
                            + "group " + "[" + resourceDroupToDelete + "] " + ex.getMessage());

        }

        // Try to delete local disk state
        try {
            AzureTestUtil.deleteServiceDocument(getHost(), this.diskState.documentSelfLink);
            getHost().log(Level.INFO,
                    "[" + this.currentTestName.getMethodName() + "] Deleted local disk state "
                            + "[" + this.diskState.name + "]");
        } catch (Throwable throwable) {
            getHost().log(Level.WARNING,
                    "[" + this.currentTestName.getMethodName() + "] unable to delete local disk state "
                            + "[" + this.diskState.name + "]");
        }



    }

    private void refreshDiskState() {

        this.diskState = getHost().getServiceState(null,
                DiskService.DiskState.class, UriUtils.buildUri(getHost(), this.diskState.documentSelfLink));
    }
}
