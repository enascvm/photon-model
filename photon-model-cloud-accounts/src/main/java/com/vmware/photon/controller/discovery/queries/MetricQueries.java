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

package com.vmware.photon.controller.discovery.queries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.vmware.photon.controller.discovery.cloudaccount.utils.QueryHelper.QueryCompletionHandler;
import com.vmware.photon.controller.discovery.cloudaccount.utils.QueryUtils;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService.ResourceMetrics;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.tasks.monitoring.StatsUtil;
import com.vmware.photon.controller.model.util.ClusterUtil.ServiceTypeCluster;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocumentDescription.TypeName;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.NumericRange;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Metrics related queries.
 */
public class MetricQueries {
    /**
     * Get raw metrics including all versions.
     *
     * @param service The service executing the query
     * @param resourceLink The resource for which the raw metrics are requested.
     * @param metricName The name of the metric
     * @param startTimeMicros The start time in micros.
     * @param endTimeMicros The end time in micros
     * @param completionHandler The completion handler
     */
    public static void getRawMetrics(Service service, String resourceLink, String metricName,
            Long startTimeMicros, Long endTimeMicros,
            QueryCompletionHandler<ServiceDocumentQueryResult> completionHandler) {

        String resourceId = UriUtils.getLastPathSegment(resourceLink);
        String metricSelfLink = UriUtils.buildUriPath(ResourceMetricsService.FACTORY_LINK, resourceId);

        Query query = Builder.create()
                .addKindFieldClause(ResourceMetrics.class)
                .addFieldClause(ResourceMetrics.FIELD_NAME_SELF_LINK, metricSelfLink,
                        MatchType.PREFIX)
                .addRangeClause(QuerySpecification.buildCompositeFieldName(ResourceMetrics.FIELD_NAME_ENTRIES, metricName),
                            NumericRange.createDoubleRange(Double.MIN_VALUE, Double.MAX_VALUE, true, true))
                .addRangeClause(ResourceMetrics.FIELD_NAME_TIMESTAMP,
                        NumericRange.createLongRange(startTimeMicros, endTimeMicros, true, true))
                .build();

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .setQuery(query)
                .build();

        QueryUtils.startQueryTaskWithSystemAuth(service, queryTask,
                ServiceTypeCluster.METRIC_SERVICE, completionHandler);
    }

    /**
     * Returns the aggregated metrics for a given compute link.
     *
     * @param service The service executing the query
     * @param computeLinks The set of compute link
     * @param completionHandler The completion handler
     */
    public static void getMetricsForComputeLinks(Service service, List<String> computeLinks,
            QueryCompletionHandler<Map<String, MetricHolder>> completionHandler) {
        if (computeLinks.isEmpty()) {
            completionHandler.handle(Collections.emptyMap(), null);
            return;
        }

        // Limit the number of datapoints
        long startTimeMicros = Utils.getNowMicrosUtc() - TimeUnit.HOURS.toMicros(1);

        Query.Builder builder = Query.Builder.create()
                .addKindFieldClause(ResourceMetrics.class);
        builder.addRangeClause(ResourceMetrics.FIELD_NAME_TIMESTAMP,
                NumericRange.createGreaterThanRange(startTimeMicros));

        Query.Builder clause = Query.Builder.create();
        for (String computeLink : computeLinks) {
            // Get ResourceMetrics documents for compute id prefix.
            String computeId = UriUtils.getLastPathSegment(computeLink);
            String selfLinkPrefix = UriUtils
                    .buildUriPath(ResourceMetricsService.FACTORY_LINK, computeId);
            clause.addFieldClause(ResourceMetrics.FIELD_NAME_SELF_LINK,
                    selfLinkPrefix, MatchType.PREFIX, Occurance.SHOULD_OCCUR);
        }

        builder.addClause(clause.build());

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.SORT)
                .addOption(QueryOption.EXPAND_CONTENT)
                .orderAscending(ResourceMetrics.FIELD_NAME_SELF_LINK, TypeName.STRING)
                .setQuery(builder.build())
                .build();

        QueryCompletionHandler<ServiceDocumentQueryResult> nestedCompletion = (results, e) -> {
            if (e != null) {
                completionHandler.handle(null, e);
                return;
            }

            if (results.documentCount == 0) {
                completionHandler.handle(Collections.emptyMap(), null);
                return;
            }

            Map<String, MetricHolder> metrics = new HashMap<>();

            for (Object o : results.documents.values()) {
                ResourceMetrics state = Utils.fromJson(o, ResourceMetrics.class);

                // metricKey = <resource_id>_<timestamp>
                String resourceId = StatsUtil.getResourceId(state.documentSelfLink);
                String computeLink = UriUtils
                        .buildUriPath(ComputeService.FACTORY_LINK, resourceId);

                MetricHolder metricHolder = metrics.get(computeLink);
                if (metricHolder == null) {
                    metricHolder = new MetricHolder();
                    metricHolder.metrics = new HashMap<>();
                }
                for (String metricName : state.entries.keySet()) {
                    metricHolder.metrics.put(metricName, state.entries.get(metricName));
                }
                metrics.put(computeLink, metricHolder);

            }
            completionHandler.handle(metrics, null);
        };

        QueryUtils.startQueryTaskWithSystemAuth(service, queryTask,
                ServiceTypeCluster.METRIC_SERVICE, nestedCompletion);
    }

    /**
     * Get the latest values for each of the requested metrics and
     * resources.
     *
     * @param service The service executing the query
     * @param metricNames Set of requested metrics.
     * @param resourceLinks Set of requested resources.
     * @param completionHandler The completion handler
     */
    public static void getLatestMetricValues(Service service, Set<String> metricNames,
            Set<String> resourceLinks,
            QueryCompletionHandler<ServiceDocumentQueryResult> completionHandler) {

        if (metricNames == null || metricNames.isEmpty()
                || resourceLinks == null || resourceLinks.isEmpty()) {
            completionHandler.handle(new ServiceDocumentQueryResult(), null);
            return;
        }

        List<Operation> operations = new ArrayList<>(metricNames.size() * resourceLinks.size());
        for (String metricName : metricNames) {
            for (String link : resourceLinks) {
                String metricLinkPrefix = UriUtils.buildUriPath(ResourceMetricsService.FACTORY_LINK, UriUtils.getLastPathSegment(link));
                Query query = Query.Builder.create()
                        .addKindFieldClause(ResourceMetrics.class)
                        .addFieldClause(ResourceMetrics.FIELD_NAME_SELF_LINK, metricLinkPrefix,
                                MatchType.PREFIX)
                        .addRangeClause(QuerySpecification.buildCompositeFieldName(ResourceMetrics.FIELD_NAME_ENTRIES, metricName),
                                NumericRange.createDoubleRange(0.0, Double.MAX_VALUE, true, true))
                        .build();
                QueryTask queryTask = QueryTask.Builder.createDirectTask()
                        .setQuery(query)
                        .addOption(QueryOption.SORT)
                        .addOption(QueryOption.EXPAND_CONTENT)
                        .addOption(QueryOption.TOP_RESULTS)
                        // No-op in Symphony. Required for special handling of immutable documents.
                        // This will prevent Lucene from holding the full result set in memory.
                        .addOption(QueryOption.INCLUDE_ALL_VERSIONS)
                        .setResultLimit(1)
                        .orderDescending(ResourceMetrics.FIELD_NAME_SELF_LINK, TypeName.STRING)
                        .build();

                Operation operation = Operation.createPost(UriUtils.buildUri(service.getHost(),
                                ServiceUriPaths.CORE_LOCAL_QUERY_TASKS))
                        .setBody(queryTask)
                        .setConnectionSharing(true);

                service.setAuthorizationContext(operation, service.getSystemAuthorizationContext());
                operations.add(operation);
            }
        }

        OperationContext operationContext = OperationContext.getOperationContext();
        OperationJoin.create(operations.stream())
                .setCompletion((ops, exs) -> {
                    OperationContext.restoreOperationContext(operationContext);

                    if (exs != null) {
                        completionHandler.handle(null, exs.values().iterator().next());
                        return;
                    }

                    ServiceDocumentQueryResult results =  new ServiceDocumentQueryResult();
                    results.documentLinks = new ArrayList<>();
                    results.documents = new HashMap<>();
                    results.documentCount = 0L;
                    for (Operation op : ops.values()) {
                        QueryTask body = op.getBody(QueryTask.class);
                        if (body.results == null || body.results.documents == null) {
                            continue;
                        }

                        results.documentLinks.addAll(body.results.documentLinks);
                        results.documents.putAll(body.results.documents);
                        results.documentCount += body.results.documentCount;


                    }
                    completionHandler.handle(results, null);
                })
                .sendWith(service, 50);
    }

    /**
     * Metric link format: /monitoring/metrics/<resource-id>_<timestamp>.
     *
     */
    public static String getMetricLinkPrefix(String resourceLink) {
        String metricKeyPrefix = UriUtils.getLastPathSegment(resourceLink);
        return UriUtils.extendUri(UriUtils.buildUri(ResourceMetricsService.FACTORY_LINK),
                metricKeyPrefix).toString();
    }

    /**
     * Helper class to holder metric name to associate time bin mapping.
     */
    public static class MetricHolder {
        public Map<String, Double> metrics;
    }
}