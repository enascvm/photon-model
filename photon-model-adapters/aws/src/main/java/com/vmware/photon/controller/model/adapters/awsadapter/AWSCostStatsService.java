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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.amazonaws.event.ProgressEvent;
import com.amazonaws.event.ProgressEventType;
import com.amazonaws.event.ProgressListener;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.transfer.Download;
import com.amazonaws.services.s3.transfer.TransferManager;

import org.joda.time.DateTime;
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
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService;
import com.vmware.photon.controller.model.tasks.QueryUtils;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService.SingleResourceStatsCollectionTaskState;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService.SingleResourceTaskCollectionStage;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
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
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;
import com.vmware.xenon.services.common.ServiceUriPaths;
/**
 * Service to gather AWS Cost related stats
 */
public class AWSCostStatsService extends StatelessService {

    public static final String SELF_LINK = AWSUriPaths.AWS_COST_STATS_ADAPTER;

    public static final String TEMP_DIR_LOCATION = "java.io.tmpdir";
    public static final String DIMENSION_CURRENCY_VALUE = "USD";
    // Past months for which bills need to be collected, default is 11, excluding current month's bill.
    protected static final String BILLS_BACK_IN_TIME_MONTHS_KEY = "aws.costsCollection.backInTime.months";

    private AWSClientManager clientManager;
    private ExecutorService executor;

    protected enum AWSCostStatsCreationStages {
        ACCOUNT_DETAILS, CLIENT, QUERY_BILL_MONTH_TO_DOWNLOAD, DOWNLOAD_THEN_PARSE, QUERY_LOCAL_RESOURCES, QUERY_LINKED_ACCOUNTS, QUERY_BILL_PROCESSED_TIME, CREATE_STATS, POST_STATS
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
        // Will hold a map of account details month-wise. The map is composed as:
        // the month as LocalDate as the key and the value of this map is the map of:
        // the account ID as the key and the account details as AwsAccountDetailDto as value
        protected Map<LocalDate, Map<String, AwsAccountDetailDto>> accountsHistoricalDetailsMap;

        // This map is one to many because an AWS instance will have different compute links if the same account
        // is added by different users.
        protected Map<String, List<ComputeState>> awsInstancesById;
        // This map is one to many because a distinct compute state will be present for each region of an AWS account.
        protected Map<String, List<ComputeState>> awsAccountIdToComputeStates;
        protected OperationContext opContext;
        protected Map<String, Long> accountIdToBillProcessedTime;
        // Holds the month for which bill has to be downloaded and processed
        protected LocalDate billMonthToDownload;

        protected AWSCostStatsCreationContext(ComputeStatsRequest statsRequest) {
            this.statsRequest = statsRequest;
            this.stage = AWSCostStatsCreationStages.ACCOUNT_DETAILS;
            this.ignorableInvoiceCharge = new ArrayList<>();
            this.awsInstancesById = new ConcurrentHashMap<>();
            this.awsAccountIdToComputeStates = new ConcurrentHashMap<>();
            this.accountIdToBillProcessedTime = new ConcurrentHashMap<>();
            this.statsResponse = new ComputeStatsResponse();
            this.statsResponse.statsList = new ArrayList<>();
            this.opContext = OperationContext.getOperationContext();
            this.accountsHistoricalDetailsMap = new ConcurrentHashMap<>();
        }
    }

    @Override
    public void handleStart(Operation start) {
        this.executor = getHost().allocateExecutor(this);
        super.handleStart(start);
    }

    @Override
    public void handleStop(Operation delete) {
        AWSClientManagerFactory.returnClientManager(this.clientManager,
                AWSConstants.AwsClientType.S3);
        this.executor.shutdown();
        AdapterUtils.awaitTermination(this.executor);
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
        try {
            switch (statsData.stage) {
            case ACCOUNT_DETAILS:
                getAccountDescription(statsData, AWSCostStatsCreationStages.CLIENT);
                break;
            case CLIENT:
                getAWSAsyncCostingClient(statsData,
                        AWSCostStatsCreationStages.QUERY_BILL_MONTH_TO_DOWNLOAD);
                break;
            case QUERY_BILL_MONTH_TO_DOWNLOAD:
                // Need to query here to get the billProcessedTime before scheduling downloading
                // bills since the months' for which bills are downloaded are determined by the
                // billProcessedTime.
                queryBillProcessedTime(statsData, AWSCostStatsCreationStages.DOWNLOAD_THEN_PARSE);
                break;
            case DOWNLOAD_THEN_PARSE:
                scheduleDownload(statsData, AWSCostStatsCreationStages.QUERY_LINKED_ACCOUNTS);
                break;
            case QUERY_LINKED_ACCOUNTS:
                queryLinkedAccounts(statsData,
                        AWSCostStatsCreationStages.QUERY_BILL_PROCESSED_TIME);
                break;
            case QUERY_BILL_PROCESSED_TIME:
                // Need to query billProcessedTime for linked accounts as well which weren't
                // available when queried last.
                queryBillProcessedTime(statsData, AWSCostStatsCreationStages.QUERY_LOCAL_RESOURCES);
                break;
            case QUERY_LOCAL_RESOURCES:
                queryInstances(statsData, AWSCostStatsCreationStages.CREATE_STATS);
                break;
            case CREATE_STATS:
                createStats(statsData, AWSCostStatsCreationStages.POST_STATS);
                break;
            case POST_STATS:
                postAllResourcesCostStats(statsData);
                break;
            default:
                logSevere(() -> String.format("Unknown AWS Cost Stats enumeration stage %s ",
                        statsData.stage.toString()));
                getFailureConsumer(statsData)
                        .accept(new Exception("Unknown AWS Cost Stats enumeration stage"));
                break;
            }
        } catch (Exception e) {
            getFailureConsumer(statsData).accept(e);
            return;
        }
    }

    protected void getAccountDescription(AWSCostStatsCreationContext statsData,
            AWSCostStatsCreationStages next) {
        Consumer<Operation> onSuccess = (op) -> {
            statsData.computeDesc = op.getBody(ComputeStateWithDescription.class);
            String accountId = statsData.computeDesc.customProperties
                    .getOrDefault(AWSConstants.AWS_ACCOUNT_ID_KEY, null);
            if (accountId == null || statsData.computeDesc.endpointLink == null) {
                logWarning(() -> String.format("Account ID or endpoint link is not set for compute state '%s'. Not"
                        + " collecting cost stats.", statsData.computeDesc.documentSelfLink));
                postAllResourcesCostStats(statsData);
                return;
            }
            Consumer<List<ComputeState>> queryResultConsumer = (accountComputeStates) -> {
                accountComputeStates = accountComputeStates.stream()
                        .filter(c -> c.endpointLink != null && c.endpointLink.equals(statsData.computeDesc.endpointLink))
                        .collect(Collectors.toList());
                statsData.awsAccountIdToComputeStates.put(accountId, accountComputeStates);
                ComputeState primaryComputeState = findRootAccountComputeState(accountComputeStates);
                if (statsData.computeDesc.documentSelfLink
                        .equals(primaryComputeState.documentSelfLink)) {
                    // The Cost stats adapter will be configured for all compute states corresponding to an account (one per region).
                    // We want to run only once corresponding to the primary compute state.
                    getParentAuth(statsData, next);
                } else {
                    logFine(() -> String.format("Compute state '%s' is not primary for account '%s'."
                                    + " Not collecting cost stats.",
                            statsData.computeDesc.documentSelfLink, accountId));
                    postAllResourcesCostStats(statsData);
                    return;
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

    private ComputeState findRootAccountComputeState(List<ComputeState> accountComputeStates) {
        return accountComputeStates.stream()
                .filter(c -> c.parentLink == null && c.endpointLink != null)
                .min(Comparator.comparing(c -> c.creationTimeMicros)).get();
    }

    private Map<String, ComputeState> findRootAccountComputeStateByEndpoint(
            List<ComputeState> accountComputeStates) {
        return accountComputeStates.stream()
                .filter(c -> c.parentLink == null && c.endpointLink != null)
                .collect(Collectors.groupingBy(c -> c.endpointLink))
                .entrySet().stream()
                // For each endpoint, find the oldest compute state
                .collect(Collectors.toMap(e -> e.getKey(),
                        e -> e.getValue().stream().min(Comparator.comparing(c -> c.creationTimeMicros)).get()));
    }

    /**
     * Method creates a query operation to get the compute states corresponding to the specified account ID.
     * The list of the resultant compute states is then passed to the specified handler for processing.
     *
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
                        AWSConstants.AWS_ACCOUNT_ID_KEY, accountId)
                .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK, "*", QueryTask.QueryTerm.MatchType.WILDCARD,
                        QueryTask.Query.Occurance.MUST_NOT_OCCUR)
                .addFieldClause(ComputeState.FIELD_NAME_ENDPOINT_LINK, "*", MatchType.WILDCARD, Occurance.MUST_OCCUR)
                .build();
        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT).setQuery(awsAccountsQuery).build();
        queryTask.setDirect(true);
        queryTask.tenantLinks = context.computeDesc.tenantLinks;
        return Operation.createPost(getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS)
                .setBody(queryTask)
                .setConnectionSharing(true).setCompletion((o, e) -> {
                    if (e != null) {
                        getFailureConsumer(context).accept(e);
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

    /**
     * Schedules downloading and parsing of AWS bills. Past month bills are collected depending upon
     * upon the value of BILLS_BACK_IN_TIME_MONTHS_KEY as a system property. This property decides how
     * many past bills should be collected, processed and parsed.
     * @param statsData context for the current running thread
     * @param next the next stage to process
     */
    protected void scheduleDownload(AWSCostStatsCreationContext statsData,
            AWSCostStatsCreationStages next) {

        OperationContext origContext = OperationContext.getOperationContext();
        this.executor.submit(() -> {
            OperationContext.restoreOperationContext(origContext);
            String accountId = statsData.computeDesc.customProperties
                    .getOrDefault(AWSConstants.AWS_ACCOUNT_ID_KEY, null);
            logFine(() -> String.format("Account: %s was last processed at: %s.", accountId,
                    statsData.accountIdToBillProcessedTime.get(accountId)));

            if (accountId == null) {
                logWarning(() -> String.format("Account ID is not set for account '%s'. "
                        + "Not collecting cost stats.", statsData.computeDesc.documentSelfLink));
                postAllResourcesCostStats(statsData);
                return;
            }

            String billsBucketName = statsData.computeDesc.customProperties
                    .getOrDefault(AWSConstants.AWS_BILLS_S3_BUCKET_NAME_KEY, null);
            try {
                if (billsBucketName == null) {
                    billsBucketName = AWSUtils.autoDiscoverBillsBucketName
                            (statsData.s3Client.getAmazonS3Client(), accountId);
                    if (billsBucketName == null) {
                        logWarning(() -> String.format("Bills Bucket name is not configured for "
                                        + "account '%s'. Not collecting cost stats.",
                                statsData.computeDesc.documentSelfLink));
                        postAllResourcesCostStats(statsData);
                        return;
                    } else {
                        setCustomProperty(statsData, AWSConstants.AWS_BILLS_S3_BUCKET_NAME_KEY,
                                billsBucketName);
                    }
                }
                if (statsData.billMonthToDownload == null) {
                    populateBillMonthToProcess(statsData, accountId);
                }
                LocalDate firstDayOfCurrentMonth = getFirstDayOfCurrentMonth();
                if (statsData.billMonthToDownload.compareTo(firstDayOfCurrentMonth) <= 0) {
                    downloadAndParse(statsData, billsBucketName);
                } else {
                    // Proceed to the next stage only after downloading and processing all the past
                    // and current months' bills.
                    statsData.stage = next;
                    handleCostStatsCreationRequest(statsData);
                }
            } catch (Exception e) {
                getFailureConsumer(statsData).accept(e);
            }
        });
    }

    /**
     * Will set AWSCostStatsCreationContext.billMonthToDownload to infer which month's bill has to be downloaded
     * to begin the AWS data collection. Bills will be downloaded starting from this month up to
     * the current month. These number of months are configurable, specified by
     * the SystemProperty {@code BILLS_BACK_IN_TIME_MONTHS_KEY}
     * @param context has the context for this running thread, is used to populate the billProcessedTime
     * @param accountId of the account whose bill has to be downloaded
     */
    protected void populateBillMonthToProcess(AWSCostStatsCreationContext context, String accountId) {

        Long billProcessedTime = context.accountIdToBillProcessedTime.getOrDefault(accountId, 0L);
        LocalDate billProcessedLocalDate = new LocalDate(billProcessedTime, DateTimeZone.UTC);
        billProcessedLocalDate = billProcessedLocalDate
                .minusDays(AWSConstants.NO_OF_DAYS_MARGIN_FOR_AWS_TO_UPDATE_BILL);
        int noPastMonthsBills = Integer.getInteger(BILLS_BACK_IN_TIME_MONTHS_KEY, AWSConstants.DEFAULT_NO_OF_MONTHS_TO_GET_PAST_BILLS);
        LocalDate currentMonth = LocalDate.now(DateTimeZone.UTC);
        //currentMonth will be last day of the month e.g. 31-MM-YYYY
        currentMonth = currentMonth.withDayOfMonth(currentMonth.dayOfMonth().getMaximumValue());
        LocalDate start = billProcessedLocalDate;
        if (billProcessedLocalDate.isBefore(currentMonth)) {
            LocalDate trendMinMonth = currentMonth.minusMonths(noPastMonthsBills);
            if (billProcessedLocalDate.isBefore(trendMinMonth)) {
                start = trendMinMonth;
            }
        }
        context.billMonthToDownload = start.withDayOfMonth(1);
        logFine(() -> String.format("Downloading AWS account %s bills since: %s.", accountId,
                context.billMonthToDownload));
    }

    private void setCustomProperty(AWSCostStatsCreationContext context, String key, String value) {
        context.computeDesc.customProperties.put(key, value);
        ComputeState accountState = new ComputeState();
        accountState.customProperties = new HashMap<>();
        accountState.customProperties.put(key, value);
        sendRequest(Operation.createPatch(this.getHost(), context.computeDesc.documentSelfLink)
                .setBody(accountState));
    }

    protected void queryInstances(AWSCostStatsCreationContext statsData,
            AWSCostStatsCreationStages next) {

        List<String> linkedAccountsLinks = statsData.awsAccountIdToComputeStates.values()
                .stream().flatMap(List::stream) // flatten collection of lists to single list
                .map(e -> e.documentSelfLink).collect(Collectors.toList()); // extract document self links of all accounts

        Query query = Query.Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_TYPE,
                        ComputeDescriptionService.ComputeDescription.ComputeType.VM_GUEST)
                .addInClause(ComputeState.FIELD_NAME_PARENT_LINK, linkedAccountsLinks,
                        Occurance.MUST_OCCUR)
                .build();
        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .setQuery(query)
                .setResultLimit(AWSConstants.getQueryResultLimit())
                .build();
        queryTask.tenantLinks = statsData.computeDesc.tenantLinks;
        queryTask.documentSelfLink = UUID.randomUUID().toString();
        QueryUtils.startQueryTask(this, queryTask).whenComplete((qrt, e) -> {
            if (e != null) {
                getFailureConsumer(statsData).accept(e);
                return;
            }
            populateAwsInstances(qrt.results.nextPageLink, statsData, next);
        });
    }

    private void populateAwsInstances(String nextPageLink, AWSCostStatsCreationContext context,
            AWSCostStatsCreationStages next) {
        if (nextPageLink == null) {
            context.stage = next;
            handleCostStatsCreationRequest(context);
        } else {
            Operation.createGet(this, nextPageLink).setCompletion((o, e) -> {
                if (e != null) {
                    getFailureConsumer(context).accept(e);
                    return;
                }
                QueryTask queryTask = o.getBody(QueryTask.class);
                Map<String, List<ComputeState>> instancesById = queryTask.results.documents.values()
                        .stream()
                        .map(s -> Utils.fromJson(s, ComputeState.class))
                        .collect(Collectors.groupingBy(c -> c.id));
                instancesById.forEach(
                        (k, v) -> context.awsInstancesById.merge(k, v, (list1, list2) -> {
                            list1.addAll(list2);
                            return list1;
                        }));
                logFine(() -> String.format("Found %d instances in current page",
                        queryTask.results.documentCount));
                populateAwsInstances(queryTask.results.nextPageLink, context, next);
            }).sendWith(this);
        }
    }

    protected void queryBillProcessedTime(AWSCostStatsCreationContext context,
            AWSCostStatsCreationStages next) {

        // Construct a list of query operations, one for each account ID to query the
        // corresponding billProcessedTime and populate in the context object.
        List<Operation> queryOps = new ArrayList<>();
        for (Entry<String, List<ComputeState>> entry : context.awsAccountIdToComputeStates
                .entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                // In case the primary account has a linked account which has not been added
                // as a compute state for enumeration and stats collection, it won't be present
                // in this map and we won't persist and hence won't query billProcessedTime
                // for that account.
                continue;
            }
            ComputeState accountComputeState = findRootAccountComputeState(entry.getValue());
            queryOps.add(createBillProcessedTimeOperation(
                    context, accountComputeState, entry.getKey()));
        }

        if (queryOps.isEmpty()) {
            // billProcessedTime has already been queried for all accounts and present in the cache.
            context.stage = next;
            handleCostStatsCreationRequest(context);
            return;
        }
        joinOperationAndSendRequest(context, next, queryOps);
    }

    private void setLinkedAccountIds(AWSCostStatsCreationContext context) {
        Map<String, String> primaryAccountProps = context.computeDesc.customProperties;
        String primaryAccountId = primaryAccountProps.get(AWSConstants.AWS_ACCOUNT_ID_KEY);
        String linkedAccountIds = context.accountsHistoricalDetailsMap
                .get(getFirstDayOfCurrentMonth()).keySet().stream()
                .filter(accountId -> accountId != null && !accountId.equals(primaryAccountId))
                .sorted().collect(Collectors.joining(","));
        String prevLinkedAccountIds = primaryAccountProps
                .getOrDefault(AWSConstants.AWS_LINKED_ACCOUNT_IDS, "");
        if (!prevLinkedAccountIds.equals(linkedAccountIds)) {
            setCustomProperty(context, AWSConstants.AWS_LINKED_ACCOUNT_IDS,
                    linkedAccountIds);
        }
    }

    protected void queryLinkedAccounts(AWSCostStatsCreationContext context,
            AWSCostStatsCreationStages next) {
        setLinkedAccountIds(context);
        // Construct a list of query operations, one for each account ID to query the
        // corresponding compute states and populate in the context object.
        List<Operation> queryOps = new ArrayList<>();
        // Get the current month's account data
        LocalDate currentMonth = getFirstDayOfCurrentMonth();
        Map<String, AwsAccountDetailDto> accountDetailsMap = context.accountsHistoricalDetailsMap.get(currentMonth);
        for (String accountId : accountDetailsMap.keySet()) {
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

        LocalDate currentMonth = getFirstDayOfCurrentMonth();
        for (Entry<LocalDate, Map<String, AwsAccountDetailDto>> monthlyAccountDetails : context.accountsHistoricalDetailsMap
                .entrySet()) {
            if (monthlyAccountDetails.getKey() != currentMonth) {
                inferDeletedVmCountsForPastMonths(context, monthlyAccountDetails.getKey());
            } else {
                inferDeletedVmCountsForCurrentMonth(context, monthlyAccountDetails.getKey());
            }
        }
    }

    private LocalDate getFirstDayOfCurrentMonth() {
        return LocalDate.now(DateTimeZone.UTC).withDayOfMonth(1);
    }

    /**
     * For the current month the deleted VM count is the total count of all such instances which are
     * present in the bill but not present in the system.
     * @param context has the context for this running thread.
     * @param month the month for which the deleted number of EC2 instances are required.
     */
    private void inferDeletedVmCountsForCurrentMonth(
            AWSCostStatsCreationContext context, LocalDate month) {

        Map<String, AwsAccountDetailDto> accountDetails = context.accountsHistoricalDetailsMap
                .get(month);
        for (AwsAccountDetailDto accountDetailDto : accountDetails.values()) {
            Set<String> allVmIds = getEc2InstanceIdsInAccount(accountDetailDto);
            Set<String> liveVmIds = context.awsInstancesById.keySet();
            Long deletedVmCount = allVmIds.stream().filter(vmId -> !liveVmIds.contains(vmId))
                    .count();
            accountDetailDto.deletedVmCount = deletedVmCount.intValue();

            logFine(() -> String.format("Found %s deleted VMs for the account: %s for the month of:"
                            + " %s-%s", deletedVmCount, accountDetailDto.id, month.getYear(),
                    month.getMonthOfYear()));
        }
    }


    /**
     * For the past months the deleted VM count is the total count of all such instances which:
     * are present in the bill for the previous month - those present in the month under consideration.
     * @param context has the context for this running thread.
     * @param month the month for which the deleted number of EC2 instances are required.
     */
    private void inferDeletedVmCountsForPastMonths(
            AWSCostStatsCreationContext context, LocalDate month) {

        Map<String, AwsAccountDetailDto> accountsDetailsOfMonthUnderConsideration = context.accountsHistoricalDetailsMap
                .get(month);
        Map<String, AwsAccountDetailDto> accountsDetailsOfNextMonth = context.accountsHistoricalDetailsMap
                .get(month.plusMonths(1));

        for (AwsAccountDetailDto accountDetailsOfMonthUnderConsideration : accountsDetailsOfMonthUnderConsideration
                .values()) {
            Set<String> instancesInMonthUnderConsideration = getEc2InstanceIdsInAccount(
                    accountDetailsOfMonthUnderConsideration);
            if (accountsDetailsOfNextMonth != null &&
                    accountsDetailsOfNextMonth
                            .get(accountDetailsOfMonthUnderConsideration.id)
                            != null) {
                Set<String> instancesInNextMonth = getEc2InstanceIdsInAccount(
                        accountsDetailsOfNextMonth
                                .get(accountDetailsOfMonthUnderConsideration.id));
                Long deletedVmCount = instancesInMonthUnderConsideration.stream()
                        .filter(vmId -> !instancesInNextMonth.contains(vmId)).count();
                accountDetailsOfMonthUnderConsideration.deletedVmCount = deletedVmCount
                        .intValue();
                logFine(() -> String.format("Found %s deleted VMs for the account: %s for the month"
                                + " of: %s", deletedVmCount,
                        accountDetailsOfMonthUnderConsideration.id, month));
            }
        }
    }

    /**
     * Returns the instance IDs of instances for a given account.
     * @param accountDetails the AwsAccountDetailDto of the account whose instance IDs are required.
     * @return Set of instance IDs, empty set in case there are none.
     */
    private Set<String> getEc2InstanceIdsInAccount(AwsAccountDetailDto accountDetails) {

        AwsServiceDetailDto ec2ServiceDetailDtoForMonthInConsideration = accountDetails.serviceDetailsMap
                .get(AWSCsvBillParser.AwsServices.ec2.getName());
        if (ec2ServiceDetailDtoForMonthInConsideration == null) {
            return new HashSet<>();
        }
        return ec2ServiceDetailDtoForMonthInConsideration.resourceDetailsMap
                .keySet().stream()
                .filter(id -> id.startsWith("i-")).collect(Collectors.toSet());
    }

    private void createStats(AWSCostStatsCreationContext statsData,
            AWSCostStatsCreationStages next) {

        inferDeletedVmCounts(statsData);

        for (Entry<LocalDate, Map<String, AwsAccountDetailDto>> accountsHistoricalDataMapEntry :
                statsData.accountsHistoricalDetailsMap.entrySet()) {
            for (Entry<String, AwsAccountDetailDto> accountsMonthlyDataEntry : accountsHistoricalDataMapEntry
                    .getValue().entrySet()) {
                createStatsForMonth(statsData, accountsHistoricalDataMapEntry.getKey(),
                        accountsMonthlyDataEntry.getValue());
            }
        }
        statsData.stage = next;
        handleCostStatsCreationRequest(statsData);
    }

    private void createStatsForMonth(AWSCostStatsCreationContext statsData, LocalDate billMonth,
            AwsAccountDetailDto awsAccountDetailDto) {

        List<ComputeState> accountComputeStates = statsData.awsAccountIdToComputeStates
                .getOrDefault(awsAccountDetailDto.id, null);
        long lastBillProcessedTimeMillis = statsData.accountIdToBillProcessedTime
                .getOrDefault(awsAccountDetailDto.id, 0L);

        if ((accountComputeStates != null) && !accountComputeStates.isEmpty()) {
            // We use the root compute state representing this account to save the account level stats.
            Map<String, ComputeState> rootAccountComputeStateByEndpoint = findRootAccountComputeStateByEndpoint(accountComputeStates);
            for (ComputeState accountComputeState : rootAccountComputeStateByEndpoint.values()) {
                logFine(() -> String.format("Processing and persisting stats for the account: %s "
                                        + "for the month: %s.", accountComputeState.documentSelfLink,
                                billMonth));
                ComputeStats accountStats = new ComputeStats();
                accountStats.statValues = new ConcurrentSkipListMap<>();
                accountStats.computeLink = accountComputeState.documentSelfLink;
                if (isBillUpdated(statsData, awsAccountDetailDto)) {
                    accountStats = createComputeStatsForAccount(
                            accountComputeState.documentSelfLink, awsAccountDetailDto, billMonth, lastBillProcessedTimeMillis);
                }
                ServiceStat billProcessedTimeStat = createBillProcessedTimeStat(statsData,
                        accountComputeState);
                accountStats.statValues.put(billProcessedTimeStat.name,
                        Collections.singletonList(billProcessedTimeStat));
                statsData.statsResponse.statsList.add(accountStats);

            }

        } else {
            logFine(() -> "AWS account with ID '%s' is not configured yet. Not creating cost"
                    + " metrics for the same.");
        }

        if (!isBillUpdated(statsData, awsAccountDetailDto)) {
            return;
        }
        // Create stats only if the current run has data that was never persisted.
        DateTime firstDayOfCurrentMonth = getFirstDayOfCurrentMonth()
                .toDateTimeAtStartOfDay(DateTimeZone.UTC);
        // Persist resource stats only for current month.
        if (awsAccountDetailDto.billProcessedTimeMillis < firstDayOfCurrentMonth
                .getMillis()) {
            return;
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
            for (Entry<String, AwsResourceDetailDto> entry : resourceDetailsMap
                    .entrySet()) {
                String resourceId = entry.getKey();
                AwsResourceDetailDto resourceDetails = entry.getValue();
                if ((resourceDetails == null) || (resourceDetails.directCosts == null)) {
                    continue;
                }
                List<ComputeState> computeStates = statsData.awsInstancesById
                        .getOrDefault(resourceId, Collections.emptyList());
                for (ComputeState resourceComputeState : computeStates) {
                    ComputeStats resourceStats = createComputeStatsForResource(
                            resourceComputeState.documentSelfLink, resourceDetails,
                            lastBillProcessedTimeMillis);
                    statsData.statsResponse.statsList.add(resourceStats);
                }
            }
        }
    }

    /**
     * Decides if stats - account, service and resource, need to be created or not after the bill
     * has been parsed and processed. If the bill hasn't changed since the last run, do NOT create
     * and persist stats, otherwise do.
     * @param context the context for the current run.
     * @param accountDetailDto the DTO for the account being processed.
     * @return true if stats need to be persisted, false otherwise.
     */
    private boolean isBillUpdated(AWSCostStatsCreationContext context,
            AwsAccountDetailDto accountDetailDto) {
        long currentBillProcessedTime = TimeUnit.MILLISECONDS
                .toMicros(accountDetailDto.billProcessedTimeMillis);
        long lastBillProcessedTime = TimeUnit.MILLISECONDS
                .toMicros(context.accountIdToBillProcessedTime
                        .getOrDefault(accountDetailDto.id, 0L));
        return currentBillProcessedTime > lastBillProcessedTime;
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
                        getFailureConsumer(statsData).accept(e);
                        return;
                    }
                }));
    }

    private void downloadAndParse(AWSCostStatsCreationContext statsData, String awsBucketName) throws IOException {

        String accountId = statsData.computeDesc.customProperties
                .getOrDefault(AWSConstants.AWS_ACCOUNT_ID_KEY, null);
        try {
            // Creating a working directory for downloading and processing the bill
            final Path workingDirPath = Paths.get(System.getProperty(TEMP_DIR_LOCATION),
                    UUID.randomUUID().toString());
            Files.createDirectories(workingDirPath);

            AWSCsvBillParser parser = new AWSCsvBillParser();
            final String csvBillZipFileName = parser.getCsvBillFileName(statsData.billMonthToDownload, accountId, true);

            Path csvBillZipFilePath = Paths.get(workingDirPath.toString(), csvBillZipFileName);
            GetObjectRequest getObjectRequest = new GetObjectRequest(awsBucketName,
                    csvBillZipFileName);
            Download download = statsData.s3Client.download(getObjectRequest,
                    csvBillZipFilePath.toFile());
            download.addProgressListener(new ProgressListener() {
                @Override
                public void progressChanged(ProgressEvent progressEvent) {
                    try {
                        ProgressEventType eventType = progressEvent.getEventType();
                        if (ProgressEventType.TRANSFER_COMPLETED_EVENT.equals(eventType)) {
                            LocalDate firstDayOfBillMonthToDownload = new LocalDate(
                                    statsData.billMonthToDownload.getYear(),
                                    statsData.billMonthToDownload.getMonthOfYear(), 1);
                            logFine(() -> String.format("Processing AWS bill for account: %s for"
                                            + " the month: %s.", accountId,
                                    firstDayOfBillMonthToDownload));
                            statsData.accountsHistoricalDetailsMap
                                    .put(firstDayOfBillMonthToDownload,
                                            parser.parseDetailedCsvBill(
                                                    statsData.ignorableInvoiceCharge,
                                                    csvBillZipFilePath));
                            deleteTempFiles();
                            OperationContext.restoreOperationContext(statsData.opContext);
                            // Continue downloading and processing the bills for past and current
                            // months' bills
                            statsData.billMonthToDownload = statsData.billMonthToDownload.plusMonths(1);
                            handleCostStatsCreationRequest(statsData);
                        } else if (ProgressEventType.TRANSFER_FAILED_EVENT.equals(eventType)) {
                            deleteTempFiles();
                            billDownloadFailureHandler(statsData, awsBucketName, accountId,
                                    new IOException("Download of AWS CSV Bill '" + csvBillZipFileName
                                                    + "' failed."));
                        }
                    } catch (Exception exception) {
                        deleteTempFiles();
                        billDownloadFailureHandler(statsData, awsBucketName, accountId, exception);
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
        } catch (AmazonS3Exception s3Exception) {
            billDownloadFailureHandler(statsData, awsBucketName, accountId, s3Exception);
        }
    }

    private void billDownloadFailureHandler(
            AWSCostStatsCreationContext statsData, String awsBucketName,
            String accountId, Exception exception) {
        StringWriter error = new StringWriter();
        exception.printStackTrace(new PrintWriter(error));
        if (isCurrentMonth(statsData.billMonthToDownload)) {
            // Abort if the current month's bill is NOT available.
            logSevere(() -> "Current month's bill is not available in the AWS S3 bucket. "
                    + error.toString());
            getFailureConsumer(statsData).accept(exception);
        } else {
            // Ignore if bill(s) of previous month(s) are not available.
            logFine(() -> String.format("AWS bill for account: %s for the month of: %s-%s was not"
                            + " available from the bucket: %s. Proceeding to process bills for"
                            + " following months. %s", accountId,
                    statsData.billMonthToDownload.getYear(),
                    statsData.billMonthToDownload.getMonthOfYear(), awsBucketName, error.toString()));
            // Continue downloading and processing the bills for following
            // month's bill
            statsData.billMonthToDownload = statsData.billMonthToDownload.plusMonths(1);
            OperationContext.restoreOperationContext(statsData.opContext);
            handleCostStatsCreationRequest(statsData);

        }
    }

    private ComputeStats createComputeStatsForResource(String resourceComputeLink,
            AwsResourceDetailDto resourceDetails, Long billProcessedTimeMillis) {

        ComputeStats resourceStats = new ComputeStats();
        resourceStats.statValues = new ConcurrentSkipListMap<>();
        resourceStats.computeLink = resourceComputeLink;
        List<ServiceStat> resourceServiceStats = new ArrayList<>();
        String normalizedStatKeyValue = AWSStatsNormalizer
                .getNormalizedStatKeyValue(AWSConstants.COST);
        for (Entry<Long, Double> cost : resourceDetails.directCosts.entrySet()) {
            Long usageStartTime = cost.getKey();
            if (usageStartTime.compareTo(billProcessedTimeMillis) > 0) {
                ServiceStat resourceStat = new ServiceStat();
                resourceStat.serviceReference = UriUtils
                        .buildUri(this.getHost(), resourceComputeLink);
                resourceStat.latestValue = cost.getValue();
                resourceStat.sourceTimeMicrosUtc = TimeUnit.MILLISECONDS.toMicros(usageStartTime);
                resourceStat.unit = AWSStatsNormalizer
                        .getNormalizedUnitValue(DIMENSION_CURRENCY_VALUE);
                resourceStat.name = normalizedStatKeyValue;
                resourceServiceStats.add(resourceStat);
            }
        }
        resourceStats.statValues.put(normalizedStatKeyValue, resourceServiceStats);
        return resourceStats;
    }

    private ComputeStats createComputeStatsForAccount(String accountComputeLink,
            AwsAccountDetailDto awsAccountDetailDto, LocalDate billMonth,
            long lastBillProcessedTimeMillis) {

        ComputeStats accountStats = new ComputeStats();
        accountStats.statValues = new ConcurrentSkipListMap<>();
        accountStats.computeLink = accountComputeLink;
        URI accountUri = UriUtils.buildUri(this.getHost(), accountComputeLink);
        long currentBillProcessedTime = TimeUnit.MILLISECONDS.toMicros(awsAccountDetailDto.billProcessedTimeMillis);

        // Account cost
        ServiceStat costStat = new ServiceStat();
        costStat.serviceReference = accountUri;
        costStat.latestValue = awsAccountDetailDto.cost;
        costStat.sourceTimeMicrosUtc = currentBillProcessedTime - AWSConstants.AGGREGATION_WINDOW_ALIGNMENT_TIME;
        costStat.unit = AWSStatsNormalizer.getNormalizedUnitValue(DIMENSION_CURRENCY_VALUE);
        costStat.name = AWSStatsNormalizer.getNormalizedStatKeyValue(AWSConstants.COST);
        accountStats.statValues.put(costStat.name, Collections.singletonList(costStat));

        // Account deleted VM count
        ServiceStat deletedVmCountStat = new ServiceStat();
        deletedVmCountStat.serviceReference = accountUri;
        deletedVmCountStat.latestValue = awsAccountDetailDto.deletedVmCount;
        deletedVmCountStat.sourceTimeMicrosUtc = currentBillProcessedTime - AWSConstants.AGGREGATION_WINDOW_ALIGNMENT_TIME;
        deletedVmCountStat.unit = PhotonModelConstants.UNIT_COST;
        deletedVmCountStat.name = PhotonModelConstants.DELETED_VM_COUNT;
        accountStats.statValues
                .put(deletedVmCountStat.name, Collections.singletonList(deletedVmCountStat));

        // Create metrics for service costs and add it at the account level.
        Map<String, List<ServiceStat>> statsForAwsService = new HashMap<>();
        if (isCurrentMonth(billMonth)) {
            for (AwsServiceDetailDto serviceDetailDto : awsAccountDetailDto.serviceDetailsMap
                    .values()) {
                statsForAwsService = createStatsForAwsService(
                        accountComputeLink, serviceDetailDto,
                        awsAccountDetailDto.billProcessedTimeMillis, lastBillProcessedTimeMillis);
                accountStats.statValues.putAll(statsForAwsService);
            }
        }
        accountStats.statValues.putAll(statsForAwsService);
        return accountStats;
    }

    private boolean isCurrentMonth(LocalDate date) {
        LocalDate dateToday = LocalDate.now(DateTimeZone.UTC);
        return date.getMonthOfYear() == dateToday.getMonthOfYear() && date.getYear() == dateToday.getYear();
    }

    private Map<String, List<ServiceStat>> createStatsForAwsService(String accountComputeLink,
            AwsServiceDetailDto serviceDetailDto, Long currentBillProcessedTimeMillis,
            Long lastBillProcessedTimeMillis) {

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
            if (usageStartTime.compareTo(lastBillProcessedTimeMillis) > 0) {
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
            if (usageStartTime.compareTo(lastBillProcessedTimeMillis) > 0) {
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
                currentBillProcessedTimeMillis, serviceDetailDto.remainingCost);
        stats.put(serviceMonthlyOtherCostMetric, serviceStats);

        // Create stats for monthly reserved recurring instance costs
        String serviceReservedRecurringCostMetric = String
                .format(AWSConstants.SERVICE_RESERVED_RECURRING_COST, serviceCode);
        serviceStats = new ArrayList<>();
        createServiceStat(accountUri, currencyUnit, serviceStats,
                serviceReservedRecurringCostMetric, currentBillProcessedTimeMillis, serviceDetailDto.reservedRecurringCost);
        stats.put(serviceReservedRecurringCostMetric, serviceStats);

        return stats;
    }

    /**
     * AWS bills are read, processed and persisted based on the records which were processed in
     * the last collection cycle. This method will persist the record ID which was successfully
     * processed in the current collection cycle. The default value of this property is 0 (Zero),
     * in which case all the records in the bill for the month being processed will be processed.
     * The bill processed time is stored as a resource metric, which are wiped off after a certain
     * duration of time. Hence, the bill processed time should be stored on every run, independent
     * of whether of a change in its value.
     * @param statsData
     * @param accountComputeState compute state of the account
     *
     */
    protected ServiceStat createBillProcessedTimeStat(AWSCostStatsCreationContext statsData,
            ComputeState accountComputeState) {

        LocalDate currentMonth = getFirstDayOfCurrentMonth();
        String accountId = statsData.computeDesc.customProperties
                .getOrDefault(AWSConstants.AWS_ACCOUNT_ID_KEY, null);

        ServiceStat billProcessedTimeStat = new ServiceStat();

        if (accountId != null) {

            billProcessedTimeStat.serviceReference = UriUtils.buildUri(this.getHost(),
                    accountComputeState.documentSelfLink);
            billProcessedTimeStat.latestValue = statsData.accountsHistoricalDetailsMap
                    .get(currentMonth)
                    .get(accountId).billProcessedTimeMillis;
            billProcessedTimeStat.sourceTimeMicrosUtc = TimeUnit.MILLISECONDS
                    .toMicros(statsData.accountsHistoricalDetailsMap.get(currentMonth)
                            .get(accountId).billProcessedTimeMillis);
            billProcessedTimeStat.unit = PhotonModelConstants.UNIT_MILLISECONDS;
            billProcessedTimeStat.name = AWSConstants.AWS_ACCOUNT_BILL_PROCESSED_TIME_MILLIS;


        } else {
            logWarning(() -> "Primary account ID not found. Not updating the processed time for"
                    + " this collection cycle.");
        }
        return billProcessedTimeStat;

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
        return ((t) -> AdapterUtils.sendFailurePatchToProvisioningTask(this,
                statsData.statsRequest.taskReference, t));
    }

    /**
     * Creates an Operation to get the billProcessedTime of an account
     * @param context has the context for this running thread, is used to populate the billProcessedTime
     * @param accountComputeState has the account compute state being processed
     * @param accountId the AWS account identifier of the account being processed
     * @return the operation to get the billProcessedTime for the entry passed
     */
    private Operation createBillProcessedTimeOperation(
            AWSCostStatsCreationContext context, ComputeState accountComputeState,
            String accountId) {

        Query.Builder builder = Query.Builder.create(Occurance.SHOULD_OCCUR);
        builder.addKindFieldClause(ResourceMetricsService.ResourceMetrics.class);
        builder.addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                UriUtils.buildUriPath(ResourceMetricsService.FACTORY_LINK,
                        UriUtils.getLastPathSegment(accountComputeState.documentSelfLink)),
                QueryTask.QueryTerm.MatchType.PREFIX);
        builder.addRangeClause(QueryTask.QuerySpecification
                        .buildCompositeFieldName(
                                ResourceMetricsService.ResourceMetrics.FIELD_NAME_ENTRIES,
                                AWSConstants.AWS_ACCOUNT_BILL_PROCESSED_TIME_MILLIS),
                QueryTask.NumericRange
                        .createDoubleRange(Double.MIN_VALUE, Double.MAX_VALUE, true, true));

        return Operation.createPost(getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS)
                .setBody(QueryTask.Builder.createDirectTask()
                        .addOption(QueryOption.SORT)
                        .addOption(QueryOption.TOP_RESULTS)
                        // No-op in photon-model. Required for special handling of immutable documents.
                        // This will prevent Lucene from holding the full result set in memory.
                        .addOption(QueryOption.INCLUDE_ALL_VERSIONS)
                        .addOption(QueryOption.EXPAND_CONTENT)
                        .orderDescending(ServiceDocument.FIELD_NAME_SELF_LINK,
                                ServiceDocumentDescription.TypeName.STRING)
                        .setResultLimit(1)
                        .setQuery(builder.build()).build())
                .setCompletion((operation, exception) -> {
                    if (exception != null) {
                        logWarning(() -> String.format("Failed to get bill processed time for"
                                + " account: %s", accountComputeState.documentSelfLink));
                        getFailureConsumer(context).accept(exception);
                        return;
                    }
                    QueryTask body = operation.getBody(QueryTask.class);
                    Collection<Object> values = body.results.documents.values();
                    if (!values.isEmpty()) {
                        ResourceMetricsService.ResourceMetrics rawResourceMetrics = Utils
                                .fromJson(values.iterator().next(),
                                        ResourceMetricsService.ResourceMetrics.class);
                        Double accountBillProcessedTime = rawResourceMetrics.entries
                                .getOrDefault(AWSConstants.AWS_ACCOUNT_BILL_PROCESSED_TIME_MILLIS,
                                        0d);
                        context.accountIdToBillProcessedTime
                                .put(accountId, accountBillProcessedTime.longValue());
                    }
                });
    }

    private void joinOperationAndSendRequest(AWSCostStatsCreationContext context,
            AWSCostStatsCreationStages next, List<Operation> queryOps) {
        OperationJoin.create(queryOps).setCompletion((operationMap, exception) -> {
            if (exception != null && !exception.isEmpty()) {
                Throwable firstException = exception.values().iterator().next();
                getFailureConsumer(context).accept(firstException);
                return;
            }
            context.stage = next;
            handleCostStatsCreationRequest(context);
        }).sendWith(this, AWSConstants.OPERATION_BATCH_SIZE);
    }
}
