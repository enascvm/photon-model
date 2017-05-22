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

import static com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationUtils.TargetCriteria;

import java.util.Arrays;
import java.util.Collection;
import java.util.logging.Level;

import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperation;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationRequest;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationUtils;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;

/**
 * Day2 power operations service for vSphere provider
 * "Reboot" (restart guest OS), "Suspend" , "Shutdown" (guest OS), "Reset" operations
 */
public class VSphereAdapterD2PowerOpsService extends StatelessService {

    public static final String SELF_LINK = ResourceOperationSpecService.buildDefaultAdapterLink(
            PhotonModelConstants.EndpointType.vsphere.name(), ResourceOperationSpecService.ResourceType.COMPUTE,
            "d2PowerOps");

    private static final long SHUTDOWN_GUEST_OS_TIMEOUT = Long.parseLong(System.getProperty("vsphere.shutdown.guest.timeout",
            "180000")); // 180000L microseconds = 180 seconds (3 minutes)

    @Override
    public void handleStart(Operation startPost) {
        Operation.CompletionHandler handler = (op, exc) -> {
            if (exc != null) {
                startPost.fail(exc);
            } else {
                startPost.complete();
            }
        };
        ResourceOperationUtils.registerResourceOperation(this, getResourceOperationSpecs(), handler);
    }

    @Override
    public void handleStop(Operation stop) {
        super.handleStop(stop);
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        ResourceOperationRequest request = op.getBody(ResourceOperationRequest.class);
        op.complete();

        logInfo("Handle operation %s for compute %s.", request.operation, request.resourceLink());
        VSphereVMContext vmContext = new VSphereVMContext(this, request);
        vmContext.pool = VSphereIOThreadPoolAllocator.getPool(this);

        VSphereVMContext.populateVMContextThen(this, vmContext, ctx -> {
            if (request.isMockRequest) {
                patchComputeAndCompleteRequest(ctx, request.operation);
                return;
            }
            handleResourceOperationRequest(ctx, request.operation);
        });
    }

    private void patchComputeAndCompleteRequest(VSphereVMContext ctx, final String operation) {
        // update the power state
        if (ResourceOperation.REBOOT.operation.equalsIgnoreCase(operation)) {
            ctx.child.powerState = ComputeService.PowerState.ON;
        } else if (ResourceOperation.SUSPEND.operation.equalsIgnoreCase(operation)) {
            ctx.child.powerState = ComputeService.PowerState.SUSPEND;
        } else if (ResourceOperation.SHUTDOWN.operation.equalsIgnoreCase(operation)) {
            ctx.child.powerState = ComputeService.PowerState.OFF;
        } else if (ResourceOperation.RESET.operation.equalsIgnoreCase(operation)) {
            ctx.child.powerState = ComputeService.PowerState.ON;
        } else {
            final String warnStr = String.format("Operation %s not supported by this service %s",
                    operation, VSphereAdapterD2PowerOpsService.class.getSimpleName());
            log(Level.WARNING, warnStr);
            ctx.taskManager.patchTaskToFailure(new IllegalArgumentException(warnStr));
            return;
        }
        Operation.createPatch(ctx.resourceReference)
                .setBody(ctx.child)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        ctx.taskManager.patchTaskToFailure(e);
                        return;
                    }
                    // finish task
                    ctx.taskManager.finishTask();
                }).sendWith(this);
    }

    private void handleResourceOperationRequest(VSphereVMContext ctx, final String operation) {
        ctx.pool.submit(this, ctx.getAdapterManagementReference(), ctx.parentAuth,
                (connection, ce) -> {
                    if (ctx.fail(ce)) {
                        return;
                    }

                    PowerStateClient client = new PowerStateClient(connection);
                    ManagedObjectReference vmMoRef;
                    try {
                        vmMoRef = CustomProperties.of(ctx.child)
                                .getMoRef(CustomProperties.MOREF);
                    } catch (Exception ex) {
                        ctx.failWithMessage("Cannot extract MoRef from compute state", ex);
                        return;
                    }

                    if (vmMoRef == null) {
                        ctx.failWithMessage("No VM MoRef found in compute state to issue a reboot request");
                        return;
                    }

                    ComputeService.PowerState vmPowerState;
                    try {
                        vmPowerState = client.getPowerState(vmMoRef);
                    } catch (Exception ex) {
                        ctx.failWithMessage("Cannot get current power state of vmMoRef, hence operation cannot be performed" + VimUtils
                                .convertMoRefToString(vmMoRef), ex);
                        return;
                    }

                    if (!ComputeService.PowerState.ON.equals(vmPowerState)) {
                        ctx.failWithMessage(String.format("Current power state of vmMoRef is %s, and not ON, " +
                                "operation cannot be performed", vmPowerState.name()));
                    } else {
                        if (ResourceOperation.REBOOT.operation.equalsIgnoreCase(operation)) {
                            rebootVMOperation(client, vmMoRef, ctx, operation);
                        } else if (ResourceOperation.SUSPEND.operation.equalsIgnoreCase(operation)) {
                            suspendVMOperation(client, vmMoRef, ctx, operation);
                        } else if (ResourceOperation.SHUTDOWN.operation.equalsIgnoreCase(operation)) {
                            shutdownGuestOSOperation(client, vmMoRef, ctx, operation);
                        } else if (ResourceOperation.RESET.operation.equalsIgnoreCase(operation)) {
                            resetVMOperation(client, vmMoRef, ctx, operation);
                        }
                    }
                });
    }

    private void rebootVMOperation(PowerStateClient client, ManagedObjectReference vmMoRef,
                                   VSphereVMContext ctx, String operation) {
        try {
            client.rebootVM(vmMoRef);
            // complete the request
            patchComputeAndCompleteRequest(ctx, operation);
        } catch (Exception ex) {
            ctx.failWithMessage("Cannot reboot vm with vmMoRef " + VimUtils.convertMoRefToString(vmMoRef), ex);
        }
    }

    private void suspendVMOperation(PowerStateClient client, ManagedObjectReference vmMoRef,
                                    VSphereVMContext ctx, String operation) {
        try {
            client.suspendVM(vmMoRef, SHUTDOWN_GUEST_OS_TIMEOUT);
            // complete the request
            patchComputeAndCompleteRequest(ctx, operation);
        } catch (Exception ex) {
            ctx.failWithMessage("Cannot suspend vm with vmMoRef " + VimUtils
                    .convertMoRefToString(vmMoRef), ex);
        }
    }

    private void shutdownGuestOSOperation(PowerStateClient client, ManagedObjectReference vmMoRef,
                                          VSphereVMContext ctx, String operation) {
        try {
            client.shutdownGuest(vmMoRef, SHUTDOWN_GUEST_OS_TIMEOUT);
            patchComputeAndCompleteRequest(ctx, operation);
        } catch (Exception ex) {
            ctx.failWithMessage("Cannot shutdown guest OS on vm with vmMoRef " + VimUtils
                    .convertMoRefToString(vmMoRef), ex);
        }
    }

    private void resetVMOperation(PowerStateClient client, ManagedObjectReference vmMoRef,
                                  VSphereVMContext ctx, String operation) {
        try {
            client.resetVM(vmMoRef);
            patchComputeAndCompleteRequest(ctx, operation);
        } catch (Exception ex) {
            ctx.failWithMessage("Cannot reset VM with vmMoRef " + VimUtils
                    .convertMoRefToString(vmMoRef), ex);
        }
    }

    private Collection<ResourceOperationSpecService.ResourceOperationSpec> getResourceOperationSpecs() {
        ResourceOperationSpecService.ResourceOperationSpec operationSpec1 = getResourceOperationSpec(ResourceOperation.REBOOT,
                TargetCriteria.RESOURCE_POWER_STATE_ON.getCriteria());
        ResourceOperationSpecService.ResourceOperationSpec operationSpec2 = getResourceOperationSpec(ResourceOperation.SUSPEND,
                TargetCriteria.RESOURCE_POWER_STATE_ON.getCriteria());
        ResourceOperationSpecService.ResourceOperationSpec operationSpec3 = getResourceOperationSpec(ResourceOperation.SHUTDOWN,
                TargetCriteria.RESOURCE_POWER_STATE_ON.getCriteria());
        ResourceOperationSpecService.ResourceOperationSpec operationSpec4 = getResourceOperationSpec(ResourceOperation.RESET,
                TargetCriteria.RESOURCE_POWER_STATE_ON.getCriteria());
        return Arrays.asList(operationSpec1, operationSpec2, operationSpec3, operationSpec4);
    }

    private ResourceOperationSpecService.ResourceOperationSpec getResourceOperationSpec(ResourceOperation operationType,
                                                                                        String targetCriteria) {
        ResourceOperationSpecService.ResourceOperationSpec spec = new ResourceOperationSpecService.ResourceOperationSpec();
        spec.adapterReference = AdapterUriUtil.buildAdapterUri(getHost(), SELF_LINK);
        spec.endpointType = PhotonModelConstants.EndpointType.vsphere.name();
        spec.resourceType = ResourceOperationSpecService.ResourceType.COMPUTE;
        spec.operation = operationType.operation;
        spec.name = operationType.displayName;
        spec.description = operationType.description;
        spec.targetCriteria = targetCriteria;
        return spec;
    }
}
