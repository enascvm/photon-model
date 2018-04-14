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

package com.vmware.photon.controller.discovery.common;

import java.util.Objects;

import com.vmware.photon.controller.discovery.common.CloudAccountConstants.UpdateAction;
import com.vmware.xenon.common.ServiceDocument.Documentation;

/**
 * An API-friendly representation of an update action on the tag field. To be used for PATCH
 * operations.
 */
public class TagFieldUpdate {
    @Documentation(description = "The action to perform on the tag.")
    public UpdateAction action;

    @Documentation(description = "The value to use for the action.")
    public TagViewState value;

    public TagFieldUpdate() {}

    public TagFieldUpdate(UpdateAction tagAction, String tagKey, String tagValue) {
        this.action = tagAction;
        this.value = new TagViewState(tagKey, tagValue);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TagFieldUpdate that = (TagFieldUpdate) o;
        return this.action == that.action
                && Objects.equals(this.value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.action, this.value);
    }
}