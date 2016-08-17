/*
 * Copyright (c) 2015-2016 VMware, Inc. All Rights Reserved.
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

import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEYID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEY_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.REGION_KEY;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.setAwsClientMockInfo;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService.EndpointAllocationTaskState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class TestAWSEndpointService extends BasicReusableHostTestCase {
    public String accessKey = "accessKey";
    public String secretKey = "secretKey";
    public String regionId = "us-east-1";
    public boolean isMock = true;
    public boolean isAwsClientMock = false;
    public String awsMockEndpointReference = null;

    @Before
    public void setUp() throws Exception {
        try {
            this.host.setMaintenanceIntervalMicros(TimeUnit.MILLISECONDS.toMicros(10));
            PhotonModelServices.startServices(this.host);
            PhotonModelTaskServices.startServices(this.host);
            AWSAdapters.startServices(this.host);

            this.host.setTimeoutSeconds(300);

            this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
            this.host.waitForServiceAvailable(AWSAdapters.LINKS);
        } catch (Throwable e) {
            throw new Exception(e);
        }
    }

    @After
    public void tearDown() throws InterruptedException {
        setAwsClientMockInfo(false, null);
    }

    @Test
    public void testValidateCredentials() throws Throwable {
        EndpointState endpoint = createEndpointState();

        EndpointAllocationTaskState validateEndpoint = new EndpointAllocationTaskState();
        validateEndpoint.options = this.isMock
                ? EnumSet.of(TaskOption.VALIDATE_ONLY, TaskOption.IS_MOCK)
                : EnumSet.of(TaskOption.VALIDATE_ONLY);
        validateEndpoint.endpointState = endpoint;

        EndpointAllocationTaskState outTask = TestUtils.doPost(this.host, validateEndpoint,
                EndpointAllocationTaskState.class,
                UriUtils.buildUri(this.host, EndpointAllocationTaskService.FACTORY_LINK));

        this.host.waitForFinishedTask(EndpointAllocationTaskState.class, outTask.documentSelfLink);
    }

    @Test
    public void testCreateEndpoint() throws Throwable {
        EndpointState ep = createEndpointState();

        EndpointAllocationTaskState validateEndpoint = new EndpointAllocationTaskState();
        validateEndpoint.endpointState = ep;
        validateEndpoint.options = this.isMock ? EnumSet.of(TaskOption.IS_MOCK) : null;

        EndpointAllocationTaskState outTask = TestUtils.doPost(this.host, validateEndpoint,
                EndpointAllocationTaskState.class,
                UriUtils.buildUri(this.host, EndpointAllocationTaskService.FACTORY_LINK));

        this.host.waitForFinishedTask(EndpointAllocationTaskState.class, outTask.documentSelfLink);

        EndpointAllocationTaskState taskState = getServiceSynchronously(outTask.documentSelfLink,
                EndpointAllocationTaskState.class);
        assertNotNull(taskState);
        assertNotNull(taskState.endpointState);

        ServiceDocument endpointState = taskState.endpointState;
        assertNotNull(endpointState.documentSelfLink);

        // check endpoint document was created
        EndpointState endpoint = getServiceSynchronously(endpointState.documentSelfLink,
                EndpointState.class);
        assertNotNull(endpoint);
        assertNotNull(endpoint.authCredentialsLink);
        assertNotNull(endpoint.computeLink);
        assertNotNull(endpoint.computeDescriptionLink);

        AuthCredentialsServiceState credentials = getServiceSynchronously(
                endpoint.authCredentialsLink, AuthCredentialsServiceState.class);
        assertNotNull(credentials);
        assertEquals(ep.endpointProperties.get(PRIVATE_KEYID_KEY), credentials.privateKeyId);
        assertEquals(ep.endpointProperties.get(PRIVATE_KEY_KEY), credentials.privateKey);

        ComputeDescription cd = getServiceSynchronously(endpoint.computeDescriptionLink,
                ComputeDescription.class);
        assertNotNull(cd);
        assertEquals(credentials.documentSelfLink, cd.authCredentialsLink);
        assertEquals(this.regionId, cd.regionId);
        assertEquals(ComputeDescription.ENVIRONMENT_NAME_AWS, cd.environmentName);

        ComputeState cs = getServiceSynchronously(endpoint.computeLink, ComputeState.class);
        assertNotNull(cs);
        assertNotNull(cs.adapterManagementReference);
    }

    @Test
    public void testShouldFailOnMissingData() throws Throwable {
        EndpointState ep = createEndpointState();
        ep.endpointProperties.clear();

        EndpointAllocationTaskState validateEndpoint = new EndpointAllocationTaskState();
        validateEndpoint.endpointState = ep;
        validateEndpoint.options = this.isMock ? EnumSet.of(TaskOption.IS_MOCK) : null;

        EndpointAllocationTaskState outTask = TestUtils.doPost(this.host, validateEndpoint,
                EndpointAllocationTaskState.class,
                UriUtils.buildUri(this.host, EndpointAllocationTaskService.FACTORY_LINK));

        EndpointAllocationTaskState failedTask = waitForFailedTask(
                EndpointAllocationTaskState.class, outTask.documentSelfLink);

        assertEquals(failedTask.taskInfo.stage, TaskState.TaskStage.FAILED);
    }

    private EndpointState createEndpointState() {
        EndpointState endpoint = new EndpointState();
        endpoint.endpointType = EndpointType.aws.name();
        endpoint.name = EndpointType.aws.name();
        endpoint.endpointProperties = new HashMap<>();
        endpoint.endpointProperties.put(REGION_KEY, this.regionId);
        endpoint.endpointProperties.put(PRIVATE_KEY_KEY, this.secretKey);
        endpoint.endpointProperties.put(PRIVATE_KEYID_KEY, this.accessKey);
        return endpoint;
    }

    public <T extends ServiceDocument> T getServiceSynchronously(
            String serviceLink, Class<T> type) throws Throwable {
        return this.host.getServiceState(null, type, UriUtils.buildUri(this.host, serviceLink));
    }
}
