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

import static com.vmware.photon.controller.discovery.notification.NotificationConstants.LEMANS_TENANT_ID_HEADER;
import static com.vmware.photon.controller.model.UriPaths.CLOUD_ACCOUNT_API_SERVICE;

import java.net.URI;
import java.util.logging.Level;

import com.vmware.photon.controller.discovery.notification.event.NotificationChangeEvent;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Helper utilities for notification service.
 */
public class NotificationUtils {

    /**
     * Helper method to send change event to Notification service.
     */
    public static void sendNotification(Service service, URI referrer, NotificationChangeEvent changeEvent,
            String cspOrgIdHash) {

        // Skip if NotificationService is not enabled
        if (!NotificationService.isEnabled()) {
            return;
        }

        OperationContext operationContext = OperationContext.getOperationContext();
        Operation getOperation = Operation.createGet(service.getHost(), NotificationManagementService.SINGLETON_LINK)
                .setReferer(referrer);
        if (OperationContext.getAuthorizationContext() != null &&
                !OperationContext.getAuthorizationContext().isSystemUser()) {
            service.setAuthorizationContext(getOperation, service.getSystemAuthorizationContext());
        }
        DeferredResult<Operation> deferredResult = service.sendWithDeferredResult(getOperation);

        deferredResult.whenComplete(
                (o, e) -> {
                    OperationContext.restoreOperationContext(operationContext);
                    if (e != null) {
                        service.getHost().log(Level.INFO, "Failed to get the state of " +
                                "NotificationManagementService: %s", Utils.toString(e));
                        return;
                    }

                    NotificationManagementService.NotificationState state =
                            o.getBody(NotificationManagementService.NotificationState.class);
                    if (!state.isEnabled) {
                        // skip it
                        return;
                    }

                    // notify the event
                    Operation postOperation =
                            Operation.createPost(UriUtils.buildUri(service.getHost(), NotificationService.SELF_LINK))
                                    .setBody(changeEvent)
                                    .setReferer(referrer)
                                    .setCompletion((opn, exn) -> {
                                        OperationContext.restoreOperationContext(operationContext);
                                        if (exn != null) {
                                            // Log failure message
                                            service.getHost().log(Level.WARNING, "Failed to notify " +
                                                    "change: %s, exception: %s", changeEvent, Utils.toString(exn));
                                            return;
                                        }
                                        // Log success message
                                        service.getHost().log(Level.INFO, "Successfully notified change: %s", changeEvent);
                                    });

                    if (OperationContext.getAuthorizationContext() != null &&
                            !OperationContext.getAuthorizationContext().isSystemUser()) {
                        service.setAuthorizationContext(postOperation, service.getSystemAuthorizationContext());
                    }
                    if (cspOrgIdHash != null) {
                        postOperation.addRequestHeader(LEMANS_TENANT_ID_HEADER, cspOrgIdHash);
                    }
                    service.getHost().sendRequest(postOperation);
                });
    }

    /**
     * Helper method to get CloudAccountLink from given endpointLink.
     */
    public static String getCloudAccountLink(String endpointLink) {
        String endpointId = UriUtils.getLastPathSegment(endpointLink);
        return UriUtils.buildUriPath(CLOUD_ACCOUNT_API_SERVICE, endpointId);
    }
}
