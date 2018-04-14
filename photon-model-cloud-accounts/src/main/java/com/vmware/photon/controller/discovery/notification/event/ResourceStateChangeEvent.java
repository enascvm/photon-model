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

import com.vmware.photon.controller.model.resources.ResourceState;

/**
 * Event to represent all the Photon Model resource state changes.
 */
public class ResourceStateChangeEvent extends NotificationChangeEvent {

    private ResourceState prevState;
    private ResourceState newState;
    private Action actionType;

    public ResourceStateChangeEvent(EventType eventType,
                                    ResourceState prevState,
                                    ResourceState newState,
                                    Action actionType) {
        super(eventType);
        this.prevState = prevState;
        this.newState = newState;
        this.actionType = actionType;
    }

    /**
     * Returns the previous state of ResourceState, if exists any.
     */
    public ResourceState getPrevState() {
        return this.prevState;
    }

    /**
     * Returns the new state of ResourceState, if exists any.
     */
    public ResourceState getNewState() {
        return this.newState;
    }

    /**
     * Returns the Action {CREATE, UPDATE, DELETE} that happened to the ResourceState in the context.
     */
    public Action getActionType() {
        return this.actionType;
    }

    @Override
    public String toString() {
        return "ResourceStateChangeEvent{" +
                "prevState=" + this.prevState +
                ", newState=" + this.newState +
                ", actionType=" + this.actionType +
                "} " + super.toString();
    }
}
