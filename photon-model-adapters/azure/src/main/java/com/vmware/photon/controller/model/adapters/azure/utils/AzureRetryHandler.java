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
import java.util.function.Consumer;

import com.microsoft.azure.CloudError;
import com.microsoft.azure.CloudException;
import com.microsoft.rest.ServiceCallback;

import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.util.callback.AsyncCallback;
import com.vmware.photon.controller.model.util.callback.RetryableAsyncCallback;
import com.vmware.photon.controller.model.util.retry.RetryPolicy;
import com.vmware.xenon.common.StatelessService;

/**
 * Azure specific retry handler receiving callback on Azure ServiceCallback and
 * funnels it to the generic callback retry handler
 */
public abstract class AzureRetryHandler<T> extends RetryableAsyncCallback<T> {

    public AzureRetryHandler(RetryPolicy retryPolicy, String requestDescription, StatelessService
            service) {
        super(retryPolicy, requestDescription, service);
    }

    public AzureRetryHandler(String requestDescription, StatelessService
            service) {
        super(getAzureRetryPolicy(), requestDescription, service);
    }

    @Override
    protected AzureAsyncCallback getCallbackInstance() {
        return new AzureAsyncCallback();
    }

    @Override
    protected final Consumer<AsyncCallback<T>> getAsyncFunction() {
        return (Consumer)getAzureAsyncFunction();
    }

    protected abstract Consumer<AzureAsyncCallback> getAzureAsyncFunction();

    public class AzureAsyncCallback implements ServiceCallback<T>, AsyncCallback<T> {

        @Override
        public void accept(T object, Throwable exception) {
            AzureRetryHandler.this.accept(object, exception);
        }

        public void failure(Throwable exception) {
            if (exception != null && exception instanceof CloudException) {
                service.logWarning("CloudException was caught. Message: " + exception.getMessage() +
                        " AzureErrorCode: " + getAzureErrorCode(exception));
            }
            accept(null, exception);
        }

        public void success(T object) {
            accept(object, null);
        }
    }

    private static List<String> retryableExceptionCodes = Arrays.asList("Conflict", "Canceled",
            "InProgress","StorageAccountOperationInProgress","RetryableError");

    /**
     * return a new instance of a RetryPolicy set with Azure specific policies.
     * Consumers can continue to modify it with additional policies.
     */
    public static RetryPolicy getAzureRetryPolicy() {
        RetryPolicy retryPolicy = new RetryPolicy()
                .withMaxDuration(AzureConstants.AZURE_REST_API_RETRY_MAX_DURATION, TimeUnit
                        .MINUTES)
                .withDelay(AzureConstants.AZURE_REST_API_RETRY_DELAY, TimeUnit.MILLISECONDS)
                .retryOn(NullPointerException.class)
                .retryIf((result, failure) -> {
                    String errorCode = getAzureErrorCode(failure);
                    if (errorCode != null && retryableExceptionCodes.contains(errorCode)) {
                        return true;
                    }
                    return false;
                });

        return retryPolicy;
    }

    /**
     *
     * @param failure Throwable to inspect for Azure error code
     * @return Azure error code string. Otherwise null.
     */
    public static String getAzureErrorCode(Throwable failure) {
        if (!(failure instanceof CloudException)) {
            return null;
        }
        CloudError cloudError = ((CloudException) failure).body();
        return cloudError.code();
    }
}
