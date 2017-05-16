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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceStateCollectionUpdateRequest;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.TenantService;

/**
 * This class implements tests for the {@link ComputeService} class.
 */
@RunWith(ComputeServiceTest.class)
@SuiteClasses({ ComputeServiceTest.ConstructorTest.class,
        ComputeServiceTest.HandleStartTest.class,
        ComputeServiceTest.HandleGetTest.class,
        ComputeServiceTest.HandlePatchTest.class,
        ComputeServiceTest.HandlePutTest.class,
        ComputeServiceTest.QueryTest.class })
public class ComputeServiceTest extends Suite {
    private static final String TEST_DESC_PROPERTY_NAME = "testDescProperty";
    private static final String TEST_DESC_PROPERTY_VALUE = UUID.randomUUID()
            .toString();

    public ComputeServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    public static ComputeService.ComputeStateWithDescription buildValidStartState(
            ComputeDescriptionService.ComputeDescription cd) throws Throwable {
        ComputeService.ComputeStateWithDescription cs = new ComputeService.ComputeStateWithDescription();
        cs.id = UUID.randomUUID().toString();
        cs.description = cd;
        cs.descriptionLink = cd.documentSelfLink;
        cs.resourcePoolLink = null;
        cs.address = "10.0.0.1";
        cs.primaryMAC = "01:23:45:67:89:ab";
        cs.powerState = ComputeService.PowerState.ON;
        cs.name = "testVM";
        cs.type = ComputeType.VM_GUEST;
        cs.cpuMhzPerCore = 1000L;
        cs.cpuCount = 2L;
        cs.gpuCount = 1L;
        cs.totalMemoryBytes = new Long(Integer.MAX_VALUE);
        cs.environmentName = ComputeDescription.ENVIRONMENT_NAME_ON_PREMISE;

        cs.adapterManagementReference = URI
                .create("https://esxhost-01:443/sdk");
        cs.diskLinks = new ArrayList<>();
        cs.diskLinks.add("http://disk");
        cs.networkInterfaceLinks = new ArrayList<>();
        cs.networkInterfaceLinks.add("http://network");
        cs.customProperties = new HashMap<>();
        cs.customProperties.put(TEST_DESC_PROPERTY_NAME,
                TEST_DESC_PROPERTY_VALUE);
        return cs;
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {

        private ComputeService computeService;

        @Before
        public void setUpTest() {
            this.computeService = new ComputeService();
        }

        @Test
        public void testServiceOptions() {

            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.CONCURRENT_GET_HANDLING,
                    Service.ServiceOption.PERSISTENCE,
                    Service.ServiceOption.REPLICATION,
                    Service.ServiceOption.OWNER_SELECTION,
                    Service.ServiceOption.IDEMPOTENT_POST);

            assertThat(this.computeService.getOptions(), is(expected));
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleStartTest extends BaseModelTest {

        public final long startTimeMicros = Utils.getNowMicrosUtc();

        @Test
        public void testDuplicatePost() throws Throwable {
            ComputeDescriptionService.ComputeDescription cd = ComputeDescriptionServiceTest
                    .createComputeDescription(this);
            ComputeService.ComputeState startState = ComputeServiceTest
                    .buildValidStartState(cd);
            ComputeService.ComputeState returnState = postServiceSynchronously(
                    ComputeService.FACTORY_LINK, startState, ComputeService.ComputeState.class);

            assertNotNull(returnState);
            assertThat(returnState.address, is(startState.address));
            startState.address = "new-address";
            returnState = postServiceSynchronously(ComputeService.FACTORY_LINK,
                            startState, ComputeService.ComputeState.class);
            assertThat(returnState.address, is(startState.address));

        }

        @Test
        public void testMissingId() throws Throwable {
            ComputeDescriptionService.ComputeDescription cd = ComputeDescriptionServiceTest
                    .createComputeDescription(this);
            ComputeService.ComputeState startState = buildValidStartState(cd);
            startState.id = null;

            ComputeService.ComputeState returnState = postServiceSynchronously(
                    ComputeService.FACTORY_LINK, startState, ComputeService.ComputeState.class);

            assertNotNull(returnState);
            assertNotNull(returnState.id);
        }

        @Test
        public void testMissingDescriptionLink() throws Throwable {
            ComputeDescriptionService.ComputeDescription cd = ComputeDescriptionServiceTest
                    .createComputeDescription(this);
            ComputeService.ComputeState startState = buildValidStartState(cd);
            startState.powerState = ComputeService.PowerState.OFF;
            startState.descriptionLink = null;

            postServiceSynchronously(ComputeService.FACTORY_LINK,
                    startState, ComputeService.ComputeState.class,
                    IllegalArgumentException.class);
        }

        @Test
        public void testMissingCreationTimeMicros() throws Throwable {
            ComputeDescriptionService.ComputeDescription cd = ComputeDescriptionServiceTest
                    .createComputeDescription(this);
            ComputeService.ComputeState startState = buildValidStartState(cd);
            startState.creationTimeMicros = null;

            ComputeService.ComputeState returnState = postServiceSynchronously(
                    ComputeService.FACTORY_LINK, startState, ComputeService.ComputeState.class);

            assertNotNull(returnState);
            assertNull(returnState.creationTimeMicros);
        }

        @Test
        public void testProvidedCreationTimeMicros() throws Throwable {
            ComputeDescriptionService.ComputeDescription cd = ComputeDescriptionServiceTest
                    .createComputeDescription(this);
            ComputeService.ComputeState startState = buildValidStartState(cd);
            startState.creationTimeMicros = Long.MIN_VALUE;

            ComputeService.ComputeState returnState = postServiceSynchronously(
                    ComputeService.FACTORY_LINK, startState, ComputeService.ComputeState.class);

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
            ComputeDescriptionService.ComputeDescription cd = ComputeDescriptionServiceTest
                    .createComputeDescription(this);
            ComputeService.ComputeState startState = buildValidStartState(cd);

            ComputeService.ComputeState returnState = postServiceSynchronously(
                    ComputeService.FACTORY_LINK, startState, ComputeService.ComputeState.class);
            assertNotNull(returnState);

            ComputeService.ComputeState getState = getServiceSynchronously(
                    returnState.documentSelfLink, ComputeService.ComputeState.class);

            assertThat(getState.id, is(startState.id));
            assertThat(getState.descriptionLink, is(startState.descriptionLink));
            assertThat(getState.address, is(startState.address));
            assertThat(getState.primaryMAC, is(startState.primaryMAC));
            assertThat(getState.powerState, is(startState.powerState));
            assertThat(getState.adapterManagementReference,
                    is(startState.adapterManagementReference));
        }

        @Test
        public void testGetExpand() throws Throwable {
            ComputeDescriptionService.ComputeDescription cd = ComputeDescriptionServiceTest
                    .createComputeDescription(this);
            ComputeService.ComputeStateWithDescription startState = buildValidStartState(cd);
            startState.creationTimeMicros = Long.MIN_VALUE;

            ComputeService.ComputeState returnState = postServiceSynchronously(
                    ComputeService.FACTORY_LINK,
                            startState, ComputeService.ComputeState.class);
            assertNotNull(returnState);

            ComputeService.ComputeStateWithDescription  getState = getServiceSynchronously(
                    UriUtils.extendUriWithQuery(UriUtils.buildUri(returnState.documentSelfLink),
                            UriUtils.URI_PARAM_ODATA_EXPAND_NO_DOLLAR_SIGN, Boolean.TRUE.toString()).toString(),
                    ComputeService.ComputeStateWithDescription.class);

            assertThat(getState.id, is(startState.id));
            assertNotNull(getState.description);
            assertThat(getState.description.id, is(startState.description.id));
            assertThat(getState.description.name,
                    is(startState.description.name));
            assertNotNull(returnState.creationTimeMicros);
            assertEquals(Long.MIN_VALUE, returnState.creationTimeMicros.longValue());

            getState = getServiceSynchronously(
                    UriUtils.extendUriWithQuery(UriUtils.buildUri(returnState.documentSelfLink),
                            UriUtils.URI_PARAM_ODATA_EXPAND, Boolean.TRUE.toString()).toString(),
                    ComputeService.ComputeStateWithDescription.class);

            assertThat(getState.id, is(startState.id));
            assertNotNull(getState.description);
            assertThat(getState.description.id, is(startState.description.id));
            assertThat(getState.description.name,
                    is(startState.description.name));
            assertNotNull(getState.creationTimeMicros);
            assertEquals(Long.MIN_VALUE, getState.creationTimeMicros.longValue());
        }
    }

    /**
     * This class implements tests for the handlePatch method.
     */
    public static class HandlePatchTest extends BaseModelTest {

        @Test
        public void testPatch() throws Throwable {
            ComputeDescriptionService.ComputeDescription cd = ComputeDescriptionServiceTest
                    .createComputeDescription(this);
            ComputeService.ComputeState startState = buildValidStartState(cd);

            ComputeService.ComputeState returnState = postServiceSynchronously(
                    ComputeService.FACTORY_LINK,
                            startState, ComputeService.ComputeState.class);
            assertNotNull(returnState);

            ComputeService.ComputeState patchBody = new ComputeService.ComputeState();
            patchBody.id = UUID.randomUUID().toString();
            patchBody.address = "10.0.0.2";
            patchBody.powerState = ComputeService.PowerState.OFF;
            patchBody.primaryMAC = "ba:98:76:54:32:10";
            patchBody.resourcePoolLink = "http://newResourcePool";
            patchBody.adapterManagementReference = URI
                    .create("http://newAdapterManagementReference");
            patchBody.tenantLinks = new ArrayList<>();
            patchBody.tenantLinks.add("tenant1");
            patchBody.groupLinks = new HashSet<>();
            patchBody.groupLinks.add("group1");
            patchServiceSynchronously(returnState.documentSelfLink,
                    patchBody);

            ComputeService.ComputeStateWithDescription getState = getServiceSynchronously(
                    returnState.documentSelfLink,
                            ComputeService.ComputeStateWithDescription.class);

            assertThat(getState.id, is(patchBody.id));
            assertThat(getState.address, is(patchBody.address));
            assertThat(getState.powerState, is(patchBody.powerState));
            assertThat(getState.primaryMAC, is(patchBody.primaryMAC));
            assertThat(getState.resourcePoolLink,
                    is(patchBody.resourcePoolLink));
            assertThat(getState.adapterManagementReference,
                    is(patchBody.adapterManagementReference));
            assertEquals(getState.tenantLinks, patchBody.tenantLinks);
            assertEquals(getState.groupLinks, patchBody.groupLinks);
            // make sure launchTimeMicros was preserved
            assertEquals(getState.creationTimeMicros, returnState.creationTimeMicros);
        }

        @Test(expected = IllegalArgumentException.class)
        public void testPatchFailOnTypeChange() throws Throwable {
            ComputeDescriptionService.ComputeDescription cd = ComputeDescriptionServiceTest
                    .createComputeDescription(this);
            ComputeService.ComputeState startState = buildValidStartState(cd);

            ComputeService.ComputeState returnState = postServiceSynchronously(
                    ComputeService.FACTORY_LINK,
                    startState, ComputeService.ComputeState.class);
            assertNotNull(returnState);

            ComputeService.ComputeState patchBody = new ComputeService.ComputeState();
            patchBody.type = ComputeType.DOCKER_CONTAINER;
            patchServiceSynchronously(returnState.documentSelfLink,
                    patchBody);
        }

        @Test
        public void testPatchNoChange() throws Throwable {
            ComputeDescriptionService.ComputeDescription cd = ComputeDescriptionServiceTest
                    .createComputeDescription(this);
            ComputeService.ComputeState startState = buildValidStartState(cd);

            ComputeService.ComputeState returnState = postServiceSynchronously(
                    ComputeService.FACTORY_LINK,
                            startState, ComputeService.ComputeState.class);
            assertNotNull(returnState);

            ComputeService.ComputeState patchBody = new ComputeService.ComputeState();
            patchServiceSynchronously(returnState.documentSelfLink,
                    patchBody);

            ComputeService.ComputeStateWithDescription getState = getServiceSynchronously(
                    returnState.documentSelfLink,
                            ComputeService.ComputeStateWithDescription.class);

            assertThat(getState.id, is(startState.id));
            assertThat(getState.address, is(startState.address));
            assertThat(getState.powerState, is(startState.powerState));
            assertThat(getState.primaryMAC, is(startState.primaryMAC));
            assertThat(getState.resourcePoolLink,
                    is(startState.resourcePoolLink));
            assertThat(getState.adapterManagementReference,
                    is(startState.adapterManagementReference));
        }

        @Test
        public void testPatchRemoveNetworkLinks() throws Throwable {
            ComputeDescriptionService.ComputeDescription cd = ComputeDescriptionServiceTest
                    .createComputeDescription(this);
            ComputeService.ComputeState startState = buildValidStartState(cd);
            // startState has four networkInterfaceLinks: network, network1, network2, network3
            startState.networkInterfaceLinks.add("http://network1");
            startState.networkInterfaceLinks.add("http://network2");
            startState.networkInterfaceLinks.add("http://network3");

            ComputeService.ComputeState returnState = postServiceSynchronously(
                    ComputeService.FACTORY_LINK,
                    startState, ComputeService.ComputeState.class);
            assertNotNull(returnState);

            ComputeService.ComputeState patchBody = new ComputeService.ComputeState();

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchBody);

            Map<String, Collection<Object>>collectionsMap = new HashMap<>();
            Collection<Object> networkLinksToBeRemoved = new ArrayList<>(Arrays.asList(
                    "http://network1", "http://network3"));
            collectionsMap.put("networkInterfaceLinks", networkLinksToBeRemoved);
            ServiceStateCollectionUpdateRequest collectionRemovalBody =
                    ServiceStateCollectionUpdateRequest.create(null, collectionsMap);

            // send PATCH to remove networkInterfaceLinks: network1, network3
            patchServiceSynchronously(returnState.documentSelfLink,
                    collectionRemovalBody);

            ComputeService.ComputeStateWithDescription getState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    ComputeService.ComputeStateWithDescription.class);

            List<String> expectedNetworkLinks = new ArrayList<>(Arrays.asList(
                    "http://network", "http://network2"));

            assertThat(getState.id, is(startState.id));
            assertEquals(getState.networkInterfaceLinks, expectedNetworkLinks);
        }
    }

    /**
     * This class implements tests for the handlePut method.
     */
    public static class HandlePutTest extends BaseModelTest {

        @Test
        public void testPut() throws Throwable {
            ComputeDescriptionService.ComputeDescription cd = ComputeDescriptionServiceTest
                    .createComputeDescription(this);
            ComputeService.ComputeState startState = buildValidStartState(cd);
            startState.creationTimeMicros = Long.MIN_VALUE;

            ComputeService.ComputeState returnState = postServiceSynchronously(
                    ComputeService.FACTORY_LINK,
                    startState, ComputeService.ComputeState.class);
            assertNotNull(returnState);

            ComputeService.ComputeState newState = new ComputeService.ComputeState();
            newState.id = UUID.randomUUID().toString();
            newState.address = "10.0.0.2";
            newState.creationTimeMicros = Long.MIN_VALUE;
            newState.descriptionLink = startState.descriptionLink;
            newState.powerState = ComputeService.PowerState.OFF;
            newState.primaryMAC = "ba:98:76:54:32:10";
            newState.resourcePoolLink = "http://newResourcePool";
            newState.adapterManagementReference = URI
                    .create("http://newAdapterManagementReference");
            newState.tenantLinks = new ArrayList<>();
            newState.tenantLinks.add("tenant1");
            newState.groupLinks = new HashSet<>();
            newState.groupLinks.add("group1");
            newState.diskLinks = new ArrayList<>();
            newState.diskLinks.add("http://disk1");
            newState.networkInterfaceLinks = new ArrayList<>();
            newState.networkInterfaceLinks.add("http://network1");

            putServiceSynchronously(returnState.documentSelfLink,
                    newState);

            ComputeService.ComputeStateWithDescription getState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    ComputeService.ComputeStateWithDescription.class);

            assertThat(getState.id, is(newState.id));
            assertThat(getState.address, is(newState.address));
            assertThat(getState.powerState, is(newState.powerState));
            assertThat(getState.primaryMAC, is(newState.primaryMAC));
            assertThat(getState.resourcePoolLink,
                    is(newState.resourcePoolLink));
            assertThat(getState.descriptionLink,
                    is(newState.descriptionLink));
            assertThat(getState.adapterManagementReference,
                    is(newState.adapterManagementReference));
            assertEquals(getState.tenantLinks, newState.tenantLinks);
            assertEquals(getState.groupLinks, newState.groupLinks);
            assertEquals(getState.diskLinks, newState.diskLinks);
            assertEquals(getState.networkInterfaceLinks, newState.networkInterfaceLinks);
            // make sure launchTimeMicros was preserved
            assertEquals(getState.creationTimeMicros, returnState.creationTimeMicros);
        }

        @Test(expected = IllegalArgumentException.class)
        public void testPutFailOnTypeChange() throws Throwable {
            ComputeDescriptionService.ComputeDescription cd = ComputeDescriptionServiceTest
                    .createComputeDescription(this);
            ComputeService.ComputeState startState = buildValidStartState(cd);
            startState.creationTimeMicros = Long.MIN_VALUE;

            ComputeService.ComputeState returnState = postServiceSynchronously(
                    ComputeService.FACTORY_LINK,
                    startState, ComputeService.ComputeState.class);
            assertNotNull(returnState);

            returnState.type = ComputeType.DOCKER_CONTAINER;

            putServiceSynchronously(returnState.documentSelfLink,
                    returnState);
        }

        @Test(expected = IllegalArgumentException.class)
        public void testPutFailOnEnvronmentNameChange() throws Throwable {
            ComputeDescriptionService.ComputeDescription cd = ComputeDescriptionServiceTest
                    .createComputeDescription(this);
            ComputeService.ComputeState startState = buildValidStartState(cd);
            startState.creationTimeMicros = Long.MIN_VALUE;

            ComputeService.ComputeState returnState = postServiceSynchronously(
                    ComputeService.FACTORY_LINK,
                    startState, ComputeService.ComputeState.class);
            assertNotNull(returnState);

            returnState.environmentName = ComputeDescription.ENVIRONMENT_NAME_AZURE;

            putServiceSynchronously(returnState.documentSelfLink,
                    returnState);
        }
    }

    /**
     * This class implements tests for query.
     */
    public static class QueryTest extends BaseModelTest {
        public static final int SERVICE_COUNT = 10;

        @Test
        public void testTenantLinksQuery() throws Throwable {
            ComputeDescriptionService.ComputeDescription cd = ComputeDescriptionServiceTest
                    .createComputeDescription(this);
            ComputeService.ComputeState cs = buildValidStartState(cd);

            URI tenantUri = UriUtils.buildFactoryUri(this.host, TenantService.class);
            cs.tenantLinks = new ArrayList<>();
            cs.tenantLinks.add(UriUtils.buildUriPath(tenantUri.getPath(),
                    "tenantA"));

            ComputeService.ComputeState startState = postServiceSynchronously(
                    ComputeService.FACTORY_LINK,
                            cs, ComputeService.ComputeState.class);

            String kind = Utils.buildKind(ComputeService.ComputeState.class);
            String propertyName = QueryTask.QuerySpecification
                    .buildCollectionItemName(ResourceState.FIELD_NAME_TENANT_LINKS);

            QueryTask q = createDirectQueryTask(kind, propertyName,
                    cs.tenantLinks.get(0));
            q = querySynchronously(q);
            assertNotNull(q.results.documentLinks);
            assertThat(q.results.documentCount, is(1L));
            assertThat(q.results.documentLinks.get(0),
                    is(startState.documentSelfLink));
        }

        @Test
        public void testCustomPropertiesQuery() throws Throwable {
            ComputeService.ComputeState[] initialStates = createInstances(SERVICE_COUNT);

            // Patch only one out of SERVICE_COUNT compute states with custom
            // property:
            String customPropComputeStateLink = initialStates[0].documentSelfLink;
            String newCustomPropertyValue = UUID.randomUUID().toString();

            ComputeService.ComputeState patchBody = new ComputeService.ComputeState();
            patchBody.customProperties = new HashMap<>();
            patchBody.customProperties.put(TEST_DESC_PROPERTY_NAME,
                    newCustomPropertyValue);
            patchServiceSynchronously(customPropComputeStateLink,
                    patchBody);

            String kind = Utils.buildKind(ComputeService.ComputeState.class);
            String propertyName = QueryTask.QuerySpecification
                    .buildCompositeFieldName(
                            ComputeService.ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                            TEST_DESC_PROPERTY_NAME);

            // Query computes with newCustomPropClause and expect 1 instance
            QueryTask q = createDirectQueryTask(kind, propertyName,
                    newCustomPropertyValue);
            queryComputes(q, 1);

            // Query computes with old CustomPropClause and expect
            // SERVICE_COUNT-1 instances
            q = createDirectQueryTask(kind, propertyName,
                    TEST_DESC_PROPERTY_VALUE);
            queryComputes(q, SERVICE_COUNT - 1);
        }

        private void queryComputes(QueryTask q, int expectedCount)
                throws Throwable {
            QueryTask queryTask = querySynchronously(q);
            assertNotNull(queryTask.results.documentLinks);
            assertFalse(queryTask.results.documentLinks.isEmpty());
            assertThat(queryTask.results.documentLinks.size(),
                    is(expectedCount));
        }

        public ComputeService.ComputeState[] createInstances(int c)
                throws Throwable {
            ComputeService.ComputeState[] instances = new ComputeService.ComputeState[c];
            ComputeDescriptionService.ComputeDescription cd = ComputeDescriptionServiceTest
                    .createComputeDescription(this);
            for (int i = 0; i < c; i++) {
                instances[i] = postServiceSynchronously(
                        ComputeService.FACTORY_LINK,
                        buildValidStartState(cd),
                        ComputeService.ComputeState.class);
            }
            return instances;
        }
    }
}
