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

import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;

import org.joda.time.LocalDate;

import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSCsvBillParser;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.xenon.common.Utils;

public class MockCostStatsAdapterService extends AWSCostStatsService {

    public static final String SELF_LINK = AWSUriPaths.PROVISIONING_AWS
            + "/mock-costs-stats-adapter";

    public static final Long billProcessedTimeMillis = 1475276400000L;

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
        AWSCsvBillParser parser = new AWSCsvBillParser();
        //sample bill used is for September month of 2016.
        LocalDate monthDate = new LocalDate(2016, 9, 1);
        Path csvBillZipFilePath;
        try {
            csvBillZipFilePath = TestUtils.getTestResourcePath(TestAWSCostAdapterService.class,
                    TestAWSSetupUtils.SAMPLE_AWS_BILL);

            statsData.accountDetailsMap = parser
                    .parseDetailedCsvBill(statsData.ignorableInvoiceCharge, csvBillZipFilePath,
                            monthDate);
        } catch (Throwable e) {
            AdapterUtils.sendFailurePatchToProvisioningTask(this,
                    statsData.statsRequest.taskReference, new RuntimeException(e));
        }
        statsData.stage = next;
        handleCostStatsCreationRequest(statsData);
    }

    protected void queryInstances(AWSCostStatsCreationContext statsData,
            AWSCostStatsCreationStages next) {
        //inserting all mock data required
        ComputeState instance1 = new ComputeState();
        instance1.documentSelfLink = "instanceSelfLink1";
        statsData.awsInstancesById.put("i-2320dc97", instance1);

        ComputeState instance2 = new ComputeState();
        instance2.documentSelfLink = "instanceSelfLink2";
        statsData.awsInstancesById.put("i-69d52add", instance2);

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
        account1ComputeState.creationTimeMicros = Utils.getNowMicrosUtc();
        account1ComputeState.customProperties = new HashMap<>();
        account1ComputeState.customProperties.put(AWSConstants.AWS_ACCOUNT_ID_KEY, "account1Id");
        context.awsAccountIdToComputeStates.put(TestAWSCostAdapterService.account1Id,
                Collections.singletonList(account1ComputeState));

        ComputeState account2ComputeState = new ComputeState();
        account2ComputeState.documentSelfLink = TestAWSCostAdapterService.account2SelfLink;
        account2ComputeState.creationTimeMicros = Utils.getNowMicrosUtc();
        account2ComputeState.customProperties = new HashMap<>();
        account2ComputeState.customProperties.put(AWSConstants.AWS_ACCOUNT_ID_KEY, "account2Id");
        context.awsAccountIdToComputeStates.put(TestAWSCostAdapterService.account2Id,
                Collections.singletonList(account2ComputeState));

        context.stage = next;
        handleCostStatsCreationRequest(context);
    }

    @Override
    protected void setBillProcessedTime(AWSCostStatsCreationContext statsData) {

        assertTrue("Last bill processed time is not correct. " +
                        "Expected: " + billProcessedTimeMillis.toString() +
                        " Got: " + statsData.accountDetailsMap.values().iterator()
                        .next().billProcessedTimeMillis.toString(),
                statsData.accountDetailsMap.values().iterator()
                        .next().billProcessedTimeMillis.toString()
                        .equals(billProcessedTimeMillis.toString()));
    }
}