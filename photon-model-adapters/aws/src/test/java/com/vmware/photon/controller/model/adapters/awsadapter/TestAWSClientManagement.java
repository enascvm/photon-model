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

package com.vmware.photon.controller.model.adapters.awsadapter;

import static org.junit.Assert.assertEquals;

import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory.getClientManager;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory.getClientReferenceCount;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory.returnClientManager;

import java.util.ArrayList;
import java.util.List;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AwsClientType;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 *Tests that the AWS client instances use a common executor pool for calling into AWS
 *and all the client caches and executor pools are cleaned up when the AWS adapters
 *are shutdown.
 *
 */
public class TestAWSClientManagement extends BasicReusableHostTestCase {
    public static final int count1 = 1;
    public static final int count2 = 2;
    public static final int count0 = 0;
    public StatelessService instanceService;
    public StatelessService statsService;
    public AuthCredentialsServiceState creds;
    public String accessKey = "accessKey";
    public String secretKey = "secretKey";
    public AmazonEC2AsyncClient client;
    boolean isMock = true;

    private int ec2ClientReferenceCount;
    private int cloudWatchClientReferenceCount;
    private int clientCacheCount;

    @Before
    public void setUp() throws Exception {
        CommandLineArgumentParser.parseFromProperties(this);
        List<String> serviceSelfLinks = new ArrayList<>();
        try {
            // TODO: VSYM-992 - improve test/remove arbitrary timeout
            this.instanceService = new AWSInstanceService();
            this.host.startService(
                    Operation.createPost(UriUtils.buildUri(this.host,
                            AWSInstanceService.class)),
                    this.instanceService);
            serviceSelfLinks.add(AWSInstanceService.SELF_LINK);

            this.statsService = new AWSStatsService();
            this.host.startService(
                    Operation.createPost(UriUtils.buildUri(this.host,
                            AWSStatsService.class)),
                    this.statsService);
            serviceSelfLinks.add(AWSStatsService.SELF_LINK);

            this.host.waitForServiceAvailable(AWSStatsService.SELF_LINK);
            this.host.waitForServiceAvailable(AWSInstanceService.SELF_LINK);
        } catch (Throwable e) {
            this.host.log("Error starting up services for the test %s", e.getMessage());
            throw new Exception(e);
        }
    }

    @After
    public void tearDown() throws InterruptedException {
        if (this.host == null) {
            return;
        }
    }

    @Test
    public void testAWSClientManagement() throws Throwable {
        this.ec2ClientReferenceCount = getClientReferenceCount(AwsClientType.EC2);
        this.cloudWatchClientReferenceCount = getClientReferenceCount(AwsClientType.CLOUD_WATCH);

        this.host.setTimeoutSeconds(60);

        // Getting a reference to client managers in the test
        AWSClientManager ec2ClientManager = getClientManager(AwsClientType.EC2);
        AWSClientManager cloudWatchClientManager = getClientManager(AwsClientType.CLOUD_WATCH);
        assertEquals(this.ec2ClientReferenceCount + count1, getClientReferenceCount(AwsClientType.EC2));
        assertEquals(this.cloudWatchClientReferenceCount + count1, getClientReferenceCount(AwsClientType.CLOUD_WATCH));

        // Getting an AWSclient from the client manager
        this.creds = new AuthCredentialsServiceState();
        this.creds.privateKey = this.accessKey;
        this.creds.privateKeyId = this.secretKey;

        this.client = ec2ClientManager.getOrCreateEC2Client(this.creds, TestAWSSetupUtils.regionId,
                this.instanceService, t -> {
                    throw new RuntimeException(t);
                });
        this.clientCacheCount = ec2ClientManager.getCacheCount();

        // Requesting another AWS client with the same set of credentials will not
        // create a new entry in the cache
        this.client = ec2ClientManager.getOrCreateEC2Client(this.creds, TestAWSSetupUtils.regionId,
                this.instanceService, t -> {
                    throw new RuntimeException(t);
                });
        assertEquals(this.clientCacheCount, ec2ClientManager.getCacheCount());

        // Emulating shutdown of individual services to check that the client resources are
        // cleaned up as expected.
        this.host.sendAndWaitExpectSuccess(
                Operation.createDelete(UriUtils.buildUri(this.host, AWSInstanceService.SELF_LINK)));
        assertEquals(this.ec2ClientReferenceCount, getClientReferenceCount(AwsClientType.EC2));

        this.host.sendAndWaitExpectSuccess(
                Operation.createDelete(UriUtils.buildUri(this.host, AWSStatsService.SELF_LINK)));
        assertEquals(this.cloudWatchClientReferenceCount, getClientReferenceCount(AwsClientType.CLOUD_WATCH));

        // Returning the references from the test
        returnClientManager(ec2ClientManager, AwsClientType.EC2);
        assertEquals(this.ec2ClientReferenceCount - count1, getClientReferenceCount(AwsClientType.EC2));
    }

    @Test
    public void testAwsS3ClientManagement() throws Throwable {

        // Ensure that we start with a clean state.
        AWSClientManagerFactory.cleanUp(AwsClientType.S3);

        // Get a reference to the client manager in the test
        AWSClientManager s3ClientManager = getClientManager(AwsClientType.S3);
        assertEquals(count1, getClientReferenceCount(AwsClientType.S3));

        AuthCredentialsServiceState testCreds = new AuthCredentialsServiceState();
        testCreds.privateKey = this.accessKey;
        testCreds.privateKeyId = this.secretKey;

        s3ClientManager.getOrCreateS3AsyncClient(testCreds, TestAWSSetupUtils.zoneId,
                this.statsService, null);
        assertEquals(count1, s3ClientManager.getCacheCount());

        // Return the references from the test
        returnClientManager(s3ClientManager, AwsClientType.S3);
        assertEquals(count0, getClientReferenceCount(AwsClientType.S3));
    }
}
