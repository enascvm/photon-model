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

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.ACCOUNT_IS_AUTO_DISCOVERED;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_LINKED_ACCOUNT_IDS;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
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
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.amazonaws.event.ProgressEvent;
import com.amazonaws.event.ProgressEventType;
import com.amazonaws.event.ProgressListener;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.transfer.TransferManager;
import org.apache.commons.collections.CollectionUtils;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

import com.vmware.photon.controller.model.adapterapi.ComputeStatsRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse.ComputeStats;
import com.vmware.photon.controller.model.adapters.aws.dto.AwsAccountDetailDto;
import com.vmware.photon.controller.model.adapters.aws.dto.AwsResourceDetailDto;
import com.vmware.photon.controller.model.adapters.aws.dto.AwsServiceDetailDto;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AwsClientType;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSMissingResourcesEnumerationService;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSCsvBillParser;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSStatsNormalizer;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService.SingleResourceStatsCollectionTaskState;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService.SingleResourceTaskCollectionStage;
import com.vmware.photon.controller.model.util.ClusterUtil;
import com.vmware.photon.controller.model.util.ClusterUtil.ServiceTypeCluster;
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
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Service to gather AWS Cost related stats
 */

public class AWSCostStatsService extends StatelessService {

    public static final String SELF_LINK = AWSUriPaths.AWS_COST_STATS_ADAPTER;

    public static final String TEMP_DIR_LOCATION = "java.io.tmpdir";
    public static final String DIMENSION_CURRENCY_VALUE = "USD";
    public static final String BATCH_SIZE_KEY = "aws.costCollection.stats.batchSize";
    public static final int DEFAULT_BATCH_SIZE = 500;
    // Past months for which bills need to be collected, default is 11, excluding current month's bill.
    protected static final String BILLS_BACK_IN_TIME_MONTHS_KEY = "aws.costsCollection.backInTime.months";

    private AWSClientManager clientManager;
    private ExecutorService executor;

    protected enum AWSCostStatsCreationStages {
        ACCOUNT_DETAILS, DOWNLOAD_PARSE_CREATE_STATS, QUERY_LOCAL_RESOURCES, QUERY_LINKED_ACCOUNTS,
        QUERY_BILL_PROCESSED_TIME, CHECK_BILL_BUCKET_CONFIG, RESERVED_INSTANCES_PLANS_COLLECTION,
        CREATE_MISSING_RESOURCES_COMPUTES, FINISH
    }

    public AWSCostStatsService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        this.clientManager = AWSClientManagerFactory.getClientManager(AwsClientType.S3);
    }

    protected class AWSCostStatsCreationContext {
        protected ComputeStateWithDescription computeDesc;
        protected AuthCredentialsService.AuthCredentialsServiceState parentAuth;
        protected ComputeStatsRequest statsRequest;
        protected ComputeStatsResponse statsResponse;
        protected TransferManager s3Client;
        protected List<String> ignorableInvoiceCharge;
        protected AWSCostStatsCreationStages stage;

        // This map is one to many because an AWS instance will have different compute links if the same account
        // is added by different users.
        protected Map<String, List<String>> awsInstanceLinksById;
        // This map is one to many because a distinct compute state will be present for each region of an AWS account.
        protected Map<String, List<ComputeState>> awsAccountIdToComputeStates;
        protected OperationContext opContext;
        protected Map<String, Long> accountIdToBillProcessedTime;
        // Holds the month for which bill has to be downloaded and processed
        protected LocalDate billMonthToDownload;
        protected TaskManager taskManager;
        // aws accountId for which this adapter is running
        protected String accountId;

        protected boolean isSecondPass = false;

        protected AWSCostStatsCreationContext(ComputeStatsRequest statsRequest) {
            this.statsRequest = statsRequest;
            this.stage = AWSCostStatsCreationStages.ACCOUNT_DETAILS;
            this.ignorableInvoiceCharge = new ArrayList<>();
            this.awsInstanceLinksById = new ConcurrentHashMap<>();
            this.awsAccountIdToComputeStates = new ConcurrentHashMap<>();
            this.accountIdToBillProcessedTime = new ConcurrentHashMap<>();
            this.statsResponse = new ComputeStatsResponse();
            this.statsResponse.statsList = new ArrayList<>();
            this.opContext = OperationContext.getOperationContext();
        }
    }

    @Override
    public void handleStart(Operation start) {
        this.executor = getHost().allocateExecutor(this);
        super.handleStart(start);
    }

    @Override
    public void handleStop(Operation delete) {
        AWSClientManagerFactory.returnClientManager(this.clientManager, AwsClientType.S3);
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
        TaskManager taskManager = new TaskManager(this, statsRequest.taskReference,
                statsRequest.resourceLink());
        if (statsRequest.isMockRequest) {
            // patch status to parent task
            taskManager.finishTask();
            return;
        }
        AWSCostStatsCreationContext statsData = new AWSCostStatsCreationContext(statsRequest);
        statsData.taskManager = taskManager;
        handleCostStatsCreationRequest(statsData);
    }

    protected void handleCostStatsCreationRequest(AWSCostStatsCreationContext statsData) {
        try {
            switch (statsData.stage) {
            case ACCOUNT_DETAILS:
                getAccountDescription(statsData, AWSCostStatsCreationStages.RESERVED_INSTANCES_PLANS_COLLECTION);
                break;
            case RESERVED_INSTANCES_PLANS_COLLECTION:
                startReservedInstancesPlansCollection(statsData, AWSCostStatsCreationStages.CHECK_BILL_BUCKET_CONFIG);
                break;
            case CHECK_BILL_BUCKET_CONFIG:
                checkBillBucketConfig(statsData, AWSCostStatsCreationStages.QUERY_LINKED_ACCOUNTS);
                break;
            case QUERY_LINKED_ACCOUNTS:
                queryLinkedAccounts(statsData, AWSCostStatsCreationStages.QUERY_BILL_PROCESSED_TIME);
                break;
            case QUERY_BILL_PROCESSED_TIME:
                queryBillProcessedTime(statsData, AWSCostStatsCreationStages.QUERY_LOCAL_RESOURCES);
                break;
            case QUERY_LOCAL_RESOURCES:
                queryInstances(statsData, AWSCostStatsCreationStages.DOWNLOAD_PARSE_CREATE_STATS);
                break;
            case DOWNLOAD_PARSE_CREATE_STATS:
                scheduleDownload(statsData, AWSCostStatsCreationStages.CREATE_MISSING_RESOURCES_COMPUTES);
                break;
            case CREATE_MISSING_RESOURCES_COMPUTES:
                createMissingResourcesComputes(statsData, AWSCostStatsCreationStages.FINISH);
                break;
            case FINISH:
                postAccumulatedCostStats(statsData, true);
                break;
            default:
                String errorMessage = String
                        .format("Unknown AWS Cost Stats enumeration stage %s", statsData.stage);
                getFailureConsumer(statsData).accept(new Exception(errorMessage));
                break;
            }
        } catch (Exception e) {
            getFailureConsumer(statsData).accept(e);
            return;
        }
    }

    protected void startReservedInstancesPlansCollection(AWSCostStatsCreationContext statsData,
            AWSCostStatsCreationStages next) {

        Operation op = Operation.createPost(
                UriUtils.buildUri(getHost(), AWSReservedInstancePlanService.SELF_LINK))
                .setBody(statsData.computeDesc.documentSelfLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        log(Level.SEVERE,
                                "Error while requesting reserved instances plans collection for compute "
                                        + statsData.computeDesc.documentSelfLink);
                    }
                });
        sendRequest(op);
        statsData.stage = next;
        handleCostStatsCreationRequest(statsData);
    }

    private void createMissingResourcesComputes(AWSCostStatsCreationContext context,
            AWSCostStatsCreationStages next) {

        String linkedAccountsCsv = context.computeDesc.customProperties.get(AWS_LINKED_ACCOUNT_IDS);
        if (context.isSecondPass || linkedAccountsCsv == null || linkedAccountsCsv.isEmpty()) {
            context.stage = next;
            handleCostStatsCreationRequest(context);
            return;
        }

        // Following function tells whether the computes of a particular account contain an
        // auto-discovered compute under the primary account's endpoint.
        Function<List<ComputeState>, Boolean> hasAutoDiscoveredCompute = (computes) -> {
            if (CollectionUtils.isEmpty(computes)) {
                return false;
            }
            return computes.stream()
                    .filter(c -> context.computeDesc.endpointLink.equals(c.endpointLink)
                            && Boolean.valueOf(c.customProperties.get(ACCOUNT_IS_AUTO_DISCOVERED)))
                    .count() != 0;
        };
        Set<String> existingLinkedAccountIds = context.awsAccountIdToComputeStates.entrySet()
                .stream().filter(e -> hasAutoDiscoveredCompute.apply(e.getValue()))
                .map(e -> e.getKey())
                .collect(Collectors.toCollection(HashSet::new));

        List<String> missingLinkedAccountIds = Stream.of(linkedAccountsCsv.split(","))
                .filter(id -> !existingLinkedAccountIds.contains(id))
                .collect(Collectors.toList());

        if (CollectionUtils.isEmpty(missingLinkedAccountIds)) {
            context.stage = next;
            handleCostStatsCreationRequest(context);
            return;
        }

        AWSMissingResourcesEnumerationService.Request request = new
                AWSMissingResourcesEnumerationService.Request();
        request.missingLinkedAccountIds = missingLinkedAccountIds;
        request.primaryAccountCompute = context.computeDesc;

        Operation op = Operation.createPost(
                UriUtils.buildUri(getHost(), AWSMissingResourcesEnumerationService.SELF_LINK))
                .setBody(request)
                .setCompletion((o, e) -> {

                    // Even if the operation fails, we continue with the second pass.
                    // The exception might indicate failure for creation of few linked account
                    // computes but not all. So we execute the second pass to model the cost of
                    // atleast the ones which were created.

                    context.stage = AWSCostStatsCreationStages.QUERY_LINKED_ACCOUNTS;
                    context.isSecondPass = true;
                    context.billMonthToDownload = null;
                    context.awsInstanceLinksById.clear();
                    handleCostStatsCreationRequest(context);
                });
        sendRequest(op);
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
                postAccumulatedCostStats(statsData, true);
                return;
            }
            statsData.accountId = accountId;
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
                    postAccumulatedCostStats(statsData, true);
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

    protected void checkBillBucketConfig(AWSCostStatsCreationContext statsData,
            AWSCostStatsCreationStages next) {
        this.executor.submit(() -> {
            OperationContext.restoreOperationContext(statsData.opContext);
            statsData.s3Client = this.clientManager.getOrCreateS3AsyncClient(statsData.parentAuth,
                    null, this, getFailureConsumer(statsData));
            if (statsData.s3Client == null) {
                logWarning(() -> String.format("Couldn't get S3 client while collecting stats for "
                        + "%s", statsData.computeDesc.documentSelfLink));
                postAccumulatedCostStats(statsData, true);
                return;
            }
            String billsBucketName = statsData.computeDesc.customProperties
                    .getOrDefault(AWSConstants.AWS_BILLS_S3_BUCKET_NAME_KEY, null);
            try {
                if (billsBucketName == null) {
                    billsBucketName = AWSUtils.autoDiscoverBillsBucketName(
                            statsData.s3Client.getAmazonS3Client(), statsData.accountId);
                    if (billsBucketName == null) {
                        logWarning(() -> String.format("Bills Bucket name is not configured for "
                                        + "account '%s'. Not collecting cost stats.",
                                statsData.computeDesc.documentSelfLink));
                        postAccumulatedCostStats(statsData, true);
                        return;
                    } else {
                        setCustomProperty(statsData, AWSConstants.AWS_BILLS_S3_BUCKET_NAME_KEY,
                                billsBucketName);
                    }
                }
            } catch (Exception e) {
                getFailureConsumer(statsData).accept(e);
                return;
            }
            statsData.stage = next;
            handleCostStatsCreationRequest(statsData);
        });
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
                .addFieldClause(ComputeState.FIELD_NAME_TYPE, ComputeType.VM_HOST)
                .build();
        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .setQuery(awsAccountsQuery).build();
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
                            .filter(cs -> cs.endpointLink != null)
                            .collect(Collectors.toList());
                    queryResultConsumer.accept(accountComputeStates);
                });
    }

    /**
     * Schedules downloading and parsing of AWS bills. Past month bills are collected depending upon
     * upon the value of BILLS_BACK_IN_TIME_MONTHS_KEY as a system property. This property decides how
     * many past bills should be collected, processed and parsed.
     * @param context context for the current running thread
     */
    protected void scheduleDownload(AWSCostStatsCreationContext context,
            AWSCostStatsCreationStages next) {
        this.executor.submit(() -> {
            OperationContext.restoreOperationContext(context.opContext);
            logFine(() -> String.format("Account: %s was last processed at: %s.", context.accountId,
                    context.accountIdToBillProcessedTime.get(context.accountId)));
            try {
                if (context.billMonthToDownload == null) {
                    populateBillMonthToProcess(context);
                }
                LocalDate firstDayOfCurrentMonth = getFirstDayOfCurrentMonth();
                if (context.billMonthToDownload.compareTo(firstDayOfCurrentMonth) <= 0) {
                    String billsBucketName = context.computeDesc.customProperties
                            .get(AWSConstants.AWS_BILLS_S3_BUCKET_NAME_KEY);
                    downloadParseAndCreateStats(context, billsBucketName);
                } else {
                    context.stage = next;
                    handleCostStatsCreationRequest(context);
                }
            } catch (Exception e) {
                getFailureConsumer(context).accept(e);
            }
        });
    }

    /**
     * Will set AWSCostStatsCreationContext.billMonthToDownload to infer which month's bill has to be downloaded
     * to begin the AWS data collection. Bills will be downloaded starting from this month up to
     * the current month. These number of months are configurable, specified by
     * the SystemProperty {@code BILLS_BACK_IN_TIME_MONTHS_KEY}
     * @param context has the context for this running thread, is used to populate the billProcessedTime
     */
    protected void populateBillMonthToProcess(AWSCostStatsCreationContext context) {

        Long billProcessedTime = context.accountIdToBillProcessedTime
                .getOrDefault(context.accountId, 0L);
        if (context.isSecondPass) {
            // This override ensures that we download previous month bills during
            // 2nd pass for auto-discovered linked accounts.
            billProcessedTime = 0L;
        }
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
        logFine(() -> String.format("Downloading AWS account %s bills since: %s.",
                context.accountId, context.billMonthToDownload));
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
            Operation.createGet(this, nextPageLink).setCompletion((o, ex) -> {
                if (ex != null) {
                    getFailureConsumer(context).accept(ex);
                    return;
                }
                QueryTask queryTask = o.getBody(QueryTask.class);
                Map<String, List<ComputeState>> instancesById = queryTask.results.documents.values()
                        .stream()
                        .map(s -> Utils.fromJson(s, ComputeState.class))
                        .collect(Collectors.groupingBy(c -> c.id));
                Map<String, List<String>> instanceLinksById = instancesById.entrySet().stream()
                        .collect(Collectors.toMap(Entry::getKey,
                                e -> e.getValue().stream().map(cs -> cs.documentSelfLink)
                                        .collect(Collectors.toList())));
                instanceLinksById.forEach(
                        (k, v) -> context.awsInstanceLinksById.merge(k, v, (list1, list2) -> {
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
            String accountId = entry.getKey();
            List<ComputeState> accountComputes = entry.getValue();
            if (CollectionUtils.isEmpty(accountComputes)) {
                // This will happen for linked accounts not configured in the system; skip.
                continue;
            }
            findRootAccountComputeStateByEndpoint(accountComputes).values()
                    .forEach(accountComputeState -> {
                        if (!context.accountIdToBillProcessedTime.containsKey(accountId)) {
                            queryOps.add(createBillProcessedTimeOperation(
                                    context, accountComputeState, accountId));
                        }
                    });
        }
        joinOperationAndSendRequest(context, next, queryOps);
    }

    protected void setLinkedAccountIds(AWSCostStatsCreationContext context, Map<String, AwsAccountDetailDto>
            awsAccountDetailDtoMap) {
        Map<String, String> primaryAccountProps = context.computeDesc.customProperties;
        String linkedAccountIds = awsAccountDetailDtoMap.keySet().stream()
                .filter(accountId -> accountId != null && !accountId.equals(context.accountId))
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
        // Construct a list of query operations, one for each account ID to query the
        // corresponding compute states and populate in the context object.
        String[] linkedAccountIds = context.computeDesc.customProperties
                .getOrDefault(AWSConstants.AWS_LINKED_ACCOUNT_IDS, "")
                .split(",");

        List<Operation> queryOps = Arrays.stream(linkedAccountIds)
                .filter(accountId -> accountId != null && !accountId.isEmpty())
                .map(accountId -> createQueryForComputeStatesByAccount(context, accountId,
                        (accountComputes) -> context.awsAccountIdToComputeStates
                                .put(accountId, accountComputes)))
                .collect(Collectors.toList());
        joinOperationAndSendRequest(context, next, queryOps);
    }

    private LocalDate getFirstDayOfCurrentMonth() {
        return LocalDate.now(DateTimeZone.UTC).withDayOfMonth(1);
    }

    protected void createAccountStats(AWSCostStatsCreationContext statsData, LocalDate billMonth,
            AwsAccountDetailDto awsAccountDetailDto) {
        Consumer<ComputeState> accountStatsProcessor = (accountComputeState) -> {
            ComputeStats accountStats = new ComputeStats();
            accountStats.statValues = new ConcurrentHashMap<>();
            accountStats.computeLink = accountComputeState.documentSelfLink;
            if (isBillUpdated(statsData, awsAccountDetailDto)) {
                long timestamp = awsAccountDetailDto.billProcessedTimeMillis
                        - AWSConstants.AGGREGATION_WINDOW_ALIGNMENT_TIME;
                ServiceStat costStat = createStat(
                        AWSStatsNormalizer.getNormalizedUnitValue(DIMENSION_CURRENCY_VALUE),
                        AWSStatsNormalizer.getNormalizedStatKeyValue(AWSConstants.COST),
                        timestamp, awsAccountDetailDto.cost);
                accountStats.statValues.put(costStat.name, Collections.singletonList(costStat));
            }
            if (isCurrentMonth(billMonth)) {
                ServiceStat billProcessedTimeStat = createStat(
                        PhotonModelConstants.UNIT_MILLISECONDS,
                        AWSConstants.AWS_ACCOUNT_BILL_PROCESSED_TIME_MILLIS,
                        awsAccountDetailDto.billProcessedTimeMillis,
                        awsAccountDetailDto.billProcessedTimeMillis);
                accountStats.statValues.put(billProcessedTimeStat.name,
                        Collections.singletonList(billProcessedTimeStat));
            }
            if (!accountStats.statValues.isEmpty()) {
                statsData.statsResponse.statsList.add(accountStats);
            }
        };
        processAccountStats(statsData, billMonth, awsAccountDetailDto, accountStatsProcessor);
        if (isCurrentMonth(billMonth) && (awsAccountDetailDto.billProcessedTimeMillis != 0L)) {
            statsData.accountIdToBillProcessedTime.put(awsAccountDetailDto.id,
                    awsAccountDetailDto.billProcessedTimeMillis);
        }
    }

    protected void createServiceStatsForAccount(AWSCostStatsCreationContext statsData,
            LocalDate billMonth, AwsAccountDetailDto awsAccountDetailDto) {
        Consumer<ComputeState> serviceStatsProcessor = (accountComputeState) -> {
            ComputeStats awsServiceStats = new ComputeStats();
            awsServiceStats.statValues = new ConcurrentHashMap<>();
            awsServiceStats.computeLink = accountComputeState.documentSelfLink;
            if (isBillUpdated(statsData, awsAccountDetailDto)) {
                for (AwsServiceDetailDto serviceDetailDto : awsAccountDetailDto.serviceDetailsMap
                        .values()) {
                    Map<String, List<ServiceStat>> statsForAwsService = createStatsForAwsService(
                            serviceDetailDto, awsAccountDetailDto.billProcessedTimeMillis);
                    awsServiceStats.statValues.putAll(statsForAwsService);
                }
                if (!awsServiceStats.statValues.isEmpty()) {
                    statsData.statsResponse.statsList.add(awsServiceStats);
                }
            }
        };
        processAccountStats(statsData, billMonth, awsAccountDetailDto, serviceStatsProcessor);
    }

    private void processAccountStats(AWSCostStatsCreationContext statsData, LocalDate billMonth,
            AwsAccountDetailDto awsAccountDetailDto, Consumer<ComputeState> processor) {
        List<ComputeState> accountComputeStates = statsData.awsAccountIdToComputeStates
                .getOrDefault(awsAccountDetailDto.id, null);
        if ((accountComputeStates == null) || accountComputeStates.isEmpty()) {
            logFine(() -> "AWS account with ID '%s' is not configured yet. Not creating cost"
                    + " metrics for the same.");
            return;
        }
        // We use root compute state representing this account to save the account level stats
        Map<String, ComputeState> rootComputesByEndpoint = findRootAccountComputeStateByEndpoint(
                accountComputeStates);
        for (ComputeState accountComputeState : rootComputesByEndpoint.values()) {
            logFine(() -> String.format("Processing and persisting account level stats: %s "
                    + "for month: %s.", accountComputeState.documentSelfLink, billMonth));
            processor.accept(accountComputeState);
        }
    }

    protected void createResourceStatsForAccount(AWSCostStatsCreationContext statsData,
            AwsAccountDetailDto awsAccountDetailDto) {

        if (!isBillUpdated(statsData, awsAccountDetailDto)) {
            return;
        }
        long lastBillProcessedTimeMillis = statsData.accountIdToBillProcessedTime
                .getOrDefault(awsAccountDetailDto.id, 0L);
        // create resource stats for only live EC2 instances that exist in system
        Map<String, AwsServiceDetailDto> serviceDetails = awsAccountDetailDto.serviceDetailsMap;
        for (String service : serviceDetails.keySet()) {
            if (!service.equalsIgnoreCase(AWSCsvBillParser.AwsServices.ec2.getName())) {
                // Instance Costs are present only with EC2 service.
                continue;
            }
            Map<String, AwsResourceDetailDto> resourceDetailsMap = serviceDetails.get(service)
                    .resourceDetailsMap;
            if (resourceDetailsMap == null) {
                continue;
            }
            for (Entry<String, AwsResourceDetailDto> entry : resourceDetailsMap.entrySet()) {
                String resourceId = entry.getKey();
                AwsResourceDetailDto resourceDetails = entry.getValue();
                if ((resourceDetails == null) || (resourceDetails.directCosts == null)) {
                    continue;
                }
                List<String> computeStateLinks = statsData.awsInstanceLinksById
                        .getOrDefault(resourceId, Collections.emptyList());
                for (String resourceComputeStateLink : computeStateLinks) {
                    ComputeStats resourceStats = createStatsForResource(resourceComputeStateLink,
                            resourceDetails, lastBillProcessedTimeMillis);
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

    protected void postAccumulatedCostStats(AWSCostStatsCreationContext context,
            boolean isFinalBatch) {
        postAccumulatedCostStats(context, isFinalBatch, false);
    }

    protected void postAccumulatedCostStats(AWSCostStatsCreationContext statsData,
            boolean isFinalBatch, boolean shouldForcePost) {
        int batchSize = Integer.getInteger(BATCH_SIZE_KEY, DEFAULT_BATCH_SIZE);
        if (!shouldForcePost && !isFinalBatch && statsData.statsResponse.statsList.size() < batchSize) {
            return;
        }
        SingleResourceStatsCollectionTaskState respBody = new SingleResourceStatsCollectionTaskState();
        respBody.taskStage = SingleResourceTaskCollectionStage.valueOf(statsData.statsRequest.nextStage);
        respBody.statsAdapterReference = UriUtils.buildUri(getHost(), SELF_LINK);
        respBody.statsList = statsData.statsResponse.statsList;
        respBody.computeLink = statsData.computeDesc.documentSelfLink;
        respBody.isFinalBatch = isFinalBatch;
        sendRequest(Operation.createPatch(statsData.statsRequest.taskReference).setBody(respBody)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        getFailureConsumer(statsData).accept(e);
                        return;
                    }
                }));
        statsData.statsResponse.statsList = new ArrayList<>();
    }

    private void downloadParseAndCreateStats(AWSCostStatsCreationContext statsData, String awsBucketName) throws IOException {
        try {
            // Creating a working directory for downloading and processing the bill
            final Path workingDirPath = Paths.get(System.getProperty(TEMP_DIR_LOCATION),
                    UUID.randomUUID().toString());
            Files.createDirectories(workingDirPath);

            AWSCsvBillParser parser = new AWSCsvBillParser();
            final String csvBillZipFileName = parser
                    .getCsvBillFileName(statsData.billMonthToDownload, statsData.accountId, true);
            Path csvBillZipFilePath = Paths.get(workingDirPath.toString(), csvBillZipFileName);
            ProgressListener listener = new ProgressListener() {
                @Override
                public void progressChanged(ProgressEvent progressEvent) {
                    try {
                        ProgressEventType eventType = progressEvent.getEventType();
                        if (ProgressEventType.TRANSFER_COMPLETED_EVENT.equals(eventType)) {
                            OperationContext.restoreOperationContext(statsData.opContext);
                            LocalDate billMonth = new LocalDate(
                                    statsData.billMonthToDownload.getYear(),
                                    statsData.billMonthToDownload.getMonthOfYear(), 1);
                            logFine(() -> String.format("Processing AWS bill for account: %s for "
                                    + "the month: %s.", statsData.accountId, billMonth));

                            parser.parseDetailedCsvBill(statsData.ignorableInvoiceCharge,
                                    csvBillZipFilePath, statsData.accountIdToBillProcessedTime,
                                    getHourlyStatsConsumer(billMonth, statsData),
                                    getMonthlyStatsConsumer(billMonth, statsData));

                            deleteTempFiles();
                            // Continue downloading and processing the bills for past and current
                            // months' bills
                            statsData.billMonthToDownload = statsData.billMonthToDownload
                                    .plusMonths(1);
                            handleCostStatsCreationRequest(statsData);
                        } else if (ProgressEventType.TRANSFER_FAILED_EVENT.equals(eventType)) {
                            deleteTempFiles();
                            billDownloadFailureHandler(statsData, awsBucketName,
                                    new IOException("Download of AWS CSV Bill '" +
                                            csvBillZipFileName + "' failed."));
                        }
                    } catch (Exception exception) {
                        deleteTempFiles();
                        billDownloadFailureHandler(statsData, awsBucketName, exception);
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
            };
            GetObjectRequest getObjectRequest = new GetObjectRequest(awsBucketName,
                    csvBillZipFileName).withGeneralProgressListener(listener);
            statsData.s3Client.download(getObjectRequest, csvBillZipFilePath.toFile());
        } catch (AmazonS3Exception s3Exception) {
            billDownloadFailureHandler(statsData, awsBucketName, s3Exception);
        }
    }

    /**
     * Consumes a single batch of hourly stats of parsed bill rows and creates stats
     */
    protected Consumer<Map<String, AwsAccountDetailDto>> getMonthlyStatsConsumer(
            LocalDate billMonth, AWSCostStatsCreationContext statsData) {
        return (accountDetailDtoMap) -> {
            accountDetailDtoMap.values().forEach(accountDto -> {
                createAccountStats(statsData, billMonth, accountDto);
            });
            boolean isCurrentMonth = isCurrentMonth(billMonth);
            if (isCurrentMonth) {
                setLinkedAccountIds(statsData, accountDetailDtoMap);
                postAccumulatedCostStats(statsData, false, true);
            } else {
                postAccumulatedCostStats(statsData, false);
            }
        };
    }

    /**
     * Consumes the monthly stats from bill rows and creates stats
     */
    protected Consumer<Map<String, AwsAccountDetailDto>> getHourlyStatsConsumer(LocalDate billMonth,
            AWSCostStatsCreationContext statsData) {
        return (accountDetailDtoMap) -> {
            accountDetailDtoMap.values().forEach(accountDto -> {
                createResourceStatsForAccount(statsData, accountDto);
                createServiceStatsForAccount(statsData, billMonth,
                        accountDto);
                accountDto.serviceDetailsMap.clear();
            });
            postAccumulatedCostStats(statsData, false);
        };
    }

    private void billDownloadFailureHandler(
            AWSCostStatsCreationContext statsData, String awsBucketName, Exception exception) {
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
                            + " following months. %s", statsData.accountId,
                    statsData.billMonthToDownload.getYear(),
                    statsData.billMonthToDownload.getMonthOfYear(), awsBucketName, error.toString
                            ()));
            // Continue downloading and processing the bills for following month's bill
            statsData.billMonthToDownload = statsData.billMonthToDownload.plusMonths(1);
            OperationContext.restoreOperationContext(statsData.opContext);
            handleCostStatsCreationRequest(statsData);

        }
    }

    private ComputeStats createStatsForResource(String resourceComputeLink,
            AwsResourceDetailDto resourceDetails, Long billProcessedTimeMillis) {

        ComputeStats resourceStats = new ComputeStats();
        resourceStats.statValues = new ConcurrentSkipListMap<>();
        resourceStats.computeLink = resourceComputeLink;
        List<ServiceStat> resourceServiceStats = new ArrayList<>();
        String normalizedStatKeyValue = AWSStatsNormalizer
                .getNormalizedStatKeyValue(AWSConstants.COST);
        for (Entry<Long, Double> cost : resourceDetails.directCosts.entrySet()) {
            ServiceStat resourceStat = createStat(
                    AWSStatsNormalizer.getNormalizedUnitValue(DIMENSION_CURRENCY_VALUE),
                    normalizedStatKeyValue, cost.getKey(), cost.getValue());
            resourceServiceStats.add(resourceStat);
        }
        resourceStats.statValues.put(normalizedStatKeyValue, resourceServiceStats);

        // Create a stat to represent how many hours a resource ran as reserve instance
        String normalizedReservedInstanceStatKey = AWSStatsNormalizer
                .getNormalizedStatKeyValue(AWSConstants.RESERVED_INSTANCE_DURATION);
        List<ServiceStat> reservedInstanceStats = new ArrayList<>();
        for (Entry<Long, Double> entry : resourceDetails.hoursAsReservedPerDay.entrySet()) {
            Long usageStartTime = entry.getKey();
            if (usageStartTime.compareTo(billProcessedTimeMillis) > 0) {
                ServiceStat resourceStat = createStat(
                        AWSStatsNormalizer.getNormalizedUnitValue(AWSConstants.UNIT_HOURS),
                        normalizedReservedInstanceStatKey, entry.getKey(), entry.getValue());
                reservedInstanceStats.add(resourceStat);
            }
        }
        logFine(() -> String.format("Reserved Instances stats count for %s is %d",
                resourceComputeLink, reservedInstanceStats.size()));
        if (reservedInstanceStats.size() > 0) {
            resourceStats.statValues.put(normalizedReservedInstanceStatKey, reservedInstanceStats);
        }

        return resourceStats;
    }

    private boolean isCurrentMonth(LocalDate date) {
        LocalDate dateToday = LocalDate.now(DateTimeZone.UTC);
        return date.getMonthOfYear() == dateToday.getMonthOfYear() && date.getYear() == dateToday
                .getYear();
    }

    private Map<String, List<ServiceStat>> createStatsForAwsService(
            AwsServiceDetailDto serviceDetailDto, Long currentBillProcessedTimeMillis) {
        String currencyUnit = AWSStatsNormalizer.getNormalizedUnitValue(DIMENSION_CURRENCY_VALUE);
        // remove any spaces in the service name.
        String serviceCode = serviceDetailDto.id.replaceAll(" ", "");
        Map<String, List<ServiceStat>> stats = new HashMap<>();

        // Create stats for hourly resource cost
        List<ServiceStat> serviceStats = new ArrayList<>();
        String serviceResourceCostMetric = String
                .format(AWSConstants.SERVICE_RESOURCE_COST, serviceCode);
        for (Entry<Long, Double> cost : serviceDetailDto.directCosts.entrySet()) {
            ServiceStat resourceCostStat = createStat(currencyUnit,
                    serviceResourceCostMetric, cost.getKey(), cost.getValue());
            serviceStats.add(resourceCostStat);
        }
        if (!serviceStats.isEmpty()) {
            stats.put(serviceResourceCostMetric, serviceStats);
        }

        // Create stats for hourly other costs
        serviceStats = new ArrayList<>();
        String serviceOtherCostMetric = String.format(AWSConstants.SERVICE_OTHER_COST, serviceCode);
        for (Entry<Long, Double> cost : serviceDetailDto.otherCosts.entrySet()) {
            ServiceStat otherCostStat = createStat(currencyUnit, serviceOtherCostMetric,
                    cost.getKey(), cost.getValue());
            serviceStats.add(otherCostStat);
        }
        if (!serviceStats.isEmpty()) {
            stats.put(serviceOtherCostMetric, serviceStats);
        }

        // Create stats for monthly other costs
        String serviceMonthlyOtherCostMetric = String
                .format(AWSConstants.SERVICE_MONTHLY_OTHER_COST, serviceCode);
        serviceStats = new ArrayList<>();
        ServiceStat monthlyOtherCostStat = createStat(currencyUnit,
                serviceMonthlyOtherCostMetric, currentBillProcessedTimeMillis,
                serviceDetailDto.remainingCost);
        serviceStats.add(monthlyOtherCostStat);
        stats.put(serviceMonthlyOtherCostMetric, serviceStats);

        // Create stats for monthly reserved recurring instance costs
        String serviceReservedRecurringCostMetric = String
                .format(AWSConstants.SERVICE_RESERVED_RECURRING_COST, serviceCode);
        serviceStats = new ArrayList<>();
        ServiceStat recurringCostStat = createStat(currencyUnit,
                serviceReservedRecurringCostMetric,
                currentBillProcessedTimeMillis, serviceDetailDto.reservedRecurringCost);
        serviceStats.add(recurringCostStat);
        stats.put(serviceReservedRecurringCostMetric, serviceStats);

        return stats;
    }

    private ServiceStat createStat(String unit, String name, Long timestamp, Number value) {
        ServiceStat stat = new ServiceStat();
        stat.latestValue = value.doubleValue();
        stat.sourceTimeMicrosUtc = TimeUnit.MILLISECONDS.toMicros(timestamp);
        stat.unit = unit;
        stat.name = name;
        return stat;
    }

    private Consumer<Throwable> getFailureConsumer(AWSCostStatsCreationContext statsData) {
        return ((t) -> statsData.taskManager.patchTaskToFailure(t));
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
                        .createDoubleRange(0.0, Double.MAX_VALUE, true, true));
        QueryTask qTask = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.SORT)
                .addOption(QueryOption.TOP_RESULTS)
                // No-op in photon-model. Required for special handling of immutable documents.
                // This will prevent Lucene from holding the full result set in memory.
                .addOption(QueryOption.INCLUDE_ALL_VERSIONS)
                .addOption(QueryOption.EXPAND_CONTENT)
                .orderDescending(ServiceDocument.FIELD_NAME_SELF_LINK,
                        ServiceDocumentDescription.TypeName.STRING)
                .setResultLimit(1)
                .setQuery(builder.build()).build();
        qTask.documentExpirationTimeMicros = Utils.getNowMicrosUtc() + QueryUtils.TEN_MINUTES_IN_MICROS;
        URI postUri = UriUtils.buildUri(
                ClusterUtil.getClusterUri(getHost(), ServiceTypeCluster.METRIC_SERVICE),
                ServiceUriPaths.CORE_LOCAL_QUERY_TASKS);
        return Operation.createPost(postUri)
                .setBody(qTask)
                .setCompletion((operation, exception) -> {
                    if (exception != null) {
                        logWarning(() -> String.format("Failed to get bill processed time for"
                                + " account: %s", accountComputeState.documentSelfLink));
                        getFailureConsumer(context).accept(exception);
                        return;
                    }
                    QueryTask body = operation.getBody(QueryTask.class);
                    Collection<Object> values = body.results.documents.values();
                    Map<String, Long> billMarkerMap = context.accountIdToBillProcessedTime;
                    if (!values.isEmpty()) {
                        ResourceMetricsService.ResourceMetrics rawResourceMetrics = Utils
                                .fromJson(values.iterator().next(),
                                        ResourceMetricsService.ResourceMetrics.class);
                        Long accountBillProcessedTime = rawResourceMetrics.entries
                                .getOrDefault(AWSConstants.AWS_ACCOUNT_BILL_PROCESSED_TIME_MILLIS,
                                        0d).longValue();

                        synchronized (billMarkerMap) {
                            Long currVal = billMarkerMap.getOrDefault(accountId, Long.MAX_VALUE);
                            long minMarker = Long.min(currVal, accountBillProcessedTime);
                            billMarkerMap.put(accountId, minMarker);
                        }
                    } else {
                        billMarkerMap.put(accountId, 0L);
                    }
                });
    }

    private void joinOperationAndSendRequest(AWSCostStatsCreationContext context,
            AWSCostStatsCreationStages next, List<Operation> queryOps) {

        if (queryOps.isEmpty()) {
            context.stage = next;
            handleCostStatsCreationRequest(context);
            return;
        }

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
