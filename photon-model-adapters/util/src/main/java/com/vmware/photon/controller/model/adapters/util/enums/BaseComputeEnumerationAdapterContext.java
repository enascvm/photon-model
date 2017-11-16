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

package com.vmware.photon.controller.model.adapters.util.enums;

import com.vmware.photon.controller.model.adapters.util.ComputeEnumerateAdapterRequest;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;

/**
 * Base class for context used by compute enumeration adapters. It loads resources from the remote
 * system, creates or updates local resource states and deletes stale resource states.
 * <p>
 * To use the class override its abstract methods and call {@link #enumerate()} method.
 *
 * @see EndpointEnumerationProcess
 *
 * @param <T>
 *            The derived context class.
 * @param <LOCAL_STATE>
 *            The class representing the local resource states.
 * @param <REMOTE>
 *            The class representing the remote resource.
 */
public abstract class BaseComputeEnumerationAdapterContext<T extends BaseComputeEnumerationAdapterContext<T, LOCAL_STATE, REMOTE>, LOCAL_STATE extends ResourceState, REMOTE>
        extends EndpointEnumerationProcess<T, LOCAL_STATE, REMOTE> {

    /**
     * The {@link ComputeEnumerateAdapterRequest request} that is being processed.
     */
    public final ComputeEnumerateAdapterRequest request;

    /**
     * The operation that triggered this enumeration. Used to signal the completion of the
     * enumeration once all the stages are successfully completed.
     */
    public final Operation operation;

    /**
     * Used to store an error while transferring to the error stage.
     */
    public Throwable error;

    public EnumerationStages stage = EnumerationStages.CLIENT;

    /**
     * Constructs the {@link BaseComputeEnumerationAdapterContext}.
     *
     * @param service
     *            The service that is creating and using this context.
     * @param request
     *            The request that triggered this enumeration.
     * @param operation
     *            The operation that triggered this enumeration.
     * @param localStateClass
     *            The class representing the local resource states.
     * @param localStateServiceFactoryLink
     *            The factory link of the service handling the local resource states.
     */
    public BaseComputeEnumerationAdapterContext(StatelessService service,
            ComputeEnumerateAdapterRequest request,
            Operation operation,
            Class<LOCAL_STATE> localStateClass,
            String localStateServiceFactoryLink) {

        super(service,
                request.original.buildUri(request.original.endpointLink),
                request.parentCompute.documentSelfLink,
                localStateClass,
                localStateServiceFactoryLink,
                request.original.deletedResourceExpirationMicros);

        this.request = request;
        this.operation = operation;
    }

    /**
     * Return the region explicitly specified in the request: {@code this.request.regionId}. Might
     * be {@code null}
     */
    @Override
    public String getEndpointRegion() {
        return this.request.regionId != null
                ? this.request.regionId
                : super.getEndpointRegion();
    }

}
