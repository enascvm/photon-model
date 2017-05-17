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

import static com.vmware.photon.controller.model.ComputeProperties.COMPUTE_HOST_LINK_PROP_NAME;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AUTH_HEADER_BEARER_PREFIX;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNT_KEY1;
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
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.getQueryResultLimit;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.cleanUpHttpClient;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.getAzureConfig;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.getResourceGroupName;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.CUSTOM_PROP_ENDPOINT_LINK;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.storage.StorageAccountsOperations;
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
import com.vmware.photon.controller.model.adapters.azure.utils.AzureDeferredResultServiceCallback;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.ComputeEnumerateAdapterRequest;
import com.vmware.photon.controller.model.adapters.util.enums.EnumerationStages;
import com.vmware.photon.controller.model.query.QueryStrategy;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.query.QueryUtils.QueryByPages;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.ResourceGroupService;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.StorageDescriptionService;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Enumeration adapter for data collection of storage artifacts in Azure.
 */
public class AzureStorageEnumerationAdapterService extends StatelessService {

    public static final String SELF_LINK = AzureUriPaths.AZURE_STORAGE_ENUMERATION_ADAPTER;
    public static final int B_TO_MB_FACTOR = 1024 * 1024;
    private static final int MAX_RESOURCES_TO_QUERY = Integer
            .getInteger(UriPaths.PROPERTY_PREFIX
                    + "enum.max.resources.query.on.delete", 950);
    private static final String VHD_EXTENSION = ".vhd";
    private ExecutorService executorService;

    /**
     * The local service context that is created to identify and create a representative set of
     * storage descriptions that are required to be created in the system based on the enumeration
     * data received from Azure.
     */
    public static class StorageEnumContext {
        public Throwable error;
        public AuthCredentialsService.AuthCredentialsServiceState parentAuth;
        public StorageEnumStages subStage;
        // Stored operation to signal completion to the Azure storage enumeration once all
        // the stages are successfully completed.
        public Operation operation;
        public OkHttpClient.Builder clientBuilder;
        public OkHttpClient httpClient;
        ComputeEnumerateResourceRequest request;
        ComputeStateWithDescription parentCompute;
        long enumerationStartTimeInMicros;
        EnumerationStages stage;
        String enumNextPageLink;
        Map<String, StorageAccount> storageAccountsToUpdateCreate = new ConcurrentHashMap<>();
        Map<String, StorageDescription> storageDescriptionsForPatching = new ConcurrentHashMap<>();
        Set<String> storageAccountIds = new HashSet<>();
        Map<String, StorageDescription> storageDescriptions = new ConcurrentHashMap<>();
        Map<String, String> storageConnectionStrings = new ConcurrentHashMap<>();
        // Storage container specific properties
        Map<String, CloudBlobContainer> storageContainers = new ConcurrentHashMap<>();
        List<String> containerIds = new ArrayList<>();
        Map<String, ResourceGroupState> resourceGroupStates = new ConcurrentHashMap<>();
        // Storage blob specific properties
        Map<String, ListBlobItem> storageBlobs = new ConcurrentHashMap<>();
        // stores mapping of ListBlobItem URI and StorageAccount
        Map<String, StorageAccount> storageAccountBlobUriMap = new ConcurrentHashMap<>();
        // stores mapping of StorageAccount id and its StorageAccount
        Map<String, StorageAccount> storageAccountMap = new ConcurrentHashMap<>();

        List<String> blobIds = new ArrayList<>();
        Map<String, DiskState> diskStates = new ConcurrentHashMap<>();
        // Azure clients
        StorageManagementClient storageClient;
        // Azure specific properties
        ApplicationTokenCredentials credentials;

        public StorageEnumContext(ComputeEnumerateAdapterRequest request,
                Operation op) {
            this.request = request.original;
            this.parentAuth = request.parentAuth;
            this.parentCompute = request.parentCompute;

            this.stage = EnumerationStages.CLIENT;
            this.operation = op;
        }
    }

    private enum StorageEnumStages {
        GET_STORAGE_ACCOUNTS,
        GET_LOCAL_STORAGE_ACCOUNTS,
        UPDATE_STORAGE_DESCRIPTIONS,
        CREATE_STORAGE_DESCRIPTIONS,
        PATCH_ADDITIONAL_STORAGE_DESCRIPTION_FIELDS,
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

    public AzureStorageEnumerationAdapterService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    /**
     * Constrain every query with endpointLink and tenantLinks, if presented.
     */
    private static void addScopeCriteria(Query.Builder qBuilder,
            Class<? extends ResourceState> stateClass, StorageEnumContext ctx) {
        // Add TENANT_LINKS criteria
        QueryUtils.addTenantLinks(qBuilder, ctx.parentCompute.tenantLinks);
        // Add ENDPOINT_LINK criteria
        QueryUtils.addEndpointLink(qBuilder, stateClass, ctx.request.endpointLink);
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

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }

        StorageEnumContext context = new StorageEnumContext(
                op.getBody(ComputeEnumerateAdapterRequest.class), op);

        if (context.request.isMockRequest) {
            op.complete();
            return;
        }

        handleEnumeration(context);
    }

    /**
     * Creates the storage description states in the local document store based on the storage
     * accounts received from the remote endpoint.
     *
     * @param context
     *            The local service context that has all the information needed to create the
     *            additional description states in the local system.
     */
    private void handleEnumeration(StorageEnumContext context) {
        switch (context.stage) {
        case CLIENT:
            if (context.credentials == null) {
                try {
                    context.credentials = getAzureConfig(context.parentAuth);
                } catch (Throwable e) {
                    logSevere(e);
                    context.error = e;
                    context.stage = EnumerationStages.ERROR;
                    handleEnumeration(context);
                    return;
                }
            }
            if (context.httpClient == null) {
                try {
                    // Creating a shared singleton Http client instance
                    // Reference
                    // https://square.github.io/okhttp/3.x/okhttp/okhttp3/OkHttpClient.html
                    // TODO: https://github.com/Azure/azure-sdk-for-java/issues/1000
                    // TODO: https://jira-hzn.eng.vmware.com/browse/VSYM-2775
                    context.httpClient = new OkHttpClient();
                    context.clientBuilder = context.httpClient.newBuilder().connectTimeout(30,
                            TimeUnit.SECONDS);
                } catch (Exception e) {
                    handleError(context, e);
                    return;
                }
            }
            context.stage = EnumerationStages.ENUMERATE;
            handleEnumeration(context);
            break;
        case ENUMERATE:
            String enumKey = getEnumKey(context);
            switch (context.request.enumerationAction) {
            case START:
                logInfo(() -> String.format("Launching Azure storage enumeration for %s", enumKey));
                context.enumerationStartTimeInMicros = Utils.getNowMicrosUtc();
                context.request.enumerationAction = EnumerationAction.REFRESH;
                handleEnumeration(context);
                break;
            case REFRESH:
                context.subStage = StorageEnumStages.GET_STORAGE_ACCOUNTS;
                handleSubStage(context);
                break;
            case STOP:
                logInfo(() -> String.format("Azure storage enumeration will be stopped for %s",
                        enumKey));
                context.stage = EnumerationStages.FINISHED;
                handleEnumeration(context);
                break;
            default:
                logSevere(() -> String.format("Unknown Azure storage enumeration action %s",
                        context.request.enumerationAction));
                context.stage = EnumerationStages.ERROR;
                handleEnumeration(context);
                break;
            }
            break;
        case FINISHED:
            logInfo(() -> String.format("Azure storage enumeration finished for %s",
                    getEnumKey(context)));
            cleanUpHttpClient(this, context.httpClient);
            context.operation.complete();
            break;
        case ERROR:
            logWarning(() -> String.format("Azure storage enumeration error for %s",
                    getEnumKey(context)));
            cleanUpHttpClient(this, context.httpClient);
            context.operation.fail(context.error);
            break;
        default:
            String msg = String.format("Unknown Azure storage enumeration stage %s ",
                    context.stage.toString());
            logSevere(() -> msg);
            context.error = new IllegalStateException(msg);
            cleanUpHttpClient(this, context.httpClient);
            context.operation.fail(context.error);
        }
    }

    private void handleSubStage(StorageEnumContext context) {
        logInfo("Azure Storage enumeration at stage %s, for %s", context.subStage,
                getEnumKey(context));
        switch (context.subStage) {
        case GET_STORAGE_ACCOUNTS:
            getStorageAccounts(context, StorageEnumStages.GET_LOCAL_STORAGE_ACCOUNTS);
            break;
        case GET_LOCAL_STORAGE_ACCOUNTS:
            getLocalStorageAccountDescriptions(context,
                    StorageEnumStages.UPDATE_STORAGE_DESCRIPTIONS);
            break;
        case UPDATE_STORAGE_DESCRIPTIONS:
            updateStorageDescriptions(context, StorageEnumStages.CREATE_STORAGE_DESCRIPTIONS);
            break;
        case CREATE_STORAGE_DESCRIPTIONS:
            createStorageDescriptions(context, StorageEnumStages.PATCH_ADDITIONAL_STORAGE_DESCRIPTION_FIELDS);
            break;
        case PATCH_ADDITIONAL_STORAGE_DESCRIPTION_FIELDS:
            patchAdditionalFields(context, StorageEnumStages.DELETE_STORAGE_DESCRIPTIONS);
            break;
        case DELETE_STORAGE_DESCRIPTIONS:
            deleteStorageDescription(context, StorageEnumStages.GET_STORAGE_CONTAINERS);
            break;
        case GET_STORAGE_CONTAINERS:
            getStorageContainersAsync(context, StorageEnumStages.GET_LOCAL_STORAGE_CONTAINERS);
            break;
        case GET_LOCAL_STORAGE_CONTAINERS:
            getLocalStorageContainerStates(context, StorageEnumStages.UPDATE_RESOURCE_GROUP_STATES);
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
            getBlobsAsync(context, StorageEnumStages.GET_LOCAL_STORAGE_DISKS);
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
            handleEnumeration(context);
            break;
        default:
            context.stage = EnumerationStages.FINISHED;
            handleEnumeration(context);
            break;
        }
    }

    private void handleError(StorageEnumContext context, Throwable e) {
        logSevere(() -> String.format("Failed at stage %s, substage %s with exception: %s",
                context.stage, context.subStage, Utils.toString(e)));
        context.error = e;
        context.stage = EnumerationStages.ERROR;
        handleEnumeration(context);
    }

    /**
     * Return a key to uniquely identify enumeration for compute host instance.
     */
    private String getEnumKey(StorageEnumContext context) {
        return context.request.getEnumKey();
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

        context.storageAccountsToUpdateCreate.clear();
        context.storageAccountBlobUriMap.clear();
        context.storageAccountMap.clear();

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
                op.complete();
                handleError(context, er);
                return;
            }

            StorageAccountResultList results = op.getBody(StorageAccountResultList.class);
            op.complete();

            List<StorageAccount> storageAccounts = results.value;

            // If there are no storage accounts in Azure move to storage account cleanup.
            if (storageAccounts == null || storageAccounts.size() == 0) {
                context.subStage = StorageEnumStages.DELETE_STORAGE_DESCRIPTIONS;
                handleSubStage(context);
                return;
            }

            context.enumNextPageLink = results.nextLink;

            logFine(() -> String.format("Retrieved %d storage accounts from Azure",
                    storageAccounts.size()));

            for (StorageAccount storageAccount : storageAccounts) {
                String id = storageAccount.id;
                context.storageAccountsToUpdateCreate.put(id, storageAccount);
                context.storageAccountIds.add(id);
                context.storageAccountMap.put(id, storageAccount);
            }

            logFine(() -> String.format("Processing %d storage accounts",
                    context.storageAccountIds.size()));

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
        if (context.storageAccountsToUpdateCreate.isEmpty()) {
            context.subStage = StorageEnumStages.CREATE_STORAGE_DESCRIPTIONS;
            handleSubStage(context);
            return;
        }

        context.storageDescriptions.clear();

        Query.Builder qBuilder = Query.Builder.create()
                .addKindFieldClause(StorageDescription.class)
                .addFieldClause(StorageDescription.FIELD_NAME_COMPUTE_HOST_LINK,
                        context.parentCompute.documentSelfLink);

        Query.Builder instanceIdFilterParentQuery = Query.Builder
                .create(Occurance.MUST_OCCUR);

        for (Map.Entry<String, StorageAccount> account : context.storageAccountsToUpdateCreate
                .entrySet()) {
            Query instanceIdFilter = Query.Builder
                    .create(Occurance.SHOULD_OCCUR)
                    .addFieldClause(StorageDescription.FIELD_NAME_ID,
                            account.getValue().id)
                    .build();
            instanceIdFilterParentQuery.addClause(instanceIdFilter);
        }

        qBuilder.addClause(instanceIdFilterParentQuery.build());

        QueryByPages<StorageDescription> queryLocalStates = new QueryByPages<>(getHost(),
                qBuilder.build(),
                StorageDescription.class, context.parentCompute.tenantLinks,
                context.request.endpointLink)
                        .setMaxPageSize(getQueryResultLimit());

        queryLocalStates.collectDocuments(Collectors.toList()).whenComplete((sds, ex) -> {
            if (ex != null) {
                handleError(context, ex);
                return;
            }
            logFine(() -> String.format("Found %d matching storage descriptions for Azure"
                    + " storage accounts", sds.size()));

            List<DeferredResult<AuthCredentialsServiceState>> results = sds
                    .stream().map(sd -> {
                        context.storageDescriptions.put(sd.id, sd);

                        // populate connectionStrings
                        if (!context.storageConnectionStrings.containsKey(sd.id)) {
                            return loadStorageAuth(context, sd);
                        } else {
                            return DeferredResult.<AuthCredentialsServiceState> completed(null);
                        }
                    }).collect(Collectors.toList());

            DeferredResult.allOf(results).handle((creds, e) -> {
                if (e != null) {
                    logWarning(() -> String.format("Failed to get storage description"
                            + " credentials: %s", e.getMessage()));
                }
                context.subStage = next;
                handleSubStage(context);
                return null;
            });

        });
    }

    /**
     * Updates matching storage descriptions for given storage accounts.
     */
    private void updateStorageDescriptions(StorageEnumContext context, StorageEnumStages next) {
        if (context.storageDescriptions.size() == 0) {
            logFine(() -> "No storage descriptions available for update");
            context.subStage = next;
            handleSubStage(context);
            return;
        }

        List<DeferredResult<Operation>> updates = context.storageDescriptions
                .values().stream().map(sd -> {
                    StorageAccount storageAccount = context.storageAccountsToUpdateCreate
                            .remove(sd.id);

                    StorageDescription storageDescriptionToUpdate = new StorageDescription();
                    storageDescriptionToUpdate.name = storageAccount.name;
                    storageDescriptionToUpdate.authCredentialsLink = sd.authCredentialsLink;
                    storageDescriptionToUpdate.regionId = storageAccount.location;
                    storageDescriptionToUpdate.documentSelfLink = sd.documentSelfLink;
                    storageDescriptionToUpdate.endpointLink = sd.endpointLink;
                    storageDescriptionToUpdate.tenantLinks = sd.tenantLinks;
                    storageDescriptionToUpdate.regionId = storageAccount.location;

                    context.storageDescriptionsForPatching.put(sd.id, sd);
                    return storageDescriptionToUpdate;
                })
                .map(sd -> Operation.createPatch(this, sd.documentSelfLink)
                        .setBody(sd)
                        .setCompletion((o, e) -> {
                            if (e != null) {
                                logWarning(
                                        () -> String.format("Failed to update storage description:"
                                                + " %s", e.getMessage()));
                            }
                        }))
                .map(o -> sendWithDeferredResult(o))
                .collect(java.util.stream.Collectors.toList());

        DeferredResult.allOf(updates).whenComplete((ignore, e) -> {
            logFine(() -> "Finished updating storage descriptions");
            context.subStage = next;
            handleSubStage(context);
        });
    }

    /*
     * Create all storage descriptions Storage descriptions mapping to Azure storage accounts have
     * an additional custom property {"storageType" : "Microsoft.Storage/storageAccounts"}
     */
    private void createStorageDescriptions(StorageEnumContext context, StorageEnumStages next) {
        if (context.storageAccountsToUpdateCreate.size() == 0) {
            if (context.enumNextPageLink != null) {
                context.subStage = StorageEnumStages.GET_STORAGE_ACCOUNTS;
                handleSubStage(context);
                return;
            }
            logFine(() -> "No storage account found for creation");
            context.subStage = next;
            handleSubStage(context);
            return;
        }

        logFine(() -> String.format("%d storage description to be created",
                context.storageAccountsToUpdateCreate.size()));

        StorageAccountsOperations stOps = getStorageManagementClient(
                context).getStorageAccountsOperations();

        List<DeferredResult<StorageDescription>> results = context.storageAccountsToUpdateCreate
                .values().stream()
                .map(sa -> createStorageDescription(context, sa, stOps))
                .collect(java.util.stream.Collectors.toList());

        DeferredResult.allOf(results).whenComplete((sds, e) -> {
            if (e != null) {
                logWarning(() -> String.format("Error: %s", e.getMessage()));
            }
            context.subStage = next;
            handleSubStage(context);
        });
    }

    private DeferredResult<StorageDescription> createStorageDescription(StorageEnumContext context,
            StorageAccount storageAccount, StorageAccountsOperations stOps) {
        String resourceGroupName = getResourceGroupName(storageAccount.id);
        AzureDeferredResultServiceCallback<StorageAccountKeys> handler = new AzureDeferredResultServiceCallback<StorageAccountKeys>(
                this, "Load account keys for storage:" + storageAccount.name) {

            @Override
            protected DeferredResult<StorageAccountKeys> consumeSuccess(
                    StorageAccountKeys keys) {
                logFine(() -> String.format("Retrieved the storage account keys for"
                        + " storage account [%s].", storageAccount.name));
                return DeferredResult.completed(keys);
            }
        };
        stOps.listKeysAsync(resourceGroupName, storageAccount.name, handler);
        return handler.toDeferredResult()
                .thenCompose(keys -> AzureUtils.storeKeys(getHost(), keys,
                        context.request.endpointLink, context.parentCompute.tenantLinks))
                .thenApply(auth -> {
                    String connectionString = String.format(STORAGE_CONNECTION_STRING,
                            storageAccount.name,
                            auth.customProperties.get(AZURE_STORAGE_ACCOUNT_KEY1));

                    context.storageConnectionStrings.put(storageAccount.id, connectionString);
                    return auth;
                })
                .thenApply(auth -> {
                    StorageDescription storageDesc = AzureUtils.constructStorageDescription(
                            context.parentCompute, context.request, storageAccount, auth.documentSelfLink);
                    context.storageDescriptionsForPatching.put(storageDesc.id, storageDesc);
                    return storageDesc;
                })
                .thenCompose(sd -> sendWithDeferredResult(Operation
                        .createPost(
                                context.request.buildUri(StorageDescriptionService.FACTORY_LINK))
                        .setBody(sd).setCompletion((o, e) -> {
                            if (e != null) {
                                logWarning(
                                        "Unable to store storage description for storage account:[%s], reason: %s",
                                        storageAccount.name, Utils.toJsonHtml(e));
                            }
                        }), StorageDescription.class));
    }

    /**
     * Patch additional values to storage description fetched from azure's storage account.
     */
    private void patchAdditionalFields(StorageEnumContext context, StorageEnumStages next) {
        if (context.storageDescriptionsForPatching.size() == 0) {
            logFine(() -> "No storage accounts need to be patched.");
            context.subStage = next;
            handleSubStage(context);
            return;
        }

        // Patching power state and network Information. Hence 2.
        // If we patch more fields, this number should be increased accordingly.
        StorageManagementClient storageManagementClient = getStorageManagementClient(context);
        StorageAccountsOperations stOps = storageManagementClient
                .getStorageAccountsOperations();
        DeferredResult.allOf(context.storageDescriptionsForPatching
                .values().stream()
                .map(storageDescription -> patchStorageDetails(stOps, storageDescription))
                .map(dr -> dr.thenCompose(storageDescription -> sendWithDeferredResult(
                        Operation.createPatch(
                                context.request.buildUri(storageDescription.documentSelfLink))
                                .setBody(storageDescription)
                                .setCompletion((o, e) -> {
                                    if (e != null) {
                                        logWarning(() -> String.format(
                                                "Error updating Storage:[%s], reason: %s",
                                                storageDescription.name, e));
                                    }
                                }))))
                .collect(Collectors.toList()))
                .whenComplete((all, e) -> {
                    if (e != null) {
                        logWarning(() -> String.format("Error: %s", e));
                    }
                    context.subStage = next;
                    handleSubStage(context);
                });

    }

    private DeferredResult<StorageDescription> patchStorageDetails(StorageAccountsOperations stOps,
            StorageDescription storageDescription) {
        String resourceGroupName = getResourceGroupName(storageDescription.id);
        String storageName = storageDescription.name;
        AzureDeferredResultServiceCallback<com.microsoft.azure.management.storage.models.StorageAccount> handler =
                new AzureDeferredResultServiceCallback<com.microsoft.azure.management.storage.models.StorageAccount>(
                        this, "Load storage account view:" + storageName) {
                    @Override
                    protected DeferredResult<com.microsoft.azure.management.storage.models.StorageAccount> consumeSuccess(
                            com.microsoft.azure.management.storage.models.StorageAccount sa) {
                        logFine(() -> String
                                .format("Retrieved instance view for storage account [%s].",
                                        storageName));
                        return DeferredResult.completed(sa);
                    }
                };
        stOps.getPropertiesAsync(resourceGroupName, storageName, handler);
        return handler.toDeferredResult().thenApply(sa -> {
            if (sa.getCreationTime() != null) {
                storageDescription.creationTimeMicros = TimeUnit.MILLISECONDS
                        .toMicros(sa.getCreationTime().getMillis());
            }
            return storageDescription;
        });
    }

    /*
     * Delete local storage accounts and all resources inside them that no longer exist in Azure
     *
     * The logic works by recording a timestamp when enumeration starts. This timestamp is used to
     * lookup resources which haven't been touched as part of current enumeration cycle. The other
     * data point this method uses is the storage accounts discovered as part of get storage
     * accounts call.
     *
     * A delete on a storage description is invoked only if it meets two criteria: - Timestamp older
     * than current enumeration cycle. - Storage account is not present on Azure.
     */
    private void deleteStorageDescription(StorageEnumContext context, StorageEnumStages next) {
        Query.Builder qBuilder = Query.Builder.create()
                .addKindFieldClause(StorageDescription.class)
                .addFieldClause(StorageDescription.FIELD_NAME_COMPUTE_HOST_LINK,
                        context.parentCompute.documentSelfLink)
                .addRangeClause(StorageDescription.FIELD_NAME_UPDATE_TIME_MICROS,
                        QueryTask.NumericRange
                                .createLessThanRange(context.enumerationStartTimeInMicros));

        QueryStrategy<StorageDescription> queryLocalStates = new QueryByPages<>(
                getHost(),
                qBuilder.build(),
                StorageDescription.class,
                context.parentCompute.tenantLinks,
                context.request.endpointLink)
                        .setMaxPageSize(getQueryResultLimit());

        List<DeferredResult<Operation>> ops = new ArrayList<>();
        queryLocalStates.queryDocuments(sd -> {
            if (context.storageAccountIds.contains(sd.id)) {
                return;
            }

            Operation dOp = Operation.createDelete(context.request.buildUri(sd.documentSelfLink));

            logFine(() -> String.format("Deleting storage description %s", sd.documentSelfLink));

            DeferredResult<Operation> dr = sendWithDeferredResult(dOp)
                    .whenComplete((o, e) -> {

                        final String message = "Delete storage description stale %s state";
                        if (e != null) {
                            logWarning(message + ": ERROR - %s",
                                    sd.documentSelfLink, Utils.toString(e));
                        } else {
                            logFine(message + ": SUCCESS",
                                    sd.documentSelfLink);
                        }
                    });

            ops.add(dr);
        })
                .thenCompose(r -> DeferredResult.allOf(ops))
                .whenComplete((r, e) -> {
                    logFine(() -> "Finished deletion of storage descriptions for Azure");
                    context.subStage = next;
                    handleSubStage(context);
                });
    }

    /*
     * Get all Azure containers by storage account
     */
    public void getStorageContainersAsync(StorageEnumContext context, StorageEnumStages next) {
        if (context.storageAccountIds.size() == 0) {
            logFine(() -> "No storage description available - clean up all resources");
            context.subStage = StorageEnumStages.DELETE_RESOURCE_GROUP_STATES;
            handleSubStage(context);
            return;
        }

        for (String id : context.storageAccountIds) {
            String storageConnectionString = context.storageConnectionStrings
                    .get(id);
            if (storageConnectionString == null) {
                continue;
            }

            try {
                CloudStorageAccount storageAccount = CloudStorageAccount
                        .parse(storageConnectionString);
                CloudBlobClient blobClient = storageAccount.createCloudBlobClient();

                ResultContinuation nextContainerResults = null;
                do {
                    ResultSegment<CloudBlobContainer> contSegment = blobClient
                            .listContainersSegmented(null,
                                    ContainerListingDetails.NONE,
                                    getQueryResultLimit(), nextContainerResults, null,
                                    null);

                    nextContainerResults = contSegment.getContinuationToken();
                    for (CloudBlobContainer container : contSegment.getResults()) {
                        String uri = container.getUri().toString();
                        context.containerIds.add(uri);
                        context.storageContainers.put(uri, container);
                        ResultContinuation nextBlobResults = null;
                        do {
                            ResultSegment<ListBlobItem> blobsSegment = container
                                    .listBlobsSegmented(
                                            null, false,
                                            EnumSet.noneOf(BlobListingDetails.class),
                                            getQueryResultLimit(), nextBlobResults, null,
                                            null);
                            nextBlobResults = blobsSegment.getContinuationToken();
                            for (ListBlobItem blobItem : blobsSegment.getResults()) {
                                String blobId = blobItem.getUri().toString();
                                context.storageBlobs.put(blobId, blobItem);
                                // populate mapping of blob uri and storage account for all storage
                                // accounts as new disks can be added to already existing blobs
                                StorageAccount blobStorageAccount = context.storageAccountMap.get(id);
                                if (blobStorageAccount != null) {
                                    context.storageAccountBlobUriMap.put(blobId, blobStorageAccount);
                                }
                                context.blobIds.add(blobId);
                            }
                        } while (nextBlobResults != null);
                    }
                } while (nextContainerResults != null);
                logFine(() -> String.format("Processing %d storage containers",
                        context.containerIds.size()));
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
    private void getLocalStorageContainerStates(StorageEnumContext context,
            StorageEnumStages next) {

        if (context.storageContainers.isEmpty()) {
            logFine(() -> "No storage containers available - clean up all resources");
            context.subStage = StorageEnumStages.DELETE_RESOURCE_GROUP_STATES;
            handleSubStage(context);
            return;
        }

        Query.Builder qBuilder = Query.Builder.create()
                .addKindFieldClause(ResourceGroupState.class)
                .addCompositeFieldClause(
                        ResourceGroupState.FIELD_NAME_CUSTOM_PROPERTIES,
                        COMPUTE_HOST_LINK_PROP_NAME,
                        context.parentCompute.documentSelfLink)
                .addCompositeFieldClause(
                        ResourceGroupState.FIELD_NAME_CUSTOM_PROPERTIES,
                        ComputeProperties.RESOURCE_TYPE_KEY,
                        ResourceGroupStateType.AzureStorageContainer.name());

        Query.Builder instanceIdFilterParentQuery = Query.Builder
                .create(Occurance.MUST_OCCUR);

        for (Map.Entry<String, CloudBlobContainer> container : context.storageContainers
                .entrySet()) {
            Query instanceIdFilter = Query.Builder
                    .create(Occurance.SHOULD_OCCUR)
                    .addFieldClause(ResourceGroupState.FIELD_NAME_ID,
                            container.getKey())
                    .build();
            instanceIdFilterParentQuery.addClause(instanceIdFilter);
        }

        qBuilder.addClause(instanceIdFilterParentQuery.build());

        QueryStrategy<ResourceGroupState> queryLocalStates = new QueryByPages<>(
                getHost(),
                qBuilder.build(),
                ResourceGroupState.class,
                context.parentCompute.tenantLinks,
                context.request.endpointLink)
                        .setMaxPageSize(getQueryResultLimit());

        queryLocalStates.queryDocuments(rg -> {
            if (context.resourceGroupStates.containsKey(rg.id)) {
                return;
            }
            context.resourceGroupStates.put(rg.id, rg);
        })
                .whenComplete((ignore, e) -> {
                    if (e != null) {
                        handleError(context, e);
                        return;
                    }
                    logFine(() -> "Finished getting resources for Azure storage containers");
                    context.subStage = next;
                    handleSubStage(context);
                });
    }

    private void updateResourceGroupStates(StorageEnumContext context, StorageEnumStages next) {
        if (context.resourceGroupStates.size() == 0) {
            logFine(() -> "No resource group states available for update");
            context.subStage = next;
            handleSubStage(context);
            return;
        }

        DeferredResult.allOf(context.resourceGroupStates
                .values().stream().map(rg -> {
                    CloudBlobContainer container = context.storageContainers.remove(rg.id);
                    return createResourceGroupStateHelper(context, container, rg);
                }).collect(Collectors.toList()))
                .whenComplete((r, e) -> {
                    logFine(() -> "Finished update of storage containers resources");
                    context.resourceGroupStates.clear();
                    context.subStage = next;
                    handleSubStage(context);
                });
    }

    private DeferredResult<Operation> createResourceGroupStateHelper(
            StorageEnumContext context, CloudBlobContainer container,
            ResourceGroupState oldResourceGroup) {
        // Associate resource group with storage account
        String storageAcctName = getStorageAccountNameFromUri(container.getStorageUri()
                .getPrimaryUri().getHost());
        Query.Builder qBuilder = Query.Builder.create()
                .addKindFieldClause(StorageDescription.class)
                .addFieldClause(StorageDescription.FIELD_NAME_COMPUTE_HOST_LINK,
                        context.parentCompute.documentSelfLink)
                .addFieldClause(StorageDescription.FIELD_NAME_NAME, storageAcctName);

        QueryStrategy<StorageDescription> queryLocalStates = new QueryUtils.QueryTop<StorageDescription>(
                getHost(),
                qBuilder.build(),
                StorageDescription.class,
                context.parentCompute.tenantLinks,
                context.request.endpointLink)
                        .setMaxResultsLimit(getQueryResultLimit());

        return queryLocalStates
                .collectLinks(Collectors.toSet())
                .thenCompose(sdls -> {
                    logFine(() -> String.format("Found %d matching storage descriptions",
                            sdls.size()));
                    // the storage account names are unique so we should only get 1 result back
                    if (!sdls.isEmpty()) {
                        String storageDescSelfLink = sdls.iterator().next();

                        if (sdls.size() > 1) {
                            logWarning(() -> String.format("Found multiple instances of the same"
                                    + " storage description %s", storageAcctName));
                        }
                        ResourceGroupState resourceGroupState = createResourceGroupStateObject(
                                context, container, storageDescSelfLink, oldResourceGroup);
                        if (oldResourceGroup != null) {
                            return updateResourceGroupState(context, resourceGroupState);
                        } else {
                            return createResourceGroupState(context, resourceGroupState);
                        }
                    } else {
                        return DeferredResult.completed((Operation) null);
                    }
                });
    }

    private ResourceGroupState createResourceGroupStateObject(StorageEnumContext context,
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
        if (context.request.endpointLink != null) {
            resourceGroupState.customProperties.put(CUSTOM_PROP_ENDPOINT_LINK,
                    context.request.endpointLink);
        }
        resourceGroupState.customProperties.put(AZURE_STORAGE_TYPE, AZURE_STORAGE_CONTAINERS);
        resourceGroupState.customProperties.put(AZURE_STORAGE_CONTAINER_LEASE_LAST_MODIFIED,
                container.getProperties().getLastModified().toString());
        resourceGroupState.customProperties.put(AZURE_STORAGE_CONTAINER_LEASE_STATE, container
                .getProperties().getLeaseState().toString());
        resourceGroupState.customProperties.put(AZURE_STORAGE_CONTAINER_LEASE_STATUS,
                container.getProperties().getLeaseStatus().toString());
        resourceGroupState.customProperties.put(ComputeProperties.RESOURCE_TYPE_KEY,
                ResourceGroupStateType.AzureStorageContainer.name());
        resourceGroupState.customProperties.put(COMPUTE_HOST_LINK_PROP_NAME,
                context.parentCompute.documentSelfLink);
        resourceGroupState.tenantLinks = context.parentCompute.tenantLinks;

        if (oldResourceGroupState != null) {
            resourceGroupState.documentSelfLink = oldResourceGroupState.documentSelfLink;
        }

        return resourceGroupState;
    }

    private DeferredResult<Operation> updateResourceGroupState(StorageEnumContext context,
            ResourceGroupState rgState) {
        return sendWithDeferredResult(
                Operation.createPatch(context.request.buildUri(rgState.documentSelfLink))
                        .setBody(rgState)
                        .setCompletion((o, e) -> {
                            // TODO: https://jira-hzn.eng.vmware.com/browse/VSYM-2765
                            if (e != null) {
                                logWarning(() -> String.format(
                                        "Failed to update storage containers: %s",
                                        e.getMessage()));
                            }
                        }));
    }

    private DeferredResult<Operation> createResourceGroupState(StorageEnumContext context,
            ResourceGroupState rgState) {
        return sendWithDeferredResult(Operation
                .createPost(context.request.buildUri(ResourceGroupService.FACTORY_LINK))
                .setBody(rgState)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning(() -> String.format("Failed to create storage containers: %s",
                                e.getMessage()));
                    }
                }));
    }

    /*
     * Create all resource group states Resource group states mapping to Azure storage containers
     * have an additional custom property {"storageType" : "Microsoft.Storage/container"}
     */
    private void createResourceGroupStates(StorageEnumContext context, StorageEnumStages next) {
        if (context.storageContainers.size() == 0) {
            logFine(() -> "No storage container found for creation");
            context.subStage = next;
            handleSubStage(context);
            return;
        }

        logFine(() -> String.format("%d storage container to be created",
                context.storageContainers.size()));

        DeferredResult.allOf(context.storageContainers
                .values().stream().map(cbc -> createResourceGroupStateHelper(context, cbc, null))
                .collect(Collectors.toList()))
                .whenComplete((r, e) -> {
                    logFine(() -> "Finished create of storage containers resources");
                    context.subStage = next;
                    handleSubStage(context);
                });
    }

    /*
     * Delete local storage containers that no longer exist in Azure
     *
     * The logic works by recording a timestamp when enumeration starts. This timestamp is used to
     * lookup resources which haven't been touched as part of current enumeration cycle. The other
     * data point this method uses is the storage accounts discovered as part of get storage
     * accounts call.
     *
     * A delete on a resource group state is invoked only if it meets two criteria: - Timestamp
     * older than current enumeration cycle. - Storage container is not present on Azure.
     */
    private void deleteResourceGroupStates(StorageEnumContext context, StorageEnumStages next) {

        Query.Builder qBuilder = Query.Builder.create()
                .addKindFieldClause(ResourceGroupState.class)
                .addCompositeFieldClause(
                        ResourceGroupState.FIELD_NAME_CUSTOM_PROPERTIES,
                        COMPUTE_HOST_LINK_PROP_NAME,
                        context.parentCompute.documentSelfLink)
                .addCompositeFieldClause(
                        ResourceGroupState.FIELD_NAME_CUSTOM_PROPERTIES,
                        ComputeProperties.RESOURCE_TYPE_KEY,
                        ResourceGroupStateType.AzureStorageContainer.name())
                .addRangeClause(ResourceGroupState.FIELD_NAME_UPDATE_TIME_MICROS,
                        QueryTask.NumericRange
                                .createLessThanRange(context.enumerationStartTimeInMicros));

        QueryStrategy<ResourceGroupState> queryLocalStates = new QueryByPages<>(
                getHost(),
                qBuilder.build(),
                ResourceGroupState.class,
                context.parentCompute.tenantLinks,
                context.request.endpointLink)
                        .setMaxPageSize(getQueryResultLimit());

        List<DeferredResult<Operation>> ops = new ArrayList<>();
        queryLocalStates.queryDocuments(rg -> {
            // if the resource group is present in Azure and has older timestamp in local
            // repository it means nothing has changed about it.
            if (context.containerIds.contains(rg.id)) {
                return;
            }

            Operation dOp = Operation.createDelete(context.request.buildUri(rg.documentSelfLink));

            logFine(() -> String.format("Deleting storage containers %s", rg.documentSelfLink));

            DeferredResult<Operation> dr = sendWithDeferredResult(dOp)
                    .whenComplete((o, e) -> {

                        final String message = "Delete storage containers stale %s state";
                        if (e != null) {
                            logWarning(message + ": ERROR - %s",
                                    rg.documentSelfLink, Utils.toString(e));
                        } else {
                            logFine(message + ": SUCCESS",
                                    rg.documentSelfLink);
                        }
                    });

            ops.add(dr);
        })
                .thenCompose(r -> DeferredResult.allOf(ops))
                .whenComplete((r, e) -> {
                    logFine(() -> "Finished deletion of storage containers for Azure");
                    context.subStage = next;
                    handleSubStage(context);
                });
    }

    private void getBlobsAsync(StorageEnumContext context, StorageEnumStages next) {
        // If no storage accounts exist in Azure, no disks exist either
        // Move on to disk deletion stage
        if (context.storageAccountIds.size() == 0) {
            logFine(() -> "No storage description available - clean up all local disks");
            context.subStage = StorageEnumStages.DELETE_DISK_STATES;
            handleSubStage(context);
            return;
        } else {
            context.subStage = next;
            handleSubStage(context);
        }
    }

    private void getLocalDiskStates(StorageEnumContext context, StorageEnumStages next) {
        if (context.storageBlobs.isEmpty()) {
            context.subStage = StorageEnumStages.CREATE_DISK_STATES;
            handleSubStage(context);
            return;
        }
        Query.Builder qBuilder = Query.Builder.create()
                .addKindFieldClause(DiskState.class)
                .addFieldClause(DiskState.FIELD_NAME_COMPUTE_HOST_LINK,
                        context.parentCompute.documentSelfLink);

        if (context.storageBlobs.size() <= MAX_RESOURCES_TO_QUERY) {
            // do not load resources from enumExternalResourcesIds
            qBuilder.addInClause(
                    ResourceState.FIELD_NAME_ID,
                    context.storageBlobs.values().stream()
                            .map(sb -> QuerySpecification.toMatchValue(sb.getUri()))
                            .collect(Collectors.toSet()));
        }

        String blobProperty = QuerySpecification
                .buildCompositeFieldName(DiskState.FIELD_NAME_CUSTOM_PROPERTIES,
                        AZURE_STORAGE_TYPE);

        qBuilder.addInClause(blobProperty, Arrays.asList(AZURE_STORAGE_BLOBS,AZURE_STORAGE_DISKS));

        QueryStrategy<DiskState> queryLocalStates = new QueryByPages<>(
                getHost(),
                qBuilder.build(),
                DiskState.class,
                context.parentCompute.tenantLinks,
                context.request.endpointLink)
                        .setMaxPageSize(getQueryResultLimit());

        // Delete stale resources.
        queryLocalStates.queryDocuments(ds -> {
            String diskUri = ds.id;
            if (context.diskStates.containsKey(diskUri)) {
                return;
            }

            context.diskStates.put(diskUri, ds);
        })
                .whenComplete((ignore, e) -> {
                    if (e != null) {
                        handleError(context, e);
                        return;
                    }
                    logFine(() -> "Finished getting local disk states for Azure blobs");
                    context.subStage = next;
                    handleSubStage(context);
                });
    }

    /**
     * Updates matching disk states for given blobs
     */
    private void updateDiskStates(StorageEnumContext context, StorageEnumStages next) {
        if (context.diskStates.size() == 0) {
            logFine(() -> "No disk states found for update");
            context.subStage = next;
            handleSubStage(context);
            return;
        }

        DeferredResult.allOf(
                context.diskStates.entrySet().stream().map(dse -> {
                    ListBlobItem blob = context.storageBlobs.remove(dse.getKey());
                    if (blob == null) {
                        logWarning("No blob found for local state: %s", dse.getKey());
                        return DeferredResult.completed((Operation) null);
                    }
                    return createDiskStateHelper(context, blob, dse.getValue());
                }).collect(Collectors.toList()))
                .whenComplete((ops, e) -> {
                    context.subStage = next;
                    handleSubStage(context);
                });
    }

    private DeferredResult<Operation> createDiskStateHelper(StorageEnumContext context,
            ListBlobItem blob,
            DiskState oldDiskState) {
        try {
            // Associate blob with the resource group state (container) it belongs to
            String containerId = blob.getContainer().getUri().toString();

            Query.Builder qBuilder = Query.Builder.create()
                    .addKindFieldClause(ResourceGroupState.class)
                    .addCompositeFieldClause(
                            ResourceGroupState.FIELD_NAME_CUSTOM_PROPERTIES,
                            COMPUTE_HOST_LINK_PROP_NAME,
                            context.parentCompute.documentSelfLink)
                    .addFieldClause(ResourceGroupState.FIELD_NAME_ID, containerId);

            QueryStrategy<ResourceGroupState> queryLocalStates = new QueryUtils.QueryTop<ResourceGroupState>(
                    getHost(),
                    qBuilder.build(),
                    ResourceGroupState.class,
                    context.parentCompute.tenantLinks,
                    context.request.endpointLink)
                            .setMaxResultsLimit(getQueryResultLimit());

            return queryLocalStates.collectLinks(Collectors.toSet())
                    .thenCompose(rgLinks -> {
                        logFine(() -> String.format("Found %d matching resource groups",
                                rgLinks.size()));
                        String containerLink = null;
                        if (rgLinks.size() > 0) {
                            containerLink = rgLinks.iterator().next();
                            if (rgLinks.size() > 1) {
                                logWarning(() -> String.format("Found multiple instances of the same"
                                        + " resource group %s", containerId));
                            }

                            if (oldDiskState != null) {
                                if (rgLinks.contains(oldDiskState.storageDescriptionLink)) {
                                    containerLink = oldDiskState.storageDescriptionLink;
                                }
                            }
                        }
                        DiskState diskState = createDiskStateObject(context, blob,
                                containerLink, oldDiskState);
                        if (oldDiskState != null) {
                            return updateDiskState(context, diskState);
                        } else {
                            return createDiskState(context, diskState);
                        }
                    });

        } catch (URISyntaxException e) {
            logWarning(() -> String.format("Could not set storage description link for blob: %s",
                    blob.getUri().toString()));
        } catch (StorageException e) {
            logWarning(
                    () -> String.format("Could not set storage description link for disk blob: %s",
                            blob.getUri().toString()));
        }
        return DeferredResult.completed(null);
    }

    private DeferredResult<Operation> updateDiskState(StorageEnumContext context,
            DiskState diskState) {
        return sendWithDeferredResult(
                Operation.createPatch(context.request.buildUri(diskState.documentSelfLink))
                        .setBody(diskState)
                        .setCompletion((completedOp, failure) -> {

                            if (failure != null) {
                                // TODO: https://jira-hzn.eng.vmware.com/browse/VSYM-2765
                                logWarning(() -> String.format("Failed update disk %s",
                                        failure.getMessage()));
                            }
                        }));
    }

    /*
     * Create all disk states Disk states mapping to Azure storage blobs have an additional custom
     * property "type" In Azure a storage blobs can be either a vhd or a any other kind of blob. To
     * be able to distinguish which blobs are actually disks we populate the type custom property as
     * either {"type" : "Microsoft.Storage/disks"} or {"type" : "Microsoft.Storage/blob"}
     */
    private void createDiskStates(StorageEnumContext context, StorageEnumStages next) {
        if (context.storageBlobs.size() == 0) {
            logFine(() -> "No disk states found for creation");
            context.subStage = next;
            handleSubStage(context);
            return;
        }

        logFine(() -> String.format("%d disk states to be created", context.storageBlobs.size()));

        DeferredResult.allOf(context.storageBlobs
                .values().stream().map(sb -> createDiskStateHelper(context, sb, null))
                .collect(Collectors.toList()))
                .whenComplete((r, e) -> {
                    if (e != null) {
                        // TODO: https://jira-hzn.eng.vmware.com/browse/VSYM-2765
                        logWarning(() -> String.format("Failed create disks %s", e.getMessage()));
                    }
                    logFine(() -> "Finished create of disk states");
                    context.subStage = next;
                    handleSubStage(context);
                });
    }

    private DiskState createDiskStateObject(StorageEnumContext context, ListBlobItem blob,
            String containerLink, DiskState oldDiskState) {

        DiskState diskState = new DiskState();
        diskState.name = UriUtils.getLastPathSegment(blob.getUri());
        if (containerLink != null) {
            diskState.storageDescriptionLink = containerLink;
        }
        diskState.resourcePoolLink = context.request.resourcePoolLink;
        diskState.computeHostLink = context.parentCompute.documentSelfLink;
        diskState.endpointLink = context.request.endpointLink;
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
            diskState.regionId = oldDiskState.regionId;
        } else {
            StorageAccount storageAccount = context.storageAccountBlobUriMap.get(blob.getUri().toString());
            diskState.id = blob.getUri().toString();
            diskState.documentSelfLink = UUID.randomUUID().toString();
            if (storageAccount != null) {
                diskState.regionId = storageAccount.location;
            }
        }
        return diskState;
    }

    private DeferredResult<Operation> createDiskState(StorageEnumContext context,
            DiskState diskState) {
        return sendWithDeferredResult(Operation
                .createPost(context.request.buildUri(DiskService.FACTORY_LINK))
                .setBody(diskState)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning(() -> String.format("Failed to create disk state: %s",
                                e.getMessage()));
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
     * A delete on a disk state is invoked only if it meets two criteria: - Timestamp older than
     * current enumeration cycle. - blob is not present on Azure.
     */
    private void deleteDiskStates(StorageEnumContext context, StorageEnumStages next) {
        Query.Builder qBuilder = Query.Builder.create()
                .addKindFieldClause(DiskState.class)
                .addFieldClause(DiskState.FIELD_NAME_COMPUTE_HOST_LINK,
                        context.parentCompute.documentSelfLink)
                .addRangeClause(DiskState.FIELD_NAME_UPDATE_TIME_MICROS,
                        QueryTask.NumericRange
                                .createLessThanRange(context.enumerationStartTimeInMicros));

        String blobProperty = QuerySpecification
                .buildCompositeFieldName(DiskState.FIELD_NAME_CUSTOM_PROPERTIES,
                        AZURE_STORAGE_TYPE);
        Query.Builder typeFilterQuery = Query.Builder
                .create(Occurance.MUST_OCCUR);

        Query blobFilter = Query.Builder.create(Occurance.SHOULD_OCCUR)
                .addFieldClause(blobProperty, AZURE_STORAGE_BLOBS)
                .build();

        QueryTask.Query diskFilter = QueryTask.Query.Builder
                .create(QueryTask.Query.Occurance.SHOULD_OCCUR)
                .addFieldClause(blobProperty, AZURE_STORAGE_DISKS)
                .build();
        typeFilterQuery.addClause(blobFilter);
        typeFilterQuery.addClause(diskFilter);

        qBuilder.addClause(typeFilterQuery.build());

        QueryStrategy<DiskState> queryLocalStates = new QueryByPages<>(
                getHost(),
                qBuilder.build(),
                DiskState.class,
                context.parentCompute.tenantLinks,
                context.request.endpointLink)
                        .setMaxPageSize(getQueryResultLimit());

        List<DeferredResult<Operation>> ops = new ArrayList<>();

        queryLocalStates.queryDocuments(ds -> {
            if (context.blobIds.contains(ds.id)) {
                return;
            }
            ops.add(deleteIfNotAttachedToCompute(context, ds));
        })
                .thenCompose(r -> DeferredResult.allOf(ops))
                .whenComplete((r, e) -> {
                    logFine(() -> "Finished deletion of disk states for Azure");
                    context.subStage = next;
                    handleSubStage(context);
                });
    }

    private DeferredResult<Operation> deleteIfNotAttachedToCompute(StorageEnumContext context,
            DiskState diskState) {
        Query query = Query.Builder.create()
                .addKindFieldClause(ComputeService.ComputeState.class)
                .addCollectionItemClause(ComputeService.ComputeState.FIELD_NAME_DISK_LINKS,
                        diskState.documentSelfLink)
                .build();

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.COUNT)
                .setQuery(query)
                .build();

        if (queryTask.documentExpirationTimeMicros == 0) {
            queryTask.documentExpirationTimeMicros = Utils.getNowMicrosUtc()
                    + QueryUtils.TEN_MINUTES_IN_MICROS;
        }

        return sendWithDeferredResult(
                Operation.createPost(
                        context.request.buildUri(ServiceUriPaths.CORE_LOCAL_QUERY_TASKS))
                        .setBody(queryTask),
                QueryTask.class)
                        .thenCompose(result -> {
                            if (result.results != null && result.results.documentCount != 0) {
                                logFine(() -> String.format(
                                        "Won't delete disk state %s, as it is attached to machine",
                                        diskState.documentSelfLink));
                                return DeferredResult.completed(new Operation());
                            }
                            logFine(() -> String.format("Deleting disk state %s",
                                    diskState.documentSelfLink));
                            return sendWithDeferredResult(
                                    Operation.createDelete(
                                            context.request.buildUri(diskState.documentSelfLink)))
                                                    .whenComplete((o, e) -> {

                                                        final String message = "Delete disk state stale %s state";
                                                        if (e != null) {
                                                            logWarning(message + ": ERROR - %s",
                                                                    diskState.documentSelfLink,
                                                                    Utils.toString(e));
                                                        } else {
                                                            logFine(message + ": SUCCESS",
                                                                    diskState.documentSelfLink);
                                                        }
                                                    });
                        });
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
    private DeferredResult<AuthCredentialsServiceState> loadStorageAuth(StorageEnumContext context,
            StorageDescription storageDesc) {
        return sendWithDeferredResult(
                Operation.createGet(context.request.buildUri(storageDesc.authCredentialsLink))
                        .setCompletion(
                                (op, ex) -> {
                                    if (ex != null) {
                                        logWarning(() -> String
                                                .format("Failed to get storage description"
                                                        + " credentials: %s", ex.getMessage()));
                                        return;
                                    }
                                }),
                AuthCredentialsServiceState.class)
                        .thenApply(auth -> {
                            String connectionString = String.format(STORAGE_CONNECTION_STRING,
                                    storageDesc.name,
                                    auth.customProperties.get(AZURE_STORAGE_ACCOUNT_KEY1));

                            context.storageConnectionStrings.put(storageDesc.id, connectionString);
                            return auth;
                        });

    }

    /*
     * Extract storage account name from storage accounts URI The pattern of the storageAccount link
     * is http://<accountName>.blob.core.windows.net
     **/
    private String getStorageAccountNameFromUri(String uri) {
        int p = uri.indexOf(".");
        return uri.substring(0, p);
    }

}
