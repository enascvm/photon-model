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
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.LIST_VIRTUAL_NETWORKS_URI;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.NETWORK_REST_API_VERSION;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.QUERY_PARAM_API_VERSION;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.getAzureConfig;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.microsoft.azure.credentials.ApplicationTokenCredentials;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.model.network.AddressSpace;
import com.vmware.photon.controller.model.adapters.azure.model.network.VirtualNetwork;
import com.vmware.photon.controller.model.adapters.azure.model.network.VirtualNetworkListResult;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.enums.EnumerationStages;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Enumeration adapter for data collection of Network related resources on Azure.
 * <p>
 * The state machine that is implemented is the following:
 * <ul>
 * <li>Get a page of virtual networks from azure</li>
 * <li>Get local network states matching the azure virtual network ids.</li>
 * <li>Update the matching network states</li>
 * <li>Create network states for azure virtual networks that does not exist in our system.</li>
 * <li>Delete network states that are not touched during the previous states because they
 * are stale entries, no longer existing in Azure.</li>
 * </ul>
 */
public class AzureNetworkEnumerationAdapterService extends StatelessService {
    public static final String SELF_LINK = AzureUriPaths.AZURE_NETWORK_ENUMERATION_ADAPTER;
    private static final String PROPERTY_NAME_ENUM_QUERY_RESULT_LIMIT =
            UriPaths.PROPERTY_PREFIX + "AzureNetworkEnumerationAdapterService.QUERY_RESULT_LIMIT";
    private static final int DEFAULT_QUERY_RESULT_LIMIT = 50;

    /**
     * The local service context that is used to identify and create/update a representative
     * set of network states based on the enumeration data received from Azure.
     */
    public static class NetworkEnumContext {
        ComputeEnumerateResourceRequest enumRequest;
        ComputeDescriptionService.ComputeDescription computeHostDesc;

        // The time when enumeration starts. This field is used also to identify stale resources
        // that should be deleted during deletion stage.
        long enumerationStartTimeInMicros;

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

        // Stored operation to signal completion to the Azure network enumeration once all the
        // stages are successfully completed.
        Operation azureNetworkAdapterOperation;

        // Stores the next page when retrieving Virtual Networks from Azure.
        String enumNextPageLink;

        String deletionNextPageLink;

        // Azure credentials.
        ApplicationTokenCredentials credentials;

        NetworkEnumContext(ComputeEnumerateResourceRequest request, Operation op) {
            this.enumRequest = request;
            this.stage = EnumerationStages.HOSTDESC;
            this.azureNetworkAdapterOperation = op;
        }
    }

    /**
     * Sub stages describing the state machine of Azure Network enumeration flow.
     */
    private enum NetworkEnumStages {
        GET_VNETS,
        QUERY_NETWORK_STATES,
        UPDATE_NETWORK_STATES,
        CREATE_NETWORK_STATES,
        DELETE_NETWORK_STATES,
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
        NetworkEnumContext ctx = new NetworkEnumContext(op.getBody(ComputeEnumerateResourceRequest
                .class), op);
        AdapterUtils.validateEnumRequest(ctx.enumRequest);
        if (ctx.enumRequest.isMockRequest) {
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
        case HOSTDESC:
            getHostComputeDescription(context);
            break;
        case PARENTAUTH:
            getParentAuth(context);
            break;
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
            switch (context.enumRequest.enumerationAction) {
            case START:
                if (!this.ongoingEnumerations.add(enumKey)) {
                    logInfo("Enumeration service has already been started for %s", enumKey);
                    handleSubStage(context, NetworkEnumStages.FINISHED);
                    return;
                }
                logInfo("Launching enumeration service for %s", enumKey);
                context.enumerationStartTimeInMicros = Utils.getNowMicrosUtc();
                context.enumRequest.enumerationAction = EnumerationAction.REFRESH;
                handleNetworkEnumeration(context);
                break;
            case REFRESH:
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
                logSevere("Unknown enumeration action %s", context.enumRequest.enumerationAction);
                context.stage = EnumerationStages.ERROR;
                handleNetworkEnumeration(context);
                break;
            }
            break;
        case FINISHED:
            context.azureNetworkAdapterOperation.complete();
            logInfo("Enumeration finished for %s", getEnumKey(context));
            this.ongoingEnumerations.remove(getEnumKey(context));
            break;
        case ERROR:
            context.azureNetworkAdapterOperation.fail(context.error);
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
            getVirtualNetworks(context, NetworkEnumStages.QUERY_NETWORK_STATES);
            break;
        case QUERY_NETWORK_STATES:
            queryNetworkStates(context, NetworkEnumStages.UPDATE_NETWORK_STATES);
            break;
        case UPDATE_NETWORK_STATES:
            updateNetworkStates(context, NetworkEnumStages.CREATE_NETWORK_STATES);
            break;
        case CREATE_NETWORK_STATES:
            createNetworkStates(context, NetworkEnumStages.DELETE_NETWORK_STATES);
            break;
        case DELETE_NETWORK_STATES:
            deleteNetworkStates(context, NetworkEnumStages.FINISHED);
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

    /**
     * Method to retrieve the parent compute host on which the enumeration task will be performed.
     */
    private void getHostComputeDescription(NetworkEnumContext ctx) {
        Consumer<Operation> onSuccess = (op) -> {
            ComputeService.ComputeStateWithDescription csd = op
                    .getBody(ComputeService.ComputeStateWithDescription.class);
            ctx.computeHostDesc = csd.description;
            ctx.stage = EnumerationStages.PARENTAUTH;
            handleNetworkEnumeration(ctx);
        };

        URI computeUri = UriUtils.extendUriWithQuery(
                UriUtils.buildUri(this.getHost(), ctx.enumRequest.resourceLink()),
                UriUtils.URI_PARAM_ODATA_EXPAND,
                Boolean.TRUE.toString());
        AdapterUtils.getServiceState(this, computeUri, onSuccess, getFailureConsumer(ctx));
    }

    /**
     * Method to arrive at the credentials needed to call the Azure API for enumerating the instances.
     */
    private void getParentAuth(NetworkEnumContext ctx) {
        URI authUri = UriUtils.buildUri(this.getHost(),
                ctx.computeHostDesc.authCredentialsLink);
        Consumer<Operation> onSuccess = (op) -> {
            ctx.parentAuth = op.getBody(AuthCredentialsService.AuthCredentialsServiceState.class);
            ctx.stage = EnumerationStages.CLIENT;
            handleNetworkEnumeration(ctx);
        };
        AdapterUtils.getServiceState(this, authUri, onSuccess, getFailureConsumer(ctx));
    }

    private Consumer<Throwable> getFailureConsumer(NetworkEnumContext ctx) {
        return (t) -> handleError(ctx, t);
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
        return "hostLink:" + ctx.enumRequest.resourceLink() +
                "-enumerationAdapterReference:" +
                ctx.computeHostDesc.enumerationAdapterReference;
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
            virtualNetworks.forEach(virtualNetwork ->
                    context.virtualNetworks.put(virtualNetwork.id, virtualNetwork));

            logFine("Processing %d virtual networks", context.virtualNetworks.size());

            handleSubStage(context, next);
        });

        sendRequest(operation);
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
                .addOption(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT)
                .setQuery(query).build();
        q.tenantLinks = context.computeHostDesc.tenantLinks;

        sendRequest(Operation
                .createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(q)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        handleError(context, e);
                        return;
                    }
                    QueryTask queryTask = o.getBody(QueryTask.class);

                    logFine("Found %d matching network statse for Azure virtual networks.",
                            queryTask.results.documentCount);

                    // If there are no matches, there is nothing to update.
                    if (queryTask.results == null || queryTask.results.documentCount == 0) {
                        handleSubStage(context, NetworkEnumStages.CREATE_NETWORK_STATES);
                        return;
                    }

                    queryTask.results.documents.values().forEach(network -> {
                        NetworkState networkState = Utils.fromJson(network, NetworkState.class);
                        context.networkStates.put(networkState.id, networkState);
                    });

                    handleSubStage(context, next);
                }));
    }

    /**
     * Updates matching network states with the actual state in Azure.
     */
    private void updateNetworkStates(NetworkEnumContext context, NetworkEnumStages next) {
        logInfo("Update Network States with the actual state in Azure.");

        if (context.networkStates.size() == 0) {
            logInfo("No network states available for update.");
            handleSubStage(context, next);
            return;
        }

        Stream<Operation> updateNetworkStateOps = context.networkStates.values().stream()
                .map(networkState -> {
                    VirtualNetwork virtualNetwork = context.virtualNetworks.get(networkState.id);
                    NetworkState networkStateToUpdate = newOrUpdateNetworkState(context,
                            virtualNetwork,
                            networkState);

                    // Remove updated virtual network from the list.
                    context.virtualNetworks.remove(networkState.id);

                    return Operation.createPatch(this, networkStateToUpdate.documentSelfLink)
                            .setBody(networkStateToUpdate);
                });

        OperationJoin.create(updateNetworkStateOps).setCompletion((ops, failures) -> {
            if (failures != null) {
                // We don't want to fail the whole data collection if some of the
                // operation fails.
                failures.values().forEach(ex -> logWarning("Error: %s", ex.getMessage()));
            }

            logInfo("Finished updating network states");

            handleSubStage(context, next);
        })
                .sendWith(this);
    }

    /**
     * Create Network States based on their actual state in Azure.
     */
    private void createNetworkStates(NetworkEnumContext context, NetworkEnumStages next) {
        logInfo("Create new Network States based on their actual state in Azure.");

        if (context.virtualNetworks.size() == 0 && context.enumNextPageLink == null) {
            logInfo("No Virtual Networks found for creation.");
            handleSubStage(context, next);
            return;
        }

        logFine("%d network states to be created", context.networkStates.size());

        Stream<Operation> createNetworkStateOps = context.virtualNetworks.values().stream().map
                (azureVirtualNetwork -> {

                    NetworkState newNetworkState = newOrUpdateNetworkState(context,
                            azureVirtualNetwork, null);

                    return Operation.createPost(getHost(), NetworkService.FACTORY_LINK)
                            .setBody(newNetworkState);
                });

        OperationJoin.create(createNetworkStateOps).setCompletion((ops, failures) -> {
            if (failures != null) {
                // We don't want to fail the whole data collection if some of the
                // operation fails.
                failures.values().forEach(ex -> logWarning("Error: %s", ex.getMessage()));
            }

            if (context.enumNextPageLink != null) {
                logInfo("Fetch the next page Virtual Network from Azure.");
                handleSubStage(context, NetworkEnumStages.GET_VNETS);
                return;
            }

            logInfo("Finished creating network states");

            handleSubStage(context, next);
        }).sendWith(this);
    }

    /**
     * @param localNetworkState null to do a create, non null to update an existing network state
     */
    private NetworkState newOrUpdateNetworkState(NetworkEnumContext context,
            VirtualNetwork azureVirtualNetwork, NetworkState localNetworkState) {

        NetworkState resultNetworkState = new NetworkState();
        if (localNetworkState != null) {
            resultNetworkState.id = localNetworkState.id;
            resultNetworkState.authCredentialsLink = localNetworkState.authCredentialsLink;
            resultNetworkState.documentSelfLink = localNetworkState.documentSelfLink;
        } else {
            resultNetworkState.id = azureVirtualNetwork.id;
            resultNetworkState.authCredentialsLink = context.parentAuth.documentSelfLink;
        }

        resultNetworkState.name = azureVirtualNetwork.name;
        resultNetworkState.regionId = azureVirtualNetwork.location;
        resultNetworkState.resourcePoolLink = context.enumRequest.resourcePoolLink;

        AddressSpace addressSpace = azureVirtualNetwork.properties.addressSpace;
        if (addressSpace != null
                && addressSpace.addressPrefixes != null
                && addressSpace.addressPrefixes.size() > 0) {

            // TODO: Get the first address prefix for now.
            resultNetworkState.subnetCIDR = addressSpace.addressPrefixes.get(0);
        }

        // TODO: There is no Azure Network Adapter Service. Add a dummy reference since this is
        // required field.
        resultNetworkState.instanceAdapterReference = UriUtils.buildUri(getHost(),
                "/dummyInstanceAdapterReference");
        resultNetworkState.tenantLinks = context.computeHostDesc.tenantLinks;

        return resultNetworkState;
    }

    /*
    * Delete local network states that no longer exist in Azure.
    *
    * The logic works by recording a timestamp when enumeration starts. This timestamp is used to
    * lookup resources which haven't been touched as part of current enumeration cycle.
    *
    */
    private void deleteNetworkStates(NetworkEnumContext context, NetworkEnumStages next) {
        logInfo("Delete Network States that no longer exists in Azure.");


        Query query = Query.Builder.create()
                .addKindFieldClause(NetworkState.class)
                .addFieldClause(NetworkState.FIELD_NAME_AUTH_CREDENTIALS_LINK,
                        context.computeHostDesc.authCredentialsLink)
                .addRangeClause(NetworkState.FIELD_NAME_UPDATE_TIME_MICROS, QueryTask.NumericRange
                        .createLessThanRange(context.enumerationStartTimeInMicros))
                .build();

        int resultLimit = Integer.getInteger(PROPERTY_NAME_ENUM_QUERY_RESULT_LIMIT,
                DEFAULT_QUERY_RESULT_LIMIT);

        QueryTask q = QueryTask.Builder.createDirectTask()
                .setQuery(query)
                .setResultLimit(resultLimit)
                .build();
        q.tenantLinks = context.computeHostDesc.tenantLinks;

        logFine("Querying Network States for deletion");
        sendRequest(Operation
                .createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(q)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        handleError(context, e);
                        return;
                    }

                    QueryTask queryTask = o.getBody(QueryTask.class);
                    context.deletionNextPageLink = queryTask.results.nextPageLink;

                    handleQueryTaskResult(context, next);

                }));
    }

    private void handleQueryTaskResult(NetworkEnumContext context, NetworkEnumStages next) {

        if (context.deletionNextPageLink == null) {
            logInfo("Finished deletion of network states for Azure.");
            handleSubStage(context, next);
            return;
        }

        logFine("Querying page [%s] for resources to be deleted", context.deletionNextPageLink);
        sendRequest(Operation.createGet(this, context.deletionNextPageLink)
                .setCompletion((completedOp, failure) -> {
                    if (failure != null) {
                        logWarning("Error querying for network states: %s.", failure.getMessage());
                        handleSubStage(context, next);
                        return;
                    }

                    QueryTask queryTask = completedOp.getBody(QueryTask.class);

                    // Delete all matching network states.
                    Stream<Operation> operations = queryTask.results.documentLinks.stream()
                            .map(link -> Operation.createDelete(this, link));

                    OperationJoin.create(operations).setCompletion((ops, failures) -> {
                        if (failures != null) {
                            // We don't want to fail the whole data collection if some of the
                            // operation fails.
                            failures.values().forEach(ex -> logWarning("Error: %s", ex.getMessage()));
                        }
                    }).sendWith(this);

                    // Store the next page in the context
                    context.deletionNextPageLink = queryTask.results.nextPageLink;

                    // Handle next page of results.
                    handleQueryTaskResult(context, next);
                }));
    }
}
