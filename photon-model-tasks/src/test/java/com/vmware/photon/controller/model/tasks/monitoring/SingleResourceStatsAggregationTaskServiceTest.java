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
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;


public class SingleResourceStatsAggregationTaskServiceTest extends BaseModelTest {

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
        ComputeState resComputeState = postServiceSynchronously(
                    ComputeService.FACTORY_LINK, computeState,
                    ComputeState.class);

        StatsCollectionTaskState collectionTaskState = new StatsCollectionTaskState();
        collectionTaskState.resourcePoolLink = rpReturnState.documentSelfLink;
        int counter = 0;
        while (counter < 5) {
            StatsCollectionTaskState returnState = this
                    .postServiceSynchronously(
                            StatsCollectionTaskService.FACTORY_LINK,
                            collectionTaskState, StatsCollectionTaskState.class);
            waitForFinishedTask(StatsCollectionTaskState.class,
                    returnState.documentSelfLink);
            counter++;
        }
        // wait for stats to be populated
        String statsUriPath = UriUtils.buildUriPath(resComputeState.documentSelfLink,
                    ServiceHost.SERVICE_URI_SUFFIX_STATS);
        this.host.waitFor("Error waiting for stats", () -> {
            ServiceStats resStats = getServiceSynchronously(statsUriPath, ServiceStats.class);
            boolean returnStatus = false;
            for (ServiceStat stat : resStats.entries.values()) {
                if (stat.latestValue == 5) {
                    returnStatus = true;
                }
            }
            return returnStatus;
        });

        // kick off an aggregation task
        SingleResourceStatsAggregationTaskState aggregationTaskState = new SingleResourceStatsAggregationTaskState();
        aggregationTaskState.resourceLink = resComputeState.documentSelfLink;
        aggregationTaskState.metricNames = new HashSet<>(
                Arrays.asList("key-1", "key-2"));
        postServiceSynchronously(SingleResourceStatsAggregationTaskService.FACTORY_LINK,
                aggregationTaskState,
                SingleResourceStatsAggregationTaskState.class);
        this.host.waitFor("Error waiting for rolled up stats", () -> {
            ServiceDocumentQueryResult result = this.host
                    .getExpandedFactoryState(UriUtils.buildUri(this.host,
                            ResourceAggregateMetricService.FACTORY_LINK));
            return (result.documentCount == 4);
        });

        // kick off an another aggregation task, ensure the version number has been updated
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
            boolean rightVersion = true;
            for (Object aggrDocument: result.documents.values()) {
                ResourceAggregateMetric aggrMetric = Utils.fromJson(aggrDocument, ResourceAggregateMetric.class);
                if (aggrMetric.documentVersion == 1 && (aggrMetric.timeBin.count == 5) &&
                        aggrMetric.timeBin.avg == 3.0 && aggrMetric.timeBin.max == 5.0 &&
                        aggrMetric.timeBin.min == 1.0) {
                    continue;
                }
                rightVersion = false;
            }
            return rightVersion;
        });
    }
}