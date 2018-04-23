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

import static com.vmware.photon.controller.discovery.endpoints.DataInitializationTaskService.INVOCATION_COUNT;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEYID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEY_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.REGION_KEY;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.Lists;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.discovery.cloudaccount.CloudAccountServices;
import com.vmware.photon.controller.discovery.common.SymphonyCommonTestUtils;
import com.vmware.photon.controller.discovery.common.services.CommonServices;
import com.vmware.photon.controller.discovery.endpoints.DataInitializationTaskService.DataInitializationState;
import com.vmware.photon.controller.discovery.onboarding.OnBoardingTestUtils;
import com.vmware.photon.controller.discovery.onboarding.OnboardingServices;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsRequest;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSAdapters;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService.SingleResourceStatsCollectionTaskState;
import com.vmware.xenon.common.BasicTestCase;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.ServiceSubscriptionState;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.common.test.VerificationHost;

public class TestDataInitializationTaskService extends BasicTestCase {
    public int numNodes = 3;

    private static final String USER_1_ID = "user1@org-1.com";
    private static final String USER_1_PASSWORD = "passwordforuser1";

    private static final String ORG_1_ID = "org-1";
    private static final String PROJECT_1_ID = "project-1";

    private URI peerUri;
    private String projectLink;

    @Override
    public void beforeHostStart(VerificationHost h) {
        h.setAuthorizationEnabled(true);
    }

    @Before
    public void setUp() throws Throwable {
        CommandLineArgumentParser.parseFromProperties(this);
        this.host.setUpPeerHosts(this.numNodes);
        this.host.joinNodesAndVerifyConvergence(this.numNodes, this.numNodes, true);
        this.host.setNodeGroupQuorum(this.numNodes);

        // start provisioning services on all the this.hosts
        this.host.setSystemAuthorizationContext();
        for (VerificationHost h : this.host.getInProcessHostMap().values()) {
            PhotonModelServices.startServices(h);
            PhotonModelTaskServices.startServices(h);
            AWSAdapters.startServices(h);
        }
        this.host.resetSystemAuthorizationContext();

        for (VerificationHost h : this.host.getInProcessHostMap().values()) {
            OnBoardingTestUtils.startCommonServices(h);
            h.setSystemAuthorizationContext();
            OnboardingServices.startServices(h, h::addPrivilegedService);
            CloudAccountServices.startServices(h, h::addPrivilegedService);
            EndpointServices.startServices(h, h::addPrivilegedService);
            CommonServices.startServices(h, h::addPrivilegedService);
            h.startService(new MockEnumerationAdapter());
            h.startService(new MockStatsAdapter());
            h.startService(new SlowMockEnumerationAdapter());
            h.resetAuthorizationContext();
        }

        OnBoardingTestUtils.waitForCommonServicesAvailability(this.host, this.host.getPeerHostUri());

        List<String> factories = new ArrayList<>();
        factories.add(DataInitializationTaskService.FACTORY_LINK);
        SymphonyCommonTestUtils
                .waitForFactoryAvailability(this.host, this.host.getPeerHostUri(), factories);

        this.peerUri = this.host.getPeerHostUri();

        OnBoardingTestUtils.setupUser(this.host, this.peerUri,
                OnBoardingTestUtils.createUserData(USER_1_ID, USER_1_PASSWORD));
        // Create an org using user-1, who becomes the admin by default
        OnBoardingTestUtils
                .setupOrganization(this.host, this.peerUri, ORG_1_ID, USER_1_ID, USER_1_PASSWORD);
        this.projectLink = OnBoardingTestUtils
                .setupProject(this.host, this.peerUri, PROJECT_1_ID, ORG_1_ID, USER_1_ID,
                        USER_1_PASSWORD);
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
    public void testDataSetup() throws Throwable {
        EndpointState endpointState = createEndpoint(EndpointType.aws.name());

        // direct task test
        DataInitializationState[] taskState = new DataInitializationState[1];
        taskState[0] = new DataInitializationState();
        taskState[0].endpoint = endpointState;
        taskState[0].tenantLinks = Collections.singleton(this.projectLink);
        taskState[0].taskInfo = TaskState.createDirect();

        TestContext testContext = new TestContext(1, Duration.ofSeconds(30));
        this.host.sendWithDeferredResult(
                Operation.createPost(
                        UriUtils.buildUri(this.peerUri, DataInitializationTaskService.FACTORY_LINK))
                        .setBody(taskState[0])
                        .forceRemote())
                .whenComplete((o, e) -> {
                    if (e != null) {
                        testContext.fail(e);
                        return;
                    }

                    taskState[0] = o.getBody(DataInitializationState.class);
                    testContext.complete();
                });
        testContext.await();
        assertNotNull(taskState[0]);
        assertEquals(TaskStage.FINISHED, taskState[0].taskInfo.stage);
        assertEquals(true, taskState[0].taskInfo.isDirect);
    }

    @Test
    public void testConcurrentEnumerations() throws Throwable {

        this.host.setTimeoutSeconds(180);
        List<EndpointState> endpointStateList = createDuplicateEndPoints(EndpointType.aws.name());

        // create data initialization task for endpoint1
        DataInitializationState[] taskState = new DataInitializationState[2];
        taskState[0] = new DataInitializationState();
        taskState[0].endpoint = endpointStateList.get(0);
        taskState[0].tenantLinks = Collections.singleton(this.projectLink);
        taskState[0].taskInfo = TaskState.createDirect();

        URI uri = UriUtils.buildUri(this.peerUri, DataInitializationTaskService.FACTORY_LINK);
        Operation op1 = Operation.createPost(uri).setBody(taskState[0]);
        this.host.send(op1);

        // this sleep is required so that task2 follows task1
        TimeUnit.SECONDS.sleep(5);

        // trigger data initialization task for endpoint2 and check its retry count state
        taskState[1] = new DataInitializationState();
        taskState[1].endpoint = endpointStateList.get(1);
        taskState[1].tenantLinks = Collections.singleton(this.projectLink);
        taskState[1].taskInfo = TaskState.createDirect();
        taskState[1].documentSelfLink = UriUtils.buildUriPath(DataInitializationTaskService.FACTORY_LINK, UUID.randomUUID().toString());
        Operation op2 = Operation.createPost(uri).setBody(taskState[1]);

        this.host.waitForResponse(op2);

        DataInitializationState dataInitializationState = this.host.getServiceState(null, DataInitializationState.class,
                ProvisioningUtils.createServiceURI(this.host, this.peerUri, taskState[1].documentSelfLink));
        String errorMessage = String.format(
                "Concurrent enumeration scenario not encountered. Retries remaining are %d and default wait count is %d",
                dataInitializationState.retriesRemaining,
                DataInitializationTaskService.DEFAULT_WAIT_COUNTS);
        Assert.assertTrue(errorMessage,
                dataInitializationState.retriesRemaining < DataInitializationTaskService.DEFAULT_WAIT_COUNTS);
    }

    @Test
    public void testDataSetupIdempotentPostTest() throws Throwable {
        EndpointState endpointState = createEndpoint(EndpointType.aws.name());
        TaskState taskInfo = TaskState.createDirect();

        // direct task test
        DataInitializationState[] taskState = new DataInitializationState[2];
        taskState[0] = new DataInitializationState();
        taskState[0].endpoint = endpointState;
        taskState[0].tenantLinks = Collections.singleton(this.projectLink);
        taskState[0].taskInfo = taskInfo;
        taskState[0].documentSelfLink = UriUtils.getLastPathSegment(endpointState.documentSelfLink);
        Operation op = this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, DataInitializationTaskService.FACTORY_LINK))
                .setBody(taskState[0])
                .forceRemote());

        taskState[0] = op.getBody(DataInitializationState.class);
        assertNotNull(taskState[0]);
        assertEquals(TaskStage.FINISHED, taskState[0].taskInfo.stage);
        assertEquals(true, taskState[0].taskInfo.isDirect);

        // Validate that the current stats on the document are 1 for 1 post.
        assertStats(taskState[0].documentSelfLink, 1.0);

        // Attempt to create a second data-initialization-task, but with the same documentSelfLink.
        // Since they share the same documentLink and DataInitializationService has IDEMPOTENT_POST
        // enabled, we expect the INVOCATION_COUNT to stay the same and the PUT to be ignored.
        taskState[0] = new DataInitializationState();
        taskState[0].endpoint = endpointState;
        taskState[0].tenantLinks = Collections.singleton(this.projectLink);
        taskState[0].taskInfo = taskInfo;
        taskState[0].documentSelfLink = UriUtils.getLastPathSegment(endpointState.documentSelfLink);
        this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, DataInitializationTaskService.FACTORY_LINK))
                .setBody(taskState[0])
                .forceRemote());

        taskState[0] = op.getBody(DataInitializationState.class);
        assertNotNull(taskState[0]);
        assertEquals(TaskStage.FINISHED, taskState[0].taskInfo.stage);
        assertEquals(true, taskState[0].taskInfo.isDirect);

        // Validate that the current stats on the old document are 1 for 1 post and 1 ignored PUT.
        assertStats(taskState[0].documentSelfLink, 1.0);

        // Attempt to create a second data-initialization-task, but with a different documentSelfLink.
        // We expect the INVOCATION_COUNT to increase because the selfLink is new.
        taskState[1] = new DataInitializationState();
        taskState[1].endpoint = createEndpoint(EndpointType.aws.name());
        taskState[1].tenantLinks = Collections.singleton(this.projectLink);
        taskState[1].taskInfo = TaskState.createDirect();
        taskState[1].documentSelfLink = UriUtils.getLastPathSegment(endpointState.documentSelfLink + "-different");

        this.host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(this.peerUri, DataInitializationTaskService.FACTORY_LINK))
                .setBody(taskState[1])
                .forceRemote());

        taskState[1] = op.getBody(DataInitializationState.class);
        assertNotNull(taskState[1]);
        assertEquals(TaskStage.FINISHED, taskState[1].taskInfo.stage);
        assertEquals(true, taskState[1].taskInfo.isDirect);

        // Validate that the current stats on the old document are 1 for 1 post and 1 ignored PUT.
        assertStats(taskState[0].documentSelfLink, 1.0);

        // Validate that the current stats on the new document are 1 for 1 post
        assertStats(taskState[1].documentSelfLink, 1.0);

        checkForSubscribers(taskState[0]);
        checkForSubscribers(taskState[1]);
    }

    @Test
    public void testDataSetupIgnoredAdapters() throws Throwable {
                // direct task test
        DataInitializationState[] taskState = new DataInitializationState[
                DataInitializationTaskService.IGNORED_ENDPOINT_ADAPTER_TYPES.size()];
        int i = 0;
        for (String adapterType : DataInitializationTaskService.IGNORED_ENDPOINT_ADAPTER_TYPES) {
            EndpointState endpointState = createEndpoint(adapterType);
            taskState[i] = new DataInitializationState();
            taskState[i].endpoint = endpointState;
            taskState[i].tenantLinks = Collections.singleton(this.projectLink);
            taskState[i].taskInfo = TaskState.createDirect();
            taskState[i].documentSelfLink = UriUtils.getLastPathSegment(endpointState.documentSelfLink);
            final int j = i;
            this.host.sendAndWait(Operation.createPost(
                    UriUtils.buildUri(this.peerUri, DataInitializationTaskService.FACTORY_LINK))
                    .setBody(taskState[i])
                    .forceRemote()
                    .setCompletion(this.host.getSafeHandler((op, ex) -> {
                        if (ex != null) {
                            this.host.failIteration(ex);
                            return;
                        }

                        taskState[j] = op.getBody(DataInitializationState.class);
                        assertNotNull(taskState[j]);
                        assertEquals(TaskStage.FINISHED, taskState[j].taskInfo.stage);
                        assertEquals(true, taskState[j].taskInfo.isDirect);
                    })));
            checkForSubscribers(taskState[j]);
            i++;
        }
    }

    @Test
    public void testDataSetupPutDirectly() throws Throwable {
        EndpointState endpointState = createEndpoint(EndpointType.aws.name());

        // direct task test
        DataInitializationState[] taskState = new DataInitializationState[1];
        taskState[0] = new DataInitializationState();
        taskState[0].endpoint = endpointState;
        taskState[0].tenantLinks = Collections.singleton(this.projectLink);
        taskState[0].taskInfo = TaskState.createDirect();
        taskState[0].documentSelfLink = UriUtils.getLastPathSegment(endpointState.documentSelfLink);
        this.host.sendAndWait(Operation.createPut(
                UriUtils.buildUri(this.peerUri, DataInitializationTaskService.FACTORY_LINK))
                .setBody(taskState[0])
                .forceRemote()
                .setCompletion(this.host.getSafeHandler((op, ex) -> {
                    Assert.assertNotNull(ex);
                    assertEquals(Operation.STATUS_CODE_BAD_REQUEST, op.getStatusCode());
                })));
    }

    /**
     * Checks the stats of a document for their invocation count to see how many times a document
     * POST was invoked.
     *
     * @param documentLink The documentLink to check.
     * @param expectedInvocationCount The expected number of POST invocations to have occurred.
     */
    private void assertStats(String documentLink, double expectedInvocationCount) {
        // Validate that the current stats on the document are 1 for 1 post and 1 ignored PUT
        Operation op = this.host.waitForResponse(Operation.createGet(
                UriUtils.buildStatsUri(UriUtils.buildUri(this.peerUri, documentLink)))
                .forceRemote());
        ServiceStats stats = op.getBody(ServiceStats.class);
        Assert.assertNotNull(stats.entries);
        assertEquals(expectedInvocationCount,
                stats.entries.get(INVOCATION_COUNT).latestValue, 0);
    }

    private void checkForSubscribers(DataInitializationState taskState) {
        this.host.waitFor("Subscriber deletion failed", () -> {
            URI subUri = UriUtils.buildSubscriptionUri(
                    UriUtils.buildUri(this.peerUri, taskState.documentSelfLink));
            Operation operation = this.host.waitForResponse(Operation.createGet(subUri));
            ServiceSubscriptionState body = operation.getBody(ServiceSubscriptionState.class);
            if (body.subscribers.size() == 0) {
                return true;
            }
            return false;
        });
    }

    private List<EndpointState> createDuplicateEndPoints(String adapterType) {
        ResourcePoolState rp = new ResourcePoolState();
        rp.name = UUID.randomUUID().toString();
        rp.id = rp.name;
        rp.documentSelfLink = rp.id;
        rp.tenantLinks = Collections.singletonList(this.projectLink);
        this.host.sendAndWaitExpectSuccess(Operation.createPost(
                UriUtils.buildUri(this.peerUri, ResourcePoolService.FACTORY_LINK))
                .setBody(rp));

        ComputeDescription hostDesc = new ComputeDescription();
        hostDesc.id = UUID.randomUUID().toString();
        hostDesc.name = hostDesc.id;
        hostDesc.documentSelfLink = hostDesc.id;
        hostDesc.supportedChildren = new ArrayList<>();
        hostDesc.supportedChildren.add(ComputeType.VM_GUEST.name());
        hostDesc.instanceAdapterReference = UriUtils.buildUri("http://mock-instance-adapter");
        hostDesc.enumerationAdapterReference = UriUtils.buildUri(
                this.peerUri, SlowMockEnumerationAdapter.SELF_LINK);
        hostDesc.statsAdapterReference = UriUtils.buildUri(
                this.peerUri, MockStatsAdapter.SELF_LINK);
        hostDesc.tenantLinks = Collections.singletonList(this.projectLink);
        this.host.sendAndWaitExpectSuccess(Operation.createPost(
                UriUtils.buildUri(this.peerUri, ComputeDescriptionService.FACTORY_LINK))
                .setBody(hostDesc));

        ComputeState computeHost = new ComputeState();
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

        EndpointState endpoint1 = new EndpointState();
        endpoint1.computeLink = UriUtils
                .buildUriPath(ComputeService.FACTORY_LINK, computeHost.documentSelfLink);
        endpoint1.computeDescriptionLink = UriUtils
                .buildUriPath(ComputeDescriptionService.FACTORY_LINK, computeHost.descriptionLink);
        endpoint1.endpointType = adapterType;
        endpoint1.name = "test-endpoint1";
        endpoint1.id = UUID.randomUUID().toString();
        endpoint1.documentSelfLink = UriUtils.buildUriPath(EndpointService.FACTORY_LINK, endpoint1.id);
        endpoint1.resourcePoolLink = UriUtils
                .buildUriPath(ResourcePoolService.FACTORY_LINK, rp.documentSelfLink);
        endpoint1.endpointProperties = new HashMap<>();
        endpoint1.endpointProperties.put(REGION_KEY, "test-regionId");
        endpoint1.endpointProperties.put(PRIVATE_KEY_KEY, "accessKey");
        endpoint1.endpointProperties.put(PRIVATE_KEYID_KEY, "secretKey");
        endpoint1.tenantLinks = Collections.singletonList(this.projectLink);

        this.host.sendAndWaitExpectSuccess(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointService.FACTORY_LINK))
                .setBody(endpoint1));

        EndpointState endpoint2 = new EndpointState();
        endpoint2.computeLink = UriUtils
                .buildUriPath(ComputeService.FACTORY_LINK, computeHost.documentSelfLink);
        endpoint2.computeDescriptionLink = UriUtils
                .buildUriPath(ComputeDescriptionService.FACTORY_LINK, computeHost.descriptionLink);
        endpoint2.endpointType = adapterType;
        endpoint2.name = "test-endpoint2";
        endpoint2.id = UUID.randomUUID().toString();
        endpoint2.documentSelfLink = UriUtils.buildUriPath(EndpointService.FACTORY_LINK, endpoint2.id);
        endpoint2.resourcePoolLink = UriUtils
                .buildUriPath(ResourcePoolService.FACTORY_LINK, rp.documentSelfLink);
        endpoint2.endpointProperties = new HashMap<>();
        endpoint2.endpointProperties.put(REGION_KEY, "test-regionId");
        endpoint2.endpointProperties.put(PRIVATE_KEY_KEY, "accessKey");
        endpoint2.endpointProperties.put(PRIVATE_KEYID_KEY, "secretKey");
        endpoint2.tenantLinks = Collections.singletonList(this.projectLink);

        this.host.sendAndWaitExpectSuccess(Operation.createPost(
                UriUtils.buildUri(this.peerUri, EndpointService.FACTORY_LINK))
                .setBody(endpoint2));

        return Lists.newArrayList(endpoint1, endpoint2);
    }

    private EndpointState createEndpoint(String adapterType) {
        ResourcePoolState rp = new ResourcePoolState();
        rp.name = UUID.randomUUID().toString();
        rp.id = rp.name;
        rp.documentSelfLink = rp.id;
        rp.tenantLinks = Collections.singletonList(this.projectLink);
        this.host.sendAndWaitExpectSuccess(Operation.createPost(
                UriUtils.buildUri(this.peerUri, ResourcePoolService.FACTORY_LINK))
                .setBody(rp));

        ComputeDescription hostDesc = new ComputeDescription();
        hostDesc.id = UUID.randomUUID().toString();
        hostDesc.name = hostDesc.id;
        hostDesc.documentSelfLink = hostDesc.id;
        hostDesc.supportedChildren = new ArrayList<>();
        hostDesc.supportedChildren.add(ComputeType.VM_GUEST.name());
        hostDesc.instanceAdapterReference = UriUtils.buildUri("http://mock-instance-adapter");
        hostDesc.enumerationAdapterReference = UriUtils.buildUri(
                this.peerUri, MockEnumerationAdapter.SELF_LINK);
        hostDesc.statsAdapterReference = UriUtils.buildUri(
                this.peerUri, MockStatsAdapter.SELF_LINK);
        hostDesc.tenantLinks = Collections.singletonList(this.projectLink);
        this.host.sendAndWaitExpectSuccess(Operation.createPost(
                UriUtils.buildUri(this.peerUri, ComputeDescriptionService.FACTORY_LINK))
                .setBody(hostDesc));

        ComputeState computeHost = new ComputeState();
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

        EndpointState endpoint = new EndpointState();
        endpoint.computeLink = UriUtils
                .buildUriPath(ComputeService.FACTORY_LINK, computeHost.documentSelfLink);
        endpoint.computeDescriptionLink = UriUtils
                .buildUriPath(ComputeDescriptionService.FACTORY_LINK, computeHost.descriptionLink);
        endpoint.endpointType = adapterType;
        endpoint.name = "test-endpoint";
        endpoint.id = UUID.randomUUID().toString();
        endpoint.documentSelfLink = endpoint.id;
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

    public static class SlowMockEnumerationAdapter extends StatelessService {
        public static String SELF_LINK = "/slow-mock-enumeration-adapter";

        @Override
        public void handlePatch(Operation op) {
            op.complete();
            // delay the request for some time.
            try {
                TimeUnit.SECONDS.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            ComputeEnumerateResourceRequest request = op
                    .getBody(ComputeEnumerateResourceRequest.class);
            TaskManager taskManager = new TaskManager(this, request.taskReference, null);
            taskManager.finishTask();
        }
    }

    public static class MockEnumerationAdapter extends StatelessService {
        public static String SELF_LINK = "/mock-enumeration-adapter";

        @Override
        public void handlePatch(Operation op) {
            op.complete();
            ComputeEnumerateResourceRequest request = op
                    .getBody(ComputeEnumerateResourceRequest.class);
            TaskManager taskManager = new TaskManager(this, request.taskReference, null);
            taskManager.finishTask();
        }
    }

    public static class MockStatsAdapter extends StatelessService {
        public static String SELF_LINK = "/mock-stats-adapter";

        @Override
        public void handlePatch(Operation op) {
            op.complete();
            ComputeStatsRequest request = op.getBody(ComputeStatsRequest.class);
            this.sendRequest(Operation.createPatch(request.taskReference)
                    .setBody(new SingleResourceStatsCollectionTaskState()));
        }
    }
}
