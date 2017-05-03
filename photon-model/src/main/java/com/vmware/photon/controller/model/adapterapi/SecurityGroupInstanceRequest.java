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
 * Request to create/destroy a security group instance on a given compute.
 */
public class SecurityGroupInstanceRequest extends ResourceRequest {

    /**
     * Instance Request type.
     */
    public enum InstanceRequestType {
        CREATE, DELETE
    }

    /**
     * Destroy or create a security group instance on the given compute.
     */
    public InstanceRequestType requestType;

    /**
     * Link to secrets.
     */
    public String authCredentialsLink;

    /**
     * The resource pool the security group exists in.
     */
    public String resourcePoolLink;

    /**
     * Custom properties related to the security group instance.
     */
    public Map<String, String> customProperties;
}
