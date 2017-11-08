/*
 * Copyright (c) 2015-2017 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.model.adapters.azure.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Tests for {@link AzureUtils}.
 */
public class TestAzureUtils {

    @Test
    public void testCanonizeId() throws Throwable {
        String httpUri = "http://azlrtemvuasa.blob.core.windows.net/vhds/az-lrt-emvua-boot-disk";
        String httpsUri = "https://azlrtemvuasa.blob.core.windows.net/vhds/az-lrt-emvua-boot-disk";
        String simpleId = "noprotocolId";
        String expected = "azlrtemvuasa.blob.core.windows.net/vhds/az-lrt-emvua-boot-disk";

        assertEquals(expected, AzureUtils.canonizeId(httpUri));
        assertEquals(expected, AzureUtils.canonizeId(httpsUri));
        assertEquals(simpleId, AzureUtils.canonizeId(simpleId));
    }
}