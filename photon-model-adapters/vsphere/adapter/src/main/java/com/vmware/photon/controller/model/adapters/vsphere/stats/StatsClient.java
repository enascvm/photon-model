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

import static com.vmware.vim25.PerfStatsType.ABSOLUTE;
import static com.vmware.vim25.PerfStatsType.RATE;
import static com.vmware.vim25.PerfSummaryType.AVERAGE;
import static com.vmware.vim25.PerformanceManagerUnit.KILO_BYTES;
import static com.vmware.vim25.PerformanceManagerUnit.MEGA_HERTZ;
import static com.vmware.vim25.PerformanceManagerUnit.PERCENT;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.BaseHelper;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.Connection;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.GetMoRef;
import com.vmware.vim25.ArrayOfPerfCounterInfo;
import com.vmware.vim25.InvalidPropertyFaultMsg;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.PerfCounterInfo;
import com.vmware.vim25.PerfEntityMetric;
import com.vmware.vim25.PerfEntityMetricBase;
import com.vmware.vim25.PerfMetricId;
import com.vmware.vim25.PerfMetricIntSeries;
import com.vmware.vim25.PerfMetricSeries;
import com.vmware.vim25.PerfStatsType;
import com.vmware.vim25.PerfSummaryType;
import com.vmware.vim25.PerformanceManagerUnit;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.xenon.common.ServiceStats.ServiceStat;

public class StatsClient extends BaseHelper {

    private static final Logger logger = Logger.getLogger(StatsClient.class.getName());

    private static final ManagedObjectReference PERF_MGR_MOREF = new ManagedObjectReference();
    private static final String PROP_PERF_COUNTER = "perfCounter";

    /**
     * Values predefined in https://github.com/vmware/photon-model/wiki/computeService.
     * The "daily." prefix is a temporary solution.
     */
    private static final String DAILY_CPU_UTILIZATION_MHZ = "daily.cpuUtilizationMhz";
    private static final String DAILY_CPU_UTILIZATION_PCT = "daily.cpuUtilizationPct";
    private static final String DAILY_MEMORY_USED_BYTES = "daily.memoryUsedBytes";

    private static final String UNIT_BYTES = "bytes";
    private static final String GROUP_CPU = "cpu";
    private static final String GROUP_MEM = "mem";
    private static final String NAME_USAGEMHZ = "usagemhz";
    private static final String NAME_USAGE = "usage";
    private static final String NAME_CONSUMED = "consumed";

    /**
     * IntervalId is really time in seconds. See https://www.vmware.com/support/developer/vc-sdk/visdk41pubs/ApiReference/vim.HistoricalInterval.html
     * for other valid values.
     */
    private static final int DEFAULT_INTERVAL_ID = 300;

    static {
        PERF_MGR_MOREF.setType(VimNames.TYPE_PERFORMANCE_MANAGER);
        PERF_MGR_MOREF.setValue("PerfMgr");
    }

    private static final ConcurrentHashMap<URI, PerfCounterLookup> lookups = new ConcurrentHashMap<>();

    private final PerfCounterLookup perfCounterLookup;

    public StatsClient(Connection connection) throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        super(connection);
        this.perfCounterLookup = createLookup(connection);
    }

    /**
     * cache perfCounters per connections. The call is slow as the response can be about 400kb
     * of xml.
     * @param conn
     * @return
     */
    private static PerfCounterLookup createLookup(Connection conn) {
        return lookups.computeIfAbsent(conn.getURI(), u -> {
            GetMoRef get = new GetMoRef(conn);
            ArrayOfPerfCounterInfo counters;
            try {
                counters = get.entityProp(PERF_MGR_MOREF, PROP_PERF_COUNTER);
            } catch (Exception e) {
                throw new RuntimeException("cannot create lookup for " + u, e);
            }

            return new PerfCounterLookup(counters.getPerfCounterInfo());
        });
    }

    /**
     * Makes a PerfMetricId by looking up the counterId for the given group/name/rollupType/... params.
     *
     * https://www.vmware.com/support/developer/vc-sdk/visdk41pubs/ApiReference/vim.PerformanceManager.MetricId.html
     *
     * See <a href="https://www.vmware.com/support/developer/vc-sdk/visdk41pubs/ApiReference/cpu_counters.html">cpu counters</a>
     * See <a href="https://www.vmware.com/support/developer/vc-sdk/visdk41pubs/ApiReference/memory_counters.html>memory counters</a>
     * See <a href="https://www.vmware.com/support/developer/vc-sdk/visdk41pubs/ApiReference/network_counters.html">network counters</a>
     *
     * @param group
     * @param name
     * @param rollupType
     * @return null if a PerfCoutner cannot be found for the given params
     */
    private PerfMetricId findMetricId(String group, String name, PerfStatsType type,
            PerformanceManagerUnit unit, PerfSummaryType rollupType) {
        PerfMetricId res = new PerfMetricId();
        PerfCounterInfo counter = this.perfCounterLookup
                .getCounter(name, group, type, rollupType, unit);

        if (counter == null) {
            String msg = String
                    .format("Cannot find metric for %s/%s/%s/%s/%s", group, name, type,
                            rollupType.value(),
                            unit.value());
            logger.warning(msg);
            throw new IllegalArgumentException(msg);
        }
        res.setCounterId(counter.getKey());
        res.setInstance("");
        return res;
    }

    /**
     * See <a href="https://www.vmware.com/support/developer/vc-sdk/visdk41pubs/ApiReference/vim.PerformanceManager.html#queryStats">queryStats method</a>
     * @param ctx
     * @return
     * @throws RuntimeFaultFaultMsg
     */
    private List<ServiceStat> querySingleEntity(StatCollectionContext ctx)
            throws RuntimeFaultFaultMsg {
        List<PerfEntityMetricBase> metrics = getVimPort().queryPerf(PERF_MGR_MOREF,
                Collections.singletonList(ctx.getSpec()));

        List<ServiceStat> res = new ArrayList<>();

        if (metrics.isEmpty()) {
            // nothing fetched
            return Collections.emptyList();
        }

        // the metrics for the single entity
        PerfEntityMetric m = (PerfEntityMetric) metrics.get(0);

        for (PerfMetricSeries pms : m.getValue()) {
            PerfMetricId metricId = pms.getId();
            PerfMetricIntSeries series = (PerfMetricIntSeries) pms;
            SamplesAggregator factory = ctx.getFactory(metricId.getCounterId());

            PerfCounterInfo counter = this.perfCounterLookup
                    .getCounterByKey(metricId.getCounterId());

            ServiceStat stat = factory.createStat(counter, m.getSampleInfo(), series.getValue());
            if (stat != null) {
                res.add(stat);
            }
        }

        return res;
    }

    public List<ServiceStat> retrieveMetricsForVm(ManagedObjectReference vm)
            throws RuntimeFaultFaultMsg {

        StatCollectionContext ctx = new StatCollectionContext(vm);

        ctx.limitResults(DEFAULT_INTERVAL_ID, 24, TimeUnit.HOURS);

        addDefaultMetrics(ctx);
        return querySingleEntity(ctx);
    }

    private void addDefaultMetrics(StatCollectionContext ctx) {
        PerfMetricId cpuUsage = findMetricId(GROUP_CPU, NAME_USAGE, RATE, PERCENT, AVERAGE);
        ctx.addMetric(cpuUsage, new DailyAggregator(DAILY_CPU_UTILIZATION_PCT, PERCENT.value()) {
            @Override
            protected double convertValue(long value) {
                return super.toPercent(value);
            }
        });

        PerfMetricId cpuMhz = findMetricId(GROUP_CPU, NAME_USAGEMHZ, RATE, MEGA_HERTZ, AVERAGE);
        ctx.addMetric(cpuMhz, new DailyAggregator(DAILY_CPU_UTILIZATION_MHZ, MEGA_HERTZ.value()));

        PerfMetricId memoryConsumed = findMetricId(GROUP_MEM, NAME_CONSUMED, ABSOLUTE, KILO_BYTES,
                AVERAGE);
        ctx.addMetric(memoryConsumed, new DailyAggregator(DAILY_MEMORY_USED_BYTES, UNIT_BYTES) {
            @Override
            protected double convertValue(long value) {
                return super.toBytes(value);
            }
        });
    }

    public List<ServiceStat> retrieveMetricsForHost(ManagedObjectReference host)
            throws RuntimeFaultFaultMsg {
        StatCollectionContext ctx = new StatCollectionContext(host);

        ctx.limitResults(DEFAULT_INTERVAL_ID, 24, TimeUnit.HOURS);

        addDefaultMetrics(ctx);

        return querySingleEntity(ctx);
    }
}
