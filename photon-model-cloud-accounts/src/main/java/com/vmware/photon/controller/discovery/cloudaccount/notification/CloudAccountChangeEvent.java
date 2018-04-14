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

import com.vmware.photon.controller.discovery.cloudaccount.CloudAccountApiService;
import com.vmware.photon.controller.discovery.notification.event.NotificationChangeEvent;

/**
 * Event to represent cloud account changes.
 */
public class CloudAccountChangeEvent extends NotificationChangeEvent {

    private CloudAccountApiService.CloudAccountViewState prevState;
    private CloudAccountApiService.CloudAccountViewState newState;
    private Action actionType;

    public CloudAccountChangeEvent(CloudAccountApiService.CloudAccountViewState prevState,
            CloudAccountApiService.CloudAccountViewState newState, Action actionType) {
        super(EventType.CLOUD_ACCOUNT);
        this.prevState = prevState;
        this.newState = newState;
        this.actionType = actionType;
    }

    /**
     * Returns the previous state of CloudAccount, if exists any.
     */
    public CloudAccountApiService.CloudAccountViewState getPrevState() {
        return this.prevState;
    }

    /**
     * Returns the new state of CloudAccount, if exists any.
     */
    public CloudAccountApiService.CloudAccountViewState getNewState() {
        return this.newState;
    }

    /**
     * Returns the Action {CREATE, UPDATE, DELETE} that happened to the CloudAccount in the context.
     */
    public Action getActionType() {
        return this.actionType;
    }

    @Override
    public String toString() {
        return "CloudAccountViewStateChangeEvent{" +
                "prevState=" + this.prevState +
                ", newState=" + this.newState +
                ", actionType=" + this.actionType +
                "} " + super.toString();
    }
}