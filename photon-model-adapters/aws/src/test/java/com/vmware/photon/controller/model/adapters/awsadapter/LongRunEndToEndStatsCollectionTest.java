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

import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSComputeHost;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSResourcePool;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.enumerateResources;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.resourceStatsCollection;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.setAwsClientMockInfo;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.zoneId;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestUtils.getExecutor;
import static com.vmware.photon.controller.model.tasks.ProvisioningUtils.createServiceURI;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;

import com.google.gson.JsonObject;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService.ResourceMetrics;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService;

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
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceHostManagementService;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Executes long running end to end Stats Collection for a given real endpoint.
 * Performs stats collection with 'AWSStatsService' as stats adapter reference for resource pool.
 * Verifies for the count of resources and estimated charges along with its metric for
 * last successful collection.
 *
 */
public class LongRunEndToEndStatsCollectionTest extends BasicTestCase {

    private static final String TEST_CASE_RUN = "Test Case Run ";
    private static final int DEFAULT_TIMEOUT_SECONDS = 200;
    private static final String SEPARATOR = ": ";
    private static final String STAT_NAME_CPU_USAGE_PERCENT = "CPU Usage Percent";
    private static final String STAT_NAME_MEMORY_AVAILABLE_PERCENT = "Memory available Percent";
    private static final String STAT_NAME_MEMORY_ON_HOST_IN_MB = "Memory on host in MB";
    private static final double BYTES_TO_MB = 1024 * 1024;

    // Time in minutes for stats availability for a EC2 instance when newly created.
    public static final int initTimeForStatsAvailInMin = 8;

    // Memory threshold percent for logging levels
    public static final int MEMORY_SEVERE_THRESHOLD = 60;
    public static final int MEMORY_WARNING_THRESHOLD = 40;

    private ResourcePoolState outPool;
    private ComputeState outComputeHost;

    private AmazonEC2AsyncClient client;
    public boolean isAwsClientMock = false;
    public String awsMockEndpointReference = null;
    private long currentTimeMicros;
    private URI nodeStatsUri = null;

    // configure endpoint details
    public boolean isMock = true;
    public String accessKey = "accessKey";
    public String secretKey = "secretKey";

    public boolean useAllRegions = true;
    public int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;

    // attribute holders related to stats collection
    // count of compute resources obtained during stats collection for a resource pool.
    private int totalComputeResources = 0;

    // stats metric key generated to hold last successful collection information
    private String lastSuccessfulCollectionTimeKey;

    private StatsToCheck statsToCheckInit = new StatsToCheck();
    private StatsToCheck statsToCheckLater = new StatsToCheck();

    public int collectionFrequencyInMinutes = 1;
    public int testRunDurationInMinutes = 3;

    private Level loggingLevelMemory;

    private double cpuUsagePercentage;
    private double availableMemoryMb;

    private double maxMemoryInMb;

    @Rule
    public TestName currentTestName = new TestName();

    /**
     * Variables to check asserts on post stats collection.
     */
    private class StatsToCheck {
        ServiceStat serviceStat = new ServiceStat();
        double estimatedCharges = 0;
    }

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
            AWSAdapters.startServices(this.host);

            this.host.setTimeoutSeconds(this.timeoutSeconds);

            this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
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
     * Test to perform end to end stats collection with stats adapter.
     * Performs enumeration against a real AWS endpoint if provided and then executes 2 sessions of
     * periodic stats collection.
     * Log the stats for Cpu and Memory usage.
     * Verifies the compute types of resources and their stats for last successful collection time.
     * Also, verifies Estimated Charges value for given AWS endpoint.
     */
    @Test
    public void testStatsCollection() throws Throwable {
        this.host.log("Running test: " + this.currentTestName);

        // if mock, simply return.
        if (this.isMock) {
            return;
        }
        this.host.log(STAT_NAME_MEMORY_ON_HOST_IN_MB + SEPARATOR + this.maxMemoryInMb);
        SingleResourceStatsCollectionTaskService service =
                new SingleResourceStatsCollectionTaskService();

        // generate stats link using stats adapter
        String statsLink = UriUtils.getLastPathSegment(AWSUriPaths.AWS_STATS_ADAPTER);
        this.lastSuccessfulCollectionTimeKey = service
                .getLastCollectionMetricKeyForAdapterLink(statsLink, true);

        // perform resource stats collection before enumeration takes place on given AWS endpoint
        resourceStatsCollection(this.host, this.isMock, this.outPool.documentSelfLink);
        // will try to fetch the compute state resources
        ServiceDocumentQueryResult result = this.host
                .getFactoryState(UriUtils.buildExpandLinksQueryUri(UriUtils.buildUri(this.host,
                        ComputeService.FACTORY_LINK)));

        // will have only 1 document of type 'VM_HOST'
        assertEquals(1, result.documents.size());
        JsonObject jsonObject = (JsonObject) result.documents.get(result.documentLinks.get(0));
        assertEquals(ComputeType.VM_HOST.toString(), jsonObject.get("type").getAsString());

        // perform resource enumeration on given AWS endpoint
        enumerateResources(this.host, this.isMock, this.outPool.documentSelfLink,
                this.outComputeHost.descriptionLink, this.outComputeHost.documentSelfLink,
                TEST_CASE_RUN);

        // periodically perform stats collection on given AWS endpoint
        runStatsCollectionLogNodeStatsPeriodically();

        this.host.log(Level.INFO, "Waiting for multiple stats collection runs...");

        // Keep the host running for some time, specified by testRunDurationInMinutes parameter.
        this.host.waitFor("Timeout while waiting for test run duration", () -> {
            TimeUnit.MINUTES.sleep(this.testRunDurationInMinutes);
            return true;
        });

        // fetch all the compute state resources
        ServiceDocumentQueryResult res = this.host
                .getFactoryState(UriUtils.buildExpandLinksQueryUri(UriUtils.buildUri(this.host,
                        ComputeService.FACTORY_LINK)));

        // total compute resources
        this.totalComputeResources = res.documents.size();
        this.currentTimeMicros = Utils.getNowMicrosUtc();

        // query and verify resource metrics obtained for first iteration of stats collection
        verifyMetricStats(res, this.statsToCheckInit);

        // periodically perform more cycles of resource stats collection on given AWS endpoint
        this.host.log(Level.INFO, "Waiting for multiple stats collection second runs...");

        // Keep the host running for some time, specified by testRunDurationInMinutes parameter.
        this.host.waitFor("Timeout while waiting for second test run duration", () -> {
            TimeUnit.MINUTES.sleep(this.testRunDurationInMinutes);
            return true;
        });

        // query and verify resource metrics obtained for second iteration of stats collection
        verifyMetricStats(res, this.statsToCheckLater);

        // the values will change only if new values are captured from AWS during stats collection.
        assertTrue(
                "LastUpdate time obtained during first cycle of stats collection should be"
                        + " less than or equal to the value during second cycle",
                this.statsToCheckInit.serviceStat.lastUpdateMicrosUtc
                        <= this.statsToCheckLater.serviceStat.lastUpdateMicrosUtc);
        assertTrue(
                "LatestValue obtained during first cycle of stats collection should be "
                        + "less than or equal to the value during second cycle",
                this.statsToCheckInit.serviceStat.latestValue <=
                        this.statsToCheckLater.serviceStat.latestValue);

        assertTrue(
                "Document version obtained during initial cycles of stats collection should be"
                        + " less than or equal to that obtained during later stats collection cycle",
                this.statsToCheckInit.serviceStat.version <=
                        this.statsToCheckInit.serviceStat.version);

        assertTrue(
                "EstimatedCharges obtained during first cycle of stats collection should be"
                        + " less than or equal to that obtained during second stats collection cycle",
                this.statsToCheckInit.estimatedCharges <=
                        this.statsToCheckLater.estimatedCharges);
    }

    /**
     * Performs query to fetch Resource metric for list of compute resources.
     * Verifies the counts of resources to indicate number of VM_HOST, VM_GUEST and other compute
     * types that includes compute type ZONE.
     */
    private void verifyMetricStats(ServiceDocumentQueryResult res, StatsToCheck statsToCheck) {
        // count for resources of type VM_HOST
        int vmHosts = 0;
        // count for resources of type VM_GUEST
        int vmGuests = 0;
        // count for resources of type ZONE
        int zones = 0;
        // count for resources of type others
        int misc = 0;

        // count of resources having Estimated Charges resource metric.
        int resourcesWithEstChargesMetricCount = 0;
        // count of resources not having Estimated Charges resource metric.
        int resourcesWithNoEstChargesMetricCount = 0;

        ResourceMetrics resourceMetric;

        // Fetch stats for each compute resource and verify its Compute Type and stat values.
        for (Map.Entry<String, Object> resourceMap : res.documents.entrySet()) {

            // Fetch stats for a compute resource
            ServiceStats stats = this.host.getServiceState(null, ServiceStats.class,
                    UriUtils.buildStatsUri(createServiceURI(host, null, resourceMap.getKey())));

            ComputeState state = Utils
                    .fromJson(resourceMap.getValue(), ComputeState.class);

            if (state.type == ComputeType.VM_HOST) {
                resourceMetric = getResourceMetrics(resourceMap.getKey(),
                        PhotonModelConstants.ESTIMATED_CHARGES);

                // EstimatedCharges metric will be available for VM_HOST resource
                assertNotNull(resourceMetric);
                statsToCheck.estimatedCharges = resourceMetric.entries
                        .get(PhotonModelConstants.ESTIMATED_CHARGES);

                // CPUUtilizationPercent metric will not be available for VM_HOST resource
                resourceMetric = getResourceMetrics(resourceMap.getKey(),
                        PhotonModelConstants.CPU_UTILIZATION_PERCENT);
                assertNull(resourceMetric);

                vmHosts++;
                resourcesWithEstChargesMetricCount++;
            } else if (state.type == ComputeType.VM_GUEST) {
                long micros = this.currentTimeMicros - state.creationTimeMicros;
                resourceMetric = getResourceMetrics(resourceMap.getKey(),
                        PhotonModelConstants.ESTIMATED_CHARGES);

                // EstimatedCharges metric will not be available for VM_GUEST resource
                assertNull(resourceMetric);

                // CPUUtilizationPercent metric will be available for VM_GUEST resource
                resourceMetric = getResourceMetrics(resourceMap.getKey(),
                        PhotonModelConstants.CPU_UTILIZATION_PERCENT);

                // CPU Utilization will be present for VM's in ON state and after 7 minutes from the
                // time VM was created.
                if (state.powerState == ComputeService.PowerState.ON
                        && micros > TimeUnit.MINUTES.toMicros(initTimeForStatsAvailInMin)) {
                    assertNotNull(resourceMetric);
                }

                vmGuests++;
                resourcesWithNoEstChargesMetricCount++;
            } else if (state.type == ComputeType.ZONE) {
                resourceMetric = getResourceMetrics(resourceMap.getKey(),
                        PhotonModelConstants.ESTIMATED_CHARGES);
                // EstimatedCharges metric will be available for ZONE
                assertNotNull(resourceMetric);

                // CPUUtilizationPercent metric will not be available for ZONE
                resourceMetric = getResourceMetrics(resourceMap.getKey(),
                        PhotonModelConstants.CPU_UTILIZATION_PERCENT);
                assertNull(resourceMetric);

                zones++;
                resourcesWithEstChargesMetricCount++;
            } else {
                misc++;
            }

            // store stats entries values for last successful collection time.
            statsToCheck.serviceStat.version = stats.entries
                    .get(this.lastSuccessfulCollectionTimeKey).version;
            statsToCheck.serviceStat.lastUpdateMicrosUtc = stats.entries
                    .get(this.lastSuccessfulCollectionTimeKey).lastUpdateMicrosUtc;
            statsToCheck.serviceStat.latestValue = stats.entries
                    .get(this.lastSuccessfulCollectionTimeKey).latestValue;

            // verify last successful collection time values is set and not null for each resource.
            assertNotNull(statsToCheck.serviceStat.version);
            assertNotNull(statsToCheck.serviceStat.lastUpdateMicrosUtc);
            assertNotNull(statsToCheck.serviceStat.latestValue);
        }
        // count of computes of misc type should be 0.
        assertEquals(0, misc);

        // There should be single host for a given Endpoint.
        assertEquals(1, vmHosts);

        // sum of VM_GUEST's + ZONE's should be equal to total compute resources minus VM_HOST's
        // this will throw exception if there are other types of compute resources discovered.
        assertEquals(this.totalComputeResources - vmHosts, vmGuests + zones);

        // Number of VM_HOST's equals number of resources having metric for EstimatedCharges
        assertEquals(vmHosts + zones, resourcesWithEstChargesMetricCount);

        // count of other resources will equal to number of resources not having metric for EstimatedCharges
        assertEquals(vmGuests, resourcesWithNoEstChargesMetricCount);

        // count of zones supposed to be greater than 0
        assertTrue(zones > 0);
    }

    /**
     * Method returns ResourceMetrics collected during stats collection for a resource endpoint
     * for the given resource metric key.
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
                        .addRangeClause(QueryTask.QuerySpecification
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
            resourceMetric = Utils.fromJson(qt.results.documents.get(documentLink),
                    ResourceMetrics.class);
        }
        return resourceMetric;
    }

    /**
     * Creates the state associated with the resource pool, compute host and the VM to be created.
     *
     * @throws Throwable
     */
    private void initResourcePoolAndComputeHost() throws Throwable {
        // Create a resource pool where the VM will be staged
        this.outPool = createAWSResourcePool(this.host);

        // create a compute host for the AWS EC2 VM
        this.outComputeHost = createAWSComputeHost(this.host, this.outPool.documentSelfLink,
                null, this.useAllRegions ? null : zoneId,
                this.accessKey, this.secretKey, this.isAwsClientMock,
                this.awsMockEndpointReference, null);

    }

    /**
     * Periodically runs stats collection and logs node stats.
     */
    private void runStatsCollectionLogNodeStatsPeriodically() {
        this.host.getScheduledExecutor().scheduleAtFixedRate(() -> {
            try {
                this.host.log(Level.INFO, "Running stats collection...");
                resourceStatsCollection(this.host, this.isMock, this.outPool.documentSelfLink);

                logNodeStats(this.host.getServiceStats(this.nodeStatsUri));
            } catch (Throwable e) {
                this.host.log(Level.WARNING,
                        "Error running stats collection in test" + e.getMessage());
            }
        }, 0, this.collectionFrequencyInMinutes, TimeUnit.MINUTES);
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

        double memoryAvailablePercent = (this.availableMemoryMb / this.maxMemoryInMb) * 100;
        // Increase logging level if available Memory is less than expected.
        if (memoryAvailablePercent > MEMORY_SEVERE_THRESHOLD) {
            this.loggingLevelMemory = Level.INFO;
        } else if (memoryAvailablePercent > MEMORY_WARNING_THRESHOLD) {
            this.loggingLevelMemory = Level.WARNING;
        }

        this.host.log(Level.INFO,
                STAT_NAME_CPU_USAGE_PERCENT + SEPARATOR + this.cpuUsagePercentage);
        this.host.log(this.loggingLevelMemory,
                STAT_NAME_MEMORY_AVAILABLE_PERCENT + SEPARATOR + this.availableMemoryMb);
    }
}