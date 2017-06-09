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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.vmware.photon.controller.model.adapterapi.ResourceOperationResponse;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.TaskService.TaskServiceState;

/**
 * Service Task Callback Factory to provide an instance of {@link ServiceTaskCallbackResponse}
 * callback Task service response. Used to abstract the calling Task service from the next Task
 * service as well as enable multiple parent Task services to use the same child Task.
 */
public class ServiceTaskCallback<E extends Enum<E>> {

    private TaskStage stageComplete;
    private Object subStageComplete;

    private TaskStage stageFailed;
    private Object subStageFailed;

    private Map<String, String> customProperties;

    public URI serviceURI;

    protected ServiceTaskCallback() {
        // GSON serialization constructor
    }

    private ServiceTaskCallback(URI serviceURI) {
        this.serviceURI = serviceURI;
        this.stageComplete = TaskStage.STARTED;
        this.stageFailed = TaskStage.FAILED;
    }

    public static <E extends Enum<E>> ServiceTaskCallback<E> create(URI serviceURI) {
        return new ServiceTaskCallback<>(serviceURI);
    }

    public ServiceTaskCallback<E> onSuccessFinishTask() {
        this.stageComplete = TaskStage.FINISHED;
        return this;
    }

    public ServiceTaskCallback<E> onErrorFailTask() {
        this.stageFailed = TaskStage.FAILED;
        return this;
    }

    public ServiceTaskCallback<E> onSuccessTo(E subStage) {
        // sub-stages are only honored in the STARTED primary stage
        this.stageComplete = TaskStage.STARTED;
        this.subStageComplete = subStage;
        return this;
    }

    public ServiceTaskCallback<E> onErrorTo(E subStage) {
        // sub-stages are only honored in the STARTED primary stage
        this.stageFailed = TaskStage.STARTED;
        this.subStageFailed = subStage;
        return this;
    }

    public ServiceTaskCallback<E> addProperty(String propName, String propValue) {
        if (this.customProperties == null) {
            this.customProperties = new HashMap<>(2);
        }
        this.customProperties.put(propName, propValue);
        return this;
    }

    public ServiceTaskCallbackResponse<E> getFinishedResponse() {
        return new ServiceTaskCallbackResponse<E>(this.stageComplete, this.subStageComplete,
                this.customProperties, null);
    }

    public ServiceTaskCallbackResponse<E> getFailedResponse(Throwable e) {
        return getFailedResponse(Utils.toServiceErrorResponse(e));
    }

    public ServiceTaskCallbackResponse<E> getFailedResponse(ServiceErrorResponse failure) {
        return new ServiceTaskCallbackResponse<E>(this.stageFailed, this.subStageFailed,
                this.customProperties,
                failure);
    }

    public void sendResponse(Service sender, Throwable e) {
        sendResponse(sender, e != null ? getFailedResponse(e) : getFinishedResponse());
    }

    public void sendResponse(Service sender, TaskServiceState taskState) {
        sendResponse(sender, taskState.taskInfo.stage == TaskState.TaskStage.FAILED
                ? getFailedResponse(taskState.taskInfo.failure) : getFinishedResponse());
    }

    public void sendResponse(Service sender, ServiceTaskCallbackResponse<E> response) {
        sender.sendRequest(Operation.createPatch(this.serviceURI).setBody(response));
    }

    public static void sendResponse(ServiceTaskCallback<?> callback, Service sender, Throwable e) {
        if (callback != null) {
            callback.sendResponse(sender, e);
        }
    }

    public static void sendResponse(ServiceTaskCallback<?> callback, Service sender,
            TaskServiceState taskState) {
        if (callback != null) {
            callback.sendResponse(sender, taskState);
        }
    }

    public static <E extends Enum<E>> void sendResponse(ServiceTaskCallback<E> callback,
            Service sender, ServiceTaskCallbackResponse<E> response) {
        if (callback != null) {
            callback.sendResponse(sender, response);
        }
    }

    /**
     * Service Task Response Callback patch body definition.
     */
    public static class ServiceTaskCallbackResponse<E extends Enum<E>> {
        public static final String KIND = Utils.buildKind(ServiceTaskCallbackResponse.class);

        public TaskState taskInfo;
        public Object taskSubStage;
        public Map<String, String> customProperties;
        public Set<ResourceOperationResponse> failures;
        public Set<ResourceOperationResponse> completed;
        public final String documentKind = KIND;

        protected ServiceTaskCallbackResponse() {
            // GSON serialization constructor
        }

        public ServiceTaskCallbackResponse(TaskStage taskStage, Object taskSubStage,
                Map<String, String> customProperties,
                ServiceErrorResponse failure) {
            this.taskInfo = new TaskState();
            this.taskInfo.stage = taskStage;
            this.taskInfo.failure = failure;
            this.taskSubStage = taskSubStage;
            this.customProperties = customProperties;
        }

        public ServiceTaskCallbackResponse<E> addProperty(String propName, String propValue) {
            if (this.customProperties == null) {
                this.customProperties = new HashMap<>(2);
            }
            this.customProperties.put(propName, propValue);
            return this;
        }

        public String getProperty(String propName) {
            if (this.customProperties == null) {
                return null;
            }
            return this.customProperties.get(propName);
        }
    }
}
