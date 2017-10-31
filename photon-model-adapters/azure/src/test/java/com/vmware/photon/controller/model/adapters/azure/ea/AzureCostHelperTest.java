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

package com.vmware.photon.controller.model.adapters.azure.ea;

import static org.junit.Assert.assertEquals;

import static com.vmware.photon.controller.model.adapters.azure.ea.utils.AzureCostHelper.getMillisForDate;
import static com.vmware.photon.controller.model.adapters.azure.ea.utils.AzureCostHelper.getSummarizedBillsToDownload;
import static com.vmware.photon.controller.model.adapters.azure.ea.utils.AzureCostHelper.shouldDownloadPastBills;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.Test;

import com.vmware.photon.controller.model.adapters.azure.constants.AzureCostConstants;
import com.vmware.xenon.common.Utils;

public class AzureCostHelperTest {

    @Test
    public void testShouldDownloadPastBills() {
        long nowMillis = TimeUnit.MICROSECONDS.toMillis(Utils.getNowMicrosUtc());
        // Never processed bills for an account
        assertEquals("Should have been downloading past bills.", shouldDownloadPastBills(0, 0,
                AzureCostConstants.NO_OF_MONTHS_TO_GET_PAST_BILLS),
                Boolean.TRUE);
        // Only current month bill processed (and OBPT == BPT; this will happen after the oldest
        // bill processed flag was introduced
        assertEquals("Should have been downloading past bills.",
                shouldDownloadPastBills(nowMillis, nowMillis,
                        AzureCostConstants.NO_OF_MONTHS_TO_GET_PAST_BILLS), Boolean.TRUE);
        // Only downloaded current month bill, never downloaded past months bills (OBPT will be 0
        // in this case since current month bill was downloaded before the OBPT change was
        // introduced.
        assertEquals("Should have been downloading past bills",
                shouldDownloadPastBills(nowMillis, 0,
                        AzureCostConstants.NO_OF_MONTHS_TO_GET_PAST_BILLS), Boolean.TRUE);
        // Downloaded past bills, but today is first day of the month
        LocalDateTime now = LocalDateTime.now();
        long firstDayCurrentMonthMillis = now.withDayOfMonth(1)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        assertEquals("Should have been downloading past bills",
                shouldDownloadPastBills(firstDayCurrentMonthMillis, nowMillis,
                        AzureCostConstants.NO_OF_MONTHS_TO_GET_PAST_BILLS), Boolean.TRUE);
        assertEquals("Should NOT be downloading past bills anymore.",
                shouldDownloadPastBills(firstDayCurrentMonthMillis,
                        now.minusMonths(AzureCostConstants.NO_OF_MONTHS_TO_GET_PAST_BILLS)
                                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        AzureCostConstants.NO_OF_MONTHS_TO_GET_PAST_BILLS),
                Boolean.FALSE);

    }

    @Test
    public void testGetSummarizedBillsToDownload() {
        long billProcessedTimeMillis = 0;
        Set<LocalDate> summarizedBillsToDownload = getSummarizedBillsToDownload(
                billProcessedTimeMillis);
        assertEquals("Number of summarized bills to download is incorrect.",
                AzureCostConstants.NO_OF_MONTHS_TO_GET_PAST_BILLS + 1,
                summarizedBillsToDownload.size());

        billProcessedTimeMillis = TimeUnit.MICROSECONDS.toMillis(Utils.getNowMicrosUtc());
        summarizedBillsToDownload = getSummarizedBillsToDownload(billProcessedTimeMillis);
        assertEquals("Number of summarized bills to download is incorrect.", 1,
                summarizedBillsToDownload.size());

        LocalDate lastMonth = LocalDate.now(DateTimeZone.UTC).minusMonths(1);
        long lastMonthMillis = getMillisForDate(lastMonth);
        summarizedBillsToDownload = getSummarizedBillsToDownload(lastMonthMillis);
        assertEquals("Number of summarized bills to download is incorrect.", 2,
                summarizedBillsToDownload.size());

        LocalDate firstDayOfCurrentMonth = LocalDate.now(DateTimeZone.UTC).withDayOfMonth(1);
        long firstDayOfCurrentMonthMillis = getMillisForDate(firstDayOfCurrentMonth);
        summarizedBillsToDownload = getSummarizedBillsToDownload(firstDayOfCurrentMonthMillis);
        assertEquals("Number of summarized bills to download is incorrect.", 2,
                summarizedBillsToDownload.size());

        LocalDate firstDayOfPrevMonth = LocalDate.now(DateTimeZone.UTC).withDayOfMonth(1)
                .minusMonths(1);
        long firstDayOfPrevMonthMillis = getMillisForDate(firstDayOfPrevMonth);
        summarizedBillsToDownload = getSummarizedBillsToDownload(firstDayOfPrevMonthMillis);
        assertEquals("Number of summarized bills to download is incorrect.", 3,
                summarizedBillsToDownload.size());

    }

}