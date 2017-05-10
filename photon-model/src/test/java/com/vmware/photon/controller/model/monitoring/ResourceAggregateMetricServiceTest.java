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

import java.util.EnumSet;
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
import com.vmware.xenon.common.ServiceStats.TimeSeriesStats.TimeBin;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * This class implements tests for the {@link ResourceAggregateMetricService} class.
 */
@RunWith(ResourceAggregateMetricServiceTest.class)
@SuiteClasses({ ResourceAggregateMetricServiceTest.ConstructorTest.class,
        ResourceAggregateMetricServiceTest.HandleStartTest.class })
public class ResourceAggregateMetricServiceTest extends Suite {

    private static final int DEFAULT_RETENTION_LIMIT_DAYS = 7;
    private static final long EXPIRATION_TIME = Utils.getNowMicrosUtc()
            + TimeUnit.DAYS.toMicros(DEFAULT_RETENTION_LIMIT_DAYS);

    public ResourceAggregateMetricServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    private static ResourceAggregateMetricService.ResourceAggregateMetric buildValidStartState() {
        ResourceAggregateMetricService.ResourceAggregateMetric statState = new ResourceAggregateMetricService.ResourceAggregateMetric();
        statState.timeBin = new TimeBin();
        statState.timeBin.avg = new Double(1000);
        statState.currentIntervalTimeStampMicrosUtc = TimeUnit.MICROSECONDS.toMillis(Utils.getNowMicrosUtc());
        statState.documentExpirationTimeMicros = EXPIRATION_TIME;
        return statState;
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {
        private ResourceAggregateMetricService StatsService = new ResourceAggregateMetricService();

        @Before
        public void setupTest() {
            this.StatsService = new ResourceAggregateMetricService();
        }

        @Test
        public void testServiceOptions() {
            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.CONCURRENT_GET_HANDLING,
                    Service.ServiceOption.IMMUTABLE,
                    Service.ServiceOption.PERSISTENCE,
                    Service.ServiceOption.ON_DEMAND_LOAD,
                    Service.ServiceOption.REPLICATION,
                    Service.ServiceOption.OWNER_SELECTION);
            assertThat(this.StatsService.getOptions(), is(expected));
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleStartTest extends BaseModelTest {
        @Test
        public void testValidStartState() throws Throwable {
            ResourceAggregateMetricService.ResourceAggregateMetric startState = buildValidStartState();
            ResourceAggregateMetricService.ResourceAggregateMetric returnState = postServiceSynchronously(
                    ResourceAggregateMetricService.FACTORY_LINK,
                            startState, ResourceAggregateMetricService.ResourceAggregateMetric.class);

            assertNotNull(returnState);
            assertEquals(returnState.timeBin.avg, startState.timeBin.avg);
            assertEquals(returnState.currentIntervalTimeStampMicrosUtc, startState.currentIntervalTimeStampMicrosUtc);
            assertEquals(returnState.documentExpirationTimeMicros, startState.documentExpirationTimeMicros);
        }

        @Test (expected = IllegalArgumentException.class)
        public void testDuplicatePost() throws Throwable {
            ResourceAggregateMetricService.ResourceAggregateMetric startState = buildValidStartState();
            ResourceAggregateMetricService.ResourceAggregateMetric returnState = postServiceSynchronously(
                    ResourceAggregateMetricService.FACTORY_LINK,
                            startState, ResourceAggregateMetricService.ResourceAggregateMetric.class);

            assertNotNull(returnState);
            assertEquals(returnState.timeBin.avg, startState.timeBin.avg);
            startState.timeBin.avg = Double.valueOf(2000);
            startState.documentSelfLink = UriUtils.getLastPathSegment(returnState.documentSelfLink);
            returnState = postServiceSynchronously(ResourceAggregateMetricService.FACTORY_LINK,
                            startState, ResourceAggregateMetricService.ResourceAggregateMetric.class);
        }

        @Test
        public void testMissingBody() throws Throwable {
            postServiceSynchronously(
                    ResourceAggregateMetricService.FACTORY_LINK,
                    null,
                    ResourceAggregateMetricService.ResourceAggregateMetric.class,
                    IllegalArgumentException.class);
        }

        @Test
        public void testMissingCurrentBin() throws Throwable {
            ResourceAggregateMetricService.ResourceAggregateMetric startState = buildValidStartState();
            startState.timeBin = null;

            postServiceSynchronously(ResourceAggregateMetricService.FACTORY_LINK,
                    startState, ResourceAggregateMetricService.ResourceAggregateMetric.class,
                    IllegalArgumentException.class);
        }

        @Test
        public void testMissingTimestamp() throws Throwable {
            ResourceAggregateMetricService.ResourceAggregateMetric startState = buildValidStartState();
            startState.currentIntervalTimeStampMicrosUtc = null;

            postServiceSynchronously(ResourceAggregateMetricService.FACTORY_LINK,
                    startState, ResourceAggregateMetricService.ResourceAggregateMetric.class,
                    IllegalArgumentException.class);
        }
    }
}
