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

package com.vmware.photon.controller.discovery.vsphere;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountTestHelper.ORG_1_ID;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.CCS_HOST;
import static com.vmware.photon.controller.discovery.vsphere.VsphereRDCSyncTaskService.PROPERTY_MAINT_INTERVAL_SECONDS;
import static com.vmware.photon.controller.discovery.vsphere.VsphereRDCSyncTaskService.STATUS_FIELD;
import static com.vmware.photon.controller.discovery.vsphere.VsphereRDCSyncTaskService.SUCCESS;
import static com.vmware.photon.controller.discovery.vsphere.VsphereRDCSyncTaskService.SubStage.CHECK_STATUS_FIELD;
import static com.vmware.photon.controller.model.UriPaths.CCS_VALIDATE_SERVICE;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.discovery.cloudaccount.CloudAccountApiService;
import com.vmware.photon.controller.discovery.cloudaccount.CloudAccountApiService.CloudAccountViewState;
import com.vmware.photon.controller.discovery.cloudaccount.CloudAccountTestHelper;
import com.vmware.photon.controller.discovery.common.CloudAccountConstants.UpdateAction;
import com.vmware.photon.controller.discovery.common.CollectionStringFieldUpdate;
import com.vmware.photon.controller.discovery.endpoints.Credentials;
import com.vmware.photon.controller.discovery.endpoints.EndpointUpdateTaskService;
import com.vmware.photon.controller.discovery.vsphere.VsphereRDCSyncTaskService.SubStage;
import com.vmware.photon.controller.discovery.vsphere.VsphereRDCSyncTaskService.VsphereRDCSyncTaskState;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.xenon.common.BasicTestCase;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService;

public class TestVsphereRDCSyncTaskService extends BasicTestCase {

    public CloudAccountTestHelper accountTestHelper;
    private VerificationHost host;
    private URI peerUri;

    @Before
    public void setUp() throws Throwable {
        System.setProperty(PROPERTY_MAINT_INTERVAL_SECONDS, "2");
        this.accountTestHelper = CloudAccountTestHelper.create(true, 1);
        this.host = this.accountTestHelper.host;
        this.peerUri = this.accountTestHelper.peerUri;
    }

    @After
    public void tearDown() {
        this.accountTestHelper.tearDown();
    }

    /**
     * Create this test here, because the we need to set the peerNode number equal to one.
     * Otherwise, query factory link may not works property because
     * {@link EndpointUpdateTaskService} set DEFAULT_1X_NODE_SELECTOR which will not sync the
     * peernodes.
     * TODO: Find solution of this issue and move this test back to TestCloudAccountApiService
     * @throws Throwable
     */
    @Test
    public void testVSphereCredentialUpdate() throws Throwable {
        String endpoint_name = "vc-local-test-endpoint";

        String endpointLink = this.accountTestHelper.createMockVsphereEndpoint(this.host, this.peerUri, ORG_1_ID,
                endpoint_name, "root", "password",
                "10.112.107.120", "privateCloud1",
                "vc-dc-name", "vc-dc-id");

        URI cloudAccountAPIURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(endpointLink));
        CloudAccountViewState result = this.host.getServiceState(null, CloudAccountViewState.class, cloudAccountAPIURI);
        this.accountTestHelper.verifyEndpoint(endpointLink, result);
        AuthCredentialsService.AuthCredentialsServiceState authCredentialsServiceState = new AuthCredentialsService.AuthCredentialsServiceState();
        authCredentialsServiceState.privateKey = "password2";
        authCredentialsServiceState.privateKeyId = "root2";
        Credentials updateCredential = Credentials.createCredentials(PhotonModelConstants.EndpointType.vsphere.name(),
                authCredentialsServiceState, null);


        CloudAccountApiService.CloudAccountPatchRequest requestBody = new CloudAccountApiService.CloudAccountPatchRequest();
        requestBody.credentials = updateCredential;
        Operation response = this.host.waitForResponse(Operation.createPatch(cloudAccountAPIURI)
                .setBody(requestBody));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        assertNull(response.getBody(EndpointService.EndpointState.class).customProperties.get(STATUS_FIELD));

        List<String> updateTaskDocumentLink = new ArrayList<>();
        this.host.waitFor("Service DocumentSelfLinks is not ready", () -> {
            boolean isReady = false;
            Operation o = this.host.waitForResponse(Operation.createGet(
                    UriUtils.buildUri(this.peerUri, EndpointUpdateTaskService.FACTORY_LINK)));
            ServiceDocumentQueryResult sd = o.getBody(ServiceDocumentQueryResult.class);
            if (sd.documentLinks != null && sd.documentLinks.size() > 0) {
                isReady = true;
                updateTaskDocumentLink.add(sd.documentLinks.get(0));
                this.host.completeIteration();
            }
            return isReady;
        });
        assertTrue(updateTaskDocumentLink.size() > 0);
        Operation checkTaskOp = this.host.waitForResponse(
                Operation.createGet(UriUtils.buildUri(this.peerUri, updateTaskDocumentLink.get(0))));
        EndpointUpdateTaskService.EndpointUpdateTaskState state = checkTaskOp.getBody(EndpointUpdateTaskService.EndpointUpdateTaskState.class);
        assertTrue(state.forceDataSync);
    }

    @Test
    public void testVsphereRDCSyncSuccess() throws Throwable {
        System.setProperty(PROPERTY_MAINT_INTERVAL_SECONDS, "2");
        String endpointLink = this.accountTestHelper.createMockVsphereEndpoint(this.host, this.peerUri, ORG_1_ID,
                "vc-local-test-endpoint", "root", "password",
                "10.112.107.120", "privateCloud1",
                "vc-dc-name", "vc-dc-id");

        URI endpointURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(endpointLink));
        CloudAccountViewState result = this.host.getServiceState(null, CloudAccountViewState.class, endpointURI);
        // make sure status field is not there.
        assertNull(result.customProperties.get(STATUS_FIELD));

        // first check current vSphereRDCSyncTask is on CHECK_STATUS_FIELD stage.
        VsphereRDCSyncTaskState syncTaskState = new VsphereRDCSyncTaskState();
        syncTaskState.endpointLink = endpointLink;
        syncTaskState.options = EnumSet.of(TaskOption.IS_MOCK);
        syncTaskState.documentExpirationTimeMicros = Utils.fromNowMicrosUtc(TimeUnit.MINUTES
                .toMicros(30));
        this.host.setSystemAuthorizationContext();
        Operation createPost = this.host.waitForResponse(Operation.createPost(UriUtils.buildUri(this
                .peerUri, VsphereRDCSyncTaskService.FACTORY_LINK))
                .setBody(syncTaskState));
        VsphereRDCSyncTaskState responseSyncTaskState = createPost.getBody(VsphereRDCSyncTaskState.class);
        assertEquals(Operation.STATUS_CODE_OK, createPost.getStatusCode());
        assertEquals(CHECK_STATUS_FIELD, responseSyncTaskState.subStage);
        this.host.resetSystemAuthorizationContext();

        // set the endpointState satisfy the RDC Sync condition.
        CollectionStringFieldUpdate updateStatusField = new CollectionStringFieldUpdate();
        updateStatusField.action = UpdateAction.ADD;
        updateStatusField.value = SUCCESS;
        CloudAccountApiService.CloudAccountPatchRequest requestBody = new CloudAccountApiService.CloudAccountPatchRequest();
        requestBody.customPropertyUpdates = new HashMap<>();
        requestBody.customPropertyUpdates.put(STATUS_FIELD, updateStatusField);
        requestBody.isMock = true;
        Operation response = this.host.waitForResponse(Operation.createPatch(endpointURI)
                .setBody(requestBody));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        // wait for the vsphereRDCSyncTask reach SUCCESS stage.
        String vsphereRDCSyncTaskLink = responseSyncTaskState.documentSelfLink;
        this.host.setSystemAuthorizationContext();
        this.host.waitFor("Task didn't reach SUCCESS stage", () -> {
            boolean isReady = false;
            Operation getResponse = this.host.waitForResponse(Operation.createGet(UriUtils
                    .buildUri(this.peerUri, vsphereRDCSyncTaskLink)));

            VsphereRDCSyncTaskState responseBody = getResponse.getBody(VsphereRDCSyncTaskState
                    .class);
            assertEquals(Operation.STATUS_CODE_OK, getResponse.getStatusCode());
            if (SubStage.SUCCESS.equals(responseBody.subStage)) {
                isReady = true;
                this.host.completeIteration();
            }
            return isReady;
        });
        this.host.resetSystemAuthorizationContext();
    }

    public static class MockCCSValidateService extends StatelessService {
        public static String SELF_LINK = CCS_VALIDATE_SERVICE;

        public MockCCSValidateService() {
            super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        }

        @Override
        public void handleStart(Operation startPost) {
            startPost.complete();
            logInfo("Mock CCS host Service started at %s", getUri());
            System.setProperty(CCS_HOST, getUri().getScheme() + "://" + getUri().getAuthority());
        }

        @Override
        public void handlePost(Operation op) {
            op.complete();
        }
    }
}
