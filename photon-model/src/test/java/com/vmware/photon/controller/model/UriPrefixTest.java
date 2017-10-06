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

package com.vmware.photon.controller.model;

import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import com.vmware.photon.controller.model.helpers.BaseModelTest;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;

public class UriPrefixTest extends BaseModelTest {

    private static final String DEFAULT_URI_PREFIX = "default";
    private static final String RESOURCES_URI_PREFIX = "foo";

    @BeforeClass
    public static void initialize() {
        System.setProperty(UriPaths.DEFAULT_BASE_URI_PREFIX_PROPERTY_NAME, DEFAULT_URI_PREFIX);
        System.setProperty(UriPaths.RESOURCES_BASE_URI_PREFIX_PROPERTY_NAME, RESOURCES_URI_PREFIX);
    }

    @Test
    public void testUriPrefix() throws Throwable {
        String factoryPath = PhotonModelServices.LINKS[0];
        assertTrue(factoryPath.startsWith(UriUtils.normalizeUriPath(RESOURCES_URI_PREFIX)));
        Operation returnOp = sendOperationSynchronously(
                Operation.createGet(this.host, factoryPath));
        assertTrue(returnOp.getStatusCode() == Operation.STATUS_CODE_OK);
        factoryPath = PhotonModelMetricServices.LINKS[0];
        assertTrue(factoryPath.startsWith(UriUtils.normalizeUriPath(DEFAULT_URI_PREFIX)));
        returnOp = sendOperationSynchronously(
                Operation.createGet(this.host, factoryPath));
        assertTrue(returnOp.getStatusCode() == Operation.STATUS_CODE_OK);
    }
}
