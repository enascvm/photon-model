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
import static org.junit.Assert.assertNotNull;

import java.util.EnumSet;
import java.util.HashMap;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.monitoring.ResourceMetricService;

import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceStats.TimeSeriesStats.DataPoint;
import com.vmware.xenon.common.Utils;


/**
 * This class implements tests for the {@link ResourceMetricService} class.
 */
@RunWith(ResourceAggregateMetricsServiceTest.class)
@SuiteClasses({ ResourceAggregateMetricsServiceTest.ConstructorTest.class,
        ResourceAggregateMetricsServiceTest.HandleStartTest.class })
public class ResourceAggregateMetricsServiceTest extends Suite {

    public ResourceAggregateMetricsServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    private static ResourceAggregateMetricsService.ResourceAggregateMetricsState buildValidStartState() {
        ResourceAggregateMetricsService.ResourceAggregateMetricsState statState = new ResourceAggregateMetricsService.ResourceAggregateMetricsState();
        statState.computeServiceLink = "initialLink";
        statState.aggregations = new HashMap<String, DataPoint>();
        statState.sourceTimeMicrosUtc = Utils.getNowMicrosUtc();
        return statState;
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {
        private ResourceAggregateMetricsService StatsService = new ResourceAggregateMetricsService();

        @Before
        public void setupTest() {
            this.StatsService = new ResourceAggregateMetricsService();
        }

        @Test
        public void testServiceOptions() {
            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.CONCURRENT_GET_HANDLING,
                    Service.ServiceOption.PERSISTENCE,
                    Service.ServiceOption.ON_DEMAND_LOAD);
            assertThat(this.StatsService.getOptions(), is(expected));
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleStartTest extends BaseModelTest {
        @Test
        public void testValidStartState() throws Throwable {
            ResourceAggregateMetricsService.ResourceAggregateMetricsState startState = buildValidStartState();
            ResourceAggregateMetricsService.ResourceAggregateMetricsState returnState = postServiceSynchronously(
                    ResourceAggregateMetricsService.FACTORY_LINK,
                            startState, ResourceAggregateMetricsService.ResourceAggregateMetricsState.class);
            assertThat(returnState.computeServiceLink, is(startState.computeServiceLink));
            assertNotNull(returnState);
        }

        @Test
        public void testDuplicatePost() throws Throwable {
            ResourceAggregateMetricsService.ResourceAggregateMetricsState startState = buildValidStartState();
            ResourceAggregateMetricsService.ResourceAggregateMetricsState returnState = postServiceSynchronously(
                    ResourceAggregateMetricsService.FACTORY_LINK,
                            startState, ResourceAggregateMetricsService.ResourceAggregateMetricsState.class);

            assertNotNull(returnState);
            assertThat(returnState.computeServiceLink, is(startState.computeServiceLink));
            startState.computeServiceLink = "new-link";
            returnState = postServiceSynchronously(ResourceAggregateMetricsService.FACTORY_LINK,
                            startState, ResourceAggregateMetricsService.ResourceAggregateMetricsState.class);
            assertThat(returnState.computeServiceLink, is(startState.computeServiceLink));
        }

        @Test
        public void testMissingBody() throws Throwable {
            postServiceSynchronously(
                    ResourceMetricService.FACTORY_LINK,
                    null,
                    ResourceMetricService.ResourceMetric.class,
                    IllegalArgumentException.class);
        }

        @Test
        public void testMissingValue() throws Throwable {
            ResourceAggregateMetricsService.ResourceAggregateMetricsState startState = buildValidStartState();
            startState.computeServiceLink = null;

            postServiceSynchronously(ResourceAggregateMetricsService.FACTORY_LINK,
                    startState, ResourceAggregateMetricsService.ResourceAggregateMetricsState.class,
                    IllegalArgumentException.class);
        }
    }
}
