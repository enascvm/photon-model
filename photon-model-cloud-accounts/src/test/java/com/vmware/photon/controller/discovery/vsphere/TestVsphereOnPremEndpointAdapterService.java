/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.discovery.vsphere;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEYID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEY_KEY;
import static com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType.ENDPOINT_HOST;

import java.util.EnumSet;
import java.util.HashMap;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService.EndpointAllocationTaskState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService;

public class TestVsphereOnPremEndpointAdapterService extends BasicReusableHostTestCase {

    public static final String PRIVATE_CLOUD_NAME = "privateCloudName";
    public boolean isMock = true;
    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @Before
    public void setUp() throws Throwable {
        try {
            if (this.host.checkServiceAvailable(VsphereOnPremEndpointAdapterService.SELF_LINK)) {
                return;
            }
            PhotonModelServices.startServices(this.host);
            PhotonModelTaskServices.startServices(this.host);
            this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);

            host.startService(
                    Operation.createPost(UriUtils.buildUri(host,
                            VsphereOnPremEndpointAdapterService.class)),
                    new VsphereOnPremEndpointAdapterService());
            this.host.waitForServiceAvailable(VsphereOnPremEndpointAdapterService.SELF_LINK);
        } catch (Throwable e) {
            throw e;
        }
    }

    @Test
    public void testEndpointAddition() throws Throwable {

        EndpointState ep = createEndpointState();
        EndpointAllocationTaskState createEndpoint = new EndpointAllocationTaskState();
        createEndpoint.endpointState = ep;
        createEndpoint.options = this.isMock ? EnumSet.of(TaskOption.IS_MOCK) : null;
        createEndpoint.adapterReference = UriUtils.buildUri(this.host,
                VsphereOnPremEndpointAdapterService.SELF_LINK);

        EndpointAllocationTaskState outTask = TestUtils.doPost(this.host, createEndpoint,
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

        // check credentials filled are corect.
        AuthCredentialsService.AuthCredentialsServiceState credentials = getServiceSynchronously(
                endpoint.authCredentialsLink, AuthCredentialsService.AuthCredentialsServiceState.class);
        assertNotNull(credentials);
        assertEquals(ep.endpointProperties.get(PRIVATE_KEYID_KEY), credentials.privateKeyId);
        assertEquals(ep.endpointProperties.get(PRIVATE_KEY_KEY), credentials.privateKey);

        //Check if compute and compute description is correct.
        ComputeDescriptionService.ComputeDescription cd = getServiceSynchronously(endpoint.computeDescriptionLink,
                ComputeDescriptionService.ComputeDescription.class);
        assertNotNull(cd);
        assertEquals(credentials.documentSelfLink, cd.authCredentialsLink);

        ComputeService.ComputeState cs = getServiceSynchronously(endpoint.computeLink, ComputeService.ComputeState.class);
        assertNotNull(cs);
        assertEquals(ENDPOINT_HOST, cs.type);
        assertNull(cs.customProperties.get("privateKey"));
        assertNull(cs.customProperties.get("privateKeyId"));

        EndpointState es = getServiceSynchronously(endpoint.documentSelfLink, EndpointState.class);
        assertNotNull(es);
        assertNull(es.endpointProperties.get("privateKey"));
        assertNull(es.endpointProperties.get("privateKeyId"));
        assertEquals(es.endpointProperties.get("hostName"), "10.112.107.120");
        assertEquals(es.endpointProperties.get(PRIVATE_CLOUD_NAME), "privateCloud1");
        assertEquals("vc-dc-id", es.endpointProperties.get(VsphereOnPremEndpointAdapterService.DC_ID_KEY));
        assertEquals("vc-dc-name", es.endpointProperties.get(VsphereOnPremEndpointAdapterService.DC_NAME_KEY));
    }

    private EndpointState createEndpointState() {
        EndpointState endpoint = new EndpointState();
        endpoint.endpointType = "vsphere-on-prem";
        endpoint.name = "vc-local-test-endpoint";
        endpoint.endpointProperties = new HashMap<>();
        endpoint.endpointProperties.put(PRIVATE_KEY_KEY, "password");
        endpoint.endpointProperties.put(PRIVATE_KEYID_KEY, "root");
        endpoint.endpointProperties.put("hostName", "10.112.107.120");
        endpoint.endpointProperties.put(PRIVATE_CLOUD_NAME, "privateCloud1");
        endpoint.endpointProperties.put(VsphereOnPremEndpointAdapterService.DC_ID_KEY, "vc-dc-id");
        endpoint.endpointProperties.put(VsphereOnPremEndpointAdapterService.DC_NAME_KEY, "vc-dc-name");
        return endpoint;
    }

    public <T extends ServiceDocument> T getServiceSynchronously(
            String serviceLink, Class<T> type) throws Throwable {
        return this.host.getServiceState(null, type, UriUtils.buildUri(this.host, serviceLink));
    }

    @Test
    public void testShouldFailOnMissingPrivateCloudName() throws Throwable {
        EndpointState ep = createEndpointState();
        ep.endpointProperties.remove(PRIVATE_CLOUD_NAME);

        EndpointAllocationTaskState validateEndpoint = new EndpointAllocationTaskState();
        validateEndpoint.endpointState = ep;
        validateEndpoint.adapterReference = UriUtils.buildUri(this.host,
                VsphereOnPremEndpointAdapterService.SELF_LINK);
        validateEndpoint.options = this.isMock ? EnumSet.of(TaskOption.IS_MOCK) : null;

        EndpointAllocationTaskState outTask = TestUtils.doPost(this.host, validateEndpoint,
                EndpointAllocationTaskState.class,
                UriUtils.buildUri(this.host, EndpointAllocationTaskService.FACTORY_LINK));

        EndpointAllocationTaskState failedTask = waitForFailedTask(
                EndpointAllocationTaskState.class, outTask.documentSelfLink);

        assertEquals(failedTask.taskInfo.stage, TaskState.TaskStage.FAILED);
    }

    @Test
    public void testShouldFailOnMissingPrivateKeyId() throws Throwable {
        EndpointState ep = createEndpointState();
        ep.endpointProperties.remove(PRIVATE_KEY_KEY);

        EndpointAllocationTaskState validateEndpoint = new EndpointAllocationTaskState();
        validateEndpoint.endpointState = ep;
        validateEndpoint.adapterReference = UriUtils.buildUri(this.host,
                VsphereOnPremEndpointAdapterService.SELF_LINK);
        validateEndpoint.options = this.isMock ? EnumSet.of(TaskOption.IS_MOCK) : null;

        EndpointAllocationTaskState outTask = TestUtils.doPost(this.host, validateEndpoint,
                EndpointAllocationTaskState.class,
                UriUtils.buildUri(this.host, EndpointAllocationTaskService.FACTORY_LINK));

        EndpointAllocationTaskState failedTask = waitForFailedTask(
                EndpointAllocationTaskState.class, outTask.documentSelfLink);

        assertEquals(failedTask.taskInfo.stage, TaskState.TaskStage.FAILED);
    }

    @Test
    public void testShouldFailOnMissingHostName() throws Throwable {
        EndpointState ep = createEndpointState();
        ep.endpointProperties.remove("hostName");

        EndpointAllocationTaskState validateEndpoint = new EndpointAllocationTaskState();
        validateEndpoint.endpointState = ep;
        validateEndpoint.adapterReference = UriUtils.buildUri(this.host,
                VsphereOnPremEndpointAdapterService.SELF_LINK);
        validateEndpoint.options = this.isMock ? EnumSet.of(TaskOption.IS_MOCK) : null;

        EndpointAllocationTaskState outTask = TestUtils.doPost(this.host, validateEndpoint,
                EndpointAllocationTaskState.class,
                UriUtils.buildUri(this.host, EndpointAllocationTaskService.FACTORY_LINK));

        EndpointAllocationTaskState failedTask = waitForFailedTask(
                EndpointAllocationTaskState.class, outTask.documentSelfLink);

        assertEquals(failedTask.taskInfo.stage, TaskState.TaskStage.FAILED);
    }

    @Test
    public void testShouldFailOnMissingPrivateKey() throws Throwable {
        EndpointState ep = createEndpointState();
        ep.endpointProperties.remove(PRIVATE_KEYID_KEY);

        EndpointAllocationTaskState validateEndpoint = new EndpointAllocationTaskState();
        validateEndpoint.endpointState = ep;
        validateEndpoint.adapterReference = UriUtils.buildUri(this.host,
                VsphereOnPremEndpointAdapterService.SELF_LINK);
        validateEndpoint.options = this.isMock ? EnumSet.of(TaskOption.IS_MOCK) : null;

        EndpointAllocationTaskState outTask = TestUtils.doPost(this.host, validateEndpoint,
                EndpointAllocationTaskState.class,
                UriUtils.buildUri(this.host, EndpointAllocationTaskService.FACTORY_LINK));

        EndpointAllocationTaskState failedTask = waitForFailedTask(
                EndpointAllocationTaskState.class, outTask.documentSelfLink);

        assertEquals(failedTask.taskInfo.stage, TaskState.TaskStage.FAILED);
    }
}
