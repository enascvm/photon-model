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

package com.vmware.photon.controller.model.adapters.vsphere;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperation;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationRequest;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationUtils;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationUtils.TargetCriteria;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.adapters.vsphere.constants.VSphereConstants;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.Connection;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.SnapshotService;
import com.vmware.photon.controller.model.resources.SnapshotService.SnapshotState;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.TaskInfoState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.OperationSequence;
import com.vmware.xenon.common.QueryResultsProcessor;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Handles snapshot related operation for a vSphere compute resource
 */
public class VSphereAdapterSnapshotService extends StatelessService {

    public static final String SELF_LINK = ResourceOperationSpecService.buildDefaultAdapterLink(
            PhotonModelConstants.EndpointType.vsphere.name(), ResourceOperationSpecService.ResourceType.COMPUTE,
            "snapshot");
    public static final Boolean REMOVE_CHILDREN = false;
    public static final Boolean SNAPSHOT_CONSOLIDATION = true;

    private static class SnapshotContext {
        SnapshotState snapshotState;
        TaskManager mgr;
        SnapshotService.SnapshotRequestType requestType;
        Boolean snapshotMemory;
        ComputeStateWithDescription computeDescription;
        ComputeStateWithDescription parentComputeDescription;
        SnapshotState existingSnapshotState;
        Collection<Operation> snapshotOperations = new ArrayList<>();

        SnapshotContext(SnapshotState snapshotState, TaskManager mgr,
                        SnapshotService.SnapshotRequestType requestType) {
            this.snapshotState = snapshotState;
            this.mgr = mgr;
            this.requestType = requestType;
        }
    }

    @Override
    public void handleStart(Operation startPost) {
        Operation.CompletionHandler handler = (op, exc) -> {
            if (exc != null) {
                startPost.fail(exc);
            } else {
                startPost.complete();
            }
        };
        ResourceOperationUtils.registerResourceOperation(this, handler, getResourceOperationSpecs());
    }

    @Override
    public void handleStop(Operation stop) {
        super.handleStop(stop);
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        op.setStatusCode(Operation.STATUS_CODE_CREATED);
        op.complete();

        ResourceOperationRequest request = op.getBody(ResourceOperationRequest.class);
        TaskManager mgr = new TaskManager(this, request.taskReference, request.resourceLink());

        if (request.isMockRequest) {
            mgr.patchTask(TaskStage.FINISHED);
            return;
        }

        SnapshotService.SnapshotRequestType requestType = SnapshotService.SnapshotRequestType.fromString(request.payload.get(VSphereConstants.VSPHERE_SNAPSHOT_REQUEST_TYPE));

        switch (requestType) {
        case CREATE:
            SnapshotContext createSnapshotContext = new SnapshotContext(populateAndGetSnapshotState(request), mgr, requestType);
            createSnapshotContext.snapshotMemory = Boolean.valueOf(request.payload.get(VSphereConstants.VSPHERE_SNAPSHOT_MEMORY));
            DeferredResult.completed(createSnapshotContext)
                    .thenCompose(this::thenSetComputeDescription)
                    .thenCompose(this::thenSetParentComputeDescription)
                    .thenCompose(this::querySnapshotStates)
                    .thenCompose(this::performSnapshotOperation)
                    .whenComplete((context, err) -> {
                        if (err != null) {
                            mgr.patchTaskToFailure(err);
                            return;
                        }
                        mgr.finishTask();
                    });
            break;
        case DELETE:
            Operation.createGet(UriUtils.buildUri(this.getHost(),request.resourceLink()))
                    .setCompletion((o,e) -> {
                        if (e != null) {
                            mgr.patchTaskToFailure(e);
                            return;
                        }
                        SnapshotContext deleteSnapshotContext = new SnapshotContext(o.getBody(SnapshotState.class), mgr,
                                requestType);
                        DeferredResult.completed(deleteSnapshotContext)
                                .thenCompose(this::thenSetComputeDescription)
                                .thenCompose(this::thenSetParentComputeDescription)
                                .thenCompose(this::performSnapshotOperation)
                                .whenComplete((context, err) -> {
                                    if (err != null) {
                                        mgr.patchTaskToFailure(err);
                                        return;
                                    }
                                    mgr.finishTask();
                                });
                    }).sendWith(this);
            break;
        case REVERT:
            break;
        default:
            mgr.patchTaskToFailure(new IllegalStateException(String.format("Unknown Operation %s for a Snapshot",
                    requestType)));
            break;
        }
    }

    private SnapshotState populateAndGetSnapshotState(ResourceOperationRequest request) {
        SnapshotState snapshotState = new SnapshotState();
        snapshotState.id = UUID.randomUUID().toString();
        snapshotState.computeLink = request.resourceLink();
        snapshotState.documentSelfLink = UriUtils.buildUriPath(SnapshotService.FACTORY_LINK, getHost().nextUUID());
        final String snapshotRequestName = request.payload.get(VSphereConstants.VSPHERE_SNAPSHOT_NAME);
        final String snapshotRequestDesc = request.payload.get(VSphereConstants.VSPHERE_SNAPSHOT_DESCRIPTION);
        if (snapshotRequestName == null || snapshotRequestName.isEmpty()) {
            snapshotState.name = "snapshot-" + Utils.getNowMicrosUtc();
        } else {
            snapshotState.name = snapshotRequestName;
        }
        if (snapshotRequestDesc == null || snapshotRequestDesc.isEmpty()) {
            snapshotState.description = "description - " + snapshotState.name;
        } else {
            snapshotState.description = snapshotRequestDesc;
        }
        return snapshotState;
    }

    private DeferredResult<SnapshotContext> thenSetComputeDescription(SnapshotContext context) {
        URI computeUri = ComputeStateWithDescription.buildUri(UriUtils.buildUri(this.getHost(), context.snapshotState.computeLink));

        return this.sendWithDeferredResult(Operation.createGet(computeUri), ComputeStateWithDescription.class)
                .thenApply(computeWithDescription -> {
                    context.computeDescription = computeWithDescription;
                    return context;
                });
    }

    private DeferredResult<SnapshotContext> thenSetParentComputeDescription(SnapshotContext context) {
        URI parentComputeUri = ComputeStateWithDescription.buildUri(UriUtils.buildUri(this.getHost(), context.computeDescription.parentLink));

        return this.sendWithDeferredResult(Operation.createGet(parentComputeUri), ComputeStateWithDescription.class)
                .thenApply(parentComputeWithDesc -> {
                    context.parentComputeDescription = parentComputeWithDesc;
                    return context;
                });
    }

    private DeferredResult<SnapshotContext> querySnapshotStates(SnapshotContext context) {
        // find if for the compute a snapshot already exist or not and if yes, get the current snapshot (among the snapshots that may exist)
        QueryTask.Query snapshotQuery = QueryTask.Query.Builder.create()
                .addKindFieldClause(SnapshotState.class)
                .addFieldClause(SnapshotState.FIELD_NAME_COMPUTE_LINK, context.snapshotState.computeLink)
                .addFieldClause(SnapshotState.FIELD_NAME_IS_CURRENT, "true")
                .build();
        QueryTask qTask = QueryTask.Builder.createDirectTask()
                .setQuery(snapshotQuery)
                .addOption(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT)
                .build();

        return this.sendWithDeferredResult(Operation.createPost(UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS))
                .setBody(qTask))
                .thenApply(op -> {
                    QueryResultsProcessor rp = QueryResultsProcessor.create(op);
                    if (rp.hasResults()) {
                        Optional<SnapshotState> snapshotStateOptional = rp.streamDocuments(SnapshotState.class).findFirst();
                        if (snapshotStateOptional.isPresent()) {
                            context.existingSnapshotState = snapshotStateOptional.get();
                            context.snapshotState.parentLink = context.existingSnapshotState.documentSelfLink;
                        }
                    }
                    return context;
                });
    }

    private DeferredResult<SnapshotContext> performSnapshotOperation(SnapshotContext context) {
        DeferredResult<SnapshotContext> result = new DeferredResult<>();
        VSphereIOThreadPool pool = VSphereIOThreadPoolAllocator.getPool(this);
        switch (context.requestType) {
        case CREATE:
            pool.submit(this, context.parentComputeDescription.adapterManagementReference,
                    context.parentComputeDescription.description.authCredentialsLink,
                    (connection, e) -> {
                        if (e != null) {
                            result.fail(e);
                        } else {
                            createSnapshot(connection, context, result);
                        }
                    });
            break;
        case DELETE:
            pool.submit(this, context.parentComputeDescription.adapterManagementReference,
                    context.parentComputeDescription.description.authCredentialsLink,
                    (connection, e) -> {
                        if (e != null) {
                            result.fail(e);
                        } else {
                            deleteSnapshot(context, connection, result);
                        }
                    });
            break;
        case REVERT:
            break;
        default:
            result.fail(new IllegalStateException("Unsupported requestType " + context.requestType));
        }
        return result;
    }

    private void createSnapshot(Connection connection, SnapshotContext context,
                                DeferredResult<SnapshotContext> deferredResult) {
        ManagedObjectReference vmMoRef = CustomProperties.of(context.computeDescription).getMoRef(CustomProperties.MOREF);

        if (vmMoRef == null) {
            deferredResult.fail(new IllegalStateException("Cannot find VM to snapshot"));
            return;
        }

        ManagedObjectReference task;
        TaskInfo info;
        try {
            logInfo("Creating snapshot for compute resource %s", context.computeDescription.name);
            task = connection.getVimPort()
                    .createSnapshotTask(vmMoRef, context.snapshotState.name, context.snapshotState.description, context.snapshotMemory, false);
            info = VimUtils.waitTaskEnd(connection, task);
            if (info.getState() != TaskInfoState.SUCCESS) {
                VimUtils.rethrow(info.getError());
            }
        } catch (Exception e) {
            deferredResult.fail(e);
            return;
        }

        CustomProperties.of(context.snapshotState).put(CustomProperties.MOREF, (ManagedObjectReference) info.getResult());
        context.snapshotState.isCurrent = true; //mark this as current snapshot

        // create a new xenon SnapshotState
        logInfo(String.format("Creating a new snapshot state for compute : %s", context.computeDescription.name));
        Operation createSnapshotState = Operation.createPost(this, SnapshotService.FACTORY_LINK)
                .setBody(context.snapshotState);

        if (context.existingSnapshotState != null) {
            // un-mark old snapshot as current snapshot
            context.existingSnapshotState.isCurrent = false;
            Operation patchOldSnapshot = Operation
                    .createPatch(UriUtils.buildUri(getHost(), context.existingSnapshotState.documentSelfLink))
                    .setBody(context.existingSnapshotState);
            OperationSequence.create(createSnapshotState)
                    .next(patchOldSnapshot)
                    .setCompletion((o, e) -> {
                        if (e != null && !e.isEmpty()) {
                            deferredResult.fail(e.values().iterator().next());
                        }
                        deferredResult.complete(context);
                    }).sendWith(this);
        } else {
            context.computeDescription.customProperties.put(ComputeProperties.CUSTOM_PROP_COMPUTE_HAS_SNAPSHOTS, "true");
            // patch compute adding the property '_hasSnapshots' with true
            Operation patchCompute = Operation
                    .createPatch(UriUtils.buildUri(getHost(), context.snapshotState.computeLink))
                    .setBody(context.computeDescription);

            OperationSequence.create(createSnapshotState)
                    .next(patchCompute)
                    .setCompletion((o, e) -> {
                        if (e != null && !e.isEmpty()) {
                            deferredResult.fail(e.values().iterator().next());
                        }
                        logInfo(String.format("Created a new snapshot state for compute : %s", context.computeDescription.name));
                        deferredResult.complete(context);
                    }).sendWith(this);
        }
    }

    private void deleteSnapshot(SnapshotContext context, Connection connection, DeferredResult<SnapshotContext> deferredResult) {
        final SnapshotState snapshot = context.snapshotState;
        // Physical snapshot processing
        ManagedObjectReference snapshotMoref = CustomProperties.of(snapshot)
                .getMoRef(CustomProperties.MOREF);

        if (snapshotMoref == null) {
            deferredResult.fail(new IllegalStateException(String.format("Cannot find the snapshot %s to removed", snapshotMoref)));
            return;
        }

        ManagedObjectReference task;
        TaskInfo info;
        try {
            logInfo("Deleting snapshot with name %s", context.snapshotState.name);
            task = connection.getVimPort()
                    .removeSnapshotTask(snapshotMoref, REMOVE_CHILDREN, SNAPSHOT_CONSOLIDATION);
            info = VimUtils.waitTaskEnd(connection, task);
            if (info.getState() != TaskInfoState.SUCCESS) {
                VimUtils.rethrow(info.getError());
            }
        } catch (Exception e) {
            logSevere("Deleting the snapshot %s failed", context.snapshotState.name);
            deferredResult.fail(e);
            return;
        }

        logInfo("Deleted the snapshot with name %s successfully", context.snapshotState.name);

        // Once the actual snapshot delete is successful, process the update of the children
        // snapshot states and the next current snapshot
        final List<SnapshotState> childSnapshots = new ArrayList<>();
        DeferredResult<List<SnapshotState>> dr = getChildSnapshots(snapshot.documentSelfLink, context.mgr) ;
        List<SnapshotState> updatedChildSnapshots = new ArrayList<>();
        dr.whenComplete((o, e) -> {
            if (e != null) {
                logSevere("Retrieving the details of children snapshots failed");
                deferredResult.fail(e);
                return;
            }
            childSnapshots.addAll(o);
            logInfo("Retrieving the details of %s children snapshots ", childSnapshots.size());
            // Update the children
            if (!childSnapshots.isEmpty()) {
                logInfo("Updating the state of child snapshots of: %s", snapshot.name);
                childSnapshots.forEach(c -> {
                    c.parentLink = snapshot.parentLink;
                    updatedChildSnapshots.add(c);
                });
                context.snapshotOperations =
                        updatedChildSnapshots
                                .stream()
                                .map(childSnapshot -> Operation.createPatch(this.getHost(),
                                        SnapshotService.FACTORY_LINK)
                                        .setBody(childSnapshot)
                                        .setReferer(getUri()))
                                .collect(Collectors.toList());
            }
            processNextStepsForDeleteOperation(context, deferredResult);
        });
    }

    private void processNextStepsForDeleteOperation(SnapshotContext context, DeferredResult<SnapshotContext> deferredResult) {
        final SnapshotState snapshot = context.snapshotState;
        // Update the isCurrent
        if (snapshot.isCurrent && snapshot.parentLink != null) {
            logInfo("Updating the parent of the snapshot %s to current", snapshot.name);
            SnapshotState parentSnapshot = new SnapshotState();
            parentSnapshot.isCurrent = Boolean.TRUE;
            context.snapshotOperations.add(Operation.createPatch(UriUtils.buildUri(this.getHost(),snapshot.parentLink))
                    .setBody(parentSnapshot).setReferer(getUri()));
        }

        // Check if the deleted snapshot is the last available snapshot
        DeferredResult<Boolean> result = isLastSnapshotForCompute(context);
        Operation[]  patchComputeOp = new Operation[1];
        result.whenComplete((b, e) -> {
            if (e != null) {
                logSevere(e);
                deferredResult.fail(e);
                return;
            }
            if (b) {
                ComputeStateWithDescription compute = context.computeDescription;
                compute.customProperties.put(ComputeProperties.CUSTOM_PROP_COMPUTE_HAS_SNAPSHOTS, Boolean.FALSE.toString());
                // patch compute adding property that it _hasSnapshots
                logInfo("Updating the state of compute resource: %s", compute.name);
                patchComputeOp[0] = Operation
                        .createPatch(UriUtils.buildUri(getHost(), snapshot.computeLink))
                        .setBody(compute)
                        .setReferer(getUri());
                context.snapshotOperations.add(patchComputeOp[0]);
            }
            OperationJoin.JoinedCompletionHandler joinCompletion = (ox,
                                                                    exc) -> {
                if (exc != null) {
                    this.logSevere(() -> String.format("Error updating the snapshot states: %s",
                            Utils.toString(exc)));
                    deferredResult.fail(
                            new IllegalStateException("Error updating the snapshot states"));
                    return;
                }
                deferredResult.complete(context);
            };

            context.snapshotOperations.add(Operation
                    .createDelete(UriUtils.buildUri(getHost(), snapshot.documentSelfLink))
                    .setReferer(getUri()));
            OperationJoin joinOp = OperationJoin.create(context.snapshotOperations);
            joinOp.setCompletion(joinCompletion);
            joinOp.sendWith(this.getHost());
        });
    }

    private DeferredResult<Boolean> isLastSnapshotForCompute(SnapshotContext context) {
        DeferredResult<Boolean> dr = new DeferredResult<>();
        // find if for the compute has only one snapshot (the one which is to be deleted)
        QueryTask qTask = getQueryWithFilters(context.snapshotState.computeLink, SnapshotState.FIELD_NAME_COMPUTE_LINK);

        Operation.createPost(UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS))
                .setBody(qTask)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logInfo(String.format("Failure getting snapshot state: %s", Utils.toString(e)));
                        dr.fail(e);
                        return;
                    }
                    QueryResultsProcessor rp = QueryResultsProcessor.create(o);
                    List<SnapshotState> snapshotsFinal;
                    if (!rp.hasResults()) {
                        dr.complete(Boolean.FALSE);
                    } else {
                        snapshotsFinal = rp.streamDocuments(SnapshotState.class)
                                .collect(Collectors.toList());
                        dr.complete(snapshotsFinal.size() == 1);
                    }
                }).sendWith(this);
        return dr;
    }

    private DeferredResult<List<SnapshotState>> getChildSnapshots(String snapshotLink, TaskManager mgr) {
        DeferredResult<List<SnapshotState>> snapshotStates = new DeferredResult<>();

        // find the child snapshots for the given snapshot document link
        QueryTask qTask = getQueryWithFilters(snapshotLink, SnapshotState.FIELD_NAME_PARENT_LINK);

        Operation.createPost(UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS))
                .setBody(qTask)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning(String.format("Failure retrieving the child snapshots %s", Utils.toString(e)));
                        snapshotStates.fail(e);
                        return;
                    }
                    QueryResultsProcessor rp = QueryResultsProcessor.create(o);

                    List<SnapshotState> snapshotsTemp = new ArrayList<>();
                    if (rp.hasResults()) {
                        snapshotsTemp = rp.streamDocuments(SnapshotState.class)
                                .collect(Collectors.toList());
                    }
                    snapshotStates.complete(snapshotsTemp);
                }).sendWith(this);
        return snapshotStates;
    }

    private QueryTask getQueryWithFilters(String documentSelfLink, String filterParamType) {
        QueryTask.Query snapshotQuery = QueryTask.Query.Builder.create()
                .addKindFieldClause(SnapshotState.class)
                .addFieldClause(filterParamType, documentSelfLink)
                .build();
        QueryTask qTask = QueryTask.Builder.createDirectTask()
                .setQuery(snapshotQuery)
                .addOption(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT)
                .build();
        return qTask;
    }

    private ResourceOperationSpecService.ResourceOperationSpec[] getResourceOperationSpecs() {
        ResourceOperationSpecService.ResourceOperationSpec createSnapshotSpec = getResourceOperationSpec(ResourceOperation.CREATE_SNAPSHOT, null);
        ResourceOperationSpecService.ResourceOperationSpec deleteSnapshotSpec = getResourceOperationSpec(ResourceOperation.DELETE_SNAPSHOT,
                TargetCriteria.RESOURCE_HAS_SNAPSHOTS.getCriteria());
        return new ResourceOperationSpecService.ResourceOperationSpec[]{createSnapshotSpec, deleteSnapshotSpec};
    }

    private ResourceOperationSpecService.ResourceOperationSpec getResourceOperationSpec(ResourceOperation operationType,
                                                                                        String targetCriteria) {
        ResourceOperationSpecService.ResourceOperationSpec spec = new ResourceOperationSpecService.ResourceOperationSpec();
        spec.adapterReference = AdapterUriUtil.buildAdapterUri(getHost(), SELF_LINK);
        spec.endpointType = PhotonModelConstants.EndpointType.vsphere.name();
        spec.resourceType = ResourceOperationSpecService.ResourceType.COMPUTE;
        spec.operation = operationType.operation;
        spec.name = operationType.displayName;
        spec.description = operationType.description;
        spec.targetCriteria = targetCriteria;
        return spec;
    }
}
