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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse.ComputeStats;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.monitoring.InMemoryResourceMetricService;
import com.vmware.photon.controller.model.monitoring.InMemoryResourceMetricService.InMemoryResourceMetric;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService.ResourceMetrics;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.util.PhotonModelUtils;
import com.vmware.photon.controller.model.tasks.TaskUtils;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService.SingleResourceStatsCollectionTaskState;

import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationSequence;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceDocumentDescription.TypeName;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.ServiceStats.TimeSeriesStats;
import com.vmware.xenon.common.ServiceStats.TimeSeriesStats.AggregationType;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.NumericRange;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskFactoryService;
import com.vmware.xenon.services.common.TaskService;

/**
 * Task service to kick off stats collection at a resource level.
 * The stats adapter associated with this resource can return stats
 * data for a set of resources
 *
 */
public class SingleResourceStatsCollectionTaskService
        extends TaskService<SingleResourceStatsCollectionTaskState> {

    public static final String FACTORY_LINK = UriPaths.MONITORING
            + "/stats-collection-resource-tasks";

    public static FactoryService createFactory() {
        TaskFactoryService fs = new TaskFactoryService(
                SingleResourceStatsCollectionTaskState.class) {
            @Override
            public Service createServiceInstance() throws Throwable {
                return new SingleResourceStatsCollectionTaskService();
            }
        };
        fs.setPeerNodeSelectorPath(ServiceUriPaths.DEFAULT_1X_NODE_SELECTOR);
        return fs;
    }

    public enum SingleResourceTaskCollectionStage {
        GET_DESCRIPTIONS, UPDATE_STATS
    }

    public static class SingleResourceStatsCollectionTaskState
            extends TaskService.TaskServiceState {
        /**
         * compute resource link
         */
        public String computeLink;

        /**
         * Task state
         */
        public SingleResourceTaskCollectionStage taskStage;

        /**
         * Body to patch back upon task completion
         */
        public Object parentPatchBody;

        /**
         * Task to patch back to
         */
        public URI parentTaskReference;

        /**
         * List of stats; this is maintained as part of the
         * state as the adapter patches this back and we
         * want the patch handler to deserialize it generically.
         * Given that the task is non persistent, the cost
         * is minimal
         */
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public List<ComputeStats> statsList;

        @Documentation(description = "The stats adapter reference")
        public URI statsAdapterReference;

    }

    public SingleResourceStatsCollectionTaskService() {
        super(SingleResourceStatsCollectionTaskState.class);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
    }

    @Override
    public void handleStart(Operation start) {
        try {
            if (!start.hasBody()) {
                start.fail(new IllegalArgumentException("body is required"));
                return;
            }
            SingleResourceStatsCollectionTaskState state = start
                    .getBody(SingleResourceStatsCollectionTaskState.class);

            validateState(state);
            start.complete();
            state.taskInfo = TaskUtils.createTaskState(TaskStage.STARTED);
            state.taskStage = SingleResourceTaskCollectionStage.GET_DESCRIPTIONS;
            TaskUtils.sendPatch(this, state);
        } catch (Throwable e) {
            start.fail(e);
        }
    }

    @Override
    public void handlePatch(Operation patch) {
        SingleResourceStatsCollectionTaskState currentState = getState(patch);
        SingleResourceStatsCollectionTaskState patchState = patch
                .getBody(SingleResourceStatsCollectionTaskState.class);
        updateState(currentState, patchState);
        patch.setBody(currentState);
        patch.complete();

        switch (currentState.taskInfo.stage) {
        case CREATED:
            break;
        case STARTED:
            handleStagePatch(currentState);
            break;
        case FINISHED:
        case FAILED:
        case CANCELLED:
            // this is a one shot task, self delete
            sendRequest(Operation
                    .createPatch(currentState.parentTaskReference)
                    .setBody(currentState.parentPatchBody)
                    .setCompletion(
                            (patchOp, patchEx) -> {
                                if (patchEx != null) {
                                    logWarning("Patching parent task failed %s",
                                            Utils.toString(patchEx));
                                }
                                sendRequest(Operation
                                        .createDelete(getUri()));
                                logFine("Finished single resource stats collection");
                            }));
            break;
        default:
            break;
        }
    }

    @Override
    public void handlePut(Operation put) {
        PhotonModelUtils.handleIdempotentPut(this, put);
    }

    private void validateState(SingleResourceStatsCollectionTaskState state) {
        if (state.computeLink == null) {
            throw new IllegalStateException("computeReference should not be null");
        }
        if (state.parentTaskReference == null) {
            throw new IllegalStateException("parentTaskReference should not be null");
        }
        if (state.parentPatchBody == null) {
            throw new IllegalStateException("parentPatchBody should not be null");
        }
    }

    @Override
    public void updateState(SingleResourceStatsCollectionTaskState currentState,
            SingleResourceStatsCollectionTaskState patchState) {
        if (patchState.taskInfo != null) {
            currentState.taskInfo = patchState.taskInfo;
        }
        if (patchState.taskStage != null) {
            currentState.taskStage = patchState.taskStage;
        }
        if (patchState.statsList != null) {
            currentState.statsList = patchState.statsList;
        }
        if (patchState.statsAdapterReference != null) {
            currentState.statsAdapterReference = patchState.statsAdapterReference;
        }
    }

    private void handleStagePatch(SingleResourceStatsCollectionTaskState currentState) {
        switch (currentState.taskStage) {
        case GET_DESCRIPTIONS:
            getDescriptions(currentState);
            break;
        case UPDATE_STATS:
            updateAndPersistStats(currentState);
            break;
        default:
            break;
        }
    }

    private void getDescriptions(SingleResourceStatsCollectionTaskState currentState) {
        URI computeDescUri = ComputeStateWithDescription
                .buildUri(UriUtils.buildUri(getHost(), currentState.computeLink));
        sendRequest(Operation
                .createGet(computeDescUri)
                .setCompletion(
                        (getOp, getEx) -> {
                            if (getEx != null) {
                                TaskUtils.sendFailurePatch(this, currentState, getEx);
                                return;
                            }

                            ComputeStateWithDescription computeStateWithDesc = getOp
                                    .getBody(ComputeStateWithDescription.class);
                            ComputeStatsRequest statsRequest = new ComputeStatsRequest();
                            URI patchUri = null;
                            Object patchBody = null;

                            ComputeDescription description = computeStateWithDesc.description;
                            URI statsAdapterReference = null;
                            List<String> tenantLinks = new ArrayList<>();
                            if (description != null) {
                                tenantLinks = description.tenantLinks;
                                // Only look in adapter references if statsAdapterReference is
                                // provided
                                if (currentState.statsAdapterReference == null) {
                                    statsAdapterReference = description.statsAdapterReference;
                                } else if (description.statsAdapterReferences != null) {
                                    for (URI uri : description.statsAdapterReferences) {
                                        if (uri.equals(currentState.statsAdapterReference)) {
                                            statsAdapterReference = uri;
                                            break;
                                        }
                                    }
                                }
                            }

                            if (statsAdapterReference != null) {
                                statsRequest.nextStage = SingleResourceTaskCollectionStage.UPDATE_STATS
                                        .name();
                                statsRequest.resourceReference = UriUtils
                                        .buildUri(getHost(), computeStateWithDesc.documentSelfLink);
                                statsRequest.taskReference = getUri();
                                patchUri = statsAdapterReference;
                                populateLastCollectionTimeForMetricsInStatsRequest(currentState,
                                        statsRequest, patchUri, tenantLinks);
                            } else {
                                // no adapter associated with this resource, just patch completion
                                SingleResourceStatsCollectionTaskState nextStageState = new SingleResourceStatsCollectionTaskState();
                                nextStageState.taskInfo = new TaskState();
                                nextStageState.taskInfo.stage = TaskStage.FINISHED;
                                patchUri = getUri();
                                patchBody = nextStageState;
                                sendStatsRequestToAdapter(currentState,
                                        patchUri, patchBody);
                            }

                        }));
    }

    private void updateAndPersistStats(SingleResourceStatsCollectionTaskState currentState) {
        if (currentState.statsAdapterReference == null) {
            throw new IllegalStateException("stats adapter reference should not be null");
        }

        // Push the last collection metric to the in memory stats available at the
        // compute-link/stats URI.
        ServiceStats.ServiceStat minuteStats = new ServiceStats.ServiceStat();
        String statsLink = getAdapterLinkFromURI(currentState.statsAdapterReference);
        minuteStats.name = getLastCollectionMetricKeyForAdapterLink(statsLink, true);
        minuteStats.latestValue = Utils.getNowMicrosUtc();
        minuteStats.sourceTimeMicrosUtc = Utils.getNowMicrosUtc();
        minuteStats.unit = PhotonModelConstants.UNIT_MICROSECONDS;

        Collection<Operation> operations = new ArrayList<>();
        URI inMemoryStatsUri = UriUtils.buildStatsUri(getHost(), currentState.computeLink);
        operations.add(Operation.createPost(inMemoryStatsUri).setBody(minuteStats));
        List<ResourceMetrics> metricsList = new ArrayList<>();
        populateResourceMetrics(metricsList,
                getLastCollectionMetricKeyForAdapterLink(statsLink, false),
                minuteStats, currentState.computeLink);

        // TODO: Support case when stats list has data for multiple resources
        // https://jira-hzn.eng.vmware.com/browse/VSYM-3121
        String computeId = UriUtils.getLastPathSegment(currentState.computeLink);

        InMemoryResourceMetric hourlyMemoryState = new InMemoryResourceMetric();
        hourlyMemoryState.timeSeriesStats = new HashMap<>();
        hourlyMemoryState.documentSelfLink = computeId.concat(StatsConstants.HOUR_SUFFIX);

        InMemoryResourceMetric dailyMemoryState = new InMemoryResourceMetric();
        dailyMemoryState.timeSeriesStats = new HashMap<>();
        dailyMemoryState.documentSelfLink = computeId.concat(StatsConstants.DAILY_SUFFIX);

        for (ComputeStats stats : currentState.statsList) {
            // TODO: https://jira-hzn.eng.vmware.com/browse/VSYM-330
            for (Entry<String, List<ServiceStat>> entries : stats.statValues.entrySet()) {
                // sort stats by source time
                Collections.sort(entries.getValue(),
                        (o1, o2) -> o1.sourceTimeMicrosUtc.compareTo(o2.sourceTimeMicrosUtc));
                // Persist every data point
                for (ServiceStat serviceStat : entries.getValue()) {
                    String computeLink = stats.computeLink;
                    if (computeLink == null) {
                        computeLink = currentState.computeLink;
                    }
                    // update in-memory stats
                    updateInMemoryStats(hourlyMemoryState, entries.getKey(), serviceStat,
                            StatsConstants.BUCKET_SIZE_HOURS_IN_MILLIS);
                    updateInMemoryStats(dailyMemoryState, entries.getKey(), serviceStat,
                            StatsConstants.BUCKET_SIZE_DAYS_IN_MILLIS);

                    populateResourceMetrics(metricsList, entries.getKey(),
                            serviceStat, computeLink);
                }
            }
        }
        for (ResourceMetrics metrics : metricsList) {
            operations.add(Operation.createPost(getHost(), ResourceMetricsService.FACTORY_LINK)
                    .setBodyNoCloning(metrics));
        }
        operations.add(Operation.createPost(getHost(), InMemoryResourceMetricService.FACTORY_LINK)
                .setBodyNoCloning(hourlyMemoryState));
        operations.add(Operation.createPost(getHost(), InMemoryResourceMetricService.FACTORY_LINK)
                .setBodyNoCloning(dailyMemoryState));

        // If there are no stats reported, just finish the task.
        if (operations.size() == 0) {
            SingleResourceStatsCollectionTaskState nextStatePatch = new SingleResourceStatsCollectionTaskState();
            nextStatePatch.taskInfo = TaskUtils.createTaskState(TaskStage.FINISHED);
            TaskUtils.sendPatch(this, nextStatePatch);
            return;
        }

        // Save each data point sequentially to create time based monotonically increasing sequence.
        OperationSequence.create(operations.toArray(new Operation[operations.size()]))
                .setCompletion((ops, exc) -> {
                    if (exc != null) {
                        logWarning("Failed stats collection: %s",
                                exc.values().iterator().next().getMessage());
                        TaskUtils.sendFailurePatch(this,
                                new SingleResourceStatsCollectionTaskState(), exc.values());
                        return;
                    }
                    SingleResourceStatsCollectionTaskState nextStatePatch = new SingleResourceStatsCollectionTaskState();
                    nextStatePatch.taskInfo = TaskUtils.createTaskState(TaskStage.FINISHED);
                    TaskUtils.sendPatch(this, nextStatePatch);
                })
                .sendWith(this);
    }

    private void updateInMemoryStats(InMemoryResourceMetric inMemoryMetric, String metricKey,
            ServiceStat serviceStat, int bucketSize) {
        // update in-memory stats
        if (inMemoryMetric.timeSeriesStats.containsKey(metricKey)) {
            inMemoryMetric.timeSeriesStats.get(metricKey)
                    .add(serviceStat.sourceTimeMicrosUtc, serviceStat.latestValue,
                            serviceStat.latestValue);
        } else {
            TimeSeriesStats tStats = new TimeSeriesStats(2, bucketSize,
                    EnumSet.allOf(AggregationType.class));
            tStats.add(serviceStat.sourceTimeMicrosUtc, serviceStat.latestValue,
                    serviceStat.latestValue);
            inMemoryMetric.timeSeriesStats.put(metricKey, tStats);
        }
    }

    private void populateResourceMetrics(List<ResourceMetrics> metricsList,
            String metricName,
            ServiceStat serviceStat,
            String computeLink) {
        if (Double.isNaN(serviceStat.latestValue)) {
            return;
        }
        ResourceMetrics metricsObjToUpdate = null;
        for (ResourceMetrics metricsObj : metricsList) {
            if (metricsObj.documentSelfLink.startsWith(UriUtils.getLastPathSegment(computeLink)) &&
                    metricsObj.timestampMicrosUtc.equals(serviceStat.sourceTimeMicrosUtc)) {
                metricsObjToUpdate = metricsObj;
                break;
            }
        }
        if (metricsObjToUpdate == null) {
            metricsObjToUpdate = new ResourceMetrics();
            metricsObjToUpdate.documentSelfLink = StatsUtil.getMetricKey(computeLink, Utils.getNowMicrosUtc());
            metricsObjToUpdate.entries = new HashMap<>();
            metricsObjToUpdate.timestampMicrosUtc = serviceStat.sourceTimeMicrosUtc;
            metricsList.add(metricsObjToUpdate);
        }
        metricsObjToUpdate.entries.put(metricName, serviceStat.latestValue);
    }

    /**
     * Gets the last collection for a compute for a given adapter URI.
     * As a first step, the in memory stats for the compute are queried and if the metric
     * for the last collection time is found, then the timestamp for that is returned.
     *
     * Else, the ResoureMetric table is queried and the latest version of the metric is used
     * to determine the last collection time for the stats.
     */
    private void populateLastCollectionTimeForMetricsInStatsRequest(
            SingleResourceStatsCollectionTaskState currentState,
            ComputeStatsRequest computeStatsRequest, URI patchUri, List<String> tenantLinks) {
        URI computeStatsUri = UriUtils
                .buildStatsUri(UriUtils.buildUri(getHost(), currentState.computeLink));
        Operation.createGet(computeStatsUri)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere("Could not get the last collection time from in memory stats: %s",
                                Utils.toString(e));
                        // get the value from the persisted store.
                        populateLastCollectionTimeFromPersistenceStore(currentState,
                                computeStatsRequest, patchUri, tenantLinks);
                        return;
                    }
                    ServiceStats serviceStats = o.getBody(ServiceStats.class);
                    String statsAdapterLink = getAdapterLinkFromURI(patchUri);
                    String lastSuccessfulRunMetricKey = getLastCollectionMetricKeyForAdapterLink(
                            statsAdapterLink, true);
                    if (serviceStats.entries.containsKey(lastSuccessfulRunMetricKey)) {
                        ServiceStat lastRunStat = serviceStats.entries
                                .get(lastSuccessfulRunMetricKey);
                        computeStatsRequest.lastCollectionTimeMicrosUtc =
                                lastRunStat.sourceTimeMicrosUtc;
                        sendStatsRequestToAdapter(currentState, patchUri, computeStatsRequest);
                    } else {
                        populateLastCollectionTimeFromPersistenceStore(currentState,
                                computeStatsRequest, patchUri, tenantLinks);
                    }
                })
                .sendWith(this);
    }

    /**
     * Queries the metric for the last successful run and sets that value in the compute stats request.
     * This value is used to determine the window size for which the stats collection happens from the provider.
     */
    private void populateLastCollectionTimeFromPersistenceStore(
            SingleResourceStatsCollectionTaskState currentState,
            ComputeStatsRequest computeStatsRequest, URI patchUri, List<String> tenantLinks) {
        String statsAdapterLink = getAdapterLinkFromURI(patchUri);
        String lastSuccessfulRunMetricKey = getLastCollectionMetricKeyForAdapterLink(
                statsAdapterLink, false);
        Query.Builder builder = Query.Builder.create();
        builder.addKindFieldClause(ResourceMetrics.class);
        builder.addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                UriUtils.buildUriPath(ResourceMetricsService.FACTORY_LINK, UriUtils.getLastPathSegment(currentState.computeLink)),
                MatchType.PREFIX);
        builder.addRangeClause( QuerySpecification.buildCompositeFieldName(ResourceMetrics.FIELD_NAME_ENTRIES, lastSuccessfulRunMetricKey),
                NumericRange.createDoubleRange(Double.MIN_VALUE, Double.MAX_VALUE, true, true));
        QueryTask task = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.SORT)
                .orderDescending(ServiceDocument.FIELD_NAME_SELF_LINK, TypeName.STRING)
                .addOption(QueryOption.TOP_RESULTS)
                .setResultLimit(1)
                .addOption(QueryOption.EXPAND_CONTENT)
                .setQuery(builder.build()).build();
        task.tenantLinks = tenantLinks;
        Operation.createPost(getHost(), ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(task)
                .setConnectionSharing(true)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere(
                                "Could not get the last collection time from persisted metrics: %s",
                                Utils.toString(e));
                        // Still continue calling into the adapter if the last known time for
                        // successful collection is not known
                        sendStatsRequestToAdapter(currentState,
                                patchUri, computeStatsRequest);
                        return;
                    }
                    // If the persisted metric can be found, use the value of the last successful
                    // collection time otherwise do not set any value for the last collection time
                    // while sending the request to the adapter.
                    QueryTask responseTask = o.getBody(QueryTask.class);
                    if (responseTask.results.documentCount > 0) {
                        Object rawMetricObj = responseTask.results.documents
                                .get(responseTask.results.documentLinks.get(0));
                        ResourceMetrics rawMetrics = Utils.fromJson(rawMetricObj,
                                ResourceMetrics.class);
                        computeStatsRequest.lastCollectionTimeMicrosUtc =
                                rawMetrics.timestampMicrosUtc;
                    }
                    sendStatsRequestToAdapter(currentState, patchUri, computeStatsRequest);
                })
                .sendWith(this);
    }

    /**
     * Sends the Stats request to the Stats adapter
     */
    private void sendStatsRequestToAdapter(SingleResourceStatsCollectionTaskState currentState,
            URI patchUri, Object patchBody) {
        sendRequest(Operation.createPatch(patchUri)
                .setBody(patchBody)
                .setCompletion((patchOp, patchEx) -> {
                    if (patchEx != null) {
                        TaskUtils.sendFailurePatch(this, currentState, patchEx);
                    }
                }));
    }

    /**
     * Forms the key to be used for looking up the last collection time for a given stats adapter.
     */
    public String getLastCollectionMetricKeyForAdapterLink(String statsAdapterLink,
            boolean appendBucketSuffix) {
        String lastSuccessfulRunMetricKey = StatsUtil.getMetricKeyPrefix(statsAdapterLink,
                PhotonModelConstants.LAST_SUCCESSFUL_STATS_COLLECTION_TIME);
        if (appendBucketSuffix) {
            lastSuccessfulRunMetricKey = lastSuccessfulRunMetricKey + StatsConstants.MIN_SUFFIX;
        }
        return lastSuccessfulRunMetricKey;
    }

    /**
     * Returns the path from the patchUri.
     */
    private String getAdapterLinkFromURI(URI patchUri) {
        return patchUri.getPath();
    }
}
