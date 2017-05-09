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

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceOperationSpec;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.xenon.common.DeferredResult;

public class ResourceOperationSpecServiceTest extends BaseResourceOperationTest {

    public String endpointType = "rosstEndpointType";

    @Test
    public void testRegister() {
        ResourceOperationSpec[] states = registerResourceOperation(
                this.endpointType, ResourceType.COMPUTE, "testRegister");
        ResourceOperationSpec requestedState = states[0];
        ResourceOperationSpec persistedState = states[1];
        Assert.assertTrue(persistedState.documentSelfLink.endsWith
                (ResourceOperationSpecFactoryService.generateSelfLink(requestedState)));
        this.logger.info("Persisted: " + persistedState);
    }

    @Test
    public void testGetByEndpointType_neg() {

        DeferredResult<ResourceOperationSpec> dr = ResourceOperationUtils.lookUpByEndpointType(
                super.host,
                super.host.getReferer(),
                "endpointType",
                ResourceType.COMPUTE,
                "operation"
        );
        ResourceOperationSpec found = join(dr);
        Assert.assertNull(found);
    }

    @Test
    public void testGetByResourceState() throws Throwable {
        EndpointState endpoint = registerEndpoint(this.endpointType);

        registerResourceOperation(
                endpoint.endpointType, ResourceType.COMPUTE, "testGetByResourceState_1");
        registerResourceOperation(
                endpoint.endpointType, ResourceType.COMPUTE, "testGetByResourceState_2");
        registerResourceOperation(
                endpoint.endpointType, ResourceType.NETWORK, "testGetByResourceState_3");

        ComputeState computeState = new ComputeState();
        computeState.endpointLink = endpoint.documentSelfLink;

        DeferredResult<List<ResourceOperationSpec>> dr = ResourceOperationUtils
                .lookupByResourceState(
                        super.host,
                        super.host.getReferer(),
                        computeState
                );
        List<ResourceOperationSpec> found = join(dr);
        Assert.assertNotNull(found);
        Assert.assertEquals(2, found.size());
    }

    @Test
    public void testGetByEndpointType() {
        getByEndpointXXX("testGetByEndpointType", null);
    }

    @Test
    public void testGetByEndpointLink() throws Throwable {
        EndpointState endpoint = registerEndpoint(this.endpointType);
        getByEndpointXXX("testGetByEndpointLink", endpoint.documentSelfLink);
    }

    private void getByEndpointXXX(String operation, String endpointLink) {
        ResourceOperationSpec[] states = registerResourceOperation(
                this.endpointType, ResourceType.COMPUTE, operation);
        ResourceOperationSpec requestedState = states[0];
        ResourceOperationSpec persistedState = states[1];

        DeferredResult<ResourceOperationSpec> dr;
        if (endpointLink != null) {
            dr = ResourceOperationUtils
                    .lookUpByEndpointLink(
                            super.host,
                            super.host.getReferer(),
                            endpointLink,
                            requestedState.resourceType,
                            requestedState.operation
                    );
        } else {
            dr = ResourceOperationUtils
                    .lookUpByEndpointType(
                            super.host,
                            super.host.getReferer(),
                            requestedState.endpointType,
                            requestedState.resourceType,
                            requestedState.operation
                    );
        }
        ResourceOperationSpec found = join(dr);
        Assert.assertNotNull(found);
        this.logger.info("Lookup: " + found);
        Assert.assertEquals(requestedState.endpointType, found.endpointType);
        Assert.assertEquals(requestedState.resourceType, found.resourceType);
        Assert.assertEquals(requestedState.operation, found.operation);
        Assert.assertEquals(persistedState.documentSelfLink, found.documentSelfLink);
    }

}