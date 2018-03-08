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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSAuthentication;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSComputeHost;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSResourcePool;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.enumerateResources;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.regionId;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.setAwsClientMockInfo;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestUtils.getExecutor;

import java.util.List;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;

import org.apache.commons.lang3.StringUtils;
import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.vmware.photon.controller.model.PhotonModelMetricServices;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSAdaptersTestUtils;
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
import com.vmware.photon.controller.model.tasks.EndpointRemovalTaskService;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
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
    private EndpointService.EndpointState endpointState;
    private EndpointService.EndpointState endpointState2;

    private AmazonEC2AsyncClient client;
    public boolean isAwsClientMock = false;
    public String awsMockEndpointReference = null;

    // configure endpoint details
    public boolean isMock = true;
    public String accessKey = "accessKey";
    public String secretKey = "secretKey";

    public boolean useAllRegions = true;
    public int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
    public static final String ENDPOINT_LINKS_FIELD_CLAUSE = "endpointLinks.item";

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
            AWSAdaptersTestUtils.startServicesSynchronously(this.host);

            this.host.setTimeoutSeconds(this.timeoutSeconds);

            this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
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
        AuthCredentialsService.AuthCredentialsServiceState auth2 = createAWSAuthentication(this.host,
                this.accessKey, this.secretKey);

        this.endpointState = TestAWSSetupUtils
                .createAWSEndpointState(this.host, auth.documentSelfLink,
                        resourcePool.documentSelfLink);

        this.endpointState2 = TestAWSSetupUtils
                .createAWSEndpointState(this.host, auth2.documentSelfLink,
                        resourcePool.documentSelfLink);

        // create a compute host for the AWS
        this.computeHost = createAWSComputeHost(this.host,
                this.endpointState,
                null /*zoneId*/, this.useAllRegions ? null : regionId,
                this.isAwsClientMock,
                this.awsMockEndpointReference, null /*tags*/);

        this.computeHost.endpointLinks.add(this.endpointState2.documentSelfLink);
        this.host.waitForResponse(Operation.createPatch(this.host, this.computeHost.documentSelfLink)
                .setBody(this.computeHost));

        this.endpointState.computeLink = this.computeHost.documentSelfLink;
        this.endpointState2.computeLink = this.computeHost.documentSelfLink;

        this.host.waitForResponse(Operation.createPatch(this.host, this.endpointState.documentSelfLink)
                .setBody(this.endpointState));
        this.host.waitForResponse(Operation.createPatch(this.host, this.endpointState2.documentSelfLink)
                .setBody(this.endpointState2));
    }

    @Test
    public void testResourceDeduplication() throws Throwable {
        this.host.log("Running test: " + this.currentTestName);

        // if mock, simply return.
        if (this.isMock) {
            return;
        }

        // perform resource enumeration on given AWS endpoint
        enumerateResources(this.host, this.computeHost, this.endpointState, this.isMock, TEST_CASE_RUN);
        validateEndpointLinks(this.endpointState.documentSelfLink, this.computeHost.documentSelfLink);

        // Add duplicate end point and verify the endpoints again.
        this.computeHost.endpointLinks.add(this.endpointState2.documentSelfLink);
        enumerateResources(this.host, this.computeHost, this.endpointState2, this.isMock, TEST_CASE_RUN);
        validateEndpointLinks(this.endpointState.documentSelfLink, this.computeHost.documentSelfLink);
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
        validateEndpointLinks(this.endpointState.documentSelfLink, this.computeHost.documentSelfLink);

        // delete endpoint
        EndpointRemovalTaskService.EndpointRemovalTaskState completeState = TestAWSSetupUtils
                .deleteEndpointState(this.host, this.endpointState, true);
        assertTrue(completeState.taskInfo.stage == TaskState.TaskStage.FINISHED);
        Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), this.endpointState.documentSelfLink))
                .setReferer(this.host.getUri());
        Operation response = this.host.waitForResponse(op);
        assertTrue("Endpoint was expected to be deleted",
                response.getStatusCode() == 404);

        validateEndpointLinks("", this.computeHost.documentSelfLink);

        // Add end point again and verify the endpoints.
        enumerateResources(this.host, this.computeHost, this.endpointState2, this.isMock,
                TEST_CASE_RUN);
        validateEndpointLinks(this.endpointState2.documentSelfLink, this.computeHost.documentSelfLink);

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

        validateEndpointLinks(this.endpointState.documentSelfLink, this.computeHost.documentSelfLink);

        // Add duplicate end point and verify the endpoints again.
        enumerateResources(this.host, this.computeHost, this.endpointState2, this.isMock,
                TEST_CASE_RUN);

        // delete endpoint
        EndpointRemovalTaskService.EndpointRemovalTaskState completeState = TestAWSSetupUtils
                .deleteEndpointState(this.host, this.endpointState2, true);
        assertTrue(completeState.taskInfo.stage == TaskState.TaskStage.FINISHED);
        Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), this.endpointState2.documentSelfLink))
                .setReferer(this.host.getUri());
        Operation response = this.host.waitForResponse(op);
        assertTrue("Endpoint was expected to be deleted",
                response.getStatusCode() == 404);
        validateEndpointLinks(this.endpointState.documentSelfLink, this.computeHost.documentSelfLink);
    }

    private void validateEndpointLinks(String expectedEndpoint, String computeHostLink) {

        List<String> computeDocLinks =  getDocumentLinks(ComputeService.ComputeState.class, expectedEndpoint);
        this.host.log("ComputeState docs size: " + computeDocLinks.size());
        for (String docLink : computeDocLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            assertTrue("Error retrieving state",
                    response.getStatusCode() == 200);
            ComputeService.ComputeState doc = response.getBody(ComputeService.ComputeState.class);

            if (!ComputeDescriptionService.ComputeDescription.ComputeType.ENDPOINT_HOST.equals(doc.type)
                    && !ComputeDescriptionService.ComputeDescription.ComputeType.ZONE.equals(doc.type)) {
                assertNotNull(doc.endpointLink);
                if (StringUtils.isEmpty(expectedEndpoint)) {
                    assertEquals("EndpointLink should be empty", expectedEndpoint, doc.endpointLink);
                } else {
                    assertThat(String.format("EndpointLinks should have: %s", expectedEndpoint),
                            doc.endpointLinks, CoreMatchers.hasItem(expectedEndpoint));
                }

                assertEquals(doc.computeHostLink, computeHostLink);
                assertNotNull(doc.documentCreationTimeMicros);
            }
        }

        List<String> diskDocLinks = getDocumentLinks(DiskService.DiskState.class, expectedEndpoint);
        this.host.log("DiskState docs size: " + diskDocLinks.size());
        for (String docLink : diskDocLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            assertTrue("Error retrieving state",
                    response.getStatusCode() == 200);
            DiskService.DiskState doc = response.getBody(DiskService.DiskState.class);

            assertNotNull(doc.endpointLink);
            if (StringUtils.isEmpty(expectedEndpoint)) {
                assertEquals("EndpointLink should be empty", expectedEndpoint, doc.endpointLink);
            } else {
                assertThat(String.format("EndpointLinks should have: %s", expectedEndpoint),
                        doc.endpointLinks, CoreMatchers.hasItem(expectedEndpoint));
            }
            assertEquals(doc.computeHostLink, computeHostLink);
            assertNotNull(doc.documentCreationTimeMicros);
        }

        List<String> networkDocLinks = getDocumentLinks(NetworkService.NetworkState.class, expectedEndpoint);
        this.host.log("NetworkState docs size: " + networkDocLinks.size());
        for (String docLink : networkDocLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            assertTrue("Error retrieving state",
                    response.getStatusCode() == 200);
            NetworkService.NetworkState doc = response.getBody(NetworkService.NetworkState.class);

            assertNotNull(doc.endpointLink);
            if (StringUtils.isEmpty(expectedEndpoint)) {
                assertEquals("EndpointLink should be empty", expectedEndpoint, doc.endpointLink);
            } else {
                assertThat(String.format("EndpointLinks should have: %s", expectedEndpoint),
                        doc.endpointLinks, CoreMatchers.hasItem(expectedEndpoint));
            }
            assertEquals(doc.computeHostLink, computeHostLink);
            assertNotNull(doc.documentCreationTimeMicros);
        }

        List<String> subnetDocLinks = getDocumentLinks(SubnetService.SubnetState.class, expectedEndpoint);
        this.host.log("SubnetState docs size: " + subnetDocLinks.size());
        for (String docLink : subnetDocLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            assertTrue("Error retrieving state",
                    response.getStatusCode() == 200);
            SubnetService.SubnetState doc = response.getBody(SubnetService.SubnetState.class);

            assertNotNull(doc.endpointLink);
            if (StringUtils.isEmpty(expectedEndpoint)) {
                assertEquals("EndpointLink should be empty", expectedEndpoint, doc.endpointLink);
            } else {
                assertThat(String.format("EndpointLinks should have: %s", expectedEndpoint),
                        doc.endpointLinks, CoreMatchers.hasItem(expectedEndpoint));
            }
            assertEquals(doc.computeHostLink, computeHostLink);
            assertNotNull(doc.documentCreationTimeMicros);
        }

        List<String> nicDocLinks = getDocumentLinks(
                NetworkInterfaceService.NetworkInterfaceState.class, expectedEndpoint);
        this.host.log("NetworkInterfaceState docs size: " + nicDocLinks.size());
        for (String docLink : nicDocLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            assertTrue("Error retrieving state",
                    response.getStatusCode() == 200);
            NetworkInterfaceService.NetworkInterfaceState doc = response
                    .getBody(NetworkInterfaceService.NetworkInterfaceState.class);

            assertNotNull(doc.endpointLink);
            if (StringUtils.isEmpty(expectedEndpoint)) {
                assertEquals("EndpointLink should be empty", expectedEndpoint, doc.endpointLink);
            } else {
                assertThat(String.format("EndpointLinks should have: %s", expectedEndpoint),
                        doc.endpointLinks, CoreMatchers.hasItem(expectedEndpoint));
            }
            assertEquals(doc.computeHostLink, computeHostLink);
            assertNotNull(doc.documentCreationTimeMicros);
        }

        List<String> sgDocLinks = getDocumentLinks(SecurityGroupService.SecurityGroupState.class, expectedEndpoint);
        this.host.log("SecurityGroupState docs size: " + sgDocLinks.size());
        for (String docLink : sgDocLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            assertTrue("Error retrieving state",
                    response.getStatusCode() == 200);
            SecurityGroupService.SecurityGroupState doc = response
                    .getBody(SecurityGroupService.SecurityGroupState.class);

            assertNotNull(doc.endpointLink);
            if (StringUtils.isEmpty(expectedEndpoint)) {
                assertEquals("EndpointLink should be empty", expectedEndpoint, doc.endpointLink);
            } else {
                assertThat(String.format("EndpointLinks should have: %s", expectedEndpoint),
                        doc.endpointLinks, CoreMatchers.hasItem(expectedEndpoint));
            }
            assertEquals(doc.computeHostLink, computeHostLink);
            assertNotNull(doc.documentCreationTimeMicros);
        }
    }

    private List<String> getDocumentLinks(Class resourceState, String endpointLink) {
        QueryTask.Query.Builder qBuilder = QueryTask.Query.Builder.create()
                .addKindFieldClause(resourceState);
        if (endpointLink != null && !endpointLink.equals("")) {
            qBuilder.addFieldClause(ENDPOINT_LINKS_FIELD_CLAUSE, endpointLink);
        } else {
            qBuilder.addFieldClause(ENDPOINT_LINKS_FIELD_CLAUSE, "*",
                    QueryTask.QueryTerm.MatchType.WILDCARD,
                    QueryTask.Query.Occurance.MUST_NOT_OCCUR);
        }

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .addOption(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT)
                .setQuery(qBuilder.build())
                .build();

        this.host.createQueryTaskService(queryTask, false, true, queryTask, null);
        return queryTask.results.documentLinks;
    }
}