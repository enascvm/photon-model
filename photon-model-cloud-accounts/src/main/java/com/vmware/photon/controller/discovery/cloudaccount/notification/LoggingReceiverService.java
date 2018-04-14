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

package com.vmware.photon.controller.discovery.cloudaccount.notification;

import java.util.concurrent.atomic.AtomicLong;

import com.vmware.photon.controller.discovery.notification.event.EnumerationCompleteEvent;
import com.vmware.photon.controller.discovery.notification.event.NotificationChangeEvent;
import com.vmware.photon.controller.discovery.notification.event.ResourceStateChangeEvent;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;

/**
 * A very simple receiver service for development and debugging use. This service will receive messages as the receiver
 * of a stream and will simply log the event and respond with route information regarding the origin of the request.
 */
public class LoggingReceiverService extends StatelessService {

    public static final String SELF_LINK = UriPaths.NOTIFICATION_LOGGING;
    public final State cachedState = new State();

    public LoggingReceiverService() {
        super(ServiceDocument.class);
    }

    public static class State {
        public AtomicLong messagesReceivedCount = new AtomicLong(0);
        public Action receivedAction;
        public String referer;
        public String receivedLink;
        public String rawBody;
    }

    @Override
    public void authorizeRequest(Operation op) {
        op.complete();
    }

    @Override
    public void handlePost(Operation postOp) {
        this.cachedState.messagesReceivedCount.incrementAndGet();
        populateStateFromRequest(postOp);
        postOp.setBody(this.cachedState);
        postOp.complete();
    }

    @Override
    public void handleGet(Operation get) {
        get.setBodyNoCloning(Utils.clone(this.cachedState));
        get.complete();
    }

    private void populateStateFromRequest(Operation post) {
        this.cachedState.receivedAction = post.getAction();
        this.cachedState.referer = post.getRefererAsString();
        this.cachedState.receivedLink = post.getUri().getPath();

        NotificationChangeEvent changeEvent = post.getBody(NotificationChangeEvent.class);
        if (NotificationChangeEvent.EventType.RESOURCE_GROUP.equals(changeEvent.getEventType())) {
            changeEvent = post.getBody(ResourceStateChangeEvent.class);
        } else if (NotificationChangeEvent.EventType.ENUMERATION.equals(changeEvent.getEventType())) {
            changeEvent = post.getBody(EnumerationCompleteEvent.class);
        } else if (NotificationChangeEvent.EventType.CLOUD_ACCOUNT.equals(changeEvent.getEventType())) {
            changeEvent = post.getBody(CloudAccountChangeEvent.class);
        }
        this.cachedState.rawBody = Utils.toJsonHtml(changeEvent);
    }
}
