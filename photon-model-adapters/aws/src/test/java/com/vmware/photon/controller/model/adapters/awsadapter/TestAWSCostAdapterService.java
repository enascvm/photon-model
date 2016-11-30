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

import static com.vmware.photon.controller.model.constants.PhotonModelConstants.DELETED_VM_COUNT;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse.ComputeStats;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSCsvBillParser;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService.SingleResourceStatsCollectionTaskState;

import com.vmware.xenon.common.BasicTestCase;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;

public class TestAWSCostAdapterService extends BasicTestCase {

    public static final String account1Id = "123456789";
    public static final String account2Id = "555555555";
    public static final String account1SelfLink = "account1SelfLink";
    public static final String account2SelfLink = "account2SelfLink";
    public static final double account1TotalCost = 100.0;
    public static final double account2TotalCost = 50.0;

    @Before
    public void setUp() throws Exception {
        CommandLineArgumentParser.parseFromProperties(this);

        try {
            PhotonModelServices.startServices(this.host);
            PhotonModelTaskServices.startServices(this.host);
            AWSAdapters.startServices(this.host);

            this.host.startService(
                    Operation.createPost(
                            UriUtils.buildUri(this.host, MockCostStatsAdapterService.class)),
                    new MockCostStatsAdapterService());

            this.host.setTimeoutSeconds(200);

            this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
            this.host.waitForServiceAvailable(AWSAdapters.LINKS);
        } catch (Throwable e) {
            this.host.log("Error starting up services for the test %s", e.getMessage());
            throw new Exception(e);
        }
    }

    @After
    public void tearDown() throws Exception {
        if (this.host == null) {
            return;
        }
        this.host.tearDownInProcessPeers();
        this.host.toggleNegativeTestMode(false);
        this.host.tearDown();
    }

    @Test
    public void testAwsBillParsingAndStatsCreation() throws Throwable {
        ComputeState account = new ComputeState();
        account.documentSelfLink = account1SelfLink;
        issueStatsRequest(account);
    }

    private void issueStatsRequest(ComputeState vm) throws Throwable {
        // spin up a stateless service that acts as the parent link to patch back to
        StatelessService parentService = new StatelessService() {
            @Override
            public void handleRequest(Operation op) {
                if (op.getAction() == Action.PATCH) {

                    SingleResourceStatsCollectionTaskState resp = op
                            .getBody(SingleResourceStatsCollectionTaskState.class);
                    if (resp.statsList.size() != 4) {
                        TestAWSCostAdapterService.this.host.failIteration(
                                new IllegalStateException("response size was incorrect."));
                        return;
                    }
                    if (!resp.statsList.get(0).computeLink.equals(vm.documentSelfLink)) {
                        TestAWSCostAdapterService.this.host
                                .failIteration(new IllegalStateException(
                                        "Incorrect resourceReference returned."));
                        return;
                    }
                    verifyCollectedStats(resp);

                    TestAWSCostAdapterService.this.host.completeIteration();
                }
            }
        };
        String servicePath = UUID.randomUUID().toString();
        Operation startOp = Operation.createPost(UriUtils.buildUri(this.host, servicePath));
        this.host.startService(startOp, parentService);
        ComputeStatsRequest statsRequest = new ComputeStatsRequest();
        statsRequest.resourceReference = UriUtils.buildUri(this.host, vm.documentSelfLink);
        statsRequest.taskReference = UriUtils.buildUri(this.host, servicePath);
        statsRequest.nextStage = SingleResourceStatsCollectionTaskService.SingleResourceTaskCollectionStage.UPDATE_STATS.name();
        this.host.sendAndWait(Operation.createPatch(UriUtils.buildUri(
                this.host, MockCostStatsAdapterService.SELF_LINK))
                .setBody(statsRequest)
                .setReferer(this.host.getUri()));
    }

    protected void verifyCollectedStats(SingleResourceStatsCollectionTaskState resp) {
        Map<String, ComputeStats> computeStatsByLink = resp.statsList.stream()
                .collect(Collectors.toMap(e -> e.computeLink, Function.identity()));
        ComputeStats computeStats = computeStatsByLink.get(account1SelfLink);
        ComputeStats account2Stats = computeStatsByLink.get(account2SelfLink);

        //check total account cost
        assertTrue(computeStats.statValues.get(AWSConstants.COST)
                .get(0).latestValue == account1TotalCost);

        // check VM count stats
        assertEquals(0, computeStats.statValues.get(DELETED_VM_COUNT).get(0).latestValue, 0.0);
        assertEquals(1, account2Stats.statValues.get(DELETED_VM_COUNT).get(0).latestValue, 0.0);

        // check that service level stats exist
        String serviceCode = AWSCsvBillParser.AwsServices.ec2.getName().replaceAll(" ", "");
        String serviceResourceCostMetric = String
                .format(AWSConstants.SERVICE_RESOURCE_COST, serviceCode);
        assertTrue(!computeStats.statValues.get(serviceResourceCostMetric).isEmpty());

        String serviceOtherCostMetric = String
                .format(AWSConstants.SERVICE_OTHER_COST, serviceCode);
        assertTrue(!computeStats.statValues.get(serviceOtherCostMetric).isEmpty());

        String serviceMonthlyOtherCostMetric = String
                .format(AWSConstants.SERVICE_MONTHLY_OTHER_COST, serviceCode);
        assertTrue(!computeStats.statValues.get(serviceMonthlyOtherCostMetric).isEmpty());

        String serviceReservedRecurringCostMetric = String
                .format(AWSConstants.SERVICE_RESERVED_RECURRING_COST, serviceCode);
        assertTrue(!computeStats.statValues.get(serviceReservedRecurringCostMetric).isEmpty());

        // Check that stat values are accompanied with Units.
        for (String key : computeStats.statValues.keySet()) {
            List<ServiceStat> stats = computeStats.statValues.get(key);
            for (ServiceStat stat : stats) {
                assertTrue("Unit is empty", !stat.unit.isEmpty());
            }
        }

        // Check the cost of linked account
        assertTrue(account2Stats.statValues.get(AWSConstants.COST)
                .get(0).latestValue == account2TotalCost);
    }
}
