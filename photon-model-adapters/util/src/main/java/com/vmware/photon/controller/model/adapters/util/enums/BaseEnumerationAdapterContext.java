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

import static com.vmware.xenon.services.common.QueryTask.NumericRange.createLessThanRange;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.vmware.photon.controller.model.adapters.util.ComputeEnumerateAdapterRequest;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.util.PhotonModelUtils;
import com.vmware.photon.controller.model.tasks.QueryStrategy;
import com.vmware.photon.controller.model.tasks.QueryUtils.QueryByPages;
import com.vmware.photon.controller.model.tasks.QueryUtils.QueryTop;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.services.common.QueryTask.Query;

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

    protected final LOCAL_STATE SKIP = null;

    /**
     * The service that is creating and using this context.
     */
    public final StatelessService service;

    /**
     * The {@link ComputeEnumerateAdapterRequest request} that is being processed.
     */
    public final ComputeEnumerateAdapterRequest request;

    /**
     * The operation that triggered this enumeration. Used to signal the completion of the
     * enumeration once all the stages are successfully completed.
     */
    public final Operation operation;

    protected final Class<LOCAL_STATE> localStateClass;
    protected final String localStateServiceFactoryLink;

    /**
     * Page of remote resources as fetched from the remote endpoint. key -> id; value -> Remote
     * object.
     */
    public final Map<String, REMOTE> remoteResources = new ConcurrentHashMap<>();

    /**
     * States stored in local document store. key -> Local state id (matching remote id); value ->
     * Local resource state.
     */
    public final Map<String, LOCAL_STATE> localResourceStates = new ConcurrentHashMap<>();

    /**
     * Used to store an error while transferring to the error stage.
     */
    public Throwable error;

    protected String enumExternalResourcesNextPageLink;

    /**
     * In-memory store of all remote resource ids being enumerated.
     */
    public final Set<String> enumExternalResourcesIds = new HashSet<>();

    /**
     * The time when this enumeration started. It is used to identify stale resources that should be
     * deleted during deletion stage.
     */
    public long enumStartTimeInMicros;

    public EnumerationStages stage = EnumerationStages.CLIENT;

    protected enum BaseEnumerationAdapterStage {
        GET_REMOTE_RESOURCES, QUERY_LOCAL_STATES, CREATE_UPDATE_LOCAL_STATES, DELETE_LOCAL_STATES, FINISHED
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
     * @param request
     *            The request that triggered this enumeration.
     * @param operation
     *            The operation that triggered this enumeration.
     * @param localStateClass
     *            The class representing the local resource states.
     * @param localStateServiceFactoryLink
     *            The factory link of the service handling the local resource states.
     */
    public BaseEnumerationAdapterContext(StatelessService service,
            ComputeEnumerateAdapterRequest request,
            Operation operation,
            Class<LOCAL_STATE> localStateClass,
            String localStateServiceFactoryLink) {

        this.service = service;
        this.request = request;
        this.operation = operation;
        this.localStateClass = localStateClass;
        this.localStateServiceFactoryLink = localStateServiceFactoryLink;
    }

    /**
     * Starts the enumeration process and returns a {@link DeferredResult} to signal completion.
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
     *            Link to the the next page. null if this is the call for the first page).
     */
    protected abstract DeferredResult<RemoteResourcesPage> getExternalResources(
            String nextPageLink);

    /**
     * Creates/updates a resource base on the remote resource.
     * <p>
     * <b>Note</b>: Descendants are off-loaded from setting the following properties:
     * <ul>
     * <li>{@code id} property should not be set since it is automatically set to the id of the
     * remote resource.</li>
     * <li>{@code tenantLinks} property should not be set since it is automatically set to
     * {@code request.parentCompute.tenantLinks}.</li>
     * <li>{@code endpointLink} property should not be set since it is automatically set to
     * {@code request.original.endpointLink}.</li>
     * </ul>
     *
     * @param remoteResource
     *            The remote resource that should be represented in Photon model as a result of
     *            current enumeration.
     * @param existingLocalResourceState
     *            The existing local resource state that matches the remote resource. null means
     *            there is no local resource state representing the remote resource.
     * @return The resource state (either existing or new) that describes the remote resource.
     */
    protected abstract DeferredResult<LOCAL_STATE> buildLocalResourceState(
            REMOTE remoteResource, LOCAL_STATE existingLocalResourceState);

    /**
     * Descendants should override this method to specify the criteria to locate the local resources
     * managed by this enumeration.
     *
     * @param qBuilder
     *            The builder used to express the query criteria.
     *
     * @see #queryLocalStates(BaseEnumerationAdapterContext) for details about the GET criteria
     *      being pre-set/used by this enumeration logic.
     * @see #deleteLocalStates(BaseEnumerationAdapterContext) for details about the DELETE criteria
     *      being pre-set/used by this enumeration logic.
     */
    protected abstract void customizeLocalStatesQuery(Query.Builder qBuilder);

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

            return getExternalResources(this.enumExternalResourcesNextPageLink)
                    .thenCompose(resourcesPage -> {
                        this.enumExternalResourcesNextPageLink = resourcesPage != null
                                ? resourcesPage.nextPageLink
                                : null;

                        if (resourcesPage != null && resourcesPage.resourcesPage != null) {
                            // Store locally.
                            this.remoteResources.putAll(resourcesPage.resourcesPage);

                            // Store ALL enum'd resource ids
                            this.enumExternalResourcesIds.addAll(
                                    resourcesPage.resourcesPage.keySet());
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
                        if (c.enumExternalResourcesNextPageLink != null) {
                            c.service.logFine(() -> "Fetch the next page of remote resources.");
                            return enumerate(BaseEnumerationAdapterStage.GET_REMOTE_RESOURCES);
                        }
                        return enumerate(BaseEnumerationAdapterStage.DELETE_LOCAL_STATES);
                    });

        case DELETE_LOCAL_STATES:
            return deleteLocalStates(self());

        default:
            return DeferredResult.completed(self());
        }
    }

    /**
     * Load local resource states that match the {@link #getExternalResources(String) page} of
     * remote resources that are being processed.
     * <p>
     * Here is the list of criteria used to locate the local resources states:
     * <ul>
     * <li>Add local documents' kind:
     * {@code qBuilder.addKindFieldClause(context.localStateClass)}</li>
     * <li>Add remote resources ids:
     * {@code qBuilder.addInClause(ResourceState.FIELD_NAME_ID, remoteResourceIds)}</li>
     * <li>Add {@code tenantLinks} and {@code endpointLink} criteria as defined by
     * {@code QueryTemplate}</li>
     * <li>Add descendant specific criteria as defined by
     * {@link customizeLocalStatesQuery(qBuilder)}</li>
     * </ul>
     */
    protected DeferredResult<T> queryLocalStates(T context) {

        if (context.remoteResources.isEmpty()) {
            return DeferredResult.completed(context);
        }

        Set<String> remoteResourceIds = context.remoteResources.keySet();

        Query.Builder qBuilder = Query.Builder.create()
                // Add documents' class
                .addKindFieldClause(context.localStateClass)
                // Add remote resources IDs
                .addInClause(ResourceState.FIELD_NAME_ID, remoteResourceIds);

        // Delegate to descendants to any doc specific criteria
        customizeLocalStatesQuery(qBuilder);

        QueryStrategy<LOCAL_STATE> queryLocalStates = new QueryTop<>(
                context.service.getHost(),
                qBuilder.build(),
                context.localStateClass,
                context.request.parentCompute.tenantLinks,
                context.request.original.endpointLink)
                        .setMaxResultsLimit(remoteResourceIds.size());

        return queryLocalStates
                .queryDocuments(doc -> context.localResourceStates.put(doc.id, doc))
                .thenApply(ignore -> context);
    }

    /**
     * Create new local resource states or update matching resource states with the actual state in
     * the remote system.
     */
    protected DeferredResult<T> createUpdateLocalResourceStates(T context) {

        context.service.logFine(() -> String.format("Create/Update local %ss to match remote resources.",
                context.localStateClass.getSimpleName()));

        if (context.remoteResources.isEmpty()) {
            context.service.logFine(() -> "No resources available for create/update.");
            return DeferredResult.completed(context);
        }

        List<DeferredResult<Operation>> drs = context.remoteResources.entrySet().stream()
                .map(entry -> {
                    // First delegate to descendant to provide the local resource to create/update
                    return buildLocalResourceState(entry.getValue(),
                            context.localResourceStates.get(entry.getKey()))
                                    .thenApply(localState -> {
                                        if (localState == context.SKIP) {
                                            return localState;
                                        }
                                        /*
                                         * Explicitly set the local resource state id to be equal to
                                         * the remote resource state id. This is important in the
                                         * query for local states.
                                         */
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

        boolean existsLocally = this.localResourceStates.containsKey(localState.id);

        final Operation op;

        if (existsLocally) {
            // Update case.
            op = Operation.createPatch(this.service, localState.documentSelfLink);
        } else {
            // Create case.

            // By default populate TENANT_LINKS
            localState.tenantLinks = this.request.parentCompute.tenantLinks;

            // By default populate ENDPOINT_ILNK
            PhotonModelUtils.setEndpointLink(localState, this.request.original.endpointLink);

            op = Operation.createPost(this.service, this.localStateServiceFactoryLink);
        }

        op.setBody(localState);

        return this.service.sendWithDeferredResult(op)
                .exceptionally(ex -> {
                    this.service.logWarning(() -> String.format("%s local %s to match remote"
                                    + " resources: ERROR - %s", op.getAction(),
                            op.getUri().getPath(), ex.getMessage()));
                    return op;
                });
    }

    /**
     * Delete stale local resource states. The logic works by recording a timestamp when enumeration
     * starts. This timestamp is used to lookup resources which have not been touched as part of
     * current enumeration cycle.
     * <p>
     * Here is the list of criteria used to locate the stale local resources states:
     * <ul>
     * <li>Add local documents' kind:
     * {@code qBuilder.addKindFieldClause(context.localStateClass)}</li>
     * <li>Add time stamp older than current enumeration cycle:
     * {@code qBuilder.addRangeClause(ServiceDocument.FIELD_NAME_UPDATE_TIME_MICROS, createLessThanRange(context.enumStartTimeInMicros))}</li>
     * <li>Add {@code tenantLinks} and {@code endpointLink} criteria as defined by
     * {@code QueryTemplate}</li>
     * <li>Add descendant specific criteria as defined by
     * {@link customizeLocalStatesQuery(qBuilder)}</li>
     * </ul>
     */
    protected DeferredResult<T> deleteLocalStates(T context) {

        context.service.logFine(() -> String.format("Delete %ss that no longer exist in the endpoint.",
                context.localStateClass.getSimpleName()));

        Query.Builder qBuilder = Query.Builder.create()
                .addKindFieldClause(context.localStateClass)
                .addRangeClause(
                        ServiceDocument.FIELD_NAME_UPDATE_TIME_MICROS,
                        createLessThanRange(context.enumStartTimeInMicros));

        // Delegate to descendants to any doc specific criteria
        customizeLocalStatesQuery(qBuilder);

        QueryStrategy<LOCAL_STATE> queryLocalStates = new QueryByPages<>(
                context.service.getHost(),
                qBuilder.build(),
                context.localStateClass,
                context.request.parentCompute.tenantLinks,
                context.request.original.endpointLink);

        // Delete stale resources but do NOT wait the deletion to complete
        // nor fail if individual deletion has failed.
        return queryLocalStates.queryDocuments(ls -> {
            if (shouldDelete(ls)) {
                Operation dOp = Operation.createDelete(context.service, ls.documentSelfLink);

                context.service.sendWithDeferredResult(dOp).whenComplete((o, e) -> {
                    if (e != null) {
                        context.service.logWarning(
                                "Delete %s that no longer exist in the Endpoint: ERROR - %s",
                                ls.documentSelfLink,
                                e.getMessage());
                    }
                });
            }
        }).thenApply(ignore -> context);
    }

    /**
     * Checks whether the local state should be deleted.
     */
    protected boolean shouldDelete(LOCAL_STATE localState) {
        return true;
    }
}
