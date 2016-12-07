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

package com.vmware.photon.controller.model.adapters.awsadapter.enumeration;

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_ATTACHMENT_VPC_FILTER;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_GATEWAY_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_MAIN_ROUTE_ASSOCIATION;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_SUBNET_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_VPC_FILTER;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_VPC_ROUTE_TABLE_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.AWS_FILTER_VPC_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSNetworkUtils.createQueryToGetExistingNetworkStatesFilteredByDiscoveredVPCs;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSNetworkUtils.createQueryToGetExistingSubnetStatesFilteredByDiscoveredSubnets;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSNetworkUtils.mapSubnetToSubnetState;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSNetworkUtils.mapVPCToNetworkState;
import static com.vmware.photon.controller.model.adapters.util.AdapterUtils.createPatchOperation;
import static com.vmware.photon.controller.model.adapters.util.AdapterUtils.createPostOperation;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.DescribeInternetGatewaysRequest;
import com.amazonaws.services.ec2.model.DescribeInternetGatewaysResult;
import com.amazonaws.services.ec2.model.DescribeRouteTablesRequest;
import com.amazonaws.services.ec2.model.DescribeRouteTablesResult;
import com.amazonaws.services.ec2.model.DescribeSubnetsRequest;
import com.amazonaws.services.ec2.model.DescribeSubnetsResult;
import com.amazonaws.services.ec2.model.DescribeVpcsRequest;
import com.amazonaws.services.ec2.model.DescribeVpcsResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.InternetGateway;
import com.amazonaws.services.ec2.model.InternetGatewayAttachment;
import com.amazonaws.services.ec2.model.RouteTable;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Vpc;

import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUriPaths;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSNetworkStateCreationAdapterService.AWSNetworkServiceCreationContext.SubnetStateWithParentVpcId;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSAsyncHandler;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Stateless service for the creation of compute states. It accepts a list of AWS instances that
 * need to be created in the local system.It also accepts a few additional fields required for
 * mapping the referential integrity relationships for the compute state when it is persisted in the
 * local system.
 */
public class AWSNetworkStateCreationAdapterService extends StatelessService {

    public static final String SELF_LINK = AWSUriPaths.AWS_NETWORK_STATE_CREATION_ADAPTER;

    private AWSClientManager clientManager;

    public static enum AWSNetworkCreationStage {
        CLIENT,
        GET_REMOTE_VPC,
        GET_LOCAL_NETWORK_STATES,
        GET_REMOTE_SUBNETS,
        GET_LOCAL_SUBNET_STATES,
        GET_INTERNET_GATEWAY,
        GET_MAIN_ROUTE_TABLE,
        CREATE_NETWORKSTATE,
        CREATE_SUBNETSTATE,
        SIGNAL_COMPLETION
    }

    public AWSNetworkStateCreationAdapterService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        this.clientManager = AWSClientManagerFactory
                .getClientManager(AWSConstants.AwsClientType.EC2);
    }

    /**
     * Data holder for information related a network state that needs to be enumerated.
     */
    public static class AWSNetworkEnumeration {
        public ComputeEnumerateResourceRequest enumerationRequest;
        public AuthCredentialsService.AuthCredentialsServiceState parentAuth;
        public String regionId;
        public List<String> tenantLinks;
    }

    public static class NetworkEnumerationResponse {
        public Map<String, String> vpcs = new HashMap<>();
        public Map<String, String> subnets = new HashMap<>();
    }

    /**
     * The service context that is created for representing the list of instances received into a
     * list of compute states that will be persisted in the system.
     */
    static class AWSNetworkServiceCreationContext {

        static class SubnetStateWithParentVpcId {
            String parentVpcId;
            SubnetState subnetState;

            SubnetStateWithParentVpcId(String parentVpcId, SubnetState subnetState) {
                this.parentVpcId = parentVpcId;
                this.subnetState = subnetState;
            }
        }

        public AmazonEC2AsyncClient amazonEC2Client;
        public AWSNetworkEnumeration networkRequest;
        public AWSNetworkCreationStage networkCreationStage;

        // Map AWS VPC id to network state for discovered VPCs
        public Map<String, NetworkState> vpcs = new HashMap<>();
        // Map for local network states. key = vpc-id, value = NetworkState.documentSelfLink
        public Map<String, String> localNetworkStateMap = new HashMap<>();

        // Map AWS Subnet id to subnet state for discovered Subnets
        public Map<String, SubnetStateWithParentVpcId> subnets = new HashMap<>();
        // Map for local subnet states. key = subnet-id, value = SubnetState.documentSelfLink
        public Map<String, String> localSubnetStateMap = new HashMap<>();

        // Cached operation to signal completion to the AWS instance adapter once all the compute
        // states are successfully created.
        public final Operation awsAdapterOperation;

        public AWSNetworkServiceCreationContext(AWSNetworkEnumeration networkRequest,
                Operation op) {
            this.networkRequest = networkRequest;
            this.networkCreationStage = AWSNetworkCreationStage.CLIENT;
            this.awsAdapterOperation = op;
        }
    }

    @Override
    public void handleStop(Operation op) {
        AWSClientManagerFactory.returnClientManager(this.clientManager,
                AWSConstants.AwsClientType.EC2);
        super.handleStop(op);
    }

    @Override
    public void handlePatch(Operation op) {
        setOperationHandlerInvokeTimeStat(op);
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        AWSNetworkEnumeration cs = op.getBody(AWSNetworkEnumeration.class);
        AWSNetworkServiceCreationContext context = new AWSNetworkServiceCreationContext(cs, op);
        if (cs.enumerationRequest.isMockRequest) {
            op.complete();
        }
        handleNetworkStateChanges(context);
    }

    /**
     * Handles the process to create and EC2 Async client and get all the VPC related information
     * from AWS. At the very least it gets the CIDR block information for the VPC, the connected
     * internet gateway (if any) and the main route table information for the VPC.
     */
    private void handleNetworkStateChanges(AWSNetworkServiceCreationContext context) {
        switch (context.networkCreationStage) {
        case CLIENT:
            getAWSAsyncClient(context, AWSNetworkCreationStage.GET_REMOTE_VPC);
            break;
        case GET_REMOTE_VPC:
            refreshVPCInformation(context, AWSNetworkCreationStage.GET_LOCAL_NETWORK_STATES);
            break;
        case GET_LOCAL_NETWORK_STATES:
            getLocalNetworkStates(context, AWSNetworkCreationStage.GET_REMOTE_SUBNETS);
            break;
        case GET_REMOTE_SUBNETS:
            getSubnetInformation(context, AWSNetworkCreationStage.GET_LOCAL_SUBNET_STATES);
            break;
        case GET_LOCAL_SUBNET_STATES:
            getLocalSubnetStates(context, AWSNetworkCreationStage.GET_INTERNET_GATEWAY);
            break;
        case GET_INTERNET_GATEWAY:
            getInternetGatewayInformation(context, AWSNetworkCreationStage.GET_MAIN_ROUTE_TABLE);
            break;
        case GET_MAIN_ROUTE_TABLE:
            getMainRouteTableInformation(context, AWSNetworkCreationStage.CREATE_NETWORKSTATE);
            break;
        case CREATE_NETWORKSTATE:
            createNetworkStateOperations(context, AWSNetworkCreationStage.CREATE_SUBNETSTATE);
            break;
        case CREATE_SUBNETSTATE:
            createSubnetStateOperations(context, AWSNetworkCreationStage.SIGNAL_COMPLETION);
            break;
        case SIGNAL_COMPLETION:
            setOperationDurationStat(context.awsAdapterOperation);
            context.awsAdapterOperation.setBody(createResponse(context));
            context.awsAdapterOperation.complete();
            break;
        default:
            Throwable t = new IllegalArgumentException(
                    "Unknown AWS enumeration:network state creation stage");
            finishWithFailure(context, t);
            break;
        }
    }

    /**
     * Shortcut method that sets the next stage into the context and delegates to
     * {@link #handleNetworkStateChanges(AWSNetworkServiceCreationContext)}.
     */
    private void handleNetworkStateChanges(
            AWSNetworkServiceCreationContext context,
            AWSNetworkCreationStage next) {
        context.networkCreationStage = next;
        handleNetworkStateChanges(context);
    }

    private Object createResponse(AWSNetworkServiceCreationContext context) {

        NetworkEnumerationResponse response = new NetworkEnumerationResponse();

        context.vpcs.forEach((k, v) -> response.vpcs.put(k, v.documentSelfLink));

        context.subnets.forEach((k, v) -> response.subnets.put(k, v.subnetState.documentSelfLink));

        return response;
    }

    private void refreshVPCInformation(AWSNetworkServiceCreationContext aws,
            AWSNetworkCreationStage next) {
        DescribeVpcsRequest vpcRequest = new DescribeVpcsRequest();
        AWSVPCAsyncHandler asyncHandler = new AWSVPCAsyncHandler(next, aws);
        aws.amazonEC2Client.describeVpcsAsync(vpcRequest, asyncHandler);
    }

    /**
     * Method to instantiate the AWS Async client for future use
     */
    private void getAWSAsyncClient(AWSNetworkServiceCreationContext context,
            AWSNetworkCreationStage next) {
        context.amazonEC2Client = this.clientManager.getOrCreateEC2Client(
                context.networkRequest.parentAuth, context.networkRequest.regionId,
                this, context.networkRequest.enumerationRequest.taskReference, true);

        handleNetworkStateChanges(context, next);
    }

    /**
     * Gets the VPC information from the local database to perform updates to existing network
     * states.
     */
    private void getLocalNetworkStates(AWSNetworkServiceCreationContext context,
            AWSNetworkCreationStage next) {
        QueryTask q = createQueryToGetExistingNetworkStatesFilteredByDiscoveredVPCs(
                context.vpcs.keySet(), context.networkRequest.tenantLinks);
        // create the query to find resources

        sendRequest(Operation
                .createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(q)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere("Failure retrieving query results: %s",
                                e.toString());
                        finishWithFailure(context, e);
                        return;
                    }
                    QueryTask responseTask = o.getBody(QueryTask.class);
                    if (responseTask.results.documents != null) {
                        for (Object s : responseTask.results.documents.values()) {
                            NetworkState networkState = Utils.fromJson(s,
                                    NetworkState.class);
                            context.localNetworkStateMap.put(networkState.id,
                                    networkState.documentSelfLink);
                        }
                    }
                    logInfo("Result of query to get local networks. There are %d network states known to the system.",
                            responseTask.results.documentCount);

                    handleNetworkStateChanges(context, next);
                }));
    }

    /**
     * Gets the Subnets that are attached to the VPCs that were discovered during the enumeration
     * process.
     */
    private void getSubnetInformation(AWSNetworkServiceCreationContext context,
            AWSNetworkCreationStage next) {
        DescribeSubnetsRequest subnetRequest = new DescribeSubnetsRequest();
        List<String> vpcList = new ArrayList<String>(context.vpcs.keySet());
        Filter filter = new Filter(AWS_VPC_FILTER, vpcList);
        subnetRequest.getFilters().add(filter);
        AWSSubnetAsyncHandler asyncHandler = new AWSSubnetAsyncHandler(next, context);
        context.amazonEC2Client.describeSubnetsAsync(subnetRequest, asyncHandler);
    }

    /**
     * Gets the Subnet information from the local database to perform updates to existing subnet
     * states.
     */
    private void getLocalSubnetStates(AWSNetworkServiceCreationContext context,
            AWSNetworkCreationStage next) {
        QueryTask q = createQueryToGetExistingSubnetStatesFilteredByDiscoveredSubnets(
                context.subnets.keySet(), context.networkRequest.tenantLinks);
        // create the query to find resources

        sendRequest(Operation
                .createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(q)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere("Failure retrieving query results: %s",
                                e.toString());
                        finishWithFailure(context, e);
                        return;
                    }
                    QueryTask responseTask = o.getBody(QueryTask.class);
                    if (responseTask.results.documents != null) {
                        for (Object s : responseTask.results.documents.values()) {
                            SubnetState subnetState = Utils.fromJson(s,
                                    SubnetState.class);
                            context.localSubnetStateMap.put(subnetState.id,
                                    subnetState.documentSelfLink);
                        }
                    }
                    logInfo("Result of query to get local subnets. There are %d subnet states known to the system.",
                            responseTask.results.documentCount);

                    handleNetworkStateChanges(context, next);
                }));
    }

    /**
     * Gets the Internet gateways that are attached to the VPCs that were discovered during the
     * enumeration process.
     */
    private void getInternetGatewayInformation(AWSNetworkServiceCreationContext context,
            AWSNetworkCreationStage next) {
        DescribeInternetGatewaysRequest internetGatewayRequest = new DescribeInternetGatewaysRequest();
        List<String> vpcList = new ArrayList<String>(context.vpcs.keySet());
        Filter filter = new Filter(AWS_ATTACHMENT_VPC_FILTER, vpcList);
        internetGatewayRequest.getFilters().add(filter);
        AWSInternetGatewayAsyncHandler asyncHandler = new AWSInternetGatewayAsyncHandler(next,
                context);
        context.amazonEC2Client.describeInternetGatewaysAsync(internetGatewayRequest, asyncHandler);
    }

    /**
     * The async handler to handle the success and errors received after invoking the describe VPCs
     * API on AWS
     */
    class AWSVPCAsyncHandler
            extends TransitionToAsyncHandler<DescribeVpcsRequest, DescribeVpcsResult> {

        AWSVPCAsyncHandler(AWSNetworkCreationStage next, AWSNetworkServiceCreationContext context) {
            super(next, context);
        }

        @Override
        protected void consumeSuccess(DescribeVpcsRequest request, DescribeVpcsResult result) {

            URI adapterUri = AdapterUriUtil.buildAdapterUri(getHost(),
                    AWSUriPaths.AWS_NETWORK_ADAPTER);
            for (Vpc resultVPC : result.getVpcs()) {
                NetworkState networkState = mapVPCToNetworkState(resultVPC,
                        this.context.networkRequest.regionId,
                        this.context.networkRequest.enumerationRequest.resourcePoolLink,
                        this.context.networkRequest.parentAuth.documentSelfLink,
                        this.context.networkRequest.tenantLinks,
                        adapterUri);
                if (networkState.subnetCIDR == null) {
                    logWarning("AWS did not return CIDR information for VPC %s",
                            resultVPC.toString());
                }
                this.context.vpcs.put(resultVPC.getVpcId(), networkState);
            }
        }
    }

    /**
     * The async handler to handle the success and errors received after invoking the describe
     * Internet Gateways API on AWS
     */
    class AWSInternetGatewayAsyncHandler extends
            TransitionToAsyncHandler<DescribeInternetGatewaysRequest, DescribeInternetGatewaysResult> {

        AWSInternetGatewayAsyncHandler(
                AWSNetworkCreationStage next,
                AWSNetworkServiceCreationContext context) {
            super(next, context);
        }

        /**
         * Update the Internet gateway information for the VPC in question. For the list of Internet
         * gateways received based on the vpc filter work through the list of attachments and VPCs
         * and update the Internet gateway information in the network state that maps to the VPC.
         */
        @Override
        protected void consumeSuccess(DescribeInternetGatewaysRequest request,
                DescribeInternetGatewaysResult result) {
            for (InternetGateway resultGateway : result.getInternetGateways()) {
                for (InternetGatewayAttachment attachment : resultGateway.getAttachments()) {
                    if (this.context.vpcs.containsKey(attachment.getVpcId())) {
                        NetworkState networkStateToUpdate = this.context.vpcs
                                .get(attachment.getVpcId());
                        networkStateToUpdate.customProperties.put(AWS_GATEWAY_ID,
                                resultGateway.getInternetGatewayId());
                        this.context.vpcs.put(attachment.getVpcId(), networkStateToUpdate);
                    }
                }
            }
        }
    }

    /**
     * The async handler to handle the success and errors received after invoking the describe
     * Internet Gateways API on AWS
     */
    class AWSSubnetAsyncHandler extends
            TransitionToAsyncHandler<DescribeSubnetsRequest, DescribeSubnetsResult> {

        AWSSubnetAsyncHandler(
                AWSNetworkCreationStage next,
                AWSNetworkServiceCreationContext context) {
            super(next, context);
        }

        /**
         * Update the Subnet information for the VPC in question.
         */
        @Override
        protected void consumeSuccess(DescribeSubnetsRequest request,
                DescribeSubnetsResult result) {

            for (Subnet subnet : result.getSubnets()) {

                if (!this.context.vpcs.containsKey(subnet.getVpcId())) {
                    logWarning("AWS returned Subnet [%s] with VCP [%s] that is missing locally.",
                            subnet.getSubnetId(), subnet.getVpcId());
                    continue;
                }

                NetworkState parentNetworkState = this.context.vpcs
                        .get(subnet.getVpcId());

                SubnetState subnetState = mapSubnetToSubnetState(subnet,
                        this.context.networkRequest.tenantLinks);

                if (subnetState.subnetCIDR == null) {
                    logWarning("AWS did not return CIDR information for Subnet %s",
                            subnet.toString());
                }

                // Update parent NetworkState with this Subnet
                parentNetworkState.customProperties.put(AWS_SUBNET_ID,
                        subnet.getSubnetId());

                this.context.subnets.put(
                        subnet.getSubnetId(),
                        new AWSNetworkServiceCreationContext.SubnetStateWithParentVpcId(
                                subnet.getVpcId(), subnetState));
            }
        }
    }

    /**
     * Gets the main route table information associated with a VPC that is being mapped to a network
     * state in the system. *
     */
    private void getMainRouteTableInformation(AWSNetworkServiceCreationContext context,
            AWSNetworkCreationStage next) {
        DescribeRouteTablesRequest routeTablesRequest = new DescribeRouteTablesRequest();
        List<String> vpcList = new ArrayList<String>(context.vpcs.keySet());

        // build filter list
        List<Filter> filters = new ArrayList<>();
        filters.add(new Filter(AWS_FILTER_VPC_ID, vpcList));
        filters.add(AWSUtils.getFilter(AWS_MAIN_ROUTE_ASSOCIATION, "true"));

        AWSMainRouteTableAsyncHandler asyncHandler = new AWSMainRouteTableAsyncHandler(next,
                context);
        context.amazonEC2Client.describeRouteTablesAsync(routeTablesRequest, asyncHandler);
    }

    /**
     * The async handler to handle the success and errors received after invoking the describe Route
     * Tables API on AWS
     */
    class AWSMainRouteTableAsyncHandler extends
            TransitionToAsyncHandler<DescribeRouteTablesRequest, DescribeRouteTablesResult> {

        AWSMainRouteTableAsyncHandler(
                AWSNetworkCreationStage next,
                AWSNetworkServiceCreationContext context) {
            super(next, context);
        }

        /**
         * Update the main route table information for the VPC that is being mapped to a network
         * state. Query AWS for the main route tables with a list of VPCs. From the result set find
         * the relevant route table Id and upda
         */
        @Override
        protected void consumeSuccess(DescribeRouteTablesRequest request,
                DescribeRouteTablesResult result) {
            for (RouteTable routeTable : result.getRouteTables()) {
                if (this.context.vpcs.containsKey(routeTable.getVpcId())) {
                    NetworkState networkStateToUpdate = this.context.vpcs
                            .get(routeTable.getVpcId());
                    networkStateToUpdate.customProperties.put(AWS_VPC_ROUTE_TABLE_ID,
                            routeTable.getRouteTableId());
                    this.context.vpcs.put(routeTable.getVpcId(),
                            networkStateToUpdate);
                }
            }
        }
    }

    /**
     * Create the network state operations for all the VPCs that need to be created or updated in
     * the system.
     */
    private void createNetworkStateOperations(AWSNetworkServiceCreationContext context,
            AWSNetworkCreationStage next) {

        if (context.vpcs.isEmpty()) {
            logInfo("No new VPCs have been discovered.Nothing to do.");

            handleNetworkStateChanges(context, next);
            return;
        }

        List<Operation> networkOperations = new ArrayList<>();

        for (String remoteVPCId : context.vpcs.keySet()) {

            NetworkState networkState = context.vpcs.get(remoteVPCId);

            final Operation networkStateOp;
            if (context.localNetworkStateMap.containsKey(remoteVPCId)) {
                // If the local network state already exists for the VPC -> Update it.
                String localNetworkStateSelfLink = context.localNetworkStateMap.get(remoteVPCId);

                networkStateOp = createPatchOperation(this,
                        networkState, localNetworkStateSelfLink);
            } else {
                networkStateOp = createPostOperation(this,
                        networkState, NetworkService.FACTORY_LINK);
            }
            networkOperations.add(networkStateOp);
        }

        OperationJoin.JoinedCompletionHandler joinCompletion = (ops,
                exc) -> {
            if (exc != null) {
                logSevere("Error creating/updating a Network state %s", Utils.toString(exc));
                finishWithFailure(context, exc.values().iterator().next());
                return;
            }
            logInfo("Successfully created/updated all the network states.");
            ops.values().forEach(op -> {
                NetworkState networkState = op.getBody(NetworkState.class);
                context.vpcs.put(networkState.id, networkState);
            });
            handleNetworkStateChanges(context, next);
        };
        OperationJoin.create(networkOperations)
                .setCompletion(joinCompletion)
                .sendWith(this);
    }

    /**
     * Create the subnet state operations for all the Subnets that need to be created or updated in
     * the system.
     */
    private void createSubnetStateOperations(AWSNetworkServiceCreationContext context,
            AWSNetworkCreationStage next) {

        if (context.subnets.isEmpty()) {
            logInfo("No new Subnets have been discovered.Nothing to do.");
            handleNetworkStateChanges(context, next);
            return;
        }

        List<Operation> subnetOperations = new ArrayList<>();

        for (String remoteSubnetId : context.subnets.keySet()) {

            SubnetStateWithParentVpcId subnetStateWithParentVpcId = context.subnets
                    .get(remoteSubnetId);
            SubnetState subnetState = subnetStateWithParentVpcId.subnetState;
            // Update networkLink with "latest" (either created or updated)
            // NetworkState.documentSelfLink
            subnetState.networkLink = context.vpcs
                    .get(subnetStateWithParentVpcId.parentVpcId).documentSelfLink;

            final Operation subnetStateOp;
            if (context.localSubnetStateMap.containsKey(remoteSubnetId)) {
                // If the local subnet state already exists for the Subnet -> Update it.
                String localSubnetStateSelfLink = context.localSubnetStateMap
                        .get(remoteSubnetId);

                subnetStateOp = createPatchOperation(this,
                        subnetState, localSubnetStateSelfLink);
            } else {
                subnetStateOp = createPostOperation(this,
                        subnetState, SubnetService.FACTORY_LINK);
            }
            subnetOperations.add(subnetStateOp);
        }

        OperationJoin.JoinedCompletionHandler joinCompletion = (ops, exc) -> {
            if (exc != null) {
                logSevere("Error creating/updating a Subnet state %s", Utils.toString(exc));
                finishWithFailure(context, exc.values().iterator().next());
                return;
            }
            logInfo("Successfully created/updated all the subnet states.");
            ops.values().forEach(op -> {
                SubnetState subnetState = op.getBody(SubnetState.class);
                context.subnets.get(subnetState.id).subnetState = subnetState;
            });
            handleNetworkStateChanges(context, next);
        };

        OperationJoin.create(subnetOperations)
                .setCompletion(joinCompletion)
                .sendWith(this);
    }

    /**
     * Release the {@link Operation} (that triggered this network enumeration) and sends failure
     * patch to enumeration task.
     */
    private void finishWithFailure(AWSNetworkServiceCreationContext context, Throwable exc) {

        context.awsAdapterOperation.fail(exc);

        AdapterUtils.sendFailurePatchToEnumerationTask(
                this, context.networkRequest.enumerationRequest.taskReference, exc);
    }

    /**
     * A specialization of {@link AWSAsyncHandler} which upon AWS async call completion either
     * transitions to next state in the state machine (as defined by
     * {@link AWSNetworkStateCreationAdapterService#handleNetworkStateChanges(AWSNetworkServiceCreationContext)})
     * or finishes with failure (as defined by
     * {@link AWSNetworkStateCreationAdapterService#finishWithFailure(AWSNetworkServiceCreationContext, Throwable)}).
     */
    private abstract class TransitionToAsyncHandler<REQ extends AmazonWebServiceRequest, RES>
            extends AWSAsyncHandler<REQ, RES> {

        final AWSNetworkCreationStage next;
        final AWSNetworkServiceCreationContext context;

        TransitionToAsyncHandler(
                AWSNetworkCreationStage next,
                AWSNetworkServiceCreationContext context) {

            this.next = next;
            this.context = context;
        }

        abstract void consumeSuccess(REQ request, RES result);

        @Override
        protected final void handleError(Exception exception) {
            finishWithFailure(this.context, exception);
        }

        @Override
        protected final void handleSuccess(REQ request, RES result) {
            consumeSuccess(request, result);
            transitionTo();
        }

        private void transitionTo() {
            handleNetworkStateChanges(this.context, this.next);
        }
    }

}
