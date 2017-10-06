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

package com.vmware.photon.controller.model.monitoring;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceStats.TimeSeriesStats;
import com.vmware.xenon.common.ServiceStats.TimeSeriesStats.TimeBin;
import com.vmware.xenon.common.StatefulService;

/**
 * In-memory stateful service used to hold timeseries data for a resource
 * grouped by a specified time windows
 */
public class InMemoryResourceMetricService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.MONITORING + "/in-memory-metrics";

    public static FactoryService createFactory() {
        return FactoryService.createIdempotent(InMemoryResourceMetricService.class);
    }

    public InMemoryResourceMetricService() {
        super(InMemoryResourceMetric.class);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
    }

    public static class InMemoryResourceMetric extends ServiceDocument {
        /**
         * Map of metric key to the time series stats
         */
        public Map <String, TimeSeriesStats> timeSeriesStats;
    }

    @Override
    public void handlePut(Operation put) {
        if (!put.hasBody()) {
            put.fail(new IllegalArgumentException("body is required"));
        }

        try {
            InMemoryResourceMetric currentState = getState(put);
            InMemoryResourceMetric updatedState = getBody(put);
            // merge the state
            for (Entry<String, TimeSeriesStats> tsStats : updatedState.timeSeriesStats.entrySet()) {
                TimeSeriesStats currentStats = currentState.timeSeriesStats.get(tsStats.getKey());
                if (currentStats == null) {
                    currentState.timeSeriesStats.put(tsStats.getKey(), tsStats.getValue());
                } else {
                    for (Entry<Long, TimeBin> bin : tsStats.getValue().bins.entrySet()) {
                        for (int i = 0; i < bin.getValue().count; i++) {
                            currentStats.add(TimeUnit.MILLISECONDS.toMicros(bin.getKey()), bin.getValue().avg, bin.getValue().avg);
                        }
                    }
                }
            }
            setState(put, currentState);
            put.setBody(currentState).complete();
        } catch (Throwable t) {
            put.fail(t);
        }
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();
        com.vmware.photon.controller.model.ServiceUtils.setRetentionLimit(template);
        return template;
    }
}
