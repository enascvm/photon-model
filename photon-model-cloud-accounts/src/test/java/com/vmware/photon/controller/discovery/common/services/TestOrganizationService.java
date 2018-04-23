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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.EnumSet;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.TestProperty;

public class TestOrganizationService extends BasicReusableHostTestCase {
    public static final int SERVICE_COUNT = 100;
    private URI factoryURI;

    @Before
    public void setUp() throws Throwable {
        this.factoryURI = UriUtils.buildFactoryUri(this.host, OrganizationService.class);
        this.host.startFactory(new OrganizationService());
        this.host.waitForReplicatedFactoryServiceAvailable(this.factoryURI);
    }

    @After
    public void tearDown() throws Throwable {
        // delete all services
        this.host.deleteAllChildServices(this.factoryURI);
    }

    @Test
    public void testFactoryPost() throws Throwable {
        ServiceDocumentQueryResult res = createInstances(SERVICE_COUNT, false);
        assertTrue(res.documentLinks.size() == SERVICE_COUNT);
        assertTrue(res.documentLinks.size() == res.documents.size());

        OrganizationService.OrganizationState initialState = buildInitialState();

        for (Object s : res.documents.values()) {
            OrganizationService.OrganizationState state = Utils.fromJson(s, OrganizationService.OrganizationState.class);
            assertEquals(state.name, initialState.name);
        }
    }

    @Test
    public void testFactoryPostWithoutId() throws Throwable {
        ServiceDocumentQueryResult res = createInstances(SERVICE_COUNT, true);
        assertTrue(res.documentLinks.size() == SERVICE_COUNT);
        assertTrue(res.documentLinks.size() == res.documents.size());
        for (Object s : res.documents.values()) {
            OrganizationService.OrganizationState state = Utils.fromJson(s, OrganizationService.OrganizationState.class);
            assertNotNull(state.name);
        }
    }

    @Test
    public void testPatch() throws Throwable {
        ServiceDocumentQueryResult initialStates = createInstances(SERVICE_COUNT, false);

        OrganizationService.OrganizationState patchBody = new OrganizationService.OrganizationState();
        patchBody.displayName = "orgA new display name";
        patchBody.name = "orgB";
        patchBody.id = UUID.randomUUID().toString();
        doPatch(EnumSet.of(TestProperty.FORCE_REMOTE), SERVICE_COUNT, initialStates, patchBody);

        patchBody.displayName = "orgA newer display name";
        patchBody.name = "orgC";
        doPatch(EnumSet.of(TestProperty.FORCE_REMOTE), SERVICE_COUNT, initialStates, patchBody);

        ServiceDocumentQueryResult afterStates = this.host.getFactoryState(UriUtils
                .buildExpandLinksQueryUri(this.factoryURI));
        for (Object s : afterStates.documents.values()) {
            OrganizationService.OrganizationState state = Utils.fromJson(s, OrganizationService.OrganizationState.class);
            OrganizationService.OrganizationState initialState = Utils.fromJson(
                    initialStates.documents.get(state.documentSelfLink),
                    OrganizationService.OrganizationState.class);
            assertNotEquals(patchBody.name, state.name);
            assertNotEquals(patchBody.id, state.id);
            assertEquals(patchBody.displayName, state.displayName);
            assertEquals(initialState.id, state.id);
            assertEquals(initialState.name, state.name);

        }
    }

    public void doPatch(EnumSet<TestProperty> props, int c,
            ServiceDocumentQueryResult initialStates,
            OrganizationService.OrganizationState patchBody) throws Throwable {
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

    private OrganizationService.OrganizationState buildInitialState() {
        OrganizationService.OrganizationState state = new OrganizationService.OrganizationState();
        state.id = UUID.randomUUID().toString();
        state.name = "orgA";
        state.displayName = "orgA display name";
        return state;
    }

    private ServiceDocumentQueryResult createInstances(int c, boolean nullId) throws Throwable {
        this.host.testStart(c);
        for (int i = 0; i < c; i++) {
            OrganizationService.OrganizationState initialState = buildInitialState();
            if (nullId) {
                initialState.id = null;
            }
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
