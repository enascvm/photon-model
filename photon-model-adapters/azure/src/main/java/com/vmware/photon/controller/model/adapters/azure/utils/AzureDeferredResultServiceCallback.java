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
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.NOT_FOUND;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.RESOURCE_GROUP_NOT_FOUND;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.RESOURCE_NOT_FOUND;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import com.microsoft.azure.CloudError;
import com.microsoft.azure.CloudException;

import com.vmware.photon.controller.model.adapters.azure.AzureAsyncCallback;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;

/**
 * Azure {@link com.microsoft.rest.ServiceCallback} that bridges to a {@link DeferredResult}.
 */
public abstract class AzureDeferredResultServiceCallback<RES> extends AzureAsyncCallback<RES> {

    /**
     * Default implementation which simple return the result by {@link #consumeSuccess(Object)} and
     * uses {@link #consumeError(Throwable) default exception handling}.
     */
    public static class Default<RES> extends AzureDeferredResultServiceCallback<RES> {

        public Default(StatelessService service, String message) {
            super(service, message);
        }

        @Override
        protected DeferredResult<RES> consumeSuccess(RES result) {
            return DeferredResult.completed(result);
        }
    }

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
     * A map of {@link CloudError} handlers stored by {@link CloudError#code()}:
     * <ul>
     * <li>{@code AzureConstants.INVALID_PARAMETER}</li>
     * <li>{@code AzureConstants.INVALID_RESOURCE_GROUP}</li>
     * </ul>
     *
     * @see #consumeError(Throwable)
     */
    protected final Map<String, BiFunction<Throwable, CloudError, Throwable>> cloudErrorHandlers;

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

        {
            this.cloudErrorHandlers = new HashMap<>();

            addRecoverFromCloudError(NOT_FOUND);
            addRecoverFromCloudError(RESOURCE_NOT_FOUND);
            addRecoverFromCloudError(RESOURCE_GROUP_NOT_FOUND);

            this.cloudErrorHandlers.put(INVALID_PARAMETER, (originalExc, cloudError) -> {
                String invalidParameterMsg = String.format(
                        "Invalid parameter. %s",
                        cloudError.message());

                return new IllegalStateException(invalidParameterMsg, originalExc);
            });

            this.cloudErrorHandlers.put(INVALID_RESOURCE_GROUP, (originalExc, cloudError) -> {
                String invalidParameterMsg = String.format(
                        "Invalid resource group parameter. %s",
                        cloudError.message());

                return new IllegalStateException(invalidParameterMsg, originalExc);
            });
        }
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
     * The default set of CloudErrors to recover from.
     * <ul>
     * <li>{@code AzureConstants.NOT_FOUND}</li>
     * <li>{@code AzureConstants.RESOURCE_NOT_FOUND}</li>
     * <li>{@code AzureConstants.RESOURCE_GROUP_NOT_FOUND}</li>
     * </ul>
     *
     * @see #consumeError(Throwable)
     */
    protected void addRecoverFromCloudError(String code) {
        this.cloudErrorHandlers.put(code, (originalExc, cloudError) -> RECOVERED);
    }

    /**
     * Hook that might be implemented by descendants to handle failed Azure call.
     *
     * <p>
     * Default implementation should be good enough for most of the cases. It provides a list of 1)
     * exceptions to recover from and 2) customizable CloudError handlers.
     *
     * @see #cloudErrorHandlers
     * @see #addRecoverFromCloudError(String)
     */
    protected Throwable consumeError(Throwable exc) {

        if (!(exc instanceof CloudException)) {
            return exc;
        }

        final CloudError cloudError = ((CloudException) exc).body();

        if (cloudError == null) {
            return exc;
        }

        BiFunction<Throwable, CloudError, Throwable> cloudErrorHandler = this.cloudErrorHandlers
                .getOrDefault(
                        // Lookup handler by code
                        cloudError.code(),
                        // Fall back to the default 're-throw' exc handler
                        (originalExc, cloudErr) -> originalExc);

        exc = cloudErrorHandler.apply(exc, cloudError);

        return exc;
    }

    @Override
    protected final void onError(final Throwable exc) {

        // First delegate to descendants to process exc

        final Throwable consumedError;
        try {
            consumedError = consumeError(exc);
        } catch (Throwable t) {
            if (this.service != null) {
                this.service.logWarning(() -> String
                        .format("%s: FAILED with %s", this.message, Utils.toString(t)));
            }
            toDeferredResult().fail(t);
            return;
        }

        // Then propagate through the DeferredResult

        if (consumedError == RECOVERED) {
            // The code has recovered from exception
            if (this.service != null) {
                this.service.logFine(() -> String.format("%s: SUCCESS with error. Details: %s",
                        this.message, Utils.toString(exc)));
            }
            toDeferredResult().complete(null);
        } else {
            if (this.service != null) {
                this.service.logWarning(() -> String.format("%s: FAILED with %s",
                        this.message, Utils.toString(consumedError)));
            }
            toDeferredResult().fail(consumedError);
        }
    }

    @Override
    protected final void onSuccess(RES result) {
        DeferredResult<RES> consumeSuccess;
        if (this.service != null) {
            this.service.logFine(() -> String.format("%s: SUCCESS", this.message));
        }
        try {
            // First delegate to descendants to process result
            consumeSuccess = consumeSuccess(result);
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
