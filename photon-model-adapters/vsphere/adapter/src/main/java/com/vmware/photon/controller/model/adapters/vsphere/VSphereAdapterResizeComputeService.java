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

package com.vmware.photon.controller.model.adapters.vsphere;

import static java.lang.Math.toIntExact;

import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperation;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationRequest;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationUtils;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.vim25.InsufficientResourcesFaultFaultMsg;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.TaskInfoState;
import com.vmware.vim25.VirtualMachineConfigSpec;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;

/**
 * resize compute components(CPU/RAM) for a vSphere virtual machine
 */
public class VSphereAdapterResizeComputeService extends StatelessService {

    public static final String SELF_LINK = ResourceOperationSpecService.buildDefaultAdapterLink(
            PhotonModelConstants.EndpointType.vsphere.name(), ResourceOperationSpecService.ResourceType.COMPUTE,
            ResourceOperation.RESIZE.operation);

    /**
     * A key to store cpu count of Compute resource type used in resize operation
     */
    public static final String COMPUTE_CPU_COUNT = "__cpuCount";

    /**
     * A key to store Memory (in giga bytes) for Compute resource type used in resize operation
     */
    public static final String COMPUTE_MEMORY_IN_MBYTES = "__memoryInMBytes";

    /**
     * A flag stating whether the VM can be rebooted to perform the reconfigure operation or not.
     * If not supplied a default value of 'true' is assumed (since most cases hot-plug CPU/memory is not supported)
     */
    public static final String REBOOT_VM_FLAG = "__rebootMachine";

    private static final long MEGA_BYTES_TO_BYTES_CONSTANT = 1048576L; // 1024 * 1024 = 1048576
    private static final long SOFT_POWER_OFF_TIMEOUT_MICROS = 180000000L;

    @Override
    public void handleStart(Operation startPost) {
        Operation.CompletionHandler handler = (op, exc) -> {
            if (exc != null) {
                startPost.fail(exc);
            } else {
                startPost.complete();
            }
        };
        ResourceOperationUtils.registerResourceOperation(this, handler, getResourceOperationSpecs());
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

        TaskManager mgr = new TaskManager(this, request.taskReference, request.resourceLink());

        if (request.payload != null && !request.payload.isEmpty()) {
            logInfo("Handle operation %s for compute %s.", request.operation, request.resourceLink());
            final String newCpu = request.payload.get(COMPUTE_CPU_COUNT);
            final String newMemory = request.payload.get(COMPUTE_MEMORY_IN_MBYTES);
            final String rebootVMFlag = request.payload.get(REBOOT_VM_FLAG);

            if ((newCpu == null || newCpu.isEmpty()) && (newMemory == null || newMemory.isEmpty())) {
                //payload doesn't contain anything so patch task to FINISHED and return
                mgr.patchTask(TaskState.TaskStage.FINISHED);
                return;
            }

            VSphereVMContext vmContext = new VSphereVMContext(this, request);
            vmContext.pool = VSphereIOThreadPoolAllocator.getPool(this);

            VSphereVMContext.populateVMContextThen(this, vmContext, ctx -> {
                int newCpuCount;
                long newMemoryInMBytes;
                boolean rebootVM;

                try {
                    if (newCpu != null && !newCpu.isEmpty()) {
                        newCpuCount = Integer.parseInt(newCpu);
                    } else {
                        newCpuCount = toIntExact(ctx.child.description.cpuCount);
                    }
                    if (newMemory != null && !newMemory.isEmpty()) {
                        newMemoryInMBytes = Long.parseLong(newMemory);
                    } else {
                        newMemoryInMBytes = ctx.child.description.totalMemoryBytes / MEGA_BYTES_TO_BYTES_CONSTANT;
                    }
                } catch (NumberFormatException numberEx) { //NumberFormatException for all the parseXXX
                    logWarning(String.format("Request payload has values for CPU: %s or memory: %s in incorrect types "
                            + numberEx.getMessage(), newCpu, newMemory));
                    ctx.failWithMessage(String.format("Request payload has values for CPU: %s or memory: %s in incorrect types ",
                            newCpu, newMemory), numberEx);
                    return;
                }

                if (rebootVMFlag != null && !rebootVMFlag.isEmpty()) {
                    rebootVM = Boolean.valueOf(rebootVMFlag);
                } else {
                    rebootVM = true;
                }

                if (request.isMockRequest) {
                    patchComputeAndCompleteRequest(ctx, newCpuCount, newMemoryInMBytes);
                    return;
                }
                handleResourceOperationRequest(ctx, newCpuCount, newMemoryInMBytes, rebootVM);
            });
        } else {
            mgr.patchTaskToFailure(new IllegalArgumentException("Request payload is required for resize compute operation"));
        }
    }

    private void patchComputeAndCompleteRequest(VSphereVMContext ctx, final int cpuCount, final long memoryInMBytes) {
        ctx.child.description.cpuCount = Long.valueOf(cpuCount);
        ctx.child.description.totalMemoryBytes = memoryInMBytes * MEGA_BYTES_TO_BYTES_CONSTANT;
        Operation.createPatch(this, ctx.child.descriptionLink)
                .setBody(ctx.child.description)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        ctx.taskManager.patchTaskToFailure(e);
                        return;
                    }
                    // finish task
                    ctx.taskManager.finishTask();
                }).sendWith(this);
    }

    private void handleResourceOperationRequest(VSphereVMContext ctx, int cpuCount, long memoryInMBytes, boolean rebootVM) {
        ctx.pool.submit(this, ctx.getAdapterManagementReference(), ctx.parentAuth,
                (connection, ce) -> {
                    if (ctx.fail(ce)) {
                        return;
                    }

                    VirtualMachineConfigSpec spec = new VirtualMachineConfigSpec();
                    spec.setNumCPUs(cpuCount);
                    spec.setMemoryMB(memoryInMBytes);

                    ManagedObjectReference vmMoRef;
                    ManagedObjectReference task;
                    TaskInfo info;
                    try {
                        vmMoRef = CustomProperties.of(ctx.child).getMoRef(CustomProperties.MOREF);
                        PowerStateClient powerStateClient = new PowerStateClient(connection);
                        boolean isComputePowerStateOn = ComputeService.PowerState.ON.equals(ctx.child.powerState) ? true : false;
                        if (rebootVM && isComputePowerStateOn) {
                            // soft power-off the VM
                            powerStateClient.shutdownGuest(vmMoRef, Utils.getNowMicrosUtc() + SOFT_POWER_OFF_TIMEOUT_MICROS); //180000000 micro seconds (3 minutes)
                        }

                        // reconfigure VM with new CPU/memory values
                        task = connection.getVimPort().reconfigVMTask(vmMoRef, spec);
                        info = VimUtils.waitTaskEnd(connection, task);

                        if (rebootVM || isComputePowerStateOn) {
                            // power-on the VM back, since it was shutdown earlier
                            powerStateClient.changePowerState(vmMoRef, ComputeService.PowerState.ON, null, 0);
                        }
                        if (info.getState() != TaskInfoState.SUCCESS) {
                            VimUtils.rethrow(info.getError());
                        }
                    } catch (InsufficientResourcesFaultFaultMsg ex) {
                        ctx.failWithMessage("Cannot perform resize operation due to insufficient resources in host " +
                                "for compute resource " + ctx.child.name, ex);
                        return;
                    } catch (Exception ex) {
                        ctx.failWithMessage("Cannot perform resize operation for compute resource " + ctx.child.name, ex);
                        return;
                    }
                    patchComputeAndCompleteRequest(ctx, cpuCount, memoryInMBytes);
                });
    }

    private ResourceOperationSpecService.ResourceOperationSpec[] getResourceOperationSpecs() {
        ResourceOperationSpecService.ResourceOperationSpec resizeOperationSpec = getResourceOperationSpec(ResourceOperation.RESIZE, null);
        return new ResourceOperationSpecService.ResourceOperationSpec[]{resizeOperationSpec};
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
