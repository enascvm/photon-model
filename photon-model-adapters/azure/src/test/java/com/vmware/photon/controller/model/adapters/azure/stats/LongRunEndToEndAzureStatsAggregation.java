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

package com.vmware.photon.controller.model.adapters.azure.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultAuthCredentials;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultComputeHost;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultEndpointState;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultResourcePool;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.getResourceMetrics;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.resourceStatsAggregation;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.resourceStatsCollection;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.runEnumeration;
import static com.vmware.photon.controller.model.tasks.ProvisioningUtils.createServiceURI;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.vmware.photon.controller.model.PhotonModelMetricServices;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapters.azure.AzureAdapters;
import com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService.ResourceMetrics;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;

import com.vmware.xenon.common.BasicReusableHostTestCase;
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
import com.vmware.xenon.services.common.QueryTask.QueryTerm;
import com.vmware.xenon.services.common.ServiceHostManagementService;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Executes long running end to end stats aggregation task for a given real azure ENDPOINT.
 * Performs stats aggregation with Azure stats adapter as stats adapter reference for resource pool.
 * Verifies the count of resource aggregate metric documents generated and present as in memory stats.
 * Verifies time bin metrics for resource aggregate metric documents.
 *
 */
public class LongRunEndToEndAzureStatsAggregation extends BasicReusableHostTestCase {

    private static final String CUSTOM_DIAGNOSTIC_ENABLED_VM = "EnumTestVM-DoNotDelete";

    private static final String SEPARATOR = ": ";
    private static final String STAT_NAME_CPU_USAGE_PERCENT = "CPU Usage Percent";
    private static final String STAT_NAME_MEMORY_AVAILABLE_IN_MB = "Memory available in MB";
    private static final double BYTES_TO_MB = 1024 * 1024;
    private static final int DEFAULT_RETENTION_LIMIT_DAYS = 56;
    private static final int EXECUTOR_TERMINATION_WAIT_DURATION_MINUTES = 1;


    private Level loggingLevelMemory;

    public int aggregationFrequencyInMinutes = 1;
    public int testRunDurationInMinutes = 3;

    // configure endpoint details
    public boolean isMock = true;
    public String clientID = "clientID";
    public String clientKey = "clientKey";
    public String subscriptionId = "subscriptionId";
    public String tenantId = "tenantId";

    // Memory threshold percent for logging levels
    public static final int MEMORY_SEVERE_THRESHOLD = 60;
    public static final int MEMORY_WARNING_THRESHOLD = 40;

    private double cpuUsagePercentage;
    private double availableMemoryMb;
    private double maxMemoryInMb;

    private static ComputeState computeHost;
    private static EndpointState endpointState;

    private URI nodeStatsUri;

    @Rule
    public TestName currentTestName = new TestName();

    @Before
    public void setUp() throws Exception {
        try {
            if (computeHost == null) {
                PhotonModelServices.startServices(this.host);
                PhotonModelTaskServices.startServices(this.host);
                PhotonModelAdaptersRegistryAdapters.startServices(this.host);
                PhotonModelMetricServices.startServices(this.host);
                AzureAdapters.startServices(this.host);

                this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
                this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
                this.host.waitForServiceAvailable(PhotonModelMetricServices.LINKS);
                this.host.waitForServiceAvailable(PhotonModelAdaptersRegistryAdapters.LINKS);
                this.host.waitForServiceAvailable(AzureAdapters.LINKS);
                this.host.setTimeoutSeconds(600);

                ResourcePoolState resourcePool = createDefaultResourcePool(this.host);

                AuthCredentialsServiceState authCredentials = createDefaultAuthCredentials(
                        this.host,
                        this.clientID,
                        this.clientKey,
                        this.subscriptionId,
                        this.tenantId);

                endpointState = createDefaultEndpointState(
                        this.host, authCredentials.documentSelfLink);

                // create a compute host for the Azure
                computeHost = createDefaultComputeHost(this.host, resourcePool.documentSelfLink,
                        endpointState);
            }

            this.nodeStatsUri = UriUtils.buildUri(this.host.getUri(), ServiceUriPaths.CORE_MANAGEMENT);
            this.maxMemoryInMb = this.host.getState().systemInfo.maxMemoryByteCount / BYTES_TO_MB;
        } catch (Throwable e) {
            throw new Exception(e);
        }
    }

    /**
     * Test to perform end to end stats aggregation with azure instance adapter.
     * Performs enumeration against a real Azure endpoint if provided.
     * Test is then performed with periodic execution of stats collection and aggregation.
     * Verifies the compute types of resources and their stats for last successful collection time.
     * Verifies in memory stats generation for resource aggregate metric documents.
     * Verifies expiration time for resource aggregate metric documents.
     * Verifies resource metric entries and its time bin entries for resource aggregate metric documents.
     */
    @Test
    public void testStatsAggregation() throws Throwable {
        this.host.log("Running test: " + this.currentTestName);

        if (this.isMock) {
            return;
        }

        // perform stats aggregation before stats collection takes place.
        // As no stats collection is performed yet, ResourceAggregateMetric document count will be 0.
        resourceStatsAggregation(this.host, computeHost.resourcePoolLink);
        ServiceDocumentQueryResult aggrRes = this.host.getFactoryState(UriUtils.buildUri(this.host,
                ResourceMetricsService.FACTORY_LINK));
        assertEquals(0, aggrRes.documentLinks.size());

        // perform enumeration on given Azure endpoint.
        runEnumeration(this.host, computeHost.documentSelfLink, computeHost.resourcePoolLink,
                endpointState, this.isMock);

        // periodically perform stats collection and aggregation on given Azure endpoint
        runStatsCollectionAndAggregationLogNodeStatsPeriodically();

        this.host.log(Level.INFO, "Waiting for multiple stats aggregation runs...");

        this.host.waitFor("Timeout while waiting for test run duration", () -> {
            TimeUnit.MINUTES.sleep(this.testRunDurationInMinutes);
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
                this.host.log(Level.INFO, "Running azure stats collection...");
                // perform stats collection on given Azure endpoint.
                resourceStatsCollection(this.host, this.isMock, computeHost.resourcePoolLink);
                ServiceDocumentQueryResult res = this.host
                        .getFactoryState(UriUtils.buildExpandLinksQueryUri(UriUtils.buildUri(this.host,
                                ComputeService.FACTORY_LINK)));

                this.host.log(Level.INFO, "Running azure stats aggregation...");
                resourceStatsAggregation(this.host, computeHost.resourcePoolLink);

                logNodeStats(this.host.getServiceStats(this.nodeStatsUri));

                ServiceDocumentQueryResult aggrResult = this.host
                        .getExpandedFactoryState(UriUtils.buildUri(this.host,
                                ResourceMetricsService.FACTORY_LINK));
                // check compute resource has stats generated for all the metric keys
                checkInMemoryStatsPresent(res);
                // check expiration time for aggregate metric documents
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
                for (String key : AzureTestUtil.getMetricNames()) {
                    if (stat.name.startsWith(key)) {
                        statCount++;
                        break;
                    }
                }
            }
            // after stats aggregation all resources should have all metrics.
            assertEquals("Did not find in-memory stats",
                    AzureTestUtil.getMetricNames().size(), statCount);
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

            if (state.type == ComputeType.VM_HOST) {
                assertNotNull(getResourceMetrics(this.host, resourceMap.getKey(),
                        PhotonModelConstants.STORAGE_USED_BYTES));
            } else if (state.type == ComputeType.VM_GUEST &&
                    state.id.toLowerCase()
                            .contains(CUSTOM_DIAGNOSTIC_ENABLED_VM.toLowerCase())) {
                assertNotNull(getResourceMetrics(this.host, resourceMap.getKey(),
                        PhotonModelConstants.CPU_UTILIZATION_PERCENT));
                assertNotNull(getResourceMetrics(this.host, resourceMap.getKey(),
                        PhotonModelConstants.MEMORY_AVAILABLE_PERCENT));
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
            for (String metricName : AzureTestUtil.getMetricNames()) {
                QuerySpecification querySpec = new QuerySpecification();

                querySpec.query = QueryTask.Query.Builder.create()
                        .addKindFieldClause(ResourceMetrics.class)
                        .addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                                UriUtils.buildUriPath(ResourceMetricsService.FACTORY_LINK, computeResourceLink),
                                QueryTerm.MatchType.PREFIX)
                        .addRangeClause(QuerySpecification
                                .buildCompositeFieldName(ResourceMetrics.FIELD_NAME_ENTRIES, metricName),
                                NumericRange.createDoubleRange(0.0, Double.MAX_VALUE, true, true))
                        .build();

                QueryTask qt = QueryTask.Builder
                        .createDirectTask()
                        .addOption(QueryOption.TOP_RESULTS)
                        .addOption(QueryOption.INCLUDE_ALL_VERSIONS)
                        .setResultLimit(1)
                        .addOption(QueryOption.EXPAND_CONTENT)
                        .addOption(QueryOption.SORT)
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


}
