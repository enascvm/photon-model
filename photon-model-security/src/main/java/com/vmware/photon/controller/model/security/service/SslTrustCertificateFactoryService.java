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

package com.vmware.photon.controller.model.security.service;

import com.vmware.photon.controller.model.security.service.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.photon.controller.model.security.util.CertificateUtil;
import com.vmware.photon.controller.model.util.AssertUtil;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;

public class SslTrustCertificateFactoryService extends FactoryService {

    public static final String SELF_LINK = SslTrustCertificateService.FACTORY_LINK;

    public SslTrustCertificateFactoryService() {
        super(SslTrustCertificateState.class);
    }

    @Override
    public Service createServiceInstance() throws Throwable {
        return new SslTrustCertificateService();
    }

    /**
     * Override the handlePost method to set the documentSelfLink. We don't want to have multiple
     * certificate states with the same certificate, so we build the documentSelfLink ourselves
     *
     * @param op
     */
    @Override
    public void handlePost(Operation op) {
        if (op.isSynchronize()) {
            op.complete();
            return;
        }
        if (op.hasBody()) {
            SslTrustCertificateState body = (SslTrustCertificateState) op.getBody(this.stateType);
            if (body == null) {
                op.fail(new IllegalArgumentException("structured body is required"));
                return;
            }

            if (body.documentSourceLink != null) {
                op.fail(new IllegalArgumentException("clone request not supported"));
                return;
            }

            body.documentSelfLink = generateSelfLink(body);
            op.setBody(body);
            op.complete();
        } else {
            op.fail(new IllegalArgumentException("body is required"));
        }
    }

    public static String generateSelfLink(SslTrustCertificateState body) {
        AssertUtil.assertNotEmpty(body.certificate, "certificate");

        return CertificateUtil.generatePureFingerPrint(
                CertificateUtil.createCertificateChain(body.certificate));
    }

}
