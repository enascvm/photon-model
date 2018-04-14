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
 * An API-friendly representation of an update action on a collection field. To be used for PATCH
 * operations.
 */
public class CollectionStringFieldUpdate {
    @Documentation(description = "The action to perform on the collection.")
    public UpdateAction action;

    @Documentation(description = "The value to use for the action.")
    public String value;

    public CollectionStringFieldUpdate() {}

    public CollectionStringFieldUpdate(UpdateAction action, String value) {
        this.action = action;
        this.value = value;
    }

    public static CollectionStringFieldUpdate create(UpdateAction action, String value) {
        return new CollectionStringFieldUpdate(action, value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CollectionStringFieldUpdate that = (CollectionStringFieldUpdate) o;
        return this.action == that.action &&
                Objects.equals(this.value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.action, this.value);
    }
}
