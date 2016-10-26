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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.monitoring.ResourceAggregateMetricService;
import com.vmware.photon.controller.model.monitoring.ResourceAggregateMetricService.ResourceAggregateMetric;
import com.vmware.photon.controller.model.monitoring.ResourceMetricService;
import com.vmware.photon.controller.model.monitoring.ResourceMetricService.ResourceMetric;
import com.vmware.photon.controller.model.resources.util.PhotonModelUtils;
import com.vmware.photon.controller.model.tasks.TaskUtils;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationSequence;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceDocumentDescription.TypeName;
import com.vmware.xenon.common.ServiceStats.TimeSeriesStats.AggregationType;
import com.vmware.xenon.common.ServiceStats.TimeSeriesStats.TimeBin;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Builder;
import com.vmware.xenon.services.common.QueryTask.NumericRange;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskFactoryService;
import com.vmware.xenon.services.common.TaskService;

/**
 * Task to aggregate resource stats for a single resource. Aggregate stats are backed by ResourceAggregateMetricService
 * instances. Aggregate metrics are identified using a key that is a combination of the resourceId, metricKey and a timestamp.
 * Queries for aggregate metrics need to issue a prefix query on resourceId and metric Key to obtain all documents and filter by date.
 *
 * Aggregation operations can run multiple time within a time window. This will result in multiple documents for the time window.
 * The first document, sorted by documentSelfLink in DESC order, represents the aggregate value after the time interval has closed. All
 * other documents represent point in time aggregates for the collection interval.
 *
 * All aggregate metrics have a timestamp that represents the end of the interval. For example if the aggregation is for hourly
 * data and the interval is 10-11, the aggregate value will have a timestamp of 11
 *
 * Aggregations are based on the the query that is specified. All resources that resolve to the query will be used for aggregation.
 * If no query is specified the aggregation will happen on the resource specified in resourceLink
 */
public class SingleResourceStatsAggregationTaskService extends
        TaskService<SingleResourceStatsAggregationTaskService.SingleResourceStatsAggregationTaskState> {

    public static final String FACTORY_LINK =
            UriPaths.MONITORING + "/single-resource-stats-aggregation";

    public static FactoryService createFactory() {
        TaskFactoryService fs = new TaskFactoryService(
                SingleResourceStatsAggregationTaskState.class) {
            @Override
            public Service createServiceInstance() throws Throwable {
                return new SingleResourceStatsAggregationTaskService();
            }
        };
        fs.setPeerNodeSelectorPath(ServiceUriPaths.DEFAULT_1X_NODE_SELECTOR);
        return fs;
    }

    public static final String STATS_QUERY_RESULT_LIMIT =
            UriPaths.PROPERTY_PREFIX
                    + "SingleResourceStatsAggregationTaskService.query.resultLimit";
    private static final int DEFAULT_QUERY_RESULT_LIMIT = 50;

    public static class SingleResourceStatsAggregationTaskState
            extends TaskService.TaskServiceState {

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
        @UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Query query;

        @Documentation(description = "Metrics to be aggregated on latest value only")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        @UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Set<String> latestValueOnly;

        @Documentation(description = "Aggregation type per metric name")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        @UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Map<String, Set<AggregationType>> aggregations;

        @Documentation(description = "Task to patch back to")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public URI parentTaskReference;

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
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.setPeerNodeSelectorPath(ServiceUriPaths.DEFAULT_1X_NODE_SELECTOR);
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
        return state;
    }

    @Override
    protected void initializeState(SingleResourceStatsAggregationTaskState state,
            Operation postOp) {
        super.initializeState(state, postOp);
        state.taskStage = StatsAggregationStage.GET_LAST_ROLLUP_TIME;

        if (state.query == null) {
            state.query = Query.Builder.create().addFieldClause(
                    ServiceDocument.FIELD_NAME_SELF_LINK, state.resourceLink).build();
        }

        if (state.aggregations == null) {
            state.aggregations = Collections.emptyMap();
        }

        if (state.latestValueOnly == null) {
            state.latestValueOnly = Collections.emptySet();
        }
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
            logFine("Starting single resource stats aggregation for : " + currentState.resourceLink);
            handleStagePatch(currentState);
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
            if (currentState.parentTaskReference != null) {
                sendRequest(Operation
                        .createPatch(currentState.parentTaskReference)
                        .setBody(currentState)
                        .setCompletion(
                                (patchOp, patchEx) -> {
                                    if (patchEx != null) {
                                        logWarning("Patching parent task failed %s",
                                                Utils.toString(patchEx));
                                    }
                                    sendRequest(Operation
                                            .createDelete(getUri()));
                                }));
            } else {
                sendRequest(Operation
                        .createDelete(getUri()));
            }
            logFine("Finished single resource stats aggregation");
            break;
        default:
            break;
        }
    }

    @Override
    public void handlePut(Operation put) {
        PhotonModelUtils.handleIdempotentPut(this, put);
    }

    private void handleStagePatch(SingleResourceStatsAggregationTaskState currentState) {
        switch (currentState.taskStage) {
        case GET_LAST_ROLLUP_TIME:
            getLastRollupTime(currentState);
            break;
        case INIT_RESOURCE_QUERY:
            initializeQuery(currentState);
            break;
        case PROCESS_RESOURCES:
            getResources(currentState);
            break;
        case PUBLISH_METRICS:
            publishMetrics(currentState);
            break;
        default:
            break;
        }
    }

    private void getLastRollupTime(SingleResourceStatsAggregationTaskState currentState) {
        Map<String, Long> lastUpdateMap = new HashMap<>();
        // for all metrics, get the last time rollup happened
        List<Operation> operations = new ArrayList<>();
        for (String metricName : currentState.metricNames) {
            List<String> rollupKeys = buildRollupKeys(metricName);
            for (String rollupKey : rollupKeys) {
                lastUpdateMap.put(rollupKey, null);

                Query.Builder builder = Query.Builder.create();
                builder.addKindFieldClause(ResourceAggregateMetric.class);
                builder.addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                        UriUtils.buildUriPath(ResourceAggregateMetricService.FACTORY_LINK,
                                StatsUtil.getMetricKeyPrefix(currentState.resourceLink, rollupKey)),
                        MatchType.PREFIX);

                Operation op = Operation.createPost(getHost(), ServiceUriPaths.CORE_QUERY_TASKS)
                        .setBody(Builder.createDirectTask()
                                .addOption(QueryOption.SORT)
                                .addOption(QueryOption.TOP_RESULTS)
                                .addOption(QueryOption.EXPAND_CONTENT)
                                .orderDescending(ServiceDocument.FIELD_NAME_SELF_LINK,
                                        TypeName.STRING)
                                .setResultLimit(1)
                                .setQuery(builder.build()).build())
                        .setConnectionSharing(true);
                operations.add(op);
            }
        }

        // Need to optimize this. Right now using OperationSequence so we don't flood the system with
        // lot of queries.
        OperationSequence.create(operations.toArray(new Operation[operations.size()]))
                .setCompletion((ops, failures) -> {
                    if (failures != null) {
                        sendSelfFailurePatch(currentState,
                                failures.values().iterator().next().getMessage());
                        return;
                    }
                    for (Operation operation : ops.values()) {
                        QueryTask response = operation.getBody(QueryTask.class);
                        for (Object obj : response.results.documents.values()) {
                            ResourceAggregateMetric aggregateMetric = Utils
                                    .fromJson(obj, ResourceAggregateMetric.class);
                            lastUpdateMap.replace(
                                    StatsUtil.getMetricName(aggregateMetric.documentSelfLink),
                                    aggregateMetric.currentIntervalTimeStampMicrosUtc);
                        }
                    }
                    SingleResourceStatsAggregationTaskState patchBody = new SingleResourceStatsAggregationTaskState();
                    patchBody.taskInfo = TaskUtils.createTaskState(TaskStage.STARTED);
                    patchBody.taskStage = StatsAggregationStage.INIT_RESOURCE_QUERY;
                    patchBody.lastRollupTimeForMetric = lastUpdateMap;
                    sendSelfPatch(patchBody);
                })
                .sendWith(this);
    }

    /**
     * Initialize query from the task state.
     */
    private void initializeQuery(SingleResourceStatsAggregationTaskState currentState) {
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
    private void getResources(SingleResourceStatsAggregationTaskState currentState) {
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
                            getRawMetrics(currentState, queryTask);
                        }));
    }

    // private class that holds the rollup metric keys of interest
    // and their last rollup time
    private static class RollupMetricHolder {
        String rollupKey;
        Long beginTimestampMicros;
    }

    private void getRawMetrics(SingleResourceStatsAggregationTaskState currentState,
            QueryTask resourceQueryTask) {
        Query.Builder overallQueryBuilder = Query.Builder.create();
        for (String resourceLink : resourceQueryTask.results.documentLinks) {
            for (Entry<String, Long> metricEntry : currentState.lastRollupTimeForMetric
                    .entrySet()) {
                Query.Builder builder = Query.Builder.create(Occurance.SHOULD_OCCUR);
                builder.addKindFieldClause(ResourceMetric.class);
                builder.addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                        UriUtils.buildUriPath(ResourceMetricService.FACTORY_LINK,
                                buildRawMetricKey(resourceLink, metricEntry.getKey())),
                        MatchType.PREFIX);
                if (metricEntry.getValue() != null) {
                    builder.addRangeClause(ResourceMetric.FIELD_NAME_TIMESTAMP,
                            NumericRange.createGreaterThanOrEqualRange(
                                    StatsUtil.computeIntervalBeginMicros(metricEntry.getValue() - 1,
                                            lookupBinSize(metricEntry.getKey()))));
                }
                overallQueryBuilder.addClause(builder.build());
            }
        }

        // create a set of rollup metric keys we are interested in and the timestamp
        // to rollup from for each
        Set<RollupMetricHolder> rollupMetricHolder = new HashSet<>();
        for (Entry<String, Long> metricEntry : currentState.lastRollupTimeForMetric
                .entrySet()) {
            RollupMetricHolder metric = new RollupMetricHolder();
            metric.rollupKey = metricEntry.getKey();
            if (metricEntry.getValue() != null) {
                metric.beginTimestampMicros = StatsUtil.computeIntervalBeginMicros(
                        metricEntry.getValue() - 1,
                        lookupBinSize(metricEntry.getKey()));
            }
            rollupMetricHolder.add(metric);
        }

        QueryTask task = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .setQuery(overallQueryBuilder.build()).build();
        sendRequest(Operation
                .createPost(getHost(), ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(task)
                .setConnectionSharing(true)
                .setCompletion((queryOp, queryEx) -> {
                    if (queryEx != null) {
                        sendSelfFailurePatch(currentState, queryEx.getMessage());
                        return;
                    }
                    Map<String, List<ResourceMetric>> rawMetricsForKey = new HashMap<>();
                    QueryTask response = queryOp.getBody(QueryTask.class);
                    for (Object obj : response.results.documents.values()) {
                        ResourceMetric rawMetric = Utils.fromJson(obj, ResourceMetric.class);
                        for (RollupMetricHolder metric : rollupMetricHolder) {
                            // we want to consider raw metrics with the specified key and the appropriate timestamp
                            if (rawMetric.documentSelfLink
                                    .contains(getRawMetricKey(metric.rollupKey))
                                    && (metric.beginTimestampMicros == null ||
                                    rawMetric.timestampMicrosUtc >= metric.beginTimestampMicros)) {
                                List<ResourceMetric> rawMetricResultSet = rawMetricsForKey
                                        .get(metric.rollupKey);
                                if (rawMetricResultSet == null) {
                                    rawMetricResultSet = new ArrayList<>();
                                    rawMetricsForKey.put(metric.rollupKey, rawMetricResultSet);
                                }
                                rawMetricResultSet.add(rawMetric);
                            }
                        }
                    }
                    aggregateMetrics(currentState, resourceQueryTask, rawMetricsForKey);
                }));
    }

    private void aggregateMetrics(SingleResourceStatsAggregationTaskState currentState,
            QueryTask resourceQueryTask, Map<String, List<ResourceMetric>> rawMetricsForKey) {
        // comparator used to sort resource metric PODOs based on document timestamp
        Comparator<ResourceMetric> comparator = (o1, o2) -> {
            if (o1.timestampMicrosUtc < o2.timestampMicrosUtc) {
                return -1;
            } else if (o1.timestampMicrosUtc > o2.timestampMicrosUtc) {
                return 1;
            }
            return 0;
        };

        Map<String, Map<Long, TimeBin>> aggregatedTimeBinMap = currentState.aggregatedTimeBinMap;
        for (Entry<String, List<ResourceMetric>> rawMetricListEntry : rawMetricsForKey.entrySet()) {
            List<ResourceMetric> rawMetricList = rawMetricListEntry.getValue();

            if (rawMetricList.isEmpty()) {
                continue;
            }

            String metricKey = rawMetricListEntry.getKey();
            Collections.sort(rawMetricList, comparator);

            if (aggregatedTimeBinMap == null) {
                aggregatedTimeBinMap = new HashMap<>();
            }
            Map<Long, TimeBin> timeBinMap = aggregatedTimeBinMap.get(metricKey);
            if (timeBinMap == null) {
                timeBinMap = new HashMap<>();
                aggregatedTimeBinMap.put(metricKey, timeBinMap);
            }

            String metricName = StatsUtil.getMetricName(rawMetricList.get(0).documentSelfLink);

            Collection<ResourceMetric> metrics = rawMetricList;
            if (currentState.latestValueOnly.contains(metricName)) {
                metrics = getLatestMetrics(rawMetricList, metricKey);
            }

            Set<AggregationType> aggregationTypes = null;

            // iterate over the raw metric values and place it in the right time bin
            for (ResourceMetric metric : metrics) {
                long binId = StatsUtil.computeIntervalEndMicros(
                                metric.timestampMicrosUtc,
                                lookupBinSize(metricKey));
                TimeBin bin = timeBinMap.get(binId);
                if (bin == null) {
                    bin = new TimeBin();
                }

                // Figure out the aggregation for the given metric
                if (aggregationTypes == null) {
                    aggregationTypes = currentState.aggregations.get(metricName);
                    if (aggregationTypes == null) {
                        aggregationTypes = EnumSet.allOf(AggregationType.class);
                    }
                }

                updateBin(bin, metric.value, aggregationTypes);
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

    /**
     * Returns the latest metrics from the raw metrics list. Since the list contains all raw metrics
     * across multiple resource we iterate on the list and pick the latest metric for each resource
     * per bin depending on the metric key.
     *
     * TODO VSYM-2481: Add custom mock stats adapter based test for this.
     */
    private Collection<ResourceMetric> getLatestMetrics(List<ResourceMetric> metrics,
            String metricKeyWithInterval) {
        if (metrics.isEmpty()) {
            return Collections.emptyList();
        }

        // Metric link to map of latest value per bin. For example:
        // /monitoring/metrics/<resource-id>_<key1> -> 1474070400000000, <latest-value-of-key1-in-this-time-bucket>
        // /monitoring/metrics/<resource-id>_<key2> -> 1474070400000000, <latest-value-of-key2-in-this-time-bucket>
        Map<String, Map<Long, ResourceMetric>> metricsByLatestValuePerInterval = new HashMap<>();
        for (ResourceMetric metric : metrics) {
            String metricLinkPrefix = StatsUtil.getMetricLinkPrefix(metric.documentSelfLink);
            Map<Long, ResourceMetric> metricsByIntervalEndTime = metricsByLatestValuePerInterval
                    .get(metricLinkPrefix);
            long binId = StatsUtil.computeIntervalEndMicros(
                            metric.timestampMicrosUtc, lookupBinSize(metricKeyWithInterval));
            if (metricsByIntervalEndTime == null) {
                metricsByIntervalEndTime = new HashMap<>();
                metricsByIntervalEndTime.put(binId, metric);
                metricsByLatestValuePerInterval.put(metricLinkPrefix, metricsByIntervalEndTime);
                continue;
            }

            ResourceMetric existingMetric = metricsByIntervalEndTime.get(binId);
            if (existingMetric == null
                    || existingMetric.timestampMicrosUtc < metric.timestampMicrosUtc) {
                metricsByIntervalEndTime.put(binId, metric);
            }
        }

        // Gather all latest values
        List<ResourceMetric> result = new ArrayList<>();
        for (Map<Long, ResourceMetric> metricsByIntervalEndTime : metricsByLatestValuePerInterval
                .values()) {
            result.addAll(metricsByIntervalEndTime.values());
        }

        return result;
    }

    // publish aggregate metric values
    private void publishMetrics(SingleResourceStatsAggregationTaskState currentState) {
        if (currentState.aggregatedTimeBinMap == null) {
            sendSelfPatch(currentState, TaskStage.FINISHED, null);
            return;
        }
        List<Operation> listOfAggrMetrics = new ArrayList<>();
        for (Entry<String, Map<Long, TimeBin>> aggregateEntries : currentState.aggregatedTimeBinMap
                .entrySet()) {
            Map<Long, TimeBin> aggrValue = aggregateEntries.getValue();
            List<Long> keys = new ArrayList<>();
            keys.addAll(aggrValue.keySet());
            // create list of operations sorted by the timebin
            Collections.sort(keys);
            for (Long timeKey : keys) {
                ResourceAggregateMetric aggrMetric = new ResourceAggregateMetric();
                aggrMetric.timeBin = aggrValue.get(timeKey);
                aggrMetric.currentIntervalTimeStampMicrosUtc = timeKey;
                aggrMetric.documentSelfLink = StatsUtil.getMetricKey(currentState.resourceLink,
                        aggregateEntries.getKey(), Utils.getNowMicrosUtc());
                listOfAggrMetrics.add(Operation
                        .createPost(getHost(), ResourceAggregateMetricService.FACTORY_LINK)
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
        });
        opSequence.sendWith(this);
    }

    // build the keys used to represent rolled up data. We currently support hourly and daily rollups
    private List<String> buildRollupKeys(String baseKey) {
        List<String> returnList = new ArrayList<>();
        returnList.add(baseKey + StatsConstants.HOUR_SUFFIX);
        // TODO VSYM-3109: Re-enable this once we fix daily rollup performance.
        // returnList.add(baseKey + StatsConstants.DAILY_SUFFIX);
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
        return StatsUtil.getMetricKeyPrefix(resourceId, rawKey);
    }

    // lookup the size of the time bin based on the metric key
    private int lookupBinSize(String metricKey) {
        if (metricKey.contains(StatsConstants.HOUR_SUFFIX)) {
            return StatsConstants.BUCKET_SIZE_HOURS_IN_MILLIS;
        }
        return StatsConstants.BUCKET_SIZE_DAYS_IN_MILLIS;
    }

    private TimeBin updateBin(TimeBin inputBin, double value,
            Set<AggregationType> aggregationTypes) {
        if (aggregationTypes.contains(AggregationType.MAX)) {
            if (inputBin.max == null || inputBin.max < value) {
                inputBin.max = value;
            }
        }

        if (aggregationTypes.contains(AggregationType.MIN)) {
            if (inputBin.min == null || inputBin.min > value) {
                inputBin.min = value;
            }
        }

        if (aggregationTypes.contains(AggregationType.AVG)) {
            if (inputBin.avg == null) {
                inputBin.avg = value;
            } else {
                inputBin.avg = ((inputBin.avg * inputBin.count) + value) / (inputBin.count + 1);
            }
        }

        if (aggregationTypes.contains(AggregationType.SUM)) {
            if (inputBin.sum == null) {
                inputBin.sum = value;
            } else {
                inputBin.sum += value;
            }
        }
        inputBin.count++;
        return inputBin;
    }
}
