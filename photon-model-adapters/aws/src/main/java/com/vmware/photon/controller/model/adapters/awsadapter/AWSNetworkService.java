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

package com.vmware.photon.controller.model.adapters.awsadapter;

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_GATEWAY_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_MAIN_ROUTE_ASSOCIATION;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_VPC_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_VPC_ROUTE_TABLE_ID;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.AttachInternetGatewayRequest;
import com.amazonaws.services.ec2.model.CreateInternetGatewayResult;
import com.amazonaws.services.ec2.model.CreateRouteRequest;
import com.amazonaws.services.ec2.model.CreateSubnetRequest;
import com.amazonaws.services.ec2.model.CreateSubnetResult;
import com.amazonaws.services.ec2.model.CreateVpcRequest;
import com.amazonaws.services.ec2.model.CreateVpcResult;
import com.amazonaws.services.ec2.model.DeleteInternetGatewayRequest;
import com.amazonaws.services.ec2.model.DeleteSubnetRequest;
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
import com.amazonaws.services.ec2.model.Vpc;

import com.vmware.photon.controller.model.adapterapi.NetworkInstanceRequest;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.tasks.ProvisionNetworkTaskService.ProvisionNetworkTaskState;
import com.vmware.photon.controller.model.tasks.QueryUtils.QueryForReferrers;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Adapter for provisioning a network on AWS.
 */
public class AWSNetworkService extends StatelessService {

    public static final String SELF_LINK = AWSUriPaths.AWS_NETWORK_ADAPTER;
    public static final String ROUTE_DEST_ALL = "0.0.0.0/0";

    /**
     * Stages for network provisioning.
     */
    private enum AWSNetworkStage {
        NETWORK_TASK_STATE,
        CREDENTIALS,
        AWS_CLIENT,
        NETWORK_STATE,
        PROVISION_VPC,
        REMOVE_VPC,
        PROVISION_SUBNET,
        REMOVE_SUBNET,
        PROVISION_GATEWAY,
        REMOVE_GATEWAY,
        PROVISION_ROUTE,
        REMOVE_ROUTE,
        FINISHED,
        FAILED
    }

    /**
     * Network request context.
     */
    private static class AWSNetworkContext {

        final Operation networkOperation;
        final NetworkInstanceRequest networkRequest;

        AuthCredentialsServiceState credentials;
        NetworkState network;
        AWSNetworkStage stage;
        ProvisionNetworkTaskState networkTaskState;
        Throwable error;
        AWSNetworkClient client;

        AWSNetworkContext(NetworkInstanceRequest networkRequest, Operation networkOperation) {
            this.networkRequest = networkRequest;
            this.networkOperation = networkOperation;
        }
    }

    private AWSClientManager clientManager;

    public AWSNetworkService() {
        this.clientManager = AWSClientManagerFactory
                .getClientManager(AWSConstants.AwsClientType.EC2);
    }

    @Override
    public void handleStop(Operation op) {
        AWSClientManagerFactory.returnClientManager(this.clientManager,
                AWSConstants.AwsClientType.EC2);
        super.handleStop(op);
    }

    @Override
    public void handleRequest(Operation op) {
        switch (op.getAction()) {
        case PATCH:
            if (!op.hasBody()) {
                op.fail(new IllegalArgumentException("body is required"));
                return;
            }
            // initialize allocation object
            AWSNetworkContext context = new AWSNetworkContext(
                    op.getBody(NetworkInstanceRequest.class), op);
            handleStages(context, AWSNetworkStage.NETWORK_TASK_STATE);
            break;
        default:
            super.handleRequest(op);
        }
    }

    private void handleStages(AWSNetworkContext context, Throwable exc) {
        context.error = exc;
        handleStages(context, AWSNetworkStage.FAILED);
    }

    private void handleStages(AWSNetworkContext context, AWSNetworkStage next) {
        context.stage = next;
        handleStages(context);
    }

    private void handleStages(AWSNetworkContext context) {
        try {
            switch (context.stage) {
            case NETWORK_TASK_STATE:
                getNetworkTaskState(context, AWSNetworkStage.NETWORK_STATE);
                break;
            case NETWORK_STATE:
                getNetworkState(context, AWSNetworkStage.CREDENTIALS);
                break;
            case CREDENTIALS:
                getCredentials(context, AWSNetworkStage.AWS_CLIENT);
                break;
            case AWS_CLIENT:
                context.client = new AWSNetworkClient(
                        this.clientManager.getOrCreateEC2Client(
                                context.credentials, context.network.regionId,
                                this, context.networkRequest.taskReference, false));
                if (context.networkRequest.requestType == NetworkInstanceRequest.InstanceRequestType.CREATE) {
                    context.stage = AWSNetworkStage.PROVISION_VPC;
                } else {
                    context.stage = AWSNetworkStage.REMOVE_GATEWAY;
                }
                handleStages(context);
                break;
            case PROVISION_VPC:
                String vpcID = context.client.createVPC(context.network.subnetCIDR);

                updateNetworkProperties(AWS_VPC_ID, vpcID, context,
                        AWSNetworkStage.PROVISION_SUBNET);
                break;
            case PROVISION_SUBNET:
                Subnet subnet = context.client.createSubnet(context.network.subnetCIDR,
                        getCustomProperty(context, AWS_VPC_ID));

                createSubnetState(subnet, context, AWSNetworkStage.PROVISION_GATEWAY);
                break;
            case PROVISION_GATEWAY:
                String gatewayID = context.client.createInternetGateway();
                context.client.attachInternetGateway(getCustomProperty(context, AWS_VPC_ID),
                        gatewayID);

                updateNetworkProperties(AWS_GATEWAY_ID, gatewayID, context,
                        AWSNetworkStage.PROVISION_ROUTE);
                break;
            case PROVISION_ROUTE:
                RouteTable routeTable = context.client.getMainRouteTable(
                        context.network.customProperties.get(AWS_VPC_ID));
                context.client.createInternetRoute(getCustomProperty(context, AWS_GATEWAY_ID),
                        routeTable.getRouteTableId(), ROUTE_DEST_ALL);

                updateNetworkProperties(AWS_VPC_ROUTE_TABLE_ID,
                        routeTable.getRouteTableId(), context, AWSNetworkStage.FINISHED);
                break;
            case REMOVE_GATEWAY:
                context.client.detachInternetGateway(getCustomProperty(context, AWS_VPC_ID),
                        getCustomProperty(context, AWS_GATEWAY_ID));
                context.client.deleteInternetGateway(getCustomProperty(context, AWS_GATEWAY_ID));

                updateNetworkProperties(AWS_GATEWAY_ID, AWSUtils.NO_VALUE, context,
                        AWSNetworkStage.REMOVE_SUBNET);
                break;
            case REMOVE_SUBNET:
                // Iterate SubnetStates (page-by-page) and delete AWS Subnet and SubnetState
                deleteSubnetStates(context, AWSNetworkStage.REMOVE_ROUTE);
                break;
            case REMOVE_ROUTE:
                // only need to update the document, the AWS artifact will be
                // removed on VPC removal
                updateNetworkProperties(AWS_VPC_ROUTE_TABLE_ID, AWSUtils.NO_VALUE,
                        context, AWSNetworkStage.REMOVE_VPC);
                break;
            case REMOVE_VPC:
                context.client.deleteVPC(getCustomProperty(context, AWS_VPC_ID));

                updateNetworkProperties(AWS_VPC_ID, AWSUtils.NO_VALUE, context,
                        AWSNetworkStage.FINISHED);
                break;
            case FAILED:
                if (context.networkRequest.taskReference != null) {
                    AdapterUtils.sendFailurePatchToProvisioningTask(
                            this, context.networkRequest.taskReference, context.error);
                } else {
                    context.networkOperation.fail(context.error);
                }
                break;
            case FINISHED:
                context.networkOperation.complete();
                AdapterUtils.sendNetworkFinishPatch(
                        this, context.networkRequest.taskReference);
                break;
            default:
                break;
            }
        } catch (Throwable error) {
            // Same as FAILED stage
            if (context.networkRequest.taskReference != null) {
                AdapterUtils.sendFailurePatchToProvisioningTask(
                        this, context.networkRequest.taskReference, error);
            } else {
                context.networkOperation.fail(error);
            }
        }
    }

    private String getCustomProperty(AWSNetworkContext context, String key) {
        return context.network.customProperties.get(key);
    }

    private void updateNetworkProperties(String key, String value,
            AWSNetworkContext context, AWSNetworkStage next) {
        if (context.network.customProperties == null) {
            context.network.customProperties = new HashMap<>();
        }

        context.network.customProperties.put(key, value);

        sendRequest(
                Operation.createPatch(this.getHost(),
                        context.networkTaskState.networkDescriptionLink)
                        .setBody(context.network)
                        .setCompletion((o, e) -> {
                            if (e != null) {
                                handleStages(context, e);
                                return;
                            }
                            handleStages(context, next);
                        }));
    }

    /**
     * Create SubnetState representing AWS Subnet instance.
     */
    private void createSubnetState(Subnet subnet, AWSNetworkContext context, AWSNetworkStage next) {
        SubnetState subnetState = new SubnetState();
        subnetState.id = subnet.getSubnetId();
        subnetState.name = subnet.getSubnetId();
        subnetState.subnetCIDR = subnet.getCidrBlock();
        subnetState.networkLink = context.network.documentSelfLink;
        subnetState.tenantLinks = context.network.tenantLinks;

        sendRequest(
                Operation.createPost(this.getHost(), SubnetService.FACTORY_LINK)
                        .setBody(subnetState)
                        .setCompletion((o, e) -> {
                            if (e != null) {
                                handleStages(context, e);
                                return;
                            }
                            handleStages(context, next);
                        }));
    }

    private void getCredentials(AWSNetworkContext context, AWSNetworkStage next) {

        sendRequest(Operation.createGet(this.getHost(), context.networkRequest.authCredentialsLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        handleStages(context, e);
                        return;
                    }
                    context.credentials = o.getBody(AuthCredentialsServiceState.class);
                    handleStages(context, next);
                }));
    }

    private void getNetworkState(AWSNetworkContext context, AWSNetworkStage next) {

        sendRequest(
                Operation.createGet(this.getHost(), context.networkTaskState.networkDescriptionLink)
                        .setCompletion((o, e) -> {
                            if (e != null) {
                                handleStages(context, e);
                                return;
                            }
                            context.network = o.getBody(NetworkState.class);
                            handleStages(context, next);
                        }));
    }

    private void getNetworkTaskState(AWSNetworkContext context,
            AWSNetworkStage next) {
        sendRequest(Operation.createGet(context.networkRequest.taskReference)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        handleStages(context, e);
                        return;
                    }
                    context.networkTaskState = o
                            .getBody(ProvisionNetworkTaskState.class);
                    handleStages(context, next);
                }));
    }

    /**
     * This client abstracts the communication with Amazon Network service.
     */
    public static class AWSNetworkClient {

        private final AmazonEC2AsyncClient client;

        public AWSNetworkClient(AmazonEC2AsyncClient client) {
            this.client = client;
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
         * Creates the subnet and return it
         */
        public Subnet createSubnet(String subnetCidr, String vpcId) {
            CreateSubnetRequest req = new CreateSubnetRequest()
                    .withCidrBlock(subnetCidr)
                    .withVpcId(vpcId);
            CreateSubnetResult res = this.client.createSubnet(req);
            return res.getSubnet();
        }

        /**
         * Delete the specified subnet
         */
        public void deleteSubnet(String subnetId) {
            DeleteSubnetRequest req = new DeleteSubnetRequest().withSubnetId(subnetId);
            this.client.deleteSubnet(req);
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

    /**
     * Delete all subnet states that refer the NetworkState we are about to delete.
     */
    private void deleteSubnetStates(AWSNetworkContext context, AWSNetworkStage next) {

        QueryForReferrers<SubnetState> subnetStates = new QueryForReferrers<>(
                getHost(),
                context.network.documentSelfLink,
                SubnetState.class,
                SubnetState.FIELD_NAME_NETWORK_LINK,
                context.networkTaskState.tenantLinks);

        DeferredResult<Void> query = subnetStates.queryDocuments(subnetState -> {
            // First delete Subnet in AWS
            context.client.deleteSubnet(subnetState.id);
            // Then delete tracking SubnetState
            Operation.createDelete(this, subnetState.documentSelfLink).sendWith(this);
        });

        query.whenComplete((v, e) -> {
            if (e != null) {
                handleStages(context, e);
            } else {
                handleStages(context, next);
            }
        });
    }
}
