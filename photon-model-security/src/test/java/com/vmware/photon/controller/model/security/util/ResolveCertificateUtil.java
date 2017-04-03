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

package com.vmware.photon.controller.model.security.util;

import static junit.framework.TestCase.assertTrue;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.URI;
import java.security.cert.CertificateException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.security.ssl.ServerX509TrustManager;
import com.vmware.photon.controller.model.security.ssl.X509TrustManagerResolver;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.test.VerificationHost;

public class ResolveCertificateUtil {
    private static final Logger logger = Logger.getLogger(ResolveCertificateUtil.class.getName());

    @Before
    public void setUp() throws Throwable {
        VerificationHost HOST = VerificationHost.create(Integer.valueOf(0));
        HOST.setMaintenanceIntervalMicros(TimeUnit.MILLISECONDS.toMicros(250L));
        CommandLineArgumentParser.parseFromProperties(HOST);
        HOST.setStressTest(HOST.isStressTest);

        HOST.start();

        ServerX509TrustManager.init(HOST);
    }

    @Test
    public void resolveTrusted() {
        X509TrustManagerResolver resolver = CertificateUtil.resolveCertificate(
                URI.create("https://www.verisign.com/"),
                5000);
        assertTrue(resolver.isCertsTrusted());
        assertNotNull(resolver.getCertificate());
        assertNull(resolver.getCertificateException());
    }

    @Test
    public void resolveSelfSigned() {
        // self-signed cert - should not be trusted
        assertUntrustedCert("https://self-signed.badssl.com/");
    }

    @Test
    public void resolveExpired() {
        // expired cert - should not be trusted
        assertUntrustedCert("https://expired.badssl.com/");
    }

    @Test
    public void resolveWrongHost() {
        // wrong host - should not be trusted
        assertUntrustedCert("https://wrong.host.badssl.com/");
    }

    @Test
    public void resolveUntrustedRoot() {
        // untrusted root - should not be trusted
        assertUntrustedCert("https://untrusted-root.badssl.com/");
    }

    @Test
    public void resolveRevoked() {
        // revoked cert - should not be trusted
        assertUntrustedCert("https://revoked.badssl.com/");
    }

    @Test
    public void resolvePinned() {
        // pinned cert - should not be trusted
        assertUntrustedCert("https://pinning-test.badssl.com/");
    }

    @Test
    public void resolveSelfSignedBehindHttpProxy() {
        String uri = "https://self-signed.badssl.com/";
        X509TrustManagerResolver resolver = CertificateUtil.resolveCertificate(
                URI.create(uri),
                new Proxy(Type.HTTP, new InetSocketAddress("proxy.vmware.com", 3128)),
                null, null,
                5000L);
        assertTrustManagerResolver(uri, resolver);
    }

    @Test(expected = LocalizableValidationException.class)
    public void resolveSelfSignedBehindSocksProxy_neg() {
        String uri = "https://self-signed.badssl.com/";
        X509TrustManagerResolver resolver = CertificateUtil.resolveCertificate(
                URI.create(uri),
                new Proxy(Type.SOCKS, new InetSocketAddress("proxy.vmware.com", 3128)),
                "user", "pass",
                5000L);
        assertTrustManagerResolver(uri, resolver);
    }

    private void assertUntrustedCert(String uri) {
        X509TrustManagerResolver trustManagerResolver = CertificateUtil.resolveCertificate(
                URI.create(uri), 5000L);
        assertTrustManagerResolver(uri, trustManagerResolver);
    }

    private void assertTrustManagerResolver(String uri,
            X509TrustManagerResolver trustManagerResolver) {
        assertFalse(trustManagerResolver.isCertsTrusted());
        assertNotNull(trustManagerResolver.getCertificate());
        CertificateException certException = trustManagerResolver.getCertificateException();
        assertNotNull(certException);
        logger.info(
                "verify:" + uri
                        + ", error: " + certException.getMessage()
                        + ", cause: " + certException.getCause().getMessage());
    }

}
