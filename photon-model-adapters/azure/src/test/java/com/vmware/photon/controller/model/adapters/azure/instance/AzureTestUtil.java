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

import static com.vmware.photon.controller.model.ComputeProperties.RESOURCE_GROUP_NAME;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_OSDISK_CACHING;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNTS;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNT_KEY1;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNT_KEY2;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_CONTAINERS;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_CONTAINER_LEASE_LAST_MODIFIED;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_CONTAINER_LEASE_STATE;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_CONTAINER_LEASE_STATUS;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_TYPE;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_TENANT_ID;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.DEFAULT_DISK_CAPACITY;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.DEFAULT_DISK_SERVICE_REFERENCE;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.DEFAULT_DISK_TYPE;
import static com.vmware.photon.controller.model.adapters.azure.enumeration.AzureStorageEnumerationAdapterService.FIELD_COMPUTE_HOST_LINK;
import static com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ENVIRONMENT_NAME_AZURE;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import com.microsoft.azure.management.compute.ComputeManagementClient;
import com.microsoft.azure.management.compute.models.VirtualMachine;
import com.microsoft.rest.ServiceResponse;

import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.enumeration.AzureComputeEnumerationAdapterService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.DiskService.DiskType;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.IpAssignment;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceGroupService;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.StorageDescriptionService;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;
import com.vmware.photon.controller.model.tasks.ResourceRemovalTaskService;
import com.vmware.photon.controller.model.tasks.ResourceRemovalTaskService.ResourceRemovalTaskState;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;

public class AzureTestUtil {
    public static final String AZURE_ADMIN_USERNAME = "azureuser";
    public static final String AZURE_ADMIN_PASSWORD = "Pa$$word1";
    public static final String AZURE_VM_SIZE = "Standard_A3";
    public static final String IMAGE_REFERENCE = "Canonical:UbuntuServer:14.04.3-LTS:latest";
    public static final String AZURE_RESOURCE_GROUP_LOCATION = "westus";
    public static final String AZURE_STORAGE_ACCOUNT_NAME = "storage";
    public static final String AZURE_STORAGE_ACCOUNT_TYPE = "Standard_RAGRS";
    public static final String AZURE_NETWORK_CIDR = "192.168.100.0/22";
    public static final String AZURE_SUBNET_CIDR = "192.168.100.0/24";
    public static final String DEFAULT_OS_DISK_CACHING = "None";
    public static final int NUMBER_OF_NICS = 2;

    public static ResourcePoolState createDefaultResourcePool(
            VerificationHost host)
            throws Throwable {
        ResourcePoolState inPool = new ResourcePoolState();
        inPool.name = UUID.randomUUID().toString();
        inPool.id = inPool.name;

        inPool.minCpuCount = 1L;
        inPool.minMemoryBytes = 1024L;

        ResourcePoolState returnPool =
                TestUtils.doPost(host, inPool, ResourcePoolState.class,
                        UriUtils.buildUri(host, ResourcePoolService.FACTORY_LINK));

        return returnPool;
    }

    public static int getAzureVMCount(ComputeManagementClient computeManagementClient) throws Exception {
        ServiceResponse<List<VirtualMachine>> response = computeManagementClient
                .getVirtualMachinesOperations().listAll();

        int count = 0;
        for (VirtualMachine virtualMachine : response.getBody()) {
            if (AzureComputeEnumerationAdapterService.AZURE_VM_TERMINATION_STATES
                    .contains(virtualMachine.getProvisioningState())) {
                continue;
            }
            count++;
        }

        return count;
    }

    public static void deleteVMs(VerificationHost host, String documentSelfLink, boolean isMock, int numberOfRemainingVMs)
            throws Throwable {
        host.testStart(1);
        ResourceRemovalTaskState deletionState = new ResourceRemovalTaskState();
        QuerySpecification resourceQuerySpec = new QuerySpecification();
        // query all documents
        resourceQuerySpec.query
                .setTermPropertyName(ServiceDocument.FIELD_NAME_SELF_LINK)
                .setTermMatchValue(documentSelfLink);
        deletionState.resourceQuerySpec = resourceQuerySpec;
        deletionState.isMockRequest = isMock;
        host.send(Operation
                          .createPost(UriUtils.buildUri(host, ResourceRemovalTaskService.FACTORY_LINK))
                          .setBody(deletionState)
                          .setCompletion(host.getCompletion()));
        host.testWait();
        ProvisioningUtils.queryComputeInstances(host, numberOfRemainingVMs);
    }

    /**
     * Create a compute host description for an Azure instance
     */
    public static ComputeState createDefaultComputeHost(
            VerificationHost host, String clientID, String clientKey, String subscriptionId,
            String tenantId, String resourcePoolLink) throws Throwable {
        AuthCredentialsServiceState auth = new AuthCredentialsServiceState();
        auth.privateKeyId = clientID;
        auth.privateKey = clientKey;
        auth.userLink = subscriptionId;
        auth.customProperties = new HashMap<>();
        auth.customProperties.put(AZURE_TENANT_ID, tenantId);
        auth.documentSelfLink = UUID.randomUUID().toString();

        TestUtils.doPost(host, auth, AuthCredentialsServiceState.class,
                UriUtils.buildUri(host, AuthCredentialsService.FACTORY_LINK));
        String authLink = UriUtils.buildUriPath(AuthCredentialsService.FACTORY_LINK,
                auth.documentSelfLink);

        ComputeDescription azureHostDescription = new ComputeDescription();
        azureHostDescription.id = UUID.randomUUID().toString();
        azureHostDescription.name = azureHostDescription.id;
        azureHostDescription.documentSelfLink = azureHostDescription.id;
        azureHostDescription.supportedChildren = new ArrayList<>();
        azureHostDescription.supportedChildren.add(ComputeType.VM_GUEST.name());
        azureHostDescription.instanceAdapterReference = UriUtils.buildUri(host,
                AzureUriPaths.AZURE_INSTANCE_ADAPTER);
        azureHostDescription.enumerationAdapterReference = UriUtils.buildUri(
                host,
                AzureUriPaths.AZURE_ENUMERATION_ADAPTER);
        azureHostDescription.authCredentialsLink = authLink;

        TestUtils.doPost(host, azureHostDescription,
                ComputeDescription.class,
                UriUtils.buildUri(host, ComputeDescriptionService.FACTORY_LINK));

        ComputeState azureComputeHost = new ComputeState();
        azureComputeHost.id = UUID.randomUUID().toString();
        azureComputeHost.name = azureHostDescription.name;
        azureComputeHost.documentSelfLink = azureComputeHost.id;
        azureComputeHost.descriptionLink = UriUtils.buildUriPath(
                ComputeDescriptionService.FACTORY_LINK, azureHostDescription.id);
        azureComputeHost.resourcePoolLink = resourcePoolLink;

        ComputeState returnState = TestUtils.doPost(host, azureComputeHost, ComputeState.class,
                UriUtils.buildUri(host, ComputeService.FACTORY_LINK));
        return returnState;
    }

    /**
     * Generate random names. For Azure, storage account names need to be unique across
     * Azure.
     */
    public static String generateName(String prefix) {
        return prefix + randomString(5);
    }

    public static String randomString(int length) {
        Random random = new Random();
        StringBuilder stringBuilder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            stringBuilder.append((char) ('a' + random.nextInt(26)));
        }
        return stringBuilder.toString();
    }

    public static ComputeState createDefaultVMResource(VerificationHost host, String azureVMName,
            String parentLink, String resourcePoolLink) throws Throwable {
        AuthCredentialsServiceState auth = new AuthCredentialsServiceState();
        auth.userEmail = AZURE_ADMIN_USERNAME;
        auth.privateKey = AZURE_ADMIN_PASSWORD;
        auth.documentSelfLink = UUID.randomUUID().toString();

        TestUtils.doPost(host, auth, AuthCredentialsServiceState.class,
                UriUtils.buildUri(host, AuthCredentialsService.FACTORY_LINK));
        String authLink = UriUtils.buildUriPath(AuthCredentialsService.FACTORY_LINK,
                auth.documentSelfLink);

        // Create a VM desc
        ComputeDescription azureVMDesc = new ComputeDescription();
        azureVMDesc.id = UUID.randomUUID().toString();
        azureVMDesc.name = azureVMDesc.id;
        azureVMDesc.regionId = AZURE_RESOURCE_GROUP_LOCATION;
        azureVMDesc.authCredentialsLink = authLink;
        azureVMDesc.documentSelfLink = azureVMDesc.id;
        azureVMDesc.instanceType = AZURE_VM_SIZE;
        azureVMDesc.environmentName = ENVIRONMENT_NAME_AZURE;
        azureVMDesc.customProperties = new HashMap<>();

        // set the create service to the azure instance service
        azureVMDesc.instanceAdapterReference = UriUtils.buildUri(host,
                AzureUriPaths.AZURE_INSTANCE_ADAPTER);

        ComputeDescription vmComputeDesc = TestUtils
                .doPost(host, azureVMDesc, ComputeDescription.class,
                        UriUtils.buildUri(host, ComputeDescriptionService.FACTORY_LINK));

        List<String> vmDisks = new ArrayList<>();
        DiskState rootDisk = new DiskState();
        rootDisk.name = azureVMName + "-boot-disk";
        rootDisk.id = UUID.randomUUID().toString();
        rootDisk.documentSelfLink = rootDisk.id;
        rootDisk.type = DiskType.HDD;
        rootDisk.sourceImageReference = URI.create(IMAGE_REFERENCE);
        rootDisk.bootOrder = 1;
        rootDisk.documentSelfLink = rootDisk.id;
        rootDisk.customProperties = new HashMap<>();
        rootDisk.customProperties.put(AZURE_OSDISK_CACHING, DEFAULT_OS_DISK_CACHING);
        rootDisk.customProperties.put(AzureConstants.AZURE_STORAGE_ACCOUNT_NAME,
                generateName(AZURE_STORAGE_ACCOUNT_NAME));
        rootDisk.customProperties.put(AzureConstants.AZURE_STORAGE_ACCOUNT_TYPE,
                AZURE_STORAGE_ACCOUNT_TYPE);

        TestUtils.doPost(host, rootDisk, DiskState.class,
                UriUtils.buildUri(host, DiskService.FACTORY_LINK));
        vmDisks.add(UriUtils.buildUriPath(DiskService.FACTORY_LINK, rootDisk.id));

        // Create network state.
        NetworkState networkState;
        {
            networkState = new NetworkState();
            networkState.id = UUID.randomUUID().toString();
            networkState.name = azureVMName + "-vNet";
            networkState.authCredentialsLink = authLink;
            networkState.resourcePoolLink = resourcePoolLink;
            networkState.subnetCIDR = AZURE_NETWORK_CIDR;
            networkState.regionId = AZURE_RESOURCE_GROUP_LOCATION;
            networkState.instanceAdapterReference = UriUtils.buildUri(host, "/dummyInstanceAdapterReference");

            networkState = TestUtils.doPost(host, networkState, NetworkState.class,
                    UriUtils.buildUri(host, NetworkService.FACTORY_LINK));
        }

        // Create subnet state.
        SubnetState subnetState;
        {
            subnetState = new SubnetState();

            subnetState.id = UUID.randomUUID().toString();
            subnetState.name = azureVMName + "-subnet";
            subnetState.networkLink = networkState.documentSelfLink;
            subnetState.subnetCIDR = AZURE_SUBNET_CIDR;

            subnetState = TestUtils.doPost(host, subnetState, SubnetState.class,
                    UriUtils.buildUri(host, SubnetService.FACTORY_LINK));
        }

        // Create network interface descriptions.
        NetworkInterfaceDescription nicDescription;
        {
            nicDescription = new NetworkInterfaceDescription();
            nicDescription.id = UUID.randomUUID().toString();
            nicDescription.assignment = IpAssignment.DYNAMIC;

            nicDescription = TestUtils.doPost(host, nicDescription, NetworkInterfaceDescription.class,
                    UriUtils.buildUri(host, NetworkInterfaceDescriptionService.FACTORY_LINK));
        }

        // Create NIC states.
        List<String> nicLinks;
        {
            nicLinks = new ArrayList<>();
            for (int i = 0; i < NUMBER_OF_NICS; i++) {
                NetworkInterfaceState nicState = new NetworkInterfaceState();
                nicState.id = UUID.randomUUID().toString();
                nicState.documentSelfLink = nicState.id;
                nicState.name = azureVMName + "-nic" + i;
                nicState.networkInterfaceDescriptionLink = nicDescription.documentSelfLink;
                nicState.networkLink = networkState.documentSelfLink;
                nicState.subnetLink = subnetState.documentSelfLink;

                nicState = TestUtils.doPost(host, nicState, NetworkInterfaceState.class,
                        UriUtils.buildUri(host, NetworkInterfaceService.FACTORY_LINK));

                nicLinks.add(nicState.documentSelfLink);
            }
        }

        // Finally create the compute resource state to provision using all constructs above.
        ComputeState resource = new ComputeState();
        resource.id = UUID.randomUUID().toString();
        resource.name = azureVMName;
        resource.parentLink = parentLink;
        resource.descriptionLink = vmComputeDesc.documentSelfLink;
        resource.resourcePoolLink = resourcePoolLink;
        resource.diskLinks = vmDisks;
        resource.networkInterfaceLinks = nicLinks;
        resource.customProperties = new HashMap<>();
        resource.customProperties.put(RESOURCE_GROUP_NAME, azureVMName);

        resource = TestUtils.doPost(host, resource, ComputeState.class,
                UriUtils.buildUri(host, ComputeService.FACTORY_LINK));

        return resource;
    }

    public static void deleteServiceDocument(VerificationHost host, String documentSelfLink)
            throws Throwable {
        host.testStart(1);
        host.send(
                Operation.createDelete(host, documentSelfLink).setCompletion(host.getCompletion()));
        host.testWait();
    }

    public static StorageDescription createDefaultStorageAccountDescription(VerificationHost host,
            String storageAccountName,
            String parentLink, String resourcePoolLink) throws Throwable {
        AuthCredentialsServiceState auth = new AuthCredentialsServiceState();
        auth.customProperties = new HashMap<>();
        auth.customProperties.put(AZURE_STORAGE_ACCOUNT_KEY1, randomString(15));
        auth.customProperties.put(AZURE_STORAGE_ACCOUNT_KEY2, randomString(15));
        auth.documentSelfLink = UUID.randomUUID().toString();

        TestUtils.doPost(host, auth, AuthCredentialsServiceState.class,
                UriUtils.buildUri(host, AuthCredentialsService.FACTORY_LINK));
        String authLink = UriUtils.buildUriPath(AuthCredentialsService.FACTORY_LINK,
                auth.documentSelfLink);

        // Create a storage description
        StorageDescription storageDesc = new StorageDescription();
        storageDesc.id = "testStorAcct-" + randomString(4);
        storageDesc.name = storageAccountName;
        storageDesc.regionId = AZURE_RESOURCE_GROUP_LOCATION;
        storageDesc.computeHostLink = parentLink;
        storageDesc.authCredentialsLink = authLink;
        storageDesc.resourcePoolLink = resourcePoolLink;
        storageDesc.documentSelfLink = UUID.randomUUID().toString();
        storageDesc.customProperties = new HashMap<>();
        storageDesc.customProperties.put(AZURE_STORAGE_TYPE, AZURE_STORAGE_ACCOUNTS);
        StorageDescription sDesc = TestUtils.doPost(host, storageDesc, StorageDescription.class,
                UriUtils.buildUri(host, StorageDescriptionService.FACTORY_LINK));
        return sDesc;
    }

    public static ResourceGroupState createDefaultResourceGroupState(VerificationHost host,
            String containerName, String parentLink) throws Throwable {
        ResourceGroupState rGroupState = new ResourceGroupState();
        rGroupState.id = "testStorCont-" + randomString(4);
        rGroupState.name = containerName;
        rGroupState.groupLinks = new HashSet<>();
        rGroupState.groupLinks.add("testStorAcct-" + randomString(4));
        rGroupState.customProperties = new HashMap<>();
        rGroupState.customProperties.put(FIELD_COMPUTE_HOST_LINK, parentLink);
        rGroupState.customProperties.put(AZURE_STORAGE_TYPE, AZURE_STORAGE_CONTAINERS);
        rGroupState.customProperties.put(AZURE_STORAGE_CONTAINER_LEASE_LAST_MODIFIED,
                randomString(10));
        rGroupState.customProperties.put(AZURE_STORAGE_CONTAINER_LEASE_STATE,
                randomString(5));
        rGroupState.customProperties.put(AZURE_STORAGE_CONTAINER_LEASE_STATUS,
                randomString(5));
        ResourceGroupState rGroup = TestUtils.doPost(host, rGroupState,
                ResourceGroupState.class, UriUtils.buildUri(host, ResourceGroupService.FACTORY_LINK));
        return rGroup;
    }

    public static DiskState createDefaultDiskState(VerificationHost host, String diskName,
            String storageContainerLink, String resourcePoolLink) throws Throwable {
        // Create a disk state
        DiskState diskState = new DiskState();
        diskState.id = UUID.randomUUID().toString();
        diskState.name = diskName;
        diskState.resourcePoolLink = resourcePoolLink;
        diskState.storageDescriptionLink = storageContainerLink;
        diskState.type = DEFAULT_DISK_TYPE;
        diskState.capacityMBytes = DEFAULT_DISK_CAPACITY;
        diskState.sourceImageReference = URI.create(DEFAULT_DISK_SERVICE_REFERENCE);
        diskState.documentSelfLink = diskState.id;
        DiskState dState = TestUtils.doPost(host, diskState, DiskState.class,
                UriUtils.buildUri(host, DiskService.FACTORY_LINK));
        return dState;
    }
}
