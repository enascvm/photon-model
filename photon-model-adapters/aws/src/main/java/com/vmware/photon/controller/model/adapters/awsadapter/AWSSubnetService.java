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
import static com.vmware.photon.controller.model.adapters.util.AdapterUtils.sendPatchToTask;

import java.net.URI;
import java.util.UUID;

import com.vmware.photon.controller.model.adapterapi.ResourceOperationResponse;
import com.vmware.photon.controller.model.adapterapi.SubnetInstanceRequest;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSNetworkClient;
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

        Throwable error;

        String awsSubnetId;

        AWSNetworkClient client;

        AWSSubnetContext(SubnetInstanceRequest request) {
            this.request = request;
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
        AWSSubnetContext context = new AWSSubnetContext(
                op.getBody(SubnetInstanceRequest.class));


        DeferredResult.completed(context)
                .thenCompose(this::populateContext)
                .thenCompose(this::handleSubnetInstanceRequest)
                .whenComplete((o, e) -> {
                    // Once done patch the calling task with correct stage.
                    if (e == null) {
                        sendPatchToTask(this, context.request.taskReference,
                                ResourceOperationResponse.finish(context.request.resourceLink
                                        ()));
                    } else {
                        sendPatchToTask(this, context.request.taskReference,
                                ResourceOperationResponse.fail(context.request.resourceLink(), e));
                    }
                });
    }

    private DeferredResult<AWSSubnetContext> populateContext(AWSSubnetContext context) {
        return DeferredResult.completed(context)
                .thenCompose(this::getSubnetState)
                .thenCompose(this::getParentNetwork)
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
        URI uri = context.request.buildUri(context.endpoint.authCredentialsLink);
        return this.sendWithDeferredResult(
                Operation.createGet(uri),
                AuthCredentialsServiceState.class)
                .thenApply(authCredentialsServiceState -> {
                    context.credentials = authCredentialsServiceState;
                    return context;
                });
    }

    private DeferredResult<AWSSubnetContext> getAWSClient(AWSSubnetContext context) {
        context.client = new AWSNetworkClient(this,
                this.clientManager.getOrCreateEC2Client(
                        context.credentials, context.parentNetwork.regionId,
                        this, context.request.taskReference, false));
        return DeferredResult.completed(context);
    }

    private DeferredResult<AWSSubnetContext> handleSubnetInstanceRequest(AWSSubnetContext context) {

        DeferredResult<AWSSubnetContext> execution = DeferredResult.completed(context);

        switch (context.request.requestType) {
        case CREATE:
            if (context.request.isMockRequest) {
                // no need to go the end-point; just generate AWS Subnet Id.
                context.awsSubnetId = UUID.randomUUID().toString();
            } else {
                execution = execution.thenCompose(this::createSubnet);
            }

            return execution.thenCompose(this::updateSubnetState);

        case DELETE:
            if (context.request.isMockRequest) {
                // no need to go to the end-point
            } else {
                execution = execution.thenCompose(this::deleteSubnet);
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
                context.parentNetwork.id)
                .thenApply(subnet -> {
                    context.awsSubnetId = subnet.getSubnetId();
                    return context;
                });
    }

    private DeferredResult<AWSSubnetContext> updateSubnetState(AWSSubnetContext context) {
        context.subnetState.id = context.awsSubnetId;
        context.subnetState.lifecycleState = LifecycleState.READY;

        return this.sendWithDeferredResult(
                Operation.createPatch(this, context.subnetState.documentSelfLink)
                        .setBody(context.subnetState))
                .thenApply(op -> context);
    }

    private DeferredResult<AWSSubnetContext> deleteSubnet(AWSSubnetContext context) {
        return context.client.deleteSubnetAsync(context.subnetState.id)
                .thenApply((result) -> context);
    }

    private DeferredResult<AWSSubnetContext> deleteSubnetState(AWSSubnetContext context) {
        return this.sendWithDeferredResult(
                Operation.createDelete(this, context.subnetState.documentSelfLink))
                .thenApply(operation -> context);
    }
}
