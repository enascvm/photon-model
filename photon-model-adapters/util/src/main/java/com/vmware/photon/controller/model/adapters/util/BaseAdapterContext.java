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

import static com.vmware.photon.controller.model.util.PhotonModelUriUtils.createInventoryUri;

import java.net.URI;
import java.util.function.Function;
import java.util.logging.Level;

import com.vmware.photon.controller.model.adapterapi.ResourceRequest;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Base class for contexts used by adapters. It {@link #populateBaseContext(BaseAdapterStage) loads}:
 * <ul>
 * <li>{@link ComputeStateWithDescription child}</li>
 * <li>{@link ComputeStateWithDescription parent}</li>
 * <li>{@link AuthCredentialsServiceState parentAuth}</li>
 * <li>{@link EndpointService.EndpointState endpoint}</li>
 * <li>{@link AuthCredentialsServiceState endpointAuth}</li>
 * </ul>
 */
public class BaseAdapterContext<T extends BaseAdapterContext<T>> {

    /**
     * Default version of {@link BaseAdapterContext} used when there's no need to extend from base.
     */
    public static final class DefaultAdapterContext
            extends BaseAdapterContext<DefaultAdapterContext> {

        public DefaultAdapterContext(StatelessService service, ResourceRequest resourceRequest) {
            super(service, resourceRequest);
        }
    }

    public enum BaseAdapterStage {
        VMDESC, PARENTDESC, ENDPOINTSTATE, PARENTAUTH, CUSTOMIZE
    }

    public final StatelessService service;
    public final URI resourceReference;

    /**
     * URI reference of endpoint. This value is not expected to be null but to support backward compatibility
     * it will read endpoint from parent resource if this value is null.
     */
    public URI endpointReference;

    /**
     * The compute state that is to be provisioned.
     */
    public ComputeStateWithDescription child;
    public ComputeStateWithDescription parent;

    public EndpointService.EndpointState endpoint;

    @Deprecated
    public AuthCredentialsServiceState parentAuth;

    // This will be same as parentAuth. Since the auth details will be read from endpoint but not from computeHost,
    // naming it as appropriate.
    public AuthCredentialsServiceState endpointAuth;

    /**
     * The error that has occurred while transitioning to the error stage.
     */
    public Throwable error;
    /**
     * Used to store the calling operation.
     */
    public Operation operation;

    public TaskManager taskManager;

    /**
     * @param service
     *            The service that is creating and using this context.
     * @param resourceRequest
     *            The ResourceRequest that is used to start the
     *            {@link #populateBaseContext(BaseAdapterStage) state machine}.
     *            <ul>
     *            <li>If {@code populateContext} is called with {@code BaseAdapterStage#VMDESC} then
     *            this should point to <b>child</b> resource.</li>
     *            <li>If {@code populateContext} is called with {@code BaseAdapterStage#PARENTDESC}
     *            then this should point to <b>parent</b> resource.</li>
     *            <li>If {@code populateContext} is called with {@code BaseAdapterStage#PARENTAUTH}
     *            then this should point to <b>parent auth</b> resource.</li>
     *            <li>If {@code populateContext} is called with {@code BaseAdapterStage#ENDPOINTSTATE}
     *            then this should point to <b>endpoint</b> resource.</li>
     *            </ul>
     */
    public BaseAdapterContext(StatelessService service, ResourceRequest resourceRequest) {
        this.service = service;
        this.resourceReference = resourceRequest.resourceReference;
        this.endpointReference = resourceRequest.endpointLinkReference;
        this.taskManager = new TaskManager(this.service, resourceRequest.taskReference,
                resourceRequest.resourceLink());
    }

    public final DeferredResult<T> populateBaseContext(BaseAdapterStage stage) {
        if (stage == null) {
            stage = BaseAdapterStage.VMDESC;
        }
        switch (stage) {
        case VMDESC:
            return getVMDescription(self())
                    .thenApply(log("getVMDescription"))
                    .thenCompose(c -> populateBaseContext(BaseAdapterStage.PARENTDESC));
        case PARENTDESC:
            return getParentDescription(self())
                    .thenApply(log("getParentDescription"))
                    .thenCompose(c -> populateBaseContext(BaseAdapterStage.ENDPOINTSTATE));
        case ENDPOINTSTATE:
            return getEndPointState(self())
                    .thenApply(log("getEndPointState"))
                    .thenCompose(c -> populateBaseContext(BaseAdapterStage.PARENTAUTH));
        case PARENTAUTH:
            return getParentAuth(self())
                    .thenApply(log("getParentAuth"))
                    .thenCompose(c -> populateBaseContext(BaseAdapterStage.CUSTOMIZE));
        case CUSTOMIZE:
            return customizeBaseContext(self()).thenApply(log("customizeBaseContext"));
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

    /**
     * Use this to log success after completing async execution stage.
     */
    protected Function<? super T, ? extends T> log(String stage) {
        return (ctx) -> {
            ctx.service.log(Level.FINE, "%s.%s: SUCCESS", this.getClass().getSimpleName(), stage);
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
     * <p>
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
     * Populate context with {@code EndpointState}.
     */
    protected DeferredResult<T> getEndPointState(T context) {
        // Fallback on compute endpointLink, if it is not set explicitly (to support backward compatibility)
        if (context.endpointReference == null && context.parent != null) {
            // first try with endpointLinks as endpointLink may be invalid in the case of de-dup
            if (context.parent.endpointLinks != null && !context.parent.endpointLinks.isEmpty()) {
                context.endpointReference = createInventoryUri(context.service.getHost(), context
                        .parent.endpointLinks.iterator().next());
            } else {
                context.endpointReference = createInventoryUri(context.service.getHost(),
                        context.parent.endpointLink);
            }
        }
        if (context.endpointReference != null) {
            Operation op = Operation.createGet(context.endpointReference);
            return context.service
                    .sendWithDeferredResult(op, EndpointService.EndpointState.class)
                    .thenApply(state -> {
                        context.endpoint = state;
                        return context;
                    });
        } else {
            return DeferredResult.completed(context);
        }
    }

    /**
     * Populate context with endpoint {@code AuthCredentialsServiceState}.
     *
     * @see #getParentAuthRef(BaseAdapterContext) for any customization.
     */
    protected DeferredResult<T> getParentAuth(T context) {

        URI parentAuthRef = getParentAuthRef(context);

        Operation op = Operation.createGet(parentAuthRef);

        return context.service
                .sendWithDeferredResult(op, AuthCredentialsServiceState.class)
                .thenApply(state -> {
                    context.parentAuth = state;
                    context.endpointAuth = state;
                    return context;
                });
    }

    /**
     * Descendants might implement this hook to provide custom link to endpoint auth.
     */
    protected URI getParentAuthRef(T context) {
        return context.endpoint != null
                // 'parent' is already resolved so used it
                ? createInventoryUri(context.service.getHost(),
                        context.endpoint.authCredentialsLink)
                // state machine starts from here so resRef should point to the parentAuth
                : createInventoryUri(context.service.getHost(), context.resourceReference);
    }

    /**
     * Hook that might be implemented by descendants to extend
     * {@link #populateBaseContext(BaseAdapterStage)}  populate logic} and customize the context.
     */
    protected DeferredResult<T> customizeBaseContext(T context) {
        return DeferredResult.completed(context);
    }

}
