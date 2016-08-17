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

import org.junit.Test;

public class NamespaceContextImplTest {

    @Test
    public void test() {
        NamespaceContextImpl ctx = new NamespaceContextImpl();

        final String ns = "myns";
        String uri = "http://schemas.dmtf.org/ovf/envelope/1";
        ctx.addNamespace(ns, uri);

        assertEquals(uri, ctx.getNamespaceURI(ns));
        assertEquals(ns, ctx.getPrefix(uri));
    }
}
