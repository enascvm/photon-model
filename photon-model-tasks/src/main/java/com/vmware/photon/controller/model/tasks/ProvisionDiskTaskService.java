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

import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapterapi.DiskInstanceRequest;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.tasks.ProvisionDiskTaskService.ProvisionDiskTaskState;
import com.vmware.photon.controller.model.tasks.ProvisionDiskTaskService.ProvisionDiskTaskState.SubStage;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.TaskService;

public class ProvisionDiskTaskService extends TaskService<ProvisionDiskTaskState> {

    public static final String FACTORY_LINK = UriPaths.PROVISIONING + "/disk-tasks";

    /**
     * Represent state of a provision disk task.
     */
    public static class ProvisionDiskTaskState extends com.vmware.xenon.services.common.TaskService.TaskServiceState {

        public static final long DEFAULT_EXPIRATION_MICROS = TimeUnit.HOURS.toMicros(1);

        /**
         * SubStage.
         */
        public enum SubStage {
            CREATING_DISK, DELETING_DISK, VALIDATE_DISK, VALIDATE_DISK_CLEANUP, DONE, FAILED
        }

        /**
         * URI reference to disk instance.
         */
        public String diskLink;

        /**
         * Task SubStage.
         */
        public ProvisionDiskTaskState.SubStage taskSubStage;

        /**
         * Optional, set by the task if not specified by the client, by querying
         * the disk state.
         */
        public URI diskAdapterReference;

        /**
         * A callback to the initiating task.
         */
        public ServiceTaskCallback<?> serviceTaskCallback;

        /**
         * Value indicating whether the service should treat this as a mock
         * request and complete the work flow without involving the underlying
         * disk infrastructure.
         */
        public boolean isMockRequest;

        /**
         * A list of tenant links which can access this task.
         */
        public List<String> tenantLinks;

    }

    public ProvisionDiskTaskService() {
        super(ProvisionDiskTaskState.class);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handleStart(Operation startPost) {
        try {
            ProvisionDiskTaskState state = startPost
                    .getBody(ProvisionDiskTaskState.class);

            validateState(state);
            validateDiskAndStart(state, startPost);

        } catch (Throwable e) {
            logSevere(e);
            startPost.fail(e);
            failTask(e);
        }
    }

    @Override
    public void handlePatch(Operation patch) {
        ProvisionDiskTaskState patchBody = patch.getBody(ProvisionDiskTaskState.class);
        ProvisionDiskTaskState currentState = getState(patch);

        // this validates and transitions the stage to the next state
        if (validateStageTransition(patch, patchBody, currentState)) {
            return;
        }

        handleStagePatch(patch, currentState);
    }

    private void handleStagePatch(Operation patch,
            ProvisionDiskTaskState currentState) {
        // Complete the self patch eagerly, after we clone state (since state is
        // not "owned"
        // by the service handler once the operation is complete).
        patch.complete();

        switch (currentState.taskInfo.stage) {
        case CREATED:
            break;
        case STARTED:
            processNextSubStage(currentState);
            break;
        case CANCELLED:
            break;
        case FAILED:
            logWarning(() -> String.format("Task failed with %s",
                    Utils.toJsonHtml(currentState.taskInfo.failure)));
            ServiceTaskCallback.sendResponse(currentState.serviceTaskCallback, this, currentState);
            break;
        case FINISHED:
            ServiceTaskCallback.sendResponse(currentState.serviceTaskCallback, this, currentState);
            break;
        default:
            break;
        }
    }

    private void processNextSubStage(ProvisionDiskTaskState updatedState) {
        ProvisionDiskTaskState.SubStage newStage = updatedState.taskSubStage;

        switch (newStage) {
        case CREATING_DISK:
            doSubStageDiskOperation(updatedState, SubStage.VALIDATE_DISK,
                    DiskInstanceRequest.DiskRequestType.CREATE);
            return;
        case DELETING_DISK:
            doSubStageDiskOperation(updatedState, SubStage.VALIDATE_DISK_CLEANUP,
                    DiskInstanceRequest.DiskRequestType.DELETE);
            return;
        case VALIDATE_DISK:
            doSubStageValidateDiskState(updatedState);
            return;
        case VALIDATE_DISK_CLEANUP:
            doSubStageValidateDiskStateCleanup(updatedState);
            return;
        case DONE:
            sendSelfPatch(TaskStage.FINISHED,
                    ProvisionDiskTaskState.SubStage.DONE, null);
            break;
        default:
            break;
        }
    }

    private void doSubStageValidateDiskState(ProvisionDiskTaskState updatedState) {
        sendRequest(Operation
                .createGet(this, updatedState.diskLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning(() -> String.format("GET to %s failed: %s", o.getUri(),
                                Utils.toString(e)));
                        failTask(e);
                        return;
                    }
                    DiskState chs = o.getBody(DiskState.class);
                    try {
                        if (!updatedState.isMockRequest) {
                            String diskId = chs.id;

                            if (diskId == null || diskId.isEmpty()) {
                                failTask(new IllegalArgumentException("Disk Id cannot be empty."));
                                return;
                            }
                        }
                        sendSelfPatch(TaskStage.FINISHED, ProvisionDiskTaskState.SubStage.DONE,
                                null);
                    } catch (Throwable ex) {
                        failTask(ex);
                    }
                }));
    }

    private void doSubStageValidateDiskStateCleanup(ProvisionDiskTaskState updatedState) {
        sendRequest(Operation
                .createGet(this, updatedState.diskLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        // Disk state should have been deleted as part of the delete disk
                        sendSelfPatch(TaskStage.FINISHED, ProvisionDiskTaskState.SubStage.DONE,
                                null);
                        return;
                    }
                    failTask(e);
                }));
    }

    private void doSubStageDiskOperation(ProvisionDiskTaskState updatedState,
            ProvisionDiskTaskState.SubStage nextStage, DiskInstanceRequest.DiskRequestType diskRequestType) {
        Operation.CompletionHandler c = (o, e) -> {
            if (e != null) {
                failTask(e);
                return;
            }

            DiskInstanceRequest cr = new DiskInstanceRequest();
            cr.resourceReference = UriUtils.buildUri(getHost(),
                    updatedState.diskLink);
            cr.requestType = diskRequestType;

            ServiceDocument subTask = o.getBody(ServiceDocument.class);
            cr.taskReference = UriUtils.buildUri(this.getHost(), subTask.documentSelfLink);
            cr.isMockRequest = updatedState.isMockRequest;
            sendHostServiceRequest(cr, updatedState.diskAdapterReference);
        };

        // after setting boot order and rebooting, we want the sub
        // task to patch us, the main task, to the "next" state
        createSubTask(c, nextStage, updatedState);
    }

    private void sendHostServiceRequest(Object body, URI adapterReference) {
        sendRequest(Operation.createPatch(adapterReference).setBody(body)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask(e);
                        return;
                    }
                }));
    }

    public void createSubTask(CompletionHandler c,
            ProvisionDiskTaskState.SubStage nextStage,
            ProvisionDiskTaskState currentState) {

        ServiceTaskCallback<SubStage> callback = ServiceTaskCallback
                .create(UriUtils.buildPublicUri(getHost(), getSelfLink()));
        callback.onSuccessTo(nextStage);

        SubTaskService.SubTaskState<SubStage> subTaskInitState = new SubTaskService.SubTaskState<>();
        subTaskInitState.errorThreshold = 0;
        subTaskInitState.serviceTaskCallback = callback;
        subTaskInitState.tenantLinks = currentState.tenantLinks;
        subTaskInitState.documentExpirationTimeMicros = currentState.documentExpirationTimeMicros;
        Operation startPost = Operation
                .createPost(this, SubTaskService.FACTORY_LINK)
                .setBody(subTaskInitState).setCompletion(c);
        sendRequest(startPost);
    }

    public boolean validateStageTransition(Operation patch,
            ProvisionDiskTaskState patchBody,
            ProvisionDiskTaskState currentState) {

        if (patchBody.taskInfo != null && patchBody.taskInfo.failure != null) {
            logWarning(() -> String.format("Task failed: %s",
                    Utils.toJson(patchBody.taskInfo.failure)));
            currentState.taskInfo.failure = patchBody.taskInfo.failure;
            if (patchBody.taskSubStage == null) {
                patchBody.taskSubStage = currentState.taskSubStage;
            }
        } else {
            if (patchBody.taskInfo == null || patchBody.taskInfo.stage == null) {
                patch.fail(new IllegalArgumentException(
                        "taskInfo and taskInfo.stage are required"));
                return true;
            }

            if (TaskState.isFinished(patchBody.taskInfo)) {
                currentState.taskInfo.stage = patchBody.taskInfo.stage;
                adjustStat(patchBody.taskInfo.stage.toString(), 1);
                currentState.taskSubStage = ProvisionDiskTaskState.SubStage.DONE;
                adjustStat(currentState.taskSubStage.toString(), 1);
                return false;
            }

            // Current state always has a non-null taskSubStage, per
            // validateState.
            if (currentState.taskSubStage == null) {
                patch.fail(new IllegalArgumentException(
                        "taskSubStage is required"));
                return true;
            }

            // Patched state must have a non-null taskSubStage,
            // because we're moving from one stage to the next.
            if (patchBody.taskSubStage == null) {
                patch.fail(new IllegalArgumentException(
                        "taskSubStage is required"));
                return true;
            }

            if (currentState.taskSubStage.ordinal() > patchBody.taskSubStage
                    .ordinal()) {
                logWarning(() -> "Attempt to move progress backwards, not allowed");
                patch.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED)
                        .complete();
                return true;
            }
        }

        logFine(() -> String.format("Current: %s(%s). New: %s(%s)", currentState.taskInfo.stage,
                currentState.taskSubStage, patchBody.taskInfo.stage,
                patchBody.taskSubStage));

        // update current stage to new stage
        currentState.taskInfo.stage = patchBody.taskInfo.stage;
        adjustStat(patchBody.taskInfo.stage.toString(), 1);

        // update sub stage
        currentState.taskSubStage = patchBody.taskSubStage;
        adjustStat(currentState.taskSubStage.toString(), 1);

        return false;
    }

    private void validateDiskAndStart(ProvisionDiskTaskState state, Operation startPost) {
        URI diskUri = UriUtils.buildUri(getHost(), state.diskLink);
        sendRequest(Operation.createGet(diskUri)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning(() ->
                                String.format("Failure retrieving disk state (%s): %s", diskUri,
                                        e.toString()));
                        o.complete();
                        failTask(e);
                        return;
                    }
                    DiskState disk = o.getBody(DiskState.class);
                    state.diskAdapterReference = disk.diskAdapterReference;

                    startPost.complete();

                    if (disk.capacityMBytes < 0) {
                        failTask(new IllegalArgumentException(
                                "disk capacity is mandatory for a disk"));
                        return;
                    }

                    if (state.taskSubStage == ProvisionDiskTaskState.SubStage.CREATING_DISK
                            && state.diskAdapterReference == null) {
                        failTask(new IllegalArgumentException(
                                "diskState does not have create service specified"));
                        return;
                    }
                    sendSelfPatch(TaskStage.STARTED, state.taskSubStage, null);
                }));
    }

    private void failTask(Throwable e) {
        logWarning(() -> String.format("Self patching to FAILED, task failure: %s", e.toString()));
        sendSelfPatch(TaskState.TaskStage.FAILED, ProvisionDiskTaskState.SubStage.FAILED, e);
    }

    private void sendSelfPatch(TaskStage newStage,
            ProvisionDiskTaskState.SubStage newSubStage, Throwable ex) {
        ProvisionDiskTaskState patchBody = new ProvisionDiskTaskState();
        patchBody.taskInfo = new TaskState();
        patchBody.taskInfo.stage = newStage;
        patchBody.taskSubStage = newSubStage;
        if (ex != null) {
            patchBody.taskInfo.failure = Utils.toServiceErrorResponse(ex);
        }
        Operation patch = Operation
                .createPatch(getUri())
                .setBody(patchBody)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                logWarning(() -> String.format("Self patch failed: %s",
                                        com.vmware.xenon.common.Utils.toString(e)));
                            }
                        });
        sendRequest(patch);
    }

    public void validateState(ProvisionDiskTaskState state) {
        if (state.diskLink == null) {
            throw new IllegalArgumentException("diskReference is required");
        }

        state.taskInfo = new TaskState();
        state.taskInfo.stage = TaskStage.CREATED;

        if (state.taskSubStage == null) {
            throw new IllegalArgumentException("taskSubStage is required");
        }

        if (state.documentExpirationTimeMicros == 0) {
            state.documentExpirationTimeMicros = Utils.getNowMicrosUtc()
                    + ProvisionDiskTaskState.DEFAULT_EXPIRATION_MICROS;
        }
    }
}
