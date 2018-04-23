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
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.INVALID_S3_BUCKETNAME;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.S3_BUCKET_PERMISSIONS_ERROR;
import static com.vmware.photon.controller.discovery.endpoints.TestEndpointUtils.createAwsCredentials;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.discovery.common.ErrorCode;
import com.vmware.photon.controller.discovery.common.SymphonyCommonTestUtils;
import com.vmware.photon.controller.discovery.endpoints.AwsEndpointS3ValidationTaskService.S3ValidationTaskState;
import com.vmware.photon.controller.discovery.endpoints.AwsEndpointS3ValidationTaskService.SubStage;
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


public class TestAwsEndpointS3ValidationTaskService extends BasicTestCase {
    public int numNodes = 3;

    private URI peerUri;
    // AWS access key
    private String accessKey = "accessKey";
    // AWS secret key
    private String secretKey = "secretKey";
    // AWS S3 bucket name
    private String s3bucketName = "testS3Bucket";
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
        SymphonyCommonTestUtils.waitForFactoryAvailability(this.host, this.host.getPeerHostUri(), factories);

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
        S3ValidationTaskState task = new S3ValidationTaskState();
        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, AwsEndpointS3ValidationTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, CREDENTIALS_REQUIRED);

        task = new S3ValidationTaskState();
        task.credentials = createAwsCredentials("bar1", "foo1");
        response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, AwsEndpointS3ValidationTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, INVALID_S3_BUCKETNAME);

        task = new S3ValidationTaskState();
        task.credentials = TestEndpointUtils.createAwsCredentialsArn("foo-arn");
        response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, AwsEndpointS3ValidationTaskService.FACTORY_LINK))
                .setBody(task));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        verifyError(response, INVALID_S3_BUCKETNAME);
    }

    @Test
    public void testS3BucketPermissionsValidation() throws Throwable {
        S3ValidationTaskState validateTask = new S3ValidationTaskState();
        validateTask.credentials = createAwsCredentials(this.accessKey, this.secretKey);
        validateTask.s3bucketName = this.s3bucketName;
        validateTask.isMock = this.isMock;
        validateTask.taskInfo = TaskState.createDirect();

        host.setSystemAuthorizationContext();
        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, AwsEndpointS3ValidationTaskService.FACTORY_LINK))
                .setBody(validateTask));
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        host.resetSystemAuthorizationContext();
        S3ValidationTaskState body = response.getBody(S3ValidationTaskState.class);
        assertEquals(SubStage.SUCCESS, body.subStage);
        assertEquals(TaskStage.FINISHED, body.taskInfo.stage);
    }

    //Note: AWS Returns the same AmazonS3Exception exception if the provided s3bucket doesn't
    // exist or if the credentials do not have access to the provided s3bucket.
    @Test
    public void testValidationReturnsErrorIfNoAccessPermissionsOnS3Bucket() throws Throwable {
        S3ValidationTaskState validateTask = new S3ValidationTaskState();
        validateTask.credentials = createAwsCredentials(this.accessKey, this.secretKey);
        validateTask.s3bucketName = this.s3bucketName + "invalid";
        validateTask.taskInfo = TaskState.createDirect();

        host.setSystemAuthorizationContext();
        Operation response = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, AwsEndpointS3ValidationTaskService.FACTORY_LINK))
                .setBody(validateTask));
        //the S3 task service itself is successful
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        host.resetSystemAuthorizationContext();
        S3ValidationTaskState body = response.getBody(S3ValidationTaskState.class);
        //but S3 validation failed
        assertEquals(TaskStage.FAILED, body.taskInfo.stage);
        assertEquals(String.valueOf(S3_BUCKET_PERMISSIONS_ERROR.getErrorCode()), body.taskInfo
                .failure.messageId);
    }


    private void verifyError(Operation op, ErrorCode errorCode) {
        ServiceErrorResponse error = op.getBody(ServiceErrorResponse.class);
        assertNotNull(error);
        assertEquals(String.valueOf(errorCode.getErrorCode()), error.messageId);
    }

}
