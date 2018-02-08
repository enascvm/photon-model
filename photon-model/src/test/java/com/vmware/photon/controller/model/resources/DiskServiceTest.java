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
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.TenantService;

/**
 * This class implements tests for the {@link DiskService} class.
 */
@RunWith(DiskServiceTest.class)
@SuiteClasses({ DiskServiceTest.ConstructorTest.class,
        DiskServiceTest.HandleStartTest.class,
        DiskServiceTest.HandlePatchTest.class,
        DiskServiceTest.HandlePutTest.class,
        DiskServiceTest.QueryTest.class })
public class DiskServiceTest extends Suite {

    public DiskServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    private static DiskState buildValidStartState(boolean assignHost)
            throws Throwable {
        DiskState disk = new DiskState();

        disk.id = UUID.randomUUID().toString();
        disk.type = DiskService.DiskType.HDD;
        disk.name = "friendly-name";
        disk.capacityMBytes = 100L;
        if (assignHost) {
            disk.computeHostLink = "host-1";
        }

        return disk;
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {

        private DiskService diskService;

        @Before
        public void setUpTest() {
            this.diskService = new DiskService();
        }

        @Test
        public void testServiceOptions() {

            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.CONCURRENT_GET_HANDLING,
                    Service.ServiceOption.OWNER_SELECTION,
                    Service.ServiceOption.PERSISTENCE,
                    Service.ServiceOption.REPLICATION,
                    Service.ServiceOption.IDEMPOTENT_POST);

            assertThat(this.diskService.getOptions(), is(expected));
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleStartTest extends BaseModelTest {

        @Test
        public void testValidStartState() throws Throwable {
            DiskState startState = buildValidStartState(false);
            DiskState returnState = postServiceSynchronously(
                    DiskService.FACTORY_LINK, startState,
                    DiskState.class);

            assertNotNull(returnState);
            assertThat(returnState.id, is(startState.id));
            assertThat(returnState.name, is(startState.name));
            assertThat(returnState.type, is(startState.type));
            assertThat(returnState.capacityMBytes,
                    is(startState.capacityMBytes));
        }

        @Test
        public void testValidStartStateWithHost() throws Throwable {
            DiskState startState = buildValidStartState(true);
            DiskState returnState = postServiceSynchronously(
                    DiskService.FACTORY_LINK, startState,
                    DiskState.class);

            assertNotNull(returnState);
            assertThat(returnState.id, is(startState.id));
            assertThat(returnState.name, is(startState.name));
            assertThat(returnState.type, is(startState.type));
            assertThat(returnState.capacityMBytes,
                    is(startState.capacityMBytes));
            assertNotNull(returnState.computeHostLink);
            assertThat(returnState.computeHostLink, is(startState.computeHostLink));
        }

        @Test
        public void testDuplicatePost() throws Throwable {
            DiskState startState = buildValidStartState(false);
            DiskState returnState = postServiceSynchronously(
                    DiskService.FACTORY_LINK, startState,
                    DiskState.class);

            assertNotNull(returnState);
            assertThat(returnState.name, is(startState.name));
            startState.name = "new-name";
            returnState = postServiceSynchronously(
                    DiskService.FACTORY_LINK, startState,
                    DiskState.class);
            assertThat(returnState.name, is(startState.name));
        }

        @Test
        public void testDuplicatePostAssignComputeHost() throws Throwable {
            DiskState startState = buildValidStartState(false);
            DiskState returnState = postServiceSynchronously(
                    DiskService.FACTORY_LINK, startState,
                    DiskState.class);

            assertNotNull(returnState);
            assertThat(returnState.name, is(startState.name));
            startState.name = "new-name";
            startState.computeHostLink = "host-1";
            returnState = postServiceSynchronously(
                    DiskService.FACTORY_LINK, startState, DiskState.class);
            assertThat(returnState.name, is(startState.name));
            assertNotNull(returnState.computeHostLink);
            assertThat(returnState.computeHostLink, is(startState.computeHostLink));
        }

        @Test
        public void testDuplicatePostModifyComputeHost() throws Throwable {
            DiskState startState = buildValidStartState(true);
            DiskState returnState = postServiceSynchronously(
                    DiskService.FACTORY_LINK, startState,
                    DiskState.class);

            assertNotNull(returnState);
            assertNotNull(returnState.computeHostLink);
            assertThat(returnState.name, is(startState.name));

            returnState.computeHostLink = "host-2";
            postServiceSynchronously(DiskService.FACTORY_LINK, returnState,
                    DiskState.class, IllegalArgumentException.class);
        }

        @Test
        public void testDuplicatePostModifyCreationTime() throws Throwable {
            DiskState startState = buildValidStartState(true);
            DiskState returnState = postServiceSynchronously(
                    DiskService.FACTORY_LINK, startState,
                    DiskState.class);

            assertNotNull(returnState);
            assertNotNull(returnState.documentCreationTimeMicros);
            long originalTime = returnState.documentCreationTimeMicros;

            returnState.documentCreationTimeMicros = Utils.getNowMicrosUtc();

            returnState = postServiceSynchronously(DiskService.FACTORY_LINK,
                    returnState, DiskState.class);
            assertThat(originalTime, is(returnState.documentCreationTimeMicros));
        }

        @Test
        public void testMissingId() throws Throwable {
            DiskState startState = buildValidStartState(false);
            startState.id = null;

            DiskState returnState = postServiceSynchronously(
                    DiskService.FACTORY_LINK, startState,
                    DiskState.class);

            assertNotNull(returnState);
            assertNotNull(returnState.id);
        }

        @Test
        public void testMissingName() throws Throwable {
            DiskState startState = buildValidStartState(false);
            startState.name = null;

            postServiceSynchronously(DiskService.FACTORY_LINK,
                    startState, DiskState.class,
                    IllegalArgumentException.class);
        }

        public void testCapacityLessThanOneMB(Long capacityMBytes)
                throws Throwable {
            DiskState startState = buildValidStartState(false);
            startState.capacityMBytes = 0L;
            startState.sourceImageReference = new URI(
                    "http://sourceImageReference");
            startState.customizationServiceReference = new URI(
                    "http://customizationServiceReference");

            DiskState returnState = postServiceSynchronously(
                    DiskService.FACTORY_LINK, startState,
                    DiskState.class);

            assertNotNull(returnState);
        }

        @Test
        public void testMissingStatus() throws Throwable {
            DiskState startState = buildValidStartState(false);
            startState.status = null;

            DiskState returnState = postServiceSynchronously(
                    DiskService.FACTORY_LINK, startState,
                    DiskState.class);

            assertNotNull(returnState);
            assertThat(returnState.status, is(DiskService.DiskStatus.DETACHED));
        }

        public void testMissingPathInFileEntry(String path) throws Throwable {
            DiskState startState = buildValidStartState(false);
            startState.bootConfig = new DiskState.BootConfig();
            startState.bootConfig.files = new DiskState.BootConfig.FileEntry[1];
            startState.bootConfig.files[0] = new DiskState.BootConfig.FileEntry();
            startState.bootConfig.files[0].path = null;

            postServiceSynchronously(DiskService.FACTORY_LINK,
                    startState, DiskState.class,
                    IllegalArgumentException.class);
            startState.bootConfig.files[0].path = "";

            postServiceSynchronously(DiskService.FACTORY_LINK,
                    startState, DiskState.class,
                    IllegalArgumentException.class);

        }

        @Test
        public void testMissingCreationTimeMicros() throws Throwable {
            DiskState startState = buildValidStartState(false);
            startState.creationTimeMicros = null;

            DiskState returnState = postServiceSynchronously(
                    DiskService.FACTORY_LINK, startState, DiskState.class);

            assertNotNull(returnState);
            assertNull(returnState.creationTimeMicros);
        }

        @Test
        public void testProvidedCreationTimeMicros() throws Throwable {
            DiskState startState = buildValidStartState(false);
            startState.creationTimeMicros = Long.MIN_VALUE;

            DiskState returnState = postServiceSynchronously(
                    DiskService.FACTORY_LINK, startState, DiskState.class);

            assertNotNull(returnState);
            assertNotNull(returnState.creationTimeMicros);
            assertEquals(Long.MIN_VALUE, returnState.creationTimeMicros.longValue());
        }
    }

    /**
     * This class implements tests for the handlePatch method.
     */
    public static class HandlePatchTest extends BaseModelTest {

        @Test
        public void testPatchZoneId() throws Throwable {
            DiskState startState = buildValidStartState(false);
            startState.zoneId = "startZoneId";

            DiskState returnState = postServiceSynchronously(
                    DiskService.FACTORY_LINK, startState,
                    DiskState.class);
            assertNotNull(returnState.documentCreationTimeMicros);

            DiskState patchState = new DiskState();
            patchState.zoneId = null;

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);
            returnState = getServiceSynchronously(
                    returnState.documentSelfLink, DiskState.class);
            assertThat(returnState.zoneId, is("startZoneId"));
            patchState.zoneId = "startZoneId";

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);
            returnState = getServiceSynchronously(
                    returnState.documentSelfLink, DiskState.class);
            assertThat(returnState.zoneId, is("startZoneId"));
            patchState.zoneId = "patchZoneId";

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);
            returnState = getServiceSynchronously(
                    returnState.documentSelfLink, DiskState.class);
            assertThat(returnState.zoneId, is("patchZoneId"));

        }

        @Test
        public void testPatchName() throws Throwable {
            DiskState startState = buildValidStartState(false);
            startState.name = "startName";

            DiskState returnState = postServiceSynchronously(
                    DiskService.FACTORY_LINK, startState,
                    DiskState.class);
            assertNotNull(returnState.documentCreationTimeMicros);

            DiskState patchState = new DiskState();
            patchState.name = null;

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);
            returnState = getServiceSynchronously(
                    returnState.documentSelfLink, DiskState.class);
            assertThat(returnState.name, is("startName"));
            patchState.name = "startName";

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);
            returnState = getServiceSynchronously(
                    returnState.documentSelfLink, DiskState.class);
            assertThat(returnState.name, is("startName"));
            patchState.name = "patchName";

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);
            returnState = getServiceSynchronously(
                    returnState.documentSelfLink, DiskState.class);
            assertThat(returnState.name, is("patchName"));

        }

        @Test
        public void testPatchStatus() throws Throwable {
            DiskState startState = buildValidStartState(false);
            startState.status = DiskService.DiskStatus.DETACHED;

            DiskState returnState = postServiceSynchronously(
                    DiskService.FACTORY_LINK, startState,
                    DiskState.class);
            assertNotNull(returnState.documentCreationTimeMicros);

            DiskState patchState = new DiskState();
            patchState.status = null;

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);
            returnState = getServiceSynchronously(
                    returnState.documentSelfLink, DiskState.class);
            assertThat(returnState.status, is(DiskService.DiskStatus.DETACHED));
            patchState.status = DiskService.DiskStatus.DETACHED;

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);
            returnState = getServiceSynchronously(
                    returnState.documentSelfLink, DiskState.class);
            assertThat(returnState.status, is(DiskService.DiskStatus.DETACHED));
            patchState.status = DiskService.DiskStatus.ATTACHED;

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);
            returnState = getServiceSynchronously(
                    returnState.documentSelfLink, DiskState.class);
            assertThat(returnState.status, is(DiskService.DiskStatus.ATTACHED));
        }

        @Test
        public void testPatchCapacityMBytes() throws Throwable {
            DiskState startState = buildValidStartState(false);
            startState.capacityMBytes = 100L;

            DiskState returnState = postServiceSynchronously(
                    DiskService.FACTORY_LINK, startState,
                    DiskState.class);
            assertNotNull(returnState.documentCreationTimeMicros);

            DiskState patchState = new DiskState();
            patchState.capacityMBytes = 0;

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);
            returnState = getServiceSynchronously(
                    returnState.documentSelfLink, DiskState.class);
            assertThat(returnState.capacityMBytes, is(100L));
            patchState.capacityMBytes = 100L;

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);
            returnState = getServiceSynchronously(
                    returnState.documentSelfLink, DiskState.class);
            assertThat(returnState.capacityMBytes, is(100L));
            patchState.capacityMBytes = 200L;

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);
            returnState = getServiceSynchronously(
                    returnState.documentSelfLink, DiskState.class);
            assertThat(returnState.capacityMBytes, is(200L));
        }

        @Test
        public void testPatchCustomProperties() throws Throwable {
            DiskState startState = buildValidStartState(false);
            startState.customProperties = new HashMap<>();
            startState.customProperties.put("cp1-key", "cp1-value");

            DiskState returnStartState = postServiceSynchronously(
                    DiskService.FACTORY_LINK, startState,
                    DiskState.class);

            DiskState patchState = new DiskState();
            patchState.customProperties = new HashMap<>();
            patchState.customProperties.put("cp2-key", "cp2-value");

            patchServiceSynchronously(returnStartState.documentSelfLink,
                    patchState);

            DiskState patchedState = getServiceSynchronously(
                    returnStartState.documentSelfLink, DiskState.class);
            HashMap<Object, Object> expectedCustomProperties = new HashMap<>();
            expectedCustomProperties.putAll(returnStartState.customProperties);
            expectedCustomProperties.putAll(patchState.customProperties);

            assertThat(patchedState.customProperties, is(expectedCustomProperties));
        }

        @Test
        public void testPatchOtherFields() throws Throwable {
            DiskState startState = buildValidStartState(false);
            startState.regionId = "data-center-id1";
            startState.resourcePoolLink = "resource-pool-link1";
            startState.authCredentialsLink = "auth-credentials-link1";
            startState.tenantLinks = new ArrayList<>();
            startState.tenantLinks.add("tenant-link1");
            startState.groupLinks = new HashSet<String>();
            startState.groupLinks.add("group1");
            startState.bootOrder = 1;
            startState.bootArguments = new String[] { "boot-argument1" };
            startState.currencyUnit = "currency-unit1";

            DiskState returnState = postServiceSynchronously(
                    DiskService.FACTORY_LINK, startState,
                    DiskState.class);
            assertNull(returnState.computeHostLink);

            DiskState patchState = new DiskState();
            patchState.regionId = "data-center-id2";
            patchState.resourcePoolLink = "resource-pool-link2";
            patchState.authCredentialsLink = "auth-credentials-link2";
            patchState.tenantLinks = new ArrayList<>();
            patchState.tenantLinks.add("tenant-link2");
            patchState.bootOrder = 2;
            patchState.bootArguments = new String[] { "boot-argument2" };
            patchState.currencyUnit = "currency-unit2";
            patchState.groupLinks = new HashSet<>();
            patchState.groupLinks.add("group2");
            patchState.computeHostLink = "host-1";

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);
            returnState = getServiceSynchronously(
                    returnState.documentSelfLink, DiskState.class);
            assertThat(returnState.regionId, is(startState.regionId));
            assertThat(returnState.resourcePoolLink,
                    is(startState.resourcePoolLink));
            assertThat(returnState.authCredentialsLink,
                    is(startState.authCredentialsLink));
            assertEquals(returnState.tenantLinks.size(), 2);
            assertEquals(returnState.groupLinks.size(), 2);

            assertThat(returnState.bootOrder, is(startState.bootOrder));
            assertThat(returnState.bootArguments, is(startState.bootArguments));
            assertThat(returnState.currencyUnit, is(startState.currencyUnit));
            assertNotNull(returnState.computeHostLink);
            assertThat(returnState.computeHostLink,
                    is(patchState.computeHostLink));
        }

        @Test
        public void testPatchAssignHost() throws Throwable {
            DiskState startState = buildValidStartState(false);

            DiskState returnState = postServiceSynchronously(
                    DiskService.FACTORY_LINK, startState, DiskState.class);
            assertNotNull(returnState.documentCreationTimeMicros);

            DiskState patchState = new DiskState();
            patchState.computeHostLink = "host-2";
            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);

            returnState = getServiceSynchronously(
                    returnState.documentSelfLink, DiskState.class);
            assertNotNull(returnState.computeHostLink);
            assertThat(returnState.computeHostLink,
                    is(patchState.computeHostLink));
        }

        @Test
        public void testPatchModifyCreationTime() throws Throwable {
            DiskState startState = buildValidStartState(false);

            DiskState returnState = postServiceSynchronously(
                    DiskService.FACTORY_LINK, startState, DiskState.class);
            assertNotNull(returnState.documentCreationTimeMicros);
            long originalCreationTime = returnState.documentCreationTimeMicros;

            DiskState patchState = new DiskState();
            long currentCreationTime = Utils.getNowMicrosUtc();
            patchState.documentCreationTimeMicros = currentCreationTime;

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);

            returnState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    DiskState.class);
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
            DiskState startState = buildValidStartState(false);

            DiskState returnState = postServiceSynchronously(
                    DiskService.FACTORY_LINK, startState, DiskState.class);
            assertNotNull(returnState);

            DiskState newState = new DiskState();
            newState.id = UUID.randomUUID().toString();
            newState.type = DiskService.DiskType.HDD;
            newState.name = "friendly-name";
            newState.capacityMBytes = 100L;
            newState.documentCreationTimeMicros = returnState.documentCreationTimeMicros;

            putServiceSynchronously(returnState.documentSelfLink,
                    newState);
            DiskState getState = getServiceSynchronously(returnState.documentSelfLink,
                    DiskState.class);
            assertThat(getState.id, is(newState.id));
            assertThat(getState.name, is(newState.name));
            assertEquals(getState.tenantLinks, newState.tenantLinks);
            assertEquals(getState.groupLinks, newState.groupLinks);
            // make sure launchTimeMicros was preserved
            assertEquals(getState.creationTimeMicros, returnState.creationTimeMicros);
            assertEquals(getState.documentCreationTimeMicros, returnState.documentCreationTimeMicros);
        }

        @Test
        public void testPutModifyCreationTime() throws Throwable {
            DiskState startState = buildValidStartState(false);

            DiskState returnState = postServiceSynchronously(
                    DiskService.FACTORY_LINK, startState, DiskState.class);
            assertNotNull(returnState);

            DiskState newState = new DiskState();
            newState.id = UUID.randomUUID().toString();
            newState.name = "networkName";
            newState.tenantLinks = new ArrayList<>();
            newState.tenantLinks.add("tenant-linkA");
            newState.documentCreationTimeMicros = Utils.getNowMicrosUtc();

            putServiceSynchronously(returnState.documentSelfLink, newState);

            DiskState getState = getServiceSynchronously(
                    returnState.documentSelfLink, DiskState.class);
            assertThat(getState.documentCreationTimeMicros, is(returnState.documentCreationTimeMicros));
        }

        @Test(expected = IllegalArgumentException.class)
        public void testPutModifyHost() throws Throwable {
            DiskState startState = buildValidStartState(true);

            DiskState returnState = postServiceSynchronously(
                    DiskService.FACTORY_LINK, startState, DiskState.class);
            assertNotNull(returnState);

            DiskState newState = new DiskState();
            newState.id = UUID.randomUUID().toString();
            newState.name = "networkName";
            newState.tenantLinks = new ArrayList<>();
            newState.tenantLinks.add("tenant-linkA");
            newState.documentCreationTimeMicros = returnState.documentCreationTimeMicros;

            newState.computeHostLink = "host-2";

            putServiceSynchronously(returnState.documentSelfLink,
                    newState);
        }
    }

    /**
     * This class implements tests for query.
     */
    public static class QueryTest extends BaseModelTest {
        @Test
        public void testTenantLinksQuery() throws Throwable {
            DiskState disk = buildValidStartState(false);

            URI tenantUri = UriUtils.buildFactoryUri(this.host, TenantService.class);
            disk.tenantLinks = new ArrayList<>();
            disk.tenantLinks.add(UriUtils.buildUriPath(tenantUri.getPath(),
                    "tenantA"));

            DiskState startState = postServiceSynchronously(
                    DiskService.FACTORY_LINK, disk,
                    DiskState.class);

            String kind = Utils.buildKind(DiskState.class);
            String propertyName = QueryTask.QuerySpecification
                    .buildCollectionItemName(ResourceState.FIELD_NAME_TENANT_LINKS);

            QueryTask q = createDirectQueryTask(kind, propertyName,
                    disk.tenantLinks.get(0));
            q = querySynchronously(q);
            assertNotNull(q.results.documentLinks);
            assertThat(q.results.documentCount, is(1L));
            assertThat(q.results.documentLinks.get(0),
                    is(startState.documentSelfLink));
        }
    }
}
