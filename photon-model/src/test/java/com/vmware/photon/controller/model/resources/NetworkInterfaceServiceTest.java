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
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import static com.vmware.photon.controller.model.constants.PhotonModelConstants.NETWORK_SUBTYPE_NETWORK_INTERFACE_STATE;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
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
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceStateWithDescription;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.TenantService;

/**
 * This class implements tests for the {@link NetworkInterfaceService} class.
 */
@RunWith(NetworkInterfaceServiceTest.class)
@SuiteClasses({ NetworkInterfaceServiceTest.ConstructorTest.class,
        NetworkInterfaceServiceTest.HandleGetTest.class,
        NetworkInterfaceServiceTest.HandleStartTest.class,
        NetworkInterfaceServiceTest.HandlePatchTest.class,
        NetworkInterfaceServiceTest.HandlePutTest.class,
        NetworkInterfaceServiceTest.QueryTest.class })
public class NetworkInterfaceServiceTest extends Suite {

    public NetworkInterfaceServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    private static NetworkInterfaceState buildValidStartState(boolean assignHost) {

        NetworkInterfaceState networkInterfaceState = new NetworkInterfaceState();

        networkInterfaceState.id = UUID.randomUUID().toString();
        networkInterfaceState.name = NetworkInterfaceServiceTest.class.getSimpleName();
        networkInterfaceState.address = "9.9.9.9";
        networkInterfaceState.securityGroupLinks = Collections.singletonList
                ("/resources/security-groups/1");
        networkInterfaceState.networkInterfaceDescriptionLink = "/resources/nicDesc/nicDesc9";
        networkInterfaceState.subnetLink = "/resources/subnet/subnet9";
        if (assignHost) {
            networkInterfaceState.computeHostLink = "host-1";
        }

        return networkInterfaceState;
    }

    private static NetworkInterfaceStateWithDescription buildValidStartState(
            NetworkInterfaceDescription nd) {

        NetworkInterfaceStateWithDescription niStateWithDesc = new NetworkInterfaceStateWithDescription();

        niStateWithDesc.id = UUID.randomUUID().toString();
        niStateWithDesc.name = NetworkInterfaceServiceTest.class.getSimpleName();
        niStateWithDesc.networkLink = "/resources/network/net9";
        niStateWithDesc.subnetLink = "/resources/subnet/subnet9";
        niStateWithDesc.securityGroupLinks = Collections.singletonList("/resources/firewall/fw9");
        niStateWithDesc.networkInterfaceDescriptionLink = nd.documentSelfLink;

        niStateWithDesc.description = nd;

        return niStateWithDesc;
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {
        private NetworkInterfaceService networkInterfaceService;

        @Before
        public void setupTest() {
            this.networkInterfaceService = new NetworkInterfaceService();
        }

        @Test
        public void testServiceOptions() {
            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.CONCURRENT_GET_HANDLING,
                    Service.ServiceOption.PERSISTENCE,
                    Service.ServiceOption.REPLICATION,
                    Service.ServiceOption.OWNER_SELECTION,
                    Service.ServiceOption.IDEMPOTENT_POST);

            assertThat(this.networkInterfaceService.getOptions(), is(expected));
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleStartTest extends BaseModelTest {
        @Test
        public void testValidStartState() throws Throwable {
            NetworkInterfaceState startState = buildValidStartState(false);
            NetworkInterfaceState returnState = postServiceSynchronously(
                    NetworkInterfaceService.FACTORY_LINK,
                    startState,
                    NetworkInterfaceState.class);

            assertEquals(returnState.type, NETWORK_SUBTYPE_NETWORK_INTERFACE_STATE);
            assertNotNull(returnState);
            assertThat(returnState.id, is(startState.id));
            assertThat(returnState.address, is(startState.address));
        }

        @Test
        public void testValidStartStateWithHost() throws Throwable {
            NetworkInterfaceState startState = buildValidStartState(true);
            NetworkInterfaceState returnState = postServiceSynchronously(
                    NetworkInterfaceService.FACTORY_LINK,
                    startState,
                    NetworkInterfaceState.class);

            assertEquals(returnState.type, NETWORK_SUBTYPE_NETWORK_INTERFACE_STATE);
            assertNotNull(returnState);
            assertThat(returnState.id, is(startState.id));
            assertThat(returnState.address, is(startState.address));
            assertThat(returnState.computeHostLink, is(startState.computeHostLink));
        }

        @Test
        public void testDuplicatePost() throws Throwable {
            NetworkInterfaceState startState = buildValidStartState(false);
            NetworkInterfaceState returnState = postServiceSynchronously(
                    NetworkInterfaceService.FACTORY_LINK,
                    startState,
                    NetworkInterfaceState.class);

            assertEquals(returnState.type, NETWORK_SUBTYPE_NETWORK_INTERFACE_STATE);
            assertNotNull(returnState);
            assertNull(returnState.computeHostLink);

            returnState = postServiceSynchronously(
                    NetworkInterfaceService.FACTORY_LINK,
                    startState,
                    NetworkInterfaceState.class);

        }

        @Test
        public void testDuplicatePostAssignComputeHost() throws Throwable {
            NetworkInterfaceState startState = buildValidStartState(false);
            NetworkInterfaceState returnState = postServiceSynchronously(
                    NetworkInterfaceService.FACTORY_LINK,
                    startState, NetworkInterfaceState.class);

            assertEquals(returnState.type, NETWORK_SUBTYPE_NETWORK_INTERFACE_STATE);
            assertNotNull(returnState);
            assertNull(returnState.computeHostLink);

            startState.computeHostLink = "host-1";
            returnState = postServiceSynchronously(
                    NetworkInterfaceService.FACTORY_LINK,
                    startState,
                    NetworkInterfaceState.class);
            assertThat(returnState.name, is(startState.name));
            assertNotNull(returnState.computeHostLink);
            assertThat(returnState.computeHostLink, is(startState.computeHostLink));
        }

        @Test
        public void testDuplicatePostModifyComputeHost() throws Throwable {
            NetworkInterfaceState startState = buildValidStartState(true);
            NetworkInterfaceState returnState = postServiceSynchronously(
                    NetworkInterfaceService.FACTORY_LINK,
                    startState, NetworkInterfaceState.class);

            assertEquals(returnState.type, NETWORK_SUBTYPE_NETWORK_INTERFACE_STATE);
            assertNotNull(returnState);
            assertNotNull(returnState.computeHostLink);

            returnState.computeHostLink = "host-2";
            postServiceSynchronously(NetworkInterfaceService.FACTORY_LINK,
                    returnState, NetworkInterfaceState.class, IllegalArgumentException.class);
        }

        @Test
        public void testDuplicatePostModifyCreationTime() throws Throwable {
            NetworkInterfaceState startState = buildValidStartState(false);
            NetworkInterfaceState returnState = postServiceSynchronously(
                    NetworkInterfaceService.FACTORY_LINK,
                    startState, NetworkInterfaceState.class);

            assertNotNull(returnState);
            assertNotNull(returnState.documentCreationTimeMicros);

            returnState.documentCreationTimeMicros = Utils.getNowMicrosUtc();

            postServiceSynchronously(NetworkInterfaceService.FACTORY_LINK,
                    returnState, NetworkInterfaceState.class, IllegalArgumentException.class);
        }

        @Test
        public void testMissingId() throws Throwable {
            NetworkInterfaceState startState = buildValidStartState(false);
            startState.id = null;

            NetworkInterfaceState returnState = postServiceSynchronously(
                    NetworkInterfaceService.FACTORY_LINK,
                    startState,
                    NetworkInterfaceState.class);

            assertNotNull(returnState);
            assertNotNull(returnState.id);
        }

        @Test
        public void testMissingBody() throws Throwable {
            postServiceSynchronously(
                    NetworkInterfaceService.FACTORY_LINK,
                    null,
                    NetworkInterfaceState.class,
                    IllegalArgumentException.class);
        }

        @Test
        public void testInvalidAddress() throws Throwable {
            NetworkInterfaceState startState = buildValidStartState(false);
            startState.address = "bad-ip-address";
            postServiceSynchronously(
                    NetworkInterfaceService.FACTORY_LINK,
                    startState,
                    NetworkInterfaceState.class,
                    IllegalArgumentException.class);
        }

        @Test
        public void testMissingAddressAndDescriptionLink() throws Throwable {
            NetworkInterfaceState startState = buildValidStartState(false);
            startState.address = null;
            startState.networkLink = null;
            startState.subnetLink = null;
            postServiceSynchronously(
                    NetworkInterfaceService.FACTORY_LINK,
                    startState,
                    NetworkInterfaceState.class,
                    IllegalArgumentException.class);
        }

        @Test
        public void testHavingAddressAndNetworkLink() throws Throwable {
            NetworkInterfaceState startState = buildValidStartState(false);
            startState.address = "10.0.0.1";
            startState.networkLink = "10.0.0.2";
            postServiceSynchronously(
                    NetworkInterfaceService.FACTORY_LINK,
                    startState,
                    NetworkInterfaceState.class,
                    null);
        }

        @Test
        public void testHavingAddressAndNotSubnetLink() throws Throwable {
            NetworkInterfaceState startState = buildValidStartState(false);
            startState.address = "10.0.0.1";
            startState.subnetLink = null;
            postServiceSynchronously(
                    NetworkInterfaceService.FACTORY_LINK,
                    startState,
                    NetworkInterfaceState.class,
                    IllegalArgumentException.class);
        }
    }

    /**
     * This class implements tests for the handleGet method.
     */
    public static class HandleGetTest extends BaseModelTest {

        NetworkInterfaceStateWithDescription startState;
        NetworkInterfaceState createdState;

        @Before
        public void beforeTest() throws Throwable {
            NetworkInterfaceDescription nd = NetworkInterfaceDescriptionServiceTest
                    .createNetworkInterfaceDescription(this);

            this.startState = buildValidStartState(nd);

            this.createdState = postServiceSynchronously(
                    NetworkInterfaceService.FACTORY_LINK,
                    this.startState,
                    NetworkInterfaceState.class);
            assertNotNull(this.createdState);
        }

        @Test
        public void testGet() throws Throwable {

            NetworkInterfaceState getState = getServiceSynchronously(
                    this.createdState.documentSelfLink,
                    NetworkInterfaceState.class);

            assertThat(getState.id, is(this.startState.id));
            assertThat(getState.name, is(this.startState.name));

            assertThat(getState.networkLink, is(this.startState.networkLink));
            assertThat(getState.subnetLink, is(this.startState.subnetLink));
            assertThat(getState.securityGroupLinks, is(this.startState.securityGroupLinks));
            assertThat(getState.networkInterfaceDescriptionLink,
                    is(this.startState.networkInterfaceDescriptionLink));
        }

        @Test
        public void testGetExpand() throws Throwable {

            NetworkInterfaceStateWithDescription getState = getServiceSynchronously(
                    UriUtils.extendUriWithQuery(UriUtils.buildUri(this.createdState.documentSelfLink),
                            UriUtils.URI_PARAM_ODATA_EXPAND, Boolean.TRUE.toString()).toString(),
                    NetworkInterfaceStateWithDescription.class);

            assertThat(getState.id, is(this.startState.id));
            assertThat(getState.name, is(this.startState.name));

            assertThat(getState.address, is(this.startState.address));
            assertThat(getState.networkLink, is(this.startState.networkLink));
            assertThat(getState.subnetLink, is(this.startState.subnetLink));
            assertThat(getState.securityGroupLinks, is(this.startState.securityGroupLinks));
            assertThat(getState.networkInterfaceDescriptionLink,
                    is(this.startState.networkInterfaceDescriptionLink));

            assertThat(getState.description.deviceIndex, is(this.startState.description.deviceIndex));
            assertThat(getState.description.id, is(this.startState.description.id));
        }
    }

    /**
     * This class implements tests for the handlePatch method.
     */
    public static class HandlePatchTest extends BaseModelTest {
        @Test
        public void testPatch() throws Throwable {
            NetworkInterfaceState startState = buildValidStartState(false);

            NetworkInterfaceState returnState = postServiceSynchronously(
                    NetworkInterfaceService.FACTORY_LINK,
                    startState, NetworkInterfaceState.class);
            assertNull(returnState.computeHostLink);
            assertNotNull(returnState.documentCreationTimeMicros);

            NetworkInterfaceState patchState = new NetworkInterfaceState();
            patchState.address = "10.0.0.1";
            patchState.networkLink = "10.0.0.4";
            patchState.tenantLinks = new ArrayList<>();
            patchState.tenantLinks.add("tenant-linkA");
            patchState.groupLinks = new HashSet<>();
            patchState.groupLinks.add("group1");
            patchState.computeHostLink = "host-1";
            patchState.documentCreationTimeMicros = returnState.documentCreationTimeMicros;
            patchServiceSynchronously(returnState.documentSelfLink, patchState);

            returnState = getServiceSynchronously(
                    returnState.documentSelfLink, NetworkInterfaceState.class);

            assertThat(returnState.address, is(patchState.address));
            assertThat(returnState.networkLink, is(startState.networkLink));
            assertEquals(returnState.tenantLinks, patchState.tenantLinks);
            assertEquals(returnState.groupLinks, patchState.groupLinks);
            assertEquals(returnState.securityGroupLinks.size(), 1);
            assertNotNull(returnState.computeHostLink);
            assertThat(returnState.computeHostLink, is(patchState.computeHostLink));
        }

        @Test
        public void testPatchAssignHost() throws Throwable {
            NetworkInterfaceState startState = buildValidStartState(false);

            NetworkInterfaceState returnState = postServiceSynchronously(
                    NetworkInterfaceService.FACTORY_LINK,
                    startState, NetworkInterfaceState.class);
            assertNull(returnState.computeHostLink);

            NetworkInterfaceState patchState = new NetworkInterfaceState();
            patchState.computeHostLink = "host-1";
            patchServiceSynchronously(returnState.documentSelfLink, patchState);

            returnState = getServiceSynchronously(
                    returnState.documentSelfLink, NetworkInterfaceState.class);
            assertNotNull(returnState.computeHostLink);
            assertThat(returnState.computeHostLink, is(patchState.computeHostLink));
        }

        @Test(expected = IllegalArgumentException.class)
        public void testPatchModifyHost() throws Throwable {
            NetworkInterfaceState startState = buildValidStartState(true);

            NetworkInterfaceState returnState = postServiceSynchronously(
                    NetworkInterfaceService.FACTORY_LINK,
                    startState, NetworkInterfaceState.class);
            assertNotNull(returnState.computeHostLink);

            NetworkInterfaceState patchState = new NetworkInterfaceState();
            patchState.computeHostLink = "host-2";
            patchServiceSynchronously(returnState.documentSelfLink, patchState);
        }

        @Test
        public void testPatchModifyCreationTime() throws Throwable {
            NetworkInterfaceState startState = buildValidStartState(false);

            NetworkInterfaceState returnState = postServiceSynchronously(
                    NetworkInterfaceService.FACTORY_LINK,
                    startState, NetworkInterfaceState.class);
            assertNotNull(returnState.documentCreationTimeMicros);
            long originalCreationTime = returnState.documentCreationTimeMicros;

            NetworkInterfaceState patchState = new NetworkInterfaceState();
            long currentCreationTime = Utils.getNowMicrosUtc();
            patchState.documentCreationTimeMicros = currentCreationTime;

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);

            returnState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    NetworkInterfaceState.class);
            assertNotNull(returnState.documentCreationTimeMicros);
            assertThat(returnState.documentCreationTimeMicros, is(originalCreationTime));
        }

        @Test
        public void testPatchDuplicateSecurityGroup() throws Throwable {
            NetworkInterfaceState startState = buildValidStartState(false);

            NetworkInterfaceState returnState = postServiceSynchronously(
                    NetworkInterfaceService.FACTORY_LINK,
                    startState, NetworkInterfaceState.class);

            NetworkInterfaceState patchState = new NetworkInterfaceState();
            patchState.address = "10.0.0.1";
            patchState.networkLink = "10.0.0.4";
            patchState.tenantLinks = new ArrayList<>();
            patchState.tenantLinks.add("tenant-linkA");
            patchState.groupLinks = new HashSet<>();

            patchState.securityGroupLinks = new ArrayList<>();
            // patch duplicate security group
            patchState.securityGroupLinks.add("/resources/security-groups/1");
            // patch unique security group
            patchState.securityGroupLinks.add("/resources/security-groups/2");
            patchState.groupLinks.add("group1");
            patchServiceSynchronously(returnState.documentSelfLink, patchState);

            returnState = getServiceSynchronously(
                    returnState.documentSelfLink, NetworkInterfaceState.class);

            assertThat(returnState.address, is(patchState.address));
            assertThat(returnState.networkLink, is(startState.networkLink));
            assertEquals(returnState.tenantLinks, patchState.tenantLinks);
            assertEquals(returnState.groupLinks, patchState.groupLinks);

            assertEquals(returnState.securityGroupLinks.size(), 2);
            assertThat(returnState.securityGroupLinks, hasItem("/resources/security-groups/1"));
            assertThat(returnState.securityGroupLinks, hasItem("/resources/security-groups/2"));
        }
    }

    /**
     * This class implements tests for the handlePut method.
     */
    public static class HandlePutTest extends BaseModelTest {

        @Test
        public void testPut() throws Throwable {
            NetworkInterfaceState startState = buildValidStartState(false);
            NetworkInterfaceState returnState = postServiceSynchronously(
                    NetworkInterfaceService.FACTORY_LINK,
                    startState, NetworkInterfaceState.class);

            assertNotNull(returnState);

            NetworkInterfaceState newState = new NetworkInterfaceState();
            newState.id = UUID.randomUUID().toString();
            newState.name = NetworkInterfaceServiceTest.class.getSimpleName();
            newState.address = "9.9.9.9";
            newState.securityGroupLinks = Collections.singletonList
                    ("/resources/security-groups/1");
            newState.networkInterfaceDescriptionLink = "/resources/nicDesc/nicDesc9";
            newState.subnetLink = "/resources/subnet/subnet9";
            newState.documentCreationTimeMicros = returnState.documentCreationTimeMicros;

            putServiceSynchronously(returnState.documentSelfLink,
                    newState);
            NetworkInterfaceState getState = getServiceSynchronously(returnState.documentSelfLink,
                    NetworkInterfaceState.class);
            assertThat(getState.id, is(newState.id));
            assertThat(getState.name, is(newState.name));
            assertThat(getState.networkLink, is(newState.networkLink));
            assertEquals(getState.tenantLinks, newState.tenantLinks);
            assertEquals(getState.groupLinks, newState.groupLinks);
            // make sure launchTimeMicros was preserved
            assertEquals(getState.creationTimeMicros, returnState.creationTimeMicros);
            assertEquals(getState.documentCreationTimeMicros, returnState.documentCreationTimeMicros);
        }

        @Test(expected = IllegalArgumentException.class)
        public void testPutModifyCreationTime() throws Throwable {
            NetworkInterfaceState startState = buildValidStartState(false);
            NetworkInterfaceState returnState = postServiceSynchronously(
                    NetworkInterfaceService.FACTORY_LINK,
                    startState, NetworkInterfaceState.class);

            assertNotNull(returnState);

            NetworkInterfaceState newState = new NetworkInterfaceState();
            newState.id = UUID.randomUUID().toString();
            newState.name = NetworkInterfaceServiceTest.class.getSimpleName();
            newState.address = "9.9.9.9";
            newState.securityGroupLinks = Collections.singletonList
                    ("/resources/security-groups/1");
            newState.networkInterfaceDescriptionLink = "/resources/nicDesc/nicDesc9";
            newState.subnetLink = "/resources/subnet/subnet9";

            newState.documentCreationTimeMicros = Utils.getNowMicrosUtc();

            putServiceSynchronously(returnState.documentSelfLink,
                    newState);
        }

        @Test(expected = IllegalArgumentException.class)
        public void testPutModifyHost() throws Throwable {
            NetworkInterfaceState startState = buildValidStartState(true);
            NetworkInterfaceState returnState = postServiceSynchronously(
                    NetworkInterfaceService.FACTORY_LINK,
                    startState, NetworkInterfaceState.class);

            assertNotNull(returnState);

            NetworkInterfaceState newState = new NetworkInterfaceState();
            newState.id = UUID.randomUUID().toString();
            newState.name = NetworkInterfaceServiceTest.class.getSimpleName();
            newState.address = "9.9.9.9";
            newState.securityGroupLinks = Collections.singletonList
                    ("/resources/security-groups/1");
            newState.networkInterfaceDescriptionLink = "/resources/nicDesc/nicDesc9";
            newState.subnetLink = "/resources/subnet/subnet9";
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
            NetworkInterfaceState nic = buildValidStartState(false);
            URI tenantUri = UriUtils.buildFactoryUri(this.host, TenantService.class);
            nic.tenantLinks = new ArrayList<>();
            nic.tenantLinks.add(UriUtils.buildUriPath(tenantUri.getPath(),
                    "tenantA"));
            NetworkInterfaceState startState = postServiceSynchronously(
                    NetworkInterfaceService.FACTORY_LINK, nic,
                    NetworkInterfaceState.class);

            String kind = Utils
                    .buildKind(NetworkInterfaceState.class);
            String propertyName = QueryTask.QuerySpecification
                    .buildCollectionItemName(ResourceState.FIELD_NAME_TENANT_LINKS);

            QueryTask q = createDirectQueryTask(kind, propertyName,
                    nic.tenantLinks.get(0));
            q = querySynchronously(q);
            assertNotNull(q.results.documentLinks);
            assertThat(q.results.documentCount, is(1L));
            assertThat(q.results.documentLinks.get(0),
                    is(startState.documentSelfLink));
        }
    }
}
