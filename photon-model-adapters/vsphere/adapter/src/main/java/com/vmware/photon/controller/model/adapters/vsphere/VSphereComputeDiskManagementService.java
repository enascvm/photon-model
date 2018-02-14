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

package com.vmware.photon.controller.model.adapters.vsphere;

import static com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperation.ATTACH_DISK;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.DISK_CONTROLLER_NUMBER;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.PROVIDER_DISK_UNIQUE_ID;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.DISK_LINK;

import java.util.ArrayList;

import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperation;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationRequest;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationUtils;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.util.PhotonModelUriUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationSequence;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

/**
 * VSphereComputeDiskD2Service deals with all the disk related day 2 operations on the compute. For
 * ex, attach disk to a VM, detach disk from a VM.
 */
public class VSphereComputeDiskManagementService extends StatelessService {
    public static final String SELF_LINK = VSphereUriPaths.COMPUTE_DISK_DAY2_SERVICE;

    @Override
    public void handleStart(Operation startPost) {
        Operation.CompletionHandler handler = (op, exc) -> {
            if (exc != null) {
                startPost.fail(exc);
            } else {
                startPost.complete();
            }
        };
        ResourceOperationUtils
                .registerResourceOperation(this, handler, getResourceOperationSpecs());
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }

        ResourceOperationRequest request = op.getBody(ResourceOperationRequest.class);
        try {
            validateInputRequest(request);
        } catch (Exception e) {
            op.fail(e);
            return;
        }
        op.setStatusCode(Operation.STATUS_CODE_CREATED);
        op.complete();

        TaskManager taskManager = new TaskManager(this, request.taskReference,
                request.resourceLink());
        logInfo("Handle operation %s for compute %s.", request.operation, request.resourceLink());

        VSphereVMDiskContext
                .populateVMDiskContextThen(this, createInitialContext(taskManager, request, op),
                        ctx -> {
                            if (request.operation.equals(ResourceOperation.ATTACH_DISK.operation)) {
                                handleAttachDisk(ctx);
                            } else if (request.operation.equals(ResourceOperation.DETACH_DISK.operation)) {
                                handleDetachDisk(ctx);
                            } else {
                                IllegalArgumentException exception = new IllegalArgumentException(
                                        String.format("Unknown Operation %s for a disk",
                                                request.operation));
                                ctx.fail(exception);
                            }
                        });
    }

    /**
     * Perform attaching disk to VM.
     */
    private void handleAttachDisk(VSphereVMDiskContext ctx) {
        if (ctx.request.isMockRequest) {
            updateComputeAndDiskStateForAttach(ctx, ctx.diskState);
            return;
        }

        // Invoke the adapter
        ctx.pool.submit(ctx.getAdapterManagementReference(), ctx.vSphereCredentials,
                (connection, ce) -> {
                    if (ctx.fail(ce)) {
                        return;
                    }

                    InstanceDiskClient diskClient = new InstanceDiskClient(connection, ctx,
                            getHost());
                    try {
                        if (ctx.contentToUpload == null || ctx.contentToUpload.length == 0) {
                            DiskService.DiskState diskState = diskClient.attachDiskToVM();
                            updateComputeAndDiskStateForAttach(ctx, diskState);
                            return;
                        }

                        diskClient.uploadISOContents(ctx.contentToUpload)
                                .whenComplete((ds, t) -> {
                                    if (t != null) {
                                        logSevere(
                                                "Upload of ISO contents failed. Error: %s",
                                                t.getMessage());
                                        ctx.fail(t);
                                        return;
                                    }

                                    //upload succeeded - delete asynchronously
                                    Operation.createDelete(this.getHost(), ctx.contentLink).sendWith(this);
                                    // Create a new thread pool here as the upload content uses a
                                    // custom service client, it empties vim port in the
                                    // connection. Hence subsequent call fails with NPE.
                                    ctx.pool.submit(
                                            ctx.getAdapterManagementReference(),
                                            ctx.vSphereCredentials,
                                            (newConn, excep) -> {
                                                if (ctx.fail(excep)) {
                                                    return;
                                                }
                                                InstanceDiskClient newClient = new
                                                        InstanceDiskClient(
                                                        newConn,
                                                        ctx, getHost());
                                                try {
                                                    ctx.diskState = ds;
                                                    DiskService.DiskState diskState = newClient
                                                            .attachDiskToVM();
                                                    updateComputeAndDiskStateForAttach(ctx, diskState);
                                                } catch (Exception ex) {
                                                    ctx.fail(ex);
                                                }
                                            });
                                });
                    } catch (Exception e) {
                        ctx.fail(e);
                    }
                });
    }

    /**
     * Perform detaching disk from VM.
     */
    private void handleDetachDisk(VSphereVMDiskContext ctx) {
        if (ctx.request.isMockRequest) {
            updateComputeAndDiskStateForDetach(ctx);
            return;
        }

        // Invoke the adapter
        ctx.pool.submit(ctx.getAdapterManagementReference(), ctx.vSphereCredentials,
                (connection, ce) -> {
                    if (ctx.fail(ce)) {
                        return;
                    }

                    InstanceDiskClient diskClient = new InstanceDiskClient(connection, ctx, getHost());
                    try {
                        diskClient.detachDiskFromVM();
                        updateComputeAndDiskStateForDetach(ctx);
                    } catch (Exception e) {
                        ctx.fail(e);
                    }
                });
    }

    /**
     * Update compute and disk state for attach operation.
     */
    private void updateComputeAndDiskStateForAttach(VSphereVMDiskContext ctx,
            DiskService.DiskState newDiskState) {
        // Call patch on the disk to update the details of the disk and then
        // finish the task
        if (ctx.computeDesc.diskLinks == null) {
            ctx.computeDesc.diskLinks = new ArrayList<>();
        }

        if (!ctx.computeDesc.diskLinks.contains(newDiskState.documentSelfLink)) {
            ctx.computeDesc.diskLinks.add(newDiskState.documentSelfLink);
        }

        // update endpoint link
        AdapterUtils.addToEndpointLinks(newDiskState, ctx.computePlacementHost.endpointLink);
        // update the disk state with mock values
        if (ctx.request.isMockRequest) {
            ctx.diskState.status = DiskService.DiskStatus.ATTACHED;
            CustomProperties.of(newDiskState)
                    .put(PROVIDER_DISK_UNIQUE_ID, newDiskState.id)
                    .put(DISK_CONTROLLER_NUMBER, 0);
        }
        OperationSequence.create(createDiskPatch(newDiskState))
                .next(createComputePatch(ctx))
                .next(ctx.mgr.createTaskPatch(TaskState.TaskStage.FINISHED))
                .setCompletion(ctx.failTaskOnError())
                .sendWith(this);
    }

    /**
     * Update compute and disk state for attach operation.
     */
    private void updateComputeAndDiskStateForDetach(VSphereVMDiskContext ctx) {
        // Update the disk state to be AVAILABLE as it is detached successfully
        ctx.diskState.status = DiskService.DiskStatus.AVAILABLE;
        ctx.diskState.sourceImageReference = null;
        CustomProperties.of(ctx.diskState)
                .put(DISK_CONTROLLER_NUMBER, (String) null)
                .put(PROVIDER_DISK_UNIQUE_ID, (String) null);
        ctx.diskState.id = UriUtils.getLastPathSegment(ctx.diskState.documentSelfLink);

        // Remove the link from the compute state. Safety null check
        if (ctx.computeDesc.diskLinks != null) {
            ctx.computeDesc.diskLinks.remove(ctx.diskState.documentSelfLink);
        }

        OperationSequence.create(createDiskPut(ctx.diskState))
                .next(createComputePut(ctx))
                .next(ctx.mgr.createTaskPatch(TaskState.TaskStage.FINISHED))
                .setCompletion(ctx.failTaskOnError())
                .sendWith(this);
    }

    /**
     * Create disk patch request to update the details of the disk.
     */
    private Operation createDiskPatch(DiskService.DiskState ds) {
        return Operation.createPatch(PhotonModelUriUtils.createInventoryUri(getHost(), ds.documentSelfLink))
                .setBody(ds);
    }

    /**
     * Create disk put request to update the details of the disk.
     */
    private Operation createDiskPut(DiskService.DiskState ds) {
        return Operation.createPut(PhotonModelUriUtils.createInventoryUri(getHost(), ds.documentSelfLink))
                .setBody(ds);
    }

    /**
     * Creates initial disk context and gets a thread from the pool to execute the remote request.
     */
    private VSphereVMDiskContext createInitialContext(TaskManager taskManager,
            ResourceOperationRequest request, Operation op) {
        VSphereVMDiskContext initialContext = new VSphereVMDiskContext(taskManager, request, op);

        initialContext.pool = VSphereIOThreadPoolAllocator.getPool(this);
        return initialContext;
    }

    /**
     * Validate input request before performing the disk day 2 operation.
     */
    private void validateInputRequest(ResourceOperationRequest request) {
        if (request.operation == null || request.operation.isEmpty()) {
            throw new IllegalArgumentException(
                    "Compute operation should be Disk.Attach or Disk.Detach.");
        }

        if (request.resourceReference == null) {
            throw new IllegalArgumentException(
                    "Compute resource to perform the disk operation cannot be empty");
        }

        if (request.payload == null || request.payload.get(DISK_LINK) == null) {
            throw new IllegalArgumentException(
                    "Disk reference to attach to Compute cannot be empty");
        }
    }

    /**
     * List of resource operations that are supported by this service.
     */
    private ResourceOperationSpecService.ResourceOperationSpec[] getResourceOperationSpecs() {
        ResourceOperationSpecService.ResourceOperationSpec attachDiskSpec = getResourceOperationSpec(
                ATTACH_DISK);
        ResourceOperationSpecService.ResourceOperationSpec detachDiskSpec = getResourceOperationSpec(
                ResourceOperation.DETACH_DISK);
        return new ResourceOperationSpecService.ResourceOperationSpec[] { attachDiskSpec,
                detachDiskSpec };
    }

    /**
     * Define a resource operation
     */
    private ResourceOperationSpecService.ResourceOperationSpec getResourceOperationSpec(
            ResourceOperation operationType) {
        ResourceOperationSpecService.ResourceOperationSpec spec = new ResourceOperationSpecService.ResourceOperationSpec();
        spec.adapterReference = AdapterUriUtil.buildAdapterUri(getHost(), SELF_LINK);
        spec.endpointType = PhotonModelConstants.EndpointType.vsphere.name();
        spec.resourceType = ResourceOperationSpecService.ResourceType.COMPUTE;
        spec.operation = operationType.operation;
        spec.name = operationType.displayName;
        spec.description = operationType.description;
        return spec;
    }

    /**
     * Patch the compute with the details of the disk references and complete the request.
     */
    private Operation createComputePatch(VSphereVMDiskContext ctx) {
        return Operation.createPatch(PhotonModelUriUtils.createInventoryUri(getHost(), ctx
                .computeDesc.documentSelfLink))
                .setBody(ctx.computeDesc);
    }

    /**
     * Invoke PUT operation on compute to remove the disk link from the links.
     */
    private Operation createComputePut(VSphereVMDiskContext ctx) {
        return Operation.createPut(PhotonModelUriUtils.createInventoryUri(getHost(), ctx
                .computeDesc.documentSelfLink))
                .setBody(ctx.computeDesc);
    }
}
