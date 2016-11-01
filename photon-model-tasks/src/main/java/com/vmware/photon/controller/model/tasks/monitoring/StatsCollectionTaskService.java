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

import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.util.PhotonModelUtils;
import com.vmware.photon.controller.model.tasks.SubTaskService;
import com.vmware.photon.controller.model.tasks.SubTaskService.SubTaskState;
import com.vmware.photon.controller.model.tasks.TaskUtils;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService.SingleResourceStatsCollectionTaskState;

import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskFactoryService;
import com.vmware.xenon.services.common.TaskService;

/**
 * Service to collect stats from compute instances under the resource pool. This task is a one shot
 * task that is not replicated or persisted. The caller takes care of invoking these tasks
 * periodically
 *
 */
public class StatsCollectionTaskService extends TaskService<StatsCollectionTaskService.StatsCollectionTaskState> {

    public static final String FACTORY_LINK = UriPaths.MONITORING + "/stats-collection-tasks";

    public static FactoryService createFactory() {
        TaskFactoryService fs =  new TaskFactoryService(StatsCollectionTaskState.class) {
            @Override
            public Service createServiceInstance() throws Throwable {
                return new StatsCollectionTaskService();
            }
        };
        fs.setPeerNodeSelectorPath(ServiceUriPaths.DEFAULT_1X_NODE_SELECTOR);
        return fs;
    }

    public static final String STATS_QUERY_RESULT_LIMIT = UriPaths.PROPERTY_PREFIX + "StatsCollectionTaskService.query.resultLimit";
    private static final String QUERY_RESULT_LIMIT = System.getProperty(STATS_QUERY_RESULT_LIMIT);
    private static final int DEFAULT_QUERY_RESULT_LIMIT = 50;

    public enum StatsCollectionStage {
        INIT, GET_RESOURCES
    }

    /**
     * This class defines the document state associated with a single StatsCollectionTaskService
     * instance.
     */
    public static class StatsCollectionTaskState extends TaskService.TaskServiceState {

        /**
         * Reference URI to the resource pool.
         */
        public String resourcePoolLink;

        public StatsCollectionStage taskStage;

        public URI statsAdapterReference;

        /**
         * cursor for obtaining compute services
         */
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public String nextPageLink;
    }

    public StatsCollectionTaskService() {
        super(StatsCollectionTaskState.class);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handleStart(Operation start) {
        try {
            if (!start.hasBody()) {
                start.fail(new IllegalArgumentException("body is required"));
                return;
            }
            StatsCollectionTaskState state = start
                    .getBody(StatsCollectionTaskState.class);

            validateState(state);
            logInfo("Starting stats collection task for: " + state.resourcePoolLink);
            start.complete();
            state.taskInfo = TaskUtils.createTaskState(TaskStage.STARTED);
            state.taskStage = StatsCollectionStage.INIT;
            handleStagePatch(start, state);
        } catch (Throwable e) {
            start.fail(e);
        }
    }

    @Override
    public void handlePatch(Operation patch) {
        StatsCollectionTaskState currentState = getState(patch);
        StatsCollectionTaskState patchState = patch
                .getBody(StatsCollectionTaskState.class);
        updateState(currentState, patchState);
        patch.setBody(currentState);
        patch.complete();

        switch (currentState.taskInfo.stage) {
        case CREATED:
            break;
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
            logInfo("Finished stats collection task for: " + currentState.resourcePoolLink);
            sendRequest(Operation
                        .createDelete(getUri()));
            break;
        default:
            break;
        }
    }

    @Override
    public void handlePut(Operation put) {
        PhotonModelUtils.handleIdempotentPut(this, put);
    }

    private void validateState(StatsCollectionTaskState state) {
        if (state.resourcePoolLink == null) {
            throw new IllegalStateException("resourcePoolLink should not be null");
        }
    }

    @Override
    public void updateState(StatsCollectionTaskState currentState,
            StatsCollectionTaskState patchState) {
        if (patchState.taskInfo != null) {
            currentState.taskInfo = patchState.taskInfo;
        }
        if (patchState.taskStage != null) {
            currentState.taskStage = patchState.taskStage;
        }
        if (patchState.nextPageLink != null) {
            currentState.nextPageLink = patchState.nextPageLink;
        }
    }

    private void handleStagePatch(Operation op, StatsCollectionTaskState currentState) {
        switch (currentState.taskStage) {
        case INIT:
            initializeQuery(op, currentState, null);
            break;
        case GET_RESOURCES:
            getResources(op, currentState);
            break;
        default:
            break;
        }
    }

    private void initializeQuery(Operation op, StatsCollectionTaskState currentState,
            ResourcePoolState resourcePoolState) {

        // load the RP state, if not already
        if (resourcePoolState == null) {
            sendRequest(Operation.createGet(this, currentState.resourcePoolLink)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            TaskUtils.sendFailurePatch(this, new StatsCollectionTaskState(), e);
                            return;
                        }

                        ResourcePoolState loadedRpState = o.getBody(ResourcePoolState.class);
                        initializeQuery(op, currentState, loadedRpState);
                    }));
            return;
        }

        int resultLimit = DEFAULT_QUERY_RESULT_LIMIT;
        try {
            resultLimit = (QUERY_RESULT_LIMIT != null) ?
                Integer.valueOf(QUERY_RESULT_LIMIT) : DEFAULT_QUERY_RESULT_LIMIT;
        } catch (NumberFormatException e) {
            // use the default;
            logWarning(STATS_QUERY_RESULT_LIMIT +
                    " is not a number; Using a default value of " + DEFAULT_QUERY_RESULT_LIMIT);
        }

        QueryTask.Builder queryTaskBuilder = QueryTask.Builder.createDirectTask()
                .setQuery(resourcePoolState.query)
                .setResultLimit(resultLimit);

        sendRequest(Operation
                .createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(queryTaskBuilder.build())
                .setCompletion((queryOp, queryEx) -> {
                    if (queryEx != null) {
                        TaskUtils.sendFailurePatch(this, new StatsCollectionTaskState(), queryEx);
                        return;
                    }
                    QueryTask rsp = queryOp.getBody(QueryTask.class);
                    StatsCollectionTaskState patchBody = new StatsCollectionTaskState();
                    if (rsp.results.nextPageLink == null) {
                        patchBody.taskInfo = TaskUtils.createTaskState(TaskStage.FINISHED);
                    } else {
                        patchBody.taskInfo = TaskUtils.createTaskState(TaskStage.STARTED);
                        patchBody.taskStage = StatsCollectionStage.GET_RESOURCES;
                        patchBody.nextPageLink = rsp.results.nextPageLink;
                    }
                    TaskUtils.sendPatch(this, patchBody);
                }));

    }

    private void getResources(Operation op, StatsCollectionTaskState currentState) {
        sendRequest(Operation
                .createGet(UriUtils.buildUri(getHost(), currentState.nextPageLink))
                .setCompletion(
                        (getOp, getEx) -> {
                            if (getEx != null) {
                                TaskUtils.sendFailurePatch(this, new StatsCollectionTaskState(), getEx);
                                return;
                            }
                            QueryTask page = getOp.getBody(QueryTask.class);
                            if (page.results.documentLinks.size() == 0) {
                                StatsCollectionTaskState patchBody = new StatsCollectionTaskState();
                                patchBody.taskInfo = TaskUtils.createTaskState(TaskStage.FINISHED);
                                TaskUtils.sendPatch(this, patchBody);
                                return;
                            }
                            createSubTask(page.results.documentLinks, page.results.nextPageLink,
                                    currentState);
                        }));
    }

    private void createSubTask(List<String> computeResources, String nextPageLink,
            StatsCollectionTaskState currentState) {
        StatsCollectionTaskState patchBody = new StatsCollectionTaskState();
        if (nextPageLink != null) {
            patchBody.taskInfo = TaskUtils.createTaskState(TaskStage.STARTED);
            patchBody.taskStage = StatsCollectionStage.GET_RESOURCES;
            patchBody.nextPageLink = nextPageLink;
        } else {
            patchBody.taskInfo = TaskUtils.createTaskState(TaskStage.FINISHED);
        }
        SubTaskState subTaskInitState = new SubTaskState();
        subTaskInitState.parentPatchBody = Utils.toJson(patchBody);
        subTaskInitState.errorThreshold = 0;
        subTaskInitState.completionsRemaining = computeResources.size();
        subTaskInitState.parentTaskLink = getSelfLink();
        Operation startPost = Operation
                .createPost(this, UUID.randomUUID().toString())
                .setBody(subTaskInitState)
                .setCompletion((postOp, postEx) -> {
                    if (postEx != null) {
                        TaskUtils.sendFailurePatch(this, new StatsCollectionTaskState(), postEx);
                        return;
                    }
                    SubTaskState body = postOp
                            .getBody(SubTaskState.class);
                    // kick off a collection task for each resource and track completion
                    // via the compute subtask
                    for (String computeLink : computeResources) {
                        createSingleResourceComputeTask(computeLink, body.documentSelfLink, currentState.statsAdapterReference);
                    }
                });
        getHost().startService(startPost, new SubTaskService());
    }

    private void createSingleResourceComputeTask(String computeLink, String subtaskLink,
            URI statsAdapterReference) {
        SingleResourceStatsCollectionTaskState initState = new SingleResourceStatsCollectionTaskState();
        initState.parentTaskReference = UriUtils.buildPublicUri(getHost(), subtaskLink);
        initState.computeLink = computeLink;
        initState.statsAdapterReference = statsAdapterReference;
        SubTaskState patchState = new SubTaskState();
        patchState.taskInfo = TaskUtils.createTaskState(TaskStage.FINISHED);
        initState.parentPatchBody = patchState;
        sendRequest(Operation
                    .createPost(this,
                            SingleResourceStatsCollectionTaskService.FACTORY_LINK)
                    .setBody(initState)
                    .setCompletion((factoryPostOp, factoryPostEx) -> {
                        if (factoryPostEx != null) {
                            TaskUtils.sendFailurePatch(this, new StatsCollectionTaskState(), factoryPostEx);
                        }
                    }));
    }
}
