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

package com.vmware.photon.controller.model.util.callback;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.vmware.photon.controller.model.util.retry.Duration;
import com.vmware.photon.controller.model.util.retry.RetryDelay;
import com.vmware.photon.controller.model.util.retry.RetryPolicy;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.StatelessService;

/*
 * Async callback handler with retry using DeferredResult.
 * Retries are policy based using RetryPolicy.
 * Xenon service is used for retry scheduling and logging.
 */
public abstract class RetryableAsyncCallback<T> extends ContextAwareAsyncCallback<T> {

    protected final RetryPolicy retryPolicy;

    protected final String requestDescription;

    protected final StatelessService service;

    // External deferred result to signal completion for the waiting consumer
    private final DeferredResult<T> externalDeferredResult = new DeferredResult<>();

    // Internal deferred result to signal completion of retry attempts
    private final DeferredResult<T> retryDeferredResult = new DeferredResult<>();

    private RetryDelay retryDelay;

    public RetryableAsyncCallback(RetryPolicy retryPolicy, String requestDescription,
            StatelessService service) {
        this.retryPolicy = retryPolicy;
        this.requestDescription = requestDescription;
        this.service = service;

        this.retryDelay = new RetryDelay(retryPolicy);
    }

    protected void processCallback(T object, Throwable exception) {

        if (this.service != null) {
            this.service.logFine(() -> String.format("%s: SUCCESS", this.requestDescription));
        }

        final DeferredResult<T> processDeferredResult;

        // test result to one the following states: success, retry or fail out
        if (isSuccess(object, exception)) {
            processDeferredResult = DeferredResult.completed(object);
        } else if (canRetry(object, exception)) {
            processDeferredResult = doRetry();
        } else {
            processDeferredResult = DeferredResult.failed(exception);
        }

        // propagate result to the external DeferredResult
        processDeferredResult.whenComplete((body, exc) -> {
            if (exc != null) {
                this.externalDeferredResult.fail(exc);
            } else {
                this.externalDeferredResult.complete(body);
            }
        });
    }

    protected boolean canRetry(T object, Throwable exception) {
        if (this.retryPolicy.getMaxDuration() == null) {
            return false;
        }

        if (this.retryDelay.isTimedout()) {
            this.retryDeferredResult.fail(new IllegalStateException(String.format("%s: Max "
                            + "retries exceeded retry duration: %d",
                    this.requestDescription, this.retryDelay.getElapsedTime().toMillis())));
            return false;
        }

        if (this.retryPolicy.canRetryFor(object, exception)) {
            return true;
        }

        return false;
    }

    private DeferredResult<T> doRetry() {
        Duration sleepDuration = this.retryDelay.getNextDelay();

        this.service.logInfo(this.requestDescription + ": doRetry in (ms): " + sleepDuration.toMillis());

        this.service.getHost().schedule(
                getAsyncRunnable(getCallbackInstance()),
                sleepDuration.toMillis(),
                TimeUnit.MILLISECONDS);

        return this.retryDeferredResult;
    }

    private Runnable getAsyncRunnable(AsyncCallback<T> retryCallback) {
        return () -> getAsyncFunction().accept(retryCallback);
    }

    /**
     * Call this method to invoke the initial async operation.
     * @return
     */
    public DeferredResult<T> execute() {
        getAsyncRunnable(getCallbackInstance()).run();
        return this.externalDeferredResult;
    }

    /**
     * Override to provide a Consumer that invokes an async operation.
     * The subclass is responsible for mapping from subclass specific callback type to this generic
     * AsyncCallback type.
     */
    protected abstract Consumer<AsyncCallback<T>> getAsyncFunction();

    /**
     * Override to create and return callback handler for subclass
     */
    protected abstract AsyncCallback<T> getCallbackInstance();

    /**
     * Simple success test. Override to provide additional logic.
     */
    protected boolean isSuccess(T object, Throwable exception) {
        if (object != null && exception == null) {
            return true;
        }
        return false;
    }
}
