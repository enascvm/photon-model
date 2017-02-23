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

package com.vmware.photon.controller.model.tasks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEYID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEY_KEY;

import java.util.EnumSet;

import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService.EndpointAllocationTaskState;
import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService;

public class EndpointServiceTests {
    private final VerificationHost host;
    public String regionId = "us-east-1";
    public boolean isMock = false;
    private String environmentName;

    public EndpointServiceTests(VerificationHost host, String regionId, boolean isMock, String environmentName) {
        this.host = host;
        this.regionId = regionId;
        this.isMock = isMock;
        this.environmentName = environmentName;
    }

    public void testValidateCredentials(EndpointService.EndpointState endpoint) throws Throwable {

        EndpointAllocationTaskState validateEndpoint = new EndpointAllocationTaskState();
        validateEndpoint.options = this.isMock
                ? EnumSet.of(TaskOption.VALIDATE_ONLY, TaskOption.IS_MOCK)
                : EnumSet.of(TaskOption.VALIDATE_ONLY);
        validateEndpoint.endpointState = endpoint;

        EndpointAllocationTaskState outTask = com.vmware.photon.controller.model.tasks.TestUtils
                .doPost(this.host, validateEndpoint,
                        EndpointAllocationTaskState.class,
                        UriUtils.buildUri(this.host, EndpointAllocationTaskService.FACTORY_LINK));

        this.host.waitForFinishedTask(
                EndpointAllocationTaskState.class,
                outTask.documentSelfLink);
    }

    public void testCreateEndpoint(EndpointService.EndpointState ep) throws Throwable {
        EndpointAllocationTaskState validateEndpoint = new EndpointAllocationTaskState();
        validateEndpoint.endpointState = ep;
        validateEndpoint.options = this.isMock ? EnumSet.of(TaskOption.IS_MOCK) : null;

        EndpointAllocationTaskState outTask = TestUtils
                .doPost(this.host, validateEndpoint,
                        EndpointAllocationTaskState.class,
                        UriUtils.buildUri(this.host, EndpointAllocationTaskService.FACTORY_LINK));

        this.host.waitForFinishedTask(
                EndpointAllocationTaskState.class,
                outTask.documentSelfLink);

        EndpointAllocationTaskState taskState = getServiceSynchronously(
                outTask.documentSelfLink,
                EndpointAllocationTaskState.class);
        assertNotNull(taskState);
        assertNotNull(taskState.endpointState);

        ServiceDocument endpointState = taskState.endpointState;
        assertNotNull(endpointState.documentSelfLink);

        // check endpoint document was created
        EndpointService.EndpointState endpoint = getServiceSynchronously(
                endpointState.documentSelfLink,
                EndpointService.EndpointState.class);
        assertNotNull(endpoint);
        assertNotNull(endpoint.authCredentialsLink);
        assertNotNull(endpoint.computeLink);
        assertNotNull(endpoint.computeDescriptionLink);

        AuthCredentialsService.AuthCredentialsServiceState credentials = getServiceSynchronously(
                endpoint.authCredentialsLink,
                AuthCredentialsService.AuthCredentialsServiceState.class);
        assertNotNull(credentials);
        assertEquals(ep.endpointProperties.get(PRIVATE_KEYID_KEY), credentials.privateKeyId);
        assertEquals(ep.endpointProperties.get(PRIVATE_KEY_KEY), credentials.privateKey);

        ComputeDescriptionService.ComputeDescription cd = getServiceSynchronously(
                endpoint.computeDescriptionLink,
                ComputeDescriptionService.ComputeDescription.class);
        assertNotNull(cd);
        assertEquals(credentials.documentSelfLink, cd.authCredentialsLink);
        assertEquals(this.regionId, cd.regionId);
        assertEquals(this.environmentName, cd.environmentName);

        ComputeService.ComputeState cs = getServiceSynchronously(endpoint.computeLink,
                ComputeService.ComputeState.class);
        assertNotNull(cs);
        assertNotNull(cs.adapterManagementReference);
        assertEquals(ComputeDescriptionService.ComputeDescription.ComputeType.VM_HOST, cs.type);
        assertEquals(this.environmentName, cs.environmentName);
        assertEquals(ComputeService.PowerState.ON, cs.powerState);
    }

    public void testCreateAndThenValidate(EndpointService.EndpointState ep) throws Throwable {
        EndpointAllocationTaskState createEndpoint = new EndpointAllocationTaskState();
        createEndpoint.endpointState = ep;
        createEndpoint.options = this.isMock ? EnumSet.of(TaskOption.IS_MOCK) : null;

        EndpointAllocationTaskState outTask = TestUtils
                .doPost(this.host, createEndpoint,
                        EndpointAllocationTaskState.class,
                        UriUtils.buildUri(this.host, EndpointAllocationTaskService.FACTORY_LINK));

        this.host.waitForFinishedTask(
                EndpointAllocationTaskState.class,
                outTask.documentSelfLink);

        EndpointAllocationTaskState taskState = getServiceSynchronously(
                outTask.documentSelfLink,
                EndpointAllocationTaskState.class);
        assertNotNull(taskState);
        assertNotNull(taskState.endpointState);

        ServiceDocument endpointState = taskState.endpointState;
        assertNotNull(endpointState.documentSelfLink);

        // check endpoint document was created
        EndpointService.EndpointState endpoint = getServiceSynchronously(
                endpointState.documentSelfLink,
                EndpointService.EndpointState.class);

        // now do validation
        EndpointAllocationTaskState validateEndpoint = new EndpointAllocationTaskState();
        validateEndpoint.endpointState = endpoint;
        validateEndpoint.options = this.isMock ?
                EnumSet.of(TaskOption.IS_MOCK) :
                EnumSet.noneOf(TaskOption.class);
        validateEndpoint.options.add(TaskOption.VALIDATE_ONLY);

        EndpointAllocationTaskState validateEndpointTask = TestUtils
                .doPost(this.host, validateEndpoint,
                        EndpointAllocationTaskState.class,
                        UriUtils.buildUri(this.host, EndpointAllocationTaskService.FACTORY_LINK));

        this.host.waitForFinishedTask(
                EndpointAllocationTaskState.class,
                validateEndpointTask.documentSelfLink);
    }

    public void testShouldFailOnMissingData(EndpointService.EndpointState ep) throws Throwable {
        ep.endpointProperties.clear();

        EndpointAllocationTaskState validateEndpoint = new EndpointAllocationTaskState();
        validateEndpoint.endpointState = ep;
        validateEndpoint.options = this.isMock ? EnumSet.of(TaskOption.IS_MOCK) : null;

        EndpointAllocationTaskState outTask = TestUtils
                .doPost(this.host, validateEndpoint,
                        EndpointAllocationTaskState.class,
                        UriUtils.buildUri(this.host, EndpointAllocationTaskService.FACTORY_LINK));

        EndpointAllocationTaskState failedTask = BasicReusableHostTestCase
                .waitForFailedTask(
                        EndpointAllocationTaskState.class,
                        outTask.documentSelfLink);

        assertEquals(failedTask.taskInfo.stage, TaskState.TaskStage.FAILED);
    }

    protected <T extends ServiceDocument> T getServiceSynchronously(
            String serviceLink, Class<T> type) throws Throwable {
        return this.host.getServiceState(null, type, UriUtils.buildUri(this.host, serviceLink));
    }
}
