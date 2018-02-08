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

import static com.vmware.photon.controller.model.constants.PhotonModelConstants.NETWORK_SUBTYPE_SUBNET_STATE;

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
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.TenantService;

/**
 * This class implements tests for the {@link SubnetService} class.
 */
@RunWith(SubnetServiceTest.class)
@SuiteClasses({ SubnetServiceTest.ConstructorTest.class,
        SubnetServiceTest.HandleStartTest.class,
        SubnetServiceTest.HandlePatchTest.class,
        SubnetServiceTest.HandlePutTest.class,
        SubnetServiceTest.QueryTest.class })
public class SubnetServiceTest extends Suite {

    public SubnetServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    private static SubnetState buildValidStartState(boolean assignHost) {
        SubnetState subnetState = new SubnetState();
        subnetState.id = UUID.randomUUID().toString();
        subnetState.name = "networkName";
        subnetState.subnetCIDR = "10.0.0.0/10";
        subnetState.networkLink = NetworkService.FACTORY_LINK + "/mynet";
        subnetState.tenantLinks = new ArrayList<>();
        subnetState.tenantLinks.add("tenant-linkA");
        subnetState.dnsServerAddresses = new ArrayList<>();
        subnetState.dnsServerAddresses.addAll(Arrays.asList("1.2.3.4", "11.22.33.44"));
        subnetState.dnsSearchDomains = new ArrayList<>();
        subnetState.dnsSearchDomains.addAll(Arrays.asList("foo.bar", "subdomain.foo.bar"));
        if (assignHost) {
            subnetState.computeHostLink = "host-1";
        }

        return subnetState;
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {
        private SubnetService subnetService = new SubnetService();

        @Before
        public void setupTest() {
            this.subnetService = new SubnetService();
        }

        @Test
        public void testServiceOptions() {
            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.CONCURRENT_GET_HANDLING,
                    Service.ServiceOption.PERSISTENCE,
                    Service.ServiceOption.REPLICATION,
                    Service.ServiceOption.OWNER_SELECTION,
                    Service.ServiceOption.IDEMPOTENT_POST);
            assertThat(this.subnetService.getOptions(), is(expected));
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleStartTest extends BaseModelTest {
        @Test
        public void testValidStartState() throws Throwable {
            SubnetState startState = buildValidStartState(false);
            SubnetState returnState = postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    startState, SubnetState.class);

            assertNotNull(returnState);
            assertEquals(returnState.type, NETWORK_SUBTYPE_SUBNET_STATE);
            assertThat(returnState.id, is(startState.id));
            assertThat(returnState.name, is(startState.name));
            assertThat(returnState.subnetCIDR, is(startState.subnetCIDR));
            assertThat(returnState.tenantLinks.get(0),
                    is(startState.tenantLinks.get(0)));
        }

        @Test
        public void testValidStartStateWithHost() throws Throwable {
            SubnetState startState = buildValidStartState(true);
            SubnetState returnState = postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    startState, SubnetState.class);

            assertNotNull(returnState);
            assertEquals(returnState.type, NETWORK_SUBTYPE_SUBNET_STATE);
            assertThat(returnState.id, is(startState.id));
            assertThat(returnState.name, is(startState.name));
            assertThat(returnState.subnetCIDR, is(startState.subnetCIDR));
            assertThat(returnState.tenantLinks.get(0),
                    is(startState.tenantLinks.get(0)));
            assertThat(returnState.regionId, is(startState.regionId));
            assertThat(returnState.instanceAdapterReference,
                    is(startState.instanceAdapterReference));
            assertThat(returnState.computeHostLink, is(startState.computeHostLink));
            assertNotNull(returnState.documentCreationTimeMicros);
        }

        @Test
        public void testInvalidStartState() throws Throwable {
            SubnetState startState = buildValidStartState(false);
            startState.subnetCIDR = null;
            SubnetState returnState = postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    startState, SubnetState.class);

            assertNotNull(returnState);
            assertEquals(returnState.type, NETWORK_SUBTYPE_SUBNET_STATE);
            assertThat(returnState.id, is(startState.id));
            assertThat(returnState.name, is(startState.name));
            assertThat(returnState.subnetCIDR, is(startState.subnetCIDR));
            assertThat(returnState.tenantLinks.get(0),
                    is(startState.tenantLinks.get(0)));
        }

        @Test
        public void testDuplicatePost() throws Throwable {
            SubnetState startState = buildValidStartState(false);
            SubnetState returnState = postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    startState, SubnetState.class);

            assertNotNull(returnState);
            assertEquals(returnState.type, NETWORK_SUBTYPE_SUBNET_STATE);
            assertThat(returnState.name, is(startState.name));
            startState.name = "new-name";
            returnState = postServiceSynchronously(SubnetService.FACTORY_LINK,
                    startState, SubnetState.class);
            assertThat(returnState.name, is(startState.name));
        }

        @Test
        public void testDuplicatePostAssignComputeHost() throws Throwable {
            SubnetState startState = buildValidStartState(false);
            SubnetState returnState = postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    startState, SubnetState.class);

            assertNotNull(returnState);
            assertEquals(returnState.type, NETWORK_SUBTYPE_SUBNET_STATE);
            assertThat(returnState.name, is(startState.name));
            assertNull(returnState.computeHostLink);
            startState.name = "new-name";
            startState.computeHostLink = "host-1";
            returnState = postServiceSynchronously(SubnetService.FACTORY_LINK,
                    startState, SubnetState.class);
            assertThat(returnState.name, is(startState.name));
            assertNotNull(returnState.computeHostLink);
            assertThat(returnState.computeHostLink, is(startState.computeHostLink));
        }

        @Test
        public void testDuplicatePostModifyComputeHost() throws Throwable {
            SubnetState startState = buildValidStartState(true);
            SubnetState returnState = postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    startState, SubnetState.class);

            assertNotNull(returnState);
            assertEquals(returnState.type, NETWORK_SUBTYPE_SUBNET_STATE);
            assertThat(returnState.name, is(startState.name));
            assertNotNull(returnState.computeHostLink);

            returnState.computeHostLink = "host-2";
            postServiceSynchronously(SubnetService.FACTORY_LINK,
                    returnState, SubnetState.class, IllegalArgumentException.class);
        }

        @Test
        public void testDuplicatePostModifyCreationTime() throws Throwable {
            SubnetState startState = buildValidStartState(false);
            SubnetState returnState = postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    startState, SubnetState.class);

            assertNotNull(returnState);
            assertNotNull(returnState.documentCreationTimeMicros);

            long originalTime = returnState.documentCreationTimeMicros;
            returnState.documentCreationTimeMicros = Utils.getNowMicrosUtc();

            returnState = postServiceSynchronously(SubnetService.FACTORY_LINK,
                    returnState, SubnetState.class);
            assertThat(originalTime, is(returnState.documentCreationTimeMicros));
        }

        @Test
        public void testInvalidValues() throws Throwable {
            SubnetState invalidSubnet1 = buildValidStartState(false);
            SubnetState invalidSubnet2 = buildValidStartState(false);
            SubnetState invalidSubnet3 = buildValidStartState(false);
            SubnetState invalidSubnet4 = buildValidStartState(false);
            SubnetState invalidSubnet5 = buildValidStartState(false);

            invalidSubnet1.subnetCIDR = "10.0.0.0";
            // invalid IP
            invalidSubnet2.subnetCIDR = "10.0.0.A";
            // invalid Subnet range
            invalidSubnet3.subnetCIDR = "10.0.0.0/33";
            // invalid Subnet range
            invalidSubnet4.subnetCIDR = "10.0.0.0/-1";
            // invalid Subnet separator
            invalidSubnet5.subnetCIDR = "10.0.0.0\\0";

            SubnetState[] states = {
                    invalidSubnet1, invalidSubnet2, invalidSubnet3,
                    invalidSubnet4, invalidSubnet5 };
            for (SubnetState state : states) {
                postServiceSynchronously(SubnetService.FACTORY_LINK,
                        state, SubnetState.class,
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
            SubnetState startState = buildValidStartState(false);

            SubnetState returnState = postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    startState, SubnetState.class);
            assertNull(returnState.computeHostLink);
            assertNotNull(returnState.documentCreationTimeMicros);

            SubnetState patchState = new SubnetState();
            patchState.name = "patchNetworkName";
            patchState.subnetCIDR = "152.151.150.222/22";
            patchState.customProperties = new HashMap<>();
            patchState.customProperties.put("patchKey", "patchValue");
            patchState.tenantLinks = new ArrayList<String>();
            patchState.tenantLinks.add("tenant1");
            patchState.groupLinks = new HashSet<String>();
            patchState.groupLinks.add("group1");
            patchState.zoneId = "my-zone";

            patchState.dnsServerAddresses = new ArrayList<>();
            patchState.dnsServerAddresses.addAll(startState.dnsServerAddresses);
            patchState.dnsServerAddresses.add("88.88.88.88");

            patchState.dnsSearchDomains = new ArrayList<>();
            patchState.dnsSearchDomains.addAll(startState.dnsSearchDomains);
            patchState.dnsSearchDomains.add("testsubdomain.foo.bar");
            patchState.computeHostLink = "host-1";

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);

            returnState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    SubnetState.class);

            assertThat(returnState.name, is(patchState.name));
            assertThat(returnState.subnetCIDR, is(patchState.subnetCIDR));
            assertThat(returnState.customProperties,
                    is(patchState.customProperties));
            assertEquals(2, returnState.tenantLinks.size());
            assertEquals(patchState.groupLinks, returnState.groupLinks);
            assertEquals(patchState.zoneId, returnState.zoneId);

            // assert that order in ordered lists is preserved and there are no duplicate entries
            assertEquals(patchState.dnsServerAddresses, returnState.dnsServerAddresses);
            assertEquals(patchState.dnsSearchDomains, returnState.dnsSearchDomains);
            assertNotNull(returnState.computeHostLink);
            assertThat(returnState.computeHostLink, is(patchState.computeHostLink));
        }

        @Test
        public void testPatchAssignHost() throws Throwable {
            SubnetState startState = buildValidStartState(false);

            SubnetState returnState = postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    startState, SubnetState.class);
            assertNull(returnState.computeHostLink);

            SubnetState patchState = new SubnetState();
            patchState.computeHostLink = "host-1";

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);

            returnState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    SubnetState.class);
            assertNotNull(returnState.computeHostLink);
            assertThat(returnState.computeHostLink, is(patchState.computeHostLink));
        }

        @Test(expected = IllegalArgumentException.class)
        public void testPatchModifyHost() throws Throwable {
            SubnetState startState = buildValidStartState(true);

            SubnetState returnState = postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    startState, SubnetState.class);
            assertNotNull(returnState.computeHostLink);

            SubnetState patchState = new SubnetState();
            patchState.computeHostLink = "host-2";

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);
        }

        @Test
        public void testPatchModifyCreationTime() throws Throwable {
            SubnetState startState = buildValidStartState(false);

            SubnetState returnState = postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    startState, SubnetState.class);
            assertNotNull(returnState.documentCreationTimeMicros);
            long originalCreationTime = returnState.documentCreationTimeMicros;

            SubnetState patchState = new SubnetState();
            long currentCreationTime = Utils.getNowMicrosUtc();
            patchState.documentCreationTimeMicros = currentCreationTime;

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);

            returnState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    SubnetState.class);
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
            SubnetState startState = buildValidStartState(false);
            SubnetState returnState = postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    startState, SubnetState.class);

            assertNotNull(returnState);

            SubnetState newState = new SubnetState();
            newState.id = UUID.randomUUID().toString();
            newState.name = "networkName";
            newState.subnetCIDR = "10.0.0.0/10";
            newState.networkLink = NetworkService.FACTORY_LINK + "/mynet";
            newState.tenantLinks = new ArrayList<>();
            newState.tenantLinks.add("tenant-linkA");
            newState.dnsServerAddresses = new ArrayList<>();
            newState.dnsServerAddresses.addAll(Arrays.asList("1.2.3.4", "11.22.33.44"));
            newState.dnsSearchDomains = new ArrayList<>();
            newState.dnsSearchDomains.addAll(Arrays.asList("foo.bar", "subdomain.foo.bar"));
            newState.documentCreationTimeMicros = returnState.documentCreationTimeMicros;

            putServiceSynchronously(returnState.documentSelfLink,
                    newState);

            SubnetState getState = getServiceSynchronously(returnState.documentSelfLink,
                    SubnetState.class);
            assertThat(getState.id, is(newState.id));
            assertThat(getState.name, is(newState.name));
            assertThat(getState.subnetCIDR, is(newState.subnetCIDR));
            assertThat(getState.networkLink, is(newState.networkLink));
            assertEquals(getState.tenantLinks, newState.tenantLinks);
            assertEquals(getState.groupLinks, newState.groupLinks);
            assertEquals(getState.dnsServerAddresses, newState.dnsServerAddresses);
            assertEquals(getState.dnsSearchDomains, newState.dnsSearchDomains);
            // make sure launchTimeMicros was preserved
            assertEquals(getState.creationTimeMicros, returnState.creationTimeMicros);
            assertEquals(getState.documentCreationTimeMicros, returnState.documentCreationTimeMicros);
        }

        @Test
        public void testPutModifyCreationTime() throws Throwable {
            SubnetState startState = buildValidStartState(false);
            SubnetState returnState = postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    startState, SubnetState.class);

            assertNotNull(returnState);

            SubnetState newState = new SubnetState();
            newState.id = UUID.randomUUID().toString();
            newState.name = "networkName";
            newState.subnetCIDR = "10.0.0.0/10";
            newState.networkLink = NetworkService.FACTORY_LINK + "/mynet";
            newState.tenantLinks = new ArrayList<>();
            newState.tenantLinks.add("tenant-linkA");
            newState.dnsServerAddresses = new ArrayList<>();
            newState.dnsServerAddresses.addAll(Arrays.asList("1.2.3.4", "11.22.33.44"));
            newState.dnsSearchDomains = new ArrayList<>();
            newState.dnsSearchDomains.addAll(Arrays.asList("foo.bar", "subdomain.foo.bar"));

            long currentTime = Utils.getNowMicrosUtc();
            newState.documentCreationTimeMicros = currentTime;

            putServiceSynchronously(returnState.documentSelfLink, newState);

            SubnetState getState = getServiceSynchronously(
                    returnState.documentSelfLink, SubnetState.class);
            assertThat(getState.documentCreationTimeMicros, is(returnState.documentCreationTimeMicros));
        }

        @Test(expected = IllegalArgumentException.class)
        public void testPutModifyHost() throws Throwable {
            SubnetState startState = buildValidStartState(true);

            SubnetState returnState = postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    startState, SubnetState.class);
            assertNotNull(returnState.documentCreationTimeMicros);
            assertNotNull(returnState.computeHostLink);

            SubnetState newState = new SubnetState();
            newState.id = UUID.randomUUID().toString();
            newState.name = "networkName";
            newState.subnetCIDR = "10.0.0.0/10";
            newState.networkLink = NetworkService.FACTORY_LINK + "/mynet";
            newState.tenantLinks = new ArrayList<>();
            newState.tenantLinks.add("tenant-linkA");
            newState.dnsServerAddresses = new ArrayList<>();
            newState.dnsServerAddresses.addAll(Arrays.asList("1.2.3.4", "11.22.33.44"));
            newState.dnsSearchDomains = new ArrayList<>();
            newState.dnsSearchDomains.addAll(Arrays.asList("foo.bar", "subdomain.foo.bar"));
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
            SubnetState subnetState = buildValidStartState(false);
            URI tenantUri = UriUtils.buildFactoryUri(this.host, TenantService.class);
            subnetState.tenantLinks = new ArrayList<>();
            subnetState.tenantLinks.add(UriUtils.buildUriPath(
                    tenantUri.getPath(), "tenantA"));
            SubnetState startState = postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    subnetState, SubnetState.class);

            String kind = Utils.buildKind(SubnetState.class);
            String propertyName = QueryTask.QuerySpecification
                    .buildCollectionItemName(ResourceState.FIELD_NAME_TENANT_LINKS);

            QueryTask q = createDirectQueryTask(kind, propertyName,
                    subnetState.tenantLinks.get(0));
            q = querySynchronously(q);
            assertNotNull(q.results.documentLinks);
            assertThat(q.results.documentCount, is(1L));
            assertThat(q.results.documentLinks.get(0),
                    is(startState.documentSelfLink));
        }
    }

}