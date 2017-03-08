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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;

import org.junit.Ignore;
import org.junit.Test;

public class SslCertificateResolverTest {
    private SslCertificateResolver resolver;

    @Ignore("Test is working but ignored because of external/vpn network requirements.")
    @Test
    public void testResolveCertificates() throws Exception {
        this.resolver = SslCertificateResolver.connect(URI.create("https://mail.google.com"));
        assertTrue(this.resolver.isCertsTrusted());
        assertNotNull(this.resolver.getCertificate());

        this.resolver = SslCertificateResolver.connect(URI.create("https://email.vmware.com"));
        assertTrue(this.resolver.isCertsTrusted());
        assertNotNull(this.resolver.getCertificate());

        // self-signed cert should not be trusted
        this.resolver = SslCertificateResolver
                .connect(URI.create("https://vcac-be.eng.vmware.com"));
        assertFalse(this.resolver.isCertsTrusted());
        assertNotNull(this.resolver.getCertificate());

    }
}
