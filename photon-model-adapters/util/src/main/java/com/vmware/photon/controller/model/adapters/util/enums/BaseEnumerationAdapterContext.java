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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.vmware.photon.controller.model.adapters.util.ComputeEnumerateAdapterRequest;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.TagFactoryService;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.photon.controller.model.resources.util.PhotonModelUtils;
import com.vmware.photon.controller.model.tasks.QueryStrategy;
import com.vmware.photon.controller.model.tasks.QueryUtils.QueryByPages;
import com.vmware.photon.controller.model.tasks.QueryUtils.QueryTop;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceStateCollectionUpdateRequest;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;
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

        this.enumStartTimeInMicros = Utils.getNowMicrosUtc();

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
     * <b>Note</b>: Descendants are responsible to provide key-values map describing the tags for
     * the remote resource, and are off-loaded from setting the following properties:
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
     * @return An instance of local state holder, consisting of the resource state (either existing
     *         or new) that describes the remote resource, and its tags placed in a map
     */
    protected abstract DeferredResult<LocalStateHolder> buildLocalResourceState(
            REMOTE remoteResource, LOCAL_STATE existingLocalResourceState);

    public class LocalStateHolder {

        public LOCAL_STATE localState;
        /**
         * From the key-value pairs, TagStates are created or updated. The localState's tagLinks
         * list is updated with the new remote tags, and the local-only tags are perserved.
         */
        public Map<String, String> remoteTags = new HashMap<>();
    }

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
     * <li>Add local documents' kind: {@code qBuilder.addKindFieldClause(context.localStateClass)}</li>
     * <li>Add remote resources ids:
     * {@code qBuilder.addInClause(ResourceState.FIELD_NAME_ID, remoteResourceIds)}</li>
     * <li>Add {@code tenantLinks} and {@code endpointLink} criteria as defined by
     * {@code QueryTemplate}</li>
     * <li>Add descendant specific criteria as defined by
     * {@link #customizeLocalStatesQuery(com.vmware.xenon.services.common.QueryTask.Query.Builder)}</li>
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

        context.service.logFine(() -> String.format(
                "Create/Update local %ss to match remote resources.",
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
                            .thenApply(stateHolder -> {
                                if (stateHolder.localState == context.SKIP) {
                                    return stateHolder;
                                }
                                /*
                                 * Explicitly set the local resource state id to be equal to the
                                 * remote resource state id. This is important in the query for
                                 * local states.
                                 */
                                stateHolder.localState.id = entry.getKey();
                                return stateHolder;
                            })
                            // then update/create state
                            .thenCompose(this::createUpdateLocalResourceState);
                }).collect(Collectors.toList());

        return DeferredResult.allOf(drs).thenApply(ignore -> context);
    }

    private DeferredResult<Operation> createUpdateLocalResourceState(
            LocalStateHolder localStateHolder) {
        if (localStateHolder.localState == this.SKIP) {
            return DeferredResult.completed(null);
        }

        final ResourceState localState = localStateHolder.localState;

        LOCAL_STATE currentState = this.localResourceStates.get(localState.id);

        final Operation lsOp;

        // Create local tag states
        DeferredResult<Void> tagsDR = DeferredResult.completed((Void) null);

        if (currentState != null) {
            // Update case.

            if (currentState.tagLinks != null && !currentState.tagLinks.isEmpty()) {
                // Update local tag states with the remote values
                tagsDR = updateLocalTagStates(localStateHolder, currentState);
            } else {
                // Create local tag states
                tagsDR = createLocalTagStates(localStateHolder);
            }

            lsOp = Operation.createPatch(this.service, currentState.documentSelfLink);
        } else {
            // Create case.

            // By default populate TENANT_LINKS
            localStateHolder.localState.tenantLinks = this.request.parentCompute.tenantLinks;

            // By default populate ENDPOINT_ILNK
            PhotonModelUtils.setEndpointLink(localStateHolder.localState,
                    this.request.original.endpointLink);

            // Create local tag states
            tagsDR = createLocalTagStates(localStateHolder);

            lsOp = Operation.createPost(this.service, this.localStateServiceFactoryLink);
        }

        lsOp.setBody(localStateHolder.localState);

        return tagsDR.thenCompose(ignore -> this.service.sendWithDeferredResult(lsOp)
                .exceptionally(ex -> {
                    this.service.logWarning(() -> String.format("%s local %s to match remote"
                            + " resources: ERROR - %s", lsOp.getAction(),
                            lsOp.getUri().getPath(), ex.getMessage()));
                    return lsOp;
                }));
    }

    /**
     * Delete stale local resource states. The logic works by recording a timestamp when enumeration
     * starts. This timestamp is used to lookup resources which have not been touched as part of
     * current enumeration cycle.
     * <p>
     * Here is the list of criteria used to locate the stale local resources states:
     * <ul>
     * <li>Add local documents' kind: {@code qBuilder.addKindFieldClause(context.localStateClass)}</li>
     * <li>Add time stamp older than current enumeration cycle:
     * {@code qBuilder.addRangeClause(ServiceDocument.FIELD_NAME_UPDATE_TIME_MICROS, createLessThanRange(context.enumStartTimeInMicros))}
     * </li>
     * <li>Add {@code tenantLinks} and {@code endpointLink} criteria as defined by
     * {@code QueryTemplate}</li>
     * <li>Add descendant specific criteria as defined by
     * {@link #customizeLocalStatesQuery(com.vmware.xenon.services.common.QueryTask.Query.Builder)}</li>
     * </ul>
     */
    protected DeferredResult<T> deleteLocalStates(T context) {

        context.service.logFine(() -> String.format(
                "Delete %ss that no longer exist in the endpoint.",
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

    // Tag Utility methods {{
    private DeferredResult<Void> createLocalTagStates(LocalStateHolder localStateHolder) {

        if (localStateHolder.remoteTags == null || localStateHolder.remoteTags.isEmpty()) {
            return DeferredResult.completed((Void) null);
        }

        localStateHolder.localState.tagLinks = new HashSet<>();

        List<DeferredResult<TagState>> localTagStatesDRs = localStateHolder.remoteTags
                .entrySet()
                .stream()
                .map(tagEntry -> newTagState(tagEntry.getKey(), tagEntry.getValue(),
                        this.request.parentCompute.tenantLinks))
                .map(tagState -> {
                    // add the link of the new tag in to the tagLinks list
                    localStateHolder.localState.tagLinks.add(tagState.documentSelfLink);
                    return tagState;
                })
                .map(tagState -> Operation
                        .createPost(this.service, TagService.FACTORY_LINK)
                        .setBody(tagState))
                .map(tagOperation -> this.service.sendWithDeferredResult(tagOperation,
                        TagState.class))
                .collect(Collectors.toList());

        return DeferredResult.allOf(localTagStatesDRs).thenApply(ignore -> (Void) null);
    }

    private DeferredResult<Void> updateLocalTagStates(LocalStateHolder localStateHolder,
            LOCAL_STATE currentState) {

        Map<String, TagState> remoteTagStates = localStateHolder.remoteTags.entrySet().stream()
                .map(tagEntry -> newTagState(tagEntry.getKey(), tagEntry.getValue(),
                        currentState.tenantLinks))
                .collect(Collectors.toMap(tagState -> tagState.documentSelfLink,
                        tagState -> tagState));

        final DeferredResult<List<TagState>> createAllLocalTagStatesDR;
        final Collection<String> tagLinksToAdd;
        {
            // the remote tags which do not exist locally will be added to the computeState tagLinks
            // list
            tagLinksToAdd = new HashSet<>(remoteTagStates.keySet());
            tagLinksToAdd.removeAll(currentState.tagLinks);

            // not existing locally tags should be created
            List<DeferredResult<TagState>> localTagStatesDRs = tagLinksToAdd.stream()
                    .map(tagLinkObj -> remoteTagStates.get(tagLinkObj))
                    .map(tagState -> Operation
                            .createPost(this.service, TagService.FACTORY_LINK)
                            .setBody(tagState))
                    .map(tagOperation -> this.service.sendWithDeferredResult(tagOperation,
                            TagState.class))
                    .collect(Collectors.toList());

            createAllLocalTagStatesDR = DeferredResult.allOf(localTagStatesDRs);
        }

        final DeferredResult<Collection<String>> removeAllExternalTagLinksDR;
        {
            // all local tag links which do not have remote correspondents and are external will be
            // removed from the currentState's tagLinks list
            Set<String> tagLinksToRemove = new HashSet<>(currentState.tagLinks);
            tagLinksToRemove.removeAll(remoteTagStates.keySet());

            if (tagLinksToRemove.isEmpty()) {

                removeAllExternalTagLinksDR = DeferredResult.completed(tagLinksToRemove);

            } else {
                // identify which of the local tag states to remove are external
                Query.Builder qBuilder = Query.Builder.create()
                        // Add documents' class
                        .addKindFieldClause(TagState.class)
                        // Add local tag links to remove
                        .addInClause(ServiceDocument.FIELD_NAME_SELF_LINK, tagLinksToRemove)
                        .addFieldClause(TagState.FIELD_NAME_EXTERNAL, Boolean.TRUE.toString());

                QueryStrategy<TagState> queryLocalStates = new QueryTop<>(
                        this.service.getHost(),
                        qBuilder.build(),
                        TagState.class,
                        currentState.tenantLinks,
                        this.request.original.endpointLink)
                        .setMaxResultsLimit(tagLinksToRemove.size());

                removeAllExternalTagLinksDR = queryLocalStates.collectLinks(Collectors
                        .toCollection(HashSet::new));
            }
        }

        return createAllLocalTagStatesDR.thenCompose(ignore -> removeAllExternalTagLinksDR)
                .thenCompose(
                        removeAllExternalTagLinks -> updateResourceTagLinks(removeAllExternalTagLinks,
                                tagLinksToAdd,
                                currentState.documentSelfLink))
                .thenAccept(ignore -> { });
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private DeferredResult<Operation> updateResourceTagLinks(
            Collection<String> tagLinksToDelete,
            Collection<String> tagLinksToAdd,
            String currentStateSelfLink) {

        // create patch operation to update tag links of the current resource state
        Map<String, Collection<Object>> collectionsToRemoveMap = new HashMap<>();
        collectionsToRemoveMap.put(ComputeState.FIELD_NAME_TAG_LINKS, (Collection) tagLinksToDelete);

        Map<String, Collection<Object>> collectionsToAddMap = new HashMap<>();
        collectionsToAddMap.put(ComputeState.FIELD_NAME_TAG_LINKS, (Collection) tagLinksToAdd);

        ServiceStateCollectionUpdateRequest updateTagLinksRequest =
                ServiceStateCollectionUpdateRequest.create(
                        collectionsToAddMap,
                        collectionsToRemoveMap);

        Operation createPatch = Operation.createPatch(this.service, currentStateSelfLink)
                .setBody(updateTagLinksRequest);

        return this.service.sendWithDeferredResult(createPatch);
    }

    public static void setTagLinksToResourceState(ResourceState resourceState,
            Map<String, String> tags) {
        if (tags == null || tags.isEmpty()) {
            return;
        }

        // we have already made sure that the tags exist and we can build their links ourselves
        resourceState.tagLinks = tags.entrySet().stream()
                .map(t -> newTagState(t.getKey(), t.getValue(), resourceState.tenantLinks))
                .map(TagFactoryService::generateSelfLink)
                .collect(Collectors.toSet());
    }

    public static TagState newTagState(String key, String value, List<String> tenantLinks) {
        TagState tagState = new TagState();
        tagState.key = key == null ? "" : key;
        tagState.value = value == null ? "" : value;
        tagState.external = true;
        // add tenant
        tagState.tenantLinks = tenantLinks;

        tagState.documentSelfLink = TagFactoryService.generateSelfLink(tagState);
        return tagState;
    }
    // }}
}
