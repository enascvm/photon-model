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

package com.vmware.photon.controller.discovery.endpoints;

import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_LINK_REQUIRED;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.failOperation;

import java.util.EnumSet;
import java.util.List;

import com.vmware.photon.controller.discovery.common.TaskHelper;
import com.vmware.photon.controller.discovery.common.services.UserService;
import com.vmware.photon.controller.discovery.common.utils.OnboardingUtils;
import com.vmware.photon.controller.discovery.endpoints.EndpointDeletionTaskService.EndpointDeletionTaskState;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.tasks.EndpointRemovalTaskService;
import com.vmware.photon.controller.model.tasks.EndpointRemovalTaskService.EndpointRemovalTaskState;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskFactoryService;
import com.vmware.xenon.services.common.TaskService;

/**
 * Task to delete endpoints.
 */
public class EndpointDeletionTaskService extends TaskService<EndpointDeletionTaskState> {
    public static final String FACTORY_LINK = UriPaths.ENDPOINT_DELETION_TASK_SERVICE;

    /**
     * The endpoint deletion task state.
     */
    public static class EndpointDeletionTaskState extends TaskService.TaskServiceState {
        @Documentation(description = "The endpoint to be deleted")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String endpointLink;

        @Documentation(description = "Indicates a mock request")
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public boolean isMock = false;

        @Documentation(description = "This is only for internal usage. It keeps track of internal stages.")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public SubStage subStage;

        @Documentation(description = "The tenant links.")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public List<String> tenantLinks;
    }

    /**
     * Endpoint deletion sub-stages.
     */
    public enum SubStage {
        /**
         * Delete endpoint.
         */
        DELETE_ENDPOINT,

        /**
         * Successful deletion of endpoint.
         */
        SUCCESS,

        /**
         * Error while deleting endpoint.
         */
        ERROR
    }

    public static FactoryService createFactory() {
        TaskFactoryService fs = new TaskFactoryService(EndpointDeletionTaskState.class) {
            @Override
            public Service createServiceInstance() throws Throwable {
                return new EndpointDeletionTaskService();
            }
        };
        fs.setPeerNodeSelectorPath(ServiceUriPaths.DEFAULT_1X_NODE_SELECTOR);
        return fs;
    }

    public EndpointDeletionTaskService() {
        super(EndpointDeletionTaskState.class);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handleStart(Operation taskOperation) {
        final EndpointDeletionTaskState state;
        if (taskOperation.hasBody()) {
            state = taskOperation.getBody(EndpointDeletionTaskState.class);
        } else {
            state = new EndpointDeletionTaskState();
            taskOperation.setBody(state);
        }

        if (state.tenantLinks != null && !state.tenantLinks.isEmpty()) {
            super.handleStart(taskOperation);
            return;
        }

        OnboardingUtils.getProjectLinks(this, (projectLinks, f) -> {
            try {
                if (f != null) {
                    throw f;
                }
                state.tenantLinks = projectLinks;
                taskOperation.setBody(state);
                super.handleStart(taskOperation);
            } catch (Throwable t) {
                logSevere("Failed during creation: %s", Utils.toString(t));
                taskOperation.fail(t);
            }
        });
    }

    @Override
    protected void initializeState(EndpointDeletionTaskState task, Operation taskOperation) {
        task.subStage = SubStage.DELETE_ENDPOINT;
        super.initializeState(task, taskOperation);
    }

    @Override
    protected EndpointDeletionTaskState validateStartPost(Operation op) {
        EndpointDeletionTaskState task = super.validateStartPost(op);
        if (task == null) {
            return null;
        }

        if (TaskState.isCancelled(task.taskInfo)
                || TaskState.isFailed(task.taskInfo)
                || TaskState.isFinished(task.taskInfo)) {
            return null;
        }

        if (!ServiceHost.isServiceCreate(op)) {
            return task;
        }

        if (task.endpointLink == null || task.endpointLink.isEmpty()) {
            failOperation(this.getHost(), op, ENDPOINT_LINK_REQUIRED,
                    Operation.STATUS_CODE_BAD_REQUEST);
            return null;
        }
        return task;
    }

    @Override
    protected boolean validateTransition(Operation patch, EndpointDeletionTaskState currentTask,
            EndpointDeletionTaskState patchBody) {
        super.validateTransition(patch, currentTask, patchBody);
        if (patchBody.taskInfo.stage == TaskStage.STARTED && patchBody.subStage == null) {
            patch.fail(new IllegalArgumentException("Missing sub-stage"));
            return false;
        }

        if (!TaskHelper.validateTransition(currentTask.subStage, patchBody.subStage)) {
            patch.fail(new IllegalArgumentException(
                    String.format("Task subStage cannot be moved from '%s' to '%s'",
                            currentTask.subStage, patchBody.subStage)));
            return false;
        }

        return true;
    }

    @Override
    public void handlePatch(Operation patch) {
        EndpointDeletionTaskState currentTask = getState(patch);
        EndpointDeletionTaskState patchBody = getBody(patch);

        if (!validateTransition(patch, currentTask, patchBody)) {
            return;
        }
        updateState(currentTask, patchBody);
        patch.complete();

        switch (currentTask.taskInfo.stage) {
        case STARTED:
            handleSubStage(currentTask);
            break;
        case FINISHED:
            logFine("Task finished successfully");
            break;
        case FAILED:
            logWarning("Task failed: %s", (currentTask.failureMessage == null ?
                    "No reason given" :
                    currentTask.failureMessage));
            break;
        default:
            logWarning("Unexpected stage: %s", currentTask.taskInfo.stage);
            break;
        }
    }

    /**
     * State machine for endpoint creation.
     */
    private void handleSubStage(EndpointDeletionTaskState task) {
        switch (task.subStage) {
        case DELETE_ENDPOINT:
            deleteEndpoint(task, SubStage.SUCCESS);
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

    /**
     * Deletes the endpoint.
     */
    private void deleteEndpoint(EndpointDeletionTaskState task, SubStage nextStage) {

        EndpointRemovalTaskState endpointTaskState = new EndpointRemovalTaskState();
        endpointTaskState.endpointLink = task.endpointLink;
        if (task.isMock) {
            endpointTaskState.options = EnumSet.of(TaskOption.IS_MOCK);
        }

        endpointTaskState.tenantLinks = task.tenantLinks;
        endpointTaskState.taskInfo = TaskState.createDirect();

        sendWithDeferredResult(Operation
                .createPost(this, EndpointRemovalTaskService.FACTORY_LINK)
                .setBody(endpointTaskState))
                .whenComplete((o, e) -> {
                    if (e != null) {
                        handleError(task, e.getMessage(), o.getStatusCode());
                        return;
                    }

                    EndpointRemovalTaskState body = o.getBody(EndpointRemovalTaskState.class);
                    if (body.taskInfo != null && body.taskInfo.stage == TaskStage.FAILED) {
                        if (body.taskInfo.failure != null) {
                            handleError(task, body.taskInfo.failure.message,
                                    body.taskInfo.failure.statusCode);
                            return;
                        }
                        handleError(task, task.failureMessage,
                                Operation.STATUS_CODE_INTERNAL_ERROR);
                        return;
                    }

                    String userId = null;
                    String userLink = o.getAuthorizationContext().getClaims().getSubject();
                    if (userLink != null && userLink.startsWith(UserService.FACTORY_LINK)) {
                        userId = UriUtils.getLastPathSegment(userLink);
                    }
                    // TODO: postTelemetry(this, body, userId, TelemetryConstants.TelemetryCloudActionState.DELETED);
                    task.subStage = nextStage;
                    sendSelfPatch(task);
                });
    }

    private void handleError(EndpointDeletionTaskState taskState, String failureMessage,
            int statusCode) {
        logWarning("Task failed at sub-stage %s with failure: %s", taskState.subStage,
                failureMessage);

        taskState.subStage = SubStage.ERROR;
        ServiceErrorResponse e = new ServiceErrorResponse();
        e.message = failureMessage;
        e.statusCode = statusCode;
        taskState.taskInfo.failure = e;
        sendSelfPatch(taskState);
    }
}
