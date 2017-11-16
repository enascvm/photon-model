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

package com.vmware.photon.controller.model.tasks.monitoring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.junit.Test;

import com.vmware.photon.controller.model.PhotonModelMetricServices;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse.ComputeStats;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService.ResourceMetrics;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState.ResourcePoolProperty;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.ScheduledTaskService;
import com.vmware.photon.controller.model.tasks.ScheduledTaskService.ScheduledTaskState;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService.SingleResourceStatsCollectionTaskState;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService.SingleResourceTaskCollectionStage;
import com.vmware.photon.controller.model.tasks.monitoring.StatsCollectionTaskService.StatsCollectionTaskState;
import com.vmware.photon.controller.model.util.ClusterUtil.ServiceTypeCluster;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.TypeName;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.NumericRange;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;

public class StatsCollectionTaskServiceTest extends BaseModelTest {
    public int numResources = 200;

    @Override
    protected void startRequiredServices() throws Throwable {
        super.startRequiredServices();
        PhotonModelTaskServices.startServices(this.getHost());
        this.host.startService(
                Operation.createPost(UriUtils.buildUri(this.host,
                        MockStatsAdapter.class)),
                new MockStatsAdapter());
        this.host.startService(
                Operation.createPost(UriUtils.buildUri(this.host,
                        CustomStatsAdapter.class)),
                new CustomStatsAdapter());
        this.host.waitForServiceAvailable(StatsCollectionTaskService.FACTORY_LINK);
        this.host.waitForServiceAvailable(SingleResourceStatsCollectionTaskService.FACTORY_LINK);
        this.host.waitForServiceAvailable(ResourceMetricsService.FACTORY_LINK);
        this.host.waitForServiceAvailable(MockStatsAdapter.SELF_LINK);
        this.host.waitForServiceAvailable(CustomStatsAdapter.SELF_LINK);
    }

    private VerificationHost setupMetricHost() throws Throwable {
        // Start a metric Host separately.
        VerificationHost metricHost = VerificationHost.create(0);
        metricHost.start();

        ServiceTypeCluster.METRIC_SERVICE.setUri(metricHost.getUri().toString());
        PhotonModelMetricServices.startServices(metricHost);
        return metricHost;
    }

    private void cleanUpMetricHost(VerificationHost metricHost) {
        ServiceTypeCluster.METRIC_SERVICE.setUri(null);
        if (metricHost == null) {
            return;
        }
        metricHost.tearDownInProcessPeers();
        metricHost.toggleNegativeTestMode(false);
        metricHost.tearDown();
    }

    @Test
    public void verifyStatsCollection() throws Throwable {
        this.testStatsCollection(false);
        this.testStatsCollection(true);
    }

    private void testStatsCollection(boolean testOnCluster) throws Throwable {
        VerificationHost metricHost = null;
        if (testOnCluster) {
            metricHost = this.setupMetricHost();
        }

        // Use this.host if metricHost is null.
        VerificationHost verificationHost = (metricHost == null ? this.host : metricHost);
        // create a metric host
        // create a compute description for all the computes
        ComputeDescription cDesc = new ComputeDescription();
        cDesc.name = UUID.randomUUID().toString();
        cDesc.statsAdapterReference = UriUtils.buildUri(this.host, MockStatsAdapter.SELF_LINK);
        ComputeDescription descReturnState = postServiceSynchronously(
                ComputeDescriptionService.FACTORY_LINK, cDesc,
                ComputeDescription.class);

        // create multiple computes
        ComputeState computeState = new ComputeState();
        computeState.name = UUID.randomUUID().toString();
        computeState.descriptionLink = descReturnState.documentSelfLink;
        List<String> computeLinks = new ArrayList<>(this.numResources);
        for (int i = 0; i < this.numResources; i++) {
            ComputeState res = postServiceSynchronously(
                    ComputeService.FACTORY_LINK, computeState,
                    ComputeState.class);
            computeLinks.add(res.documentSelfLink);
        }

        // create a resource pool including all the created computes
        ResourcePoolState rpState = new ResourcePoolState();
        rpState.name = UUID.randomUUID().toString();
        rpState.properties = EnumSet.of(ResourcePoolProperty.ELASTIC);
        rpState.query = Query.Builder.create().addKindFieldClause(ComputeState.class)
                .addInClause(ServiceDocument.FIELD_NAME_SELF_LINK, computeLinks).build();
        ResourcePoolState rpReturnState = postServiceSynchronously(
                ResourcePoolService.FACTORY_LINK, rpState,
                ResourcePoolState.class);

        // create a stats collection scheduler task
        StatsCollectionTaskState statCollectionState = new StatsCollectionTaskState();
        statCollectionState.resourcePoolLink = rpReturnState.documentSelfLink;
        statCollectionState.options = EnumSet.of(TaskOption.SELF_DELETE_ON_COMPLETION);
        ScheduledTaskState statsCollectionTaskState = new ScheduledTaskState();
        statsCollectionTaskState.factoryLink = StatsCollectionTaskService.FACTORY_LINK;
        statsCollectionTaskState.initialStateJson = Utils.toJson(statCollectionState);
        statsCollectionTaskState.intervalMicros = TimeUnit.SECONDS.toMicros(2);
        statsCollectionTaskState = postServiceSynchronously(
                ScheduledTaskService.FACTORY_LINK, statsCollectionTaskState,
                ScheduledTaskState.class);
        ServiceDocumentQueryResult res = this.host.getFactoryState(UriUtils
                .buildExpandLinksQueryUri(UriUtils.buildUri(this.host,
                        ScheduledTaskService.FACTORY_LINK)));
        assertTrue(res.documents.size() > 0);

        // get stats from resources; make sure maintenance has run more than once
        // the last successful collection time should be populated as an in memory stat.
        for (int i = 0; i < this.numResources; i++) {
            String statsUriPath = UriUtils.buildUriPath(computeLinks.get(i),
                    ServiceHost.SERVICE_URI_SUFFIX_STATS);
            this.host.waitFor("Error waiting for in memory stats", () -> {
                ServiceStats resStats = getServiceSynchronously(statsUriPath, ServiceStats.class);
                boolean returnStatus = false;
                for (ServiceStat stat : resStats.entries.values()) {
                    if (stat.latestValue > 0) {
                        returnStatus = true;
                        break;
                    }
                }
                return returnStatus;
            });
        }
        host.log(Level.INFO,
                "Successfully verified that all the last collection time is available in memory.");
        // check that all the stats retuned from the mock stats adapter are
        // persisted at a per metric level along with the last collection run time
        for (String computeLink : computeLinks) {
            ResourceMetrics metric = getResourceMetrics(verificationHost, computeLink,
                    MockStatsAdapter.KEY_1);
            assertNotNull("The resource metric for" + MockStatsAdapter.KEY_1 +
                    " should not be null ", metric);
            assertEquals(metric.entries.size(), 1);
            assertEquals(metric.customProperties.get("prop1"), "val1");

            ResourceMetrics metric2 = getResourceMetrics(verificationHost, computeLink,
                    MockStatsAdapter.KEY_2);
            assertNotNull("The resource metric for" + MockStatsAdapter.KEY_2 +
                    "should not be null ", metric2);
            assertEquals(metric2.entries.size(), 1);
            String lastSuccessfulRunMetricKey = UriUtils
                    .getLastPathSegment(MockStatsAdapter.SELF_LINK) + StatsUtil.SEPARATOR
                    + PhotonModelConstants.LAST_SUCCESSFUL_STATS_COLLECTION_TIME;
            ResourceMetrics metricLastRun = getResourceMetrics(verificationHost, computeLink,
                    lastSuccessfulRunMetricKey);
            assertNotNull("The resource metric for" + lastSuccessfulRunMetricKey
                    + " should not be null ", metricLastRun);

        }
        host.log(Level.INFO,
                "Successfully verified that the required resource metrics are persisted in the resource metrics table");

        // Verify sorted order of the metrics versions by timestamp
        for (String computeLink : computeLinks) {
            // get all versions
            QueryTask qt = QueryTask.Builder
                    .createDirectTask()
                    .addOption(QueryOption.EXPAND_CONTENT)
                    .addOption(QueryOption.SORT)
                    .orderAscending(ServiceDocument.FIELD_NAME_SELF_LINK, TypeName.STRING)
                    .setQuery(
                            Query.Builder.create().addKindFieldClause(ResourceMetrics.class)
                                    .addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                                            UriUtils.buildUriPath(
                                                    ResourceMetricsService.FACTORY_LINK,
                                                    UriUtils.getLastPathSegment(computeLink)),
                                            MatchType.PREFIX)
                                    .addRangeClause(QuerySpecification.buildCompositeFieldName(
                                            ResourceMetrics.FIELD_NAME_ENTRIES,
                                            MockStatsAdapter.KEY_1),
                                            NumericRange.createDoubleRange(Double.MIN_VALUE,
                                                    Double.MAX_VALUE, true, true))
                                    .build())
                    .build();
            verificationHost.createQueryTaskService(qt, false, true, qt, null);

            ResourceMetrics prevMetric = null;
            for (String documentLink : qt.results.documentLinks) {
                ResourceMetrics metric = Utils
                        .fromJson(qt.results.documents.get(documentLink), ResourceMetrics.class);

                if (prevMetric == null) {
                    prevMetric = metric;
                    continue;
                }

                assertTrue(prevMetric.timestampMicrosUtc < metric.timestampMicrosUtc);
            }
        }

        // verify that the aggregation tasks have been deleted
        this.host.waitFor("Timeout waiting for task to expire", () -> {
            ServiceDocumentQueryResult collectRes = this.host.getFactoryState(UriUtils.buildUri(
                    this.host, StatsCollectionTaskService.FACTORY_LINK));
            if (collectRes.documentLinks.size() == 0) {
                return true;
            }
            return false;
        });
        if (testOnCluster) {
            this.cleanUpMetricHost(metricHost);
        }
    }

    @Test
    public void testStatsQueryCustomization() throws Throwable {

        // Before start clear Computes (if any)
        ServiceDocumentQueryResult computes = this.host
                .getFactoryState(UriUtils.buildExpandLinksQueryUri(UriUtils.buildUri(this.host,
                        ComputeService.FACTORY_LINK)));

        for (Map.Entry<String, Object> t : computes.documents.entrySet()) {
            deleteServiceSynchronously(t.getKey());
        }

        // create a compute description for all the computes
        ComputeDescription cDesc = new ComputeDescription();
        cDesc.name = UUID.randomUUID().toString();
        cDesc.statsAdapterReference = UriUtils.buildUri(this.host, MockStatsAdapter.SELF_LINK);
        ComputeDescription descReturnState = postServiceSynchronously(
                ComputeDescriptionService.FACTORY_LINK, cDesc,
                ComputeDescription.class);

        // create multiple computes
        ComputeState computeState = new ComputeState();
        computeState.name = UUID.randomUUID().toString();
        computeState.descriptionLink = descReturnState.documentSelfLink;
        List<String> computeLinks = new ArrayList<>();

        // Create 20 Computes of different type.
        for (int i = 0; i < 20; i++) {
            // Set even computes to be ENDPOINT_HOST, the odd one -> VM_GUEST
            if (i % 2 == 0) {
                computeState.type = ComputeType.ENDPOINT_HOST;
            } else {
                computeState.type = ComputeType.VM_GUEST;
            }

            ComputeState res = postServiceSynchronously(
                    ComputeService.FACTORY_LINK, computeState,
                    ComputeState.class);
            computeLinks.add(res.documentSelfLink);
        }

        // create a resource pool including all the created computes. It will be customized during
        // StatsCollection task
        ResourcePoolState rpState = new ResourcePoolState();
        rpState.name = UUID.randomUUID().toString();
        rpState.properties = EnumSet.of(ResourcePoolProperty.ELASTIC);
        rpState.query = Query.Builder.create().addKindFieldClause(ComputeState.class)
                .addInClause(ServiceDocument.FIELD_NAME_SELF_LINK, computeLinks).build();
        ResourcePoolState rpReturnState = postServiceSynchronously(
                ResourcePoolService.FACTORY_LINK, rpState,
                ResourcePoolState.class);

        // Create additional Query clause
        List<Query> queries = new ArrayList<>();
        Query typeQuery = new Query();
        typeQuery.setOccurance(Occurance.MUST_OCCUR);
        typeQuery.setTermPropertyName(ComputeState.FIELD_NAME_TYPE);
        typeQuery.setTermMatchValue(ComputeType.ENDPOINT_HOST.name());
        queries.add(typeQuery);

        // create a stats collection task
        StatsCollectionTaskState statCollectionState = new StatsCollectionTaskState();
        statCollectionState.resourcePoolLink = rpReturnState.documentSelfLink;
        statCollectionState.customizationClauses = queries;
        //statCollectionState.options = EnumSet.of(TaskOption.SELF_DELETE_ON_COMPLETION);

        StatsCollectionTaskState finalStatCollectionState = postServiceSynchronously(
                StatsCollectionTaskService.FACTORY_LINK, statCollectionState,
                StatsCollectionTaskState.class);

        // give 1 minute max time for StatsCollection task to finish.
        host.setTimeoutSeconds(60);
        host.waitFor(String.format("Timeout waiting for StatsCollectionTask: [%s] to complete.",
                finalStatCollectionState.documentSelfLink), () -> {
                    StatsCollectionTaskState stats = getServiceSynchronously(
                            finalStatCollectionState.documentSelfLink,
                            StatsCollectionTaskState.class);
                    return stats.taskInfo != null && stats.taskInfo.stage == TaskStage.FINISHED;
                });

        ServiceDocumentQueryResult res = this.host
                .getFactoryState(UriUtils.buildExpandLinksQueryUri(UriUtils.buildUri(this.host,
                        ComputeService.FACTORY_LINK)));
        assertEquals(20, res.documents.size());

        int vmHosts = 0;
        int vmGuests = 0;

        // Traverse through [computeLink/stats] URL and find statistics. Only Computes of type
        // ENDPOINT_HOST should provide statistics.
        for (Map.Entry<String, Object> map : res.documents.entrySet()) {
            String uri = String.format("%s/stats", map.getKey());
            ServiceStats stats = getServiceSynchronously(uri, ServiceStats.class);
            ComputeState state = Utils.fromJson(map.getValue(), ComputeState.class);
            if (state.type == ComputeType.ENDPOINT_HOST) {
                assertTrue(!stats.entries.isEmpty());
                vmHosts++;
            } else {
                assertTrue(stats.entries.isEmpty());
                vmGuests++;
            }
        }

        assertEquals(10, vmHosts);
        assertEquals(10, vmGuests);

        //clean up
        deleteServiceSynchronously(finalStatCollectionState.documentSelfLink);
    }

    /**
     * Queries all ResourceMetric documents with the prefix provided.
     * Sorts the documents by documentSelfLink.
     * Returns the first document.
     */
    private ResourceMetrics getResourceMetrics(VerificationHost host, String resourceLink,
            String metricKey) {
        QueryTask qt = QueryTask.Builder
                .createDirectTask()
                .addOption(QueryOption.TOP_RESULTS)
                // No-op in photon-model. Required for special handling of immutable documents.
                // This will prevent Lucene from holding the full result set in memory.
                .addOption(QueryOption.INCLUDE_ALL_VERSIONS)
                .setResultLimit(1)
                .addOption(QueryOption.EXPAND_CONTENT)
                .addOption(QueryOption.SORT)
                .orderDescending(ServiceDocument.FIELD_NAME_SELF_LINK, TypeName.STRING)
                .setQuery(Query.Builder.create()
                        .addKindFieldClause(ResourceMetrics.class)
                        .addCompositeFieldClause(ResourceMetrics.FIELD_NAME_CUSTOM_PROPERTIES,
                                ResourceMetrics.PROPERTY_RESOURCE_LINK, resourceLink)
                        .addRangeClause(QuerySpecification.buildCompositeFieldName(
                                ResourceMetrics.FIELD_NAME_ENTRIES, metricKey),
                                NumericRange.createDoubleRange(Double.MIN_VALUE, Double.MAX_VALUE,
                                        true, true))
                        .build())
                .build();
        host.createQueryTaskService(qt, false, true, qt, null);
        String documentLink = qt.results.documentLinks.get(0);
        ResourceMetrics resourceMetric = Utils.fromJson(qt.results.documents.get(documentLink),
                ResourceMetrics.class);
        return resourceMetric;
    }

    @Test
    public void testCustomStatsAdapter() throws Throwable {
        ResourcePoolState rpState = new ResourcePoolState();
        rpState.name = UUID.randomUUID().toString();
        ResourcePoolState rpReturnState = postServiceSynchronously(
                ResourcePoolService.FACTORY_LINK, rpState,
                ResourcePoolState.class);

        ComputeDescription desc = new ComputeDescription();
        desc.name = rpState.name;
        desc.statsAdapterReferences = Collections
                .singleton(UriUtils.buildUri(this.host, CustomStatsAdapter.SELF_LINK));
        ComputeDescription descReturnState = postServiceSynchronously(
                ComputeDescriptionService.FACTORY_LINK, desc,
                ComputeDescription.class);

        ComputeState computeState = new ComputeState();
        computeState.name = rpState.name;
        computeState.descriptionLink = descReturnState.documentSelfLink;
        computeState.resourcePoolLink = rpReturnState.documentSelfLink;
        List<String> computeLinks = new ArrayList<>();
        for (int i = 0; i < this.numResources; i++) {
            ComputeState res = postServiceSynchronously(
                    ComputeService.FACTORY_LINK, computeState,
                    ComputeState.class);
            computeLinks.add(res.documentSelfLink);
        }

        // create a stats collection scheduler task
        StatsCollectionTaskState statCollectionState = new StatsCollectionTaskState();
        statCollectionState.resourcePoolLink = rpReturnState.documentSelfLink;
        statCollectionState.statsAdapterReference = UriUtils.buildUri(this.host,
                CustomStatsAdapter.SELF_LINK);
        statCollectionState.options = EnumSet.of(TaskOption.SELF_DELETE_ON_COMPLETION);
        ScheduledTaskState statsCollectionTaskState = new ScheduledTaskState();
        statsCollectionTaskState.factoryLink = StatsCollectionTaskService.FACTORY_LINK;
        statsCollectionTaskState.initialStateJson = Utils.toJson(statCollectionState);
        statsCollectionTaskState.intervalMicros = TimeUnit.MILLISECONDS.toMicros(500);
        statsCollectionTaskState = postServiceSynchronously(
                ScheduledTaskService.FACTORY_LINK, statsCollectionTaskState,
                ScheduledTaskState.class);
        ServiceDocumentQueryResult res = this.host.getFactoryState(UriUtils
                .buildExpandLinksQueryUri(UriUtils.buildUri(this.host,
                        ScheduledTaskService.FACTORY_LINK)));
        assertTrue(res.documents.size() > 0);

        // get stats from resources
        for (int i = 0; i < computeLinks.size(); i++) {
            String statsUriPath = UriUtils.buildUriPath(computeLinks.get(i),
                    ServiceHost.SERVICE_URI_SUFFIX_STATS);
            this.host.waitFor("Error waiting for stats", () -> {
                ServiceStats resStats = getServiceSynchronously(statsUriPath, ServiceStats.class);
                boolean returnStatus = false;

                // check if custom stats adapter was invoked and the last collection time metric
                // was populated in the in memory stats
                for (ServiceStat stat : resStats.entries.values()) {
                    if (stat.name
                            .startsWith(
                                    UriUtils.getLastPathSegment(CustomStatsAdapter.SELF_LINK))) {
                        returnStatus = true;
                        break;
                    }
                }
                return returnStatus;
            });
        }

        //clean up
        deleteServiceSynchronously(statsCollectionTaskState.documentSelfLink);
    }

    @Test
    public void testCustomStatsAdapterPrecedence() throws Throwable {
        ResourcePoolState rpState = new ResourcePoolState();
        rpState.name = UUID.randomUUID().toString();
        ResourcePoolState rpReturnState = postServiceSynchronously(
                ResourcePoolService.FACTORY_LINK, rpState,
                ResourcePoolState.class);

        ComputeDescription desc = new ComputeDescription();
        desc.name = rpState.name;
        desc.statsAdapterReference = UriUtils.buildUri(this.host, MockStatsAdapter.SELF_LINK);
        desc.statsAdapterReferences = new HashSet<>(
                Arrays.asList(UriUtils.buildUri(this.host, "/foo"),
                        UriUtils.buildUri(this.host, CustomStatsAdapter.SELF_LINK)));
        ComputeDescription descReturnState = postServiceSynchronously(
                ComputeDescriptionService.FACTORY_LINK, desc,
                ComputeDescription.class);

        ComputeState computeState = new ComputeState();
        computeState.name = rpState.name;
        computeState.descriptionLink = descReturnState.documentSelfLink;
        computeState.resourcePoolLink = rpReturnState.documentSelfLink;
        List<String> computeLinks = new ArrayList<>();
        for (int i = 0; i < this.numResources; i++) {
            ComputeState res = postServiceSynchronously(
                    ComputeService.FACTORY_LINK, computeState,
                    ComputeState.class);
            computeLinks.add(res.documentSelfLink);
        }

        // create a stats collection scheduler task
        StatsCollectionTaskState statCollectionState = new StatsCollectionTaskState();
        statCollectionState.resourcePoolLink = rpReturnState.documentSelfLink;
        statCollectionState.statsAdapterReference = UriUtils.buildUri(this.host,
                CustomStatsAdapter.SELF_LINK);
        statCollectionState.options = EnumSet.of(TaskOption.SELF_DELETE_ON_COMPLETION);
        ScheduledTaskState statsCollectionTaskState = new ScheduledTaskState();
        statsCollectionTaskState.factoryLink = StatsCollectionTaskService.FACTORY_LINK;
        statsCollectionTaskState.initialStateJson = Utils.toJson(statCollectionState);
        statsCollectionTaskState.intervalMicros = TimeUnit.MILLISECONDS.toMicros(500);
        statsCollectionTaskState = postServiceSynchronously(
                ScheduledTaskService.FACTORY_LINK, statsCollectionTaskState,
                ScheduledTaskState.class);
        ServiceDocumentQueryResult res = this.host.getFactoryState(UriUtils
                .buildExpandLinksQueryUri(UriUtils.buildUri(this.host,
                        ScheduledTaskService.FACTORY_LINK)));
        assertTrue(res.documents.size() > 0);

        // get stats from resources
        for (int i = 0; i < computeLinks.size(); i++) {
            String statsUriPath = UriUtils.buildUriPath(computeLinks.get(i),
                    ServiceHost.SERVICE_URI_SUFFIX_STATS);
            this.host.waitFor("Error waiting for stats", () -> {
                ServiceStats resStats = getServiceSynchronously(statsUriPath, ServiceStats.class);
                boolean returnStatus = false;

                // check if custom stats adapter was invoked and the last collection value was
                // populated correctly.
                for (ServiceStat stat : resStats.entries.values()) {
                    //host.log(Level.INFO, "*****%s", stat.name);
                    if (stat.name.startsWith(
                            UriUtils.getLastPathSegment(CustomStatsAdapter.SELF_LINK))) {
                        returnStatus = true;
                        break;
                    }
                }
                return returnStatus;
            });
        }

        //clean up
        deleteServiceSynchronously(statsCollectionTaskState.documentSelfLink);
    }

    @Test(expected = IllegalStateException.class)
    public void testComputeStatsResponseCustomPropertiesLimit() {
        ComputeStats stats = new ComputeStats();
        for (int i = 0; i <= ComputeStatsResponse.CUSTOM_PROPERTIES_LIMIT; ++i) {
            stats.addCustomProperty("key" + i, "val" + i);
        }
    }

    public static class CustomStatsAdapter extends StatelessService {
        public static final String SELF_LINK = "/custom-stats-adapter";
        static final String KEY_1 = "customMetricKey";
        static final String UNIT_1 = "customMetricUnit";

        @Override
        public void handleRequest(Operation op) {
            if (!op.hasBody()) {
                op.fail(new IllegalArgumentException("body is required"));
                return;
            }
            switch (op.getAction()) {
            case PATCH:
                op.complete();
                ComputeStatsRequest request = op.getBody(ComputeStatsRequest.class);
                SingleResourceStatsCollectionTaskState response = new SingleResourceStatsCollectionTaskState();
                Map<String, List<ServiceStat>> statValues = new HashMap<>();
                ServiceStat key1 = new ServiceStat();
                key1.latestValue = new Random().nextInt(1000);
                key1.sourceTimeMicrosUtc = TimeUnit.MILLISECONDS
                        .toMicros(System.currentTimeMillis());
                key1.unit = UNIT_1;
                statValues.put(KEY_1, Collections.singletonList(key1));
                ComputeStats stats = new ComputeStats();
                stats.statValues = statValues;
                stats.computeLink = request.resourceReference.getPath();
                response.statsList = new ArrayList<>();
                response.statsList.add(stats);
                response.taskStage = SingleResourceTaskCollectionStage.valueOf(request.nextStage);
                response.statsAdapterReference = UriUtils.buildUri(getHost(), SELF_LINK);
                this.sendRequest(Operation.createPatch(request.taskReference)
                        .setBody(response));
                break;
            default:
                super.handleRequest(op);
            }
        }
    }
}
