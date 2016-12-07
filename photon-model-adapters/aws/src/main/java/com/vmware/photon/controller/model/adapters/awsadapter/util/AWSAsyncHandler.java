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
import com.amazonaws.handlers.AsyncHandler;

import com.vmware.xenon.common.OperationContext;

/**
 * Operation context aware {@link AsyncHandler}.
 */
public abstract class AWSAsyncHandler<REQ extends AmazonWebServiceRequest, RES>
        implements AsyncHandler<REQ, RES> {

    protected OperationContext opContext;

    protected AWSAsyncHandler() {
        this.opContext = OperationContext.getOperationContext();
    }

    /**
     * Invoked when an error happens during AWS async call.
     */
    protected abstract void handleError(Exception exception);

    /**
     * Invoked when AWS async call is successful.
     */
    protected abstract void handleSuccess(REQ request, RES result);

    @Override
    public final void onError(Exception exception) {
        OperationContext.restoreOperationContext(this.opContext);
        handleError(exception);
    }

    @Override
    public final void onSuccess(REQ request, RES result) {
        OperationContext.restoreOperationContext(this.opContext);
        handleSuccess(request, result);
    }

}
