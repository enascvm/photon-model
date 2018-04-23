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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.discovery.common.services.UserService.UserState;
import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.AuthorizationContext;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.TestProperty;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.ExampleService;
import com.vmware.xenon.services.common.MinimalTestService;
import com.vmware.xenon.services.common.UserGroupService;

public class TestUserService extends BasicReusableHostTestCase {
    public static final int SERVICE_COUNT = 100;
    private URI factoryURI;

    @Before
    public void setUp() throws Throwable {
        this.factoryURI = UriUtils.buildFactoryUri(this.host, UserService.class);
        this.host.addPrivilegedService(UserService.class);
        this.host.startFactory(new UserService());
        this.host.waitForReplicatedFactoryServiceAvailable(this.factoryURI);
    }

    @After
    public void tearDown() throws Throwable {
        // delete all services
        this.host.deleteAllChildServices(this.factoryURI);
    }

    @Test
    public void testFactoryPost() throws Throwable {
        ServiceDocumentQueryResult res = createInstances(SERVICE_COUNT);
        assertTrue(res.documentLinks.size() == SERVICE_COUNT);
        assertTrue(res.documentLinks.size() == res.documents.size());

        UserService.UserState initialState = buildInitialState();

        for (Object s : res.documents.values()) {
            UserService.UserState state = Utils.fromJson(s, UserService.UserState.class);
            assertEquals(state.email, initialState.email);
            assertEquals(state.customProperties.get(UserState.FIRST_NAME_PROPERTY_NAME),
                    initialState.customProperties.get(UserState.FIRST_NAME_PROPERTY_NAME));
            assertEquals(state.customProperties.get(UserState.LAST_NAME_PROPERTY_NAME),
                    initialState.customProperties.get(UserState.LAST_NAME_PROPERTY_NAME));
        }
    }

    @Test
    public void testServiceDocumentProperties() {
        ServiceDocumentDescription.Builder builder = ServiceDocumentDescription.Builder.create();
        ServiceDocumentDescription desc = builder.buildDescription(UserState.class);
        assertEquals(1024 * 1024 * 10, desc.serializedStateSizeLimit);
        assertEquals(10000, desc.versionRetentionLimit);
    }

    @Test
    public void testPatch() throws Throwable {
        ServiceDocumentQueryResult initialStates = createInstances(SERVICE_COUNT);

        UserState patchBody = new UserState();
        patchBody.email = "bar@bar.com";
        patchBody.customProperties = new HashMap<String, String>();
        patchBody.customProperties.put(UserState.FIRST_NAME_PROPERTY_NAME, "bar");
        patchBody.customProperties.put(UserState.LAST_NAME_PROPERTY_NAME, "bar");
        patchBody.userGroupLinks = new HashSet<String>();
        patchBody.userGroupLinks.add("link1");
        doPatch(EnumSet.of(TestProperty.FORCE_REMOTE), SERVICE_COUNT, initialStates, patchBody);

        ServiceDocumentQueryResult afterStates = this.host.getFactoryState(UriUtils
                .buildExpandLinksQueryUri(this.factoryURI));
        for (Object s : afterStates.documents.values()) {
            UserState state = Utils.fromJson(s, UserState.class);
            assertEquals(patchBody.email, state.email);
            assertEquals(patchBody.customProperties.get(UserState.FIRST_NAME_PROPERTY_NAME),
                    state.customProperties.get(UserState.FIRST_NAME_PROPERTY_NAME));
            assertEquals(patchBody.customProperties.get(UserState.LAST_NAME_PROPERTY_NAME),
                    state.customProperties.get(UserState.LAST_NAME_PROPERTY_NAME));
            assertEquals(patchBody.userGroupLinks,
                    state.userGroupLinks);
        }
    }

    @Test
    public void testInvalidEmail() throws Throwable {
        UserState startBody = new UserState();
        startBody.email = "bar";
        Operation startPost = Operation.createPost(this.factoryURI)
                    .setBody(startBody)
                    .setCompletion(this.host.getCompletion());
        this.host.sendAndWaitExpectFailure(startPost);
    }

    @Test
    public void testDuplicatePost() throws Throwable {
        UserState startBody = new UserState();
        startBody.email = "bar@bar.com";
        startBody.documentSelfLink = UUID.randomUUID().toString();
        Operation startPost = Operation.createPost(this.factoryURI)
                    .setBody(startBody)
                    .setCompletion(this.host.getCompletion());
        this.host.sendAndWaitExpectSuccess(startPost);
        this.host.sendAndWaitExpectFailure(startPost);
    }

    @Test
    public void testAuthorization() throws Throwable {
        VerificationHost authHost =  VerificationHost.create(0);
        authHost.setAuthorizationEnabled(true);
        authHost.start();
        authHost.setSystemAuthorizationContext();
        URI userServiceFactoryUri = UriUtils.buildFactoryUri(authHost, UserService.class);
        authHost.addPrivilegedService(UserService.class);
        authHost.startFactory(new UserService());
        authHost.waitForReplicatedFactoryServiceAvailable(userServiceFactoryUri);
        UserService.UserState initialState = buildInitialState();
        initialState.documentSelfLink = UUID.randomUUID().toString();
        String userServiceLink = UriUtils.buildUriPath(UserService.FACTORY_LINK, initialState.documentSelfLink);
        Operation startPost = Operation.createPost(userServiceFactoryUri)
                .setBody(initialState)
                .setCompletion(authHost.getCompletion());
        authHost.sendAndWaitExpectSuccess(startPost);
        // spin up a privileged service to query for auth context
        MinimalTestService s = new MinimalTestService();
        authHost.addPrivilegedService(MinimalTestService.class);
        authHost.startServiceAndWait(s, UUID.randomUUID().toString(), null);
        authHost.resetSystemAuthorizationContext();
        // patch the service and check to see authz cache has been cleared
        assertNotNull(assumeIdentityAndGetContext(authHost, userServiceLink, s, true));
        UserState patchBody = new UserState();
        patchBody.email = "bar@bar.com";
        authHost.setSystemAuthorizationContext();
        Operation patchOp = Operation.createPatch(UriUtils.buildUri(authHost,userServiceLink))
                .setBody(patchBody)
                .setCompletion(authHost.getCompletion());
        authHost.sendAndWaitExpectSuccess(patchOp);
        authHost.resetSystemAuthorizationContext();
        assertNull(assumeIdentityAndGetContext(authHost, userServiceLink, s, false));
        assertNotNull(assumeIdentityAndGetContext(authHost, userServiceLink, s, true));
        authHost.setSystemAuthorizationContext();
        // delete the service and check to see authz cache has been cleared
        Operation deleteOp = Operation.createDelete(UriUtils.buildUri(authHost,userServiceLink))
                .setCompletion(authHost.getCompletion());
        authHost.sendAndWaitExpectSuccess(deleteOp);
        authHost.resetSystemAuthorizationContext();
        assertNull(assumeIdentityAndGetContext(authHost, userServiceLink, s, false));
        authHost.tearDown();
    }

    @Test
    public void testExtractOrgLinks() throws Throwable {
        UserState userState = new UserState();
        Set<String> orgLinks = UserService.extractUsersOrgLinks(userState);
        Assert.assertEquals(0, orgLinks.size());

        userState.userGroupLinks = new HashSet<String>();

        orgLinks = UserService.extractUsersOrgLinks(userState);
        Assert.assertEquals(0, orgLinks.size());

        int validGroupLinksSize = 10;
        for (int i = 0; i < validGroupLinksSize / 2; i++) {
            userState.userGroupLinks.add(UserGroupService.FACTORY_LINK + "/link" + i);
        }

        for (int i = validGroupLinksSize / 2; i < validGroupLinksSize; i++) {
            userState.userGroupLinks.add(UserGroupService.FACTORY_LINK + "/link" + i + "-extra");
        }

        userState.userGroupLinks.add("invalid");

        orgLinks = UserService.extractUsersOrgLinks(userState);
        Assert.assertEquals(validGroupLinksSize, orgLinks.size());
        for (int i = 0; i < validGroupLinksSize; i++) {
            Assert.assertTrue(orgLinks.contains(OrganizationService.FACTORY_LINK + "/link" + i));
        }
    }

    private AuthorizationContext assumeIdentityAndGetContext(VerificationHost host, String userLink,
            Service privilegedService, boolean populateCache) throws Throwable {
        AuthorizationContext authContext = host.assumeIdentity(userLink);
        if (populateCache) {
            host.sendAndWaitExpectSuccess(
                    Operation.createGet(UriUtils.buildUri(host, ExampleService.FACTORY_LINK)));
        }
        return host.getAuthorizationContext(privilegedService, authContext.getToken());
    }

    public void doPatch(EnumSet<TestProperty> props, int c,
            ServiceDocumentQueryResult initialStates,
            UserState patchBody) throws Throwable {
        this.host.testStart(c);
        for (String link : initialStates.documentLinks) {
            Operation patch = Operation.createPatch(UriUtils.buildUri(this.host, link))
                    .setBody(patchBody)
                    .setCompletion(this.host.getCompletion());
            if (props.contains(TestProperty.FORCE_REMOTE)) {
                patch.forceRemote();
            }
            this.host.send(patch);
        }
        this.host.testWait();
    }

    private UserService.UserState buildInitialState() {
        UserService.UserState state = new UserService.UserState();
        state.email = "foo@bar.com";
        state.customProperties = new HashMap<String, String>();
        state.customProperties.put(UserState.FIRST_NAME_PROPERTY_NAME, "foo");
        state.customProperties.put(UserState.LAST_NAME_PROPERTY_NAME, "bar");
        return state;
    }

    private ServiceDocumentQueryResult createInstances(int c) throws Throwable {
        this.host.testStart(c);
        for (int i = 0; i < c; i++) {
            UserService.UserState initialState = buildInitialState();
            Operation startPost = Operation.createPost(this.factoryURI)
                    .setBody(initialState)
                    .setCompletion(this.host.getCompletion());
            this.host.send(startPost);
        }
        this.host.testWait();

        ServiceDocumentQueryResult res = this.host.getFactoryState(UriUtils
                .buildExpandLinksQueryUri(this.factoryURI));
        return res;
    }

}
