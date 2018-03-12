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

package com.vmware.photon.controller.model.adapters.azure.utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.PagedList;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.compute.VirtualMachine;
import com.microsoft.azure.management.resources.ResourceGroup;
import com.microsoft.azure.management.resources.ResourceGroups;
import org.junit.Before;
import org.junit.Test;

import com.vmware.xenon.common.BasicTestCase;
import com.vmware.xenon.common.Utils;

public class AzureRemoteCleanup extends BasicTestCase {
    // Configurable options for the test.
    public boolean isMock = true;
    public String clientID = "clientID";
    public String clientKey = "clientKey";
    public String subscriptionId = "subscriptionId";
    public String tenantId = "tenantId";

    public Azure azureClient;

    public static final String TIME_STAMP_TAG_KEY = "TIME_STAMP";
    public static String AZ_LRT_RESOURCE_GROUP_TAG = "az-lrt";
    public static String ENUMTEST_RESOURCE_GROUP_TAG = "enumtest-";


    private ApplicationTokenCredentials credentials;

    @Before
    public void setUp() throws Exception {
        try {
            this.credentials = new ApplicationTokenCredentials(
                    this.clientID, this.tenantId, this.clientKey, AzureEnvironment.AZURE);
            this.azureClient = Azure.configure()
                    .authenticate(this.credentials)
                    .withSubscription(this.subscriptionId);
        } catch (Throwable e) {
            throw new Exception(e);
        }
    }

    /**
     * Initiating a clean up of a resource group will clean the following resources:
     * 1) VM
     * 2) Storage Accounts
     * 3) Public IP
     * 4) Virtual Network
     * 5) Security groups (if used by other resources it will not be deleted )
     * 6) Nics (if used by other resources it will not be deleted )
     *
     * Note: Currently we are provisioning resources on Azure in the following two tests with the prefixes mentioned below:
     * 1) TestAzureLongRunningEnumeration: az-lrt-
     * 2) TestAzureEnumerationTask: enumtest-
     * We are cleaning up all the resources based on the above mentioned tag after an hour its created.
     */
    @Test
    public void cleanResourceGroups() throws IOException {

        ResourceGroups resourceGroups = this.azureClient.resourceGroups();
        List<ResourceGroup> resourceGroupsToBeDeleted = new ArrayList<>();
        try {
            resourceGroups.list().stream().forEach(resourceGroup -> {
                this.host.log(Level.INFO, "Client reading resource group name: %s in region: %s",
                        resourceGroup.name(), resourceGroup.regionName());
                if (resourceGroup.name().startsWith(AZ_LRT_RESOURCE_GROUP_TAG) || resourceGroup.name().startsWith(ENUMTEST_RESOURCE_GROUP_TAG)) {
                    // get the creation from the time_stamp tag value in the vm's tag
                    // if the resource is more than an hour old add the resource group to resourceGroupsToBeDeleted list
                    PagedList<VirtualMachine> virtualMachines = this.azureClient.virtualMachines().listByResourceGroup(resourceGroup.name());
                    virtualMachines.stream().forEach(vm -> {
                        if (vm.tags().containsKey(TIME_STAMP_TAG_KEY)) {
                            this.host.log(Level.INFO, "Virtual machine is tagged with: %s",
                                    vm.name());
                            long vmCreationTime = Long.valueOf(vm.tags().get(TIME_STAMP_TAG_KEY));
                            long timeDifference = Utils.getNowMicrosUtc() - vmCreationTime;
                            if (timeDifference > TimeUnit.HOURS.toMicros(1)) {
                                resourceGroupsToBeDeleted.add(resourceGroup);
                            }
                        } else {
                            this.host.log(Level.INFO,
                                    "Tag not found for Virtual Machine: %s, will perform tagging.",
                                    vm.name());
                            //if a vm does not have a time_stamp tag the update the vm with the current time
                            // And the next run of this script will delete this particular resource
                            vm.update()
                                    .withTag(TIME_STAMP_TAG_KEY, String.valueOf(Utils.getNowMicrosUtc()))
                                    .apply();
                        }
                    });
                }
            });
            resourceGroupsToBeDeleted.stream().forEach(resourceGroup -> {
                this.host.log(Level.INFO, "Terminating stale resource group: %s",
                        resourceGroup.name());
                this.azureClient.resourceGroups().deleteByName(resourceGroup.name());
                this.host.log("Terminated stale resource group: %s",
                        resourceGroup.name());
            });
        } catch (Exception ex) {
            this.host.log(Level.INFO, ex.getMessage());
        }
    }
}