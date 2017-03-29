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

import static com.vmware.photon.controller.model.ComputeProperties.CUSTOM_OS_TYPE;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AUTH_HEADER_BEARER_PREFIX;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_DIAGNOSTIC_STORAGE_ACCOUNT_LINK;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_OSDISK_CACHING;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_RESOURCE_GROUP_NAME;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNT_URI;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.LINUX_OPERATING_SYSTEM;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.LIST_VM_URI;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.QUERY_PARAM_API_VERSION;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.VM_REST_API_VERSION;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.WINDOWS_OPERATING_SYSTEM;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.getQueryResultLimit;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.cleanUpHttpClient;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.getAzureConfig;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.getResourceGroupName;
import static com.vmware.photon.controller.model.adapters.util.TagsUtil.newExternalTagState;
import static com.vmware.photon.controller.model.adapters.util.TagsUtil.setTagLinksToResourceState;
import static com.vmware.photon.controller.model.adapters.util.TagsUtil.updateLocalTagStates;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.CUSTOM_PROP_ENDPOINT_LINK;
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
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

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
import com.microsoft.rest.ServiceResponse;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;

import com.vmware.photon.controller.model.ComputeProperties.OSType;
import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.azure.AzureAsyncCallback;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.model.vm.VirtualMachine;
import com.vmware.photon.controller.model.adapters.azure.model.vm.VirtualMachineListResult;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.ComputeEnumerateAdapterRequest;
import com.vmware.photon.controller.model.adapters.util.enums.EnumerationStages;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.ComputeService.LifecycleState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.tasks.QueryUtils;
import com.vmware.xenon.common.DeferredResult;
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
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;

/**
 * Enumeration adapter for data collection of VMs on Azure.
 */
public class AzureComputeEnumerationAdapterService extends StatelessService {
    public static final String SELF_LINK = AzureUriPaths.AZURE_COMPUTE_ENUMERATION_ADAPTER;

    public AzureComputeEnumerationAdapterService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    public static final List<String> AZURE_VM_TERMINATION_STATES = Arrays.asList("Deleting",
            "Deleted");

    private static final String EXPAND_INSTANCE_VIEW_PARAM = "instanceView";

    /**
     * Substages to handle Azure VM data collection.
     */
    private enum ComputeEnumerationSubStages {
        LISTVMS,
        GET_COMPUTE_STATES,
        CREATE_TAG_STATES,
        UPDATE_COMPUTE_STATES,
        UPDATE_TAG_LINKS,
        GET_DISK_STATES,
        GET_STORAGE_DESCRIPTIONS,
        CREATE_COMPUTE_DESCRIPTIONS,
        UPDATE_DISK_STATES,
        CREATE_NETWORK_INTERFACE_STATES,
        CREATE_COMPUTE_STATES,
        PATCH_ADDITIONAL_FIELDS,
        DELETE_COMPUTE_STATES,
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
        AdapterUtils.awaitTermination(this.executorService);
        super.handleStop(delete);
    }

    /**
     * The enumeration service context that holds all the information needed to determine the list
     * of instances that need to be represented in the system.
     */
    private static class EnumerationContext {
        ComputeEnumerateResourceRequest request;
        ComputeStateWithDescription parentCompute;
        EnumerationStages stage;
        Throwable error;
        AuthCredentialsServiceState parentAuth;
        long enumerationStartTimeInMicros;
        String deletionNextPageLink;
        String enumNextPageLink;

        // Substage specific fields
        ComputeEnumerationSubStages subStage;
        Map<String, VirtualMachine> virtualMachines = new ConcurrentHashMap<>();
        Map<String, ComputeState> computeStates = new ConcurrentHashMap<>();
        Map<String, DiskState> diskStates = new ConcurrentHashMap<>();
        Map<String, StorageDescription> storageDescriptions = new ConcurrentHashMap<>();
        Map<String, String> networkInterfaceIds = new ConcurrentHashMap<>();
        Map<String, String> computeDescriptionIds = new ConcurrentHashMap<>();
        // Compute States for patching additional fields.
        Map<String, ComputeState> computeStatesForPatching = new ConcurrentHashMap<>();
        Map<String, VirtualMachine> vmsToUpdate = new ConcurrentHashMap<>();

        List<String> vmIds = new ArrayList<>();

        // Stored operation to signal completion to the Azure storage enumeration once all the
        // stages
        // are successfully completed.
        public Operation operation;
        // Azure specific fields
        ApplicationTokenCredentials credentials;
        public OkHttpClient.Builder clientBuilder;
        public OkHttpClient httpClient;

        // Azure clients
        NetworkManagementClient networkClient;
        ComputeManagementClient computeClient;

        EnumerationContext(ComputeEnumerateAdapterRequest request, Operation op) {
            this.request = request.original;
            this.parentAuth = request.parentAuth;
            this.parentCompute = request.parentCompute;

            this.stage = EnumerationStages.CLIENT;
            this.operation = op;
        }
    }

    private Set<String> ongoingEnumerations = new ConcurrentSkipListSet<>();

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        EnumerationContext ctx = new EnumerationContext(
                op.getBody(ComputeEnumerateAdapterRequest.class), op);

        if (ctx.request.isMockRequest) {
            op.complete();
            return;
        }
        handleEnumeration(ctx);
    }

    private void handleEnumeration(EnumerationContext ctx) {
        switch (ctx.stage) {
        case CLIENT:
            if (ctx.credentials == null) {
                try {
                    ctx.credentials = getAzureConfig(ctx.parentAuth);
                } catch (Throwable e) {
                    logSevere(e);
                    ctx.error = e;
                    ctx.stage = EnumerationStages.ERROR;
                    handleEnumeration(ctx);
                    return;
                }
            }
            if (ctx.httpClient == null) {
                try {
                    // Creating a shared singleton Http client instance
                    // Reference
                    // https://square.github.io/okhttp/3.x/okhttp/okhttp3/OkHttpClient.html
                    // TODO: https://github.com/Azure/azure-sdk-for-java/issues/1000
                    ctx.httpClient = new OkHttpClient();
                    ctx.clientBuilder = ctx.httpClient.newBuilder();
                } catch (Exception e) {
                    handleError(ctx, e);
                    return;
                }
            }
            ctx.stage = EnumerationStages.ENUMERATE;
            handleEnumeration(ctx);
            break;
        case ENUMERATE:
            String enumKey = getEnumKey(ctx);
            switch (ctx.request.enumerationAction) {
            case START:
                if (!this.ongoingEnumerations.add(enumKey)) {
                    logWarning(() -> String.format("Enumeration service has already been started"
                            + " for %s", enumKey));
                    return;
                }
                logInfo(() -> String.format("Launching enumeration service for %s", enumKey));
                ctx.enumerationStartTimeInMicros = Utils.getNowMicrosUtc();
                ctx.request.enumerationAction = EnumerationAction.REFRESH;
                handleEnumeration(ctx);
                break;
            case REFRESH:
                ctx.subStage = ComputeEnumerationSubStages.LISTVMS;
                handleSubStage(ctx);
                break;
            case STOP:
                if (this.ongoingEnumerations.remove(enumKey)) {
                    logInfo(() -> String.format("Enumeration service will be stopped for %s",
                            enumKey));
                } else {
                    logInfo(() -> String.format("Enumeration service is not running or was already"
                                    + " stopped for %s", enumKey));
                }
                ctx.stage = EnumerationStages.FINISHED;
                handleEnumeration(ctx);
                break;
            default:
                logSevere(() -> String.format("Unknown enumeration action %s",
                        ctx.request.enumerationAction));
                ctx.stage = EnumerationStages.ERROR;
                handleEnumeration(ctx);
                break;
            }
            break;
        case FINISHED:
            ctx.operation.complete();
            cleanUpHttpClient(this, ctx.httpClient);
            logInfo(() -> String.format("Enumeration finished for %s", getEnumKey(ctx)));
            this.ongoingEnumerations.remove(getEnumKey(ctx));
            break;
        case ERROR:
            ctx.operation.fail(ctx.error);
            cleanUpHttpClient(this, ctx.httpClient);
            logWarning(() -> String.format("Enumeration error for %s", getEnumKey(ctx)));
            this.ongoingEnumerations.remove(getEnumKey(ctx));
            break;
        default:
            String msg = String.format("Unknown Azure enumeration stage %s ", ctx.stage.toString());
            logSevere(() -> msg);
            ctx.error = new IllegalStateException(msg);
            ctx.operation.fail(ctx.error);
            cleanUpHttpClient(this, ctx.httpClient);
            this.ongoingEnumerations.remove(getEnumKey(ctx));
        }
    }

    /**
     * Handle enumeration substages for VM data collection.
     */
    private void handleSubStage(EnumerationContext ctx) {
        if (!this.ongoingEnumerations.contains(getEnumKey(ctx))) {
            ctx.stage = EnumerationStages.FINISHED;
            handleEnumeration(ctx);
            return;
        }

        switch (ctx.subStage) {
        case LISTVMS:
            getVmList(ctx);
            break;
        case GET_COMPUTE_STATES:
            queryForComputeStates(ctx);
            break;
        case CREATE_TAG_STATES:
            createTagStates(ctx);
            break;
        case UPDATE_COMPUTE_STATES:
            updateComputeStates(ctx);
            break;
        case UPDATE_TAG_LINKS:
            updateTagLinks(ctx).whenComplete(thenHandleSubStage(ctx, ComputeEnumerationSubStages.GET_DISK_STATES));
            break;
        case GET_DISK_STATES:
            queryForDiskStates(ctx);
            break;
        case GET_STORAGE_DESCRIPTIONS:
            queryForDiagnosticStorageDescriptions(ctx);
            break;
        case CREATE_COMPUTE_DESCRIPTIONS:
            createComputeDescriptions(ctx);
            break;
        case UPDATE_DISK_STATES:
            updateDiskStates(ctx);
            break;
        case CREATE_NETWORK_INTERFACE_STATES:
            createNetworkInterfaceStates(ctx);
            break;
        case CREATE_COMPUTE_STATES:
            createComputeStates(ctx);
            break;
        case PATCH_ADDITIONAL_FIELDS:
            patchAdditionalFields(ctx);
            break;
        case DELETE_COMPUTE_STATES:
            deleteComputeStates(ctx);
            break;
        case FINISHED:
            ctx.stage = EnumerationStages.FINISHED;
            handleEnumeration(ctx);
            break;
        default:
            String msg = String
                    .format("Unknown Azure enumeration sub-stage %s ", ctx.subStage.toString());
            ctx.error = new IllegalStateException(msg);
            ctx.stage = EnumerationStages.ERROR;
            handleEnumeration(ctx);
            break;
        }
    }

    private DeferredResult<EnumerationContext> updateTagLinks(EnumerationContext context) {
        logFine(() -> "Create or update Network States' tags with the actual tags in Azure.");

        if (context.vmsToUpdate.size() == 0) {
            logFine(() -> "No local networks or subnets to be updated so there are no tags to update.");
            return DeferredResult.completed(context);
        } else {

            List<DeferredResult<Void>> updateTagLinksOps = new ArrayList<>();
            // update tag links for the existing NetworkStates
            for (String vmId : context.computeStatesForPatching.keySet()) {
                VirtualMachine vm = context.vmsToUpdate.get(vmId);
                ComputeState existingComputeState = context.computeStatesForPatching.get(vmId);
                Map<String, String> remoteTags = new HashMap<>();
                if (vm.tags != null && !vm.tags.isEmpty()) {
                    for (Entry<String, String> vmTagEntry : vm.tags.entrySet()) {
                        remoteTags.put(vmTagEntry.getKey(), vmTagEntry.getValue());
                    }
                }
                updateTagLinksOps.add(updateLocalTagStates(this, existingComputeState, remoteTags));
                // clean up the working copy of existing compute state tag links, so that they are not overwritten
                // with the updated ones in the subsequent state machine steps
                existingComputeState.tagLinks = null;
                context.computeStatesForPatching.put(vmId, existingComputeState);
            }

            return DeferredResult.allOf(updateTagLinksOps).thenApply(gnore -> context);
        }
    }

    /**
     * {@code handleSubStage} version suitable for chaining to
     * {@code DeferredResult.whenComplete}.
     */
    private BiConsumer<EnumerationContext, Throwable> thenHandleSubStage(EnumerationContext context,
            ComputeEnumerationSubStages next) {
        // NOTE: In case of error 'ignoreCtx' is null so use passed context!
        return (ignoreCtx, exc) -> {
            if (exc != null) {
                context.stage = EnumerationStages.ERROR;
                handleEnumeration(context);
                return;
            }
            context.subStage = next;
            handleSubStage(context);
        };
    }

    /**
     * Deletes undiscovered resources.
     *
     * The logic works by recording a timestamp when enumeration starts. This timestamp is used to
     * lookup resources which haven't been touched as part of current enumeration cycle. The other
     * data point this method uses is the virtual machines discovered as part of list vm call.
     *
     * Finally, delete on a resource is invoked only if it meets two criteria: - Timestamp older
     * than current enumeration cycle. - VM not present on Azure.
     *
     * The method paginates through list of resources for deletion.
     */
    private void deleteComputeStates(EnumerationContext ctx) {
        Query.Builder qBuilder = Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK,
                        ctx.request.resourceLink())
                .addRangeClause(ComputeState.FIELD_NAME_UPDATE_TIME_MICROS,
                        NumericRange.createLessThanRange(ctx.enumerationStartTimeInMicros))
                .addInClause(ComputeState.FIELD_NAME_LIFECYCLE_STATE,
                        Arrays.asList(LifecycleState.PROVISIONING.toString(),
                                LifecycleState.RETIRED.toString()),
                        Occurance.MUST_NOT_OCCUR);

        addScopeCriteria(qBuilder, ComputeState.class, ctx);

        QueryTask q = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .setQuery(qBuilder.build())
                .setResultLimit(getQueryResultLimit())
                .build();
        q.tenantLinks = ctx.parentCompute.tenantLinks;

        logFine(() -> "Querying compute resources for deletion");
        QueryUtils.startQueryTask(this, q)
                .whenComplete((queryTask, e) -> {
                    if (e != null) {
                        handleError(ctx, e);
                        return;
                    }

                    if (queryTask.results.nextPageLink == null) {
                        logFine(() -> "No compute states match for deletion");
                        ctx.subStage = ComputeEnumerationSubStages.FINISHED;
                        handleSubStage(ctx);
                        return;
                    }

                    ctx.deletionNextPageLink = queryTask.results.nextPageLink;
                    deleteOrRetireHelper(ctx);
                });
    }

    /**
     * Helper method to paginate through resources to be deleted.
     */
    private void deleteOrRetireHelper(EnumerationContext ctx) {
        if (ctx.deletionNextPageLink == null) {
            logFine(() -> String.format("Finished %s of compute states for Azure",
                    ctx.request.preserveMissing ? "retiring" : "deletion"));
            ctx.subStage = ComputeEnumerationSubStages.FINISHED;
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

                if (ctx.request.preserveMissing) {
                    logFine(() -> String.format("Retiring compute state %s",
                            computeState.documentSelfLink));
                    ComputeState cs = new ComputeState();
                    cs.powerState = PowerState.OFF;
                    cs.lifecycleState = LifecycleState.RETIRED;
                    operations.add(
                            Operation.createPatch(this, computeState.documentSelfLink).setBody(cs));
                } else {
                    operations.add(Operation.createDelete(this, computeState.documentSelfLink));
                    logFine(() -> String.format("Deleting compute state %s",
                            computeState.documentSelfLink));

                    if (computeState.diskLinks != null && !computeState.diskLinks.isEmpty()) {
                        operations.add(Operation
                                .createDelete(this, computeState.diskLinks.get(0)));
                        logFine(() -> String.format("Deleting disk state %s",
                                computeState.diskLinks.get(0)));
                    }
                }
            }

            if (operations.size() == 0) {
                logFine(() -> String.format("No compute/disk states to %s",
                        ctx.request.preserveMissing ? "retire" : "delete"));
                deleteOrRetireHelper(ctx);
                return;
            }

            OperationJoin.create(operations)
                    .setCompletion((ops, exs) -> {
                        if (exs != null) {
                            // We don't want to fail the whole data collection if some of the
                            // operation fails.
                            exs.values().forEach(
                                    ex -> logWarning(() -> String.format("Error: %s",
                                            ex.getMessage())));
                        }

                        deleteOrRetireHelper(ctx);
                    })
                    .sendWith(this);
        };
        logFine(() -> String.format("Querying page [%s] for resources to be %s",
                ctx.deletionNextPageLink, ctx.request.preserveMissing ? "retire" : "delete"));
        sendRequest(Operation.createGet(this, ctx.deletionNextPageLink)
                .setCompletion(completionHandler));
    }

    /**
     * Enumerate VMs from Azure.
     */
    private void getVmList(EnumerationContext ctx) {
        logFine(() -> "Enumerating VMs from Azure");
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
        operation.addRequestHeader(Operation.CONTENT_TYPE_HEADER,
                Operation.MEDIA_TYPE_APPLICATION_JSON);
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
                ctx.subStage = ComputeEnumerationSubStages.DELETE_COMPUTE_STATES;
                handleSubStage(ctx);
                return;
            }

            ctx.enumNextPageLink = results.nextLink;

            logFine(() -> String.format("Retrieved %d VMs from Azure", virtualMachines.size()));
            logFine(() -> String.format("Next page link %s", ctx.enumNextPageLink));

            for (VirtualMachine virtualMachine : virtualMachines) {
                // We don't want to process VMs that are being terminated.
                if (AZURE_VM_TERMINATION_STATES
                        .contains(virtualMachine.properties.provisioningState)) {
                    logFine(() -> String.format("Not processing %s", virtualMachine.id));
                    continue;
                }

                // Azure for some case changes the case of the vm id.
                String vmId = virtualMachine.id.toLowerCase();
                ctx.virtualMachines.put(vmId, virtualMachine);
                ctx.vmIds.add(vmId);
            }

            logFine(() -> String.format("Processing %d VMs", ctx.vmIds.size()));

            ctx.subStage = ComputeEnumerationSubStages.GET_COMPUTE_STATES;
            handleSubStage(ctx);

        });
        sendRequest(operation);
    }

    /**
     * Query all compute states for the cluster filtered by the received set of instance Ids.
     */
    private void queryForComputeStates(EnumerationContext ctx) {
        Query.Builder qBuilder = Query.Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK, ctx.request.resourceLink());

        addScopeCriteria(qBuilder, ComputeState.class, ctx);

        Query.Builder instanceIdFilterParentQuery = Query.Builder.create(Occurance.MUST_OCCUR);

        for (String instanceId : ctx.virtualMachines.keySet()) {
            Query instanceIdFilter = Query.Builder.create(Occurance.SHOULD_OCCUR)
                    .addFieldClause(ComputeState.FIELD_NAME_ID, instanceId)
                    .build();
            instanceIdFilterParentQuery.addClause(instanceIdFilter);
        }

        qBuilder.addClause(instanceIdFilterParentQuery.build());

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .setQuery(qBuilder.build())
                .addOption(QueryOption.EXPAND_CONTENT)
                .addOption(QueryOption.TOP_RESULTS)
                .setResultLimit(getQueryResultLimit())
                .build();
        queryTask.tenantLinks = ctx.parentCompute.tenantLinks;

        QueryUtils.startQueryTask(this, queryTask)
                .whenComplete((qrt, e) -> {
                    if (e != null) {
                        handleError(ctx, e);
                        return;
                    }
                    logFine(() -> String.format("Found %d matching compute states for Azure VMs",
                            qrt.results.documentCount));

                    // If there are no matches, there is nothing to update.
                    if (qrt.results.documentCount == 0) {
                        ctx.subStage = ComputeEnumerationSubStages.GET_DISK_STATES;
                        handleSubStage(ctx);
                        return;
                    }

                    for (Object s : qrt.results.documents.values()) {
                        ComputeState computeState = Utils.fromJson(s, ComputeState.class);
                        String instanceId = computeState.id;
                        ctx.computeStates.put(instanceId, computeState);
                    }

                    ctx.subStage = ComputeEnumerationSubStages.CREATE_TAG_STATES;
                    handleSubStage(ctx);
                });
    }

    /**
     * Create tag states for the VM's tags
     */
    private void createTagStates(EnumerationContext context) {
        logFine(() -> "Create or update Tag States for discovered VMs with the actual state in Azure.");

        if (context.virtualMachines.isEmpty()) {
            logFine("No VMs fount, so no tags need to be created/updated. Continue to update compute states.");
            context.subStage = ComputeEnumerationSubStages.UPDATE_COMPUTE_STATES;
            handleSubStage(context);
            return;
        }

        // POST each of the tags. If a tag exists it won't be created again. We don't want the name
        // tags, so filter them out
        List<Operation> operations = context.virtualMachines.values()
                .stream().filter(vm -> vm.tags != null && !vm.tags.isEmpty())
                .flatMap(vm -> vm.tags.entrySet().stream())
                .map(entry -> newExternalTagState(entry.getKey(), entry.getValue(), context.parentCompute.tenantLinks))
                .map(tagState -> Operation.createPost(this, TagService.FACTORY_LINK)
                        .setBody(tagState))
                .collect(Collectors.toList());

        if (operations.isEmpty()) {
            context.subStage = ComputeEnumerationSubStages.UPDATE_COMPUTE_STATES;
            handleSubStage(context);
        } else {
            OperationJoin.create(operations).setCompletion((ops, exs) -> {
                if (exs != null && !exs.isEmpty()) {
                    handleError(context, exs.values().iterator().next());
                    return;
                }

                logFine("Continue on to update compute states.");
                context.subStage = ComputeEnumerationSubStages.UPDATE_COMPUTE_STATES;
                handleSubStage(context);
            }).sendWith(this);
        }
    }

    /**
     * Updates matching compute states for given VMs.
     */
    private void updateComputeStates(EnumerationContext ctx) {
        if (ctx.computeStates.size() == 0) {
            logFine(() -> "No compute states found to be updated.");
            ctx.subStage = ComputeEnumerationSubStages.UPDATE_TAG_LINKS;
            handleSubStage(ctx);
            return;
        }

        Iterator<Entry<String, ComputeState>> iterator = ctx.computeStates.entrySet()
                .iterator();
        AtomicInteger numOfUpdates = new AtomicInteger(ctx.computeStates.size());
        while (iterator.hasNext()) {
            Entry<String, ComputeState> csEntry = iterator.next();
            // Get the local state and its corresponding remote VM
            ComputeState computeState = csEntry.getValue();
            VirtualMachine virtualMachine = ctx.virtualMachines.get(csEntry.getKey());

            // Store them in a separate structure for the update algorithm
            ctx.computeStatesForPatching.put(csEntry.getKey(), computeState);
            ctx.vmsToUpdate.put(csEntry.getKey(), virtualMachine);

            // Remove the processed compute state and vm from the all elements' maps
            iterator.remove();
            ctx.virtualMachines.remove(computeState.id);

            if (computeState.diskLinks == null || computeState.diskLinks.size() != 1) {
                logWarning(() -> String.format("Only 1 disk is currently supported. Update skipped for"
                                + " compute state %s", computeState.id));

                if (ctx.computeStates.size() == 0) {
                    logFine(() -> "Finished updating compute states.");
                    ctx.subStage = ComputeEnumerationSubStages.UPDATE_TAG_LINKS;
                    handleSubStage(ctx);
                }

                continue;
            }

            DiskState rootDisk = new DiskState();
            rootDisk.customProperties = new HashMap<>();
            rootDisk.customProperties.put(AZURE_OSDISK_CACHING,
                    virtualMachine.properties.storageProfile.getOsDisk().getCaching());
            rootDisk.documentSelfLink = computeState.diskLinks.get(0);

            Operation.createPatch(this, rootDisk.documentSelfLink)
                    .setBody(rootDisk)
                    .setCompletion((completedOp, failure) -> {

                        // TODO: https://jira-hzn.eng.vmware.com/browse/VSYM-2765
                        if (failure != null) {
                            logWarning(() -> String.format("Failed to update compute state: %s",
                                    failure.getMessage()));
                        }

                        if (numOfUpdates.decrementAndGet() == 0) {
                            logFine(() -> "Finished updating compute states.");
                            ctx.subStage = ComputeEnumerationSubStages.UPDATE_TAG_LINKS;
                            handleSubStage(ctx);
                        }
                    })
                    .sendWith(this);
        }
    }

    /**
     * Get all disk states related to given VMs
     */
    private void queryForDiskStates(EnumerationContext ctx) {
        if (ctx.virtualMachines.size() == 0) {
            logFine(() -> "No virtual machines found to be associated with local disks");
            ctx.subStage = ComputeEnumerationSubStages.PATCH_ADDITIONAL_FIELDS;
            handleSubStage(ctx);
            return;
        }
        Query.Builder qBuilder = Query.Builder.create()
                .addKindFieldClause(DiskState.class)
                .addFieldClause(DiskState.FIELD_NAME_COMPUTE_HOST_LINK,
                        ctx.parentCompute.documentSelfLink);

        addScopeCriteria(qBuilder, DiskState.class, ctx);

        Query.Builder diskUriFilterParentQuery = Query.Builder.create();

        List<Query> diskUriFilters = new ArrayList<>();
        for (String instanceId : ctx.virtualMachines.keySet()) {
            String diskId = getVhdUri(ctx.virtualMachines.get(instanceId));

            if (diskId == null) {
                continue;
            }

            Query diskUriFilter = Query.Builder.create(Query.Occurance.SHOULD_OCCUR)
                    .addFieldClause(DiskState.FIELD_NAME_ID, diskId)
                    .build();
            diskUriFilters.add(diskUriFilter);
        }

        if (diskUriFilters.isEmpty()) {
            logFine(() -> "No virtual machines found to be associated with local disks");
            ctx.subStage = ComputeEnumerationSubStages.PATCH_ADDITIONAL_FIELDS;
            handleSubStage(ctx);
            return;
        }

        diskUriFilters.stream().forEach(diskUriFilter -> diskUriFilterParentQuery.addClause(diskUriFilter));
        qBuilder.addClause(diskUriFilterParentQuery.build());

        QueryTask q = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .addOption(QueryOption.TOP_RESULTS)
                .setResultLimit(getQueryResultLimit())
                .setQuery(qBuilder.build())
                .build();
        q.tenantLinks = ctx.parentCompute.tenantLinks;

        QueryUtils.startQueryTask(this, q)
                .whenComplete((queryTask, e) -> {
                    if (e != null) {
                        logWarning(() -> String.format("Failed to get disk: %s", e.getMessage()));
                        return;
                    }
                    logFine(() -> String.format("Found %d matching disk states for Azure blobs",
                            queryTask.results.documentCount));

                    // If there are no matches, continue with storage descriptions
                    if (queryTask.results.documentCount == 0) {
                        ctx.subStage = ComputeEnumerationSubStages.GET_STORAGE_DESCRIPTIONS;
                        handleSubStage(ctx);
                        return;
                    }

                    for (Object d : queryTask.results.documents.values()) {
                        DiskState diskState = Utils.fromJson(d, DiskState.class);
                        String diskUri = diskState.id;
                        ctx.diskStates.put(diskUri, diskState);
                    }

                    ctx.subStage = ComputeEnumerationSubStages.GET_STORAGE_DESCRIPTIONS;
                    handleSubStage(ctx);
                });
    }

    /**
     * Get all storage descriptions responsible for diagnostics of given VMs.
     */
    private void queryForDiagnosticStorageDescriptions(EnumerationContext ctx) {
        Query.Builder qBuilder = Query.Builder.create()
                .addKindFieldClause(StorageDescription.class)
                .addFieldClause(StorageDescription.FIELD_NAME_COMPUTE_HOST_LINK,
                        ctx.parentCompute.documentSelfLink);

        addScopeCriteria(qBuilder, StorageDescription.class, ctx);

        Query.Builder storageDescUriFilterParentQuery = Query.Builder
                .create(Query.Occurance.MUST_OCCUR);

        ctx.virtualMachines.keySet().stream().filter(instanceId -> ctx.virtualMachines
                .get(instanceId).properties.diagnosticsProfile != null)
                .forEach(instanceId -> {
                    String diagnosticStorageAccountUri = ctx.virtualMachines
                            .get(instanceId).properties.diagnosticsProfile.getBootDiagnostics()
                                    .getStorageUri();

                    String storageAccountProperty = QuerySpecification
                            .buildCompositeFieldName(
                                    StorageDescription.FIELD_NAME_CUSTOM_PROPERTIES,
                                    AZURE_STORAGE_ACCOUNT_URI);

                    Query storageAccountUriFilter = Query.Builder.create(Occurance.SHOULD_OCCUR)
                            .addFieldClause(storageAccountProperty, diagnosticStorageAccountUri)
                            .build();

                    storageDescUriFilterParentQuery.addClause(storageAccountUriFilter);
                });

        Query sdq = storageDescUriFilterParentQuery.build();
        if (sdq.booleanClauses == null || sdq.booleanClauses.isEmpty()) {
            ctx.subStage = ComputeEnumerationSubStages.CREATE_COMPUTE_DESCRIPTIONS;
            handleSubStage(ctx);
            return;
        }
        qBuilder.addClause(sdq);

        QueryTask q = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .addOption(QueryOption.TOP_RESULTS)
                .setResultLimit(getQueryResultLimit())
                .setQuery(qBuilder.build())
                .build();
        q.tenantLinks = ctx.parentCompute.tenantLinks;

        QueryUtils.startQueryTask(this, q)
                .whenComplete((queryTask, e) -> {
                    if (e != null) {
                        logWarning(() -> String.format("Failed to get storage accounts: %s",
                                e.getMessage()));
                        return;
                    }

                    logFine(() -> String.format("Found %d matching diagnostics storage accounts",
                            queryTask.results.documentCount));

                    // If there are no matches, continue to creating compute states
                    if (queryTask.results.documentCount == 0) {
                        ctx.subStage = ComputeEnumerationSubStages.CREATE_COMPUTE_DESCRIPTIONS;
                        handleSubStage(ctx);
                        return;
                    }

                    for (Object d : queryTask.results.documents.values()) {
                        StorageDescription storageDesc = Utils
                                .fromJson(d, StorageDescription.class);
                        String storageDescUri = storageDesc.customProperties
                                .get(AZURE_STORAGE_ACCOUNT_URI);
                        ctx.storageDescriptions.put(storageDescUri, storageDesc);
                    }

                    ctx.subStage = ComputeEnumerationSubStages.CREATE_COMPUTE_DESCRIPTIONS;
                    handleSubStage(ctx);
                });
    }

    /**
     * Creates relevant resources for given VMs.
     */
    private void createComputeDescriptions(EnumerationContext ctx) {
        if (ctx.virtualMachines.size() == 0) {
            if (ctx.enumNextPageLink != null) {
                ctx.subStage = ComputeEnumerationSubStages.LISTVMS;
                handleSubStage(ctx);
                return;
            }

            logFine(() -> "No virtual machine found for creation.");
            ctx.subStage = ComputeEnumerationSubStages.PATCH_ADDITIONAL_FIELDS;
            handleSubStage(ctx);
            return;
        }

        logFine(() -> String.format("%d compute description with states to be created",
                ctx.virtualMachines.size()));

        Iterator<Entry<String, VirtualMachine>> iterator = ctx.virtualMachines.entrySet()
                .iterator();

        Collection<Operation> opCollection = new ArrayList<>();
        while (iterator.hasNext()) {
            Entry<String, VirtualMachine> vmEntry = iterator.next();
            VirtualMachine virtualMachine = vmEntry.getValue();

            AuthCredentialsServiceState auth = new AuthCredentialsServiceState();
            auth.userEmail = virtualMachine.properties.osProfile.getAdminUsername();
            auth.privateKey = virtualMachine.properties.osProfile.getAdminPassword();
            auth.documentSelfLink = UUID.randomUUID().toString();
            auth.tenantLinks = ctx.parentCompute.tenantLinks;
            auth.customProperties = new HashMap<>();
            if (ctx.request.endpointLink != null) {
                auth.customProperties.put(CUSTOM_PROP_ENDPOINT_LINK, ctx.request.endpointLink);
            }

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
            computeDescription.endpointLink = ctx.request.endpointLink;
            computeDescription.documentSelfLink = computeDescription.id;
            computeDescription.environmentName = ENVIRONMENT_NAME_AZURE;
            computeDescription.instanceType = virtualMachine.properties.hardwareProfile.getVmSize();
            computeDescription.instanceAdapterReference = ctx.parentCompute.description.instanceAdapterReference;
            computeDescription.statsAdapterReference = ctx.parentCompute.description.statsAdapterReference;
            computeDescription.customProperties = new HashMap<>();

            // TODO: https://jira-hzn.eng.vmware.com/browse/VSYM-1268
            String resourceGroupName = getResourceGroupName(virtualMachine.id);
            computeDescription.customProperties.put(AZURE_RESOURCE_GROUP_NAME,
                    resourceGroupName);
            computeDescription.tenantLinks = ctx.parentCompute.tenantLinks;

            Operation compDescOp = Operation
                    .createPost(getHost(), ComputeDescriptionService.FACTORY_LINK)
                    .setBody(computeDescription);
            ctx.computeDescriptionIds.put(virtualMachine.name, computeDescription.id);
            opCollection.add(compDescOp);
        }

        OperationJoin.create(opCollection)
                .setCompletion((ops, exs) -> {
                    if (exs != null) {
                        exs.values().forEach(ex -> logWarning(() -> String.format("Error: %s",
                                ex.getMessage())));
                    }

                    logFine(() -> "Continue on to updating disks.");
                    ctx.subStage = ComputeEnumerationSubStages.UPDATE_DISK_STATES;
                    handleSubStage(ctx);
                }).sendWith(this);
    }

    /**
     * Update disk states with additional custom properties
     */
    private void updateDiskStates(EnumerationContext ctx) {
        Iterator<Entry<String, VirtualMachine>> iterator = ctx.virtualMachines.entrySet()
                .iterator();

        Collection<Operation> opCollection = new ArrayList<>();
        while (iterator.hasNext()) {
            Entry<String, VirtualMachine> vmEntry = iterator.next();
            VirtualMachine virtualMachine = vmEntry.getValue();
            String diskUri = getVhdUri(virtualMachine);

            if (diskUri == null) {
                logFine(() -> String.format("Disk URI not found for vm: %s", virtualMachine.id));
                continue;
            }

            DiskState diskToUpdate = ctx.diskStates.get(diskUri);
            if (diskToUpdate == null) {
                logFine(() -> String.format("Disk not found: %s", diskUri));
                continue;
            }
            ImageReference imageReference = virtualMachine.properties.storageProfile
                    .getImageReference();
            diskToUpdate.sourceImageReference = URI.create(imageReferenceToImageId(imageReference));
            diskToUpdate.bootOrder = 1;
            if (diskToUpdate.customProperties == null) {
                diskToUpdate.customProperties = new HashMap<>();
            }
            diskToUpdate.customProperties.put(AZURE_OSDISK_CACHING,
                    virtualMachine.properties.storageProfile.getOsDisk().getCaching());
            Operation diskOp = Operation.createPatch(getHost(), diskToUpdate.documentSelfLink)
                    .setBody(diskToUpdate);
            opCollection.add(diskOp);
        }

        if (opCollection.isEmpty()) {
            logFine(() -> "No local disk states fount to update.");
            ctx.subStage = ComputeEnumerationSubStages.CREATE_NETWORK_INTERFACE_STATES;
            handleSubStage(ctx);
            return;
        }

        OperationJoin.create(opCollection)
                .setCompletion((ops, exs) -> {
                    if (exs != null) {
                        // TODO: https://jira-hzn.eng.vmware.com/browse/VSYM-3256
                        exs.values().forEach(ex -> logWarning(() -> String.format("Error: %s",
                                ex.getMessage())));
                    }

                    logFine(() -> "Continue on to create tag states.");
                    ctx.subStage = ComputeEnumerationSubStages.CREATE_NETWORK_INTERFACE_STATES;
                    handleSubStage(ctx);

                }).sendWith(this);
    }

    /**
     * Create network interface states for each VM
     */
    private void createNetworkInterfaceStates(EnumerationContext ctx) {
        Iterator<Entry<String, VirtualMachine>> iterator = ctx.virtualMachines.entrySet()
                .iterator();

        Collection<Operation> opCollection = new ArrayList<>();
        while (iterator.hasNext()) {
            Entry<String, VirtualMachine> vmEntry = iterator.next();
            VirtualMachine virtualMachine = vmEntry.getValue();

            // TODO: https://jira-hzn.eng.vmware.com/browse/VSYM-1473
            if (virtualMachine.properties.networkProfile != null) {
                NetworkInterfaceReference networkInterfaceReference = virtualMachine.properties.networkProfile
                        .getNetworkInterfaces().get(0);
                NetworkInterfaceState networkState = new NetworkInterfaceState();
                networkState.documentSelfLink = UUID.randomUUID().toString();
                networkState.id = networkInterfaceReference.getId();
                networkState.endpointLink = ctx.request.endpointLink;
                // Setting to the same ID since there is nothing obtained during enumeration other
                // than the ID
                networkState.networkLink = networkInterfaceReference.getId();
                networkState.tenantLinks = ctx.parentCompute.tenantLinks;
                Operation networkOp = Operation
                        .createPost(getHost(), NetworkInterfaceService.FACTORY_LINK)
                        .setBody(networkState);
                opCollection.add(networkOp);

                ctx.networkInterfaceIds.put(
                        virtualMachine.properties.networkProfile.getNetworkInterfaces().get(0)
                                .getId(),
                        UriUtils.buildUriPath(NetworkInterfaceService.FACTORY_LINK,
                                networkState.documentSelfLink));
            }
        }
        OperationJoin.create(opCollection)
                .setCompletion((ops, exs) -> {
                    if (exs != null) {
                        exs.values().forEach(ex -> logWarning(() -> String.format("Error: %s",
                                ex.getMessage())));
                    }

                    logFine(() -> "Continue to create network interfaces.");
                    ctx.subStage = ComputeEnumerationSubStages.CREATE_COMPUTE_STATES;
                    handleSubStage(ctx);

                }).sendWith(this);
    }

    /**
     * Create compute state for each VM
     */
    private void createComputeStates(EnumerationContext ctx) {
        Iterator<Entry<String, VirtualMachine>> iterator = ctx.virtualMachines.entrySet()
                .iterator();

        Collection<Operation> opCollection = new ArrayList<>();
        while (iterator.hasNext()) {
            Entry<String, VirtualMachine> vmEntry = iterator.next();
            VirtualMachine virtualMachine = vmEntry.getValue();
            iterator.remove();

            List<String> vmDisks = new ArrayList<>();
            if (ctx.diskStates != null && ctx.diskStates.size() > 0) {
                String diskUri = getVhdUri(virtualMachine);
                if (diskUri != null) {
                    DiskState state = ctx.diskStates.get(diskUri);
                    if (state != null) {
                        vmDisks.add(state.documentSelfLink);
                    }
                }
            }

            if (vmDisks.isEmpty()) {
                continue;
            }

            List<String> networkLinks = new ArrayList<>();
            networkLinks.add(ctx.networkInterfaceIds.get(
                    virtualMachine.properties.networkProfile.getNetworkInterfaces().get(0)
                            .getId()));

            // Create compute state
            ComputeState computeState = new ComputeState();
            computeState.documentSelfLink = UUID.randomUUID().toString();
            computeState.creationTimeMicros = Utils.getNowMicrosUtc();
            computeState.id = virtualMachine.id.toLowerCase();
            computeState.name = virtualMachine.name;
            computeState.type = ComputeType.VM_GUEST;
            computeState.environmentName = ComputeDescription.ENVIRONMENT_NAME_AZURE;
            computeState.parentLink = ctx.request.resourceLink();
            computeState.descriptionLink = UriUtils
                    .buildUriPath(ComputeDescriptionService.FACTORY_LINK,
                            ctx.computeDescriptionIds.get(virtualMachine.name));
            computeState.endpointLink = ctx.request.endpointLink;
            computeState.resourcePoolLink = ctx.request.resourcePoolLink;
            computeState.diskLinks = vmDisks;
            computeState.customProperties = new HashMap<>();
            computeState.customProperties.put(CUSTOM_OS_TYPE, getNormalizedOSType(virtualMachine));

            // add tag links
            setTagLinksToResourceState(computeState, virtualMachine.tags);

            if (virtualMachine.properties.diagnosticsProfile != null) {
                String diagnosticsAccountUri = virtualMachine.properties.diagnosticsProfile
                        .getBootDiagnostics().getStorageUri();
                StorageDescription storageDesk = ctx.storageDescriptions.get(diagnosticsAccountUri);
                if (storageDesk != null) {
                    computeState.customProperties.put(AZURE_DIAGNOSTIC_STORAGE_ACCOUNT_LINK,
                            storageDesk.documentSelfLink);
                }
            }
            computeState.tenantLinks = ctx.parentCompute.tenantLinks;
            computeState.networkInterfaceLinks = networkLinks;

            ctx.computeStatesForPatching.put(computeState.id, computeState);

            Operation resourceOp = Operation
                    .createPost(getHost(), ComputeService.FACTORY_LINK)
                    .setBody(computeState);
            opCollection.add(resourceOp);
        }

        if (opCollection.isEmpty()) {
            logFine(() -> "No compute states found for update.");
            ctx.subStage = ComputeEnumerationSubStages.PATCH_ADDITIONAL_FIELDS;
            handleSubStage(ctx);
        }

        OperationJoin.create(opCollection)
                .setCompletion((ops, exs) -> {
                    if (exs != null) {
                        exs.values().forEach(ex -> logWarning(() -> String.format("Error: %s",
                                ex.getMessage())));
                    }

                    if (ctx.enumNextPageLink != null) {
                        ctx.subStage = ComputeEnumerationSubStages.LISTVMS;
                        handleSubStage(ctx);
                        return;
                    }

                    logFine(() -> "Finished creating compute states.");
                    ctx.subStage = ComputeEnumerationSubStages.PATCH_ADDITIONAL_FIELDS;
                    handleSubStage(ctx);

                }).sendWith(this);
    }

    private void patchAdditionalFields(EnumerationContext ctx) {
        if (ctx.computeStatesForPatching.size() == 0) {
            logFine(() -> "No compute states need to be patched.");
            ctx.subStage = ComputeEnumerationSubStages.DELETE_COMPUTE_STATES;
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

    private void patchAdditionalFieldsHelper(EnumerationContext ctx, ComputeState computeState,
            AtomicInteger numOfPatches) {
        String resourceGroupName = getResourceGroupName(computeState.id);
        String vmName = computeState.name;
        patchVMInstanceDetails(ctx, computeState, resourceGroupName, vmName, numOfPatches);
        patchVMNetworkDetails(ctx, computeState, resourceGroupName, vmName, numOfPatches);
    }

    private void patchVMInstanceDetails(EnumerationContext ctx, ComputeState computeState,
            String resourceGroupName, String vmName, AtomicInteger numOfPatches) {
        ComputeManagementClient computeClient = getComputeManagementClient(ctx);
        computeClient.getVirtualMachinesOperations().getAsync(resourceGroupName, vmName,
                EXPAND_INSTANCE_VIEW_PARAM,
                new AzureAsyncCallback<com.microsoft.azure.management.compute.models.VirtualMachine>() {
                    @Override
                    public void onError(Throwable e) {
                        // TODO: https://jira-hzn.eng.vmware.com/browse/VSYM-2765
                        logWarning(() -> String.format("Error getting compute: %s",
                                e.getMessage()));
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
                                computeState.creationTimeMicros = TimeUnit.MILLISECONDS
                                        .toMicros(status.getTime().getMillis());
                            } else if (status.getCode()
                                    .equals(AzureConstants.AZURE_VM_POWER_STATE_RUNNING)) {
                                computeState.powerState = PowerState.ON;
                            } else if (status.getCode()
                                    .equals(AzureConstants.AZURE_VM_POWER_STATE_DEALLOCATED)) {
                                computeState.powerState = PowerState.OFF;
                            }
                        }
                        computeState.type = ComputeType.VM_GUEST;
                        computeState.environmentName = ComputeDescription.ENVIRONMENT_NAME_AZURE;
                        patchComputeResource(ctx, computeState, numOfPatches);
                    }
                });
    }

    /**
     * Gets the network links from the compute state. Obtains the Network state information from
     * Azure.
     */
    private void patchVMNetworkDetails(EnumerationContext ctx, ComputeState computeState,
            String resourceGroupName, String vmName, AtomicInteger numOfPatches) {
        // TODO: https://jira-hzn.eng.vmware.com/browse/VSYM-1473
        if (computeState.networkInterfaceLinks != null) {
            String networkLink = computeState.networkInterfaceLinks.get(0);
            Operation.createGet(getHost(), networkLink).setCompletion((o, e) -> {
                if (e != null) {
                    // TODO: https://jira-hzn.eng.vmware.com/browse/VSYM-2765
                    logWarning(() -> String.format("Error getting network interface link: %s",
                            e.getMessage()));
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
                                // TODO: https://jira-hzn.eng.vmware.com/browse/VSYM-2765
                                logWarning(() -> String.format("Error getting network interface"
                                                + " details: %s", e.getMessage()));
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

                                    getPublicIpAddress(ctx, computeState, resourceGroupName,
                                            publicIPAddressName, numOfPatches);
                                } else {
                                    // No public IP address found. Log and move on.
                                    logFine(() -> String.format("No public IP found for [%s]",
                                            vmName));
                                    numOfPatches.decrementAndGet();
                                }
                            }
                        });
            }).sendWith(this);
        } else {
            // There was no network for this compute. Log and move on.
            logFine(() -> String.format("No network links found for [%s]", vmName));
            numOfPatches.decrementAndGet();
        }
    }

    /**
     * Gets the public IP address from the VM and patches the compute state.
     */
    private void getPublicIpAddress(EnumerationContext ctx, ComputeState computeState,
            String resourceGroupName, String publicIPAddressName, AtomicInteger numOfPatches) {
        NetworkManagementClient client = getNetworkManagementClient(ctx);
        client.getPublicIPAddressesOperations().getAsync(resourceGroupName,
                publicIPAddressName,
                null, new AzureAsyncCallback<PublicIPAddress>() {
                    @Override
                    public void onError(Throwable e) {
                        // TODO: https://jira-hzn.eng.vmware.com/browse/VSYM-2765
                        logWarning(() -> String.format("Error getting public IP: %s",
                                e.getMessage()));
                        numOfPatches.decrementAndGet();
                    }

                    @Override
                    public void onSuccess(ServiceResponse<PublicIPAddress> result) {
                        PublicIPAddress publicIp = result.getBody();
                        computeState.address = publicIp.getIpAddress();
                        patchComputeResource(ctx, computeState, numOfPatches);
                    }
                });
    }

    private void patchComputeResource(EnumerationContext ctx, ComputeState computeState,
            AtomicInteger numOfPatches) {
        String documentLink = computeState.documentSelfLink
                .startsWith(ComputeService.FACTORY_LINK) ? computeState.documentSelfLink
                        : UriUtils.buildUriPath(ComputeService.FACTORY_LINK,
                                computeState.documentSelfLink);
        Operation computePatchOp = Operation
                .createPatch(getHost(), documentLink)
                .setBody(computeState);
        sendRequest(computePatchOp);

        if (numOfPatches.decrementAndGet() == 0) {
            logFine(() -> "Finished patching compute states.");
            ctx.subStage = ComputeEnumerationSubStages.DELETE_COMPUTE_STATES;
            handleSubStage(ctx);
        }
    }

    private void handleError(EnumerationContext ctx, Throwable e) {
        logSevere(() -> String.format("Failed at stage %s and sub-stage %s with exception: %s",
                ctx.stage, ctx.subStage, Utils.toString(e)));
        ctx.error = e;
        ctx.stage = EnumerationStages.ERROR;
        handleEnumeration(ctx);
    }

    /**
     * Return a key to uniquely identify enumeration for compute host instance.
     */
    private String getEnumKey(EnumerationContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("hostLink:").append(ctx.request.resourceLink());
        sb.append("-enumerationAdapterReference:")
                .append(ctx.parentCompute.description.enumerationAdapterReference);
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

    private String getVhdUri(VirtualMachine vm) {
        if (vm.properties == null
                || vm.properties.storageProfile == null
                || vm.properties.storageProfile.getOsDisk() == null
                || vm.properties.storageProfile.getOsDisk().getVhd() == null
                || vm.properties.storageProfile.getOsDisk().getVhd().getUri() == null) {
            logWarning(String.format("Enumeration failed. VM %s has a ManagedDisk configuration, which is currently not supported.", vm.id));
            return null;
        }
        return httpsToHttp(vm.properties.storageProfile.getOsDisk().getVhd().getUri());
    }

    /**
     * Converts https to http.
     */
    private String httpsToHttp(String uri) {
        return (uri.startsWith("https")) ? uri.replace("https", "http") : uri;
    }

    /**
     * Constrain every query with endpointLink and tenantLinks, if presented.
     */
    private static void addScopeCriteria(Query.Builder qBuilder, Class<? extends ResourceState> stateClass, EnumerationContext ctx) {
        // Add TENANT_LINKS criteria
        QueryUtils.addTenantLinks(qBuilder, ctx.parentCompute.tenantLinks);
        // Add ENDPOINT_LINK criteria
        QueryUtils.addEndpointLink(qBuilder, stateClass, ctx.request.endpointLink);
    }
}
