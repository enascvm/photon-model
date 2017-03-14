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

/**
 * Request to create/destroy a subnet instance on a given compute.
 * <p>
 * Reference to the subnet state describing the subnet to be provisioned should be provided in
 * {@link ResourceRequest#resourceReference}.
 */
public class SubnetInstanceRequest extends ResourceRequest {

    /**
     * Type of an Instance Request.
     */
    public enum InstanceRequestType {
        CREATE, DELETE
    }

    /**
     * Destroy or create a subnet instance on the given compute.
     */
    public InstanceRequestType requestType;
}
