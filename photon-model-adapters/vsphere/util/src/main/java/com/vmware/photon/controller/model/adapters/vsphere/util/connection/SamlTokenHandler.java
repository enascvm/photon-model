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

import javax.xml.soap.SOAPException;
import javax.xml.ws.handler.soap.SOAPMessageContext;

import org.w3c.dom.DOMException;
import org.w3c.dom.Node;

/**
 * Handler class to add the SAML token inside the security header
 */
public class SamlTokenHandler extends AbstractHeaderHandler {

    private final Node token;

    /**
     * @param token SAML token to be embedded
     */
    public SamlTokenHandler(Node token) {
        if (!SamlUtils.isSamlToken(token)) {
            throw new IllegalArgumentException(SamlUtils.ERR_NOT_A_SAML_TOKEN);
        }
        this.token = token;
    }

    @Override
    public boolean handleMessage(SOAPMessageContext smc) {
        if (SamlUtils.isOutgoingMessage(smc)) {
            try {
                Node securityNode = SamlUtils.getOrCreateSecurityElement(SamlUtils
                        .getOrCreateSOAPHeader(smc));
                securityNode.appendChild(securityNode.getOwnerDocument()
                        .importNode(this.token, true));
                SamlUtils.addSoapBodyUuid(smc);
            } catch (DOMException e) {
                throw new RuntimeException(e);
            } catch (SOAPException e) {
                throw new RuntimeException(e);
            }
        }
        return true;
    }
}
