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

package com.vmware.photon.controller.model.adapters.azure.ea.enumeration;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest;
import com.vmware.photon.controller.model.adapterapi.ResourceRequest;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.model.cost.AzureSubscription;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext.BaseAdapterStage;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.query.QueryUtils.QueryByPages;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService;
import com.vmware.photon.controller.model.util.ClusterUtil;
import com.vmware.photon.controller.model.util.ClusterUtil.ServiceTypeCluster;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;

/**
 * Enumerator for creating ComputeStates for the entities in Azure bill against which Azure
 * Cost stats will be created. At this point of time this service will manage the ComputeStates for
 * Azure Accounts under a Azure EA account.
 *
 * This service as of now expects the invoker to send the details about the entities in Azure bill
 * for which the enumeration has to happen.
 */

public class AzureSubscriptionsEnumerationService extends StatelessService {

    public static final String SELF_LINK = AzureUriPaths.AZURE_SUBSCRIPTIONS_ENUMERATOR;

    private static final int OPERATION_BATCH_SIZE = 50;

    /**
     * The request class for creating computes for Azure costs, expects the details about the
     * entities to be sent in the request.
     */
    public static class AzureSubscriptionsEnumerationRequest extends ResourceRequest {
        public Collection<AzureSubscription> azureSubscriptions;
    }

    public AzureSubscriptionsEnumerationService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    protected enum AzureCostComputeEnumerationStages {
        UPDATE_EXISTING_RESOURCES, FETCH_EXISTING_RESOURCES, CREATE_RESOURCES, COMPLETED
    }

    protected static class AzureSubscriptionsEnumerationContext
            extends BaseAdapterContext<AzureSubscriptionsEnumerationContext> {
        protected AzureCostComputeEnumerationStages stage;
        protected Map<String, AzureSubscription> idToSubscription;

        public AzureSubscriptionsEnumerationContext(
                StatelessService service,
                AzureSubscriptionsEnumerationRequest resourceRequest, Operation parentOp) {
            super(service, resourceRequest);
            // If azureSubscriptions is null or empty don't do anything
            if (resourceRequest.azureSubscriptions == null
                    || resourceRequest.azureSubscriptions.isEmpty()) {
                this.stage = AzureCostComputeEnumerationStages.COMPLETED;
            } else {
                this.idToSubscription = resourceRequest.azureSubscriptions
                        .stream().collect(Collectors.toMap(subscription -> subscription.entityId,
                                subscription -> subscription));
                this.stage = AzureCostComputeEnumerationStages.UPDATE_EXISTING_RESOURCES;
            }
            this.operation = parentOp;
        }

        @Override
        protected URI getParentAuthRef(AzureSubscriptionsEnumerationContext context) {
            return UriUtils.extendUri(ClusterUtil.getClusterUri(context.service.getHost(),
                    ServiceTypeCluster.INVENTORY_SERVICE),
                    context.parent.description.authCredentialsLink);
        }
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }

        AzureSubscriptionsEnumerationRequest request =
                op.getBody(AzureSubscriptionsEnumerationRequest.class);
        if (request.isMockRequest) {
            op.complete();
            return;
        }
        AzureSubscriptionsEnumerationContext azureSubscriptionsEnumerationContext =
                new AzureSubscriptionsEnumerationContext(this, request, op);

        azureSubscriptionsEnumerationContext.populateBaseContext(BaseAdapterStage.PARENTDESC)
                .whenComplete((c, e) -> {
                    if (e != null) {
                        getFailureConsumer(azureSubscriptionsEnumerationContext).accept(e);
                        return;
                    }
                    handleAzureSubscriptionsEnumerationRequest(azureSubscriptionsEnumerationContext);
                });
    }

    private void handleAzureSubscriptionsEnumerationRequest(
            AzureSubscriptionsEnumerationContext enumerationContext) {
        try {
            switch (enumerationContext.stage) {
            case UPDATE_EXISTING_RESOURCES:
                updateExistingResources(enumerationContext,
                            AzureCostComputeEnumerationStages.FETCH_EXISTING_RESOURCES);
                break;
            case FETCH_EXISTING_RESOURCES:
                fetchExistingResources(enumerationContext,
                            AzureCostComputeEnumerationStages.CREATE_RESOURCES);
                break;
            case CREATE_RESOURCES:
                createResources(enumerationContext,
                        AzureCostComputeEnumerationStages.COMPLETED);
                break;
            case COMPLETED:
                enumerationContext.operation.complete();
                break;
            default:
                logSevere(
                        () -> String.format("Unknown Azure Cost Compute enumeration stage %s ",
                               enumerationContext.stage.toString()));
                break;
            }
        } catch (Exception e) {
            getFailureConsumer(enumerationContext).accept(e);
        }
    }

    private void fetchExistingResources(AzureSubscriptionsEnumerationContext enumerationContext,
                                        AzureCostComputeEnumerationStages nextStage) {
        Query azureComputesQuery =
                createQueryForAzureSubscriptionComputes(enumerationContext);

        QueryByPages<ComputeState> querySubscriptionsComputes = new QueryByPages<>(
                getHost(), azureComputesQuery, ComputeState.class,
                enumerationContext.parent.tenantLinks);
        querySubscriptionsComputes.setClusterType(ServiceTypeCluster.INVENTORY_SERVICE);
        querySubscriptionsComputes.queryDocuments(computeState -> {
                    if (computeState.customProperties != null
                            && computeState.customProperties
                            .containsKey(AzureConstants.AZURE_SUBSCRIPTION_ID_KEY)) {
                        String subscriptionUuid = computeState.customProperties
                                .get(AzureConstants.AZURE_SUBSCRIPTION_ID_KEY);
                        enumerationContext.idToSubscription.remove(subscriptionUuid);
                    }
                }
                ).whenComplete((aVoid, t) -> {
                    if (t != null) {
                        getFailureConsumer(enumerationContext).accept(t);
                        return;
                    }
                    enumerationContext.stage = nextStage;
                    handleAzureSubscriptionsEnumerationRequest(enumerationContext);
                });
    }

    private void updateExistingResources(AzureSubscriptionsEnumerationContext enumerationContext,
                                         AzureCostComputeEnumerationStages nextStage) {
        // Query the subscriptions which we want to create to check if they already exist
        Query azureSubscriptionEndpointQuery = createQueryForAzureSubscriptionEndpoints(
                enumerationContext);

        QueryByPages<EndpointState> querySubscriptionEndpoints = new QueryByPages<>(getHost(),
                azureSubscriptionEndpointQuery, EndpointState.class,
                enumerationContext.parent.tenantLinks);
        querySubscriptionEndpoints.setClusterType(ServiceTypeCluster.INVENTORY_SERVICE);
        querySubscriptionEndpoints.setMaxPageSize(QueryUtils.DEFAULT_RESULT_LIMIT);
        querySubscriptionEndpoints.collectDocuments(Collectors.toList())
                .whenComplete((subscriptionEndpoints, t) -> {
                    if (t != null) {
                        getFailureConsumer(enumerationContext).accept(t);
                        return;
                    }
                    if (subscriptionEndpoints.size() == 0) {
                        enumerationContext.stage = nextStage;
                        handleAzureSubscriptionsEnumerationRequest(enumerationContext);
                        return;
                    }
                    queryExistingComputeStatesOfEndpoints(enumerationContext, nextStage,
                            subscriptionEndpoints);
                });
    }

    private void queryExistingComputeStatesOfEndpoints(AzureSubscriptionsEnumerationContext
             enumerationContext, AzureCostComputeEnumerationStages nextStage,
             Collection<EndpointState> subscriptionEndpoints) {
        // Get the compute states for already existing subscription endpoints and
        // if needed patch them with custom properties
        Map<String, ComputeState> computeLinkToState = new ConcurrentHashMap<>();
        Collection<Operation> getComputeOps = subscriptionEndpoints
                .stream()
                .map(endpointState -> {
                    Operation op = Operation.createGet(UriUtils.extendUri(
                            getInventoryServiceUri(), endpointState.computeLink))
                            .setCompletion((o, t) -> {
                                if (t != null) {
                                    logSevere(
                                            () -> String.format("Failed getting compute state" +
                                                            "for  %s due to %s",
                                                    endpointState.computeLink, Utils.toString(t)));
                                    return;
                                }
                                ComputeState cs = o.getBody(ComputeState.class);
                                // Only add custom properties if it does not have it already
                                if (!hasAzureEaCustomProperties(cs)) {
                                    ComputeState comWithProps = new ComputeState();
                                    String subscriptionId = endpointState.endpointProperties
                                            .get(EndpointConfigRequest.USER_LINK_KEY);
                                    comWithProps.customProperties =
                                            getPropertiesMap(enumerationContext,
                                                    enumerationContext.idToSubscription
                                                            .get(subscriptionId), false);
                                    computeLinkToState.put(cs.documentSelfLink, comWithProps);
                                }
                            });
                    return op;
                })
                .collect(Collectors.toList());

        joinOperationAndSendRequest(getComputeOps, enumerationContext, (enumCtx) -> {
            patchExitingSubscriptionComputes(enumCtx, nextStage, computeLinkToState);
        });
    }

    private boolean hasAzureEaCustomProperties(ComputeState computeState) {
        if (computeState.customProperties != null
                && computeState.customProperties
                .containsKey(AzureConstants.AZURE_ENROLLMENT_NUMBER_KEY)) {
            return true;
        }
        return false;
    }

    private void patchExitingSubscriptionComputes(AzureSubscriptionsEnumerationContext
                    enumerationContext, AzureCostComputeEnumerationStages nextStage,
                    Map<String, ComputeState> computeLinkToPatchState) {
        Collection<Operation> patchComputesOp = computeLinkToPatchState.keySet()
                .stream()
                .map(computeLink -> {
                    Operation patchOp = Operation.createPatch(UriUtils.extendUri(
                            getInventoryServiceUri(), computeLink))
                            .setBody(computeLinkToPatchState.get(computeLink));
                    return  patchOp;
                })
                .collect(Collectors.toList());

        joinOperationAndSendRequest(patchComputesOp, enumerationContext, (enumCtx) -> {
            enumCtx.stage = nextStage;
            handleAzureSubscriptionsEnumerationRequest(enumCtx);
        });
    }

    private Query createQueryForAzureSubscriptionEndpoints(AzureSubscriptionsEnumerationContext
                                                                   enumerationContext) {
        // Query existing endpoint for Subscription with id subscriptionId
        Query  azureSubscriptionEndpointQuery = Query.Builder.create()
                .addKindFieldClause(EndpointState.class)
                .addFieldClause(EndpointState.FIELD_NAME_ENDPOINT_TYPE,
                        EndpointType.azure.name())
                .addInClause(QueryTask.QuerySpecification.buildCompositeFieldName
                                (new String[]{EndpointState.FIELD_NAME_ENDPOINT_PROPERTIES,
                                        EndpointConfigRequest.USER_LINK_KEY}),
                        enumerationContext.idToSubscription.keySet())
                .build();
        return azureSubscriptionEndpointQuery;
    }

    private Query createQueryForAzureSubscriptionComputes(
            AzureSubscriptionsEnumerationContext enumerationContext) {
        //Fetch ComputeStates having custom property endPointType as Azure, Type as VM_HOST
        Query azureSubscriptionComputesQuery = Query.Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addCompositeFieldClause(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                        EndpointAllocationTaskService.CUSTOM_PROP_ENPOINT_TYPE,
                        EndpointType.azure.name())
                .addCompositeFieldClause(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                        AzureConstants.AZURE_ENROLLMENT_NUMBER_KEY,
                        enumerationContext.parentAuth.privateKeyId)
                .addFieldClause(ComputeState.FIELD_NAME_TYPE, ComputeType.VM_HOST)
                .addFieldClause(ComputeState.FIELD_NAME_ENDPOINT_LINK,
                        enumerationContext.parent.endpointLink)
                .build();
        return azureSubscriptionComputesQuery;
    }

    private void createResources(AzureSubscriptionsEnumerationContext enumerationContext,
                                 AzureCostComputeEnumerationStages nextStage) {
        // Go through new subscriptions and create corresponding ComputeState
        // and ComputeDescription for them
        Collection<ComputeState> computesToCreate = new ArrayList<>();

        // Create ComputeDescription
        Collection<Operation> createComputeDescOps = enumerationContext.idToSubscription.values()
                .stream()
                .map(subscription -> {
                    Operation op = Operation.createPost(UriUtils.extendUri(getInventoryServiceUri(),
                            ComputeDescriptionService.FACTORY_LINK))
                            .setBody(AzureUtils
                                    .constructAzureSubscriptionComputeDescription(
                                            enumerationContext.parent.endpointLink,
                                            enumerationContext.parent.tenantLinks,
                                            subscription.entityId, null, null))
                            .setCompletion((o, e) -> {
                                if (e != null) {
                                    logSevere(
                                            () -> String.format("Compute description creation "
                                                            + " failed for azure subscription %s",
                                                    subscription.entityId));
                                    return;
                                }
                                ComputeDescription cd = o.getBody(ComputeDescription.class);
                                String csName = AzureUtils.constructSubscriptionName(subscription);
                                computesToCreate.add(AzureUtils
                                        .constructAzureSubscriptionComputeState(
                                        enumerationContext.parent.endpointLink, cd.documentSelfLink,
                                        enumerationContext.parent.tenantLinks, csName,
                                        enumerationContext.parent.resourcePoolLink,
                                        getPropertiesMap(enumerationContext, subscription,
                                                true), null));
                            });
                    return op;
                })
                .collect(Collectors.toList());

        joinOperationAndSendRequest(createComputeDescOps, enumerationContext, (subsEnumCtx) -> {
            // Now create the ComputeState
            Collection<Operation> createComputeOps = computesToCreate.stream()
                    .map(computeState -> {
                        Operation op = Operation.createPost(UriUtils
                                .extendUri(getInventoryServiceUri(), ComputeService.FACTORY_LINK))
                                .setBody(computeState)
                                .setCompletion((o, e) -> {
                                    if (e != null) {
                                        logSevere(() -> String
                                                .format("Compute state creation failed for azure" +
                                                        " subscription %s", computeState.name ));
                                        return;
                                    }
                                });
                        return op;
                    })
                    .collect(Collectors.toList());
            joinOperationAndSendRequest(createComputeOps, subsEnumCtx, (enumCtx) -> {
                enumCtx.stage = nextStage;
                handleAzureSubscriptionsEnumerationRequest(enumCtx);
            });
        });
    }

    private void joinOperationAndSendRequest(
            Collection<Operation> operations,
            AzureSubscriptionsEnumerationContext enumerationContext,
            Consumer<AzureSubscriptionsEnumerationContext> successConsumer) {

        if (operations.isEmpty()) {
            successConsumer.accept(enumerationContext);
            return;
        }

        OperationJoin.create(operations).setCompletion((operationMap, exception) -> {
            if (exception != null && !exception.isEmpty()) {
                Throwable firstException = exception.values().iterator().next();
                getFailureConsumer(enumerationContext).accept(firstException);
                return;
            }
            successConsumer.accept(enumerationContext);
        }).sendWith(this, OPERATION_BATCH_SIZE);
    }

    private Map<String, String> getPropertiesMap(
            AzureSubscriptionsEnumerationContext enumerationContext,
            AzureSubscription subscription, boolean isAutoCreated) {
        Map<String, String> properties = new HashMap<>();
        // Store the subscription GUID as the account ID since Azure accounts are
        // identified by their subscription GUIDs.
        properties.put(PhotonModelConstants.CLOUD_ACCOUNT_ID, subscription.entityId);
        properties.put(AzureConstants.AZURE_SUBSCRIPTION_ID_KEY, subscription.entityId);
        properties.put(AzureConstants.AZURE_ENROLLMENT_NUMBER_KEY,
                enumerationContext.parentAuth.privateKeyId);
        properties.put(AzureConstants.AZURE_ACCOUNT_OWNER_EMAIL_ID, subscription.parentEntityId);
        if (StringUtils.isNotBlank(subscription.parentEntityName)) {
            properties.put(AzureConstants.AZURE_ACCOUNT_OWNER_NAME, subscription.parentEntityName);
        }
        if (isAutoCreated) {
            properties.put(PhotonModelConstants.AUTO_DISCOVERED_ENTITY, Boolean.TRUE.toString());
        }
        return properties;
    }

    private Consumer<Throwable> getFailureConsumer(AzureSubscriptionsEnumerationContext statsData) {
        return ((t) -> {
            logSevere(() -> String.format("Azure subscription enumeration failed at %s due to %s",
                    statsData.stage.toString(), Utils.toString(t)));
            statsData.operation.fail(t);
        });
    }

    private URI getInventoryServiceUri() {
        return ClusterUtil.getClusterUri(getHost(), ServiceTypeCluster.INVENTORY_SERVICE);
    }
}
