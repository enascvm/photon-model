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
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants
        .AWS_ACCOUNT_BILL_PROCESSED_TIME_MILLIS;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_ACCOUNT_ID_KEY;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_LINKED_ACCOUNT_IDS;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.STORAGE_TYPE_EBS;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.STORAGE_TYPE_S3;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
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

import com.vmware.photon.controller.model.UriPaths;
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
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSCsvBillParser.AwsServices;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSStatsNormalizer;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService.ResourceMetrics;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.query.QueryUtils.QueryByPages;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService.SingleResourceStatsCollectionTaskState;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService.SingleResourceTaskCollectionStage;

import com.vmware.photon.controller.model.tasks.monitoring.StatsUtil;
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
    public static final int INTERNAL_REQUEST_TIMEOUT_SECONDS = Integer.getInteger(
            UriPaths.PROPERTY_PREFIX + "aws.costCollection.internalRequestTimeoutSecs", 300);
    public static final int DEFAULT_BATCH_SIZE = 500;
    // Past months for which bills need to be collected, default is 11, excluding current month's bill.
    protected static final String BILLS_BACK_IN_TIME_MONTHS_KEY = "aws.costsCollection.backInTime.months";

    private AWSClientManager clientManager;
    private ExecutorService executor;

    protected enum AWSCostStatsCreationStages {
        ACCOUNT_DETAILS, DOWNLOAD_PARSE_CREATE_STATS, QUERY_LOCAL_RESOURCES, QUERY_LINKED_ACCOUNTS,
        QUERY_MARKERS, CHECK_BILL_BUCKET_CONFIG, RESERVED_INSTANCES_PLANS_COLLECTION,
        CREATE_MISSING_RESOURCES_COMPUTES, FINISH
    }

    protected enum AWSCostStatsCreationSubStage {
        QUERY_INSTANCES, QUERY_VOLUMES
    }

    public AWSCostStatsService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        this.clientManager = AWSClientManagerFactory.getClientManager(AwsClientType.S3_TRANSFER_MANAGER);
    }

    protected class AWSCostStatsCreationContext {
        protected ComputeStateWithDescription computeDesc;
        protected AuthCredentialsService.AuthCredentialsServiceState parentAuth;
        protected ComputeStatsRequest statsRequest;
        protected ComputeStatsResponse statsResponse;
        protected TransferManager s3Client;
        protected List<String> ignorableInvoiceCharge;
        protected AWSCostStatsCreationStages stage;
        protected AWSCostStatsCreationSubStage subStage;

        // This map is one to many because an AWS instance will have different compute links if the same account
        // is added by different users.
        protected Map<String, Set<String>> awsResourceLinksById;
        // This map is one to many because a distinct compute state will be present for each region of an AWS account.
        protected Map<String, List<ComputeState>> awsAccountIdToComputeStates;
        protected OperationContext opContext;
        // Holds the month for which bill has to be downloaded and processed
        protected LocalDate billMonthToDownload;
        protected TaskManager taskManager;
        // aws accountId for which this adapter is running
        protected String accountId;
        protected boolean isSecondPass = false;
        protected final Map<String, ResourceMetrics> accountsMarkersMap = new ConcurrentHashMap<>();

        protected AWSCostStatsCreationContext(ComputeStatsRequest statsRequest) {
            this.statsRequest = statsRequest;
            this.stage = AWSCostStatsCreationStages.ACCOUNT_DETAILS;
            this.ignorableInvoiceCharge = new ArrayList<>();
            this.awsResourceLinksById = new ConcurrentHashMap<>();
            this.awsAccountIdToComputeStates = new ConcurrentHashMap<>();
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
        AWSClientManagerFactory.returnClientManager(this.clientManager, AwsClientType.S3_TRANSFER_MANAGER);
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
                queryLinkedAccounts(statsData, AWSCostStatsCreationStages.QUERY_MARKERS);
                break;
            case QUERY_MARKERS:
                queryMarkers(statsData, AWSCostStatsCreationStages.QUERY_LOCAL_RESOURCES);
                break;
            case QUERY_LOCAL_RESOURCES:
                if (statsData.subStage == null) {
                    statsData.subStage = AWSCostStatsCreationSubStage.QUERY_INSTANCES;
                }
                switch (statsData.subStage) {
                case QUERY_INSTANCES:
                    queryInstances(statsData, AWSCostStatsCreationStages.QUERY_LOCAL_RESOURCES,
                            AWSCostStatsCreationSubStage.QUERY_VOLUMES);
                    break;
                case QUERY_VOLUMES:
                    queryVolumes(statsData, AWSCostStatsCreationStages.DOWNLOAD_PARSE_CREATE_STATS, null);
                    break;
                default:
                    getFailureConsumer(statsData).accept(new Exception("Unknown AwsCostStatsCreation subStage"));
                    break;
                }
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
                String errorMessage = String.format("Unknown AWS Cost Stats enumeration stage %s", statsData.stage);
                getFailureConsumer(statsData).accept(new Exception(errorMessage));
                break;
            }
        } catch (Exception e) {
            getFailureConsumer(statsData).accept(e);
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

    private boolean isAutoDiscoveryEnabled(AWSCostStatsCreationContext context) {
        String autoDiscoveryFlag = context.computeDesc.customProperties
                .getOrDefault(PhotonModelConstants.IS_RESOURCE_AUTO_DISCOVERY_ENABLED, null);
        return (autoDiscoveryFlag == null || autoDiscoveryFlag.isEmpty() || Boolean
                .valueOf(autoDiscoveryFlag));
    }

    private void createMissingResourcesComputes(AWSCostStatsCreationContext context,
            AWSCostStatsCreationStages next) {

        String linkedAccountsCsv = context.computeDesc.customProperties.get(AWS_LINKED_ACCOUNT_IDS);
        if (context.isSecondPass || linkedAccountsCsv == null || linkedAccountsCsv.isEmpty() ||
                !isAutoDiscoveryEnabled(context)) {
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
                    context.awsResourceLinksById.clear();
                    handleCostStatsCreationRequest(context);
                });
        sendRequest(op);
    }

    protected void getAccountDescription(AWSCostStatsCreationContext statsData,
            AWSCostStatsCreationStages next) {
        Consumer<Operation> onSuccess = (op) -> {
            ComputeStateWithDescription compute = op.getBody(ComputeStateWithDescription.class);
            statsData.computeDesc = compute;
            String accountId = AWSUtils.isAwsS3Proxy() ? "mock" : compute.customProperties.get(AWS_ACCOUNT_ID_KEY);
            if (compute.type != ComputeType.VM_HOST || compute.parentLink != null
                    || compute.endpointLink == null || accountId == null) {
                logWarning(() -> String.format("AWS Cost collection is not supported for this "
                                + "compute type or the compute is missing mandatory properties: %s",
                        compute.documentSelfLink));
                postAccumulatedCostStats(statsData, true);
                return;
            }
            statsData.accountId = accountId;
            statsData.awsAccountIdToComputeStates.put(accountId, Collections.singletonList(compute));
            getParentAuth(statsData, next);
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
        URI authUri = UriUtils.extendUri(getInventoryServiceUri(), authLink);
        AdapterUtils.getServiceState(this, authUri, onSuccess, getFailureConsumer(statsData));
    }

    protected void checkBillBucketConfig(AWSCostStatsCreationContext statsData,
            AWSCostStatsCreationStages next) {
        this.executor.submit(() -> {
            OperationContext.restoreOperationContext(statsData.opContext);
            statsData.s3Client = this.clientManager.getOrCreateS3TransferManager(statsData.parentAuth,
                    null, this, getFailureConsumer(statsData));
            if (statsData.s3Client == null) {
                logWarning(() -> String.format("Couldn't get S3_TRANSFER_MANAGER client while collecting stats for "
                        + "%s", statsData.computeDesc.documentSelfLink));
                postAccumulatedCostStats(statsData, true);
                return;
            }
            String billsBucketName = statsData.computeDesc.customProperties
                    .getOrDefault(AWSConstants.AWS_BILLS_S3_BUCKET_NAME_KEY, null);
            try {
                if (billsBucketName == null || billsBucketName.isEmpty()) {
                    billsBucketName = AWSUtils.autoDiscoverBillsBucketName(
                            statsData.s3Client.getAmazonS3Client(), statsData.accountId);
                    if (billsBucketName == null || billsBucketName.isEmpty()) {
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

    private Map<String, ComputeState> findRootAccountComputeStateByEndpoint(
            List<ComputeState> accountComputeStates) {
        return accountComputeStates.stream()
                .collect(Collectors.groupingBy(c -> c.endpointLink))
                .entrySet().stream()
                .collect(Collectors.toMap(Entry::getKey, e -> e.getValue().get(0)));
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
            String accountId, Consumer<List<ComputeState>> queryResultConsumer) {
        Query awsAccountsQuery = Query.Builder.create().addKindFieldClause(ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_TYPE, ComputeType.VM_HOST)
                .addCompositeFieldClause(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                        EndpointAllocationTaskService.CUSTOM_PROP_ENPOINT_TYPE,
                        PhotonModelConstants.EndpointType.aws.name())
                .addCompositeFieldClause(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                        AWS_ACCOUNT_ID_KEY, accountId)
                .addInCollectionItemClause(ComputeState.FIELD_NAME_TENANT_LINKS,
                        context.computeDesc.tenantLinks)
                .build();
        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .setQuery(awsAccountsQuery).build();
        queryTask.setDirect(true);
        queryTask.tenantLinks = context.computeDesc.tenantLinks;
        return Operation.createPost(UriUtils.extendUri(getInventoryServiceUri(),
                ServiceUriPaths.CORE_LOCAL_QUERY_TASKS))
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
                            .filter(cs -> cs.parentLink == null && cs.endpointLink != null)
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

        Long billProcessedTime = context.accountsMarkersMap.get(context.accountId).entries
                .getOrDefault(AWS_ACCOUNT_BILL_PROCESSED_TIME_MILLIS, 0d).longValue();
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
        sendRequest(Operation.createPatch(UriUtils.extendUri(
                getInventoryServiceUri(), context.computeDesc.documentSelfLink))
                .setBody(accountState));
    }

    protected void queryInstances(AWSCostStatsCreationContext statsData, AWSCostStatsCreationStages nextStage,
            AWSCostStatsCreationSubStage nextSubStage) {

        Set<String> endpointLinks = statsData.awsAccountIdToComputeStates.values()
                .stream().flatMap(List::stream) // flatten collection of lists to single list
                .map(e -> e.endpointLink)
                .collect(Collectors.toSet()); // extract endpointLinks of all accounts

        Query query = Query.Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_TYPE,
                        ComputeDescriptionService.ComputeDescription.ComputeType.VM_GUEST)
                .addInClause(ComputeState.FIELD_NAME_ENDPOINT_LINK, endpointLinks, Occurance.MUST_OCCUR)
                .build();

        populateAwsResources(query, statsData, nextStage, nextSubStage);
    }

    protected void queryVolumes(AWSCostStatsCreationContext statsData, AWSCostStatsCreationStages nextStage,
            AWSCostStatsCreationSubStage nextSubStage) {

        Set<String> endpointLinks = statsData.awsAccountIdToComputeStates.values()
                .stream().flatMap(List::stream) // flatten collection of lists to single list
                .map(e -> e.endpointLink).collect(Collectors.toSet()); // extract endpointLinks of all accounts

        List<String> supportedStorageTypes = Arrays.asList(STORAGE_TYPE_EBS, STORAGE_TYPE_S3);
        Query query = Query.Builder.create()
                .addKindFieldClause(DiskState.class)
                .addInClause(DiskState.FIELD_NAME_ENDPOINT_LINK, endpointLinks, Occurance.MUST_OCCUR)
                .addInClause(DiskState.FIELD_NAME_STORAGE_TYPE, supportedStorageTypes)
                .build();

        populateAwsResources(query, statsData, nextStage, nextSubStage);
    }

    private void populateAwsResources(Query query, AWSCostStatsCreationContext context,
            AWSCostStatsCreationStages nextStage, AWSCostStatsCreationSubStage nextSubStage) {
        QueryByPages<ResourceState> queryResources = new QueryByPages<>(getHost(), query, ResourceState.class,
                context.computeDesc.tenantLinks);
        queryResources.setClusterType(ServiceTypeCluster.DISCOVERY_SERVICE);
        queryResources.queryDocuments(getAwsResourceConsumer(context)).whenComplete((v, t) -> {
            if (t != null) {
                getFailureConsumer(context).accept(t);
                return;
            }
            context.stage = nextStage;
            context.subStage = nextSubStage;
            handleCostStatsCreationRequest(context);
        });
    }

    private Consumer<ResourceState> getAwsResourceConsumer(AWSCostStatsCreationContext context) {
        return (resource) -> {
            Set<String> links = context.awsResourceLinksById.computeIfAbsent(resource.id, (k) -> new HashSet<>());
            links.add(resource.documentSelfLink);
        };
    }

    protected void queryMarkers(AWSCostStatsCreationContext context, AWSCostStatsCreationStages next) {
        // Construct a list of query operations, one for each account ID to query the
        // corresponding billProcessedTime and populate in the context object.
        List<Operation> queryOps = new ArrayList<>();
        for (Entry<String, List<ComputeState>> entry : context.awsAccountIdToComputeStates.entrySet()) {
            List<ComputeState> accountComputes = entry.getValue();
            if (CollectionUtils.isEmpty(accountComputes)) {
                // This will happen for linked accounts not configured in the system; skip.
                continue;
            }
            findRootAccountComputeStateByEndpoint(accountComputes).values()
                    .forEach(accountComputeState -> {
                        if (!context.accountsMarkersMap.containsKey(entry.getKey())) {
                            queryOps.add(getMarkerMetricsOp(context, accountComputeState));
                        }
                    });
        }
        joinOperationAndSendRequest(context, next, queryOps);
    }

    protected long getCurrentMonthStartTimeMicros() {
        return getFirstDayOfCurrentMonth().toDateTimeAtStartOfDay().getMillis() * 1000;
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
                ServiceStat costStat = createStat(AWSStatsNormalizer.getNormalizedUnitValue(DIMENSION_CURRENCY_VALUE),
                        AWSStatsNormalizer.getNormalizedStatKeyValue(AWSConstants.COST),
                        awsAccountDetailDto.billProcessedTimeMillis, awsAccountDetailDto.cost);
                accountStats.statValues.put(costStat.name, Collections.singletonList(costStat));
                ServiceStat otherCostsStat = createStat(
                        AWSStatsNormalizer.getNormalizedUnitValue(DIMENSION_CURRENCY_VALUE), AWSConstants.OTHER_CHARGES,
                        awsAccountDetailDto.billProcessedTimeMillis, awsAccountDetailDto.otherCharges);
                accountStats.statValues.put(otherCostsStat.name, Collections.singletonList(otherCostsStat));
            }
            if (!accountStats.statValues.isEmpty()) {
                statsData.statsResponse.statsList.add(accountStats);
            }
        };
        processAccountStats(statsData, billMonth, awsAccountDetailDto, accountStatsProcessor);

        ResourceMetrics prevMarkerMetrics = statsData.accountsMarkersMap.get(awsAccountDetailDto.id);
        if (prevMarkerMetrics != null) {
            prevMarkerMetrics.entries.putAll(transformMapDataTypes(awsAccountDetailDto.lineCountPerInterval));
        }
    }

    protected void createServiceStatsForAccount(AWSCostStatsCreationContext statsData,
            LocalDate billMonth, AwsAccountDetailDto awsAccountDetailDto) {
        Consumer<ComputeState> serviceStatsProcessor = (accountComputeState) -> {
            ComputeStats awsServiceStats = new ComputeStats();
            awsServiceStats.statValues = new ConcurrentHashMap<>();
            awsServiceStats.computeLink = accountComputeState.documentSelfLink;
            awsServiceStats.addCustomProperty(PhotonModelConstants.DOES_CONTAIN_SERVICE_STATS, Boolean.TRUE.toString());
            for (AwsServiceDetailDto serviceDetailDto : awsAccountDetailDto.serviceDetailsMap.values()) {
                Map<String, List<ServiceStat>> statsForAwsService = createStatsForAwsService(serviceDetailDto,
                        awsAccountDetailDto.billProcessedTimeMillis);
                awsServiceStats.statValues.putAll(statsForAwsService);
            }
            if (!awsServiceStats.statValues.isEmpty()) {
                statsData.statsResponse.statsList.add(awsServiceStats);
            }
        };
        insertEC2ServiceDetail(awsAccountDetailDto);
        processAccountStats(statsData, billMonth, awsAccountDetailDto, serviceStatsProcessor);
    }

    private void insertEC2ServiceDetail(AwsAccountDetailDto awsAccountDetailDto) {
        AwsServiceDetailDto vm = awsAccountDetailDto.serviceDetailsMap.get(AwsServices.EC2_Instance_Usage.getName());
        AwsServiceDetailDto ebs = awsAccountDetailDto.serviceDetailsMap.get(AwsServices.EC2_EBS.getName());
        AwsServiceDetailDto others = awsAccountDetailDto.serviceDetailsMap.get(AwsServices.EC2_Others.getName());
        AwsServiceDetailDto ec2ServiceDetail = new AwsServiceDetailDto();
        ec2ServiceDetail.id = AwsServices.EC2.getName();
        ec2ServiceDetail.type = AwsServices.getTypeByName(AwsServices.EC2.getName()).toString();
        ec2ServiceDetail.directCosts = Stream.of(vm, ebs, others).filter(Objects::nonNull)
                .map(dto -> dto.directCosts.entrySet()).flatMap(Set::stream)
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue, Double::sum));
        ec2ServiceDetail.otherCosts = Stream.of(vm, ebs, others).filter(Objects::nonNull)
                .map(dto -> dto.otherCosts.entrySet()).flatMap(Set::stream)
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue, Double::sum));
        ec2ServiceDetail.remainingCost = Stream.of(vm, ebs, others).filter(Objects::nonNull)
                .mapToDouble(dto -> dto.remainingCost).sum();
        ec2ServiceDetail.reservedRecurringCost = Stream.of(vm, ebs, others).filter(Objects::nonNull)
                .mapToDouble(dto -> dto.reservedRecurringCost).sum();
        awsAccountDetailDto.serviceDetailsMap.put(AwsServices.EC2.getName(), ec2ServiceDetail);
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
        Map<String, AwsServiceDetailDto> serviceDetails = awsAccountDetailDto.serviceDetailsMap;
        List<AwsServices> supportedServices = Arrays.asList(AwsServices.EC2_Instance_Usage, AwsServices.EC2_EBS, AwsServices.S3);
        for (String service : serviceDetails.keySet()) {
            if (!supportedServices.contains(AwsServices.getByName(service))) {
                continue;
            }
            Map<String, AwsResourceDetailDto> resourceDetailsMap = serviceDetails.get(service).resourceDetailsMap;
            if (resourceDetailsMap == null) {
                continue;
            }
            for (Entry<String, AwsResourceDetailDto> entry : resourceDetailsMap.entrySet()) {
                String resourceId = entry.getKey();
                AwsResourceDetailDto resourceDetails = entry.getValue();
                if ((resourceDetails == null) || (resourceDetails.directCosts == null)) {
                    continue;
                }
                Set<String> resourceLinks = statsData.awsResourceLinksById
                        .getOrDefault(resourceId, Collections.emptySet());
                for (String resourceStateLink : resourceLinks) {
                    ComputeStats resourceStats = createStatsForResource(resourceStateLink, resourceDetails);
                    statsData.statsResponse.statsList.add(resourceStats);
                }
            }
        }
    }

    /**
     * Decides if stats need to be created or not after the bill has been parsed and processed.
     * If the bill hasn't changed since the last run, do NOT create and persist stats, otherwise do.
     */
    private boolean isBillUpdated(AWSCostStatsCreationContext context, AwsAccountDetailDto accountDetailDto) {
        ResourceMetrics prevMarkerMetrics = context.accountsMarkersMap.get(accountDetailDto.id);
        for (Entry<String, Integer> entry : accountDetailDto.lineCountPerInterval.entrySet()) {
            Double prevLineCount = prevMarkerMetrics.entries.get(entry.getKey());
            if (prevLineCount == null || !prevLineCount.equals(entry.getValue().doubleValue())) {
                return true;
            }
        }
        return false;
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

                            parser.parseDetailedCsvBill(statsData.ignorableInvoiceCharge, csvBillZipFilePath,
                                    statsData.awsAccountIdToComputeStates.keySet(),
                                    getHourlyStatsConsumer(billMonth, statsData),
                                    getMonthlyStatsConsumer(billMonth, statsData));
                            deleteTempFiles();
                            // Continue downloading and processing the bills for past and current months' bills
                            statsData.billMonthToDownload = statsData.billMonthToDownload.plusMonths(1);
                            handleCostStatsCreationRequest(statsData);
                        } else if (ProgressEventType.TRANSFER_FAILED_EVENT.equals(eventType)) {
                            deleteTempFiles();
                            billDownloadFailureHandler(statsData, awsBucketName, new IOException(
                                    "Download of AWS CSV Bill '" + csvBillZipFileName + "' failed."));
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
            boolean isCurrentMonth = isCurrentMonth(billMonth);
            accountDetailDtoMap.values().forEach(accountDto -> {
                createAccountStats(statsData, billMonth, accountDto);
                if (isCurrentMonth) {
                    createMarkerMetrics(statsData, accountDto);
                }
            });
            if (isCurrentMonth) {
                setLinkedAccountIds(statsData, accountDetailDtoMap);
                postAccumulatedCostStats(statsData, false, true);
            } else {
                postAccumulatedCostStats(statsData, false);
            }
        };
    }

    private void createMarkerMetrics(AWSCostStatsCreationContext context, AwsAccountDetailDto accountDto) {
        List<ComputeState> accountComputeStates = context.awsAccountIdToComputeStates.get(accountDto.id);
        if ((accountComputeStates == null) || accountComputeStates.isEmpty()) {
            logFine(() -> "AWS account with ID '%s' is not configured yet. Not creating marker metrics for the same.");
            return;
        }
        // We use root compute state representing this account to save the account level stats
        Map<String, ComputeState> rootComputesByEndpoint = findRootAccountComputeStateByEndpoint(accountComputeStates);
        URI uri = UriUtils.buildUri(ClusterUtil.getClusterUri(getHost(), ServiceTypeCluster.METRIC_SERVICE),
                ResourceMetricsService.FACTORY_LINK);
        for (ComputeState compute : rootComputesByEndpoint.values()) {
            ResourceMetrics markerMetrics = new ResourceMetrics();
            markerMetrics.documentSelfLink = StatsUtil.getMetricKey(compute.documentSelfLink, Utils.getNowMicrosUtc());
            markerMetrics.entries = new HashMap<>();
            markerMetrics.entries.putAll(transformMapDataTypes(accountDto.lineCountPerInterval));
            markerMetrics.entries
                    .put(AWS_ACCOUNT_BILL_PROCESSED_TIME_MILLIS, accountDto.billProcessedTimeMillis.doubleValue());
            markerMetrics.timestampMicrosUtc = getCurrentMonthStartTimeMicros();
            markerMetrics.customProperties = new HashMap<>();
            markerMetrics.customProperties.put(ResourceMetrics.PROPERTY_RESOURCE_LINK, compute.documentSelfLink);
            markerMetrics.customProperties
                    .put(PhotonModelConstants.CONTAINS_BILL_PROCESSED_TIME_STAT, Boolean.TRUE.toString());
            markerMetrics.documentExpirationTimeMicros = Utils.getNowMicrosUtc() + TimeUnit.DAYS.toMicros(7);
            sendRequest(Operation.createPost(uri).setBodyNoCloning(markerMetrics));
        }
    }

    private Map<String, Double> transformMapDataTypes(Map<String, Integer> map) {
        return map.entrySet().stream().collect(Collectors.toMap(Entry::getKey, e -> e.getValue().doubleValue()));
    }

    /**
     * Consumes the monthly stats from bill rows and creates stats
     */
    protected BiConsumer<Map<String, AwsAccountDetailDto>, String> getHourlyStatsConsumer(LocalDate billMonth,
            AWSCostStatsCreationContext statsData) {
        return (accountDetailDtoMap, interval) -> {
            accountDetailDtoMap.values().forEach(accountDto -> {
                if (!accountDto.serviceDetailsMap.isEmpty()) {
                    filterAccountDetails(statsData, accountDto, interval);
                    createResourceStatsForAccount(statsData, accountDto);
                    createServiceStatsForAccount(statsData, billMonth, accountDto);
                    accountDto.serviceDetailsMap.clear();
                }
            });
            postAccumulatedCostStats(statsData, false);
        };
    }

    private void filterAccountDetails(AWSCostStatsCreationContext context, AwsAccountDetailDto accountDto,
            String interval) {
        ResourceMetrics markerMetrics = context.accountsMarkersMap.get(accountDto.id);
        if (markerMetrics == null || interval == null) {
            return;
        }
        Double previousLineCount = markerMetrics.entries.get(interval);
        Integer currentLineCount = accountDto.lineCountPerInterval.get(interval);
        if (previousLineCount != null && currentLineCount != null
                && previousLineCount.intValue() == currentLineCount) {
            accountDto.serviceDetailsMap.clear();
        }
    }

    private void billDownloadFailureHandler(
            AWSCostStatsCreationContext statsData, String awsBucketName, Exception exception) {
        StringWriter error = new StringWriter();
        exception.printStackTrace(new PrintWriter(error));
        if (isCurrentMonth(statsData.billMonthToDownload)) {
            // Abort if the current month's bill is NOT available.
            logSevere(() -> String.format("Could not process current month's bill for %s,"
                            + "Check bucket preferences and  User permissions : %s",
                    statsData.computeDesc.documentSelfLink, error.toString()));
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

    private ComputeStats createStatsForResource(String resourceLink, AwsResourceDetailDto resourceDetails) {

        ComputeStats resourceStats = new ComputeStats();
        resourceStats.statValues = new ConcurrentSkipListMap<>();
        resourceStats.computeLink = resourceLink;
        List<ServiceStat> resourceServiceStats = new ArrayList<>();
        String normalizedStatKeyValue = AWSStatsNormalizer.getNormalizedStatKeyValue(AWSConstants.COST);
        for (Entry<Long, Double> cost : resourceDetails.directCosts.entrySet()) {
            ServiceStat resourceStat = createStat(AWSStatsNormalizer.getNormalizedUnitValue(DIMENSION_CURRENCY_VALUE),
                    normalizedStatKeyValue, cost.getKey(), cost.getValue());
            resourceServiceStats.add(resourceStat);
        }
        resourceStats.statValues.put(normalizedStatKeyValue, resourceServiceStats);

        // Create a stat to represent how many hours a resource ran as reserve instance
        String normalizedReservedInstanceStatKey = AWSStatsNormalizer
                .getNormalizedStatKeyValue(AWSConstants.RESERVED_INSTANCE_DURATION);
        List<ServiceStat> reservedInstanceStats = new ArrayList<>();
        for (Entry<Long, Double> entry : resourceDetails.hoursAsReservedPerDay.entrySet()) {
            ServiceStat resourceStat = createStat(AWSStatsNormalizer.getNormalizedUnitValue(AWSConstants.UNIT_HOURS),
                    normalizedReservedInstanceStatKey, entry.getKey(), entry.getValue());
            reservedInstanceStats.add(resourceStat);
        }
        logFine(() -> String.format("Reserved Instances stats count for %s is %d",
                resourceLink, reservedInstanceStats.size()));
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
            if (cost.getValue() > 0) {
                ServiceStat resourceCostStat = createStat(currencyUnit,
                        serviceResourceCostMetric, cost.getKey(), cost.getValue());
                serviceStats.add(resourceCostStat);
            }
        }
        if (!serviceStats.isEmpty()) {
            stats.put(serviceResourceCostMetric, serviceStats);
        }

        // Create stats for hourly other costs
        serviceStats = new ArrayList<>();
        String serviceOtherCostMetric = String.format(AWSConstants.SERVICE_OTHER_COST, serviceCode);
        for (Entry<Long, Double> cost : serviceDetailDto.otherCosts.entrySet()) {
            if (cost.getValue() > 0) {
                ServiceStat otherCostStat = createStat(currencyUnit, serviceOtherCostMetric,
                        cost.getKey(), cost.getValue());
                serviceStats.add(otherCostStat);
            }
        }
        if (!serviceStats.isEmpty()) {
            stats.put(serviceOtherCostMetric, serviceStats);
        }

        // Create stats for monthly other costs
        if (serviceDetailDto.remainingCost > 0) {
            String serviceMonthlyOtherCostMetric = String.format(AWSConstants.SERVICE_MONTHLY_OTHER_COST, serviceCode);
            serviceStats = new ArrayList<>();
            ServiceStat monthlyOtherCostStat = createStat(currencyUnit, serviceMonthlyOtherCostMetric,
                    currentBillProcessedTimeMillis, serviceDetailDto.remainingCost);
            serviceStats.add(monthlyOtherCostStat);
            stats.put(serviceMonthlyOtherCostMetric, serviceStats);
        }

        // Create stats for monthly reserved recurring instance costs
        if (serviceDetailDto.reservedRecurringCost > 0) {
            String serviceReservedRecurringCostMetric = String.format(AWSConstants.SERVICE_RESERVED_RECURRING_COST,
                    serviceCode);
            serviceStats = new ArrayList<>();
            ServiceStat recurringCostStat = createStat(currencyUnit, serviceReservedRecurringCostMetric,
                    currentBillProcessedTimeMillis, serviceDetailDto.reservedRecurringCost);
            serviceStats.add(recurringCostStat);
            stats.put(serviceReservedRecurringCostMetric, serviceStats);
        }

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

    private QueryTask getQueryTaskForMetric(ComputeState accountComputeState) {
        Query.Builder builder = Query.Builder.create(Occurance.SHOULD_OCCUR);
        builder.addKindFieldClause(ResourceMetrics.class);
        builder.addCompositeFieldClause(ResourceMetrics.FIELD_NAME_CUSTOM_PROPERTIES,
                ResourceMetrics.PROPERTY_RESOURCE_LINK, accountComputeState.documentSelfLink);
        builder.addCompositeFieldClause(ResourceMetrics.FIELD_NAME_CUSTOM_PROPERTIES,
                PhotonModelConstants.CONTAINS_BILL_PROCESSED_TIME_STAT, Boolean.TRUE.toString());

        QueryTask qTask = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.SORT)
                .addOption(QueryOption.TOP_RESULTS)
                // No-op in photon-model. Required for special handling of immutable documents.
                // This will prevent Lucene from holding the full result set in memory.
                .addOption(QueryOption.INCLUDE_ALL_VERSIONS)
                .addOption(QueryOption.EXPAND_CONTENT)
                .orderDescending(ServiceDocument.FIELD_NAME_SELF_LINK, ServiceDocumentDescription.TypeName.STRING)
                .setResultLimit(1)
                .setQuery(builder.build()).build();
        qTask.documentExpirationTimeMicros = Utils.fromNowMicrosUtc(QueryUtils.TEN_MINUTES_IN_MICROS);
        return qTask;
    }

    private Operation getMarkerMetricsOp(AWSCostStatsCreationContext context, ComputeState accComputeState) {

        QueryTask qTask = getQueryTaskForMetric(accComputeState);
        URI postUri = UriUtils.buildUri(ClusterUtil.getClusterUri(getHost(), ServiceTypeCluster.METRIC_SERVICE),
                ServiceUriPaths.CORE_LOCAL_QUERY_TASKS);
        return Operation.createPost(postUri).setBody(qTask).setConnectionSharing(true)
                .setExpiration(Utils.fromNowMicrosUtc(TimeUnit.SECONDS.toMicros(INTERNAL_REQUEST_TIMEOUT_SECONDS)))
                .setCompletion((operation, exception) -> {
                    if (exception != null) {
                        logWarning(() -> String.format(
                                "Failed to get bill processed time for account: %s",
                                accComputeState.documentSelfLink));
                        getFailureConsumer(context).accept(exception);
                        return;
                    }
                    QueryTask body = operation.getBody(QueryTask.class);
                    String accountId = accComputeState.customProperties.get(AWS_ACCOUNT_ID_KEY);
                    if (body.results.documentCount == 0) {
                        ResourceMetrics markerMetrics = new ResourceMetrics();
                        markerMetrics.timestampMicrosUtc = getCurrentMonthStartTimeMicros();
                        markerMetrics.entries = new HashMap<>();
                        markerMetrics.entries
                                .put(AWSConstants.AWS_ACCOUNT_BILL_PROCESSED_TIME_MILLIS, 0d);
                        markerMetrics.documentSelfLink = StatsUtil.getMetricKey(
                                accComputeState.documentSelfLink,
                                Utils.getNowMicrosUtc());
                        context.accountsMarkersMap.put(accountId, markerMetrics);
                    } else {
                        ResourceMetrics markerMetrics = body.results.documents.values().stream()
                                .map(o -> Utils.fromJson(o, ResourceMetrics.class))
                                .collect(Collectors.toList()).get(0);
                        context.accountsMarkersMap.putIfAbsent(accountId, markerMetrics);
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

    private URI getInventoryServiceUri() {
        return ClusterUtil.getClusterUri(getHost(), ServiceTypeCluster.DISCOVERY_SERVICE);
    }
}
