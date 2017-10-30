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

package com.vmware.photon.controller.model.adapters.vsphere;

import static com.vmware.photon.controller.model.util.PhotonModelUriUtils.createInventoryUri;

import java.net.URI;
import java.util.function.Consumer;

import com.vmware.photon.controller.model.adapterapi.ResourceRequest;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * A vSphere virtual machine compute representation
 */
public class VSphereVMContext extends BaseAdapterContext<VSphereVMContext> {

    public VSphereIOThreadPool pool;
    public Consumer<Throwable> errorHandler;

    public VSphereVMContext(StatelessService service, ResourceRequest resourceRequest) {
        super(service, resourceRequest);
        this.errorHandler = failure -> {
            this.taskManager.patchTaskToFailure(failure);
        };
    }

    /**
     * Populates the given initial context and invoke the onSuccess handler when built. At every step,
     * if failure occurs the VSphereVMContext's errorHandler is invoked to cleanup.
     *
     * @param ctx
     * @param onSuccess
     */
    protected static void populateVMContextThen(Service service, VSphereVMContext ctx,
                                             Consumer<VSphereVMContext> onSuccess) {

        if (ctx.child == null) {
            URI computeUri = UriUtils
                    .extendUriWithQuery(ctx.resourceReference,
                            UriUtils.URI_PARAM_ODATA_EXPAND,
                            Boolean.TRUE.toString());
            AdapterUtils.getServiceState(service, computeUri, op -> {
                ctx.child = op.getBody(ComputeStateWithDescription.class);
                populateVMContextThen(service, ctx, onSuccess);
            }, ctx.errorHandler);
            return;
        }

        if (ctx.parent == null && ctx.child.parentLink != null) {
            URI computeUri = UriUtils
                    .extendUriWithQuery(
                            UriUtils.buildUri(service.getHost(), ctx.child.parentLink),
                            UriUtils.URI_PARAM_ODATA_EXPAND,
                            Boolean.TRUE.toString());

            AdapterUtils.getServiceState(service, computeUri, op -> {
                ctx.parent = op.getBody(ComputeStateWithDescription.class);
                populateVMContextThen(service, ctx, onSuccess);
            }, ctx.errorHandler);
            return;
        }

        if (ctx.parentAuth == null) {
            if (ctx.parent.description.authCredentialsLink == null) {
                ctx.fail(new IllegalStateException(
                        "authCredentialsLink is not defined in resource "
                                + ctx.parent.description.documentSelfLink));
                return;
            }

            URI credUri = createInventoryUri(service.getHost(),
                    ctx.parent.description.authCredentialsLink);
            AdapterUtils.getServiceState(service, credUri, op -> {
                ctx.parentAuth = op.getBody(AuthCredentialsServiceState.class);
                populateVMContextThen(service, ctx, onSuccess);
            }, ctx.errorHandler);
            return;
        }

        // context populated, invoke handler
        onSuccess.accept(ctx);
    }

    /**
     * The returned JoinedCompletionHandler fails this context by invoking the error handler if any
     * error is found in {@link OperationJoin.JoinedCompletionHandler#handle(java.util.Map, java.util.Map) error map}.
     */
    protected OperationJoin.JoinedCompletionHandler failTaskOnError() {
        return (ops, failures) -> {
            if (failures != null && !failures.isEmpty()) {
                Throwable firstError = failures.values().iterator().next();
                this.fail(firstError);
            }
        };
    }

    /**
     * Fails the resource operation request by invoking the errorHandler.
     *
     * @param th
     * @return true if th is defined, false otherwise
     */
    protected boolean fail(Throwable th) {
        if (th != null) {
            this.errorHandler.accept(th);
            return true;
        } else {
            return false;
        }
    }

    protected void failWithMessage(String msg, Throwable t) {
        this.fail(new Exception(msg, t));
    }

    protected URI getAdapterManagementReference() {
        if (this.child.adapterManagementReference != null) {
            return this.child.adapterManagementReference;
        } else {
            return this.parent.adapterManagementReference;
        }
    }

    protected void failWithMessage(String msg) {
        fail(new IllegalStateException(msg));
    }
}
