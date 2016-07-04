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

package com.vmware.photon.controller.model.adapters.vsphere.stats;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.vmware.photon.controller.model.adapters.vsphere.VimUtils;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.PerfMetricId;
import com.vmware.vim25.PerfQuerySpec;

public class StatCollectionContext {
    private final PerfQuerySpec spec;
    private final Map<Integer, SamplesAggregator> aggregatorsByPerfCounterId;

    public StatCollectionContext(ManagedObjectReference ref) {
        this.spec = new PerfQuerySpec();
        this.spec.setEntity(ref);
        this.aggregatorsByPerfCounterId = new HashMap<>();
    }

    /**
     * Intervals are predefined. See
     * https://www.vmware.com/support/developer/vc-sdk/visdk41pubs/ApiReference/vim.PerformanceManager.html#updateHistoricalInterval
     * @param intervalId
     * @param durationBackInTime
     * @param unit
     */
    public void limitResults(int intervalId, long durationBackInTime, TimeUnit unit) {
        long now = System.currentTimeMillis();
        // allow for some clock drift
        int clockDrift = 30 * 60 * 1000;

        this.spec.setStartTime(VimUtils.convertMillisToXmlCalendar(
                now - unit.toMillis(durationBackInTime) - clockDrift));

        this.spec.setEndTime(VimUtils.convertMillisToXmlCalendar(now + clockDrift));

        this.spec.setIntervalId(intervalId);
    }

    /**
     * Add a metric to the query and an aggregator to process it later.
     * @param metric
     * @param samplesAggregator
     */
    public void addMetric(PerfMetricId metric, SamplesAggregator samplesAggregator) {
        this.aggregatorsByPerfCounterId.put(metric.getCounterId(), samplesAggregator);
        this.spec.getMetricId().add(metric);
    }

    public PerfQuerySpec getSpec() {
        return this.spec;
    }

    public SamplesAggregator getFactory(int counterId) {
        return this.aggregatorsByPerfCounterId.get(counterId);
    }
}
