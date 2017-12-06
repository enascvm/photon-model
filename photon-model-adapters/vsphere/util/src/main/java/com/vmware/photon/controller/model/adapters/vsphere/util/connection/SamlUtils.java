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

import java.io.IOException;
import java.io.StringReader;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPHeader;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.handler.soap.SOAPMessageContext;

import org.apache.cxf.ws.security.sts.provider.model.secext.ObjectFactory;
import org.apache.cxf.ws.security.sts.provider.model.secext.SecurityHeaderType;
import org.apache.cxf.ws.security.sts.provider.model.utility.TimestampType;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class SamlUtils {

    public static final String ERR_INSERTING_SECURITY_HEADER = "Error inserting Security header " +
            "into the SOAP message. Too many Security found.";
    public static final String ERR_NOT_A_SAML_TOKEN = "Token provided is not a SAML token";
    public static final String MARSHALL_EXCEPTION_ERR_MSG = "Error marshalling JAXB document";

    public static final int REQUEST_VALIDITY_IN_MINUTES = 10;

    public static final String SECURITY_ELEMENT_NAME = "Security";

    public static final String URN_OASIS_NAMES_TC_SAML_2_0_ASSERTION =
            "urn:oasis:names:tc:SAML:2.0:assertion";

    public static final String WSS_NS = "http://docs.oasis-open" +
            ".org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd";

    public static final String XML_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    static final ObjectFactory wsseObjFactory = new ObjectFactory();

    /**
     * Creates a SAML document based on the passed token
     */
    public static Document createSamlDocument(String token)
            throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        return dBuilder.parse(new InputSource(new StringReader(token)));
    }

    /**
     * Finds the Security element from the header. If not found then creates one
     * and returns the same
     *
     */
    public static Node getOrCreateSecurityElement(SOAPHeader header) {
        NodeList targetElement = header.getElementsByTagNameNS(
                WSS_NS, SECURITY_ELEMENT_NAME);
        if (targetElement == null || targetElement.getLength() == 0) {
            JAXBElement<SecurityHeaderType> value = wsseObjFactory
                    .createSecurity(wsseObjFactory.createSecurityHeaderType());
            Node headerNode = marshallJaxbElement(value).getDocumentElement();
            return header.appendChild(header.getOwnerDocument().importNode(
                    headerNode, true));
        } else if (targetElement.getLength() > 1) {
            throw new RuntimeException(ERR_INSERTING_SECURITY_HEADER);
        }
        return targetElement.item(0);
    }

    /**
     * Returns the header. If not present then adds one and return the same
     *
     */
    public static SOAPHeader getOrCreateSOAPHeader(SOAPMessageContext smc)
            throws SOAPException {
        SOAPEnvelope envelope = smc.getMessage().getSOAPPart().getEnvelope();
        return envelope.getHeader() == null ? envelope.addHeader() : envelope.getHeader();
    }

    /**
     * Returns true if the {@link SOAPMessageContext} is an outgoing message
     *
     */
    public static boolean isOutgoingMessage(SOAPMessageContext smc) {
        Boolean outboundProperty = (Boolean) smc
                .get(MessageContext.MESSAGE_OUTBOUND_PROPERTY);
        return outboundProperty;
    }

    /**
     * Performs an elementary test to check if the Node possibly represents a
     * SAML token.
     *
     */
    public static boolean isSamlToken(Node token) {
        return (URN_OASIS_NAMES_TC_SAML_2_0_ASSERTION.equalsIgnoreCase(token.getNamespaceURI()))
                && ("assertion".equalsIgnoreCase(token.getLocalName()));
    }

    /**
     * Marshall a jaxbElement into a Document
     *
     */
    public static final <T> Document marshallJaxbElement(
            JAXBElement<T> jaxbElement) {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document result = null;
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(TimestampType.class,
                    SecurityHeaderType.class);
            result = dbf.newDocumentBuilder().newDocument();
            jaxbContext.createMarshaller().marshal(jaxbElement, result);
        } catch (JAXBException jaxbException) {
            throw new RuntimeException(MARSHALL_EXCEPTION_ERR_MSG, jaxbException);
        } catch (ParserConfigurationException pce) {
            throw new RuntimeException(MARSHALL_EXCEPTION_ERR_MSG, pce);
        }

        return result;
    }

}
