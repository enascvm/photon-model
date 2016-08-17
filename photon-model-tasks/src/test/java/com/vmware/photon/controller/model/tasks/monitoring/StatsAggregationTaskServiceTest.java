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

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.monitoring.ResourceAggregateMetricsService;
import com.vmware.photon.controller.model.monitoring.ResourceAggregateMetricsService.ResourceAggregateMetricsState;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.monitoring.StatsAggregationTaskService.StatsAggregationTaskState;
import com.vmware.photon.controller.model.tasks.monitoring.StatsCollectionTaskService.StatsCollectionTaskState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class StatsAggregationTaskServiceTest extends BaseModelTest {

    public int numResources = 200;

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
        this.host.waitForServiceAvailable(MockStatsAdapter.SELF_LINK);
    }

    @Test
    public void testStatsAggregation() throws Throwable {
        // create a resource pool
        ResourcePoolState rpState = new ResourcePoolState();
        rpState.name = "testName";
        ResourcePoolState rpReturnState = postServiceSynchronously(
                ResourcePoolService.FACTORY_LINK, rpState,
                ResourcePoolState.class);
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
        List<String> computeLinks = new ArrayList<String>();
        for (int i = 0; i < this.numResources; i++) {
            ComputeState res = postServiceSynchronously(
                    ComputeService.FACTORY_LINK, computeState,
                    ComputeState.class);
            computeLinks.add(res.documentSelfLink);
        }
        StatsCollectionTaskState collectionTaskState = new StatsCollectionTaskState();
        collectionTaskState.resourcePoolLink = rpReturnState.documentSelfLink;
        postServiceSynchronously(
                StatsCollectionTaskService.FACTORY_LINK, collectionTaskState,
                StatsCollectionTaskState.class);

        // kick off an aggregation task when stats are not populated
        StatsAggregationTaskState aggregationTaskState = new StatsAggregationTaskState();
        aggregationTaskState.resourcePoolLink =  rpReturnState.documentSelfLink;
        postServiceSynchronously(StatsAggregationTaskService.FACTORY_LINK, aggregationTaskState,
                StatsAggregationTaskState.class);
        this.host.waitFor("Error waiting for stats", () -> {
            ServiceDocumentQueryResult aggrRes = this.host.getFactoryState(UriUtils.buildUri(this.host,
                        ResourceAggregateMetricsService.FACTORY_LINK));
            // Expect 0 stats because they're not collected yet
            if (aggrRes.documentCount == 0) {
                return true;
            }
            return false;
        });

        // wait for stats to be populated for all resources
        for (int i = 0; i < this.numResources; i++) {
            String statsUriPath = UriUtils.buildUriPath(computeLinks.get(i),
                    ServiceHost.SERVICE_URI_SUFFIX_STATS);
            this.host.waitFor("Error waiting for stats", () -> {
                ServiceStats resStats = getServiceSynchronously(statsUriPath, ServiceStats.class);
                boolean returnStatus = false;
                for (ServiceStat stat : resStats.entries.values()) {
                    if (stat.latestValue >= 1 ) {
                        returnStatus = true;
                    }
                }
                return returnStatus;
            });
        }
        // kick off an aggregation task
        aggregationTaskState = new StatsAggregationTaskState();
        aggregationTaskState.resourcePoolLink =  rpReturnState.documentSelfLink;
        postServiceSynchronously(StatsAggregationTaskService.FACTORY_LINK, aggregationTaskState,
                StatsAggregationTaskState.class);
        this.host.waitFor("Error waiting for stats", () -> {
            ServiceDocumentQueryResult aggrRes = this.host.getFactoryState(UriUtils.buildUri(this.host,
                        ResourceAggregateMetricsService.FACTORY_LINK));
            if (aggrRes.documentCount == (2 * this.numResources)) {
                return true;
            }
            return false;
        });
        ServiceDocumentQueryResult aggrRes = this.host.getExpandedFactoryState(UriUtils.buildUri(this.host,
                ResourceAggregateMetricsService.FACTORY_LINK));
        assertTrue(aggrRes.documentCount == (2 * this.numResources));
        int countOfCurrentMetrics = 0;
        int countOfPreviousMetrics = 0;
        for (Object aggrStatDoc : aggrRes.documents.values()) {
            ResourceAggregateMetricsState aggrMetricState = Utils.fromJson(aggrStatDoc, ResourceAggregateMetricsState.class);
            assertTrue((computeLinks.contains(aggrMetricState.computeServiceLink)));
            if (aggrMetricState.aggregations.size() == 4) {
                countOfCurrentMetrics++;
            } else if (aggrMetricState.aggregations.size() == 0) {
                // test does not have any metrics for the previous interval
                countOfPreviousMetrics++;
            }
        }
        assertTrue(countOfCurrentMetrics == this.numResources);
        assertTrue(countOfPreviousMetrics == this.numResources);
    }
}
