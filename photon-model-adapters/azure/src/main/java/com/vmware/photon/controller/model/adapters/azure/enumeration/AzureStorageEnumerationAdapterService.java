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

package com.vmware.photon.controller.model.adapters.azure.enumeration;

import static com.vmware.photon.controller.model.ComputeProperties.FIELD_COMPUTE_HOST_LINK;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AUTH_HEADER_BEARER_PREFIX;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNTS;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNT_KEY1;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNT_KEY2;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNT_URI;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_BLOBS;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_CONTAINERS;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_CONTAINER_LEASE_LAST_MODIFIED;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_CONTAINER_LEASE_STATE;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_CONTAINER_LEASE_STATUS;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_DISKS;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_TYPE;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.DEFAULT_DISK_SERVICE_REFERENCE;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.DEFAULT_DISK_TYPE;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.LIST_STORAGE_ACCOUNTS;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.QUERY_PARAM_API_VERSION;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.STORAGE_ACCOUNT_REST_API_VERSION;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.STORAGE_CONNECTION_STRING;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.awaitTermination;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.cleanUpHttpClient;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.getAzureConfig;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.getResourceGroupName;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.CUSTOM_PROP_ENPOINT_LINK;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import com.microsoft.azure.CloudException;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.storage.StorageManagementClient;
import com.microsoft.azure.management.storage.StorageManagementClientImpl;
import com.microsoft.azure.management.storage.models.StorageAccountKeys;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.ResultContinuation;
import com.microsoft.azure.storage.ResultSegment;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.BlobListingDetails;
import com.microsoft.azure.storage.blob.CloudBlob;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.ContainerListingDetails;
import com.microsoft.azure.storage.blob.ListBlobItem;
import com.microsoft.rest.ServiceResponse;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.ResourceGroupStateType;
import com.vmware.photon.controller.model.adapters.azure.model.storage.StorageAccount;
import com.vmware.photon.controller.model.adapters.azure.model.storage.StorageAccountResultList;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.ComputeEnumerateAdapterRequest;
import com.vmware.photon.controller.model.adapters.util.enums.EnumerationStages;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.ResourceGroupService;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.StorageDescriptionService;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;
import com.vmware.photon.controller.model.tasks.QueryUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;

/**
 * Enumeration adapter for data collection of storage artifacts in Azure.
 */
public class AzureStorageEnumerationAdapterService extends StatelessService {
    public static final String SELF_LINK = AzureUriPaths.AZURE_STORAGE_ENUMERATION_ADAPTER;
    private static final String VHD_EXTENSION = ".vhd";
    public static final int B_TO_MB_FACTOR = 1024 * 1024;

    private static final String PROPERTY_NAME_ENUM_QUERY_RESULT_LIMIT =
            UriPaths.PROPERTY_PREFIX + "AzureStorageEnumerationAdapterService.QUERY_RESULT_LIMIT";
    private static final int QUERY_RESULT_LIMIT = 50;

    public AzureStorageEnumerationAdapterService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    private enum StorageEnumStages {
        GET_STORAGE_ACCOUNTS,
        GET_LOCAL_STORAGE_ACCOUNTS,
        UPDATE_STORAGE_DESCRIPTIONS,
        CREATE_STORAGE_DESCRIPTIONS,
        DELETE_STORAGE_DESCRIPTIONS,
        GET_STORAGE_CONTAINERS,
        GET_LOCAL_STORAGE_CONTAINERS,
        UPDATE_RESOURCE_GROUP_STATES,
        CREATE_RESOURCE_GROUP_STATES,
        DELETE_RESOURCE_GROUP_STATES,
        GET_BLOBS,
        GET_LOCAL_STORAGE_DISKS,
        CREATE_DISK_STATES,
        UPDATE_DISK_STATES,
        DELETE_DISK_STATES,
        FINISHED
    }

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

    /**
     * The local service context that is created to identify and create a representative set of
     * storage descriptions that are required to be created in the system based on the enumeration
     * data received from Azure.
     */
    public static class StorageEnumContext {
        ComputeEnumerateResourceRequest enumRequest;
        ComputeStateWithDescription parentCompute;
        long enumerationStartTimeInMicros;
        EnumerationStages stage;
        String enumNextPageLink;
        String deletionNextPageLink;

        public Throwable error;

        public AuthCredentialsService.AuthCredentialsServiceState parentAuth;

        // Storage account specific properties
        Map<String, StorageAccount> allStorageAccounts = new ConcurrentHashMap<>();
        Map<String, StorageAccount> storageAccountsToUpdateCreate = new ConcurrentHashMap<>();
        List<String> storageAccountIds = new ArrayList<>();
        Map<String, StorageDescription> storageDescriptions = new ConcurrentHashMap<>();
        Map<String, String> storageConnectionStrings = new ConcurrentHashMap<>();

        // Storage container specific properties
        Map<String, CloudBlobContainer> storageContainers = new ConcurrentHashMap<>();
        List<String> containerIds = new ArrayList<>();
        Map<String, ResourceGroupState> resourceGroupStates = new ConcurrentHashMap<>();

        // Storage blob specific properties
        Map<String, ListBlobItem> storageBlobs = new ConcurrentHashMap<>();
        List<String> blobIds = new ArrayList<>();
        Map<String, DiskState> diskStates = new ConcurrentHashMap<>();

        public StorageEnumStages subStage;
        // Stored operation to signal completion to the Azure storage enumeration once all
        // the stages are successfully completed.
        public Operation azureStorageAdapterOperation;
        // Azure clients
        StorageManagementClient storageClient;

        // Azure specific properties
        ApplicationTokenCredentials credentials;
        public OkHttpClient.Builder clientBuilder;
        public OkHttpClient httpClient;

        public StorageEnumContext(ComputeEnumerateAdapterRequest request,
                Operation op) {
            this.enumRequest = request.computeEnumerateResourceRequest;
            this.parentAuth = request.parentAuth;
            this.parentCompute = request.parentCompute;

            this.stage = EnumerationStages.CLIENT;
            this.azureStorageAdapterOperation = op;
        }
    }

    private Set<String> ongoingEnumerations = new ConcurrentSkipListSet<>();

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        StorageEnumContext context = new StorageEnumContext(op.getBody
                (ComputeEnumerateAdapterRequest.class), op);
        AdapterUtils.validateEnumRequest(context.enumRequest);
        if (context.enumRequest.isMockRequest) {
            op.complete();
            return;
        }
        handleStorageEnumeration(context);
    }

    /**
     * Creates the storage description states in the local document store based on the storage
     * accounts received from the remote endpoint.
     * @param context The local service context that has all the information needed to create the
     *                additional description states in the local system.
     */
    private void handleStorageEnumeration(StorageEnumContext context) {
        switch (context.stage) {
        case CLIENT:
            if (context.credentials == null) {
                try {
                    context.credentials = getAzureConfig(context.parentAuth);
                } catch (Throwable e) {
                    logSevere(e);
                    context.error = e;
                    context.stage = EnumerationStages.ERROR;
                    handleStorageEnumeration(context);
                    return;
                }
            }
            if (context.httpClient == null) {
                try {
                    // Creating a shared singleton Http client instance
                    // Reference https://square.github.io/okhttp/3.x/okhttp/okhttp3/OkHttpClient.html
                    // TODO: https://github.com/Azure/azure-sdk-for-java/issues/1000
                    // TODO: https://jira-hzn.eng.vmware.com/browse/VSYM-2775
                    context.httpClient = new OkHttpClient();
                    context.clientBuilder = context.httpClient.newBuilder();
                } catch (Exception e) {
                    handleError(context, e);
                    return;
                }
            }
            context.stage = EnumerationStages.ENUMERATE;
            handleStorageEnumeration(context);
            break;
        case ENUMERATE:
            String enumKey = getEnumKey(context);
            switch (context.enumRequest.enumerationAction) {
            case START:
                if (!this.ongoingEnumerations.add(enumKey)) {
                    logInfo("Enumeration service has already been started for %s", enumKey);

                    return;
                }
                logInfo("Launching enumeration service for %s", enumKey);
                context.enumerationStartTimeInMicros = Utils.getNowMicrosUtc();
                context.enumRequest.enumerationAction = EnumerationAction.REFRESH;
                handleStorageEnumeration(context);
                break;
            case REFRESH:
                context.subStage = StorageEnumStages.GET_STORAGE_ACCOUNTS;
                handleSubStage(context);
                break;
            case STOP:
                if (this.ongoingEnumerations.remove(enumKey)) {
                    logInfo("Enumeration service will be stopped for %s", enumKey);
                } else {
                    logInfo("Enumeration service is not running or has already been stopped for %s",
                            enumKey);
                }
                context.stage = EnumerationStages.FINISHED;
                handleStorageEnumeration(context);
                break;
            default:
                logSevere("Unknown enumeration action %s", context.enumRequest.enumerationAction);
                context.stage = EnumerationStages.ERROR;
                handleStorageEnumeration(context);
                break;
            }
            break;
        case FINISHED:
            context.azureStorageAdapterOperation.complete();
            cleanUpHttpClient(this, context.httpClient);
            logInfo("Enumeration finished for %s", getEnumKey(context));
            this.ongoingEnumerations.remove(getEnumKey(context));
            break;
        case ERROR:
            context.azureStorageAdapterOperation.fail(context.error);
            cleanUpHttpClient(this, context.httpClient);
            logWarning("Enumeration error for %s", getEnumKey(context));
            this.ongoingEnumerations.remove(getEnumKey(context));
            break;
        default:
            String msg = String.format("Unknown Azure enumeration stage %s ",
                    context.stage.toString());
            logSevere(msg);
            context.error = new IllegalStateException(msg);
            cleanUpHttpClient(this, context.httpClient);
            this.ongoingEnumerations.remove(getEnumKey(context));
        }
    }

    private void handleSubStage(StorageEnumContext context) {
        if (!this.ongoingEnumerations.contains(getEnumKey(context))) {
            context.stage = EnumerationStages.FINISHED;
            handleStorageEnumeration(context);
            return;
        }

        switch (context.subStage) {
        case GET_STORAGE_ACCOUNTS:
            getStorageAccounts(context, StorageEnumStages.GET_LOCAL_STORAGE_ACCOUNTS);
            break;
        case GET_LOCAL_STORAGE_ACCOUNTS:
            getLocalStorageAccountDescriptions(context, StorageEnumStages.UPDATE_STORAGE_DESCRIPTIONS);
            break;
        case UPDATE_STORAGE_DESCRIPTIONS:
            updateStorageDescriptions(context, StorageEnumStages.CREATE_STORAGE_DESCRIPTIONS);
            break;
        case CREATE_STORAGE_DESCRIPTIONS:
            createStorageDescriptions(context, StorageEnumStages.DELETE_STORAGE_DESCRIPTIONS);
            break;
        case DELETE_STORAGE_DESCRIPTIONS:
            deleteStorageDescription(context, StorageEnumStages.GET_STORAGE_CONTAINERS);
            break;
        case GET_STORAGE_CONTAINERS:
            getStorageContainers(context, StorageEnumStages.GET_LOCAL_STORAGE_CONTAINERS);
            break;
        case GET_LOCAL_STORAGE_CONTAINERS:
            getLocalStorageContainerStates(context,
                    StorageEnumStages.UPDATE_RESOURCE_GROUP_STATES);
            break;
        case UPDATE_RESOURCE_GROUP_STATES:
            updateResourceGroupStates(context, StorageEnumStages.CREATE_RESOURCE_GROUP_STATES);
            break;
        case CREATE_RESOURCE_GROUP_STATES:
            createResourceGroupStates(context, StorageEnumStages.DELETE_RESOURCE_GROUP_STATES);
            break;
        case DELETE_RESOURCE_GROUP_STATES:
            deleteResourceGroupStates(context, StorageEnumStages.GET_BLOBS);
            break;
        case GET_BLOBS:
            getBlobs(context, StorageEnumStages.GET_LOCAL_STORAGE_DISKS);
            break;
        case GET_LOCAL_STORAGE_DISKS:
            getLocalDiskStates(context, StorageEnumStages.UPDATE_DISK_STATES);
            break;
        case UPDATE_DISK_STATES:
            updateDiskStates(context, StorageEnumStages.CREATE_DISK_STATES);
            break;
        case CREATE_DISK_STATES:
            createDiskStates(context, StorageEnumStages.DELETE_DISK_STATES);
            break;
        case DELETE_DISK_STATES:
            deleteDiskStates(context, StorageEnumStages.FINISHED);
            break;
        case FINISHED:
            context.stage = EnumerationStages.FINISHED;
            handleStorageEnumeration(context);
            break;
        default:
            break;
        }
    }

    private void handleError(StorageEnumContext context, Throwable e) {
        logSevere("Failed at stage %s with exception: %s", context.stage, Utils.toString(e));
        context.error = e;
        context.stage = EnumerationStages.ERROR;
        handleStorageEnumeration(context);
    }

    /**
     * Return a key to uniquely identify enumeration for compute host instance.
     */
    private String getEnumKey(StorageEnumContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("hostLink:").append(context.enumRequest.resourceLink());
        sb.append("-enumerationAdapterReference:")
                .append(context.parentCompute.description.enumerationAdapterReference);
        return sb.toString();
    }

    /**
     * Retrieve storage accounts from Azure.
     */
    private void getStorageAccounts(StorageEnumContext context, StorageEnumStages next) {
        String uriStr = context.enumNextPageLink;
        URI uri;

        if (uriStr == null) {
            uriStr = AdapterUriUtil.expandUriPathTemplate(LIST_STORAGE_ACCOUNTS,
                    context.parentAuth.userLink);
            uri = UriUtils.extendUriWithQuery(UriUtils.buildUri(uriStr),
                    QUERY_PARAM_API_VERSION, STORAGE_ACCOUNT_REST_API_VERSION);
        } else {
            uri = UriUtils.buildUri(uriStr);
        }

        Operation operation = Operation.createGet(uri);
        operation.addRequestHeader(Operation.ACCEPT_HEADER, Operation.MEDIA_TYPE_APPLICATION_JSON);
        operation.addRequestHeader(Operation.CONTENT_TYPE_HEADER,
                Operation.MEDIA_TYPE_APPLICATION_JSON);
        try {
            operation.addRequestHeader(Operation.AUTHORIZATION_HEADER,
                    AUTH_HEADER_BEARER_PREFIX + context.credentials.getToken());
        } catch (Exception ex) {
            this.handleError(context, ex);
            return;
        }

        operation.setCompletion((op, er) -> {
            if (er != null) {
                handleError(context, er);
                return;
            }

            StorageAccountResultList results = op.getBody(StorageAccountResultList.class);
            List<StorageAccount> storageAccounts = results.value;

            // If there are no storage accounts in Azure move to storage account cleanup.
            if (storageAccounts == null || storageAccounts.size() == 0) {
                context.subStage = StorageEnumStages.DELETE_STORAGE_DESCRIPTIONS;
                handleSubStage(context);
                return;
            }

            context.enumNextPageLink = results.nextLink;

            logFine("Retrieved %d storage accounts from Azure", storageAccounts.size());

            for (StorageAccount storageAccount : storageAccounts) {
                String id = storageAccount.id;
                context.allStorageAccounts.put(id, storageAccount);
                context.storageAccountsToUpdateCreate.put(id, storageAccount);
                context.storageAccountIds.add(id);
            }

            logFine("Processing %d storage accounts", context.storageAccountIds.size());

            context.subStage = next;
            handleSubStage(context);
        });
        sendRequest(operation);
    }

    /**
     * Query all storage descriptions for the cluster filtered by the received set of storage
     * account Ids
     */
    private void getLocalStorageAccountDescriptions(StorageEnumContext context,
            StorageEnumStages next) {
        Query query = Query.Builder.create()
                .addKindFieldClause(StorageDescription.class)
                .addFieldClause(StorageDescription.FIELD_NAME_COMPUTE_HOST_LINK,
                        context.parentCompute.documentSelfLink)
                .build();

        Query.Builder instanceIdFilterParentQuery = Query.Builder
                .create(Occurance.MUST_OCCUR);

        for (Map.Entry<String, StorageAccount> account : context.storageAccountsToUpdateCreate
                .entrySet()) {
            Query instanceIdFilter = Query.Builder
                    .create(Occurance.SHOULD_OCCUR)
                    .addFieldClause(StorageDescription.FIELD_NAME_ID,
                            account.getValue().id).build();
            instanceIdFilterParentQuery.addClause(instanceIdFilter);
        }

        query.addBooleanClause(instanceIdFilterParentQuery.build());

        QueryTask q = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .addOption(QueryOption.TOP_RESULTS)
                .setQuery(query)
                .setResultLimit(context.storageAccountsToUpdateCreate.size())
                .build();
        q.tenantLinks = context.parentCompute.tenantLinks;

        QueryUtils.startQueryTask(this, q)
                .whenComplete((queryTask, e) -> {
                    if (e != null) {
                        handleError(context, e);
                        return;
                    }
                    logFine("Found %d matching storage descriptions for Azure storage accounts",
                            queryTask.results.documentCount);

                    // If there are no matches, there is nothing to update.
                    if (queryTask.results.documentCount == 0) {
                        context.subStage = StorageEnumStages.CREATE_STORAGE_DESCRIPTIONS;
                        handleSubStage(context);
                        return;
                    }

                    for (Object s : queryTask.results.documents.values()) {
                        StorageDescription storageDescription = Utils.fromJson(s,
                                StorageDescription.class);
                        String storageAcctId = storageDescription.id;
                        context.storageDescriptions.put(storageAcctId, storageDescription);

                        // populate connectionStrings
                        if (!context.storageConnectionStrings.containsKey(storageDescription.id)) {
                            addConnectionString(context, storageDescription);
                        }
                    }

                    context.subStage = next;
                    handleSubStage(context);
                });
    }

    /**
     * Updates matching storage descriptions for given storage accounts.
     */
    private void updateStorageDescriptions(StorageEnumContext context, StorageEnumStages next) {
        if (context.storageDescriptions.size() == 0) {
            logInfo("No storage descriptions available for update");
            context.subStage = next;
            handleSubStage(context);
            return;
        }
        Iterator<Map.Entry<String, StorageDescription>> iterator =
                context.storageDescriptions.entrySet().iterator();
        AtomicInteger numOfUpdates = new AtomicInteger(context.storageDescriptions.size());
        while (iterator.hasNext()) {
            Map.Entry<String, StorageDescription> storageDescEntry = iterator.next();
            StorageDescription storageDescription = storageDescEntry.getValue();
            StorageAccount storageAccount = context.storageAccountsToUpdateCreate.get(storageDescEntry.getKey());
            iterator.remove();

            StorageDescription storageDescriptionToUpdate = new StorageDescription();
            storageDescriptionToUpdate.name = storageAccount.name;
            storageDescriptionToUpdate.authCredentialsLink = storageDescription.authCredentialsLink;
            storageDescriptionToUpdate.regionId = storageAccount.location;
            storageDescriptionToUpdate.documentSelfLink = storageDescription.documentSelfLink;
            storageDescriptionToUpdate.endpointLink = storageDescription.endpointLink;

            // populate connectionStrings
            if (!context.storageConnectionStrings.containsKey(storageDescription.id)) {
                addConnectionString(context, storageDescription);
            }

            Operation.createPatch(this, storageDescriptionToUpdate.documentSelfLink)
                    .setBody(storageDescriptionToUpdate)
                    .setCompletion((completedOp, failure) -> {
                        // Remove processed storage descriptions from the map
                        context.storageAccountsToUpdateCreate.remove(storageDescription.id);

                        // TODO: https://jira-hzn.eng.vmware.com/browse/VSYM-2765
                        if (failure != null) {
                            logWarning("Failed to update storage description: %s",
                                    failure.getMessage());
                        }

                        if (numOfUpdates.decrementAndGet() == 0) {
                            logInfo("Finished updating storage descriptions");
                            context.subStage = StorageEnumStages.CREATE_STORAGE_DESCRIPTIONS;
                            handleSubStage(context);
                        }
                    })
                    .sendWith(this);
        }
    }

    /*
     * Create all storage descriptions
     * Storage descriptions mapping to Azure storage accounts have an additional custom property
     * {"storageType" : "Microsoft.Storage/storageAccounts"}
     */
    private void createStorageDescriptions(StorageEnumContext context, StorageEnumStages next) {
        if (context.storageAccountsToUpdateCreate.size() == 0) {
            if (context.enumNextPageLink != null) {
                context.subStage = StorageEnumStages.GET_STORAGE_ACCOUNTS;
                handleSubStage(context);
                return;
            }
            logInfo("No storage account found for creation");
            context.subStage = next;
            handleSubStage(context);
            return;
        }

        logFine("%d storage description to be created", context.storageAccountsToUpdateCreate.size());

        Iterator<Map.Entry<String, StorageAccount>> iterator =
                context.storageAccountsToUpdateCreate.entrySet().iterator();
        AtomicInteger size = new AtomicInteger(context.storageAccountsToUpdateCreate.size());

        while (iterator.hasNext()) {
            Map.Entry<String, StorageAccount> storageAcct = iterator.next();
            StorageAccount storageAccount = storageAcct.getValue();
            iterator.remove();
            createStorageDescriptionHelper(context, storageAccount, size);
        }
    }

    private void createStorageDescriptionHelper(StorageEnumContext context,
            StorageAccount storageAccount, AtomicInteger size) {
        Collection<Operation> opCollection = new ArrayList<>();
        String resourceGroupName = getResourceGroupName(storageAccount.id);

        try {
            // Retrieve and store the list of access keys for the storage account
            ServiceResponse<StorageAccountKeys> keys =
                    getStorageManagementClient(context).getStorageAccountsOperations()
                    .listKeys(resourceGroupName, storageAccount.name);

            AuthCredentialsService.AuthCredentialsServiceState storageAuth =
                    new AuthCredentialsService.AuthCredentialsServiceState();
            storageAuth.documentSelfLink = UUID.randomUUID().toString();
            storageAuth.customProperties = new HashMap<>();
            storageAuth.customProperties.put(AZURE_STORAGE_ACCOUNT_KEY1, keys.getBody().getKey1());
            storageAuth.customProperties.put(AZURE_STORAGE_ACCOUNT_KEY2, keys.getBody().getKey2());
            storageAuth.tenantLinks = context.parentCompute.tenantLinks;
            storageAuth.customProperties.put(StorageDescription.FIELD_NAME_ENDPOINT_LINK,
                    context.enumRequest.endpointLink);

            Operation storageAuthOp = Operation
                    .createPost(getHost(), AuthCredentialsService.FACTORY_LINK)
                    .setBody(storageAuth);
            sendRequest(storageAuthOp);

            String connectionString = String.format(STORAGE_CONNECTION_STRING, storageAccount.name,
                    keys.getBody().getKey1());
            context.storageConnectionStrings.put(storageAccount.id, connectionString);

            String storageAuthLink = UriUtils.buildUriPath(AuthCredentialsService.FACTORY_LINK,
                    storageAuth.documentSelfLink);
            StorageDescription storageDescription = new StorageDescription();
            storageDescription.id = storageAccount.id;
            storageDescription.regionId = storageAccount.location;
            storageDescription.name = storageAccount.name;
            storageDescription.authCredentialsLink = storageAuthLink;
            storageDescription.resourcePoolLink = context.enumRequest.resourcePoolLink;
            storageDescription.documentSelfLink = UUID.randomUUID().toString();
            storageDescription.endpointLink = context.enumRequest.endpointLink;
            storageDescription.computeHostLink = context.parentCompute.documentSelfLink;
            storageDescription.customProperties = new HashMap<>();
            storageDescription.customProperties.put(AZURE_STORAGE_TYPE, AZURE_STORAGE_ACCOUNTS);
            storageDescription.customProperties.put(AZURE_STORAGE_ACCOUNT_URI,
                    storageAccount.properties.primaryEndpoints.blob);
            storageDescription.tenantLinks = context.parentCompute.tenantLinks;

            Operation storageDescOp = Operation
                    .createPost(getHost(), StorageDescriptionService.FACTORY_LINK)
                    .setBody(storageDescription);
            opCollection.add(storageDescOp);
            OperationJoin.create(opCollection)
                    .setCompletion((ops, exs) -> {
                        if (exs != null) {
                            exs.values().forEach(ex -> logWarning("Error: %s", ex.getMessage()));
                        }

                        if (context.enumNextPageLink != null) {
                            context.subStage = StorageEnumStages.GET_STORAGE_ACCOUNTS;
                            handleSubStage(context);
                        }

                        if (size.decrementAndGet() == 0) {
                            logInfo("Finished creating storage descriptions");
                            context.subStage = StorageEnumStages.DELETE_STORAGE_DESCRIPTIONS;
                            handleSubStage(context);
                        }
                    })
                    .sendWith(this);
        } catch (CloudException e) {
            handleError(context, e);
            return;
        } catch (IOException e) {
            handleError(context, e);
            return;
        }
    }

    /*
     * Delete local storage accounts and all resources inside them that no longer exist in Azure
     *
     * The logic works by recording a timestamp when enumeration starts. This timestamp is used to
     * lookup resources which haven't been touched as part of current enumeration cycle. The other
     * data point this method uses is the storage accounts discovered as part of get storage
     * accounts call.
     *
     * A delete on a storage description is invoked only if it meets two criteria:
     * - Timestamp older than current enumeration cycle.
     * - Storage account is not present on Azure.
     */
    private void deleteStorageDescription(StorageEnumContext context, StorageEnumStages next) {
        int resultLimit = Integer.getInteger(PROPERTY_NAME_ENUM_QUERY_RESULT_LIMIT, 50);
        Query query = Query.Builder.create()
                .addKindFieldClause(StorageDescription.class)
                .addFieldClause(StorageDescription.FIELD_NAME_COMPUTE_HOST_LINK,
                        context.parentCompute.documentSelfLink)
                .addRangeClause(StorageDescription.FIELD_NAME_UPDATE_TIME_MICROS,
                        QueryTask.NumericRange
                                .createLessThanRange(context.enumerationStartTimeInMicros))
                .build();

        QueryTask q = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .setQuery(query)
                .setResultLimit(resultLimit)
                .build();
        q.tenantLinks = context.parentCompute.tenantLinks;

        logFine("Querying storage descriptions for deletion");
        QueryUtils.startQueryTask(this, q)
                .whenComplete((queryTask, e) -> {
                    if (e != null) {
                        handleError(context, e);
                        return;
                    }
                    if (queryTask.results.nextPageLink == null) {
                        logInfo("No storage accounts found for deletion");
                        context.subStage = next;
                        handleSubStage(context);
                        return;
                    }

                    context.deletionNextPageLink = queryTask.results.nextPageLink;
                    deleteStorageDescriptionHelper(context);
                });
    }

    private void deleteStorageDescriptionHelper(StorageEnumContext context) {
        if (context.deletionNextPageLink == null) {
            logInfo("Finished deletion of storage descriptions for Azure");
            context.subStage = StorageEnumStages.GET_STORAGE_CONTAINERS;
            handleSubStage(context);
            return;
        }

        Operation.CompletionHandler completionHandler = (o, e) -> {
            if (e != null) {
                handleError(context, e);
                return;
            }
            QueryTask queryTask = o.getBody(QueryTask.class);

            context.deletionNextPageLink = queryTask.results.nextPageLink;

            List<Operation> operations = new ArrayList<>();
            for (Object s : queryTask.results.documents.values()) {
                StorageDescription storageDescription = Utils.fromJson(s, StorageDescription.class);
                String storageDeskId =  storageDescription.id;

                // if the storage disk is present in Azure and has older timestamp in local
                // repository it means nothing has changed about it.
                if (context.storageAccountIds.contains(storageDeskId)) {
                    continue;
                }

                operations.add(Operation.createDelete(this, storageDescription.documentSelfLink));
                logFine("Deleting storage description %s", storageDescription.documentSelfLink);
            }

            if (operations.size() == 0) {
                logFine("No storage descriptions deleted");
                deleteStorageDescriptionHelper(context);
                return;
            }

            OperationJoin.create(operations)
                    .setCompletion((ops, exs) -> {
                        if (exs != null) {
                            // We don't want to fail the whole data collection if some of the
                            // operation fails.
                            exs.values().forEach(
                                    ex -> logWarning("Error: %s", ex.getMessage()));
                        }

                        deleteStorageDescriptionHelper(context);
                    })
                    .sendWith(this);
        };
        logFine("Querying page [%s] for resources to be deleted", context.deletionNextPageLink);
        sendRequest(Operation.createGet(this, context.deletionNextPageLink)
                .setCompletion(completionHandler));
    }

    /*
     * Get all Azure containers by storage account
     */
    public void getStorageContainers(StorageEnumContext context, StorageEnumStages next) {
        if (context.allStorageAccounts.size() == 0) {
            logInfo("No storage description available - clean up all resources");
            context.subStage = StorageEnumStages.DELETE_RESOURCE_GROUP_STATES;
            handleSubStage(context);
            return;
        }

        for (Map.Entry<String, StorageAccount> account : context.allStorageAccounts.entrySet()) {
            String storageConnectionString = context.storageConnectionStrings.get(account.getValue().id);
            if (storageConnectionString == null) {
                continue;
            }

            try {
                CloudStorageAccount storageAccount = CloudStorageAccount
                        .parse(storageConnectionString);
                CloudBlobClient blobClient = storageAccount.createCloudBlobClient();
                Iterable<CloudBlobContainer> containerList = blobClient.listContainers();

                for (CloudBlobContainer container : containerList) {
                    String uri = container.getUri().toString();
                    context.containerIds.add(uri);
                    context.storageContainers.put(uri, container);
                }
                logFine("Processing %d storage containers", context.containerIds.size());
            } catch (Exception e) {
                handleError(context, e);
                return;
            }
        }

        context.subStage = next;
        handleSubStage(context);
    }

    /*
     * Get all local resource group states
     */
    private void getLocalStorageContainerStates(StorageEnumContext context, StorageEnumStages next) {
        String computeHostProperty = QueryTask.QuerySpecification
                .buildCompositeFieldName(ResourceGroupState.FIELD_NAME_CUSTOM_PROPERTIES,
                        FIELD_COMPUTE_HOST_LINK);

        Query query = Query.Builder.create()
                .addKindFieldClause(ResourceGroupState.class)
                .addFieldClause(computeHostProperty, context.parentCompute.documentSelfLink)
                .build();

        Query.Builder instanceIdFilterParentQuery = Query.Builder
                .create(Occurance.MUST_OCCUR);

        for (Map.Entry<String, CloudBlobContainer> container : context.storageContainers
                .entrySet()) {
            Query instanceIdFilter = Query.Builder
                    .create(Occurance.SHOULD_OCCUR)
                    .addFieldClause(ResourceGroupState.FIELD_NAME_ID,
                            container.getValue().getUri().toString()).build();
            instanceIdFilterParentQuery.addClause(instanceIdFilter);
        }

        query.addBooleanClause(instanceIdFilterParentQuery.build());

        QueryTask q = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .addOption(QueryOption.TOP_RESULTS)
                .setQuery(query)
                .setResultLimit(context.storageContainers.size())
                .build();
        q.tenantLinks = context.parentCompute.tenantLinks;

        QueryUtils.startQueryTask(this, q)
                .whenComplete((queryTask, e) -> {
                    if (e != null) {
                        handleError(context, e);
                        return;
                    }
                    logFine("Found %d matching resource for Azure storage containers",
                            queryTask.results.documentCount);

                    // If there are no matches, there is nothing to update.
                    if (queryTask.results.documentCount == 0) {
                        context.subStage = StorageEnumStages.CREATE_RESOURCE_GROUP_STATES;
                        handleSubStage(context);
                        return;
                    }

                    for (Object s : queryTask.results.documents.values()) {
                        ResourceGroupState resourceGroupState = Utils
                                .fromJson(s, ResourceGroupState.class);
                        String containerId = resourceGroupState.id;
                        context.resourceGroupStates.put(containerId, resourceGroupState);
                    }

                    context.subStage = next;
                    handleSubStage(context);
                });
    }

    private void updateResourceGroupStates(StorageEnumContext context, StorageEnumStages next) {
        if (context.resourceGroupStates.size() == 0) {
            logInfo("No resource group states available for update");
            context.subStage = next;
            handleSubStage(context);
            return;
        }
        Iterator<Map.Entry<String, ResourceGroupState>> iterator = context.resourceGroupStates.entrySet()
                .iterator();
        AtomicInteger numOfUpdates = new AtomicInteger(context.resourceGroupStates.size());
        while (iterator.hasNext()) {
            Map.Entry<String, ResourceGroupState> resourceGroupEntry = iterator.next();
            ResourceGroupState resourceGroup = resourceGroupEntry.getValue();
            CloudBlobContainer container = context.storageContainers.get(resourceGroupEntry.getKey());
            iterator.remove();
            createResourceGroupStateHelper(context, container, resourceGroup, numOfUpdates);
        }
    }

    private void createResourceGroupStateHelper (StorageEnumContext context,
            CloudBlobContainer container, ResourceGroupState oldResourceGroup, AtomicInteger size) {
        // Associate resource group with storage account
        String storageAcctName =  getStorageAccountNameFromUri(container.getStorageUri()
                .getPrimaryUri().getHost());
        Query query = Query.Builder.create()
                .addKindFieldClause(StorageDescription.class)
                .addFieldClause(StorageDescription.FIELD_NAME_COMPUTE_HOST_LINK,
                        context.parentCompute.documentSelfLink)
                .addFieldClause(StorageDescription.FIELD_NAME_NAME, storageAcctName)
                .build();

        QueryTask q = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .addOption(QueryOption.TOP_RESULTS)
                .setQuery(query)
                .setResultLimit(1)
                .build();
        q.tenantLinks = context.parentCompute.tenantLinks;

        QueryUtils.startQueryTask(this, q)
                .whenComplete((queryTask, e) -> {
                    if (e != null) {
                        logWarning("Could not retrieve storage description link");
                        return;
                    }
                    logFine("Found %d matching resource group", queryTask.results.documentCount);

                    String storageDescSelfLink = null;
                    // the storage account names are unique so we should only get 1 result back
                    if (queryTask.results.documentCount > 0) {
                        storageDescSelfLink = queryTask.results.documentLinks.get(0);

                        if (queryTask.results.documentCount > 1) {
                            StorageDescription storageDesc = Utils
                                    .fromJson(queryTask.results.documents
                                                    .get(queryTask.results.documentLinks.get(0)),
                                            StorageDescription.class);
                            logWarning("Found multiple instance of the same storage description %s",
                                    storageDesc.name);
                        }

                        ResourceGroupState resourceGroupState = createResourceGroupStateObject(
                                context, container, storageDescSelfLink, oldResourceGroup);
                        if (oldResourceGroup != null) {
                            updateResourceGroupState(context, resourceGroupState, size);
                        } else {
                            createResourceGroupState(context, resourceGroupState, size);
                        }
                    }
                });
    }

    private  ResourceGroupState createResourceGroupStateObject (StorageEnumContext context,
            CloudBlobContainer container, String storageLink,
            ResourceGroupState oldResourceGroupState) {
        ResourceGroupState resourceGroupState = new ResourceGroupState();
        resourceGroupState.id = container.getUri().toString();
        resourceGroupState.name = container.getName();
        if (storageLink != null) {
            resourceGroupState.groupLinks = new HashSet<>();
            resourceGroupState.groupLinks.add(storageLink);
        }
        resourceGroupState.customProperties = new HashMap<>();
        resourceGroupState.customProperties.put(CUSTOM_PROP_ENPOINT_LINK,
                context.enumRequest.endpointLink);
        resourceGroupState.customProperties.put(AZURE_STORAGE_TYPE, AZURE_STORAGE_CONTAINERS);
        resourceGroupState.customProperties.put(AZURE_STORAGE_CONTAINER_LEASE_LAST_MODIFIED,
                container.getProperties().getLastModified().toString());
        resourceGroupState.customProperties.put(AZURE_STORAGE_CONTAINER_LEASE_STATE, container
                .getProperties().getLeaseState().toString());
        resourceGroupState.customProperties.put(AZURE_STORAGE_CONTAINER_LEASE_STATUS,
                container.getProperties().getLeaseStatus().toString());
        resourceGroupState.customProperties.put(ComputeProperties.RESOURCE_TYPE_KEY,
                ResourceGroupStateType.AzureStorageContainer.name());

        if (oldResourceGroupState != null) {
            resourceGroupState.documentSelfLink = oldResourceGroupState.documentSelfLink;
        } else {
            resourceGroupState.tenantLinks = context.parentCompute.tenantLinks;
        }
        return resourceGroupState;
    }

    private void updateResourceGroupState (StorageEnumContext context, ResourceGroupState rgState,
            AtomicInteger size) {
        Operation.createPatch(this, rgState.documentSelfLink)
                .setBody(rgState)
                .setCompletion((completedOp, failure) -> {
                    // Remove processed resource group states from the map
                    context.storageContainers.remove(rgState.id);

                    // TODO: https://jira-hzn.eng.vmware.com/browse/VSYM-2765
                    if (failure != null) {
                        logWarning("Failed to update storage containers: %s", failure.getMessage());
                    }

                    if (size.decrementAndGet() == 0) {
                        logInfo("Finished updating resource group states");
                        context.subStage = StorageEnumStages.CREATE_RESOURCE_GROUP_STATES;
                        handleSubStage(context);
                    }
                })
                .sendWith(this);
    }

    /*
     * Create all resource group states
     * Resource group states mapping to Azure storage containers have an additional custom property
     * {"storageType" : "Microsoft.Storage/container"}
     */
    private void createResourceGroupStates(StorageEnumContext context, StorageEnumStages next) {
        if (context.storageContainers.size() == 0) {
            logInfo("No storage container found for creation");
            context.subStage = next;
            handleSubStage(context);
            return;
        }

        logFine("%d storage container to be created", context.storageContainers.size());

        Iterator<Map.Entry<String, CloudBlobContainer>> iterator = context.storageContainers.entrySet().iterator();
        AtomicInteger size = new AtomicInteger(context.storageContainers.size());

        while (iterator.hasNext()) {
            Map.Entry<String, CloudBlobContainer> container = iterator.next();
            CloudBlobContainer storageContainer = container.getValue();
            iterator.remove();
            createResourceGroupStateHelper(context, storageContainer, null, size);
        }
    }

    private void createResourceGroupState (StorageEnumContext context, ResourceGroupState rgState,
            AtomicInteger size) {
        sendRequest(Operation
                .createPost(this, ResourceGroupService.FACTORY_LINK)
                .setBody(rgState)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        handleError(context, e);
                        return;
                    }

                    if (size.decrementAndGet() == 0) {
                        logInfo("Finished creating resource group states");
                        context.subStage = StorageEnumStages.DELETE_RESOURCE_GROUP_STATES;
                        handleSubStage(context);
                    }
                }));
    }

    /*
     * Delete local storage containers that no longer exist in Azure
     *
     * The logic works by recording a timestamp when enumeration starts. This timestamp is used to
     * lookup resources which haven't been touched as part of current enumeration cycle. The other
     * data point this method uses is the storage accounts discovered as part of get storage accounts call.
     *
     * A delete on a resource group state  is invoked only if it meets two criteria:
     * - Timestamp older than current enumeration cycle.
     * - Storage container is not present on Azure.
     */
    private void deleteResourceGroupStates(StorageEnumContext context, StorageEnumStages next) {
        int resultLimit = Integer.getInteger(PROPERTY_NAME_ENUM_QUERY_RESULT_LIMIT, 50);
        String computeHostProperty = QueryTask.QuerySpecification
                .buildCompositeFieldName(ResourceGroupState.FIELD_NAME_CUSTOM_PROPERTIES,
                        FIELD_COMPUTE_HOST_LINK);
        String resourceTypeProperty = QueryTask.QuerySpecification.buildCompositeFieldName
                (ResourceGroupState.FIELD_NAME_CUSTOM_PROPERTIES, ComputeProperties
                        .RESOURCE_TYPE_KEY);

        Query query = Query.Builder.create()
                .addKindFieldClause(ResourceGroupState.class)
                .addFieldClause(computeHostProperty, context.parentCompute.documentSelfLink)
                .addFieldClause(resourceTypeProperty,
                        ResourceGroupStateType.AzureStorageContainer.name())
                .addRangeClause(ResourceGroupState.FIELD_NAME_UPDATE_TIME_MICROS,
                        QueryTask.NumericRange
                                .createLessThanRange(context.enumerationStartTimeInMicros))
                .build();

        QueryTask q = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .setQuery(query)
                .setResultLimit(resultLimit)
                .build();
        q.tenantLinks = context.parentCompute.tenantLinks;

        logFine("Querying resource group states for deletion");
        QueryUtils.startQueryTask(this, q)
                .whenComplete((queryTask, e) -> {
                    if (e != null) {
                        handleError(context, e);
                        return;
                    }
                    if (queryTask.results.nextPageLink == null) {
                        logInfo("No storage containers found for deletion");
                        context.subStage = next;
                        handleSubStage(context);
                        return;
                    }

                    context.deletionNextPageLink = queryTask.results.nextPageLink;
                    deleteResourceGroupHelper(context);
                });
    }

    private void deleteResourceGroupHelper(StorageEnumContext context) {
        if (context.deletionNextPageLink == null) {
            logInfo("Finished deletion of resource group states for Azure");
            context.subStage = StorageEnumStages.GET_BLOBS;
            handleSubStage(context);
            return;
        }

        Operation.CompletionHandler completionHandler = (o, e) -> {
            if (e != null) {
                handleError(context, e);
                return;
            }
            QueryTask queryTask = o.getBody(QueryTask.class);

            context.deletionNextPageLink = queryTask.results.nextPageLink;

            List<Operation> operations = new ArrayList<>();
            for (Object s : queryTask.results.documents.values()) {
                ResourceGroupState resourceGroupState = Utils.fromJson(s, ResourceGroupState.class);
                String resourceGroupId = resourceGroupState.id;

                // if the resource group is present in Azure and has older timestamp in local
                // repository it means nothing has changed about it.
                if (context.containerIds.contains(resourceGroupId)) {
                    continue;
                }

                operations.add(Operation.createDelete(this, resourceGroupState.documentSelfLink));
                logFine("Deleting storage group state %s", resourceGroupState.documentSelfLink);
            }

            if (operations.size() == 0) {
                logFine("No resource group states deleted");
                deleteResourceGroupHelper(context);
                return;
            }

            OperationJoin.create(operations)
                    .setCompletion((ops, exs) -> {
                        if (exs != null) {
                            // We don't want to fail the whole data collection if some of the
                            // operation fails.
                            exs.values().forEach(
                                    ex -> logWarning("Error: %s", ex.getMessage()));
                        }

                        deleteResourceGroupHelper(context);
                    })
                    .sendWith(this);
        };
        logFine("Querying page [%s] for resources to be deleted", context.deletionNextPageLink);
        sendRequest(Operation.createGet(this, context.deletionNextPageLink)
                .setCompletion(completionHandler));
    }

    private void getBlobs(StorageEnumContext context, StorageEnumStages next) {
        // If no storage accounts exist in Azure, no disks exist either
        // Move on to disk deletion stage
        if (context.storageAccountIds.size() == 0) {
            logInfo("No storage description available - clean up all local disks");
            context.subStage = StorageEnumStages.DELETE_DISK_STATES;
            handleSubStage(context);
            return;
        }

        for (String storageAccountId: context.storageAccountIds) {

            String storageConnectionString = context.storageConnectionStrings.get(
                    storageAccountId);
            if (storageConnectionString == null) {
                continue;
            }
            try {
                CloudStorageAccount storageAccount = CloudStorageAccount
                        .parse(storageConnectionString);
                CloudBlobClient blobClient = storageAccount.createCloudBlobClient();
                ResultContinuation nextContainerResults = null;
                do {
                    ResultSegment<CloudBlobContainer> contSegment =
                            blobClient.listContainersSegmented(null, ContainerListingDetails.NONE,
                                    QUERY_RESULT_LIMIT, nextContainerResults, null, null);

                    nextContainerResults = contSegment.getContinuationToken();
                    for (CloudBlobContainer container : contSegment.getResults()) {
                        ResultContinuation nextBlobResults = null;
                        do {
                            ResultSegment<ListBlobItem> blobsSegment = container.listBlobsSegmented(
                                    null, false, EnumSet.noneOf(BlobListingDetails.class),
                                    QUERY_RESULT_LIMIT, nextBlobResults, null, null);
                            nextBlobResults = blobsSegment.getContinuationToken();
                            for (ListBlobItem blobItem : blobsSegment.getResults()) {
                                String id = blobItem.getUri().toString();
                                context.storageBlobs.put(id, blobItem);
                                context.blobIds.add(id);
                            }
                        } while (nextBlobResults != null);
                    }
                } while (nextContainerResults != null);
            } catch (Exception e) {
                handleError(context, e);
                return;
            }
        }

        context.subStage = next;
        handleSubStage(context);
    }

    private void getLocalDiskStates(StorageEnumContext context, StorageEnumStages next) {
        Query query = Query.Builder.create()
                .addKindFieldClause(DiskState.class)
                .addFieldClause(DiskState.FIELD_NAME_COMPUTE_HOST_LINK,
                        context.parentCompute.documentSelfLink)
                .build();

        Query.Builder diskUriFilterParentQuery = Query.Builder.create(Occurance.MUST_OCCUR);

        for (Map.Entry<String, ListBlobItem> blob : context.storageBlobs.entrySet()) {
            Query diskUriFilter = Query.Builder.create(Occurance.SHOULD_OCCUR)
                    .addFieldClause(DiskState.FIELD_NAME_ID, blob.getValue().getUri())
                    .build();

            diskUriFilterParentQuery.addClause(diskUriFilter);
        }

        query.addBooleanClause(diskUriFilterParentQuery.build());

        String blobProperty = QueryTask.QuerySpecification
                .buildCompositeFieldName(DiskState.FIELD_NAME_CUSTOM_PROPERTIES,
                        AZURE_STORAGE_TYPE);
        Query.Builder typeFilterQuery = Query.Builder
                .create(Occurance.MUST_OCCUR);

        Query blobFilter = Query.Builder.create(Occurance.SHOULD_OCCUR)
                .addFieldClause(blobProperty, AZURE_STORAGE_BLOBS)
                .build();

        Query diskFilter = Query.Builder.create(Occurance.SHOULD_OCCUR)
                .addFieldClause(blobProperty, AZURE_STORAGE_DISKS)
                .build();
        typeFilterQuery.addClause(blobFilter);
        typeFilterQuery.addClause(diskFilter);

        query.addBooleanClause(typeFilterQuery.build());
        QueryTask q = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .addOption(QueryOption.TOP_RESULTS)
                .setQuery(query)
                .setResultLimit(context.storageBlobs.size())
                .build();
        q.tenantLinks = context.parentCompute.tenantLinks;

        QueryUtils.startQueryTask(this, q)
                .whenComplete((queryTask, e) -> {
                    if (e != null) {
                        handleError(context, e);
                        return;
                    }
                    logFine("Found %d matching disk states for Azure blobs",
                            queryTask.results.documentCount);

                    // If there are no matches, there is nothing to update.
                    if (queryTask.results.documentCount == 0) {
                        context.subStage = StorageEnumStages.CREATE_DISK_STATES;
                        handleSubStage(context);
                        return;
                    }

                    for (Object d : queryTask.results.documents.values()) {
                        DiskState diskState = Utils.fromJson(d, DiskState.class);
                        String diskUri = diskState.id;
                        context.diskStates.put(diskUri, diskState);
                    }

                    context.subStage = next;
                    handleSubStage(context);
                });
    }

    /**
     * Updates matching disk states for given blobs
     */
    private void updateDiskStates(StorageEnumContext context, StorageEnumStages next) {
        if (context.diskStates.size() == 0) {
            logInfo("No disk states found for update");
            context.subStage = next;
            handleSubStage(context);
            return;
        }

        Iterator<Map.Entry<String, DiskState>> iterator = context.diskStates.entrySet()
                .iterator();
        AtomicInteger numOfUpdates = new AtomicInteger(context.diskStates.size());
        while (iterator.hasNext()) {
            Map.Entry<String, DiskState> diskStateEntry = iterator.next();
            DiskState diskState = diskStateEntry.getValue();
            ListBlobItem blob = context.storageBlobs.get(diskStateEntry.getKey());
            iterator.remove();
            createDiskStateHelper(context, blob, diskState, numOfUpdates);
        }
    }

    private void createDiskStateHelper (StorageEnumContext context, ListBlobItem blob,
            DiskState oldDiskState, AtomicInteger size) {
        try {
            // Associate blob with the resource group state (container) it belongs to
            String containerId = blob.getContainer().getUri().toString();
            String computeHostProperty = QueryTask.QuerySpecification
                    .buildCompositeFieldName(ResourceGroupState.FIELD_NAME_CUSTOM_PROPERTIES,
                            FIELD_COMPUTE_HOST_LINK);
            Query query = Query.Builder.create()
                    .addKindFieldClause(ResourceGroupState.class)
                    .addFieldClause(computeHostProperty,
                            context.parentCompute.documentSelfLink)
                    .addFieldClause(ResourceGroupState.FIELD_NAME_ID, containerId)
                    .build();

            QueryTask q = QueryTask.Builder.createDirectTask()
                    .addOption(QueryOption.EXPAND_CONTENT)
                    .addOption(QueryOption.TOP_RESULTS)
                    .setQuery(query)
                    .setResultLimit(1)
                    .build();
            q.tenantLinks = context.parentCompute.tenantLinks;

            QueryUtils.startQueryTask(this, q)
                    .whenComplete((queryTask, e) -> {
                        if (e != null) {
                            logWarning("Could not retrieve resource group state.");
                            return;
                        }
                        logFine("Found %d matching resource group",
                                queryTask.results.documentCount);

                        String containerSelfLink = null;
                        // the storage container names are unique so we should only get 1 result back
                        if (queryTask.results.documentCount > 0) {
                            containerSelfLink = queryTask.results.documentLinks.get(0);

                            if (queryTask.results.documentCount > 1) {
                                ResourceGroupState rGState = Utils
                                        .fromJson(queryTask.results.documents
                                                        .get(queryTask.results.documentLinks.get(0)),
                                                ResourceGroupState.class);
                                logWarning("Found multiple instance of the same resource group %s",
                                        rGState.id);
                            }
                        }

                        DiskState diskState = createDiskStateObject(context, blob,
                                containerSelfLink, oldDiskState);
                        if (oldDiskState != null) {
                            updateDiskState(context, diskState, size);
                        } else {
                            createDiskState(context, diskState, size);
                        }
                    });
        } catch (URISyntaxException e) {
            logWarning("Could not set storage description link for blob: %s",
                    blob.getUri().toString());
        } catch (StorageException e) {
            logWarning("Could not set storage description link for disk blob: %s",
                    blob.getUri().toString());
        }
    }

    private void updateDiskState(StorageEnumContext context, DiskState diskState,
            AtomicInteger size) {
        Operation.createPatch(this, diskState.documentSelfLink)
                .setBody(diskState)
                .setCompletion((completedOp, failure) -> {
                    // Remove processed disk states from the map
                    context.storageBlobs.remove(diskState.id);

                    if (failure != null) {
                        // TODO: https://jira-hzn.eng.vmware.com/browse/VSYM-2765
                        logWarning("Failed deleting disk %s", failure.getMessage());
                    }

                    if (size.decrementAndGet() == 0) {
                        logInfo("Finished updating disk states");
                        context.subStage = StorageEnumStages.CREATE_DISK_STATES;
                        handleSubStage(context);
                    }
                })
                .sendWith(this);
    }

    /*
     * Create all disk states
     * Disk states mapping to Azure storage blobs have an additional custom property "type"
     * In Azure a storage blobs can be either a vhd or a any other kind of blob. To be able to
     * distinguish which blobs are actually disks we populate the type custom property as either
     * {"type" : "Microsoft.Storage/disks"} or {"type" : "Microsoft.Storage/blob"}
     */
    private void createDiskStates(StorageEnumContext context, StorageEnumStages next) {
        if (context.storageBlobs.size() == 0) {
            logInfo("No disk states found for creation");
            context.subStage = next;
            handleSubStage(context);
            return;
        }

        logFine("%d disk states to be created", context.storageBlobs.size());
        Iterator<Map.Entry<String, ListBlobItem>> iterator = context.storageBlobs.entrySet()
                .iterator();
        AtomicInteger size = new AtomicInteger(context.storageBlobs.size());

        while (iterator.hasNext()) {
            Map.Entry<String, ListBlobItem> blob = iterator.next();
            ListBlobItem storageBlob = blob.getValue();

            iterator.remove();
            createDiskStateHelper(context, storageBlob, null, size);
        }
    }

    private DiskState createDiskStateObject (StorageEnumContext context, ListBlobItem blob,
            String containerLink, DiskState oldDiskState) {
        DiskState diskState = new DiskState();
        diskState.name = UriUtils.getLastPathSegment(blob.getUri());
        if (containerLink != null) {
            diskState.storageDescriptionLink = containerLink;
        }
        diskState.resourcePoolLink = context.enumRequest.resourcePoolLink;
        diskState.computeHostLink = context.parentCompute.documentSelfLink;
        diskState.endpointLink = context.enumRequest.endpointLink;
        diskState.tenantLinks = context.parentCompute.tenantLinks;
        long bLength = 0;
        if (blob instanceof CloudBlob) {
            CloudBlob blobItem = (CloudBlob) blob;
            bLength = blobItem.getProperties().getLength();
        }
        diskState.capacityMBytes = bLength / B_TO_MB_FACTOR;
        diskState.customProperties = new HashMap<>();
        String diskType = isDisk(diskState.name) ? AZURE_STORAGE_DISKS : AZURE_STORAGE_BLOBS;
        diskState.customProperties.put(AZURE_STORAGE_TYPE, diskType);
        // following two properties are set to defaults - can't retrieve that information from
        // existing calls
        diskState.type = DEFAULT_DISK_TYPE;
        diskState.sourceImageReference = URI.create(DEFAULT_DISK_SERVICE_REFERENCE);
        if (oldDiskState != null) {
            if (diskState.storageDescriptionLink == null) {
                diskState.storageDescriptionLink = oldDiskState.storageDescriptionLink;
            }
            diskState.id = oldDiskState.id;
            diskState.documentSelfLink = oldDiskState.documentSelfLink;
        } else {
            diskState.id = blob.getUri().toString();
            diskState.documentSelfLink = UUID.randomUUID().toString();
        }
        return diskState;
    }

    private void createDiskState(StorageEnumContext context, DiskState diskState,
            AtomicInteger size) {
        sendRequest(Operation
                .createPost(this, DiskService.FACTORY_LINK)
                .setBody(diskState)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        handleError(context, e);
                        return;
                    }

                    if (size.decrementAndGet() == 0) {
                        logInfo("Finished creating disk states");
                        context.subStage = StorageEnumStages.DELETE_DISK_STATES;
                        handleSubStage(context);
                    }
                }));
    }

    /*
    * Delete local disk states that no longer exist in Azure
    *
    * The logic works by recording a timestamp when enumeration starts. This timestamp is used to
    * lookup resources which haven't been touched as part of current enumeration cycle. The other
    * data point this method uses is the blob discovered as part of get blob call.
    *
    * A delete on a disk state is invoked only if it meets two criteria:
    * - Timestamp older than current enumeration cycle.
    * - blob is not present on Azure.
    */
    private void deleteDiskStates(StorageEnumContext context, StorageEnumStages next) {
        Query query = Query.Builder.create()
                .addKindFieldClause(DiskState.class)
                .addFieldClause(DiskState.FIELD_NAME_COMPUTE_HOST_LINK,
                        context.parentCompute.documentSelfLink)
                .addRangeClause(DiskState.FIELD_NAME_UPDATE_TIME_MICROS,
                        QueryTask.NumericRange
                                .createLessThanRange(context.enumerationStartTimeInMicros))
                .build();

        String blobProperty = QueryTask.QuerySpecification
                .buildCompositeFieldName(DiskState.FIELD_NAME_CUSTOM_PROPERTIES,
                        AZURE_STORAGE_TYPE);
        Query.Builder typeFilterQuery = Query.Builder
                .create(Occurance.MUST_OCCUR);

        Query blobFilter = Query.Builder.create(Occurance.SHOULD_OCCUR)
                .addFieldClause(blobProperty, AZURE_STORAGE_BLOBS)
                .build();

        QueryTask.Query diskFilter = QueryTask.Query.Builder.create(QueryTask.Query.Occurance.SHOULD_OCCUR)
                .addFieldClause(blobProperty, AZURE_STORAGE_DISKS)
                .build();
        typeFilterQuery.addClause(blobFilter);
        typeFilterQuery.addClause(diskFilter);

        query.addBooleanClause(typeFilterQuery.build());

        int resultLimit = Integer
                .getInteger(PROPERTY_NAME_ENUM_QUERY_RESULT_LIMIT, QUERY_RESULT_LIMIT);
        QueryTask q = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .setQuery(query)
                .setResultLimit(resultLimit)
                .build();
        q.tenantLinks = context.parentCompute.tenantLinks;

        logFine("Querying disk states for deletion");
        QueryUtils.startQueryTask(this, q)
                .whenComplete((queryTask, e) -> {
                    if (e != null) {
                        handleError(context, e);
                        return;
                    }
                    if (queryTask.results.nextPageLink == null) {
                        logInfo("No disk states found for deletion");
                        context.subStage = next;
                        handleSubStage(context);
                        return;
                    }

                    context.deletionNextPageLink = queryTask.results.nextPageLink;
                    deleteDisksHelper(context);
                });
    }

    private void deleteDisksHelper(StorageEnumContext context) {
        if (context.deletionNextPageLink == null) {
            logInfo("Finished deletion of disk states for Azure");
            context.subStage = StorageEnumStages.FINISHED;
            handleSubStage(context);
            return;
        }

        Operation.CompletionHandler completionHandler = (o, e) -> {
            if (e != null) {
                handleError(context, e);
                return;
            }
            QueryTask queryTask = o.getBody(QueryTask.class);

            context.deletionNextPageLink = queryTask.results.nextPageLink;

            List<Operation> operations = new ArrayList<>();
            for (Object s : queryTask.results.documents.values()) {
                DiskState diskState = Utils.fromJson(s, DiskState.class);
                String diskStateId =  diskState.id;

                // the disk still exists in Azure but nothing had changed about it
                // the timestamp is old since we didn't need to update it
                if (context.blobIds.contains(diskStateId)) {
                    continue;
                }

                operations.add(Operation.createDelete(this, diskState.documentSelfLink));
                logFine("Deleting disk state %s", diskState.documentSelfLink);
            }

            if (operations.size() == 0) {
                logFine("No disk states deleted");
                deleteDisksHelper(context);
                return;
            }

            OperationJoin.create(operations)
                    .setCompletion((ops, exs) -> {
                        if (exs != null) {
                            // We don't want to fail the whole data collection if some of the
                            // operation fails.
                            exs.values().forEach(
                                    ex -> logWarning("Error: %s", ex.getMessage()));
                        }

                        deleteDisksHelper(context);
                    })
                    .sendWith(this);
        };
        logFine("Querying page [%s] for resources to be deleted", context.deletionNextPageLink);
        sendRequest(Operation.createGet(this, context.deletionNextPageLink)
                .setCompletion(completionHandler));
    }

    private StorageManagementClient getStorageManagementClient(StorageEnumContext context) {
        if (context.storageClient == null) {
            context.storageClient = new StorageManagementClientImpl(
                    AzureConstants.BASE_URI, context.credentials, context.clientBuilder,
                    getRetrofitBuilder());
            context.storageClient.setSubscriptionId(context.parentAuth.userLink);
        }
        return context.storageClient;
    }

    private Retrofit.Builder getRetrofitBuilder() {
        Retrofit.Builder builder = new Retrofit.Builder();
        builder.callbackExecutor(this.executorService);
        return builder;
    }

    // Only page blobs that have the .vhd extension are disks.
    private boolean isDisk(String name) {
        return name.endsWith(VHD_EXTENSION);
    }

    // Helper to populate map of connection strings
    private void addConnectionString(StorageEnumContext context, StorageDescription storageDesc) {
        Operation.createGet(this, storageDesc.authCredentialsLink)
                .setCompletion(
                        (op, ex) -> {
                            if (ex != null) {
                                logWarning("Failed to get storage description credentials: %s",
                                        ex.getMessage());
                                return;
                            }

                            AuthCredentialsService.AuthCredentialsServiceState storageAuth = op
                                    .getBody(AuthCredentialsService.AuthCredentialsServiceState.class);

                            String connectionString = String.format(STORAGE_CONNECTION_STRING,
                                    storageDesc.name,
                                    storageAuth.customProperties.get(AZURE_STORAGE_ACCOUNT_KEY1));

                            context.storageConnectionStrings.put(storageDesc.id, connectionString);
                        })
                .sendWith(this);
    }

    /*
     * Extract storage account name from storage accounts URI
     * The pattern of the storageAccount link is http://<accountName>.blob.core.windows.net
     **/
    private String getStorageAccountNameFromUri(String uri) {
        int p = uri.indexOf(".");
        return uri.substring(0, p);
    }

}
