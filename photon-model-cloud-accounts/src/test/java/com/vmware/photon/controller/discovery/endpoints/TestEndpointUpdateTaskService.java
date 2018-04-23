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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_ALREADY_EXISTS;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_LINK_REQUIRED;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_NAME_REQUIRED;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_NOTHING_TO_UPDATE;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_TAG_NULL_EMPTY;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_WITH_S3BUCKETNAME_ALREADY_EXISTS;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.UpdateAction.ADD;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.UpdateAction.REMOVE;
import static com.vmware.photon.controller.discovery.endpoints.CostUsageReportCreationUtils.checkIfReportAlreadyExists;
import static com.vmware.photon.controller.discovery.endpoints.Credentials.AwsCredential.AuthType.ARN;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_AUTH_TYPE;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BUCKET_PREFIX;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_CI_COSTUSAGE_REPORT_NAME;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_PREFIX_SERVICE_TAG;
import static com.vmware.photon.controller.discovery.endpoints.TestEndpointCreationTaskService.createMockAwsEndpoint;
import static com.vmware.photon.controller.discovery.endpoints.TestEndpointCreationTaskService.createMockAwsEndpointArn;
import static com.vmware.photon.controller.discovery.endpoints.TestEndpointCreationTaskService.createMockAwsEndpointCostUsage;
import static com.vmware.photon.controller.discovery.endpoints.TestEndpointUtils.createAwsCredentials;
import static com.vmware.photon.controller.discovery.endpoints.TestEndpointUtils.createAwsCredentialsArn;
import static com.vmware.photon.controller.model.UriPaths.CLOUD_ACCOUNT_API_SERVICE;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.ARN_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.EXTERNAL_ID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEYID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEY_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.REGION_KEY;

import java.net.URI;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.discovery.common.CloudAccountConstants;
import com.vmware.photon.controller.discovery.common.CloudAccountConstants.UpdateAction;
import com.vmware.photon.controller.discovery.common.CollectionStringFieldUpdate;
import com.vmware.photon.controller.discovery.common.ErrorCode;
import com.vmware.photon.controller.discovery.common.PhotonControllerCloudAccountUtils;
import com.vmware.photon.controller.discovery.common.SymphonyCommonTestUtils;
import com.vmware.photon.controller.discovery.common.TagFieldUpdate;
import com.vmware.photon.controller.discovery.common.services.OrganizationService;
import com.vmware.photon.controller.discovery.common.services.UserService;
import com.vmware.photon.controller.discovery.common.services.UserService.UserState;
import com.vmware.photon.controller.discovery.endpoints.EndpointCreationTaskService.EndpointCreationTaskState;
import com.vmware.photon.controller.discovery.endpoints.EndpointUpdateTaskService.EndpointUpdateTaskState;
import com.vmware.photon.controller.discovery.endpoints.EndpointUpdateTaskService.SubStage;
import com.vmware.photon.controller.discovery.onboarding.OnBoardingTestUtils;
import com.vmware.photon.controller.discovery.onboarding.OnboardingServices;
import com.vmware.photon.controller.discovery.onboarding.OnboardingUtils;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSAdapters;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.adapters.util.TagsUtil;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.xenon.common.BasicTestCase;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.UserGroupService;

public class TestEndpointUpdateTaskService extends BasicTestCase {
    public int numNodes = 3;

    private static final String USER_1_ID = "user1@org-1.com";
    private static final String USER_1_PASSWORD = "passwordforuser1";

    private static final String USER_2_ID = "user2@org-1.com";
    private static final String USER_2_PASSWORD = "passwordforuser2";

    private static final String USER_3_ID = "user3@org-2.com";
    private static final String USER_3_PASSWORD = "passwordforuser3";

    private static final String USER_4_ID = "user4@org-1.com";
    private static final String USER_4_PASSWORD = "passwordforuser4";

    private static final String ORG_1_ID = "org-1";
    private static final String PROJECT_1_ID = OnboardingUtils.getDefaultProjectName();

    private static final String ORG_2_ID = "org-2";
    private static final String PROJECT_2_ID = OnboardingUtils.getDefaultProjectName();

    // AWS access key
    private String accessKey = "accessKey";
    // AWS secret key
    private String secretKey = "secretKey";
    // AWS S3 bucketName
    private String s3bucketName = "s3bucketName";
    private String s3bucketPrefix = "prefix";
    private String costUsageReportName = "report";
    private boolean isMock = true;

    private URI peerUri;
    private String orgLink;
    private String orgLink2;
    private String projectLink;
    private String projectLink2;
    private String userLink;
    private AtomicReference<TestContext> ctxRef = new AtomicReference<TestContext>();

    @Override
    public void beforeHostStart(VerificationHost h) {
        h.setAuthorizationEnabled(true);
    }

    @Before
    public void setUp() throws Throwable {
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
            h.startService(new MockEnumerationAdapter(this.ctxRef));
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
        factories.add(EndpointCreationTaskService.FACTORY_LINK);
        factories.add(EndpointUpdateTaskService.FACTORY_LINK);
        factories.add(EndpointAllocationTaskService.FACTORY_LINK);
        factories.add(AwsCostUsageReportTaskService.FACTORY_LINK);
        SymphonyCommonTestUtils
                .waitForFactoryAvailability(this.host, this.host.getPeerHostUri(), factories);

        this.peerUri = this.host.getPeerHostUri();

        /* Four different users:
         *  - User 1: in org1, project1
         *  - User 2: in org1, but not project1
         *  - User 3: in org1, project1
         *  - User 4: in org2
         */
        OnBoardingTestUtils.setupUser(this.host, this.peerUri,
                OnBoardingTestUtils.createUserData(USER_1_ID, USER_1_PASSWORD));
        OnBoardingTestUtils.setupOrganization(this.host, this.peerUri, ORG_1_ID, USER_1_ID, USER_1_PASSWORD);
        this.projectLink = OnBoardingTestUtils.setupProject(this.host, this.peerUri, PROJECT_1_ID,
                ORG_1_ID, USER_1_ID, USER_1_PASSWORD);
        this.orgLink = UriUtils.buildUriPath(OrganizationService.FACTORY_LINK,
                Utils.computeHash(ORG_1_ID));
        this.userLink = UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_1_ID));

        // user2
        OnBoardingTestUtils.setupUser(this.host, this.peerUri,
                OnBoardingTestUtils.createUserData(USER_2_ID, USER_2_PASSWORD));
        OnBoardingTestUtils.addUserToOrgOrProject(this.host, this.peerUri, this.orgLink, USER_2_ID,
                USER_2_ID, USER_2_PASSWORD);

        // user3
        OnBoardingTestUtils.setupUser(this.host, this.peerUri,
                OnBoardingTestUtils.createUserData(USER_3_ID, USER_3_PASSWORD));
        OnBoardingTestUtils.addUserToOrgOrProject(this.host, this.peerUri, this.orgLink, USER_3_ID,
                USER_3_ID, USER_3_PASSWORD);
        OnBoardingTestUtils.addUserToOrgOrProject(this.host, this.peerUri, this.projectLink,
                USER_3_ID, USER_3_ID, USER_3_PASSWORD);

        // user4
        OnBoardingTestUtils.setupUser(this.host, this.peerUri,
                OnBoardingTestUtils.createUserData(USER_4_ID, USER_4_PASSWORD));
        this.host.assumeIdentity(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_4_ID), null));
        OnBoardingTestUtils.setupOrganization(this.host, this.peerUri, ORG_2_ID, USER_4_ID,
                USER_4_PASSWORD);
        this.projectLink2 = OnBoardingTestUtils.setupProject(this.host, this.peerUri, PROJECT_2_ID,
                ORG_2_ID, USER_4_ID, USER_4_PASSWORD);
        this.orgLink2 = UriUtils.buildUriPath(OrganizationService.FACTORY_LINK,
                Utils.computeHash(ORG_2_ID));

        this.host.assumeIdentity(this.userLink);
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
        EndpointUpdateTaskState task = new EndpointUpdateTaskState();
        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointUpdateTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, ENDPOINT_LINK_REQUIRED);

        task.endpointLink = "foo";
        Set<TagFieldUpdate> tags = new HashSet<>();
        TagFieldUpdate tagUpdate = new TagFieldUpdate(ADD,"dummyKeyB", "");
        Collections.addAll(tags, tagUpdate);
        task.tagUpdates = new HashSet<>();
        task.tagUpdates.addAll(tags);
        response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointUpdateTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, ENDPOINT_TAG_NULL_EMPTY);

        tags = new HashSet<>();
        tagUpdate = new TagFieldUpdate(ADD, "", "dummyVal");
        Collections.addAll(tags, tagUpdate);
        task.tagUpdates = new HashSet<>();
        task.tagUpdates.addAll(tags);
        response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointUpdateTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, ENDPOINT_TAG_NULL_EMPTY);

        tags = new HashSet<>();
        tagUpdate = new TagFieldUpdate(ADD, null, "val");
        Collections.addAll(tags, tagUpdate);
        task.tagUpdates = new HashSet<>();
        task.tagUpdates.addAll(tags);
        response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointUpdateTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, ENDPOINT_TAG_NULL_EMPTY);

        tags = new HashSet<>();
        tagUpdate = new TagFieldUpdate(ADD, "key", null);
        Collections.addAll(tags, tagUpdate);
        task.tagUpdates = new HashSet<>();
        task.tagUpdates.addAll(tags);
        response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointUpdateTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, ENDPOINT_TAG_NULL_EMPTY);
    }

    @Test
    public void testInvalidEndpointLink() {
        EndpointUpdateTaskState task = new EndpointUpdateTaskState();
        task.endpointLink = "foobar";
        task.name = "my-endpoint-update";
        task.taskInfo = TaskState.createDirect();

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointUpdateTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        EndpointUpdateTaskState body = response.getBody(EndpointUpdateTaskState.class);
        assertEquals(SubStage.ERROR, body.subStage);
        assertEquals(TaskStage.FAILED, body.taskInfo.stage);
        assertEquals(Operation.STATUS_CODE_NOT_FOUND, body.taskInfo.failure.statusCode);
    }

    @Test
    public void testUpdateToAnotherExistingCloudAccountFailsValidation() {
        EndpointCreationTaskService.EndpointCreationTaskState endpointState =
                createMockAwsEndpoint(this.host, this.peerUri, this.orgLink,
                        "my-endpoint1", "foo1", "bar1",
                        "testS3Bucket1", null, null);

        EndpointCreationTaskService.EndpointCreationTaskState endpointStateToUpdate =
                createMockAwsEndpoint(this.host, this.peerUri, this.orgLink,
                        "my-endpoint2", "foo2", "bar2",
                        "testS3Bucket2", null, null);

        EndpointUpdateTaskState updateTask = new EndpointUpdateTaskState();
        updateTask.name = "my-endpoint2";
        updateTask.endpointLink = endpointStateToUpdate.endpointLink;
        updateTask.credentials = endpointState.credentials;
        updateTask.taskInfo = TaskState.createDirect();

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointUpdateTaskService.FACTORY_LINK))
                .setBody(updateTask));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        EndpointUpdateTaskState body = response.getBody(EndpointUpdateTaskState.class);
        assertEquals(SubStage.ERROR, body.subStage);
        assertEquals(TaskStage.FAILED, body.taskInfo.stage);

        String endpointLink = UriUtils.getLastPathSegment(endpointState.endpointLink);
        String expectedCloudAccountLink = UriUtils.buildUriPath(
                CLOUD_ACCOUNT_API_SERVICE, endpointLink);
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, body.taskInfo.failure.statusCode);
        assertEquals(ENDPOINT_ALREADY_EXISTS.getErrorCode(),
                Integer.parseInt(body.taskInfo.failure.messageId));
        assertEquals(expectedCloudAccountLink, body.taskInfo.failure.message);
    }

    @Test
    public void testUpdateToAnotherExistingCloudAccountFailsValidationArn() {
        String arn = "arn:aws:iam::123456789123:role/test-role";
        String externalId = "test-external-id";

        EndpointCreationTaskService.EndpointCreationTaskState endpointState =
                createMockAwsEndpointArn(this.host, this.peerUri, this.orgLink, "my-endpoint1",
                        arn + "1", externalId,"testS3Bucket1", null, null);

        EndpointCreationTaskService.EndpointCreationTaskState endpointStateToUpdate =
                createMockAwsEndpointArn(this.host, this.peerUri, this.orgLink, "my-endpoint2",
                        arn + "2", externalId,"testS3Bucket2", null, null);

        EndpointUpdateTaskState updateTask = new EndpointUpdateTaskState();
        updateTask.name = "my-endpoint2";
        updateTask.endpointLink = endpointStateToUpdate.endpointLink;
        updateTask.credentials = endpointState.credentials;
        updateTask.taskInfo = TaskState.createDirect();

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointUpdateTaskService.FACTORY_LINK))
                .setBody(updateTask));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        EndpointUpdateTaskState body = response.getBody(EndpointUpdateTaskState.class);
        assertEquals(SubStage.ERROR, body.subStage);
        assertEquals(TaskStage.FAILED, body.taskInfo.stage);

        String endpointLink = UriUtils.getLastPathSegment(endpointState.endpointLink);
        String expectedCloudAccountLink = UriUtils.buildUriPath(
                CLOUD_ACCOUNT_API_SERVICE, endpointLink);
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, body.taskInfo.failure.statusCode);
        assertEquals(ENDPOINT_ALREADY_EXISTS.getErrorCode(),
                Integer.parseInt(body.taskInfo.failure.messageId));
        assertEquals(expectedCloudAccountLink, body.taskInfo.failure.message);
    }

    @Test
    public void testUpdateToAnotherExistingS3BucketFailsValidationArn() {
        String arn = "arn:aws:iam::123456789123:role/test-role";
        String externalId = "test-external-id";

        EndpointCreationTaskService.EndpointCreationTaskState endpointState =
                createMockAwsEndpointArn(this.host, this.peerUri, this.orgLink,
                        "my-endpoint1", arn + "1", externalId,
                        "testS3Bucket1", null, null);

        EndpointCreationTaskService.EndpointCreationTaskState endpointStateToUpdate =
                createMockAwsEndpointArn(this.host, this.peerUri, this.orgLink,
                        "my-endpoint2", arn + "2", externalId,
                        "testS3Bucket2", null, null);

        EndpointUpdateTaskState updateTask = new EndpointUpdateTaskState();
        updateTask.endpointLink = endpointStateToUpdate.endpointLink;
        updateTask.customPropertyUpdates = new HashMap<>();
        CollectionStringFieldUpdate update = new CollectionStringFieldUpdate();
        update.action = CloudAccountConstants.UpdateAction.ADD;
        update.value = "testS3Bucket1";
        updateTask.customPropertyUpdates.put(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET, update);
        updateTask.taskInfo = TaskState.createDirect();

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointUpdateTaskService.FACTORY_LINK))
                .setBody(updateTask));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        EndpointUpdateTaskState body = response.getBody(EndpointUpdateTaskState.class);
        assertEquals(SubStage.ERROR, body.subStage);
        assertEquals(TaskStage.FAILED, body.taskInfo.stage);

        String endpointLink = UriUtils.getLastPathSegment(endpointState.endpointLink);
        String expectedCloudAccountLink = UriUtils.buildUriPath(
                CLOUD_ACCOUNT_API_SERVICE, endpointLink);
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, body.taskInfo.failure.statusCode);
        assertEquals(ENDPOINT_WITH_S3BUCKETNAME_ALREADY_EXISTS.getErrorCode(),
                Integer.parseInt(body.taskInfo.failure.messageId));
        assertEquals(expectedCloudAccountLink, body.taskInfo.failure.message);
    }

    @Test
    public void testUpdateToAnotherExistingS3BucketFailsValidation() {
        EndpointCreationTaskService.EndpointCreationTaskState endpointState =
                createMockAwsEndpoint(this.host, this.peerUri, this.orgLink,
                        "my-endpoint1", "foo1", "bar1",
                        "testS3Bucket1", null, null);

        EndpointCreationTaskService.EndpointCreationTaskState endpointStateToUpdate =
                createMockAwsEndpoint(this.host, this.peerUri, this.orgLink,
                        "my-endpoint2", "foo2", "bar2",
                        "testS3Bucket2", null, null);

        EndpointUpdateTaskState updateTask = new EndpointUpdateTaskState();
        updateTask.endpointLink = endpointStateToUpdate.endpointLink;
        updateTask.customPropertyUpdates = new HashMap<String, CollectionStringFieldUpdate>();
        CollectionStringFieldUpdate update = new CollectionStringFieldUpdate();
        update.action = CloudAccountConstants.UpdateAction.ADD;
        update.value = "testS3Bucket1";
        updateTask.customPropertyUpdates.put(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET, update);
        updateTask.taskInfo = TaskState.createDirect();

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointUpdateTaskService.FACTORY_LINK))
                .setBody(updateTask));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        EndpointUpdateTaskState body = response.getBody(EndpointUpdateTaskState.class);
        assertEquals(SubStage.ERROR, body.subStage);
        assertEquals(TaskStage.FAILED, body.taskInfo.stage);

        String endpointLink = UriUtils.getLastPathSegment(endpointState.endpointLink);
        String expectedCloudAccountLink = UriUtils.buildUriPath(
                CLOUD_ACCOUNT_API_SERVICE, endpointLink);
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, body.taskInfo.failure.statusCode);
        assertEquals(ENDPOINT_WITH_S3BUCKETNAME_ALREADY_EXISTS.getErrorCode(),
                Integer.parseInt(body.taskInfo.failure.messageId));
        assertEquals(expectedCloudAccountLink, body.taskInfo.failure.message);
    }

    @Test
    public void testAddS3BucketOnlyUpdateInMockMode() {
        EndpointCreationTaskService.EndpointCreationTaskState endpointStateToUpdate =
                createMockAwsEndpoint(this.host, this.peerUri, this.orgLink,
                        "my-endpoint2", "foo2", "bar2",
                        "testS3Bucket2", null, null);

        EndpointUpdateTaskState updateTask = new EndpointUpdateTaskState();
        updateTask.endpointLink = endpointStateToUpdate.endpointLink;
        updateTask.customPropertyUpdates = new HashMap<String, CollectionStringFieldUpdate>();
        CollectionStringFieldUpdate update = new CollectionStringFieldUpdate();
        update.action = CloudAccountConstants.UpdateAction.ADD;
        update.value = "testS3Bucket1";
        updateTask.customPropertyUpdates.put(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET, update);
        // this.isMock = true skips credential validation only for this test
        updateTask.isMock = true;
        updateTask.taskInfo = TaskState.createDirect();

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointUpdateTaskService.FACTORY_LINK))
                .setBody(updateTask));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        EndpointUpdateTaskState body = response.getBody(EndpointUpdateTaskState.class);
        assertEquals(SubStage.SUCCESS, body.subStage);
        assertEquals(TaskStage.FINISHED, body.taskInfo.stage);

        // Validate S3 is successfully updated
        Operation endpointOp = this.host.waitForResponse(Operation.createGet(
                UriUtils.buildUri(this.peerUri, body.endpointLink)));
        assertEquals(Operation.STATUS_CODE_OK, endpointOp.getStatusCode());
        EndpointState endpoint = endpointOp.getBody(EndpointState.class);
        assertNotNull(endpoint);
        assertEquals("testS3Bucket1", endpoint.customProperties.get(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET));
    }

    @Test
    public void testUpdateCostUsageReportS3Properties() throws GeneralSecurityException {
        this.host.assumeIdentity(this.userLink);

        EndpointCreationTaskService.EndpointCreationTaskState endpointStateToUpdate =
                createMockAwsEndpointCostUsage(this.host, this.peerUri, this.orgLink,
                        "my-endpoint2", this.secretKey, this.accessKey,
                        this.s3bucketName, null, null,this.s3bucketPrefix,this.costUsageReportName);

        EndpointUpdateTaskState updateTask = new EndpointUpdateTaskState();
        updateTask.endpointLink = endpointStateToUpdate.endpointLink;
        updateTask.customPropertyUpdates = new HashMap<String, CollectionStringFieldUpdate>();
        CollectionStringFieldUpdate s3bucketUpdate = new CollectionStringFieldUpdate(ADD, this.s3bucketName);
        updateTask.customPropertyUpdates.put(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET, s3bucketUpdate);
        CollectionStringFieldUpdate s3PrefixUpdate = new CollectionStringFieldUpdate(ADD, this.s3bucketPrefix);
        updateTask.customPropertyUpdates.put(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BUCKET_PREFIX, s3PrefixUpdate);
        CollectionStringFieldUpdate reportUpdate = new CollectionStringFieldUpdate(ADD, this.costUsageReportName);
        updateTask.customPropertyUpdates.put(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_COSTUSAGE_REPORT_NAME, reportUpdate);

        // this.isMock = true skips credential validation only for this test
        updateTask.isMock = true;
        updateTask.taskInfo = TaskState.createDirect();

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointUpdateTaskService.FACTORY_LINK))
                .setBody(updateTask));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        EndpointUpdateTaskState body = response.getBody(EndpointUpdateTaskState.class);
        assertEquals(SubStage.SUCCESS, body.subStage);
        assertEquals(TaskStage.FINISHED, body.taskInfo.stage);

        // Validate S3 is successfully updated
        Operation endpointOp = this.host.waitForResponse(Operation.createGet(
                UriUtils.buildUri(this.peerUri, body.endpointLink)));
        assertEquals(Operation.STATUS_CODE_OK, endpointOp.getStatusCode());
        EndpointState endpoint = endpointOp.getBody(EndpointState.class);
        assertNotNull(endpoint);
        assertEquals(this.s3bucketName, endpoint.customProperties.get(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET));
        assertEquals(this.costUsageReportName, endpoint.customProperties.get(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_COSTUSAGE_REPORT_NAME));
        assertEquals(this.s3bucketPrefix, endpoint.customProperties.get(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BUCKET_PREFIX));

        // check report creation only if its not in mock mode
        if (!updateTask.isMock) {
            Credentials awsCredentials = createAwsCredentials(this.accessKey, this.secretKey);
            Boolean ifReportExists = checkIfReportAlreadyExists(awsCredentials, this.s3bucketName,
                    this.s3bucketPrefix, this.costUsageReportName);
            assertEquals(ifReportExists, true);
        }
    }

    @Test
    public void testUpdateByAddingCostUsageReportS3Properties() {
        EndpointCreationTaskService.EndpointCreationTaskState endpointStateToUpdate =
                createMockAwsEndpoint(this.host, this.peerUri, this.orgLink,
                        "my-endpoint2", this.secretKey, this.accessKey, null,
                        null, null);

        EndpointUpdateTaskState updateTask = new EndpointUpdateTaskState();
        updateTask.endpointLink = endpointStateToUpdate.endpointLink;
        updateTask.customPropertyUpdates = new HashMap<String, CollectionStringFieldUpdate>();
        CollectionStringFieldUpdate s3bucketUpdate = new CollectionStringFieldUpdate(ADD, this.s3bucketName);
        updateTask.customPropertyUpdates.put(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET, s3bucketUpdate);
        CollectionStringFieldUpdate s3PrefixUpdate = new CollectionStringFieldUpdate(ADD, this.s3bucketPrefix);
        updateTask.customPropertyUpdates.put(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BUCKET_PREFIX, s3PrefixUpdate);
        CollectionStringFieldUpdate reportUpdate = new CollectionStringFieldUpdate(ADD, this.costUsageReportName);
        updateTask.customPropertyUpdates.put(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_COSTUSAGE_REPORT_NAME, reportUpdate);

        // this.isMock = true skips credential validation only for this test
        updateTask.isMock = this.isMock;
        updateTask.taskInfo = TaskState.createDirect();

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointUpdateTaskService.FACTORY_LINK))
                .setBody(updateTask));

        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        EndpointUpdateTaskState body = response.getBody(EndpointUpdateTaskState.class);
        assertEquals(SubStage.SUCCESS, body.subStage);
        assertEquals(TaskStage.FINISHED, body.taskInfo.stage);

        // Validate S3 is successfully updated
        Operation endpointOp = this.host.waitForResponse(Operation.createGet(
                UriUtils.buildUri(this.peerUri, body.endpointLink)));
        assertEquals(Operation.STATUS_CODE_OK, endpointOp.getStatusCode());
        EndpointState endpoint = endpointOp.getBody(EndpointState.class);
        assertNotNull(endpoint);
        assertEquals(this.s3bucketName, endpoint.customProperties.get(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET));
        assertEquals(this.costUsageReportName, endpoint.customProperties.get(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_COSTUSAGE_REPORT_NAME));
        assertEquals(this.s3bucketPrefix, endpoint.customProperties.get(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BUCKET_PREFIX));

        // check report creation only if its not in mock mode
        if (!updateTask.isMock) {
            Credentials awsCredentials = createAwsCredentials(this.accessKey, this.secretKey);
            Boolean ifReportExists = checkIfReportAlreadyExists(awsCredentials, this.s3bucketName,
                    this.s3bucketPrefix, this.costUsageReportName);
            assertEquals(ifReportExists, true);
        }
    }

    @Test
    public void testOnlyRemoveS3BucketUpdate() {
        EndpointCreationTaskService.EndpointCreationTaskState endpointStateToUpdate =
                createMockAwsEndpoint(this.host, this.peerUri, this.orgLink,
                        "my-endpoint2", "foo2", "bar2",
                        "testS3Bucket2", null, null);

        EndpointUpdateTaskState updateTask = new EndpointUpdateTaskState();
        updateTask.endpointLink = endpointStateToUpdate.endpointLink;
        updateTask.customPropertyUpdates = new HashMap<String, CollectionStringFieldUpdate>();
        CollectionStringFieldUpdate update = new CollectionStringFieldUpdate();
        update.action = CloudAccountConstants.UpdateAction.REMOVE;
        update.value = "testS3Bucket2";
        updateTask.customPropertyUpdates.put(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET, update);
        updateTask.taskInfo = TaskState.createDirect();

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointUpdateTaskService.FACTORY_LINK))
                .setBody(updateTask));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        EndpointUpdateTaskState body = response.getBody(EndpointUpdateTaskState.class);
        assertEquals(SubStage.SUCCESS, body.subStage);
        assertEquals(TaskStage.FINISHED, body.taskInfo.stage);

        // Validate S3 is successfully updated
        Operation endpointOp = this.host.waitForResponse(Operation.createGet(
                UriUtils.buildUri(this.peerUri, body.endpointLink)));
        assertEquals(Operation.STATUS_CODE_OK, endpointOp.getStatusCode());
        EndpointState endpoint = endpointOp.getBody(EndpointState.class);
        assertNotNull(endpoint);
        assertEquals("my-endpoint2", endpoint.name);
        assertNull(endpoint.customProperties.get(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET));
    }

    @Test
    public void testUpdateEndpointNameAndDescription() {
        EndpointCreationTaskService.EndpointCreationTaskState endpointStateToUpdate =
                createMockAwsEndpoint(this.host, this.peerUri, this.orgLink,
                        "my-endpoint2", "foo2", "bar2",
                        "testS3Bucket2", null, null);

        EndpointUpdateTaskState updateTask = new EndpointUpdateTaskState();
        updateTask.endpointLink = endpointStateToUpdate.endpointLink;
        updateTask.name = "my-endpoint2-updated-name";
        updateTask.description = "my-endpoint2-updated-description";
        updateTask.taskInfo = TaskState.createDirect();

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointUpdateTaskService.FACTORY_LINK))
                .setBody(updateTask));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        EndpointUpdateTaskState body = response.getBody(EndpointUpdateTaskState.class);
        assertEquals(SubStage.SUCCESS, body.subStage);
        assertEquals(TaskStage.FINISHED, body.taskInfo.stage);

        // Validate updates
        Operation endpointOp = this.host.waitForResponse(Operation.createGet(
                UriUtils.buildUri(this.peerUri, body.endpointLink)));
        assertEquals(Operation.STATUS_CODE_OK, endpointOp.getStatusCode());
        EndpointState endpoint = endpointOp.getBody(EndpointState.class);
        assertNotNull(endpoint);
        assertEquals("my-endpoint2-updated-name", endpoint.name);
        assertEquals("my-endpoint2-updated-description", endpoint.desc);
    }

    @Test
    public void testUpdateRequiredEndpointNameToEmptyFails() {
        EndpointCreationTaskService.EndpointCreationTaskState endpointStateToUpdate =
                createMockAwsEndpoint(this.host, this.peerUri, this.orgLink,
                        "my-endpoint2", "foo2", "bar2",
                        "testS3Bucket2", null, null);

        EndpointUpdateTaskState updateTask = new EndpointUpdateTaskState();
        updateTask.endpointLink = endpointStateToUpdate.endpointLink;
        updateTask.name = "";
        updateTask.description = "my-endpoint2-updated-description";
        updateTask.taskInfo = TaskState.createDirect();

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointUpdateTaskService.FACTORY_LINK))
                .setBody(updateTask));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, ENDPOINT_NAME_REQUIRED);
    }

    @Test
    public void testUpdateOnlyOptionalDescriptionToEmpty() {
        EndpointCreationTaskService.EndpointCreationTaskState endpointStateToUpdate =
                createMockAwsEndpoint(this.host, this.peerUri, this.orgLink,
                        "my-endpoint2", "foo2", "bar2",
                        "testS3Bucket2", null, null);

        EndpointUpdateTaskState updateTask = new EndpointUpdateTaskState();
        updateTask.endpointLink = endpointStateToUpdate.endpointLink;
        updateTask.taskInfo = TaskState.createDirect();

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointUpdateTaskService.FACTORY_LINK))
                .setBody(updateTask));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, ENDPOINT_NOTHING_TO_UPDATE);

        // empty description should be successful
        updateTask.description = "";
        updateTask.taskInfo = TaskState.createDirect();
        response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointUpdateTaskService.FACTORY_LINK))
                .setBody(updateTask));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        EndpointUpdateTaskState body = response.getBody(EndpointUpdateTaskState.class);
        assertEquals(SubStage.SUCCESS, body.subStage);
        assertEquals(TaskStage.FINISHED, body.taskInfo.stage);

        // Validate updates
        Operation endpointOp = this.host.waitForResponse(Operation.createGet(
                UriUtils.buildUri(this.peerUri, body.endpointLink)));
        assertEquals(Operation.STATUS_CODE_OK, endpointOp.getStatusCode());
        EndpointState endpoint = endpointOp.getBody(EndpointState.class);
        assertNotNull(endpoint);
        assertEquals("", endpoint.desc);
    }

    @Test
    public void testUpdateToAddRemoveServiceTag() {
        EndpointCreationTaskService.EndpointCreationTaskState endpointStateToUpdate =
                createMockAwsEndpoint(this.host, this.peerUri, this.orgLink,
                        "my-endpoint2", "foo2", "bar2",
                        "testS3Bucket2", null, null);

        // Add service tag
        EndpointUpdateTaskState updateTask = new EndpointUpdateTaskState();
        updateTask.endpointLink = endpointStateToUpdate.endpointLink;
        updateTask.name = "my-endpoint2-updated-name";
        updateTask.description = "my-endpoint2-updated-description";
        CollectionStringFieldUpdate serviceUpdate = new CollectionStringFieldUpdate();
        serviceUpdate.action = CloudAccountConstants.UpdateAction.ADD;
        serviceUpdate.value = "cost-insight";
        updateTask.serviceUpdate = serviceUpdate;
        String key = ENDPOINT_CUSTOM_PROPERTY_NAME_PREFIX_SERVICE_TAG + serviceUpdate.value.toLowerCase();
        updateTask.taskInfo = TaskState.createDirect();

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointUpdateTaskService.FACTORY_LINK))
                .setBody(updateTask));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        EndpointUpdateTaskState body = response.getBody(EndpointUpdateTaskState.class);
        assertEquals(SubStage.SUCCESS, body.subStage);
        assertEquals(TaskStage.FINISHED, body.taskInfo.stage);

        // Validate service tag added
        Operation endpointOp = this.host.waitForResponse(Operation.createGet(
                UriUtils.buildUri(this.peerUri, body.endpointLink)));
        assertEquals(Operation.STATUS_CODE_OK, endpointOp.getStatusCode());
        EndpointState endpoint = endpointOp.getBody(EndpointState.class);
        assertNotNull(endpoint);
        assertEquals("my-endpoint2-updated-name", endpoint.name);
        assertEquals("my-endpoint2-updated-description", endpoint.desc);
        assertEquals("enabled", endpoint.customProperties.get(key));

        // Remove Service tag
        updateTask = new EndpointUpdateTaskState();
        updateTask.endpointLink = endpointStateToUpdate.endpointLink;
        serviceUpdate = new CollectionStringFieldUpdate();
        serviceUpdate.action = CloudAccountConstants.UpdateAction.REMOVE;
        serviceUpdate.value = "cost-insight";
        updateTask.serviceUpdate = serviceUpdate;
        updateTask.taskInfo = TaskState.createDirect();

        response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointUpdateTaskService.FACTORY_LINK))
                .setBody(updateTask));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        body = response.getBody(EndpointUpdateTaskState.class);
        assertEquals(SubStage.SUCCESS, body.subStage);
        assertEquals(TaskStage.FINISHED, body.taskInfo.stage);

        // Validate service tag removed
        endpointOp = this.host.waitForResponse(Operation.createGet(
                UriUtils.buildUri(this.peerUri, body.endpointLink)));
        assertEquals(Operation.STATUS_CODE_OK, endpointOp.getStatusCode());
        endpoint = endpointOp.getBody(EndpointState.class);
        assertNotNull(endpoint);
        assertNull(endpoint.customProperties.get(key));
    }

    @Test
    public void testEndpointOwnersUpdateOnly() throws Throwable {
        this.host.setSystemAuthorizationContext();
        OnBoardingTestUtils
                .addUserToOrgOrProject(this.host, this.peerUri, this.orgLink, USER_3_ID,
                        USER_3_ID, USER_3_PASSWORD);
        this.host.resetAuthorizationContext();

        this.host.assumeIdentity(
                UriUtils.buildUriPath(UserService.FACTORY_LINK,
                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_1_ID)), null);

        List<CollectionStringFieldUpdate> ownerUpdates = new ArrayList<>();
        ownerUpdates.add(CollectionStringFieldUpdate.create(UpdateAction.ADD, USER_3_ID));
        ownerUpdates.add(CollectionStringFieldUpdate.create(UpdateAction.REMOVE, USER_2_ID));

        EndpointCreationTaskState task = createMockAwsEndpoint(this.host, this.peerUri, this.orgLink,
                "my-endpoint", "foo", "bar",
                null, null, Collections.singleton(USER_2_ID));
        String endpointLink = task.endpointLink;

        EndpointUpdateTaskState updateTask = new EndpointUpdateTaskState();
        updateTask.endpointLink = endpointLink;
        updateTask.taskInfo = TaskState.createDirect();
        updateTask.ownerUpdates = ownerUpdates;

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointUpdateTaskService.FACTORY_LINK))
                .setBody(updateTask));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        EndpointUpdateTaskState body = response.getBody(EndpointUpdateTaskState.class);
        assertEquals(SubStage.SUCCESS, body.subStage);
        assertEquals(TaskStage.FINISHED, body.taskInfo.stage);

        // Validate an update occurred
        Operation endpointOp = this.host.waitForResponse(Operation.createGet(
                UriUtils.buildUri(this.peerUri, body.endpointLink)));
        assertEquals(Operation.STATUS_CODE_OK, endpointOp.getStatusCode());
        EndpointState endpoint = endpointOp.getBody(EndpointState.class);
        assertNotNull(endpoint);

        this.host.setSystemAuthorizationContext();

        // Validate owner update
        Operation getUser2 = this.host.waitForResponse(Operation.createGet(UriUtils
                .buildUri(this.peerUri, UriUtils.buildUriPath(UserService.FACTORY_LINK,
                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_2_ID)))));
        Operation getUser3 = this.host.waitForResponse(Operation.createGet(UriUtils
                .buildUri(this.peerUri, UriUtils.buildUriPath(UserService.FACTORY_LINK,
                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_3_ID)))));

        UserState user2State = getUser2.getBody(UserState.class);
        UserState user3State = getUser3.getBody(UserState.class);

        assertFalse(user2State.userGroupLinks.contains(EndpointUtils.buildEndpointAuthzArtifactSelfLink(
                UserGroupService.FACTORY_LINK, endpoint.documentSelfLink, endpoint.tenantLinks)));
        assertTrue(user3State.userGroupLinks.contains(EndpointUtils.buildEndpointAuthzArtifactSelfLink(
                UserGroupService.FACTORY_LINK, endpoint.documentSelfLink, endpoint.tenantLinks)));
    }

    @Test
    public void testEndpointUpdate() throws Throwable {
        this.host.setSystemAuthorizationContext();
        OnBoardingTestUtils
                .addUserToOrgOrProject(this.host, this.peerUri, this.orgLink, USER_3_ID,
                        USER_3_ID, USER_3_PASSWORD);
        this.host.resetAuthorizationContext();

        this.host.assumeIdentity(
                UriUtils.buildUriPath(UserService.FACTORY_LINK,
                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_1_ID)), null);

        List<CollectionStringFieldUpdate> ownerUpdates = new ArrayList<>();
        ownerUpdates.add(CollectionStringFieldUpdate.create(UpdateAction.ADD, USER_3_ID));
        ownerUpdates.add(CollectionStringFieldUpdate.create(UpdateAction.REMOVE, USER_2_ID));

        EndpointCreationTaskState task = createMockAwsEndpoint(this.host, this.peerUri, this.orgLink,
                "my-endpoint", "foo", "bar",
                null, null, Collections.singleton(USER_2_ID));
        String endpointLink = task.endpointLink;

        EndpointUpdateTaskState updateTask = new EndpointUpdateTaskState();
        updateTask.endpointLink = endpointLink;
        updateTask.credentials = createAwsCredentials("update-bar",
                "update-foo");
        updateTask.isMock = true;
        updateTask.name = "my-endpoint-update";
        updateTask.taskInfo = TaskState.createDirect();
        updateTask.ownerUpdates = ownerUpdates;

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointUpdateTaskService.FACTORY_LINK))
                .setBody(updateTask));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        EndpointUpdateTaskState body = response.getBody(EndpointUpdateTaskState.class);
        assertEquals(SubStage.SUCCESS, body.subStage);
        assertEquals(TaskStage.FINISHED, body.taskInfo.stage);

        // Validate an update occurred
        Operation endpointOp = this.host.waitForResponse(Operation.createGet(
                UriUtils.buildUri(this.peerUri, body.endpointLink)));
        assertEquals(Operation.STATUS_CODE_OK, endpointOp.getStatusCode());
        EndpointState endpoint = endpointOp.getBody(EndpointState.class);
        assertNotNull(endpoint);
        assertEquals(updateTask.name, endpoint.name);
        assertNotNull(endpoint.endpointProperties);

        this.host.setSystemAuthorizationContext();

        // Validate owner update
        Operation getUser2 = this.host.waitForResponse(Operation.createGet(UriUtils
                .buildUri(this.peerUri, UriUtils.buildUriPath(UserService.FACTORY_LINK,
                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_2_ID)))));
        Operation getUser3 = this.host.waitForResponse(Operation.createGet(UriUtils
                .buildUri(this.peerUri, UriUtils.buildUriPath(UserService.FACTORY_LINK,
                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_3_ID)))));

        UserState user2State = getUser2.getBody(UserState.class);
        UserState user3State = getUser3.getBody(UserState.class);

        assertFalse(user2State.userGroupLinks.contains(EndpointUtils.buildEndpointAuthzArtifactSelfLink(
                UserGroupService.FACTORY_LINK, endpoint.documentSelfLink, endpoint.tenantLinks)));
        assertTrue(user3State.userGroupLinks.contains(EndpointUtils.buildEndpointAuthzArtifactSelfLink(
                UserGroupService.FACTORY_LINK, endpoint.documentSelfLink, endpoint.tenantLinks)));
    }

    @Test
    public void testEndpointUpdateCredentials() throws Throwable {
        EndpointCreationTaskState task = createMockAwsEndpoint(this.host, this.peerUri, this.orgLink,
                "my-endpoint", "foo", "bar",
                null, null, null);
        String endpointLink = task.endpointLink;

        EndpointUpdateTaskState updateTask = new EndpointUpdateTaskState();
        updateTask.endpointLink = endpointLink;
        updateTask.name = "my-endpoint-update";
        updateTask.endpointProperties = new HashMap<>();
        updateTask.endpointProperties.put(PRIVATE_KEYID_KEY, "this-update-will-be-overridden");
        updateTask.credentials = createAwsCredentials("update-bar", "update-foo");
        updateTask.isMock = true;
        updateTask.taskInfo = TaskState.createDirect();

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointUpdateTaskService.FACTORY_LINK))
                .setBody(updateTask));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        EndpointUpdateTaskState body = response.getBody(EndpointUpdateTaskState.class);
        assertEquals(SubStage.SUCCESS, body.subStage);
        assertEquals(TaskStage.FINISHED, body.taskInfo.stage);

        // Validate an update occurred
        Operation endpointOp = this.host.waitForResponse(Operation.createGet(
                UriUtils.buildUri(this.peerUri, body.endpointLink)));
        assertEquals(Operation.STATUS_CODE_OK, endpointOp.getStatusCode());
        EndpointState endpoint = endpointOp.getBody(EndpointState.class);
        assertNotNull(endpoint);
        assertEquals(updateTask.name, endpoint.name);
        assertNotNull(endpoint.endpointProperties);
        assertEquals("update-bar",
                endpoint.endpointProperties.get(PRIVATE_KEYID_KEY));
    }

    @Test
    public void testEndpointUpdateCredentialsTriggersDataInitialization() throws Throwable {
        EndpointState endpointState = createEndpoint(PhotonModelConstants.EndpointType.aws.name());
        String endpointLink = UriUtils.buildUriPath(EndpointService.FACTORY_LINK, endpointState.id);

        EndpointUpdateTaskState updateTask = new EndpointUpdateTaskState();
        updateTask.endpointLink = endpointLink;
        updateTask.name = "my-endpoint-update";
        updateTask.endpointProperties = new HashMap<>();
        updateTask.endpointProperties.put(PRIVATE_KEYID_KEY, "this-update-will-be-overridden");
        updateTask.credentials = createAwsCredentials("update-bar", "update-foo");
        updateTask.tenantLinks = Collections.singleton(this.projectLink);
        updateTask.isMock = true;
        // setting this flag triggers Data Initialization even if this.isMock=true
        updateTask.isMockRunDataInit = true;
        updateTask.adapterReference = UriUtils.buildUri(this.peerUri, MockEnumerationAdapter.SELF_LINK);
        updateTask.taskInfo = TaskState.createDirect();

        TestContext testContext = new TestContext(1, Duration.ofSeconds(30));
        this.ctxRef.set(testContext);

        // Validate update finished successfully
        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointUpdateTaskService.FACTORY_LINK))
                .setBody(updateTask));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        EndpointUpdateTaskState body = response.getBody(EndpointUpdateTaskState.class);
        assertEquals(SubStage.SUCCESS, body.subStage);
        assertEquals(TaskStage.FINISHED, body.taskInfo.stage);

        // wait until testContext is closed in the MockEnumerationAdapter
        // this confirms that DataInitialization has been triggered
        testContext.await();
    }

    @Test
    public void testEndpointUpdateCredentialsArn() throws Throwable {
        EndpointCreationTaskState task = createMockAwsEndpointArn(this.host, this.peerUri, this.orgLink,
                "my-endpoint", "arn:aws:iam::123456789123:role/test-role", "external-id",
                null, null, null);
        String endpointLink = task.endpointLink;

        EndpointUpdateTaskState updateTask = new EndpointUpdateTaskState();
        updateTask.endpointLink = endpointLink;
        updateTask.name = "my-endpoint-update";
        updateTask.credentials = createAwsCredentialsArn("arn:aws:iam::123456789123:role/test-role-2", "update-foo");
        updateTask.isMock = true;
        updateTask.taskInfo = TaskState.createDirect();

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointUpdateTaskService.FACTORY_LINK))
                .setBody(updateTask));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        EndpointUpdateTaskState body = response.getBody(EndpointUpdateTaskState.class);
        assertEquals(SubStage.SUCCESS, body.subStage);
        assertEquals(TaskStage.FINISHED, body.taskInfo.stage);

        // Validate an update occurred
        Operation endpointOp = this.host.waitForResponse(Operation.createGet(
                UriUtils.buildUri(this.peerUri, body.endpointLink)));
        assertEquals(Operation.STATUS_CODE_OK, endpointOp.getStatusCode());
        EndpointState endpoint = endpointOp.getBody(EndpointState.class);
        assertNotNull(endpoint);
        assertEquals(updateTask.name, endpoint.name);
        assertNotNull(endpoint.endpointProperties);

        AuthCredentialsServiceState[] authCredentials = new AuthCredentialsServiceState[1];
        TestContext ctx = new TestContext(1, Duration.ofSeconds(30));
        host.sendWithDeferredResult(Operation.createGet(UriUtils.buildUri(this.peerUri, endpoint.authCredentialsLink)))
                .whenComplete((o, e) -> {
                    if (e != null) {
                        ctx.fail(e);
                        return;
                    }

                    authCredentials[0] = o.getBody(AuthCredentialsServiceState.class);
                    ctx.complete();
                });
        ctx.await();

        assertEquals("update-foo", authCredentials[0].customProperties.get(EXTERNAL_ID_KEY));
        assertEquals("arn:aws:iam::123456789123:role/test-role-2", authCredentials[0].customProperties.get(ARN_KEY));
        assertEquals(ARN.name(), authCredentials[0].customProperties.get(ENDPOINT_CUSTOM_PROPERTY_NAME_AUTH_TYPE));
    }

    /**
     * TODO: See {@url https://jira.eng.vmware.com/browse/VSYM-12657} for ARN -> IAM & vice-versa
     * update support.
     */
    @Test
    public void testEndpointUpdateCredentialsIamToArnNotSupported() throws Throwable {
        EndpointCreationTaskState task = createMockAwsEndpoint(this.host, this.peerUri, this.orgLink,
                "my-endpoint", "foo", "bar",
                null, null, null);
        String endpointLink = task.endpointLink;

        EndpointUpdateTaskState updateTask = new EndpointUpdateTaskState();
        updateTask.endpointLink = endpointLink;
        updateTask.name = "my-endpoint-update";
        updateTask.credentials = createAwsCredentialsArn("arn:aws:iam::123456789123:role/test-role", "external-id");
        updateTask.isMock = true;
        updateTask.taskInfo = TaskState.createDirect();

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointUpdateTaskService.FACTORY_LINK))
                .setBody(updateTask));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        EndpointUpdateTaskState body = response.getBody(EndpointUpdateTaskState.class);
        assertEquals(SubStage.ERROR, body.subStage);
        assertEquals(TaskStage.FAILED, body.taskInfo.stage);
        assertEquals("'accessKeyId' is required", body.taskInfo.failure.message);
    }

    /**
     * TODO: See {@url https://jira.eng.vmware.com/browse/VSYM-12657} for ARN -> IAM & vice-versa
     * update support.
     */
    @Test
    public void testEndpointUpdateCredentialsArnToIamNotSupported() throws Throwable {
        EndpointCreationTaskState task = createMockAwsEndpointArn(this.host, this.peerUri, this.orgLink,
                "my-endpoint", "arn:aws:iam::123456789123:role/test-role", "external-id",
                null, null, null);
        String endpointLink = task.endpointLink;

        EndpointUpdateTaskState updateTask = new EndpointUpdateTaskState();
        updateTask.endpointLink = endpointLink;
        updateTask.name = "my-endpoint-update";
        updateTask.credentials = createAwsCredentials("update-bar", "update-foo");
        updateTask.isMock = true;
        updateTask.taskInfo = TaskState.createDirect();

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointUpdateTaskService.FACTORY_LINK))
                .setBody(updateTask));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        EndpointUpdateTaskState body = response.getBody(EndpointUpdateTaskState.class);
        assertEquals(SubStage.ERROR, body.subStage);
        assertEquals(TaskStage.FAILED, body.taskInfo.stage);
        assertEquals("'arn' is required", body.taskInfo.failure.message);
    }

    @Test
    public void testUpdateInvalidOwner() throws Throwable {
        EndpointCreationTaskState task = createMockAwsEndpoint(this.host, this.peerUri, this.orgLink,
                "my-endpoint", "foo", "bar",
                null, null, null);
        String endpointLink = task.endpointLink;

        EndpointUpdateTaskState updateTask = new EndpointUpdateTaskState();
        updateTask.endpointLink = endpointLink;
        updateTask.name = "my-endpoint-update";
        updateTask.endpointProperties = new HashMap<>();
        updateTask.endpointProperties.put(PRIVATE_KEYID_KEY, "this-update-will-be-overridden");
        updateTask.credentials = createAwsCredentials("update-bar", "update-foo");
        updateTask.isMock = true;
        updateTask.taskInfo = TaskState.createDirect();
        updateTask.ownerUpdates = Collections.singletonList(CollectionStringFieldUpdate.create(
                UpdateAction.ADD, USER_4_ID));

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointUpdateTaskService.FACTORY_LINK))
                .setBody(updateTask));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        EndpointUpdateTaskState body = response.getBody(EndpointUpdateTaskState.class);
        assertEquals(SubStage.ERROR, body.subStage);
        assertEquals(TaskStage.FAILED, body.taskInfo.stage);

        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, body.taskInfo.failure.statusCode);
    }

    @Test
    public void testUpdateNonExistantOwner() {
        EndpointCreationTaskState task = createMockAwsEndpoint(this.host, this.peerUri, this.orgLink,
                "my-endpoint", "foo", "bar",
                null, null, null);
        String endpointLink = task.endpointLink;

        EndpointUpdateTaskState updateTask = new EndpointUpdateTaskState();
        updateTask.endpointLink = endpointLink;
        updateTask.name = "my-endpoint-update";
        updateTask.endpointProperties = new HashMap<>();
        updateTask.endpointProperties.put(PRIVATE_KEYID_KEY, "this-update-will-be-overridden");
        updateTask.credentials = createAwsCredentials("update-bar", "update-foo");
        updateTask.isMock = true;
        updateTask.taskInfo = TaskState.createDirect();
        updateTask.ownerUpdates = Collections.singletonList(CollectionStringFieldUpdate.create(
                UpdateAction.ADD, USER_4_ID));

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointUpdateTaskService.FACTORY_LINK))
                .setBody(updateTask));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        EndpointUpdateTaskState body = response.getBody(EndpointUpdateTaskState.class);
        assertEquals(SubStage.ERROR, body.subStage);
        assertEquals(TaskStage.FAILED, body.taskInfo.stage);

        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, body.taskInfo.failure.statusCode);
    }

    @Test
    public void testUpdateWithAddRemoveTags() throws Throwable {
        // create endpoint with no tags
        EndpointCreationTaskState endpointTask = createMockAwsEndpoint(this.host, this.peerUri, this.orgLink,
                "endpoint-a", "foo", "bar", null,
                null, null);

        // Update endpoint by adding 2 tags
        EndpointUpdateTaskState updateTask = new EndpointUpdateTaskState();
        updateTask.endpointLink = endpointTask.endpointLink;
        updateTask.name = "endpoint-a-updated";
        updateTask.description = "endpoint-a-updated-description";
        updateTask.tagUpdates = new HashSet<>();
        updateTask.tagUpdates.add(new TagFieldUpdate(ADD, "key1", "value1"));
        updateTask.tagUpdates.add(new TagFieldUpdate(ADD, "key2", "value2"));
        updateTask.tagUpdates.add(new TagFieldUpdate(ADD, "key3", "value3"));
        updateTask.taskInfo = TaskState.createDirect();

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointUpdateTaskService.FACTORY_LINK))
                .setBody(updateTask));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        EndpointUpdateTaskState body = response.getBody(EndpointUpdateTaskState.class);
        assertEquals(SubStage.SUCCESS, body.subStage);
        assertEquals(TaskStage.FINISHED, body.taskInfo.stage);

        // Validate 3 tag added
        Operation endpointOp = this.host.waitForResponse(Operation.createGet(
                UriUtils.buildUri(this.peerUri, body.endpointLink)));
        assertEquals(Operation.STATUS_CODE_OK, endpointOp.getStatusCode());
        EndpointState endpoint = endpointOp.getBody(EndpointState.class);
        assertNotNull(endpoint);
        assertEquals("endpoint-a-updated", endpoint.name);
        assertEquals("endpoint-a-updated-description", endpoint.desc);
        assertNotNull(endpoint.tagLinks);
        assertEquals(3, endpoint.tagLinks.size());

        this.host.setSystemAuthorizationContext();
        ServiceDocumentQueryResult tagResults = SymphonyCommonTestUtils.queryDocuments(this.host,
                this.peerUri, Utils.buildKind(TagState.class), 3);
        assertNotNull(tagResults);
        assertEquals(3, tagResults.documentCount.intValue());

        this.host.assumeIdentity(this.userLink);
        // Update endpoint by removing two existing tags
        updateTask = new EndpointUpdateTaskState();
        updateTask.tagUpdates = new HashSet<>();
        updateTask.endpointLink = endpointTask.endpointLink;
        updateTask.tagUpdates.add(new TagFieldUpdate(REMOVE, "key1", "value1"));
        updateTask.tagUpdates.add(new TagFieldUpdate(REMOVE, "key2", "value2"));
        updateTask.taskInfo = TaskState.createDirect();

        response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointUpdateTaskService.FACTORY_LINK))
                .setBody(updateTask));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        body = response.getBody(EndpointUpdateTaskState.class);
        assertEquals(SubStage.SUCCESS, body.subStage);
        assertEquals(TaskStage.FINISHED, body.taskInfo.stage);

        // Validate 2 tags removed
        endpointOp = this.host.waitForResponse(Operation.createGet(
                UriUtils.buildUri(this.peerUri, body.endpointLink)));
        assertEquals(Operation.STATUS_CODE_OK, endpointOp.getStatusCode());
        endpoint = endpointOp.getBody(EndpointState.class);
        assertNotNull(endpoint);
        assertEquals("endpoint-a-updated", endpoint.name);
        assertNotNull(endpoint.tagLinks);
        assertEquals(1, endpoint.tagLinks.size());

        // verify only 3 tags exist in index
        this.host.setSystemAuthorizationContext();
        tagResults = SymphonyCommonTestUtils.queryDocuments(this.host, this.peerUri,
                Utils.buildKind(TagState.class), 3);
        assertNotNull(tagResults);
        assertEquals(3, tagResults.documentCount.intValue());

        this.host.assumeIdentity(this.userLink);
        // Update endpoint by adding two more tags and removing the existing one
        updateTask = new EndpointUpdateTaskState();
        updateTask.endpointLink = endpointTask.endpointLink;
        updateTask.tagUpdates = new HashSet<>();
        updateTask.tagUpdates.add(new TagFieldUpdate(ADD, "key4", "value4"));
        updateTask.tagUpdates.add(new TagFieldUpdate(ADD, "key5", "value5"));
        updateTask.tagUpdates.add(new TagFieldUpdate(REMOVE, "key3", "value3"));
        updateTask.taskInfo = TaskState.createDirect();

        response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointUpdateTaskService.FACTORY_LINK))
                .setBody(updateTask));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        body = response.getBody(EndpointUpdateTaskState.class);
        assertEquals(SubStage.SUCCESS, body.subStage);
        assertEquals(TaskStage.FINISHED, body.taskInfo.stage);

        // Validate 2 tag added and 1 tags removed
        endpointOp = this.host.waitForResponse(Operation.createGet(
                UriUtils.buildUri(this.peerUri, body.endpointLink)));
        assertEquals(Operation.STATUS_CODE_OK, endpointOp.getStatusCode());
        endpoint = endpointOp.getBody(EndpointState.class);
        assertNotNull(endpoint);
        assertEquals("endpoint-a-updated", endpoint.name);
        assertNotNull(endpoint.tagLinks);
        assertEquals(2, endpoint.tagLinks.size());

        // verify only 5 tags exist in index
        this.host.setSystemAuthorizationContext();
        tagResults = SymphonyCommonTestUtils.queryDocuments(this.host, this.peerUri,
                 Utils.buildKind(TagState.class), 5);
        assertNotNull(tagResults);
        assertEquals(5, tagResults.documentCount.intValue());

        this.host.assumeIdentity(this.userLink);
        // update endpoint by adding and deleting the same tag - expected to get completion without
        // any change in the tags
        updateTask = new EndpointUpdateTaskState();
        updateTask.endpointLink = endpointTask.endpointLink;
        updateTask.tagUpdates = new HashSet<>();
        updateTask.tagUpdates.add(new TagFieldUpdate(ADD, "key3", "value3"));
        updateTask.tagUpdates.add(new TagFieldUpdate(REMOVE, "key3", "value3"));
        updateTask.taskInfo = TaskState.createDirect();

        response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointUpdateTaskService.FACTORY_LINK))
                .setBody(updateTask));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        body = response.getBody(EndpointUpdateTaskState.class);
        assertEquals(SubStage.SUCCESS, body.subStage);
        assertEquals(TaskStage.FINISHED, body.taskInfo.stage);

        // Validate 2 tags are in place
        endpointOp = this.host.waitForResponse(Operation.createGet(
                UriUtils.buildUri(this.peerUri, body.endpointLink)));
        assertEquals(Operation.STATUS_CODE_OK, endpointOp.getStatusCode());
        endpoint = endpointOp.getBody(EndpointState.class);
        assertNotNull(endpoint);
        assertEquals("endpoint-a-updated", endpoint.name);
        assertNotNull(endpoint.tagLinks);
        assertEquals(2, endpoint.tagLinks.size());

        // create a tag without the USER_DEFINED origin and verify the tag gets updated to have the
        // USER_DEFINED origin after the endpoint is created
        TagState tag = TagsUtil.newTagState("key6", "value6",
                EnumSet.of(TagState.TagOrigin.DISCOVERED), Arrays.asList(this.projectLink));

        response = host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, TagService.FACTORY_LINK))
                .setBody(tag));
        tag = response.getBody(TagState.class);
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        // update endpoint by only adding tag, that already exists in index as DISCOVERED
        updateTask = new EndpointUpdateTaskState();
        updateTask.endpointLink = endpointTask.endpointLink;
        updateTask.tagUpdates = new HashSet<>();
        updateTask.tagUpdates.add(new TagFieldUpdate(ADD, "key6", "value6"));
        updateTask.taskInfo = TaskState.createDirect();

        response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointUpdateTaskService.FACTORY_LINK))
                .setBody(updateTask));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        body = response.getBody(EndpointUpdateTaskState.class);
        assertEquals(SubStage.SUCCESS, body.subStage);
        assertEquals(TaskStage.FINISHED, body.taskInfo.stage);

        // Validate 1 tag added (total of 3 tags)
        endpointOp = this.host.waitForResponse(Operation.createGet(
                UriUtils.buildUri(this.peerUri, body.endpointLink)));
        assertEquals(Operation.STATUS_CODE_OK, endpointOp.getStatusCode());
        endpoint = endpointOp.getBody(EndpointState.class);
        assertNotNull(endpoint);
        assertEquals("endpoint-a-updated", endpoint.name);
        assertNotNull(endpoint.tagLinks);
        assertEquals(3, endpoint.tagLinks.size());
        // verify there are only 6 tags in the index and all of them have the option USER_DEFINED
        // but only one of them has the option DISCOVERED
        for (String link : endpoint.tagLinks) {
            response = this.host.waitForResponse(Operation.createGet(
                    UriUtils.buildUri(this.peerUri, link)));
            TagState updatedTag = response.getBody(TagState.class);
            if (updatedTag.key.equals("key6") && updatedTag.value.equals("value6")) {
                assertTrue(updatedTag.origins.contains(TagState.TagOrigin.USER_DEFINED)
                        && updatedTag.origins.contains(TagState.TagOrigin.DISCOVERED));
            } else {
                assertTrue(updatedTag.origins.contains(TagState.TagOrigin.USER_DEFINED));
                assertEquals(1, updatedTag.origins.size());
            }
        }

        this.host.setSystemAuthorizationContext();
        tagResults = SymphonyCommonTestUtils.queryDocuments(this.host, this.peerUri,
                Utils.buildKind(TagState.class), 6);
        assertNotNull(tagResults);
        assertEquals(6, tagResults.documentCount.intValue());
    }

    /**
     * Scenario:
     *  Pre create tagA and tagB in Org1
     *  Create endpoint in Org2
     *  Update endpoint to add tagA, tagB and tagC
     *
     *  Verify: 5 tags exist in the system, 2 in each Org1 and 3 in Org2
     */
    @Test
    public void testTagUpdateInDifferentOrgs() throws Throwable {
        // create tags in same tenant
        TagState tagA = TagsUtil.newTagState("key1", "value1",
                EnumSet.of(TagState.TagOrigin.DISCOVERED), Arrays.asList(this.projectLink));
        Operation response = host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, TagService.FACTORY_LINK))
                .setBody(tagA));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        TagState tagB = TagsUtil.newTagState("key2", "value2",
                EnumSet.of(TagState.TagOrigin.DISCOVERED), Arrays.asList(this.projectLink));
        response = host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, TagService.FACTORY_LINK))
                .setBody(tagB));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        host.assumeIdentity(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_4_ID)), null);
        // create endpoint with no tags
        EndpointCreationTaskState endpointTask = createMockAwsEndpoint(this.host, this.peerUri,
                this.orgLink2,"endpoint-a", "foo", "bar", null, null, null);

        // Update endpoint by adding 2 tags
        EndpointUpdateTaskState updateTask = new EndpointUpdateTaskState();
        updateTask.endpointLink = endpointTask.endpointLink;
        updateTask.tagUpdates = new HashSet<>();
        updateTask.tagUpdates.add(new TagFieldUpdate(ADD, "key1", "value1"));
        updateTask.tagUpdates.add(new TagFieldUpdate(ADD, "key2", "value2"));
        updateTask.tagUpdates.add(new TagFieldUpdate(ADD, "key3", "value3"));
        updateTask.taskInfo = TaskState.createDirect();

        response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointUpdateTaskService.FACTORY_LINK))
                .setBody(updateTask));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        EndpointUpdateTaskState body = response.getBody(EndpointUpdateTaskState.class);
        assertEquals(SubStage.SUCCESS, body.subStage);
        assertEquals(TaskStage.FINISHED, body.taskInfo.stage);

        // Validate 3 tag added
        Operation endpointOp = this.host.waitForResponse(Operation.createGet(
                UriUtils.buildUri(this.peerUri, body.endpointLink)));
        assertEquals(Operation.STATUS_CODE_OK, endpointOp.getStatusCode());
        EndpointState endpoint = endpointOp.getBody(EndpointState.class);
        assertNotNull(endpoint);
        assertNotNull(endpoint.tagLinks);
        assertEquals(3, endpoint.tagLinks.size());

        this.host.setSystemAuthorizationContext();
        ServiceDocumentQueryResult tagResults = SymphonyCommonTestUtils.queryDocuments(this.host,
                this.peerUri, Utils.buildKind(TagState.class), 5);
        assertNotNull(tagResults);
        assertEquals(5, tagResults.documentCount.intValue());

        int org1Tags = 0;
        int org2Tags = 0;
        TagState tag;
        for (String link : tagResults.documents.keySet()) {
            tag = Utils.fromJson(tagResults.documents.get(link),
                    TagState.class);
            org1Tags = (tag.tenantLinks.contains(this.projectLink)) ? org1Tags + 1 : org1Tags;
            org2Tags = (tag.tenantLinks.contains(this.projectLink2)) ? org2Tags + 1 : org2Tags;
        }
        // there should be 2 tags in org1 and 3 in org2
        assertEquals(2, org1Tags);
        assertEquals(3, org2Tags);
    }

    private EndpointState createEndpoint(String adapterType) {
        ResourcePoolService.ResourcePoolState rp = new ResourcePoolService.ResourcePoolState();
        rp.name = UUID.randomUUID().toString();
        rp.id = rp.name;
        rp.documentSelfLink = rp.id;
        rp.tenantLinks = Collections.singletonList(this.projectLink);
        this.host.sendAndWaitExpectSuccess(Operation.createPost(
                UriUtils.buildUri(this.peerUri, ResourcePoolService.FACTORY_LINK))
                .setBody(rp));

        ComputeDescriptionService.ComputeDescription hostDesc = new ComputeDescriptionService.ComputeDescription();
        hostDesc.id = UUID.randomUUID().toString();
        hostDesc.name = hostDesc.id;
        hostDesc.documentSelfLink = hostDesc.id;
        hostDesc.supportedChildren = new ArrayList<>();
        hostDesc.supportedChildren.add(ComputeDescriptionService.ComputeDescription.ComputeType.VM_GUEST.name());
        hostDesc.instanceAdapterReference = UriUtils.buildUri("http://mock-instance-adapter");
        hostDesc.enumerationAdapterReference = UriUtils.buildUri(
                this.peerUri, MockEnumerationAdapter.SELF_LINK);
        hostDesc.statsAdapterReference = UriUtils.buildUri(
                this.peerUri, TestDataInitializationTaskService.MockStatsAdapter.SELF_LINK);
        hostDesc.tenantLinks = Collections.singletonList(this.projectLink);
        this.host.sendAndWaitExpectSuccess(Operation.createPost(
                UriUtils.buildUri(this.peerUri, ComputeDescriptionService.FACTORY_LINK))
                .setBody(hostDesc));

        ComputeService.ComputeState computeHost = new ComputeService.ComputeState();
        computeHost.id = UUID.randomUUID().toString();
        computeHost.documentSelfLink = computeHost.id;
        computeHost.descriptionLink = UriUtils.buildUriPath(
                ComputeDescriptionService.FACTORY_LINK, hostDesc.id);
        computeHost.resourcePoolLink = rp.documentSelfLink;
        computeHost.tenantLinks = Collections.singletonList(this.projectLink);
        computeHost.adapterManagementReference = UriUtils.buildUri("http://www.foo.com");
        this.host.sendAndWaitExpectSuccess(Operation.createPost(
                UriUtils.buildUri(this.peerUri, ComputeService.FACTORY_LINK))
                .setBody(computeHost));

        AuthCredentialsServiceState auth = new AuthCredentialsServiceState();
        auth.documentSelfLink = UUID.randomUUID().toString();
        auth.type = "PublicKey";
        auth.privateKeyId = "foo";
        auth.privateKey = "bar";
        this.host.setSystemAuthorizationContext();
        this.host.sendAndWaitExpectSuccess(Operation.createPost(
                UriUtils.buildUri(this.peerUri, AuthCredentialsService.FACTORY_LINK))
                .setBody(auth));

        EndpointState endpoint = new EndpointState();
        endpoint.computeLink = UriUtils
                .buildUriPath(ComputeService.FACTORY_LINK, computeHost.documentSelfLink);
        endpoint.computeDescriptionLink = computeHost.descriptionLink;
        endpoint.endpointType = adapterType;
        endpoint.name = "test-endpoint";
        endpoint.id = UUID.randomUUID().toString();
        endpoint.documentSelfLink = endpoint.id;
        endpoint.authCredentialsLink = UriUtils
                .buildUriPath(AuthCredentialsService.FACTORY_LINK, auth.documentSelfLink);

        endpoint.resourcePoolLink = UriUtils
                .buildUriPath(ResourcePoolService.FACTORY_LINK, rp.documentSelfLink);
        endpoint.endpointProperties = new HashMap<>();
        endpoint.endpointProperties.put(REGION_KEY, "test-regionId");
        endpoint.endpointProperties.put(PRIVATE_KEY_KEY, "accessKey");
        endpoint.endpointProperties.put(PRIVATE_KEYID_KEY, "secretKey");
        endpoint.tenantLinks = Collections.singletonList(this.projectLink);

        this.host.sendAndWaitExpectSuccess(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointService.FACTORY_LINK))
                .setBody(endpoint));

        return endpoint;
    }

    public static class MockEnumerationAdapter extends StatelessService {
        public static String SELF_LINK = "/mock-enumeration-adapter";

        private AtomicReference<TestContext> ctxRef;

        public MockEnumerationAdapter(AtomicReference<TestContext> ctxRef) {
            this.ctxRef = ctxRef;
        }

        @Override
        public void handlePatch(Operation op) {
            op.complete();
            TestContext ctx = this.ctxRef.get();

            ComputeEnumerateResourceRequest request = op.getBody(ComputeEnumerateResourceRequest.class);
            TaskManager taskManager = new TaskManager(this, request.taskReference, null);
            taskManager.finishTask();
            // closing the passed test context
            ctx.complete();
        }
    }

    private void verifyError(Operation op, ErrorCode errorCode) {
        ServiceErrorResponse error = op.getBody(ServiceErrorResponse.class);
        assertNotNull(error);
        assertEquals(String.valueOf(errorCode.getErrorCode()), error.messageId);
    }
}
