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

import java.math.BigDecimal;
import java.net.URI;
import java.util.Collections;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.vmware.photon.controller.discovery.common.PhotonControllerCloudAccountUtils;
import com.vmware.photon.controller.discovery.common.SymphonyCommonTestUtils;
import com.vmware.photon.controller.discovery.common.UtilizationThreshold;
import com.vmware.photon.controller.discovery.common.services.OrganizationService;
import com.vmware.photon.controller.discovery.common.services.ProjectService.ProjectState;
import com.vmware.photon.controller.discovery.common.services.ProjectService.ProjectStatus;
import com.vmware.photon.controller.discovery.common.services.UserService;
import com.vmware.photon.controller.discovery.onboarding.OnBoardingTestUtils;
import com.vmware.photon.controller.discovery.onboarding.organization.OrganizationCreationService;
import com.vmware.photon.controller.discovery.onboarding.organization.OrganizationCreationService.OrganizationCreationRequest;
import com.vmware.photon.controller.discovery.onboarding.project.ProjectCreationTaskService.ProjectCreationTaskState;
import com.vmware.photon.controller.discovery.onboarding.project.ProjectUpdateTaskService.ProjectUpdateTaskState;
import com.vmware.photon.controller.discovery.onboarding.user.UserCreationService;
import com.vmware.photon.controller.discovery.onboarding.user.UserCreationService.UserCreationRequest;
import com.vmware.photon.controller.discovery.onboarding.user.UserUpdateService;
import com.vmware.photon.controller.discovery.onboarding.user.UserUpdateService.UserUpdateRequest;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.common.test.VerificationHost;

public class TestProjectUpdateTaskService {

    private static final String UT_METRIC = "CPU";
    private static final UtilizationThreshold UT1 = new UtilizationThreshold();

    static {
        UT1.overLimit = 10;
        UT1.underLimit = 1;
        UT1.unit = "TO";
    }

    private static final UtilizationThreshold UT2 = new UtilizationThreshold();

    static {
        UT1.overLimit = 9;
        UT1.underLimit = 2;
        UT1.unit = "GO";
    }

    private VerificationHost host;
    // VSYM - 1803
    private final int NUMBER_NODES = 1;

    @Before
    public void setUp() throws Throwable {
        this.host =  OnBoardingTestUtils.setupOnboardingServices(this.NUMBER_NODES);
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

    @Ignore //{VSYM-4244}
    @Test
    public void testProjectUpdate() throws Throwable {

        URI peerUri = this.host.getPeerHostUri();

        // create 2 users in the system
        UserCreationRequest userData = OnBoardingTestUtils.createUserData("user-1@org.com", "passwordforuser-1");
        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, UserCreationService.SELF_LINK))
                .setBody(userData));

        UserCreationRequest userData2 = OnBoardingTestUtils.createUserData("user-2@org.com", "passwordforuser-2");
        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, UserCreationService.SELF_LINK))
                .setBody(userData2));

        // create an organization
        OrganizationCreationRequest orgData = new OrganizationCreationRequest();
        orgData.orgId = "org-1";
        orgData.organizationName = "org-1";
        orgData.displayName = "org-1 display name";

        this.host.assumeIdentity(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(userData.email)), null);
        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, OrganizationCreationService.SELF_LINK))
                .setBody(orgData));

        ProjectCreationTaskState projectData = new ProjectCreationTaskState();
        projectData.projectName = "project-1";
        projectData.budget = new BigDecimal(100);
        projectData.description = "project-1 desc";
        String orgNameHash = Utils.computeHash(orgData.orgId);
        projectData.organizationLink = UriUtils.buildUriPath(OrganizationService.FACTORY_LINK,
                orgNameHash);
        projectData.utilizationThresholds = Collections.singletonMap(UT_METRIC, UT1);

        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, ProjectCreationTaskService.FACTORY_LINK))
                .setBody(projectData));
        // verify in system context as all these resources are owned by the system
        this.host.setSystemAuthorizationContext();
        ServiceDocumentQueryResult res = SymphonyCommonTestUtils.queryDocuments(this.host, peerUri,
                Utils.buildKind(ProjectState.class), 1);

        ProjectState project =
                Utils.fromJson(res.documents.values().iterator().next(), ProjectState.class);
        assertEquals(projectData.projectName, project.name);
        assertEquals(projectData.budget, project.budget);
        assertEquals(projectData.description, project.description);
        assertEquals(projectData.utilizationThresholds.size(),
                project.utilizationThresholds.size());
        assertEquals(projectData.utilizationThresholds.get(UT_METRIC).overLimit,
                project.utilizationThresholds.get(UT_METRIC).overLimit);
        assertEquals(projectData.utilizationThresholds.get(UT_METRIC).underLimit,
                project.utilizationThresholds.get(UT_METRIC).underLimit);
        assertEquals(projectData.utilizationThresholds.get(UT_METRIC).unit,
                project.utilizationThresholds.get(UT_METRIC).unit);
        this.host.resetSystemAuthorizationContext();

        // verify that the user can access the resource pool created for the project
        this.host.assumeIdentity(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(userData.email)), null);

        ProjectUpdateTaskState updateData = new ProjectUpdateTaskState();
        updateData.projectName = "project-2";
        updateData.budget = new BigDecimal(1000);
        updateData.description = "project-2 desc";
        updateData.status = ProjectStatus.DRAFT;
        updateData.utilizationThresholds = Collections.singletonMap(UT_METRIC, UT2);

        updateData.taskInfo = TaskState.createDirect();

        // post with no project link
        this.host.sendAndWaitExpectFailure(Operation
                .createPost(UriUtils.buildUri(peerUri, ProjectUpdateTaskService.FACTORY_LINK))
                .setBody(updateData));

        updateData.projectLink = project.documentSelfLink;

        // test if update on project is successful with unchanged status when project in DRAFT status
        verifySuccessfulProjectUpdate(updateData, peerUri, userData.email);

        // make project status ACTIVE
        updateData.status = ProjectStatus.ACTIVE;
        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, ProjectUpdateTaskService.FACTORY_LINK))
                .setBody(updateData));

        // test if update on project is successful with unchanged status when project in ACTIVE status
        verifySuccessfulProjectUpdate(updateData, peerUri, userData.email);

        // test some invalid state transitions
        updateData.status = ProjectStatus.RETIRED;
        updateData.documentSelfLink = UUID.randomUUID().toString();
        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, ProjectUpdateTaskService.FACTORY_LINK))
                .setBody(updateData));
        this.host.waitForFailedTask(ProjectUpdateTaskState.class,
                UriUtils.buildUri(peerUri,
                        UriUtils.buildUriPath(ProjectUpdateTaskService.FACTORY_LINK, updateData.documentSelfLink)).toString());
        updateData.status = ProjectStatus.DRAFT;
        updateData.documentSelfLink = UUID.randomUUID().toString();
        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, ProjectUpdateTaskService.FACTORY_LINK))
                .setBody(updateData));
        this.host.waitForFailedTask(ProjectUpdateTaskState.class,
                UriUtils.buildUri(peerUri,
                        UriUtils.buildUriPath(ProjectUpdateTaskService.FACTORY_LINK, updateData.documentSelfLink)).toString());

        // verify in system context as all these resources are owned by the system
        this.host.setSystemAuthorizationContext();
        res = SymphonyCommonTestUtils.queryDocuments(this.host, peerUri,
                Utils.buildKind(ProjectState.class), 1);

        project =
                Utils.fromJson(res.documents.values().iterator().next(), ProjectState.class);
        assertEquals(updateData.projectName, project.name);
        assertEquals(updateData.budget, project.budget);
        assertEquals(updateData.description, project.description);
        assertEquals(ProjectStatus.ACTIVE, project.status);
        assertEquals(updateData.utilizationThresholds.size(),
                project.utilizationThresholds.size());
        assertEquals(updateData.utilizationThresholds.get(UT_METRIC).overLimit,
                project.utilizationThresholds.get(UT_METRIC).overLimit);
        assertEquals(updateData.utilizationThresholds.get(UT_METRIC).underLimit,
                project.utilizationThresholds.get(UT_METRIC).underLimit);
        assertEquals(updateData.utilizationThresholds.get(UT_METRIC).unit,
                project.utilizationThresholds.get(UT_METRIC).unit);
        this.host.resetSystemAuthorizationContext();

        // assume identity of user2; this user should not be able to update the project
        this.host.assumeIdentity(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(userData2.email)), null);
        updateData.documentSelfLink = null;
        this.host.sendAndWaitExpectFailure(Operation
                .createPost(UriUtils.buildUri(peerUri, ProjectUpdateTaskService.FACTORY_LINK))
                .setBody(updateData));

        // add user-2 to the project as a non-admin
        this.host.assumeIdentity(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(userData.email)), null);
        UserUpdateRequest userPatchData = new UserUpdateRequest();
        userPatchData.email = userData2.email;
        userPatchData.isAdmin = false;
        userPatchData.entityLink = project.documentSelfLink;
        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, UserUpdateService.SELF_LINK))
                .setBody(userPatchData));

        this.host.assumeIdentity(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(userData2.email)), null);
        TestContext ctx = this.host.testCreate(1);
        this.host.send(Operation
                .createPost(UriUtils.buildUri(peerUri, ProjectUpdateTaskService.FACTORY_LINK))
                .setBody(updateData)
                .setCompletion((postOp, postEx) -> {
                    if (postEx != null) {
                        ctx.failIteration(postEx);
                        return;
                    }
                    ProjectUpdateTaskState returnState = postOp.getBody(ProjectUpdateTaskState.class);
                    if (!TaskState.isFailed(returnState.taskInfo)) {
                        ctx.failIteration(new IllegalStateException("task did not fail as expected"));
                        return;
                    }
                    ctx.completeIteration();
                }));
        this.host.testWait(ctx);

        // next add the user as an admin user
        this.host.assumeIdentity(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(userData.email)), null);
        userPatchData = new UserUpdateRequest();
        userPatchData.email = userData2.email;
        userPatchData.isAdmin = true;
        userPatchData.entityLink = project.documentSelfLink;
        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, UserUpdateService.SELF_LINK))
                .setBody(userPatchData));

        this.host.assumeIdentity(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(userData2.email)), null);
        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, ProjectUpdateTaskService.FACTORY_LINK))
                .setBody(updateData));
    }

    /**
     * Verifies that project update is successful.
     * @param updateData Desired project state
     */
    private void verifySuccessfulProjectUpdate(ProjectUpdateTaskState updateData, URI peerUri, String userEmail) {
        TestContext ctx = this.host.testCreate(1);
        this.host.send(Operation
                .createPost(UriUtils.buildUri(peerUri, ProjectUpdateTaskService.FACTORY_LINK))
                .setBody(updateData)
                .setCompletion((postOp, postEx) -> {
                    if (postEx != null) {
                        ctx.failIteration(postEx);
                        return;
                    }
                    ProjectUpdateTaskState returnState = postOp.getBody(ProjectUpdateTaskState.class);
                    if (TaskState.isFailed(returnState.taskInfo)) {
                        ctx.failIteration(new IllegalStateException("Task failed, not expected."));
                        return;
                    }

                    assertEquals(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                            PhotonControllerCloudAccountUtils.computeHashWithSHA256(userEmail)), returnState.userLink);
                    ctx.completeIteration();
                }));
        this.host.testWait(ctx);
    }
}
