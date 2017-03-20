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

package com.vmware.photon.controller.model.security;

import com.vmware.photon.controller.model.security.service.SslTrustCertificateFactoryService;
import com.vmware.photon.controller.model.security.service.SslTrustCertificateService;
import com.vmware.xenon.common.ServiceHost;

/**
 * Helper class that starts all the photon model security related services
 */
public class PhotonModelSecurityServices {

    public static final String[] LINKS = {
            SslTrustCertificateService.FACTORY_LINK };

    public static void startServices(ServiceHost host) throws Throwable {

        host.startFactory(SslTrustCertificateService.class, SslTrustCertificateFactoryService::new);
    }

}
