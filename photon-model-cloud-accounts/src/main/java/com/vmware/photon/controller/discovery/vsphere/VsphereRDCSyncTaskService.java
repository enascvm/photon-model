/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.discovery.vsphere;

import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.CCS_HOST;
import static com.vmware.photon.controller.discovery.vsphere.VsphereOnPremEndpointAdapterService.DC_ID_KEY;
import static com.vmware.photon.controller.model.UriPaths.CCS_VALIDATE_SERVICE;
import static com.vmware.photon.controller.model.UriPaths.VSPHERE_RDC_SYNC_TASK_PATH;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

import com.vmware.photon.controller.discovery.vsphere.ExecuteCommandRequest.ExecuteCommandRequestBuilder;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskFactoryService;
import com.vmware.xenon.services.common.TaskService;

/**
 * Task Service to force the RDC Sync for vSphere cloud account.
 */
public class VsphereRDCSyncTaskService extends
        TaskService<VsphereRDCSyncTaskService.VsphereRDCSyncTaskState> {

    public static final String FACTORY_LINK = VSPHERE_RDC_SYNC_TASK_PATH;
    public static final String STATUS_FIELD = "status";
    public static final String SUCCESS = "SUCCESS";

    /**
     * Maintenance interval.
     */
    public static final String PROPERTY_MAINT_INTERVAL_SECONDS = "VsphereRDCSyncTaskService" +
            ".INTERVAL_SECONDS";
    private static final long DEFAULT_MAINT_INTERVAL_SECONDS = 30;

    /**
     * The ccs-command-server host to force vCenter data collection.
     */
    public URI ccsHost;

    /**
     * The command definition id to force vCenter data collection task.
     */
    public static final String COMMAND_DEFINITION_ID = "6f2ba5918767e6f5";

    /**
     * Task state of vSphereRDCSync Service.
     */
    public static class VsphereRDCSyncTaskState extends TaskService.TaskServiceState {

        @Documentation(description = "The endpoint to be updated")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String endpointLink;

        @Documentation(description = "This is only for internal usage. It keeps track of internal stages.")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public SubStage subStage;

        @Documentation(description = "Existing endpoint")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public EndpointState endpointState;

        /**
         * Task options
         */
        public EnumSet<TaskOption> options;
    }

    /**
     * Endpoint update sub-stages.
     */
    public enum SubStage {

        /**
         * Check if the status field value in the customProperties become SUCCESS
         */
        CHECK_STATUS_FIELD,

        /**
         * Force vCenter data collector sync.
         */
        SYNC_DATA_COLLECTOR,

        /**
         * Successful sync RDC.
         */
        SUCCESS,

        /**
         * Error while syncing endpoint.
         */
        ERROR
    }


    public VsphereRDCSyncTaskService() {
        super(VsphereRDCSyncTaskState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.toggleOption(ServiceOption.PERIODIC_MAINTENANCE, true);
    }

    public static FactoryService createFactory() {
        TaskFactoryService fs = new TaskFactoryService(VsphereRDCSyncTaskState.class) {
            @Override
            public Service createServiceInstance() throws Throwable {
                return new VsphereRDCSyncTaskService();
            }
        };
        fs.setPeerNodeSelectorPath(ServiceUriPaths.DEFAULT_1X_NODE_SELECTOR);
        return fs;
    }

    @Override
    public void handleStart(Operation start) {
        long maintenanceInterval = Long.getLong(PROPERTY_MAINT_INTERVAL_SECONDS, DEFAULT_MAINT_INTERVAL_SECONDS);
        if (!ServiceHost.isServiceCreate(start)) {
            // Skip if this is a restart operation, but make sure we set the maintenanceInterval.
            if (start.hasBody()) {
                setMaintenanceIntervalMicros(TimeUnit.SECONDS.toMicros(maintenanceInterval));
            }
            start.complete();
            return;
        }

        setMaintenanceIntervalMicros(TimeUnit.SECONDS.toMicros(maintenanceInterval));
        super.handleStart(start);
    }

    @Override
    protected void initializeState(VsphereRDCSyncTaskState task, Operation taskOperation) {
        task.subStage = SubStage.CHECK_STATUS_FIELD;
        try {
            this.ccsHost = new URI(System.getProperty(CCS_HOST));
        } catch (URISyntaxException e) {
            logWarning("ccsHost is not a valid URI: %s", System.getProperty(CCS_HOST));
            taskOperation.fail(e);
            return;
        }
        super.initializeState(task, taskOperation);
    }

    @Override
    protected VsphereRDCSyncTaskState validateStartPost(Operation op) {
        VsphereRDCSyncTaskState state = super.validateStartPost(op);
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return null;
        }

        if (System.getProperty(CCS_HOST) == null || System.getProperty(CCS_HOST).isEmpty()) {
            logWarning("ccs-command-channel-server-svc URI not provided, patching vCenter " +
                    "credential will not force the data sync automatically. Resources will not " +
                    "been updated!");
            op.fail(new IllegalArgumentException("ccs-command-channel host is required"));
            return null;
        }

        if (state.endpointLink == null || state.endpointLink.isEmpty()) {
            op.fail(new IllegalStateException("'endpointLink' is required"));
            return null;
        }

        Utils.validateState(getStateDescription(), state);
        return state;
    }

    @Override
    protected boolean validateTransition(Operation patch, VsphereRDCSyncTaskState currentTask,
            VsphereRDCSyncTaskState patchBody) {
        super.validateTransition(patch, currentTask, patchBody);

        if (patchBody.taskInfo.stage == TaskStage.STARTED && patchBody.subStage == null) {
            patch.fail(new IllegalArgumentException("Missing sub-stage"));
            return false;
        }

        return true;
    }

    @Override
    public void handlePatch(Operation patch) {
        try {
            VsphereRDCSyncTaskState patchBody = getBody(patch);
            VsphereRDCSyncTaskState currentState = getState(patch);

            if (!validateTransition(patch, currentState, patchBody)) {
                return;
            }
            boolean hasStateChanged = Utils.mergeWithState(getStateDescription(),
                    currentState, patchBody);
            if (!hasStateChanged) {
                patch.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
            } else {
                patch.setBody(currentState);
            }
            patch.complete();

            switch (currentState.taskInfo.stage) {
            case STARTED:
                handleSubStage(currentState);
                break;
            case FINISHED:
                logFine("Task finished successfully");
                handleTaskCompleted(currentState);
                break;
            case FAILED:
                logWarning("Task failed: %s", (currentState.failureMessage == null ?
                        "No reason given" : currentState.failureMessage));
                handleTaskCompleted(currentState);
                break;
            default:
                logWarning("Unexpected stage: %s", currentState.taskInfo.stage);
                break;
            }
        } catch (Throwable e) {
            patch.fail(e);
        }
    }


    private void handleSubStage(VsphereRDCSyncTaskState task) {
        switch (task.subStage) {
        case CHECK_STATUS_FIELD:
            checkSyncStatus(task, SubStage.SYNC_DATA_COLLECTOR);
            break;
        case SYNC_DATA_COLLECTOR:
            syncVCenterDataCollector(task, SubStage.SUCCESS);
            break;
        case SUCCESS:
            sendSelfFinishedPatch(task);
            break;
        case ERROR:
            sendSelfFailurePatch(task, task.taskInfo.failure.message);
            break;
        default:
            sendSelfFailurePatch(task, "Unknown stage encountered: " + task.subStage);
        }
    }

    @Override
    public void handlePeriodicMaintenance(Operation maintenanceOp) {
        if (getProcessingStage() != ProcessingStage.AVAILABLE) {
            logWarning(() -> String.format("Skipping maintenance since service is not available: %s ",
                    getUri()));
            return;
        }

        sendRequest(Operation.createGet(getUri())
                .setCompletion((getOp, getEx) -> {
                    if (getEx != null) {
                        maintenanceOp.fail(getEx);
                        return;
                    }
                    VsphereRDCSyncTaskState state = getOp.getBody(VsphereRDCSyncTaskState.class);
                    handleSubStage(state);
                    maintenanceOp.complete();
                }));
    }

    private void checkSyncStatus(VsphereRDCSyncTaskState state, SubStage nextStage) {
        Operation op = Operation.createGet(this, state.endpointLink);
        sendRequest(op.setCompletion((operation, throwable) -> {
            if (throwable != null) {
                handleError(state, throwable.getMessage());
                return;
            }

            state.endpointState = operation.getBody(EndpointState.class);

            if (state.endpointState == null || state.endpointState.customProperties == null) {
                handleError(state, "Bad endpointState, please check the endpoint document.");
                return;
            }

            if (SUCCESS.equals(state.endpointState.customProperties.get(STATUS_FIELD))) {
                state.subStage = nextStage;
                sendSelfPatch(state);
            }
            logInfo("The endpointState: %s is not ready to force data sync, will retry in %d " +
                    "seconds", state.endpointLink, getMaintenanceIntervalMicros());
        }));
    }


    private void syncVCenterDataCollector(VsphereRDCSyncTaskState task, SubStage nextStage) {
        ExecuteCommandRequestBuilder executeCommandRequestBuilder = new ExecuteCommandRequestBuilder();

        String dcId = task.endpointState.customProperties.get(DC_ID_KEY);

        ExecuteCommandRequest executeCommandRequest = executeCommandRequestBuilder.withCommand
                (COMMAND_DEFINITION_ID).withTargetProxy(dcId).build();

        try {
            sendWithDeferredResult(Operation
                    .createPost(UriUtils.buildUri(new URI(System.getProperty(CCS_HOST)), CCS_VALIDATE_SERVICE))
                    .setBody(executeCommandRequest)
                    .setContentType(Operation.MEDIA_TYPE_APPLICATION_JSON))
                    .whenComplete((o, e) -> {
                        if (e != null) {
                            handleError(task, e.getMessage());
                            return;
                        }
                        task.subStage = nextStage;
                        sendSelfPatch(task);
                    });
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }


    /**
     * Log the error msg, but not stop the task. Keep periodically checking.
     * @param taskState : VsphereRDCSyncTaskState
     * @param failureMessage : the error message
     */
    private void handleError(VsphereRDCSyncTaskState taskState, String failureMessage) {
        logWarning("Task failed at sub-stage %s with failure: %s", taskState.subStage,
                failureMessage);
    }

    /**
     * Self delete this task if specified through {@link TaskOption#SELF_DELETE_ON_COMPLETION}
     * option.
     */
    private void handleTaskCompleted(VsphereRDCSyncTaskState taskState) {
        if (taskState.options.contains(TaskOption.SELF_DELETE_ON_COMPLETION)) {
            sendWithDeferredResult(Operation.createDelete(getUri()))
                    .whenComplete((o, e) -> {
                        if (e != null) {
                            logSevere(() -> String.format(
                                    "Self-delete upon %s stage: FAILED with %s at subStage: %s",
                                    taskState.taskInfo.stage, Utils.toString(e), taskState.subStage));
                        } else {
                            logInfo(() -> String.format(
                                    "Self-delete upon %s stage: SUCCESS", taskState.taskInfo.stage));
                        }
                    });
        }
    }
}
