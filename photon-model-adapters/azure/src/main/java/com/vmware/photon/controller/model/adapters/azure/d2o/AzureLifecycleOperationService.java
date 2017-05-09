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

package com.vmware.photon.controller.model.adapters.azure.d2o;

import static com.vmware.photon.controller.model.ComputeProperties.RESOURCE_GROUP_NAME;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;

import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.credentials.AzureEnvironment;
import com.microsoft.azure.management.compute.ComputeManagementClient;
import com.microsoft.azure.management.compute.ComputeManagementClientImpl;
import com.microsoft.rest.ServiceCallback;
import com.microsoft.rest.ServiceResponse;

import okhttp3.OkHttpClient;

import retrofit2.Retrofit;

import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperation;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationRequest;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceOperationSpec;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceType;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationUtils;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext.BaseAdapterStage;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext.DefaultAdapterContext;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.security.util.EncryptionUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;


public class AzureLifecycleOperationService extends StatelessService {

    public static final String SELF_LINK = ResourceOperationSpecService.buildDefaultAdapterLink(
                    EndpointType.azure.name(), ResourceOperationSpecService.ResourceType.COMPUTE,
            "d2opower");

    private static class AzureLifecycleOpDataHolder {

        final ResourceOperationRequest request;

        ComputeManagementClient client;
        OkHttpClient httpClient;
        AzureLifecycleOperationService service;
        String vmName;
        String rgName;

        public AzureLifecycleOpDataHolder(AzureLifecycleOperationService service,
                ResourceOperationRequest request) {
            this.request = request;
            this.service = service;
        }
    }

    private ExecutorService executorService;

    public ApplicationTokenCredentials credentials;

    @Override
    public void handleStart(Operation startPost) {
        this.executorService = getHost().allocateExecutor(this);
        CompletionHandler completionHandler = (op, exc) -> {
            if (exc != null) {
                startPost.fail(exc);
            }
            startPost.complete();
        };
        ResourceOperationUtils.registerResourceOperation(this,
                getResourceOperationSpecs(), completionHandler);
    }

    private Collection<ResourceOperationSpec> getResourceOperationSpecs() {
        List<ResourceOperationSpec> specs = new ArrayList<>();
        ResourceOperationSpec spec1 = new ResourceOperationSpec();
        spec1.adapterReference = AdapterUriUtil.buildAdapterUri(getHost(), SELF_LINK);
        spec1.endpointType = EndpointType.azure.name();
        spec1.resourceType = ResourceType.COMPUTE;
        spec1.operation = ResourceOperation.RESTART.operation;
        spec1.name = ResourceOperation.RESTART.displayName;
        spec1.description = ResourceOperation.RESTART.description;
        spec1.targetCriteria = ResourceOperationUtils.SCRIPT_CONTEXT_RESOURCE +
                ".powerState.equals('ON')";
        specs.add(spec1);
        ResourceOperationSpec spec2 = new ResourceOperationSpec();
        spec2.adapterReference = AdapterUriUtil.buildAdapterUri(getHost(), SELF_LINK);
        spec2.endpointType = EndpointType.azure.name();
        spec2.resourceType = ResourceType.COMPUTE;
        spec2.operation = ResourceOperation.SUSPEND.operation;
        spec2.name = ResourceOperation.SUSPEND.displayName;
        spec2.description = ResourceOperation.SUSPEND.description;
        spec2.targetCriteria = ResourceOperationUtils.SCRIPT_CONTEXT_RESOURCE +
                ".powerState.equals('ON')";
        specs.add(spec2);
        return specs;
    }

    @Override
    public void handleStop(Operation delete) {
        this.executorService.shutdown();
        AdapterUtils.awaitTermination(this.executorService);
        super.handleStop(delete);
    }


    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        ResourceOperationRequest request = op.getBody(ResourceOperationRequest.class);
        op.complete();
        AzureLifecycleOpDataHolder dh = new AzureLifecycleOpDataHolder(this, request);
        logInfo("Handle operation %s for compute %s.",
                request.operation, request.resourceLink());
        if (request.isMockRequest) {
            updateComputeState(dh, new DefaultAdapterContext(this, request));
            return;
        } else {
            new DefaultAdapterContext(this, request)
                    .populateBaseContext(BaseAdapterStage.VMDESC)
                    .whenComplete((c, e) -> {
                        if (e != null) {
                            c.taskManager.patchTaskToFailure(e);
                            this.logSevere(
                                    "Error populating base context during Azure resource operation %s for resource %s failed with error %s",
                                    request.operation, request.resourceReference,
                                    Utils.toString(e));
                            return;
                        }
                        String clientId = c.parentAuth.privateKeyId;
                        String clientKey = EncryptionUtils.decrypt(c.parentAuth.privateKey);
                        String tenantId = c.parentAuth.customProperties
                                .get(AzureConstants.AZURE_TENANT_ID);

                        ApplicationTokenCredentials credentials = new ApplicationTokenCredentials(
                                clientId, tenantId, clientKey,
                                AzureEnvironment.AZURE);

                        dh.httpClient = new OkHttpClient();
                        OkHttpClient.Builder clientBuilder = dh.httpClient.newBuilder();

                        dh.client = new ComputeManagementClientImpl(
                                AzureConstants.BASE_URI, credentials, clientBuilder,
                                getRetrofitBuilder());
                        dh.client.setSubscriptionId(c.parentAuth.userLink);
                        dh.vmName = c.child.name != null ? c.child.name : c.child.id;
                        dh.rgName = getResourceGroupName(c);
                        applyResourceOperation(dh, c);
                    });
        }
    }

    private void applyResourceOperation(AzureLifecycleOpDataHolder dh, DefaultAdapterContext ctx) {
        if (ResourceOperation.RESTART.operation.equals(dh.request.operation)) {
            restart(dh, ctx);
        } else if (ResourceOperation.SUSPEND.operation.equals(dh.request.operation)) {
            suspend(dh, ctx);
        } else {
            String errorMsg = String.format(
                    "Unsupported resource operation %s requested for resource %s under group %s.",
                    dh.request.operation, dh.vmName, dh.rgName);
            ctx.taskManager.patchTaskToFailure(new IllegalArgumentException(errorMsg));
        }
    }

    private void restart(AzureLifecycleOpDataHolder dh, DefaultAdapterContext ctx) {
        dh.client.getVirtualMachinesOperations().restartAsync(dh.rgName, dh.vmName,
                new ServiceCallback<Void>() {
                    @Override
                    public void failure(Throwable paramThrowable) {
                        logSevere(
                                "Error: Azure restart operation failed for resource %s in resourceGroup %s with error %s",
                                dh.vmName, dh.rgName, Utils.toString(paramThrowable));
                        ctx.taskManager.patchTaskToFailure(paramThrowable);
                        AzureUtils.cleanUpHttpClient(dh.service, dh.httpClient);
                    }

                    @Override
                    public void success(ServiceResponse<Void> paramServiceResponse) {
                        logFine(
                                "Success: Azure restart operation for resource %s in resourceGroup %s completed successfully.",
                                dh.vmName, dh.rgName);
                        updateComputeState(dh, ctx);
                        AzureUtils.cleanUpHttpClient(dh.service, dh.httpClient);
                    }
                });
    }

    private void suspend(AzureLifecycleOpDataHolder dh, DefaultAdapterContext ctx) {
        dh.client.getVirtualMachinesOperations().deallocateAsync(dh.rgName, dh.vmName,
                new ServiceCallback<Void>() {
                    @Override
                    public void failure(Throwable paramThrowable) {
                        logSevere(
                                "Error: Azure deallocate operation failed for resource %s in resourceGroup %s with error %s",
                                dh.vmName, dh.rgName, Utils.toString(paramThrowable));
                        ctx.taskManager.patchTaskToFailure(paramThrowable);
                        AzureUtils.cleanUpHttpClient(dh.service, dh.httpClient);
                    }

                    @Override
                    public void success(ServiceResponse<Void> paramServiceResponse) {
                        logFine(
                                "Success: Azure deallocate operation for resource %s in resourceGroup %s completed successfully.",
                                dh.vmName, dh.rgName);
                        updateComputeState(dh, ctx);
                        AzureUtils.cleanUpHttpClient(dh.service, dh.httpClient);
                    }
                });
    }

    private Retrofit.Builder getRetrofitBuilder() {
        Retrofit.Builder builder = new Retrofit.Builder();
        builder.callbackExecutor(this.executorService);
        return builder;
    }

    private String getResourceGroupName(DefaultAdapterContext ctx) {
        String resourceGroupName = null;
        if (ctx.child.customProperties != null) {
            resourceGroupName = ctx.child.customProperties.get(RESOURCE_GROUP_NAME);
        }

        if (resourceGroupName == null && ctx.child.description.customProperties != null) {
            resourceGroupName = ctx.child.description.customProperties.get(RESOURCE_GROUP_NAME);
        }
        return resourceGroupName;
    }

    private void updateComputeState(AzureLifecycleOpDataHolder dh, DefaultAdapterContext c) {
        ComputeState state = new ComputeState();
        state.powerState = getPowerState(dh.request);
        Operation.createPatch(dh.request.resourceReference)
                .setBody(state)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        c.taskManager.patchTaskToFailure(e);
                        return;
                    }
                    c.taskManager.finishTask();
                })
                .sendWith(this);
    }

    private PowerState getPowerState(ResourceOperationRequest request) {
        PowerState state = PowerState.UNKNOWN;
        if (ResourceOperation.RESTART.operation.equals(request.operation)) {
            state = PowerState.ON;
        } else if (ResourceOperation.SUSPEND.operation.equals(request.operation)) {
            state = PowerState.SUSPEND;
        }
        return state;
    }
}
