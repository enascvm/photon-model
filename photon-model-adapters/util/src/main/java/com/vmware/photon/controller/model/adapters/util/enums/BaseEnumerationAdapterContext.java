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

package com.vmware.photon.controller.model.adapters.util.enums;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.tasks.QueryUtils;

import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;

/**
 * Base class for context used by enumeration adapters. It loads resources from the remote system.
 * Create/updates local resource states and deletes stale resource states.
 * <p>
 * To use the class override its abstract methods and call {@link #enumerate()} method.
 *
 * @param <T>
 *            The derived context class.
 * @param <LOCAL_STATE>
 *            The class representing the local resource states.
 * @param <REMOTE>
 *            The class representing the remote resource.
 */
public abstract class BaseEnumerationAdapterContext<T extends BaseEnumerationAdapterContext<T, LOCAL_STATE, REMOTE>, LOCAL_STATE extends ResourceState, REMOTE> {

    public static final String PROPERTY_NAME_ENUM_QUERY_RESULT_LIMIT =
            UriPaths.PROPERTY_PREFIX + "Enumeration.QUERY_RESULT_LIMIT";
    private static int DEFAULT_QUERY_RESULT_LIMIT = Integer.getInteger(
            PROPERTY_NAME_ENUM_QUERY_RESULT_LIMIT,
            100);

    protected final LOCAL_STATE SKIP = null;

    public ComputeStateWithDescription parentCompute;

    /**
     * Page of remote resources as fetched from the remote endpoint. key -> id; value -> Remote
     * object.
     */
    public Map<String, REMOTE> remoteResources = new ConcurrentHashMap<>();

    /**
     * States stored in local document store. key -> Local state id (matching remote id); value ->
     * Local resource state.
     */
    public Map<String, LOCAL_STATE> localResourceStates = new ConcurrentHashMap<>();

    /**
     * The service that is creating and using this context.
     */
    protected final StatelessService service;

    protected String enumNextPageLink;
    protected String deletionNextPageLink;

    protected Class<LOCAL_STATE> localStateClass;
    protected String localStateServiceFactoryLink;

    protected enum BaseEnumerationAdapterStage {
        GET_REMOTE_RESOURCES,
        QUERY_LOCAL_STATES,
        CREATE_UPDATE_LOCAL_STATES,
        DELETE_LOCAL_STATES,
        FINISHED
    }

    /**
     * Represents a single page of remote resource.
     */
    public class RemoteResourcesPage {
        /**
         * A link to the next page. Null if next page is not available.
         * <p>
         * The value returned will be passed to the next call of
         * {@link #getExternalResources(String)} method to fetch the next page.
         */
        public String nextPageLink;
        /**
         * The loaded page of remote resources.
         */
        public LinkedHashMap<String, REMOTE> resourcesPage = new LinkedHashMap<>();
    }

    /**
     * Constructs the {@link BaseEnumerationAdapterContext}.
     *
     * @param service
     *            The service that is creating and using this context.
     * @param localStateClass
     *            The class representing the local resource states.
     * @param parentCompute
     *            Parent compute with description.
     */
    public BaseEnumerationAdapterContext(StatelessService service,
            Class<LOCAL_STATE> localStateClass, String localStateServiceFactoryLink,
            ComputeStateWithDescription parentCompute) {
        this.service = service;
        this.localStateClass = localStateClass;
        this.localStateServiceFactoryLink = localStateServiceFactoryLink;
        this.parentCompute = parentCompute;
    }

    /**
     * @return Starts the enumeration process and returns a {@link DeferredResult} to signal
     *         completion.
     */
    public DeferredResult<T> enumerate() {
        return enumerate(BaseEnumerationAdapterStage.GET_REMOTE_RESOURCES);
    }

    /**
     * Return a page of external resources from the remote system.
     * <p>
     * {@see RemoteResourcesPage} for information about the format of the returned data.
     *
     * @param nextPageLink
     *            Information about the next page (null if this is the call for the first page).
     */
    protected abstract DeferredResult<RemoteResourcesPage> getExternalResources(
            String nextPageLink);

    /**
     * Creates/updates a new resource base on the remote resource.
     * <p>
     * Note: id property should not be set since it is automatically set to the id of the remote
     * resource.
     *
     * @param remoteResource
     *            remote resource.
     * @param existingLocalResourceState
     *            existing local resource that matches the remote resource. null is no local
     *            resource matches the remote one.
     * @return a resource state that describes the remote resource.
     */
    protected abstract DeferredResult<LOCAL_STATE> buildLocalResourceState(
            REMOTE remoteResource, LOCAL_STATE existingLocalResourceState);

    protected abstract Query getDeleteQuery();

    /**
     * Isolate all cases when this instance should be cast to T. For internal use only.
     */
    @SuppressWarnings("unchecked")
    protected T self() {
        return (T) this;
    }

    /**
     * Main enumeration state machine.
     */
    protected DeferredResult<T> enumerate(BaseEnumerationAdapterStage stage) {
        switch (stage) {
        case GET_REMOTE_RESOURCES:
            // Clear any previous results.
            this.remoteResources.clear();
            this.localResourceStates.clear();

            return getExternalResources(this.enumNextPageLink)
                    .thenCompose(resourcesPage -> {
                        this.enumNextPageLink = resourcesPage != null ? resourcesPage.nextPageLink
                                : null;

                        if ((resourcesPage != null) && (resourcesPage.resourcesPage != null)) {
                            // Store locally.
                            this.remoteResources.putAll(resourcesPage.resourcesPage);
                        }

                        return enumerate(BaseEnumerationAdapterStage.QUERY_LOCAL_STATES);
                    });
        case QUERY_LOCAL_STATES:
            return queryLocalStates(self())
                    .thenCompose(
                            c -> enumerate(BaseEnumerationAdapterStage.CREATE_UPDATE_LOCAL_STATES));
        case CREATE_UPDATE_LOCAL_STATES:
            return createUpdateLocalResourceStates(self())
                    .thenCompose(c -> {
                        if (c.enumNextPageLink != null) {
                            this.service.logFine("Fetch the next page remote resources.");
                            return enumerate(BaseEnumerationAdapterStage.GET_REMOTE_RESOURCES);
                        } else {
                            return enumerate(BaseEnumerationAdapterStage.DELETE_LOCAL_STATES);
                        }
                    });
        case DELETE_LOCAL_STATES:
            return deleteLocalStates(self());
        default:
            return DeferredResult.completed(self());

        }
    }

    /**
     * Load local resource states that match the page of remote resources that is being processed.
     */
    protected DeferredResult<T> queryLocalStates(T context) {
        this.service.logFine("Query local document store.");

        if (context.remoteResources == null || context.remoteResources.isEmpty()) {
            return DeferredResult.completed(context);
        }

        Query query = Query.Builder.create()
                .addKindFieldClause(this.localStateClass)
                .addInClause(ResourceState.FIELD_NAME_ID, context.remoteResources.keySet())
                .build();

        if (context.parentCompute.tenantLinks != null && !context.parentCompute.tenantLinks
                .isEmpty()) {
            query.addBooleanClause(Query.Builder.create()
                    .addInCollectionItemClause(ResourceState.FIELD_NAME_TENANT_LINKS,
                            context.parentCompute.tenantLinks)
                    .build()
            );
        }

        QueryTask q = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .addOption(QueryOption.TOP_RESULTS)
                .setResultLimit(DEFAULT_QUERY_RESULT_LIMIT)
                .setQuery(query)
                .build();
        q.tenantLinks = context.parentCompute.tenantLinks;

        return QueryUtils.startQueryTask(this.service, q)
                .thenApply(queryTask -> {
                    this.service
                            .logFine("Found %d matching resource group states for Cloud resources.",
                                    queryTask.results.documentCount);

                    // If there are no matches, there is nothing to update.
                    if (queryTask.results != null && queryTask.results.documentCount > 0) {
                        queryTask.results.documents.values().forEach(localResourceState -> {
                            LOCAL_STATE localState = Utils.fromJson(localResourceState,
                                    this.localStateClass);
                            context.localResourceStates.put(localState.id,
                                    localState);
                        });
                    }

                    return context;
                });
    }

    /**
     * Create new local resource states or update matching resource states with the actual state in
     * the remote system.
     */
    protected DeferredResult<T> createUpdateLocalResourceStates(T context) {
        this.service.logFine("Create or update local resource with the actual state from "
                + "remote system.");

        if (context.remoteResources.isEmpty()) {
            this.service.logFine("No resources available for create/update.");
            return DeferredResult.completed(context);
        }

        List<DeferredResult<Operation>> drs = context.remoteResources.entrySet().stream()
                .map(entry -> {
                    // First delegate to descendant to provide the local resource to create/update
                    return buildLocalResourceState(entry.getValue(),
                            this.localResourceStates.get(entry.getKey()))
                                    .thenApply(localState -> {
                                        if (localState == this.SKIP) {
                                            return localState;
                                        }
                                        // Explicitly set the local resource state id to be equal to
                                        // the remote
                                        // resource state id. This is important in the query for
                                        // local states.
                                        localState.id = entry.getKey();
                                        return localState;
                                    })
                                    // then update/create state
                                    .thenCompose(this::createUpdateLocalResourceState);
                }).collect(Collectors.toList());

        return DeferredResult.allOf(drs).thenApply(ignore -> context);
    }

    private DeferredResult<Operation> createUpdateLocalResourceState(LOCAL_STATE localState) {
        if (localState == this.SKIP) {
            return DeferredResult.completed(null);
        }

        boolean existsLocally = this.localResourceStates
                .containsKey(localState.id);
        Operation op = existsLocally
                // Update case.
                ? Operation.createPatch(this.service, localState.documentSelfLink)
                // Create case.
                : Operation.createPost(this.service,
                        this.localStateServiceFactoryLink);

        op.setBody(localState);

        return this.service.sendWithDeferredResult(op).exceptionally(ex -> {
            this.service.logWarning("Error: %s", ex.getMessage());
            return op;
        });
    }

    ;

    /**
     * Delete stale local resource states.
     */
    protected DeferredResult<T> deleteLocalStates(T context) {
        this.service.logFine("Delete Resource Group States that no longer exists in the Cloud.");
        Query query = getDeleteQuery();
        QueryTask q = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .setQuery(query)
                .setResultLimit(DEFAULT_QUERY_RESULT_LIMIT)
                .build();
        q.tenantLinks = context.parentCompute.tenantLinks;

        this.service.logFine("Querying %s for deletion.", this.localStateClass.getSimpleName());

        return sendDeleteQueryTask(q, context);
    }

    private DeferredResult<T> sendDeleteQueryTask(QueryTask q, T context) {

        DeferredResult<T> deletetionCompletion = new DeferredResult<>();

        QueryUtils.startQueryTask(this.service, q)
                .thenAccept(queryTask -> {
                    context.deletionNextPageLink = queryTask.results.nextPageLink;

                    handleDeleteQueryTaskResult(context, deletetionCompletion);
                });
        return deletetionCompletion;
    }

    private void handleDeleteQueryTaskResult(T context, DeferredResult<T> deletionCompletion) {

        if (context.deletionNextPageLink == null) {
            this.service.logFine("Finished deletion stage .");
            deletionCompletion.complete(context);
        }

        this.service.logFine("Querying page [%s] for resources to be deleted",
                context.deletionNextPageLink);

        // Delete stale resources but don't wait the deletion to complete, nor fail if
        // individual deletion has failed.
        this.service.sendWithDeferredResult(
                Operation.createGet(this.service, context.deletionNextPageLink))
                .thenAccept(completedOp -> {
                    QueryTask queryTask = completedOp.getBody(QueryTask.class);

                    if (queryTask.results.documentCount > 0) {
                        // Delete all matching states.
                        List<DeferredResult<Operation>> deferredResults = queryTask.results.documentLinks
                                .stream()
                                .filter(link -> shouldDelete(queryTask, link))
                                .map(link -> Operation.createDelete(this.service, link))
                                .map(this.service::sendWithDeferredResult)
                                .collect(Collectors.toList());

                        DeferredResult.allOf(deferredResults).whenComplete((operations,
                                throwable) -> {

                            if (throwable != null) {
                                this.service.logWarning("Error: %s", throwable
                                        .getMessage());
                            }
                        });
                    }

                    // Store the next page in the context
                    context.deletionNextPageLink = queryTask.results.nextPageLink;

                    // Handle next page of results.
                    handleDeleteQueryTaskResult(context, deletionCompletion);

                });
    }

    /**
     * Checks whether the link should be deleted.
     */
    protected boolean shouldDelete(QueryTask queryTask, String link) {
        return true;
    }
}
