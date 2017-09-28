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

import static com.vmware.photon.controller.model.adapters.util.AdapterUtils.getDeletionState;
import static com.vmware.photon.controller.model.resources.util.PhotonModelUtils.setEndpointLink;
import static com.vmware.xenon.services.common.QueryTask.NumericRange.createLessThanRange;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest;
import com.vmware.photon.controller.model.adapters.util.TagsUtil;
import com.vmware.photon.controller.model.query.QueryUtils.QueryByPages;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.util.AssertUtil;
import com.vmware.photon.controller.model.util.ClusterUtil.ServiceTypeCluster;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;

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
public abstract class EndpointEnumerationProcess<T extends EndpointEnumerationProcess<T, LOCAL_STATE, REMOTE>, LOCAL_STATE extends ResourceState, REMOTE> {

    private static final int MAX_RESOURCES_TO_QUERY_ON_DELETE = Integer
            .getInteger(UriPaths.PROPERTY_PREFIX
                    + "enum.max.resources.query.on.delete", 950);

    /**
     * The service that is creating and initiating this enumeration process.
     */
    public final StatelessService service;

    /**
     * The end-point URI for which the enumeration process is triggered.
     */
    public final URI endpointReference;

    // Extracted from endpointReference {{
    public EndpointState endpointState;
    public AuthCredentialsServiceState endpointAuthState;
    // }}

    protected final Class<LOCAL_STATE> localStateClass;
    protected final String localStateServiceFactoryLink;

    public ResourceState resourceDeletionState = new ResourceState();

    protected final LOCAL_STATE SKIP = null;

    /**
     * Flag controlling whether infra fields (such as tenantLinks and endpointLink) should be
     * applied (for example set or populated). Default value is {@code true}.
     *
     * @see #createUpdateLocalResourceState(LocalStateHolder)
     */
    private boolean applyInfraFields = true;

    /**
     * Flag controlling whether queries should be agnostic to endpointLink or not (as part of resource deduplication
     * work).
     */
    private boolean endpointLinkAgnostic = false;

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
     * @param deletedResourceExpirationMicros
     *            Time in micros at which to expire deleted resources.
     */
    public EndpointEnumerationProcess(StatelessService service,
            URI endpointReference,
            Class<LOCAL_STATE> localStateClass,
            String localStateServiceFactoryLink,
            long deletedResourceExpirationMicros) {
        this.service = service;
        this.endpointReference = endpointReference;
        this.localStateClass = localStateClass;
        this.localStateServiceFactoryLink = localStateServiceFactoryLink;
        this.resourceDeletionState = getDeletionState(deletedResourceExpirationMicros);
    }

    public boolean isApplyInfraFields() {
        return this.applyInfraFields;
    }

    public void setApplyInfraFields(boolean applyInfraFields) {
        this.applyInfraFields = applyInfraFields;
    }

    public boolean isEndpointLinkAgnostic() {
        return this.endpointLinkAgnostic;
    }

    public void setEndpointLinkAgnostic(boolean endpointLinkAgnostic) {
        this.endpointLinkAgnostic = endpointLinkAgnostic;
    }

    /**
     * The main method that starts the enumeration process and returns a {@link DeferredResult} to
     * signal completion.
     */
    public DeferredResult<T> enumerate() {

        this.enumStartTimeInMicros = Utils.getNowMicrosUtc();

        return DeferredResult.completed(self())
                .thenCompose(this::getEndpointState)
                .thenApply(log("getEndpointState"))
                .thenCompose(this::getEndpointAuthState)
                .thenApply(log("getEndpointAuthState"))
                .thenCompose(this::enumeratePageByPage)
                .thenApply(log("enumeratePageByPage"))
                .thenCompose(this::deleteLocalResourceStates)
                .thenApply(log("deleteLocalResourceStates"));
    }

    /**
     * Use this to log success after completing async execution stage.
     */
    protected Function<? super T, ? extends T> log(String stage) {
        return (ctx) -> {
            ctx.service.log(Level.FINE, "%s.%s: SUCCESS", this.getClass().getSimpleName(), stage);
            return ctx;
        };
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
     * @see {@link #queryLocalStates(EndpointEnumerationProcess)} for details about the GET criteria
     *      being pre-set/used by this enumeration logic.
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
     * By default return
     * {@code this.endpointState.endpointProperties.get(EndpointConfigRequest.REGION_KEY)} which
     * might be {@code null}. Descendants might override to provide specific region in case
     * REGION_KEY property is not specified.
     */
    public String getEndpointRegion() {
        AssertUtil.assertNotNull(
                this.endpointState,
                "endpointState should have been initialized by getEndpointState()");

        return this.endpointState.endpointProperties != null
                ? this.endpointState.endpointProperties.get(EndpointConfigRequest.REGION_KEY)
                : null;
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
                            "Fetch page [%s] of %d remote resources: SUCCESS",
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
                .thenCompose(this::queryLocalStates)
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
     * {@link #customizeLocalStatesQuery(com.vmware.xenon.services.common.QueryTask.Query.Builder)}</li>
     * </ul>
     */
    protected DeferredResult<T> queryLocalStates(T context) {

        String msg = "Query local %ss to match %d remote resources";

        context.service.logFine(
                () -> String.format(msg + ": STARTED",
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

        if (getEndpointRegion() != null) {
            // Limit documents within end-point region
            qBuilder.addFieldClause(ResourceState.FIELD_NAME_REGION_ID, getEndpointRegion());
        }

        // Delegate to descendants to any doc specific criteria
        customizeLocalStatesQuery(qBuilder);

        QueryByPages<LOCAL_STATE> queryLocalStates = new QueryByPages<>(
                context.service.getHost(),
                qBuilder.build(),
                context.localStateClass,
                isApplyInfraFields() ? context.endpointState.tenantLinks : null,
                !isEndpointLinkAgnostic() ? context.endpointState.documentSelfLink : null);
        queryLocalStates.setMaxPageSize(remoteIds.size());
        queryLocalStates.setClusterType(ServiceTypeCluster.INVENTORY_SERVICE);

        return queryLocalStates
                .queryDocuments(doc -> context.localResourceStates.put(doc.id, doc))
                .thenApply(ignore -> {
                    context.service.logFine(
                            () -> String.format(
                                    msg + ": FOUND %s",
                                    context.localStateClass.getSimpleName(),
                                    context.remoteResources.size(),
                                    context.localResourceStates.size()));

                    return context;
                });
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

            this.service.logFine(() -> String.format(msg,
                    context.localStateClass.getSimpleName(),
                    context.remoteResources.size(),
                    ops.stream().filter(Objects::nonNull)
                            .collect(groupingBy(Operation::getAction, counting()))));

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
        final Operation localStateOp;

        if (currentState == null) {
            // Create case

            if (localState.regionId == null) {
                // By default populate REGION_ID, if not already set by descendant
                localState.regionId = getEndpointRegion();
            }

            if (isApplyInfraFields()) {
                // By default populate TENANT_LINKS
                localState.tenantLinks = this.endpointState.tenantLinks;

                // By default populate ENDPOINT_ILNK
                setEndpointLink(localState, this.endpointState.documentSelfLink);

                localState.computeHostLink = this.endpointState.computeLink;
            }

            localStateOp = Operation.createPost(this.service, this.localStateServiceFactoryLink);
        } else {
            // Update case
            if (isApplyInfraFields()) {
                // update the endpointLinks
                setEndpointLink(localState, this.endpointState.documentSelfLink);
            }
            localStateOp = Operation.createPatch(this.service, currentState.documentSelfLink);
        }

        DeferredResult<Set<String>> tagLinksDR = TagsUtil.createOrUpdateTagStates(
                this.service,
                localState,
                currentState,
                localStateHolder.remoteTags);

        return tagLinksDR
                .thenApply(tagLinks -> {
                    localState.tagLinks = tagLinks;

                    localStateOp.setBodyNoCloning(localState);

                    return localStateOp;
                })
                .thenCompose(this.service::sendWithDeferredResult)
                .whenComplete((ignoreOp, exc) -> {
                    String msg = "%s local %s(id=%s) to match remote resources";
                    if (exc != null) {
                        this.service.logWarning(
                                () -> String.format(msg + ": FAILED with %s",
                                        localStateOp.getAction(),
                                        localState.getClass().getSimpleName(),
                                        localState.id,
                                        Utils.toString(exc)));
                    } else {
                        this.service.log(Level.FINEST,
                                () -> String.format(msg + ": SUCCESS",
                                        localStateOp.getAction(),
                                        localState.getClass().getSimpleName(),
                                        localState.id));
                    }
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
     * {@link #customizeLocalStatesQuery(com.vmware.xenon.services.common.QueryTask.Query.Builder)}</li>
     * </ul>
     */
    protected DeferredResult<T> deleteLocalResourceStates(T context) {

        final String msg = "Delete %ss that no longer exist in the endpoint: %s";

        context.service.logFine(
                () -> String.format(msg, context.localStateClass.getSimpleName(), "STARTING"));

        Query.Builder qBuilder = Query.Builder.create()
                // Add documents' class
                .addKindFieldClause(context.localStateClass)
                .addRangeClause(
                        ServiceDocument.FIELD_NAME_UPDATE_TIME_MICROS,
                        createLessThanRange(context.enumStartTimeInMicros));

        if (getEndpointRegion() != null) {
            // Limit documents within end-point region
            qBuilder.addFieldClause(ResourceState.FIELD_NAME_REGION_ID, getEndpointRegion());
        }

        if (!this.enumExternalResourcesIds.isEmpty() &&
                this.enumExternalResourcesIds.size() <= MAX_RESOURCES_TO_QUERY_ON_DELETE) {
            // do not load resources from enumExternalResourcesIds
            qBuilder.addInClause(
                    ResourceState.FIELD_NAME_ID,
                    this.enumExternalResourcesIds,
                    Occurance.MUST_NOT_OCCUR);
        }

        // Delegate to descendants to any doc specific criteria
        customizeLocalStatesQuery(qBuilder);

        QueryByPages<LOCAL_STATE> queryLocalStates = new QueryByPages<>(
                context.service.getHost(),
                qBuilder.build(),
                context.localStateClass,
                isApplyInfraFields() ? context.endpointState.tenantLinks : null,
                !isEndpointLinkAgnostic() ? context.endpointState.documentSelfLink : null);

        List<DeferredResult<Operation>> ops = new ArrayList<>();

        // Delete stale resources.
        return queryLocalStates.queryDocuments(ls -> {
            if (!shouldDelete(ls)) {
                return;
            }

            Operation dOp = Operation.createDelete(context.service, ls.documentSelfLink)
                    .setBody(context.resourceDeletionState);

            DeferredResult<Operation> dr = context.service.sendWithDeferredResult(dOp)
                    .whenComplete((o, e) -> {
                        final String message = "Delete stale %s state";
                        if (e != null) {
                            context.service.logWarning(message + ": FAILED with %s",
                                    ls.documentSelfLink, Utils.toString(e));
                        } else {
                            context.service.log(Level.FINEST, message + ": SUCCESS",
                                    ls.documentSelfLink);
                        }
                    });

            ops.add(dr);
        })
                .thenCompose(r -> DeferredResult.allOf(ops))
                .thenApply(r -> context);

    }

    /**
     * Checks whether the local state should be deleted.
     */
    protected boolean shouldDelete(LOCAL_STATE localState) {
        return !this.enumExternalResourcesIds.contains(localState.id);
    }
}
