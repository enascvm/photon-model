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

package com.vmware.photon.controller.model.adapters.azure.ea.stats;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.opencsv.CSVReader;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;
import org.apache.commons.io.FileUtils;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.vmware.photon.controller.model.adapterapi.ComputeStatsRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse.ComputeStats;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureCostConstants;
import com.vmware.photon.controller.model.adapters.azure.ea.AzureCostStatsServiceHelper;
import com.vmware.photon.controller.model.adapters.azure.ea.AzureDetailedBillHandler;
import com.vmware.photon.controller.model.adapters.azure.ea.enumeration
        .AzureSubscriptionsEnumerationService;
import com.vmware.photon.controller.model.adapters.azure.ea.enumeration
        .AzureSubscriptionsEnumerationService.AzureSubscriptionsEnumerationRequest;
import com.vmware.photon.controller.model.adapters.azure.model.cost.AzureErrorResponse;
import com.vmware.photon.controller.model.adapters.azure.model.cost.AzureService;
import com.vmware.photon.controller.model.adapters.azure.model.cost.AzureSubscription;
import com.vmware.photon.controller.model.adapters.azure.model.cost.EaSummarizedBillElement;
import com.vmware.photon.controller.model.adapters.azure.model.cost.OldApi;
import com.vmware.photon.controller.model.adapters.azure.model.cost.OldEaSummarizedBillElement;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureStatsNormalizer;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
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
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Collects cost data for Azure EA accounts in the following stages:
 * 1. Gets the credentials of the configured account.
 * 2. Checks which past months EA account costs need to be collected based on
 *    a configurable property. In case of Azure EA accounts, this collects only the EA
 *    account-level costs for past months. Azure subscription-level costs are collected only
 *    for current months.
 * 3. Downloads current month's bill
 * 4. Starts processing the bill in the following manner:
 *    a. Processes a day's bill and gets the subscriptions in this bill
 *    b. Segregates cost by Azure subscription, service and resource-level costs.
 *    b. Checks if any of these subscriptions have never appeared before in the current month's bill
 *    c. For all such subscriptions which are new, sends a request to the enumerator to create
 *       compute states for these subscriptions
 *    d. Creates daily cost stats for services
 *    e. Posts these stats
 *    f. Repeats steps (a)-(e) till the bill is completely processed
 * 5. Creates monthly cost stats for subscription and posts these stats
 * 6. Creates monthly cost stats for EA-account for past and current month
 * 7. Clean-up, if any.
 */
public class AzureCostStatsService extends StatelessService {
    public static final String SELF_LINK = AzureUriPaths.AZURE_COST_STATS_ADAPTER;

    // Past months for which cost stats need to be collected, default is 11,
    // excluding current month's stats.
    static final String BILLS_BACK_IN_TIME_MONTHS_KEY =
            "azure.costsCollection.backInTime.months";

    // Indicates the number of months' whose detailed bills are to be processed (including current
    /// month). So 1 indicates process only current month's detailed bill.
    private static final int NO_OF_DETAILED_BILLS_TO_DOWNLOAD = 1;

    // Cost will be stored in this currency; independent of the currency in which the bills
    // are obtained in. Currency conversion needs to convert cost.
    public static final String DEFAULT_CURRENCY_VALUE = "USD";

    private ExecutorService executor;

    protected enum Stages {
        GET_COMPUTE_HOST,
        GET_AUTH,
        GET_BILL_MONTH_TO_FETCH_STATS_FOR,
        GET_HISTORICAL_COSTS,
        GET_LAST_COLLECTED_EA_TOTAL_USAGE_COST,
        DOWNLOAD_DETAILED_BILL,
        PARSE_DETAILED_BILL,
        CREATE_UPDATE_MISSING_COMPUTE_STATES,
        SET_LINKED_ACCOUNTS_CUSTOM_PROPERTY,
        QUERY_LINKED_SUBSCRIPTIONS,
        CREATE_MONTHLY_STATS_AND_POST,
        CREATE_DAILY_STATS_AND_POST
    }

    public AzureCostStatsService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @SuppressWarnings("WeakerAccess")
    public static class Context {
        // Please try to maintain an alphabetic order in member variables; helps while debugging
        // Map of all subscriptions, along with their cost; mainly needed to accumulate
        // total subscription cost at the end of the job, to store a single stat for
        // the subscription.
        private Map<String, AzureSubscription> allSubscriptionsCost = new ConcurrentHashMap<>();
        protected AuthCredentialsServiceState auth;
        private File billFile;
        private CSVReader csvReader;
        // The month for which cost stats need to be downloaded.
        private LocalDate billMonthToDownload = LocalDate.now(DateTimeZone.UTC).minusMonths(
                Integer.getInteger(BILLS_BACK_IN_TIME_MONTHS_KEY,
                        AzureCostConstants.DEFAULT_NO_OF_MONTHS_TO_GET_PAST_BILLS));
        protected long billProcessedTimeMillis = 0;
        protected ComputeStateWithDescription computeHostDesc;
        private String currency = DEFAULT_CURRENCY_VALUE;
        private Map<String, AzureSubscription> monthlyBillBatch = new ConcurrentHashMap<>();
        private Map<LocalDate, EaAccountCost> eaAccountCost = new ConcurrentHashMap<>();
        private OperationContext opContext = OperationContext.getOperationContext();
        private Double storedCurrentMonthEaUsageCost;
        protected Stages stage = Stages.GET_COMPUTE_HOST;
        private ComputeStatsRequest statsRequest;
        private ComputeStatsResponse statsResponse = new ComputeStatsResponse();
        private Map<String, ComputeState> subscriptionGuidToComputeState = new ConcurrentHashMap<>();
        private TaskManager taskManager;
        // billParsingCompleteTimeMillis is populated once the entire bill is parsed
        private long billParsingCompleteTimeMillis = 0;

        Context(ComputeStatsRequest statsRequest) {
            this.statsRequest = statsRequest;
            this.statsResponse.statsList = new ArrayList<>();
        }
    }

    private static class EaAccountCost {
        Double monthlyEaAccountUsageCost;
        Double monthlyEaAccountMarketplaceCost;
        Double monthlyEaAccountSeparatelyBilledCost;
    }

    @Override
    public void handleStart(Operation start) {
        this.executor = getHost().allocateExecutor(this);
        super.handleStart(start);
    }

    @Override
    public void handleStop(Operation delete) {
        this.executor.shutdown();
        AdapterUtils.awaitTermination(this.executor);
        super.handleStop(delete);
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required."));
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

        Context context = new Context(statsRequest);
        context.taskManager = taskManager;
        handleRequest(context);
    }

    private void handleRequest(Context context) {
        try {
            logFine(() -> String.format("Cost collection at stage: %s", context.stage));
            switch (context.stage) {
            case GET_COMPUTE_HOST:
                getComputeHost(context, Stages.GET_AUTH);
                break;
            case GET_AUTH:
                getAuth(context, Stages.GET_BILL_MONTH_TO_FETCH_STATS_FOR);
                break;
            case GET_BILL_MONTH_TO_FETCH_STATS_FOR:
                getBillProcessedTime(context, Stages.GET_HISTORICAL_COSTS);
                break;
            case GET_HISTORICAL_COSTS:
                // Try getting cost using the new API and if the call fails, try with
                // the old API.
                try {
                    getPastAndCurrentMonthsEaAccountCost(context,
                            Stages.GET_LAST_COLLECTED_EA_TOTAL_USAGE_COST);
                } catch (Exception e) {
                    getPastAndCurrentMonthsEaAccountCostUsingOldApi(context,
                            Stages.GET_LAST_COLLECTED_EA_TOTAL_USAGE_COST);
                }
                break;
            case GET_LAST_COLLECTED_EA_TOTAL_USAGE_COST:
                getStoredEaUsageCost(context, Stages.DOWNLOAD_DETAILED_BILL);
                break;
            case DOWNLOAD_DETAILED_BILL:
                downloadDetailedBill(context, Stages.PARSE_DETAILED_BILL);
                break;
            case PARSE_DETAILED_BILL:
                parseDetailedBill(context, Stages.SET_LINKED_ACCOUNTS_CUSTOM_PROPERTY);
                break;
            case SET_LINKED_ACCOUNTS_CUSTOM_PROPERTY:
                setLinkedSubscriptionGuids(context, Stages.QUERY_LINKED_SUBSCRIPTIONS);
                break;
            case QUERY_LINKED_SUBSCRIPTIONS:
                queryLinkedSubscriptions(context, Stages.CREATE_DAILY_STATS_AND_POST);
                break;
            case CREATE_DAILY_STATS_AND_POST:
                createStats(context);
                break;
            case CREATE_MONTHLY_STATS_AND_POST:
                createMonthlyStats(context, Stages.PARSE_DETAILED_BILL);
                break;
            default:
                logSevere(() -> String.format("Unknown Azure Cost Stats stage %s ",
                        context.stage.toString()));
                break;
            }
        } catch (Exception exception) {
            handleError(context, null, exception, false);
        }
    }

    private void getComputeHost(Context context, Stages next) {
        Consumer<Operation> onSuccess = (op) -> {
            context.computeHostDesc = op.getBody(ComputeStateWithDescription.class);
            context.stage = next;
            handleRequest(context);
        };

        URI computeUri = UriUtils.extendUriWithQuery(
                context.statsRequest.resourceReference,
                UriUtils.URI_PARAM_ODATA_EXPAND,
                Boolean.TRUE.toString());
        AdapterUtils.getServiceState(this, computeUri, onSuccess, getFailureConsumer(context));
    }

    /**
     * Gets the credentials (enrollment number & API key) required to call the Azure API for
     * requesting data.
     */
    private void getAuth(Context context, Stages next) {
        logInfo(() -> "Starting cost stats collection for account: ");

        URI authUri = UriUtils.extendUri(getInventoryServiceUri(),
                context.computeHostDesc.description.authCredentialsLink);
        Consumer<Operation> onSuccess = (op) -> {
            context.auth = op.getBody(AuthCredentialsServiceState.class);
            context.stage = next;
            handleRequest(context);
        };
        AdapterUtils.getServiceState(this, authUri, onSuccess, getFailureConsumer(context));
    }

    private void getStoredEaUsageCost(Context context, Stages next) {
        QueryTask.Query.Builder builder = QueryTask.Query.Builder
                .create(QueryTask.Query.Occurance.SHOULD_OCCUR);
        builder.addKindFieldClause(ResourceMetricsService.ResourceMetrics.class);
        builder.addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                UriUtils.buildUriPath(ResourceMetricsService.FACTORY_LINK,
                        UriUtils.getLastPathSegment(context.computeHostDesc.documentSelfLink)),
                QueryTask.QueryTerm.MatchType.PREFIX);
        builder.addRangeClause(QueryTask.QuerySpecification
                        .buildCompositeFieldName(
                                ResourceMetricsService.ResourceMetrics.FIELD_NAME_ENTRIES,
                                AzureCostConstants.USAGE_COST),
                QueryTask.NumericRange
                        .createDoubleRange(Double.MIN_VALUE, Double.MAX_VALUE, true, true));
        URI queryUri = UriUtils.extendUri(getMetricsServiceUri(),
                ServiceUriPaths.CORE_LOCAL_QUERY_TASKS);
        Operation.createPost(queryUri)
                .setBody(QueryTask.Builder.createDirectTask()
                        .addOption(QueryTask.QuerySpecification.QueryOption.SORT)
                        .addOption(QueryTask.QuerySpecification.QueryOption.TOP_RESULTS)
                        // No-op in photon-model. Required for special handling of immutable documents.
                        // This will prevent Lucene from holding the full result set in memory.
                        .addOption(QueryTask.QuerySpecification.QueryOption.INCLUDE_ALL_VERSIONS)
                        .addOption(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT)
                        .orderDescending(ResourceMetricsService.ResourceMetrics.FIELD_NAME_TIMESTAMP,
                                ServiceDocumentDescription.TypeName.LONG)
                        .setResultLimit(1)
                        .setQuery(builder.build()).build())
                .setCompletion((operation, exception) -> {
                    if (exception != null) {
                        handleError(context, Stages.DOWNLOAD_DETAILED_BILL,
                                exception, true);
                        return;
                    }
                    QueryTask body = operation.getBody(QueryTask.class);
                    if (body == null || body.results == null || body.results.documents == null) {
                        context.storedCurrentMonthEaUsageCost = null;
                        return;
                    }
                    Collection<Object> values = body.results.documents.values();
                    if (!values.isEmpty()) {
                        if (values.iterator().next() == null) {
                            context.storedCurrentMonthEaUsageCost = null;
                            return;
                        }
                        ResourceMetricsService.ResourceMetrics rawResourceMetrics = Utils
                                .fromJson(values.iterator().next(),
                                        ResourceMetricsService.ResourceMetrics.class);
                        context.storedCurrentMonthEaUsageCost = rawResourceMetrics.entries
                                .get(AzureCostConstants.USAGE_COST);
                    }
                    context.stage = next;
                    handleRequest(context);
                }).sendWith(this);
    }

    private void getBillProcessedTime(Context context, Stages next) {
        QueryTask.Query.Builder builder = QueryTask.Query.Builder
                .create(QueryTask.Query.Occurance.SHOULD_OCCUR);
        builder.addKindFieldClause(ResourceMetricsService.ResourceMetrics.class);
        builder.addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                UriUtils.buildUriPath(ResourceMetricsService.FACTORY_LINK,
                        UriUtils.getLastPathSegment(context.computeHostDesc.documentSelfLink)),
                QueryTask.QueryTerm.MatchType.PREFIX);
        builder.addRangeClause(QueryTask.QuerySpecification
                        .buildCompositeFieldName(
                                ResourceMetricsService.ResourceMetrics.FIELD_NAME_ENTRIES,
                                PhotonModelConstants.CLOUD_ACCOUNT_COST_SYNC_MARKER_MILLIS),
                QueryTask.NumericRange
                        .createDoubleRange(Double.MIN_VALUE, Double.MAX_VALUE, true, true));
        URI queryUri = UriUtils.extendUri(getMetricsServiceUri(),
                ServiceUriPaths.CORE_LOCAL_QUERY_TASKS);
        Operation.createPost(queryUri)
                .setBody(QueryTask.Builder.createDirectTask()
                        .addOption(QueryTask.QuerySpecification.QueryOption.SORT)
                        .addOption(QueryTask.QuerySpecification.QueryOption.TOP_RESULTS)
                        // No-op in photon-model. Required for special handling of immutable documents.
                        // This will prevent Lucene from holding the full result set in memory.
                        .addOption(QueryTask.QuerySpecification.QueryOption.INCLUDE_ALL_VERSIONS)
                        .addOption(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT)
                        .orderDescending(ServiceDocument.FIELD_NAME_SELF_LINK,
                                ServiceDocumentDescription.TypeName.STRING)
                        .setResultLimit(1)
                        .setQuery(builder.build()).build())
                .setCompletion((operation, exception) -> {
                    if (exception != null) {
                        handleError(context, Stages.GET_HISTORICAL_COSTS,
                                exception, true);
                        return;
                    }
                    QueryTask body = operation.getBody(QueryTask.class);
                    if (body == null || body.results == null || body.results.documents == null) {
                        context.billProcessedTimeMillis = 0;
                        return;
                    }
                    Collection<Object> values = body.results.documents.values();
                    if (!values.isEmpty()) {
                        if (values.iterator().next() == null) {
                            context.billProcessedTimeMillis = 0;
                            return;
                        }
                        ResourceMetricsService.ResourceMetrics rawResourceMetrics = Utils
                                .fromJson(values.iterator().next(),
                                        ResourceMetricsService.ResourceMetrics.class);
                        context.billProcessedTimeMillis = rawResourceMetrics.entries
                                .getOrDefault(
                                        PhotonModelConstants.CLOUD_ACCOUNT_COST_SYNC_MARKER_MILLIS,
                                        0d).longValue();
                    }
                    context.stage = next;
                    handleRequest(context);
                }).sendWith(this);

    }

    /**
     * Azure provides an API which will fetch the total EA account cost for a given month.
     * This API is invoked iteratively to the get the past (@code BILLS_BACK_IN_TIME_MONTHS_KEY}
     * months' cost.
     * @param context data holder for current run.
     * @param next next stage to proceed to.
     */
    @OldApi
    private void getPastAndCurrentMonthsEaAccountCostUsingOldApi(Context context, Stages next) {
        populateBillMonthToDownload(context,
                context.auth.customProperties.get(AzureConstants.AZURE_TENANT_ID));
        LocalDate billMonthToDownload = context.billMonthToDownload;
        List<Operation> summarizedBillOps = new ArrayList<>();
        while (!billMonthToDownload
                .isAfter(AzureCostStatsServiceHelper.getFirstDayOfCurrentMonth())) {
            // Get past BILLS_BACK_IN_TIME_MONTHS_KEY months' and the current month's
            // EA account cost.
            Operation summarizedBillOp = AzureCostStatsServiceHelper
                    .getOldBillOperation(context.auth.privateKeyId,
                            context.auth.privateKey,
                            billMonthToDownload,
                            AzureCostConstants.QUERY_PARAM_BILL_TYPE_VALUE_SUMMARY);
            summarizedBillOps.add(summarizedBillOp);
            billMonthToDownload = billMonthToDownload.plusMonths(1);
        }
        // Since billMonthToDownload has advanced until current month, subtract
        // NO_OF_DETAILED_BILLS_TO_DOWNLOAD to download detailed bills for the past
        // NO_OF_DETAILED_BILLS_TO_DOWNLOAD months including that of current month.
        context.billMonthToDownload = billMonthToDownload.minusMonths(
                NO_OF_DETAILED_BILLS_TO_DOWNLOAD);

        joinOldSummarizedBillOpsSendAndProcessRequest(context, next, summarizedBillOps);

    }

    /**
     * Azure provides an API which will fetch the total EA account cost for a given month.
     * This API is invoked iteratively to the get the past (@code BILLS_BACK_IN_TIME_MONTHS_KEY}
     * months' cost.
     * @param context data holder for current run.
     * @param next next stage to proceed to.
     */
    private void getPastAndCurrentMonthsEaAccountCost(Context context, Stages next) {
        populateBillMonthToDownload(context,
                context.auth.customProperties.get(AzureConstants.AZURE_TENANT_ID));
        LocalDate billMonthToDownload = context.billMonthToDownload;
        List<Operation> summarizedBillOps = new ArrayList<>();
        while (!billMonthToDownload
                .isAfter(AzureCostStatsServiceHelper.getFirstDayOfCurrentMonth())) {
            // Get past BILLS_BACK_IN_TIME_MONTHS_KEY months' and the current month's
            // EA account cost.
            Operation summarizedBillOp = AzureCostStatsServiceHelper
                    .getSummarizedBillOperation(context.auth.privateKeyId,
                            context.auth.privateKey,
                            billMonthToDownload);
            summarizedBillOps.add(summarizedBillOp);
            billMonthToDownload = billMonthToDownload.plusMonths(1);
        }
        // Since billMonthToDownload has advanced until current month, subtract
        // NO_OF_DETAILED_BILLS_TO_DOWNLOAD to download detailed bills for the past
        // NO_OF_DETAILED_BILLS_TO_DOWNLOAD months including that of current month.
        context.billMonthToDownload = billMonthToDownload.minusMonths(
                NO_OF_DETAILED_BILLS_TO_DOWNLOAD);

        joinSummarizedBillOpsSendAndProcessRequest(context, next, summarizedBillOps);

    }

    /**
     * Will set (@code Context.billMonthToDownload} to infer which months' bills
     * have to be downloaded. Summarized bills will be downloaded starting from this month up to
     * the current month. These number of months are configurable, specified by
     * the SystemProperty {@code BILLS_BACK_IN_TIME_MONTHS_KEY}
     * TODO gjobin: This will only determine which months' summarized bill need to be downloaded
     * Construct a method to determine which detailed bills need to be downloaded.
     * @param context has the context for this running thread, is used to populate
     *                the billProcessedTimeMillis
     * @param accountId of the account whose bill has to be downloaded
     */
    private void populateBillMonthToDownload(Context context, String accountId) {

        long billProcessedTime = context.billProcessedTimeMillis;
        LocalDate billProcessedLocalDate = new LocalDate(billProcessedTime, DateTimeZone.UTC);
        billProcessedLocalDate = billProcessedLocalDate
                .minusDays(AzureCostConstants.NO_OF_DAYS_MARGIN_FOR_AZURE_TO_UPDATE_BILL);
        int noPastMonthsBills = Integer.getInteger(BILLS_BACK_IN_TIME_MONTHS_KEY,
                AzureCostConstants.DEFAULT_NO_OF_MONTHS_TO_GET_PAST_BILLS);
        LocalDate currentMonth = LocalDate.now(DateTimeZone.UTC);
        // currentMonth will indicate the last day of the current month e.g. 31-MM-yyyy
        currentMonth = currentMonth.withDayOfMonth(currentMonth.dayOfMonth().getMaximumValue());
        LocalDate start = billProcessedLocalDate;
        if (billProcessedLocalDate.isBefore(currentMonth)) {
            LocalDate trendMinMonth = currentMonth.minusMonths(noPastMonthsBills);
            if (billProcessedLocalDate.isBefore(trendMinMonth)) {
                start = trendMinMonth;
            }
        }
        context.billMonthToDownload = start.withDayOfMonth(1);
        logFine(() -> String.format("Downloading Azure account %s bills since: %s.", accountId,
                context.billMonthToDownload));
    }

    /**
     * Gets the detailed bill for the current month. Currently, the bill is obtained in JSON format.
     * @param context data holder for the current run.
     * @param next the next stage to proceed to.
     */
    private void downloadDetailedBill(Context context, Stages next) {
        EaAccountCost eaAccountCost = context.eaAccountCost
                .get(AzureCostStatsServiceHelper.getFirstDayOfCurrentMonth());
        if (eaAccountCost != null) {
            Double collectedCurrentMonthEaUsageCost = eaAccountCost.monthlyEaAccountUsageCost;
            if (collectedCurrentMonthEaUsageCost.equals(context.storedCurrentMonthEaUsageCost)) {
                logInfo(() -> "Microsoft Azure hasn't updated the bill since the last run."
                        + "Aborting this run.");
                postStats(context, true);
                return;
            }
        }
        this.executor.submit(() -> {
            // Restore context since this is a new thread
            OperationContext.restoreOperationContext(context.opContext);
            final LocalDate firstDayOfCurrentMonth = AzureCostStatsServiceHelper
                    .getFirstDayOfCurrentMonth();

            Request oldDetailedBillRequest = AzureCostStatsServiceHelper
                    .getOldDetailedBillRequest(context.auth.privateKeyId,
                            context.auth.privateKey, firstDayOfCurrentMonth);
            OkHttpClient.Builder b = new OkHttpClient.Builder();
            b.readTimeout(AzureCostConstants.READ_TIMEOUT_FOR_REQUESTS, TimeUnit.MILLISECONDS);
            b.connectTimeout(AzureCostConstants.READ_TIMEOUT_FOR_REQUESTS, TimeUnit.MILLISECONDS);

            b.interceptors().add(this::createRequestRetryInterceptor);

            OkHttpClient client = b.build();

            client.newCall(oldDetailedBillRequest).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException ioEx) {
                    // Restore context on failure to enable sending failure patch
                    OperationContext.restoreOperationContext(context.opContext);
                    handleError(context, next, ioEx, false);
                }

                @Override
                public void onResponse(Call call, final Response response) throws IOException {
                    // Restore context on success
                    OperationContext.restoreOperationContext(context.opContext);
                    if (response == null || !response.isSuccessful()) {
                        if (response == null) {
                            handleError(context, next,
                                    new Exception("Empty response obtained from Azure"), false);
                        } else {
                            logSevere(() -> String
                                    .format("Unexpected response obtained from Azure: %s",
                                            response));
                            handleError(context, next, new Exception(response.toString()), false);
                        }
                    } else {
                        final Path workingDirPath = Paths.get(System
                                        .getProperty(AzureCostStatsServiceHelper.TEMP_DIR_LOCATION),
                                UUID.randomUUID().toString());
                        Path workingDirectory = Files.createDirectories(workingDirPath);
                        File downloadedFile = new File(workingDirectory.toString(),
                                AzureCostStatsServiceHelper
                                        .getCsvBillFileName(context.auth.privateKeyId,
                                                firstDayOfCurrentMonth));
                        try (BufferedSink sink = Okio.buffer(Okio.sink(downloadedFile))) {
                            sink.writeAll(response.body().source());
                        } catch (Exception ex) {
                            logWarning(() -> String
                                    .format("Unexpected data obtained from Azure: %s",
                                            Utils.toString(ex)));
                            handleError(context, next, ex, false);
                            return;
                        }
                        context.stage = next;
                        context.billFile = downloadedFile;
                        handleRequest(context);
                    }
                }
            });
        });
    }

    private Response createRequestRetryInterceptor(Interceptor.Chain chain) throws IOException {
        Request request = chain.request();
        // try the request
        Response response = chain.proceed(request);
        int tryCount = 0;
        while (!response.isSuccessful()
                && tryCount < AzureCostConstants.MAX_RETRIES_ON_REQUEST_FAILURE) {
            Response finalResponse = response;
            logWarning(() -> String
                    .format("Unexpected response obtained from Azure: %s",
                            finalResponse));

            tryCount++;
            // retry the request
            response = chain.proceed(request);
        }
        // otherwise just pass the original response on
        return response;
    }

    /**
     * Process the detailed bill downloaded from Azure, create stats for respective entities
     * and post the stats.
     * @param context data holder for the current run.
     */
    private void parseDetailedBill(Context context, Stages next) {
        try {
            AzureDetailedBillHandler billHandler = new AzureDetailedBillHandler();
            if (context.csvReader == null) {
                context.csvReader = new CSVReader(new FileReader(context.billFile),
                        AzureCostConstants.DEFAULT_COLUMN_SEPARATOR,
                        AzureCostConstants.DEFAULT_QUOTE_CHARACTER,
                        AzureCostConstants.DEFAULT_ESCAPE_CHARACTER,
                        AzureCostConstants.DEFAULT_LINES_TO_SKIP);
            }
            boolean parsingComplete = billHandler
                    .parseDetailedCsv(context.csvReader, context.billProcessedTimeMillis,
                            context.currency, getDailyStatsConsumer(context, next));
            if (parsingComplete) {
                cleanUp(context);
            }
        } catch (IOException ioException) {
            handleError(context, null, ioException, false);
        }
    }

    /**
     * The user adds the EA account as the end-point. By parsing the bill, we understand there are
     * one or more subscriptions, resources whose compute states need to be created in order
     * to store cost information for those entities.
     */
    private void createMissingComputeStates(Context context,
            Stages next, List<AzureSubscription> newSubscriptions) {
        logInfo(() -> String
                .format("Creating compute states for the following subscriptions: %s ",
                        newSubscriptions.toString()));
        AzureSubscriptionsEnumerationRequest request = new AzureSubscriptionsEnumerationRequest();
        request.resourceReference = UriUtils
                .extendUri(getInventoryServiceUri(), context.computeHostDesc.documentSelfLink);
        request.azureSubscriptions = newSubscriptions;
        Operation.createPatch(getHost(), AzureSubscriptionsEnumerationService.SELF_LINK)
                .setBody(request)
                .setCompletion((operation, exception) -> {
                    logInfo(() -> ("Finished creating compute states for subscriptions."));
                    context.stage = next;
                    handleRequest(context);
                }).sendWith(this);
    }

    private void queryLinkedSubscriptions(Context context, Stages next) {
        // Construct a list of query operations, one for each subscription ID to query the
        // corresponding compute states and populate in the context object.
        String[] linkedSubscriptionGuids = context.computeHostDesc.customProperties
                .getOrDefault(AzureCostConstants.LINKED_SUBSCRIPTION_GUIDS, "")
                .split(",");

        List<Operation> queryOps = Arrays
                .stream(linkedSubscriptionGuids)
                .filter(subscriptionGuid -> subscriptionGuid != null && !subscriptionGuid.isEmpty())
                .map(subscriptionGuid -> createQueryForComputeStatesBySubscription(context,
                        subscriptionGuid,
                        (subscriptionComputeState) -> context.subscriptionGuidToComputeState
                                .put(subscriptionGuid, subscriptionComputeState)))
                .collect(Collectors.toList());
        joinOpSendRequest(context, queryOps, next);
    }

    /**
     * Creates a query operation to get the compute states corresponding to the specified
     * subscription GUID. The list of the resultant compute states is then passed to the specified
     * handler for processing.
     *
     * @return operation object representing the query
     */
    private Operation createQueryForComputeStatesBySubscription(Context context,
            String subscriptionGuid, Consumer<ComputeState> queryResultConsumer) {
        QueryTask.Query azureSubscriptionsQuery = QueryTask.Query.Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addCompositeFieldClause(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                        EndpointAllocationTaskService.CUSTOM_PROP_ENPOINT_TYPE,
                        PhotonModelConstants.EndpointType.azure.name())
                .addCompositeFieldClause(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                        PhotonModelConstants.CLOUD_ACCOUNT_ID, subscriptionGuid)
                .addFieldClause(ComputeState.FIELD_NAME_TYPE,
                        ComputeDescriptionService.ComputeDescription.ComputeType.VM_HOST)
                .build();
        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .addOption(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT)
                .setQuery(azureSubscriptionsQuery).build();
        queryTask.setDirect(true);
        queryTask.tenantLinks = context.computeHostDesc.tenantLinks;
        return Operation.createPost(UriUtils.extendUri(getInventoryServiceUri(),
                ServiceUriPaths.CORE_LOCAL_QUERY_TASKS))
                .setBody(queryTask)
                .setConnectionSharing(true)
                .setCompletion((operation, exception) -> {
                    if (exception != null) {
                        getFailureConsumer(context).accept(exception);
                        return;
                    }
                    QueryTask responseTask = operation.getBody(QueryTask.class);
                    if (responseTask == null || responseTask.results == null
                            || responseTask.results.documents == null) {
                        // Couldn't find compute state for subscription with specified GUID
                        return;
                    }
                    List<ComputeState> subscriptionComputeState = responseTask.results.documents
                            .values().stream()
                            .map(s -> Utils.fromJson(s, ComputeState.class))
                            .filter(cs -> cs.endpointLink != null)
                            .collect(Collectors.toList());
                    if (subscriptionComputeState == null || subscriptionComputeState.size() == 0) {
                        return;
                    }
                    queryResultConsumer.accept(subscriptionComputeState.get(0));
                });
    }

    /**
     * Consumes the daily bill rows and creates stats
     */
    private BiConsumer<Map<String, AzureSubscription>, Long>  getDailyStatsConsumer(
            Context context, Stages next) {
        return (newMonthlyBillBatch, parsingCompleteTimeMillis) -> {
            if (parsingCompleteTimeMillis != null) {
                // This means bill parsing is complete
                context.billParsingCompleteTimeMillis = parsingCompleteTimeMillis;
            }
            List<AzureSubscription> newSubscriptions = getNewSubscriptions(
                    newMonthlyBillBatch, context.allSubscriptionsCost);
            populateMonthlySubscriptionCost(context, newMonthlyBillBatch);
            context.monthlyBillBatch = newMonthlyBillBatch;
            // Call the subscription compute state enumeration service here and pass
            if (newSubscriptions.size() > 0) {
                // If there are compute states that need to be created, then first create them,
                // get the compute states for the newly created subscriptions and proceed with
                // next stage.
                createMissingComputeStates(context, next, newSubscriptions);
            } else {
                // If there aren't any new subscriptions, proceed with next stage.
                context.stage = next;
                handleRequest(context);
            }
        };
    }

    /**
     * Populate the total cost of each subscription for a given month.
     * @param context the data holder for the current run.
     * @param newMonthlyBillBatch new batch of rows of the bill.
     */
    private void populateMonthlySubscriptionCost(Context context,
            Map<String, AzureSubscription> newMonthlyBillBatch) {
        for (AzureSubscription subscription : newMonthlyBillBatch.values()) {
            String subscriptionGuid = subscription.entityId;
            if (context.allSubscriptionsCost.get(subscriptionGuid) == null) {
                AzureSubscription newSubscription = new AzureSubscription(
                        subscriptionGuid, subscription.entityName, subscription.parentEntityId,
                        subscription.parentEntityName);
                newSubscription.addCost(subscription.getCost());
                context.allSubscriptionsCost.put(subscriptionGuid, newSubscription);
            } else {
                AzureSubscription existingSubscription = context.allSubscriptionsCost
                        .get(subscriptionGuid);
                context.allSubscriptionsCost.put(subscriptionGuid,
                        existingSubscription.addCost(subscription.getCost()));
            }
        }
    }

    /**
     * Gets two maps of subscriptions and returns the difference between the two
     * @param newBillBatch map of subscriptions before parsing new batch of rows.
     * @param oldSubscriptions map of all subscriptions
     * @return the difference as a list of subscriptions between the two maps of subscriptions
     */
    private List<AzureSubscription> getNewSubscriptions(Map<String, AzureSubscription> newBillBatch,
            Map<String, AzureSubscription> oldSubscriptions) {
        return newBillBatch.entrySet().stream()
                .filter(e -> !oldSubscriptions.containsKey(e.getKey()))
                .map(Entry::getValue)
                .collect(Collectors.toList());
    }

    private void createStats(Context context) {
        context.monthlyBillBatch.values().forEach(subscription -> {
            subscription.getServices().values()
                    .forEach(service -> createServiceStatsForSubscription(context, subscription));
            subscription.getServices().clear();
        });
        if (context.statsResponse.statsList.size() == 0) {
            return;
        }
        postStats(context, false);
        // Check if the entire bill is parsed
        if (context.billParsingCompleteTimeMillis > 0) {
            context.stage = Stages.CREATE_MONTHLY_STATS_AND_POST;
        } else {
            context.stage = Stages.PARSE_DETAILED_BILL;
        }
        handleRequest(context);
    }

    private void createMonthlyStats(Context context, Stages next) {
        context.billProcessedTimeMillis = context.billParsingCompleteTimeMillis;
        context.allSubscriptionsCost.values()
                .forEach(subscription -> createAzureSubscriptionStats(context, subscription));
        List<ComputeStats> eaAccountStats = createEaAccountStats(
                context.eaAccountCost, context.computeHostDesc,
                context.billParsingCompleteTimeMillis, context.auth.privateKey);
        if (eaAccountStats.size() > 0) {
            context.statsResponse.statsList.addAll(eaAccountStats);
            postStats(context, true);
            logInfo(() -> "Finished collecting cost stats.");
        }
        context.stage = next;
        handleRequest(context);
    }

    // Create Azure account stats
    private void createAzureSubscriptionStats(Context context, AzureSubscription subscription) {
        Consumer<ComputeState> subscriptionStatsProcessor = (subscriptionComputeState) -> {
            String costStatName = AzureStatsNormalizer
                    .getNormalizedStatKeyValue(AzureCostConstants.COST);
            String costUnit = AzureStatsNormalizer.getNormalizedUnitValue(DEFAULT_CURRENCY_VALUE);

            ComputeStats subscriptionStats = new ComputeStats();
            subscriptionStats.computeLink = subscriptionComputeState.documentSelfLink;
            subscriptionStats.statValues = new ConcurrentSkipListMap<>();
            ServiceStat azureAccountStat = AzureCostStatsServiceHelper
                    .createServiceStat(costStatName, subscription.getCost(), costUnit,
                            context.billProcessedTimeMillis);
            subscriptionStats.statValues
                    .put(costStatName, Collections.singletonList(azureAccountStat));
            context.statsResponse.statsList.add(subscriptionStats);

        };
        processSubscriptionStats(context, subscription, subscriptionStatsProcessor);
    }

    private void setLinkedSubscriptionGuids(Context context, Stages next) {
        Map<String, String> eaAccountProps = context.computeHostDesc.customProperties;

        // Get the list of all unique subscription GUIDs(comma separated list).
        // The list is sorted to get the same list independent of order of subscription GUIDs.
        String linkedSubsGuids = context.allSubscriptionsCost.values().stream()
                .map(subscription -> subscription.entityId).distinct().sorted()
                .collect(Collectors.joining(","));

        String prevLinkedSubsGuids = eaAccountProps
                .getOrDefault(AzureCostConstants.LINKED_SUBSCRIPTION_GUIDS, "");
        if (!prevLinkedSubsGuids.equals(linkedSubsGuids)) {
            setCustomProperty(context, AzureCostConstants.LINKED_SUBSCRIPTION_GUIDS,
                    linkedSubsGuids, next);
        } else {
            context.stage = next;
            handleRequest(context);
        }
    }

    /**
     * EA account cost stats are created on a month-level. The usage cost, marketplace cost,
     * separately billed cost and total cost are stored on a month-level.
     * @param monthlyEaAccountCost map of monthly EA usage, marketplace and separately billed cost.
     * @param computeHostDesc compute host of EA account
     * @param billProcessedTime time when the bill was processed in the current run.
     * @param privateKey the usage API key.
     * @return list of created stats.
     */
    private static List<ComputeStats> createEaAccountStats(
            Map<LocalDate, EaAccountCost> monthlyEaAccountCost,
            ComputeStateWithDescription computeHostDesc, long billProcessedTime,
            String privateKey) {
        List<ComputeStats> eaAccountStats = new ArrayList<>();

        Map<LocalDate, Double> monthlyEaAccountTotalCost = getEaAccountTotalCost(
                monthlyEaAccountCost);

        // Create and add EA account usage, marketplace, separately billed and total cost
        // stats to all stats list.
        eaAccountStats.addAll(createMonthlyEaAccountCostStats(monthlyEaAccountCost,
                monthlyEaAccountTotalCost, computeHostDesc));

        eaAccountStats.add(createBillProcessedTimeStat(computeHostDesc, billProcessedTime));

        eaAccountStats.add(createApiKeyExpiresTimeStat(computeHostDesc,
                AzureCostStatsServiceHelper.getUsageKeyExpiryTime(privateKey)));
        return eaAccountStats;
    }

    /**
     * Computes the total account cost incurred for a month for an EA account.
     * Total cost = usage cost + marketplace cost + separately billed cost
     * @param monthlyEaAccountCost map of monthly EA usage, marketplace and separately billed cost.
     * @return the map of monthly total cost for an EA account.
     */
    private static Map<LocalDate, Double> getEaAccountTotalCost(
            Map<LocalDate, EaAccountCost> monthlyEaAccountCost) {
        Map<LocalDate, Double> monthlyTotalCost = new HashMap<>();
        for (Entry<LocalDate, EaAccountCost> accountCostEntry : monthlyEaAccountCost.entrySet()) {
            LocalDate month = accountCostEntry.getKey();
            EaAccountCost accountCost = accountCostEntry.getValue();
            double totalCost = accountCost.monthlyEaAccountUsageCost
                    + accountCost.monthlyEaAccountMarketplaceCost
                    + accountCost.monthlyEaAccountSeparatelyBilledCost;
            monthlyTotalCost.put(month, totalCost);
        }
        return monthlyTotalCost;
    }

    private static List<ComputeStats> createMonthlyEaAccountCostStats(
            Map<LocalDate, EaAccountCost> monthlyEaAccountCosts,
            Map<LocalDate, Double> monthlyTotalCosts,
            ComputeStateWithDescription computeHostDesc) {

        List<ComputeStats> accountStats = new ArrayList<>();
        String usageCostStatName = AzureCostConstants.USAGE_COST;
        String marketplaceCostStatName = AzureCostConstants.MARKETPLACE_COST;
        String separatelyBilledCostStatName = AzureCostConstants.SEPARATELY_BILLED_COST;
        String costUnit = AzureStatsNormalizer.getNormalizedUnitValue(DEFAULT_CURRENCY_VALUE);

        for (Entry<LocalDate, Double> monthlyTotalCost : monthlyTotalCosts.entrySet()) {
            ComputeStats accountStat = new ComputeStats();
            accountStat.computeLink = computeHostDesc.documentSelfLink;
            accountStat.statValues = new ConcurrentSkipListMap<>();

            LocalDate month = monthlyTotalCost.getKey();
            long timeStamp = adaptMonthToCostTimeStamp(month);
            EaAccountCost eaAccountCost = monthlyEaAccountCosts.get(month);
            ServiceStat usageCostServiceStat = AzureCostStatsServiceHelper
                    .createServiceStat(usageCostStatName, eaAccountCost.monthlyEaAccountUsageCost,
                            costUnit, timeStamp);
            ServiceStat marketplaceCostServiceStat = AzureCostStatsServiceHelper
                    .createServiceStat(marketplaceCostStatName,
                            eaAccountCost.monthlyEaAccountMarketplaceCost,
                            costUnit, timeStamp);
            ServiceStat separatelyBilledCostServiceStat = AzureCostStatsServiceHelper
                    .createServiceStat(separatelyBilledCostStatName,
                            eaAccountCost.monthlyEaAccountSeparatelyBilledCost,
                            costUnit, timeStamp);

            accountStat.statValues
                    .put(usageCostStatName, Collections.singletonList(usageCostServiceStat));
            accountStat.statValues.put(marketplaceCostStatName,
                    Collections.singletonList(marketplaceCostServiceStat));
            accountStat.statValues.put(separatelyBilledCostStatName,
                    Collections.singletonList(separatelyBilledCostServiceStat));
            accountStats.add(accountStat);
        }
        return accountStats;
    }

    private static long adaptMonthToCostTimeStamp(LocalDate month) {
        if (AzureCostStatsServiceHelper.isCurrentMonth(month)) {
            return AzureCostStatsServiceHelper.getMillisForDate(LocalDate.now(DateTimeZone.UTC));
        } else {
            return AzureCostStatsServiceHelper
                    .getMillisForDate(month.dayOfMonth().withMaximumValue());
        }
    }

    private static ComputeStats createBillProcessedTimeStat(
            ComputeStateWithDescription computeHostDesc, long billProcessedTime) {
        // Create bill processed time stat: will be linked with the EA account's compute state
        // since the bill is obtained and processed at the EA account level.
        ComputeStats accountStats = new ComputeStats();
        accountStats.computeLink = computeHostDesc.documentSelfLink;
        accountStats.statValues = new ConcurrentSkipListMap<>();

        ServiceStat billProcessedTimeStat = AzureCostStatsServiceHelper.createServiceStat(
                PhotonModelConstants.CLOUD_ACCOUNT_COST_SYNC_MARKER_MILLIS,
                billProcessedTime,
                PhotonModelConstants.UNIT_MILLISECONDS,
                billProcessedTime);
        accountStats.statValues.put(billProcessedTimeStat.name,
                Collections.singletonList(billProcessedTimeStat));
        return accountStats;
    }

    private static ComputeStats createApiKeyExpiresTimeStat(
            ComputeStateWithDescription computeHostDesc,
            long keyExpiryTime) {
        ComputeStats accountStats = new ComputeStats();
        accountStats.computeLink = computeHostDesc.documentSelfLink;
        accountStats.statValues = new ConcurrentSkipListMap<>();

        ServiceStat keyExpiryTimeMillis = AzureCostStatsServiceHelper.createServiceStat(
                AzureCostConstants.EA_ACCOUNT_USAGE_KEY_EXPIRY_TIME_MILLIS,
                keyExpiryTime,
                PhotonModelConstants.UNIT_MILLISECONDS,
                AzureCostStatsServiceHelper.getMillisNow());
        accountStats.statValues.put(keyExpiryTimeMillis.name,
                Collections.singletonList(keyExpiryTimeMillis));
        return accountStats;
    }

    private void setCustomProperty(Context context, String key, String value, Stages next) {
        context.computeHostDesc.customProperties.put(key, value);
        ComputeState accountState = new ComputeState();
        accountState.customProperties = new HashMap<>();
        accountState.customProperties.put(key, value);
        sendRequest(Operation.createPatch(UriUtils.extendUri(getInventoryServiceUri(),
                context.computeHostDesc.documentSelfLink))
                .setBody(accountState)
                .setCompletion((operation, exception) -> {
                    if (exception != null) {
                        handleError(context, null, exception, false);
                        return;
                    }
                    context.stage = next;
                    handleRequest(context);
                }));
    }

    private void processSubscriptionStats(Context context,
            AzureSubscription subscription, Consumer<ComputeState> processor) {
        ComputeState subscriptionComputeState = context.subscriptionGuidToComputeState
                .getOrDefault(subscription.entityId, null);
        if (subscriptionComputeState == null) {
            logWarning(() -> String.format("Could not find compute state for Azure subscription "
                            + "with ID '%s'. Not creating cost metrics for the same.",
                    subscription.entityId));
            return;
        }
        processor.accept(subscriptionComputeState);
    }

    private void createServiceStatsForSubscription(Context context,
            AzureSubscription subscription) {
        Consumer<ComputeState> serviceStatsProcessor = (subscriptionComputeState) -> {
            ComputeStats subscriptionStats = new ComputeStats();
            subscriptionStats.statValues = new ConcurrentHashMap<>();
            subscriptionStats.computeLink = subscriptionComputeState.documentSelfLink;
            for (AzureService service : subscription.getServices().values()) {
                Map<String, List<ServiceStat>> statsForAzureService = createStatsForAzureService(
                        service);
                subscriptionStats.statValues.putAll(statsForAzureService);
            }
            if (!subscriptionStats.statValues.isEmpty()) {
                context.statsResponse.statsList.add(subscriptionStats);
            }
        };
        processSubscriptionStats(context, subscription, serviceStatsProcessor);
    }

    private Map<String, List<ServiceStat>> createStatsForAzureService(AzureService service) {
        String currencyUnit = AzureStatsNormalizer.getNormalizedUnitValue(DEFAULT_CURRENCY_VALUE);
        String serviceCode = service.meterCategory.replaceAll(" ", "");
        Map<String, List<ServiceStat>> stats = new HashMap<>();

        // Create stats for daily resource cost
        List<ServiceStat> serviceStats = new ArrayList<>();
        String serviceResourceCostMetric = String
                .format(AzureCostConstants.SERVICE_RESOURCE_COST, serviceCode);
        for (Entry<Long, Double> cost : service.directCosts.entrySet()) {
            ServiceStat resourceCostStat = AzureCostStatsServiceHelper
                    .createServiceStat(serviceResourceCostMetric,
                            cost.getValue(), currencyUnit, cost.getKey());
            serviceStats.add(resourceCostStat);
        }
        if (!serviceStats.isEmpty()) {
            stats.put(serviceResourceCostMetric, serviceStats);
        }

        return stats;
    }

    /**
     * Send stats for persistence.
     * @param context Holds data to be posted for persistence in
     * {@code context.statsResponse.statsList}
     * @param isFinalBatch indicates if the batch of stats being posted is the last batch in
     *                     this run.
     */
    private void postStats(Context context, boolean isFinalBatch) {
        SingleResourceStatsCollectionTaskState respBody =
                new SingleResourceStatsCollectionTaskState();
        respBody.taskStage = SingleResourceTaskCollectionStage
                .valueOf(context.statsRequest.nextStage);
        respBody.statsAdapterReference = UriUtils.buildUri(getHost(), SELF_LINK);
        respBody.statsList = context.statsResponse.statsList;
        respBody.computeLink = context.computeHostDesc.documentSelfLink;
        respBody.isFinalBatch = isFinalBatch;
        sendRequest(Operation.createPatch(context.statsRequest.taskReference).setBody(respBody)
                .setCompletion((operation, exception) -> {
                    if (exception != null) {
                        handleError(context, null, exception, false);
                    }
                }));
        context.statsResponse.statsList = new ArrayList<>();
    }

    private void joinOpSendRequest(Context context,
            List<Operation> queryOps, Stages next) {

        if (queryOps.isEmpty()) {
            return;
        }

        OperationJoin
                .create(queryOps)
                .setCompletion((operations, exceptions) -> {
                    if (exceptions != null && !exceptions.isEmpty()) {
                        Throwable firstException = exceptions.values().iterator().next();
                        getFailureConsumer(context).accept(firstException);
                    }
                    context.stage = next;
                    handleRequest(context);
                }).sendWith(this, AzureCostConstants.OPERATION_BATCH_SIZE);
    }

    /**
     * Sends a request to Azure to get the overall EA account cost for past months and processes
     * the response obtained to populate the account cost into a map of the month and the cost
     * values.
     * @param context the data holder for the current run.
     * @param next the stage to proceed to.
     * @param queryOps the list of operations to send.
     */
    @OldApi
    private void joinOldSummarizedBillOpsSendAndProcessRequest(Context context,
            Stages next, List<Operation> queryOps) {
        OperationJoin.create(queryOps).setCompletion((operations, exceptions) -> {
            handleJoinOpErrors(context, next, exceptions);
            for (Entry<Long, Operation> operationEntry : operations.entrySet()) {
                try {
                    if (operationEntry == null || operationEntry.getValue() == null
                            || operationEntry.getValue().getUri() == null) {
                        continue;
                    }
                    String requestUri = operationEntry.getValue().getUri().toString();
                    if (operationEntry.getValue() == null
                            || operationEntry.getValue().getBodyRaw() == null) {
                        logWarning(() -> String
                                .format("Error retrieving bill from Azure for the operation: %s.",
                                        requestUri));
                        continue;
                    }
                    logFine(() -> String
                            .format("Got cost for %d months' cost from Azure.", operations.size()));
                    if (operationEntry.getValue().getBodyRaw() instanceof ServiceErrorResponse) {
                        logInfo(() -> String
                                .format("Obtained response from Azure: %s",
                                        operationEntry.getValue()
                                                .getBody(ServiceErrorResponse.class)));
                    }
                    OldEaSummarizedBillElement[] summarizedBillElements = operationEntry.getValue()
                            .getBody(OldEaSummarizedBillElement[].class);
                    if (summarizedBillElements != null) {
                        populatePastAndCurrentMonthsEaAccountCostUsingOldApi(context, requestUri,
                                summarizedBillElements);
                    } else {
                        Throwable throwable = new Throwable(
                                "Summarized bill request: " + requestUri
                                        + " returned unexpected response: " + operationEntry
                                        .getValue()
                                        .toString(), null);
                        handleError(context, next, throwable, true);
                        return;
                    }
                } catch (Exception ex) {
                    handleError(context, null, ex, false);
                    return;
                }
            }
            // Go to next stage once we have tried getting the cost of all required months
            context.stage = next;
            handleRequest(context);
        }).sendWith(this, AzureCostConstants.OPERATION_BATCH_SIZE);
    }

    /**
     * Sends a request to Azure to get the overall EA account cost for past months and processes
     * the response obtained to populate the account cost into a map of the month and the cost
     * values.
     * @param context the data holder for the current run.
     * @param next the stage to proceed to.
     * @param queryOps the list of operations to send.
     */
    @OldApi
    private void joinSummarizedBillOpsSendAndProcessRequest(Context context,
            Stages next, List<Operation> queryOps) {
        OperationJoin.create(queryOps).setCompletion((operations, exceptions) -> {
            handleJoinOpErrors(context, next, exceptions);
            for (Entry<Long, Operation> operationEntry : operations.entrySet()) {
                try {
                    if (operationEntry == null || operationEntry.getValue() == null
                            || operationEntry.getValue().getUri() == null) {
                        logWarning(() -> "Error retrieving bill from Azure for the operation: %s.");
                        continue;
                    }
                    String requestUri = operationEntry.getValue().getUri().toString();
                    logFine(() -> String
                            .format("Obtained response from Azure: %s",
                                    operationEntry.getValue().getBodyRaw()));
                    if (operationEntry.getValue().getBodyRaw() instanceof ServiceErrorResponse) {
                        logInfo(() -> String
                                .format("Obtained response from Azure: %s",
                                        operationEntry.getValue()
                                                .getBody(ServiceErrorResponse.class)));
                        handleError(context, next,
                                new Exception(operationEntry.getValue().getBodyRaw().toString()),
                                true);
                        return;
                    }
                    if (operationEntry.getValue().getBodyRaw().toString().contains("error")) {
                        AzureErrorResponse errorResp = operationEntry.getValue()
                                .getBody(AzureErrorResponse.class);
                        Exception ex = new Exception((errorResp.message));
                        logWarning(
                                () -> String.format("Obtained response from Azure: %s", errorResp));
                        handleError(context, next, ex, true);
                        return;
                    }

                    EaSummarizedBillElement summarizedBillElements = operationEntry.getValue()
                            .getBody(EaSummarizedBillElement.class);
                    if (summarizedBillElements != null) {
                        populatePastAndCurrentMonthsEaAccountCost(context, requestUri,
                                summarizedBillElements);
                    } else {
                        Throwable throwable = new Throwable(
                                "Summarized bill request: " + requestUri
                                        + " returned unexpected response: " + operationEntry
                                        .getValue()
                                        .toString(), null);
                        handleError(context, next, throwable, true);
                        return;
                    }
                } catch (Exception ex) {
                    handleError(context, null, ex, false);
                    return;
                }
            }
            // Go to next stage once we have tried getting the cost of all required months
            context.stage = next;
            handleRequest(context);
        }).sendWith(this, AzureCostConstants.OPERATION_BATCH_SIZE);
    }

    /**
     * Populates the cost incurred on the overall EA account in the past months.
     * @param context the data holder for the current run
     * @param requestUri the request URI: this is needed to get the correlate corresponding to
     *                   the cost obtained in the response
     * @param summarizedBillElements the response obtained from Azure, contains the account cost
     */
    @OldApi
    private void populatePastAndCurrentMonthsEaAccountCostUsingOldApi(
            Context context,
            String requestUri, OldEaSummarizedBillElement[] summarizedBillElements) {
        // Set the account cost to null (NOT zero) by default. If the cost obtained
        // was NOT a parse-able Double value, we will have null values. NOT setting to zero since
        // genuine zero values will then be unidentifiable; this will
        // help to distinguish unknown values from real zero costs.
        Double accountUsageCost = null;
        Double accountMarketplaceCost = null;
        Double accountSeparatelyBilledCost = null;
        Double initialBalance = 0d;
        String currency = null;
        LocalDate date = null;
        LocalDate billMonth = AzureCostStatsServiceHelper.getMonthFromOldRequestUri(requestUri);
        for (OldEaSummarizedBillElement billElement : summarizedBillElements) {
            billElement = AzureCostStatsServiceHelper
                    .sanitizeSummarizedBillElementUsingOldApi(billElement);
            String serviceCommitment = billElement.serviceCommitment;
            context.currency = DEFAULT_CURRENCY_VALUE;
            if (serviceCommitment == null) {
                continue;
            }
            switch (serviceCommitment) {
            case AzureCostConstants.SERVICE_COMMITMENT_TOTAL_USAGE:
                try {
                    accountUsageCost = Double.valueOf(billElement.amount);
                    currency = billElement.currencyCode;
                } catch (NumberFormatException numberFormatEx) {
                    OldEaSummarizedBillElement finalBillElement = billElement;
                    logWarning(() -> String
                            .format("Could not convert cost obtained from summarized bill " +
                                    "to a double value: %s", finalBillElement));
                }
                break;
            case AzureCostConstants.SERVICE_COMMITMENT_BEGINNING_BALANCE:
                initialBalance = getBillElementAmountUsingOldApi(billElement);
                break;
            case AzureCostConstants.SERVICE_COMMITMENT_REPORT_GENERATION_DATE:
                final DateTimeFormatter formatter = DateTimeFormat
                        .forPattern(
                                AzureCostConstants.SERVICE_COMMITMENT_REPORT_GENERATION_DATE_FORMAT);
                date = formatter.parseLocalDate(billElement.amount);
                // The report generation date will the chronologically last date in the detailed bill.
                context.billProcessedTimeMillis = AzureCostStatsServiceHelper
                        .getMillisForDate(date);
                break;
            case AzureCostConstants.SERVICE_COMMITMENT_CHARGES_BILLED_SEPARATELY:
                accountSeparatelyBilledCost = getBillElementAmountUsingOldApi(billElement);
                break;
            case AzureCostConstants.SERVICE_COMMITMENT_MARKETPLACE_SERVICE_CHARGES_BILLED_SEPARATELY:
                accountMarketplaceCost = getBillElementAmountUsingOldApi(billElement);
                break;
            default:
                break;
            }
        }
        setEaAccountCosts(context, billMonth, initialBalance, accountUsageCost,
                accountMarketplaceCost, accountSeparatelyBilledCost);
        if (date != null && (AzureCostStatsServiceHelper.isCurrentMonth(date)
                || (AzureCostStatsServiceHelper.isPreviousMonth(date)))) {
            // Pick the currency for current month or the month just before
            // the current month. The past month's currency is taken since there may be
            // cases when the current month's bill is not available and based on the
            // assumption that currency won't change within a span of a few days and if it
            // does, it will be corrected when the current month's bill is generated.
            context.currency = currency;
        }
    }

    /**
     * Populates the cost incurred on the overall EA account in the past months.
     * @param context the data holder for the current run
     * @param requestUri the request URI: this is needed to get the correlate corresponding to
     *                   the cost obtained in the response
     * @param billElement the response obtained from Azure, contains the account cost
     */
    @OldApi
    private void populatePastAndCurrentMonthsEaAccountCost(Context context,
            String requestUri, EaSummarizedBillElement billElement) {
        // Set the account cost to null (NOT zero) by default. If the cost obtained
        // was NOT a parse-able Double value, we will have null values. NOT setting to zero since
        // genuine zero values will then be unidentifiable; this will
        // help to distinguish unknown values from real zero costs.
        Double initialBalance;
        LocalDate billMonth = AzureCostStatsServiceHelper.getMonthFromRequestUri(requestUri);
        context.currency = DEFAULT_CURRENCY_VALUE;
        if (AzureCostConstants.exchangeRates.get(billElement.currencyCode) != null) {
            context.currency = billElement.currencyCode;
        }
        Double accountUsageCost = AzureCostStatsServiceHelper
                .getDoubleOrNull(billElement, billElement.totalUsage);
        // If we don't get a proper initial balance, we set it to zero: for ease in computing
        // costs and since there should not be any harm
        initialBalance = AzureCostStatsServiceHelper
                .getDoubleOrZero(billElement, billElement.beginningBalance);
        Double accountSeparatelyBilledCost = AzureCostStatsServiceHelper
                .getDoubleOrNull(billElement,
                        billElement.chargesBilledSeparately);
        Double accountMarketplaceCost = AzureCostStatsServiceHelper.getDoubleOrNull(billElement,
                billElement.azureMarketplaceServiceCharges);
        setEaAccountCosts(context, billMonth, initialBalance, accountUsageCost,
                accountMarketplaceCost, accountSeparatelyBilledCost);
    }

    @OldApi
    private Double getBillElementAmountUsingOldApi(OldEaSummarizedBillElement billElement) {
        Double value = null;
        try {
            value = Double.valueOf(billElement.amount);
        } catch (NumberFormatException numberFormatEx) {
            logWarning(() -> String
                    .format("Could not convert cost obtained from summarized bill " +
                            "to a double value: %s", billElement));
        }
        return value;
    }

    private void setEaAccountCosts(Context context, LocalDate billMonth,
            Double initialBalance, Double usageCost, Double marketplaceCost,
            Double separatelyBilledCost) {
        EaAccountCost eaAccountCost = new EaAccountCost();
        if (usageCost != null) {
            // Include only sensible months' account cost.
            // Total EA cost for a month =
            // SERVICE_COMMITMENT_TOTAL_USAGE - SERVICE_COMMITMENT_BEGINNING_BALANCE
            Double totalUsageCost = usageCost - initialBalance;
            logFine(() -> String
                    .format("Total cost for the month of: %s is %f - %f = %f", billMonth,
                            usageCost, initialBalance, totalUsageCost));
            eaAccountCost.monthlyEaAccountUsageCost = totalUsageCost;
        }
        if (marketplaceCost != null) {
            eaAccountCost.monthlyEaAccountMarketplaceCost = marketplaceCost;
        }
        if (separatelyBilledCost != null) {
            eaAccountCost.monthlyEaAccountSeparatelyBilledCost = separatelyBilledCost;
        }
        context.eaAccountCost.put(billMonth, eaAccountCost);
    }

    private void handleJoinOpErrors(Context context, Stages next, Map<Long, Throwable> exceptions) {
        if (exceptions != null && !exceptions.isEmpty()) {
            for (Throwable ex : exceptions.values()) {
                String exceptionString = Utils.toString(ex);
                logWarning(() -> String
                        .format("Error retrieving bill from Azure for the operation: %s.",
                                exceptionString));
                if (exceptionString
                        .contains(AzureCostConstants.ERROR_RESPONSE_MESSAGE_SERVICE_UNAVAILABLE)) {
                    logWarning(() -> String.format("One of the request failed with "
                            + "the exception: %s Retrying the requests.", exceptionString));
                    //TODO gjobin: Check how to retry from here.
                }
                handleError(context, next, ex, false);
            }
        }
    }

    private void cleanUp(Context context) {
        try {
            if (context.csvReader != null) {
                context.csvReader.close();
            }
            if (context.billFile != null) {
                FileUtils.deleteDirectory(context.billFile.getParentFile());
            }
        } catch (Exception ex) {
            logWarning(() -> String
                    .format("Unable to clean-up file: %s %s", context.billFile.getParentFile(), Utils.toString(ex)));
        }
    }

    private void handleError(Context context, Stages next, Throwable throwable,
            boolean shouldGoToNextStage) {
        try {
            if (AzureCostStatsServiceHelper.isCurrentMonth(context.billMonthToDownload)
                    || next == null) {
                cleanUp(context);
                if (throwable != null) {
                    logSevere(() -> String.format("Failed at stage %s with the exception: %s",
                            context.stage, Utils.toString(throwable)));
                    getFailureConsumer(context).accept(throwable);
                }
            } else {
                logInfo(() -> String.format("Failed at stage %s with the exception: %s"
                                + "Proceeding to next stage since this is not critical.",
                        context.stage, Utils.toString(throwable)));
                if (shouldGoToNextStage) {
                    context.stage = next;
                    handleRequest(context);
                }
            }
        } catch (Exception ex) {
            getFailureConsumer(context).accept(ex);
        }
    }

    private Consumer<Throwable> getFailureConsumer(Context context) {
        return ((t) -> context.taskManager.patchTaskToFailure(t));
    }

    private URI getInventoryServiceUri() {
        return ClusterUtil.getClusterUri(getHost(), ServiceTypeCluster.DISCOVERY_SERVICE);
    }

    private URI getMetricsServiceUri() {
        return ClusterUtil.getClusterUri(getHost(), ServiceTypeCluster.METRIC_SERVICE);
    }
}
