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

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_ELASTIC_IP_ALLOCATION_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_NAT_GATEWAY_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_ROUTE_TABLE_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory.returnClientManager;
import static com.vmware.photon.controller.model.util.PhotonModelUriUtils.createInventoryUri;

import java.net.URI;
import java.util.HashMap;
import java.util.UUID;

import com.vmware.photon.controller.model.adapterapi.SubnetInstanceRequest;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSNetworkClient;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.support.LifecycleState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Adapter for provisioning a subnet on AWS.
 */
public class AWSSubnetService extends StatelessService {
    public static final String SELF_LINK = AWSUriPaths.AWS_SUBNET_ADAPTER;

    /**
     * Subnet request context.
     */
    private static class AWSSubnetContext {

        final SubnetInstanceRequest request;

        EndpointState endpoint;
        AuthCredentialsServiceState credentials;
        SubnetState subnetState;
        NetworkState parentNetwork;
        SubnetState publicSubnet;

        String awsSubnetId;

        AWSNetworkClient client;

        TaskManager taskManager;

        AWSNATGatewayContext natSubContext;

        AWSSubnetContext(StatelessService service, SubnetInstanceRequest request) {
            this.request = request;
            this.taskManager = new TaskManager(service, request.taskReference,
                    request.resourceLink());
        }

        private static class AWSNATGatewayContext {
            String allocationId;
            String natGatewayId;
            String routeTableId;
        }
    }

    private AWSClientManager clientManager;

    /**
     * Extend default 'start' logic with loading AWS client.
     */
    @Override
    public void handleStart(Operation op) {

        this.clientManager = AWSClientManagerFactory
                .getClientManager(AWSConstants.AwsClientType.EC2);

        super.handleStart(op);
    }

    /**
     * Extend default 'stop' logic with releasing AWS client.
     */
    @Override
    public void handleStop(Operation op) {

        returnClientManager(this.clientManager, AWSConstants.AwsClientType.EC2);

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
        AWSSubnetContext context = new AWSSubnetContext(this,
                op.getBody(SubnetInstanceRequest.class));

        DeferredResult.completed(context)
                .thenCompose(this::populateContext)
                .thenCompose(this::handleSubnetInstanceRequest)
                .whenComplete((o, e) -> {
                    // Once done patch the calling task with correct stage.
                    if (e == null) {
                        context.taskManager.finishTask();
                    } else {
                        context.taskManager.patchTaskToFailure(e);
                    }
                });
    }

    private DeferredResult<AWSSubnetContext> populateContext(AWSSubnetContext context) {
        return DeferredResult.completed(context)
                .thenCompose(this::getSubnetState)
                .thenCompose(this::getNATContext)
                .thenCompose(this::getParentNetwork)
                .thenCompose(this::getPublicSubnet)
                .thenCompose(this::getEndpointState)
                .thenCompose(this::getCredentials)
                .thenCompose(this::getAWSClient);
    }

    private DeferredResult<AWSSubnetContext> getSubnetState(AWSSubnetContext context) {
        return this.sendWithDeferredResult(
                Operation.createGet(context.request.resourceReference),
                SubnetState.class)
                .thenApply(subnetState -> {
                    context.subnetState = subnetState;
                    return context;
                });
    }

    private DeferredResult<AWSSubnetContext> getNATContext(AWSSubnetContext context) {
        if (context.subnetState == null || context.subnetState.customProperties == null) {
            return DeferredResult.completed(context);
        }

        String allocationId = context.subnetState.customProperties.get(
                AWS_ELASTIC_IP_ALLOCATION_ID);
        String natGatewayId = context.subnetState.customProperties.get(
                AWS_NAT_GATEWAY_ID);
        String routeTableId = context.subnetState.customProperties.get(
                AWS_ROUTE_TABLE_ID);

        if (allocationId != null || natGatewayId != null || routeTableId != null) {
            context.natSubContext = new AWSSubnetContext.AWSNATGatewayContext();
            context.natSubContext.allocationId = allocationId;
            context.natSubContext.natGatewayId = natGatewayId;
            context.natSubContext.routeTableId = routeTableId;
        }

        return DeferredResult.completed(context);
    }

    private DeferredResult<AWSSubnetContext> getParentNetwork(AWSSubnetContext context) {
        URI uri = context.request.buildUri(context.subnetState.networkLink);
        return this.sendWithDeferredResult(
                Operation.createGet(uri),
                NetworkState.class)
                .thenApply(parentNetwork -> {
                    context.parentNetwork = parentNetwork;
                    return context;
                });
    }

    private DeferredResult<AWSSubnetContext> getPublicSubnet(AWSSubnetContext context) {
        if (context.subnetState.externalSubnetLink == null) {
            return DeferredResult.completed(context);
        }

        URI uri = context.request.buildUri(context.subnetState.externalSubnetLink);
        return this.sendWithDeferredResult(
                Operation.createGet(uri),
                SubnetState.class)
                .thenApply(publicSubnet -> {
                    context.publicSubnet = publicSubnet;
                    return context;
                });
    }

    private DeferredResult<AWSSubnetContext> getEndpointState(AWSSubnetContext context) {
        URI uri = context.request.buildUri(context.subnetState.endpointLink);
        return this.sendWithDeferredResult(
                Operation.createGet(uri),
                EndpointState.class)
                .thenApply(endpointState -> {
                    context.endpoint = endpointState;
                    return context;
                });
    }

    private DeferredResult<AWSSubnetContext> getCredentials(AWSSubnetContext context) {
        URI uri = createInventoryUri(this.getHost(), context.endpoint.authCredentialsLink);
        return this.sendWithDeferredResult(
                Operation.createGet(uri),
                AuthCredentialsServiceState.class)
                .thenApply(authCredentialsServiceState -> {
                    context.credentials = authCredentialsServiceState;
                    return context;
                });
    }

    private DeferredResult<AWSSubnetContext> getAWSClient(AWSSubnetContext context) {
        if (context.request.isMockRequest) {
            return DeferredResult.completed(context);
        }

        DeferredResult<AWSSubnetContext> r = new DeferredResult<>();
        context.client = new AWSNetworkClient(this,
                this.clientManager.getOrCreateEC2Client(
                        context.credentials, context.parentNetwork.regionId,
                        this, (t) -> r.fail(t)));
        if (context.client != null) {
            r.complete(context);
        }
        return r;
    }

    private DeferredResult<AWSSubnetContext> handleSubnetInstanceRequest(AWSSubnetContext context) {

        DeferredResult<AWSSubnetContext> execution = DeferredResult.completed(context);

        switch (context.request.requestType) {
        case CREATE:
            if (context.request.isMockRequest) {
                // no need to go to the end-point; just generate random subnet id and zoneId
                context.subnetState.zoneId = context.awsSubnetId = UUID.randomUUID().toString();
                execution = execution.thenCompose(this::updateSubnetState);
            } else {
                execution = execution
                        .thenCompose(this::createSubnet)
                        .thenCompose(this::updateSubnetState)
                        .thenCompose(this::nameTagSubnet)
                        .thenCompose(this::provideOutboundAccess);
            }

            return execution;

        case DELETE:
            if (context.request.isMockRequest) {
                // no need to go to the end-point
                this.logFine("Mock request to delete an AWS subnet ["
                        + context.subnetState.name + "] processed.");
            } else {
                execution = execution
                        .thenCompose(this::deleteSubnet)
                        .thenCompose(this::deleteNATResources);
            }

            return execution.thenCompose(this::deleteSubnetState);
        default:
            IllegalStateException ex = new IllegalStateException("unsupported request type");
            return DeferredResult.failed(ex);
        }
    }

    private DeferredResult<AWSSubnetContext> createSubnet(AWSSubnetContext context) {
        return context.client.createSubnetAsync(
                context.subnetState.subnetCIDR,
                context.parentNetwork.id,
                context.subnetState.zoneId)
                .thenApply(subnet -> {
                    context.awsSubnetId = subnet.getSubnetId();
                    context.subnetState.zoneId = context.subnetState.zoneId == null ?
                            subnet.getAvailabilityZone() : context.subnetState.zoneId;
                    return context;
                });
    }

    private DeferredResult<AWSSubnetContext> nameTagSubnet(AWSSubnetContext context) {
        return context.client.createNameTagAsync(context.awsSubnetId, context.subnetState.name)
                .thenApply(none -> context);
    }

    private DeferredResult<AWSSubnetContext> updateSubnetState(AWSSubnetContext context) {
        context.subnetState.id = context.awsSubnetId;
        context.subnetState.lifecycleState = LifecycleState.READY;

        addNATCustomProperties(context.natSubContext, context.subnetState);

        return this.sendWithDeferredResult(
                Operation.createPatch(this, context.subnetState.documentSelfLink)
                        .setBody(context.subnetState))
                .thenApply(op -> context);
    }

    private DeferredResult<AWSSubnetContext> deleteSubnet(AWSSubnetContext context) {
        return context.client.deleteSubnetAsync(context.subnetState.id)
                .thenApply((result) -> context);
    }

    private DeferredResult<AWSSubnetContext> deleteNATResources(AWSSubnetContext context) {
        if (context.natSubContext == null) {
            return DeferredResult.completed(context);
        }

        return DeferredResult.completed(context)
                .thenCompose(this::deleteNATGateway)
                .thenCompose(this::deleteRouteTable)
                .thenCompose(this::releaseIPAddress)
                .thenApply((result) -> context);
    }

    private DeferredResult<AWSSubnetContext> deleteNATGateway(AWSSubnetContext context) {
        if (context.natSubContext.natGatewayId == null) {
            return DeferredResult.completed(context);
        }

        return context.client.deleteNATGateway(context.natSubContext.natGatewayId,
                context.taskManager, context.subnetState.documentExpirationTimeMicros)
                .thenApply((result) -> context);
    }

    private DeferredResult<AWSSubnetContext> deleteRouteTable(AWSSubnetContext context) {
        if (context.natSubContext.routeTableId == null) {
            return DeferredResult.completed(context);
        }

        return context.client.deleteRouteTable(context.natSubContext.routeTableId)
                .thenApply((result) -> context);
    }

    private DeferredResult<AWSSubnetContext> releaseIPAddress(AWSSubnetContext context) {
        if (context.natSubContext.allocationId == null) {
            return DeferredResult.completed(context);
        }

        return context.client.releaseElasticIPAddress(context.natSubContext.allocationId)
                .thenApply((result) -> context);
    }

    private DeferredResult<AWSSubnetContext> deleteSubnetState(AWSSubnetContext context) {
        return this.sendWithDeferredResult(
                Operation.createDelete(this, context.subnetState.documentSelfLink))
                .thenApply(operation -> context);
    }

    /**
     * Provides outbound access for the newly created subnet. That requires 5 steps:
     * 1. allocate an elastic IP for a NAT gateway (temporary).
     * 2. create a NAT gateway.
     * 3. create a new route table.
     * 4. associate the subnet to the new route table.
     * 5. add a route for Internet traffic to the NAT gateway.
     */
    private DeferredResult<AWSSubnetContext> provideOutboundAccess(AWSSubnetContext context) {
        DeferredResult<AWSSubnetContext> execution = DeferredResult.completed(context);

        if (context.publicSubnet == null) {
            return execution;
        }

        return execution
                .thenCompose(this::getAddressAllocationId)
                .thenCompose(this::createRouteTable)
                .thenCompose(this::createNATGateway)
                .thenCompose(this::associateSubnetToRouteTable)
                .thenCompose(this::addRouteToNatGateway);
    }

    /**
     * Allocates an elastic IP address for the NAT gateway
     * Note: this is temporary. Eventually, this address will be allocated outside this service.
     */
    private DeferredResult<AWSSubnetContext> getAddressAllocationId(AWSSubnetContext context) {
        return context.client.allocateElasticIPAddress()
                .thenApply(allocationId -> {
                    context.natSubContext = new AWSSubnetContext.AWSNATGatewayContext();
                    context.natSubContext.allocationId = allocationId;
                    return context;
                })
                .thenCompose(this::updateSubnetState);
    }

    /**
     * Creates a NAT gateway
     */
    private DeferredResult<AWSSubnetContext> createNATGateway(AWSSubnetContext context) {
        return context.client.createNatGateway(context.publicSubnet.id,
                context.natSubContext.allocationId, context.taskManager,
                context.subnetState.documentExpirationTimeMicros)
                .thenApply(natGatewayId -> {
                    context.natSubContext.natGatewayId = natGatewayId;
                    return context;
                })
                .thenCompose(this::updateSubnetState);
    }

    /**
     * Creates a new route table
     */
    private DeferredResult<AWSSubnetContext> createRouteTable(AWSSubnetContext context) {
        return context.client.createRouteTable(context.parentNetwork.id)
                .thenApply(routeTableId -> {
                    context.natSubContext.routeTableId = routeTableId;
                    return context;
                })
                .thenCompose(this::updateSubnetState);
    }

    /**
     * Associates the newly provisioned subnet to the route table
     */
    private DeferredResult<AWSSubnetContext> associateSubnetToRouteTable(AWSSubnetContext context) {
        return context.client.associateSubnetToRouteTable(context.natSubContext.routeTableId,
                context.awsSubnetId).thenApply(ignore -> context);
    }

    /**
     * Adds route for Internet traffic to NAT gateway
     */
    private DeferredResult<AWSSubnetContext> addRouteToNatGateway(AWSSubnetContext context) {
        return context.client.addRouteToNatGateway(context.natSubContext.routeTableId,
                context.natSubContext.natGatewayId).thenApply(ignore -> context);
    }

    /**
     * Adds NAT-specific data to the subnet's custom properties
     */
    private void addNATCustomProperties(AWSSubnetContext.AWSNATGatewayContext natContext,
            SubnetState subnetState) {
        if (natContext == null) {
            return;
        }

        if (subnetState.customProperties == null) {
            subnetState.customProperties = new HashMap<>();
        }

        if (natContext.allocationId != null) {
            subnetState.customProperties.put(AWS_ELASTIC_IP_ALLOCATION_ID, natContext.allocationId);
        }

        if (natContext.natGatewayId != null) {
            subnetState.customProperties.put(AWS_NAT_GATEWAY_ID, natContext.natGatewayId);
        }

        if (natContext.routeTableId != null) {
            subnetState.customProperties.put(AWS_ROUTE_TABLE_ID, natContext.routeTableId);
        }
    }
}
