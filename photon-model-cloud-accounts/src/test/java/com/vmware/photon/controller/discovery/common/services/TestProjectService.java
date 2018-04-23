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
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.net.URI;
import java.util.EnumSet;
import java.util.HashMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.discovery.common.UtilizationThreshold;
import com.vmware.photon.controller.discovery.common.services.ProjectService.ProjectState;
import com.vmware.photon.controller.discovery.common.services.ProjectService.ProjectStatus;
import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.TestProperty;

public class TestProjectService extends BasicReusableHostTestCase {
    public static final int SERVICE_COUNT = 100;
    public static final String METRIC = "CPU";
    private URI factoryURI;

    @Before
    public void setUp() throws Throwable {
        this.factoryURI = UriUtils.buildFactoryUri(this.host, ProjectService.class);
        this.host.startFactory(new ProjectService());
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

        ProjectService.ProjectState initialState = buildInitialState();

        for (Object s : res.documents.values()) {
            ProjectService.ProjectState state = Utils.fromJson(s, ProjectService.ProjectState.class);
            assertEquals(state.name, initialState.name);
            assertEquals(state.budget, initialState.budget);
            assertEquals(state.description, initialState.description);
        }
    }

    @Test
    public void testFactoryPostWithThreshold() throws Throwable {
        ProjectState initialState = buildInitialStateWithUtilizationTheshold();

        Operation startPost = Operation.createPost(this.factoryURI)
                .setBody(initialState);
        Operation response = this.host.waitForResponse(startPost);

        ProjectState actual = getOne(response.getBody(ProjectState.class).documentSelfLink);
        assertEquals(initialState.name, actual.name);
        assertEquals(initialState.budget, actual.budget);
        assertEquals(initialState.description, actual.description);
        assertEquals(initialState.status, actual.status);
        assertEquals(initialState.utilizationThresholds.size(),
                actual.utilizationThresholds.size());
        assertEquals(initialState.utilizationThresholds.get(METRIC).overLimit,
                actual.utilizationThresholds.get(METRIC).overLimit);
        assertEquals(initialState.utilizationThresholds.get(METRIC).underLimit,
                actual.utilizationThresholds.get(METRIC).underLimit);
        assertEquals(initialState.utilizationThresholds.get(METRIC).unit,
                actual.utilizationThresholds.get(METRIC).unit);
    }

    @Test
    public void testPatch() throws Throwable {
        ServiceDocumentQueryResult initialStates = createInstances(SERVICE_COUNT);

        ProjectService.ProjectState patchBody = new ProjectService.ProjectState();
        patchBody.name = "projectB";
        patchBody.budget = new BigDecimal(200);
        patchBody.description = "projectB desc";
        patchBody.status = ProjectStatus.RETIRED;

        doPatch(EnumSet.of(TestProperty.FORCE_REMOTE), SERVICE_COUNT, initialStates, patchBody);

        ServiceDocumentQueryResult afterStates = this.host.getFactoryState(UriUtils
                .buildExpandLinksQueryUri(this.factoryURI));
        for (Object s : afterStates.documents.values()) {
            ProjectService.ProjectState state = Utils.fromJson(s, ProjectService.ProjectState.class);
            assertEquals(patchBody.name, state.name);
            assertEquals(patchBody.budget, state.budget);
            assertEquals(patchBody.description, state.description);
            assertEquals(patchBody.status, state.status);
        }
    }

    public void doPatch(EnumSet<TestProperty> props, int c,
            ServiceDocumentQueryResult initialStates,
            ProjectService.ProjectState patchBody) throws Throwable {
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

    private ProjectState buildInitialState() {
        ProjectService.ProjectState state = new ProjectService.ProjectState();
        state.name = "projectA";
        state.budget = new BigDecimal(100);
        state.description = "projectA desc";
        state.status = ProjectStatus.DRAFT;
        return state;
    }

    private ProjectState buildInitialStateWithUtilizationTheshold() {
        ProjectState initialState = buildInitialState();
        initialState.utilizationThresholds = new HashMap<>();
        UtilizationThreshold cpu = new UtilizationThreshold();
        cpu.overLimit = 10;
        cpu.underLimit = 5;
        initialState.utilizationThresholds.put(METRIC, cpu);

        return initialState;
    }

    private ServiceDocumentQueryResult createInstances(int c) throws Throwable {
        this.host.testStart(c);
        for (int i = 0; i < c; i++) {
            ProjectService.ProjectState initialState = buildInitialState();
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

    private ProjectState getOne(String link) {
        Operation getOp = Operation.createGet(this.host, link);
        Operation response = this.host.waitForResponse(getOp);
        return response.getBody(ProjectState.class);
    }

}
