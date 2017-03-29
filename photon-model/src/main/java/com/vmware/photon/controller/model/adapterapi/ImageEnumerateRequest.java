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

/**
 * Request to enumerate images per end-point. The {@code resourceReference} value is the URI to the
 * end-point.
 */
public class ImageEnumerateRequest extends ResourceRequest {

    /**
     * Image enumeration request type.
     */
    public enum ImageEnumerateRequestType {
        /**
         * Instruct the adapter to enumerate images that are public/global for all end-points of
         * passed end-point type.
         */
        PUBLIC,
        /**
         * Instruct the adapter to enumerate images that are private/specific for passed end-point.
         */
        PRIVATE
    }

    /**
     * Image enumeration request type.
     */
    public ImageEnumerateRequestType requestType;

    /**
     * Image enumeration action type.
     */
    public EnumerationAction enumerationAction;

}
