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

package com.vmware.photon.controller.model.adapters.awsadapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSAuthentication;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSComputeHost;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSResourcePool;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.enumerateResources;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.regionId;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.resourceStatsAggregation;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.resourceStatsCollection;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.setAwsClientMockInfo;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestUtils.getExecutor;
import static com.vmware.photon.controller.model.tasks.ProvisioningUtils.createServiceURI;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.vmware.photon.controller.model.PhotonModelInMemoryServices;
import com.vmware.photon.controller.model.PhotonModelMetricServices;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService.ResourceMetrics;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.xenon.common.BasicTestCase;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.NumericRange;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceHostManagementService;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Executes long running end to end stats aggregation task for a given real AWS endpoint.
 * Performs stats aggregation with 'AWSStatsService' as stats adapter reference for resource pool.
 */
public class LongRunEndToEndStatsAggregationTest extends BasicTestCase {
    private static final String TEST_CASE_INITIAL = "Initial Run ";
    private static final int DEFAULT_TIMEOUT_SECONDS = 200;
    private static final int EXECUTOR_TERMINATION_WAIT_DURATION_MINUTES = 2;

    private static final String SEPARATOR = ": ";
    private static final String STAT_NAME_CPU_USAGE_PERCENT = "CPU Usage Percent";
    private static final String STAT_NAME_MEMORY_AVAILABLE_IN_MB = "Memory available in MB";
    private static final String STAT_NAME_MEMORY_ON_HOST_IN_MB = "Memory on host in MB";
    private static final double BYTES_TO_MB = 1024 * 1024;

    private static final int DEFAULT_RETENTION_LIMIT_DAYS = 56;

    private ComputeState computeHost;
    private EndpointState endpointState;

    // Time in minutes for stats availability for a EC2 instance when newly created.
    private static final int initTimeForStatsAvailInMin = 8;

    private AmazonEC2AsyncClient client;
    public boolean isAwsClientMock = false;
    public String awsMockEndpointReference = null;

    // Memory threshold percent for logging levels
    public static final int MEMORY_SEVERE_THRESHOLD = 60;
    public static final int MEMORY_WARNING_THRESHOLD = 40;

    // configure endpoint details
    public boolean isMock = true;
    public String accessKey = "accessKey";
    public String secretKey = "secretKey";

    public boolean useAllRegions = true;
    public int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;

    private URI nodeStatsUri = null;

    public int aggregationFrequencyInMinutes = 1;
    public int testRunDurationInMinutes = 3;

    private Level loggingLevelMemory;

    private double cpuUsagePercentage;
    private double availableMemoryMb;
    private double maxMemoryInMb;

    @Rule
    public TestName currentTestName = new TestName();

    @Before
    public void setUp() throws Throwable {
        CommandLineArgumentParser.parseFromProperties(this);

        setAwsClientMockInfo(this.isAwsClientMock, this.awsMockEndpointReference);
        // create credentials
        AuthCredentialsServiceState creds = new AuthCredentialsServiceState();
        creds.privateKey = this.secretKey;
        creds.privateKeyId = this.accessKey;
        this.client = AWSUtils.getAsyncClient(creds, null, getExecutor());

        try {
            PhotonModelServices.startServices(this.host);
            PhotonModelTaskServices.startServices(this.host);
            PhotonModelMetricServices.startServices(this.host);
            PhotonModelInMemoryServices.startServices(this.host);
            PhotonModelAdaptersRegistryAdapters.startServices(this.host);
            AWSAdapters.startServices(this.host);

            this.host.setTimeoutSeconds(this.timeoutSeconds);

            this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelMetricServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelInMemoryServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelAdaptersRegistryAdapters.LINKS);
            this.host.waitForServiceAvailable(AWSAdapters.LINKS);

        } catch (Throwable e) {
            this.host.log("Error starting up services for the test %s", e.getMessage());
            throw new Exception(e);
        }

        this.nodeStatsUri = UriUtils.buildUri(this.host.getUri(), ServiceUriPaths.CORE_MANAGEMENT);
        this.maxMemoryInMb = this.host.getState().systemInfo.maxMemoryByteCount / BYTES_TO_MB;
        // create the compute host, resource pool and the VM state to be used in the test.
        initResourcePoolAndComputeHost();
    }

    @After
    public void tearDown() throws Throwable {
        if (this.host == null) {
            return;
        }
        this.client.shutdown();
        setAwsClientMockInfo(false, null);
    }

    /**
     * Test to perform end to end stats aggregation with stats adapter.
     * Performs enumeration against a real AWS endpoint if provided and then executes stats collection.
     * Test is then performed with periodic execution of stats collection and aggregation.
     * Verifies the compute types of resources and their stats for last successful collection time.
     * Verifies in memory stats generation for resource aggregate metric documents.
     * Verifies expiration time for resource aggregate metric documents.
     * Verifies resource metric entries and its time bin entries for resource aggregate metric documents.
     * Also, verifies Estimated Charges value for given AWS endpoint.
     */
    @Test
    public void testStatsAggregation() throws Throwable {
        this.host.log("Running test: " + this.currentTestName);

        if (this.isMock) {
            return;
        }
        this.host.log(STAT_NAME_MEMORY_ON_HOST_IN_MB + SEPARATOR + this.maxMemoryInMb);

        // perform stats aggregation before stats collection takes place.
        // As no stats collection is performed yet, ResourceAggregateMetric document count will be 0.
        resourceStatsAggregation(this.host, this.computeHost.resourcePoolLink);
        ServiceDocumentQueryResult aggrRes = this.host.getFactoryState(UriUtils.buildUri(this.host,
                ResourceMetricsService.FACTORY_LINK));
        assertEquals(0, aggrRes.documentLinks.size());

        // perform enumeration on given AWS endpoint.
        enumerateResources(this.host, this.computeHost, this.endpointState, this.isMock,
                TEST_CASE_INITIAL);

        // periodically perform stats aggregation on given AWS endpoint
        runStatsCollectionAndAggregationLogNodeStatsPeriodically();

        this.host.log(Level.INFO, "Waiting for multiple stats aggregation runs...");

        this.host.waitFor("Timeout while waiting for test run duration", () -> {
            TimeUnit.MINUTES.sleep(this.testRunDurationInMinutes);
            this.host.getScheduledExecutor().shutdown();
            this.host.getScheduledExecutor().awaitTermination(EXECUTOR_TERMINATION_WAIT_DURATION_MINUTES,
                    TimeUnit.MINUTES);
            return true;
        });
    }

    /**
     * Periodically runs stats collection and aggregation and logs node stats.
     */
    private void runStatsCollectionAndAggregationLogNodeStatsPeriodically() {
        this.host.getScheduledExecutor().scheduleAtFixedRate(() -> {
            try {
                this.host.log(Level.INFO, "Running stats collection...");
                // perform stats collection on given AWS endpoint.
                resourceStatsCollection(this.host, this.isMock, this.computeHost.resourcePoolLink);
                ServiceDocumentQueryResult res = this.host
                        .getFactoryState(UriUtils.buildExpandLinksQueryUri(UriUtils.buildUri(this.host,
                                ComputeService.FACTORY_LINK)));

                this.host.log(Level.INFO, "Running stats aggregation...");
                resourceStatsAggregation(this.host, this.computeHost.resourcePoolLink);

                logNodeStats(this.host.getServiceStats(this.nodeStatsUri));

                ServiceDocumentQueryResult aggrResult = this.host
                        .getExpandedFactoryState(UriUtils.buildUri(this.host,
                                ResourceMetricsService.FACTORY_LINK));
                // check compute resource has stats generated for all metric keys
                checkInMemoryStatsPresent(res);
                // check expiration time for all resource aggregate metric documents
                checkExpirationTime(aggrResult);
                // check to verify appropriate resource metrics entries are present for compute resources.
                verifyResourceMetricEntries(res);
                // verify time bin metrics for every compute resource and endpoint estimated charges
                verifyTimeBinMetrics(res);
            } catch (Throwable e) {
                this.host.log(Level.WARNING,
                        "Error running stats aggregation in test" + e.getMessage());
            }
        }, 0, this.aggregationFrequencyInMinutes, TimeUnit.MINUTES);
    }

    /**
     * Perform check to verify that every compute resource has stats generated for all the metric
     * keys using which stats aggregation was performed on resources.
     */
    private void checkInMemoryStatsPresent(ServiceDocumentQueryResult res) {
        for (Map.Entry<String, Object> resourceMap : res.documents.entrySet()) {
            ServiceStats resStats = this.host.getServiceState(null, ServiceStats.class,
                    UriUtils.buildStatsUri(createServiceURI(host, null, resourceMap.getKey())));
            int statCount = 0;
            for (ServiceStat stat : resStats.entries.values()) {
                for (String key : TestAWSSetupUtils.getMetricNames()) {
                    if (stat.name.startsWith(key)) {
                        statCount++;
                        break;
                    }
                }
            }
            // after stats aggregation all resources should have all metrics.
            assertEquals("Did not find in-memory stats",
                    TestAWSSetupUtils.getMetricNames().size(), statCount);
        }
    }

    /**
     * Performs check to verify that expiration time for aggregate metric documents for every
     * compute resource has been set to be expired after 56 days.
     */
    private void checkExpirationTime(ServiceDocumentQueryResult aggrResult) {
        long expectedExpirationTime = Utils.getNowMicrosUtc()
                + TimeUnit.DAYS.toMicros(DEFAULT_RETENTION_LIMIT_DAYS);

        for (Object aggrDocument : aggrResult.documents.values()) {
            ResourceMetrics aggrMetric = Utils
                    .fromJson(aggrDocument, ResourceMetrics.class);
            // Make sure all the documents have expiration time set.
            assertTrue("Expiration time is not correctly set.",
                    aggrMetric.documentExpirationTimeMicros < expectedExpirationTime);
        }
    }

    /**
     * Performs check to verify appropriate resource metrics entries are present for
     * compute resources.
     */
    private void verifyResourceMetricEntries(ServiceDocumentQueryResult res) {
        for (Map.Entry<String, Object> resourceMap : res.documents.entrySet()) {
            ComputeState state = Utils
                    .fromJson(resourceMap.getValue(), ComputeState.class);

            if (state.type == ComputeType.ENDPOINT_HOST) {
                assertNotNull(getResourceMetrics(resourceMap.getKey(),
                        PhotonModelConstants.ESTIMATED_CHARGES));
            } else if (state.type == ComputeType.VM_GUEST) {
                long micros = Utils.getNowMicrosUtc() - state.creationTimeMicros;
                if (state.powerState == PowerState.ON
                        && micros > TimeUnit.MINUTES.toMicros(initTimeForStatsAvailInMin)) {
                    assertNotNull(getResourceMetrics(resourceMap.getKey(),
                            PhotonModelConstants.CPU_UTILIZATION_PERCENT));
                }
            } else if (state.type == ComputeType.ZONE) {
                assertNotNull(getResourceMetrics(resourceMap.getKey(),
                        PhotonModelConstants.ESTIMATED_CHARGES));
            } else {
                assertNull(getResourceMetrics(resourceMap.getKey(),
                        PhotonModelConstants.ESTIMATED_CHARGES));
                assertNull(getResourceMetrics(resourceMap.getKey(),
                        PhotonModelConstants.CPU_UTILIZATION_PERCENT));
            }
        }
    }

    /**
     * Performs check to verify time bin metrics are available for every compute resource and also
     * checks the occurrence of estimated charges for one of the resource metrics.
     */
    private void verifyTimeBinMetrics(ServiceDocumentQueryResult res) {
        List<Object> results = new ArrayList<>();

        for (String computeResourceLink : res.documentLinks) {
            for (String metricName : TestAWSSetupUtils.getMetricNames()) {
                QueryTask.QuerySpecification querySpec = new QueryTask.QuerySpecification();

                querySpec.query = QueryTask.Query.Builder.create()
                        .addKindFieldClause(ResourceMetrics.class)
                        .addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                                UriUtils.buildUriPath(ResourceMetricsService.FACTORY_LINK, computeResourceLink),
                                QueryTask.QueryTerm.MatchType.PREFIX)
                        .addRangeClause(QuerySpecification
                                .buildCompositeFieldName(ResourceMetrics.FIELD_NAME_ENTRIES, metricName),
                                NumericRange.createDoubleRange(0.0, Double.MAX_VALUE, true, true))
                        .build();

                QueryTask qt = QueryTask.Builder
                        .createDirectTask()
                        .addOption(QueryTask.QuerySpecification.QueryOption.TOP_RESULTS)
                        .addOption(QueryTask.QuerySpecification.QueryOption.INCLUDE_ALL_VERSIONS)
                        .setResultLimit(1)
                        .addOption(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT)
                        .addOption(QueryTask.QuerySpecification.QueryOption.SORT)
                        .orderDescending(ServiceDocument.FIELD_NAME_SELF_LINK,
                                ServiceDocumentDescription.TypeName.STRING)
                        .setQuery(querySpec.query).build();

                this.host.createQueryTaskService(qt, false, true, qt, null);
                if (qt.results.documentLinks.size() > 0
                        && qt.results.documentLinks.get(0) != null) {
                    results.add(qt.results.documents.get(qt.results.documentLinks.get(0)));
                }
            }
        }

        long expectedExpirationTime = Utils.getNowMicrosUtc()
                + TimeUnit.DAYS.toMicros(DEFAULT_RETENTION_LIMIT_DAYS);
        boolean estimatedChargeFound = false;

        for (Object aggrDocument : results) {
            ResourceMetrics aggrMetric = Utils
                    .fromJson(aggrDocument, ResourceMetrics.class);

            // Make sure all the documents have expiration time set.
            assertTrue("Expiration time is not correctly set.",
                    aggrMetric.documentExpirationTimeMicros < expectedExpirationTime);

            // The assertion here checks whether we are aggregating only on latest value. To
            // that effect, here is the breakdown for the check:
            // count = num of resources: one value for each resource
            // sum = null: not specified in the aggregate type set

            assertNotNull("Value is not set", aggrMetric.entries.get(0));
        }
        assertTrue(estimatedChargeFound);
    }

    /**
     * Method returns ResourceMetrics collected during stats collection and aggregation for a
     * resource endpoint for the given resource metric key.
     */
    private ResourceMetrics getResourceMetrics(String resourceLink,
            String metricKey) {
        QueryTask qt = QueryTask.Builder
                .createDirectTask()
                .addOption(QueryOption.TOP_RESULTS)
                .addOption(QueryOption.INCLUDE_ALL_VERSIONS)
                .setResultLimit(1)
                .addOption(QueryOption.EXPAND_CONTENT)
                .addOption(QueryOption.SORT)
                .orderDescending(ServiceDocument.FIELD_NAME_SELF_LINK,
                        ServiceDocumentDescription.TypeName.STRING)
                .setQuery(QueryTask.Query.Builder.create()
                        .addKindFieldClause(ResourceMetrics.class)
                        .addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                                UriUtils.buildUriPath(ResourceMetricsService.FACTORY_LINK,
                                        UriUtils.getLastPathSegment(resourceLink)),
                                QueryTask.QueryTerm.MatchType.PREFIX)
                        .addRangeClause(QuerySpecification
                                        .buildCompositeFieldName(ResourceMetrics.FIELD_NAME_ENTRIES,
                                                metricKey),
                                QueryTask.NumericRange
                                        .createDoubleRange(0.0, Double.MAX_VALUE, true,
                                                true))
                        .build())
                .build();
        this.host.createQueryTaskService(qt, false, true, qt, null);
        ResourceMetrics resourceMetric = null;
        if (qt.results.documentLinks.size() > 0) {
            String documentLink = qt.results.documentLinks.get(0);
            resourceMetric = Utils
                    .fromJson(qt.results.documents.get(documentLink), ResourceMetrics.class);
        }
        return resourceMetric;
    }

    /**
     * Prints logs for node stats (CPU usage and Memory usage).
     * @param statsMap Map containing node stats.
     */
    private void logNodeStats(Map<String, ServiceStat> statsMap) {
        // In case getServiceStats method fails or returns null.
        if (statsMap == null || statsMap.isEmpty()) {
            this.host.log(Level.WARNING, "Error getting CPU utilization and Memory usage.");
            return;
        }

        this.cpuUsagePercentage =
                statsMap.get(ServiceHostManagementService.STAT_NAME_CPU_USAGE_PCT_PER_HOUR)
                        .latestValue;
        this.availableMemoryMb =
                statsMap.get(ServiceHostManagementService.STAT_NAME_AVAILABLE_MEMORY_BYTES_PER_HOUR)
                        .latestValue / BYTES_TO_MB;
        this.loggingLevelMemory = Level.SEVERE;

        // Increase logging level if available Memory is less than expected.
        if ((this.availableMemoryMb / this.maxMemoryInMb) * 100 > MEMORY_SEVERE_THRESHOLD) {
            this.loggingLevelMemory = Level.INFO;
        } else if ((this.availableMemoryMb / this.maxMemoryInMb) * 100 > MEMORY_WARNING_THRESHOLD) {
            this.loggingLevelMemory = Level.WARNING;
        }

        this.host.log(Level.INFO,
                STAT_NAME_CPU_USAGE_PERCENT + SEPARATOR + this.cpuUsagePercentage);
        this.host.log(this.loggingLevelMemory,
                STAT_NAME_MEMORY_AVAILABLE_IN_MB + SEPARATOR + this.availableMemoryMb);
    }

    /**
     * Creates the state associated with the resource pool, compute host and the VM to be created.
     *
     * @throws Throwable
     */
    private void initResourcePoolAndComputeHost() throws Throwable {
        // Create a resource pool where the VM will be housed
        ResourcePoolState resourcePool = createAWSResourcePool(this.host);

        AuthCredentialsServiceState auth = createAWSAuthentication(this.host, this.accessKey, this.secretKey);

        this.endpointState = TestAWSSetupUtils.createAWSEndpointState(this.host, auth.documentSelfLink, resourcePool.documentSelfLink);

        // create a compute host for the AWS EC2 VM
        this.computeHost = createAWSComputeHost(this.host,
                this.endpointState,
                null /*zoneId*/, this.useAllRegions ? null : regionId,
                this.isAwsClientMock,
                this.awsMockEndpointReference, null /*tags*/);
    }
}
