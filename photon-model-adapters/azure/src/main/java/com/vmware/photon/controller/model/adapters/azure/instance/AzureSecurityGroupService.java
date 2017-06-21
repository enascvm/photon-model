/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.model.adapters.azure.instance;

import static com.vmware.photon.controller.model.tasks.ProvisionSecurityGroupTaskService.NETWORK_STATE_ID_PROP_NAME;

import java.net.URI;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import com.microsoft.azure.management.network.implementation.NetworkSecurityGroupsInner;

import com.vmware.photon.controller.model.adapterapi.SecurityGroupInstanceRequest;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureDeferredResultServiceCallback;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureSdkClients;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureSecurityGroupUtils;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext.BaseAdapterStage;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.util.AssertUtil;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;

/**
 * Adapter to create/delete a security group on Azure.
 */
public class AzureSecurityGroupService extends StatelessService {

    public static final String SELF_LINK = AzureUriPaths.AZURE_SECURITY_GROUP_ADAPTER;

    /**
     * Security group request context.
     */
    private static class AzureSecurityGroupContext extends
            BaseAdapterContext<AzureSecurityGroupContext> {

        final SecurityGroupInstanceRequest securityGroupRequest;

        AzureSdkClients azureSdkClients;
        NetworkSecurityGroupsInner azureSecurityGroupClient;

        SecurityGroupState securityGroupState;
        ResourceGroupState securityGroupRGState;

        NetworkState networkState;
        ResourceGroupState networkRGState;

        AzureSecurityGroupContext(AzureSecurityGroupService service,
                SecurityGroupInstanceRequest request) {
            super(service, request);

            this.securityGroupRequest = request;
        }

        @Override
        protected URI getParentAuthRef(AzureSecurityGroupContext context) {
            return UriUtils.buildUri(
                    context.service.getHost(),
                    context.securityGroupState.authCredentialsLink);
        }

        public void close() {
            if (this.azureSdkClients != null) {
                this.azureSdkClients.close();
                this.azureSdkClients = null;
            }
        }
    }

    private ExecutorService executorService;

    @Override
    public void handleStart(Operation op) {

        this.executorService = getHost().allocateExecutor(this);

        super.handleStart(op);
    }

    @Override
    public void handleStop(Operation op) {
        this.executorService.shutdown();
        AdapterUtils.awaitTermination(this.executorService);

        super.handleStop(op);
    }

    @Override
    public void handlePatch(Operation op) {

        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }

        // initialize context object
        AzureSecurityGroupContext context = new AzureSecurityGroupContext(this,
                op.getBody(SecurityGroupInstanceRequest.class));

        // Immediately complete the Operation from calling task.
        op.complete();

        DeferredResult.completed(context)
                .thenCompose(this::populateContext)
                .thenCompose(this::handleSecurityGroupInstanceRequest)
                .whenComplete((o, e) -> {
                    // Once done patch the calling task with correct stage.
                    if (e == null) {
                        context.taskManager.finishTask();
                    } else {
                        context.taskManager.patchTaskToFailure(e);
                    }
                    context.close();
                });
    }

    private DeferredResult<AzureSecurityGroupContext> populateContext(
            AzureSecurityGroupContext context) {
        return DeferredResult.completed(context)
                // get security group from request.resourceReference
                .thenCompose(this::getSecurityGroupState)
                // get the Azure resource group from security group (if any)
                .thenCompose(this::getSecurityGroupRGState)
                // get the network state for which the security group is created
                .thenCompose(this::getNetworkState)
                // get the Azure resource group for the network
                .thenCompose(this::getNetworkRGState)
                // create the Azure clients
                .thenCompose(this::getCredentials)
                .thenCompose(this::getAzureClients);
    }

    private DeferredResult<AzureSecurityGroupContext> getSecurityGroupState(
            AzureSecurityGroupContext context) {
        return this.sendWithDeferredResult(
                Operation.createGet(context.securityGroupRequest.resourceReference),
                SecurityGroupState.class)
                .thenApply(securityGroupState -> {
                    context.securityGroupState = securityGroupState;
                    return context;
                });
    }

    private DeferredResult<AzureSecurityGroupContext> getSecurityGroupRGState(
            AzureSecurityGroupContext context) {
        AssertUtil.assertNotNull(context.securityGroupState.groupLinks,
                "context.securityGroupState.groupLinks is null.");

        return AzureUtils.filterRGsByType(getHost(),
                context.securityGroupState.groupLinks, context.securityGroupState.endpointLink,
                context.securityGroupState.tenantLinks)
                .thenApply(resourceGroupState -> {
                    context.securityGroupRGState = resourceGroupState;
                    return context;
                });
    }

    private DeferredResult<AzureSecurityGroupContext> getNetworkState(
            AzureSecurityGroupContext context) {
        if (context.securityGroupRGState != null) {
            // no need to get network state; the resource group was already identified
            return DeferredResult.completed(context);
        }

        // on Azure, we place the isolation security group on the same resource group as the
        // network being isolated; we find that by extracting the network state id which is passed
        // as a custom property of the request, and then find the appropriate resource group that
        // is linked to that network.

        AssertUtil.assertNotNull(context.securityGroupRequest.customProperties,
                "context.request.customProperties is null.");
        String networkStateId = context.securityGroupRequest.customProperties.get(NETWORK_STATE_ID_PROP_NAME);
        AssertUtil.assertNotNull(networkStateId,
                "context.request.customProperties doesn't contain the network state id.");

        // use the network state id to find the resource groups associated to the network being
        // isolated by this security group
        Query query = Query.Builder.create()
                .addKindFieldClause(NetworkState.class)
                .addFieldClause(NetworkState.FIELD_NAME_ID, networkStateId)
                .build();
        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .setQuery(query)
                .addOption(QueryOption.EXPAND_CONTENT)
                .addOption(QueryOption.TOP_RESULTS)
                .setResultLimit(1)
                .build();

        return QueryUtils.startQueryTask(this, queryTask)
                .thenApply(qrt -> {
                    AssertUtil.assertTrue(qrt.results.documents.values().size() == 1,
                            "Network state with id [" + networkStateId +
                                    "] was not uniquely identified");
                    context.networkState = Utils
                            .fromJson(qrt.results.documents.values().iterator().next(),
                                    NetworkState.class);
                    return context;
                });
    }

    private DeferredResult<AzureSecurityGroupContext> getNetworkRGState(
            AzureSecurityGroupContext context) {
        if (context.securityGroupRGState != null) {
            // no need to get network RG state; the resource group was already identified
            return DeferredResult.completed(context);
        }

        AssertUtil.assertNotNull(context.networkState.groupLinks,
                "context.networkState.groupLinks is null.");

        return AzureUtils.filterRGsByType(getHost(),
                context.networkState.groupLinks, context.networkState.endpointLink,
                context.networkState.tenantLinks)
                .thenApply(resourceGroupState -> {
                    context.networkRGState = resourceGroupState;
                    // add link to this resource group to the security group (if it doesn't exist)
                    // this is necessary in order for this security group to be placed in the right
                    // resource group in Azure
                    if (context.securityGroupState.groupLinks == null) {
                        context.securityGroupState.groupLinks = new HashSet<>();
                    }
                    context.securityGroupState.groupLinks.add(
                            context.networkRGState.documentSelfLink);

                    return context;
                });
    }

    private DeferredResult<AzureSecurityGroupContext> getCredentials(
            AzureSecurityGroupContext context) {
        return context.populateBaseContext(BaseAdapterStage.PARENTAUTH);
    }

    private DeferredResult<AzureSecurityGroupContext> handleSecurityGroupInstanceRequest(
            AzureSecurityGroupContext context) {

        DeferredResult<AzureSecurityGroupContext> execution = DeferredResult.completed(context);

        switch (context.securityGroupRequest.requestType) {
        case CREATE:
            if (context.securityGroupRequest.isMockRequest) {
                // no need to go the end-point; just generate Azure Security Group Id.
                context.securityGroupState.id = UUID.randomUUID().toString();
            } else {
                execution = execution
                        .thenCompose(this::createSecurityGroup);
            }

            return execution
                    .thenCompose(this::updateSecurityGroupState);

        case DELETE:
            if (context.securityGroupRequest.isMockRequest) {
                // no need to go to the end-point
                logFine(() -> String.format("Mock request to delete an Azure security group [%s] "
                                + "processed.", context.securityGroupState.name));
            } else {
                execution = execution.thenCompose(this::deleteSecurityGroup);
            }

            return execution.thenCompose(this::deleteSecurityGroupState);
        default:
            IllegalStateException ex = new IllegalStateException("unsupported request type");
            return DeferredResult.failed(ex);
        }
    }

    private DeferredResult<AzureSecurityGroupContext> getAzureClients(AzureSecurityGroupContext ctx) {
        if (ctx.securityGroupRequest.isMockRequest) {
            return DeferredResult.completed(ctx);
        }

        if (ctx.azureSdkClients == null) {
            ctx.azureSdkClients = new AzureSdkClients(this.executorService, ctx.parentAuth);
            ctx.azureSecurityGroupClient = ctx.azureSdkClients.getNetworkManagementClientImpl()
                    .networkSecurityGroups();
        }

        return DeferredResult.completed(ctx);
    }

    private DeferredResult<AzureSecurityGroupContext> createSecurityGroup(
            AzureSecurityGroupContext context) {

        String rgName = context.securityGroupRGState != null ?
                context.securityGroupRGState.name : context.networkRGState.name;

        final String msg = "Creating Azure Security Group [" + context.securityGroupState.name
                + "] in resource group [" + rgName + "].";

        return AzureSecurityGroupUtils.createSecurityGroup(this,
                context.azureSecurityGroupClient,
                context.securityGroupState, rgName,
                context.securityGroupState.regionId, msg)
                .thenApply(sg -> {
                    // Populate the security group id with Azure Network Security Group ID
                    context.securityGroupState.id = sg.id();
                    return context;
                });
    }

    private DeferredResult<AzureSecurityGroupContext> deleteSecurityGroup(
            AzureSecurityGroupContext context) {

        String rgName = context.securityGroupRGState != null ?
                context.securityGroupRGState.name : context.networkRGState.name;
        String securityGroupName = context.securityGroupState.name;

        final String msg = "Deleting Azure Security Group [" + securityGroupName
                + "] in resource group [" + rgName + "].";

        AzureDeferredResultServiceCallback<Void> handler =
                new AzureDeferredResultServiceCallback<Void>(this, msg) {
                    @Override
                    protected DeferredResult<Void> consumeSuccess(Void result) {
                        return DeferredResult.completed(null);
                    }
                };
        context.azureSecurityGroupClient.deleteAsync(rgName, securityGroupName, handler);
        return handler.toDeferredResult()
                .thenApply(ignore -> context);
    }

    private DeferredResult<AzureSecurityGroupContext> deleteSecurityGroupState(AzureSecurityGroupContext context) {
        return this.sendWithDeferredResult(
                Operation.createDelete(this, context.securityGroupState.documentSelfLink))
                .thenApply(operation -> context);
    }

    private DeferredResult<AzureSecurityGroupContext> updateSecurityGroupState(
            AzureSecurityGroupContext context) {

        return this.sendWithDeferredResult(Operation.createPatch(this,
                context.securityGroupState.documentSelfLink).setBody(context.securityGroupState))
                .thenApply(o -> context);
    }
}
