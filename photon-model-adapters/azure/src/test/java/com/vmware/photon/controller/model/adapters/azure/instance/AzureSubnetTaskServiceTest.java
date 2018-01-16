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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.STATUS_SUBNET_NOT_VALID;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.AZURE_RESOURCE_GROUP_LOCATION;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultAuthCredentials;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultComputeHost;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultEndpointState;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultResourceGroupState;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultResourcePool;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.generateName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.network.AddressSpace;
import com.microsoft.azure.management.network.implementation.NetworkManagementClientImpl;
import com.microsoft.azure.management.network.implementation.SubnetInner;
import com.microsoft.azure.management.network.implementation.SubnetsInner;
import com.microsoft.azure.management.network.implementation.VirtualNetworkInner;
import com.microsoft.azure.management.network.implementation.VirtualNetworksInner;
import com.microsoft.azure.management.resources.implementation.ResourceGroupInner;
import com.microsoft.azure.management.resources.implementation.ResourceGroupsInner;
import com.microsoft.azure.management.resources.implementation.ResourceManagementClientImpl;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapterapi.SubnetInstanceRequest.InstanceRequestType;
import com.vmware.photon.controller.model.adapters.azure.AzureAdapters;
import com.vmware.photon.controller.model.adapters.azure.AzureAsyncCallback;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.ResourceGroupStateType;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.support.LifecycleState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.ProvisionSubnetTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionSubnetTaskService.ProvisionSubnetTaskState;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Tests for {@link AzureSubnetService}.
 */
public class AzureSubnetTaskServiceTest extends BaseModelTest {

    private static final String AZURE_DEFAULT_VPC_CIDR = "172.16.0.0/16";
    private static final String AZURE_NON_EXISTING_SUBNET_CIDR = "172.16.12.0/22";

    public boolean isMock = true;

    public String clientID = "clientID";
    public String clientKey = "clientKey";
    public String subscriptionId = "subscriptionId";
    public String tenantId = "tenantId";
    public String regionId = AZURE_RESOURCE_GROUP_LOCATION;

    private static ComputeState computeHost;
    private static EndpointState endpointState;

    private String azurePrefix = generateName("subnettest-");
    private String rgName = this.azurePrefix + "-rg";
    private String vNetName = this.azurePrefix + "-vNet";
    private String subnetName = this.azurePrefix + "-subnet";

    private NetworkState networkState;

    private VirtualNetworksInner vNetClient;
    private ResourceGroupsInner rgOpsClient;
    private SubnetsInner subnetsClient;

    @Override
    @Before
    public void setUp() throws Throwable {
        CommandLineArgumentParser.parseFromProperties(this);

        if (computeHost == null) {
            PhotonModelServices.startServices(this.host);
            PhotonModelTaskServices.startServices(this.host);
            PhotonModelAdaptersRegistryAdapters.startServices(this.host);
            AzureAdapters.startServices(this.host);

            this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelAdaptersRegistryAdapters.LINKS);
            this.host.waitForServiceAvailable(AzureAdapters.LINKS);

            // TODO: VSYM-992 - improve test/fix arbitrary timeout
            this.host.setTimeoutSeconds(600);

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
                    this.clientID,
                    this.tenantId, this.clientKey, AzureEnvironment.AZURE);

            NetworkManagementClientImpl networkManagementClient = new NetworkManagementClientImpl(
                    credentials).withSubscriptionId(this.subscriptionId);

            ResourceManagementClientImpl resourceManagementClient =
                    new ResourceManagementClientImpl(credentials).withSubscriptionId(this.subscriptionId);

            this.vNetClient = networkManagementClient.virtualNetworks();
            this.rgOpsClient = resourceManagementClient.resourceGroups();
            this.subnetsClient = networkManagementClient.subnets();

            ResourceGroupInner rg = new ResourceGroupInner();
            rg.withName(this.rgName);
            rg.withLocation(this.regionId);
            this.rgOpsClient.createOrUpdate(this.rgName, rg);

            VirtualNetworkInner vNet = new VirtualNetworkInner();

            // Azure's custom serializers don't handle Collections.SingletonList well, so use ArrayList
            AddressSpace addressSpace = new AddressSpace();
            List<String> cidrs = new ArrayList<>();

            cidrs.add(AZURE_DEFAULT_VPC_CIDR);

            addressSpace.withAddressPrefixes(cidrs);
            vNet.withAddressSpace(addressSpace);
            vNet.withLocation(this.regionId);
            this.vNetClient.createOrUpdate(this.rgName, this.vNetName, vNet);
        }

        ResourceGroupState rgState = createDefaultResourceGroupState(
                this.host,
                this.rgName,
                computeHost, endpointState,
                ResourceGroupStateType.AzureResourceGroup);

        this.networkState = createNetworkState(rgState.documentSelfLink);
    }

    @Override
    protected void startRequiredServices() throws Throwable {
        AzureAdapters.startServices(this.host);

        super.startRequiredServices();
    }

    @After
    public void tearDown() {
        if (this.isMock) {
            return;
        }

        this.rgOpsClient.deleteAsync(this.rgName, new AzureAsyncCallback<Void>() {
            @Override
            protected void onError(Throwable e) {
                AzureSubnetTaskServiceTest.this.host.log(Level.WARNING, "Error deleting resource "
                        + "group: " + ExceptionUtils.getMessage(e));
            }

            @Override
            protected void onSuccess(Void result) {
                // Do nothing.
            }
        });
    }

    @Test
    public void testCreateSubnet() throws Throwable {
        SubnetState subnetState = createSubnetState(this.subnetName);

        kickOffSubnetProvision(InstanceRequestType.CREATE, subnetState, TaskStage.FINISHED);

        subnetState = getServiceSynchronously(subnetState.documentSelfLink, SubnetState.class);

        assertNotNull(subnetState.id);
        assertNotEquals(subnetState.id, this.subnetName);
        assertEquals(LifecycleState.READY, subnetState.lifecycleState);

        if (!this.isMock) {
            // Verify that the subnet was deleted.
            SubnetInner subnetResponse = this.subnetsClient
                    .get(this.rgName, this.vNetName, this.subnetName, null);

            assertEquals(this.subnetName, subnetResponse.name());
        }
    }

    @Test
    public void testDeleteSubnet() throws Throwable {
        SubnetInner azureSubnet = createAzureSubnet();

        SubnetState subnetState = createSubnetState(azureSubnet.id());

        kickOffSubnetProvision(InstanceRequestType.DELETE, subnetState, TaskStage.FINISHED);

        if (!this.isMock) {
            // Verify that the subnet was deleted.
            SubnetInner subnetInner = this.subnetsClient.get(this.rgName, this.vNetName, this.subnetName, null);
            if (subnetInner != null) {
                fail("Subnet should not exist in Azure.");
            }
        }
    }

    @Test
    public void testCreateSubnetDuplicatedCIDR() throws Throwable {
        Assume.assumeFalse(this.isMock);

        // Provision subnet in Azure.
        SubnetState subnetState = createSubnetState(null);

        kickOffSubnetProvision(InstanceRequestType.CREATE, subnetState, TaskStage.FINISHED);

        subnetState.name += "new";
        patchServiceSynchronously(subnetState.documentSelfLink, subnetState);

        // Try to provision the same subnet second time.
        ProvisionSubnetTaskState state = kickOffSubnetProvision(
                InstanceRequestType.CREATE, subnetState, TaskStage.FAILED);

        assertTrue(state.taskInfo.failure.message.contains(STATUS_SUBNET_NOT_VALID));
    }

    private SubnetInner createAzureSubnet() throws Throwable {

        SubnetInner subnet = new SubnetInner();
        subnet.withName(this.subnetName);
        subnet.withId(UUID.randomUUID().toString());
        subnet.withAddressPrefix(AZURE_NON_EXISTING_SUBNET_CIDR);

        if (this.isMock) {
            return subnet;
        }

        SubnetInner subnetResponse = this.subnetsClient
                .createOrUpdate(this.rgName, this.vNetName, this.subnetName, subnet);

        return subnetResponse;
    }

    private SubnetState createSubnetState(String id) throws Throwable {
        SubnetState subnetState = new SubnetState();
        subnetState.id = id;
        subnetState.name = this.subnetName;
        subnetState.lifecycleState = LifecycleState.PROVISIONING;
        subnetState.subnetCIDR = AZURE_NON_EXISTING_SUBNET_CIDR;
        subnetState.networkLink = this.networkState.documentSelfLink;
        subnetState.instanceAdapterReference = UriUtils.buildUri(this.host,
                AzureSubnetService.SELF_LINK);
        subnetState.endpointLink = endpointState.documentSelfLink;
        subnetState.tenantLinks = endpointState.tenantLinks;

        return postServiceSynchronously(
                SubnetService.FACTORY_LINK, subnetState, SubnetState.class);
    }

    private NetworkState createNetworkState(String resourceGroupLink) throws Throwable {
        NetworkState networkState = new NetworkState();
        networkState.id = this.vNetName;
        networkState.name = this.vNetName;
        networkState.subnetCIDR = AZURE_DEFAULT_VPC_CIDR;
        networkState.tenantLinks = endpointState.tenantLinks;
        networkState.endpointLink = endpointState.documentSelfLink;
        networkState.resourcePoolLink = "dummyResourcePoolLink";
        networkState.groupLinks = Collections.singleton(resourceGroupLink);
        networkState.regionId = this.regionId;
        networkState.instanceAdapterReference =
                UriUtils.buildUri(this.host, AzureInstanceService.SELF_LINK);

        return postServiceSynchronously(
                NetworkService.FACTORY_LINK, networkState, NetworkState.class);
    }

    private ProvisionSubnetTaskState kickOffSubnetProvision(InstanceRequestType requestType,
            SubnetState subnetState, TaskStage expectedTaskState) throws Throwable {

        ProvisionSubnetTaskState taskState = new ProvisionSubnetTaskState();
        taskState.requestType = requestType;
        taskState.subnetLink = subnetState.documentSelfLink;
        taskState.options = this.isMock
                ? EnumSet.of(TaskOption.IS_MOCK)
                : EnumSet.noneOf(TaskOption.class);

        // Start/Post subnet provisioning task
        taskState = postServiceSynchronously(
                ProvisionSubnetTaskService.FACTORY_LINK,
                taskState,
                ProvisionSubnetTaskState.class);

        // Wait for image-enumeration task to complete
        return waitForServiceState(
                ProvisionSubnetTaskState.class,
                taskState.documentSelfLink,
                liveState -> expectedTaskState == liveState.taskInfo.stage);
    }

}
