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

import static com.vmware.photon.controller.model.adapters.azure.AzureUriPaths.AZURE_SECURITY_GROUP_ADAPTER;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AUTH_HEADER_BEARER_PREFIX;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.LIST_NETWORK_SECURITY_GROUP_URI;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.NETWORK_REST_API_VERSION;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.QUERY_PARAM_API_VERSION;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.getAzureConfig;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.network.SecurityRuleDirection;

import org.apache.commons.net.util.SubnetUtils;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.ResourceGroupStateType;
import com.vmware.photon.controller.model.adapters.azure.model.network.NetworkSecurityGroup;
import com.vmware.photon.controller.model.adapters.azure.model.network.NetworkSecurityGroupListResult;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.adapters.util.ComputeEnumerateAdapterRequest;
import com.vmware.photon.controller.model.adapters.util.enums.BaseComputeEnumerationAdapterContext;
import com.vmware.photon.controller.model.adapters.util.enums.EnumerationStages;
import com.vmware.photon.controller.model.query.QueryUtils.QueryTop;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.SecurityGroupService;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState.Rule;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState.Rule.Access;
import com.vmware.photon.controller.model.util.ClusterUtil.ServiceTypeCluster;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;

/**
 * Enumeration adapter for data collection of Azure network security groups.
 */
public class AzureSecurityGroupEnumerationAdapterService extends StatelessService {

    public static final String SELF_LINK = AzureUriPaths.AZURE_FIREWALL_ENUMERATION_ADAPTER;

    public static class SecurityGroupEnumContext extends
            BaseComputeEnumerationAdapterContext<SecurityGroupEnumContext, SecurityGroupState, NetworkSecurityGroup> {

        // Azure credentials.
        ApplicationTokenCredentials credentials;

        // Stores the map of resource groups state ids to document self links.
        // key -> resource group id; value - link to the local ResourceGroupState object.
        Map<String, String> securityGroupRGStates = new HashMap<>();

        SecurityGroupEnumContext(StatelessService service, ComputeEnumerateAdapterRequest request,
                Operation op) {

            super(service, request, op, SecurityGroupState.class,
                    SecurityGroupService.FACTORY_LINK);
            setApplyEndpointLink(false);
        }

        @Override
        public DeferredResult<RemoteResourcesPage> getExternalResources(String nextPageLink) {

            this.securityGroupRGStates.clear();

            URI uri;
            if (nextPageLink == null) {
                // First request to fetch Network Security Groups from Azure.
                String uriStr = AdapterUriUtil
                        .expandUriPathTemplate(LIST_NETWORK_SECURITY_GROUP_URI,
                                this.request.endpointAuth.userLink);
                uri = UriUtils.extendUriWithQuery(
                        UriUtils.buildUri(uriStr),
                        QUERY_PARAM_API_VERSION, NETWORK_REST_API_VERSION);
            } else {
                // Request to fetch next page of Network Security Groups from Azure.
                uri = UriUtils.buildUri(nextPageLink);
            }

            final Operation operation = Operation.createGet(uri);
            operation.addRequestHeader(Operation.ACCEPT_HEADER,
                    Operation.MEDIA_TYPE_APPLICATION_JSON);
            operation.addRequestHeader(Operation.CONTENT_TYPE_HEADER,
                    Operation.MEDIA_TYPE_APPLICATION_JSON);
            try {
                operation.addRequestHeader(Operation.AUTHORIZATION_HEADER,
                        AUTH_HEADER_BEARER_PREFIX + this.credentials.getToken(AzureUtils.getAzureBaseUri()));
            } catch (Exception ex) {
                return DeferredResult.failed(ex);
            }

            return this.service
                    .sendWithDeferredResult(operation, NetworkSecurityGroupListResult.class)
                    .thenApply(results -> {
                        RemoteResourcesPage page = new RemoteResourcesPage();

                        if (results.value != null) {
                            results.value.forEach(securityGroup -> {
                                page.resourcesPage.put(securityGroup.id, securityGroup);
                            });
                        }
                        page.nextPageLink = results.nextLink;

                        return page;
                    });
        }

        /**
         * Load local Security Group states and in addition load their related resource groups. They
         * will be used when building the updated local Security Group states.
         */
        @Override
        protected DeferredResult<SecurityGroupEnumContext> queryLocalStates(
                SecurityGroupEnumContext context) {
            return super.queryLocalStates(context)
                    .thenCompose(this::getSecurityGroupRGStates);
        }

        @Override
        protected void customizeLocalStatesQuery(Query.Builder qBuilder) {
            qBuilder.addFieldClause(ResourceState.FIELD_NAME_COMPUTE_HOST_LINK,
                    this.request.parentCompute.documentSelfLink);
        }

        @Override
        protected DeferredResult<LocalStateHolder> buildLocalResourceState(
                NetworkSecurityGroup networkSecurityGroup,
                SecurityGroupState localSecurityGroupState) {
            LocalStateHolder holder = new LocalStateHolder();

            String resourceGroupId = AzureUtils.getResourceGroupId(networkSecurityGroup.id);
            String rgDocumentSelfLink = this.securityGroupRGStates.get(resourceGroupId);
            if (rgDocumentSelfLink == null) {
                // The Resource Group of Security Group is still not enumerated.
                // TODO: add log.
                holder.localState = this.SKIP;
                return DeferredResult.completed(holder);
            }

            holder.localState = new SecurityGroupState();

            if (localSecurityGroupState != null) {
                // Update: no need to touch Photon fields which are not affected by Azure
                holder.localState.documentSelfLink = localSecurityGroupState.documentSelfLink;
            } else {
                // Create: need to set Photon fields
                holder.localState.customProperties = new HashMap<>();
                holder.localState.customProperties.put(
                        ComputeProperties.COMPUTE_HOST_LINK_PROP_NAME,
                        this.request.parentCompute.documentSelfLink);

                holder.localState.authCredentialsLink = this.request.endpointAuth.documentSelfLink;
                holder.localState.resourcePoolLink = this.request.original.resourcePoolLink;

                holder.localState.instanceAdapterReference = UriUtils
                        .buildUri(this.service.getHost(), AZURE_SECURITY_GROUP_ADAPTER);
            }

            // Fields explicitly affected by Azure

            holder.localState.groupLinks = Collections.singleton(rgDocumentSelfLink);
            holder.localState.name = networkSecurityGroup.name;
            holder.localState.regionId = networkSecurityGroup.location;

            // Add tags
            if (networkSecurityGroup.tags != null) {
                holder.remoteTags.putAll(networkSecurityGroup.tags);
            }

            if (networkSecurityGroup.properties == null
                    || networkSecurityGroup.properties.securityRules == null) {
                // No rules.
                return DeferredResult.completed(holder);
            }

            holder.localState.ingress = new ArrayList<>();
            holder.localState.egress = new ArrayList<>();

            networkSecurityGroup.properties.securityRules.forEach(securityRule -> {

                Rule rule = new Rule();
                rule.name = securityRule.name;
                rule.access = AzureConstants.AZURE_SECURITY_GROUP_ACCESS
                        .equalsIgnoreCase(securityRule.properties.access)
                                ? Access.Allow
                                : Access.Deny;
                rule.protocol = securityRule.properties.protocol;

                final List<SecurityGroupState.Rule> rulesList;
                String ports;

                if (SecurityRuleDirection.INBOUND.toString().equalsIgnoreCase(
                        securityRule.properties.direction)) {
                    // ingress rule.
                    rule.ipRangeCidr = securityRule.properties.sourceAddressPrefix;
                    ports = securityRule.properties.sourcePortRange;
                    rulesList = holder.localState.ingress;
                } else {
                    // egress rule.
                    rule.ipRangeCidr = securityRule.properties.destinationAddressPrefix;
                    ports = securityRule.properties.destinationPortRange;
                    rulesList = holder.localState.egress;
                }

                if (SecurityGroupService.ANY.equals(ports)) {
                    ports = SecurityGroupService.ALL_PORTS;
                }

                rule.ports = ports;
                try {
                    if (!SecurityGroupService.ANY.equals(rule.ipRangeCidr)) {
                        new SubnetUtils(rule.ipRangeCidr);
                    }
                    rulesList.add(rule);
                } catch (IllegalArgumentException e) {
                    // Ignore this rule as not supported by the system.
                    this.service.logWarning(() -> String.format("Network Security Rule is ignored."
                            + " Rule ip range: %s.", rule.ipRangeCidr));
                }
            });

            return DeferredResult.completed(holder);
        }

        /**
         * For each loaded Security Group load its corresponding resource group state based on the
         * resource group id.
         */
        private DeferredResult<SecurityGroupEnumContext> getSecurityGroupRGStates(
                SecurityGroupEnumContext ctx) {

            if (ctx.remoteResources.keySet().isEmpty()) {
                return DeferredResult.completed(ctx);
            }

            List<String> resourceGroupIds = ctx.remoteResources.keySet().stream()
                    .map(AzureUtils::getResourceGroupId)
                    .collect(Collectors.toList());

            Query.Builder qBuilder = Builder.create()
                    .addKindFieldClause(ResourceGroupState.class)
                    .addInClause(ResourceState.FIELD_NAME_ID, resourceGroupIds)
                    .addCompositeFieldClause(
                            ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                            ComputeProperties.RESOURCE_TYPE_KEY,
                            ResourceGroupStateType.AzureResourceGroup.name());

            QueryTop<ResourceGroupState> queryByPages = new QueryTop<>(
                    this.service.getHost(),
                    qBuilder.build(),
                    ResourceGroupState.class,
                    ctx.request.parentCompute.tenantLinks,
                    null /* endpointLink */,
                    this.request.parentCompute.documentSelfLink)
                    .setClusterType(ServiceTypeCluster.INVENTORY_SERVICE);

            return queryByPages
                    .queryDocuments(rg -> ctx.securityGroupRGStates.put(rg.id, rg.documentSelfLink))
                    .thenApply(ignore -> ctx);
        }
    }

    public AzureSecurityGroupEnumerationAdapterService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }

        SecurityGroupEnumContext ctx = new SecurityGroupEnumContext(
                this, op.getBody(ComputeEnumerateAdapterRequest.class), op);

        if (ctx.request.original.isMockRequest) {
            op.complete();
            return;
        }

        handleEnumeration(ctx);
    }

    /**
     * Safe version of {@link #handleEnumeration(ResourceGroupEnumContext)} which catches exception and
     * forward to {@link #handleError(ResourceGroupEnumContext, Throwable)}.
     */
    private void handleEnumeration(SecurityGroupEnumContext ctx) {
        try {
            handleEnumerationImpl(ctx);
        } catch (Throwable e) {
            handleError(ctx, e);
        }
    }

    /**
     * Creates the firewall states in the local document store based on the network security groups
     * received from the remote endpoint.
     *
     * @param context
     *            The local service context that has all the information needed to create the
     *            additional description states in the local system.
     */
    private void handleEnumerationImpl(SecurityGroupEnumContext context) {
        switch (context.stage) {

        case CLIENT:
            if (context.credentials == null) {
                context.credentials = getAzureConfig(context.request.endpointAuth);
            }
            context.stage = EnumerationStages.ENUMERATE;
            handleEnumeration(context);
            break;
        case ENUMERATE:
            String enumKey = getEnumKey(context);
            switch (context.request.original.enumerationAction) {
            case START:
                logInfo(() -> String.format("Launching Azure SecurityGroup enumeration for %s",
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
                        "Azure SecurityGroup enumeration will be stopped for %s",
                        enumKey));
                context.stage = EnumerationStages.FINISHED;
                handleEnumeration(context);
                break;
            default:
                handleError(context, new RuntimeException("Unknown enumeration action"
                        + context.request.original.enumerationAction));
                break;
            }
            break;
        case FINISHED:
            logInfo(() -> String.format("Azure SecurityGroup enumeration finished for %s",
                    getEnumKey(context)));
            context.operation.complete();
            break;
        case ERROR:
            logWarning(() -> String.format("Azure SecurityGroup enumeration error for %s",
                    getEnumKey(context)));
            context.operation.fail(context.error);
            break;
        default:
            String msg = String.format("Unknown Azure enumeration stage %s ",
                    context.stage.toString());
            logSevere(() -> msg);
            context.error = new IllegalStateException(msg);
            context.operation.fail(context.error);
        }
    }

    /**
     * Return a key to uniquely identify enumeration for compute host instance.
     */
    private String getEnumKey(SecurityGroupEnumContext ctx) {
        return ctx.request.original.getEnumKey();
    }

    private void handleError(SecurityGroupEnumContext ctx, Throwable e) {
        logSevere(() -> String.format("Failed at stage %s with exception: %s", ctx.stage,
                Utils.toString(e)));
        ctx.error = e;
        ctx.stage = EnumerationStages.ERROR;
        handleEnumeration(ctx);
    }
}
