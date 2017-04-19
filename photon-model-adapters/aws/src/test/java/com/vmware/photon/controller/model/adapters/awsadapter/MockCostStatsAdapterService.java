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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSCsvBillParser;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.tasks.TestUtils;
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
        account1ComputeState.endpointLink = "endpoint1";
        account1ComputeState.descriptionLink = UriUtils
                .buildUriPath(ComputeDescriptionService.FACTORY_LINK,
                        generateUuidFromStr("123"));
        account1ComputeState.creationTimeMicros = Utils.getNowMicrosUtc();
        account1ComputeState.customProperties = new HashMap<>();
        account1ComputeState.customProperties
                .put(AWSConstants.AWS_ACCOUNT_ID_KEY, "account1Id");
        account1ComputeState.description = new ComputeDescription();
        account1ComputeState.description.statsAdapterReferences = statsAdapterReferences;
        statsData.computeDesc = account1ComputeState;
        statsData.awsAccountIdToComputeStates.put(TestAWSCostAdapterService.account1Id,
                Collections.singletonList(account1ComputeState));
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
    protected void queryBillProcessedTime(AWSCostStatsCreationContext context,
            AWSCostStatsCreationStages next) {
        context.stage = next;
        handleCostStatsCreationRequest(context);
    }

    @Override
    protected void startReservedInstancesPlansCollection(AWSCostStatsCreationContext context,
            AWSCostStatsCreationStages next) {
        context.stage = next;
        handleCostStatsCreationRequest(context);
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
            Map<String, Long> accountMarkers = new HashMap<>();
            accountMarkers.put(TestAWSCostAdapterService.account1Id, 0L);
            accountMarkers.put(TestAWSCostAdapterService.account2Id, 0L);
            LocalDate billMonth = LocalDate.now(DateTimeZone.UTC).withDayOfMonth(1);
            parser.parseDetailedCsvBill(statsData.ignorableInvoiceCharge, csvBillZipFilePath,
                    accountMarkers, getHourlyStatsConsumer(billMonth, statsData),
                    getMonthlyStatsConsumer(billMonth, statsData));
        } catch (Throwable e) {
            statsData.taskManager.patchTaskToFailure(e);
            return;
        }
        statsData.stage = next;
        handleCostStatsCreationRequest(statsData);
    }

    private void validatePastMonthsBillsAreScheduledForDownload(
            AWSCostStatsCreationContext statsData) {

        // Backup the accountIdToBillProcessedTimeMap and restore after test
        Map<String, Long> accountIdToBillProcessedTimeBackup = statsData
                .accountIdToBillProcessedTime;

        // Set billProcessedTime to 0 for this test case
        for (Map.Entry<String, Long> entries : statsData.accountIdToBillProcessedTime.entrySet()) {
            entries.setValue(0L);
        }
        AWSCostStatsService costStatsService = new AWSCostStatsService();
        costStatsService.populateBillMonthToProcess(statsData);

        String property = System.getProperty(AWSCostStatsService.BILLS_BACK_IN_TIME_MONTHS_KEY);
        int numberOfMonthsToDownloadBill = AWSConstants.DEFAULT_NO_OF_MONTHS_TO_GET_PAST_BILLS;

        if (property != null) {
            numberOfMonthsToDownloadBill = new Integer(property);
        }

        assertEquals("Bill collection starting month is incorrect. Expected: %s Got: %s",
                LocalDate.now(DateTimeZone.UTC).getMonthOfYear(), statsData.billMonthToDownload
                        .plusMonths(numberOfMonthsToDownloadBill).getMonthOfYear());
        statsData.accountIdToBillProcessedTime = accountIdToBillProcessedTimeBackup;
    }

    @Override
    protected void queryInstances(AWSCostStatsCreationContext statsData,
            AWSCostStatsCreationStages next) {
        //inserting all mock data required
        ComputeState instance1 = new ComputeState();
        instance1.documentSelfLink = INSTANCE_1_SELF_LINK;
        statsData.awsInstanceLinksById
                .put("i-2320dc97", Collections.singletonList(INSTANCE_1_SELF_LINK));
        ComputeState instance2 = new ComputeState();
        instance2.documentSelfLink = INSTANCE_2_SELF_LINK;
        statsData.awsInstanceLinksById
                .put("i-69d52add", Collections.singletonList(INSTANCE_2_SELF_LINK));
        statsData.stage = next;
        handleCostStatsCreationRequest(statsData);
    }

    private String generateUuidFromStr(String linkedAccountId) {
        return UUID.nameUUIDFromBytes(linkedAccountId.getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
    }
}
