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

package com.vmware.photon.controller.discovery.onboarding;

import com.vmware.photon.controller.discovery.common.ErrorCode;

/**
 * Defines common error codes and associated messages for errors raised by the symphony
 * onboarding services.
 */
public enum OnboardingErrorCode implements ErrorCode {
    DUPLICATE_PROJECT_NAME(40001, "Project already exists."),
    ORG_NAME_NULL(40002, "'organizationName' is required"),
    PROJECT_CREATION_TASK_FAILURE(40225, "Error creating auth services for project.");

    private int errorCode;
    private String message;

    OnboardingErrorCode(int errorCode, String message) {
        this.errorCode = errorCode;
        this.message = message;
    }

    @Override
    public int getErrorCode() {
        return this.errorCode;
    }

    @Override
    public String getMessage() {
        return this.message;
    }
}
