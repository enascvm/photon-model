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

import com.vmware.xenon.common.ServiceDocument.Documentation;

/**
 * An API-friendly representation of the TagState.
 * Each tag will have information on the key and value pairs.
 */
public class TagViewState {
    @Documentation(description = "Tag key")
    public String key;

    @Documentation(description = "Tag value")
    public String value;

    public TagViewState(String tagKey, String tagValue) {
        this.key = tagKey;
        this.value = tagValue;
    }

    /**
     * Helper for determining null or empty content of TagViewState
     */
    public boolean isEmpty() {
        return (this.key == null || this.value == null || this.key.isEmpty() || this.value.isEmpty());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TagViewState that = (TagViewState) o;
        return this.key.equals(that.key) && this.value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.key, this.value);
    }
}
