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

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Test;

import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceOperationSpec;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestRequestSender;

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
    public void testRegisterAndPatchExtensions() {
        ResourceOperationSpec[] states = registerResourceOperation(
                this.endpointType, ResourceType.COMPUTE, "testRegisterAndPatchExtensions");
        ResourceOperationSpec requestedState = states[0];
        ResourceOperationSpec persistedState = states[1];
        Assert.assertTrue(persistedState.documentSelfLink.endsWith
                (ResourceOperationSpecFactoryService.generateSelfLink(requestedState)));
        this.logger.info("Persisted: " + persistedState);

        String E_2 = "e2";

        ResourceOperationSpec specWithExtension = new ResourceOperationSpec();
        specWithExtension.documentSelfLink = ResourceOperationSpecFactoryService.generateSelfLink
                (requestedState.endpointType, requestedState.resourceType,
                        requestedState.operation);
        specWithExtension.extensions = new HashMap<>();
        specWithExtension.extensions.put(E_2, "v2");

        Operation patchOne = Operation.createPatch(
                super.host,
                specWithExtension.documentSelfLink)
                .setBodyNoCloning(specWithExtension);
        TestRequestSender sender = super.host.getTestRequestSender();
        sender.sendAndWait(patchOne);
        ResourceOperationSpec patchedSpec = sender.sendGetAndWait(
                UriUtils.buildUri(super.host, specWithExtension.documentSelfLink),
                ResourceOperationSpec.class);

        Assert.assertNotNull(patchedSpec);
        Assert.assertNotNull(patchedSpec.extensions);
        Assert.assertNotNull(patchedSpec.extensions.get(E_2));

        specWithExtension.name = "tryNewName";
        Operation patchTwo = Operation.createPatch(
                super.host,
                specWithExtension.documentSelfLink)
                .setBodyNoCloning(specWithExtension);
        try {
            sender.sendAndWait(patchTwo);
        } catch (RuntimeException rte) {
            Throwable[] allSuppressed = rte.getSuppressed();
            Assert.assertNotNull(allSuppressed);
            Assert.assertEquals(1, allSuppressed.length);
            Throwable suppressed = allSuppressed[0];
            this.logger.info("Suppressed error: " + suppressed.getMessage());
            Assert.assertTrue(suppressed instanceof IllegalArgumentException);
        }
    }

    @Test
    public void testGetByEndpointType_neg() {

        DeferredResult<ResourceOperationSpec> dr = ResourceOperationUtils.lookUpByEndpointType(
                super.host,
                super.host.getReferer(),
                UUID.randomUUID().toString(),
                ResourceType.COMPUTE,
                UUID.randomUUID().toString(),
                null,
                null
        );
        ResourceOperationSpec found = join(dr);
        Assert.assertNull(found);
    }

    @Test
    public void testGetByResourceState() throws Throwable {

        ComputeState computeState = prepare(this.endpointType,
                "testGetByResourceState_1", "testGetByResourceState_2");
        registerResourceOperation(
                this.endpointType, ResourceType.NETWORK, "testGetByResourceState_3");

        DeferredResult<List<ResourceOperationSpec>> dr = ResourceOperationUtils
                .lookupByResourceState(
                        super.host,
                        super.host.getReferer(),
                        computeState,
                        null,
                        null
                );
        List<ResourceOperationSpec> found = join(dr);
        Assert.assertNotNull(found);
        Assert.assertEquals(2, found.size());
    }

    @Test
    public void testGetByResourceStateAndOperation() throws Throwable {
        String operation = UUID.randomUUID().toString();
        ComputeState computeState = prepare(this.endpointType,
                "testGetByResourceStateAndOperation_1",
                "testGetByResourceStateAndOperation_2",
                operation);
        DeferredResult<List<ResourceOperationSpec>> dr = ResourceOperationUtils
                .lookupByResourceState(
                        super.host,
                        super.host.getReferer(),
                        computeState,
                        operation,
                        null
                );
        List<ResourceOperationSpec> found = join(dr);
        Assert.assertNotNull(found);
        Assert.assertEquals(1, found.size());
        ResourceOperationSpec resourceOperationSpec = found.get(0);
        Assert.assertEquals(operation, resourceOperationSpec.operation);
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
                            requestedState.operation,
                            null,
                            null
                    );
        } else {
            dr = ResourceOperationUtils
                    .lookUpByEndpointType(
                            super.host,
                            super.host.getReferer(),
                            requestedState.endpointType,
                            requestedState.resourceType,
                            requestedState.operation,
                            null,
                            null
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

    private ComputeState prepare(String endpointType, String... ops) throws Throwable {
        EndpointState endpoint = registerEndpoint(endpointType);

        for (String op : ops) {
            registerResourceOperation(endpointType, ResourceType.COMPUTE, op);
        }

        ComputeState computeState = new ComputeState();
        computeState.endpointLink = endpoint.documentSelfLink;
        return computeState;
    }

}