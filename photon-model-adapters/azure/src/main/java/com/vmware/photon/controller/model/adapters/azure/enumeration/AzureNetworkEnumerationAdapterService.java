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
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.DEFAULT_INSTANCE_ADAPTER_REFERENCE;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.LIST_VIRTUAL_NETWORKS_URI;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.NETWORK_REST_API_VERSION;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.QUERY_PARAM_API_VERSION;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.getQueryResultLimit;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.getAzureConfig;
import static com.vmware.photon.controller.model.adapters.util.AdapterUtils.getDeletionState;
import static com.vmware.photon.controller.model.adapters.util.TagsUtil.newTagState;
import static com.vmware.photon.controller.model.adapters.util.TagsUtil.setTagLinksToResourceState;
import static com.vmware.photon.controller.model.adapters.util.TagsUtil.updateLocalTagStates;
import static com.vmware.photon.controller.model.util.PhotonModelUriUtils.createInventoryUri;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.microsoft.azure.credentials.ApplicationTokenCredentials;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AzureResourceType;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.ResourceGroupStateType;
import com.vmware.photon.controller.model.adapters.azure.enumeration.AzureNetworkEnumerationAdapterService.NetworkEnumContext.SubnetStateWithParentVNetId;
import com.vmware.photon.controller.model.adapters.azure.model.network.AddressSpace;
import com.vmware.photon.controller.model.adapters.azure.model.network.Subnet;
import com.vmware.photon.controller.model.adapters.azure.model.network.VirtualNetwork;
import com.vmware.photon.controller.model.adapters.azure.model.network.VirtualNetworkListResult;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.ComputeEnumerateAdapterRequest;
import com.vmware.photon.controller.model.adapters.util.enums.EnumerationStages;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.photon.controller.model.resources.util.PhotonModelUtils;
import com.vmware.photon.controller.model.support.LifecycleState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.NumericRange;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;

/**
 * Enumeration adapter for data collection of Network related resources on Azure.
 * <p>
 * The state machine that is implemented is the following:
 * <ul>
 * <li>Get a page of virtual networks from azure</li>
 * <li>Get subnets for the retrieved virtual networks.</li>
 * <li>Get local network states matching the azure network ids.</li>
 * <li>Get local subnet states matching the azure subnets ids.</li>
 * <li>Create/Update the matching network states</li>
 * <li>Create/Update the matching subnet states</li>
 * <li>Delete network states that are not touched during the previous stages because they are stale
 * entries, no longer existing in Azure.</li>
 * <li>Delete subnet states that are not touched during the previous stages and belong to the
 * touched networks during the previous states.</li>
 * </ul>
 */
public class AzureNetworkEnumerationAdapterService extends StatelessService {

    public static final String SELF_LINK = AzureUriPaths.AZURE_NETWORK_ENUMERATION_ADAPTER;

    private static final String NETWORK_TAG_TYPE_VALUE = AzureResourceType.azure_vnet.toString();
    private static final String SUBNET_TAG_TYPE_VALUE = AzureResourceType.azure_subnet.toString();

    /**
     * The local service context that is used to identify and create/update a representative set of
     * network states based on the enumeration data received from Azure.
     */
    public static class NetworkEnumContext {

        ComputeEnumerateResourceRequest request;

        ComputeStateWithDescription parentCompute;

        EnumerationStages stage;

        NetworkEnumStages subStage;

        // Used to store an error while transferring to the error stage.
        Throwable error;

        AuthCredentialsService.AuthCredentialsServiceState endpointAuth;

        // Virtual Networks page as fetched from Azure
        // key -> Virtual Network id; value -> Virtual Network.
        Map<String, VirtualNetwork> virtualNetworks = new ConcurrentHashMap<>();

        // Network States stored in local document store.
        // key -> Network State id (matching Azure Virtual Network id); value -> Network State
        Map<String, NetworkState> networkStates = new ConcurrentHashMap<>();

        // Stores the map of resource groups state ids to document self links.
        // key -> resource group id; value - link to the local ResourceGroupState object.
        Map<String, String> resourceGroupStates = new ConcurrentHashMap<>();

        Map<String, SubnetStateWithParentVNetId> subnets = new ConcurrentHashMap<>();

        // Local subnet states map.
        // Key -> Subnet state id; value -> subnet state documentLink.
        Map<String, String> subnetStates = new ConcurrentHashMap<>();

        // stores a mapping of internal tag values for networks.
        Map<String, String> networkInternalTagsMap = new ConcurrentHashMap<>();
        // stores documentSelfLink for networks internal tags
        Set<String> networkInternalTagLinksSet = new HashSet<>();

        // stores a mapping of internal tag values for subnets.
        Map<String, String> subnetInternalTagsMap = new ConcurrentHashMap<>();
        // stores documentSelfLink for subnets internal tags
        Set<String> subnetInternalTagLinksSet = new HashSet<>();

        // Stored operation to signal completion to the Azure network enumeration once all the
        // stages are successfully completed.
        Operation operation;

        // The time when enumeration starts. This field is used also to identify stale resources
        // that should be deleted during deletion stage.
        long enumerationStartTimeInMicros;

        // List to temporary store all virtual network ids
        List<String> virtualNetworkIds = new ArrayList<>();

        // List to temporary store all subnet ids
        List<String> subnetIds = new ArrayList<>();

        // Stores the next page when retrieving Virtual Networks from Azure.
        String enumNextPageLink;

        String deletionNextPageLink;

        // Azure credentials.
        ApplicationTokenCredentials credentials;

        ResourceState resourceDeletionState;

        static class SubnetStateWithParentVNetId {
            String parentVNetId;
            SubnetState subnetState;

            SubnetStateWithParentVNetId(String parentVNetId, SubnetState subnetState) {
                this.parentVNetId = parentVNetId;
                this.subnetState = subnetState;
            }
        }

        NetworkEnumContext(ComputeEnumerateAdapterRequest request, Operation op) {
            this.request = request.original;
            this.endpointAuth = request.endpointAuth;
            this.parentCompute = request.parentCompute;

            this.stage = EnumerationStages.CLIENT;
            this.operation = op;
            this.resourceDeletionState = getDeletionState(
                    request.original.deletedResourceExpirationMicros);
        }

        // Clear results from previous page of resources.
        void clearPageTempData() {
            this.virtualNetworks.clear();
            this.networkStates.clear();
            this.subnets.clear();
            this.subnetStates.clear();
            this.resourceGroupStates.clear();
        }

        void addVirtualNetwork(VirtualNetwork virtualNetwork) {
            this.virtualNetworks.put(virtualNetwork.id, virtualNetwork);
            this.virtualNetworkIds.add(virtualNetwork.id);
        }

        void addSubnet(Subnet subnet, SubnetStateWithParentVNetId subnetStateWithParentVNetId) {
            this.subnets.put(subnet.id, subnetStateWithParentVNetId);
            this.subnetIds.add(subnet.id);
        }
    }

    /**
     * Sub stages describing the state machine of Azure Network enumeration flow.
     */
    private enum NetworkEnumStages {
        GET_VNETS,
        GET_SUBNETS,
        QUERY_RESOURCE_GROUP_STATES,
        QUERY_NETWORK_STATES,
        QUERY_SUBNET_STATES,
        CREATE_NETWORK_EXTERNAL_TAG_STATES,
        CREATE_NETWORK_INTERNAL_TAG_STATES,
        CREATE_UPDATE_NETWORK_STATES,
        UPDATE_TAG_LINKS,
        CREATE_SUBNET_INTERNAL_TAG_STATES,
        CREATE_UPDATE_SUBNET_STATES,
        DELETE_NETWORK_STATES,
        DELETE_SUBNET_STATES,
        FINISHED
    }

    public AzureNetworkEnumerationAdapterService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    private static void addScopeCriteria(Query.Builder qBuilder,
            Class<? extends ServiceDocument> stateClass, NetworkEnumContext ctx) {
        // Add parent compute host criteria
        qBuilder.addFieldClause(ResourceState.FIELD_NAME_COMPUTE_HOST_LINK,
                ctx.parentCompute.documentSelfLink);
        // Add TENANT_LINKS criteria
        QueryUtils.addTenantLinks(qBuilder, ctx.parentCompute.tenantLinks);
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        NetworkEnumContext ctx = new NetworkEnumContext(
                op.getBody(ComputeEnumerateAdapterRequest.class), op);
        AdapterUtils.validateEnumRequest(ctx.request);
        if (ctx.request.isMockRequest) {
            op.complete();
            return;
        }
        handleEnumeration(ctx);
    }

    /**
     * Creates the network states in the local document store based on the networks received from
     * the remote endpoint.
     *
     * @param context
     *            The local service context that has all the information needed to create the
     *            additional description states in the local system.
     */
    private void handleEnumeration(NetworkEnumContext context) {
        switch (context.stage) {

        case CLIENT:
            if (context.credentials == null) {
                try {
                    context.credentials = getAzureConfig(context.endpointAuth);
                } catch (Throwable e) {
                    logSevere(e);
                    context.error = e;
                    context.stage = EnumerationStages.ERROR;
                    handleEnumeration(context);
                    return;
                }
            }
            context.stage = EnumerationStages.ENUMERATE;
            handleEnumeration(context);
            break;
        case ENUMERATE:
            String enumKey = getEnumKey(context);
            switch (context.request.enumerationAction) {
            case START:
                logInfo(() -> String.format("Launching Azure network enumeration for %s", enumKey));
                context.request.enumerationAction = EnumerationAction.REFRESH;
                handleEnumeration(context);
                break;
            case REFRESH:
                context.enumerationStartTimeInMicros = Utils.getNowMicrosUtc();
                context.subStage = NetworkEnumStages.GET_VNETS;
                handleSubStage(context);
                break;
            case STOP:
                logInfo(() -> String.format("Azure network enumeration will be stopped for %s",
                        enumKey));
                context.stage = EnumerationStages.FINISHED;
                handleEnumeration(context);
                break;
            default:
                logSevere(() -> String.format("Unknown enumeration action %s",
                        context.request.enumerationAction));
                context.stage = EnumerationStages.ERROR;
                handleEnumeration(context);
                break;
            }
            break;
        case FINISHED:
            logInfo(() -> String.format("Azure network enumeration finished for %s",
                    getEnumKey(context)));
            context.operation.complete();
            break;
        case ERROR:
            logWarning(() -> String.format("Azure network enumeration error for %s",
                    getEnumKey(context)));
            context.operation.fail(context.error);
            break;
        default:
            String msg = String
                    .format("Unknown Azure network enumeration stage %s ",
                            context.stage.toString());
            logSevere(() -> msg);
            context.error = new IllegalStateException(msg);
            context.operation.fail(context.error);
        }
    }

    private void handleSubStage(NetworkEnumContext context) {
        switch (context.subStage) {
        case GET_VNETS:
            context.clearPageTempData();
            getVirtualNetworks(context, NetworkEnumStages.GET_SUBNETS);
            break;
        case GET_SUBNETS:
            getSubnets(context, NetworkEnumStages.QUERY_RESOURCE_GROUP_STATES);
            break;
        case QUERY_RESOURCE_GROUP_STATES:
            queryResourceGroupStates(context, NetworkEnumStages.QUERY_NETWORK_STATES);
            break;
        case QUERY_NETWORK_STATES:
            queryNetworkStates(context, NetworkEnumStages.QUERY_SUBNET_STATES);
            break;
        case QUERY_SUBNET_STATES:
            querySubnetStates(context, NetworkEnumStages.CREATE_NETWORK_EXTERNAL_TAG_STATES);
            break;
        case CREATE_NETWORK_EXTERNAL_TAG_STATES:
            createNetworkExternalTagStates(context,
                    NetworkEnumStages.CREATE_NETWORK_INTERNAL_TAG_STATES);
            break;
        case CREATE_NETWORK_INTERNAL_TAG_STATES:
            createNetworkInternalTagStates(context, NetworkEnumStages.CREATE_UPDATE_NETWORK_STATES);
            break;
        case CREATE_UPDATE_NETWORK_STATES:
            createUpdateNetworkStates(context, NetworkEnumStages.UPDATE_TAG_LINKS);
            break;
        case UPDATE_TAG_LINKS:
            updateNetworkTagLinks(context).whenComplete(
                    thenHandleSubStage(context,
                            NetworkEnumStages.CREATE_SUBNET_INTERNAL_TAG_STATES));
            break;
        case CREATE_SUBNET_INTERNAL_TAG_STATES:
            createSubnetInternalTagStates(context, NetworkEnumStages.CREATE_UPDATE_SUBNET_STATES);
            break;
        case CREATE_UPDATE_SUBNET_STATES:
            createUpdateSubnetStates(context, NetworkEnumStages.DELETE_NETWORK_STATES);
            break;
        case DELETE_NETWORK_STATES:
            disassociateNetworkStates(context, NetworkEnumStages.DELETE_SUBNET_STATES);
            break;
        case DELETE_SUBNET_STATES:
            disassociateSubnetStates(context, NetworkEnumStages.FINISHED);
            break;
        case FINISHED:
            context.stage = EnumerationStages.FINISHED;
            handleEnumeration(context);
            break;
        default:
            break;
        }
    }

    /**
     * {@code handleSubStage} version suitable for chaining to {@code DeferredResult.whenComplete}.
     */
    private BiConsumer<NetworkEnumContext, Throwable> thenHandleSubStage(NetworkEnumContext context,
            NetworkEnumStages next) {
        // NOTE: In case of error 'ignoreCtx' is null so use passed context!
        return (ignoreCtx, exc) -> {
            if (exc != null) {
                context.stage = EnumerationStages.ERROR;
                handleEnumeration(context);
                return;
            }
            handleSubStage(context, next);
        };
    }

    /**
     * Shortcut method that sets the next stage into the context and delegates to
     * {@link #handleSubStage(NetworkEnumContext)}.
     */
    private void handleSubStage(NetworkEnumContext ctx, NetworkEnumStages nextStage) {
        logFine(() -> String.format("Transition to " + nextStage));
        ctx.subStage = nextStage;
        handleSubStage(ctx);
    }

    private void handleError(NetworkEnumContext ctx, Throwable e) {
        logSevere(() -> String.format("Failed at stage %s with exception: %s", ctx.stage,
                Utils.toString(e)));
        ctx.error = e;
        ctx.stage = EnumerationStages.ERROR;
        handleEnumeration(ctx);
    }

    /**
     * Return a key to uniquely identify enumeration for compute host instance.
     */
    private String getEnumKey(NetworkEnumContext ctx) {
        return ctx.request.getEnumKey();
    }

    /**
     * Retrieve a page of Virtual Network resources from azure, based on the provided compute host
     * description.
     */
    private void getVirtualNetworks(NetworkEnumContext context, NetworkEnumStages next) {
        logFine(() -> "Enumerating Virtual Networks from Azure.");

        URI uri;
        if (context.enumNextPageLink == null) {
            // First request to fetch Virtual Networks from Azure.
            String uriStr = AdapterUriUtil.expandUriPathTemplate(LIST_VIRTUAL_NETWORKS_URI,
                    context.endpointAuth.userLink);
            uri = UriUtils.extendUriWithQuery(
                    UriUtils.buildUri(uriStr),
                    QUERY_PARAM_API_VERSION, NETWORK_REST_API_VERSION);
        } else {
            // Request to fetch next page of Virtual Networks from Azure.
            uri = UriUtils.buildUri(context.enumNextPageLink);
        }

        final Operation operation = Operation.createGet(uri);
        operation.addRequestHeader(Operation.ACCEPT_HEADER, Operation.MEDIA_TYPE_APPLICATION_JSON);
        operation.addRequestHeader(Operation.CONTENT_TYPE_HEADER,
                Operation.MEDIA_TYPE_APPLICATION_JSON);
        try {
            operation.addRequestHeader(Operation.AUTHORIZATION_HEADER,
                    AUTH_HEADER_BEARER_PREFIX
                            + context.credentials.getToken(AzureUtils.getAzureBaseUri()));
        } catch (Exception ex) {
            this.handleError(context, ex);
            return;
        }

        operation.setCompletion((op, ex) -> {
            op.complete();
            if (ex != null) {
                handleError(context, ex);
                return;
            }

            VirtualNetworkListResult results = op.getBody(VirtualNetworkListResult.class);
            List<VirtualNetwork> virtualNetworks = results.value;

            // If there are no Virtual Networks in Azure we directly skip over to deletion phase.
            if (virtualNetworks == null || virtualNetworks.size() == 0) {
                handleSubStage(context, NetworkEnumStages.DELETE_NETWORK_STATES);
                return;
            }

            // Store next page link.
            context.enumNextPageLink = results.nextLink;

            logFine(() -> String.format("Retrieved %d Virtual Networks from Azure",
                    virtualNetworks.size()));
            logFine(() -> String.format("Next page link %s", context.enumNextPageLink));

            // Store virtual networks for further processing during the next stages.
            virtualNetworks.forEach(virtualNetwork -> {
                context.addVirtualNetwork(virtualNetwork);
            });

            logFine(() -> String.format("Processing %d virtual networks",
                    context.virtualNetworks.size()));

            handleSubStage(context, next);
        });

        sendRequest(operation);
    }

    /**
     * Based on the retrieved page of Virtual Network resources from azure, compose the structures
     * holding Azure Subnets data.
     */
    private void getSubnets(NetworkEnumContext context, NetworkEnumStages next) {
        logFine(() -> "Enumerating Subnets from Azure.");

        context.virtualNetworks.values().forEach(virtualNetwork -> {
            if (virtualNetwork.properties.subnets != null) {
                virtualNetwork.properties.subnets.forEach(subnet -> {

                    SubnetState subnetState = buildSubnetState(subnet,
                            context.parentCompute.tenantLinks, context.request.endpointLink,
                            virtualNetwork.location, context.parentCompute.documentSelfLink);
                    SubnetStateWithParentVNetId subnetStateWithParentVNetId = new SubnetStateWithParentVNetId(
                            virtualNetwork.id, subnetState);

                    context.addSubnet(subnet, subnetStateWithParentVNetId);
                });
            }
        });

        handleSubStage(context, next);
    }

    /**
     * Map Azure subnet to {@link SubnetState}.
     */
    private SubnetState buildSubnetState(Subnet subnet, List<String> tenantLinks,
            String endpointLink, String location, String parentLink) {
        if (subnet == null) {
            throw new IllegalArgumentException("Cannot map Subnet to subnet state for null "
                    + "instance.");
        }

        SubnetState subnetState = new SubnetState();
        subnetState.id = subnet.id;
        subnetState.name = subnet.name;
        if (subnet.properties != null) {
            subnetState.subnetCIDR = subnet.properties.addressPrefix;
        }

        subnetState.tenantLinks = tenantLinks;
        subnetState.endpointLink = endpointLink;
        AdapterUtils.addToEndpointLinks(subnetState, endpointLink);

        subnetState.supportPublicIpAddress = true;
        subnetState.computeHostLink = parentLink;

        subnetState.customProperties = new HashMap<>();
        subnetState.regionId = location;
        // on Azure, zoneId is the same as regionId
        subnetState.zoneId = location;
        if (AzureConstants.GATEWAY_SUBNET_NAME.equalsIgnoreCase(subnet.name)) {
            // This is a subnet gateway. Mark it for infrastructure use only.
            subnetState.customProperties.put(ComputeProperties.INFRASTRUCTURE_USE_PROP_NAME,
                    Boolean.TRUE.toString());
        }
        return subnetState;
    }

    /**
     * Query resource group states stored in the local document store based on the retrieved azure
     * virtual networks.
     */
    private void queryResourceGroupStates(NetworkEnumContext context, NetworkEnumStages next) {
        List<String> resourceGroupIds = context.virtualNetworks.values().stream()
                .map(vNet -> AzureUtils.getResourceGroupId(vNet.id))
                .collect(Collectors.toList());

        Query.Builder qBuilder = Builder.create()
                .addKindFieldClause(ResourceGroupState.class)
                .addInClause(ResourceState.FIELD_NAME_ID, resourceGroupIds)
                .addCompositeFieldClause(
                        ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                        ComputeProperties.RESOURCE_TYPE_KEY,
                        ResourceGroupStateType.AzureResourceGroup.name());

        addScopeCriteria(qBuilder, ResourceGroupState.class, context);

        QueryTask qt = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .setResultLimit(getQueryResultLimit())
                .setQuery(qBuilder.build())
                .build();
        qt.tenantLinks = context.parentCompute.tenantLinks;

        QueryUtils.startInventoryQueryTask(this, qt)
                .whenComplete((queryTask, e) -> {
                    if (e != null) {
                        handleError(context, e);
                        return;
                    }

                    // If no match found, continue to query network states
                    if (queryTask.results.nextPageLink == null) {
                        logFine(() -> "No matching resource group state found");
                        handleSubStage(context, next);
                        return;
                    }
                    context.enumNextPageLink = queryTask.results.nextPageLink;
                    getResourceGroupStatesHelper(context, next);
                });
    }

    private void getResourceGroupStatesHelper(NetworkEnumContext context, NetworkEnumStages next) {
        Operation.CompletionHandler completionHandler = (o, e) -> {
            if (e != null) {
                handleError(context, e);
                return;
            }
            QueryTask queryTask = o.getBody(QueryTask.class);

            context.enumNextPageLink = queryTask.results.nextPageLink;

            queryTask.results.documents.values().forEach(document -> {
                ResourceGroupState rgState = Utils.fromJson(document, ResourceGroupState.class);
                context.resourceGroupStates.put(rgState.id, rgState.documentSelfLink);
            });

            if (context.enumNextPageLink != null) {
                getResourceGroupStatesHelper(context, next);
            } else {
                logFine(() -> "Finished getting resource group states");
                handleSubStage(context, next);
                return;
            }
        };
        logFine(() -> String.format("Querying page [%s] for getting local disk states",
                context.enumNextPageLink));
        sendRequest(
                Operation.createGet(createInventoryUri(this.getHost(), context.enumNextPageLink))
                        .setCompletion(completionHandler));
    }

    /**
     * Query network states stored in the local document store based on the retrieved azure virtual
     * networks.
     */
    private void queryNetworkStates(NetworkEnumContext context, NetworkEnumStages next) {
        logFine(() -> "Query Network States from local document store.");

        Query.Builder qBuilder = Query.Builder.create()
                .addKindFieldClause(NetworkState.class)
                .addInClause(NetworkState.FIELD_NAME_ID, context.virtualNetworks.keySet());

        addScopeCriteria(qBuilder, NetworkState.class, context);

        QueryTask qt = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .setResultLimit(getQueryResultLimit())
                .setQuery(qBuilder.build())
                .build();
        qt.tenantLinks = context.parentCompute.tenantLinks;

        QueryUtils.startInventoryQueryTask(this, qt)
                .whenComplete((queryTask, e) -> {
                    if (e != null) {
                        handleError(context, e);
                        return;
                    }

                    // If there are no matches, there is nothing to update.
                    if (queryTask.results.nextPageLink == null) {
                        logFine(() -> "No matching network states for Azure virtual networks");
                        handleSubStage(context, next);
                        return;
                    }
                    context.enumNextPageLink = queryTask.results.nextPageLink;
                    getNetworkStatesHelper(context, next);
                });
    }

    private void getNetworkStatesHelper(NetworkEnumContext context, NetworkEnumStages next) {
        Operation.CompletionHandler completionHandler = (o, e) -> {
            if (e != null) {
                handleError(context, e);
                return;
            }
            QueryTask queryTask = o.getBody(QueryTask.class);

            context.enumNextPageLink = queryTask.results.nextPageLink;

            queryTask.results.documents.values().forEach(network -> {
                NetworkState networkState = Utils.fromJson(network, NetworkState.class);
                context.networkStates.put(networkState.id, networkState);
            });

            if (context.enumNextPageLink != null) {
                getNetworkStatesHelper(context, next);
            } else {
                logFine(() -> "Finished getting network states for azure virtual networks");
                handleSubStage(context, next);
                return;
            }
        };
        logFine(() -> String.format("Querying page [%s] for getting network states",
                context.enumNextPageLink));
        sendRequest(
                Operation.createGet(createInventoryUri(this.getHost(), context.enumNextPageLink))
                        .setCompletion(completionHandler));
    }

    /**
     * Query subnet states stored in the local document store based on the retrieved azure subnets.
     */
    private void querySubnetStates(NetworkEnumContext context, NetworkEnumStages next) {
        if (context.subnets == null || context.subnets.isEmpty()) {
            handleSubStage(context, next);
            return;
        }

        logFine(() -> "Query Subnet States from local document store.");
        Builder qBuilder = Query.Builder.create()
                .addKindFieldClause(SubnetState.class)
                .addInClause(SubnetState.FIELD_NAME_ID, context.subnets.keySet());

        addScopeCriteria(qBuilder, SubnetState.class, context);

        QueryTask q = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .setResultLimit(getQueryResultLimit())
                .setQuery(qBuilder.build())
                .build();
        q.tenantLinks = context.parentCompute.tenantLinks;

        QueryUtils.startInventoryQueryTask(this, q)
                .whenComplete((queryTask, e) -> {
                    if (e != null) {
                        handleError(context, e);
                        return;
                    }
                    // If there are no matches, there is nothing to update.
                    if (queryTask.results.nextPageLink == null) {
                        logFine(() -> "No matching subnet states found for Azure subnets");
                        handleSubStage(context, next);
                        return;
                    }
                    context.enumNextPageLink = queryTask.results.nextPageLink;
                    getSubnetStatesHelper(context, next);
                });
    }

    private void getSubnetStatesHelper(NetworkEnumContext context, NetworkEnumStages next) {
        Operation.CompletionHandler completionHandler = (o, e) -> {
            if (e != null) {
                handleError(context, e);
                return;
            }
            QueryTask queryTask = o.getBody(QueryTask.class);

            context.enumNextPageLink = queryTask.results.nextPageLink;

            queryTask.results.documents.values().forEach(result -> {

                SubnetState subnetState = Utils.fromJson(result, SubnetState.class);
                context.subnetStates.put(subnetState.id, subnetState.documentSelfLink);
            });

            if (context.enumNextPageLink != null) {
                getSubnetStatesHelper(context, next);
            } else {
                logFine(() -> "Finished getting subnet states for Azure subnets");
                handleSubStage(context, next);
                return;
            }
        };
        logFine(() -> String.format("Querying page [%s] for getting subnet states",
                context.enumNextPageLink));
        sendRequest(
                Operation.createGet(createInventoryUri(this.getHost(), context.enumNextPageLink))
                        .setCompletion(completionHandler));
    }

    /**
     * Create external and internal tags resources for Networks.
     */
    private void createNetworkExternalTagStates(NetworkEnumContext context,
            NetworkEnumStages next) {
        logFine("Create or update Tag States for discovered Networks with the actual state in Azure.");

        if (context.virtualNetworks.isEmpty()) {
            logFine("No vNet tags found to create.");
            handleSubStage(context, next);
            return;
        }

        // POST each of the tags. If a tag exists it won't be created again. We don't want the name
        // tags, so filter them out
        List<Operation> operations = context.virtualNetworks.values().stream()
                .filter(vNet -> vNet.tags != null && !vNet.tags.isEmpty())
                .flatMap(vNet -> vNet.tags.entrySet().stream())
                .map(vNetTagEntry -> newTagState(vNetTagEntry.getKey(), vNetTagEntry.getValue(),
                        true, context.parentCompute.tenantLinks))
                .map(vNetTagState -> Operation
                        .createPost(this, TagService.FACTORY_LINK)
                        .setBody(vNetTagState))
                .collect(Collectors.toList());

        if (operations.isEmpty()) {
            handleSubStage(context, next);
        } else {
            OperationJoin.create(operations).setCompletion((ops, exs) -> {
                if (exs != null && !exs.isEmpty()) {
                    handleError(context, exs.values().iterator().next());
                } else {
                    handleSubStage(context, next);
                }
            }).sendWith(this);
        }
    }

    /**
     * Create internal tag states for networks.
     */
    private void createNetworkInternalTagStates(NetworkEnumContext context,
            NetworkEnumStages next) {
        TagState internalTypeTag = newTagState(PhotonModelConstants.TAG_KEY_TYPE,
                NETWORK_TAG_TYPE_VALUE, false, context.parentCompute.tenantLinks);
        // operation to create tag "type" for subnets.
        Operation.createPost(this, TagService.FACTORY_LINK)
                .setBody(internalTypeTag)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere("Error creating internal type tag for networks %s",
                                e.getMessage());
                    } else {
                        context.networkInternalTagsMap.put(PhotonModelConstants.TAG_KEY_TYPE,
                                NETWORK_TAG_TYPE_VALUE);
                        context.networkInternalTagLinksSet.add(internalTypeTag.documentSelfLink);
                    }
                    handleSubStage(context, next);
                }).sendWith(this);
    }

    /**
     * Create new network states or update matching network states with the actual state in Azure.
     */
    private void createUpdateNetworkStates(NetworkEnumContext context, NetworkEnumStages next) {
        logFine(() -> "Create or update Network States with the actual state in Azure.");

        if (context.virtualNetworks.size() == 0) {
            logFine(() -> "No virtual networks found to create/update.");
            handleSubStage(context, next);
            return;
        }

        Stream<Operation> operations = context.virtualNetworks.values().stream()
                .map(virtualNetwork -> {
                    NetworkState existingNetworkState = context.networkStates
                            .get(virtualNetwork.id);

                    NetworkState networkState = buildNetworkState(context, virtualNetwork,
                            existingNetworkState);

                    setTagLinksToResourceState(networkState, virtualNetwork.tags, true);

                    if (existingNetworkState == null) {
                        // set internal tags as tagLinks for networks to be newly created.
                        setTagLinksToResourceState(networkState, context.networkInternalTagsMap,
                                false);
                    } else {
                        // for already existing networks, add internal tags only if missing
                        if (networkState.tagLinks == null || networkState.tagLinks.isEmpty()) {
                            setTagLinksToResourceState(networkState, context.networkInternalTagsMap,
                                    false);
                        } else {
                            context.networkInternalTagLinksSet.stream()
                                    .filter(tagLink -> !networkState.tagLinks.contains(tagLink))
                                    .map(tagLink -> networkState.tagLinks.add(tagLink))
                                    .collect(Collectors.toSet());
                        }
                    }

                    CompletionHandler handler = (completedOp, failure) -> {
                        if (failure != null) {
                            // Process successful operations only.
                            logWarning(() -> String.format("Error: %s", failure.getMessage()));
                            return;
                        }

                        NetworkState result;

                        if (completedOp.getStatusCode() == Operation.STATUS_CODE_NOT_MODIFIED) {
                            // Use the original networkState as result
                            result = networkState;
                        } else {
                            result = completedOp.getBody(NetworkState.class);
                        }

                        context.networkStates.put(result.id, result);
                    };

                    return existingNetworkState != null ?
                    // Update case.
                    Operation.createPatch(this, networkState.documentSelfLink)
                            .setBody(networkState)
                            .setCompletion(handler) :
                    // Create case.
                    Operation.createPost(getHost(), NetworkService.FACTORY_LINK)
                            .setBody(networkState)
                            .setCompletion(handler);
                });

        OperationJoin.create(operations).setCompletion((ops, failures) -> {
            // We don't want to fail the whole data collection if some of the so we don't care of
            // any potential operation failures. They are already logged at individual operation
            // level.

            logFine(() -> "Finished updating network states.");

            handleSubStage(context, next);
        }).sendWith(this);
    }

    private DeferredResult<NetworkEnumContext> updateNetworkTagLinks(NetworkEnumContext context) {
        logFine(() -> "Create or update Network States' tags with the actual tags in Azure.");

        if (context.virtualNetworks.size() == 0) {
            logFine(() -> "No local networks to be updated so there are no tags to update.");
            return DeferredResult.completed(context);
        } else {

            List<DeferredResult<Set<String>>> updateNetwLinksOps = new ArrayList<>();
            // update tag links for the existing NetworkStates
            for (String vnetId : context.networkStates.keySet()) {
                if (!context.virtualNetworks.containsKey(vnetId)) {
                    continue; // this is not a network to update
                }
                VirtualNetwork vNet = context.virtualNetworks.get(vnetId);
                NetworkState existingNetworkState = context.networkStates.get(vnetId);
                Map<String, String> remoteTags = new HashMap<>();
                if (vNet.tags != null && !vNet.tags.isEmpty()) {
                    for (Entry<String, String> vNetTagEntry : vNet.tags.entrySet()) {
                        remoteTags.put(vNetTagEntry.getKey(), vNetTagEntry.getValue());
                    }
                }
                updateNetwLinksOps
                        .add(updateLocalTagStates(this, existingNetworkState, remoteTags, null));
            }

            return DeferredResult.allOf(updateNetwLinksOps).thenApply(gnore -> context);
        }
    }

    /**
     * Create internal tag resources for subnets.
     */
    private void createSubnetInternalTagStates(NetworkEnumContext context, NetworkEnumStages next) {
        TagState internalTypeTag = newTagState(PhotonModelConstants.TAG_KEY_TYPE,
                SUBNET_TAG_TYPE_VALUE, false, context.parentCompute.tenantLinks);
        // operation to create tag "type" for subnets.
        Operation.createPost(this, TagService.FACTORY_LINK)
                .setBody(internalTypeTag)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere("Error creating internal type tag for subnets %s",
                                e.getMessage());
                    } else {
                        context.subnetInternalTagsMap.put(PhotonModelConstants.TAG_KEY_TYPE,
                                SUBNET_TAG_TYPE_VALUE);
                        context.subnetInternalTagLinksSet.add(internalTypeTag.documentSelfLink);
                    }
                    handleSubStage(context, next);
                }).sendWith(this);
    }

    /**
     * Create new subnet states or updates matching subnet states with the actual state in Azure.
     */
    private void createUpdateSubnetStates(NetworkEnumContext context, NetworkEnumStages next) {
        if (context.subnets.size() == 0) {
            logFine(() -> "No network states available for update.");
            handleSubStage(context, next);
            return;
        }

        Stream<Operation> operations = context.subnets.keySet().stream().map(subnetId -> {
            SubnetStateWithParentVNetId subnetStateWithParentVNetId = context.subnets.get(subnetId);

            SubnetState subnetState = subnetStateWithParentVNetId.subnetState;
            if (!context.subnetStates.containsKey(subnetId)) {
                // set internal tags as tagLinks for subnets to be newly created.
                setTagLinksToResourceState(subnetState, context.subnetInternalTagsMap, false);
            } else {
                // for already existing subnets, add internal tags only if missing
                if (subnetState.tagLinks == null || subnetState.tagLinks.isEmpty()) {
                    setTagLinksToResourceState(subnetState, context.subnetInternalTagsMap,
                            false);
                } else {
                    context.subnetInternalTagLinksSet.stream()
                            .filter(tagLink -> !subnetState.tagLinks.contains(tagLink))
                            .map(tagLink -> subnetState.tagLinks.add(tagLink))
                            .collect(Collectors.toSet());
                }
            }

            // Update networkLink with "latest" (either created or updated)
            // NetworkState.documentSelfLink
            NetworkState networkState = context.networkStates
                    .get(subnetStateWithParentVNetId.parentVNetId);
            if (networkState != null) {
                subnetState.networkLink = networkState.documentSelfLink;
            } else {
                logWarning(() -> String.format("Network state corresponding to subnet with"
                        + " name [%s] was not found. Network Link is left empty.",
                        subnetState.name));
            }
            subnetState.endpointLink = context.request.endpointLink;
            subnetState.computeHostLink = context.parentCompute.documentSelfLink;
            AdapterUtils.addToEndpointLinks(subnetState, context.request.endpointLink);

            return context.subnetStates.containsKey(subnetId) ?
            // Update case
            Operation.createPatch(this, context.subnetStates.get(subnetId))
                    .setBody(subnetState) :
            // Create case.
            Operation.createPost(getHost(), SubnetService.FACTORY_LINK)
                    .setBody(subnetState);
        });

        OperationJoin.create(operations).setCompletion((ops, failures) -> {
            if (failures != null) {
                // We don't want to fail the whole data collection if some of the
                // operation fails.
                failures.values().forEach(ex -> logWarning(() -> String.format("Error: %s",
                        ex.getMessage())));
            }

            // Process successful operations.
            ops.values().stream()
                    .filter(operation -> failures != null && !failures
                            .containsKey(operation.getId()))
                    .filter(operation -> operation
                            .getStatusCode() != Operation.STATUS_CODE_NOT_MODIFIED)
                    .forEach(operation -> {
                        SubnetState subnetState = operation.getBody(SubnetState.class);
                        context.subnets.get(subnetState.id).subnetState = subnetState;
                    });

            if (context.enumNextPageLink != null) {
                logFine(() -> "Fetch next page of Virtual Networks from Azure.");
                handleSubStage(context, NetworkEnumStages.GET_VNETS);
                return;
            }

            logFine(() -> "Finished updating network states");

            handleSubStage(context, next);
        }).sendWith(this);
    }

    /**
     * @param localNetworkState
     *            null to do a create, non null to update an existing network state
     */
    private NetworkState buildNetworkState(NetworkEnumContext context,
            VirtualNetwork azureVirtualNetwork, NetworkState localNetworkState) {

        NetworkState resultNetworkState = new NetworkState();
        if (localNetworkState != null) {
            resultNetworkState.id = localNetworkState.id;
            resultNetworkState.authCredentialsLink = localNetworkState.authCredentialsLink;
            resultNetworkState.documentSelfLink = localNetworkState.documentSelfLink;
            resultNetworkState.groupLinks = localNetworkState.groupLinks;
            resultNetworkState.resourcePoolLink = localNetworkState.resourcePoolLink;
            resultNetworkState.tagLinks = localNetworkState.tagLinks;
            resultNetworkState.endpointLinks = localNetworkState.endpointLinks;
        } else {
            resultNetworkState.id = azureVirtualNetwork.id;
            resultNetworkState.authCredentialsLink = context.endpointAuth.documentSelfLink;
            resultNetworkState.resourcePoolLink = context.request.resourcePoolLink;
        }

        resultNetworkState.name = azureVirtualNetwork.name;
        resultNetworkState.regionId = azureVirtualNetwork.location;
        resultNetworkState.endpointLink = context.request.endpointLink;
        resultNetworkState.computeHostLink = context.parentCompute.documentSelfLink;
        AdapterUtils.addToEndpointLinks(resultNetworkState, context.request.endpointLink);

        AddressSpace addressSpace = azureVirtualNetwork.properties.addressSpace;
        if (addressSpace != null
                && addressSpace.addressPrefixes != null
                && addressSpace.addressPrefixes.size() > 0) {

            // TODO: Get the first address prefix for now.
            // Trim any whitespaces that might be presented (VSYM-4132).
            resultNetworkState.subnetCIDR = addressSpace.addressPrefixes.get(0).trim();
        }

        // Add gateway as custom property in case gateway is defined
        String gatewayId = AzureUtils.getVirtualNetworkGatewayId(azureVirtualNetwork);
        if (gatewayId != null) {
            resultNetworkState.customProperties = Collections.singletonMap(
                    ComputeProperties.FIELD_VIRTUAL_GATEWAY, gatewayId);
            logFine(() -> String.format("Added Gateway %s for Network State %s.", gatewayId,
                    resultNetworkState.name));
        }

        // TODO: There is no Azure Network Adapter Service. Add a default reference since this is
        // required field.
        resultNetworkState.instanceAdapterReference = AdapterUriUtil.buildAdapterUri(
                getHost().getPort(),
                DEFAULT_INSTANCE_ADAPTER_REFERENCE);
        String resourceGroupId = AzureUtils.getResourceGroupId(azureVirtualNetwork.id);
        String resourceGroupStateLink = context.resourceGroupStates.get(resourceGroupId);
        if (resourceGroupStateLink != null) {
            if (resultNetworkState.groupLinks == null) {
                resultNetworkState.groupLinks = new HashSet<>();
            }
            // Add if a resource group state with this name exists.
            // If not then the resource group was not enumerated yet. The groupLink will be filled
            // during the next enumeration cycle.
            resultNetworkState.groupLinks.add(resourceGroupStateLink);
        }

        resultNetworkState.tenantLinks = context.parentCompute.tenantLinks;

        return resultNetworkState;
    }

    /**
     * Delete local network states that no longer exist in Azure.
     * <p>
     * The logic works by recording a timestamp when enumeration starts. This timestamp is used to
     * lookup resources which haven't been touched as part of current enumeration cycle.
     */
    private void disassociateNetworkStates(NetworkEnumContext context, NetworkEnumStages next) {
        logFine(() -> "Disassociate Network States that no longer exists in Azure.");

        Builder qBuilder = Query.Builder.create()
                .addKindFieldClause(NetworkState.class)
                .addRangeClause(NetworkState.FIELD_NAME_UPDATE_TIME_MICROS,
                        NumericRange.createLessThanRange(context.enumerationStartTimeInMicros));

        addScopeCriteria(qBuilder, NetworkState.class, context);

        QueryTask q = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .setQuery(qBuilder.build())
                .setResultLimit(getQueryResultLimit())
                .build();
        q.tenantLinks = context.parentCompute.tenantLinks;

        logFine(() -> "Querying Network States for disassociation.");

        // Add deleted NetworkStates to the list.
        sendDisassociateQueryTask(q, context, next);
    }

    /**
     * Delete subnet states that no longer exist in Azure.
     * <p>
     * The logic works by recording a timestamp when enumeration starts. This timestamp is used to
     * lookup resources which haven't been touched as part of current enumeration cycle and belong
     * to networks touched by this enumeration cycle (either created/updated/deleted).
     */
    private void disassociateSubnetStates(NetworkEnumContext context, NetworkEnumStages next) {
        Builder builder = Query.Builder.create()
                .addKindFieldClause(SubnetState.class)
                .addFieldClause(SubnetState.FIELD_NAME_LIFECYCLE_STATE,
                        LifecycleState.PROVISIONING.name(), MatchType.TERM,
                        Occurance.MUST_NOT_OCCUR)
                .addRangeClause(SubnetState.FIELD_NAME_UPDATE_TIME_MICROS,
                        NumericRange.createLessThanRange(context.enumerationStartTimeInMicros));

        addScopeCriteria(builder, SubnetState.class, context);

        QueryTask q = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .setQuery(builder.build())
                .setResultLimit(getQueryResultLimit())
                .build();
        q.tenantLinks = context.parentCompute.tenantLinks;

        logFine(() -> "Querying Subnet States for disassociation.");
        sendDisassociateQueryTask(q, context, next);
    }

    private void sendDisassociateQueryTask(QueryTask q, NetworkEnumContext context,
            NetworkEnumStages next) {

        QueryUtils.startInventoryQueryTask(this, q)
                .whenComplete((queryTask, e) -> {
                    if (e != null) {
                        handleError(context, e);
                        return;
                    }

                    context.deletionNextPageLink = queryTask.results.nextPageLink;

                    handleDisassociateQueryTaskResult(context, next);
                });
    }

    /**
     * Get next page of query results and disassociate then.
     */
    private void handleDisassociateQueryTaskResult(NetworkEnumContext context,
            NetworkEnumStages next) {

        if (context.deletionNextPageLink == null) {
            logFine(() -> "Finished disassociation stage.");
            handleSubStage(context, next);
            return;
        }

        logFine(() -> String.format("Querying page [%s] for resources to be disassociated",
                context.deletionNextPageLink));
        sendRequest(Operation
                .createGet(createInventoryUri(this.getHost(), context.deletionNextPageLink))
                .setCompletion((completedOp, ex) -> {
                    if (ex != null) {
                        handleError(context, ex);
                        return;
                    }

                    QueryTask queryTask = completedOp.getBody(QueryTask.class);

                    if (queryTask.results.documentCount > 0) {
                        // Disassociate all matching states.
                        List<Operation> operations = queryTask.results.documentLinks.stream()
                                .filter(link -> shouldDelete(context, queryTask, link))
                                .map(link -> {
                                    NetworkState networkState = Utils.fromJson(
                                            queryTask.results.documents.get(link),
                                            NetworkState.class);

                                    return PhotonModelUtils.createRemoveEndpointLinksOperation(
                                            this,
                                            context.request.endpointLink,
                                            networkState);
                                })
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList());

                        if (!operations.isEmpty()) {
                            OperationJoin.create(operations).sendWith(this);
                        }
                    }

                    // Store the next page in the context
                    context.deletionNextPageLink = queryTask.results.nextPageLink;

                    // Handle next page of results.
                    handleDisassociateQueryTaskResult(context, next);
                }));
    }

    /**
     * Checks whether an entity should be delete.
     */
    private boolean shouldDelete(NetworkEnumContext context, QueryTask queryTask, String link) {
        if (link.startsWith(NetworkService.FACTORY_LINK)) {
            NetworkState networkState = Utils
                    .fromJson(queryTask.results.documents.get(link), NetworkState.class);
            if (context.virtualNetworkIds.contains(networkState.id)) {
                return false;
            }
        } else if (link.startsWith(SubnetService.FACTORY_LINK)) {
            SubnetState subnetState = Utils
                    .fromJson(queryTask.results.documents.get(link), SubnetState.class);
            if (context.subnetIds.contains(subnetState.id)) {
                return false;
            }
        }
        return true;
    }

}
