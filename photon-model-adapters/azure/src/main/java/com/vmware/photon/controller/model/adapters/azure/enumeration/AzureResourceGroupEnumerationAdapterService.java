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

import static com.vmware.photon.controller.model.ComputeProperties.FIELD_COMPUTE_HOST_LINK;
import static com.vmware.photon.controller.model.ComputeProperties.RESOURCE_TYPE_KEY;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AUTH_HEADER_BEARER_PREFIX;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.LIST_RESOURCE_GROUPS_URI;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.QUERY_PARAM_API_VERSION;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.RESOURCE_GROUP_REST_API_VERSION;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.getAzureConfig;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import com.microsoft.azure.credentials.ApplicationTokenCredentials;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.ResourceGroupStateType;
import com.vmware.photon.controller.model.adapters.azure.model.resourcegroup.ResourceGroup;
import com.vmware.photon.controller.model.adapters.azure.model.resourcegroup.ResourceGroupListResult;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.ComputeEnumerateAdapterRequest;
import com.vmware.photon.controller.model.adapters.util.enums.BaseEnumerationAdapterContext;
import com.vmware.photon.controller.model.adapters.util.enums.EnumerationStages;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;

/**
 * Enumeration adapter for data collection of Azure resource groups.
 */
public class AzureResourceGroupEnumerationAdapterService extends StatelessService {
    public static final String SELF_LINK = AzureUriPaths.AZURE_RESOURCE_GROUP_ENUMERATION_ADAPTER;

    public static class ResourceGroupEnumContext extends
            BaseEnumerationAdapterContext<ResourceGroupEnumContext, ResourceGroupState,
                    ResourceGroup> {

        ComputeEnumerateResourceRequest enumRequest;
        AuthCredentialsService.AuthCredentialsServiceState parentAuth;

        EnumerationStages stage;

        // Stored operation to signal completion to the Azure resource group enumeration once all]
        // the stages are successfully completed.
        Operation azureResourceGroupAdapterOperation;

        // Azure credentials.
        ApplicationTokenCredentials credentials;

        List<String> resourceGroupIds = new ArrayList<>();

        // The time when enumeration starts. This field is used also to identify stale resources
        // that should be deleted during deletion stage.
        long enumerationStartTimeInMicros;

        // Used to store an error while transferring to the error stage.
        Throwable error;

        ResourceGroupEnumContext(ComputeEnumerateAdapterRequest request, Operation op,
                StatelessService service) {
            super(service, ResourceGroupState.class, request.parentCompute);

            this.enumRequest = request.computeEnumerateResourceRequest;
            this.parentAuth = request.parentAuth;

            this.stage = EnumerationStages.CLIENT;
            this.azureResourceGroupAdapterOperation = op;
        }

        @Override
        public DeferredResult<RemoteResourcesPage> getExternalResources(String nextPageLink) {
            URI uri;
            if (nextPageLink == null) {
                // First request to fetch Resource Groups from Azure.
                String uriStr = AdapterUriUtil.expandUriPathTemplate(LIST_RESOURCE_GROUPS_URI,
                        this.parentAuth.userLink);
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
                        AUTH_HEADER_BEARER_PREFIX + this.credentials.getToken());
            } catch (Exception ex) {
                return DeferredResult.failed(ex);
            }

            return this.service.sendWithDeferredResult(operation, ResourceGroupListResult.class)
                    .thenApply(results -> {
                        RemoteResourcesPage page = new RemoteResourcesPage();

                        if (results.value != null) {
                            results.value.forEach(resourceGroup -> {
                                page.resourcesPage.put(resourceGroup.id, resourceGroup);
                                this.resourceGroupIds.add(resourceGroup.id);
                            });
                        }
                        page.nextPageLink = results.nextLink;

                        return page;
                    });
        }

        @Override
        protected Query getDeleteQuery() {
            String rgTypeProperty = QuerySpecification
                    .buildCompositeFieldName(
                            ResourceGroupState.FIELD_NAME_CUSTOM_PROPERTIES,
                            ComputeProperties.RESOURCE_TYPE_KEY);

            String computeHostProperty = QuerySpecification.buildCompositeFieldName(
                    ResourceGroupState.FIELD_NAME_CUSTOM_PROPERTIES,
                    ComputeProperties.FIELD_COMPUTE_HOST_LINK);

            return Builder.create()
                    .addKindFieldClause(ResourceGroupState.class)
                    .addFieldClause(computeHostProperty,
                            this.parentCompute.documentSelfLink)
                    .addFieldClause(rgTypeProperty,
                            ResourceGroupStateType.AzureResourceGroup.name())
                    .addInClause(ResourceGroupState.FIELD_NAME_ID, this.resourceGroupIds, Occurance
                            .MUST_NOT_OCCUR)
                    .addRangeClause(ResourceGroupState.FIELD_NAME_UPDATE_TIME_MICROS,
                            QueryTask.NumericRange
                                    .createLessThanRange(this.enumerationStartTimeInMicros))
                    .build();
        }

        @Override
        protected ResourceGroupState buildLocalResourceState(
                ResourceGroup remoteResourceGroup,
                ResourceGroupState localResourceGroupState) {

            ResourceGroupState resultResourceGroupState = new ResourceGroupState();

            if (localResourceGroupState != null) {
                resultResourceGroupState.documentSelfLink = localResourceGroupState.documentSelfLink;
            } else {
                resultResourceGroupState.customProperties = new HashMap<>();
                resultResourceGroupState.customProperties.put(FIELD_COMPUTE_HOST_LINK,
                        this.parentCompute.documentSelfLink);
                resultResourceGroupState.customProperties.put(RESOURCE_TYPE_KEY,
                        ResourceGroupStateType.AzureResourceGroup.name());
            }

            resultResourceGroupState.name = remoteResourceGroup.name;
            resultResourceGroupState.tenantLinks = this.parentCompute.tenantLinks;

            return resultResourceGroupState;
        }
    }

    private Set<String> ongoingEnumerations = new ConcurrentSkipListSet<>();

    public AzureResourceGroupEnumerationAdapterService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        ResourceGroupEnumContext ctx = new ResourceGroupEnumContext(op.getBody
                (ComputeEnumerateAdapterRequest.class), op, this);
        AdapterUtils.validateEnumRequest(ctx.enumRequest);
        if (ctx.enumRequest.isMockRequest) {
            op.complete();
            return;
        }
        handleResourceGroupEnumeration(ctx);
    }

    /**
     * Creates the resource group states in the local document store based on the resource groups
     * received from the remote endpoint.
     *
     * @param context The local service context that has all the information needed to create the
     *                additional description states in the local system.
     */
    private void handleResourceGroupEnumeration(ResourceGroupEnumContext context) {
        switch (context.stage) {

        case CLIENT:
            if (context.credentials == null) {
                try {
                    context.credentials = getAzureConfig(context.parentAuth);
                } catch (Throwable e) {
                    handleError(context, e);
                    return;
                }
            }
            context.stage = EnumerationStages.ENUMERATE;
            handleResourceGroupEnumeration(context);
            break;
        case ENUMERATE:
            String enumKey = getEnumKey(context);
            switch (context.enumRequest.enumerationAction) {
            case START:
                if (!this.ongoingEnumerations.add(enumKey)) {
                    logInfo("Enumeration service has already been started for %s", enumKey);
                    context.stage = EnumerationStages.FINISHED;
                    handleResourceGroupEnumeration(context);
                    return;
                }
                logInfo("Launching enumeration service for %s", enumKey);
                context.enumRequest.enumerationAction = EnumerationAction.REFRESH;
                handleResourceGroupEnumeration(context);
                break;
            case REFRESH:
                context.enumerationStartTimeInMicros = Utils.getNowMicrosUtc();
                // Allow base context class to enumerate the resources.
                context.enumerate()
                        .whenComplete((resourceGroupEnumContext, throwable) -> {
                            if (throwable != null) {
                                handleError(context, throwable);
                                return;
                            }
                            context.stage = EnumerationStages.FINISHED;
                            handleResourceGroupEnumeration(context);
                        });
                break;
            case STOP:
                if (this.ongoingEnumerations.remove(enumKey)) {
                    logInfo("Enumeration service will be stopped for %s", enumKey);
                } else {
                    logInfo("Enumeration service is not running or has already been stopped for %s",
                            enumKey);
                }
                context.stage = EnumerationStages.FINISHED;
                handleResourceGroupEnumeration(context);
                break;
            default:
                handleError(context, new RuntimeException(
                        "Unknown enumeration action" + context.enumRequest.enumerationAction));
                break;
            }
            break;
        case FINISHED:
            context.azureResourceGroupAdapterOperation.complete();
            logInfo("Enumeration finished for %s", getEnumKey(context));
            this.ongoingEnumerations.remove(getEnumKey(context));
            break;
        case ERROR:
            context.azureResourceGroupAdapterOperation.fail(context.error);
            logWarning("Enumeration error for %s", getEnumKey(context));
            this.ongoingEnumerations.remove(getEnumKey(context));
            break;
        default:
            String msg = String
                    .format("Unknown Azure enumeration stage %s ", context.stage.toString());
            logSevere(msg);
            context.error = new IllegalStateException(msg);
            this.ongoingEnumerations.remove(getEnumKey(context));
        }
    }

    /**
     * Return a key to uniquely identify enumeration for compute host instance.
     */
    private String getEnumKey(ResourceGroupEnumContext ctx) {
        return "hostLink:" + ctx.enumRequest.resourceLink() +
                "-enumerationAdapterReference:" +
                ctx.parentCompute.description.enumerationAdapterReference;
    }

    private void handleError(ResourceGroupEnumContext ctx, Throwable e) {
        logSevere("Failed at stage %s with exception: %s", ctx.stage, Utils.toString(e));
        ctx.error = e;
        ctx.stage = EnumerationStages.ERROR;
        handleResourceGroupEnumeration(ctx);
    }
}
