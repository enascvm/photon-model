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

package com.vmware.photon.controller.model.adapters.util;

import java.net.URI;
import java.util.logging.Level;

import com.vmware.photon.controller.model.adapterapi.ResourceOperationResponse;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.Utils;

/**
 * Manage tasks lifecycle with this. Also a CompletionHandler so it
 * can be passed to {@link Operation#setCompletion(java.util.function.Consumer, CompletionHandler)}
 */
public class TaskManager implements CompletionHandler {
    private final Service service;
    private final URI taskReference;
    private String resourceLink;

    public TaskManager(Service service, URI taskReference, String resourceLink) {
        this.service = service;
        this.taskReference = taskReference;
        this.resourceLink = resourceLink;
    }

    public void patchTask(TaskStage stage) {
        if (this.taskReference == null) {
            log(Level.WARNING,
                    "Skipping task patching in stage %s as taskReference URI is not provided",
                    stage);
            return;
        }
        log(Level.INFO, "Patching task %s to stage: %s", this.taskReference, stage);
        Operation op = createTaskPatch(stage);
        op.sendWith(this.service);
    }

    @Override
    public void handle(Operation completedOp, Throwable failure) {
        if (failure == null) {
            throw new IllegalStateException("TaskManager can only be used as error handler");
        }
        this.patchTaskToFailure(failure);
    }

    public void finishTask() {
        patchTask(TaskStage.FINISHED);
    }

    public Operation createTaskPatch(TaskStage stage) {
        ResourceOperationResponse body = ResourceOperationResponse.finish(this.resourceLink);
        body.taskInfo.stage = stage;
        return Operation.createPatch(this.taskReference).setBody(body);
    }

    public void patchTaskToFailure(Throwable failure) {
        patchTaskToFailure(failure != null ? failure.getMessage() : null, failure);
    }

    public void patchTaskToFailure(String msg, Throwable failure) {
        if (this.taskReference == null) {
            log(Level.WARNING, "Skipping task patching as taskReference URI is not provided: %s",
                    failure.getMessage());
            return;
        }
        log(Level.WARNING, "Patching task %s to failure: %s %s", this.taskReference,
                failure.getMessage(), Utils.toString(failure));
        createFailurePatch(msg, failure).sendWith(this.service);
    }

    private Operation createFailurePatch(String msg, Throwable failure) {
        ResourceOperationResponse body = ResourceOperationResponse.fail(this.resourceLink, failure);
        body.failureMessage = failure.getClass().getName() + ": " + msg;

        return Operation
                .createPatch(this.taskReference)
                .setBody(body);
    }

    private void log(Level level, String fmt, Object... args) {
        Utils.log(TaskManager.class, TaskManager.class.getSimpleName(), level, fmt, args);
    }
}
