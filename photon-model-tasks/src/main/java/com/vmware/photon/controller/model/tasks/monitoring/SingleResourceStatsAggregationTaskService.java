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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.monitoring.ResourceAggregateMetricService;
import com.vmware.photon.controller.model.monitoring.ResourceAggregateMetricService.ResourceAggregateMetric;
import com.vmware.photon.controller.model.monitoring.ResourceMetricService;
import com.vmware.photon.controller.model.monitoring.ResourceMetricService.ResourceMetric;
import com.vmware.photon.controller.model.tasks.TaskUtils;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.OperationSequence;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceStats.TimeSeriesStats.TimeBin;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.NumericRange;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskService;

/**
 * Task to aggregate resource stats for a single resource. Aggregate stats are backed by ResourceAggregateMetricService
 * instances. Aggregate metrics are identified using a key that is a combination of the resourceId and metricKey. This means
 * that the same service document is written to on every rollup resulting in the documents being versioned over time. Queries
 * for aggregate metrics need to issue a query to include all versions and filter by date
 *
 * Aggregation operations can run multiple time within a time window. This will result in multiple versions of the document for
 * the time window. The one with the highest version represent the aggregate value after the time interval has closed. All
 * other versions represent point in time aggregates for the collection interval
 *
 * All aggregate metrics have a timestamp that represents the end of the interval. For example if the aggregation is for hourly
 * data and the interval is 10-11, the aggregate value will have a timestamp of 11
 *
 * Aggregations are based on the the query that is specified. All resources that resolve to the query will be used for aggregation.
 * If no query is specified the aggregation will happen on the resource specified in resourceLink
 */
public class SingleResourceStatsAggregationTaskService extends TaskService<SingleResourceStatsAggregationTaskService.SingleResourceStatsAggregationTaskState> {

    public static final String FACTORY_LINK = UriPaths.MONITORING + "/single-resource-stats-aggregation";

    public static final String STATS_QUERY_RESULT_LIMIT =
            UriPaths.PROPERTY_PREFIX + "SingleResourceStatsAggregationTaskService.query.resultLimit";
    private static final int DEFAULT_QUERY_RESULT_LIMIT = 100;

    public static class SingleResourceStatsAggregationTaskState extends TaskService.TaskServiceState {

        @Documentation(description = "Resource to invoke stats aggregation on")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String resourceLink;

        @Documentation(description = "The set of metric names to aggregate on")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public Set<String> metricNames;

        @Documentation(description = "The query to lookup resources for stats aggregation."
                + " If no query is specified, the aggregation is performed on the resource"
                + " identified by the resourceLink parameter")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Query query;

        @Documentation(description = "Task to patch back to")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String parentLink;

        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        @UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public StatsAggregationStage taskStage;

        // the latest time the metric was rolled up
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        @UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Map<String, Long> lastRollupTimeForMetric;

        // aggregated metrics by timestamp and metric key
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        @UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Map<String, Map<Long, TimeBin>> aggregatedTimeBinMap;

        //cursor for obtaining compute services - this is set for the first time based on
        //the result of a query task and updated on every patch thereafter based on the result
        //object obtained when a GET is issued on the link
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String queryResultLink;
    }

    public enum StatsAggregationStage {
       GET_LAST_ROLLUP_TIME, INIT_RESOURCE_QUERY, PROCESS_RESOURCES, PUBLISH_METRICS
    }

    public SingleResourceStatsAggregationTaskService() {
        super(SingleResourceStatsAggregationTaskState.class);
    }

    @Override
    protected SingleResourceStatsAggregationTaskState validateStartPost(Operation postOp) {
        SingleResourceStatsAggregationTaskState state = super.validateStartPost(postOp);
        if (state == null) {
            return null;
        }
        if (state.resourceLink == null) {
            postOp.fail(new IllegalArgumentException("resourceLink needs to be specified"));
            return null;
        }
        if (state.metricNames == null || state.metricNames.isEmpty()) {
            postOp.fail(new IllegalArgumentException("metricNames needs to be specified"));
            return null;
        }
        if (state.query == null) {
            state.query = Query.Builder.create().addFieldClause(
                   ServiceDocument.FIELD_NAME_SELF_LINK, state.resourceLink).build();
        }
        return state;
    }

    @Override
    protected void initializeState(SingleResourceStatsAggregationTaskState state, Operation postOp) {
        super.initializeState(state, postOp);
        state.taskStage = StatsAggregationStage.GET_LAST_ROLLUP_TIME;
    }

    @Override
    public void handlePatch(Operation patch) {
        SingleResourceStatsAggregationTaskState currentState = getState(patch);
        SingleResourceStatsAggregationTaskState patchState = getBody(patch);
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
            if (currentState.parentLink != null) {
                sendRequest(Operation
                        .createPatch(UriUtils.buildUri(getHost(), currentState.parentLink))
                        .setBody(currentState)
                        .setCompletion(
                            (patchOp, patchEx) -> {
                                if (patchEx != null) {
                                    logWarning("Patching parent task failed %s",
                                            Utils.toString(patchEx));
                                }
                            }));
            }
            break;
        default:
            break;
        }
    }

    private void handleStagePatch(Operation op, SingleResourceStatsAggregationTaskState currentState) {
        switch (currentState.taskStage) {
        case GET_LAST_ROLLUP_TIME:
            getLastRollupTime(op, currentState);
            break;
        case INIT_RESOURCE_QUERY:
            initializeQuery(op, currentState);
            break;
        case PROCESS_RESOURCES:
            getResources(op, currentState);
            break;
        case PUBLISH_METRICS:
            publishMetrics(op, currentState);
            break;
        default:
            break;
        }
    }

    private void getLastRollupTime(Operation op, SingleResourceStatsAggregationTaskState currentState) {
        Query.Builder builder = Query.Builder.create();
        builder.addKindFieldClause(ResourceAggregateMetric.class);
        Map<String, Long> lastUpdateMap = new HashMap<String, Long>();
        // for all metrics, get the last time rollup happened
        for (String metricName : currentState.metricNames) {
            List<String> rollupKeys = buildRollupKeys(metricName);
            for (String rollupKey : rollupKeys) {
                String serviceKey = buildMetricKey(UriUtils.getLastPathSegment(currentState.resourceLink), rollupKey);
                lastUpdateMap.put(rollupKey, null);
                builder.addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                        UriUtils.buildUriPath(ResourceAggregateMetricService.FACTORY_LINK, serviceKey), Occurance.SHOULD_OCCUR);
            }
        }
        sendRequest(Operation.createPost(getHost(), ServiceUriPaths.CORE_QUERY_TASKS)
                            .setBody(QueryTask.Builder.createDirectTask()
                                    .addOption(QueryOption.BROADCAST)
                                    .addOption(QueryOption.EXPAND_CONTENT)
                                    .setQuery(builder.build()).build())
                            .setConnectionSharing(true)
                            .setCompletion((queryResultOp, queryEx) -> {
                                if (queryEx != null) {
                                    sendSelfFailurePatch(currentState, queryEx.getMessage());
                                    return;
                                }
                                QueryTask rsp = queryResultOp.getBody(QueryTask.class);
                                for (Object aggrObj : rsp.results.documents.values()) {
                                    ResourceAggregateMetric aggrState = Utils.fromJson(aggrObj, ResourceAggregateMetric.class);
                                    lastUpdateMap.replace(getMetricKey(UriUtils.getLastPathSegment(aggrState.documentSelfLink),
                                            UriUtils.getLastPathSegment(currentState.resourceLink)),
                                            aggrState.currentIntervalTimeStampMicrosUtc);
                                }
                                SingleResourceStatsAggregationTaskState patchBody = new SingleResourceStatsAggregationTaskState();
                                patchBody.taskInfo = TaskUtils.createTaskState(TaskStage.STARTED);
                                patchBody.taskStage = StatsAggregationStage.INIT_RESOURCE_QUERY;
                                patchBody.lastRollupTimeForMetric = lastUpdateMap;
                                sendSelfPatch(patchBody);
                            }));
    }

    /**
     * Initialize query from the task state.
     */
    private void initializeQuery(Operation op, SingleResourceStatsAggregationTaskState currentState) {
        int resultLimit = Integer.getInteger(STATS_QUERY_RESULT_LIMIT, DEFAULT_QUERY_RESULT_LIMIT);
        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .setQuery(currentState.query)
                .setResultLimit(resultLimit)
                .build();


        sendRequest(Operation.createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(queryTask)
                .setConnectionSharing(true)
                .setCompletion((queryOp, queryEx) -> {
                    if (queryEx != null) {
                        sendSelfFailurePatch(currentState, queryEx.getMessage());
                        return;
                    }

                    QueryTask rsp = queryOp.getBody(QueryTask.class);
                    SingleResourceStatsAggregationTaskState patchBody = new SingleResourceStatsAggregationTaskState();
                    if (rsp.results.nextPageLink == null) {
                        patchBody.taskInfo = TaskUtils.createTaskState(TaskStage.FINISHED);
                    } else {
                        patchBody.taskInfo = TaskUtils.createTaskState(TaskStage.STARTED);
                        patchBody.taskStage = StatsAggregationStage.PROCESS_RESOURCES;
                        patchBody.queryResultLink = rsp.results.nextPageLink;
                    }
                    sendSelfPatch(patchBody);
                }));
    }

    /**
     * Gets resources for the given query, fetch raw metrics and compute partial aggregations
     * as one step
     */
    private void getResources(Operation op, SingleResourceStatsAggregationTaskState currentState) {
        sendRequest(Operation
                .createGet(UriUtils.buildUri(getHost(), currentState.queryResultLink))
                .setCompletion(
                        (getOp, getEx) -> {
                            if (getEx != null) {
                                sendSelfFailurePatch(currentState, getEx.getMessage());
                                return;
                            }
                            QueryTask queryTask = getOp.getBody(QueryTask.class);
                            if (queryTask.results.documentCount == 0) {
                                currentState.taskStage = StatsAggregationStage.PUBLISH_METRICS;
                                sendSelfPatch(currentState);
                                return;
                            }
                            getRawMetrics(op, currentState, queryTask);
                        }));
    }

    private void getRawMetrics(Operation op, SingleResourceStatsAggregationTaskState currentState, QueryTask resourceQueryTask) {
        Set<Operation> rawMericQueryOps = new HashSet<Operation>();
        Map<String, String> taskUUIDToMetricKeyMap = new HashMap<String, String>();
        for (String resourceLink : resourceQueryTask.results.documentLinks) {
            for (Entry<String, Long> metricEntry : currentState.lastRollupTimeForMetric.entrySet()) {
                String resourceRawMetricKey = buildRawMetricKey(resourceLink, metricEntry.getKey());
                Query.Builder builder = Query.Builder.create();
                builder.addKindFieldClause(ResourceMetric.class);
                builder.addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                        UriUtils.buildUriPath(ResourceMetricService.FACTORY_LINK, resourceRawMetricKey));
                if (metricEntry.getValue() != null) {
                    builder.addRangeClause(ResourceMetric.FIELD_NAME_TIMESTAMP,
                            NumericRange.createGreaterThanOrEqualRange(
                                    computeIntervalBegin((metricEntry.getValue() - 1) , lookupBinSize(metricEntry.getKey()))));
                }
                QueryTask task = QueryTask.Builder.createDirectTask()
                        .addOption(QueryOption.BROADCAST)
                        .addOption(QueryOption.EXPAND_CONTENT)
                        .addOption(QueryOption.INCLUDE_ALL_VERSIONS)
                        .setQuery(builder.build()).build();
                String taskId = UUID.randomUUID().toString();
                task.documentSelfLink = taskId;
                Operation queryOp = Operation.createPost(getHost(), ServiceUriPaths.CORE_QUERY_TASKS)
                        .setBody(task)
                        .setConnectionSharing(true);
                rawMericQueryOps.add(queryOp);
                taskUUIDToMetricKeyMap.put(taskId, metricEntry.getKey());
            }
        }
        OperationJoin.JoinedCompletionHandler postJoinCompletion = (postOps,
                postExList) -> {
            if (postExList != null) {
                sendSelfFailurePatch(currentState, postExList.values().iterator().next().getMessage());
                return;
            }
            Map<String, List<ResourceMetric>> rawMetricsForKey = new HashMap<>();
            for (Operation resultOp : postOps.values()) {
                QueryTask rsp = resultOp.getBody(QueryTask.class);
                String taskId = UriUtils.getLastPathSegment(rsp.documentSelfLink);
                String metricKey = taskUUIDToMetricKeyMap.get(taskId);
                List<ResourceMetric> rawMetricResultSet = rawMetricsForKey.get(metricKey);
                if (rawMetricResultSet == null) {
                    rawMetricResultSet = new ArrayList<ResourceMetric>();
                    rawMetricsForKey.put(metricKey, rawMetricResultSet);
                }
                for (Object aggrObj : rsp.results.documents.values()) {
                    ResourceMetric rawMetric = Utils.fromJson(aggrObj, ResourceMetric.class);
                    rawMetricResultSet.add(rawMetric);
                }
            }
            aggregateMetrics(op, currentState, resourceQueryTask, rawMetricsForKey);
        };
        OperationJoin postJoinOp = OperationJoin.create(rawMericQueryOps);
        postJoinOp.setCompletion(postJoinCompletion);
        postJoinOp.sendWith(this);
    }

    private void aggregateMetrics(Operation op, SingleResourceStatsAggregationTaskState currentState,
            QueryTask resourceQueryTask, Map<String, List<ResourceMetric>> rawMetricsForKey) {
        // comparator used to sort resource metric PODOs based on document timestamp
        Comparator<ResourceMetric> comparator = new Comparator<ResourceMetric>() {
            @Override
            public int compare(ResourceMetric o1, ResourceMetric o2) {
                if (o1.timestampMicrosUtc < o2.timestampMicrosUtc) {
                    return -1;
                } else if (o1.timestampMicrosUtc > o2.timestampMicrosUtc) {
                    return 1;
                }
                return 0;
            }
        };
        Map<String, Map<Long, TimeBin>> aggregatedTimeBinMap = currentState.aggregatedTimeBinMap;
        for (Entry<String, List<ResourceMetric>> rawMetricListEntry : rawMetricsForKey.entrySet()) {
            List<ResourceMetric> rawMetricList = rawMetricListEntry.getValue();
            String metricKey = rawMetricListEntry.getKey();
            Collections.sort(rawMetricList, comparator);

            if (aggregatedTimeBinMap == null) {
                aggregatedTimeBinMap = new HashMap<>();
            }
            Map<Long, TimeBin> timeBinMap = aggregatedTimeBinMap.get(metricKey);
            if (timeBinMap == null) {
                timeBinMap = new HashMap<Long, TimeBin>();
                aggregatedTimeBinMap.put(metricKey, timeBinMap);
            }
            // iterate over the raw metric values and place it in the right time bin
            for (ResourceMetric metric : rawMetricList) {
                long binId = computeIntervalEnd(metric.timestampMicrosUtc, lookupBinSize(metricKey));
                TimeBin bin = timeBinMap.get(binId);
                if (bin == null) {
                    bin = new TimeBin();
                }
                updateBin(bin, metric.value);
                timeBinMap.put(binId, bin);
            }
        }
        SingleResourceStatsAggregationTaskState patchBody = new SingleResourceStatsAggregationTaskState();
        patchBody.taskInfo = TaskUtils.createTaskState(TaskStage.STARTED);
        patchBody.aggregatedTimeBinMap = aggregatedTimeBinMap;
        if (resourceQueryTask.results.nextPageLink == null) {
            patchBody.taskStage = StatsAggregationStage.PUBLISH_METRICS;
        } else {
            patchBody.taskStage = StatsAggregationStage.PROCESS_RESOURCES;
            patchBody.queryResultLink = resourceQueryTask.results.nextPageLink;
        }
        sendSelfPatch(patchBody);
    }

    // publish aggregate metric values
    private void publishMetrics(Operation op, SingleResourceStatsAggregationTaskState currentState) {
        if (currentState.aggregatedTimeBinMap == null) {
            sendSelfPatch(currentState, TaskStage.FINISHED, null);
            return;
        }
        List<Operation> listOfAggrMetrics = new ArrayList<Operation>();
        for (Entry<String, Map<Long, TimeBin>> aggregateEntries : currentState.aggregatedTimeBinMap.entrySet()) {
            Map<Long, TimeBin> aggrValue = aggregateEntries.getValue();
            List<Long> keys = new ArrayList<Long>();
            keys.addAll(aggrValue.keySet());
            // create list of operations sorted by the timebin
            Collections.sort(keys);
            for (Long timeKey : keys) {
                ResourceAggregateMetric aggrMetric = new ResourceAggregateMetric();
                aggrMetric.timeBin = aggrValue.get(timeKey);
                aggrMetric.currentIntervalTimeStampMicrosUtc = TimeUnit.MILLISECONDS.toMicros(timeKey);
                aggrMetric.documentSelfLink = buildMetricKey(
                        UriUtils.getLastPathSegment(currentState.resourceLink), aggregateEntries.getKey());
                listOfAggrMetrics.add(Operation.createPost(getHost(), ResourceAggregateMetricService.FACTORY_LINK)
                        .setBody(aggrMetric));
            }
        }
        Iterator<Operation> createOpIterator = listOfAggrMetrics.iterator();
        if (!createOpIterator.hasNext()) {
            // nothing to persist, just finish the task
            sendSelfPatch(currentState, TaskStage.FINISHED, null);
            return;
        }
        OperationSequence opSequence = OperationSequence.create(createOpIterator.next());
        // we only need to sequence operations for the same metric; this is a global sequence per resource that
        // needs to be optimized
        while (createOpIterator.hasNext()) {
            opSequence = opSequence.next(createOpIterator.next());
        }
        opSequence.setCompletion((ops, exc) -> {
            if (exc != null) {
                sendSelfFailurePatch(currentState, exc.values().iterator().next().getMessage());
                return;
            }
            sendSelfPatch(currentState, TaskStage.FINISHED, null);
            return;
        });
        opSequence.sendWith(this);
    }

    // extract the metric key from the documentSelfLink of the raw resource metric
    private String getMetricKey(String resourceMetricKey, String resourceKey) {
        return resourceMetricKey.replace(resourceKey, "");
    }

    // build the keys used to represent rolled up data. We currently support hourly and daily rollups
    private List<String> buildRollupKeys(String baseKey) {
        List<String> returnList = new ArrayList<String>();
        returnList.add(new StringBuilder(baseKey).append(StatsConstants.HOUR_SUFFIX).toString());
        returnList.add(new StringBuilder(baseKey).append(StatsConstants.DAILY_SUFFIX).toString());
        return returnList;
    }

    // get the raw metric key based on the rollup metric key
    private String getRawMetricKey(String rollupKey) {
        if (rollupKey.contains(StatsConstants.HOUR_SUFFIX)) {
            return rollupKey.replace(StatsConstants.HOUR_SUFFIX, "");
        }
        return rollupKey.replace(StatsConstants.DAILY_SUFFIX, "");
    }

    private String buildRawMetricKey(String resourceId, String rollupMetricKey) {
        String rawKey = getRawMetricKey(rollupMetricKey);
        return buildMetricKey(resourceId, rawKey);
    }

    private String buildMetricKey(String resourceId, String metricKey) {
        return new StringBuilder(UriUtils.getLastPathSegment(resourceId)).append("-").append(metricKey).toString();
    }

    // compute the beginning of the rollup interval
    private long computeIntervalBegin(long timestampMicros, long bucketDurationMillis) {
        long timeMillis = TimeUnit.MICROSECONDS.toMillis(timestampMicros);
        timeMillis -= (timeMillis % bucketDurationMillis);
        return timeMillis;
    }

    // compute the end of the rollup interval
    private long computeIntervalEnd(long timestampMicros, long bucketDurationMillis) {
        long timeMillis = computeIntervalBegin(timestampMicros, bucketDurationMillis);
        return (timeMillis + bucketDurationMillis);
    }

    // lookup the size of the time bin based on the metric key
    private int lookupBinSize(String metricKey) {
        if (metricKey.contains(StatsConstants.HOUR_SUFFIX)) {
            return StatsConstants.BUCKET_SIZE_HOURS_IN_MILLIS;
        }
        return StatsConstants.BUCKET_SIZE_DAYS_IN_MILLIS;
    }

    private TimeBin updateBin(TimeBin inputBin, double value) {
        if (inputBin.max == null || inputBin.max < value) {
            inputBin.max = value;
        }
        if (inputBin.min == null || inputBin.min > value) {
            inputBin.min = value;
        }
        if (inputBin.avg == null) {
            inputBin.avg = value;
        } else {
            inputBin.avg = ((inputBin.avg * inputBin.count) + value) / (inputBin.count + 1);
        }
        if (inputBin.sum == null) {
            inputBin.sum = value;
        } else {
            inputBin.sum += value;
        }
        inputBin.count++;
        return inputBin;
    }
}
