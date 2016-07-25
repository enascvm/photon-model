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


import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.monitoring.ResourceAggregateMetricsService;
import com.vmware.photon.controller.model.monitoring.ResourceAggregateMetricsService.ResourceAggregateMetricsState;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.tasks.TaskUtils;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.ServiceStats.TimeSeriesStats.DataPoint;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskService;

/**
 * Task to aggregate resource stats. Resources associated with a resource pool are
 * queried in a paginated fashion and the in memory stats associated with a resource
 * is fetched and persisted for the resource
 */
public class StatsAggregationTaskService extends TaskService<StatsAggregationTaskService.StatsAggregationTaskState> {

    public static final String FACTORY_LINK = UriPaths.MONITORING + "/stats-aggregation";

    public static final String STATS_QUERY_RESULT_LIMIT = UriPaths.PROPERTY_PREFIX + "StatsAggregationTaskService.query.resultLimit";
    private static final int DEFAULT_QUERY_RESULT_LIMIT = 100;

    public static class StatsAggregationTaskState extends TaskService.TaskServiceState {

        @Documentation(description = "Resource pool to invoke stats aggregation on")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String resourcePoolLink;

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
    public void handleStart(Operation start) {
        try {
            if (!start.hasBody()) {
                start.fail(new IllegalArgumentException("body is required"));
                return;
            }
            StatsAggregationTaskState state = start
                    .getBody(StatsAggregationTaskState.class);

            validateState(state);
            state.taskInfo = TaskUtils.createTaskState(TaskStage.CREATED);
            start.setBody(state)
                .setStatusCode(Operation.STATUS_CODE_ACCEPTED)
                .complete();
            state.taskInfo = TaskUtils.createTaskState(TaskStage.STARTED);
            state.taskStage = StatsAggregationStage.INIT;
            sendSelfPatch(state);
        } catch (Throwable e) {
            start.fail(e);
        }
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
        case CREATED:
            break;
        case STARTED:
            handleStagePatch(patch, currentState);
            break;
        case FINISHED:
        case FAILED:
        case CANCELLED:
            // this is a one shot task, self delete
            sendRequest(Operation
                    .createDelete(getUri()));
            break;
        default:
            break;
        }
    }

    private void validateState(StatsAggregationTaskState state) {
        if (state.resourcePoolLink == null) {
            throw new IllegalStateException("resourcePoolLink should not be null");
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
        Query query = Query.Builder.create()
                .addKindFieldClause(ComputeService.ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_RESOURCE_POOL_LINK, currentState.resourcePoolLink)
                .build();
        QueryTask.Builder queryTaskBuilder = QueryTask.Builder.createDirectTask()
                .setQuery(query).setResultLimit(resultLimit).addOption(QueryOption.EXPAND_CONTENT);

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
                            List<Operation> statsOperations = new ArrayList<Operation>();
                            for (String computeLink: page.results.documentLinks) {
                                statsOperations.add(Operation.createGet(UriUtils.buildStatsUri(getHost(), computeLink))
                                        .setReferer(getHost().getUri()));
                            }
                            OperationJoin.JoinedCompletionHandler joinCompletion = (ops,exc) -> {
                                if (exc != null) {
                                    sendSelfFailurePatch(currentState, exc.values().iterator().next().getMessage());
                                    return;
                                }
                                createResourceAggregationStats(op, currentState, ops.values(), page);
                            };
                            OperationJoin joinOp = OperationJoin.create(statsOperations);
                            joinOp.setCompletion(joinCompletion);
                            joinOp.sendWith(getHost());
                        }));
    }

    private long normalizeTimestamp(long timestampMicros, long bucketDurationMillis) {
        long timeMillis = TimeUnit.MICROSECONDS.toMillis(timestampMicros);
        timeMillis -= (timeMillis % bucketDurationMillis);
        return timeMillis;
    }

    private ResourceAggregateMetricsState buildResourceAggregateMetricsState(String computeServiceLink,
            List<String> tenantLinks, long bucketForInterval) {
        ResourceAggregateMetricsState aggrMetricState = new ResourceAggregateMetricsState();
        aggrMetricState.computeServiceLink = computeServiceLink;
        aggrMetricState.aggregations = new HashMap<String, DataPoint>();
        aggrMetricState.sourceTimeMicrosUtc = TimeUnit.MILLISECONDS.toMicros(bucketForInterval);
        aggrMetricState.tenantLinks = tenantLinks;
        aggrMetricState.documentSelfLink = new StringBuilder().append(UriUtils.getLastPathSegment(computeServiceLink))
                .append("-").append(Long.toString(bucketForInterval)).toString();
        return aggrMetricState;
    }

    private void createResourceAggregationStats(Operation op, StatsAggregationTaskState currentState,
            Collection<Operation> statsOps, QueryTask page) {
        List<Operation> postResourceOps = new ArrayList<Operation>();
        Map<String, List<String>> computeToTenantLinksMap = new HashMap<String, List<String>>();
        for (Object computeServiceObject: page.results.documents.values()) {
            ComputeState computeState = Utils.fromJson(computeServiceObject, ComputeState.class);
            computeToTenantLinksMap.put(computeState.documentSelfLink, computeState.tenantLinks);
        }
        for (Operation resultOp : statsOps) {
            if (!resultOp.hasBody()) {
                continue;
            }
            ServiceStats stats = resultOp.getBody(ServiceStats.class);
            if (stats.documentSelfLink == null) {
                continue;
            }
            String computeServiceLink = stats.documentSelfLink.replace(ServiceHost.SERVICE_URI_SUFFIX_STATS, "");
            // normalize the collection time such that it aligns with a bucket boundary used for time series stats
            long currentTime = Utils.getNowMicrosUtc();
            // we are dealing with hourly buckets at this time; this scheme can be extended to deal with other
            // bucket ranges (day, week etc. as well)
            long bucketForCurrentInterval = normalizeTimestamp(currentTime, StatsConstants.BUCKET_SIZE_HOURS_IN_MILLS);
            long bucketForPreviousInterval = normalizeTimestamp(currentTime - TimeUnit.HOURS.toMicros(1),
                    StatsConstants.BUCKET_SIZE_HOURS_IN_MILLS);
            ResourceAggregateMetricsState aggrMetricStateForCurrentInterval =
                    buildResourceAggregateMetricsState(computeServiceLink, computeToTenantLinksMap.get(computeServiceLink),
                            bucketForCurrentInterval);
            ResourceAggregateMetricsState aggrMetricStateForPreviousInterval =
                    buildResourceAggregateMetricsState(computeServiceLink, computeToTenantLinksMap.get(computeServiceLink),
                            bucketForPreviousInterval);
            for (Entry<String, ServiceStat> entry : stats.entries.entrySet() ) {
                ServiceStat stat = entry.getValue();
                // if the stat is maintained at a minute granularity, skip it as we don't want to aggregate the time
                // series data at this granularity
                if (stat.name.endsWith(StatsConstants.MIN_SUFFIX)) {
                    continue;
                }
                if (!stat.timeSeriesStats.dataPoints.isEmpty()) {
                    long latestBucket = stat.timeSeriesStats.dataPoints.lastKey();
                    if (latestBucket == bucketForCurrentInterval) {
                        aggrMetricStateForCurrentInterval.aggregations.put(entry.getKey(), stat.timeSeriesStats.dataPoints.get(latestBucket));
                    }
                    // remove the latest bucket, so that we can look the the closed bucket for the last interval
                    stat.timeSeriesStats.dataPoints.remove(latestBucket);
                }
                // we really need to do this only if the task ran for the first time after the active bucket changed;
                // doing this unconditionally for now and can be optimized later
                if (!stat.timeSeriesStats.dataPoints.isEmpty()) {
                    long previousBucket = stat.timeSeriesStats.dataPoints.lastKey();
                    if (previousBucket == bucketForPreviousInterval) {
                        aggrMetricStateForPreviousInterval.aggregations.put(entry.getKey(), stat.timeSeriesStats.dataPoints.get(previousBucket));
                    }
                }
            }
            postResourceOps.add(
                    Operation.createPost(UriUtils.buildUri(getHost(), ResourceAggregateMetricsService.FACTORY_LINK))
                    .setBody(aggrMetricStateForCurrentInterval)
                    .setReferer(getHost().getUri()));
            postResourceOps.add(
                    Operation.createPost(UriUtils.buildUri(getHost(), ResourceAggregateMetricsService.FACTORY_LINK))
                    .setBody(aggrMetricStateForPreviousInterval)
                    .setReferer(getHost().getUri()));
        }
        if (postResourceOps.size() == 0) {
            StatsAggregationTaskState patchBody = new StatsAggregationTaskState();
            if (page.results.nextPageLink == null) {
                patchBody.taskInfo = TaskUtils.createTaskState(TaskStage.FINISHED);
            } else {
                patchBody.taskInfo = TaskUtils.createTaskState(TaskStage.STARTED);
                patchBody.taskStage = StatsAggregationStage.GET_RESOURCES;
                patchBody.queryResultLink = page.results.nextPageLink;
            }
            sendSelfPatch(patchBody);
            return;
        }
        OperationJoin.JoinedCompletionHandler postJoinCompletion = (postOps,
                postExList) -> {
            if (postExList != null) {
                sendSelfFailurePatch(currentState, postExList.values().iterator().next().getMessage());
                return;
            }
            StatsAggregationTaskState patchBody = new StatsAggregationTaskState();
            if (page.results.nextPageLink == null) {
                patchBody.taskInfo = TaskUtils.createTaskState(TaskStage.FINISHED);
            } else {
                patchBody.taskInfo = TaskUtils.createTaskState(TaskStage.STARTED);
                patchBody.taskStage = StatsAggregationStage.GET_RESOURCES;
                patchBody.queryResultLink = page.results.nextPageLink;
            }
            sendSelfPatch(patchBody);
        };
        OperationJoin postJoinOp = OperationJoin.create(postResourceOps);
        postJoinOp.setCompletion(postJoinCompletion);
        postJoinOp.sendWith(getHost());
    }
}
