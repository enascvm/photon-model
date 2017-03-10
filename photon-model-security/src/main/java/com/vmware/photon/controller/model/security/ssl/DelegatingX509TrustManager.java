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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.X509TrustManager;

/**
 * A TrustManager implementation that delegates to a dynamic list of other TrustManagers.
 * <p>
 * The list can be changed in runtime without reloading or replacing the main TrustManager.
 */
public class DelegatingX509TrustManager implements X509TrustManager {
    private final Map<Object, X509TrustManager> delegates = new ConcurrentHashMap<Object, X509TrustManager>();

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {

        throw new UnsupportedOperationException(
                "This trust manager is intended to be used to validate servers only");
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {

        if (this.delegates.isEmpty()) {
            throw new CertificateException("Can't work without any delegates");
        }

        CertificateException lastException = null;
        for (X509TrustManager delegate : this.delegates.values()) {
            try {
                delegate.checkServerTrusted(chain, authType);

                // found a delegate that accepts the certificate, so break out
                // without checking the rest
                return;

            } catch (CertificateException x) {
                lastException = x;
                continue;
            }
        }

        // if we reached here then none of the delegates accepted the
        // certificate, so throw the last exception
        throw lastException;
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        // not implemented yet - might be needed for valid certificates
        return new X509Certificate[0];
    }

    /**
     * Add a delegate identified by the given unique key (can be used to remove it later)
     * @param key
     * @param newDelegate
     */
    public void putDelegate(Object key, X509TrustManager newDelegate) {
        this.delegates.put(key, newDelegate);
    }

    /**
     * Get a delegate identified by the given unique key
     * @param key
     * @return the delegate for the key
     */
    public X509TrustManager getDelegate(Object key) {
        return this.delegates.get(key);
    }

    /**
     * Remove a previously added delegate
     * @param key
     * @return
     */
    public X509TrustManager removeDelegate(Object key) {
        return this.delegates.remove(key);
    }

}
