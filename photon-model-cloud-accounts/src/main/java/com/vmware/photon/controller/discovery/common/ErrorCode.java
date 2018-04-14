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

package com.vmware.photon.controller.discovery.common;

/**
 * Interface for service service error codes. Service owners can implement {@link ErrorCode}
 * and provide service specific error codes and messages.
 */
public interface ErrorCode {

    /**
     * Returns error code.
     * <p>
     * The specific error code to be used as a reference for specific exception reason. It is
     * something that conveys information to a particular problem domain.
     */
    int getErrorCode();

    /**
     * Returns the message.
     */
    String getMessage();
}
