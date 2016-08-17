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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
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
import com.vmware.photon.controller.model.tasks.monitoring.ResourceStatsAggregationTaskService.ResourceStatsAggregationTaskState;
import com.vmware.photon.controller.model.tasks.monitoring.StatsCollectionTaskService.StatsCollectionTaskState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask.Query;

public class ResourceStatsAggregationTaskServiceTest extends BaseModelTest {

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
        this.host.waitForServiceAvailable(ResourceStatsAggregationTaskService.FACTORY_LINK);
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
        // wait for stats to be populated for all resources
        for (int i = 0; i < this.numResources; i++) {
            String statsUriPath = UriUtils.buildUriPath(computeLinks.get(i),
                    ServiceHost.SERVICE_URI_SUFFIX_STATS);
            this.host.waitFor("Error waiting for stats", () -> {
                ServiceStats resStats = getServiceSynchronously(statsUriPath, ServiceStats.class);
                boolean returnStatus = false;
                for (ServiceStat stat : resStats.entries.values()) {
                    if (stat.latestValue >= 1) {
                        returnStatus = true;
                    }
                }
                return returnStatus;
            });
        }

        // kick off an aggregation task
        ResourceStatsAggregationTaskState aggregationTaskState = new ResourceStatsAggregationTaskState();
        aggregationTaskState.resourceLink = rpReturnState.documentSelfLink;
        aggregationTaskState.metricNames = new HashSet<>(
                Arrays.asList("key-1(Hourly)", "key-2(Hourly)"));
        aggregationTaskState.query = Query.Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_RESOURCE_POOL_LINK,
                        rpReturnState.documentSelfLink)
                .build();
        postServiceSynchronously(ResourceStatsAggregationTaskService.FACTORY_LINK,
                aggregationTaskState,
                ResourceStatsAggregationTaskState.class);
        this.host.waitFor("Error waiting for stats", () -> {
            ServiceDocumentQueryResult result = this.host
                    .getFactoryState(UriUtils.buildUri(this.host,
                            ResourceAggregateMetricsService.FACTORY_LINK));
            return result.documentCount == 1;
        });

        // do assertions
        ServiceDocumentQueryResult result = this.host
                .getExpandedFactoryState(UriUtils.buildUri(this.host,
                        ResourceAggregateMetricsService.FACTORY_LINK));
        assertEquals(1, result.documentCount.longValue());

        ResourceAggregateMetricsState aggrMetricState = Utils
                .fromJson(result.documents.values().iterator().next(),
                        ResourceAggregateMetricsState.class);
        assertEquals(rpReturnState.documentSelfLink, aggrMetricState.computeServiceLink);
        assertEquals(2, aggrMetricState.aggregations.size());
        assertEquals(200.0, aggrMetricState.aggregations.get("key-1(Hourly)").count, 0);
        assertEquals(200.0, aggrMetricState.aggregations.get("key-2(Hourly)").count, 0);
    }
}