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

package com.vmware.photon.controller.model.adapters.vsphere;

import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEYID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEY_KEY;
import static com.vmware.photon.controller.model.adapters.vsphere.VSphereEndpointAdapterService.HOST_NAME_KEY;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.HashMap;
import java.util.logging.Level;

import org.junit.Ignore;
import org.junit.Test;

import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.tasks.EndpointServiceTests;

public class TestVSphereEndpointService extends BaseVSphereAdapterTest {

    @Override public void setUp() throws Throwable {
        super.setUp();

        this.host.log(Level.INFO, "Executing test with isMock = %s", isMock());
    }

    @Test
    public void testValidateCredentials() throws Throwable {
        new EndpointServiceTests(this.host, this.datacenterId, isMock(),
                ComputeDescriptionService.ComputeDescription.ENVIRONMENT_NAME_ON_PREMISE)
                .testValidateCredentials(createEndpointState());
    }

    @Test
    @Ignore("Test expects ComputeDescription to be of type VM_HOST, but is null")
    public void testCreateEndpoint() throws Throwable {
        new EndpointServiceTests(this.host, this.datacenterId, isMock(),
                ComputeDescriptionService.ComputeDescription.ENVIRONMENT_NAME_ON_PREMISE)
                .testCreateEndpoint(createEndpointState());
    }

    @Test
    public void testCreateAndThenValidate() throws Throwable {
        new EndpointServiceTests(this.host, this.datacenterId, isMock(),
                ComputeDescriptionService.ComputeDescription.ENVIRONMENT_NAME_ON_PREMISE)
                .testCreateAndThenValidate(createEndpointState());
    }

    @Test
    @Ignore("NPE")
    public void testShouldFailOnMissingData() throws Throwable {
        new EndpointServiceTests(this.host, this.datacenterId, isMock(),
                ComputeDescriptionService.ComputeDescription.ENVIRONMENT_NAME_ON_PREMISE)
                .testShouldFailOnMissingData(createEndpointState());
    }

    public EndpointService.EndpointState createEndpointState() throws MalformedURLException {
        EndpointService.EndpointState endpoint = new EndpointService.EndpointState();
        endpoint.endpointType = PhotonModelConstants.EndpointType.vsphere.name();
        endpoint.name = PhotonModelConstants.EndpointType.vsphere.name();
        endpoint.regionId = this.datacenterId;

        endpoint.endpointProperties = new HashMap<>();
        endpoint.endpointProperties.put(PRIVATE_KEYID_KEY,
                vcUsername != null ? vcUsername : "username");
        endpoint.endpointProperties.put(PRIVATE_KEY_KEY,
                vcPassword != null ? vcPassword : "password");
        endpoint.endpointProperties.put(HOST_NAME_KEY,
                vcUrl != null ? URI.create(vcUrl).toURL().getHost() : "hostname");
        return endpoint;
    }
}
