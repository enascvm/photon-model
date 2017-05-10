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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Test;

import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.monitoring.ResourceAggregateMetricService;
import com.vmware.photon.controller.model.monitoring.ResourceAggregateMetricService.ResourceAggregateMetric;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsAggregationTaskService.SingleResourceStatsAggregationTaskState;
import com.vmware.photon.controller.model.tasks.monitoring.StatsCollectionTaskService.StatsCollectionTaskState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.ServiceStats.TimeSeriesStats.AggregationType;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.SortOrder;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;

public class SingleResourceStatsAggregationTaskServiceTest extends BaseModelTest {

    private static final int NUM_COMPUTE_RESOURCES = 200;
    private static final int NUM_COLLECTIONS = 5;
    private static final int DEFAULT_RETENTION_LIMIT_DAYS = 56;

    @Override
    protected void startRequiredServices() throws Throwable {
        super.startRequiredServices();
        PhotonModelTaskServices.startServices(this.getHost());
        this.host.startService(
                Operation.createPost(UriUtils.buildUri(this.host,
                        MockStatsAdapter.class)),
                new MockStatsAdapter());
        this.host.waitForServiceAvailable(StatsCollectionTaskService.FACTORY_LINK);
        this.host.waitForServiceAvailable(SingleResourceStatsCollectionTaskService.FACTORY_LINK);
        this.host.waitForServiceAvailable(SingleResourceStatsAggregationTaskService.FACTORY_LINK);
        this.host.waitForServiceAvailable(MockStatsAdapter.SELF_LINK);
    }

    @Test
    public void testResourceStatsAggregation() throws Throwable {
        // create a resource pool
        ResourcePoolState rpState = new ResourcePoolState();
        rpState.name = "testName";
        ResourcePoolState rpReturnState = postServiceSynchronously(
                ResourcePoolService.FACTORY_LINK, rpState,
                ResourcePoolState.class);

        ComputeState[] computeStateArray = new ComputeState[NUM_COMPUTE_RESOURCES];
        for (int i = 0; i < NUM_COMPUTE_RESOURCES; i++) {
            ComputeDescription cDesc = new ComputeDescription();
            cDesc.name = rpState.name;
            cDesc.statsAdapterReference = UriUtils.buildUri(this.host, MockStatsAdapter.SELF_LINK);
            ComputeDescription descReturnState = postServiceSynchronously(
                    ComputeDescriptionService.FACTORY_LINK, cDesc,
                    ComputeDescription.class);
            ComputeState computeState = new ComputeState();
            computeState.name = rpState.name;
            computeState.descriptionLink = descReturnState.documentSelfLink;
            computeState.resourcePoolLink = rpReturnState.documentSelfLink;
            computeStateArray[i] = postServiceSynchronously(
                    ComputeService.FACTORY_LINK, computeState,
                    ComputeState.class);
        }

        StatsCollectionTaskState collectionTaskState = new StatsCollectionTaskState();
        collectionTaskState.resourcePoolLink = rpReturnState.documentSelfLink;
        collectionTaskState.taskInfo = TaskState.createDirect();
        int counter = 0;
        // executing stats collection multiple times
        while (counter < NUM_COLLECTIONS) {
            this.postServiceSynchronously(
                            StatsCollectionTaskService.FACTORY_LINK,
                            collectionTaskState, StatsCollectionTaskState.class);
            counter++;
        }
        // wait for stats to be populated
        this.host.waitFor("Error waiting for stats", () -> {
            boolean returnStatus = false;
            for (int i = 0; i < NUM_COMPUTE_RESOURCES; i++) {
                String statsUriPath = UriUtils.buildUriPath(computeStateArray[i].documentSelfLink,
                        ServiceHost.SERVICE_URI_SUFFIX_STATS);
                ServiceStats resStats = getServiceSynchronously(statsUriPath, ServiceStats.class);
                for (ServiceStat stat : resStats.entries.values()) {
                    if (stat.name
                            .startsWith(UriUtils.getLastPathSegment(MockStatsAdapter.SELF_LINK))) {
                        returnStatus = true;
                        break;
                    }
                }
            }
            return returnStatus;
        });

        // kick off an aggregation task
        SingleResourceStatsAggregationTaskState aggregationTaskState = new SingleResourceStatsAggregationTaskState();
        aggregationTaskState.resourceLink = computeStateArray[0].documentSelfLink;
        aggregationTaskState.metricNames = new HashSet<>(
                Arrays.asList(MockStatsAdapter.KEY_1, MockStatsAdapter.KEY_2, MockStatsAdapter.KEY_3));
        postServiceSynchronously(SingleResourceStatsAggregationTaskService.FACTORY_LINK,
                aggregationTaskState,
                SingleResourceStatsAggregationTaskState.class);
        this.host.waitFor("Error waiting for rolled up stats", () -> {
            ServiceDocumentQueryResult result = this.host
                    .getExpandedFactoryState(UriUtils.buildUri(this.host,
                            ResourceAggregateMetricService.FACTORY_LINK));
            return (result.documentCount == 2);
        });

        String statsUriPath = UriUtils.buildUriPath(computeStateArray[0].documentSelfLink,
                    ServiceHost.SERVICE_URI_SUFFIX_STATS);
        ServiceStats resStats = getServiceSynchronously(statsUriPath, ServiceStats.class);
        int statCount = 0;
        for (ServiceStat stat : resStats.entries.values()) {
            if (stat.name.startsWith(MockStatsAdapter.KEY_1) ||
                    stat.name.startsWith(MockStatsAdapter.KEY_2) ) {
                statCount++;
            }
        }
        Assert.assertEquals("Did not find in-memory stats", 2, statCount);

        // kick off an another aggregation task, ensure there are documents
        postServiceSynchronously(SingleResourceStatsAggregationTaskService.FACTORY_LINK,
                aggregationTaskState,
                SingleResourceStatsAggregationTaskState.class);
        this.host.waitFor("Error waiting for rolled up stats", () -> {
            ServiceDocumentQueryResult result = this.host
                    .getExpandedFactoryState(UriUtils.buildUri(this.host,
                            ResourceAggregateMetricService.FACTORY_LINK));
            if (result.documents.size() == 0) {
                return false;
            }
            // Expiration time = Now + 7 day.
            long expectedExpirationTime = Utils.getNowMicrosUtc()
                    + TimeUnit.DAYS.toMicros(DEFAULT_RETENTION_LIMIT_DAYS);

            boolean rightVersion = true;
            for (Object aggrDocument : result.documents.values()) {
                ResourceAggregateMetric aggrMetric = Utils
                        .fromJson(aggrDocument, ResourceAggregateMetric.class);
                // Make sure all the documents have expiration time set.
                Assert.assertTrue("Expiration time is not correctly set.",
                        aggrMetric.documentExpirationTimeMicros <  expectedExpirationTime);
                if (aggrMetric.timeBin.count == NUM_COLLECTIONS) {
                    continue;
                }
                rightVersion = false;
            }
            return rightVersion;
        });

        // kick off an aggregation task with a query that resolves to all resources with the specified resource pool link;
        // ensure that we have aggregated data over all raw metric versions
        aggregationTaskState = new SingleResourceStatsAggregationTaskState();
        aggregationTaskState.resourceLink = rpReturnState.documentSelfLink;
        aggregationTaskState.metricNames = new HashSet<>(
                Arrays.asList(MockStatsAdapter.KEY_1, MockStatsAdapter.KEY_2, MockStatsAdapter.KEY_3));
        aggregationTaskState.query =
                Query.Builder.create()
                        .addKindFieldClause(ComputeState.class)
                        .addFieldClause(ComputeState.FIELD_NAME_RESOURCE_POOL_LINK,
                        rpReturnState.documentSelfLink).build();
        postServiceSynchronously(SingleResourceStatsAggregationTaskService.FACTORY_LINK,
                aggregationTaskState,
                SingleResourceStatsAggregationTaskState.class);
        this.host.waitFor("Error waiting for rolled up stats", () -> {
            QuerySpecification querySpec = new QuerySpecification();
            querySpec.query = Query.Builder.create()
                    .addKindFieldClause(ResourceAggregateMetric.class)
                    .addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                            UriUtils.buildUriPath(ResourceAggregateMetricService.FACTORY_LINK,
                                    UriUtils.getLastPathSegment(rpReturnState.documentSelfLink)),
                            MatchType.PREFIX).build();
            querySpec.options.add(QueryOption.EXPAND_CONTENT);
            ServiceDocumentQueryResult result = this.host
                    .createAndWaitSimpleDirectQuery(querySpec, 2, 2);
            boolean rightVersion = true;
            // Expiration time = Now + 7 day.
            long expectedExpirationTime = Utils.getNowMicrosUtc()
                    + TimeUnit.DAYS.toMicros(DEFAULT_RETENTION_LIMIT_DAYS);

            for (Object aggrDocument : result.documents.values()) {
                ResourceAggregateMetric aggrMetric = Utils
                        .fromJson(aggrDocument, ResourceAggregateMetric.class);
                // Make sure all the documents have expiration time set.
                Assert.assertTrue("Expiration time is not correctly set.",
                        aggrMetric.documentExpirationTimeMicros < expectedExpirationTime);

                if (aggrMetric.timeBin.count == NUM_COLLECTIONS * NUM_COMPUTE_RESOURCES) {
                    continue;
                }
                rightVersion = false;
            }
            return rightVersion;
        });

        // kick off an aggregation task with a query that resolves to all resources with the specified resource pool link;
        // ensure that we have aggregated data over latest metric values
        aggregationTaskState = new SingleResourceStatsAggregationTaskState();
        aggregationTaskState.resourceLink = rpReturnState.documentSelfLink;
        aggregationTaskState.latestValueOnly = Stream
                .of(MockStatsAdapter.KEY_1, MockStatsAdapter.KEY_2).collect(Collectors.toSet());

        Map<String, Set<AggregationType>> aggregations = new HashMap<>();
        aggregations.put(MockStatsAdapter.KEY_1, Stream.of(
                AggregationType.AVG, AggregationType.MAX, AggregationType.SUM)
                .collect(Collectors.toSet()));
        aggregations.put(MockStatsAdapter.KEY_2, Stream.of(
                AggregationType.AVG, AggregationType.MAX, AggregationType.SUM)
                .collect(Collectors.toSet()));

        aggregationTaskState.aggregations = aggregations;
        aggregationTaskState.metricNames = new HashSet<>(
                Arrays.asList(MockStatsAdapter.KEY_1, MockStatsAdapter.KEY_2));
        aggregationTaskState.query =
                Query.Builder.create()
                        .addKindFieldClause(ComputeState.class)
                        .addFieldClause(ComputeState.FIELD_NAME_RESOURCE_POOL_LINK,
                        rpReturnState.documentSelfLink).build();
        postServiceSynchronously(SingleResourceStatsAggregationTaskService.FACTORY_LINK,
                aggregationTaskState,
                SingleResourceStatsAggregationTaskState.class);

        this.host.waitFor("Error waiting for rolled up stats", () -> {
            List<String> metricNames = Arrays.asList(
                    MockStatsAdapter.KEY_1 + StatsConstants.HOUR_SUFFIX,
                    MockStatsAdapter.KEY_2 + StatsConstants.HOUR_SUFFIX
            );

            List<Object> results = new ArrayList<>();
            for (String metricName : metricNames) {
                QuerySpecification querySpec = new QuerySpecification();
                querySpec.query = Query.Builder.create()
                        .addKindFieldClause(ResourceAggregateMetric.class)
                        .addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                                UriUtils.buildUriPath(ResourceAggregateMetricService.FACTORY_LINK,
                                        StatsUtil.getMetricKeyPrefix(rpReturnState.documentSelfLink,
                                                metricName)),
                                MatchType.PREFIX)
                        .build();

                querySpec.sortOrder = SortOrder.DESC;
                querySpec.sortTerm = new QueryTask.QueryTerm();
                querySpec.sortTerm.propertyType = ServiceDocumentDescription.TypeName.STRING;
                querySpec.sortTerm.propertyName = ServiceDocument.FIELD_NAME_SELF_LINK;
                querySpec.options.add(QueryOption.EXPAND_CONTENT);
                querySpec.options.add(QueryOption.SORT);
                // No-op in photon-model. Required for special handling of immutable documents.
                // This will prevent Lucene from holding the full result set in memory.
                querySpec.options.add(QueryOption.INCLUDE_ALL_VERSIONS);
                querySpec.resultLimit = 1;

                ServiceDocumentQueryResult result = this.host
                        .createAndWaitSimpleDirectQuery(querySpec, 1, 1);
                results.add(result.documents.get(result.documentLinks.get(0)));
            }

            boolean rightVersion = true;
            // Expiration time = Now + 7 day.
            long expectedExpirationTime = Utils.getNowMicrosUtc()
                    + TimeUnit.DAYS.toMicros(DEFAULT_RETENTION_LIMIT_DAYS);

            for (Object aggrDocument : results) {
                ResourceAggregateMetric aggrMetric = Utils
                        .fromJson(aggrDocument, ResourceAggregateMetric.class);

                // Make sure all the documents have expiration time set.
                Assert.assertTrue("Expiration time is not correctly set.",
                        aggrMetric.documentExpirationTimeMicros < expectedExpirationTime);

                // The assertion here checks whether we are aggregating only on latest value. To
                // that effect, here is the breakdown for the check:
                // count = num of resources: one value for each resource
                // sum = null: not specified in the aggregate type set
                if (aggrMetric.timeBin.min == null
                        && aggrMetric.timeBin.count == NUM_COMPUTE_RESOURCES) {
                    continue;
                }
                rightVersion = false;
            }
            return rightVersion;
        });

        // kick off an aggregation task with 'SingleResourceStatsAggregationTaskState.hasResources'
        // set to false.
        aggregationTaskState =
                new SingleResourceStatsAggregationTaskState();
        aggregationTaskState.resourceLink = computeStateArray[1].documentSelfLink;
        aggregationTaskState.metricNames = new HashSet<>(
                Arrays.asList(MockStatsAdapter.KEY_1, MockStatsAdapter.KEY_2,
                        MockStatsAdapter.KEY_3));
        aggregationTaskState.hasResources = false;

        // 3 ResourceAggregateMetricService services are created for 3
        // MockStatsAdapter constants: KEY_1, KEY_2 and KEY_3
        postServiceSynchronously(SingleResourceStatsAggregationTaskService.FACTORY_LINK,
                aggregationTaskState,
                SingleResourceStatsAggregationTaskState.class);

        this.host.waitFor("Error waiting for rolled up stats", () -> {
            ServiceDocumentQueryResult result = this.host
                    .getExpandedFactoryState(UriUtils.buildUri(this.host,
                            ResourceAggregateMetricService.FACTORY_LINK));

            // Expiration time = Now + 7 day.
            long expectedExpirationTime = Utils.getNowMicrosUtc()
                    + TimeUnit.DAYS.toMicros(DEFAULT_RETENTION_LIMIT_DAYS);

            boolean isMetricZero = true;
            int count = 0;
            for (Object aggrDocument : result.documents.values()) {
                ResourceAggregateMetric aggrMetric = Utils
                        .fromJson(aggrDocument, ResourceAggregateMetric.class);
                // Make sure all the documents have expiration time set.
                Assert.assertTrue("Expiration time is not correctly set.",
                        aggrMetric.documentExpirationTimeMicros < expectedExpirationTime);

                // make sure the timeBin values are 0 and count is 1
                if (aggrMetric.timeBin.sum == 0 && aggrMetric.timeBin.avg == 0 &&
                        aggrMetric.timeBin.max == 0 && aggrMetric.timeBin.min == 0 &&
                        aggrMetric.timeBin.count == 1) {
                    count++;
                }
                // count should match the new documents created with hasResources = false.
                if (count == 3) {
                    isMetricZero = true;
                }
            }
            return isMetricZero;
        });

        // verify that the aggregation tasks have been deleted
        this.host.waitFor("Timeout waiting for task to expire", () -> {
            ServiceDocumentQueryResult res =
                    this.host.getFactoryState(UriUtils.buildUri(
                        this.host, SingleResourceStatsAggregationTaskService.FACTORY_LINK));
            return res.documentLinks.size() == 0;
        });

    }
}
