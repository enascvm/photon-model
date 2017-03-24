/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.PhotonModelMetricServices;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.monitoring.ResourceAggregateMetricService;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.photon.controller.model.tasks.monitoring.StatsAggregationTaskService.StatsAggregationTaskState;
import com.vmware.photon.controller.model.tasks.monitoring.StatsCollectionTaskService.StatsCollectionTaskState;
import com.vmware.photon.controller.model.util.ClusterUtil.ServiceTypeCluster;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;

public class MetricsClusterTest extends BaseModelTest {

    public int numResources = 100;

    VerificationHost metricHost = null;

    @Before
    public void setup() throws Throwable {
        // Start a metric Host separately.
        this.metricHost = VerificationHost.create(0);
        this.metricHost.start();

        ServiceTypeCluster.METRIC_SERVICE.setUri(this.metricHost.getUri().toString());
        PhotonModelMetricServices.startServices(this.metricHost);
    }

    @After
    public void cleanUp() {
        ServiceTypeCluster.METRIC_SERVICE.setUri(null);
        if (this.metricHost == null) {
            return;
        }
        this.metricHost.tearDownInProcessPeers();
        this.metricHost.toggleNegativeTestMode(false);
        this.metricHost.tearDown();
    }

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
        this.host.waitForServiceAvailable(StatsAggregationTaskService.FACTORY_LINK);
        this.host.waitForServiceAvailable(SingleResourceStatsAggregationTaskService.FACTORY_LINK);
        this.host.waitForServiceAvailable(ResourceMetricsService.FACTORY_LINK);
        this.host.waitForServiceAvailable(ResourceAggregateMetricService.FACTORY_LINK);
        this.host.waitForServiceAvailable(MockStatsAdapter.SELF_LINK);
    }

    @Test
    public void testStatsCollectorCreation() throws Throwable {
        // create a resource pool
        ResourcePoolState rpState = new ResourcePoolState();
        rpState.name = UUID.randomUUID().toString();
        ResourcePoolState rpReturnState = postServiceSynchronously(
                ResourcePoolService.FACTORY_LINK, rpState,
                ResourcePoolState.class);

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
        computeState.resourcePoolLink = rpReturnState.documentSelfLink;
        List<String> computeLinks = new ArrayList<>(this.numResources);
        for (int i = 0; i < this.numResources; i++) {
            ComputeState res = postServiceSynchronously(
                    ComputeService.FACTORY_LINK, computeState,
                    ComputeState.class);
            computeLinks.add(res.documentSelfLink);
        }

        // Run a stats collection on the resources
        StatsCollectionTaskState collectionTaskState = new StatsCollectionTaskState();
        collectionTaskState.resourcePoolLink = rpReturnState.documentSelfLink;
        collectionTaskState.options = EnumSet.of(TaskOption.SELF_DELETE_ON_COMPLETION);
        collectionTaskState.taskInfo = TaskState.createDirect();
        postServiceSynchronously(
                StatsCollectionTaskService.FACTORY_LINK, collectionTaskState,
                StatsCollectionTaskState.class);

        // verify that the collection tasks have been deleted
        this.host.waitFor("Timeout waiting for task to expire", () -> {
            ServiceDocumentQueryResult collectRes =
                    this.host.getFactoryState(UriUtils.buildUri(
                        this.host, StatsCollectionTaskService.FACTORY_LINK));
            if (collectRes.documentLinks.size() == 0) {
                return true;
            }
            return false;
        });

        // Get a Count on this.host() for Resource metrics
        Long resourceHostResourceMetricCount = this.getDocumentCount(this.host, ResourceMetricsService.FACTORY_LINK);
        // Get a Count on this.metricHost() for ResourceMetrics
        Long metricHostResourceMetricCount = this.getDocumentCount(this.metricHost, ResourceMetricsService.FACTORY_LINK);

        // Count should be 0 on this.host()
        assertEquals(0, resourceHostResourceMetricCount.intValue());
        // Count should be something on this.metricHost()
        assertEquals(this.numResources * 2, metricHostResourceMetricCount.intValue());

        // Kick off an Aggregation Task - Verifies that the ResourceMetricQueries are going to the right cluster.
        StatsAggregationTaskState aggregationTaskState = new StatsAggregationTaskState();
        Query taskQuery = Query.Builder.create()
                .addFieldClause(ComputeState.FIELD_NAME_RESOURCE_POOL_LINK, rpReturnState.documentSelfLink).build();
        aggregationTaskState.query =  taskQuery;
        aggregationTaskState.metricNames = Collections.singleton(MockStatsAdapter.KEY_1);
        aggregationTaskState.taskInfo = TaskState.createDirect();
        postServiceSynchronously(StatsAggregationTaskService.FACTORY_LINK, aggregationTaskState,
                StatsAggregationTaskState.class);

        // verify that the aggregation tasks have been deleted
        this.host.waitFor("Timeout waiting for task to expire", () -> {
            ServiceDocumentQueryResult res =
                    this.host.getFactoryState(UriUtils.buildUri(
                        this.host, StatsAggregationTaskService.FACTORY_LINK));
            if (res.documentLinks.size() == 0) {
                return true;
            }
            return false;
        });

        this.host.waitFor("Error waiting for stats", () -> {
            ServiceDocumentQueryResult aggrRes = this.metricHost.getFactoryState(UriUtils.buildUri(this.metricHost,
                        ResourceAggregateMetricService.FACTORY_LINK));
            if (aggrRes.documentCount ==  this.numResources) {
                return true;
            }
            return false;
        });

        // Get a Count on this.host() for Aggregate metrics
        Long resourceHostAggregateMetricCount = this.getDocumentCount(this.host, ResourceAggregateMetricService.FACTORY_LINK);
        // Get a Count on this.metricHost() for Aggregate Metrics
        Long metricHostAggregateMetricCount = this.getDocumentCount(this.metricHost, ResourceAggregateMetricService.FACTORY_LINK);

        // Count should be 0 on this.host()
        assertEquals(0, resourceHostAggregateMetricCount.intValue());
        // Count should be something on this.metricHost()
        assertEquals(this.numResources, metricHostAggregateMetricCount.intValue());
    }

    private Long getDocumentCount(VerificationHost host, String factoryLink) {
        QueryTask queryTask = QueryTask.Builder
                .createDirectTask()
                .addOption(QueryOption.INCLUDE_ALL_VERSIONS)
                .addOption(QueryOption.COUNT)
                .setQuery(
                        Query.Builder.create().addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                                factoryLink, MatchType.PREFIX).build())
                .build();
        host.createQueryTaskService(queryTask, false, true, queryTask, null);
        return queryTask.results.documentCount;
    }
}
