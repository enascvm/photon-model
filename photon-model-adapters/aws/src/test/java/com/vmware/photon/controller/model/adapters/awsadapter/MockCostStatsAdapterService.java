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

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

import com.vmware.photon.controller.model.adapters.aws.dto.AwsAccountDetailDto;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSCsvBillParser;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.Utils;

public class MockCostStatsAdapterService extends AWSCostStatsService {

    public static final String SELF_LINK = AWSUriPaths.PROVISIONING_AWS
            + "/mock-costs-stats-adapter";

    public static final String INSTANCE_1_SELF_LINK = "instanceSelfLink1";
    public static final String INSTANCE_2_SELF_LINK = "instanceSelfLink2";

    public static final Long billProcessedTimeMillis =
            com.vmware.photon.controller.model.adapters.awsadapter.TestUtils.getDateTimeToday()
                    .withDayOfMonth(1).toDateTime(DateTimeZone.UTC).withTimeAtStartOfDay().getMillis();

    @Override
    protected void getAccountDescription(AWSCostStatsCreationContext statsData,
            AWSCostStatsCreationStages next) {
        getParentAuth(statsData, next);
    }

    @Override
    protected void getParentAuth(AWSCostStatsCreationContext statsData,
            AWSCostStatsCreationStages next) {
        statsData.stage = next;
        handleCostStatsCreationRequest(statsData);
    }

    @Override
    protected void getAWSAsyncCostingClient(AWSCostStatsCreationContext statsData) {
        return;
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
        LocalDate monthDate = com.vmware.photon.controller.model.adapters.awsadapter.TestUtils.getDateTimeToday().toLocalDate();
        Path csvBillZipFilePath;
        try {
            csvBillZipFilePath = TestUtils.getTestResourcePath(TestAWSCostAdapterService.class,
                    TestAWSSetupUtils.getCurrentMonthsSampleBillFilePath().toString());
            Map<String, Long> accountMarkers = new HashMap<>();
            accountMarkers.put(TestAWSCostAdapterService.account1Id, 0L);
            accountMarkers.put(TestAWSCostAdapterService.account2Id, 0L);
            Map<String, AwsAccountDetailDto> accountDetails = parser
                    .parseDetailedCsvBill(statsData.ignorableInvoiceCharge, csvBillZipFilePath,
                            accountMarkers);
            statsData.accountsHistoricalDetailsMap.put(monthDate, accountDetails);
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
        Map<String, Long> accountIdToBillProcessedTimeBackup = statsData.accountIdToBillProcessedTime;

        // Set billProcessedTime to 0 for this test case
        for (Map.Entry<String, Long> entries : statsData.accountIdToBillProcessedTime.entrySet()) {
            entries.setValue(0L);
        }
        AWSCostStatsService costStatsService = new AWSCostStatsService();
        costStatsService.populateBillMonthToProcess(statsData, "");

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

        statsData.computeDesc = new ComputeStateWithDescription();
        statsData.computeDesc.documentSelfLink = "accountSelfLink";
        statsData.computeDesc.customProperties = new HashMap<>();
        statsData.computeDesc.customProperties
                .put(AWSConstants.AWS_ACCOUNT_ID_KEY, TestAWSCostAdapterService.account1Id);

        statsData.stage = next;
        handleCostStatsCreationRequest(statsData);
    }

    @Override
    protected void queryLinkedAccounts(AWSCostStatsCreationContext context,
            AWSCostStatsCreationStages next) {

        ComputeState account1ComputeState = new ComputeState();
        account1ComputeState.documentSelfLink = TestAWSCostAdapterService.account1SelfLink;
        account1ComputeState.endpointLink = "endpoint1";
        account1ComputeState.creationTimeMicros = Utils.getNowMicrosUtc();
        account1ComputeState.customProperties = new HashMap<>();
        account1ComputeState.customProperties.put(AWSConstants.AWS_ACCOUNT_ID_KEY, "account1Id");
        context.awsAccountIdToComputeStates.put(TestAWSCostAdapterService.account1Id,
                Collections.singletonList(account1ComputeState));

        ComputeState account2ComputeState = new ComputeState();
        account2ComputeState.documentSelfLink = TestAWSCostAdapterService.account2SelfLink;
        account2ComputeState.endpointLink = "endpoint2";
        account2ComputeState.creationTimeMicros = Utils.getNowMicrosUtc();
        account2ComputeState.customProperties = new HashMap<>();
        account2ComputeState.customProperties.put(AWSConstants.AWS_ACCOUNT_ID_KEY, "account2Id");
        context.awsAccountIdToComputeStates.put(TestAWSCostAdapterService.account2Id,
                Collections.singletonList(account2ComputeState));

        context.stage = next;
        handleCostStatsCreationRequest(context);
    }

    @Override
    protected ServiceStats.ServiceStat createBillProcessedTimeStat(
            AwsAccountDetailDto awsAccountDetailDto) {
        ServiceStats.ServiceStat billProcessedTimeStat = new ServiceStats.ServiceStat();
        billProcessedTimeStat.name = AWSConstants.AWS_ACCOUNT_BILL_PROCESSED_TIME_MILLIS;
        billProcessedTimeStat.unit = PhotonModelConstants.UNIT_MILLISECONDS;
        billProcessedTimeStat.latestValue = billProcessedTimeMillis;
        billProcessedTimeStat.sourceTimeMicrosUtc = Utils.getNowMicrosUtc();
        return billProcessedTimeStat;
    }

    @Override
    protected void setLinkedAccountIds(AWSCostStatsCreationContext context) {
        return;
    }
}
