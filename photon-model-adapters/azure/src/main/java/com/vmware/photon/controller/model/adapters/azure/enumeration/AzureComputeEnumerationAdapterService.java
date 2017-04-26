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
import static com.vmware.photon.controller.model.ComputeProperties.RESOURCE_GROUP_NAME;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.compute.ComputeManagementClient;
import com.microsoft.azure.management.compute.ComputeManagementClientImpl;
import com.microsoft.azure.management.compute.VirtualMachinesOperations;
import com.microsoft.azure.management.compute.models.ImageReference;
import com.microsoft.azure.management.compute.models.InstanceViewStatus;
import com.microsoft.azure.management.compute.models.NetworkInterfaceReference;
import com.microsoft.azure.management.network.NetworkInterfacesOperations;
import com.microsoft.azure.management.network.NetworkManagementClient;
import com.microsoft.azure.management.network.NetworkManagementClientImpl;
import com.microsoft.azure.management.network.models.NetworkInterface;
import com.microsoft.azure.management.network.models.NetworkInterfaceIPConfiguration;

import okhttp3.OkHttpClient;

import org.apache.commons.lang3.tuple.Pair;

import retrofit2.Retrofit;

import com.vmware.photon.controller.model.ComputeProperties.OSType;
import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.model.vm.VirtualMachine;
import com.vmware.photon.controller.model.adapters.azure.model.vm.VirtualMachineListResult;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureDeferredResultServiceCallback;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.ComputeEnumerateAdapterRequest;
import com.vmware.photon.controller.model.adapters.util.enums.EnumerationStages;
import com.vmware.photon.controller.model.query.QueryStrategy;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.query.QueryUtils.QueryByPages;
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
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.resources.TagService;
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
    public static final List<String> AZURE_VM_TERMINATION_STATES = Arrays.asList("Deleting",
            "Deleted");
    private static final String EXPAND_INSTANCE_VIEW_PARAM = "instanceView";
    private ExecutorService executorService;

    /**
     * The enumeration service context that holds all the information needed to determine the list
     * of instances that need to be represented in the system.
     */
    private static class EnumerationContext {
        // Stored operation to signal completion to the Azure storage enumeration once all the
        // stages
        // are successfully completed.
        public Operation operation;
        public OkHttpClient.Builder clientBuilder;
        public OkHttpClient httpClient;
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
        Map<String, NicMetadata> networkInterfaceIds = new ConcurrentHashMap<>();
        Map<String, String> computeDescriptionIds = new ConcurrentHashMap<>();
        // Compute States for patching additional fields.
        Map<String, ComputeState> computeStatesForPatching = new ConcurrentHashMap<>();
        List<String> vmIds = new ArrayList<>();
        // Azure specific fields
        ApplicationTokenCredentials credentials;
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

    private static class NicMetadata {
        private NetworkInterfaceState state;
        private String macAddress;
        private String publicIp;
    }

    /**
     * Substages to handle Azure VM data collection.
     */
    private enum ComputeEnumerationSubStages {
        LISTVMS,
        GET_COMPUTE_STATES,
        CREATE_TAG_STATES,
        UPDATE_COMPUTE_STATES,
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

    public AzureComputeEnumerationAdapterService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    /**
     * Constrain every query with endpointLink and tenantLinks, if presented.
     */
    private static void addScopeCriteria(Query.Builder qBuilder,
            Class<? extends ResourceState> stateClass, EnumerationContext ctx) {
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
            switch (ctx.request.enumerationAction) {
            case START:
                logInfo(() -> String.format("Launching Azure compute enumeration for %s",
                        ctx.request.getEnumKey()));
                ctx.enumerationStartTimeInMicros = Utils.getNowMicrosUtc();
                ctx.request.enumerationAction = EnumerationAction.REFRESH;
                handleEnumeration(ctx);
                break;
            case REFRESH:
                ctx.subStage = ComputeEnumerationSubStages.LISTVMS;
                handleSubStage(ctx);
                break;
            case STOP:
                logInfo(() -> String.format("Azure compute enumeration will be stopped for %s",
                        ctx.request.getEnumKey()));
                ctx.stage = EnumerationStages.FINISHED;
                handleEnumeration(ctx);
                break;
            default:
                logSevere(() -> String.format("Unknown Azure enumeration action %s",
                        ctx.request.enumerationAction));
                ctx.stage = EnumerationStages.ERROR;
                handleEnumeration(ctx);
                break;
            }
            break;
        case FINISHED:
            logInfo(() -> String.format("Azure compute enumeration finished for %s",
                    ctx.request.getEnumKey()));
            cleanUpHttpClient(this, ctx.httpClient);
            ctx.operation.complete();
            break;
        case ERROR:
            logWarning(() -> String.format("Azure compute enumeration error for %s",
                    ctx.request.getEnumKey()));
            cleanUpHttpClient(this, ctx.httpClient);
            ctx.operation.fail(ctx.error);
            break;
        default:
            String msg = String.format("Unknown Azure compute enumeration stage %s ",
                    ctx.stage.toString());
            logSevere(() -> msg);
            ctx.error = new IllegalStateException(msg);
            cleanUpHttpClient(this, ctx.httpClient);
            ctx.operation.fail(ctx.error);
        }
    }

    /**
     * Handle enumeration substages for VM data collection.
     */
    private void handleSubStage(EnumerationContext ctx) {
        switch (ctx.subStage) {
        case LISTVMS:
            getVmList(ctx, ComputeEnumerationSubStages.GET_COMPUTE_STATES);
            break;
        case GET_COMPUTE_STATES:
            queryForComputeStates(ctx, ComputeEnumerationSubStages.GET_DISK_STATES);
            break;
        case GET_DISK_STATES:
            queryForDiskStates(ctx, ComputeEnumerationSubStages.CREATE_TAG_STATES);
            break;
        case CREATE_TAG_STATES:
            createTagStates(ctx, ComputeEnumerationSubStages.CREATE_NETWORK_INTERFACE_STATES);
            break;
        case CREATE_NETWORK_INTERFACE_STATES:
            createNetworkInterfaceStates(ctx, ComputeEnumerationSubStages.UPDATE_DISK_STATES);
            break;
        case UPDATE_DISK_STATES:
            updateDiskStates(ctx, ComputeEnumerationSubStages.UPDATE_COMPUTE_STATES);
            break;
        case UPDATE_COMPUTE_STATES:
            updateComputeStates(ctx, ComputeEnumerationSubStages.CREATE_COMPUTE_DESCRIPTIONS);
            break;
        case CREATE_COMPUTE_DESCRIPTIONS:
            createComputeDescriptions(ctx,
                    ComputeEnumerationSubStages.GET_STORAGE_DESCRIPTIONS);
            break;
        case GET_STORAGE_DESCRIPTIONS:
            queryForDiagnosticStorageDescriptions(ctx,
                    ComputeEnumerationSubStages.CREATE_COMPUTE_STATES);
            break;
        case CREATE_COMPUTE_STATES:
            createComputeStates(ctx, ComputeEnumerationSubStages.PATCH_ADDITIONAL_FIELDS);
            break;
        case PATCH_ADDITIONAL_FIELDS:
            patchAdditionalFields(ctx, ComputeEnumerationSubStages.DELETE_COMPUTE_STATES);
            break;
        case DELETE_COMPUTE_STATES:
            deleteComputeStates(ctx, ComputeEnumerationSubStages.FINISHED);
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

    /**
     * {@code handleSubStage} version suitable for chaining to {@code DeferredResult.whenComplete}.
     */
    private <T> BiConsumer<T, Throwable> thenHandleSubStage(EnumerationContext context,
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
    private void deleteComputeStates(EnumerationContext ctx, ComputeEnumerationSubStages next) {
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
                        ctx.subStage = next;
                        handleSubStage(ctx);
                        return;
                    }

                    ctx.deletionNextPageLink = queryTask.results.nextPageLink;
                    deleteOrRetireHelper(ctx, next);
                });
    }

    /**
     * Helper method to paginate through resources to be deleted.
     */
    private void deleteOrRetireHelper(EnumerationContext ctx, ComputeEnumerationSubStages next) {
        if (ctx.deletionNextPageLink == null) {
            logFine(() -> String.format("Finished %s of compute states for Azure",
                    ctx.request.preserveMissing ? "retiring" : "deletion"));
            ctx.subStage = next;
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
                deleteOrRetireHelper(ctx, next);
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

                        deleteOrRetireHelper(ctx, next);
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
    private void getVmList(EnumerationContext ctx, ComputeEnumerationSubStages next) {
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

        ctx.virtualMachines.clear();
        operation.setCompletion((op, er) -> {
            if (er != null) {
                op.complete();
                handleError(ctx, er);
                return;
            }

            VirtualMachineListResult results = op.getBody(VirtualMachineListResult.class);
            op.complete();

            List<VirtualMachine> virtualMachines = results.value;

            // If there are no VMs in Azure we directly skip over to deletion phase.
            if (virtualMachines == null || virtualMachines.size() == 0) {
                ctx.subStage = ComputeEnumerationSubStages.DELETE_COMPUTE_STATES;
                handleSubStage(ctx);
                return;
            }

            ctx.enumNextPageLink = results.nextLink;

            logInfo(() -> String.format("Retrieved %d VMs from Azure", virtualMachines.size()));
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

            ctx.subStage = next;
            handleSubStage(ctx);

        });
        sendRequest(operation);
    }

    /**
     * Query all compute states for the cluster filtered by the received set of instance Ids.
     */
    private void queryForComputeStates(EnumerationContext ctx, ComputeEnumerationSubStages next) {
        if (ctx.virtualMachines.isEmpty()) {
            ctx.subStage = ComputeEnumerationSubStages.DELETE_COMPUTE_STATES;
            handleSubStage(ctx);
            return;
        }

        Query.Builder qBuilder = Query.Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK, ctx.request.resourceLink());

        Query.Builder instanceIdFilterParentQuery = Query.Builder.create(Occurance.MUST_OCCUR);

        for (String instanceId : ctx.virtualMachines.keySet()) {
            Query instanceIdFilter = Query.Builder.create(Occurance.SHOULD_OCCUR)
                    .addFieldClause(ComputeState.FIELD_NAME_ID, instanceId)
                    .build();
            instanceIdFilterParentQuery.addClause(instanceIdFilter);
        }

        qBuilder.addClause(instanceIdFilterParentQuery.build());

        QueryStrategy<ComputeState> queryLocalStates = new QueryByPages<ComputeState>(
                getHost(),
                qBuilder.build(),
                ComputeState.class,
                ctx.parentCompute.tenantLinks,
                ctx.request.endpointLink)
                        .setMaxPageSize(getQueryResultLimit());

        queryLocalStates.queryDocuments(c -> {
            ctx.computeStates.put(c.id, c);
        })
                .whenComplete(thenHandleSubStage(ctx, next));
    }

    /**
     * Create tag states for the VM's tags
     */
    private void createTagStates(EnumerationContext context, ComputeEnumerationSubStages next) {
        logFine(() -> "Create or update Tag States for discovered VMs with the actual state in Azure.");

        if (context.virtualMachines.isEmpty()) {
            logFine("No VMs found, so no tags need to be created/updated. Continue to update compute states.");
            context.subStage = ComputeEnumerationSubStages.DELETE_COMPUTE_STATES;
            handleSubStage(context);
            return;
        }

        // POST each of the tags. If a tag exists it won't be created again. We don't want the name
        // tags, so filter them out
        List<DeferredResult<Operation>> operations = context.virtualMachines
                .values()
                .stream().filter(vm -> vm.tags != null && !vm.tags.isEmpty())
                .flatMap(vm -> vm.tags.entrySet().stream())
                .map(entry -> newExternalTagState(entry.getKey(), entry.getValue(),
                        context.parentCompute.tenantLinks))
                .map(tagState -> sendWithDeferredResult(Operation
                        .createPost(context.request.buildUri(TagService.FACTORY_LINK))
                        .setBody(tagState)))
                .collect(java.util.stream.Collectors.toList());

        if (operations.isEmpty()) {
            context.subStage = next;
            handleSubStage(context);
        } else {
            DeferredResult.allOf(operations).whenComplete(thenHandleSubStage(context, next));
        }
    }

    /**
     * Updates matching compute states for given VMs.
     */
    private void updateComputeStates(EnumerationContext ctx, ComputeEnumerationSubStages next) {
        if (ctx.computeStates.isEmpty()) {
            logFine(() -> "No compute states found to be updated.");
            ctx.subStage = next;
            handleSubStage(ctx);
            return;
        }

        DeferredResult.allOf(ctx.computeStates.values().stream().map(c -> {
            VirtualMachine virtualMachine = ctx.virtualMachines.remove(c.id);
            return Pair.of(c, virtualMachine);
        })
                .map(p -> {
                    ComputeState cs = p.getLeft();
                    Map<String, String> tags = p.getRight().tags;
                    DeferredResult<Set<String>> result = DeferredResult.completed(null);
                    if (tags != null && !tags.isEmpty()) {
                        Set<String> tagLinks = cs.tagLinks;
                        cs.tagLinks = null;
                        result = updateLocalTagStates(this, cs, tagLinks, tags);
                    }
                    ctx.computeStatesForPatching.put(cs.id, cs);
                    return result;
                })
                .collect(Collectors.toList())).whenComplete(thenHandleSubStage(ctx, next));
    }

    /**
     * Get all disk states related to given VMs
     */
    private void queryForDiskStates(EnumerationContext ctx, ComputeEnumerationSubStages next) {
        if (ctx.virtualMachines.size() == 0) {
            logFine(() -> "No virtual machines found to be associated with local disks");
            ctx.subStage = ComputeEnumerationSubStages.DELETE_COMPUTE_STATES;
            handleSubStage(ctx);
            return;
        }
        ctx.diskStates.clear();

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
            ctx.subStage = next;
            handleSubStage(ctx);
            return;
        }

        diskUriFilters.stream()
                .forEach(diskUriFilter -> diskUriFilterParentQuery.addClause(diskUriFilter));
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

                    if (queryTask.results.documentCount == 0) {
                        ctx.subStage = next;
                        handleSubStage(ctx);
                        return;
                    }

                    for (Object d : queryTask.results.documents.values()) {
                        DiskState diskState = Utils.fromJson(d, DiskState.class);
                        String diskUri = diskState.id;
                        ctx.diskStates.put(diskUri, diskState);
                    }

                    ctx.subStage = next;
                    handleSubStage(ctx);
                });
    }

    /**
     * Get all storage descriptions responsible for diagnostics of given VMs.
     */
    private void queryForDiagnosticStorageDescriptions(EnumerationContext ctx,
            ComputeEnumerationSubStages next) {
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
            ctx.subStage = next;
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
                        ctx.subStage = next;
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

                    ctx.subStage = next;
                    handleSubStage(ctx);
                });
    }

    /**
     * Creates relevant resources for given VMs.
     */
    private void createComputeDescriptions(EnumerationContext ctx,
            ComputeEnumerationSubStages next) {
        if (ctx.virtualMachines.size() == 0) { // nothing to create
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
                    ctx.subStage = next;
                    handleSubStage(ctx);
                }).sendWith(this);
    }

    /**
     * Update disk states with additional custom properties
     */
    private void updateDiskStates(EnumerationContext ctx, ComputeEnumerationSubStages next) {
        if (ctx.virtualMachines.isEmpty()) {
            logFine(() -> "No virtual machines found to be associated with local disks");
            ctx.subStage = ComputeEnumerationSubStages.DELETE_COMPUTE_STATES;
            handleSubStage(ctx);
            return;
        }
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
            Operation diskOp = Operation
                    .createPatch(ctx.request.buildUri(diskToUpdate.documentSelfLink))
                    .setBody(diskToUpdate);
            opCollection.add(diskOp);
        }

        if (opCollection.isEmpty()) {
            logFine(() -> "No local disk states fount to update.");
            ctx.subStage = next;
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

                    logFine(() -> "Continue on to create network interfaces.");
                    ctx.subStage = next;
                    handleSubStage(ctx);

                }).sendWith(this);
    }

    /**
     * Create network interface states for each VM
     */
    private void createNetworkInterfaceStates(EnumerationContext ctx,
            ComputeEnumerationSubStages next) {

        NetworkManagementClient client = getNetworkManagementClient(ctx);
        NetworkInterfacesOperations netOps = client.getNetworkInterfacesOperations();

        List<DeferredResult<Pair<NetworkInterface, String>>> remoteNics = ctx.virtualMachines
                .values().stream()
                .filter(vm -> vm.properties.networkProfile != null && !vm.properties.networkProfile
                        .getNetworkInterfaces().isEmpty())
                .flatMap(vm -> vm.properties.networkProfile.getNetworkInterfaces().stream()
                        .map(nic -> Pair.of(nic, vm.id)))
                .map(pair -> loadRemoteNic(pair, netOps))
                .collect(Collectors.toList());

        DeferredResult.allOf(remoteNics)
                .thenCompose(rnics -> {
                    return loadSubnets(ctx, rnics)
                            .thenCompose(subnetPerNicId -> doCreateOrUpdateNics(ctx, subnetPerNicId,
                                    rnics));
                }).whenComplete(thenHandleSubStage(ctx, next));

    }

    private DeferredResult<List<NetworkInterfaceState>> doCreateOrUpdateNics(EnumerationContext ctx,
            Map<String, String> subnetPerNicId,
            List<Pair<NetworkInterface, String>> rnics) {
        Map<String, Pair<NetworkInterface, String>> remoteStates = rnics.stream()
                .collect(Collectors.toMap(p -> p.getLeft().getId(), p -> p));
        Query.Builder qBuilder = Query.Builder.create()
                .addKindFieldClause(NetworkInterfaceState.class)
                .addInClause(NetworkInterfaceState.FIELD_NAME_ID,
                        rnics.stream().map(p -> p.getLeft().getId()).collect(Collectors.toList()));

        QueryByPages<NetworkInterfaceState> queryLocalStates = new QueryByPages<>(getHost(),
                qBuilder.build(),
                NetworkInterfaceState.class, ctx.parentCompute.tenantLinks,
                ctx.request.endpointLink)
                        .setMaxPageSize(getQueryResultLimit());

        return queryLocalStates
                .collectDocuments(Collectors.toList())
                .thenCompose(lnics -> {
                    List<DeferredResult<NetworkInterfaceState>> ops = new java.util.ArrayList<>();
                    lnics.stream().forEach(nic -> {
                        Pair<NetworkInterface, String> pair = remoteStates.remove(nic.id);
                        NetworkInterface rnic = pair.getLeft();

                        nic.name = rnic.getName();
                        nic.subnetLink = subnetPerNicId.get(rnic.getId());

                        NicMetadata nicMeta = new NicMetadata();
                        nicMeta.state = nic;
                        nicMeta.macAddress = rnic.getMacAddress();

                        List<NetworkInterfaceIPConfiguration> ipConfigurations = rnic
                                .getIpConfigurations();
                        if (ipConfigurations != null && !ipConfigurations.isEmpty()) {
                            NetworkInterfaceIPConfiguration nicIPConf = ipConfigurations.get(0);
                            nic.address = nicIPConf.getPrivateIPAddress();
                            nicMeta.publicIp = nicIPConf.getPublicIPAddress() != null
                                    ? nicIPConf.getPublicIPAddress().getIpAddress() : null;
                        }

                        ops.add(sendWithDeferredResult(Operation
                                .createPatch(ctx.request.buildUri(nic.documentSelfLink))
                                .setBody(nic), NetworkInterfaceState.class)
                                        .thenApply(r -> {
                                            ctx.networkInterfaceIds.put(rnic.getId(), nicMeta);
                                            return r;
                                        }));
                    });

                    remoteStates.values().stream().forEach(p -> {
                        NetworkInterface rnic = p.getLeft();
                        NetworkInterfaceState state = new NetworkInterfaceState();
                        state.id = rnic.getId();
                        state.name = rnic.getName();
                        state.subnetLink = subnetPerNicId.get(rnic.getId());
                        state.endpointLink = ctx.request.endpointLink;
                        state.tenantLinks = ctx.parentCompute.tenantLinks;

                        NicMetadata nicMeta = new NicMetadata();
                        nicMeta.macAddress = rnic.getMacAddress();

                        List<NetworkInterfaceIPConfiguration> ipConfigurations = rnic
                                .getIpConfigurations();
                        if (ipConfigurations != null && !ipConfigurations.isEmpty()) {
                            NetworkInterfaceIPConfiguration nicIPConf = ipConfigurations.get(0);
                            state.address = nicIPConf.getPrivateIPAddress();
                            nicMeta.publicIp = nicIPConf.getPublicIPAddress() != null
                                    ? nicIPConf.getPublicIPAddress().getIpAddress() : null;
                        }

                        ops.add(sendWithDeferredResult(Operation
                                .createPost(
                                        ctx.request.buildUri(NetworkInterfaceService.FACTORY_LINK))
                                .setBody(state), NetworkInterfaceState.class)
                                        .thenApply(nic -> {
                                            nicMeta.state = nic;
                                            ctx.networkInterfaceIds.put(rnic.getId(), nicMeta);
                                            return nic;
                                        }));
                    });
                    return DeferredResult.allOf(ops);
                });
    }

    private DeferredResult<Pair<NetworkInterface, String>> loadRemoteNic(
            Pair<NetworkInterfaceReference, String> pair, NetworkInterfacesOperations netOps) {
        AzureDeferredResultServiceCallback<NetworkInterface> handler = new AzureDeferredResultServiceCallback<NetworkInterface>(
                this, "Load Nic:" + pair.getLeft().getId()) {
            @Override
            protected DeferredResult<NetworkInterface> consumeSuccess(
                    NetworkInterface nic) {
                return DeferredResult.completed(nic);
            }
        };
        String networkInterfaceName = UriUtils.getLastPathSegment(pair.getLeft().getId());
        String nicRG = getResourceGroupName(pair.getLeft().getId());
        String resourceGroupName = getResourceGroupName(pair.getRight());
        if (!resourceGroupName.equalsIgnoreCase(nicRG)) {
            logWarning(
                    "VM resource group %s is different from nic resource group %s, for nic %s",
                    resourceGroupName, nicRG, pair.getLeft().getId());
        }
        netOps.getAsync(resourceGroupName, networkInterfaceName, "ipConfigurations/publicIPAddress",
                handler);
        return handler.toDeferredResult()
                .thenApply(loaded -> Pair.of(loaded, pair.getRight()));
    }

    private DeferredResult<Map<String, String>> loadSubnets(EnumerationContext ctx,
            List<Pair<NetworkInterface, String>> rnics) {
        Map<String, List<Pair<NetworkInterface, String>>> nicsPerSubnet = rnics.stream()
                .filter(p -> p.getLeft().getIpConfigurations() != null && !p.getLeft()
                        .getIpConfigurations().isEmpty())
                .filter(p -> p.getLeft().getIpConfigurations().get(0).getSubnet() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        p -> p.getLeft().getIpConfigurations().get(0).getSubnet().getId()));

        Query.Builder qBuilder = Query.Builder.create()
                .addKindFieldClause(SubnetState.class)
                .addInClause(NetworkInterfaceState.FIELD_NAME_ID,
                        nicsPerSubnet.keySet().stream().collect(Collectors.toList()));

        QueryByPages<SubnetState> queryLocalStates = new QueryByPages<>(getHost(),
                qBuilder.build(),
                SubnetState.class, ctx.parentCompute.tenantLinks,
                ctx.request.endpointLink)
                        .setMaxPageSize(getQueryResultLimit());
        Map<String, String> subnetLinkPerNicId = new HashMap<>();
        return queryLocalStates
                .queryDocuments(subnet -> {
                    nicsPerSubnet.get(subnet.id).forEach(p -> subnetLinkPerNicId
                            .put(p.getLeft().getId(), subnet.documentSelfLink));
                }).thenApply(ignore -> {
                    return subnetLinkPerNicId;
                });
    }

    /**
     * Create compute state for each VM
     */
    private void createComputeStates(EnumerationContext ctx, ComputeEnumerationSubStages next) {
        if (ctx.virtualMachines.isEmpty()) {
            logFine(() -> "No computes to create.");
            ctx.subStage = next;
            handleSubStage(ctx);
            return;
        }

        List<DeferredResult<ComputeState>> results = ctx.virtualMachines
                .values().stream()
                .map(vm -> createComputeState(ctx, vm))
                .map(computeState -> sendWithDeferredResult(Operation.createPost(
                        ctx.request.buildUri(ComputeService.FACTORY_LINK))
                        .setBody(computeState), ComputeState.class)
                                .thenApply(cs -> {
                                    ctx.computeStatesForPatching.put(cs.id, cs);
                                    return cs;
                                }))
                .collect(java.util.stream.Collectors.toList());

        DeferredResult.allOf(results).whenComplete((all, e) -> {
            if (e != null) {
                logWarning(() -> String.format("Error: %s", e));
            }

            if (ctx.enumNextPageLink != null) {
                ctx.subStage = ComputeEnumerationSubStages.LISTVMS;
                handleSubStage(ctx);
                return;
            }

            logFine(() -> "Finished creating compute states.");
            ctx.subStage = next;
            handleSubStage(ctx);
        });
    }

    private ComputeState createComputeState(EnumerationContext ctx, VirtualMachine virtualMachine) {

        List<String> vmDisks = new ArrayList<>();
        if (ctx.diskStates != null && ctx.diskStates.size() > 0) {
            String diskUri = getVhdUri(virtualMachine);
            if (diskUri != null) {
                DiskState state = ctx.diskStates.remove(diskUri);
                if (state != null) {
                    vmDisks.add(state.documentSelfLink);
                }
            }
        }

        // Create compute state
        ComputeState computeState = new ComputeState();
        computeState.documentSelfLink = UUID.randomUUID().toString();
        computeState.creationTimeMicros = Utils.getNowMicrosUtc();
        computeState.id = virtualMachine.id.toLowerCase();
        computeState.name = virtualMachine.name;
        computeState.regionId = virtualMachine.location;

        computeState.type = ComputeType.VM_GUEST;
        computeState.environmentName = ComputeDescription.ENVIRONMENT_NAME_AZURE;
        computeState.parentLink = ctx.request.resourceLink();
        computeState.descriptionLink = UriUtils
                .buildUriPath(ComputeDescriptionService.FACTORY_LINK,
                        ctx.computeDescriptionIds.get(virtualMachine.name));
        computeState.endpointLink = ctx.request.endpointLink;
        computeState.resourcePoolLink = ctx.request.resourcePoolLink;
        computeState.diskLinks = vmDisks;
        computeState.instanceType = virtualMachine.properties.hardwareProfile.getVmSize();

        computeState.customProperties = new HashMap<>();
        computeState.customProperties.put(CUSTOM_OS_TYPE, getNormalizedOSType(virtualMachine));

        String resourceGroupName = getResourceGroupName(virtualMachine.id);
        computeState.customProperties.put(AZURE_RESOURCE_GROUP_NAME, resourceGroupName);

        if (virtualMachine.properties.diagnosticsProfile != null) {
            String diagnosticsAccountUri = virtualMachine.properties.diagnosticsProfile
                    .getBootDiagnostics().getStorageUri();
            StorageDescription storageDesk = ctx.storageDescriptions.get(diagnosticsAccountUri);
            if (storageDesk != null) {
                computeState.customProperties.put(AZURE_DIAGNOSTIC_STORAGE_ACCOUNT_LINK,
                        storageDesk.documentSelfLink);
            }
        }
        // add tag links
        setTagLinksToResourceState(computeState, virtualMachine.tags);
        computeState.tenantLinks = ctx.parentCompute.tenantLinks;

        List<String> networkLinks = new ArrayList<>();
        NicMetadata nicMeta = ctx.networkInterfaceIds
                .remove(virtualMachine.properties.networkProfile.getNetworkInterfaces().get(0)
                        .getId());
        if (nicMeta != null) {
            computeState.address = nicMeta.publicIp;
            computeState.primaryMAC = nicMeta.macAddress;
            networkLinks.add(nicMeta.state.documentSelfLink);
        }
        computeState.networkInterfaceLinks = networkLinks;

        return computeState;
    }

    private void patchAdditionalFields(EnumerationContext ctx, ComputeEnumerationSubStages next) {
        if (ctx.computeStatesForPatching.size() == 0) {
            logFine(() -> "No compute states need to be patched.");
            ctx.subStage = next;
            handleSubStage(ctx);
            return;
        }

        // Patching power state and network Information. Hence 2.
        // If we patch more fields, this number should be increased accordingly.
        ComputeManagementClient computeClient = getComputeManagementClient(ctx);
        VirtualMachinesOperations vmOps = computeClient
                .getVirtualMachinesOperations();
        DeferredResult.allOf(ctx.computeStatesForPatching
                .values().stream()
                .map(c -> patchVMInstanceDetails(ctx, vmOps, c))
                .map(dr -> dr.thenCompose(c -> sendWithDeferredResult(
                        Operation.createPatch(ctx.request.buildUri(c.documentSelfLink)).setBody(c)
                                .setCompletion((o, e) -> {
                                    if (e != null) {
                                        logWarning(() -> String.format(
                                                "Error updating VM:[%s], reason: %s", c.name, e));
                                    }
                                }))))
                .collect(Collectors.toList()))
                .whenComplete((all, e) -> {
                    if (e != null) {
                        logWarning(() -> String.format("Error: %s", e));
                    }
                    ctx.subStage = next;
                    handleSubStage(ctx);
                });

    }

    private DeferredResult<ComputeState> patchVMInstanceDetails(EnumerationContext ctx,
            VirtualMachinesOperations vmOps, ComputeState computeState) {

        String resourceGroupName = getResourceGroupName(computeState.id);
        String vmName = computeState.name;
        AzureDeferredResultServiceCallback<com.microsoft.azure.management.compute.models.VirtualMachine> handler = new AzureDeferredResultServiceCallback<com.microsoft.azure.management.compute.models.VirtualMachine>(
                this, "Load virtual machine instance view:" + vmName) {
            @Override
            protected DeferredResult<com.microsoft.azure.management.compute.models.VirtualMachine> consumeSuccess(
                    com.microsoft.azure.management.compute.models.VirtualMachine vm) {
                logFine(() -> String.format("Retrieved instance view for vm [%s].", vmName));
                return DeferredResult.completed(vm);
            }
        };
        vmOps.getAsync(resourceGroupName, vmName, EXPAND_INSTANCE_VIEW_PARAM, handler);
        return handler.toDeferredResult().thenApply(vm -> {
            for (InstanceViewStatus status : vm.getInstanceView().getStatuses()) {
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
            if (computeState.customProperties == null) {
                computeState.customProperties = new HashMap<>();
            }
            computeState.customProperties.put(RESOURCE_GROUP_NAME, resourceGroupName);

            computeState.type = ComputeType.VM_GUEST;
            computeState.environmentName = ComputeDescription.ENVIRONMENT_NAME_AZURE;
            return computeState;
        });
    }

    private void handleError(EnumerationContext ctx, Throwable e) {
        logSevere(() -> String.format("Failed at stage %s and sub-stage %s with exception: %s",
                ctx.stage, ctx.subStage, Utils.toString(e)));
        ctx.error = e;
        ctx.stage = EnumerationStages.ERROR;
        handleEnumeration(ctx);
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
            logWarning(String.format(
                    "Enumeration failed. VM %s has a ManagedDisk configuration, which is currently not supported.",
                    vm.id));
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
}
