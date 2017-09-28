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


package com.vmware.photon.controller.model.adapters.awsadapter.enumeration;

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.getQueryPageSize;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUriPaths.AWS_LOAD_BALANCER_ADAPTER;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingAsyncClient;
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest;
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersResult;
import com.amazonaws.services.elasticloadbalancing.model.HealthCheck;
import com.amazonaws.services.elasticloadbalancing.model.Instance;
import com.amazonaws.services.elasticloadbalancing.model.ListenerDescription;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;

import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AwsClientType;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUriPaths;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSNetworkStateEnumerationAdapterService.AWSNetworkEnumerationResponse;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSSecurityGroupEnumerationAdapterService.AWSSecurityGroupEnumerationResponse;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSDeferredResultAsyncHandler;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.adapters.util.ComputeEnumerateAdapterRequest;
import com.vmware.photon.controller.model.adapters.util.Pair;
import com.vmware.photon.controller.model.adapters.util.enums.BaseComputeEnumerationAdapterContext;
import com.vmware.photon.controller.model.adapters.util.enums.EnumerationStages;
import com.vmware.photon.controller.model.query.QueryStrategy;
import com.vmware.photon.controller.model.query.QueryUtils.QueryByPages;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService.LoadBalancerDescription.HealthCheckConfiguration;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService.LoadBalancerDescription.RouteConfiguration;
import com.vmware.photon.controller.model.resources.LoadBalancerService;
import com.vmware.photon.controller.model.resources.LoadBalancerService.LoadBalancerState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceStateCollectionUpdateRequest;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;

public class AWSLoadBalancerEnumerationAdapterService extends StatelessService {

    public static final String SELF_LINK = AWSUriPaths.AWS_LOAD_BALANCER_ENUMERATION_ADAPTER;

    private AWSClientManager clientManager;

    public static final String ENABLE_LOAD_BALANCER_PROPERTY = "photon-model.adapter.aws"
            + ".enable.loadbalancer.enumeration";

    // By default load balancer enumeration is disabled
    private static final Boolean ENABLE_LOAD_BALANCER_ENUMERATION = Boolean
            .getBoolean(ENABLE_LOAD_BALANCER_PROPERTY);

    public AWSLoadBalancerEnumerationAdapterService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        this.clientManager = AWSClientManagerFactory.getClientManager(AwsClientType.LOAD_BALANCING);
    }

    /**
     * Request accepted by this service to trigger enumeration of Network entities in Amazon.
     *
     * @see AWSNetworkEnumerationResponse
     */
    public static class AWSLoadBalancerEnumerationRequest {
        public ComputeEnumerateAdapterRequest computeRequest;
        public AWSNetworkEnumerationResponse enumeratedNetworks;
        public AWSSecurityGroupEnumerationResponse enumeratedSecurityGroups;
    }

    private static class LoadBalancerEnumContext extends
            BaseComputeEnumerationAdapterContext<AWSLoadBalancerEnumerationAdapterService.LoadBalancerEnumContext, LoadBalancerState, LoadBalancerDescription> {

        private static final String TARGET_PATTERN = "([a-zA-Z]*):([\\d]*)(/.*)?";

        private String regionId;

        public AmazonElasticLoadBalancingAsyncClient amazonLoadBalancerClient;

        private Map<String, String> localComputeStates = new HashMap<>();
        private Map<String, String> localSubNetworkStates = new HashMap<>();
        private Map<String, String> localSecurityGroupStates = new HashMap<>();

        public LoadBalancerEnumContext(StatelessService service,
                AWSLoadBalancerEnumerationRequest request, Operation op) {

            super(service, request.computeRequest, op, LoadBalancerState.class,
                    LoadBalancerService.FACTORY_LINK);

            this.regionId = request.computeRequest.regionId;

            if (request.enumeratedNetworks != null && request.enumeratedNetworks.subnets != null) {
                this.localSubNetworkStates = request.enumeratedNetworks.subnets;
            }

            if (request.enumeratedSecurityGroups != null
                    && request.enumeratedSecurityGroups.securityGroupStates != null) {
                this.localSecurityGroupStates = request.enumeratedSecurityGroups.securityGroupStates;
            }
        }

        @Override
        protected DeferredResult<LoadBalancerEnumContext> queryLocalStates(
                LoadBalancerEnumContext context) {
            return super.queryLocalStates(context)
                    .thenCompose(this::getComputeStates);
        }

        private DeferredResult<LoadBalancerEnumContext> getComputeStates(
                LoadBalancerEnumContext context) {
            if (context.remoteResources.values().isEmpty()) {
                return DeferredResult.completed(context);
            }

            List<String> instanceIds = context.remoteResources.values().stream()
                    .flatMap(lb -> lb.getInstances().stream())
                    .map(Instance::getInstanceId)
                    .collect(Collectors.toList());

            if (instanceIds.isEmpty()) {
                return DeferredResult.completed(context);
            }

            Query.Builder qBuilder = Builder.create()
                    .addKindFieldClause(ComputeState.class)
                    .addInClause(ResourceState.FIELD_NAME_ID, instanceIds);

            QueryStrategy<ComputeState> queryByPages = new QueryByPages<>(
                    this.service.getHost(),
                    qBuilder.build(),
                    ComputeState.class,
                    context.request.parentCompute.tenantLinks,
                    context.request.original.endpointLink);

            return queryByPages.queryDocuments(
                    computeState -> this.localComputeStates
                            .put(computeState.id, computeState.documentSelfLink))
                    .thenApply(ignore -> context);
        }

        @Override
        protected DeferredResult<RemoteResourcesPage> getExternalResources(String nextPageLink) {
            DescribeLoadBalancersRequest describeRequest = new DescribeLoadBalancersRequest()
                    .withPageSize(getQueryPageSize());

            if (nextPageLink != null) {
                describeRequest.setMarker(nextPageLink);
            }

            String msg =
                    "Getting AWS Load Balancers [" + this.request.original.resourceReference + "]";

            AWSDeferredResultAsyncHandler<DescribeLoadBalancersRequest, DescribeLoadBalancersResult> asyncHandler =
                    new AWSDeferredResultAsyncHandler<>(this.service, msg);

            this.amazonLoadBalancerClient.describeLoadBalancersAsync(describeRequest, asyncHandler);

            return asyncHandler.toDeferredResult().thenApply(describeLoadBalancersResult -> {
                RemoteResourcesPage page = new RemoteResourcesPage();
                page.nextPageLink = describeLoadBalancersResult.getNextMarker();

                describeLoadBalancersResult.getLoadBalancerDescriptions().forEach(
                        lbDescription -> page.resourcesPage
                                .put(lbDescription.getLoadBalancerName(), lbDescription));

                return page;
            });
        }

        @Override
        protected DeferredResult<LocalStateHolder> buildLocalResourceState(
                LoadBalancerDescription remoteResource,
                LoadBalancerState existingLocalResourceState) {

            LocalStateHolder stateHolder = new LocalStateHolder();
            stateHolder.localState = new LoadBalancerState();

            stateHolder.localState.name = remoteResource.getLoadBalancerName();
            stateHolder.localState.address = remoteResource.getDNSName();
            stateHolder.localState.endpointLink = this.request.original.endpointLink;
            if (stateHolder.localState.endpointLinks == null) {
                stateHolder.localState.endpointLinks = new HashSet<String>();
            }
            stateHolder.localState.endpointLinks.add(this.request.original.endpointLink);
            stateHolder.localState.internetFacing = !"internal".equals(remoteResource.getScheme());

            stateHolder.localState.routes = getRouteConfigurations(remoteResource);

            if (existingLocalResourceState == null) {
                stateHolder.localState.regionId = this.regionId;
                stateHolder.localState.instanceAdapterReference = AdapterUriUtil
                        .buildAdapterUri(this.service.getHost(), AWS_LOAD_BALANCER_ADAPTER);

                stateHolder.localState.subnetLinks = remoteResource.getSubnets().stream()
                        .map(subnetId -> this.localSubNetworkStates.get(subnetId))
                        .filter(Objects::nonNull).collect(Collectors.toSet());

                stateHolder.localState.securityGroupLinks = remoteResource.getSecurityGroups()
                        .stream()
                        .map(sgId -> this.localSecurityGroupStates.get(sgId))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

                stateHolder.localState.computeLinks = remoteResource.getInstances().stream()
                        .map(instance -> this.localComputeStates.get(instance.getInstanceId()))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

                // Skip load balancers that do not have any instances attached
                if (stateHolder.localState.computeLinks.isEmpty()) {
                    stateHolder.localState = SKIP;
                    return DeferredResult.completed(stateHolder);
                }
            } else {
                ServiceStateCollectionUpdateRequest updateRequest = calculateDeltaForUpdateCollectionRequest(
                        remoteResource, existingLocalResourceState);

                return this.service
                        .sendWithDeferredResult(Operation
                                .createPatch(this.service,
                                        existingLocalResourceState.documentSelfLink)
                                .setBody(updateRequest)
                        )
                        .thenApply(ignore -> stateHolder);
            }

            return DeferredResult.completed(stateHolder);
        }

        /**
         * Calculates the links that need to be added and the links to be removed from the
         * Compute links, SecurityGroup links and Subnet links
         */
        private ServiceStateCollectionUpdateRequest calculateDeltaForUpdateCollectionRequest(
                LoadBalancerDescription remoteResource,
                LoadBalancerState existingLocalResourceState) {

            Map<String, Collection<Object>> linksToAdd = new HashMap<>();
            Map<String, Collection<Object>> linksToRemove = new HashMap<>();

            // Only update compute links if the load balancer is not manager by the adapter
            if (existingLocalResourceState.descriptionLink == null) {
                Set<String> remoteComputeLinks = remoteResource.getInstances().stream()
                        .map(instance -> this.localComputeStates.get(instance.getInstanceId()))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

                Pair<Collection<Object>, Collection<Object>> linkDeltaPair = getLinkDeltaPair(
                        existingLocalResourceState.computeLinks,
                        remoteComputeLinks);

                linksToAdd.put(LoadBalancerService.FIELD_NAME_COMPUTE_LINKS, linkDeltaPair.left);
                linksToRemove.put(LoadBalancerService.FIELD_NAME_COMPUTE_LINKS,
                        linkDeltaPair.right);
            }

            {
                Set<String> remoteSubnetLinks = remoteResource.getSubnets().stream()
                        .map(subnetId -> this.localSubNetworkStates.get(subnetId))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

                Pair<Collection<Object>, Collection<Object>> linkDeltaPair = getLinkDeltaPair(
                        existingLocalResourceState.subnetLinks,
                        remoteSubnetLinks);

                linksToAdd.put(LoadBalancerService.FIELD_NAME_SUBNET_LINKS, linkDeltaPair.left);
                linksToRemove.put(LoadBalancerService.FIELD_NAME_SUBNET_LINKS,
                        linkDeltaPair.right);
            }

            {
                List<String> remoteSecurityGroups = remoteResource.getSecurityGroups()
                        .stream()
                        .map(sgId -> this.localSecurityGroupStates.get(sgId))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

                Pair<Collection<Object>, Collection<Object>> linkDeltaPair = getLinkDeltaPair(
                        existingLocalResourceState.securityGroupLinks,
                        remoteSecurityGroups);

                linksToAdd.put(LoadBalancerService.FIELD_NAME_SECURITY_GROUP_LINKS,
                        linkDeltaPair.left);
                linksToRemove.put(LoadBalancerService.FIELD_NAME_SECURITY_GROUP_LINKS,
                        linkDeltaPair.right);
            }

            return ServiceStateCollectionUpdateRequest.create(linksToAdd, linksToRemove);

        }

        private Pair<Collection<Object>, Collection<Object>> getLinkDeltaPair(
                Collection<String> localLinks, Collection<String> remoteLinks) {

            Collection<Object> linksToAdd = findDifferences(remoteLinks, localLinks);
            Collection<Object> linksToRemove = findDifferences(localLinks, remoteLinks);

            return Pair.of(linksToAdd, linksToRemove);
        }

        /**
         * @return The values that are contained in Collection a and are not contained in
         * Collection b
         */
        private Collection<Object> findDifferences(Collection<String> a, Collection<String> b) {
            return a.stream()
                    .filter(link -> !b.contains(link))
                    .collect(Collectors.toList());
        }

        private List<RouteConfiguration> getRouteConfigurations(
                LoadBalancerDescription remoteResource) {

            List<RouteConfiguration> routes = remoteResource.getListenerDescriptions().stream()
                    .map(ListenerDescription::getListener)
                    .map(listener -> {
                        RouteConfiguration routeConfiguration = new RouteConfiguration();
                        routeConfiguration.instancePort = String
                                .valueOf(listener.getInstancePort());
                        routeConfiguration.instanceProtocol = listener.getInstanceProtocol();
                        routeConfiguration.port = String.valueOf(listener.getLoadBalancerPort());
                        routeConfiguration.protocol = listener.getProtocol();
                        return routeConfiguration;
                    })
                    .collect(Collectors.toList());

            // Attach the HealthCheck from AWS to the fist route
            if (!routes.isEmpty()) {
                HealthCheck healthCheck = remoteResource.getHealthCheck();
                RouteConfiguration routeConfiguration = routes.iterator().next();

                Pattern targetPattern = Pattern.compile(TARGET_PATTERN);
                Matcher targetMatcher = targetPattern.matcher(healthCheck.getTarget());
                if (targetMatcher.find()) {
                    routeConfiguration.healthCheckConfiguration = new HealthCheckConfiguration();
                    routeConfiguration.healthCheckConfiguration.timeoutSeconds = healthCheck
                            .getTimeout();
                    routeConfiguration.healthCheckConfiguration.unhealthyThreshold = healthCheck
                            .getUnhealthyThreshold();
                    routeConfiguration.healthCheckConfiguration.healthyThreshold = healthCheck
                            .getHealthyThreshold();
                    routeConfiguration.healthCheckConfiguration.intervalSeconds = healthCheck
                            .getInterval();

                    routeConfiguration.healthCheckConfiguration.protocol = targetMatcher.group(1);
                    routeConfiguration.healthCheckConfiguration.port = targetMatcher.group(2);
                    routeConfiguration.healthCheckConfiguration.urlPath = targetMatcher.group(3);
                }

            }
            return routes;
        }

        @Override
        protected void customizeLocalStatesQuery(Builder qBuilder) {
            qBuilder.addFieldClause(LoadBalancerState.FIELD_NAME_REGION_ID, this.request.regionId);
        }
    }

    /**
     * Method to instantiate the AWS Async client for future use
     */
    private void getAWSAsyncClient(LoadBalancerEnumContext context, EnumerationStages next) {
        if (context.amazonLoadBalancerClient == null) {
            context.amazonLoadBalancerClient = this.clientManager
                    .getOrCreateLoadBalancingClient(context.request.endpointAuth, context.regionId,
                            this, context.request.original.isMockRequest,
                            (t) -> handleError(context, t));
            if (context.amazonLoadBalancerClient == null) {
                return;
            }
            context.stage = next;
        }
        handleEnumeration(context);
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }

        LoadBalancerEnumContext ctx = new LoadBalancerEnumContext(this,
                op.getBody(AWSLoadBalancerEnumerationRequest.class), op);

        if (ctx.request.original.isMockRequest || !ENABLE_LOAD_BALANCER_ENUMERATION) {
            op.complete();
            return;
        }

        handleEnumeration(ctx);
    }

    @Override
    public void handleStop(Operation op) {
        AWSClientManagerFactory.returnClientManager(this.clientManager,
                AWSConstants.AwsClientType.LOAD_BALANCING);
        super.handleStop(op);
    }

    /**
     * Creates the load balancer states in the local document store based on the load balancers
     * received from the remote endpoint.
     *
     * @param context The local service context that has all the information needed to create the
     *                additional states in the local system.
     */
    private void handleEnumeration(LoadBalancerEnumContext context) {
        switch (context.stage) {

        case CLIENT:
            getAWSAsyncClient(context, EnumerationStages.ENUMERATE);
            break;
        case ENUMERATE:
            switch (context.request.original.enumerationAction) {
            case START:
                context.request.original.enumerationAction = EnumerationAction.REFRESH;
                handleEnumeration(context);
                break;
            case REFRESH:
                // Allow base context class to enumerate the resources.
                context.enumerate().whenComplete((ignoreCtx, throwable) -> {
                    if (throwable != null) {
                        handleError(context, throwable);
                        return;
                    }
                    context.stage = EnumerationStages.FINISHED;
                    handleEnumeration(context);
                });
                break;
            case STOP:
                context.stage = EnumerationStages.FINISHED;
                handleEnumeration(context);
                break;
            default:
                handleError(context, new RuntimeException(
                        "Unknown enumeration action" + context.request.original.enumerationAction));
                break;
            }
            break;
        case FINISHED:
            context.operation.complete();
            break;
        case ERROR:
            context.operation.fail(context.error);
            break;
        default:
            String msg = String
                    .format("Unknown AWS enumeration stage %s ", context.stage.toString());
            logSevere(() -> msg);
            context.error = new IllegalStateException(msg);
        }
    }

    private void handleError(LoadBalancerEnumContext ctx, Throwable e) {
        logSevere(() -> String
                .format("Failed at stage %s with exception: %s", ctx.stage, Utils.toString(e)));
        ctx.error = e;
        ctx.stage = EnumerationStages.ERROR;
        handleEnumeration(ctx);
    }
}
