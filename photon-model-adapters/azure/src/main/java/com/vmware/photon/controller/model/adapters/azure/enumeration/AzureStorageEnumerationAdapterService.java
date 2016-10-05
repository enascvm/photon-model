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

import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AUTH_HEADER_BEARER_PREFIX;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNTS;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNT_KEY1;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNT_KEY2;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_BLOBS;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_DISKS;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_TYPE;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.DEFAULT_DISK_CAPACITY;
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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.microsoft.azure.CloudException;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.storage.StorageManagementClient;
import com.microsoft.azure.management.storage.StorageManagementClientImpl;
import com.microsoft.azure.management.storage.models.StorageAccountKeys;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.ListBlobItem;

import com.microsoft.rest.ServiceResponse;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.model.storage.StorageAccount;
import com.vmware.photon.controller.model.adapters.azure.model.storage.StorageAccountResultList;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.enums.EnumerationStages;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.StorageDescriptionService;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Enumeration adapter for data collection of storage artifacts in Azure.
 */
public class AzureStorageEnumerationAdapterService extends StatelessService {
    public static final String SELF_LINK = AzureUriPaths.AZURE_STORAGE_ENUMERATION_ADAPTER;
    private static final String VHD_EXTENSION = ".vhd";

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
     * The local service context that is created to identify and create a representative set of storage descriptions
     * that are required to be created in the system based on the enumeration data received from Azure.
     */
    public static class StorageEnumContext {
        ComputeEnumerateResourceRequest enumRequest;
        ComputeDescriptionService.ComputeDescription computeHostDesc;
        long enumerationStartTimeInMicros;
        EnumerationStages stage;
        String deletionNextPageLink;

        public Throwable error;

        public AuthCredentialsService.AuthCredentialsServiceState parentAuth;

        // Storage account specific properties
        Map<String, StorageAccount> storageAccounts = new ConcurrentHashMap<>();
        List<String> storageAccountIds = new ArrayList<>();
        Map<String, StorageDescription> storageDescriptions = new ConcurrentHashMap<>();
        Map<String, String> storageConnectionStrings = new ConcurrentHashMap<>();

        // Storage blob specific properties
        Map<String, ListBlobItem> storageBlobs = new ConcurrentHashMap<>();
        List<String> blobIds = new ArrayList<>();
        Map<String, DiskState> diskStates = new ConcurrentHashMap<>();

        public StorageEnumStages subStage;
        // Stored operation to signal completion to the Azure storage enumeration once all the stages
        // are successfully completed.
        public Operation azureStorageAdapterOperation;
        // Azure clients
        StorageManagementClient storageClient;

        // Azure specific properties
        ApplicationTokenCredentials credentials;
        public OkHttpClient.Builder clientBuilder;
        public OkHttpClient httpClient;

        public StorageEnumContext(ComputeEnumerateResourceRequest request, Operation op) {
            this.enumRequest = request;
            this.stage = EnumerationStages.HOSTDESC;
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
        StorageEnumContext ctx = new StorageEnumContext(op.getBody(ComputeEnumerateResourceRequest.class), op);
        AdapterUtils.validateEnumRequest(ctx.enumRequest);
        if (ctx.enumRequest.isMockRequest) {
            op.complete();
            return;
        }
        handleStorageEnumeration(ctx);
    }

    /**
     * Creates the storage description states in the local document store based on the storage accounts received from
     * the remote endpoint.
     * @param context The local service context that has all the information needed to create the additional description
     * states in the local system.
     */
    private void handleStorageEnumeration(StorageEnumContext context) {
        switch (context.stage) {
        case HOSTDESC:
            getHostComputeDescription(context);
            break;
        case PARENTAUTH:
            getParentAuth(context);
            break;
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
            String msg = String.format("Unknown Azure enumeration stage %s ", context.stage.toString());
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
            deleteStorageDescription(context, StorageEnumStages.GET_BLOBS);
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

    /**
     * Method to retrieve the parent compute host on which the enumeration task will be performed.
     */
    private void getHostComputeDescription(StorageEnumContext ctx) {
        Consumer<Operation> onSuccess = (op) -> {
            ComputeService.ComputeStateWithDescription csd = op.getBody(ComputeService.ComputeStateWithDescription.class);
            ctx.computeHostDesc = csd.description;
            ctx.stage = EnumerationStages.PARENTAUTH;
            handleStorageEnumeration(ctx);
        };

        URI computeUri = UriUtils.extendUriWithQuery(
                UriUtils.buildUri(this.getHost(), ctx.enumRequest.resourceLink()),
                UriUtils.URI_PARAM_ODATA_EXPAND,
                Boolean.TRUE.toString());
        AdapterUtils.getServiceState(this, computeUri, onSuccess, getFailureConsumer(ctx));
    }

    /**
     * Method to arrive at the credentials needed to call the Azure API for enumerating the instances.
     */
    private void getParentAuth(StorageEnumContext ctx) {
        URI authUri = UriUtils.buildUri(this.getHost(),
                ctx.computeHostDesc.authCredentialsLink);
        Consumer<Operation> onSuccess = (op) -> {
            ctx.parentAuth = op.getBody(AuthCredentialsService.AuthCredentialsServiceState.class);
            ctx.stage = EnumerationStages.CLIENT;
            handleStorageEnumeration(ctx);
        };
        AdapterUtils.getServiceState(this, authUri, onSuccess, getFailureConsumer(ctx));
    }

    private Consumer<Throwable> getFailureConsumer(StorageEnumContext ctx) {
        return (t) -> {
            ctx.stage = EnumerationStages.ERROR;
            ctx.error = t;
            handleStorageEnumeration(ctx);
        };
    }

    private void handleError(StorageEnumContext ctx, Throwable e) {
        logSevere("Failed at stage %s with exception: %s", ctx.stage, Utils.toString(e));
        ctx.error = e;
        ctx.stage = EnumerationStages.ERROR;
        handleStorageEnumeration(ctx);
    }

    /**
     * Return a key to uniquely identify enumeration for compute host instance.
     */
    private String getEnumKey(StorageEnumContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("hostLink:").append(ctx.enumRequest.resourceLink());
        sb.append("-enumerationAdapterReference:")
                .append(ctx.computeHostDesc.enumerationAdapterReference);
        return sb.toString();
    }

    private void getStorageAccounts(StorageEnumContext context, StorageEnumStages next) {
        String uriStr = AdapterUriUtil.expandUriPathTemplate(LIST_STORAGE_ACCOUNTS,
                context.parentAuth.userLink);
        URI uri = UriUtils.extendUriWithQuery(UriUtils.buildUri(uriStr),
                QUERY_PARAM_API_VERSION, STORAGE_ACCOUNT_REST_API_VERSION);

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

            logFine("Retrieved %d storage accounts from Azure", storageAccounts.size());

            for (StorageAccount storageAccount: storageAccounts) {
                String id = storageAccount.id;
                context.storageAccounts.put(id, storageAccount);
                context.storageAccountIds.add(id);
            }

            logFine("Processing %d storage accounts", context.storageAccountIds.size());

            context.subStage = next;
            handleSubStage(context);
        });
        sendRequest(operation);
    }

    /**
     * Query all storage descriptions for the cluster filtered by the received set of storage account Ids
     */
    private void getLocalStorageAccountDescriptions(StorageEnumContext ctx, StorageEnumStages next) {
        Query query = Query.Builder.create()
                .addKindFieldClause(StorageDescription.class)
                .addFieldClause(StorageDescription.FIELD_NAME_COMPUTE_HOST_LINK,
                        ctx.enumRequest.resourceLink())
                .build();

        QueryTask.Query.Builder instanceIdFilterParentQuery = Query.Builder.create(QueryTask.Query.Occurance.MUST_OCCUR);

        for (Map.Entry<String, StorageAccount> account : ctx.storageAccounts.entrySet()) {
            QueryTask.Query instanceIdFilter = Query.Builder.create(QueryTask.Query.Occurance.SHOULD_OCCUR)
                    .addFieldClause(StorageDescription.FIELD_NAME_ID,
                            account.getValue().id).build();
            instanceIdFilterParentQuery.addClause(instanceIdFilter);
        }

        query.addBooleanClause(instanceIdFilterParentQuery.build());

        QueryTask q = QueryTask.Builder.createDirectTask()
                .addOption(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT)
                .setQuery(query).build();
        q.tenantLinks = ctx.computeHostDesc.tenantLinks;

        sendRequest(Operation
                .createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(q)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        handleError(ctx, e);
                        return;
                    }
                    QueryTask queryTask = o.getBody(QueryTask.class);

                    logFine("Found %d matching storage descriptions for Azure storage accounts",
                            queryTask.results.documentCount);

                    // If there are no matches, there is nothing to update.
                    if (queryTask.results.documentCount == 0) {
                        ctx.subStage = StorageEnumStages.CREATE_STORAGE_DESCRIPTIONS;
                        handleSubStage(ctx);
                        return;
                    }

                    for (Object s : queryTask.results.documents.values()) {
                        StorageDescription storageDescription = Utils.fromJson(s, StorageDescription.class);
                        String storageAcctId = storageDescription.id;
                        ctx.storageDescriptions.put(storageAcctId, storageDescription);

                        // populate connectionStrings
                        if (!ctx.storageConnectionStrings.containsKey(storageDescription.id)) {
                            addConnectionString(ctx, storageDescription);
                        }
                    }

                    ctx.subStage = next;
                    handleSubStage(ctx);
                }));
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
        Iterator<Map.Entry<String, StorageDescription>> iterator = context.storageDescriptions.entrySet()
                .iterator();
        AtomicInteger numOfUpdates = new AtomicInteger(context.storageDescriptions.size());
        while (iterator.hasNext()) {
            Map.Entry<String, StorageDescription> storageDescEntry = iterator.next();
            StorageDescription storageDescription = storageDescEntry.getValue();
            StorageAccount storageAccount = context.storageAccounts.get(storageDescEntry.getKey());
            iterator.remove();

            StorageDescription storageDescriptionToUpdate = new StorageDescription();
            storageDescriptionToUpdate.name = storageAccount.name;
            storageDescriptionToUpdate.authCredentialsLink = storageDescription.authCredentialsLink;
            storageDescriptionToUpdate.regionId = storageAccount.location;
            storageDescriptionToUpdate.documentSelfLink = storageDescription.documentSelfLink;

            // populate connectionStrings
            if (!context.storageConnectionStrings.containsKey(storageDescription.id)) {
                addConnectionString(context, storageDescription);
            }

            Operation.createPatch(this, storageDescriptionToUpdate.documentSelfLink)
                    .setBody(storageDescriptionToUpdate)
                    .setCompletion((completedOp, failure) -> {
                        // Remove processed storage descriptions from the map
                        context.storageAccounts.remove(storageDescription.id);

                        // TODO: https://jira-hzn.eng.vmware.com/browse/VSYM-2765
                        if (failure != null) {
                            logWarning("Failed to update storage description: %s", failure.getMessage());
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
        if (context.storageAccounts.size() == 0) {
            logInfo("No storage account found for creation");
            context.subStage = next;
            handleSubStage(context);
            return;
        }

        logFine("%d storage description to be created", context.storageAccounts.size());

        Iterator<Map.Entry<String, StorageAccount>> iterator = context.storageAccounts.entrySet().iterator();
        AtomicInteger size = new AtomicInteger(context.storageAccounts.size());

        while (iterator.hasNext()) {
            Map.Entry<String, StorageAccount> storageAcct = iterator.next();
            StorageAccount storageAccount = storageAcct.getValue();
            iterator.remove();
            createStorageDescriptionHelper(context, storageAccount, size);
        }
    }

    private void createStorageDescriptionHelper(StorageEnumContext ctx, StorageAccount storageAccount,
            AtomicInteger size) {
        Collection<Operation> opCollection = new ArrayList<>();
        String resourceGroupName = getResourceGroupName(storageAccount.id);

        try {
            // Retrieve and store the list of access keys for the storage account
            ServiceResponse<StorageAccountKeys> keys = getStorageManagementClient(ctx).getStorageAccountsOperations()
                    .listKeys(resourceGroupName, storageAccount.name);

            AuthCredentialsService.AuthCredentialsServiceState storageAuth = new AuthCredentialsService.AuthCredentialsServiceState();
            storageAuth.documentSelfLink = UUID.randomUUID().toString();;
            storageAuth.customProperties = new HashMap<>();
            storageAuth.customProperties.put(AZURE_STORAGE_ACCOUNT_KEY1, keys.getBody().getKey1());
            storageAuth.customProperties.put(AZURE_STORAGE_ACCOUNT_KEY2, keys.getBody().getKey2());
            storageAuth.tenantLinks = ctx.computeHostDesc.tenantLinks;

            Operation storageAuthOp = Operation
                    .createPost(getHost(), AuthCredentialsService.FACTORY_LINK)
                    .setBody(storageAuth);
            sendRequest(storageAuthOp);

            String connectionString = String.format(STORAGE_CONNECTION_STRING, storageAccount.name,
                    keys.getBody().getKey1());
            ctx.storageConnectionStrings.put(storageAccount.id, connectionString);

            String storageAuthLink = UriUtils.buildUriPath(AuthCredentialsService.FACTORY_LINK,
                    storageAuth.documentSelfLink);
            StorageDescription storageDescription = new StorageDescription();
            storageDescription.id = storageAccount.id;
            storageDescription.regionId = storageAccount.location;
            storageDescription.name = storageAccount.name;
            storageDescription.authCredentialsLink = storageAuthLink;
            storageDescription.resourcePoolLink = ctx.enumRequest.resourcePoolLink;
            storageDescription.documentSelfLink = UUID.randomUUID().toString();
            storageDescription.computeHostLink = ctx.enumRequest.resourceLink();
            storageDescription.customProperties = new HashMap<>();
            storageDescription.customProperties.put(AZURE_STORAGE_TYPE, AZURE_STORAGE_ACCOUNTS);
            storageDescription.tenantLinks = ctx.computeHostDesc.tenantLinks;

            Operation storageDescOp = Operation
                    .createPost(getHost(), StorageDescriptionService.FACTORY_LINK)
                    .setBody(storageDescription);
            opCollection.add(storageDescOp);
            OperationJoin.create(opCollection)
                    .setCompletion((ops, exs) -> {
                        if (exs != null) {
                            exs.values().forEach(ex -> logWarning("Error: %s", ex.getMessage()));
                        }

                        if (size.decrementAndGet() == 0) {
                            logInfo("Finished creating storage descriptions");
                            ctx.subStage = StorageEnumStages.DELETE_STORAGE_DESCRIPTIONS;
                            handleSubStage(ctx);
                        }
                    })
                    .sendWith(this);
        } catch (CloudException e) {
            handleError(ctx, e);
            return;
        } catch (IOException e) {
            handleError(ctx, e);
            return;
        }
    }

    /*
     * Delete local storage accounts and all resources inside them that no longer exist in Azure
     *
     * The logic works by recording a timestamp when enumeration starts. This timestamp is used to
     * lookup resources which haven't been touched as part of current enumeration cycle. The other
     * data point this method uses is the storage accounts discovered as part of get storage accounts call.
     *
     * A delete on a storage description is invoked only if it meets two criteria:
     * - Timestamp older than current enumeration cycle.
     * - Storage account is not present on Azure.
     */
    private void deleteStorageDescription(StorageEnumContext context, StorageEnumStages next) {
        Operation.CompletionHandler completionHandler = (o, e) -> {
            if (e != null) {
                handleError(context, e);
                return;
            }
            QueryTask queryTask = o.getBody(QueryTask.class);

            if (queryTask.results.nextPageLink == null) {
                logInfo("No storage accounts found for deletion");
                context.subStage = next;
                handleSubStage(context);
                return;
            }

            context.deletionNextPageLink = queryTask.results.nextPageLink;
            deleteStorageDescriptionHelper(context);
        };

        int resultLimit = Integer.getInteger(PROPERTY_NAME_ENUM_QUERY_RESULT_LIMIT, 50);
        QueryTask.Query query = QueryTask.Query.Builder.create()
                .addKindFieldClause(StorageDescription.class)
                .addFieldClause(StorageDescription.FIELD_NAME_COMPUTE_HOST_LINK,
                        context.enumRequest.resourceLink())
                .addRangeClause(StorageDescription.FIELD_NAME_UPDATE_TIME_MICROS,
                        QueryTask.NumericRange
                                .createLessThanRange(context.enumerationStartTimeInMicros))
                .build();

        QueryTask q = QueryTask.Builder.createDirectTask()
                .addOption(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT)
                .setQuery(query)
                .setResultLimit(resultLimit)
                .build();
        q.tenantLinks = context.computeHostDesc.tenantLinks;

        logFine("Querying storage descriptions for deletion");
        sendRequest(Operation
                .createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(q)
                .setCompletion(completionHandler));
    }

    private void deleteStorageDescriptionHelper(StorageEnumContext ctx) {
        if (ctx.deletionNextPageLink == null) {
            logInfo("Finished deletion of storage descriptions for Azure");
            ctx.subStage = StorageEnumStages.GET_BLOBS;
            handleSubStage(ctx);
            return;
        }

        Operation.CompletionHandler completionHandler = (o, e) -> {
            if (e != null) {
                handleError(ctx, e);
                return;
            }
            QueryTask queryTask = o.getBody(QueryTask.class);

            ctx.deletionNextPageLink = queryTask.results.nextPageLink;

            List<Operation> operations = new ArrayList<>();
            for (Object s : queryTask.results.documents.values()) {
                StorageDescription storageDescription = Utils.fromJson(s, StorageDescription.class);
                String storageDeskId =  storageDescription.id;

                // if the storage disk is present in Azure and has older timestamp in local repository it means nothing
                // has changed about it.
                if (ctx.storageAccountIds.contains(storageDeskId)) {
                    continue;
                }

                operations.add(Operation.createDelete(this, storageDescription.documentSelfLink));
                logFine("Deleting storage description %s", storageDescription.documentSelfLink);
            }

            if (operations.size() == 0) {
                logFine("No storage descriptions deleted");
                deleteStorageDescriptionHelper(ctx);
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

                        deleteStorageDescriptionHelper(ctx);
                    })
                    .sendWith(this);
        };
        logFine("Querying page [%s] for resources to be deleted", ctx.deletionNextPageLink);
        sendRequest(Operation.createGet(this, ctx.deletionNextPageLink)
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
            try {
                String storageConnectionString = context.storageConnectionStrings.get(storageAccountId);
                CloudStorageAccount storageAccount = null;
                try {
                    storageAccount = CloudStorageAccount.parse(storageConnectionString);
                } catch (URISyntaxException e) {
                    handleError(context, e);
                    return;
                } catch (InvalidKeyException e) {
                    handleError(context, e);
                    return;
                }

                CloudBlobClient blobClient = storageAccount.createCloudBlobClient();
                Iterable<CloudBlobContainer> containerList = blobClient.listContainers();

                for (CloudBlobContainer container : containerList) {
                    for (ListBlobItem blobItem : container.listBlobs()) {
                        String id = blobItem.getUri().toString();
                        context.storageBlobs.put(id, blobItem);
                        context.blobIds.add(id);
                    }
                }
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
                        context.computeHostDesc.documentSelfLink)
                .build();

        QueryTask.Query.Builder diskUriFilterParentQuery = QueryTask.Query.Builder
                .create(QueryTask.Query.Occurance.MUST_OCCUR);

        for (Map.Entry<String, ListBlobItem> blob : context.storageBlobs.entrySet()) {
            QueryTask.Query diskUriFilter = QueryTask.Query.Builder.create(QueryTask.Query.Occurance.SHOULD_OCCUR)
                    .addFieldClause(DiskState.FIELD_NAME_ID, blob.getValue().getUri())
                    .build();

            diskUriFilterParentQuery.addClause(diskUriFilter);
        }

        query.addBooleanClause(diskUriFilterParentQuery.build());

        String blobProperty = QueryTask.QuerySpecification
                .buildCompositeFieldName(DiskState.FIELD_NAME_CUSTOM_PROPERTIES,
                        AZURE_STORAGE_TYPE);
        QueryTask.Query.Builder typeFilterQuery = QueryTask.Query.Builder
                .create(QueryTask.Query.Occurance.MUST_OCCUR);

        QueryTask.Query blobFilter = QueryTask.Query.Builder.create(QueryTask.Query.Occurance.SHOULD_OCCUR)
                .addFieldClause(blobProperty, AZURE_STORAGE_BLOBS)
                .build();

        QueryTask.Query diskFilter = QueryTask.Query.Builder.create(QueryTask.Query.Occurance.SHOULD_OCCUR)
                .addFieldClause(blobProperty, AZURE_STORAGE_DISKS)
                .build();
        typeFilterQuery.addClause(blobFilter);
        typeFilterQuery.addClause(diskFilter);

        query.addBooleanClause(typeFilterQuery.build());
        QueryTask q = QueryTask.Builder.createDirectTask()
                .addOption(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT)
                .setQuery(query).build();
        q.tenantLinks = context.computeHostDesc.tenantLinks;

        sendRequest(Operation
                .createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(q)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        handleError(context, e);
                        return;
                    }
                    QueryTask queryTask = o.getBody(QueryTask.class);

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

                    context.subStage = StorageEnumStages.UPDATE_DISK_STATES;
                    handleSubStage(context);
                }));
    }

    /**
     * Updates matching disk states for given blobs
     */
    private void updateDiskStates(StorageEnumContext context, StorageEnumStages next) {
        if (context.diskStates.size() == 0) {
            logInfo("No disk states available for update");
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

            DiskState diskStateToUpdate = new DiskState();
            diskStateToUpdate.name = getBlobName(blob.getUri().toString());
            try {
                diskStateToUpdate.storageDescriptionLink = blob.getContainer().getName();
            } catch (URISyntaxException e) {
                logWarning("Could not update storage description link for %s", diskState.name);
                return;
            } catch (StorageException e) {
                logWarning("Could not update storage description link for %s", diskState.name);
                return;
            }
            diskStateToUpdate.storageDescriptionLink = diskState.storageDescriptionLink;
            diskStateToUpdate.computeHostLink = context.computeHostDesc.documentSelfLink;
            diskStateToUpdate.documentSelfLink = diskState.documentSelfLink;
            diskStateToUpdate.customProperties = new HashMap<>();
            String diskType = isDisk(diskStateToUpdate.name) ? AZURE_STORAGE_DISKS : AZURE_STORAGE_BLOBS;
            diskStateToUpdate.customProperties.put(AZURE_STORAGE_TYPE, diskType);

            Operation.createPatch(this, diskStateToUpdate.documentSelfLink)
                    .setBody(diskStateToUpdate)
                    .setCompletion((completedOp, failure) -> {
                        // Remove processed disk states from the map
                        context.storageBlobs.remove(diskState.id);

                        if (failure != null) {
                            // TODO: https://jira-hzn.eng.vmware.com/browse/VSYM-2765
                            logWarning("Failed deleting disk %s", failure.getMessage());
                        }

                        if (numOfUpdates.decrementAndGet() == 0) {
                            logInfo("Finished updating disk states");
                            context.subStage = StorageEnumStages.CREATE_DISK_STATES;
                            handleSubStage(context);
                        }
                    })
                    .sendWith(this);
        }
    }

    /*
     * Create all disk states
     * Disk states mapping to Azure storage blobs have an additional custom property "type"
     * In Azure a storage blobs can be either a vhd or a any other kind of blob. To be able to distinguish which blobs
     * are actually disks we populate the type custom property as either {"type" : "Microsoft.Storage/disks"} or
     * {"type" : "Microsoft.Storage/blob"}
     */
    private void createDiskStates(StorageEnumContext context, StorageEnumStages next) {
        if (context.storageBlobs.size() == 0) {
            logInfo("No disk states available for creation");
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
            createDiskStateHelper(context, storageBlob, size);
        }
    }

    private void createDiskStateHelper(StorageEnumContext context, ListBlobItem blob,
            AtomicInteger size) {
        DiskState diskState = new DiskState();
        diskState.id = blob.getUri().toString();
        diskState.name = getBlobName(blob.getUri().toString());
        try {
            diskState.storageDescriptionLink = blob.getContainer().getName();
        } catch (URISyntaxException e) {
            logWarning("Could not set storage description link for blob: %s", diskState.name);
            return;
        } catch (StorageException e) {
            logWarning("Could not set storage description link for disk blob: %s", diskState.name);
            return;
        }
        diskState.resourcePoolLink = context.enumRequest.resourcePoolLink;
        diskState.documentSelfLink = UUID.randomUUID().toString();
        diskState.customProperties = new HashMap<>();
        String diskType = isDisk(diskState.name) ? AZURE_STORAGE_DISKS : AZURE_STORAGE_BLOBS;
        diskState.customProperties.put(AZURE_STORAGE_TYPE, diskType);
        // following three properties are set to defaults - can't retrieve that information from existing calls
        diskState.type = DEFAULT_DISK_TYPE;
        diskState.capacityMBytes = DEFAULT_DISK_CAPACITY;
        diskState.sourceImageReference = URI.create(DEFAULT_DISK_SERVICE_REFERENCE);
        diskState.computeHostLink = context.computeHostDesc.documentSelfLink;
        diskState.tenantLinks = context.computeHostDesc.tenantLinks;

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
        Operation.CompletionHandler completionHandler = (o, e) -> {
            if (e != null) {
                handleError(context, e);
                return;
            }
            QueryTask queryTask = o.getBody(QueryTask.class);

            if (queryTask.results.nextPageLink == null) {
                logInfo("No disk states found for deletion");
                context.subStage = next;
                handleSubStage(context);
                return;
            }

            context.deletionNextPageLink = queryTask.results.nextPageLink;
            deleteDisksHelper(context);
        };

        QueryTask.Query query = QueryTask.Query.Builder.create()
                .addKindFieldClause(DiskState.class)
                .addFieldClause(DiskState.FIELD_NAME_COMPUTE_HOST_LINK,
                        context.computeHostDesc.documentSelfLink)
                .addRangeClause(DiskState.FIELD_NAME_UPDATE_TIME_MICROS,
                        QueryTask.NumericRange
                                .createLessThanRange(context.enumerationStartTimeInMicros))
                .build();

        String blobProperty = QueryTask.QuerySpecification
                .buildCompositeFieldName(DiskState.FIELD_NAME_CUSTOM_PROPERTIES,
                        AZURE_STORAGE_TYPE);
        QueryTask.Query.Builder typeFilterQuery = QueryTask.Query.Builder
                .create(QueryTask.Query.Occurance.MUST_OCCUR);

        QueryTask.Query blobFilter = QueryTask.Query.Builder.create(QueryTask.Query.Occurance.SHOULD_OCCUR)
                .addFieldClause(blobProperty, AZURE_STORAGE_BLOBS)
                .build();

        QueryTask.Query diskFilter = QueryTask.Query.Builder.create(QueryTask.Query.Occurance.SHOULD_OCCUR)
                .addFieldClause(blobProperty, AZURE_STORAGE_DISKS)
                .build();
        typeFilterQuery.addClause(blobFilter);
        typeFilterQuery.addClause(diskFilter);

        query.addBooleanClause(typeFilterQuery.build());

        int resultLimit = Integer.getInteger(PROPERTY_NAME_ENUM_QUERY_RESULT_LIMIT, QUERY_RESULT_LIMIT);
        QueryTask q = QueryTask.Builder.createDirectTask()
                .addOption(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT)
                .setQuery(query)
                .setResultLimit(resultLimit)
                .build();
        q.tenantLinks = context.computeHostDesc.tenantLinks;

        logFine("Querying disk states for deletion");
        sendRequest(Operation
                .createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(q)
                .setCompletion(completionHandler));
    }

    private void deleteDisksHelper(StorageEnumContext ctx) {
        if (ctx.deletionNextPageLink == null) {
            logInfo("Finished deletion of disk states for Azure");
            ctx.subStage = StorageEnumStages.FINISHED;
            handleSubStage(ctx);
            return;
        }

        Operation.CompletionHandler completionHandler = (o, e) -> {
            if (e != null) {
                handleError(ctx, e);
                return;
            }
            QueryTask queryTask = o.getBody(QueryTask.class);

            ctx.deletionNextPageLink = queryTask.results.nextPageLink;

            List<Operation> operations = new ArrayList<>();
            for (Object s : queryTask.results.documents.values()) {
                DiskState diskState = Utils.fromJson(s, DiskState.class);
                String diskStateId =  diskState.id;

                // the disk still exists in Azure but nothing had changed about it
                // the timestamp is old since we didn't need to update it
                if (ctx.blobIds.contains(diskStateId)) {
                    continue;
                }

                operations.add(Operation.createDelete(this, diskState.documentSelfLink));
                logFine("Deleting disk state %s", diskState.documentSelfLink);
            }

            if (operations.size() == 0) {
                logFine("No disk states deleted");
                deleteDisksHelper(ctx);
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

                        deleteDisksHelper(ctx);
                    })
                    .sendWith(this);
        };
        logFine("Querying page [%s] for resources to be deleted", ctx.deletionNextPageLink);
        sendRequest(Operation.createGet(this, ctx.deletionNextPageLink)
                .setCompletion(completionHandler));
    }

    private StorageManagementClient getStorageManagementClient(StorageEnumContext ctx) {
        if (ctx.storageClient == null) {
            ctx.storageClient = new StorageManagementClientImpl(
                    AzureConstants.BASE_URI, ctx.credentials, ctx.clientBuilder,
                    getRetrofitBuilder());
            ctx.storageClient.setSubscriptionId(ctx.parentAuth.userLink);
        }
        return ctx.storageClient;
    }

    private Retrofit.Builder getRetrofitBuilder() {
        Retrofit.Builder builder = new Retrofit.Builder();
        builder.callbackExecutor(this.executorService);
        return builder;
    }

    // The uri of the blobs has the following pattern https://storageAccountName/containerName/blobName
    private String getBlobName(String path) {
        int p = path.lastIndexOf("/");
        return path.substring(p + 1);
    }

    // Only page blobs that have the .vhd extension are disks.
    private boolean isDisk(String name) {
        return name.substring(name.length() - 4) == VHD_EXTENSION;
    }

    // Helper to populate map of connection strings
    public void addConnectionString(StorageEnumContext context, StorageDescription storageDesc) {
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

                            String connectionString = String.format(STORAGE_CONNECTION_STRING, storageDesc.name,
                                    storageAuth.customProperties.get(AZURE_STORAGE_ACCOUNT_KEY1));

                            context.storageConnectionStrings.put(storageDesc.id, connectionString);
                        })
                .sendWith(this);
    }
}
