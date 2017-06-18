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

package com.vmware.photon.controller.model.adapters.registry;

import java.util.List;
import java.util.Map;

/**
 * FetchDataRequest entity used to request data from backend
 * <p>The contract is that in UI the entity is filled with proper data which is passed to the
 * service that shall provide the data.
 */
public class FetchDataRequest {
    public enum RequestType {
        EndpointType, ResourceOperation, ResourceDetails
    }

    /**
     * Mandatory field specifying the type(context) of the request.
     * <p>
     * This field specify how to interpret the {@link #entityId} value.
     */
    public RequestType requestType;
    /**
     * Mandatory field specifying the entity identifier for which fetch data is requested.
     * <p>
     * this field in combination with the {@link #requestType} field identify unique the
     * entity in which context the fetch data is requested
     * <ul>examples
     * <li>/resources/compute/3096736d2dbc2e7555188c1064140 for compute</li>
     * <li>awd for endpoint</li>
     * </ul>
     */
    public String entityId;
    /**
     * optional header field for request specific meta-data e.g. reboot : operation=reboot inc
     * ase of operation specific fetch data.
     */
    public Map<String, String> header;

    /**
     * optional tenant links
     */
    public List<String> tenantLinks;

    /**
     * Optional data, the actual payload in json representation. Adapter will get this as Map
     */
    public Object data;
}
