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

import java.net.URI;

import com.vmware.photon.controller.model.adapterapi.ComputePowerRequest;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.util.PhotonModelUriUtils;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationSequence;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState.TaskStage;

/**
 */
public class VSphereAdapterPowerService extends StatelessService {
    public static final String SELF_LINK = VSphereUriPaths.POWER_SERVICE;

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        ComputePowerRequest request = op.getBody(ComputePowerRequest.class);

        op.setStatusCode(Operation.STATUS_CODE_CREATED);
        op.complete();

        ProvisionContext.populateContextThen(this, createInitialContext(request), ctx -> {
            if (request.isMockRequest) {
                patchComputeAndCompleteRequest(request, ctx);
                return;
            }

            handlePowerRequest(ctx, request);
        });
    }

    private void handlePowerRequest(ProvisionContext ctx, ComputePowerRequest request) {
        ctx.pool.submit(ctx.getAdapterManagementReference(), ctx.vSphereCredentials,
                (connection, ce) -> {
                    if (ctx.fail(ce)) {
                        return;
                    }

                    PowerStateClient client = new PowerStateClient(connection);

                    ManagedObjectReference vmMoRef;
                    try {
                        vmMoRef = getVmMoRef(ctx.child);
                    } catch (Exception e) {
                        ctx.failWithMessage("Cannot extract MoRef from compute state", e);
                        return;
                    }

                    if (vmMoRef == null) {
                        ctx.failWithMessage("No VM MoRef found in compute state");
                        return;
                    }
                    PowerState currentState;
                    try {
                        currentState = client.getPowerState(vmMoRef);
                    } catch (Exception e) {
                        // probably should ignore this error and assume power state has to change
                        ctx.failWithMessage("Cannot get current power state of vmMoRef" + VimUtils
                                .convertMoRefToString(vmMoRef), e);
                        return;
                    }

                    if (currentState == request.powerState) {
                        ctx.mgr.finishTask();
                        return;
                    }

                    try {
                        long politenessDeadlineMicros = computeDeadline(ctx.task);

                        client.changePowerState(vmMoRef, request.powerState,
                                request.powerTransition, politenessDeadlineMicros);
                    } catch (Exception e) {
                        ctx.failWithMessage("cannot change power state of vmMoRef " + VimUtils
                                .convertMoRefToString(vmMoRef), e);
                        return;
                    }

                    patchComputeAndCompleteRequest(request, ctx);
                });
    }

    private long computeDeadline(ServiceDocument task) {
        // wait for soft power-off for as long as the task will be valid
        return task.documentExpirationTimeMicros;
    }

    private Operation patchComputeResource(ComputeState state, URI computeReference) {
        return Operation.createPatch(
                PhotonModelUriUtils.createInventoryUri(getHost(), computeReference))
                .setBody(state);
    }

    /**
     * Tries to extract the MoRef for the compute state. Fill fail or return null if no value found
     * in the customPropertoes for a well-known key.
     *
     * @param state
     * @return
     */
    private ManagedObjectReference getVmMoRef(ComputeStateWithDescription state) {
        return CustomProperties.of(state)
                .getMoRef(CustomProperties.MOREF);
    }

    private void patchComputeAndCompleteRequest(ComputePowerRequest request,
            ProvisionContext ctx) {
        // update just the power state
        ctx.child.powerState = request.powerState;
        if (PowerState.OFF.equals(request.powerState)) {
            ctx.child.address = ""; //set the IP address to empty in case of POWER OFF
        }
        Operation patchState = patchComputeResource(ctx.child, ctx.computeReference);

        // finish task
        Operation patchTask = ctx.mgr.createTaskPatch(TaskStage.FINISHED);

        OperationSequence.create(patchState)
                .next(patchTask)
                .setCompletion(ctx.failTaskOnError())
                .sendWith(this);
    }

    private ProvisionContext createInitialContext(ComputePowerRequest request) {
        ProvisionContext initialContext = new ProvisionContext(this,request);

        initialContext.pool = VSphereIOThreadPoolAllocator.getPool(this);
        return initialContext;
    }
}
