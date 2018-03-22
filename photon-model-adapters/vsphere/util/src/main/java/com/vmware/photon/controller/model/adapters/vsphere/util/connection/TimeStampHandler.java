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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import javax.xml.bind.JAXBElement;
import javax.xml.soap.SOAPException;
import javax.xml.ws.handler.soap.SOAPMessageContext;

import org.apache.cxf.ws.security.sts.provider.model.utility.AttributedDateTime;
import org.apache.cxf.ws.security.sts.provider.model.utility.ObjectFactory;
import org.apache.cxf.ws.security.sts.provider.model.utility.TimestampType;
import org.w3c.dom.DOMException;
import org.w3c.dom.Node;

/**
 * Handler class to add the TimeStamp element inside the security header
 */
public class TimeStampHandler extends AbstractHeaderHandler {

    public static final String GMT = "GMT";

    public DateFormat dateFormatter;

    /**
     * Creates a datetime formatter needed for populating objects containing XML
     * requests/responses.
     */
    public static DateFormat createDateFormatter() {
        DateFormat dateFormat = new SimpleDateFormat(SamlUtils.XML_DATE_FORMAT);
        // always send UTC/GMT time
        dateFormat.setTimeZone(TimeZone.getTimeZone(TimeStampHandler.GMT));
        return dateFormat;
    }

    public DateFormat getDateFormatter() {
        if (this.dateFormatter == null) {
            this.dateFormatter = createDateFormatter();
        }
        return this.dateFormatter;
    }

    /**
     * Creates a timestamp WS-Security element. It is needed for the STS to tell
     * if the request is invalid due to slow delivery
     *
     * @return timestamp element issued with start date = NOW and expiration
     * date = NOW + REQUEST_VALIDITY_IN_MINUTES
     */
    private JAXBElement<TimestampType> createTimestamp() {
        ObjectFactory wssuObjFactory = new ObjectFactory();

        final long now = System.currentTimeMillis();
        Date createDate = new Date(now);
        Date expirationDate = new Date(
                now
                        + TimeUnit.MINUTES
                        .toMillis(SamlUtils.REQUEST_VALIDITY_IN_MINUTES));

        DateFormat wssDateFormat = getDateFormatter();
        AttributedDateTime createTime = wssuObjFactory
                .createAttributedDateTime();
        createTime.setValue(wssDateFormat.format(createDate));

        AttributedDateTime expirationTime = wssuObjFactory
                .createAttributedDateTime();
        expirationTime.setValue(wssDateFormat.format(expirationDate));

        TimestampType timestamp = wssuObjFactory.createTimestampType();
        timestamp.setCreated(createTime);
        timestamp.setExpires(expirationTime);
        return wssuObjFactory.createTimestamp(timestamp);
    }

    @Override
    public boolean handleMessage(SOAPMessageContext smc) {
        if (SamlUtils.isOutgoingMessage(smc)) {
            try {
                Node securityNode = SamlUtils.getOrCreateSecurityElement(SamlUtils
                        .getOrCreateSOAPHeader(smc));
                Node timeStampNode = SamlUtils.marshallJaxbElement(
                        createTimestamp()).getDocumentElement();
                securityNode.appendChild(securityNode.getOwnerDocument()
                        .importNode(timeStampNode, true));
                SamlUtils.addTimestampUuid(smc);
            } catch (DOMException e) {
                throw new RuntimeException(e);
            } catch (SOAPException e) {
                throw new RuntimeException(e);
            }
        }
        return true;
    }
}
