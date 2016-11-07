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

import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AUTH_HEADER_BEARER_PREFIX;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.LIST_STORAGE_ACCOUNTS;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.QUERY_PARAM_API_VERSION;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.STORAGE_ACCOUNT_REST_API_VERSION;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.STORAGE_CONNECTION_STRING;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.awaitTermination;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.cleanUpHttpClient;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.getAzureConfig;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.getResourceGroupName;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.microsoft.azure.credentials.ApplicationTokenCredentials;

import com.microsoft.azure.management.storage.StorageManagementClient;
import com.microsoft.azure.management.storage.StorageManagementClientImpl;
import com.microsoft.azure.management.storage.models.StorageAccountKeys;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.CloudBlob;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudPageBlob;
import com.microsoft.azure.storage.blob.ListBlobItem;
import com.microsoft.azure.storage.blob.PageRange;
import com.microsoft.rest.ServiceResponse;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;

import com.vmware.photon.controller.model.adapterapi.ComputeStatsRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse;
import com.vmware.photon.controller.model.adapters.azure.AzureAsyncCallback;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.model.storage.StorageAccount;
import com.vmware.photon.controller.model.adapters.azure.model.storage.StorageAccountResultList;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;

/**
 * This service collects the used bytes and the total bytes of blobs (disks or not) per compute host.
 */
public class AzureComputeHostStorageStatsGatherer extends StatelessService {
    public static final String SELF_LINK = AzureUriPaths.AZURE_COMPUTE_HOST_STORAGE_STATS_GATHERER;

    private ExecutorService executorService;

    private enum StorageMetricsStages {
        GET_PARENT_AUTH,
        GET_CLIENT,
        GET_COMPUTE_HOST,
        GET_STORAGE_ACCOUNTS,
        CALCULATE_METRICS,
        FINISHED,
        ERROR
    }

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

    private class AzureStorageStatsDataHolder {
        public ComputeService.ComputeStateWithDescription computeHostDesc;
        public AuthCredentialsService.AuthCredentialsServiceState parentAuth;
        public ComputeStatsRequest statsRequest;
        public ApplicationTokenCredentials credentials;
        public ComputeStatsResponse.ComputeStats statsResponse;
        public StorageMetricsStages stage;
        public Operation azureStorageStatsOperation;
        public long utilizedBytes = 0;

        // Storage account specific properties
        Map<String, StorageAccount> storageAccounts = new ConcurrentHashMap<>();
        List<CloudBlob> snapshots = Collections.synchronizedList(new ArrayList<CloudBlob>());

        // Azure clients
        StorageManagementClient storageClient;
        // Azure specific properties
        public OkHttpClient.Builder clientBuilder;
        public OkHttpClient httpClient;

        public Throwable error;

        public AzureStorageStatsDataHolder(Operation op) {
            this.statsResponse = new ComputeStatsResponse.ComputeStats();
            // create a thread safe map to hold stats values for resource
            this.statsResponse.statValues = new ConcurrentSkipListMap<>();
            this.azureStorageStatsOperation = op;
            this.stage = StorageMetricsStages.GET_COMPUTE_HOST;
        }
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }

        ComputeStatsRequest statsRequest = op.getBody(ComputeStatsRequest.class);

        if (statsRequest.isMockRequest) {
            // patch status to parent task
            AdapterUtils.sendPatchToProvisioningTask(this, statsRequest.taskReference);
            return;
        }

        AzureStorageStatsDataHolder statsData = new AzureStorageStatsDataHolder(op);
        statsData.statsRequest = statsRequest;
        handleStorageMetricDiscovery(statsData);
    }

    /**
     * Calculates the storage metrics based on the storage accounts received from the remote
     * endpoint.
     * @param dataHolder The local service context that has all the information needed to
     * states in the local system.
     */
    private void handleStorageMetricDiscovery(AzureStorageStatsDataHolder dataHolder) {
        switch (dataHolder.stage) {
        case GET_COMPUTE_HOST:
            getComputeHost(dataHolder, StorageMetricsStages.GET_PARENT_AUTH);
            break;
        case GET_PARENT_AUTH:
            getParentAuth(dataHolder, StorageMetricsStages.GET_CLIENT);
            break;
        case GET_CLIENT:
            if (dataHolder.credentials == null) {
                try {
                    dataHolder.credentials = getAzureConfig(dataHolder.parentAuth);
                } catch (Throwable e) {
                    logSevere(e);
                    dataHolder.error = e;
                    dataHolder.stage = StorageMetricsStages.ERROR;
                    handleStorageMetricDiscovery(dataHolder);
                    return;
                }
            }
            if (dataHolder.httpClient == null) {
                try {
                    // Creating a shared singleton Http client instance
                    // Reference https://square.github.io/okhttp/3.x/okhttp/okhttp3/OkHttpClient.html
                    // TODO: https://github.com/Azure/azure-sdk-for-java/issues/1000
                    // TODO: https://jira-hzn.eng.vmware.com/browse/VSYM-2775
                    dataHolder.httpClient = new OkHttpClient();
                    dataHolder.clientBuilder = dataHolder.httpClient.newBuilder();
                } catch (Exception e) {
                    handleError(dataHolder, e);
                    return;
                }
            }
            dataHolder.stage = StorageMetricsStages.GET_STORAGE_ACCOUNTS;
            handleStorageMetricDiscovery(dataHolder);
            break;
        case GET_STORAGE_ACCOUNTS:
            getStorageAccounts(dataHolder, StorageMetricsStages.CALCULATE_METRICS);
            break;
        case CALCULATE_METRICS:
            getBlobUsedBytes(dataHolder, StorageMetricsStages.FINISHED);
            break;
        case FINISHED:
            dataHolder.azureStorageStatsOperation.setBody(dataHolder.statsResponse);
            dataHolder.azureStorageStatsOperation.complete();
            cleanUpHttpClient(this, dataHolder.httpClient);
            logInfo("Storage utilization stats collection complete for compute host %s", dataHolder.computeHostDesc.id);
            break;
        case ERROR:
            dataHolder.azureStorageStatsOperation.fail(dataHolder.error);
            cleanUpHttpClient(this, dataHolder.httpClient);
            logWarning("Storage utilization stats collection failed for compute host %s", dataHolder.computeHostDesc.id);
            break;
        default:
        }
    }

    private void getComputeHost(AzureStorageStatsDataHolder statsData, StorageMetricsStages next) {
        Consumer<Operation> onSuccess = (op) -> {
            ComputeService.ComputeStateWithDescription csd = op.getBody(ComputeService.ComputeStateWithDescription.class);
            statsData.computeHostDesc = csd;
            statsData.stage = next;
            handleStorageMetricDiscovery(statsData);
        };

        URI computeUri = UriUtils.extendUriWithQuery(
                statsData.statsRequest.resourceReference,
                UriUtils.URI_PARAM_ODATA_EXPAND,
                Boolean.TRUE.toString());
        AdapterUtils.getServiceState(this, computeUri, onSuccess, getFailureConsumer(statsData));
    }

    /**
     * Method to arrive at the credentials needed to call the Azure API for enumerating the
     * instances.
     */
    private void getParentAuth(AzureStorageStatsDataHolder statsDataHolder,
            StorageMetricsStages next) {
        URI authUri = UriUtils.buildUri(this.getHost(),
                statsDataHolder.computeHostDesc.description.authCredentialsLink);
        Consumer<Operation> onSuccess = (op) -> {
            statsDataHolder.parentAuth = op.getBody(AuthCredentialsService
                    .AuthCredentialsServiceState.class);
            statsDataHolder.stage = next;
            handleStorageMetricDiscovery(statsDataHolder);
        };
        AdapterUtils.getServiceState(this, authUri, onSuccess, getFailureConsumer(statsDataHolder));
    }

    private void getStorageAccounts(AzureStorageStatsDataHolder statsData,
            StorageMetricsStages next) {
        String uriStr = AdapterUriUtil.expandUriPathTemplate(LIST_STORAGE_ACCOUNTS,
                statsData.parentAuth.userLink);
        URI uri = UriUtils.extendUriWithQuery(UriUtils.buildUri(uriStr),
                QUERY_PARAM_API_VERSION, STORAGE_ACCOUNT_REST_API_VERSION);

        Operation operation = Operation.createGet(uri);
        operation.addRequestHeader(Operation.ACCEPT_HEADER, Operation.MEDIA_TYPE_APPLICATION_JSON);
        operation.addRequestHeader(Operation.CONTENT_TYPE_HEADER,
                Operation.MEDIA_TYPE_APPLICATION_JSON);
        try {
            operation.addRequestHeader(Operation.AUTHORIZATION_HEADER,
                    AUTH_HEADER_BEARER_PREFIX + statsData.credentials.getToken());
        } catch (Exception ex) {
            this.handleError(statsData, ex);
            return;
        }

        operation.setCompletion((op, er) -> {
            if (er != null) {
                handleError(statsData, er);
                return;
            }

            StorageAccountResultList results = op.getBody(StorageAccountResultList.class);
            List<StorageAccount> storageAccounts = results.value;

            // If there are no storage accounts in Azure, the are no bytes being used/billed
            if (storageAccounts == null || storageAccounts.size() == 0) {
                statsData.stage = StorageMetricsStages.FINISHED;
                handleStorageMetricDiscovery(statsData);
                return;
            }

            logFine("Retrieved %d storage accounts from Azure", storageAccounts.size());

            for (StorageAccount storageAccount : storageAccounts) {
                statsData.storageAccounts.put(storageAccount.id, storageAccount);
            }
            logFine("Processing %d storage accounts", statsData.storageAccounts.size());

            statsData.stage = next;
            handleStorageMetricDiscovery(statsData);
        });
        sendRequest(operation);
    }

    private void getBlobUsedBytes(AzureStorageStatsDataHolder statsData,
            StorageMetricsStages next) {
        String metricName = PhotonModelConstants.STORAGE_USED_BYTES;
        List<ServiceStats.ServiceStat> statDatapoints = new ArrayList<>();
        StorageManagementClient storageClient = getStorageManagementClient(statsData);
        AtomicInteger accountsCount = new AtomicInteger(statsData.storageAccounts.size());
        for (Map.Entry<String, StorageAccount> account : statsData.storageAccounts.entrySet()) {
            String resourceGroupName = getResourceGroupName(account.getValue().id);
            storageClient.getStorageAccountsOperations().listKeysAsync(resourceGroupName,
                    account.getValue().name, new AzureAsyncCallback<StorageAccountKeys>() {
                        @Override
                        public void onError(Throwable e) {
                            handleError(statsData, e);
                        }

                        public void onSuccess(ServiceResponse<StorageAccountKeys> result) {
                            logInfo("Retrieved the storage account keys for storage account [%s].",
                                    account.getValue().name);
                            accountsCount.decrementAndGet();
                            StorageAccountKeys keys = result.getBody();
                            String storageConnectionString = String
                                    .format(STORAGE_CONNECTION_STRING, account.getValue().name,
                                            keys.getKey1());

                            try {
                                CloudStorageAccount storageAccount = CloudStorageAccount
                                        .parse(storageConnectionString);
                                CloudBlobClient blobClient = storageAccount.createCloudBlobClient();
                                Iterable<CloudBlobContainer> containerList = blobClient.listContainers();

                                for (CloudBlobContainer container : containerList) {
                                    for (ListBlobItem blobItem : container.listBlobs()) {

                                        if (blobItem instanceof CloudPageBlob) {
                                            CloudPageBlob pageBlob = (CloudPageBlob) blobItem;
                                            // Due to concurrency issues: We can only get the used bytes of a blob
                                            // only by creating a snapshot and getting the page ranges of the snapshot
                                            // TODO https://jira-hzn.eng.vmware.com/browse/VSYM-3445
                                            CloudBlob blobSnapshot = pageBlob.createSnapshot();
                                            statsData.snapshots.add(blobSnapshot);
                                            CloudPageBlob pageBlobSnapshot = (CloudPageBlob) blobSnapshot;
                                            ArrayList<PageRange> pages = pageBlobSnapshot.downloadPageRanges();

                                            // TODO store disk utilized bytes more granularly
                                            // https://jira-hzn.eng.vmware.com/browse/VSYM-3355
                                            for (PageRange pageRange : pages) {
                                                statsData.utilizedBytes += pageRange.getEndOffset()
                                                        - pageRange.getStartOffset();
                                            }
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                handleError(statsData, e);
                                return;
                            } finally {
                                // Delete snapshot - otherwise snapshot blobs will accumulate in the
                                // Azure account
                                if (statsData.snapshots.size() > 0) {
                                    synchronized (statsData.snapshots) {
                                        for (CloudBlob snapshot : statsData.snapshots) {
                                            try {
                                                snapshot.deleteIfExists();
                                                statsData.snapshots.remove(snapshot);
                                            } catch (StorageException e) {
                                                handleError(statsData, e);
                                                continue;
                                            }
                                        }
                                    }
                                }
                            }
                            // if all storage accounts were processed, create ServiceStat and finish
                            if (accountsCount.get() == 0) {
                                if (statsData.utilizedBytes != 0) {
                                    ServiceStats.ServiceStat stat = new ServiceStats.ServiceStat();
                                    stat.latestValue = statsData.utilizedBytes;
                                    stat.sourceTimeMicrosUtc = TimeUnit.MILLISECONDS.toMicros(Utils.getNowMicrosUtc());
                                    stat.unit = PhotonModelConstants.getUnitForMetric(metricName);
                                    statDatapoints.add(stat);
                                }
                                statsData.statsResponse.statValues.put(metricName, statDatapoints);
                                if (statsData.statsResponse.statValues.size() == 1) {
                                    statsData.statsResponse.computeLink = statsData.computeHostDesc.documentSelfLink;
                                }
                                statsData.stage = next;
                                handleStorageMetricDiscovery(statsData);
                            }
                        }
                    });
        }
    }

    private StorageManagementClient getStorageManagementClient(AzureStorageStatsDataHolder dataholder) {
        if (dataholder.storageClient == null) {
            dataholder.storageClient = new StorageManagementClientImpl(
                    AzureConstants.BASE_URI, dataholder.credentials, dataholder.clientBuilder,
                    getRetrofitBuilder());
            dataholder.storageClient.setSubscriptionId(dataholder.parentAuth.userLink);
        }
        return dataholder.storageClient;
    }

    private Retrofit.Builder getRetrofitBuilder() {
        Retrofit.Builder builder = new Retrofit.Builder();
        builder.callbackExecutor(this.executorService);
        return builder;
    }

    private void handleError(AzureStorageStatsDataHolder dataHolder, Throwable e) {
        logSevere("Failed at stage %s with exception: %s", dataHolder.stage, Utils.toString(e));
        dataHolder.error = e;
        dataHolder.stage = StorageMetricsStages.ERROR;
        handleStorageMetricDiscovery(dataHolder);
    }

    private Consumer<Throwable> getFailureConsumer(AzureStorageStatsDataHolder statsData) {
        return ((throwable) -> {
            if (throwable instanceof ServiceHost.ServiceNotFoundException) {
                logWarning("Skipping storage stats collection because [%s]", throwable.getMessage());
                return;
            }
            AdapterUtils.sendFailurePatchToProvisioningTask(this,
                    statsData.statsRequest.taskReference, throwable);
        });
    }
}