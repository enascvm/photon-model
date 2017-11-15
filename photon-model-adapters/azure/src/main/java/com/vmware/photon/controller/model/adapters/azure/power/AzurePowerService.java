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

import static com.vmware.photon.controller.model.resources.ComputeService.PowerState.OFF;

import com.microsoft.azure.management.compute.implementation.OperationStatusResponseInner;

import com.vmware.photon.controller.model.adapterapi.ComputePowerRequest;
import com.vmware.photon.controller.model.adapters.azure.AzureAsyncCallback;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureBaseAdapterContext;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext.BaseAdapterStage;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;

/**
 * Adapter to power on/power off a VM instance on Azure.
 */
public class AzurePowerService extends StatelessService {

    public static final String SELF_LINK = AzureUriPaths.AZURE_POWER_ADAPTER;

    private static class AzurePowerContext
            extends AzureBaseAdapterContext<AzurePowerContext> {

        final ComputePowerRequest request;

        String vmName;
        String rgName;

        private AzurePowerContext(
                AzurePowerService service,
                ComputePowerRequest request) {

            super(service, request);

            this.request = request;
        }
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }

        ComputePowerRequest request = op.getBody(ComputePowerRequest.class);

        op.complete();

        AzurePowerContext ctx = new AzurePowerContext(this, request);

        if (request.isMockRequest) {
            updateComputeState(ctx);
        } else {
            ctx.populateBaseContext(BaseAdapterStage.VMDESC)
                    .whenComplete((ignoreCtx, e) -> {
                        if (e != null) {
                            logSevere(
                                    "Error populating base context during Azure power state operation %s for resource %s failed with error %s",
                                    request.powerState, request.resourceReference,
                                    Utils.toString(e));

                            ctx.finishExceptionally(e);

                            return;
                        }

                        ctx.vmName = ctx.child.name != null ? ctx.child.name : ctx.child.id;
                        ctx.rgName = AzureUtils.getResourceGroupName(ctx);

                        applyPowerOperation(ctx);
                    });
        }
    }

    private void applyPowerOperation(AzurePowerContext ctx) {
        switch (ctx.request.powerState) {
        case OFF:
            powerOff(ctx);
            break;
        case ON:
            powerOn(ctx);
            break;
        case UNKNOWN:
        default:
            ctx.finishExceptionally(
                    new IllegalArgumentException("Unsupported power state transition requested."));
        }
    }

    private void powerOff(AzurePowerContext ctx) {
        ctx.azureSdkClients.getAzureClient().virtualMachines().inner().powerOffAsync(
                ctx.rgName,
                ctx.vmName,
                new AzureAsyncCallback<OperationStatusResponseInner>() {
                    @Override
                    public void onError(Throwable paramThrowable) {
                        ctx.finishExceptionally(paramThrowable);
                    }

                    @Override
                    public void onSuccess(OperationStatusResponseInner paramServiceResponse) {
                        updateComputeState(ctx);
                    }
                });
    }

    private void powerOn(AzurePowerContext ctx) {
        ctx.azureSdkClients.getAzureClient().virtualMachines().inner().startAsync(
                ctx.rgName,
                ctx.vmName,
                new AzureAsyncCallback<OperationStatusResponseInner>() {
                    @Override
                    public void onError(Throwable paramThrowable) {
                        ctx.finishExceptionally(paramThrowable);
                    }

                    @Override
                    public void onSuccess(OperationStatusResponseInner paramServiceResponse) {
                        updateComputeState(ctx);
                    }
                });
    }

    private void updateComputeState(AzurePowerContext ctx) {
        ComputeState state = new ComputeState();
        state.powerState = ctx.request.powerState;
        if (OFF.equals(ctx.request.powerState)) {
            state.address = ""; // clear IP address in case of power-off
        }
        Operation.createPatch(ctx.request.resourceReference)
                .setBody(state)
                .setCompletion((o, e) -> ctx.finish(e))
                .sendWith(this);
    }
}