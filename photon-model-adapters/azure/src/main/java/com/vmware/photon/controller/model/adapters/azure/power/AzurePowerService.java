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

import java.util.concurrent.ExecutorService;

import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.credentials.AzureEnvironment;
import com.microsoft.azure.management.compute.ComputeManagementClient;
import com.microsoft.azure.management.compute.ComputeManagementClientImpl;
import com.microsoft.rest.ServiceCallback;
import com.microsoft.rest.ServiceResponse;

import okhttp3.OkHttpClient;

import retrofit2.Retrofit;

import com.vmware.photon.controller.model.adapterapi.ComputePowerRequest;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext.BaseAdapterStage;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext.DefaultAdapterContext;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.security.util.EncryptionUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.StatelessService;

/**
 * Adapter to power on/power off a VM instance on Azure.
 */
public class AzurePowerService extends StatelessService {

    public static final String SELF_LINK = AzureUriPaths.AZURE_POWER_ADAPTER;

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
        op.complete();
        if (pr.isMockRequest) {
            updateComputeState(pr, new DefaultAdapterContext(this, pr));
        } else {
            new DefaultAdapterContext(this, pr)
                    .populateBaseContext(BaseAdapterStage.VMDESC)
                    .whenComplete((c, e) -> {
                        if (e != null) {
                            c.taskManager.patchTaskToFailure(e);
                            this.logSevere(
                                    "Error while population base context during Azure power operation",
                                    e.getMessage());
                            return;
                        }
                        String clientId = c.parentAuth.privateKeyId;
                        String clientKey = EncryptionUtils.decrypt(c.parentAuth.privateKey);
                        String tenantId = c.parentAuth.customProperties
                                .get(AzureConstants.AZURE_TENANT_ID);

                        ApplicationTokenCredentials credentials = new ApplicationTokenCredentials(
                                clientId, tenantId, clientKey,
                                AzureEnvironment.AZURE);

                        OkHttpClient httpClient = new OkHttpClient();
                        OkHttpClient.Builder clientBuilder = httpClient.newBuilder();

                        ComputeManagementClient client = new ComputeManagementClientImpl(
                                AzureConstants.BASE_URI, credentials, clientBuilder,
                                getRetrofitBuilder());
                        client.setSubscriptionId(c.parentAuth.userLink);
                        applyPowerOperation(client, pr, c);
                    });
        }
    }

    private void applyPowerOperation(ComputeManagementClient client, ComputePowerRequest pr,
            DefaultAdapterContext c) {
        switch (pr.powerState) {
        case OFF:
            powerOff(client, pr, c);
            break;
        case ON:
            powerOn(client, pr, c);
            break;
        case UNKNOWN:
        default:
            c.taskManager.patchTaskToFailure(
                    new IllegalArgumentException("Unsupported power state transition requested."));
        }

    }

    private void powerOff(ComputeManagementClient client, ComputePowerRequest pr,
            DefaultAdapterContext c) {
        OperationContext opContext = OperationContext.getOperationContext();
        String vmName = c.child.name != null ? c.child.name : c.child.id;
        client.getVirtualMachinesOperations().powerOffAsync(getResourceGroupName(c), vmName,
                new ServiceCallback<Void>() {
                    @Override
                    public void failure(Throwable paramThrowable) {
                        OperationContext.restoreOperationContext(opContext);
                        c.taskManager.patchTaskToFailure(paramThrowable);
                    }

                    @Override
                    public void success(ServiceResponse<Void> paramServiceResponse) {
                        OperationContext.restoreOperationContext(opContext);
                        updateComputeState(pr, c);
                    }
                });
    }

    private void powerOn(ComputeManagementClient client, ComputePowerRequest pr,
            DefaultAdapterContext c) {
        OperationContext opContext = OperationContext.getOperationContext();
        String vmName = c.child.name != null ? c.child.name : c.child.id;
        client.getVirtualMachinesOperations().startAsync(getResourceGroupName(c), vmName,
                new ServiceCallback<Void>() {
                    @Override
                    public void failure(Throwable paramThrowable) {
                        OperationContext.restoreOperationContext(opContext);
                        c.taskManager.patchTaskToFailure(paramThrowable);
                    }

                    @Override
                    public void success(ServiceResponse<Void> paramServiceResponse) {
                        OperationContext.restoreOperationContext(opContext);
                        updateComputeState(pr, c);
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

    private void updateComputeState(ComputePowerRequest pr, DefaultAdapterContext c) {
        ComputeState state = new ComputeState();
        state.powerState = pr.powerState;
        Operation.createPatch(pr.resourceReference)
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