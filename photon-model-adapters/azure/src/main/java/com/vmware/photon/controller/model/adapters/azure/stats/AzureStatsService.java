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

package com.vmware.photon.controller.model.adapters.azure.stats;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.vmware.photon.controller.model.adapterapi.ComputeStatsRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService.SingleResourceStatsCollectionTaskState;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService.SingleResourceTaskCollectionStage;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;

public class AzureStatsService extends StatelessService {
    public static final String SELF_LINK = AzureUriPaths.AZURE_STATS_ADAPTER;

    private class AzureStatsDataHolder {
        public ComputeStateWithDescription computeDesc;
        public ComputeStatsRequest statsRequest;
        public TaskManager taskManager;
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }

        ComputeStatsRequest statsRequest = op.getBody(ComputeStatsRequest.class);
        op.complete();
        TaskManager taskManager = new TaskManager(this, statsRequest.taskReference,
                statsRequest.resourceLink());
        if (statsRequest.isMockRequest) {
            // patch status to parent task
            taskManager.finishTask();
            return;
        }

        AzureStatsDataHolder statsData = new AzureStatsDataHolder();
        statsData.statsRequest = statsRequest;
        statsData.taskManager = taskManager;
        getVMDescription(statsData);
    }

    /**
     * Returns if the given compute description is a compute host or not.
     */
    private boolean isComputeHost(ComputeDescription computeDescription) {
        Set<String> supportedChildren = computeDescription.supportedChildren;
        return supportedChildren != null && supportedChildren.contains(ComputeType.VM_GUEST.name());
    }

    private void getVMDescription(AzureStatsDataHolder statsData) {
        Consumer<Operation> onSuccess = (op) -> {
            statsData.computeDesc = op.getBody(ComputeStateWithDescription.class);
            boolean isComputeHost = isComputeHost(statsData.computeDesc.description);
            // If not a compute host, relay the request to Azure Stats Gatherer
            // with the task reference intact, so the Gatherer will send the
            // response directly to the task which called it.
            if (!isComputeHost) {
                ComputeStatsRequest statsRequest = statsData.statsRequest;
                Operation statsOp = Operation.createPatch(
                        UriUtils.buildUri(getHost(), AzureUriPaths.AZURE_COMPUTE_STATS_GATHERER))
                        .setBody(statsRequest);
                sendRequest(statsOp);
                return;
            }

            getComputeHostStats(statsData);
        };
        URI computeUri = UriUtils.extendUriWithQuery(statsData.statsRequest.resourceReference,
                UriUtils.URI_PARAM_ODATA_EXPAND, Boolean.TRUE.toString());
        AdapterUtils.getServiceState(this, computeUri, onSuccess, getFailureConsumer(statsData));
    }

    /**
     * Get metrics at the compute host level.
     * @param statsData
     */
    private void getComputeHostStats(AzureStatsDataHolder statsData) {
        ComputeStatsRequest statsRequest = statsData.statsRequest;
        Collection<Operation> opCollection = new ArrayList<>();

        Operation computeStatsOp = Operation.createPatch(
                UriUtils.buildUri(getHost(), AzureUriPaths.AZURE_COMPUTE_HOST_STATS_GATHERER))
                .setBody(statsRequest)
                .setReferer(getUri());
        opCollection.add(computeStatsOp);

        Operation storageStatsOp = Operation.createPatch(
                UriUtils.buildUri(getHost(),
                        AzureUriPaths.AZURE_COMPUTE_HOST_STORAGE_STATS_GATHERER))
                .setBody(statsRequest)
                .setReferer(getUri());

        opCollection.add(storageStatsOp);

        OperationJoin.create(opCollection)
                .setCompletion((ops, exs) -> {
                    if (exs != null) {
                        exs.values().forEach(ex -> logWarning(() -> String.format("Error: %s",
                                ex.getMessage())));
                        sendFailurePatch(statsData, exs.values().iterator().next());
                        return;
                    }
                    SingleResourceStatsCollectionTaskState statsResponse =
                            new SingleResourceStatsCollectionTaskState();
                    statsResponse.taskStage = SingleResourceTaskCollectionStage
                            .valueOf(statsData.statsRequest.nextStage);
                    statsResponse.statsList = new ArrayList<>();
                    statsResponse.statsAdapterReference = UriUtils.buildUri(getHost(), SELF_LINK);

                    for (Map.Entry<Long, Operation> op : ops.entrySet()) {
                        ComputeStatsResponse.ComputeStats stats = op.getValue()
                                .getBody(ComputeStatsResponse.ComputeStats.class);
                        if (stats != null) {
                            if (statsResponse.statsList == null ||
                                    statsResponse.statsList.size() == 0) {
                                statsResponse.statsList.add(stats);
                            } else {
                                for (Map.Entry<String, List<ServiceStat>> entry :
                                        stats.statValues.entrySet()) {
                                    statsResponse.statsList.get(0).statValues
                                            .put(entry.getKey(), entry.getValue());
                                }
                            }
                        }
                    }
                    this.sendRequest(
                            Operation.createPatch(statsData.statsRequest.taskReference)
                                    .setBody(statsResponse));
                    logFine(() -> "Finished collection of compute host stats");
                })
                .sendWith(this);
    }

    /**
     * Sends a failure patch back to the caller.
     */
    private void sendFailurePatch(AzureStatsDataHolder statsData, Throwable throwable) {
        // TODO: https://jira-hzn.eng.vmware.com/browse/VSYM-1271
        statsData.taskManager.patchTaskToFailure(throwable);
    }

    /**
     * Returns an instance of Consumer to fail the task.
     */
    private Consumer<Throwable> getFailureConsumer(AzureStatsDataHolder statsData) {
        return ((throwable) -> {
            statsData.taskManager.patchTaskToFailure(throwable);
        });
    }
}
