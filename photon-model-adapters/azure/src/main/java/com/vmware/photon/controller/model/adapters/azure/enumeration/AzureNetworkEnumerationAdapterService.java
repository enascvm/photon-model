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

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.microsoft.azure.credentials.ApplicationTokenCredentials;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
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
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.tasks.QueryUtils;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;

/**
 * Enumeration adapter for data collection of Network related resources on Azure.
 * <p>
 * The state machine that is implemented is the following:
 * <ul>
 * <li>Get a page of virtual networks from azure</li>
 * <li>Get subnets for the retrieved virtual networks.</li>
 * <li>Get local network states matching the azure  network ids.</li>
 * <li>Get local subnet states matching the azure subnets ids.</li>
 * <li>Create/Update the matching network states</li>
 * <li>Create/Update the matching subnet states</li>
 * <li>Delete network states that are not touched during the previous stages because they
 * are stale entries, no longer existing in Azure.</li>
 * <li>Delete subnet states that are not touched during the previous stages and belong to the
 * touched networks during the previous states.</li>
 * </ul>
 */
public class AzureNetworkEnumerationAdapterService extends StatelessService {
    public static final String SELF_LINK = AzureUriPaths.AZURE_NETWORK_ENUMERATION_ADAPTER;

    /**
     * The local service context that is used to identify and create/update a representative
     * set of network states based on the enumeration data received from Azure.
     */
    public static class NetworkEnumContext {
        static class SubnetStateWithParentVNetId {
            String parentVNetId;
            SubnetState subnetState;

            SubnetStateWithParentVNetId(String parentVNetId, SubnetState subnetState) {
                this.parentVNetId = parentVNetId;
                this.subnetState = subnetState;
            }
        }

        ComputeEnumerateResourceRequest request;
        ComputeStateWithDescription parentCompute;

        EnumerationStages stage;
        NetworkEnumStages subStage;

        // Used to store an error while transferring to the error stage.
        Throwable error;

        AuthCredentialsService.AuthCredentialsServiceState parentAuth;

        // Virtual Networks page as fetched from Azure
        // key -> Virtual Network id; value -> Virtual Network.
        Map<String, VirtualNetwork> virtualNetworks = new ConcurrentHashMap<>();
        // Network States stored in local document store.
        // key -> Network State id (matching Azure Virtual Network id); value -> Network State
        Map<String, NetworkState> networkStates = new ConcurrentHashMap<>();
        // Stores the link to NetworkStates that created/updated/deleted in the current enumeration
        // in the local document store. This list is used during SubnetStates deletion.
        List<String> modifiedNetworksLinks = new ArrayList<>();
        // Stores the map of resource groups state ids to document self links.
        // key -> resource group id; value - link to the local ResourceGroupState object.
        Map<String, String> resourceGroupStates = new ConcurrentHashMap<>();

        Map<String, SubnetStateWithParentVNetId> subnets = new ConcurrentHashMap<>();
        // Local subnet states map.
        // Key -> Subnet state id; value -> subnet state documentLink.
        Map<String, String> subnetStates = new ConcurrentHashMap<>();

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

        NetworkEnumContext(ComputeEnumerateAdapterRequest request, Operation op) {
            this.request = request.computeEnumerateResourceRequest;
            this.parentAuth = request.parentAuth;
            this.parentCompute = request.parentCompute;

            this.stage = EnumerationStages.CLIENT;
            this.operation = op;
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
        CREATE_UPDATE_NETWORK_STATES,
        CREATE_UPDATE_SUBNET_STATES,
        DELETE_NETWORK_STATES,
        DELETE_SUBNET_STATES,
        FINISHED
    }

    private Set<String> ongoingEnumerations = new ConcurrentSkipListSet<>();

    public AzureNetworkEnumerationAdapterService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        NetworkEnumContext ctx = new NetworkEnumContext(op.getBody
                (ComputeEnumerateAdapterRequest.class), op);
        AdapterUtils.validateEnumRequest(ctx.request);
        if (ctx.request.isMockRequest) {
            op.complete();
            return;
        }
        handleNetworkEnumeration(ctx);
    }

    /**
     * Creates the network states in the local document store based on the networks
     * received from the remote endpoint.
     *
     * @param context The local service context that has all the information needed to create the
     *                additional description states in the local system.
     */
    private void handleNetworkEnumeration(NetworkEnumContext context) {
        switch (context.stage) {

        case CLIENT:
            if (context.credentials == null) {
                try {
                    context.credentials = getAzureConfig(context.parentAuth);
                } catch (Throwable e) {
                    logSevere(e);
                    context.error = e;
                    context.stage = EnumerationStages.ERROR;
                    handleNetworkEnumeration(context);
                    return;
                }
            }
            context.stage = EnumerationStages.ENUMERATE;
            handleNetworkEnumeration(context);
            break;
        case ENUMERATE:
            String enumKey = getEnumKey(context);
            switch (context.request.enumerationAction) {
            case START:
                if (!this.ongoingEnumerations.add(enumKey)) {
                    logInfo("Enumeration service has already been started for %s", enumKey);
                    handleSubStage(context, NetworkEnumStages.FINISHED);
                    return;
                }
                logInfo("Launching enumeration service for %s", enumKey);
                context.request.enumerationAction = EnumerationAction.REFRESH;
                handleNetworkEnumeration(context);
                break;
            case REFRESH:
                context.enumerationStartTimeInMicros = Utils.getNowMicrosUtc();
                context.subStage = NetworkEnumStages.GET_VNETS;
                handleSubStage(context);
                break;
            case STOP:
                if (this.ongoingEnumerations.remove(enumKey)) {
                    logInfo("Enumeration service will be stopped for %s", enumKey);
                } else {
                    logInfo("Enumeration service is not running or has already been stopped for %s",
                            enumKey);
                }
                context.stage = EnumerationStages.FINISHED;
                handleNetworkEnumeration(context);
                break;
            default:
                logSevere("Unknown enumeration action %s", context.request.enumerationAction);
                context.stage = EnumerationStages.ERROR;
                handleNetworkEnumeration(context);
                break;
            }
            break;
        case FINISHED:
            context.operation.complete();
            logInfo("Enumeration finished for %s", getEnumKey(context));
            this.ongoingEnumerations.remove(getEnumKey(context));
            break;
        case ERROR:
            context.operation.fail(context.error);
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

    private void handleSubStage(NetworkEnumContext context) {
        if (!this.ongoingEnumerations.contains(getEnumKey(context))) {
            context.stage = EnumerationStages.FINISHED;
            handleNetworkEnumeration(context);
            return;
        }

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
            querySubnetStates(context, NetworkEnumStages.CREATE_UPDATE_NETWORK_STATES);
            break;
        case CREATE_UPDATE_NETWORK_STATES:
            createUpdateNetworkStates(context, NetworkEnumStages.CREATE_UPDATE_SUBNET_STATES);
            break;
        case CREATE_UPDATE_SUBNET_STATES:
            createUpdateSubnetStates(context, NetworkEnumStages.DELETE_NETWORK_STATES);
            break;
        case DELETE_NETWORK_STATES:
            deleteNetworkStates(context, NetworkEnumStages.DELETE_SUBNET_STATES);
            break;
        case DELETE_SUBNET_STATES:
            deleteSubnetStates(context, NetworkEnumStages.FINISHED);
            break;
        case FINISHED:
            context.stage = EnumerationStages.FINISHED;
            handleNetworkEnumeration(context);
            break;
        default:
            break;
        }
    }

    /**
     * Shortcut method that sets the next stage into the context and delegates to
     * {@link #handleSubStage(NetworkEnumContext)}.
     */
    private void handleSubStage(NetworkEnumContext ctx, NetworkEnumStages nextStage) {
        logFine("Transition to " + nextStage);
        ctx.subStage = nextStage;
        handleSubStage(ctx);
    }

    private void handleError(NetworkEnumContext ctx, Throwable e) {
        logSevere("Failed at stage %s with exception: %s", ctx.stage, Utils.toString(e));
        ctx.error = e;
        ctx.stage = EnumerationStages.ERROR;
        handleNetworkEnumeration(ctx);
    }

    /**
     * Return a key to uniquely identify enumeration for compute host instance.
     */
    private String getEnumKey(NetworkEnumContext ctx) {
        return "hostLink:" + ctx.request.resourceLink() +
                "-enumerationAdapterReference:" +
                ctx.parentCompute.description.enumerationAdapterReference;
    }

    /**
     * Retrieve a page of Virtual Network resources from azure, based on the provided compute host
     * description.
     */
    private void getVirtualNetworks(NetworkEnumContext context, NetworkEnumStages next) {
        logInfo("Enumerating Virtual Networks from Azure.");

        URI uri;
        if (context.enumNextPageLink == null) {
            // First request to fetch Virtual Networks from Azure.
            String uriStr = AdapterUriUtil.expandUriPathTemplate(LIST_VIRTUAL_NETWORKS_URI,
                    context.parentAuth.userLink);
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
                    AUTH_HEADER_BEARER_PREFIX + context.credentials.getToken());
        } catch (Exception ex) {
            this.handleError(context, ex);
            return;
        }

        operation.setCompletion((op, ex) -> {
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

            logFine("Retrieved %d Virtual Networks from Azure", virtualNetworks.size());
            logFine("Next page link %s", context.enumNextPageLink);

            // Store virtual networks for further processing during the next stages.
            virtualNetworks.forEach(virtualNetwork -> {
                context.addVirtualNetwork(virtualNetwork);
            });

            logFine("Processing %d virtual networks", context.virtualNetworks.size());

            handleSubStage(context, next);
        });

        sendRequest(operation);
    }

    /**
     * Based on the retrieved page of Virtual Network resources from azure, compose the
     * structures holding Azure Subnets data.
     */
    private void getSubnets(NetworkEnumContext context, NetworkEnumStages next) {
        logInfo("Enumerating Subnets from Azure.");

        context.virtualNetworks.values().forEach(virtualNetwork -> {
            if (virtualNetwork.properties.subnets != null) {
                virtualNetwork.properties.subnets.forEach(subnet -> {

                    SubnetState subnetState = buildSubnetState(subnet,
                            context.parentCompute.tenantLinks, context.request.endpointLink);
                    SubnetStateWithParentVNetId subnetStateWithParentVNetId = new
                            SubnetStateWithParentVNetId(virtualNetwork.id, subnetState);

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
            String endpointLink) {
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

        String rgTypeProperty = QuerySpecification
                .buildCompositeFieldName(
                        ResourceGroupState.FIELD_NAME_CUSTOM_PROPERTIES,
                        ComputeProperties.RESOURCE_TYPE_KEY);
        String computeHostProperty = QuerySpecification.buildCompositeFieldName(
                ResourceGroupState.FIELD_NAME_CUSTOM_PROPERTIES,
                ComputeProperties.FIELD_COMPUTE_HOST_LINK);

        Query query = Builder.create()
                .addKindFieldClause(ResourceGroupState.class)
                .addFieldClause(computeHostProperty,
                        context.parentCompute.documentSelfLink)
                .addFieldClause(rgTypeProperty,
                        ResourceGroupStateType.AzureResourceGroup.name())
                .addInClause(ResourceGroupState.FIELD_NAME_ID, resourceGroupIds)
                .build();

        QueryTask qt = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .addOption(QueryOption.TOP_RESULTS)
                .setResultLimit(getQueryResultLimit())
                .setQuery(query)
                .build();
        qt.tenantLinks = context.parentCompute.tenantLinks;

        QueryUtils.startQueryTask(this, qt)
                .whenComplete((queryTask, e) -> {
                    if (e != null) {
                        handleError(context, e);
                        return;
                    }
                    if (queryTask.results != null && queryTask.results.documentCount > 0) {
                        queryTask.results.documents.values().forEach(document -> {
                            ResourceGroupState rgState = Utils.fromJson(document, ResourceGroupState
                                    .class);
                            context.resourceGroupStates.put(rgState.id, rgState.documentSelfLink);
                        });
                    }

                    handleSubStage(context, next);
                });
    }

    /**
     * Query network states stored in the local document store based on the retrieved azure
     * virtual networks.
     */
    private void queryNetworkStates(NetworkEnumContext context, NetworkEnumStages next) {
        logInfo("Query Network States from local document store.");

        Query query = Query.Builder.create()
                .addKindFieldClause(NetworkState.class)
                .addInClause(NetworkState.FIELD_NAME_ID, context.virtualNetworks.keySet())
                .build();

        QueryTask q = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .addOption(QueryOption.TOP_RESULTS)
                .setResultLimit(getQueryResultLimit())
                .setQuery(query)
                .build();
        q.tenantLinks = context.parentCompute.tenantLinks;

        QueryUtils.startQueryTask(this, q)
                .whenComplete((queryTask, e) -> {
                    if (e != null) {
                        handleError(context, e);
                        return;
                    }

                    logFine("Found %d matching network states for Azure virtual networks.",
                            queryTask.results.documentCount);

                    // If there are no matches, there is nothing to update.
                    if (queryTask.results != null && queryTask.results.documentCount > 0) {
                        queryTask.results.documents.values().forEach(network -> {
                            NetworkState networkState = Utils.fromJson(network, NetworkState.class);
                            context.networkStates.put(networkState.id, networkState);
                        });
                    }

                    handleSubStage(context, next);
                });
    }

    /**
     * Query subnet states stored in the local document store based on the retrieved azure
     * subnets.
     */
    private void querySubnetStates(NetworkEnumContext context, NetworkEnumStages next) {
        logInfo("Query Subnet States from local document store.");

        Query query = Query.Builder.create()
                .addKindFieldClause(SubnetState.class)
                .addInClause(NetworkState.FIELD_NAME_ID, context.subnets.keySet())
                .build();

        QueryTask q = QueryTask.Builder.createDirectTask()
                .addOption(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT)
                .addOption(QueryOption.TOP_RESULTS)
                .setResultLimit(getQueryResultLimit())
                .setQuery(query).build();
        q.tenantLinks = context.parentCompute.tenantLinks;

        QueryUtils.startQueryTask(this, q)
                .whenComplete((queryTask, e) -> {
                    if (e != null) {
                        handleError(context, e);
                        return;
                    }

                    logFine("Found %d matching subnet states for Azure subnets.",
                            queryTask.results.documentCount);

                    // If there are no matches, there is nothing to update.
                    if (queryTask.results != null || queryTask.results.documentCount > 0) {
                        queryTask.results.documents.values().forEach(result -> {

                            SubnetState subnetState = Utils.fromJson(result, SubnetState.class);
                            context.subnetStates.put(subnetState.id, subnetState.documentSelfLink);
                        });
                    }

                    handleSubStage(context, next);

                });
    }

    /**
     * Create new network states or update matching network states with the actual state in
     * Azure.
     */
    private void createUpdateNetworkStates(NetworkEnumContext context, NetworkEnumStages next) {
        logInfo("Create or update Network States with the actual state in Azure.");

        if (context.virtualNetworks.size() == 0) {
            logInfo("No virtual networks available for create/update.");
            handleSubStage(context, next);
            return;
        }

        Stream<Operation> operations = context.virtualNetworks.values().stream().map
                (virtualNetwork -> {
                    NetworkState existingNetworkState = context.networkStates
                            .get(virtualNetwork.id);

                    NetworkState networkState = buildNetworkState(context, virtualNetwork,
                            existingNetworkState);

                    CompletionHandler handler = (completedOp, failure) -> {
                        if (failure != null) {
                            // Process successful operations only.
                            logWarning("Error: %s", failure.getMessage());
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
                        context.modifiedNetworksLinks.add(result.documentSelfLink);
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

            logInfo("Finished updating network states");

            handleSubStage(context, next);
        }).sendWith(this);
    }

    /**
     * Create new subnet states or updates matching subnet states with the actual state in
     * Azure.
     */
    private void createUpdateSubnetStates(NetworkEnumContext context, NetworkEnumStages next) {
        if (context.subnets.size() == 0) {
            logInfo("No network states available for update.");
            handleSubStage(context, next);
            return;
        }

        Stream<Operation> operations = context.subnets.keySet().stream().map
                (subnetId -> {
                    SubnetStateWithParentVNetId subnetStateWithParentVNetId = context.subnets.get
                            (subnetId);

                    SubnetState subnetState = subnetStateWithParentVNetId.subnetState;

                    // Update networkLink with "latest" (either created or updated)
                    // NetworkState.documentSelfLink
                    NetworkState networkState = context.networkStates.get
                            (subnetStateWithParentVNetId.parentVNetId);
                    if (networkState != null) {
                        subnetState.networkLink = networkState.documentSelfLink;
                    } else {
                        logWarning("Network state corresponding to subnet with name [" +
                                subnetState.name + "] was not found. Network Link is left empty.");
                    }
                    subnetState.endpointLink = context.request.endpointLink;

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
                failures.values().forEach(ex -> logWarning("Error: %s", ex.getMessage()));
            }

            // Process successful operations.
            ops.values().stream()
                    .filter(operation -> failures != null && !failures
                            .containsKey(operation.getId()))
                    .filter(operation -> operation.getStatusCode()
                            != Operation.STATUS_CODE_NOT_MODIFIED)
                    .forEach(operation -> {
                        SubnetState subnetState = operation.getBody(SubnetState.class);
                        context.subnets.get(subnetState.id).subnetState = subnetState;
                    });

            if (context.enumNextPageLink != null) {
                logInfo("Fetch the next page Virtual Networks from Azure.");
                handleSubStage(context, NetworkEnumStages.GET_VNETS);
                return;
            }

            logInfo("Finished updating network states");

            handleSubStage(context, next);
        })
                .sendWith(this);
    }

    /**
     * @param localNetworkState null to do a create, non null to update an existing network state
     */
    private NetworkState buildNetworkState(NetworkEnumContext context,
            VirtualNetwork azureVirtualNetwork, NetworkState localNetworkState) {

        NetworkState resultNetworkState = new NetworkState();
        if (localNetworkState != null) {
            resultNetworkState.id = localNetworkState.id;
            resultNetworkState.authCredentialsLink = localNetworkState.authCredentialsLink;
            resultNetworkState.documentSelfLink = localNetworkState.documentSelfLink;
            resultNetworkState.groupLinks = localNetworkState.groupLinks;
        } else {
            resultNetworkState.id = azureVirtualNetwork.id;
            resultNetworkState.authCredentialsLink = context.parentAuth.documentSelfLink;
        }

        resultNetworkState.name = azureVirtualNetwork.name;
        resultNetworkState.regionId = azureVirtualNetwork.location;
        resultNetworkState.resourcePoolLink = context.request.resourcePoolLink;
        resultNetworkState.endpointLink = context.request.endpointLink;

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
            resultNetworkState.customProperties = Collections.singletonMap(ComputeProperties
                            .FIELD_VIRTUAL_GATEWAY,
                    gatewayId);
            logInfo("Added Gateway %s for Network State %s.", gatewayId, resultNetworkState.name);
        }

        // TODO: There is no Azure Network Adapter Service. Add a default reference since this is
        // required field.
        resultNetworkState.instanceAdapterReference = UriUtils.buildUri(getHost(),
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
    private void deleteNetworkStates(NetworkEnumContext context, NetworkEnumStages next) {
        logInfo("Delete Network States that no longer exists in Azure.");

        Query query = Query.Builder.create()
                .addKindFieldClause(NetworkState.class)
                .addFieldClause(NetworkState.FIELD_NAME_AUTH_CREDENTIALS_LINK,
                        context.parentCompute.description.authCredentialsLink)
                .addInClause(ResourceGroupState.FIELD_NAME_ID, context.virtualNetworkIds, Occurance
                        .MUST_NOT_OCCUR)
                .addRangeClause(ResourceGroupState.FIELD_NAME_UPDATE_TIME_MICROS,
                        QueryTask.NumericRange
                                .createLessThanRange(context.enumerationStartTimeInMicros))
                .build();

        QueryTask q = QueryTask.Builder.createDirectTask()
                .setQuery(query)
                .setResultLimit(getQueryResultLimit())
                .build();
        q.tenantLinks = context.parentCompute.tenantLinks;

        logFine("Querying Network States for deletion.");

        // Add deleted NetworkStates to the modified networks list.
        sendDeleteQueryTask(q, context, next, context.modifiedNetworksLinks::add);
    }

    /**
     * Delete subnet states that no longer exist in Azure.
     * <p>
     * The logic works by recording a timestamp when enumeration starts. This timestamp is used to
     * lookup resources which haven't been touched as part of current enumeration cycle and
     * belong to networks touched by this enumeration cycle (either created/updated/deleted).
     */
    private void deleteSubnetStates(NetworkEnumContext context, NetworkEnumStages next) {
        Query query = Query.Builder.create()
                .addKindFieldClause(SubnetState.class)
                .addInClause(SubnetState.FIELD_NAME_NETWORK_LINK, context.modifiedNetworksLinks)
                .addInClause(ResourceGroupState.FIELD_NAME_ID, context.virtualNetworkIds, Occurance
                        .MUST_NOT_OCCUR)
                .addRangeClause(ResourceGroupState.FIELD_NAME_UPDATE_TIME_MICROS,
                        QueryTask.NumericRange
                                .createLessThanRange(context.enumerationStartTimeInMicros))
                .build();
        QueryTask q = QueryTask.Builder.createDirectTask()
                .setQuery(query)
                .setResultLimit(getQueryResultLimit())
                .build();
        q.tenantLinks = context.parentCompute.tenantLinks;

        logFine("Querying Subnet States for deletion.");
        sendDeleteQueryTask(q, context, next, null);
    }

    private void sendDeleteQueryTask(QueryTask q, NetworkEnumContext context,
            NetworkEnumStages next, Consumer<String> preDeleteProcessor) {

        QueryUtils.startQueryTask(this, q)
                .whenComplete((queryTask, e) -> {
                    if (e != null) {
                        handleError(context, e);
                        return;
                    }

                    context.deletionNextPageLink = queryTask.results.nextPageLink;

                    handleDeleteQueryTaskResult(context, next, preDeleteProcessor);
                });
    }

    /**
     * Get next page of query results and delete then.
     *
     * @param preDeleteProcessor an optional {@link Consumer} that will be called before deleting
     *                           the matching resources.
     */
    private void handleDeleteQueryTaskResult(NetworkEnumContext context,
            NetworkEnumStages next, Consumer<String> preDeleteProcessor) {

        if (context.deletionNextPageLink == null) {
            logInfo("Finished deletion stage .");
            handleSubStage(context, next);
            return;
        }

        logFine("Querying page [%s] for resources to be deleted", context.deletionNextPageLink);
        sendRequest(Operation.createGet(this, context.deletionNextPageLink)
                .setCompletion((completedOp, ex) -> {
                    if (ex != null) {
                        handleError(context, ex);
                        return;
                    }

                    QueryTask queryTask = completedOp.getBody(QueryTask.class);

                    if (queryTask.results.documentCount > 0) {
                        // Delete all matching states.
                        Stream<Operation> operations = queryTask.results.documentLinks.stream()
                                .map(link -> {
                                    if (preDeleteProcessor != null) {
                                        preDeleteProcessor.accept(link);
                                    }
                                    return link;
                                })
                                .map(link -> Operation.createDelete(this, link));

                        OperationJoin.create(operations).setCompletion((ops, failures) -> {
                            if (failures != null) {
                                // We don't want to fail the whole data collection if some of the
                                // operation fails.
                                failures.values()
                                        .forEach(e -> logWarning("Error: %s", e.getMessage()));
                            }
                        }).sendWith(this);
                    }

                    // Store the next page in the context
                    context.deletionNextPageLink = queryTask.results.nextPageLink;

                    // Handle next page of results.
                    handleDeleteQueryTaskResult(context, next, preDeleteProcessor);
                }));
    }
}