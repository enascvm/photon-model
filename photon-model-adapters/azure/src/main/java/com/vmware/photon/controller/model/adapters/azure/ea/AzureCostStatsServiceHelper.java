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
        .AzureConstants.AUTH_HEADER_BEARER_PREFIX;
import static com.vmware.photon.controller.model.adapters.azure.constants
        .AzureCostConstants.API_VERSION_HEADER;
import static com.vmware.photon.controller.model.adapters.azure.constants
        .AzureCostConstants.API_VERSION_HEADER_VALUE;
import static com.vmware.photon.controller.model.adapters.azure.constants
        .AzureCostConstants.BILL_FORMAT;
import static com.vmware.photon.controller.model.adapters.azure.constants
        .AzureCostConstants.DETAILED_CSV_BILL_NAME_MID;
import static com.vmware.photon.controller.model.adapters.azure.constants
        .AzureCostConstants.OLD_EA_USAGE_REPORT;
import static com.vmware.photon.controller.model.adapters.azure.constants
        .AzureCostConstants.PATH_PARAM_BILLING_PERIODS;
import static com.vmware.photon.controller.model.adapters.azure.constants
        .AzureCostConstants.QUERY_PARAM_BILL_MONTH;
import static com.vmware.photon.controller.model.adapters.azure.constants
        .AzureCostConstants.QUERY_PARAM_BILL_TYPE;
import static com.vmware.photon.controller.model.adapters.azure.constants
        .AzureCostConstants.QUERY_PARAM_BILL_TYPE_VALUE_DETAILED;
import static com.vmware.photon.controller.model.adapters.azure.constants
        .AzureCostConstants.QUERY_PARAM_RESPONSE_FORMAT;
import static com.vmware.photon.controller.model.adapters.azure.constants
        .AzureCostConstants.QUERY_PARAM_RESPONSE_FORMAT_VALUE_CSV;
import static com.vmware.photon.controller.model.adapters.azure.constants
        .AzureCostConstants.QUERY_PARAM_RESPONSE_FORMAT_VALUE_JSON;
import static com.vmware.photon.controller.model.adapters.azure.constants
        .AzureCostConstants.SUMMARIZED_BILL;
import static com.vmware.photon.controller.model.adapters.azure.constants
        .AzureCostConstants.THRESHOLD_FOR_TIME_IN_SECONDS;
import static com.vmware.photon.controller.model.adapters.azure.constants
        .AzureCostConstants.TIMESTAMP_FORMAT_WITH_DATE_FORMAT_MM_DD_YYYY_WITH_OBLIQUE_DELIMITER;
import static com.vmware.photon.controller.model.adapters.azure.constants
        .AzureCostConstants.TIMESTAMP_FORMAT_WITH_DATE_FORMAT_YYYY_HYPHEN_MM;
import static com.vmware.photon.controller.model.adapters.azure.constants
        .AzureCostConstants.TIMESTAMP_FORMAT_WITH_DATE_FORMAT_YYYY_MM;
import static com.vmware.photon.controller.model.adapters.azure.constants
        .AzureCostConstants.TWO_MINUTES_IN_MILLISECONDS;
import static com.vmware.photon.controller.model.adapters.azure.constants
        .AzureCostConstants.UNKNOWN_SERVICE_NAME;
import static com.vmware.photon.controller.model.adapters.azure.constants
        .AzureCostConstants.UNKNOWN_SUBSCRIPTION;
import static com.vmware.photon.controller.model.adapters.azure.constants
        .AzureCostConstants.USAGE_API_KEY_JSON_EXPIRES_KEY;
import static com.vmware.photon.controller.model.adapters.azure.ea.AzureDetailedBillHandler.BillHeaders.ACCOUNT_NAME;
import static com.vmware.photon.controller.model.adapters.azure.ea.AzureDetailedBillHandler.BillHeaders.ACCOUNT_OWNER_ID;
import static com.vmware.photon.controller.model.adapters.azure.ea.AzureDetailedBillHandler.BillHeaders.ADDITIONAL_INFO;
import static com.vmware.photon.controller.model.adapters.azure.ea.AzureDetailedBillHandler.BillHeaders.CONSUMED_QUANTITY;
import static com.vmware.photon.controller.model.adapters.azure.ea.AzureDetailedBillHandler.BillHeaders.CONSUMED_SERVICE;
import static com.vmware.photon.controller.model.adapters.azure.ea.AzureDetailedBillHandler.BillHeaders.COST_CENTER;
import static com.vmware.photon.controller.model.adapters.azure.ea.AzureDetailedBillHandler.BillHeaders.DATE;
import static com.vmware.photon.controller.model.adapters.azure.ea.AzureDetailedBillHandler.BillHeaders.DAY;
import static com.vmware.photon.controller.model.adapters.azure.ea.AzureDetailedBillHandler.BillHeaders.DEPARTMENT_NAME;
import static com.vmware.photon.controller.model.adapters.azure.ea.AzureDetailedBillHandler.BillHeaders.EXTENDED_COST;
import static com.vmware.photon.controller.model.adapters.azure.ea.AzureDetailedBillHandler.BillHeaders.INSTANCE_ID;
import static com.vmware.photon.controller.model.adapters.azure.ea.AzureDetailedBillHandler.BillHeaders.METER_CATEGORY;
import static com.vmware.photon.controller.model.adapters.azure.ea.AzureDetailedBillHandler.BillHeaders.METER_ID;
import static com.vmware.photon.controller.model.adapters.azure.ea.AzureDetailedBillHandler.BillHeaders.METER_NAME;
import static com.vmware.photon.controller.model.adapters.azure.ea.AzureDetailedBillHandler.BillHeaders.METER_REGION;
import static com.vmware.photon.controller.model.adapters.azure.ea.AzureDetailedBillHandler.BillHeaders.METER_SUB_CATEGORY;
import static com.vmware.photon.controller.model.adapters.azure.ea.AzureDetailedBillHandler.BillHeaders.MONTH;
import static com.vmware.photon.controller.model.adapters.azure.ea.AzureDetailedBillHandler.BillHeaders.PRODUCT;
import static com.vmware.photon.controller.model.adapters.azure.ea.AzureDetailedBillHandler.BillHeaders.RESOURCE_GROUP;
import static com.vmware.photon.controller.model.adapters.azure.ea.AzureDetailedBillHandler.BillHeaders.RESOURCE_LOCATION;
import static com.vmware.photon.controller.model.adapters.azure.ea.AzureDetailedBillHandler.BillHeaders.RESOURCE_RATE;
import static com.vmware.photon.controller.model.adapters.azure.ea.AzureDetailedBillHandler.BillHeaders.SERVICE_ADMINISTRATOR_ID;
import static com.vmware.photon.controller.model.adapters.azure.ea.AzureDetailedBillHandler.BillHeaders.SERVICE_INFO_1;
import static com.vmware.photon.controller.model.adapters.azure.ea.AzureDetailedBillHandler.BillHeaders.SERVICE_INFO_2;
import static com.vmware.photon.controller.model.adapters.azure.ea.AzureDetailedBillHandler.BillHeaders.STORE_SERVICE_IDENTIFIER;
import static com.vmware.photon.controller.model.adapters.azure.ea.AzureDetailedBillHandler.BillHeaders.SUBSCRIPTION_GUID;
import static com.vmware.photon.controller.model.adapters.azure.ea.AzureDetailedBillHandler.BillHeaders.SUBSCRIPTION_ID;
import static com.vmware.photon.controller.model.adapters.azure.ea.AzureDetailedBillHandler.BillHeaders.SUBSCRIPTION_NAME;
import static com.vmware.photon.controller.model.adapters.azure.ea.AzureDetailedBillHandler.BillHeaders.TAGS;
import static com.vmware.photon.controller.model.adapters.azure.ea.AzureDetailedBillHandler.BillHeaders.UNIT_OF_MEASURE;
import static com.vmware.photon.controller.model.adapters.azure.ea.AzureDetailedBillHandler.BillHeaders.YEAR;
import static com.vmware.photon.controller.model.adapters.azure.ea.stats.AzureCostStatsService.DEFAULT_CURRENCY_VALUE;

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
import java.util.List;
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

import com.vmware.photon.controller.model.adapters.azure.constants.AzureCostConstants;
import com.vmware.photon.controller.model.adapters.azure.model.cost.EaDetailedBillElement;
import com.vmware.photon.controller.model.adapters.azure.model.cost.OldApi;
import com.vmware.photon.controller.model.adapters.azure.model.cost.OldEaSummarizedBillElement;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.UriUtils;

/**
 * Utility class for Azure cost stats service.
 */
public interface AzureCostStatsServiceHelper {

    Logger logger = Logger.getLogger("AzureCostStatsServiceHelper");

    String TEMP_DIR_LOCATION = "java.io.tmpdir";
    String AZURE_BILLS = "azure-bills";

    static Operation getSummarizedBillOperation(String enrollmentNumber, String accessToken,
            LocalDate billMonthToDownload) {
        String baseUri = AdapterUriUtil
                .expandUriPathTemplate(SUMMARIZED_BILL, enrollmentNumber,
                        dateInNewApiExpectedFormat(billMonthToDownload));

        logger.info(String.format("Request: %s", baseUri));

        Operation operation = Operation.createGet(UriUtils.buildUri(baseUri));
        addDefaultRequestHeaders(operation, accessToken);
        // Retry thrice on failure.
        operation = operation.setRetryCount(3);
        // Set expiration to be 60 secs from the time the operation is created. This is being
        // done since we have seen instances when the detailed bill takes extremely long
        // time to respond for large bills.
        operation = operation.setExpiration((getMillisNow() + TWO_MINUTES_IN_MILLISECONDS) * 1000);
        return operation;
    }

    @OldApi
    static Request getOldDetailedBillRequest(String enrollmentNumber, String accessToken,
            LocalDate billMonthToDownload) {
        String baseUri = AdapterUriUtil
                .expandUriPathTemplate(OLD_EA_USAGE_REPORT, enrollmentNumber);
        String dateInBillingApiExpectedFormat = getDateInBillingApiFormat(
                billMonthToDownload);

        URI uri = UriUtils.extendUriWithQuery(UriUtils.buildUri(baseUri),
                QUERY_PARAM_BILL_MONTH, dateInBillingApiExpectedFormat,
                QUERY_PARAM_BILL_TYPE, QUERY_PARAM_BILL_TYPE_VALUE_DETAILED,
                QUERY_PARAM_RESPONSE_FORMAT, QUERY_PARAM_RESPONSE_FORMAT_VALUE_CSV);

        return new Request.Builder()
                .url(uri.toString())
                .addHeader(Operation.AUTHORIZATION_HEADER, AUTH_HEADER_BEARER_PREFIX + accessToken)
                .addHeader(API_VERSION_HEADER, API_VERSION_HEADER_VALUE)
                .build();
    }

    @OldApi
    static Operation getOldBillOperation(String enrollmentNumber, String accessToken,
            LocalDate billMonthToDownload, String billType) {
        String baseUri = AdapterUriUtil
                .expandUriPathTemplate(OLD_EA_USAGE_REPORT, enrollmentNumber);
        // Get the summarized bill in JSON format and detailed bill in CSV format.
        String billFormat = billType.equals(QUERY_PARAM_BILL_TYPE_VALUE_DETAILED) ?
                QUERY_PARAM_RESPONSE_FORMAT_VALUE_CSV :
                QUERY_PARAM_RESPONSE_FORMAT_VALUE_JSON;
        String dateInBillingApiExpectedFormat = getDateInBillingApiFormat(
                billMonthToDownload);
        URI uri = UriUtils.extendUriWithQuery(UriUtils.buildUri(baseUri),
                QUERY_PARAM_BILL_MONTH, dateInBillingApiExpectedFormat,
                QUERY_PARAM_BILL_TYPE, billType,
                QUERY_PARAM_RESPONSE_FORMAT, billFormat);

        logger.info(String.format("Request: %s", uri.toString()));

        Operation operation = Operation.createGet(uri);
        addDefaultRequestHeaders(operation, accessToken);
        // Retry thrice on failure.
        operation = operation.setRetryCount(3);
        // Set expiration to be 60 secs from the time the operation is created. This is being
        // done since we have seen instances when the detailed bill takes extremely long
        // time to respond for large bills.
        operation = operation.setExpiration((getMillisNow() + TWO_MINUTES_IN_MILLISECONDS) * 1000);
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
        return enrollmentNumber + DETAILED_CSV_BILL_NAME_MID + "-" + year + "-" + monthStrBuffer
                + BILL_FORMAT;
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
                AUTH_HEADER_BEARER_PREFIX + accessToken);
        operation.addRequestHeader(API_VERSION_HEADER,
                API_VERSION_HEADER_VALUE);
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
        return LocalDateTime.parse(yearMonth,
                DateTimeFormat.forPattern(TIMESTAMP_FORMAT_WITH_DATE_FORMAT_YYYY_HYPHEN_MM))
                .toLocalDate();
    }

    static LocalDate getLocalDateFromYearMonthString(String yearMonth) {
        return LocalDateTime.parse(yearMonth,
                DateTimeFormat.forPattern(TIMESTAMP_FORMAT_WITH_DATE_FORMAT_YYYY_MM))
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
        LocalDate localDate = LocalDateTime.parse(dateString,
                DateTimeFormat.forPattern(
                        TIMESTAMP_FORMAT_WITH_DATE_FORMAT_MM_DD_YYYY_WITH_OBLIQUE_DELIMITER))
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
                UNKNOWN_SERVICE_NAME :
                detailedBillElement.meterCategory;

        // set subscription name to unknown in case subscription name is absent.
        detailedBillElement.subscriptionName = StringUtils
                .isBlank(detailedBillElement.subscriptionName) ?
                UNKNOWN_SUBSCRIPTION :
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
                .equalsIgnoreCase(DEFAULT_CURRENCY_VALUE)) {
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
                    .get(USAGE_API_KEY_JSON_EXPIRES_KEY).getAsLong();
            if (expiryTime < THRESHOLD_FOR_TIME_IN_SECONDS) {
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
        int monthParamEndIndex =
                requestUri.indexOf(QUERY_PARAM_BILL_MONTH + "=") + QUERY_PARAM_BILL_MONTH.length()
                        + 1;
        int ampersand = requestUri.indexOf("&");
        return getLocalDateFromYearHyphenMonthString(requestUri.substring(monthParamEndIndex, ampersand))
                .withDayOfMonth(1);
    }

    static LocalDate getMonthFromRequestUri(String requestUri) {
        int monthParamEndIndex =
                requestUri.indexOf(PATH_PARAM_BILLING_PERIODS + "/") + PATH_PARAM_BILLING_PERIODS
                        .length() + 1;
        return getLocalDateFromYearMonthString(
                requestUri.substring(monthParamEndIndex, monthParamEndIndex + 6)).withDayOfMonth(1);
    }


}
