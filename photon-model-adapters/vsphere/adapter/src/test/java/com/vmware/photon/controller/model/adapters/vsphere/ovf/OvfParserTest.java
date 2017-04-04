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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;

import org.apache.http.client.HttpClient;
import org.junit.Ignore;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.xenon.common.Utils;

@Ignore
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

            assertTrue(description.cpuCount > 0);
            assertTrue(description.totalMemoryBytes > 0);
        }

        List<String> nets = parser.extractNetworks(ovfDoc);
        assertEquals(1, nets.size());
        assertEquals("Network 1", nets.get(0));
    }

    /**
     * Test is ignore because it is slow.
     * @throws IOException
     */
    @Test
    @Ignore
    public void tarsAreDownloadedAndExtracted() throws IOException {
        HttpClient client = OvfRetriever.newInsecureClient();
        OvfRetriever retriever = new OvfRetriever(client);

        String photonOs = "https://bintray.com/vmware/photon/download_file?file_path=photon-custom-hw10-1.0-13c08b6.ova";
        URI ovfUri = retriever.downloadIfOva(URI.create(photonOs));

        assertTrue(ovfUri.toString().endsWith("photon-custom-hw10.ovf"));
    }

    @Test
    public void nonTarsAreNoProcessed() throws IOException {
        HttpClient client = OvfRetriever.newInsecureClient();
        OvfRetriever retriever = new OvfRetriever(client);

        URI definitelyNotTar = URI.create("https://vmware.github.io/photon/");
        URI ovfUri = retriever.downloadIfOva(definitelyNotTar);

        assertSame(ovfUri, definitelyNotTar);
    }

    @Test
    public void ovfsAreNotProcessed() throws IOException {
        HttpClient client = OvfRetriever.newInsecureClient();
        OvfRetriever retriever = new OvfRetriever(client);

        URI anOvf = new File("src/test/resources/vcenter.ovf").toURI();
        URI ovfUri = retriever.downloadIfOva(anOvf);

        assertSame(ovfUri, anOvf);
    }

    @Test
    public void filesAreNotProcessed() throws IOException {
        HttpClient client = OvfRetriever.newInsecureClient();
        OvfRetriever retriever = new OvfRetriever(client);

        URI anOvf = new File("src/test/java/com/vmware/photon/controller/model/adapters/vsphere/ovf/OvfParserTest.java")
                .toURI();
        URI ovfUri = retriever.downloadIfOva(anOvf);

        assertSame(ovfUri, anOvf);
    }
}
