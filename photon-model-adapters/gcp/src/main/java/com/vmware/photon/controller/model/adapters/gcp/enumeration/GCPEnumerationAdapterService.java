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

package com.vmware.photon.controller.model.adapters.gcp.enumeration;

import static com.vmware.photon.controller.model.ComputeProperties.CUSTOM_OS_TYPE;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.AUTH_HEADER_BEARER_PREFIX;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.DEFAULT_DISK_CAPACITY;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.DEFAULT_DISK_SERVICE_REFERENCE;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.DEFAULT_DISK_SOURCE_IMAGE;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.DISK_AUTO_DELETE;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.LIST_VM_TEMPLATE_URI;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.MAX_RESULTS;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.PAGE_TOKEN;
import static com.vmware.photon.controller.model.adapters.gcp.utils.GCPUtils.assignIPAddress;
import static com.vmware.photon.controller.model.adapters.gcp.utils.GCPUtils.assignPowerState;
import static com.vmware.photon.controller.model.adapters.gcp.utils.GCPUtils.extractActualInstanceType;
import static com.vmware.photon.controller.model.adapters.gcp.utils.GCPUtils.extractRegionFromZone;
import static com.vmware.photon.controller.model.adapters.gcp.utils.GCPUtils.privateKeyFromPkcs8;
import static com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ENVIRONMENT_NAME_GCP;

import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.google.api.services.compute.ComputeScopes;

import com.vmware.photon.controller.model.ComputeProperties.OSType;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.gcp.GCPUriPaths;
import com.vmware.photon.controller.model.adapters.gcp.podo.authorization.GCPAccessTokenResponse;
import com.vmware.photon.controller.model.adapters.gcp.podo.vm.GCPDisk;
import com.vmware.photon.controller.model.adapters.gcp.podo.vm.GCPInstance;
import com.vmware.photon.controller.model.adapters.gcp.podo.vm.GCPInstancesList;
import com.vmware.photon.controller.model.adapters.gcp.utils.GCPUtils;
import com.vmware.photon.controller.model.adapters.gcp.utils.JSONWebToken;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.enums.EnumerationStages;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.DiskService.DiskType;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.NumericRange;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Enumeration Adapter for the Google Cloud Platform. Performs a list call to the GCP API and
 * reconciles the local state with the state on the remote system. It lists the instances on the
 * remote system. Compares those with the local system, creates the instances that are missing
 * in the local system and deletes the instances that are redundant.
 */
public class GCPEnumerationAdapterService extends StatelessService {

    public static final String SELF_LINK = GCPUriPaths.GCP_ENUMERATION_ADAPTER;

    private static final String PROPERTY_NAME_ENUM_QUERY_RESULT_LIMIT = UriPaths.PROPERTY_PREFIX
            + "GCPEnumerationAdapterService.QUERY_RESULT_LIMIT";
    private static final int QUERY_RESULT_LIMIT =
            Integer.getInteger(PROPERTY_NAME_ENUM_QUERY_RESULT_LIMIT, 100);

    private static final String PROPERTY_NAME_ENUM_VM_PAGE_SIZE = UriPaths.PROPERTY_PREFIX
            + "GCPEnumerationAdapterService.VM_PAGE_SIZE";
    // The vm page size cannot be larger than 500. And it must be a string.
    private static final String VM_PAGE_SIZE = String.valueOf(Math.min(
            Integer.getInteger(PROPERTY_NAME_ENUM_VM_PAGE_SIZE, 50), 500));

    /**
     * SubStages to handle GCP VMs data collection.
     */
    private enum EnumerationSubStages {
        LIST_REMOTE_VMS,
        QUERY_LOCAL_VMS,
        UPDATE_COMPUTESTATE_COMPUTEDESCRIPTION_DISK,
        CREATE_LOCAL_VMS,
        DELETE_LOCAL_VMS,
        FINISHED
    }

    private static class EnumerationContext {
        // Basic fields
        ComputeEnumerateResourceRequest enumRequest;
        ComputeDescription computeHostDesc;
        EnumerationStages stage;
        Throwable error;
        AuthCredentialsServiceState parentAuth;
        ResourceGroupState resourceGroup;
        Long enumerationStartTimeInMicros;
        String enumNextPageLink;

        // Substage specific fields
        EnumerationSubStages subStage;
        Map<Long, GCPInstance> virtualMachines = new ConcurrentHashMap<>();
        List<ComputeState> computeStates = new LinkedList<>();
        Set<Long> vmIds = new HashSet<>();

        // GCP specific fields
        String accessToken;
        String userEmail;
        String privateKey;
        String projectId;
        String zoneId;
        Operation gcpAdapterOperation;

        EnumerationContext(Operation op) {
            this.gcpAdapterOperation = op;
            this.enumRequest = op.getBody(ComputeEnumerateResourceRequest.class);
            this.stage = EnumerationStages.HOSTDESC;
        }
    }

    public GCPEnumerationAdapterService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    /**
     * The REST PATCH request handler. This is the entry of starting an enumeration.
     * @param op Operation which should contain request body.
     */
    @Override
    public void handlePatch(Operation op) {
        setOperationHandlerInvokeTimeStat(op);
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        EnumerationContext ctx = new EnumerationContext(op);
        AdapterUtils.validateEnumRequest(ctx.enumRequest);
        if (ctx.enumRequest.isMockRequest) {
            op.complete();
            AdapterUtils.sendPatchToEnumerationTask(this,ctx.enumRequest.taskReference);
            return;
        }
        handleEnumerationRequest(ctx);
    }

    /**
     * The basic flow of dealing with an enumeration request.
     * @param ctx Enumeration Context which should decide the current stage of enumeration.
     */
    private void handleEnumerationRequest(EnumerationContext ctx) {
        switch (ctx.stage) {
        case HOSTDESC:
            getHostComputeDescription(ctx, EnumerationStages.PARENTAUTH);
            break;
        case PARENTAUTH:
            getParentAuth(ctx, EnumerationStages.RESOURCEGROUP);
            break;
        case RESOURCEGROUP:
            getResourceGroup(ctx, EnumerationStages.CLIENT);
            break;
        case CLIENT:
            try {
                // The access token will expire in one hour.
                // And this access token can be only used for readonly operations.
                getAccessToken(ctx, ctx.userEmail,
                        Collections.singleton(ComputeScopes.COMPUTE_READONLY),
                        privateKeyFromPkcs8(ctx.privateKey), EnumerationStages.ENUMERATE);
            } catch (Throwable e) {
                logSevere(e);
                ctx.error = e;
                ctx.stage = EnumerationStages.ERROR;
                handleEnumerationRequest(ctx);
                return;
            }
            break;
        case ENUMERATE:
            switch (ctx.enumRequest.enumerationAction) {
            case START:
                ctx.enumerationStartTimeInMicros = Utils.getNowMicrosUtc();
                ctx.enumRequest.enumerationAction = EnumerationAction.REFRESH;
                handleEnumerationRequest(ctx);
                break;
            case REFRESH:
                ctx.subStage = EnumerationSubStages.LIST_REMOTE_VMS;
                handleSubStage(ctx);
                break;
            case STOP:
                ctx.stage = EnumerationStages.FINISHED;
                handleEnumerationRequest(ctx);
                break;
            default:
                logSevere("Unknown enumeration action %s", ctx.enumRequest.enumerationAction);
                ctx.stage = EnumerationStages.ERROR;
                handleEnumerationRequest(ctx);
            }
            break;
        case FINISHED:
            AdapterUtils.sendPatchToEnumerationTask(this, ctx.enumRequest.taskReference);
            setOperationDurationStat(ctx.gcpAdapterOperation);
            ctx.gcpAdapterOperation.complete();
            break;
        case ERROR:
            ctx.gcpAdapterOperation.fail(ctx.error);
            AdapterUtils.sendFailurePatchToEnumerationTask(this,
                    ctx.enumRequest.taskReference, ctx.error);
            break;
        default:
            String msg = String.format("Unknown GCP enumeration stage %s ", ctx.stage.toString());
            logSevere(msg);
            ctx.error = new IllegalStateException(msg);
            ctx.gcpAdapterOperation.fail(ctx.error);
            AdapterUtils.sendFailurePatchToEnumerationTask(this,
                    ctx.enumRequest.taskReference, ctx.error);
        }
    }

    /**
     * The basic flow of dealing with each sub stage in an enumeration.
     * @param ctx Enumeration Context which should decide the current sub stage of enumeration.
     */
    private void handleSubStage(EnumerationContext ctx) {
        switch (ctx.subStage) {
        case LIST_REMOTE_VMS:
            enumerate(ctx);
            break;
        case QUERY_LOCAL_VMS:
            queryForComputeStates(ctx, ctx.virtualMachines);
            break;
        case UPDATE_COMPUTESTATE_COMPUTEDESCRIPTION_DISK:
            update(ctx);
            break;
        case CREATE_LOCAL_VMS:
            create(ctx);
            break;
        case DELETE_LOCAL_VMS:
            delete(ctx);
            break;
        case FINISHED:
            ctx.stage = EnumerationStages.FINISHED;
            handleEnumerationRequest(ctx);
            break;
        default:
            String msg = String
                    .format("Unknown GCP enumeration sub-stage %s ", ctx.subStage.toString());
            ctx.error = new IllegalStateException(msg);
            ctx.stage = EnumerationStages.ERROR;
            handleEnumerationRequest(ctx);
        }
    }

    /**
     * Deletes undiscovered resources.
     *
     * The logic works by recording a timestamp when enumeration starts. This timestamp is used to
     * lookup resources which haven't been touched as part of current enumeration cycle. The other
     * data point this method uses is the virtual machines discovered as part of list vm call.
     *
     * Finally, deletion on a resource is invoked only if it meets two criteria:
     * - Timestamp older than current enumeration cycle.
     * - VM not present on GCP.
     *
     * The method paginates through list of resources for deletion.
     * @param ctx The Enumeration Context.
     */
    private void delete(EnumerationContext ctx) {
        CompletionHandler completionHandler = (o, e) -> deleteQueryCompletionHandler(ctx, o, e);

        QueryTask.Query query = QueryTask.Query.Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_RESOURCE_POOL_LINK,
                        ctx.enumRequest.resourcePoolLink)
                .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK,
                        ctx.enumRequest.resourceLink())
                .addRangeClause(ComputeState.FIELD_NAME_UPDATE_TIME_MICROS,
                        NumericRange.createLessThanRange(ctx.enumerationStartTimeInMicros))
                .build();
        QueryTask q = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .setResultLimit(QUERY_RESULT_LIMIT)
                .setQuery(query)
                .build();
        q.tenantLinks = ctx.computeHostDesc.tenantLinks;

        logFine("Querying compute resources for deletion");
        sendRequest(Operation
                .createPost(this, ServiceUriPaths.CORE_LOCAL_QUERY_TASKS)
                .setConnectionSharing(true)
                .setBody(q)
                .setCompletion(completionHandler));
    }

    /**
     * This helper function deletes all compute states in one deletion page
     * every iteration. For each compute state, its associated disks will
     * also be deleted. After deletion, it will check if there is a next
     * deletion page. If there is, it will delete that page recursively.
     * If there are nothing to delete, it will jump to the finished stage
     * of the whole enumeration.
     * @param ctx The enumeration context.
     * @param results The results of deletion query.
     */
    private void deleteHelper(EnumerationContext ctx, ServiceDocumentQueryResult results) {
        if (results.documentCount == 0) {
            checkLinkAndFinishDeleting(ctx, results.nextPageLink);
            return;
        }

        List<Operation> operations = new ArrayList<>();
        results.documents.values().forEach(json -> {
            ComputeState computeState = Utils.fromJson(json, ComputeState.class);
            Long vmId = Long.parseLong(computeState.id);

            if (!ctx.vmIds.contains(vmId)) {
                operations.add(Operation.createDelete(this, computeState.documentSelfLink));
                logFine("Deleting compute state %s", computeState.documentSelfLink);

                if (computeState.diskLinks != null && !computeState.diskLinks.isEmpty()) {
                    computeState.diskLinks.forEach(diskLink -> {
                        operations.add(Operation.createDelete(this, diskLink));
                        logFine("Deleting disk state %s", diskLink);
                    });
                }
            }
        });

        if (operations.isEmpty()) {
            checkLinkAndFinishDeleting(ctx, results.nextPageLink);
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
                    checkLinkAndFinishDeleting(ctx, results.nextPageLink);
                }).sendWith(this);
    }

    /**
     * The completion handler of the operation of deletion query.
     * @param ctx The Enumeration Context.
     * @param o The Operation of completion handler.
     * @param e The Error of completion handler.
     */
    private void deleteQueryCompletionHandler(EnumerationContext ctx, Operation o, Throwable e) {
        if (e != null) {
            handleError(ctx, e);
            return;
        }
        QueryTask queryTask = o.getBody(QueryTask.class);
        deleteHelper(ctx, queryTask.results);
    }

    /**
     * The helper function which checks if the deletion is finished.
     * If finished, go to Finished sub stage.
     * @param ctx The Enumeration Context.
     * @param deletionNextPageLink The next deletion page link.
     */
    private void checkLinkAndFinishDeleting(EnumerationContext ctx, String deletionNextPageLink) {
        if (deletionNextPageLink != null) {
            logFine("Querying page [%s] for resources to be deleted", deletionNextPageLink);
            Operation.createGet(this, deletionNextPageLink)
                    .setCompletion((o, e) -> deleteQueryCompletionHandler(ctx, o, e))
                    .sendWith(this);
            return;
        }
        logInfo("No compute states match for deletion");
        ctx.subStage = EnumerationSubStages.FINISHED;
        handleSubStage(ctx);
    }

    /**
     * Creates relevant resources for given VMs.
     * @param ctx The Enumeration Context.
     */
    private void create(EnumerationContext ctx) {
        logFine("Creating Local Compute States");

        AtomicInteger size = new AtomicInteger(ctx.virtualMachines.size());
        logFine("%s compute description with states to be created", size.toString());
        ctx.virtualMachines.values().forEach(virtualMachine ->
                createHelper(ctx, virtualMachine, size));
    }

    /**
     * This helper function creates a compute state according to the corresponding
     * instance on the cloud. It will also creates an auth credential and a compute
     * description with it. Moreover, if there is a root disk of the instance on
     * cloud, it will creates a root disk locally. Otherwise, it will create a
     * default root disk with the compute state. All the creation operations will
     * run in parallel. After all remote instances get created, it will jump to
     * list vm stage if there is a next page of list vms. Otherwise it will jump
     * to the delete stage.
     * @param ctx The Enumeration Context.
     * @param virtualMachine The virtual machine to be created.
     * @param size The number of remaining virtual machines to be created.
     */
    private void createHelper(EnumerationContext ctx, GCPInstance virtualMachine, AtomicInteger size) {
        List<Operation> operations = new ArrayList<>();

        // TODO VSYM-1106: refactor the creation logic here.
        // Create compute description.
        // Map GCP instance data to compute description.
        ComputeDescription computeDescription = new ComputeDescription();
        computeDescription.id = UUID.randomUUID().toString();
        computeDescription.name = virtualMachine.name;
        computeDescription.zoneId = virtualMachine.zone;
        // TODO VSYM-1139: dynamically acquire all gcp zones, regions and mappings.
        computeDescription.regionId = extractRegionFromZone(virtualMachine.zone);
        computeDescription.instanceType = extractActualInstanceType(virtualMachine.machineType);
        computeDescription.authCredentialsLink = ctx.parentAuth.documentSelfLink;
        computeDescription.documentSelfLink = computeDescription.id;
        computeDescription.environmentName = ENVIRONMENT_NAME_GCP;
        computeDescription.instanceAdapterReference = UriUtils.buildUri(
                ServiceHost.LOCAL_HOST,
                this.getHost().getPort(),
                GCPUriPaths.GCP_INSTANCE_ADAPTER, null);
        computeDescription.statsAdapterReference = UriUtils.buildUri(
                ServiceHost.LOCAL_HOST,
                this.getHost().getPort(),
                GCPUriPaths.GCP_STATS_ADAPTER, null);
        computeDescription.tenantLinks = ctx.computeHostDesc.tenantLinks;

        Operation compDescOp = Operation
                .createPost(getHost(), ComputeDescriptionService.FACTORY_LINK)
                .setBody(computeDescription);
        operations.add(compDescOp);

        // Create root disk.
        DiskService.DiskState rootDisk = new DiskService.DiskState();
        rootDisk.id = UUID.randomUUID().toString();
        rootDisk.documentSelfLink = rootDisk.id;
        rootDisk.customProperties = new HashMap<>();
        boolean foundRoot = false;
        if (virtualMachine.disks != null && !virtualMachine.disks.isEmpty()) {
            for (GCPDisk gcpDisk : virtualMachine.disks) {
                if (gcpDisk.boot) {
                    foundRoot = true;
                    rootDisk.name = gcpDisk.deviceName;
                    rootDisk.customProperties.put(DISK_AUTO_DELETE, gcpDisk.autoDelete.toString());
                    break;
                }
            }
        }
        if (!foundRoot) {
            rootDisk.name = rootDisk.id;
        }
        // These are required fields in disk service.
        // They cannot be accessed during vm enumeration.
        rootDisk.type = DiskType.HDD;
        rootDisk.capacityMBytes = DEFAULT_DISK_CAPACITY;
        rootDisk.sourceImageReference = URI.create(DEFAULT_DISK_SOURCE_IMAGE);
        rootDisk.customizationServiceReference = URI.create(DEFAULT_DISK_SERVICE_REFERENCE);
        // No matter we find root disk or not, the root disk should be booted first.
        rootDisk.bootOrder = 1;
        rootDisk.tenantLinks = ctx.computeHostDesc.tenantLinks;

        Operation diskOp = Operation.createPost(getHost(), DiskService.FACTORY_LINK)
                .setBody(rootDisk);
        operations.add(diskOp);

        List<String> vmDisks = new ArrayList<>();
        vmDisks.add(UriUtils.buildUriPath(DiskService.FACTORY_LINK, rootDisk.documentSelfLink));

        // Create compute state
        ComputeState resource = new ComputeState();
        resource.id = virtualMachine.id.toString();
        resource.type = ComputeType.VM_GUEST;
        resource.environmentName = ComputeDescription.ENVIRONMENT_NAME_GCP;
        resource.name = virtualMachine.name;
        resource.parentLink = ctx.enumRequest.resourceLink();
        resource.descriptionLink = UriUtils.buildUriPath(
                ComputeDescriptionService.FACTORY_LINK, computeDescription.documentSelfLink);
        resource.resourcePoolLink = ctx.enumRequest.resourcePoolLink;
        resource.diskLinks = vmDisks;
        resource.customProperties = new HashMap<>();
        String osType = getNormalizedOSType(virtualMachine);
        if (osType != null) {
            resource.customProperties.put(CUSTOM_OS_TYPE, osType);
        }
        resource.tenantLinks = ctx.computeHostDesc.tenantLinks;
        assignIPAddress(resource, virtualMachine);
        assignPowerState(resource, virtualMachine.status);

        Operation resourceOp = Operation
                .createPost(getHost(), ComputeService.FACTORY_LINK)
                .setBody(resource);
        operations.add(resourceOp);

        OperationJoin.create(operations)
                .setCompletion((ops, exs) -> {
                    if (exs != null) {
                        exs.values().forEach(ex -> logWarning("Error: %s", ex.getMessage()));
                    }
                    if (size.decrementAndGet() == 0) {
                        ctx.virtualMachines.clear();
                        if (ctx.enumNextPageLink != null) {
                            ctx.subStage = EnumerationSubStages.LIST_REMOTE_VMS;
                        } else {
                            logFine("Finished creating compute states");
                            ctx.subStage = EnumerationSubStages.DELETE_LOCAL_VMS;
                        }
                        handleSubStage(ctx);
                    }
                }).sendWith(this);
    }

    /**
     * Updates matching compute states for given VMs.
     * @param ctx The Enumeration Context.
     */
    private void update(EnumerationContext ctx) {
        logFine("Updating Local VMs");

        AtomicInteger numOfUpdates = new AtomicInteger(ctx.computeStates.size());
        ctx.computeStates.forEach(computeState -> {
            Long instanceId = Long.parseLong(computeState.id);
            GCPInstance GCPInstance = ctx.virtualMachines.remove(instanceId);
            updateHelper(ctx, computeState, GCPInstance, numOfUpdates);
        });
    }

    /**
     * This function will update the root disk data of a compute state. If the
     * instance on the cloud does not have any disks or does not have a boot
     * disk, the update will skip this instance. After all local vms are updated
     * it will jump to create stage.
     *
     * So far we update ip address, power state in compute state and instance type
     * in compute description and disk custom properties
     * @param ctx The Enumeration Context.
     * @param computeState The compute state to be updated.
     * @param vm The virtual machine used to update compute state.
     * @param numOfUpdates The number of remaining compute states to be updated.
     */
    private void updateHelper(EnumerationContext ctx, ComputeState computeState, GCPInstance vm,
                              AtomicInteger numOfUpdates) {
        List<Operation> operations = new ArrayList<>();

        ComputeState computeStatePatch = new ComputeState();
        assignIPAddress(computeStatePatch, vm);
        assignPowerState(computeStatePatch, vm.status);
        operations.add(Operation.createPatch(getHost(),
                computeState.documentSelfLink).setBody(computeStatePatch));

        ComputeDescription computeDescription = new ComputeDescription();
        computeDescription.instanceType = extractActualInstanceType(vm.machineType);
        operations.add(Operation.createPatch(getHost(),
                computeState.descriptionLink).setBody(computeDescription));

        if (vm.disks != null && !vm.disks.isEmpty()) {
            for (GCPDisk gcpDisk : vm.disks) {
                if (gcpDisk.boot) {
                    DiskState diskState = new DiskState();
                    diskState.customProperties = new HashMap<>();
                    diskState.customProperties.put(DISK_AUTO_DELETE, gcpDisk.autoDelete.toString());
                    diskState.documentSelfLink = computeState.diskLinks.get(0);
                    operations.add(Operation.createPatch(getHost(),
                            diskState.documentSelfLink).setBody(diskState));
                    break;
                }
            }
        }

        OperationJoin.create(operations)
                .setCompletion((ops, exs) -> {
                    if (exs != null) {
                        exs.values().forEach(ex -> logWarning("Error: %s", ex.getMessage()));
                    }
                    countAndFinishUpdating(ctx, numOfUpdates);
                }).sendWith(this);
    }

    /**
     * The helper function which checks if the update is finished.
     * If finished, go to Delete or List VMs sub stage depending on
     * if there are still remaining virtual machines.
     * @param ctx The enumeration context.
     * @param numOfUpdates The
     */
    private void countAndFinishUpdating(EnumerationContext ctx, AtomicInteger numOfUpdates) {
        if (numOfUpdates.decrementAndGet() == 0) {
            ctx.computeStates.clear();
            logFine("Finished updating compute states");
            // If there are still some cloud instances left, these instances
            // should be mapped to local compute states. So we jump to create stage.
            // Otherwise, we go to delete stage or list vms stage depending on
            // whether the next enumeration page link is valid or not.
            if (ctx.virtualMachines.isEmpty()) {
                // If the next enumeration page link is not valid, we can
                // jump directly to delete stage since there are no more
                // instances on cloud.
                // Otherwise, we need to go to list vms page and fetch instances
                // on next page.
                if (ctx.enumNextPageLink == null) {
                    ctx.subStage = EnumerationSubStages.DELETE_LOCAL_VMS;
                } else {
                    ctx.subStage = EnumerationSubStages.LIST_REMOTE_VMS;
                }
            } else {
                ctx.subStage = EnumerationSubStages.CREATE_LOCAL_VMS;
            }
            handleSubStage(ctx);
        }
    }

    /**
     * Query all compute states for the cluster filtered by the received set of instance Ids.
     * @param ctx The Enumeration Context.
     * @param vms The Map of VM IDs and VMs.
     */
    private void queryForComputeStates(EnumerationContext ctx, Map<Long, GCPInstance> vms) {
        logFine("Enumerating Local Compute States");

        QueryTask.Query.Builder instanceIdFilterParentQuery =
                QueryTask.Query.Builder.create(QueryTask.Query.Occurance.MUST_OCCUR);
        for (Long instanceId : vms.keySet()) {
            QueryTask.Query instanceIdFilter = QueryTask.Query.Builder
                    .create(QueryTask.Query.Occurance.SHOULD_OCCUR)
                    .addFieldClause(ComputeState.FIELD_NAME_ID, instanceId.toString())
                    .build();
            instanceIdFilterParentQuery.addClause(instanceIdFilter);
        }

        QueryTask.Query query = QueryTask.Query.Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_RESOURCE_POOL_LINK,
                        ctx.enumRequest.resourcePoolLink)
                .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK,
                        ctx.enumRequest.resourceLink())
                .build()
                .addBooleanClause(instanceIdFilterParentQuery.build());

        QueryTask q = QueryTask.Builder.createDirectTask()
                .addOption(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT)
                .setQuery(query)
                .build();
        q.tenantLinks = ctx.computeHostDesc.tenantLinks;

        sendRequest(Operation
                .createPost(this, ServiceUriPaths.CORE_LOCAL_QUERY_TASKS)
                .setConnectionSharing(true)
                .setBody(q)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        handleError(ctx, e);
                        return;
                    }
                    QueryTask queryTask = o.getBody(QueryTask.class);

                    logFine("Found %d matching compute states for GCP Instances",
                            queryTask.results.documentCount);

                    // If there are local compute states with same id as cloud instances,
                    // these compute states need to be updated.
                    // Otherwise, we can jump directly to create stage.
                    if (queryTask.results.documentCount > 0) {
                        for (Object s : queryTask.results.documents.values()) {
                            ComputeState computeState = Utils.fromJson(s, ComputeState.class);
                            ctx.computeStates.add(computeState);
                        }
                        ctx.subStage = EnumerationSubStages.UPDATE_COMPUTESTATE_COMPUTEDESCRIPTION_DISK;
                    } else {
                        ctx.subStage = EnumerationSubStages.CREATE_LOCAL_VMS;
                    }
                    handleSubStage(ctx);
                }));
    }

    /**
     * Enumerate VMs from Google Cloud Platform.
     * @param ctx The Enumeration Context.
     */
    private void enumerate(EnumerationContext ctx) {
        logFine("Enumerating VMs from GCP");
        URI uri;

        if (ctx.enumNextPageLink != null) {
            uri = UriUtils.extendUriWithQuery(UriUtils.buildUri(String.format(LIST_VM_TEMPLATE_URI,
                    ctx.projectId, ctx.zoneId)), MAX_RESULTS, VM_PAGE_SIZE, PAGE_TOKEN,
                    ctx.enumNextPageLink);
        } else {
            uri = UriUtils.extendUriWithQuery(UriUtils.buildUri(String.format(LIST_VM_TEMPLATE_URI,
                    ctx.projectId, ctx.zoneId)), MAX_RESULTS, VM_PAGE_SIZE);
        }

        Operation.createGet(uri)
                .addRequestHeader(Operation.AUTHORIZATION_HEADER, AUTH_HEADER_BEARER_PREFIX
                        + ctx.accessToken)
                .setCompletion((op, er) -> {
                    if (er != null) {
                        handleError(ctx, er);
                        return;
                    }

                    GCPInstancesList GCPInstancesList = op.getBody(GCPInstancesList.class);
                    List<GCPInstance> GCPInstances = GCPInstancesList.items;

                    if (GCPInstances == null || GCPInstances.size() == 0) {
                        ctx.subStage = EnumerationSubStages.DELETE_LOCAL_VMS;
                        handleSubStage(ctx);
                        return;
                    }

                    ctx.enumNextPageLink = GCPInstancesList.nextPageToken;

                    logFine("Retrieved %d VMs from GCP", GCPInstances.size());
                    logFine("Next page link %s", ctx.enumNextPageLink);

                    for (GCPInstance GCPInstance : GCPInstances) {
                        ctx.virtualMachines.put(GCPInstance.id, GCPInstance);
                        ctx.vmIds.add(GCPInstance.id);
                    }

                    logFine("Processing %d VMs", ctx.vmIds.size());

                    ctx.subStage = EnumerationSubStages.QUERY_LOCAL_VMS;
                    handleSubStage(ctx);
                }).sendWith(this);
    }

    /**
     * Method to retrieve the parent compute host on which the enumeration task will be performed.
     * @param ctx The Enumeration Context.
     * @param next The next enumeration sub stage.
     */
    private void getHostComputeDescription(EnumerationContext ctx, EnumerationStages next) {
        Consumer<Operation> onSuccess = (op) -> {
            ComputeStateWithDescription csd = op.getBody(ComputeStateWithDescription.class);
            ctx.computeHostDesc = csd.description;
            validateHost(ctx, ctx.computeHostDesc);
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
     * Method to arrive at the credentials needed to call the GCP API for enumerating the instances.
     * @param ctx The Enumeration Context.
     * @param next The next enumeration sub stage.
     */
    private void getParentAuth(EnumerationContext ctx, EnumerationStages next) {
        Consumer<Operation> onSuccess = (op) -> {
            ctx.parentAuth = op.getBody(AuthCredentialsServiceState.class);
            validateAuth(ctx, ctx.parentAuth);
            ctx.stage = next;
            handleEnumerationRequest(ctx);
        };
        URI authUri = UriUtils.buildUri(this.getHost(), ctx.computeHostDesc.authCredentialsLink);
        AdapterUtils.getServiceState(this, authUri, onSuccess, getFailureConsumer(ctx));
    }

    /**
     * Method to retrieve the resource group on which the enumeration task will be performed.
     * @param ctx The Enumeration Context.
     * @param next The next enumeration sub stage.
     */
    private void getResourceGroup(EnumerationContext ctx, EnumerationStages next) {
        Consumer<Operation> onSuccess = op -> {
            ctx.resourceGroup = op.getBody(ResourceGroupState.class);
            validateResourceGroup(ctx, ctx.resourceGroup);
            ctx.stage = next;
            handleEnumerationRequest(ctx);
        };
        URI resourceGroupURI = UriUtils.buildUri(this.getHost(),
                ctx.computeHostDesc.groupLinks.iterator().next());
        AdapterUtils.getServiceState(this, resourceGroupURI, onSuccess, getFailureConsumer(ctx));
    }

    /**
     * Method to get the access token to send RESTful APIs later.
     * Every access token is only valid for an hour.
     * @param ctx The Enumeration Context.
     * @param clientEmail The client email in service account's credential file.
     * @param scopes The limitation of application's access.
     * @param privateKey The key generated by private key in service account's credential file.
     * @throws GeneralSecurityException The exception will be thrown when private key is invalid.
     * @throws IOException The exception will be thrown when inputs are mal-formatted.
     */
    private void getAccessToken(EnumerationContext ctx, String clientEmail,
            Collection<String> scopes, PrivateKey privateKey, EnumerationStages next)
            throws GeneralSecurityException, IOException {
        Consumer<GCPAccessTokenResponse> onSuccess = response -> {
            ctx.accessToken = response.access_token;
            ctx.stage = next;
            handleEnumerationRequest(ctx);
        };

        JSONWebToken jwt = new JSONWebToken(clientEmail, scopes, privateKey);
        String assertion = jwt.getAssertion();
        GCPUtils.getAccessToken(this, assertion, onSuccess, getFailureConsumer(ctx));
    }

    /**
     * The call back function of failure handler when get necessary service start for enumeration.
     * @param ctx The Enumeration Context.
     * @return The call back interface.
     */
    private Consumer<Throwable> getFailureConsumer(EnumerationContext ctx) {
        return t -> {
            ctx.stage = EnumerationStages.ERROR;
            ctx.error = t;
            handleEnumerationRequest(ctx);
        };
    }

    /**
     * Method to validate that the passed in Compute Host Description is valid.
     * Validating that the zoneId is populated in Compute Host Description.
     * @param ctx The enumeration context.
     * @param computeHostDesc The compute host description.
     */
    private void validateHost(EnumerationContext ctx, ComputeDescription computeHostDesc) {
        if (computeHostDesc.zoneId == null) {
            throw new IllegalArgumentException("zoneId is required");
        }
        if (computeHostDesc.authCredentialsLink == null) {
            throw new IllegalArgumentException("auth credential is required");
        }
        if (computeHostDesc.groupLinks == null) {
            throw new IllegalArgumentException("resource group link is required");
        }
        if (computeHostDesc.groupLinks.size() != 1) {
            throw new IllegalArgumentException("number of resource groups should be one");
        }
        ctx.zoneId = computeHostDesc.zoneId;
    }

    /**
     * Method to validate that the passed in Auth Credential Response is valid.
     * Validating that the userEmail and privateKey are populated in the response.
     * @param parentAuth the auth credential
     */
    private void validateAuth(EnumerationContext ctx, AuthCredentialsServiceState parentAuth) {
        if (parentAuth.userEmail == null) {
            throw new IllegalArgumentException("userEmail is required");
        }
        if (parentAuth.privateKey == null) {
            throw new IllegalArgumentException("privateKey is required");
        }
        ctx.userEmail = parentAuth.userEmail;
        ctx.privateKey = parentAuth.privateKey;
    }

    /**
     * Method to validate that the passed in Resource Group is valid.
     * Validating that the projectId is populated in Resource Group.
     * @param ctx The enumeration context.
     * @param resourceGroup The resource group.
     */
    private void validateResourceGroup(EnumerationContext ctx, ResourceGroupState resourceGroup) {
        if (resourceGroup.name == null) {
            throw new IllegalArgumentException("projectName is required");
        }
        ctx.projectId = resourceGroup.name;
    }

    /**
     * Error handler if there are exceptions during the enumeration.
     * @param ctx The enumeration context.
     * @param e The exception during enumeration.
     */
    private void handleError(EnumerationContext ctx, Throwable e) {
        logSevere(e);
        ctx.error = e;
        ctx.stage = EnumerationStages.ERROR;
        handleEnumerationRequest(ctx);
    }

    /**
     * Get the os type from GCP instance response.
     * @param instance The response of GCP instance resource.
     * @return The string value of the os type.
     */
    private String getNormalizedOSType(GCPInstance instance) {
        if (instance.disks == null || instance.disks.isEmpty()) {
            return null;
        }
        String license = null;
        for (GCPDisk disk : instance.disks) {
            if (disk.boot) {
                if (disk.licenses != null && disk.licenses.size() == 1) {
                    license = disk.licenses.get(0).toLowerCase();
                }
                break;
            }
        }
        if (license == null) {
            return null;
        }
        if (license.contains("windows")) {
            return OSType.WINDOWS.toString();
        }
        return OSType.LINUX.toString();
    }
}
