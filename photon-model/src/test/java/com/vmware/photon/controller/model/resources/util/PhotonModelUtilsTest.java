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

package com.vmware.photon.controller.model.resources.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceStateWithDescription;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;

/**
 * Tests for the {@link PhotonModelUtils} class.
 */
@RunWith(PhotonModelUtilsTest.class)
@SuiteClasses({ PhotonModelUtilsTest.SetEndpointLinkTest.class })
public class PhotonModelUtilsTest extends Suite {

    public PhotonModelUtilsTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    /**
     * Test for
     * {@link PhotonModelUtils#setEndpointLink(com.vmware.xenon.common.ServiceDocument, String)}.
     */
    public static class SetEndpointLinkTest extends BaseModelTest {

        static final String EPL_VALUE = "testEndpointLink";
        static final String ADDITIONAL_EPL_VALUE = "newTestEndpointLink";

        static final Map<String, String> EPL_VALUE_AS_CUSTOM_PROP = Collections.singletonMap(
                PhotonModelConstants.CUSTOM_PROP_ENDPOINT_LINK, EPL_VALUE);

        @Test
        public void testSetExplicitProperty() {
            {
                ComputeDescription cd = new ComputeDescription();
                ComputeDescription state = PhotonModelUtils
                        .setEndpointLink(cd, EPL_VALUE);
                assertEquals(state.endpointLink, EPL_VALUE);
                assertNull(state.customProperties);
                assertNotNull(state.endpointLinks);
                assertTrue(state.endpointLinks.contains(EPL_VALUE));
                //Verify that during an update scenario the collection is updated as expected.
                state = PhotonModelUtils
                        .setEndpointLink(state, ADDITIONAL_EPL_VALUE);
                assertNotNull(state.endpointLinks);
                assertTrue(state.endpointLinks.size() == 2);
                assertTrue(state.endpointLinks.contains(EPL_VALUE));
                assertTrue(state.endpointLinks.contains(ADDITIONAL_EPL_VALUE));
            }
            {
                ComputeState state = PhotonModelUtils.setEndpointLink(new ComputeState(),
                        EPL_VALUE);
                assertEquals(state.endpointLink, EPL_VALUE);
                assertNull(state.customProperties);
                assertNotNull(state.endpointLinks);
                assertTrue(state.endpointLinks.contains(EPL_VALUE));
            }
            {
                ComputeStateWithDescription state = PhotonModelUtils
                        .setEndpointLink(new ComputeStateWithDescription(), EPL_VALUE);
                assertEquals(state.endpointLink, EPL_VALUE);
                assertNull(state.customProperties);
                assertNotNull(state.endpointLinks);
                assertTrue(state.endpointLinks.contains(EPL_VALUE));
            }
            {
                DiskState state = PhotonModelUtils.setEndpointLink(new DiskState(), EPL_VALUE);
                assertEquals(state.endpointLink, EPL_VALUE);
                assertNull(state.customProperties);
                assertNotNull(state.endpointLinks);
                assertTrue(state.endpointLinks.contains(EPL_VALUE));
            }
            {
                NetworkInterfaceDescription state = PhotonModelUtils
                        .setEndpointLink(new NetworkInterfaceDescription(), EPL_VALUE);

                assertEquals(state.endpointLink, EPL_VALUE);
                assertNull(state.customProperties);
                assertNotNull(state.endpointLinks);
                assertTrue(state.endpointLinks.contains(EPL_VALUE));
            }
            {
                NetworkInterfaceState state = PhotonModelUtils
                        .setEndpointLink(new NetworkInterfaceState(), EPL_VALUE);
                assertEquals(state.endpointLink, EPL_VALUE);
                assertNull(state.customProperties);
                assertNotNull(state.endpointLinks);
                assertTrue(state.endpointLinks.contains(EPL_VALUE));
            }
            {
                NetworkInterfaceStateWithDescription state = PhotonModelUtils
                        .setEndpointLink(new NetworkInterfaceStateWithDescription(), EPL_VALUE);
                assertEquals(state.endpointLink, EPL_VALUE);
                assertNull(state.customProperties);
                assertNotNull(state.endpointLinks);
                assertTrue(state.endpointLinks.contains(EPL_VALUE));
            }
            {
                NetworkState state = PhotonModelUtils.setEndpointLink(new NetworkState(),
                        EPL_VALUE);
                assertEquals(state.endpointLink, EPL_VALUE);
                assertNull(state.customProperties);
                assertNotNull(state.endpointLinks);
                assertTrue(state.endpointLinks.contains(EPL_VALUE));
            }
            {
                SecurityGroupState state = PhotonModelUtils
                        .setEndpointLink(new SecurityGroupState(), EPL_VALUE);
                assertEquals(state.endpointLink, EPL_VALUE);
                assertNull(state.customProperties);
                assertNotNull(state.endpointLinks);
                assertTrue(state.endpointLinks.contains(EPL_VALUE));
            }
            {
                StorageDescription state = PhotonModelUtils
                        .setEndpointLink(new StorageDescription(), EPL_VALUE);
                assertEquals(state.endpointLink, EPL_VALUE);
                assertNull(state.customProperties);
                assertNotNull(state.endpointLinks);
                assertTrue(state.endpointLinks.contains(EPL_VALUE));
            }
            {
                SubnetState state = PhotonModelUtils.setEndpointLink(new SubnetState(), EPL_VALUE);
                assertEquals(state.endpointLink, EPL_VALUE);
                assertNull(state.customProperties);
                assertNotNull(state.endpointLinks);
                assertTrue(state.endpointLinks.contains(EPL_VALUE));
            }
        }

        public void testSetCustomProperty() throws Throwable {

            // Validate with null customProperties
            {
                ResourceGroupState state = new ResourceGroupState();

                PhotonModelUtils.setEndpointLink(state, EPL_VALUE);

                assertEquals(state.customProperties, EPL_VALUE_AS_CUSTOM_PROP);
            }

            // Validate with pre-set customProperties
            {
                ResourceGroupState state = new ResourceGroupState();
                state.customProperties = new HashMap<>();
                state.customProperties.put("aaa", "AAA");

                PhotonModelUtils.setEndpointLink(state, EPL_VALUE);

                Map<String, String> expected = new HashMap<>();
                expected.put("aaa", "AAA");
                expected.putAll(EPL_VALUE_AS_CUSTOM_PROP);

                assertEquals(state.customProperties, expected);
            }
        }

        public void testSetOnUnsupported() throws Throwable {
            {
                // Create inner ServiceDocument not known by PhotonModelUtils
                ResourceGroupState state = new ResourceGroupState() {

                };

                PhotonModelUtils.setEndpointLink(state, EPL_VALUE);

                assertNull(state.customProperties);
            }

            {
                // Create inner ResourceState not known by PhotonModelUtils
                ComputeState state = new ComputeState() {

                };

                PhotonModelUtils.setEndpointLink(state, EPL_VALUE);

                assertNull(state.customProperties);
                assertNull(state.endpointLink);
            }
        }

    }
}
