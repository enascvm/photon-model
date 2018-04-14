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

package com.vmware.photon.controller.discovery.notification.event;

import com.vmware.xenon.common.Utils;

/**
 * Base class for notification changes from discovery service to other services.
 */
public class NotificationChangeEvent {

    public enum EventType {CLOUD_ACCOUNT, RESOURCE_GROUP, ENUMERATION}

    public enum Action {CREATE, UPDATE, DELETE}

    private EventType eventType;
    private final long eventTime;

    public NotificationChangeEvent(EventType eventType) {
        this.eventType = eventType;
        this.eventTime = Utils.getNowMicrosUtc();
    }

    public EventType getEventType() {
        return this.eventType;
    }

    public long getEventTime() {
        return this.eventTime;
    }

    @Override
    public String toString() {
        return "NotificationChangeEvent{" +
                "eventType=" + this.eventType +
                ", eventTime=" + this.eventTime +
                '}';
    }
}
