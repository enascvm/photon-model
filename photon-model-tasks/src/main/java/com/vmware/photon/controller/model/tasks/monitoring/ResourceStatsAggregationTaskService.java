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

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.monitoring.ResourceAggregateMetricsService;
import com.vmware.photon.controller.model.monitoring.ResourceAggregateMetricsService.ResourceAggregateMetricsState;
import com.vmware.photon.controller.model.tasks.TaskUtils;
import com.vmware.photon.controller.model.tasks.monitoring.ResourceStatsAggregationTaskService.ResourceStatsAggregationTaskState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.ServiceStats.TimeSeriesStats.TimeBin;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskService;

/**
 * Task to do query based resource stats aggregation. The task looks up resources based on the query
 * and then aggregations for the metrics given. Currently, this task only support calculating
 * accumulated value for the metrics.
 */
public class ResourceStatsAggregationTaskService
        extends TaskService<ResourceStatsAggregationTaskState> {

    public static final String FACTORY_LINK = UriPaths.MONITORING + "/resource-stats-aggregation";

    public static final String STATS_QUERY_RESULT_LIMIT =
            UriPaths.PROPERTY_PREFIX + "ResourceStatsAggregationTaskService.query.resultLimit";
    private static final int DEFAULT_QUERY_RESULT_LIMIT = 100;

    public static class ResourceStatsAggregationTaskState extends TaskService.TaskServiceState {

        @Documentation(description = "The resource for which stats aggregation will be performed")
        public String resourceLink;

        @Documentation(description = "The set of metric names to aggregate on")
        public Set<String> metricNames;

        @Documentation(description = "The query to lookup resources for stats aggregation")
        public Query query;

        @Documentation(description = "The auth context links")
        public List<String> tenantLinks;

        /**
         * Sub-stages for the task.
         */
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public SubStage subStage;

        /**
         * cursor for obtaining compute services - this is set for the first time based on
         * the result of a query task and updated on every patch thereafter based on the result
         * object obtained when a GET is issued on the link
         */
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String queryResultLink;

        /**
         * Intermediate aggregated values.
         */
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Map<String, TimeBin> aggregations = new HashMap<>();
    }

    /**
     * Intermediate task stages.
     */
    public enum SubStage {
        /**
         * The initialization stage.
         */
        INIT,

        /**
         * Stage to query resources.
         */
        GET_RESOURCES,

        /**
         * Stage to persist the aggregated values.
         */
        SAVE_STATE
    }

    public ResourceStatsAggregationTaskService() {
        super(ResourceStatsAggregationTaskState.class);
    }

    @Override
    protected void initializeState(ResourceStatsAggregationTaskState task,
            Operation taskOperation) {
        task.subStage = SubStage.INIT;
        super.initializeState(task, taskOperation);
    }

    @Override
    protected ResourceStatsAggregationTaskState validateStartPost(Operation taskOperation) {
        ResourceStatsAggregationTaskState task = super.validateStartPost(taskOperation);

        if (task != null) {
            if (task.subStage != null) {
                taskOperation.fail(
                        new IllegalArgumentException("Do not specify subStage: internal use only"));
                return null;
            }

            if (task.metricNames == null || task.metricNames.isEmpty()) {
                taskOperation.fail(
                        new IllegalArgumentException("metricNames cannot be null or empty"));
                return null;
            }

            if (task.resourceLink == null || task.resourceLink.isEmpty()) {
                taskOperation.fail(
                        new IllegalArgumentException("resourceLink cannot be null or empty"));
                return null;
            }

            if (task.query == null) {
                taskOperation.fail(
                        new IllegalArgumentException("query cannot be null"));
                return null;
            }
        }

        return task;
    }

    @Override
    protected boolean validateTransition(Operation patch,
            ResourceStatsAggregationTaskState currentTask,
            ResourceStatsAggregationTaskState patchBody) {
        super.validateTransition(patch, currentTask, patchBody);
        if (patchBody.taskInfo.stage == TaskStage.STARTED && patchBody.subStage == null) {
            patch.fail(new IllegalArgumentException("Missing sub-stage"));
            return false;
        }

        return true;
    }

    @Override
    public void handlePatch(Operation patch) {
        ResourceStatsAggregationTaskState currentState = getState(patch);
        ResourceStatsAggregationTaskState patchState = getBody(patch);

        if (!validateTransition(patch, currentState, patchState)) {
            return;
        }

        updateState(currentState, patchState);
        patch.setBody(currentState);
        patch.complete();

        switch (currentState.taskInfo.stage) {
        case STARTED:
            handleSubStage(currentState);
            break;
        case FINISHED:
            logFine("Task finished successfully");
            // this is a one shot task, self delete
            sendRequest(Operation.createDelete(getUri()));
            break;
        case CANCELLED:
            logWarning("Task cancelled");
            sendRequest(Operation.createDelete(getUri()));
            break;
        case FAILED:
            logWarning("Task failed: %s", (currentState.failureMessage == null ? "No reason given"
                    : currentState.failureMessage));
            sendRequest(Operation.createDelete(getUri()));
            break;
        default:
            logWarning("Unexpected stage: %s", currentState.taskInfo.stage);
            sendRequest(Operation.createDelete(getUri()));
            break;
        }
    }

    private void handleSubStage(ResourceStatsAggregationTaskState currentState) {
        switch (currentState.subStage) {
        case INIT:
            initializeQuery(currentState);
            break;
        case GET_RESOURCES:
            getResources(currentState);
            break;
        case SAVE_STATE:
            saveState(currentState);
            break;
        default:
            break;
        }
    }

    /**
     * Initialize query from the task state.
     */
    private void initializeQuery(ResourceStatsAggregationTaskState currentState) {
        int resultLimit = Integer.getInteger(STATS_QUERY_RESULT_LIMIT, DEFAULT_QUERY_RESULT_LIMIT);

        Query query = new Query();
        query.addBooleanClause(currentState.query);

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .setQuery(query)
                .setResultLimit(resultLimit)
                .build();

        queryTask.tenantLinks = currentState.tenantLinks;

        sendRequest(Operation.createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(queryTask)
                .setConnectionSharing(true)
                .setCompletion((queryOp, queryEx) -> {
                    if (queryEx != null) {
                        sendSelfFailurePatch(currentState, queryEx.getMessage());
                        return;
                    }

                    QueryTask rsp = queryOp.getBody(QueryTask.class);
                    ResourceStatsAggregationTaskState patchBody = new ResourceStatsAggregationTaskState();

                    if (rsp.results.nextPageLink == null) {
                        patchBody.taskInfo = TaskUtils.createTaskState(TaskStage.FINISHED);
                    } else {
                        patchBody.taskInfo = TaskUtils.createTaskState(TaskStage.STARTED);
                        patchBody.subStage = SubStage.GET_RESOURCES;
                        patchBody.queryResultLink = rsp.results.nextPageLink;
                    }
                    sendSelfPatch(patchBody);
                }));

    }

    /**
     * Gets resources for the given query.
     */
    private void getResources(ResourceStatsAggregationTaskState currentState) {
        sendRequest(Operation
                .createGet(UriUtils.buildUri(getHost(), currentState.queryResultLink))
                .setCompletion(
                        (op, ex) -> {
                            if (ex != null) {
                                sendSelfFailurePatch(currentState, ex.getMessage());
                                return;
                            }

                            QueryTask queryTask = op.getBody(QueryTask.class);
                            if (queryTask.results.documentCount == 0) {
                                ResourceStatsAggregationTaskState patchBody = new ResourceStatsAggregationTaskState();
                                patchBody.taskInfo = TaskUtils.createTaskState(TaskStage.STARTED);
                                patchBody.subStage = SubStage.SAVE_STATE;
                                sendSelfPatch(patchBody);
                                return;
                            }

                            // Get stats for the resources
                            List<Operation> statsOperations = queryTask.results.documentLinks
                                    .stream().map(resourceLink -> Operation.createGet(
                                            UriUtils.buildStatsUri(getHost(), resourceLink))
                                            .setReferer(getHost().getUri()))
                                    .collect(Collectors.toList());

                            OperationJoin.JoinedCompletionHandler joinCompletion = (ops, exc) -> {
                                if (exc != null) {
                                    sendSelfFailurePatch(currentState,
                                            exc.values().iterator().next().getMessage());
                                    return;
                                }
                                aggregateStats(currentState, ops.values(), queryTask);
                            };

                            OperationJoin joinOp = OperationJoin.create(statsOperations);
                            joinOp.setCompletion(joinCompletion);
                            joinOp.sendWith(getHost());
                        }));
    }

    /**
     * Aggregate stats for given metric names and stats values.
     */
    private void aggregateStats(ResourceStatsAggregationTaskState currentState,
            Collection<Operation> statsOps, QueryTask queryTask) {
        Map<String, TimeBin> aggregations = currentState.aggregations;
        for (String metricName : currentState.metricNames) {
            double aggregateValue = 0.0;
            int count = 0;
            for (Operation resultOp : statsOps) {
                if (!resultOp.hasBody()) {
                    continue;
                }

                ServiceStats stats = resultOp.getBody(ServiceStats.class);
                if (stats.documentSelfLink == null) {
                    continue;
                }

                ServiceStat stat = stats.entries.get(metricName);
                if (stat == null || stat.timeSeriesStats == null
                        || stat.timeSeriesStats.bins == null
                        || stat.timeSeriesStats.bins.isEmpty()) {
                    continue;
                }

                // Calculate aggregate value from the latest data point.
                // We can define a behavior enum later to do different kind of aggregation.
                long latestBucket = stat.timeSeriesStats.bins.lastKey();
                aggregateValue += stat.timeSeriesStats.bins.get(latestBucket).avg;
                count++;
            }

            if (count == 0) {
                continue;
            }

            // Here we store the aggregate value across all aggregation types since there is no
            // avg, max, min in this case.
            TimeBin dp = new TimeBin();
            dp.avg = aggregateValue;
            dp.max = aggregateValue;
            dp.min = aggregateValue;
            dp.count = count;

            TimeBin existingDP = aggregations.get(metricName);
            if (existingDP == null) {
                aggregations.put(metricName, dp);
            } else {
                existingDP.avg += dp.avg;
                existingDP.max += dp.max;
                existingDP.min += dp.min;
                existingDP.count += dp.count;
                aggregations.put(metricName, existingDP);
            }
        }

        ResourceStatsAggregationTaskState patchBody = new ResourceStatsAggregationTaskState();
        patchBody.taskInfo = TaskUtils.createTaskState(TaskStage.STARTED);
        patchBody.aggregations = aggregations;

        if (queryTask.results.nextPageLink == null) {
            patchBody.subStage = SubStage.SAVE_STATE;
        } else {
            patchBody.subStage = SubStage.GET_RESOURCES;
            patchBody.queryResultLink = queryTask.results.nextPageLink;
        }
        sendSelfPatch(patchBody);
    }

    /**
     * Saves metric aggregations state to {@link ResourceAggregateMetricsService}.
     */
    private void saveState(ResourceStatsAggregationTaskState currentState) {
        if (currentState.aggregations.isEmpty()) {
            sendSelfPatch(currentState, TaskStage.FINISHED, null);
            return;
        }

        ResourceAggregateMetricsState aggrMetricState = new ResourceAggregateMetricsState();
        aggrMetricState.computeServiceLink = currentState.resourceLink;
        aggrMetricState.aggregations = currentState.aggregations;
        aggrMetricState.sourceTimeMicrosUtc = Utils.getNowMicrosUtc();
        aggrMetricState.tenantLinks = currentState.tenantLinks;
        aggrMetricState.documentSelfLink =
                UriUtils.getLastPathSegment(aggrMetricState.computeServiceLink) +
                        "-" +
                        Long.toString(aggrMetricState.sourceTimeMicrosUtc);

        Operation.createPost(UriUtils.buildUri(getHost(),
                ResourceAggregateMetricsService.FACTORY_LINK))
                .setBody(aggrMetricState)
                .setReferer(getHost().getUri())
                .setCompletion((completedOp, failure) -> {
                    if (failure != null) {
                        sendSelfFailurePatch(currentState, failure.getMessage());
                        return;
                    }
                    sendSelfPatch(currentState, TaskStage.FINISHED, null);
                })
                .sendWith(this);
    }
}