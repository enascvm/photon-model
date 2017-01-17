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

package com.vmware.photon.controller.model.adapters.azure;

import com.microsoft.rest.ServiceCallback;
import com.microsoft.rest.ServiceResponse;

import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.StatelessService;

/**
 * Operation context aware service callback handler.
 */
public abstract class AzureAsyncCallback<T> extends ServiceCallback<T> {
    OperationContext opContext;
    protected StatelessService service;

    public AzureAsyncCallback(StatelessService service) {
        this();

        this.service = service;
    }

    public AzureAsyncCallback() {
        this.opContext = OperationContext.getOperationContext();
    }

    /**
     * Invoked when a failure happens during service call.
     */
    protected abstract void onError(Throwable e);

    /**
     * Invoked when a service call is successful.
     */
    protected abstract void onSuccess(ServiceResponse<T> result);

    @Override
    public final void failure(Throwable t) {
        OperationContext.restoreOperationContext(this.opContext);
        onError(t);
    }

    @Override
    public final void success(ServiceResponse<T> result) {
        OperationContext.restoreOperationContext(this.opContext);
        onSuccess(result);
    }
}
