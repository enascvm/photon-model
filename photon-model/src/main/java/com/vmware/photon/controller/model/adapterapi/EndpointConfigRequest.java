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

import java.util.Map;

/**
 * Request to validate and/or enhance an Endpoint. The {@link ResourceRequest#resourceReference}
 * field is reference to the Endpoint to enhance.
 * <p>
 * If the value of {@link EndpointConfigRequest#validateOnly} is {@code true} then value of
 * {@link ResourceRequest#resourceReference} won't be set.
 */
public class EndpointConfigRequest extends ResourceRequest {
    public static final String USER_LINK_KEY = "userLink";
    public static final String USER_EMAIL_KEY = "userEmail";
    public static final String PRIVATE_KEY_KEY = "privateKey";
    public static final String PRIVATE_KEYID_KEY = "privateKeyId";
    public static final String PUBLIC_KEY_KEY = "publicKey";
    public static final String TOKEN_REFERENCE_KEY = "tokenReference";
    public static final String REGION_KEY = "regionId";
    public static final String ZONE_KEY = "zoneId";

    /**
     * Endpoint request type.
     */
    public enum RequestType {
        VALIDATE,
        ENHANCE
    }

    /**
     * Request type.
     */
    public RequestType requestType;

    /**
     * A map of value to use to validate and enhance Endpoint.
     */
    public Map<String, String> endpointProperties;
}
