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
import java.util.List;

import com.google.gson.reflect.TypeToken;

import org.junit.Assert;
import org.junit.Test;

import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceOperationSpec;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class ResourceOperationServiceTest extends BaseResourceOperationTest {

    public String endpointType = "rostEndpointType";

    @Test
    public void testGetResourceOperations() throws Throwable {
        EndpointState endpoint = registerEndpoint(this.endpointType);

        ResourceOperationSpec spec1 = createResourceOperationSpec(
                endpoint.endpointType, ResourceType.COMPUTE, "testGetResourceOperations_1");
        ResourceOperationSpec spec2 = createResourceOperationSpec(
                endpoint.endpointType, ResourceType.COMPUTE, "testGetResourceOperations_2");
        spec2.targetCriteria = "false";
        ResourceOperationSpec spec3 = createResourceOperationSpec(
                endpoint.endpointType, ResourceType.COMPUTE, "testGetResourceOperations_3");
        spec3.targetCriteria = "true";
        ResourceOperationSpec spec4 = createResourceOperationSpec(
                endpoint.endpointType, ResourceType.NETWORK, "testGetResourceOperations_4");

        registerResourceOperation(spec1);
        registerResourceOperation(spec2);
        registerResourceOperation(spec3);
        registerResourceOperation(spec4);

        ComputeState computeState = new ComputeState();
        computeState.descriptionLink = "dummy-descriptionLink";
        computeState.endpointLink = endpoint.documentSelfLink;

        ComputeState createdComputeState = registerComputeState(computeState);

        String query = UriUtils.buildUriQuery(
                ResourceOperationService.QUERY_PARAM_RESOURCE,
                createdComputeState.documentSelfLink);
        URI uri = UriUtils.buildUri(super.host, ResourceOperationService.SELF_LINK, query);
        Operation operation = sendOperationSynchronously(Operation.createGet(uri)
                .setReferer(super.host.getReferer()));
        Assert.assertNotNull(operation);

        String json = Utils.toJson(operation.getBodyRaw());
        List<ResourceOperationSpec> list = Utils.fromJson(json,
                new TypeToken<List<ResourceOperationSpec>>() {
                }.getType());
        this.logger.info("list: " + list);
        Assert.assertEquals(2, list.size());
        Assert.assertNotNull(list.get(0));
        Assert.assertNotNull(list.get(1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetResourceOperations_neg() throws Throwable {
        URI uri = UriUtils.buildUri(super.host, ResourceOperationService.SELF_LINK, null);
        sendOperationSynchronously(Operation.createGet(uri).setReferer(super.host.getReferer()));
    }
}