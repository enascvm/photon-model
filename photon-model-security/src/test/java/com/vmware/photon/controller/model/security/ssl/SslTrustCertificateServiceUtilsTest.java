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

import java.security.cert.X509Certificate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.security.BaseTestCase;
import com.vmware.photon.controller.model.security.CommonTestStateFactory;
import com.vmware.photon.controller.model.security.service.SslTrustCertificateFactoryService;
import com.vmware.photon.controller.model.security.service.SslTrustCertificateService;
import com.vmware.photon.controller.model.security.service.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.photon.controller.model.security.util.CertificateUtil;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

public class SslTrustCertificateServiceUtilsTest extends BaseTestCase {
    private final Logger logger = Logger.getLogger(getClass().getName());
    public static final long MAX_TIMEOUT_TO_WAIT_IN_MILLIS = TimeUnit.SECONDS.toMillis(30);

    @Before
    public void before() throws Throwable {
        super.before();
        this.host.startService(new SslTrustCertificateFactoryService());
        waitForServiceAvailability(SslTrustCertificateService.FACTORY_LINK);
    }

    @Test
    public void registerAndDeleteCertificate() throws Throwable {
        CountDownLatch register = new CountDownLatch(1);
        CountDownLatch delete = new CountDownLatch(1);
        SslTrustCertificateServiceUtils.subscribe(this.host, consumer(register, delete));

        SslTrustCertificateState certState = new SslTrustCertificateState();
        String certPEM = CommonTestStateFactory.getFileContent("test_ssl_trust.PEM").trim();
        X509Certificate[] certificates =
                CertificateUtil.createCertificateChain(certPEM);
        // Populate the certificate properties based on the first (end server) certificate
        X509Certificate endCertificate = certificates[0];
        certState.certificate =
                CertificateUtil.toPEMformat(endCertificate);
        SslTrustCertificateState.populateCertificateProperties(
                certState,
                endCertificate);

        this.logger.info(String.format(
                "Register certificate with common name: %s and fingerprint: %s in trust store",
                certState.commonName, certState.fingerprint));
        //save untrusted certificate to the trust store
        this.host.send(
                Operation.createPost(
                        this.host,
                        SslTrustCertificateService.FACTORY_LINK)
                        .setBody(certState)
                        .addPragmaDirective(
                                Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
        );
        if (!register.await(MAX_TIMEOUT_TO_WAIT_IN_MILLIS, TimeUnit.MILLISECONDS)) {
            Assert.fail("No register notification received");
        }

        String certDocumentId = CertificateUtil.generatePureFingerPrint(
                CertificateUtil.createCertificateChain(certState.certificate));
        this.logger.info("Certificate " + certDocumentId + " registered.");

        String deleteLink = UriUtils
                .buildUriPath(SslTrustCertificateService.FACTORY_LINK, certDocumentId);
        this.host.send(
                Operation.createDelete(this.host, deleteLink)
        );
        if (!delete.await(MAX_TIMEOUT_TO_WAIT_IN_MILLIS, TimeUnit.MILLISECONDS)) {
            Assert.fail("No delete notification received for " + deleteLink);
        }
        this.logger.info("Certificate " + certDocumentId + " deleted.");
    }

    private Consumer<Operation> consumer(
            CountDownLatch registerLatch, CountDownLatch deleteLatch) {
        return operation -> {
            Utils.log(getClass(), getClass().getName(), Level.WARNING,
                    "process certificate changed for operation %s", operation.toLogString());
            QueryTask queryTask = operation.getBody(QueryTask.class);
            if (queryTask.results != null && queryTask.results.documentLinks != null
                    && !queryTask.results.documentLinks.isEmpty()) {

                queryTask.results.documents.values().stream().forEach(doc -> {
                    SslTrustCertificateState cert = Utils
                            .fromJson(doc, SslTrustCertificateState.class);
                    if (Action.DELETE.toString().equals(cert.documentUpdateAction)) {
                        deleteLatch.countDown();
                    } else {
                        registerLatch.countDown();
                    }
                });
            } else {
                Utils.log(getClass(), getClass().getName(), Level.WARNING,
                        "No document links for operation %s", operation.toLogString());
            }
            operation.complete();
        };
    }
}
