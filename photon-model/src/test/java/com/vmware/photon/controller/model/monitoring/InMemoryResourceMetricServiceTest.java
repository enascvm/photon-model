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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.helpers.BaseModelTest;

import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceStats.TimeSeriesStats;
import com.vmware.xenon.common.ServiceStats.TimeSeriesStats.AggregationType;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * This class implements tests for the {@link InMemoryResourceMetricService} class.
 */
@RunWith(InMemoryResourceMetricServiceTest.class)
@SuiteClasses({ InMemoryResourceMetricServiceTest.ConstructorTest.class,
        InMemoryResourceMetricServiceTest.HandleStartTest.class })
public class InMemoryResourceMetricServiceTest extends Suite {

    private static final long HOUR_IN_MILLIS = TimeUnit.HOURS.toMillis(1);

    public InMemoryResourceMetricServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    private static InMemoryResourceMetricService.InMemoryResourceMetric buildValidStartState() {
        InMemoryResourceMetricService.InMemoryResourceMetric  statState = new InMemoryResourceMetricService.InMemoryResourceMetric();
        statState.timeSeriesStats = new HashMap<>();
        TimeSeriesStats statsEntry = new TimeSeriesStats(2, HOUR_IN_MILLIS, EnumSet.of(AggregationType.AVG));
        statsEntry.add(Utils.getNowMicrosUtc(), 1, 1);
        statState.timeSeriesStats.put("key1", statsEntry);
        return statState;
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {
        private InMemoryResourceMetricService StatsService = new InMemoryResourceMetricService();

        @Before
        public void setupTest() {
            this.StatsService = new InMemoryResourceMetricService();
        }

        @Test
        public void testServiceOptions() {
            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.REPLICATION,
                    Service.ServiceOption.OWNER_SELECTION,
                    Service.ServiceOption.IDEMPOTENT_POST);
            assertThat(this.StatsService.getOptions(), is(expected));
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleStartTest extends BaseModelTest {
        @Before
        public void setupTest() {
            this.host.startFactory(new InMemoryResourceMetricService());
            this.host.waitForServiceAvailable(InMemoryResourceMetricService.FACTORY_LINK);
        }

        @Test
        public void testValidStartState() throws Throwable {
            InMemoryResourceMetricService.InMemoryResourceMetric startState = buildValidStartState();
            InMemoryResourceMetricService.InMemoryResourceMetric returnState = postServiceSynchronously(
                    InMemoryResourceMetricService.FACTORY_LINK,
                            startState, InMemoryResourceMetricService.InMemoryResourceMetric.class);

            assertNotNull(returnState);
        }

        @Test
        public void testIdempotentPostService() throws Throwable {
            InMemoryResourceMetricService.InMemoryResourceMetric metric = buildValidStartState();
            metric.documentSelfLink = "default";
            postServiceSynchronously(
                    InMemoryResourceMetricService.FACTORY_LINK,
                            metric, InMemoryResourceMetricService.InMemoryResourceMetric.class);
            InMemoryResourceMetricService.InMemoryResourceMetric returnState =
                    getServiceSynchronously(UriUtils.buildUriPath(InMemoryResourceMetricService.FACTORY_LINK, metric.documentSelfLink),
                    InMemoryResourceMetricService.InMemoryResourceMetric.class);
            assertEquals(returnState.timeSeriesStats.size(), 1);
            assertTrue(returnState.timeSeriesStats.get("key1").bins.values().iterator().next().count == 1);

            metric.timeSeriesStats = new HashMap<>();
            TimeSeriesStats statsEntry = new TimeSeriesStats(2, HOUR_IN_MILLIS, EnumSet.of(AggregationType.AVG));
            statsEntry.add(Utils.getNowMicrosUtc(), 2, 2);
            metric.timeSeriesStats.put("key1", statsEntry);

            returnState = postServiceSynchronously(
                    InMemoryResourceMetricService.FACTORY_LINK,
                          metric, InMemoryResourceMetricService.InMemoryResourceMetric.class);
            returnState = getServiceSynchronously(UriUtils.buildUriPath(InMemoryResourceMetricService.FACTORY_LINK, metric.documentSelfLink),
                    InMemoryResourceMetricService.InMemoryResourceMetric.class);
            assertEquals(returnState.timeSeriesStats.size(), 1);
            assertTrue(returnState.timeSeriesStats.get("key1").bins.values().iterator().next().count == 2);
            assertTrue(returnState.timeSeriesStats.get("key1").bins.values().iterator().next().avg == 1.5);
        }
    }
}
