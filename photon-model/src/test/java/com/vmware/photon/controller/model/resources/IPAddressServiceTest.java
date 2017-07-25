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
import java.util.UUID;
import java.util.logging.Logger;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.IPAddressService.IPAddressState;
import com.vmware.photon.controller.model.resources.IPAddressService.IPAddressState.IPAddressStatus;
import com.vmware.photon.controller.model.support.IPVersion;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.TenantService;

/**
 * This class implements tests for the {@link IPAddressService} class.
 */
@RunWith(IPAddressServiceTest.class)
@Suite.SuiteClasses({
        IPAddressServiceTest.ConstructorTest.class,
        IPAddressServiceTest.HandleStartTest.class,
        IPAddressServiceTest.HandlePatchTest.class,
        IPAddressServiceTest.QueryTest.class
        })
public class IPAddressServiceTest extends Suite {
    static final Logger logger = Logger.getLogger(IPAddressServiceTest.class.getName());

    public IPAddressServiceTest(Class<?> klass, RunnerBuilder builder)  throws InitializationError {
        super(klass, builder);
    }

    private static IPAddressState buildValidStartState() {
        IPAddressState ipAddressState = new IPAddressState();
        ipAddressState.id = UUID.randomUUID().toString();
        ipAddressState.name = "ipAddress";
        ipAddressState.ipAddress = "192.130.120.110";
        ipAddressState.ipVersion = IPVersion.IPv4;
        ipAddressState.ipAddressStatus = IPAddressStatus.ALLOCATED;
        ipAddressState.subnetRangeLink = SubnetRangeService.FACTORY_LINK + "/subnet-range-1";
        ipAddressState.tenantLinks = new ArrayList<>();
        ipAddressState.tenantLinks.add("tenant-linkA");
        ipAddressState.connectedResourceLink = ComputeService.FACTORY_LINK + "/machine-1";

        return ipAddressState;
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {
        private IPAddressService IPAddressService = new IPAddressService();

        @Before
        public void setupTest() {
            this.IPAddressService = new IPAddressService();
        }

        @Test
        public void testServiceOptions() {
            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.CONCURRENT_GET_HANDLING,
                    Service.ServiceOption.PERSISTENCE,
                    Service.ServiceOption.REPLICATION,
                    Service.ServiceOption.OWNER_SELECTION,
                    Service.ServiceOption.IDEMPOTENT_POST);
            assertThat(this.IPAddressService.getOptions(), is(expected));
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleStartTest extends BaseModelTest {

        @Before
        public void setUp() throws Throwable {
            super.setUp();
            this.getHost().startFactory(new IPAddressService());
            this.getHost().startFactory(new SubnetRangeService());
        }

        @Test
        public void testValidStartState() throws Throwable {
            IPAddressState startState = buildValidStartState();
            IPAddressState returnState = postServiceSynchronously(
                    IPAddressService.FACTORY_LINK, startState, IPAddressState.class);

            assertNotNull(returnState);
            assertThat(returnState.id, is(startState.id));
            assertThat(returnState.name, is(startState.name));
            assertThat(returnState.ipAddress, is(startState.ipAddress));
            assertThat(returnState.ipAddressStatus, is(startState.ipAddressStatus));
            assertThat(returnState.ipVersion, is(startState.ipVersion));
            assertThat(returnState.subnetRangeLink, is(startState.subnetRangeLink));
            assertThat(returnState.connectedResourceLink, is(startState.connectedResourceLink));

            assertThat(returnState.tenantLinks.get(0),
                    is(startState.tenantLinks.get(0)));

        }

        @Test
        public void testDefaultValues() throws Throwable {
            IPAddressState addressState = buildValidStartState();
            addressState.ipVersion = null;
            addressState = postServiceSynchronously(
                    IPAddressService.FACTORY_LINK, addressState, IPAddressState.class);

            assertNotNull(addressState);
            assertThat(IPVersion.IPv4, is(addressState.ipVersion));
        }

        @Test(expected = IllegalArgumentException.class)
        public void testMissingRequiredFieldsOnPost() throws Throwable {
            IPAddressState startState = buildValidStartState();

            // Missing ip address
            startState.ipAddress = null;
            postServiceSynchronously(IPAddressService.FACTORY_LINK, startState,
                    IPAddressState.class);
            assertThat("exception expected", false);
        }

        @Test(expected = IllegalArgumentException.class)
        public void testMissingRequiredFieldsOnPut() throws Throwable {
            IPAddressState startState = buildValidStartState();
            IPAddressState returnState = postServiceSynchronously(IPAddressService.FACTORY_LINK,
                    startState, IPAddressState.class);

            // Missing ip address
            returnState.ipAddress = null;
            putServiceSynchronously(returnState.documentSelfLink, returnState);
            assertThat("exception expected", false);
        }

        @Test
        public void testDuplicatePost() throws Throwable {
            IPAddressState startState = buildValidStartState();
            IPAddressState returnState = postServiceSynchronously(
                    IPAddressService.FACTORY_LINK, startState, IPAddressState.class);

            assertNotNull(returnState);
            assertThat(returnState.name, is(startState.name));
            startState.name = startState.name + "-second";
            returnState = postServiceSynchronously(IPAddressService.FACTORY_LINK,
                    startState, IPAddressState.class);
            assertThat(returnState.name, is(startState.name));
        }

        @Test
        public void testInvalidValuesOnPost() throws Throwable {

            IPAddressState missingIp = buildValidStartState();
            missingIp.ipAddress = "";

            IPAddressState invalidIp = buildValidStartState();
            invalidIp.ipAddress = "abc.1.1.1";

            IPAddressState[] states = {
                    missingIp, invalidIp
            };

            for (IPAddressState state : states) {
                System.out.println("Subnet range: " + state.toString());
                try {
                    postServiceSynchronously(IPAddressService.FACTORY_LINK, state,
                            IPAddressState.class);
                    assertThat("exception expected for invalid post", false);
                } catch (Exception e) {
                    assertThat("exception expected for invalid post", e instanceof LocalizableValidationException);
                }
            }
        }

        @Test
        public void testInvalidValuesOnPut() throws Throwable {

            IPAddressState missingIp = buildValidStartState();
            missingIp = postServiceSynchronously(IPAddressService.FACTORY_LINK, missingIp,
                    IPAddressState.class);
            missingIp.ipAddress = "";

            IPAddressState invalidIp = buildValidStartState();
            invalidIp = postServiceSynchronously(IPAddressService.FACTORY_LINK, invalidIp,
                    IPAddressState.class);
            invalidIp.ipAddress = "abc.1.1.1";

            IPAddressState invalidIp2 = buildValidStartState();
            invalidIp2 = postServiceSynchronously(IPAddressService.FACTORY_LINK, invalidIp2,
                    IPAddressState.class);
            invalidIp2.ipAddress = "1.1.1";

            IPAddressState invalidIp3 = buildValidStartState();
            invalidIp3 = postServiceSynchronously(IPAddressService.FACTORY_LINK, invalidIp3,
                    IPAddressState.class);
            invalidIp3.ipAddress = "1.1.1.1.1";

            IPAddressState invalidIp4 = buildValidStartState();
            invalidIp4 = postServiceSynchronously(IPAddressService.FACTORY_LINK, invalidIp4,
                    IPAddressState.class);
            invalidIp4.ipAddress = "257.1.1.1";

            IPAddressState invalidIp5 = buildValidStartState();
            invalidIp5 = postServiceSynchronously(IPAddressService.FACTORY_LINK, invalidIp5,
                    IPAddressState.class);
            invalidIp5.ipAddress = "-1.1.1.1";

            IPAddressState[] states = {
                    missingIp, invalidIp, invalidIp2, invalidIp3, invalidIp4, invalidIp5
            };

            for (IPAddressState state : states) {
                System.out.println("Subnet range: " + state.toString());
                try {
                    putServiceSynchronously(state.documentSelfLink, state);
                    assertThat("exception expected for invalid put", false);
                } catch (Exception e) {
                    assertThat("exception expected for invalid put", e instanceof LocalizableValidationException);
                }
            }
        }

        @Test
        public void testStatusUpdates() throws Throwable {

            // Create IP address
            IPAddressState startState = buildValidStartState();
            startState.connectedResourceLink = null;
            startState.ipAddressStatus = IPAddressStatus.AVAILABLE;
            IPAddressState returnState = postServiceSynchronously(IPAddressService.FACTORY_LINK, startState,
                    IPAddressState.class);

            assertEquals(returnState.ipAddressStatus, IPAddressStatus.AVAILABLE);

            // Invalid: AVAILABLE -> RELEASED
            returnState.ipAddressStatus = IPAddressStatus.RELEASED;
            try {
                putServiceSynchronously(returnState.documentSelfLink, returnState);
                assertThat("exception expected for invalid status transition", false);
            } catch (Exception e) {
                assertThat("exception expected for invalid status transition", e instanceof IllegalArgumentException);
            }

            // Verify no transition
            returnState = getServiceSynchronously(returnState.documentSelfLink, IPAddressState.class);
            assertEquals(returnState.ipAddressStatus, IPAddressStatus.AVAILABLE);

            // Valid: AVAILABLE -> ALLOCATED
            returnState.ipAddressStatus = IPAddressStatus.ALLOCATED;
            returnState.connectedResourceLink = ComputeService.FACTORY_LINK + "/machine-1";
            putServiceSynchronously(returnState.documentSelfLink, returnState);

            // Verify transition
            returnState = getServiceSynchronously(returnState.documentSelfLink, IPAddressState.class);
            assertEquals(returnState.ipAddressStatus, IPAddressStatus.ALLOCATED);

            // Invalid: ALLOCATED -> AVAILABLE
            returnState.ipAddressStatus = IPAddressStatus.AVAILABLE;
            returnState.connectedResourceLink = null;
            try {
                putServiceSynchronously(returnState.documentSelfLink, returnState);
                assertThat("exception expected for invalid status transition", false);
            } catch (Exception e) {
                assertThat("exception expected for invalid status transition", e instanceof IllegalArgumentException);
            }

            // Verify no transition
            returnState = getServiceSynchronously(returnState.documentSelfLink, IPAddressState.class);
            assertEquals(returnState.ipAddressStatus, IPAddressStatus.ALLOCATED);

            // Valid: ALLOCATED -> RELEASED
            returnState.ipAddressStatus = IPAddressStatus.RELEASED;
            returnState.connectedResourceLink = null;
            putServiceSynchronously(returnState.documentSelfLink, returnState);

            // Verify transition
            returnState = getServiceSynchronously(returnState.documentSelfLink, IPAddressState.class);
            assertEquals(returnState.ipAddressStatus, IPAddressStatus.RELEASED);

            // Invalid: RELEASED -> ALLOCATED
            returnState.ipAddressStatus = IPAddressStatus.ALLOCATED;
            try {
                putServiceSynchronously(returnState.documentSelfLink, returnState);
                assertThat("exception expected for invalid status transition", false);
            } catch (Exception e) {
                assertThat("exception expected for invalid status transition", e instanceof IllegalArgumentException);
            }

            // Verify no transition
            returnState = getServiceSynchronously(returnState.documentSelfLink, IPAddressState.class);
            assertEquals(returnState.ipAddressStatus, IPAddressStatus.RELEASED);

            // Valid: RELEASED -> AVAILABLE
            returnState.ipAddressStatus = IPAddressStatus.AVAILABLE;
            putServiceSynchronously(returnState.documentSelfLink, returnState);

            // Verify transition
            returnState = getServiceSynchronously(returnState.documentSelfLink, IPAddressState.class);
            assertEquals(returnState.ipAddressStatus, IPAddressStatus.AVAILABLE);

        }

        @Test
        public void testStatusUpdatesConnectedResource() throws Throwable {
            // Create IP address
            IPAddressState startState = buildValidStartState();
            IPAddressState returnState = postServiceSynchronously(IPAddressService.FACTORY_LINK, startState,
                    IPAddressState.class);

            // Invalid: ALLOCATED -> RELEASED, connectedResourceLink still set
            returnState.ipAddressStatus = IPAddressStatus.RELEASED;
            try {
                putServiceSynchronously(returnState.documentSelfLink, returnState);
                assertThat("exception expected for invalid status transition", false);
            } catch (Exception e) {
                assertThat("exception expected for invalid status transition", e instanceof IllegalArgumentException);
            }

            // Release IP address
            returnState.connectedResourceLink = null;
            putServiceSynchronously(returnState.documentSelfLink, returnState);

            // Invalid: RELEASED -> AVAILABLE, connectedResourceLink set
            returnState.ipAddressStatus = IPAddressStatus.AVAILABLE;
            returnState.connectedResourceLink = ComputeService.FACTORY_LINK + "/machine-1";
            try {
                putServiceSynchronously(returnState.documentSelfLink, returnState);
                assertThat("exception expected for invalid status transition", false);
            } catch (Exception e) {
                assertThat("exception expected for invalid status transition", e instanceof IllegalArgumentException);
            }

            // Mark IP address AVAILABLE
            returnState.connectedResourceLink = null;
            putServiceSynchronously(returnState.documentSelfLink, returnState);

            // Invalid: AVAILABLE -> ALLOCATED, connectedResourceLink not set
            returnState.ipAddressStatus = IPAddressStatus.ALLOCATED;
            try {
                putServiceSynchronously(returnState.documentSelfLink, returnState);
                assertThat("exception expected for invalid status transition", false);
            } catch (Exception e) {
                assertThat("exception expected for invalid status transition", e instanceof IllegalArgumentException);
            }

            // Mark IP address ALLOCATED
            returnState.connectedResourceLink = ComputeService.FACTORY_LINK + "/machine-1";
            putServiceSynchronously(returnState.documentSelfLink, returnState);
        }
    }

    /**
     * This class implements tests for the handlePatch method.
     */
    public static class HandlePatchTest extends BaseModelTest {

        @Before
        public void setUp() throws Throwable {
            super.setUp();
            this.getHost().startFactory(new IPAddressService());
            this.getHost().startFactory(new SubnetRangeService());
        }

        @Test
        public void testPatch() throws Throwable {
            IPAddressState startState = buildValidStartState();

            IPAddressState returnState = postServiceSynchronously(
                    IPAddressService.FACTORY_LINK,
                    startState, IPAddressState.class);

            assertEquals(returnState.ipAddressStatus, IPAddressStatus.ALLOCATED);
            // Patch the ip address status
            IPAddressState patchState = new IPAddressState();
            patchState.ipAddressStatus = IPAddressStatus.RELEASED;
            patchState.connectedResourceLink = ResourceUtils.NULL_LINK_VALUE;
            patchServiceSynchronously(returnState.documentSelfLink, patchState);

            IPAddressState afterPatchState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    IPAddressState.class);

            assertThat(afterPatchState.name, is(startState.name));
            assertThat(afterPatchState.ipAddress, is(startState.ipAddress));
            assertThat(afterPatchState.subnetRangeLink, is(startState.subnetRangeLink));
            assertThat(afterPatchState.ipAddressStatus, is(patchState.ipAddressStatus));
            assertThat("Document version increased", afterPatchState.documentVersion > returnState.documentVersion);
        }

        @Test
        public void testPatchNoChange() throws Throwable {
            IPAddressState startState = buildValidStartState();

            IPAddressState returnState = postServiceSynchronously(
                    IPAddressService.FACTORY_LINK,
                    startState, IPAddressState.class);

            // Same start ip address
            IPAddressState patchState = new IPAddressState();
            patchState.ipAddress = startState.ipAddress;

            patchServiceSynchronously(returnState.documentSelfLink, patchState);

            // Verify no change
            IPAddressState afterPatchState = getServiceSynchronously(returnState.documentSelfLink,
                    IPAddressState.class);
            assertEquals(startState.documentVersion, afterPatchState.documentVersion);
        }

        @Test
        public void testStatusUpdatesConnectedResource() throws Throwable {
            // Create IP address
            IPAddressState startState = buildValidStartState();
            IPAddressState returnState = postServiceSynchronously(IPAddressService.FACTORY_LINK, startState,
                    IPAddressState.class);

            // Invalid: ALLOCATED -> RELEASED, connectedResourceLink still set
            returnState.ipAddressStatus = IPAddressStatus.RELEASED;
            try {
                patchServiceSynchronously(returnState.documentSelfLink, returnState);
                assertThat("exception expected for invalid status transition", false);
            } catch (Exception e) {
                assertThat("exception expected for invalid status transition", e instanceof IllegalArgumentException);
            }

            // Release IP address
            returnState.connectedResourceLink = ResourceUtils.NULL_LINK_VALUE;
            patchServiceSynchronously(returnState.documentSelfLink, returnState);

            // Invalid: RELEASED -> AVAILABLE, connectedResourceLink set
            returnState.ipAddressStatus = IPAddressStatus.AVAILABLE;
            returnState.connectedResourceLink = ComputeService.FACTORY_LINK + "/machine-1";
            try {
                patchServiceSynchronously(returnState.documentSelfLink, returnState);
                assertThat("exception expected for invalid status transition", false);
            } catch (Exception e) {
                assertThat("exception expected for invalid status transition", e instanceof IllegalArgumentException);
            }

            // Mark IP address AVAILABLE
            returnState.connectedResourceLink = ResourceUtils.NULL_LINK_VALUE;
            patchServiceSynchronously(returnState.documentSelfLink, returnState);

            // Invalid: AVAILABLE -> ALLOCATED, connectedResourceLink not set
            returnState.ipAddressStatus = IPAddressStatus.ALLOCATED;
            try {
                patchServiceSynchronously(returnState.documentSelfLink, returnState);
                assertThat("exception expected for invalid status transition", false);
            } catch (Exception e) {
                assertThat("exception expected for invalid status transition", e instanceof IllegalArgumentException);
            }

            // Mark IP address ALLOCATED
            returnState.connectedResourceLink = ComputeService.FACTORY_LINK + "/machine-1";
            patchServiceSynchronously(returnState.documentSelfLink, returnState);
        }
    }

    /**
     * This class implements tests for query.
     */
    public static class QueryTest extends BaseModelTest {

        @Before
        public void setUp() throws Throwable {
            super.setUp();
            this.getHost().startFactory(new SubnetRangeService());
            this.getHost().startFactory(new IPAddressService());
        }

        @Test
        public void testTenantLinksQuery() throws Throwable {
            IPAddressState ipAddressState = buildValidStartState();
            URI tenantUri = UriUtils.buildFactoryUri(this.host, TenantService.class);
            ipAddressState.tenantLinks = new ArrayList<>();
            ipAddressState.tenantLinks.add(UriUtils.buildUriPath(
                    tenantUri.getPath(), "tenantA"));
            IPAddressState startState = postServiceSynchronously(
                    IPAddressService.FACTORY_LINK,
                    ipAddressState, IPAddressState.class);

            String kind = Utils.buildKind(IPAddressState.class);
            String propertyName = QueryTask.QuerySpecification
                    .buildCollectionItemName(ResourceState.FIELD_NAME_TENANT_LINKS);

            QueryTask q = createDirectQueryTask(kind, propertyName,
                    ipAddressState.tenantLinks.get(0));
            q = querySynchronously(q);
            assertNotNull(q.results.documentLinks);
            assertThat(q.results.documentCount, is(1L));
            assertThat(q.results.documentLinks.get(0),
                    is(startState.documentSelfLink));
        }

        /**
         * Get list of subnet ranges for the subnet
         *
         * @throws Throwable
         */
        @Test
        public void testSubnetRangeLinksQuery() throws Throwable {

            // populate data
            String ipPrefix = "10.10.10.";
            for (int i = 1; i < 4; i++) {
                IPAddressState ipAddressState = buildValidStartState();
                ipAddressState.ipAddress = ipPrefix + i;
                ipAddressState.ipVersion = IPVersion.IPv4;
                ipAddressState.subnetRangeLink = SubnetRangeService.FACTORY_LINK  + "/subnet-range-A";
                IPAddressState startState = postServiceSynchronously(
                        IPAddressService.FACTORY_LINK,
                        ipAddressState, IPAddressState.class);
            }

            String kind = Utils.buildKind (IPAddressState.class);
            String propertyName = IPAddressState.FIELD_NAME_SUBNET_RANGE_LINK;

            QueryTask q = createDirectQueryTask(kind, propertyName,
                    SubnetRangeService.FACTORY_LINK + "/subnet-range-A");
            q = querySynchronously(q);
            assertNotNull(q.results.documentLinks);
            assertThat(q.results.documentCount, is(3L));
        }
    }
}