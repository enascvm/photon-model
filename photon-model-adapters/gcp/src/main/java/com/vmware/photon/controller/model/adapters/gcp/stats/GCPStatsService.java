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

package com.vmware.photon.controller.model.adapters.gcp.stats;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.vmware.photon.controller.model.adapterapi.ComputeStatsRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse.ComputeStats;
import com.vmware.photon.controller.model.adapters.gcp.GCPUriPaths;
import com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants;
import com.vmware.photon.controller.model.adapters.gcp.podo.authorization.GCPAccessTokenResponse;
import com.vmware.photon.controller.model.adapters.gcp.podo.stats.GCPMetricResponse;
import com.vmware.photon.controller.model.adapters.gcp.podo.stats.TimeSeries;
import com.vmware.photon.controller.model.adapters.gcp.utils.GCPStatsNormalizer;
import com.vmware.photon.controller.model.adapters.gcp.utils.GCPUtils;
import com.vmware.photon.controller.model.adapters.gcp.utils.JSONWebToken;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.security.util.EncryptionUtils;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService.SingleResourceStatsCollectionTaskState;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService.SingleResourceTaskCollectionStage;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Metrics collection service for Google Cloud Platform. Gets authenticated using OAuth
 * API. Collects instance level and host level metrics for Google Compute Engine instances using
 * Stackdriver monitoring API.
 */
public class GCPStatsService extends StatelessService {
    public static final String SELF_LINK = GCPUriPaths.GCP_STATS_ADAPTER;

    /**
     * Stores GCP metric names and their corresponding units.
     * Metric units are not provided as a part of the response by the API, hence they are
     * hard coded.
     * TODO: VSYM-1462 - Get metric units by making a request to monitoring API
     */
    public static final String[][] METRIC_NAMES_UNITS = {{GCPConstants.CPU_UTILIZATION,
            GCPConstants.UNIT_PERCENT}, {GCPConstants.DISK_READ_BYTES, GCPConstants.UNIT_BYTE},
            {GCPConstants.DISK_READ_OPERATIONS, GCPConstants.UNIT_COUNT},
            {GCPConstants.DISK_WRITE_BYTES, GCPConstants.UNIT_BYTE},
            {GCPConstants.DISK_WRITE_OPERATIONS, GCPConstants.UNIT_COUNT},
            {GCPConstants.NETWORK_IN_BYTES, GCPConstants.UNIT_BYTE},
            {GCPConstants.NETWORK_IN_PACKETS, GCPConstants.UNIT_COUNT},
            {GCPConstants.NETWORK_OUT_BYTES, GCPConstants.UNIT_BYTE},
            {GCPConstants.NETWORK_OUT_PACKETS, GCPConstants.UNIT_COUNT}};

    public GCPStatsService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    /**
     * Stages of GCP stats collection
     */
    private enum StatsCollectionStage {
        /**
         * Default first stage for the service. Collecting the VM description.
         */
        VM_DESC,

        /**
         * Collecting compute host description.
         */
        PARENT_VM_DESC,

        /**
         * Collecting credentials from AuthCredentialService.
         */
        CREDENTIALS,

        /**
         * Collecting project name from Resource group.
         */
        PROJECT_ID,

        /**
         * Getting access token using OAuth.
         */
        ACCESS_TOKEN,

        /**
         * Collecting stats by making requests to Stackdriver monitoring API.
         */
        STATS,

        /**
         * Error stage
         */
        ERROR,

        /**
         * Stage to handle mock request, directly patches back to parent.
         */
        FINISHED
    }

    /**
     * Data holder class for GCPStatsService. Stores all the fields used by the service.
     */
    private class GCPStatsDataHolder {
        public ComputeStateWithDescription computeDesc;
        public ComputeStateWithDescription parentDesc;
        public StatsCollectionStage stage;
        public AuthCredentialsService.AuthCredentialsServiceState parentAuth;
        public ComputeStatsRequest statsRequest;
        public ComputeStats statsResponse;
        public Throwable error;
        public AtomicInteger numResponses = new AtomicInteger(0);
        public boolean isComputeHost;
        public String userEmail;
        public String privateKey;
        public String accessToken;
        public String projectId;
        public String instanceId;
        public Operation gcpStatsCollectionOperation;
        public TaskManager taskManager;

        public GCPStatsDataHolder(Operation op) {
            this.gcpStatsCollectionOperation = op;
            this.statsResponse = new ComputeStats();
            // create a thread safe map to hold stats values for resource
            this.statsResponse.statValues = new ConcurrentSkipListMap<>();
        }
    }

    /**
     * Convert timestamp associated  with metric from response format (RFC 3339) to microseconds.
     * @param timestamp Timestamp of metric in RFC 3339 format.
     * @return Timestamp of metric in microseconds.
     * @throws ParseException
     */
    private long getTimestampInMicros(String timestamp) throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat(GCPConstants.TIME_INTERVAL_FORMAT);
        dateFormat.setTimeZone(TimeZone.getTimeZone(GCPConstants.UTC_TIMEZONE_ID));
        Date date = dateFormat.parse(timestamp);
        long time = TimeUnit.MILLISECONDS.toMicros(date.getTime());
        return time;
    }

    /**
     * Gets the start time parameter required for metric request URI.
     * New SimpleDateFromat and Date instances are created for every call as these classes are
     * not thread safe.
     * @return startTime parameter of the metric request URI in RFC 3339 format.
     */
    private static String getStartTime() {
        SimpleDateFormat dateFormat = new SimpleDateFormat(GCPConstants.TIME_INTERVAL_FORMAT);
        dateFormat.setTimeZone(TimeZone.getTimeZone(GCPConstants.UTC_TIMEZONE_ID));

        /*
         * Subtract 4 minutes from current time.
         * Currently, 3 minutes old metrics are obtained to account for latency.
         * Request for more recent metrics mostly results in empty response body.
         */
        Date date = new Date(TimeUnit.MICROSECONDS.toMillis(Utils.getNowMicrosUtc()) - GCPConstants.START_TIME_MILLIS);
        return dateFormat.format(date);
    }

    /**
     * Gets the end time parameter required for metric request URI in RFC 3339 format.
     * New SimpleDateFromat and Date instances are created for every call as these classes are
     * not thread safe.
     * @return endTime parameter of the metric request URI in RFC 3339 format.
     */
    private static String getEndTime() {
        SimpleDateFormat dateFormat = new SimpleDateFormat(GCPConstants.TIME_INTERVAL_FORMAT);
        dateFormat.setTimeZone(TimeZone.getTimeZone(GCPConstants.UTC_TIMEZONE_ID));

        /*
         * Subtract 3 minutes from current time.
         * Data points collected by the monitoring API are spaced 60 seconds apart, hence we set
         * interval to 60 seconds to obtain one data point at the most.
         * This sometimes results in empty response body, if the interval contains no data points.
         */
        Date date = new Date(TimeUnit.MICROSECONDS.toMillis(Utils.getNowMicrosUtc()) - GCPConstants.END_TIME_MILLIS);
        return dateFormat.format(date);
    }

    /**
     * Builds the metric request filter value string.
     * @param metricName Name of the metric to be requested.
     * @param statsData The GCPStatsDataHolder instance containing statsRequest.
     * @return Metric request filter value string
     */
    private String getRequestFilterValue(String metricName, GCPStatsDataHolder statsData) {
        String filterValue;
        /* If the given resource is a compute host, do not add instance ID parameter in
         * the filter value.
         * If the given resource is a VM, add instance ID parameter in the filter value.
         */
        if (statsData.isComputeHost) {
            filterValue = GCPConstants.METRIC_TYPE_FILTER + "=\""
                    + GCPConstants.METRIC_NAME_PREFIX + metricName + "\"";
        } else {
            filterValue = GCPConstants.METRIC_TYPE_FILTER + "=\""
                    + GCPConstants.METRIC_NAME_PREFIX + metricName + "\""
                    + "+AND+" + GCPConstants.INSTANCE_NAME_FILTER + "=\""
                    + statsData.instanceId + "\"";
        }

        return filterValue;
    }

    /**
     * Method for building VM level stats metric request URI.
     * The URI has nested key, value pairs in the query.
     * Use of UriUtils methods for creating the entire query results in double encoding, thus
     * manual string concatenation is used.
     * @param metricName Name of the metric to be requested.
     * @param statsData The GCPStatsDataHolder instance containing statsRequest.
     * @return Metric request URI for VM level stats.
     */
    private URI getRequestUriForVM(String metricName, GCPStatsDataHolder statsData) {
        try {
            URI baseUri = new URI(GCPConstants.MONITORING_API_URI
                    + statsData.projectId + GCPConstants.TIMESERIES_PREFIX);

            String filterValue = getRequestFilterValue(metricName, statsData);

            URI uri = UriUtils.extendUriWithQuery(baseUri, GCPConstants.FILTER_KEY, filterValue,
                    GCPConstants.INTERVAL_START_TIME, getStartTime(),
                    GCPConstants.INTERVAL_END_TIME, getEndTime());

            return uri;
        } catch (URISyntaxException e) {
            handleError(statsData, e);
            return null;
        }
    }

    /**
     * Method for building host level stats metric request URI.
     * The URI has nested key, value pairs in the query.
     * Use of UriUtils methods for creating the entire query results in double encoding, thus
     * manual string concatenation is used.
     * @param metricName Name of the metric to be requested.
     * @param statsData The GCPStatsDataHolder instance containing statsRequest.
     * @return Metric request URI for host level stats.
     */
    private URI getRequestUriForHost(String metricName, GCPStatsDataHolder statsData) {
        try {
            URI baseUri = new URI(GCPConstants.MONITORING_API_URI
                    + statsData.projectId + GCPConstants.TIMESERIES_PREFIX);

            String filterValue = getRequestFilterValue(metricName, statsData);

            URI uri;
            /*
             * Values for aggregation enums are different for CPU Utilization and other metrics.
             * CPU Utilization readings are instantaneous, mean of all data points is taken
             * during aggregation.
             * All other stats are observed over time, sum of all data points is taken during
             * aggregation.
             * Thus, build a different URI if the metric is CPU Utilization.
             */
            if (metricName.equals(GCPConstants.CPU_UTILIZATION)) {
                uri = UriUtils.extendUriWithQuery(baseUri, GCPConstants.AGGREGATION_ALIGNMENT_PERIOD,
                    GCPConstants.AGGREGATION_ALIGNMENT_PERIOD_VALUE,
                    GCPConstants.AGGREGATION_CROSS_SERIES_REDUCER, GCPConstants.CPU_UTIL_CROSS_SERIES_REDUCER_VALUE,
                    GCPConstants.AGGREGATION_PER_SERIES_ALIGNER, GCPConstants.CPU_UTIL_PER_SERIES_ALIGNER_VALUE,
                    GCPConstants.FILTER_KEY, filterValue,
                    GCPConstants.INTERVAL_START_TIME, getStartTime(),
                    GCPConstants.INTERVAL_END_TIME, getEndTime());
            } else {
                uri = UriUtils.extendUriWithQuery(baseUri, GCPConstants.AGGREGATION_ALIGNMENT_PERIOD,
                        GCPConstants.AGGREGATION_ALIGNMENT_PERIOD_VALUE,
                        GCPConstants.AGGREGATION_CROSS_SERIES_REDUCER, GCPConstants.CROSS_SERIES_REDUCER_VALUE,
                        GCPConstants.AGGREGATION_PER_SERIES_ALIGNER, GCPConstants.PER_SERIES_ALIGNER_VALUE,
                        GCPConstants.FILTER_KEY, filterValue,
                        GCPConstants.INTERVAL_START_TIME, getStartTime(),
                        GCPConstants.INTERVAL_END_TIME, getEndTime());
            }
            return uri;
        } catch (URISyntaxException e) {
            handleError(statsData, e);
            return null;
        }
    }

    /**
     * The REST PATCH request handler. This is the entry of starting stats collection.
     * @param patch Operation which should contain request body.
     */
    @Override
    public void handlePatch(Operation patch) {
        setOperationHandlerInvokeTimeStat(patch);
        if (!patch.hasBody()) {
            patch.fail(new IllegalArgumentException("body is required"));
            return;
        }
        patch.complete();
        ComputeStatsRequest statsRequest = patch.getBody(ComputeStatsRequest.class);
        GCPStatsDataHolder statsData = new GCPStatsDataHolder(patch);
        statsData.statsRequest = statsRequest;
        statsData.taskManager = new TaskManager(this, statsRequest.taskReference,
                statsRequest.resourceLink());

        // If mock mode is enabled, patch back to the parent.
        if (statsData.statsRequest.isMockRequest) {
            statsData.stage = StatsCollectionStage.FINISHED;
            handleStatsRequest(statsData);
        } else {
            statsData.stage = StatsCollectionStage.VM_DESC;
            handleStatsRequest(statsData);
        }
    }

    /**
     * The flow for dealing with each stage in the service.
     * @param statsData The GCPStatsDataHolder instance which decides the current stage.
     */
    public void handleStatsRequest(GCPStatsDataHolder statsData) {
        switch (statsData.stage) {
        case VM_DESC:
            getVMDescription(statsData, StatsCollectionStage.PARENT_VM_DESC,
                    StatsCollectionStage.CREDENTIALS);
            break;
        case PARENT_VM_DESC:
            getParentVMDescription(statsData, StatsCollectionStage.CREDENTIALS);
            break;
        case CREDENTIALS:
            getParentAuth(statsData, StatsCollectionStage.PROJECT_ID);
            break;
        case PROJECT_ID:
            getProjectId(statsData, StatsCollectionStage.ACCESS_TOKEN);
            break;
        case ACCESS_TOKEN:
            try {
                getAccessToken(statsData, StatsCollectionStage.STATS);
            } catch (GeneralSecurityException | IOException e) {
                handleError(statsData, e);
                return;
            }
            break;
        case STATS:
            getStats(statsData, StatsCollectionStage.FINISHED);
            break;
        case ERROR:
            statsData.taskManager.patchTaskToFailure(statsData.error);
            break;
        case FINISHED:
            // Patch status to parent task
            statsData.taskManager.finishTask();
            break;
        default:
            String err = String.format("Unknown GCP stats collection stage %s ", statsData.stage.toString());
            logSevere(err);
            statsData.error = new IllegalStateException(err);
            statsData.gcpStatsCollectionOperation.fail(statsData.error);
            // Patch failure back to parent task
            statsData.taskManager.patchTaskToFailure(statsData.error);
        }
    }

    /**
     * Gets the description of the VM for which stats are to be collected.
     * Sets the VM ID, required for making metric requests, in current GCPStatsDataHolder
     * instance.
     * @param statsData The GCPStatsDataHolder instance containing statsRequest.
     * @param nextStage The next stage of StatsCollectionStage for the service.
     */
    private void getVMDescription(GCPStatsDataHolder statsData,
            StatsCollectionStage nextStageForVM,
            StatsCollectionStage nextStageForComputeHost) {
        Consumer<Operation> onSuccess = (op) -> {
            statsData.computeDesc = op.getBody(ComputeStateWithDescription.class);
            statsData.instanceId = statsData.computeDesc.id;
            statsData.isComputeHost = AdapterUtils.isComputeHost(statsData.computeDesc);

            // If the given resource is a compute host, directly get parent auth.
            if (statsData.isComputeHost) {
                statsData.stage = nextStageForComputeHost;
            } else {
                statsData.stage = nextStageForVM;
            }
            handleStatsRequest(statsData);
        };
        URI computeUri = UriUtils.extendUriWithQuery(statsData.statsRequest.resourceReference,
                UriUtils.URI_PARAM_ODATA_EXPAND, Boolean.TRUE.toString());
        AdapterUtils.getServiceState(this, computeUri, onSuccess, getFailureConsumer(statsData));
    }

    /**
     * Gets the description of the compute host corresponding to the VM for which stats
     * are to be collected.
     * @param statsData The GCPStatsDataHolder instance containing statsRequest.
     * @param nextStage The next stage of StatsCollectionStage for the service.
     */
    private void getParentVMDescription(GCPStatsDataHolder statsData, StatsCollectionStage nextStage) {
        Consumer<Operation> onSuccess = (op) -> {
            statsData.parentDesc = op.getBody(ComputeStateWithDescription.class);
            statsData.stage = nextStage;
            handleStatsRequest(statsData);
        };
        URI computeUri = UriUtils.extendUriWithQuery(
                UriUtils.buildUri(getHost(), statsData.computeDesc.parentLink),
                UriUtils.URI_PARAM_ODATA_EXPAND, Boolean.TRUE.toString());
        AdapterUtils.getServiceState(this, computeUri, onSuccess, getFailureConsumer(statsData));
    }

    /**
     * Gets the credentials for the corresponding compute host from AuthCredentialService.
     * @param statsData The GCPStatsDataHolder instance containing statsRequest.
     * @param nextStage The next stage of StatsCollectionStage for the service.
     */
    private void getParentAuth(GCPStatsDataHolder statsData, StatsCollectionStage nextStage) {
        Consumer<Operation> onSuccess = (op) -> {
            statsData.parentAuth = op.getBody(AuthCredentialsServiceState.class);
            statsData.userEmail = statsData.parentAuth.userEmail;
            statsData.privateKey = EncryptionUtils.decrypt(statsData.parentAuth.privateKey);
            statsData.stage = nextStage;
            handleStatsRequest(statsData);
        };
        String authLink;
        if (statsData.isComputeHost) {
            authLink = statsData.computeDesc.description.authCredentialsLink;
        } else {
            authLink = statsData.parentDesc.description.authCredentialsLink;
        }
        AdapterUtils.getServiceState(this, authLink, onSuccess, getFailureConsumer(statsData));
    }

    /**
     * Gets the project name for the corresponding compute host.
     * @param statsData The GCPStatsDataHolder instance containing statsRequest.
     * @param nextStage The next stage of StatsCollectionStage for the service.
     */
    private void getProjectId(GCPStatsDataHolder statsData, StatsCollectionStage nextStage) {
        Consumer<Operation> onSuccess = (op) -> {
            ResourceGroupState rgs = op.getBody(ResourceGroupState.class);
            statsData.projectId = rgs.name;
            statsData.stage = nextStage;
            handleStatsRequest(statsData);
        };

        ArrayList<String> groupLink;

        /*
         * If given resource is a compute host, we directly get groupLinks from the resource
         * description.
         */
        if (statsData.isComputeHost) {
            groupLink = new ArrayList<>(statsData.computeDesc.description.groupLinks);
        } else {
            groupLink = new ArrayList<>(statsData.parentDesc.description.groupLinks);
        }
        AdapterUtils.getServiceState(this, groupLink.get(0), onSuccess, getFailureConsumer(statsData));
    }

    /**
     * Gets the access token required for making requests to the monitoring API.
     * @param statsData The GCPStatsDataHolder instance containing statsRequest.
     * @param nextStage The next stage of StatsCollectionStage for the service.
     * @throws GeneralSecurityException
     * @throws IOException
     */
    private void getAccessToken(GCPStatsDataHolder statsData, StatsCollectionStage nextStage)
            throws GeneralSecurityException, IOException {
        Consumer<GCPAccessTokenResponse> onSuccess = (response) -> {
            statsData.accessToken = response.access_token;
            statsData.stage = nextStage;
            handleStatsRequest(statsData);
        };

        JSONWebToken jwt = new JSONWebToken(statsData.userEmail, GCPConstants.SCOPES,
                GCPUtils.privateKeyFromPkcs8(statsData.privateKey));
        String assertion = jwt.getAssertion();
        GCPUtils.getAccessToken(this, assertion, onSuccess, getFailureConsumer(statsData));
    }

    /**
     * Makes request to the monitoring API to get stats.
     * @param statsData The GCPStatsDataHolder instance containing statsRequest.
     * @param nextStage The next stage of StatsCollectionStage for the service.
     */
    private void getStats(GCPStatsDataHolder statsData, StatsCollectionStage nextStage) {
        for (String[] metricInfo : METRIC_NAMES_UNITS) {
            URI uri;

            /*
             * Do not specify instance id in the metric request URI if the given resource is
             * a compute host and specify aggregation parameters in the metric request URI
             * in order to get host level stats.
             */
            if (statsData.isComputeHost) {
                uri = getRequestUriForHost(metricInfo[0], statsData);
            } else {
                uri = getRequestUriForVM(metricInfo[0], statsData);
            }

            if (uri == null) {
                statsData.error = new IllegalStateException("The request URI is null.");
                statsData.stage = StatsCollectionStage.ERROR;
                handleStatsRequest(statsData);
            }

            Operation.createGet(uri).addRequestHeader(Operation.AUTHORIZATION_HEADER,
                    GCPConstants.AUTH_HEADER_BEARER_PREFIX + statsData.accessToken)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            handleError(statsData, e);
                            return;
                        }
                        GCPMetricResponse response = o.getBody(GCPMetricResponse.class);
                        storeAndSendStats(statsData, metricInfo, response, nextStage);
                    }).sendWith(this);
        }
    }

    /**
     * Stores the stats in current GCPStatsDataHolder instance and patches back the response
     * to the caller task.
     * @param statsData The GCPStatsDataHolder instance containing statsRequest.
     * @param metricInfo Metric name and unit pair from METRIC_NAMES_UNITS array.
     * @param response The response from the monitoring API associated with metric in
     * the current metricInfo pair.
     * @param nextStage The next stage of StatsCollectionStage for the service.
     */
    private void storeAndSendStats(GCPStatsDataHolder statsData, String[] metricInfo,
            GCPMetricResponse response, StatsCollectionStage nextStage) {
        ServiceStat stat = new ServiceStat();
        List<ServiceStat> datapoint = new ArrayList<>();
        if (response.timeSeries != null) {
            TimeSeries ts = response.timeSeries[0];
            stat.latestValue = ts.points[0].value.int64Value == null ?
                    Double.parseDouble(ts.points[0].value.doubleValue) :
                    Double.parseDouble(ts.points[0].value.int64Value);
            stat.unit = GCPStatsNormalizer.getNormalizedUnitValue(metricInfo[1]);
            try {
                stat.sourceTimeMicrosUtc = getTimestampInMicros(ts.points[0]
                        .interval.startTime);
            } catch (ParseException e) {
                handleError(statsData, e);
                return;
            }
            datapoint = Collections.singletonList(stat);
        }
        statsData.statsResponse.statValues.put(GCPStatsNormalizer.getNormalizedStatKeyValue
                (metricInfo[0]), datapoint);

        // After all the metrics are collected, send them as a response to the caller task.
        if (statsData.numResponses.incrementAndGet() == METRIC_NAMES_UNITS.length) {
            SingleResourceStatsCollectionTaskState respBody = new SingleResourceStatsCollectionTaskState();
            statsData.statsResponse.computeLink = statsData.computeDesc.documentSelfLink;
            respBody.taskStage = SingleResourceTaskCollectionStage.valueOf(statsData.statsRequest.nextStage);
            respBody.statsList = Collections.singletonList(statsData.statsResponse);
            setOperationDurationStat(statsData.gcpStatsCollectionOperation);
            statsData.gcpStatsCollectionOperation.complete();
            respBody.statsAdapterReference = UriUtils.buildUri(getHost(), SELF_LINK);
            this.sendRequest(Operation.createPatch(statsData.statsRequest.taskReference)
                    .setBody(respBody));
        }
    }

    /**
     * Error handler for GCPStatsService.
     * @param statsData The GCPStatsDataHolder instance containing statsRequest.
     * @param e The throwable associated with error.
     */
    private void handleError(GCPStatsDataHolder statsData, Throwable e) {
        logSevere(e);
        statsData.error = e;
        statsData.stage = StatsCollectionStage.ERROR;
        handleStatsRequest(statsData);
    }

    /**
     * Failure consumer for  AdapterUtils.getServiceState method.
     * @param statsData The GCPStatsDataHolder instance containing statsRequest.
     * @return Throwable
     */
    private Consumer<Throwable> getFailureConsumer(GCPStatsDataHolder statsData) {
        return ((t) -> {
            statsData.taskManager.patchTaskToFailure(t);
        });
    }
}