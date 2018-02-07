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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask.Query;


/**
 * This class implements tests for the {@link ResourceGroupService} class.
 */
@RunWith(ResourceGroupServiceTest.class)
@SuiteClasses({ ResourceGroupServiceTest.ConstructorTest.class,
        ResourceGroupServiceTest.HandleStartTest.class,
        ResourceGroupServiceTest.HandlePatchTest.class,
        ResourceGroupServiceTest.HandlePutTest.class})
public class ResourceGroupServiceTest extends Suite {

    public ResourceGroupServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    private static ResourceGroupService.ResourceGroupState buildValidStartState(boolean assignHost)
            throws Throwable {
        ResourceGroupService.ResourceGroupState rg = new ResourceGroupService.ResourceGroupState();
        rg.name = "my resource group";
        rg.customProperties = new HashMap<>();
        rg.customProperties.put("key1", "value1");
        if (assignHost) {
            rg.computeHostLink = "host-1";
        }
        return rg;
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {

        private ResourceGroupService resourceGroupService;

        @Before
        public void setUpTest() {
            this.resourceGroupService = new ResourceGroupService();
        }

        @Test
        public void testServiceOptions() {

            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.CONCURRENT_GET_HANDLING,
                    Service.ServiceOption.OWNER_SELECTION,
                    Service.ServiceOption.PERSISTENCE,
                    Service.ServiceOption.REPLICATION,
                    Service.ServiceOption.IDEMPOTENT_POST);

            assertThat(this.resourceGroupService.getOptions(), is(expected));
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleStartTest extends BaseModelTest {
        @Test
        public void testValidStartState() throws Throwable {
            ResourceGroupService.ResourceGroupState startState = buildValidStartState(false);
            ResourceGroupService.ResourceGroupState returnState = postServiceSynchronously(
                    ResourceGroupService.FACTORY_LINK, startState,
                    ResourceGroupService.ResourceGroupState.class);

            assertThat(returnState.name, is(startState.name));
            startState.customProperties.forEach((k, v) ->
                    assertEquals(v, returnState.customProperties.get(k)));
        }

        @Test
        public void testValidStartStateWithHost() throws Throwable {
            ResourceGroupService.ResourceGroupState startState = buildValidStartState(true);
            ResourceGroupService.ResourceGroupState returnState = postServiceSynchronously(
                    ResourceGroupService.FACTORY_LINK, startState,
                    ResourceGroupService.ResourceGroupState.class);

            assertThat(returnState.name, is(startState.name));
            assertEquals(returnState.customProperties, returnState.customProperties);
            assertThat(returnState.computeHostLink, is(startState.computeHostLink));
        }

        @Test
        public void testDuplicatePost() throws Throwable {
            ResourceGroupService.ResourceGroupState startState = buildValidStartState(false);
            ResourceGroupService.ResourceGroupState returnState = postServiceSynchronously(
                    ResourceGroupService.FACTORY_LINK, startState,
                    ResourceGroupService.ResourceGroupState.class);

            assertNotNull(returnState);
            assertThat(returnState.name, is(startState.name));
            startState.name = "new name";
            returnState = postServiceSynchronously(
                    ResourceGroupService.FACTORY_LINK, startState,
                    ResourceGroupService.ResourceGroupState.class);

            assertNotNull(returnState);
            assertThat(returnState.name, is(startState.name));
            ResourceGroupService.ResourceGroupState newState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    ResourceGroupService.ResourceGroupState.class);
            assertThat(newState.name, is(startState.name));
        }

        @Test
        public void testDuplicatePostAssignComputeHost() throws Throwable {
            ResourceGroupService.ResourceGroupState startState = buildValidStartState(false);
            ResourceGroupService.ResourceGroupState returnState = postServiceSynchronously(
                    ResourceGroupService.FACTORY_LINK, startState,
                    ResourceGroupService.ResourceGroupState.class);

            assertNotNull(returnState);
            assertNull(returnState.computeHostLink);
            assertThat(returnState.name, is(startState.name));
            startState.name = "new name";
            startState.computeHostLink = "host-1";
            returnState = postServiceSynchronously(
                    ResourceGroupService.FACTORY_LINK, startState,
                    ResourceGroupService.ResourceGroupState.class);

            assertNotNull(returnState);
            assertThat(returnState.name, is(startState.name));
            ResourceGroupService.ResourceGroupState newState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    ResourceGroupService.ResourceGroupState.class);
            assertThat(newState.name, is(startState.name));
            assertNotNull(returnState.computeHostLink);
            assertThat(returnState.computeHostLink, is(startState.computeHostLink));
        }

        @Test
        public void testDuplicatePostModifyComputeHost() throws Throwable {
            ResourceGroupService.ResourceGroupState startState = buildValidStartState(true);
            ResourceGroupService.ResourceGroupState returnState = postServiceSynchronously(
                    ResourceGroupService.FACTORY_LINK, startState,
                    ResourceGroupService.ResourceGroupState.class);

            assertNotNull(returnState);
            assertNotNull(returnState.computeHostLink);
            assertThat(returnState.name, is(startState.name));

            returnState.computeHostLink = "host-2";
            postServiceSynchronously(
                    ResourceGroupService.FACTORY_LINK, returnState,
                    ResourceGroupService.ResourceGroupState.class, IllegalArgumentException.class);
        }

        @Test
        public void testDuplicatePostModifyCreationTime() throws Throwable {
            ResourceGroupService.ResourceGroupState startState = buildValidStartState(false);
            ResourceGroupService.ResourceGroupState returnState = postServiceSynchronously(
                    ResourceGroupService.FACTORY_LINK,
                    startState, ResourceGroupService.ResourceGroupState.class);

            assertNotNull(returnState);
            assertNotNull(returnState.documentCreationTimeMicros);

            returnState.documentCreationTimeMicros = Utils.getNowMicrosUtc();

            postServiceSynchronously(ResourceGroupService.FACTORY_LINK,
                    returnState, ResourceGroupService.ResourceGroupState.class,
                    IllegalArgumentException.class);
        }

        @Test
        public void testMissingName() throws Throwable {
            ResourceGroupService.ResourceGroupState startState = buildValidStartState(false);
            startState.name = null;
            postServiceSynchronously(ResourceGroupService.FACTORY_LINK,
                    startState, ResourceGroupService.ResourceGroupState.class,
                    IllegalArgumentException.class);
        }
    }

    /**
     * This class implements tests for the handlePatch method.
     */
    public static class HandlePatchTest extends BaseModelTest {
        @Test
        public void testPatchResourceGroupName() throws Throwable {
            ResourceGroupService.ResourceGroupState startState = postServiceSynchronously(
                    ResourceGroupService.FACTORY_LINK, buildValidStartState(false),
                    ResourceGroupService.ResourceGroupState.class);
            assertNull(startState.computeHostLink);
            assertNotNull(startState.documentCreationTimeMicros);

            ResourceGroupService.ResourceGroupState patchState = new ResourceGroupService.ResourceGroupState();
            patchState.name = UUID.randomUUID().toString();
            patchState.customProperties = new HashMap<String, String>();
            patchState.customProperties.put("key2", "value2");
            patchState.tenantLinks = new ArrayList<String>();
            patchState.tenantLinks.add("tenant1");
            patchState.computeHostLink = "host-1";
            patchState.query = Query.Builder.create().build();
            patchServiceSynchronously(startState.documentSelfLink,
                    patchState);

            ResourceGroupService.ResourceGroupState newState = getServiceSynchronously(
                    startState.documentSelfLink,
                    ResourceGroupService.ResourceGroupState.class);
            assertThat(newState.name, is(patchState.name));
            assertEquals(newState.tenantLinks, patchState.tenantLinks);
            assertTrue(newState.customProperties.size() >= 2);
            assertNotNull(newState.query);
            assertNotNull(newState.computeHostLink);
            assertThat(newState.computeHostLink, is(patchState.computeHostLink));
        }

        @Test(expected = IllegalArgumentException.class)
        public void testPatchModifyHost() throws Throwable {
            ResourceGroupService.ResourceGroupState startState = postServiceSynchronously(
                    ResourceGroupService.FACTORY_LINK, buildValidStartState(true),
                    ResourceGroupService.ResourceGroupState.class);
            assertNotNull(startState.computeHostLink);

            ResourceGroupService.ResourceGroupState patchState = new ResourceGroupService.ResourceGroupState();
            patchState.computeHostLink = "host-2";
            patchServiceSynchronously(startState.documentSelfLink, patchState);
        }

        @Test
        public void testPatchModifyCreationTime() throws Throwable {
            ResourceGroupService.ResourceGroupState startState = buildValidStartState(false);

            ResourceGroupService.ResourceGroupState returnState = postServiceSynchronously(
                    ResourceGroupService.FACTORY_LINK,
                    startState, ResourceGroupService.ResourceGroupState.class);
            assertNotNull(returnState.documentCreationTimeMicros);
            long originalCreationTime = returnState.documentCreationTimeMicros;

            ResourceGroupService.ResourceGroupState patchState = new ResourceGroupService.ResourceGroupState();
            long currentCreationTime = Utils.getNowMicrosUtc();
            patchState.documentCreationTimeMicros = currentCreationTime;

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);

            returnState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    ResourceGroupService.ResourceGroupState.class);
            assertNotNull(returnState.documentCreationTimeMicros);
            assertThat(returnState.documentCreationTimeMicros, is(originalCreationTime));
        }
    }

    /**
     * This class implements tests for the handlePut method.
     */
    public static class HandlePutTest extends BaseModelTest {

        @Test
        public void testPut() throws Throwable {
            ResourceGroupService.ResourceGroupState startState = buildValidStartState(false);
            ResourceGroupService.ResourceGroupState returnState = postServiceSynchronously(
                    ResourceGroupService.FACTORY_LINK,
                    startState, ResourceGroupService.ResourceGroupState.class);

            assertNotNull(returnState);

            ResourceGroupService.ResourceGroupState newState = new ResourceGroupService.ResourceGroupState();
            newState.name = "my resource group";
            newState.customProperties = new HashMap<>();
            newState.customProperties.put("key1", "value1");
            newState.documentCreationTimeMicros = returnState.documentCreationTimeMicros;

            putServiceSynchronously(returnState.documentSelfLink,
                    newState);

            ResourceGroupService.ResourceGroupState getState = getServiceSynchronously(returnState.documentSelfLink,
                    ResourceGroupService.ResourceGroupState.class);
            assertThat(getState.name, is(newState.name));
            assertEquals(getState.tenantLinks, newState.tenantLinks);
            assertEquals(getState.groupLinks, newState.groupLinks);
            // make sure launchTimeMicros was preserved
            assertEquals(getState.creationTimeMicros, returnState.creationTimeMicros);
            assertEquals(getState.documentCreationTimeMicros,
                    returnState.documentCreationTimeMicros);
        }

        @Test(expected = IllegalArgumentException.class)
        public void testPutModifyCreationTime() throws Throwable {
            ResourceGroupService.ResourceGroupState startState = buildValidStartState(false);
            ResourceGroupService.ResourceGroupState returnState = postServiceSynchronously(
                    ResourceGroupService.FACTORY_LINK,
                    startState, ResourceGroupService.ResourceGroupState.class);

            assertNotNull(returnState);

            ResourceGroupService.ResourceGroupState newState = new ResourceGroupService.ResourceGroupState();
            newState.name = "my resource group";
            newState.customProperties = new HashMap<>();
            newState.customProperties.put("key1", "value1");

            newState.documentCreationTimeMicros = Utils.getNowMicrosUtc();

            putServiceSynchronously(returnState.documentSelfLink, newState);
        }

        @Test(expected = IllegalArgumentException.class)
        public void testPutModifyHost() throws Throwable {
            ResourceGroupService.ResourceGroupState startState = buildValidStartState(true);
            ResourceGroupService.ResourceGroupState returnState = postServiceSynchronously(
                    ResourceGroupService.FACTORY_LINK,
                    startState, ResourceGroupService.ResourceGroupState.class);

            assertNotNull(returnState);

            ResourceGroupService.ResourceGroupState newState = new ResourceGroupService.ResourceGroupState();
            newState.name = "my resource group";
            newState.customProperties = new HashMap<>();
            newState.customProperties.put("key1", "value1");
            newState.documentCreationTimeMicros = returnState.documentCreationTimeMicros;

            newState.computeHostLink = "host-2";

            putServiceSynchronously(returnState.documentSelfLink, newState);
        }
    }
}
