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

package com.vmware.photon.controller.discovery.common.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static com.vmware.xenon.common.Operation.REQUEST_AUTH_TOKEN_HEADER;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.discovery.common.CloudAccountConstants;
import com.vmware.photon.controller.discovery.common.PhotonControllerCloudAccountUtils;
import com.vmware.photon.controller.discovery.common.SymphonyCommonTestUtils;
import com.vmware.photon.controller.discovery.common.authn.SymphonyBasicAuthenticationService;
import com.vmware.photon.controller.discovery.common.services.ProjectService.ProjectState;
import com.vmware.photon.controller.discovery.common.services.UserContextQueryService.UserContext;
import com.vmware.photon.controller.discovery.onboarding.OnBoardingTestUtils;
import com.vmware.photon.controller.discovery.onboarding.organization.OrganizationCreationService;
import com.vmware.photon.controller.discovery.onboarding.organization.OrganizationCreationService.OrganizationCreationRequest;
import com.vmware.photon.controller.discovery.onboarding.user.UserCreationService;
import com.vmware.photon.controller.discovery.onboarding.user.UserCreationService.UserCreationRequest;
import com.vmware.photon.controller.discovery.onboarding.user.UserUpdateService;
import com.vmware.photon.controller.discovery.onboarding.user.UserUpdateService.UserUpdateRequest;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.xenon.common.Claims;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.AuthorizationContext;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;

public class TestUserContextQueryService {
    private int NUMBER_NODES = 3;
    private String USER1_EMAIL = "user@tenant1.com";
    private String USER1_PSW = "psw1";
    private String USER1_ORG = "org1";

    private String USER2_EMAIL = "user@tenant2.com";
    private String USER2_PSW = "psw2";
    private String USER2_ORG = "org2";

    private static final String ENV_1 = "env-1";
    private static final String PROJECT_1 = "project-1";
    private static final String PROJECT_2 = "project-2";
    private String USER3_EMAIL = "user3@tenant1.com";
    private String USER3_PSW = "psw3";

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
    public void testTenantOnboarding() throws Throwable {
        URI peerUri = this.host.getPeerHostUri();
        String projectName = PROJECT_1;

        createUserAndOrg(this.USER1_EMAIL, this.USER1_PSW, this.USER1_ORG, peerUri);
        createProject(projectName, this.USER1_EMAIL, this.USER1_PSW,
                        this.USER1_ORG, peerUri);

        // create a project and don't associate it with any org; this should not show up in the result
        this.host.setSystemAuthorizationContext();
        ResourceGroupState projectState = new ResourceGroupState();
        projectState.name = projectName;
        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, ProjectService.FACTORY_LINK))
                .setBody(projectState));
        this.host.resetSystemAuthorizationContext();

        SymphonyCommonTestUtils.authenticate(this.host, peerUri, this.USER1_EMAIL,
                this.USER1_PSW);

        // Issue request to the tenant query service and see if the right tenantLinks are obtained
        this.host.sendAndWait(Operation
                .createGet(UriUtils.buildUri(peerUri, UserContextQueryService.SELF_LINK))
                .setCompletion(
                        (op, ex) -> {
                            if (ex != null) {
                                this.host.failIteration(ex);
                                return;
                            }

                            UserContext userContext = op.getBody(UserContext.class);
                            if (userContext.projects.size() != 1) {
                                this.host.failIteration(new IllegalStateException(
                                        "project size is incorrect"));
                                return;
                            }
                            if (!userContext.projects.iterator().next().name
                                    .equals(projectName)) {
                                this.host.failIteration(new IllegalStateException(
                                        "project name is incorrect"));
                                return;
                            }
                            if (userContext.organizations.size() != 1) {
                                this.host.failIteration(new IllegalStateException(
                                        "org size is incorrect"));
                                return;
                            }
                            if (!userContext.organizations.iterator().next().id
                                    .equals(this.USER1_ORG)) {
                                this.host.failIteration(new IllegalStateException(
                                        "org name is incorrect"));
                                return;
                            }
                            if (!userContext.user.email.equals(this.USER1_EMAIL)) {
                                this.host.failIteration(new IllegalStateException(
                                        "user email is incorrect"));
                                return;
                            }
                            this.host.completeIteration();
                        }
                ));

        // create a new org and team and reissue the query
        OrganizationCreationRequest org2Data = new OrganizationCreationRequest();
        org2Data.orgId = "org-2";
        org2Data.organizationName = "org-2";
        org2Data.displayName = "org-2 display name";
        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, OrganizationCreationService.SELF_LINK))
                .setBody(org2Data));
        // create a project for the org created above
        String project2Name = "project-2";
        OnBoardingTestUtils.setupProject(this.host, peerUri, project2Name,
                org2Data.organizationName, this.USER1_EMAIL,
                this.USER1_PSW);
        this.host.sendAndWait(Operation
                .createGet(UriUtils.buildUri(peerUri, UserContextQueryService.SELF_LINK))
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        this.host.failIteration(ex);
                        return;
                    }
                    UserContext userContext = op.getBody(UserContext.class);
                    if (userContext.projects.size() != 2) {
                        this.host.failIteration(new IllegalStateException("team size is incorrect"));
                        return;
                    }
                    if (userContext.organizations.size() != 2) {
                        this.host.failIteration(new IllegalStateException("org size is incorrect"));
                        return;
                    }
                    if (!userContext.user.email.equals(this.USER1_EMAIL)) {
                        this.host.failIteration(new IllegalStateException("user email is incorrect"));
                        return;
                    }
                    this.host.completeIteration();
                }
                ));
    }

    @Test
    public void testCrossOrgProjectNameCollision() throws Throwable {
        URI peerUri = this.host.getPeerHostUri();
        createUserAndOrg(this.USER1_EMAIL, this.USER1_PSW, this.USER1_ORG, peerUri);
        createUserAndOrg(this.USER2_EMAIL, this.USER2_PSW, this.USER2_ORG, peerUri);

        // create projects having the same name than their org
        createProject(this.USER1_ORG, this.USER1_EMAIL, this.USER1_PSW,
                        this.USER1_ORG, peerUri);
        createProject(this.USER2_ORG, this.USER2_EMAIL, this.USER2_PSW,
                        this.USER2_ORG, peerUri);

        // create projects having the same name than the other org
        createProject(this.USER2_ORG, this.USER1_EMAIL, this.USER1_PSW,
                        this.USER1_ORG, peerUri);
        createProject(this.USER1_ORG, this.USER2_EMAIL, this.USER2_PSW,
                        this.USER2_ORG, peerUri);

        // create projects having the same name across orgs
        createProject("testProject", this.USER1_EMAIL, this.USER1_PSW, this.USER1_ORG, peerUri);
        createProject("testProject", this.USER2_EMAIL, this.USER2_PSW, this.USER2_ORG, peerUri);

        UserContext user1Ctx = getUserContext(this.USER1_EMAIL, this.USER1_PSW, peerUri);
        assertNotNull(user1Ctx);
        assertNotNull(user1Ctx.organizations);
        assertEquals(1, user1Ctx.organizations.size());
        assertNotNull(user1Ctx.user);
        assertEquals(this.USER1_EMAIL, user1Ctx.user.email);
        assertProject(user1Ctx, "testProject", this.USER2_ORG, this.USER1_ORG);


        UserContext user2Ctx = getUserContext(this.USER2_EMAIL, this.USER2_PSW, peerUri);
        assertNotNull(user2Ctx);
        assertNotNull(user2Ctx.organizations);
        assertEquals(1, user2Ctx.organizations.size());
        assertNotNull(user2Ctx.user);
        assertEquals(this.USER2_EMAIL, user2Ctx.user.email);
        assertProject(user2Ctx, "testProject", this.USER2_ORG, this.USER1_ORG);

    }

    @Test
    public void testUserContextForOrgAdmin() throws Throwable {
        URI peerUri = this.host.getPeerHostUri();
        // User 1 is the org admin for the org
        createUserAndOrg(this.USER1_EMAIL, this.USER1_PSW, this.USER1_ORG, peerUri);
        createProject(PROJECT_1, this.USER1_EMAIL, this.USER1_PSW, this.USER1_ORG, peerUri);

        // Create User 3 and associate them with the existing org
        createUser(this.USER3_EMAIL, this.USER3_PSW, peerUri);
        String organizationLink = UriUtils.buildUriPath(OrganizationService.FACTORY_LINK,
                Utils.computeHash(this.USER1_ORG));
        associateExistingUserWithAnOrg(this.USER1_EMAIL, this.USER3_EMAIL, organizationLink);

        // Get the user context for a user that is not part of any projects so the user context
        // query should return 0 projects.
        UserContext user2Ctx = getUserContext(this.USER3_EMAIL, this.USER3_PSW, peerUri);
        assertNotNull(user2Ctx);
        assertNotNull(user2Ctx.organizations);
        assertEquals(1, user2Ctx.organizations.size());
        assertNotNull(user2Ctx.projects);
        assertEquals(0, user2Ctx.projects.size());
        assertNotNull(user2Ctx.user);
        assertEquals(this.USER3_EMAIL, user2Ctx.user.email);

        createProject(PROJECT_2, this.USER3_EMAIL, this.USER3_PSW, this.USER1_ORG, peerUri);

        // The user context query for the org admin should return the projects created by them and
        // other users in the org
        UserContext user1Ctx = getUserContext(this.USER1_EMAIL, this.USER1_PSW, peerUri);
        assertNotNull(user1Ctx);
        assertNotNull(user1Ctx.organizations);
        assertEquals(1, user1Ctx.organizations.size());
        assertNotNull(user1Ctx.user);
        assertEquals(this.USER1_EMAIL, user1Ctx.user.email);
        assertProject(user1Ctx, PROJECT_1, PROJECT_2);

        // The user context query for the ordinary users should only return the projects they
        // created
        UserContext user2CtxUpdated = getUserContext(this.USER3_EMAIL, this.USER3_PSW, peerUri);
        assertNotNull(user2CtxUpdated);
        assertNotNull(user2CtxUpdated.organizations);
        assertEquals(1, user2CtxUpdated.organizations.size());
        assertNotNull(user2CtxUpdated.user);
        assertEquals(this.USER3_EMAIL, user2CtxUpdated.user.email);
        assertProject(user2CtxUpdated, PROJECT_2);

    }

    private void assertProject(UserContext actual, String... expectedProjectNames) {
        List<String> expected = new ArrayList<>(Arrays.asList(expectedProjectNames));
        Set<String> orgs = actual.organizations.stream().map(e -> e.documentSelfLink)
                .collect(Collectors.toSet());

        assertNotNull(actual.projects);
        assertEquals(expected.size(), actual.projects.size());
        for (ProjectState p : actual.projects) {
            assertTrue("Unexpected UserContext project", expected.remove(p.name));
            assertFalse("Unexpected UserContext project tenant",
                    Sets.intersection(orgs, p.tenantLinks).isEmpty());
        }

    }

    @Test
    public void testOrgScope() throws Throwable {
        VerificationHost localServiceHost = null;
        try {
            // we cannot use the 3 node cluster used in the other tests as we cannot set
            // a custom auth service when initializing peer hosts in VerificationHost
            localServiceHost = VerificationHost.create(0);
            localServiceHost.setAuthorizationEnabled(true);
            final String finalUser1Email = this.USER1_EMAIL;
            final String finalUser1Org = this.USER1_ORG;
            localServiceHost.setAuthenticationService( new SymphonyBasicAuthenticationService() {
                @Override
                public void handlePost(Operation op) {
                    if (op.hasPragmaDirective(Operation.PRAGMA_DIRECTIVE_VERIFY_TOKEN)) {
                        op.removePragmaDirective(Operation.PRAGMA_DIRECTIVE_VERIFY_TOKEN);
                        Claims.Builder claimsBuilder = new Claims.Builder();
                        claimsBuilder.setSubject(
                                UriUtils.buildUriPath(UserService.FACTORY_LINK,
                                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(finalUser1Email)));
                        Map<String, String> propMap = new HashMap<>();
                        // always log the user in in the context of org1
                        propMap.put(CloudAccountConstants.CSP_ORG_ID, finalUser1Org);
                        claimsBuilder.setProperties(propMap);
                        AuthorizationContext.Builder ab = AuthorizationContext.Builder.create();
                        ab.setClaims(claimsBuilder.getResult());
                        ab.setToken(op.getRequestHeader(REQUEST_AUTH_TOKEN_HEADER));
                        op.setBody(ab.getResult());
                        op.complete();
                        return;
                    }
                    super.handlePost(op);
                }
            });
            localServiceHost.start();
            URI localHostUri = localServiceHost.getUri();
            localServiceHost.setSystemAuthorizationContext();
            PhotonModelServices.startServices(localServiceHost);
            PhotonModelTaskServices.startServices(localServiceHost);
            localServiceHost.resetSystemAuthorizationContext();
            OnBoardingTestUtils.startCommonServices(localServiceHost);
            OnBoardingTestUtils.waitForCommonServicesAvailability(localServiceHost, localHostUri);
            createUserAndOrg(this.USER1_EMAIL, this.USER1_PSW, this.USER1_ORG, localHostUri);
            createProject(PROJECT_1, this.USER1_EMAIL, this.USER1_PSW, this.USER1_ORG, localHostUri);
            createUserAndOrg(this.USER1_EMAIL, this.USER1_PSW, this.USER2_ORG, localHostUri);
            createProject(PROJECT_1, this.USER1_EMAIL, this.USER1_PSW, this.USER2_ORG, localHostUri);
            UserContext userCtx = getUserContext(this.USER1_EMAIL, this.USER1_PSW, localHostUri);
            assertTrue(userCtx.organizations.size() == 1);
            assertTrue(userCtx.projects.size() == 1);
        } finally {
            if (localServiceHost != null) {
                localServiceHost.stop();
            }
        }
    }

    private void createUserAndOrg(String email, String password, String org, URI remoteHostUri) throws Throwable {
        createUser(email, password, remoteHostUri);
        this.host.assumeIdentity(
                UriUtils.buildUriPath(UserService.FACTORY_LINK,
                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(email)), null);

        OrganizationCreationRequest orgData = new OrganizationCreationRequest();
        orgData.orgId = org;
        orgData.organizationName = org + " name";
        orgData.displayName = org + " display name";

        Operation response = this.host.waitForResponse(Operation
                .createPost(UriUtils.buildUri(remoteHostUri, OrganizationCreationService.SELF_LINK))
                .setBody(orgData));
        System.out.println();
    }

    private void createUser(String email, String password, URI remoteHostUri) {
        UserCreationRequest userData = new UserCreationRequest();
        userData.email = email;
        userData.password = password;
        userData.firstName = "firstname";
        userData.lastName = "lastname";
        this.host.waitForResponse(Operation
                .createPost(UriUtils.buildUri(remoteHostUri, UserCreationService.SELF_LINK))
                .setBody(userData));
    }

    private void associateExistingUserWithAnOrg(String orgAdminEmail, String userEmail,
            String orgLink) throws Throwable {
        this.host.assumeIdentity(
                UriUtils.buildUriPath(UserService.FACTORY_LINK,
                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(orgAdminEmail)), null);
        URI peerUri = this.host.getPeerHostUri();
        UserUpdateRequest userData = new UserUpdateRequest();
        userData.email = userEmail;
        userData.isAdmin = false;
        userData.entityLink = orgLink;
        Operation response = this.host.waitForResponse(Operation
                .createPost(UriUtils.buildUri(peerUri, UserUpdateService.SELF_LINK))
                .setBody(userData));
        assertNotNull(response);
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
    }

    private void createProject(String projectName, String email, String password,
            String org, URI remoteHostUri) throws Throwable {
        this.host.assumeIdentity(
                UriUtils.buildUriPath(UserService.FACTORY_LINK,
                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(email)), null);
        OnBoardingTestUtils.setupProject(this.host, remoteHostUri, projectName,
                org, email, password);
    }

    private UserContext getUserContext(String email, String password, URI remoteHostUri) throws Throwable {
        this.host.assumeIdentity(
                UriUtils.buildUriPath(UserService.FACTORY_LINK,
                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(email)), null);

        Operation response = this.host.waitForResponse(Operation
                .createGet(UriUtils.buildUri(remoteHostUri, UserContextQueryService.SELF_LINK)));

        return response.getBody(UserContext.class);
    }

}
