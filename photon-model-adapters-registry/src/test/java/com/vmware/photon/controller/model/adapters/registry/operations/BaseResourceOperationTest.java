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

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;

import org.junit.Assert;

import com.vmware.photon.controller.model.adapters.registry.BaseAdaptersRegistryServiceTest;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceOperationSpec;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.TenantService;

public abstract class BaseResourceOperationTest extends BaseAdaptersRegistryServiceTest {

    public static ComputeState createComputeState(String hostName) {
        ComputeState computeState = new ComputeState();
        computeState.cpuCount = 4L;
        computeState.cpuMhzPerCore = 1000L;
        computeState.hostName = hostName;
        computeState.customProperties = new HashMap<>();
        computeState.customProperties.put("p1", "v1");
        computeState.customProperties.put("p2", "v2");
        return computeState;
    }

    public static ResourceOperationSpec createResourceOperationSpec() {
        ResourceOperationSpec spec = new ResourceOperationSpec();
        spec.resourceType = ResourceType.COMPUTE;
        spec.operation = "operation";
        spec.name = "name";
        spec.endpointType = "endpointType";
        spec.description = "description";
        spec.adapterReference = URI.create("uri");
        return spec;
    }

    protected ResourceOperationSpec createResourceOperationSpec(
            String endpointType, ResourceType resourceType, String operation) {
        ResourceOperationSpec roSpec = new ResourceOperationSpec();
        roSpec.endpointType = endpointType;
        roSpec.operation = operation;
        roSpec.name = operation;
        roSpec.description = operation;
        roSpec.resourceType = resourceType;
        roSpec.adapterReference = UriUtils.buildUri(this.host,
                ResourceOperationSpecService.buildDefaultAdapterLink(
                        roSpec.endpointType, roSpec.resourceType, roSpec.operation));

        return roSpec;
    }

    protected ResourceOperationSpec[] registerResourceOperation(
            String endpointType, ResourceType resourceType, String operation) {
        ResourceOperationSpec roSpec = createResourceOperationSpec(
                endpointType, resourceType, operation);

        return registerResourceOperation(roSpec);
    }

    protected ResourceOperationSpec[] registerResourceOperation(ResourceOperationSpec roState) {
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

    protected EndpointState registerEndpoint(String endpointType) throws Throwable {
        EndpointService.EndpointState endpointState = createEndpointState(endpointType);
        URI tenantUri = UriUtils.buildFactoryUri(this.host, TenantService.class);
        endpointState.tenantLinks = new ArrayList<>();
        endpointState.tenantLinks.add(UriUtils.buildUriPath(
                tenantUri.getPath(), "tenantA"));
        return postServiceSynchronously(
                EndpointService.FACTORY_LINK,
                endpointState, EndpointService.EndpointState.class);
    }

    private static EndpointState createEndpointState(String endpointType) {
        EndpointState endpoint = new EndpointState();
        endpoint.endpointType = endpointType;
        endpoint.name = endpointType;
        endpoint.tenantLinks = new ArrayList<>(1);
        endpoint.tenantLinks.add("tenant1-link");
        return endpoint;
    }

    protected ComputeState registerComputeState(ComputeState computeState) throws Throwable {
        Operation operation = sendOperationSynchronously(Operation
                .createPost(super.host, ComputeService.FACTORY_LINK)
                .setBody(computeState));
        if (operation.getStatusCode() == Operation.STATUS_CODE_OK) {
            ComputeState result = operation.getBody(ComputeState.class);
            markForDelete(result.documentSelfLink);
            return result;
        }
        throw new IllegalStateException(String.valueOf(operation.getBodyRaw()));
    }
}
