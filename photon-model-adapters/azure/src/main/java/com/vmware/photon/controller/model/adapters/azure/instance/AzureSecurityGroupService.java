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
import java.util.stream.Collectors;

import com.microsoft.azure.management.network.implementation.NetworkSecurityGroupInner;
import com.microsoft.azure.management.network.implementation.NetworkSecurityGroupsInner;

import com.vmware.photon.controller.model.adapterapi.SecurityGroupInstanceRequest;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureBaseAdapterContext;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureDeferredResultServiceCallback;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureDeferredResultServiceCallback.Default;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureSecurityGroupUtils;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext.BaseAdapterStage;
import com.vmware.photon.controller.model.query.QueryUtils.QueryTop;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.util.AssertUtil;
import com.vmware.photon.controller.model.util.ClusterUtil.ServiceTypeCluster;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask.Query;

/**
 * Adapter to create/delete a security group on Azure.
 */
public class AzureSecurityGroupService extends StatelessService {

    public static final String SELF_LINK = AzureUriPaths.AZURE_SECURITY_GROUP_ADAPTER;

    /**
     * Security group request context.
     */
    private static class AzureSecurityGroupContext extends
            AzureBaseAdapterContext<AzureSecurityGroupContext> {

        final SecurityGroupInstanceRequest securityGroupRequest;

        SecurityGroupState securityGroupState;
        ResourceGroupState securityGroupRGState;

        NetworkState networkState;
        ResourceGroupState networkRGState;

        NetworkSecurityGroupInner securityGroup;

        AzureSecurityGroupContext(
                AzureSecurityGroupService service,
                ExecutorService executorService,
                SecurityGroupInstanceRequest request) {

            super(service, executorService, request);

            this.securityGroupRequest = request;
        }

        /**
         * Get the authentication object using security group authentication.
         */
        @Override
        protected URI getParentAuthRef(AzureSecurityGroupContext context) {
            return UriUtils.buildUri(
                    context.service.getHost(),
                    context.securityGroupState.authCredentialsLink);
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
        AzureSecurityGroupContext context = new AzureSecurityGroupContext(
                this,
                this.executorService,
                op.getBody(SecurityGroupInstanceRequest.class));

        // Immediately complete the Operation from calling task.
        op.complete();

        DeferredResult.completed(context)
                .thenCompose(this::populateContext)
                .thenCompose(this::handleSecurityGroupInstanceRequest)
                // Once done patch the calling task with correct stage.
                .whenComplete((o, e) -> context.finish(e));
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
                .thenCompose(this::getAzureClient);
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
                /*
                 * ignore tenantLinks for this query; group link and endpoint link (which is
                 * implicitly tenanted) should be sufficient to uniquely identify the resource group
                 */
                null)
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
        String networkStateId = context.securityGroupRequest.customProperties
                .get(NETWORK_STATE_ID_PROP_NAME);
        AssertUtil.assertNotNull(networkStateId,
                "context.request.customProperties doesn't contain the network state id.");
        AssertUtil.assertNotNull(context.securityGroupState.endpointLink,
                "context.securityGroupState.endpointLink is null.");

        // use the network state id to find the resource groups associated to the network being
        // isolated by this security group
        Query query = Query.Builder.create()
                .addKindFieldClause(NetworkState.class)
                .addFieldClause(NetworkState.FIELD_NAME_ID, networkStateId)
                .build();

        QueryTop<NetworkState> queryNetworkStates = new QueryTop<>(
                this.getHost(),
                query,
                NetworkState.class,
                /*
                 * ignore tenantLinks for this query; network id and endpoint link (which is
                 * implicitly tenanted) should be sufficient to uniquely identify the network
                 */
                null,
                context.securityGroupState.endpointLink);
        queryNetworkStates.setMaxResultsLimit(1);
        queryNetworkStates.setClusterType(ServiceTypeCluster.INVENTORY_SERVICE);

        return queryNetworkStates.collectDocuments(Collectors.toList())
                .thenApply(networkStates -> {
                    AssertUtil.assertNotNull(networkStates, "Network state with id [" +
                            networkStateId + "] was not found.");
                    context.networkState = networkStates.get(0);
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
                    AssertUtil.assertNotNull(resourceGroupState, "Unable to identify a "
                            + "suitable resource group for security group [" +
                            context.securityGroupState.name + "]");
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

    private DeferredResult<AzureSecurityGroupContext> getAzureClient(
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
                return execution
                        .thenCompose(this::updateSecurityGroupState);
            } else {
                return execution
                        .thenCompose(this::createSecurityGroup)
                        .thenCompose(this::updateSecurityGroupState)
                        .thenCompose(this::updateRules);
            }
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

    private DeferredResult<AzureSecurityGroupContext> createSecurityGroup(
            AzureSecurityGroupContext context) {

        String rgName = context.securityGroupRGState != null ? context.securityGroupRGState.name
                : context.networkRGState.name;

        final String msg = "Creating Azure Security Group [" + context.securityGroupState.name
                + "] in resource group [" + rgName + "].";

        NetworkSecurityGroupsInner azureSecurityGroupClient = context.azureSdkClients
                .getNetworkManagementClientImpl().networkSecurityGroups();

        return AzureSecurityGroupUtils.createSecurityGroup(this,
                azureSecurityGroupClient,
                context.securityGroupState, rgName,
                context.securityGroupState.regionId, msg)
                .thenApply(sg -> {
                    // Populate the security group id with Azure Network Security Group ID
                    context.securityGroupState.id = sg.id();
                    context.securityGroup = sg;
                    return context;
                });
    }

    private DeferredResult<AzureSecurityGroupContext> updateRules(
            AzureSecurityGroupContext context) {

        String rgName = context.securityGroupRGState != null ? context.securityGroupRGState.name
                : context.networkRGState.name;

        final String msg = "Adding Azure Security Rules to Group ["
                + context.securityGroupState.name + "] in resource group [" + rgName + "].";

        NetworkSecurityGroupsInner azureSecurityGroupClient = context.azureSdkClients
                .getNetworkManagementClientImpl().networkSecurityGroups();

        return AzureSecurityGroupUtils.addSecurityRules(this,
                azureSecurityGroupClient,
                context.securityGroupState, rgName,
                context.securityGroup, msg)
                .thenApply(__ -> context);
    }

    private DeferredResult<AzureSecurityGroupContext> deleteSecurityGroup(
            AzureSecurityGroupContext context) {

        String rgName = context.securityGroupRGState != null ? context.securityGroupRGState.name
                : context.networkRGState.name;
        String securityGroupName = context.securityGroupState.name;

        final String msg = "Deleting Azure Security Group [" + securityGroupName
                + "] in resource group [" + rgName + "].";

        NetworkSecurityGroupsInner azureSecurityGroupClient = context.azureSdkClients
                .getNetworkManagementClientImpl().networkSecurityGroups();

        AzureDeferredResultServiceCallback<Void> handler = new Default<>(this, msg);

        azureSecurityGroupClient.deleteAsync(rgName, securityGroupName, handler);

        return handler.toDeferredResult().thenApply(ignore -> context);
    }

    private DeferredResult<AzureSecurityGroupContext> deleteSecurityGroupState(
            AzureSecurityGroupContext context) {
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
