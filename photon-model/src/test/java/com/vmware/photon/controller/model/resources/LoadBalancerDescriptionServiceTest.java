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
import java.util.Arrays;
import java.util.Collections;
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
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService.LoadBalancerDescription;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService.LoadBalancerDescription.HealthCheckConfiguration;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService.LoadBalancerDescription.Protocol;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService.LoadBalancerDescription.RouteConfiguration;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.TenantService;

/**
 * This class implements tests for the {@link LoadBalancerDescriptionService} class.
 */
@RunWith(LoadBalancerDescriptionServiceTest.class)
@SuiteClasses({ LoadBalancerDescriptionServiceTest.ConstructorTest.class,
        LoadBalancerDescriptionServiceTest.HandleStartTest.class,
        LoadBalancerDescriptionServiceTest.HandlePatchTest.class,
        LoadBalancerDescriptionServiceTest.QueryTest.class })
public class LoadBalancerDescriptionServiceTest extends Suite {

    public LoadBalancerDescriptionServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    public static LoadBalancerDescription buildValidStartState() {
        LoadBalancerDescription state = new LoadBalancerDescription();
        state.id = UUID.randomUUID().toString();
        state.name = "lbName";
        state.endpointLink = EndpointService.FACTORY_LINK + "/my-endpoint";
        state.computeDescriptionLinks = Arrays.asList(ComputeDescriptionService.FACTORY_LINK +
                "/a-compute-desc");
        state.networkName = "lb-net";
        state.regionId = "regionId";
        state.tenantLinks = new ArrayList<>();
        state.tenantLinks.add("tenant-linkA");

        RouteConfiguration route1 = new RouteConfiguration();
        route1.protocol = Protocol.HTTP.name();
        route1.port = "80";
        route1.instanceProtocol = Protocol.HTTP.name();
        route1.instancePort = "80";
        route1.healthCheckConfiguration = new HealthCheckConfiguration();
        route1.healthCheckConfiguration.protocol = Protocol.HTTP.name();
        route1.healthCheckConfiguration.port = "80";

        RouteConfiguration route2 = new RouteConfiguration();
        route2.protocol = Protocol.HTTPS.name();
        route2.port = "443";
        route2.instanceProtocol = Protocol.HTTP.name();
        route2.instancePort = "443";

        state.routes = Arrays.asList(route1, route2);

        return state;
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {
        private LoadBalancerDescriptionService loadBalancerDescriptionService =
                new LoadBalancerDescriptionService();

        @Before
        public void setupTest() {
            this.loadBalancerDescriptionService = new LoadBalancerDescriptionService();
        }

        @Test
        public void testServiceOptions() {
            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.CONCURRENT_GET_HANDLING,
                    Service.ServiceOption.PERSISTENCE,
                    Service.ServiceOption.REPLICATION,
                    Service.ServiceOption.OWNER_SELECTION,
                    Service.ServiceOption.IDEMPOTENT_POST);
            assertThat(this.loadBalancerDescriptionService.getOptions(), is(expected));
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleStartTest extends BaseModelTest {
        @Test
        public void testValidStartState() throws Throwable {
            LoadBalancerDescription startState = buildValidStartState();
            LoadBalancerDescription returnState = postServiceSynchronously(
                    LoadBalancerDescriptionService.FACTORY_LINK,
                    startState, LoadBalancerDescription.class);

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
            LoadBalancerDescription startState = buildValidStartState();
            LoadBalancerDescription returnState = postServiceSynchronously(
                    LoadBalancerDescriptionService.FACTORY_LINK,
                    startState, LoadBalancerDescription.class);

            assertNotNull(returnState);
            assertThat(returnState.name, is(startState.name));
            startState.name = "new-name";
            returnState = postServiceSynchronously(LoadBalancerDescriptionService.FACTORY_LINK,
                    startState, LoadBalancerDescription.class);
            assertThat(returnState.name, is(startState.name));
        }

        @Test
        public void testInvalidValues() throws Throwable {
            LoadBalancerDescription missingRoutes = buildValidStartState();
            LoadBalancerDescription missingProtocol = buildValidStartState();
            LoadBalancerDescription missingPort = buildValidStartState();
            LoadBalancerDescription missingInstanceProtocol = buildValidStartState();
            LoadBalancerDescription missingInstancePort = buildValidStartState();
            LoadBalancerDescription invalidPortString = buildValidStartState();
            LoadBalancerDescription invalidPortNumber = buildValidStartState();
            LoadBalancerDescription invalidInstancePortString = buildValidStartState();
            LoadBalancerDescription invalidInstancePortNumber = buildValidStartState();
            LoadBalancerDescription missingHealthProtocol = buildValidStartState();
            LoadBalancerDescription missingHealthPort = buildValidStartState();
            LoadBalancerDescription invalidHealthPortString = buildValidStartState();
            LoadBalancerDescription invalidHealthPortNumber = buildValidStartState();
            LoadBalancerDescription bothNetworkAndSubnetsSet = buildValidStartState();
            LoadBalancerDescription noNetworkAndSubnetsSet = buildValidStartState();

            missingRoutes.routes = null;
            missingProtocol.routes.get(0).protocol = null;
            missingPort.routes.get(0).port = null;
            missingInstanceProtocol.routes.get(0).instanceProtocol = null;
            missingInstancePort.routes.get(0).instancePort = null;
            invalidPortString.routes.get(0).port = "text";
            invalidPortNumber.routes.get(0).port =
                    "" + (LoadBalancerDescriptionService.MIN_PORT_NUMBER - 1);
            invalidInstancePortString.routes.get(0).port = "random string";
            invalidInstancePortNumber.routes.get(0).instancePort =
                    "" + (LoadBalancerDescriptionService.MAX_PORT_NUMBER + 1);
            missingHealthProtocol.routes.get(0).healthCheckConfiguration.protocol = null;
            missingHealthPort.routes.get(0).healthCheckConfiguration.port = null;
            invalidHealthPortString.routes.get(0).healthCheckConfiguration.port = "20-30";
            invalidHealthPortNumber.routes.get(0).healthCheckConfiguration.port = "100000";
            bothNetworkAndSubnetsSet.subnetLinks = Collections
                    .singleton(SubnetService.FACTORY_LINK + "/a-subnet");
            noNetworkAndSubnetsSet.networkName = null;

            {
                LoadBalancerDescription[] states = { missingRoutes,
                        missingProtocol, missingPort, missingInstanceProtocol, missingInstancePort,
                        invalidPortNumber, invalidInstancePortNumber, missingHealthProtocol,
                        missingHealthPort, invalidHealthPortNumber, bothNetworkAndSubnetsSet,
                        noNetworkAndSubnetsSet };
                for (LoadBalancerDescription state : states) {
                    postServiceSynchronously(LoadBalancerDescriptionService.FACTORY_LINK,
                            state, LoadBalancerDescription.class,
                            IllegalArgumentException.class);
                }
            }

            {
                LoadBalancerDescription[] states = { invalidPortString, invalidInstancePortString,
                        invalidHealthPortString };
                for (LoadBalancerDescription state : states) {
                    postServiceSynchronously(LoadBalancerDescriptionService.FACTORY_LINK,
                            state, LoadBalancerDescription.class,
                            NumberFormatException.class);
                }
            }
        }
    }

    /**
     * This class implements tests for the handlePatch method.
     */
    public static class HandlePatchTest extends BaseModelTest {
        @Test
        public void testPatch() throws Throwable {
            LoadBalancerDescription startState = buildValidStartState();

            LoadBalancerDescription returnState = postServiceSynchronously(
                    LoadBalancerDescriptionService.FACTORY_LINK,
                    startState, LoadBalancerDescription.class);

            LoadBalancerDescription patchState = new LoadBalancerDescription();
            patchState.name = "patchNetworkName";
            patchState.endpointLink = EndpointService.FACTORY_LINK + "/new-endpoint";
            patchState.computeDescriptionLinks = Arrays
                    .asList(ComputeDescriptionService.FACTORY_LINK + "/b-compute-desc");
            patchState.subnetLinks = new HashSet<>();
            patchState.subnetLinks.add(SubnetService.FACTORY_LINK + "/b-subnet");
            patchState.customProperties = new HashMap<>();
            patchState.customProperties.put("patchKey", "patchValue");
            patchState.tenantLinks = new ArrayList<String>();
            patchState.tenantLinks.add("tenant1");
            patchState.groupLinks = new HashSet<String>();
            patchState.groupLinks.add("group1");
            patchState.regionId = "new-region";
            patchState.routes = Arrays.asList(startState.routes.get(1));
            patchServiceSynchronously(returnState.documentSelfLink, patchState);

            returnState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    LoadBalancerDescription.class);

            assertThat(returnState.name, is(patchState.name));
            assertThat(returnState.endpointLink, is(patchState.endpointLink));
            assertThat(returnState.computeDescriptionLinks, is(Arrays.asList(
                    ComputeDescriptionService.FACTORY_LINK + "/a-compute-desc",
                    ComputeDescriptionService.FACTORY_LINK + "/b-compute-desc")));
            assertThat(returnState.subnetLinks.size(), is(1));
            assertThat(returnState.customProperties,
                    is(patchState.customProperties));
            assertThat(returnState.tenantLinks.size(), is(2));
            assertEquals(returnState.groupLinks, patchState.groupLinks);
            // region ID must not be changed
            assertEquals(returnState.regionId, startState.regionId);
            assertEquals(1, returnState.routes.size());
            assertEquals(2, startState.routes.size());
        }

        @Test
        public void testRegionIdPatch() throws Throwable {
            LoadBalancerDescription startState = buildValidStartState();
            startState.regionId = null;

            LoadBalancerDescription returnState = postServiceSynchronously(
                    LoadBalancerDescriptionService.FACTORY_LINK,
                    startState, LoadBalancerDescription.class);

            LoadBalancerDescription patchState = new LoadBalancerDescription();
            patchState.regionId = "new-region";
            patchServiceSynchronously(returnState.documentSelfLink, patchState);

            returnState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    LoadBalancerDescription.class);

            // region ID must be set
            assertEquals(patchState.regionId, returnState.regionId);
        }
    }

    /**
     * This class implements tests for query.
     */
    public static class QueryTest extends BaseModelTest {
        @Test
        public void testTenantLinksQuery() throws Throwable {
            LoadBalancerDescription LoadBalancerDescription = buildValidStartState();
            URI tenantUri = UriUtils.buildFactoryUri(this.host, TenantService.class);
            LoadBalancerDescription.tenantLinks = new ArrayList<>();
            LoadBalancerDescription.tenantLinks.add(UriUtils.buildUriPath(
                    tenantUri.getPath(), "tenantA"));
            LoadBalancerDescription startState = postServiceSynchronously(
                    LoadBalancerDescriptionService.FACTORY_LINK,
                    LoadBalancerDescription, LoadBalancerDescription.class);

            String kind = Utils.buildKind(LoadBalancerDescription.class);
            String propertyName = QueryTask.QuerySpecification
                    .buildCollectionItemName(ResourceState.FIELD_NAME_TENANT_LINKS);

            QueryTask q = createDirectQueryTask(kind, propertyName,
                    LoadBalancerDescription.tenantLinks.get(0));
            q = querySynchronously(q);
            assertNotNull(q.results.documentLinks);
            assertThat(q.results.documentCount, is(1L));
            assertThat(q.results.documentLinks.get(0),
                    is(startState.documentSelfLink));
        }
    }

}
