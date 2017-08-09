/*
 * Copyright (c) 2015-2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.model.resources;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.DeploymentService.DeploymentServiceState;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceStateCollectionUpdateRequest;
import com.vmware.xenon.common.Utils;

/**
 * This class implements tests for the {@link DeploymentService} class.
 */
@RunWith(DeploymentServiceTest.class)
@SuiteClasses({ DeploymentServiceTest.ConstructorTest.class,
        DeploymentServiceTest.HandleStartTest.class,
        DeploymentServiceTest.HandleGetTest.class,
        DeploymentServiceTest.HandlePatchTest.class,
        DeploymentServiceTest.HandlePutTest.class })
public class DeploymentServiceTest extends Suite {
    private static final String TEST_DESC_PROPERTY_NAME = "testDescProperty";
    private static final String TEST_DESC_PROPERTY_VALUE = UUID.randomUUID()
            .toString();

    public DeploymentServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    public static DeploymentServiceState buildValidStartState() throws Throwable {
        DeploymentServiceState cs = new DeploymentServiceState();
        cs.id = UUID.randomUUID().toString();
        cs.name = "my app";
        cs.componentLinks = new HashSet<>();
        cs.componentLinks.add("/compute/uuid");
        cs.descriptionLink = "/bp/foobar";
        cs.customProperties = new HashMap<>();
        cs.customProperties.put(TEST_DESC_PROPERTY_NAME,
                TEST_DESC_PROPERTY_VALUE);
        return cs;
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {

        private DeploymentService DeploymentService;

        @Before
        public void setUpTest() {
            this.DeploymentService = new DeploymentService();
        }

        @Test
        public void testServiceOptions() {

            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.CONCURRENT_GET_HANDLING,
                    Service.ServiceOption.PERSISTENCE,
                    Service.ServiceOption.REPLICATION,
                    Service.ServiceOption.OWNER_SELECTION,
                    Service.ServiceOption.IDEMPOTENT_POST);

            assertThat(this.DeploymentService.getOptions(), is(expected));
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleStartTest extends BaseModelTest {

        public final long startTimeMicros = Utils.getNowMicrosUtc();

        @Test
        public void testDuplicatePost() throws Throwable {
            DeploymentServiceState startState = DeploymentServiceTest
                    .buildValidStartState();
            DeploymentServiceState returnState = postServiceSynchronously(
                    DeploymentService.FACTORY_LINK, startState,
                    DeploymentServiceState.class);

            assertNotNull(returnState);
            assertThat(returnState.desc, is(startState.desc));
            startState.desc = "new-address";
            returnState = postServiceSynchronously(DeploymentService.FACTORY_LINK,
                    startState, DeploymentServiceState.class);
            assertThat(returnState.desc, is(startState.desc));
        }

        @Test
        public void testMissingId() throws Throwable {
            DeploymentServiceState startState = buildValidStartState();
            startState.id = null;

            DeploymentServiceState returnState = postServiceSynchronously(
                    DeploymentService.FACTORY_LINK, startState,
                    DeploymentServiceState.class);

            assertNotNull(returnState);
            assertNotNull(returnState.id);
        }

        @Test
        public void testMissingCreationTimeMicros() throws Throwable {
            DeploymentServiceState startState = buildValidStartState();
            startState.creationTimeMicros = null;

            DeploymentServiceState returnState = postServiceSynchronously(
                    DeploymentService.FACTORY_LINK, startState,
                    DeploymentServiceState.class);

            assertNotNull(returnState);
            assertNotNull(returnState.creationTimeMicros);
        }

        @Test
        public void testProvidedCreationTimeMicros() throws Throwable {
            DeploymentServiceState startState = buildValidStartState();
            startState.creationTimeMicros = Long.MIN_VALUE;

            DeploymentServiceState returnState = postServiceSynchronously(
                    DeploymentService.FACTORY_LINK, startState,
                    DeploymentServiceState.class);

            assertNotNull(returnState);
            assertNotNull(returnState.creationTimeMicros);
            assertEquals(Long.MIN_VALUE, returnState.creationTimeMicros.longValue());
        }
    }

    /**
     * This class implements tests for the handleGet method.
     */
    public static class HandleGetTest extends BaseModelTest {
        @Test
        public void testGet() throws Throwable {
            DeploymentServiceState startState = buildValidStartState();

            DeploymentServiceState returnState = postServiceSynchronously(
                    DeploymentService.FACTORY_LINK, startState,
                    DeploymentServiceState.class);
            assertNotNull(returnState);

            DeploymentServiceState getState = getServiceSynchronously(
                    returnState.documentSelfLink, DeploymentServiceState.class);

            assertThat(getState.id, is(startState.id));
            assertThat(getState.descriptionLink, is(startState.descriptionLink));
            assertThat(getState.desc, is(startState.desc));
            assertThat(getState.componentLinks, is(startState.componentLinks));
        }
    }

    /**
     * This class implements tests for the handlePatch method.
     */
    public static class HandlePatchTest extends BaseModelTest {

        @Test
        public void testPatch() throws Throwable {
            DeploymentServiceState startState = buildValidStartState();
            startState.creationTimeMicros = Long.MIN_VALUE;

            DeploymentServiceState returnState = postServiceSynchronously(
                    DeploymentService.FACTORY_LINK,
                    startState, DeploymentServiceState.class);
            assertNotNull(returnState);

            DeploymentServiceState patchBody = new DeploymentServiceState();
            patchBody.id = UUID.randomUUID().toString();
            patchBody.desc = "10.0.0.2";
            patchBody.tenantLinks = new ArrayList<>();
            patchBody.tenantLinks.add("tenant1");
            patchBody.groupLinks = new HashSet<>();
            patchBody.groupLinks.add("group1");
            patchServiceSynchronously(returnState.documentSelfLink,
                    patchBody);

            DeploymentServiceState getState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    DeploymentServiceState.class);

            assertThat(getState.id, is(patchBody.id));
            assertThat(getState.desc, is(patchBody.desc));
            assertEquals(getState.tenantLinks, patchBody.tenantLinks);
            assertEquals(getState.groupLinks, patchBody.groupLinks);
            // make sure launchTimeMicros was preserved
            assertEquals((Long) Long.MIN_VALUE, getState.creationTimeMicros);
        }

        @Test(expected = IllegalArgumentException.class)
        public void testPatchFailOnDescriptionLinkChange() throws Throwable {
            DeploymentServiceState startState = buildValidStartState();

            DeploymentServiceState returnState = postServiceSynchronously(
                    DeploymentService.FACTORY_LINK,
                    startState, DeploymentServiceState.class);
            assertNotNull(returnState);

            DeploymentServiceState patchBody = new DeploymentServiceState();
            patchBody.descriptionLink = "should not be updated";
            patchServiceSynchronously(returnState.documentSelfLink,
                    patchBody);
        }

        @Test
        public void testPatchNoChange() throws Throwable {
            DeploymentServiceState startState = buildValidStartState();

            DeploymentServiceState returnState = postServiceSynchronously(
                    DeploymentService.FACTORY_LINK,
                    startState, DeploymentServiceState.class);
            assertNotNull(returnState);

            DeploymentServiceState patchBody = new DeploymentServiceState();
            patchServiceSynchronously(returnState.documentSelfLink,
                    patchBody);

            DeploymentServiceState getState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    DeploymentServiceState.class);

            assertEquals(0L, getState.documentVersion);
        }

        @Test
        public void testPatchRemoveComponentsLinks() throws Throwable {
            DeploymentServiceState startState = buildValidStartState();
            startState.componentLinks.clear();
            startState.componentLinks.add("http://network0");
            startState.componentLinks.add("http://network1");
            startState.componentLinks.add("http://network2");
            startState.componentLinks.add("http://network3");

            DeploymentServiceState returnState = postServiceSynchronously(
                    DeploymentService.FACTORY_LINK,
                    startState, DeploymentServiceState.class);
            assertNotNull(returnState);

            Map<String, Collection<Object>> collectionsMap = new HashMap<>();
            Collection<Object> networkLinksToBeRemoved = new ArrayList<>(Arrays.asList(
                    "http://network1", "http://network3"));
            collectionsMap.put("componentLinks", networkLinksToBeRemoved);
            ServiceStateCollectionUpdateRequest collectionRemovalBody = ServiceStateCollectionUpdateRequest
                    .create(null, collectionsMap);

            // send PATCH to remove networkInterfaceLinks: network1, network3
            patchServiceSynchronously(returnState.documentSelfLink,
                    collectionRemovalBody);

            DeploymentServiceState getState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    DeploymentServiceState.class);

            Set<String> expectedCompLinks = new HashSet<String>(Arrays.asList(
                    "http://network0", "http://network2"));

            assertThat(getState.id, is(startState.id));
            assertEquals(expectedCompLinks, getState.componentLinks);
        }
    }

    /**
     * This class implements tests for the handlePut method.
     */
    public static class HandlePutTest extends BaseModelTest {

        @Test
        public void testPut() throws Throwable {
            DeploymentServiceState startState = buildValidStartState();
            startState.creationTimeMicros = Long.MIN_VALUE;

            DeploymentServiceState returnState = postServiceSynchronously(
                    DeploymentService.FACTORY_LINK,
                    startState, DeploymentServiceState.class);
            assertNotNull(returnState);

            DeploymentServiceState newState = new DeploymentServiceState();
            newState.id = UUID.randomUUID().toString();
            newState.desc = "10.0.0.2";
            newState.creationTimeMicros = Long.MIN_VALUE;
            newState.descriptionLink = startState.descriptionLink;
            newState.tenantLinks = new ArrayList<>();
            newState.tenantLinks.add("tenant1");
            newState.groupLinks = new HashSet<>();
            newState.groupLinks.add("group1");
            newState.componentLinks = new HashSet<>();
            newState.componentLinks.add("http://disk1");

            putServiceSynchronously(returnState.documentSelfLink,
                    newState);

            DeploymentServiceState getState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    DeploymentServiceState.class);

            assertEquals(getState.id, newState.id);
            assertEquals(getState.desc, newState.desc);
            assertEquals(getState.descriptionLink, newState.descriptionLink);

            assertEquals(getState.tenantLinks, newState.tenantLinks);
            assertEquals(getState.groupLinks, newState.groupLinks);
            assertEquals(getState.componentLinks, newState.componentLinks);
            assertEquals((Long) Long.MIN_VALUE, returnState.creationTimeMicros);
        }

        @Test(expected = IllegalArgumentException.class)
        public void testPutFailOnDescriptionLinkChange() throws Throwable {
            DeploymentServiceState startState = buildValidStartState();

            DeploymentServiceState returnState = postServiceSynchronously(
                    DeploymentService.FACTORY_LINK,
                    startState, DeploymentServiceState.class);
            assertNotNull(returnState);

            returnState.descriptionLink = "/foobarUpdated";

            putServiceSynchronously(returnState.documentSelfLink,
                    returnState);
        }
    }

}
