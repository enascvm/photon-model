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

import static com.vmware.photon.controller.model.adapters.awsadapter.MockCostStatsAdapterService.INSTANCE_1_SELF_LINK;
import static com.vmware.photon.controller.model.adapters.awsadapter.MockCostStatsAdapterService.INSTANCE_2_SELF_LINK;
import static com.vmware.xenon.services.common.QueryTask.NumericRange.createDoubleRange;
import static com.vmware.xenon.services.common.QueryTask.QuerySpecification.buildCompositeFieldName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.After;
import org.junit.Test;

import com.vmware.photon.controller.model.adapterapi.ComputeStatsRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse.ComputeStats;
import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSCsvBillParser;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSStatsNormalizer;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService.ResourceMetrics;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService.EndpointAllocationTaskState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService.SingleResourceStatsCollectionTaskState;
import com.vmware.photon.controller.model.tasks.monitoring.StatsCollectionTaskService;
import com.vmware.photon.controller.model.tasks.monitoring.StatsCollectionTaskService.StatsCollectionTaskState;

import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

public class TestAWSCostAdapterService extends BaseModelTest {

    public static final String account1Id = "123456789";
    public static final String account2Id = "555555555";
    public static final String account1SelfLink = "account1SelfLink";
    public static final String account2SelfLink = "account2SelfLink";
    public static final double account1TotalCost = 100.0;
    public static final double account2TotalCost = 50.0;
    public static final double instance1TotalCost = 0.0;
    public static final double instance2TotalCost = 0.00000001;

    public boolean isMock = true;
    public String accessKey = "accessKey";
    public String secretKey = "secretKey";

    @Override
    protected void startRequiredServices() throws Throwable {
        CommandLineArgumentParser.parseFromProperties(this);
        super.startRequiredServices();
        PhotonModelTaskServices.startServices(this.host);
        PhotonModelAdaptersRegistryAdapters.startServices(this.host);
        AWSAdapters.startServices(this.host);
        this.host.startService(
                Operation.createPost(
                        UriUtils.buildUri(this.host, MockCostStatsAdapterService.class)),
                new MockCostStatsAdapterService());
        this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
        this.host.waitForServiceAvailable(PhotonModelAdaptersRegistryAdapters.LINKS);
        this.host.waitForServiceAvailable(AWSAdapters.LINKS);
        this.host.setTimeoutSeconds(900);
        if (this.isMock) {
            // We run mock-tests against a dummy bill.
            // This bill is re-generated every time for current month
            TestUtils.generateCurrentMonthsBill();
        }
        System.setProperty(AWSCostStatsService.BATCH_SIZE_KEY, "1");
    }

    @After
    public void tearDown() throws Exception {
        if (this.isMock) {
            TestUtils.deleteCurrentMonthsBill();
        }
        System.clearProperty(AWSCostStatsService.BATCH_SIZE_KEY);
    }

    @Test
    public void testAwsBillParsingAndStatsCreation() throws Throwable {
        ComputeState account = new ComputeState();
        account.documentSelfLink = account1SelfLink;
        issueStatsRequest(account);
    }

    @Test
    public void testAwsCostAdapterEndToEnd() throws Throwable {
        if (this.isMock || new LocalDate(DateTimeZone.UTC).getDayOfMonth() == 1) {
            return;
        }
        ResourcePoolState resourcePool = TestAWSSetupUtils.createAWSResourcePool(this.host);
        EndpointState endpointState = new EndpointState();
        endpointState.resourcePoolLink = resourcePool.documentSelfLink;
        endpointState.endpointType = PhotonModelConstants.EndpointType.aws.name();
        endpointState.name = "test-aws-endpoint";
        endpointState.endpointProperties = new HashMap<>();
        endpointState.endpointProperties.put(EndpointConfigRequest.PRIVATE_KEY_KEY, this.secretKey);
        endpointState.endpointProperties
                .put(EndpointConfigRequest.PRIVATE_KEYID_KEY, this.accessKey);
        EndpointAllocationTaskState endpointAllocationTaskState =
                new EndpointAllocationTaskState();
        endpointAllocationTaskState.endpointState = endpointState;
        endpointAllocationTaskState.tenantLinks = Collections.singletonList("tenant-1");
        EndpointAllocationTaskState returnState = postServiceSynchronously(
                EndpointAllocationTaskService.FACTORY_LINK,
                endpointAllocationTaskState,
                EndpointAllocationTaskState.class);
        EndpointAllocationTaskState completeState = this.waitForServiceState(
                EndpointAllocationTaskState.class,
                returnState.documentSelfLink,
                state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                        .ordinal());

        System.setProperty(AWSCostStatsService.BILLS_BACK_IN_TIME_MONTHS_KEY, "1");

        triggerStatsCollection(resourcePool);
        verifyPersistedStats(completeState, AWSConstants.COST, 2);

        //Check if second iteration of adapter succeeds.
        triggerStatsCollection(resourcePool);
        verifyPersistedStats(completeState, AWSConstants.AWS_ACCOUNT_BILL_PROCESSED_TIME_MILLIS, 3);

        System.clearProperty(AWSCostStatsService.BILLS_BACK_IN_TIME_MONTHS_KEY);
    }

    private void verifyPersistedStats(EndpointAllocationTaskState completeState, String metric,
            int expectedCount) {
        this.host.waitFor("Timeout waiting for stats", () -> {
            QueryTask.QuerySpecification querySpec = new QueryTask.QuerySpecification();
            querySpec.query = QueryTask.Query.Builder.create()
                    .addKindFieldClause(ResourceMetrics.class)
                    .addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                            UriUtils.buildUriPath(ResourceMetricsService.FACTORY_LINK,
                                    UriUtils.getLastPathSegment(
                                            completeState.endpointState.computeLink)),
                            QueryTask.QueryTerm.MatchType.PREFIX)
                    .addRangeClause(buildCompositeFieldName(
                            ResourceMetrics.FIELD_NAME_ENTRIES, metric),
                            createDoubleRange(0.0, Double.MAX_VALUE, true, true))
                    .build();
            querySpec.options.add(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);
            ServiceDocumentQueryResult result = this.host
                    .createAndWaitSimpleDirectQuery(querySpec, expectedCount, expectedCount);
            boolean statsCollected = true;
            for (Object metrics : result.documents.values()) {
                ResourceMetrics rawMetrics = Utils.fromJson(metrics, ResourceMetrics.class);
                Double rawMetric = rawMetrics.entries.get(metric);
                if (rawMetric != null) {
                    continue;
                }
                statsCollected = false;
            }
            return statsCollected;
        });
    }

    private void triggerStatsCollection(ResourcePoolState pool) {
        StatsCollectionTaskState statCollectionState = new StatsCollectionTaskState();
        statCollectionState.resourcePoolLink = pool.documentSelfLink;
        statCollectionState.statsAdapterReference = UriUtils.buildUri(this.host,
                AWSCostStatsService.SELF_LINK);
        statCollectionState.documentSelfLink = "cost-stats-adapter";
        statCollectionState.options = EnumSet.of(TaskOption.SELF_DELETE_ON_COMPLETION);
        statCollectionState.taskInfo = TaskState.createDirect();
        Operation op = Operation.createPost(this.host, StatsCollectionTaskService.FACTORY_LINK)
                .setBody(statCollectionState).setReferer(this.host.getReferer());
        this.host.sendAndWaitExpectSuccess(op);
    }

    private void issueStatsRequest(ComputeState account) throws Throwable {
        List<ComputeStats> allStats = new ArrayList<>();
        // spin up a stateless service that acts as the parent link to patch back to
        StatelessService parentService = new StatelessService() {
            @Override
            public void handleRequest(Operation op) {
                if (op.getAction() == Action.PATCH) {
                    if (TestAWSCostAdapterService.this.isMock) {
                        SingleResourceStatsCollectionTaskState resp = op
                                .getBody(SingleResourceStatsCollectionTaskState.class);
                        allStats.addAll(resp.statsList);
                        if (resp.isFinalBatch) {
                            verifyCollectedStats(allStats);
                            TestAWSCostAdapterService.this.host.completeIteration();
                        }
                    } else {
                        TestAWSCostAdapterService.this.host.completeIteration();
                    }
                }
            }
        };
        sendStatsRequest(account, parentService);
    }

    private void sendStatsRequest(ComputeState account, StatelessService parentService)
            throws Throwable {
        String servicePath = UUID.randomUUID().toString();
        Operation startOp = Operation.createPost(UriUtils.buildUri(this.host, servicePath));
        this.host.startService(startOp, parentService);

        ComputeStatsRequest statsRequest = new ComputeStatsRequest();
        statsRequest.resourceReference = UriUtils.buildUri(this.host, account.documentSelfLink);
        statsRequest.taskReference = UriUtils.buildUri(this.host, servicePath);
        statsRequest.isMockRequest = !this.isMock;
        statsRequest.nextStage = SingleResourceStatsCollectionTaskService
                .SingleResourceTaskCollectionStage.UPDATE_STATS.name();
        this.host.sendAndWait(Operation
                .createPatch(UriUtils.buildUri(this.host, MockCostStatsAdapterService.SELF_LINK))
                .setBody(statsRequest)
                .setReferer(this.host.getUri()));
    }

    private void verifyCollectedStats(List<ComputeStats> statsList) {

        Map<String, ComputeStats> computeStatsByLink = statsList.stream()
                .collect(Collectors.toMap(e -> e.computeLink, Function.identity(), (allStats, stats) -> {
                    allStats.statValues.putAll(stats.statValues);
                    return allStats;
                }));
        ComputeStats account1Stats = computeStatsByLink.get(account1SelfLink);
        ComputeStats account2Stats = computeStatsByLink.get(account2SelfLink);
        String normalizedStatKeyValue = AWSStatsNormalizer.getNormalizedStatKeyValue(AWSConstants.COST);

        // verify account costs
        assertTrue(account1Stats.statValues.get(normalizedStatKeyValue).get(0).latestValue == account1TotalCost);
        assertTrue(account2Stats.statValues.get(normalizedStatKeyValue).get(0).latestValue == account2TotalCost);

        // check that service level stats exist
        String serviceCode = AWSCsvBillParser.AwsServices.EC2.getName().replaceAll(" ", "");
        String serviceResourceCostMetric = String.format(AWSConstants.SERVICE_RESOURCE_COST, serviceCode);
        assertTrue(!account1Stats.statValues.get(serviceResourceCostMetric).isEmpty());

        String serviceOtherCostMetric = String.format(AWSConstants.SERVICE_OTHER_COST, serviceCode);
        assertTrue(!account1Stats.statValues.get(serviceOtherCostMetric).isEmpty());

        String serviceMonthlyOtherCostMetric = String.format(AWSConstants.SERVICE_MONTHLY_OTHER_COST, serviceCode);
        assertTrue(!account1Stats.statValues.get(serviceMonthlyOtherCostMetric).isEmpty());

        String serviceReservedRecurringCostMetric = String
                .format(AWSConstants.SERVICE_RESERVED_RECURRING_COST, serviceCode);
        assertTrue(!account1Stats.statValues.get(serviceReservedRecurringCostMetric).isEmpty());

        ComputeStats instance1Stats = computeStatsByLink.get(INSTANCE_1_SELF_LINK);
        ComputeStats instance2Stats = computeStatsByLink.get(INSTANCE_2_SELF_LINK);
        assertEquals(instance1TotalCost,
                instance1Stats.statValues.get(normalizedStatKeyValue).get(0).latestValue, 0);
        assertEquals(instance2TotalCost,
                instance2Stats.statValues.get(normalizedStatKeyValue).get(0).latestValue, 0);
        String normalizedReservedInstanceStatKeyValue = AWSStatsNormalizer
                .getNormalizedStatKeyValue(AWSConstants.RESERVED_INSTANCE_DURATION);
        assertEquals(1.0, instance1Stats.statValues.get(normalizedReservedInstanceStatKeyValue)
                .get(0).latestValue, 0);
        assertEquals(1.0, instance1Stats.statValues.get(normalizedReservedInstanceStatKeyValue)
                .get(0).latestValue, 0);

        // Check that stat values are accompanied with Units.
        for (String key : account1Stats.statValues.keySet()) {
            List<ServiceStat> stats = account1Stats.statValues.get(key);
            for (ServiceStat stat : stats) {
                assertTrue("Unit is empty", !stat.unit.isEmpty());
            }
        }
    }
}
