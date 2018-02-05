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

package com.vmware.photon.controller.model.adapters.azure.ea.utils;

import static com.vmware.photon.controller.model.adapters.azure.constants
        .AzureCostConstants.NO_OF_DAYS_MARGIN_FOR_AZURE_TO_UPDATE_BILL_IN_MILLIS;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

import com.opencsv.CSVReader;
import com.opencsv.bean.HeaderColumnNameMappingStrategy;

import com.vmware.photon.controller.model.adapters.azure.constants.AzureCostConstants;
import com.vmware.photon.controller.model.adapters.azure.model.cost.AzureService;
import com.vmware.photon.controller.model.adapters.azure.model.cost.AzureSubscription;
import com.vmware.photon.controller.model.adapters.azure.model.cost.BillParsingStatus;
import com.vmware.photon.controller.model.adapters.azure.model.cost.EaDetailedBillElement;

public class AzureDetailedBillHandler {

    private static final Logger logger = Logger
            .getLogger(AzureDetailedBillHandler.class.getSimpleName());

    public enum BillHeaders {

        ACCOUNT_OWNER_ID(0),
        ACCOUNT_NAME(1),
        SERVICE_ADMINISTRATOR_ID(2),
        SUBSCRIPTION_ID(3),
        SUBSCRIPTION_GUID(4),
        SUBSCRIPTION_NAME(5),
        DATE(6),
        MONTH(7),
        DAY(8),
        YEAR(9),
        PRODUCT(10),
        METER_ID(11),
        METER_CATEGORY(12),
        METER_SUB_CATEGORY(13),
        METER_REGION(14),
        METER_NAME(15),
        CONSUMED_QUANTITY(16),
        RESOURCE_RATE(17),
        EXTENDED_COST(18),
        RESOURCE_LOCATION(19),
        CONSUMED_SERVICE(20),
        INSTANCE_ID(21),
        SERVICE_INFO_1(22),
        SERVICE_INFO_2(23),
        ADDITIONAL_INFO(24),
        TAGS(25),
        STORE_SERVICE_IDENTIFIER(26),
        DEPARTMENT_NAME(27),
        COST_CENTER(28),
        UNIT_OF_MEASURE(29),
        RESOURCE_GROUP(30);

        public int position;

        BillHeaders(int position) {
            this.position = position;
        }

    }

    public BillParsingStatus parseDetailedCsv(File billFile, Set<String> newSubscriptions, BillParsingStatus status,
            long billProcessedTimeMillis, String currency,
            BiConsumer<Map<String, AzureSubscription>, Long> dailyStatsConsumer)
            throws IOException {
        logger.fine(() -> "Beginning to parse CSV file.");
        try (CSVReader csvReader = new CSVReader(new FileReader(billFile),
                AzureCostConstants.DEFAULT_COLUMN_SEPARATOR, AzureCostConstants.DEFAULT_QUOTE_CHARACTER,
                AzureCostConstants.DEFAULT_ESCAPE_CHARACTER, (int) status.getNoLinesRead())) {

            HeaderColumnNameMappingStrategy<EaDetailedBillElement> strategy =
                    new HeaderColumnNameMappingStrategy<>();
            strategy.setType(EaDetailedBillElement.class);
            long timeToStartBillProcessing = getTimeToStartBillProcessing(billProcessedTimeMillis);

            // This map will contain daily subscription, service & resource cost. The subscription
            // GUID is the key and the subscription details is the value. This map is maintained
            // since daily-level stats are needed for services and resources.
            Map<String, AzureSubscription> monthlyBill = new HashMap<>();
            String[] nextRow;
            Long prevRowEpoch = null;
            while ((nextRow = csvReader.readNext()) != null) {
                final String[] finalNextRow = nextRow;
                if (nextRow.length != BillHeaders.values().length) {
                    // Skip any blank or malformed rows
                    logger.warning(() ->
                            String.format("Skipping malformed row: %s", Arrays.toString(finalNextRow)));
                    continue;
                }
                logger.fine(() -> String.format("Beginning to process row: %s", Arrays.toString(finalNextRow)));

                EaDetailedBillElement detailedBillElement = AzureCostHelper
                        .sanitizeDetailedBillElement(nextRow, currency);
                AzureSubscription subscription = populateSubscriptionCost(monthlyBill,
                        detailedBillElement);
                long curRowEpoch = detailedBillElement.epochDate;
                if (shouldCreateServiceAndResourceCost(detailedBillElement, newSubscriptions,
                        timeToStartBillProcessing)) {
                    AzureService service = populateServiceCost(subscription,
                            detailedBillElement);
                    populateResourceCost(service, detailedBillElement);
                }
                billProcessedTimeMillis =
                        billProcessedTimeMillis < curRowEpoch ?
                                curRowEpoch : billProcessedTimeMillis;
                monthlyBill.put(detailedBillElement.subscriptionGuid, subscription);
                if (prevRowEpoch != null && !prevRowEpoch.equals(curRowEpoch)) {
                    // This indicates that we have processed all rows belonging to a
                    // corresponding day in the current month's bill.
                    // Consume the batch
                    // Subtract 1, to account for detecting date change line.
                    status.setNoLinesRead(csvReader.getLinesRead() - 1);
                    dailyStatsConsumer.accept(monthlyBill, null);
                    break;
                }
                prevRowEpoch = curRowEpoch;
            }
            if ((nextRow == null && monthlyBill.size() > 0) || (nextRow == null
                    && csvReader.getLinesRead() == AzureCostConstants.DEFAULT_LINES_TO_SKIP)) {
                status.setParsingComplete(true);
                dailyStatsConsumer.accept(monthlyBill, billProcessedTimeMillis);
                logger.fine(() -> "Finished parsing CSV bill.");
            }
            return status;
        }
    }

    /**
     * Create service and resource cost only if the time of the bill row is greater than
     * the time when we last processed this bill or if there are any new subscriptions
     * which were explicitly added.
     * @param bRow the row of the bill
     * @param newSubscriptions list of subscriptions added explicitly after the bill was processed last.
     * @param timeToStartBillProcessing the time after which the bill should be processed
     * @return whether to process the row of the bill
     */
    private boolean shouldCreateServiceAndResourceCost(EaDetailedBillElement bRow,
            Set<String> newSubscriptions, Long timeToStartBillProcessing) {
        return AzureCostHelper.getMillisForDateString(bRow.date)
                > timeToStartBillProcessing || newSubscriptions.contains(bRow.subscriptionGuid);
    }

    private AzureSubscription populateSubscriptionCost(Map<String, AzureSubscription> monthlyBill,
            EaDetailedBillElement bRow) {
        AzureSubscription subscription = monthlyBill.get(bRow.subscriptionGuid);
        if (subscription == null) {
            subscription = createSubscriptionDto(bRow);
        }
        return subscription.addToDailyCosts(bRow.epochDate, bRow.extendedCost);
    }

    private AzureService populateServiceCost(AzureSubscription subscription,
            EaDetailedBillElement bRow) {
        if (subscription == null) {
            subscription = createSubscriptionDto(bRow);
        }
        return subscription.addToServicesMap(bRow);
    }

    private void populateResourceCost(AzureService service, EaDetailedBillElement bRow) {
        service.addToResourcesMap(bRow);
    }

    private AzureSubscription createSubscriptionDto(EaDetailedBillElement bRow) {
        return new AzureSubscription(bRow.subscriptionGuid, bRow.subscriptionName,
                bRow.accountOwnerId, bRow.accountName);
    }

    /**
     * Azure keeps updating their bills for a few days in the past. This date is currently
     * known to be AzureCostConstants.NO_OF_DAYS_MARGIN_FOR_AZURE_TO_UPDATE_BILL. Any new
     * row that is added in the past AzureCostConstants.NO_OF_DAYS_MARGIN_FOR_AZURE_TO_UPDATE_BILL
     * needs to be parsed and processed.
     * @param billProcessedTime the time of the last row of the bill when it was parsed in
     *                          the last run.
     * @return the time since bill parsing should be started.
     */
    private static long getTimeToStartBillProcessing(long billProcessedTime) {
        return billProcessedTime - NO_OF_DAYS_MARGIN_FOR_AZURE_TO_UPDATE_BILL_IN_MILLIS;
    }

}
