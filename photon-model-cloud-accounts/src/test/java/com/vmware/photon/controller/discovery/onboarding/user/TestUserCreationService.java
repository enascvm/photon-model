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

package com.vmware.photon.controller.discovery.onboarding.user;

import static org.junit.Assert.assertEquals;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.discovery.common.PhotonControllerCloudAccountUtils;
import com.vmware.photon.controller.discovery.common.SymphonyCommonTestUtils;
import com.vmware.photon.controller.discovery.common.authn.SymphonyBasicAuthenticationService;
import com.vmware.photon.controller.discovery.common.services.UserService;
import com.vmware.photon.controller.discovery.common.services.UserService.UserState;
import com.vmware.photon.controller.discovery.onboarding.user.UserCreationService.UserCreationRequest;
import com.vmware.photon.controller.discovery.onboarding.user.UserUpdateService.UserUpdateRequest;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class TestUserCreationService {

    private VerificationHost host;
    private int NUMBER_NODES = 3;

    @Before
    public void setUp() throws Throwable {
        this.host = VerificationHost.create(0);
        this.host.setAuthorizationEnabled(true);
        this.host.start();
        this.host.setUpPeerHosts(this.NUMBER_NODES);
        this.host.joinNodesAndVerifyConvergence(this.NUMBER_NODES, this.NUMBER_NODES, true);
        this.host.setNodeGroupQuorum(this.NUMBER_NODES);
        List<String> factories = new ArrayList<String>();
        for (VerificationHost h : this.host.getInProcessHostMap().values()) {
            h.setSystemAuthorizationContext();
            h.addPrivilegedService(SymphonyBasicAuthenticationService.class);
            h.startService(new SymphonyBasicAuthenticationService());
            h.waitForServiceAvailable(SymphonyBasicAuthenticationService.SELF_LINK);
            h.addPrivilegedService(UserCreationService.class);
            h.startService(new UserCreationService());
            h.waitForServiceAvailable(UserCreationService.SELF_LINK);
            h.addPrivilegedService(UserService.class);
            h.startFactory(new UserService());
            h.resetSystemAuthorizationContext();
        }
        // wait for all the factories this test relies on
        factories.add(AuthCredentialsService.FACTORY_LINK);
        factories.add(UserService.FACTORY_LINK);
        SymphonyCommonTestUtils.waitForFactoryAvailability(
                this.host, this.host.getPeerHostUri(), factories);
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

    public void testUserOnboarding(String password) throws Throwable {
        URI peerUri = this.host.getPeerHostUri();

        // create a user in the system
        UserCreationRequest userData = new UserCreationRequest();
        userData.email = "foo@bar.com";
        userData.password = password;
        this.host.sendAndWaitExpectFailure(Operation
                .createPost(UriUtils.buildUri(peerUri, UserCreationService.SELF_LINK))
                .setBody(userData));

        userData.firstName = "foo";
        userData.lastName = "bar";
        this.host.sendAndWaitExpectSuccess(Operation
                .createPost(UriUtils.buildUri(peerUri, UserCreationService.SELF_LINK))
                .setBody(userData));

        // send a PATCH as an unauthenticated user. The operation should fail
        UserUpdateRequest userPatchData = new UserUpdateRequest();
        userPatchData.email = userData.email;
        this.host.sendAndWaitExpectFailure(Operation
                .createPatch(UriUtils.buildUri(peerUri, UserCreationService.SELF_LINK))
                .setBody(userData));

        // verify in system context as all these resources are owned by the system
        this.host.setSystemAuthorizationContext();
        ServiceDocumentQueryResult res = SymphonyCommonTestUtils.queryDocuments(this.host, peerUri,
                Utils.buildKind(UserService.UserState.class), 1);

        String emailHash = PhotonControllerCloudAccountUtils.computeHashWithSHA256(userData.email);
        UserState user =
                Utils.fromJson(res.documents.get(UriUtils.buildUriPath(
                        UserService.FACTORY_LINK, emailHash)), UserState.class);
        assertEquals(user.email, userData.email);
        assertEquals(user.customProperties.get(UserState.FIRST_NAME_PROPERTY_NAME), userData.firstName);
        assertEquals(user.customProperties.get(UserState.LAST_NAME_PROPERTY_NAME), userData.lastName);
        assertEquals(user.documentSelfLink,
                UriUtils.buildUriPath(UserService.FACTORY_LINK, emailHash));

        res = SymphonyCommonTestUtils.queryDocuments(this.host, peerUri,
                Utils.buildKind(AuthCredentialsServiceState.class), 1);

        AuthCredentialsServiceState authState =
                Utils.fromJson(res.documents.get(UriUtils.buildUriPath(
                                AuthCredentialsService.FACTORY_LINK, emailHash)),
                        AuthCredentialsServiceState.class);
        assertEquals(authState.userEmail, userData.email);
        assertEquals(authState.privateKey, userData.password);
        assertEquals(authState.documentSelfLink,
                UriUtils.buildUriPath(AuthCredentialsService.FACTORY_LINK, emailHash));

        this.host.resetSystemAuthorizationContext();

        // repost using the same creds, the op should fail
        this.host.sendAndWait(Operation
                .createPost(UriUtils.buildUri(peerUri, UserCreationService.SELF_LINK))
                .setBody(userData)
                .setCompletion((op, ex) -> {
                    if (op.getStatusCode() == Operation.STATUS_CODE_CONFLICT) {
                        this.host.completeIteration();
                        return;
                    }
                    this.host.failIteration(new IllegalStateException("Invalid error code received"));
                }));
    }

    @Test
    public void testUserOnboardingWithNonNullPassword() throws Throwable {
        testUserOnboarding("passwordforfoo");
    }

    @Test
    public void testUserOnboardingWithNullPassword() throws Throwable {
        // Null password is acceptable only when symphony is registered with CSP
        String cspUri = System.getProperty(UserCreationService.CSP_URI);
        System.setProperty(UserCreationService.CSP_URI, "https://example-csp-uri.com");
        testUserOnboarding(null);
        if (cspUri != null) {
            System.setProperty(UserCreationService.CSP_URI, cspUri);
        } else {
            System.clearProperty(UserCreationService.CSP_URI);
        }
    }

    @Test
    public void testRollBackLogic() throws Throwable {
        URI peerUri = this.host.getPeerHostUri();

        Operation shutDownService = this.host.waitForResponse(Operation
                .createDelete(UriUtils.buildUri(peerUri, AuthCredentialsService.FACTORY_LINK)));
        assertEquals(Operation.STATUS_CODE_OK, shutDownService.getStatusCode());

            // create a user in the system
        UserCreationRequest userData = new UserCreationRequest();
        userData.firstName = "foo";
        userData.lastName = "bar";

        Operation response = this.host.waitForResponse(Operation
                .createPost(UriUtils.buildUri(peerUri, UserCreationService.SELF_LINK))
                .setBody(userData));

        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());

        // Due to authCredentialService is not available, so it fails during create user. User
        // document already created should be deleted.
        this.host.setSystemAuthorizationContext();
        ServiceDocumentQueryResult res = SymphonyCommonTestUtils.queryDocuments(this.host, peerUri,
                Utils.buildKind(UserService.UserState.class), 0);
    }

}
