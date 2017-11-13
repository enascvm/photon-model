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

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWSResourceType.ec2_subnet;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWSResourceType.ec2_vpc;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_ATTACHMENT_VPC_FILTER;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_GATEWAY_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_MAIN_ROUTE_ASSOCIATION;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_VPC_ID_FILTER;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_VPC_ROUTE_TABLE_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.AWS_FILTER_VPC_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSNetworkUtils.createQueryToGetExistingNetworkStatesFilteredByDiscoveredVPCs;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSNetworkUtils.createQueryToGetExistingSubnetStatesFilteredByDiscoveredSubnets;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSNetworkUtils.mapSubnetToSubnetState;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSNetworkUtils.mapVPCToNetworkState;
import static com.vmware.photon.controller.model.adapters.util.AdapterUtils.createPatchOperation;
import static com.vmware.photon.controller.model.adapters.util.AdapterUtils.createPostOperation;
import static com.vmware.photon.controller.model.adapters.util.AdapterUtils.getDeletionState;
import static com.vmware.photon.controller.model.adapters.util.TagsUtil.newTagState;
import static com.vmware.photon.controller.model.adapters.util.TagsUtil.setTagLinksToResourceState;
import static com.vmware.photon.controller.model.adapters.util.TagsUtil.updateLocalTagStates;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.TAG_KEY_TYPE;
import static com.vmware.xenon.services.common.QueryTask.NumericRange.createLessThanRange;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.stream.Collectors;

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
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.Vpc;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUriPaths;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSNetworkStateEnumerationAdapterService.AWSNetworkStateCreationContext.SubnetStateWithParentVpcId;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSAsyncHandler;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.query.QueryUtils.QueryByPages;
import com.vmware.photon.controller.model.resources.ComputeService.LifecycleState;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.photon.controller.model.util.ClusterUtil.ServiceTypeCluster;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.OperationJoin.JoinedCompletionHandler;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;

/**
 * Stateless service for the creation of compute states. It accepts a list of AWS instances that
 * need to be created in the local system.It also accepts a few additional fields required for
 * mapping the referential integrity relationships for the compute state when it is persisted in the
 * local system.
 */
public class AWSNetworkStateEnumerationAdapterService extends StatelessService {

    public static final String SELF_LINK = AWSUriPaths.AWS_NETWORK_STATE_CREATION_ADAPTER;
    private static final int MAX_RESOURCES_TO_QUERY_ON_DELETE = Integer
            .getInteger(UriPaths.PROPERTY_PREFIX
                    + "enum.max.resources.query.on.delete", 950);
    public static final List<String> internalTagList = Arrays.asList(ec2_vpc.toString(),
            ec2_subnet.toString());

    private AWSClientManager clientManager;

    /**
     * Request accepted by this service to trigger enumeration of Network entities in Amazon.
     *
     * @see AWSNetworkEnumerationResponse
     */
    public static class AWSNetworkEnumerationRequest {
        public ComputeEnumerateResourceRequest request;
        public AuthCredentialsService.AuthCredentialsServiceState parentAuth;
        public String regionId;
        public List<String> tenantLinks;
        public String parentComputeLink;
    }

    /**
     * Response returned by this service as a result of enumerating Network entities in Amazon.
     *
     * @see AWSNetworkEnumerationRequest
     */
    public static class AWSNetworkEnumerationResponse {
        /**
         * Map discovered AWS VPC {@link Vpc#getVpcId() id} to network state
         * {@code documentSelfLink}.
         */
        public Map<String, String> vpcs = new HashMap<>();
        /**
         * Map discovered AWS Subnet {@link Subnet#getSubnetId() id} to subnet state
         * {@code documentSelfLink}.
         */
        public Map<String, String> subnets = new HashMap<>();
    }

    /**
     * The service context that is created for representing the list of instances received into a
     * list of compute states that will be persisted in the system.
     */
    static class AWSNetworkStateCreationContext {

        /**
         * The time when this enumeration started. It is used to identify stale resources that should be
         * deleted during deletion stage.
         */
        protected long enumStartTimeInMicros;

        // Cached operation to signal completion to the AWS instance adapter once all the compute
        // states are successfully created.
        public final Operation operation;

        public AmazonEC2AsyncClient amazonEC2Client;
        public AWSNetworkEnumerationRequest request;
        public AWSNetworkStateCreationStage networkCreationStage;

        // Map AWS VPC id to AWS VPC object
        public Map<String, Vpc> awsVpcs = new HashMap<>();
        // Map AWS VPC id to network state for discovered VPCs
        public Map<String, NetworkState> vpcs = new HashMap<>();
        // Map for local network states. key = vpc-id, value = NetworkState.documentSelfLink
        public Map<String, NetworkState> localNetworkStateMap = new HashMap<>();

        // Map AWS Subnet id to AWS Subnet object
        public Map<String, Subnet> awsSubnets = new HashMap<>();
        // Map AWS Subnet id to subnet state for discovered Subnets
        public Map<String, SubnetStateWithParentVpcId> subnets = new HashMap<>();
        // Map for local subnet states. key = subnet-id, value = SubnetState.documentSelfLink
        public Map<String, SubnetState> localSubnetStateMap = new HashMap<>();
        // stores a mapping of internal tag values for networks.
        Map<String, String> networkInternalTagsMap = new ConcurrentHashMap<>();
        // stores documentSelfLink for networks internal tags
        Set<String> networkInternalTagLinksSet = new HashSet<>();

        // stores a mapping of internal tag values for subnets.
        Map<String, String> subnetInternalTagsMap = new ConcurrentHashMap<>();
        // stores documentSelfLink for subnets internal tags
        Set<String> subnetInternalTagLinksSet = new HashSet<>();
        List<Tag> createdExternalTags = new ArrayList<>();
        public ResourceState resourceDeletionState;

        static class SubnetStateWithParentVpcId {
            String parentVpcId;
            SubnetState subnetState;

            SubnetStateWithParentVpcId(String parentVpcId, SubnetState subnetState) {
                this.parentVpcId = parentVpcId;
                this.subnetState = subnetState;
            }
        }

        public AWSNetworkStateCreationContext(AWSNetworkEnumerationRequest request,
                Operation op) {
            this.request = request;
            this.networkCreationStage = AWSNetworkStateCreationStage.CLIENT;
            this.operation = op;
            this.resourceDeletionState = getDeletionState(request.request
                    .deletedResourceExpirationMicros);
        }
    }

    enum AWSNetworkStateCreationStage {
        CLIENT,
        CREATE_INTERNAL_TYPE_TAGS,
        GET_REMOTE_VPC,
        GET_LOCAL_NETWORK_STATES,
        GET_REMOTE_SUBNETS,
        GET_LOCAL_SUBNET_STATES,
        GET_INTERNET_GATEWAY,
        GET_MAIN_ROUTE_TABLE,
        CREATE_TAG_STATES,
        UPDATE_TAG_LINKS,
        CREATE_NETWORKSTATE,
        CREATE_SUBNETSTATE,
        DELETE_STALE_RESOURCE_STATES,
        SIGNAL_COMPLETION
    }

    public AWSNetworkStateEnumerationAdapterService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
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
    public void handlePatch(Operation op) {
        setOperationHandlerInvokeTimeStat(op);
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        AWSNetworkEnumerationRequest cs = op.getBody(AWSNetworkEnumerationRequest.class);
        AWSNetworkStateCreationContext context = new AWSNetworkStateCreationContext(cs, op);
        if (cs.request.isMockRequest) {
            op.complete();
        }
        try {
            handleNetworkStateChanges(context);
        } catch (Exception e) {
            op.fail(e);
        }
    }

    /**
     * Handles the process to create and EC2 Async client and get all the VPC related information
     * from AWS. At the very least it gets the CIDR block information for the VPC, the connected
     * internet gateway (if any) and the main route table information for the VPC.
     */
    private void handleNetworkStateChanges(AWSNetworkStateCreationContext context) {
        switch (context.networkCreationStage) {
        case CLIENT:
            context.enumStartTimeInMicros = Utils.getNowMicrosUtc();
            getAWSAsyncClient(context, AWSNetworkStateCreationStage.CREATE_INTERNAL_TYPE_TAGS);
            break;
        case CREATE_INTERNAL_TYPE_TAGS:
            createInternalTypeTags(context, AWSNetworkStateCreationStage.GET_REMOTE_VPC);
            break;
        case GET_REMOTE_VPC:
            refreshVPCInformation(context, AWSNetworkStateCreationStage.GET_LOCAL_NETWORK_STATES);
            break;
        case GET_LOCAL_NETWORK_STATES:
            getLocalNetworkStates(context, AWSNetworkStateCreationStage.GET_REMOTE_SUBNETS);
            break;
        case GET_REMOTE_SUBNETS:
            getSubnetInformation(context, AWSNetworkStateCreationStage.GET_LOCAL_SUBNET_STATES);
            break;
        case GET_LOCAL_SUBNET_STATES:
            getLocalSubnetStates(context, AWSNetworkStateCreationStage.GET_INTERNET_GATEWAY);
            break;
        case GET_INTERNET_GATEWAY:
            getInternetGatewayInformation(context,
                    AWSNetworkStateCreationStage.GET_MAIN_ROUTE_TABLE);
            break;
        case GET_MAIN_ROUTE_TABLE:
            getMainRouteTableInformation(context, AWSNetworkStateCreationStage.CREATE_TAG_STATES);
            break;
        case CREATE_TAG_STATES:
            createTags(context, AWSNetworkStateCreationStage.CREATE_NETWORKSTATE);
            break;
        case CREATE_NETWORKSTATE:
            createNetworkStateOperations(context, AWSNetworkStateCreationStage.CREATE_SUBNETSTATE);
            break;
        case CREATE_SUBNETSTATE:
            createSubnetStateOperations(context, AWSNetworkStateCreationStage.UPDATE_TAG_LINKS);
            break;
        case UPDATE_TAG_LINKS:
            updateTagLinks(context).whenComplete(thenNetworkStateChanges(context,
                    AWSNetworkStateCreationStage.DELETE_STALE_RESOURCE_STATES));
            break;
        case DELETE_STALE_RESOURCE_STATES:
            DeferredResult.completed(context)
                .thenCompose(this::deleteStaleSubnetStates)
                .thenCompose(this::deleteStaleNetworkStates)
                .whenComplete(thenNetworkStateChanges(context, AWSNetworkStateCreationStage.SIGNAL_COMPLETION));
            break;
        case SIGNAL_COMPLETION:
            setOperationDurationStat(context.operation);
            context.operation.setBody(createResponse(context));
            context.operation.complete();
            break;
        default:
            Throwable t = new IllegalArgumentException(
                    "Unknown AWS enumeration:network state creation stage");
            finishWithFailure(context, t);
            break;
        }
    }

    /**
     * {@code handleNetworkStateChanges} version suitable for chaining to
     * {@code DeferredResult.whenComplete}.
     */
    private BiConsumer<AWSNetworkStateCreationContext, Throwable> thenNetworkStateChanges(
            AWSNetworkStateCreationContext context,
            AWSNetworkStateCreationStage next) {
        // NOTE: In case of error 'ignoreCtx' is null so use passed context!
        return (ignoreCtx, exc) -> {
            if (exc != null) {
                this.logWarning("Failure updating tagLinks for networks and subnets: %s",
                        exc.getMessage());
            }
            handleNetworkStateChanges(context, next);
        };
    }

    /**
     * Shortcut method that sets the next stage into the context and delegates to
     * {@link #handleNetworkStateChanges(AWSNetworkStateCreationContext)}.
     */
    private void handleNetworkStateChanges(
            AWSNetworkStateCreationContext context,
            AWSNetworkStateCreationStage next) {
        try {
            context.networkCreationStage = next;
            handleNetworkStateChanges(context);
        } catch (Exception e) {
            context.operation.fail(e);
        }
    }

    private Object createResponse(AWSNetworkStateCreationContext context) {

        AWSNetworkEnumerationResponse response = new AWSNetworkEnumerationResponse();

        context.vpcs.forEach((k, v) -> response.vpcs.put(k, v.documentSelfLink));

        context.subnets.forEach((k, v) -> response.subnets.put(k, v.subnetState.documentSelfLink));

        return response;
    }

    private void refreshVPCInformation(AWSNetworkStateCreationContext aws,
            AWSNetworkStateCreationStage next) {
        DescribeVpcsRequest vpcRequest = new DescribeVpcsRequest();
        AWSVPCAsyncHandler asyncHandler = new AWSVPCAsyncHandler(next, aws);
        aws.amazonEC2Client.describeVpcsAsync(vpcRequest, asyncHandler);
    }

    /**
     * Method to instantiate the AWS Async client for future use
     */
    private void getAWSAsyncClient(AWSNetworkStateCreationContext context,
            AWSNetworkStateCreationStage next) {
        context.amazonEC2Client = this.clientManager.getOrCreateEC2Client(
                context.request.parentAuth, context.request.regionId,
                this, (t) -> context.operation.fail(t));
        if (context.amazonEC2Client != null) {
            handleNetworkStateChanges(context, next);
        }
    }

    /**
     * Method to create the internal tags for subnets and VPCs.
     */
    private void createInternalTypeTags(AWSNetworkStateCreationContext context,
            AWSNetworkStateCreationStage next) {
        // Go over the list of internal tags to be created. Find whatever already does not have an
        // associated tag state and create an operation for its creation.

        List<Operation> joinOperations = new ArrayList<>();
        for (String resourceType : internalTagList) {
            TagState typeTag = newTagState(TAG_KEY_TYPE, resourceType, false,
                    context.request.tenantLinks);
            Operation op = Operation.createPost(this, TagService.FACTORY_LINK)
                    .setBody(typeTag);
            joinOperations.add(op);

        }

        OperationJoin.create(joinOperations)
                .setCompletion((ops, exs) -> {
                    if (exs != null) {
                        exs.values()
                                .forEach(ex -> logWarning(
                                        () -> String.format("Error creating internal tag : %s",
                                                ex.getMessage())));
                        context.networkCreationStage = next;
                        handleNetworkStateChanges(context);
                        return;
                    }
                    for (String internalTagValue : internalTagList) {
                        TagState tagState = newTagState(TAG_KEY_TYPE, internalTagValue, false,
                                context.request.tenantLinks);
                        if (internalTagValue.equalsIgnoreCase(ec2_vpc.toString())) {
                            context.networkInternalTagsMap
                                    .put(PhotonModelConstants.TAG_KEY_TYPE, tagState.value);
                            context.networkInternalTagLinksSet.add(tagState.documentSelfLink);
                        } else {
                            context.subnetInternalTagsMap
                                    .put(PhotonModelConstants.TAG_KEY_TYPE, tagState.value);
                            context.subnetInternalTagLinksSet.add(tagState.documentSelfLink);
                        }
                    }
                    context.networkCreationStage = next;
                    handleNetworkStateChanges(context);
                }).sendWith(this);
    }

    /**
     * Gets the VPC information from the local database to perform updates to existing network
     * states.
     */
    private void getLocalNetworkStates(AWSNetworkStateCreationContext context,
            AWSNetworkStateCreationStage next) {

        if (context.vpcs.isEmpty()) {
            handleNetworkStateChanges(context, next);
            return;
        }

        QueryTask queryTask = createQueryToGetExistingNetworkStatesFilteredByDiscoveredVPCs(
                context.vpcs.keySet(),
                context.request.request.endpointLink,
                context.request.regionId,
                context.request.tenantLinks);

        // create the query to find resources
        QueryUtils.startInventoryQueryTask(this, queryTask)
                .whenComplete((qrt, e) -> {
                    if (e != null) {
                        logSevere(() -> String.format("Failure retrieving query results: %s",
                                e.toString()));
                        finishWithFailure(context, e);
                        return;
                    }

                    if (qrt.results.documents != null) {
                        for (Object s : qrt.results.documents.values()) {
                            NetworkState networkState = Utils.fromJson(s, NetworkState.class);
                            context.localNetworkStateMap
                                    .put(networkState.id, networkState);
                        }
                    }

                    logFine(() -> String.format("%d network states found.",
                            qrt.results.documentCount));
                    handleNetworkStateChanges(context, next);
                });
    }

    /**
     * Gets the Subnets that are attached to the VPCs that were discovered during the enumeration
     * process.
     */
    private void getSubnetInformation(AWSNetworkStateCreationContext context,
            AWSNetworkStateCreationStage next) {
        DescribeSubnetsRequest subnetRequest = new DescribeSubnetsRequest();
        List<String> vpcList = new ArrayList<>(context.vpcs.keySet());
        Filter filter = new Filter(AWS_VPC_ID_FILTER, vpcList);
        subnetRequest.getFilters().add(filter);
        AWSSubnetAsyncHandler asyncHandler = new AWSSubnetAsyncHandler(next, context);
        context.amazonEC2Client.describeSubnetsAsync(subnetRequest, asyncHandler);
    }

    /**
     * Gets the Subnet information from the local database to perform updates to existing subnet
     * states.
     */
    private void getLocalSubnetStates(AWSNetworkStateCreationContext context,
            AWSNetworkStateCreationStage next) {

        if (context.subnets.isEmpty()) {
            handleNetworkStateChanges(context, next);
            return;
        }

        QueryTask q = createQueryToGetExistingSubnetStatesFilteredByDiscoveredSubnets(
                context.subnets.keySet(),
                context.request.request.endpointLink,
                context.request.regionId,
                context.request.tenantLinks);

        // create the query to find resources
        QueryUtils.startInventoryQueryTask(this, q)
                .whenComplete((queryTask, e) -> {
                    if (e != null) {
                        logSevere(() -> String.format("Failed retrieving query results: %s",
                                e.toString()));
                        finishWithFailure(context, e);
                        return;
                    }
                    if (queryTask.results.documents != null) {
                        for (Object s : queryTask.results.documents.values()) {
                            SubnetState subnetState = Utils.fromJson(s, SubnetState.class);
                            context.localSubnetStateMap.put(subnetState.id,
                                    subnetState);
                        }
                    }
                    logFine(() -> String.format("%d subnet states found.",
                            queryTask.results.documentCount));

                    handleNetworkStateChanges(context, next);
                });
    }

    /**
     * Gets the Internet gateways that are attached to the VPCs that were discovered during the
     * enumeration process.
     */
    private void getInternetGatewayInformation(AWSNetworkStateCreationContext context,
            AWSNetworkStateCreationStage next) {
        DescribeInternetGatewaysRequest internetGatewayRequest = new DescribeInternetGatewaysRequest();
        List<String> vpcList = new ArrayList<>(context.vpcs.keySet());
        Filter filter = new Filter(AWS_ATTACHMENT_VPC_FILTER, vpcList);
        internetGatewayRequest.getFilters().add(filter);
        AWSInternetGatewayAsyncHandler asyncHandler = new AWSInternetGatewayAsyncHandler(next,
                context);
        context.amazonEC2Client.describeInternetGatewaysAsync(internetGatewayRequest, asyncHandler);
    }

    /**
     * Gets the main route table information associated with a VPC that is being mapped to a network
     * state in the system. *
     */
    private void getMainRouteTableInformation(AWSNetworkStateCreationContext context,
            AWSNetworkStateCreationStage next) {
        DescribeRouteTablesRequest routeTablesRequest = new DescribeRouteTablesRequest();
        List<String> vpcList = new ArrayList<>(context.vpcs.keySet());

        // build filter list
        List<Filter> filters = new ArrayList<>();
        filters.add(new Filter(AWS_FILTER_VPC_ID, vpcList));
        filters.add(AWSUtils.getFilter(AWS_MAIN_ROUTE_ASSOCIATION, "true"));

        AWSMainRouteTableAsyncHandler asyncHandler = new AWSMainRouteTableAsyncHandler(next,
                context);
        context.amazonEC2Client.describeRouteTablesAsync(routeTablesRequest, asyncHandler);
    }

    /**
     * Gets the Networks and Subnets tags information and creates TagState for each tag
     */
    private void createTags(
            AWSNetworkStateCreationContext context,
            AWSNetworkStateCreationStage next) {
        // Collect all tags in a List
        List<Tag> allNetworkAndSubnetsTags = context.awsVpcs.values().stream()
                // Create only the tags for the new vpcs
                .filter(vpc -> !context.localNetworkStateMap.containsKey(vpc.getVpcId()))
                .flatMap(vpc -> vpc.getTags().stream())
                .collect(Collectors.toList());
        allNetworkAndSubnetsTags.addAll(context.awsSubnets.values().stream()
                // Create only the tags for the new subnets
                .filter(subnet -> !context.localSubnetStateMap.containsKey(subnet.getSubnetId()))
                .flatMap(subnet -> subnet.getTags().stream())
                .collect(Collectors.toList()));

        // POST each of the tags. If a tag exists it won't be created again. We don't want the name
        // tags, so filter them out
        List<Operation> operations = new ArrayList<>();
        Map<Long, Tag> tagsCreationOperationIdsMap = new ConcurrentHashMap<>();

        allNetworkAndSubnetsTags.stream()
                .filter(t -> !AWSConstants.AWS_TAG_NAME.equals(t.getKey()))
                .forEach(t -> {
                    TagState tagState = newTagState(t.getKey(), t.getValue(), true,
                            context.request.tenantLinks);
                    Operation createTagOp = Operation.createPost(this, TagService.FACTORY_LINK)
                            .setBody(tagState);
                    operations.add(createTagOp);
                    tagsCreationOperationIdsMap.put(createTagOp.getId(), t);
                });

        if (operations.isEmpty()) {
            context.networkCreationStage = next;
            handleNetworkStateChanges(context);
        } else {
            OperationJoin.create(operations).setCompletion((ops, exs) -> {
                if (exs != null && !exs.isEmpty()) {
                    this.logWarning("Failure creating external tags for network and subnets: %s",
                            exs.get(0).getMessage());
                }

                ops.values().stream()
                        .filter(operation -> operation.getStatusCode() == Operation.STATUS_CODE_OK
                                || operation.getStatusCode() == Operation.STATUS_CODE_NOT_MODIFIED)
                        .forEach(operation -> {
                            if (tagsCreationOperationIdsMap.containsKey(operation.getId())) {
                                context.createdExternalTags.add(tagsCreationOperationIdsMap
                                        .get(operation.getId()));
                            }
                        });

                context.networkCreationStage = next;
                handleNetworkStateChanges(context);
            }).sendWith(this);
        }
    }

    private DeferredResult<AWSNetworkStateCreationContext> updateTagLinks(
            AWSNetworkStateCreationContext context) {
        if ((context.awsVpcs == null || context.awsVpcs.isEmpty())
                && (context.awsSubnets == null || context.awsSubnets.isEmpty())) {
            logFine(() -> "No local vpcs or subnets to be updated so there are no tags to update.");
            return DeferredResult.completed(context);
        } else {

            List<DeferredResult<Set<String>>> updateNetworkSubnetTagLinksOps = new ArrayList<>();
            // update tag links for the existing NetworkStates
            for (String vpcId : context.awsVpcs.keySet()) {
                if (!context.localNetworkStateMap.containsKey(vpcId)) {
                    continue; // this is not a network to update
                }
                Vpc vpc = context.awsVpcs.get(vpcId);
                NetworkState existingNetworkState = context.localNetworkStateMap.get(vpcId);
                Map<String, String> remoteTags = new HashMap<>();
                for (Tag awsVpcTag : vpc.getTags()) {
                    if (!awsVpcTag.getKey().equals(AWSConstants.AWS_TAG_NAME)) {
                        remoteTags.put(awsVpcTag.getKey(), awsVpcTag.getValue());
                    }
                }
                updateNetworkSubnetTagLinksOps
                        .add(updateLocalTagStates(this, existingNetworkState, remoteTags, null));
            }
            // update tag links for the existing SubnetStates
            for (String subnetId : context.awsSubnets.keySet()) {
                if (!context.localSubnetStateMap.containsKey(subnetId)) {
                    continue; // this is not a subnet to update
                }
                Subnet subnet = context.awsSubnets.get(subnetId);
                SubnetState existingSubnetState = context.localSubnetStateMap.get(subnetId);
                Map<String, String> remoteTags = new HashMap<>();
                for (Tag awsSubnetTag : subnet.getTags()) {
                    if (!awsSubnetTag.getKey().equals(AWSConstants.AWS_TAG_NAME)) {
                        remoteTags.put(awsSubnetTag.getKey(), awsSubnetTag.getValue());
                    }
                }
                updateNetworkSubnetTagLinksOps
                        .add(updateLocalTagStates(this, existingSubnetState, remoteTags, null));
            }

            return DeferredResult.allOf(updateNetworkSubnetTagLinksOps)
                    .thenApply(ignore -> context);
        }
    }

    /**
     * Create the network state operations for all the VPCs that need to be created or updated in
     * the system.
     */
    private void createNetworkStateOperations(
            AWSNetworkStateCreationContext context,
            AWSNetworkStateCreationStage next) {

        if (context.vpcs.isEmpty()) {
            logFine(() -> "No new VPCs have been discovered.");
            handleNetworkStateChanges(context, next);
            return;
        }

        final List<Operation> networkOperations = new ArrayList<>();

        for (String remoteVPCId : context.vpcs.keySet()) {

            NetworkState networkState = context.vpcs.get(remoteVPCId);

            final Operation networkStateOp;
            if (context.localNetworkStateMap.containsKey(remoteVPCId)) {
                // If the local network state already exists for the VPC -> Update it.
                networkState.documentSelfLink = context.localNetworkStateMap.get(remoteVPCId).documentSelfLink;
                // don't overwrite resourcePoolLink
                networkState.resourcePoolLink = null;
                if (networkState.tagLinks == null || networkState.tagLinks.isEmpty()) {
                    setTagLinksToResourceState(networkState, context.networkInternalTagsMap,
                            false);
                } else {
                    context.networkInternalTagLinksSet.stream()
                            .filter(tagLink -> !networkState.tagLinks.contains(tagLink))
                            .map(tagLink -> networkState.tagLinks.add(tagLink))
                            .collect(Collectors.toSet());
                }

                networkStateOp = createPatchOperation(this,
                        networkState, networkState.documentSelfLink);
            } else {
                Vpc awsVpc = context.awsVpcs.get(remoteVPCId);
                // Add both external and internal tags.
                setResourceTags(networkState, awsVpc.getTags());
                setTagLinksToResourceState(networkState, context.networkInternalTagsMap, false);

                networkStateOp = createPostOperation(this,
                        networkState, NetworkService.FACTORY_LINK);
            }
            networkOperations.add(networkStateOp);
        }

        JoinedCompletionHandler joinCompletion = (ops, excs) -> {
            if (excs != null) {
                Entry<Long, Throwable> excEntry = excs.entrySet().iterator().next();
                Throwable exc = excEntry.getValue();
                Operation op = ops.get(excEntry.getKey());
                logSevere(() -> String.format("Error %s-ing a Network state: %s", op.getAction(),
                        Utils.toString(excs)));
                finishWithFailure(context, exc);
                return;
            }
            logFine(() -> "Created/updated all network states.");
            ops.values().stream()
                    .filter(op -> op.getStatusCode() != Operation.STATUS_CODE_NOT_MODIFIED)
                    .forEach(op -> {
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
    private void createSubnetStateOperations(AWSNetworkStateCreationContext context,
            AWSNetworkStateCreationStage next) {

        if (context.subnets.isEmpty()) {
            logFine(() -> "No new subnets found.");
            handleNetworkStateChanges(context, next);
            return;
        }

        final List<Operation> subnetOperations = new ArrayList<>();

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
                subnetState.documentSelfLink = context.localSubnetStateMap
                        .get(remoteSubnetId).documentSelfLink;
                // for already existing subnets, add internal tags only if missing
                if (subnetState.tagLinks == null || subnetState.tagLinks.isEmpty()) {
                    setTagLinksToResourceState(subnetState, context.subnetInternalTagsMap, false);
                } else {
                    context.subnetInternalTagLinksSet.stream()
                            .filter(tagLink -> !subnetState.tagLinks.contains(tagLink))
                            .map(tagLink -> subnetState.tagLinks.add(tagLink))
                            .collect(Collectors.toSet());
                }
                subnetStateOp = createPatchOperation(this,
                        subnetState, subnetState.documentSelfLink);
            } else {
                // add tag links
                Subnet awsSubnet = context.awsSubnets.get(remoteSubnetId);
                setResourceTags(subnetState, awsSubnet.getTags());
                setTagLinksToResourceState(subnetState, context.subnetInternalTagsMap, false);

                subnetStateOp = createPostOperation(this,
                        subnetState, SubnetService.FACTORY_LINK);
            }
            subnetOperations.add(subnetStateOp);
        }

        JoinedCompletionHandler joinCompletion = (ops, excs) -> {
            if (excs != null) {
                Entry<Long, Throwable> excEntry = excs.entrySet().iterator().next();
                Throwable exc = excEntry.getValue();
                Operation op = ops.get(excEntry.getKey());
                logSevere(() -> String.format("Error %s-ing a Subnet state: %s", op.getAction(),
                        Utils.toString(excs)));
                finishWithFailure(context, exc);
                return;
            }
            logFine(() -> "Successfully created/updated all subnet states.");
            ops.values().stream()
                    .filter(op -> op.getStatusCode() != Operation.STATUS_CODE_NOT_MODIFIED)
                    .forEach(op -> {
                        SubnetState subnetState = op.getBody(SubnetState.class);
                        context.subnets.get(subnetState.id).subnetState = subnetState;
                    });
            handleNetworkStateChanges(context, next);
        };

        OperationJoin.create(subnetOperations)
                .setCompletion(joinCompletion)
                .sendWith(this);
    }

    private DeferredResult<AWSNetworkStateCreationContext> deleteStaleNetworkStates(AWSNetworkStateCreationContext context) {

        return deleteStaleLocalStates(context, NetworkState.class, context.vpcs.keySet());
    }

    private DeferredResult<AWSNetworkStateCreationContext> deleteStaleSubnetStates(
            AWSNetworkStateCreationContext context) {

        return deleteStaleLocalStates(context, SubnetState.class, context.awsSubnets.keySet());
    }

    private DeferredResult<AWSNetworkStateCreationContext> deleteStaleLocalStates(
            AWSNetworkStateCreationContext context,
            Class<? extends ResourceState> localStateClass,
            Set<String> remoteResourcesKeys) {

        final String msg = "Delete %ss that no longer exist in the endpoint: %s";

        logFine(
                () -> String.format(msg, localStateClass.getSimpleName(), "STARTING"));

        Query.Builder qBuilder = Query.Builder.create()
                .addKindFieldClause(localStateClass)
                .addFieldClause(
                        "lifecycleState",
                        LifecycleState.PROVISIONING.toString(),
                        Occurance.MUST_NOT_OCCUR)
                .addRangeClause(
                        ServiceDocument.FIELD_NAME_UPDATE_TIME_MICROS,
                        createLessThanRange(context.enumStartTimeInMicros));

        if (context.request.regionId != null) {
            // Delete resources only in this End-point region
            qBuilder.addFieldClause(ResourceState.FIELD_NAME_REGION_ID, context.request.regionId);
        }

        if (!remoteResourcesKeys.isEmpty() &&
                remoteResourcesKeys.size() <= MAX_RESOURCES_TO_QUERY_ON_DELETE) {
            // do not load resources from enumExternalResourcesIds
            qBuilder.addInClause(
                    ResourceState.FIELD_NAME_ID,
                    remoteResourcesKeys,
                    Occurance.MUST_NOT_OCCUR);
        }

        QueryByPages<? extends ResourceState> queryLocalStates = new QueryByPages<>(
                getHost(),
                qBuilder.build(),
                localStateClass,
                context.request.tenantLinks,
                context.request.request.endpointLink);

        queryLocalStates.setClusterType(ServiceTypeCluster.INVENTORY_SERVICE);

        List<DeferredResult<Operation>> ops = new ArrayList<>();

        // Delete stale resources.
        return queryLocalStates.queryDocuments(ls -> {
            if (remoteResourcesKeys.contains(ls.id)) {
                return;
            }

            Operation dOp = Operation.createDelete(this, ls.documentSelfLink)
                    .setBody(context.resourceDeletionState)
                    .setReferer(this.getUri());

            DeferredResult<Operation> dr = sendWithDeferredResult(dOp)
                    .whenComplete((o, e) -> {
                        final String message = "Delete stale %s state";
                        if (e != null) {
                            logWarning(message + ": FAILED with %s",
                                    ls.documentSelfLink, Utils.toString(e));
                        } else {
                            log(Level.FINEST, message + ": SUCCESS",
                                    ls.documentSelfLink);
                        }
                    });

            ops.add(dr);
        })
                .thenCompose(r -> DeferredResult.allOf(ops))
                .thenApply(r -> context);
    }

    private void setResourceTags(ResourceState resourceState, List<Tag> tags) {
        if (tags != null && !tags.isEmpty()) {

            Map<String, String> awsResourceTags = tags.stream().collect(
                    Collectors.toMap(tag -> tag.getKey(), tag -> tag.getValue()));

            // The name of the compute state is the value of the AWS_TAG_NAME tag
            String nameTag = awsResourceTags.get(AWSConstants.AWS_TAG_NAME);
            if (nameTag != null) {
                resourceState.name = nameTag;
                awsResourceTags.remove(AWSConstants.AWS_TAG_NAME);
            }
            // add tag links
            setTagLinksToResourceState(resourceState, awsResourceTags, true);
        }
    }

    /**
     * Release the {@link Operation} (that triggered this network enumeration) and sends failure
     * patch to enumeration task.
     */
    private void finishWithFailure(AWSNetworkStateCreationContext context, Throwable exc) {

        context.operation.fail(exc);

    }

    /**
     * The async handler to handle the success and errors received after invoking the describe VPCs
     * API on AWS
     */
    class AWSVPCAsyncHandler
            extends TransitionToAsyncHandler<DescribeVpcsRequest, DescribeVpcsResult> {

        AWSVPCAsyncHandler(AWSNetworkStateCreationStage next,
                AWSNetworkStateCreationContext context) {
            super(next, context);
        }

        @Override
        protected void consumeSuccess(DescribeVpcsRequest request, DescribeVpcsResult result) {

            URI adapterUri = AdapterUriUtil.buildAdapterUri(getHost(),
                    AWSUriPaths.AWS_NETWORK_ADAPTER);
            for (Vpc resultVPC : result.getVpcs()) {
                NetworkState networkState = mapVPCToNetworkState(resultVPC,
                        this.context.request.regionId,
                        this.context.request.request.resourcePoolLink,
                        this.context.request.request.endpointLink,
                        this.context.request.parentAuth.documentSelfLink,
                        this.context.request.parentComputeLink,
                        this.context.request.tenantLinks,
                        adapterUri);
                if (networkState.subnetCIDR == null) {
                    logWarning(() -> String.format("AWS did not return CIDR information for VPC %s",
                            resultVPC.toString()));
                }
                this.context.awsVpcs.put(resultVPC.getVpcId(), resultVPC);
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
                AWSNetworkStateCreationStage next,
                AWSNetworkStateCreationContext context) {
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
                AWSNetworkStateCreationStage next,
                AWSNetworkStateCreationContext context) {
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
                    logWarning(() -> String.format("AWS returned Subnet [%s] with VCP [%s] that is"
                            + " missing locally.", subnet.getSubnetId(), subnet.getVpcId()));
                    continue;
                }

                SubnetState subnetState = mapSubnetToSubnetState(subnet,
                        this.context.request.tenantLinks,
                        this.context.request.regionId,
                        this.context.request.parentComputeLink,
                        this.context.request.request.endpointLink);

                if (subnetState.subnetCIDR == null) {
                    logWarning(() -> String.format("AWS did not return CIDR information for Subnet"
                            + " %s", subnet.toString()));
                }

                this.context.awsSubnets.put(subnet.getSubnetId(), subnet);
                this.context.subnets.put(
                        subnet.getSubnetId(),
                        new AWSNetworkStateCreationContext.SubnetStateWithParentVpcId(
                                subnet.getVpcId(), subnetState));
            }
        }
    }

    /**
     * The async handler to handle the success and errors received after invoking the describe Route
     * Tables API on AWS
     */
    class AWSMainRouteTableAsyncHandler extends
            TransitionToAsyncHandler<DescribeRouteTablesRequest, DescribeRouteTablesResult> {

        AWSMainRouteTableAsyncHandler(
                AWSNetworkStateCreationStage next,
                AWSNetworkStateCreationContext context) {
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
     * A specialization of {@link AWSAsyncHandler} which upon AWS async call completion either
     * transitions to next state in the state machine (as defined by
     * {@link AWSNetworkStateEnumerationAdapterService#handleNetworkStateChanges(AWSNetworkStateCreationContext)})
     * or finishes with failure (as defined by
     * {@link AWSNetworkStateEnumerationAdapterService#finishWithFailure(AWSNetworkStateCreationContext, Throwable)}).
     */
    private abstract class TransitionToAsyncHandler<REQ extends AmazonWebServiceRequest, RES>
            extends AWSAsyncHandler<REQ, RES> {

        final AWSNetworkStateCreationStage next;
        final AWSNetworkStateCreationContext context;

        TransitionToAsyncHandler(
                AWSNetworkStateCreationStage next,
                AWSNetworkStateCreationContext context) {

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
