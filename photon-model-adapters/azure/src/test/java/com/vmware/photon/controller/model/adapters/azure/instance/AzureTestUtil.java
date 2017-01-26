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

import static com.vmware.photon.controller.model.ComputeProperties.FIELD_COMPUTE_HOST_LINK;
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
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.DEFAULT_INSTANCE_ADAPTER_REFERENCE;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

import com.microsoft.azure.CloudException;
import com.microsoft.azure.management.compute.ComputeManagementClient;
import com.microsoft.azure.management.compute.models.VirtualMachine;
import com.microsoft.azure.management.network.NetworkManagementClient;
import com.microsoft.azure.management.network.models.PublicIPAddress;
import com.microsoft.azure.management.network.models.SubResource;
import com.microsoft.azure.management.network.models.Subnet;
import com.microsoft.azure.management.network.models.VirtualNetworkGateway;
import com.microsoft.azure.management.network.models.VirtualNetworkGatewayIPConfiguration;
import com.microsoft.azure.management.network.models.VirtualNetworkGatewaySku;
import com.microsoft.rest.ServiceCallback;
import com.microsoft.rest.ServiceResponse;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.ResourceGroupStateType;
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
import com.vmware.photon.controller.model.resources.SecurityGroupService;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState.Rule;
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

    // This instance size DOES support 2 NICs! If you change it please correct NUMBER_OF_NICS.
    public static final String AZURE_VM_SIZE = "Standard_A3";

    public static final String IMAGE_REFERENCE = "Canonical:UbuntuServer:14.04.3-LTS:latest";

    public static final String AZURE_RESOURCE_GROUP_LOCATION = "westus";

    public static final String AZURE_STORAGE_ACCOUNT_NAME = "storage";
    public static final String AZURE_STORAGE_ACCOUNT_TYPE = "Standard_RAGRS";

    // If you change the number of NICs, please do so with the CIDRs!
    public static final int NUMBER_OF_NICS = 2;
    public static final String AZURE_NETWORK_NAME = "vNet";
    public static final String AZURE_SECURITY_GROUP_NAME = "NSG-name";
    public static final String AZURE_NETWORK_CIDR = "172.16.0.0/16";
    public static final String AZURE_SUBNET_NAME = "subnet";

    // As prerequisite Azure Gateway requires subnet named "GatewaySubnet".
    public static final String AZURE_GATEWAY_SUBNET_NAME = "GatewaySubnet";

    public static final String[] AZURE_SUBNET_CIDR;

    // Split the address space among the subnets attached to NICs and Gateway
    // The first [number of NICs] Subnet CIDRs are reserved for the NICs and the last one for the
    // Gateway.
    static {
        AZURE_SUBNET_CIDR = new String[NUMBER_OF_NICS + 1];
        AZURE_SUBNET_CIDR[0] = "172.16.0.0/18";
        AZURE_SUBNET_CIDR[1] = "172.16.64.0/18";
        AZURE_SUBNET_CIDR[2] = "172.16.128.0/18";
    }

    public static final String AZURE_GATEWAY_NAME = "gateway";
    public static final String AZURE_GATEWAY_IP_CONFIGURATION_NAME = "gateway-ipconfig";
    public static final String AZURE_GATEWAY_PUBLIC_IP_NAME = "gateway-pip";
    public static final String AZURE_GATEWAY_IP_ALLOCATION_METHOD = "Dynamic";
    public static final String AZURE_GATEWAY_SKU = "Standard";
    public static final String AZURE_GATEWAY_TYPE = "Vpn";
    public static final String AZURE_GATEWAY_VPN_TYPE = "RouteBased";

    public static final String DEFAULT_OS_DISK_CACHING = "None";

    public static ResourcePoolState createDefaultResourcePool(
            VerificationHost host)
            throws Throwable {
        ResourcePoolState inPool = new ResourcePoolState();
        inPool.name = UUID.randomUUID().toString();
        inPool.id = inPool.name;

        inPool.minCpuCount = 1L;
        inPool.minMemoryBytes = 1024L;

        ResourcePoolState returnPool = TestUtils.doPost(host, inPool, ResourcePoolState.class,
                UriUtils.buildUri(host, ResourcePoolService.FACTORY_LINK));

        return returnPool;
    }

    public static int getAzureVMCount(ComputeManagementClient computeManagementClient)
            throws Exception {
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

    public static void deleteVMs(VerificationHost host, String documentSelfLink, boolean isMock,
            int numberOfRemainingVMs)
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
        ProvisioningUtils.queryDocumentsAndAssertExpectedCount(host, numberOfRemainingVMs,
                ComputeService.FACTORY_LINK, true);
    }

    public static AuthCredentialsServiceState createDefaultAuthCredentials(VerificationHost host,
            String clientID, String clientKey, String subscriptionId, String tenantId)
            throws Throwable {

        AuthCredentialsServiceState auth = new AuthCredentialsServiceState();
        auth.privateKeyId = clientID;
        auth.privateKey = clientKey;
        auth.userLink = subscriptionId;
        auth.customProperties = new HashMap<>();
        auth.customProperties.put(AZURE_TENANT_ID, tenantId);
        auth.documentSelfLink = UUID.randomUUID().toString();

        return TestUtils.doPost(host, auth, AuthCredentialsServiceState.class,
                UriUtils.buildUri(host, AuthCredentialsService.FACTORY_LINK));
    }

    /**
     * Create a compute host description for an Azure instance
     */
    public static ComputeState createDefaultComputeHost(
            VerificationHost host, String resourcePoolLink, String authLink) throws Throwable {

        ComputeDescription azureHostDescription = new ComputeDescription();
        azureHostDescription.id = UUID.randomUUID().toString();
        azureHostDescription.name = azureHostDescription.id;
        azureHostDescription.environmentName = ComputeDescription.ENVIRONMENT_NAME_AZURE;
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
        azureComputeHost.type = ComputeType.VM_HOST;
        azureComputeHost.environmentName = ComputeDescription.ENVIRONMENT_NAME_AZURE;
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
     * Generate random names. For Azure, storage account names need to be unique across Azure.
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
            String parentLink, String resourcePoolLink, String computeHostAuthLink)
            throws Throwable {

        return createDefaultVMResource(host, azureVMName, parentLink, resourcePoolLink,
                computeHostAuthLink, null /* networkRGLink */);
    }

    public static ComputeState createDefaultVMResource(VerificationHost host, String azureVMName,
            String parentLink, String resourcePoolLink, String computeHostAuthLink,
            String networkRGLink)
            throws Throwable {

        ResourceGroupState vmRG = createDefaultResourceGroupState(
                host, azureVMName, parentLink, ResourceGroupStateType.AzureResourceGroup);

        String resourceGroupLink = vmRG.documentSelfLink;

        if (networkRGLink == null) {
            // The RG where the VM is deployed is also used as RG for the Network!
            networkRGLink = resourceGroupLink;
        }

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
        azureVMDesc.environmentName = ComputeDescription.ENVIRONMENT_NAME_AZURE;
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

        // Create NICs
        List<String> nicLinks = createDefaultNicStates(
                host, resourcePoolLink, computeHostAuthLink, networkRGLink)
                .stream()
                .map(nic -> nic.documentSelfLink)
                .collect(Collectors.toList());

        // Finally create the compute resource state to provision using all constructs above.
        ComputeState computeState = new ComputeState();
        computeState.id = UUID.randomUUID().toString();
        computeState.name = azureVMName;
        computeState.parentLink = parentLink;
        computeState.type = ComputeType.VM_GUEST;
        computeState.environmentName = ComputeDescription.ENVIRONMENT_NAME_AZURE;
        computeState.descriptionLink = vmComputeDesc.documentSelfLink;
        computeState.resourcePoolLink = resourcePoolLink;
        computeState.diskLinks = vmDisks;
        computeState.networkInterfaceLinks = nicLinks;
        computeState.customProperties = new HashMap<>();
        computeState.customProperties.put(RESOURCE_GROUP_NAME, azureVMName);
        computeState.groupLinks = new HashSet<>();
        computeState.groupLinks.add(resourceGroupLink);

        computeState = TestUtils.doPost(host, computeState, ComputeState.class,
                UriUtils.buildUri(host, ComputeService.FACTORY_LINK));

        return computeState;
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
            String resourceGroupName, String parentLink, ResourceGroupStateType resourceGroupType)
            throws Throwable {

        ResourceGroupState rGroupState = new ResourceGroupState();

        rGroupState.id = "testResGroup-" + randomString(4);
        rGroupState.name = resourceGroupName;

        rGroupState.groupLinks = new HashSet<>();
        rGroupState.groupLinks.add("testResGroup-" + randomString(4));

        rGroupState.customProperties = new HashMap<>();
        rGroupState.customProperties.put(FIELD_COMPUTE_HOST_LINK, parentLink);
        rGroupState.customProperties.put(AZURE_STORAGE_TYPE, AZURE_STORAGE_CONTAINERS);
        rGroupState.customProperties.put(AZURE_STORAGE_CONTAINER_LEASE_LAST_MODIFIED,
                randomString(10));
        rGroupState.customProperties.put(AZURE_STORAGE_CONTAINER_LEASE_STATE,
                randomString(5));
        rGroupState.customProperties.put(AZURE_STORAGE_CONTAINER_LEASE_STATUS,
                randomString(5));
        rGroupState.customProperties.put(ComputeProperties.RESOURCE_TYPE_KEY,
                resourceGroupType.name());

        ResourceGroupState rGroup = TestUtils.doPost(host, rGroupState,
                ResourceGroupState.class,
                UriUtils.buildUri(host, ResourceGroupService.FACTORY_LINK));

        return rGroup;
    }

    /**
     * Create a disk state
     */
    public static DiskState createDefaultDiskState(VerificationHost host, String diskName,
            String storageContainerLink, String resourcePoolLink) throws Throwable {

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

    /*
     * NOTE: It is highly recommended to keep this method in sync with its AWS counterpart:
     * TestAWSSetupUtils.createAWSNicStates
     */
    public static List<NetworkInterfaceState> createDefaultNicStates(
            VerificationHost host,
            String resourcePoolLink,
            String authCredentialsLink,
            String networkRGLink) throws Throwable {

        // Create network state.
        NetworkState networkState;
        {
            networkState = new NetworkState();
            networkState.id = AZURE_NETWORK_NAME;
            networkState.name = AZURE_NETWORK_NAME;
            networkState.subnetCIDR = AZURE_NETWORK_CIDR;
            networkState.authCredentialsLink = authCredentialsLink;
            networkState.resourcePoolLink = resourcePoolLink;
            networkState.groupLinks = Collections.singleton(networkRGLink);
            networkState.regionId = AZURE_RESOURCE_GROUP_LOCATION;
            networkState.instanceAdapterReference = UriUtils.buildUri(host,
                    DEFAULT_INSTANCE_ADAPTER_REFERENCE);

            networkState = TestUtils.doPost(host, networkState,
                    NetworkState.class,
                    UriUtils.buildUri(host, NetworkService.FACTORY_LINK));
        }

        // Create NIC states.
        List<NetworkInterfaceState> nics = new ArrayList<>();

        for (int i = 0; i < NUMBER_OF_NICS; i++) {

            // Create subnet state per NIC.
            SubnetState subnetState;
            {
                subnetState = new SubnetState();

                subnetState.id = AZURE_SUBNET_NAME + i;
                subnetState.name = AZURE_SUBNET_NAME + i;
                subnetState.networkLink = networkState.documentSelfLink;
                subnetState.subnetCIDR = AZURE_SUBNET_CIDR[i];

                subnetState = TestUtils.doPost(host, subnetState,
                        SubnetState.class,
                        UriUtils.buildUri(host, SubnetService.FACTORY_LINK));
            }

            // Create security group state
            SecurityGroupState securityGroupState;
            {
                securityGroupState = new SecurityGroupState();
                securityGroupState.authCredentialsLink = authCredentialsLink;
                securityGroupState.documentSelfLink = securityGroupState.id;
                securityGroupState.name = AZURE_SECURITY_GROUP_NAME;
                securityGroupState.tenantLinks = new ArrayList<>();
                securityGroupState.tenantLinks.add("tenant-linkA");
                securityGroupState.groupLinks = Collections.singleton(networkRGLink);
                ArrayList<Rule> ingressRules = new ArrayList<>();

                Rule ssh = new Rule();
                ssh.name = "ssh-in";
                ssh.protocol = "tcp";
                ssh.ipRangeCidr = "0.0.0.0/0";
                ssh.ports = "22";
                ingressRules.add(ssh);
                securityGroupState.ingress = ingressRules;

                ArrayList<Rule> egressRules = new ArrayList<>();
                Rule out = new Rule();
                out.name = "out";
                out.protocol = "tcp";
                out.ipRangeCidr = "0.0.0.0/0";
                out.ports = "1-65535";
                egressRules.add(out);
                securityGroupState.egress = egressRules;

                securityGroupState.regionId = "regionId";
                securityGroupState.resourcePoolLink = "/link/to/rp";
                securityGroupState.instanceAdapterReference = new URI(
                        "http://instanceAdapterReference");

                securityGroupState = TestUtils.doPost(host, securityGroupState,
                        SecurityGroupState.class, UriUtils.buildUri(host, SecurityGroupService
                                .FACTORY_LINK));
            }

            // Create NIC description.
            NetworkInterfaceDescription nicDescription;
            {
                nicDescription = new NetworkInterfaceDescription();

                nicDescription.id = "nicDesc" + i;
                nicDescription.name = "nicDesc" + i;
                nicDescription.deviceIndex = i;
                nicDescription.assignment = IpAssignment.DYNAMIC;

                nicDescription = TestUtils.doPost(host, nicDescription,
                        NetworkInterfaceDescription.class,
                        UriUtils.buildUri(host, NetworkInterfaceDescriptionService.FACTORY_LINK));
            }

            NetworkInterfaceState nicState = new NetworkInterfaceState();

            nicState.id = "nic" + i;
            nicState.name = "nic" + i;
            nicState.deviceIndex = nicDescription.deviceIndex;

            if (i == 0) {
                // Attach security group only on the primary nic.
                nicState.securityGroupLinks =
                        Collections.singletonList(securityGroupState.documentSelfLink);
            }

            nicState.networkInterfaceDescriptionLink = nicDescription.documentSelfLink;
            nicState.subnetLink = subnetState.documentSelfLink;
            nicState.networkLink = subnetState.networkLink;

            nicState = TestUtils.doPost(host, nicState,
                    NetworkInterfaceState.class,
                    UriUtils.buildUri(host, NetworkInterfaceService.FACTORY_LINK));

            nics.add(nicState);
        }

        return nics;
    }

    /**
     * Adds Gateway to Virtual Network in Azure
     */
    public static void addAzureGatewayToVirtualNetwork(NetworkManagementClient
            networkManagementClient, String resourceGroupName, String azureNetworkName)
            throws CloudException, IOException, InterruptedException {

        // create Gateway Subnet
        Subnet gatewaySubnetParams = new Subnet();
        gatewaySubnetParams.setName(AZURE_GATEWAY_SUBNET_NAME);
        gatewaySubnetParams.setAddressPrefix(AZURE_SUBNET_CIDR[NUMBER_OF_NICS]);
        Subnet gatewaySubnet = networkManagementClient
                .getSubnetsOperations()
                .createOrUpdate(resourceGroupName, azureNetworkName,
                        AZURE_GATEWAY_SUBNET_NAME,
                        gatewaySubnetParams).getBody();

        // create Public IP
        PublicIPAddress publicIPAddressParams = new PublicIPAddress();
        publicIPAddressParams.setPublicIPAllocationMethod(AZURE_GATEWAY_IP_ALLOCATION_METHOD);
        publicIPAddressParams.setLocation(AZURE_RESOURCE_GROUP_LOCATION);

        PublicIPAddress publicIPAddress = networkManagementClient
                .getPublicIPAddressesOperations()
                .createOrUpdate(resourceGroupName, AZURE_GATEWAY_PUBLIC_IP_NAME,
                        publicIPAddressParams).getBody();

        SubResource publicIPSubResource = new SubResource();
        publicIPSubResource.setId(publicIPAddress.getId());

        // create IP Configuration
        VirtualNetworkGatewayIPConfiguration ipConfiguration = new
                VirtualNetworkGatewayIPConfiguration();
        ipConfiguration.setName(AZURE_GATEWAY_IP_CONFIGURATION_NAME);
        ipConfiguration.setSubnet(gatewaySubnet);
        ipConfiguration.setPrivateIPAllocationMethod(AZURE_GATEWAY_IP_ALLOCATION_METHOD);

        ipConfiguration.setPublicIPAddress(publicIPSubResource);

        // create Virtual Network Gateway
        VirtualNetworkGateway virtualNetworkGateway = new VirtualNetworkGateway();
        virtualNetworkGateway.setGatewayType(AZURE_GATEWAY_TYPE);
        virtualNetworkGateway.setVpnType(AZURE_GATEWAY_VPN_TYPE);
        VirtualNetworkGatewaySku vNetGatewaySku = new VirtualNetworkGatewaySku();
        vNetGatewaySku.setName(AZURE_GATEWAY_SKU);
        vNetGatewaySku.setTier(AZURE_GATEWAY_SKU);
        vNetGatewaySku.setCapacity(2);
        virtualNetworkGateway.setSku(vNetGatewaySku);
        virtualNetworkGateway.setLocation(AZURE_RESOURCE_GROUP_LOCATION);

        List<VirtualNetworkGatewayIPConfiguration> ipConfigurations = new ArrayList<>();
        ipConfigurations.add(ipConfiguration);
        virtualNetworkGateway.setIpConfigurations(ipConfigurations);

        // Call the async variant because the virtual network gateway provisioning depends on
        // the public IP address assignment which is time-consuming operation
        networkManagementClient.getVirtualNetworkGatewaysOperations().createOrUpdateAsync(
                resourceGroupName, AZURE_GATEWAY_NAME, virtualNetworkGateway, new
                        ServiceCallback<VirtualNetworkGateway>() {
                            @Override
                            public void failure(Throwable throwable) {
                                throw new RuntimeException(
                                        "Error creating Azure Virtual Network Gateway.");
                            }

                            @Override
                            public void success(
                                    ServiceResponse<VirtualNetworkGateway> serviceResponse) {

                            }
                        });
    }
}
