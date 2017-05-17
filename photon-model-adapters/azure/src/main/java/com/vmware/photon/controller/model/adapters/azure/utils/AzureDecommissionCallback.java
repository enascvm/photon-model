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

import java.util.concurrent.TimeUnit;

import com.microsoft.rest.ServiceCallback;

import com.vmware.photon.controller.model.adapters.azure.AzureAsyncCallback;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.ExponentialRetryStrategy;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.RetryStrategy;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.StatelessService;

/**
 * Use this Azure callback in case of Azure decommission call (such as delete Resource Group) with a
 * method signature similar to:
 * {@code ServiceCall deleteAsync(..., ServiceCallback<Void> deleteCallback)}
 * <p>
 * This callback is responsible to wait until the resource does not exist on the Azure.
 */
public abstract class AzureDecommissionCallback extends AzureDeferredResultServiceCallback<Void> {

    private static final String MSG = "WAIT decommission to succeed";

    // Used by waitProvisioningToSucceed logic to signal completion
    private final DeferredResult<Void> waitDecommissionToSucceed = new DeferredResult<>();

    private final RetryStrategy retryStrategy;

    /**
     * Create a new {@link AzureDecommissionCallback}.
     */
    public AzureDecommissionCallback(StatelessService service, String message) {

        super(service, message);

        // It seems that 'delete' takes time so increase
        // maxDelayTime to 15secs and maxWaitTime to 20mins
        ExponentialRetryStrategy expRetryStrategy = new ExponentialRetryStrategy();
        expRetryStrategy.maxDelayMillis = TimeUnit.SECONDS.toMillis(15);
        expRetryStrategy.maxWaitMillis = TimeUnit.MINUTES.toMillis(20);

        this.retryStrategy = expRetryStrategy;
    }

    @Override
    protected final DeferredResult<Void> consumeSuccess(Void body) {
        // Assume the resource STILL EXISTs upon deleteAsync method completion
        final Boolean existence = true;
        return waitDecommissionToSucceed(existence).thenCompose(this::consumeDecommissionSuccess);
    }

    /**
     * Hook to be implemented by descendants to handle 'Succeeded' Azure resource decommission.
     * Since implementations might decide to trigger/initiate sync operation they are required to
     * return {@link DeferredResult} to track its completion.
     * <p>
     * This call is introduced by analogy with {@link #consumeSuccess(Object)}.
     */
    protected abstract DeferredResult<Void> consumeDecommissionSuccess(Void body);

    /**
     * This Runnable abstracts/models the Azure 'check resource existence' call.
     *
     * @param checkExistenceCallback
     *            The special callback that should be used while creating the Azure 'check resource
     *            existence' call.
     */
    protected abstract Runnable checkExistenceCall(
            ServiceCallback<Boolean> checkExistenceCallback);

    /**
     * The core logic that waits for decommission to succeed. It polls periodically Azure for
     * resource existence.
     */
    private DeferredResult<Void> waitDecommissionToSucceed(Boolean existence) {

        this.service.logFine(this.message + ": existence = " + existence);

        long nextDelayMillis = this.retryStrategy.nextDelayMillis();

        if (!existence) {

            // checkExistence has returned FALSE so resource has been decommissioned successfully
            // Completes 'waitDecommissionToSucceed' task with SUCCESS
            this.waitDecommissionToSucceed.complete((Void) null);

        } else if (nextDelayMillis == RetryStrategy.EXHAUSTED) {

            // Max number of re-tries has reached.
            // Completes 'waitDecommissionToSucceed' task with EXCEPTION.
            this.waitDecommissionToSucceed.fail(new IllegalStateException(
                    MSG + ": max wait time ("
                            + TimeUnit.MILLISECONDS.toSeconds(this.retryStrategy.maxWaitMillis)
                            + " secs) exceeded"));
        } else {
            // Retry one more time
            this.service.getHost().schedule(
                    checkExistenceCall(new CheckExistenceCallback()),
                    nextDelayMillis,
                    TimeUnit.MILLISECONDS);
        }

        return this.waitDecommissionToSucceed;
    }

    /**
     * Specialization of Azure callback used by {@link AzureDecommissionCallback} to handle 'check
     * resource existence' call. Should be passed to Azure SDK methods with a signature similar to:
     * {@code ServiceCall checkExistenceAsync(..., ServiceCallback<Boolean> checkExistenceCallback)}
     */
    private class CheckExistenceCallback extends AzureAsyncCallback<Boolean> {

        @Override
        public void onError(Throwable e) {
            e = new IllegalStateException(MSG + ": FAILED", e);

            AzureDecommissionCallback.this.waitDecommissionToSucceed.fail(e);
        }

        @Override
        public void onSuccess(Boolean result) {
            AzureDecommissionCallback.this
                    .waitDecommissionToSucceed(result);
        }
    }
}
