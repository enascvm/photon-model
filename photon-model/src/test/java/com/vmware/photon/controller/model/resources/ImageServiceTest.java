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

import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static com.vmware.xenon.common.UriUtils.buildUriPath;

import java.util.Collections;
import java.util.EnumSet;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.ImageService.ImageState;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;
import com.vmware.xenon.services.common.TenantService;

/**
 * This class implements tests for the {@link ImageService} class.
 */
@RunWith(ImageServiceTest.class)
@SuiteClasses({ ImageServiceTest.ConstructorTest.class,
        ImageServiceTest.HandleStartTest.class,
        ImageServiceTest.HandlePatchTest.class,
        ImageServiceTest.QueryTest.class })
public class ImageServiceTest extends Suite {

    public ImageServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    // Copy-pasted from ImageService.getDocumentTemplate
    private static ImageState buildValidStartState() {

        ImageState image = new ImageState();

        image.id = "endpoint-specific-image-id";
        image.name = "endpoint-specific-image-name";
        image.description = "User-friendly-image-description";
        image.osFamily = "Linux";
        image.regionId = "endpoint-specific-image-region-id";

        image.endpointLink = buildUriPath(EndpointService.FACTORY_LINK, "the-A-cloud");
        image.groupLinks = singleton(
                buildUriPath(ResourceGroupService.FACTORY_LINK, "the-A-folder"));
        image.tenantLinks = singletonList(buildUriPath(TenantService.FACTORY_LINK, "the-A-tenant"));

        return image;
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {

        private ImageService service = new ImageService();

        @Test
        public void testServiceOptions() {
            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.CONCURRENT_GET_HANDLING,
                    Service.ServiceOption.PERSISTENCE,
                    Service.ServiceOption.REPLICATION,
                    Service.ServiceOption.OWNER_SELECTION,
                    Service.ServiceOption.IDEMPOTENT_POST,
                    Service.ServiceOption.ON_DEMAND_LOAD);
            assertEquals(expected, this.service.getOptions());
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleStartTest extends BaseModelTest {

        private final ImageState startState = buildValidStartState();

        @Test
        public void testValidStartState() throws Throwable {

            ImageState returnState = postServiceSynchronously(
                    ImageService.FACTORY_LINK,
                    this.startState, ImageState.class);

            assertNotNull(returnState);

            assertEquals(this.startState.id, returnState.id);
            assertEquals(this.startState.name, returnState.name);
            assertEquals(this.startState.regionId, returnState.regionId);
            assertEquals(this.startState.osFamily, returnState.osFamily);
            assertEquals(this.startState.description, returnState.description);
            assertEquals(this.startState.endpointLink, returnState.endpointLink);
            assertEquals(this.startState.endpointType, null);

            assertEquals(this.startState.tenantLinks, returnState.tenantLinks);
        }

        @Test
        public void testInvalidStartState() throws Throwable {

            ImageState invalidState = this.startState;

            {
                // Both cannot be set
                invalidState.endpointLink = buildUriPath(EndpointService.FACTORY_LINK, "endpointLink");
                invalidState.endpointType = "someEndpointType";

                postServiceSynchronously(
                        ImageService.FACTORY_LINK,
                        invalidState, ImageState.class, IllegalArgumentException.class);
            }
            {
                // Either should be set
                invalidState.endpointLink = null;
                invalidState.endpointType = null;

                postServiceSynchronously(
                        ImageService.FACTORY_LINK,
                        invalidState, ImageState.class, IllegalArgumentException.class);
            }
        }

        @Test
        public void testDuplicatePostWithChange() throws Throwable {

            ImageState returnState = postServiceSynchronously(
                    ImageService.FACTORY_LINK,
                    this.startState, ImageState.class);

            assertNotNull(returnState);
            assertEquals(this.startState.name, returnState.name);

            this.startState.name = "new-name";
            returnState = postServiceSynchronously(ImageService.FACTORY_LINK,
                    this.startState, ImageState.class);

            assertEquals(this.startState.name, returnState.name);
        }

        @Test
        public void testDuplicatePostWithNoChange() throws Throwable {

            ImageState returnState = postServiceSynchronously(
                    ImageService.FACTORY_LINK,
                    this.startState, ImageState.class);

            assertNotNull(returnState);

            returnState = postServiceSynchronously(ImageService.FACTORY_LINK,
                    returnState, ImageState.class);

            assertNotNull(returnState);
        }

    }

    /**
     * This class implements tests for the handlePatch method.
     */
    public static class HandlePatchTest extends BaseModelTest {

        private final ImageState startState = buildValidStartState();

        @Test
        public void testPatch() throws Throwable {

            ImageState returnState = postServiceSynchronously(
                    ImageService.FACTORY_LINK,
                    this.startState, ImageState.class);

            ImageState patchState = new ImageState();
            patchState.name = "patchName";
            patchState.description = "patchDesc";
            patchState.regionId = "patchRegion";
            patchState.osFamily = "Windows";

            patchState.customProperties = Collections.singletonMap("patchCPKey", "patchCPValue");
            patchState.tenantLinks = Collections.singletonList("patchTenant");
            patchState.groupLinks = Collections.singleton("patchGroup");

            patchServiceSynchronously(returnState.documentSelfLink, patchState);

            returnState = getServiceSynchronously(returnState.documentSelfLink, ImageState.class);

            assertEquals(patchState.name, returnState.name);
            assertEquals(patchState.description, returnState.description);
            assertEquals(patchState.regionId, returnState.regionId);
            assertEquals(patchState.osFamily, returnState.osFamily);

            assertEquals(patchState.customProperties, returnState.customProperties);

            assertEquals(2, returnState.tenantLinks.size());
            assertTrue(returnState.tenantLinks.contains("patchTenant"));
            assertTrue(returnState.tenantLinks.contains(this.startState.tenantLinks.get(0)));

            assertEquals(2, returnState.groupLinks.size());
            assertTrue(returnState.groupLinks.contains("patchGroup"));
            assertTrue(
                    returnState.groupLinks.contains(this.startState.groupLinks.iterator().next()));
        }
    }

    /**
     * This class implements tests for query ImageStates.
     */
    public static class QueryTest extends BaseModelTest {

        private ImageState startState = buildValidStartState();

        @Test
        public void testQueryImages() throws Throwable {

            ImageState state = postServiceSynchronously(
                    ImageService.FACTORY_LINK,
                    this.startState, ImageState.class);

            Query.Builder qBuilder = Query.Builder.create()
                    .addKindFieldClause(ImageState.class)
                    .addFieldClause(ImageState.FIELD_NAME_ID, state.id)
                    .addFieldClause(ImageState.FIELD_NAME_NAME, state.name)
                    .addCaseInsensitiveFieldClause(ImageState.FIELD_NAME_DESCRIPTION,
                            state.description.toUpperCase(), MatchType.TERM, Occurance.MUST_OCCUR)
                    .addCaseInsensitiveFieldClause(ImageState.FIELD_NAME_OS_FAMILY,
                            state.osFamily.toUpperCase(), MatchType.TERM, Occurance.MUST_OCCUR)
                    .addFieldClause(ImageState.FIELD_NAME_REGION_ID, state.regionId)
                    .addFieldClause(ImageState.FIELD_NAME_ENDPOINT_LINK, state.endpointLink);

            state.tenantLinks.forEach(tenantLink -> qBuilder
                    .addCollectionItemClause(ImageState.FIELD_NAME_TENANT_LINKS, tenantLink));

            QueryTask qt = QueryTask.Builder.createDirectTask()
                    .setQuery(qBuilder.build())
                    .build();

            qt = querySynchronously(qt);

            assertNotNull(qt.results.documentLinks);
            assertEquals((Long) 1L, qt.results.documentCount);
            assertEquals(state.documentSelfLink, qt.results.documentLinks.get(0));
        }
    }

}
