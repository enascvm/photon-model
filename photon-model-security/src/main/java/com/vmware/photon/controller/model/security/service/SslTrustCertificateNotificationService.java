/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
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

import static com.vmware.photon.controller.model.UriPaths.CONFIG;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import com.vmware.photon.controller.model.security.service.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.photon.controller.model.security.ssl.ServerX509TrustManager;
import com.vmware.xenon.common.NodeSelectorService.SelectOwnerResponse;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * A helper service responsible to broadcast certificate change notifications to all the nodes in
 * the group.
 */
public class SslTrustCertificateNotificationService extends StatelessService {
    public static final String SELF_LINK = CONFIG + "/certificate-change-notification";

    @Override
    public void handlePost(Operation post) {
        SslTrustCertificateState certificate = post.getBody(SslTrustCertificateState.class);
        post.complete();

        logInfo(() -> String.format("Received change notification for "
                + "certificate with alias %s", certificate.getAlias()));

        ServerX509TrustManager instance = ServerX509TrustManager.getInstance();
        if (instance != null) {
            instance.handleCertificateChange(certificate);
        }
    }

    /**
     * Broadcasts a change notification about the given certificate state. The notification is then
     * received by each node in the group (including the current one) in the
     * {@link #handlePost} method.
     */
    public static void fireCertificateChangeNotification(ServiceHost host,
            SslTrustCertificateState cert) {
        host.log(Level.INFO, () -> String.format("Broadcasting change notification for "
                + "certificate with alias %s", cert.getAlias()));
        Operation notificationOp = Operation
                .createPost(host, SslTrustCertificateNotificationService.SELF_LINK)
                .setReferer(host.getUri())
                .setBodyNoCloning(cert)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.log(Level.SEVERE,
                                "Failure broadcasting change notification for "
                                        + "certificate with alias %s: %s",
                                cert.getAlias(),
                                Utils.toString(e));
                    }
                });
        host.broadcastRequest(
                ServiceUriPaths.DEFAULT_NODE_SELECTOR, false, notificationOp);
    }

    /**
     * Helper that will execute the given Runnable only if the current node owns the given document.
     */
    public static void executeIfLocalOwner(ServiceHost host, String
            documentSelfLink, Runnable runnable) {
        Operation op = Operation
                .createPost(null)
                .setExpiration(Utils.fromNowMicrosUtc(TimeUnit.SECONDS.toMicros(10)))
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.log(Level.SEVERE, "Error selecting owner for document %s: %s",
                                documentSelfLink, Utils.toString(e));
                    } else {
                        SelectOwnerResponse response = o.getBody(SelectOwnerResponse.class);
                        if (response.isLocalHostOwner) {
                            runnable.run();
                        } else {
                            host.log(Level.INFO, () ->
                                    String.format("Not a local owner for document %s",
                                            documentSelfLink));
                        }
                    }
                });
        host.selectOwner(ServiceUriPaths.DEFAULT_NODE_SELECTOR, documentSelfLink, op);
    }
}
