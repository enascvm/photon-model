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

import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSSecurityGroupClient.DEFAULT_SECURITY_GROUP_DESC;
import static com.vmware.photon.controller.model.tasks.ProvisionSecurityGroupTaskService.NETWORK_STATE_ID_PROP_NAME;
import static com.vmware.photon.controller.model.util.PhotonModelUriUtils.createInventoryUri;

import java.net.URI;
import java.util.HashMap;
import java.util.UUID;

import com.vmware.photon.controller.model.adapterapi.SecurityGroupInstanceRequest;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSSecurityGroupClient;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Security group service for AWS. AWS Security Groups will be the
 * primary artifact created and managed.
 */
public class AWSSecurityGroupService extends StatelessService {
    public static final String SELF_LINK = AWSUriPaths.AWS_SECURITY_GROUP_ADAPTER;
    public static final String SECURITY_GROUP_ID = "awsSecurityGroupID";

    private AWSClientManager clientManager;

    /**
     * Security Group request stages.
     */
    public static class AWSSecurityGroupContext {

        public AWSSecurityGroupClient client;
        public AuthCredentialsServiceState credentials;
        public SecurityGroupInstanceRequest request;
        public SecurityGroupState securityGroup;
        public String securityGroupId;
        public Throwable error;
        TaskManager taskManager;
        public Operation operation;

        AWSSecurityGroupContext(StatelessService service, Operation operation,
                SecurityGroupInstanceRequest request) {
            this.request = request;
            if (request.taskReference != null) {
                this.taskManager = new TaskManager(service, request.taskReference,
                        request.resourceLink());
            }

            this.operation = operation;
        }

        public boolean isAsync() {
            return this.taskManager != null;
        }
    }

    /**
     * Extend default 'start' logic with loading AWS client.
     */
    @Override
    public void handleStart(Operation op) {
        this.clientManager = AWSClientManagerFactory
                .getClientManager(AWSConstants.AwsClientType.EC2);

        super.handleStart(op);
    }

    @Override
    public void handleStop(Operation op) {
        AWSClientManagerFactory.returnClientManager(this.clientManager,
                AWSConstants.AwsClientType.EC2);
        super.handleStop(op);
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        SecurityGroupInstanceRequest request = op.getBody(SecurityGroupInstanceRequest.class);

        // initialize request context
        AWSSecurityGroupContext context = new AWSSecurityGroupContext(this, op, request);
        if (context.isAsync()) {
            // Immediately complete the Operation from calling task.
            op.complete();
        }

        DeferredResult.completed(context)
                .thenCompose(this::populateContext)
                .thenCompose(this::handleSecurityGroupInstanceRequest)
                .whenComplete((o, e) -> {
                    // Once done patch the calling task with correct stage.
                    if (e == null) {
                        if (context.isAsync()) {
                            context.taskManager.finishTask();
                        } else {
                            context.operation.setBody(context.securityGroup);
                            context.operation.complete();
                        }
                    } else {
                        if (context.isAsync()) {
                            context.taskManager.patchTaskToFailure(e);
                        } else {
                            context.operation.fail(e);
                        }
                    }
                });

    }

    private DeferredResult<AWSSecurityGroupContext> populateContext(
            AWSSecurityGroupContext context) {
        return DeferredResult.completed(context)
                .thenCompose(this::getSecurityGroup)
                .thenCompose(this::getCredentials)
                .thenCompose(this::getAWSClient);
    }

    public DeferredResult<AWSSecurityGroupContext> getSecurityGroup(
            AWSSecurityGroupContext context) {
        return this.sendWithDeferredResult(
                Operation.createGet(context.request.resourceReference),
                SecurityGroupState.class)
                .thenApply(securityGroup -> {
                    context.securityGroup = securityGroup;
                    context.securityGroupId = securityGroup.id;
                    return context;
                });
    }

    private DeferredResult<AWSSecurityGroupContext> getCredentials(
            AWSSecurityGroupContext context) {
        URI uri = createInventoryUri(this.getHost(), context.securityGroup.authCredentialsLink);
        return this.sendWithDeferredResult(
                Operation.createGet(uri),
                AuthCredentialsServiceState.class)
                .thenApply(authCredentialsServiceState -> {
                    context.credentials = authCredentialsServiceState;
                    return context;
                });
    }

    private DeferredResult<AWSSecurityGroupContext> getAWSClient(AWSSecurityGroupContext context) {
        if (context.request.isMockRequest) {
            return DeferredResult.completed(context);
        }

        DeferredResult<AWSSecurityGroupContext> r = new DeferredResult<>();
        this.clientManager.getOrCreateEC2ClientAsync(context.credentials,
                context.securityGroup.regionId, this)
                .whenComplete((client, t) -> {
                    if (t != null) {
                        r.fail(t);
                        return;
                    }

                    context.client = new AWSSecurityGroupClient(this, client);
                    r.complete(context);
                });
        return r;
    }

    private DeferredResult<AWSSecurityGroupContext> handleSecurityGroupInstanceRequest(
            AWSSecurityGroupContext context) {

        DeferredResult<AWSSecurityGroupContext> execution = DeferredResult.completed(context);

        switch (context.request.requestType) {
        case CREATE:
            if (context.request.isMockRequest) {
                // no need to go the end-point; just generate AWS Security Group Id.
                context.securityGroupId = UUID.randomUUID().toString();
            } else {
                execution = execution
                        .thenCompose(this::createSecurityGroup)
                        .thenCompose(this::updateRules);
            }

            return execution;

        case DELETE:
            if (context.request.isMockRequest) {
                // no need to go to the end-point
                this.logFine("Mock request to delete an AWS security group ["
                        + context.securityGroup.name + "] processed.");
            } else {
                execution = execution.thenCompose(this::deleteSecurityGroup);
            }

            return execution.thenCompose(this::deleteSecurityGroupState);
        default:
            IllegalStateException ex = new IllegalStateException("unsupported request type");
            return DeferredResult.failed(ex);
        }
    }

    public DeferredResult<AWSSecurityGroupContext> createSecurityGroup(
            AWSSecurityGroupContext context) {
        String vpcId = getCustomProperty(context, AWSConstants.AWS_VPC_ID);
        vpcId = (vpcId == null &&
                context.request.customProperties != null) ?
                context.request.customProperties.get(NETWORK_STATE_ID_PROP_NAME) :
                vpcId;

        return context.client.createSecurityGroupAsync(context.securityGroup.name,
                context.securityGroup.desc != null ?
                        context.securityGroup.desc : DEFAULT_SECURITY_GROUP_DESC,
                vpcId)
                .thenApply(sgId -> {
                    context.securityGroup.id = context.securityGroupId = sgId;
                    return context;
                })
                .thenCompose(ctx -> updateSecurityGroupProperties(context.securityGroup,
                        SECURITY_GROUP_ID, context.securityGroupId)
                        .thenApply(sg -> context));
    }

    public DeferredResult<AWSSecurityGroupContext> updateRules(
            AWSSecurityGroupContext context) {
        return context.client.updateIngressRules(context.securityGroup.ingress,
                context.securityGroupId)
                .thenCompose(v -> context.client.updateEgressRules(context.securityGroup.egress,
                        context.securityGroupId))
                .thenCompose(v -> addInnerRules(context))
                .thenApply(v -> context);
    }

    private String getCustomProperty(AWSSecurityGroupContext requestState,
            String key) {
        if (requestState.securityGroup.customProperties != null) {
            return requestState.securityGroup.customProperties.get(key);
        }
        return null;
    }

    private DeferredResult<SecurityGroupState> updateSecurityGroupProperties(
            SecurityGroupState securityGroup, String key, String value) {
        if (securityGroup.customProperties == null) {
            securityGroup.customProperties = new HashMap<>();
        }

        securityGroup.customProperties.put(key, value);

        return this.sendWithDeferredResult(Operation.createPatch(this,
                securityGroup.documentSelfLink).setBody(securityGroup))
                .thenApply(o -> o.getBody(SecurityGroupState.class));
    }

    public DeferredResult<AWSSecurityGroupContext> deleteSecurityGroup(
            AWSSecurityGroupContext context) {

        return context.client.deleteSecurityGroupAsync(context.securityGroupId)
                .thenApply(v -> context);
    }

    public DeferredResult<AWSSecurityGroupContext> deleteSecurityGroupState(
            AWSSecurityGroupContext context) {
        return this.sendWithDeferredResult(
                Operation.createDelete(this, context.securityGroup.documentSelfLink))
                .thenApply(operation -> context);
    }

    /**
     * Adds the rules for internal communication among group members
     */
    public DeferredResult<Void> addInnerRules(AWSSecurityGroupContext context) {
        return context.client.addInnerIngressRule(context.securityGroupId)
                .thenCompose(v -> context.client.addInnerEgressRule(context.securityGroupId));
    }
}
