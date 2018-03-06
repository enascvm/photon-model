/*
 * Copyright (c) 2015-2018 VMware, Inc. All Rights Reserved.
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

import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.STORAGE_CONNECTION_STRING;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.AZURE_RESOURCE_GROUP_LOCATION;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.DEFAULT_NIC_SPEC;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.IMAGE_REFERENCE;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createImageSource;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createVMResourceFromSpec;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.deleteVMs;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.generateName;

import java.util.List;
import java.util.stream.Collectors;

import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.resources.fluentcore.utils.SdkContext;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudPageBlob;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.PhotonModelMetricServices;
import com.vmware.photon.controller.model.adapters.azure.base.AzureBaseTest;
import com.vmware.photon.controller.model.adapters.util.instance.BaseComputeInstanceContext;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.xenon.common.UriUtils;


public class TestAzureDeleteTask extends AzureBaseTest {

    // Every test in addition might change it.
    private static String azureVMName = generateName("test_");

    private static String storageAccountName;

    private static String resourceGroupName;

    private ComputeService.ComputeState vmState;


    @Before
    public final void setupStorageAccount() throws Throwable {

        if (this.isMock) {
            return;
        }
        //Create Resource Group
        Azure azure = getAzureSdkClients().getAzureClient();
        resourceGroupName = SdkContext.randomResourceName(azureVMName, azureVMName.length() + 5);
        azure.resourceGroups().define(resourceGroupName)
                .withRegion(AZURE_RESOURCE_GROUP_LOCATION)
                .create();

        //Create Storage Account
        storageAccountName = azureVMName.replace("_", "") + "teststorageacc";
        azure.storageAccounts().define(storageAccountName)
                .withRegion(AZURE_RESOURCE_GROUP_LOCATION)
                .withExistingResourceGroup(resourceGroupName)
                .create();

    }

    /**
     * Creates a Azure unmanaged instance with persist options for disks. Checks if VHD blob cleanup happens according to the persist flag.
     */
    @Test
    public void testProvisionWithPersistentUnmanagedDisk() throws Throwable {
        BaseComputeInstanceContext.ImageSource imageSource = createImageSource(getHost(), this.endpointState, IMAGE_REFERENCE);

        // Create a Azure VM compute resource with 2 additional disks.
        int numberOfAdditionalDisks = 3;

        AzureTestUtil.VMResourceSpec vmResourceSpec = new AzureTestUtil.VMResourceSpec(getHost(), this.computeHost,
                this.endpointState, azureVMName)
                .withImageSource(imageSource)
                .withNicSpecs(DEFAULT_NIC_SPEC)
                .withNumberOfAdditionalDisks(numberOfAdditionalDisks)
                .withPersistentDisks(AzureTestUtil.VMResourceSpec.PersistentDisks.SOME)
                .withExistingStorageAccount(storageAccountName,resourceGroupName)
                .withManagedDisk(false);

        // create Azure VM compute resource.
        this.vmState = createVMResourceFromSpec(vmResourceSpec);

        kickOffProvisionTask();

        List<DiskService.DiskState> diskStates = this.vmState.diskLinks.stream()
                .map(diskLink -> getHost().getServiceState(
                        null, DiskService.DiskState.class, UriUtils.buildUri(getHost(), diskLink)))
                .collect(Collectors.toList());


        int computeStatesToRemain = 1;
        deleteVMs(getHost(), this.vmState.documentSelfLink, this.isMock,
                computeStatesToRemain);

        // assertions
        if (!this.isMock) {
            Azure az = getAzureSdkClients().getAzureClient();
            String key = az.storageAccounts().getByResourceGroup(resourceGroupName, storageAccountName).getKeys().get(0).value();
            CloudStorageAccount cloudStorageAccount = CloudStorageAccount.parse(
                    String.format(STORAGE_CONNECTION_STRING, storageAccountName, key));
            CloudBlobClient client = cloudStorageAccount.createCloudBlobClient();
            CloudBlobContainer container = client.getContainerReference("vhds");
            for (DiskService.DiskState diskState : diskStates) {
                String vhdBlobName = diskState.id.substring(diskState.id.lastIndexOf("/") + 1);
                CloudPageBlob blob = container.getPageBlobReference(vhdBlobName);
                assertEquals("Disk VHD blob should persist", diskState.persistent, blob.exists());
            }
        }

    }

    // kick off a provision task to do the actual VM creation
    private void kickOffProvisionTask() throws Throwable {

        ProvisionComputeTaskService.ProvisionComputeTaskState provisionTask = new ProvisionComputeTaskService.ProvisionComputeTaskState();

        provisionTask.computeLink = this.vmState.documentSelfLink;
        provisionTask.isMockRequest = this.isMock;
        provisionTask.taskSubStage = ProvisionComputeTaskService.ProvisionComputeTaskState.SubStage.CREATING_HOST;

        provisionTask = TestUtils.doPost(getHost(),
                provisionTask,
                ProvisionComputeTaskService.ProvisionComputeTaskState.class,
                UriUtils.buildUri(getHost(), ProvisionComputeTaskService.FACTORY_LINK));

        getHost().waitForFinishedTask(
                ProvisionComputeTaskService.ProvisionComputeTaskState.class,
                provisionTask.documentSelfLink);

        this.vmState = getHost().getServiceState(null,
                ComputeService.ComputeState.class,
                UriUtils.buildUri(getHost(), this.vmState.documentSelfLink));
    }




    @Override
    protected void startRequiredServices() throws Throwable {

        super.startRequiredServices();

        PhotonModelMetricServices.startServices(getHost());

        // TODO: VSYM-992 - improve test/fix arbitrary timeout
        getHost().setTimeoutSeconds(1200);
    }

    @After
    public void tearDown() throws Exception {

        if (this.isMock) {
            return;
        }

        //delete RG of storage account
        getAzureSdkClients().getAzureClient().resourceGroups().beginDeleteByName(resourceGroupName);
    }

}
