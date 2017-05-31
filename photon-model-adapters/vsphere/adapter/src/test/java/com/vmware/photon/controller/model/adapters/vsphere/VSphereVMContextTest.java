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

package com.vmware.photon.controller.model.adapters.vsphere;

import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationRequest;
import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;

public class VSphereVMContextTest extends BasicReusableHostTestCase {
    private VSphereVMContext context;

    @Before
    public void beforeTest() {
        final URI mockUri = UriUtils.buildUri(this.host, UUID.randomUUID().toString());
        ResourceOperationRequest resourceOperationRequest = new ResourceOperationRequest();
        resourceOperationRequest.resourceReference = mockUri;
        resourceOperationRequest.operation = "Reboot"; //reboot operation
        resourceOperationRequest.taskReference = mockUri;

        StatelessService mockService = new StatelessService();
        mockService.setHost(this.host);
        //create context calling the constructor, it should have errorHandler populated by default (i.e. errorHandler != null)
        this.context = new VSphereVMContext(mockService, resourceOperationRequest);
    }

    @Test
    public void failTaskOnErrorWithErrorHandler() throws Exception {
        assertTrue(this.context.fail(new IllegalStateException(String.format("Test Failure for checking " +
                "not null value of error handler in class: %s", this.context.getClass().getSimpleName()))));
    }
}