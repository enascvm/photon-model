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

import static org.junit.Assert.assertTrue;

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_TAG_NAME;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestUtils.getExecutor;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.TagDescription;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSNetworkClient;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;

import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class TestAWSUtils {
    /*
    * This test requires the following three command line variables.
    * If they are not present the tests will be ignored
    * Pass them into the test with the -Ddcp.variable=value syntax
    * i.e -Ddcp.privateKey="XXXXXXXXXXXXXXXXXXXX"
    *     -Ddcp.privateKeyId="YYYYYYYYYYYYYYYYYY"
    *     -Ddcp.region="us-east-1"
    *
    * privateKey & privateKeyId are credentials to an AWS VPC account
    * region is the ec2 region where the tests should be run (us-east-1)
    */

    public static final String TEST_NAME = "VMW-Testing";

    // command line options
    public String privateKey;
    public String privateKeyId;
    public String region;

    VerificationHost host;

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        CommandLineArgumentParser.parseFromProperties(this);

        // ignore if any of the required properties are missing
        org.junit.Assume.assumeTrue(TestUtils.isNull(this.privateKey, this.privateKeyId, this.region));

        this.host = VerificationHost.create(0);
        try {
            this.host.start();
            PhotonModelServices.startServices(this.host);
            PhotonModelTaskServices.startServices(this.host);
        } catch (Throwable e) {
            throw new Exception(e);
        }
    }

    @After
    public void tearDown() throws InterruptedException {
        if (this.host == null) {
            return;
        }

        this.host.tearDownInProcessPeers();
        this.host.toggleNegativeTestMode(false);
        this.host.tearDown();
    }

    @Test
    public void testClientCreation() throws Throwable {
        AuthCredentialsServiceState creds = new AuthCredentialsServiceState();
        creds.privateKey = this.privateKey;
        creds.privateKeyId = this.privateKeyId;
        AWSUtils.getAsyncClient(creds, this.region, getExecutor());
    }

    @Test
    public void testInvalidClientCredentials() throws Throwable {
        this.expectedEx.expect(AmazonServiceException.class);
        AuthCredentialsServiceState creds = new AuthCredentialsServiceState();
        creds.privateKey = "bar";
        creds.privateKeyId = "foo";
        AWSUtils.getAsyncClient(creds, this.region, getExecutor());
    }

    @Test
    public void testResourceNaming() throws Throwable {
        boolean tagFound = false;
        AmazonEC2AsyncClient client = TestUtils.getClient(this.privateKeyId,this.privateKey,this.region,false);

        //create something to name
        AWSNetworkClient svc = new AWSNetworkClient(client);
        String vpcID = svc.createVPC("10.20.0.0/16");
        AWSUtils.tagResourcesWithName(client, TEST_NAME, vpcID);
        List<TagDescription> tags = AWSUtils.getResourceTags(vpcID,client);

        for (TagDescription tagDesc:tags) {
            if (tagDesc.getKey().equalsIgnoreCase(AWS_TAG_NAME)) {
                assertTrue(tagDesc.getValue().equalsIgnoreCase(TEST_NAME));
                tagFound = true;
                break;
            }
        }
        // ensure we found the tag
        assertTrue(tagFound);
        svc.deleteVPC(vpcID);
    }

    /**
     * Test that expects null response since there arn't sufficient datapoints
     */
    @Test
    public void testAverageBurnRateCalculationExpectNull() {
        List<Datapoint> dpList = new ArrayList<>();
        dpList.add(new Datapoint());
        Double burnRate = AWSUtils.calculateAverageBurnRate(dpList);
        assertTrue("Received a non-null response", burnRate == null);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testAverageBurnRateCalculation() {
        List<Datapoint> dpList = new ArrayList<>();
        dpList.add(getDatapoint(1.0, new Date(2016, 10, 9, 5, 00, 00)));
        dpList.add(getDatapoint(2.0, new Date(2016, 10, 9, 9, 00, 00)));
        dpList.add(getDatapoint(3.0, new Date(2016, 10, 9, 13, 00, 00)));
        dpList.add(getDatapoint(4.0, new Date(2016, 10, 9, 17, 00, 00)));
        dpList.add(getDatapoint(5.0, new Date(2016, 10, 9, 21, 00, 00)));
        dpList.add(getDatapoint(6.0, new Date(2016, 10, 10, 1, 00, 00)));
        dpList.add(getDatapoint(7.0, new Date(2016, 10, 10, 5, 00, 00)));
        dpList.add(getDatapoint(8.0, new Date(2016, 10, 10, 9, 00, 00)));
        dpList.add(getDatapoint(9.0, new Date(2016, 10, 10, 13, 00, 00)));
        dpList.add(getDatapoint(10.0, new Date(2016, 10, 10, 17, 00, 00)));
        Double burnRate = AWSUtils.calculateAverageBurnRate(dpList);
        assertTrue("Received a null response", burnRate != null);
        assertTrue("BurnRate value is negative", burnRate > 0.0);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testAverageBurnRateCalculationWithResetToZero() {
        List<Datapoint> dpList = new ArrayList<>();
        dpList.add(getDatapoint(4.0, new Date(2016, 10, 9, 5, 00, 00)));
        dpList.add(getDatapoint(5.0, new Date(2016, 10, 9, 9, 00, 00)));
        dpList.add(getDatapoint(6.0, new Date(2016, 10, 9, 13, 00, 00)));
        dpList.add(getDatapoint(7.0, new Date(2016, 10, 9, 17, 00, 00)));
        dpList.add(getDatapoint(8.0, new Date(2016, 10, 9, 21, 00, 00)));
        dpList.add(getDatapoint(9.0, new Date(2016, 10, 10, 1, 00, 00)));
        dpList.add(getDatapoint(0.0, new Date(2016, 10, 10, 5, 00, 00)));
        dpList.add(getDatapoint(1.0, new Date(2016, 10, 10, 9, 00, 00)));
        dpList.add(getDatapoint(2.0, new Date(2016, 10, 10, 13, 00, 00)));
        dpList.add(getDatapoint(3.0, new Date(2016, 10, 10, 17, 00, 00)));
        Double burnRate = AWSUtils.calculateAverageBurnRate(dpList);
        assertTrue("Received a null response", burnRate != null);
        assertTrue("BurnRate value is negative", burnRate > 0.0);
    }

    /**
     * Test that expects null response since there arn't sufficient datapoints
     */
    @Test
    public void testCurrentBurnRateCalculationExpectNull() {
        List<Datapoint> dpList = new ArrayList<>();
        dpList.add(new Datapoint());
        Double burnRate = AWSUtils.calculateCurrentBurnRate(dpList);
        assertTrue("Received a non-null response", burnRate == null);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testCurrentBurnRateCalculation() {
        List<Datapoint> dpList = new ArrayList<>();
        dpList.add(getDatapoint(1.0, new Date(2016, 10, 9, 5, 00, 00)));
        dpList.add(getDatapoint(2.0, new Date(2016, 10, 9, 9, 00, 00)));
        dpList.add(getDatapoint(3.0, new Date(2016, 10, 9, 13, 00, 00)));
        dpList.add(getDatapoint(4.0, new Date(2016, 10, 9, 17, 00, 00)));
        dpList.add(getDatapoint(5.0, new Date(2016, 10, 9, 21, 00, 00)));
        dpList.add(getDatapoint(6.0, new Date(2016, 10, 10, 1, 00, 00)));
        dpList.add(getDatapoint(7.0, new Date(2016, 10, 10, 5, 00, 00)));
        dpList.add(getDatapoint(8.0, new Date(2016, 10, 10, 9, 00, 00)));
        Double burnRate = AWSUtils.calculateCurrentBurnRate(dpList);
        assertTrue("Received a null response", burnRate != null);
        assertTrue("BurnRate value is negative", burnRate > 0.0);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testCurrentBurnRateCalculationWithResetToZero() {
        List<Datapoint> dpList = new ArrayList<>();
        dpList.add(getDatapoint(4.0, new Date(2016, 10, 9, 13, 00, 00)));
        dpList.add(getDatapoint(5.0, new Date(2016, 10, 9, 17, 00, 00)));
        dpList.add(getDatapoint(6.0, new Date(2016, 10, 9, 21, 00, 00)));
        dpList.add(getDatapoint(7.0, new Date(2016, 10, 10, 1, 00, 00)));
        dpList.add(getDatapoint(0.0, new Date(2016, 10, 10, 5, 00, 00)));
        dpList.add(getDatapoint(1.0, new Date(2016, 10, 10, 9, 00, 00)));
        dpList.add(getDatapoint(2.0, new Date(2016, 10, 10, 13, 00, 00)));
        dpList.add(getDatapoint(3.0, new Date(2016, 10, 10, 17, 00, 00)));
        Double burnRate = AWSUtils.calculateCurrentBurnRate(dpList);
        assertTrue("Received a null response", burnRate != null);
        assertTrue("BurnRate value is negative", burnRate > 0.0);
    }

    private Datapoint getDatapoint(Double average, Date timestamp) {
        Datapoint dp = new Datapoint();
        dp.setAverage(average);
        dp.setTimestamp(timestamp);
        return dp;
    }
}
