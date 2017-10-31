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

import static com.vmware.photon.controller.model.adapters.azure.ea.utils
        .AzureDetailedBillHandler.BillHeaders.ACCOUNT_NAME;
import static com.vmware.photon.controller.model.adapters.azure.ea.utils
        .AzureDetailedBillHandler.BillHeaders.ACCOUNT_OWNER_ID;
import static com.vmware.photon.controller.model.adapters.azure.ea.utils
        .AzureDetailedBillHandler.BillHeaders.ADDITIONAL_INFO;
import static com.vmware.photon.controller.model.adapters.azure.ea.utils
        .AzureDetailedBillHandler.BillHeaders.CONSUMED_QUANTITY;
import static com.vmware.photon.controller.model.adapters.azure.ea.utils
        .AzureDetailedBillHandler.BillHeaders.CONSUMED_SERVICE;
import static com.vmware.photon.controller.model.adapters.azure.ea.utils
        .AzureDetailedBillHandler.BillHeaders.COST_CENTER;
import static com.vmware.photon.controller.model.adapters.azure.ea.utils
        .AzureDetailedBillHandler.BillHeaders.DATE;
import static com.vmware.photon.controller.model.adapters.azure.ea.utils
        .AzureDetailedBillHandler.BillHeaders.DAY;
import static com.vmware.photon.controller.model.adapters.azure.ea.utils
        .AzureDetailedBillHandler.BillHeaders.DEPARTMENT_NAME;
import static com.vmware.photon.controller.model.adapters.azure.ea.utils
        .AzureDetailedBillHandler.BillHeaders.EXTENDED_COST;
import static com.vmware.photon.controller.model.adapters.azure.ea.utils
        .AzureDetailedBillHandler.BillHeaders.INSTANCE_ID;
import static com.vmware.photon.controller.model.adapters.azure.ea.utils
        .AzureDetailedBillHandler.BillHeaders.METER_CATEGORY;
import static com.vmware.photon.controller.model.adapters.azure.ea.utils
        .AzureDetailedBillHandler.BillHeaders.METER_ID;
import static com.vmware.photon.controller.model.adapters.azure.ea.utils
        .AzureDetailedBillHandler.BillHeaders.METER_NAME;
import static com.vmware.photon.controller.model.adapters.azure.ea.utils
        .AzureDetailedBillHandler.BillHeaders.METER_REGION;
import static com.vmware.photon.controller.model.adapters.azure.ea.utils
        .AzureDetailedBillHandler.BillHeaders.METER_SUB_CATEGORY;
import static com.vmware.photon.controller.model.adapters.azure.ea.utils
        .AzureDetailedBillHandler.BillHeaders.MONTH;
import static com.vmware.photon.controller.model.adapters.azure.ea.utils
        .AzureDetailedBillHandler.BillHeaders.PRODUCT;
import static com.vmware.photon.controller.model.adapters.azure.ea.utils
        .AzureDetailedBillHandler.BillHeaders.RESOURCE_GROUP;
import static com.vmware.photon.controller.model.adapters.azure.ea.utils
        .AzureDetailedBillHandler.BillHeaders.RESOURCE_LOCATION;
import static com.vmware.photon.controller.model.adapters.azure.ea.utils
        .AzureDetailedBillHandler.BillHeaders.RESOURCE_RATE;
import static com.vmware.photon.controller.model.adapters.azure.ea.utils
        .AzureDetailedBillHandler.BillHeaders.SERVICE_ADMINISTRATOR_ID;
import static com.vmware.photon.controller.model.adapters.azure.ea.utils
        .AzureDetailedBillHandler.BillHeaders.SERVICE_INFO_1;
import static com.vmware.photon.controller.model.adapters.azure.ea.utils
        .AzureDetailedBillHandler.BillHeaders.SERVICE_INFO_2;
import static com.vmware.photon.controller.model.adapters.azure.ea.utils
        .AzureDetailedBillHandler.BillHeaders.STORE_SERVICE_IDENTIFIER;
import static com.vmware.photon.controller.model.adapters.azure.ea.utils
        .AzureDetailedBillHandler.BillHeaders.SUBSCRIPTION_GUID;
import static com.vmware.photon.controller.model.adapters.azure.ea.utils
        .AzureDetailedBillHandler.BillHeaders.SUBSCRIPTION_ID;
import static com.vmware.photon.controller.model.adapters.azure.ea.utils
        .AzureDetailedBillHandler.BillHeaders.SUBSCRIPTION_NAME;
import static com.vmware.photon.controller.model.adapters.azure.ea.utils
        .AzureDetailedBillHandler.BillHeaders.TAGS;
import static com.vmware.photon.controller.model.adapters.azure.ea.utils
        .AzureDetailedBillHandler.BillHeaders.UNIT_OF_MEASURE;
import static com.vmware.photon.controller.model.adapters.azure.ea.utils
        .AzureDetailedBillHandler.BillHeaders.YEAR;

import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.google.gson.JsonParser;

import okhttp3.Request;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;

import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureCostConstants;
import com.vmware.photon.controller.model.adapters.azure.model.cost.EaDetailedBillElement;
import com.vmware.photon.controller.model.adapters.azure.model.cost.OldApi;
import com.vmware.photon.controller.model.adapters.azure.model.cost.OldEaSummarizedBillElement;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Utility class for Azure cost stats service.
 */
public interface AzureCostHelper {

    Logger logger = Logger.getLogger(AzureCostHelper.class.getName());

    String TEMP_DIR_LOCATION = "java.io.tmpdir";
    String AZURE_BILLS = "azure-bills";

    static Operation getSummarizedBillOp(String enrollmentNumber, String accessToken,
            LocalDate billMonthToDownload) {
        String baseUri = AdapterUriUtil
                .expandUriPathTemplate(AzureCostConstants.SUMMARIZED_BILL, enrollmentNumber,
                        dateInNewApiExpectedFormat(billMonthToDownload));

        logger.info(String.format("Request: %s", baseUri));

        Operation operation = Operation.createGet(UriUtils.buildUri(baseUri));
        addDefaultRequestHeaders(operation, accessToken);
        // Retry thrice on failure.
        operation = operation.setRetryCount(3);
        operation = setExpirationForExternalRequests(operation);
        return operation;
    }

    @OldApi
    static Request getOldDetailedBillRequest(String enrollmentNumber, String accessToken,
            LocalDate billMonthToDownload) {
        String baseUri = AdapterUriUtil
                .expandUriPathTemplate(AzureCostConstants.OLD_EA_USAGE_REPORT, enrollmentNumber);
        String dateInBillingApiExpectedFormat = getDateInBillingApiFormat(
                billMonthToDownload);

        URI uri = UriUtils.extendUriWithQuery(UriUtils.buildUri(baseUri),
                AzureCostConstants.QUERY_PARAM_BILL_MONTH, dateInBillingApiExpectedFormat,
                AzureCostConstants.QUERY_PARAM_BILL_TYPE,
                AzureCostConstants.QUERY_PARAM_BILL_TYPE_VALUE_DETAILED,
                AzureCostConstants.QUERY_PARAM_RESPONSE_FORMAT,
                AzureCostConstants.QUERY_PARAM_RESPONSE_FORMAT_VALUE_CSV);

        return new Request
                .Builder()
                .url(uri.toString())
                .addHeader(Operation.AUTHORIZATION_HEADER,
                AzureConstants.AUTH_HEADER_BEARER_PREFIX + accessToken)
                .addHeader(AzureCostConstants.API_VERSION_HEADER,
                        AzureCostConstants.API_VERSION_HEADER_VALUE).build();
    }

    @OldApi
    static Operation getOldBillOperation(String enrollmentNumber, String accessToken,
            LocalDate billMonthToDownload, String billType) {
        String baseUri = AdapterUriUtil
                .expandUriPathTemplate(AzureCostConstants.OLD_EA_USAGE_REPORT, enrollmentNumber);
        // Get the summarized bill in JSON format and detailed bill in CSV format.
        String billFormat = billType.equals(AzureCostConstants.QUERY_PARAM_BILL_TYPE_VALUE_DETAILED) ?
                AzureCostConstants.QUERY_PARAM_RESPONSE_FORMAT_VALUE_CSV :
                AzureCostConstants.QUERY_PARAM_RESPONSE_FORMAT_VALUE_JSON;
        String dateInBillingApiExpectedFormat = getDateInBillingApiFormat(
                billMonthToDownload);
        URI uri = UriUtils.extendUriWithQuery(UriUtils.buildUri(baseUri),
                AzureCostConstants.QUERY_PARAM_BILL_MONTH, dateInBillingApiExpectedFormat,
                AzureCostConstants.QUERY_PARAM_BILL_TYPE, billType,
                AzureCostConstants.QUERY_PARAM_RESPONSE_FORMAT, billFormat);

        logger.info(String.format("Request: %s", uri.toString()));

        Operation operation = Operation.createGet(uri);
        addDefaultRequestHeaders(operation, accessToken);
        // Retry thrice on failure.
        operation = operation.setRetryCount(3);
        operation = setExpirationForExternalRequests(operation);
        return operation;
    }

    @OldApi
    static Operation getOldBillAvailableMonths(String enrollmentNumber, String accessToken) {
        String baseUri = AdapterUriUtil
                .expandUriPathTemplate(AzureCostConstants.OLD_EA_BILL_AVAILABLE_MONTHS, enrollmentNumber);
        // Get the summarized bill in JSON format and detailed bill in CSV format.
        URI uri = UriUtils.extendUriWithQuery(UriUtils.buildUri(baseUri));

        logger.info(String.format("Request: %s", uri.toString()));

        Operation operation = Operation.createGet(uri);
        addDefaultRequestHeaders(operation, accessToken);
        // Retry thrice on failure.
        operation = operation.setRetryCount(3);
        operation = setExpirationForExternalRequests(operation);
        return operation;
    }

    static String writeBillToFile(String enrollmentNumber, LocalDate date, String content)
            throws IOException {
        final Path tempDirectory = Paths.get(System.getProperty(TEMP_DIR_LOCATION), AZURE_BILLS);
        Path workingDirectory = Files.createDirectories(tempDirectory);
        String billFileName = getCsvBillFileName(enrollmentNumber, date);
        billFileName = Paths.get(workingDirectory.toString(), billFileName).toString();
        FileWriter fileWriter = new FileWriter(billFileName);
        fileWriter.write(content);
        fileWriter.close();
        return billFileName;
    }

    static String getCsvBillFileName(String enrollmentNumber, LocalDate date) {
        StringBuilder monthStrBuffer = new StringBuilder();
        int month = date.getMonthOfYear();
        int year = date.getYear();
        if (month <= 9) {
            monthStrBuffer.append('0');
        }
        monthStrBuffer.append(month);
        return enrollmentNumber + AzureCostConstants.DETAILED_CSV_BILL_NAME_MID + "-" + year + "-" + monthStrBuffer
                + AzureCostConstants.BILL_FORMAT;
    }

    static ServiceStat createServiceStat(String serviceResourceCostMetric, Number value,
            String currencyUnit, Long timestamp) {
        ServiceStats.ServiceStat stat = new ServiceStats.ServiceStat();
        stat.name = serviceResourceCostMetric;
        stat.latestValue = value != null ? value.doubleValue() : 0.0d;
        stat.sourceTimeMicrosUtc = TimeUnit.MILLISECONDS.toMicros(timestamp);
        stat.unit = currencyUnit;
        return stat;
    }

    /**
     * Add the default headers applicable to all API calls.
     * @param operation operation to perform.
     * @param accessToken API key for authorization.
     */
    static void addDefaultRequestHeaders(Operation operation, String accessToken) {
        operation.addRequestHeader(Operation.AUTHORIZATION_HEADER,
                AzureConstants.AUTH_HEADER_BEARER_PREFIX + accessToken);
        operation.addRequestHeader(AzureCostConstants.API_VERSION_HEADER,
                AzureCostConstants.API_VERSION_HEADER_VALUE);
    }

    static boolean isCurrentMonth(LocalDate month) {
        if (month == null) {
            return false;
        }
        LocalDate firstDayOfCurrentMonth = getFirstDayOfCurrentMonth();
        return month.getMonthOfYear() == firstDayOfCurrentMonth.getMonthOfYear() &&
                month.getYear() == firstDayOfCurrentMonth.getYear();
    }

    static boolean isPreviousMonth(LocalDate month) {
        if (month == null) {
            return false;
        }
        LocalDate firstDayOfCurrentMonthMinusOne = getFirstDayOfCurrentMonth().minusMonths(1);

        return month.getMonthOfYear() == firstDayOfCurrentMonthMinusOne.getMonthOfYear() &&
                month.getYear() == firstDayOfCurrentMonthMinusOne.getYear();
    }

    static String getDateInBillingApiFormat(LocalDate date) {
        int year = date.getYear();
        int month = date.getMonthOfYear();
        String monthStr = Integer.toString(month);
        if (month < 10) {
            monthStr = "0" + monthStr;
        }
        return year + "-" + monthStr;
    }

    static String dateInNewApiExpectedFormat(LocalDate date) {
        int year = date.getYear();
        int month = date.getMonthOfYear();
        String monthStr = Integer.toString(month);
        if (month < 10) {
            monthStr = "0" + monthStr;
        }
        return year + monthStr;
    }

    static LocalDate getFirstDayOfCurrentMonth() {
        return getDateToday().withDayOfMonth(1);
    }

    static LocalDate getDateToday() {
        return LocalDate.now(DateTimeZone.UTC);
    }

    static long getMillisNow() {
        return DateTime.now(DateTimeZone.UTC).getMillis();
    }

    static LocalDate getLocalDateFromYearHyphenMonthString(String yearMonth) {
        return LocalDateTime.parse(yearMonth, DateTimeFormat
                .forPattern(AzureCostConstants.TIMESTAMP_FORMAT_WITH_DATE_FORMAT_YYYY_HYPHEN_MM))
                .toLocalDate();
    }

    static LocalDate getLocalDateFromYearMonthString(String yearMonth) {
        return LocalDateTime.parse(yearMonth, DateTimeFormat
                .forPattern(AzureCostConstants.TIMESTAMP_FORMAT_WITH_DATE_FORMAT_YYYY_MM))
                .toLocalDate();
    }

    /**
     * Gets date as string and convert it to epoch.
     * @param dateString date as string (in the format MM-dd-yyyy).
     * @return date in epoch (with time as start of the day).
     */
    static long getMillisForDateString(String dateString) {
        if (StringUtils.isBlank(dateString)) {
            logger.warning(String.format("Invalid date string: %s. Using 0 (zero) as epoch date.",
                    dateString));
            return 0;
        }
        LocalDate localDate = LocalDateTime.parse(dateString, DateTimeFormat.forPattern(
                AzureCostConstants.TIMESTAMP_FORMAT_WITH_DATE_FORMAT_MM_DD_YYYY_WITH_OBLIQUE_DELIMITER))
                .toLocalDate();
        return getMillisForDate(localDate);
    }

    static long getMillisForDate(LocalDate localDate) {
        return localDate.toDateTimeAtStartOfDay(DateTimeZone.UTC).getMillis();
    }

    @OldApi
    static OldEaSummarizedBillElement sanitizeSummarizedBillElementUsingOldApi(
            OldEaSummarizedBillElement summarizedBillElement) {
        return summarizedBillElement;
    }

    /**
     * Get a bill line item and check if any of the required / mandatory fields are not present
     * or are in a form that cannot be processed further (like cost being a non-parse-able double)
     * and if they are, set them to an acceptable value.
     * This method also processes fields in the elements, which after processing, would make
     * processing those fields faster or easier. For ex. convert date in the element to epoch time
     * @param billRow bill line item (may be a JSON object's Java representation)
     * @param currency currency of costs in the bill.
     * @return the sanitized bill item.
     */
    static EaDetailedBillElement sanitizeDetailedBillElement(
            String[] billRow, String currency) {
        EaDetailedBillElement detailedBillElement = constructDetailedBillElementObject(billRow);
        // set meter category to unknown in case meter category is absent.
        detailedBillElement.meterCategory = StringUtils.isBlank(detailedBillElement.meterCategory) ?
                AzureCostConstants.UNKNOWN_SERVICE_NAME :
                detailedBillElement.meterCategory;

        // set subscription name to unknown in case subscription name is absent.
        detailedBillElement.subscriptionName = StringUtils
                .isBlank(detailedBillElement.subscriptionName) ?
                AzureCostConstants.UNKNOWN_SUBSCRIPTION :
                detailedBillElement.subscriptionName;

        // set epochDate to epoch format of date.
        detailedBillElement.epochDate = getMillisForDateString(detailedBillElement.date);

        convertToUsd(detailedBillElement, currency);

        return detailedBillElement;
    }

    static EaDetailedBillElement constructDetailedBillElementObject(String[] billRow) {
        EaDetailedBillElement billElement = new EaDetailedBillElement();
        billElement.accountOwnerId = billRow[ACCOUNT_OWNER_ID.position];
        billElement.accountName = billRow[ACCOUNT_NAME.position];
        billElement.serviceAdministratorId = billRow[SERVICE_ADMINISTRATOR_ID.position];
        billElement.subscriptionId = billRow[SUBSCRIPTION_ID.position];
        billElement.subscriptionGuid = billRow[SUBSCRIPTION_GUID.position];
        billElement.subscriptionName = billRow[SUBSCRIPTION_NAME.position];
        billElement.date = billRow[DATE.position];
        billElement.month = getIntNoException(billRow, billRow[MONTH.position]);
        billElement.day = getIntNoException(billRow, billRow[DAY.position]);
        billElement.year = getIntNoException(billRow, billRow[YEAR.position]);
        billElement.product = billRow[PRODUCT.position];
        billElement.meterId = billRow[METER_ID.position];
        billElement.meterCategory = billRow[METER_CATEGORY.position];
        billElement.meterSubCategory = billRow[METER_SUB_CATEGORY.position];
        billElement.meterRegion = billRow[METER_REGION.position];
        billElement.meterName = billRow[METER_NAME.position];
        billElement.consumedQuantity = getDoubleOrNull(billRow,
                billRow[CONSUMED_QUANTITY.position]);
        billElement.resourceRate = getDoubleOrNull(billRow, billRow[RESOURCE_RATE.position]);
        billElement.extendedCost = getDoubleOrNull(billRow, billRow[EXTENDED_COST.position]);
        billElement.resourceLocation = billRow[RESOURCE_LOCATION.position];
        billElement.consumedService = billRow[CONSUMED_SERVICE.position];
        billElement.instanceId = billRow[INSTANCE_ID.position];
        billElement.serviceInfo1 = billRow[SERVICE_INFO_1.position];
        billElement.serviceInfo2 = billRow[SERVICE_INFO_2.position];
        billElement.additionalInfo = billRow[ADDITIONAL_INFO.position];
        billElement.tags = billRow[TAGS.position];
        billElement.storeServiceIdentifier = billRow[STORE_SERVICE_IDENTIFIER.position];
        billElement.departmentName = billRow[DEPARTMENT_NAME.position];
        billElement.costCenter = billRow[COST_CENTER.position];
        billElement.unitOfMeasure = billRow[UNIT_OF_MEASURE.position];
        billElement.resourceGroup = billRow[RESOURCE_GROUP.position];

        return billElement;
    }

    static int getIntNoException(String[] billRow, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException numberFormatEx) {
            logger.warning(String.format(
                    "Unexpected value %s in bill element: %s. "
                            + "Setting value to 0 (zero) for this bill element.",
                    value, Arrays.toString(billRow)));
            return 0;
        }

    }

    static double getDoubleOrNull(String[] billRow, String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException numberFormatEx) {
            logger.warning(String.format(
                    "Unexpected value %s in bill element: %s. "
                            + "Setting value to 0 (zero) for this bill element.",
                    value, Arrays.toString(billRow)));
            return 0d;
        }

    }

    static Double getDoubleOrNull(Object billElement, String value) {
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException numberFormatEx) {
            logger.warning(String.format("Could not convert cost obtained from summarized bill " +
                    "to a double value: %s", billElement.toString()));
            return null;
        }
    }

    static double getDoubleOrZero(Object billElement, String value) {
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException numberFormatEx) {
            logger.warning(String.format("Could not convert cost obtained from summarized bill " +
                    "to a double value: %s", billElement.toString()));
            return 0d;
        }
    }

    static void convertToUsd(EaDetailedBillElement detailedBillElement, String currency) {
        if (detailedBillElement.extendedCost != 0 && !StringUtils.isBlank(currency) && !currency
                .equalsIgnoreCase(AzureCostConstants.DEFAULT_CURRENCY_VALUE)) {
            Double exchangeRate = AzureCostConstants.exchangeRates.get(currency);
            if (exchangeRate == null) {
                // Will log for every line item in the bill.
                logger.severe("Unknown currency of costs. Not converting to USD.");
                return;
            }
            if (exchangeRate != 1d && exchangeRate != 0d) {
                detailedBillElement.extendedCost = detailedBillElement.extendedCost / exchangeRate;
            }
        }
    }

    static int safeLongToInt(long l) {
        if (l < Integer.MIN_VALUE || l > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    l + " cannot be cast to int without changing its value.");
        }
        return (int) l;
    }

    /**
     * Decodes the usage API key and gets the time when the key will expire.
     * @param usageApiKey JWT token.
     * @return time in milliseconds when the token will expire.
     */
    static long getUsageKeyExpiryTime(String usageApiKey) {
        try {
            List<String> jwtDecodedJson = getJwtDecodedJson(usageApiKey);
            long expiryTime = new JsonParser().parse(jwtDecodedJson.get(1)).getAsJsonObject()
                    .get(AzureCostConstants.USAGE_API_KEY_JSON_EXPIRES_KEY).getAsLong();
            if (expiryTime < AzureCostConstants.THRESHOLD_FOR_TIME_IN_SECONDS) {
                // convert to millis
                expiryTime *= 1000;
            }
            return expiryTime;
        } catch (Exception e) {
            logger.warning("Couldn't decode JWT token to get expiry time. Returning -1");
            return -1;
        }
    }

    static List<String> getJwtDecodedJson(String jwtToken) throws UnsupportedEncodingException {
        List<String> jsonStrings = new ArrayList<>();
        String[] splitValues = jwtToken.split("\\.");
        jsonStrings.add(getJson(splitValues[0]));
        jsonStrings.add(getJson(splitValues[1]));
        return jsonStrings;

    }

    static String getJson(String strEncoded) throws UnsupportedEncodingException {
        byte[] decodedBytes = Base64.getDecoder().decode(strEncoded);
        return new String(decodedBytes, "UTF-8");
    }

    @OldApi
    static LocalDate getMonthFromOldRequestUri(String requestUri) {
        int monthParamEndIndex = requestUri.indexOf(AzureCostConstants.QUERY_PARAM_BILL_MONTH + "=")
                + AzureCostConstants.QUERY_PARAM_BILL_MONTH.length() + 1;
        int ampersand = requestUri.indexOf("&");
        return getLocalDateFromYearHyphenMonthString(requestUri.substring(monthParamEndIndex, ampersand))
                .withDayOfMonth(1);
    }

    static LocalDate getMonthFromRequestUri(String requestUri) {
        int monthParamEndIndex =
                requestUri.indexOf(AzureCostConstants.PATH_PARAM_BILLING_PERIODS + "/")
                        + AzureCostConstants.PATH_PARAM_BILLING_PERIODS.length() + 1;
        return getLocalDateFromYearMonthString(
                requestUri.substring(monthParamEndIndex, monthParamEndIndex + 6)).withDayOfMonth(1);
    }

    /**
     * Extending expiration for slow APIs. This is being done since we have seen instances
     * when the detailed bill takes extremely long time to respond for large bills.
     * @param op operation whose expiration has to be increased.
     */
    static Operation setExpirationForExternalRequests(Operation op) {
        return op.setExpiration(Utils.fromNowMicrosUtc(
                TimeUnit.SECONDS.toMicros(AzureCostConstants.EXTERNAL_REQUEST_TIMEOUT_SECONDS)));
    }

    static Set<LocalDate> getSummarizedBillsToDownload(long billProcessedTimeMillis) {
        LocalDate billProcessedDate = new LocalDate(billProcessedTimeMillis, DateTimeZone.UTC);
        LocalDate today = LocalDate.now(DateTimeZone.UTC).dayOfMonth().withMaximumValue();;
        if (billProcessedTimeMillis == 0) {
            billProcessedDate = today
                    .minusMonths(AzureCostConstants.NO_OF_MONTHS_TO_GET_PAST_BILLS);
        }
        Set<LocalDate> summarizedBillsToGet = new HashSet<>();
        for (; billProcessedDate.isBefore(today) || billProcessedDate
                .isEqual(today); billProcessedDate = billProcessedDate.plusMonths(1)) {
            if (billProcessedDate.getDayOfMonth()
                    <= AzureCostConstants.NO_OF_DAYS_MARGIN_FOR_AZURE_TO_UPDATE_BILL) {
                summarizedBillsToGet.add(billProcessedDate.minusMonths(1));
            }
            summarizedBillsToGet.add(billProcessedDate);
        }
        return summarizedBillsToGet;
    }

    /**
     * Check if the detailed bill should be downloaded. The detailed bill will not be downloaded
     * and processed in case the final total EA account cost in the last bill processed and the new
     * total EA account usage cost obtained from the summarized bill is the same and no new
     * subscriptions have been explicitly added to the system.
     * @param oldCost total EA account cost obtained by summing up all line item costs
     *                from the detailed bill in the last run.
     * @param newCost total EA account usage cost obtained from the summarized bill API
     * @param newSubscriptions explicitly added subscriptions
     * @return true if the bill has to be downloaded, false otherwise.
     */
    static boolean shouldDownloadCurrentMonthBill(Double oldCost, Double newCost,
            Set<String> newSubscriptions) {
        return oldCost == null || newCost == null
                || !oldCost.equals(newCost) || !newSubscriptions.isEmpty();
    }

    static boolean haveProcessedAllPastBills(long billProcessedMillis,
            long oldestBillProcessedMillis, int noBillsAvailable) {
        if (billProcessedMillis == 0) {
            return false;
        }
        if (AzureCostConstants.NO_OF_MONTHS_TO_GET_PAST_BILLS > 0) {
            long oldestTimeToConsiderMillis;
            // If we have parsed the past bills at least once, oldest bill processed time will be
            // populated; consider this metric while considering whether all bills have been
            // processed, else use the current bill processed time since this will indicate which
            // past bill is being processed.
            oldestTimeToConsiderMillis = oldestBillProcessedMillis > 0 ?
                    Math.min(billProcessedMillis, oldestBillProcessedMillis) : billProcessedMillis;
            LocalDate oldestBillProcessedDate = new LocalDate(oldestTimeToConsiderMillis,
                    DateTimeZone.UTC);
            LocalDate currentMonth = LocalDate.now(DateTimeZone.UTC);
            int maxPastBillsToDownload = Math
                    .min(AzureCostConstants.NO_OF_MONTHS_TO_GET_PAST_BILLS, noBillsAvailable);
            LocalDate oldestMonthToProcess = currentMonth.minusMonths(maxPastBillsToDownload)
                    .dayOfMonth().withMaximumValue();
            if (oldestBillProcessedDate.isBefore(oldestMonthToProcess) ||
                    oldestBillProcessedDate.equals(oldestMonthToProcess)) {
                // Even if we have processed all past bills, process previous month's bills on
                // the first five days of the current month.
                if (currentMonth.getDayOfMonth()
                        <= AzureCostConstants.NO_OF_DAYS_MARGIN_FOR_AZURE_TO_UPDATE_BILL) {
                    if (isPreviousMonth(new LocalDate(billProcessedMillis))) {
                        logger.fine("Previous month's bills have been processed.");
                        return true;
                    } else {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }
        return true;
    }

    /**
     * Past months bills be downloaded in the following case:
     *   1. The bill processed time is 0: This means this is the first run for the account
     *   2. The cost is being collected on the first
     *      AzureCostConstants.NO_OF_DAYS_MARGIN_FOR_AZURE_TO_UPDATE_BILL days of the month.
     *      Azure will keep updating the past month's bill for these days and hence we'll have to
     *      collect past month's cost.
     * @param billProcessedTimeMillis the last time when the current month's bill was processed.
     * @param oldestBillProcessedMillis latest time from the oldest bill parsed for an account.
     * @param noPastBillsAvailable the minimum of number of bills that are actually available after
     *                             the account was created and
     *                             AzureCostConstants.NO_OF_MONTHS_TO_GET_PAST_BILLS
     * @return true only in the above cases
     */
    static boolean shouldDownloadPastBills(long billProcessedTimeMillis,
            long oldestBillProcessedMillis, int noPastBillsAvailable) {
        if (billProcessedTimeMillis == 0) {
            return true;
        }
        if (AzureCostConstants.NO_OF_MONTHS_TO_GET_PAST_BILLS > 0) {
            LocalDate currentMonth = LocalDate.now(DateTimeZone.UTC);
            if (currentMonth.getDayOfMonth()
                    <= AzureCostConstants.NO_OF_DAYS_MARGIN_FOR_AZURE_TO_UPDATE_BILL) {
                // Download past month's bill if today is 5th or less of the month since Azure keeps
                // updating the bill for the past month
                return true;
            }
            if (oldestBillProcessedMillis == 0 ||
                    !haveProcessedAllPastBills(billProcessedTimeMillis,
                            oldestBillProcessedMillis, noPastBillsAvailable)) {
                // Download past months bills in case all bills have not been downloaded and
                // current month's bill has been parsed.
                return true;
            }
        }
        return false;
    }

}
