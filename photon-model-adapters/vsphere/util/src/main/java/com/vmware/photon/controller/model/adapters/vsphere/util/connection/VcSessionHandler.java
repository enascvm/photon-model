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

import java.util.Set;
import javax.xml.namespace.QName;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPHeader;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.handler.soap.SOAPHandler;
import javax.xml.ws.handler.soap.SOAPMessageContext;

import org.w3c.dom.DOMException;

/**
 * Handler class to add the vcSessionCookie element inside the soap header
 */
public final class VcSessionHandler implements SOAPHandler<SOAPMessageContext> {

    private final String vcSessionCookie;

    public VcSessionHandler(String vcSessionCookie) {
        this.vcSessionCookie = vcSessionCookie;
    }

    @Override
    public boolean handleMessage(SOAPMessageContext smc) {
        if (isOutgoingMessage(smc)) {
            try {
                SOAPHeader header = getSOAPHeader(smc);

                SOAPElement vcsessionHeader =
                        header.addChildElement(new javax.xml.namespace.QName("#",
                                "vcSessionCookie"));
                vcsessionHeader.setValue(this.vcSessionCookie);

            } catch (DOMException e) {
                throw new RuntimeException(e);
            } catch (SOAPException e) {
                throw new RuntimeException(e);
            }
        }
        return true;
    }

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

    /**
     * Returns the header. If not present then adds one and return the same
     */
    private SOAPHeader getSOAPHeader(SOAPMessageContext smc) throws SOAPException {
        return smc.getMessage().getSOAPPart().getEnvelope().getHeader() == null ? smc
                .getMessage().getSOAPPart().getEnvelope().addHeader()
                : smc.getMessage().getSOAPPart().getEnvelope().getHeader();
    }

    /**
     * Returns true if the {@link SOAPMessageContext} is part of the request
     */
    private boolean isOutgoingMessage(SOAPMessageContext smc) {
        Boolean outboundProperty =
                (Boolean) smc.get(MessageContext.MESSAGE_OUTBOUND_PROPERTY);
        return outboundProperty.booleanValue();
    }
}
