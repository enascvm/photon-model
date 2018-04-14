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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.vmware.xenon.common.ServiceDocument.Documentation;

/**
 * The class that holds the metadata about a particular field.
 */
public class ResourceProperty {

    @Documentation(description = "The property name")
    public String name;

    @Documentation(description = "The property type")
    public String type;

    @Documentation(description = "Flag to determine if this property can be used as a sortable field.")
    public boolean isSortable;

    @Documentation(description = "Flag to determine if this property can be used as a filterable field.")
    public boolean isFilterable;

    @Documentation(description = "The possible values for the given property")
    public List<String> values;

    public ResourceProperty(String name, String type) {
        this.name = name;
        this.type = type;
        this.isSortable = false;
        this.isFilterable = false;
        this.values = new ArrayList<>();
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.name, this.type, this.isSortable, this.values);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ResourceProperty)) {
            return false;
        }
        ResourceProperty resourceProperty = (ResourceProperty) o;
        return this.isSortable == resourceProperty.isSortable &&
                Objects.equals(this.name, resourceProperty.name) &&
                Objects.equals(this.type, resourceProperty.type) &&
                Objects.deepEquals(this.values, resourceProperty.values);
    }

    public static class NameComparator implements Comparator<ResourceProperty>, Serializable {

        private static final long serialVersionUID = 1L;

        @Override
        public int compare(ResourceProperty resourceProperty1,
                ResourceProperty resourceProperty2) {
            return resourceProperty1.name.compareTo(resourceProperty2.name);
        }
    }
}
