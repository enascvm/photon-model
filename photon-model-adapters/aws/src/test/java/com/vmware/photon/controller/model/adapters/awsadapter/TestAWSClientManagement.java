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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assume.assumeTrue;

import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.ARN_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.EXTERNAL_ID_KEY;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.AWS_ARN_DEFAULT_SESSION_DURATION_SECONDS_PROPERTY;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.AWS_MASTER_ACCOUNT_ACCESS_KEY_PROPERTY;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.AWS_MASTER_ACCOUNT_SECRET_KEY_PROPERTY;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory.getClientManager;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory.getClientReferenceCount;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory.returnClientManager;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.securitytoken.model.AWSSecurityTokenServiceException;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AwsClientType;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;
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
    public String awsMasterAccountAccessKey;
    public String awsMasterAccountSecretKey;
    public String arn;
    public String externalId;
    public AmazonEC2AsyncClient client;
    boolean isMock = true;

    private int ec2ClientReferenceCount;
    private int cloudWatchClientReferenceCount;
    private int clientCacheCount;

    @Before
    public void setUp() throws Exception {
        CommandLineArgumentParser.parseFromProperties(this);

        // ignore if any of the required properties are missing
        assumeTrue(TestUtils.isNull(this.awsMasterAccountAccessKey, this.awsMasterAccountSecretKey,
                this.arn, this.externalId));

        System.setProperty(AWS_MASTER_ACCOUNT_ACCESS_KEY_PROPERTY, this.awsMasterAccountAccessKey);
        System.setProperty(AWS_MASTER_ACCOUNT_SECRET_KEY_PROPERTY, this.awsMasterAccountSecretKey);
        System.setProperty(AWS_ARN_DEFAULT_SESSION_DURATION_SECONDS_PROPERTY, String.valueOf(900));

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
    public void testAWSClientManagementArn() throws Throwable {
        this.ec2ClientReferenceCount = getClientReferenceCount(AwsClientType.EC2);
        this.host.setTimeoutSeconds(60);

        // Getting a reference to client managers in the test
        AWSClientManager ec2ClientManager = getClientManager(AwsClientType.EC2);
        assertEquals(this.ec2ClientReferenceCount + count1, getClientReferenceCount(AwsClientType.EC2));

        this.creds = new AuthCredentialsServiceState();
        this.creds.customProperties = new HashMap<>();
        this.creds.customProperties.put(ARN_KEY, this.arn);
        this.creds.customProperties.put(EXTERNAL_ID_KEY, this.externalId);

        TestContext waitContext = new TestContext(1, Duration.ofSeconds(30L));
        ec2ClientManager.getOrCreateEC2ClientAsync(this.creds, TestAWSSetupUtils.regionId,
                this.instanceService)
                .exceptionally(t -> {
                    waitContext.fail(t);
                    throw new CompletionException(t);
                })
                .thenAccept(ec2Client -> {
                    this.client = ec2Client;
                    waitContext.complete();
                });
        waitContext.await();

        Assert.assertNotNull(this.client);
        this.clientCacheCount = ec2ClientManager.getCacheCount();

        // Requesting another AWS client with the same set of credentials will not
        // create a new entry in the cache
        AmazonEC2AsyncClient oldClient = this.client;
        TestContext nextContext = new TestContext(1, Duration.ofSeconds(30L));
        ec2ClientManager.getOrCreateEC2ClientAsync(this.creds, TestAWSSetupUtils.regionId,
                this.instanceService)
                .exceptionally(t -> {
                    nextContext.fail(t);
                    throw new CompletionException(t);
                })
                .thenAccept(ec2Client -> {
                    this.client = ec2Client;
                    nextContext.complete();
                });
        nextContext.await();

        Assert.assertNotNull(this.client);
        Assert.assertEquals(oldClient, this.client);
        assertEquals(this.clientCacheCount, ec2ClientManager.getCacheCount());
    }

    @Test
    public void testAWSClientManagementArnDr() throws Throwable {
        this.ec2ClientReferenceCount = getClientReferenceCount(AwsClientType.EC2);
        this.host.setTimeoutSeconds(60);

        // Getting a reference to client managers in the test
        AWSClientManager ec2ClientManager = getClientManager(AwsClientType.EC2);
        assertEquals(this.ec2ClientReferenceCount + count1, getClientReferenceCount(AwsClientType.EC2));

        this.creds = new AuthCredentialsServiceState();
        this.creds.customProperties = new HashMap<>();
        this.creds.customProperties.put(ARN_KEY, this.arn);
        this.creds.customProperties.put(EXTERNAL_ID_KEY, this.externalId);

        TestContext waitContext = new TestContext(1, Duration.ofSeconds(30L));
        ec2ClientManager.getOrCreateEC2ClientAsync(this.creds, TestAWSSetupUtils.regionId,
                this.instanceService)
                .exceptionally(t -> {
                    waitContext.fail(t);
                    throw new CompletionException(t);
                })
                .thenAccept(ec2Client -> {
                    this.client = ec2Client;
                    waitContext.complete();
                });
        waitContext.await();

        Assert.assertNotNull(this.client);
        this.clientCacheCount = ec2ClientManager.getCacheCount();

        // Requesting another AWS client with the same set of credentials will not
        // create a new entry in the cache
        AmazonEC2AsyncClient oldClient = this.client;
        TestContext nextContext = new TestContext(1, Duration.ofSeconds(30L));
        ec2ClientManager.getOrCreateEC2ClientAsync(this.creds, TestAWSSetupUtils.regionId,
                this.instanceService)
                .exceptionally(t -> {
                    nextContext.fail(t);
                    throw new CompletionException(t);
                })
                .thenAccept(ec2Client -> {
                    this.client = ec2Client;
                    nextContext.complete();
                });
        nextContext.await();

        Assert.assertNotNull(this.client);
        Assert.assertEquals(oldClient, this.client);
        assertEquals(this.clientCacheCount, ec2ClientManager.getCacheCount());
    }

    @Test
    public void testEc2ClientInvalidArnKey() throws Throwable {
        this.ec2ClientReferenceCount = getClientReferenceCount(AwsClientType.EC2);
        this.host.setTimeoutSeconds(60);

        // Getting a reference to client managers in the test
        AWSClientManager ec2ClientManager = getClientManager(AwsClientType.EC2);
        assertEquals(this.ec2ClientReferenceCount + count1, getClientReferenceCount(AwsClientType.EC2));

        this.creds = new AuthCredentialsServiceState();
        this.creds.customProperties = new HashMap<>();
        this.creds.customProperties.put(ARN_KEY, this.arn + "-invalid");
        this.creds.customProperties.put(EXTERNAL_ID_KEY, this.externalId);

        AWSSecurityTokenServiceException[] expectedException = new AWSSecurityTokenServiceException[1];

        TestContext waitContext = new TestContext(1, Duration.ofSeconds(60L));
        ec2ClientManager.getOrCreateEC2ClientAsync(this.creds, TestAWSSetupUtils.regionId,
                this.instanceService)
                .exceptionally(t -> {
                    expectedException[0] = (AWSSecurityTokenServiceException) t.getCause();
                    waitContext.complete();
                    throw new CompletionException(t);
                });
        waitContext.await();

        Assert.assertNull(this.client);
        Assert.assertEquals(Operation.STATUS_CODE_FORBIDDEN, expectedException[0].getStatusCode());
        Assert.assertEquals("AccessDenied", expectedException[0].getErrorCode());
    }

    @Test
    public void testEc2ClientInvalidExternalId() throws Throwable {
        this.ec2ClientReferenceCount = getClientReferenceCount(AwsClientType.EC2);
        this.host.setTimeoutSeconds(60);

        // Getting a reference to client managers in the test
        AWSClientManager ec2ClientManager = getClientManager(AwsClientType.EC2);
        assertEquals(this.ec2ClientReferenceCount + count1, getClientReferenceCount(AwsClientType.EC2));

        this.creds = new AuthCredentialsServiceState();
        this.creds.customProperties = new HashMap<>();
        this.creds.customProperties.put(ARN_KEY, this.arn);
        this.creds.customProperties.put(EXTERNAL_ID_KEY, "invalid");

        AWSSecurityTokenServiceException[] expectedException = new AWSSecurityTokenServiceException[1];

        TestContext waitContext = new TestContext(1, Duration.ofSeconds(60L));
        ec2ClientManager.getOrCreateEC2ClientAsync(this.creds, TestAWSSetupUtils.regionId,
                this.instanceService)
                .exceptionally(t -> {
                    expectedException[0] = (AWSSecurityTokenServiceException) t.getCause();
                    waitContext.complete();
                    throw new CompletionException(t);
                });
        waitContext.await();

        Assert.assertNull(this.client);
        Assert.assertEquals(Operation.STATUS_CODE_FORBIDDEN, expectedException[0].getStatusCode());
        Assert.assertEquals("AccessDenied", expectedException[0].getErrorCode());
    }

    @Test
    public void testAWSClientManagement() throws Throwable {
        this.ec2ClientReferenceCount = getClientReferenceCount(AwsClientType.EC2);
        this.cloudWatchClientReferenceCount = getClientReferenceCount(AwsClientType.CLOUD_WATCH);

        this.host.setTimeoutSeconds(60);

        // Getting a reference to client managers in the test
        AWSClientManager ec2ClientManager = getClientManager(AwsClientType.EC2);
        @SuppressWarnings("unused")
        AWSClientManager cloudWatchClientManager = getClientManager(AwsClientType.CLOUD_WATCH);
        assertEquals(this.ec2ClientReferenceCount + count1, getClientReferenceCount(AwsClientType.EC2));
        assertEquals(this.cloudWatchClientReferenceCount + count1, getClientReferenceCount(AwsClientType.CLOUD_WATCH));

        // Getting an AWSclient from the client manager
        this.creds = new AuthCredentialsServiceState();
        this.creds.privateKey = this.accessKey;
        this.creds.privateKeyId = this.secretKey;

        TestContext waitContext = new TestContext(1, Duration.ofSeconds(30L));
        ec2ClientManager.getOrCreateEC2ClientAsync(this.creds, TestAWSSetupUtils.regionId,
                this.instanceService)
                .exceptionally(t -> {
                    waitContext.fail(t);
                    throw new CompletionException(t);
                })
                .thenAccept(ec2Client -> {
                    this.client = ec2Client;
                    waitContext.complete();
                });
        waitContext.await();
        this.clientCacheCount = ec2ClientManager.getCacheCount();

        // Requesting another AWS client with the same set of credentials will not
        // create a new entry in the cache
        TestContext nextContext = new TestContext(1, Duration.ofSeconds(30L));
        ec2ClientManager.getOrCreateEC2ClientAsync(this.creds, TestAWSSetupUtils.regionId,
                this.instanceService)
                .exceptionally(t -> {
                    nextContext.fail(t);
                    throw new CompletionException(t);
                })
                .thenAccept(ec2Client -> {
                    this.client = ec2Client;
                    nextContext.complete();
                });
        nextContext.await();
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
        AWSClientManagerFactory.cleanUp(AwsClientType.S3_TRANSFER_MANAGER);

        // Get a reference to the client manager in the test
        AWSClientManager s3ClientManager = getClientManager(AwsClientType.S3_TRANSFER_MANAGER);
        assertEquals(count1, getClientReferenceCount(AwsClientType.S3_TRANSFER_MANAGER));

        AuthCredentialsServiceState testCreds = new AuthCredentialsServiceState();
        testCreds.privateKey = this.accessKey;
        testCreds.privateKeyId = this.secretKey;

        TestContext waitContext = new TestContext(1, Duration.ofSeconds(30L));
        s3ClientManager.getOrCreateEC2ClientAsync(this.creds, TestAWSSetupUtils.regionId,
                this.instanceService)
                .exceptionally(t -> {
                    waitContext.fail(t);
                    throw new CompletionException(t);
                })
                .thenAccept(s3Client -> {
                    this.client = s3Client;
                    waitContext.complete();
                });
        waitContext.await();
        assertEquals(count1, s3ClientManager.getCacheCount());

        // Return the references from the test
        returnClientManager(s3ClientManager, AwsClientType.S3_TRANSFER_MANAGER);
        assertEquals(count0, getClientReferenceCount(AwsClientType.S3_TRANSFER_MANAGER));
    }

    /**
     * This test requires a minimum of 15 minutes waiting to ensure the ARN credentials refresh
     * occurs.
     */
    @Ignore
    @Test
    public void testAWSClientManagementArnRefresh() throws Throwable {
        this.ec2ClientReferenceCount = getClientReferenceCount(AwsClientType.EC2);
        this.host.setTimeoutSeconds(1200);

        // Getting a reference to client managers in the test
        AWSClientManager ec2ClientManager = getClientManager(AwsClientType.EC2);
        assertEquals(this.ec2ClientReferenceCount + count1, getClientReferenceCount(AwsClientType.EC2));

        this.creds = new AuthCredentialsServiceState();
        this.creds.customProperties = new HashMap<>();
        this.creds.customProperties.put(ARN_KEY, this.arn);
        this.creds.customProperties.put(EXTERNAL_ID_KEY, this.externalId);

        TestContext waitContext = new TestContext(1, Duration.ofSeconds(30L));
        ec2ClientManager.getOrCreateEC2ClientAsync(this.creds, TestAWSSetupUtils.regionId,
                this.instanceService)
                .exceptionally(t -> {
                    waitContext.fail(t);
                    throw new CompletionException(t);
                })
                .thenAccept(ec2Client -> {
                    this.client = ec2Client;
                    waitContext.complete();
                });
        waitContext.await();
        this.clientCacheCount = ec2ClientManager.getCacheCount();

        host.log(Level.INFO,
                "Waiting 16 minutes for the current set of credentials to expire.");
        Thread.sleep(TimeUnit.MINUTES.toMillis(16));
        host.log(Level.INFO, "Retrieving the ec2 client with a refreshed set of credentials.");

        // Requesting the EC2 client will generate a new client as the original client's credentials
        // are now expired.
        AmazonEC2AsyncClient oldClient = this.client;
        TestContext nextContext = new TestContext(1, Duration.ofSeconds(30L));
        ec2ClientManager.getOrCreateEC2ClientAsync(this.creds, TestAWSSetupUtils.regionId,
                this.instanceService)
                .exceptionally(t -> {
                    nextContext.fail(t);
                    throw new CompletionException(t);
                })
                .thenAccept(ec2Client -> {
                    this.client = ec2Client;
                    this.clientCacheCount++;
                    nextContext.complete();
                });
        nextContext.await();

        assertNotEquals(oldClient, this.client);
        assertEquals(this.clientCacheCount, ec2ClientManager.getCacheCount());
    }
}
