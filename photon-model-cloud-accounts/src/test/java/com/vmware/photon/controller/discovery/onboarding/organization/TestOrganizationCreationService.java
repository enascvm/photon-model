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

package com.vmware.photon.controller.discovery.onboarding.organization;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import static com.vmware.photon.controller.discovery.common.services.UserService.FACTORY_LINK;

import java.net.URI;
import java.util.Iterator;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.discovery.common.PhotonControllerCloudAccountUtils;
import com.vmware.photon.controller.discovery.common.SymphonyCommonTestUtils;
import com.vmware.photon.controller.discovery.common.services.OrganizationService;
import com.vmware.photon.controller.discovery.common.services.OrganizationService.OrganizationState;
import com.vmware.photon.controller.discovery.common.services.UserService.UserState;
import com.vmware.photon.controller.discovery.onboarding.OnBoardingTestUtils;
import com.vmware.photon.controller.discovery.onboarding.organization.OrganizationCreationService.OrganizationCreationRequest;
import com.vmware.photon.controller.discovery.onboarding.user.UserCreationService;
import com.vmware.photon.controller.discovery.onboarding.user.UserCreationService.UserCreationRequest;
import com.vmware.photon.controller.discovery.onboarding.user.UserUpdateService;
import com.vmware.photon.controller.discovery.onboarding.user.UserUpdateService.UserUpdateRequest;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.ResourceGroupService;
import com.vmware.xenon.services.common.RoleService;
import com.vmware.xenon.services.common.RoleService.RoleState;
import com.vmware.xenon.services.common.UserGroupService;

public class TestOrganizationCreationService {

    private static final String SEPERATOR = "-";
    private static final String ADMIN_SUFFIX = "admin";
    private static final String USER_SUFFIX = "user";

    private VerificationHost host;
    private int NUMBER_NODES = 3;

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
    public void testOrganizationOnboarding() throws Throwable {

        URI peerUri = this.host.getPeerHostUri();

        // create 2 users in the system
        UserCreationRequest userData = OnBoardingTestUtils.createUserData("user-1@org-1.com", "passwordforuser-2");;
        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, UserCreationService.SELF_LINK))
                .setBody(userData));

        UserCreationRequest userData2 = OnBoardingTestUtils.createUserData("user-2@org-1.com", "passwordforuser-2");
        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, UserCreationService.SELF_LINK))
                .setBody(userData2));

        // create an org
        OrganizationCreationRequest orgData = new OrganizationCreationRequest();
        orgData.orgId = "org-1";
        orgData.organizationName = "org-1";
        orgData.displayName = "org-1 display name";

        // post to org provisioning service with no auth context
        this.host.sendAndWaitExpectFailure(Operation
                .createPost(UriUtils.buildUri(peerUri, OrganizationCreationService.SELF_LINK))
                .setBody(orgData));

        this.host.assumeIdentity(UriUtils.buildUriPath(FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(userData.email)), null);

        // post to tenant provisioning service and make sure the right artifacts are created
        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, OrganizationCreationService.SELF_LINK))
                .setBody(orgData));

        // verify in system context as all these resources are owned by the system
        this.host.setSystemAuthorizationContext();
        ServiceDocumentQueryResult res = SymphonyCommonTestUtils.queryDocuments(this.host, peerUri,
                Utils.buildKind(OrganizationState.class), 1);

        OrganizationState orgState =
                Utils.fromJson(res.documents.values().iterator().next(), OrganizationState.class);
        assertEquals(orgState.name, orgData.organizationName);
        String orgLink = UriUtils.getLastPathSegment(orgState.documentSelfLink);

        res = SymphonyCommonTestUtils.queryDocuments(this.host, peerUri,
                Utils.buildKind(UserGroupService.UserGroupState.class), 2);

        res = SymphonyCommonTestUtils.queryDocuments(this.host, peerUri,
                Utils.buildKind(ResourceGroupService.ResourceGroupState.class), 4);

        res = SymphonyCommonTestUtils.queryDocuments(this.host, peerUri,
                Utils.buildKind(RoleState.class), 4);

        res = SymphonyCommonTestUtils.queryDocuments(this.host, peerUri,
                Utils.buildKind(UserState.class), 2);

        String userEmailHash = PhotonControllerCloudAccountUtils.computeHashWithSHA256(userData.email);
        UserState userState =
                Utils.fromJson(res.documents.get(UriUtils.buildUriPath(
                        FACTORY_LINK, userEmailHash)), UserState.class);
        Iterator<String> groupIter = userState.userGroupLinks.iterator();
        while (groupIter.hasNext()) {
            String userGroupLink = groupIter.next();
            String adminUserGRoup = UriUtils
                    .buildUriPath(UserGroupService.FACTORY_LINK,
                            (new StringBuffer(orgLink).append(SEPERATOR)
                                    .append(ADMIN_SUFFIX)).toString());
            String nonAdminUserGRoup = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                    (new StringBuffer(orgLink).append(SEPERATOR).append(USER_SUFFIX))
                            .toString());
            assertTrue(userGroupLink.equals(adminUserGRoup)
                    || userGroupLink.equals(nonAdminUserGRoup));
        }
        this.host.resetSystemAuthorizationContext();

        // add user-2 to the tenant as a non-admin
        this.host.assumeIdentity(UriUtils.buildUriPath(FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(userData.email)), null);
        String user2EmailHash = PhotonControllerCloudAccountUtils.computeHashWithSHA256(userData2.email);
        UserUpdateRequest userPatchData = new UserUpdateRequest();
        userPatchData.email = userData2.email;
        userPatchData.isAdmin = false;
        userPatchData.entityLink = UriUtils.buildUriPath(OrganizationService.FACTORY_LINK,
                orgLink);
        this.host.setTimeoutSeconds(3600);
        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, UserUpdateService.SELF_LINK))
                .setBody(userPatchData));

        this.host.setSystemAuthorizationContext();
        res =  SymphonyCommonTestUtils.queryDocuments(this.host, peerUri,
                Utils.buildKind(UserState.class), 2);

        UserState user =
                Utils.fromJson(res.documents.get(UriUtils.buildUriPath(
                        FACTORY_LINK, user2EmailHash)), UserState.class);
        assertTrue(user.userGroupLinks.size() == 1);
        assertTrue(user.userGroupLinks.contains(UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                new StringBuffer(orgLink)
                        .append(SEPERATOR).append(USER_SUFFIX).toString())));
        this.host.resetSystemAuthorizationContext();

        // next add the user as an admin user
        this.host.assumeIdentity(UriUtils.buildUriPath(FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(userData.email)), null);
        userPatchData = new UserUpdateRequest();
        userPatchData.email = userData2.email;
        userPatchData.isAdmin = true;
        userPatchData.entityLink = UriUtils.buildUriPath(OrganizationService.FACTORY_LINK,
                orgLink);
        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, UserUpdateService.SELF_LINK))
                .setBody(userPatchData));

        this.host.setSystemAuthorizationContext();
        res =  SymphonyCommonTestUtils.queryDocuments(this.host, peerUri,
                Utils.buildKind(UserState.class), 2);
        user = Utils.fromJson(res.documents.get(UriUtils.buildUriPath(
                FACTORY_LINK, user2EmailHash)), UserState.class);
        assertTrue(user.userGroupLinks.size() == 2);
        assertTrue(user.userGroupLinks.contains(UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                new StringBuffer(orgLink)
                        .append(SEPERATOR).append(ADMIN_SUFFIX).toString())));
        assertTrue(user.userGroupLinks.contains(UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                new StringBuffer(orgLink)
                        .append(SEPERATOR).append(USER_SUFFIX).toString())));
        this.host.resetSystemAuthorizationContext();

        // create org-2 as the system user; the logged in user does not have permissions to
        // access this org
        this.host.setSystemAuthorizationContext();
        OrganizationState org2State = new OrganizationState();
        org2State.name = "org-2";
        org2State.displayName = "org-2 display name";
        String tenant2Hash = Utils.computeHash(org2State.name);
        org2State.documentSelfLink = tenant2Hash;
        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, OrganizationService.FACTORY_LINK))
                .setBody(org2State));
        this.host.resetSystemAuthorizationContext();

        // try to add user-2 to the new org, the operation should fail as the logged in user is
        // not an admin
        this.host.assumeIdentity(UriUtils.buildUriPath(FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(userData.email)), null);
        UserUpdateRequest userPatchDataForTenant2 = new UserUpdateRequest();
        userPatchDataForTenant2.email = userData2.email;
        userPatchDataForTenant2.isAdmin = false;
        userPatchDataForTenant2.entityLink = UriUtils.buildUriPath(OrganizationService.FACTORY_LINK,
                tenant2Hash);
        this.host.sendAndWaitExpectFailure(Operation
                .createPost(UriUtils.buildUri(peerUri, UserUpdateService.SELF_LINK))
                .setBody(userPatchDataForTenant2));
    }

    @Test
    public void testRollbackLogic() throws Throwable {
        URI peerUri = this.host.getPeerHostUri();

        // create 2 users in the system
        UserCreationRequest userData = OnBoardingTestUtils.createUserData("user-1@org-1.com", "passwordforuser-2");;
        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, UserCreationService.SELF_LINK))
                .setBody(userData));

        // shut down RoleService to let `createAuth` step in OrganizationCreationService fail to
        // invoke rollback logic.
        Operation shutDownService = this.host.waitForResponse(Operation
                .createDelete(UriUtils.buildUri(peerUri, RoleService.FACTORY_LINK)));
        assertEquals(Operation.STATUS_CODE_OK, shutDownService.getStatusCode());

        // create an org
        OrganizationCreationRequest orgData = new OrganizationCreationRequest();
        orgData.orgId = "org-1";
        orgData.organizationName = "org-1";
        orgData.displayName = "org-1 display name";

        this.host.assumeIdentity(UriUtils.buildUriPath(FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(userData.email)), null);

        // post to tenant provisioning service and make sure the right artifacts are created
        Operation response = this.host.waitForResponse(Operation
                .createPost(UriUtils.buildUri(peerUri, OrganizationCreationService.SELF_LINK))
                .setBody(orgData));
        assertEquals(Operation.STATUS_CODE_INTERNAL_ERROR, response.getStatusCode());

        // verify in system context as all these resources are in the system
        this.host.setSystemAuthorizationContext();
        SymphonyCommonTestUtils.queryDocuments(this.host, peerUri,
                Utils.buildKind(OrganizationState.class), 0);
        SymphonyCommonTestUtils.queryDocuments(this.host, peerUri,
                Utils.buildKind(UserState.class), 1);
    }
}
