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

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import static com.vmware.xenon.services.common.QueryTask.NumericRange.createLessThanRange;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
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
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask.Query;

/**
 * The class abstracts the core enumeration logic per end-point. It consists of the following steps:
 * <ul>
 * <li>Loads the end-point state and authentication from provided end-point reference.</li>
 * <li>Loads remote resources from the remote system page-by-page.</li>
 * <li>Creates or updates corresponding local resource states.</li>
 * <li>Deletes stale local resource states.</li>
 * </ul>
 * <p>
 * To use the class override its abstract methods and call {@link #enumerate()} method.
 *
 * @param <T>
 *            The derived enumeration class.
 * @param <LOCAL_STATE>
 *            The class representing the local resource states.
 * @param <REMOTE>
 *            The class representing the remote resource.
 */
// NOTE: FOR INTERNAL PURPOSE ONLY. WILL BE PROMOTED TO PUBLIC IN A NEXT CL.
public abstract class EndpointEnumerationProcess<T extends EndpointEnumerationProcess<T, LOCAL_STATE, REMOTE>, LOCAL_STATE extends ResourceState, REMOTE> {

    /**
     * The service that is creating and initiating this enumeration process.
     */
    public final StatelessService service;

    /**
     * The end-point URI for which the enumeration process is triggered.
     */
    public final URI endpointReference;

    // Extracted from endpointReference {{
    protected EndpointState endpointState;
    protected AuthCredentialsServiceState endpointAuthState;
    // }}

    protected final Class<LOCAL_STATE> localStateClass;
    protected final String localStateServiceFactoryLink;

    protected final LOCAL_STATE SKIP = null;

    /**
     * Represents a single page of remote resources.
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
     * Current page of remote resources as fetched from the remote end-point.
     * <ul>
     * <li>key = remote object id</li>
     * <li>value = remote object</li>
     * </ul>
     */
    public final Map<String, REMOTE> remoteResources = new ConcurrentHashMap<>();

    /**
     * In-memory store of <b>all</> remote resource ids being enumerated.
     */
    public final Set<String> enumExternalResourcesIds = new HashSet<>();

    /**
     * The time when this enumeration started. It is used to identify stale resources that should be
     * deleted during deletion stage.
     */
    protected long enumStartTimeInMicros;

    /**
     * Link to the next page of remote resources. {@code null} indicates 'no more pages'.
     */
    protected String enumExternalResourcesNextPageLink;

    /**
     * States stored in local store that correspond to current page of {@link #remoteResources}.
     * <ul>
     * <li>key = local state id (matching remote object id)</li>
     * <li>value = local state</li>
     * </ul>
     */
    public final Map<String, LOCAL_STATE> localResourceStates = new ConcurrentHashMap<>();

    public class LocalStateHolder {

        public LOCAL_STATE localState;
        /**
         * From the key-value pairs, TagStates are created or updated. The localState's tagLinks
         * list is updated with the new remote tags, and the local-only tags are preserved.
         */
        public Map<String, String> remoteTags = new HashMap<>();
    }

    /**
     * Constructs the {@link EndpointEnumerationProcess}.
     *
     * @param service
     *            The service that is creating and using this enumeration logic.
     * @param endpointReference
     *            Reference to the end-point that is target of this enumeration.
     * @param localStateClass
     *            The class representing the local resource states.
     * @param localStateServiceFactoryLink
     *            The factory link of the service handling the local resource states.
     */
    public EndpointEnumerationProcess(StatelessService service,
            URI endpointReference,
            Class<LOCAL_STATE> localStateClass,
            String localStateServiceFactoryLink) {

        this.service = service;
        this.endpointReference = endpointReference;
        this.localStateClass = localStateClass;
        this.localStateServiceFactoryLink = localStateServiceFactoryLink;
    }

    /**
     * The main method that starts the enumeration process and returns a {@link DeferredResult} to
     * signal completion.
     */
    public DeferredResult<T> enumerate() {

        this.enumStartTimeInMicros = Utils.getNowMicrosUtc();

        return DeferredResult.completed(self())
                .thenCompose(this::getEndpointState)
                .thenCompose(this::getEndpointAuthState)
                .thenCompose(this::enumeratePageByPage)
                .thenCompose(this::deleteLocalResourceStates);
    }

    /**
     * Return a page of external resources from the remote system.
     *
     * @param nextPageLink
     *            Link to the the next page. null if this is the call for the first page.
     *
     * @see {@link RemoteResourcesPage} for information about the format of the returned data.
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
     * {@code endpointState.tenantLinks}.</li>
     * <li>{@code endpointLink} property should not be set since it is automatically set to
     * {@code endpointState.documentSelfLink}.</li>
     * </ul>
     *
     * @param remoteResource
     *            The remote resource that should be represented in Photon model as a result of
     *            current enumeration.
     * @param existingLocalResourceState
     *            The existing local resource state that matches the remote resource. {@code null}
     *            means there is no local resource state representing the remote resource.
     *
     * @return An instance of local state holder, consisting of the resource state (either existing
     *         or new) that describes the remote resource, and its tags placed in a map.
     */
    protected abstract DeferredResult<LocalStateHolder> buildLocalResourceState(
            REMOTE remoteResource, LOCAL_STATE existingLocalResourceState);

    /**
     * Descendants should override this method to specify the criteria to locate the local resources
     * managed by this enumeration.
     *
     * @param qBuilder
     *            The builder used to express the query criteria.
     *
     * @see {@link #queryLocalResourceStates(EndpointEnumerationProcess)} for details about the GET
     *      criteria being pre-set/used by this enumeration logic.
     * @see {@link #deleteLocalResourceStates(EndpointEnumerationProcess)} for details about the
     *      DELETE criteria being pre-set/used by this enumeration logic.
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
     * Resolve {@code EndpointState} from {@link #endpointReference} and set it to
     * {@link #endpointState}.
     */
    protected DeferredResult<T> getEndpointState(T context) {

        Operation op = Operation.createGet(context.endpointReference);

        return context.service
                .sendWithDeferredResult(op, EndpointState.class)
                .thenApply(state -> {
                    context.endpointState = state;
                    return context;
                });
    }

    /**
     * Resolve {@code AuthCredentialsServiceState end-point auth} from {@link #endpointState} and
     * set it to {@link #endpointAuthState}.
     */
    protected DeferredResult<T> getEndpointAuthState(T context) {

        Operation op = Operation.createGet(context.service,
                context.endpointState.authCredentialsLink);

        return context.service
                .sendWithDeferredResult(op, AuthCredentialsServiceState.class)
                .thenApply(state -> {
                    context.endpointAuthState = state;
                    return context;
                });
    }

    /**
     * Get a page of remote resources from the remote system.
     */
    protected DeferredResult<T> getRemoteResources(T context) {

        // Clear any previous results.
        this.remoteResources.clear();
        this.localResourceStates.clear();

        // Delegate to descendants to fetch remote resources
        return getExternalResources(this.enumExternalResourcesNextPageLink)

                .thenApply(remoteResourcesPage -> {

                    context.service.logFine(() -> String.format(
                            "Fetch next page [%s] of %d remote resources: SUCCESS",
                            this.enumExternalResourcesNextPageLink == null
                                    ? "FIRST" : this.enumExternalResourcesNextPageLink,
                            remoteResourcesPage.resourcesPage.size()));

                    // Store locally.
                    this.remoteResources.putAll(remoteResourcesPage.resourcesPage);

                    this.enumExternalResourcesNextPageLink = remoteResourcesPage.nextPageLink;

                    // Store ALL enum'd resource ids
                    this.enumExternalResourcesIds.addAll(this.remoteResources.keySet());

                    return context;
                });
    }

    /**
     * Enumerate remote resources page-by-page.
     */
    protected DeferredResult<T> enumeratePageByPage(T context) {

        return DeferredResult.completed(context)
                .thenCompose(this::getRemoteResources)
                .thenCompose(this::queryLocalResourceStates)
                .thenCompose(this::createUpdateLocalResourceStates)
                .thenCompose(ctx -> ctx.enumExternalResourcesNextPageLink != null
                        ? enumeratePageByPage(ctx)
                        : DeferredResult.completed(ctx));
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
    protected DeferredResult<T> queryLocalResourceStates(T context) {

        context.service.logFine(
                () -> String.format("Query local %ss to match %d remote resources.",
                        context.localStateClass.getSimpleName(),
                        context.remoteResources.size()));

        if (context.remoteResources.isEmpty()) {
            return DeferredResult.completed(context);
        }

        Set<String> remoteIds = context.remoteResources.keySet();

        Query.Builder qBuilder = Query.Builder.create()
                // Add documents' class
                .addKindFieldClause(context.localStateClass)
                // Add remote resources IDs
                .addInClause(ResourceState.FIELD_NAME_ID, remoteIds);

        // Delegate to descendants to any doc specific criteria
        customizeLocalStatesQuery(qBuilder);

        QueryTop<LOCAL_STATE> queryLocalStates = new QueryTop<>(
                context.service.getHost(),
                qBuilder.build(),
                context.localStateClass,
                context.endpointState.tenantLinks,
                context.endpointState.documentSelfLink);
        queryLocalStates.setMaxResultsLimit(remoteIds.size());

        return queryLocalStates
                .queryDocuments(doc -> context.localResourceStates.put(doc.id, doc))
                .thenApply(ignore -> context);
    }

    /**
     * Create new local resource states or update matching resource states with the actual state in
     * the remote system.
     */
    protected DeferredResult<T> createUpdateLocalResourceStates(T context) {

        String msg = "POST/PATCH local %ss to match %d remote resources: %s";

        context.service.logFine(() -> String.format(msg,
                context.localStateClass.getSimpleName(),
                context.remoteResources.size(),
                "STARTING"));

        if (context.remoteResources.isEmpty()) {
            return DeferredResult.completed(context);
        }

        List<DeferredResult<Operation>> drs = context.remoteResources.entrySet().stream()

                .map(remoteResourceEntry -> {

                    String remoteResourceId = remoteResourceEntry.getKey();
                    REMOTE remoteResource = remoteResourceEntry.getValue();

                    LOCAL_STATE localResource = context.localResourceStates.get(remoteResourceId);

                    // Delegate to descendants to provide the local resource state to create/update
                    return buildLocalResourceState(remoteResource, localResource)

                            /*
                             * Explicitly set the local resource state id to be equal to the remote
                             * resource state id. This is important in the query for local states.
                             */
                            .thenApply(lsHolder -> {
                                if (lsHolder.localState != context.SKIP) {
                                    lsHolder.localState.id = remoteResourceId;
                                }
                                return lsHolder;
                            })

                            // Then actually update/create the state
                            .thenCompose(this::createUpdateLocalResourceState);
                })

                .collect(Collectors.toList());

        return DeferredResult.allOf(drs).thenApply(ops -> {

            this.service.logFine(
                    () -> String.format(msg,
                            context.localStateClass.getSimpleName(),
                            context.remoteResources.size(),
                            ops.stream().collect(groupingBy(Operation::getAction, counting()))));

            return context;
        });
    }

    protected DeferredResult<Operation> createUpdateLocalResourceState(
            LocalStateHolder localStateHolder) {

        final ResourceState localState = localStateHolder.localState;

        if (localState == this.SKIP) {
            return DeferredResult.completed(null);
        }

        final LOCAL_STATE currentState = this.localResourceStates.get(localState.id);

        // POST or PATCH local state
        final Operation lsOp;

        if (currentState == null) {
            // Create case

            // By default populate TENANT_LINKS
            localState.tenantLinks = this.endpointState.tenantLinks;

            // By default populate ENDPOINT_ILNK
            PhotonModelUtils.setEndpointLink(localState, this.endpointState.documentSelfLink);

            lsOp = Operation.createPost(this.service, this.localStateServiceFactoryLink);
        } else {
            // Update case

            lsOp = Operation.createPatch(this.service, currentState.documentSelfLink);
        }

        lsOp.setBody(localState);

        // Create local tag states
        DeferredResult<Void> tagsDR = createLocalTagStates(localStateHolder);

        return tagsDR.thenCompose(ignore -> this.service.sendWithDeferredResult(lsOp)
                .exceptionally(ex -> {
                    this.service.logWarning(
                            () -> String.format("%s local %s to match remote resources: ERROR - %s",
                                    lsOp.getAction(),
                                    lsOp.getUri().getPath(),
                                    ex.getMessage()));
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
    protected DeferredResult<T> deleteLocalResourceStates(T context) {

        context.service
                .logFine(() -> String.format("Delete %ss that no longer exist in the endpoint.",
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
                context.endpointState.tenantLinks,
                context.endpointState.documentSelfLink);

        // Delete stale resources but do NOT wait the deletion to complete
        // nor fail if individual deletion has failed.
        return queryLocalStates.queryDocuments(ls -> {

            if (shouldDelete(ls)) {

                Operation dOp = Operation.createDelete(context.service, ls.documentSelfLink);

                context.service.sendWithDeferredResult(dOp).whenComplete((o, e) -> {
                    final String msg = "Delete stale %s state";
                    if (e != null) {
                        context.service.logWarning(msg + ": ERROR - %s",
                                ls.documentSelfLink, Utils.toString(e));
                    } else {
                        context.service.log(Level.FINEST, msg + ": SUCCESS", ls.documentSelfLink);
                    }
                });
            }
        }).thenApply(ignore -> context);
    }

    /**
     * Checks whether the local state should be deleted.
     */
    protected boolean shouldDelete(LOCAL_STATE localState) {
        return !this.enumExternalResourcesIds.contains(localState.id);
    }

    // Tag Utility methods {{
    protected DeferredResult<Void> createLocalTagStates(LocalStateHolder localStateHolder) {

        if (localStateHolder.remoteTags == null || localStateHolder.remoteTags.isEmpty()) {
            return DeferredResult.completed((Void) null);
        }

        localStateHolder.localState.tagLinks = new HashSet<>();

        List<DeferredResult<TagState>> localTagStatesDRs = localStateHolder.remoteTags
                .entrySet()
                .stream()
                .map(tagEntry -> newTagState(tagEntry.getKey(), tagEntry.getValue(),
                        this.endpointState.tenantLinks))
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

        // add tenant
        tagState.tenantLinks = tenantLinks;

        tagState.documentSelfLink = TagFactoryService.generateSelfLink(tagState);

        return tagState;
    }
    // }}
}
