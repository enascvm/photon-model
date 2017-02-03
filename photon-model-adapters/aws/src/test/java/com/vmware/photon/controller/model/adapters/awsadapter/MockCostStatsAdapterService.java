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

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSCsvBillParser;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.Utils;

public class MockCostStatsAdapterService extends AWSCostStatsService {

    public static final String SELF_LINK = AWSUriPaths.PROVISIONING_AWS
            + "/mock-costs-stats-adapter";

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
    protected void getAWSAsyncCostingClient(AWSCostStatsCreationContext statsData,
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

    protected void scheduleDownload(AWSCostStatsCreationContext statsData,
            AWSCostStatsCreationStages next) {

        validatePastMonthsBillsAreScheduledForDownload(statsData);

        AWSCsvBillParser parser = new AWSCsvBillParser();
        LocalDate monthDate = com.vmware.photon.controller.model.adapters.awsadapter.TestUtils.getDateTimeToday().toLocalDate();
        Path csvBillZipFilePath;
        try {
            csvBillZipFilePath = TestUtils.getTestResourcePath(TestAWSCostAdapterService.class,
                    TestAWSSetupUtils.getCurrentMonthsSampleBillFilePath().toString());

            statsData.accountsHistoricalDetailsMap.put(monthDate, parser
                    .parseDetailedCsvBill(statsData.ignorableInvoiceCharge, csvBillZipFilePath
                    ));
        } catch (Throwable e) {
            AdapterUtils.sendFailurePatchToProvisioningTask(this,
                    statsData.statsRequest.taskReference, new RuntimeException(e));
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

    protected void queryInstances(AWSCostStatsCreationContext statsData,
            AWSCostStatsCreationStages next) {
        //inserting all mock data required
        ComputeState instance1 = new ComputeState();
        instance1.documentSelfLink = "instanceSelfLink1";
        statsData.awsInstancesById.put("i-2320dc97", Collections.singletonList(instance1));

        ComputeState instance2 = new ComputeState();
        instance2.documentSelfLink = "instanceSelfLink2";
        statsData.awsInstancesById.put("i-69d52add", Collections.singletonList(instance2));

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
            AWSCostStatsCreationContext statsData, ComputeState accountComputeState) {
        ServiceStats.ServiceStat billProcessedTimeStat = new ServiceStats.ServiceStat();
        billProcessedTimeStat.name = AWSConstants.AWS_ACCOUNT_BILL_PROCESSED_TIME_MILLIS;
        billProcessedTimeStat.unit = PhotonModelConstants.UNIT_MILLISECONDS;

        assertTrue("Last bill processed time is not correct. " +
                        "Expected: " + billProcessedTimeMillis.toString() + " Got: "
                        + statsData.accountsHistoricalDetailsMap.values().iterator().next().values()
                        .iterator().next().billProcessedTimeMillis,
                statsData.accountsHistoricalDetailsMap.values().iterator().next().values()
                        .iterator().next().billProcessedTimeMillis == billProcessedTimeMillis);

        return billProcessedTimeStat;
    }
}
