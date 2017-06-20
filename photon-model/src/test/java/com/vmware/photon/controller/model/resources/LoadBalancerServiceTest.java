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
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService.LoadBalancerDescription.HealthCheckConfiguration;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService.LoadBalancerDescription.Protocol;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService.LoadBalancerDescription.RouteConfiguration;
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

    public static LoadBalancerState buildValidStartState() {
        LoadBalancerState loadBalancerState = new LoadBalancerState();
        loadBalancerState.descriptionLink = LoadBalancerDescriptionService.FACTORY_LINK + "/lb-desc";
        loadBalancerState.id = UUID.randomUUID().toString();
        loadBalancerState.name = "lbName";
        loadBalancerState.endpointLink = EndpointService.FACTORY_LINK + "/my-endpoint";
        loadBalancerState.computeLinks = new HashSet<>();
        loadBalancerState.computeLinks.add(ComputeService.FACTORY_LINK + "/a-compute");
        loadBalancerState.subnetLinks = new HashSet<>();
        loadBalancerState.subnetLinks.add(SubnetService.FACTORY_LINK + "/a-subnet");
        loadBalancerState.regionId = "regionId";
        loadBalancerState.tenantLinks = new ArrayList<>();
        loadBalancerState.tenantLinks.add("tenant-linkA");

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

        loadBalancerState.routes = Arrays.asList(route1, route2);

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
            LoadBalancerState missingEndpointLink = buildValidStartState();
            LoadBalancerState missingComputeLinks = buildValidStartState();
            LoadBalancerState missingSubnetLinks = buildValidStartState();
            LoadBalancerState missingRoutes = buildValidStartState();
            LoadBalancerState missingProtocol = buildValidStartState();
            LoadBalancerState missingPort = buildValidStartState();
            LoadBalancerState missingInstanceProtocol = buildValidStartState();
            LoadBalancerState missingInstancePort = buildValidStartState();
            LoadBalancerState invalidPortString = buildValidStartState();
            LoadBalancerState invalidPortNumber = buildValidStartState();
            LoadBalancerState invalidInstancePortString = buildValidStartState();
            LoadBalancerState invalidInstancePortNumber = buildValidStartState();
            LoadBalancerState missingHealthProtocol = buildValidStartState();
            LoadBalancerState missingHealthPort = buildValidStartState();
            LoadBalancerState invalidHealthPortString = buildValidStartState();
            LoadBalancerState invalidHealthPortNumber = buildValidStartState();

            missingEndpointLink.endpointLink = null;
            missingComputeLinks.computeLinks = null;
            missingSubnetLinks.subnetLinks = null;
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

            {
                LoadBalancerState[] states = { missingEndpointLink, missingComputeLinks,
                        missingSubnetLinks, missingRoutes, missingProtocol, missingPort,
                        missingInstanceProtocol, missingInstancePort, invalidPortNumber,
                        invalidInstancePortNumber, missingHealthProtocol, missingHealthPort,
                        invalidHealthPortNumber };
                for (LoadBalancerState state : states) {
                    postServiceSynchronously(LoadBalancerService.FACTORY_LINK,
                            state, LoadBalancerState.class,
                            IllegalArgumentException.class);
                }
            }

            {
                LoadBalancerState[] states = { invalidPortString, invalidInstancePortString,
                        invalidHealthPortString };
                for (LoadBalancerState state : states) {
                    postServiceSynchronously(LoadBalancerService.FACTORY_LINK,
                            state, LoadBalancerState.class,
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
            LoadBalancerState startState = buildValidStartState();

            LoadBalancerState returnState = postServiceSynchronously(
                    LoadBalancerService.FACTORY_LINK,
                    startState, LoadBalancerState.class);

            LoadBalancerState patchState = new LoadBalancerState();
            patchState.descriptionLink = LoadBalancerDescriptionService.FACTORY_LINK + "/new-desc";
            patchState.name = "patchNetworkName";
            patchState.endpointLink = EndpointService.FACTORY_LINK + "/new-endpoint";
            patchState.computeLinks = new HashSet<>();
            patchState.computeLinks.add(ComputeService.FACTORY_LINK + "/b-compute");
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
            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);

            returnState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    LoadBalancerState.class);

            assertThat(returnState.descriptionLink, is(startState.descriptionLink)); // no change
            assertThat(returnState.name, is(patchState.name));
            assertThat(returnState.endpointLink, is(startState.endpointLink)); // no change
            assertThat(returnState.computeLinks.size(), is(2));
            assertThat(returnState.subnetLinks.size(), is(2));
            assertThat(returnState.customProperties,
                    is(patchState.customProperties));
            assertThat(returnState.tenantLinks.size(), is(2));
            assertEquals(returnState.groupLinks, patchState.groupLinks);
            // region ID should not be updated
            assertEquals(returnState.regionId, startState.regionId);
            assertEquals(1, returnState.routes.size());
            assertEquals(2, startState.routes.size());
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
