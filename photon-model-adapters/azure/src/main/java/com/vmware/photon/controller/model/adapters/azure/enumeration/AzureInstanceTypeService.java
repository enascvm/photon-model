/*
 * Copyright (c) 2015-2017 VMware, Inc. All Rights Reserved.
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

import static com.vmware.photon.controller.model.util.PhotonModelUriUtils.createInventoryUri;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

import com.microsoft.azure.management.compute.implementation.VirtualMachineSizeInner;
import com.microsoft.azure.management.compute.implementation.VirtualMachineSizesInner;

import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureDeferredResultServiceCallback;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureDeferredResultServiceCallback.Default;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureSdkClients;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.support.InstanceTypeList;
import com.vmware.photon.controller.model.support.InstanceTypeList.InstanceType;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Service that returns a list of instance types that are supported by Azure. The caller is required
 * to provide a valid endpointLink in the "endpoint" uri parameter.
 */
public class AzureInstanceTypeService extends StatelessService {

    public static final String SELF_LINK = AzureUriPaths.AZURE_INSTANCE_TYPE_ADAPTER;

    /**
     * The end-point link for which to return instance types. Must be passed as GET request
     * parameter.
     */
    public static final String URI_PARAM_ENDPOINT = "endpoint";

    /**
     * The region id for which to return instance types. Must be passed as GET request parameter.
     */
    public static final String URI_PARAM_REGION_ID = "regionId";

    private static class AzureInstanceTypeContext implements AutoCloseable {

        final AzureInstanceTypeService service;
        final String endpointLink;
        final String regionId;

        // Extracted from endpointLink {{
        EndpointState endpointState;
        AzureSdkClients azureSdkClients;
        // }}

        /**
         * The result object that's served by this service.
         */
        InstanceTypeList instanceTypesList = new InstanceTypeList();

        AzureInstanceTypeContext(AzureInstanceTypeService service, String endpointLink,
                String regionId) {
            this.service = service;

            this.endpointLink = endpointLink;
            this.regionId = regionId;
        }

        public final DeferredResult<AzureInstanceTypeContext> populate() {

            return DeferredResult.completed(this)
                    .thenCompose(this::getEndpointState)
                    .thenCompose(this::getEndpointAuthState)
                    .thenCompose(this::loadInstanceTypes);
        }

        DeferredResult<AzureInstanceTypeContext> getEndpointState(
                AzureInstanceTypeContext ctx) {

            Operation op = Operation.createGet(ctx.service, ctx.endpointLink);

            return ctx.service
                    .sendWithDeferredResult(op, EndpointState.class)
                    .thenApply(state -> {
                        ctx.endpointState = state;
                        return ctx;
                    });
        }

        DeferredResult<AzureInstanceTypeContext> getEndpointAuthState(
                AzureInstanceTypeContext ctx) {

            Operation op = Operation.createGet(createInventoryUri(ctx.service.getHost(),
                    ctx.endpointState.authCredentialsLink));

            return ctx.service
                    .sendWithDeferredResult(op, AuthCredentialsServiceState.class)
                    .thenApply(authState -> {
                        ctx.azureSdkClients = new AzureSdkClients(authState);
                        return ctx;
                    });
        }

        DeferredResult<AzureInstanceTypeContext> loadInstanceTypes(AzureInstanceTypeContext ctx) {

            VirtualMachineSizesInner virtualMachineSizes = ctx.azureSdkClients
                    .getComputeManager()
                    .inner()
                    .virtualMachineSizes();

            String msg = "Getting Azure instance types for ["
                    + ctx.regionId + "/" + ctx.endpointState.name + "] Endpoint";

            AzureDeferredResultServiceCallback<List<VirtualMachineSizeInner>> handler = new Default<>(
                    ctx.service, msg);

            virtualMachineSizes.listAsync(ctx.regionId, handler);

            return handler.toDeferredResult().thenApply(vmSizes -> {

                ctx.instanceTypesList.tenantLinks = ctx.endpointState.tenantLinks;

                ctx.instanceTypesList.instanceTypes = vmSizes.stream()
                        .map(vmSize -> {
                            InstanceType instanceType = new InstanceType(
                                    vmSize.name(), vmSize.name());

                            instanceType.cpuCount = vmSize.numberOfCores();
                            instanceType.memoryInMB = vmSize.memoryInMB();
                            instanceType.bootDiskSizeInMB = vmSize.osDiskSizeInMB();
                            instanceType.dataDiskSizeInMB = vmSize.resourceDiskSizeInMB();
                            instanceType.dataDiskMaxCount = vmSize.maxDataDiskCount();

                            return instanceType;
                        })
                        .collect(Collectors.toList());

                return ctx;
            });
        }

        @Override
        public void close() {
            if (this.azureSdkClients != null) {
                this.azureSdkClients.close();
                this.azureSdkClients = null;
            }
        }
    }

    public AzureInstanceTypeService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handleGet(Operation op) {

        Map<String, String> queryParams = UriUtils.parseUriQueryParams(op.getUri());

        final String endpointLink = queryParams.get(URI_PARAM_ENDPOINT);

        if (endpointLink == null || endpointLink.isEmpty()) {
            op.fail(new IllegalArgumentException(
                    "'" + URI_PARAM_ENDPOINT + "' query param is required"));
            return;
        }

        final String regionId = queryParams.get(URI_PARAM_REGION_ID);

        if (regionId == null || regionId.isEmpty()) {
            op.fail(new IllegalArgumentException(
                    "'" + URI_PARAM_REGION_ID + "' query param is required"));
            return;
        }

        AzureInstanceTypeContext context = new AzureInstanceTypeContext(
                this, endpointLink, regionId);

        final String msg = "Instance types enumeration";

        logFine(() -> msg + ": STARTED");

        context.populate().whenComplete((ignoreCtx, err) -> {
            try {
                if (err == null) {
                    logFine(() -> msg + ": COMPLETED");
                    op.setBodyNoCloning(context.instanceTypesList).complete();
                } else {
                    Throwable finalErr = err instanceof CompletionException ? err.getCause() : err;
                    logSevere(() -> msg + ": FAILED with " + Utils.toString(finalErr));
                    op.fail(finalErr);
                }
            } finally {
                // NOTE: In case of error 'ignoreCtx' is null so use passed context!
                context.close();
            }
        });
    }
}
