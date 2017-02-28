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

import java.util.EnumSet;
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

/**
 * This class implements tests for the {@link TagService} class.
 */
@RunWith(TagServiceTest.class)
@SuiteClasses({ TagServiceTest.ConstructorTest.class,
        TagServiceTest.HandleStartTest.class,
        TagServiceTest.HandlePatchTest.class,
        TagServiceTest.HandlePutTest.class })
public class TagServiceTest extends Suite {

    public TagServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    private static TagService.TagState buildRandomStartState()
            throws Throwable {
        TagService.TagState tag = new TagService.TagState();
        tag.key = UUID.randomUUID().toString();
        tag.value = UUID.randomUUID().toString();
        return tag;
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {

        private TagService tagService;

        @Before
        public void setUpTest() {
            this.tagService = new TagService();
        }

        @Test
        public void testServiceOptions() {

            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.CONCURRENT_GET_HANDLING,
                    Service.ServiceOption.OWNER_SELECTION,
                    Service.ServiceOption.PERSISTENCE,
                    Service.ServiceOption.REPLICATION,
                    Service.ServiceOption.IDEMPOTENT_POST);

            assertThat(this.tagService.getOptions(), is(expected));
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleStartTest extends BaseModelTest {
        @Test
        public void testValidStartState() throws Throwable {
            TagService.TagState startState = buildRandomStartState();
            TagService.TagState returnState = postServiceSynchronously(
                    TagService.FACTORY_LINK, startState,
                    TagService.TagState.class);

            assertThat(returnState.key, is(startState.key));
            assertThat(returnState.value, is(startState.value));
        }

        @Test
        public void testMissingKey() throws Throwable {
            TagService.TagState startState = buildRandomStartState();
            startState.key = null;
            postServiceSynchronously(TagService.FACTORY_LINK,
                    startState, TagService.TagState.class,
                    IllegalArgumentException.class);
        }

        @Test
        public void testMissingValue() throws Throwable {
            TagService.TagState startState = buildRandomStartState();
            startState.value = null;
            postServiceSynchronously(TagService.FACTORY_LINK,
                    startState, TagService.TagState.class,
                    IllegalArgumentException.class);
        }

        @Test
        public void testEmptyValue() throws Throwable {
            TagService.TagState startState = buildRandomStartState();
            startState.value = "";
            TagService.TagState returnState = postServiceSynchronously(TagService.FACTORY_LINK,
                    startState, TagService.TagState.class);
            assertThat(returnState.value, is(startState.value));
        }

        @Test
        public void testSubsequentPosts() throws Throwable {
            TagService.TagState startState = buildRandomStartState();
            TagService.TagState returnState = postServiceSynchronously(
                    TagService.FACTORY_LINK, startState,
                    TagService.TagState.class);
            assertNotNull(returnState);
            assertThat(returnState.key, is(startState.key));
            assertThat(returnState.value, is(startState.value));

            startState.key = UUID.randomUUID().toString();
            returnState = postServiceSynchronously(
                    TagService.FACTORY_LINK, startState,
                    TagService.TagState.class);
            assertNotNull(returnState);
            assertThat(returnState.key, is(startState.key));
            assertThat(returnState.value, is(startState.value));

            TagService.TagState newState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    TagService.TagState.class);
            assertThat(newState.key, is(startState.key));
            assertThat(newState.value, is(startState.value));
        }

        @Test
        public void testExternalTagPost() throws Throwable {
            TagService.TagState startState = buildRandomStartState();
            startState.external = Boolean.TRUE;
            TagService.TagState returnState = postServiceSynchronously(
                    TagService.FACTORY_LINK, startState,
                    TagService.TagState.class);
            assertNotNull(returnState);
            assertThat(returnState.key, is(startState.key));
            assertThat(returnState.value, is(startState.value));
            assertThat(returnState.external, is(startState.external));

            startState.external = null;
            returnState = postServiceSynchronously(
                    TagService.FACTORY_LINK, startState,
                    TagService.TagState.class);
            assertNotNull(returnState);
            assertThat(returnState.key, is(startState.key));
            assertThat(returnState.value, is(startState.value));
            assertThat(returnState.external, is(Boolean.TRUE));
        }

        @Test
        public void testSwitchExternalToLocalTagPost() throws Throwable {
            TagService.TagState startState = buildRandomStartState();
            startState.external = Boolean.TRUE;
            TagService.TagState returnState = postServiceSynchronously(
                    TagService.FACTORY_LINK, startState,
                    TagService.TagState.class);
            assertNotNull(returnState);
            assertThat(returnState.key, is(startState.key));
            assertThat(returnState.value, is(startState.value));
            assertThat(returnState.external, is(startState.external));

            startState.external = Boolean.FALSE;
            returnState = postServiceSynchronously(
                    TagService.FACTORY_LINK, startState,
                    TagService.TagState.class);
            assertNotNull(returnState);
            assertThat(returnState.key, is(startState.key));
            assertThat(returnState.value, is(startState.value));
            assertThat(returnState.external, is(startState.external));
        }

        @Test
        public void testSwitchLocalToExternalTagPost() throws Throwable {
            TagService.TagState startState = buildRandomStartState();
            startState.external = Boolean.FALSE;
            TagService.TagState returnState = postServiceSynchronously(
                    TagService.FACTORY_LINK, startState,
                    TagService.TagState.class);
            assertNotNull(returnState);
            assertThat(returnState.key, is(startState.key));
            assertThat(returnState.value, is(startState.value));
            assertThat(returnState.external, is(startState.external));

            startState.external = Boolean.TRUE;
            returnState = postServiceSynchronously(
                    TagService.FACTORY_LINK, startState,
                    TagService.TagState.class);
            assertNotNull(returnState);
            assertThat(returnState.key, is(startState.key));
            assertThat(returnState.value, is(startState.value));
            assertThat(returnState.external, is(Boolean.FALSE));
        }

        @Test
        public void testIdempotentPost() throws Throwable {
            TagService.TagState startState = buildRandomStartState();

            TagService.TagState returnState = postServiceSynchronously(
                    TagService.FACTORY_LINK, startState,
                    TagService.TagState.class);

            TagService.TagState newReturnState = postServiceSynchronously(
                    TagService.FACTORY_LINK, startState,
                    TagService.TagState.class);

            assertThat(newReturnState.documentSelfLink, is(returnState.documentSelfLink));
            assertThat(newReturnState.documentVersion, is(1L));
        }
    }

    /**
     * This class implements tests for the handlePatch method.
     */
    public static class HandlePatchTest extends BaseModelTest {
        @Test(expected = UnsupportedOperationException.class)
        public void testPatchTag() throws Throwable {
            TagService.TagState startState = buildRandomStartState();
            TagService.TagState returnState = postServiceSynchronously(
                    TagService.FACTORY_LINK, startState,
                    TagService.TagState.class);

            TagService.TagState patchState = buildRandomStartState();
            patchServiceSynchronously(returnState.documentSelfLink, patchState);
        }
    }

    /**
     * This class implements tests for the handlePut method.
     */
    public static class HandlePutTest extends BaseModelTest {
        @Test(expected = UnsupportedOperationException.class)
        public void testPutTagDifferentValues() throws Throwable {
            TagService.TagState startState = buildRandomStartState();
            TagService.TagState returnState = postServiceSynchronously(
                    TagService.FACTORY_LINK, startState,
                    TagService.TagState.class);

            startState.key = UUID.randomUUID().toString();
            startState.value = UUID.randomUUID().toString();
            putServiceSynchronously(returnState.documentSelfLink, startState);
        }

        @Test
        public void testPutTagSameValues() throws Throwable {
            TagService.TagState startState = buildRandomStartState();
            TagService.TagState returnState = postServiceSynchronously(
                    TagService.FACTORY_LINK, startState,
                    TagService.TagState.class);

            putServiceSynchronously(returnState.documentSelfLink, startState);

            TagService.TagState newState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    TagService.TagState.class);
            assertThat(newState.key, is(startState.key));
            assertThat(newState.value, is(startState.value));
            assertEquals(newState.tenantLinks, startState.tenantLinks);
        }
    }
}
