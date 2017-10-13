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
import java.util.HashSet;
import java.util.UUID;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.support.IPVersion;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost.ServiceNotFoundException;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.TenantService;

/**
 * This class implements tests for the {@link SubnetRangeService} class.
 */
@RunWith(SubnetRangeServiceTest.class)
@Suite.SuiteClasses({
        SubnetRangeServiceTest.ConstructorTest.class,
        SubnetRangeServiceTest.HandleCreateTest.class,
        SubnetRangeServiceTest.HandlePatchTest.class,
        SubnetRangeServiceTest.QueryTest.class
        })

public class SubnetRangeServiceTest extends Suite {

    public SubnetRangeServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    private static SubnetRangeService.SubnetRangeState buildValidSubnetRangeState(
            SubnetState subnetState) {
        SubnetRangeService.SubnetRangeState subnetRangeState = new SubnetRangeService.SubnetRangeState();
        subnetRangeState.id = UUID.randomUUID().toString();
        subnetRangeState.name = "test-range-1";
        subnetRangeState.startIPAddress = "192.130.120.110";
        subnetRangeState.endIPAddress = "192.130.120.140";
        subnetRangeState.ipVersion = IPVersion.IPv4;
        subnetRangeState.isDHCP = false;
        subnetRangeState.dnsServerAddresses = new ArrayList<>();
        subnetRangeState.dnsServerAddresses.add("1.2.3.4");
        subnetRangeState.dnsServerAddresses.add("5.6.7.8");
        subnetRangeState.dnsSearchDomains = new ArrayList<>();
        subnetRangeState.dnsSearchDomains.add("dnsServer1.corp.local");
        subnetRangeState.dnsSearchDomains.add("dnsServer2.corp.local");
        subnetRangeState.domain = "vmware.com";
        subnetRangeState.subnetLink = subnetState.documentSelfLink;
        subnetRangeState.tenantLinks = new ArrayList<>();
        subnetRangeState.tenantLinks.add("tenant-linkA");

        return subnetRangeState;
    }

    private static SubnetService.SubnetState buildValidSubnetState() {
        SubnetService.SubnetState subnetState = new SubnetState();
        subnetState.subnetCIDR = "192.130.120.0/24";
        subnetState.networkLink = UriUtils
                .buildUriPath(NetworkService.FACTORY_LINK, UUID.randomUUID().toString());
        subnetState.tenantLinks = new ArrayList<String>();
        subnetState.tagLinks = new HashSet<>();
        subnetState.gatewayAddress = "192.130.120.1";

        return subnetState;
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {
        private SubnetRangeService SubnetRangeService;

        @Before
        public void setupTest() {
            this.SubnetRangeService = new SubnetRangeService();
        }

        @Test
        public void testServiceOptions() {
            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.CONCURRENT_GET_HANDLING,
                    Service.ServiceOption.PERSISTENCE,
                    Service.ServiceOption.REPLICATION,
                    Service.ServiceOption.OWNER_SELECTION,
                    Service.ServiceOption.IDEMPOTENT_POST);
            assertThat(this.SubnetRangeService.getOptions(), is(expected));
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleCreateTest extends BaseModelTest {

        @Before
        public void setUp() throws Throwable {
            super.setUp();
            this.getHost().startFactory(new SubnetService());
            this.getHost().startFactory(new SubnetRangeService());
        }

        @Test
        public void testValidStartState() throws Throwable {
            SubnetService.SubnetState subnetRequest = buildValidSubnetState();
            SubnetService.SubnetState subnetState = postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    subnetRequest, SubnetService.SubnetState.class);

            SubnetRangeService.SubnetRangeState startState = buildValidSubnetRangeState(
                    subnetState);
            SubnetRangeService.SubnetRangeState returnState = postServiceSynchronously(
                    SubnetRangeService.FACTORY_LINK,
                    startState, SubnetRangeService.SubnetRangeState.class);

            assertNotNull(returnState);
            assertThat(returnState.id, is(startState.id));
            assertThat(returnState.name, is(startState.name));
            assertThat(returnState.startIPAddress, is(startState.startIPAddress));
            assertThat(returnState.endIPAddress, is(startState.endIPAddress));
            assertThat(returnState.ipVersion, is(startState.ipVersion));
            assertThat(returnState.isDHCP, is(startState.isDHCP));

            assertThat(returnState.tenantLinks.get(0),
                    is(startState.tenantLinks.get(0)));

        }

        @Test(expected = IllegalArgumentException.class)
        public void testMissingRequiredFieldsOnPut() throws Throwable {

            SubnetService.SubnetState subnetRequest = buildValidSubnetState();
            SubnetService.SubnetState subnetState = postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    subnetRequest, SubnetService.SubnetState.class);

            SubnetRangeService.SubnetRangeState startState = buildValidSubnetRangeState(
                    subnetState);
            SubnetRangeService.SubnetRangeState returnState = postServiceSynchronously(
                    SubnetRangeService.FACTORY_LINK,
                    startState,
                    SubnetRangeService.SubnetRangeState.class);
            returnState.startIPAddress = null;
            putServiceSynchronously(returnState.documentSelfLink, returnState);
            assertThat("exception expected for missing required fields on put", false);
        }

        @Test
        public void testValidPut() throws Throwable {

            SubnetService.SubnetState subnetRequest = buildValidSubnetState();
            SubnetService.SubnetState subnetState = postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    subnetRequest, SubnetService.SubnetState.class);

            postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    subnetState, SubnetService.SubnetState.class);

            SubnetRangeService.SubnetRangeState startState = buildValidSubnetRangeState(
                    subnetState);
            SubnetRangeService.SubnetRangeState returnState = postServiceSynchronously(
                    SubnetRangeService.FACTORY_LINK,
                    startState,
                    SubnetRangeService.SubnetRangeState.class);
            returnState.isDHCP = true;
            returnState.startIPAddress = "192.130.120.150";
            returnState.endIPAddress = "192.130.120.200";
            returnState.domain = "cnn.com";

            putServiceSynchronously(returnState.documentSelfLink, returnState);

            // Verify values
            SubnetRangeService.SubnetRangeState afterPutState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    SubnetRangeService.SubnetRangeState.class);

            assertEquals(returnState.isDHCP, afterPutState.isDHCP);
            assertEquals(returnState.startIPAddress, afterPutState.startIPAddress);
            assertEquals(returnState.endIPAddress, afterPutState.endIPAddress);
            assertEquals(returnState.domain, afterPutState.domain);
        }

        @Test(expected = IllegalArgumentException.class)
        public void testMissingRequiredFieldsOnPost() throws Throwable {
            SubnetService.SubnetState subnetRequest = buildValidSubnetState();
            SubnetService.SubnetState subnetState = postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    subnetRequest, SubnetService.SubnetState.class);

            postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    subnetState, SubnetService.SubnetState.class);

            SubnetRangeService.SubnetRangeState startState = buildValidSubnetRangeState(
                    subnetState);
            startState.startIPAddress = null;
            postServiceSynchronously(SubnetRangeService.FACTORY_LINK, startState,
                    SubnetRangeService.SubnetRangeState.class);
            assertThat("exception expected for missing required fields", false);
        }

        @Test
        @Ignore //Duplicate posts wont work because of newly added range overlap check
        public void testDuplicatePost() throws Throwable {

            SubnetService.SubnetState subnetRequest = buildValidSubnetState();
            SubnetService.SubnetState subnetState = postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    subnetRequest, SubnetService.SubnetState.class);

            postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    subnetState, SubnetService.SubnetState.class);

            SubnetRangeService.SubnetRangeState startState = buildValidSubnetRangeState(
                    subnetState);
            SubnetRangeService.SubnetRangeState returnState = postServiceSynchronously(
                    SubnetRangeService.FACTORY_LINK,
                    startState, SubnetRangeService.SubnetRangeState.class);

            assertNotNull(returnState);
            assertThat(returnState.name, is(startState.name));
            startState.name = startState.name + "-second";
            returnState = postServiceSynchronously(SubnetRangeService.FACTORY_LINK,
                    startState, SubnetRangeService.SubnetRangeState.class);
            assertThat(returnState.name, is(startState.name));
        }

        @Test
        public void testInvalidValuesOnPost() throws Throwable {

            SubnetService.SubnetState subnetRequest = buildValidSubnetState();
            SubnetService.SubnetState subnetState = postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    subnetRequest, SubnetService.SubnetState.class);

            postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    subnetState, SubnetService.SubnetState.class);

            SubnetRangeService.SubnetRangeState missingStartIP = buildValidSubnetRangeState(
                    subnetState);
            missingStartIP.startIPAddress = "";

            SubnetRangeService.SubnetRangeState invalidStartIP = buildValidSubnetRangeState(
                    subnetState);
            invalidStartIP.startIPAddress = "abc.1.1.1";

            SubnetRangeService.SubnetRangeState missingEndIP = buildValidSubnetRangeState(
                    subnetState);
            missingEndIP.endIPAddress = "";

            SubnetRangeService.SubnetRangeState invalidEndIP = buildValidSubnetRangeState(
                    subnetState);
            invalidEndIP.endIPAddress = "abc.1.1.1";

            SubnetRangeService.SubnetRangeState[] states = {
                    missingStartIP, invalidStartIP, missingEndIP, invalidEndIP };

            for (SubnetRangeService.SubnetRangeState state : states) {
                System.out.println("Subnet range: " + state.toString());
                try {
                    postServiceSynchronously(SubnetRangeService.FACTORY_LINK, state,
                            SubnetRangeService.SubnetRangeState.class);
                    assertThat("exception expected", false);
                } catch (Exception e) {
                    assertThat("exception expected",
                            e instanceof LocalizableValidationException);
                }
            }
        }

        @Test(expected = LocalizableValidationException.class)
        public void testIpOutOfCidr() throws Throwable {

            SubnetService.SubnetState subnetRequest = buildValidSubnetState();
            SubnetService.SubnetState subnetState = postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    subnetRequest, SubnetService.SubnetState.class);
            subnetState.subnetCIDR = "1.2.3.0/24";

            postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    subnetState, SubnetService.SubnetState.class);

            SubnetRangeService.SubnetRangeState subnetRangeState = buildValidSubnetRangeState(
                    subnetState);

            postServiceSynchronously(
                    SubnetRangeService.FACTORY_LINK,
                    subnetRangeState, SubnetRangeService.SubnetRangeState.class);
        }

        @Test(expected = ServiceNotFoundException.class)
        public void testInvalidSubnetLink() throws Throwable {

            SubnetService.SubnetState subnetRequest = buildValidSubnetState();
            SubnetService.SubnetState subnetState = postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    subnetRequest, SubnetService.SubnetState.class);

            postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    subnetState, SubnetService.SubnetState.class);

            SubnetRangeService.SubnetRangeState subnetRangeState = buildValidSubnetRangeState(
                    subnetState);
            subnetRangeState.subnetLink = "doesNotExist";

            postServiceSynchronously(
                    SubnetRangeService.FACTORY_LINK,
                    subnetRangeState, SubnetRangeService.SubnetRangeState.class);
        }

        @Test
        public void testNullSubnetLink() throws Throwable {

            SubnetService.SubnetState subnetRequest = buildValidSubnetState();
            SubnetService.SubnetState subnetState = postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    subnetRequest, SubnetService.SubnetState.class);

            postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    subnetState, SubnetService.SubnetState.class);

            SubnetRangeService.SubnetRangeState subnetRangeState = buildValidSubnetRangeState(
                    subnetState);
            subnetRangeState.subnetLink = null;

            postServiceSynchronously(
                    SubnetRangeService.FACTORY_LINK,
                    subnetRangeState, SubnetRangeService.SubnetRangeState.class);
        }

        @Test(expected = LocalizableValidationException.class)
        public void testRangeOverlap() throws Throwable {

            SubnetService.SubnetState subnetRequest = buildValidSubnetState();
            SubnetService.SubnetState subnetState = postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    subnetRequest, SubnetService.SubnetState.class);

            SubnetRangeService.SubnetRangeState startState = buildValidSubnetRangeState(
                    subnetState);

            //Post same range twice. It should throw an exception due to iprange overlap
            postServiceSynchronously(SubnetRangeService.FACTORY_LINK, startState,
                    SubnetRangeService.SubnetRangeState.class);
            postServiceSynchronously(SubnetRangeService.FACTORY_LINK, startState,
                    SubnetRangeService.SubnetRangeState.class);

        }

    }

    /**
     * This class implements tests for the handlePatch method.
     */
    public static class HandlePatchTest extends BaseModelTest {

        @Before
        public void setUp() throws Throwable {
            super.setUp();
            this.getHost().startFactory(new SubnetRangeService());
            this.getHost().startFactory(new SubnetService());
        }

        @Test
        public void testPatch() throws Throwable {

            SubnetService.SubnetState subnetRequest = buildValidSubnetState();
            SubnetService.SubnetState subnetState = postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    subnetRequest, SubnetService.SubnetState.class);

            postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    subnetState, SubnetService.SubnetState.class);

            SubnetRangeService.SubnetRangeState startState = buildValidSubnetRangeState(
                    subnetState);

            SubnetRangeService.SubnetRangeState returnState = postServiceSynchronously(
                    SubnetRangeService.FACTORY_LINK,
                    startState, SubnetRangeService.SubnetRangeState.class);

            // Patch 2 values
            SubnetRangeService.SubnetRangeState patchState = new SubnetRangeService.SubnetRangeState();
            patchState.startIPAddress = "192.130.120.100";
            patchState.isDHCP = true;
            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);

            returnState = getServiceSynchronously(returnState.documentSelfLink,
                    SubnetRangeService.SubnetRangeState.class);

            assertThat(patchState.startIPAddress, is(returnState.startIPAddress));
            assertThat(patchState.isDHCP, is(returnState.isDHCP));
            assertThat(startState.name, is(returnState.name));
            assertThat(startState.subnetLink, is(returnState.subnetLink));
        }

        @Test
        public void testPatchNoChange() throws Throwable {

            SubnetService.SubnetState subnetRequest = buildValidSubnetState();
            SubnetService.SubnetState subnetState = postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    subnetRequest, SubnetService.SubnetState.class);

            postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    subnetState, SubnetService.SubnetState.class);

            SubnetRangeService.SubnetRangeState startState = buildValidSubnetRangeState(
                    subnetState);

            SubnetRangeService.SubnetRangeState returnState = postServiceSynchronously(
                    SubnetRangeService.FACTORY_LINK,
                    startState, SubnetRangeService.SubnetRangeState.class);

            // Same start ip address
            SubnetRangeService.SubnetRangeState patchState = new SubnetRangeService.SubnetRangeState();
            patchState.startIPAddress = startState.startIPAddress;

            patchServiceSynchronously(returnState.documentSelfLink, patchState);

            // Verify no change
            SubnetRangeService.SubnetRangeState afterPatchState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    SubnetRangeService.SubnetRangeState.class);
            assertEquals(patchState.startIPAddress, afterPatchState.startIPAddress);
            assertEquals(startState.documentVersion, afterPatchState.documentVersion);
        }

        @Test
        public void testPatchDnsServers() throws Throwable {

            SubnetService.SubnetState subnetRequest = buildValidSubnetState();
            SubnetService.SubnetState subnetState = postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    subnetRequest, SubnetService.SubnetState.class);

            postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    subnetState, SubnetService.SubnetState.class);

            SubnetRangeService.SubnetRangeState startState = buildValidSubnetRangeState(
                    subnetState);

            SubnetRangeService.SubnetRangeState returnState = postServiceSynchronously(
                    SubnetRangeService.FACTORY_LINK,
                    startState, SubnetRangeService.SubnetRangeState.class);

            // Update value for dns server addresses
            SubnetRangeService.SubnetRangeState patchState = new SubnetRangeService.SubnetRangeState();
            patchState.dnsServerAddresses = new ArrayList<>();
            patchState.dnsServerAddresses.addAll(startState.dnsServerAddresses);
            patchState.dnsSearchDomains = new ArrayList<>();
            patchState.dnsSearchDomains.addAll(startState.dnsSearchDomains);

            patchServiceSynchronously(returnState.documentSelfLink, patchState);

            // Verify no change after patching with the same set
            SubnetRangeService.SubnetRangeState afterPatchState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    SubnetRangeService.SubnetRangeState.class);

            assertEquals(startState.dnsServerAddresses, afterPatchState.dnsServerAddresses);
            assertEquals(startState.dnsSearchDomains, afterPatchState.dnsSearchDomains);
        }

        @Test
        public void testPatchSingleAssignmentProperty() throws Throwable {

            SubnetService.SubnetState subnetRequest = buildValidSubnetState();
            SubnetService.SubnetState subnetState = postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    subnetRequest, SubnetService.SubnetState.class);

            postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    subnetState, SubnetService.SubnetState.class);

            SubnetRangeService.SubnetRangeState startState = buildValidSubnetRangeState(
                    subnetState);

            SubnetRangeService.SubnetRangeState returnState = postServiceSynchronously(
                    SubnetRangeService.FACTORY_LINK,
                    startState, SubnetRangeService.SubnetRangeState.class);

            // New value for subnet link, even when marked single assignment
            SubnetRangeService.SubnetRangeState patchState = new SubnetRangeService.SubnetRangeState();
            patchState.subnetLink = returnState.subnetLink + "-1";

            patchServiceSynchronously(returnState.documentSelfLink, patchState);

            // Verify no change
            SubnetRangeService.SubnetRangeState afterPatchState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    SubnetRangeService.SubnetRangeState.class);

            assertEquals(startState.subnetLink, afterPatchState.subnetLink);
            assertEquals(startState.documentVersion, afterPatchState.documentVersion);
        }

        @Test(expected = LocalizableValidationException.class)
        public void testInvalidPatch() throws Throwable {
            SubnetService.SubnetState subnetRequest = buildValidSubnetState();
            SubnetService.SubnetState subnetState = postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    subnetRequest, SubnetService.SubnetState.class);

            postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    subnetState, SubnetService.SubnetState.class);

            SubnetRangeService.SubnetRangeState startState = buildValidSubnetRangeState(
                    subnetState);

            SubnetRangeService.SubnetRangeState returnState = postServiceSynchronously(
                    SubnetRangeService.FACTORY_LINK,
                    startState, SubnetRangeService.SubnetRangeState.class);

            // Invalid start ip address
            SubnetRangeService.SubnetRangeState patchState = new SubnetRangeService.SubnetRangeState();
            patchState.startIPAddress = "192.130.140.120";

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);
            assertThat("Should have failed", false);
        }

        /**
         * Patching a property which is of type set.
         * The patch adds values to the set, there is no way to remove, unless we
         * code a special handling
         *
         * @throws Throwable
         */
        @Test
        public void testListPatch() throws Throwable {

            SubnetService.SubnetState subnetRequest = buildValidSubnetState();
            SubnetService.SubnetState subnetState = postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    subnetRequest, SubnetService.SubnetState.class);

            postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    subnetState, SubnetService.SubnetState.class);

            SubnetRangeService.SubnetRangeState startState = buildValidSubnetRangeState(
                    subnetState);

            SubnetRangeService.SubnetRangeState returnState = postServiceSynchronously(
                    SubnetRangeService.FACTORY_LINK,
                    startState, SubnetRangeService.SubnetRangeState.class);

            assertThat("DNS server addresses set", returnState.dnsServerAddresses != null);
            assertThat("There are 2 DNS server addresses in the set",
                    returnState.dnsServerAddresses.size() == 2);

            // Patch with single dns server; it will replace the current list
            SubnetRangeService.SubnetRangeState patchState = new SubnetRangeService.SubnetRangeState();
            patchState.dnsServerAddresses = new ArrayList<>();
            patchState.dnsServerAddresses.add("99.99.99.99");

            patchServiceSynchronously(returnState.documentSelfLink, patchState);

            returnState = getServiceSynchronously(returnState.documentSelfLink,
                    SubnetRangeService.SubnetRangeState.class);
            assertNotNull(returnState);
            assertNotNull(returnState.dnsServerAddresses);
            assertEquals(patchState.dnsServerAddresses, returnState.dnsServerAddresses);
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
        }

        @Test
        public void testTenantLinksQuery() throws Throwable {
            SubnetService.SubnetState subnetRequest = buildValidSubnetState();
            SubnetService.SubnetState subnetState = postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    subnetRequest, SubnetService.SubnetState.class);
            postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    subnetState, SubnetService.SubnetState.class);

            SubnetRangeService.SubnetRangeState subnetRangeState = buildValidSubnetRangeState(
                    subnetState);
            URI tenantUri = UriUtils.buildFactoryUri(this.host, TenantService.class);
            subnetRangeState.tenantLinks = new ArrayList<>();
            subnetRangeState.tenantLinks.add(UriUtils.buildUriPath(
                    tenantUri.getPath(), "tenantA"));
            SubnetRangeService.SubnetRangeState startState = postServiceSynchronously(
                    SubnetRangeService.FACTORY_LINK,
                    subnetRangeState, SubnetRangeService.SubnetRangeState.class);

            String kind = Utils.buildKind(SubnetRangeService.SubnetRangeState.class);
            String propertyName = QueryTask.QuerySpecification
                    .buildCollectionItemName(ResourceState.FIELD_NAME_TENANT_LINKS);

            QueryTask q = createDirectQueryTask(kind, propertyName,
                    subnetRangeState.tenantLinks.get(0));
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
        public void testSubnetLinksQuery() throws Throwable {

            SubnetService.SubnetState subnetRequest = buildValidSubnetState();
            SubnetService.SubnetState subnetState = postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    subnetRequest, SubnetService.SubnetState.class);
            subnetState.subnetCIDR = "10.10.0.0/16";

            postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    subnetState, SubnetService.SubnetState.class);

            // populate data
            String[] ipPrefixes = { "10.10.10.", "10.10.20.", "10.10.30." };
            for (String ipPrefix : ipPrefixes) {
                SubnetRangeService.SubnetRangeState subnetRangeState = buildValidSubnetRangeState(
                        subnetState);
                subnetRangeState.startIPAddress = ipPrefix + "1";
                subnetRangeState.endIPAddress = ipPrefix + "100";
                subnetRangeState.ipVersion = IPVersion.IPv4;
                subnetRangeState.isDHCP = false;
                postServiceSynchronously(
                        SubnetRangeService.FACTORY_LINK,
                        subnetRangeState, SubnetRangeService.SubnetRangeState.class);
            }

            String kind = Utils.buildKind(SubnetRangeService.SubnetRangeState.class);
            String propertyName = SubnetRangeService.SubnetRangeState.FIELD_NAME_SUBNET_LINK;
            String subnetDocumentLink = subnetState.documentSelfLink;

            QueryTask q = createDirectQueryTask(kind, propertyName,
                    subnetDocumentLink);
            q = querySynchronously(q);
            assertNotNull(q.results.documentLinks);
            assertThat(q.results.documentCount, is(3L));
        }

    }
}