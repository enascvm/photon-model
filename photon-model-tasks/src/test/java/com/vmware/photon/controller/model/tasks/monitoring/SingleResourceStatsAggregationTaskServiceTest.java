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

import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService.ResourceMetrics;
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
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

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

        // number of keys = 2, number of lastCollectionTimeMetrics = 2
        int numberOfRawMetrics = NUM_COLLECTIONS * NUM_COMPUTE_RESOURCES * 2 * 2;

        // kick off an aggregation task
        SingleResourceStatsAggregationTaskState aggregationTaskState = new SingleResourceStatsAggregationTaskState();
        aggregationTaskState.resourceLink = computeStateArray[0].documentSelfLink;
        aggregationTaskState.metricNames = new HashSet<>(
                Arrays.asList(MockStatsAdapter.KEY_1, MockStatsAdapter.KEY_2, MockStatsAdapter.KEY_3));
        postServiceSynchronously(SingleResourceStatsAggregationTaskService.FACTORY_LINK,
                aggregationTaskState,
                SingleResourceStatsAggregationTaskState.class);

        long expectedExpirationTime = Utils.getNowMicrosUtc()
                + TimeUnit.DAYS.toMicros(DEFAULT_RETENTION_LIMIT_DAYS);

        this.host.waitFor("Error waiting for rolled up stats", () -> {
            ServiceDocumentQueryResult result = this.host
                    .getExpandedFactoryState(UriUtils.buildUri(this.host,
                            ResourceMetricsService.FACTORY_LINK));
            String randomDocumentLink = result.documentLinks.get(0);
            ResourceMetrics metric = Utils.fromJson(result.documents.get(randomDocumentLink),
                    ResourceMetrics.class);
            // Make sure all the documents have expiration time set.
            Assert.assertTrue("Expiration time is not correctly set.",
                    metric.documentExpirationTimeMicros < expectedExpirationTime);
            return (result.documentCount == 2 + numberOfRawMetrics);
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

        // verify that the aggregation tasks have been deleted
        this.host.waitFor("Timeout waiting for task to expire", () -> {
            ServiceDocumentQueryResult res =
                    this.host.getFactoryState(UriUtils.buildUri(
                        this.host, SingleResourceStatsAggregationTaskService.FACTORY_LINK));
            return res.documentLinks.size() == 0;
        });
    }
}
