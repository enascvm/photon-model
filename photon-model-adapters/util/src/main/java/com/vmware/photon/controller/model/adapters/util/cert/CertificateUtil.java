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

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;

import com.vmware.photon.controller.model.adapters.util.AssertUtil;
import com.vmware.photon.controller.model.support.CertificateInfo;
import com.vmware.xenon.common.Utils;

/**
 * Utility class that provides useful functions for certificate's data retrieval and manipulation.
 */
public class CertificateUtil {
    public static final String COMMON_NAME_KEY = "commonName";
    public static final String ISSUER_NAME_KEY = "issuerName";
    public static final String SERIAL_KEY = "serial";
    public static final String FINGERPRINT_KEY = "fingerprint";
    public static final String VALID_SINCE_KEY = "validSince";
    public static final String VALID_TO_KEY = "validTo";

    private static final String HEX = "0123456789ABCDEF";

    /**
     * Utility method to decode a certificate chain PEM encoded string value to an array of
     * X509Certificate certificate instances.
     * @param certChainPEM
     *         - a certificate chain (one or more certificates) PEM encoded string value.
     * @return - decoded array of X509Certificate  certificate instances.
     * @throws RuntimeException
     *         if a certificate can't be decoded to X509Certificate type certificate.
     */
    public static X509Certificate[] createCertificateChain(String certChainPEM) {
        AssertUtil.assertNotNull(certChainPEM, "certChainPEM should not be null.");

        List<X509Certificate> chain = new ArrayList<>();
        try (PEMParser parser = new PEMParser(new StringReader(certChainPEM))) {

            JcaX509CertificateConverter converter = new JcaX509CertificateConverter();
            X509CertificateHolder certificateHolder;
            while ((certificateHolder = (X509CertificateHolder) parser.readObject()) != null) {
                chain.add(converter.getCertificate(certificateHolder));
            }
        } catch (IOException | CertificateException e) {
            throw new RuntimeException("Failed to create certificate: " + certChainPEM, e);
        }

        if (chain.isEmpty()) {
            throw new RuntimeException("A valid certificate was not found: " + certChainPEM);
        }

        return chain.toArray(new X509Certificate[chain.size()]);
    }

    /**
     * Utility method to decode a certificate PEM encoded string value to X509Certificate type
     * certificate instance.
     * <p>
     * The difference between this method and {@link #createCertificateChain(String)} is that this
     * expects exactly one PEM encoded certificate. Use when the PEM represents a distinguished
     * private or public key. For general purpose, where the expectation is to have one or more
     * PEM encoded certificates, certificate chain, use {@link #createCertificateChain(String)}.
     * @param certPEM
     *         - a certificate PEM encoded string value.
     * @return - decoded X509Certificate type certificate instance.
     * @throws RuntimeException
     *         if the certificate can't be decoded to X509Certificate type certificate.
     */
    public static X509Certificate createCertificate(String certPEM) {
        X509Certificate[] createCertificateChain = createCertificateChain(certPEM);
        AssertUtil.assertTrue(createCertificateChain.length == 1,
                "Expected exactly one certificate in PEM: " + certPEM);

        return createCertificateChain[0];
    }

    /**
     * Create an empty key store
     */
    public static KeyStore createEmptyKeyStore() {
        try {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null); // initialize empty keystore
            return keyStore;

        } catch (KeyStoreException | NoSuchAlgorithmException
                | CertificateException | IOException e) {
            throw new RuntimeException("Failed to create empty keystore", e);
        }
    }

    /**
     * Create a TrustManager using the given trust store
     */
    public static TrustManager[] getTrustManagers(KeyStore trustStore) {
        try {
            TrustManagerFactory tmf = TrustManagerFactory
                    .getInstance(TrustManagerFactory.getDefaultAlgorithm());

            tmf.init(trustStore);
            return tmf.getTrustManagers();

        } catch (NoSuchAlgorithmException | KeyStoreException e) {
            throw new RuntimeException(
                    "Failed to create a TrustManager from a trust store", e);
        }
    }

    /**
     * Serialize Certificate in PEM format
     */
    public static String toPEMformat(X509Certificate certificate) {
        StringWriter sw = new StringWriter();
        JcaPEMWriter pemWriter = new JcaPEMWriter(sw);
        try {
            pemWriter.writeObject(certificate);
            pemWriter.close();

            return sw.toString();

        } catch (IOException x) {
            throw new RuntimeException("Failed to serialize certificate", x);
        }
    }

    /**
     * Serialize Certificate chain in PEM format
     */
    public static String toPEMformat(X509Certificate[] certificateChain) {
        StringWriter sw = new StringWriter();
        for (X509Certificate certificate : certificateChain) {
            sw.append(toPEMformat(certificate));
        }
        return sw.toString();
    }

    /**
     * Extracts from DN a given attribute.
     * @param dn
     *         The entire DN
     * @param attribute
     *         The attribute to extract
     * @return the attribute value or null if not found an attribute with the given dn.
     */
    public static String getAttributeFromDN(String dn, String attribute) {
        try {
            LdapName subjectDn = new LdapName(dn);

            for (Rdn rdn : subjectDn.getRdns()) {
                if (rdn.getType().equals(attribute)) {
                    return rdn.getValue().toString();
                }
            }
        } catch (InvalidNameException e) {
            throw new IllegalArgumentException(e);
        }

        return null;
    }

    /**
     * Extracts the Certificate Principal Common Name (CN).
     */
    public static String getCommonName(Principal certPrincipal) {
        if (certPrincipal == null || certPrincipal.getName() == null
                || certPrincipal.getName().isEmpty()) {
            return null;
        }
        String attr = getAttributeFromDN(certPrincipal.getName(), "CN");
        if (attr == null) {
            Utils.logWarning("DN %s doesn't contain attribute 'CN'", certPrincipal.getName());
            attr = certPrincipal.getName();
        }
        return attr;

    }

    /**
     * Compute the {@link ThumbprintAlgorithm#DEFAULT} thumbprint of a X.509 certificate.
     * @param cert
     *         certificate
     * @return the thumbprint corresponding to the certificate; {@code not-null} value
     */
    public static String computeCertificateThumbprint(X509Certificate cert) {
        return computeCertificateThumbprint(cert, ThumbprintAlgorithm.DEFAULT);
    }

    /**
     * Compute thumbprint of a X.509 certificate as specified in {@link ThumbprintAlgorithm}
     * parameter.
     * @param cert
     *         certificate
     * @param thumbprintAlgorithm
     *         the type of {@link ThumbprintAlgorithm}
     * @return the thumbprint corresponding to the certificate; {@code not-null} value
     */
    public static String computeCertificateThumbprint(X509Certificate cert,
            ThumbprintAlgorithm thumbprintAlgorithm) {
        AssertUtil.assertNotNull(cert, "'certificate' must not be null.");
        byte[] digest;
        try {
            MessageDigest md = MessageDigest.getInstance(thumbprintAlgorithm.algorithmName);
            digest = md.digest(cert.getEncoded());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        } catch (CertificateEncodingException e) {
            throw new IllegalArgumentException(e);
        }

        StringBuilder thumbprint = new StringBuilder();
        for (int i = 0, len = digest.length; i < len; ++i) {
            if (i > 0) {
                thumbprint.append(':');
            }
            byte b = digest[i];
            thumbprint.append(HEX.charAt((b & 0xF0) >> 4));
            thumbprint.append(HEX.charAt(b & 0x0F));
        }
        return thumbprint.toString();
    }

    /**
     * Extracts {@link X509Certificate} properties from the given {@code cert}
     * @param cert
     *         the x509 certificate
     * @return x509 certificate related properties as map
     */
    public static Map<String, String> getCertificateInfoProperties(X509Certificate cert) {
        Map<String, String> certificateInfo = new HashMap<>();
        certificateInfo.put(COMMON_NAME_KEY, CertificateUtil.getCommonName(cert.getSubjectDN()));
        certificateInfo.put(ISSUER_NAME_KEY, CertificateUtil.getCommonName(cert.getIssuerDN()));
        certificateInfo.put(SERIAL_KEY, getSerialNumber(cert));
        certificateInfo.put(FINGERPRINT_KEY, CertificateUtil.computeCertificateThumbprint(cert));
        certificateInfo.put(VALID_SINCE_KEY, Long.toString(cert.getNotBefore().getTime()));
        certificateInfo.put(VALID_TO_KEY, Long.toString(cert.getNotAfter().getTime()));
        return certificateInfo;
    }

    /**
     * create {@link CertificateInfo} for the given {@code certificateChain}
     * @param certificateChain
     *         the certificate chain
     * @return new {@link CertificateInfo} instance for the provided {@code certificateChain}
     */
    public static CertificateInfo getCertificateInfo(X509Certificate[] certificateChain) {
        AssertUtil.assertNotNull(certificateChain, "'certificateChain' must be set.");
        AssertUtil.assertTrue(certificateChain.length > 0, "'certificateChain' cannot be empty.");
        return CertificateInfo.of(
                toPEMformat(certificateChain),
                getCertificateInfoProperties(certificateChain[0]));
    }

    private static String getSerialNumber(X509Certificate cert) {
        return cert.getSerialNumber() == null ? null : cert.getSerialNumber().toString();
    }

    /**
     * <ul>Thumbprint algorithm enum with two options
     * <li>SHA-1 - Secure Hash Algorithm 1</li>
     * <li>SHA-256 - Fixed size 256-bit (32-byte) hash Secure Hash Algorithm </li>
     * </ul>
     */
    public enum ThumbprintAlgorithm {
        SHA_1("SHA-1", "[a-fA-F0-9:]{59}"),
        SHA_256("SHA-256", "[a-fA-F0-9:]{95}");

        public static final ThumbprintAlgorithm DEFAULT = SHA_1;

        ThumbprintAlgorithm(String algorithmName, String thumbprintRegex) {
            this.algorithmName = algorithmName;
            this.thumbprintRegex = thumbprintRegex;
        }

        /**
         * algorithm name
         */
        public final String algorithmName;

        /**
         * algorithm specific match pattern as regex expression
         */
        private final String thumbprintRegex;

        /**
         * validate whether given {@code thumbPrint} has valid format
         * @param thumbPrint
         *         the thumbPrint to validate
         * @return whether valid or not
         */
        public boolean isValid(String thumbPrint) {
            return thumbPrint != null && thumbPrint.matches(this.thumbprintRegex);
        }
    }
}