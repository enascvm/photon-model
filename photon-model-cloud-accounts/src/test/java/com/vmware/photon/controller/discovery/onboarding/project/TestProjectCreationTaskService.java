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
import static org.junit.Assert.assertTrue;

import static com.vmware.photon.controller.discovery.onboarding.OnboardingErrorCode.PROJECT_CREATION_TASK_FAILURE;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.discovery.common.PhotonControllerCloudAccountUtils;
import com.vmware.photon.controller.discovery.common.SymphonyCommonTestUtils;
import com.vmware.photon.controller.discovery.common.services.OrganizationService;
import com.vmware.photon.controller.discovery.common.services.OrganizationService.OrganizationState;
import com.vmware.photon.controller.discovery.common.services.ProjectService;
import com.vmware.photon.controller.discovery.common.services.ProjectService.ProjectState;
import com.vmware.photon.controller.discovery.common.services.ProjectService.ProjectStatus;
import com.vmware.photon.controller.discovery.common.services.ResourceEnumerationService;
import com.vmware.photon.controller.discovery.common.services.ResourcePoolConfigurationService;
import com.vmware.photon.controller.discovery.common.services.UserService;
import com.vmware.photon.controller.discovery.common.services.UserService.UserState;
import com.vmware.photon.controller.discovery.common.utils.ErrorUtil;
import com.vmware.photon.controller.discovery.onboarding.OnBoardingTestUtils;
import com.vmware.photon.controller.discovery.onboarding.OnboardingErrorCode;
import com.vmware.photon.controller.discovery.onboarding.organization.OrganizationCreationService;
import com.vmware.photon.controller.discovery.onboarding.organization.OrganizationCreationService.OrganizationCreationRequest;
import com.vmware.photon.controller.discovery.onboarding.project.ProjectCreationTaskService.ProjectCreationTaskState;
import com.vmware.photon.controller.discovery.onboarding.user.UserCreationService;
import com.vmware.photon.controller.discovery.onboarding.user.UserCreationService.UserCreationRequest;
import com.vmware.photon.controller.discovery.onboarding.user.UserUpdateService;
import com.vmware.photon.controller.discovery.onboarding.user.UserUpdateService.UserUpdateAction;
import com.vmware.photon.controller.discovery.onboarding.user.UserUpdateService.UserUpdateRequest;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.ResourceGroomerTaskService;
import com.vmware.photon.controller.model.tasks.ScheduledTaskService.ScheduledTaskState;
import com.vmware.photon.controller.model.tasks.monitoring.StatsCollectionTaskService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.ResourceGroupService;
import com.vmware.xenon.services.common.RoleService;
import com.vmware.xenon.services.common.UserGroupService;

public class TestProjectCreationTaskService {

    private static final String SEPERATOR = "-";
    private static final String ADMIN_SUFFIX = "admin";
    private static final String USER_SUFFIX = "user";
    private final int NUMBER_NODES = 3;
    private VerificationHost host;

    @Before
    public void setUp() throws Throwable {
        this.host = OnBoardingTestUtils.setupOnboardingServices(this.NUMBER_NODES);
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
    public void testProjectCreation() throws Throwable {

        URI peerUri = this.host.getPeerHostUri();

        // create 3 users in the system
        UserCreationRequest userData = OnBoardingTestUtils
                .createUserData("user-1@org.com", "passwordforuser-1");
        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, UserCreationService.SELF_LINK))
                .setBody(userData));

        UserCreationRequest userData2 = OnBoardingTestUtils
                .createUserData("user-2@org.com", "passwordforuser-2");
        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, UserCreationService.SELF_LINK))
                .setBody(userData2));

        UserCreationRequest userData3 = OnBoardingTestUtils
                .createUserData("user-3@org.com", "passwordforuser-3");
        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, UserCreationService.SELF_LINK))
                .setBody(userData3));

        // create an organization
        OrganizationCreationRequest orgData = new OrganizationCreationRequest();
        orgData.orgId = "org-1";
        orgData.organizationName = "org-1";
        orgData.displayName = "org-1 display name";

        this.host.assumeIdentity(
                UriUtils.buildUriPath(UserService.FACTORY_LINK,
                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(userData.email)), null);
        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, OrganizationCreationService.SELF_LINK))
                .setBody(orgData));

        ProjectCreationTaskState projectData = new ProjectCreationTaskState();
        projectData.projectName = "project-1";
        projectData.budget = new BigDecimal(100);
        projectData.description = "project-1 desc";

        // post to project creation service with no org name
        this.host.sendAndWaitExpectFailure(Operation
                .createPost(UriUtils.buildUri(peerUri, ProjectCreationTaskService.FACTORY_LINK))
                .setBody(projectData));

        String orgNameHash = Utils.computeHash(orgData.orgId);
        projectData.organizationLink = UriUtils.buildUriPath(OrganizationService.FACTORY_LINK,
                orgNameHash);

        projectData.taskInfo = TaskState.createDirect();
        // post to project creation service and make sure the right artifacts are created
        TestContext ctx = new TestContext(1, Duration.ofSeconds(10));
        this.host.send(Operation
                .createPost(UriUtils.buildUri(peerUri, ProjectCreationTaskService.FACTORY_LINK))
                .setBody(projectData)
                .setCompletion((postOp, postEx) -> {
                    if (postEx != null) {
                        ctx.fail(postEx);
                        return;
                    }
                    ProjectCreationTaskState returnState = postOp
                            .getBody(ProjectCreationTaskState.class);
                    if (returnState.projectLink == null) {
                        ctx.fail(new IllegalStateException("project info not obtained"));
                        return;
                    }

                    assertEquals(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                            PhotonControllerCloudAccountUtils.computeHashWithSHA256(userData.email)), returnState.userLink);
                    ctx.complete();
                }));
        ctx.await();

        // post to project creation service with the same name; the operation should not fail
        this.host.send(Operation
                .createPost(UriUtils.buildUri(peerUri, ProjectCreationTaskService.FACTORY_LINK))
                .setBody(projectData));

        // post to project creation service with same name, and verify the taskInfo failure details
        // message as part of response
        this.host.send(Operation
                .createPost(UriUtils.buildUri(peerUri, ProjectCreationTaskService.FACTORY_LINK))
                .setBody(projectData)
                .setCompletion((o, e) -> {
                    ServiceErrorResponse serviceErrorResponse = ErrorUtil.create(
                            OnboardingErrorCode.DUPLICATE_PROJECT_NAME);
                    ProjectCreationTaskState currentState = Utils
                            .fromJson(o.getBody(String.class), ProjectCreationTaskState.class);
                    assertEquals(currentState.taskInfo.stage, TaskState.TaskStage.FAILED);
                    assertEquals(currentState.taskInfo.failure.message,
                            serviceErrorResponse.message);
                    assertEquals(currentState.taskInfo.failure.messageId,
                            serviceErrorResponse.messageId);
                }));

        // verify in system context as all these resources are owned by the system
        this.host.setSystemAuthorizationContext();

        ServiceDocumentQueryResult res = SymphonyCommonTestUtils.queryDocuments(this.host, peerUri,
                Utils.buildKind(ProjectState.class), 1);

        ProjectState project =
                Utils.fromJson(res.documents.values().iterator().next(),
                        ProjectState.class);
        assertEquals(project.name, projectData.projectName);
        assertEquals(project.budget, projectData.budget);
        assertEquals(project.description, projectData.description);
        assertEquals(project.status, ProjectStatus.DRAFT);
        String projectLink = UriUtils.getLastPathSegment(project.documentSelfLink);

        res = SymphonyCommonTestUtils.queryDocuments(this.host, peerUri,
                Utils.buildKind(UserGroupService.UserGroupState.class), 4);

        res = SymphonyCommonTestUtils.queryDocuments(this.host, peerUri,
                Utils.buildKind(ResourceGroupService.ResourceGroupState.class), 5);

        res = SymphonyCommonTestUtils.queryDocuments(this.host, peerUri,
                Utils.buildKind(RoleService.RoleState.class), 5);

        res = SymphonyCommonTestUtils.queryDocuments(this.host, peerUri,
                Utils.buildKind(UserService.UserState.class), 3);

        String userEmailHash = PhotonControllerCloudAccountUtils.computeHashWithSHA256(userData.email);
        UserState userState =
                Utils.fromJson(res.documents.get(UriUtils.buildUriPath(
                        UserService.FACTORY_LINK, userEmailHash)), UserState.class);
        for (String userGroupLink : userState.userGroupLinks) {
            String adminUserGroup = UriUtils
                    .buildUriPath(UserGroupService.FACTORY_LINK,
                            (new StringBuffer(projectLink).append(SEPERATOR)
                                    .append(ADMIN_SUFFIX)).toString());
            String nonAdminUserGroup = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                    (new StringBuffer(projectLink).append(SEPERATOR).append(USER_SUFFIX))
                            .toString());
            String orgAdminUserGroup = UriUtils
                    .buildUriPath(UserGroupService.FACTORY_LINK,
                            (new StringBuffer(orgNameHash).append(SEPERATOR)
                                    .append(ADMIN_SUFFIX)).toString());
            String orgNonAdminUserGroup = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                    (new StringBuffer(orgNameHash).append(SEPERATOR).append(USER_SUFFIX))
                            .toString());
            assertTrue(userGroupLink.equals(adminUserGroup)
                    || userGroupLink.equals(nonAdminUserGroup)
                    || userGroupLink.equals(orgAdminUserGroup)
                    || userGroupLink.equals(orgNonAdminUserGroup));
        }

        res = SymphonyCommonTestUtils.queryDocuments(this.host, peerUri,
                Utils.buildKind(ResourcePoolState.class), 1);
        ResourcePoolState resourcePoolState = Utils
                .fromJson(res.documents.values().iterator().next(),
                        ResourcePoolState.class);
        assertEquals(UriUtils.getLastPathSegment(resourcePoolState.documentSelfLink),
                UriUtils.getLastPathSegment(project.documentSelfLink));
        assertEquals(resourcePoolState.tenantLinks, Collections
                .singletonList(UriUtils.buildUriPath(
                        ProjectService.FACTORY_LINK, projectLink)));
        assertEquals(resourcePoolState.documentSelfLink,
                UriUtils.buildUriPath(ResourcePoolService.FACTORY_LINK, projectLink));

        // check if stats collection is started.
        res = SymphonyCommonTestUtils.queryDocuments(this.host, peerUri,
                Utils.buildKind(ScheduledTaskState.class), 3);
        for (Object taskObj : res.documents.values()) {
            ScheduledTaskState statsTaskState = Utils
                    .fromJson(taskObj, ScheduledTaskState.class);
            assertTrue(
                            statsTaskState.factoryLink
                                    .equals(StatsCollectionTaskService.FACTORY_LINK) ||
                            statsTaskState.factoryLink
                                    .equals(ResourceEnumerationService.SELF_LINK) ||
                                    statsTaskState.factoryLink.equals(ResourceGroomerTaskService.FACTORY_LINK));
        }

        this.host.resetSystemAuthorizationContext();

        // verify that the user can access the resource pool created for the project
        this.host.assumeIdentity(
                UriUtils.buildUriPath(UserService.FACTORY_LINK,
                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(userData.email)),
                null);
        res = this.host
                .getFactoryState(UriUtils.buildUri(peerUri, ResourcePoolService.FACTORY_LINK));
        assertTrue(res.documentCount == 1);

        // verify that one of the stateless service the user has access to can be invoked
        this.host.sendAndWaitExpectSuccess(Operation
                .createGet(UriUtils.buildUri(peerUri, ResourcePoolConfigurationService.SELF_LINK)));

        // verify that the user who does not belong to the org cannot see the project that was created
        this.host.assumeIdentity(
                UriUtils.buildUriPath(UserService.FACTORY_LINK,
                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(userData2.email)),
                null);
        this.host.sendAndWaitExpectFailure(Operation
                .createGet(UriUtils.buildUri(peerUri, UriUtils.buildUriPath(
                        ProjectService.FACTORY_LINK,
                        projectLink))));

        // add user-2 to the project as a non-admin
        this.host.assumeIdentity(
                UriUtils.buildUriPath(UserService.FACTORY_LINK,
                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(userData.email)),
                null);
        String user2EmailHash = PhotonControllerCloudAccountUtils.computeHashWithSHA256(userData2.email);
        UserUpdateRequest userPatchData = new UserUpdateRequest();
        userPatchData.email = userData2.email;
        userPatchData.isAdmin = false;
        userPatchData.entityLink = UriUtils.buildUriPath(
                ProjectService.FACTORY_LINK,
                projectLink);
        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, UserUpdateService.SELF_LINK))
                .setBody(userPatchData));
        // check to see if the user can view resources that are part of the project
        this.host.assumeIdentity(
                UriUtils.buildUriPath(UserService.FACTORY_LINK,
                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(userData2.email)), null);
        res = this.host
                .getFactoryState(UriUtils.buildUri(peerUri, ResourcePoolService.FACTORY_LINK));
        assertTrue(res.documentCount == 1);

        this.host.setSystemAuthorizationContext();
        res = SymphonyCommonTestUtils.queryDocuments(this.host, peerUri,
                Utils.buildKind(UserService.UserState.class), 3);

        UserState user =
                Utils.fromJson(res.documents.get(UriUtils.buildUriPath(
                        UserService.FACTORY_LINK, user2EmailHash)), UserState.class);
        assertTrue(user.userGroupLinks.size() == 1);
        assertTrue(user.userGroupLinks.contains(UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                new StringBuffer(projectLink)
                        .append(SEPERATOR).append(USER_SUFFIX).toString())));
        this.host.resetSystemAuthorizationContext();

        // next add the user as an admin user
        this.host.assumeIdentity(
                UriUtils.buildUriPath(UserService.FACTORY_LINK,
                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(userData.email)), null);
        userPatchData = new UserUpdateRequest();
        userPatchData.email = userData2.email;
        userPatchData.isAdmin = true;
        userPatchData.entityLink = UriUtils.buildUriPath(ProjectService.FACTORY_LINK,
                projectLink);
        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, UserUpdateService.SELF_LINK))
                .setBody(userPatchData));

        this.host.setSystemAuthorizationContext();
        res = SymphonyCommonTestUtils.queryDocuments(this.host, peerUri,
                Utils.buildKind(UserService.UserState.class), 3);
        user = Utils.fromJson(res.documents.get(UriUtils.buildUriPath(
                UserService.FACTORY_LINK, user2EmailHash)), UserState.class);
        assertTrue(user.userGroupLinks.size() == 2);
        assertTrue(user.userGroupLinks.contains(UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                new StringBuffer(projectLink)
                        .append(SEPERATOR).append(ADMIN_SUFFIX).toString())));
        assertTrue(user.userGroupLinks.contains(UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                new StringBuffer(projectLink)
                        .append(SEPERATOR).append(USER_SUFFIX).toString())));
        this.host.resetSystemAuthorizationContext();

        // create project-2 as the system user; the logged in user does not have permissions to
        // access this project
        this.host.setSystemAuthorizationContext();
        ProjectState project2State =
                new ProjectState();
        project2State.name = "project-2";
        String project2Hash = Utils.computeHash(project2State.name);
        project2State.documentSelfLink = project2Hash;
        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, ProjectService.FACTORY_LINK))
                .setBody(project2State));
        this.host.resetSystemAuthorizationContext();

        // try to add user-2 to the new project, the operation should fail as the logged in user is
        // not an admin
        this.host.assumeIdentity(
                UriUtils.buildUriPath(UserService.FACTORY_LINK,
                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(userData.email)), null);
        UserUpdateRequest userPatchDataForTeam2 = new UserUpdateRequest();
        userPatchDataForTeam2.email = userData2.email;
        userPatchDataForTeam2.isAdmin = false;
        userPatchDataForTeam2.entityLink = UriUtils.buildUriPath(ProjectService.FACTORY_LINK,
                project2Hash);
        this.host.sendAndWaitExpectFailure(Operation
                .createPost(UriUtils.buildUri(peerUri, UserUpdateService.SELF_LINK))
                .setBody(userPatchDataForTeam2));

        // add user-3 as a non admin to the parent org.
        // This user should not have any visibility into the resources of the project, but can see the project
        UserUpdateRequest userPatchDataForUser3 = new UserUpdateRequest();
        userPatchDataForUser3.email = userData3.email;
        userPatchDataForUser3.isAdmin = false;
        userPatchDataForUser3.entityLink = UriUtils.buildUriPath(OrganizationService.FACTORY_LINK,
                orgNameHash);
        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, UserUpdateService.SELF_LINK))
                .setBody(userPatchDataForUser3));
        this.host.assumeIdentity(
                UriUtils.buildUriPath(UserService.FACTORY_LINK,
                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(userData3.email)), null);
        // no resource pool entries must be visible to this user
        res = this.host
                .getFactoryState(UriUtils.buildUri(peerUri, ResourcePoolService.FACTORY_LINK));
        assertTrue(res.documentCount == 0);

        this.host.sendAndWaitExpectSuccess(Operation
                .createGet(UriUtils.buildUri(peerUri, UriUtils.buildUriPath(
                        ProjectService.FACTORY_LINK,
                        projectLink))));

        this.host.sendAndWaitExpectFailure(Operation
                .createDelete(UriUtils.buildUri(peerUri, UriUtils.buildUriPath(
                        ProjectService.FACTORY_LINK,
                        projectLink))));

        // check to verify that an non-admin on the org can create a new project
        ProjectCreationTaskState newProjectData = new ProjectCreationTaskState();
        newProjectData.projectName = "new-project-1";
        newProjectData.organizationLink = UriUtils.buildUriPath(OrganizationService.FACTORY_LINK,
                orgNameHash);
        newProjectData.taskInfo = TaskState.createDirect();
        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, ProjectCreationTaskService.FACTORY_LINK))
                .setBody(newProjectData));

        // next add the same user as an admin of the org; the user should automatically get visibility
        // to the projects under the org
        this.host.assumeIdentity(
                UriUtils.buildUriPath(UserService.FACTORY_LINK,
                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(userData.email)),
                null);
        userPatchDataForUser3 = new UserUpdateRequest();
        userPatchDataForUser3.email = userData3.email;
        userPatchDataForUser3.isAdmin = true;
        userPatchDataForUser3.entityLink = UriUtils.buildUriPath(OrganizationService.FACTORY_LINK,
                orgNameHash);
        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, UserUpdateService.SELF_LINK))
                .setBody(userPatchDataForUser3));

        // check to see that user-3 can view the resource pool within the project
        this.host.assumeIdentity(
                UriUtils.buildUriPath(UserService.FACTORY_LINK,
                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(userData3.email)),
                null);
        this.host.waitFor("Timeout waiting for resource pool", () -> {
            ServiceDocumentQueryResult queryRes = this.host
                    .getFactoryState(UriUtils.buildUri(peerUri, ResourcePoolService.FACTORY_LINK));
            return queryRes.documentCount == 2;
        });

        // verify that one of the stateless service the user has access to can be invoked
        this.host.sendAndWaitExpectSuccess(Operation
                .createGet(UriUtils.buildUri(peerUri, ResourcePoolConfigurationService.SELF_LINK)));

        // remove the user from the org
        this.host.assumeIdentity(
                UriUtils.buildUriPath(UserService.FACTORY_LINK,
                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(userData.email)), null);
        userPatchDataForUser3 = new UserUpdateRequest();
        userPatchDataForUser3.email = userData3.email;
        userPatchDataForUser3.entityLink = UriUtils.buildUriPath(OrganizationService.FACTORY_LINK,
                orgNameHash);
        userPatchDataForUser3.action = UserUpdateAction.REMOVE_USER;
        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, UserUpdateService.SELF_LINK))
                .setBody(userPatchDataForUser3));

        // check to see that user-3 can no longer see resources under this org
        this.host.assumeIdentity(
                UriUtils.buildUriPath(UserService.FACTORY_LINK,
                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(userData3.email)), null);
        this.host.waitFor("Timeout waiting for resource pool", () -> {
            ServiceDocumentQueryResult queryRes = this.host
                    .getFactoryState(UriUtils.buildUri(peerUri, ResourcePoolService.FACTORY_LINK));
            return queryRes.documentCount == 0;
        });
    }

    @Test
    public void testRollbackLogic() throws Throwable {
        URI peerUri = this.host.getPeerHostUri();

        // create an user
        UserCreationRequest userData = OnBoardingTestUtils
                .createUserData("user-1@org.com", "passwordforuser-1");
        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, UserCreationService.SELF_LINK))
                .setBody(userData));

        // create an organization
        OrganizationCreationRequest orgData = new OrganizationCreationRequest();
        orgData.orgId = "org-1";
        orgData.organizationName = "org-1";
        orgData.displayName = "org-1 display name";

        this.host.assumeIdentity(
                UriUtils.buildUriPath(UserService.FACTORY_LINK,
                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(userData.email)), null);
        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, OrganizationCreationService.SELF_LINK))
                .setBody(orgData));

        // shut down one service
        Operation shutDownService = this.host.waitForResponse(Operation
                .createDelete(UriUtils.buildUri(peerUri, RoleService.FACTORY_LINK)));
        assertEquals(Operation.STATUS_CODE_OK, shutDownService.getStatusCode());

        ProjectCreationTaskState projectData = new ProjectCreationTaskState();
        projectData.projectName = "project-1";
        projectData.budget = new BigDecimal(100);
        projectData.description = "project-1 desc";

        String orgNameHash = Utils.computeHash(orgData.orgId);
        projectData.organizationLink = UriUtils.buildUriPath(OrganizationService.FACTORY_LINK,
                orgNameHash);

        projectData.taskInfo = TaskState.createDirect();
        // post to project creation service and make sure the right artifacts are created
        TestContext ctx = new TestContext(1, Duration.ofSeconds(10));
        this.host.sendWithDeferredResult(Operation
                .createPost(UriUtils.buildUri(peerUri, ProjectCreationTaskService.FACTORY_LINK))
                .setBody(projectData))
                .whenComplete((postOp, postEx) -> {
                    if (postEx != null) {
                        ctx.fail(postEx);
                        return;
                    }
                    ProjectCreationTaskState returnState = postOp.getBody(ProjectCreationTaskState.class);

                    assertNotNull(returnState.failureMessage);
                    assertEquals(PROJECT_CREATION_TASK_FAILURE.getErrorCode(),
                            Integer.parseInt(returnState.taskInfo.failure.messageId));

                    ctx.complete();
                });
        ctx.await();

        this.host.setSystemAuthorizationContext();
        SymphonyCommonTestUtils.queryDocuments(this.host, peerUri,
                Utils.buildKind(OrganizationState.class), 1);
        SymphonyCommonTestUtils.queryDocuments(this.host, peerUri,
                Utils.buildKind(UserState.class), 1);
        this.host.setSystemAuthorizationContext();
        SymphonyCommonTestUtils.queryDocuments(this.host, peerUri,
                Utils.buildKind(ProjectState.class), 0);
    }
}
