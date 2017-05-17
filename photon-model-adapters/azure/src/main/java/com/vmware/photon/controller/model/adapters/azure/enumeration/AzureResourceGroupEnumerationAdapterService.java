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

package com.vmware.photon.controller.model.adapters.azure.enumeration;

import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AUTH_HEADER_BEARER_PREFIX;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_CORE_MANAGEMENT_URI;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.LIST_RESOURCE_GROUPS_URI;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.QUERY_PARAM_API_VERSION;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.RESOURCE_GROUP_REST_API_VERSION;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.getAzureConfig;

import java.net.URI;
import java.util.HashMap;

import com.microsoft.azure.credentials.ApplicationTokenCredentials;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.ResourceGroupStateType;
import com.vmware.photon.controller.model.adapters.azure.model.resourcegroup.ResourceGroup;
import com.vmware.photon.controller.model.adapters.azure.model.resourcegroup.ResourceGroupListResult;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.adapters.util.ComputeEnumerateAdapterRequest;
import com.vmware.photon.controller.model.adapters.util.enums.BaseComputeEnumerationAdapterContext;
import com.vmware.photon.controller.model.adapters.util.enums.EnumerationStages;
import com.vmware.photon.controller.model.resources.ResourceGroupService;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask.Query;

/**
 * Enumeration adapter for data collection of Azure resource groups.
 */
public class AzureResourceGroupEnumerationAdapterService extends StatelessService {

    public static final String SELF_LINK = AzureUriPaths.AZURE_RESOURCE_GROUP_ENUMERATION_ADAPTER;

    public static class ResourceGroupEnumContext extends
            BaseComputeEnumerationAdapterContext<ResourceGroupEnumContext, ResourceGroupState, ResourceGroup> {

        // Azure credentials.
        ApplicationTokenCredentials credentials;

        ResourceGroupEnumContext(StatelessService service, ComputeEnumerateAdapterRequest request,
                Operation op) {

            super(service, request, op, ResourceGroupState.class,
                    ResourceGroupService.FACTORY_LINK);
        }

        @Override
        public DeferredResult<RemoteResourcesPage> getExternalResources(String nextPageLink) {
            URI uri;
            if (nextPageLink == null) {
                // First request to fetch Resource Groups from Azure.
                String uriStr = AdapterUriUtil.expandUriPathTemplate(LIST_RESOURCE_GROUPS_URI,
                        this.request.parentAuth.userLink);
                uri = UriUtils.extendUriWithQuery(
                        UriUtils.buildUri(uriStr),
                        QUERY_PARAM_API_VERSION, RESOURCE_GROUP_REST_API_VERSION);
            } else {
                // Request to fetch next page of Virtual Networks from Azure.
                uri = UriUtils.buildUri(nextPageLink);
            }

            final Operation operation = Operation.createGet(uri);
            operation.addRequestHeader(Operation.ACCEPT_HEADER,
                    Operation.MEDIA_TYPE_APPLICATION_JSON);
            operation.addRequestHeader(Operation.CONTENT_TYPE_HEADER,
                    Operation.MEDIA_TYPE_APPLICATION_JSON);
            try {
                operation.addRequestHeader(Operation.AUTHORIZATION_HEADER,
                        AUTH_HEADER_BEARER_PREFIX + this.credentials.getToken(AZURE_CORE_MANAGEMENT_URI));
            } catch (Exception ex) {
                return DeferredResult.failed(ex);
            }

            return this.service.sendWithDeferredResult(operation, ResourceGroupListResult.class)
                    .thenApply(results -> {
                        RemoteResourcesPage page = new RemoteResourcesPage();

                        if (results.value != null) {
                            results.value.forEach(resourceGroup -> {
                                page.resourcesPage.put(resourceGroup.id, resourceGroup);
                            });
                        }
                        page.nextPageLink = results.nextLink;

                        return page;
                    });
        }

        @Override
        protected void customizeLocalStatesQuery(Query.Builder qBuilder) {

            qBuilder.addCompositeFieldClause(
                    ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                    ComputeProperties.COMPUTE_HOST_LINK_PROP_NAME,
                    this.request.parentCompute.documentSelfLink);

            qBuilder.addCompositeFieldClause(
                    ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                    ComputeProperties.RESOURCE_TYPE_KEY,
                    ResourceGroupStateType.AzureResourceGroup.name());
        }

        @Override
        protected DeferredResult<LocalStateHolder> buildLocalResourceState(
                ResourceGroup remoteResourceGroup,
                ResourceGroupState localResourceGroupState) {

            LocalStateHolder holder = new LocalStateHolder();
            holder.localState = new ResourceGroupState();

            if (localResourceGroupState != null) {
                // Update: do not set Photon fields which are not explicitly affected by Azure
                holder.localState.documentSelfLink = localResourceGroupState.documentSelfLink;
            } else {
                // Create: set Photon fields
                holder.localState.customProperties = new HashMap<>();
                holder.localState.customProperties.put(
                        ComputeProperties.COMPUTE_HOST_LINK_PROP_NAME,
                        this.request.parentCompute.documentSelfLink);
                holder.localState.customProperties.put(
                        ComputeProperties.RESOURCE_TYPE_KEY,
                        ResourceGroupStateType.AzureResourceGroup.name());
            }

            // Fields explicitly affected by Azure
            holder.localState.name = remoteResourceGroup.name;

            return DeferredResult.completed(holder);
        }
    }

    public AzureResourceGroupEnumerationAdapterService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }

        ResourceGroupEnumContext ctx = new ResourceGroupEnumContext(
                this, op.getBody(ComputeEnumerateAdapterRequest.class), op);

        if (ctx.request.original.isMockRequest) {
            op.complete();
            return;
        }

        handleEnumeration(ctx);
    }

    /**
     * Creates the resource group states in the local document store based on the resource groups
     * received from the remote endpoint.
     *
     * @param context
     *            The local service context that has all the information needed to create the
     *            additional description states in the local system.
     */
    private void handleEnumeration(ResourceGroupEnumContext context) {
        switch (context.stage) {

        case CLIENT:
            if (context.credentials == null) {
                try {
                    context.credentials = getAzureConfig(context.request.parentAuth);
                } catch (Throwable e) {
                    handleError(context, e);
                    return;
                }
            }
            context.stage = EnumerationStages.ENUMERATE;
            handleEnumeration(context);
            break;
        case ENUMERATE:
            String enumKey = getEnumKey(context);
            switch (context.request.original.enumerationAction) {
            case START:
                logInfo(() -> String.format("Launching Azure ResourceGroup enumeration for %s",
                        enumKey));
                context.request.original.enumerationAction = EnumerationAction.REFRESH;
                handleEnumeration(context);
                break;
            case REFRESH:
                // Allow base context class to enumerate the resources.
                context.enumerate()
                        .whenComplete((ignoreCtx, t) -> {
                            // NOTE: In case of error 'ignoreCtx' is null so use passed context!
                            if (t != null) {
                                handleError(context, t);
                                return;
                            }
                            context.stage = EnumerationStages.FINISHED;
                            handleEnumeration(context);
                        });
                break;
            case STOP:
                logInfo(() -> String.format(
                        "Azure ResourceGroup enumeration will be stopped for %s",
                        enumKey));
                context.stage = EnumerationStages.FINISHED;
                handleEnumeration(context);
                break;
            default:
                handleError(context,
                        new RuntimeException("Unknown Azure ResourceGroup enumeration action"
                                + context.request.original.enumerationAction));
                break;
            }
            break;
        case FINISHED:
            logInfo(() -> String.format("Azure ResourceGroup enumeration finished for %s",
                    getEnumKey(context)));
            context.operation.complete();
            break;
        case ERROR:
            logWarning(() -> String.format("Azure ResourceGroup enumeration error for %s",
                    getEnumKey(context)));
            context.operation.fail(context.error);
            break;
        default:
            String msg = String.format("Unknown Azure ResourceGroup enumeration stage %s ",
                    context.stage.toString());
            logSevere(() -> msg);
            context.error = new IllegalStateException(msg);
            context.operation.fail(context.error);
        }
    }

    /**
     * Return a key to uniquely identify enumeration for compute host instance.
     */
    private String getEnumKey(ResourceGroupEnumContext ctx) {
        return ctx.request.original.getEnumKey();
    }

    private void handleError(ResourceGroupEnumContext ctx, Throwable e) {
        logSevere(() -> String.format("Failed at stage %s with exception: %s", ctx.stage,
                Utils.toString(e)));
        ctx.error = e;
        ctx.stage = EnumerationStages.ERROR;
        handleEnumeration(ctx);
    }
}
