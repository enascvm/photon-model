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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.util.encoders.Base64;

/*
 * Utility class that provides methods for public/private key generation and key conversion.
 */
public class KeyUtil {
    public static final int KEY_SIZE = 1024;
    public static final String RSA_ALGORITHM = "RSA";

    public static KeyPair generateRSAKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(
                    RSA_ALGORITHM, new BouncyCastleProvider());
            generator.initialize(KEY_SIZE);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate RSA key pair", e);
        }
    }

    public static String toPEMFormat(Key key) {
        StringWriter sw = new StringWriter();
        JcaPEMWriter pemWriter = new JcaPEMWriter(sw);
        try {
            pemWriter.writeObject(key);
            pemWriter.close();

            return sw.toString();

        } catch (IOException x) {
            throw new RuntimeException("Failed to serialize key", x);
        }
    }

    public static String toPublicOpenSSHFormat(RSAPublicKey rsaPublicKey) {
        ByteArrayOutputStream byteOs = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(byteOs);
        try {
            dos.writeInt("ssh-rsa".getBytes().length);
            dos.write("ssh-rsa".getBytes());

            dos.writeInt(rsaPublicKey.getPublicExponent().toByteArray().length);
            dos.write(rsaPublicKey.getPublicExponent().toByteArray());

            dos.writeInt(rsaPublicKey.getModulus().toByteArray().length);
            dos.write(rsaPublicKey.getModulus().toByteArray());

            String publicKeyEncoded = new String(Base64.encode(byteOs.toByteArray()));
            return "ssh-rsa " + publicKeyEncoded;
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode public key to OpenSSH format", e);
        }
    }

    /**
     * Decodes certificate
     *
     * @param certificate
     *            Base64 encoded certificate
     * @return X509Certificate
     * @throws CertificateException
     */
    public static X509Certificate decodeCertificate(String certificate)
            throws CertificateException {

        certificate = certificate != null ? certificate.trim() : null;
        if (certificate != null && (certificate = certificate.trim()).length() > 0) {
            if (!certificate.startsWith("-----BEGIN")) {
                String cert_begin = "-----BEGIN CERTIFICATE-----\n";
                String end_cert = "\n-----END CERTIFICATE-----";
                certificate = String.format("%s%s%s", cert_begin, certificate, end_cert);
            }
            return decodeCertificate(certificate.getBytes());
        }
        return null;
    }

    private static X509Certificate decodeCertificate(byte[] bytes) throws CertificateException {
        CertificateFactory fact = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) fact
                .generateCertificate(new ByteArrayInputStream(bytes));
        return cert;

    }

}
