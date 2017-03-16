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

import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.Utils;

/**
 * Certificate related service error response used to set as operation body when there is some
 * issue with certificates.
 */

public class CertificateInfoServiceErrorResponse extends ServiceErrorResponse {
    /**
     * The common mask for certificate info related error responses
     */
    public static final int ERROR_CODE_CERTIFICATE_MASK = 0x90000000;
    /**
     * indicates the certificate is not trusted
     */
    public static final int ERROR_CODE_UNTRUSTED_CERTIFICATE =
            ERROR_CODE_CERTIFICATE_MASK | 0x00000001;

    public static final String KIND = Utils.buildKind(CertificateInfoServiceErrorResponse.class);

    /**
     * The certificate information.
     */
    public CertificateInfo certificateInfo;

    private CertificateInfoServiceErrorResponse(CertificateInfo certificateInfo,
            int statusCode, int errorCode, String message) {
        if (certificateInfo == null) {
            throw new IllegalArgumentException("'certificateInfo' must be set.");
        }
        this.documentKind = KIND;
        this.certificateInfo = certificateInfo;
        this.statusCode = statusCode;
        this.errorCode = errorCode;
        this.message = message;
    }

    public static CertificateInfoServiceErrorResponse create(
            CertificateInfo certificateInfo,
            int statusCode, int errorCode, String message) {
        return new CertificateInfoServiceErrorResponse(
                certificateInfo, statusCode, errorCode, message);
    }

    public static CertificateInfoServiceErrorResponse create(
            CertificateInfo certificateInfo,
            int statusCode, int errorCode, Throwable e) {
        return create(certificateInfo, statusCode, errorCode, e != null ? e.getMessage() : null);
    }
}
