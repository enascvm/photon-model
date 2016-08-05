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

import static com.vmware.photon.controller.model.ComputeProperties.CUSTOM_DISPLAY_NAME;
import static com.vmware.photon.controller.model.ComputeProperties.CUSTOM_OS_TYPE;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AUTH_HEADER_BEARER_PREFIX;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_DIAGNOSTIC_STORAGE_ACCOUNT_NAME;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_OSDISK_CACHING;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_RESOURCE_GROUP_NAME;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNT_KEY1;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNT_KEY2;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNT_NAME;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_VM_SIZE;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.LINUX_OPERATING_SYSTEM;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.LIST_VM_URI;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.QUERY_PARAM_API_VERSION;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.VM_REST_API_VERSION;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.WINDOWS_OPERATING_SYSTEM;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.awaitTermination;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.cleanUpHttpClient;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.getAzureConfig;
import static com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ENVIRONMENT_NAME_AZURE;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.compute.ComputeManagementClient;
import com.microsoft.azure.management.compute.ComputeManagementClientImpl;
import com.microsoft.azure.management.compute.models.ImageReference;
import com.microsoft.azure.management.compute.models.InstanceViewStatus;
import com.microsoft.azure.management.compute.models.NetworkInterfaceReference;
import com.microsoft.azure.management.network.NetworkManagementClient;
import com.microsoft.azure.management.network.NetworkManagementClientImpl;
import com.microsoft.azure.management.network.models.NetworkInterface;
import com.microsoft.azure.management.network.models.PublicIPAddress;
import com.microsoft.azure.management.storage.StorageManagementClient;
import com.microsoft.azure.management.storage.StorageManagementClientImpl;
import com.microsoft.azure.management.storage.models.StorageAccountKeys;
import com.microsoft.rest.ServiceResponse;

import okhttp3.OkHttpClient;

import retrofit2.Retrofit;

import com.vmware.photon.controller.model.ComputeProperties.OSType;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.azure.AzureAsyncCallback;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.model.vm.VirtualMachine;
import com.vmware.photon.controller.model.adapters.azure.model.vm.VirtualMachineListResult;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.enums.EnumerationStages;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.DiskService.DiskType;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.StorageDescriptionService;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.NumericRange;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Enumeration adapter for data collection of VMs on Azure.
 */
public class AzureEnumerationAdapterService extends StatelessService {
    public static final String SELF_LINK = AzureUriPaths.AZURE_ENUMERATION_ADAPTER;
    private static final Pattern STORAGE_ACCOUNT_NAME_PATTERN = Pattern.compile("https?://([^.]*)");
    private static final Pattern RESOURCE_GROUP_NAME_PATTERN = Pattern.compile(".*/resourcegroups/([^/]*)");

    private static final String PROPERTY_NAME_ENUM_QUERY_RESULT_LIMIT =
            UriPaths.PROPERTY_PREFIX + "AzureEnumerationAdapterService.QUERY_RESULT_LIMIT";
    private static final int QUERY_RESULT_LIMIT = Integer
            .getInteger(PROPERTY_NAME_ENUM_QUERY_RESULT_LIMIT, 50);

    public static final List<String> AZURE_VM_TERMINATION_STATES = Arrays.asList("Deleting", "Deleted");

    private static final String EXPAND_INSTANCE_VIEW_PARAM = "instanceView";

    /**
     * Substages to handle Azure VM data collection.
     */
    private enum EnumerationSubStages {
        LISTVMS, QUERY, UPDATE, CREATE, PATCH_ADDITIONAL_FIELDS, DELETE, FINISHED
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
     * The enumeration service context that holds all the information needed to determine the list
     * of instances that need to be represented in the system.
     */
    private static class EnumerationContext {
        ComputeEnumerateResourceRequest enumRequest;
        ComputeDescription computeHostDesc;
        EnumerationStages stage;
        Throwable error;
        AuthCredentialsServiceState parentAuth;
        long enumerationStartTimeInMicros;
        String deletionNextPageLink;
        String enumNextPageLink;

        // Substage specific fields
        EnumerationSubStages subStage;
        Map<String, VirtualMachine> virtualMachines = new ConcurrentHashMap<>();
        Map<String, ComputeState> computeStates = new ConcurrentHashMap<>();
        // Compute States for patching additional fields.
        Map<String, ComputeState> computeStatesForPatching = new ConcurrentHashMap<>();
        List<String> vmIds = new ArrayList<>();

        // Azure specific fields
        ApplicationTokenCredentials credentials;
        public OkHttpClient.Builder clientBuilder;
        public OkHttpClient httpClient;

        // Azure clients
        StorageManagementClient storageClient;
        NetworkManagementClient networkClient;
        ComputeManagementClient computeClient;

        EnumerationContext(ComputeEnumerateResourceRequest request) {
            this.enumRequest = request;
            this.stage = EnumerationStages.HOSTDESC;
        }
    }

    private Set<String> ongoingEnumerations = new ConcurrentSkipListSet<>();

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        op.complete();
        EnumerationContext ctx = new EnumerationContext(
                op.getBody(ComputeEnumerateResourceRequest.class));
        AdapterUtils.validateEnumRequest(ctx.enumRequest);
        if (ctx.enumRequest.isMockRequest) {
            // patch status to parent task
            AdapterUtils.sendPatchToEnumerationTask(this, ctx.enumRequest.taskReference);
            return;
        }
        handleEnumerationRequest(ctx);
    }

    private void handleEnumerationRequest(EnumerationContext ctx) {
        switch (ctx.stage) {
        case HOSTDESC:
            getHostComputeDescription(ctx, EnumerationStages.PARENTAUTH);
            break;
        case PARENTAUTH:
            getParentAuth(ctx, EnumerationStages.CLIENT);
            break;
        case CLIENT:
            if (ctx.credentials == null) {
                try {
                    ctx.credentials = getAzureConfig(ctx.parentAuth);
                } catch (Throwable e) {
                    logSevere(e);
                    ctx.error = e;
                    ctx.stage = EnumerationStages.ERROR;
                    handleEnumerationRequest(ctx);
                    return;
                }
            }
            if (ctx.httpClient == null) {
                try {
                    // Creating a shared singleton Http client instance
                    // Reference https://square.github.io/okhttp/3.x/okhttp/okhttp3/OkHttpClient.html
                    // TODO: https://github.com/Azure/azure-sdk-for-java/issues/1000
                    ctx.httpClient = new OkHttpClient();
                    ctx.clientBuilder = ctx.httpClient.newBuilder();
                } catch (Exception e) {
                    handleError(ctx, e);
                    return;
                }
            }
            ctx.stage = EnumerationStages.ENUMERATE;
            handleEnumerationRequest(ctx);
            break;
        case ENUMERATE:
            String enumKey = getEnumKey(ctx);
            switch (ctx.enumRequest.enumerationAction) {
            case START:
                if (this.ongoingEnumerations.contains(enumKey)) {
                    logInfo("Enumeration service has already been started for %s", enumKey);
                    return;
                }
                this.ongoingEnumerations.add(enumKey);
                logInfo("Launching enumeration service for %s", enumKey);
                ctx.enumerationStartTimeInMicros = Utils.getNowMicrosUtc();
                ctx.enumRequest.enumerationAction = EnumerationAction.REFRESH;
                handleEnumerationRequest(ctx);
                break;
            case REFRESH:
                ctx.subStage = EnumerationSubStages.LISTVMS;
                handleSubStage(ctx);
                break;
            case STOP:
                if (this.ongoingEnumerations.contains(enumKey)) {
                    logInfo("Enumeration service will be stopped for %s", enumKey);
                    this.ongoingEnumerations.remove(enumKey);
                } else {
                    logInfo("Enumeration service is not running or has already been stopped for %s",
                            enumKey);
                }
                ctx.stage = EnumerationStages.FINISHED;
                handleEnumerationRequest(ctx);
                break;
            default:
                logSevere("Unknown enumeration action %s", ctx.enumRequest.enumerationAction);
                ctx.stage = EnumerationStages.ERROR;
                handleEnumerationRequest(ctx);
                break;
            }
            break;
        case FINISHED:
            cleanUpHttpClient(this, ctx.httpClient);
            logInfo("Enumeration finished for %s", getEnumKey(ctx));
            this.ongoingEnumerations.remove(getEnumKey(ctx));
            AdapterUtils.sendPatchToEnumerationTask(this, ctx.enumRequest.taskReference);
            break;
        case ERROR:
            cleanUpHttpClient(this, ctx.httpClient);
            logWarning("Enumeration error for %s", getEnumKey(ctx));
            AdapterUtils.sendFailurePatchToEnumerationTask(this,
                    ctx.enumRequest.taskReference, ctx.error);
            break;
        default:
            cleanUpHttpClient(this, ctx.httpClient);
            String msg = String.format("Unknown Azure enumeration stage %s ", ctx.stage.toString());
            logSevere(msg);
            ctx.error = new IllegalStateException(msg);
            AdapterUtils.sendFailurePatchToEnumerationTask(this,
                    ctx.enumRequest.taskReference, ctx.error);
        }
    }

    /**
     * Handle enumeration substages for VM data collection.
     */
    private void handleSubStage(EnumerationContext ctx) {
        if (!this.ongoingEnumerations.contains(getEnumKey(ctx))) {
            ctx.stage = EnumerationStages.FINISHED;
            handleEnumerationRequest(ctx);
            return;
        }

        switch (ctx.subStage) {
        case LISTVMS:
            enumerate(ctx);
            break;
        case QUERY:
            queryForComputeStates(ctx, ctx.virtualMachines);
            break;
        case UPDATE:
            update(ctx);
            break;
        case CREATE:
            create(ctx);
            break;
        case PATCH_ADDITIONAL_FIELDS:
            patchAdditionalFields(ctx);
            break;
        case DELETE:
            delete(ctx);
            break;
        case FINISHED:
            ctx.stage = EnumerationStages.FINISHED;
            handleEnumerationRequest(ctx);
            break;
        default:
            String msg = String
                    .format("Unknown Azure enumeration sub-stage %s ", ctx.subStage.toString());
            ctx.error = new IllegalStateException(msg);
            ctx.stage = EnumerationStages.ERROR;
            handleEnumerationRequest(ctx);
            break;
        }
    }

    /**
     * Deletes undiscovered resources.
     *
     * The logic works by recording a timestamp when enumeration starts. This timestamp is used to
     * lookup resources which haven't been touched as part of current enumeration cycle. The other
     * data point this method uses is the virtual machines discovered as part of list vm call.
     *
     * Finally, delete on a resource is invoked only if it meets two criteria:
     * - Timestamp older than current enumeration cycle.
     * - VM not present on Azure.
     *
     * The method paginates through list of resources for deletion.
     */
    private void delete(EnumerationContext ctx) {
        CompletionHandler completionHandler = (o, e) -> {
            if (e != null) {
                handleError(ctx, e);
                return;
            }
            QueryTask queryTask = o.getBody(QueryTask.class);

            if (queryTask.results.nextPageLink == null) {
                logInfo("No compute states match for deletion");
                ctx.subStage = EnumerationSubStages.FINISHED;
                handleSubStage(ctx);
                return;
            }

            ctx.deletionNextPageLink = queryTask.results.nextPageLink;
            deleteHelper(ctx);
        };

        Query query = Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK,
                        ctx.enumRequest.resourceLink())
                .addRangeClause(ComputeState.FIELD_NAME_UPDATE_TIME_MICROS,
                        NumericRange.createLessThanRange(ctx.enumerationStartTimeInMicros))
                .build();

        QueryTask q = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .setQuery(query)
                .setResultLimit(QUERY_RESULT_LIMIT)
                .build();
        q.tenantLinks = ctx.computeHostDesc.tenantLinks;

        logFine("Querying compute resources for deletion");
        sendRequest(Operation
                .createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(q)
                .setCompletion(completionHandler));
    }

    /**
     * Helper method to paginate through resources to be deleted.
     */
    private void deleteHelper(EnumerationContext ctx) {
        if (ctx.deletionNextPageLink == null) {
            logInfo("Finished deletion of compute states for Azure");
            ctx.subStage = EnumerationSubStages.FINISHED;
            handleSubStage(ctx);
            return;
        }

        CompletionHandler completionHandler = (o, e) -> {
            if (e != null) {
                handleError(ctx, e);
                return;
            }
            QueryTask queryTask = o.getBody(QueryTask.class);

            ctx.deletionNextPageLink = queryTask.results.nextPageLink;

            List<Operation> operations = new ArrayList<>();
            for (Object s : queryTask.results.documents.values()) {
                ComputeState computeState = Utils
                        .fromJson(s, ComputeState.class);
                String vmId = computeState.id;

                // Since we only update disks during update, some compute states might be
                // present in Azure but have older timestamp in local repository.
                if (ctx.vmIds.contains(vmId)) {
                    continue;
                }

                operations.add(Operation.createDelete(this, computeState.documentSelfLink));
                logFine("Deleting compute state %s", computeState.documentSelfLink);

                if (computeState.diskLinks != null && !computeState.diskLinks.isEmpty()) {
                    operations.add(Operation
                            .createDelete(this, computeState.diskLinks.get(0)));
                    logFine("Deleting disk state %s", computeState.diskLinks.get(0));
                }
            }

            if (operations.size() == 0) {
                logFine("No compute/disk states deleted");
                deleteHelper(ctx);
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

                        deleteHelper(ctx);
                    })
                    .sendWith(this);
        };
        logFine("Querying page [%s] for resources to be deleted", ctx.deletionNextPageLink);
        sendRequest(Operation.createGet(this, ctx.deletionNextPageLink)
                .setCompletion(completionHandler));
    }

    /**
     * Method to retrieve the parent compute host on which the enumeration task will be performed.
     */
    private void getHostComputeDescription(EnumerationContext ctx, EnumerationStages next) {
        Consumer<Operation> onSuccess = (op) -> {
            ComputeStateWithDescription csd = op.getBody(ComputeStateWithDescription.class);
            ctx.computeHostDesc = csd.description;
            ctx.stage = next;
            handleEnumerationRequest(ctx);
        };

        URI computeUri = UriUtils
                .extendUriWithQuery(
                        UriUtils.buildUri(this.getHost(), ctx.enumRequest.resourceLink()),
                        UriUtils.URI_PARAM_ODATA_EXPAND,
                        Boolean.TRUE.toString());
        AdapterUtils.getServiceState(this, computeUri, onSuccess, getFailureConsumer(ctx));
    }

    /**
     * Method to arrive at the credentials needed to call the Azure API for enumerating the instances.
     */
    private void getParentAuth(EnumerationContext ctx, EnumerationStages next) {
        URI authUri = UriUtils.buildUri(this.getHost(),
                ctx.computeHostDesc.authCredentialsLink);
        Consumer<Operation> onSuccess = (op) -> {
            ctx.parentAuth = op.getBody(AuthCredentialsServiceState.class);
            ctx.stage = next;
            handleEnumerationRequest(ctx);
        };
        AdapterUtils.getServiceState(this, authUri, onSuccess, getFailureConsumer(ctx));
    }

    private Consumer<Throwable> getFailureConsumer(EnumerationContext ctx) {
        return (t) -> {
            ctx.stage = EnumerationStages.ERROR;
            ctx.error = t;
            handleEnumerationRequest(ctx);
        };
    }

    /**
     * Enumerate VMs from Azure.
     */
    private void enumerate(EnumerationContext ctx) {
        logInfo("Enumerating VMs from Azure");
        String uriStr = ctx.enumNextPageLink;
        URI uri;

        if (uriStr == null) {
            uriStr = AdapterUriUtil.expandUriPathTemplate(LIST_VM_URI, ctx.parentAuth.userLink);
            uri = UriUtils.extendUriWithQuery(UriUtils.buildUri(uriStr),
                    QUERY_PARAM_API_VERSION, VM_REST_API_VERSION);
        } else {
            uri = UriUtils.buildUri(uriStr);
        }

        Operation operation = Operation.createGet(uri);
        operation.addRequestHeader(Operation.ACCEPT_HEADER, Operation.MEDIA_TYPE_APPLICATION_JSON);
        operation.addRequestHeader(Operation.CONTENT_TYPE_HEADER, Operation.MEDIA_TYPE_APPLICATION_JSON);
        try {
            operation.addRequestHeader(Operation.AUTHORIZATION_HEADER,
                    AUTH_HEADER_BEARER_PREFIX + ctx.credentials.getToken());
        } catch (Exception ex) {
            this.handleError(ctx, ex);
            return;
        }

        operation.setCompletion((op, er) -> {
            if (er != null) {
                handleError(ctx, er);
                return;
            }

            VirtualMachineListResult results = op.getBody(VirtualMachineListResult.class);

            List<VirtualMachine> virtualMachines = results.value;

            // If there are no VMs in Azure we directly skip over to deletion phase.
            if (virtualMachines == null || virtualMachines.size() == 0) {
                ctx.subStage = EnumerationSubStages.DELETE;
                handleSubStage(ctx);
                return;
            }

            ctx.enumNextPageLink = results.nextLink;

            logFine("Retrieved %d VMs from Azure", virtualMachines.size());
            logFine("Next page link %s", ctx.enumNextPageLink);

            for (VirtualMachine virtualMachine : virtualMachines) {
                // We don't want to process VMs that are being terminated.
                if (AZURE_VM_TERMINATION_STATES.contains(virtualMachine.properties.provisioningState)) {
                    logFine("Not processing %d", virtualMachine.id);
                    continue;
                }

                // Azure for some case changes the case of the vm id.
                String vmId = virtualMachine.id.toLowerCase();
                ctx.virtualMachines.put(vmId, virtualMachine);
                ctx.vmIds.add(vmId);
            }

            logFine("Processing %d VMs", ctx.vmIds.size());

            ctx.subStage = EnumerationSubStages.QUERY;
            handleSubStage(ctx);

        });
        sendRequest(operation);
    }

    /**
     * Query all compute states for the cluster filtered by the received set of instance Ids.
     */
    private void queryForComputeStates(EnumerationContext ctx, Map<String, VirtualMachine> vms) {
        QueryTask q = new QueryTask();
        q.setDirect(true);
        q.querySpec = new QueryTask.QuerySpecification();
        q.querySpec.options.add(QueryOption.EXPAND_CONTENT);
        q.querySpec.query = Query.Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK,
                        ctx.enumRequest.resourceLink())
                .build();

        Query.Builder instanceIdFilterParentQuery = Query.Builder.create(Occurance.MUST_OCCUR);

        for (String instanceId : vms.keySet()) {
            QueryTask.Query instanceIdFilter = Query.Builder.create(Occurance.SHOULD_OCCUR)
                    .addFieldClause(ComputeState.FIELD_NAME_ID, instanceId)
                    .build();
            instanceIdFilterParentQuery.addClause(instanceIdFilter);
        }
        q.querySpec.query.addBooleanClause(instanceIdFilterParentQuery.build());
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

                    logFine("Found %d matching compute states for Azure VMs",
                            queryTask.results.documentCount);

                    // If there are no matches, there is nothing to update.
                    if (queryTask.results.documentCount == 0) {
                        ctx.subStage = EnumerationSubStages.CREATE;
                        handleSubStage(ctx);
                        return;
                    }

                    for (Object s : queryTask.results.documents.values()) {
                        ComputeState computeState = Utils
                                .fromJson(s, ComputeState.class);
                        String instanceId = computeState.id;
                        ctx.computeStates.put(instanceId, computeState);
                    }

                    ctx.subStage = EnumerationSubStages.UPDATE;
                    handleSubStage(ctx);
                }));
    }

    /**
     * Updates matching compute states for given VMs.
     */
    private void update(EnumerationContext ctx) {
        if (ctx.computeStates.size() == 0) {
            logInfo("No compute states available for update");
            ctx.subStage = EnumerationSubStages.CREATE;
            handleSubStage(ctx);
            return;
        }

        Iterator<Entry<String, ComputeState>> iterator = ctx.computeStates.entrySet()
                .iterator();
        AtomicInteger numOfUpdates = new AtomicInteger(ctx.computeStates.size());
        while (iterator.hasNext()) {
            Entry<String, ComputeState> csEntry = iterator.next();
            ComputeState computeState = csEntry.getValue();
            ctx.computeStatesForPatching.put(csEntry.getKey(), computeState);
            VirtualMachine virtualMachine = ctx.virtualMachines.get(csEntry.getKey());
            iterator.remove();
            updateHelper(ctx, computeState, virtualMachine, numOfUpdates);
        }
    }

    private void updateHelper(EnumerationContext ctx, ComputeState computeState,
            VirtualMachine vm, AtomicInteger numOfUpdates) {
        if (computeState.diskLinks == null || computeState.diskLinks.size() != 1) {
            logWarning("Only 1 disk is currently supported. Update skipped for compute state %s",
                    computeState.id);

            if (ctx.computeStates.size() == 0) {
                logInfo("Finished updating compute states");
                ctx.subStage = EnumerationSubStages.CREATE;
                handleSubStage(ctx);
            }

            return;
        }

        DiskState rootDisk = new DiskState();
        rootDisk.customProperties = new HashMap<>();
        rootDisk.customProperties.put(AZURE_OSDISK_CACHING,
                vm.properties.storageProfile.getOsDisk().getCaching());
        rootDisk.documentSelfLink = computeState.diskLinks.get(0);

        // TODO VSYM-630: Discover storage keys for storage account during Azure enumeration

        Operation.createPatch(this, rootDisk.documentSelfLink)
                .setBody(rootDisk)
                .setCompletion((completedOp, failure) -> {
                    // Remove processed virtual machine from the map
                    ctx.virtualMachines.remove(computeState.id);

                    if (failure != null) {
                        logSevere(failure);
                    }

                    if (numOfUpdates.decrementAndGet() == 0) {
                        logInfo("Finished updating compute states");
                        ctx.subStage = EnumerationSubStages.CREATE;
                        handleSubStage(ctx);
                    }
                })
                .sendWith(this);
    }

    /**
     * Creates relevant resources for given VMs.
     */
    private void create(EnumerationContext ctx) {
        if (ctx.virtualMachines.size() == 0) {
            if (ctx.enumNextPageLink != null) {
                ctx.subStage = EnumerationSubStages.LISTVMS;
                handleSubStage(ctx);
                return;
            }

            logInfo("No virtual machine available for creation");
            ctx.subStage = EnumerationSubStages.PATCH_ADDITIONAL_FIELDS;
            handleSubStage(ctx);
            return;
        }

        logFine("%d compute description with states to be created", ctx.virtualMachines.size());

        Iterator<Entry<String, VirtualMachine>> iterator = ctx.virtualMachines.entrySet()
                .iterator();
        AtomicInteger size = new AtomicInteger(ctx.virtualMachines.size());

        while (iterator.hasNext()) {
            Entry<String, VirtualMachine> vmEntry = iterator.next();
            VirtualMachine virtualMachine = vmEntry.getValue();
            iterator.remove();
            createHelper(ctx, virtualMachine, size);
        }
    }

    private void createHelper(EnumerationContext ctx, VirtualMachine virtualMachine,
            AtomicInteger size) {
        Collection<Operation> opCollection = new ArrayList<>();
        AuthCredentialsServiceState auth = new AuthCredentialsServiceState();
        auth.userEmail = virtualMachine.properties.osProfile.getAdminUsername();
        auth.privateKey = virtualMachine.properties.osProfile.getAdminPassword();
        auth.documentSelfLink = UUID.randomUUID().toString();
        auth.tenantLinks = ctx.computeHostDesc.tenantLinks;

        String authLink = UriUtils.buildUriPath(AuthCredentialsService.FACTORY_LINK,
                auth.documentSelfLink);

        Operation authOp = Operation
                .createPost(getHost(), AuthCredentialsService.FACTORY_LINK)
                .setBody(auth);
        opCollection.add(authOp);

        // TODO VSYM-631: Match existing descriptions for new VMs discovered on Azure
        ComputeDescription computeDescription = new ComputeDescription();
        computeDescription.id = UUID.randomUUID().toString();
        computeDescription.name = virtualMachine.name;
        computeDescription.regionId = virtualMachine.location;
        computeDescription.authCredentialsLink = authLink;
        computeDescription.documentSelfLink = computeDescription.id;
        computeDescription.environmentName = ENVIRONMENT_NAME_AZURE;
        computeDescription.instanceAdapterReference = UriUtils
                .buildUri(getHost(), AzureUriPaths.AZURE_INSTANCE_ADAPTER);
        computeDescription.statsAdapterReference = UriUtils
                .buildUri(getHost(), AzureUriPaths.AZURE_STATS_ADAPTER);
        computeDescription.customProperties = new HashMap<>();
        computeDescription.customProperties
                .put(AZURE_VM_SIZE, virtualMachine.properties.hardwareProfile.getVmSize());
        String diagnosticStorageAccountName = null;
        // TODO: https://jira-hzn.eng.vmware.com/browse/VSYM-1452
        if (virtualMachine.properties.diagnosticsProfile != null) {
            diagnosticStorageAccountName = getStorageAccountName(virtualMachine.properties.diagnosticsProfile
                    .getBootDiagnostics().getStorageUri());
            computeDescription.customProperties.put(AZURE_DIAGNOSTIC_STORAGE_ACCOUNT_NAME,
                    diagnosticStorageAccountName);
        }
        // TODO: https://jira-hzn.eng.vmware.com/browse/VSYM-1268
        String resourceGroupName = getResourceGroupName(virtualMachine.id);
        computeDescription.customProperties.put(AZURE_RESOURCE_GROUP_NAME,
                resourceGroupName);
        computeDescription.tenantLinks = ctx.computeHostDesc.tenantLinks;

        Operation compDescOp = Operation
                .createPost(getHost(), ComputeDescriptionService.FACTORY_LINK)
                .setBody(computeDescription);
        opCollection.add(compDescOp);

        // Create root disk
        DiskState rootDisk = new DiskState();
        rootDisk.id = UUID.randomUUID().toString();
        rootDisk.documentSelfLink = rootDisk.id;
        rootDisk.name = virtualMachine.properties.storageProfile.getOsDisk().getName();
        rootDisk.type = DiskType.HDD;
        ImageReference imageReference = virtualMachine.properties.storageProfile.getImageReference();
        rootDisk.sourceImageReference = URI.create(imageReferenceToImageId(imageReference));
        rootDisk.bootOrder = 1;
        rootDisk.customProperties = new HashMap<>();
        rootDisk.customProperties.put(AZURE_OSDISK_CACHING,
                virtualMachine.properties.storageProfile.getOsDisk().getCaching());
        rootDisk.customProperties.put(AZURE_STORAGE_ACCOUNT_NAME,
                getStorageAccountName(
                        virtualMachine.properties.storageProfile.getOsDisk().getVhd().getUri()));
        rootDisk.tenantLinks = ctx.computeHostDesc.tenantLinks;

        List<String> vmDisks = new ArrayList<>();
        vmDisks.add(UriUtils.buildUriPath(DiskService.FACTORY_LINK, rootDisk.documentSelfLink));

        String storageAuthLink = UUID.randomUUID().toString();
        Operation storageOp = null;
        if (diagnosticStorageAccountName != null && diagnosticStorageAccountName.length() > 0) {
            logInfo("Diagnostic Storage Account is enabled for VM [%s]", computeDescription.name);
            getStorageManagementClient(ctx).getStorageAccountsOperations().listKeysAsync(
                    resourceGroupName, diagnosticStorageAccountName,
                    new AzureAsyncCallback<StorageAccountKeys>() {

                        @Override
                        public void onError(Throwable e) {
                            logSevere(e.getMessage());
                        }

                        @Override
                        public void onSuccess(ServiceResponse<StorageAccountKeys> result) {
                            StorageAccountKeys keys = result.getBody();
                            String key1 = keys.getKey1();
                            String key2 = keys.getKey2();

                            AuthCredentialsServiceState storageAuth = new AuthCredentialsServiceState();
                            storageAuth.documentSelfLink = storageAuthLink;
                            storageAuth.customProperties = new HashMap<>();
                            storageAuth.customProperties.put(AZURE_STORAGE_ACCOUNT_KEY1, key1);
                            storageAuth.customProperties.put(AZURE_STORAGE_ACCOUNT_KEY2, key2);
                            storageAuth.tenantLinks = ctx.computeHostDesc.tenantLinks;

                            Operation storageAuthOp = Operation
                                    .createPost(getHost(), AuthCredentialsService.FACTORY_LINK)
                                    .setBody(storageAuth);
                            sendRequest(storageAuthOp);
                        }
                    });
            StorageDescription storageDescription = new StorageDescription();
            storageDescription.documentSelfLink = UUID.randomUUID().toString();
            storageDescription.name = diagnosticStorageAccountName;
            storageDescription.resourcePoolLink = ctx.enumRequest.resourcePoolLink;
            storageDescription.tenantLinks = ctx.computeHostDesc.tenantLinks;
            storageDescription.authCredentialsLink = UriUtils.buildUriPath(
                    AuthCredentialsService.FACTORY_LINK,
                    storageAuthLink);

            storageOp = Operation
                    .createPost(getHost(),
                            StorageDescriptionService.FACTORY_LINK)
                    .setBody(storageDescription);
            opCollection.add(storageOp);

            // Set the storage description link only if storage account is present
            rootDisk.storageDescriptionLink = UriUtils.buildUriPath(
                    StorageDescriptionService.FACTORY_LINK,
                    storageDescription.documentSelfLink);
        }

        Operation diskOp = Operation.createPost(getHost(), DiskService.FACTORY_LINK)
                .setBody(rootDisk);
        opCollection.add(diskOp);

        // TODO: https://jira-hzn.eng.vmware.com/browse/VSYM-1473
        List<String> networkLinks = null;
        if (virtualMachine.properties.networkProfile != null) {
            NetworkInterfaceReference networkInterfaceReference = virtualMachine.properties.networkProfile
                    .getNetworkInterfaces().get(0);
            NetworkInterfaceState networkState = new NetworkInterfaceState();
            networkState.documentSelfLink = UUID.randomUUID().toString();
            networkState.id = networkInterfaceReference.getId();
            // Setting to the same ID since there is nothing obtained during enumeration other than the ID
            networkState.networkDescriptionLink = networkInterfaceReference.getId();
            networkState.tenantLinks = ctx.computeHostDesc.tenantLinks;
            Operation networkOp = Operation
                    .createPost(getHost(), NetworkInterfaceService.FACTORY_LINK)
                    .setBody(networkState);
            opCollection.add(networkOp);

            networkLinks = new ArrayList<>();
            networkLinks.add(UriUtils.buildUriPath(NetworkInterfaceService.FACTORY_LINK,
                    networkState.documentSelfLink));
        }

        // Create compute state
        ComputeState resource = new ComputeState();
        resource.documentSelfLink = UUID.randomUUID().toString();
        resource.creationTimeMicros = Utils.getNowMicrosUtc();
        resource.id = virtualMachine.id.toLowerCase();
        resource.parentLink = ctx.enumRequest.resourceLink();
        resource.descriptionLink = UriUtils.buildUriPath(
                ComputeDescriptionService.FACTORY_LINK, computeDescription.id);
        resource.resourcePoolLink = ctx.enumRequest.resourcePoolLink;
        resource.diskLinks = vmDisks;
        resource.customProperties = new HashMap<>();
        resource.customProperties.put(CUSTOM_DISPLAY_NAME, virtualMachine.name);
        resource.customProperties.put(CUSTOM_OS_TYPE, getNormalizedOSType(virtualMachine));
        resource.tenantLinks = ctx.computeHostDesc.tenantLinks;
        resource.networkLinks = networkLinks;

        ctx.computeStatesForPatching.put(resource.id, resource);

        Operation resourceOp = Operation
                .createPost(getHost(), ComputeService.FACTORY_LINK)
                .setBody(resource);
        opCollection.add(resourceOp);

        OperationJoin.create(opCollection)
                .setCompletion((ops, exs) -> {
                    if (exs != null) {
                        exs.values().forEach(ex -> logWarning("Error: %s", ex.getMessage()));
                    }

                    if (size.decrementAndGet() == 0) {
                        if (ctx.enumNextPageLink != null) {
                            ctx.subStage = EnumerationSubStages.LISTVMS;
                            handleSubStage(ctx);
                            return;
                        }

                        logInfo("Finished creating compute states");
                        ctx.subStage = EnumerationSubStages.PATCH_ADDITIONAL_FIELDS;
                        handleSubStage(ctx);
                    }

                }).sendWith(this);
    }

    private void patchAdditionalFields(EnumerationContext ctx) {
        if (ctx.computeStatesForPatching.size() == 0) {
            logInfo("No compute states available to patch additional fields");
            ctx.subStage = EnumerationSubStages.DELETE;
            handleSubStage(ctx);
            return;
        }

        // Patching power state and network Information. Hence 2.
        // If we patch more fields, this number should be increased accordingly.
        int numberOfAdditionalFieldsToPatch = 2;
        Iterator<Entry<String, ComputeState>> iterator = ctx.computeStatesForPatching.entrySet()
                .iterator();
        AtomicInteger numOfPatches = new AtomicInteger(
                ctx.computeStatesForPatching.size() * numberOfAdditionalFieldsToPatch);
        while (iterator.hasNext()) {
            Entry<String, ComputeState> csEntry = iterator.next();
            ComputeState computeState = csEntry.getValue();
            patchAdditionalFieldsHelper(ctx, computeState, numOfPatches);
        }
    }

    private void patchAdditionalFieldsHelper(EnumerationContext ctx, ComputeState resource,
            AtomicInteger numOfPatches) {
        String resourceGroupName = getResourceGroupName(resource.id);
        String vmName = resource.customProperties.get(CUSTOM_DISPLAY_NAME);
        patchVMInstanceDetails(ctx, resource, resourceGroupName, vmName, numOfPatches);
        patchVMNetworkDetails(ctx, resource, resourceGroupName, vmName, numOfPatches);
    }

    private void patchVMInstanceDetails(EnumerationContext ctx, ComputeState resource,
            String resourceGroupName, String vmName, AtomicInteger numOfPatches) {
        ComputeManagementClient computeClient = getComputeManagementClient(ctx);
        computeClient.getVirtualMachinesOperations().getAsync(resourceGroupName, vmName,
                EXPAND_INSTANCE_VIEW_PARAM,
                new AzureAsyncCallback<com.microsoft.azure.management.compute.models.VirtualMachine>() {

                    @Override
                    public void onError(Throwable e) {
                        logSevere(e.getMessage());
                        // There was an error for this compute. Log and move on.
                        numOfPatches.decrementAndGet();
                    }

                    @Override
                    public void onSuccess(
                            ServiceResponse<com.microsoft.azure.management.compute.models.VirtualMachine> result) {
                        com.microsoft.azure.management.compute.models.VirtualMachine machine = result
                                .getBody();
                        for (InstanceViewStatus status : machine.getInstanceView().getStatuses()) {
                            if (status.getCode()
                                    .equals(AzureConstants.AZURE_VM_PROVISIONING_STATE_SUCCEEDED)) {
                                resource.creationTimeMicros = TimeUnit.MILLISECONDS
                                        .toMicros(status.getTime().getMillis());
                            } else if (status.getCode()
                                    .equals(AzureConstants.AZURE_VM_POWER_STATE_RUNNING)) {
                                resource.powerState = PowerState.ON;
                            } else if (status.getCode()
                                    .equals(AzureConstants.AZURE_VM_POWER_STATE_DEALLOCATED)) {
                                resource.powerState = PowerState.OFF;
                            }
                        }
                        patchComputeResource(ctx, resource, numOfPatches);
                    }
                });
    }

    /**
     * Gets the network links from the compute resource.
     * Obtains the Network state information from Azure.
     */
    private void patchVMNetworkDetails(EnumerationContext ctx,
            ComputeState resource, String resourceGroupName, String vmName, AtomicInteger numOfPatches) {
        // TODO: https://jira-hzn.eng.vmware.com/browse/VSYM-1473
        if (resource.networkLinks != null) {
            String networkLink = resource.networkLinks.get(0);
            Operation.createGet(getHost(), networkLink).setCompletion((o, e) -> {
                if (e != null) {
                    logSevere(e.getMessage());
                    // There was an error for this compute. Log and move on.
                    numOfPatches.decrementAndGet();
                    return;
                }
                NetworkInterfaceState state = o.getBody(NetworkInterfaceState.class);
                String networkInterfaceName = UriUtils.getLastPathSegment(state.id);

                NetworkManagementClient client = getNetworkManagementClient(ctx);
                client.getNetworkInterfacesOperations().getAsync(resourceGroupName,
                        networkInterfaceName, null, new AzureAsyncCallback<NetworkInterface>() {

                            @Override
                            public void onError(Throwable e) {
                                logSevere(e.getMessage());
                                // There was an error for this compute. Log and move on.
                                numOfPatches.decrementAndGet();
                            }

                            @Override
                            public void onSuccess(ServiceResponse<NetworkInterface> result) {
                                NetworkInterface networkInterface = result.getBody();
                                if (networkInterface.getIpConfigurations() != null
                                        && !networkInterface.getIpConfigurations().isEmpty()
                                        && networkInterface.getIpConfigurations().get(0)
                                                .getPublicIPAddress() != null) {
                                    String publicIPAddressId = networkInterface
                                            .getIpConfigurations()
                                            .get(0).getPublicIPAddress().getId();
                                    String publicIPAddressName = UriUtils
                                            .getLastPathSegment(publicIPAddressId);

                                    getPublicIpAddress(ctx, resource, resourceGroupName, publicIPAddressName, numOfPatches);
                                } else {
                                    // There was no public IP address for this compute. Log and move on.
                                    logInfo("No public IP found for [%s]", vmName);
                                    numOfPatches.decrementAndGet();
                                }
                            }
                        });
            }).sendWith(this);
        } else {
            // There was no network for this compute. Log and move on.
            logInfo("No network links found for [%s]", vmName);
            numOfPatches.decrementAndGet();
        }
    }

    /**
     * Gets the public IP address from the VM and patches the compute state.
     */
    private void getPublicIpAddress(EnumerationContext ctx, ComputeState resource, String resourceGroupName, String publicIPAddressName, AtomicInteger numOfPatches) {
        NetworkManagementClient client = getNetworkManagementClient(ctx);
        client.getPublicIPAddressesOperations().getAsync(resourceGroupName,
                publicIPAddressName,
                null, new AzureAsyncCallback<PublicIPAddress>() {

                    @Override
                    public void onError(Throwable e) {
                        logSevere(e.getMessage());
                        numOfPatches.decrementAndGet();
                    }

                    @Override
                    public void onSuccess(
                            ServiceResponse<PublicIPAddress> result) {
                        PublicIPAddress publicIp = result.getBody();
                        resource.address = publicIp.getIpAddress();
                        patchComputeResource(ctx, resource, numOfPatches);
                    }
                });
    }

    private void patchComputeResource(EnumerationContext ctx, ComputeState resource, AtomicInteger numOfPatches) {
        Operation computePatchOp = Operation
                .createPost(getHost(), ComputeService.FACTORY_LINK)
                .setBody(resource);
        sendRequest(computePatchOp);

        if (numOfPatches.decrementAndGet() == 0) {
            logInfo("Finished patching compute states");
            ctx.subStage = EnumerationSubStages.DELETE;
            handleSubStage(ctx);
        }
    }

    private void handleError(EnumerationContext ctx, Throwable e) {
        logSevere("Failed at stage %s and sub-stage %s with exception: %s", ctx.stage, ctx.subStage,
                Utils.toString(e));
        ctx.error = e;
        ctx.stage = EnumerationStages.ERROR;
        handleEnumerationRequest(ctx);
    }

    /**
     * Return a key to uniquely identify enumeration for compute host instance.
     */
    private String getEnumKey(EnumerationContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("hostLink:").append(ctx.enumRequest.resourceLink());
        sb.append("-enumerationAdapterReference:")
                .append(ctx.computeHostDesc.enumerationAdapterReference);
        return sb.toString();
    }

    /**
     * Converts image reference to image identifier.
     */
    private String imageReferenceToImageId(ImageReference imageReference) {
        return imageReference.getPublisher() + ":" + imageReference.getOffer() + ":"
                + imageReference.getSku() + ":" + imageReference.getVersion();
    }

    private Retrofit.Builder getRetrofitBuilder() {
        Retrofit.Builder builder = new Retrofit.Builder();
        builder.callbackExecutor(this.executorService);
        return builder;
    }

    private ComputeManagementClient getComputeManagementClient(EnumerationContext ctx) {
        if (ctx.computeClient == null) {
            ctx.computeClient = new ComputeManagementClientImpl(
                    AzureConstants.BASE_URI, ctx.credentials, ctx.clientBuilder,
                    getRetrofitBuilder());
            ctx.computeClient.setSubscriptionId(ctx.parentAuth.userLink);
        }
        return ctx.computeClient;
    }

    private StorageManagementClient getStorageManagementClient(EnumerationContext ctx) {
        if (ctx.storageClient == null) {
            ctx.storageClient = new StorageManagementClientImpl(
                    AzureConstants.BASE_URI, ctx.credentials, ctx.clientBuilder,
                    getRetrofitBuilder());
            ctx.storageClient.setSubscriptionId(ctx.parentAuth.userLink);
        }
        return ctx.storageClient;
    }

    private NetworkManagementClient getNetworkManagementClient(EnumerationContext ctx) {
        if (ctx.networkClient == null) {
            ctx.networkClient = new NetworkManagementClientImpl(
                    AzureConstants.BASE_URI, ctx.credentials, ctx.clientBuilder,
                    getRetrofitBuilder());
            ctx.networkClient.setSubscriptionId(ctx.parentAuth.userLink);
        }
        return ctx.networkClient;
    }

    /**
     * Extracts storage account name from the given storage URI.
     */
    private String getStorageAccountName(String storageUri) {
        Matcher matcher = STORAGE_ACCOUNT_NAME_PATTERN.matcher(storageUri);
        if (matcher.find()) {
            return matcher.group(1);
        }
        logFine("Input storageUri was [%s]", storageUri);
        return storageUri;
    }

    private String getResourceGroupName(String vmId) {
        Matcher matcher = RESOURCE_GROUP_NAME_PATTERN.matcher(vmId.toLowerCase());
        if (matcher.find()) {
            return matcher.group(1).toLowerCase();
        }
        logFine("Input VM id was [%s]", vmId);
        return vmId;
    }

    /**
     * Return Instance normalized OS Type.
     */
    private String getNormalizedOSType(VirtualMachine vm) {
        if (vm.properties == null
                || vm.properties.storageProfile == null
                || vm.properties.storageProfile.getOsDisk() == null
                || vm.properties.storageProfile.getOsDisk().getOsType() == null) {
            return null;
        }
        String osType = vm.properties.storageProfile.getOsDisk().getOsType();
        if (WINDOWS_OPERATING_SYSTEM.equalsIgnoreCase(osType)) {
            return OSType.WINDOWS.toString();
        } else if (LINUX_OPERATING_SYSTEM.equalsIgnoreCase(osType)) {
            return OSType.LINUX.toString();
        } else {
            return null;
        }
    }
}
