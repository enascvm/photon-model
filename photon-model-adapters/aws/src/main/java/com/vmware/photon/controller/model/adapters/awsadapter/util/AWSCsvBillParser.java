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

package com.vmware.photon.controller.model.adapters.awsadapter.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.supercsv.cellprocessor.CellProcessorAdaptor;
import org.supercsv.cellprocessor.Optional;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.comment.CommentStartsWith;
import org.supercsv.exception.SuperCsvCellProcessorException;
import org.supercsv.io.CsvMapReader;
import org.supercsv.io.ICsvMapReader;
import org.supercsv.prefs.CsvPreference;
import org.supercsv.util.CsvContext;

import com.vmware.photon.controller.model.adapters.aws.dto.AwsAccountDetailDto;
import com.vmware.photon.controller.model.adapters.aws.dto.AwsResourceDetailDto;
import com.vmware.photon.controller.model.adapters.aws.dto.AwsServiceDetailDto;

public class AWSCsvBillParser {

    public static final String AWS_BILL_CSV_FILE_NAME_MID = "-aws-cost-allocation-";
    public static final String AWS_DETAILED_BILL_CSV_FILE_NAME_MID = "-aws-billing-detailed-line-items-with-resources-and-tags-";
    public static final String AWS_BILL_CSV_FILE_NAME_SUFFIX = ".csv";
    public static final String AWS_BILL_ZIP_FILE_NAME_SUFFIX = ".zip";
    public static final String AWS_SKIP_COMMENTS = "Don't see your tags in the report";
    public static final String RUN_INSTANCES = "RunInstances";
    public static final String SIGN_UP_CHARGE = "Sign up charge";
    public static final String TAG_KEY_DELIMITTER = ":";
    public static final String DETAILED_CSV_DATE_FORMAT_YYYY_MM_DD_HH_MM_SS = "yyyy-MM-dd HH:mm:ss";
    public static final String INVOICE_TOTAL = "InvoiceTotal";
    public static final String ACCOUNT_TOTAL = "AccountTotal";

    public Map<String, AwsAccountDetailDto> parseDetailedCsvBill(
            List<String> ignorableInvoiceCharge, Path csvBillZipFilePath) throws IOException {

        Path workingDirPath = csvBillZipFilePath.getParent();
        unzip(csvBillZipFilePath.toString(), workingDirPath.toString());
        String unzippedCsvFilePathStr = csvBillZipFilePath.toString()
                .substring(0, csvBillZipFilePath.toString().lastIndexOf('.'));
        Path unzippedCsvFilePath = Paths.get(unzippedCsvFilePathStr);

        Map<String, AwsAccountDetailDto> accountToDetailsMap;
        try (InputStream extractedObjectContentInputStream = new FileInputStream(
                unzippedCsvFilePath.toFile())) {
            accountToDetailsMap = parseDetailedCsvBill(extractedObjectContentInputStream,
                    ignorableInvoiceCharge);
        } finally {
            Files.deleteIfExists(unzippedCsvFilePath);
        }

        return accountToDetailsMap;
    }

    private Map<String, AwsAccountDetailDto> parseDetailedCsvBill(InputStream inputStream,
            Collection<String> ignorableInvoiceCharge)
            throws IOException {
        final CsvPreference STANDARD_SKIP_COMMENTS = new CsvPreference.Builder(
                CsvPreference.STANDARD_PREFERENCE)
                    .skipComments(new CommentStartsWith(AWS_SKIP_COMMENTS))
                    .build();

        try (InputStreamReader reader = new InputStreamReader(inputStream, "UTF-8");
                ICsvMapReader mapReader = new CsvMapReader(reader, STANDARD_SKIP_COMMENTS)) {
            final String[] header = mapReader.getHeader(true);

            List<CellProcessor> processorList = new ArrayList<>();
            final CellProcessor[] basicProcessors = getDetailedProcessors(header);

            processorList.addAll(Arrays.asList(basicProcessors));
            List<String> tagHeaders = new ArrayList<>();

            // Add new cell-processors for each extra tag column
            int numberOfTags = header.length - basicProcessors.length;
            if (numberOfTags > 0) {
                for (int i = 0; i < numberOfTags; i++) {
                    processorList.add(new Optional());
                    tagHeaders.add(header[basicProcessors.length + i]);
                }
            }
            CellProcessor[] cellProcessorArray = new CellProcessor[processorList.size()];

            Map<String, AwsAccountDetailDto> monthlyBill = new HashMap<>();
            cellProcessorArray = processorList.toArray(cellProcessorArray);
            Map<String, Object> rowMap;
            while ((rowMap = mapReader.read(header, cellProcessorArray)) != null) {
                readRow(rowMap, monthlyBill, tagHeaders, ignorableInvoiceCharge);
            }
            // Subtract the sign up charge from the account cost
            for (AwsAccountDetailDto awsAccountDetail : monthlyBill.values()) {
                Double signUpCharge = awsAccountDetail.signUpCharge;
                if (signUpCharge != 0) {
                    awsAccountDetail.cost = awsAccountDetail.cost - signUpCharge;
                }
            }

            return monthlyBill;
        }
    }

    public String getCsvBillFileName(LocalDate date, String accountId, boolean isZipFile) {
        StringBuilder monthStrBuffer = new StringBuilder();
        int month = date.getMonthOfYear();
        int year = date.getYear();
        if (month <= 9) {
            monthStrBuffer.append('0');
        }
        monthStrBuffer.append(month);
        String awsBillFileName;
        if (isZipFile) {
            awsBillFileName = accountId + AWS_DETAILED_BILL_CSV_FILE_NAME_MID + year
                    + "-"
                    + monthStrBuffer + AWS_BILL_CSV_FILE_NAME_SUFFIX
                    + AWS_BILL_ZIP_FILE_NAME_SUFFIX;
        } else {
            awsBillFileName = accountId + AWS_BILL_CSV_FILE_NAME_MID + year + "-"
                    + monthStrBuffer
                    + AWS_BILL_CSV_FILE_NAME_SUFFIX;
        }
        return awsBillFileName;
    }

    private void unzip(String zipFileName, String outputFolder) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(zipFileName))) {
            ZipEntry zipEntry = zipInputStream.getNextEntry();
            String outputFileName = zipEntry.getName();
            File outputFile = new File(outputFolder, outputFileName);

            try (FileOutputStream fileOutputStream = new FileOutputStream(outputFile)) {
                int len;
                byte[] buffer = new byte[1024 * 64];
                while ((len = zipInputStream.read(buffer)) > 0) {
                    fileOutputStream.write(buffer, 0, len);
                }
                zipInputStream.closeEntry();
            }
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * This method reads each row of the AWS bill file, ignores the values that
     * are not required, creates or updates the corresponding entry in
     * monthlyBill Map.
     **/
    private void readRow(Map<String, Object> rowMap, Map<String, AwsAccountDetailDto> monthlyBill,
            List<String> tagHeaders, Collection<String> ignorableInvoiceCharge) {

        final String linkedAccountId = getStringFieldValue(rowMap,
                DetailedCsvHeaders.LINKED_ACCOUNT_ID);
        String serviceName = getStringFieldValue(rowMap, DetailedCsvHeaders.PRODUCT_NAME);
        String subscriptionId = getStringFieldValue(rowMap, DetailedCsvHeaders.SUBSCRIPTION_ID);

        // For all rows except summary rows this is not null.
        if (subscriptionId == null || subscriptionId.length() == 0 || serviceName == null
                || serviceName.length() == 0) {
            // Reads the summary lines in bill file, which consists of the
            // account cost and puts it in the monthly bill map
            readSummaryRow(rowMap, linkedAccountId, serviceName, monthlyBill,
                    ignorableInvoiceCharge);
            return;
        }
        AwsAccountDetailDto accountDetails = createOrGetAccountDetailObject(monthlyBill,
                linkedAccountId);
        AwsServiceDetailDto serviceDetail = createOrGetServiceDetailObject(accountDetails,
                serviceName);
        // Get the UsageStartTime for this lineItem.
        // LocalDateTime usageStartTimeFromCsv = (LocalDateTime)
        // rowMap.get(DetailedCsvHeaders.USAGE_START_DATE);

        Double resourceCost = getResourceCost(rowMap);
        // In case we do not have resource id, this might be unknown
        // cost(unallocated) or one of summary line items {otherCost
        // (recurring charges for reserved instance) or sign up charges(which we
        // have to ignore)}
        String resourceId = getStringFieldValue(rowMap, DetailedCsvHeaders.RESOURCE_ID);
        if (resourceId == null || resourceId.length() == 0) {
            // Check if this row has usageStartTime, if so set otherCost for
            // day, otherwise set it as common for month, can divide later for
            // all days
            if (rowMap.get(DetailedCsvHeaders.USAGE_START_DATE) != null) {
                LocalDateTime usageStartTimeFromCsv = (LocalDateTime) rowMap
                        .get(DetailedCsvHeaders.USAGE_START_DATE);
                Long millisForBillHour = getMillisForHour(usageStartTimeFromCsv);
                serviceDetail.addToOtherCosts(millisForBillHour, resourceCost);
                // Adding zero as direct cost for this entity to allow
                // populating this as a resource while getting services- refer
                // AwsInventoryServiceImpl#getAwsServicesCost()
                serviceDetail.addToDirectCosts(millisForBillHour, 0d);
                setBillProcessedTime(accountDetails, millisForBillHour);
            } else {
                serviceDetail.addToRemainingCost(resourceCost);
            }
            return;
        }

        LocalDateTime usageStartTimeFromCsv = (LocalDateTime) rowMap
                .get(DetailedCsvHeaders.USAGE_START_DATE);
        Long millisForBillHour = getMillisForHour(usageStartTimeFromCsv);
        serviceDetail.addToDirectCosts(millisForBillHour, resourceCost);

        DateTime firstDayOfCurrentMonth = LocalDate.now(DateTimeZone.UTC).withDayOfMonth(1).toDateTimeAtStartOfDay(DateTimeZone.UTC);
        if (firstDayOfCurrentMonth.toLocalDateTime().isBefore(usageStartTimeFromCsv)) {
            // Populate resource stats only for current month since resource stats are not needed
            // for previous months.
            AwsResourceDetailDto resourceDetail = createOrGetResourceDetailObject(rowMap,
                    serviceDetail,
                    resourceId);
            resourceDetail.addToDirectCosts(millisForBillHour, resourceCost);
            setLatestResourceValues(rowMap, tagHeaders, resourceDetail);
        }
        setBillProcessedTime(accountDetails, millisForBillHour);
    }

    /**
     * Set billProcessedTimeMillis for linked accounts as well as primary account.
     */
    private void setBillProcessedTime(AwsAccountDetailDto accountDetails,
            Long millisForBillHour) {
        if (millisForBillHour > accountDetails.billProcessedTimeMillis) {
            // TODO gjobin: Re-align the bill processed time once the aggregation window moves
            // to start time instead of the current end-time
            // Need to subtract a small amount of time from the bill processed time
            // since this time is being incremented by an hour when stats are aggregated
            // leading to the cost becoming a metric of the next month (since
            // the billProcessedTime for the past months is the last hour of the last day
            // of the month.
            accountDetails.billProcessedTimeMillis =
                    millisForBillHour - TimeUnit.SECONDS.toMillis(1);
        }
    }

    private Long getMillisForHour(LocalDateTime usageStartTime) {
        return usageStartTime.toDateTime(DateTimeZone.UTC).getMillis();
    }

    private Double getResourceCost(Map<String, Object> rowMap) {
        Double resourceCost = 0d;
        if (rowMap.containsKey(DetailedCsvHeaders.BLENDED_COST)) {
            resourceCost = getAmountFieldValue(rowMap, DetailedCsvHeaders.BLENDED_COST);
        } else if (rowMap.containsKey(DetailedCsvHeaders.COST)) {
            resourceCost = getAmountFieldValue(rowMap, DetailedCsvHeaders.COST);
        }
        return resourceCost;
    }

    private void setLatestResourceValues(Map<String, Object> rowMap, List<String> tagHeaders,
            AwsResourceDetailDto resourceDetail) {
        LocalDateTime usageStartTimeFromCsv = (LocalDateTime) rowMap
                .get(DetailedCsvHeaders.USAGE_START_DATE);
        LocalDateTime existingUsageStartTime = null;
        if (resourceDetail.usageStartTime != null) {
            existingUsageStartTime = new LocalDateTime(resourceDetail.usageStartTime);
        }
        if (existingUsageStartTime == null
                || existingUsageStartTime.compareTo(usageStartTimeFromCsv) <= 0) {
            resourceDetail.itemDescription = getStringFieldValue(rowMap,
                    DetailedCsvHeaders.ITEM_DESCRIPTION);
            resourceDetail.usageStartTime = usageStartTimeFromCsv.toDate().getTime();
            resourceDetail.tags = getTagsForResources(rowMap, tagHeaders);
            boolean isRowMarkedAsReserved = convertReservedInstance(
                    getStringFieldValue(rowMap, DetailedCsvHeaders.IS_RESERVED_INSTANCE));
            boolean isResourceReservedForThisHour;
            Long millisForBillDay = getMillisForHour(usageStartTimeFromCsv);
            if (existingUsageStartTime != null
                    && existingUsageStartTime.isEqual(usageStartTimeFromCsv)) {
                isResourceReservedForThisHour = resourceDetail.isReservedInstance
                        || isRowMarkedAsReserved;
                if (isRowMarkedAsReserved && !resourceDetail.isReservedInstance) {
                    resourceDetail.addToHoursAsReservedPerDay(millisForBillDay, 1.0);
                }
            } else {
                isResourceReservedForThisHour = isRowMarkedAsReserved;
                if (isResourceReservedForThisHour) {
                    resourceDetail.addToHoursAsReservedPerDay(millisForBillDay, 1.0);
                }
            }
            resourceDetail.isReservedInstance = isResourceReservedForThisHour;
        }
    }

    private AwsResourceDetailDto createOrGetResourceDetailObject(Map<String, Object> rowMap,
            AwsServiceDetailDto serviceDetail, String resourceId) {
        AwsResourceDetailDto resourceDetail = serviceDetail.getResourceDetail(resourceId);
        if (resourceDetail == null) {
            resourceDetail = new AwsResourceDetailDto();
            resourceDetail.availabilityZone = ((String) rowMap
                    .get(DetailedCsvHeaders.AVAILABILITY_ZONE));
            resourceDetail.type = "OTHERS";
            serviceDetail.addToResourceDetailMap(resourceId, resourceDetail);
        }
        return resourceDetail;
    }

    private String getStringFieldValue(Map<String, Object> rowMap, String fieldName) {
        Object obj = rowMap.get(fieldName);
        if (obj != null) {
            return obj.toString();
        }
        return "";
    }

    private Double getAmountFieldValue(Map<String, Object> rowMap, String fieldName) {
        Object obj = rowMap.get(fieldName);
        if (obj != null) {
            return Double.valueOf(obj.toString());
        }
        return 0d;
    }

    private boolean matchFieldValue(Map<String, Object> rowMap, String fieldName, String value) {
        Object fieldObj = rowMap.get(fieldName);
        if (fieldObj != null) {
            if (fieldObj.toString().equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private AwsServiceDetailDto createOrGetServiceDetailObject(AwsAccountDetailDto accountDetails,
            String serviceName) {
        AwsServiceDetailDto serviceDetail = accountDetails.fetchServiceDetail(serviceName);

        /*
         * If the service is not present, add the service and its details from
         * the bill
         */
        if (serviceDetail == null) {
            serviceDetail = new AwsServiceDetailDto();
            serviceDetail.id = serviceName;
            serviceDetail.type = AwsServices.getTypeByName(serviceName).toString();
            accountDetails.addToServiceDetailsMap(serviceName, serviceDetail);
        }
        return serviceDetail;
    }

    private AwsAccountDetailDto createOrGetAccountDetailObject(
            Map<String, AwsAccountDetailDto> monthlyBill,
            final String linkedAccountId) {
        AwsAccountDetailDto accountDetails = monthlyBill.get(linkedAccountId);
        if (accountDetails == null) {
            accountDetails = new AwsAccountDetailDto();
            accountDetails.id = linkedAccountId;
            monthlyBill.put(linkedAccountId, accountDetails);
        }
        return accountDetails;
    }

    private boolean convertReservedInstance(String isReserved) {
        switch (isReserved) {
        case "Y":
            return true;
        case "N":
            return false;
        default:
            return false;
        }
    }

    private Map<String, String> getTagsForResources(Map<String, Object> rowMap,
            List<String> tagHeaders) {
        Map<String, String> tagMappings = new HashMap<>();
        for (String tagKey : tagHeaders) {
            String tagValue = (String) rowMap.get(tagKey);
            if (tagValue != null) {
                // tags are always like [user | somethingelse]:tagFamily
                tagMappings.put(tagKey.split(TAG_KEY_DELIMITTER)[1], tagValue);
            }
        }
        return tagMappings;
    }

    private void readSummaryRow(Map<String, Object> rowMap, String linkedAccountId,
            String productName,
            Map<String, AwsAccountDetailDto> accountDetails,
            Collection<String> ignorableInvoiceCharge) {
        AwsAccountDetailDto awsAccountDetail;
        if (linkedAccountId == null || linkedAccountId.length() == 0) {
            // The AccountId is not obtained from LinkedAccountId in case of
            // non-consolidated bills and has to be fetched from PayerAccountId
            // column from the bill file
            awsAccountDetail = accountDetails.get(rowMap.get(DetailedCsvHeaders.PAYER_ACCOUNT_ID).toString());
        } else {
            awsAccountDetail = accountDetails.get(linkedAccountId);
        }
        if (matchFieldValue(rowMap, DetailedCsvHeaders.RECORD_TYPE, DetailedCsvHeaders.LINE_ITEM)) {
            // If the RecordType is LineItem then it is either sign up charge or
            // recurring charges for reserved purchase
            if (matchFieldValue(rowMap, DetailedCsvHeaders.OPERATION,
                    RUN_INSTANCES)) {
                // If the Operation is RunInstances, it is recurring charge
                AwsServiceDetailDto serviceDetail = createOrGetServiceDetailObject(
                        createOrGetAccountDetailObject(accountDetails, linkedAccountId),
                        productName);
                serviceDetail.addToReservedRecurringCost(getResourceCost(rowMap));
            } else if (getStringFieldValue(rowMap, DetailedCsvHeaders.ITEM_DESCRIPTION)
                    .startsWith(SIGN_UP_CHARGE)) {
                // Subtract the one-time subscription charge from the account
                // cost since the one-time subscription charge is divided among
                // the total cost of all months
                awsAccountDetail.signUpCharge = getResourceCost(rowMap);
                ignorableInvoiceCharge
                        .add(getStringFieldValue(rowMap, DetailedCsvHeaders.INVOICE_ID));
            }
        } else if (matchFieldValue(rowMap, DetailedCsvHeaders.RECORD_TYPE,
                INVOICE_TOTAL)
                || matchFieldValue(rowMap, DetailedCsvHeaders.RECORD_TYPE,
                        ACCOUNT_TOTAL)) {
            // If the RecordType is InvoiceTotal, this is the account monthly
            // cost for non-consolidated bills, ie, for primary accounts with no
            // linked accounts
            // If the RecordType is AccountTotal, this is the account monthly
            // cost for consolidated bills
            if (!ignorableInvoiceCharge
                    .contains(getStringFieldValue(rowMap, DetailedCsvHeaders.INVOICE_ID))) {
                awsAccountDetail.cost = getResourceCost(rowMap);
            }
        }
    }

    private static CellProcessor[] getDetailedProcessors(String[] header) {
        final CellProcessor[] PROCESSORS;
        if (headerContainsBlendedCost(header)) {
            PROCESSORS = new CellProcessor[] { new Optional(), new Optional(), new Optional(),
                    new Optional(),
                    new Optional(), new Optional(), new Optional(), new Optional(), new Optional(),
                    new Optional(),
                    new Optional(), new Optional(), new Optional(), new Optional(),
                    new Optional(new ParseLocalDateTimeInDetailedCsv()),
                    new Optional(new ParseLocalDateTimeInDetailedCsv()), new Optional(),
                    new Optional(), new Optional(),
                    new Optional(), new Optional(), new Optional() };
        } else {
            PROCESSORS = new CellProcessor[] { new Optional(), new Optional(), new Optional(),
                    new Optional(),
                    new Optional(), new Optional(), new Optional(), new Optional(), new Optional(),
                    new Optional(),
                    new Optional(), new Optional(), new Optional(), new Optional(),
                    new Optional(new ParseLocalDateTimeInDetailedCsv()),
                    new Optional(new ParseLocalDateTimeInDetailedCsv()), new Optional(),
                    new Optional(), new Optional(),
                    new Optional() };
        }

        return PROCESSORS;
    }

    private static boolean headerContainsBlendedCost(String[] header) {
        if (header == null) {
            return false;
        }
        if (header.getClass().getComponentType().isInstance(DetailedCsvHeaders.BLENDED_COST)) {
            for (int i = 0; i < header.length; i++) {
                if (DetailedCsvHeaders.BLENDED_COST.equals(header[i])) {
                    return true;
                }
            }
        }
        return false;
    }

    private static class ParseLocalDateTimeInDetailedCsv extends CellProcessorAdaptor {

        private ParseLocalDateTimeInDetailedCsv() {
            super();
        }

        @Override
        public Object execute(Object value, CsvContext context) {

            validateInputNotNull(value, context);
            try {
                DateTimeFormatter pattern = DateTimeFormat
                        .forPattern(DETAILED_CSV_DATE_FORMAT_YYYY_MM_DD_HH_MM_SS);
                LocalDateTime localDate = new LocalDateTime(
                        pattern.parseLocalDateTime(value.toString()));

                return next.execute(localDate, context);
            } catch (IllegalArgumentException e) {
                throw new SuperCsvCellProcessorException(
                        String.format("Could not parse '%s' as a LocalDateTime", value), context,
                        this);
            }
        }
    }

    private static class DetailedCsvHeaders {
        static final String COST = "Cost";
        static final String PAYER_ACCOUNT_ID = "PayerAccountId";
        static final String LINKED_ACCOUNT_ID = "LinkedAccountId";
        static final String BLENDED_COST = "BlendedCost";
        static final String USAGE_START_DATE = "UsageStartDate";
        static final String PRODUCT_NAME = "ProductName";
        static final String RESOURCE_ID = "ResourceId";
        static final String AVAILABILITY_ZONE = "AvailabilityZone";
        static final String IS_RESERVED_INSTANCE = "ReservedInstance";
        static final String ITEM_DESCRIPTION = "ItemDescription";
        static final String RECORD_TYPE = "RecordType";
        static final String LINE_ITEM = "LineItem";
        static final String SUBSCRIPTION_ID = "SubscriptionId";
        static final String OPERATION = "Operation";
        static final String INVOICE_ID = "InvoiceID";
    }

    public enum AwsServices {
        ec2("Amazon Elastic Compute Cloud", PublicCloudServiceType.COMPUTE),

        rds("Amazon RDS Service", PublicCloudServiceType.DATABASE),

        sns("Amazon Simple Notification Service", PublicCloudServiceType.OTHERS),

        s3("Amazon Simple Storage Service", PublicCloudServiceType.STORAGE);

        private final String name;
        private final PublicCloudServiceType type;

        AwsServices(String name, PublicCloudServiceType type) {
            this.name = name;
            this.type = type;
        }

        public String getName() {
            return this.name;
        }

        public PublicCloudServiceType getType() {
            return this.type;
        }

        public static AwsServices getByName(String name) {
            name = name.replaceAll(" ", "");
            for (AwsServices service : AwsServices.values()) {
                String serviceName = service.getName().replaceAll(" ", "");
                if (serviceName.contains(name)) {
                    return service;
                }
            }
            return null;
        }

        public static PublicCloudServiceType getTypeByName(String name) {
            AwsServices service = getByName(name);
            if (service != null) {
                return service.getType();
            }
            return PublicCloudServiceType.OTHERS;
        }
    }

    private enum PublicCloudServiceType {
        COMPUTE, STORAGE, DATABASE, OTHERS;

        public String toString() {
            return name();
        }
    }
}
