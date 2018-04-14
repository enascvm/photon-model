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

/**
 * Event to represent enumeration completion.
 */
public class EnumerationCompleteEvent extends NotificationChangeEvent {

    public enum EnumerationStatus {FINISHED, FAILED, CANCELLED}

    private String cloudAccountLink;
    private EnumerationStatus status;

    public EnumerationCompleteEvent(String cloudAccountLink, EnumerationStatus status) {
        super(EventType.ENUMERATION);
        this.cloudAccountLink = cloudAccountLink;
        this.status = status;
    }

    /**
     * Returns the name of cloud account for which enumeration has been performed.
     */
    public String getCloudAccountLink() {
        return this.cloudAccountLink;
    }

    /**
     * Returns the status of the enumeration process.
     */
    public EnumerationStatus getStatus() {
        return this.status;
    }

    @Override
    public String toString() {
        return "EnumerationCompleteEvent{" +
                "cloudAccountLink=" + this.cloudAccountLink +
                ", status=" + this.status +
                "} " + super.toString();
    }
}
