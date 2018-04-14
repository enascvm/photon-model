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

package com.vmware.photon.controller.discovery.common.utils;

import static com.vmware.xenon.common.Operation.STATUS_CODE_INTERNAL_ERROR;

import com.vmware.photon.controller.discovery.common.ErrorCode;
import com.vmware.photon.controller.discovery.common.PhotonControllerErrorCode;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceErrorResponse;

/**
 * Custom error response for symphony.
 * </p>
 * <ul>
 *  <li>Error codes are five digit integers.</li>
 *  <li>Error codes do not start with a 0 as the first digit.</li>
 *  <li>The first two digits of the error code will internally identify the domain/component/feature that is producing errors.</li>
 *  <li>The remaining three digits will identify specific errors within the domain/component/feature.</li>
 * </ul>
 */
public class ErrorUtil {
    private static final String DEFAULT_ERROR_MSG = "Consult your administrator to go through server logs for more information about this error.";

    /**
     * Creates error response for given error code.
     */
    public static ServiceErrorResponse create(ErrorCode errorCode) {
        ServiceErrorResponse errorResponse = new ServiceErrorResponse();
        errorResponse.messageId = String.valueOf(errorCode.getErrorCode());
        errorResponse.message = message(errorCode);
        return errorResponse;
    }

    /**
     * Creates error response for given error code.
     */
    public static ServiceErrorResponse create(ErrorCode errorCode, String... args) {
        ServiceErrorResponse errorResponse = new ServiceErrorResponse();
        errorResponse.messageId = String.valueOf(errorCode.getErrorCode());
        errorResponse.message = message(errorCode, args);
        return errorResponse;
    }

    public static ServiceErrorResponse create(int statusCode, String msgFormat, Object... args) {
        ServiceErrorResponse rsp = new ServiceErrorResponse();
        rsp.message = String.format(msgFormat, args);
        rsp.statusCode = statusCode;
        return rsp;
    }

    /**
     * Returns formatted message string for given {@link ErrorCode}.
     */
    public static String message(ErrorCode errorCode) {
        String format = "%d: %s";
        String errMsg = errorCode.getMessage() == null ? DEFAULT_ERROR_MSG : errorCode.getMessage();
        return String.format(format, errorCode.getErrorCode(), errMsg);
    }

    /**
     * Returns formatted message string with args for given {@link ErrorCode}.
     */
    public static String message(ErrorCode errorCode, String... args) {
        String format = "%d: %s";
        String errMsg = errorCode.getMessage() == null ? DEFAULT_ERROR_MSG : errorCode.getMessage();
        String formattedMsg = String.format(format, errorCode.getErrorCode(), errMsg);
        if (args != null && args.length > 0) {
            return String.format(formattedMsg, args);
        }
        return formattedMsg;
    }

    /**
     * Fails the operation with generic 500 error.
     */
    public static final void handleGenericError(Operation op) {
        op.setBody(ErrorUtil.create(PhotonControllerErrorCode.GENERIC_ERROR));
        op.fail(STATUS_CODE_INTERNAL_ERROR);
    }
}