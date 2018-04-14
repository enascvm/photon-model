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

import static com.vmware.photon.controller.model.UriPaths.CLOUD_METRICS_QUERY_PAGE_SERVICE;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.CPU_UTILIZATION_PERCENT;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.getUnitForMetric;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.vmware.photon.controller.discovery.cloudaccount.utils.QueryHelper;
import com.vmware.photon.controller.discovery.cloudusage.metrics.CloudMetricsService.CloudMetric;
import com.vmware.photon.controller.discovery.cloudusage.metrics.CloudMetricsService.CloudMetricsState;
import com.vmware.photon.controller.discovery.queries.ComputeQueries;
import com.vmware.photon.controller.discovery.queries.MetricQueries;
import com.vmware.photon.controller.discovery.queries.MetricQueries.MetricHolder;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

/**
 * Query service to return the aggregated cloud metrics (CPU,Memory,Network,Storage) for the configured
 * endpoints in the system grouped by projects.
 *
 * This service accepts a pageLink of projects and does processing that involves
 *
 *  - Getting the expanded resourceGroupStates to get the project details.
 *  - Getting the endpoints that are associated with the passed in list of projects.
 *  - Get the persisted metrics associated with the cloud endpoints.
 *  - Aggregate the persisted metrics by environment type (AWS,Azure,GCP etc).
 *  - After querying for all this information, it creates a succinct representation {@link CloudMetricsState} for each project
 *    and persists them.
 *  - Further it creates a ServiceDocumentQueryResult object that has a map of all the project cloud metric entities.
 *    Coupled with this are the nextPage and prevPage links to access all the records.
 *
 */
public class CloudMetricsQueryPageService extends StatelessService {

    public static final String SELF_LINK_PREFIX = CLOUD_METRICS_QUERY_PAGE_SERVICE;

    public static enum CloudMetricsQueryStages {
        QUERY_PROJECTS, QUERY_ENDPOINTS, GROUP_ENDPOINTS_BY_CLOUD, QUERY_ENDPOINT_METRICS, COMPUTE_AGGREGATES, BUILD_RESULT, SUCCESS
    }

    public final String pageLink;
    public final long expirationTimeMicros;
    public final List<String> tenantLinks;

    /**
     * The service context to save per request information to be used for processing.
     */
    public static class CloudMetricsQueryContext {
        CloudMetricsQueryStages stage;
        Operation originalOperation;
        String nextPageLink;
        String prevPageLink;
        ServiceDocumentQueryResult results;
        Throwable error;

        // The map for saving the project links along with the associated resource group state.
        Map<String, ResourceGroupState> projectsMap;
        // The list of project links for which metrics are being queried.
        List<String> projectLinks;
        // The list of endpoints associated with each project
        Map<String, List<ComputeStateWithDescription>> endpointMapByProject;
        // The list of endpoints grouped by cloud type and associated with each project.
        Map<String, Map<String, List<String>>> endpointMapByProjectGroupedByCloudType;
        // The map for the compute link and the associated metric state
        Map<String, MetricHolder> endpointMetricsMap;
        // The endpoint metrics for each cloudType associated with each project.
        Map<String, Map<String, List<MetricHolder>>> endpointMetricsMapByProjectGroupedByCloudType;
        // The map of all the cloud metrics states that were created.
        Map<String, CloudMetricsState> cloudMetricsMap;

        public CloudMetricsQueryContext(Operation op) {
            this.originalOperation = op;
            this.stage = CloudMetricsQueryStages.QUERY_PROJECTS;
            this.projectsMap = new HashMap<>();
            this.projectLinks = new ArrayList<String>();
            this.endpointMetricsMap = new HashMap<>();
            this.endpointMapByProject = new HashMap<>();
            this.endpointMapByProjectGroupedByCloudType = new HashMap<>();
            this.endpointMetricsMapByProjectGroupedByCloudType = new HashMap<>();
            this.cloudMetricsMap = new HashMap<>();
        }
    }

    public CloudMetricsQueryPageService(String pageLink, long expMicros, List<String> tenantLinks) {
        this.pageLink = pageLink;
        this.expirationTimeMicros = expMicros;
        this.tenantLinks = tenantLinks;
    }

    @Override
    public void handleStart(Operation post) {
        ServiceDocument initState = post.getBody(ServiceDocument.class);

        long interval = initState.documentExpirationTimeMicros - Utils.getNowMicrosUtc();
        if (interval <= 0) {
            logWarning("Task expiration is in the past, extending it");
            interval = TimeUnit.SECONDS.toMicros(getHost().getMaintenanceIntervalMicros() * 2);
        }

        super.toggleOption(ServiceOption.PERIODIC_MAINTENANCE, true);
        super.setMaintenanceIntervalMicros(interval);

        post.complete();
    }

    @Override
    public void handleGet(Operation op) {
        CloudMetricsQueryContext ctx = new CloudMetricsQueryContext(op);
        handleCloudMetricsQueryRequest(ctx);
    }

    @Override
    public void handleMaintenance(Operation op) {
        op.complete();
        getHost().stopService(this);
    }

    /**
     * Handles the request for the query of cloud metrics. Goes through different stages, to get a list of projects,
     * get the associated endpoints for those projects, get the cloud metrics, perform aggreation, create a document
     * representation for the data, persist it , create a service document query result to represent that state
     * and send the response back to the caller with the result set.
     */
    private void handleCloudMetricsQueryRequest(CloudMetricsQueryContext context) {
        switch (context.stage) {
        case QUERY_PROJECTS:
            getProjects(context, CloudMetricsQueryStages.QUERY_ENDPOINTS);
            break;
        case QUERY_ENDPOINTS:
            getEndpoints(context, CloudMetricsQueryStages.GROUP_ENDPOINTS_BY_CLOUD);
            break;
        case GROUP_ENDPOINTS_BY_CLOUD:
            groupEndpointsByCloud(context, CloudMetricsQueryStages.QUERY_ENDPOINT_METRICS);
            break;
        case QUERY_ENDPOINT_METRICS:
            getEndpointMetrics(context, CloudMetricsQueryStages.COMPUTE_AGGREGATES);
            break;
        case COMPUTE_AGGREGATES:
            computeMetricAggregates(context, CloudMetricsQueryStages.BUILD_RESULT);
            break;
        case BUILD_RESULT:
            buildResult(context, CloudMetricsQueryStages.SUCCESS);
            break;
        case SUCCESS:
            handleSuccess(context);
            break;
        default:
            throw new IllegalStateException("Unknown stage encountered: " + context.stage);

        }
    }

    /**
     * Executes the query for the given page link.
     */
    private void getProjects(CloudMetricsQueryContext context, CloudMetricsQueryStages nextStage) {
        Operation operation = Operation.createGet(this, this.pageLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        context.error = e;
                        handleError(context);
                        return;
                    }

                    OperationContext.setFrom(context.originalOperation);
                    QueryTask body = o.getBody(QueryTask.class);
                    ServiceDocumentQueryResult results = body.results;

                    if (results.documentCount == 0) {
                        context.results = new ServiceDocumentQueryResult();
                        context.stage = CloudMetricsQueryStages.SUCCESS;
                        handleSuccess(context);
                        return;
                    }

                    extractQueryResults(context, results);
                    context.stage = nextStage;
                    handleCloudMetricsQueryRequest(context);
                });
        setAuthorizationContext(operation, getSystemAuthorizationContext());
        sendRequest(operation);

    }

    /**
     * Extract the query results and start page services as needed.
     * - Creating a map of projectLink -> ResourceGroupState
     * - Creating a list of projectLinks to be used in the next set of queries.
     */
    private void extractQueryResults(CloudMetricsQueryContext context,
            ServiceDocumentQueryResult result) {
        for (Object o : result.documents.values()) {
            ResourceGroupState state = Utils.fromJson(o, ResourceGroupState.class);
            context.projectsMap.put(state.documentSelfLink, state);
        }
        context.projectLinks = context.projectsMap.keySet().stream().collect(Collectors.toList());

        if (result.nextPageLink != null && !result.nextPageLink.isEmpty()) {
            CloudMetricsQueryPageService pageService = new CloudMetricsQueryPageService(
                    result.nextPageLink, this.expirationTimeMicros,
                    this.tenantLinks);

            context.nextPageLink = QueryHelper
                    .startStatelessPageService(this, CloudMetricsQueryPageService.SELF_LINK_PREFIX,
                            pageService,
                            this.expirationTimeMicros,
                            failure -> {
                                context.error = failure;
                                handleError(context);
                            });
        }

        if (result.prevPageLink != null && !result.prevPageLink.isEmpty()) {
            CloudMetricsQueryPageService pageService = new CloudMetricsQueryPageService(
                    result.prevPageLink, this.expirationTimeMicros,
                    this.tenantLinks);

            context.prevPageLink = QueryHelper
                    .startStatelessPageService(this, CloudMetricsQueryPageService.SELF_LINK_PREFIX,
                            pageService,
                            this.expirationTimeMicros,
                            failure -> {
                                context.error = failure;
                                handleError(context);
                            });
        }
    }

    /**
     * Gets the endpoints associated with each project
     */
    private void getEndpoints(CloudMetricsQueryContext context,
            CloudMetricsQueryStages next) {

        AtomicInteger count = new AtomicInteger(context.projectLinks.size());
        for (String projectLink : context.projectLinks) {
            ComputeQueries.getEndpointsByProject(this, projectLink, (endpointList, e) -> {
                if (e != null) {
                    count.decrementAndGet();
                    logSevere(e);
                    return;
                }

                if (endpointList != null) {
                    context.endpointMapByProject.put(projectLink, endpointList);
                }

                // if no endpoints are present for the given set of projects then
                // return from the service.
                if (count.decrementAndGet() == 0) {
                    if (context.endpointMapByProject.size() == 0) {
                        context.results = new ServiceDocumentQueryResult();
                        context.stage = CloudMetricsQueryStages.SUCCESS;
                        handleSuccess(context);
                        return;
                    }
                    context.stage = next;
                    handleCloudMetricsQueryRequest(context);
                }
            });
        }
    }

    /**
     * Groups the list of endpoints for a given project by the cloud type.
     *
     * - Accepts a map of endpoints for each projectLink
     * - Groups the endpoints by cloud type
     */
    private void groupEndpointsByCloud(CloudMetricsQueryContext context,
            CloudMetricsQueryStages next) {

        for (String projectLink : context.endpointMapByProject.keySet()) {
            List<ComputeStateWithDescription> computeStates = context.endpointMapByProject
                    .get(projectLink);
            Map<String, List<String>> endpointsGroupedByCloudType = computeStates.stream().collect(
                    Collectors.groupingBy(
                            computeStateWithDescription -> computeStateWithDescription.description.environmentName,
                            Collectors.mapping(
                                    computeStateWithDescription -> computeStateWithDescription.documentSelfLink,
                                    Collectors.toList())));
            context.endpointMapByProjectGroupedByCloudType.put(projectLink,
                    endpointsGroupedByCloudType);
        }

        context.stage = next;
        handleCloudMetricsQueryRequest(context);
    }

    /**
     * Gets the metrics associated with each of the endpoints.
     */
    private void getEndpointMetrics(CloudMetricsQueryContext context,
            CloudMetricsQueryStages next) {
        List<String> computeLinks = new ArrayList<>();
        for (List<ComputeStateWithDescription> computeList : context.endpointMapByProject
                .values()) {
            computeLinks
                    .addAll(computeList.stream().map(computeState -> computeState.documentSelfLink)
                            .collect(Collectors.toList()));
        }

        MetricQueries.getMetricsForComputeLinks(this, computeLinks,
                (metricMap, e) -> {
                    if (e != null) {
                        logSevere(e);
                        return;
                    }
                    if (metricMap != null) {
                        context.endpointMetricsMap = metricMap;
                    }
                    // If no metrics were discovered for any of the endpoints.
                    if (context.endpointMetricsMap.size() == 0) {
                        context.results = new ServiceDocumentQueryResult();
                        context.stage = CloudMetricsQueryStages.SUCCESS;
                        handleSuccess(context);
                        return;
                    }
                    context.stage = next;
                    handleCloudMetricsQueryRequest(context);
                });
    }

    /**
     * Computes the aggregation of the metrics by cloud type. Further creates the document that is persisted to represented
     * the the metric aggregation.
     *
     * The basic algorithm here is to traverse the map that already contains the grouping information by cloud type.
     * Get the metrics associated with the endpoints, grouped by the cloud type. Perform aggregation on those metrics
     * and create the result set.
     *
     * The information is represented in one state object per project. The metric state object in turn contains
     * a map that saves a list of aggregated metrics per cloud type.
     */
    private void computeMetricAggregates(CloudMetricsQueryContext context,
            CloudMetricsQueryStages next) {
        for (String projectLink : context.endpointMapByProjectGroupedByCloudType.keySet()) {
            CloudMetricsState cloudMetricsState = new CloudMetricsState(
                    context.projectsMap.get(projectLink).name);

            Map<String, List<String>> endpointMapByCloudType = context.endpointMapByProjectGroupedByCloudType
                    .get(projectLink);
            for (Entry<String, List<String>> cloudTypeEntry : endpointMapByCloudType.entrySet()) {
                String cloudType = cloudTypeEntry.getKey();
                List<String> endpoints = cloudTypeEntry.getValue();
                List<MetricHolder> metricList = new ArrayList<>();
                for (String computeLink : endpoints) {
                    MetricHolder metric = context.endpointMetricsMap.get(computeLink);
                    if (metric != null) {
                        metricList.add(metric);
                    }
                }
                List<CloudMetric> cloudMetricList = performAggregation(cloudType, metricList);
                if (cloudMetricList == null) {
                    continue;
                }
                cloudMetricsState.cloudMetricsByEnvironmentMap.put(cloudType,
                        cloudMetricList);
            }
            cloudMetricsState.tenantLinks = Collections.singletonList(projectLink);
            cloudMetricsState.documentExpirationTimeMicros = this.expirationTimeMicros;
            cloudMetricsState.id = UUID.randomUUID().toString();
            context.cloudMetricsMap.put(cloudMetricsState.id, cloudMetricsState);
        }
        context.stage = next;
        handleCloudMetricsQueryRequest(context);

    }

    /**
     * Performs the aggregation for the different metrics for the cloud endpoints.
     * - CPU Utilization is represented in Percentages; so the average value is computed.
     * TODO This code will be augumented to perform aggregation for different metrics
     * that are available in the ResourceAggregateMetricsState.
     */
    private List<CloudMetric> performAggregation(String cloudType, List<MetricHolder> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return null;
        }

        String cpuUtilizationHourly = CPU_UTILIZATION_PERCENT;
        List<Double> cpuUtilizationPercents = new ArrayList<>();

        for (MetricHolder holder : metrics) {
            cpuUtilizationPercents.add(holder.metrics.get(cpuUtilizationHourly));
        }

        double average = cpuUtilizationPercents
                .stream()
                .mapToDouble(cpuUtil -> cpuUtil)
                .average()
                .getAsDouble();

        CloudMetric cloudMetric = new CloudMetric();
        cloudMetric.metricName = cpuUtilizationHourly;
        cloudMetric.metricValue = average;
        cloudMetric.metricUnit = getUnitForMetric(CPU_UTILIZATION_PERCENT);
        return Collections.singletonList(cloudMetric);
    }

    /**
     * Save cloud metrics PODOs and build {@link ServiceDocumentQueryResult}.
     */
    private void buildResult(CloudMetricsQueryContext context, CloudMetricsQueryStages nextStage) {
        List<Operation> operations = new ArrayList<>();
        for (CloudMetricsState cloudMetricState : context.cloudMetricsMap.values()) {
            Operation operation = Operation
                    .createPost(getHost(), CloudMetricsService.FACTORY_LINK)
                    .setBody(cloudMetricState);
            operations.add(operation);
        }

        OperationJoin.create(operations)
                .setCompletion((ops, failures) -> {
                    if (failures != null) {
                        context.error = failures.values().iterator().next();
                        handleError(context);
                        return;
                    }

                    for (Operation op : ops.values()) {
                        CloudMetricsState cloudMetricsState = op.getBody(CloudMetricsState.class);
                        CloudMetricsState prevCloudCostState = context.cloudMetricsMap.get(
                                cloudMetricsState.id);

                        if (prevCloudCostState == null) {
                            context.error = new IllegalStateException(
                                    "Cannot look up cloud cost with id: " + cloudMetricsState.id);
                            handleError(context);
                            return;
                        }

                        context.cloudMetricsMap.put(cloudMetricsState.id, cloudMetricsState);
                    }

                    ServiceDocumentQueryResult result = new ServiceDocumentQueryResult();
                    result.documentLinks = new ArrayList<>();
                    result.documents = new LinkedHashMap<>();
                    for (CloudMetricsState cloudMetricState : context.cloudMetricsMap.values()) {
                        result.documentLinks.add(cloudMetricState.documentSelfLink);
                        result.documents.put(cloudMetricState.documentSelfLink, cloudMetricState);
                    }

                    result.nextPageLink = context.nextPageLink;
                    result.prevPageLink = context.prevPageLink;
                    result.documentCount = (long) context.projectLinks.size();
                    context.results = result;
                    context.stage = nextStage;
                    handleCloudMetricsQueryRequest(context);
                })
                .sendWith(this);

    }

    private void handleSuccess(CloudMetricsQueryContext ctx) {
        ctx.originalOperation.setBody(ctx.results);
        ctx.originalOperation.complete();
    }

    private void handleError(CloudMetricsQueryContext ctx) {
        logSevere("Failed at stage %s with exception: %s", ctx.stage, Utils.toString(ctx.error));
        ctx.originalOperation.fail(ctx.error);
    }

}
