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

package com.vmware.photon.controller.model.adapters.vsphere.network;

import com.vmware.photon.controller.model.adapterapi.NetworkInstanceRequest;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.adapters.vsphere.VSphereIOThreadPool;
import com.vmware.photon.controller.model.adapters.vsphere.VSphereIOThreadPoolAllocator;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.StatelessService;

/**
 */
public abstract class BaseVsphereNetworkProvisionFlow {

    private final StatelessService service;

    private final NetworkInstanceRequest request;

    private final OperationContext operationContext;

    private final TaskManager taskManager;

    public BaseVsphereNetworkProvisionFlow(
            StatelessService service,
            NetworkInstanceRequest req,
            OperationContext operationContext) {
        this.service = service;
        this.request = req;
        this.operationContext = operationContext;
        this.taskManager = new TaskManager(service, req.taskReference);
    }

    public BaseVsphereNetworkProvisionFlow(StatelessService service, NetworkInstanceRequest req) {
        this(service, req, OperationContext.getOperationContext());
    }

    public StatelessService getService() {
        return this.service;
    }

    protected VSphereIOThreadPool getVsphereIoPool() {
        return VSphereIOThreadPoolAllocator.getPool(getService());
    }

    protected NetworkInstanceRequest getRequest() {
        return this.request;
    }

    protected TaskManager getTaskManager() {
        return this.taskManager;
    }

    protected OperationContext getOperationContext() {
        return this.operationContext;
    }

    /**
     * Starts provisioning flow asynchronously.
     * On error the tasks is patched to FAILED.
     */
    public final void provisionAsync() {
        DeferredResult<Void> start = DeferredResult.completed(null);
        prepare(start)
                .exceptionally(e -> {
                    getTaskManager().patchTaskToFailure(e);
                    return null;
                });
    }

    protected abstract DeferredResult<?> prepare(DeferredResult<Void> start);
}
