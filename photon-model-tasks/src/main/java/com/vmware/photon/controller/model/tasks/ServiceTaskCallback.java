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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.vmware.photon.controller.model.adapterapi.ResourceOperationResponse;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.Utils;

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

    public String serviceSelfLink;

    protected ServiceTaskCallback() {
        // GSON serialization constructor
    }

    private ServiceTaskCallback(String serviceSelfLink) {
        this.serviceSelfLink = serviceSelfLink;
        this.stageComplete = TaskStage.STARTED;
        this.stageFailed = TaskStage.FAILED;
    }

    public static <E extends Enum<E>> ServiceTaskCallback<E> create(String serviceSelfLink) {
        return new ServiceTaskCallback<>(serviceSelfLink);
    }

    public ServiceTaskCallback<E> onSuccessFinishTask() {
        this.stageComplete = TaskStage.FINISHED;
        return this;
    }

    public ServiceTaskCallback<E> onErrorFailTask() {
        this.stageComplete = TaskStage.FAILED;
        return this;
    }

    public ServiceTaskCallback<E> onSuccessTo(E subStage) {
        this.subStageComplete = subStage;
        return this;
    }

    public ServiceTaskCallback<E> onErrorTo(E subStage) {
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
