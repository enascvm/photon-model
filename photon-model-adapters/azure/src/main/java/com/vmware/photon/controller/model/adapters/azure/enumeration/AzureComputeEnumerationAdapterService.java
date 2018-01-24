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
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_DATA_DISK_CACHING;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_DIAGNOSTIC_STORAGE_ACCOUNT_LINK;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_MANAGED_DISK_TYPE;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_OSDISK_CACHING;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_RESOURCE_GROUP_NAME;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNT_URI;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.DISK_CONTROLLER_NUMBER;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.getQueryResultLimit;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.getResourceGroupName;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.injectOperationContext;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.isDiskManaged;
import static com.vmware.photon.controller.model.adapters.util.TagsUtil.newTagState;
import static com.vmware.photon.controller.model.adapters.util.TagsUtil.setTagLinksToResourceState;
import static com.vmware.photon.controller.model.adapters.util.TagsUtil.updateLocalTagStates;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.CUSTOM_PROP_ENDPOINT_LINK;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.SOURCE_TASK_LINK;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.TAG_KEY_TYPE;
import static com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ENVIRONMENT_NAME_AZURE;
import static com.vmware.photon.controller.model.util.PhotonModelUriUtils.createInventoryUri;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.compute.DataDisk;
import com.microsoft.azure.management.compute.InstanceViewStatus;
import com.microsoft.azure.management.compute.InstanceViewTypes;
import com.microsoft.azure.management.compute.OSDisk;
import com.microsoft.azure.management.compute.OperatingSystemTypes;
import com.microsoft.azure.management.compute.implementation.ImageReferenceInner;
import com.microsoft.azure.management.compute.implementation.NetworkInterfaceReferenceInner;
import com.microsoft.azure.management.compute.implementation.VirtualMachineInner;
import com.microsoft.azure.management.compute.implementation.VirtualMachinesInner;
import com.microsoft.azure.management.network.PublicIPAddress;
import com.microsoft.azure.management.network.implementation.NetworkInterfaceIPConfigurationInner;
import com.microsoft.azure.management.network.implementation.NetworkInterfaceInner;
import com.microsoft.azure.management.network.implementation.NetworkInterfacesInner;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import rx.functions.Action1;

import com.vmware.photon.controller.model.ComputeProperties.OSType;
import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapterapi.RegionEnumerationResponse;
import com.vmware.photon.controller.model.adapterapi.RegionEnumerationResponse.RegionInfo;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AzureResourceType;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureDeferredResultServiceCallback;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureDeferredResultServiceCallback.Default;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureSdkClients;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.ComputeEnumerateAdapterRequest;
import com.vmware.photon.controller.model.adapters.util.enums.EnumerationStages;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.query.QueryUtils.QueryByPages;
import com.vmware.photon.controller.model.query.QueryUtils.QueryTop;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.ComputeService.LifecycleState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.photon.controller.model.resources.util.PhotonModelUtils;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService;
import com.vmware.photon.controller.model.util.ClusterUtil.ServiceTypeCluster;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceStateCollectionUpdateRequest;
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

    public static final List<String> AZURE_VM_TERMINATION_STATES = Arrays.asList(
            "Deleting",
            "Deleted");

    private static final String NETWORK_INTERFACE_TAG_TYPE_VALUE = AzureResourceType.azure_net_interface.toString();

    /**
     * The enumeration service context that holds all the information needed to determine the list
     * of instances that need to be represented in the system.
     */
    private static class EnumerationContext implements AutoCloseable {
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
        AuthCredentialsServiceState endpointAuth;
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

        // Azure clients
        AzureSdkClients azureSdkClients;

        Map<String, RegionInfo> regions = new ConcurrentHashMap<>();
        Set<String> regionIds = new HashSet<>();

        EnumerationContext(ComputeEnumerateAdapterRequest request, Operation op) {
            this.request = request.original;
            this.endpointAuth = request.endpointAuth;
            this.parentCompute = request.parentCompute;

            this.stage = EnumerationStages.CLIENT;
            this.operation = op;
        }

        @Override
        public void close() {
            if (this.azureSdkClients != null) {
                this.azureSdkClients.close();
                this.azureSdkClients = null;
            }
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
        COLLECT_REGIONS,
        LISTVMS,
        GET_COMPUTE_STATES,
        CREATE_COMPUTE_EXTERNAL_TAG_STATES,
        CREATE_COMPUTE_INTERNAL_TYPE_TAG,
        UPDATE_COMPUTE_STATES,
        UPDATE_REGIONS,
        UPDATE_COMPUTE_DESCRIPTIONS,
        GET_DISK_STATES,
        GET_STORAGE_DESCRIPTIONS,
        CREATE_COMPUTE_DESCRIPTIONS,
        UPDATE_DISK_STATES,
        CREATE_NETWORK_INTERFACE_INTERNAL_TAG_STATES,
        CREATE_NETWORK_INTERFACE_STATES,
        CREATE_COMPUTE_STATES,
        PATCH_ADDITIONAL_FIELDS,
        DISASSOCIATE_COMPUTE_STATES,
        FINISHED
    }

    /**
     * Constrain every query with endpointLink and tenantLinks, if presented.
     */
    private static void addScopeCriteria(Query.Builder qBuilder,
            Class<? extends ResourceState> stateClass,
            EnumerationContext ctx) {
        // Add parent compute host criteria
        qBuilder.addFieldClause(ResourceState.FIELD_NAME_COMPUTE_HOST_LINK, ctx.parentCompute.documentSelfLink);
        // Add TENANT_LINKS criteria
        QueryUtils.addTenantLinks(qBuilder, ctx.parentCompute.tenantLinks);
    }

    private ExecutorService executorService;

    public AzureComputeEnumerationAdapterService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
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
            ctx.close();
            return;
        }
        handleEnumeration(ctx);
    }

    private void handleEnumeration(EnumerationContext ctx) {
        switch (ctx.stage) {
        case CLIENT:
            if (ctx.azureSdkClients == null) {
                try {
                    ctx.azureSdkClients = new AzureSdkClients(ctx.endpointAuth);
                } catch (Throwable e) {
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
                ctx.subStage = ComputeEnumerationSubStages.COLLECT_REGIONS;
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
            ctx.operation.complete();
            ctx.close();
            break;
        case ERROR:
            logWarning(() -> String
                    .format("Azure compute enumeration error for %s, Failed due to %s",
                            ctx.request.getEnumKey(), Utils.toString(ctx.error)));
            ctx.operation.fail(ctx.error);
            ctx.close();
            break;
        default:
            String msg = String.format("Unknown Azure compute enumeration stage %s ",
                    ctx.stage.toString());
            handleError(ctx, new IllegalStateException(msg));
        }
    }

    /**
     * Handle enumeration substages for VM data collection.
     */
    private void handleSubStage(EnumerationContext ctx) {
        try {
            doHandleSubStage(ctx);
        } catch (Throwable e) {
            handleError(ctx, e);
        }
    }

    private void doHandleSubStage(EnumerationContext ctx) {
        logInfo(() -> String.format(
                "Handle substage %s for [endpointLink:%s]", ctx.subStage, ctx.request.endpointLink));
        switch (ctx.subStage) {
        case COLLECT_REGIONS:
            collectRegions(ctx, ComputeEnumerationSubStages.LISTVMS);
            break;
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
            createInternalTypeTag(ctx,
                    ComputeEnumerationSubStages.CREATE_COMPUTE_EXTERNAL_TAG_STATES);
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
            updateComputeStates(ctx, ComputeEnumerationSubStages.UPDATE_REGIONS);
            break;
        case UPDATE_REGIONS:
            updateRegions(ctx, ComputeEnumerationSubStages.UPDATE_COMPUTE_DESCRIPTIONS);
            break;
        case UPDATE_COMPUTE_DESCRIPTIONS:
            updateComputeDescriptions(ctx, ComputeEnumerationSubStages.CREATE_COMPUTE_DESCRIPTIONS);
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
            patchAdditionalFields(ctx, ComputeEnumerationSubStages.DISASSOCIATE_COMPUTE_STATES);
            break;
        case DISASSOCIATE_COMPUTE_STATES:
            disassociateComputeStates(ctx, ComputeEnumerationSubStages.FINISHED);
            break;
        case FINISHED:
            ctx.stage = EnumerationStages.FINISHED;
            handleEnumeration(ctx);
            break;
        default:
            String msg = String
                    .format("Unknown Azure enumeration sub-stage %s ", ctx.subStage.toString());
            handleError(ctx, new IllegalStateException(msg));
            break;
        }
    }

    private void collectRegions(EnumerationContext ctx, ComputeEnumerationSubStages next) {
        Operation getEndpoint = Operation.createGet(this, ctx.request.endpointLink);

        DeferredResult<EndpointState> getEndpointDR = sendWithDeferredResult(getEndpoint,
                EndpointState.class);
        getEndpointDR
                .thenCompose(endpoint -> {
                    Operation getRegions = Operation
                            .createPost(this, AzureRegionEnumerationAdapterService.SELF_LINK)
                            .setBody(endpoint);

                    return sendWithDeferredResult(getRegions, RegionEnumerationResponse.class);
                }).whenComplete((regionsResponse, e) -> {
                    if (e != null) {
                        logWarning("Resource enumeration failed at stage %s with exception %s",
                                ctx.stage, Utils.toString(e));
                        handleError(ctx, e);
                    } else {
                        ctx.regions.putAll(regionsResponse.regions.stream()
                                .collect(Collectors.toMap(r -> r.regionId, r -> r)));
                        ctx.regionIds.addAll(ctx.regions.keySet());
                        ctx.subStage = next;
                        handleSubStage(ctx);
                    }
                });
    }

    private ComputeDescription createComputeDescriptionForRegion(EnumerationContext context, RegionInfo r) {
        ComputeDescriptionService.ComputeDescription cd = Utils
                .clone(context.parentCompute.description);
        cd.supportedChildren = new ArrayList<>();
        cd.supportedChildren.add(ComputeType.VM_GUEST.toString());
        cd.id = r.regionId;
        cd.documentSelfLink = generateRegionComputeDescriptionLinkId(r.regionId, context.request.endpointLink);
        cd.name = r.name;
        cd.regionId = r.regionId;
        cd.endpointLink = context.request.endpointLink;
        AdapterUtils.addToEndpointLinks(cd, context.request.endpointLink);

        // Book keeping information about the creation of the compute description in the system.
        if (cd.customProperties == null) {
            cd.customProperties = new HashMap<>();
        }
        cd.customProperties.put(SOURCE_TASK_LINK,
                ResourceEnumerationTaskService.FACTORY_LINK);

        return cd;
    }

    private ComputeState createComputeInstanceForRegion(EnumerationContext context, RegionInfo regionInfo) {
        ComputeService.ComputeState computeState = new ComputeService.ComputeState();
        computeState.name = regionInfo.name;
        computeState.id = regionInfo.regionId;

        computeState.adapterManagementReference = context.parentCompute.adapterManagementReference;
        computeState.instanceAdapterReference = context.parentCompute.description.instanceAdapterReference;
        computeState.statsAdapterReference = context.parentCompute.description.statsAdapterReference;
        computeState.parentLink = context.parentCompute.documentSelfLink;
        computeState.computeHostLink = context.parentCompute.documentSelfLink;
        computeState.resourcePoolLink = context.request.resourcePoolLink;
        computeState.endpointLink = context.request.endpointLink;
        AdapterUtils.addToEndpointLinks(computeState, context.request.endpointLink);

        String descriptionLinkId = generateRegionComputeDescriptionLinkId(regionInfo.regionId, context.request.endpointLink);
        computeState.descriptionLink = UriUtils
                .buildUriPath(ComputeDescriptionService.FACTORY_LINK, descriptionLinkId);
        computeState.type = ComputeType.ZONE;
        computeState.regionId = regionInfo.regionId;
        computeState.environmentName = ComputeDescription.ENVIRONMENT_NAME_AZURE;

        computeState.powerState = PowerState.ON;

        computeState.customProperties = context.parentCompute.customProperties;
        if (computeState.customProperties == null) {
            computeState.customProperties = new HashMap<>();
        }
        computeState.customProperties
                .put(SOURCE_TASK_LINK, ResourceEnumerationTaskService.FACTORY_LINK);

        computeState.tenantLinks = context.parentCompute.tenantLinks;
        return computeState;
    }

    private String generateRegionComputeDescriptionLinkId(String regionId, String endpointLink) {
        return UUID.nameUUIDFromBytes((regionId + endpointLink).getBytes()).toString();
    }

    /**
     * {@code handleSubStage} version suitable for chaining to {@code DeferredResult.whenComplete}.
     */
    private <T> BiConsumer<T, Throwable> thenHandleSubStage(
            EnumerationContext context,
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

    private void createInternalTypeTag(EnumerationContext context,
            ComputeEnumerationSubStages next) {
        TagService.TagState typeTag = newTagState(TAG_KEY_TYPE,
                AzureResourceType.azure_vm.toString(),
                false, context.parentCompute.tenantLinks);

        Operation.CompletionHandler handler = (completedOp, failure) -> {
            if (failure == null) {
                // if no error, store the internal tag into context
                context.internalTagLinks.add(typeTag.documentSelfLink);
            } else {
                // log the error and continue the enumeration
                logWarning(() -> String
                        .format("Error creating internal tag: %s", failure.getMessage()));
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
     * <p>
     * The logic works by recording a timestamp when enumeration starts. This timestamp is used to
     * lookup resources which haven't been touched as part of current enumeration cycle. The other
     * data point this method uses is the virtual machines discovered as part of list vm call.
     * <p>
     * Finally, delete on a resource is invoked only if it meets two criteria: - Timestamp older
     * than current enumeration cycle. - VM not present on Azure.
     * <p>
     * The method paginates through list of resources for deletion.
     */
    private void disassociateComputeStates(EnumerationContext ctx, ComputeEnumerationSubStages next) {
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
        QueryUtils.startInventoryQueryTask(this, q)
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
                    disassociateOrRetireHelper(ctx, next);
                });
    }

    /**
     * Helper method to paginate through resources to be deleted.
     */
    private void disassociateOrRetireHelper(EnumerationContext ctx,
            ComputeEnumerationSubStages next) {
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
                ComputeState computeState = Utils.fromJson(s, ComputeState.class);
                String vmId = computeState.id;

                // Since we only update disks during update, some compute states might be
                // present in Azure but have older timestamp in local repository.
                if (ctx.vmIds.contains(vmId) || ctx.regionIds.contains(vmId)) {
                    continue;
                }

                if (ctx.request.preserveMissing) {
                    logFine(() -> String
                            .format("Retiring compute state %s", computeState.documentSelfLink));
                    ComputeState cs = new ComputeState();
                    cs.powerState = PowerState.OFF;
                    cs.lifecycleState = LifecycleState.RETIRED;
                    operations.add(Operation.createPatch(this, computeState.documentSelfLink)
                            .setBody(cs));
                } else {
                    // Deleting the localResourceState is done by disassociating the endpointLink from the
                    // localResourceState. If the localResourceState isn't associated with any other
                    // endpointLink, we issue a delete then
                    Operation dOp = PhotonModelUtils
                            .createRemoveEndpointLinksOperation(
                                    this, ctx.request.endpointLink, computeState);

                    if (dOp != null) {
                        dOp.sendWith(getHost());
                        logFine(() -> String.format("Deleting compute state %s",
                                computeState.documentSelfLink));
                    }
                    if (computeState.diskLinks != null && !computeState.diskLinks.isEmpty()) {
                        computeState.diskLinks.forEach(dl -> {
                            sendRequest(Operation.createGet(this, dl)
                                    .setCompletion((op, ex) -> {
                                        if (ex != null) {
                                            logWarning(() -> String
                                                    .format("Error retrieving " + "diskState: %s",
                                                            ex.getMessage()));
                                        } else {
                                            DiskState diskState = op.getBody(DiskState.class);
                                            Operation diskOp = PhotonModelUtils
                                                    .createRemoveEndpointLinksOperation(
                                                            this,
                                                            ctx.request.endpointLink,
                                                            diskState);

                                            if (diskOp != null) {
                                                diskOp.sendWith(getHost());
                                                logFine(() -> String
                                                        .format("Deleting disk state %s of machine %s",
                                                                dl, computeState.documentSelfLink));
                                            }
                                        }
                                    }));
                        });
                    }

                    if (computeState.networkInterfaceLinks != null
                            && !computeState.networkInterfaceLinks.isEmpty()) {
                        computeState.networkInterfaceLinks.forEach(nil -> {
                            sendRequest(Operation.createGet(this, nil)
                                    .setCompletion((op, ex) -> {
                                        if (ex != null) {
                                            logWarning(() -> String
                                                    .format("Error retrieving NetworkInterface state: %s",
                                                            ex.getMessage()));
                                        } else {
                                            NetworkInterfaceState networkInterfaceState = op
                                                    .getBody(NetworkInterfaceState.class);
                                            Operation nicOp = PhotonModelUtils
                                                    .createRemoveEndpointLinksOperation(
                                                            this,
                                                            ctx.request.endpointLink,
                                                            networkInterfaceState);
                                            if (nicOp != null) {
                                                nicOp.sendWith(getHost());
                                                logFine(() -> String
                                                        .format("Deleting NetworkInterface state %s of machine %s",
                                                                nil,
                                                                computeState.documentSelfLink));
                                            }
                                        }
                                    }));
                        });
                    }
                }
            }

            if (operations.size() == 0) {
                logFine(() -> String.format("No compute/disk states to %s",
                        ctx.request.preserveMissing ? "retire" : "delete"));
                disassociateOrRetireHelper(ctx, next);
                return;
            }

            OperationJoin.create(operations)
                    .setCompletion((ops, exs) -> {
                        if (exs != null) {
                            // We don't want to fail the whole data collection if some of the
                            // operation fails.
                            exs.values().forEach(ex -> logWarning(
                                    () -> String.format("Error: %s", ex.getMessage())));
                        }
                        disassociateOrRetireHelper(ctx, next);
                    })
                    .sendWith(this);
        };
        logFine(() -> String
                .format("Querying page [%s] for resources to be %s", ctx.deletionNextPageLink,
                        ctx.request.preserveMissing ? "retire" : "delete"));
        sendRequest(
                Operation.createGet(createInventoryUri(this.getHost(), ctx.deletionNextPageLink))
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
        };

        PhotonModelUtils.runInExecutor(this.executorService, () -> {
            logFine(() -> "Enumerating VMs from Azure");

            Azure azureClient = ctx.azureSdkClients.getAzureClient();

            ctx.virtualMachines.clear();

            // take(1) allows subscriber invokes call() action only once and then unsubscribe.
            if (ctx.enumNextPageLink == null) {
                azureClient.virtualMachines().inner().listAsync()
                        .take(1)
                        .subscribe(vmEnumerationCompletion(ctx, next));
            } else {
                azureClient.virtualMachines().inner().listNextAsync(ctx.enumNextPageLink)
                        .take(1)
                        .subscribe(vmEnumerationCompletion(ctx, next));
            }
        }, failure);
    }

    /**
     * Completion handler for VM enumeration call. For async calls to list VMs, Azure returns
     * Observable<Page<VirtualMachineInner>>. The following completion subscribes to the Observable
     * and Overrides call(<T>) to include logic to process pages of VMs when we receive them.
     */
    private Action1<Page<VirtualMachineInner>> vmEnumerationCompletion(
            EnumerationContext ctx,
            ComputeEnumerationSubStages next) {

        Action1<Page<VirtualMachineInner>> vmEnumCompletion = new Action1<Page<VirtualMachineInner>>() {

            @Override
            public void call(Page<VirtualMachineInner> virtualMachineInnerPage) {
                List<VirtualMachineInner> virtualMachineInners = virtualMachineInnerPage.items();
                ctx.enumNextPageLink = virtualMachineInnerPage.nextPageLink();

                if (virtualMachineInners == null || virtualMachineInners.size() == 0) {
                    ctx.subStage = ComputeEnumerationSubStages.GET_COMPUTE_STATES;
                    handleSubStage(ctx);
                    return;
                }

                logInfo(() -> String
                        .format("Retrieved %d VMs from Azure", virtualMachineInners.size()));
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

        return injectOperationContext(vmEnumCompletion);
    }

    /**
     * Query all compute states for the cluster filtered by the received set of instance Ids.
     */
    private void queryForComputeStates(EnumerationContext ctx, ComputeEnumerationSubStages next) {
        if (ctx.virtualMachines.isEmpty() && ctx.regions.isEmpty()) {
            ctx.subStage = ComputeEnumerationSubStages.DISASSOCIATE_COMPUTE_STATES;
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

        // also get compute states representing regions
        for (RegionInfo region : ctx.regions.values()) {
            Query instanceIdFilter = Query.Builder.create(Occurance.SHOULD_OCCUR)
                    .addFieldClause(ComputeState.FIELD_NAME_ID, region.regionId)
                    .build();
            instanceIdFilterParentQuery.addClause(instanceIdFilter);
        }

        qBuilder.addClause(instanceIdFilterParentQuery.build());

        QueryByPages<ComputeState> queryLocalStates = new QueryByPages<>(
                getHost(),
                qBuilder.build(),
                ComputeState.class,
                ctx.parentCompute.tenantLinks,
                null, // endpointLink
                ctx.parentCompute.documentSelfLink)
                // Use max page size cause we collect ComputeStates
                .setMaxPageSize(QueryUtils.MAX_RESULT_LIMIT)
                .setClusterType(ServiceTypeCluster.INVENTORY_SERVICE);

        queryLocalStates
                .queryDocuments(c -> ctx.computeStates.put(c.id, c))
                .whenComplete(thenHandleSubStage(ctx, next));
    }

    /**
     * Create tag states for the VM's tags
     */
    private void createTagStates(EnumerationContext context, ComputeEnumerationSubStages next) {
        logFine(() -> "Create or update Tag States for discovered VMs with the actual state in Azure.");

        if (context.virtualMachines.isEmpty()) {
            logFine("No VMs found, so no tags need to be created/updated. Continue to update compute states.");

            if (context.regions.isEmpty()) {
                context.subStage = ComputeEnumerationSubStages.DISASSOCIATE_COMPUTE_STATES;
                handleSubStage(context);
                return;
            } else {
                context.subStage = ComputeEnumerationSubStages.UPDATE_COMPUTE_STATES;
                handleSubStage(context);
                return;
            }
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
                .filter(p -> p != null && p.getLeft() != null && p.getRight() != null)
                .map(p -> {
                    ComputeState cs = p.getLeft();

                    // Update data disks if added externally
                    VirtualMachineInner vm = p.getRight();
                    // No. of data disks + OS disk
                    int disksCount = vm.storageProfile().dataDisks().size() + 1;

                    if (disksCount != cs.diskLinks.size()) {
                        vm.storageProfile().dataDisks().forEach(dataDisk -> {
                            String dataDiskId;
                            if (isDiskManaged(vm)) {
                                dataDiskId = dataDisk.managedDisk().id();
                            } else {
                                dataDiskId = dataDisk.vhd().uri();
                            }
                            String diskStateLink = ctx.diskStates.get(dataDiskId).documentSelfLink;

                            if (!cs.diskLinks.contains(diskStateLink)) {
                                cs.diskLinks.add(diskStateLink);
                            }
                        });
                    }

                    Map<String, String> tags = p.getRight().getTags();
                    DeferredResult<Set<String>> result = DeferredResult.completed(null);
                    if (tags != null && !tags.isEmpty()) {
                        Set<String> tagLinks = cs.tagLinks;
                        cs.tagLinks = null;
                        result = updateLocalTagStates(this, cs, tagLinks, tags, null);
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

    private void updateRegions(EnumerationContext ctx, ComputeEnumerationSubStages next) {
        if (ctx.regionIds.isEmpty()) {
            logFine(() -> "No compute regions needs to updated.");
            ctx.subStage = next;
            handleSubStage(ctx);
            return;
        }

        Map<String, ComputeState> existingRegionIds =
                ctx.computeStates.entrySet().stream()
                    .filter(entry -> ctx.regions.containsKey(entry.getKey()))
                    .collect(Collectors.toMap(p -> p.getKey(), p -> p.getValue()));

        ctx.regions.keySet().removeAll(existingRegionIds.keySet());
        ctx.computeStates.keySet().removeAll(existingRegionIds.keySet());

        DeferredResult.allOf(
                existingRegionIds.values().stream().map(
                        compute -> {
                            Map<String, Collection<Object>> collectionsToAddMap = Collections.singletonMap
                                    (ResourceState.FIELD_NAME_ENDPOINT_LINKS,
                                            Collections.singletonList(ctx.request.endpointLink));

                            ServiceStateCollectionUpdateRequest updateEndpointLinksRequest = ServiceStateCollectionUpdateRequest
                                    .create(collectionsToAddMap, null);

                            return this.sendWithDeferredResult(Operation.createPatch(this.getHost(), compute.documentSelfLink)
                                    .setReferer(this.getHost().getUri())
                                    .setBody(updateEndpointLinksRequest));
                        }).collect(Collectors.toList()))
                .whenComplete(thenHandleSubStage(ctx, next));
    }

    private void updateComputeDescriptions(EnumerationContext ctx, ComputeEnumerationSubStages next) {
        if (ctx.computeStatesForPatching.isEmpty()) {
            logFine(() -> "No compute descriptions needs to updated.");
            ctx.subStage = next;
            handleSubStage(ctx);
            return;
        }

        DeferredResult.allOf(
                ctx.computeStatesForPatching.values().stream().map(
                        entry -> {
                            Map<String, Collection<Object>> collectionsToAddMap = Collections.singletonMap
                                    (ComputeDescription.FIELD_NAME_ENDPOINT_LINKS,
                                            Collections.singletonList(ctx.request.endpointLink));

                            ServiceStateCollectionUpdateRequest updateEndpointLinksRequest = ServiceStateCollectionUpdateRequest
                                    .create(collectionsToAddMap, null);

                            return this.sendWithDeferredResult(Operation.createPatch(this.getHost(), entry.descriptionLink)
                                    .setReferer(this.getHost().getUri())
                                    .setBody(updateEndpointLinksRequest));
                        }).collect(Collectors.toList()))
                .whenComplete(thenHandleSubStage(ctx, next));
    }

    /**
     * Get all disk states related to given VMs
     */
    private void queryForDiskStates(EnumerationContext ctx, ComputeEnumerationSubStages next) {
        if (ctx.virtualMachines.size() == 0) {
            logFine(() -> "No virtual machines found to be associated with local disks");

            if (ctx.regions.isEmpty()) {
                ctx.subStage = ComputeEnumerationSubStages.DISASSOCIATE_COMPUTE_STATES;
                handleSubStage(ctx);
                return;
            } else {
                ctx.subStage = ComputeEnumerationSubStages.UPDATE_COMPUTE_STATES;
                handleSubStage(ctx);
                return;
            }
        }
        ctx.diskStates.clear();

        List<String> diskIdList = new ArrayList<>();

        for (String instanceId : ctx.virtualMachines.keySet()) {
            VirtualMachineInner virtualMachine = ctx.virtualMachines.get(instanceId);

            String diskId = getVhdUri(virtualMachine);
            if (diskId == null) {
                continue;
            }

            diskIdList.add(diskId);

            List<String> dataDiskIDList = getDataDisksID(virtualMachine, AzureUtils.isDiskManaged(virtualMachine));
            if (null != dataDiskIDList && dataDiskIDList.size() > 0) {
                diskIdList.addAll(dataDiskIDList);
            }
        }

        Query.Builder qBuilder = Query.Builder.create()
                .addKindFieldClause(DiskState.class)
                .addInClause(DiskState.FIELD_NAME_ID, diskIdList, Occurance.SHOULD_OCCUR);

        QueryTop<DiskState> queryDiskStates = new QueryTop<>(
                getHost(),
                qBuilder.build(),
                DiskState.class,
                ctx.parentCompute.tenantLinks,
                null, // endpointLink
                ctx.parentCompute.documentSelfLink)
                // Use max 10K Top, cause we collect DiskStates
                .setClusterType(ServiceTypeCluster.INVENTORY_SERVICE);

        queryDiskStates.collectDocuments(Collectors.toList())
                .whenComplete((diskStates, e) -> {
                    if (e != null) {
                        handleError(ctx, e);
                        return;
                    }
                    if (diskStates == null) {
                        return;
                    }
                    diskStates.forEach(diskState -> ctx.diskStates.put(diskState.id, diskState));
                    ctx.subStage = next;
                    handleSubStage(ctx);
                });
    }

    /**
     * Get all storage descriptions responsible for diagnostics of given VMs.
     */
    private void queryForDiagnosticStorageDescriptions(EnumerationContext ctx,
            ComputeEnumerationSubStages next) {

        List<String> diagnosticStorageAccountUris = new ArrayList<>();

        String storageAccountProperty = QuerySpecification
                .buildCompositeFieldName(
                        StorageDescription.FIELD_NAME_CUSTOM_PROPERTIES,
                        AZURE_STORAGE_ACCOUNT_URI);

        ctx.virtualMachines.keySet().stream()
                .filter(instanceId -> ctx.virtualMachines.get(instanceId) != null
                        && ctx.virtualMachines.get(instanceId).diagnosticsProfile() != null
                        && ctx.virtualMachines.get(instanceId).diagnosticsProfile()
                        .bootDiagnostics() != null
                        && ctx.virtualMachines.get(instanceId).diagnosticsProfile()
                        .bootDiagnostics().storageUri() != null)
                .forEach(instanceId -> {
                    diagnosticStorageAccountUris.add(ctx.virtualMachines
                            .get(instanceId).diagnosticsProfile().bootDiagnostics()
                            .storageUri());
                });

        if (diagnosticStorageAccountUris.isEmpty()) {
            ctx.subStage = next;
            handleSubStage(ctx);
            return;
        }

        Query.Builder qBuilder = Query.Builder.create()
                .addKindFieldClause(StorageDescription.class)
                .addInClause(storageAccountProperty, diagnosticStorageAccountUris);

        QueryTop<StorageDescription> queryDiskStates = new QueryTop<>(
                getHost(),
                qBuilder.build(),
                StorageDescription.class,
                ctx.parentCompute.tenantLinks,
                null, // endpointLink
                ctx.parentCompute.documentSelfLink)
                // Use max 10K Top, cause we collect DiskStates
                .setClusterType(ServiceTypeCluster.INVENTORY_SERVICE);

        queryDiskStates
                .queryDocuments(storageDesc -> {
                    ctx.storageDescriptions.put(
                            storageDesc.customProperties.get(AZURE_STORAGE_ACCOUNT_URI),
                            storageDesc);
                })
                .thenRun(() -> logFine(() -> String.format(
                        "Found %d matching diagnostics storage accounts",
                        ctx.storageDescriptions.size())))
                .whenComplete(thenHandleSubStage(ctx, next));
    }

    /**
     * Creates relevant resources for given VMs.
     */
    private void createComputeDescriptions(EnumerationContext ctx,
            ComputeEnumerationSubStages next) {
        if (ctx.virtualMachines.size() == 0 && ctx.regions.isEmpty()) { // nothing to create
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
                    .createPost(createInventoryUri(getHost(), AuthCredentialsService.FACTORY_LINK))
                    .setBody(auth);
            opCollection.add(authOp);

            // TODO VSYM-631: Match existing descriptions for new VMs discovered on Azure
            ComputeDescription computeDescription = new ComputeDescription();
            computeDescription.id = UUID.randomUUID().toString();
            computeDescription.name = virtualMachine.name();
            computeDescription.regionId = virtualMachine.location();
            computeDescription.authCredentialsLink = authLink;
            computeDescription.endpointLink = ctx.request.endpointLink;
            AdapterUtils.addToEndpointLinks(computeDescription, ctx.request.endpointLink);

            computeDescription.documentSelfLink = computeDescription.id;
            computeDescription.environmentName = ENVIRONMENT_NAME_AZURE;
            if (virtualMachine.hardwareProfile() != null
                    && virtualMachine.hardwareProfile().vmSize() != null) {
                computeDescription.instanceType = virtualMachine.hardwareProfile().vmSize()
                        .toString();
            }
            computeDescription.instanceAdapterReference = ctx.parentCompute.description.instanceAdapterReference;
            computeDescription.statsAdapterReference = ctx.parentCompute.description.statsAdapterReference;
            computeDescription.diskAdapterReference = ctx.parentCompute.description.diskAdapterReference;
            computeDescription.computeHostLink = ctx.parentCompute.documentSelfLink;
            computeDescription.customProperties = new HashMap<>();
            computeDescription.customProperties.put(SOURCE_TASK_LINK,
                    ResourceEnumerationTaskService.FACTORY_LINK);

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

        for (RegionInfo region : ctx.regions.values()) {
            ComputeDescription computeDescriptionForRegion = createComputeDescriptionForRegion(ctx,
                    region);
            Operation compDescOp = Operation
                    .createPost(getHost(), ComputeDescriptionService.FACTORY_LINK)
                    .setBody(computeDescriptionForRegion);
            ctx.computeDescriptionIds.put(region.regionId, computeDescriptionForRegion.id);
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

            if (ctx.regions.isEmpty()) {
                ctx.subStage = ComputeEnumerationSubStages.DISASSOCIATE_COMPUTE_STATES;
                handleSubStage(ctx);
                return;
            } else {
                ctx.subStage = ComputeEnumerationSubStages.UPDATE_COMPUTE_STATES;
                handleSubStage(ctx);
                return;
            }
        }
        Iterator<Entry<String, VirtualMachineInner>> iterator = ctx.virtualMachines.entrySet()
                .iterator();

        Collection<Operation> opCollection = new ArrayList<>();
        while (iterator.hasNext()) {
            Entry<String, VirtualMachineInner> vmEntry = iterator.next();
            VirtualMachineInner virtualMachine = vmEntry.getValue();

            if (virtualMachine.storageProfile() == null
                    || virtualMachine.storageProfile().imageReference() == null) {
                continue;
            }

            if (virtualMachine.storageProfile().osDisk() == null) {
                logWarning(() -> "VM has empty OS disk.");
                continue;
            }

            String osDiskUri = getVhdUri(virtualMachine);
            if (osDiskUri == null) {
                logFine(() -> String.format("OS Disk URI not found for vm: %s", virtualMachine.id()));
                continue;
            }
            updateOSDiskProperties(ctx, virtualMachine, osDiskUri, opCollection);

            // create data disk states and update their custom properties
            updateDataDiskProperties(ctx, virtualMachine, opCollection);
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

    private DiskState createOSDiskState(VirtualMachineInner vm, EnumerationContext ctx) {
        DiskState diskState = new DiskState();
        diskState.id = getVhdUri(vm);
        if (vm.storageProfile() != null
                && vm.storageProfile().osDisk() != null
                && vm.storageProfile().osDisk().diskSizeGB() != null) {
            OSDisk osDisk = vm.storageProfile().osDisk();
            diskState.capacityMBytes = osDisk.diskSizeGB() * 1024;
            diskState.name = osDisk.name();
        }
        diskState.computeHostLink = ctx.parentCompute.documentSelfLink;
        diskState.resourcePoolLink = ctx.request.resourcePoolLink;
        diskState.tenantLinks = ctx.parentCompute.tenantLinks;
        diskState.status = DiskService.DiskStatus.ATTACHED;
        String id = UUID.randomUUID().toString();
        diskState.documentSelfLink = UriUtils.buildUriPath(DiskService.FACTORY_LINK, id);
        diskState.endpointLink = ctx.request.endpointLink;
        AdapterUtils.addToEndpointLinks(diskState, ctx.request.endpointLink);
        return diskState;
    }

    private void updateDiskCustomProperties(VirtualMachineInner vm, EnumerationContext ctx,
                                            DiskState diskToUpdate) {
        ImageReferenceInner imageReference = vm.storageProfile()
                .imageReference();
        diskToUpdate.sourceImageReference = URI.create(imageReferenceToImageId(imageReference));
        diskToUpdate.bootOrder = 1;
        if (diskToUpdate.customProperties == null) {
            diskToUpdate.customProperties = new HashMap<>();
        }
        if (vm.storageProfile().osDisk() != null
                && vm.storageProfile().osDisk().caching() != null) {
            diskToUpdate.customProperties.put(AZURE_OSDISK_CACHING,
                    vm.storageProfile().osDisk().caching().name());
        }
        diskToUpdate.computeHostLink = ctx.parentCompute.documentSelfLink;
        if (StringUtils.isEmpty(diskToUpdate.endpointLink)) {
            diskToUpdate.endpointLink = ctx.request.endpointLink;
        }
        AdapterUtils.addToEndpointLinks(diskToUpdate, ctx.request.endpointLink);
    }

    private void updateOSDiskProperties(EnumerationContext ctx, VirtualMachineInner virtualMachine, String diskUri,
                                        Collection<Operation> opCollection) {
        DiskState diskToUpdate = ctx.diskStates.get(diskUri);
        Operation diskToUpdateOp = null;
        if (diskToUpdate == null) {
            diskToUpdate = createOSDiskState(virtualMachine, ctx);
            updateDiskCustomProperties(virtualMachine, ctx, diskToUpdate);
            diskToUpdateOp = Operation.createPost(getHost(), DiskService.FACTORY_LINK)
                    .setBody(diskToUpdate);
        } else {
            updateDiskCustomProperties(virtualMachine, ctx, diskToUpdate);
            diskToUpdateOp = Operation.createPatch(getHost(), diskToUpdate.documentSelfLink)
                    .setBody(diskToUpdate);
        }
        opCollection.add(diskToUpdateOp);
        ctx.diskStates.put(diskToUpdate.id, diskToUpdate);
    }

    private DiskState createDataDiskState(EnumerationContext ctx, DataDisk dataDisk, boolean isManaged) {

        DiskState diskState = new DiskState();

        diskState.documentSelfLink = UriUtils.buildUriPath(
                DiskService.FACTORY_LINK, UUID.randomUUID().toString());
        diskState.name = dataDisk.name();
        diskState.capacityMBytes = dataDisk.diskSizeGB() * 1024;
        diskState.status = DiskService.DiskStatus.ATTACHED;
        diskState.tenantLinks = ctx.parentCompute.tenantLinks;
        diskState.resourcePoolLink = ctx.request.resourcePoolLink;
        diskState.computeHostLink = ctx.parentCompute.documentSelfLink;

        diskState.endpointLink = ctx.request.endpointLink;
        AdapterUtils.addToEndpointLinks(diskState, ctx.request.endpointLink);

        diskState.customProperties = new HashMap<>();
        diskState.customProperties.put(AZURE_DATA_DISK_CACHING, dataDisk.caching().name());
        diskState.customProperties.put(DISK_CONTROLLER_NUMBER, String.valueOf(dataDisk.lun()));

        if (isManaged) {
            diskState.id = dataDisk.managedDisk().id();
            diskState.customProperties.put(AZURE_MANAGED_DISK_TYPE,
                    dataDisk.managedDisk().storageAccountType().toString());
        } else {
            diskState.id = AzureUtils.canonizeId(dataDisk.vhd().uri());
        }

        return diskState;
    }

    private void updateDataDiskProperties(EnumerationContext ctx, VirtualMachineInner vm,
                                          Collection<Operation> opCollection) {
        if (vm.storageProfile() == null) {
            return;
        }
        if (vm.storageProfile().dataDisks() != null) {
            vm.storageProfile().dataDisks().forEach(dataDisk -> {
                DiskState diskToUpdate = null;
                if (AzureUtils.isDiskManaged(vm)) {
                    diskToUpdate = ctx.diskStates.get(dataDisk.managedDisk().id());
                } else {
                    diskToUpdate = ctx.diskStates.get(dataDisk.vhd().uri());
                }

                Operation diskToUpdateOp = null;
                if (null == diskToUpdate) {
                    diskToUpdate = createDataDiskState(ctx, dataDisk, AzureUtils.isDiskManaged(vm));
                    diskToUpdateOp = Operation.createPost(getHost(), DiskService.FACTORY_LINK)
                            .setBody(diskToUpdate);
                } else {
                    diskToUpdateOp = Operation.createPatch(getHost(), diskToUpdate.documentSelfLink)
                            .setBody(diskToUpdate);
                }
                opCollection.add(diskToUpdateOp);
                ctx.diskStates.put(diskToUpdate.id, diskToUpdate);
            });
        }
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
            logWarning(
                    "Failure getting Azure network interface states [endpointLink:%s] [Exception:%s]",
                    ctx.request.endpointLink, e.getMessage());
            handleError(ctx, e);
        };

        PhotonModelUtils.runInExecutor(this.executorService, () -> {

            Azure azureClient = ctx.azureSdkClients.getAzureClient();

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
                            .thenCompose(subnetPerNicId -> doCreateUpdateNics(ctx, subnetPerNicId,
                                    rnics)))
                    .whenComplete(thenHandleSubStage(ctx, next));
        }, failure);
    }

    /**
     * Manages creating and updating Network Interfaces resources based on network interfaces
     * associated with virtual machines.
     */
    private DeferredResult<List<NetworkInterfaceState>> doCreateUpdateNics(EnumerationContext ctx,
            Map<String, String> subnetPerNicId,
            List<Pair<NetworkInterfaceInner, String>> remoteNics) {

        Map<String, Pair<NetworkInterfaceInner, String>> remoteStates = remoteNics.stream()
                .filter(p -> p.getLeft() != null)
                .collect(Collectors.toMap(p -> p.getLeft().id(), p -> p));
        Query.Builder qBuilder = Query.Builder.create()
                .addKindFieldClause(NetworkInterfaceState.class)
                .addInClause(NetworkInterfaceState.FIELD_NAME_ID, remoteStates.keySet());

        QueryByPages<NetworkInterfaceState> queryLocalStates = new QueryByPages<>(getHost(),
                qBuilder.build(),
                NetworkInterfaceState.class,
                ctx.parentCompute.tenantLinks,
                null, // endpointLink
                ctx.parentCompute.documentSelfLink)
                // Use max page size cause we collect NetworkInterfaceState
                .setMaxPageSize(QueryUtils.MAX_RESULT_LIMIT)
                .setClusterType(ServiceTypeCluster.INVENTORY_SERVICE);

        return queryLocalStates
                .collectDocuments(Collectors.toList())
                .thenCompose(localNics -> requestCreateUpdateNic(
                        localNics, remoteStates, ctx, subnetPerNicId, remoteNics));
    }

    /**
     * Serves request for creating and updating Network Interfaces resources
     */
    private DeferredResult<List<NetworkInterfaceState>> requestCreateUpdateNic(
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

        // Disassociate the NICs that exist locally but not on remote.
        List<String> remoteNicIds = new ArrayList<>();
        // Collect IDs of all remote network interfaces. Delete all local states that
        // have IDs other than remoteNicIds (i.e. stale states).
        remoteNics.stream()
                .forEach(pair -> remoteNicIds.add(pair.getLeft().id()));

        disassociateNicHelper(remoteNicIds, ctx);
        return DeferredResult.allOf(ops);
    }

    /**
     * Helper for deleting stale network interfaces.
     */
    private DeferredResult<List<Operation>> disassociateNicHelper(
            List<String> remoteNicIds, EnumerationContext ctx) {

        Query.Builder qBuilder = Query.Builder.create()
                .addKindFieldClause(NetworkInterfaceState.class);

        QueryByPages<NetworkInterfaceState> queryLocalStates = new QueryByPages<>(getHost(),
                qBuilder.build(),
                NetworkInterfaceState.class,
                ctx.parentCompute.tenantLinks,
                ctx.request.endpointLink)
                // Use max page size cause we collect NetworkInterfaceState
                .setMaxPageSize(QueryUtils.MAX_RESULT_LIMIT)
                .setClusterType(ServiceTypeCluster.INVENTORY_SERVICE);

        return queryLocalStates
                .collectDocuments(Collectors.toList())
                .thenCompose(allLocalNics -> disassociateNics(ctx, remoteNicIds, allLocalNics));
    }

    /**
     * Deletes stale network interface states that are deleted from the remote.
     */
    private DeferredResult<List<Operation>> disassociateNics(
            EnumerationContext ctx, List<String> remoteNicIds, List<NetworkInterfaceState> allLocalNics) {

        List<DeferredResult<Operation>> updateOps = new ArrayList<>();

        allLocalNics.stream()
                .filter(localNic -> !remoteNicIds.contains(localNic.id))
                .forEach(localNic -> {
                    Operation upOp = PhotonModelUtils.createRemoveEndpointLinksOperation
                            (this, ctx.request.endpointLink, localNic);
                    if (upOp != null) {
                        CompletionHandler completionHandler = upOp.getCompletion();
                        upOp.setCompletion(null);

                        updateOps.add(sendWithDeferredResult(upOp).whenComplete((o, e) -> {
                            completionHandler.handle(o, e);
                        }));
                    }
                });

        return DeferredResult.allOf(updateOps);
    }

    private void updateNic(List<NetworkInterfaceState> localNics,
            Map<String, Pair<NetworkInterfaceInner, String>> remoteStates, EnumerationContext ctx,
            Map<String, String> subnetPerNicId, List<DeferredResult<NetworkInterfaceState>> ops) {

        localNics.stream().forEach(nic -> {
            // fetch and remove local NIC present in 'remoteStates' so that only new NICs are left
            // present before we move forward to create new NICs.
            Pair<NetworkInterfaceInner, String> pair = remoteStates.remove(nic.id);
            if (pair != null) {
                NetworkInterfaceInner remoteNic = pair.getLeft();
                processCreateUpdateNicRequest(nic, remoteNic, ctx, ops, subnetPerNicId, false);
            }
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
            AdapterUtils.addToEndpointLinks(nic, ctx.request.endpointLink);
            nic.tenantLinks = ctx.parentCompute.tenantLinks;
            nic.regionId = remoteNic.location();
            nic.computeHostLink = ctx.parentCompute.documentSelfLink;
        } else {
            if (StringUtils.isEmpty(nic.endpointLink)) {
                nic.endpointLink = ctx.request.endpointLink;
            }
            nic.endpointLinks.add(ctx.request.endpointLink);
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
            logWarning(
                    "Error getting public IP address from Azure [endpointLink:%s], [Exception:%s]",
                    ctx.request.endpointLink, e.getMessage());

            handleError(ctx, e);
        };

        PhotonModelUtils.runInExecutor(this.executorService, () -> {

            Azure azure = ctx.azureSdkClients.getAzureClient();

            azure.publicIPAddresses()
                    .getByIdAsync(nicIPConf.publicIPAddress().id())
                    .subscribe(injectOperationContext(new Action1<PublicIPAddress>() {
                        @Override
                        public void call(PublicIPAddress publicIPAddress) {
                            nicMeta.publicIp = publicIPAddress.ipAddress();
                            if (publicIPAddress.inner().dnsSettings() != null) {
                                nicMeta.publicDnsName = publicIPAddress.inner().dnsSettings()
                                        .fqdn();
                            }
                            executeNicCreateUpdateRequest(nic, remoteNic, ctx, ops, nicMeta,
                                    isCreate);
                        }
                    }));
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

        AzureDeferredResultServiceCallback<NetworkInterfaceInner> handler = new Default<>(
                this, "Getting Azure NIC: " + pair.getLeft().id());

        String networkInterfaceName = UriUtils.getLastPathSegment(pair.getLeft().id());
        String nicRG = getResourceGroupName(pair.getLeft().id());
        String resourceGroupName = getResourceGroupName(pair.getRight());
        if (!resourceGroupName.equalsIgnoreCase(nicRG)) {
            logWarning(
                    "VM resource group %s is different from nic resource group %s, for nic %s",
                    resourceGroupName, nicRG, pair.getLeft().id());
        }

        PhotonModelUtils.runInExecutor(this.executorService, () -> {
            netOps.getByResourceGroupAsync(resourceGroupName, networkInterfaceName,
                    "ipConfigurations/publicIPAddress", handler);
        }, handler::failure);

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
                qBuilder.build(),
                SubnetState.class,
                ctx.parentCompute.tenantLinks,
                null, // endpointLink
                ctx.parentCompute.documentSelfLink)
                // Use max page size cause we collect SubnetState
                .setMaxPageSize(QueryUtils.MAX_RESULT_LIMIT)
                .setClusterType(ServiceTypeCluster.INVENTORY_SERVICE);

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
        if (ctx.virtualMachines.isEmpty() && ctx.regions.isEmpty()) {
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


        List<DeferredResult<ComputeState>> regionResults = ctx.regions.values()
                .stream()
                .map(regionInfo -> createComputeInstanceForRegion(ctx, regionInfo))
                .map(computeState -> sendWithDeferredResult(Operation.createPost(
                        ctx.request.buildUri(ComputeService.FACTORY_LINK))
                        .setBody(computeState), ComputeState.class))
                .collect(java.util.stream.Collectors.toList());

        List<DeferredResult<ComputeState>> allRequests = new ArrayList<>();

        allRequests.addAll(results);
        allRequests.addAll(regionResults);

        DeferredResult.allOf(allRequests).whenComplete((all, e) -> {
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

    private ComputeState createComputeState(EnumerationContext ctx,
            VirtualMachineInner virtualMachine) {

        List<String> vmDisks = new ArrayList<>();
        if (ctx.diskStates != null && ctx.diskStates.size() > 0) {
            String diskUri = getVhdUri(virtualMachine);
            if (diskUri != null) {
                DiskState state = ctx.diskStates.remove(diskUri);
                if (state != null) {
                    vmDisks.add(state.documentSelfLink);
                }
            }

            // add all data disk links of VM
            List<String> dataDiskIDs = getDataDisksID(virtualMachine, AzureUtils.isDiskManaged(virtualMachine));

            if (dataDiskIDs != null) {
                dataDiskIDs.forEach(dataDiskID -> {
                    DiskState dataDiskState = ctx.diskStates.remove(dataDiskID);
                    if (null != dataDiskState) {
                        vmDisks.add(dataDiskState.documentSelfLink);
                    }
                });
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
        AdapterUtils.addToEndpointLinks(computeState, ctx.request.endpointLink);
        computeState.resourcePoolLink = ctx.request.resourcePoolLink;
        computeState.computeHostLink = ctx.parentCompute.documentSelfLink;
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

        if (virtualMachine.diagnosticsProfile() != null
                && virtualMachine.diagnosticsProfile().bootDiagnostics() != null
                && virtualMachine.diagnosticsProfile().bootDiagnostics().storageUri() != null) {
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
            handleError(ctx, e);
        };

        PhotonModelUtils.runInExecutor(this.executorService, () -> {

            Azure azureClient = ctx.azureSdkClients.getAzureClient();

            VirtualMachinesInner vmOps = azureClient.virtualMachines().inner();

            DeferredResult.allOf(ctx.computeStatesForPatching
                    .values().stream()
                    .map(c -> patchVMInstanceDetails(ctx, vmOps, c))
                    .map(dr -> dr.thenCompose(c -> sendWithDeferredResult(
                            Operation.createPatch(ctx.request.buildUri(c.documentSelfLink))
                                    .setBody(c)
                                    .setCompletion((o, e) -> {
                                        if (e != null) {
                                            logWarning(() -> String.format(
                                                    "Error updating VM:[%s], reason: %s", c.name,
                                                    e));
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

        AzureDeferredResultServiceCallback<VirtualMachineInner> handler = new Default<>(
                this, "Load virtual machine instance view:" + vmName);

        PhotonModelUtils.runInExecutor(this.executorService, () -> {
            vmOps.getByResourceGroupAsync(resourceGroupName, vmName,
                    InstanceViewTypes.INSTANCE_VIEW, handler);
        }, handler::failure);

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
            computeState.endpointLinks.add(ctx.request.endpointLink);

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

    private List<String> getDataDisksID(VirtualMachineInner vm, boolean isManaged) {
        if (vm.storageProfile() != null &&
                vm.storageProfile().dataDisks() != null) {
            if (isManaged) {
                return vm.storageProfile()
                        .dataDisks()
                        .stream()
                        .map(dataDisk -> dataDisk.managedDisk().id())
                        .collect(Collectors.toList());
            } else {
                return vm.storageProfile()
                        .dataDisks()
                        .stream()
                        .map(dataDisk -> AzureUtils.canonizeId(dataDisk.vhd().uri()))
                        .collect(Collectors.toList());
            }
        } else {
            logWarning(() -> "VM has empty storage profile, or zero data disks.");
            return null;
        }
    }

    private String getVhdUri(VirtualMachineInner vm) {
        OSDisk osDisk;
        if (vm.storageProfile() == null || vm.storageProfile().osDisk() == null) {
            logWarning(() -> "VM has empty storage profile, or OS disk.");
            return null;
        } else {
            osDisk = vm.storageProfile().osDisk();

            if (isDiskManaged(vm)) {
                return osDisk.managedDisk().id();
            } else {
                return AzureUtils.canonizeId(osDisk.vhd().uri());
            }
        }
    }

}
