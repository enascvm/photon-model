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

package com.vmware.photon.controller.model.support;

import java.util.Map;

import com.vmware.xenon.common.Utils;

/**
 * Certificate information holder
 */
public class CertificateInfo {
    public static final String KIND = Utils.buildKind(CertificateInfo.class);
    public String documentKind = KIND;
    /**
     * The certificate in string format. e.g. PEM for X509Certificate, ssh host key, etc
     */
    public final String certificate;

    /**
     * Certificate related properties which may provide additional information about the given
     * certificate.
     */
    public final Map<String, String> properties;

    private CertificateInfo(String certificate, Map<String, String> properties) {
        if (certificate == null) {
            throw new IllegalArgumentException("'certificate' must be set.");
        }
        this.certificate = certificate;
        this.properties = properties;
    }

    public static CertificateInfo of(String certificate, Map<String, String> properties) {
        return new CertificateInfo(certificate, properties);
    }

    @Override
    public String toString() {
        return String.format("%s[certificate=%s, properties=%s]",
                getClass().getSimpleName(), this.certificate, this.properties);
    }
}
