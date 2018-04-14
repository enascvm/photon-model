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

package com.vmware.photon.controller.discovery.notification;

import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.CSP_AUTHENTICATION_SCHEME_NAME;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.CSP_DISCOVERY_INSTANCE_NAME;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.CSP_URI;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.LEMANS_GATEWAY_HOST;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.SERVICE_AUTH_SECRET;
import static com.vmware.photon.controller.discovery.csp.CspUriPaths.CSP_AUTHORIZE;
import static com.vmware.photon.controller.discovery.notification.NotificationConstants.LEMANS_TENANT_ID_HEADER;
import static com.vmware.photon.controller.discovery.notification.NotificationConstants.STREAM_NAME;
import static com.vmware.photon.controller.model.UriPaths.LEMANS_STREAM;
import static com.vmware.photon.controller.model.UriPaths.NOTIFICATION_SERVICE;
import static com.vmware.xenon.common.Operation.MEDIA_TYPE_APPLICATION_X_WWW_FORM_ENCODED;

import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import com.vmware.photon.controller.discovery.csp.CspAccessToken;
import com.vmware.photon.controller.discovery.notification.event.NotificationChangeEvent;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.authn.BasicAuthenticationUtils;

/**
 * Service that posts the notification events to Le-Mans gateway.
 */
public class NotificationService extends StatelessService {

    public static final String SELF_LINK = NOTIFICATION_SERVICE;

    private final String lemansGatewayHostString;
    private final String serviceLink;

    private URI lemansGatewayHost;
    private URI cspURI;
    private String clientCredentialsToken;

    public NotificationService(String lemansGatewayHost) {
        this(lemansGatewayHost, UriUtils.buildUriPath(LEMANS_STREAM, STREAM_NAME));
    }

    public NotificationService(String lemansGatewayHostString, String serviceLink) {

        super.toggleOption(ServiceOption.PERIODIC_MAINTENANCE, true);
        super.setMaintenanceIntervalMicros(TimeUnit.MINUTES.toMicros(15));

        if (lemansGatewayHostString == null || lemansGatewayHostString.isEmpty()) {
            String error = "Le-Mans Gateway service URI is not provided.";
            logSevere(error);
            throw new IllegalArgumentException(error);
        }

        this.lemansGatewayHostString = lemansGatewayHostString;
        this.serviceLink = serviceLink;
        log(Level.INFO, "Notification service initialized with Le-Mans Gateway service URI: %s, " +
                "serviceLink: %s", this.lemansGatewayHostString, this.serviceLink);
    }

    @Override
    public void handleStart(Operation start) {
        String cspUri = System.getProperty(CSP_URI);
        try {
            if (cspUri != null) {
                this.cspURI = new URI(cspUri);
            }

            this.lemansGatewayHost = new URI(this.lemansGatewayHostString);

            refreshAuthToken(() -> {
                start.complete();
                logInfo("Notification Service Started %s", getSelfLink());
            });
        } catch (Exception e) {
            start.fail(e);
        }
    }

    @Override
    public void handlePost(Operation post) {
        NotificationChangeEvent event = post.getBody(NotificationChangeEvent.class);
        log(Level.INFO, "Pushing notification to Le-Mans gateway stream for the event: %s", event);

        // send messages on the stream
        postMessage(post.getBodyRaw(), post.getRequestHeader(LEMANS_TENANT_ID_HEADER));
        post.complete();
    }


    @Override
    public void handlePeriodicMaintenance(Operation maintOp) {
        try {
            refreshAuthToken(maintOp::complete);
        } catch (Exception e) {
            maintOp.fail(e);
        }
    }

    private void refreshAuthToken(Runnable callback) {
        if (this.cspURI == null) {
            callback.run();
            return;
        }
        Operation authOp = Operation.createPost(UriUtils.buildUri(this.cspURI, CSP_AUTHORIZE))
                .addRequestHeader(Operation.AUTHORIZATION_HEADER,  getEncodedCspAuthCredentials())
                .setContentType(MEDIA_TYPE_APPLICATION_X_WWW_FORM_ENCODED)
                .setBody("grant_type=client_credentials");

        sendWithDeferredResult(authOp, CspAccessToken.class)
                .whenComplete((cspAuthTokenState, ex) -> {
                    if (ex != null) {
                        logWarning("Unable to get CSP auth token from CSP at %s, %s", authOp.getUri(),
                                Utils.toString(ex));
                        callback.run();
                        return;
                    }
                    if (cspAuthTokenState.cspAuthToken == null || cspAuthTokenState.cspAuthToken.isEmpty()) {
                        logWarning("CSP auth token from CSP at %s is empty!", authOp.getUri());
                        callback.run();
                        return;
                    }

                    this.clientCredentialsToken = cspAuthTokenState.cspAuthToken;
                    logInfo("Successfully refreshed CSP auth token %s", this.clientCredentialsToken);
                    callback.run();
                });
    }

    private void postMessage(Object body, String cspOrgIdHash) {
        Operation post = Operation.createPost(UriUtils.buildUri(this.lemansGatewayHost, this.serviceLink))
                .setBody(body)
                .setReferer(getHost().getUri())
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                log(Level.SEVERE, "Post to Le-Mans gateway stream failed, %s",
                                        Utils.toString(e));
                                return;
                            }
                            log(Level.INFO, "Post to Le-Mans gateway steam is successful");
                        });

        post.addRequestHeader(Operation.REQUEST_AUTH_TOKEN_HEADER,
                CSP_AUTHENTICATION_SCHEME_NAME + this.clientCredentialsToken);

        // add tenant id
        if (cspOrgIdHash != null) {
            post.addRequestHeader(LEMANS_TENANT_ID_HEADER, cspOrgIdHash);
        }
        post.sendWith(getHost());
    }

    private String getEncodedCspAuthCredentials() {
        return BasicAuthenticationUtils.constructBasicAuth(CSP_DISCOVERY_INSTANCE_NAME,
                System.getProperty(SERVICE_AUTH_SECRET));
    }

    public static boolean isEnabled() {
        return (System.getProperty(LEMANS_GATEWAY_HOST) != null);
    }
}
