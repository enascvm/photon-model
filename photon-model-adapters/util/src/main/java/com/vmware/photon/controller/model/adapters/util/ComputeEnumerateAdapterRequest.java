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

package com.vmware.photon.controller.model.adapters.util;

import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;

import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Holds the compute resource request and other data that is required by the helper flows to
 * perform resource enumeration.
 *
 * This is the contract from the master adapter enumeration service
 * (XXXEnumerationAdapterService) to the slave enumeration services dealing with specific
 * resources (networks, storage, etc).
 */
public class ComputeEnumerateAdapterRequest {

    /**
     * The original enumerate-compute request send to the adapter.
     */
    public ComputeEnumerateResourceRequest original;

    public AuthCredentialsServiceState parentAuth;
    public ComputeStateWithDescription parentCompute;
    /**
     * Optional region to run the enumeration operation on
     */
    public String regionId;

    public ComputeEnumerateAdapterRequest(ComputeEnumerateResourceRequest request,
            AuthCredentialsService.AuthCredentialsServiceState parentAuth,
            ComputeStateWithDescription parentCompute) {
        this.original = request;
        this.parentAuth = parentAuth;
        this.parentCompute = parentCompute;
    }

    public ComputeEnumerateAdapterRequest(ComputeEnumerateResourceRequest request,
            AuthCredentialsService.AuthCredentialsServiceState parentAuth,
            ComputeStateWithDescription parentCompute, String regionId) {
        this.original = request;
        this.parentAuth = parentAuth;
        this.parentCompute = parentCompute;
        this.regionId = regionId;
    }
}
