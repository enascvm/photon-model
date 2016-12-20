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

package com.vmware.photon.controller.model.adapterapi;

import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.Utils;

/**
 * Default response, to be used by adapters when reporting back to a given task.
 */
public class ResourceOperationResponse {
    public static final String KIND = Utils.buildKind(ResourceOperationResponse.class);

    public String resourceLink;

    public TaskState taskInfo;

    public String documentKind = KIND;

    public static ResourceOperationResponse finish(String resourceLink) {
        return response(resourceLink, TaskStage.FINISHED);
    }

    public static ResourceOperationResponse cancel(String resourceLink) {
        return response(resourceLink, TaskStage.CANCELLED);
    }

    public static ResourceOperationResponse fail(String resourceLink, Throwable t) {
        return fail(resourceLink, Utils.toServiceErrorResponse(t));
    }

    public static ResourceOperationResponse fail(String resourceLink,
            ServiceErrorResponse error) {
        ResourceOperationResponse r = response(resourceLink, TaskStage.FAILED);
        r.taskInfo.failure = error;
        return r;
    }

    private static ResourceOperationResponse response(String resourceLink, TaskStage stage) {
        ResourceOperationResponse r = new ResourceOperationResponse();
        r.resourceLink = resourceLink;
        r.taskInfo = new TaskState();
        r.taskInfo.stage = stage;
        return r;
    }
}
