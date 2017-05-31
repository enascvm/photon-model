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

import static com.vmware.photon.controller.model.constants.PhotonModelConstants.NETWORK_SUBTYPE_SUBNET_STATE;

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
        SubnetServiceTest.QueryTest.class })
public class SubnetServiceTest extends Suite {

    public SubnetServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    private static SubnetState buildValidStartState() {
        SubnetState subnetState = new SubnetState();
        subnetState.id = UUID.randomUUID().toString();
        subnetState.name = "networkName";
        subnetState.subnetCIDR = "10.0.0.0/10";
        subnetState.networkLink = NetworkService.FACTORY_LINK + "/mynet";
        subnetState.tenantLinks = new ArrayList<>();
        subnetState.tenantLinks.add("tenant-linkA");

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
            SubnetState startState = buildValidStartState();
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
            SubnetState startState = buildValidStartState();
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
        public void testInvalidValues() throws Throwable {
            SubnetState missingSubnet = buildValidStartState();
            SubnetState invalidSubnet1 = buildValidStartState();
            SubnetState invalidSubnet2 = buildValidStartState();
            SubnetState invalidSubnet3 = buildValidStartState();
            SubnetState invalidSubnet4 = buildValidStartState();
            SubnetState invalidSubnet5 = buildValidStartState();

            missingSubnet.subnetCIDR = null;
            // no subnet
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
                    missingSubnet, invalidSubnet1, invalidSubnet2, invalidSubnet3,
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
            SubnetState startState = buildValidStartState();

            SubnetState returnState = postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    startState, SubnetState.class);

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
            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);

            returnState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    SubnetState.class);

            assertThat(returnState.name, is(patchState.name));
            assertThat(returnState.subnetCIDR, is(patchState.subnetCIDR));
            assertThat(returnState.customProperties,
                    is(patchState.customProperties));
            assertEquals(returnState.tenantLinks.size(), 2);
            assertEquals(returnState.groupLinks, patchState.groupLinks);
            assertEquals(returnState.zoneId, patchState.zoneId);
        }
    }

    /**
     * This class implements tests for query.
     */
    public static class QueryTest extends BaseModelTest {
        @Test
        public void testTenantLinksQuery() throws Throwable {
            SubnetState subnetState = buildValidStartState();
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
