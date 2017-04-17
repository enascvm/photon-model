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

package com.vmware.photon.controller.model.adapters.registry;

import java.net.URI;
import java.util.ArrayList;

import org.junit.Assert;
import org.junit.Test;

import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecFactoryService;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceOperationSpec;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceType;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationUtils;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.TenantService;

public class ResourceOperationSpecServiceTest extends BaseAdaptersRegistryServiceTest {

    public static final String POWER_STATE = "powerState";

    public String endpointType = "rostEndpointType";

    @Test
    public void testRegister() {
        ResourceOperationSpec[] states = registerResourceOperation();
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
                super.host.getReferer().toASCIIString(),
                "endpointType",
                ResourceType.COMPUTE,
                "operation"
        );
        ResourceOperationSpec found = join(dr);
        Assert.assertNull(found);
    }

    @Test
    public void testGetByEndpointType() {
        getByEndpointXXX(null);
    }

    @Test
    public void testGetByEndpointLink() throws Throwable {
        EndpointState endpoint = registerEndpoint();
        getByEndpointXXX(endpoint.documentSelfLink);
    }

    private void getByEndpointXXX(String endpointLink) {
        ResourceOperationSpec[] states = registerResourceOperation();
        ResourceOperationSpec requestedState = states[0];
        ResourceOperationSpec persistedState = states[1];

        DeferredResult<ResourceOperationSpec> dr;
        if (endpointLink != null) {
            dr = ResourceOperationUtils
                    .lookUpByEndpointLink(
                            super.host,
                            super.host.getReferer().toASCIIString(),
                            endpointLink,
                            requestedState.resourceType,
                            requestedState.operation
                    );
        } else {
            dr = ResourceOperationUtils
                    .lookUpByEndpointType(
                            super.host,
                            super.host.getReferer().toASCIIString(),
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

    private ResourceOperationSpec[] registerResourceOperation() {
        ResourceOperationSpec roState = new ResourceOperationSpec();
        roState.endpointType = this.endpointType;
        roState.operation = "reconfigure";
        roState.name = "Reconfigure";
        roState.description = "Reconfigure operation description";
        roState.resourceType = ResourceType.COMPUTE;
        roState.adapterReference = UriUtils.buildUri(this.host,
                ResourceOperationSpecService.buildDefaultAdapterLink(
                        roState.endpointType, roState.resourceType, roState.operation));

        Operation registerOp = Operation.createPost(
                super.host,
                ResourceOperationSpecService.FACTORY_LINK)
                .setBody(roState).setCompletion((op, ex) -> {
                    if (ex != null) {
                        this.logger.severe(Utils.toString(ex));
                        op.fail(ex);
                    } else {
                        op.complete();
                    }
                });
        DeferredResult<Operation> deferredResult = super.host.sendWithDeferredResult(registerOp)
                .exceptionally(e -> {
                            this.logger.severe("Error: " + Utils.toString(e));
                            return null;
                        }
                );
        // TestRequestSender
        join(deferredResult);
        Operation response = deferredResult.getNow((Operation) null);
        ResourceOperationSpec persistedState = response.getBody(ResourceOperationSpec.class);
        Assert.assertNotNull(persistedState);
        markForDelete(persistedState.documentSelfLink);
        return new ResourceOperationSpec[] { roState, persistedState };
    }

    private EndpointState registerEndpoint() throws Throwable {
        EndpointService.EndpointState endpointState = createEndpointState();
        URI tenantUri = UriUtils.buildFactoryUri(this.host, TenantService.class);
        endpointState.tenantLinks = new ArrayList<>();
        endpointState.tenantLinks.add(UriUtils.buildUriPath(
                tenantUri.getPath(), "tenantA"));
        return postServiceSynchronously(
                EndpointService.FACTORY_LINK,
                endpointState, EndpointService.EndpointState.class);
    }

    private EndpointState createEndpointState() {
        EndpointState endpoint = new EndpointState();
        endpoint.endpointType = this.endpointType;
        endpoint.name = this.endpointType;
        endpoint.tenantLinks = new ArrayList<>(1);
        endpoint.tenantLinks.add("tenant1-link");
        return endpoint;
    }

}