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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.vmware.photon.controller.model.adapterapi.ComputeStatsRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse.ComputeStats;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.ScheduledTaskService;
import com.vmware.photon.controller.model.tasks.ScheduledTaskService.ScheduledTaskState;
import com.vmware.photon.controller.model.tasks.monitoring.StatsCollectionTaskService.StatsCollectionTaskState;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class StatsCollectionTaskServiceTest extends BaseModelTest {

    public int numResources = 100;

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
        this.host.waitForServiceAvailable(MockStatsAdapter.SELF_LINK);
        this.host.waitForServiceAvailable(CustomStatsAdapter.SELF_LINK);
    }

    @Test
    public void testStatsCollectorCreation() throws Throwable {
        // create a resource pool
        ResourcePoolState rpState = new ResourcePoolState();
        rpState.name = UUID.randomUUID().toString();
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
        List<String> computeLinks = new ArrayList<>();
        for (int i = 0; i < this.numResources; i++) {
            ComputeState res = postServiceSynchronously(
                    ComputeService.FACTORY_LINK, computeState,
                    ComputeState.class);
            computeLinks.add(res.documentSelfLink);
        }
        // create a stats collection scheduler task
        StatsCollectionTaskState statCollectionState =
                new StatsCollectionTaskState();
        statCollectionState.resourcePoolLink = rpReturnState.documentSelfLink;
        ScheduledTaskState statsCollectionTaskState = new ScheduledTaskState();
        statsCollectionTaskState.factoryLink = StatsCollectionTaskService.FACTORY_LINK;
        statsCollectionTaskState.initialStateJson = Utils.toJson(statCollectionState);
        postServiceSynchronously(
                ScheduledTaskService.FACTORY_LINK, statsCollectionTaskState,
                ScheduledTaskState.class);
        ServiceDocumentQueryResult res = this.host.getFactoryState(UriUtils
                .buildExpandLinksQueryUri(UriUtils.buildUri(this.host,
                        ScheduledTaskService.FACTORY_LINK)));
        assertTrue(res.documents.size() > 0);

        // get stats from resources; make sure maintenance has run more than once
        for (int i = 0; i < this.numResources; i++) {
            String statsUriPath = UriUtils.buildUriPath(computeLinks.get(i),
                    ServiceHost.SERVICE_URI_SUFFIX_STATS);
            this.host.waitFor("Error waiting for stats", () -> {
                ServiceStats resStats = getServiceSynchronously(statsUriPath, ServiceStats.class);
                boolean returnStatus = false;

                for (ServiceStat stat : resStats.entries.values()) {
                    if (stat.latestValue >= this.numResources &&
                            stat.timeSeriesStats.bins.size() > 0) {
                        returnStatus = true;
                        break;
                    }
                }
                return returnStatus;
            });
        }
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
        statCollectionState.statsAdapterReference = UriUtils.buildUri(this.host, CustomStatsAdapter.SELF_LINK);
        ScheduledTaskState statsCollectionTaskState = new ScheduledTaskState();
        statsCollectionTaskState.factoryLink = StatsCollectionTaskService.FACTORY_LINK;
        statsCollectionTaskState.initialStateJson = Utils.toJson(statCollectionState);
        statsCollectionTaskState.intervalMicros = TimeUnit.MILLISECONDS.toMicros(250);
        postServiceSynchronously(
                ScheduledTaskService.FACTORY_LINK, statsCollectionTaskState,
                ScheduledTaskState.class);
        ServiceDocumentQueryResult res = this.host.getFactoryState(UriUtils
                .buildExpandLinksQueryUri(UriUtils.buildUri(this.host,
                        ScheduledTaskService.FACTORY_LINK)));
        assertTrue(res.documents.size() > 0);

        // get stats from resources
        for (int i = 0; i < this.numResources; i++) {
            String statsUriPath = UriUtils.buildUriPath(computeLinks.get(i),
                    ServiceHost.SERVICE_URI_SUFFIX_STATS);
            this.host.waitFor("Error waiting for stats", () -> {
                ServiceStats resStats = getServiceSynchronously(statsUriPath, ServiceStats.class);
                boolean returnStatus = false;

                // check if custom stats adapter was invoked
                for (ServiceStat stat : resStats.entries.values()) {
                    if (stat.name.startsWith(CustomStatsAdapter.KEY_1)) {
                        returnStatus = true;
                        break;
                    }
                }
                return returnStatus;
            });
        }
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
        statCollectionState.statsAdapterReference = UriUtils.buildUri(this.host, CustomStatsAdapter.SELF_LINK);
        ScheduledTaskState statsCollectionTaskState = new ScheduledTaskState();
        statsCollectionTaskState.factoryLink = StatsCollectionTaskService.FACTORY_LINK;
        statsCollectionTaskState.initialStateJson = Utils.toJson(statCollectionState);
        statsCollectionTaskState.intervalMicros = TimeUnit.MILLISECONDS.toMicros(250);
        postServiceSynchronously(
                ScheduledTaskService.FACTORY_LINK, statsCollectionTaskState,
                ScheduledTaskState.class);
        ServiceDocumentQueryResult res = this.host.getFactoryState(UriUtils
                .buildExpandLinksQueryUri(UriUtils.buildUri(this.host,
                        ScheduledTaskService.FACTORY_LINK)));
        assertTrue(res.documents.size() > 0);

        // get stats from resources
        for (int i = 0; i < this.numResources; i++) {
            String statsUriPath = UriUtils.buildUriPath(computeLinks.get(i),
                    ServiceHost.SERVICE_URI_SUFFIX_STATS);
            this.host.waitFor("Error waiting for stats", () -> {
                ServiceStats resStats = getServiceSynchronously(statsUriPath, ServiceStats.class);
                boolean returnStatus = false;

                // check if custom stats adapter was invoked
                for (ServiceStat stat : resStats.entries.values()) {
                    if (stat.name.startsWith(CustomStatsAdapter.KEY_1)) {
                        returnStatus = true;
                        break;
                    }
                }
                return returnStatus;
            });
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
                ComputeStatsResponse response = new ComputeStatsResponse();
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
                response.taskStage = request.nextStage;
                this.sendRequest(Operation.createPatch(request.taskReference)
                        .setBody(response));
                break;
            default:
                super.handleRequest(op);
            }
        }

    }
}
