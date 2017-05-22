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

package com.vmware.photon.controller.model.adapters.vsphere.util.connection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.xml.ws.handler.Handler;
import javax.xml.ws.handler.HandlerResolver;
import javax.xml.ws.handler.PortInfo;
import javax.xml.ws.handler.soap.SOAPHandler;
import javax.xml.ws.handler.soap.SOAPMessageContext;

@SuppressWarnings("rawtypes")
public class HeaderHandlerResolver implements HandlerResolver {

    private final List<Handler> handlerChain = new ArrayList<Handler>();

    @Override
    public List<Handler> getHandlerChain(PortInfo arg0) {
        return Collections.unmodifiableList(this.handlerChain);
    }

    /**
     * Adds a specific {@link Handler} to the handler chain
     *
     * @param handler
     */
    public void addHandler(SOAPHandler<SOAPMessageContext> handler) {
        this.handlerChain.add(handler);
    }

    /**
     * Clears the current list of {@link Handler} in the handler chain
     */
    public void clearHandlerChain() {
        this.handlerChain.clear();
    }
}
