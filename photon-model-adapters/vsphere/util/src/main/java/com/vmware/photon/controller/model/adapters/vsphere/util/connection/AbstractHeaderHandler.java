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

package com.vmware.photon.controller.model.adapters.vsphere.util.connection;

import java.util.Set;
import javax.xml.namespace.QName;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.handler.soap.SOAPHandler;
import javax.xml.ws.handler.soap.SOAPMessageContext;

/**
 * Base Abstract class that all the header handler will be extending.
 * <p>
 * <pre>
 * The reason for this abstract base class is two folds:
 * 1. Provide a common base class that can be utilized by the single
 * {@link HeaderHandlerResolver}
 * 2. The subclasses can simply implement the handleMessage() method instead to
 * creating dummy code for all the other methods as well
 * </pre>
 *
 */
public abstract class AbstractHeaderHandler implements
        SOAPHandler<SOAPMessageContext> {

    @Override
    public void close(MessageContext arg0) {
    }

    @Override
    public boolean handleFault(SOAPMessageContext arg0) {
        return false;
    }

    @Override
    public Set<QName> getHeaders() {
        return null;
    }

}