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

package com.vmware.photon.controller.model.adapters.awsadapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEYID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEY_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.REGION_KEY;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.concurrent.CompletionException;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.model.InstanceType;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.support.InstanceTypeList;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService.EndpointAllocationTaskState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;

/**
 * Tests for {@link AWSInstanceTypeService}.
 */
public class AWSInstanceTypeServiceTest extends BaseModelTest {

    // Populated from command line props {{
    public String accessKey = "accessKey";
    public String secretKey = "secretKey";
    public boolean isMock = true;
    // }}

    @Before
    public final void beforeTest() throws Throwable {
        CommandLineArgumentParser.parseFromProperties(this);
    }

    @Override
    protected void startRequiredServices() throws Throwable {

        // Start PhotonModelServices
        super.startRequiredServices();

        PhotonModelTaskServices.startServices(getHost());
        getHost().waitForServiceAvailable(PhotonModelTaskServices.LINKS);

        PhotonModelAdaptersRegistryAdapters.startServices(getHost());
        getHost().waitForServiceAvailable(PhotonModelAdaptersRegistryAdapters.LINKS);

        AWSAdapters.startServices(getHost());

        getHost().waitForServiceAvailable(AWSAdapters.CONFIG_LINK);
    }

    @Test
    public void testGetInstanceTypesPositive() throws Throwable {
        EndpointState ep = createEndpointState();

        TestContext ctx = this.host.testCreate(1);

        Operation getOperation = Operation
                .createGet(UriUtils.buildUri(this.host, AWSInstanceTypeService.SELF_LINK +
                        "?endpoint=" + ep.documentSelfLink))
                .setCompletion((operation, throwable) -> {
                    try {
                        if (throwable != null) {
                            ctx.failIteration(throwable);
                            return;
                        }

                        assertEquals(Operation.STATUS_CODE_OK, operation.getStatusCode());
                        InstanceTypeList instanceTypes = operation.getBody(InstanceTypeList.class);

                        assertNotNull("Tenant links should ne set.", instanceTypes.tenantLinks);
                        assertEquals("Tenant links size equal to endpoint tenant links size is "
                                        + "expecteed.", ep.tenantLinks.size(),
                                instanceTypes.tenantLinks.size());

                        assertNotNull("Instance types should not be null.",
                                instanceTypes.instanceTypes);

                        instanceTypes.instanceTypes.stream()
                                .filter(instanceType -> instanceType.id == null
                                        || instanceType.name == null)
                                .findFirst()
                                .ifPresent(instanceType -> fail("Found instance type without id "
                                        + "or name."));

                        // Validate that all types have cpu and memory extended data set.
                        instanceTypes.instanceTypes.stream()
                                .filter(instanceType -> instanceType.cpuCount == null ||
                                        instanceType.memoryInMB == null)
                                .findFirst()
                                .ifPresent(instanceType ->
                                        fail("Found instance type without extended data present: " +
                                                instanceType.id));

                        // Check that one of the well known type is present.
                        InstanceTypeList.InstanceType t2MicroType = instanceTypes.instanceTypes
                                .stream()
                                .filter(instanceType -> InstanceType.T2Micro.toString().equals
                                        (instanceType.id))
                                .findFirst()
                                .orElseThrow(() -> new AssertionError("Unable to "
                                        + "find t2.micro instance type."));

                        assertEquals(1, t2MicroType.cpuCount.intValue());
                        assertEquals(1024, t2MicroType.memoryInMB.intValue());
                        assertNotNull(t2MicroType.storageType);
                        assertNotNull(t2MicroType.networkType);

                        ctx.completeIteration();
                    } catch (AssertionError err) {
                        ctx.failIteration(err);
                    }
                });

        this.send(getOperation);
        this.testWait(ctx);
    }

    @Test(expected = CompletionException.class)
    public void testGetInstanceTypesNoEndpointLink() throws Throwable {
        try {
            this.getServiceSynchronously(AWSInstanceTypeService.SELF_LINK, ServiceDocument.class);
            fail("Get without endpoint link should fail.");
        } catch (CompletionException ex) {
            assertTrue(ex.getCause() instanceof IllegalArgumentException);
            throw ex;
        }
    }

    private EndpointState createEndpointState() throws Throwable {

        EndpointType endpointType = EndpointType.aws;

        EndpointState endpoint;
        {
            endpoint = new EndpointState();

            endpoint.endpointType = endpointType.name();
            endpoint.id = endpointType.name() + "-id";
            endpoint.name = endpointType.name() + "-name";

            endpoint.endpointProperties = new HashMap<>();
            endpoint.endpointProperties.put(PRIVATE_KEY_KEY, this.secretKey);
            endpoint.endpointProperties.put(PRIVATE_KEYID_KEY, this.accessKey);
            endpoint.endpointProperties.put(REGION_KEY, Regions.US_EAST_1.getName());
        }

        EndpointAllocationTaskState allocateEndpoint = new EndpointAllocationTaskState();
        allocateEndpoint.endpointState = endpoint;
        allocateEndpoint.options = this.isMock ? EnumSet.of(TaskOption.IS_MOCK) : null;
        allocateEndpoint.taskInfo = new TaskState();
        allocateEndpoint.taskInfo.isDirect = true;

        allocateEndpoint.tenantLinks = Collections.singletonList(endpointType.name() + "-tenant");

        allocateEndpoint = com.vmware.photon.controller.model.tasks.TestUtils
                .doPost(this.host, allocateEndpoint,
                        EndpointAllocationTaskState.class,
                        UriUtils.buildUri(this.host, EndpointAllocationTaskService.FACTORY_LINK));

        return allocateEndpoint.endpointState;
    }

}