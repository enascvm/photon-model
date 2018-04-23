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

package com.vmware.photon.controller.discovery.endpoints;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_LINK_REQUIRED;
import static com.vmware.photon.controller.discovery.endpoints.TestEndpointCreationTaskService.createMockAwsEndpoint;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.discovery.common.ErrorCode;
import com.vmware.photon.controller.discovery.common.SymphonyCommonTestUtils;
import com.vmware.photon.controller.discovery.common.services.OrganizationService;
import com.vmware.photon.controller.discovery.endpoints.EndpointCreationTaskService.EndpointCreationTaskState;
import com.vmware.photon.controller.discovery.endpoints.EndpointDeletionTaskService.EndpointDeletionTaskState;
import com.vmware.photon.controller.discovery.endpoints.EndpointDeletionTaskService.SubStage;
import com.vmware.photon.controller.discovery.onboarding.OnBoardingTestUtils;
import com.vmware.photon.controller.discovery.onboarding.OnboardingServices;
import com.vmware.photon.controller.discovery.onboarding.OnboardingUtils;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSAdapters;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.xenon.common.BasicTestCase;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;

public class TestEndpointDeletionTaskService extends BasicTestCase {
    public int numNodes = 3;

    private static final String USER_1_ID = "user1@org-1.com";
    private static final String USER_1_PASSWORD = "passwordforuser1";

    private static final String ORG_1_ID = "org-1";
    private static final String PROJECT_1_ID = OnboardingUtils.getDefaultProjectName();

    private URI peerUri;
    private String orgLink;

    @Override
    public void beforeHostStart(VerificationHost h) {
        h.setAuthorizationEnabled(true);
    }

    @Before
    public void setUp() throws Throwable {
        CommandLineArgumentParser.parseFromProperties(this);
        host.setUpPeerHosts(this.numNodes);
        host.joinNodesAndVerifyConvergence(this.numNodes, this.numNodes, true);
        host.setNodeGroupQuorum(this.numNodes);

        // start provisioning services on all the hosts
        host.setSystemAuthorizationContext();
        for (VerificationHost h : host.getInProcessHostMap().values()) {
            PhotonModelServices.startServices(h);
            PhotonModelTaskServices.startServices(h);
            AWSAdapters.startServices(h);
            PhotonModelAdaptersRegistryAdapters.startServices(h);
        }
        host.resetSystemAuthorizationContext();

        for (VerificationHost h : this.host.getInProcessHostMap().values()) {
            OnBoardingTestUtils.startCommonServices(h);
            h.setSystemAuthorizationContext();
            OnboardingServices.startServices(h, h::addPrivilegedService);
            EndpointServices.startServices(h, h::addPrivilegedService);
            h.resetAuthorizationContext();
        }

        OnBoardingTestUtils.waitForCommonServicesAvailability(host, host.getPeerHostUri());

        List<String> factories = new ArrayList<>();
        factories.add(EndpointCreationTaskService.FACTORY_LINK);
        factories.add(EndpointDeletionTaskService.FACTORY_LINK);
        factories.add(EndpointAllocationTaskService.FACTORY_LINK);
        SymphonyCommonTestUtils
                .waitForFactoryAvailability(this.host, this.host.getPeerHostUri(), factories);

        this.peerUri = this.host.getPeerHostUri();

        OnBoardingTestUtils.setupUser(this.host, this.peerUri,
                OnBoardingTestUtils.createUserData(USER_1_ID, USER_1_PASSWORD));
        OnBoardingTestUtils
                .setupOrganization(this.host, this.peerUri, ORG_1_ID, USER_1_ID, USER_1_PASSWORD);
        OnBoardingTestUtils.setupProject(this.host, this.peerUri, PROJECT_1_ID, ORG_1_ID, USER_1_ID,
                USER_1_PASSWORD);

        this.orgLink = UriUtils.buildUriPath(OrganizationService.FACTORY_LINK,
                Utils.computeHash(ORG_1_ID));
    }

    @After
    public void tearDown() throws Throwable {
        if (this.host == null) {
            return;
        }
        this.host.tearDownInProcessPeers();
        this.host.toggleNegativeTestMode(false);
        this.host.tearDown();
    }

    @Test
    public void testInvalidArgs() {
        EndpointDeletionTaskState task = new EndpointDeletionTaskState();
        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointDeletionTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, ENDPOINT_LINK_REQUIRED);
    }

    @Test
    public void testInvalidEndpointLink() {
        EndpointDeletionTaskState task = new EndpointDeletionTaskState();
        task.endpointLink = "foobar";
        task.taskInfo = TaskState.createDirect();

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointDeletionTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        EndpointDeletionTaskState body = response.getBody(EndpointDeletionTaskState.class);
        assertEquals(SubStage.ERROR, body.subStage);
        assertEquals(TaskStage.FAILED, body.taskInfo.stage);
        // TODO: Photon model is not setting the right status code.
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, body.taskInfo.failure.statusCode);
    }

    @Test
    public void testEndpointDeletion() throws Throwable {
        EndpointCreationTaskState createResponse = createMockAwsEndpoint(this.host, this.peerUri, this.orgLink,
                        "my-endpoint", "foo", "bar",
                null, null, null);
        String endpointLink = createResponse.endpointLink;

        EndpointDeletionTaskState task = new EndpointDeletionTaskState();
        task.endpointLink = endpointLink;
        task.taskInfo = TaskState.createDirect();

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointDeletionTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        EndpointDeletionTaskState body = response.getBody(EndpointDeletionTaskState.class);
        assertEquals(SubStage.SUCCESS, body.subStage);
        assertEquals(TaskStage.FINISHED, body.taskInfo.stage);
    }

    private void verifyError(Operation op, ErrorCode errorCode) {
        ServiceErrorResponse error = op.getBody(ServiceErrorResponse.class);
        assertNotNull(error);
        assertEquals(String.valueOf(errorCode.getErrorCode()), error.messageId);
    }

}
