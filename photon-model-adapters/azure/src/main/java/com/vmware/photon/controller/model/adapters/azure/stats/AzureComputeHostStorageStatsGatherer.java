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
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_CORE_MANAGEMENT_URI;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_TENANT_ID;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.LIST_STORAGE_ACCOUNTS;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.QUERY_PARAM_API_VERSION;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.STORAGE_ACCOUNT_REST_API_VERSION;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.STORAGE_CONNECTION_STRING;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.buildRestClient;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.cleanUpHttpClient;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.getAzureConfig;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.getResourceGroupName;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.storage.implementation.StorageAccountListKeysResultInner;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.ResultContinuation;
import com.microsoft.azure.storage.ResultSegment;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.BlobListingDetails;
import com.microsoft.azure.storage.blob.CloudBlob;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudPageBlob;
import com.microsoft.azure.storage.blob.ContainerListingDetails;
import com.microsoft.azure.storage.blob.ListBlobItem;
import com.microsoft.azure.storage.blob.PageRange;

import com.microsoft.rest.RestClient;

import com.vmware.photon.controller.model.adapterapi.ComputeStatsRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse;
import com.vmware.photon.controller.model.adapters.azure.AzureAsyncCallback;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.model.storage.StorageAccount;
import com.vmware.photon.controller.model.adapters.azure.model.storage.StorageAccountResultList;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
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
    public static final Integer QUERY_RESULT_LIMIT = 50;

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
        AdapterUtils.awaitTermination(this.executorService);
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
        final List<CloudBlob> snapshots = Collections.synchronizedList(new ArrayList<>());

        // Azure clients
        Azure azureClient;
        RestClient restClient;

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
            dataHolder.stage = StorageMetricsStages.GET_STORAGE_ACCOUNTS;
            handleStorageMetricDiscovery(dataHolder);
            break;
        case GET_STORAGE_ACCOUNTS:
            getStorageAccounts(dataHolder, StorageMetricsStages.CALCULATE_METRICS);
            break;
        case CALCULATE_METRICS:
            getBlobUsedBytesAsync(dataHolder, StorageMetricsStages.FINISHED);
            break;
        case FINISHED:
            dataHolder.azureStorageStatsOperation.setBody(dataHolder.statsResponse);
            dataHolder.azureStorageStatsOperation.complete();
            cleanUpHttpClient(dataHolder.restClient.httpClient());
            logInfo(() -> String.format("Storage utilization stats collection complete for compute"
                    + " host %s", dataHolder.computeHostDesc.id));
            break;
        case ERROR:
            dataHolder.azureStorageStatsOperation.fail(dataHolder.error);
            cleanUpHttpClient(dataHolder.restClient.httpClient());
            logWarning(() -> String.format("Storage utilization stats collection failed for compute"
                    + " host %s", dataHolder.computeHostDesc.id));
            break;
        default:
        }
    }

    private void getComputeHost(AzureStorageStatsDataHolder statsData, StorageMetricsStages next) {
        Consumer<Operation> onSuccess = (op) -> {
            statsData.computeHostDesc = op.getBody(ComputeService.ComputeStateWithDescription.class);
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
                    AUTH_HEADER_BEARER_PREFIX + statsData.credentials.getToken(AZURE_CORE_MANAGEMENT_URI));
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

            logFine(() -> String.format("Retrieved %d storage accounts from Azure",
                    storageAccounts.size()));

            for (StorageAccount storageAccount : storageAccounts) {
                statsData.storageAccounts.put(storageAccount.id, storageAccount);
            }
            logFine(() -> String.format("Processing %d storage accounts",
                    statsData.storageAccounts.size()));

            statsData.stage = next;
            handleStorageMetricDiscovery(statsData);
        });
        sendRequest(operation);
    }

    private void getBlobUsedBytesAsync(AzureStorageStatsDataHolder statsData,
            StorageMetricsStages next) {
        OperationContext origContext = OperationContext.getOperationContext();
        this.executorService.submit(() -> {
            OperationContext.restoreOperationContext(origContext);
            String metricName = PhotonModelConstants.STORAGE_USED_BYTES;
            List<ServiceStats.ServiceStat> statDatapoints = new ArrayList<>();

            Azure azureClient = getAzureClient(statsData);
            AtomicInteger accountsCount = new AtomicInteger(statsData.storageAccounts.size());
            final List<Throwable> exs = new ArrayList<>();
            for (Map.Entry<String, StorageAccount> account : statsData.storageAccounts
                    .entrySet()) {
                String resourceGroupName = getResourceGroupName(account.getValue().id);
                azureClient.storageAccounts().inner().listKeysAsync(resourceGroupName,
                        account.getValue().name, new AzureAsyncCallback<StorageAccountListKeysResultInner>() {
                            @Override
                            public void onError(Throwable e) {
                                handleError(statsData, e);
                            }

                            @Override
                            public void onSuccess(StorageAccountListKeysResultInner result) {
                                logFine(() -> String
                                        .format("Retrieved the storage account keys for"
                                                        + " storage account [%s].",
                                                account.getValue().name));
                                String storageConnectionString = String
                                        .format(STORAGE_CONNECTION_STRING,
                                                account.getValue().name,
                                                result.keys().get(0).value());

                                try {
                                    CloudStorageAccount storageAccount = CloudStorageAccount
                                            .parse(storageConnectionString);
                                    CloudBlobClient blobClient = storageAccount
                                            .createCloudBlobClient();
                                    ResultContinuation nextContainerResults = null;
                                    do {
                                        ResultSegment<CloudBlobContainer> contSegment =
                                                blobClient.listContainersSegmented(null,
                                                        ContainerListingDetails.NONE,
                                                        QUERY_RESULT_LIMIT,
                                                        nextContainerResults, null, null);

                                        nextContainerResults = contSegment
                                                .getContinuationToken();
                                        for (CloudBlobContainer container : contSegment
                                                .getResults()) {
                                            ResultContinuation nextBlobResults = null;
                                            do {
                                                ResultSegment<ListBlobItem> blobsSegment = container
                                                        .listBlobsSegmented(
                                                                null, false, EnumSet.noneOf(
                                                                        BlobListingDetails.class),
                                                                QUERY_RESULT_LIMIT,
                                                                nextBlobResults, null, null);
                                                nextBlobResults = blobsSegment
                                                        .getContinuationToken();
                                                for (ListBlobItem blobItem : blobsSegment
                                                        .getResults()) {
                                                    if (blobItem instanceof CloudPageBlob) {
                                                        CloudPageBlob pageBlob = (CloudPageBlob) blobItem;
                                                        // Due to concurrency issues: We can only get the used bytes of a blob
                                                        // only by creating a snapshot and getting the page ranges of the snapshot
                                                        // TODO https://jira-hzn.eng.vmware.com/browse/VSYM-3445
                                                        try {
                                                            CloudBlob blobSnapshot = pageBlob
                                                                    .createSnapshot();
                                                            statsData.snapshots
                                                                    .add(blobSnapshot);
                                                            CloudPageBlob pageBlobSnapshot = (CloudPageBlob) blobSnapshot;
                                                            ArrayList<PageRange> pages = pageBlobSnapshot
                                                                    .downloadPageRanges();

                                                            // TODO store disk utilized bytes more granularly
                                                            // https://jira-hzn.eng.vmware.com/browse/VSYM-3355
                                                            for (PageRange pageRange : pages) {
                                                                statsData.utilizedBytes +=
                                                                        pageRange.getEndOffset()
                                                                                - pageRange
                                                                                .getStartOffset();
                                                            }
                                                        } catch (StorageException e) {
                                                            logWarning(() -> String
                                                                    .format("Error getting blob size: [%s]",
                                                                            e.getMessage()));
                                                        }
                                                    }
                                                }
                                            } while (nextBlobResults != null);
                                        }
                                    } while (nextContainerResults != null);
                                } catch (Exception e) {
                                    logWarning(() -> String
                                            .format("Exception while getting blob used bytes: %s",
                                                    Utils.toString(e)));
                                    exs.add(e);
                                } finally {
                                    // Delete snapshot - otherwise snapshot blobs will accumulate
                                    // in the Azure account
                                    if (statsData.snapshots.size() > 0) {
                                        synchronized (statsData.snapshots) {
                                            Iterator<CloudBlob> snapshotIterator = statsData.snapshots
                                                    .iterator();
                                            while (snapshotIterator.hasNext()) {
                                                try {
                                                    CloudBlob snapshot = snapshotIterator
                                                            .next();
                                                    snapshot.deleteIfExists();
                                                    snapshotIterator.remove();
                                                } catch (StorageException e) {
                                                    // Best effort to delete all the snapshots
                                                    logWarning(() -> String
                                                            .format("Exception while deleting snapshot: %s",
                                                                    Utils.toString(e)));
                                                }
                                            }
                                        }
                                    }
                                }

                                // if all storage accounts were processed, create ServiceStat and finish
                                if (accountsCount.decrementAndGet() == 0) {
                                    if (!exs.isEmpty()) {
                                        handleError(statsData, exs.iterator().next());
                                        return;
                                    }

                                    if (statsData.utilizedBytes != 0) {
                                        ServiceStats.ServiceStat stat = new ServiceStats.ServiceStat();
                                        stat.latestValue = statsData.utilizedBytes;
                                        stat.sourceTimeMicrosUtc = TimeUnit.MILLISECONDS
                                                .toMicros(Utils.getNowMicrosUtc());
                                        stat.unit = PhotonModelConstants
                                                .getUnitForMetric(metricName);
                                        statDatapoints.add(stat);
                                    }
                                    statsData.statsResponse.statValues
                                            .put(metricName, statDatapoints);
                                    if (statsData.statsResponse.statValues.size() == 1) {
                                        statsData.statsResponse.computeLink = statsData.computeHostDesc.documentSelfLink;
                                    }
                                    statsData.stage = next;
                                    handleStorageMetricDiscovery(statsData);
                                }
                            }
                        });
            }
        });
    }

    private Azure getAzureClient(AzureStorageStatsDataHolder dataholder) {
        if (dataholder.azureClient == null) {
            if (dataholder.restClient == null) {
                dataholder.restClient = buildRestClient(dataholder.credentials, this.executorService);
            }
            dataholder.azureClient = Azure.authenticate(dataholder.restClient,
                    dataholder.parentAuth.customProperties.get(AZURE_TENANT_ID))
                    .withSubscription(dataholder.parentAuth.userLink);
        }
        return dataholder.azureClient;
    }

    private void handleError(AzureStorageStatsDataHolder dataHolder, Throwable e) {
        logSevere(() -> String.format("Failed at stage %s with exception: %s", dataHolder.stage,
                Utils.toString(e)));
        dataHolder.error = e;
        dataHolder.stage = StorageMetricsStages.ERROR;
        handleStorageMetricDiscovery(dataHolder);
    }

    private Consumer<Throwable> getFailureConsumer(AzureStorageStatsDataHolder statsData) {
        return ((throwable) -> {
            if (throwable instanceof ServiceHost.ServiceNotFoundException) {
                logWarning(() -> String.format("Skipping storage stats collection because [%s]",
                        throwable.getMessage()));
                statsData.azureStorageStatsOperation.complete();
                return;
            }
            statsData.azureStorageStatsOperation.fail(throwable);
        });
    }
}