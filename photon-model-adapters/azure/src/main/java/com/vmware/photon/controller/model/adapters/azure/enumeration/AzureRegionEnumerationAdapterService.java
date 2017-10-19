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

package com.vmware.photon.controller.model.adapters.azure.enumeration;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import com.microsoft.azure.management.resources.implementation.LocationInner;

import com.vmware.photon.controller.model.adapterapi.RegionEnumerationResponse;
import com.vmware.photon.controller.model.adapterapi.RegionEnumerationResponse.RegionInfo;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.endpoint.AzureEndpointAdapterService;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureDeferredResultServiceCallback;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureDeferredResultServiceCallback.Default;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureSdkClients;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.EndpointAdapterUtils;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class AzureRegionEnumerationAdapterService extends StatelessService {

    public static final String SELF_LINK = AzureUriPaths.AZURE_REGION_ENUMERATION_ADAPTER_SERVICE;

    private static class Context implements AutoCloseable {

        final AzureRegionEnumerationAdapterService service;
        final EndpointState request;

        AuthCredentialsServiceState authentication;

        AzureSdkClients azureSdkClients;

        List<LocationInner> azureLocations;

        Context(AzureRegionEnumerationAdapterService service, EndpointState request) {
            this.service = service;
            this.request = request;
        }

        /**
         * Release internal {@link AzureSdkClients} instance.
         */
        @Override
        public final void close() {
            if (this.azureSdkClients != null) {
                this.azureSdkClients.close();
                this.azureSdkClients = null;
            }
        }

        protected DeferredResult<Context> getAuthentication(Context context) {

            if (this.request.authCredentialsLink == null) {
                AzureEndpointAdapterService.credentials().accept(
                        context.authentication = new AuthCredentialsServiceState(),
                        EndpointAdapterUtils.Retriever.of(this.request.endpointProperties));

                return DeferredResult.completed(context);
            }

            Operation op = Operation.createGet(
                    context.service.getHost(),
                    context.request.authCredentialsLink);

            return context.service
                    .sendWithDeferredResult(op, AuthCredentialsServiceState.class)
                    .thenApply(state -> {
                        context.authentication = state;
                        return context;
                    });
        }

        protected DeferredResult<Context> getAzureClient(Context context) {

            context.azureSdkClients = new AzureSdkClients(
                    context.service.executorService,
                    context.authentication);

            return DeferredResult.completed(context);
        }

        protected DeferredResult<Context> getAzureLocations(Context context) {

            AzureDeferredResultServiceCallback<List<LocationInner>> callback = new Default<>(
                    context.service,
                    "Retrieving locations for subscription with id "
                            + context.azureSdkClients.authState.userLink);

            context.azureSdkClients
                    .getSubscriptionClientImpl()
                    .subscriptions()
                    .listLocationsAsync(context.azureSdkClients.authState.userLink, callback);

            return callback.toDeferredResult().thenApply(locations -> {
                context.azureLocations = locations;
                return context;
            });
        }
    }

    private ExecutorService executorService;

    public AzureRegionEnumerationAdapterService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handleStart(Operation startPost) {
        this.executorService = getHost().allocateExecutor(this);

        super.handleStart(startPost);
    }

    @Override
    public void handleStop(Operation delete) {
        this.executorService.shutdown();
        AdapterUtils.awaitTermination(this.executorService);
        super.handleStop(delete);
    }

    @Override
    public void handlePost(Operation post) {
        if (!post.hasBody()) {
            post.fail(new IllegalArgumentException("body is required"));
            return;
        }

        Context ctx = new Context(this, post.getBody(EndpointState.class));

        DeferredResult.completed(ctx)
                .thenCompose(ctx::getAuthentication)
                .thenCompose(ctx::getAzureClient)
                .thenCompose(ctx::getAzureLocations)
                .whenComplete((ignoreCtx, t) -> {
                    try {
                        if (t != null) {
                            post.fail(t);
                            return;
                        }

                        RegionEnumerationResponse result = new RegionEnumerationResponse();
                        result.regions = ctx.azureLocations.stream()
                                .map(loc -> new RegionInfo(loc.displayName(), loc.name()))
                                .collect(Collectors.toList());

                        post.setBodyNoCloning(result).complete();
                    } finally {
                        ctx.close();
                    }
                });
    }

}
