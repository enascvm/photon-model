/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.model.data;

import java.util.HashMap;

import com.vmware.photon.controller.model.data.SchemaField.Constraint;
import com.vmware.photon.controller.model.data.SchemaField.Type;
import com.vmware.photon.controller.model.util.AssertUtil;

/**
 * A builder class for constructing {@link SchemaField}s.
 */
public class SchemaFieldBuilder {
    private SchemaField field;
    private SchemaBuilder schema;
    private SchemaBuilder parent;

    protected SchemaFieldBuilder(SchemaBuilder parent) {
        this.field = new SchemaField();
        this.parent = parent;
    }

    public SchemaFieldBuilder withDataType(String dataType) {
        AssertUtil.assertNotEmpty(dataType, "'dataType' cannot be null.");
        this.field.dataType = dataType;
        return this;
    }

    public SchemaFieldBuilder withType(Type type) {
        this.field.type = type;
        return this;
    }

    public SchemaBuilder withSchema() {
        this.schema = new SchemaBuilder(this);
        return this.schema;
    }

    public SchemaFieldBuilder withLabel(String label) {
        this.field.label = label;
        return this;
    }

    public SchemaFieldBuilder withDescription(String description) {
        this.field.description = description;
        return this;
    }

    public SchemaFieldBuilder withConstraint(Constraint constraint, Object value) {
        this.field.constraints = this.field.constraints != null ? this.field
                .constraints : new HashMap<>();
        this.field.constraints.put(constraint, value);
        return this;
    }

    public SchemaBuilder done() {
        return this.parent;
    }

    public SchemaField build() {
        if (this.schema != null) {
            this.field.schema = this.schema.build();
        }
        return this.field;
    }
}
