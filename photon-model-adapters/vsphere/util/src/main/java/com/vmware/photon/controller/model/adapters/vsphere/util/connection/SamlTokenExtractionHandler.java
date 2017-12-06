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

import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPException;
import javax.xml.ws.handler.soap.SOAPMessageContext;

import org.w3c.dom.Element;
import org.w3c.dom.Node;


/**
 * Handler class to extract the Saml token from the response stream in the raw
 * form before JAX-WS deserializes it. This is needed because the default
 * deserializer with JAX-WS does not maintain the line separators present inside
 * the token while deserializing and later serializing the SAML token. Thus we
 * have introduced this crude way to extracting the raw token to be used for
 * other operations
 */
public class SamlTokenExtractionHandler extends AbstractHeaderHandler {

    private Node token;

    @Override
    public boolean handleMessage(SOAPMessageContext smc) {
        if (!SamlUtils.isOutgoingMessage(smc)) {
            try {
                // Extract the Token
                SOAPBody responseBody = smc.getMessage().getSOAPBody();
                Node firstChild = responseBody.getFirstChild();
                if (firstChild != null
                        && "RequestSecurityTokenResponseCollection"
                        .equalsIgnoreCase(firstChild.getLocalName())) {
                    if (firstChild.getFirstChild() != null
                            && "RequestSecurityTokenResponse"
                            .equalsIgnoreCase(firstChild
                                    .getFirstChild().getLocalName())) {
                        Node rstrNode = firstChild.getFirstChild();
                        if (rstrNode.getFirstChild() != null
                                && "RequestedSecurityToken"
                                .equalsIgnoreCase(rstrNode
                                        .getFirstChild().getLocalName())) {
                            Node rstNode = rstrNode.getFirstChild();
                            if (rstNode.getFirstChild() != null
                                    && "Assertion".equalsIgnoreCase(rstNode
                                    .getFirstChild().getLocalName())) {
                                this.token = rstNode.getFirstChild();
                            }
                        }
                    }
                } else {
                    if (firstChild != null
                            && "RequestSecurityTokenResponse"
                            .equalsIgnoreCase(firstChild.getLocalName())) {
                        if (firstChild.getFirstChild() != null
                                && "RequestedSecurityToken"
                                .equalsIgnoreCase(firstChild
                                        .getFirstChild().getLocalName())) {
                            Node rstNode = firstChild.getFirstChild();
                            if (rstNode.getFirstChild() != null
                                    && "Assertion".equalsIgnoreCase(rstNode
                                    .getFirstChild().getLocalName())) {
                                this.token = rstNode.getFirstChild();
                            }
                        }
                    }
                }
            } catch (SOAPException e) {
                throw new RuntimeException(e);
            }
        }
        return true;
    }

    public Element getToken() {
        return (Element) this.token;
    }
}
