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

package com.vmware.photon.controller.model.adapters.awsadapter.util;

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_MAIN_ROUTE_ASSOCIATION;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_TAG_NAME;

import java.util.ArrayList;
import java.util.List;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.AmazonEC2Exception;
import com.amazonaws.services.ec2.model.AttachInternetGatewayRequest;
import com.amazonaws.services.ec2.model.CreateInternetGatewayResult;
import com.amazonaws.services.ec2.model.CreateRouteRequest;
import com.amazonaws.services.ec2.model.CreateSubnetRequest;
import com.amazonaws.services.ec2.model.CreateSubnetResult;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.CreateTagsResult;
import com.amazonaws.services.ec2.model.CreateVpcRequest;
import com.amazonaws.services.ec2.model.CreateVpcResult;
import com.amazonaws.services.ec2.model.DeleteInternetGatewayRequest;
import com.amazonaws.services.ec2.model.DeleteSubnetRequest;
import com.amazonaws.services.ec2.model.DeleteSubnetResult;
import com.amazonaws.services.ec2.model.DeleteVpcRequest;
import com.amazonaws.services.ec2.model.DescribeInternetGatewaysRequest;
import com.amazonaws.services.ec2.model.DescribeInternetGatewaysResult;
import com.amazonaws.services.ec2.model.DescribeRouteTablesRequest;
import com.amazonaws.services.ec2.model.DescribeRouteTablesResult;
import com.amazonaws.services.ec2.model.DescribeSubnetsRequest;
import com.amazonaws.services.ec2.model.DescribeSubnetsResult;
import com.amazonaws.services.ec2.model.DescribeVpcsRequest;
import com.amazonaws.services.ec2.model.DescribeVpcsResult;
import com.amazonaws.services.ec2.model.DetachInternetGatewayRequest;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.InternetGateway;
import com.amazonaws.services.ec2.model.RouteTable;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.Vpc;

import com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.StatelessService;

/**
 * This client abstracts the communication with Amazon Network service.
 */
public class AWSNetworkClient {

    public static final String STATUS_CODE_SUBNET_NOT_FOUND = "InvalidSubnetID.NotFound";
    public static final String STATUS_CODE_SUBNET_CONFLICT = "InvalidSubnet.Conflict";

    private final AmazonEC2AsyncClient client;
    private StatelessService service;

    public AWSNetworkClient(AmazonEC2AsyncClient client) {
        this.client = client;
    }

    public AWSNetworkClient(StatelessService service, AmazonEC2AsyncClient client) {
        this(client);
        this.service = service;
    }

    public Vpc getVPC(String vpcId) {
        DescribeVpcsRequest req = new DescribeVpcsRequest().withVpcIds(vpcId);
        DescribeVpcsResult result = this.client.describeVpcs(req);
        List<Vpc> vpcs = result.getVpcs();
        if (vpcs != null && vpcs.size() == 1) {
            return vpcs.get(0);
        }
        return null;
    }

    /**
     * Get the default VPC - return null if no default specified
     */
    public Vpc getDefaultVPC() {
        DescribeVpcsRequest req = new DescribeVpcsRequest();
        DescribeVpcsResult result = this.client.describeVpcs(req);
        List<Vpc> vpcs = result.getVpcs();
        for (Vpc vpc : vpcs) {
            if (vpc.isDefault()) {
                return vpc;
            }
        }
        return null;
    }

    /**
     * Creates the VPC and returns the VPC id
     */
    public String createVPC(String subnetCidr) {
        CreateVpcRequest req = new CreateVpcRequest().withCidrBlock(subnetCidr);
        CreateVpcResult vpc = this.client.createVpc(req);

        return vpc.getVpc().getVpcId();
    }

    /**
     * Delete the specified VPC
     */
    public void deleteVPC(String vpcId) {
        DeleteVpcRequest req = new DeleteVpcRequest().withVpcId(vpcId);
        this.client.deleteVpc(req);
    }

    public Subnet getSubnet(String subnetId) {
        DescribeSubnetsRequest req = new DescribeSubnetsRequest()
                .withSubnetIds(subnetId);
        DescribeSubnetsResult subnetResult = this.client.describeSubnets(req);
        List<Subnet> subnets = subnetResult.getSubnets();
        return subnets.isEmpty() ? null : subnets.get(0);
    }

    /**
     * Creates the subnet and return it.
     */
    public Subnet createSubnet(String subnetCidr, String vpcId) {
        CreateSubnetRequest req = new CreateSubnetRequest()
                .withCidrBlock(subnetCidr)
                .withVpcId(vpcId);
        CreateSubnetResult res = this.client.createSubnet(req);
        return res.getSubnet();
    }

    /**
     * Async create the subnet and return it.
     */
    public DeferredResult<Subnet> createSubnetAsync(String subnetCidr, String vpcId) {

        CreateSubnetRequest req = new CreateSubnetRequest()
                .withCidrBlock(subnetCidr)
                .withVpcId(vpcId);

        String message = "Create AWS Subnet with CIDR [" + subnetCidr
                + "] for vpc id [" + vpcId + "].";

        AWSDeferredResultAsyncHandler<CreateSubnetRequest, CreateSubnetResult> handler = new
                AWSDeferredResultAsyncHandler<>(this.service, message);
        this.client.createSubnetAsync(req, handler);
        return handler.toDeferredResult()
                .thenApply(CreateSubnetResult::getSubnet);
    }

    public DeferredResult<Void> createNameTagAsync(String resourceId, String name) {
        Tag nameTag = new Tag().withKey(AWS_TAG_NAME).withValue(name);

        CreateTagsRequest request = new CreateTagsRequest()
                .withResources(resourceId)
                .withTags(nameTag);

        String message = "Name tag AWS resource with id [" + resourceId + "] with name ["
                + name + "].";
        AWSDeferredResultAsyncHandler<CreateTagsRequest, CreateTagsResult> handler =
                new AWSDeferredResultAsyncHandler<>(this.service, message);

        this.client.createTagsAsync(request, handler);

        return handler.toDeferredResult()
                .thenApply(result -> (Void) null);
    }

    /**
     * Delete the specified subnet
     *
     * @throws AmazonEC2Exception if the subnet doesn't exist.
     */
    public void deleteSubnet(String subnetId) throws AmazonEC2Exception {
        DeleteSubnetRequest req = new DeleteSubnetRequest().withSubnetId(subnetId);
        this.client.deleteSubnet(req);
    }

    public DeferredResult<Void> deleteSubnetAsync(String subnetId) {
        DeleteSubnetRequest req = new DeleteSubnetRequest().withSubnetId(subnetId);
        String message = "Delete AWS Subnet with id [" + subnetId + "].";

        AWSDeferredResultAsyncHandler<DeleteSubnetRequest, DeleteSubnetResult> handler = new
                AWSDeferredResultAsyncHandler<>(this.service, message);

        this.client.deleteSubnetAsync(req, handler);

        return handler.toDeferredResult()
                .thenApply(result -> (Void) null);
    }

    public String createInternetGateway() {
        CreateInternetGatewayResult res = this.client.createInternetGateway();
        return res.getInternetGateway().getInternetGatewayId();
    }

    public InternetGateway getInternetGateway(String resourceId) {
        DescribeInternetGatewaysRequest req = new DescribeInternetGatewaysRequest()
                .withInternetGatewayIds(resourceId);
        DescribeInternetGatewaysResult res = this.client.describeInternetGateways(req);
        List<InternetGateway> internetGateways = res.getInternetGateways();
        return internetGateways.isEmpty() ? null : internetGateways.get(0);
    }

    public void deleteInternetGateway(String resourceID) {
        DeleteInternetGatewayRequest req = new DeleteInternetGatewayRequest()
                .withInternetGatewayId(resourceID);
        this.client.deleteInternetGateway(req);
    }

    public void attachInternetGateway(String vpcId, String gatewayId) {
        AttachInternetGatewayRequest req = new AttachInternetGatewayRequest()
                .withVpcId(vpcId)
                .withInternetGatewayId(gatewayId);
        this.client.attachInternetGateway(req);
    }

    public void detachInternetGateway(String vpcId, String gatewayId) {
        DetachInternetGatewayRequest req = new DetachInternetGatewayRequest()
                .withVpcId(vpcId)
                .withInternetGatewayId(gatewayId);
        this.client.detachInternetGateway(req);
    }

    /**
     * Get the main route table for a given VPC
     */
    public RouteTable getMainRouteTable(String vpcId) {
        // build filter list
        List<Filter> filters = new ArrayList<>();
        filters.add(AWSUtils.getFilter(AWSUtils.AWS_FILTER_VPC_ID, vpcId));
        filters.add(AWSUtils.getFilter(AWS_MAIN_ROUTE_ASSOCIATION, "true"));

        DescribeRouteTablesRequest req = new DescribeRouteTablesRequest()
                .withFilters(filters);
        DescribeRouteTablesResult res = this.client.describeRouteTables(req);
        List<RouteTable> routeTables = res.getRouteTables();
        return routeTables.isEmpty() ? null : routeTables.get(0);
    }

    /**
     * Create a route from a specified CIDR Subnet to a specific GW / Route Table
     */
    public void createInternetRoute(String gatewayId, String routeTableId, String subnetCidr) {
        CreateRouteRequest req = new CreateRouteRequest()
                .withGatewayId(gatewayId)
                .withRouteTableId(routeTableId)
                .withDestinationCidrBlock(subnetCidr);
        this.client.createRoute(req);
    }
}
