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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.vmware.photon.controller.model.adapterapi.ComputeStatsRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse.ComputeStats;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService.SingleResourceStatsCollectionTaskState;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService.SingleResourceTaskCollectionStage;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class MockStatsAdapter extends StatelessService {

    public static final String SELF_LINK = "/mock-stats-adapter";
    public static final String KEY_1 = "key1";
    public static final String KEY_2 = "key2";
    public static final String KEY_3 = "key3";
    public static final String UNIT_1 = "unit1";
    public static final String UNIT_2 = "unit2";

    private AtomicLong counter = new AtomicLong(0);

    @Override
    public void handleStart(Operation startPost) {
        this.counter = new AtomicLong(0);
        super.handleStart(startPost);
    }

    @Override
    public void handleRequest(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        switch (op.getAction()) {
        case PATCH:
            op.complete();
            ComputeStatsRequest statsRequest = op.getBody(ComputeStatsRequest.class);
            SingleResourceStatsCollectionTaskState statsResponse = new SingleResourceStatsCollectionTaskState();
            Map<String, List<ServiceStat>> statValues = new HashMap<>();
            double currentCounter = this.counter.incrementAndGet();
            ServiceStat key1 = new ServiceStat();
            key1.latestValue = currentCounter;
            long timestampMicros = Utils.getNowMicrosUtc();
            key1.sourceTimeMicrosUtc = timestampMicros;
            key1.unit = UNIT_1;
            statValues.put(KEY_1, Collections.singletonList(key1));
            sendBatchResponse(statsRequest, statsResponse, statValues, false);
            statValues.clear();
            ServiceStat key2 = new ServiceStat();
            key2.latestValue = currentCounter;
            key2.sourceTimeMicrosUtc = timestampMicros;
            key2.unit = UNIT_2;
            statValues.put(KEY_2, Collections.singletonList(key2));
            sendBatchResponse(statsRequest, statsResponse, statValues, true);
            break;
        default:
            super.handleRequest(op);
        }
    }

    private void sendBatchResponse(ComputeStatsRequest statsRequest,
            SingleResourceStatsCollectionTaskState statsResponse,
            Map<String, List<ServiceStat>> statValues, boolean isFinalBatch) {
        ComputeStats cStat = new ComputeStats();
        cStat.statValues = statValues;
        cStat.computeLink = statsRequest.resourceReference.getPath();
        cStat.addCustomProperty("prop1", "val1");
        statsResponse.statsList = new ArrayList<>();
        statsResponse.statsList.add(cStat);
        statsResponse.taskStage = SingleResourceTaskCollectionStage.valueOf(statsRequest.nextStage);
        statsResponse.statsAdapterReference = UriUtils.buildUri(getHost(), SELF_LINK);
        statsResponse.isFinalBatch = isFinalBatch;
        this.sendRequest(Operation.createPatch(statsRequest.taskReference).setBody(statsResponse));
    }

}
