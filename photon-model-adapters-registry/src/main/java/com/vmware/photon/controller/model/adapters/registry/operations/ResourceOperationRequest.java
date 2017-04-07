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

package com.vmware.photon.controller.model.adapters.registry.operations;

import java.util.Map;

import com.vmware.photon.controller.model.adapterapi.ResourceRequest;

/**
 * {@link ResourceOperationRequest} is the contract between the caller/consumer of a service
 * registered with {@link com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceOperationSpec}
 * and the adapter providing the actual functionality, manifested by the same {@link
 * com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceOperationSpec}.
 * In other words this is the body of the request to the resource operation adapter.
 */
public class ResourceOperationRequest extends ResourceRequest {
    /**
     * This is the value of {@link com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceOperationSpec#operation}
     * <p>
     * Caller of the Resource Operation Adapter, specified by the {@link
     * com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceOperationSpec},
     * shall set the value, so that the adapter knows for which operation was called. This is
     * introduced for convenience, so that a single adapter can serve multiple operations
     */
    public String operation;

    /**
     * This is the actual payload of the resource operation's request.
     */
    public Map<String, String> payload;
}
