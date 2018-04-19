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

import com.vmware.xenon.common.OperationContext;

/**
 * Carries over the Xenon thread context to the callback handler thread.
 */
public abstract class ContextAwareAsyncCallback<T> implements AsyncCallback<T> {

    protected OperationContext opContext;

    public ContextAwareAsyncCallback() {
        this.opContext = OperationContext.getOperationContext();
    }

    @Override
    public final void accept(T object, Throwable exception) {
        OperationContext.restoreOperationContext(this.opContext);
        processCallback(object, exception);
    }

    /**
     * Override with work to do up on callback from an async operation.
     * @param object Expected object instance. Otherwise null.
     * @param exception Exception caught during async operation.
     */
    protected abstract void processCallback(T object, Throwable exception);
}
