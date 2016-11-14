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

import java.net.URI;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService.ResourceMetrics;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.NumericRange;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;

/**
 * This class implements tests for the {@link ResourceMetricService} class.
 */
@RunWith(ResourceMetricsServiceTest.class)
@SuiteClasses({ ResourceMetricsServiceTest.ConstructorTest.class,
        ResourceMetricsServiceTest.HandleStartTest.class })
public class ResourceMetricsServiceTest extends Suite {

    private static String KEY1 = "key1";
    private static Double VAL = Double.valueOf(1000);
    private static final int DEFAULT_RETENTION_LIMIT_DAYS = 7;
    private static final long EXPIRATION_TIME = Utils.getNowMicrosUtc()
            + TimeUnit.DAYS.toMicros(DEFAULT_RETENTION_LIMIT_DAYS);

    public ResourceMetricsServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    private static ResourceMetricsService.ResourceMetrics buildValidStartState() {
        ResourceMetricsService.ResourceMetrics statState = new ResourceMetricsService.ResourceMetrics();
        statState.entries = new HashMap<>();
        statState.timestampMicrosUtc  = TimeUnit.MICROSECONDS.toMillis(Utils.getNowMicrosUtc());
        statState.entries.put(KEY1, VAL);
        statState.documentExpirationTimeMicros = EXPIRATION_TIME;
        return statState;
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {
        private ResourceMetricsService StatsService = new ResourceMetricsService();

        @Before
        public void setupTest() {
            this.StatsService = new ResourceMetricsService();
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
            ResourceMetricsService.ResourceMetrics startState = buildValidStartState();
            ResourceMetricsService.ResourceMetrics returnState = postServiceSynchronously(
                    ResourceMetricsService.FACTORY_LINK,
                            startState, ResourceMetricsService.ResourceMetrics.class);

            assertNotNull(returnState);
            assertThat(returnState.entries.values().iterator().next(),
                    is(startState.entries.values().iterator().next()));
            assertThat(returnState.timestampMicrosUtc,
                    is(startState.timestampMicrosUtc));
            assertThat(returnState.documentExpirationTimeMicros,
                    is(startState.documentExpirationTimeMicros));
            QueryTask.QuerySpecification querySpec = new QueryTask.QuerySpecification();
            querySpec.query = Query.Builder.create().addRangeClause(
                    QuerySpecification.buildCompositeFieldName(ResourceMetrics.FIELD_NAME_ENTRIES,KEY1), NumericRange.createEqualRange(VAL)).build();
            this.host.createAndWaitSimpleDirectQuery(querySpec, 1, 1);
        }

        @Test
        public void testMissingBody() throws Throwable {
            postServiceSynchronously(
                    ResourceMetricsService.FACTORY_LINK,
                    null,
                    ResourceMetricsService.ResourceMetrics.class,
                    IllegalArgumentException.class);
        }

        @Test
        public void testFactoryAvailable() throws Throwable {
            this.host.startFactory(new ResourceMetricsService());
            this.host.waitForServiceAvailable(ResourceMetricsService.FACTORY_LINK);

            this.host.testStart(1);
            PhotonModelServices.setFactoryToAvailable(this.host, ResourceMetricsService.FACTORY_LINK, this.host.getCompletion());
            this.host.testWait();

            URI availableUri = UriUtils.buildAvailableUri(this.host, ResourceMetricsService.FACTORY_LINK);
            Operation factoryAvailableOp = Operation.createGet(availableUri)
                    .setCompletion(this.host.getCompletion());
            this.host.log(Level.INFO, "Attempting to get factory's availability: %s", availableUri);
            this.host.sendAndWait(factoryAvailableOp);
        }
    }
}
