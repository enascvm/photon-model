/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.model.security.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.security.BaseTestCase;
import com.vmware.photon.controller.model.security.CommonTestStateFactory;
import com.vmware.photon.controller.model.security.service.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.photon.controller.model.security.util.CertificateUtil;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.UriUtils;

public class SslTrustCertificateServiceTest extends BaseTestCase {
    private String sslTrust1;
    private String sslTrust2;
    private SslTrustCertificateState sslTrustCert;

    @Before
    public void setUp() throws Throwable {
        this.sslTrust1 = CommonTestStateFactory.getFileContent("test_ssl_trust.PEM").trim();
        this.sslTrust2 = CommonTestStateFactory.getFileContent("test_ssl_trust2.PEM").trim();
        this.sslTrustCert = new SslTrustCertificateState();
        this.sslTrustCert.certificate = this.sslTrust1;

        this.host.startService(new SslTrustCertificateFactoryService());
        waitForServiceAvailability(SslTrustCertificateService.FACTORY_LINK);
    }

    @Test
    public void testPOSTandGET() throws Throwable {
        verifyService(
                FactoryService.create(SslTrustCertificateService.class),
                SslTrustCertificateState.class,
                (prefix, index) -> {
                    return this.sslTrustCert;
                },
                (prefix, serviceDocument) -> {
                    SslTrustCertificateState state = (SslTrustCertificateState) serviceDocument;
                    assertEquals(this.sslTrustCert.certificate, state.certificate);
                    validateCertProperties(state);
                });
    }

    @Test
    public void testValidateOnStart() throws Throwable {
        this.sslTrustCert.certificate = null;
        validateIllegalArgumentException(() -> {
            postForValidation(this.sslTrustCert);
        }, "certificate must not be null.");

        this.sslTrustCert.certificate = "invalid cert";
        try {
            postForValidation(this.sslTrustCert);
            fail();
        } catch (RuntimeException e) {

        }
    }

    @Test
    public void testPATCH() throws Throwable {
        this.sslTrustCert = doPost(this.sslTrustCert, SslTrustCertificateService.FACTORY_LINK);
        this.sslTrustCert.certificate = this.sslTrust2;

        boolean expectedFailure = false;
        URI uri = UriUtils.buildUri(this.host, this.sslTrustCert.documentSelfLink);
        doOperation(this.sslTrustCert, uri, expectedFailure, Action.PATCH);

        SslTrustCertificateState updatedSslTrustCert = getDocument(SslTrustCertificateState.class,
                this.sslTrustCert.documentSelfLink);

        assertEquals(this.sslTrust2, updatedSslTrustCert.certificate);
        validateCertProperties(updatedSslTrustCert);
    }

    @Test
    public void testPUT() throws Throwable {
        this.sslTrustCert = doPost(this.sslTrustCert, SslTrustCertificateService.FACTORY_LINK);
        this.sslTrustCert.certificate = this.sslTrust2;

        boolean expectedFailure = false;
        URI uri = UriUtils.buildUri(this.host, this.sslTrustCert.documentSelfLink);
        doOperation(this.sslTrustCert, uri, expectedFailure, Action.PUT);

        SslTrustCertificateState updatedSslTrustCert = getDocument(SslTrustCertificateState.class,
                this.sslTrustCert.documentSelfLink);

        assertEquals(this.sslTrust2, updatedSslTrustCert.certificate);
        validateCertProperties(updatedSslTrustCert);
    }

    @Test
    public void testIdempotentPOST() throws Throwable {
        SslTrustCertificateState sslTrustCert1 = new SslTrustCertificateState();
        sslTrustCert1.certificate = this.sslTrust1;
        sslTrustCert1.subscriptionLink = null;
        sslTrustCert1 = doPost(sslTrustCert1, SslTrustCertificateService.FACTORY_LINK);

        SslTrustCertificateState sslTrustCert2 = new SslTrustCertificateState();
        sslTrustCert2.certificate = this.sslTrust1;
        sslTrustCert2.subscriptionLink = "subscription-link";
        sslTrustCert2 = doPost(sslTrustCert2, SslTrustCertificateService.FACTORY_LINK);

        this.sslTrustCert = getDocument(SslTrustCertificateState.class,
                sslTrustCert1.documentSelfLink);

        /* We POST two different objects without explicitly setting the documentSelfLink, but these
         * objects have the same certificate. The factory will build the same documentSelfLink for
         * both of these objects and the idempotent option will turn the post to a put, so we expect
         * to have the subscriptionLink set after the POST */
        assertEquals(sslTrustCert2.subscriptionLink, this.sslTrustCert.subscriptionLink);
        validateCertProperties(this.sslTrustCert);
    }

    private void postForValidation(SslTrustCertificateState state) throws Throwable {
        URI uri = UriUtils.buildUri(this.host, SslTrustCertificateService.FACTORY_LINK);
        doOperation(state, uri, true, Action.POST);
    }

    private void validateCertProperties(SslTrustCertificateState state) throws Exception {
        X509Certificate[] certificates = CertificateUtil.createCertificateChain(state.certificate);

        for (X509Certificate cert : certificates) {
            cert.checkValidity();

            assertEquals(cert.getNotAfter(), new Date(TimeUnit.MICROSECONDS
                    .toMillis(state.documentExpirationTimeMicros)));
            assertEquals(CertificateUtil.getCommonName(cert.getSubjectDN()), state.commonName);
            assertEquals(CertificateUtil.getCommonName(cert.getIssuerDN()), state.issuerName);
            assertEquals(cert.getSerialNumber().toString(), state.serial);
            assertEquals(CertificateUtil.computeCertificateThumbprint(cert), state.fingerprint);
            assertEquals(cert.getNotBefore().getTime(), state.validSince);
            assertEquals(cert.getNotAfter().getTime(), state.validTo);
        }
    }
}
