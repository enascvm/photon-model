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

import static com.vmware.photon.controller.model.adapters.azure.constants
        .AzureCostConstants.NO_OF_DAYS_MARGIN_FOR_AZURE_TO_UPDATE_BILL_IN_MILLIS;
import static com.vmware.photon.controller.model.adapters.azure.ea.AzureDetailedBillHandler.BillHeaders.DATE;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

import com.opencsv.CSVReader;
import com.opencsv.bean.HeaderColumnNameMappingStrategy;

import com.vmware.photon.controller.model.adapters.azure.model.cost.AzureService;
import com.vmware.photon.controller.model.adapters.azure.model.cost.AzureSubscription;
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

    public boolean parseDetailedCsv(CSVReader csvReader, long billProcessedTimeMillis,
            String currency, BiConsumer<Map<String, AzureSubscription>, Long> dailyStatsConsumer,
            BiConsumer<Map<String, AzureSubscription>, Long> monthlyStatsConsumer) throws IOException {
        logger.fine("Beginning to parse CSV billFileReader.");
        HeaderColumnNameMappingStrategy<EaDetailedBillElement> strategy =
                new HeaderColumnNameMappingStrategy<>();
        strategy.setType(EaDetailedBillElement.class);
        boolean parsingComplete = false;

        // This map will contain daily subscription, service & resource cost. The subscription
        // GUID is the key and the subscription details is the value. This map is maintained
        // since daily-level stats are needed for services and resources.
        Map<String, AzureSubscription> monthlyBill = new HashMap<>();
        String[] nextRow;
        Long prevRowEpoch = null;
        nextRow = getFirstLineToParse(csvReader, billProcessedTimeMillis);
        if (nextRow == null) {
            // There are no rows in the bill that should be parsed.
            monthlyStatsConsumer.accept(monthlyBill, billProcessedTimeMillis);
            return true;
        }
        do {
            if (nextRow.length != BillHeaders.values().length) {
                // Skip any blank or malformed rows
                continue;
            }
            logger.fine(String.format("Processing row: %s", Arrays.toString(nextRow)));
            EaDetailedBillElement detailedBillElement = AzureCostStatsServiceHelper
                    .sanitizeDetailedBillElement(nextRow,
                            currency);
            long curRowEpoch = detailedBillElement.epochDate;
            billProcessedTimeMillis =
                    billProcessedTimeMillis < curRowEpoch ?
                            curRowEpoch :
                            billProcessedTimeMillis;
            segregateSubscriptionServiceResourceCost(detailedBillElement, monthlyBill);
            if (prevRowEpoch != null && !prevRowEpoch.equals(curRowEpoch)) {
                // This indicates that we have processed all rows belonging to a
                // corresponding day in the current month's bill.
                // Consume the batch
                dailyStatsConsumer.accept(monthlyBill, csvReader.getLinesRead());
                break;
            }
            prevRowEpoch = curRowEpoch;
        } while ((nextRow = csvReader.readNext()) != null);
        if (nextRow == null) {
            parsingComplete = true;
            monthlyStatsConsumer.accept(monthlyBill, billProcessedTimeMillis);
        }
        logger.fine("Finished parsing CSV billFileReader.");
        return parsingComplete;
    }

    /**
     * The initial rows of the bill may have been parsed and processed in the last run. This
     * method will check the date in the bill to compare if those rows were parsed and will
     * return the first row which is perceived as the first row that should be parsed.
     * @param csvReader the reader reading the CSV file.
     * @param billProcessedTimeMillis the time (in milliseconds since epoch) till which the bill
     *                                was parsed in the last run.
     * @return the first row that should be parsed; null if all rows of the bill have been parsed
     * or if the bill file is empty.
     * @throws IOException while parsing the bill.
     */
    private String[] getFirstLineToParse(CSVReader csvReader, long billProcessedTimeMillis)
            throws IOException {
        String[] nextRow;
        long timeToStartBillProcessing = getTimeToStartBillProcessing(billProcessedTimeMillis);
        while ((nextRow = csvReader.readNext()) != null) {
            if (nextRow.length != BillHeaders.values().length) {
                // Skip any blank or malformed rows
                logger.warning(
                        String.format("Skipping malformed row: %s", (Arrays.toString(nextRow))));
                break;
            }
            if (AzureCostStatsServiceHelper.getMillisForDateString(nextRow[DATE.position])
                    > timeToStartBillProcessing) {
                break;
            } else {
                logger.fine(
                        String.format("Skipping row since it has been parsed in the last run: %s",
                                (Arrays.toString(nextRow))));
            }
        }
        return nextRow;
    }

    /**
     * Segregate the cost of the constituent Azure accounts of the EA account, segregate
     * the service-level cost of each of these accounts and segregate the resource-level cost
     * in each of these services.
     * @param billElement the bill row.
     * @param monthlyBill the map with the account ID as the key and the account details
     *                          (containing the service and resource-level costs) as the value.
     * @return the map with the account ID as the key and the account details (containing
     *         the service and resource-level costs) as the value.
     */
    private Map<String, AzureSubscription> segregateSubscriptionServiceResourceCost(
            EaDetailedBillElement billElement, Map<String, AzureSubscription> monthlyBill) {

        String subscriptionGuid = billElement.subscriptionGuid;
        AzureSubscription subscription = monthlyBill.get(subscriptionGuid);
        if (subscription == null) {
            // New subscription found in the bill.
            AzureSubscription newSubscription = createSubscriptionDto(billElement);
            AzureService newService = newSubscription.addToServicesMap(billElement);
            newService.addToResourcesMap(billElement);
            monthlyBill.put(subscriptionGuid, newSubscription);
        } else if (subscription.fetchServiceDetail(billElement.meterCategory) == null) {
            // New service found.
            subscription.addCost(billElement.extendedCost);
            AzureService newService = subscription.addToServicesMap(billElement);
            newService.addToResourcesMap(billElement);
            monthlyBill.put(subscriptionGuid, subscription);
        } else {
            subscription.addCost(billElement.extendedCost);
            AzureService service = subscription.fetchServiceDetail(billElement.meterCategory);
            service.addToDailyCosts(billElement.epochDate, billElement.extendedCost);
            service.addToResourcesMap(billElement);
            monthlyBill.put(subscriptionGuid, subscription);
        }
        logger.fine(() -> String
                .format("Got %d Azure subscriptions in the bill.", monthlyBill.size()));
        return monthlyBill;
    }

    private AzureSubscription createSubscriptionDto(
            EaDetailedBillElement billElement) {
        return new AzureSubscription(billElement.subscriptionGuid, billElement.subscriptionName,
                billElement.accountOwnerId, billElement.accountName);
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
