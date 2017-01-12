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
import java.util.function.Function;

import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
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

        public DefaultAdapterContext(StatelessService service, URI computeReference) {
            super(service, computeReference);
        }
    }

    public static enum BaseAdapterStage {
        VMDESC, PARENTDESC, PARENTAUTH
    }

    public final StatelessService service;
    public final URI resourceReference;

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

    /**
     * @param service
     *            The service that is creating and using this context.
     * @param resourceReference
     *            The URI of the resource that is used to start the
     *            {@link #populateContext(BaseAdapterStage) state machine}.
     *            <ul>
     *            <li>If {@code populateContext} is called with {@code BaseAdapterStage#VMDESC} then
     *            this should point to <b>child</b> resource.</li>
     *            <li>If {@code populateContext} is called with {@code BaseAdapterStage#PARENTDESC}
     *            then this should point to <b>parent</b> resource.</li>
     *            <li>If {@code populateContext} is called with {@code BaseAdapterStage#PARENTAUTH}
     *            then this should point to <b>parent auth</b> resource.</li>
     *            </ul>
     */
    public BaseAdapterContext(StatelessService service, URI resourceReference) {
        this.service = service;
        this.resourceReference = resourceReference;
    }

    public DeferredResult<T> populateContext(BaseAdapterStage stage) {
        if (stage == null) {
            stage = BaseAdapterStage.VMDESC;
        }
        switch (stage) {
        case VMDESC:
            return getVMDescription(self())
                    .thenApply(log("getVMDescription"))
                    .thenCompose(c -> populateContext(BaseAdapterStage.PARENTDESC));
        case PARENTDESC:
            return getParentDescription(self())
                    .thenApply(log("getParentDescription"))
                    .thenCompose(c -> populateContext(BaseAdapterStage.PARENTAUTH));
        case PARENTAUTH:
            return getParentAuth(self()).thenApply(log("getParentAuth"));
        default:
            return DeferredResult.completed(self());
        }
    }

    /**
     * Isolate all cases when this instance should be cast to T. For internal use only.
     */
    @SuppressWarnings("unchecked")
    protected T self() {
        return (T) this;
    }

    protected Function<? super T, ? extends T> log(String stage) {
        return (ctx) -> {
            ctx.service.logFine("%s.%s: SUCCESS", this.getClass().getSimpleName(), stage);
            return ctx;
        };
    }

    /**
     * Populate context with child {@code ComputeStateWithDescription}.
     */
    protected DeferredResult<T> getVMDescription(T context) {

        URI ref = ComputeStateWithDescription.buildUri(context.resourceReference);

        Operation op = Operation.createGet(ref);

        return context.service
                .sendWithDeferredResult(op, ComputeStateWithDescription.class)
                .thenApply(state -> {
                    context.child = state;
                    return context;
                });
    }

    /**
     * Populate context with parent {@code ComputeStateWithDescription}.
     *
     * <p>
     * By default {@code context.child.parentLink} is used as source.
     */
    protected DeferredResult<T> getParentDescription(T context) {

        URI ref = context.child != null
                // 'child' is already resolved so used it
                ? UriUtils.buildUri(context.service.getHost(), context.child.parentLink)
                // state machine starts from here so resRef should point to the parent
                : context.resourceReference;

        ref = ComputeStateWithDescription.buildUri(ref);

        Operation op = Operation.createGet(ref);

        return context.service
                .sendWithDeferredResult(op, ComputeStateWithDescription.class)
                .thenApply(state -> {
                    context.parent = state;
                    return context;
                });
    }

    /**
     * Populate context with parent {@code AuthCredentialsServiceState}.
     *
     * @see {@link #getParentAuthLink(BaseAdapterContext)} for any customization
     */
    protected DeferredResult<T> getParentAuth(T context) {

        URI parentAuthRef = getParentAuthRef(context);

        Operation op = Operation.createGet(parentAuthRef);

        return context.service
                .sendWithDeferredResult(op, AuthCredentialsServiceState.class)
                .thenApply(state -> {
                    context.parentAuth = state;
                    return context;
                });
    }

    /**
     * Descendants might implement this hook to provide custom link to parent auth.
     */
    protected URI getParentAuthRef(T context) {
        return context.parent != null
                // 'parent' is already resolved so used it
                ? UriUtils.buildUri(context.service.getHost(),
                        context.parent.description.authCredentialsLink)
                // state machine starts from here so resRef should point to the parentAuth
                : context.resourceReference;
    }

}
