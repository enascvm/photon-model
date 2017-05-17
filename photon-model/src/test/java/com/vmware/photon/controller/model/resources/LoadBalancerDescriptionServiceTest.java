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
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService.LoadBalancerDescription;
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

    private static LoadBalancerDescription buildValidStartState() {
        LoadBalancerDescription state = new LoadBalancerDescription();
        state.id = UUID.randomUUID().toString();
        state.name = "networkName";
        state.endpointLink = EndpointService.FACTORY_LINK + "/my-endpoint";
        state.computeDescriptionLink = ComputeDescriptionService.FACTORY_LINK + "/a-compute-desc";
        state.subnetLinks = new HashSet<>();
        state.subnetLinks.add(SubnetService.FACTORY_LINK + "/a-subnet");
        state.regionId = "regionId";
        state.protocol = "HTTP";
        state.port = 80;
        state.instanceProtocol = "HTTP";
        state.instancePort = 80;
        state.tenantLinks = new ArrayList<>();
        state.tenantLinks.add("tenant-linkA");

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
            LoadBalancerDescription missingEndpointLink = buildValidStartState();
            LoadBalancerDescription missingComputeDescriptionLink = buildValidStartState();
            LoadBalancerDescription missingSubnetLinks = buildValidStartState();
            LoadBalancerDescription missingProtocol = buildValidStartState();
            LoadBalancerDescription missingPort = buildValidStartState();
            LoadBalancerDescription missingInstanceProtocol = buildValidStartState();
            LoadBalancerDescription missingInstancePort = buildValidStartState();
            LoadBalancerDescription invalidPort = buildValidStartState();
            LoadBalancerDescription invalidInstancePort = buildValidStartState();

            missingEndpointLink.endpointLink = null;
            missingComputeDescriptionLink.computeDescriptionLink = null;
            missingSubnetLinks.subnetLinks = null;
            missingProtocol.protocol = null;
            missingPort.port = null;
            missingInstanceProtocol.instanceProtocol = null;
            missingInstancePort.instancePort = null;
            invalidPort.port = LoadBalancerDescriptionService.MIN_PORT_NUMBER - 1;
            invalidInstancePort.instancePort = LoadBalancerDescriptionService.MAX_PORT_NUMBER + 1;

            LoadBalancerDescription[] states = { missingEndpointLink,
                    missingComputeDescriptionLink, missingSubnetLinks,  missingProtocol,
                    missingPort, missingInstanceProtocol, missingInstancePort, invalidPort,
                    invalidInstancePort };
            for (LoadBalancerDescription state : states) {
                postServiceSynchronously(LoadBalancerDescriptionService.FACTORY_LINK,
                        state, LoadBalancerDescription.class,
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
            LoadBalancerDescription startState = buildValidStartState();

            LoadBalancerDescription returnState = postServiceSynchronously(
                    LoadBalancerDescriptionService.FACTORY_LINK,
                    startState, LoadBalancerDescription.class);

            LoadBalancerDescription patchState = new LoadBalancerDescription();
            patchState.name = "patchNetworkName";
            patchState.endpointLink = EndpointService.FACTORY_LINK + "/new-endpoint";
            patchState.computeDescriptionLink = ComputeDescriptionService.FACTORY_LINK
                    + "/b-compute-desc";
            patchState.subnetLinks = new HashSet<>();
            patchState.subnetLinks.add(SubnetService.FACTORY_LINK + "/b-subnet");
            patchState.customProperties = new HashMap<>();
            patchState.customProperties.put("patchKey", "patchValue");
            patchState.tenantLinks = new ArrayList<String>();
            patchState.tenantLinks.add("tenant1");
            patchState.groupLinks = new HashSet<String>();
            patchState.groupLinks.add("group1");
            patchState.regionId = "new-region";
            patchServiceSynchronously(returnState.documentSelfLink, patchState);

            returnState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    LoadBalancerDescription.class);

            assertThat(returnState.name, is(patchState.name));
            assertThat(returnState.endpointLink, is(patchState.endpointLink));
            assertThat(returnState.computeDescriptionLink, is(patchState.computeDescriptionLink));
            assertThat(returnState.subnetLinks.size(), is(2));
            assertThat(returnState.customProperties,
                    is(patchState.customProperties));
            assertThat(returnState.tenantLinks.size(), is(2));
            assertEquals(returnState.groupLinks, patchState.groupLinks);
            // region ID should not be updated
            assertEquals(returnState.regionId, startState.regionId);
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
