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

package com.vmware.photon.controller.model.adapters.awsadapter;

import static java.util.Collections.singletonList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_ELASTIC_IP_ALLOCATION_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_NAT_GATEWAY_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_ROUTE_TABLE_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_SUBNET_CIDR_FILTER;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_VPC_ID_FILTER;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.AWS_DEFAULT_VPC_CIDR;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.AWS_DEFAULT_VPC_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.AWS_NON_EXISTING_PUBLIC_SUBNET_CIDR;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.AWS_NON_EXISTING_PUBLIC_SUBNET_NAME;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.AWS_NON_EXISTING_SUBNET_CIDR;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.AWS_NON_EXISTING_SUBNET_NAME;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestUtils.getExecutor;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSNetworkClient.STATUS_CODE_SUBNET_CONFLICT;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.Address;
import com.amazonaws.services.ec2.model.AmazonEC2Exception;
import com.amazonaws.services.ec2.model.CreateSubnetRequest;
import com.amazonaws.services.ec2.model.DeleteSubnetRequest;
import com.amazonaws.services.ec2.model.DescribeAddressesRequest;
import com.amazonaws.services.ec2.model.DescribeNatGatewaysRequest;
import com.amazonaws.services.ec2.model.DescribeRouteTablesRequest;
import com.amazonaws.services.ec2.model.DescribeSubnetsRequest;
import com.amazonaws.services.ec2.model.DescribeSubnetsResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.NatGateway;
import com.amazonaws.services.ec2.model.Route;
import com.amazonaws.services.ec2.model.RouteTable;
import com.amazonaws.services.ec2.model.Subnet;

import io.netty.handler.codec.http.HttpResponseStatus;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.PhotonModelMetricServices;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapterapi.SubnetInstanceRequest.InstanceRequestType;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
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
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class AWSSubnetTaskServiceTest extends BaseModelTest {
    private static final int DEFAULT_TIMOUT_SECONDS = 200;

    public String secretKey = "secretKey";
    public String accessKey = "accessKey";
    private String regionId = TestAWSSetupUtils.regionId;
    private int timeoutSeconds = DEFAULT_TIMOUT_SECONDS;
    public boolean isMock = true;
    private AmazonEC2AsyncClient client;

    private EndpointState endpointState;
    private NetworkState networkState;

    @Before
    public void setUp() throws Throwable {
        CommandLineArgumentParser.parseFromProperties(this);

        try {
            PhotonModelServices.startServices(this.host);
            PhotonModelMetricServices.startServices(this.host);
            PhotonModelTaskServices.startServices(this.host);
            PhotonModelAdaptersRegistryAdapters.startServices(this.host);
            AWSAdapters.startServices(this.host);

            AuthCredentialsServiceState creds = new AuthCredentialsServiceState();
            creds.privateKey = this.secretKey;
            creds.privateKeyId = this.accessKey;
            this.client = AWSUtils.getAsyncClient(creds, this.regionId, getExecutor());

            this.host.setTimeoutSeconds(this.timeoutSeconds);
            this.host.waitForServiceAvailable(PhotonModelAdaptersRegistryAdapters.LINKS);
            this.host.waitForServiceAvailable(AWSAdapters.LINKS);

            deleteAwsSubnet();
            deleteAwsPublicSubnet();
            this.endpointState = createEndpointState();
            this.networkState = createNetworkState();

        } catch (Throwable e) {
            this.host.log("Error starting up services for the test %s", e.getMessage());
            throw new Exception(e);
        }
    }

    @After
    public void tearDown() {
        deleteAwsSubnet();
        deleteAwsPublicSubnet();
    }

    @Test
    public void testCreateSubnet() throws Throwable {
        SubnetState subnetState = provisionSubnet(AWS_NON_EXISTING_SUBNET_NAME,
                AWS_NON_EXISTING_SUBNET_CIDR, null);

        assertNotNull(subnetState.id);
        assertEquals(LifecycleState.READY, subnetState.lifecycleState);

        if (!this.isMock) {
            // Verify that the subnet was created.
            DescribeSubnetsRequest describeRequest = new DescribeSubnetsRequest()
                    .withSubnetIds(Collections.singletonList(subnetState.id));
            List<Subnet> subnets = this.client.describeSubnets(describeRequest).getSubnets();

            assertNotNull(subnets);
            assertEquals(1, subnets.size());
        }
    }

    @Test
    public void testCreateSubnetWithOutboundAccess() throws Throwable {
        // provision a "public" subnet first
        SubnetState publicSubnetState = provisionSubnet(AWS_NON_EXISTING_PUBLIC_SUBNET_NAME,
                AWS_NON_EXISTING_PUBLIC_SUBNET_CIDR, null);

        assertNotNull(publicSubnetState.id);
        assertEquals(LifecycleState.READY, publicSubnetState.lifecycleState);

        SubnetState subnetState = provisionSubnet(AWS_NON_EXISTING_SUBNET_NAME,
                AWS_NON_EXISTING_SUBNET_CIDR, publicSubnetState.documentSelfLink);

        assertNotNull(subnetState.id);
        assertEquals(LifecycleState.READY, subnetState.lifecycleState);

        if (!this.isMock) {
            // Verify that the subnet was created.
            DescribeSubnetsRequest describeRequest = new DescribeSubnetsRequest()
                    .withSubnetIds(Collections.singletonList(subnetState.id));
            List<Subnet> subnets = this.client.describeSubnets(describeRequest).getSubnets();

            assertNotNull(subnets);
            assertEquals(1, subnets.size());

            // Verify that a NAT gateway was created
            assertNotNull(subnetState.customProperties);
            String natGatewayId = subnetState.customProperties.get(AWS_NAT_GATEWAY_ID);
            String routeTableId = subnetState.customProperties.get(AWS_ROUTE_TABLE_ID);
            String allocationId = subnetState.customProperties.get(AWS_ELASTIC_IP_ALLOCATION_ID);
            assertNotNull(natGatewayId);
            assertNotNull(routeTableId);
            assertNotNull(allocationId);

            DescribeNatGatewaysRequest describeNatGatewaysRequest = new DescribeNatGatewaysRequest()
                    .withNatGatewayIds(Collections.singletonList(natGatewayId));
            List<NatGateway> natGateways = this.client.describeNatGateways
                    (describeNatGatewaysRequest).getNatGateways();
            assertNotNull(natGateways);
            assertEquals(1, natGateways.size());
            NatGateway natGateway = natGateways.get(0);
            assertEquals(publicSubnetState.id, natGateway.getSubnetId());
            assertNotNull(natGateway.getNatGatewayAddresses());
            assertEquals(1, natGateway.getNatGatewayAddresses().size());
            assertEquals(allocationId,
                    natGateway.getNatGatewayAddresses().get(0).getAllocationId());
            assertEquals("available", natGateways.get(0).getState());

            //verify that a route table was created
            DescribeRouteTablesRequest describeRouteTablesRequest = new DescribeRouteTablesRequest()
                    .withRouteTableIds(Collections.singletonList(routeTableId));
            List<RouteTable> routeTables = this.client.describeRouteTables(
                    describeRouteTablesRequest).getRouteTables();
            assertNotNull(routeTables);
            assertEquals(1, routeTables.size());
            RouteTable routeTable = routeTables.get(0);
            assertNotNull(routeTable.getAssociations());
            assertEquals(1, routeTable.getAssociations().size());
            assertEquals(subnetState.id, routeTable.getAssociations().get(0).getSubnetId());
            assertNotNull(routeTable.getRoutes());
            assertEquals(2, routeTable.getRoutes().size());
            boolean hasRouteToNatGateway = false;
            for (Route route : routeTable.getRoutes()) {
                if (route.getDestinationCidrBlock().equals("0.0.0.0/0") &&
                        route.getNatGatewayId() != null &&
                        route.getNatGatewayId().equals(natGatewayId)) {
                    hasRouteToNatGateway = true;
                    break;
                }
            }
            assertTrue(hasRouteToNatGateway);

            // Verify that an IP address allocation was created
            DescribeAddressesRequest describeAddressesRequest = new DescribeAddressesRequest()
                    .withAllocationIds(Collections.singletonList(allocationId));
            List<Address> addresses = this.client.describeAddresses(describeAddressesRequest)
                    .getAddresses();
            assertNotNull(addresses);
            assertEquals(1, addresses.size());
        }

        // delete the subnet
        kickOffSubnetProvision(InstanceRequestType.DELETE, subnetState, TaskStage.FINISHED);

        if (!this.isMock) {
            // Verify that the subnet was deleted.
            DescribeSubnetsRequest describeRequest = new DescribeSubnetsRequest()
                    .withSubnetIds(Collections.singletonList(subnetState.id));

            try {
                this.client.describeSubnets(describeRequest).getSubnets();
                fail("Subnet should not exist in AWS.");
            } catch (AmazonEC2Exception ex) {
                assertEquals(HttpResponseStatus.BAD_REQUEST.code(), ex.getStatusCode());
            }

            // Verify that the NAT gateway was deleted
            String natGatewayId = subnetState.customProperties.get(AWS_NAT_GATEWAY_ID);
            String routeTableId = subnetState.customProperties.get(AWS_ROUTE_TABLE_ID);
            String allocationId = subnetState.customProperties.get(AWS_ELASTIC_IP_ALLOCATION_ID);

            DescribeNatGatewaysRequest describeNatGatewaysRequest = new DescribeNatGatewaysRequest()
                    .withNatGatewayIds(Collections.singletonList(natGatewayId));
            List<NatGateway> natGateways = this.client.describeNatGateways(
                    describeNatGatewaysRequest).getNatGateways();
            assertNotNull(natGateways);
            assertEquals(1, natGateways.size());
            assertEquals("deleted", natGateways.get(0).getState());

            // Verify that the route table was deleted
            DescribeRouteTablesRequest describeRouteTablesRequest = new DescribeRouteTablesRequest()
                    .withRouteTableIds(Collections.singletonList(routeTableId));
            try {
                this.client.describeRouteTables(describeRouteTablesRequest).getRouteTables();
                fail("Route table should not exist in AWS.");
            } catch (AmazonEC2Exception ex) {
                assertEquals(HttpResponseStatus.BAD_REQUEST.code(), ex.getStatusCode());
            }

            DescribeAddressesRequest describeAddressesRequest = new DescribeAddressesRequest()
                    .withAllocationIds(Collections.singletonList(allocationId));
            try {
                this.client.describeAddresses(describeAddressesRequest).getAddresses();
                fail("IP address allocation should not exist in AWS.");
            } catch (AmazonEC2Exception ex) {
                assertEquals(HttpResponseStatus.BAD_REQUEST.code(), ex.getStatusCode());
            }
        }
    }

    @Test
    public void testDeleteSubnet() throws Throwable {
        Subnet awsSubnet = createAwsSubnet();

        SubnetState subnetState = createSubnetState(awsSubnet.getSubnetId(), AWS_NON_EXISTING_SUBNET_NAME,
                AWS_NON_EXISTING_SUBNET_CIDR, null);

        kickOffSubnetProvision(InstanceRequestType.DELETE, subnetState, TaskStage.FINISHED);

        if (!this.isMock) {
            // Verify that the subnet was deleted.
            DescribeSubnetsRequest describeRequest = new DescribeSubnetsRequest()
                    .withSubnetIds(Collections.singletonList(awsSubnet.getSubnetId()));

            try {
                this.client.describeSubnets(describeRequest).getSubnets();
                fail("Subnet should not exist in AWS.");
            } catch (AmazonEC2Exception ex) {
                assertEquals(HttpResponseStatus.BAD_REQUEST.code(), ex.getStatusCode());
            }
        }
    }

    @Test
    public void testCreateSubnetDuplicatedCIDR() throws Throwable {
        Assume.assumeFalse(this.isMock);

        // Provision subnet in AWS.
        SubnetState subnetState = createSubnetState(null, AWS_NON_EXISTING_SUBNET_NAME,
                AWS_NON_EXISTING_SUBNET_CIDR, null);

        kickOffSubnetProvision(InstanceRequestType.CREATE, subnetState, TaskStage.FINISHED);

        // Try to provision the same subnet second time.
        ProvisionSubnetTaskState state = kickOffSubnetProvision(
                InstanceRequestType.CREATE, subnetState, TaskStage.FAILED);

        assertTrue(state.taskInfo.failure.message.contains(STATUS_CODE_SUBNET_CONFLICT) );
    }

    private Subnet createAwsSubnet() {
        if (this.isMock) {
            Subnet subnet = new Subnet();
            subnet.setSubnetId(UUID.randomUUID().toString());
            return subnet;
        }

        CreateSubnetRequest createRequest = new CreateSubnetRequest(AWS_DEFAULT_VPC_ID,
                AWS_NON_EXISTING_SUBNET_CIDR);
        return this.client.createSubnet(createRequest).getSubnet();
    }

    public void deleteAwsSubnet() {
        if (this.isMock) {
            return;
        }
        DescribeSubnetsRequest subnetRequest = new DescribeSubnetsRequest()
                .withFilters(
                        new Filter(AWS_VPC_ID_FILTER, singletonList(AWS_DEFAULT_VPC_ID)))
                .withFilters(
                        new Filter(AWS_SUBNET_CIDR_FILTER,
                                singletonList(AWS_NON_EXISTING_SUBNET_CIDR)));
        DescribeSubnetsResult subnetResult = this.client.describeSubnets(subnetRequest);
        subnetResult.getSubnets().forEach(subnet -> {
            DeleteSubnetRequest deleteRequest = new DeleteSubnetRequest(subnet.getSubnetId());
            this.client.deleteSubnet(deleteRequest);
        });
    }

    public void deleteAwsPublicSubnet() {
        if (this.isMock) {
            return;
        }
        DescribeSubnetsRequest subnetRequest = new DescribeSubnetsRequest()
                .withFilters(
                        new Filter(AWS_VPC_ID_FILTER, singletonList(AWS_DEFAULT_VPC_ID)))
                .withFilters(
                        new Filter(AWS_SUBNET_CIDR_FILTER,
                                singletonList(AWS_NON_EXISTING_PUBLIC_SUBNET_CIDR)));
        DescribeSubnetsResult subnetResult = this.client.describeSubnets(subnetRequest);
        subnetResult.getSubnets().forEach(subnet -> {
            DeleteSubnetRequest deleteRequest = new DeleteSubnetRequest(subnet.getSubnetId());
            this.client.deleteSubnet(deleteRequest);
        });
    }

    private SubnetState createSubnetState(String awsSubnetId, String name, String subnetCidr,
            String publicSubnetLink) throws Throwable {
        SubnetState subnetState = new SubnetState();
        subnetState.id = awsSubnetId;
        subnetState.lifecycleState = LifecycleState.PROVISIONING;
        subnetState.name = name;
        subnetState.subnetCIDR = subnetCidr;
        subnetState.networkLink = this.networkState.documentSelfLink;
        subnetState.instanceAdapterReference = UriUtils.buildUri(this.host,
                AWSSubnetService.SELF_LINK);
        subnetState.endpointLink = this.endpointState.documentSelfLink;
        subnetState.endpointLinks = new HashSet<>();
        subnetState.endpointLinks.add(this.endpointState.documentSelfLink);
        subnetState.externalSubnetLink = publicSubnetLink;

        return postServiceSynchronously(
                SubnetService.FACTORY_LINK, subnetState, SubnetState.class);
    }

    private NetworkState createNetworkState() throws Throwable {
        NetworkState networkState = new NetworkState();
        networkState.id = AWS_DEFAULT_VPC_ID;
        networkState.subnetCIDR = AWS_DEFAULT_VPC_CIDR;
        networkState.endpointLink = this.endpointState.documentSelfLink;
        networkState.endpointLinks = new HashSet<String>();
        networkState.endpointLinks.add(this.endpointState.documentSelfLink);
        networkState.resourcePoolLink = "dummyResourcePoolLink";
        networkState.regionId = this.regionId;
        networkState.instanceAdapterReference =
                UriUtils.buildUri(this.host, AWSNetworkService.SELF_LINK);

        return postServiceSynchronously(
                NetworkService.FACTORY_LINK, networkState, NetworkState.class);
    }

    private EndpointState createEndpointState() throws Throwable {

        EndpointState endpoint = new EndpointState();

        String endpointType = EndpointType.aws.name();
        endpoint.id = endpointType + "Id";
        endpoint.name = endpointType + "Name";
        endpoint.endpointType = endpointType;
        endpoint.tenantLinks = singletonList(endpointType + "Tenant");
        endpoint.authCredentialsLink = createAuthCredentialsState().documentSelfLink;

        return postServiceSynchronously(
                EndpointService.FACTORY_LINK,
                endpoint,
                EndpointState.class);
    }

    private AuthCredentialsServiceState createAuthCredentialsState() throws Throwable {

        AuthCredentialsServiceState creds = new AuthCredentialsServiceState();

        creds.privateKey = this.secretKey;
        creds.privateKeyId = this.accessKey;

        return postServiceSynchronously(
                AuthCredentialsService.FACTORY_LINK,
                creds,
                AuthCredentialsServiceState.class);
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

    private SubnetState provisionSubnet(String name, String subnetCidr, String publicSubnetLink)
            throws Throwable {
        SubnetState subnetState = createSubnetState(null, name, subnetCidr, publicSubnetLink);

        kickOffSubnetProvision(InstanceRequestType.CREATE, subnetState, TaskStage.FINISHED);

        return getServiceSynchronously(subnetState.documentSelfLink, SubnetState.class);
    }
}
