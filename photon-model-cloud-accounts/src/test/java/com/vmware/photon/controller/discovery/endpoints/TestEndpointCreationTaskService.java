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
import static org.junit.Assert.assertTrue;

import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.CREDENTIALS_REQUIRED;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_NAME_REQUIRED;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_ORG_LINK_REQUIRED;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_TAG_NULL_EMPTY;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_TYPE_REQUIRED;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_WITH_S3BUCKETNAME_ALREADY_EXISTS;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.S3_BUCKET_PERMISSIONS_ERROR;
import static com.vmware.photon.controller.discovery.endpoints.CostUsageReportCreationUtils.checkIfReportAlreadyExists;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_COST_USAGE_REPORT_SERVICE_REGION;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BUCKET_PREFIX;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_CI_COSTUSAGE_REPORT_NAME;
import static com.vmware.photon.controller.discovery.endpoints.TestEndpointUtils.createAwsCredentials;
import static com.vmware.photon.controller.discovery.endpoints.TestEndpointUtils.createAwsCredentialsArn;
import static com.vmware.photon.controller.model.UriPaths.CLOUD_ACCOUNT_API_SERVICE;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEYID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.REGION_KEY;

import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.discovery.common.ErrorCode;
import com.vmware.photon.controller.discovery.common.PhotonControllerCloudAccountUtils;
import com.vmware.photon.controller.discovery.common.SymphonyCommonTestUtils;
import com.vmware.photon.controller.discovery.common.TagViewState;
import com.vmware.photon.controller.discovery.common.services.OrganizationService;
import com.vmware.photon.controller.discovery.common.services.UserService;
import com.vmware.photon.controller.discovery.common.services.UserService.UserState;
import com.vmware.photon.controller.discovery.endpoints.EndpointCreationTaskService.EndpointCreationTaskState;
import com.vmware.photon.controller.discovery.endpoints.EndpointCreationTaskService.SubStage;
import com.vmware.photon.controller.discovery.onboarding.OnBoardingTestUtils;
import com.vmware.photon.controller.discovery.onboarding.OnboardingServices;
import com.vmware.photon.controller.discovery.onboarding.OnboardingUtils;
import com.vmware.photon.controller.discovery.onboarding.user.UserUpdateService;
import com.vmware.photon.controller.discovery.onboarding.user.UserUpdateService.UserUpdateRequest;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSAdapters;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.adapters.util.TagsUtil;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.photon.controller.model.resources.TagService.TagState.TagOrigin;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.xenon.common.BasicTestCase;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.ResourceGroupService;
import com.vmware.xenon.services.common.RoleService;
import com.vmware.xenon.services.common.UserGroupService;

public class TestEndpointCreationTaskService extends BasicTestCase {
    public int numNodes = 3;

    private static final String USER_1_ID = "user1@org-1.com";
    private static final String USER_1_PASSWORD = "passwordforuser1";

    private static final String USER_2_ID = "user2@org-1.com";
    private static final String USER_2_PASSWORD = "passwordforuser2";

    private static final String USER_3_ID = "user3@org-2.com";
    private static final String USER_3_PASSWORD = "passwordforuser3";

    private static final String USER_4_ID = "user4@org-2.com";
    private static final String USER_4_PASSWORD = "passwordforuser4";

    private static final String ORG_1_ID = "org-1";
    private static final String PROJECT_1_ID = OnboardingUtils.getDefaultProjectName();

    private static final String ORG_2_ID = "org-2";
    private static final String PROJECT_2_ID = OnboardingUtils.getDefaultProjectName();

    private static final String ENDPOINT_NAME_A = "my-endpoint-a";
    private static final String ENDPOINT_NAME_B = "my-endpoint-b";
    private static final String PRIVATE_KEY = "foo";
    private static final String PRIVATE_KEY_ID = "bar";

    // AWS access key
    private String accessKey = "accessKey";
    // AWS secret key
    private String secretKey = "secretKey";
    // AWS S3 bucketName
    private String s3bucketName = "s3bucketName";
    private static String s3bucketPrefix = "s3Prefix";
    private static String costAndUsageReport = "testReport";
    private static boolean isMock = true;

    private URI peerUri;
    private String userLink;
    private String orgLink;
    private String orgLink2;
    private String projectLink;

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
        factories.add(EndpointAllocationTaskService.FACTORY_LINK);
        factories.add(TagService.FACTORY_LINK);
        SymphonyCommonTestUtils
                .waitForFactoryAvailability(this.host, this.host.getPeerHostUri(), factories);

        this.peerUri = this.host.getPeerHostUri();

        /*
         * Four different users:
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
        OnBoardingTestUtils.setupProject(this.host, this.peerUri, PROJECT_2_ID,
                ORG_2_ID, USER_4_ID, USER_4_PASSWORD);
        this.orgLink2 = UriUtils.buildUriPath(OrganizationService.FACTORY_LINK,
                Utils.computeHash(ORG_2_ID));
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
        EndpointCreationTaskState task = new EndpointCreationTaskState();
        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointCreationTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, ENDPOINT_NAME_REQUIRED);

        task = new EndpointCreationTaskState();
        task.name = "foobar";
        response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointCreationTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, ENDPOINT_TYPE_REQUIRED);

        task = new EndpointCreationTaskState();
        task.name = "foobar";
        task.type = EndpointType.azure.name();
        response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointCreationTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, ENDPOINT_ORG_LINK_REQUIRED);

        task = new EndpointCreationTaskState();
        task.name = "foobar";
        task.type = EndpointType.azure.name();
        task.orgLink = "dummyOrg";
        response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointCreationTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, CREDENTIALS_REQUIRED);

        task = new EndpointCreationTaskState();
        task.name = "foobar";
        task.type = EndpointType.azure.name();
        task.orgLink = "dummyOrg";
        task.credentials = createAwsCredentials("bar", "foo");
        Set<TagViewState> tags = new HashSet<>();
        TagViewState tagA = new TagViewState("", "dummyVal");
        Collections.addAll(tags, tagA);
        task.tags = new HashSet<>();
        task.tags.addAll(tags);
        response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointCreationTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, ENDPOINT_TAG_NULL_EMPTY);

        tags = new HashSet<>();
        tagA = new TagViewState("dummyKeyB", "");
        Collections.addAll(tags, tagA);
        task.tags = new HashSet<>();
        task.tags.addAll(tags);
        response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointCreationTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, ENDPOINT_TAG_NULL_EMPTY);

        tags = new HashSet<>();
        tagA = new TagViewState(null , "val");
        Collections.addAll(tags, tagA);
        task.tags = new HashSet<>();
        task.tags.addAll(tags);
        response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointCreationTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, ENDPOINT_TAG_NULL_EMPTY);

        tags = new HashSet<>();
        tagA = new TagViewState("key", null);
        Collections.addAll(tags, tagA);
        task.tags = new HashSet<>();
        task.tags.addAll(tags);
        response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointCreationTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, ENDPOINT_TAG_NULL_EMPTY);
    }

    @Test
    public void testInvalidOrg() {
        EndpointCreationTaskState task = new EndpointCreationTaskState();
        task.name = "my-endpoint";
        task.orgLink = "foobar";
        task.type = EndpointType.aws.name();
        task.credentials = createAwsCredentials("bar", "foo");
        task.taskInfo = TaskState.createDirect();

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointCreationTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        EndpointCreationTaskState body = response.getBody(EndpointCreationTaskState.class);
        assertEquals(SubStage.ERROR, body.subStage);
        assertEquals(TaskStage.FAILED, body.taskInfo.stage);
        assertEquals(Operation.STATUS_CODE_FORBIDDEN, body.taskInfo.failure.statusCode);
    }

    @Test
    public void testInvalidCredentials() {
        EndpointCreationTaskState task = new EndpointCreationTaskState();
        task.name = "my-endpoint";
        task.orgLink = this.orgLink;
        task.type = EndpointType.aws.name();
        task.credentials = createAwsCredentials("bar", "foo");
        task.taskInfo = TaskState.createDirect();

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointCreationTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        EndpointCreationTaskState body = response.getBody(EndpointCreationTaskState.class);
        assertEquals(SubStage.ERROR, body.subStage);
        assertEquals(TaskStage.FAILED, body.taskInfo.stage);
    }

    @Test
    public void testDuplicateS3Bucket() throws Throwable {
        this.host.assumeIdentity(this.userLink);
        //When you create an endpoint, authCredential state is also created
        EndpointCreationTaskService.EndpointCreationTaskState endpointTaskState =
                createMockAwsEndpoint(this.host, this.peerUri, this.orgLink,
                        "my-endpoint", "foo", "bar",
                        "testS3Bucket", null, null);

        EndpointCreationTaskState task = new EndpointCreationTaskState();
        task.name = "my-endpoint";
        task.orgLink = this.orgLink;
        task.type = EndpointType.aws.name();
        task.credentials = createAwsCredentials("bar1", "foo1");
        Map<String, String> customProperties = new HashMap<>();
        customProperties.put(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET, "testS3Bucket");
        task.customProperties = customProperties;
        task.taskInfo = TaskState.createDirect();

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointCreationTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        EndpointCreationTaskState body = response.getBody(EndpointCreationTaskState.class);
        assertEquals(SubStage.ERROR, body.subStage);
        assertEquals(TaskStage.FAILED, body.taskInfo.stage);

        String endpointLink = UriUtils.getLastPathSegment(endpointTaskState.endpointLink);
        String expectedCloudAccountLink = UriUtils.buildUriPath(
                CLOUD_ACCOUNT_API_SERVICE, endpointLink);
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, body.taskInfo.failure.statusCode);
        assertEquals(ENDPOINT_WITH_S3BUCKETNAME_ALREADY_EXISTS.getErrorCode(),
                Integer.parseInt(body.taskInfo.failure.messageId));
        assertEquals(expectedCloudAccountLink, body.taskInfo.failure.message);
    }

    @Test
    public void testInvalidAccessPermissionsOnS3Bucket() throws Throwable {
        this.host.assumeIdentity(this.userLink);

        EndpointCreationTaskState task = new EndpointCreationTaskState();
        task.name = "my-endpoint";
        task.orgLink = this.orgLink;
        task.type = EndpointType.aws.name();
        task.credentials = createAwsCredentials(this.accessKey, this.secretKey);
        Map<String, String> customProperties = new HashMap<>();
        customProperties.put(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET, this.s3bucketName +
                "invalid");
        task.customProperties = customProperties;
        task.isMock = isMock;
        task.taskInfo = TaskState.createDirect();

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointCreationTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        if (!isMock) {
            EndpointCreationTaskState body = response.getBody(EndpointCreationTaskState.class);
            assertEquals(SubStage.ERROR, body.subStage);
            assertEquals(TaskStage.FAILED, body.taskInfo.stage);

            assertEquals(Operation.STATUS_CODE_BAD_REQUEST, body.taskInfo.failure.statusCode);
            assertEquals(S3_BUCKET_PERMISSIONS_ERROR.getErrorCode(),
                    Integer.parseInt(body.taskInfo.failure.messageId));

            // Error message from AWS will be something like this - The specified bucket does not
            // exist (Service: Amazon S3; Status Code: 404; Error
            // Code: NoSuchBucket; Request ID: XXXXXXXXXXX)
            assertNotNull(body.taskInfo.failure.message);
        }
    }

    @Test
    public void testCostAndUsageReport() throws GeneralSecurityException {

        this.host.assumeIdentity(this.userLink);

        EndpointCreationTaskState task = createMockAwsEndpointCostUsage(this.host, this.peerUri, this.orgLink,
                ENDPOINT_NAME_A, this.secretKey, this.accessKey, this.s3bucketName, null, null,
                s3bucketPrefix, costAndUsageReport);

        Operation endpointOp = this.host.waitForResponse(Operation.createGet(
                UriUtils.buildUri(this.peerUri, task.endpointLink)));
        assertEquals(Operation.STATUS_CODE_OK, endpointOp.getStatusCode());
        EndpointState endpoint = endpointOp.getBody(EndpointState.class);
        assertNotNull(endpoint);
        assertNotNull(endpoint.endpointProperties);

        if (!isMock) {
            Credentials awsCredentials = createAwsCredentials(this.accessKey, this.secretKey);
            Boolean ifReportExists = checkIfReportAlreadyExists(awsCredentials, this.s3bucketName,
                    s3bucketPrefix, costAndUsageReport);
            assertEquals(ifReportExists, true);
        }
    }

    @Test
    public void testNullPrivateKeyId() {
        EndpointCreationTaskState task = new EndpointCreationTaskState();
        task.name = "my-endpoint";
        task.orgLink = this.orgLink;
        task.type = EndpointType.aws.name();
        task.credentials = createAwsCredentials(null, "foo");
        task.taskInfo = TaskState.createDirect();

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointCreationTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, CREDENTIALS_REQUIRED);
    }

    @Test
    public void testNullPrivateKey() {
        EndpointCreationTaskState task = new EndpointCreationTaskState();
        task.name = "my-endpoint";
        task.orgLink = this.orgLink;
        task.type = EndpointType.aws.name();
        task.credentials = createAwsCredentials("bar", null);
        task.taskInfo = TaskState.createDirect();

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointCreationTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, CREDENTIALS_REQUIRED);
    }

    public static EndpointCreationTaskState createMockAwsEndpointArn(VerificationHost host, URI peerUri,
            String orgLink, String endpointName, String arn, String externalId,
            String s3bucketName, Set<TagViewState> tags, Set<String> delegatedOwnerEmails) {
        EndpointCreationTaskState task = createEndpointCreationTaskStateArn(orgLink, endpointName,
                arn, externalId, s3bucketName, tags, delegatedOwnerEmails);
        return createMockAwsEndpoint(host, peerUri, task);
    }

    public static EndpointCreationTaskState createMockAwsEndpoint(VerificationHost host, URI peerUri,
            String orgLink, String endpointName, String secretAccessKey, String accessKeyId,
            String s3bucketName, Set<TagViewState> tags, Set<String> delegatedOwnerEmails) {
        EndpointCreationTaskState task = createEndpointCreationTaskState(orgLink, endpointName,
                secretAccessKey, accessKeyId, s3bucketName, tags, delegatedOwnerEmails);
        return createMockAwsEndpoint(host, peerUri, task);
    }

    public static EndpointCreationTaskState createMockAwsEndpointCostUsage(VerificationHost host, URI peerUri,
            String orgLink, String endpointName, String secretAccessKey, String accessKeyId,
            String s3bucketName, Set<TagViewState> tags, Set<String> delegatedOwnerEmails, String s3Prefix,
            String costAndUsageReport) {

        EndpointCreationTaskState task = createEndpointCreationCostUsageTaskState(orgLink, endpointName,
                secretAccessKey, accessKeyId, s3bucketName, tags, delegatedOwnerEmails,
                null, null, s3Prefix, costAndUsageReport);
        return createMockAwsEndpoint(host, peerUri, task);
    }

    public static EndpointCreationTaskState createEndpointCreationCostUsageTaskState(String orgLink,
             String endpointName, String secretAccessKey, String accessKeyId, String s3bucketName,
             Set<TagViewState> tags, Set<String> delegatedOwnerEmails, String arn, String externalId,
             String prefix, String costAndUsageReport) {

        EndpointCreationTaskState task = new EndpointCreationTaskState();
        task.name = endpointName;
        task.orgLink = orgLink;
        task.type = EndpointType.aws.name();
        Map<String, String> endpointProperties = new HashMap<>();
        endpointProperties.put(REGION_KEY, ENDPOINT_COST_USAGE_REPORT_SERVICE_REGION.getName());
        task.endpointProperties = endpointProperties;

        Map<String, String> customProperties = new HashMap<>();
        customProperties.put(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET, s3bucketName);
        customProperties.put(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BUCKET_PREFIX, prefix);
        customProperties.put(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_COSTUSAGE_REPORT_NAME, costAndUsageReport);
        task.customProperties = customProperties;

        if (arn != null) {
            task.credentials = createAwsCredentialsArn(arn, externalId);
        } else {
            task.credentials = createAwsCredentials(accessKeyId, secretAccessKey);
        }
        task.ownerEmails = delegatedOwnerEmails;
        task.tags = tags;
        task.isMock = isMock;
        task.taskInfo = TaskState.createDirect();
        return task;
    }

    public static EndpointCreationTaskState createMockAwsEndpoint(VerificationHost host,
            URI peerUri, EndpointCreationTaskState task) {
        Operation response = host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(peerUri, EndpointCreationTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        EndpointCreationTaskState body = response.getBody(EndpointCreationTaskState.class);
        assertEquals(SubStage.SUCCESS, body.subStage);
        assertEquals(TaskStage.FINISHED, body.taskInfo.stage);
        assertNotNull(body.endpointLink);
        return body;
    }

    public static EndpointCreationTaskState createEndpointCreationTaskStateArn(String orgLink,
            String endpointName, String arn, String externalId, String s3bucketName, Set<TagViewState> tags,
            Set<String> delegatedOwnerEmails) {
        return createEndpointCreationTaskState(orgLink, endpointName, null, null,
                s3bucketName, tags, delegatedOwnerEmails, arn, externalId);
    }

    public static EndpointCreationTaskState createEndpointCreationTaskState(String orgLink,
            String endpointName, String secretAccessKey, String accessKeyId, String s3bucketName,
            Set<TagViewState> tags, Set<String> delegatedOwnerEmails) {
        return createEndpointCreationTaskState(orgLink, endpointName, secretAccessKey, accessKeyId,
                s3bucketName, tags, delegatedOwnerEmails, null, null);
    }

    public static EndpointCreationTaskState createEndpointCreationTaskState(String orgLink,
            String endpointName, String secretAccessKey, String accessKeyId, String s3bucketName,
            Set<TagViewState> tags, Set<String> delegatedOwnerEmails, String arn, String externalId) {
        EndpointCreationTaskState task = new EndpointCreationTaskState();
        task.name = endpointName;
        task.orgLink = orgLink;
        task.type = EndpointType.aws.name();
        Map<String, String> endpointProperties = new HashMap<>();
        endpointProperties.put(REGION_KEY, "test-regionId");
        task.endpointProperties = endpointProperties;
        Map<String, String> customProperties = new HashMap<>();
        customProperties.put(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET, s3bucketName);
        task.customProperties = customProperties;
        if (arn != null) {
            task.credentials = createAwsCredentialsArn(arn, externalId);
        } else {
            task.credentials = createAwsCredentials(accessKeyId, secretAccessKey);
        }
        task.isMock = true;
        task.ownerEmails = delegatedOwnerEmails;
        task.taskInfo = TaskState.createDirect();
        task.tags = (tags != null) ? tags : null;
        return task;
    }

    @Test
    public void testEndpointCreation() throws Throwable {
        this.host.assumeIdentity(this.userLink);
        EndpointCreationTaskState task = createMockAwsEndpoint(this.host, this.peerUri,
                this.orgLink, ENDPOINT_NAME_A, PRIVATE_KEY, PRIVATE_KEY_ID,
                null, null, null);
        String name = "my-endpoint";
        String privateKey = "foo";
        String privateKeyId = "bar";

        this.host.setSystemAuthorizationContext();
        this.host.sendAndWaitExpectSuccess(Operation.createGet(
                UriUtils.buildUri(this.peerUri,
                        EndpointUtils.buildEndpointAuthzArtifactSelfLink(UserGroupService.FACTORY_LINK,
                                task.endpointLink, task.tenantLinks))));

        this.host.sendAndWaitExpectSuccess(Operation.createGet(
                UriUtils.buildUri(this.peerUri,
                        EndpointUtils.buildEndpointAuthzArtifactSelfLink(ResourceGroupService.FACTORY_LINK,
                                task.endpointLink, task.tenantLinks))));

        this.host.sendAndWaitExpectSuccess(Operation.createGet(
                UriUtils.buildUri(this.peerUri,
                        EndpointUtils
                                .buildEndpointAuthzArtifactSelfLink(ResourceGroupService.FACTORY_LINK,
                                        task.endpointLink, task.tenantLinks))));

        this.host.sendAndWaitExpectSuccess(Operation.createGet(
                UriUtils.buildUri(this.peerUri,
                        EndpointUtils
                                .buildEndpointAuthzArtifactSelfLink(RoleService.FACTORY_LINK,
                                        task.endpointLink, task.tenantLinks))));

        Operation creatorUserState = this.host.waitForResponse(Operation.createGet(UriUtils
                .buildUri(this.peerUri, UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_1_ID)))).setReferer(this.host.getUri()));

        UserState state = creatorUserState.getBody(UserState.class);

        assertTrue(state.userGroupLinks.contains(EndpointUtils.buildEndpointAuthzArtifactSelfLink
                (UserGroupService.FACTORY_LINK, task.endpointLink, task.tenantLinks)));

        this.host.resetSystemAuthorizationContext();

        this.host.assumeIdentity(this.userLink);
        Operation endpointOp = this.host.waitForResponse(Operation.createGet(
                UriUtils.buildUri(this.peerUri, task.endpointLink)));
        assertEquals(Operation.STATUS_CODE_OK, endpointOp.getStatusCode());
        EndpointState endpoint = endpointOp.getBody(EndpointState.class);
        assertNotNull(endpoint);
        assertNotNull(endpoint.endpointProperties);
        assertEquals(PRIVATE_KEY_ID, endpoint.endpointProperties.get(PRIVATE_KEYID_KEY));

        // Test invalid user access. Today, without project level permissions, user-2 cannot access
        // the endpoint. We have a story to tackle that once it is implemented this test will
        // need to be updated.
        this.host.assumeIdentity(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_2_ID)), null);

        Operation failedEndpointOp = this.host.waitForResponse(Operation.createGet(
                UriUtils.buildUri(this.peerUri, task.endpointLink)));
        assertEquals(Operation.STATUS_CODE_FORBIDDEN, failedEndpointOp.getStatusCode());
    }

    @Test
    public void testEndpointCreationWithTags() throws Throwable {
        Set<TagViewState> tags = new HashSet<>();
        TagViewState tagA = new TagViewState("key1", "value1");
        tags.add(tagA);
        TagViewState tagB = new TagViewState("key2", "value2");
        tags.add(tagB);

        this.host.assumeIdentity(this.userLink);
        EndpointCreationTaskState task = createMockAwsEndpoint(this.host, this.peerUri, this.orgLink,
                ENDPOINT_NAME_A, PRIVATE_KEY, PRIVATE_KEY_ID, null,
                tags, null);

        Operation endpointOp = this.host.waitForResponse(Operation.createGet(
                UriUtils.buildUri(this.peerUri, task.endpointLink)));
        assertEquals(Operation.STATUS_CODE_OK, endpointOp.getStatusCode());
        EndpointState endpoint = endpointOp.getBody(EndpointState.class);
        assertNotNull(endpoint);
        assertNotNull(endpoint.endpointProperties);
        assertNotNull(endpoint.tagLinks);
        assertEquals(2, endpoint.tagLinks.size());
        assertEquals(PRIVATE_KEY_ID, endpoint.endpointProperties.get(PRIVATE_KEYID_KEY));

        // create a tag without the USER_DEFINED origin and verify the tag gets updated to have the
        // USER_DEFINED origin after the endpoint is created
        tags.clear();
        tagA = new TagViewState("key3", "value3");
        tags.add(tagA);

        TagState tag = TagsUtil.newTagState(tagA.key, tagA.value,
                EnumSet.of(TagOrigin.DISCOVERED), Arrays.asList(this.projectLink));

        Operation response = host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, TagService.FACTORY_LINK))
                .setBody(tag));
        tag = response.getBody(TagState.class);
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        task = createMockAwsEndpoint(this.host, this.peerUri, this.orgLink, ENDPOINT_NAME_B,
                PRIVATE_KEY, PRIVATE_KEY_ID, null, tags, null);
        endpointOp = this.host.waitForResponse(Operation.createGet(
                UriUtils.buildUri(this.peerUri, task.endpointLink)));
        assertEquals(Operation.STATUS_CODE_OK, endpointOp.getStatusCode());
        endpoint = endpointOp.getBody(EndpointState.class);
        assertNotNull(endpoint);
        assertNotNull(endpoint.endpointProperties);
        assertNotNull(endpoint.tagLinks);
        assertEquals(1, endpoint.tagLinks.size());
        assertEquals(PRIVATE_KEY_ID, endpoint.endpointProperties.get(PRIVATE_KEYID_KEY));
        response = this.host.waitForResponse(Operation.createGet(
                UriUtils.buildUri(this.peerUri, endpoint.tagLinks.iterator().next())));
        TagState updatedTag = response.getBody(TagState.class);
        assertTrue(tag.documentSelfLink.equals(updatedTag.documentSelfLink));
        assertTrue(updatedTag.origins.contains(TagOrigin.USER_DEFINED)
                && updatedTag.origins.contains(TagOrigin.DISCOVERED));
    }

    /**
     * Scenario:
     *  Pre create tagA (with DISCOVERED option)
     *  As user1 in org1 - Create an endpoint with tagA and tagB
     *  As user2 in org1 - Create an endpoint with tagA and tagB
     *
     *  Verify: there are only two tags in the system
     */
    @Test
    public void testEndpointCreationWithTagsFromDifferentUsersSameOrg() throws Throwable {
        this.host.assumeIdentity(this.userLink);
        Set<TagViewState> tags = new HashSet<>();
        TagViewState tagA = new TagViewState("key1", "value1");
        tags.add(tagA);
        TagViewState tagB = new TagViewState("key2", "value2");
        tags.add(tagB);

        // create tags in same tenant
        TagState tagStateA = TagsUtil.newTagState(tagA.key, tagA.value,
                EnumSet.of(TagOrigin.DISCOVERED), Arrays.asList(this.projectLink));
        Operation response = host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, TagService.FACTORY_LINK))
                .setBody(tagStateA));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        EndpointCreationTaskState task = createMockAwsEndpoint(this.host, this.peerUri, this.orgLink,
                ENDPOINT_NAME_A, PRIVATE_KEY, PRIVATE_KEY_ID, null, tags, null);

        // create endpoint as user1
        Operation endpointOp = this.host.waitForResponse(Operation.createGet(
                UriUtils.buildUri(this.peerUri, task.endpointLink)));
        assertEquals(Operation.STATUS_CODE_OK, endpointOp.getStatusCode());
        EndpointState endpoint = endpointOp.getBody(EndpointState.class);
        assertNotNull(endpoint);
        assertNotNull(endpoint.endpointProperties);
        assertNotNull(endpoint.tagLinks);
        assertEquals(2, endpoint.tagLinks.size());
        assertEquals(PRIVATE_KEY_ID, endpoint.endpointProperties.get(PRIVATE_KEYID_KEY));
        this.host.resetSystemAuthorizationContext();

        // create second endpoint as user3
        this.host.assumeIdentity(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_3_ID)), null);
        task = createMockAwsEndpoint(this.host, this.peerUri, this.orgLink,
                ENDPOINT_NAME_B, PRIVATE_KEY, PRIVATE_KEY_ID, null, tags, null);
        endpointOp = this.host.waitForResponse(Operation.createGet(
                UriUtils.buildUri(this.peerUri, task.endpointLink)));
        assertEquals(Operation.STATUS_CODE_OK, endpointOp.getStatusCode());
        endpoint = endpointOp.getBody(EndpointState.class);
        assertNotNull(endpoint);
        assertNotNull(endpoint.endpointProperties);
        assertNotNull(endpoint.tagLinks);
        assertEquals(2, endpoint.tagLinks.size());
        assertEquals(PRIVATE_KEY_ID, endpoint.endpointProperties.get(PRIVATE_KEYID_KEY));

        this.host.setSystemAuthorizationContext();
        ServiceDocumentQueryResult tagResults = SymphonyCommonTestUtils.queryDocuments(this.host,
                this.peerUri, Utils.buildKind(TagState.class), 2);
        assertNotNull(tagResults);
        assertEquals(2, tagResults.documentCount.intValue());
        Set<String> tenantLinks = new HashSet<>();
        for (String link : tagResults.documents.keySet()) {
            tenantLinks.addAll(Utils.fromJson(tagResults.documents.get(link),
                    TagState.class).tenantLinks);
        }
        // tags belong to same org - only one tenantLink should be present
        assertEquals(1, tenantLinks.size());
    }

    /**
     * Scenario:
     *  As userA in orgA - Create endpoint with tagA
     *  As userB in orgB - Create endpoint with tagA
     *
     *  Verify: two tags were created in the system (same key and value)
     */
    @Test
    public void testEndpointCreationWithTagsInDifferentOrgs() throws Throwable {
        Set<TagViewState> tags = new HashSet<>();
        TagViewState tag = new TagViewState("key1", "value1");
        tags.add(tag);

        // create endpoint for user1 in org1
        this.host.assumeIdentity(this.userLink);
        EndpointCreationTaskState task = createMockAwsEndpoint(this.host, this.peerUri,
                this.orgLink,
                ENDPOINT_NAME_A, PRIVATE_KEY, PRIVATE_KEY_ID, null, tags, null);

        Operation endpointOp = this.host.waitForResponse(Operation.createGet(
                UriUtils.buildUri(this.peerUri, task.endpointLink)));
        assertEquals(Operation.STATUS_CODE_OK, endpointOp.getStatusCode());
        EndpointState endpoint = endpointOp.getBody(EndpointState.class);
        assertNotNull(endpoint);
        assertNotNull(endpoint.endpointProperties);
        assertNotNull(endpoint.tagLinks);
        assertEquals(1, endpoint.tagLinks.size());
        assertEquals(PRIVATE_KEY_ID, endpoint.endpointProperties.get(PRIVATE_KEYID_KEY));
        this.host.resetSystemAuthorizationContext();

        // create endpoint for user2 in org2
        this.host.assumeIdentity(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_4_ID)), null);
        task = createMockAwsEndpoint(this.host, this.peerUri, this.orgLink2, ENDPOINT_NAME_B,
                PRIVATE_KEY, PRIVATE_KEY_ID, null, tags, null);
        endpointOp = this.host.waitForResponse(Operation.createGet(
                UriUtils.buildUri(this.peerUri, task.endpointLink)));
        assertEquals(Operation.STATUS_CODE_OK, endpointOp.getStatusCode());
        endpoint = endpointOp.getBody(EndpointState.class);
        assertNotNull(endpoint);
        assertNotNull(endpoint.endpointProperties);
        assertNotNull(endpoint.tagLinks);
        assertEquals(1, endpoint.tagLinks.size());
        assertEquals(PRIVATE_KEY_ID, endpoint.endpointProperties.get(PRIVATE_KEYID_KEY));
        this.host.setSystemAuthorizationContext();
        ServiceDocumentQueryResult tagResults = SymphonyCommonTestUtils.queryDocuments(this.host,
                this.peerUri, Utils.buildKind(TagState.class), 2);
        assertNotNull(tagResults);
        assertEquals(2, tagResults.documentCount.intValue());
        Set<String> tenantLinks = new HashSet<>();
        for (String link : tagResults.documents.keySet()) {
            TagState tagState = Utils.fromJson(tagResults.documents.get(link), TagState.class);
            assertTrue(tagState.key.equals(tag.key));
            assertTrue(tagState.value.equals(tag.value));
            tenantLinks.addAll(tagState.tenantLinks);
        }
        // tags belong to different orgs - two tenantLink should be present
        assertEquals(2, tenantLinks.size());
    }

    @Test
    public void testCreationInvalidOwner() throws Throwable {
        OnBoardingTestUtils.setupUser(this.host, this.peerUri,
                OnBoardingTestUtils.createUserData(USER_3_ID, USER_3_PASSWORD));
        OnBoardingTestUtils
                .setupOrganization(this.host, this.peerUri, ORG_2_ID, USER_3_ID, USER_3_PASSWORD);
        OnBoardingTestUtils.setupProject(this.host, this.peerUri, PROJECT_2_ID, ORG_2_ID, USER_3_ID,
                USER_3_PASSWORD);

        EndpointCreationTaskState task = new EndpointCreationTaskState();
        task.documentSelfLink = UriUtils.buildUriPath(EndpointCreationTaskService.FACTORY_LINK,
                UUID.randomUUID().toString());
        task.name = "name";
        task.orgLink = this.orgLink2;
        task.type = EndpointType.aws.name();
        Map<String, String> endpointProperties = new HashMap<>();
        endpointProperties.put(REGION_KEY, "test-regionId");
        task.endpointProperties = endpointProperties;
        Map<String, String> customProperties = new HashMap<>();
        customProperties.put(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET, this.s3bucketName);
        task.customProperties = customProperties;
        task.credentials = createAwsCredentials("access_key", "private_key");
        task.isMock = true;
        task.ownerEmails = Collections.singleton(USER_3_ID);
        task.taskInfo = TaskState.createDirect();

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointCreationTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        EndpointCreationTaskState body = response.getBody(EndpointCreationTaskState.class);
        assertEquals(SubStage.ERROR, body.subStage);
        assertEquals(TaskStage.FAILED, body.taskInfo.stage);

        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, body.taskInfo.failure.statusCode);
    }

    @Test
    public void testCreationOwner() throws Throwable {
        this.host.assumeIdentity(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_1_ID), null));

        // User 3 is org admin
        OnBoardingTestUtils.setupUser(this.host, this.peerUri,
                OnBoardingTestUtils.createUserData(USER_3_ID, USER_3_PASSWORD));
        addAdminToOrgOrProject(this.host, this.peerUri, this.orgLink, USER_3_ID,
                        USER_3_ID, USER_3_PASSWORD);

        // User 4 is org user
        OnBoardingTestUtils.setupUser(this.host, this.peerUri,
                OnBoardingTestUtils.createUserData(USER_4_ID, USER_4_PASSWORD));
        OnBoardingTestUtils
                .addUserToOrgOrProject(this.host, this.peerUri, this.orgLink, USER_4_ID,
                        USER_4_ID, USER_4_PASSWORD);


        EndpointCreationTaskState task = new EndpointCreationTaskState();
        task.documentSelfLink = UriUtils.buildUriPath(EndpointCreationTaskService.FACTORY_LINK,
                UUID.randomUUID().toString());
        task.name = "name";
        task.orgLink = this.orgLink;
        task.type = EndpointType.aws.name();
        Map<String, String> endpointProperties = new HashMap<>();
        endpointProperties.put(REGION_KEY, "test-regionId");
        task.endpointProperties = endpointProperties;
        Map<String, String> customProperties = new HashMap<>();
        customProperties.put(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET, this.s3bucketName);
        task.customProperties = customProperties;
        task.credentials = createAwsCredentials("access_key", "private_key");
        task.isMock = true;
        Set<String> delegatedOwners = new HashSet<>();
        delegatedOwners.add(USER_4_ID);
        task.ownerEmails = delegatedOwners;
        task.taskInfo = TaskState.createDirect();

        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointCreationTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        this.host.setSystemAuthorizationContext();

        Operation endpointFactory = this.host.waitForResponse(Operation.createGet(UriUtils
                .buildUri(this.peerUri, EndpointService.FACTORY_LINK)).setReferer(this.host.getUri()));

        ServiceDocumentQueryResult result = endpointFactory.getBody(ServiceDocumentQueryResult.class);
        String endpointLink = result.documentLinks.get(0);

        Operation user1UserState = this.host.waitForResponse(Operation.createGet(UriUtils
                .buildUri(this.peerUri, UriUtils.buildUriPath(UserService.FACTORY_LINK,
                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_1_ID)))).setReferer(this.host.getUri()));

        Operation user2UserState = this.host.waitForResponse(Operation.createGet(UriUtils
                .buildUri(this.peerUri, UriUtils.buildUriPath(UserService.FACTORY_LINK,
                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_2_ID)))).setReferer(this.host.getUri()));

        Operation user5UserState = this.host.waitForResponse(Operation.createGet(UriUtils
                .buildUri(this.peerUri, UriUtils.buildUriPath(UserService.FACTORY_LINK,
                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_3_ID)))).setReferer(this.host.getUri()));

        Operation user6UserState = this.host.waitForResponse(Operation.createGet(UriUtils
                .buildUri(this.peerUri, UriUtils.buildUriPath(UserService.FACTORY_LINK,
                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_4_ID)))).setReferer(this.host.getUri()));

        UserState user1 = user1UserState.getBody(UserState.class);
        UserState user2 = user2UserState.getBody(UserState.class);
        UserState user3 = user5UserState.getBody(UserState.class);
        UserState user4 = user6UserState.getBody(UserState.class);

        String orgId = UriUtils.getLastPathSegment(this.orgLink);
        String orgAdminUserGroupLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                OnboardingUtils.buildAuthzArtifactLink(orgId, true));
        String orgUserUserGroupLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                OnboardingUtils.buildAuthzArtifactLink(orgId, false));

        assertTrue(user1.userGroupLinks.contains(orgAdminUserGroupLink));
        assertTrue(user1.userGroupLinks.contains(EndpointUtils
                .buildEndpointAuthzArtifactSelfLink(UserGroupService.FACTORY_LINK, endpointLink,
                        task.orgLink)));
        assertFalse(user2.userGroupLinks.contains(orgAdminUserGroupLink));
        assertFalse(user2.userGroupLinks.contains(EndpointUtils
                .buildEndpointAuthzArtifactSelfLink(UserGroupService.FACTORY_LINK, endpointLink,
                       task.orgLink)));
        assertTrue(user3.userGroupLinks.contains(orgAdminUserGroupLink));
        assertFalse(user3.userGroupLinks.contains(EndpointUtils
                .buildEndpointAuthzArtifactSelfLink(UserGroupService.FACTORY_LINK, endpointLink,
                        task.orgLink)));
        assertTrue(user4.userGroupLinks.contains(orgUserUserGroupLink));
        assertFalse(user4.userGroupLinks.contains(orgAdminUserGroupLink));
        assertTrue(user4.userGroupLinks.contains(EndpointUtils
                .buildEndpointAuthzArtifactSelfLink(UserGroupService.FACTORY_LINK,endpointLink,
                        task.orgLink)));
    }

    private void verifyError(Operation op, ErrorCode errorCode) {
        ServiceErrorResponse error = op.getBody(ServiceErrorResponse.class);
        assertNotNull(error);
        assertEquals(String.valueOf(errorCode.getErrorCode()), error.messageId);
    }

    private static Operation addAdminToOrgOrProject(VerificationHost h, URI peerUri, String entityLink,
            String email,
            String userName, String password)
            throws Throwable {
        SymphonyCommonTestUtils.authenticate(h, peerUri, userName, password);
        UserUpdateRequest tenantPatch = new UserUpdateRequest();
        tenantPatch.isAdmin = true;
        tenantPatch.entityLink = entityLink;
        tenantPatch.email = email;
        return h.waitForResponse(Operation
                .createPost(UriUtils.buildUri(peerUri, UserUpdateService.SELF_LINK))
                .setBody(tenantPatch)
                .setCompletion(h.getCompletion()));
    }
}
