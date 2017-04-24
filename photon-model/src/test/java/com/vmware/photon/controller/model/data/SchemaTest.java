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

import static com.vmware.photon.controller.model.data.SchemaField.DATATYPE_BOOLEAN;
import static com.vmware.photon.controller.model.data.SchemaField.DATATYPE_DATETIME;
import static com.vmware.photon.controller.model.data.SchemaField.DATATYPE_STRING;

import java.util.Arrays;
import java.util.HashMap;
import java.util.logging.Logger;

import org.junit.Assert;
import org.junit.Test;

import com.vmware.photon.controller.model.data.SchemaField.Constraint;
import com.vmware.photon.controller.model.data.SchemaField.Type;
import com.vmware.xenon.common.Utils;

// @formatter:off
/**
 * Schema:
 * <pre>
     {
        "name": "test",
        "description": "test descr",
        "fields": {
            "f1": {
                "label": "field 1",
                "description": "field 1 descr",
                "dataType": "string",
                "type": "list",
                "constraints": {
                    "permissibleValues": [
                        "one",
                        "two",
                        "three"
                    ],
                    "mandatory": true
                }
            },
            "f2": {
                "label": "field 2",
                "description": "field 2 descr",
                "schema": {
                    "fields": {
                        "f2_2": {
                            "label": "field 2_2",
                            "description": "field 2_2 descr",
                            "dataType": "dateTime"
                        },
                        "f2_1": {
                            "label": "field 2_1",
                            "description": "field 2_1 descr",
                            "type": "list"
                        }
                    }
                },
                "type": "list"
            }
        }
     }
 * </pre>
 * Instance:
 * <pre>
     {
         "f1": [
             "one",
             "two"
         ],
         "f2": [
             {
                 "f2_1[1]": [
                     "f2_1[1] value_1",
                     "f2_1[1] value_2",
                     "f2_1[1] value_3"
                 ],
                 "f2_2[1]": "2017-05-05T16:39:57+03:00"
             },
             {
                 "f2_1[2]": [
                     "f2_1[2] value_1",
                     "f2_1[2] value_2"
                 ],
                 "f2_2[2]": "2017-05-06T18:39:57+03:00"
             }
         ]
     }
 * </pre>
 */
// @formatter:on
public class SchemaTest {
    private Logger logger = Logger.getLogger(getClass().getName());

    @Test
    public void testRaw() {
        Schema schema = new Schema();
        schema.name = "test";
        schema.description = "test descr";
        schema.fields = new HashMap<>();

        SchemaField f1 = new SchemaField();
        f1.label = "field 1";
        f1.description = "field 1 descr";
        f1.dataType = DATATYPE_STRING;
        f1.type = Type.LIST;
        f1.constraints = new HashMap<>();
        f1.constraints.put(Constraint.mandatory, Boolean.TRUE);
        f1.constraints.put(Constraint.permissibleValues, Arrays.asList("one", "two", "three"));
        schema.fields.put("f1", f1);

        SchemaField f2 = new SchemaField();
        f2.label = "field 2";
        f2.description = "field 2 descr";
        f2.type = Type.LIST;
        f2.schema = new Schema();
        {
            f2.schema.fields = new HashMap<>();

            SchemaField f2_1 = new SchemaField();
            f2_1.label = "field 2_1";
            f2_1.description = "field 2_1 descr";
            f2_1.type = Type.LIST;
            f2.schema.fields.put("f2_1", f2_1);

            SchemaField f2_2 = new SchemaField();
            f2_2.label = "field 2_2";
            f2_2.description = "field 2_2 descr";
            f2_2.dataType = DATATYPE_DATETIME;
            f2.schema.fields.put("f2_2", f2_2);
        }

        schema.fields.put("f2", f2);

        String toJson = Utils.toJson(schema);
        this.logger.info("toJson: " + toJson);
        Schema fromJson = Utils.fromJson(toJson, Schema.class);
        this.logger.info("fromJson: " + fromJson);
        String toJson_2 = Utils.toJson(fromJson);
        this.logger.info("toJson:" + toJson_2);
        Assert.assertEquals(toJson, toJson_2);
    }

    @Test
    public void testWithBuilder() {
        SchemaBuilder schemaBuilder = new SchemaBuilder();

        // @formatter:off
        Schema schema = schemaBuilder
                .withName("testWithBuilder")
                .withDescription("testWithBuilder desc")
                .addField("f1")
                    .withLabel("F 1")
                    .withDescription("Field 1 desc")
                    .withType(Type.LIST)
                    .withConstraint(Constraint.readOnly, true)
                .done()
                .addField("f2")
                    .withLabel("F2")
                    .withSchema()
                        .addField("f2.1")
                            .withLabel("F2-1")
                        .done()
                        .addField("f2.2")
                            .withLabel("F2-2")
                            .withDataType(DATATYPE_BOOLEAN)
                        .done()
                    .done()
                .done()
                .build();
        // @formatter:on

        String toJson = Utils.toJson(schema);
        this.logger.info("toJson: " + toJson);
        Schema fromJson = Utils.fromJson(toJson, Schema.class);
        this.logger.info("fromJson: " + fromJson);
        String toJson_2 = Utils.toJson(fromJson);
        this.logger.info("toJson:" + toJson_2);
        Assert.assertEquals(toJson, toJson_2);
    }

}
