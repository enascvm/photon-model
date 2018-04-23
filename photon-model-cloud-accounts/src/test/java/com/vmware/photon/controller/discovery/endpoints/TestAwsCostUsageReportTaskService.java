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

import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.CREDENTIALS_REQUIRED;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.INVALID_COST_AND_USAGE_REPORT_NAME;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.INVALID_S3_BUCKETNAME;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.INVALID_S3_BUCKET_PREFIX;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.S3_COST_USAGE_EXCEPTION;
import static com.vmware.photon.controller.discovery.endpoints.TestEndpointUtils.createAwsCredentials;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.discovery.common.ErrorCode;
import com.vmware.photon.controller.discovery.common.SymphonyCommonTestUtils;
import com.vmware.photon.controller.discovery.endpoints.AwsCostUsageReportTaskService.AwsCostUsageReportTaskState;
import com.vmware.photon.controller.discovery.endpoints.AwsCostUsageReportTaskService.SubStage;
import com.vmware.photon.controller.discovery.onboarding.OnBoardingTestUtils;
import com.vmware.photon.controller.discovery.onboarding.OnboardingServices;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSAdapters;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.xenon.common.BasicTestCase;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.VerificationHost;


public class TestAwsCostUsageReportTaskService extends BasicTestCase {
    public int numNodes = 3;

    private URI peerUri;
    // AWS access key
    private String accessKey = "accessKey";
    // AWS secret key
    private String secretKey = "secretKey";
    // AWS S3 bucket name
    private String s3bucketName = "testS3Bucket";
    private String s3bucketPrefix = "s3bucketPrefix";
    private String costAndUsageReport = "testReport";
    private boolean isMock = true;

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
        factories.add(AwsEndpointS3ValidationTaskService.FACTORY_LINK);
        SymphonyCommonTestUtils
                .waitForFactoryAvailability(this.host, this.host.getPeerHostUri(), factories);

        this.peerUri = this.host.getPeerHostUri();
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
        AwsCostUsageReportTaskState task = new AwsCostUsageReportTaskState();
        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, AwsCostUsageReportTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, CREDENTIALS_REQUIRED);

        task = new AwsCostUsageReportTaskState();
        task.credentials = createAwsCredentials("bar1", "foo1");
        response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, AwsCostUsageReportTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, INVALID_S3_BUCKETNAME);

        task = new AwsCostUsageReportTaskState();
        task.credentials = createAwsCredentials("bar1", "foo1");
        task.s3bucketName = "s3bucketName";
        response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, AwsCostUsageReportTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, INVALID_S3_BUCKET_PREFIX);

        task = new AwsCostUsageReportTaskState();
        task.credentials = createAwsCredentials("bar1", "foo1");
        task.s3bucketName = "s3bucketName";
        task.s3bucketPrefix = "s3bucketPrefix";
        response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, AwsCostUsageReportTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, INVALID_COST_AND_USAGE_REPORT_NAME);

        task = new AwsCostUsageReportTaskState();
        task.credentials = TestEndpointUtils.createAwsCredentialsArn("foo-arn");
        response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, AwsCostUsageReportTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, INVALID_S3_BUCKETNAME);
    }

    @Test
    public void testCheckCostUsageReportSuccessfully() throws Throwable {
        AwsCostUsageReportTaskState task = new AwsCostUsageReportTaskState();
        task.credentials = createAwsCredentials(this.accessKey, this.secretKey);
        task.s3bucketName = this.s3bucketName;
        task.s3bucketPrefix = this.s3bucketPrefix;
        task.costUsageReportName = this.costAndUsageReport;
        task.isMock = this.isMock;
        task.taskInfo = TaskState.createDirect();

        host.setSystemAuthorizationContext();
        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, AwsCostUsageReportTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        host.resetSystemAuthorizationContext();
        AwsCostUsageReportTaskState body = response.getBody(AwsCostUsageReportTaskState.class);
        assertEquals(SubStage.SUCCESS, body.subStage);
        assertEquals(TaskStage.FINISHED, body.taskInfo.stage);
    }


    @Test
    public void testCheckCostUsageReportInvalidBucketErrorsOut() throws Throwable {
        AwsCostUsageReportTaskState task = new AwsCostUsageReportTaskState();
        task.credentials = createAwsCredentials(this.accessKey, this.secretKey);
        task.s3bucketName = this.s3bucketName + "invalid";
        task.s3bucketPrefix = this.s3bucketPrefix;
        task.costUsageReportName = this.costAndUsageReport;
        task.isMock = this.isMock;
        task.taskInfo = TaskState.createDirect();

        host.setSystemAuthorizationContext();
        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, AwsCostUsageReportTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        host.resetSystemAuthorizationContext();
        // AWS returns an AmazonS3Exception that provided s3 bucketName is not found.
        // For e.g. Not Found (Service: Amazon S3; Status Code: 404; Error Code: 404 Not Found;
        // Request ID: XXXXXXXXXXXXXX)
        // This shouldn't happen because we validate for s3 bucket's existence with permissions
        // before calling create report in Endpoint Creation or Update
        if (!this.isMock) {
            AwsCostUsageReportTaskState body = response.getBody(AwsCostUsageReportTaskState.class);
            assertEquals(SubStage.ERROR, body.subStage);
            assertEquals(TaskStage.FAILED, body.taskInfo.stage);
            assertEquals(String.valueOf(S3_COST_USAGE_EXCEPTION.getErrorCode()),
                    body.taskInfo.failure.messageId);
        }
    }

    @Test
    public void testCheckCostUsageReportSucceedsEvenWhenReportExistsRemotely() throws Throwable {
        // setup
        AwsCostUsageReportTaskState task = new AwsCostUsageReportTaskState();
        task.credentials = createAwsCredentials(this.accessKey, this.secretKey);
        task.s3bucketName = this.s3bucketName;
        task.s3bucketPrefix = this.s3bucketPrefix;
        task.costUsageReportName = this.costAndUsageReport;
        task.isMock = this.isMock;
        task.taskInfo = TaskState.createDirect();

        host.setSystemAuthorizationContext();
        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, AwsCostUsageReportTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        AwsCostUsageReportTaskState body = response.getBody(AwsCostUsageReportTaskState.class);
        assertEquals(SubStage.SUCCESS, body.subStage);
        assertEquals(TaskStage.FINISHED, body.taskInfo.stage);

        // Actual test
        // Try again, this time it should succeed without creating the report again
        task = new AwsCostUsageReportTaskState();
        task.credentials = createAwsCredentials(this.accessKey, this.secretKey);
        task.s3bucketName = this.s3bucketName;
        task.s3bucketPrefix = this.s3bucketPrefix;
        task.costUsageReportName = this.costAndUsageReport;
        task.isMock = this.isMock;
        task.taskInfo = TaskState.createDirect();

        response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, AwsCostUsageReportTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        body = response.getBody(AwsCostUsageReportTaskState.class);
        assertEquals(SubStage.SUCCESS, body.subStage);
        assertEquals(TaskStage.FINISHED, body.taskInfo.stage);
        host.resetSystemAuthorizationContext();
    }

    @Test
    public void testCheckCurSucceedsWhenBucketExistsButNotPrefixAndReportRemotely() throws Throwable {
        // setup
        AwsCostUsageReportTaskState task = new AwsCostUsageReportTaskState();
        task.credentials = createAwsCredentials(this.accessKey, this.secretKey);
        task.s3bucketName = this.s3bucketName;
        task.s3bucketPrefix = this.s3bucketPrefix;
        task.costUsageReportName = this.costAndUsageReport;
        task.isMock = this.isMock;
        task.taskInfo = TaskState.createDirect();

        host.setSystemAuthorizationContext();
        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, AwsCostUsageReportTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        AwsCostUsageReportTaskState body = response.getBody(AwsCostUsageReportTaskState.class);
        assertEquals(SubStage.SUCCESS, body.subStage);
        assertEquals(TaskStage.FINISHED, body.taskInfo.stage);

        // Actual test
        // Try again, this time it should succeed by creating report with new provided prefix, name
        task = new AwsCostUsageReportTaskState();
        task.credentials = createAwsCredentials(this.accessKey, this.secretKey);
        task.s3bucketName = this.s3bucketName;
        task.s3bucketPrefix = this.s3bucketPrefix + "-another";
        task.costUsageReportName = this.costAndUsageReport + "-another";
        task.isMock = this.isMock;
        task.taskInfo = TaskState.createDirect();

        response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, AwsCostUsageReportTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        body = response.getBody(AwsCostUsageReportTaskState.class);
        if (!this.isMock) {
            assertEquals(SubStage.ERROR, body.subStage);
            assertEquals(TaskStage.FAILED, body.taskInfo.stage);
            assertEquals(S3_COST_USAGE_EXCEPTION.getErrorCode(),
                    Integer.parseInt(body.taskInfo.failure.messageId));
            host.resetSystemAuthorizationContext();
            return;
        }
        assertEquals(SubStage.SUCCESS, body.subStage);
        assertEquals(TaskStage.FINISHED, body.taskInfo.stage);
        host.resetSystemAuthorizationContext();
    }


    @Test
    public void testCheckCurSucceedsWhenPrefixExistsButNotReportRemotely() throws Throwable {
        // setup
        AwsCostUsageReportTaskState task = new AwsCostUsageReportTaskState();
        task.credentials = createAwsCredentials(this.accessKey, this.secretKey);
        task.s3bucketName = this.s3bucketName;
        task.s3bucketPrefix = this.s3bucketPrefix;
        task.costUsageReportName = this.costAndUsageReport;
        task.isMock = this.isMock;
        task.taskInfo = TaskState.createDirect();

        host.setSystemAuthorizationContext();
        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, AwsCostUsageReportTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        AwsCostUsageReportTaskState body = response.getBody(AwsCostUsageReportTaskState.class);
        assertEquals(SubStage.SUCCESS, body.subStage);
        assertEquals(TaskStage.FINISHED, body.taskInfo.stage);

        // Actual test
        // Try again, this time it should succeed by creating report with new provided name
        task = new AwsCostUsageReportTaskState();
        task.credentials = createAwsCredentials(this.accessKey, this.secretKey);
        task.s3bucketName = this.s3bucketName;
        task.s3bucketPrefix = this.s3bucketPrefix;
        task.costUsageReportName = this.costAndUsageReport + "-another";
        task.isMock = this.isMock;
        task.taskInfo = TaskState.createDirect();

        response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, AwsCostUsageReportTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        body = response.getBody(AwsCostUsageReportTaskState.class);
        if (!this.isMock) {
            assertEquals(SubStage.ERROR, body.subStage);
            assertEquals(TaskStage.FAILED, body.taskInfo.stage);
            assertEquals(S3_COST_USAGE_EXCEPTION.getErrorCode(),
                    Integer.parseInt(body.taskInfo.failure.messageId));
            host.resetSystemAuthorizationContext();
            return;
        }
        assertEquals(SubStage.SUCCESS, body.subStage);
        assertEquals(TaskStage.FINISHED, body.taskInfo.stage);
        host.resetSystemAuthorizationContext();
    }


    @Test
    public void testCheckCurErrorsWhenReportExistsButNotPrefixRemotely() throws Throwable {
        // setup
        AwsCostUsageReportTaskState task = new AwsCostUsageReportTaskState();
        task.credentials = createAwsCredentials(this.accessKey, this.secretKey);
        task.s3bucketName = this.s3bucketName;
        task.s3bucketPrefix = this.s3bucketPrefix;
        task.costUsageReportName = this.costAndUsageReport;
        task.isMock = this.isMock;
        task.taskInfo = TaskState.createDirect();

        host.setSystemAuthorizationContext();
        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, AwsCostUsageReportTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        AwsCostUsageReportTaskState body = response.getBody(AwsCostUsageReportTaskState.class);
        assertEquals(SubStage.SUCCESS, body.subStage);
        assertEquals(TaskStage.FINISHED, body.taskInfo.stage);

        // Actual test
        // Try again, this time it should fail saying the report already exists (AWS behavior)
        task = new AwsCostUsageReportTaskState();
        task.credentials = createAwsCredentials(this.accessKey, this.secretKey);
        task.s3bucketName = this.s3bucketName;
        task.s3bucketPrefix = this.s3bucketPrefix + "-another";
        task.costUsageReportName = this.costAndUsageReport;
        task.isMock = this.isMock;
        task.taskInfo = TaskState.createDirect();

        response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, AwsCostUsageReportTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        host.resetSystemAuthorizationContext();
        // AWS returns an DuplicateReportNameException that the report already exists.
        // For e.g. null (Service: AWSCostAndUsageReport; Status Code: 400;
        // Error Code: DuplicateReportNameException; Request ID: aa55e826-2ecd-11e8-9900-69055c7d1780)
        body = response.getBody(AwsCostUsageReportTaskState.class);
        if (!this.isMock) {
            assertEquals(SubStage.ERROR, body.subStage);
            assertEquals(TaskStage.FAILED, body.taskInfo.stage);
            assertEquals(S3_COST_USAGE_EXCEPTION.getErrorCode(),
                    Integer.parseInt(body.taskInfo.failure.messageId));
            host.resetSystemAuthorizationContext();
            return;
        }
        assertEquals(SubStage.SUCCESS, body.subStage);
        assertEquals(TaskStage.FINISHED, body.taskInfo.stage);
    }

    private void verifyError(Operation op, ErrorCode errorCode) {
        ServiceErrorResponse error = op.getBody(ServiceErrorResponse.class);
        assertNotNull(error);
        assertEquals(String.valueOf(errorCode.getErrorCode()), error.messageId);
    }

}
