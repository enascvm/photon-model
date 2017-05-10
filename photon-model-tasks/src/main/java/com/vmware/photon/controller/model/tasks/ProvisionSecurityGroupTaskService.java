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

package com.vmware.photon.controller.model.tasks;

import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.EXPAND;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.FIXED_ITEM_NAME;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.OPTIONAL;

import java.util.List;
import java.util.Map;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapterapi.SecurityGroupInstanceRequest;
import com.vmware.photon.controller.model.adapterapi.SecurityGroupInstanceRequest.InstanceRequestType;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.tasks.ProvisionSecurityGroupTaskService.ProvisionSecurityGroupTaskState;
import com.vmware.photon.controller.model.tasks.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.TaskService;

/**
 * Provision security group task service.
 */
public class ProvisionSecurityGroupTaskService extends TaskService<ProvisionSecurityGroupTaskState> {
    public static final String FACTORY_LINK = UriPaths.PROVISIONING + "/security-group-tasks";

    public static final String NETWORK_STATE_ID_PROP_NAME = "__networkStateId";

    /**
     * Substages of the tasks.
     */
    public enum SubStage {
        CREATED, PROVISIONING_SECURITY_GROUP, FINISHED, FAILED
    }

    /**
     * Represents state of a security group task.
     */
    public static class ProvisionSecurityGroupTaskState extends TaskService.TaskServiceState {
        public InstanceRequestType requestType;

        /**
         * The description of the security group instance being realized.
         */
        public String securityGroupDescriptionLink;

        /**
         * Tracks the sub stage (creating network or security group). Set by the
         * run-time.
         */
        public SubStage taskSubStage;

        /**
         * For testing. If set, the request will not actuate any computes
         * directly but will patch back success.
         */
        public boolean isMockRequest = false;

        /**
         * A list of tenant links which can access this task.
         */
        public List<String> tenantLinks;

        /**
         * Custom properties associated with the task
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @PropertyOptions(usage = { OPTIONAL }, indexing = { EXPAND, FIXED_ITEM_NAME })
        public Map<String, String> customProperties;

        /**
         * A callback to the initiating task.
         */
        public ServiceTaskCallback<?> serviceTaskCallback;

        public void validate() throws Exception {
            if (this.requestType == null) {
                throw new IllegalArgumentException("requestType required");
            }

            if (this.securityGroupDescriptionLink == null
                    || this.securityGroupDescriptionLink.isEmpty()) {
                throw new IllegalArgumentException(
                        "securityGroupDescriptionLink required");
            }
        }
    }

    public ProvisionSecurityGroupTaskService() {
        super(ProvisionSecurityGroupTaskState.class);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handleStart(Operation start) {
        if (!start.hasBody()) {
            start.fail(new IllegalArgumentException("body is required"));
            return;
        }

        ProvisionSecurityGroupTaskState state = start
                .getBody(ProvisionSecurityGroupTaskState.class);
        try {
            state.validate();
        } catch (Exception e) {
            start.fail(e);
            return;
        }
        state.taskInfo = new TaskState();
        state.taskInfo.stage = TaskState.TaskStage.CREATED;
        state.taskSubStage = SubStage.CREATED;
        start.complete();

        // start the task
        sendSelfPatch(TaskState.TaskStage.CREATED, null);
    }

    @Override
    public void handlePatch(Operation patch) {
        if (!patch.hasBody()) {
            patch.fail(new IllegalArgumentException("body is required"));
            return;
        }

        ProvisionSecurityGroupTaskState currState = getState(patch);
        ProvisionSecurityGroupTaskState patchState = patch
                .getBody(ProvisionSecurityGroupTaskState.class);

        if (TaskState.isFailed(patchState.taskInfo)) {
            currState.taskInfo = patchState.taskInfo;
        }

        switch (patchState.taskInfo.stage) {
        case CREATED:
            currState.taskSubStage = nextStage(currState);

            handleSubStages(currState);
            logInfo(() -> String.format("%s %s on %s started", "Security Group",
                    currState.requestType.toString(), currState.securityGroupDescriptionLink));
            break;

        case STARTED:
            currState.taskInfo.stage = TaskState.TaskStage.STARTED;
            break;
        case FINISHED:
            SubStage nextStage = nextStage(currState);
            if (nextStage == SubStage.FINISHED) {
                currState.taskInfo.stage = TaskState.TaskStage.FINISHED;
                logInfo(() -> "Task is complete");
                notifyParentTask(currState);
            } else {
                sendSelfPatch(TaskState.TaskStage.CREATED, null);
            }
            break;
        case FAILED:
            logWarning(() -> String.format("Task failed with %s",
                    Utils.toJsonHtml(currState.taskInfo.failure)));
            notifyParentTask(currState);
            break;
        case CANCELLED:
            break;
        default:
            break;
        }

        patch.complete();
    }

    private SubStage nextStage(ProvisionSecurityGroupTaskState state) {
        return state.requestType == InstanceRequestType.CREATE ? nextSubStageOnCreate(state.taskSubStage)
                : nextSubstageOnDelete(state.taskSubStage);
    }

    private SubStage nextSubStageOnCreate(SubStage currStage) {
        return SubStage.values()[currStage.ordinal() + 1];
    }

    // deletes follow the inverse order;
    private SubStage nextSubstageOnDelete(SubStage currStage) {
        if (currStage == SubStage.CREATED) {
            return SubStage.PROVISIONING_SECURITY_GROUP;
        } else if (currStage == SubStage.PROVISIONING_SECURITY_GROUP) {
            return SubStage.FINISHED;
        } else {
            return SubStage.values()[currStage.ordinal() + 1];
        }
    }

    private void handleSubStages(ProvisionSecurityGroupTaskState currState) {
        switch (currState.taskSubStage) {
        case PROVISIONING_SECURITY_GROUP:
            patchAdapter(currState);
            break;
        case FINISHED:
            sendSelfPatch(TaskState.TaskStage.FINISHED, null);
            break;
        case FAILED:
            break;
        default:
            break;
        }
    }

    private SecurityGroupInstanceRequest toReq(SecurityGroupState securityGroupState,
            ProvisionSecurityGroupTaskState taskState) {
        SecurityGroupInstanceRequest req = new SecurityGroupInstanceRequest();
        req.requestType = taskState.requestType;
        req.resourceReference = UriUtils.buildUri(this.getHost(),
                taskState.securityGroupDescriptionLink);
        req.authCredentialsLink = securityGroupState.authCredentialsLink;
        req.resourcePoolLink = securityGroupState.resourcePoolLink;
        req.taskReference = this.getUri();
        req.isMockRequest = taskState.isMockRequest;
        req.customProperties = taskState.customProperties;

        return req;
    }

    private void patchAdapter(ProvisionSecurityGroupTaskState taskState) {

        sendRequest(Operation
                .createGet(
                        UriUtils.buildUri(this.getHost(),
                                taskState.securityGroupDescriptionLink))
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                sendSelfPatch(TaskState.TaskStage.FAILED, e);
                                return;
                            }
                            SecurityGroupState securityGroupState = o
                                    .getBody(SecurityGroupState.class);
                            SecurityGroupInstanceRequest req = toReq(securityGroupState,
                                    taskState);

                            sendRequest(Operation
                                    .createPatch(
                                            securityGroupState.instanceAdapterReference)
                                    .setBody(req)
                                    .setCompletion(
                                            (oo, ee) -> {
                                                if (ee != null) {
                                                    sendSelfPatch(
                                                            TaskState.TaskStage.FAILED,
                                                            ee);
                                                }
                                            }));
                        }));
    }

    private void sendSelfPatch(TaskState.TaskStage stage, Throwable e) {
        ProvisionSecurityGroupTaskState body = new ProvisionSecurityGroupTaskState();
        body.taskInfo = new TaskState();
        if (e == null) {
            body.taskInfo.stage = stage;
        } else {
            body.taskInfo.stage = TaskState.TaskStage.FAILED;
            body.taskInfo.failure = Utils.toServiceErrorResponse(e);
            logWarning(() -> String.format("Patching to failed: %s", Utils.toString(e)));
        }

        sendSelfPatch(body);
    }

    private void notifyParentTask(ProvisionSecurityGroupTaskState currentState) {
        if (currentState.serviceTaskCallback == null) {
            return;
        }

        ServiceTaskCallbackResponse<?> parentPatchBody;
        if (currentState.taskInfo.stage == TaskState.TaskStage.FAILED) {
            parentPatchBody = currentState.serviceTaskCallback
                    .getFailedResponse(currentState.taskInfo.failure);
        } else {
            parentPatchBody = currentState.serviceTaskCallback.getFinishedResponse();
        }

        sendRequest(Operation.createPatch(currentState.serviceTaskCallback.serviceURI)
                .setBody(parentPatchBody));
    }
}
