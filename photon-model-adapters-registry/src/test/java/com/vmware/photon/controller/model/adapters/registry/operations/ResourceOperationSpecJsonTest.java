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

package com.vmware.photon.controller.model.adapters.registry.operations;

import java.net.URI;
import java.util.HashMap;
import java.util.logging.Logger;

import org.junit.Assert;
import org.junit.Test;

import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceOperationSpec;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceType;
import com.vmware.photon.controller.model.data.Schema;
import com.vmware.photon.controller.model.data.SchemaBuilder;
import com.vmware.photon.controller.model.data.SchemaField;
import com.vmware.photon.controller.model.data.SchemaField.Type;
import com.vmware.xenon.common.Utils;

public class ResourceOperationSpecJsonTest {
    private static final String EX_ONE = "extensionOne";
    private static final String EX_TWO = "extensionTwo";

    private Logger logger = Logger.getLogger(getClass().getName());

    @Test
    public void testRoundtrip() {
        ResourceOperationSpec origSpec = new ResourceOperationSpec();
        origSpec.operation = "oper";
        origSpec.targetCriteria = "true";
        origSpec.adapterReference = URI.create("http://localhost");
        origSpec.description = "OP desc";
        origSpec.name = "OP name";
        origSpec.resourceType = ResourceType.COMPUTE;
        origSpec.endpointType = "myEP";

        //@formatter:off
        origSpec.schema = new SchemaBuilder()
                .withName("schema1")
                .addField("f1")
                .withDataType(SchemaField.DATATYPE_STRING)
                .withLabel("F One")
                .withType(Type.VALUE)
                .done()
                .build();

        ExtensionOne origExtensionOne = new ExtensionOne();
        origExtensionOne.prop1 = "property one";
        origExtensionOne.prop2 = URI.create("urn:prop2:value");

        ExtensionTwo origExtensionTwo = new ExtensionTwo();
        origExtensionTwo.int1 = 42;
        origSpec.extensions = new HashMap<>();
        origSpec.extensions.put(EX_ONE, Utils.toJson(origExtensionOne));
        origSpec.extensions.put(EX_TWO, Utils.toJson(origExtensionTwo));
        //@formatter:on

        String json = Utils.toJson(origSpec);
        this.logger.info("json: " + json);
        ResourceOperationSpec trgtSpec = Utils.fromJson(json, ResourceOperationSpec.class);

        Assert.assertNotNull(trgtSpec.schema);
        Schema origSchema = origSpec.schema;
        Schema trgtSchema = trgtSpec.schema;
        Assert.assertEquals(origSchema.name, trgtSchema.name);
        Assert.assertEquals(origSchema.fields, trgtSchema.fields);

        String jsonExtensionOne = trgtSpec.extensions.get(EX_ONE);
        {
            Assert.assertNotNull(jsonExtensionOne);
            this.logger.info("jsonExtensionOne: " + jsonExtensionOne);
            BaseExtension be = Utils.fromJson(jsonExtensionOne, BaseExtension.class);
            Assert.assertEquals(ExtensionOne.KIND, be.documentKind);
            ExtensionOne trgtExtensionOne = Utils.fromJson(jsonExtensionOne, ExtensionOne.class);
            this.logger.info("trgtExtensionOne: " + trgtExtensionOne);
            Assert.assertEquals(origExtensionOne.prop1, trgtExtensionOne.prop1);
            Assert.assertEquals(origExtensionOne.prop2, trgtExtensionOne.prop2);
        }

        String jsonExtensionTwo = trgtSpec.extensions.get(EX_TWO);
        {
            Assert.assertNotNull(jsonExtensionTwo);
            this.logger.info("jsonExtensionTwo: " + jsonExtensionTwo);
            BaseExtension be = Utils.fromJson(jsonExtensionTwo, BaseExtension.class);
            Assert.assertEquals(ExtensionTwo.KIND, be.documentKind);
            ExtensionTwo trgtExtensionTwo = Utils.fromJson(jsonExtensionTwo, ExtensionTwo.class);
            this.logger.info("trgtExtensionTwo: " + trgtExtensionTwo);
            Assert.assertEquals(origExtensionTwo.int1, trgtExtensionTwo.int1);
        }
    }

    public static class BaseExtension {
        public static final String KIND = Utils.buildKind(BaseExtension.class);

        public String documentKind = KIND;
    }

    public static class ExtensionOne extends BaseExtension {
        public static final String KIND = Utils.buildKind(ExtensionOne.class);

        public String prop1;

        public URI prop2;

        {
            super.documentKind = KIND;
        }

        @Override
        public String toString() {
            return String.format("%s[prop1=%s, prop2=%s]",
                    getClass().getSimpleName(), this.prop1, this.prop2);
        }
    }

    public static class ExtensionTwo extends BaseExtension {
        public static final String KIND = Utils.buildKind(ExtensionTwo.class);

        public int int1;

        {
            super.documentKind = KIND;
        }

        @Override
        public String toString() {
            return String.format("%s[int1=%s]",
                    getClass().getSimpleName(), this.int1);
        }
    }
}
