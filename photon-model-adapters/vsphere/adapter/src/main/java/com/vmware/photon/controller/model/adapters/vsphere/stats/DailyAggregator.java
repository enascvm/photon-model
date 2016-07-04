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

import static java.awt.SystemColor.info;

import java.util.EnumSet;
import java.util.List;

import com.vmware.vim25.PerfCounterInfo;
import com.vmware.vim25.PerfSampleInfo;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.ServiceStats.TimeSeriesStats;
import com.vmware.xenon.common.ServiceStats.TimeSeriesStats.AggregationType;

/**
 * Aggregates on a daily basis.
 * See https://www.vmware.com/support/developer/vc-sdk/visdk41pubs/ApiReference/vim.PerformanceManager.html#updateHistoricalInterval
 * The values are already aggregated at a 5minutes interval so just populate the timeSeries.
 */
public class DailyAggregator implements SamplesAggregator {

    private final String name;
    private final String unit;

    public DailyAggregator(String name, String unit) {
        this.name = name;
        this.unit = unit;
    }

    @Override
    public ServiceStat createStat(PerfCounterInfo pc, List<PerfSampleInfo> infos,
            List<Long> values) {

        if (infos == null || infos.isEmpty()) {
            return null;
        }

        ServiceStat res = new ServiceStat();

        res.name = this.name;
        res.unit = this.unit;

        PerfSampleInfo info = null;
        double converted = 0;

        int intervalLengthMinutes = 5;
        res.timeSeriesStats = new TimeSeriesStats(24 * 60 / intervalLengthMinutes,
                intervalLengthMinutes * 60 * 1000,
                EnumSet.allOf(AggregationType.class));

        for (int i = 0; i < infos.size(); i++) {
            info = infos.get(i);
            Long value = values.get(i);

            converted = convertValue(value);
            res.timeSeriesStats.add(getSampleTimestampMicros(info), converted);
        }

        res.sourceTimeMicrosUtc = res.lastUpdateMicrosUtc = getSampleTimestampMicros(info);
        res.latestValue = converted;
        return res;
    }

    protected double convertValue(long value) {
        return value;
    }

    protected double toPercent(long value) {
        return value / 100.0;
    }

    protected double toBytes(long valueKb) {
        return valueKb * 1000.0;
    }

    private long getSampleTimestampMicros(PerfSampleInfo info) {
        return info.getTimestamp().toGregorianCalendar().getTimeInMillis() * 1000;
    }
}
