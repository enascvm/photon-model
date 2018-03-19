/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.microsoft.azure.CloudError;
import com.microsoft.azure.CloudException;
import com.microsoft.rest.ServiceCallback;

import com.vmware.photon.controller.model.adapters.azure.AzureAsyncCallback;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.FixedRetryStrategy;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.RetryStrategy;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;

/**
 * Azure {@link com.microsoft.rest.ServiceCallback} that bridges to a {@link DeferredResult}.
 */
public abstract class AzureDeferredResultServiceCallbackWithRetry<RES> extends AzureAsyncCallback<RES> {

    private static final String MSG = "WAIT retry to succeed";

    private static List<String> retryableExceptionCodes = Arrays.asList("Conflict", "Canceled",
            "InProgress","StorageAccountOperationInProgress","RetryableError");

    // Used to signal completion of internal retry service calls
    protected DeferredResult<RES> waitRetryDr = new DeferredResult<>();

    protected final RetryStrategy retryStrategy;

    /**
     * The DeferredResult instance used to track this async callback.
     */
    private final DeferredResult<RES> deferredResult = new DeferredResult<>();

    /**
     * Informational message that describes the Service to Azure interaction.
     */
    protected final String message;

    /**
     * Constructs {@link AzureDeferredResultServiceCallbackWithRetry}.
     *
     * @param service
     *            The service that is talking with Azure.
     * @param message
     *            Informational message that describes the Service to Azure interaction.
     */
    public AzureDeferredResultServiceCallbackWithRetry(StatelessService service, String message) {
        super(service);
        this.message = message;

        this.retryStrategy = new FixedRetryStrategy();
        this.retryStrategy.delayMillis = TimeUnit.MILLISECONDS.toMillis(500);
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

    protected final DeferredResult<RES> consumeError(Throwable exc) {
        if (isRetryable(exc)) {
            return waitAndRetry();
        }
        return DeferredResult.failed(exc);
    }

    private boolean isRetryable(Throwable exc) {
        if (!(exc instanceof CloudException)) {
            return false;
        }

        final CloudError cloudError = ((CloudException) exc).body();
        if (cloudError == null) {
            return false;
        }

        if (retryableExceptionCodes.contains(cloudError.code())) {
            return true;
        }

        return false;
    }

    private DeferredResult<RES> waitAndRetry() {

        long nextDelayMillis = this.retryStrategy.nextDelayMillis();

        this.service.logInfo(this.message + ": waitAndRetry in (ms): " + nextDelayMillis);

        if (nextDelayMillis == RetryStrategy.EXHAUSTED) {
            // Max number of re-tries has reached.
            // Completes 'waitProvisioningToSucceed' task with exception.
            this.waitRetryDr.fail(new IllegalStateException(
                    MSG + ": max wait time ("
                            + TimeUnit.MILLISECONDS.toSeconds(this.retryStrategy.maxWaitMillis)
                            + " secs) exceeded"));
        } else {
            // Retry one more time
            this.service.getHost().schedule(
                    retryServiceCall(new RetryServiceCallback()),
                    nextDelayMillis,
                    TimeUnit.MILLISECONDS);
        }

        return this.waitRetryDr;
    }

    /**
     * Hook to be implemented by descendants to retry a failed Azure call.
     */
    protected abstract Runnable retryServiceCall(ServiceCallback<RES> retryCallback);

    @Override
    protected final void onError(final Throwable exc) {
        this.service.logWarning(() -> String
                .format("%s: onError with %s", this.message, exc));

        DeferredResult<RES> consumeErrorDr;

        try {
            consumeErrorDr = consumeError(exc);
        } catch (Throwable t) {
            if (this.service != null) {
                this.service.logSevere(() -> String
                        .format("%s: FAILED with %s", this.message, Utils.toString(t)));
            }
            toDeferredResult().fail(t);
            return;
        }

        consumeErrorDr.whenComplete((body, e) -> {
            if (exc != null) {
                toDeferredResult().fail(e);
            } else {
                toDeferredResult().complete(body);
            }
        });
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

    protected class RetryServiceCallback extends AzureAsyncCallback<RES> {
        @Override
        public void onError(Throwable e) {
            AzureDeferredResultServiceCallbackWithRetry.this.service.logWarning(() -> String
                    .format("%s: RetryServiceCallback.onError with %s",
                            AzureDeferredResultServiceCallbackWithRetry
                            .this.message, e));
            AzureDeferredResultServiceCallbackWithRetry.this.onError(e);
        }

        @Override
        public void onSuccess(RES result) {
            AzureDeferredResultServiceCallbackWithRetry.this.service.logInfo(() -> String
                    .format("%s: RetryServiceCallback.onSuccess with %s",
                            AzureDeferredResultServiceCallbackWithRetry
                                    .this.message, result));
            AzureDeferredResultServiceCallbackWithRetry.this.onSuccess(result);
        }
    }
}