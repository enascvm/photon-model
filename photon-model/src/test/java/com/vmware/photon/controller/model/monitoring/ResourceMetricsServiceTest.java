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

import java.net.URI;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.PhotonModelMetricServices;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService.ResourceMetrics;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.util.ClusterUtil;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.NumericRange;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;

/**
 * This class implements tests for the {@link ResourceMetricsService} class.
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
        statState.timestampMicrosUtc = TimeUnit.MICROSECONDS.toMillis(Utils.getNowMicrosUtc());
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
                    QuerySpecification
                            .buildCompositeFieldName(ResourceMetrics.FIELD_NAME_ENTRIES, KEY1),
                    NumericRange.createEqualRange(VAL)).build();
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
            PhotonModelMetricServices
                    .setFactoryToAvailable(this.host, ResourceMetricsService.FACTORY_LINK,
                            this.host.getCompletion());
            this.host.testWait();

            URI availableUri = UriUtils
                    .buildAvailableUri(this.host, ResourceMetricsService.FACTORY_LINK);
            Operation factoryAvailableOp = Operation.createGet(availableUri)
                    .setCompletion(this.host.getCompletion());
            this.host.log(Level.INFO, "Attempting to get factory's availability: %s", availableUri);
            this.host.sendAndWait(factoryAvailableOp);
        }

        @Test
        public void testResourceMetricsDelete() throws Throwable {
            this.host.startFactory(new ResourceMetricsService());
            this.host.waitForServiceAvailable(ResourceMetricsService.FACTORY_LINK);

            ResourceMetrics metricToBeDeletedWithoutBody = postResourceMetricsDocument(this.host);
            ResourceMetrics metricToBeDeletedWithBodyExpireNow = postResourceMetricsDocument(
                    this.host);
            ResourceMetrics metricToBeDeletedWithBodyExpireLater = postResourceMetricsDocument(
                    this.host);
            assertNotNull(metricToBeDeletedWithoutBody);
            assertNotNull(metricToBeDeletedWithBodyExpireNow);
            assertNotNull(metricToBeDeletedWithBodyExpireLater);

            ServiceDocument expireNowDocument = new ServiceDocument();
            expireNowDocument.documentExpirationTimeMicros = Utils.getNowMicrosUtc();
            ServiceDocument expireLaterDocument = new ServiceDocument();
            expireLaterDocument.documentExpirationTimeMicros = Utils.getNowMicrosUtc()
                    + TimeUnit.MINUTES.toMicros(10);

            // Delete a metric without body and validate that it exists as deleted in the index
            // with expected expiration.
            Operation deleteWithoutBodyOp = Operation.createDelete(UriUtils.buildUri(this.host,
                    metricToBeDeletedWithoutBody.documentSelfLink))
                    .setReferer(this.host.getUri());
            // Delete a metric with body and set current expiry. Validate that the document gets
            // removed from the index.
            Operation deleteWithBodyExpireNowOp = Operation
                    .createDelete(UriUtils.buildUri(this.host,
                            metricToBeDeletedWithBodyExpireNow.documentSelfLink))
                    .setBody(expireNowDocument)
                    .setReferer(this.host.getUri());
            // Delete a metric with body and set future expiry. Validate that it exists as deleted
            // in the index with expected expiration.
            Operation deleteWithBodyExpireLaterOp = Operation
                    .createDelete(UriUtils.buildUri(this.host,
                            metricToBeDeletedWithBodyExpireLater.documentSelfLink))
                    .setBody(expireLaterDocument)
                    .setReferer(this.host.getUri());
            Operation deleteWithoutBodyResponse = this.host.waitForResponse(deleteWithoutBodyOp);
            Operation deleteWithBodyExpireNowResponse = this.host
                    .waitForResponse(deleteWithBodyExpireNowOp);
            Operation deleteWithBodyExpireLaterResponse = this.host
                    .waitForResponse(deleteWithBodyExpireLaterOp);
            assertEquals(200, deleteWithoutBodyResponse.getStatusCode());
            assertEquals(200, deleteWithBodyExpireLaterResponse.getStatusCode());
            assertEquals(200, deleteWithBodyExpireNowResponse.getStatusCode());

            List<String> expectDocumentLinks = Arrays.asList(metricToBeDeletedWithoutBody.documentSelfLink,
                    metricToBeDeletedWithBodyExpireLater.documentSelfLink);
            List<String> unexpectedDocumentLinks = Arrays.asList(metricToBeDeletedWithBodyExpireNow.documentSelfLink);

            // Wait for Xenon groomer to remove the expired documents.
            this.host.waitFor("Timeout waiting for document cleanup", () -> {
                try {
                    validateResourceMetricsDeletion(metricToBeDeletedWithoutBody.documentSelfLink,
                            metricToBeDeletedWithBodyExpireNow.documentSelfLink,
                            metricToBeDeletedWithBodyExpireLater.documentSelfLink);
                } catch (Exception e) {
                    return false;
                }
                return true;
            });
        }

        public static ResourceMetrics postResourceMetricsDocument(VerificationHost host) {
            ResourceMetrics resourceMetrics = new ResourceMetrics();
            resourceMetrics.timestampMicrosUtc = Utils.getNowMicrosUtc();
            resourceMetrics.documentExpirationTimeMicros = 0;
            resourceMetrics.entries = new HashMap<>();
            resourceMetrics.entries.put("key", 1.1);
            resourceMetrics.customProperties = new HashMap<>();
            resourceMetrics.customProperties.put("key", "value");
            resourceMetrics.documentSelfLink = UriUtils
                    .buildUriPath(ResourceMetricsService.FACTORY_LINK,
                            UUID.randomUUID().toString());

            Operation postOp = Operation
                    .createPost(UriUtils.buildUri(host, ResourceMetricsService.FACTORY_LINK))
                    .setBody(resourceMetrics).setReferer(host.getUri());
            Operation postResponse = host.waitForResponse(postOp);
            if (postResponse.getStatusCode() == 200) {
                return resourceMetrics;
            } else {
                return null;
            }
        }

        public void validateResourceMetricsDeletion(String metricToBeDeletedWithoutBodyLink,
                String metricToBeDeletedWithBodyExpireNowLink, String metricToBeDeletedWithBodyExpireLaterLink)
                throws Exception {
            QueryTask response;
            while (true) {
                Query query = Query.Builder.create()
                        .addKindFieldClause(ResourceMetrics.class)
                        .build();
                QueryTask queryTask = QueryTask.Builder.createDirectTask()
                        .setQuery(query)
                        .addOption(QueryOption.INCLUDE_DELETED)
                        .addOption(QueryOption.EXPAND_CONTENT)
                        .build();

                Operation queryOp = QueryUtils.createQueryTaskOperation(this.host, queryTask,
                        ClusterUtil.ServiceTypeCluster.METRIC_SERVICE).setReferer
                        (this.host.getUri());
                Operation queryResponse = this.host.waitForResponse(queryOp);
                assertEquals(200, queryResponse.getStatusCode());

                response = queryResponse.getBody(QueryTask.class);

                if (response.results.documentLinks.contains(metricToBeDeletedWithoutBodyLink)
                        && response.results.documentLinks.contains(metricToBeDeletedWithBodyExpireLaterLink)
                        && !response.results.documentLinks.contains(metricToBeDeletedWithBodyExpireNowLink)) {
                    break;
                }
                Thread.sleep(TimeUnit.SECONDS.toMillis(1));
            }

            ResourceMetrics metricWithNoExpiry = Utils.fromJson(response.results.documents
                    .get(metricToBeDeletedWithoutBodyLink), ResourceMetrics.class);
            ResourceMetrics metricWithFutureExpiry = Utils.fromJson(response.results.documents
                    .get(metricToBeDeletedWithBodyExpireLaterLink), ResourceMetrics.class);
            assertTrue(metricWithNoExpiry.documentExpirationTimeMicros == 0);
            assertTrue(metricWithFutureExpiry.documentExpirationTimeMicros > Utils.getNowMicrosUtc());
            assertTrue(metricWithFutureExpiry.documentUpdateAction.equals("DELETE"));
            assertTrue(metricWithFutureExpiry.documentUpdateAction.equals("DELETE"));
        }
    }
}
