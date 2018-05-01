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

import static com.vmware.photon.controller.model.resources.TagService.TagState.TagOrigin.DISCOVERED;
import static com.vmware.photon.controller.model.resources.TagService.TagState.TagOrigin.SYSTEM;
import static com.vmware.photon.controller.model.resources.TagService.TagState.TagOrigin.USER_DEFINED;
import static com.vmware.photon.controller.model.resources.util.PhotonModelUtils.createOriginTagQuery;
import static com.vmware.photon.controller.model.util.PhotonModelUriUtils.createInventoryUri;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.TagService.TagState;

import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskFactoryService;
import com.vmware.xenon.services.common.TaskService;

/**
 * Groomer task for marking stale tag states as deleted.
 */

public class TagGroomerTaskService extends TaskService<TagGroomerTaskService.TagDeletionRequest> {
    public static final String FACTORY_LINK = UriPaths.PROVISIONING + "/tag-groomer-task";

    public static final int DEFAULT_QUERY_RESULT_LIMIT = 100;
    public static final String TAG_QUERY_RESULT_LIMIT = UriPaths.PROPERTY_PREFIX + "TagGroomerTaskService.query.resultLimit";
    public static final int QUERY_RESULT_LIMIT = Integer.getInteger(TAG_QUERY_RESULT_LIMIT, DEFAULT_QUERY_RESULT_LIMIT);
    public static final int MAX_QUERY_RESULT_LIMIT = QueryUtils.MAX_RESULT_LIMIT;
    public static final long DEFAULT_EXPIRATION_MICROS = TimeUnit.DAYS.toMicros(7);

    public static FactoryService createFactory() {
        TaskFactoryService fs = new TaskFactoryService(TagDeletionRequest.class) {
            @Override
            public Service createServiceInstance() throws Throwable {
                return new TagGroomerTaskService();
            }
        };
        fs.setPeerNodeSelectorPath(ServiceUriPaths.DEFAULT_1X_NODE_SELECTOR);
        return fs;
    }

    public TagGroomerTaskService() {
        super(TagDeletionRequest.class);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    public enum SubStage {
        /**
         * Query for tag states.
         */
        QUERY_FOR_ALL_TAG_STATES,

        /**
         * Get the next page of tagLinks, if they exist.
         */
        GET_TAGS_NEXT_PAGE,

        /**
         * Query for the documents that are associated with first page of tag results.
         */
        QUERY_FOR_DOCUMENTS_TAGGED_WITH_TAG_STATE,

        /**
         * Get the next page of documents, if they exist.
         */
        GET_DOCUMENTS_NEXT_PAGE,

        /**
         * Soft delete stale tag states. Tag states are marked as deleted and not deleted from index.
         */
        SOFT_DELETE_TAG_STATES,

        /**
         * Complete patching tags as deleted.
         */
        FINISHED,

        /**
         * Fail deletion.
         */
        FAILED
    }

    /**
     * Task state for TagGroomerTaskService.
     */
    public static class TagDeletionRequest extends TaskService.TaskServiceState {
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public SubStage subStage;

        // Map of tag states page.
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public Map<String, TagState> tagsMap;

        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public String tagsNextPageLink;

        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public String docsNextPageLink;

        public TagDeletionRequest() {
            this.subStage = SubStage.QUERY_FOR_ALL_TAG_STATES;
            this.tagsMap = new HashMap<>();
        }
    }

    @Override
    public void handlePatch(Operation patch) {
        TagDeletionRequest currentTask = getState(patch);
        TagDeletionRequest patchBody = getBody(patch);

        if (!validateTransition(patch, currentTask, patchBody)) {
            return;
        }

        updateState(currentTask, patchBody);
        patch.setBody(currentTask);
        patch.complete();

        switch (currentTask.taskInfo.stage) {
        case STARTED:
            logInfo("Started stale tag deletion.");
            handleSubStage(currentTask);
            break;
        case FINISHED:
            logInfo("Successfully finished stale tag deletion.");
            break;
        case FAILED:
            logWarning("Task failed: %s", (currentTask.failureMessage == null ?
                    "No reason given" : currentTask.failureMessage));
            break;
        default:
            logWarning("Unexpected subStage: %s", currentTask.taskInfo.stage);
            break;
        }
    }

    /**
     * Override updateState to update all service values from patch body.
     */
    @Override
    protected void updateState(TagDeletionRequest currentTask, TagDeletionRequest patchBody) {
        currentTask.tagsMap = patchBody.tagsMap;
        currentTask.tagsNextPageLink = patchBody.tagsNextPageLink;
        currentTask.docsNextPageLink = patchBody.docsNextPageLink;
        // auto-merge fields based on annotations
        super.updateState(currentTask, patchBody);
    }

    /**
     * State machine to go through task flow.
     */
    private void handleSubStage(TagDeletionRequest task) {
        switch (task.subStage) {
        case QUERY_FOR_ALL_TAG_STATES:
            getDiscoveredNotDeletedTagStates(task, SubStage.GET_TAGS_NEXT_PAGE);
            break;
        case GET_TAGS_NEXT_PAGE:
            getNextPageOfTagStates(task, SubStage.QUERY_FOR_DOCUMENTS_TAGGED_WITH_TAG_STATE);
            break;
        case QUERY_FOR_DOCUMENTS_TAGGED_WITH_TAG_STATE:
            queryDocumentsTaggedWithTag(task, SubStage.GET_DOCUMENTS_NEXT_PAGE);
            break;
        case GET_DOCUMENTS_NEXT_PAGE:
            getNextPageOfTaggedDocuments(task, SubStage.SOFT_DELETE_TAG_STATES);
            break;
        case SOFT_DELETE_TAG_STATES:
            markStaleTagsDeleted(task, SubStage.GET_TAGS_NEXT_PAGE);
            break;
        case FINISHED:
            sendSelfFinishedPatch(task);
            break;
        case FAILED:
            sendSelfFailurePatch(task, task.failureMessage == null ? "TagGroomerTask failed." :
                    task.failureMessage);
            break;
        default:
            task.subStage = SubStage.FAILED;
            sendSelfFailurePatch(task, "Unknown subStage encountered: " + task.subStage);
            break;
        }
    }

    /**
     * Collect all tag states with origins ["DISCOVERED"] that have not been soft deleted, and
     * perform soft deletion of stale ones by page.
     *
     * If the tag has multiple origins, do not delete the tag.
     */
    private void getDiscoveredNotDeletedTagStates(TagDeletionRequest task, SubStage next) {
        Query query = Query.Builder.create()
                .addKindFieldClause(TagState.class).build();

        Map<String, Query.Occurance> origin = new HashMap<>();
        origin.put(DISCOVERED.toString(), Query.Occurance.MUST_OCCUR);
        origin.put(SYSTEM.toString(), Query.Occurance.MUST_NOT_OCCUR);
        origin.put(USER_DEFINED.toString(), Query.Occurance.MUST_NOT_OCCUR);

        Query externalQuery = createOriginTagQuery(Boolean.TRUE, origin);
        query.addBooleanClause(externalQuery);

        query.addBooleanClause(new Query()
                .setTermPropertyName(TagState.FIELD_NAME_DELETED)
                .setTermMatchValue(Boolean.FALSE.toString()));

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .setQuery(query)
                .addOption(QueryOption.EXPAND_CONTENT)
                .setResultLimit(QUERY_RESULT_LIMIT)
                .build();

        QueryUtils.startInventoryQueryTask(this, queryTask)
                .whenComplete((response, e) -> {
                    if (e != null) {
                        task.failureMessage = e.getMessage();
                        task.subStage = SubStage.FAILED;
                        sendSelfPatch(task);
                        return;
                    }

                    if (response.results != null && response.results.nextPageLink != null) {
                        task.tagsNextPageLink = response.results.nextPageLink;
                        task.subStage = next;
                    } else {
                        task.subStage = SubStage.FINISHED;
                    }
                    sendSelfPatch(task);
                });
    }

    /**
     * Retrieve nextPage of tagLinks to be processed.
     */
    private void getNextPageOfTagStates(TagDeletionRequest task, SubStage next) {
        if (task.tagsNextPageLink == null) {
            task.subStage = SubStage.FINISHED;
            handleSubStage(task);
            return;
        }
        // reset all the results from the last page that was processed.
        task.tagsMap.clear();

        Operation.createGet(createInventoryUri(this.getHost(), task.tagsNextPageLink))
                .setReferer(this.getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("Error getting next page of results [ex=%s]",
                                e.getMessage());
                        task.failureMessage = e.getMessage();
                        task.subStage = SubStage.FAILED;
                        sendSelfPatch(task);
                        return;
                    }

                    QueryTask qrt = o.getBody(QueryTask.class);
                    if (qrt.results != null && qrt.results.documentLinks != null) {
                        qrt.results.documents.values().forEach(tagState -> {
                            TagState ts = Utils.fromJson(tagState, TagState.class);
                            task.tagsMap.put(ts.documentSelfLink, ts);
                        });
                        task.tagsNextPageLink = qrt.results.nextPageLink;
                    }

                    task.subStage = next;
                    sendSelfPatch(task);
                })
                .sendWith(this);
    }

    /**
     * Issue query for documents that contain the specific tagLink.
     */
    private void queryDocumentsTaggedWithTag(TagDeletionRequest task, SubStage next) {
        if (task.tagsMap.isEmpty()) {
            task.subStage = SubStage.FINISHED;
            sendSelfPatch(task);
            return;
        }

        Set<String> tagLinks = new HashSet<>();
        task.tagsMap.forEach((k,v) -> tagLinks.add(k));

        Query query = Query.Builder.create()
                .addInCollectionItemClause(ResourceState.FIELD_NAME_TAG_LINKS, tagLinks)
                .build();

        // get all documents that contain the links.
        QueryTask queryTask = QueryTask.Builder
                .createDirectTask()
                .addOption(QueryOption.SELECT_LINKS)
                .addLinkTerm(ResourceState.FIELD_NAME_TAG_LINKS)
                .setResultLimit(MAX_QUERY_RESULT_LIMIT)
                .setQuery(query)
                .build();

        QueryUtils.startInventoryQueryTask(this, queryTask)
                .whenComplete((response, e) -> {
                    if (e != null) {
                        task.failureMessage = e.getMessage();
                        task.subStage = SubStage.FAILED;
                        sendSelfPatch(task);
                        return;
                    }

                    if (response.results != null) {
                        task.docsNextPageLink = response.results.nextPageLink;
                        task.subStage = next;
                    } else {
                        task.subStage = SubStage.GET_TAGS_NEXT_PAGE;
                    }
                    sendSelfPatch(task);
                });
    }

    /**
     * Retrieve nextPage of tagged documents and find which tags are not being used. Go over all
     * pages and identify all unused tags before proceeding with marking tag as deleted.
     */
    private void getNextPageOfTaggedDocuments(TagDeletionRequest task, SubStage next) {
        if (task.docsNextPageLink == null) {
            if (!task.tagsMap.isEmpty()) {
                task.subStage = next;
            } else {
                task.subStage = SubStage.GET_TAGS_NEXT_PAGE;
            }
            sendSelfPatch(task);
            return;
        }

        Operation.createGet(createInventoryUri(this.getHost(), task.docsNextPageLink))
                .setReferer(this.getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("Error getting next page of tagged documents [ex=%s]",
                                e.getMessage());
                        task.subStage = SubStage.GET_TAGS_NEXT_PAGE;
                        sendSelfPatch(task);
                        return;
                    }

                    QueryTask qrt = o.getBody(QueryTask.class);
                    if (qrt.results != null && qrt.results.selectedLinks != null) {
                        qrt.results.selectedLinks.forEach(tagLink -> {
                            if (qrt.results.selectedLinks.contains(tagLink)) {
                                task.tagsMap.remove(tagLink);
                            }
                        });
                        task.docsNextPageLink = qrt.results.nextPageLink;
                    } else {
                        // if results are null, don't fail but proceed to next page of tags
                        task.subStage = SubStage.GET_TAGS_NEXT_PAGE;
                        sendSelfPatch(task);
                        return;
                    }
                    getNextPageOfTaggedDocuments(task, next);
                })
                .sendWith(this);
    }

    /**
     * Mark stale tags as deleted. On completion, process the next batch of tagLinks or complete task.
     */
    private void markStaleTagsDeleted(TagDeletionRequest task, SubStage next) {
        if (task.tagsMap.isEmpty()) {
            if (task.tagsNextPageLink == null) {
                task.subStage = SubStage.FINISHED;
            } else {
                task.subStage = next;
            }
            sendSelfPatch(task);
            return;
        }

        // Create an update operation for each stale tagState
        List<Operation> operations = new ArrayList<>();
        for (Map.Entry<String, TagState> entry : task.tagsMap.entrySet()) {
            TagState tagToDelete = entry.getValue();
            tagToDelete.deleted = Boolean.TRUE;
            tagToDelete.documentExpirationTimeMicros = Utils.getNowMicrosUtc()
                    + DEFAULT_EXPIRATION_MICROS;
            operations.add(Operation.createPatch(createInventoryUri(this.getHost(),
                    tagToDelete.documentSelfLink))
                    .setBody(tagToDelete)
                    .setReferer(this.getUri()));
        }

        OperationJoin.create(operations).setCompletion((ops, exs) -> {
            if (exs != null && !exs.isEmpty()) {
                exs.values().forEach(
                        ex -> this.logWarning(() -> String.format("Error: %s", ex.getMessage())));
            } else {
                task.subStage = next;
                sendSelfPatch(task);
            }
        }).sendWith(this);
    }
}