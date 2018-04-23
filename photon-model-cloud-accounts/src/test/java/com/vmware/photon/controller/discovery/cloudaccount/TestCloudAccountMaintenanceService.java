/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.discovery.cloudaccount;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountMaintenanceService.PROPERTY_QUERY_SIZE;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountTestHelper.CUSTOM_PROP_VALUE;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountTestHelper.USER_1_ID;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_DISCOVERY_MAINT_COMPLETE;

import java.net.URI;
import java.util.List;
import java.util.logging.Level;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.discovery.cloudaccount.CloudAccountApiService.CloudAccountViewState;
import com.vmware.photon.controller.discovery.cloudaccount.CloudAccountMaintenanceService.CloudAccountMaintenanceState;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;

public class TestCloudAccountMaintenanceService {
    public CloudAccountTestHelper accountTestHelper;
    public int resultLimit = 2;

    private VerificationHost host;
    private URI peerUri;

    @Before
    public void setUp() {
        System.setProperty(PROPERTY_QUERY_SIZE, String.valueOf(this.resultLimit));
        this.accountTestHelper = CloudAccountTestHelper.create();
        this.host = this.accountTestHelper.host;
        this.peerUri = this.accountTestHelper.peerUri;
    }

    @After
    public void tearDown() {
        this.accountTestHelper.tearDown();
    }

    private void ensureCreatedByAndBucketCleaned(List<String> endpointLinks) {
        checkEndpoints(endpointLinks, true);
    }

    private void ensuredBucketCleaned(List<String> endpointLinks) {
        checkEndpoints(endpointLinks, false);
    }

    private void checkEndpoints(List<String> endpointLinks, boolean checkCreatedBy) {
        boolean customPropertiesCorrected = true;
        for (String endpointLink : endpointLinks) {
            URI apiFriendlyLink = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                    UriUtils.getLastPathSegment(endpointLink));

            // If the first endpoint lacks custom properties, we can assume others do too.
            CloudAccountViewState result = this.host.getServiceState(null, CloudAccountViewState.class, apiFriendlyLink);
            if (checkCreatedBy && !USER_1_ID.equals(result.createdBy.email)) {
                this.host.log(Level.WARNING, "EndpointState createdBy not cleaned: %s", Utils.toJson(result));
                customPropertiesCorrected = false;
                break;
            }

            if (!result.customProperties.get(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET)
                    .contains(CUSTOM_PROP_VALUE)) {
                this.host.log(Level.WARNING, "EndpointState S3 bucket not cleaned: %s", Utils.toJson(result));
                customPropertiesCorrected = false;
                break;
            }

            if (!Boolean.TRUE.toString().equals(result.customProperties.get(ENDPOINT_CUSTOM_PROPERTY_NAME_DISCOVERY_MAINT_COMPLETE))) {
                this.host.log(Level.WARNING, "EndpointState not marked as cleaned: %s", Utils.toJson(result));
                customPropertiesCorrected = false;
                break;
            }
        }
        assertThat(customPropertiesCorrected, equalTo(Boolean.TRUE));
    }

    @Test
    public void testMaintenance() throws Throwable {
        int numEndpoints = this.resultLimit * 3;
        List<String> endpointLinks = this.accountTestHelper.createPayingAccountsWithoutCustomProperties(numEndpoints);
        this.host.setSystemAuthorizationContext();
        Operation post = Operation.createPost(
                UriUtils.buildUri(this.peerUri, UriPaths.CLOUD_ACCOUNT_MAINTENANCE_SERVICE))
                .setBody(new ServiceDocument());
        CloudAccountMaintenanceState state = this.host.getTestRequestSender().sendAndWait(post, CloudAccountMaintenanceState.class);
        assertThat(state, notNullValue());
        assertThat(state.documentExpirationTimeMicros, not(equalTo(0L)));
        assertThat(state.cleanedEndpointLinks, hasSize(endpointLinks.size()));
        assertThat(state.cleanedEndpointLinks, hasItems(endpointLinks.toArray(new String[0])));
        this.host.resetSystemAuthorizationContext();

        ensureCreatedByAndBucketCleaned(endpointLinks);
    }

    @Test
    public void testMaintenanceSystemUser() throws Throwable {
        int numEndpoints = this.resultLimit * 3;
        List<String> endpointLinks = this.accountTestHelper.createPayingAccountsWithoutCustomProperties(numEndpoints);
        this.host.setSystemAuthorizationContext();

        Operation post = Operation.createPost(
                UriUtils.buildUri(this.peerUri, UriPaths.CLOUD_ACCOUNT_MAINTENANCE_SERVICE))
                .setBody(new ServiceDocument());
        CloudAccountMaintenanceState state = this.host.getTestRequestSender().sendAndWait(post, CloudAccountMaintenanceState.class);
        assertThat(state, notNullValue());
        assertThat(state.documentExpirationTimeMicros, not(equalTo(0L)));
        assertThat(state.cleanedEndpointLinks, hasSize(endpointLinks.size()));
        assertThat(state.cleanedEndpointLinks, hasItems(endpointLinks.toArray(new String[0])));
        this.host.resetSystemAuthorizationContext();

        // We can't clean createdBy since Endpoint owned by service user, but we can clean S3 bucket
        ensuredBucketCleaned(endpointLinks);
    }
}
