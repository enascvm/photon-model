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

package com.vmware.photon.controller.model.adapters.azure.stats;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import com.vmware.photon.controller.model.adapterapi.ComputeStatsRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse.ComputeStats;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService.ResourceMetrics;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.util.ClusterUtil;
import com.vmware.photon.controller.model.util.ClusterUtil.ServiceTypeCluster;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * This service queries the per VM metrics stored in the local store and aggregates them at the
 * compute host level.
 * It is called per compute host to collect the metrics.
 *
 */
public class AzureComputeHostStatsGatherer extends StatelessService {
    public static final String SELF_LINK = AzureUriPaths.AZURE_COMPUTE_HOST_STATS_GATHERER;

    private ExecutorService executorService;

    private enum ComputeHostMetricsStages {
        GET_COMPUTE_HOST,
        CALCULATE_METRICS,
        FINISHED,
        ERROR
    }

    @Override
    public void handleStart(Operation startPost) {
        this.executorService = getHost().allocateExecutor(this);
        super.handleStart(startPost);
    }

    @Override
    public void handleStop(Operation delete) {
        this.executorService.shutdown();
        AdapterUtils.awaitTermination(this.executorService);
        super.handleStop(delete);
    }

    private class AzureStatsDataHolder {
        public ComputeStatsRequest statsRequest;
        public ComputeStats statsResponse;
        public Operation computeHostStatsOp;

        public ComputeService.ComputeStateWithDescription computeHost;
        public ComputeHostMetricsStages stage;
        public Throwable error;

        public AzureStatsDataHolder(Operation op) {
            this.statsResponse = new ComputeStatsResponse.ComputeStats();
            // create a thread safe map to hold stats values for resource
            this.statsResponse.statValues = new ConcurrentSkipListMap<>();
            this.computeHostStatsOp = op;
            this.stage = ComputeHostMetricsStages.GET_COMPUTE_HOST;
        }
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        ComputeStatsRequest statsRequest = op.getBody(ComputeStatsRequest.class);

        AzureStatsDataHolder statsData = new AzureStatsDataHolder(op);
        statsData.statsRequest = statsRequest;
        handleMetricDiscovery(statsData);
    }

    /**
     * Retrieve the compute host metrics from the local document store based on the
     * computes received from the remote endpoint.
     * @param dataHolder The local service context that has all the information needed to
     * states in the local system.
     */
    private void handleMetricDiscovery(AzureStatsDataHolder dataHolder) {
        switch (dataHolder.stage) {
        case GET_COMPUTE_HOST:
            getComputeHost(dataHolder, ComputeHostMetricsStages.CALCULATE_METRICS);
            break;
        case CALCULATE_METRICS:
            getComputeHostStats(dataHolder);
            break;
        case FINISHED:
            dataHolder.computeHostStatsOp.setBody(dataHolder.statsResponse);
            dataHolder.computeHostStatsOp.complete();
            logInfo(() -> String.format("Stats collection complete for compute host %s",
                    dataHolder.computeHost.id));
            break;
        case ERROR:
            dataHolder.computeHostStatsOp.fail(dataHolder.error);
            logWarning(() -> String.format("Stats collection failed for compute host %s",
                    dataHolder.computeHost.id));
            break;
        default:
        }
    }

    private void getComputeHost(AzureStatsDataHolder statsData, ComputeHostMetricsStages next) {
        Consumer<Operation> onSuccess = (op) -> {
            statsData.computeHost = op.getBody(ComputeService.ComputeStateWithDescription.class);
            statsData.stage = next;
            handleMetricDiscovery(statsData);
        };
        URI computeHostUri = UriUtils.extendUriWithQuery(statsData.statsRequest.resourceReference,
                UriUtils.URI_PARAM_ODATA_EXPAND, Boolean.TRUE.toString());
        AdapterUtils.getServiceState(this, computeHostUri, onSuccess,
                getFailureConsumer(statsData));
    }

    /**
     * Query all the children VMs of the compute host.
     */
    private void getComputeHostStats(AzureStatsDataHolder statsData) {
        Query query = Query.Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK,
                        statsData.computeHost.documentSelfLink)
                .build();

        // TODO VSYM-1270: Handle Pagination
        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .setQuery(query)
                .build();
        queryTask.tenantLinks = statsData.computeHost.tenantLinks;

        QueryUtils.startQueryTask(this, queryTask)
                .whenComplete((qrt, e) -> handleComputeQueryCompletion(qrt, e, statsData));
    }

    /**
     * Get all the children computes and create a query task for each to query the metrics.
     */
    private void handleComputeQueryCompletion(QueryTask queryTask, Throwable failure,
            AzureStatsDataHolder statsData) {
        if (failure != null) {
            logSevere(failure.getMessage());
            statsData.error = failure;
            statsData.stage = ComputeHostMetricsStages.ERROR;
            handleMetricDiscovery(statsData);
            return;
        }

        if (queryTask == null || queryTask.results == null) {
            sendFailurePatch(statsData, new RuntimeException(
                    String.format("Unexpected query result for '%s'", queryTask.documentSelfLink)));
            return;
        }

        int computeCount = Math.toIntExact(queryTask.results.documentCount);

        // No children found, proceed to finish
        if (computeCount <= 0) {
            statsData.stage = ComputeHostMetricsStages.FINISHED;
            handleMetricDiscovery(statsData);
            return;
        }

        // Create multiple operations, one each for a VM compute.
        List<Operation> statOperations = new ArrayList<>(computeCount);
        for (String computeLink : queryTask.results.documentLinks) {
            Operation statsOp = getStatsQueryTaskOperation(statsData, computeLink);
            statOperations.add(statsOp);
        }

        OperationJoin.create(statOperations)
                .setCompletion((ops, failures) ->
                        handleQueryTaskResponseAndConsolidateStats(ops, failures, statsData))
                .sendWith(this, 50);
    }

    /**
     * Create a query task for each compute VM and return the operation.
     */
    private Operation getStatsQueryTaskOperation(AzureStatsDataHolder statsData, String computeLink) {
        String computeId = UriUtils.getLastPathSegment(computeLink);
        String selfLink = UriUtils.buildUriPath(ResourceMetricsService.FACTORY_LINK, computeId);

        // TODO VSYM-3695: Limit the time boundaries on Azure metrics retrieval for each compute
        Query query = Query.Builder.create()
                .addKindFieldClause(ResourceMetrics.class)
                .addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK, selfLink, MatchType.PREFIX)
                .build();

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .setQuery(query)
                .addOption(QueryOption.EXPAND_CONTENT)
                .addOption(QueryOption.TOP_RESULTS)
                .setResultLimit(QueryUtils.DEFAULT_MAX_RESULT_LIMIT)
                .build();
        queryTask.tenantLinks = statsData.computeHost.tenantLinks;

        return Operation
                .createPost(UriUtils.buildUri(
                        ClusterUtil.getClusterUri(this.getHost(),
                                ServiceTypeCluster.METRIC_SERVICE),
                        ServiceUriPaths.CORE_LOCAL_QUERY_TASKS))
                .setBody(queryTask);
    }

    /**
     * Consolidates all the query task responses into one response and patches back.
     */
    private void handleQueryTaskResponseAndConsolidateStats(Map<Long, Operation> ops,
            Map<Long, Throwable> failures, AzureStatsDataHolder statsData) {
        try {
            if (failures != null) {
                sendFailurePatch(statsData, failures.get(0L));
                return;
            }
            List<QueryTask> items = new ArrayList<>(ops.size());

            for (Operation op : ops.values()) {
                QueryTask queryResult = op.getBody(QueryTask.class);
                items.add(queryResult);
            }

            statsData.statsResponse = aggregateComputeStatsResponses(statsData, items);
            statsData.stage = ComputeHostMetricsStages.FINISHED;
            handleMetricDiscovery(statsData);
        } catch (Throwable t) {
            sendFailurePatch(statsData, t);
        }
    }

    /**
     * Aggregates stats from all the compute VMs to make up compute Host stats.
     */
    private ComputeStats aggregateComputeStatsResponses(
            AzureStatsDataHolder statsData, List<QueryTask> items) {
        int numberOfComputeResponse = items.size();
        ComputeStats computeStats = new ComputeStats();
        computeStats.computeLink = statsData.computeHost.documentSelfLink;

        Map<String, ServiceStat> statMap = new HashMap<>();
        // Gather all the stats in a single response.
        for (QueryTask queryResult : items) {
            if (queryResult.results.documents != null) {
                for (String key : queryResult.results.documents.keySet()) {
                    ResourceMetrics metric = Utils
                            .fromJson(queryResult.results.documents.get(key),
                            ResourceMetrics.class);
                    for (Map.Entry<String, Double> entry : metric.entries.entrySet()) {
                        String metricName = entry.getKey();
                        if (statMap.containsKey(metricName)) {
                            statMap.get(metricName).latestValue += entry.getValue();
                        } else {
                            ServiceStat stat = new ServiceStat();
                            stat.latestValue = entry.getValue();
                            statMap.put(metricName, stat);
                        }
                    }
                }
            }
        }

        computeStats.statValues = new ConcurrentSkipListMap<>();
        // Divide each metric value by the number of computes to get an average value.
        for (String key : statMap.keySet()) {
            ServiceStat serviceStatValue = statMap.get(key);
            serviceStatValue.unit = PhotonModelConstants.getUnitForMetric(key);
            serviceStatValue.sourceTimeMicrosUtc = Utils.getNowMicrosUtc();
            serviceStatValue.latestValue = serviceStatValue.latestValue / numberOfComputeResponse;
            computeStats.statValues.put(key, Collections.singletonList(serviceStatValue));
        }

        return computeStats;
    }

    /**
     * Sends a failure patch back to the caller.
     */
    private void sendFailurePatch(AzureStatsDataHolder statsData, Throwable throwable) {
        // TODO: https://jira-hzn.eng.vmware.com/browse/VSYM-1271
        statsData.computeHostStatsOp.fail(throwable);
    }

    /**
     * Returns an instance of Consumer to fail the task.
     */
    private Consumer<Throwable> getFailureConsumer(AzureStatsDataHolder statsData) {
        return ((throwable) -> {
            statsData.computeHostStatsOp.fail(throwable);
        });
    }
}
