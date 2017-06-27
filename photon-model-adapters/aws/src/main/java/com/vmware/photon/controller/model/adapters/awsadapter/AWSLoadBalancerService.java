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

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingAsyncClient;
import com.amazonaws.services.elasticloadbalancing.model.ConfigureHealthCheckRequest;
import com.amazonaws.services.elasticloadbalancing.model.ConfigureHealthCheckResult;
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerResult;
import com.amazonaws.services.elasticloadbalancing.model.DeleteLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.DeleteLoadBalancerResult;
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
import com.vmware.photon.controller.model.resources.SecurityGroupService.Protocol;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState.Rule;
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
    public static final String SECURITY_GROUP_DOCUMENT_SELF_LINK =
            "awsSecurityGroupDocumentSelfLink";

    /**
     * Load balancer request context.
     */
    private static class AWSLoadBalancerContext {

        final LoadBalancerInstanceRequest request;

        LoadBalancerStateExpanded loadBalancerStateExpanded;
        AuthCredentialsServiceState credentials;
        String loadBalancerAddress;

        AmazonElasticLoadBalancingAsyncClient client;

        TaskManager taskManager;
        SecurityGroupState securityGroupState;

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
                .thenApply(state -> {
                    context.loadBalancerStateExpanded = state;
                    return context;
                });
    }

    private DeferredResult<AWSLoadBalancerContext> getCredentials(AWSLoadBalancerContext context) {
        URI uri = context.request
                .buildUri(context.loadBalancerStateExpanded.endpointState.authCredentialsLink);
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

        if (context.loadBalancerStateExpanded.customProperties == null ||
                context.loadBalancerStateExpanded.customProperties
                        .get(SECURITY_GROUP_DOCUMENT_SELF_LINK) == null) {
            return DeferredResult.completed(context);
        }
        String sgDocumentSelfLink = context.loadBalancerStateExpanded.customProperties.get
                (SECURITY_GROUP_DOCUMENT_SELF_LINK);

        URI uri = context.request.buildUri(sgDocumentSelfLink);
        return this.sendWithDeferredResult(Operation.createGet(uri), SecurityGroupState.class)
                .thenApply(securityGroupState -> {
                    context.securityGroupState = securityGroupState;
                    return context;
                });
    }

    private DeferredResult<AWSLoadBalancerContext> handleInstanceRequest(
            AWSLoadBalancerContext context) {
        DeferredResult<AWSLoadBalancerContext> execution = DeferredResult.completed(context);

        switch (context.request.requestType) {
        case CREATE:
            if (context.request.isMockRequest) {
                // no need to go the end-point; just generate AWS Load Balancer Id.
                // TODO
            } else {
                execution = execution
                        .thenCompose(this::createSecurityGroup)
                        .thenCompose(this::createLoadBalancer)
                        .thenCompose(this::configureHealthCheck)
                        .thenCompose(this::updateLoadBalancerState)
                        .thenCompose(this::assignInstances);
            }

            return execution;

        case DELETE:
            if (context.request.isMockRequest) {
                // no need to go to the end-point (TODO: add ID to the log message)
                this.logFine("Mock request to delete an AWS load balancer processed.");
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

    private DeferredResult<AWSLoadBalancerContext> createSecurityGroup(
            AWSLoadBalancerContext context) {
        return DeferredResult.completed(context)
                .thenCompose(this::createSecurityGroupState)
                .thenCompose(this::provisionSecurityGroup);
    }

    private DeferredResult<AWSLoadBalancerContext> createSecurityGroupState(
            AWSLoadBalancerContext context) {

        SecurityGroupState state = new SecurityGroupState();
        state.authCredentialsLink = context.credentials.documentSelfLink;
        state.endpointLink = context.loadBalancerStateExpanded.endpointLink;
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

        Operation operation = Operation.createPost(this, FACTORY_LINK).setBody(state);

        return this.sendWithDeferredResult(operation, SecurityGroupState.class)
                .thenApply(securityGroupState -> {
                    context.securityGroupState = securityGroupState;
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
            LoadBalancerInstanceRequest request) {
        SecurityGroupInstanceRequest req = new SecurityGroupInstanceRequest();
        req.requestType = type;
        req.resourceReference = UriUtils.buildUri(this.getHost(),
                securityGroupState.documentSelfLink);
        req.authCredentialsLink = securityGroupState.authCredentialsLink;
        req.resourcePoolLink = securityGroupState.resourcePoolLink;
        req.isMockRequest = request.isMockRequest;

        return req;
    }

    private DeferredResult<AWSLoadBalancerContext> provisionSecurityGroup(
            AWSLoadBalancerContext context) {

        SecurityGroupInstanceRequest req = buildSecurityGroupInstanceRequest(context
                .securityGroupState, InstanceRequestType.CREATE, context.request);

        Operation operation = Operation.createPatch(this,
                AWSSecurityGroupService.SELF_LINK)
                .setBody(req);
        return sendWithDeferredResult(operation, SecurityGroupState.class)
                .thenApply(sgs -> {
                    context.securityGroupState = sgs;
                    return context;
                });
    }

    private DeferredResult<AWSLoadBalancerContext> createLoadBalancer(
            AWSLoadBalancerContext context) {
        CreateLoadBalancerRequest request = buildCreationRequest(context);

        String message = "Create a new AWS Load Balancer with name ["
                + context.loadBalancerStateExpanded.name + "].";
        AWSDeferredResultAsyncHandler<CreateLoadBalancerRequest, CreateLoadBalancerResult> handler =
                new AWSDeferredResultAsyncHandler<CreateLoadBalancerRequest,
                        CreateLoadBalancerResult>(this, message) {
                    @Override
                    protected DeferredResult<CreateLoadBalancerResult> consumeSuccess(
                            CreateLoadBalancerRequest request,
                            CreateLoadBalancerResult result) {
                        return DeferredResult.completed(result);
                    }
                };

        context.client.createLoadBalancerAsync(request, handler);
        return handler.toDeferredResult().thenApply(result -> {
            context.loadBalancerAddress = result.getDNSName();
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
        AWSDeferredResultAsyncHandler<ConfigureHealthCheckRequest, ConfigureHealthCheckResult> handler = new AWSDeferredResultAsyncHandler<ConfigureHealthCheckRequest, ConfigureHealthCheckResult>(
                this, message) {
            @Override
            protected DeferredResult<ConfigureHealthCheckResult> consumeSuccess(
                    ConfigureHealthCheckRequest request, ConfigureHealthCheckResult result) {
                return DeferredResult.completed(result);
            }
        };

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
        loadBalancerState.address = context.loadBalancerAddress;
        loadBalancerState.customProperties = new HashMap<>();
        loadBalancerState.customProperties.put(SECURITY_GROUP_DOCUMENT_SELF_LINK,
                context.securityGroupState.documentSelfLink);

        Operation op = Operation
                .createPatch(this, context.loadBalancerStateExpanded.documentSelfLink);
        op.setBody(loadBalancerState);

        return this.sendWithDeferredResult(op).thenApply(ignore -> context);
    }

    private DeferredResult<AWSLoadBalancerContext> assignInstances(AWSLoadBalancerContext context) {

        // Do not try to assign instances if there aren't any
        if (context.loadBalancerStateExpanded.computes.isEmpty()) {
            return DeferredResult.completed(context);
        }

        RegisterInstancesWithLoadBalancerRequest request = buildInstanceRegistrationRequest(
                context);

        String message = "Registering instances to AWS Load Balancer with name ["
                + context.loadBalancerStateExpanded.name + "].";
        AWSDeferredResultAsyncHandler<RegisterInstancesWithLoadBalancerRequest, RegisterInstancesWithLoadBalancerResult> handler =
                new AWSDeferredResultAsyncHandler<RegisterInstancesWithLoadBalancerRequest,
                        RegisterInstancesWithLoadBalancerResult>(this, message) {
                    @Override
                    protected DeferredResult<RegisterInstancesWithLoadBalancerResult> consumeSuccess(
                            RegisterInstancesWithLoadBalancerRequest request,
                            RegisterInstancesWithLoadBalancerResult result) {
                        return DeferredResult.completed(result);
                    }
                };

        context.client.registerInstancesWithLoadBalancerAsync(request, handler);
        return handler.toDeferredResult()
                .thenApply(ignore -> context);
    }

    private CreateLoadBalancerRequest buildCreationRequest(AWSLoadBalancerContext context) {

        CreateLoadBalancerRequest request = new CreateLoadBalancerRequest()
                .withLoadBalancerName(context.loadBalancerStateExpanded.name)
                .withListeners(buildListeners(context))
                .withSubnets(context.loadBalancerStateExpanded.subnets.stream()
                        .map(subnet -> subnet.id).collect(Collectors.toList()))
                .withSecurityGroups(context.securityGroupState.id);

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
        RegisterInstancesWithLoadBalancerRequest request =
                new RegisterInstancesWithLoadBalancerRequest();

        return request.withLoadBalancerName(context.loadBalancerStateExpanded.name)
                .withInstances(context.loadBalancerStateExpanded.computes.stream()
                        .map(compute -> new Instance(compute.id))
                        .collect(Collectors.toList()));
    }

    private DeferredResult<AWSLoadBalancerContext> deleteLoadBalancer(
            AWSLoadBalancerContext context) {
        DeleteLoadBalancerRequest request = new DeleteLoadBalancerRequest()
                .withLoadBalancerName(context.loadBalancerStateExpanded.name);

        String message = "Delete AWS Load Balancer with name ["
                + context.loadBalancerStateExpanded.name + "].";
        AWSDeferredResultAsyncHandler<DeleteLoadBalancerRequest, DeleteLoadBalancerResult> handler =
                new AWSDeferredResultAsyncHandler<DeleteLoadBalancerRequest,
                        DeleteLoadBalancerResult>(this, message) {
                    @Override
                    protected DeferredResult<DeleteLoadBalancerResult> consumeSuccess(
                            DeleteLoadBalancerRequest request, DeleteLoadBalancerResult result) {
                        return DeferredResult.completed(result);
                    }
                };

        context.client.deleteLoadBalancerAsync(request, handler);
        return handler.toDeferredResult()
                .thenApply(ignore -> context);
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
        SecurityGroupInstanceRequest req = buildSecurityGroupInstanceRequest(context
                .securityGroupState, InstanceRequestType.DELETE, context.request);

        Operation operation = Operation.createPatch(this,
                AWSSecurityGroupService.SELF_LINK)
                .setBody(req);
        return sendWithDeferredResult(operation, SecurityGroupState.class)
                .exceptionally(th -> {
                    // Delete requests should not fail, only log the problem.
                    this.logWarning("Unable to delete Security Group {}", context
                            .securityGroupState.name);
                    this.logFine(th.getMessage());
                    return null;
                })
                .thenApply(ignore -> context);
    }
}
