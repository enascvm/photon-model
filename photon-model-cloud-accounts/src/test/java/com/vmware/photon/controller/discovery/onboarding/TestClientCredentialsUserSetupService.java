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

package com.vmware.photon.controller.discovery.onboarding;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import static com.vmware.photon.controller.discovery.onboarding.ClientCredentialsUserSetupService.buildResourceGroupNameFromClientId;
import static com.vmware.photon.controller.discovery.onboarding.ClientCredentialsUserSetupService.buildUserGroupNameFromClientId;
import static com.vmware.photon.controller.discovery.onboarding.OnboardingUtils.buildUserIdFromClientId;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;

import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.discovery.common.SymphonyCommonTestUtils;
import com.vmware.photon.controller.discovery.onboarding.ClientCredentialsUserSetupService.ClientCredentialsUserSetupRequest;
import com.vmware.xenon.common.BasicTestCase;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.ResourceGroupService;
import com.vmware.xenon.services.common.RoleService.RoleState;
import com.vmware.xenon.services.common.UserGroupService;
import com.vmware.xenon.services.common.UserGroupService.UserGroupState;
import com.vmware.xenon.services.common.UserService.UserState;

/**
 * Test for client credentials user setup.
 */
public class TestClientCredentialsUserSetupService extends BasicTestCase {
    public static final String COST_INSIGHT_CLIENT_ID = "cost_insight";
    public static final String NETWORK_INSIGHT_CLIENT_ID = "network-insight";

    @Before
    public void setUp() throws Throwable {
        OnBoardingTestUtils.startCommonServices(this.host);
        OnBoardingTestUtils.waitForCommonServicesAvailability(this.host, this.host.getUri());
        this.host.setSystemAuthorizationContext();
        this.host.addPrivilegedService(ClientCredentialsUserSetupService.class);
        this.host.startService(new ClientCredentialsUserSetupService());
        this.host.waitForServiceAvailable(ClientCredentialsUserSetupService.SELF_LINK);
        this.host.resetSystemAuthorizationContext();
    }

    @Test
    public void testClientCredentialsUserSetup() throws Throwable {
        ClientCredentialsUserSetupRequest req = new ClientCredentialsUserSetupRequest();
        req.clientIds = new HashSet<>(Arrays.asList(COST_INSIGHT_CLIENT_ID));

        this.host.sendAndWaitExpectSuccess(
                Operation.createPost(this.host, ClientCredentialsUserSetupService.SELF_LINK)
                        .setBody(req));

        // query artifacts to ensure they have been created as expected
        ServiceDocumentQueryResult res = SymphonyCommonTestUtils.queryDocuments(this.host,
                this.host.getUri(),
                Utils.buildKind(UserState.class), 1);
        UserState userState = Utils.fromJson(res.documents.values().iterator().next(),
                UserState.class);
        assertEquals(userState.email, buildUserIdFromClientId(COST_INSIGHT_CLIENT_ID));
        res = SymphonyCommonTestUtils.queryDocuments(this.host, this.host.getUri(),
                Utils.buildKind(AuthCredentialsServiceState.class), 1);
        AuthCredentialsServiceState authCredentials = Utils.fromJson(
                res.documents.values().iterator().next(),
                AuthCredentialsServiceState.class);
        assertEquals(authCredentials.userEmail, buildUserIdFromClientId(COST_INSIGHT_CLIENT_ID));
        res = SymphonyCommonTestUtils
                .queryDocuments(this.host, this.host.getUri(),
                        Utils.buildKind(RoleState.class), 1);
        RoleState roleState = Utils.fromJson(res.documents.values().iterator().next(),
                RoleState.class);
        for (Action action : EnumSet.allOf(Action.class)) {
            assertTrue(roleState.verbs.contains(action));
        }
        assertEquals(roleState.userGroupLink,
                UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                        buildUserGroupNameFromClientId(COST_INSIGHT_CLIENT_ID)));
        assertEquals(roleState.resourceGroupLink,
                UriUtils.buildUriPath(ResourceGroupService.FACTORY_LINK,
                        buildResourceGroupNameFromClientId(COST_INSIGHT_CLIENT_ID)));
        res = SymphonyCommonTestUtils.queryDocuments(this.host, this.host.getUri(),
                Utils.buildKind(UserGroupState.class), 1);
        UserGroupState userGroupState = Utils.fromJson(res.documents.values().iterator().next(),
                UserGroupState.class);
        assertTrue(userGroupState.query.booleanClauses.size() == 1);
        assertEquals(QuerySpecification.buildCollectionItemName(UserState.FIELD_NAME_EMAIL),
                userGroupState.query.booleanClauses.get(0).term.propertyName);
        assertEquals(buildUserIdFromClientId(COST_INSIGHT_CLIENT_ID),
                userGroupState.query.booleanClauses.get(0).term.matchValue);
    }

    @Test
    public void testIdempotentClientCredentialsUserSetup() throws Throwable {
        ClientCredentialsUserSetupRequest req = new ClientCredentialsUserSetupRequest();
        req.clientIds = new HashSet<>(Arrays.asList(COST_INSIGHT_CLIENT_ID));

        this.host.sendAndWaitExpectSuccess(
                Operation.createPost(this.host, ClientCredentialsUserSetupService.SELF_LINK)
                        .setBody(req));

        this.host.sendAndWaitExpectSuccess(
                Operation.createPost(this.host, ClientCredentialsUserSetupService.SELF_LINK)
                        .setBody(req));

        // query artifacts to ensure they have been created as expected
        ServiceDocumentQueryResult res = SymphonyCommonTestUtils.queryDocuments(this.host,
                this.host.getUri(),
                Utils.buildKind(UserState.class), 1);
        UserState userState = Utils.fromJson(res.documents.values().iterator().next(),
                UserState.class);
        assertEquals(userState.email, buildUserIdFromClientId(COST_INSIGHT_CLIENT_ID));
        res = SymphonyCommonTestUtils.queryDocuments(this.host, this.host.getUri(),
                Utils.buildKind(AuthCredentialsServiceState.class), 1);
        AuthCredentialsServiceState authCredentials = Utils.fromJson(
                res.documents.values().iterator().next(),
                AuthCredentialsServiceState.class);
        assertEquals(authCredentials.userEmail, buildUserIdFromClientId(COST_INSIGHT_CLIENT_ID));
        res = SymphonyCommonTestUtils
                .queryDocuments(this.host, this.host.getUri(),
                        Utils.buildKind(RoleState.class), 1);
        RoleState roleState = Utils.fromJson(res.documents.values().iterator().next(),
                RoleState.class);
        for (Action action : EnumSet.allOf(Action.class)) {
            assertTrue(roleState.verbs.contains(action));
        }
        assertEquals(roleState.userGroupLink,
                UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                        buildUserGroupNameFromClientId(COST_INSIGHT_CLIENT_ID)));
        assertEquals(roleState.resourceGroupLink,
                UriUtils.buildUriPath(ResourceGroupService.FACTORY_LINK,
                        buildResourceGroupNameFromClientId(COST_INSIGHT_CLIENT_ID)));
        res = SymphonyCommonTestUtils.queryDocuments(this.host, this.host.getUri(),
                Utils.buildKind(UserGroupState.class), 1);
        UserGroupState userGroupState = Utils.fromJson(res.documents.values().iterator().next(),
                UserGroupState.class);
        assertTrue(userGroupState.query.booleanClauses.size() == 1);
        assertEquals(QuerySpecification.buildCollectionItemName(UserState.FIELD_NAME_EMAIL),
                userGroupState.query.booleanClauses.get(0).term.propertyName);
        assertEquals(buildUserIdFromClientId(COST_INSIGHT_CLIENT_ID),
                userGroupState.query.booleanClauses.get(0).term.matchValue);
    }

    @Test
    public void testMultipleClientCredentialsUserSetup() throws Throwable {
        ClientCredentialsUserSetupRequest req = new ClientCredentialsUserSetupRequest();
        req.clientIds = new HashSet<>(
                Arrays.asList(COST_INSIGHT_CLIENT_ID, NETWORK_INSIGHT_CLIENT_ID));

        this.host.sendAndWaitExpectSuccess(
                Operation.createPost(this.host, ClientCredentialsUserSetupService.SELF_LINK)
                        .setBody(req));

        // query artifacts to ensure they have been created as expected
        SymphonyCommonTestUtils
                .queryDocuments(this.host, this.host.getUri(), Utils.buildKind(UserState.class), 2);

        SymphonyCommonTestUtils.queryDocuments(this.host, this.host.getUri(),
                Utils.buildKind(AuthCredentialsServiceState.class), 2);

        SymphonyCommonTestUtils
                .queryDocuments(this.host, this.host.getUri(), Utils.buildKind(RoleState.class), 2);

        SymphonyCommonTestUtils.queryDocuments(this.host, this.host.getUri(),
                Utils.buildKind(UserGroupState.class), 2);
    }

    @Test
    public void testBadRequest() throws Throwable {
        this.host.sendAndWaitExpectFailure(
                Operation.createPost(this.host, ClientCredentialsUserSetupService.SELF_LINK));

        ClientCredentialsUserSetupRequest req = new ClientCredentialsUserSetupRequest();
        this.host.sendAndWaitExpectFailure(
                Operation.createPost(this.host, ClientCredentialsUserSetupService.SELF_LINK)
                        .setBody(req));

        req.clientIds = Collections.emptySet();
        this.host.sendAndWaitExpectFailure(
                Operation.createPost(this.host, ClientCredentialsUserSetupService.SELF_LINK)
                        .setBody(req));
    }
}
