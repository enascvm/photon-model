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

import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest;
import com.vmware.photon.controller.model.adapterapi.ResourceRequest;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.ea.enumeration.AzureSubscriptionEndpointCreationService.AzureSubscriptionEndpointCreationRequest;
import com.vmware.photon.controller.model.adapters.azure.model.cost.AzureSubscription;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext.BaseAdapterStage;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.query.QueryUtils.QueryByPages;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.util.ClusterUtil;
import com.vmware.photon.controller.model.util.ClusterUtil.ServiceTypeCluster;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask.Query;

/**
 * Enumerator for creating EndpointStates for the subscriptions found  in Azure bill
 * This service as of now expects the invoker to send the details about the subscriptions
 * in Azure bill for which the endpoint enumeration has to happen.
 */

public class AzureSubscriptionEndpointsEnumerationService extends StatelessService {

    public static final String SELF_LINK = AzureUriPaths.AZURE_SUBSCRIPTION_ENDPOINTS_ENUMERATOR;

    private static final int OPERATION_BATCH_SIZE = 50;

    /**
     * The request class for creating computes for Azure costs, expects the details about the
     * entities to be sent in the request.
     */
    public static class AzureSubscriptionEndpointsEnumerationRequest extends ResourceRequest {
        public Collection<AzureSubscription> azureSubscriptions;
    }

    @Override
    public void handleStart(Operation startPost) {
        startHelperServices(startPost);
    }

    public AzureSubscriptionEndpointsEnumerationService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    protected enum AzureSubscriptionEndpointComputeEnumerationStages {
        FETCH_EXISTING_RESOURCES, CREATE_RESOURCES, COMPLETED
    }

    // Start related services
    private void startHelperServices(Operation startPost) {

        Operation postSubscriptionEndpointCreationService = Operation
                .createPost(this, AzureSubscriptionEndpointCreationService.SELF_LINK)
                .setReferer(this.getUri());

        this.getHost().startService(postSubscriptionEndpointCreationService,
                new AzureSubscriptionEndpointCreationService());

        AdapterUtils.registerForServiceAvailability(getHost(),
                operation -> startPost.complete(), startPost::fail,
                AzureSubscriptionEndpointCreationService.SELF_LINK);
    }

    protected static class AzureSubscriptionEndpointsEnumerationContext
            extends BaseAdapterContext<AzureSubscriptionEndpointsEnumerationContext> {
        protected AzureSubscriptionEndpointComputeEnumerationStages stage;
        protected Map<String, AzureSubscription> idToSubscription;

        public AzureSubscriptionEndpointsEnumerationContext(
                StatelessService service, AzureSubscriptionEndpointsEnumerationRequest
                        resourceRequest, Operation parentOp) {
            super(service, resourceRequest);
            // If azureSubscriptions is null or empty don't do anything
            if (resourceRequest.azureSubscriptions == null
                    || resourceRequest.azureSubscriptions.isEmpty()) {
                this.stage = AzureSubscriptionEndpointComputeEnumerationStages.COMPLETED;
            } else {
                this.stage = AzureSubscriptionEndpointComputeEnumerationStages
                        .FETCH_EXISTING_RESOURCES;
                this.idToSubscription = resourceRequest.azureSubscriptions
                        .stream()
                        .collect(Collectors.toMap(subscription -> subscription.entityId,
                                subscription -> subscription));
            }
            this.operation = parentOp;
        }
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }

        AzureSubscriptionEndpointsEnumerationRequest request =
                op.getBody(AzureSubscriptionEndpointsEnumerationRequest.class);
        if (request.isMockRequest) {
            op.complete();
            return;
        }
        AzureSubscriptionEndpointsEnumerationContext azureCostComputeEnumerationContext =
                new AzureSubscriptionEndpointsEnumerationContext(this, request, op);
        azureCostComputeEnumerationContext.populateBaseContext(BaseAdapterStage.PARENTDESC)
                .whenComplete((c, e) -> {
                    if (e != null) {
                        getFailureConsumer(azureCostComputeEnumerationContext).accept(e);
                        return;
                    }
                    handleAzureCostComputeEnumerationRequest(azureCostComputeEnumerationContext);
                });
    }

    private void handleAzureCostComputeEnumerationRequest(
            AzureSubscriptionEndpointsEnumerationContext enumerationContext) {
        try {
            switch (enumerationContext.stage) {
            case FETCH_EXISTING_RESOURCES:
                fetchExistingSubscriptionEndpoints(enumerationContext,
                        AzureSubscriptionEndpointComputeEnumerationStages.CREATE_RESOURCES);
                break;
            case CREATE_RESOURCES:
                createSubscriptionEndpoints(enumerationContext,
                        AzureSubscriptionEndpointComputeEnumerationStages.COMPLETED);
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

    private void fetchExistingSubscriptionEndpoints(
            AzureSubscriptionEndpointsEnumerationContext enumerationContext,
            AzureSubscriptionEndpointComputeEnumerationStages nextStage) {
        Query azureEndpointsQuery =
                createQueryForAzureSubscriptionEndpoints(enumerationContext);
        QueryByPages<EndpointState> querySubscriptionEndpoints = new QueryByPages<>(
                getHost(), azureEndpointsQuery, EndpointState.class,
                enumerationContext.parent.tenantLinks);
        querySubscriptionEndpoints.setClusterType(ServiceTypeCluster.DISCOVERY_SERVICE);

        querySubscriptionEndpoints.queryDocuments(endpointState -> {
                    if (endpointState.endpointProperties != null
                            && endpointState.endpointProperties
                            .containsKey(EndpointConfigRequest.USER_LINK_KEY)) {
                        String subscriptionUuid = endpointState.endpointProperties
                                .get(EndpointConfigRequest.USER_LINK_KEY);
                        enumerationContext.idToSubscription.remove(subscriptionUuid);
                    }
                }
                ).whenComplete((aVoid, t) -> {
                    if (t != null) {
                        getFailureConsumer(enumerationContext).accept(t);
                        return;
                    }
                    enumerationContext.stage = nextStage;
                    handleAzureCostComputeEnumerationRequest(enumerationContext);
                });
    }

    private Query createQueryForAzureSubscriptionEndpoints(
            AzureSubscriptionEndpointsEnumerationContext enumerationContext) {
        //Fetch Endpoints of type Azure and having parentLink as the Azure EA account
        return Query.Builder.create()
                .addKindFieldClause(EndpointState.class)
                .addFieldClause(EndpointState.FIELD_NAME_ENDPOINT_TYPE, EndpointType.azure.name())
                .addFieldClause(EndpointState.FIELD_NAME_PARENT_LINK,
                        enumerationContext.parent.endpointLink)
                .build();
    }

    private void createSubscriptionEndpoints(
            AzureSubscriptionEndpointsEnumerationContext enumerationContext,
            AzureSubscriptionEndpointComputeEnumerationStages nextStage) {

        // Create Endpoints for Subscriptions
        Collection<Operation> createSubscriptionEndpointOps =
                enumerationContext.idToSubscription.values()
                        .stream()
                        .map(subscription -> {
                            return Operation.createPatch(getHost(),
                                    AzureSubscriptionEndpointCreationService.SELF_LINK)
                                    .setBody(
                                            createSubscriptionEndpointCreationRequest(
                                                    enumerationContext, subscription))
                                    .setCompletion((o, e) -> {
                                        if (e != null) {
                                            logSevere(
                                                    () -> String.format("Creation of endpoint " +
                                                                    "failed for subscription %s",
                                                            subscription.entityId ));
                                            return;
                                        }
                                    });
                        })
                        .collect(Collectors.toList());

        joinOperationAndSendRequest(createSubscriptionEndpointOps, enumerationContext,
                (costComputeCtx) -> {
                    enumerationContext.stage = nextStage;
                    handleAzureCostComputeEnumerationRequest(enumerationContext);
                });
    }

    private AzureSubscriptionEndpointCreationRequest createSubscriptionEndpointCreationRequest(
            AzureSubscriptionEndpointsEnumerationContext context, AzureSubscription subscription) {
        AzureSubscriptionEndpointCreationRequest request =
                new AzureSubscriptionEndpointCreationRequest();
        request.accountId = subscription.parentEntityId;
        request.subscriptionId = subscription.entityId;
        request.resourceReference = UriUtils.extendUri(ClusterUtil.getClusterUri(getHost(),
                ServiceTypeCluster.DISCOVERY_SERVICE), context.parent.endpointLink);
        return request;
    }

    private void joinOperationAndSendRequest(
            Collection<Operation> operations,
            AzureSubscriptionEndpointsEnumerationContext enumerationContext,
            Consumer<AzureSubscriptionEndpointsEnumerationContext> successConsumer) {

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

    private Consumer<Throwable> getFailureConsumer(
            AzureSubscriptionEndpointsEnumerationContext statsData) {
        return ((t) -> {
            logSevere(() -> String.format("Azure Cost Compute enumeration failed at %s due to %s",
                    statsData.stage.toString(), Utils.toString(t)));
            statsData.operation.fail(t);
        });
    }
}
