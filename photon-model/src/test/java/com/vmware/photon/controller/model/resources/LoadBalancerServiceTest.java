/*
 * Copyright (c) 2015-2017 VMware, Inc. All Rights Reserved.
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
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.LoadBalancerService.LoadBalancerState;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.TenantService;

/**
 * This class implements tests for the {@link LoadBalancerService} class.
 */
@RunWith(LoadBalancerServiceTest.class)
@SuiteClasses({ LoadBalancerServiceTest.ConstructorTest.class,
        LoadBalancerServiceTest.HandleStartTest.class,
        LoadBalancerServiceTest.HandlePatchTest.class,
        LoadBalancerServiceTest.QueryTest.class })
public class LoadBalancerServiceTest extends Suite {

    public LoadBalancerServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    private static LoadBalancerState buildValidStartState() {
        LoadBalancerState loadBalancerState = new LoadBalancerState();
        loadBalancerState.id = UUID.randomUUID().toString();
        loadBalancerState.name = "networkName";
        loadBalancerState.endpointLink = EndpointService.FACTORY_LINK + "/my-endpoint";
        loadBalancerState.regionId = "regionId";
        loadBalancerState.zoneId = "zoneId";
        loadBalancerState.protocol = "HTTP";
        loadBalancerState.port = 80;
        loadBalancerState.instanceProtocol = "HTTP";
        loadBalancerState.instancePort = 80;
        loadBalancerState.tenantLinks = new ArrayList<>();
        loadBalancerState.tenantLinks.add("tenant-linkA");

        return loadBalancerState;
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {
        private LoadBalancerService loadBalancerService = new LoadBalancerService();

        @Before
        public void setupTest() {
            this.loadBalancerService = new LoadBalancerService();
        }

        @Test
        public void testServiceOptions() {
            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.CONCURRENT_GET_HANDLING,
                    Service.ServiceOption.PERSISTENCE,
                    Service.ServiceOption.REPLICATION,
                    Service.ServiceOption.OWNER_SELECTION,
                    Service.ServiceOption.IDEMPOTENT_POST);
            assertThat(this.loadBalancerService.getOptions(), is(expected));
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleStartTest extends BaseModelTest {
        @Test
        public void testValidStartState() throws Throwable {
            LoadBalancerState startState = buildValidStartState();
            LoadBalancerState returnState = postServiceSynchronously(
                    LoadBalancerService.FACTORY_LINK,
                    startState, LoadBalancerState.class);

            assertNotNull(returnState);
            assertThat(returnState.id, is(startState.id));
            assertThat(returnState.name, is(startState.name));
            assertThat(returnState.endpointLink, is(startState.endpointLink));
            assertThat(returnState.regionId, is(startState.regionId));
            assertThat(returnState.tenantLinks.get(0),
                    is(startState.tenantLinks.get(0)));
        }

        @Test
        public void testDuplicatePost() throws Throwable {
            LoadBalancerState startState = buildValidStartState();
            LoadBalancerState returnState = postServiceSynchronously(
                    LoadBalancerService.FACTORY_LINK,
                    startState, LoadBalancerState.class);

            assertNotNull(returnState);
            assertThat(returnState.name, is(startState.name));
            startState.name = "new-name";
            returnState = postServiceSynchronously(LoadBalancerService.FACTORY_LINK,
                    startState, LoadBalancerState.class);
            assertThat(returnState.name, is(startState.name));
        }

        @Test
        public void testInvalidValues() throws Throwable {
            LoadBalancerState missingRegionId = buildValidStartState();
            LoadBalancerState missingZoneId = buildValidStartState();
            LoadBalancerState missingEndpointLink = buildValidStartState();
            LoadBalancerState missingProtocol = buildValidStartState();
            LoadBalancerState missingPort = buildValidStartState();
            LoadBalancerState missingInstanceProtocol = buildValidStartState();
            LoadBalancerState missingInstancePort = buildValidStartState();
            LoadBalancerState invalidPort = buildValidStartState();
            LoadBalancerState invalidInstancePort = buildValidStartState();

            missingRegionId.regionId = null;
            missingZoneId.zoneId = null;
            missingEndpointLink.endpointLink = null;
            missingProtocol.protocol = null;
            missingPort.port = null;
            missingInstanceProtocol.instanceProtocol = null;
            missingInstancePort.instancePort = null;
            invalidPort.port = LoadBalancerService.MIN_PORT_NUMBER - 1;
            invalidInstancePort.instancePort = LoadBalancerService.MAX_PORT_NUMBER + 1;

            LoadBalancerState[] states = { missingRegionId, missingZoneId, missingEndpointLink,
                    missingProtocol, missingPort, missingInstanceProtocol, missingInstancePort,
                    invalidPort, invalidInstancePort };
            for (LoadBalancerState state : states) {
                postServiceSynchronously(LoadBalancerService.FACTORY_LINK,
                        state, LoadBalancerState.class,
                        IllegalArgumentException.class);
            }
        }
    }

    /**
     * This class implements tests for the handlePatch method.
     */
    public static class HandlePatchTest extends BaseModelTest {
        @Test
        public void testPatch() throws Throwable {
            LoadBalancerState startState = buildValidStartState();

            LoadBalancerState returnState = postServiceSynchronously(
                    LoadBalancerService.FACTORY_LINK,
                    startState, LoadBalancerState.class);

            LoadBalancerState patchState = new LoadBalancerState();
            patchState.name = "patchNetworkName";
            patchState.endpointLink = EndpointService.FACTORY_LINK + "/new-endpoint";
            patchState.customProperties = new HashMap<>();
            patchState.customProperties.put("patchKey", "patchValue");
            patchState.tenantLinks = new ArrayList<String>();
            patchState.tenantLinks.add("tenant1");
            patchState.groupLinks = new HashSet<String>();
            patchState.groupLinks.add("group1");
            patchState.regionId = "new-region";
            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);

            returnState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    LoadBalancerState.class);

            assertThat(returnState.name, is(patchState.name));
            assertThat(returnState.endpointLink, is(startState.endpointLink)); // no change
            assertThat(returnState.customProperties,
                    is(patchState.customProperties));
            assertEquals(returnState.tenantLinks.size(), 2);
            assertEquals(returnState.groupLinks, patchState.groupLinks);
            assertEquals(returnState.regionId, patchState.regionId);
        }
    }

    /**
     * This class implements tests for query.
     */
    public static class QueryTest extends BaseModelTest {
        @Test
        public void testTenantLinksQuery() throws Throwable {
            LoadBalancerState LoadBalancerState = buildValidStartState();
            URI tenantUri = UriUtils.buildFactoryUri(this.host, TenantService.class);
            LoadBalancerState.tenantLinks = new ArrayList<>();
            LoadBalancerState.tenantLinks.add(UriUtils.buildUriPath(
                    tenantUri.getPath(), "tenantA"));
            LoadBalancerState startState = postServiceSynchronously(
                    LoadBalancerService.FACTORY_LINK,
                    LoadBalancerState, LoadBalancerState.class);

            String kind = Utils.buildKind(LoadBalancerState.class);
            String propertyName = QueryTask.QuerySpecification
                    .buildCollectionItemName(ResourceState.FIELD_NAME_TENANT_LINKS);

            QueryTask q = createDirectQueryTask(kind, propertyName,
                    LoadBalancerState.tenantLinks.get(0));
            q = querySynchronously(q);
            assertNotNull(q.results.documentLinks);
            assertThat(q.results.documentCount, is(1L));
            assertThat(q.results.documentLinks.get(0),
                    is(startState.documentSelfLink));
        }
    }

}
