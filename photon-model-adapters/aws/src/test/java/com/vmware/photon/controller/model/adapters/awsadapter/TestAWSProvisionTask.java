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
import static org.junit.Assert.assertTrue;

import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.AWS_VM_REQUEST_TIMEOUT_MINUTES;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSComputeHost;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSResourcePool;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSVMResource;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.getAwsInstancesByIds;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.getCompute;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.setAwsClientMockInfo;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.verifyRemovalOfResourceState;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestUtils.getExecutor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Tag;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse.ComputeStats;
import com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.BaseLineState;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;
import com.vmware.photon.controller.model.tasks.TestUtils;

import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Test to provision a VM instance on AWS and tear it down
 * The test exercises the AWS instance adapter to create the VM
 * All public fields below can be specified via command line arguments
 * If the 'isMock' flag is set to true the test runs the adapter in mock
 * mode and does not actually create a VM.
 * Minimally the accessKey and secretKey for AWS must be specified.
 *
 */
public class TestAWSProvisionTask {

    private static final String INSTANCEID_PREFIX = "i-";
    private VerificationHost host;
    // fields that are used across method calls, stash them as private fields
    private ComputeService.ComputeState vmState;
    public String accessKey = "accessKey";
    public String secretKey = "secretKey";
    public boolean isMock = true;
    public boolean isAwsClientMock = false;
    public String awsMockEndpointReference = null;
    private AmazonEC2AsyncClient client;
    public int timeElapsedSinceLastCollectionInMinutes = 4;


    @Before
    public void setUp() throws Exception {
        CommandLineArgumentParser.parseFromProperties(this);

        setAwsClientMockInfo(this.isAwsClientMock, this.awsMockEndpointReference);
        AuthCredentialsServiceState creds = new AuthCredentialsServiceState();
        creds.privateKey = this.secretKey;
        creds.privateKeyId = this.accessKey;
        this.client = AWSUtils.getAsyncClient(creds, TestAWSSetupUtils.zoneId, getExecutor());
        this.host = VerificationHost.create(0);
        try {
            this.host.setMaintenanceIntervalMicros(TimeUnit.MILLISECONDS.toMicros(250));
            this.host.start();
            PhotonModelServices.startServices(this.host);
            PhotonModelTaskServices.startServices(this.host);
            AWSAdapters.startServices(this.host);

            this.host.setTimeoutSeconds(600);

            this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
            this.host.waitForServiceAvailable(AWSAdapters.LINKS);
        } catch (Throwable e) {
            throw new Exception(e);
        }
    }

    @After
    public void tearDown() throws InterruptedException {
        if (this.host == null) {
            return;
        }
        // try to delete the VMs
        if (this.vmState != null && this.vmState.id.startsWith(INSTANCEID_PREFIX)) {
            try {
                TestAWSSetupUtils.deleteVMs(this.vmState.documentSelfLink, this.isMock, this.host);
            } catch (Throwable deleteEx) {
                // just log and move on
                this.host.log(Level.WARNING, "Exception deleting VM - %s", deleteEx.getMessage());
            }
        }
        this.host.tearDownInProcessPeers();
        this.host.toggleNegativeTestMode(false);
        this.host.tearDown();

        setAwsClientMockInfo(false, null);
    }

    // Creates a AWS instance via a provision task.
    @Test
    public void testProvision() throws Throwable {

        // Create a resource pool where the VM will be housed
        ResourcePoolState outPool = createAWSResourcePool(this.host);

        // create a compute host for the AWS EC2 VM
        ComputeService.ComputeState outComputeHost = createAWSComputeHost(this.host,
                outPool.documentSelfLink, this.accessKey, this.secretKey,
                this.isAwsClientMock, this.awsMockEndpointReference, null);

        // create a AWS VM compute resoruce

        this.vmState = createAWSVMResource(this.host, outComputeHost.documentSelfLink,
                outPool.documentSelfLink, this.getClass(), null);

        // kick off a provision task to do the actual VM creation
        ProvisionComputeTaskState provisionTask = new ProvisionComputeTaskService.ProvisionComputeTaskState();

        provisionTask.computeLink = this.vmState.documentSelfLink;
        provisionTask.isMockRequest = this.isMock;
        provisionTask.taskSubStage = ProvisionComputeTaskState.SubStage.CREATING_HOST;
        // Wait for default request timeout in minutes for the machine to be powered ON before
        // reporting failure to the parent task.
        provisionTask.documentExpirationTimeMicros = Utils.getNowMicrosUtc()
                + TimeUnit.MINUTES.toMicros(AWS_VM_REQUEST_TIMEOUT_MINUTES);

        ProvisionComputeTaskService.ProvisionComputeTaskState outTask = TestUtils.doPost(this.host,
                provisionTask,
                ProvisionComputeTaskState.class,
                UriUtils.buildUri(this.host,
                        ProvisionComputeTaskService.FACTORY_LINK));

        this.host.waitForFinishedTask(ProvisionComputeTaskState.class, outTask.documentSelfLink);

        // check that the VM has been created
        ProvisioningUtils.queryComputeInstances(this.host, 2);

        if (!this.isMock) {
            ComputeState compute = getCompute(this.host, this.vmState.documentSelfLink);
            List<Instance> instances = getAwsInstancesByIds(this.client, this.host,
                    Collections.singletonList(compute.id));
            assertTags(Collections.emptySet(), instances.get(0), this.vmState.name);
        }

        this.host.setTimeoutSeconds(600);
        this.host.waitFor("Error waiting for stats with default collection windows", () -> {
            try {
                this.host.log(Level.INFO,
                        "Issuing stats request for VM with default collection window.");
                issueStatsRequest(this.vmState, null);
            } catch (Throwable t) {
                return false;
            }
            return true;
        });

        this.host.waitFor("Error waiting for host stats", () -> {
            try {
                this.host.log(Level.INFO,
                        "Issuing stats request for compute host with default collection window.");
                issueStatsRequest(outComputeHost, null);
            } catch (Throwable t) {
                return false;
            }
            return true;
        });

        this.host.waitFor("Error waiting for stats with explicit collection window", () -> {
            try {
                this.host.log(Level.INFO,
                        "Issuing stats request for compute host with explicit collection window.");
                Long currentTime = Utils.getNowMicrosUtc();
                Long lastCollectionTimeInMicros = currentTime
                        - TimeUnit.MINUTES.toMicros(this.timeElapsedSinceLastCollectionInMinutes);
                issueStatsRequest(outComputeHost, lastCollectionTimeInMicros);
            } catch (Throwable t) {
                return false;
            }
            return true;
        });

        // store the network links and disk links for removal check later
        List<String> resourcesToDelete = new ArrayList<>();
        if (this.vmState.diskLinks != null) {
            resourcesToDelete.addAll(this.vmState.diskLinks);
        }
        if (this.vmState.networkInterfaceLinks != null) {
            resourcesToDelete.addAll(this.vmState.networkInterfaceLinks);
        }

        // delete vm
        TestAWSSetupUtils.deleteVMs(this.vmState.documentSelfLink, this.isMock, this.host);

        // validates the local documents of network links and disk links have been removed
        verifyRemovalOfResourceState(this.host, resourcesToDelete);

        // create another AWS VM
        List<String> instanceIdList = new ArrayList<>();

        Set<TagState> tags = createTags(null,
                "testProvisionKey1", "testProvisionValue1",
                "testProvisionKey2", "testProvisionValue2");

        Set<String> tagLinks = tags.stream().map(t -> t.documentSelfLink)
                .collect(Collectors.toSet());
        this.vmState = TestAWSSetupUtils.createAWSVMResource(this.host, outComputeHost.documentSelfLink,
                outPool.documentSelfLink, this.getClass(), tagLinks);

        TestAWSSetupUtils.provisionMachine(this.host, this.vmState, this.isMock, instanceIdList);

        BaseLineState remoteStateBefore = null;
        if (!this.isMock) {
            ComputeState compute = getCompute(this.host, this.vmState.documentSelfLink);

            List<Instance> instances = getAwsInstancesByIds(this.client, this.host,
                    Collections.singletonList(compute.id));
            assertTags(tags, instances.get(0), this.vmState.name);

            // reach out to AWS and get the current state
            remoteStateBefore = TestAWSSetupUtils
                    .getBaseLineInstanceCount(this.host, this.client, null);
        }

        // delete just the local representation of the resource
        TestAWSSetupUtils.deleteVMs(this.vmState.documentSelfLink, this.isMock, this.host, true);
        if (!this.isMock) {
            try {
                BaseLineState remoteStateAfter = TestAWSSetupUtils
                        .getBaseLineInstanceCount(this.host, this.client, null);

            } finally {
                TestAWSSetupUtils.deleteVMsUsingEC2Client(this.client, this.host, instanceIdList);
            }
        }
        this.vmState = null;
    }

    private void assertTags(Set<TagState> expectedTagStates, Instance instance,
            String instanceName) {
        Set<Tag> expectedTags = expectedTagStates.stream().map(ts -> new Tag(ts.key, ts.value))
                .collect(Collectors.toSet());

        Set<Tag> actualTags = new HashSet<>(instance.getTags());
        // account for the name tag
        assertEquals(expectedTags.size() + 1, actualTags.size());
        assertTrue(actualTags.containsAll(expectedTags));

        Tag nameTag = new Tag(AWSConstants.AWS_TAG_NAME, instanceName);
        assertTrue(actualTags.contains(nameTag));
    }

    private Set<TagState> createTags(List<String> tenantLinks, String... keyValue)
            throws Throwable {

        Set<TagState> result = new HashSet<>();

        for (int i = 0; i <= keyValue.length - 2; i = i + 2) {
            TagState tagState = new TagState();
            tagState.tenantLinks = tenantLinks;
            tagState.key = keyValue[i];
            tagState.value = keyValue[i + 1];

            TagState response = TestUtils.doPost(this.host, tagState, TagState.class,
                    UriUtils.buildUri(this.host, TagService.FACTORY_LINK));

            result.add(response);
        }

        return result;
    }

    private void issueStatsRequest(ComputeState vm, Long lastCollectionTime) throws Throwable {
        // spin up a stateless service that acts as the parent link to patch back to
        StatelessService parentService = new StatelessService() {
            @Override
            public void handleRequest(Operation op) {
                if (op.getAction() == Action.PATCH) {
                    if (!TestAWSProvisionTask.this.isMock) {
                        ComputeStatsResponse resp = op.getBody(ComputeStatsResponse.class);
                        if (resp.statsList.size() != 1) {
                            TestAWSProvisionTask.this.host.failIteration(
                                    new IllegalStateException("response size was incorrect."));
                            return;
                        }
                        // Size == 1, because APICallCount
                        if (resp.statsList.get(0).statValues.size() == 1) {
                            TestAWSProvisionTask.this.host.failIteration(new IllegalStateException(
                                    "incorrect number of metrics received."));
                            return;
                        }
                        // If the last collection time was set to collect stats for previous windows
                        // and they are not collected then fail the current iteration
                        if (lastCollectionTime != null) {
                            if (resp.statsList.get(0).statValues
                                    .get(PhotonModelConstants.CPU_UTILIZATION_PERCENT)
                                    .size() < 2) {
                                TestAWSProvisionTask.this.host
                                        .failIteration(new IllegalStateException(
                                                "incorrect number of data points received when collection window is specified."));
                                return;
                            }

                        }
                        if (!resp.statsList.get(0).computeLink.equals(vm.documentSelfLink)) {
                            TestAWSProvisionTask.this.host.failIteration(new IllegalStateException(
                                    "Incorrect resourceReference returned."));
                            return;
                        }
                        verifyCollectedStats(resp, lastCollectionTime);
                    }
                    TestAWSProvisionTask.this.host.completeIteration();
                }
            }
        };
        String servicePath = UUID.randomUUID().toString();
        Operation startOp = Operation.createPost(UriUtils.buildUri(this.host, servicePath));
        this.host.startService(startOp, parentService);
        ComputeStatsRequest statsRequest = new ComputeStatsRequest();
        statsRequest.resourceReference = UriUtils.buildUri(this.host, vm.documentSelfLink);
        statsRequest.isMockRequest = this.isMock;
        statsRequest.taskReference = UriUtils.buildUri(this.host, servicePath);
        if (lastCollectionTime != null) {
            statsRequest.lastCollectionTimeMicrosUtc = lastCollectionTime;
        }
        this.host.sendAndWait(Operation.createPatch(UriUtils.buildUri(
                this.host, AWSUriPaths.AWS_STATS_ADAPTER))
                .setBody(statsRequest)
                .setReferer(this.host.getUri()));
    }

    private void verifyCollectedStats(ComputeStatsResponse response, Long lastCollectionTime) {
        ComputeStats computeStats = response.statsList.get(0);
        assertTrue("Compute Link is empty", !computeStats.computeLink.isEmpty());
        assertTrue("APICallCount is not present", computeStats.statValues.keySet()
                .contains(PhotonModelConstants.API_CALL_COUNT));
        // Check that stat values are accompanied with Units.
        for (String key : computeStats.statValues.keySet()) {
            List<ServiceStat> stats = computeStats.statValues.get(key);
            for (ServiceStat stat : stats) {
                assertTrue("Unit is empty", !stat.unit.isEmpty());
                // Check if burn rate values are positive.
                if (key.equalsIgnoreCase(PhotonModelConstants.AVERAGE_BURN_RATE_PER_HOUR)) {
                    assertTrue("Average burn rate is negative", stat.latestValue >= 0);
                }

                if (key.equalsIgnoreCase(PhotonModelConstants.CURRENT_BURN_RATE_PER_HOUR)) {
                    assertTrue("Current burn rate is negative", stat.latestValue >= 0);
                }
            }
            // If the statsCollectionTime was set to sometime in the past, the adapter should be
            // collecting more than one value for the same metric. Using cpu utilization as an
            // representative case as the number
            // of data points can vary across metrics even if the window is set when requesting data
            // from the provider.
            if (lastCollectionTime != null
                    && key.equalsIgnoreCase(PhotonModelConstants.CPU_UTILIZATION_PERCENT)) {
                assertTrue(
                        "incorrect number of data points received when collection window is specified for metric ."
                                + key,
                        stats.size() > 1);
            }

            // Check if the datapoints collected are after the lastCollectionTime.
            if (lastCollectionTime != null
                    && key.equalsIgnoreCase(PhotonModelConstants.ESTIMATED_CHARGES)) {
                for (ServiceStat stat : stats) {
                    assertTrue("The datapoint collected is older than last collection time.",
                            stat.sourceTimeMicrosUtc >= lastCollectionTime);
                }
            }
        }
    }
}