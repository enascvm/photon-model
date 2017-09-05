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
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_DIAGNOSTIC_STORAGE_ACCOUNT_LINK;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_OSDISK_CACHING;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_RESOURCE_GROUP_NAME;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNT_URI;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_TENANT_ID;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AzureResourceType.azure_net_interface;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AzureResourceType.azure_vm;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.getQueryResultLimit;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.buildRestClient;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.cleanUpHttpClient;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.getAzureConfig;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.getResourceGroupName;
import static com.vmware.photon.controller.model.adapters.util.TagsUtil.newTagState;
import static com.vmware.photon.controller.model.adapters.util.TagsUtil.setTagLinksToResourceState;
import static com.vmware.photon.controller.model.adapters.util.TagsUtil.updateLocalTagStates;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.CUSTOM_PROP_ENDPOINT_LINK;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.TAG_KEY_TYPE;
import static com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ENVIRONMENT_NAME_AZURE;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.microsoft.azure.Page;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.compute.InstanceViewStatus;
import com.microsoft.azure.management.compute.InstanceViewTypes;
import com.microsoft.azure.management.compute.OperatingSystemTypes;
import com.microsoft.azure.management.compute.implementation.ImageReferenceInner;
import com.microsoft.azure.management.compute.implementation.NetworkInterfaceReferenceInner;
import com.microsoft.azure.management.compute.implementation.VirtualMachineInner;
import com.microsoft.azure.management.compute.implementation.VirtualMachinesInner;
import com.microsoft.azure.management.network.PublicIPAddress;
import com.microsoft.azure.management.network.implementation.NetworkInterfaceIPConfigurationInner;
import com.microsoft.azure.management.network.implementation.NetworkInterfaceInner;
import com.microsoft.azure.management.network.implementation.NetworkInterfacesInner;

import com.microsoft.rest.RestClient;

import org.apache.commons.lang3.tuple.Pair;

import rx.functions.Action1;

import com.vmware.photon.controller.model.ComputeProperties.OSType;
import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureDeferredResultServiceCallback;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.ComputeEnumerateAdapterRequest;
import com.vmware.photon.controller.model.adapters.util.enums.EnumerationStages;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
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
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.photon.controller.model.resources.util.PhotonModelUtils;
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
    private ExecutorService executorService;
    private static final String NETWORK_INTERFACE_TAG_TYPE_VALUE = azure_net_interface.toString();

    /**
     * The enumeration service context that holds all the information needed to determine the list
     * of instances that need to be represented in the system.
     */
    private static class EnumerationContext {
        // Stored operation to signal completion to the Azure storage enumeration once all the
        // stages
        // are successfully completed.
        public Operation operation;
        // maintains internal tagLinks, for example: link for tagState for type=azure_vm
        public Set<String> internalTagLinks = new HashSet<>();
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
        Map<String, VirtualMachineInner> virtualMachines = new ConcurrentHashMap<>();
        Map<String, ComputeState> computeStates = new ConcurrentHashMap<>();
        Map<String, DiskState> diskStates = new ConcurrentHashMap<>();
        Map<String, StorageDescription> storageDescriptions = new ConcurrentHashMap<>();
        Map<String, NicMetadata> networkInterfaceIds = new ConcurrentHashMap<>();
        Map<String, String> computeDescriptionIds = new ConcurrentHashMap<>();
        // stores a mapping of all internal tags for network interfaces.
        Map<String, String> nicInternalTagsMap = new ConcurrentHashMap<>();
        // stores documentSelfLink for network interfaces internal tags
        Set<String> nicInternalTagLinksSet = new HashSet<>();
        // Compute States for patching additional fields.
        Map<String, ComputeState> computeStatesForPatching = new ConcurrentHashMap<>();
        List<String> vmIds = new ArrayList<>();
        // Azure specific fields
        ApplicationTokenCredentials credentials;
        // Azure clients
        Azure azure;
        RestClient restClient;

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
        private String publicDnsName;
    }

    /**
     * Substages to handle Azure VM data collection.
     */
    private enum ComputeEnumerationSubStages {
        LISTVMS,
        GET_COMPUTE_STATES,
        CREATE_COMPUTE_EXTERNAL_TAG_STATES,
        CREATE_COMPUTE_INTERNAL_TYPE_TAG,
        UPDATE_COMPUTE_STATES,
        GET_DISK_STATES,
        GET_STORAGE_DESCRIPTIONS,
        CREATE_COMPUTE_DESCRIPTIONS,
        UPDATE_DISK_STATES,
        CREATE_NETWORK_INTERFACE_INTERNAL_TAG_STATES,
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
            Class<? extends ResourceState> stateClass,
            EnumerationContext ctx) {

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
            cleanUpHttpClient(ctx.restClient.httpClient());
            ctx.operation.complete();
            break;
        case ERROR:
            logWarning(() -> String
                    .format("Azure compute enumeration error for %s, Failed due to %s",
                            ctx.request.getEnumKey(), Utils.toString(ctx.error)));
            cleanUpHttpClient(ctx.restClient.httpClient());
            ctx.operation.fail(ctx.error);
            break;
        default:
            String msg = String.format("Unknown Azure compute enumeration stage %s ",
                    ctx.stage.toString());
            logSevere(() -> msg);
            ctx.error = new IllegalStateException(msg);
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
            queryForDiskStates(ctx, ComputeEnumerationSubStages.CREATE_COMPUTE_INTERNAL_TYPE_TAG);
            break;
        case CREATE_COMPUTE_INTERNAL_TYPE_TAG:
            createInternalTypeTag(ctx, ComputeEnumerationSubStages.CREATE_COMPUTE_EXTERNAL_TAG_STATES);
            break;
        case CREATE_COMPUTE_EXTERNAL_TAG_STATES:
            createTagStates(ctx,
                    ComputeEnumerationSubStages.CREATE_NETWORK_INTERFACE_INTERNAL_TAG_STATES);
            break;
        case CREATE_NETWORK_INTERFACE_INTERNAL_TAG_STATES:
            createNetworkInterfaceInternalTagStates(ctx,
                    ComputeEnumerationSubStages.CREATE_NETWORK_INTERFACE_STATES);
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
            // if exception occurs at one of the sub stages, don't error out and leave enumeration
            // incomplete, instead move to the next sub stage.
            if (exc != null) {
                logWarning("Resource enumeration failed at stage %s with exception %s",
                        context.stage, Utils.toString(exc));
            }
            context.subStage = next;
            handleSubStage(context);
        };
    }

    private void createInternalTypeTag(EnumerationContext context, ComputeEnumerationSubStages next) {
        TagService.TagState typeTag = newTagState(TAG_KEY_TYPE, azure_vm.toString(),
                false, context.parentCompute.tenantLinks);

        Operation.CompletionHandler handler = (completedOp, failure) -> {
            if (failure == null) {
                // if no error, store the internal tag into context
                context.internalTagLinks.add(typeTag.documentSelfLink);
            } else {
                // log the error and continue the enumeration
                logWarning(() -> String.format("Error creating internal tag: %s", failure.getMessage()));
            }
            context.subStage = next;
            handleSubStage(context);
        };

        sendRequest(Operation.createPost(this, TagService.FACTORY_LINK)
                .setBody(typeTag)
                .setCompletion(handler));
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
        Consumer<Throwable> failure = e -> {
            logWarning("Failure retrieving Azure VMs for [endpoint=%s] [Exception:%s]",
                    ctx.request.endpointLink, e.getMessage());
            handleError(ctx, e);
            return;
        };

        PhotonModelUtils.runInExecutor(this.executorService, () -> {
            logFine(() -> "Enumerating VMs from Azure");

            Azure azureClient = getAzureClient(ctx);

            ctx.virtualMachines.clear();

            if (ctx.enumNextPageLink == null) {
                azureClient.virtualMachines().inner().listAsync()
                        .subscribe(vmEnumerationCompletion(ctx, next));
            } else {
                azureClient.virtualMachines().inner().listNextAsync(ctx.enumNextPageLink)
                        .subscribe(vmEnumerationCompletion(ctx, next));
            }
        }, failure);
    }

    /**
     * Completion handler for VM enumeration call.
     * For async calls to list VMs, Azure returns Observable<Page<VirtualMachineInner>>. The following
     * completion subscribes to the Observable and Overrides call(<T>) to include logic to process pages of VMs
     * when we receive them.
     */
    private Action1<Page<VirtualMachineInner>> vmEnumerationCompletion(EnumerationContext ctx,
                                                                    ComputeEnumerationSubStages next) {
        Action1<Page<VirtualMachineInner>> enumerationCompletion = new Action1<Page<VirtualMachineInner>>() {
            @Override
            public void call(Page<VirtualMachineInner> virtualMachineInnerPage) {
                List<VirtualMachineInner> virtualMachineInners = virtualMachineInnerPage.items();
                ctx.enumNextPageLink = virtualMachineInnerPage.nextPageLink();

                if (virtualMachineInners == null || virtualMachineInners.size() == 0) {
                    ctx.subStage = ComputeEnumerationSubStages.DELETE_COMPUTE_STATES;
                    handleSubStage(ctx);
                    return;
                }

                logInfo(() -> String.format("Retrieved %d VMs from Azure", virtualMachineInners.size()));
                logFine(() -> String.format("Next page link %s", ctx.enumNextPageLink));

                for (VirtualMachineInner virtualMachine : virtualMachineInners) {
                    // We don't want to process VMs that are being terminated.
                    if (AZURE_VM_TERMINATION_STATES.contains(virtualMachine.provisioningState())) {
                        logFine(() -> String.format("Not processing %s", virtualMachine.id()));
                        continue;
                    }
                    // Azure for some case changes the case of the vm id.
                    String vmId = virtualMachine.id().toLowerCase();
                    ctx.virtualMachines.put(vmId, virtualMachine);
                    ctx.vmIds.add(vmId);
                }

                logFine(() -> String.format("Processing %d VMs", ctx.vmIds.size()));

                ctx.subStage = next;
                handleSubStage(ctx);
            }
        };

        return enumerationCompletion;
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
        }).whenComplete(thenHandleSubStage(ctx, next));
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
                .stream().filter(vm -> vm.getTags() != null && !vm.getTags().isEmpty())
                .flatMap(vm -> vm.getTags().entrySet().stream())
                .map(entry -> newTagState(entry.getKey(), entry.getValue(), true,
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
            VirtualMachineInner virtualMachine = ctx.virtualMachines.remove(c.id);
            return Pair.of(c, virtualMachine);
        })
                .map(p -> {
                    ComputeState cs = p.getLeft();
                    Map<String, String> tags = p.getRight().getTags();
                    DeferredResult<Set<String>> result = DeferredResult.completed(null);
                    if (tags != null && !tags.isEmpty()) {
                        Set<String> tagLinks = cs.tagLinks;
                        cs.tagLinks = null;
                        result = updateLocalTagStates(this, cs, tagLinks, tags);
                    }
                    // add internal type tags
                    if (cs.tagLinks == null) {
                        cs.tagLinks = new HashSet<>();
                    }
                    cs.tagLinks.addAll(ctx.internalTagLinks);
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
                .get(instanceId).diagnosticsProfile() != null)
                .forEach(instanceId -> {
                    String diagnosticStorageAccountUri = ctx.virtualMachines
                            .get(instanceId).diagnosticsProfile().bootDiagnostics()
                                    .storageUri();

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

        Iterator<Entry<String, VirtualMachineInner>> iterator = ctx.virtualMachines.entrySet()
                .iterator();

        Collection<Operation> opCollection = new ArrayList<>();
        while (iterator.hasNext()) {
            Entry<String, VirtualMachineInner> vmEntry = iterator.next();
            VirtualMachineInner virtualMachine = vmEntry.getValue();

            AuthCredentialsServiceState auth = new AuthCredentialsServiceState();
            if (virtualMachine.osProfile() != null) {
                auth.userEmail = virtualMachine.osProfile().adminUsername();
                auth.privateKey = virtualMachine.osProfile().adminPassword();
            }
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
            computeDescription.name = virtualMachine.name();
            computeDescription.regionId = virtualMachine.location();
            computeDescription.authCredentialsLink = authLink;
            computeDescription.endpointLink = ctx.request.endpointLink;
            computeDescription.documentSelfLink = computeDescription.id;
            computeDescription.environmentName = ENVIRONMENT_NAME_AZURE;
            if (virtualMachine.hardwareProfile() != null
                    && virtualMachine.hardwareProfile().vmSize() != null) {
                computeDescription.instanceType = virtualMachine.hardwareProfile().vmSize().toString();
            }
            computeDescription.instanceAdapterReference = ctx.parentCompute.description.instanceAdapterReference;
            computeDescription.statsAdapterReference = ctx.parentCompute.description.statsAdapterReference;
            computeDescription.diskAdapterReference = ctx.parentCompute.description.diskAdapterReference;
            computeDescription.customProperties = new HashMap<>();

            // TODO: https://jira-hzn.eng.vmware.com/browse/VSYM-1268
            String resourceGroupName = getResourceGroupName(virtualMachine.id());
            computeDescription.customProperties.put(AZURE_RESOURCE_GROUP_NAME,
                    resourceGroupName);
            computeDescription.tenantLinks = ctx.parentCompute.tenantLinks;

            Operation compDescOp = Operation
                    .createPost(getHost(), ComputeDescriptionService.FACTORY_LINK)
                    .setBody(computeDescription);
            ctx.computeDescriptionIds.put(virtualMachine.name(), computeDescription.id);
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
        Iterator<Entry<String, VirtualMachineInner>> iterator = ctx.virtualMachines.entrySet()
                .iterator();

        Collection<Operation> opCollection = new ArrayList<>();
        while (iterator.hasNext()) {
            Entry<String, VirtualMachineInner> vmEntry = iterator.next();
            VirtualMachineInner virtualMachine = vmEntry.getValue();
            String diskUri = getVhdUri(virtualMachine);

            if (diskUri == null) {
                logFine(() -> String.format("Disk URI not found for vm: %s", virtualMachine.id()));
                continue;
            }

            DiskState diskToUpdate = ctx.diskStates.get(diskUri);
            if (diskToUpdate == null) {
                logFine(() -> String.format("Disk not found: %s", diskUri));
                continue;
            }
            ImageReferenceInner imageReference = virtualMachine.storageProfile()
                    .imageReference();
            diskToUpdate.sourceImageReference = URI.create(imageReferenceToImageId(imageReference));
            diskToUpdate.bootOrder = 1;
            if (diskToUpdate.customProperties == null) {
                diskToUpdate.customProperties = new HashMap<>();
            }
            diskToUpdate.customProperties.put(AZURE_OSDISK_CACHING,
                    virtualMachine.storageProfile().osDisk().caching().name());
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
     * Create internal tag resources for network interfaces.
     */
    private void createNetworkInterfaceInternalTagStates(EnumerationContext ctx,
            ComputeEnumerationSubStages next) {
        TagState internalTypeTag = newTagState(PhotonModelConstants.TAG_KEY_TYPE,
                NETWORK_INTERFACE_TAG_TYPE_VALUE, false, ctx.parentCompute.tenantLinks);
        // operation to create tag "type" for network interfaces.
        Operation.createPost(this, TagService.FACTORY_LINK)
                .setBody(internalTypeTag)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere("Error creating internal type tag for network interfaces: %s",
                                e.getMessage());
                    } else {
                        ctx.nicInternalTagsMap.put(PhotonModelConstants.TAG_KEY_TYPE,
                                NETWORK_INTERFACE_TAG_TYPE_VALUE);
                        ctx.nicInternalTagLinksSet.add(internalTypeTag.documentSelfLink);
                    }
                    ctx.subStage = next;
                    handleSubStage(ctx);
                }).sendWith(this);
    }

    /**
     * Create network interface states for each VM
     */
    private void createNetworkInterfaceStates(EnumerationContext ctx,
            ComputeEnumerationSubStages next) {
        Consumer<Throwable> failure = e -> {
            logWarning("Failure getting Azure network interface states [endpointLink:%s] [Exception:%s]",
                    ctx.request.endpointLink, e.getMessage());
            handleError(ctx, e);
            return;
        };

        PhotonModelUtils.runInExecutor(this.executorService, () -> {
            Azure azureClient = getAzureClient(ctx);
            NetworkInterfacesInner netOps = azureClient.networkInterfaces().inner();
            List<DeferredResult<Pair<NetworkInterfaceInner, String>>> remoteNics = ctx.virtualMachines
                    .values().stream()
                    .filter(vm -> vm.networkProfile() != null && !vm.networkProfile()
                            .networkInterfaces().isEmpty())
                    .flatMap(vm -> vm.networkProfile().networkInterfaces().stream()
                            .map(nic -> Pair.of(nic, vm.id())))
                    .map(pair -> loadRemoteNic(pair, netOps))
                    .collect(Collectors.toList());

            DeferredResult.allOf(remoteNics)
                    .thenCompose(rnics -> loadSubnets(ctx, rnics)
                            .thenCompose(subnetPerNicId -> doCreateUpdateDeleteNics(ctx, subnetPerNicId,
                                    rnics)))
                    .whenComplete(thenHandleSubStage(ctx, next));
        }, failure);


    }

    /**
     * Manages creating and updating Network Interfaces resources based on network interfaces
     * associated with virtual machines.
     */
    private DeferredResult<List<NetworkInterfaceState>> doCreateUpdateDeleteNics(EnumerationContext ctx,
            Map<String, String> subnetPerNicId,
            List<Pair<NetworkInterfaceInner, String>> remoteNics) {

        Map<String, Pair<NetworkInterfaceInner, String>> remoteStates = remoteNics.stream()
                .filter(p -> p.getLeft() != null)
                .collect(Collectors.toMap(p -> p.getLeft().id(), p -> p));
        Query.Builder qBuilder = Query.Builder.create()
                .addKindFieldClause(NetworkInterfaceState.class)
                .addInClause(NetworkInterfaceState.FIELD_NAME_ID, remoteStates.keySet());

        QueryByPages<NetworkInterfaceState> queryLocalStates = new QueryByPages<>(getHost(),
                qBuilder.build(), NetworkInterfaceState.class, ctx.parentCompute.tenantLinks,
                ctx.request.endpointLink).setMaxPageSize(getQueryResultLimit());

        return queryLocalStates.collectDocuments(Collectors.toList()).thenCompose(
                localNics -> requestCreateUpdateDeleteNic(localNics, remoteStates, ctx, subnetPerNicId,
                        remoteNics));
    }

    /**
     * Serves request for creating and updating Network Interfaces resources
     */
    private DeferredResult<List<NetworkInterfaceState>> requestCreateUpdateDeleteNic(
            List<NetworkInterfaceState> localNics,
            Map<String, Pair<NetworkInterfaceInner, String>> remoteStates, EnumerationContext ctx,
            Map<String, String> subnetPerNicId,
            List<Pair<NetworkInterfaceInner, String>> remoteNics) {
        List<DeferredResult<NetworkInterfaceState>> ops = new ArrayList<>();

        // execute update existing NICs followed by creating new NICs. Update needs to be performed
        // before creating new NICs as NICs present in 'localNics' will be removed from 'remoteStates'
        // while updating local NICs.

        // update network interfaces identified as 'localNics'. Here 'remoteStates' includes newly
        // identified nics + local nics.
        updateNic(localNics, remoteStates, ctx, subnetPerNicId, ops);
        // create new network interfaces identified as 'remoteStates'. Here, 'remoteStates' includes
        // ONLY newly identified nics.
        createNic(remoteStates, ctx, subnetPerNicId, ops);
        // Delete the NICs that exist locally but not on remote.
        List<String> remoteNicIds = new ArrayList<>();
        // Collect IDs of all remote network interfaces. Delete all local states that
        // have IDs other than remoteNicIds (i.e. stale states).
        remoteNics.stream()
                .forEach(pair -> remoteNicIds.add(pair.getLeft().id()));

        deleteNicHelper(remoteNicIds, ctx);

        return DeferredResult.allOf(ops);
    }

    /**
     * Helper for deleting stale network interfaces.
     */
    private DeferredResult<List<Operation>> deleteNicHelper(
            List<String> remoteNicIds, EnumerationContext ctx) {

        Query.Builder qBuilder = Query.Builder.create()
                .addKindFieldClause(NetworkInterfaceState.class);

        QueryByPages<NetworkInterfaceState> queryLocalStates = new QueryByPages<>(getHost(),
                qBuilder.build(), NetworkInterfaceState.class, ctx.parentCompute.tenantLinks,
                ctx.request.endpointLink).setMaxPageSize(getQueryResultLimit());

        return queryLocalStates.collectDocuments(Collectors.toList()).thenCompose(
                allLocalNics -> deleteNics(remoteNicIds, allLocalNics));
    }

    /**
     * Deletes stale network interface states that are deleted from the remote.
     */
    private DeferredResult<List<Operation>> deleteNics(
            List<String> remoteNicIds, List<NetworkInterfaceState> allLocalNics) {

        List<DeferredResult<Operation>> deleteOps = new ArrayList<>();

        allLocalNics.stream()
                .filter(localNic -> !remoteNicIds.contains(localNic.id))
                .forEach(localNic -> {
                    deleteOps.add(sendWithDeferredResult(Operation.createDelete(this.getHost(),
                            localNic.documentSelfLink).setReferer(this.getUri())));
                });

        return DeferredResult.allOf(deleteOps);
    }

    private void updateNic(List<NetworkInterfaceState> localNics,
            Map<String, Pair<NetworkInterfaceInner, String>> remoteStates, EnumerationContext ctx,
            Map<String, String> subnetPerNicId, List<DeferredResult<NetworkInterfaceState>> ops) {

        localNics.stream().forEach(nic -> {
            // fetch and remove local NIC present in 'remoteStates' so that only new NICs are left
            // present before we move forward to create new NICs.
            Pair<NetworkInterfaceInner, String> pair = remoteStates.remove(nic.id);
            NetworkInterfaceInner remoteNic = pair.getLeft();
            processCreateUpdateNicRequest(nic, remoteNic, ctx, ops, subnetPerNicId, false);
        });
    }

    private void createNic(Map<String, Pair<NetworkInterfaceInner, String>> remoteStates,
            EnumerationContext ctx,
            Map<String, String> subnetPerNicId, List<DeferredResult<NetworkInterfaceState>> ops) {
        remoteStates.values().stream().forEach(p -> {
            NetworkInterfaceInner remoteNic = p.getLeft();
            NetworkInterfaceState state = new NetworkInterfaceState();
            processCreateUpdateNicRequest(state, remoteNic, ctx, ops, subnetPerNicId, true);
        });
    }

    /**
     * Processes request for creating and updating Network interface resources.
     */
    private void processCreateUpdateNicRequest(NetworkInterfaceState nic,
            NetworkInterfaceInner remoteNic, EnumerationContext ctx,
            List<DeferredResult<NetworkInterfaceState>> ops, Map<String, String> subnetPerNicId,
            boolean isCreate) {
        nic.name = remoteNic.name();
        nic.subnetLink = subnetPerNicId.get(remoteNic.id());

        NicMetadata nicMeta = new NicMetadata();
        nicMeta.state = nic;
        nicMeta.macAddress = remoteNic.macAddress();

        // If its a POST request, assign NetworkInterfaceInner ID
        // else will default to original ID for PATCH requests
        if (isCreate) {
            nic.id = remoteNic.id();
            nic.endpointLink = ctx.request.endpointLink;
            nic.tenantLinks = ctx.parentCompute.tenantLinks;
            nic.regionId = remoteNic.location();
        }

        List<NetworkInterfaceIPConfigurationInner> ipConfigurations = remoteNic.ipConfigurations();
        if (ipConfigurations == null || ipConfigurations.isEmpty()) {
            executeNicCreateUpdateRequest(nic, remoteNic, ctx, ops, nicMeta, isCreate);
            return;
        }
        NetworkInterfaceIPConfigurationInner nicIPConf = ipConfigurations.get(0);
        nic.address = nicIPConf.privateIPAddress();
        if (nicIPConf.publicIPAddress() == null) {
            executeNicCreateUpdateRequest(nic, remoteNic, ctx, ops, nicMeta, isCreate);
            return;
        }
        // IP address is not directly available in NetworkInterfaceIPConfigurationInner.
        // It is available as a SubResource, We use the SubResource ID of IP address from
        // NetworkInterfaceIPConfigurationInner to obtain the IP address.
        Consumer<Throwable> failure = e -> {
            logWarning("Error getting public IP address from Azure [endpointLink:%s], [Exception:%s]",
                    ctx.request.endpointLink, e.getMessage());
            return;
        };

        PhotonModelUtils.runInExecutor(this.executorService, () -> {
            Azure azure = getAzureClient(ctx);
            azure.publicIPAddresses()
                    .getByIdAsync(nicIPConf.publicIPAddress().id())
                    .subscribe(new Action1<PublicIPAddress>() {
                        @Override
                        public void call(PublicIPAddress publicIPAddress) {
                            nicMeta.publicIp = publicIPAddress.ipAddress();
                            if (publicIPAddress.inner().dnsSettings() != null) {
                                nicMeta.publicDnsName = publicIPAddress.inner().dnsSettings().fqdn();
                            }
                            executeNicCreateUpdateRequest(nic, remoteNic, ctx, ops, nicMeta, isCreate);
                        }
                    });
        }, failure);

    }

    private void executeNicCreateUpdateRequest(NetworkInterfaceState nic,
            NetworkInterfaceInner remoteNic, EnumerationContext ctx,
            List<DeferredResult<NetworkInterfaceState>> ops, NicMetadata nicMeta,
            boolean isCreate) {
        if (isCreate) {
            // set internal tags as tagLinks for nics to be newly created.
            setTagLinksToResourceState(nic, ctx.nicInternalTagsMap, false);
            // perform POST request
            addPostToNetworkInterfaceService(ctx, ops, nic, remoteNic, nicMeta);
        } else {
            // for already existing nics, add internal tags only if missing
            if (nic.tagLinks == null || nic.tagLinks.isEmpty()) {
                setTagLinksToResourceState(nic, ctx.nicInternalTagsMap, false);
            } else {
                ctx.nicInternalTagLinksSet.stream()
                        .filter(tagLink -> !nic.tagLinks.contains(tagLink))
                        .map(tagLink -> nic.tagLinks.add(tagLink))
                        .collect(Collectors.toSet());
            }
            // perform PATCH request
            addPatchToNetworkInterfaceService(ctx, ops, nic, remoteNic, nicMeta);
        }
    }

    private void addPatchToNetworkInterfaceService(EnumerationContext ctx,
            List<DeferredResult<NetworkInterfaceState>> ops, NetworkInterfaceState nic,
            NetworkInterfaceInner remoteNic, NicMetadata nicMeta) {
        ops.add(sendWithDeferredResult(Operation
                .createPatch(ctx.request.buildUri(nic.documentSelfLink))
                .setBody(nic), NetworkInterfaceState.class)
                .thenApply(nicState -> {
                    ctx.networkInterfaceIds.put(remoteNic.id(), nicMeta);
                    return nicState;
                }));
    }

    private void addPostToNetworkInterfaceService(EnumerationContext ctx,
            List<DeferredResult<NetworkInterfaceState>> ops, NetworkInterfaceState state,
            NetworkInterfaceInner remoteNic, NicMetadata nicMeta) {
        ops.add(sendWithDeferredResult(Operation
                .createPost(ctx.request.buildUri(NetworkInterfaceService.FACTORY_LINK))
                .setBody(state), NetworkInterfaceState.class)
                .thenApply(nic -> {
                    nicMeta.state = nic;
                    ctx.networkInterfaceIds.put(remoteNic.id(), nicMeta);
                    return nic;
                }));
    }

    private DeferredResult<Pair<NetworkInterfaceInner, String>> loadRemoteNic(
            Pair<NetworkInterfaceReferenceInner, String> pair, NetworkInterfacesInner netOps) {
        AzureDeferredResultServiceCallback<NetworkInterfaceInner> handler = new AzureDeferredResultServiceCallback<NetworkInterfaceInner>(
                this, "Load Nic: " + pair.getLeft().id()) {
            @Override
            protected DeferredResult<NetworkInterfaceInner> consumeSuccess(
                    NetworkInterfaceInner nic) {
                if (nic == null) {
                    logWarning("Failed to get information for nic: %s", pair.getLeft().id());
                }
                return DeferredResult.completed(nic);
            }
        };
        String networkInterfaceName = UriUtils.getLastPathSegment(pair.getLeft().id());
        String nicRG = getResourceGroupName(pair.getLeft().id());
        String resourceGroupName = getResourceGroupName(pair.getRight());
        if (!resourceGroupName.equalsIgnoreCase(nicRG)) {
            logWarning(
                    "VM resource group %s is different from nic resource group %s, for nic %s",
                    resourceGroupName, nicRG, pair.getLeft().id());
        }

        Consumer<Throwable> failure = e -> {
            logWarning("Error getting Azure NIC [Exception:%s]", e.getMessage());
            return;
        };

        PhotonModelUtils.runInExecutor(this.executorService, () -> {
            netOps.getByResourceGroupAsync(resourceGroupName, networkInterfaceName,
                    "ipConfigurations/publicIPAddress", handler);
        }, failure);

        return handler.toDeferredResult()
                .thenApply(loaded -> Pair.of(loaded, pair.getRight()));

    }

    private DeferredResult<Map<String, String>> loadSubnets(EnumerationContext ctx,
            List<Pair<NetworkInterfaceInner, String>> remoteNics) {

        Map<String, List<Pair<NetworkInterfaceInner, String>>> nicsPerSubnet = remoteNics.stream()
                .filter(p -> p.getLeft() != null &&
                        p.getLeft().ipConfigurations() != null &&
                        !p.getLeft().ipConfigurations().isEmpty() &&
                        p.getLeft().ipConfigurations().get(0).subnet() != null)
                .collect(java.util.stream.Collectors
                        .groupingBy(p -> p.getLeft().ipConfigurations().get(0).subnet().id()));

        Query.Builder qBuilder = Query.Builder.create()
                .addKindFieldClause(SubnetState.class)
                .addInClause(NetworkInterfaceState.FIELD_NAME_ID,
                        nicsPerSubnet.keySet().stream().collect(Collectors.toList()));

        QueryByPages<SubnetState> queryLocalStates = new QueryByPages<>(getHost(),
                qBuilder.build(), SubnetState.class, ctx.parentCompute.tenantLinks,
                ctx.request.endpointLink).setMaxPageSize(getQueryResultLimit());
        Map<String, String> subnetLinkPerNicId = new HashMap<>();

        return queryLocalStates.queryDocuments(subnet ->
                nicsPerSubnet.get(subnet.id).forEach(p -> subnetLinkPerNicId
                        .put(p.getLeft().id(), subnet.documentSelfLink)))
                .thenApply(ignore -> subnetLinkPerNicId);
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

    private ComputeState createComputeState(EnumerationContext ctx, VirtualMachineInner virtualMachine) {

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
        computeState.id = virtualMachine.id().toLowerCase();
        computeState.name = virtualMachine.name();
        computeState.regionId = virtualMachine.location();

        computeState.type = ComputeType.VM_GUEST;
        computeState.environmentName = ComputeDescription.ENVIRONMENT_NAME_AZURE;
        computeState.parentLink = ctx.request.resourceLink();
        computeState.descriptionLink = UriUtils
                .buildUriPath(ComputeDescriptionService.FACTORY_LINK,
                        ctx.computeDescriptionIds.get(virtualMachine.name()));
        computeState.endpointLink = ctx.request.endpointLink;
        computeState.resourcePoolLink = ctx.request.resourcePoolLink;
        computeState.diskLinks = vmDisks;
        if (virtualMachine.hardwareProfile() != null
                && virtualMachine.hardwareProfile().vmSize() != null) {
            computeState.instanceType = virtualMachine.hardwareProfile().vmSize().toString();
        }
        computeState.instanceAdapterReference = ctx.parentCompute.description.instanceAdapterReference;
        computeState.statsAdapterReference = ctx.parentCompute.description.statsAdapterReference;

        computeState.customProperties = new HashMap<>();
        computeState.customProperties.put(CUSTOM_OS_TYPE, getNormalizedOSType(virtualMachine));

        String resourceGroupName = getResourceGroupName(virtualMachine.id());
        computeState.customProperties.put(AZURE_RESOURCE_GROUP_NAME, resourceGroupName);

        if (virtualMachine.diagnosticsProfile() != null) {
            String diagnosticsAccountUri = virtualMachine.diagnosticsProfile()
                    .bootDiagnostics().storageUri();
            StorageDescription storageDesk = ctx.storageDescriptions.get(diagnosticsAccountUri);
            if (storageDesk != null) {
                computeState.customProperties.put(AZURE_DIAGNOSTIC_STORAGE_ACCOUNT_LINK,
                        storageDesk.documentSelfLink);
            }
        }

        computeState.tenantLinks = ctx.parentCompute.tenantLinks;

        // add tag links
        setTagLinksToResourceState(computeState, virtualMachine.getTags(), true);
        if (computeState.tagLinks == null) {
            computeState.tagLinks = new HashSet<>();
        }
        // add internal type tags
        computeState.tagLinks.addAll(ctx.internalTagLinks);

        List<String> networkLinks = new ArrayList<>();
        NicMetadata nicMeta = ctx.networkInterfaceIds
                .remove(virtualMachine.networkProfile().networkInterfaces().get(0)
                        .id());
        if (nicMeta != null) {
            computeState.address = nicMeta.publicIp;
            computeState.hostName = nicMeta.publicDnsName;
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
        Consumer<Throwable> failure = e -> {
            logWarning("Failure getting Azure Virtual Machines");
            return;
        };

        PhotonModelUtils.runInExecutor(this.executorService, () -> {
            Azure azureClient = getAzureClient(ctx);
            VirtualMachinesInner vmOps = azureClient
                    .virtualMachines().inner();
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
        }, failure);
    }

    private DeferredResult<ComputeState> patchVMInstanceDetails(EnumerationContext ctx,
                                                                VirtualMachinesInner vmOps, ComputeState computeState) {

        String resourceGroupName = getResourceGroupName(computeState.id);
        String vmName = computeState.name;
        AzureDeferredResultServiceCallback<VirtualMachineInner> handler = new AzureDeferredResultServiceCallback<VirtualMachineInner>(
                this, "Load virtual machine instance view:" + vmName) {
            @Override
            protected DeferredResult<VirtualMachineInner> consumeSuccess(
                    VirtualMachineInner vm) {
                logFine(() -> String.format("Retrieved instance view for vm [%s].", vmName));
                return DeferredResult.completed(vm);
            }
        };

        Consumer<Throwable> failure = e -> {
            logWarning("Error getting Azure VM instance view [endpointLink:%s] [Exception:%s]",
                    ctx.request.endpointLink, e.getMessage());
            return;
        };

        PhotonModelUtils.runInExecutor(this.executorService, () -> {
            vmOps.getByResourceGroupAsync(resourceGroupName, vmName, InstanceViewTypes.INSTANCE_VIEW, handler);
        }, failure);

        return handler.toDeferredResult().thenApply(vm -> {
            for (InstanceViewStatus status : vm.instanceView().statuses()) {
                if (status.code()
                        .equals(AzureConstants.AZURE_VM_PROVISIONING_STATE_SUCCEEDED)) {
                    computeState.creationTimeMicros = TimeUnit.MILLISECONDS
                            .toMicros(status.time().getMillis());
                } else if (status.code()
                        .equals(AzureConstants.AZURE_VM_POWER_STATE_RUNNING)) {
                    computeState.powerState = PowerState.ON;
                } else if (status.code()
                        .equals(AzureConstants.AZURE_VM_POWER_STATE_STOPPED)) {
                    computeState.powerState = PowerState.OFF;
                } else if (status.code()
                        .equals(AzureConstants.AZURE_VM_POWER_STATE_DEALLOCATED)) {
                    computeState.powerState = PowerState.SUSPEND;
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
    private String imageReferenceToImageId(ImageReferenceInner imageReference) {
        return imageReference.publisher() + ":" + imageReference.offer() + ":"
                + imageReference.sku() + ":" + imageReference.version();
    }

    private Azure getAzureClient(EnumerationContext ctx) {
        if (ctx.azure == null) {
            if (ctx.restClient == null) {
                ctx.restClient = buildRestClient(ctx.credentials,this.executorService);
            }
            ctx.azure = Azure.authenticate(ctx.restClient, ctx.parentAuth.customProperties.get(AZURE_TENANT_ID))
                    .withSubscription(ctx.parentAuth.userLink);
        }
        return ctx.azure;
    }

    /**
     * Return Instance normalized OS Type.
     */
    private String getNormalizedOSType(VirtualMachineInner vm) {
        if (vm.storageProfile() == null
                || vm.storageProfile().osDisk() == null
                || vm.storageProfile().osDisk().osType() == null) {
            return null;
        }
        OperatingSystemTypes osType = vm.storageProfile().osDisk().osType();
        if (OperatingSystemTypes.WINDOWS.equals(osType)) {
            return OSType.WINDOWS.toString();
        } else if (OperatingSystemTypes.LINUX.equals(osType)) {
            return OSType.LINUX.toString();
        } else {
            return null;
        }
    }

    private String getVhdUri(VirtualMachineInner vm) {
        if (vm.storageProfile() == null
                || vm.storageProfile().osDisk() == null
                || vm.storageProfile().osDisk().vhd() == null
                || vm.storageProfile().osDisk().vhd().uri() == null) {
            logWarning(String.format(
                    "Enumeration failed. VM %s has a ManagedDisk configuration, which is currently not supported.",
                    vm.id()));
            return null;
        }
        return httpsToHttp(vm.storageProfile().osDisk().vhd().uri());
    }

    /**
     * Converts https to http.
     */
    private String httpsToHttp(String uri) {
        return (uri.startsWith("https")) ? uri.replace("https", "http") : uri;
    }
}
