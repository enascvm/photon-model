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

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

import javax.net.ssl.X509TrustManager;

import com.vmware.xenon.common.Utils;

public class X509TrustManagerResolver implements X509TrustManager {
    private static X509TrustManager trustManager;
    private List<X509Certificate> connectionCertificates = new ArrayList<>();
    private boolean certsTrusted;
    private CertificateException certificateException;

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
        this.certificateException = validateIfTrusted(certs, authType);
        this.certsTrusted = this.certificateException == null;

        Collections.addAll(this.connectionCertificates, certs);
    }

    public boolean isCertsTrusted() {
        return this.certsTrusted;
    }

    /**
     * Get the Server Trust Certificate from the chain.
     */
    public X509Certificate getCertificate() {
        if (this.connectionCertificates.isEmpty()) {
            throw new IllegalStateException(
                    "checkServerTrusted was not called or was not successful.");
        }
        return this.connectionCertificates.get(0);
    }

    /**
     * @return {@link CertificateException} in case the certificate is not trusted
     */
    public CertificateException getCertificateException() {
        return this.certificateException;
    }

    public X509Certificate[] getCertificateChain() {
        if (this.connectionCertificates.isEmpty()) {
            throw new IllegalStateException(
                    "checkServerTrusted was not called or was not successful.");
        }

        X509Certificate[] certificateChain =
                new X509Certificate[this.connectionCertificates.size()];
        certificateChain = this.connectionCertificates.toArray(certificateChain);
        return certificateChain;
    }

    private CertificateException validateIfTrusted(X509Certificate[] certificates,
            String authType) {
        if (trustManager == null) {
            trustManager = ServerX509TrustManager.getInstance();
            if (trustManager == null) {
                return new CertificateException(
                        "Cannot validate certificate chain.",
                        new IllegalStateException("ServerX509TrustManager not initialized."));
            }
        }

        try {
            trustManager.checkServerTrusted(certificates, authType);
            return null;
        } catch (CertificateException e) {
            Utils.log(getClass(), CertificateException.class.getSimpleName(), Level.FINE,
                    Utils.toString(e));
            return e;
        }
    }

}