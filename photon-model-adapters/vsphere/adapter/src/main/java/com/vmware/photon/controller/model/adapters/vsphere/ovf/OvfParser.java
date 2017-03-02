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

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.IOUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.vmware.photon.controller.model.adapters.vsphere.CustomProperties;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.xenon.common.Utils;

public class OvfParser {
    public static final String PROP_OVF_CONFIGURATION = "ovf.configuration";

    public static final String PROP_OVF_URI = "ovf.uri";

    public static final String PREFIX_OVF_PROP = "ovf.prop:";

    public static final String PROP_OVF_ARCHIVE_URI = "ova.uri";

    private XPath xpath;

    /**
     * http://blogs.vmware.com/vapp/2009/11/virtual-hardware-in-ovf-part-1.html
     */
    private static final String RESOURCE_TYPE_CPU = "3";

    private static final String RESOURCE_TYPE_MEMORY = "4";

    /**
     * Allocation units defined by OVF. A simpler guide can be found here:
     * http://opennodecloud.com/howto/2013/12/25/howto-ON-ovf-reference.html
     */
    private static Map<String, Long> multipliersByMemoryAllocationUnit = new HashMap<String, Long>() {
        private static final long serialVersionUID = 1L;

        {
            put("", 1L);

            put("KB", pow2(10));
            put("KILOBYTE", pow2(10));
            put("byte*2^10", pow2(10));

            put("MB", pow2(20));
            put("MEGABYTE", pow2(20));
            put("byte*2^20", pow2(20));

            put("GB", pow2(30));
            put("GIGABYTE", pow2(30));
            put("byte*2^30", pow2(30));

            put("TERABYTE", pow2(40));
            put("GIGABYTE", pow2(40));
            put("byte*2^40", pow2(40));
        }

        private Long pow2(long i) {
            return 1L << i;
        }
    };

    /**
     * Produces several descriptions based on the different hardware configuration defined in the OVF
     * descriptor.
     * @param doc OVF descriptor to parse
     * @param template use as a basis of the ComputeDescription.
     * @return
     */
    public List<ComputeDescription> parse(Document doc, ComputeDescription template) {
        CustomProperties cust = CustomProperties.of(template);

        NodeList props = nodes(doc,
                "/ovf:Envelope/ovf:VirtualSystem/ovf:ProductSection/ovf:Property");
        for (Element prop : iterableElements(props)) {
            String userConfigurable = attr("ovf:userConfigurable", prop);
            if (!"true".equals(userConfigurable)) {
                continue;
            }
            String key = attr("ovf:key", prop);
            Element section = (Element) prop.getParentNode();
            String instanceId = attr("ovf:instance", section);
            String classId = attr("ovf:class", section);
            String description = text(prop, "ovf:Description/text()");

            cust.put(property(classId, key, instanceId), description);
        }

        String productName = text(doc,
                "/ovf:Envelope/ovf:VirtualSystem/ovf:ProductSection/ovf:Product/text()");
        String productVersion = text(doc,
                "/ovf:Envelope/ovf:VirtualSystem/ovf:ProductSection/ovf:Version/text()");
        template.name = productName + " " + productVersion;

        NodeList hwItems = nodes(doc,
                "/ovf:Envelope/ovf:VirtualSystem/ovf:VirtualHardwareSection/ovf:Item");

        Map<String, ComputeDescription> hwByConfigName = new HashMap<>();

        for (Element item : iterableElements(hwItems)) {
            String configName = attr("ovf:configuration", item);
            ComputeDescription desc = hwByConfigName.get(configName);
            if (desc == null) {
                desc = Utils.clone(template);
                desc.documentSelfLink = UUID.randomUUID().toString();
                desc.id = "ovf-imported-" + desc.documentSelfLink;
                desc.customProperties.put(PROP_OVF_CONFIGURATION, configName);
                hwByConfigName.put(configName, desc);
            }

            String resourceType = text(item, "rasd:ResourceType/text()");
            if (RESOURCE_TYPE_CPU.equals(resourceType)) {
                long qty = Long.parseLong(text(item, "rasd:VirtualQuantity/text()"));
                desc.cpuCount = qty;
            }

            if (RESOURCE_TYPE_MEMORY.equals(resourceType)) {
                double qty = Double.parseDouble(text(item, "rasd:VirtualQuantity/text()"));
                long mult = memAllocationUnit2Multiplier(text(item, "rasd:AllocationUnits/text()"));
                desc.totalMemoryBytes = (long) (qty * mult);
            }
        }

        for (Iterator<ComputeDescription> it = hwByConfigName.values().iterator(); it.hasNext(); ) {
            ComputeDescription desc = it.next();
            if (desc.cpuCount <= 0) {
                it.remove();
            }
        }

        return new ArrayList<>(hwByConfigName.values());
    }

    /**
     * Extracts the logical names of the networks the ovf needs.
     * @param doc
     * @return
     */
    public List<String> extractNetworks(Document doc) {
        NodeList nodes = nodes(doc,
                "/ovf:Envelope/ovf:NetworkSection/ovf:Network");


        List<String> res = new ArrayList<>(2);
        for (Element item : iterableElements(nodes)) {
            res.add(attr("ovf:name", item));
        }

        return res;
    }

    /**
     * Convert a an AllocationUnit string to bytes multiplier, for example KB would produce 1024.
     * @param unit
     * @return
     */
    private long memAllocationUnit2Multiplier(String unit) {
        // remove spaces from unit, just in case
        Long res = multipliersByMemoryAllocationUnit.get(unit.replace(" ", ""));
        if (res == null) {
            throw new IllegalArgumentException("Cannot map " + unit + " to a known unit");
        } else {
            return res;
        }
    }

    /**
     * Extracts an attribute by name. Returns empty string if attribute was not found.
     * If the attr name is prefixed and not found, an attempt is made to find it without prefix
     * @param name
     * @param element
     * @return
     */
    private String attr(String name, Element element) {
        String res = text(element, "@" + name);
        int i = name.indexOf(':');
        if (res.length() == 0 && i >= 0) {
            return attr(name.substring(i + 1), element);
        }

        return res;
    }

    /**
     * Queries for a list nodes matching the xpath expression.
     *
     * @param root
     * @param xpathExpr
     * @return
     */
    private NodeList nodes(Node root, String xpathExpr) {
        try {
            return (NodeList) xpath().evaluate(xpathExpr, root, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns a single string matching the xpath expression
     * @param root
     * @param xpathExpr
     * @return
     */
    private String text(Node root, String xpathExpr) {
        try {
            return (String) xpath().evaluate(xpathExpr, root, XPathConstants.STRING);
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        }
    }

    public Document retrieveDescriptor(URI uri) throws IOException, SAXException {
        DocumentBuilder documentBuilder = newDocumentBuilder();

        CloseableHttpClient client = OvfRetriever.newInsecureClient();
        try {
            return documentBuilder.parse(new OvfRetriever(client).retrieveAsStream(uri));
        } finally {
            IOUtils.closeQuietly(client);
        }
    }

    /**
     * Produces a string usable as key in customProperties.
     * The pattern is: ovf.prop:$classId.$keyId.$instanceId.
     * If a component is undefined it is not included in the key and also
     * The pattern used by the ovftool is used.
     * See https://www.mylesgray.com/virtualisation/deploying-ovaovf-remote-vcenter-using-ovftool/
     * @param classId
     * @param key
     * @param instanceId
     * @return
     */
    private String property(String classId, String key, String instanceId) {
        return PREFIX_OVF_PROP + makePropertyKey(classId, key, instanceId);
    }

    private String makePropertyKey(String classId, String key, String instanceId) {
        StringBuilder sb = new StringBuilder();
        if (classId != null && classId.length() > 0) {
            sb.append(classId);
            sb.append(".");
        }

        if (key != null && key.length() > 0) {
            sb.append(key);
        }

        if (instanceId != null && instanceId.length() > 0) {
            sb.append(".");
            sb.append(instanceId);
        }

        return sb.toString();
    }

    private static XPath newXpath() {
        XPathFactory xf = XPathFactory.newInstance();
        XPath xpath = xf.newXPath();

        NamespaceContextImpl ctx = new NamespaceContextImpl();

        ctx.addNamespace("ovf", "http://schemas.dmtf.org/ovf/envelope/1");
        ctx.addNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance");
        ctx.addNamespace("rasd",
                "http://schemas.dmtf.org/wbem/wscim/1/cim-schema/2/CIM_ResourceAllocationSettingData");
        ctx.addNamespace("vssd",
                "http://schemas.dmtf.org/wbem/wscim/1/cim-schema/2/CIM_VirtualSystemSettingData");
        ctx.addNamespace("vmw", "http://www.vmware.com/schema/ovf");

        xpath.setNamespaceContext(ctx);
        return xpath;
    }

    private DocumentBuilder newDocumentBuilder() {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setValidating(false);
        try {
            return dbf.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Make a NodeList behave like Iterable<Element>.
     * @param n
     * @return
     */
    private Iterable<Element> iterableElements(final NodeList n) {
        return () -> new Iterator<Element>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < n.getLength();
            }

            @Override
            public Element next() {
                if (hasNext()) {
                    return (Element) n.item(index++);
                } else {
                    throw new NoSuchElementException();
                }
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    private XPath xpath() {
        if (this.xpath == null) {
            this.xpath = newXpath();
        }

        return this.xpath;
    }
}
