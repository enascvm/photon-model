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

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.amazonaws.event.ProgressEvent;
import com.amazonaws.event.ProgressEventType;
import com.amazonaws.event.ProgressListener;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.transfer.Download;
import com.amazonaws.services.s3.transfer.TransferManager;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

import com.vmware.photon.controller.model.adapterapi.ComputeStatsRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse.ComputeStats;
import com.vmware.photon.controller.model.adapters.aws.dto.AwsAccountDetailDto;
import com.vmware.photon.controller.model.adapters.aws.dto.AwsResourceDetailDto;
import com.vmware.photon.controller.model.adapters.aws.dto.AwsServiceDetailDto;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSCsvBillParser;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSStatsNormalizer;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService.SingleResourceStatsCollectionTaskState;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService.SingleResourceTaskCollectionStage;
import com.vmware.photon.controller.model.tasks.monitoring.StatsUtil;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Service to gather AWS Cost related stats
 */
public class AWSCostStatsService extends StatelessService {

    public static final String SELF_LINK = AWSUriPaths.AWS_COST_STATS_ADAPTER;

    public static final String TEMP_DIR_LOCATION = "java.io.tmpdir";
    public static final String DIMENSION_CURRENCY_VALUE = "USD";

    private AWSClientManager clientManager;

    protected enum AWSCostStatsCreationStages {
        ACCOUNT_DETAILS, CLIENT, DOWNLOAD_THEN_PARSE, QUERY_LOCAL_RESOURCES, QUERY_LINKED_ACCOUNTS, QUERY_BILL_PROCESSED_TIME, CREATE_STATS, POST_STATS
    }

    public AWSCostStatsService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        this.clientManager = AWSClientManagerFactory
                .getClientManager(AWSConstants.AwsClientType.S3);
    }

    protected class AWSCostStatsCreationContext {
        protected ComputeStateWithDescription computeDesc;
        protected AuthCredentialsService.AuthCredentialsServiceState parentAuth;
        protected ComputeStatsRequest statsRequest;
        protected ComputeStatsResponse statsResponse;
        protected TransferManager s3Client;
        protected List<String> ignorableInvoiceCharge;
        protected AWSCostStatsCreationStages stage;
        protected Map<String, AwsAccountDetailDto> accountDetailsMap;
        protected Map<String, ComputeState> awsInstancesById;
        // This map is one to many because a distinct compute state will be present for each region of an AWS account.
        protected Map<String, List<ComputeState>> awsAccountIdToComputeStates;
        protected OperationContext opContext;
        protected Map<String, Long> accountIdToBillProcessedTime;

        protected AWSCostStatsCreationContext(ComputeStatsRequest statsRequest) {
            this.statsRequest = statsRequest;
            this.stage = AWSCostStatsCreationStages.ACCOUNT_DETAILS;
            this.ignorableInvoiceCharge = new ArrayList<>();
            this.awsInstancesById = new ConcurrentSkipListMap<>();
            this.awsAccountIdToComputeStates = new ConcurrentSkipListMap<>();
            this.accountIdToBillProcessedTime = new ConcurrentHashMap<>();
            this.statsResponse = new ComputeStatsResponse();
            this.statsResponse.statsList = new ArrayList<>();
            this.opContext = OperationContext.getOperationContext();
        }
    }

    @Override
    public void handleStop(Operation delete) {
        AWSClientManagerFactory.returnClientManager(this.clientManager,
                AWSConstants.AwsClientType.S3);
        super.handleStop(delete);
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        op.complete();
        ComputeStatsRequest statsRequest = op.getBody(ComputeStatsRequest.class);
        if (statsRequest.isMockRequest) {
            // patch status to parent task
            AdapterUtils.sendPatchToProvisioningTask(this, statsRequest.taskReference);
            return;
        }
        AWSCostStatsCreationContext statsData = new AWSCostStatsCreationContext(statsRequest);
        handleCostStatsCreationRequest(statsData);

    }

    protected void handleCostStatsCreationRequest(AWSCostStatsCreationContext statsData) {
        switch (statsData.stage) {
        case ACCOUNT_DETAILS:
            getAccountDescription(statsData, AWSCostStatsCreationStages.CLIENT);
            break;
        case CLIENT:
            getAWSAsyncCostingClient(statsData, AWSCostStatsCreationStages.DOWNLOAD_THEN_PARSE);
            break;
        case DOWNLOAD_THEN_PARSE:
            scheduleDownload(statsData, AWSCostStatsCreationStages.QUERY_LINKED_ACCOUNTS);
            break;
        case QUERY_LINKED_ACCOUNTS:
            queryLinkedAccounts(statsData, AWSCostStatsCreationStages.QUERY_LOCAL_RESOURCES);
            break;
        case QUERY_LOCAL_RESOURCES:
            queryInstances(statsData, AWSCostStatsCreationStages.QUERY_BILL_PROCESSED_TIME);
            break;
        case QUERY_BILL_PROCESSED_TIME:
            queryBillProcessedTime(statsData, AWSCostStatsCreationStages.CREATE_STATS);
            break;
        case CREATE_STATS:
            createStats(statsData, AWSCostStatsCreationStages.POST_STATS);
            break;
        case POST_STATS:
            postAllResourcesCostStats(statsData);
            break;
        default:
            logSevere("Unknown AWS Cost Stats enumeration stage %s ", statsData.stage.toString());
            AdapterUtils.sendFailurePatchToProvisioningTask(this,
                    statsData.statsRequest.taskReference,
                    new Exception("Unknown AWS Cost Stats enumeration stage"));
            break;
        }
    }

    protected void getAccountDescription(AWSCostStatsCreationContext statsData,
            AWSCostStatsCreationStages next) {
        Consumer<Operation> onSuccess = (op) -> {
            statsData.computeDesc = op.getBody(ComputeStateWithDescription.class);
            String accountId = statsData.computeDesc.customProperties
                    .getOrDefault(AWSConstants.AWS_ACCOUNT_ID_KEY, null);
            if (accountId == null) {
                logWarning(
                        "Account ID is not set for compute state '%s'. Not collecting cost stats.",
                        statsData.computeDesc.documentSelfLink);
                return;
            }
            Consumer<List<ComputeState>> queryResultConsumer = (accountComputeStates) -> {
                statsData.awsAccountIdToComputeStates.put(accountId, accountComputeStates);
                ComputeState primaryComputeState = findPrimaryComputeState(accountComputeStates);
                if (statsData.computeDesc.documentSelfLink
                        .equals(primaryComputeState.documentSelfLink)) {
                    // The Cost stats adapter will be configured for all compute states corresponding to an account (one per region).
                    // We want to run only once corresponding to the primary compute state.
                    getParentAuth(statsData, next);
                } else {
                    logFine("Compute state '%s' is not primary for account '%s'. Not collecting cost stats.",
                            statsData.computeDesc.documentSelfLink, accountId);
                }
            };
            sendRequest(createQueryForComputeStatesByAccount(statsData, accountId,
                    queryResultConsumer));
        };
        URI computeUri = UriUtils.extendUriWithQuery(statsData.statsRequest.resourceReference,
                UriUtils.URI_PARAM_ODATA_EXPAND, Boolean.TRUE.toString());
        AdapterUtils.getServiceState(this, computeUri, onSuccess, getFailureConsumer(statsData));
    }

    protected void getParentAuth(AWSCostStatsCreationContext statsData,
            AWSCostStatsCreationStages next) {
        Consumer<Operation> onSuccess = (op) -> {
            statsData.parentAuth = op.getBody(AuthCredentialsServiceState.class);
            statsData.stage = next;
            handleCostStatsCreationRequest(statsData);
        };
        String authLink = statsData.computeDesc.description.authCredentialsLink;
        AdapterUtils.getServiceState(this, authLink, onSuccess, getFailureConsumer(statsData));
    }

    private ComputeState findPrimaryComputeState(List<ComputeState> accountComputeStates) {
        // As of now, the oldest created compute state for this account represents the
        // primary compute state from costing stats perspective.
        return accountComputeStates.stream().min(Comparator.comparing(e -> e.creationTimeMicros))
                .get();
    }

    /**
     * Method creates a query operation to get the compute states corresponding to the specified account ID.
     * The list of the resultant compute states is then passed to the specified handler for processing.
     * @param context
     * @param accountId
     * @param queryResultConsumer
     * @return operation object representing the query
     */
    protected Operation createQueryForComputeStatesByAccount(AWSCostStatsCreationContext context,
            String accountId,
            Consumer<List<ComputeState>> queryResultConsumer) {
        Query awsAccountsQuery = Query.Builder.create().addKindFieldClause(ComputeState.class)
                .addCompositeFieldClause(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                        EndpointAllocationTaskService.CUSTOM_PROP_ENPOINT_TYPE,
                        PhotonModelConstants.EndpointType.aws.name())
                .addCompositeFieldClause(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                        AWSConstants.AWS_ACCOUNT_ID_KEY, accountId).build();
        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT).setQuery(awsAccountsQuery).build();
        queryTask.setDirect(true);
        queryTask.tenantLinks = context.computeDesc.tenantLinks;
        return Operation.createPost(getHost(), ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(queryTask)
                .setConnectionSharing(true).setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere(e);
                        AdapterUtils.sendFailurePatchToProvisioningTask(this,
                                context.statsRequest.taskReference, e);
                        return;
                    }
                    QueryTask responseTask = o.getBody(QueryTask.class);
                    List<ComputeState> accountComputeStates = responseTask.results.documents
                            .values().stream()
                            .map(s -> Utils.fromJson(s, ComputeState.class))
                            .collect(Collectors.toList());
                    queryResultConsumer.accept(accountComputeStates);
                });
    }

    protected void getAWSAsyncCostingClient(AWSCostStatsCreationContext statsData,
            AWSCostStatsCreationStages next) {
        URI parentURI = statsData.statsRequest.taskReference;
        statsData.s3Client = this.clientManager.getOrCreateS3AsyncClient(statsData.parentAuth, null,
                this, parentURI);
        statsData.stage = next;
        handleCostStatsCreationRequest(statsData);
    }

    protected void scheduleDownload(AWSCostStatsCreationContext statsData,
            AWSCostStatsCreationStages next) {
        //Starting creation of cost stats for current month

        String accountId = statsData.computeDesc.customProperties
                .getOrDefault(AWSConstants.AWS_ACCOUNT_ID_KEY, null);
        if (accountId == null) {
            logWarning("Account ID is not set for account '%s'. Not collecting cost stats.",
                    statsData.computeDesc.documentSelfLink);
            return;
        }

        LocalDate currentDate = LocalDate.now(DateTimeZone.UTC);

        String billsBucketName = statsData.computeDesc.customProperties
                .getOrDefault(AWSConstants.AWS_BILLS_S3_BUCKET_NAME_KEY, null);
        if (billsBucketName == null) {
            billsBucketName = AWSUtils
                    .autoDiscoverBillsBucketName(statsData.s3Client.getAmazonS3Client(), accountId);
            if (billsBucketName == null) {
                logWarning(
                        "Bills Bucket name is not configured for account '%s'. Not collecting cost stats.",
                        statsData.computeDesc.documentSelfLink);
                return;
            } else {
                setBillsBucketNameInAccount(statsData, billsBucketName);
            }
        }
        try {
            downloadAndParse(statsData, billsBucketName, currentDate, next);
        } catch (IOException e) {
            logSevere(e);
            AdapterUtils.sendFailurePatchToProvisioningTask(this,
                    statsData.statsRequest.taskReference, e);
        }
    }

    private void setBillsBucketNameInAccount(AWSCostStatsCreationContext statsData,
            String billsBucketName) {

        ComputeState accountState = new ComputeState();
        accountState.customProperties = new HashMap<>();
        accountState.customProperties
                .put(AWSConstants.AWS_BILLS_S3_BUCKET_NAME_KEY, billsBucketName);
        sendRequest(Operation.createPatch(this.getHost(), statsData.computeDesc.documentSelfLink)
                .setBody(accountState));
    }

    protected void queryInstances(AWSCostStatsCreationContext statsData,
            AWSCostStatsCreationStages next) {

        List<String> linkedAccountsLinks = statsData.awsAccountIdToComputeStates.values()
                .stream().flatMap(List::stream) // flatten collection of lists to single list
                .map(e -> e.documentSelfLink).collect(Collectors.toList()); // extract document self links of all accounts

        Query query = Query.Builder.create().addKindFieldClause(ComputeState.class)
                .addInClause(ComputeState.FIELD_NAME_PARENT_LINK, linkedAccountsLinks,
                        Occurance.MUST_OCCUR).build();
        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT).setQuery(query)
                .build();
        queryTask.setDirect(true);
        queryTask.tenantLinks = statsData.computeDesc.tenantLinks;
        queryTask.documentSelfLink = UUID.randomUUID().toString();
        sendRequest(
                Operation.createPost(getHost(), ServiceUriPaths.CORE_QUERY_TASKS).setBody(queryTask)
                        .setConnectionSharing(true).setCompletion((o, e) -> {
                            if (e != null) {
                                logSevere(e);
                                AdapterUtils.sendFailurePatchToProvisioningTask(this,
                                        statsData.statsRequest.taskReference, e);
                                return;
                            }
                            QueryTask responseTask = o.getBody(QueryTask.class);
                            for (Object s : responseTask.results.documents.values()) {
                                ComputeState localInstance = Utils.fromJson(s,
                                        ComputeService.ComputeState.class);
                                statsData.awsInstancesById.put(localInstance.id, localInstance);
                            }
                            logFine("Got result of the query to get local resources in Cost Adapter. There are %d instances known to the system.",
                                    responseTask.results.documentCount);
                            statsData.stage = next;
                            handleCostStatsCreationRequest(statsData);
                        }));
    }

    protected void queryBillProcessedTime(AWSCostStatsCreationContext context,
            AWSCostStatsCreationStages next) {
        // Construct a list of query operations, one for each account ID to query the
        // corresponding billProcessedTime and populate in the context object.
        List<Operation> queryOps = new ArrayList<>();
        for (Entry<String, List<ComputeState>> entry : context.awsAccountIdToComputeStates
                .entrySet()) {
            ComputeState accountComputeState = findPrimaryComputeState(entry.getValue());
            String accountId = accountComputeState.customProperties
                    .get(AWSConstants.AWS_ACCOUNT_ID_KEY);
            if (!context.accountIdToBillProcessedTime.containsKey(entry.getKey())) {
                Operation queryOp = createBillProcessedTimeOperation(context, accountComputeState,
                        accountId);
                queryOps.add(queryOp);
            }
        }

        if (queryOps.isEmpty()) {
            // billProcessedTime has already been queried for all accounts and present in the cache.
            context.stage = next;
            handleCostStatsCreationRequest(context);
            return;
        }

        joinOperationAndSendRequest(context, next, queryOps);

    }

    protected void queryLinkedAccounts(AWSCostStatsCreationContext context,
            AWSCostStatsCreationStages next) {

        // Construct a list of query operations, one for each account ID to query the
        // corresponding compute states and populate in the context object.
        List<Operation> queryOps = new ArrayList<>();
        for (String accountId : context.accountDetailsMap.keySet()) {
            if (!context.awsAccountIdToComputeStates.containsKey(accountId)) {
                Operation queryOp = createQueryForComputeStatesByAccount(context,
                        accountId,
                        (accountComputeStates) -> {
                            context.awsAccountIdToComputeStates
                                    .put(accountId, accountComputeStates);
                        });
                queryOps.add(queryOp);
            }
        }

        if (queryOps.isEmpty()) {
            // Linked accounts have already been queried and present in the cache.
            context.stage = next;
            handleCostStatsCreationRequest(context);
            return;
        }

        joinOperationAndSendRequest(context, next, queryOps);
    }

    private void inferDeletedVmCounts(AWSCostStatsCreationContext context) {
        for (AwsAccountDetailDto accountDetailDto : context.accountDetailsMap.values()) {
            AwsServiceDetailDto ec2ServiceDetailDto = accountDetailDto.serviceDetailsMap
                    .get(AWSCsvBillParser.AwsServices.ec2.getName());
            if (ec2ServiceDetailDto == null) {
                continue;
            }
            Set<String> allVmIds = ec2ServiceDetailDto.resourceDetailsMap.keySet().stream()
                    .filter(id -> id.startsWith("i-")).collect(Collectors.toSet());
            // For the current month the deleted VM count is the total count of all such instances
            // which are present in the bill but not present in the system.
            Set<String> liveVmIds = context.awsInstancesById.keySet();
            Long deletedVmCount = allVmIds.stream().filter(vmId -> !liveVmIds.contains(vmId)).count();
            accountDetailDto.deletedVmCount = deletedVmCount.intValue();
        }
    }

    private void createStats(AWSCostStatsCreationContext statsData,
            AWSCostStatsCreationStages next) {

        inferDeletedVmCounts(statsData);
        int count = 0;

        for (Entry<String, AwsAccountDetailDto> accountDetailsMapEntry : statsData.accountDetailsMap.entrySet()) {
            AwsAccountDetailDto awsAccountDetailDto = accountDetailsMapEntry.getValue();
            List<ComputeState> accountComputeStates = statsData.awsAccountIdToComputeStates
                    .getOrDefault(awsAccountDetailDto.id, null);
            Long billProcessedTimeMillis = 0L;

            if ((accountComputeStates != null) && !accountComputeStates.isEmpty()) {
                // We use the oldest compute state among those representing this account to save the account level stats.
                ComputeState accountComputeState = findPrimaryComputeState(accountComputeStates);

                String accountId = accountComputeState.customProperties
                        .get(AWSConstants.AWS_ACCOUNT_ID_KEY);
                billProcessedTimeMillis = statsData.accountIdToBillProcessedTime
                        .getOrDefault(accountId, 0L);
                logInfo("Processing and persisting stats for the account: %s "
                                + "since the time: %s UTC.",
                        accountComputeState.documentSelfLink, billProcessedTimeMillis);
                ComputeStats accountStats = createComputeStatsForAccount(
                        accountComputeState.documentSelfLink, awsAccountDetailDto,
                        billProcessedTimeMillis);
                statsData.statsResponse.statsList.add(accountStats);
            } else {
                logFine("AWS account with ID '%s' is not configured yet. Not creating cost metrics for the same.");
            }

            // create resource stats for only live EC2 instances that exist in system
            Map<String, AwsServiceDetailDto> serviceDetails = awsAccountDetailDto.serviceDetailsMap;
            for (String service : serviceDetails.keySet()) {
                if (!service.equalsIgnoreCase(AWSCsvBillParser.AwsServices.ec2.getName())) {
                    // Instance Costs are present only with EC2 service.
                    continue;
                }
                Map<String, AwsResourceDetailDto> resourceDetailsMap = serviceDetails
                        .get(service).resourceDetailsMap;
                if (resourceDetailsMap == null) {
                    continue;
                }
                for (Entry<String, AwsResourceDetailDto> entry : resourceDetailsMap.entrySet()) {
                    String resourceName = entry.getKey();
                    ComputeState computeState = statsData.awsInstancesById.get(resourceName);
                    if (computeState == null) {
                        logFine("Skipping creating costs for AWS instance %s since its compute link does not exist.",
                                resourceName);
                        continue;
                    }
                    AwsResourceDetailDto resourceDetails = entry.getValue();
                    if (resourceDetails.directCosts != null) {
                        ComputeStats vmStats = createComputeStatsForResource(
                                computeState.documentSelfLink, resourceDetails,
                                billProcessedTimeMillis);
                        statsData.statsResponse.statsList.add(vmStats);
                        count++;
                    }
                }
            }
        }
        logFine("Created Stats for %d instances", count);
        statsData.stage = next;
        handleCostStatsCreationRequest(statsData);
    }

    private void postAllResourcesCostStats(AWSCostStatsCreationContext statsData) {

        SingleResourceStatsCollectionTaskState respBody = new SingleResourceStatsCollectionTaskState();
        respBody.taskStage = SingleResourceTaskCollectionStage.valueOf(statsData.statsRequest.nextStage);
        respBody.statsAdapterReference = UriUtils.buildUri(getHost(), SELF_LINK);
        respBody.statsList = statsData.statsResponse.statsList;
        respBody.computeLink = statsData.computeDesc.documentSelfLink;
        sendRequest(Operation.createPatch(statsData.statsRequest.taskReference).setBody(respBody)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere(e);
                        AdapterUtils.sendFailurePatchToProvisioningTask(this,
                                statsData.statsRequest.taskReference, e);
                        return;
                    }
                    // After posting all stats, persist the time in the bill for the last row until which
                    // current collection & processing was done.
                    setBillProcessedTime(statsData);
                }));
    }

    private void downloadAndParse(AWSCostStatsCreationContext statsData,
            String awsBucketName, LocalDate date, AWSCostStatsCreationStages next)
            throws IOException {

        // Creating a working directory for downloading and processing the bill
        final Path workingDirPath = Paths.get(System.getProperty(TEMP_DIR_LOCATION),
                UUID.randomUUID().toString());
        Files.createDirectories(workingDirPath);

        String accountId = statsData.computeDesc.customProperties
                .getOrDefault(AWSConstants.AWS_ACCOUNT_ID_KEY, null);
        AWSCsvBillParser parser = new AWSCsvBillParser();
        final String csvBillZipFileName = parser.getCsvBillFileName(date, accountId, true);

        Path csvBillZipFilePath = Paths.get(workingDirPath.toString(), csvBillZipFileName);
        GetObjectRequest getObjectRequest = new GetObjectRequest(awsBucketName, csvBillZipFileName);
        Download download = statsData.s3Client.download(getObjectRequest,
                csvBillZipFilePath.toFile());

        final StatelessService service = this;
        download.addProgressListener(new ProgressListener() {
            @Override
            public void progressChanged(ProgressEvent progressEvent) {
                try {
                    ProgressEventType eventType = progressEvent.getEventType();
                    if (ProgressEventType.TRANSFER_COMPLETED_EVENT.equals(eventType)) {
                        LocalDate monthDate = new LocalDate(date.getYear(), date.getMonthOfYear(), 1);
                        statsData.accountDetailsMap = parser.parseDetailedCsvBill(
                                statsData.ignorableInvoiceCharge,
                                csvBillZipFilePath,
                                monthDate);
                        deleteTempFiles();
                        OperationContext.restoreOperationContext(statsData.opContext);
                        statsData.stage = next;
                        handleCostStatsCreationRequest(statsData);
                    } else if (ProgressEventType.TRANSFER_FAILED_EVENT.equals(eventType)) {
                        deleteTempFiles();
                        throw new IOException(
                                "Download of AWS CSV Bill '" + csvBillZipFileName + "' failed.");
                    }
                } catch (IOException e) {
                    logSevere(e);
                    AdapterUtils.sendFailurePatchToProvisioningTask(service,
                            statsData.statsRequest.taskReference, e);
                }
            }

            private void deleteTempFiles() {
                try {
                    Files.deleteIfExists(csvBillZipFilePath);
                    Files.deleteIfExists(workingDirPath);
                } catch (IOException e) {
                    // Ignore IO exception while cleaning files.
                }
            }
        });
    }

    private ComputeStats createComputeStatsForResource(String resourceComputeLink,
            AwsResourceDetailDto resourceDetails, Long billProcessedTimeMillis) {

        ComputeStats resourceStats = new ComputeStats();
        resourceStats.statValues = new ConcurrentSkipListMap<>();
        resourceStats.computeLink = resourceComputeLink;
        List<ServiceStat> resourceServiceStats = new ArrayList<>();
        for (Entry<Long, Double> cost : resourceDetails.directCosts.entrySet()) {
            Long usageStartTime = cost.getKey();
            if (usageStartTime.compareTo(billProcessedTimeMillis) > 0) {
                ServiceStat resourceStat = new ServiceStat();
                resourceStat.serviceReference = UriUtils.buildUri(this.getHost(), resourceComputeLink);
                resourceStat.latestValue = cost.getValue();
                resourceStat.sourceTimeMicrosUtc = TimeUnit.MILLISECONDS.toMicros(usageStartTime);
                resourceStat.unit = AWSStatsNormalizer.getNormalizedUnitValue(DIMENSION_CURRENCY_VALUE);
                resourceStat.name = AWSConstants.COST;
                resourceServiceStats.add(resourceStat);
            }
        }
        resourceStats.statValues.put(AWSConstants.COST, resourceServiceStats);
        return resourceStats;
    }

    private ComputeStats createComputeStatsForAccount(String accountComputeLink,
            AwsAccountDetailDto awsAccountDetailDto,
            Long billProcessedTimeMillis) {

        ComputeStats accountStats = new ComputeStats();
        accountStats.statValues = new ConcurrentSkipListMap<>();
        accountStats.computeLink = accountComputeLink;
        URI accountUri = UriUtils.buildUri(this.getHost(), accountComputeLink);
        long statTime = TimeUnit.MILLISECONDS.toMicros(awsAccountDetailDto.month);

        // Account cost
        ServiceStat costStat = new ServiceStat();
        costStat.serviceReference = accountUri;
        costStat.latestValue = awsAccountDetailDto.cost;
        costStat.sourceTimeMicrosUtc = statTime;
        costStat.unit = AWSStatsNormalizer.getNormalizedUnitValue(DIMENSION_CURRENCY_VALUE);
        costStat.name = AWSConstants.COST;
        accountStats.statValues.put(costStat.name, Collections.singletonList(costStat));

        // Account deleted VM count
        ServiceStat deletedVmCountStat = new ServiceStat();
        deletedVmCountStat.serviceReference = accountUri;
        deletedVmCountStat.latestValue = awsAccountDetailDto.deletedVmCount;
        deletedVmCountStat.sourceTimeMicrosUtc = statTime;
        deletedVmCountStat.unit = PhotonModelConstants.UNIT_COST;
        deletedVmCountStat.name = PhotonModelConstants.DELETED_VM_COUNT;
        accountStats.statValues
                .put(deletedVmCountStat.name, Collections.singletonList(deletedVmCountStat));

        // Create metrics for service costs and add it at the account level.
        for (AwsServiceDetailDto serviceDetailDto : awsAccountDetailDto.serviceDetailsMap
                .values()) {
            Map<String, List<ServiceStat>> statsForAwsService = createStatsForAwsService(
                    accountComputeLink, serviceDetailDto, awsAccountDetailDto.month,
                    billProcessedTimeMillis);
            accountStats.statValues.putAll(statsForAwsService);
        }
        return accountStats;
    }

    private Map<String, List<ServiceStat>> createStatsForAwsService(String accountComputeLink,
            AwsServiceDetailDto serviceDetailDto, Long month, Long billProcessedTimeMillis) {

        URI accountUri = UriUtils.buildUri(accountComputeLink);
        String currencyUnit = AWSStatsNormalizer.getNormalizedUnitValue(DIMENSION_CURRENCY_VALUE);
        // remove any spaces in the service name.
        String serviceCode = serviceDetailDto.id.replaceAll(" ", "");
        Map<String, List<ServiceStat>> stats = new HashMap<>();

        // Create stats for hourly resource cost
        List<ServiceStat> serviceStats = new ArrayList<>();
        String serviceResourceCostMetric = String
                .format(AWSConstants.SERVICE_RESOURCE_COST, serviceCode);
        for (Entry<Long, Double> cost : serviceDetailDto.directCosts.entrySet()) {
            Long usageStartTime = cost.getKey();
            if (usageStartTime.compareTo(billProcessedTimeMillis) > 0) {
                createServiceStat(accountUri, currencyUnit,
                        serviceStats, serviceResourceCostMetric, usageStartTime, cost.getValue());
            }
        }
        if (!serviceStats.isEmpty()) {
            stats.put(serviceResourceCostMetric, serviceStats);
        }

        // Create stats for hourly other costs
        serviceStats = new ArrayList<>();
        String serviceOtherCostMetric = String
                .format(AWSConstants.SERVICE_OTHER_COST, serviceCode);
        for (Entry<Long, Double> cost : serviceDetailDto.otherCosts.entrySet()) {
            Long usageStartTime = cost.getKey();
            if (usageStartTime.compareTo(billProcessedTimeMillis) > 0) {
                createServiceStat(accountUri, currencyUnit,
                        serviceStats, serviceOtherCostMetric, usageStartTime, cost.getValue());
            }
        }
        if (!serviceStats.isEmpty()) {
            stats.put(serviceOtherCostMetric, serviceStats);
        }

        // Create stats for monthly other costs
        String serviceMonthlyOtherCostMetric = String
                .format(AWSConstants.SERVICE_MONTHLY_OTHER_COST, serviceCode);
        serviceStats = new ArrayList<>();
        createServiceStat(accountUri, currencyUnit, serviceStats, serviceMonthlyOtherCostMetric,
                month, serviceDetailDto.remainingCost);
        stats.put(serviceMonthlyOtherCostMetric, serviceStats);

        // Create stats for monthly reserved recurring instance costs
        String serviceReservedRecurringCostMetric = String
                .format(AWSConstants.SERVICE_RESERVED_RECURRING_COST, serviceCode);
        serviceStats = new ArrayList<>();
        createServiceStat(accountUri, currencyUnit, serviceStats,
                serviceReservedRecurringCostMetric, month, serviceDetailDto.reservedRecurringCost);
        stats.put(serviceReservedRecurringCostMetric, serviceStats);

        return stats;
    }

    /**
     * AWS bills are read, processed and persisted based on the records which were processed in
     * the last collection cycle. This method will persist the record ID which was successfully
     * processed in the current collection cycle. The default value of this property is 0 (Zero),
     * in which case all the records in the bill for the month being processed will be processed.
     *  @param statsData
     *
     */
    protected void setBillProcessedTime(AWSCostStatsCreationContext statsData) {

        for (Entry<String, List<ComputeState>> accountComputeStates : statsData.awsAccountIdToComputeStates
                .entrySet()) {
            if (accountComputeStates.getValue() == null || accountComputeStates.getValue()
                    .isEmpty()) {
                continue;
            }
            for (ComputeState accountComputeState : accountComputeStates.getValue()) {

                String accountId = statsData.computeDesc.customProperties
                        .getOrDefault(AWSConstants.AWS_ACCOUNT_ID_KEY, null);

                if (accountId != null) {
                    String billProcessedTime = StatsUtil.getMetricKeyPrefix(SELF_LINK,
                            AWSConstants.AWS_ACCOUNT_BILL_PROCESSED_TIME_MILLIS);

                    ServiceStat stat = new ServiceStat();
                    stat.serviceReference = UriUtils
                            .buildUri(this.getHost(), accountComputeState.documentSelfLink);
                    stat.latestValue = statsData.accountDetailsMap
                            .get(accountId).billProcessedTimeMillis;
                    stat.sourceTimeMicrosUtc = TimeUnit.MILLISECONDS
                            .toMicros(statsData.accountDetailsMap
                                    .get(accountId).month);
                    stat.unit = PhotonModelConstants.UNIT_MILLISECONDS;
                    stat.name = billProcessedTime;

                    sendRequest(Operation
                            .createPost(UriUtils.buildStatsUri(getHost(),
                                    accountComputeState.documentSelfLink))
                            .setBody(stat)
                            .setCompletion((o, e) -> {
                                if (e != null) {
                                    logSevere("Unable to set billProcessedTime for account: %s: %s",
                                            accountComputeState.documentSelfLink, e);
                                }
                            }));
                } else {
                    logWarning(
                            "Couldn't find the primary account ID. "
                                    + "Not updating the processed time for this collection cycle.");
                }
            }
        }
    }

    private void createServiceStat(URI accountUri,
            String currencyUnit, List<ServiceStat> serviceStats,
            String serviceResourceCostMetric, Long timestamp, Double cost) {

        ServiceStat stat = new ServiceStat();
        stat.serviceReference = accountUri;
        stat.latestValue = cost;
        stat.sourceTimeMicrosUtc = TimeUnit.MILLISECONDS.toMicros(timestamp);
        stat.unit = currencyUnit;
        stat.name = serviceResourceCostMetric;
        serviceStats.add(stat);
    }

    private Consumer<Throwable> getFailureConsumer(AWSCostStatsCreationContext statsData) {

        return ((t) -> {
            AdapterUtils.sendFailurePatchToProvisioningTask(this,
                    statsData.statsRequest.taskReference, t);
        });
    }

    /**
     * Creates an Operation to get the billProcessedTime of an account
     * @param context has the context for this running thread, is used to populate the billProcessedTime
     * @param accountComputeState has the account compute state being processed
     * @param accountId the AWS account identifier of the account being processed
     * @return the operation to get the billProcessedTime for the entry passed
     */
    private Operation createBillProcessedTimeOperation(AWSCostStatsCreationContext context,
            ComputeState accountComputeState, String accountId) {

        return Operation.createGet(
                UriUtils.buildStatsUri(getHost(), accountComputeState.documentSelfLink))
                .setCompletion((operation, exception) -> {
                    if (exception != null) {
                        logWarning(
                                "Failed to get bill processed time for account: %s %s",
                                accountComputeState.documentSelfLink, exception);
                        AdapterUtils.sendFailurePatchToProvisioningTask(this,
                                context.statsRequest.taskReference, exception);
                    }
                    ServiceStats body = operation.getBody(ServiceStats.class);
                    ServiceStat serviceStat = body.entries
                            .get(StatsUtil.getMetricKeyPrefix(SELF_LINK,
                                    AWSConstants.AWS_ACCOUNT_BILL_PROCESSED_TIME_MILLIS));
                    long accountBillProcessedTime = 0L;
                    if (serviceStat != null) {
                        accountBillProcessedTime = (long) serviceStat.latestValue;
                    }
                    context.accountIdToBillProcessedTime
                            .put(accountId, accountBillProcessedTime);
                });
    }

    private void joinOperationAndSendRequest(AWSCostStatsCreationContext context,
            AWSCostStatsCreationStages next, List<Operation> queryOps) {
        OperationJoin.create(queryOps).setCompletion((operationMap, exception) -> {
            if (exception != null && !exception.isEmpty()) {
                Throwable firstException = exception.values().iterator().next();
                logSevere(firstException);
                AdapterUtils.sendFailurePatchToProvisioningTask(this,
                        context.statsRequest.taskReference, firstException);
                return;
            }
            context.stage = next;
            handleCostStatsCreationRequest(context);
        }).sendWith(this, AWSConstants.OPERATION_BATCH_SIZE);
    }

}
