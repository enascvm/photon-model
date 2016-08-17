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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.namespace.NamespaceContext;

public class NamespaceContextImpl implements NamespaceContext {
    private final Map<String, String> prefix2Uri;

    public NamespaceContextImpl() {
        this.prefix2Uri = new HashMap<>();
    }

    public void addNamespace(String prefix, String uri) {
        this.prefix2Uri.put(prefix, uri);
    }

    @Override
    public String getNamespaceURI(String prefix) {
        return this.prefix2Uri.get(prefix);
    }

    @Override
    public String getPrefix(String namespaceURI) {
        for (Entry<String, String> e : this.prefix2Uri.entrySet()) {
            if (e.getValue().equals(namespaceURI)) {
                return e.getKey();
            }
        }

        return null;
    }

    @Override
    public Iterator<?> getPrefixes(String namespaceURI) {
        throw new UnsupportedOperationException();
    }
}
