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

package com.vmware.photon.controller.model.adapters.util.power;

import com.vmware.photon.controller.model.adapterapi.ComputePowerRequest;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;

/**
 * Base class for context used by power adapters. It handles power transitions and updates the
 * compute state.
 * <p>
 * To use the class override its abstract methods and call {@link #applyPowerOperation()} method.
 *
 * @param <CONTEXT> The derived context class.
 */
public abstract class BasePowerContext<CONTEXT extends BasePowerContext<CONTEXT>>
        extends BaseAdapterContext<CONTEXT> {

    public ComputePowerRequest request;

    /**
     * Constructs the {@link BasePowerContext}.
     *
     * @param service             The service that is creating and using this context.
     * @param computePowerRequest The power request.
     */
    public BasePowerContext(StatelessService service,
            ComputePowerRequest computePowerRequest) {
        super(service, computePowerRequest.resourceReference);
        this.request = computePowerRequest;
    }

    /**
     * Adapter specific logic to suspend a VM
     *
     * @return a {@link DeferredResult} to signal completion.
     */
    protected abstract DeferredResult<CONTEXT> suspend(CONTEXT context);

    /**
     * Adapter specific logic to power on a VM
     *
     * @return a {@link DeferredResult} to signal completion.
     */
    protected abstract DeferredResult<CONTEXT> powerOn(CONTEXT context);

    /**
     * Adapter specific logic to power off a VM
     *
     * @return a {@link DeferredResult} to signal completion.
     */
    protected abstract DeferredResult<CONTEXT> powerOff(CONTEXT context);

    /**
     * Extend this method for custom handling of mock request.
     * After this method's completion stage the compute state will be updated automatically
     */
    protected DeferredResult<CONTEXT> handleMockRequest(CONTEXT context) {
        return DeferredResult.completed(context);
    }

    /**
     * Applies the power state in the request.
     *
     * @return a {@link DeferredResult} to signal completion.
     */
    public final DeferredResult<CONTEXT> applyPowerOperation() {
        return populateBaseContext(BaseAdapterStage.VMDESC)
                .thenCompose(this::applyPowerOperation)
                .thenCompose(this::updateComputeState);

    }

    /**
     * Applies the power state in the request.
     * <p>
     * Calls the appropriate abstract method depending on the power state specified in the request
     *
     * @return a {@link DeferredResult} to signal completion.
     */
    private DeferredResult<CONTEXT> applyPowerOperation(CONTEXT context) {
        if (context.request.isMockRequest) {
            return handleMockRequest(context);
        }
        switch (context.request.powerState) {
        case OFF:
            return powerOff(context);
        case ON:
            return powerOn(context);
        case SUSPEND:
            return suspend(context);
        case UNKNOWN:
        default:
            return DeferredResult.failed(new IllegalArgumentException(
                    "Unsupported power state transition requested. State: "
                            + context.request.powerState));
        }
    }

    /**
     * Updates the compute state with the new power state
     */
    private DeferredResult<CONTEXT> updateComputeState(CONTEXT context) {
        ComputeState state = new ComputeState();
        state.powerState = context.request.powerState;

        Operation op = Operation.createPatch(context.request.resourceReference).setBody(state);

        return this.service.sendWithDeferredResult(op).thenApply(ignore -> context);
    }
}
