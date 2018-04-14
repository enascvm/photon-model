/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.discovery.cloudusage;

import static com.vmware.photon.controller.discovery.cloudaccount.utils.QueryHelper.buildComputeParentByDescriptionsQueryTask;
import static com.vmware.photon.controller.discovery.cloudaccount.utils.QueryHelper.buildStageOneComputeParentQueryTask;
import static com.vmware.photon.controller.discovery.cloudaccount.utils.QueryHelper.findComputeDescriptions;
import static com.vmware.photon.controller.discovery.cloudaccount.utils.QueryHelper.startQueryTask;
import static com.vmware.photon.controller.discovery.cloudaccount.utils.QueryHelper.startStatelessPageService;
import static com.vmware.photon.controller.model.UriPaths.CLOUD_USAGE_TASK_SERVICE;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.vmware.photon.controller.discovery.cloudaccount.utils.QueryHelper.QueryCompletionHandler;
import com.vmware.photon.controller.discovery.cloudusage.CloudUsageReportTaskService.CloudUsageReportTaskState;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.TaskService;

/**
 * Task service to kick off cloud usage report generation. The task service processes incoming
 * requests and executes appropriate query. A successful task completion populates the result field
 * in {@link CloudUsageReportTaskState}. The clients can do GET operation on the next/prev page
 * links to get more results.
 *
 * More details at: https://confluence.eng.vmware.com/x/3_V4CQ
 */
public class CloudUsageReportTaskService extends TaskService<CloudUsageReportTaskState> {
    public static final String FACTORY_LINK = CLOUD_USAGE_TASK_SERVICE;

    private static final String PROPERTY_NAME_QUERY_RESULT_LIMIT =
            UriPaths.PROPERTY_PREFIX + "CloudUsageReportTaskService.QUERY_RESULT_LIMIT";
    private static final int QUERY_RESULT_LIMIT = Integer
            .getInteger(PROPERTY_NAME_QUERY_RESULT_LIMIT, 50);

    /**
     * The state for the cloud usage task service.
     */
    public static class CloudUsageReportTaskState extends TaskService.TaskServiceState {
        @Documentation(description = "The query specification for filtering the public cloud usage report")
        public QuerySpecification filter;

        @Documentation(description = "This is only for internal usage. It keeps track of internal stages")
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public SubStage subStage;

        @Documentation(description = "This is only for internal usage. It keeps track of compute descriptions that match cloud type filter")
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public Set<String> descriptionLinks;

        @Documentation(description = "The public cloud usage result set")
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public ServiceDocumentQueryResult results;

        @Documentation(description = "The tenant links")
        public List<String> tenantLinks;
    }

    /**
     * Substages for task processing.
     */
    enum SubStage {
        /**
         * Stage to process the request.
         */
        PROCESS_REQUEST,

        /**
         * Stage to execute the compute host based query.
         */
        QUERY_COMPUTE_HOST,

        /**
         * Stage to filter compute descriptions.
         */
        QUERY_COMPUTE_DESCRIPTIONS,

        /**
         * Stage to indicate success.
         */
        SUCCESS
    }

    public CloudUsageReportTaskService() {
        super(CloudUsageReportTaskState.class);
        toggleOption(ServiceOption.REPLICATION, true);
        toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    protected void initializeState(CloudUsageReportTaskState task, Operation taskOperation) {
        task.subStage = SubStage.PROCESS_REQUEST;
        super.initializeState(task, taskOperation);
    }

    @Override
    protected CloudUsageReportTaskState validateStartPost(Operation taskOperation) {
        CloudUsageReportTaskState task = super.validateStartPost(taskOperation);

        if (task != null) {
            if (task.subStage != null) {
                taskOperation.fail(
                        new IllegalArgumentException("Do not specify subStage: internal use only"));
                return null;
            }
        }

        return task;
    }

    @Override
    protected boolean validateTransition(Operation patch, CloudUsageReportTaskState currentTask,
            CloudUsageReportTaskState patchBody) {
        super.validateTransition(patch, currentTask, patchBody);
        if (patchBody.taskInfo.stage == TaskStage.STARTED && patchBody.subStage == null) {
            patch.fail(new IllegalArgumentException("Missing sub-stage"));
            return false;
        }

        return true;
    }

    @Override
    public void handlePatch(Operation patch) {
        CloudUsageReportTaskState currentTask = getState(patch);
        CloudUsageReportTaskState patchBody = getBody(patch);

        if (!validateTransition(patch, currentTask, patchBody)) {
            return;
        }
        updateState(currentTask, patchBody);
        patch.complete();

        switch (patchBody.taskInfo.stage) {
        case STARTED:
            handleSubStage(patchBody);
            break;
        case FINISHED:
            logFine("Task finished successfully");
            break;
        case FAILED:
            logWarning("Task failed: %s", (patchBody.failureMessage == null ? "No reason given"
                    : patchBody.failureMessage));
            break;
        default:
            logWarning("Unexpected stage: %s", patchBody.taskInfo.stage);
            break;
        }
    }

    /**
     * State machine for the task service.
     */
    private void handleSubStage(CloudUsageReportTaskState state) {
        switch (state.subStage) {
        case PROCESS_REQUEST:
            processRequest(state);
            break;
        case QUERY_COMPUTE_DESCRIPTIONS:
            queryComputeDescriptions(state, SubStage.QUERY_COMPUTE_HOST);
            break;
        case QUERY_COMPUTE_HOST:
            queryComputeHost(state, SubStage.SUCCESS);
            break;
        case SUCCESS:
            sendSelfPatch(state, TaskStage.FINISHED, null);
            break;
        default:
            throw new IllegalStateException("Unknown stage encountered: " + state.subStage);
        }
    }

    /**
     * Method to process the incoming querying filters and sort terms.
     */
    private void processRequest(CloudUsageReportTaskState state) {
        // TODO VSYM-1134: Based on sort param chose appropriate state machine.
        state.subStage = SubStage.QUERY_COMPUTE_DESCRIPTIONS;
        sendSelfPatch(state);
    }

    private void queryComputeDescriptions(CloudUsageReportTaskState state, SubStage nextStage) {
        // This query will serve as stage one input to graph query.
        QueryTask queryTask = buildStageOneComputeParentQueryTask(state.tenantLinks);

        Consumer<ServiceDocumentQueryResult> onSuccess = result -> {
            if (result == null || result.nextPageLink == null) {
                state.subStage = nextStage;
                sendSelfPatch(state);
                return;
            }

            // TODO: Extract filters from query spec.
            // We assume that if no environmentName filter is present, then we query for all
            // environments.
            Set<String> environmentFilters = new HashSet<>(Arrays.asList(
                    ComputeDescription.ENVIRONMENT_NAME_AWS,
                    ComputeDescription.ENVIRONMENT_NAME_AZURE
            ));

            QueryCompletionHandler<Set<String>> completionHandler = (descriptionLinks, failure) -> {
                if (failure != null) {
                    handleError(state, failure);
                    return;
                }

                state.descriptionLinks = descriptionLinks;
                state.subStage = nextStage;
                sendSelfPatch(state);
            };

            // Recursively find all compute descriptions matching the environmentFilters
            findComputeDescriptions(this, result.nextPageLink, state.tenantLinks,
                    environmentFilters, completionHandler);

        };

        startQueryTask(this, queryTask, onSuccess, failure -> handleError(state, failure));
    }

    /**
     * Create and execute compute host based query.
     */
    private void queryComputeHost(CloudUsageReportTaskState state, SubStage nextStage) {
        if (state.descriptionLinks == null || state.descriptionLinks.isEmpty()) {
            state.results = new ServiceDocumentQueryResult();
            state.subStage = nextStage;
            sendSelfPatch(state);
            return;
        }

        int resultLimit = (state.filter == null || state.filter.resultLimit == null) ?
                QUERY_RESULT_LIMIT : state.filter.resultLimit;

        QueryTask queryTask = buildComputeParentByDescriptionsQueryTask(state.descriptionLinks,
                state.tenantLinks, resultLimit);

        Consumer<ServiceDocumentQueryResult> onSuccess = queryResult -> {
            ServiceDocumentQueryResult result = new ServiceDocumentQueryResult();
            result.documentCount = queryResult.documentCount;

            if (queryResult.nextPageLink != null && !queryResult.nextPageLink.isEmpty()) {
                CloudUsagePageService pageService = new CloudUsagePageService(
                        queryResult.nextPageLink, state.documentExpirationTimeMicros,
                        state.tenantLinks);

                result.nextPageLink = startStatelessPageService(this,
                        CloudUsagePageService.SELF_LINK_PREFIX,
                        pageService, state.documentExpirationTimeMicros,
                        failure -> handleError(state, failure));
            }

            if (queryResult.prevPageLink != null && !queryResult.prevPageLink.isEmpty()) {
                CloudUsagePageService pageService = new CloudUsagePageService(
                        queryResult.prevPageLink, state.documentExpirationTimeMicros,
                        state.tenantLinks);

                result.prevPageLink = startStatelessPageService(this,
                        CloudUsagePageService.SELF_LINK_PREFIX,
                        pageService, state.documentExpirationTimeMicros,
                        failure -> handleError(state, failure));
            }

            state.results = result;
            state.subStage = nextStage;
            sendSelfPatch(state);
        };

        startQueryTask(this, queryTask, onSuccess, failure -> handleError(state, failure));
    }

    private void handleError(CloudUsageReportTaskState state, Throwable ex) {
        logSevere("Failed at sub-stage %s with exception: %s", state.subStage,
                Utils.toString(ex));
        sendSelfFailurePatch(state, ex.getLocalizedMessage());
    }
}
