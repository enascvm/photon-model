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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSCsvBillParser;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService.ResourceMetrics;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.photon.controller.model.tasks.monitoring.StatsUtil;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class MockCostStatsAdapterService extends AWSCostStatsService {

    public static final String SELF_LINK = AWSUriPaths.PROVISIONING_AWS
            + "/mock-costs-stats-adapter";

    public static final String INSTANCE_1_SELF_LINK = "instanceSelfLink1";
    public static final String INSTANCE_2_SELF_LINK = "instanceSelfLink2";

    @Override
    protected void getAccountDescription(AWSCostStatsCreationContext statsData,
            AWSCostStatsCreationStages next) {

        ComputeDescription compDesc = new ComputeDescription();
        compDesc.id = "123";
        compDesc.documentSelfLink = UriUtils.buildUriPath(ComputeDescriptionService.FACTORY_LINK,
                generateUuidFromStr(compDesc.id));
        Set<URI> statsAdapterReferences = new HashSet<>();
        statsAdapterReferences.add(UriUtils.buildUri("stats-adapter-references"));
        compDesc.statsAdapterReferences = statsAdapterReferences;
        statsData.accountId = TestAWSCostAdapterService.account1Id;
        ComputeStateWithDescription account1ComputeState = new ComputeStateWithDescription();
        account1ComputeState.documentSelfLink = TestAWSCostAdapterService.account1SelfLink;
        account1ComputeState.type = ComputeType.VM_HOST;
        account1ComputeState.endpointLink = "endpoint1";
        account1ComputeState.endpointLinks = new HashSet<String>();
        account1ComputeState.endpointLinks.add("endpoint1");
        account1ComputeState.name = "account1";
        account1ComputeState.descriptionLink = UriUtils
                .buildUriPath(ComputeDescriptionService.FACTORY_LINK, generateUuidFromStr("123"));
        account1ComputeState.creationTimeMicros = Utils.getNowMicrosUtc();
        account1ComputeState.customProperties = new HashMap<>();
        account1ComputeState.customProperties
                .put(AWSConstants.AWS_ACCOUNT_ID_KEY, "account1Id");
        account1ComputeState.customProperties
                .put(EndpointAllocationTaskService.CUSTOM_PROP_ENPOINT_TYPE, EndpointType.aws.name());
        account1ComputeState.description = new ComputeDescription();
        account1ComputeState.description.statsAdapterReferences = statsAdapterReferences;
        account1ComputeState.tenantLinks = Collections.singletonList("tenant-1");
        statsData.computeDesc = account1ComputeState;
        statsData.awsAccountIdToComputeStates.put(TestAWSCostAdapterService.account1Id,
                Collections.singletonList(account1ComputeState));
        inferEndpointLink(statsData);
        getParentAuth(statsData, next);
    }

    @Override
    protected void getParentAuth(AWSCostStatsCreationContext statsData,
            AWSCostStatsCreationStages next) {
        statsData.stage = next;
        handleCostStatsCreationRequest(statsData);
    }

    @Override
    protected void checkBillBucketConfig(AWSCostStatsCreationContext statsData,
            AWSCostStatsCreationStages next) {
        statsData.stage = next;
        handleCostStatsCreationRequest(statsData);
    }

    @Override
    protected void queryMarkers(AWSCostStatsCreationContext context, AWSCostStatsCreationStages next) {
        context.awsAccountIdToComputeStates.forEach((accId, states) -> {
            if (accId.equals(TestAWSCostAdapterService.account2Id)) {
                states.get(0).documentSelfLink = TestAWSCostAdapterService.account2SelfLink;
            }
        });
        context.accountsMarkersMap.put(TestAWSCostAdapterService.account1Id,
                getMockMarkerMetrics(TestAWSCostAdapterService.account1SelfLink));
        context.accountsMarkersMap.put(TestAWSCostAdapterService.account2Id,
                getMockMarkerMetrics(TestAWSCostAdapterService.account2SelfLink));
        context.stage = next;
        context.subStage = AWSCostStatsCreationSubStage.QUERY_INSTANCES;
        handleCostStatsCreationRequest(context);
    }

    @Override
    protected void startReservedInstancesPlansCollection(AWSCostStatsCreationContext context,
            AWSCostStatsCreationStages next) {
        context.stage = next;
        handleCostStatsCreationRequest(context);
    }

    private ResourceMetrics getMockMarkerMetrics(String selfLink) {
        ResourceMetrics markerMetrics = new ResourceMetrics();
        markerMetrics.timestampMicrosUtc = getCurrentMonthStartTimeMicros();
        markerMetrics.entries = new HashMap<>();
        markerMetrics.documentSelfLink = StatsUtil.getMetricKey(selfLink, Utils.getNowMicrosUtc());
        return markerMetrics;
    }

    @Override
    protected void scheduleDownload(AWSCostStatsCreationContext statsData,
            AWSCostStatsCreationStages next) {

        validatePastMonthsBillsAreScheduledForDownload(statsData);
        AWSCsvBillParser parser = new AWSCsvBillParser();
        Path csvBillZipFilePath;
        try {
            csvBillZipFilePath = TestUtils.getTestResourcePath(TestAWSCostAdapterService.class,
                    TestAWSSetupUtils.getCurrentMonthsSampleBillFilePath().toString());
            LocalDate billMonth = LocalDate.now(DateTimeZone.UTC).withDayOfMonth(1);
            parser.parseDetailedCsvBill(statsData.ignorableInvoiceCharge, csvBillZipFilePath,
                    statsData.awsAccountIdToComputeStates.keySet(), getHourlyStatsConsumer(billMonth, statsData),
                    getMonthlyStatsConsumer(billMonth, statsData));
        } catch (Throwable e) {
            statsData.taskManager.patchTaskToFailure(e);
            return;
        }
        statsData.stage = next;
        handleCostStatsCreationRequest(statsData);
    }

    private void validatePastMonthsBillsAreScheduledForDownload(AWSCostStatsCreationContext statsData) {
        AWSCostStatsService costStatsService = new AWSCostStatsService();
        costStatsService.populateBillMonthToProcess(statsData);

        int numberOfMonthsToDownloadBill = Integer.getInteger(AWSCostStatsService.BILLS_BACK_IN_TIME_MONTHS_KEY,
                AWSConstants.DEFAULT_NO_OF_MONTHS_TO_GET_PAST_BILLS);

        assertEquals("Bill collection starting month is incorrect. Expected: %s Got: %s",
                LocalDate.now(DateTimeZone.UTC).getMonthOfYear(), statsData.billMonthToDownload
                        .plusMonths(numberOfMonthsToDownloadBill).getMonthOfYear());
    }

    @Override
    protected void queryInstances(AWSCostStatsCreationContext statsData,
            AWSCostStatsCreationStages nextStage, AWSCostStatsCreationSubStage nextSubStage) {
        //inserting all mock data required
        ComputeState instance1 = new ComputeState();
        instance1.documentSelfLink = INSTANCE_1_SELF_LINK;
        statsData.awsResourceLinksById.put("i-2320dc97", Collections.singleton(INSTANCE_1_SELF_LINK));
        ComputeState instance2 = new ComputeState();
        instance2.documentSelfLink = INSTANCE_2_SELF_LINK;
        statsData.awsResourceLinksById.put("i-69d52add", Collections.singleton(INSTANCE_2_SELF_LINK));
        statsData.stage = nextStage;
        statsData.subStage = nextSubStage;
        handleCostStatsCreationRequest(statsData);
    }

    @Override
    protected void queryVolumes(AWSCostStatsCreationContext statsData, AWSCostStatsCreationStages nextStage,
            AWSCostStatsCreationSubStage nextSubStage) {
        statsData.stage = nextStage;
        statsData.subStage = nextSubStage;
        handleCostStatsCreationRequest(statsData);
    }

    private String generateUuidFromStr(String linkedAccountId) {
        return UUID.nameUUIDFromBytes(linkedAccountId.getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
    }
}
