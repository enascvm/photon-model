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
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.CREDENTIALS_REQUIRED;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_ALREADY_EXISTS;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_ID_REQUIRED;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_NAME_REQUIRED;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_ORG_LINK_REQUIRED;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_TAG_NULL_EMPTY;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_TYPE_REQUIRED;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.INVALID_ENDPOINT_OWNER;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.INVALID_ENDPOINT_TYPE;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.INVALID_USER_FOR_OWNERS_UPDATE;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountTestHelper.ACCOUNT_DESCRIPTION;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountTestHelper.DEFAULT_CLOUD_ACCOUNT_FILTER_LIST_SIZE;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountTestHelper.DEFAULT_CLOUD_ACCOUNT_PROPERTIES_LIST_SIZE;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountTestHelper.DEFAULT_CLOUD_ACCOUNT_SORT_LIST_SIZE;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountTestHelper.ORG_1_ID;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountTestHelper.PRIVATE_KEY_ID_PREFIX;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountTestHelper.PRIVATE_KEY_PREFIX;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountTestHelper.USER_1_ID;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountTestHelper.USER_1_PASSWORD;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountTestHelper.USER_2_ID;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountTestHelper.USER_2_PASSWORD;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.UpdateAction.ADD;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_SYNC_JOB_STATUS;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.SyncJobStage.DONE;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.SyncJobStage.FAILED;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.SyncJobStage.RUNNING;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.SyncJobStage.WAITING;
import static com.vmware.photon.controller.discovery.endpoints.TestEndpointUtils.createAwsCredentials;
import static com.vmware.photon.controller.discovery.endpoints.TestEndpointUtils.createVSphereCredentials;
import static com.vmware.photon.controller.discovery.vsphere.VsphereOnPremEndpointAdapterService.DC_ID_KEY;
import static com.vmware.photon.controller.discovery.vsphere.VsphereOnPremEndpointAdapterService.DC_NAME_KEY;
import static com.vmware.photon.controller.discovery.vsphere.VsphereOnPremEndpointAdapterService.HOST_NAME_KEY;
import static com.vmware.photon.controller.discovery.vsphere.VsphereOnPremEndpointAdapterService.PRIVATE_CLOUD_NAME_KEY;
import static com.vmware.photon.controller.model.UriPaths.CLOUD_ACCOUNT_API_SERVICE;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEYID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEY_KEY;
import static com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService.EP_CP_ENUMERATION_TASK_STATE;
import static com.vmware.xenon.common.UriUtils.URI_PARAM_ODATA_LIMIT;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.discovery.cloudaccount.CloudAccountApiService.CloudAccountCreateRequest;
import com.vmware.photon.controller.discovery.cloudaccount.CloudAccountApiService.CloudAccountPatchRequest;
import com.vmware.photon.controller.discovery.cloudaccount.CloudAccountApiService.CloudAccountSummaryRequest;
import com.vmware.photon.controller.discovery.cloudaccount.CloudAccountApiService.CloudAccountSummaryViewState;
import com.vmware.photon.controller.discovery.cloudaccount.CloudAccountApiService.CloudAccountTypeSummary;
import com.vmware.photon.controller.discovery.cloudaccount.CloudAccountApiService.CloudAccountValidateRequest;
import com.vmware.photon.controller.discovery.cloudaccount.CloudAccountApiService.CloudAccountViewState;
import com.vmware.photon.controller.discovery.common.CloudAccountConstants.UpdateAction;
import com.vmware.photon.controller.discovery.common.CollectionStringFieldUpdate;
import com.vmware.photon.controller.discovery.common.ErrorCode;
import com.vmware.photon.controller.discovery.common.PhotonControllerCloudAccountUtils;
import com.vmware.photon.controller.discovery.common.ResourceProperties;
import com.vmware.photon.controller.discovery.common.ResourceProperty;
import com.vmware.photon.controller.discovery.common.TagFieldUpdate;
import com.vmware.photon.controller.discovery.common.TagViewState;
import com.vmware.photon.controller.discovery.common.services.UserService;
import com.vmware.photon.controller.discovery.common.services.UserService.UserState;
import com.vmware.photon.controller.discovery.endpoints.Credentials;
import com.vmware.photon.controller.discovery.endpoints.EndpointUtils;
import com.vmware.photon.controller.discovery.onboarding.OnBoardingTestUtils;
import com.vmware.photon.controller.discovery.onboarding.OnboardingUtils;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.ServiceStateCollectionUpdateRequest;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.TestRequestSender;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.UserGroupService;

public class TestCloudAccountApiService {
    public CloudAccountTestHelper accountTestHelper;
    private VerificationHost host;
    private URI peerUri;
    private String orgLink;
    private TestRequestSender testRequestSender;

    private static final String COST_INSIGHT = "cost_insight";
    private static final String DISCOVERY = "discovery";

    @Before
    public void setUp() {
        System.setProperty(AWSUtils.AWS_MAX_ERROR_RETRY_PROPERTY, "1");
        this.accountTestHelper = CloudAccountTestHelper.create();
        this.host = this.accountTestHelper.host;
        this.testRequestSender = this.host.getTestRequestSender();
        this.peerUri = this.accountTestHelper.peerUri;
        this.orgLink = this.accountTestHelper.orgLink;
    }

    @After
    public void tearDown() {
        this.accountTestHelper.tearDown();
    }

    @Test
    public void testInvalidArgs() {
        CloudAccountCreateRequest cloudAccountCreateRequest = new CloudAccountCreateRequest();
        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK))
                .setBody(cloudAccountCreateRequest));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, ENDPOINT_NAME_REQUIRED);

        cloudAccountCreateRequest = new CloudAccountCreateRequest();
        cloudAccountCreateRequest.name = "foobar";
        response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK))
                .setBody(cloudAccountCreateRequest));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, ENDPOINT_TYPE_REQUIRED);

        cloudAccountCreateRequest = new CloudAccountCreateRequest();
        cloudAccountCreateRequest.name = "foobar";
        cloudAccountCreateRequest.type = EndpointType.azure.name();
        response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK))
                .setBody(cloudAccountCreateRequest));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, ENDPOINT_ORG_LINK_REQUIRED);

        cloudAccountCreateRequest = new CloudAccountCreateRequest();
        cloudAccountCreateRequest.name = "foobar";
        cloudAccountCreateRequest.type = EndpointType.azure.name();
        cloudAccountCreateRequest.orgLink = "dummyOrg";
        response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK))
                .setBody(cloudAccountCreateRequest));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, CREDENTIALS_REQUIRED);

        cloudAccountCreateRequest = new CloudAccountCreateRequest();
        cloudAccountCreateRequest.name = "foobar";
        cloudAccountCreateRequest.type = "invalid-type";
        response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK))
                .setBody(cloudAccountCreateRequest));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, INVALID_ENDPOINT_TYPE);

        cloudAccountCreateRequest = new CloudAccountCreateRequest();
        cloudAccountCreateRequest.name = "foobar";
        cloudAccountCreateRequest.type = EndpointType.aws.name();
        cloudAccountCreateRequest.orgLink = "dummyOrg";
        cloudAccountCreateRequest.credentials = createAwsCredentials("bar", "foo");
        Set<TagViewState> tags = new HashSet<>();
        TagViewState tagA = new TagViewState("", "dummyVal");
        Collections.addAll(tags, tagA);
        cloudAccountCreateRequest.tags = new HashSet<>();
        cloudAccountCreateRequest.tags.addAll(tags);
        response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK))
                .setBody(cloudAccountCreateRequest));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, ENDPOINT_TAG_NULL_EMPTY);

        // test invalid tags for update
        String endpointLink = this.accountTestHelper.createMockAzureEAEndpoint("azure-foo", "foo-1",
                "bar-1").documentSelfLink;
        URI cloudAccountAPIURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(endpointLink));

        CloudAccountPatchRequest cloudAccountPatchRequest = new CloudAccountPatchRequest();
        Set<TagFieldUpdate> tagUpdates = new HashSet<>();
        TagFieldUpdate tagUpdate = new TagFieldUpdate(ADD,"dummyKeyB", "");
        Collections.addAll(tagUpdates, tagUpdate);
        cloudAccountPatchRequest.tagUpdates = new HashSet<>();
        cloudAccountPatchRequest.tagUpdates.addAll(tagUpdates);
        response = this.host.waitForResponse(Operation.createPatch(cloudAccountAPIURI)
                .setBody(cloudAccountPatchRequest));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, ENDPOINT_TAG_NULL_EMPTY);
    }

    @Test
    public void testInvalidOrg() {
        CloudAccountCreateRequest cloudAccountCreateRequest = new CloudAccountCreateRequest();
        cloudAccountCreateRequest.name = "my-endpoint";
        cloudAccountCreateRequest.orgLink = "foobar";
        cloudAccountCreateRequest.type = EndpointType.aws.name();
        cloudAccountCreateRequest.credentials = createAwsCredentials("bar", "foo");

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK))
                .setBody(cloudAccountCreateRequest));
        assertEquals(Operation.STATUS_CODE_FORBIDDEN, response.getStatusCode());
    }

    @Test
    public void testInvalidCredentials() {
        CloudAccountCreateRequest cloudAccountCreateRequest = new CloudAccountCreateRequest();
        cloudAccountCreateRequest.name = "my-endpoint";
        cloudAccountCreateRequest.orgLink = this.orgLink;
        cloudAccountCreateRequest.type = EndpointType.aws.name();
        cloudAccountCreateRequest.credentials = createAwsCredentials("bar", "foo");

        this.host.sendAndWaitExpectFailure(Operation.createPost(
                UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK))
                .setBody(cloudAccountCreateRequest), Operation.STATUS_CODE_BAD_REQUEST);
    }

    @Test
    public void testCloudAccountCreation() throws Throwable {
        this.accountTestHelper.createMockPayingAwsEndpoint("my-endpoint", "foo", "bar", null, null, null);
        this.accountTestHelper.createMockAzureEAEndpoint("my-azure-ea", "foo", "bar");
    }

    @Test
    public void testJobStatusFieldsChanged() throws Throwable {
        String endpointLink = this.accountTestHelper.createMockAwsEndpoint("my-endpoint", "foo", "bar", null, null, null);
        URI cloudAccountAPIURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(endpointLink));
        CloudAccountViewState result = this.host.getServiceState(null, CloudAccountViewState.class, cloudAccountAPIURI);

        // before enumeration, it should be in waiting stage
        assertTrue(result.customProperties.containsKey(ENDPOINT_CUSTOM_PROPERTY_NAME_SYNC_JOB_STATUS));
        assertEquals(WAITING.getStatusCode(), result.customProperties.get(ENDPOINT_CUSTOM_PROPERTY_NAME_SYNC_JOB_STATUS));

        // once the enumeration triggered
        CloudAccountPatchRequest updateRequest = new CloudAccountPatchRequest();
        updateRequest.customPropertyUpdates = new HashMap<>();
        CollectionStringFieldUpdate addBucket = new CollectionStringFieldUpdate();
        addBucket.action = ADD;
        addBucket.value = "STARTED";
        updateRequest.customPropertyUpdates.put(
                EP_CP_ENUMERATION_TASK_STATE, addBucket);
        updateRequest.isMock = true;
        URI endpointURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(endpointLink));
        Operation response = this.host.waitForResponse(Operation.createPatch(endpointURI)
                .setBody(updateRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        assertEquals(RUNNING.getStatusCode(),
                response.getBody(EndpointState.class).customProperties.get(ENDPOINT_CUSTOM_PROPERTY_NAME_SYNC_JOB_STATUS));

        // once the enumeration finished
        updateRequest = new CloudAccountPatchRequest();
        updateRequest.customPropertyUpdates = new HashMap<>();
        addBucket = new CollectionStringFieldUpdate();
        addBucket.action = ADD;
        addBucket.value = "FINISHED";
        updateRequest.customPropertyUpdates.put(
                EP_CP_ENUMERATION_TASK_STATE, addBucket);
        updateRequest.isMock = true;
        endpointURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(endpointLink));
        response = this.host.waitForResponse(Operation.createPatch(endpointURI)
                .setBody(updateRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        assertEquals(DONE.getStatusCode(),
                response.getBody(EndpointState.class).customProperties.get(ENDPOINT_CUSTOM_PROPERTY_NAME_SYNC_JOB_STATUS));

        // once the enumeration failed
        updateRequest = new CloudAccountPatchRequest();
        updateRequest.customPropertyUpdates = new HashMap<>();
        addBucket = new CollectionStringFieldUpdate();
        addBucket.action = ADD;
        addBucket.value = "FAILED";
        updateRequest.customPropertyUpdates.put(
                EP_CP_ENUMERATION_TASK_STATE, addBucket);
        updateRequest.isMock = true;
        endpointURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(endpointLink));
        response = this.host.waitForResponse(Operation.createPatch(endpointURI)
                .setBody(updateRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        assertEquals(FAILED.getStatusCode(),
                response.getBody(EndpointState.class).customProperties.get(ENDPOINT_CUSTOM_PROPERTY_NAME_SYNC_JOB_STATUS));
    }

    @Test
    public void testCloudAccountList() throws Throwable {
        int numEndpoints = 5;
        List<String> endpointLinks = this.accountTestHelper.createMockAwsEndpoints(numEndpoints, null, null, null);

        URI getAllEndpoints = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK);
        ServiceDocumentQueryResult result = this.host.getServiceState(null, ServiceDocumentQueryResult.class, getAllEndpoints);
        this.accountTestHelper.verifyEndpointsPage(endpointLinks, result);
    }

    @Test
    public void testCloudAccountListNoEndpoints() throws Throwable {
        URI getAllEndpoints = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK);
        ServiceDocumentQueryResult result = this.host.getServiceState(null, ServiceDocumentQueryResult.class, getAllEndpoints);
        assertThat(result.documentCount, equalTo(Long.valueOf(0)));
    }

    @Test
    public void testCloudAccountGet() throws Throwable {
        int numEndpoints = 5;
        List<String> endpointLinks = this.accountTestHelper.createMockAwsEndpoints(numEndpoints, null, null, null);
        int randomNdx = ThreadLocalRandom.current().nextInt(0, numEndpoints);
        String randomEndpointLink = endpointLinks.get(randomNdx);
        URI randomApiFriendlyLink = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(randomEndpointLink));

        CloudAccountViewState result = this.host.getServiceState(null, CloudAccountApiService.CloudAccountViewState.class, randomApiFriendlyLink);
        this.accountTestHelper.verifyEndpoint(randomEndpointLink, result);
    }

    @Test
    public void testCloudAccountGetWhenCredentialLookupFails() throws Throwable {
        String endpointLink = this.accountTestHelper.createMockAwsEndpoint("my-endpoint", "foo", "bar", null, null, null);

        EndpointState endpoint = this.testRequestSender.sendGetAndWait(UriUtils.buildUri(this.peerUri, endpointLink), EndpointState.class);

        // Delete the authCredentialsLink
        this.testRequestSender.sendAndWait(Operation.createDelete(UriUtils.buildUri(this.peerUri, endpoint.authCredentialsLink)));

        // Test GET to a specific cloud account id
        URI newCloudAccountGetDirectUri = UriUtils.buildUri(this.peerUri,
                UriUtils.buildUriPath(CloudAccountApiService.SELF_LINK, UriUtils.getLastPathSegment(endpoint.documentSelfLink)));
        CloudAccountViewState cloudAccountViewState = this.testRequestSender.sendGetAndWait(newCloudAccountGetDirectUri, CloudAccountViewState.class);
        assertNotNull(cloudAccountViewState);
        assertNotNull(cloudAccountViewState.errors);
        assertEquals(1, cloudAccountViewState.errors.size());

        // Test GET on factory
        ServiceDocumentQueryResult result = this.testRequestSender.sendGetAndWait(UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK), ServiceDocumentQueryResult.class);
        assertNotNull(result);
    }

    @Test
    public void testCloudAccountGetOldApi() throws Throwable {
        EndpointState oldEndpoint = this.accountTestHelper.createMockAwsEndpointOldApi(
                "AWS with old API", PRIVATE_KEY_PREFIX, PRIVATE_KEY_ID_PREFIX);
        URI newCloudAccountGetDirectUri = UriUtils.buildUri(this.peerUri,
                UriUtils.buildUriPath(CloudAccountApiService.SELF_LINK, UriUtils.getLastPathSegment(oldEndpoint.documentSelfLink)));

        CloudAccountViewState apiView = this.testRequestSender.sendGetAndWait(newCloudAccountGetDirectUri, CloudAccountViewState.class);
        assertThat(apiView, notNullValue());
    }

    @Test
    public void testCloudAccountGet404() throws Throwable {
        URI badEndpointURI = UriUtils
                .buildUri(this.peerUri, CloudAccountApiService.SELF_LINK, UUID.randomUUID().toString());
        this.host.sendAndWaitExpectFailure(
                Operation.createGet(badEndpointURI), Operation.STATUS_CODE_NOT_FOUND);
    }

    @Test
    public void testCloudAccountValidation() throws Throwable {
        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri,
                        CloudAccountApiService.CLOUD_ACCOUNT_VALIDATION_PATH_TEMPLATE)));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());

        CloudAccountValidateRequest validateRequest = new CloudAccountValidateRequest();
        validateRequest.type = EndpointType.aws.name();
        validateRequest.credentials = createAwsCredentials("bar", "foo");
        validateRequest.isMock = true;

        response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri,
                        CloudAccountApiService.CLOUD_ACCOUNT_VALIDATION_PATH_TEMPLATE))
                .setBody(validateRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        validateRequest = new CloudAccountValidateRequest();
        validateRequest.type = EndpointType.aws.name();
        validateRequest.credentials = createAwsCredentials("bar", "foo");

        this.host.sendAndWaitExpectFailure(Operation.createPost(
                UriUtils.buildUri(this.peerUri,
                        CloudAccountApiService.CLOUD_ACCOUNT_VALIDATION_PATH_TEMPLATE))
                .setBody(validateRequest), Operation.STATUS_CODE_BAD_REQUEST);
    }

    @Test
    public void testVsphereCloudAccountUpdateValidationWithId() throws Throwable {
        String endpointLink = this.accountTestHelper.createMockVsphereEndpoint(this.host, this.peerUri, ORG_1_ID,
                "vc-local-test-endpoint", "root", "password",
                "10.112.107.120", "privateCloud1",
                "vc-dc-name", "vc-dc-id");
        String endpointId = UriUtils.getLastPathSegment(endpointLink);
        String cloudAccountLink = UriUtils.buildUriPath(CLOUD_ACCOUNT_API_SERVICE, endpointId);

        // update validate api on above created endpoint passes validation
        CloudAccountValidateRequest validateRequest = new CloudAccountValidateRequest();
        validateRequest.type = EndpointType.vsphere.name();
        validateRequest.credentials = createVSphereCredentials("root", "password");
        Map<String, String> customProperties = new HashMap<>();
        customProperties.put(HOST_NAME_KEY, "10.112.107.120");
        customProperties.put(PRIVATE_CLOUD_NAME_KEY, "privateCloud1");
        customProperties.put(DC_ID_KEY, "vc-dc-id");
        customProperties.put(DC_NAME_KEY, "vc-dc-name");
        validateRequest.customProperties = customProperties;
        validateRequest.id = cloudAccountLink;
        validateRequest.isMock = true;

        URI validateOnlyUri = UriUtils.buildUri(this.peerUri,
                CloudAccountApiService.CLOUD_ACCOUNT_VALIDATION_PATH_TEMPLATE);
        Operation response = this.host.waitForResponse(Operation.createPost(validateOnlyUri)
                .setBody(validateRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        // update validate api on above created endpoint without Id fails validation
        // generic validate api doesn't pass validation because it finds itself(endpoint) while
        // validating duplicate endpoints
        validateRequest.id = null;
        response = this.host.waitForResponse(Operation.createPost(validateOnlyUri)
                .setBody(validateRequest));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, ENDPOINT_ALREADY_EXISTS);
    }


    @Test
    public void testCloudAccountValidationWithId() throws Throwable {
        String awsEndpointLink = this.accountTestHelper.createMockAwsEndpoint(
                "my-aws-endpoint-1", "foo", "bar", null, null, null);
        String endpointId = UriUtils.getLastPathSegment(awsEndpointLink);

        // update validate api passes validation
        CloudAccountValidateRequest validateRequest = new CloudAccountValidateRequest();
        validateRequest.type = EndpointType.aws.name();
        validateRequest.credentials = createAwsCredentials("bar", "foo");
        validateRequest.isMock = true;

        URI validateWithIdUri = UriUtils.buildUri(this.peerUri,
                CloudAccountApiService.SELF_LINK, endpointId, "validate");
        Operation response = this.host.waitForResponse(Operation.createPost(validateWithIdUri)
                .setBody(validateRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        // generic validate api doesn't pass validation because it finds itself(endpoint) while
        // validating duplicate endpoints
        URI validateOnlyUri = UriUtils.buildUri(this.peerUri,
                CloudAccountApiService.CLOUD_ACCOUNT_VALIDATION_PATH_TEMPLATE);

        response = this.host.waitForResponse(Operation.createPost(validateOnlyUri)
                .setBody(validateRequest));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        ServiceErrorResponse error = response.getBody(ServiceErrorResponse.class);
        assertNotNull(error);
        assertEquals(String.valueOf(ENDPOINT_ALREADY_EXISTS.getErrorCode()), error.messageId);
    }


    @Test
    public void testCloudAccountValidationWithIdInRequest() throws Throwable {
        String awsEndpointLink = this.accountTestHelper.createMockAwsEndpoint(
                "my-aws-endpoint-1", "foo", "bar", null, null, null);
        String endpointId = UriUtils.getLastPathSegment(awsEndpointLink);
        String cloudAccountLink = UriUtils.buildUriPath(CLOUD_ACCOUNT_API_SERVICE, endpointId);

        // update validate api passes validation
        CloudAccountValidateRequest validateRequest = new CloudAccountValidateRequest();
        validateRequest.type = EndpointType.aws.name();
        validateRequest.credentials = createAwsCredentials("bar", "foo");
        // its known that link is being called id. This will be deprecated soon in lieu of new
        // validate with Id API
        validateRequest.id = cloudAccountLink;
        validateRequest.isMock = true;

        URI validateOnlyUri = UriUtils.buildUri(this.peerUri,
                CloudAccountApiService.CLOUD_ACCOUNT_VALIDATION_PATH_TEMPLATE);
        Operation response = this.host.waitForResponse(Operation.createPost(validateOnlyUri)
                .setBody(validateRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        // generic validate api doesn't pass validation if no id is passed in the request because it
        // finds itself(endpoint) while validating duplicate endpoints
        validateRequest.id = null;
        response = this.host.waitForResponse(Operation.createPost(validateOnlyUri)
                .setBody(validateRequest));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, ENDPOINT_ALREADY_EXISTS);
    }

    @Test
    public void testAzureEACredentialUpdate() throws Throwable {
        String azureEaEndpointName = "my-azure-ea-1";
        String azureEndpointLink = this.accountTestHelper.createMockAzureEAEndpoint(azureEaEndpointName, "foo-1", "bar-1").documentSelfLink;

        AuthCredentialsService.AuthCredentialsServiceState authCredentialsServiceState = new AuthCredentialsService.AuthCredentialsServiceState();
        authCredentialsServiceState.privateKey = "foo-2";
        Credentials updatedCredential = Credentials.createCredentials(EndpointType.azure_ea.name(),
                authCredentialsServiceState, null);
        CloudAccountPatchRequest patchRequest = new CloudAccountPatchRequest();
        patchRequest.credentials = updatedCredential;
        patchRequest.isMock = true;

        URI cloudAccountAPIURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(azureEndpointLink));

        Operation response = this.host.waitForResponse(Operation.createPatch(cloudAccountAPIURI)
                .setBody(patchRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        EndpointState modelState = this.host.getServiceState(
                null, EndpointState.class, UriUtils.buildUri(this.peerUri, azureEndpointLink));
        AuthCredentialsService.AuthCredentialsServiceState modelAuth = this.host.getServiceState(
                null, AuthCredentialsService.AuthCredentialsServiceState.class, UriUtils.buildUri(this.peerUri, modelState.authCredentialsLink));
        assertThat(authCredentialsServiceState.privateKey, equalTo(modelAuth.privateKey));

        // Test send enrollementId without change.
        authCredentialsServiceState = new AuthCredentialsService.AuthCredentialsServiceState();
        authCredentialsServiceState.privateKeyId = "bar-1";
        authCredentialsServiceState.privateKey = "foo-3";
        updatedCredential = Credentials.createCredentials(EndpointType.azure_ea.name(),
                authCredentialsServiceState, null);
        patchRequest = new CloudAccountPatchRequest();
        patchRequest.credentials = updatedCredential;
        patchRequest.isMock = true;
        response = this.host.waitForResponse(Operation.createPatch(cloudAccountAPIURI)
                .setBody(patchRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        // Test send enrollmentId with change.
        authCredentialsServiceState = new AuthCredentialsService.AuthCredentialsServiceState();
        authCredentialsServiceState.privateKeyId = "bar-2";
        authCredentialsServiceState.privateKey = "foo-4";
        updatedCredential = Credentials.createCredentials(EndpointType.azure_ea.name(),
                authCredentialsServiceState, null);
        patchRequest = new CloudAccountPatchRequest();
        patchRequest.credentials = updatedCredential;
        patchRequest.isMock = true;
        response = this.host.waitForResponse(Operation.createPatch(cloudAccountAPIURI)
                .setBody(patchRequest));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
    }

    @Test
    public void testPatchCredentialWithInvalidTypeOfCloudAccount() throws Throwable {
        // Currently, only support patch credential on azure_ea and vshpere account, other type of
        // account would result in failure.

        // test with aws account
        String aws_endpoint_name = "my-aws-endpoint-1";
        String awsEndpointLink = this.accountTestHelper.createMockAwsEndpoint(aws_endpoint_name, "foo", "bar", null, null, null);
        URI cloudAccountAPIURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(awsEndpointLink));

        CloudAccountPatchRequest updateRequest = new CloudAccountPatchRequest();
        updateRequest.isMock = true;
        AuthCredentialsService.AuthCredentialsServiceState authCredentialsServiceState = new AuthCredentialsService.AuthCredentialsServiceState();
        Credentials updatedCredential = Credentials.createCredentials(EndpointType.aws.name(),
                authCredentialsServiceState, null);
        updateRequest.credentials = updatedCredential;
        Operation response = this.host.waitForResponse(Operation.createPatch(cloudAccountAPIURI)
                .setBody(updateRequest));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
    }

    @Test
    public void testAWSCredentialUpdate() throws Throwable {
        String aws_endpoint_name = "my-aws-endpoint-1";
        String awsEndpointLink = this.accountTestHelper.createMockAwsEndpoint(aws_endpoint_name, "foo", "bar", null, null, null);

        URI cloudAccountAPIURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(awsEndpointLink));
        CloudAccountViewState result = this.host.getServiceState(null, CloudAccountViewState.class, cloudAccountAPIURI);
        assertThat(result.name, equalTo(aws_endpoint_name));
        assertThat(result.description, equalTo(ACCOUNT_DESCRIPTION));


        AuthCredentialsService.AuthCredentialsServiceState authCredentialsServiceState = new AuthCredentialsService.AuthCredentialsServiceState();
        authCredentialsServiceState.privateKey = "foo2";
        authCredentialsServiceState.privateKeyId = "bar2";
        Credentials updatedCredential = Credentials.createCredentials(EndpointType.aws.name(),
                authCredentialsServiceState, null);
        CloudAccountPatchRequest patchRequest = new CloudAccountPatchRequest();
        patchRequest.credentials = updatedCredential;
        patchRequest.isMock = true;

        Operation response = this.host.waitForResponse(Operation.createPatch(cloudAccountAPIURI)
                .setBody(patchRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        EndpointState modelState = this.host.getServiceState(null, EndpointState.class, UriUtils.buildUri(this.peerUri, awsEndpointLink));
        AuthCredentialsService.AuthCredentialsServiceState modelAuth = this.host.getServiceState(
                null, AuthCredentialsService.AuthCredentialsServiceState.class, UriUtils.buildUri(this.peerUri, modelState.authCredentialsLink));
        assertThat(authCredentialsServiceState.privateKey, equalTo(modelAuth.privateKey));
        assertThat(authCredentialsServiceState.privateKeyId, equalTo(modelAuth.privateKeyId));

        authCredentialsServiceState = new AuthCredentialsService.AuthCredentialsServiceState();
        authCredentialsServiceState.privateKey = "";
        authCredentialsServiceState.privateKeyId = "";
        updatedCredential = Credentials.createCredentials(EndpointType.aws.name(),
                authCredentialsServiceState, null);
        patchRequest = new CloudAccountPatchRequest();
        patchRequest.credentials = updatedCredential;
        patchRequest.isMock = true;
        response = this.host.waitForResponse(Operation.createPatch(cloudAccountAPIURI)
                .setBody(patchRequest));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
    }

    @Test
    public void testAzureCredentialUpdate() throws Throwable {
        String azure_endpoint_name = "my-azure-endpoint";
        EndpointState azureEndpoint = this.accountTestHelper.createMockAzureEndpoint(azure_endpoint_name,
                "clientId1", "psw1", "tenantId", "subscriptionId");

        URI cloudAccountAPIURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(azureEndpoint.documentSelfLink));

        AuthCredentialsService.AuthCredentialsServiceState authCredentialsServiceState = new AuthCredentialsService.AuthCredentialsServiceState();
        authCredentialsServiceState.privateKey = "psw2";
        authCredentialsServiceState.privateKeyId = "clientId2";
        Credentials updatedCredential = Credentials.createCredentials(EndpointType.azure.name(),
                authCredentialsServiceState, azureEndpoint.endpointProperties);
        CloudAccountPatchRequest patchRequest = new CloudAccountPatchRequest();
        patchRequest.credentials = updatedCredential;
        patchRequest.isMock = true;

        Operation response = this.host.waitForResponse(Operation.createPatch(cloudAccountAPIURI)
                .setBody(patchRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        EndpointState modelState = this.host.getServiceState(
                null, EndpointState.class, UriUtils.buildUri(this.peerUri, azureEndpoint.documentSelfLink));
        AuthCredentialsService.AuthCredentialsServiceState modelAuth = this.host.getServiceState(
                null, AuthCredentialsService.AuthCredentialsServiceState.class, UriUtils.buildUri(this.peerUri, modelState.authCredentialsLink));
        assertThat(authCredentialsServiceState.privateKey, equalTo(modelAuth.privateKey));
        assertThat(authCredentialsServiceState.privateKeyId, equalTo(modelAuth.privateKeyId));

        // Test update Azure with subscriptionId changed.
        authCredentialsServiceState = new AuthCredentialsService.AuthCredentialsServiceState();
        authCredentialsServiceState.privateKey = "psw3";
        authCredentialsServiceState.privateKeyId = "clientId3";
        updatedCredential = Credentials.createCredentials(EndpointType.azure.name(),
                authCredentialsServiceState, azureEndpoint.endpointProperties);
        updatedCredential.azure.subscriptionId = "subscriptionId2";
        patchRequest = new CloudAccountPatchRequest();
        patchRequest.credentials = updatedCredential;
        patchRequest.isMock = true;
        response = this.host.waitForResponse(Operation.createPatch(cloudAccountAPIURI)
                .setBody(patchRequest));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());

        // Test update Azure without subscriptionId changed.
        authCredentialsServiceState = new AuthCredentialsService.AuthCredentialsServiceState();
        authCredentialsServiceState.privateKey = "psw3";
        authCredentialsServiceState.privateKeyId = "clientId3";
        updatedCredential = Credentials.createCredentials(EndpointType.azure.name(),
                authCredentialsServiceState, azureEndpoint.endpointProperties);
        updatedCredential.azure.subscriptionId = "subscriptionId";
        patchRequest = new CloudAccountPatchRequest();
        patchRequest.credentials = updatedCredential;
        patchRequest.isMock = true;
        response = this.host.waitForResponse(Operation.createPatch(cloudAccountAPIURI)
                .setBody(patchRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        modelState = this.host.getServiceState(
                null, EndpointState.class, UriUtils.buildUri(this.peerUri, azureEndpoint.documentSelfLink));
        modelAuth = this.host.getServiceState(
                null, AuthCredentialsService.AuthCredentialsServiceState.class, UriUtils.buildUri(this.peerUri, modelState.authCredentialsLink));
        assertThat(authCredentialsServiceState.privateKey, equalTo(modelAuth.privateKey));
        assertThat(authCredentialsServiceState.privateKeyId, equalTo(modelAuth.privateKeyId));
    }

    @Test
    public void testVSphereCredentialRemoval() throws Throwable {

        String endpoint_name = "vc-local-test-endpoint";

        String endpointLink = this.accountTestHelper.createMockVsphereEndpoint(this.host, this.peerUri, ORG_1_ID,
                endpoint_name, "root", "password",
                "10.112.107.120", "privateCloud1",
                "vc-dc-name", "vc-dc-id");

        URI cloudAccountAPIURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(endpointLink));
        CloudAccountViewState result = this.host.getServiceState(null, CloudAccountViewState.class, cloudAccountAPIURI);
        this.accountTestHelper.verifyEndpoint(endpointLink, result);
        assertNull(result.endpointProperties.get(PRIVATE_KEY_KEY));
        assertNull(result.endpointProperties.get(PRIVATE_KEYID_KEY));

        EndpointState modelState = this.host.getServiceState(
                null, EndpointState.class, UriUtils.buildUri(this.peerUri, endpointLink));
        // Password must not be exposed from endpoint propeties
        assertNull(modelState.endpointProperties.get(PRIVATE_KEY_KEY));
        assertNull(modelState.endpointProperties.get(PRIVATE_KEYID_KEY));

        AuthCredentialsService.AuthCredentialsServiceState modelAuth = this.host.getServiceState(
                null, AuthCredentialsService.AuthCredentialsServiceState.class, UriUtils.buildUri(this.peerUri, modelState.authCredentialsLink));
        assertThat(modelAuth, notNullValue());
        // Auth link must contain the password
        assertThat("password", equalTo(modelAuth.privateKey));

        // With wrong key (other than "credentials.vsphere.password")
        CloudAccountPatchRequest badRequest = new CloudAccountPatchRequest();
        badRequest.propertyUpdates = new HashMap<>();

        CollectionStringFieldUpdate collectionStringFieldUpdate = new CollectionStringFieldUpdate();
        collectionStringFieldUpdate.action = ADD;
        collectionStringFieldUpdate.value = "noOp";

        badRequest.propertyUpdates.put("credentials.aws.password", collectionStringFieldUpdate);

        Operation response = this.host.waitForResponse(Operation.createPatch(cloudAccountAPIURI).setBody(badRequest));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        assertEquals(CloudAccountErrorCode.ENDPOINT_INVALID_UPDATE_ACTION.getErrorCode(),
                Integer.parseInt(response.getBody(ServiceErrorResponse.class).messageId));

        // with right key but null action.
        badRequest = new CloudAccountPatchRequest();
        badRequest.propertyUpdates = new HashMap<>();
        collectionStringFieldUpdate.action = null;
        badRequest.propertyUpdates.put(EndpointUtils.ENDPOINT_PROPERTY_VSPHERE_PASSWORD, collectionStringFieldUpdate);

        response = this.host.waitForResponse(Operation.createPatch(cloudAccountAPIURI).setBody(badRequest));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        assertEquals(CloudAccountErrorCode.ENDPOINT_INVALID_UPDATE_ACTION.getErrorCode(),
                Integer.parseInt(response.getBody(ServiceErrorResponse.class).messageId));

        // With right parameters, validate the result
        this.host.log(Level.INFO," Before patch request: " + Utils.toJson(modelAuth));
        testCredentialsRemoval(endpoint_name, endpointLink, cloudAccountAPIURI);
        this.host.log(Level.INFO," After patch request: " + Utils.toJson(modelAuth));

        // Test for system user
        String endpoint_name2 = "vc-local-test-endpoint2";
        String endpointLink2 = this.accountTestHelper.createMockVsphereEndpoint(this.host, this.peerUri, ORG_1_ID,
                endpoint_name2, "root2", "password2",
                "10.112.107.120", "privateCloud1",
                "vc-dc-name", "vc-dc-id");

        URI cloudAccountAPIURI2 = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(endpointLink2));
        result = this.host.getServiceState(null, CloudAccountViewState.class, cloudAccountAPIURI2);
        this.accountTestHelper.verifyEndpoint(endpointLink2, result);

        modelState = this.host.getServiceState(
                null, EndpointState.class, UriUtils.buildUri(this.peerUri, endpointLink2));
        modelAuth = this.host.getServiceState(
                null, AuthCredentialsService.AuthCredentialsServiceState.class, UriUtils.buildUri(this.peerUri, modelState.authCredentialsLink));
        assertThat(modelAuth, notNullValue());
        // Auth link must contain the password
        assertThat("password2", equalTo(modelAuth.privateKey));

        // Patch it and verify the result
        this.host.setSystemAuthorizationContext();
        testCredentialsRemoval(endpoint_name2, endpointLink2, cloudAccountAPIURI2);
        this.host.resetAuthorizationContext();
    }

    private void testCredentialsRemoval(String endpoint_name, String endpointLink, URI cloudAccountAPIURI) {
        CloudAccountPatchRequest updateRequest = new CloudAccountPatchRequest();
        updateRequest.name = endpoint_name;
        updateRequest.propertyUpdates = new HashMap<>();
        CollectionStringFieldUpdate collectionStringFieldUpdate = new CollectionStringFieldUpdate();
        collectionStringFieldUpdate.action = UpdateAction.REMOVE;
        updateRequest.propertyUpdates.put(EndpointUtils.ENDPOINT_PROPERTY_VSPHERE_PASSWORD, collectionStringFieldUpdate);
        updateRequest.propertyUpdates.put(EndpointUtils.ENDPOINT_PROPERTY_VSPHERE_USERNAME, collectionStringFieldUpdate);

        Operation response = this.host.waitForResponse(Operation.createPatch(cloudAccountAPIURI).setBody(updateRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        EndpointState modelState = this.host.getServiceState(
                null, EndpointState.class, UriUtils.buildUri(this.peerUri, endpointLink));
        AuthCredentialsService.AuthCredentialsServiceState modelAuth = this.host.getServiceState(
                null, AuthCredentialsService.AuthCredentialsServiceState.class, UriUtils.buildUri(this.peerUri, modelState.authCredentialsLink));
        assertThat(modelAuth, notNullValue());

        // Auth link must not contain the password (it will be empty string)
        assertTrue(modelAuth.privateKey.isEmpty());
        assertTrue(modelAuth.privateKeyId.isEmpty());
    }

    @Test
    public void testCloudAccountPartialUpdateWithMockMode() {
        String aws_endpoint_name = "my-aws-endpoint-1";
        String aws_updated_endpoint_name = "my-aws-endpoint-2";
        String aws_updated_desc = "aws_updated_desc";
        String azure_endpoint_name = "my-azure-ea-1";
        String azure_updated_endpoint_name = "my-azure-ea-2";
        String azure_updated_desc = "azure_updated_desc";

        String awsEndpointLink = this.accountTestHelper.createMockAwsEndpoint(aws_endpoint_name, "foo", "bar", null, null, null);
        String azureEndpointLink = this.accountTestHelper.createMockAzureEAEndpoint(azure_endpoint_name, "foo-1", "bar-1").documentSelfLink;

        URI endpointURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(awsEndpointLink));
        CloudAccountViewState result = this.host.getServiceState(null, CloudAccountViewState.class, endpointURI);
        assertThat(result.name, equalTo(aws_endpoint_name));
        assertThat(result.description, equalTo(ACCOUNT_DESCRIPTION));

        endpointURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(azureEndpointLink));
        result = this.host.getServiceState(null, CloudAccountViewState.class, endpointURI);
        assertThat(result.name, equalTo(azure_endpoint_name));
        assertThat(result.description, equalTo(ACCOUNT_DESCRIPTION));

        CloudAccountPatchRequest updateRequest = new CloudAccountPatchRequest();
        updateRequest.name = aws_updated_endpoint_name;
        updateRequest.description = aws_updated_desc;
        updateRequest.isMock = true;

        endpointURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(awsEndpointLink));
        Operation response = this.host.waitForResponse(Operation.createPatch(endpointURI).setBody(updateRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        CloudAccountViewState viewState = response.getBody(CloudAccountViewState.class);
        assertThat(viewState.name, equalTo(aws_updated_endpoint_name));
        assertThat(viewState.description, equalTo(aws_updated_desc));
        result = this.host.getServiceState(null, CloudAccountViewState.class, endpointURI);
        assertThat(result.name, equalTo(aws_updated_endpoint_name));
        assertThat(result.description, equalTo(aws_updated_desc));

        updateRequest = new CloudAccountPatchRequest();
        updateRequest.name = azure_updated_endpoint_name;
        updateRequest.description = azure_updated_desc;
        updateRequest.isMock = true;

        endpointURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(azureEndpointLink));
        response = this.host.waitForResponse(Operation.createPatch(endpointURI).setBody(updateRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        viewState = response.getBody(CloudAccountViewState.class);
        assertThat(viewState.name, equalTo(azure_updated_endpoint_name));
        assertThat(viewState.description, equalTo(azure_updated_desc));
        result = this.host.getServiceState(null, CloudAccountViewState.class, endpointURI);
        assertThat(result.name, equalTo(azure_updated_endpoint_name));
        assertThat(result.description, equalTo(azure_updated_desc));
    }

    @Test
    public void testCloudAccountPartialUpdateWithoutMockMode() {
        String aws_endpoint_name = "my-aws-endpoint-1";
        String aws_updated_endpoint_name = "my-aws-endpoint-2";
        String aws_updated_desc = "aws_updated_desc";
        String azure_endpoint_name = "my-azure-ea-1";
        String azure_updated_endpoint_name = "my-azure-ea-2";
        String azure_updated_desc = "azure_updated_desc";

        String awsEndpointLink = this.accountTestHelper.createMockAwsEndpoint(aws_endpoint_name, "foo",
                "bar",
                null, null, null);
        String azureEndpointLink = this.accountTestHelper.createMockAzureEAEndpoint(azure_endpoint_name, "foo-1", "bar-1").documentSelfLink;

        URI endpointURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(awsEndpointLink));
        CloudAccountViewState result = this.host.getServiceState(null, CloudAccountViewState.class, endpointURI);
        assertThat(result.name, equalTo(aws_endpoint_name));
        assertThat(result.description, equalTo(ACCOUNT_DESCRIPTION));

        endpointURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(azureEndpointLink));
        result = this.host.getServiceState(null, CloudAccountViewState.class, endpointURI);
        assertThat(result.name, equalTo(azure_endpoint_name));
        assertThat(result.description, equalTo(ACCOUNT_DESCRIPTION));

        CloudAccountPatchRequest updateRequest = new CloudAccountPatchRequest();
        updateRequest.name = aws_updated_endpoint_name;
        updateRequest.description = aws_updated_desc;
        updateRequest.isMock = false;

        endpointURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(awsEndpointLink));
        Operation response = this.host.waitForResponse(Operation.createPatch(endpointURI).setBody(updateRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        CloudAccountViewState viewState = response.getBody(CloudAccountViewState.class);
        assertThat(viewState.name, equalTo(aws_updated_endpoint_name));
        assertThat(viewState.description, equalTo(aws_updated_desc));
        result = this.host.getServiceState(null, CloudAccountViewState.class, endpointURI);
        assertThat(result.name, equalTo(aws_updated_endpoint_name));
        assertThat(result.description, equalTo(aws_updated_desc));

        updateRequest = new CloudAccountPatchRequest();
        updateRequest.name = azure_updated_endpoint_name;
        updateRequest.description = azure_updated_desc;
        updateRequest.isMock = false;

        endpointURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(azureEndpointLink));
        response = this.host.waitForResponse(Operation.createPatch(endpointURI).setBody(updateRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        viewState = response.getBody(CloudAccountViewState.class);
        assertThat(viewState.name, equalTo(azure_updated_endpoint_name));
        assertThat(viewState.description, equalTo(azure_updated_desc));
        result = this.host.getServiceState(null, CloudAccountViewState.class, endpointURI);
        assertThat(result.name, equalTo(azure_updated_endpoint_name));
        assertThat(result.description, equalTo(azure_updated_desc));
    }

    @Test
    public void testCloudAccountDeletion() {
        Operation response = this.host.waitForResponse(Operation.createDelete(
                UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK)));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, ENDPOINT_ID_REQUIRED);

        String path = UriUtils.buildUriPath(CloudAccountApiService.SELF_LINK, "foo");
        response = this.host.waitForResponse(Operation.createDelete(
                UriUtils.buildUri(this.peerUri, path)));
        assertEquals(Operation.STATUS_CODE_NOT_FOUND, response.getStatusCode());

        String endpointLink = this.accountTestHelper.createMockAwsEndpoint("my-endpoint", "foo", "bar", null, null, null);

        path = UriUtils.buildUriPath(CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(endpointLink));
        response = this.host.waitForResponse(Operation.createDelete(
                UriUtils.buildUri(this.peerUri, path)));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
    }

    private void verifyError(Operation op, ErrorCode errorCode) {
        ServiceErrorResponse error = op.getBody(ServiceErrorResponse.class);
        assertNotNull(error);
        assertEquals(String.valueOf(errorCode.getErrorCode()), error.messageId);
    }

    @Test
    public void testInvalidOverwriteCredentials() {
        CloudAccountCreateRequest createRequest = new CloudAccountCreateRequest();
        createRequest.name = "test-endpoint";
        createRequest.orgLink = this.orgLink;
        createRequest.type = EndpointType.aws.name();
        Map<String, String> endpointProperties = new HashMap<>();
        endpointProperties.put(PRIVATE_KEY_KEY, "foo");
        endpointProperties.put(PRIVATE_KEYID_KEY, "bar");
        createRequest.endpointProperties = endpointProperties;
        createRequest.credentials = createAwsCredentials("bar-overwrite", "foo-overwrite");
        createRequest.isMock = true;

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK))
                .setBody(createRequest));
        assertEquals(Operation.STATUS_CODE_CREATED, response.getStatusCode());
        String cloudAccountLink = response.getResponseHeader(Operation.LOCATION_HEADER);
        assertNotNull(cloudAccountLink);
        String endpointLink = UriUtils.buildUriPath(EndpointService.FACTORY_LINK, UriUtils.getLastPathSegment(cloudAccountLink));
        assertNotNull(endpointLink);

        Operation getResp = this.host.waitForResponse(
                Operation.createGet(UriUtils.buildUri(this.peerUri, endpointLink)));
        EndpointState createdEndpoint =
                getResp.getBody(EndpointState.class);
        assertNotNull(createdEndpoint);
        Assert.assertEquals(2, createdEndpoint.endpointProperties.size());
        Assert.assertEquals("bar-overwrite", createdEndpoint.endpointProperties.get(PRIVATE_KEYID_KEY));
        Assert.assertEquals("true", createdEndpoint.endpointProperties.get("supportPublicImages"));
    }

    @Test
    public void testCreateVSphereEndpoint() {
        String endpointLink = this.accountTestHelper.createMockVsphereEndpoint(this.host, this.peerUri, ORG_1_ID,
                "vc-local-test-endpoint", "root", "password",
                "10.112.107.120", "privateCloud1",
                "vc-dc-name", "vc-dc-id");

        URI endpointURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(endpointLink));
        CloudAccountViewState result = this.host.getServiceState(null, CloudAccountViewState.class, endpointURI);
        this.accountTestHelper.verifyEndpoint(endpointLink, result);
    }

    @Test
    public void testCreateDuplicateVSphereEndpointThrowsError() throws Throwable {
        // combination of this.hostName + userName should not be duplicate
        String endpointLink = this.accountTestHelper.createMockVsphereEndpoint(this.host, this.peerUri, ORG_1_ID,
                "vc-local-test-endpoint", "root", "password",
                "10.112.107.120", "privateCloud1",
                "vc-dc-name", "vc-dc-id");

        URI endpointURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(endpointLink));
        CloudAccountViewState result = this.host.getServiceState(
                null, CloudAccountViewState.class, endpointURI);
        this.accountTestHelper.verifyEndpoint(endpointLink, result);

        CloudAccountCreateRequest task = new CloudAccountCreateRequest();
        task.name = "vc-local-test-endpoint";
        task.orgId = ORG_1_ID;
        task.description = ACCOUNT_DESCRIPTION;
        task.type = EndpointType.vsphere.name();

        Map<String, String> customProperties = new HashMap<>();
        customProperties.put(HOST_NAME_KEY, "10.112.107.120");
        customProperties.put(PRIVATE_CLOUD_NAME_KEY, "privateCloud1");
        customProperties.put(DC_ID_KEY, "vc-dc-id");
        customProperties.put(DC_NAME_KEY, "vc-dc-name");
        task.customProperties = customProperties;
        task.credentials = createVSphereCredentials("root", "password");

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        ServiceErrorResponse errorResponse = response.getBody(ServiceErrorResponse.class);
        String endpointId = UriUtils.getLastPathSegment(endpointLink);
        String expectedCloudAccountLink = UriUtils.buildUriPath(
                CLOUD_ACCOUNT_API_SERVICE, endpointId);
        assertEquals(expectedCloudAccountLink, errorResponse.message);
        assertEquals(ENDPOINT_ALREADY_EXISTS.getErrorCode(),
                Integer.parseInt(errorResponse.messageId));
    }

    @Test
    public void testCreateVSphereEndpointWithSameHostnameDifferentUserShouldWork() throws Throwable {
        // combination of this.hostName + userName should not be duplicate
        String endpointLink = this.accountTestHelper.createMockVsphereEndpoint(this.host, this.peerUri, ORG_1_ID,
                "vc-local-test-endpoint", "root", "password",
                "10.112.107.120", "privateCloud1",
                "vc-dc-name", "vc-dc-id");

        URI endpointURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(endpointLink));
        CloudAccountViewState result = this.host.getServiceState(
                null, CloudAccountViewState.class, endpointURI);
        this.accountTestHelper.verifyEndpoint(endpointLink, result);

        CloudAccountCreateRequest task = new CloudAccountCreateRequest();
        task.name = "vc-local-test-endpoint";
        task.orgId = ORG_1_ID;
        task.description = ACCOUNT_DESCRIPTION;
        task.type = EndpointType.vsphere.name();

        Map<String, String> customProperties = new HashMap<>();
        customProperties.put(HOST_NAME_KEY, "10.112.107.120");
        customProperties.put(PRIVATE_CLOUD_NAME_KEY, "privateCloud1");
        customProperties.put(DC_ID_KEY, "vc-dc-id");
        customProperties.put(DC_NAME_KEY, "vc-dc-name");
        task.customProperties = customProperties;
        task.credentials = createVSphereCredentials("root1", "password");

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_CREATED, response.getStatusCode());
    }

    @Test
    public void testCreateVSphereEndpointWithDifferentHostnameSameUserShouldWork() throws  Throwable {
        // combination of this.hostName + userName should not be duplicate
        String endpointLink = this.accountTestHelper.createMockVsphereEndpoint(this.host, this.peerUri, ORG_1_ID,
                "vc-local-test-endpoint", "root", "password",
                "10.112.107.121", "privateCloud1",
                "vc-dc-name", "vc-dc-id");

        URI endpointURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(endpointLink));
        CloudAccountViewState result = this.host.getServiceState(
                null, CloudAccountViewState.class, endpointURI);
        this.accountTestHelper.verifyEndpoint(endpointLink, result);

        CloudAccountCreateRequest task = new CloudAccountCreateRequest();
        task.name = "vc-local-test-endpoint";
        task.orgId = ORG_1_ID;
        task.description = ACCOUNT_DESCRIPTION;
        task.type = EndpointType.vsphere.name();

        Map<String, String> customProperties = new HashMap<>();
        customProperties.put(HOST_NAME_KEY, "10.112.107.120");
        customProperties.put(PRIVATE_CLOUD_NAME_KEY, "privateCloud1");
        customProperties.put(DC_ID_KEY, "vc-dc-id");
        customProperties.put(DC_NAME_KEY, "vc-dc-name");
        task.customProperties = customProperties;
        task.credentials = createVSphereCredentials("root", "password");

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_CREATED, response.getStatusCode());
    }

    @Test
    public void testCreateVSphereEndpointWithoutEndpointProperties() {
        CloudAccountCreateRequest requestBody = this.accountTestHelper
                .createMockVsphereEndpointWithoutEndpointProperties(ORG_1_ID,"vc-local-test-endpoint", "root",
                        "password", "10.112.107.120", "privateCloud1",
                        "vc-dc-name", "vc-dc-id");

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK))
                .setBody(requestBody));
        assertEquals(Operation.STATUS_CODE_CREATED, response.getStatusCode());

        CloudAccountCreateRequest responseBody = response.getBody(CloudAccountCreateRequest.class);

        // Because the request payload only set the customProperties, so there should be nothing
        // in the endpointProperties in the post response.
        assertEquals(0, responseBody.endpointProperties.size());
        assertEquals("10.112.107.120", requestBody.customProperties.get(HOST_NAME_KEY));
        assertEquals("vc-dc-id", requestBody.customProperties.get(DC_ID_KEY));
        assertEquals("vc-dc-name", requestBody.customProperties.get(DC_NAME_KEY));
        assertEquals("privateCloud1", requestBody.customProperties.get(PRIVATE_CLOUD_NAME_KEY));

        String cloudAccountLink = response.getResponseHeader(Operation.LOCATION_HEADER);
        assertNotNull(cloudAccountLink);
        String endpointLink = UriUtils.buildUriPath(EndpointService.FACTORY_LINK, UriUtils.getLastPathSegment(cloudAccountLink));

        Operation getResp = this.host.waitForResponse(
                Operation.createGet(UriUtils.buildUri(this.peerUri, endpointLink)));
        EndpointState createdEndpoint = getResp.getBody(EndpointState.class);

        // When we Get on this cloud-account, we should see the values both in customProperties
        // and endpointProperties.
        assertNotNull(createdEndpoint);
        assertEquals("10.112.107.120", createdEndpoint.customProperties.get(HOST_NAME_KEY));
        assertEquals("vc-dc-id", createdEndpoint.customProperties.get(DC_ID_KEY));
        assertEquals("vc-dc-name", createdEndpoint.customProperties.get(DC_NAME_KEY));
        assertEquals("privateCloud1", createdEndpoint.customProperties.get(PRIVATE_CLOUD_NAME_KEY));
        assertEquals("vc-dc-id", createdEndpoint.endpointProperties.get(DC_ID_KEY));
        assertEquals("vc-dc-name", createdEndpoint.endpointProperties.get(DC_NAME_KEY));
        assertEquals("privateCloud1", createdEndpoint.endpointProperties.get(PRIVATE_CLOUD_NAME_KEY));
    }

    @Test
    public void testCreateVSphereEndpointWithCustomPropertiesAndEndpointProperties() {
        CloudAccountCreateRequest requestBody = this.accountTestHelper
                .createMockVsphereEndpointWithCustomProperties(ORG_1_ID,"vc-local-test-endpoint", "root",
                        "password", "10.112.107.120", "privateCloud1",
                        "vc-dc-name", "vc-dc-id");
        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK))
                .setBody(requestBody));
        assertEquals(Operation.STATUS_CODE_CREATED, response.getStatusCode());

        CloudAccountCreateRequest responseBody = response.getBody(CloudAccountCreateRequest.class);

        // customProperties and endpointProperties should show the same values.
        assertEquals("10.112.107.120", responseBody.endpointProperties.get(HOST_NAME_KEY));
        assertEquals("vc-dc-id", responseBody.endpointProperties.get(DC_ID_KEY));
        assertEquals("vc-dc-name", responseBody.endpointProperties.get(DC_NAME_KEY));
        assertEquals("privateCloud1", responseBody.endpointProperties.get(PRIVATE_CLOUD_NAME_KEY));

        assertEquals("10.112.107.120", responseBody.customProperties.get(HOST_NAME_KEY));
        assertEquals("vc-dc-id", responseBody.customProperties.get(DC_ID_KEY));
        assertEquals("vc-dc-name", responseBody.customProperties.get(DC_NAME_KEY));
        assertEquals("privateCloud1", responseBody.customProperties.get(PRIVATE_CLOUD_NAME_KEY));

        String cloudAccountLink = response.getResponseHeader(Operation.LOCATION_HEADER);
        assertNotNull(cloudAccountLink);
        String endpointLink = UriUtils.buildUriPath(EndpointService.FACTORY_LINK, UriUtils.getLastPathSegment(cloudAccountLink));

        Operation getResp = this.host.waitForResponse(
                Operation.createGet(UriUtils.buildUri(this.peerUri, endpointLink)));
        EndpointState createdEndpoint = getResp.getBody(EndpointState.class);

        // When we Get on this cloud-account, we should see the values both in customProperties
        // and endpointProperties.
        assertNotNull(createdEndpoint);
        assertEquals("10.112.107.120", createdEndpoint.customProperties.get(HOST_NAME_KEY));
        assertEquals("vc-dc-id", createdEndpoint.customProperties.get(DC_ID_KEY));
        assertEquals("vc-dc-name", createdEndpoint.customProperties.get(DC_NAME_KEY));
        assertEquals("privateCloud1", createdEndpoint.customProperties.get(PRIVATE_CLOUD_NAME_KEY));
        assertEquals("vc-dc-id", createdEndpoint.endpointProperties.get(DC_ID_KEY));
        assertEquals("vc-dc-name", createdEndpoint.endpointProperties.get(DC_NAME_KEY));
        assertEquals("privateCloud1", createdEndpoint.endpointProperties.get(PRIVATE_CLOUD_NAME_KEY));
    }

    @Test
    public void testCreateWithOrgId() {
        CloudAccountCreateRequest createRequest = new CloudAccountCreateRequest();
        createRequest.name = "test-endpoint";
        // Create with a dummy link - this should be replaced when orgID is provided
        createRequest.orgLink = "dummyLink";
        createRequest.orgId = ORG_1_ID;
        createRequest.type = EndpointType.aws.name();
        createRequest.credentials = createAwsCredentials("bar", "foo");
        createRequest.isMock = true;

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK))
                .setBody(createRequest));
        assertEquals(Operation.STATUS_CODE_CREATED, response.getStatusCode());
        String cloudAccountLink = response.getResponseHeader(Operation.LOCATION_HEADER);
        assertNotNull(cloudAccountLink);
        String endpointLink = UriUtils.buildUriPath(EndpointService.FACTORY_LINK, UriUtils.getLastPathSegment(cloudAccountLink));
        assertNotNull(endpointLink);
    }

    @Test
    public void testSummaryWithNoInput() throws Throwable {
        Long numAWSAccounts = 3L;
        Long numAzureEAAccounts = 2L;
        Long numVsphereAccounts = 1L;
        createCloudAccountsForSummaryTest(null);
        CloudAccountSummaryRequest cloudAccountSummaryRequest = new CloudAccountSummaryRequest();
        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK, "summary"))
                .setBody(cloudAccountSummaryRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        CloudAccountSummaryViewState cloudAccountSummaryViewState =
                response.getBody(CloudAccountSummaryViewState.class);
        assertNotNull(cloudAccountSummaryViewState.typeSummary);
        for (EndpointType endpointType : EndpointType.values()) {
            CloudAccountTypeSummary summary = cloudAccountSummaryViewState.typeSummary.get(endpointType.name());
            if (summary == null) {
                this.host.log(Level.WARNING, "CloudAccountTypeSummary is null for %s", endpointType.name());
                this.host.log(Level.WARNING, "Contents of cloudAccountSummaryViewState.typeSummary");
                for (String type : cloudAccountSummaryViewState.typeSummary.keySet()) {
                    CloudAccountTypeSummary tempSummary = cloudAccountSummaryViewState.typeSummary.get(type);
                    this.host.log("Type [%s] Count [%d]", type, tempSummary.count);
                }
            }
            assertNotNull(summary);
            switch (endpointType) {
            case aws:
                assertEquals(numAWSAccounts, summary.count);
                break;
            case azure_ea:
                assertEquals(numAzureEAAccounts, summary.count);
                break;
            case vsphere:
                assertEquals(numVsphereAccounts, summary.count);
                break;
            default:
                break;
            }
        }
    }

    @Test
    public void testSummaryWithValidInput() throws Throwable {
        Long numAWSAccounts = 3L;
        Long numVsphereAccounts = 1L;
        createCloudAccountsForSummaryTest(null);
        CloudAccountSummaryRequest cloudAccountSummaryRequest = new CloudAccountSummaryRequest();
        cloudAccountSummaryRequest.cloudAccountTypes = new ArrayList<>();
        cloudAccountSummaryRequest.cloudAccountTypes.add(EndpointType.aws.name());
        cloudAccountSummaryRequest.cloudAccountTypes.add(EndpointType.vsphere.name());
        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK, "summary"))
                .setBody(cloudAccountSummaryRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        CloudAccountSummaryViewState cloudAccountSummaryViewState =
                response.getBody(CloudAccountSummaryViewState.class);
        assertNotNull(cloudAccountSummaryViewState.typeSummary);
        assertEquals(2, cloudAccountSummaryViewState.typeSummary.size());

        CloudAccountTypeSummary summary = cloudAccountSummaryViewState.typeSummary.get(EndpointType.aws.name());
        assertNotNull(summary);
        assertEquals(numAWSAccounts, summary.count);

        summary = cloudAccountSummaryViewState.typeSummary.get(EndpointType.vsphere.name());
        assertNotNull(summary);
        assertEquals(numVsphereAccounts, summary.count);
    }

    @Test
    public void testsummaryWithMixOfValidAndInvalidInput() throws Throwable {
        Long numAWSAccounts = 3L;
        createCloudAccountsForSummaryTest(null);
        CloudAccountSummaryRequest cloudAccountSummaryRequest = new CloudAccountSummaryRequest();
        cloudAccountSummaryRequest.cloudAccountTypes = new ArrayList<>();
        cloudAccountSummaryRequest.cloudAccountTypes.add(EndpointType.aws.name());
        cloudAccountSummaryRequest.cloudAccountTypes.add("dummy");
        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK, "summary"))
                .setBody(cloudAccountSummaryRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        CloudAccountSummaryViewState cloudAccountSummaryViewState =
                response.getBody(CloudAccountSummaryViewState.class);
        assertNotNull(cloudAccountSummaryViewState.typeSummary);
        assertEquals(1, cloudAccountSummaryViewState.typeSummary.size());

        CloudAccountTypeSummary summary = cloudAccountSummaryViewState.typeSummary.get(EndpointType.aws.name());
        assertNotNull(summary);
        assertEquals(numAWSAccounts, summary.count);
    }

    @Test
    public void testSummaryWithInvalidInput() throws Throwable {
        createCloudAccountsForSummaryTest(null);
        CloudAccountSummaryRequest cloudAccountSummaryRequest = new CloudAccountSummaryRequest();
        cloudAccountSummaryRequest.cloudAccountTypes = new ArrayList<>();
        cloudAccountSummaryRequest.cloudAccountTypes.add("dummy1");
        cloudAccountSummaryRequest.cloudAccountTypes.add("dummy2");
        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK, "summary"))
                .setBody(cloudAccountSummaryRequest));

        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
    }

    @Test
    public void testSummaryWithNoInputAndDefaultService() throws Throwable {
        Long zero = 0L;
        Long numAWSAccounts = 3L;
        Long numAzureEAAccounts = 2L;
        Long numVsphereAccounts = 1L;

        // create resource for default service (discovery) and issue summary request on cost_insight
        // expect 0 results
        createCloudAccountsForSummaryTest(null);
        CloudAccountSummaryRequest cloudAccountSummaryRequest = new CloudAccountSummaryRequest();
        cloudAccountSummaryRequest.service = COST_INSIGHT;
        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK, "summary"))
                .setBody(cloudAccountSummaryRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        CloudAccountSummaryViewState cloudAccountSummaryViewState =
                response.getBody(CloudAccountSummaryViewState.class);
        assertNotNull(cloudAccountSummaryViewState.typeSummary);
        for (EndpointType endpointType : EndpointType.values()) {
            CloudAccountTypeSummary summary = cloudAccountSummaryViewState.typeSummary.get(endpointType.name());
            if (summary == null) {
                this.host.log(Level.WARNING, "CloudAccountTypeSummary is null for %s", endpointType.name());
                this.host.log(Level.WARNING, "Contents of cloudAccountSummaryViewState.typeSummary");
                for (String type : cloudAccountSummaryViewState.typeSummary.keySet()) {
                    CloudAccountTypeSummary tempSummary = cloudAccountSummaryViewState.typeSummary.get(type);
                    this.host.log("Type [%s] Count [%d]", type, tempSummary.count);
                }
            }
            assertNotNull(summary);
            assertEquals(zero, summary.count);
        }

        // request summary on default service (discovery) - expect results
        cloudAccountSummaryRequest.service = null;
        response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK, "summary"))
                .setBody(cloudAccountSummaryRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        cloudAccountSummaryViewState = response.getBody(CloudAccountSummaryViewState.class);
        for (EndpointType endpointType : EndpointType.values()) {
            CloudAccountTypeSummary summary = cloudAccountSummaryViewState.typeSummary.get(endpointType.name());
            if (summary == null) {
                this.host.log(Level.WARNING, "CloudAccountTypeSummary is null for %s", endpointType.name());
                this.host.log(Level.WARNING, "Contents of cloudAccountSummaryViewState.typeSummary");
                for (String type : cloudAccountSummaryViewState.typeSummary.keySet()) {
                    CloudAccountTypeSummary tempSummary = cloudAccountSummaryViewState.typeSummary.get(type);
                    this.host.log("Type [%s] Count [%d]", type, tempSummary.count);
                }
            }
            assertNotNull(summary);
            switch (endpointType) {
            case aws:
                assertEquals(numAWSAccounts, summary.count);
                break;
            case azure_ea:
                assertEquals(numAzureEAAccounts, summary.count);
                break;
            case vsphere:
                assertEquals(numVsphereAccounts, summary.count);
                break;
            default:
                break;
            }
        }
    }

    @Test
    public void testSummaryWithNoInputAndOtherService() throws Throwable {
        Long zero = 0L;
        Long numAWSAccounts = 3L;
        Long numAzureEAAccounts = 2L;
        Long numVsphereAccounts = 1L;

        // create resource for cost_insight service (only to aws accounts) and issue summary
        // request on discovery
        // expect non-0 results since by default they all have discovery as well
        createCloudAccountsForSummaryTest(COST_INSIGHT);
        CloudAccountSummaryRequest cloudAccountSummaryRequest = new CloudAccountSummaryRequest();
        cloudAccountSummaryRequest.service = null;
        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK, "summary"))
                .setBody(cloudAccountSummaryRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        CloudAccountSummaryViewState cloudAccountSummaryViewState =
                response.getBody(CloudAccountSummaryViewState.class);
        assertNotNull(cloudAccountSummaryViewState.typeSummary);
        for (EndpointType endpointType : EndpointType.values()) {
            CloudAccountTypeSummary summary = cloudAccountSummaryViewState.typeSummary.get(endpointType.name());
            if (summary == null) {
                this.host.log(Level.WARNING, "CloudAccountTypeSummary is null for %s", endpointType.name());
                this.host.log(Level.WARNING, "Contents of cloudAccountSummaryViewState.typeSummary");
                for (String type : cloudAccountSummaryViewState.typeSummary.keySet()) {
                    CloudAccountTypeSummary tempSummary = cloudAccountSummaryViewState.typeSummary.get(type);
                    this.host.log("Type [%s] Count [%d]", type, tempSummary.count);
                }
            }
            assertNotNull(summary);
            switch (endpointType) {
            case aws:
                assertEquals(numAWSAccounts, summary.count);
                break;
            case azure_ea:
                assertEquals(numAzureEAAccounts, summary.count);
                break;
            case vsphere:
                assertEquals(numVsphereAccounts, summary.count);
                break;
            default:
                break;
            }
        }

        // summary request by cost_insight - expect 0 results for all clouds apart from aws
        cloudAccountSummaryRequest.service = COST_INSIGHT;
        response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK, "summary"))
                .setBody(cloudAccountSummaryRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        cloudAccountSummaryViewState = response.getBody(CloudAccountSummaryViewState.class);
        assertNotNull(cloudAccountSummaryViewState.typeSummary);
        for (EndpointType endpointType : EndpointType.values()) {
            CloudAccountTypeSummary summary = cloudAccountSummaryViewState.typeSummary.get(endpointType.name());
            if (summary == null) {
                this.host.log(Level.WARNING, "CloudAccountTypeSummary is null for %s", endpointType.name());
                this.host.log(Level.WARNING, "Contents of cloudAccountSummaryViewState.typeSummary");
                for (String type : cloudAccountSummaryViewState.typeSummary.keySet()) {
                    CloudAccountTypeSummary tempSummary = cloudAccountSummaryViewState.typeSummary.get(type);
                    this.host.log("Type [%s] Count [%d]", type, tempSummary.count);
                }
            }
            assertNotNull(summary);
            switch (endpointType) {
            case aws:
                assertEquals(numAWSAccounts, summary.count);
                break;
            case azure_ea:
                assertEquals(zero, summary.count);
                break;
            case vsphere:
                assertEquals(zero, summary.count);
                break;
            default:
                break;
            }
        }
    }

    @Test
    public void testSummaryWithNoInputAndMatchingService() throws Throwable {
        Long zero = 0L;

        // create resource for CI service (cost_insight) and do a summary request on summary
        createCloudAccountsForSummaryTest(DISCOVERY);
        CloudAccountSummaryRequest cloudAccountSummaryRequest = new CloudAccountSummaryRequest();
        cloudAccountSummaryRequest.service = COST_INSIGHT;
        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK, "summary"))
                .setBody(cloudAccountSummaryRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        CloudAccountSummaryViewState cloudAccountSummaryViewState =
                response.getBody(CloudAccountSummaryViewState.class);
        assertNotNull(cloudAccountSummaryViewState.typeSummary);
        for (EndpointType endpointType : EndpointType.values()) {
            CloudAccountTypeSummary summary = cloudAccountSummaryViewState.typeSummary.get(endpointType.name());
            if (summary == null) {
                this.host.log(Level.WARNING, "CloudAccountTypeSummary is null for %s", endpointType.name());
                this.host.log(Level.WARNING, "Contents of cloudAccountSummaryViewState.typeSummary");
                for (String type : cloudAccountSummaryViewState.typeSummary.keySet()) {
                    CloudAccountTypeSummary tempSummary = cloudAccountSummaryViewState.typeSummary.get(type);
                    this.host.log("Type [%s] Count [%d]", type, tempSummary.count);
                }
            }
            assertNotNull(summary);
            assertEquals(zero, summary.count);
        }
    }

    @Test
    public void testGetWithOnlyOldVsphereEndpoint() throws Throwable {
        String endpointLink = this.accountTestHelper.createMockVsphereEndpointOldApi(this.host, this.peerUri, ORG_1_ID,
                "vc-local-test-endpoint-1", "root", "password",
                "10.112.107.120", "privateCloud1",
                "vc-dc-name", "vc-dc-id");

        URI getAllEndpoints = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK);
        ServiceDocumentQueryResult result = this.host.getServiceState(null, ServiceDocumentQueryResult.class, getAllEndpoints);
        this.accountTestHelper.verifyEndpointsPage(Arrays.asList(endpointLink), result);

    }

    @Test
    public void testCustomPropertiesCRUD() {
        String endpointName = "aws-endpoint-1";
        String s3BucketName = "bucket1";
        String updatedS3BucketName = "updated1";
        String awsEndpointLink = this.accountTestHelper.createMockAwsEndpoint(
                endpointName, "foo", "bar", null, null, null);

        // add s3 bucket name
        CloudAccountPatchRequest updateRequest = new CloudAccountPatchRequest();
        updateRequest.customPropertyUpdates = new HashMap<>();
        CollectionStringFieldUpdate addBucket = new CollectionStringFieldUpdate();
        addBucket.action = ADD;
        addBucket.value = s3BucketName;
        updateRequest.customPropertyUpdates.put(
                EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET, addBucket);
        updateRequest.isMock = true;
        URI endpointURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(awsEndpointLink));
        Operation response = this.host.waitForResponse(Operation.createPatch(endpointURI)
                .setBody(updateRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        endpointURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(awsEndpointLink));
        response = this.host.waitForResponse(Operation.createPatch(endpointURI)
                .setBody(updateRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        CloudAccountViewState viewState = response.getBody(CloudAccountViewState.class);
        assertThat(viewState.name, equalTo(endpointName));
        assertNotNull(viewState.customProperties
                .get(EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET));
        assertThat(viewState.customProperties.get(
                EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET),
                equalTo(s3BucketName));

        // update s3 bucket name
        updateRequest = new CloudAccountPatchRequest();
        updateRequest.customPropertyUpdates = new HashMap<>();
        CollectionStringFieldUpdate updateBucket = new CollectionStringFieldUpdate();
        updateBucket.action = ADD;
        updateBucket.value = updatedS3BucketName;
        updateRequest.customPropertyUpdates.put(
                EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET, updateBucket);
        updateRequest.isMock = true;
        endpointURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(awsEndpointLink));
        response = this.host.waitForResponse(Operation.createPatch(endpointURI)
                .setBody(updateRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        endpointURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(awsEndpointLink));
        response = this.host.waitForResponse(Operation.createPatch(endpointURI)
                .setBody(updateRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        viewState = response.getBody(CloudAccountViewState.class);
        assertThat(viewState.name, equalTo(endpointName));
        assertNotNull(viewState.customProperties
                .get(EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET));
        assertThat(viewState.customProperties.get(
                EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET),
                equalTo(updatedS3BucketName));

        // verify that ADD without a value is ignored
        updateRequest = new CloudAccountPatchRequest();
        updateRequest.customPropertyUpdates = new HashMap<>();
        CollectionStringFieldUpdate noOp = new CollectionStringFieldUpdate();
        noOp.action = ADD;
        noOp.value = null;
        updateRequest.customPropertyUpdates.put(
                EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET, noOp);
        updateRequest.isMock = true;
        endpointURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(awsEndpointLink));
        response = this.host.waitForResponse(Operation.createPatch(endpointURI)
                .setBody(updateRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        endpointURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(awsEndpointLink));
        response = this.host.waitForResponse(Operation.createPatch(endpointURI)
                .setBody(updateRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        viewState = response.getBody(CloudAccountViewState.class);
        assertThat(viewState.name, equalTo(endpointName));
        assertNotNull(viewState.customProperties
                .get(EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET));
        assertThat(viewState.customProperties.get(
                EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET),
                equalTo(updatedS3BucketName));

        // remove s3 bucket name property with value
        updateRequest = new CloudAccountPatchRequest();
        updateRequest.customPropertyUpdates = new HashMap<>();
        CollectionStringFieldUpdate removeBucket = new CollectionStringFieldUpdate();
        removeBucket.action = UpdateAction.REMOVE;
        removeBucket.value = "noOp";
        updateRequest.customPropertyUpdates.put(
                EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET, removeBucket);
        updateRequest.isMock = true;
        endpointURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(awsEndpointLink));
        response = this.host.waitForResponse(Operation.createPatch(endpointURI)
                .setBody(updateRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        endpointURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(awsEndpointLink));
        response = this.host.waitForResponse(Operation.createPatch(endpointURI)
                .setBody(updateRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        viewState = response.getBody(CloudAccountViewState.class);
        assertThat(viewState.name, equalTo(endpointName));
        assertNull(viewState.customProperties
                .get(EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET));

        // remove s3 bucket name property without a value
        updateRequest = new CloudAccountPatchRequest();
        updateRequest.customPropertyUpdates = new HashMap<>();
        removeBucket = new CollectionStringFieldUpdate();
        removeBucket.action = UpdateAction.REMOVE;
        updateRequest.customPropertyUpdates.put(
                EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET, removeBucket);
        updateRequest.isMock = true;
        endpointURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(awsEndpointLink));
        response = this.host.waitForResponse(Operation.createPatch(endpointURI)
                .setBody(updateRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        endpointURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(awsEndpointLink));
        response = this.host.waitForResponse(Operation.createPatch(endpointURI)
                .setBody(updateRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        viewState = response.getBody(CloudAccountViewState.class);
        assertThat(viewState.name, equalTo(endpointName));
        assertNull(viewState.customProperties
                .get(EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET));

        // negative test case
        // property key with null update action
        updateRequest = new CloudAccountPatchRequest();
        updateRequest.customPropertyUpdates = new HashMap<>();
        CollectionStringFieldUpdate invalid = new CollectionStringFieldUpdate();
        invalid.action = null;
        invalid.value = "foo";
        updateRequest.customPropertyUpdates.put(
                EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET, invalid);
        updateRequest.isMock = true;
        endpointURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(awsEndpointLink));
        response = this.host.waitForResponse(Operation.createPatch(endpointURI)
                .setBody(updateRequest));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        assertEquals(CloudAccountErrorCode.ENDPOINT_INVALID_UPDATE_ACTION.getErrorCode(),
                Integer.parseInt(response.getBody(ServiceErrorResponse.class).messageId));
    }

    @Test
    public void testCreateWithServiceTag() {
        Set<String> services = new HashSet<>();
        services.add("service-1");
        services.add("service-2");

        String awsEndpointLink1 = this.accountTestHelper.createMockAwsEndpoint("aws-endpoint-1", "foo", "bar", null,  null, null);
        String awsEndpointLink2 = this.accountTestHelper.createMockAwsEndpoint("aws-endpoint-2", "foo", "bar", services, null, null);

        URI getAllEndpoints = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK);
        ServiceDocumentQueryResult result = this.host.getServiceState(null, ServiceDocumentQueryResult.class, getAllEndpoints);
        this.accountTestHelper.verifyEndpointsPage(Arrays.asList(awsEndpointLink1, awsEndpointLink2), result);
    }

    @Test
    public void testAddServiceTagWithMockMode() {

        String awsEndpointLink1 = this.accountTestHelper.createMockAwsEndpoint("aws-endpoint-1", "foo", "bar", null, null, null);

        CloudAccountPatchRequest updateRequest = new CloudAccountPatchRequest();
        updateRequest.serviceUpdate = new CollectionStringFieldUpdate();
        updateRequest.serviceUpdate.action = ADD;
        updateRequest.serviceUpdate.value = "service-1";
        updateRequest.isMock = true;

        URI endpointURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(awsEndpointLink1));
        Operation response = this.host.waitForResponse(Operation.createPatch(endpointURI).setBody(updateRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        URI getAllEndpoints = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK);
        ServiceDocumentQueryResult result = this.host.getServiceState(null, ServiceDocumentQueryResult.class, getAllEndpoints);
        this.accountTestHelper.verifyEndpointsPage(Arrays.asList(awsEndpointLink1), result);
    }

    @Test
    public void testAddServiceTagWithoutMockMode() {

        String awsEndpointLink1 = this.accountTestHelper.createMockAwsEndpoint("aws-endpoint-1", "foo",
                "bar", null, null, null);

        CloudAccountPatchRequest updateRequest = new CloudAccountPatchRequest();
        updateRequest.serviceUpdate = new CollectionStringFieldUpdate();
        updateRequest.serviceUpdate.action = ADD;
        updateRequest.serviceUpdate.value = "service-1";
        updateRequest.isMock = false;

        URI endpointURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(awsEndpointLink1));
        Operation response = this.host.waitForResponse(Operation.createPatch(endpointURI).setBody(updateRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        URI getAllEndpoints = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK);
        ServiceDocumentQueryResult result = this.host.getServiceState(null, ServiceDocumentQueryResult.class, getAllEndpoints);
        this.accountTestHelper.verifyEndpointsPage(Arrays.asList(awsEndpointLink1), result);
    }

    @Test
    public void testRemoveServiceTagWithMockMode() {
        Set<String> services = new HashSet<>();
        services.add("service-A");
        services.add("service-2");

        String awsEndpointLink1 = this.accountTestHelper.createMockAwsEndpoint("aws-endpoint-1", "foo",
                "bar", services, null, null);
        CloudAccountPatchRequest updateRequest = new CloudAccountPatchRequest();
        updateRequest.serviceUpdate = new CollectionStringFieldUpdate();
        updateRequest.serviceUpdate.action = UpdateAction.REMOVE;
        updateRequest.serviceUpdate.value = "service-A";
        updateRequest.isMock = true;

        URI endpointURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(awsEndpointLink1));
        Operation response = this.host.waitForResponse(Operation.createPatch(endpointURI).setBody(updateRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        URI getAllEndpoints = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK);
        ServiceDocumentQueryResult result = this.host.getServiceState(null, ServiceDocumentQueryResult.class, getAllEndpoints);
        this.accountTestHelper.verifyEndpointsPage(Arrays.asList(awsEndpointLink1), result);
    }

    @Test
    public void testRemoveServiceTagWithoutMockMode() {
        Set<String> services = new HashSet<>();
        services.add("service-A");
        services.add("service-2");

        String awsEndpointLink1 = this.accountTestHelper.createMockAwsEndpoint("aws-endpoint-1", "foo",
                "bar", services, null, null);
        CloudAccountPatchRequest updateRequest = new CloudAccountPatchRequest();
        updateRequest.serviceUpdate = new CollectionStringFieldUpdate();
        updateRequest.serviceUpdate.action = UpdateAction.REMOVE;
        updateRequest.serviceUpdate.value = "service-A";
        updateRequest.isMock = false;

        URI endpointURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(awsEndpointLink1));
        Operation response = this.host.waitForResponse(Operation.createPatch(endpointURI).setBody(updateRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        URI getAllEndpoints = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK);
        ServiceDocumentQueryResult result = this.host.getServiceState(null, ServiceDocumentQueryResult.class, getAllEndpoints);
        this.accountTestHelper.verifyEndpointsPage(Arrays.asList(awsEndpointLink1), result);
    }

    @Test
    public void testRemoveNonExistentServiceTag() {
        String awsEndpointLink1 = this.accountTestHelper
                .createMockAwsEndpoint("aws-endpoint-1",
                "foo", "bar", null, null, null);

        CloudAccountPatchRequest updateRequest = new CloudAccountPatchRequest();
        updateRequest.serviceUpdate = new CollectionStringFieldUpdate();
        updateRequest.serviceUpdate.action = UpdateAction.REMOVE;
        updateRequest.serviceUpdate.value = "service-1";
        updateRequest.isMock = true;

        URI endpointURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(awsEndpointLink1));
        Operation response = this.host.waitForResponse(Operation.createPatch(endpointURI).setBody(updateRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        URI getAllEndpoints = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK);
        ServiceDocumentQueryResult result = this.host.getServiceState(null, ServiceDocumentQueryResult.class, getAllEndpoints);
        this.accountTestHelper.verifyEndpointsPage(Arrays.asList(awsEndpointLink1), result);
    }

    @Test
    public void testGetFilters() {
        URI getFiltersURI = UriUtils.buildUri(
                this.peerUri, CloudAccountApiService.CLOUD_ACCOUNT_PROPERTIES_PATH_TEMPLATE);
        Operation response = this.host.waitForResponse(Operation.createGet(getFiltersURI));
        assertNotNull(response);
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        ResourceProperties body = response.getBody(ResourceProperties.class);
        assertNotNull(body);
        assertEquals(DEFAULT_CLOUD_ACCOUNT_PROPERTIES_LIST_SIZE, body.results.size());
        assertEquals(DEFAULT_CLOUD_ACCOUNT_PROPERTIES_LIST_SIZE, body.documentCount);
        int filterCount = 0;
        int sortCount = 0;
        for (ResourceProperty property : body.results) {
            if (property.isFilterable) {
                filterCount++;
            }
            if (property.isSortable) {
                sortCount++;
            }
        }
        assertEquals(DEFAULT_CLOUD_ACCOUNT_FILTER_LIST_SIZE, filterCount);
        assertEquals(DEFAULT_CLOUD_ACCOUNT_SORT_LIST_SIZE, sortCount);
    }

    @Test
    public void testAddCloudAccountWithInvalidOwner() throws Throwable {
        String userId3 = "user3@example.com";
        String userId3Password = "passwordforuser3";

        this.accountTestHelper.createUser(userId3, userId3Password);
        this.accountTestHelper.createSecondOrg();
        this.accountTestHelper.addUserToOrgOrProject(userId3, userId3, userId3Password, this.accountTestHelper.org2Link, false);

        CloudAccountCreateRequest cloudAccountCreateRequest = new CloudAccountCreateRequest();
        cloudAccountCreateRequest.name = "test-endpoint";
        cloudAccountCreateRequest.description = ACCOUNT_DESCRIPTION;
        cloudAccountCreateRequest.orgLink = this.orgLink;
        cloudAccountCreateRequest.type = EndpointType.aws.name();

        cloudAccountCreateRequest.credentials = createAwsCredentials("foo", "bar");
        cloudAccountCreateRequest.isMock = true;
        cloudAccountCreateRequest.owners = Collections.singleton(userId3);

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK))
                .setBody(cloudAccountCreateRequest));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, INVALID_ENDPOINT_OWNER);
    }

    @Test
    public void testCloudAccountWithOwners() throws Throwable {
        String tenantLink = this.accountTestHelper.orgLink;
        String userId3 = "user3@example.com";
        String userId3Password = "passwordforuser3";

        String userId4 = "user4@example.com";
        String userId4Password = "passwordforuser4";

        this.accountTestHelper.createUser(USER_2_ID, USER_2_PASSWORD);
        this.accountTestHelper.addUserToOrgOrProject(USER_2_ID, USER_2_ID, USER_2_PASSWORD, this.orgLink, false);

        this.accountTestHelper.createUser(userId3, userId3Password);
        this.accountTestHelper.addUserToOrgOrProject(userId3, userId3, userId3Password, this.orgLink, true);

        this.accountTestHelper.createUser(userId4, userId4Password);
        this.accountTestHelper.addUserToOrgOrProject(userId4, userId4, userId4Password, this.orgLink, false);

        Set<String> delegatedOwners = new HashSet<>();
        delegatedOwners.add(USER_1_ID);
        delegatedOwners.add(USER_2_ID);

        String awsEndpointLink = this.accountTestHelper.createMockAwsEndpoint("aws-endpoint-1", "foo", "bar", null, delegatedOwners, null);

        URI getAllEndpoints = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK);
        ServiceDocumentQueryResult result = this.host.getServiceState(null, ServiceDocumentQueryResult.class, getAllEndpoints);
        this.accountTestHelper.verifyEndpointsPage(Collections.singletonList(awsEndpointLink), result);

        this.host.setSystemAuthorizationContext();

        Operation endpointFactory = this.host.waitForResponse(Operation.createGet(UriUtils
                .buildUri(this.peerUri, EndpointService.FACTORY_LINK)).setReferer(this.host.getUri()));

        result = endpointFactory.getBody(ServiceDocumentQueryResult.class);
        String endpointLink = result.documentLinks.get(0);

        Operation user1UserState = this.host.waitForResponse(Operation.createGet(UriUtils
                .buildUri(this.peerUri, UriUtils.buildUriPath(UserService.FACTORY_LINK,
                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_1_ID)))).setReferer(this.host.getUri()));

        Operation user2UserState = this.host.waitForResponse(Operation.createGet(UriUtils
                .buildUri(this.peerUri, UriUtils.buildUriPath(UserService.FACTORY_LINK,
                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_2_ID)))).setReferer(this.host.getUri()));

        Operation user3UserState = this.host.waitForResponse(Operation.createGet(UriUtils
                .buildUri(this.peerUri, UriUtils.buildUriPath(UserService.FACTORY_LINK,
                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(userId3)))).setReferer(this.host.getUri()));

        Operation user4UserState = this.host.waitForResponse(Operation.createGet(UriUtils
                .buildUri(this.peerUri, UriUtils.buildUriPath(UserService.FACTORY_LINK,
                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(userId4)))).setReferer(this.host.getUri()));

        UserState user1 = user1UserState.getBody(UserState.class);
        UserState user2 = user2UserState.getBody(UserState.class);
        UserState user3 = user3UserState.getBody(UserState.class);
        UserState user4 = user4UserState.getBody(UserState.class);

        String orgId = UriUtils.getLastPathSegment(this.orgLink);
        String orgAdminUserGroupLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                OnboardingUtils.buildAuthzArtifactLink(orgId, true));
        String orgUserUserGroupLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                OnboardingUtils.buildAuthzArtifactLink(orgId, false));

        // User 1 - Org Owner and a delegated owner
        assertTrue(user1.userGroupLinks.contains(orgAdminUserGroupLink));
        assertTrue(user1.userGroupLinks.contains(EndpointUtils
                .buildEndpointAuthzArtifactSelfLink(UserGroupService.FACTORY_LINK, endpointLink, tenantLink)));

        // User 2 - Org User and a delegated owner
        assertFalse(user2.userGroupLinks.contains(orgAdminUserGroupLink));
        assertTrue(user2.userGroupLinks.contains(EndpointUtils
                .buildEndpointAuthzArtifactSelfLink(UserGroupService.FACTORY_LINK, endpointLink, tenantLink)));

        // User 3 - Org Owner
        assertTrue(user3.userGroupLinks.contains(orgAdminUserGroupLink));
        assertFalse(user3.userGroupLinks.contains(EndpointUtils
                .buildEndpointAuthzArtifactSelfLink(UserGroupService.FACTORY_LINK, endpointLink, tenantLink)));

        // User 4 - Org User
        assertTrue(user4.userGroupLinks.contains(orgUserUserGroupLink));
        assertFalse(user4.userGroupLinks.contains(orgAdminUserGroupLink));
        assertFalse(user4.userGroupLinks.contains(EndpointUtils
                .buildEndpointAuthzArtifactSelfLink(UserGroupService.FACTORY_LINK,endpointLink, tenantLink)));

        this.host.resetAuthorizationContext();

        Set<CollectionStringFieldUpdate> ownersUpdate = new HashSet<>();
        ownersUpdate.add(CollectionStringFieldUpdate.create(ADD, userId3));
        ownersUpdate.add(CollectionStringFieldUpdate.create(UpdateAction.REMOVE, USER_2_ID));

        CloudAccountPatchRequest updateRequest = new CloudAccountPatchRequest();
        updateRequest.ownerUpdates = ownersUpdate;

        URI endpointURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(endpointLink));
        Operation response = this.host.waitForResponse(Operation.createPatch(endpointURI).setBody(updateRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        this.host.setSystemAuthorizationContext();

        // Validate owner update
        Operation getUser2 = this.host.waitForResponse(Operation.createGet(UriUtils
                .buildUri(this.peerUri, UriUtils.buildUriPath(UserService.FACTORY_LINK,
                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_2_ID)))));
        Operation getUser3 = this.host.waitForResponse(Operation.createGet(UriUtils
                .buildUri(this.peerUri, UriUtils.buildUriPath(UserService.FACTORY_LINK,
                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(userId3)))));

        user2 = getUser2.getBody(UserState.class);
        user3 = getUser3.getBody(UserState.class);

        assertFalse(user2.userGroupLinks.contains(EndpointUtils.buildEndpointAuthzArtifactSelfLink(
                UserGroupService.FACTORY_LINK, endpointLink, tenantLink)));
        assertTrue(user3.userGroupLinks.contains(EndpointUtils.buildEndpointAuthzArtifactSelfLink(
                UserGroupService.FACTORY_LINK, endpointLink, tenantLink)));

        this.host.resetAuthorizationContext();
    }

    @Test
    public void testCloudAccountUpdateWithInvalidOwners() throws Throwable {
        this.accountTestHelper.createSecondOrg();
        this.accountTestHelper.createUser(USER_2_ID, USER_2_PASSWORD);
        this.accountTestHelper.addUserToOrgOrProject(USER_2_ID, USER_2_ID, USER_2_PASSWORD, this.accountTestHelper.org2Link, true);

        this.host.assumeIdentity(
                UriUtils.buildUriPath(UserService.FACTORY_LINK,
                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_1_ID)), null);

        String awsEndpointLink = this.accountTestHelper.createMockAwsEndpoint("aws-endpoint-1", "foo", "bar", null, null, null);
        Set<CollectionStringFieldUpdate> ownersUpdate = new HashSet<>();
        ownersUpdate.add(CollectionStringFieldUpdate.create(ADD, USER_2_ID));

        CloudAccountPatchRequest updateRequest = new CloudAccountPatchRequest();
        updateRequest.ownerUpdates = ownersUpdate;

        URI endpointURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(awsEndpointLink));
        Operation response = this.host.waitForResponse(Operation.createPatch(endpointURI).setBody(updateRequest));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, INVALID_USER_FOR_OWNERS_UPDATE);
    }

    @Test
    public void testCloudAccountUpdateWithNonExistentOwners() throws Throwable {
        String awsEndpointLink = this.accountTestHelper.createMockAwsEndpoint("aws-endpoint-1", "foo", "bar", null, null, null);
        Set<CollectionStringFieldUpdate> ownersUpdate = new HashSet<>();
        ownersUpdate.add(CollectionStringFieldUpdate.create(ADD, "dummy-user@example.com"));

        CloudAccountPatchRequest updateRequest = new CloudAccountPatchRequest();
        updateRequest.ownerUpdates = ownersUpdate;

        URI endpointURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(awsEndpointLink));
        Operation response = this.host.waitForResponse(Operation.createPatch(endpointURI).setBody(updateRequest));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, INVALID_USER_FOR_OWNERS_UPDATE);

    }

    @Test
    public void testGetOwners() throws Throwable {
        OnBoardingTestUtils.setupUser(this.host, this.accountTestHelper.peerUri,
                OnBoardingTestUtils.createUserData("user3@org-1.com", "passwordforuser3"));
        OnBoardingTestUtils
                .addUserToOrgOrProject(this.host, this.accountTestHelper.peerUri, this.accountTestHelper.orgLink,
                        "user3@org-1.com", USER_1_ID, USER_1_PASSWORD);
        String userLink = UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256("user3@org-1.com"));

        String endpointLink = this.accountTestHelper
                .createMockAwsEndpoint("owners-endpoint", "foo", "bar", null, null, null);

        // Custom patch the user3 with ownership of the cloud account
        String ownerGroupLink = EndpointUtils
                .buildEndpointAuthzArtifactSelfLink(UserGroupService.FACTORY_LINK, endpointLink, this.orgLink);
        Map<String, Collection<Object>> itemsToAdd = Collections
                .singletonMap(UserState.FIELD_NAME_USER_GROUP_LINKS,
                        new HashSet<>(Collections.singleton(ownerGroupLink)));

        ServiceStateCollectionUpdateRequest groupLinksUpdateRequest = ServiceStateCollectionUpdateRequest
                .create(itemsToAdd, null);
        URI uri = UriUtils.buildUri(this.peerUri, userLink);
        this.host.setSystemAuthorizationContext();
        this.host.sendAndWaitExpectSuccess(Operation.createPatch(uri).setBody(groupLinksUpdateRequest));
        this.host.resetSystemAuthorizationContext();

        String ownersLink = UriUtils.buildUriPath(CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(endpointLink), "owners");
        uri = UriUtils.buildUri(this.peerUri, ownersLink);

        // Test limit parameter
        URI uriLimitOne = UriUtils.extendUriWithQuery(uri, URI_PARAM_ODATA_LIMIT, "1");
        Operation response = this.host.waitForResponse(Operation.createGet(uriLimitOne));
        assertNotNull(response);
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        ServiceDocumentQueryResult results = response.getBody(ServiceDocumentQueryResult.class);
        assertNotNull(results);
        assertEquals(1, results.documentCount.longValue());
        assertNotNull(results.documents);
        assertNotNull(results.nextPageLink);
        UserViewState userViewState = Utils
                .fromJson(results.documents.values().iterator().next(), UserViewState.class);
        assertEquals(USER_1_ID, userViewState.email);

        // Test with default limit
        response = this.host.waitForResponse(Operation.createGet(uri));
        assertNotNull(response);
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        results = response.getBody(ServiceDocumentQueryResult.class);
        assertNotNull(results);
        assertEquals(2, results.documentCount.longValue());
        assertNotNull(results.documents);

        // Test with invalid endpoint id
        ownersLink = UriUtils.buildUriPath(CloudAccountApiService.SELF_LINK, "foo", "owners");
        uri = UriUtils.buildUri(this.peerUri, ownersLink);
        response = this.host.waitForResponse(Operation.createGet(uri));
        assertNotNull(response);
        assertEquals(Operation.STATUS_CODE_NOT_FOUND, response.getStatusCode());
    }

    private void createCloudAccountsForSummaryTest(String service) throws Throwable {
        int numAWSAccounts = 3;
        Set<String> services = null;
        if (service != null) {
            services = new HashSet<>();
            services.add(service);
        }

        this.accountTestHelper.createMockAwsEndpoints(numAWSAccounts, services, null, null);
        this.accountTestHelper.createMockAzureEAEndpoint("my-azure-ea-1", "foo-1", "bar-1");
        this.accountTestHelper.createMockAzureEAEndpoint("my-azure-ea-2", "foo-2", "bar-2");

        this.accountTestHelper.createMockVsphereEndpoint(this.host, this.peerUri, ORG_1_ID,
                "vc-local-test-endpoint", "root", "password",
                "10.112.107.120", "privateCloud1",
                "vc-dc-name", "vc-dc-id");
    }

    @Test
    public void testCreateAccountWithTags() throws Throwable {
        Set<TagViewState> tags = new HashSet<>();
        TagViewState tagA = new TagViewState("dummyKey", "dummyVal");
        TagViewState tagB = new TagViewState("dummyKeyB", "dummyValB");
        Collections.addAll(tags, tagA, tagB);

        String awsEndpointLink1 = this.accountTestHelper.createMockAwsEndpoint("aws-endpoint-1", "foo",
                "bar", null, null, null);
        String awsEndpointLink2 = this.accountTestHelper.createMockAwsEndpoint("aws-endpoint-2", "foo",
                "bar", null, null, tags);

        URI getAllEndpoints = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK);
        ServiceDocumentQueryResult result = this.host.getServiceState(null, ServiceDocumentQueryResult.class, getAllEndpoints);
        this.accountTestHelper.verifyEndpointsPage(Arrays.asList(awsEndpointLink1, awsEndpointLink2), result);
    }

    @Test
    public void testAddRemoveTagsToAccount() throws Throwable {
        String awsEndpointLink1 = this.accountTestHelper.createMockAwsEndpoint("aws-endpoint-1",
                "foo", "bar", null, null, null);

        CloudAccountPatchRequest updateRequest = new CloudAccountPatchRequest();
        updateRequest.isMock = true;
        updateRequest.tagUpdates = new HashSet<>();
        TagFieldUpdate tagUpdate =  new TagFieldUpdate();
        tagUpdate.action = ADD;
        tagUpdate.value = new TagViewState("key1", "value1");
        updateRequest.tagUpdates.add(tagUpdate);

        URI endpointURI = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(awsEndpointLink1));
        Operation response = this.host.waitForResponse(Operation.createPatch(endpointURI).setBody(updateRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        URI getAllEndpoints = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK);
        ServiceDocumentQueryResult result = this.host.getServiceState(null,
                ServiceDocumentQueryResult.class, getAllEndpoints);
        this.accountTestHelper.verifyEndpointsPage(Arrays.asList(awsEndpointLink1), result);

        // remove tags from cloud account
        updateRequest = new CloudAccountPatchRequest();
        updateRequest.tagUpdates = new HashSet<>();
        tagUpdate =  new TagFieldUpdate();
        tagUpdate.action = UpdateAction.REMOVE;
        tagUpdate.value = new TagViewState("key1", "value1");
        updateRequest.tagUpdates.add(tagUpdate);
        updateRequest.isMock = true;

        response = this.host.waitForResponse(Operation.createPatch(endpointURI).setBody(updateRequest));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        getAllEndpoints = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK);
        result = this.host.getServiceState(null, ServiceDocumentQueryResult.class,
                getAllEndpoints);
        this.accountTestHelper.verifyEndpointsPage(Arrays.asList(awsEndpointLink1), result);
    }
}
