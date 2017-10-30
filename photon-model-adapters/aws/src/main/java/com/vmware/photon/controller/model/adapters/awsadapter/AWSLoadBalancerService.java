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

import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory.returnClientManager;
import static com.vmware.photon.controller.model.resources.SecurityGroupService.FACTORY_LINK;
import static com.vmware.photon.controller.model.tasks.ProvisionSecurityGroupTaskService.NETWORK_STATE_ID_PROP_NAME;
import static com.vmware.photon.controller.model.util.PhotonModelUriUtils.createInventoryUri;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingAsyncClient;
import com.amazonaws.services.elasticloadbalancing.model.ConfigureHealthCheckRequest;
import com.amazonaws.services.elasticloadbalancing.model.ConfigureHealthCheckResult;
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerResult;
import com.amazonaws.services.elasticloadbalancing.model.DeleteLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.DeleteLoadBalancerResult;
import com.amazonaws.services.elasticloadbalancing.model.DeregisterInstancesFromLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.DeregisterInstancesFromLoadBalancerResult;
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest;
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersResult;
import com.amazonaws.services.elasticloadbalancing.model.HealthCheck;
import com.amazonaws.services.elasticloadbalancing.model.Instance;
import com.amazonaws.services.elasticloadbalancing.model.Listener;
import com.amazonaws.services.elasticloadbalancing.model.RegisterInstancesWithLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.RegisterInstancesWithLoadBalancerResult;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapterapi.LoadBalancerInstanceRequest;
import com.vmware.photon.controller.model.adapterapi.SecurityGroupInstanceRequest;
import com.vmware.photon.controller.model.adapterapi.SecurityGroupInstanceRequest.InstanceRequestType;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSDeferredResultAsyncHandler;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService.LoadBalancerDescription;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService.LoadBalancerDescription.HealthCheckConfiguration;
import com.vmware.photon.controller.model.resources.LoadBalancerService.LoadBalancerState;
import com.vmware.photon.controller.model.resources.LoadBalancerService.LoadBalancerStateExpanded;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.Protocol;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState.Rule;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.util.ClusterUtil;
import com.vmware.photon.controller.model.util.ClusterUtil.ServiceTypeCluster;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Adapter for provisioning a load balancer on AWS.
 */
public class AWSLoadBalancerService extends StatelessService {
    public static final String SELF_LINK = AWSUriPaths.AWS_LOAD_BALANCER_ADAPTER;
    private static final int MAX_NAME_LENGTH = 32;

    /**
     * Load balancer request context.
     */
    private static class AWSLoadBalancerContext {

        final LoadBalancerInstanceRequest request;

        LoadBalancerStateExpanded loadBalancerStateExpanded;
        AuthCredentialsServiceState credentials;

        // Instances registered with the AWS load balancer
        List<Instance> registeredInstances;
        // Instances to be registered in the load balancer based on the instances that are defined
        // in the LB state and are missing from the AWS load balancer
        List<String> instanceIdsToRegister;
        // Instances to be deregistered from the load balancer based on the instances that are defined
        // in the AWS load balancer and are missing from the LB state
        List<String> instanceIdsToDeregister;

        AmazonElasticLoadBalancingAsyncClient client;

        TaskManager taskManager;
        List<SecurityGroupState> securityGroupStates;

        String vpcId;
        SecurityGroupState provisionedSecurityGroupState;

        AWSLoadBalancerContext(StatelessService service, LoadBalancerInstanceRequest request) {
            this.request = request;
            this.taskManager = new TaskManager(service, request.taskReference,
                    request.resourceLink());
        }
    }

    private AWSClientManager clientManager;

    /**
     * Extend default 'start' logic with loading AWS client.
     */
    @Override
    public void handleStart(Operation op) {
        this.clientManager = AWSClientManagerFactory
                .getClientManager(AWSConstants.AwsClientType.LOAD_BALANCING);

        super.handleStart(op);
    }

    /**
     * Extend default 'stop' logic with releasing AWS client.
     */
    @Override
    public void handleStop(Operation op) {
        returnClientManager(this.clientManager, AWSConstants.AwsClientType.LOAD_BALANCING);

        super.handleStop(op);
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }

        // Immediately complete the Operation from calling task.
        op.complete();

        // initialize context object
        AWSLoadBalancerContext context = new AWSLoadBalancerContext(this,
                op.getBody(LoadBalancerInstanceRequest.class));

        DeferredResult.completed(context)
                .thenCompose(this::populateContext)
                .thenCompose(this::handleInstanceRequest)
                .whenComplete((o, e) -> {
                    // Once done patch the calling task with correct stage.
                    if (e == null) {
                        context.taskManager.finishTask();
                    } else {
                        context.taskManager.patchTaskToFailure(e);
                    }
                });
    }

    private DeferredResult<AWSLoadBalancerContext> populateContext(AWSLoadBalancerContext context) {
        return DeferredResult.completed(context)
                .thenCompose(this::getLoadBalancerState)
                .thenCompose(this::getCredentials)
                .thenCompose(this::getAWSClient)
                .thenCompose(this::getSecurityGroupState);
    }

    private DeferredResult<AWSLoadBalancerContext> getLoadBalancerState(
            AWSLoadBalancerContext context) {
        return this
                .sendWithDeferredResult(
                        Operation.createGet(LoadBalancerStateExpanded.buildUri(
                                context.request.resourceReference)),
                        LoadBalancerStateExpanded.class)
                .thenApply(lbStateExpanded -> {
                    if (lbStateExpanded.computes == null) {
                        lbStateExpanded.computes = Collections.emptySet();
                    }
                    return lbStateExpanded;
                }).thenApply(state -> {
                    context.loadBalancerStateExpanded = state;
                    return context;
                });
    }

    private DeferredResult<AWSLoadBalancerContext> getCredentials(AWSLoadBalancerContext context) {
        URI uri = createInventoryUri(this.getHost(),
                context.loadBalancerStateExpanded.endpointState.authCredentialsLink);
        return this.sendWithDeferredResult(
                Operation.createGet(uri), AuthCredentialsServiceState.class)
                .thenApply(authCredentialsServiceState -> {
                    context.credentials = authCredentialsServiceState;
                    return context;
                });
    }

    private DeferredResult<AWSLoadBalancerContext> getAWSClient(AWSLoadBalancerContext context) {
        if (context.request.isMockRequest) {
            return DeferredResult.completed(context);
        }

        DeferredResult<AWSLoadBalancerContext> r = new DeferredResult<>();
        context.client = this.clientManager.getOrCreateLoadBalancingClient(context.credentials,
                context.loadBalancerStateExpanded.regionId, this, context.request.isMockRequest,
                r::fail);
        if (context.client != null) {
            r.complete(context);
        }
        return r;
    }

    private DeferredResult<AWSLoadBalancerContext> getSecurityGroupState(
            AWSLoadBalancerContext context) {

        if (context.loadBalancerStateExpanded.securityGroupLinks == null ||
                context.loadBalancerStateExpanded.securityGroupLinks.isEmpty()) {
            context.securityGroupStates = Collections.emptyList();
            return DeferredResult.completed(context);
        }

        List<DeferredResult<SecurityGroupState>> securityGroupDRs =
                context.loadBalancerStateExpanded.securityGroupLinks.stream()
                        .map(context.request::buildUri)
                        .map(uri -> sendWithDeferredResult(Operation.createGet(uri),
                                SecurityGroupState.class))
                        .collect(Collectors.toList());

        return DeferredResult.allOf(securityGroupDRs)
                .thenApply(securityGroupStates -> {
                    context.securityGroupStates = securityGroupStates;
                    return context;
                });
    }

    private DeferredResult<AWSLoadBalancerContext> handleInstanceRequest(
            AWSLoadBalancerContext context) {
        DeferredResult<AWSLoadBalancerContext> execution = DeferredResult.completed(context);

        switch (context.request.requestType) {
        case CREATE:
            if (context.request.isMockRequest) {
                // no need to go the end-point; just populate an dummy LB address
                context.loadBalancerStateExpanded.address = "lb-mock-address.com";
                execution = execution
                        .thenCompose(this::updateLoadBalancerState);
            } else {
                execution = execution
                        .thenCompose(this::stripDownInvalidCharactersFromLoadBalancerName)
                        .thenCompose(this::createSecurityGroup)
                        .thenCompose(this::createLoadBalancer)
                        .thenCompose(this::configureHealthCheck)
                        .thenCompose(this::updateLoadBalancerState)
                        .thenCompose(this::assignInstances);
            }

            return execution;

        case UPDATE:
            if (context.request.isMockRequest) {
                this.logFine("Mock request to update an AWS load balancer ["
                        + context.loadBalancerStateExpanded.name + "] processed.");
            } else {
                execution = execution.thenCompose(this::getAWSLoadBalancer)
                        .thenCompose(this::assignInstances);
            }

            return execution;

        case DELETE:
            if (context.request.isMockRequest) {
                // no need to go to the end-point
                this.logFine("Mock request to delete an AWS load balancer ["
                        + context.loadBalancerStateExpanded.name + "] processed.");
            } else {
                execution = execution
                        .thenCompose(this::deleteLoadBalancer)
                        .thenCompose(this::deleteSecurityGroup);
            }

            return execution.thenCompose(this::deleteLoadBalancerState);

        default:
            IllegalStateException ex = new IllegalStateException("Unsupported request type");
            return DeferredResult.failed(ex);
        }
    }

    /**
     * Strips the name of invalid characters. In AWS Load Balancer name should contain only
     * characters or digits or dash
     */
    private DeferredResult<AWSLoadBalancerContext> stripDownInvalidCharactersFromLoadBalancerName(
            AWSLoadBalancerContext context) {

        // strip down invalid characters
        context.loadBalancerStateExpanded.name = context.loadBalancerStateExpanded.name.replaceAll
                ("[^a-zA-Z0-9-]","");

        // truncate if needed
        if (context.loadBalancerStateExpanded.name.length() > MAX_NAME_LENGTH) {
            context.loadBalancerStateExpanded.name = context.loadBalancerStateExpanded.name
                    .substring(0, MAX_NAME_LENGTH);
        }

        return DeferredResult.completed(context);
    }

    private DeferredResult<AWSLoadBalancerContext> createSecurityGroup(
            AWSLoadBalancerContext context) {

        return DeferredResult.completed(context)
                .thenCompose(this::populateVpcIdFromSubnet)
                .thenCompose(this::createSecurityGroupState)
                .thenCompose(this::provisionSecurityGroup);
    }

    private DeferredResult<AWSLoadBalancerContext> populateVpcIdFromSubnet(
            AWSLoadBalancerContext context) {
        SubnetState subnetState = context.loadBalancerStateExpanded.subnets.stream().findFirst()
                .orElse(null);

        if (subnetState == null) {
            return DeferredResult.completed(context);
        }

        Operation get = Operation.createGet(UriUtils.buildUri(getHost(), subnetState.networkLink));

        return this.sendWithDeferredResult(get, NetworkState.class)
                .thenApply(networkState -> {
                    context.vpcId = networkState.id;
                    return context;
                });
    }

    private DeferredResult<AWSLoadBalancerContext> createSecurityGroupState(
            AWSLoadBalancerContext context) {

        SecurityGroupState state = new SecurityGroupState();
        state.authCredentialsLink = context.credentials.documentSelfLink;
        state.endpointLink = context.loadBalancerStateExpanded.endpointLink;
        if (state.endpointLinks == null) {
            state.endpointLinks = new HashSet<String>();
        }
        state.endpointLinks.add(context.loadBalancerStateExpanded.endpointLink);
        state.instanceAdapterReference = UriUtils.buildUri(getHost(), AWSSecurityGroupService
                .SELF_LINK);
        state.resourcePoolLink = context.loadBalancerStateExpanded.endpointState.resourcePoolLink;
        state.customProperties = new HashMap<>();
        state.customProperties.put(ComputeProperties.INFRASTRUCTURE_USE_PROP_NAME,
                Boolean.TRUE.toString());
        state.tenantLinks = context.loadBalancerStateExpanded.tenantLinks;
        state.regionId = context.loadBalancerStateExpanded.regionId;
        state.name = context.loadBalancerStateExpanded.name + "_SG";
        state.ingress = context.loadBalancerStateExpanded.routes.stream()
                .map(routeConfiguration -> buildRule(routeConfiguration.port))
                .collect(Collectors.toList());

        state.egress = context.loadBalancerStateExpanded.routes.stream()
                .map(routeConfiguration -> buildRule(routeConfiguration.instancePort))
                .collect(Collectors.toList());
        state.computeHostLink = context.loadBalancerStateExpanded.computeHostLink;

        Operation operation = Operation.createPost(this, FACTORY_LINK).setBody(state);

        return this.sendWithDeferredResult(operation, SecurityGroupState.class)
                .thenApply(securityGroupState -> {
                    context.provisionedSecurityGroupState = securityGroupState;
                    return context;
                });
    }

    private Rule buildRule(String port) {
        Rule rule = new Rule();

        //TODO determine the ip range cidr
        rule.ipRangeCidr = "0.0.0.0/0";
        rule.ports = port;
        rule.name = port + "_rule";
        rule.protocol = Protocol.TCP.getName();
        return rule;
    }

    private SecurityGroupInstanceRequest buildSecurityGroupInstanceRequest(SecurityGroupState
            securityGroupState, InstanceRequestType type,
            AWSLoadBalancerContext context) {
        SecurityGroupInstanceRequest req = new SecurityGroupInstanceRequest();
        req.requestType = type;
        req.resourceReference = UriUtils.extendUri(ClusterUtil.getClusterUri(getHost(),
                ServiceTypeCluster.INVENTORY_SERVICE),
                securityGroupState.documentSelfLink);
        req.authCredentialsLink = securityGroupState.authCredentialsLink;
        req.resourcePoolLink = securityGroupState.resourcePoolLink;
        req.isMockRequest = context.request.isMockRequest;
        req.customProperties = new HashMap<>();
        req.customProperties.put(NETWORK_STATE_ID_PROP_NAME, context.vpcId);

        return req;
    }

    private DeferredResult<AWSLoadBalancerContext> provisionSecurityGroup(
            AWSLoadBalancerContext context) {

        SecurityGroupInstanceRequest req = buildSecurityGroupInstanceRequest(
                context.provisionedSecurityGroupState,
                InstanceRequestType.CREATE, context);

        Operation operation = Operation.createPatch(this,
                AWSSecurityGroupService.SELF_LINK)
                .setBody(req);
        return sendWithDeferredResult(operation, SecurityGroupState.class)
                .thenApply(sgs -> {
                    context.provisionedSecurityGroupState = sgs;
                    return context;
                });
    }

    private DeferredResult<AWSLoadBalancerContext> createLoadBalancer(
            AWSLoadBalancerContext context) {
        CreateLoadBalancerRequest request = buildCreationRequest(context);

        String message = "Create a new AWS Load Balancer with name ["
                + context.loadBalancerStateExpanded.name + "].";
        AWSDeferredResultAsyncHandler<CreateLoadBalancerRequest, CreateLoadBalancerResult> handler =
                new AWSDeferredResultAsyncHandler<>(this, message);

        context.client.createLoadBalancerAsync(request, handler);

        return handler.toDeferredResult().thenApply(result -> {
            context.loadBalancerStateExpanded.address = result.getDNSName();
            return context;
        });
    }

    private DeferredResult<AWSLoadBalancerContext> configureHealthCheck(
            AWSLoadBalancerContext context) {

        ConfigureHealthCheckRequest request = buildHealthCheckRequest(context);

        if (request == null) {
            return DeferredResult.completed(context);
        }

        String message = "Configure a health check to AWS Load Balancer with name ["
                + context.loadBalancerStateExpanded.name + "].";
        AWSDeferredResultAsyncHandler<ConfigureHealthCheckRequest, ConfigureHealthCheckResult> handler =
                new AWSDeferredResultAsyncHandler<>(this, message);

        context.client.configureHealthCheckAsync(request, handler);

        return handler.toDeferredResult().thenApply(ignore -> context);
    }

    private ConfigureHealthCheckRequest buildHealthCheckRequest(AWSLoadBalancerContext context) {

        HealthCheckConfiguration healthCheckConfiguration = context.loadBalancerStateExpanded.routes
                .stream()
                .filter(config -> config != null && config.healthCheckConfiguration != null)
                .map(config -> config.healthCheckConfiguration).findFirst().orElse(null);

        if (healthCheckConfiguration == null) {
            return null;
        }

        // Construct the target HTTP:80/index.html
        String target = healthCheckConfiguration.protocol + ":" + healthCheckConfiguration.port
                + healthCheckConfiguration.urlPath;

        HealthCheck healthCheck = new HealthCheck()
                .withHealthyThreshold(healthCheckConfiguration.healthyThreshold)
                .withInterval(healthCheckConfiguration.intervalSeconds).withTarget(target)
                .withTimeout(healthCheckConfiguration.timeoutSeconds)
                .withUnhealthyThreshold(healthCheckConfiguration.unhealthyThreshold);

        return new ConfigureHealthCheckRequest()
                .withLoadBalancerName(context.loadBalancerStateExpanded.name)
                .withHealthCheck(healthCheck);
    }

    private DeferredResult<AWSLoadBalancerContext> updateLoadBalancerState(
            AWSLoadBalancerContext context) {
        LoadBalancerState loadBalancerState = new LoadBalancerState();
        loadBalancerState.address = context.loadBalancerStateExpanded.address;
        loadBalancerState.name = context.loadBalancerStateExpanded.name;
        if (context.provisionedSecurityGroupState != null) {
            loadBalancerState.securityGroupLinks = Collections
                    .singletonList(context.provisionedSecurityGroupState.documentSelfLink);
        }

        Operation op = Operation
                .createPatch(this, context.loadBalancerStateExpanded.documentSelfLink);
        op.setBody(loadBalancerState);

        return this.sendWithDeferredResult(op).thenApply(ignore -> context);
    }

    private DeferredResult<AWSLoadBalancerContext> getAWSLoadBalancer(
            AWSLoadBalancerContext context) {
        DescribeLoadBalancersRequest describeRequest = new DescribeLoadBalancersRequest()
                .withLoadBalancerNames(context.loadBalancerStateExpanded.name);

        String message =
                "Describing AWS load balancer [" + context.loadBalancerStateExpanded.name + "].";
        AWSDeferredResultAsyncHandler<DescribeLoadBalancersRequest, DescribeLoadBalancersResult> handler =
                new AWSDeferredResultAsyncHandler<>(this, message);

        context.client.describeLoadBalancersAsync(describeRequest, handler);

        return handler.toDeferredResult().thenCompose(result -> {

            List<com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription> lbs = result
                    .getLoadBalancerDescriptions();

            if (lbs != null && !lbs.isEmpty() && lbs.size() == 1) {
                context.registeredInstances = lbs.iterator().next().getInstances();
                return DeferredResult.completed(context);
            }

            return DeferredResult.failed(new IllegalStateException(
                    "Unable to describe load balancer with name '"
                            + context.loadBalancerStateExpanded.name + "' for update"));
        });
    }

    private DeferredResult<AWSLoadBalancerContext> assignInstances(AWSLoadBalancerContext context) {
        // If the registered instances are null this is a newly provisioned load balancer
        // so add all instances from the load balancer state to the registration request
        if (context.registeredInstances == null) {
            context.instanceIdsToRegister = context.loadBalancerStateExpanded.computes.stream()
                    .map(computeState -> computeState.id)
                    .collect(Collectors.toList());

            context.instanceIdsToDeregister = Collections.emptyList();
        } else {
            context.instanceIdsToRegister = context.loadBalancerStateExpanded.computes.stream()
                    .map(computeState -> computeState.id)
                    .filter(csId -> context.registeredInstances.stream()
                            .noneMatch(i -> i.getInstanceId().equals(csId))
                    )
                    .collect(Collectors.toList());

            context.instanceIdsToDeregister = context.registeredInstances.stream()
                    .map(Instance::getInstanceId)
                    .filter(instanceId -> context.loadBalancerStateExpanded.computes.stream()
                            .noneMatch(computeState -> computeState.id.equals(instanceId))
                    )
                    .collect(Collectors.toList());
        }

        return DeferredResult.completed(context)
                .thenCompose(this::registerInstances)
                .thenCompose(this::deregisterInstances);

    }

    private DeferredResult<AWSLoadBalancerContext> registerInstances(
            AWSLoadBalancerContext context) {
        // Do not try to assign instances if there aren't any
        if (context.instanceIdsToRegister.isEmpty()) {
            return DeferredResult.completed(context);
        }

        RegisterInstancesWithLoadBalancerRequest request = buildInstanceRegistrationRequest(
                context);

        String message = "Registering instances to AWS Load Balancer with name ["
                + context.loadBalancerStateExpanded.name + "]";
        AWSDeferredResultAsyncHandler<RegisterInstancesWithLoadBalancerRequest, RegisterInstancesWithLoadBalancerResult> handler =
                new AWSDeferredResultAsyncHandler<>(this, message);

        context.client.registerInstancesWithLoadBalancerAsync(request, handler);

        return handler.toDeferredResult()
                .thenApply(ignore -> context);
    }

    private DeferredResult<AWSLoadBalancerContext> deregisterInstances(
            AWSLoadBalancerContext context) {
        // Do not try to deregister instances if there aren't any
        if (context.instanceIdsToDeregister.isEmpty()) {
            return DeferredResult.completed(context);
        }

        DeregisterInstancesFromLoadBalancerRequest request = buildInstanceDeregistrationRequest(
                context);

        String message = "Deregistering instances to AWS Load Balancer with name ["
                + context.loadBalancerStateExpanded.name + "]";

        AWSDeferredResultAsyncHandler<DeregisterInstancesFromLoadBalancerRequest, DeregisterInstancesFromLoadBalancerResult> handler =
                new AWSDeferredResultAsyncHandler<>(this, message);

        context.client.deregisterInstancesFromLoadBalancerAsync(request, handler);

        return handler.toDeferredResult().thenApply(ignore -> context);
    }

    private CreateLoadBalancerRequest buildCreationRequest(AWSLoadBalancerContext context) {
        // Combine all security groups associated with the LB to a single list
        Collection<SecurityGroupState> securityGroupsToUse = new ArrayList<>();
        if (context.provisionedSecurityGroupState != null) {
            securityGroupsToUse.add(context.provisionedSecurityGroupState);
        }
        if (context.securityGroupStates != null && !context.securityGroupStates.isEmpty()) {
            securityGroupsToUse.addAll(context.securityGroupStates);
        }

        CreateLoadBalancerRequest request = new CreateLoadBalancerRequest()
                .withLoadBalancerName(context.loadBalancerStateExpanded.name)
                .withListeners(buildListeners(context))
                .withSubnets(context.loadBalancerStateExpanded.subnets.stream()
                        .map(subnet -> subnet.id)
                        .collect(Collectors.toList())
                )
                .withSecurityGroups(
                        securityGroupsToUse.stream().map(sg -> sg.id).collect(Collectors.toList()));

        // Set scheme to Internet-facing if specified. By default, an internal load balancer is
        // created
        if (!Boolean.TRUE.equals(context.loadBalancerStateExpanded.internetFacing)) {
            request.setScheme("internal");
        }

        return request;
    }

    private List<Listener> buildListeners(AWSLoadBalancerContext context) {
        return context.loadBalancerStateExpanded.routes.stream().map(routeConfiguration -> {
            Listener listener = new Listener()
                    .withLoadBalancerPort(Integer.parseInt(routeConfiguration.port))
                    .withInstancePort(Integer.parseInt(routeConfiguration.instancePort));

            // Convert HTTPS protocol on the load balancer to TCP thus the load balancer will act
            // as a SSL Pass-through. Set the instance protocol to be TCP as well as both protocols
            // must be on the same layer
            if (LoadBalancerDescription.Protocol.HTTPS.name()
                    .equalsIgnoreCase(routeConfiguration.protocol)) {
                listener.setProtocol(LoadBalancerDescription.Protocol.TCP.name());
                listener.setInstanceProtocol(LoadBalancerDescription.Protocol.TCP.name());
            } else {
                listener.setProtocol(routeConfiguration.protocol);
                listener.setInstanceProtocol(routeConfiguration.instanceProtocol);
            }

            return listener;
        }).collect(Collectors.toList());
    }

    private RegisterInstancesWithLoadBalancerRequest buildInstanceRegistrationRequest(
            AWSLoadBalancerContext context) {

        return new RegisterInstancesWithLoadBalancerRequest()
                .withLoadBalancerName(context.loadBalancerStateExpanded.name)
                .withInstances(context.instanceIdsToRegister.stream()
                        .map(Instance::new)
                        .collect(Collectors.toList())
                );
    }

    private DeregisterInstancesFromLoadBalancerRequest buildInstanceDeregistrationRequest(
            AWSLoadBalancerContext context) {

        return new DeregisterInstancesFromLoadBalancerRequest()
                .withLoadBalancerName(context.loadBalancerStateExpanded.name)
                .withInstances(context.instanceIdsToDeregister.stream()
                        .map(Instance::new)
                        .collect(Collectors.toList())
                );
    }

    private DeferredResult<AWSLoadBalancerContext> deleteLoadBalancer(
            AWSLoadBalancerContext context) {
        DeleteLoadBalancerRequest request = new DeleteLoadBalancerRequest()
                .withLoadBalancerName(context.loadBalancerStateExpanded.name);

        String message = "Delete AWS Load Balancer with name ["
                + context.loadBalancerStateExpanded.name + "]";
        AWSDeferredResultAsyncHandler<DeleteLoadBalancerRequest, DeleteLoadBalancerResult> handler =
                new AWSDeferredResultAsyncHandler<>(this, message);

        context.client.deleteLoadBalancerAsync(request, handler);

        return handler.toDeferredResult().thenApply(ignore -> context);
    }

    private DeferredResult<AWSLoadBalancerContext> deleteLoadBalancerState(
            AWSLoadBalancerContext context) {
        return this
                .sendWithDeferredResult(
                        Operation.createDelete(this,
                                context.loadBalancerStateExpanded.documentSelfLink))
                .thenApply(operation -> context);
    }

    private DeferredResult<AWSLoadBalancerContext> deleteSecurityGroup(
            AWSLoadBalancerContext context) {
        List<SecurityGroupState> infrastructureSecurityGroups =
                context.securityGroupStates.stream()
                        .filter(this::isInfrastructureResource)
                        .collect(Collectors.toList());
        if (infrastructureSecurityGroups.isEmpty()) {
            return DeferredResult.completed(context);
        }

        List<DeferredResult<Operation>> deletionDRs = infrastructureSecurityGroups.stream()
                .map(sg -> buildSecurityGroupInstanceRequest(sg, InstanceRequestType.DELETE,
                        context))
                .map(req -> Operation.createPatch(this, AWSSecurityGroupService.SELF_LINK)
                        .setBody(req))
                .map(op -> sendWithDeferredResult(op).exceptionally(th -> {
                    // Delete requests should not fail, only log the problem.
                    this.logWarning("Unable to delete security group: %s", th.getMessage());
                    return null;
                })).collect(Collectors.toList());

        return DeferredResult.allOf(deletionDRs).thenApply(ignore -> context);
    }

    private boolean isInfrastructureResource(ResourceState resource) {
        return resource.customProperties != null && Boolean.TRUE.toString().equals(
                resource.customProperties.get(ComputeProperties.INFRASTRUCTURE_USE_PROP_NAME));
    }
}
