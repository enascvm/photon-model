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

package com.vmware.photon.controller.discovery.cloudusage.metrics;

import static com.vmware.photon.controller.discovery.queries.ProjectQueries.getProjects;
import static com.vmware.photon.controller.model.UriPaths.CLOUD_METRICS_QUERY_TASK_SERVICE;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.vmware.photon.controller.discovery.cloudaccount.utils.QueryHelper;
import com.vmware.photon.controller.discovery.cloudusage.metrics.CloudMetricsQueryTaskService.CloudMetricsQueryTaskState;
import com.vmware.photon.controller.discovery.common.services.UserContextQueryService;
import com.vmware.photon.controller.discovery.common.services.UserContextQueryService.UserContext;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.TaskService;

/**
 * - This is a task service to query for the metrics (CPU utilization, memory, storage etc)
 * across different cloud endpoints in symphony.
 * - In addition the service performs aggregation of values on a per cloud level.
 * - The core value addition of this service to pull the stats information for different cloud endpoints,
 * group the information by projects and further aggregate it by different cloud types and
 * return it to the end user in a paginated and easy to consume PODO where each of the attributes is a first
 * class citizen.
 *
 * This Cloud Endpoint Task Service has the following workflow
 *
 * 1. Client does a POST to the task factory service to create a task service.
 * 2. The task is kicked off and a URL is returned to the end user. The end user
 *    can poll for completion on the URL or subscribe to notifications.
 * 3. Once the task is completed successfully, a result object will be populated.
 *    This result object will have links to the {@link CloudMetricsQueryPageService} prev
 *    and next pages.
 * 4. Once the end user queries the {@link CloudMetricsQueryPageService}  using the URI returned above, the complete
 *    workflow is triggered to query the different documents and populate a CloudMetricsReport PODO.
 * 5. The CloudMetricPageService queries the underlying data store for endpoints, stats, projects, performs aggregation
 *    and persists them and creates the result {ServiceDocumentQueryResult} which is returned to the end user.
 *
 */
public class CloudMetricsQueryTaskService
        extends TaskService<CloudMetricsQueryTaskState> {

    public static final String PROPERTY_NAME_QUERY_RESULT_LIMIT = UriPaths.PROPERTY_PREFIX
            + "CloudMetricsQueryTaskService.QUERY_RESULT_LIMIT";
    private static final int QUERY_RESULT_LIMIT = 50;

    /**
     * The sub stages handled by this task service. It will primarily hand off control to a stateless
     * service to perform the low level querying. Once a response is received from the underlying service.
     * It will perform a self patch to mark completion of the task.
     */
    public enum SubStage {
        QUERY_PROJECTS, SUCCESS
    }

    public static final String FACTORY_LINK = CLOUD_METRICS_QUERY_TASK_SERVICE;


    /**
     * The state for the cloud metrics task state.
     */
    public static class CloudMetricsQueryTaskState extends TaskService.TaskServiceState {
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public SubStage subStage;

        @Documentation(description = "The query specification to perform filtering.")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL })
        public QuerySpecification filter;

        @Documentation(description = "The compute host result set")
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public ServiceDocumentQueryResult results;

        @Documentation(description = "The authorization links associated with this request.")
        public List<String> tenantLinks;
    }

    public CloudMetricsQueryTaskService() {
        super(CloudMetricsQueryTaskState.class);
        toggleOption(ServiceOption.REPLICATION, true);
        toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    /**
     * Ensure that the input task is valid. At the time of creation, the substage should not be set.
     */
    @Override
    protected CloudMetricsQueryTaskState validateStartPost(Operation taskOperation) {
        CloudMetricsQueryTaskState task = super.validateStartPost(taskOperation);
        if (task == null) {
            return null;
        }
        if (ServiceHost.isServiceCreate(taskOperation)) {
            if (task.subStage != null) {
                taskOperation.fail(
                        new IllegalArgumentException("Do not specify subStage: internal use only"));
                return null;
            }
        }
        return task;
    }

    /**
     * Validate that the PATCH we got requests reasonable changes to our state
     */
    @Override
    protected boolean validateTransition(Operation patch, CloudMetricsQueryTaskState currentTask,
            CloudMetricsQueryTaskState patchBody) {
        super.validateTransition(patch, currentTask, patchBody);
        if (patchBody.taskInfo.stage == TaskStage.STARTED && patchBody.subStage == null) {
            patch.fail(new IllegalArgumentException("Missing substage"));
            return false;
        }
        if (currentTask.taskInfo != null && currentTask.taskInfo.stage != null) {
            if (currentTask.taskInfo.stage == TaskStage.STARTED
                    && patchBody.taskInfo.stage == TaskStage.STARTED) {
                if (currentTask.subStage.ordinal() > patchBody.subStage.ordinal()) {
                    patch.fail(new IllegalArgumentException("Task substage cannot move backwards"));
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Populates the tenant links from the user context in case they are not supplied by the end user at the
     * time of invoking the task service.
     */
    @Override
    public void handleStart(Operation taskOperation) {
        CloudMetricsQueryTaskState state;
        if (!taskOperation.hasBody()) {
            state = new CloudMetricsQueryTaskState();
            state.taskInfo = TaskState.create();
            taskOperation.setBody(state);
        }

        CloudMetricsQueryTaskState body = taskOperation.getBody(CloudMetricsQueryTaskState.class);

        if (body.tenantLinks != null && !body.tenantLinks.isEmpty()) {
            super.handleStart(taskOperation);
            return;
        }

        Operation.createGet(this, UserContextQueryService.SELF_LINK)
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        taskOperation.fail(ex);
                        return;
                    }

                    UserContext userContext = op.getBody(UserContext.class);
                    body.tenantLinks = userContext.projects.stream()
                            .map(resourceGroupState -> resourceGroupState.documentSelfLink)
                            .collect(Collectors.toList());
                    taskOperation.setBody(body);
                    super.handleStart(taskOperation);

                })
                .sendWith(this);

    }

    /**
     * Initialize the task
     *
     */
    @Override
    protected void initializeState(CloudMetricsQueryTaskState task, Operation taskOperation) {
        task.subStage = SubStage.QUERY_PROJECTS;
        super.initializeState(task, taskOperation);
    }

    /**
     * Handle PATCH
     * It queries the underlying compute host query service, and when that completes
     * it updates the task state and progresses to the next step by doing a self PATCH.
     *
     */
    @Override
    public void handlePatch(Operation patch) {
        CloudMetricsQueryTaskState currentTask = getState(patch);
        CloudMetricsQueryTaskState patchBody = getBody(patch);

        if (!validateTransition(patch, currentTask, patchBody)) {
            return;
        }
        updateState(currentTask, patchBody);
        patch.complete();

        switch (patchBody.taskInfo.stage) {
        case STARTED:
            handleSubstage(patchBody);
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
     * Handles the sub stages for this task
     */
    private void handleSubstage(CloudMetricsQueryTaskState state) {
        switch (state.subStage) {
        case QUERY_PROJECTS:
            queryProjects(state, SubStage.SUCCESS);
            break;
        case SUCCESS:
            sendSelfPatch(state, TaskStage.FINISHED, null);
            break;
        default:
            logWarning("Unexpected sub stage: %s", state.subStage);
            break;
        }
    }

    /**
     * Gets the project links for the given user from the user context.
     */
    private void queryProjects(CloudMetricsQueryTaskState state, SubStage next) {
        Operation.createGet(this, UserContextQueryService.SELF_LINK)
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        handleError(state, ex);
                        return;
                    }

                    UserContext userContext = op.getBody(UserContext.class);

                    Set<String> projectLinks = userContext.projects.stream()
                            .map(resourceGroupState -> resourceGroupState.documentSelfLink)
                            .collect(Collectors.toSet());

                    int resultLimit = (state.filter == null || state.filter.resultLimit == null)
                            ? Integer.getInteger(PROPERTY_NAME_QUERY_RESULT_LIMIT,
                                    QUERY_RESULT_LIMIT)
                            : state.filter.resultLimit;

                    getProjects(this, projectLinks, resultLimit,
                            (queryResult, e) -> {
                                if (e != null) {
                                    handleError(state, e);
                                    return;
                                }

                                ServiceDocumentQueryResult results = new ServiceDocumentQueryResult();
                                results.documentCount = queryResult.documentCount;

                                if (queryResult.nextPageLink != null && !queryResult.nextPageLink
                                        .isEmpty()) {
                                    CloudMetricsQueryPageService pageService = new CloudMetricsQueryPageService(
                                            queryResult.nextPageLink,
                                            state.documentExpirationTimeMicros,
                                            state.tenantLinks);

                                    results.nextPageLink = QueryHelper
                                            .startStatelessPageService(this,
                                                    CloudMetricsQueryPageService.SELF_LINK_PREFIX,
                                                    pageService,
                                                    state.documentExpirationTimeMicros,
                                                    failure -> handleError(state, failure));
                                }

                                if (queryResult.prevPageLink != null && !queryResult.prevPageLink
                                        .isEmpty()) {
                                    CloudMetricsQueryPageService pageService = new CloudMetricsQueryPageService(
                                            queryResult.prevPageLink,
                                            state.documentExpirationTimeMicros,
                                            state.tenantLinks);

                                    results.prevPageLink = QueryHelper
                                            .startStatelessPageService(this,
                                                    CloudMetricsQueryPageService.SELF_LINK_PREFIX,
                                                    pageService,
                                                    state.documentExpirationTimeMicros,
                                                    failure -> handleError(state, failure));
                                }

                                state.results = results;
                                state.subStage = next;
                                sendSelfPatch(state);
                            });

                })
                .sendWith(this);
    }

    private void handleError(CloudMetricsQueryTaskState state, Throwable ex) {
        logSevere("Failed at sub-stage %s with exception: %s", state.subStage,
                Utils.toString(ex));
        sendSelfFailurePatch(state, ex.getLocalizedMessage());
    }

}
