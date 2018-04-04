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

import static com.vmware.photon.controller.model.ComputeProperties.RESOURCE_GROUP_NAME;
import static com.vmware.photon.controller.model.util.PhotonModelUriUtils.createInventoryUri;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

import com.microsoft.azure.management.network.implementation.NetworkSecurityGroupInner;
import com.microsoft.azure.management.network.implementation.NetworkSecurityGroupsInner;
import com.microsoft.azure.management.resources.implementation.ResourceGroupInner;
import com.microsoft.azure.management.resources.implementation.ResourceGroupsInner;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapterapi.SecurityGroupInstanceRequest;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.ResourceGroupStateType;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureBaseAdapterContext;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureDeferredResultServiceCallback;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureDeferredResultServiceCallback.Default;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureSecurityGroupUtils;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext.BaseAdapterStage;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ResourceGroupService;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;

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
        ResourceGroupState resourceGroupState;

        // The RG the SG lands
        public ResourceGroupInner resourceGroup;

        NetworkSecurityGroupInner securityGroup;

        AzureSecurityGroupContext(
                AzureSecurityGroupService service,
                SecurityGroupInstanceRequest request) {

            super(service, request);

            this.securityGroupRequest = request;
        }

        /**
         * Get the authentication object using security group authentication.
         */
        @Override
        protected URI getParentAuthRef(AzureSecurityGroupContext context) {
            return UriUtils.buildUri(createInventoryUri(
                    context.service.getHost(),
                    context.securityGroupState.authCredentialsLink));
        }
    }

    @Override
    public void handlePatch(Operation op) {

        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }

        // initialize context object
        AzureSecurityGroupContext context = new AzureSecurityGroupContext(
                this, op.getBody(SecurityGroupInstanceRequest.class));

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
                .thenCompose(this::getEndpointState)
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

    private DeferredResult<AzureSecurityGroupContext> getEndpointState(
            AzureSecurityGroupContext context) {

        String endpointLink = context.securityGroupState.endpointLink;
        if (context.securityGroupState.endpointLinks != null && !context.securityGroupState
                .endpointLinks.isEmpty()) {
            endpointLink = context.securityGroupState.endpointLinks.iterator().next();
        }

        return this.sendWithDeferredResult(
                Operation.createGet(this, endpointLink), EndpointState.class)
                .thenApply(endpointState -> {
                    context.endpoint = endpointState;
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
                        .thenCompose(this::createResourceGroup)
                        .thenCompose(this::createResourceGroupState)
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

        String rgName = context.resourceGroup.name();

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

        String rgName = context.resourceGroup.name();

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
        String rgName = null;

        if (context.securityGroupState.customProperties != null) {
            rgName = context.securityGroupState.customProperties.get(RESOURCE_GROUP_NAME);
        }

        rgName = rgName != null ? rgName : String.format("%s-rg", context.securityGroupState.name);

        ResourceGroupsInner azureClient = context.azureSdkClients
                .getResourceManagementClientImpl().resourceGroups();

        String msg = "Deleting resource group [" + rgName + "] for [" +
                context.securityGroupState.name + "] network security group";

        AzureDeferredResultServiceCallback<Void> callback = new Default<>(this, msg);

        azureClient.deleteAsync(rgName, callback);

        return callback.toDeferredResult().thenApply(__ -> context);
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

    private DeferredResult<AzureSecurityGroupContext> createResourceGroup(
            AzureSecurityGroupContext context) {
        ResourceGroupInner resourceGroup = new ResourceGroupInner();
        resourceGroup.withLocation(context.securityGroupState.regionId);

        return AzureSecurityGroupUtils.createResourceGroupForSecurityGroup(this,
                context.azureSdkClients.getResourceManagementClientImpl().resourceGroups(),
                context.securityGroupState.name, resourceGroup)
                .thenApply(rg -> {
                    context.resourceGroup = rg;
                    // add the resource group name to the SG custom properties
                    if (context.securityGroupState.customProperties == null) {
                        context.securityGroupState.customProperties = new HashMap<>(1);
                    }
                    context.securityGroupState.customProperties.put(RESOURCE_GROUP_NAME, context
                            .resourceGroup.name());
                    return context;
                });
    }

    /**
     * Create a resource group state that represents the new Azure resource group.
     * This state is added to the security group state's group links.
     */
    private DeferredResult<AzureSecurityGroupContext> createResourceGroupState(
            AzureSecurityGroupContext context) {
        ResourceGroupState resourceGroupState = new ResourceGroupState();

        resourceGroupState.tenantLinks = context.securityGroupState.tenantLinks;
        resourceGroupState.endpointLink = context.securityGroupState.endpointLink;
        resourceGroupState.endpointLinks = context.securityGroupState.endpointLinks;

        resourceGroupState.id = context.resourceGroup.id();
        resourceGroupState.name = context.resourceGroup.name();

        if (resourceGroupState.customProperties == null) {
            resourceGroupState.customProperties = new HashMap<>(1);
        }

        resourceGroupState.customProperties.put(ComputeProperties.RESOURCE_TYPE_KEY,
                ResourceGroupStateType.AzureResourceGroup.name());

        resourceGroupState.computeHostLink = context.endpoint.computeLink;
        resourceGroupState.customProperties.put(ComputeProperties.COMPUTE_HOST_LINK_PROP_NAME,
                context.endpoint.computeLink);

        return this.sendWithDeferredResult(Operation.createPost(this,
                ResourceGroupService.FACTORY_LINK).setBody(resourceGroupState))
                .thenApply(o -> {
                    ResourceGroupState rgs = o.getBody(ResourceGroupState.class);
                    context.resourceGroupState = rgs;

                    // add group link to the created resource group state
                    if (context.securityGroupState.groupLinks == null) {
                        context.securityGroupState.groupLinks = new HashSet<>();
                    }
                    context.securityGroupState.groupLinks.add(rgs.documentSelfLink);

                    return context;
                });
    }
}
