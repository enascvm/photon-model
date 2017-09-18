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

package com.vmware.photon.controller.model.adapters.azure.power;

import static com.vmware.photon.controller.model.ComputeProperties.RESOURCE_GROUP_NAME;
import static com.vmware.photon.controller.model.resources.ComputeService.PowerState.OFF;

import java.util.concurrent.ExecutorService;

import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.compute.implementation.OperationStatusResponseInner;
import com.microsoft.rest.RestClient;

import com.vmware.photon.controller.model.adapterapi.ComputePowerRequest;
import com.vmware.photon.controller.model.adapters.azure.AzureAsyncCallback;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext.BaseAdapterStage;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext.DefaultAdapterContext;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.security.util.EncryptionUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;

/**
 * Adapter to power on/power off a VM instance on Azure.
 */
public class AzurePowerService extends StatelessService {

    public static final String SELF_LINK = AzureUriPaths.AZURE_POWER_ADAPTER;

    private static class AzurePowerDataHolder {
        final ComputePowerRequest pr;
        Azure azureClient;
        RestClient restClient;
        AzurePowerService service;
        String vmName;
        String rgName;

        public AzurePowerDataHolder(AzurePowerService service, ComputePowerRequest pr) {
            this.pr = pr;
            this.service = service;
        }
    }

    private ExecutorService executorService;

    public ApplicationTokenCredentials credentials;

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
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }

        ComputePowerRequest pr = op.getBody(ComputePowerRequest.class);
        AzurePowerDataHolder dh = new AzurePowerDataHolder(this, pr);
        op.complete();
        if (pr.isMockRequest) {
            updateComputeState(dh, new DefaultAdapterContext(this, pr));
        } else {
            new DefaultAdapterContext(this, pr)
                    .populateBaseContext(BaseAdapterStage.VMDESC)
                    .whenComplete((c, e) -> {
                        if (e != null) {
                            c.taskManager.patchTaskToFailure(e);
                            this.logSevere(
                                    "Error populating base context during Azure power state operation %s for resource %s failed with error %s",
                                    pr.powerState, pr.resourceReference, Utils.toString(e));
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
                        applyPowerOperation(dh, c);
                    });
        }
    }

    private void applyPowerOperation(AzurePowerDataHolder dh, DefaultAdapterContext c) {
        switch (dh.pr.powerState) {
        case OFF:
            powerOff(dh, c);
            break;
        case ON:
            powerOn(dh, c);
            break;
        case UNKNOWN:
        default:
            c.taskManager.patchTaskToFailure(
                    new IllegalArgumentException("Unsupported power state transition requested."));
        }
    }

    private void powerOff(AzurePowerDataHolder dh, DefaultAdapterContext c) {
        dh.azureClient.virtualMachines().inner().powerOffAsync(dh.rgName, dh.vmName,
                new AzureAsyncCallback<OperationStatusResponseInner>() {
                    @Override
                    public void onError(Throwable paramThrowable) {
                        c.taskManager.patchTaskToFailure(paramThrowable);
                        AzureUtils.cleanUpHttpClient(dh.restClient);
                    }

                    @Override
                    public void onSuccess(OperationStatusResponseInner paramServiceResponse) {
                        updateComputeState(dh, c);
                        AzureUtils.cleanUpHttpClient(dh.restClient);
                    }
                });
    }

    private void powerOn(AzurePowerDataHolder dh, DefaultAdapterContext c) {
        dh.azureClient.virtualMachines().inner().startAsync(dh.rgName, dh.vmName,
                new AzureAsyncCallback<OperationStatusResponseInner>() {
                        @Override
                    public void onError(Throwable paramThrowable) {
                            c.taskManager.patchTaskToFailure(paramThrowable);
                            AzureUtils.cleanUpHttpClient(dh.restClient);
                        }

                        @Override
                    public void onSuccess(OperationStatusResponseInner paramServiceResponse) {
                            updateComputeState(dh, c);
                            AzureUtils.cleanUpHttpClient(dh.restClient);
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

    private void updateComputeState(AzurePowerDataHolder dh, DefaultAdapterContext c) {
        ComputeState state = new ComputeState();
        state.powerState = dh.pr.powerState;
        if (OFF.equals(dh.pr.powerState)) {
            state.address = ""; //clear IP address in case of power-off
        }
        Operation.createPatch(dh.pr.resourceReference)
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
}