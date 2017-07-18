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

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.tagResourcesWithName;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSAuthentication;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSComputeHost;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSResourcePool;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.deleteVMsOnThisEndpoint;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.deleteVMsUsingEC2Client;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.enumerateResources;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.getBaseLineInstanceCount;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.getComputeByAWSId;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.instanceType;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.provisionAWSVMWithEC2Client;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.regionId;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.setAwsClientMockInfo;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.setUpTestVpc;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.tearDownTestVpc;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.waitForInstancesToBeTerminated;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.waitForProvisioningToComplete;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.zoneId;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestUtils.getExecutor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.PhotonModelMetricServices;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse;
import com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.BaseLineState;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService.SingleResourceTaskCollectionStage;

import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 *
 * Minimally the accessKey and secretKey for AWS must be specified must be provided to run the test and the isMock
 * flag needs to be turned OFF.
 *
 */
public class TestAWSEnumerationAtScale extends BasicReusableHostTestCase {
    private static final float HUNDERED = 100.0f;
    private static final int MOCK_STATS_SIZE = 4;

    public ComputeService.ComputeState vmState;

    public ComputeState computeHost;
    public EndpointState endpointState;

    public AuthCredentialsServiceState creds;

    public static final String EC2_IMAGEID = "ami-0d4cfd66";
    public static final String T2_NANO_INSTANCE_TYPE = "t2.nano";
    public static final String DEFAULT_SECURITY_GROUP_NAME = "cell-manager-security-group";
    public static final String SCALE_VM_NAME = "scale-test-vm";
    public static final String TEST_CASE_BASELINE_VMs = "Baseline VMs on AWS ";
    public static final String TEST_CASE_INITIAL_RUN_AT_SCALE = "Initial Run at Scale ";
    public static final String TEST_CASE_DISCOVER_UPDATES_AT_SCALE = "Discover Updates at Scale ";
    public static final String TEST_CASE_DISCOVER_DELETES_AT_SCALE = "Discover Deletes at Scale ";

    public static List<String> instancesToCleanUp = new ArrayList<String>();
    public static List<String> instanceIds = new ArrayList<String>();
    public static List<Boolean> provisioningFlags;
    public static List<Boolean> deletionFlags = new ArrayList<Boolean>();

    public List<String> instanceIdsToDelete = new ArrayList<String>();
    public AmazonEC2AsyncClient client;
    public BaseLineState baseLineState;
    public boolean isMock = true;
    public boolean isAwsClientMock = false;
    public String awsMockEndpointReference = null;
    public String accessKey = "accessKey";
    public String secretKey = "secretKey";
    public int instanceCountAtScale = 10;
    public int batchSize = 50;
    public int errorRate = 5;
    public int modifyRate = 10;
    public int awsAccountLimit = 1000;

    public static List<String> testComputeDescriptions = new ArrayList<String>(
            Arrays.asList(zoneId + "~" + T2_NANO_INSTANCE_TYPE,
                    zoneId + "~" + instanceType));

    private Map<String, Object> awsTestContext;
    private String subnetId;
    private String securityGroupId;

    @Before
    public void setUp() throws Exception {
        CommandLineArgumentParser.parseFromProperties(this);

        setAwsClientMockInfo(this.isAwsClientMock, this.awsMockEndpointReference);
        // create credentials
        this.creds = new AuthCredentialsServiceState();
        this.creds.privateKey = this.secretKey;
        this.creds.privateKeyId = this.accessKey;
        this.client = AWSUtils.getAsyncClient(this.creds, TestAWSSetupUtils.regionId, getExecutor());

        this.awsTestContext = new HashMap<>();
        setUpTestVpc(this.client, this.awsTestContext, this.isMock);
        this.subnetId = (String) this.awsTestContext.get(TestAWSSetupUtils.SUBNET_KEY);
        this.securityGroupId = (String) this.awsTestContext.get(TestAWSSetupUtils.SECURITY_GROUP_KEY);

        try {
            PhotonModelServices.startServices(this.host);
            PhotonModelMetricServices.startServices(this.host);
            PhotonModelTaskServices.startServices(this.host);
            PhotonModelAdaptersRegistryAdapters.startServices(this.host);
            AWSAdapters.startServices(this.host);
            // start the aws mock stats service
            this.host.startService(
                    Operation.createPost(UriUtils.buildUri(this.host, AWSMockStatsService.class)),
                    new AWSMockStatsService());

            this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelAdaptersRegistryAdapters.LINKS);
            this.host.waitForServiceAvailable(AWSAdapters.LINKS);
            this.host.waitForServiceAvailable(AWSMockStatsService.SELF_LINK);

            // TODO: VSYM-992 - improve test/remove arbitrary timeout
            this.host.setTimeoutSeconds(200);

            // create the compute host, resource pool and the VM state to be used in the test.
            initResourcePoolAndComputeHost();
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
        try {
            // Delete all vms from the endpoint that were created from the test
            this.host.log("Deleting %d instance created from the test ", instancesToCleanUp.size());
            bounceAWSClient();
            if (instancesToCleanUp.size() > 0) {
                int initialCount = instancesToCleanUp.size() % this.batchSize > 0
                        ? (instancesToCleanUp.size() % this.batchSize) : this.batchSize;
                boolean firstDeletionCycle = true;
                int oldIndex = 0;
                List<String> instanceBatchToDelete = new ArrayList<String>();
                for (int totalDeletedInstances = initialCount; totalDeletedInstances <= instancesToCleanUp
                        .size(); totalDeletedInstances += this.batchSize) {
                    if (firstDeletionCycle) {
                        instanceBatchToDelete = instancesToCleanUp.subList(0, initialCount);
                        firstDeletionCycle = false;
                    } else {
                        instanceBatchToDelete = instancesToCleanUp.subList(oldIndex,
                                totalDeletedInstances);
                    }
                    oldIndex = totalDeletedInstances;
                    this.host.log("Deleting %d instances", instanceBatchToDelete.size());
                    deleteVMsOnThisEndpoint(this.host, this.isMock,
                            this.computeHost.documentSelfLink,
                            instanceBatchToDelete);
                    // Check that all the instances that are required to be deleted are in
                    // terminated state on AWS
                    waitForInstancesToBeTerminated(this.client, this.host, instanceBatchToDelete);
                }
            }
            tearDownTestVpc(this.client, this.host, this.awsTestContext, this.isMock);
            this.client.shutdown();
            setAwsClientMockInfo(false, null);
        } catch (Throwable deleteEx) {
            // just log and move on
            this.host.log(Level.WARNING, "Exception deleting VMs - %s", deleteEx.getMessage());
        }
    }

    /**
     * Re-initializes the AWS client so that the outstanding memory buffers and threads are released back.
     */
    private void bounceAWSClient() {
        this.client.shutdown();
        this.client = AWSUtils.getAsyncClient(this.creds, TestAWSSetupUtils.regionId, getExecutor());
    }

    @Test
    public void testEnumerationAtScale() throws Throwable {
        if (!this.isMock) {
            this.host.setTimeoutSeconds(600);
            this.baseLineState = getBaseLineInstanceCount(this.host, this.client, testComputeDescriptions);
            // Run data collection when there are no resources on the AWS endpoint. Ensure that
            // there are no failures if the number of discovered instances is 0.
            enumerateResources(this.host, this.computeHost, this.endpointState, this.isMock,
                    TEST_CASE_BASELINE_VMs);
            // Check if the requested number of instances are under the set account limits
            if ((this.baseLineState.baselineVMCount + this.instanceCountAtScale) >= this.awsAccountLimit) {
                this.host.log(
                        "Requested number of resources will exceed account limit. Reducing number"
                        + " of requested instances");
                this.instanceCountAtScale = this.awsAccountLimit - this.baseLineState.baselineVMCount;
            }
            // Create {instanceCountAtScale} VMs on AWS
            this.host.log("Running scale test by provisioning %d instances", this.instanceCountAtScale);
            int initialCount = this.instanceCountAtScale % this.batchSize > 0
                    ? (this.instanceCountAtScale % this.batchSize) : this.batchSize;
            boolean firstSpawnCycle = true;
            for (int totalSpawnedInstances = initialCount; totalSpawnedInstances <= this.instanceCountAtScale; totalSpawnedInstances += this.batchSize) {
                int instancesToSpawn = this.batchSize;
                if (firstSpawnCycle) {
                    instancesToSpawn = initialCount;
                    firstSpawnCycle = false;
                }
                instanceIds = provisionAWSVMWithEC2Client(this.client, this.host, instancesToSpawn,
                        instanceType, this.subnetId, this.securityGroupId);
                instancesToCleanUp.addAll(instanceIds);
                this.host.log("Instances to cleanup is %d", instancesToCleanUp.size());
                waitForProvisioningToComplete(instanceIds, this.host, this.client, this.errorRate);
                this.host.log("Instances have turned on");
            }
            enumerateResources(this.host, this.computeHost, this.endpointState, this.isMock,
                    TEST_CASE_INITIAL_RUN_AT_SCALE);

            this.vmState = getComputeByAWSId(this.host, instanceIds.get(0));

            if (this.isAwsClientMock) {
                this.host.setTimeoutSeconds(600);

                this.host.waitFor("Error waiting for stats", () -> {
                    try {
                        issueMockStatsRequest(this.vmState);
                    } catch (Throwable t) {
                        return false;
                    }
                    return true;
                });

                this.host.waitFor("Error waiting for host stats", () -> {
                    try {
                        issueMockStatsRequest(this.computeHost);
                    } catch (Throwable t) {
                        return false;
                    }
                    return true;
                });

            }

            // UPDATE some percent of the spawned instances to have a tag
            int instancesToTagCount = (int) ((this.instanceCountAtScale / HUNDERED) * this.modifyRate);
            this.host.log("Updating %d instances", instancesToTagCount);
            List<String> instanceIdsToTag = instancesToCleanUp.subList(0, instancesToTagCount);

            tagResourcesWithName(this.client, SCALE_VM_NAME,
                    instanceIdsToTag.toArray(new String[0]));

            // Record the time taken to discover updates to a subset of the instances.
            enumerateResources(this.host, this.computeHost, this.endpointState, this.isMock,
                    TEST_CASE_DISCOVER_UPDATES_AT_SCALE);

            // DELETE some percent of the instances
            this.host.log("Deleting %d instances", instancesToTagCount);
            deleteVMsUsingEC2Client(this.client, this.host, instanceIdsToTag);
            // Record time spent in enumeration to discover the deleted instances and delete them.
            enumerateResources(this.host, this.computeHost, this.endpointState, this.isMock,
                    TEST_CASE_DISCOVER_DELETES_AT_SCALE);
        } else {
            // Do nothing. Basic enumeration logic tested in functional test.
        }
    }

    /**
     * Verify whether the mock stats gathered are correct or not.
     * @param vm The AWS VM compute state.
     * @throws Throwable
     */
    private void issueMockStatsRequest(ComputeService.ComputeState vm) throws Throwable {
        // spin up a stateless service that acts as the parent link to patch back to
        StatelessService parentService = new StatelessService() {
            @Override
            public void handleRequest(Operation op) {
                if (op.getAction() == Action.PATCH) {
                    ComputeStatsResponse resp = op.getBody(ComputeStatsResponse.class);
                    if (resp.statsList.size() != 1) {
                        TestAWSEnumerationAtScale.this.host.failIteration(
                                new IllegalStateException("response size was incorrect."));
                        return;
                    }
                    if (resp.statsList.get(0).statValues.size() != MOCK_STATS_SIZE) {
                        TestAWSEnumerationAtScale.this.host.failIteration(
                                new IllegalStateException("incorrect number of metrics received."));
                        return;
                    }
                    if (!resp.statsList.get(0).computeLink.equals(vm.documentSelfLink)) {
                        TestAWSEnumerationAtScale.this.host.failIteration(
                                new IllegalStateException("Incorrect resourceReference returned."));
                        return;
                    }
                    verifyCollectedMockStats(resp);

                    TestAWSEnumerationAtScale.this.host.completeIteration();
                }
            }
        };
        String servicePath = UUID.randomUUID().toString();
        Operation startOp = Operation.createPost(UriUtils.buildUri(this.host, servicePath));
        this.host.startService(startOp, parentService);
        ComputeStatsRequest statsRequest = new ComputeStatsRequest();
        statsRequest.resourceReference = UriUtils.buildUri(this.host, vm.documentSelfLink);
        statsRequest.isMockRequest = this.isMock;
        statsRequest.nextStage = SingleResourceTaskCollectionStage.UPDATE_STATS.name();
        statsRequest.taskReference = UriUtils.buildUri(this.host, servicePath);
        this.host.sendAndWait(Operation.createPatch(UriUtils.buildUri(
                this.host, AWSMockStatsService.SELF_LINK))
                .setBody(statsRequest)
                .setReferer(this.host.getUri()));
    }

    /**
     * Verify whether the mock stats gathered are correct or not.
     * @param response The stats response.
     */
    private void verifyCollectedMockStats(ComputeStatsResponse response) {
        ComputeStatsResponse.ComputeStats computeStats = response.statsList.get(0);
        Assert.assertTrue("Compute Link is empty", !computeStats.computeLink.isEmpty());
        // Check that stat values are accompanied with Units.
        for (String key : computeStats.statValues.keySet()) {
            List<ServiceStats.ServiceStat> stats = computeStats.statValues.get(key);
            for (ServiceStats.ServiceStat stat : stats) {
                Assert.assertTrue("Unit is empty", !stat.unit.isEmpty());
            }
        }
    }

    /**
     * Creates the state associated with the resource pool, compute host and the VM to be created.
     * @throws Throwable
     */
    public void initResourcePoolAndComputeHost() throws Throwable {
        // Create a resource pool where the VM will be housed
        ResourcePoolState resourcePool = createAWSResourcePool(this.host);

        AuthCredentialsServiceState auth = createAWSAuthentication(this.host, this.accessKey, this.secretKey);

        this.endpointState = TestAWSSetupUtils.createAWSEndpointState(this.host, auth.documentSelfLink, resourcePool.documentSelfLink);

        // create a compute host for the AWS EC2 VM
        this.computeHost = createAWSComputeHost(this.host,
                this.endpointState,
                zoneId, regionId,
                this.isAwsClientMock,
                this.awsMockEndpointReference, null /*tags*/);
    }

}
