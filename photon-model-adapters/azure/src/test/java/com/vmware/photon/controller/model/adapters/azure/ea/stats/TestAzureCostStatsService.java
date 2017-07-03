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

package com.vmware.photon.controller.model.adapters.azure.ea.stats;

import static com.vmware.photon.controller.model.util.AssertUtil.assertNotNull;
import static com.vmware.xenon.services.common.QueryTask.NumericRange.createDoubleRange;
import static com.vmware.xenon.services.common.QueryTask.QuerySpecification.buildCompositeFieldName;

import java.util.EnumSet;
import java.util.HashMap;

import org.junit.Ignore;
import org.junit.Test;

import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureCostConstants;
import com.vmware.photon.controller.model.adapters.azure.ea.AzureEaAdapters;
import com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.photon.controller.model.tasks.monitoring.StatsCollectionTaskService;

import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

public class TestAzureCostStatsService extends BaseModelTest {

    public boolean isMock = true;

    private static final String AZURE_CLIENT_ID = "azure.clientId";
    private static final String AZURE_CLIENT_SECRET = "azure.clientSecret";
    private static final String AZURE_SUBSCRIPTION_ID = "azure.subscriptionId";
    private static final String AZURE_TENANT_ID = "azure.tenantId";

    private String clientId = System.getProperty(AZURE_CLIENT_ID);
    private String clientSecret = System.getProperty(AZURE_CLIENT_SECRET);

    private static final int NO_OF_MONTHS_COST_REQUIRED = 12;

    public String enrollmentNumber;
    public String usageApiKey;

    @Override
    protected void startRequiredServices() throws Throwable {
        CommandLineArgumentParser.parseFromProperties(this);
        super.startRequiredServices();
        PhotonModelTaskServices.startServices(this.host);
        PhotonModelAdaptersRegistryAdapters.startServices(this.host);
        this.host.startService(new AzureCostStatsService());
        AzureEaAdapters.startServices(this.host);
        this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
        this.host.waitForServiceAvailable(PhotonModelAdaptersRegistryAdapters.LINKS);
        this.host.waitForServiceAvailable(AzureEaAdapters.LINKS);
        this.host.setTimeoutSeconds(900);
    }

    @Test
    @Ignore("VSYM-7696")
    public void testAzureCostStatsServiceEndToEnd() throws Throwable {
        if (this.isMock) {
            return;
        }
        ResourcePoolService.ResourcePoolState resourcePool = AzureTestUtil
                .createDefaultResourcePool(this.host);

        EndpointService.EndpointState endpointState = new EndpointService.EndpointState();
        endpointState.resourcePoolLink = resourcePool.documentSelfLink;
        endpointState.endpointType = PhotonModelConstants.EndpointType.azure_ea.name();
        endpointState.name = "test-azure-endpoint";
        endpointState.endpointProperties = new HashMap<>();
        endpointState.endpointProperties
                .put(EndpointConfigRequest.PRIVATE_KEYID_KEY, this.enrollmentNumber);
        endpointState.endpointProperties
                .put(EndpointConfigRequest.PRIVATE_KEY_KEY, this.usageApiKey);

        assertNotNull(this.enrollmentNumber,
                "Provide \"xenon.enrollmentNumber\" and \"xenon.usageApiKey\" as system properties.");
        assertNotNull(this.usageApiKey,
                "Provide \"xenon.enrollmentNumber\" and \"xenon.usageApiKey\" as system properties.");

        EndpointAllocationTaskService.EndpointAllocationTaskState endpointAllocationTaskState =
                new EndpointAllocationTaskService.EndpointAllocationTaskState();
        endpointAllocationTaskState.endpointState = endpointState;
        EndpointAllocationTaskService.EndpointAllocationTaskState returnState = postServiceSynchronously(
                EndpointAllocationTaskService.FACTORY_LINK,
                endpointAllocationTaskState,
                EndpointAllocationTaskService.EndpointAllocationTaskState.class);
        EndpointAllocationTaskService.EndpointAllocationTaskState completeState = this
                .waitForServiceState(
                        EndpointAllocationTaskService.EndpointAllocationTaskState.class,
                        returnState.documentSelfLink,
                        state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                                .ordinal());

        System.setProperty(AzureCostStatsService.BILLS_BACK_IN_TIME_MONTHS_KEY,
                Integer.toString(NO_OF_MONTHS_COST_REQUIRED - 1));

        triggerStatsCollection(resourcePool);
        verifyPersistedStats(completeState, AzureCostConstants.USAGE_COST,
                NO_OF_MONTHS_COST_REQUIRED);
        verifyPersistedStats(completeState,
                PhotonModelConstants.CLOUD_ACCOUNT_COST_SYNC_MARKER_MILLIS, 1);

        //Check if second iteration of adapter succeeds.
        triggerStatsCollection(resourcePool);
        // Expected count is same since the second run is not supposed to persist any stat
        // since the second run is running almost immediately after the first and Azure
        // may not (99.9% of the time) have updated the cost during this time.
        verifyPersistedStats(completeState, AzureCostConstants.USAGE_COST,
                NO_OF_MONTHS_COST_REQUIRED);
        verifyPersistedStats(completeState,
                PhotonModelConstants.CLOUD_ACCOUNT_COST_SYNC_MARKER_MILLIS, 1);

        System.clearProperty(AzureCostStatsService.BILLS_BACK_IN_TIME_MONTHS_KEY);
    }

    private void triggerStatsCollection(ResourcePoolService.ResourcePoolState pool) {
        StatsCollectionTaskService.StatsCollectionTaskState statCollectionState
                = new StatsCollectionTaskService.StatsCollectionTaskState();

        statCollectionState.resourcePoolLink = pool.documentSelfLink;
        statCollectionState.statsAdapterReference = UriUtils.buildUri(this.host,
                AzureCostStatsService.SELF_LINK);
        statCollectionState.documentSelfLink = "azure-cost-stats-service";
        statCollectionState.options = EnumSet.of(TaskOption.SELF_DELETE_ON_COMPLETION);
        statCollectionState.taskInfo = TaskState.createDirect();
        Operation op = Operation.createPost(this.host, StatsCollectionTaskService.FACTORY_LINK)
                .setBody(statCollectionState).setReferer(this.host.getReferer());
        this.host.sendAndWaitExpectSuccess(op);
    }

    private void verifyPersistedStats(
            EndpointAllocationTaskService.EndpointAllocationTaskState completeState,
            String metric, int expectedCount) {
        this.host.waitFor("Timeout waiting for stats", () -> {
            QueryTask.QuerySpecification querySpec = new QueryTask.QuerySpecification();
            querySpec.query = QueryTask.Query.Builder.create()
                    .addKindFieldClause(ResourceMetricsService.ResourceMetrics.class)
                    .addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                            UriUtils.buildUriPath(ResourceMetricsService.FACTORY_LINK,
                                    UriUtils.getLastPathSegment(
                                            completeState.endpointState.computeLink)),
                            QueryTask.QueryTerm.MatchType.PREFIX)
                    .addRangeClause(buildCompositeFieldName(
                            ResourceMetricsService.ResourceMetrics.FIELD_NAME_ENTRIES, metric),
                            createDoubleRange(Double.MIN_VALUE, Double.MAX_VALUE, true, true))
                    .build();
            querySpec.options.add(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);
            ServiceDocumentQueryResult result = this.host
                    .createAndWaitSimpleDirectQuery(querySpec, expectedCount, expectedCount);
            boolean statsCollected = true;
            for (Object metrics : result.documents.values()) {
                ResourceMetricsService.ResourceMetrics rawMetrics = Utils
                        .fromJson(metrics, ResourceMetricsService.ResourceMetrics.class);
                Double rawMetric = rawMetrics.entries.get(metric);
                if (rawMetric != null) {
                    continue;
                }
                statsCollected = false;
            }
            return statsCollected;
        });
    }

}