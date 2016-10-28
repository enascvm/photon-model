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

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Consumer;

import com.vmware.photon.controller.model.adapterapi.ComputeStatsRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse.ComputeStats;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSStatsNormalizer;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService.SingleResourceStatsCollectionTaskState;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService.SingleResourceTaskCollectionStage;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;

/**
 * Service to gather mock stats on AWS.
 */
public class AWSMockStatsService extends StatelessService {

    public AWSMockStatsService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    public static final String SELF_LINK = AWSUriPaths.PROVISIONING_AWS
            + "/mock-stats-adapter";

    private static final double CPU_UTILIZATION_MOCK_VALUE = 23.7;
    private static final double DISK_READ_OPS_MOCK_VALUE = 10.0;
    private static final double NETWORK_OUT_MOCK_VALUE = 400.0;
    private static final double NETWORK_PACKETS_IN_MOCK_VALUE = 20.0;

    public static final String[] METRIC_NAMES = { AWSConstants.CPU_UTILIZATION,
            AWSConstants.DISK_READ_BYTES, AWSConstants.DISK_WRITE_BYTES,
            AWSConstants.NETWORK_IN, AWSConstants.NETWORK_OUT,
            AWSConstants.CPU_CREDIT_USAGE, AWSConstants.CPU_CREDIT_BALANCE,
            AWSConstants.DISK_READ_OPS, AWSConstants.DISK_WRITE_OPS,
            AWSConstants.NETWORK_PACKETS_IN, AWSConstants.NETWORK_PACKETS_OUT,
            AWSConstants.STATUS_CHECK_FAILED, AWSConstants.STATUS_CHECK_FAILED_INSTANCE,
            AWSConstants.STATUS_CHECK_FAILED_SYSTEM };

    // Map of metric name to ServiceStat list
    private static final Map<String, List<ServiceStat>> MOCK_STATS_MAP;

    static {
        Map<String, List<ServiceStat>> statsMap = new HashMap<>();
        // populate mock stats values
        populateSingleMockMetric(AWSConstants.CPU_UTILIZATION, CPU_UTILIZATION_MOCK_VALUE,
                AWSConstants.UNIT_PERCENT, statsMap);
        populateSingleMockMetric(AWSConstants.DISK_READ_OPS, DISK_READ_OPS_MOCK_VALUE,
                AWSConstants.UNIT_COUNT, statsMap);
        populateSingleMockMetric(AWSConstants.NETWORK_OUT, NETWORK_OUT_MOCK_VALUE,
                AWSConstants.UNIT_BYTES, statsMap);
        populateSingleMockMetric(AWSConstants.NETWORK_PACKETS_IN, NETWORK_PACKETS_IN_MOCK_VALUE,
                AWSConstants.UNIT_COUNT, statsMap);

        MOCK_STATS_MAP = Collections.unmodifiableMap(statsMap);
    }

    private class AWSMockStatsDataHolder {
        public ComputeStateWithDescription computeState;
        public ComputeStateWithDescription parentComputeState;
        public ComputeStatsRequest statsRequest;
        public ComputeStats statsResponse;
        public boolean isComputeHost;

        public AWSMockStatsDataHolder() {
            this.statsResponse = new ComputeStats();
            // create a thread safe map to hold stats values for resource
            this.statsResponse.statValues = new ConcurrentSkipListMap<>();
        }
    }

    @Override
    public void handleStart(Operation startPost) {
        super.handleStart(startPost);
    }

    @Override
    public void handleStop(Operation delete) {
        super.handleStop(delete);
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
        AWSMockStatsDataHolder statsData = new AWSMockStatsDataHolder();
        statsData.statsRequest = statsRequest;
        getVMDescription(statsData);
    }

    private void getVMDescription(AWSMockStatsDataHolder statsData) {
        Consumer<Operation> onSuccess = (op) -> {
            statsData.computeState = op.getBody(ComputeStateWithDescription.class);
            statsData.isComputeHost = isComputeHost(statsData.computeState.description);
            if (statsData.isComputeHost) {
                getMockStats(statsData, METRIC_NAMES);
            } else {
                getParentVMDescription(statsData);
            }
        };
        URI computeUri = UriUtils.extendUriWithQuery(statsData.statsRequest.resourceReference,
                UriUtils.URI_PARAM_ODATA_EXPAND,
                Boolean.TRUE.toString());
        AdapterUtils.getServiceState(this, computeUri, onSuccess, getFailureConsumer(statsData));
    }

    private void getParentVMDescription(AWSMockStatsDataHolder statsData) {
        Consumer<Operation> onSuccess = (op) -> {
            statsData.parentComputeState = op.getBody(ComputeStateWithDescription.class);
            getMockStats(statsData, METRIC_NAMES);
        };
        URI computeUri = UriUtils.extendUriWithQuery(
                UriUtils.buildUri(getHost(), statsData.computeState.parentLink),
                UriUtils.URI_PARAM_ODATA_EXPAND,
                Boolean.TRUE.toString());
        AdapterUtils.getServiceState(this, computeUri, onSuccess, getFailureConsumer(statsData));
    }

    /**
     * Gets mock EC2 statistics.
     *
     * @param statsData The context object for mock stats.
     * @param metricNames The metrics names to gather stats for.
     */
    private void getMockStats(AWSMockStatsDataHolder statsData, String[] metricNames) {
        for (String metricName : metricNames) {
            // start feeding mock stats data
            if (MOCK_STATS_MAP.get(metricName) != null) {
                logFine("Retrieving %s mock metric from AWS", metricName);
                List<ServiceStat> statValue = MOCK_STATS_MAP.get(metricName);
                statsData.statsResponse.statValues
                        .put(AWSStatsNormalizer.getNormalizedStatKeyValue(metricName), statValue);
            }
        }

        SingleResourceStatsCollectionTaskState respBody = new SingleResourceStatsCollectionTaskState();
        statsData.statsResponse.computeLink = statsData.computeState.documentSelfLink;
        respBody.statsAdapterReference = UriUtils.buildUri(getHost(), SELF_LINK);
        respBody.taskStage = SingleResourceTaskCollectionStage.valueOf(statsData.statsRequest.nextStage);
        respBody.statsList = new ArrayList<>();
        respBody.statsList.add(statsData.statsResponse);

        this.sendRequest(Operation.createPatch(statsData.statsRequest.taskReference)
                .setBody(respBody));
    }

    private Consumer<Throwable> getFailureConsumer(AWSMockStatsDataHolder statsData) {
        return ((t) -> {
            AdapterUtils.sendFailurePatchToProvisioningTask(this,
                    statsData.statsRequest.taskReference, t);
        });
    }

    /**
     * Returns if the given compute description is a compute host or not.
     */
    private boolean isComputeHost(ComputeDescription computeDescription) {
        List<String> supportedChildren = computeDescription.supportedChildren;
        return supportedChildren != null && supportedChildren.contains(ComputeType.VM_GUEST.name());
    }

    /**
     * Gets mock EC2 statistics.
     *
     * @param metricName The metric names to gather stats for.
     * @param latestValue The stats value of the metricName.
     * @param unit The stats unit of the metricName.
     * @param statsMap The map of metric name to ServiceStat list.
     */
    private static void populateSingleMockMetric(String metricName, double latestValue, String unit,
             Map<String, List<ServiceStat>> statsMap) {
        ServiceStat stat = new ServiceStat();
        stat.latestValue = latestValue;
        stat.unit = AWSStatsNormalizer.getNormalizedUnitValue(unit);
        List<ServiceStat> statDatapoints = new ArrayList<>();
        statDatapoints.add(stat);

        statsMap.put(metricName, statDatapoints);
    }
}
