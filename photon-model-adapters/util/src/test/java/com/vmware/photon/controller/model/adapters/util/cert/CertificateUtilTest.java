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

package com.vmware.photon.controller.model.adapters.util.cert;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.security.cert.X509Certificate;
import java.util.Properties;
import java.util.Scanner;

import org.junit.BeforeClass;
import org.junit.Test;

import com.vmware.photon.controller.model.adapters.util.cert.CertificateUtil.ThumbprintAlgorithm;
import com.vmware.xenon.common.Utils;

/**
 * Test {@link CertificateUtil} methods
 */
public class CertificateUtilTest {
    private static final String PROPERTIES_FILE_NAME = "CertificateUtilTest.properties";
    private static final String VALID_CERT_PROP_NAME = "cert.valid";
    private static final String INVALID_CERT_PROP_NAME = "cert.invalid";
    public static final String CERTS_CA_PEM = "certs/ca.pem";
    public static final String CERTS_CHAIN_PEM = "certs/chain.pem";
    public static final String RSA = "RSA";

    private static Properties testProperties = new Properties();

    @BeforeClass
    public static void loadProperties() {
        testProperties = new Properties();

        try (InputStream in = CertificateUtilTest.class.getClassLoader().getResourceAsStream(
                PROPERTIES_FILE_NAME)) {

            if (in == null) {
                fail("Test input properties file missing: " + PROPERTIES_FILE_NAME);
            }
            testProperties.load(in);

        } catch (IOException e) {
            fail("Failed to read properties file with test input: " + PROPERTIES_FILE_NAME + ", "
                    + e.getMessage());
        }
    }

    @Test
    public void testCreateCertificateValid() {
        createCertificate(VALID_CERT_PROP_NAME);
    }

    @Test(expected = RuntimeException.class)
    public void testCreateCertificateInvalid() {
        createCertificate(INVALID_CERT_PROP_NAME);
    }

    @Test
    public void testComputeThumbprint() throws Exception {
        X509Certificate cert = createValidCertificate();
        String thumbprint = CertificateUtil.computeCertificateThumbprint(cert);
        assertNotNull(thumbprint);
        assertTrue(ThumbprintAlgorithm.SHA_1.isValid(thumbprint)
                || ThumbprintAlgorithm.SHA_256.isValid(thumbprint));
    }

    @Test
    public void testGetAttributeFromDN() throws Exception {
        String subjectDN = "CN=vcac.eng.vmware.com, OU=R&D, O=VMware,C=US";

        assertEquals("vcac.eng.vmware.com", CertificateUtil.getAttributeFromDN(subjectDN, "CN"));
        assertEquals("R&D", CertificateUtil.getAttributeFromDN(subjectDN, "OU"));
        assertEquals("VMware", CertificateUtil.getAttributeFromDN(subjectDN, "O"));
        assertEquals("US", CertificateUtil.getAttributeFromDN(subjectDN, "C"));
    }

    public void testGetAttributeFromDNWithInvalidAttr() {
        String subjectDN = "CN=vcac.eng.vmware.com, OU=R&D, O=VMware,C=US";

        // ST attribute is invalid
        String attr = CertificateUtil.getAttributeFromDN(subjectDN, "ST");
        assertNull(attr);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetAttributeFromDNWithInvalidDN() {
        String subjectDN = "InvalidDn";

        CertificateUtil.getAttributeFromDN(subjectDN, "ST");
    }

    @Test
    public void testGetCertificateCommonName() throws Exception {
        X509Certificate cert = createValidCertificate();
        String commonName = CertificateUtil.getCommonName(cert.getSubjectDN());
        assertNotNull(commonName);

        commonName = CertificateUtil.getCommonName(cert.getIssuerDN());
        assertNotNull(commonName);
    }

    @Test
    public void testSerializeCertificateToPEMformat() throws Exception {
        X509Certificate cert = createValidCertificate();
        String sslTrust = CertificateUtil.toPEMformat(cert);
        assertNotNull(sslTrust);
        X509Certificate convertedCert = CertificateUtil.createCertificate(sslTrust);
        assertEquals(cert, convertedCert);
    }

    @Test
    public void testLoadCACert() {
        String caCertPEM = loadPemFileContent(CERTS_CA_PEM);
        X509Certificate x509Certificate = CertificateUtil.createCertificate(caCertPEM);
        assertNotNull(x509Certificate);
    }

    @Test
    public void testGenerateServerCert() {
        String caCertPEM = loadPemFileContent(CERTS_CA_PEM);
        X509Certificate issuerCertificate = CertificateUtil.createCertificate(caCertPEM);

        assertNotNull(issuerCertificate);
    }

    @Test
    public void testLoadChain() {
        String chainPEM = loadPemFileContent(CERTS_CHAIN_PEM);
        X509Certificate[] chain = CertificateUtil.createCertificateChain(chainPEM);
        assertEquals(3, chain.length);

        String formattedPEM = CertificateUtil.toPEMformat(chain);
        assertEquals(switchToUnixLineEnds(chainPEM), switchToUnixLineEnds(formattedPEM));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLoadChainAsSingleCertificate() {
        String chainPEM = loadPemFileContent(CERTS_CHAIN_PEM);
        CertificateUtil.createCertificate(chainPEM);
    }

    public static X509Certificate[] loadCertificateChain(String pemFile) {
        String chainPEM = CertificateUtilTest.loadPemFileContent(pemFile);
        return CertificateUtil.createCertificateChain(chainPEM);
    }

    public static String loadPemFileContent(String pemFile) {
        try (InputStream in = CertificateUtilTest.class.getClassLoader()
                .getResourceAsStream(pemFile)) {

            if (in == null) {
                return null;
            }
            try (Scanner sc = new Scanner(in, "UTF-8")) {
                return sc.useDelimiter("\\A").next();
            }

        } catch (IOException e) {
            Utils.logWarning("Unable to load pem file %s, reason %s", pemFile, e.getMessage());
            return null;
        }
    }

    private X509Certificate createCertificate(String propName) {
        String sslTrust = testProperties.getProperty(propName);
        return CertificateUtil.createCertificate(sslTrust);
    }

    private X509Certificate createValidCertificate() {
        return createCertificate(VALID_CERT_PROP_NAME);
    }

    public static String switchToUnixLineEnds(String s) {
        return s == null ? null : s.replaceAll("\r\n", "\n");
    }

}
