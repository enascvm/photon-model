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

import java.net.URI;

import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Base class for contexts used by adapters. It {@link #populateContext(BaseAdapterStage) loads}:
 * <ul>
 * <li>{@link ComputeStateWithDescription child}</li>
 * <li>{@link ComputeStateWithDescription parent}</li>
 * <li>{@link AuthCredentialsServiceState parentAuth}</li>
 * </ul>
 */
public class BaseAdapterContext<T extends BaseAdapterContext<T>> {

    /**
     * Default version of {@link BaseAdapterContext} used when there's no need to extend from base.
     */
    public static final class DefaultAdapterContext
            extends BaseAdapterContext<DefaultAdapterContext> {

        public DefaultAdapterContext(Service service, URI computeReference) {
            super(service, computeReference);
        }
    }

    /**
     * The service that is creating and using this context.
     */
    public final Service service;
    public URI resourceReference;

    /**
     * The compute state that is to be provisioned.
     */
    public ComputeStateWithDescription child;
    public ComputeStateWithDescription parent;
    public AuthCredentialsServiceState parentAuth;

    /**
     * The error that has occurred while transitioning to the error stage.
     */
    public Throwable error;
    /**
     * Used to store the calling operation.
     */
    public Operation adapterOperation;

    public enum BaseAdapterStage {
        VMDESC, PARENTDESC, PARENTAUTH
    }

    public BaseAdapterContext(Service service, URI computeReference) {
        this.service = service;
        this.resourceReference = computeReference;
    }

    public DeferredResult<T> populateContext(BaseAdapterStage stage) {
        if (stage == null) {
            stage = BaseAdapterStage.VMDESC;
        }
        switch (stage) {
        case VMDESC:
            return getVMDescription(self())
                    .thenCompose(c -> populateContext(BaseAdapterStage.PARENTDESC));
        case PARENTDESC:
            return getParentDescription(self())
                    .thenCompose(c -> populateContext(BaseAdapterStage.PARENTAUTH));
        case PARENTAUTH:
            return getParentAuth(self());
        default:
            return DeferredResult.completed(self());
        }
    }

    /**
     * Isolate all cases when this instance should be cast to CHILD. For internal use only.
     */
    @SuppressWarnings("unchecked")
    protected T self() {
        return (T) this;
    }

    private DeferredResult<T> getVMDescription(T context) {

        URI uri = ComputeStateWithDescription.buildUri(context.resourceReference);

        Operation op = Operation.createGet(uri);

        return context.service
                .sendWithDeferredResult(op, ComputeStateWithDescription.class)
                .thenApply(state -> {
                    context.child = state;
                    return context;
                });
    }

    private DeferredResult<T> getParentDescription(T context) {

        URI uri = context.child != null
                ? UriUtils.buildUri(context.service.getHost(), context.child.parentLink)
                : context.resourceReference;

        uri = ComputeStateWithDescription.buildUri(uri);

        Operation op = Operation.createGet(uri);

        return context.service
                .sendWithDeferredResult(op, ComputeStateWithDescription.class)
                .thenApply(state -> {
                    context.parent = state;
                    return context;
                });
    }

    private DeferredResult<T> getParentAuth(T context) {
        URI uri = context.parent != null
                ? UriUtils.buildUri(context.service.getHost(),
                        context.parent.description.authCredentialsLink)
                : context.resourceReference;

        Operation op = Operation.createGet(uri);

        return context.service
                .sendWithDeferredResult(op, AuthCredentialsServiceState.class)
                .thenApply(state -> {
                    context.parentAuth = state;
                    return context;
                });
    }

}
