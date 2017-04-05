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

package com.vmware.photon.controller.model.adapters.azure.utils;

import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.INVALID_PARAMETER;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.INVALID_RESOURCE_GROUP;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.RESOURCE_NOT_FOUND;

import com.microsoft.azure.CloudError;
import com.microsoft.azure.CloudException;
import com.microsoft.rest.ServiceResponse;

import com.vmware.photon.controller.model.adapters.azure.AzureAsyncCallback;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.StatelessService;

/**
 * Azure {@link com.microsoft.rest.ServiceCallback} that bridges to a {@link DeferredResult}.
 */
public abstract class AzureDeferredResultServiceCallback<RES> extends AzureAsyncCallback<RES> {

    /**
     * Return this instance by {@link #consumeError(Throwable)} to indicate that the descendant has
     * recovered from exception.
     */
    protected static final Exception RECOVERED = null;

    /**
     * The DeferredResult instance used to track this async callback.
     */
    private final DeferredResult<RES> deferredResult = new DeferredResult<>();

    /**
     * Informational message that describes the Service to Azure interaction.
     */
    protected final String message;

    /**
     * Constructs {@link AzureDeferredResultServiceCallback}.
     *
     * @param service
     *            The service that is talking with Azure.
     * @param message
     *            Informational message that describes the Service to Azure interaction.
     */
    public AzureDeferredResultServiceCallback(StatelessService service, String message) {
        super(service);
        this.message = message;
    }

    /**
     * Return this async callback as DeferredResult instance.
     */
    public DeferredResult<RES> toDeferredResult() {
        return this.deferredResult;
    }

    /**
     * Hook to be implemented by descendants to handle successful Azure call.
     */
    protected abstract DeferredResult<RES> consumeSuccess(RES result);

    /**
     * Hook that might be implemented by descendants to handle failed Azure call.
     */
    protected Throwable consumeError(Throwable exc) {
        if (exc instanceof CloudException) {
            CloudException azureExc = (CloudException) exc;
            CloudError body = azureExc.getBody();
            if (body != null) {
                String code = body.getCode();
                if (RESOURCE_NOT_FOUND.equalsIgnoreCase(code)) {
                    return RECOVERED;
                } else if (INVALID_PARAMETER.equals(code) || INVALID_RESOURCE_GROUP.equals(code)) {
                    String invalidParameterMsg = String.format(
                            "Invalid parameter. %s",
                            body.getMessage());

                    IllegalStateException e = new IllegalStateException(invalidParameterMsg, exc);
                    return e;
                }
            }
        }
        return exc;
    }

    @Override
    protected final void onError(final Throwable exc) {
        final Throwable consumedError;

        // First delegate to descendants to process exc

        try {
            consumedError = consumeError(exc);
        } catch (Throwable t) {
            if (this.service != null) {
                this.service.logWarning(this.message + ": FAILED. Details: " + t.getMessage());
            }
            toDeferredResult().fail(t);
            return;
        }

        // Then propagate through the DeferredResult

        if (consumedError == RECOVERED) {
            // The code has recovered from exception
            if (this.service != null) {
                this.service.logFine(() -> String.format("%s: SUCCESS with error. Details: %s",
                        this.message, exc.getMessage()));
            }
            toDeferredResult().complete(null);
        } else {
            if (this.service != null) {
                this.service.logWarning(
                        this.message + ": FAILED. Details: " + consumedError.getMessage());
            }
            toDeferredResult().fail(consumedError);
        }
    }

    @Override
    protected final void onSuccess(ServiceResponse<RES> result) {
        DeferredResult<RES> consumeSuccess;
        if (this.service != null) {
            this.service.logFine(this.message + ": SUCCESS");
        }
        try {
            // First delegate to descendants to process result
            consumeSuccess = consumeSuccess(result.getBody());
        } catch (Throwable t) {
            toDeferredResult().fail(t);
            return;
        }

        // Then propagate through the DeferredResult
        consumeSuccess.whenComplete((body, exc) -> {
            if (exc != null) {
                toDeferredResult().fail(exc);
            } else {
                toDeferredResult().complete(body);
            }
        });
    }
}
