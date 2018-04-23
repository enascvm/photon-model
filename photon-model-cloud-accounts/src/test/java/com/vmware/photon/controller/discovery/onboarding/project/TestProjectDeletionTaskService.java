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

package com.vmware.photon.controller.discovery.onboarding.project;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;
import java.util.logging.Level;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.discovery.common.PhotonControllerCloudAccountUtils;
import com.vmware.photon.controller.discovery.common.SymphonyCommonTestUtils;
import com.vmware.photon.controller.discovery.common.services.OrganizationService;
import com.vmware.photon.controller.discovery.common.services.ProjectService.ProjectState;
import com.vmware.photon.controller.discovery.common.services.ProjectService.ProjectStatus;
import com.vmware.photon.controller.discovery.common.services.UserService;
import com.vmware.photon.controller.discovery.onboarding.OnBoardingTestUtils;
import com.vmware.photon.controller.discovery.onboarding.OnboardingUtils;
import com.vmware.photon.controller.discovery.onboarding.organization.OrganizationCreationService;
import com.vmware.photon.controller.discovery.onboarding.organization.OrganizationCreationService.OrganizationCreationRequest;
import com.vmware.photon.controller.discovery.onboarding.project.ProjectCreationTaskService.ProjectCreationTaskState;
import com.vmware.photon.controller.discovery.onboarding.project.ProjectDeletionTaskService.ProjectDeletionTaskState;
import com.vmware.photon.controller.discovery.onboarding.project.ProjectUpdateTaskService.ProjectUpdateTaskState;
import com.vmware.photon.controller.discovery.onboarding.user.UserCreationService;
import com.vmware.photon.controller.discovery.onboarding.user.UserCreationService.UserCreationRequest;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.ScheduledTaskService.ScheduledTaskState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.ResourceGroupService;
import com.vmware.xenon.services.common.RoleService;
import com.vmware.xenon.services.common.UserGroupService;

public class TestProjectDeletionTaskService {

    private static final String ORG_1 = "org-1";
    private static final String ORG_1_NAME = "org-1 name";
    private static final String ORG_1_DISPLAY_NAME = "org-1 display name";
    private static final String PROJECT_1 = "project-1";
    private static final String USER_2_PASSWORD = "passwordforuser-2";
    private static final String USER_2_EMAIL = "user-2@org.com";
    private static final String USER_1_PASSWORD = "passwordforuser-1";
    private static final String USER_1_EMAIL = "user-1@org.com";
    private VerificationHost host;
    private final int NUMBER_NODES = 3;

    @Before
    public void setUp() throws Throwable {
        this.host = OnBoardingTestUtils.setupOnboardingServices(this.NUMBER_NODES);
        this.host.setTimeoutSeconds(600);
    }

    @After
    public void tearDown() throws InterruptedException {
        if (this.host == null) {
            return;
        }
        this.host.tearDownInProcessPeers();
        this.host.toggleNegativeTestMode(false);
        this.host.tearDown();
    }

    @Test
    public void testProjectDelete() throws Throwable {

        URI peerUri = this.host.getPeerHostUri();

        // create 2 users in the system
        UserCreationRequest userData = OnBoardingTestUtils
                .createUserData(USER_1_EMAIL, USER_1_PASSWORD);
        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, UserCreationService.SELF_LINK))
                .setBody(userData));

        UserCreationRequest userData2 = OnBoardingTestUtils
                .createUserData(USER_2_EMAIL, USER_2_PASSWORD);
        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, UserCreationService.SELF_LINK))
                .setBody(userData2));

        // create an organization
        OrganizationCreationRequest orgData = new OrganizationCreationRequest();

        orgData.orgId = ORG_1;
        orgData.organizationName = ORG_1;
        orgData.displayName = ORG_1_DISPLAY_NAME;

        this.host.assumeIdentity(
                UriUtils.buildUriPath(UserService.FACTORY_LINK,
                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(userData.email)), null);
        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, OrganizationCreationService.SELF_LINK))
                .setBody(orgData));

        ProjectCreationTaskState projectData = new ProjectCreationTaskState();
        projectData.projectName = PROJECT_1;
        projectData.budget = new BigDecimal(100);
        String orgNameHash = Utils.computeHash(orgData.orgId);
        projectData.organizationLink = UriUtils.buildUriPath(OrganizationService.FACTORY_LINK,
                orgNameHash);

        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, ProjectCreationTaskService.FACTORY_LINK))
                .setBody(projectData));
        // verify in system context as all these resources are owned by the system
        this.host.setSystemAuthorizationContext();
        ServiceDocumentQueryResult res = SymphonyCommonTestUtils.queryDocuments(this.host, peerUri,
                Utils.buildKind(ProjectState.class), 1);

        ProjectState project =
                Utils.fromJson(res.documents.values().iterator().next(),
                        ProjectState.class);
        assertEquals(project.name, projectData.projectName);

        res = SymphonyCommonTestUtils.queryDocuments(this.host, peerUri,
                Utils.buildKind(ScheduledTaskState.class), 3);
        // verify that the resource pool created for the project has one tenantLink
        this.host.setSystemAuthorizationContext();
        res = SymphonyCommonTestUtils.queryDocuments(this.host, peerUri,
                Utils.buildKind(ResourcePoolState.class), 1);
        ResourcePoolState rpState =
                Utils.fromJson(res.documents.values().iterator().next(), ResourcePoolState.class);
        assertEquals(rpState.tenantLinks.size(), 1);

        res = SymphonyCommonTestUtils.queryDocuments(this.host, peerUri,
                Utils.buildKind(ResourceGroupService.ResourceGroupState.class), 5);
        OnboardingUtils.buildAuthzArtifactLink(UriUtils.buildUriPath(ResourceGroupService.FACTORY_LINK,
                        UriUtils.getLastPathSegment(projectData.organizationLink)), true);
        this.host.resetSystemAuthorizationContext();

        this.host.assumeIdentity(
                UriUtils.buildUriPath(UserService.FACTORY_LINK,
                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(userData.email)),
                null);

        ProjectDeletionTaskState deleteData = new ProjectDeletionTaskState();
        deleteData.taskInfo = TaskState.createDirect();

        // post with no project link
        this.host.sendAndWaitExpectFailure(Operation
                .createPost(UriUtils.buildUri(peerUri, ProjectDeletionTaskService.FACTORY_LINK))
                .setBody(deleteData));

        deleteData.projectLink = project.documentSelfLink;
        deleteData.taskInfo = TaskState.createDirect();
        deleteData.documentSelfLink = UUID.randomUUID().toString();
        // try to delete a project that has not been retired
        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, ProjectDeletionTaskService.FACTORY_LINK))
                .setBody(deleteData));
        this.host.waitForFailedTask(ProjectUpdateTaskState.class,
                UriUtils.buildUri(peerUri,
                        UriUtils.buildUriPath(ProjectDeletionTaskService.FACTORY_LINK,
                                deleteData.documentSelfLink)).toString());

        ProjectUpdateTaskState updateData = new ProjectUpdateTaskState();
        updateData.status = ProjectStatus.RETIRED;
        updateData.taskInfo = TaskState.createDirect();
        updateData.projectLink = project.documentSelfLink;
        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, ProjectUpdateTaskService.FACTORY_LINK))
                .setBody(updateData));
        // now delete the project
        deleteData.documentSelfLink = null;
        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, ProjectDeletionTaskService.FACTORY_LINK))
                .setBody(deleteData));

        // verify that the resource pool created for the project has no tenantLink
        this.host.setSystemAuthorizationContext();
        res = SymphonyCommonTestUtils.queryDocuments(this.host, peerUri,
                Utils.buildKind(ResourcePoolState.class), 1);
        rpState =
                Utils.fromJson(res.documents.values().iterator().next(), ResourcePoolState.class);
        assertEquals(rpState.tenantLinks.size(), 0);

        res = SymphonyCommonTestUtils.queryDocuments(this.host, peerUri,
                Utils.buildKind(ProjectState.class), 0);
        res = SymphonyCommonTestUtils.queryDocuments(this.host, peerUri,
                Utils.buildKind(ScheduledTaskState.class), 1);
        this.host.setSystemAuthorizationContext();
        res = SymphonyCommonTestUtils.queryDocuments(this.host, peerUri,
                Utils.buildKind(UserGroupService.UserGroupState.class), 2);

        res = SymphonyCommonTestUtils.queryDocuments(this.host, peerUri,
                Utils.buildKind(ResourceGroupService.ResourceGroupState.class), 4);
        res = SymphonyCommonTestUtils.queryDocuments(this.host, peerUri,
                Utils.buildKind(RoleService.RoleState.class), 4);

        this.host.resetSystemAuthorizationContext();

    }

    @Test
    public void testOrgAdminAccessToProjects() throws Throwable {
        URI peerUri = this.host.getPeerHostUri();

        // Create user1 in the system
        UserCreationRequest userData = OnBoardingTestUtils
                .createUserData(USER_1_EMAIL, USER_1_PASSWORD);
        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, UserCreationService.SELF_LINK))
                .setBody(userData));

        // Create an organization as user 1. user 1 is the org admin by default
        OrganizationCreationRequest orgData = new OrganizationCreationRequest();
        orgData.orgId = ORG_1;
        orgData.organizationName = ORG_1_NAME;
        orgData.displayName = ORG_1_DISPLAY_NAME;

        this.host.assumeIdentity(
                UriUtils.buildUriPath(UserService.FACTORY_LINK,
                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(userData.email)), null);
        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, OrganizationCreationService.SELF_LINK))
                .setBody(orgData));

        // Create user 2 and associate that user with the existing org
        UserCreationRequest user2Data = OnBoardingTestUtils
                .createUserData(USER_2_EMAIL, USER_2_PASSWORD);
        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, UserCreationService.SELF_LINK))
                .setBody(user2Data));

        String organizationLink = UriUtils.buildUriPath(OrganizationService.FACTORY_LINK,
                Utils.computeHash(orgData.orgId));
        Operation response = OnBoardingTestUtils.addUserToOrgOrProject(this.host, peerUri,
                organizationLink, USER_2_EMAIL, USER_1_EMAIL, USER_1_PASSWORD);
        assertNotNull(response);
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        // Create a project as user 2
        this.host.assumeIdentity(
                UriUtils.buildUriPath(UserService.FACTORY_LINK,
                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(user2Data.email)),
                null);
        ProjectCreationTaskState projectData = new ProjectCreationTaskState();
        projectData.projectName = PROJECT_1;
        projectData.budget = new BigDecimal(100);
        projectData.organizationLink = organizationLink;

        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, ProjectCreationTaskService.FACTORY_LINK))
                .setBody(projectData));

        // Retrieve the project and verify that it has been created correctly.
        this.host.setSystemAuthorizationContext();
        ServiceDocumentQueryResult res = SymphonyCommonTestUtils.queryDocuments(this.host, peerUri,
                Utils.buildKind(ProjectState.class), 1);

        ProjectState project = Utils.fromJson(res.documents.values().iterator().next(),
                ProjectState.class);
        assertEquals(project.name, projectData.projectName);
        this.host.log(Level.INFO, "Project is " + Utils.toJson(false, true, project));
        this.host.resetSystemAuthorizationContext();

        // As user 1 who is org admin try updating and deleting the project
        this.host.assumeIdentity(
                UriUtils.buildUriPath(UserService.FACTORY_LINK,
                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(userData.email)), null);

        ProjectUpdateTaskState updateData = new ProjectUpdateTaskState();
        updateData.status = ProjectStatus.RETIRED;
        updateData.taskInfo = TaskState.createDirect();
        updateData.projectLink = project.documentSelfLink;
        Operation projectUpdateResponse = this.host.waitForResponse(Operation
                .createPost(UriUtils.buildUri(peerUri, ProjectUpdateTaskService.FACTORY_LINK))
                .setBody(updateData));
        assertNotNull(projectUpdateResponse);
        assertEquals(Operation.STATUS_CODE_OK, projectUpdateResponse.getStatusCode());

        // Get the project update task state and verify the status. It should have FINISHED
        Operation projectUpdateTaskStateOp = this.host.waitForResponse(Operation
                .createGet(UriUtils.buildExpandLinksQueryUri(
                        UriUtils.buildUri(peerUri, ProjectUpdateTaskService.FACTORY_LINK))));
        ServiceDocumentQueryResult result = projectUpdateTaskStateOp.getBody(ServiceDocumentQueryResult.class);
        assertNotNull(result.documents);
        String projectUpdateTaskJson = result.documents.values().iterator().next().toString();
        ProjectUpdateTaskState responseData = Utils.fromJson(projectUpdateTaskJson,
                ProjectUpdateTaskState.class);
        assertNotNull(responseData);
        assertEquals(responseData.taskInfo.stage, TaskStage.FINISHED);

        // Now delete the project as the org admin
        ProjectDeletionTaskState deleteData = new ProjectDeletionTaskState();
        deleteData.taskInfo = TaskState.createDirect();
        deleteData.projectLink = project.documentSelfLink;
        deleteData.taskInfo = TaskState.createDirect();
        deleteData.documentSelfLink = UUID.randomUUID().toString();
        deleteData.documentSelfLink = null;
        Operation projectDeleteResponse = this.host.waitForResponse(Operation
                .createPost(UriUtils.buildUri(peerUri, ProjectDeletionTaskService.FACTORY_LINK))
                .setBody(deleteData));
        assertNotNull(projectDeleteResponse);
        assertEquals(Operation.STATUS_CODE_OK, projectDeleteResponse.getStatusCode());
        assertEquals(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_1_EMAIL)), projectDeleteResponse.getBody(
                        ProjectDeletionTaskState.class).userLink);

        // Get the project deletion task state and verify the status. It should have FINISHED
        Operation projectDeletionTaskStateOp = this.host.waitForResponse(Operation
                .createGet(UriUtils.buildExpandLinksQueryUri(
                        UriUtils.buildUri(peerUri, ProjectUpdateTaskService.FACTORY_LINK))));
        result = projectDeletionTaskStateOp
                .getBody(ServiceDocumentQueryResult.class);
        assertNotNull(result.documents);
        String projectDeletionTaskJson = result.documents.values().iterator().next().toString();
        ProjectDeletionTaskState deletionResponse = Utils.fromJson(projectDeletionTaskJson,
                ProjectDeletionTaskState.class);
        assertNotNull(deletionResponse);
        assertEquals(deletionResponse.taskInfo.stage, TaskStage.FINISHED);
    }
}
