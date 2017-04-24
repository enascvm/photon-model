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
import java.util.stream.Collectors;

/**
 * Helper class to build schemas with a dsl-like syntax
 */
public class SchemaBuilder {
    private Schema schema;
    private HashMap<String, SchemaFieldBuilder> fields;
    private SchemaFieldBuilder parent;

    public SchemaBuilder() {
        this.schema = new Schema();
        this.fields = new HashMap<>();
    }

    protected SchemaBuilder(SchemaFieldBuilder fieldBuilder) {
        this();
        this.parent = fieldBuilder;
    }

    public SchemaFieldBuilder addField(String fieldName) {
        SchemaFieldBuilder fieldBuilder = new SchemaFieldBuilder(this);
        this.fields.put(fieldName, fieldBuilder);
        return fieldBuilder;
    }

    public SchemaBuilder withName(String name) {
        this.schema.name = name;
        return this;
    }

    public SchemaBuilder withDescription(String description) {
        this.schema.description = description;
        return this;
    }

    public Schema build() {
        this.schema.fields = this.fields
                .entrySet().stream()
                .collect(Collectors
                        .toMap(entry -> entry.getKey(), entry -> entry.getValue().build()));
        return this.schema;
    }

    public SchemaFieldBuilder done() {
        return this.parent;
    }
}