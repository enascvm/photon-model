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

package com.vmware.photon.controller.model.adapters.azure.utils;

import java.util.concurrent.ExecutorService;

import com.vmware.photon.controller.model.adapterapi.ResourceRequest;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.StatelessService;

/**
 * Azure specific {@link BaseAdapterContext} impl which adds support for managing
 * {@link AzureSdkClients} upon authentication loading and context completion.
 */
public abstract class AzureBaseAdapterContext<T extends AzureBaseAdapterContext<T>>
        extends BaseAdapterContext<T>
        implements AutoCloseable {

    public AzureSdkClients azureSdkClients;

    protected final ExecutorService executorService;

    /**
     * Should be used if you want to create {@link AzureSdkClients} <b>manually</b>.
     */
    protected AzureBaseAdapterContext(
            StatelessService service,
            ResourceRequest resourceRequest) {

        this(service, null /* ExecutorService */, resourceRequest);
    }

    /**
     * Should be used if you want {@link AzureSdkClients} to be <b>auto</b> created as part of
     * {@link BaseAdapterContext#populateBaseContext(com.vmware.photon.controller.model.adapters.util.BaseAdapterContext.BaseAdapterStage)}
     * method using {@link BaseAdapterContext#parentAuth}.
     */
    protected AzureBaseAdapterContext(
            StatelessService service,
            ExecutorService executorService,
            ResourceRequest resourceRequest) {

        super(service, resourceRequest);

        this.executorService = executorService;
    }

    /**
     * Hook into parent context population and auto create {@link #azureSdkClients}.
     */
    @Override
    protected DeferredResult<T> customizeBaseContext(T context) {

        if (context.azureSdkClients == null
                && context.executorService != null
                && context.parentAuth != null) {

            context.azureSdkClients = new AzureSdkClients(
                    context.executorService,
                    context.parentAuth);
        }

        return DeferredResult.completed(context);
    }

    /**
     * Either finish with success or failure depending on passed {@code exc}.
     */
    public final void finish(Throwable exc) {
        if (exc == null) {
            finishSuccessfully();
        } else {
            finishExceptionally(exc);
        }
    }

    /**
     * Call {@code TaskManager.finishTask()} and {@link #close()} this context.
     */
    public void finishSuccessfully() {
        try {
            this.taskManager.finishTask();
        } finally {
            close();
        }
    }

    /**
     * Call {@code TaskManager.patchTaskToFailure(Throwable)} and {@link #close()} this context.
     */
    public void finishExceptionally(Throwable exc) {
        try {
            this.taskManager.patchTaskToFailure(exc);
        } finally {
            close();
        }
    }

    /**
     * Release internal {@link AzureSdkClients} instance.
     */
    @Override
    public final void close() {
        if (this.azureSdkClients != null) {
            this.azureSdkClients.close();
            this.azureSdkClients = null;
        }
    }

}
