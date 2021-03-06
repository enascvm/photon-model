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

package com.vmware.photon.controller.model.adapters.awsadapter;

import static com.vmware.photon.controller.model.util.PhotonModelUriUtils.createInventoryUri;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsyncClient;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse.ComputeStats;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSStatsNormalizer;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService.SingleResourceStatsCollectionTaskState;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService.SingleResourceTaskCollectionStage;
import com.vmware.photon.controller.model.tasks.monitoring.StatsUtil;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Service to gather stats on AWS.
 */
public class AWSStatsService extends StatelessService {
    private AWSClientManager clientManager;

    public static final String AWS_COLLECTION_PERIOD_SECONDS = UriPaths.PROPERTY_PREFIX + "AWSStatsService.collectionPeriod";
    private static final long DEFAULT_AWS_COLLECTION_PERIOD_SECONDS = TimeUnit.HOURS.toSeconds(1);

    public AWSStatsService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    public static final String SELF_LINK = AWSUriPaths.AWS_STATS_ADAPTER;

    public static final String[] METRIC_NAMES = { AWSConstants.CPU_UTILIZATION };

    public static final String[] AGGREGATE_METRIC_NAMES_ACROSS_INSTANCES = {
            AWSConstants.CPU_UTILIZATION};

    private static final String[] STATISTICS = { "Average", "SampleCount" };
    private static final String NAMESPACE = "AWS/EC2";
    private static final String DIMENSION_INSTANCE_ID = "InstanceId";
    // This is the maximum window size for which stats should be collected in case the last
    // collection time is not specified.
    // Defaulting to 6 hrs.
    private static final long MAX_METRIC_COLLECTION_WINDOW_IN_MINUTES = TimeUnit.HOURS.toMinutes(6);

    // Cost
    private static final String BILLING_NAMESPACE = "AWS/Billing";
    private static final String DIMENSION_CURRENCY = "Currency";
    private static final String DIMENSION_CURRENCY_VALUE = "USD";
    private static final int COST_COLLECTION_WINDOW_IN_DAYS = 14;
    private static final int COST_COLLECTION_PERIOD_IN_SECONDS = 14400;
    private static final int COST_COLLECTION_PERIOD_IN_HOURS = 4;
    // AWS stores all billing data in us-east-1 zone.
    private static final String COST_ZONE_ID = "us-east-1";
    private static final int NUM_OF_COST_DATAPOINTS_IN_A_DAY = 6;

    private class AWSStatsDataHolder {
        public ComputeStateWithDescription computeDesc;
        public ComputeStateWithDescription parentDesc;
        public AuthCredentialsService.AuthCredentialsServiceState parentAuth;
        public ComputeStatsRequest statsRequest;
        public ComputeStats statsResponse;
        public AtomicInteger numResponses = new AtomicInteger(0);
        public AmazonCloudWatchAsyncClient statsClient;
        public AmazonCloudWatchAsyncClient billingClient;
        public boolean isComputeHost;
        public TaskManager taskManager;

        public AWSStatsDataHolder() {
            this.statsResponse = new ComputeStats();
            // create a thread safe map to hold stats values for resource
            this.statsResponse.statValues = new ConcurrentSkipListMap<>();
        }
    }

    /**
     * Extend default 'start' logic with loading AWS client.
     */
    @Override
    public void handleStart(Operation op) {

        this.clientManager = AWSClientManagerFactory
                .getClientManager(AWSConstants.AwsClientType.CLOUD_WATCH);

        super.handleStart(op);
    }

    @Override
    public void handleStop(Operation delete) {
        AWSClientManagerFactory.returnClientManager(this.clientManager,
                AWSConstants.AwsClientType.CLOUD_WATCH);
        super.handleStop(delete);
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        ComputeStatsRequest statsRequest = op.getBody(ComputeStatsRequest.class);
        op.complete();
        TaskManager taskManager = new TaskManager(this, statsRequest.taskReference,
                statsRequest.resourceLink());
        if (statsRequest.isMockRequest) {
            // patch status to parent task
            taskManager.finishTask();
            return;
        }
        AWSStatsDataHolder statsData = new AWSStatsDataHolder();
        statsData.statsRequest = statsRequest;
        statsData.taskManager = taskManager;
        getVMDescription(statsData);
    }

    private void getVMDescription(AWSStatsDataHolder statsData) {
        Consumer<Operation> onSuccess = (op) -> {
            statsData.computeDesc = op.getBody(ComputeStateWithDescription.class);
            statsData.isComputeHost = isComputeHost(statsData.computeDesc);

            // if we have a compute host then we directly get the auth.
            if (statsData.isComputeHost) {
                getParentAuth(statsData);
            } else {
                getParentVMDescription(statsData);
            }
        };
        URI computeUri = UriUtils.extendUriWithQuery(statsData.statsRequest.resourceReference,
                UriUtils.URI_PARAM_ODATA_EXPAND,
                Boolean.TRUE.toString());
        AdapterUtils.getServiceState(this, computeUri, onSuccess, getFailureConsumer(statsData));
    }

    private void getParentVMDescription(AWSStatsDataHolder statsData) {
        Consumer<Operation> onSuccess = (op) -> {
            statsData.parentDesc = op.getBody(ComputeStateWithDescription.class);
            getParentAuth(statsData);
        };
        URI computeUri = UriUtils.extendUriWithQuery(
                UriUtils.buildUri(getHost(), statsData.computeDesc.parentLink),
                UriUtils.URI_PARAM_ODATA_EXPAND,
                Boolean.TRUE.toString());
        AdapterUtils.getServiceState(this, computeUri, onSuccess, getFailureConsumer(statsData));
    }

    private void getParentAuth(AWSStatsDataHolder statsData) {
        Consumer<Operation> onSuccess = (op) -> {
            statsData.parentAuth = op.getBody(AuthCredentialsServiceState.class);
            getStats(statsData);
        };
        String authLink;
        if (statsData.isComputeHost) {
            authLink = statsData.computeDesc.description.authCredentialsLink;
        } else {
            authLink = statsData.parentDesc.description.authCredentialsLink;
        }
        URI authURI = createInventoryUri(this.getHost(), authLink);
        AdapterUtils.getServiceState(this, authURI, onSuccess, getFailureConsumer(statsData));
    }

    private Consumer<Throwable> getFailureConsumer(AWSStatsDataHolder statsData) {
        return ((t) -> {
            statsData.taskManager.patchTaskToFailure(t);
        });
    }

    private void getStats(AWSStatsDataHolder statsData) {
        if (statsData.isComputeHost) {
            // Get host level stats for billing and ec2.
            getAWSAsyncBillingClient(statsData)
                    .whenComplete((client, t) -> {
                        if (t != null) {
                            getFailureConsumer(statsData).accept(t);
                            return;
                        }

                        statsData.billingClient = client;
                        getBillingStats(statsData);
                    });
            return;
        }

        getAWSAsyncStatsClient(statsData)
                .whenComplete((client, t) -> {
                    if (t != null) {
                        getFailureConsumer(statsData).accept(t);
                        return;
                    }

                    statsData.statsClient = client;
                    getEC2Stats(statsData, METRIC_NAMES, false);
                });
    }

    /**
     * Gets EC2 statistics.
     *
     * @param statsData The context object for stats.
     * @param metricNames The metrics names to gather stats for.
     * @param isAggregateStats Indicates where we are interested in aggregate stats or not.
     */
    private void getEC2Stats(AWSStatsDataHolder statsData, String[] metricNames,
            boolean isAggregateStats) {
        Long collectionPeriod = Long.getLong(AWS_COLLECTION_PERIOD_SECONDS,
                DEFAULT_AWS_COLLECTION_PERIOD_SECONDS);
        for (String metricName : metricNames) {
            GetMetricStatisticsRequest metricRequest = new GetMetricStatisticsRequest();
            // get datapoint for the for the passed in time window.
            try {
                setRequestCollectionWindow(
                        TimeUnit.MINUTES.toMicros(MAX_METRIC_COLLECTION_WINDOW_IN_MINUTES),
                        statsData.statsRequest.lastCollectionTimeMicrosUtc, collectionPeriod,
                        metricRequest);
            } catch (IllegalStateException e) {
                // no data to process. notify parent
                statsData.taskManager.finishTask();
                return;
            }
            metricRequest.setPeriod(collectionPeriod.intValue());
            metricRequest.setStatistics(Arrays.asList(STATISTICS));
            metricRequest.setNamespace(NAMESPACE);

            // Provide instance id dimension only if it is not aggregate stats.
            if (!isAggregateStats) {
                List<Dimension> dimensions = new ArrayList<>();
                Dimension dimension = new Dimension();
                dimension.setName(DIMENSION_INSTANCE_ID);
                String instanceId = statsData.computeDesc.id;
                dimension.setValue(instanceId);
                dimensions.add(dimension);
                metricRequest.setDimensions(dimensions);
            }

            metricRequest.setMetricName(metricName);

            logFine(() -> String.format("Retrieving %s metric from AWS", metricName));
            AsyncHandler<GetMetricStatisticsRequest, GetMetricStatisticsResult> resultHandler =
                    new AWSStatsHandler(statsData, metricNames.length, isAggregateStats);
            statsData.statsClient.getMetricStatisticsAsync(metricRequest, resultHandler);
        }
    }

    private void getBillingStats(AWSStatsDataHolder statsData) {
        Dimension dimension = new Dimension();
        dimension.setName(DIMENSION_CURRENCY);
        dimension.setValue(DIMENSION_CURRENCY_VALUE);
        GetMetricStatisticsRequest request = new GetMetricStatisticsRequest();
        // AWS pushes billing metrics every 4 hours. However the timeseries returned does not have
        // static time stamps associated with the data points. The timestamps range from
        // (currentTime - 4 hrs) and are spaced at 4 hrs.
        // Get all 14 days worth of estimated charges data by default when last collection time is not set.
        // Otherwise set the window to lastCollectionTime - 4 hrs.
        Long lastCollectionTimeForEstimatedCharges = null;
        Long collectionPeriod = Long.getLong(AWS_COLLECTION_PERIOD_SECONDS,
                DEFAULT_AWS_COLLECTION_PERIOD_SECONDS);
        if (statsData.statsRequest.lastCollectionTimeMicrosUtc != null) {
            lastCollectionTimeForEstimatedCharges =
                    statsData.statsRequest.lastCollectionTimeMicrosUtc
                            - TimeUnit.HOURS.toMicros(COST_COLLECTION_PERIOD_IN_HOURS);

        }

        // defaulting to fetch 14 days of estimated charges data
        try {
            setRequestCollectionWindow(
                    TimeUnit.DAYS.toMicros(COST_COLLECTION_WINDOW_IN_DAYS),
                    lastCollectionTimeForEstimatedCharges,
                    collectionPeriod,
                    request);
        } catch (IllegalStateException e) {
            // no data to process. notify parent
            statsData.taskManager.finishTask();
            return;
        }

        request.setPeriod(COST_COLLECTION_PERIOD_IN_SECONDS);
        request.setStatistics(Arrays.asList(STATISTICS));
        request.setNamespace(BILLING_NAMESPACE);
        request.setDimensions(Collections.singletonList(dimension));
        request.setMetricName(AWSConstants.ESTIMATED_CHARGES);

        logFine(() -> String.format("Retrieving %s metric from AWS",
                AWSConstants.ESTIMATED_CHARGES));
        AsyncHandler<GetMetricStatisticsRequest, GetMetricStatisticsResult> resultHandler =
                new AWSBillingStatsHandler(statsData, lastCollectionTimeForEstimatedCharges);
        statsData.billingClient.getMetricStatisticsAsync(request, resultHandler);
    }

    /**
     * Sets the window of time for the statistics collection. If the last collection time is passed in the compute stats request
     * then that value is used for getting the stats data from the provider else the default configured window for stats
     * collection is used.
     *
     * Also, if the last collection time is really a long time ago, then the maximum collection window is honored to collect
     * the stats from the provider.
     */
    private void setRequestCollectionWindow(Long defaultStartWindowMicros,
            Long lastCollectionTimeMicros,
            Long collectionPeriodInSeconds,
            GetMetricStatisticsRequest request) {
        long endTimeMicros = StatsUtil.computeIntervalBeginMicros(Utils.getNowMicrosUtc(),
                TimeUnit.SECONDS.toMillis(collectionPeriodInSeconds));
        request.setEndTime(new Date(TimeUnit.MICROSECONDS.toMillis(endTimeMicros)));
        Long maxCollectionWindowStartTime = TimeUnit.MICROSECONDS.toMillis(endTimeMicros) -
                TimeUnit.MICROSECONDS.toMillis(defaultStartWindowMicros);
        // If the last collection time is available, then the stats data from the provider will be
        // fetched from that time onwards. Else, the stats collection is performed starting from the
        // default configured window.
        if (lastCollectionTimeMicros == null) {
            request.setStartTime(new Date(maxCollectionWindowStartTime));
            return;
        }
        if (lastCollectionTimeMicros != 0) {
            if (lastCollectionTimeMicros > endTimeMicros) {
                throw new IllegalStateException(
                        "The last stats collection time cannot be in the future.");
                // Check if the last collection time calls for collection to earlier than the
                // maximum defined windows size.
                // In that case default to the maximum collection window.
            } else if (TimeUnit.MICROSECONDS
                    .toMillis(lastCollectionTimeMicros) < maxCollectionWindowStartTime) {
                request.setStartTime(new Date(maxCollectionWindowStartTime));
                return;
            }
            long beginMicros = StatsUtil.computeIntervalBeginMicros(lastCollectionTimeMicros,
                    TimeUnit.SECONDS.toMillis(collectionPeriodInSeconds));
            request.setStartTime(new Date(
                    TimeUnit.MICROSECONDS.toMillis(beginMicros)));
        }
    }

    private DeferredResult<AmazonCloudWatchAsyncClient> getAWSAsyncStatsClient(
            AWSStatsDataHolder statsData) {
        return this.clientManager.getOrCreateCloudWatchClientAsync(statsData.parentAuth,
                statsData.computeDesc.description.regionId, this,
                statsData.statsRequest.isMockRequest);
    }

    private DeferredResult<AmazonCloudWatchAsyncClient> getAWSAsyncBillingClient(
            AWSStatsDataHolder statsData) {
        return this.clientManager.getOrCreateCloudWatchClientAsync(statsData.parentAuth,
                COST_ZONE_ID, this, statsData.statsRequest.isMockRequest);
    }

    /**
     * Billing specific async handler.
     */
    private class AWSBillingStatsHandler implements
            AsyncHandler<GetMetricStatisticsRequest, GetMetricStatisticsResult> {

        private AWSStatsDataHolder statsData;
        private OperationContext opContext;
        private Long lastCollectionTimeMicrosUtc;

        public AWSBillingStatsHandler(AWSStatsDataHolder statsData,
                Long lastCollectionTimeMicrosUtc) {
            this.statsData = statsData;
            this.opContext = OperationContext.getOperationContext();
            this.lastCollectionTimeMicrosUtc = lastCollectionTimeMicrosUtc;
        }

        @Override
        public void onError(Exception exception) {
            OperationContext.restoreOperationContext(this.opContext);
            this.statsData.taskManager.patchTaskToFailure(exception);
        }

        @Override
        public void onSuccess(GetMetricStatisticsRequest request,
                GetMetricStatisticsResult result) {
            try {
                OperationContext.restoreOperationContext(this.opContext);
                List<Datapoint> dpList = result.getDatapoints();
                // Sort the data points in increasing order of timestamp to calculate Burn rate
                Collections
                        .sort(dpList, (o1, o2) -> o1.getTimestamp().compareTo(o2.getTimestamp()));

                List<ServiceStat> estimatedChargesDatapoints = new ArrayList<>();
                if (dpList != null && dpList.size() != 0) {
                    for (Datapoint dp : dpList) {
                        // If the datapoint collected is older than the last collection time, skip it.
                        if (this.lastCollectionTimeMicrosUtc != null &&
                                TimeUnit.MILLISECONDS.toMicros(dp.getTimestamp()
                                        .getTime())
                                        <= this.lastCollectionTimeMicrosUtc) {
                            continue;
                        }

                        // If there is no lastCollectionTime or the datapoint collected in newer
                        // than the lastCollectionTime, push it.
                        ServiceStat stat = new ServiceStat();
                        stat.latestValue = dp.getAverage();
                        stat.unit = AWSStatsNormalizer
                                .getNormalizedUnitValue(DIMENSION_CURRENCY_VALUE);
                        stat.sourceTimeMicrosUtc = TimeUnit.MILLISECONDS
                                .toMicros(dp.getTimestamp().getTime());
                        estimatedChargesDatapoints.add(stat);
                    }

                    this.statsData.statsResponse.statValues.put(
                            AWSStatsNormalizer.getNormalizedStatKeyValue(result.getLabel()),
                            estimatedChargesDatapoints);

                    // Calculate average burn rate only if there is more than 1 datapoint available.
                    // This will ensure that NaN errors will not occur.
                    if (dpList.size() > 1) {
                        ServiceStat averageBurnRate = new ServiceStat();
                        averageBurnRate.latestValue = AWSUtils.calculateAverageBurnRate(dpList);
                        averageBurnRate.unit = AWSStatsNormalizer
                                .getNormalizedUnitValue(DIMENSION_CURRENCY_VALUE);
                        averageBurnRate.sourceTimeMicrosUtc = Utils.getSystemNowMicrosUtc();
                        this.statsData.statsResponse.statValues.put(
                                AWSStatsNormalizer
                                        .getNormalizedStatKeyValue(AWSConstants.AVERAGE_BURN_RATE),
                                Collections.singletonList(averageBurnRate));
                    }

                    // Calculate current burn rate only if there is more than 1 day worth of data available.
                    if (dpList.size() > NUM_OF_COST_DATAPOINTS_IN_A_DAY) {
                        ServiceStat currentBurnRate = new ServiceStat();
                        currentBurnRate.latestValue = AWSUtils.calculateCurrentBurnRate(dpList);
                        currentBurnRate.unit = AWSStatsNormalizer
                                .getNormalizedUnitValue(DIMENSION_CURRENCY_VALUE);
                        currentBurnRate.sourceTimeMicrosUtc = Utils.getSystemNowMicrosUtc();
                        this.statsData.statsResponse.statValues.put(
                                AWSStatsNormalizer
                                        .getNormalizedStatKeyValue(AWSConstants.CURRENT_BURN_RATE),
                                Collections.singletonList(currentBurnRate));
                    }
                }
                sendStats(this.statsData);
            } catch (Exception e) {
                this.statsData.taskManager.patchTaskToFailure(e);
            }
        }
    }

    private class AWSStatsHandler implements
            AsyncHandler<GetMetricStatisticsRequest, GetMetricStatisticsResult> {

        private final int numOfMetrics;
        private final Boolean isAggregateStats;
        private AWSStatsDataHolder statsData;
        private OperationContext opContext;

        public AWSStatsHandler(AWSStatsDataHolder statsData, int numOfMetrics,
                Boolean isAggregateStats) {
            this.statsData = statsData;
            this.numOfMetrics = numOfMetrics;
            this.isAggregateStats = isAggregateStats;
            this.opContext = OperationContext.getOperationContext();
        }

        @Override
        public void onError(Exception exception) {
            OperationContext.restoreOperationContext(this.opContext);
            this.statsData.taskManager.patchTaskToFailure(exception);
        }

        @Override
        public void onSuccess(GetMetricStatisticsRequest request,
                GetMetricStatisticsResult result) {
            try {
                OperationContext.restoreOperationContext(this.opContext);
                List<ServiceStat> statDatapoints = new ArrayList<>();
                List<Datapoint> dpList = result.getDatapoints();
                if (dpList != null && dpList.size() != 0) {
                    for (Datapoint dp : dpList) {
                        ServiceStat stat = new ServiceStat();
                        stat.latestValue = dp.getAverage();
                        stat.unit = AWSStatsNormalizer.getNormalizedUnitValue(dp.getUnit());
                        stat.sourceTimeMicrosUtc = TimeUnit.MILLISECONDS
                                .toMicros(dp.getTimestamp().getTime());
                        statDatapoints.add(stat);
                    }

                    this.statsData.statsResponse.statValues
                            .put(AWSStatsNormalizer.getNormalizedStatKeyValue(result.getLabel()),
                                    statDatapoints);
                }

                if (this.statsData.numResponses.incrementAndGet() == this.numOfMetrics) {
                    sendStats(this.statsData);
                }
            } catch (Exception e) {
                this.statsData.taskManager.patchTaskToFailure(e);
            }
        }
    }

    private void sendStats(AWSStatsDataHolder statsData) {
        SingleResourceStatsCollectionTaskState respBody = new SingleResourceStatsCollectionTaskState();
        statsData.statsResponse.computeLink = statsData.computeDesc.documentSelfLink;
        respBody.taskStage = SingleResourceTaskCollectionStage
                .valueOf(statsData.statsRequest.nextStage);
        respBody.statsAdapterReference = UriUtils.buildUri(getHost(), SELF_LINK);
        respBody.statsList = new ArrayList<>();
        respBody.statsList.add(statsData.statsResponse);
        sendRequest(
                Operation.createPatch(statsData.statsRequest.taskReference)
                        .setBody(respBody));
    }
    /**
     * Returns if the given compute description is a compute host or not.
     */
    private boolean isComputeHost(ComputeStateWithDescription computeStateWithDescription) {
        return computeStateWithDescription.type == ComputeType.ENDPOINT_HOST;
    }
}
