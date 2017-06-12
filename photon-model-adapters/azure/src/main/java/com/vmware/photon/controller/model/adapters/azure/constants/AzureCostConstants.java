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

package com.vmware.photon.controller.model.adapters.azure.constants;

import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.google.common.collect.ImmutableMap;

import com.vmware.photon.controller.model.adapters.azure.model.cost.ExchangeRates;
import com.vmware.xenon.common.FileUtils;
import com.vmware.xenon.common.Operation;

/**
 * This class contains Microsoft Azure cost-collection constants.
 */
public class AzureCostConstants {

    private static final Logger logger = Logger.getLogger("AzureCostConstants");

    // EA-specific
    // Azure keeps updating the bill for past days. This number is an approximate indicator of those
    // number of days.
    public static final int NO_OF_DAYS_MARGIN_FOR_AZURE_TO_UPDATE_BILL = 5;
    public static final long NO_OF_DAYS_MARGIN_FOR_AZURE_TO_UPDATE_BILL_IN_MILLIS =
            NO_OF_DAYS_MARGIN_FOR_AZURE_TO_UPDATE_BILL * 60 * 60 * 24 * 1000;
    // By default we will collect the following number of months bills from Azure.
    public static final int DEFAULT_NO_OF_MONTHS_TO_GET_PAST_BILLS = 11;

    public static final int TWO_MINUTES_IN_MILLISECONDS = 120000;

    public static final int READ_TIMEOUT_FOR_REQUESTS = TWO_MINUTES_IN_MILLISECONDS;

    public static final int DOWNLOAD_CHUNK_SIZE = 2048; //Same as Okio Segment.SIZE

    // Maximum number of times to re-try a request on failure before aborting
    public static final int MAX_RETRIES_ON_REQUEST_FAILURE = 3;

    // Old API
    // Base URI
    private static final String OLD_EA_BASE_URI_FOR_REST = "https://ea.azure.com/rest/{enrollmentNumber}";
    // Usage report
    public static final String OLD_EA_USAGE_REPORT = OLD_EA_BASE_URI_FOR_REST + "/usage-report";
    // Query parameters
    // month for which bill is required (2016-01 for the bill month January, 2016)
    public static final String QUERY_PARAM_BILL_MONTH = "month";
    // Type of response expected (summary / detail)
    public static final String QUERY_PARAM_BILL_TYPE = "type";
    // Summarized response
    public static final String QUERY_PARAM_BILL_TYPE_VALUE_SUMMARY = "summary";
    // Detailed response
    public static final String QUERY_PARAM_BILL_TYPE_VALUE_DETAILED = "detail";
    // Format of output expected (Eg. csv / json)
    public static final String QUERY_PARAM_RESPONSE_FORMAT = "fmt";
    // Response in JSON format
    public static final String QUERY_PARAM_RESPONSE_FORMAT_VALUE_JSON = "json";
    // Response in CSV format
    public static final String QUERY_PARAM_RESPONSE_FORMAT_VALUE_CSV = "csv";
    // Bill stored in format- CSV
    public static final String BILL_FORMAT = ".csv";
    // API version header
    public static final String API_VERSION_HEADER = "api-version";
    public static final String API_VERSION_HEADER_VALUE = "1.0";

    // New API
    // Base URI
    private static final String EA_BASE_URI_FOR_REST = "https://consumption.azure.com/v1/enrollments/{enrollmentNumber}";
    // Billing periods path param
    public static final String PATH_PARAM_BILLING_PERIODS = "/billingperiods";
    // Available bills
    private static final String ALL_BILLING_PERIODS =
            EA_BASE_URI_FOR_REST + PATH_PARAM_BILLING_PERIODS;
    // Get bill for specified billing period
    public static final String PATH_PARAM_BILL_BY_BILLING_PERIOD_AND_TYPE = "/billingperiods/{billingPeriod}/{billType}";
    // Billing summary
    public static final String SUMMARIZED_BILL =
            ALL_BILLING_PERIODS + "/{billPeriod}" + "/balancesummary";
    // Detailed usage bill
    public static final String PATH_PARAM_USAGE_DETAILS_FOR_CURRENT_MONTH = "/usagedetails";
    // Usage details by specified start and end date
    public static final String PATH_PARAM_USAGE_DETAILS_FOR_BY_DATE = "/usagedetailsbycustomdate";
    // Detailed marketplace bill
    public static final String PATH_PARAM_MARKETPLACE_DETAILS_FOR_CURRENT_MONTH = "/marketplacecharges";
    // Marketplace details by specified start and end date
    public static final String PATH_PARAM_MARKETPLACE_DETAILS_BY_DATE = "/marketplacechargesbycustomdate";
    // Get details starting from date (misleading term: the query param refers to time, but it
    // expects the date in format YYYY-dd-MM
    public static final String QUERY_PARAM_START_TIME = "startTime";
    // Get details ending on date in format YYYY-dd-MM
    public static final String QUERY_PARAM_END_TIME = "endTime";

    // EA summarized bill fields
    public static final String SERVICE_COMMITMENT_REPORT_GENERATION_DATE = "Report Generation Date";
    public static final String SERVICE_COMMITMENT_REPORT_GENERATION_DATE_FORMAT = "MM/dd/yyyy";
    public static final String SERVICE_COMMITMENT_BEGINNING_BALANCE = "Beginning Balance";
    // This amount is paid by the customer but does not appear in the detailed bill. It is charged
    // separately.
    public static final String SERVICE_COMMITMENT_CHARGES_BILLED_SEPARATELY =
            "Charges Billed Separately";
    // This amount is paid by the customer and is the total amount that appears in the detailed bill.
    public static final String SERVICE_COMMITMENT_TOTAL_USAGE =
            "Total Usage (Commitment Utilized + Overage)";
    // This amount is paid by the customer but does not appear in the detailed bill. It is charged
    // separately.
    public static final String SERVICE_COMMITMENT_MARKETPLACE_SERVICE_CHARGES_BILLED_SEPARATELY =
            "Azure Marketplace Service Charges <br /> (Billed Separately)";

    // EA detailed bill
    public static final String DETAILED_CSV_BILL_NAME_MID = "-detailed-bill";
    public static final String SUMMARIZED_CSV_BILL_NAME_MID = "-summarized-bill";

    // CSV file constants
    public static final char DEFAULT_COLUMN_SEPARATOR = ',';
    public static final char DEFAULT_QUOTE_CHARACTER = '"';
    public static final char DEFAULT_ESCAPE_CHARACTER = '\\';
    public static final int DEFAULT_LINES_TO_SKIP = 3; // First two lines of the CSV bill is garbage.

    public static final String UNKNOWN_SERVICE_NAME = "UnknownService";
    public static final String UNKNOWN_SUBSCRIPTION = "UnknownSubscription";

    // Ignored bill values
    // "Null" as values are ignored tags
    public static final String BILL_ELEMENT_FIELD_VALUE_NULL = "null";

    // Number of operations to send in a batch when using OperationJoin
    public static final int OPERATION_BATCH_SIZE = 50;
    public static final String EA_ACCOUNT_USAGE_KEY_EXPIRY_TIME_MILLIS = "UsageKeyExpiryTimeMillis";
    // Custom property key to store the GUID of the subscription
    public static final String SUBSCRIPTION_GUID = "subscriptionId";
    // Custom property key to store and get the subscriptions that belong to a particular EA
    // account. The key is "linkedAccountIds" since this key was initially created by AWS and
    // is being reused in Azure for code-reuse in analysis.
    public static final String LINKED_SUBSCRIPTION_GUIDS = "linkedAccountIds";

    public static final String AZURE_BILL_FIELD_DATE_DEFAULT_DELIMITER = "/";
    public static final String TIMESTAMP_FORMAT_WITH_DATE_FORMAT_MM_DD_YYYY_WITH_OBLIQUE_DELIMITER =
            "MM/dd/yyyy";
    public static final String TIMESTAMP_FORMAT_WITH_DATE_FORMAT_YYYY_HYPHEN_MM = "yyyy-MM";
    public static final String TIMESTAMP_FORMAT_WITH_DATE_FORMAT_YYYY_MM = "yyyyMM";

    public static final String ERROR_RESPONSE_MESSAGE_SERVICE_UNAVAILABLE = "error 503 for GET";

    // For stats normalization
    public static final String COST = "Cost";
    public static final String USAGE_COST = "UsageCost";
    public static final String MARKETPLACE_COST = "MarketplaceCost";
    public static final String SEPARATELY_BILLED_COST = "SeparatelyBilledCost";
    public static final String SERVICE_RESOURCE_COST = "Service.%s.ResourceCost";

    // Properties file storing exchange rates
    private static final String EXCHANGE_RATES_JSON_FILE_NAME = "exchangeRates.json";

    // Usage API key
    public static final String USAGE_API_KEY_JSON_EXPIRES_KEY = "exp";

    public static final long THRESHOLD_FOR_TIME_IN_SECONDS = 9999999999L;

    public static Map<String, Double> exchangeRates = new HashMap<>();

    static {
        Operation readFile = Operation.createGet(null).setCompletion((op, ex) -> {
                    if (ex != null) {
                        logger.warning("Unable to get exchange rates. "
                                + "Using default ones; this may be old.");
                        setDefaultExchangeRates();
                        return;
                    }
                    ExchangeRates exRates = op.getBody(ExchangeRates.class);
                    exchangeRates = exRates.rates;
                }
        );

        try {
            URL resource = AzureCostConstants.class
                    .getResource(EXCHANGE_RATES_JSON_FILE_NAME);
            String fileUri = "";
            if (resource != null) {
                fileUri = resource.getFile();
            }
            File jsonPayloadFile = new File(fileUri);
            FileUtils.readFileAndComplete(readFile, jsonPayloadFile);
        } catch (Exception e) {
            setDefaultExchangeRates();
        }
    }

    /**
     * This map will store currency values with respect to USD. Last updated on 20 April 2017.
     * Obtained from: https://openexchangerates.org/api/latest.json
     */
    private static void setDefaultExchangeRates() {
        exchangeRates = ImmutableMap.<String, Double>builder()
                .put("AED", 3.673009)
                .put("AFN", 67.750957)
                .put("ALL", 126.23511)
                .put("AMD", 485.115379)
                .put("ANG", 1.78185)
                .put("AOA", 165.9115)
                .put("ARS", 15.384)
                .put("AUD", 1.332027)
                .put("AWG", 1.800506)
                .put("AZN", 1.6775)
                .put("BAM", 1.825399)
                .put("BBD", 2d)
                .put("BDT", 82.038)
                .put("BGN", 1.82576)
                .put("BHD", 0.376939)
                .put("BIF", 1695.75)
                .put("BMD", 1d)
                .put("BND", 1.398915)
                .put("BOB", 6.937439)
                .put("BRL", 3.150102)
                .put("BSD", 1d)
                .put("BTC", 0.000812291596)
                .put("BTN", 64.597233)
                .put("BWP", 10.460621)
                .put("BYN", 1.880494)
                .put("BYR", 20026.25)
                .put("BZD", 2.012101)
                .put("CAD", 1.347757)
                .put("CDF", 1392.5)
                .put("CHF", 0.997165)
                .put("CLF", 0.024182)
                .put("CLP", 648.1)
                .put("CNH", 6.882256)
                .put("CNY", 6.886)
                .put("COP", 2869.27)
                .put("CRC", 557.59)
                .put("CUC", 1d)
                .put("CUP", 24.728383)
                .put("CVE", 103.25)
                .put("CZK", 25.071397)
                .put("DJF", 178.72)
                .put("DKK", 6.93492)
                .put("DOP", 47.269988)
                .put("DZD", 109.945717)
                .put("EGP", 18.162)
                .put("ERN", 15.345906)
                .put("ETB", 22.904258)
                .put("EUR", 0.932238)
                .put("FJD", 2.076448)
                .put("FKP", 0.780742)
                .put("GBP", 0.780742)
                .put("GEL", 2.39252)
                .put("GGP", 0.780742)
                .put("GHS", 4.1493)
                .put("GIP", 0.780742)
                .put("GMD", 45d)
                .put("GNF", 9324.8)
                .put("GTQ", 7.3428)
                .put("GYD", 208.115)
                .put("HKD", 7.77495)
                .put("HNL", 23.467244)
                .put("HRK", 6.942869)
                .put("HTG", 68.353)
                .put("HUF", 292.0915)
                .put("IDR", 13325.81875)
                .put("ILS", 3.667985)
                .put("IMP", 0.780742)
                .put("INR", 64.6355)
                .put("IQD", 1166.309957)
                .put("IRR", 32450.748605)
                .put("ISK", 110.06)
                .put("JEP", 0.780742)
                .put("JMD", 129.19)
                .put("JOD", 0.709503)
                .put("JPY", 108.89553226)
                .put("KES", 103.477524)
                .put("KGS", 67.787751)
                .put("KHR", 4016.2)
                .put("KMF", 459.7)
                .put("KPW", 899.91)
                .put("KRW", 1139.55)
                .put("KWD", 0.30445)
                .put("KYD", 0.834194)
                .put("KZT", 311.7)
                .put("LAK", 8213.65)
                .put("LBP", 1508.1)
                .put("LKR", 152.41)
                .put("LRD", 94.497383)
                .put("LSL", 13.323931)
                .put("LYD", 1.417788)
                .put("MAD", 10.0183)
                .put("MDL", 19.315)
                .put("MGA", 3203.4)
                .put("MKD", 57.4055)
                .put("MMK", 1354.45)
                .put("MNT", 2411.277802)
                .put("MOP", 8.016996)
                .put("MRO", 360.225)
                .put("MUR", 35.280922)
                .put("MVR", 15.400126)
                .put("MWK", 726.28)
                .put("MXN", 18.878042)
                .put("MYR", 4.400441)
                .put("MZN", 65.999581)
                .put("NAD", 13.323931)
                .put("NGN", 315.59)
                .put("NIO", 29.611052)
                .put("NOK", 8.581574)
                .put("NPR", 103.415)
                .put("NZD", 1.420105)
                .put("OMR", 0.38501)
                .put("PAB", 1d)
                .put("PEN", 3.251659)
                .put("PGK", 3.178149)
                .put("PHP", 49.788)
                .put("PKR", 104.918899)
                .put("PLN", 3.968197)
                .put("PYG", 5533.35)
                .put("QAR", 3.641133)
                .put("RON", 4.232805)
                .put("RSD", 115.215)
                .put("RUB", 56.5179)
                .put("RWF", 840.47)
                .put("SAR", 3.75033)
                .put("SBD", 7.872287)
                .put("SCR", 13.483)
                .put("SDG", 6.683751)
                .put("SEK", 8.965119)
                .put("SGD", 1.397055)
                .put("SHP", 0.780742)
                .put("SLL", 7476.027571)
                .put("SOS", 578.5)
                .put("SRD", 7.547)
                .put("SSP", 115.94315)
                .put("STD", 22849.954125)
                .put("SVC", 8.759112)
                .put("SYP", 214.346667)
                .put("SZL", 13.343966)
                .put("THB", 34.35675)
                .put("TJS", 8.879387)
                .put("TMT", 3.50998)
                .put("TND", 2.314592)
                .put("TOP", 2.292086)
                .put("TRY", 3.669183)
                .put("TTD", 6.737121)
                .put("TWD", 30.4225)
                .put("TZS", 2235.2)
                .put("UAH", 26.816052)
                .put("UGX", 3615.8)
                .put("USD", 1d)
                .put("UYU", 28.532734)
                .put("UZS", 3681.95)
                .put("VEF", 10.111774)
                .put("VND", 22690.703228)
                .put("VUV", 108.179017)
                .put("WST", 2.573423)
                .put("XAF", 611.919472)
                .put("XAG", 0.0550161)
                .put("XAU", 0.00078151)
                .put("XCD", 2.70255)
                .put("XDR", 0.732683)
                .put("XOF", 613.497623)
                .put("XPD", 0.00128286)
                .put("XPF", 111.616697)
                .put("XPT", 0.00103352)
                .put("YER", 250.25)
                .put("ZAR", 13.270867)
                .put("ZMK", 5252.024745)
                .put("ZMW", 9.409865)
                .put("ZWL", 322.322775)
                .build();
    }
}
