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

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.ArrayList;
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
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState.ResourcePoolProperty;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceStateCollectionUpdateRequest;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.TenantService;

/**
 * This class implements tests for the {@link ResourcePoolService} class.
 */
@RunWith(ResourcePoolServiceTest.class)
@SuiteClasses({ ResourcePoolServiceTest.ConstructorTest.class,
        ResourcePoolServiceTest.HandleStartTest.class,
        ResourcePoolServiceTest.HandlePutTest.class,
        ResourcePoolServiceTest.HandlePatchTest.class,
        ResourcePoolServiceTest.QueryTest.class })
public class ResourcePoolServiceTest extends Suite {

    public ResourcePoolServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    public static ResourcePoolService.ResourcePoolState buildValidStartState()
            throws Throwable {
        ResourcePoolService.ResourcePoolState rp = new ResourcePoolService.ResourcePoolState();
        rp.id = UUID.randomUUID().toString();
        rp.currencyUnit = "US dollar";
        rp.maxCpuCostPerMinute = 10.0;
        rp.maxCpuCount = 16L;
        rp.maxDiskCapacityBytes = 2 ^ 40L;
        rp.maxDiskCostPerMinute = 10.0;
        rp.maxGpuCount = 16L;
        rp.maxMemoryBytes = 2 ^ 36L;
        rp.minCpuCount = 2L;
        rp.minDiskCapacityBytes = 2 ^ 40L;
        rp.minGpuCount = 0L;
        rp.minMemoryBytes = 2 ^ 34L;
        rp.name = "esx medium resource pool";
        rp.projectName = "GCE-project-123";
        return rp;
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {

        private ResourcePoolService resourcePoolService;

        @Before
        public void setUpTest() {
            this.resourcePoolService = new ResourcePoolService();
        }

        @Test
        public void testServiceOptions() {

            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.CONCURRENT_GET_HANDLING,
                    Service.ServiceOption.OWNER_SELECTION,
                    Service.ServiceOption.PERSISTENCE,
                    Service.ServiceOption.REPLICATION,
                    Service.ServiceOption.IDEMPOTENT_POST);

            assertThat(this.resourcePoolService.getOptions(), is(expected));
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleStartTest extends BaseModelTest {
        @Test
        public void testValidStartState() throws Throwable {
            ResourcePoolService.ResourcePoolState startState = buildValidStartState();
            ResourcePoolService.ResourcePoolState returnState = postServiceSynchronously(
                    ResourcePoolService.FACTORY_LINK, startState,
                    ResourcePoolService.ResourcePoolState.class);

            assertNotNull(returnState);
            assertThat(returnState.id, is(startState.id));
            assertThat(returnState.name, is(startState.name));
            assertThat(returnState.projectName, is(startState.projectName));
            assertThat(returnState.currencyUnit, is(startState.currencyUnit));
            assertThat(returnState.maxCpuCount, is(startState.maxCpuCount));
            assertThat(returnState.maxGpuCount, is(startState.maxGpuCount));
            assertThat(returnState.maxMemoryBytes,
                    is(startState.maxMemoryBytes));
            assertThat(returnState.minMemoryBytes,
                    is(startState.minMemoryBytes));
            assertThat(returnState.maxCpuCostPerMinute,
                    is(startState.maxCpuCostPerMinute));
            assertThat(returnState.maxDiskCapacityBytes,
                    is(startState.maxDiskCapacityBytes));
            assertNotNull(returnState.query);
            assertFalse(returnState.query.booleanClauses.isEmpty());
        }

        @Test
        public void testDuplicatePost() throws Throwable {
            ResourcePoolService.ResourcePoolState startState = buildValidStartState();
            ResourcePoolService.ResourcePoolState returnState = postServiceSynchronously(
                    ResourcePoolService.FACTORY_LINK, startState,
                    ResourcePoolService.ResourcePoolState.class);

            assertNotNull(returnState);
            assertThat(returnState.projectName, is(startState.projectName));
            startState.projectName = "new projectName";
            returnState = postServiceSynchronously(
                    ResourcePoolService.FACTORY_LINK, startState,
                    ResourcePoolService.ResourcePoolState.class);

            assertNotNull(returnState);
            assertThat(returnState.projectName, is(startState.projectName));
            ResourcePoolService.ResourcePoolState newState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    ResourcePoolService.ResourcePoolState.class);
            assertThat(newState.projectName, is(startState.projectName));
        }

        @Test
        public void testMissingId() throws Throwable {
            ResourcePoolService.ResourcePoolState startState = buildValidStartState();
            startState.id = null;

            ResourcePoolService.ResourcePoolState returnState = postServiceSynchronously(
                    ResourcePoolService.FACTORY_LINK, startState,
                    ResourcePoolService.ResourcePoolState.class);

            assertNotNull(returnState);
            assertNotNull(returnState.id);
        }

        @Test
        public void testElastic() throws Throwable {
            ResourcePoolService.ResourcePoolState startState = buildValidStartState();
            startState.properties = EnumSet.of(ResourcePoolProperty.ELASTIC);
            startState.query = Query.Builder.create().build();
            ResourcePoolService.ResourcePoolState returnState = postServiceSynchronously(
                    ResourcePoolService.FACTORY_LINK, startState,
                    ResourcePoolService.ResourcePoolState.class);

            assertNotNull(returnState);
            assertNotNull(returnState.query);
            assertNull(returnState.query.booleanClauses);
        }

        @Test
        public void testElasticNoQuery() throws Throwable {
            ResourcePoolService.ResourcePoolState startState = buildValidStartState();
            startState.properties = EnumSet.of(ResourcePoolProperty.ELASTIC);
            postServiceSynchronously(
                    ResourcePoolService.FACTORY_LINK, startState,
                    ResourcePoolService.ResourcePoolState.class, IllegalArgumentException.class);
        }

        @Test
        public void testNonElasticWithQuery() throws Throwable {
            ResourcePoolService.ResourcePoolState startState = buildValidStartState();
            startState.query = Query.Builder.create().build();
            ResourcePoolService.ResourcePoolState returnState = postServiceSynchronously(
                    ResourcePoolService.FACTORY_LINK, startState,
                    ResourcePoolService.ResourcePoolState.class);
            assertNotNull(returnState.query.booleanClauses);
        }
    }

    /**
     * This class implements tests for the handlePut method.
     */
    public static class HandlePutTest extends BaseModelTest {
        @Test
        public void testPutToNonElastic() throws Throwable {
            // create elastic
            ResourcePoolService.ResourcePoolState startState = buildValidStartState();
            startState.properties = EnumSet.of(ResourcePoolProperty.ELASTIC);
            startState.query = Query.Builder.create().build();
            startState = postServiceSynchronously(
                    ResourcePoolService.FACTORY_LINK, startState,
                    ResourcePoolService.ResourcePoolState.class);

            // replace with non-elastic
            startState.properties = EnumSet.noneOf(ResourcePoolProperty.class);
            startState.query = null;
            putServiceSynchronously(startState.documentSelfLink, startState);

            ResourcePoolService.ResourcePoolState newState = getServiceSynchronously(
                    startState.documentSelfLink,
                    ResourcePoolService.ResourcePoolState.class);
            assertThat(newState.documentSelfLink, is(startState.documentSelfLink));
            assertTrue(newState.properties.isEmpty());
            assertNotNull(newState.query);
            assertFalse(newState.query.booleanClauses.isEmpty());
        }

        @Test
        public void testPutNonElasticWithQuery() throws Throwable {
            // create non-elastic
            ResourcePoolService.ResourcePoolState startState = buildValidStartState();
            startState.properties = EnumSet.noneOf(ResourcePoolProperty.class);
            startState = postServiceSynchronously(
                    ResourcePoolService.FACTORY_LINK, startState,
                    ResourcePoolService.ResourcePoolState.class);

            // in a put action, passing the query is ok for non-elastic pools (no exception)
            startState.maxCpuCount = 12L;
            putServiceSynchronously(startState.documentSelfLink, startState);
        }
    }

    /**
     * This class implements tests for the handlePatch method.
     */
    public static class HandlePatchTest extends BaseModelTest {
        @Test
        public void testPatchResourcePoolName() throws Throwable {
            ResourcePoolService.ResourcePoolState startState = createResourcePoolService();

            ResourcePoolService.ResourcePoolState patchState = new ResourcePoolService.ResourcePoolState();
            patchState.name = UUID.randomUUID().toString();
            patchState.tenantLinks = new ArrayList<String>();
            patchState.tenantLinks.add("tenant1");
            patchState.groupLinks = new HashSet<String>();
            patchState.groupLinks.add("group1");
            ResourcePoolService.ResourcePoolState newState = patchServiceSynchronously(
                    startState.documentSelfLink,
                    patchState, ResourcePoolService.ResourcePoolState.class);

            assertThat(newState.name, is(patchState.name));
            assertEquals(newState.tenantLinks, patchState.tenantLinks);
            assertEquals(newState.groupLinks, patchState.groupLinks);
            assertNotNull(newState.query);
        }

        @Test
        public void testPatchResponseBody() throws Throwable {
            ResourcePoolService.ResourcePoolState startState = createResourcePoolService();

            ResourcePoolService.ResourcePoolState patchState = new ResourcePoolService.ResourcePoolState();
            patchState.name = UUID.randomUUID().toString();
            ResourcePoolService.ResourcePoolState returnedState = patchServiceSynchronously(
                    startState.documentSelfLink, patchState,
                    ResourcePoolService.ResourcePoolState.class);

            assertThat(returnedState.name, is(patchState.name));

            // test full state is returned
            assertNotNull(returnedState.query);
        }

        @Test
        public void testPatchResponseBodyNoChange() throws Throwable {
            ResourcePoolService.ResourcePoolState startState = createResourcePoolService();

            ResourcePoolService.ResourcePoolState patchState = new ResourcePoolService.ResourcePoolState();
            patchState.name = startState.name;
            Operation patchOp = Operation
                    .createPatch(UriUtils.buildUri(this.host, startState.documentSelfLink))
                    .setBody(patchState);
            Operation returnedOp = sendOperationSynchronously(patchOp);

            assertThat(returnedOp.getStatusCode(), is(Operation.STATUS_CODE_NOT_MODIFIED));

            // test full state is not returned (the original patch state is returned)
            assertNull(returnedOp.getBody(ResourcePoolService.ResourcePoolState.class).query);
        }

        @Test
        public void testPatchResourcePoolProjectName() throws Throwable {
            ResourcePoolService.ResourcePoolState startState = createResourcePoolService();

            ResourcePoolService.ResourcePoolState patchState = new ResourcePoolService.ResourcePoolState();
            patchState.projectName = UUID.randomUUID().toString();
            ResourcePoolService.ResourcePoolState newState = patchServiceSynchronously(
                    startState.documentSelfLink,
                    patchState, ResourcePoolService.ResourcePoolState.class);

            assertThat(newState.projectName, is(patchState.projectName));
        }

        @Test
        public void testPatchToElastic() throws Throwable {
            ResourcePoolService.ResourcePoolState startState = createResourcePoolService();

            ResourcePoolService.ResourcePoolState patchState = new ResourcePoolService.ResourcePoolState();
            patchState.properties = EnumSet.of(ResourcePoolProperty.ELASTIC);
            patchState.query = Query.Builder.create().build();
            ResourcePoolService.ResourcePoolState newState = patchServiceSynchronously(
                    startState.documentSelfLink,
                    patchState, ResourcePoolService.ResourcePoolState.class);

            assertThat(newState.properties, is(EnumSet.of(ResourcePoolProperty.ELASTIC)));
            assertNotNull(newState.query);
            assertNull(newState.query.booleanClauses);
        }

        @Test(expected = IllegalArgumentException.class)
        public void testPatchToElasticNoQuery() throws Throwable {
            ResourcePoolService.ResourcePoolState startState = createResourcePoolService();

            ResourcePoolService.ResourcePoolState patchState = new ResourcePoolService.ResourcePoolState();
            patchState.properties = EnumSet.of(ResourcePoolProperty.ELASTIC);
            patchServiceSynchronously(startState.documentSelfLink, patchState);
        }

        @Test
        public void testPatchToNonElastic() throws Throwable {
            // create elastic
            ResourcePoolService.ResourcePoolState startState = buildValidStartState();
            startState.properties = EnumSet.of(ResourcePoolProperty.ELASTIC);
            startState.query = Query.Builder.create().build();
            startState = postServiceSynchronously(
                    ResourcePoolService.FACTORY_LINK, startState,
                    ResourcePoolService.ResourcePoolState.class);

            // patch with non-elastic
            ResourcePoolService.ResourcePoolState patchState = new ResourcePoolService.ResourcePoolState();
            patchState.properties = EnumSet.noneOf(ResourcePoolProperty.class);
            patchServiceSynchronously(startState.documentSelfLink, patchState);

            // verify RP is still elastic - set items cannot be removed through a patch request
            ResourcePoolService.ResourcePoolState newState = getServiceSynchronously(
                    startState.documentSelfLink,
                    ResourcePoolService.ResourcePoolState.class);
            assertThat(newState.properties, is(EnumSet.of(ResourcePoolProperty.ELASTIC)));
            assertNotNull(newState.query);
            assertNull(newState.query.booleanClauses);
        }

        @Test
        public void testPatchToNonElasticThroughCollectionUpdate() throws Throwable {
            // create elastic
            ResourcePoolService.ResourcePoolState startState = buildValidStartState();
            startState.properties = EnumSet.of(ResourcePoolProperty.ELASTIC);
            startState.query = Query.Builder.create().build();
            startState = postServiceSynchronously(
                    ResourcePoolService.FACTORY_LINK, startState,
                    ResourcePoolService.ResourcePoolState.class);

            // patch with a collection update request that removed the ELASTIC property
            Map<String, Collection<Object>> itemsToRemove = new HashMap<>();
            List<Object> propertiesToRemove = new ArrayList<>();
            propertiesToRemove.add(ResourcePoolProperty.ELASTIC);
            itemsToRemove.put(ResourcePoolState.FIELD_NAME_PROPERTIES, propertiesToRemove);
            patchServiceSynchronously(startState.documentSelfLink,
                    ServiceStateCollectionUpdateRequest.create(null, itemsToRemove));

            // verify RP is now non-elastic
            ResourcePoolService.ResourcePoolState newState = getServiceSynchronously(
                    startState.documentSelfLink,
                    ResourcePoolService.ResourcePoolState.class);
            assertThat(newState.properties, is(EnumSet.noneOf(ResourcePoolProperty.class)));
            assertNotNull(newState.query);
            assertFalse(newState.query.booleanClauses.isEmpty());
        }

        @Test
        public void testPatchNumericFields() throws Throwable {
            // create with null values
            ResourcePoolService.ResourcePoolState startState = new ResourcePoolService.ResourcePoolState();
            startState.name = "my-pool";
            startState = postServiceSynchronously(
                    ResourcePoolService.FACTORY_LINK, startState,
                    ResourcePoolService.ResourcePoolState.class);

            // patch with values
            ResourcePoolService.ResourcePoolState patchState = new ResourcePoolService.ResourcePoolState();
            patchState.maxCpuCount = 5L;
            patchState.maxMemoryBytes = 5L;
            patchState.maxCpuCostPerMinute = 5d;
            ResourcePoolService.ResourcePoolState newState = patchServiceSynchronously(
                    startState.documentSelfLink, patchState,
                    ResourcePoolService.ResourcePoolState.class);

            assertThat(newState.maxCpuCount, is(5L));
            assertThat(newState.maxMemoryBytes, is(5L));
            assertThat(newState.maxCpuCostPerMinute, is(5d));
            assertThat(newState.minCpuCount, is(nullValue()));

            // patch with new values
            patchState = new ResourcePoolService.ResourcePoolState();
            patchState.maxMemoryBytes = 6L;
            patchState.maxCpuCostPerMinute = 6d;
            newState = patchServiceSynchronously(startState.documentSelfLink, patchState,
                    ResourcePoolService.ResourcePoolState.class);

            assertThat(newState.maxCpuCount, is(5L));
            assertThat(newState.maxMemoryBytes, is(6L));
            assertThat(newState.maxCpuCostPerMinute, is(6d));
            assertThat(newState.minCpuCount, is(nullValue()));
        }

        private ResourcePoolService.ResourcePoolState createResourcePoolService()
                throws Throwable {
            ResourcePoolService.ResourcePoolState startState = buildValidStartState();
            return postServiceSynchronously(
                    ResourcePoolService.FACTORY_LINK, startState,
                    ResourcePoolService.ResourcePoolState.class);
        }
    }

    /**
     * This class implements tests for query.
     */
    public static class QueryTest extends BaseModelTest {
        @Test
        public void testTenantLinksQuery() throws Throwable {
            ResourcePoolService.ResourcePoolState rp = buildValidStartState();

            URI tenantUri = UriUtils.buildFactoryUri(this.host, TenantService.class);
            rp.tenantLinks = new ArrayList<>();
            rp.tenantLinks.add(UriUtils.buildUriPath(tenantUri.getPath(),
                    "tenantA"));

            ResourcePoolService.ResourcePoolState startState = postServiceSynchronously(
                    ResourcePoolService.FACTORY_LINK, rp,
                    ResourcePoolService.ResourcePoolState.class);

            String kind = Utils
                    .buildKind(ResourcePoolService.ResourcePoolState.class);
            String propertyName = QueryTask.QuerySpecification
                    .buildCollectionItemName(ResourceState.FIELD_NAME_TENANT_LINKS);

            QueryTask q = createDirectQueryTask(kind, propertyName,
                    rp.tenantLinks.get(0));
            q = querySynchronously(q);
            assertNotNull(q.results.documentLinks);
            assertThat(q.results.documentCount, is(1L));
            assertThat(q.results.documentLinks.get(0),
                    is(startState.documentSelfLink));
        }

        @Test
        public void testResourcePoolQuery() throws Throwable {
            // Create a resourcePool
            ResourcePoolService.ResourcePoolState rp = buildValidStartState();
            ResourcePoolService.ResourcePoolState startState = postServiceSynchronously(
                    ResourcePoolService.FACTORY_LINK, rp,
                    ResourcePoolService.ResourcePoolState.class);

            // Create a ComputeService in the same resource Pool
            ComputeDescriptionService.ComputeDescription cd = ComputeDescriptionServiceTest
                    .createComputeDescription(this);
            ComputeService.ComputeState cs = ComputeServiceTest
                    .buildValidStartState(cd);
            cs.resourcePoolLink = startState.documentSelfLink;
            ComputeService.ComputeState csStartState = postServiceSynchronously(
                    ComputeService.FACTORY_LINK,
                    cs, ComputeService.ComputeState.class);

            QueryTask q = QueryTask.Builder.createDirectTask().setQuery(startState.query).build();
            QueryTask qr = querySynchronously(q);

            assertNotNull(qr.results.documentLinks);
            assertThat(qr.results.documentCount, is(1L));
            assertThat(qr.results.documentLinks.get(0),
                    is(csStartState.documentSelfLink));
        }
    }
}
