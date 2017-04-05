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

import static java.util.Collections.singletonList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.STATUS_SUBNET_NOT_VALID;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.AZURE_RESOURCE_GROUP_LOCATION;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultAuthCredentials;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultResourceGroupState;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.generateName;

import java.util.Collections;
import java.util.EnumSet;
import java.util.UUID;
import java.util.logging.Level;

import com.microsoft.azure.CloudException;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.credentials.AzureEnvironment;
import com.microsoft.azure.management.network.NetworkManagementClient;
import com.microsoft.azure.management.network.NetworkManagementClientImpl;
import com.microsoft.azure.management.network.SubnetsOperations;
import com.microsoft.azure.management.network.VirtualNetworksOperations;
import com.microsoft.azure.management.network.models.AddressSpace;
import com.microsoft.azure.management.network.models.Subnet;
import com.microsoft.azure.management.resources.ResourceGroupsOperations;
import com.microsoft.azure.management.resources.ResourceManagementClient;
import com.microsoft.azure.management.resources.ResourceManagementClientImpl;
import com.microsoft.azure.management.resources.models.ResourceGroup;
import com.microsoft.rest.ServiceResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
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
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
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

    private static final String AZURE_DEFAULT_VPC_CIDR = "172.16.12.0/16";
    private static final String AZURE_NON_EXISTING_SUBNET_CIDR = "172.16.12.0/22";

    public boolean isMock = true;

    public String clientID = "clientID";
    public String clientKey = "clientKey";
    public String subscriptionId = "subscriptionId";
    public String tenantId = "tenantId";
    public String regionId = AZURE_RESOURCE_GROUP_LOCATION;

    private static String authLink;

    private String azurePrefix = generateName("subnettest-");
    private String rgName = this.azurePrefix + "-rg";
    private String vNetName = this.azurePrefix + "-vNet";
    private String subnetName = this.azurePrefix + "-subnet";

    private EndpointState endpointState;
    private NetworkState networkState;

    private VirtualNetworksOperations vNetClient;
    private ResourceGroupsOperations rgOpsClient;
    private SubnetsOperations subnetsClient;

    @Before
    public void setUp() throws Throwable {
        CommandLineArgumentParser.parseFromProperties(this);

        if (authLink == null) {
            PhotonModelServices.startServices(this.host);
            PhotonModelTaskServices.startServices(this.host);
            AzureAdapters.startServices(this.host);

            this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
            this.host.waitForServiceAvailable(AzureAdapters.LINKS);

            // TODO: VSYM-992 - improve test/fix arbitrary timeout
            this.host.setTimeoutSeconds(600);

            AuthCredentialsServiceState authCredentials = createDefaultAuthCredentials(
                    this.host,
                    this.clientID,
                    this.clientKey,
                    this.subscriptionId,
                    this.tenantId);
            authLink = authCredentials.documentSelfLink;
        }

        this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
        this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
        this.host.waitForServiceAvailable(AzureAdapters.LINKS);

        if (!this.isMock) {
            ApplicationTokenCredentials credentials = new ApplicationTokenCredentials(
                    this.clientID,
                    this.tenantId, this.clientKey, AzureEnvironment.AZURE);

            NetworkManagementClient networkManagementClient = new NetworkManagementClientImpl(
                    credentials);
            networkManagementClient.setSubscriptionId(this.subscriptionId);

            ResourceManagementClient resourceManagementClient =
                    new ResourceManagementClientImpl(credentials);
            resourceManagementClient.setSubscriptionId(this.subscriptionId);

            this.vNetClient = networkManagementClient.getVirtualNetworksOperations();
            this.rgOpsClient = resourceManagementClient.getResourceGroupsOperations();
            this.subnetsClient = networkManagementClient.getSubnetsOperations();

            ResourceGroup rg = new ResourceGroup();
            rg.setName(this.rgName);
            rg.setLocation(this.regionId);
            this.rgOpsClient.createOrUpdate(this.rgName, rg);

            com.microsoft.azure.management.network.models.VirtualNetwork vNet = new com.microsoft
                    .azure.management.network.models.VirtualNetwork();
            vNet.setId(this.vNetName);
            AddressSpace addressSpace = new AddressSpace();
            addressSpace.setAddressPrefixes(Collections.singletonList(AZURE_DEFAULT_VPC_CIDR));
            vNet.setAddressSpace(addressSpace);
            vNet.setLocation(this.regionId);
            this.vNetClient.createOrUpdate(this.rgName, this.vNetName, vNet);
        }

        this.endpointState = createEndpointState();

        ResourceGroupState rgState = createDefaultResourceGroupState(
                this.host,
                this.rgName,
                null,
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
            protected void onSuccess(ServiceResponse<Void> result) {
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
            ServiceResponse<Subnet> subnetResponse = this.subnetsClient
                    .get(this.rgName, this.vNetName, this.subnetName, null);

            assertEquals(HttpResponseStatus.OK.code(), subnetResponse.getResponse().code());
            assertEquals(this.subnetName, subnetResponse.getBody().getName());
        }
    }

    @Test
    public void testDeleteSubnet() throws Throwable {
        Subnet azureSubnet = createAzureSubnet();

        SubnetState subnetState = createSubnetState(azureSubnet.getId());

        kickOffSubnetProvision(InstanceRequestType.DELETE, subnetState, TaskStage.FINISHED);

        if (!this.isMock) {
            // Verify that the subnet was deleted.
            try {
                this.subnetsClient.get(this.rgName, this.vNetName, this.subnetName, null);
                fail("Subnet should not exists in Azure.");
            } catch (CloudException ex) {
                assertEquals(HttpResponseStatus.NOT_FOUND.code(), ex.getResponse().code());
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

    private Subnet createAzureSubnet() throws Throwable {

        Subnet subnet = new Subnet();
        subnet.setName(this.subnetName);
        subnet.setId(UUID.randomUUID().toString());
        subnet.setAddressPrefix(AZURE_NON_EXISTING_SUBNET_CIDR);

        if (this.isMock) {
            return subnet;
        }

        ServiceResponse<Subnet> subnetResponse = this.subnetsClient
                .createOrUpdate(this.rgName, this.vNetName, this.subnetName, subnet);

        return subnetResponse.getBody();
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
        subnetState.endpointLink = this.endpointState.documentSelfLink;

        return postServiceSynchronously(
                SubnetService.FACTORY_LINK, subnetState, SubnetState.class);
    }

    private EndpointState createEndpointState() throws Throwable {

        EndpointState endpoint = new EndpointState();

        String endpointType = EndpointType.aws.name();
        endpoint.id = endpointType + "Id";
        endpoint.name = endpointType + "Name";
        endpoint.endpointType = endpointType;
        endpoint.tenantLinks = singletonList(endpointType + "Tenant");
        endpoint.authCredentialsLink = authLink;

        return postServiceSynchronously(
                EndpointService.FACTORY_LINK,
                endpoint,
                EndpointState.class);
    }

    private NetworkState createNetworkState(String resourceGroupLink) throws Throwable {
        NetworkState networkState = new NetworkState();
        networkState.id = this.vNetName;
        networkState.name = this.vNetName;
        networkState.subnetCIDR = AZURE_DEFAULT_VPC_CIDR;
        networkState.endpointLink = this.endpointState.documentSelfLink;
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
        taskState.subnetDescriptionLink = subnetState.documentSelfLink;
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
