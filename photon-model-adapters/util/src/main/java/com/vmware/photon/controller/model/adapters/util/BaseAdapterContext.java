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
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Base context class for adapters. Used to load endpoint related data.
 */
public class BaseAdapterContext {

    public ComputeStateWithDescription child;
    public ComputeStateWithDescription parent;
    public AuthCredentialsService.AuthCredentialsServiceState parentAuth;
    public Service service;
    public URI resourceReference;

    /**
     * Used to store an error that has occurred while transitioning to the error stage.
     */
    public Throwable error;
    /**
     * Used to store the calling operation.
     */
    public Operation adapterOperation;

    public enum BaseAdapterStages {
        VMDESC,
        PARENTDESC,
        PARENTAUTH,
        DONE
    }

    public BaseAdapterContext(Service service, URI computeReference) {
        this.service = service;
        this.resourceReference = computeReference;
    }

    public static void populateContextThen(Service service, BaseAdapterStages stage,
            URI computeReference,
            BiConsumer<BaseAdapterContext, Throwable> onSuccess) {
        BaseAdapterContext context = new BaseAdapterContext(service, computeReference);
        populateContextThen(context, stage, onSuccess);

    }

    public static <T extends BaseAdapterContext> void populateContextThen(T context,
            BaseAdapterStages stage,
            BiConsumer<T, Throwable> onSuccess) {
        doPopulateContextThen(context, stage, onSuccess);
    }

    private static <T extends BaseAdapterContext> void doPopulateContextThen(T context,
            BaseAdapterStages stage,
            BiConsumer<T, Throwable> onSuccess) {
        switch (stage) {
        case VMDESC:
            getVMDescription(context, BaseAdapterStages.PARENTDESC, onSuccess);
            break;
        case PARENTDESC:
            getParentDescription(context, BaseAdapterStages.PARENTAUTH, onSuccess);
            break;
        case PARENTAUTH:
            getParentAuth(context, BaseAdapterStages.DONE, onSuccess);
            break;
        case DONE:
        default:
            onSuccess.accept(context, null);
            break;
        }
    }

    /*
     * method will be responsible for getting the compute description for the requested resource and
     * then passing to the next step
     */
    private static <T extends BaseAdapterContext> void getVMDescription(T ctx, BaseAdapterStages next,
            BiConsumer<T, Throwable> callback) {
        Consumer<Operation> onSuccess = (op) -> {
            ctx.child = op.getBody(ComputeStateWithDescription.class);
            doPopulateContextThen(ctx, next, callback);
        };
        URI computeUri = UriUtils.extendUriWithQuery(
                ctx.resourceReference, UriUtils.URI_PARAM_ODATA_EXPAND,
                Boolean.TRUE.toString());
        AdapterUtils.getServiceState(ctx.service, computeUri, onSuccess,
                getFailureConsumer(ctx, callback));
    }

    /*
     * Method will get the service for the identified link
     */
    private static <T extends BaseAdapterContext> void getParentDescription(T ctx, BaseAdapterStages next,
            BiConsumer<T, Throwable> callback) {
        Consumer<Operation> onSuccess = (op) -> {
            ctx.parent = op.getBody(ComputeStateWithDescription.class);
            doPopulateContextThen(ctx, next, callback);
        };
        URI baseUri = ctx.child == null ? ctx.resourceReference
                : UriUtils.buildUri(ctx.service.getHost(), ctx.child.parentLink);

        URI parentExpandURI = ComputeStateWithDescription.buildUri(baseUri);
        AdapterUtils.getServiceState(ctx.service, parentExpandURI, onSuccess,
                getFailureConsumer(ctx, callback));
    }

    private static <T extends BaseAdapterContext> void getParentAuth(T ctx, BaseAdapterStages next,
            BiConsumer<T, Throwable> callback) {
        URI authURI = ctx.parent == null ? ctx.resourceReference
                : UriUtils.buildUri(ctx.service.getHost(),
                ctx.parent.description.authCredentialsLink);

        Consumer<Operation> onSuccess = (op) -> {
            ctx.parentAuth = op.getBody(AuthCredentialsServiceState.class);
            doPopulateContextThen(ctx, next, callback);
        };
        AdapterUtils.getServiceState(ctx.service, authURI, onSuccess,
                getFailureConsumer(ctx, callback));
    }

    private static <T extends BaseAdapterContext> Consumer<Throwable> getFailureConsumer(T ctx,
            BiConsumer<T, Throwable> callback) {
        return (t) -> {
            callback.accept(ctx, t);
        };
    }
}
