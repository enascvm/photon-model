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
import static com.vmware.photon.controller.model.adapters.azure.AzureUriPaths.AZURE_FIREWALL_ADAPTER;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AUTH_HEADER_BEARER_PREFIX;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_SECURITY_GROUP_DIRECTION_INBOUND;
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
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;

import com.microsoft.azure.credentials.ApplicationTokenCredentials;

import org.apache.commons.net.util.SubnetUtils;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.ResourceGroupStateType;
import com.vmware.photon.controller.model.adapters.azure.model.network.NetworkSecurityGroup;
import com.vmware.photon.controller.model.adapters.azure.model.network.NetworkSecurityGroupListResult;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.ComputeEnumerateAdapterRequest;
import com.vmware.photon.controller.model.adapters.util.enums.BaseEnumerationAdapterContext;
import com.vmware.photon.controller.model.adapters.util.enums.EnumerationStages;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.SecurityGroupService;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState.Rule;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState.Rule.Access;
import com.vmware.photon.controller.model.tasks.QueryUtils.QueryByPages;
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
 * Enumeration adapter for data collection of Azure network security groups.
 */
public class AzureSecurityGroupEnumerationAdapterService extends StatelessService {
    public static final String SELF_LINK = AzureUriPaths.AZURE_FIREWALL_ENUMERATION_ADAPTER;

    public static class SecurityGroupEnumContext extends
            BaseEnumerationAdapterContext<SecurityGroupEnumContext, SecurityGroupState,
                    NetworkSecurityGroup> {

        ComputeEnumerateResourceRequest enumRequest;
        AuthCredentialsService.AuthCredentialsServiceState parentAuth;

        EnumerationStages stage;

        // Stored operation to signal completion to the Azure security group enumeration once all
        // the stages are successfully completed.
        Operation azureSecurityGroupAdapterOperation;

        // Azure credentials.
        ApplicationTokenCredentials credentials;

        // The time when enumeration starts. This field is used also to identify stale resources
        // that should be deleted during deletion stage.
        long enumerationStartTimeInMicros;

        List<String> securityGroupIds = new ArrayList<>();
        // Stores the map of resource groups state ids to document self links.
        // key -> resource group id; value - link to the local ResourceGroupState object.
        Map<String, String> securityGroupRGStates = new HashMap<>();

        // Used to store an error while transferring to the error stage.
        Throwable error;

        SecurityGroupEnumContext(ComputeEnumerateAdapterRequest request, Operation op,
                StatelessService service) {
            super(service, SecurityGroupState.class, SecurityGroupService.FACTORY_LINK, request
                    .parentCompute);

            this.enumRequest = request.computeEnumerateResourceRequest;
            this.parentAuth = request.parentAuth;

            this.stage = EnumerationStages.CLIENT;
            this.azureSecurityGroupAdapterOperation = op;
        }

        @Override
        public DeferredResult<RemoteResourcesPage> getExternalResources(String nextPageLink) {
            this.securityGroupRGStates.clear();

            URI uri;
            if (nextPageLink == null) {
                // First request to fetch Network Security Groups from Azure.
                String uriStr = AdapterUriUtil
                        .expandUriPathTemplate(LIST_NETWORK_SECURITY_GROUP_URI,
                                this.parentAuth.userLink);
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
                        AUTH_HEADER_BEARER_PREFIX + this.credentials.getToken());
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
                                this.securityGroupIds.add(securityGroup.id);
                            });
                        }
                        page.nextPageLink = results.nextLink;

                        return page;
                    });
        }

        /**
         * Load local Security Group states and in addition load all related resource groups.
         * They will be used when building the updated local Security Group states.
         */
        @Override
        protected DeferredResult<SecurityGroupEnumContext> queryLocalStates(
                SecurityGroupEnumContext context) {

            return super.queryLocalStates(context).thenCompose(
                    this::getSecurityGroupRGStates);
        }

        @Override
        protected Query getDeleteQuery() {
            String computeHostProperty = QuerySpecification.buildCompositeFieldName(
                    SecurityGroupState.FIELD_NAME_CUSTOM_PROPERTIES,
                    ComputeProperties.FIELD_COMPUTE_HOST_LINK);

            return Builder.create()
                    .addKindFieldClause(SecurityGroupState.class)
                    .addFieldClause(computeHostProperty,
                            this.parentCompute.documentSelfLink)
                    .addInClause(SecurityGroupState.FIELD_NAME_ID, this.securityGroupIds, Occurance
                            .MUST_NOT_OCCUR)
                    .addRangeClause(NetworkState.FIELD_NAME_UPDATE_TIME_MICROS,
                            QueryTask.NumericRange
                                    .createLessThanRange(this.enumerationStartTimeInMicros))
                    .build();
        }

        @Override
        protected SecurityGroupState buildLocalResourceState(
                NetworkSecurityGroup networkSecurityGroup,
                SecurityGroupState localSecurityGroupState) {

            String resourceGroupId = AzureUtils.getResourceGroupId(networkSecurityGroup.id);
            String rgDocumentSelfLink = this.securityGroupRGStates.get(resourceGroupId);
            if (rgDocumentSelfLink == null) {
                // Resource group is still not enumerated.
                // TODO: add log.
                return SKIP;
            }

            SecurityGroupState resultSecurityGroupState = new SecurityGroupState();
            resultSecurityGroupState.groupLinks = Collections.singleton(rgDocumentSelfLink);
            if (localSecurityGroupState != null) {
                resultSecurityGroupState.documentSelfLink = localSecurityGroupState.documentSelfLink;
                resultSecurityGroupState.authCredentialsLink = localSecurityGroupState.authCredentialsLink;

            } else {
                resultSecurityGroupState.authCredentialsLink = this.parentAuth.documentSelfLink;
                resultSecurityGroupState.customProperties = new HashMap<>();
                resultSecurityGroupState.customProperties.put(FIELD_COMPUTE_HOST_LINK,
                        this.parentCompute.documentSelfLink);
            }

            resultSecurityGroupState.name = networkSecurityGroup.name;
            resultSecurityGroupState.regionId = networkSecurityGroup.location;
            resultSecurityGroupState.resourcePoolLink = this.enumRequest.resourcePoolLink;
            resultSecurityGroupState.tenantLinks = this.parentCompute.tenantLinks;

            // TODO: AzureFirewallService currently doesn't exist.
            resultSecurityGroupState.instanceAdapterReference = UriUtils
                    .buildUri(this.service.getHost(),
                            AZURE_FIREWALL_ADAPTER);

            resultSecurityGroupState.ingress = new ArrayList<>();
            resultSecurityGroupState.egress = new ArrayList<>();

            if (networkSecurityGroup.properties == null ||
                    networkSecurityGroup.properties.securityRules == null) {
                // No rules.
                return resultSecurityGroupState;
            }

            networkSecurityGroup.properties.securityRules.forEach(securityRule -> {

                Rule rule = new Rule();
                rule.name = securityRule.name;
                rule.access = AzureConstants.AZURE_SECURITY_GROUP_ACCESS
                        .equalsIgnoreCase(securityRule.properties.access) ?
                        Access.Allow : Access.Deny;
                rule.protocol = securityRule.properties.protocol;

                List<SecurityGroupState.Rule> rulesList;
                String ports;

                if (AZURE_SECURITY_GROUP_DIRECTION_INBOUND.equalsIgnoreCase(
                        securityRule.properties.direction)) {
                    // ingress rule.
                    rule.ipRangeCidr = securityRule.properties.sourceAddressPrefix;
                    ports = securityRule.properties.sourcePortRange;
                    rulesList = resultSecurityGroupState.ingress;
                } else {
                    // egress rule.
                    rule.ipRangeCidr = securityRule.properties.destinationPortRange;
                    ports = securityRule.properties.destinationPortRange;
                    rulesList = resultSecurityGroupState.egress;
                }

                if (SecurityGroupService.ANY.equals(ports)) {
                    ports = "1-65535";
                }

                rule.ports = ports;
                try {
                    if (!SecurityGroupService.ANY.equals(rule.ipRangeCidr)) {
                        new SubnetUtils(rule.ipRangeCidr);
                    }
                    rulesList.add(rule);
                } catch (IllegalArgumentException e) {
                    // Ignore this rule as not supported by the system.
                    this.service.logWarning("Network Security Rule is ignored. Rule ip "
                            + "range: %s.", rule.ipRangeCidr);
                }
            });
            return resultSecurityGroupState;
        }

        /**
         * For each loaded Security Group load its corresponding resource group state based on
         * the resource group id.
         */
        private DeferredResult<SecurityGroupEnumContext> getSecurityGroupRGStates(
                SecurityGroupEnumContext context) {
            if (context.remoteResources == null || context.remoteResources.keySet().isEmpty()) {
                return DeferredResult.completed(context);
            }

            List<String> resourceGroupIds = context.remoteResources.keySet().stream()
                    .map(AzureUtils::getResourceGroupId)
                    .collect(Collectors.toList());

            Query query = Builder.create()
                    .addKindFieldClause(ResourceGroupState.class)
                    .addCompositeFieldClause(ResourceGroupState.FIELD_NAME_CUSTOM_PROPERTIES,
                            ComputeProperties.FIELD_COMPUTE_HOST_LINK,
                            this.parentCompute.documentSelfLink)
                    .addCompositeFieldClause(ResourceGroupState.FIELD_NAME_CUSTOM_PROPERTIES,
                            ComputeProperties.RESOURCE_TYPE_KEY,
                            ResourceGroupStateType.AzureResourceGroup.name())
                    .addInClause(ResourceGroupState.FIELD_NAME_ID, resourceGroupIds)
                    .build();

            QueryByPages<ResourceGroupState> queryByPages = new QueryByPages<>(
                    this.service.getHost(),
                    query,
                    ResourceGroupState.class,
                    null).setMaxPageSize(resourceGroupIds.size());

            return queryByPages
                    .queryDocuments(rgState ->
                            this.securityGroupRGStates.put(rgState.id, rgState.documentSelfLink))
                    .thenApply(ignore -> context);
        }
    }

    Set<String> ongoingEnumerations = new ConcurrentSkipListSet<>();

    public AzureSecurityGroupEnumerationAdapterService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        SecurityGroupEnumContext ctx = new SecurityGroupEnumContext(op.getBody
                (ComputeEnumerateAdapterRequest.class), op, this);
        AdapterUtils.validateEnumRequest(ctx.enumRequest);
        if (ctx.enumRequest.isMockRequest) {
            op.complete();
            return;
        }
        handleEnumeration(ctx);
    }

    /**
     * Creates the firewall states in the local document store based on the network security groups
     * received from the remote endpoint.
     *
     * @param context The local service context that has all the information needed to create the
     *                additional description states in the local system.
     */
    private void handleEnumeration(SecurityGroupEnumContext context) {
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
            handleEnumeration(context);
            break;
        case ENUMERATE:
            String enumKey = getEnumKey(context);
            switch (context.enumRequest.enumerationAction) {
            case START:
                if (!this.ongoingEnumerations.add(enumKey)) {
                    logInfo("Enumeration service has already been started for %s", enumKey);
                    context.stage = EnumerationStages.FINISHED;
                    handleEnumeration(context);
                    return;
                }
                logInfo("Launching enumeration service for %s", enumKey);
                context.enumRequest.enumerationAction = EnumerationAction.REFRESH;
                handleEnumeration(context);
                break;
            case REFRESH:
                context.enumerationStartTimeInMicros = Utils.getNowMicrosUtc();
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
                if (this.ongoingEnumerations.remove(enumKey)) {
                    logInfo("Enumeration service will be stopped for %s", enumKey);
                } else {
                    logInfo("Enumeration service is not running or has already been stopped for %s",
                            enumKey);
                }
                context.stage = EnumerationStages.FINISHED;
                handleEnumeration(context);
                break;
            default:
                handleError(context, new RuntimeException(
                        "Unknown enumeration action" + context.enumRequest.enumerationAction));
                break;
            }
            break;
        case FINISHED:
            context.azureSecurityGroupAdapterOperation.complete();
            logInfo("Enumeration finished for %s", getEnumKey(context));
            this.ongoingEnumerations.remove(getEnumKey(context));
            break;
        case ERROR:
            context.azureSecurityGroupAdapterOperation.fail(context.error);
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
    private String getEnumKey(SecurityGroupEnumContext ctx) {
        return "hostLink:" + ctx.enumRequest.resourceLink() +
                "-enumerationAdapterReference:" +
                ctx.parentCompute.description.enumerationAdapterReference;
    }

    private void handleError(SecurityGroupEnumContext ctx, Throwable e) {
        logSevere("Failed at stage %s with exception: %s", ctx.stage, Utils.toString(e));
        ctx.error = e;
        ctx.stage = EnumerationStages.ERROR;
        handleEnumeration(ctx);
    }
}
