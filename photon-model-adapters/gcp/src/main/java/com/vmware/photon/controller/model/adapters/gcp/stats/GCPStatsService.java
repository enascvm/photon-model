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

package com.vmware.photon.controller.model.adapters.gcp.stats;

import java.net.URI;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Consumer;

import com.vmware.photon.controller.model.adapterapi.ComputeStatsRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse.ComputeStats;
import com.vmware.photon.controller.model.adapters.gcp.GCPUriPaths;
import com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Metrics collection service for Google Cloud Platform. Gets authenticated using OAuth
 * API. Collects instance level metrics for Google Compute Engine instances using Stackdriver
 * monitoring API.
 * TODO: VSYM-1463 - Patch back gcp stats to caller task
 * TODO: VSYM-1465 - Get access token and stats for gcp monitoring
 */
public class GCPStatsService extends StatelessService {
    public static final String SELF_LINK = GCPUriPaths.GCP_STATS_ADAPTER;

    /**
     * Stores GCP metric names and their corresponding units.
     * Metric units are not provided as a part of the response by the API, hence they are
     * hard coded.
     * TODO: VSYM-1462 - Get metric units by making a request to monitoring API
     */
    public static final String[][] METRIC_NAMES_UNITS = {{GCPConstants.CPU_UTILIZATION,
            GCPConstants.UNIT_PERCENT}, {GCPConstants.DISK_READ_BYTES, GCPConstants.UNIT_BYTE},
            {GCPConstants.DISK_READ_OPERATIONS, GCPConstants.UNIT_COUNT},
            {GCPConstants.DISK_WRITE_BYTES, GCPConstants.UNIT_BYTE},
            {GCPConstants.DISK_WRITE_OPERATIONS, GCPConstants.UNIT_COUNT},
            {GCPConstants.NETWORK_IN_BYTES, GCPConstants.UNIT_BYTE},
            {GCPConstants.NETWORK_IN_PACKETS, GCPConstants.UNIT_COUNT},
            {GCPConstants.NETWORK_OUT_BYTES, GCPConstants.UNIT_BYTE},
            {GCPConstants.NETWORK_OUT_PACKETS, GCPConstants.UNIT_COUNT}};

    /**
     * Stages of GCP stats collection
     */
    private enum StatsCollectionStage {
        /**
         * Default first stage for the service. Collecting the VM description.
         */
        VM_DESC,

        /**
         * Collecting compute host description.
         */
        PARENT_VM_DESC,

        /**
         * Collecting credentials from AuthCredentialService.
         */
        CREDENTIALS,

        /**
         * Collecting project name from Resource group.
         */
        PROJECT_NAME,

        /**
         * The last stage, after response is sent back to the caller task.
         */
        FINISHED
    }

    /**
     * Data holder class for GCPStatsService. Stores all the fields used by the service.
     */
    private class GCPStatsDataHolder {
        // Basic fields
        ComputeStateWithDescription computeDesc;
        ComputeStateWithDescription parentDesc;
        StatsCollectionStage stage;
        AuthCredentialsService.AuthCredentialsServiceState parentAuth;
        ComputeStatsRequest statsRequest;
        ComputeStats statsResponse;
        Throwable error;

        // GCP specific fields
        /*
         * Unused warning is suppressed for now. These fields will be used in the future.
         */
        @SuppressWarnings("unused")
        String userEmail;
        @SuppressWarnings("unused")
        String privateKey;
        @SuppressWarnings("unused")
        String accessToken;
        @SuppressWarnings("unused")
        String projectId;
        @SuppressWarnings("unused")
        String instanceId;

        public GCPStatsDataHolder() {
            this.statsResponse = new ComputeStats();
            // create a thread safe map to hold stats values for resource
            this.statsResponse.statValues = new ConcurrentSkipListMap<>();
        }
    }

    /**
     * The REST PATCH request handler. This is the entry of starting stats collection.
     * @param patch Operation which should contain request body.
     */
    @Override
    public void handlePatch(Operation patch) {
        if (!patch.hasBody()) {
            patch.fail(new IllegalArgumentException("body is required"));
            return;
        }
        patch.complete();
        ComputeStatsRequest statsRequest = patch.getBody(ComputeStatsRequest.class);
        GCPStatsDataHolder statsData = new GCPStatsDataHolder();
        statsData.statsRequest = statsRequest;
        statsData.stage = StatsCollectionStage.VM_DESC;
        handleStatsRequest(statsData);
    }

    /**
     * The flow for dealing with each stage in the service.
     * @param statsData The GCPStatsDataHolder instance which decides the current stage.
     */
    public void handleStatsRequest(GCPStatsDataHolder statsData) {
        switch (statsData.stage) {
        case VM_DESC:
            getVMDescription(statsData, StatsCollectionStage.PARENT_VM_DESC);
            break;
        case PARENT_VM_DESC:
            getParentVMDescription(statsData, StatsCollectionStage.CREDENTIALS);
            break;
        case CREDENTIALS:
            getParentAuth(statsData, StatsCollectionStage.PROJECT_NAME);
            break;
        case PROJECT_NAME:
            getProjectName(statsData, StatsCollectionStage.FINISHED);
            break;
        case FINISHED:
            // Patch status to parent task
            AdapterUtils.sendPatchToProvisioningTask(this, statsData.statsRequest.taskReference);
            break;
        default:
            String err = String.format("Unknown GCP stats collection stage %s ", statsData.stage.toString());
            logSevere(err);
            statsData.error = new IllegalStateException(err);
            // Patch failure back to parent task
            AdapterUtils.sendFailurePatchToProvisioningTask(this,
                    statsData.statsRequest.taskReference, statsData.error);
        }
    }

    /**
     * Gets the description of the VM for which stats are to be collected.
     * Sets the VM ID, required for making metric requests, in current GCPStatsDataHolder
     * instance.
     * @param statsData The GCPStatsDataHolder instance containing statsRequest.
     * @param nextStage The next stage of CollectionStage for the service.
     */
    private void getVMDescription(GCPStatsDataHolder statsData, StatsCollectionStage nextStage) {
        Consumer<Operation> onSuccess = (op) -> {
            statsData.computeDesc = op.getBody(ComputeStateWithDescription.class);
            statsData.instanceId = statsData.computeDesc.id;
            statsData.stage = nextStage;
            handleStatsRequest(statsData);
        };
        URI computeUri = UriUtils.extendUriWithQuery(statsData.statsRequest.resourceReference,
                UriUtils.URI_PARAM_ODATA_EXPAND, Boolean.TRUE.toString());
        AdapterUtils.getServiceState(this, computeUri, onSuccess, getFailureConsumer(statsData));
    }

    /**
     * Gets the description of the compute host corresponding to the VM for which stats
     * are to be collected.
     * @param statsData The GCPStatsDataHolder instance containing statsRequest.
     * @param nextStage The next stage of CollectionStage for the service.
     */
    private void getParentVMDescription(GCPStatsDataHolder statsData, StatsCollectionStage nextStage) {
        Consumer<Operation> onSuccess = (op) -> {
            statsData.parentDesc = op.getBody(ComputeStateWithDescription.class);
            statsData.stage = nextStage;
            handleStatsRequest(statsData);
        };
        URI computeUri = UriUtils.extendUriWithQuery(
                UriUtils.buildUri(getHost(), statsData.computeDesc.parentLink),
                UriUtils.URI_PARAM_ODATA_EXPAND, Boolean.TRUE.toString());
        AdapterUtils.getServiceState(this, computeUri, onSuccess, getFailureConsumer(statsData));
    }

    /**
     * Gets the credentials for the corresponding compute host from AuthCredentialService.
     * @param statsData The GCPStatsDataHolder instance containing statsRequest.
     * @param nextStage The next stage of CollectionStage for the service.
     */
    private void getParentAuth(GCPStatsDataHolder statsData, StatsCollectionStage nextStage) {
        Consumer<Operation> onSuccess = (op) -> {
            statsData.parentAuth = op.getBody(AuthCredentialsServiceState.class);
            statsData.userEmail = statsData.parentAuth.userEmail;
            statsData.privateKey = statsData.parentAuth.privateKey;
            statsData.stage = nextStage;
            handleStatsRequest(statsData);
        };
        String authLink = statsData.parentDesc.description.authCredentialsLink;
        AdapterUtils.getServiceState(this, authLink, onSuccess, getFailureConsumer(statsData));
    }

    /**
     * Gets the project name for the corresponding compute host.
     * @param statsData The GCPStatsDataHolder instance containing statsRequest.
     * @param nextStage The next stage of CollectionStage for the service.
     */
    private void getProjectName(GCPStatsDataHolder statsData, StatsCollectionStage nextStage) {
        Consumer<Operation> onSuccess = (op) -> {
            ResourceGroupState rgs = op.getBody(ResourceGroupState.class);
            statsData.projectId = rgs.name;
            statsData.stage = nextStage;
            handleStatsRequest(statsData);
        };
        ArrayList<String> groupLink = new ArrayList<String>(statsData.parentDesc.description.groupLinks);
        AdapterUtils.getServiceState(this, groupLink.get(0), onSuccess, getFailureConsumer(statsData));
    }

    private Consumer<Throwable> getFailureConsumer(GCPStatsDataHolder statsData) {
        return ((t) -> {
            AdapterUtils.sendFailurePatchToProvisioningTask(this,
                    statsData.statsRequest.taskReference, t);
        });
    }
}