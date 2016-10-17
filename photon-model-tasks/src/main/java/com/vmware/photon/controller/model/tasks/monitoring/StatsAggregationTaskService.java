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

package com.vmware.photon.controller.model.tasks.monitoring;


import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.tasks.SubTaskService;
import com.vmware.photon.controller.model.tasks.TaskUtils;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsAggregationTaskService.SingleResourceStatsAggregationTaskState;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskService;

/**
 * Task to aggregate resource stats for a set of resources specified by a query.
 */
public class StatsAggregationTaskService extends TaskService<StatsAggregationTaskService.StatsAggregationTaskState> {

    public static final String FACTORY_LINK = UriPaths.MONITORING + "/stats-aggregation";

    public static final String STATS_QUERY_RESULT_LIMIT = UriPaths.PROPERTY_PREFIX + "StatsAggregationTaskService.query.resultLimit";
    private static final int DEFAULT_QUERY_RESULT_LIMIT = 100;

    public static class StatsAggregationTaskState extends TaskService.TaskServiceState {

        @Documentation(description = "The set of metric names to aggregate on")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public Set<String> metricNames;

        @Documentation(description = "The query to lookup resources for stats aggregation")
        public Query query;

        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        @UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public StatsAggregationStage taskStage;

        /**
         * cursor for obtaining compute services - this is set for the first time based on
         * the result of a query task and updated on every patch thereafter based on the result
         * object obtained when a GET is issued on the link
         */
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        @UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String queryResultLink;
    }

    public enum StatsAggregationStage {
        INIT, GET_RESOURCES
    }

    public StatsAggregationTaskService() {
        super(StatsAggregationTaskState.class);
    }

    @Override
    protected StatsAggregationTaskState validateStartPost(Operation postOp) {
        StatsAggregationTaskState state = super.validateStartPost(postOp);
        if (state == null) {
            return null;
        }
        if (state.query == null) {
            postOp.fail(new IllegalArgumentException("query needs to be specified"));
            return null;
        }
        if (state.metricNames == null || state.metricNames.isEmpty()) {
            postOp.fail(new IllegalStateException("metricNames should not be null or empty"));
            return null;
        }
        return state;
    }

    @Override
    protected void initializeState(StatsAggregationTaskState state, Operation postOp) {
        super.initializeState(state, postOp);
        state.taskStage = StatsAggregationStage.INIT;
    }

    @Override
    public void handlePatch(Operation patch) {
        StatsAggregationTaskState currentState = getState(patch);
        StatsAggregationTaskState patchState = getBody(patch);
        validateTransition(patch, currentState, patchState);
        updateState(currentState, patchState);
        patch.setBody(currentState);
        patch.complete();

        switch (currentState.taskInfo.stage) {
        case STARTED:
            handleStagePatch(patch, currentState);
            break;
        case FINISHED:
        case FAILED:
        case CANCELLED:
            if (TaskState.isFailed(currentState.taskInfo) ||
                    TaskState.isCancelled(currentState.taskInfo)) {
                if (currentState.failureMessage != null) {
                    logWarning(currentState.failureMessage);
                }
            }
            sendRequest(Operation
                        .createDelete(getUri()));
            break;
        default:
            break;
        }
    }

    private void handleStagePatch(Operation op, StatsAggregationTaskState currentState) {
        switch (currentState.taskStage) {
        case INIT:
            initializeQuery(op, currentState);
            break;
        case GET_RESOURCES:
            getResources(op, currentState);
            break;
        default:
            break;
        }
    }

    private void initializeQuery(Operation op, StatsAggregationTaskState currentState) {

        int resultLimit = Integer.getInteger(STATS_QUERY_RESULT_LIMIT, DEFAULT_QUERY_RESULT_LIMIT);
        QueryTask.Builder queryTaskBuilder = QueryTask.Builder.createDirectTask()
                .setQuery(currentState.query).setResultLimit(resultLimit);
        sendRequest(Operation
                .createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(queryTaskBuilder.build())
                .setConnectionSharing(true)
                .setCompletion((queryOp, queryEx) -> {
                    if (queryEx != null) {
                        sendSelfFailurePatch(currentState, queryEx.getMessage());
                        return;
                    }
                    QueryTask rsp = queryOp.getBody(QueryTask.class);
                    StatsAggregationTaskState patchBody = new StatsAggregationTaskState();
                    if (rsp.results.nextPageLink == null) {
                        patchBody.taskInfo = TaskUtils.createTaskState(TaskStage.FINISHED);
                    } else {
                        patchBody.taskInfo = TaskUtils.createTaskState(TaskStage.STARTED);
                        patchBody.taskStage = StatsAggregationStage.GET_RESOURCES;
                        patchBody.queryResultLink = rsp.results.nextPageLink;
                    }
                    sendSelfPatch(patchBody);
                }));

    }

    private void getResources(Operation op, StatsAggregationTaskState currentState) {
        sendRequest(Operation
                .createGet(UriUtils.buildUri(getHost(), currentState.queryResultLink))
                .setCompletion(
                        (getOp, getEx) -> {
                            if (getEx != null) {
                                sendSelfFailurePatch(currentState, getEx.getMessage());
                                return;
                            }
                            QueryTask page = getOp.getBody(QueryTask.class);
                            if (page.results.documentLinks.size() == 0) {
                                sendSelfPatch(currentState, TaskStage.FINISHED, null);
                                return;
                            }
                            // process resources
                            createSubTask(page.results.documentLinks, page.results.nextPageLink,
                                    currentState);
                        }));
    }

    private void createSubTask(List<String> computeResources, String nextPageLink,
            StatsAggregationTaskState currentState) {
        StatsAggregationTaskState patchBody = new StatsAggregationTaskState();
        if (nextPageLink != null) {
            patchBody.taskInfo = TaskUtils.createTaskState(TaskStage.STARTED);
            patchBody.taskStage = StatsAggregationStage.GET_RESOURCES;
            patchBody.queryResultLink = nextPageLink;
        } else {
            patchBody.taskInfo = TaskUtils.createTaskState(TaskStage.FINISHED);
        }
        SubTaskService.SubTaskState subTaskInitState = new SubTaskService.SubTaskState();
        subTaskInitState.parentPatchBody = Utils.toJson(patchBody);
        subTaskInitState.errorThreshold = 0;
        subTaskInitState.completionsRemaining = computeResources.size();
        subTaskInitState.parentTaskLink = getSelfLink();
        Operation startPost = Operation
                .createPost(this, UUID.randomUUID().toString())
                .setBody(subTaskInitState)
                .setCompletion((postOp, postEx) -> {
                    if (postEx != null) {
                        TaskUtils.sendFailurePatch(this, new StatsAggregationTaskState(), postEx);
                        return;
                    }
                    SubTaskService.SubTaskState body = postOp
                            .getBody(SubTaskService.SubTaskState.class);
                    // kick off a aggregation task for each resource and track completion
                    // via the compute subtask
                    for (String computeLink : computeResources) {
                        createSingleResourceComputeTask(computeLink, body.documentSelfLink, currentState);
                    }
                });
        getHost().startService(startPost, new SubTaskService());
    }

    private void createSingleResourceComputeTask(String resourceLink, String subtaskLink, StatsAggregationTaskState currentState ) {
        SingleResourceStatsAggregationTaskState initState = new SingleResourceStatsAggregationTaskState();
        initState.resourceLink = resourceLink;
        initState.metricNames = currentState.metricNames;
        initState.parentLink = subtaskLink;
        sendRequest(Operation
                    .createPost(this,
                            SingleResourceStatsAggregationTaskService.FACTORY_LINK)
                    .setBody(initState)
                    .setCompletion((factoryPostOp, factoryPostEx) -> {
                        if (factoryPostEx != null) {
                            TaskUtils.sendFailurePatch(this, new StatsAggregationTaskState(), factoryPostEx);
                        }
                    }));
    }
}
