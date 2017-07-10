/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.RouterService.RouterState;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.TenantService;

/**
 * This class implements tests for the {@link RouterService} class.
 */
@RunWith(RouterServiceTest.class)
@Suite.SuiteClasses({ RouterServiceTest.ConstructorTest.class,
        RouterServiceTest.HandleStartTest.class,
        RouterServiceTest.HandlePatchTest.class,
        RouterServiceTest.QueryTest.class })
public class RouterServiceTest extends Suite {

    public RouterServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    public static RouterState buildValidStartState() {
        RouterState routerState = new RouterState();
        routerState.id = UUID.randomUUID().toString();
        routerState.name = "routerName";
        routerState.endpointLink = EndpointService.FACTORY_LINK + "/my-endpoint";
        routerState.regionId = "region1";
        routerState.type = "type1";
        routerState.tenantLinks = new ArrayList<>();
        routerState.tenantLinks.add("tenant-linkA");

        return routerState;
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {
        private RouterService routerService = new RouterService();

        @Before
        public void setupTest() {
            this.routerService = new RouterService();
        }

        @Test
        public void testServiceOptions() {
            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.CONCURRENT_GET_HANDLING,
                    Service.ServiceOption.PERSISTENCE,
                    Service.ServiceOption.REPLICATION,
                    Service.ServiceOption.OWNER_SELECTION,
                    Service.ServiceOption.IDEMPOTENT_POST);
            assertThat(this.routerService.getOptions(), is(expected));
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleStartTest extends BaseModelTest {
        @Test
        public void testValidStartState() throws Throwable {
            RouterState startState = buildValidStartState();
            RouterState returnState = postServiceSynchronously(
                    RouterService.FACTORY_LINK,
                    startState, RouterState.class);

            assertNotNull(returnState);
            assertThat(returnState.id, is(startState.id));
            assertThat(returnState.name, is(startState.name));
            assertThat(returnState.endpointLink, is(startState.endpointLink));
            assertThat(returnState.regionId, is(startState.regionId));
            assertThat(returnState.tenantLinks.get(0), is(startState.tenantLinks.get(0)));
        }

        @Test
        public void testDuplicatePost() throws Throwable {
            RouterState startState = buildValidStartState();
            RouterState returnState = postServiceSynchronously(
                    RouterService.FACTORY_LINK,
                    startState, RouterState.class);

            assertNotNull(returnState);
            assertThat(returnState.name, is(startState.name));
            returnState.name = "new-name";
            RouterState returnState2 = postServiceSynchronously(RouterService.FACTORY_LINK,
                    returnState, RouterState.class);
            assertThat(returnState2.name, is(returnState.name));
            assertThat(returnState2.documentSelfLink, is(returnState.documentSelfLink));
        }

        @Test
        public void testInvalidValues() throws Throwable {
            RouterState missingEndpointLink = buildValidStartState();
            missingEndpointLink.endpointLink = null;
            postServiceSynchronously(RouterService.FACTORY_LINK,
                    missingEndpointLink, RouterState.class,
                    IllegalArgumentException.class);
        }
    }

    /**
     * This class implements tests for the handlePatch method.
     */
    public static class HandlePatchTest extends BaseModelTest {
        @Test
        public void testPatch() throws Throwable {
            RouterState startState = buildValidStartState();

            RouterState returnState = postServiceSynchronously(
                    RouterService.FACTORY_LINK,
                    startState, RouterState.class);

            RouterState patchState = new RouterState();
            patchState.name = "patchNetworkName";
            patchState.endpointLink = EndpointService.FACTORY_LINK + "/new-endpoint";
            patchState.customProperties = new HashMap<>();
            patchState.customProperties.put("patchKey", "patchValue");
            patchState.tenantLinks = new ArrayList<String>();
            patchState.tenantLinks.add("tenant1");
            patchState.groupLinks = new HashSet<String>();
            patchState.groupLinks.add("group1");
            patchState.regionId = "new-region";
            patchState.type = "new-type";
            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);

            returnState = getServiceSynchronously(
                    returnState.documentSelfLink, RouterState.class);

            assertThat(returnState.name, is(patchState.name));
            assertThat(returnState.endpointLink, is(startState.endpointLink)); // no change
            assertThat(returnState.customProperties, is(patchState.customProperties));
            assertThat(returnState.tenantLinks.size(), is(2));
            assertEquals(returnState.groupLinks, patchState.groupLinks);
            // region ID should not be updated
            assertEquals(returnState.regionId, startState.regionId);
            assertEquals(returnState.type, patchState.type);
        }
    }

    /**
     * This class implements tests for query.
     */
    public static class QueryTest extends BaseModelTest {
        @Test
        public void testTenantLinksQuery() throws Throwable {
            RouterState RouterState = buildValidStartState();
            URI tenantUri = UriUtils.buildFactoryUri(this.host, TenantService.class);
            RouterState.tenantLinks = new ArrayList<>();
            RouterState.tenantLinks.add(UriUtils.buildUriPath(
                    tenantUri.getPath(), "tenantA"));
            RouterState startState = postServiceSynchronously(
                    RouterService.FACTORY_LINK,
                    RouterState, RouterState.class);

            String kind = Utils.buildKind(RouterState.class);
            String propertyName = QueryTask.QuerySpecification
                    .buildCollectionItemName(ResourceState.FIELD_NAME_TENANT_LINKS);

            QueryTask q = createDirectQueryTask(kind, propertyName,
                    RouterState.tenantLinks.get(0));
            q = querySynchronously(q);
            assertNotNull(q.results.documentLinks);
            assertThat(q.results.documentCount, is(1L));
            assertThat(q.results.documentLinks.get(0),
                    is(startState.documentSelfLink));
        }
    }

}
