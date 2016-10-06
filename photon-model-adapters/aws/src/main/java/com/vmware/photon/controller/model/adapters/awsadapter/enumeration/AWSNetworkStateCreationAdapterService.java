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
import static com.vmware.photon.controller.model.adapters.util.AdapterUtils.createPatchOperation;
import static com.vmware.photon.controller.model.adapters.util.AdapterUtils.createPostOperation;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.handlers.AsyncHandler;
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
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSNetworkUtils;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
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
        GET_SUBNETS,
        GET_INTERNET_GATEWAY,
        GET_MAIN_ROUTE_TABLE,
        CREATE_NETWORKSTATE,
        SIGNAL_COMPLETION
    }

    public AWSNetworkStateCreationAdapterService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        this.clientManager = AWSClientManagerFactory
                .getClientManager(AWSConstants.AwsClientType.EC2);
    }

    /**
     * Data holder for information related a network state that needs to be enumerated.
     *
     */
    public static class AWSNetworkEnumeration {
        public ComputeEnumerateResourceRequest enumerationRequest;
        public AuthCredentialsService.AuthCredentialsServiceState parentAuth;
        public String regionId;
        public List<String> tenantLinks;
    }

    public static class NetworkEnumerationResponse {
        public Map<String, String> vpcs;
    }

    /**
     * The service context that is created for representing the list of instances received into a
     * list of compute states that will be persisted in the system.
     *
     */
    public static class AWSNetworkServiceCreationContext {
        public AmazonEC2AsyncClient amazonEC2Client;
        public AWSNetworkEnumeration networkRequest;
        public AWSNetworkCreationStage networkCreationStage;

        // Map AWS VPC id to network state link for the discovered VPCs
        public Map<String, NetworkState> vpcs = new HashMap<>();
        // Map for local network states. The key is the vpc-id.
        public Map<String, NetworkState> localNetworkStateMap;
        // Cached operation to signal completion to the AWS instance adapter once all the compute
        // states are successfully created.
        public Operation awsAdapterOperation;

        public AWSNetworkServiceCreationContext(AWSNetworkEnumeration networkRequest,
                Operation op) {
            this.networkRequest = networkRequest;
            this.localNetworkStateMap = new HashMap<String, NetworkState>();
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
            getLocalNetworkStates(context, AWSNetworkCreationStage.GET_SUBNETS);
            break;
        case GET_SUBNETS:
            getSubnetInformation(context, AWSNetworkCreationStage.GET_INTERNET_GATEWAY);
            break;
        case GET_INTERNET_GATEWAY:
            getInternetGatewayInformation(context, AWSNetworkCreationStage.GET_MAIN_ROUTE_TABLE);
            break;
        case GET_MAIN_ROUTE_TABLE:
            getMainRouteTableInformation(context, AWSNetworkCreationStage.CREATE_NETWORKSTATE);
            break;
        case CREATE_NETWORKSTATE:
            createNetworkStateOperations(context, AWSNetworkCreationStage.SIGNAL_COMPLETION);
            break;
        case SIGNAL_COMPLETION:
            setOperationDurationStat(context.awsAdapterOperation);
            context.awsAdapterOperation.setBody(createResponse(context));
            context.awsAdapterOperation.complete();
            break;
        default:
            Throwable t = new IllegalArgumentException(
                    "Unknown AWS enumeration:network state creation stage");
            AdapterUtils.sendFailurePatchToEnumerationTask(this,
                    context.networkRequest.enumerationRequest.taskReference, t);
            break;
        }
    }

    private Object createResponse(AWSNetworkServiceCreationContext context) {
        NetworkEnumerationResponse response = new NetworkEnumerationResponse();
        response.vpcs = new HashMap<>();
        if (context.vpcs != null) {
            context.vpcs.forEach((k, v) -> response.vpcs.put(k, v.documentSelfLink));
        }
        return response;
    }

    private void refreshVPCInformation(AWSNetworkServiceCreationContext aws,
            AWSNetworkCreationStage next) {
        DescribeVpcsRequest vpcRequest = new DescribeVpcsRequest();
        AWSVPCAsyncHandler asyncHandler = new AWSVPCAsyncHandler(this, aws, next);
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
        context.networkCreationStage = next;
        handleNetworkStateChanges(context);
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
                .setConnectionSharing(true)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere("Failure retrieving query results: %s",
                                e.toString());
                        AdapterUtils.sendFailurePatchToEnumerationTask(this,
                                context.networkRequest.enumerationRequest.taskReference, e);
                        return;
                    }
                    QueryTask responseTask = o.getBody(QueryTask.class);
                    for (Object s : responseTask.results.documents.values()) {
                        NetworkState networkState = Utils.fromJson(s,
                                NetworkState.class);
                        context.localNetworkStateMap.put(networkState.id,
                                networkState);
                    }
                    logInfo("Result of query to get local networks. There are %d network states known to the system.",
                            responseTask.results.documentCount);
                    context.networkCreationStage = next;
                    handleNetworkStateChanges(context);
                    return;
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
        AWSSubnetAsyncHandler asyncHandler = new AWSSubnetAsyncHandler(this, next, context);
        context.amazonEC2Client.describeSubnetsAsync(subnetRequest, asyncHandler);
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
        AWSInternetGatewayAsyncHandler asyncHandler = new AWSInternetGatewayAsyncHandler(this, next,
                context);
        context.amazonEC2Client.describeInternetGatewaysAsync(internetGatewayRequest, asyncHandler);
    }

    /**
     * The async handler to handle the success and errors received after invoking the describe VPCs
     * API on AWS
     */
    private static class AWSVPCAsyncHandler
            implements AsyncHandler<DescribeVpcsRequest, DescribeVpcsResult> {

        private AWSNetworkStateCreationAdapterService service;
        private AWSNetworkServiceCreationContext aws;
        private AWSNetworkCreationStage next;
        private OperationContext opContext;

        private AWSVPCAsyncHandler(AWSNetworkStateCreationAdapterService service,
                AWSNetworkServiceCreationContext aws, AWSNetworkCreationStage next) {
            this.service = service;
            this.aws = aws;
            this.next = next;
            this.opContext = OperationContext.getOperationContext();
        }

        @Override
        public void onError(Exception exception) {
            OperationContext.restoreOperationContext(this.opContext);
            AdapterUtils.sendFailurePatchToEnumerationTask(this.service,
                    this.aws.networkRequest.enumerationRequest.taskReference,
                    exception);

        }

        @Override
        public void onSuccess(DescribeVpcsRequest request, DescribeVpcsResult result) {
            OperationContext.restoreOperationContext(this.opContext);

            URI adapterUri = UriUtils.buildUri(
                    ServiceHost.LOCAL_HOST,
                    this.service.getHost().getPort(),
                    AWSUriPaths.AWS_INSTANCE_ADAPTER, null);
            for (Vpc resultVPC : result.getVpcs()) {
                NetworkState networkState = AWSNetworkUtils.mapVPCToNetworkState(resultVPC,
                        this.aws.networkRequest.regionId,
                        this.aws.networkRequest.enumerationRequest.resourcePoolLink,
                        this.aws.networkRequest.parentAuth.documentSelfLink,
                        this.aws.networkRequest.tenantLinks,
                        adapterUri);
                if (networkState.subnetCIDR == null) {
                    this.service.logWarning("AWS did not return CIDR information for VPC %s",
                            resultVPC.toString());
                }
                this.aws.vpcs.put(resultVPC.getVpcId(), networkState);
            }
            this.aws.networkCreationStage = this.next;
            this.service.handleNetworkStateChanges(this.aws);
        }
    }

    /**
     * The async handler to handle the success and errors received after invoking the describe
     * Internet Gateways API on AWS
     */
    public static class AWSInternetGatewayAsyncHandler
            implements
            AsyncHandler<DescribeInternetGatewaysRequest, DescribeInternetGatewaysResult> {

        private AWSNetworkStateCreationAdapterService service;
        private AWSNetworkServiceCreationContext aws;
        private AWSNetworkCreationStage next;
        private OperationContext opContext;

        private AWSInternetGatewayAsyncHandler(AWSNetworkStateCreationAdapterService service,
                AWSNetworkCreationStage next,
                AWSNetworkServiceCreationContext aws) {
            this.service = service;
            this.aws = aws;
            this.next = next;
            this.opContext = OperationContext.getOperationContext();
        }

        @Override
        public void onError(Exception exception) {
            OperationContext.restoreOperationContext(this.opContext);
            AdapterUtils.sendFailurePatchToEnumerationTask(this.service,
                    this.aws.networkRequest.enumerationRequest.taskReference,
                    exception);

        }

        /**
         * Update the Internet gateway information for the VPC in question. For the list of Internet
         * gateways received based on the vpc filter work through the list of attachments and VPCs
         * and update the Internet gateway information in the network state that maps to the VPC.
         */
        @Override
        public void onSuccess(DescribeInternetGatewaysRequest request,
                DescribeInternetGatewaysResult result) {
            OperationContext.restoreOperationContext(this.opContext);
            for (InternetGateway resultGateway : result.getInternetGateways()) {
                for (InternetGatewayAttachment attachment : resultGateway.getAttachments()) {
                    if (this.aws.vpcs.containsKey(attachment.getVpcId())) {
                        NetworkState networkStateToUpdate = this.aws.vpcs
                                .get(attachment.getVpcId());
                        networkStateToUpdate.customProperties.put(AWS_GATEWAY_ID,
                                resultGateway.getInternetGatewayId());
                        this.aws.vpcs.put(attachment.getVpcId(), networkStateToUpdate);
                    }
                }
            }
            this.aws.networkCreationStage = this.next;
            this.service.handleNetworkStateChanges(this.aws);
        }
    }

    /**
     * The async handler to handle the success and errors received after invoking the describe
     * Internet Gateways API on AWS
     */
    public static class AWSSubnetAsyncHandler
            implements
            AsyncHandler<DescribeSubnetsRequest, DescribeSubnetsResult> {

        private AWSNetworkStateCreationAdapterService service;
        private AWSNetworkServiceCreationContext aws;
        private AWSNetworkCreationStage next;
        private OperationContext opContext;

        private AWSSubnetAsyncHandler(AWSNetworkStateCreationAdapterService service,
                AWSNetworkCreationStage next,
                AWSNetworkServiceCreationContext aws) {
            this.service = service;
            this.aws = aws;
            this.next = next;
            this.opContext = OperationContext.getOperationContext();
        }

        @Override
        public void onError(Exception exception) {
            OperationContext.restoreOperationContext(this.opContext);
            AdapterUtils.sendFailurePatchToEnumerationTask(this.service,
                    this.aws.networkRequest.enumerationRequest.taskReference,
                    exception);

        }

        /**
         * Update the Subnet information for the VPC in question.
         */
        @Override
        public void onSuccess(DescribeSubnetsRequest request,
                DescribeSubnetsResult result) {
            OperationContext.restoreOperationContext(this.opContext);
            for (Subnet subnet : result.getSubnets()) {
                if (this.aws.vpcs.containsKey(subnet.getVpcId())) {
                    NetworkState networkStateToUpdate = this.aws.vpcs
                            .get(subnet.getVpcId());
                    networkStateToUpdate.customProperties.put(AWS_SUBNET_ID,
                            subnet.getSubnetId());
                    this.aws.vpcs.put(subnet.getVpcId(), networkStateToUpdate);
                }
            }
            this.aws.networkCreationStage = this.next;
            this.service.handleNetworkStateChanges(this.aws);
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

        AWSMainRouteTableAsyncHandler asyncHandler = new AWSMainRouteTableAsyncHandler(this, next,
                context);
        context.amazonEC2Client.describeRouteTablesAsync(routeTablesRequest, asyncHandler);
    }

    /**
     * The async handler to handle the success and errors received after invoking the describe Route
     * Tables API on AWS
     */
    public static class AWSMainRouteTableAsyncHandler
            implements
            AsyncHandler<DescribeRouteTablesRequest, DescribeRouteTablesResult> {

        private AWSNetworkStateCreationAdapterService service;
        private AWSNetworkServiceCreationContext aws;
        private AWSNetworkCreationStage next;
        private OperationContext opContext;

        private AWSMainRouteTableAsyncHandler(AWSNetworkStateCreationAdapterService service,
                AWSNetworkCreationStage next,
                AWSNetworkServiceCreationContext aws) {
            this.service = service;
            this.aws = aws;
            this.next = next;
            this.opContext = OperationContext.getOperationContext();
        }

        @Override
        public void onError(Exception exception) {
            OperationContext.restoreOperationContext(this.opContext);
            AdapterUtils.sendFailurePatchToEnumerationTask(this.service,
                    this.aws.networkRequest.enumerationRequest.taskReference,
                    exception);

        }

        /**
         * Update the main route table information for the VPC that is being mapped to a network
         * state. Query AWS for the main route tables with a list of VPCs. From the result set find
         * the relevant route table Id and upda
         */
        @Override
        public void onSuccess(DescribeRouteTablesRequest request,
                DescribeRouteTablesResult result) {
            OperationContext.restoreOperationContext(this.opContext);
            for (RouteTable routeTable : result.getRouteTables()) {
                if (this.aws.vpcs.containsKey(routeTable.getVpcId())) {
                    NetworkState networkStateToUpdate = this.aws.vpcs
                            .get(routeTable.getVpcId());
                    networkStateToUpdate.customProperties.put(AWS_VPC_ROUTE_TABLE_ID,
                            routeTable.getRouteTableId());
                    this.aws.vpcs.put(routeTable.getVpcId(),
                            networkStateToUpdate);
                }
            }
            this.aws.networkCreationStage = this.next;
            this.service.handleNetworkStateChanges(this.aws);
        }
    }

    /**
     * Create the network state operations for all the VPCs that need to be created or updated in
     * the system.
     */
    private void createNetworkStateOperations(AWSNetworkServiceCreationContext context,
            AWSNetworkCreationStage next) {
        if (context.vpcs == null
                || context.vpcs.size() == 0) {
            logInfo("No new VPCs have been discovered.Nothing to do.");
            context.networkCreationStage = next;
            handleNetworkStateChanges(context);
        } else {
            List<Operation> networkOperations = new ArrayList<>();
            for (String remoteVPCId : context.vpcs.keySet()) {
                NetworkState networkState = context.vpcs.get(remoteVPCId);
                Operation networkStateOperation = null;
                // If the local network state already exists for the VPC. Update it.
                if (context.localNetworkStateMap.containsKey(remoteVPCId)) {
                    networkState.documentSelfLink = context.localNetworkStateMap
                            .get(remoteVPCId).documentSelfLink;
                    networkStateOperation = createPatchOperation(this,
                            networkState, networkState.documentSelfLink);
                    networkOperations.add(networkStateOperation);
                    continue;
                }
                networkStateOperation = createPostOperation(this,
                        networkState, NetworkService.FACTORY_LINK);
                networkOperations.add(networkStateOperation);
            }

            if (networkOperations.isEmpty()) {
                logInfo("No networks(VPC) have found.");
                context.networkCreationStage = next;
                handleNetworkStateChanges(context);
                return;
            }

            OperationJoin.JoinedCompletionHandler joinCompletion = (ops,
                    exc) -> {
                if (exc != null) {
                    logSevere("Error creating a Network state %s", Utils.toString(exc));
                    AdapterUtils.sendFailurePatchToEnumerationTask(this,
                            context.networkRequest.enumerationRequest.taskReference,
                            exc.values().iterator().next());
                    return;

                }
                logInfo("Successfully created all the networks and compute states.");
                ops.values().forEach(op -> {
                    NetworkState body = op.getBody(NetworkState.class);
                    context.vpcs.put(body.id, body);
                });
                context.networkCreationStage = next;
                handleNetworkStateChanges(context);
            };
            OperationJoin joinOp = OperationJoin.create(networkOperations);
            joinOp.setCompletion(joinCompletion);
            joinOp.sendWith(this);
        }
    }

}
