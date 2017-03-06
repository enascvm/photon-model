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

package com.vmware.photon.controller.model.adapters.azure.stats;

import static com.vmware.xenon.services.common.QueryTask.NumericRange.createDoubleRange;
import static com.vmware.xenon.services.common.QueryTask.QuerySpecification.buildCompositeFieldName;

import java.util.EnumSet;
import java.util.HashMap;

import org.junit.Test;

import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest;
import com.vmware.photon.controller.model.adapters.azure.AzureAdapters;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureCostConstants;
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

    @Override
    protected void startRequiredServices() throws Throwable {
        CommandLineArgumentParser.parseFromProperties(this);
        super.startRequiredServices();
        PhotonModelTaskServices.startServices(this.host);
        PhotonModelAdaptersRegistryAdapters.startServices(this.host);
        this.host.startService(new AzureCostStatsService());
        AzureAdapters.startServices(this.host);
        this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
        this.host.waitForServiceAvailable(PhotonModelAdaptersRegistryAdapters.LINKS);
        this.host.waitForServiceAvailable(AzureAdapters.LINKS);
        this.host.setTimeoutSeconds(900);
    }

    @Test
    public void testAzureCostStatsServiceEndToEnd() throws Throwable {
        if (this.isMock) {
            return;
        }
        ResourcePoolService.ResourcePoolState resourcePool = AzureTestUtil
                .createDefaultResourcePool(this.host);

        EndpointService.EndpointState endpointState = new EndpointService.EndpointState();
        endpointState.resourcePoolLink = resourcePool.documentSelfLink;
        endpointState.endpointType = PhotonModelConstants.EndpointType.azure.name();
        endpointState.name = "test-azure-endpoint";
        endpointState.endpointProperties = new HashMap<>();
        endpointState.endpointProperties
                .put(EndpointConfigRequest.PRIVATE_KEYID_KEY, this.clientId);
        endpointState.endpointProperties
                .put(EndpointConfigRequest.PRIVATE_KEY_KEY, this.clientSecret);
        String subscriptionId = System.getProperty(AZURE_SUBSCRIPTION_ID);
        endpointState.endpointProperties.put(EndpointConfigRequest.USER_LINK_KEY, subscriptionId);
        String tenantId = System.getProperty(AZURE_TENANT_ID);
        endpointState.endpointProperties.put(AzureConstants.AZURE_TENANT_ID, tenantId);

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

        System.setProperty(AzureCostStatsService.BILLS_BACK_IN_TIME_MONTHS_KEY, "1");

        triggerStatsCollection(resourcePool);
        verifyPersistedStats(completeState, AzureCostConstants.COST);

        //Check if second iteration of adapter succeeds.
        triggerStatsCollection(resourcePool);
        verifyPersistedStats(completeState,
                PhotonModelConstants.CLOUD_ACCOUNT_COST_SYNC_MARKER_MILLIS);

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
            String metric) {
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
                    .createAndWaitSimpleDirectQuery(querySpec, 2, 2);
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