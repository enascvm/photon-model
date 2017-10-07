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

package com.vmware.photon.controller.model.tasks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.LoadBalancerService.LoadBalancerState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.resources.util.PhotonModelUtils;
import com.vmware.photon.controller.model.tasks.ResourceGroomerTaskService.EndpointResourceDeletionRequest;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceStateCollectionUpdateRequest;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskFactoryService;
import com.vmware.xenon.services.common.TaskService;

/**
 * Groomer task for disassociating documents that are associated with invalid endpointLinks and
 * deleting all documents that have no endpoint associated with them.
 *
 * The task can be run periodically using ScheduledTaskService. The task requires a tenantLink
 * to be specified. Optionally, it can also be in context of a single endpoint.
 *
 * The task is invoked by EndpointRemovalTaskService to disassociate/delete documents which were
 * associated with the endpoint being deleted.
 */
public class ResourceGroomerTaskService
        extends TaskService<EndpointResourceDeletionRequest> {

    public static final String FACTORY_LINK = UriPaths.PROVISIONING + "/stale-document-deletion";

    public static final int QUERY_RESULT_LIMIT = QueryUtils.MAX_RESULT_LIMIT;
    public static final int OPERATION_BATCH_SIZE = PhotonModelConstants.OPERATION_BATCH_SIZE;

    public static final String STAT_NAME_DOCUMENTS_DELETED = "documentsDeletedCount";
    public static final String STAT_NAME_DOCUMENTS_PATCHED = "documentsPatchedCount";
    public static final String STAT_UNIT_COUNT = "count";

    public static final String COMPUTE_STATE_DOCUMENT_KIND = Utils.buildKind(ComputeState.class);
    public static final String DISK_STATE_DOCUMENT_KIND = Utils.buildKind(DiskState.class);
    public static final String COMPUTE_DESCRIPTION_DOCUMENT_KIND = Utils
            .buildKind(ComputeDescription.class);
    public static final String NETWORK_STATE_DOCUMENT_KIND = Utils.buildKind(NetworkState.class);
    public static final String NETWORK_INTERFACE_STATE_DOCUMENT_KIND = Utils
            .buildKind(NetworkInterfaceState.class);
    public static final String SECURITY_GROUP_STATE_DOCUMENT_KIND = Utils
            .buildKind(SecurityGroupState.class);
    public static final String SUBNET_STATE_DOCUMENT_KIND = Utils.buildKind(SubnetState.class);
    public static final String LOAD_BALANCER_DOCUMENT_KIND = Utils.buildKind(LoadBalancerState.class);
    public static final String STORAGE_DESCRIPTION_DOCUMENT_KIND = Utils.buildKind(
            StorageDescription.class);

    /**
     * List holding document kinds to check for deletion.
     */
    private static final Collection<String> DOCUMENT_KINDS = Arrays.asList(
            COMPUTE_STATE_DOCUMENT_KIND,
            DISK_STATE_DOCUMENT_KIND,
            COMPUTE_DESCRIPTION_DOCUMENT_KIND,
            NETWORK_STATE_DOCUMENT_KIND,
            NETWORK_INTERFACE_STATE_DOCUMENT_KIND,
            SECURITY_GROUP_STATE_DOCUMENT_KIND,
            SUBNET_STATE_DOCUMENT_KIND,
            LOAD_BALANCER_DOCUMENT_KIND,
            STORAGE_DESCRIPTION_DOCUMENT_KIND
    );

    public enum SubStage {
        /**
         * Query for resource documents, process the first query page and populate
         * endpointLinksByDocumentLinks.
         */
        QUERY_DOCUMENT_LINKS_AND_ASSOCIATED_ENDPOINT_LINKS,

        /**
         * Get the next page of documents to process. If last page, finish the flow.
         */
        GET_NEXT_PAGE_OR_FINISH,

        /**
         * Get endpointLinks of all documents and collect those which are valid.
         */
        COLLECT_VALID_ENDPOINT_LINKS,

        /**
         * Collect those documents for deletion/disassociation whose endpointLinks are not valid.
         */
        COLLECT_DOCUMENTS_TO_BE_DELETED_OR_DISASSOCIATED,

        /**
         * Delete/disassociate stale documents whose endpointLinks are not valid.
         */
        DELETE_OR_DISASSOCIATE_STALE_DOCUMENTS,

        /**
         * Deletion flow finished successfully.
         */
        FINISHED,

        /**
         * Deletion flow failed.
         */
        FAILED
    }

    public static FactoryService createFactory() {
        TaskFactoryService fs = new TaskFactoryService(EndpointResourceDeletionRequest.class) {
            @Override
            public Service createServiceInstance() throws Throwable {
                return new ResourceGroomerTaskService();
            }
        };
        fs.setPeerNodeSelectorPath(ServiceUriPaths.DEFAULT_1X_NODE_SELECTOR);
        return fs;
    }

    public ResourceGroomerTaskService() {
        super(EndpointResourceDeletionRequest.class);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    /**
     * Task state for ResourceGroomerTaskService.
     */
    public static class EndpointResourceDeletionRequest extends TaskService.TaskServiceState {
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public SubStage subStage;

        // List of tenantLinks.
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public Set<String> tenantLinks;

        // Invalid endpointLinks.
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public Set<String> validEndpointLinks;

        // List of stale documents to be deleted.
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public Set<String> documentsToBeDeletedLinks;

        // Map of stale documents to be disassociated by endpointLink.
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public Map<String, Set<String>> endpointLinksByDocumentsToBeDisassociated;

        // Map of all documentLinks with their associated endpointLinks.
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public Map<String, Set<String>> endpointLinksByDocumentLinks;

        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public String nextPageLink;

        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public int documentsDeletedCount;

        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public int documentsPatchedCount;

        public EndpointResourceDeletionRequest() {
            this.subStage = SubStage.QUERY_DOCUMENT_LINKS_AND_ASSOCIATED_ENDPOINT_LINKS;
            this.validEndpointLinks = new HashSet<>();
            this.documentsToBeDeletedLinks = new HashSet<>();
            this.endpointLinksByDocumentsToBeDisassociated = new ConcurrentHashMap<>();
            this.endpointLinksByDocumentLinks = new ConcurrentHashMap<>();
        }
    }

    @Override
    public void handleStart(Operation post) {
        EndpointResourceDeletionRequest state = post.getBody(EndpointResourceDeletionRequest.class);

        if (state.tenantLinks == null || state.tenantLinks.isEmpty()) {
            post.fail(new IllegalArgumentException("tenantLinks is required."));
            return;
        }

        logInfo("Starting stale document deletion based on [tenant = %s].",
                state.tenantLinks.iterator().next());

        super.handleStart(post);
    }

    /**
     * Overrides updateState to force update all lists with values in patch body.
     */
    @Override
    protected void updateState(EndpointResourceDeletionRequest currentTask,
            EndpointResourceDeletionRequest patchBody) {

        currentTask.validEndpointLinks = patchBody.validEndpointLinks;
        currentTask.documentsToBeDeletedLinks = patchBody.documentsToBeDeletedLinks;
        currentTask.endpointLinksByDocumentsToBeDisassociated = patchBody
                .endpointLinksByDocumentsToBeDisassociated;
        currentTask.endpointLinksByDocumentLinks = patchBody.endpointLinksByDocumentLinks;
        currentTask.nextPageLink = patchBody.nextPageLink;
        currentTask.documentsDeletedCount = patchBody.documentsDeletedCount;
        currentTask.documentsPatchedCount = patchBody.documentsPatchedCount;

        super.updateState(currentTask, patchBody);
    }

    @Override
    public void handlePatch(Operation patch) {

        EndpointResourceDeletionRequest currentTask = getState(patch);
        EndpointResourceDeletionRequest patchBody = getBody(patch);

        if (!validateTransition(patch, currentTask, patchBody)) {
            return;
        }

        updateState(currentTask, patchBody);
        patch.complete();

        switch (currentTask.taskInfo.stage) {
        case STARTED:
            handleSubStage(currentTask);
            break;
        case FINISHED:
            logInfo("Successfully finished stale document deletion.");
            break;
        case FAILED:
            logWarning("Task failed: %s", (currentTask.failureMessage == null ?
                    "No reason given" :
                    currentTask.failureMessage));
            break;
        default:
            logWarning("Unexpected subStage: %s", currentTask.taskInfo.stage);
            break;
        }
    }

    /**
     * State machine to go through task flow.
     */
    private void handleSubStage(EndpointResourceDeletionRequest task) {

        switch (task.subStage) {
        case QUERY_DOCUMENT_LINKS_AND_ASSOCIATED_ENDPOINT_LINKS:
            queryDocumentLinksAndAssociatedEndpointLinks(task,
                    SubStage.COLLECT_VALID_ENDPOINT_LINKS);
            break;
        case GET_NEXT_PAGE_OR_FINISH:
            getNextPageOrFinish(task, SubStage.COLLECT_VALID_ENDPOINT_LINKS);
            break;
        case COLLECT_VALID_ENDPOINT_LINKS:
            collectValidEndpoints(task, SubStage.COLLECT_DOCUMENTS_TO_BE_DELETED_OR_DISASSOCIATED);
            break;
        case COLLECT_DOCUMENTS_TO_BE_DELETED_OR_DISASSOCIATED:
            collectDocumentsToBeDeletedAndDisassociated(task, SubStage.DELETE_OR_DISASSOCIATE_STALE_DOCUMENTS);
            break;
        case DELETE_OR_DISASSOCIATE_STALE_DOCUMENTS:
            deleteStaleDocuments(task, SubStage.GET_NEXT_PAGE_OR_FINISH);
            break;
        case FINISHED:
            setStats(task);
            sendSelfFinishedPatch(task);
            break;
        case FAILED:
            setStats(task);
            sendSelfFailurePatch(task, task.failureMessage == null
                    ? "StaleEndpointDocumentDeletionTask failed."
                    : task.failureMessage);
            break;
        default:
            task.subStage = SubStage.FAILED;
            sendSelfFailurePatch(task, "Unknown subStage encountered: " + task.subStage);
            break;
        }
    }

    /**
     * Queries resource documents and their associated endpointLinks for given context and starts
     * the document deletion flow for first page.
     */
    private void queryDocumentLinksAndAssociatedEndpointLinks(EndpointResourceDeletionRequest task,
            SubStage next) {

        QueryTask queryTask = buildQueryTask(task);

        Operation.createPost(this.getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS)
                .setReferer(this.getUri())
                .setBody(queryTask)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        task.failureMessage = e.getMessage();
                        task.subStage = SubStage.FAILED;
                        sendSelfPatch(task);
                        return;
                    }

                    QueryTask response = o.getBody(QueryTask.class);

                    if (response.results != null && response.results.documents != null) {
                        populateEndpointLinksByDocumentLinks(response.results.documents,
                                task.endpointLinksByDocumentLinks);
                    }

                    task.nextPageLink = null;

                    if (response.results != null) {
                        task.nextPageLink = response.results.nextPageLink;
                    }

                    task.subStage = next;
                    sendSelfPatch(task);
                }).sendWith(this);
    }

    /**
     * Gets the next page and starts stale document deletion for it. Finishes if last page is
     * already processed.
     */
    private void getNextPageOrFinish(EndpointResourceDeletionRequest task, SubStage next) {

        if (task.nextPageLink == null) {
            task.subStage = SubStage.FINISHED;
            sendSelfPatch(task);
            return;
        }

        task.endpointLinksByDocumentLinks.clear();
        task.validEndpointLinks.clear();
        task.documentsToBeDeletedLinks.clear();
        task.endpointLinksByDocumentsToBeDisassociated.clear();

        Operation.createGet(this.getHost(), task.nextPageLink)
                .setReferer(this.getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        task.failureMessage = e.getMessage();
                        task.subStage = SubStage.FAILED;
                        sendSelfPatch(task);
                        return;
                    }

                    QueryTask response = o.getBody(QueryTask.class);

                    if (response.results != null && response.results.documents != null) {
                        populateEndpointLinksByDocumentLinks(response.results.documents,
                                task.endpointLinksByDocumentLinks);
                    }

                    task.nextPageLink = null;

                    if (response.results != null) {
                        task.nextPageLink = response.results.nextPageLink;

                    }

                    task.subStage = next;
                    sendSelfPatch(task);
                }).sendWith(this);
    }

    /**
     * Get endpoints for all documents and collect valid endpointLinks by querying for all links.
     */
    private void collectValidEndpoints(EndpointResourceDeletionRequest task,
            SubStage next) {

        // Collect endpointLinks for all documents.
        Set<String> currentEndpointLinks = new HashSet<>();
        task.endpointLinksByDocumentLinks.values().stream()
                .forEach(links -> {
                    currentEndpointLinks.addAll(links);
                });

        if (currentEndpointLinks.isEmpty()) {
            task.subStage = next;
            sendSelfPatch(task);
            return;
        }

        QueryTask queryTask = buildQueryTask(task, true, currentEndpointLinks);

        Operation.createPost(this.getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS)
                .setBody(queryTask)
                .setReferer(this.getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        task.failureMessage = e.getMessage();
                        task.subStage = SubStage.FAILED;
                        sendSelfPatch(task);
                        return;
                    }

                    QueryTask response = o.getBody(QueryTask.class);

                    if (response.results != null && response.results.documentLinks != null) {
                        task.validEndpointLinks.addAll(response.results.documentLinks);
                    }

                    if (response.results != null && response.results.nextPageLink != null) {
                        processValidEndpointLinksQueryPage(task, response.results.nextPageLink,
                                next);
                        return;
                    }

                    task.subStage = next;
                    sendSelfPatch(task);
                }).sendWith(this);
    }

    /**
     * Collects pages of valid endpoints.
     */
    private void processValidEndpointLinksQueryPage(EndpointResourceDeletionRequest task,
            String nextPageLink, SubStage next) {

        if (nextPageLink == null) {
            task.subStage = next;
            sendSelfPatch(task);
            return;
        }

        Operation.createGet(this.getHost(), nextPageLink)
                .setReferer(this.getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        task.failureMessage = e.getMessage();
                        task.subStage = SubStage.FAILED;
                        sendSelfPatch(task);
                        return;
                    }

                    QueryTask response = o.getBody(QueryTask.class);

                    if (response.results != null && response.results.documentLinks != null) {
                        task.validEndpointLinks.addAll(response.results.documentLinks);
                    }

                    if (response.results != null && response.results.nextPageLink != null) {
                        processValidEndpointLinksQueryPage(task, response.results.nextPageLink, next);
                        return;
                    }

                    task.subStage = next;
                    sendSelfPatch(task);
                }).sendWith(this);
    }

    /**
     * Collect all those documents whose endpointLink is not valid for deletion.
     */
    private void collectDocumentsToBeDeletedAndDisassociated(EndpointResourceDeletionRequest task,
            SubStage next) {

        task.endpointLinksByDocumentLinks.entrySet().stream()
                .forEach(entry -> {
                    // If endpointLinks set for a documentLink is empty, that means there are no
                    // endpoints associated with it, so we can directly collect it for deletion.
                    // Otherwise, find the invalid endpointLinks for a document by removing all
                    // valid endpointLinks.
                    // If all links in a documents endpointLinks are invalid, we will disassociate
                    // all of them and later be able to delete that document. So instead, directly
                    // collect that document for deletion.
                    // Otherwise, collect the document for disassociation if one or some of it's
                    // endpointLinks are invalid.
                    if (entry.getValue().isEmpty()) {
                        task.documentsToBeDeletedLinks.add(entry.getKey());
                    } else {
                        Set<String> endpointLinks = new HashSet<>(entry.getValue());

                        endpointLinks.removeAll(task.validEndpointLinks);

                        if (endpointLinks.size() == entry.getValue().size()) {
                            task.documentsToBeDeletedLinks.add(entry.getKey());
                        } else if (!endpointLinks.isEmpty()) {
                            task.endpointLinksByDocumentsToBeDisassociated
                                    .put(entry.getKey(), endpointLinks);
                        }
                    }
                });

        task.subStage = next;
        sendSelfPatch(task);
    }

    /**
     * Deletes documents that have no endpointLinks associated with them.
     * Disassociate documents if they have invalid endpointLinks by sending a collection update
     * patch.
     */
    private void deleteStaleDocuments(EndpointResourceDeletionRequest task,
            SubStage next) {

        if (task.documentsToBeDeletedLinks.isEmpty()
                && task.endpointLinksByDocumentsToBeDisassociated.isEmpty()) {
            task.subStage = next;
            sendSelfPatch(task);
            return;
        }

        List<Operation> deletePatchOperations = new ArrayList<>();

        task.documentsToBeDeletedLinks.stream()
                .forEach(documentLink -> {
                    deletePatchOperations.add(Operation.createDelete(this.getHost(), documentLink)
                            .setReferer(this.getHost().getUri()));
                });

        task.endpointLinksByDocumentsToBeDisassociated.entrySet().stream()
                .forEach(entry -> {
                    Map<String, Collection<Object>> itemsToRemove = Collections.singletonMap
                            (ResourceState.FIELD_NAME_ENDPOINT_LINKS, new ArrayList<>(entry.getValue()));
                    Map<String, Collection<Object>> itemsToAdd = Collections.singletonMap
                            (ResourceState.FIELD_NAME_ENDPOINT_LINKS, Collections.EMPTY_LIST);
                    ServiceStateCollectionUpdateRequest request
                            = ServiceStateCollectionUpdateRequest.create(itemsToAdd, itemsToRemove);
                    deletePatchOperations.add(Operation.createPatch(this.getHost(), entry.getKey())
                            .setBody(request)
                            .setReferer(this.getUri()));
                    logInfo("Removing endpointLinks: %s from resource: %s",
                            entry.getValue(), entry.getKey());
                });

        logInfo("Deleting stale documents that have invalid endpointLinks. [documentCount=%s]",
                task.documentsToBeDeletedLinks.size());
        logInfo("Patching stale documents that have invalid endpointLinks. [documentCount=%s]",
                task.endpointLinksByDocumentsToBeDisassociated.size());

        task.documentsDeletedCount += task.documentsToBeDeletedLinks.size();
        task.documentsPatchedCount += task.endpointLinksByDocumentsToBeDisassociated.size();

        OperationJoin.create(deletePatchOperations)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        task.failureMessage = e.get(0).getMessage();
                        task.subStage = SubStage.FAILED;
                        sendSelfPatch(task);
                        return;
                    }

                    task.subStage = next;
                    sendSelfPatch(task);
                }).sendWith(this, OPERATION_BATCH_SIZE);
    }

    /**
     * Helper for building resource document query.
     */
    private static QueryTask buildQueryTask(EndpointResourceDeletionRequest task) {
        return buildQueryTask(task, false, null);
    }

    /**
     * Builds QueryTask for resource document query and endpoint query.
     * If endpointLink and tenantLinks are specified, adds clauses for that and creates a
     * QueryTask to be executed in proper context.
     */
    private static QueryTask buildQueryTask(EndpointResourceDeletionRequest task,
            boolean isEndpointQuery, Set<String> currentEndpointLinks) {

        Query query;

        if (isEndpointQuery) {
            query = Query.Builder.create()
                    .addInClause(ServiceDocument.FIELD_NAME_SELF_LINK, currentEndpointLinks)
                    .addInCollectionItemClause(ResourceState.FIELD_NAME_TENANT_LINKS, task.tenantLinks)
                    .build();
        } else {
            query = Query.Builder.create()
                    .addInClause(ServiceDocument.FIELD_NAME_KIND, DOCUMENT_KINDS)
                    .addInCollectionItemClause(ResourceState.FIELD_NAME_TENANT_LINKS, task.tenantLinks)
                    .build();
        }

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .setQuery(query)
                .setResultLimit(QUERY_RESULT_LIMIT)
                .build();

        if (!isEndpointQuery) {
            queryTask.querySpec.options.add(QueryOption.EXPAND_CONTENT);
        }

        queryTask.tenantLinks = new ArrayList<>(task.tenantLinks);

        return queryTask;
    }

    /**
     * Helper for creating corresponding object and parsing the response for given documentKind
     * to store selfLink and endpointLink.
     */
    private static void populateEndpointLinksByDocumentLinks(Map<String, Object> documents,
            Map<String, Set<String>> endpointLinksByDocumentLinks) {

        documents.values().stream()
                .forEach(document -> {
                    ServiceDocument doc = Utils.fromJson(document, ServiceDocument.class);
                    Set<String> endpointLinks = new HashSet<>();

                    if (doc.documentKind.equals(COMPUTE_STATE_DOCUMENT_KIND)) {
                        ComputeState state = Utils.fromJson(document, ComputeState.class);
                        if (state.endpointLinks != null) {
                            endpointLinks.addAll(state.endpointLinks);
                        }
                        endpointLinksByDocumentLinks.put(state.documentSelfLink, endpointLinks);
                    } else if (doc.documentKind.equals(DISK_STATE_DOCUMENT_KIND)) {
                        DiskState state = Utils.fromJson(document, DiskState.class);
                        if (state.endpointLinks != null) {
                            endpointLinks.addAll(state.endpointLinks);
                        }
                        endpointLinksByDocumentLinks.put(state.documentSelfLink, endpointLinks);
                    } else if (doc.documentKind.equals(COMPUTE_DESCRIPTION_DOCUMENT_KIND)) {
                        ComputeDescription state = Utils.fromJson(document, ComputeDescription.class);
                        if (state.endpointLinks != null) {
                            endpointLinks.addAll(state.endpointLinks);
                        }
                        endpointLinksByDocumentLinks.put(state.documentSelfLink, endpointLinks);
                    } else if (doc.documentKind.equals(NETWORK_STATE_DOCUMENT_KIND)) {
                        NetworkState state = Utils.fromJson(document, NetworkState.class);
                        if (state.endpointLinks != null) {
                            endpointLinks.addAll(state.endpointLinks);
                        }
                        endpointLinksByDocumentLinks.put(state.documentSelfLink, endpointLinks);
                    } else if (doc.documentKind.equals(NETWORK_INTERFACE_STATE_DOCUMENT_KIND)) {
                        NetworkInterfaceState state = Utils.fromJson(document, NetworkInterfaceState.class);
                        if (state.endpointLinks != null) {
                            endpointLinks.addAll(state.endpointLinks);
                        }
                        endpointLinksByDocumentLinks.put(state.documentSelfLink, endpointLinks);
                    } else if (doc.documentKind.equals(SECURITY_GROUP_STATE_DOCUMENT_KIND)) {
                        SecurityGroupState state = Utils.fromJson(document, SecurityGroupState.class);
                        if (state.endpointLinks != null) {
                            endpointLinks.addAll(state.endpointLinks);
                        }
                        endpointLinksByDocumentLinks.put(state.documentSelfLink, endpointLinks);
                    } else if (doc.documentKind.equals(SUBNET_STATE_DOCUMENT_KIND)) {
                        SubnetState state = Utils.fromJson(document, SubnetState.class);
                        if (state.endpointLinks != null) {
                            endpointLinks.addAll(state.endpointLinks);
                        }
                        endpointLinksByDocumentLinks.put(state.documentSelfLink, endpointLinks);
                    }
                });
    }

    /**
     * Set service stats, number of documents deleted and number of documents patched.
     */
    private void setStats(EndpointResourceDeletionRequest task) {
        PhotonModelUtils.setStat(this, STAT_NAME_DOCUMENTS_DELETED, STAT_UNIT_COUNT,
                task.documentsDeletedCount);
        PhotonModelUtils.setStat(this, STAT_NAME_DOCUMENTS_PATCHED, STAT_UNIT_COUNT,
                task.documentsPatchedCount);
    }
}