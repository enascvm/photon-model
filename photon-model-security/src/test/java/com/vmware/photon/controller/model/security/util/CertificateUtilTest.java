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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static com.vmware.photon.controller.model.security.TestUtils.switchToUnixLineEnds;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.logging.Logger;

import org.junit.BeforeClass;
import org.junit.Test;

import com.vmware.photon.controller.model.security.util.CertificateUtil.CertChainKeyPair;
import com.vmware.photon.controller.model.security.util.CertificateUtil.ThumbprintAlgorithm;
import com.vmware.xenon.common.Utils;

/**
 * Test CertificateUtil methods
 */
public class CertificateUtilTest {
    private static final Logger logger = Logger.getLogger(CertificateUtilTest.class.getName());

    private static final String PROPERTIES_FILE_NAME = "CertificateUtilTest.properties";
    private static final String VALID_CERT_PROP_NAME = "cert.valid";
    private static final String INVALID_CERT_PROP_NAME = "cert.invalid";
    private static final String VALID_KEY_PROP_NAME = "key.valid";
    private static final String INVALID_KEY_PROP_NAME = "key.invalid";

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
    public void testCreateKeyValid() {
        createKey(VALID_KEY_PROP_NAME);
    }

    @Test(expected = RuntimeException.class)
    public void testCreateKeyInvalid() {
        createKey(INVALID_KEY_PROP_NAME);
    }

    @Test
    public void testComputeThumbprint() throws Exception {
        X509Certificate cert = createValidCertificate();
        String thumbprint = CertificateUtil.computeCertificateThumbprint(cert);
        assertNotNull(thumbprint);
        assertTrue(ThumbprintAlgorithm.isValidThumbprint(thumbprint));
    }

    @Test
    public void testGetCertificateInfoProperties() throws Exception {
        X509Certificate cert = createValidCertificate();
        Map<String, String> props = CertificateUtil.getCertificateInfoProperties(cert);
        assertNotNull(props);
        assertEquals("client", props.get(CertificateUtil.COMMON_NAME_KEY));
        assertEquals(1453814195000L, Long.parseLong(props.get(CertificateUtil.VALID_SINCE_KEY)));
        assertEquals(1706274995000L, Long.parseLong(props.get(CertificateUtil.VALID_TO_KEY)));
        assertEquals("1453814169", props.get(CertificateUtil.SERIAL_KEY));
        assertEquals("localhost", props.get(CertificateUtil.ISSUER_NAME_KEY));
        assertEquals("05:49:2E:BC:8F:47:A9:0A:C3:FA:8C:D6:9F:2D:83:A1:EE:3E:AE:A2",
                props.get(CertificateUtil.FINGERPRINT_KEY));
    }

    @Test
    public void testGetAttributeFromDN() throws Exception {
        String subjectDN = "CN=vcac.eng.vmware.com, OU=R&D, O=VMware,C=US";

        assertEquals("vcac.eng.vmware.com", CertificateUtil.getAttributeFromDN(subjectDN, "CN"));
        assertEquals("R&D", CertificateUtil.getAttributeFromDN(subjectDN, "OU"));
        assertEquals("VMware", CertificateUtil.getAttributeFromDN(subjectDN, "O"));
        assertEquals("US", CertificateUtil.getAttributeFromDN(subjectDN, "C"));
    }

    @Test
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
        String caCertPEM = loadPemFileContent("certs/ca.pem");
        X509Certificate x509Certificate = CertificateUtil.createCertificate(caCertPEM);
        assertNotNull(x509Certificate);
    }

    @Test
    public void testLoadCAKey() {
        String keyPem = loadPemFileContent("certs/ca-key.pem");

        KeyPair keyPair = CertificateUtil.createKeyPair(keyPem);
        assertNotNull(keyPair);
        assertNotNull(keyPair.getPrivate());
    }

    @Test
    public void testGenerateClientCert() {
        String keyPem = loadPemFileContent("certs/ca-key.pem");

        KeyPair keyPair = CertificateUtil.createKeyPair(keyPem);

        String caCertPEM = loadPemFileContent("certs/ca.pem");
        X509Certificate issuerCertificate = CertificateUtil.createCertificate(caCertPEM);

        CertChainKeyPair signedForClient = CertificateUtil.generateSignedForClient("testClient",
                issuerCertificate, keyPair.getPrivate());
        assertNotNull(signedForClient.getPrivateKey());
        assertNotNull(signedForClient.getCertificateChain());
        assertEquals(2, signedForClient.getCertificateChain().size());
    }

    @Test
    public void testGenerateServerCert() {
        String keyPem = loadPemFileContent("certs/ca-key.pem");

        KeyPair keyPair = CertificateUtil.createKeyPair(keyPem);

        String caCertPEM = loadPemFileContent("certs/ca.pem");
        X509Certificate issuerCertificate = CertificateUtil.createCertificate(caCertPEM);

        CertChainKeyPair signedForClient = CertificateUtil.generateSignedForClient("testServer",
                issuerCertificate, keyPair.getPrivate());
        assertNotNull(signedForClient.getPrivateKey());
        assertNotNull(signedForClient.getCertificateChain());
        assertEquals(2, signedForClient.getCertificateChain().size());
    }

    @Test
    public void testLoadChain() {
        String chainPEM = loadPemFileContent("certs/chain.pem");
        X509Certificate[] chain = CertificateUtil.createCertificateChain(chainPEM);
        assertEquals(3, chain.length);

        String formattedPEM = CertificateUtil.toPEMformat(chain);
        assertEquals(switchToUnixLineEnds(chainPEM), switchToUnixLineEnds(formattedPEM));
    }

    @Test
    public void testValidateCertificateChain() {
        String chainPEM = loadPemFileContent("certs/chain.pem");
        X509Certificate[] chain = CertificateUtil.createCertificateChain(chainPEM);
        assertTrue(isValid(chain));

        chainPEM = loadPemFileContent("certs/ca.pem");
        chain = CertificateUtil.createCertificateChain(chainPEM);
        assertTrue(isValid(chain));

        chainPEM = loadPemFileContent("certs/ca-past-validity.pem");
        chain = CertificateUtil.createCertificateChain(chainPEM);
        assertFalse(isValid(chain));

        chainPEM = loadPemFileContent("certs/chain-invalid.pem");
        chain = CertificateUtil.createCertificateChain(chainPEM);
        assertFalse(isValid(chain));

        chainPEM = loadPemFileContent("certs/chain-invalid-occurrences.pem");
        chain = CertificateUtil.createCertificateChain(chainPEM);
        assertFalse(isValid(chain));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLoadChainAsSingleCertificate() {
        String chainPEM = loadPemFileContent("certs/chain.pem");
        CertificateUtil.createCertificate(chainPEM);
    }

    @Test
    public void testGeneratePureFingerPrint() {
        String pem = loadPemFileContent("certs/chain.pem");
        X509Certificate[] chain = CertificateUtil.createCertificateChain(pem);
        String s = CertificateUtil.generatePureFingerPrint(chain);

        assertNotNull(s);
        assertNotEquals(0, s.length());
        assertTrue(s.matches("[0-9a-f]+"));

        pem = loadPemFileContent("certs/ca.pem");
        X509Certificate cert = CertificateUtil.createCertificate(pem);
        s = CertificateUtil.generatePureFingerPrint(cert);

        assertNotNull(s);
        assertNotEquals(0, s.length());
        assertTrue(s.matches("[0-9a-f]+"));
    }

    private static String loadPemFileContent(String pemFile) {
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

    private void createKey(String propName) {
        String key = testProperties.getProperty(propName);
        CertificateUtil.createKeyPair(key).getPrivate();
    }

    private static boolean isValid(X509Certificate[] chain) {
        try {
            CertificateUtil.validateCertificateChain(chain);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}
