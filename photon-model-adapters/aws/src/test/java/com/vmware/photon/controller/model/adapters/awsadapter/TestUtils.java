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

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.DEFAULT_ALLOWED_NETWORK;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.DEFAULT_PROTOCOL;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.zoneId;
import static com.vmware.photon.controller.model.tasks.QueryUtils.QueryTemplate.waitToComplete;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.comment.CommentStartsWith;
import org.supercsv.io.CsvMapReader;
import org.supercsv.io.CsvMapWriter;
import org.supercsv.io.ICsvMapReader;
import org.supercsv.io.ICsvMapWriter;
import org.supercsv.prefs.CsvPreference;

import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSCsvBillParser;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.SecurityGroupService;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState.Rule;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.tasks.QueryUtils;
import com.vmware.photon.controller.model.tasks.QueryUtils.QueryByPages;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.TenantService;

public class TestUtils {

    public static AmazonEC2AsyncClient getClient(String privateKeyId, String privateKey,
            String region, boolean isMockRequest) {
        AuthCredentialsServiceState creds = new AuthCredentialsServiceState();
        creds.privateKey = privateKey;
        creds.privateKeyId = privateKeyId;
        return AWSUtils.getAsyncClient(creds, region, getExecutor());
    }

    // validate that the passed items are not null
    public static boolean isNull(String... options) {
        for (String option : options) {
            if (option == null) {
                return false;
            }
        }
        return true;
    }

    public static ArrayList<Rule> getAllowIngressRules() {
        ArrayList<Rule> rules = new ArrayList<>();

        Rule ssh = new Rule();
        ssh.protocol = DEFAULT_PROTOCOL;
        ssh.ipRangeCidr = DEFAULT_ALLOWED_NETWORK;
        ssh.ports = "22";
        rules.add(ssh);

        Rule http = new Rule();
        http.protocol = DEFAULT_PROTOCOL;
        http.ipRangeCidr = DEFAULT_ALLOWED_NETWORK;
        http.ports = "80";
        rules.add(http);

        Rule range = new Rule();
        range.protocol = DEFAULT_PROTOCOL;
        range.ipRangeCidr = DEFAULT_ALLOWED_NETWORK;
        range.ports = "41000-42000";
        rules.add(range);

        return rules;
    }

    public static ArrayList<Rule> getAllowEgressRules(String subnet) {
        ArrayList<Rule> rules = new ArrayList<>();

        Rule out = new Rule();
        out.protocol = DEFAULT_PROTOCOL;
        out.ipRangeCidr = subnet;
        out.ports = "1-65535";
        rules.add(out);

        return rules;
    }

    public static void postSecurityGroup(VerificationHost host, SecurityGroupState state, Operation
            response)
            throws Throwable {
        URI firewallFactory = UriUtils.buildUri(host, SecurityGroupService.FACTORY_LINK);
        host.testStart(1);
        Operation startPost = Operation.createPost(firewallFactory)
                .setBody(state)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                        return;
                    }
                    response.setBody(o.getBody(SecurityGroupState.class));
                    host.completeIteration();
                });
        host.send(startPost);
        host.testWait();
    }


    public static void postNetwork(VerificationHost host, NetworkState state, Operation response)
            throws Throwable {
        URI networkFactory = UriUtils.buildUri(host, NetworkService.FACTORY_LINK);
        host.testStart(1);
        Operation startPost = Operation.createPost(networkFactory)
                .setBody(state)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                        return;
                    }
                    response.setBody(o.getBody(NetworkState.class));
                    host.completeIteration();
                });
        host.send(startPost);
        host.testWait();
    }

    public static NetworkState buildNetworkState(VerificationHost host) {
        URI tenantFactoryURI = UriUtils.buildFactoryUri(host, TenantService.class);

        NetworkState network = new NetworkState();
        network.regionId = zoneId;
        network.id = UUID.randomUUID().toString();
        network.subnetCIDR = "10.1.0.0/16";
        network.tenantLinks = new ArrayList<>();
        network.tenantLinks.add(UriUtils.buildUriPath(tenantFactoryURI.getPath(), "tenantA"));
        return network;
    }

    public static void postCredentials(VerificationHost host, Operation response, String privateKey, String privateKeyId) throws Throwable {
        AuthCredentialsServiceState creds = new AuthCredentialsServiceState();
        creds.privateKey = privateKey;
        creds.privateKeyId = privateKeyId;

        URI authFactory = UriUtils.buildFactoryUri(host, AuthCredentialsService.class);

        host.testStart(1);
        Operation startPost = Operation.createPost(authFactory)
                .setBody(creds)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                        return;
                    }
                    response.setBody(o.getBody(AuthCredentialsServiceState.class));
                    host.completeIteration();
                });
        host.send(startPost);
        host.testWait();

    }

    public static void postResourcePool(VerificationHost host,Operation response) throws Throwable {
        URI poolFactory = UriUtils.buildUri(host, ResourcePoolService.FACTORY_LINK);
        ResourcePoolState pool = new ResourcePoolState();
        pool.name = "test-aws";
        host.testStart(1);
        Operation startPost = Operation.createPost(poolFactory)
                .setBody(pool)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                        return;
                    }
                    response.setBody(o.getBody(ResourcePoolState.class));
                    host.completeIteration();
                });
        host.send(startPost);
        host.testWait();

    }

    public static void getNetworkState(VerificationHost host, String networkLink,Operation response) throws Throwable {

        host.testStart(1);
        URI networkURI = UriUtils.buildUri(host,networkLink);
        Operation startGet = Operation.createGet(networkURI)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                        return;
                    }
                    response.setBody(o.getBody(NetworkState.class));
                    host.completeIteration();
                });
        host.send(startGet);
        host.testWait();

    }

    /**
     * Get all SubnetStates within passed NetworkState. In other words, get all subnet states that
     * refer the network state passed.
     */
    public static List<SubnetState> getSubnetStates(
            VerificationHost host,
            NetworkState networkState) throws Throwable {


        Query queryForReferrers = QueryUtils.queryForReferrers(
                networkState.documentSelfLink,
                SubnetState.class,
                SubnetState.FIELD_NAME_NETWORK_LINK);

        QueryByPages<SubnetState> querySubnetStatesReferrers = new QueryByPages<>(
                host,
                queryForReferrers,
                SubnetState.class,
                networkState.tenantLinks,
                networkState.endpointLink);

        DeferredResult<List<SubnetState>> subnetDR =
                querySubnetStatesReferrers.collectDocuments(Collectors.toList());

        return waitToComplete(subnetDR);
    }

    public static ExecutorService getExecutor() {
        return Executors.newFixedThreadPool(Utils.DEFAULT_THREAD_COUNT,
                r -> new Thread(r, "test/" + Utils.getNowMicrosUtc()));
    }

    /**
     * Get the existing bill and update the required fields and create a new bill for current month.
     * @throws Throwable all exceptions are thrown back to caller.
     */
    public static void generateCurrentMonthsBill() throws Throwable {
        Path zipFilePath = com.vmware.photon.controller.model.tasks.TestUtils.getTestResourcePath(TestAWSCostAdapterService.class,
                TestAWSSetupUtils.SAMPLE_AWS_BILL);
        List<Map<String, Object>> rows = extractAndParseCsvFile(zipFilePath);
        updateFields(rows);
        Path newZipFilePath = updateZipFilePathWithCurrentMonth(zipFilePath);
        createCompressedCsv(rows, newZipFilePath);
        String newCsvFile = newZipFilePath.toString().substring(0, newZipFilePath.toString().lastIndexOf(AWSCsvBillParser.AWS_BILL_ZIP_FILE_NAME_SUFFIX));
        deleteFile(Paths.get(newCsvFile));
    }

    private static void deleteFile(Path filePath) throws IOException {
        Files.deleteIfExists(filePath);
    }

    private static void createCompressedCsv(List<Map<String, Object>> rows, Path newCsvZipFilePath) throws Exception {
        writeCsvToFile(rows, newCsvZipFilePath);
        Path csvFilePath = getCsvFilePathFromZipFilePath(newCsvZipFilePath);
        compressFileWithZip(csvFilePath);
    }

    private static Path getCsvFilePathFromZipFilePath(Path filePath) {
        return Paths.get(filePath.toString()
                .substring(0, filePath.toString().lastIndexOf('.')));
    }

    private static void compressFileWithZip(Path filePath) throws Exception {
        String csvFile = filePath.toString().substring(filePath.toString().lastIndexOf(FileSystems.getDefault().getSeparator()) + 1);

        FileOutputStream fileOutStream = new FileOutputStream(filePath + ".zip");
        ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutStream);
        ZipEntry zipEntry = new ZipEntry(csvFile);
        zipOutputStream.putNextEntry(zipEntry);
        FileInputStream fileInputStream = new FileInputStream(filePath.toString());

        int len;
        byte[] buffer = new byte[1024];
        while ((len = fileInputStream.read(buffer)) > 0) {
            zipOutputStream.write(buffer, 0, len);
        }

        fileInputStream.close();
        zipOutputStream.closeEntry();
        zipOutputStream.close();
    }

    private static Path updateZipFilePathWithCurrentMonth(Path zipFilePath) {
        int dateBeginIndex = zipFilePath.toString().indexOf(
                AWSCsvBillParser.AWS_DETAILED_BILL_CSV_FILE_NAME_MID) +
                AWSCsvBillParser.AWS_DETAILED_BILL_CSV_FILE_NAME_MID.length();
        int dateEndIndex = zipFilePath.toString().indexOf(AWSCsvBillParser.AWS_BILL_CSV_FILE_NAME_SUFFIX);
        String oldDate = zipFilePath.toString().substring(dateBeginIndex, dateEndIndex);
        LocalDateTime dateToday = getDateTimeToday();
        String newDate = getDateForBillName(dateToday);
        return Paths.get(zipFilePath.toString().replace(oldDate, newDate));
    }

    public static LocalDateTime getDateTimeToday() {
        return LocalDateTime.now(DateTimeZone.UTC);
    }

    public static String getDateForBillName(LocalDateTime date) {
        String monthString = String.valueOf(date.getMonthOfYear());

        if (monthString.length() == 1) {
            monthString = "0" + monthString;
        }

        return date.getYear() + "-" + monthString;
    }

    private static void updateFields(List<Map<String, Object>> csvRows) {
        for (Map<String, Object> row : csvRows) {
            updateRecordId(row);
            updateUsageStartAndEndTime(row);
        }
    }

    private static void updateUsageStartAndEndTime(Map<String, Object> row) {
        if (row.get(AWSCsvBillParser.DetailedCsvHeaders.USAGE_START_DATE) != null) {
            row.put(AWSCsvBillParser.DetailedCsvHeaders.USAGE_START_DATE, getUpdatedUsageStartDate());
            row.put(AWSCsvBillParser.DetailedCsvHeaders.USAGE_END_DATE, getUpdatedUsageEndDate());
        }
    }

    /**
     * Returns the time to be updated as the UsageStartTime of all rows of the bill
     */
    private static String getUpdatedUsageStartDate() {
        LocalDateTime firstDayOfCurrentMonth = getDateTimeToday().withDayOfMonth(1);
        DateTimeFormatter dtf = DateTimeFormat.forPattern("yyyy-MM-dd");
        DateTimeFormatter dtfOut = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
        DateTime dateTime = dtf.parseDateTime(firstDayOfCurrentMonth.toLocalDate().toString());
        return dtfOut.print(dateTime);
    }

    /**
     * Returns the time to be updated as the UsageEndTime of all rows of the bill
     */
    private static String getUpdatedUsageEndDate() {
        LocalDateTime firstDayOfCurrentMonth = LocalDateTime.now().withDayOfMonth(1);
        DateTimeFormatter dtf = DateTimeFormat.forPattern("yyyy-MM-dd");
        DateTimeFormatter dtfOut = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
        DateTime dateTime = dtf.parseDateTime(firstDayOfCurrentMonth.toLocalDate().toString());
        return dtfOut.print(dateTime.plusHours(1));
    }

    private static void updateRecordId(Map<String, Object> row) {
        Object recordId = row.get(AWSCsvBillParser.DetailedCsvHeaders.RECORD_ID);
        if (recordId != null) {
            BigInteger recordIdBi = new BigInteger((String) recordId);
            row.put(AWSCsvBillParser.DetailedCsvHeaders.RECORD_ID, recordIdBi.add(BigInteger.ONE).toString());
        }
    }

    private static List<Map<String, Object>> extractAndParseCsvFile(Path filePath) throws IOException {
        List<Map<String, Object>> csvRows = new ArrayList<>();
        String AWS_SKIP_COMMENTS = "Don't see your tags in the report";

        AWSCsvBillParser.unzip(filePath.toString(), filePath.getParent().toString());

        String unzippedCsvFilePathStr = filePath.toString()
                .substring(0, filePath.toString().lastIndexOf('.'));

        final CsvPreference STANDARD_SKIP_COMMENTS = new CsvPreference.Builder(
                CsvPreference.STANDARD_PREFERENCE)
                .skipComments(new CommentStartsWith(AWS_SKIP_COMMENTS))
                .build();

        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(
                Paths.get(unzippedCsvFilePathStr).toFile()), "UTF-8");
             ICsvMapReader mapReader = new CsvMapReader(reader, STANDARD_SKIP_COMMENTS)) {
            final String[] header = mapReader.getHeader(true);

            List<CellProcessor> processorList = new ArrayList<>();
            final CellProcessor[] basicProcessors = AWSCsvBillParser.getDetailedProcessors(header);

            processorList.addAll(Arrays.asList(basicProcessors));

            // Add new cell-processors for each extra tag column
            int numberOfTags = header.length - basicProcessors.length;
            if (numberOfTags > 0) {
                for (int i = 0; i < numberOfTags; i++) {
                    processorList.add(new org.supercsv.cellprocessor.Optional());
                }
            }
            CellProcessor[] cellProcessorArray = new CellProcessor[processorList.size()];

            cellProcessorArray = processorList.toArray(cellProcessorArray);
            Map<String, Object> row;
            while ((row = mapReader.read(header, cellProcessorArray)) != null) {
                csvRows.add(row);
            }
            return csvRows;
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * Write the updated data back to the CSV
     */
    private static void writeCsvToFile(List<Map<String, Object>> rows, Path csvBillZipFilePath) throws Exception {

        String unzippedCsvFilePathStr = csvBillZipFilePath.toString()
                .substring(0, csvBillZipFilePath.toString().lastIndexOf('.'));

        ICsvMapWriter listWriter = null;
        try {
            listWriter = new CsvMapWriter(new FileWriter(unzippedCsvFilePathStr), CsvPreference.STANDARD_PREFERENCE);

            // the header elements are used to map the bean values to each column (names must match)
            final String[] header = new String[]{"InvoiceID", "PayerAccountId", "LinkedAccountId", "RecordType",
                    "RecordId", "ProductName", "RateId", "SubscriptionId", "PricingPlanId", "UsageType", "Operation",
                    "AvailabilityZone", "ReservedInstance", "ItemDescription", "UsageStartDate", "UsageEndDate",
                    "UsageQuantity", "BlendedRate", "BlendedCost", "UnBlendedRate", "UnBlendedCost", "ResourceId",
                    "user:Description", "user:Geo", "user:Name", "user:testTag", "user:testTag2"};

            listWriter.writeHeader(header);

            for (Map<String, Object> row : rows) {
                listWriter.write(row, header);
            }
        } finally {
            if (listWriter != null) {
                listWriter.close();
            }
        }
    }

    public static void deleteCurrentMonthsBill() throws Exception {
        try {
            Path zipFilePath = com.vmware.photon.controller.model.tasks.TestUtils.getTestResourcePath(TestAWSCostAdapterService.class,
                    TestAWSSetupUtils.SAMPLE_AWS_BILL);
            Path newZipFilePath = updateZipFilePathWithCurrentMonth(zipFilePath);
            String newCsvFile = newZipFilePath.toString().substring(0, newZipFilePath.toString().lastIndexOf(AWSCsvBillParser.AWS_BILL_ZIP_FILE_NAME_SUFFIX));
            deleteFile(Paths.get(newCsvFile));
        } catch (Throwable t) {
            throw new Exception(t);
        }
    }

}
