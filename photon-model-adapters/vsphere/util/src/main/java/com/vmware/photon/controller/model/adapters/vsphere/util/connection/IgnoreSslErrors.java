/*
 * Copyright (c) 2015-2016 VMware, Inc. All Rights Reserved.
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

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.xml.ws.BindingProvider;

public class IgnoreSslErrors {
    /**
     * com.sun.xml.internal.ws.developer.JAXWSProperties
     */
    private static final String HOSTNAME_VERIFIER = "com.sun.xml.internal.ws.transport.https.client.hostname.verifier";
    private static final String SSL_SOCKET_FACTORY = "com.sun.xml.internal.ws.transport.https.client.SSLSocketFactory";

    private static SSLSocketFactory socketFactory;

    public static void ignoreErrors(BindingProvider bp) {
        Map<String, Object> requestContext = bp.getRequestContext();

        requestContext.put(SSL_SOCKET_FACTORY, getInsecureSSLSocketFactory());
        requestContext.put(HOSTNAME_VERIFIER, newNonVerifyingHostnameVerifier());
    }

    public static void useTrustManager(BindingProvider bp, TrustManager trustManager) {
        Map<String, Object> requestContext = bp.getRequestContext();

        requestContext.put(SSL_SOCKET_FACTORY, newTrustedSocketFactory(trustManager));
        requestContext.put(HOSTNAME_VERIFIER, newNonVerifyingHostnameVerifier());
    }

    private static SSLSocketFactory newTrustedSocketFactory(TrustManager trustManager) {
        return newTrustedSslContext("TLS", trustManager).getSocketFactory();
    }

    public static HostnameVerifier newNonVerifyingHostnameVerifier() {
        return new NonVerifyingHostnameVerifier();
    }

    private static SSLSocketFactory getInsecureSSLSocketFactory() {
        // harmless race
        SSLSocketFactory factory = socketFactory;
        if (factory == null) {
            factory = socketFactory = newInsecureSslContext("TLS").getSocketFactory();
        }
        return factory;
    }

    public static SSLContext newInsecureSslContext(String protocol) {
        TrustManager[] trustManagers = new TrustManager[] {
                new AllTrustingTrustManager()
        };

        try {
            SSLContext sslContext = SSLContext.getInstance(protocol);
            sslContext.init(new KeyManager[0], trustManagers, new SecureRandom());
            return sslContext;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static SSLContext newTrustedSslContext(String protocol, TrustManager trustManager) {
        TrustManager[] trustManagers = new TrustManager[] {
                trustManager
        };

        try {
            SSLContext sslContext = SSLContext.getInstance(protocol);
            sslContext.init(new KeyManager[0], trustManagers, new SecureRandom());
            return sslContext;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static class NonVerifyingHostnameVerifier implements HostnameVerifier {
        @Override
        public boolean verify(String hostName, SSLSession session) {
            return true;
        }
    }

    private static class AllTrustingTrustManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] certs, String authType)
                throws CertificateException {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] certs, String authType)
                throws CertificateException {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
