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

import static com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationUtils.SCRIPT_CONTEXT_RESOURCE;

import java.net.URI;
import java.util.HashMap;

import org.junit.Assert;
import org.junit.Test;

import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceOperationSpec;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;

public class ResourceOperationUtilsTest {

    @Test
    public void testIsAvailable_null_neg() {
        ResourceOperationSpec spec = createResourceOperationSpec();

        ComputeState computeState = createComputeState("testIsAvailable_null_neg");

        boolean ret = ResourceOperationUtils.isAvailable(computeState, spec);
        Assert.assertTrue(ret);
    }

    @Test
    public void testIsAvailable_noComputeState_pos() {
        ResourceOperationSpec spec = createResourceOperationSpec();
        spec.targetCriteria = "true";

        ResourceOperationUtils.isAvailable(null, spec);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIsAvailable_noTargetCriteria_pos() {
        ResourceOperationUtils.isAvailable(null, null);
    }

    @Test
    public void testIsAvailable_simple_pos() {
        ResourceOperationSpec spec = createResourceOperationSpec();
        spec.targetCriteria = SCRIPT_CONTEXT_RESOURCE + ".hostName.startsWith('test')"
                + " && " + SCRIPT_CONTEXT_RESOURCE + ".cpuCount==4";

        ComputeState computeState = createComputeState("testIsAvailable_simple_pos");

        boolean ret = ResourceOperationUtils.isAvailable(computeState, spec);
        Assert.assertTrue(ret);
    }

    @Test
    public void testIsAvailable_custProps_pos() {
        ResourceOperationSpec spec = createResourceOperationSpec();
        spec.targetCriteria = SCRIPT_CONTEXT_RESOURCE + ".customProperties.p1=='v1'";

        ComputeState computeState = createComputeState("testIsAvailable_custProps_pos");

        boolean ret = ResourceOperationUtils.isAvailable(computeState, spec);
        Assert.assertTrue(ret);
    }

    @Test
    public void testIsAvailable_simple_neg() {
        ResourceOperationSpec spec = createResourceOperationSpec();
        spec.targetCriteria = SCRIPT_CONTEXT_RESOURCE + ".hostName.startsWith('noWay')";

        ComputeState computeState = createComputeState("testIsAvailable_simple_neg");

        boolean ret = ResourceOperationUtils.isAvailable(computeState, spec);
        Assert.assertFalse(ret);
    }

    @Test
    public void testIsAvailable_custProps_neg() {
        ResourceOperationSpec spec = createResourceOperationSpec();
        spec.targetCriteria = SCRIPT_CONTEXT_RESOURCE + ".customProperties.p1=='v2'";

        ComputeState computeState = createComputeState("testIsAvailable_custProps_neg");

        boolean ret = ResourceOperationUtils.isAvailable(computeState, spec);
        Assert.assertFalse(ret);
    }

    @Test
    public void testIsAvailable_changeContextFromScript() {
        ResourceOperationSpec spec = createResourceOperationSpec();
        spec.targetCriteria = SCRIPT_CONTEXT_RESOURCE + ".hostName='changed'";

        ComputeState computeState = createComputeState("testIsAvailable_changeContextFromScript");

        String originalValue = computeState.hostName;
        boolean ret = ResourceOperationUtils.isAvailable(computeState, spec);
        Assert.assertEquals(originalValue, computeState.hostName);
    }

    private ComputeState createComputeState(String hostName) {
        ComputeState computeState = new ComputeState();
        computeState.cpuCount = 4L;
        computeState.cpuMhzPerCore = 1000L;
        computeState.hostName = hostName;
        computeState.customProperties = new HashMap<>();
        computeState.customProperties.put("p1", "v1");
        computeState.customProperties.put("p2", "v2");
        return computeState;
    }

    private ResourceOperationSpec createResourceOperationSpec() {
        ResourceOperationSpec spec = new ResourceOperationSpec();
        spec.resourceType = ResourceType.COMPUTE;
        spec.operation = "operation";
        spec.name = "name";
        spec.endpointType = "endpointType";
        spec.description = "description";
        spec.adapterReference = URI.create("uri");
        return spec;
    }
}