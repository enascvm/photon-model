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

package com.vmware.photon.controller.model.adapters.awsadapter.enumeration;

import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSAuthentication;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSComputeHost;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSResourcePool;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.enumerateResources;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.regionId;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.setAwsClientMockInfo;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestUtils.getExecutor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.vmware.photon.controller.model.PhotonModelMetricServices;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSAdapters;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils;
import com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.SecurityGroupService;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.EndpointRemovalTaskService;
import com.vmware.xenon.common.BasicTestCase;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.QueryTask;


public class TestResourceDeduplication extends BasicTestCase {

    private static final String TEST_CASE_RUN = "Test Case Run ";
    private static final int DEFAULT_TIMEOUT_SECONDS = 200;

    private ComputeService.ComputeState computeHost;
    private ComputeService.ComputeState computeHost2;
    private EndpointService.EndpointState endpointState;
    private EndpointService.EndpointState endpointState2;

    private AmazonEC2AsyncClient client;
    public boolean isAwsClientMock = false;
    public String awsMockEndpointReference = null;

    // configure endpoint details
    public boolean isMock = false;
    public String accessKey = "AKIAJFDVYHWRMOTUAMKQ";
    public String secretKey = "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";

    public boolean useAllRegions = true;
    public int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;

    public static final String ENDPOINT_LINK_1 = "/resources/endpoints/1";
    public static final String ENDPOINT_LINK_2 = "/resources/endpoints/2";

    @Rule
    public TestName currentTestName = new TestName();

    @Before
    public void setUp() throws Throwable {
        CommandLineArgumentParser.parseFromProperties(this);

        setAwsClientMockInfo(this.isAwsClientMock, this.awsMockEndpointReference);
        // create credentials
        AuthCredentialsService.AuthCredentialsServiceState creds = new AuthCredentialsService.AuthCredentialsServiceState();
        creds.privateKey = this.secretKey;
        creds.privateKeyId = this.accessKey;
        this.client = AWSUtils.getAsyncClient(creds, null, getExecutor());

        try {
            PhotonModelServices.startServices(this.host);
            PhotonModelMetricServices.startServices(this.host);
            PhotonModelTaskServices.startServices(this.host);
            PhotonModelAdaptersRegistryAdapters.startServices(this.host);
            AWSAdapters.startServices(this.host);

            this.host.setTimeoutSeconds(this.timeoutSeconds);

            this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelAdaptersRegistryAdapters.LINKS);
            this.host.waitForServiceAvailable(AWSAdapters.LINKS);
        } catch (Throwable e) {
            this.host.log("Error starting up services for the test %s", e.getMessage());
            throw new Exception(e);
        }
        initResourcePoolAndComputeHost();
    }

    @After
    public void tearDown() throws Throwable {
        if (this.host == null) {
            return;
        }
        this.client.shutdown();
        setAwsClientMockInfo(false, null);
    }

    /**
     * Creates the state associated with the resource pool, compute host and the VM to be created.
     *
     * @throws Throwable
     */
    private void initResourcePoolAndComputeHost() throws Throwable {
        // Create a resource pool where the VM will be housed
        ResourcePoolService.ResourcePoolState resourcePool = createAWSResourcePool(this.host);

        AuthCredentialsService.AuthCredentialsServiceState auth = createAWSAuthentication(this.host,
                this.accessKey, this.secretKey);

        this.endpointState = TestAWSSetupUtils
                .createAWSEndpointState(this.host, auth.documentSelfLink,
                        resourcePool.documentSelfLink);

        this.endpointState2 = TestAWSSetupUtils
                .createAWSEndpointState(this.host, auth.documentSelfLink,
                        resourcePool.documentSelfLink);


        // create a compute host for the AWS
        this.computeHost = createAWSComputeHost(this.host,
                this.endpointState,
                null /*zoneId*/, this.useAllRegions ? null : regionId,
                this.isAwsClientMock,
                this.awsMockEndpointReference, null /*tags*/);

        this.computeHost2 = createAWSComputeHost(this.host,
                this.endpointState,
                null /*zoneId*/, this.useAllRegions ? null : regionId,
                this.isAwsClientMock,
                this.awsMockEndpointReference, null /*tags*/);

        endpointState.computeLink = this.computeHost.documentSelfLink;
        endpointState2.computeLink = this.computeHost2.documentSelfLink;

        this.host.waitForResponse(Operation.createPatch(this.host, endpointState.documentSelfLink)
                .setBody(endpointState));
        this.host.waitForResponse(Operation.createPatch(this.host, endpointState2.documentSelfLink)
                .setBody(endpointState2));
    }

    @Test
    public void testResourceDeduplication() throws Throwable {
        this.host.log("Running test: " + this.currentTestName);

        // if mock, simply return.
        if (this.isMock) {
            return;
        }

        // perform resource enumeration on given AWS endpoint
        enumerateResources(this.host, this.computeHost, this.endpointState, this.isMock,
                TEST_CASE_RUN);

        List<String> expectedEndpoints = new ArrayList<>();
        expectedEndpoints.add(this.endpointState.documentSelfLink);

        validateEndpointLinks(1, expectedEndpoints, this.endpointState.documentSelfLink);

        // Add duplicate end point and verify the endpoints again.
        enumerateResources(this.host, this.computeHost2, this.endpointState2, this.isMock,
                TEST_CASE_RUN);
        expectedEndpoints.add(this.endpointState2.documentSelfLink);
        validateEndpointLinks(2, expectedEndpoints, this.endpointState.documentSelfLink);
    }


    @Test
    public void testEndpointDisassociationOnEndpointDeletion() throws Throwable {
        this.host.log("Running test: " + this.currentTestName);

        // if mock, simply return.
        if (this.isMock) {
            return;
        }

        // perform resource enumeration on given AWS endpoint
        enumerateResources(this.host, this.computeHost, this.endpointState, this.isMock,
                TEST_CASE_RUN);

        List<String> expectedEndpoints = new ArrayList<>();
        expectedEndpoints.add(this.endpointState.documentSelfLink);

        validateEndpointLinks(1, expectedEndpoints, this.endpointState.documentSelfLink);

        // delete endpoint
        EndpointRemovalTaskService.EndpointRemovalTaskState completeState = TestAWSSetupUtils
                .deleteEndpointState(this.host, this.endpointState, true);
        assertTrue(completeState.taskInfo.stage == TaskState.TaskStage.FINISHED);

        validateEndpointLinks(0, new ArrayList<>(), "");
    }


    @Test
    public void testEndpointDisassociationOnEndpointDeletionWithMultipleEndpoints() throws
            Throwable {
        this.host.log("Running test: " + this.currentTestName);

        // if mock, simply return.
        if (this.isMock) {
            return;
        }

        // perform resource enumeration on given AWS endpoint
        enumerateResources(this.host, this.computeHost, this.endpointState, this.isMock,
                TEST_CASE_RUN);

        List<String> expectedEndpoints = new ArrayList<>();
        expectedEndpoints.add(this.endpointState.documentSelfLink);

        validateEndpointLinks(1, expectedEndpoints, this.endpointState.documentSelfLink);

        // Add duplicate end point and verify the endpoints again.
        enumerateResources(this.host, this.computeHost2, this.endpointState2, this.isMock,
                TEST_CASE_RUN);
        expectedEndpoints.add(this.endpointState2.documentSelfLink);
//        validateEndpointLinks(2, expectedEndpoints, this.endpointState.documentSelfLink);

        // delete endpoint
        EndpointRemovalTaskService.EndpointRemovalTaskState completeState = TestAWSSetupUtils
                .deleteEndpointState(this.host, this.endpointState2, true);
        assertTrue(completeState.taskInfo.stage == TaskState.TaskStage.FINISHED);

        expectedEndpoints.remove(this.endpointState2.documentSelfLink);
        validateEndpointLinks(1, expectedEndpoints, this.endpointState.documentSelfLink);
    }

    private void validateEndpointLinks(int size, List<String> expectedEndpoints, String
            expectedEndpointLink) {

        List<String> computeDocLinks = getDocumentLinks(ComputeService.ComputeState.class);
        for (String docLink : computeDocLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            Assert.assertTrue("Error retrieving state",
                    response.getStatusCode() == 200);
            ComputeService.ComputeState doc = response.getBody(ComputeService.ComputeState.class);
            Assert.assertNotNull(doc.endpointLink);
            assertEquals(expectedEndpointLink, doc.endpointLink);

            if (!ComputeDescriptionService.ComputeDescription.ComputeType.VM_HOST.equals(doc.type)) {
                Assert.assertEquals(size, doc.endpointLinks.size());

                for (String endpointLink : expectedEndpoints) {
                    Assert.assertTrue(doc.endpointLinks.contains(endpointLink));
                }
            }
        }

        List<String> computeDescDocLinks = getDocumentLinks(ComputeDescriptionService.ComputeDescription.class);
        for (String docLink : computeDescDocLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            Assert.assertTrue("Error retrieving state",
                    response.getStatusCode() == 200);
            ComputeDescriptionService.ComputeDescription doc = response.getBody(ComputeDescriptionService.ComputeDescription.class);

            // TODO Remove the if after 1 compute host for n endpoints.
            if (doc.zoneId != null) {
                Assert.assertNotNull(doc.endpointLink);
                Assert.assertEquals(size, doc.endpointLinks.size());
                for (String endpointLink : expectedEndpoints) {
                    Assert.assertTrue(doc.endpointLinks.contains(endpointLink));
                }
            }
        }

        List<String> diskDocLinks = getDocumentLinks(DiskService.DiskState.class);
        for (String docLink : diskDocLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            Assert.assertTrue("Error retrieving state",
                    response.getStatusCode() == 200);
            DiskService.DiskState doc = response.getBody(DiskService.DiskState.class);
            Assert.assertNotNull(doc.endpointLink);
//            assertEquals(expectedEndpointLink, doc.endpointLink);

            Assert.assertEquals(size, doc.endpointLinks.size());
            for (String endpointLink : expectedEndpoints) {
                Assert.assertTrue(doc.endpointLinks.contains(endpointLink));
            }
        }

        List<String> networkDocLinks = getDocumentLinks(NetworkService.NetworkState.class);
        for (String docLink : networkDocLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            Assert.assertTrue("Error retrieving state",
                    response.getStatusCode() == 200);
            NetworkService.NetworkState doc = response.getBody(NetworkService.NetworkState.class);
            Assert.assertNotNull(doc.endpointLink);
            assertEquals(expectedEndpointLink, doc.endpointLink);

            Assert.assertEquals(size, doc.endpointLinks.size());
            for (String endpointLink : expectedEndpoints) {
                Assert.assertTrue(doc.endpointLinks.contains(endpointLink));
            }
        }

        List<String> subnetDocLinks = getDocumentLinks(SubnetService.SubnetState.class);
        for (String docLink : subnetDocLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            Assert.assertTrue("Error retrieving state",
                    response.getStatusCode() == 200);
            SubnetService.SubnetState doc = response.getBody(SubnetService.SubnetState.class);
            Assert.assertNotNull(doc.endpointLink);
            assertEquals(expectedEndpointLink, doc.endpointLink);

            Assert.assertEquals(size, doc.endpointLinks.size());
            for (String endpointLink : expectedEndpoints) {
                Assert.assertTrue(doc.endpointLinks.contains(endpointLink));
            }
        }

        List<String> nicDocLinks = getDocumentLinks(
                NetworkInterfaceService.NetworkInterfaceState.class);
        for (String docLink : nicDocLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            Assert.assertTrue("Error retrieving state",
                    response.getStatusCode() == 200);
            NetworkInterfaceService.NetworkInterfaceState doc = response
                    .getBody(NetworkInterfaceService.NetworkInterfaceState.class);
            Assert.assertNotNull(doc.endpointLink);
            assertEquals(expectedEndpointLink, doc.endpointLink);

            Assert.assertEquals(size, doc.endpointLinks.size());
            for (String endpointLink : expectedEndpoints) {
                Assert.assertTrue(doc.endpointLinks.contains(endpointLink));
            }
        }

        List<String> sgDocLinks = getDocumentLinks(SecurityGroupService.SecurityGroupState.class);
        for (String docLink : sgDocLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            Assert.assertTrue("Error retrieving state",
                    response.getStatusCode() == 200);
            SecurityGroupService.SecurityGroupState doc = response
                    .getBody(SecurityGroupService.SecurityGroupState.class);
            Assert.assertNotNull(doc.endpointLink);
            assertEquals(expectedEndpointLink, doc.endpointLink);

            Assert.assertEquals(size, doc.endpointLinks.size());
            for (String endpointLink : expectedEndpoints) {
                Assert.assertTrue(doc.endpointLinks.contains(endpointLink));
            }
        }
    }

    private List<String> getDocumentLinks(Class resourceState) {
        QueryTask.Query.Builder qBuilder = QueryTask.Query.Builder.create()
                .addKindFieldClause(resourceState);

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .addOption(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT)
                .setQuery(qBuilder.build())
                .build();

        this.host.createQueryTaskService(queryTask, false, true, queryTask, null);
        return queryTask.results.documentLinks;
    }
}