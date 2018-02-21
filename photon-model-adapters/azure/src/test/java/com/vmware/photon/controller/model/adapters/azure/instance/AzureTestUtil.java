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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static com.vmware.photon.controller.model.ComputeProperties.COMPUTE_HOST_LINK_PROP_NAME;
import static com.vmware.photon.controller.model.ComputeProperties.RESOURCE_GROUP_NAME;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_DATA_DISK_CACHING;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_OSDISK_CACHING;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNTS;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNT_KEY1;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNT_KEY2;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_BLOBS;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_CONTAINERS;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_CONTAINER_LEASE_LAST_MODIFIED;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_CONTAINER_LEASE_STATE;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_CONTAINER_LEASE_STATUS;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_DISKS;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_TYPE;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_TENANT_ID;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AzureResourceType.azure_net_interface;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AzureResourceType.azure_subnet;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AzureResourceType.azure_vnet;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.DEFAULT_DISK_CAPACITY;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.DEFAULT_DISK_TYPE;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.DEFAULT_INSTANCE_ADAPTER_REFERENCE;

import static com.vmware.photon.controller.model.constants.PhotonModelConstants.CPU_UTILIZATION_PERCENT;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.CUSTOM_PROP_ENDPOINT_LINK;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.MEMORY_USED_PERCENT;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.STORAGE_USED_BYTES;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.TAG_KEY_TYPE;

import static com.vmware.photon.controller.model.resources.util.PhotonModelUtils.ENDPOINT_LINK_EXPLICIT_SUPPORT;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.microsoft.azure.CloudException;
import com.microsoft.azure.SubResource;
import com.microsoft.azure.management.compute.CachingTypes;
import com.microsoft.azure.management.compute.InstanceViewTypes;
import com.microsoft.azure.management.compute.implementation.ComputeManagementClientImpl;
import com.microsoft.azure.management.compute.implementation.VirtualMachineInner;
import com.microsoft.azure.management.network.AddressSpace;
import com.microsoft.azure.management.network.IPAllocationMethod;
import com.microsoft.azure.management.network.SecurityRuleAccess;
import com.microsoft.azure.management.network.SecurityRuleDirection;
import com.microsoft.azure.management.network.SecurityRuleProtocol;
import com.microsoft.azure.management.network.VirtualNetworkGatewaySku;
import com.microsoft.azure.management.network.VirtualNetworkGatewaySkuName;
import com.microsoft.azure.management.network.VirtualNetworkGatewaySkuTier;
import com.microsoft.azure.management.network.VirtualNetworkGatewayType;
import com.microsoft.azure.management.network.VpnType;
import com.microsoft.azure.management.network.implementation.NetworkManagementClientImpl;
import com.microsoft.azure.management.network.implementation.NetworkSecurityGroupInner;
import com.microsoft.azure.management.network.implementation.PublicIPAddressInner;
import com.microsoft.azure.management.network.implementation.SecurityRuleInner;
import com.microsoft.azure.management.network.implementation.SubnetInner;
import com.microsoft.azure.management.network.implementation.VirtualNetworkGatewayIPConfigurationInner;
import com.microsoft.azure.management.network.implementation.VirtualNetworkGatewayInner;
import com.microsoft.azure.management.network.implementation.VirtualNetworkInner;
import com.microsoft.azure.management.resources.fluentcore.arm.Region;
import com.microsoft.azure.management.resources.implementation.ResourceGroupInner;
import com.microsoft.azure.management.resources.implementation.ResourceManagementClientImpl;
import com.microsoft.azure.management.storage.SkuName;
import com.microsoft.rest.ServiceCallback;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AzureResourceType;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.ResourceGroupStateType;
import com.vmware.photon.controller.model.adapters.azure.enumeration.AzureComputeEnumerationAdapterService;
import com.vmware.photon.controller.model.adapters.azure.enumeration.AzureEnumerationAdapterService;
import com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.AzureNicSpecs.GatewaySpec;
import com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.AzureNicSpecs.NetSpec;
import com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.AzureNicSpecs.NicSpec;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils;
import com.vmware.photon.controller.model.adapters.util.TagsUtil;
import com.vmware.photon.controller.model.adapters.util.instance.BaseComputeInstanceContext.ImageSource;
import com.vmware.photon.controller.model.adapters.util.instance.BaseComputeInstanceContext.ImageSource.Type;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService.ResourceMetrics;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.DiskService.DiskType;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ImageService;
import com.vmware.photon.controller.model.resources.ImageService.ImageState;
import com.vmware.photon.controller.model.resources.ImageService.ImageState.DiskConfiguration;
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
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.SecurityGroupService;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState.Rule;
import com.vmware.photon.controller.model.resources.StorageDescriptionService;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.photon.controller.model.resources.util.PhotonModelUtils;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService.ResourceEnumerationTaskState;
import com.vmware.photon.controller.model.tasks.ResourceRemovalTaskService;
import com.vmware.photon.controller.model.tasks.ResourceRemovalTaskService.ResourceRemovalTaskState;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.photon.controller.model.tasks.monitoring.StatsAggregationTaskService;
import com.vmware.photon.controller.model.tasks.monitoring.StatsAggregationTaskService.StatsAggregationTaskState;
import com.vmware.photon.controller.model.tasks.monitoring.StatsCollectionTaskService;
import com.vmware.photon.controller.model.tasks.monitoring.StatsCollectionTaskService.StatsCollectionTaskState;
import com.vmware.photon.controller.model.util.ClusterUtil;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.QueryTask.QueryTerm;

public class AzureTestUtil {
    public static final String AZURE_ADMIN_USERNAME = "azureuser";
    public static final String AZURE_ADMIN_PASSWORD = "Pa$$word1";

    // This instance size DOES support 2 NICs! If you change it please correct NUMBER_OF_NICS.
    public static final String AZURE_VM_SIZE = "STANDARD_A3";

    public static final String IMAGE_REFERENCE = "Canonical:UbuntuServer:14.04.3-LTS:latest";

    public static final String AZURE_RESOURCE_GROUP_LOCATION = "westus";

    public static final String AZURE_STORAGE_ACCOUNT_TYPE = "Standard_RAGRS";

    /*
     * VERY IMPORTANT: Do NOT change the vNet-subnet name to something random/unique per test run.
     * There's no need since for every VM deployment we are creating new Resource Group and the
     * names of the entities within (vNets inclusive) need to be unique just in the scope of the RG,
     * and not across the subscription.
     *
     * Otherwise this will break TestAzureProvisionTask.testProvisionVMUsingSharedNetwork and will
     * result in orphan vNets under 'test-sharedNetworkRG' RG.
     */

    public static final String AZURE_SECURITY_GROUP_NAME = "test-NSG";

    public static final String AZURE_SHARED_NETWORK_RESOURCE_GROUP_NAME = "test-sharedNetworkRG";

    // used to test network and SG RG assignment - this RG should be filtered out, its not the
    // correct type
    public static final String AZURE_STORAGE_CONTAINER_RG_NAME = "test-StorageContainerRG";

    public static final AzureNicSpecs DEFAULT_NIC_SPEC;
    public static final AzureNicSpecs SHARED_NETWORK_NIC_SPEC;
    public static final AzureNicSpecs NO_PUBLIC_IP_NIC_SPEC;
    public static final AzureNicSpecs PRIVATE_IP_NIC_SPEC;

    // Boot disk size of 32 GBs. We can only increase size of OS Disk. The Image used already has 30
    // GB size.
    public static final long AZURE_CUSTOM_OSDISK_SIZE = 1024 * 32;
    // Data Disk Size of 20GBs
    public static final long AZURE_CUSTOM_DATA_DISK_SIZE = 1024 * 20;


    public static final String AZURE_NETWORK_NAME = "test-vNet";
    private static final String AZURE_NETWORK_CIDR = "172.16.0.0/16";
    private static final String AZURE_SUBNET_NAME = "test-subnet";

    // The number of subnet CIDRs drives the number of nics created.
    private static final String[] AZURE_SUBNET_CIDR = { "172.16.0.0/18", "172.16.64.0/18" };

    private static final String AZURE_GATEWAY_NAME = "gateway";
    private static final String AZURE_GATEWAY_CIDR = "172.16.128.0/18";
    private static final String AZURE_GATEWAY_IP_CONFIGURATION_NAME = "gateway-ipconfig";
    private static final String AZURE_GATEWAY_PUBLIC_IP_NAME = "gateway-pip";

    static {
        DEFAULT_NIC_SPEC = createDefaultNicSpec();

        NO_PUBLIC_IP_NIC_SPEC = createNoPublicIpNicSpec();

        SHARED_NETWORK_NIC_SPEC = createSharedNetworkNicSpec();

        PRIVATE_IP_NIC_SPEC = createPrivateIpNicSpec();
    }

    private static AzureNicSpecs createDefaultNicSpec() {
        return initializeNicSpecs(null /* prefix */ , false /* assignGateway */,
                true /* assignPublicIpAddress */, false /* assignPrivateIpAddress */);
    }

    private static AzureNicSpecs createNoPublicIpNicSpec() {
        return initializeNicSpecs(null /* prefix */ , false /* assignGateway */,
                false /* assignPublicIpAddress */, false /* assignPrivateIpAddress */);
    }

    private static AzureNicSpecs createSharedNetworkNicSpec() {
        return initializeNicSpecs(null /* prefix */ , true /* assignGateway */,
                true /* assignPublicIpAddress */, false /* assignPrivateIpAddress */);
    }

    private static AzureNicSpecs createPrivateIpNicSpec() {
        return initializeNicSpecs(null /* prefix */ , false /* assignGateway */,
                true /* assignPublicIpAddress */, true /* assignPrivateIpAddress */);
    }

    public static final CachingTypes DEFAULT_OS_DISK_CACHING = CachingTypes.NONE;
    public static final CachingTypes DEFAULT_DATA_DISK_CACHING = CachingTypes.READ_WRITE;

    public static class AzureNicSpecs {

        public static class NetSpec {

            public final String name;
            public final String cidr;
            public final String zoneId;

            public NetSpec(String name, String cidr, String zoneId) {
                this.name = name;
                this.cidr = cidr;
                this.zoneId = zoneId;
            }
        }

        public static class NicSpec {

            private IpAssignment ipAssignment;
            private NetSpec subnetSpec;
            private String staticIp;

            public static NicSpec createDynamic(NetSpec subnetSpec) {
                return new NicSpec()
                        .withSubnetSpec(subnetSpec)
                        .withDynamicIpAssignment();
            }

            public static NicSpec createStatic(NetSpec subnetSpec) {
                return new NicSpec()
                        .withSubnetSpec(subnetSpec)
                        .withStaticIpAssignment();
            }

            private NicSpec withSubnetSpec(NetSpec subnetSpec) {
                this.subnetSpec = subnetSpec;
                return this;
            }

            private NicSpec withStaticIpAssignment() {

                if (this.subnetSpec == null) {
                    throw new IllegalArgumentException("'subnetSpec' can not be empty!");
                }

                this.ipAssignment = IpAssignment.STATIC;

                try {
                    this.staticIp = generateBaseOnCIDR();
                } catch (UnknownHostException e) {
                    throw new IllegalArgumentException(e);
                }
                return this;
            }

            private NicSpec withDynamicIpAssignment() {
                this.ipAssignment = IpAssignment.DYNAMIC;
                return this;
            }

            public String ip() {
                return this.staticIp;
            }

            public IpAssignment getIpAssignment() {
                return this.ipAssignment;
            }

            public NetSpec getSubnetSpec() {
                return this.subnetSpec;
            }

            private String generateBaseOnCIDR() throws UnknownHostException {
                assertNotNull(this.subnetSpec.cidr);
                String[] startingIpAndRange = this.subnetSpec.cidr.split("/");
                assertEquals(2, startingIpAndRange.length);
                String startingIp = startingIpAndRange[0];
                String range = startingIpAndRange[1];
                String staticIp = generateIp(startingIp, range);
                return staticIp;
            }

            private String generateIp(String startingIp, String range) throws UnknownHostException {
                Random r = new Random();
                int startingIpAsInt = ByteBuffer
                        .wrap(InetAddress.getByName(startingIp).getAddress())
                        .getInt();

                int min = 3;
                int max = Integer.valueOf(range);
                // To prevent Address 172.31.48.0 is in subnet's reserved address range
                int nextSubnetIpAsInt = startingIpAsInt | (r.nextInt((max - 1) - (min + 1)) + min);
                return InetAddress
                        .getByAddress(ByteBuffer.allocate(4).putInt(nextSubnetIpAsInt).array())
                        .getHostAddress();
            }

            private NicSpec() {
                // Leave the initialization to createDynamic() & createStatic(...) methods.
            }
        }

        public static class GatewaySpec {
            public final String name;
            public final String cidr;
            public final String zoneId;
            public final String ipConfigurationName;
            public final String publicIpName;
            public final IPAllocationMethod ipAllocationMethod;
            public final VirtualNetworkGatewaySkuName skuName;
            public final VirtualNetworkGatewaySkuTier skuTier;
            public final VirtualNetworkGatewayType type;
            public final VpnType vpnType;

            public GatewaySpec(String name, String cidr, String zoneId, String ipConfigurationName,
                    String publicIpName, IPAllocationMethod ipAllocationMethod,
                    VirtualNetworkGatewaySkuName skuName,
                    VirtualNetworkGatewaySkuTier skuTier, VirtualNetworkGatewayType type,
                    VpnType vpnType) {
                this.name = name;
                this.cidr = cidr;
                this.zoneId = zoneId;
                this.ipConfigurationName = ipConfigurationName;
                this.publicIpName = publicIpName;
                this.ipAllocationMethod = ipAllocationMethod;
                this.skuName = skuName;
                this.skuTier = skuTier;
                this.type = type;
                this.vpnType = vpnType;
            }
        }

        public final NetSpec network;
        public final List<NicSpec> nicSpecs;
        public final GatewaySpec gateway;
        public final boolean assignPublicIpAddress;

        public AzureNicSpecs(NetSpec network, GatewaySpec gateway,
                List<NicSpec> nicSpecs, boolean assignPublicIpAddress) {
            this.network = network;
            this.nicSpecs = nicSpecs;
            this.gateway = gateway;
            this.assignPublicIpAddress = assignPublicIpAddress;
        }
    }

    public static AzureNicSpecs initializeNicSpecs(String prefix, boolean assignGateway,
            boolean assignPublicIpAddress, boolean assignPrivateIpAddress) {
        String networkName = (prefix != null ? prefix + "-" : "") + AZURE_NETWORK_NAME;
        NetSpec network = new NetSpec(
                networkName,
                AZURE_NETWORK_CIDR,
                AZURE_RESOURCE_GROUP_LOCATION);

        List<NetSpec> subnets = new ArrayList<>();
        for (int i = 0; i < AZURE_SUBNET_CIDR.length; i++) {
            String subnetName = (prefix != null ? prefix + "-" : "") + AZURE_SUBNET_NAME + i;

            subnets.add(new NetSpec(subnetName,
                    AZURE_SUBNET_CIDR[i],
                    AZURE_RESOURCE_GROUP_LOCATION));
        }
        GatewaySpec gateway = assignGateway ? new GatewaySpec(AZURE_GATEWAY_NAME,
                AZURE_GATEWAY_CIDR,
                AZURE_RESOURCE_GROUP_LOCATION,
                AZURE_GATEWAY_IP_CONFIGURATION_NAME,
                AZURE_GATEWAY_PUBLIC_IP_NAME,
                IPAllocationMethod.DYNAMIC,
                VirtualNetworkGatewaySkuName.STANDARD,
                VirtualNetworkGatewaySkuTier.STANDARD,
                VirtualNetworkGatewayType.VPN,
                VpnType.ROUTE_BASED) : null;

        List<NicSpec> nicSpecs = new ArrayList<>();

        for (int i = 0; i < subnets.size(); i++) {
            NicSpec nicSpec = null;
            if (i == 0 && assignPrivateIpAddress) {
                nicSpec = NicSpec.createStatic(subnets.get(i));
            } else {
                nicSpec = NicSpec.createDynamic(subnets.get(i));
            }
            nicSpecs.add(nicSpec);
        }

        return new AzureNicSpecs(network, gateway, nicSpecs, assignPublicIpAddress);
    }

    public static ResourcePoolState createDefaultResourcePool(
            VerificationHost host)
            throws Throwable {

        ResourcePoolState inPool = new ResourcePoolState();
        inPool.name = UUID.randomUUID().toString();
        inPool.id = inPool.name;

        inPool.minCpuCount = 1L;
        inPool.minMemoryBytes = 1024L;

        return TestUtils.doPost(host, inPool, ResourcePoolState.class,
                UriUtils.buildUri(host, ResourcePoolService.FACTORY_LINK));
    }

    public static VirtualNetworkInner getAzureVirtualNetwork(
            NetworkManagementClientImpl networkManagementClient,
            String resourceGroupName,
            String virtualNetworkName) throws Exception {

        return networkManagementClient
                .virtualNetworks()
                .getByResourceGroup(resourceGroupName, virtualNetworkName, null);
    }

    public static VirtualMachineInner getAzureVirtualMachine(
            ComputeManagementClientImpl computeManagementClient,
            String resourceGroupName,
            String vmName) throws Exception {

        return computeManagementClient
                .virtualMachines()
                .getByResourceGroup(resourceGroupName, vmName, null);
    }

    public static VirtualMachineInner getAzureVirtualMachineWithExtension(
            ComputeManagementClientImpl computeManagementClient,
            String resourceGroupName,
            String vmName,
            InstanceViewTypes expand) throws Exception {

        return computeManagementClient
                .virtualMachines()
                .getByResourceGroup(resourceGroupName, vmName, expand);
    }

    public static NetworkSecurityGroupInner getAzureSecurityGroup(
            NetworkManagementClientImpl networkManagementClient,
            String resourceGroupName,
            String securityGroupName) throws Exception {

        return networkManagementClient
                .networkSecurityGroups()
                .getByResourceGroup(resourceGroupName, securityGroupName, null);
    }

    public static VirtualNetworkInner updateAzureVirtualNetwork(
            NetworkManagementClientImpl networkManagementClient,
            String resourceGroupName,
            String virtualNetworkName,
            VirtualNetworkInner parameters) throws Exception {

        return networkManagementClient
                .virtualNetworks()
                .createOrUpdate(resourceGroupName, virtualNetworkName, parameters);
    }

    public static NetworkSecurityGroupInner updateAzureSecurityGroup(
            NetworkManagementClientImpl networkManagementClient,
            String resourceGroupName,
            String networkSecurityGroupName,
            NetworkSecurityGroupInner parameters) throws Exception {

        return networkManagementClient
                .networkSecurityGroups()
                .createOrUpdate(resourceGroupName, networkSecurityGroupName, parameters);
    }

    public static VirtualMachineInner updateAzureVirtualMachine(
            ComputeManagementClientImpl computeManagementClient,
            String resourceGroupName,
            String vmName,
            VirtualMachineInner parameters) throws Exception {

        return computeManagementClient
                .virtualMachines()
                .createOrUpdate(resourceGroupName, vmName, parameters);
    }

    public static int getAzureVMCount(ComputeManagementClientImpl computeManagementClient)
            throws Exception {

        List<VirtualMachineInner> response = computeManagementClient
                .virtualMachines().list();

        int count = 0;
        for (VirtualMachineInner virtualMachine : response) {
            if (AzureComputeEnumerationAdapterService.AZURE_VM_TERMINATION_STATES
                    .contains(virtualMachine.provisioningState())) {
                continue;
            }
            count++;
        }

        return count;
    }

    public static void deleteVMs(VerificationHost host, String documentSelfLink, boolean isMock,
            int numberOfRemainingVMs)
            throws Throwable {

        // query VM doc to delete
        QuerySpecification resourceQuerySpec = new QuerySpecification();
        resourceQuerySpec.query
                .setTermPropertyName(ServiceDocument.FIELD_NAME_SELF_LINK)
                .setTermMatchValue(documentSelfLink);

        ResourceRemovalTaskState deletionState = new ResourceRemovalTaskState();
        deletionState.resourceQuerySpec = resourceQuerySpec;
        deletionState.isMockRequest = isMock;

        // Post/Start the ResourceRemovalTaskState...
        deletionState = TestUtils.doPost(host, deletionState, ResourceRemovalTaskState.class,
                UriUtils.buildUri(host, ResourceRemovalTaskService.FACTORY_LINK));
        // ...and wait for the task to complete
        host.waitForFinishedTask(ResourceRemovalTaskState.class, deletionState.documentSelfLink);

        ProvisioningUtils.queryDocumentsAndAssertExpectedCount(host, numberOfRemainingVMs,
                ComputeService.FACTORY_LINK, false);
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
            VerificationHost host, String resourcePoolLink, EndpointState endpointState)
            throws Throwable {

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
        azureHostDescription.statsAdapterReference = UriUtils.buildUri(host,
                AzureUriPaths.AZURE_STATS_ADAPTER);
        azureHostDescription.authCredentialsLink = endpointState.authCredentialsLink;
        azureHostDescription.endpointLink = endpointState.documentSelfLink;
        azureHostDescription.endpointLinks = new HashSet<>();
        azureHostDescription.endpointLinks.add(endpointState.documentSelfLink);
        azureHostDescription.computeHostLink = endpointState.computeHostLink;
        azureHostDescription.tenantLinks = endpointState.tenantLinks;

        azureHostDescription = TestUtils.doPost(host, azureHostDescription,
                ComputeDescription.class,
                UriUtils.buildUri(host, ComputeDescriptionService.FACTORY_LINK));

        ComputeState azureComputeHost = new ComputeState();
        azureComputeHost.id = UUID.randomUUID().toString();
        azureComputeHost.documentSelfLink = azureComputeHost.id;
        azureComputeHost.type = ComputeType.ENDPOINT_HOST;
        azureComputeHost.environmentName = ComputeDescription.ENVIRONMENT_NAME_AZURE;
        azureComputeHost.name = azureHostDescription.name;
        azureComputeHost.descriptionLink = azureHostDescription.documentSelfLink;
        azureComputeHost.resourcePoolLink = resourcePoolLink;
        azureComputeHost.endpointLink = endpointState.documentSelfLink;
        azureComputeHost.endpointLinks = new HashSet<>();
        azureComputeHost.endpointLinks.add(endpointState.documentSelfLink);
        azureComputeHost.tenantLinks = endpointState.tenantLinks;

        return TestUtils.doPost(host, azureComputeHost, ComputeState.class,
                UriUtils.buildUri(host, ComputeService.FACTORY_LINK));
    }

    public static EndpointState createDefaultEndpointState(VerificationHost host, String authLink)
            throws Throwable {

        return createEndpointState(host, authLink, EndpointType.azure);
    }

    public static EndpointState createEndpointState(VerificationHost host, String authLink,
            EndpointType endpointType)
            throws Throwable {

        return createEndpointState(host, authLink, endpointType, Region.US_WEST.name());
    }

    public static EndpointState createEndpointState(VerificationHost host, String authLink,
            EndpointType endpointType, String region)
            throws Throwable {

        EndpointState endpoint = new EndpointState();

        endpoint.endpointType = endpointType.name();
        endpoint.id = endpointType.name() + "-id";
        endpoint.name = endpointType.name() + "-name";

        endpoint.authCredentialsLink = authLink;

        if (region != null) {
            endpoint.endpointProperties = Collections.singletonMap(
                    EndpointConfigRequest.REGION_KEY, region);
        }

        endpoint.tenantLinks = Collections.singletonList(endpointType.name() + "-tenant");

        return TestUtils.doPost(host, endpoint, EndpointState.class,
                UriUtils.buildUri(host, EndpointService.FACTORY_LINK));
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
            ComputeState computeHost, EndpointState endpointState,
            AzureNicSpecs nicSpecs) throws Throwable {

        return createDefaultVMResource(host, azureVMName, computeHost, endpointState, nicSpecs,
                null /* networkRGLink */, 0);
    }

    public static ComputeState createDefaultVMResource(VerificationHost host, String azureVMName,
            ComputeState computeHost, EndpointState endpointState,
            int numberOfDisks ) throws Throwable {

        return createDefaultVMResource(host, azureVMName, computeHost, endpointState,
                DEFAULT_NIC_SPEC,
                null, numberOfDisks);
    }

    public static ComputeState createDefaultVMResource(VerificationHost host, String azureVMName,
            ComputeState computeHost, EndpointState endpointState,
            AzureNicSpecs nicSpecs, String networkRGLink) throws Throwable {
        return createDefaultVMResource(host, azureVMName, computeHost, endpointState, nicSpecs,
                networkRGLink, 0);
    }

    public static ComputeState createDefaultVMResource(VerificationHost host, String azureVMName,
            ComputeState computeHost, EndpointState endpointState,
            AzureNicSpecs nicSpecs, String networkRGLink, int numberOfAdditionalDisks)
            throws Throwable {

        final ImageSource imageSource;
        {
            // Create Public image state
            ImageState bootImage = new ImageState();

            bootImage.id = IMAGE_REFERENCE;
            bootImage.endpointType = endpointState.endpointType;

            bootImage = TestUtils.doPost(host, bootImage, ImageState.class,
                    UriUtils.buildUri(host, ImageService.FACTORY_LINK));

            imageSource = ImageSource.fromImageState(bootImage);
        }

        return createDefaultVMResource(host, azureVMName, computeHost, endpointState, nicSpecs,
                networkRGLink, imageSource, numberOfAdditionalDisks);
    }

    /**
     * This class encapsulates VerficationHost, VM's name, ComputeState, EndpointState,
     * number of additional disks, imageID etc information required for creating VM.
     */
    public static class VMResourceSpec {
        public final VerificationHost host;
        public final ComputeState computeHost;
        public final EndpointState endpointState;
        public final String azureVmName;
        public AzureTestUtil.AzureNicSpecs nicSpecs;
        public String networkRGLink;
        public ImageSource imageSource;
        public int numberOfAdditionalDisks;
        public List<String> externalDiskLinks;
        public boolean isManagedDisk;

        public VMResourceSpec(VerificationHost host, ComputeState computeHost, EndpointState endpointState,
                              String azureVmName) {
            this.host = host;
            this.computeHost = computeHost;
            this.endpointState = endpointState;
            this.azureVmName = azureVmName;
        }

        public VMResourceSpec withNicSpecs(AzureTestUtil.AzureNicSpecs nicSpecs) {
            this.nicSpecs = nicSpecs;
            return this;
        }

        public VMResourceSpec withImageSource(ImageSource imageSource) {
            this.imageSource = imageSource;
            return this;
        }

        public VMResourceSpec withNumberOfAdditionalDisks(int numberOfAdditionalDisks) {
            this.numberOfAdditionalDisks = numberOfAdditionalDisks;
            return this;
        }

        public VMResourceSpec withExternalDiskLinks(List<String> externalDiskLinks) {
            this.externalDiskLinks = externalDiskLinks;
            return this;
        }

        public VMResourceSpec withManagedDisk(boolean isManagedDisk) {
            this.isManagedDisk = isManagedDisk;
            return this;
        }
    }

    public static ImageSource createImageSource(VerificationHost host,
                                                EndpointState endpointState, String imageRefId) throws Throwable {
        ImageSource imageSource;
        ImageState bootImage = new ImageState();

        bootImage.id = imageRefId;
        bootImage.endpointType = endpointState.endpointType;

        bootImage = TestUtils.doPost(host, bootImage, ImageState.class, UriUtils.buildUri(host, ImageService.FACTORY_LINK));

        imageSource = ImageSource.fromImageState(bootImage);

        return imageSource;
    }

    /**
     * Separate method to create VM from given spec
     */
    public static ComputeState createVMResourceFromSpec(VMResourceSpec spec) throws Throwable {

        final String defaultVmRGName = spec.azureVmName;

        // TODO Modify createDefaultResourceGroupState() to have only spec parameter passed
        final ResourceGroupState defaultVmRG = createDefaultResourceGroupState(
                spec.host, defaultVmRGName, spec.computeHost, spec.endpointState,
                ResourceGroupStateType.AzureResourceGroup);

        final String defaultVmRGLink = defaultVmRG.documentSelfLink;

        if (spec.networkRGLink == null) {
            // The RG where the VM is deployed is also used as RG for the Network!
            spec.networkRGLink = defaultVmRGLink;
        }
        // The RG where the VM is deployed is also used as RG for the SecurityGroup!
        final String sgRGLink = defaultVmRGLink;

        // Create resource group with a different type. It should be filtered out.
        ResourceGroupState azureStorageContainerRG = createDefaultResourceGroupState(
                spec.host, AZURE_STORAGE_CONTAINER_RG_NAME, spec.computeHost, spec.endpointState,
                ResourceGroupStateType.AzureStorageContainer);

        final Set<String> networkRGLinks = new HashSet<>();
        networkRGLinks.add(spec.networkRGLink);
        networkRGLinks.add(azureStorageContainerRG.documentSelfLink);

        final Set<String> sgRGLinks = new HashSet<>();
        sgRGLinks.add(sgRGLink);
        sgRGLinks.add(azureStorageContainerRG.documentSelfLink);

        AuthCredentialsServiceState azureVMAuth = new AuthCredentialsServiceState();
        azureVMAuth.userEmail = AZURE_ADMIN_USERNAME;
        azureVMAuth.privateKey = AZURE_ADMIN_PASSWORD;
        azureVMAuth = TestUtils.doPost(spec.host, azureVMAuth, AuthCredentialsServiceState.class,
                UriUtils.buildUri(spec.host, AuthCredentialsService.FACTORY_LINK));

        // Create a VM desc
        ComputeDescription azureVMDesc = new ComputeDescription();
        azureVMDesc.id = UUID.randomUUID().toString();
        azureVMDesc.documentSelfLink = azureVMDesc.id;
        azureVMDesc.name = azureVMDesc.id;
        azureVMDesc.regionId = AZURE_RESOURCE_GROUP_LOCATION;
        azureVMDesc.authCredentialsLink = azureVMAuth.documentSelfLink;
        azureVMDesc.tenantLinks = spec.endpointState.tenantLinks;
        azureVMDesc.endpointLink = spec.endpointState.documentSelfLink;
        azureVMDesc.endpointLinks = new HashSet<>();
        azureVMDesc.endpointLinks.add(spec.endpointState.documentSelfLink);
        azureVMDesc.computeHostLink = spec.endpointState.computeHostLink;
        azureVMDesc.instanceType = AZURE_VM_SIZE;
        azureVMDesc.environmentName = ComputeDescription.ENVIRONMENT_NAME_AZURE;
        azureVMDesc.customProperties = new HashMap<>();

        // set the create service to the azure instance service
        azureVMDesc.instanceAdapterReference = UriUtils.buildUri(spec.host,
                AzureUriPaths.AZURE_INSTANCE_ADAPTER);

        azureVMDesc.powerAdapterReference = UriUtils.buildUri(spec.host,
                AzureUriPaths.AZURE_POWER_ADAPTER);

        azureVMDesc = TestUtils.doPost(spec.host, azureVMDesc, ComputeDescription.class,
                UriUtils.buildUri(spec.host, ComputeDescriptionService.FACTORY_LINK));


        DiskState rootDisk = new DiskState();
        rootDisk.name = spec.azureVmName + "-boot-disk";
        rootDisk.id = UUID.randomUUID().toString();
        rootDisk.documentSelfLink = rootDisk.id;
        rootDisk.type = DiskType.HDD;

        // Custom OSDisk size of 32 GBs
        rootDisk.capacityMBytes = AZURE_CUSTOM_OSDISK_SIZE;

        rootDisk.bootOrder = 1;

        rootDisk.endpointLink = spec.endpointState.documentSelfLink;
        rootDisk.endpointLinks = new HashSet<>();
        rootDisk.endpointLinks.add(spec.endpointState.documentSelfLink);
        rootDisk.computeHostLink = spec.endpointState.computeHostLink;
        rootDisk.tenantLinks = spec.endpointState.tenantLinks;

        rootDisk.customProperties = new HashMap<>();
        rootDisk.customProperties.put(AZURE_OSDISK_CACHING, DEFAULT_OS_DISK_CACHING.name());

        if (spec.imageSource.type == Type.PRIVATE_IMAGE) {
            if (spec.isManagedDisk) {
                rootDisk.imageLink = spec.imageSource.asImageState().documentSelfLink;
                rootDisk.customProperties.put(AzureConstants.AZURE_MANAGED_DISK_TYPE, SkuName.STANDARD_LRS.toString());
            }
        } else if (spec.imageSource.type == Type.PUBLIC_IMAGE) {
            if (spec.isManagedDisk) {
                rootDisk.imageLink = spec.imageSource.asImageState().documentSelfLink;
                rootDisk.customProperties.put(AzureConstants.AZURE_MANAGED_DISK_TYPE, SkuName.STANDARD_LRS.toString());
            } else {
                rootDisk.imageLink = spec.imageSource.asImageState().documentSelfLink;
                rootDisk.customProperties.put(
                        AzureConstants.AZURE_STORAGE_ACCOUNT_NAME,
                        (spec.azureVmName + "sa").replaceAll("[_-]", "").toLowerCase());
                rootDisk.customProperties.put(
                        AzureConstants.AZURE_STORAGE_ACCOUNT_RG_NAME,
                        defaultVmRGName);
                rootDisk.customProperties.put(
                        AzureConstants.AZURE_STORAGE_ACCOUNT_TYPE,
                        AZURE_STORAGE_ACCOUNT_TYPE);
            }
        } else if (spec.imageSource.type == Type.IMAGE_REFERENCE) {
            rootDisk.sourceImageReference = URI.create(spec.imageSource.asRef());
        }

        rootDisk = TestUtils.doPost(spec.host, rootDisk, DiskState.class,
                UriUtils.buildUri(spec.host, DiskService.FACTORY_LINK));

        List<String> vmDisks = new ArrayList<>();
        vmDisks.add(rootDisk.documentSelfLink);

        //create additional disks
        if (spec.numberOfAdditionalDisks > 0) {
            // TODO Need to modify createAdditionalDisks() to have only spec passed as parameter
            vmDisks.addAll(createAdditionalDisks(spec.host, spec.azureVmName,
                    spec.endpointState, spec.numberOfAdditionalDisks, spec.isManagedDisk));
        }

        // Add external existing data disks (if present) to the list for attaching
        if (null != spec.externalDiskLinks && spec.externalDiskLinks.size() > 0) {
            vmDisks.addAll(spec.externalDiskLinks);
        }

        // Create NICs
        List<String> nicLinks = createDefaultNicStates(
                spec.host, spec.computeHost, spec.endpointState, networkRGLinks, sgRGLinks, spec.nicSpecs)
                .stream()
                .map(nic -> nic.documentSelfLink)
                .collect(Collectors.toList());

        // Finally create the compute resource state to provision using all constructs above.
        ComputeState computeState = new ComputeState();
        computeState.id = UUID.randomUUID().toString();
        computeState.name = spec.azureVmName;
        computeState.parentLink = spec.computeHost.documentSelfLink;
        computeState.type = ComputeType.VM_GUEST;
        computeState.environmentName = ComputeDescription.ENVIRONMENT_NAME_AZURE;
        computeState.descriptionLink = azureVMDesc.documentSelfLink;
        computeState.resourcePoolLink = spec.computeHost.resourcePoolLink;
        computeState.diskLinks = vmDisks;
        computeState.networkInterfaceLinks = nicLinks;
        computeState.customProperties = Collections.singletonMap(RESOURCE_GROUP_NAME, defaultVmRGName);
        computeState.groupLinks = Collections.singleton(defaultVmRGLink);
        computeState.endpointLink = spec.endpointState.documentSelfLink;
        computeState.endpointLinks = new HashSet<>();
        computeState.endpointLinks.add(spec.endpointState.documentSelfLink);
        computeState.computeHostLink = spec.endpointState.computeHostLink;
        computeState.tenantLinks = spec.endpointState.tenantLinks;
        computeState.creationTimeMicros = TimeUnit.MILLISECONDS
                .toMicros(System.currentTimeMillis());

        return TestUtils.doPost(spec.host, computeState, ComputeState.class,
                UriUtils.buildUri(spec.host, ComputeService.FACTORY_LINK));

    }

    public static ComputeState createDefaultVMResource(VerificationHost host, String azureVMName,
            ComputeState computeHost, EndpointState endpointState,
            AzureNicSpecs nicSpecs, String networkRGLink, ImageSource imageSource, int numberOfAdditionalDisks)
            throws Throwable {

        final String defaultVmRGName = azureVMName;

        final ResourceGroupState defaultVmRG = createDefaultResourceGroupState(
                host, defaultVmRGName, computeHost, endpointState,
                ResourceGroupStateType.AzureResourceGroup);

        final String defaultVmRGLink = defaultVmRG.documentSelfLink;

        if (networkRGLink == null) {
            // The RG where the VM is deployed is also used as RG for the Network!
            networkRGLink = defaultVmRGLink;
        }
        // The RG where the VM is deployed is also used as RG for the SecurityGroup!
        final String sgRGLink = defaultVmRGLink;

        // Create resource group with a different type. It should be filtered out.
        ResourceGroupState azureStorageContainerRG = createDefaultResourceGroupState(
                host, AZURE_STORAGE_CONTAINER_RG_NAME, computeHost, endpointState,
                ResourceGroupStateType.AzureStorageContainer);

        final Set<String> networkRGLinks = new HashSet<>();
        networkRGLinks.add(networkRGLink);
        networkRGLinks.add(azureStorageContainerRG.documentSelfLink);

        final Set<String> sgRGLinks = new HashSet<>();
        sgRGLinks.add(sgRGLink);
        sgRGLinks.add(azureStorageContainerRG.documentSelfLink);

        AuthCredentialsServiceState azureVMAuth = new AuthCredentialsServiceState();
        azureVMAuth.userEmail = AZURE_ADMIN_USERNAME;
        azureVMAuth.privateKey = AZURE_ADMIN_PASSWORD;
        azureVMAuth = TestUtils.doPost(host, azureVMAuth, AuthCredentialsServiceState.class,
                UriUtils.buildUri(host, AuthCredentialsService.FACTORY_LINK));

        // Create a VM desc
        ComputeDescription azureVMDesc = new ComputeDescription();
        azureVMDesc.id = UUID.randomUUID().toString();
        azureVMDesc.documentSelfLink = azureVMDesc.id;
        azureVMDesc.name = azureVMDesc.id;
        azureVMDesc.regionId = AZURE_RESOURCE_GROUP_LOCATION;
        azureVMDesc.authCredentialsLink = azureVMAuth.documentSelfLink;
        azureVMDesc.tenantLinks = endpointState.tenantLinks;
        azureVMDesc.endpointLink = endpointState.documentSelfLink;
        azureVMDesc.endpointLinks = new HashSet<>();
        azureVMDesc.endpointLinks.add(endpointState.documentSelfLink);
        azureVMDesc.computeHostLink = computeHost.documentSelfLink;
        azureVMDesc.instanceType = AZURE_VM_SIZE;
        azureVMDesc.environmentName = ComputeDescription.ENVIRONMENT_NAME_AZURE;
        azureVMDesc.customProperties = new HashMap<>();

        // set the create service to the azure instance service
        azureVMDesc.instanceAdapterReference = UriUtils.buildUri(host,
                AzureUriPaths.AZURE_INSTANCE_ADAPTER);

        azureVMDesc.powerAdapterReference = UriUtils.buildUri(host,
                AzureUriPaths.AZURE_POWER_ADAPTER);

        azureVMDesc = TestUtils.doPost(host, azureVMDesc, ComputeDescription.class,
                UriUtils.buildUri(host, ComputeDescriptionService.FACTORY_LINK));

        List<String> vmDisks = new ArrayList<>();

        DiskState rootDisk = new DiskState();
        rootDisk.name = azureVMName + "-boot-disk";
        rootDisk.id = UUID.randomUUID().toString();
        rootDisk.documentSelfLink = rootDisk.id;
        rootDisk.type = DiskType.HDD;
        // Custom OSDisk size of 32 GBs
        rootDisk.capacityMBytes = AZURE_CUSTOM_OSDISK_SIZE;
        if (imageSource.type == Type.PRIVATE_IMAGE || imageSource.type == Type.PUBLIC_IMAGE) {
            rootDisk.imageLink = imageSource.asImageState().documentSelfLink;
        } else if (imageSource.type == Type.IMAGE_REFERENCE) {
            rootDisk.sourceImageReference = URI.create(imageSource.asRef());
        }
        rootDisk.bootOrder = 1;

        rootDisk.endpointLink = endpointState.documentSelfLink;
        rootDisk.endpointLinks = new HashSet<>();
        rootDisk.endpointLinks.add(endpointState.documentSelfLink);
        rootDisk.computeHostLink = computeHost.documentSelfLink;
        rootDisk.tenantLinks = endpointState.tenantLinks;

        rootDisk.customProperties = new HashMap<>();
        rootDisk.customProperties.put(AZURE_OSDISK_CACHING, DEFAULT_OS_DISK_CACHING.name());

        rootDisk.customProperties.put(
                AzureConstants.AZURE_STORAGE_ACCOUNT_NAME,
                (azureVMName + "sa").replaceAll("[_-]", "").toLowerCase());
        rootDisk.customProperties.put(
                AzureConstants.AZURE_STORAGE_ACCOUNT_RG_NAME,
                defaultVmRGName);
        rootDisk.customProperties.put(
                AzureConstants.AZURE_STORAGE_ACCOUNT_TYPE,
                AZURE_STORAGE_ACCOUNT_TYPE);

        rootDisk = TestUtils.doPost(host, rootDisk, DiskState.class,
                UriUtils.buildUri(host, DiskService.FACTORY_LINK));

        vmDisks.add(rootDisk.documentSelfLink);

        //create additional disks
        vmDisks.addAll(createAdditionalDisks(host,azureVMName,endpointState,numberOfAdditionalDisks, false));
        // Create NICs
        List<String> nicLinks = createDefaultNicStates(
                host, computeHost, endpointState, networkRGLinks, sgRGLinks, nicSpecs)
                        .stream()
                        .map(nic -> nic.documentSelfLink)
                        .collect(Collectors.toList());

        // Finally create the compute resource state to provision using all constructs above.
        ComputeState computeState = new ComputeState();
        computeState.id = UUID.randomUUID().toString();
        computeState.name = azureVMName;
        computeState.parentLink = computeHost.documentSelfLink;
        computeState.type = ComputeType.VM_GUEST;
        computeState.environmentName = ComputeDescription.ENVIRONMENT_NAME_AZURE;
        computeState.descriptionLink = azureVMDesc.documentSelfLink;
        computeState.resourcePoolLink = computeHost.resourcePoolLink;
        computeState.diskLinks = vmDisks;
        computeState.networkInterfaceLinks = nicLinks;
        computeState.customProperties = Collections.singletonMap(RESOURCE_GROUP_NAME, defaultVmRGName);
        computeState.groupLinks = Collections.singleton(defaultVmRGLink);
        computeState.endpointLink = endpointState.documentSelfLink;
        computeState.endpointLinks = new HashSet<>();
        computeState.endpointLinks.add(endpointState.documentSelfLink);
        computeState.computeHostLink = computeHost.documentSelfLink;
        computeState.tenantLinks = endpointState.tenantLinks;
        computeState.tagLinks = createTagStateSet(host, endpointState.tenantLinks,
                        TAG_KEY_TYPE, AzureConstants.AzureResourceType.azure_vm.toString());
        computeState.creationTimeMicros = TimeUnit.MILLISECONDS
                .toMicros(System.currentTimeMillis());

        return TestUtils.doPost(host, computeState, ComputeState.class,
                UriUtils.buildUri(host, ComputeService.FACTORY_LINK));
    }

    public static TagState getTagState(List<String> tenantLinks, String key, String value) {

        return TagsUtil.newTagState(key, value, EnumSet.of(TagState.TagOrigin.SYSTEM), tenantLinks);
    }

    public static TagState createTagState(VerificationHost host, List<String> tenantLinks,
            String key, String value) throws Throwable {

        TagState tagState = getTagState(tenantLinks, key, value);
        return TestUtils.doPost(host, tagState, TagState.class,
                UriUtils.buildUri(host, TagService.FACTORY_LINK));
    }

    public static Set<String> createTagStateSet(VerificationHost host, List<String> tenantLinks,
            String key, String value) throws Throwable {

        return Collections.singleton(
                createTagState(host, tenantLinks, key, value).documentSelfLink);
    }

    public static List<String> createAdditionalDisks(VerificationHost host,String azureVMName,
            EndpointState endpointState,  int numberOfDisks, boolean isManagedDisk) throws Throwable {

        List<String> diskStateArrayList = new ArrayList<>();
        for (int i = 0; i < numberOfDisks; i++ ) {
            DiskState dataDisk = new DiskState();
            dataDisk.name = azureVMName + "-data-disk-" + i;
            dataDisk.id = UUID.randomUUID().toString();
            dataDisk.type = DiskType.HDD;
            dataDisk.capacityMBytes = AZURE_CUSTOM_DATA_DISK_SIZE; // Custom Data Disk size of 20GB
            dataDisk.bootOrder = 2;
            dataDisk.endpointLink = endpointState.documentSelfLink;
            dataDisk.endpointLinks = new HashSet<>();
            dataDisk.endpointLinks.add(endpointState.documentSelfLink);
            dataDisk.computeHostLink = endpointState.computeHostLink;
            dataDisk.tenantLinks = endpointState.tenantLinks;
            dataDisk.storageType = AZURE_STORAGE_DISKS;
            dataDisk.customProperties = new HashMap<>();
            dataDisk.customProperties.put(AZURE_DATA_DISK_CACHING, DEFAULT_DATA_DISK_CACHING.toString());

            if (!isManagedDisk) {
                dataDisk.customProperties.put(AzureConstants.AZURE_STORAGE_ACCOUNT_NAME,
                        (azureVMName + "sa").replace("-", "").toLowerCase());
                dataDisk.customProperties.put(AzureConstants.AZURE_STORAGE_ACCOUNT_RG_NAME,
                        azureVMName);
                dataDisk.customProperties.put(AzureConstants.AZURE_STORAGE_ACCOUNT_TYPE,
                        AZURE_STORAGE_ACCOUNT_TYPE);
            } else {
                dataDisk.customProperties.put(
                        AzureConstants.AZURE_MANAGED_DISK_TYPE, SkuName.STANDARD_LRS.toString());
            }

            dataDisk = TestUtils.doPost(host, dataDisk, DiskState.class,
                    UriUtils.buildUri(host, DiskService.FACTORY_LINK));

            diskStateArrayList.add(dataDisk.documentSelfLink);
        }
        return  diskStateArrayList;

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
            ComputeState computeHost, EndpointState endpointState) throws Throwable {

        AuthCredentialsServiceState auth = new AuthCredentialsServiceState();
        auth.customProperties = new HashMap<>();
        auth.customProperties.put(AZURE_STORAGE_ACCOUNT_KEY1, randomString(15));
        auth.customProperties.put(AZURE_STORAGE_ACCOUNT_KEY2, randomString(15));

        auth = TestUtils.doPost(host, auth, AuthCredentialsServiceState.class,
                UriUtils.buildUri(host, AuthCredentialsService.FACTORY_LINK));

        String authLink = UriUtils.buildUriPath(AuthCredentialsService.FACTORY_LINK,
                auth.documentSelfLink);

        // Create a storage description
        StorageDescription storageDesc = new StorageDescription();
        storageDesc.id = "testStorAcct-" + randomString(4);
        storageDesc.name = storageAccountName;
        storageDesc.regionId = AZURE_RESOURCE_GROUP_LOCATION;
        storageDesc.computeHostLink = computeHost.documentSelfLink;
        storageDesc.authCredentialsLink = authLink;
        storageDesc.resourcePoolLink = computeHost.resourcePoolLink;

        storageDesc.tenantLinks = endpointState.tenantLinks;
        storageDesc.endpointLink = endpointState.documentSelfLink;
        storageDesc.endpointLinks = new HashSet<>();
        storageDesc.endpointLinks.add(endpointState.documentSelfLink);
        storageDesc.customProperties = new HashMap<>();
        storageDesc.customProperties.put(AZURE_STORAGE_TYPE, AZURE_STORAGE_ACCOUNTS);

        return TestUtils.doPost(host, storageDesc, StorageDescription.class,
                UriUtils.buildUri(host, StorageDescriptionService.FACTORY_LINK));
    }

    public static ResourceGroupState createDefaultResourceGroupState(VerificationHost host,
            String resourceGroupName, ComputeState computeHost, EndpointState endpointState,
            ResourceGroupStateType resourceGroupType)
            throws Throwable {

        ResourceGroupState rGroupState = new ResourceGroupState();

        rGroupState.name = resourceGroupName;
        rGroupState.id = rGroupState.name + randomString(4);

        rGroupState.groupLinks = Collections.singleton(rGroupState.name + randomString(4));

        rGroupState.tenantLinks = endpointState.tenantLinks;
        rGroupState.regionId = AZURE_RESOURCE_GROUP_LOCATION;

        rGroupState.endpointLink = endpointState.documentSelfLink;
        rGroupState.endpointLinks = new HashSet<>();
        rGroupState.endpointLinks.add(endpointState.documentSelfLink);
        rGroupState.computeHostLink = computeHost.documentSelfLink;
        rGroupState.customProperties = new HashMap<>();
        rGroupState.customProperties.put(COMPUTE_HOST_LINK_PROP_NAME, computeHost.documentSelfLink);
        rGroupState.customProperties.put(CUSTOM_PROP_ENDPOINT_LINK, endpointState.documentSelfLink);
        rGroupState.customProperties.put(AZURE_STORAGE_TYPE, AZURE_STORAGE_CONTAINERS);
        rGroupState.customProperties.put(AZURE_STORAGE_CONTAINER_LEASE_LAST_MODIFIED,
                randomString(10));
        rGroupState.customProperties.put(AZURE_STORAGE_CONTAINER_LEASE_STATE,
                randomString(5));
        rGroupState.customProperties.put(AZURE_STORAGE_CONTAINER_LEASE_STATUS,
                randomString(5));
        rGroupState.customProperties.put(ComputeProperties.RESOURCE_TYPE_KEY,
                resourceGroupType.name());

        return TestUtils.doPost(host, rGroupState,
                ResourceGroupState.class,
                UriUtils.buildUri(host, ResourceGroupService.FACTORY_LINK));
    }

    public static SecurityGroupState createSecurityGroupState(VerificationHost host, EndpointState
            endpointState, String name,
            List<Rule> inboundRules, List<Rule> outboudRules) throws
            Throwable {
        SecurityGroupState securityGroupState = new SecurityGroupState();
        securityGroupState.id = name;
        securityGroupState.name = name;
        securityGroupState.instanceAdapterReference = UriUtils.buildUri(host,
                AzureSecurityGroupService.SELF_LINK);
        securityGroupState.endpointLink = endpointState.documentSelfLink;
        securityGroupState.endpointLinks = new HashSet<>();
        securityGroupState.endpointLinks.add(endpointState.documentSelfLink);
        securityGroupState.computeHostLink = endpointState.computeHostLink;
        securityGroupState.tenantLinks = endpointState.tenantLinks;
        securityGroupState.ingress = inboundRules;
        securityGroupState.egress = outboudRules;
        securityGroupState.authCredentialsLink = endpointState.authCredentialsLink;
        securityGroupState.resourcePoolLink = "test-resource-pool-link";
        securityGroupState.regionId = AZURE_RESOURCE_GROUP_LOCATION;

        return TestUtils.doPost(host, securityGroupState,
                SecurityGroupState.class,
                UriUtils.buildUri(host, SecurityGroupService.FACTORY_LINK));
    }

    /**
     * Create a disk state
     */
    public static DiskState createDefaultDiskState(VerificationHost host, String diskName,
            String storageContainerLink, ComputeState computeHost, EndpointState endpointState)
            throws Throwable {

        DiskState diskState = new DiskState();

        diskState.id = UUID.randomUUID().toString();
        diskState.documentSelfLink = diskState.id;
        diskState.name = diskName;
        diskState.computeHostLink = computeHost.documentSelfLink;
        diskState.resourcePoolLink = computeHost.resourcePoolLink;

        diskState.tenantLinks = endpointState.tenantLinks;
        diskState.endpointLink = endpointState.documentSelfLink;
        diskState.endpointLinks = new HashSet<>();
        diskState.endpointLinks.add(endpointState.documentSelfLink);

        List<String> tenantLinks = Collections.singletonList( EndpointType.azure.name() + "-tenant");
        diskState.tagLinks = createTagStateSet(host, endpointState.tenantLinks,
                TAG_KEY_TYPE, AzureResourceType.azure_vhd.name());

        diskState.storageDescriptionLink = storageContainerLink;
        diskState.type = DEFAULT_DISK_TYPE;
        diskState.storageType = AZURE_STORAGE_DISKS;
        diskState.capacityMBytes = DEFAULT_DISK_CAPACITY;

        return TestUtils.doPost(host, diskState, DiskState.class,
                UriUtils.buildUri(host, DiskService.FACTORY_LINK));
    }

    /*
     * NOTE: It is highly recommended to keep this method in sync with its AWS counterpart:
     * TestAWSSetupUtils.createAWSNicStates
     */
    public static List<NetworkInterfaceState> createDefaultNicStates(
            VerificationHost host,
            ComputeState computeHost, EndpointState endpointState,
            Set<String> networkRGLinks,
            Set<String> sgRGLinks,
            AzureNicSpecs azureNicSpecs) throws Throwable {

        // Create network state.
        NetworkState networkState;
        {
            networkState = new NetworkState();
            networkState.id = azureNicSpecs.network.name;
            networkState.name = azureNicSpecs.network.name;
            networkState.subnetCIDR = azureNicSpecs.network.cidr;
            networkState.authCredentialsLink = endpointState.authCredentialsLink;
            networkState.endpointLink = endpointState.documentSelfLink;
            networkState.endpointLinks = new HashSet<>();
            networkState.endpointLinks.add(endpointState.documentSelfLink);
            networkState.tenantLinks = endpointState.tenantLinks;
            networkState.resourcePoolLink = computeHost.resourcePoolLink;
            networkState.groupLinks = networkRGLinks;
            networkState.regionId = azureNicSpecs.network.zoneId;
            networkState.instanceAdapterReference = UriUtils.buildUri(host,
                    DEFAULT_INSTANCE_ADAPTER_REFERENCE);
            networkState.tagLinks = createTagStateSet(host, endpointState.tenantLinks,
                    TAG_KEY_TYPE, azure_vnet.name());
            networkState.computeHostLink = endpointState.computeHostLink;

            networkState = TestUtils.doPost(host, networkState,
                    NetworkState.class,
                    UriUtils.buildUri(host, NetworkService.FACTORY_LINK));
        }

        // Create NIC states.
        List<NetworkInterfaceState> nics = new ArrayList<>();

        for (int i = 0; i < azureNicSpecs.nicSpecs.size(); i++) {

            NicSpec nicSpec = azureNicSpecs.nicSpecs.get(i);

            // Create subnet state per NIC.
            SubnetState subnetState;
            {
                subnetState = new SubnetState();

                subnetState.id = azureNicSpecs.nicSpecs.get(i).subnetSpec.name;
                subnetState.name = azureNicSpecs.nicSpecs.get(i).subnetSpec.name;
                subnetState.subnetCIDR = azureNicSpecs.nicSpecs.get(i).subnetSpec.cidr;
                subnetState.zoneId = azureNicSpecs.nicSpecs.get(i).subnetSpec.zoneId;
                subnetState.networkLink = networkState.documentSelfLink;
                subnetState.endpointLink = endpointState.documentSelfLink;
                subnetState.endpointLinks = new HashSet<>();
                subnetState.endpointLinks.add(endpointState.documentSelfLink);
                subnetState.computeHostLink = endpointState.computeHostLink;
                subnetState.tenantLinks = endpointState.tenantLinks;
                subnetState.tagLinks = createTagStateSet(host, endpointState.tenantLinks,
                        TAG_KEY_TYPE, azure_subnet.name());

                subnetState = TestUtils.doPost(host, subnetState,
                        SubnetState.class,
                        UriUtils.buildUri(host, SubnetService.FACTORY_LINK));
            }

            // Create security group state
            SecurityGroupState securityGroupState;
            {
                securityGroupState = new SecurityGroupState();
                securityGroupState.name = AZURE_SECURITY_GROUP_NAME;
                securityGroupState.authCredentialsLink = endpointState.authCredentialsLink;
                securityGroupState.endpointLink = endpointState.documentSelfLink;
                securityGroupState.endpointLinks = new HashSet<>();
                securityGroupState.endpointLinks.add(endpointState.documentSelfLink);
                securityGroupState.computeHostLink = endpointState.computeHostLink;
                securityGroupState.tenantLinks = endpointState.tenantLinks;
                securityGroupState.groupLinks = sgRGLinks;
                securityGroupState.regionId = "regionId";
                securityGroupState.resourcePoolLink = "/link/to/rp";
                securityGroupState.instanceAdapterReference = new URI(
                        "http://instanceAdapterReference");

                {
                    Rule ssh = new Rule();
                    ssh.name = "ssh-in";
                    ssh.protocol = "tcp";
                    ssh.ipRangeCidr = "0.0.0.0/0";
                    ssh.ports = "22";

                    securityGroupState.ingress = Collections.singletonList(ssh);
                }

                {
                    Rule out = new Rule();
                    out.name = "out";
                    out.protocol = "tcp";
                    out.ipRangeCidr = "0.0.0.0/0";
                    out.ports = SecurityGroupService.ALL_PORTS;

                    securityGroupState.egress = Collections.singletonList(out);
                }

                securityGroupState = TestUtils.doPost(host, securityGroupState,
                        SecurityGroupState.class,
                        UriUtils.buildUri(host, SecurityGroupService.FACTORY_LINK));
            }

            // Create NIC description.
            NetworkInterfaceDescription nicDescription;
            {
                nicDescription = new NetworkInterfaceDescription();

                nicDescription.id = "nicDesc" + i;
                nicDescription.name = "nicDesc" + i;
                nicDescription.deviceIndex = i;
                nicDescription.assignPublicIpAddress = azureNicSpecs.assignPublicIpAddress;
                nicDescription.tenantLinks = endpointState.tenantLinks;
                nicDescription.endpointLink = endpointState.documentSelfLink;
                nicDescription.endpointLinks = new HashSet<>();
                nicDescription.endpointLinks.add(endpointState.documentSelfLink);
                nicDescription.computeHostLink = endpointState.computeHostLink;
                nicDescription.assignment = nicSpec.getIpAssignment();
                // if staticIp is null, it will be assigned automatically by DHCP.
                nicDescription.address = nicSpec.ip();


                nicDescription = TestUtils.doPost(host, nicDescription,
                        NetworkInterfaceDescription.class,
                        UriUtils.buildUri(host, NetworkInterfaceDescriptionService.FACTORY_LINK));
            }

            NetworkInterfaceState nicState = new NetworkInterfaceState();

            nicState.id = "nic" + i;
            nicState.name = "nic" + i;
            nicState.deviceIndex = nicDescription.deviceIndex;
            nicState.networkInterfaceDescriptionLink = nicDescription.documentSelfLink;
            nicState.subnetLink = subnetState.documentSelfLink;
            nicState.networkLink = subnetState.networkLink;
            nicState.tenantLinks = endpointState.tenantLinks;
            nicState.endpointLink = endpointState.documentSelfLink;
            nicState.endpointLinks = new HashSet<>();
            nicState.endpointLinks.add(endpointState.documentSelfLink);
            nicState.computeHostLink = endpointState.computeHostLink;
            if (nicSpec.getIpAssignment() == IpAssignment.STATIC) {
                // There is a rule in:
                // \photon-model\photon-model\src\main\java\com\vmware\photon\controller\model\resources\NetworkInterfaceService.java::validateState()
                // // which will throws java.lang.IllegalArgumentException: both networkLink and IP
                // cannot be set
                nicState.networkLink = null;
            }

            if (i == 0) {
                // Attach security group only on the primary nic.
                nicState.securityGroupLinks = Collections.singletonList(
                        securityGroupState.documentSelfLink);
            }
            nicState.tagLinks = Collections.singleton(TagsUtil.newTagState
                    (TAG_KEY_TYPE, azure_net_interface.name(), false, endpointState
                            .tenantLinks).documentSelfLink);

            nicState = TestUtils.doPost(host, nicState,
                    NetworkInterfaceState.class,
                    UriUtils.buildUri(host, NetworkInterfaceService.FACTORY_LINK));

            nics.add(nicState);
        }

        return nics;
    }

    /**
     * Create SHARED vNet-Subnets-Gateway in a separate RG.
     * <p>
     * NOTE1: The names of the vNet and Subnets MUST be equal to the ones set by
     * AzureTestUtil.createDefaultNicStates.
     * <p>
     * NOTE2: Since this is SHARED vNet it's not deleted after the test.
     */
    public static ResourceGroupInner createResourceGroupWithSharedNetwork(
            ResourceManagementClientImpl resourceManagementClient,
            NetworkManagementClientImpl networkManagementClient,
            AzureNicSpecs nicSpecs) throws Throwable {

        // Create the shared RG itself
        ResourceGroupInner sharedNetworkRG = createResourceGroup(resourceManagementClient,
                AZURE_SHARED_NETWORK_RESOURCE_GROUP_NAME);

        // Create shared vNet-Subnet-Gateway under shared RG
        createAzureVirtualNetwork(sharedNetworkRG.name(), nicSpecs,
                networkManagementClient);

        // Create shared NSG under shared RG
        createAzureNetworkSecurityGroup(sharedNetworkRG.name(), networkManagementClient);

        return sharedNetworkRG;
    }

    public static ResourceGroupInner createResourceGroup(
            ResourceManagementClientImpl resourceManagementClient,
            String name) {
        ResourceGroupInner rgParams = new ResourceGroupInner();
        rgParams.withName(name);
        rgParams.withLocation(AzureTestUtil.AZURE_RESOURCE_GROUP_LOCATION);

        ResourceGroupInner resourceGroup = resourceManagementClient.resourceGroups()
                .createOrUpdate(rgParams.name(), rgParams);
        return resourceGroup;
    }

    public static void deleteResourceGroup(ResourceManagementClientImpl resourceManagementClient,
            String name) {
        resourceManagementClient.resourceGroups().delete(name);
    }

    public static ImageSource createPrivateImageSource(
            VerificationHost host,
            EndpointState endpointState) throws Throwable {

        ImageState bootImage = new ImageState();

        // Change id and name according to your custom image details
        bootImage.id = "/subscriptions/817776f9-ef2a-4681-9774-a66f9be11e22/resourceGroups/Images/providers/Microsoft.Compute/images/SourceImageLinuxUnmanagedAPI";
        bootImage.name = "SourceImageLinuxUnmanagedAPI";
        bootImage.osFamily = "Linux";
        bootImage.tenantLinks = endpointState.tenantLinks;
        bootImage.endpointLink = endpointState.documentSelfLink;
        bootImage.endpointLinks = new HashSet<>();
        bootImage.endpointLinks.add(endpointState.documentSelfLink);
        List<DiskConfiguration> imageDisks = new ArrayList<>();
        DiskConfiguration osDiskConfig = new DiskConfiguration();

        imageDisks.add(osDiskConfig);

        // Add/Remove data disks and also map correct LUN values based on your private image configuration
        DiskConfiguration dataDiskConfig1 = new DiskConfiguration();
        dataDiskConfig1.properties = new HashMap<>();
        dataDiskConfig1.properties.put(AzureConstants.AZURE_DISK_LUN, "0");

        imageDisks.add(dataDiskConfig1);

        DiskConfiguration dataDiskConfig2 = new DiskConfiguration();
        dataDiskConfig2.properties = new HashMap<>();
        dataDiskConfig2.properties.put(AzureConstants.AZURE_DISK_LUN, "1");

        imageDisks.add(dataDiskConfig2);

        bootImage.diskConfigs = imageDisks;

        bootImage = TestUtils.doPost(host, bootImage, ImageState.class,
                UriUtils.buildUri(host, ImageService.FACTORY_LINK));

        return ImageSource.fromImageState(bootImage);
    }

    private static void createAzureVirtualNetwork(String resourceGroupName,
            AzureNicSpecs azureNicSpecs,
            NetworkManagementClientImpl networkManagementClient) throws Exception {

        try {
            VirtualNetworkInner vNet = new VirtualNetworkInner();
            vNet.withLocation(azureNicSpecs.network.zoneId);

            AddressSpace addressSpace = new AddressSpace();
            addressSpace.withAddressPrefixes(
                    Collections.singletonList(azureNicSpecs.network.cidr));
            vNet.withAddressSpace(addressSpace);

            List<SubnetInner> subnetList = new ArrayList<>();

            for (int i = 0; i < azureNicSpecs.nicSpecs.size(); i++) {
                SubnetInner subnet = new SubnetInner();
                subnet.withName(azureNicSpecs.nicSpecs.get(i).subnetSpec.name);
                subnet.withAddressPrefix(azureNicSpecs.nicSpecs.get(i).subnetSpec.cidr);

                subnetList.add(subnet);
            }

            vNet.withSubnets(subnetList);

            networkManagementClient.virtualNetworks().createOrUpdate(
                    resourceGroupName, azureNicSpecs.network.name, vNet);

            addAzureGatewayToVirtualNetwork(resourceGroupName, azureNicSpecs, networkManagementClient);

        } catch (CloudException ex) {
            /*
             * CloudException is thrown if the vNet already exists and we are trying to do an
             * update, because there are objects (GatewaySubnet) attached to it
             */
            assertTrue(ex.body().code().equals("InUseSubnetCannotBeDeleted"));
        }
    }

    private static void createAzureNetworkSecurityGroup(String resourceGroupName,
            NetworkManagementClientImpl networkManagementClient) throws Exception {

        final NetworkSecurityGroupInner sharedNSG = new NetworkSecurityGroupInner();
        sharedNSG.withLocation(AzureTestUtil.AZURE_RESOURCE_GROUP_LOCATION);

        SecurityRuleInner sr = new SecurityRuleInner();
        sr.withPriority(AzureConstants.AZURE_SECURITY_GROUP_PRIORITY);
        sr.withAccess(SecurityRuleAccess.ALLOW);
        sr.withDirection(SecurityRuleDirection.INBOUND);
        sr.withSourceAddressPrefix(AzureConstants.AZURE_SECURITY_GROUP_SOURCE_ADDRESS_PREFIX);
        sr.withDestinationAddressPrefix(
                AzureConstants.AZURE_SECURITY_GROUP_DESTINATION_ADDRESS_PREFIX);

        sr.withSourcePortRange(AzureConstants.AZURE_SECURITY_GROUP_SOURCE_PORT_RANGE);
        sr.withDestinationPortRange(
                AzureConstants.AZURE_LINUX_SECURITY_GROUP_DESTINATION_PORT_RANGE);
        sr.withName(AzureConstants.AZURE_LINUX_SECURITY_GROUP_NAME);
        sr.withProtocol(SecurityRuleProtocol.TCP);

        sharedNSG.withSecurityRules(Collections.singletonList(sr));

        networkManagementClient.networkSecurityGroups()
                .createOrUpdate(resourceGroupName, AzureTestUtil.AZURE_SECURITY_GROUP_NAME,
                        sharedNSG);
    }

    /**
     * Adds Gateway to Virtual Network in Azure
     */
    private static void addAzureGatewayToVirtualNetwork(String resourceGroupName,
            AzureNicSpecs nicSpecs, NetworkManagementClientImpl networkManagementClient)
            throws CloudException, IOException, InterruptedException {

        // create Gateway Subnet
        SubnetInner gatewaySubnetParams = new SubnetInner();
        gatewaySubnetParams.withName(nicSpecs.gateway.name);
        gatewaySubnetParams.withAddressPrefix(nicSpecs.gateway.cidr);
        SubnetInner gatewaySubnet = networkManagementClient
                .subnets()
                .createOrUpdate(resourceGroupName, nicSpecs.network.name,
                        AzureConstants.GATEWAY_SUBNET_NAME,
                        gatewaySubnetParams);

        // create Public IP
        PublicIPAddressInner publicIPAddressParams = new PublicIPAddressInner();
        publicIPAddressParams.withPublicIPAllocationMethod(IPAllocationMethod.DYNAMIC);
        publicIPAddressParams.withLocation(nicSpecs.gateway.zoneId);

        PublicIPAddressInner publicIPAddress = networkManagementClient
                .publicIPAddresses()
                .createOrUpdate(resourceGroupName, nicSpecs.gateway.publicIpName,
                        publicIPAddressParams);

        SubResource publicIPSubResource = new SubResource();
        publicIPSubResource.withId(publicIPAddress.id());

        // create IP Configuration
        VirtualNetworkGatewayIPConfigurationInner ipConfiguration = new VirtualNetworkGatewayIPConfigurationInner();
        ipConfiguration.withName(nicSpecs.gateway.ipConfigurationName);
        ipConfiguration.withSubnet(gatewaySubnet);
        ipConfiguration.withPrivateIPAllocationMethod(IPAllocationMethod.DYNAMIC);

        ipConfiguration.withPublicIPAddress(publicIPSubResource);

        // create Virtual Network Gateway
        VirtualNetworkGatewayInner virtualNetworkGateway = new VirtualNetworkGatewayInner();
        virtualNetworkGateway.withGatewayType(VirtualNetworkGatewayType.VPN);
        virtualNetworkGateway.withVpnType(VpnType.ROUTE_BASED);
        VirtualNetworkGatewaySku vNetGatewaySku = new VirtualNetworkGatewaySku();
        vNetGatewaySku.withName(VirtualNetworkGatewaySkuName.STANDARD);
        vNetGatewaySku.withTier(VirtualNetworkGatewaySkuTier.STANDARD);
        vNetGatewaySku.withCapacity(2);
        virtualNetworkGateway.withSku(vNetGatewaySku);
        virtualNetworkGateway.withLocation(AZURE_RESOURCE_GROUP_LOCATION);

        List<VirtualNetworkGatewayIPConfigurationInner> ipConfigurations = new ArrayList<>();
        ipConfigurations.add(ipConfiguration);
        virtualNetworkGateway.withIpConfigurations(ipConfigurations);

        // Call the async variant because the virtual network gateway provisioning depends on
        // the public IP address assignment which is time-consuming operation
        networkManagementClient.virtualNetworkGateways().createOrUpdateAsync(
                resourceGroupName, nicSpecs.gateway.name, virtualNetworkGateway,
                new ServiceCallback<VirtualNetworkGatewayInner>() {
                    @Override
                    public void failure(Throwable throwable) {
                        throw new RuntimeException(
                                "Error creating Azure Virtual Network Gateway.", throwable);
                    }

                    @Override
                    public void success(
                            VirtualNetworkGatewayInner response) {

                    }
                });
    }

    /**
     * Assert that a resource with the provided name exist in the document store.
     *
     * @param factoryLink
     *            Factory link to the stateful service which states to check.
     * @param name
     *            name of the resource to assert if exists.
     * @param shouldExists
     *            whether to assert if a resource exists or not.
     */
    public static void assertResourceExists(VerificationHost host, String factoryLink,
            String name, boolean shouldExists) {

        ServiceDocumentQueryResult result = host.getExpandedFactoryState(
                UriUtils.buildUri(host, factoryLink));

        boolean exists = false;
        for (Object document : result.documents.values()) {
            ResourceState state = Utils.fromJson(document, ResourceState.class);

            if (name.equals(state.name)) {
                exists = true;
                break;
            }
        }

        assertEquals("Expected: " + shouldExists + ", but was: " + exists, shouldExists, exists);
    }


    /**
     * Assert that a resource with the provided name exist in the document store.
     *
     * @param factoryLink
     *            Factory link to the stateful service which states to check.
     * @param name
     *            name of the resource to assert if exists.
     * @param isDisassociated
     *            whether to assert if a resource exists or not.
     */
    public static void assertResourceDisassociated(
            VerificationHost host, String factoryLink, String name, boolean isDisassociated) {

        ServiceDocumentQueryResult result = host.getExpandedFactoryState(
                UriUtils.buildUri(host, factoryLink));

        boolean disassociated = false;

        for (Object document : result.documents.values()) {
            // Read doc as ServiceDocument to access its 'documentKind'
            ServiceDocument serviceDoc = Utils.fromJson(document, ServiceDocument.class);

            Class<? extends ResourceState> resourceClass = ENDPOINT_LINK_EXPLICIT_SUPPORT
                    .stream()
                    .filter(clazz -> serviceDoc.documentKind.equals(Utils.buildKind(clazz)))
                    .findFirst()
                    .orElse(null);

            if (resourceClass != null) {
                // Read doc as ResourceState to access its 'endpointLinks'
                ResourceState resource = Utils.fromJson(document, resourceClass);

                if (Objects.equals(name, resource.name) && resource.endpointLinks.isEmpty()) {
                    String endpointLink = PhotonModelUtils.getEndpointLink(resource);

                    if (endpointLink == null || endpointLink.isEmpty()) {
                        disassociated = true;
                        break;
                    }
                }
            }
        }

        assertEquals("isDisassociated", isDisassociated, disassociated);
    }
    /**
     * Validate DiskStates are populated with the appropriate type tagLinks
     */
    public static void validateDiskInternalTag(VerificationHost host) {

        ServiceDocumentQueryResult result = host.getExpandedFactoryState(
                UriUtils.buildUri(host, DiskService.FACTORY_LINK));

        List<String> tenantLinks = Collections.singletonList( EndpointType.azure.name() + "-tenant");
        for (Object document : result.documents.values()) {
            DiskState state = Utils.fromJson(document, DiskState.class);
            if (state.storageType != null) {
                switch (state.storageType) {
                case AZURE_STORAGE_DISKS:
                    assertTrue(state.tagLinks.contains(getTagState(tenantLinks, TAG_KEY_TYPE,
                            AzureResourceType.azure_vhd.name()).documentSelfLink));
                    break;
                case AZURE_STORAGE_BLOBS:
                    assertTrue(state.tagLinks.contains(getTagState(tenantLinks, TAG_KEY_TYPE,
                            AzureResourceType.azure_blob.name()).documentSelfLink));
                    break;
                default:
                    break;
                }
            }
        }
    }


    /**
     * Query to get ResourceMetrics document for a specific resource containing a specific metric.
     *
     * @param host
     *            host against which query is triggered
     * @param resourceLink
     *            Link to the resource on which stats are being collected.
     * @param metricKey
     *            Metric name.
     * @return ResourceMetrics document.
     */
    public static ResourceMetrics getResourceMetrics(VerificationHost host, String resourceLink,
            String metricKey) {
        QueryTask qt = QueryTask.Builder
                .createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .addOption(QueryOption.SORT)
                .orderDescending(ServiceDocument.FIELD_NAME_SELF_LINK,
                        ServiceDocumentDescription.TypeName.STRING)
                .setQuery(QueryTask.Query.Builder.create()
                        .addKindFieldClause(ResourceMetrics.class)
                        .addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                                UriUtils.buildUriPath(ResourceMetricsService.FACTORY_LINK,
                                        UriUtils.getLastPathSegment(resourceLink)),
                                QueryTerm.MatchType.PREFIX)
                        .addRangeClause(QuerySpecification
                                .buildCompositeFieldName(ResourceMetrics.FIELD_NAME_ENTRIES,
                                        metricKey),
                                QueryTask.NumericRange
                                        .createDoubleRange(0.0, Double.MAX_VALUE, true, true))
                        .build())
                .build();

        Operation op = QueryUtils.createQueryTaskOperation(host, qt, ClusterUtil
                .ServiceTypeCluster.METRIC_SERVICE)
                .setReferer(host.getUri()).setBody(qt).setCompletion((o, e) -> {
                    if (e != null) {
                        host.log(Level.INFO, e.toString());
                    }
                });

        Operation result = host.waitForResponse(op);
        QueryTask qtResult = result.getBody(QueryTask.class);
        ResourceMetrics resourceMetric = null;
        if (qtResult.results.documentLinks.size() > 0) {
            String documentLink = qtResult.results.documentLinks.get(0);
            resourceMetric = Utils.fromJson(qtResult.results.documents.get(documentLink),
                    ResourceMetrics.class);
        }
        return resourceMetric;
    }

    /**
     * Runs azure enumeration.
     */
    public static void runEnumeration(VerificationHost host, String hostSelfLink,
            String resourcePoolLink, EndpointState endpointState, boolean isMock) throws Throwable {
        ResourceEnumerationTaskState enumerationTaskState = new ResourceEnumerationTaskState();

        enumerationTaskState.endpointLink = endpointState.documentSelfLink;
        enumerationTaskState.tenantLinks = endpointState.tenantLinks;
        enumerationTaskState.parentComputeLink = hostSelfLink;
        enumerationTaskState.enumerationAction = EnumerationAction.START;
        enumerationTaskState.adapterManagementReference = UriUtils
                .buildUri(AzureEnumerationAdapterService.SELF_LINK);
        enumerationTaskState.resourcePoolLink = resourcePoolLink;
        if (isMock) {
            enumerationTaskState.options = EnumSet.of(TaskOption.IS_MOCK);
        }

        ResourceEnumerationTaskState enumTask = TestUtils
                .doPost(host, enumerationTaskState, ResourceEnumerationTaskState.class,
                        UriUtils.buildUri(host, ResourceEnumerationTaskService.FACTORY_LINK));

        host.waitFor("Error waiting for enumeration task", () -> {
            try {
                ResourceEnumerationTaskState state = host
                        .waitForFinishedTask(ResourceEnumerationTaskState.class,
                                enumTask.documentSelfLink);
                if (state != null) {
                    return true;
                }
            } catch (Throwable e) {
                return false;
            }
            return false;
        });
    }

    /**
     * Helper for running stats collection task.
     *
     * @param host
     * @param isMock
     * @throws Throwable
     */
    public static void resourceStatsCollection(VerificationHost host,
            boolean isMock, String resourcePoolLink) throws Throwable {
        resourceStatsCollection(host, null,
                isMock ? EnumSet.of(TaskOption.IS_MOCK) : null, resourcePoolLink);
    }

    /**
     * Waits for stats collection task to be finished.
     *
     * @param host
     * @param peerURI
     * @param options
     * @throws Throwable
     */
    public static void resourceStatsCollection(VerificationHost host, URI peerURI,
            EnumSet<TaskOption> options, String resourcePoolLink) throws Throwable {
        StatsCollectionTaskState statsTask = performResourceStatsCollection(
                host, options, resourcePoolLink);

        // Wait for the stats collection task to be completed.
        host.waitForFinishedTask(StatsCollectionTaskState.class,
                ProvisioningUtils.createServiceURI(host, peerURI, statsTask.documentSelfLink));
    }

    /**
     * Performs stats collection for given resourcePoolLink.
     */
    public static StatsCollectionTaskState performResourceStatsCollection(
            VerificationHost host, EnumSet<TaskOption> options, String resourcePoolLink)
            throws Throwable {

        StatsCollectionTaskState statsCollectionTaskState = new StatsCollectionTaskState();

        statsCollectionTaskState.resourcePoolLink = resourcePoolLink;
        statsCollectionTaskState.options = EnumSet.noneOf(TaskOption.class);

        if (options != null) {
            statsCollectionTaskState.options = options;
        }

        URI uri = UriUtils.buildUri(host, StatsCollectionTaskService.FACTORY_LINK);
        StatsCollectionTaskState statsTask = TestUtils.doPost(
                host, statsCollectionTaskState, StatsCollectionTaskState.class, uri);

        return statsTask;
    }

    /**
     * Performs stats collection for given resourcePoolLink.
     */
    public static void resourceStatsAggregation(VerificationHost host,
            String resourcePoolLink) throws Throwable {
        host.testStart(1);
        StatsAggregationTaskState statsAggregationTaskState = new StatsAggregationTaskState();
        QueryTask.Query taskQuery = QueryTask.Query.Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_RESOURCE_POOL_LINK, resourcePoolLink)
                .build();
        statsAggregationTaskState.query = taskQuery;
        statsAggregationTaskState.taskInfo = TaskState.createDirect();
        statsAggregationTaskState.metricNames = getMetricNames();

        host.send(Operation
                .createPost(
                        UriUtils.buildUri(host,
                                StatsAggregationTaskService.FACTORY_LINK))
                .setBody(statsAggregationTaskState)
                .setCompletion(host.getCompletion()));
        host.testWait();
    }

    /**
     * Define the metric names that should be aggregated.
     */
    public static Set<String> getMetricNames() {
        Set<String> metricNames = new HashSet<>();
        // CPU
        metricNames.add(CPU_UTILIZATION_PERCENT);

        // Memory
        metricNames.add(MEMORY_USED_PERCENT);

        // Storage
        metricNames.add(STORAGE_USED_BYTES);

        return metricNames;
    }

    public static SecurityGroupState getSecurityGroupState(VerificationHost host,
            String  securityGroupLink) throws Throwable {
        Operation response = new Operation();
        getSecurityGroupState(host, securityGroupLink, response);
        return response.getBody(SecurityGroupState.class);
    }

    private static void getSecurityGroupState(VerificationHost host,
            String securityGroupLink, Operation response) throws Throwable {

        host.testStart(1);
        URI securityGroupURI = UriUtils.buildUri(host, securityGroupLink);
        Operation startGet = Operation.createGet(securityGroupURI)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                        return;
                    }
                    response.setBody(o.getBody(SecurityGroupState.class));
                    host.completeIteration();
                });
        host.send(startGet);
        host.testWait();
    }

    public static void setAzureClientMockInfo(boolean isAwsClientMock,
            String awsMockEndpointReference) {
        AzureUtils.setAzureClientMock(isAwsClientMock);
        AzureUtils.setAzureMockHost(awsMockEndpointReference);
    }

    private static ResourceState getResourceState(VerificationHost host, String resourceLink)
            throws Throwable {
        Operation response = host.waitForResponse(Operation.createGet(host, resourceLink));
        return response.getBody(ResourceState.class);
    }

    /**
     * Validate the deletion of documents of ResourceState.
     */
    public static void verifyRemovalOfResourceState(VerificationHost host,
                                                    List<String> resourceStateLinks) throws Throwable {
        for (String resourceLink : resourceStateLinks) {
            ResourceState resourceState = getResourceState(host, resourceLink);
            assertNotNull(resourceState);
            // make sure the document has been removed.
            assertNull(resourceState.documentSelfLink);
        }
    }
}
