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

package com.vmware.photon.controller.model.adapters.azure.stats;

import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_DIAGNOSTIC_STORAGE_ACCOUNT_LINK;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.awaitTermination;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.ResultSegment;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.table.CloudTable;
import com.microsoft.azure.storage.table.CloudTableClient;
import com.microsoft.azure.storage.table.DynamicTableEntity;
import com.microsoft.azure.storage.table.EntityProperty;
import com.microsoft.azure.storage.table.TableQuery;
import com.microsoft.azure.storage.table.TableQuery.Operators;
import com.microsoft.azure.storage.table.TableQuery.QueryComparisons;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.vmware.photon.controller.model.adapterapi.ComputeStatsRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse.ComputeStats;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.model.stats.AzureMetricRequest;
import com.vmware.photon.controller.model.adapters.azure.model.stats.AzureMetricResponse;
import com.vmware.photon.controller.model.adapters.azure.model.stats.Datapoint;
import com.vmware.photon.controller.model.adapters.azure.model.stats.Location;
import com.vmware.photon.controller.model.adapters.azure.model.stats.MetricAvailability;
import com.vmware.photon.controller.model.adapters.azure.model.stats.MetricDefinitions;
import com.vmware.photon.controller.model.adapters.azure.model.stats.TableInfo;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureStatsNormalizer;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService.SingleResourceStatsCollectionTaskState;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService.SingleResourceTaskCollectionStage;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.ServiceHost.ServiceNotFoundException;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * This service queries the Azure storage table account to obtain the metrics of a VM.
 * It is called per compute VM to obtain the metrics.
 *
 */
public class AzureComputeStatsGatherer extends StatelessService {
    public static final String SELF_LINK = AzureUriPaths.AZURE_COMPUTE_STATS_GATHERER;
    private static final String PARTITION_KEY = "PartitionKey";
    private static final String COUNTER_NAME_KEY = "CounterName";
    private static final String TIMESTAMP = "Timestamp";
    public static final String[] METRIC_NAMES = { AzureConstants.NETWORK_BYTES_IN,
            AzureConstants.NETWORK_BYTES_OUT, AzureConstants.DISK_WRITES_PER_SECOND,
            AzureConstants.DISK_READS_PER_SECOND, AzureConstants.CPU_UTILIZATION,
            AzureConstants.MEMORY_AVAILABLE, AzureConstants.MEMORY_USED,
            AzureConstants.PERCENT_MEMORY_AVAILABLE, AzureConstants.PERCENT_MEMORY_USED,
            AzureConstants.DISK_READ_BYTES_PER_SECOND, AzureConstants.DISK_WRITE_BYTES_PER_SECOND };

    private ExecutorService executorService;

    @Override
    public void handleStart(Operation startPost) {
        this.executorService = getHost().allocateExecutor(this);
        super.handleStart(startPost);
    }

    @Override
    public void handleStop(Operation delete) {
        this.executorService.shutdown();
        awaitTermination(this, this.executorService);
        super.handleStop(delete);
    }

    private class AzureStatsDataHolder {
        public ComputeStateWithDescription computeDesc;
        public ComputeStateWithDescription parentDesc;
        public DiskState bootDisk;
        public StorageDescription storageDescripton;
        public AuthCredentialsService.AuthCredentialsServiceState storageAccountAuth;
        public AuthCredentialsService.AuthCredentialsServiceState parentAuth;
        public ComputeStatsRequest statsRequest;
        public ApplicationTokenCredentials credentials;
        public ComputeStats statsResponse;
        public AtomicInteger numResponses = new AtomicInteger(0);
        public String tableName;
        public String partitionValue;

        public AzureStatsDataHolder() {
            this.statsResponse = new ComputeStats();
            // create a thread safe map to hold stats values for resource
            this.statsResponse.statValues = new ConcurrentSkipListMap<>();
        }
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

        AzureStatsDataHolder statsData = new AzureStatsDataHolder();
        statsData.statsRequest = statsRequest;
        // TODO: https://jira-hzn.eng.vmware.com/browse/VSYM-1336
        getVMDescription(statsData);
    }

    private void getVMDescription(AzureStatsDataHolder statsData) {
        Consumer<Operation> onSuccess = (op) -> {
            statsData.computeDesc = op.getBody(ComputeStateWithDescription.class);
            getParentVMDescription(statsData);
        };
        URI computeUri = UriUtils.extendUriWithQuery(statsData.statsRequest.resourceReference,
                UriUtils.URI_PARAM_ODATA_EXPAND, Boolean.TRUE.toString());
        AdapterUtils.getServiceState(this, computeUri, onSuccess, getFailureConsumer(statsData));
    }

    private void getParentVMDescription(AzureStatsDataHolder statsData) {
        Consumer<Operation> onSuccess = (op) -> {
            statsData.parentDesc = op.getBody(ComputeStateWithDescription.class);
            getParentAuth(statsData);
        };
        URI computeUri = UriUtils.extendUriWithQuery(
                UriUtils.buildUri(getHost(), statsData.computeDesc.parentLink),
                UriUtils.URI_PARAM_ODATA_EXPAND, Boolean.TRUE.toString());
        AdapterUtils.getServiceState(this, computeUri, onSuccess, getFailureConsumer(statsData));
    }

    private void getParentAuth(AzureStatsDataHolder statsData) {
        Consumer<Operation> onSuccess = (op) -> {
            statsData.parentAuth = op
                    .getBody(AuthCredentialsService.AuthCredentialsServiceState.class);
            getBootDisk(statsData);
        };
        String authLink = statsData.parentDesc.description.authCredentialsLink;
        AdapterUtils.getServiceState(this, authLink,
                onSuccess, getFailureConsumer(statsData));
    }

    private void getBootDisk(AzureStatsDataHolder statsData) {
        Consumer<Operation> onSuccess = (op) -> {
            statsData.bootDisk = op.getBody(DiskState.class);
            getStorageAccount(statsData);
        };
        /*
         * VSYM-655 - https://jira-hzn.eng.vmware.com/browse/VSYM-655
         * Until Azure design is finalized, the first and only disk will always be the boot disk.
         */
        if (statsData.computeDesc.diskLinks == null ||
                statsData.computeDesc.diskLinks.isEmpty()) {
            AdapterUtils.sendFailurePatchToProvisioningTask(this,
                    statsData.statsRequest.taskReference,
                    new IllegalStateException("No disks found"));
        }
        AdapterUtils.getServiceState(this, statsData.computeDesc.diskLinks.get(0), onSuccess,
                getFailureConsumer(statsData));
    }

    private void getStorageAccount(AzureStatsDataHolder statsData) {
        Consumer<Operation> onSuccess = (op) -> {
            statsData.storageDescripton = op.getBody(StorageDescription.class);
            getStorageAccountAuth(statsData);
        };
        AdapterUtils.getServiceState(this, statsData.computeDesc.customProperties
                        .get(AZURE_DIAGNOSTIC_STORAGE_ACCOUNT_LINK), onSuccess,
                ((throwable) -> {
                    if (throwable instanceof ServiceNotFoundException) {
                        logInfo("Skipping stats collection because storage account not found for [%s]",
                                statsData.computeDesc.name);
                        patchEmptyResponse(statsData);
                        return;
                    }
                    AdapterUtils.sendFailurePatchToProvisioningTask(this,
                            statsData.statsRequest.taskReference, throwable);
                }));
    }

    private void getStorageAccountAuth(AzureStatsDataHolder statsData) {
        Consumer<Operation> onSuccess = (op) -> {
            statsData.storageAccountAuth = op.getBody(AuthCredentialsServiceState.class);
            getStats(statsData);
        };
        AdapterUtils.getServiceState(this, statsData.storageDescripton.authCredentialsLink, onSuccess,
                getFailureConsumer(statsData));
    }

    private Consumer<Throwable> getFailureConsumer(AzureStatsDataHolder statsData) {
        return ((throwable) -> {
            if (throwable instanceof ServiceNotFoundException) {
                logWarning("Skipping stats collection because [%s]", throwable.getMessage());
                patchEmptyResponse(statsData);
                return;
            }
            AdapterUtils.sendFailurePatchToProvisioningTask(this,
                    statsData.statsRequest.taskReference, throwable);
        });
    }

    private void getAzureApplicationTokenCredential(AzureStatsDataHolder statsData) {
        if (statsData.credentials == null) {
            try {
                statsData.credentials = AzureUtils.getAzureConfig(statsData.parentAuth);
            } catch (Exception e) {
                logSevere(e);
            }
        }
    }

    private void getStats(AzureStatsDataHolder statsData) {
        getAzureApplicationTokenCredential(statsData);
        try {
            getMetricDefinitions(statsData);
        } catch (Exception e) {
            AdapterUtils.sendFailurePatchToProvisioningTask(this,
                    statsData.statsRequest.taskReference, e);
        }
    }

    /**
     * Get the metric definitions from Azure using the Endpoint "/metricDefinitions"
     * The request and response of the API is as described in
     * {@link https://msdn.microsoft.com/en-us/library/azure/dn931939.aspx} Insights REST.
     * @param statsData
     * @throws URISyntaxException
     * @throws IOException
     */
    private void getMetricDefinitions(AzureStatsDataHolder statsData)
            throws URISyntaxException, IOException {
        String azureInstanceId = statsData.computeDesc.id;
        URI uri = UriUtils.buildUri(new URI(AzureConstants.BASE_URI_FOR_REST), azureInstanceId,
                AzureConstants.METRIC_DEFINITIONS_ENDPOINT);
        // Adding a filter to avoid huge data flow on the network
        /*
         * VSYM-656: https://jira-hzn.eng.vmware.com/browse/VSYM-656
         * Remove the filter when Unit of a metric is required.
         */
        uri = UriUtils.extendUriWithQuery(uri,
                AzureConstants.QUERY_PARAM_API_VERSION,
                AzureConstants.DIAGNOSTIC_SETTING_API_VERSION,
                AzureConstants.QUERY_PARAM_FILTER, AzureConstants.METRIC_DEFINITIONS_MEMORY_FILTER);
        Operation operation = Operation.createGet(uri);
        operation.addRequestHeader(Operation.ACCEPT_HEADER, Operation.MEDIA_TYPE_APPLICATION_JSON);
        operation.addRequestHeader(Operation.AUTHORIZATION_HEADER,
                AzureConstants.AUTH_HEADER_BEARER_PREFIX + statsData.credentials.getToken());
        operation.setCompletion((op, ex) -> {
            if (ex != null) {
                AdapterUtils.sendFailurePatchToProvisioningTask(this,
                        statsData.statsRequest.taskReference, ex);
            }
            MetricDefinitions metricDefinitions = op.getBody(MetricDefinitions.class);
            DateTimeFormatter dateTimeFormatter = DateTimeFormat
                    .forPattern(AzureConstants.METRIC_TIME_FORMAT);
            if (metricDefinitions.getValues() != null &&
                    !metricDefinitions.getValues().isEmpty()) {
                for (MetricAvailability metricAvailability : metricDefinitions.getValues().get(0)
                        .getMetricAvailabilities()) {
                    if (metricAvailability.getTimeGrain()
                            .equals(AzureConstants.METRIC_TIME_GRAIN_1_MINUTE)) {
                        Location location = metricAvailability.getLocation();
                        Date mostRecentTableDate = null;
                        for (TableInfo tableInfo : location.getTableInfo()) {
                            Date startDate = dateTimeFormatter.parseDateTime(tableInfo.getStartTime())
                                    .toDate();
                            if (mostRecentTableDate == null || startDate.after(mostRecentTableDate)) {
                                mostRecentTableDate = startDate;
                                statsData.tableName = tableInfo.getTableName();
                            }
                        }
                        statsData.partitionValue = location.getPartitionKey();
                    }
                }
            }
            if (statsData.tableName != null && statsData.tableName.length() > 0) {
                try {
                    getMetrics(statsData);
                } catch (Exception e) {
                    AdapterUtils.sendFailurePatchToProvisioningTask(this,
                            statsData.statsRequest.taskReference, e);
                }
            } else {
                patchEmptyResponse(statsData);
            }
        });
        sendRequest(operation);
    }

    private void patchEmptyResponse(AzureStatsDataHolder statsData) {
        // Patch back to the Parent with empty response
        SingleResourceStatsCollectionTaskState respBody = new SingleResourceStatsCollectionTaskState();
        statsData.statsResponse.computeLink = statsData.computeDesc.documentSelfLink;
        respBody.taskStage = SingleResourceTaskCollectionStage.valueOf(statsData.statsRequest.nextStage);
        respBody.statsAdapterReference = UriUtils.buildUri(getHost(), SELF_LINK);
        respBody.statsList = new ArrayList<>();
        this.sendRequest(Operation.createPatch(statsData.statsRequest.taskReference)
                .setBody(respBody));
    }

    private void getMetrics(AzureStatsDataHolder statsData)
            throws InvalidKeyException, URISyntaxException, StorageException {
        String storageAccountName = statsData.storageDescripton.name;
        String storageKey = statsData.storageAccountAuth.customProperties
                .get(AzureConstants.AZURE_STORAGE_ACCOUNT_KEY1);
        String storageConnectionString = String.format(AzureConstants.STORAGE_CONNECTION_STRING,
                storageAccountName, storageKey);
        for (String metricName : METRIC_NAMES) {
            AzureMetricRequest request = new AzureMetricRequest();
            request.setStorageConnectionString(storageConnectionString);
            request.setTableName(statsData.tableName);
            request.setPartitionValue(statsData.partitionValue);
            long endTimeMicros = Utils.getNowMicrosUtc();
            Date timeStamp = new Date(TimeUnit.MICROSECONDS.toMillis(endTimeMicros)
                    - TimeUnit.MINUTES.toMillis(AzureConstants.METRIC_COLLECTION_PERIOD));
            request.setTimestamp(timeStamp);
            request.setMetricName(metricName);
            AzureMetricsHandler handler = new AzureMetricsHandler(this, statsData);
            getMetricStatisticsAsync(request, handler);
        }
    }

    private class AzureMetricsHandler
            implements AsyncHandler<AzureMetricRequest, AzureMetricResponse> {
        private AzureStatsDataHolder statsData;
        private StatelessService service;
        private OperationContext opContext;

        public AzureMetricsHandler(StatelessService service, AzureStatsDataHolder statsData) {
            this.statsData = statsData;
            this.service = service;
            this.opContext = OperationContext.getOperationContext();
        }

        @Override
        public void onError(Exception exception) {
            OperationContext.restoreOperationContext(this.opContext);
            AdapterUtils.sendFailurePatchToProvisioningTask(this.service,
                    this.statsData.statsRequest.taskReference, exception);
        }

        @Override
        public void onSuccess(AzureMetricRequest request, AzureMetricResponse result) {
            OperationContext.restoreOperationContext(this.opContext);
            List<ServiceStat> statDatapoints = new ArrayList<>();
            List<Datapoint> dpList = result.getDatapoints();
            String normalizedMetricName = AzureStatsNormalizer.getNormalizedStatKeyValue(result.getLabel());
            if (dpList != null && dpList.size() != 0) {
                for (Datapoint dp : dpList) {
                    // TODO: https://jira-hzn.eng.vmware.com/browse/VSYM-769
                    ServiceStat stat = new ServiceStat();
                    stat.latestValue = dp.getAverage();
                    stat.sourceTimeMicrosUtc = TimeUnit.MILLISECONDS.toMicros(dp.getTimestamp().getTime());
                    stat.unit = PhotonModelConstants.getUnitForMetric(normalizedMetricName);
                    statDatapoints.add(stat);
                }

                this.statsData.statsResponse.statValues.put(normalizedMetricName, statDatapoints);
            }

            if (this.statsData.numResponses.incrementAndGet() == METRIC_NAMES.length) {
                SingleResourceStatsCollectionTaskState respBody = new SingleResourceStatsCollectionTaskState();
                this.statsData.statsResponse.computeLink = this.statsData.computeDesc.documentSelfLink;
                respBody.taskStage = SingleResourceTaskCollectionStage.valueOf(this.statsData.statsRequest.nextStage);
                respBody.statsList = new ArrayList<>();
                respBody.statsList.add(this.statsData.statsResponse);
                respBody.statsAdapterReference = UriUtils.buildUri(getHost(), SELF_LINK);
                this.service.sendRequest(
                        Operation.createPatch(this.statsData.statsRequest.taskReference)
                        .setBody(respBody));
            }
        }
    }

    /**
     * Uses the executorService to kick of a new Callable,
     * which in turn patches back to the AsyncHandler that is passed.
     *
     * @param request
     * The request object
     * @param asyncHandler
     * The Asynchronous handler that will be called.
     * @return
     */
    public void getMetricStatisticsAsync(
            final AzureMetricRequest request,
            final AsyncHandler<AzureMetricRequest, AzureMetricResponse> asyncHandler) {
        this.executorService.submit(new Runnable() {
            @Override
            public void run() {
                AzureMetricResponse response = new AzureMetricResponse();
                try {
                    // Create the table client required to make calls to the table
                    CloudStorageAccount cloudStorageAccount = CloudStorageAccount
                            .parse(request.getStorageConnectionString());
                    CloudTableClient tableClient = cloudStorageAccount.createCloudTableClient();

                    // Get the table reference using the table name
                    CloudTable table = tableClient.getTableReference(request.getTableName());

                    // Create filters to limit the data
                    String partitionFilter = TableQuery.generateFilterCondition(
                            PARTITION_KEY, QueryComparisons.EQUAL, request.getPartitionValue());

                    String timestampFilter = TableQuery.generateFilterCondition(
                            TIMESTAMP, QueryComparisons.GREATER_THAN_OR_EQUAL,
                            request.getTimestamp());

                    String partitionAndTimestampFilter = TableQuery.combineFilters(
                            partitionFilter, Operators.AND, timestampFilter);

                    String counterFilter = TableQuery.generateFilterCondition(COUNTER_NAME_KEY,
                            QueryComparisons.EQUAL, request.getMetricName());

                    // Combine all the filters
                    String combinedFilter = TableQuery.combineFilters(
                            partitionAndTimestampFilter, Operators.AND, counterFilter);

                    // Create the query
                    TableQuery<DynamicTableEntity> query = TableQuery
                            .from(DynamicTableEntity.class).where(combinedFilter);

                    response.setLabel(request.getMetricName());
                    List<Datapoint> datapoints = new ArrayList<>();
                    ResultSegment<DynamicTableEntity> entities = table
                            .executeSegmented(query, null);
                    for (DynamicTableEntity entity : entities.getResults()) {
                        HashMap<String, EntityProperty> properties = entity
                                .getProperties();
                        Datapoint dp = new Datapoint();
                        for (String key : properties.keySet()) {
                            switch (key) {
                            case AzureConstants.METRIC_KEY_LAST:
                                dp.setLast(properties.get(key).getValueAsDoubleObject());
                                break;
                            case AzureConstants.METRIC_KEY_MAXIMUM:
                                dp.setMaximum(properties.get(key).getValueAsDoubleObject());
                                break;
                            case AzureConstants.METRIC_KEY_MINIMUM:
                                dp.setMinimum(properties.get(key).getValueAsDoubleObject());
                                break;
                            case AzureConstants.METRIC_KEY_COUNTER_NAME:
                                dp.setCounterName(properties.get(key).getValueAsString());
                                break;
                            case AzureConstants.METRIC_KEY_TIMESTAMP:
                                dp.setTimestamp(properties.get(key).getValueAsDate());
                                break;
                            case AzureConstants.METRIC_KEY_TOTAL:
                                dp.setTotal(properties.get(key).getValueAsDoubleObject());
                                break;
                            case AzureConstants.METRIC_KEY_AVERAGE:
                                dp.setAverage(properties.get(key).getValueAsDoubleObject());
                                break;
                            case AzureConstants.METRIC_KEY_COUNT:
                                dp.setCount(properties.get(key).getValueAsIntegerObject());
                                break;
                            default:
                                logInfo("Invalid key [%s]", key);
                                break;
                            }
                        }
                        datapoints.add(dp);
                        break;
                    }
                    response.setDatapoints(datapoints);
                } catch (Exception ex) {
                    if (asyncHandler != null) {
                        asyncHandler.onError(ex);
                    }
                }
                if (asyncHandler != null) {
                    asyncHandler.onSuccess(request, response);
                }
            }
        });
    }
}
