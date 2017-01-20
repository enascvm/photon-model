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
import static org.junit.Assert.assertNotNull;

import java.util.Collections;
import java.util.EnumSet;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.IpAssignment;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.xenon.common.Service;

/**
 * This class implements tests for the {@link NetworkInterfaceDescriptionService} class.
 */
@RunWith(NetworkInterfaceDescriptionServiceTest.class)
@SuiteClasses({ NetworkInterfaceDescriptionServiceTest.ConstructorTest.class,
        NetworkInterfaceDescriptionServiceTest.HandleGetTest.class })
public class NetworkInterfaceDescriptionServiceTest extends Suite {

    public NetworkInterfaceDescriptionServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    public static NetworkInterfaceDescription buildValidStartState()
            throws Throwable {

        NetworkInterfaceDescription nd = new NetworkInterfaceDescription();

        nd.id = UUID.randomUUID().toString();
        nd.name = NetworkInterfaceDescriptionServiceTest.class.getSimpleName();

        nd.address = "8.8.8.8";
        nd.assignment = IpAssignment.STATIC;
        nd.securityGroupLinks = Collections.singletonList("/resources/firewall/fw8");
        nd.networkLink = "/resources/network/net8";
        nd.subnetLink = "/resources/subnet/subnet8";

        return nd;
    }

    public static NetworkInterfaceDescription createNetworkInterfaceDescription(
            BaseModelTest test) throws Throwable {

        NetworkInterfaceDescription nd = buildValidStartState();

        return test.postServiceSynchronously(
                NetworkInterfaceDescriptionService.FACTORY_LINK, nd,
                NetworkInterfaceDescription.class);
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {

        private NetworkInterfaceDescriptionService networkInterfaceDescriptionService;

        @Before
        public void setUpTest() {
            this.networkInterfaceDescriptionService = new NetworkInterfaceDescriptionService();
        }

        @Test
        public void testServiceOptions() {

            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.CONCURRENT_GET_HANDLING,
                    Service.ServiceOption.OWNER_SELECTION,
                    Service.ServiceOption.PERSISTENCE,
                    Service.ServiceOption.REPLICATION,
                    Service.ServiceOption.IDEMPOTENT_POST);

            assertThat(this.networkInterfaceDescriptionService.getOptions(),
                    is(expected));
        }
    }

    /**
     * This class implements tests for the handleGet method.
     */
    public static class HandleGetTest extends BaseModelTest {

        @Test
        public void testGet() throws Throwable {
            NetworkInterfaceDescription startState = buildValidStartState();

            NetworkInterfaceDescription returnState = postServiceSynchronously(
                    NetworkInterfaceDescriptionService.FACTORY_LINK,
                    startState,
                    NetworkInterfaceDescription.class);
            assertNotNull(returnState);

            NetworkInterfaceDescription getState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    NetworkInterfaceDescription.class);
            assertNotNull(getState);

            assertThat(getState.id, is(startState.id));
            assertThat(getState.name, is(startState.name));

            assertThat(getState.address, is(startState.address));
            assertThat(getState.assignment, is(startState.assignment));
            assertThat(getState.securityGroupLinks, is(startState.securityGroupLinks));
            assertThat(getState.networkLink, is(startState.networkLink));
            assertThat(getState.subnetLink, is(startState.subnetLink));
            assertThat(getState.deviceIndex, is(startState.deviceIndex));
        }
    }
}
