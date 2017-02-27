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

package com.vmware.photon.controller.model.adapters.vsphere;

import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.vmware.photon.controller.model.monitoring.ResourceMetricsService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.tasks.ScheduledTaskService;
import com.vmware.photon.controller.model.tasks.ScheduledTaskService.ScheduledTaskState;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.photon.controller.model.tasks.monitoring.StatsCollectionTaskService;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 *
 */
public class TestVSphereStatsTask extends BaseVSphereAdapterTest {

    private ComputeDescription computeHostDescription;
    private ComputeState computeHost;

    @Test
    public void testCollectStats() throws Throwable {
        // Create a resource pool where the VM will be housed
        this.resourcePool = createResourcePool();

        this.auth = createAuth();

        this.computeHostDescription = createComputeDescription();
        this.computeHost = createComputeHost();

        // collect data
        doRefresh();

        // collect stats for a few instances
        StatsCollectionTaskService.StatsCollectionTaskState statCollectionState =
                new StatsCollectionTaskService.StatsCollectionTaskState();
        statCollectionState.resourcePoolLink = this.resourcePool.documentSelfLink;
        ScheduledTaskState statsCollectionTaskState = new ScheduledTaskState();
        statsCollectionTaskState.factoryLink = StatsCollectionTaskService.FACTORY_LINK;
        statsCollectionTaskState.initialStateJson = Utils.toJson(statCollectionState);
        statsCollectionTaskState.intervalMicros =   TimeUnit.MINUTES.toMicros(1);

        TestUtils.doPost(this.host,
                statsCollectionTaskState,
                ScheduledTaskState.class,
                UriUtils.buildUri(this.host,
                        ScheduledTaskService.FACTORY_LINK));

        if (isMock()) {
            return;
        }

        // wait up to 5 minutes as first call to perfManager can take a while.
        host.setTimeoutSeconds(5 * 60);
        host.waitFor("No stats inserted", () -> {
            ServiceDocumentQueryResult state = host
                    .getFactoryState(
                            UriUtils.buildFactoryUri(TestVSphereStatsTask.this.host,
                                    ResourceMetricsService.class));

            return !state.documentLinks.isEmpty();
        });
    }

    private void doRefresh() throws Throwable {
        enumerateComputes(this.computeHost);
    }

    /**
     * Create a compute host representing a vcenter server
     */
    private ComputeState createComputeHost() throws Throwable {
        ComputeState computeState = new ComputeState();
        computeState.id = nextName("host");
        computeState.name = this.computeHostDescription.name;
        computeState.documentSelfLink = computeState.id;
        computeState.descriptionLink = this.computeHostDescription.documentSelfLink;
        //computeState.resourcePoolLink = this.resourcePool.documentSelfLink;
        computeState.adapterManagementReference = getAdapterManagementReference();

        ComputeState returnState = TestUtils.doPost(this.host, computeState,
                ComputeState.class,
                UriUtils.buildUri(this.host, ComputeService.FACTORY_LINK));
        return returnState;
    }
}
