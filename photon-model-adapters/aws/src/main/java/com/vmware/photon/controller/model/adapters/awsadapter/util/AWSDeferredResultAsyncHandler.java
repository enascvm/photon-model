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

package com.vmware.photon.controller.model.adapters.awsadapter.util;

import com.amazonaws.AmazonWebServiceRequest;

import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;

/**
 * {@link AWSAsyncHandler} that bridges to {@link DeferredResult}. The benefit is that async
 * handling across Xenon, Azure, AWS, etc. is unified based on {@code DeferredResult}.
 */
public class AWSDeferredResultAsyncHandler<REQ extends AmazonWebServiceRequest, RES>
        extends AWSAsyncHandler<REQ, RES> {

    /**
     * Return this instance by {@link #consumeError(Exception)} to indicate that the descendant has
     * recovered from exception.
     */
    protected static final Exception RECOVERED = null;

    /**
     * The DeferredResult instance used to track this async callback.
     */
    private final DeferredResult<RES> deferredResult = new DeferredResult<>();

    /**
     * Return this async callback as DeferredResult instance.
     */
    public DeferredResult<RES> toDeferredResult() {
        return this.deferredResult;
    }

    /**
     * Hook that might be implemented by descendants to handle successful AWS call.
     * By default the AWS result is returned as {@link DeferredResult}.
     */
    protected DeferredResult<RES> consumeSuccess(REQ request, RES result) {
        return DeferredResult.completed(result);
    }

    /**
     * Hook that might be implemented by descendants to handle failed AWS call.
     *
     * @return The exception to propagate or {@link #RECOVERED} to indicate the handler has
     *         recovered from the exception.
     */
    protected Exception consumeError(Exception exception) {
        return exception;
    }

    protected final String message;

    /**
     * Constructs {@link AWSDeferredResultAsyncHandler}.
     *
     * @param service The service that is talking with AWS.
     * @param message Informational message that describes the Service to AWS interaction.
     */
    public AWSDeferredResultAsyncHandler(StatelessService service, String message) {
        super(service);
        this.message = message;
    }

    @Override
    protected final void handleError(Exception exc) {
        final Throwable consumedError;
        try {
            // First delegate to descendants to process exc
            consumedError = consumeError(exc);
        } catch (Throwable t) {
            if (this.service != null) {
                this.service.logWarning(() -> String.format("%s: FAILED with %s",
                        this.message, Utils.toString(t)));
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
    protected final void handleSuccess(REQ request, RES result) {
        final DeferredResult<RES> consumeSuccess;
        if (this.service != null) {
            this.service.logFine(() -> this.message + ": SUCCESS");
        }
        try {
            // First delegate to descendants to process result
            consumeSuccess = consumeSuccess(request, result);
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
