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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.vmware.photon.controller.model.adapterapi.ResourceRequest;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureCostConstants;
import com.vmware.photon.controller.model.adapters.azure.model.cost.AzureSubscription;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext.BaseAdapterStage;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;

import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
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
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;

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

    public static final String COMPUTES_NAME_FORMAT = "%s-%s"; //azure-subscriptionUuid

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
        FETCH_EXISTING_RESOURCES, CREATE_RESOURCES, COMPLETED
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
                this.stage = AzureCostComputeEnumerationStages.FETCH_EXISTING_RESOURCES;
            }
            this.operation = parentOp;
        }

        @Override
        protected URI getParentAuthRef(AzureSubscriptionsEnumerationContext context) {
            return UriUtils.extendUri(ClusterUtil.getClusterUri(context.service.getHost(),
                    ServiceTypeCluster.DISCOVERY_SERVICE),
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
        Consumer<AzureSubscriptionsEnumerationContext> queryCompletionConsumer =
                (AzureSubscriptionsEnumerationContext enumContext) -> {
                    enumContext.stage = nextStage;
                    handleAzureSubscriptionsEnumerationRequest(enumContext);
                };

        Consumer<Collection<ComputeState>> queryResultsConsumer =
                (Collection<ComputeState> computeStates) -> {
                    filterExistingSubscriptions(enumerationContext, computeStates);
                };

        queryForAzureSubscriptionComputes(enumerationContext, queryResultsConsumer,
                queryCompletionConsumer);
    }

    private void queryForAzureSubscriptionComputes(
            AzureSubscriptionsEnumerationContext enumerationContext,
            Consumer<Collection<ComputeState>> queryResultsConsumer,
            Consumer<AzureSubscriptionsEnumerationContext> queryCompletionConsumer) {
        Query azureComputesQuery =
                createQueryForAzureSubscriptionComputes(enumerationContext);

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .setResultLimit(QueryUtils.DEFAULT_RESULT_LIMIT)
                .setQuery(azureComputesQuery).build();
        queryTask.tenantLinks = enumerationContext.parent.tenantLinks;

        QueryUtils.startQueryTask(this, queryTask, ServiceTypeCluster.DISCOVERY_SERVICE)
                .whenComplete((responseTask, t) -> {
                    if (t != null) {
                        getFailureConsumer(enumerationContext).accept(t);
                        return;
                    }
                    queryResultsConsumer.accept(getComputeStates(responseTask.results.documents));
                    if (responseTask.results.nextPageLink != null) {
                        queryRemainingAzureSubscriptionComputes(responseTask.results.nextPageLink,
                                enumerationContext, queryResultsConsumer, queryCompletionConsumer);
                    } else {
                        queryCompletionConsumer.accept(enumerationContext);
                    }
                });
    }

    private Collection<ComputeState> getComputeStates(Map<String, Object> documents) {
        if (documents == null || documents.isEmpty()) {
            return Collections.emptyList();
        }
        Collection<ComputeState> azureAccountComputeStates = documents
                        .values().stream()
                        .map(s -> Utils.fromJson(s, ComputeState.class))
                        .collect(Collectors.toList());
        return azureAccountComputeStates;
    }

    private void queryRemainingAzureSubscriptionComputes(
            String nextPageLink,
            AzureSubscriptionsEnumerationContext enumerationContext,
            Consumer<Collection<ComputeState>> queryResultsConsumer,
            Consumer<AzureSubscriptionsEnumerationContext> queryCompletionConsumer) {
        sendRequest(Operation.createGet(UriUtils.extendUri(getInventoryServiceUri(), nextPageLink))
                .setCompletion( (op, t) -> {
                    if (t != null) {
                        getFailureConsumer(enumerationContext).accept(t);
                        return;
                    }
                    QueryTask responseTask = op.getBody(QueryTask.class);
                    queryResultsConsumer.accept(getComputeStates(responseTask.results.documents));
                    if (responseTask.results.nextPageLink != null) {
                        queryRemainingAzureSubscriptionComputes(responseTask.results.nextPageLink,
                                enumerationContext, queryResultsConsumer, queryCompletionConsumer);
                    } else {
                        queryCompletionConsumer.accept(enumerationContext);
                    }
                }));
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
                        AzureCostConstants.AZURE_ENROLLMENT_NUMBER_KEY,
                        enumerationContext.parentAuth.privateKeyId)
                .addFieldClause(ComputeState.FIELD_NAME_TYPE, ComputeType.VM_HOST)
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
                            .setBody(createComputeDescription(enumerationContext, subscription))
                            .setCompletion((o, e) -> {
                                if (e != null) {
                                    logSevere(
                                            () -> String.format("Compute description creation "
                                                            + " failed for azure subscription %s",
                                                    subscription.entityId));
                                    return;
                                }
                                ComputeDescription cd = o.getBody(ComputeDescription.class);
                                computesToCreate.add(createComputeState(enumerationContext,
                                                subscription, cd));
                            });
                    return op;
                })
                .collect(Collectors.toList());

        joinOperationAndSendRequest(createComputeDescOps, enumerationContext, (costComputeCxt) -> {
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
            joinOperationAndSendRequest(createComputeOps, enumerationContext, (costComputeCtx) -> {
                enumerationContext.stage = nextStage;
                handleAzureSubscriptionsEnumerationRequest(enumerationContext);
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

    private void filterExistingSubscriptions(
            AzureSubscriptionsEnumerationContext enumerationContext,
            Collection<ComputeState> existingComputes) {
        // Go through each of existing computes  and look
        // for ones without subscriptionUuid in custom property
        existingComputes.stream()
                .forEach(computeState -> {
                    if (computeState.customProperties != null
                            && computeState.customProperties
                            .containsKey(AzureCostConstants.AZURE_SUBSCRIPTION_ID_KEY)) {
                        String subscriptionUuid = computeState.customProperties
                                .get(AzureCostConstants.AZURE_SUBSCRIPTION_ID_KEY);
                        enumerationContext.idToSubscription.remove(subscriptionUuid);
                    }
                });
    }

    private ComputeDescription createComputeDescription(
            AzureSubscriptionsEnumerationContext enumerationContext,
            AzureSubscription subscription) {
        ComputeDescription cd = new ComputeDescription();
        cd.tenantLinks = enumerationContext.parent.tenantLinks;
        cd.endpointLink = enumerationContext.parent.endpointLink;
        cd.name = String.format(COMPUTES_NAME_FORMAT,
                enumerationContext.parent.description.name,
                subscription.entityId);
        cd.environmentName = ComputeDescription.ENVIRONMENT_NAME_AZURE;
        cd.id = UUID.randomUUID().toString();
        Map<String, String> customProperties = new HashMap<>();
        customProperties.put(EndpointAllocationTaskService.CUSTOM_PROP_ENPOINT_TYPE,
                EndpointType.azure.name());
        cd.customProperties = customProperties;
        return cd;
    }

    private ComputeState createComputeState(AzureSubscriptionsEnumerationContext enumerationContext,
                                            AzureSubscription subscription,
                                            ComputeDescription computeDescription) {
        ComputeState cs = new ComputeState();
        cs.id = UUID.randomUUID().toString();
        cs.name = String.format(COMPUTES_NAME_FORMAT,
                enumerationContext.parent.name, subscription.entityId);
        cs.tenantLinks = enumerationContext.parent.tenantLinks;
        cs.endpointLink = enumerationContext.parent.endpointLink;
        cs.customProperties = getPropertiesMap(enumerationContext, subscription);
        cs.environmentName = ComputeDescription.ENVIRONMENT_NAME_AZURE;
        cs.type = ComputeType.VM_HOST;
        cs.descriptionLink = computeDescription.documentSelfLink;
        return cs;
    }

    private Map<String, String> getPropertiesMap(
            AzureSubscriptionsEnumerationContext enumerationContext,
            AzureSubscription subscription) {
        Map<String, String> properties = new HashMap<>();
        properties.put(AzureCostConstants.AZURE_SUBSCRIPTION_ID_KEY,
                subscription.entityId);
        properties.put(AzureCostConstants.AZURE_ENROLLMENT_NUMBER_KEY,
                enumerationContext.parentAuth.privateKeyId);
        properties.put(AzureCostConstants.AZURE_ACCOUNT_ID,
                subscription.parentEntityId);
        properties.put(EndpointAllocationTaskService.CUSTOM_PROP_ENPOINT_TYPE,
                EndpointType.azure.name());
        properties.put(PhotonModelConstants.AUTO_DISCOVERED_ENTITY, Boolean.TRUE.toString());
        return properties;
    }

    private Consumer<Throwable> getFailureConsumer(AzureSubscriptionsEnumerationContext statsData) {
        return ((t) -> {
            logSevere(() -> String.format("Azure Cost Compute enumeration failed at %s due to %s",
                    statsData.stage.toString(), Utils.toString(t)));
            statsData.operation.fail(t);
        });
    }

    private URI getInventoryServiceUri() {
        return ClusterUtil.getClusterUri(getHost(), ServiceTypeCluster.DISCOVERY_SERVICE);
    }
}
