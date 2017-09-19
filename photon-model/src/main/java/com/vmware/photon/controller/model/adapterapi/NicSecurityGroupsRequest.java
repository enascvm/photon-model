/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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

import java.util.List;
import java.util.Map;

public class NicSecurityGroupsRequest extends ResourceRequest {

    /**
     * Instance Request type.
     */
    public enum OperationRequestType {
        ADD, REMOVE
    }

    /**
     * Add or remove security groups to the given network interface
     */
    public OperationRequestType requestType;

    /**
     * Link to secrets.
     */
    public String authCredentialsLink;

    /**
     * Links to security groups to be assigned to network interface
     */
    public List<String> securityGroupLinks;

    /**
     * Custom properties related to the network interface.
     */
    public Map<String, String> customProperties;
}
