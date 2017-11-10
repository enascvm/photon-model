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

package com.vmware.photon.controller.model.security.ssl;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.vmware.xenon.common.ServiceHost;

public class ServerX509TrustManagerTest {

    private static ServerX509TrustManager trustManager;

    @BeforeClass
    public static void setUp() throws Throwable {
        // Force a custom trust store... that shouldn't override the Java default cacerts.
        URI customStore = ServerX509TrustManagerTest.class
                .getResource("/certs/trusted_certificates.jks").toURI();
        File f = new File(customStore.getPath());
        System.setProperty(ServerX509TrustManager.JAVAX_NET_SSL_TRUST_STORE, f.getPath());
        System.setProperty(ServerX509TrustManager.JAVAX_NET_SSL_TRUST_STORE_PASSWORD, "changeit");

        // Fake host, not really needed for the purpose of the trust manager test.
        ServiceHost host = new ServiceHost() {

        };

        trustManager = ServerX509TrustManager.init(host);
    }

    @Test
    @Ignore
    public void testTrustedCertificates() throws Exception {

        // Validate a public certificate chain, e.g. the Docker registry one.
        // Is should work because the default cacerts from the JRE is always included and trusted

        trustManager.checkServerTrusted(getCertificates("/certs/docker.com.chain.crt"), "RSA");

        // Validate a custom certificate.
        // It should work because a truststore which contains the cert is passed as argument.

        trustManager.checkServerTrusted(getCertificates("/certs/trusted_server.crt"), "RSA");

        // Validate a custom certificate signed by a custom CA which is trusted.
        trustManager.checkServerTrusted(getCertificates("/certs/signed-server.crt"), "RSA");
    }

    @Test
    public void testUntrustedCertificates() throws Exception {

        // Validate a custom certificate.
        // Is should fail as it is signed by untrusted CA
        try {
            trustManager.checkServerTrusted(
                    getCertificates("/certs/untrusted-server.crt"), "RSA");
            fail("Should not trust untrusted certificate");
        } catch (CertificateException ignored) {
        }
    }

    @Test
    public void testInvalidate() {
        ServerX509TrustManager instance = ServerX509TrustManager.getInstance();
        Assert.assertNotNull(instance);
        ServerX509TrustManager.invalidate();
        instance = ServerX509TrustManager.getInstance();
        Assert.assertNull(instance);
    }

    private static X509Certificate[] getCertificates(String filename) throws Exception {
        URI customCertificate = ServerX509TrustManagerTest.class.getResource(filename).toURI();
        try (InputStream is = new FileInputStream(customCertificate.getPath())) {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            Collection<? extends Certificate> certificate = factory.generateCertificates(is);
            return certificate.toArray(new X509Certificate[] {});
        }
    }

}
