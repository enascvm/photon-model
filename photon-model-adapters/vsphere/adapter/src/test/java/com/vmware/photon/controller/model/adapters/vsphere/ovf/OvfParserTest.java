/*
 * Copyright (c) 2015-2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.model.adapters.vsphere.ovf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;

import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.xenon.common.Utils;

public class OvfParserTest {
    @Test
    public void test() throws IOException, SAXException {
        OvfParser parser = new OvfParser();
        Document ovfDoc = parser
                .retrieveDescriptor(new File("src/test/resources/vcenter.ovf").toURI());

        ComputeDescription template = new ComputeDescription();
        template.instanceAdapterReference = URI.create("http://test");

        List<ComputeDescription> results = parser.parse(ovfDoc, template);

        System.out.println(Utils.toJsonHtml(results));
        // all hw configurations are converted
        assertEquals(9, results.size());

        // template properties are copied
        for (ComputeDescription description : results) {
            assertEquals(description.instanceAdapterReference, template.instanceAdapterReference);
        }

        // network, properties and hardware config translated
        for (ComputeDescription description : results) {
            assertTrue(description.customProperties
                    .containsKey("ovf.prop:guestinfo.cis.appliance.net.addr.family"));
            assertTrue(description.customProperties
                    .containsKey("ovf.prop:guestinfo.cis.appliance.root.passwd"));
            assertTrue(description.customProperties.containsKey("ovf.net:Network 1"));

            assertTrue(description.cpuCount > 0);
            assertTrue(description.totalMemoryBytes > 0);
        }
    }
}
