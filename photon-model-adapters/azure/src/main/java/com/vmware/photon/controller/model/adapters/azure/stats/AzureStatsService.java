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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Consumer;

import com.vmware.photon.controller.model.adapterapi.ComputeStatsRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse.ComputeStats;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.monitoring.ResourceMetricService;
import com.vmware.photon.controller.model.monitoring.ResourceMetricService.ResourceMetric;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService.SingleResourceStatsCollectionTaskState;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService.SingleResourceTaskCollectionStage;
import com.vmware.photon.controller.model.tasks.monitoring.StatsUtil;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;
import com.vmware.xenon.services.common.ServiceUriPaths;

public class AzureStatsService extends StatelessService {
    public static final String SELF_LINK = AzureUriPaths.AZURE_STATS_ADAPTER;

    private class AzureStatsDataHolder {
        public ComputeStateWithDescription computeDesc;
        public ComputeStatsRequest statsRequest;
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        op.complete();
        ComputeStatsRequest statsRequest = op.getBody(ComputeStatsRequest.class);

        if (statsRequest.isMockRequest) {
            // patch status to parent task
            AdapterUtils.sendPatchToProvisioningTask(this, statsRequest.taskReference);
            return;
        }

        AzureStatsDataHolder statsData = new AzureStatsDataHolder();
        statsData.statsRequest = statsRequest;
        getVMDescription(statsData);
    }

    /**
     * Returns if the given compute description is a compute host or not.
     */
    private boolean isComputeHost(ComputeDescription computeDescription) {
        List<String> supportedChildren = computeDescription.supportedChildren;
        return supportedChildren != null && supportedChildren.contains(ComputeType.VM_GUEST.name());
    }

    private void getVMDescription(AzureStatsDataHolder statsData) {
        Consumer<Operation> onSuccess = (op) -> {
            statsData.computeDesc = op.getBody(ComputeStateWithDescription.class);
            boolean isComputeHost = isComputeHost(statsData.computeDesc.description);
            // If not a compute host, relay the request to Azure Stats Gatherer
            // with the task reference intact, so the Gatherer will send the
            // response directly to the task which called it.
            if (!isComputeHost) {
                ComputeStatsRequest statsRequest = statsData.statsRequest;
                Operation statsOp = Operation.createPatch(
                        UriUtils.buildUri(getHost(), AzureUriPaths.AZURE_STATS_GATHERER))
                        .setBody(statsRequest)
                        .setReferer(getUri());
                getHost().sendRequest(statsOp);
                return;
            }

            // If it's a compute host, get children and send stats request for each.
            getComputeHostStats(statsData);
        };
        URI computeUri = UriUtils.extendUriWithQuery(statsData.statsRequest.resourceReference,
                UriUtils.URI_PARAM_ODATA_EXPAND, Boolean.TRUE.toString());
        AdapterUtils.getServiceState(this, computeUri, onSuccess, getFailureConsumer(statsData));
    }

    /**
     * Query all the children VM compute of the compute host.
     */
    private void getComputeHostStats(AzureStatsDataHolder statsData) {
        URI queryUri = UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_QUERY_TASKS);
        QueryTask.QuerySpecification querySpec = new QueryTask.QuerySpecification();

        String kind = Utils.buildKind(ComputeService.ComputeState.class);
        QueryTask.Query kindClause = new QueryTask.Query().setTermPropertyName(
                ServiceDocument.FIELD_NAME_KIND).setTermMatchValue(kind);
        querySpec.query.addBooleanClause(kindClause);

        QueryTask.Query parentClause = new QueryTask.Query()
                .setTermMatchType(MatchType.TERM)
                .setTermPropertyName(ComputeService.ComputeState.FIELD_NAME_PARENT_LINK)
                .setTermMatchValue(statsData.computeDesc.documentSelfLink);
        querySpec.query.addBooleanClause(parentClause);

        // TODO: Handle Pagination - https://jira-hzn.eng.vmware.com/browse/VSYM-1270
        QueryTask task = QueryTask.create(querySpec).setDirect(true);
        task.tenantLinks = statsData.computeDesc.tenantLinks;
        Operation queryOp = Operation
                .createPost(queryUri)
                .setBody(task)
                .setCompletion((o, f) -> handleComputeQueryCompletion(o, f, statsData));
        sendRequest(queryOp);
    }

    /**
     * Create a query task for each compute VM and return the operation.
     */
    private Operation getStatsQueryTaskOperation(AzureStatsDataHolder statsData, String computeLink) {
        String computeId = UriUtils.getLastPathSegment(computeLink);
        URI queryUri = UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_QUERY_TASKS);
        QueryTask.QuerySpecification querySpec = new QueryTask.QuerySpecification();

        String kind = Utils.buildKind(ResourceMetric.class);
        QueryTask.Query kindClause = new QueryTask.Query().setTermPropertyName(
                ServiceDocument.FIELD_NAME_KIND).setTermMatchValue(kind);
        querySpec.query.addBooleanClause(kindClause);

        String selfLinkValue = UriUtils.buildUriPath(
                ResourceMetricService.FACTORY_LINK,
                computeId) + UriUtils.URI_WILDCARD_CHAR;

        QueryTask.Query computeClause = new QueryTask.Query()
                .setTermMatchType(MatchType.WILDCARD)
                .setTermPropertyName(ComputeService.ComputeState.FIELD_NAME_SELF_LINK)
                .setTermMatchValue(selfLinkValue);
        querySpec.query.addBooleanClause(computeClause);

        QueryTask task = QueryTask.create(querySpec).setDirect(true);
        task.querySpec.options = EnumSet.of(QueryOption.EXPAND_CONTENT);
        task.tenantLinks = statsData.computeDesc.tenantLinks;
        Operation queryOp = Operation
                .createPost(queryUri)
                .setBody(task);
        return queryOp;
    }

    /**
     * Get all the children computes and create a query task for each to query the metrics.
     */
    private void handleComputeQueryCompletion(Operation operation, Throwable failure,
            AzureStatsDataHolder statsData) {
        if (failure != null) {
            logSevere(failure.getMessage());
            sendFailurePatch(statsData, failure);
            return;
        }

        QueryTask queryResult = operation.getBody(QueryTask.class);
        if (queryResult == null || queryResult.results == null) {
            sendFailurePatch(statsData, new RuntimeException(
                    String.format("Unexpected query result for '%s'",
                            operation.getUri())));
            return;
        }

        int computeCount = Math.toIntExact(queryResult.results.documentCount);

        // No children found. Send an empty response back.
        if (computeCount <= 0) {
            SingleResourceStatsCollectionTaskState response = new SingleResourceStatsCollectionTaskState();
            response.taskStage = (SingleResourceTaskCollectionStage) statsData.statsRequest.nextStage;
            response.statsList = new ArrayList<>();
            response.statsAdapterReference = UriUtils.buildUri(getHost(), SELF_LINK);
            this.sendRequest(
                    Operation.createPatch(statsData.statsRequest.taskReference)
                            .setBody(response));
            return;
        }

        // Create multiple operations, one each for a VM compute.
        List<Operation> statOperations = new ArrayList<>(computeCount);
        for (String computeLink : queryResult.results.documentLinks) {
            Operation statsOp = getStatsQueryTaskOperation(statsData, computeLink);
            statOperations.add(statsOp);
        }

        OperationJoin.create(statOperations)
                .setCompletion((ops, failures) -> handleQueryTaskResponseAndConsolidateStats(ops,
                        failures, statsData))
                .sendWith(this);
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

            // Aggregate all the responses into a single response
            SingleResourceStatsCollectionTaskState response = aggregateComputeStatsResponses(
                    statsData, items);
            response.taskStage = (SingleResourceTaskCollectionStage) statsData.statsRequest.nextStage;
            response.statsAdapterReference = UriUtils.buildUri(getHost(), SELF_LINK);
            this.sendRequest(
                    Operation.createPatch(statsData.statsRequest.taskReference)
                            .setBody(response)
                            .setReferer(getUri()));
        } catch (Throwable t) {
            sendFailurePatch(statsData, t);
        }
    }

    /**
     * Aggregates stats from all the compute VMs to make up compute Host stats.
     */
    private SingleResourceStatsCollectionTaskState aggregateComputeStatsResponses(
            AzureStatsDataHolder statsData, List<QueryTask> items) {
        int numberOfComputeResponse = items.size();
        ComputeStats computeStats = new ComputeStats();
        computeStats.computeLink = statsData.computeDesc.documentSelfLink;

        Map<String, ServiceStat> statMap = new HashMap<>();
        // Gather all the stats in a single response.
        for (QueryTask queryResult : items) {
            if (queryResult.results.documents != null) {
                for (String key : queryResult.results.documents.keySet()) {
                    ResourceMetric metric = Utils.fromJson(queryResult.results.documents.get(key),
                            ResourceMetric.class);
                    String metricName = StatsUtil.getMetricName(metric.documentSelfLink);
                    if (statMap.containsKey(metricName)) {
                        statMap.get(metricName).latestValue += metric.value;
                    } else {
                        ServiceStat stat = new ServiceStat();
                        stat.latestValue = metric.value;
                        statMap.put(metricName, stat);
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

        SingleResourceStatsCollectionTaskState statsResponse = new SingleResourceStatsCollectionTaskState();
        statsResponse.statsList = new ArrayList<>();
        if (computeStats.statValues.size() > 0) {
            statsResponse.statsList.add(computeStats);
        }
        return statsResponse;
    }

    /**
     * Sends a failure patch back to the caller.
     */
    private void sendFailurePatch(AzureStatsDataHolder statsData, Throwable throwable) {
        // TODO: https://jira-hzn.eng.vmware.com/browse/VSYM-1271
        AdapterUtils.sendFailurePatchToProvisioningTask(this,
                statsData.statsRequest.taskReference, throwable);
    }

    /**
     * Returns an instance of Consumer to fail the task.
     */
    private Consumer<Throwable> getFailureConsumer(AzureStatsDataHolder statsData) {
        return ((throwable) -> {
            AdapterUtils.sendFailurePatchToProvisioningTask(this,
                    statsData.statsRequest.taskReference, throwable);
        });
    }
}
