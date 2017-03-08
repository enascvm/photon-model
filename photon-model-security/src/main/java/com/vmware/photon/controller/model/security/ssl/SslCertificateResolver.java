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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Utils;

/**
 * Helper class, responsible for resolving remote SSL certificates.
 */
public class SslCertificateResolver {
    private static final Logger logger = Logger.getLogger(SslCertificateResolver.class.getName());

    private static X509TrustManager trustManager;
    private static final int DEFAULT_SECURE_CONNECTION_PORT = 443;
    private static final long DEFAULT_CONNECTION_TIMEOUT_MILLIS = Long.getLong(
            "ssl.resolver.import.timeout.millis", TimeUnit.SECONDS.toMillis(30));
    private final String uri;
    private final String hostAddress;
    private final int port;

    private List<X509Certificate> connectionCertificates;
    private boolean certsTrusted;
    private final long timeout;
    private SSLContext sslContext;

    public static SslCertificateResolver connect(URI uri) {
        return new SslCertificateResolver(uri).connect();
    }

    public SslCertificateResolver(URI uri) {
        this(uri, DEFAULT_CONNECTION_TIMEOUT_MILLIS);
    }

    public SslCertificateResolver(URI uri, long timeout) {
        this.hostAddress = uri.getHost();
        this.port = uri.getPort() == -1 ? DEFAULT_SECURE_CONNECTION_PORT : uri.getPort();
        this.timeout = timeout;
        this.uri = String.format("%s://%s:%d", uri.getScheme(), this.hostAddress, this.port);
    }

    public SslCertificateResolver connect() {
        logger.entering(logger.getName(), "connect");
        this.connectionCertificates = new ArrayList<>();
        // create a SocketFactory without TrustManager (well with one that accepts anything)
        TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }

            @Override
            public void checkClientTrusted(X509Certificate[] certs,
                    String authType) {
                throw new UnsupportedOperationException("Client authentication is not supported.");
            }

            @Override
            public void checkServerTrusted(X509Certificate[] certs,
                    String authType) throws CertificateException {
                certs[0].checkValidity();
                SslCertificateResolver.this.certsTrusted = validateIfTrusted(certs, authType);

                for (X509Certificate certificate : certs) {
                    SslCertificateResolver.this.connectionCertificates.add(certificate);
                }
            }
        } };

        try {
            if (this.sslContext == null) {
                this.sslContext = SSLContext.getInstance("TLS");
                this.sslContext.init(null, trustAllCerts, new SecureRandom());
            }
        } catch (KeyManagementException | NoSuchAlgorithmException e) {
            logger.throwing(logger.getName(), "connect", e);
            throw new LocalizableValidationException(e, "Failed to initialize SSL context.", "common.ssh.context.init");
        }

        SSLSocketFactory sslSocketFactory = this.sslContext.getSocketFactory();
        try (SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket()) {
            sslSocket.connect(new InetSocketAddress(this.hostAddress, this.port), (int) this.timeout);
            SSLSession session = sslSocket.getSession();
            session.invalidate();

        } catch (IOException e) {
            if (this.certsTrusted || !this.connectionCertificates.isEmpty()) {
                Utils.logWarning(
                        "Exception while resolving certificate for host: [%s]. Error: %s ",
                        this.uri, e.getMessage());
            } else {
                logger.throwing(logger.getName(), "connect", e);
                throw new IllegalArgumentException(e.getMessage(), e);
            }
        }


        if (this.connectionCertificates.size() == 0) {
            LocalizableValidationException e = new LocalizableValidationException(
                    "Importing ssl certificate failed for server: " + this.uri,
                    "common.certificate.import.failed", this.uri);

            logger.throwing(logger.getName(), "connect", e);
            throw e;
        }
        logger.exiting(logger.getName(), "connect");
        return this;
    }

    private boolean validateIfTrusted(X509Certificate[] certificates, String authType) {
        if (trustManager == null) {
            trustManager = ServerX509TrustManager.init(null);
        }

        try {
            trustManager.checkServerTrusted(certificates, authType);
            return true;
        } catch (CertificateException e) {
            Utils.log(getClass(), CertificateException.class.getSimpleName(), Level.FINE,
                    Utils.toString(e));
            return false;
        }
    }

    public boolean isCertsTrusted() {
        return this.certsTrusted;
    }

    public X509Certificate[] getCertificateChain() {
        if (this.connectionCertificates.isEmpty()) {
            throw new IllegalStateException("connect() was not called or was not successful.");
        }

        X509Certificate[] certificateChain = new X509Certificate[this.connectionCertificates.size()];
        certificateChain = this.connectionCertificates.toArray(certificateChain);
        return certificateChain;
    }

    /**
     * Get the Server Trust Certificate from the chain.
     */
    public X509Certificate getCertificate() {
        if (this.connectionCertificates.isEmpty()) {
            throw new IllegalStateException("connect() was not called or was not successful.");
        }
        return this.connectionCertificates.get(0);
    }

}
