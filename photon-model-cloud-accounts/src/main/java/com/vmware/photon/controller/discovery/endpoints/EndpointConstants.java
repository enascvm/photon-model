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

package com.vmware.photon.controller.discovery.endpoints;

import com.vmware.photon.controller.model.UriPaths;

public class EndpointConstants {

    // Constant for empty string
    public static final String EMPTY_STRING = "";

    public static final String SEPARATOR = "-";

    /**
     * Authz artifact selfLink related constants.
     */
    public static final String RESOURCES = "resources";
    public static final String ENDPOINTS = "endpoints";
    public static final String OWNERS = "owners";
    public static final String RESOURCES_ENDPOINTS_AUTHZ_ARTIFACT_PREFIX = RESOURCES + SEPARATOR
            + ENDPOINTS;
    public static final String RESOURCES_ENDPOINTS_AUTHZ_ARTIFACT_OWNERS_PREFIX =
            RESOURCES_ENDPOINTS_AUTHZ_ARTIFACT_PREFIX + SEPARATOR + OWNERS;

    /**
     * Property to disable discovery from setting endpoint adapter reference
     */
    public static final String PROPERTY_NAME_DISABLE_ENDPOINT_ADAPTER = UriPaths.SYMPHONY_PROPERTY_PREFIX +
            "disableEndpointAdapterUri";
    public static final boolean DISABLE_ENDPOINT_ADAPTER_URI =
            Boolean.getBoolean(System.getProperty(PROPERTY_NAME_DISABLE_ENDPOINT_ADAPTER));
}
