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

package com.vmware.photon.controller.model.adapters.vsphere.stats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import com.vmware.photon.controller.model.adapterapi.ComputeStatsRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse.ComputeStats;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.adapters.vsphere.CustomProperties;
import com.vmware.photon.controller.model.adapters.vsphere.ProvisionContext;
import com.vmware.photon.controller.model.adapters.vsphere.VSphereIOThreadPoolAllocator;
import com.vmware.photon.controller.model.adapters.vsphere.VSphereUriPaths;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService.SingleResourceStatsCollectionTaskState;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService.SingleResourceTaskCollectionStage;

import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.RuntimeFaultFaultMsg;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;

public class VSphereAdapterStatsService extends StatelessService {

    public static final String SELF_LINK = VSphereUriPaths.STATS_SERVICE;

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        op.complete();

        ComputeStatsRequest statsRequest = op.getBody(ComputeStatsRequest.class);
        TaskManager mgr = new TaskManager(this, statsRequest.taskReference);

        ProvisionContext.populateContextThen(this, createInitialContext(statsRequest), ctx -> {
            if (statsRequest.isMockRequest) {
                // patch status to parent task
                persistStats(mockStats(), statsRequest);
                mgr.patchTask(TaskStage.FINISHED);
                return;
            }
            collectStats(ctx, statsRequest);
        });
    }

    private List<ServiceStat> mockStats() {
        List<ServiceStat> res = new ArrayList<>(2);
        ServiceStat m1 = new ServiceStat();
        m1.lastUpdateMicrosUtc = System.currentTimeMillis() * 1000;
        m1.name = "cpu.mock";
        m1.latestValue = 17;
        res.add(m1);

        ServiceStat m2 = new ServiceStat();
        m2.lastUpdateMicrosUtc = System.currentTimeMillis() * 1000;
        m2.name = "memory.mock";
        m2.latestValue = 11;
        res.add(m2);
        return res;
    }

    private void collectStats(ProvisionContext ctx, ComputeStatsRequest statsRequest) {
        ctx.pool.submit(this, ctx.getAdapterManagementReference(), ctx.vSphereCredentials,
                (conn, ce) -> {
                    if (ctx.fail(ce)) {
                        return;
                    }

                    String type = CustomProperties.of(ctx.child).getString(CustomProperties.TYPE);
                    if (type == null) {
                        logInfo("Missing type for %s, cannot retrieve stats",
                                ctx.child.documentSelfLink);
                        return;
                    }

                    StatsClient client;
                    try {
                        client = new StatsClient(conn);
                    } catch (Exception e) {
                        ctx.failWithMessage("Error connecting to PerformanceManager", e);
                        return;
                    }

                    ManagedObjectReference obj = CustomProperties.of(ctx.child)
                            .getMoRef(CustomProperties.MOREF);

                    List<ServiceStat> stats;
                    try {
                        stats = getStats(client, type, obj);
                    } catch (Exception e) {
                        ctx.failWithMessage("Error retrieving stats", e);
                        return;
                    }

                    try {
                        persistStats(stats, statsRequest);
                    } catch (Exception e) {
                        ctx.failWithMessage("Error persisting stats", e);
                        return;
                    }
                });
    }

    private void persistStats(List<ServiceStat> stats, ComputeStatsRequest statsRequest) {
        ComputeStats cs = new ComputeStats();
        cs.computeLink = statsRequest.resourceReference.toString();
        cs.statValues = new HashMap<>();

        for (ServiceStat stat : stats) {
            cs.statValues.put(stat.name, Collections.singletonList(stat));
        }

        SingleResourceStatsCollectionTaskState respBody = new SingleResourceStatsCollectionTaskState();
        respBody.statsList = new ArrayList<>();
        respBody.statsList.add(cs);
        respBody.taskStage = SingleResourceTaskCollectionStage.valueOf(statsRequest.nextStage);
        respBody.statsAdapterReference = UriUtils.buildUri(getHost(), SELF_LINK);

        this.sendRequest(Operation.createPatch(statsRequest.taskReference)
                .setBody(respBody));
    }

    private List<ServiceStat> getStats(StatsClient client, String type, ManagedObjectReference obj)
            throws RuntimeFaultFaultMsg {
        List<ServiceStat> metrics = null;
        switch (type) {
        case VimNames.TYPE_VM:
            metrics = client.retrieveMetricsForVm(obj);
            break;
        case VimNames.TYPE_COMPUTE_RESOURCE:
        case VimNames.TYPE_CLUSTER_COMPUTE_RESOURCE:
            metrics = client.retrieveMetricsForCluster(obj);
            break;
        case VimNames.TYPE_HOST:
            metrics = client.retrieveMetricsForHost(obj);
            break;
        default:
            logInfo("Cannot retrieve metrics for type " + type);
        }

        return metrics;
    }

    private ProvisionContext createInitialContext(ComputeStatsRequest statsRequest) {
        ProvisionContext initialContext = new ProvisionContext(statsRequest);

        // global error handler: it marks the task as failed
        initialContext.errorHandler = failure -> {
            TaskManager mgr = new TaskManager(this, statsRequest.taskReference);
            mgr.patchTaskToFailure(failure);
        };

        initialContext.pool = VSphereIOThreadPoolAllocator.getPool(this);
        return initialContext;
    }
}
