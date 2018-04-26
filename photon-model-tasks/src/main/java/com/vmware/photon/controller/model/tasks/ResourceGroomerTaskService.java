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

import static com.vmware.photon.controller.model.constants.PhotonModelConstants.CUSTOM_PROP_ENDPOINT_LINK;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.SOURCE_TASK_LINK;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ImageService.ImageState;
import com.vmware.photon.controller.model.resources.LoadBalancerService.LoadBalancerState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceGroupService;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.ResourceUtils;
import com.vmware.photon.controller.model.resources.RouterService.RouterState;
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
import com.vmware.xenon.common.ServiceStateMapUpdateRequest;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskFactoryService;
import com.vmware.xenon.services.common.TaskService;

/**
 * Groomer task for disassociating documents that are associated with invalid endpointLinks and
 * deleting all documents that have no endpoint associated with them.
 *
 * The task can be run periodically using ScheduledTaskService. The task requires a tenantLink
 * to be specified. Optionally, it can also be in context of a single endpoint, when endpointLink is
 * specified.
 *
 * The task is invoked by EndpointRemovalTaskService for a single endpoint to disassociate/delete
 * documents which were associated with the endpoint being deleted.
 */
public class ResourceGroomerTaskService
        extends TaskService<EndpointResourceDeletionRequest> {

    public static final String FACTORY_LINK = UriPaths.PROVISIONING + "/stale-document-deletion";

    public static final int QUERY_RESULT_LIMIT = Math.min(1000, QueryUtils.MAX_RESULT_LIMIT);
    public static final int OPERATION_BATCH_SIZE = PhotonModelConstants.OPERATION_BATCH_SIZE;

    // Set max document size to 1 MB.
    public static final int MAX_SERIALIZED_STATE_SIZE_BYTES = 1 * 1024 * 1024;

    public static final String PATH_SEPARATOR = "/";
    public static final String EMPTY_STRING = "";
    public static final String STAT_NAME_DOCUMENTS_DELETED = "documentsDeletedCount";
    public static final String STAT_NAME_ENDPOINT_LINKS_PATCHED = "endpointLinksPatchedCount";
    public static final String STAT_NAME_ENDPOINT_LINK_PATCHED = "endpointLinkPatchedCount";
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
    public static final String RESOURCE_GROUP_DOCUMENT_KIND = Utils.buildKind(ResourceGroupState.class);
    public static final String IMAGE_STATE_KIND = Utils.buildKind(ImageState.class);
    public static final String ROUTER_STATE_KIND = Utils.buildKind(RouterState.class);
    public static final String AUTH_CREDENTIALS_SERVICE_STATE_KIND = Utils.buildKind(
            AuthCredentialsServiceState.class);

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
            STORAGE_DESCRIPTION_DOCUMENT_KIND,
            RESOURCE_GROUP_DOCUMENT_KIND,
            IMAGE_STATE_KIND,
            ROUTER_STATE_KIND,
            AUTH_CREDENTIALS_SERVICE_STATE_KIND
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
         * Get endpointLinks of all documents and collect those which are deleted.
         */
        COLLECT_DELETED_ENDPOINT_LINKS,

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


        // Valid endpointLinks.
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public Set<String> deletedEndpointLinks;

        // List of stale documents to be deleted.
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public Set<String> documentsToBeDeletedLinks;

        // Map of endpointLinks by stale document link.
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public Map<String, Set<String>> endpointLinksByDocumentsToBeDisassociated;

        // Map of endpointLink to be PATCHed by document link.
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public Map<String, String> endpointLinkToBePatchedByDocumentLinks;

        // Map of all documentLinks with their associated endpointLinks.
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public Map<String, Set<String>> endpointLinksByDocumentLinks;

        // Map of all documentLinks with their associated endpointLink.
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public Map<String, String> endpointLinkByDocumentLinks;

        // Next page link of documents query.
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public String nextPageLink;

        // Endpoint link is passed when caller wants the groomer task to run
        // only in context of a single endpoint i.e. only the documents that contain this
        // endpoint are disassociated/deleted.
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public String endpointLink;

        // Total number of documents deleted by the task.
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public int documentsDeletedCount;

        // Total number of endpointLinks patched by the task.
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public int endpointLinksPatchedCount;

        // Total number of endpointLink patched by the task.
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public int endpointLinkPatchedCount;

        public EndpointResourceDeletionRequest() {
            this.subStage = SubStage.QUERY_DOCUMENT_LINKS_AND_ASSOCIATED_ENDPOINT_LINKS;
            this.deletedEndpointLinks = new HashSet<>();
            this.documentsToBeDeletedLinks = new HashSet<>();
            this.endpointLinkToBePatchedByDocumentLinks = new ConcurrentHashMap<>();
            this.endpointLinksByDocumentsToBeDisassociated = new ConcurrentHashMap<>();
            this.endpointLinksByDocumentLinks = new ConcurrentHashMap<>();
            this.endpointLinkByDocumentLinks = new ConcurrentHashMap<>();
        }
    }

    @Override
    public void handleStart(Operation post) {
        EndpointResourceDeletionRequest state = post.getBody(EndpointResourceDeletionRequest.class);

        if (state.tenantLinks == null || state.tenantLinks.isEmpty()) {
            post.fail(new IllegalArgumentException("tenantLinks is required."));
            return;
        }

        if (state.endpointLink == null) {
            logInfo("Starting resource groomer task for [tenant = %s].",
                    state.tenantLinks.iterator().next());
        } else if (state.endpointLink != null
                && (state.endpointLink.isEmpty()
                || !state.endpointLink.startsWith(EndpointService.FACTORY_LINK))) {
            post.fail(new IllegalArgumentException(String.format("endpointLink (%s) is malformed.",
                    state.endpointLink)));
        } else {
            logInfo("Starting resource groomer task for [endpoint = %s].",
                    state.endpointLink);
        }

        super.handleStart(post);
    }

    /**
     * Overrides updateState to force update all lists with values in patch body.
     */
    @Override
    protected void updateState(EndpointResourceDeletionRequest currentTask,
            EndpointResourceDeletionRequest patchBody) {

        currentTask.deletedEndpointLinks = patchBody.deletedEndpointLinks;
        currentTask.documentsToBeDeletedLinks = patchBody.documentsToBeDeletedLinks;
        currentTask.endpointLinkToBePatchedByDocumentLinks = patchBody
                .endpointLinkToBePatchedByDocumentLinks;
        currentTask.endpointLinksByDocumentsToBeDisassociated = patchBody
                .endpointLinksByDocumentsToBeDisassociated;
        currentTask.endpointLinksByDocumentLinks = patchBody.endpointLinksByDocumentLinks;
        currentTask.endpointLinkByDocumentLinks = patchBody.endpointLinkByDocumentLinks;
        currentTask.nextPageLink = patchBody.nextPageLink;
        currentTask.documentsDeletedCount = patchBody.documentsDeletedCount;
        currentTask.endpointLinksPatchedCount = patchBody.endpointLinksPatchedCount;
        currentTask.endpointLinkPatchedCount = patchBody.endpointLinkPatchedCount;

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
                    SubStage.COLLECT_DELETED_ENDPOINT_LINKS);
            break;
        case GET_NEXT_PAGE_OR_FINISH:
            getNextPageOrFinish(task, SubStage.COLLECT_DELETED_ENDPOINT_LINKS);
            break;
        case COLLECT_DELETED_ENDPOINT_LINKS:
            collectDeletedEndpoints(task, SubStage.COLLECT_DOCUMENTS_TO_BE_DELETED_OR_DISASSOCIATED);
            break;
        case COLLECT_DOCUMENTS_TO_BE_DELETED_OR_DISASSOCIATED:
            collectDocumentsToBeDeletedAndDisassociated(task, SubStage.DELETE_OR_DISASSOCIATE_STALE_DOCUMENTS);
            break;
        case DELETE_OR_DISASSOCIATE_STALE_DOCUMENTS:
            deleteDisassociatePatchStaleDocuments(task, SubStage.GET_NEXT_PAGE_OR_FINISH);
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

        QueryUtils.startInventoryQueryTask(this, queryTask)
                .whenComplete((response, e) -> {
                    if (e != null) {
                        task.failureMessage = e.getMessage();
                        task.subStage = SubStage.FAILED;
                        sendSelfPatch(task);
                        return;
                    }

                    if (response.results != null && response.results.documents != null) {
                        populateEndpointLinksByDocumentLinks(response.results.documents,
                                task.endpointLinksByDocumentLinks, task.endpointLinkByDocumentLinks);
                    }

                    task.nextPageLink = null;

                    if (response.results != null) {
                        task.nextPageLink = response.results.nextPageLink;
                    }

                    task.subStage = next;
                    sendSelfPatch(task);
                });
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
        task.endpointLinkByDocumentLinks.clear();
        task.deletedEndpointLinks.clear();
        task.documentsToBeDeletedLinks.clear();
        task.endpointLinkToBePatchedByDocumentLinks.clear();
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
                                task.endpointLinksByDocumentLinks, task.endpointLinkByDocumentLinks);
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
     * Get endpointLinks for all documents and collect deleted endpointLinks by querying for all links
     * with INCLUDE_DELETED.
     */
    private void collectDeletedEndpoints(EndpointResourceDeletionRequest task,
            SubStage next) {

        QueryTask queryTask;

        // If endpointLink is specified, only query for that endpoint. Otherwise collect all endpointLinks
        // from all documents and query for those.
        if (isEndpointSpecified(task)) {
            queryTask = buildQueryTask(task, true, Collections.singleton(task.endpointLink));
        } else {
            Set<String> currentEndpointLinks = new HashSet<>();
            task.endpointLinksByDocumentLinks.values().stream()
                    .forEach(links -> {
                        currentEndpointLinks.addAll(links);
                    });
            currentEndpointLinks.addAll(task.endpointLinkByDocumentLinks.values());

            if (currentEndpointLinks.isEmpty()) {
                task.subStage = next;
                sendSelfPatch(task);
                return;
            }
            queryTask = buildQueryTask(task, true, currentEndpointLinks);
        }

        QueryUtils.startInventoryQueryTask(this, queryTask)
                .whenComplete((response, e) -> {
                    if (e != null) {
                        task.failureMessage = e.getMessage();
                        task.subStage = SubStage.FAILED;
                        sendSelfPatch(task);
                        return;
                    }

                    if (response.results != null && response.results.documents != null) {
                        response.results.documents.values().stream()
                                .forEach(obj -> {
                                    EndpointState state = Utils.fromJson(obj, EndpointState.class);
                                    if (state.documentUpdateAction.equals(Action.DELETE.name())) {
                                        task.deletedEndpointLinks.add(state.documentSelfLink);
                                    }
                                });
                    }

                    if (response.results != null && response.results.nextPageLink != null) {
                        processDeletedEndpointLinksQueryPage(task, response.results.nextPageLink,
                                next);
                        return;
                    }

                    task.subStage = next;
                    sendSelfPatch(task);
                });
    }

    /**
     * Collects pages of deleted endpoints.
     */
    private void processDeletedEndpointLinksQueryPage(EndpointResourceDeletionRequest task,
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

                    if (response.results != null && response.results.documents != null) {
                        response.results.documents.values().stream()
                                .forEach(obj -> {
                                    EndpointState state = Utils.fromJson(obj, EndpointState.class);
                                    if (state.documentUpdateAction.equals(Action.DELETE.name())) {
                                        task.deletedEndpointLinks.add(state.documentSelfLink);
                                    }
                                });
                    }

                    if (response.results != null && response.results.nextPageLink != null) {
                        processDeletedEndpointLinksQueryPage(task, response.results.nextPageLink, next);
                        return;
                    }

                    task.subStage = next;
                    sendSelfPatch(task);
                }).sendWith(this);
    }

    /**
     * Collect documents for deletion/disassociation/patching.
     */
    private void collectDocumentsToBeDeletedAndDisassociated(EndpointResourceDeletionRequest task,
            SubStage next) {
        if (!isEndpointSpecified(task)) {
            collectDocumentsForAllEndpoints(task, next);
            return;
        }

        if (task.deletedEndpointLinks.contains(task.endpointLink)) {
            collectDocumentsForSpecifiedEndpoint(task, next);
        } else {
            task.failureMessage = "Deletion/Disassociation of documents for valid "
                    + "endpoints is not supported.";
            task.subStage = SubStage.FAILED;
            sendSelfPatch(task);
        }

    }

    /**
     * Collects all documents whose endpointLink is invalid for deletion/disassociation/patching.
     */
    private void collectDocumentsForAllEndpoints(EndpointResourceDeletionRequest task,
            SubStage next) {

        task.endpointLinksByDocumentLinks.entrySet().stream()
                .forEach(entry -> {
                    // If endpointLinks set for a documentLink is empty, check the endpointLink for
                    // that document. If the document has a deleted endpointLink,
                    // delete the document.
                    // Otherwise, if endpointLinks set is not empty, find the valid endpointLinks
                    // for a document by removing all deleted endpointLinks from the set.
                    // If all links in a documents endpointLinks set are deleted, we will disassociate
                    // all of them and later be able to delete that document. So instead, directly
                    // collect that document for deletion.
                    // Otherwise, collect the document for disassociation if one or some of it's
                    // endpointLinks are deleted. At the same time, check if the endpointLink for
                    // this document exists and is valid. If it is not, then we PATCH the document
                    // with one of the remaining valid endpointLinks.
                    Set<String> existingEndpointLinks = entry.getValue();
                    if (existingEndpointLinks.isEmpty()) {
                        String endpointLink = task.endpointLinkByDocumentLinks.get(entry.getKey());
                        if (endpointLink == null) {
                            task.documentsToBeDeletedLinks.add(entry.getKey());
                        } else if (endpointLink.equals(EMPTY_STRING)
                                || !endpointLink.equals(EMPTY_STRING)
                                && task.deletedEndpointLinks.contains(endpointLink)) {
                            task.documentsToBeDeletedLinks.add(entry.getKey());
                        }
                    } else {
                        Set<String> endpointLinks = new HashSet<>(existingEndpointLinks);
                        endpointLinks.removeIf(link -> task.deletedEndpointLinks.contains(link));
                        if (endpointLinks.isEmpty()) {
                            task.documentsToBeDeletedLinks.add(entry.getKey());
                        } else {
                            existingEndpointLinks.removeAll(endpointLinks);
                            if (!existingEndpointLinks.isEmpty()) {
                                task.endpointLinksByDocumentsToBeDisassociated
                                        .put(entry.getKey(), existingEndpointLinks);
                            }
                            String endpointLink = task.endpointLinkByDocumentLinks
                                    .get(entry.getKey());
                            if (endpointLink != null && !endpointLinks.isEmpty() &&
                                    !endpointLinks.contains(endpointLink)) {
                                List<String> sortedEndpointLinks = new ArrayList<>(endpointLinks);
                                Collections.sort(sortedEndpointLinks);
                                task.endpointLinkToBePatchedByDocumentLinks
                                        .put(entry.getKey(), sortedEndpointLinks.get(0));
                            }
                        }
                    }
                });

        task.subStage = next;
        sendSelfPatch(task);
    }

    /**
     * Collects documents for for deletion/disassociation/patching only for the endpoint being
     * deleted specified in the task and ignores other endpoints, even if invalid.
     */
    private void collectDocumentsForSpecifiedEndpoint(EndpointResourceDeletionRequest task,
            SubStage next) {
        task.endpointLinksByDocumentLinks.entrySet().stream()
                .forEach(entry -> {
                    // If endpointLinks set for a documentLink is empty, check the endpointLink for
                    // that document. If the document has a null/empty or endpointLink currently
                    // being deleted, delete the document.
                    // Otherwise, if endpointLinks set is not empty, and contains only the endpointLink
                    // being deleted, we will disassociate and eventually delete that document, so instead
                    // directly mark it for deletion.
                    // Otherwise, disassociate the endpointLink being deleted.
                    // If the endpointLink for the document being deleted is the endpointLink being
                    // deleted, replace it with next valid endpointLink from endpointLinks.
                    Set<String> existingEndpointLinks = entry.getValue();
                    if (existingEndpointLinks.isEmpty()) {
                        String endpointLink = task.endpointLinkByDocumentLinks.get(entry.getKey());
                        if (endpointLink == null) {
                            task.documentsToBeDeletedLinks.add(entry.getKey());
                        } else if (endpointLink.equals(EMPTY_STRING)
                                || endpointLink.equals(task.endpointLink)) {
                            task.documentsToBeDeletedLinks.add(entry.getKey());
                        }
                    } else if (existingEndpointLinks.size() == 1
                            && existingEndpointLinks.contains(task.endpointLink)) {
                        task.documentsToBeDeletedLinks.add(entry.getKey());
                    } else {
                        task.endpointLinksByDocumentsToBeDisassociated.put(entry.getKey(),
                                Collections.singleton(task.endpointLink));
                        if (task.endpointLinkByDocumentLinks.get(entry.getKey())
                                .equals(task.endpointLink)) {
                            Set<String> endpointLinks = task.endpointLinksByDocumentLinks
                                    .get(entry.getKey());
                            endpointLinks.remove(task.endpointLink);
                            if (!endpointLinks.isEmpty()) {
                                List<String> sortedEndpointLinks = new ArrayList<>(endpointLinks);
                                Collections.sort(sortedEndpointLinks);
                                task.endpointLinkToBePatchedByDocumentLinks
                                        .put(entry.getKey(), sortedEndpointLinks.get(0));
                            }
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
    private void deleteDisassociatePatchStaleDocuments(EndpointResourceDeletionRequest task,
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
                    ServiceStateCollectionUpdateRequest request
                            = ServiceStateCollectionUpdateRequest.create(null, itemsToRemove);
                    deletePatchOperations.add(Operation.createPatch(this.getHost(), entry.getKey())
                            .setBody(request)
                            .setReferer(this.getUri()));
                    logInfo("Removing endpointLinks: %s from resource: %s",
                            entry.getValue(), entry.getKey());
                });

        task.endpointLinkToBePatchedByDocumentLinks.entrySet().stream()
                .forEach(entry -> {
                    if (entry.getKey().startsWith(ResourceGroupService.FACTORY_LINK) ||
                            entry.getKey().startsWith(AuthCredentialsService.FACTORY_LINK)) {
                        deletePatchOperations.add(createResourceGroupEndpointLinkPatchOp(entry.getKey(),
                                entry.getValue()));
                    } else {
                        deletePatchOperations.add(createEndpointLinkPatchOp(entry.getKey(), entry.getValue()));
                    }
                    logInfo("Changing endpointLink to %s from resource: %s",
                            entry.getValue(), entry.getKey());
                });

        logInfo("Deleting stale documents that have invalid endpointLinks. [documentCount=%s]",
                task.documentsToBeDeletedLinks.size());
        logInfo("Patching stale documents that have invalid endpointLinks list. [documentCount=%s]",
                task.endpointLinksByDocumentsToBeDisassociated.size());
        logInfo("Patching stale documents that have invalid endpointLink. [documentCount=%s]",
                task.endpointLinkToBePatchedByDocumentLinks.size());

        task.documentsDeletedCount += task.documentsToBeDeletedLinks.size();
        task.endpointLinksPatchedCount += task.endpointLinksByDocumentsToBeDisassociated.size();
        task.endpointLinkPatchedCount += task.endpointLinkToBePatchedByDocumentLinks.size();

        OperationJoin.create(deletePatchOperations)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        task.failureMessage = e.values().iterator().next().getMessage();
                        task.subStage = SubStage.FAILED;
                        sendSelfFailurePatch(task, task.failureMessage);
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
     * If endpointLink is specified, adds clauses for that and creates a
     * QueryTask to be executed in tenant's context.
     */
    private static QueryTask buildQueryTask(EndpointResourceDeletionRequest task,
            boolean isEndpointQuery, Set<String> currentEndpointLinks) {

        Query query;

        if (isEndpointQuery) {
            query = Query.Builder.create()
                    .addInClause(ServiceDocument.FIELD_NAME_SELF_LINK, currentEndpointLinks)
                    .addFieldClause(ServiceDocument.FIELD_NAME_UPDATE_ACTION,
                            Action.DELETE.name())
                    .addInCollectionItemClause(ResourceState.FIELD_NAME_TENANT_LINKS, task.tenantLinks)
                    .build();
        } else {
            query = Query.Builder.create()
                    .addInClause(ServiceDocument.FIELD_NAME_KIND, DOCUMENT_KINDS)
                    .addInCollectionItemClause(ResourceState.FIELD_NAME_TENANT_LINKS, task.tenantLinks)
                    .build();

            if (isEndpointSpecified(task)) {
                Query endpointSpecificClauses = Query.Builder.create(Occurance.MUST_OCCUR)
                        .addFieldClause(ResourceState.FIELD_NAME_ENDPOINT_LINK, task.endpointLink,
                                Occurance.SHOULD_OCCUR)
                        .addCollectionItemClause(ResourceState.FIELD_NAME_ENDPOINT_LINKS,
                                task.endpointLink, Occurance.SHOULD_OCCUR)
                        .build();

                query.addBooleanClause(endpointSpecificClauses);
            }
        }

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .setQuery(query)
                .addOption(QueryOption.EXPAND_CONTENT)
                .setResultLimit(QUERY_RESULT_LIMIT)
                .build();

        if (isEndpointQuery) {
            queryTask.querySpec.options.add(QueryOption.INCLUDE_DELETED);
            queryTask.querySpec.options.add(QueryOption.BROADCAST);
            queryTask.querySpec.options.add(QueryOption.OWNER_SELECTION);
        }

        queryTask.tenantLinks = new ArrayList<>(task.tenantLinks);

        return queryTask;
    }

    /**
     * Helper for creating corresponding object and parsing the response for given documentKind
     * to store selfLink and endpointLink.
     */
    private static void populateEndpointLinksByDocumentLinks(Map<String, Object> documents,
            Map<String, Set<String>> endpointLinksByDocumentLinks,
            Map<String, String> endpointLinkByDocumentLinks) {

        for (Object document : documents.values()) {
            ServiceDocument doc = Utils.fromJson(document, ServiceDocument.class);
            Set<String> endpointLinks = new HashSet<>();

            if (doc.documentKind.equals(COMPUTE_STATE_DOCUMENT_KIND)) {
                ComputeState state = Utils.fromJson(document, ComputeState.class);
                if (state.endpointLinks != null) {
                    state.endpointLinks.remove(null);
                    endpointLinks.addAll(state.endpointLinks);
                }
                endpointLinksByDocumentLinks.put(state.documentSelfLink, endpointLinks);
                endpointLinkByDocumentLinks.put(state.documentSelfLink,
                        state.endpointLink != null ? state.endpointLink : EMPTY_STRING);
            } else if (doc.documentKind.equals(DISK_STATE_DOCUMENT_KIND)) {
                DiskState state = Utils.fromJson(document, DiskState.class);
                if (state.customProperties != null && state.customProperties.containsKey(
                        ResourceUtils.CUSTOM_PROP_NO_ENDPOINT)) {
                    // skip resources that have never been attached to a particular endpoint
                    continue;
                }
                if (state.endpointLinks != null) {
                    state.endpointLinks.remove(null);
                    endpointLinks.addAll(state.endpointLinks);
                }
                endpointLinksByDocumentLinks.put(state.documentSelfLink, endpointLinks);
                endpointLinkByDocumentLinks.put(state.documentSelfLink,
                        state.endpointLink != null ? state.endpointLink : EMPTY_STRING);
            } else if (doc.documentKind.equals(COMPUTE_DESCRIPTION_DOCUMENT_KIND)) {
                ComputeDescription state = Utils.fromJson(document, ComputeDescription.class);
                // only deleting discovered resources
                if (!(state.customProperties != null &&
                        (ResourceEnumerationTaskService.FACTORY_LINK.equals(
                                state.customProperties.get(SOURCE_TASK_LINK)) ||
                        EndpointAllocationTaskService.FACTORY_LINK.equals(
                                state.customProperties.get(SOURCE_TASK_LINK))))) {
                    continue;
                }
                if (state.endpointLinks != null) {
                    state.endpointLinks.remove(null);
                    endpointLinks.addAll(state.endpointLinks);
                }
                endpointLinksByDocumentLinks.put(state.documentSelfLink, endpointLinks);
                endpointLinkByDocumentLinks.put(state.documentSelfLink,
                        state.endpointLink != null ? state.endpointLink : EMPTY_STRING);
            } else if (doc.documentKind.equals(NETWORK_STATE_DOCUMENT_KIND)) {
                NetworkState state = Utils.fromJson(document, NetworkState.class);
                if (state.endpointLinks != null) {
                    state.endpointLinks.remove(null);
                    endpointLinks.addAll(state.endpointLinks);
                }
                endpointLinksByDocumentLinks.put(state.documentSelfLink, endpointLinks);
                endpointLinkByDocumentLinks.put(state.documentSelfLink,
                        state.endpointLink != null ? state.endpointLink : EMPTY_STRING);
            } else if (doc.documentKind.equals(NETWORK_INTERFACE_STATE_DOCUMENT_KIND)) {
                NetworkInterfaceState state = Utils.fromJson(document, NetworkInterfaceState.class);
                if (state.endpointLinks != null) {
                    state.endpointLinks.remove(null);
                    endpointLinks.addAll(state.endpointLinks);
                }
                endpointLinksByDocumentLinks.put(state.documentSelfLink, endpointLinks);
                endpointLinkByDocumentLinks.put(state.documentSelfLink,
                        state.endpointLink != null ? state.endpointLink : EMPTY_STRING);
            } else if (doc.documentKind.equals(SECURITY_GROUP_STATE_DOCUMENT_KIND)) {
                SecurityGroupState state = Utils.fromJson(document, SecurityGroupState.class);
                if (state.endpointLinks != null) {
                    state.endpointLinks.remove(null);
                    endpointLinks.addAll(state.endpointLinks);
                }
                endpointLinksByDocumentLinks.put(state.documentSelfLink, endpointLinks);
                endpointLinkByDocumentLinks.put(state.documentSelfLink,
                        state.endpointLink != null ? state.endpointLink : EMPTY_STRING);
            } else if (doc.documentKind.equals(SUBNET_STATE_DOCUMENT_KIND)) {
                SubnetState state = Utils.fromJson(document, SubnetState.class);
                if (state.endpointLinks != null) {
                    state.endpointLinks.remove(null);
                    endpointLinks.addAll(state.endpointLinks);
                }
                endpointLinksByDocumentLinks.put(state.documentSelfLink, endpointLinks);
                endpointLinkByDocumentLinks.put(state.documentSelfLink,
                        state.endpointLink != null ? state.endpointLink : EMPTY_STRING);
            }  else if (doc.documentKind.equals(LOAD_BALANCER_DOCUMENT_KIND)) {
                LoadBalancerState state = Utils.fromJson(document, LoadBalancerState.class);
                if (state.endpointLinks != null) {
                    state.endpointLinks.remove(null);
                    endpointLinks.addAll(state.endpointLinks);
                }
                endpointLinksByDocumentLinks.put(state.documentSelfLink, endpointLinks);
                endpointLinkByDocumentLinks.put(state.documentSelfLink,
                        state.endpointLink != null ? state.endpointLink : EMPTY_STRING);
            } else if (doc.documentKind.equals(STORAGE_DESCRIPTION_DOCUMENT_KIND)) {
                StorageDescription state = Utils.fromJson(document, StorageDescription.class);
                if (state.endpointLinks != null) {
                    state.endpointLinks.remove(null);
                    endpointLinks.addAll(state.endpointLinks);
                }
                endpointLinksByDocumentLinks.put(state.documentSelfLink, endpointLinks);
                endpointLinkByDocumentLinks.put(state.documentSelfLink,
                        state.endpointLink != null ? state.endpointLink : EMPTY_STRING);
            } else if (doc.documentKind.equals(RESOURCE_GROUP_DOCUMENT_KIND)) {
                ResourceGroupState state = Utils.fromJson(document, ResourceGroupState.class);
                if (state.customProperties != null && state.customProperties
                        .containsKey(ResourceGroupService.PROPERTY_NAME_IS_USER_CREATED)) {
                    continue;
                }
                if (state.customProperties != null && state.customProperties.containsKey(
                        ResourceUtils.CUSTOM_PROP_NO_ENDPOINT)) {
                    // skip resources that have never been attached to a particular endpoint
                    continue;
                }
                if (state.endpointLinks != null) {
                    state.endpointLinks.remove(null);
                    endpointLinks.addAll(state.endpointLinks);
                }
                endpointLinksByDocumentLinks.put(state.documentSelfLink, endpointLinks);
                endpointLinkByDocumentLinks.put(state.documentSelfLink,
                        state.endpointLink != null ? state.endpointLink : EMPTY_STRING);
            } else if (doc.documentKind.equals(IMAGE_STATE_KIND)) {
                ImageState state = Utils.fromJson(document, ImageState.class);
                if (state.isPublicImage()) {
                    // public images should not be removed by the groomer as they are not related
                    // to a particular endpoint (their endpoint links are always empty)
                    continue;
                }
                if (state.endpointLinks != null) {
                    state.endpointLinks.remove(null);
                    endpointLinks.addAll(state.endpointLinks);
                }
                endpointLinksByDocumentLinks.put(state.documentSelfLink, endpointLinks);
                endpointLinkByDocumentLinks.put(state.documentSelfLink,
                        state.endpointLink != null ? state.endpointLink : EMPTY_STRING);
            } else if (doc.documentKind.equals(ROUTER_STATE_KIND)) {
                RouterState state = Utils.fromJson(document, RouterState.class);
                if (state.endpointLinks != null) {
                    state.endpointLinks.remove(null);
                    endpointLinks.addAll(state.endpointLinks);
                }
                endpointLinksByDocumentLinks.put(state.documentSelfLink, endpointLinks);
                endpointLinkByDocumentLinks.put(state.documentSelfLink,
                        state.endpointLink != null ? state.endpointLink : EMPTY_STRING);
            } else if (doc.documentKind.equals(AUTH_CREDENTIALS_SERVICE_STATE_KIND)) {
                AuthCredentialsServiceState state = Utils.fromJson(document,
                        AuthCredentialsServiceState.class);
                if (state.customProperties != null && state.customProperties
                        .get(CUSTOM_PROP_ENDPOINT_LINK) != null) {
                    endpointLinkByDocumentLinks.put(state.documentSelfLink,
                            state.customProperties.get(CUSTOM_PROP_ENDPOINT_LINK));
                } else {
                    endpointLinkByDocumentLinks.put(state.documentSelfLink, EMPTY_STRING);
                }
            }
        }
    }

    /**
     * Set service stats, number of documents deleted and number of documents patched.
     */
    private void setStats(EndpointResourceDeletionRequest task) {
        PhotonModelUtils.setStat(this, STAT_NAME_DOCUMENTS_DELETED, STAT_UNIT_COUNT,
                task.documentsDeletedCount);
        PhotonModelUtils.setStat(this, STAT_NAME_ENDPOINT_LINKS_PATCHED, STAT_UNIT_COUNT,
                task.endpointLinksPatchedCount);
        PhotonModelUtils.setStat(this, STAT_NAME_ENDPOINT_LINK_PATCHED, STAT_UNIT_COUNT,
                task.endpointLinkPatchedCount);
    }

    /**
     * Creates an operation with ServiceStateMapUpdateRequest for updating customProperties.__endpointLink
     * in ResourceGroupStates and AuthCredentialsServiceState
     */
    private Operation createResourceGroupEndpointLinkPatchOp(String documentLink, String endpointLink) {
        Map<Object, Object> customProperty = new HashMap<>();
        customProperty.put(CUSTOM_PROP_ENDPOINT_LINK, endpointLink);
        Map<String, Map<Object, Object>> entriesToAdd = new HashMap<>();
        entriesToAdd.put(ResourceGroupState.FIELD_NAME_CUSTOM_PROPERTIES, customProperty);
        ServiceStateMapUpdateRequest request = ServiceStateMapUpdateRequest.create(entriesToAdd, null);

        return Operation.createPatch(this.getHost(), documentLink)
                .setReferer(this.getUri())
                .setBody(request);
    }

    /**
     * Creates an operation with PatchEndpointLinkObject for updating endpointLink in resources except
     * resource groups and auth credentials.
     */
    private Operation createEndpointLinkPatchOp(String documentLink, String endpointLink) {
        PatchEndpointLinkObject state = new PatchEndpointLinkObject();
        state.endpointLink = endpointLink;

        return Operation.createPatch(this.getHost(), documentLink)
                .setReferer(this.getUri())
                .setBody(state);
    }

    /**
     * Returns whether task is being invoked in context of a single endpoint.
     */
    private static boolean isEndpointSpecified(EndpointResourceDeletionRequest task) {
        return task.endpointLink != null && !task.endpointLink.isEmpty();
    }

    /**
     * Object used for patching endpointLink to resources.
     */
    class PatchEndpointLinkObject {
        String endpointLink;
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        EndpointResourceDeletionRequest template = (EndpointResourceDeletionRequest) super
                .getDocumentTemplate();
        template.documentDescription.serializedStateSizeLimit = MAX_SERIALIZED_STATE_SIZE_BYTES;
        return template;
    }
}