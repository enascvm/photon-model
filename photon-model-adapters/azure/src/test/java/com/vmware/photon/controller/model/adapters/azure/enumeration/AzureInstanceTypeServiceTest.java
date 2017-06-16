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

package com.vmware.photon.controller.model.adapters.azure.enumeration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import static com.vmware.xenon.common.UriUtils.buildUri;
import static com.vmware.xenon.common.UriUtils.buildUriQuery;

import java.net.URI;
import java.util.List;

import com.microsoft.azure.management.compute.VirtualMachineSizeTypes;
import com.microsoft.azure.management.compute.implementation.VirtualMachineSizeInner;

import org.junit.Assume;
import org.junit.Test;

import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest;
import com.vmware.photon.controller.model.adapters.azure.base.AzureBaseTest;
import com.vmware.photon.controller.model.support.InstanceTypeList;
import com.vmware.photon.controller.model.support.InstanceTypeList.InstanceType;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.test.TestContext;

/**
 * Tests for {@link AzureInstanceTypeService}.
 */
public class AzureInstanceTypeServiceTest extends AzureBaseTest {

    @Test
    public void testGetInstanceTypesPositive() throws Throwable {

        Assume.assumeFalse(this.isMock);

        TestContext ctx = getHost().testCreate(1);

        URI uri = buildUri(getHost(),
                AzureInstanceTypeService.SELF_LINK,
                buildUriQuery("endpoint", this.endpointState.documentSelfLink));

        send(Operation.createGet(uri).setCompletion((op, t) -> {
            try {
                if (t != null) {
                    ctx.failIteration(t);
                    return;
                }

                assertEquals(Operation.STATUS_CODE_OK, op.getStatusCode());

                InstanceTypeList instanceTypesList = op.getBody(InstanceTypeList.class);

                assertNotNull("Tenant links should ne set.", instanceTypesList.tenantLinks);
                assertEquals("Tenant links size equal to endpoint tenant links size is "
                        + "expected.", this.endpointState.tenantLinks.size(),
                        instanceTypesList.tenantLinks.size());

                assertNotNull("Instance types should not be null.",
                        instanceTypesList.instanceTypes);

                InstanceType instanceTypeBasicA0 = instanceTypesList.instanceTypes.stream()
                        .filter(instanceType -> VirtualMachineSizeTypes.BASIC_A0.toString()
                                .equals(instanceType.name))
                        .findFirst()
                        .get();
                assertNotNull("BASIC_A0 Instance type should not be null.",
                        instanceTypeBasicA0);

                final String regionId = this.endpointState.endpointProperties
                        .get(EndpointConfigRequest.REGION_KEY);

                final List<VirtualMachineSizeInner> azureSizes = getAzureSdkClients()
                        .getComputeManager()
                        .inner()
                        .virtualMachineSizes()
                        .list(regionId);

                assertEquals(azureSizes.size(), instanceTypesList.instanceTypes.size());

                VirtualMachineSizeInner azureSizeBasicA0 = azureSizes.stream().filter(
                        azureSize -> VirtualMachineSizeTypes.BASIC_A0.toString()
                                .equals(azureSize.name()))
                        .findFirst()
                        .get();

                assertEquals("Invalid cpuCount",
                        azureSizeBasicA0.numberOfCores(),
                        instanceTypeBasicA0.cpuCount);

                assertEquals("Invalid dataDiskMaxCount",
                        azureSizeBasicA0.maxDataDiskCount(),
                        instanceTypeBasicA0.dataDiskMaxCount);

                assertEquals("Invalid dataDiskSizeInMB",
                        azureSizeBasicA0.resourceDiskSizeInMB(),
                        instanceTypeBasicA0.dataDiskSizeInMB);

                assertEquals("Invalid bootDiskSizeInMB",
                        azureSizeBasicA0.osDiskSizeInMB(),
                        instanceTypeBasicA0.bootDiskSizeInMB);

                assertEquals("Invalid memoryInMB",
                        azureSizeBasicA0.memoryInMB(),
                        instanceTypeBasicA0.memoryInMB);

                ctx.completeIteration();

            } catch (AssertionError err) {
                ctx.failIteration(err);
            }
        }));

        testWait(ctx);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetInstanceTypesNoEndpointLink() throws Throwable {

        getServiceSynchronously(AzureInstanceTypeService.SELF_LINK, ServiceDocument.class);
    }
}