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

import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.REGION_KEY;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import com.microsoft.azure.management.compute.implementation.VirtualMachineSizeInner;
import com.microsoft.azure.management.compute.implementation.VirtualMachineSizesInner;
import com.microsoft.azure.management.resources.fluentcore.arm.Region;

import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureDeferredResultServiceCallback;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureSdkClients;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
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

    public static final String URI_PARAM_ENDPOINT = "endpoint";

    private static class Context implements AutoCloseable {

        final StatelessService service;
        final String endpointLink;

        // Extracted from endpointLink {{
        EndpointState endpointState;
        String regionId;
        AuthCredentialsServiceState endpointAuthState;
        // }}

        AzureSdkClients azureSdkClients;

        /**
         * The result object that's served by this service.
         */
        InstanceTypeList instanceTypesList = new InstanceTypeList();

        Context(StatelessService service, String endpointLink) {
            this.service = service;
            this.endpointLink = endpointLink;
        }

        public final DeferredResult<Context> populate() {

            return DeferredResult.completed(this)
                    .thenCompose(this::getEndpointState)
                    .thenCompose(this::getEndpointAuthState)
                    .thenCompose(this::loadInstanceTypes);
        }

        protected DeferredResult<Context> getEndpointState(Context ctx) {

            Operation op = Operation.createGet(ctx.service, ctx.endpointLink);

            return ctx.service
                    .sendWithDeferredResult(op, EndpointState.class)
                    .thenApply(state -> {
                        ctx.endpointState = state;

                        ctx.regionId = state.endpointProperties.getOrDefault(
                                REGION_KEY, Region.US_WEST.name());

                        return ctx;
                    });
        }

        protected DeferredResult<Context> getEndpointAuthState(Context ctx) {

            Operation op = Operation.createGet(ctx.service, ctx.endpointState.authCredentialsLink);

            return ctx.service
                    .sendWithDeferredResult(op, AuthCredentialsServiceState.class)
                    .thenApply(state -> {
                        ctx.endpointAuthState = state;

                        ctx.azureSdkClients = new AzureSdkClients(
                                ((AzureInstanceTypeService) ctx.service).executorService,
                                ctx.endpointAuthState);

                        return ctx;
                    });
        }

        protected DeferredResult<Context> loadInstanceTypes(Context ctx) {

            VirtualMachineSizesInner virtualMachineSizes = ctx.azureSdkClients
                    .getComputeManager()
                    .inner()
                    .virtualMachineSizes();

            String msg = "Getting Azure instance types for ["
                    + ctx.regionId + "/" + ctx.endpointState.name + "] Endpoint";

            AzureDeferredResultServiceCallback<List<VirtualMachineSizeInner>> handler = new AzureDeferredResultServiceCallback<List<VirtualMachineSizeInner>>(
                    ctx.service, msg) {
                @Override
                protected DeferredResult<List<VirtualMachineSizeInner>> consumeSuccess(
                        List<VirtualMachineSizeInner> result) {
                    return DeferredResult.completed(result);
                }
            };

            virtualMachineSizes.listAsync(ctx.regionId, handler);

            return handler.toDeferredResult()
                    .thenApply(vmSizes -> {

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

    private ExecutorService executorService;

    public AzureInstanceTypeService() {
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
    public void handleGet(Operation op) {

        Map<String, String> queryParams = UriUtils.parseUriQueryParams(op.getUri());

        String endpointLink = queryParams.get(URI_PARAM_ENDPOINT);

        if (endpointLink == null || endpointLink.isEmpty()) {
            op.fail(new IllegalArgumentException(
                    "'" + URI_PARAM_ENDPOINT + "' query param is required"));
            return;
        }

        Context context = new Context(this, endpointLink);

        final String msg = "Instance types enumeration";

        logFine(() -> msg + ": STARTED");

        context.populate().whenComplete((ignoreCtx, err) -> {
            try {
                // NOTE: In case of error 'ignoreCtx' is null so use passed context!
                if (err == null) {
                    logFine(() -> msg + ": COMPLETED");
                    op.setBodyNoCloning(context.instanceTypesList).complete();
                } else {
                    logSevere(() -> msg + ": FAILED with " + Utils.toString(err));
                    op.fail(err);
                }
            } finally {
                context.close();
            }
        });
    }

}
