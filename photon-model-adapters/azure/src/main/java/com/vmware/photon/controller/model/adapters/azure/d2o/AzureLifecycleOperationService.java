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
import static com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationUtils.TargetCriteria;

import java.util.concurrent.ExecutorService;

import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.compute.implementation.OperationStatusResponseInner;
import com.microsoft.rest.RestClient;
import com.microsoft.rest.ServiceCallback;


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

        Azure azureClient;
        RestClient restClient;
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
            } else {
                startPost.complete();
            }
        };
        ResourceOperationUtils.registerResourceOperation(this,
                completionHandler, getResourceOperationSpecs());
    }

    private ResourceOperationSpec[] getResourceOperationSpecs() {
        ResourceOperationSpec operationSpec1 = getResourceOperationSpec(ResourceOperation.RESTART,
                TargetCriteria.RESOURCE_POWER_STATE_ON.getCriteria());
        ResourceOperationSpec operationSpec2 = getResourceOperationSpec(ResourceOperation.SUSPEND,
                TargetCriteria.RESOURCE_POWER_STATE_ON.getCriteria());
        return new ResourceOperationSpec[] {operationSpec1, operationSpec2};
    }

    private ResourceOperationSpec getResourceOperationSpec(ResourceOperation operationType,
                                                           String targetCriteria) {
        ResourceOperationSpec spec = new ResourceOperationSpec();
        spec.adapterReference = AdapterUriUtil.buildAdapterUri(getHost(), SELF_LINK);
        spec.endpointType = EndpointType.azure.name();
        spec.resourceType = ResourceType.COMPUTE;
        spec.operation = operationType.operation;
        spec.name = operationType.displayName;
        spec.description = operationType.description;
        spec.targetCriteria = targetCriteria;
        return spec;
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

                        dh.restClient = AzureUtils.buildRestClient(credentials, this.executorService);

                        dh.azureClient = Azure.authenticate(dh.restClient, tenantId)
                                .withSubscription(c.parentAuth.userLink);
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
        dh.azureClient.virtualMachines().inner().restartAsync(dh.rgName, dh.vmName,
                new ServiceCallback<OperationStatusResponseInner>() {
                    @Override
                    public void failure(Throwable paramThrowable) {
                        logSevere(
                                "Error: Azure restart operation failed for resource %s in resourceGroup %s with error %s",
                                dh.vmName, dh.rgName, Utils.toString(paramThrowable));
                        ctx.taskManager.patchTaskToFailure(paramThrowable);
                        AzureUtils.cleanUpHttpClient(dh.restClient.httpClient());
                    }

                    @Override
                    public void success(OperationStatusResponseInner paramServiceResponse) {
                        logFine(
                                "Success: Azure restart operation for resource %s in resourceGroup %s completed successfully.",
                                dh.vmName, dh.rgName);
                        updateComputeState(dh, ctx);
                        AzureUtils.cleanUpHttpClient(dh.restClient.httpClient());
                    }
                });
    }

    private void suspend(AzureLifecycleOpDataHolder dh, DefaultAdapterContext ctx) {
        dh.azureClient.virtualMachines().inner().deallocateAsync(dh.rgName, dh.vmName,
                new ServiceCallback<OperationStatusResponseInner>() {
                    @Override
                    public void failure(Throwable paramThrowable) {
                        logSevere(
                                "Error: Azure deallocate operation failed for resource %s in resourceGroup %s with error %s",
                                dh.vmName, dh.rgName, Utils.toString(paramThrowable));
                        ctx.taskManager.patchTaskToFailure(paramThrowable);
                        AzureUtils.cleanUpHttpClient(dh.restClient.httpClient());
                    }

                    @Override
                    public void success(OperationStatusResponseInner paramServiceResponse) {
                        logFine(
                                "Success: Azure deallocate operation for resource %s in resourceGroup %s completed successfully.",
                                dh.vmName, dh.rgName);
                        updateComputeState(dh, ctx);
                        AzureUtils.cleanUpHttpClient(dh.restClient.httpClient());
                    }
                });
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
