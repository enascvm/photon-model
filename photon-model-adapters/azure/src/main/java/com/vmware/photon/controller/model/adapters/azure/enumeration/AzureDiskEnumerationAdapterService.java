/*
 * Copyright (c) 2017-2018 VMware, Inc. All Rights Reserved.
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
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_MANAGED_DISK_TYPE;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.DISK_CONTROLLER_NUMBER;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.DISK_REST_API_VERSION;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.DISK_STATUS_UNATTACHED;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.LIST_DISKS_URI;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.QUERY_PARAM_API_VERSION;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.getAzureConfig;
import static com.vmware.photon.controller.model.adapters.util.AdapterUtils.getDeletionState;
import static com.vmware.photon.controller.model.util.PhotonModelUriUtils.createInventoryUri;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.microsoft.azure.credentials.ApplicationTokenCredentials;

import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.model.storage.Disk;
import com.vmware.photon.controller.model.adapters.azure.model.storage.ManagedDiskList;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.ComputeEnumerateAdapterRequest;
import com.vmware.photon.controller.model.adapters.util.TagsUtil;
import com.vmware.photon.controller.model.adapters.util.enums.EnumerationStages;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.query.QueryUtils.QueryByPages;
import com.vmware.photon.controller.model.query.QueryUtils.QueryTop;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.DiskService.DiskStatus;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.resources.util.PhotonModelUtils;
import com.vmware.photon.controller.model.util.ClusterUtil;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;

/**
 * Enumeration adapter for data collection of Managed Disk related resources on Azure.
 *
 * Get all managed disks from Azure.
 * Get local disk states.
 * Update local disk states matching the disk IDs.
 * Or create disk states for the disks which are independent and not in local.
 * Delete disk states from local store that are not present in Azure.
 *
 */
public class AzureDiskEnumerationAdapterService extends StatelessService {

    public static final String SELF_LINK = AzureUriPaths.AZURE_DISK_ENUMERATION_ADAPTER;

    private enum DiskEnumStages {
        CREATE_INTERNAL_TYPE_TAG,
        GET_DISKS,
        GET_LOCAL_DISK_STATES,
        UPDATE_LOCAL_DISK_STATES,
        DISASSOCIATE_ENDPOINT_LINKS,
        FINISHED
    }

    public static class DiskEnumContext {
        ComputeEnumerateResourceRequest request;

        ComputeService.ComputeStateWithDescription parentCompute;

        EnumerationStages stage;

        DiskEnumStages subStage;

        // Used to store an error while transferring to the error stage.
        Throwable error;

        AuthCredentialsService.AuthCredentialsServiceState endpointAuth;

        // Azure credentials.
        ApplicationTokenCredentials credentials;

        Operation operation;

        long enumerationStartTimeInMicros;

        public Set<String> internalTagLinks = new HashSet<>();

        // key -> Managed disk id and value -> DataDisk
        Map<String, Disk> managedDisks = new ConcurrentHashMap<>();

        Map<String, Disk> unattachedDisks = new ConcurrentHashMap<>();

        // key -> DiskState id (same as Managed disk id) and value -> DiskState
        Map<String, DiskState> localDiskStates = new ConcurrentHashMap<>();

        // Stores the next page when retrieving Disks from Azure.
        String enumNextPageLink;

        ResourceState resourceDeletionState;

        public DiskEnumContext(ComputeEnumerateAdapterRequest request,
                               Operation op) {
            this.request = request.original;
            this.endpointAuth = request.endpointAuth;
            this.parentCompute = request.parentCompute;

            this.operation = op;
            this.stage = EnumerationStages.CLIENT;
            this.resourceDeletionState = getDeletionState(request.original
                    .deletedResourceExpirationMicros);
        }
    }

    public AzureDiskEnumerationAdapterService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handlePatch(Operation patch) {
        if (!patch.hasBody()) {
            patch.fail(new IllegalArgumentException("body is required"));
            return;
        }
        DiskEnumContext context = new DiskEnumContext(
                patch.getBody(ComputeEnumerateAdapterRequest.class),
                patch);
        AdapterUtils.validateEnumRequest(context.request);
        if (context.request.isMockRequest) {
            patch.complete();
            return;
        }
        handleEnumeration(context);
    }

    private void handleEnumeration(DiskEnumContext ctx) {
        switch (ctx.stage) {

        case CLIENT:
            if (ctx.credentials == null) {
                try {
                    ctx.credentials = getAzureConfig(ctx.endpointAuth);
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
                logInfo(() -> String.format("Launching Azure Managed Disks enumeration for %s", enumKey));
                ctx.request.enumerationAction = EnumerationAction.REFRESH;
                handleEnumeration(ctx);
                break;
            case REFRESH:
                ctx.enumerationStartTimeInMicros = Utils.getNowMicrosUtc();
                ctx.subStage = DiskEnumStages.CREATE_INTERNAL_TYPE_TAG;
                handleSubStage(ctx);
                break;
            case STOP:
                logInfo(() -> String.format("Azure Managed Disks enumeration will be stopped for %s",
                        enumKey));
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
            logInfo(() -> String.format("Azure Managed Disks enumeration finished for %s",
                    getEnumKey(ctx)));
            ctx.operation.complete();
            break;
        case ERROR:
            logWarning(() -> String.format("Azure Managed Disks enumeration error for %s",
                    getEnumKey(ctx)));
            ctx.operation.fail(ctx.error);
            break;
        default:
            String msg = String
                    .format("Unknown Azure Managed Disks enumeration stage %s",
                            ctx.stage.toString());
            logSevere(() -> msg);
            ctx.error = new IllegalStateException(msg);
            ctx.operation.fail(ctx.error);
        }
    }

    private void handleSubStage(DiskEnumContext ctx) {
        switch (ctx.subStage) {

        case CREATE_INTERNAL_TYPE_TAG:
            createInternalTypeTag(ctx, DiskEnumStages.GET_DISKS);
            break;
        case GET_DISKS:
            getManagedDisks(ctx, DiskEnumStages.GET_LOCAL_DISK_STATES);
            break;
        case GET_LOCAL_DISK_STATES:
            getLocalDiskStates(ctx, DiskEnumStages.UPDATE_LOCAL_DISK_STATES);
            break;
        case UPDATE_LOCAL_DISK_STATES:
            createUpdateDiskStates(ctx, DiskEnumStages.DISASSOCIATE_ENDPOINT_LINKS);
            break;
        case DISASSOCIATE_ENDPOINT_LINKS:
            disassociateEndpointLinksFromDiskStates(ctx, DiskEnumStages.FINISHED);
            break;
        case FINISHED:
            ctx.stage = EnumerationStages.FINISHED;
            handleEnumeration(ctx);
            break;
        default:
            break;
        }
    }

    private void handleError(DiskEnumContext ctx, Throwable e) {
        logSevere(() -> String.format("Failed at stage %s with exception: %s", ctx.stage,
                Utils.toString(e)));
        ctx.error = e;
        ctx.stage = EnumerationStages.ERROR;
        handleEnumeration(ctx);
    }

    private void getManagedDisks(DiskEnumContext ctx, DiskEnumStages nextStage) {
        logInfo(() -> "Enumerating Azure Managed disks.");

        URI uri;
        if (ctx.enumNextPageLink == null) {
            // TODO- change to use sdk to fetch disks
            String uriStr = AdapterUriUtil.expandUriPathTemplate(LIST_DISKS_URI,
                    ctx.endpointAuth.userLink);
            uri = UriUtils.extendUriWithQuery(
                    UriUtils.buildUri(uriStr),
                    QUERY_PARAM_API_VERSION, DISK_REST_API_VERSION);
        } else {
            uri = UriUtils.buildUri(ctx.enumNextPageLink);
        }

        final Operation operation = Operation.createGet(uri);
        operation.addRequestHeader(Operation.ACCEPT_HEADER,
                Operation.MEDIA_TYPE_APPLICATION_JSON);
        operation.addRequestHeader(Operation.CONTENT_TYPE_HEADER,
                Operation.MEDIA_TYPE_APPLICATION_JSON);
        try {
            operation.addRequestHeader(Operation.AUTHORIZATION_HEADER,
                    AUTH_HEADER_BEARER_PREFIX
                            + ctx.credentials.getToken(AzureUtils.getAzureBaseUri()));
        } catch (Exception ex) {
            handleError(ctx, ex);
            return;
        }

        operation.setCompletion((op, th) -> {
            if (th != null) {
                handleError(ctx, th);
                return;
            }

            ManagedDiskList results = op.getBody(ManagedDiskList.class);
            // Store next page link
            ctx.enumNextPageLink = results.nextLink;
            logInfo(() -> String.format("Next page link %s", ctx.enumNextPageLink));

            List<Disk> diskList = results.value;

            if (diskList == null || diskList.size() == 0) {
                ctx.subStage = DiskEnumStages.DISASSOCIATE_ENDPOINT_LINKS;
                handleSubStage(ctx);
                return;
            }

            logInfo(() -> String.format("Retrieved %d managed disks from Azure",
                    diskList.size()));

            // save all disks from Azure to process further
            diskList.forEach(disk -> ctx.managedDisks.put(disk.id, disk));

            // filter all un-attached disks from diskList
            List<Disk> unattachedDisks = diskList.stream().filter(
                    dk -> dk.properties.diskState.equals(DISK_STATUS_UNATTACHED))
                    .collect(Collectors.toList());
            // TODO - Remove toLowerCase() after https://github.com/Azure/azure-sdk-for-java/issues/2014 is fixed.
            // save all unattached disks in managedDisks map for further processing
            unattachedDisks.forEach(disk -> ctx.unattachedDisks.put(disk.id.toLowerCase(), disk));

            logInfo(() -> String.format("Processing %d independent disks",
                    ctx.managedDisks.size()));

            if (ctx.enumNextPageLink != null) {
                ctx.subStage = DiskEnumStages.GET_DISKS;
                logFine(() -> String.format("Transition to same stage" + ctx.subStage));
            } else {
                ctx.subStage = nextStage;
                logFine(() -> String.format("Transition to " + nextStage));
            }
            handleSubStage(ctx);
        });
        sendRequest(operation);

    }

    private void getLocalDiskStates(DiskEnumContext ctx, DiskEnumStages nextStage) {
        if (ctx.unattachedDisks.isEmpty()) {
            ctx.subStage = nextStage;
            handleSubStage(ctx);
            return;
        }

        logInfo(() -> "Query disk states from local document store.");

        // TODO- Add a clause to fetch disk states using un-attached disks IDs after
        // defect on Azure is resolved - https://github.com/Azure/azure-sdk-for-java/issues/2014
        Query.Builder qBuilder = Query.Builder.create()
                .addKindFieldClause(DiskState.class)
                .addInClause(DiskState.FIELD_NAME_ID, ctx.managedDisks.keySet(), Query.Occurance.SHOULD_OCCUR);

        QueryByPages<DiskState> queryLocalStates = new QueryByPages<>(
                getHost(),
                qBuilder.build(),
                DiskState.class,
                ctx.parentCompute.tenantLinks,
                null /* endpoint */,
                ctx.parentCompute.documentSelfLink)
                .setMaxPageSize(QueryUtils.MAX_RESULT_LIMIT)
                .setClusterType(ClusterUtil.ServiceTypeCluster.INVENTORY_SERVICE);

        queryLocalStates.collectDocuments(Collectors.toList())
                .whenComplete((diskStates, e) -> {
                    if (e != null) {
                        handleError(ctx, e);
                        return;
                    }
                    if (diskStates == null) {
                        return;
                    }
                    // TODO - Remove toLowerCase() after https://github.com/Azure/azure-sdk-for-java/issues/2014 is fixed.
                    diskStates.forEach(diskState -> ctx.localDiskStates.put(diskState.id.toLowerCase(), diskState));
                    logFine(() -> String.format("Transition to " + nextStage));
                    ctx.subStage = nextStage;
                    handleSubStage(ctx);
                });
    }

    private void createUpdateDiskStates(DiskEnumContext ctx, DiskEnumStages nextStage) {
        if (ctx.unattachedDisks.isEmpty()) {
            ctx.subStage = nextStage;
            handleSubStage(ctx);
            return;
        }
        Collection<Operation> opCollection = new ArrayList<>();

        ctx.unattachedDisks
                .entrySet()
                .stream()
                .forEach(entry -> {

                    DiskState diskState = ctx.localDiskStates.get(entry.getKey());

                    Operation diskOp = null;

                    if (diskState != null) {
                        diskState.status = DiskStatus.DETACHED;
                        if (diskState.endpointLinks != null && !diskState.endpointLinks.contains(ctx.request.endpointLink)) {
                            AdapterUtils.addToEndpointLinks(diskState, ctx.request.endpointLink);
                        }
                        if (diskState.endpointLink == null || diskState.endpointLink.equals("")) {
                            diskState.endpointLink = ctx.request.endpointLink;
                        }
                        if (diskState.customProperties != null &&
                                diskState.customProperties.containsKey(DISK_CONTROLLER_NUMBER)) {
                            diskState.customProperties.remove(DISK_CONTROLLER_NUMBER);
                        }
                        if (diskState.tagLinks == null) {
                            diskState.tagLinks = new HashSet<>();
                        }
                        diskState.tagLinks.addAll(ctx.internalTagLinks);
                        diskState.regionId = entry.getValue().location;
                        diskOp = Operation.createPatch(createInventoryUri(getHost(), diskState.documentSelfLink))
                                .setBody(diskState);
                    } else {
                        diskState = createLocalDiskState(ctx, entry.getValue());
                        diskOp = Operation.createPost(createInventoryUri(getHost(), DiskService.FACTORY_LINK))
                                .setBody(diskState);
                    }
                    ctx.localDiskStates.put(diskState.id, diskState);
                    opCollection.add(diskOp);
                });

        if (opCollection.isEmpty()) {
            ctx.subStage = nextStage;
            handleSubStage(ctx);
            return;
        }

        logInfo(() -> "Create and/or update disk states in local document store.");

        OperationJoin.create(opCollection)
                .setCompletion((ops, exs) -> {
                    if (exs != null) {
                        exs.values().forEach(ex -> logWarning(() -> String.format("Error: %s",
                                ex.getMessage())));
                        return;
                    }
                    logFine(() -> String.format("Transition to " + nextStage));
                    ctx.subStage = nextStage;
                    handleSubStage(ctx);
                }).sendWith(this);
    }

    /**
     * Disassociate EndpointLinks from Disk States (of local store) that are no longer existing in Azure.
     */
    private void disassociateEndpointLinksFromDiskStates(DiskEnumContext ctx, DiskEnumStages nextStage) {

        Query.Builder qBuilder = Query.Builder.create()
                .addKindFieldClause(DiskState.class)
                .addFieldClause(DiskState.FIELD_NAME_STATUS, DiskStatus.DETACHED)
                .addRangeClause(DiskState.FIELD_NAME_UPDATE_TIME_MICROS,
                        QueryTask.NumericRange.createLessThanRange(ctx.enumerationStartTimeInMicros));

        QueryTop<DiskState> queryLocalDiskStates = new QueryTop<>(
                getHost(),
                qBuilder.build(),
                DiskState.class,
                ctx.parentCompute.tenantLinks,
                null,
                ctx.parentCompute.documentSelfLink)
                .setClusterType(ClusterUtil.ServiceTypeCluster.INVENTORY_SERVICE);

        final String msg = queryLocalDiskStates.documentClass.getSimpleName() +
                " - Disassociation of endpoint links from disk states ";
        logInfo(() -> msg + " STARTED");

        queryLocalDiskStates.queryDocuments(ds -> {
            // check for diskState which is managed disk type
            if (ds.customProperties != null &&
                    ds.customProperties.containsKey(AZURE_MANAGED_DISK_TYPE)) {
                if (!ctx.unattachedDisks.containsKey(ds.id.toLowerCase())) {
                    Operation disassociateOp = PhotonModelUtils.createRemoveEndpointLinksOperation(
                            this,
                            ctx.request.endpointLink,
                            ds);
                    if (disassociateOp != null) {
                        sendRequest(disassociateOp);
                    }
                }
            }
        })
                .thenRun(() -> logInfo(() -> "Disassociation of endpoint link from disk states which " +
                        "are not present in Azure."))
                .whenComplete((aVoid, th) -> {
                    if (th != null) {
                        handleError(ctx, th);
                    }
                    logFine(() -> msg + ": SUCCESS");
                    logFine(() -> String.format("Transition to " + nextStage));
                    ctx.subStage = nextStage;
                    handleSubStage(ctx);
                });
    }

    /**
     * Construct DiskState object from a Disk
     */
    private DiskState createLocalDiskState(DiskEnumContext ctx, Disk disk) {
        DiskState diskState = new DiskState();

        String id = UUID.randomUUID().toString();
        diskState.documentSelfLink = UriUtils.buildUriPath(DiskService.FACTORY_LINK, id);
        diskState.name = disk.name;
        diskState.id = disk.id;
        diskState.capacityMBytes = (long) disk.properties.diskSizeGB * 1024;
        diskState.status = DiskStatus.DETACHED;
        diskState.tenantLinks = ctx.parentCompute.tenantLinks;
        diskState.resourcePoolLink = ctx.request.resourcePoolLink;
        diskState.computeHostLink = ctx.parentCompute.documentSelfLink;
        diskState.authCredentialsLink = ctx.endpointAuth.documentSelfLink;
        diskState.endpointLink = ctx.request.endpointLink;
        AdapterUtils.addToEndpointLinks(diskState, ctx.request.endpointLink);

        diskState.regionId = disk.location;
        if (diskState.tagLinks == null) {
            diskState.tagLinks = new HashSet<>();
        }
        // add internal type tags
        diskState.tagLinks.addAll(ctx.internalTagLinks);
        diskState.customProperties = new HashMap<>();
        diskState.customProperties.put(AZURE_MANAGED_DISK_TYPE, disk.properties.accountType);

        return diskState;
    }

    /**
     * Return a key to uniquely identify enumeration for compute host instance.
     */
    private String getEnumKey(DiskEnumContext ctx) {
        return ctx.request.getEnumKey();
    }

    private void createInternalTypeTag(DiskEnumContext context, DiskEnumStages next) {
        TagService.TagState typeTag = TagsUtil.newTagState(PhotonModelConstants.TAG_KEY_TYPE,
                AzureConstants.AzureResourceType.azure_managed_disk.toString(),
                false, context.parentCompute.tenantLinks);

        Operation.createPost(this, TagService.FACTORY_LINK)
                .setBody(typeTag)
                .setReferer(this.getUri())
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        // log the error and continue with enumeration
                        logWarning(() -> String
                                .format("Error creating internal tag: %s", ex.getMessage()));
                    } else {
                        // if no error, store the internal tag into context
                        context.internalTagLinks.add(typeTag.documentSelfLink);
                    }
                }).sendWith(this);

        context.subStage = next;
        handleSubStage(context);
    }
}
