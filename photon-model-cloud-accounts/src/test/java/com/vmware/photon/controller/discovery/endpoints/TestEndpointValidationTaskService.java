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

package com.vmware.photon.controller.discovery.endpoints;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.CREDENTIALS_REQUIRED;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_ALREADY_EXISTS;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_TYPE_REQUIRED;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_WITH_S3BUCKETNAME_ALREADY_EXISTS;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.INVALID_ENDPOINT_OWNER;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.S3_BUCKET_PERMISSIONS_ERROR;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.VSPHERE_DCID_REQUIRED;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.VSPHERE_HOSTNAME_REQUIRED;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET;
import static com.vmware.photon.controller.discovery.endpoints.EndpointValidationTaskService.buildAuthCredentialQueryTask;
import static com.vmware.photon.controller.discovery.endpoints.TestEndpointCreationTaskService.createMockAwsEndpoint;
import static com.vmware.photon.controller.discovery.endpoints.TestEndpointCreationTaskService.createMockAwsEndpointArn;
import static com.vmware.photon.controller.discovery.endpoints.TestEndpointUtils.createAwsCredentials;
import static com.vmware.photon.controller.discovery.vsphere.VsphereOnPremEndpointAdapterService.HOST_NAME_KEY;
import static com.vmware.photon.controller.model.UriPaths.CLOUD_ACCOUNT_API_SERVICE;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.ARN_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEYID_KEY;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType.aws;
import static com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState.FIELD_NAME_CUSTOM_PROPERTIES;
import static com.vmware.xenon.services.common.QueryTask.QuerySpecification.buildCompositeFieldName;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.discovery.common.CloudAccountConstants;
import com.vmware.photon.controller.discovery.common.ErrorCode;
import com.vmware.photon.controller.discovery.common.PhotonControllerCloudAccountUtils;
import com.vmware.photon.controller.discovery.common.SymphonyCommonTestUtils;
import com.vmware.photon.controller.discovery.common.services.OrganizationService;
import com.vmware.photon.controller.discovery.common.services.UserService;
import com.vmware.photon.controller.discovery.common.utils.ErrorUtil;
import com.vmware.photon.controller.discovery.endpoints.EndpointValidationTaskService.EndpointValidationTaskState;
import com.vmware.photon.controller.discovery.endpoints.EndpointValidationTaskService.SubStage;
import com.vmware.photon.controller.discovery.onboarding.OnBoardingTestUtils;
import com.vmware.photon.controller.discovery.onboarding.OnboardingServices;
import com.vmware.photon.controller.discovery.onboarding.OnboardingUtils;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSAdapters;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.xenon.common.BasicTestCase;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.QueryTask;

public class TestEndpointValidationTaskService extends BasicTestCase {
    public int numNodes = 3;

    private static final String USER_1_ID = "user1@org-1.com";
    private static final String USER_1_PASSWORD = "passwordforuser1";

    private static final String ORG_1_ID = "org-1";
    private static final String PROJECT_1_ID = OnboardingUtils.getDefaultProjectName();

    // AWS access key
    private String accessKey = "accessKey";
    // AWS secret key
    private String secretKey = "secretKey";
    // AWS S3 bucket name
    private String s3bucketName = "testS3Bucket";
    private boolean isMock = true;

    private URI peerUri;
    private String orgLink;
    private String projectLink;

    @Override
    public void beforeHostStart(VerificationHost h) {
        h.setAuthorizationEnabled(true);
    }

    @Before
    public void setUp() throws Throwable {
        //set pageSize to 5
        System.setProperty(CloudAccountConstants.PROPERTY_NAME_QUERY_RESULT_LIMIT, String.valueOf(5));
        CommandLineArgumentParser.parseFromProperties(this);
        host.setUpPeerHosts(this.numNodes);
        host.joinNodesAndVerifyConvergence(this.numNodes, this.numNodes, true);
        host.setNodeGroupQuorum(this.numNodes);

        // start provisioning services on all the hosts
        host.setSystemAuthorizationContext();
        for (VerificationHost h : host.getInProcessHostMap().values()) {
            PhotonModelServices.startServices(h);
            PhotonModelTaskServices.startServices(h);
            AWSAdapters.startServices(h);
            PhotonModelAdaptersRegistryAdapters.startServices(h);
        }
        host.resetSystemAuthorizationContext();

        for (VerificationHost h : this.host.getInProcessHostMap().values()) {
            OnBoardingTestUtils.startCommonServices(h);
            h.setSystemAuthorizationContext();
            OnboardingServices.startServices(h, h::addPrivilegedService);
            EndpointServices.startServices(h, h::addPrivilegedService);
            h.resetAuthorizationContext();
        }

        OnBoardingTestUtils.waitForCommonServicesAvailability(host, host.getPeerHostUri());

        List<String> factories = new ArrayList<>();
        factories.add(EndpointValidationTaskService.FACTORY_LINK);
        factories.add(AwsEndpointS3ValidationTaskService.FACTORY_LINK);
        SymphonyCommonTestUtils
                .waitForFactoryAvailability(this.host, this.host.getPeerHostUri(), factories);

        this.peerUri = this.host.getPeerHostUri();

        OnBoardingTestUtils.setupUser(this.host, this.peerUri,
                OnBoardingTestUtils.createUserData(USER_1_ID, USER_1_PASSWORD));
        OnBoardingTestUtils
                .setupOrganization(this.host, this.peerUri, ORG_1_ID, USER_1_ID, USER_1_PASSWORD);
        this.projectLink = OnBoardingTestUtils.setupProject(this.host, this.peerUri, PROJECT_1_ID,
                ORG_1_ID, USER_1_ID, USER_1_PASSWORD);

        this.orgLink = UriUtils.buildUriPath(OrganizationService.FACTORY_LINK,
                Utils.computeHash(ORG_1_ID));
    }

    @After
    public void tearDown() throws Throwable {
        if (this.host == null) {
            return;
        }
        this.host.tearDownInProcessPeers();
        this.host.toggleNegativeTestMode(false);
        this.host.tearDown();
    }

    @Test
    public void testInvalidArgs() {
        EndpointValidationTaskState task = new EndpointValidationTaskState();
        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointValidationTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, ENDPOINT_TYPE_REQUIRED);

        task = new EndpointValidationTaskState();
        task.type = aws.name();
        response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointValidationTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, CREDENTIALS_REQUIRED);

        task = new EndpointValidationTaskState();
        task.type = EndpointType.vsphere.name();
        task.credentials = TestEndpointUtils.createVSphereCredentials("foo", "bar");
        response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointValidationTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, VSPHERE_HOSTNAME_REQUIRED);
    }

    @Test
    public void testInvalidVsphereArgs() {
        EndpointValidationTaskState task = new EndpointValidationTaskState();
        task.type = EndpointType.vsphere.name();
        task.credentials = TestEndpointUtils.createVSphereCredentials("foo", "bar");
        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointValidationTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, VSPHERE_HOSTNAME_REQUIRED);


        task = new EndpointValidationTaskState();
        task.type = EndpointType.vsphere.name();
        task.credentials = TestEndpointUtils.createVSphereCredentials("foo", "bar");
        task.customProperties = new HashMap<>();
        task.customProperties.put(HOST_NAME_KEY, "test");

        response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointValidationTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, VSPHERE_DCID_REQUIRED);
    }

    @Test
    public void testEndpointValidation() throws Throwable {
        EndpointValidationTaskState validateTask = new EndpointValidationTaskState();
        validateTask.type = aws.name();
        validateTask.credentials = createAwsCredentials("bar", "foo");
        validateTask.isMock = true;
        validateTask.taskInfo = TaskState.createDirect();
        validateTask.owners = Collections.singleton(USER_1_ID);

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointValidationTaskService.FACTORY_LINK))
                .setBody(validateTask));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        EndpointValidationTaskState body = response.getBody(EndpointValidationTaskState.class);
        assertEquals(SubStage.SUCCESS, body.subStage);
        assertEquals(TaskStage.FINISHED, body.taskInfo.stage);
    }

    @Test
    public void testEndpointValidationInvalidOwners() throws Throwable {
        EndpointValidationTaskState validateTask = new EndpointValidationTaskState();
        validateTask.type = aws.name();
        validateTask.credentials = createAwsCredentials("bar", "foo");
        validateTask.isMock = true;
        validateTask.taskInfo = TaskState.createDirect();
        validateTask.owners = Collections.singleton("invaliduser@vmware.com");

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointValidationTaskService.FACTORY_LINK))
                .setBody(validateTask));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        EndpointValidationTaskState body = response.getBody(EndpointValidationTaskState.class);
        assertEquals(SubStage.ERROR, body.subStage);
        assertEquals(TaskStage.FAILED, body.taskInfo.stage);
        assertEquals(body.taskInfo.failure.message, ErrorUtil.message(INVALID_ENDPOINT_OWNER));
    }

    @Test
    public void testEndpointValidationMissingCredentials() throws Throwable {
        EndpointValidationTaskState validateTask = new EndpointValidationTaskState();
        validateTask.type = aws.name();
        validateTask.credentials = createAwsCredentials(null, null);
        validateTask.isMock = true;
        validateTask.taskInfo = TaskState.createDirect();

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointValidationTaskService.FACTORY_LINK))
                .setBody(validateTask));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, CREDENTIALS_REQUIRED);
    }

    @Test
    public void testValidationReturnsErrorForDuplicateEndpoint() throws Throwable {
        //When you create an endpoint, authCredential state is also created
        EndpointCreationTaskService.EndpointCreationTaskState endpointTaskState =
                createMockAwsEndpoint(this.host, this.peerUri, this.orgLink,
                "my-endpoint", "foo", "bar",
                        null, null, null);

        EndpointValidationTaskState validateTask = new EndpointValidationTaskState();
        validateTask.type = aws.name();
        validateTask.credentials = createAwsCredentials("bar", "foo");
        validateTask.isMock = true;
        validateTask.taskInfo = TaskState.createDirect();

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointValidationTaskService.FACTORY_LINK))
                .setBody(validateTask));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        EndpointValidationTaskState body = response.getBody(EndpointValidationTaskState.class);
        assertEquals(TaskStage.FAILED, body.taskInfo.stage);
        String endpointId = UriUtils.getLastPathSegment(endpointTaskState.endpointLink);
        String expectedCloudAccountLink = UriUtils.buildUriPath(
                CLOUD_ACCOUNT_API_SERVICE, endpointId);
        assertEquals(expectedCloudAccountLink, body.taskInfo.failure.message);
        assertEquals(ENDPOINT_ALREADY_EXISTS.getErrorCode(),
                Integer.parseInt(body.taskInfo.failure.messageId));
    }

    @Test
    public void testValidationReturnsErrorForDuplicateEndpointArn() throws Throwable {
        String arn = "arn:aws:iam::123456789123:role/test-role";
        String externalId = "test-external-id";

        //When you create an endpoint, authCredential state is also created
        EndpointCreationTaskService.EndpointCreationTaskState endpointTaskState =
                createMockAwsEndpointArn(this.host, this.peerUri, this.orgLink,
                        "my-endpoint", arn, externalId, "testBucket", null, null);

        EndpointValidationTaskState validateTask = new EndpointValidationTaskState();
        validateTask.type = aws.name();
        validateTask.credentials = TestEndpointUtils.createAwsCredentialsArn(arn, externalId);
        validateTask.isMock = true;
        validateTask.taskInfo = TaskState.createDirect();

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointValidationTaskService.FACTORY_LINK))
                .setBody(validateTask));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        EndpointValidationTaskState body = response.getBody(EndpointValidationTaskState.class);
        assertEquals(TaskStage.FAILED, body.taskInfo.stage);
        String endpointId = UriUtils.getLastPathSegment(endpointTaskState.endpointLink);
        String expectedCloudAccountLink = UriUtils.buildUriPath(
                CLOUD_ACCOUNT_API_SERVICE, endpointId);
        assertEquals(expectedCloudAccountLink, body.taskInfo.failure.message);
        assertEquals(ENDPOINT_ALREADY_EXISTS.getErrorCode(),
                Integer.parseInt(body.taskInfo.failure.messageId));
    }

    @Test
    public void testValidationReturnsErrorForDuplicateEndpointWithPagination() throws Throwable {
        List<String> endpoints = createEndpoints(25);

        EndpointValidationTaskState validateTask = new EndpointValidationTaskState();
        validateTask.type = aws.name();
        validateTask.credentials = createAwsCredentials("bar", "foo");
        validateTask.isMock = true;
        validateTask.taskInfo = TaskState.createDirect();

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointValidationTaskService.FACTORY_LINK))
                .setBody(validateTask));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        EndpointValidationTaskState body = response.getBody(EndpointValidationTaskState.class);
        assertEquals(TaskStage.FAILED, body.taskInfo.stage);
        assertNotNull(body.taskInfo.failure.message);

        //delete Endpoints
        deleteEndpoints(endpoints);
    }


    @Test
    public void testValidationPassesWhenAuthCredIsPresentButDuplicateEndpointIsAbsent()
            throws Throwable {
        EndpointCreationTaskService.EndpointCreationTaskState createResponse =
                createMockAwsEndpoint(this.host, this.peerUri, this.orgLink,
                        "my-endpoint", "foo", "bar",
                        null, null, null);
        String endpointLink = createResponse.endpointLink;

        EndpointDeletionTaskService.EndpointDeletionTaskState task =
                new EndpointDeletionTaskService.EndpointDeletionTaskState();
        task.endpointLink = endpointLink;
        task.taskInfo = TaskState.createDirect();

        Operation deleteResponse = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointDeletionTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_OK, deleteResponse.getStatusCode());
        EndpointDeletionTaskService.EndpointDeletionTaskState deleteResponseBody = deleteResponse
                .getBody(EndpointDeletionTaskService.EndpointDeletionTaskState.class);
        assertEquals(EndpointDeletionTaskService.SubStage.SUCCESS, deleteResponseBody.subStage);
        assertEquals(TaskStage.FINISHED, deleteResponseBody.taskInfo.stage);

        EndpointValidationTaskState validateTask = new EndpointValidationTaskState();
        validateTask.type = aws.name();
        validateTask.credentials = createAwsCredentials("bar", "foo");
        validateTask.isMock = true;
        validateTask.taskInfo = TaskState.createDirect();

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointValidationTaskService.FACTORY_LINK))
                .setBody(validateTask));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        EndpointValidationTaskState body = response.getBody(EndpointValidationTaskState.class);
        assertEquals(TaskStage.FINISHED, body.taskInfo.stage);
        assertNull(body.taskInfo.failure);
    }


    @Test
    public void testValidationReturnsErrorIfDuplicateS3BucketExists() throws Throwable {
        //When you create an endpoint, authCredential state is also created
        EndpointCreationTaskService.EndpointCreationTaskState endpointTaskState =
                createMockAwsEndpoint(this.host, this.peerUri, this.orgLink,
                        "my-endpoint", "foo", "bar",
                        "testS3Bucket", null, null);

        EndpointValidationTaskState validateTask = new EndpointValidationTaskState();
        validateTask.type = aws.name();
        validateTask.credentials = createAwsCredentials("bar1", "foo1");
        Map<String, String> customProperties = new HashMap<>();
        customProperties.put(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET, "testS3Bucket");
        validateTask.customProperties = customProperties;
        validateTask.isMock = true;
        validateTask.taskInfo = TaskState.createDirect();

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointValidationTaskService.FACTORY_LINK))
                .setBody(validateTask));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        EndpointValidationTaskState body = response.getBody(EndpointValidationTaskState.class);
        assertEquals(TaskStage.FAILED, body.taskInfo.stage);
        String endpointLink = UriUtils.getLastPathSegment(endpointTaskState.endpointLink);
        String expectedCloudAccountLink = UriUtils.buildUriPath(
                CLOUD_ACCOUNT_API_SERVICE, endpointLink);
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, body.taskInfo.failure.statusCode);
        assertEquals(ENDPOINT_WITH_S3BUCKETNAME_ALREADY_EXISTS.getErrorCode(),
                Integer.parseInt(body.taskInfo.failure.messageId));
        assertEquals(expectedCloudAccountLink, body.taskInfo.failure.message);

        // Test the same check with ARN
        validateTask = new EndpointValidationTaskState();
        validateTask.type = aws.name();
        validateTask.credentials = TestEndpointUtils.createAwsCredentialsArn("arn:aws:iam::123456789123:role/test-role");
        validateTask.customProperties = customProperties;
        validateTask.isMock = true;
        validateTask.taskInfo = TaskState.createDirect();

        response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointValidationTaskService.FACTORY_LINK))
                .setBody(validateTask));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        body = response.getBody(EndpointValidationTaskState.class);
        assertEquals(TaskStage.FAILED, body.taskInfo.stage);
        endpointLink = UriUtils.getLastPathSegment(endpointTaskState.endpointLink);
        expectedCloudAccountLink = UriUtils.buildUriPath(
                CLOUD_ACCOUNT_API_SERVICE, endpointLink);
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, body.taskInfo.failure.statusCode);
        assertEquals(ENDPOINT_WITH_S3BUCKETNAME_ALREADY_EXISTS.getErrorCode(),
                Integer.parseInt(body.taskInfo.failure.messageId));
        assertEquals(expectedCloudAccountLink, body.taskInfo.failure.message);
    }

    @Test
    public void testValidationReturnsErrorIfNoAccessPermissionsOnS3Bucket() throws Throwable {
        this.host.assumeIdentity(
                    UriUtils.buildUriPath(UserService.FACTORY_LINK, PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_1_ID)));
        EndpointValidationTaskState validateTask = new EndpointValidationTaskState();
        validateTask.type = aws.name();
        validateTask.credentials = createAwsCredentials(this.accessKey, this.secretKey);
        Map<String, String> customProperties = new HashMap<>();
        customProperties.put(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET, this.s3bucketName);
        validateTask.customProperties = customProperties;
        validateTask.isMock = this.isMock;
        validateTask.taskInfo = TaskState.createDirect();

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointValidationTaskService.FACTORY_LINK))
                .setBody(validateTask));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        if (!this.isMock) {
            EndpointValidationTaskState body = response.getBody(EndpointValidationTaskState.class);
            assertEquals(TaskStage.FAILED, body.taskInfo.stage);
            assertEquals(String.valueOf(S3_BUCKET_PERMISSIONS_ERROR.getErrorCode()), body.taskInfo
                    .failure.messageId);
        }
    }

    @Test
    public void testBuildAuthCredentialQueryTask() {
        EndpointValidationTaskState task = new EndpointValidationTaskState();
        task.type = aws.name();
        task.credentials = TestEndpointUtils.createAwsCredentialsArn("test-arn");

        QueryTask queryTask = null;
        try {
            queryTask = buildAuthCredentialQueryTask(task);
        } catch (IllegalAccessException ignored) {
            fail();
        }
        QueryTask.QueryTerm queryTerm = queryTask.querySpec.query.booleanClauses.get(1).term;
        assertEquals(buildCompositeFieldName(FIELD_NAME_CUSTOM_PROPERTIES, ARN_KEY),
                queryTerm.propertyName);
        assertEquals("test-arn", queryTerm.matchValue);

        task = new EndpointValidationTaskState();
        task.type = aws.name();
        task.credentials = createAwsCredentials("foo", "bar");

        try {
            queryTask = buildAuthCredentialQueryTask(task);
        } catch (IllegalAccessException ignored) {
            fail();
        }

        queryTerm = queryTask.querySpec.query.booleanClauses.get(1).term;
        assertEquals(PRIVATE_KEYID_KEY, queryTerm.propertyName);
        assertEquals("foo", queryTerm.matchValue);

        task = new EndpointValidationTaskState();
        task.type = aws.name();
        task.credentials = createAwsCredentials(null, null);

        try {
            buildAuthCredentialQueryTask(task);
            fail();
        } catch (IllegalAccessException e) {
            assertEquals("Could not retrieve private key from endpoint.", e.getMessage());
        }
    }

    private List<String> createEndpoints(int count) {
        List<String> endpointLinks = new ArrayList<>();

        EndpointService.EndpointState endpointState = new EndpointService.EndpointState();
        endpointState.endpointType = "Amazon Web Services";
        endpointState.id = UUID.randomUUID().toString();
        endpointState.name = endpointState.id;
        endpointState.tenantLinks = Collections.singletonList(this.projectLink);

        for (int i = 0; i < count; i++) {
            EndpointCreationTaskService.EndpointCreationTaskState createResponse =
                    createMockAwsEndpoint(this.host, this.peerUri, this.orgLink,
                            "my-endpoint-" + i, "foo", "bar",
                            null, null, null);
            endpointLinks.add(createResponse.endpointLink);
        }

        return endpointLinks;
    }

    private void deleteEndpoints(List<String> endpointLinks) {
        for (String endpointLink : endpointLinks) {
            EndpointDeletionTaskService.EndpointDeletionTaskState task =
                    new EndpointDeletionTaskService.EndpointDeletionTaskState();
            task.endpointLink = endpointLink;
            task.taskInfo = TaskState.createDirect();

            Operation response = this.host.waitForResponse(Operation.createPost(
                    UriUtils.buildUri(this.peerUri, EndpointDeletionTaskService.FACTORY_LINK))
                    .setBody(task));
            assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        }
    }

    private void verifyError(Operation op, ErrorCode errorCode) {
        ServiceErrorResponse error = op.getBody(ServiceErrorResponse.class);
        assertNotNull(error);
        assertEquals(String.valueOf(errorCode.getErrorCode()), error.messageId);
    }

}
