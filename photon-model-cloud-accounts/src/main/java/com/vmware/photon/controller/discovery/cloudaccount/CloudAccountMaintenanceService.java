/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.discovery.cloudaccount;

import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_CREATEDBY_EMAIL;
import static com.vmware.photon.controller.model.query.QueryUtils.startInventoryQueryTask;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.vmware.photon.controller.discovery.common.services.UserService.UserState;
import com.vmware.photon.controller.discovery.endpoints.EndpointUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.QueryResultsProcessor;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * <p>
 * This class will cleanup any missing data related to cloud accounts (aka: endpoints)
 * that might arise when clients use the "old" {@code /provisioning/endpoints photon-model} APIs
 * to onboard accounts.
 * </p><p>
 * When the {@code photon-model} services are used directly to create endpoints, the custom
 * properties needed to display "Created By Email" will not be present. This maintenance task will
 * find any endpoints that are missing the "created by" custom property and add them back in.
 * </p><p>
 * It also takes care of adding the S3 bills bucket to the custom property if it detects an S3
 * bucket was added via a direct {@code photon-model} call and the matching custom property is not
 * added to the {@code EndpointState} like it would've been had the new {@code /api/cloud-accounts}
 * API been used.
 * </p><p>
 * NOTE: This maintenance service only needs to run on a single node in the node group and should
 * only be used via {@link com.vmware.photon.controller.model.tasks.ScheduledTaskService}
 * </p>
 */
public class CloudAccountMaintenanceService extends StatefulService {
    public static final String FACTORY_LINK = UriPaths.CLOUD_ACCOUNT_MAINTENANCE_SERVICE;

    /** When processing the QueryTask to cleanup data, use this for the page size. */
    public static final String PROPERTY_QUERY_SIZE = "CloudAccountMaintenanceService.QUERY_SIZE";
    private static final int DEFAULT_QUERY_SIZE = 50;

    /** If expiration isn't set explicitly, only keep state for this long. */
    private static final Long DEFAULT_EXPIRATION_MICROS = TimeUnit.MINUTES.toMicros(15);

    /** Create a default factory service that starts instances of this task service on POST. */
    public static FactoryService createFactory() {
        return FactoryService.create(CloudAccountMaintenanceService.class);
    }

    public static class CloudAccountMaintenanceState extends ServiceDocument {
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public Set<String> cleanedEndpointLinks;

        /** Stores any exception that occurs while processing the maintenance task. */
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public Throwable exception;
    }

    public CloudAccountMaintenanceService() {
        super(CloudAccountMaintenanceState.class);
    }

    @Override
    public void handleStart(Operation op) {
        initializeState(op);

        // This query will return all endpoints that were created with the photon-model API
        // directly (and thus, don't have the createdBy.email customProperty)
        // NOTE: if authPrincipal is the system user... then we won't be able to clean createdBy.
        String createdByField = QuerySpecification.buildCompositeFieldName(
                ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                ENDPOINT_CUSTOM_PROPERTY_NAME_CREATEDBY_EMAIL);
        // Don't continue to process endpoints that have already been cleaned
        String maintenanceCompleteField = QuerySpecification.buildCompositeFieldName(
                ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_DISCOVERY_MAINT_COMPLETE);
        Query query = Query.Builder.create()
                .addKindFieldClause(EndpointState.class)
                .addFieldClause(
                        createdByField, UriUtils.URI_WILDCARD_CHAR,
                        MatchType.WILDCARD, Occurance.MUST_NOT_OCCUR)
                .addFieldClause(maintenanceCompleteField, Boolean.TRUE.toString(),
                        Occurance.MUST_NOT_OCCUR)
                .build();

        int resultLimit = Integer.getInteger(PROPERTY_QUERY_SIZE, DEFAULT_QUERY_SIZE);

        // Expand computeLink so we can see if the S3 bills bucket name was added
        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .setQuery(query)
                .addLinkTerm("computeLink")
                .addOptions(EnumSet.of(QueryOption.EXPAND_CONTENT, QueryOption.SELECT_LINKS,
                        QueryOption.EXPAND_LINKS))
                .setResultLimit(resultLimit)
                .build();

        logFine("Cleaning endpoint data with QueryTask:\n%s", Utils.toJson(queryTask));
        startInventoryQueryTask(this, queryTask, true)
                .whenComplete((qt, failure) -> {
                    if (failure != null) {
                        logWarning("Error cleaning endpoint data:\n%s", Utils.toString(failure));
                        op.fail(failure);
                        return;
                    }

                    processQueryPage(qt.results.nextPageLink, op);
                });
    }

    private void initializeState(Operation op) {
        CloudAccountMaintenanceState state = op.getBody(CloudAccountMaintenanceState.class);
        state.cleanedEndpointLinks = new HashSet<>();
        if (state.documentExpirationTimeMicros == 0L) {
            state.documentExpirationTimeMicros = DEFAULT_EXPIRATION_MICROS;
        }
    }

    private void finishTask(Operation maintenanceOp) {
        CloudAccountMaintenanceState state = maintenanceOp.getBody(CloudAccountMaintenanceState.class);
        if (state.exception != null) {
            logWarning("Maintenance task failed: %s", Utils.toString(state.exception));
            maintenanceOp.fail(state.exception);
        } else {
            logFine("Maintenance task finished successfully.");
            maintenanceOp.complete();
        }
    }

    private void processQueryPage(String nextPageLink, Operation maintenanceOp) {
        if (nextPageLink == null) {
            finishTask(maintenanceOp);
            return;
        }

        // Get results page as system user so we can access all endpoints and the user service
        OperationContext origContext = OperationContext.getOperationContext();
        Operation getPage = Operation.createGet(this, nextPageLink);
        setAuthorizationContext(getPage, getSystemAuthorizationContext());
        sendWithDeferredResult(getPage, QueryTask.class)
                .thenAccept(queryTask -> processQueryTaskResults(queryTask, nextPageLink, maintenanceOp))
                .whenComplete((aVoid, err) -> OperationContext.restoreOperationContext(origContext));
    }

    private void processQueryTaskResults(QueryTask queryTask, String nextPageLink, Operation maintenanceOp) {
        QueryResultsProcessor processor = QueryResultsProcessor.create(queryTask);
        AtomicLong endpointsRemaining = new AtomicLong(processor.getQueryResult().documentCount);
        if (endpointsRemaining.get() == 0L) {
            finishTask(maintenanceOp);
            return;
        }

        CloudAccountMaintenanceState state = maintenanceOp.getBody(CloudAccountMaintenanceState.class);
        logInfo("Cleaning %s endpoints from [nextPageLink=%s]", endpointsRemaining.get(), nextPageLink);

        for (EndpointState endpoint : processor.documents(EndpointState.class)) {
            if (endpoint.customProperties == null) {
                endpoint.customProperties = new HashMap<>();
            }

            // If the bills bucket was provided, copy value to expected place in customProperties
            ComputeState compute = processor.selectedDocument(endpoint.computeLink, ComputeState.class);
            String billsBucketName = compute.customProperties != null ?
                    compute.customProperties.get(AWSConstants.AWS_BILLS_S3_BUCKET_NAME_KEY) : null;
            logFine("Processing [endpoint=%s] [computeLink=%s] [compute.customProperties=%s]",
                    endpoint.documentSelfLink, endpoint.computeLink, compute.customProperties);
            if (billsBucketName != null && billsBucketName.trim().length() > 0) {
                endpoint.customProperties.put(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET, billsBucketName);
            }

            // Populate customProperty for createdBy.email by looking up the owner's email
            // (unless the "owner" is already the system user)
            if (!ServiceUriPaths.CORE_AUTHZ_SYSTEM_USER.equals(endpoint.documentAuthPrincipalLink)) {
                sendWithDeferredResult(
                        Operation.createGet(this, endpoint.documentAuthPrincipalLink), UserState.class)
                        .thenAccept(userState -> {
                            endpoint.customProperties.put(ENDPOINT_CUSTOM_PROPERTY_NAME_CREATEDBY_EMAIL, userState.email);
                            saveCleanedEndpoints(endpoint, endpointsRemaining, state, processor, maintenanceOp);
                        })
                        .exceptionally(throwable -> {
                            logWarning("Error getting [user=%s]: %s",
                                    endpoint.documentAuthPrincipalLink, Utils.toString(throwable));
                            state.exception = throwable;
                            return null;
                        });
            } else {
                saveCleanedEndpoints(endpoint, endpointsRemaining, state, processor, maintenanceOp);
            }
        }
    }

    private void saveCleanedEndpoints(EndpointState endpoint, AtomicLong endpointsRemaining, CloudAccountMaintenanceState state, QueryResultsProcessor processor, Operation maintenanceOp) {
        // Mark this endpoint as being "cleaned" so we don't try and reprocess later
        endpoint.customProperties.put(
                EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_DISCOVERY_MAINT_COMPLETE,
                Boolean.TRUE.toString());

        Operation patchEndpoint = Operation.createPatch(this, endpoint.documentSelfLink)
                .setBody(endpoint);
        sendWithDeferredResult(patchEndpoint, EndpointState.class)
                .whenComplete((endpointState, throwable) -> {
                    if (throwable != null) {
                        logWarning("Error correcting [endpoint=%s] via PATCH: %s",
                                endpoint.documentSelfLink, Utils.toString(throwable));
                        state.exception = throwable;
                    } else {
                        logFine("Successfully PATCHed [endpoint=%s]", endpoint.documentSelfLink);
                        state.cleanedEndpointLinks.add(endpoint.documentSelfLink);
                    }

                    if (endpointsRemaining.decrementAndGet() == 0) {
                        logFine("Finished processing page");
                        processQueryPage(processor.getQueryResult().nextPageLink, maintenanceOp);
                    }
                });
    }
}
