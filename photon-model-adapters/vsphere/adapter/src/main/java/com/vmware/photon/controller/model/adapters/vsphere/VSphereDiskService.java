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

import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.DISK_DATASTORE_NAME;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.DISK_FULL_PATH;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.DISK_PARENT_DIRECTORY;

import com.vmware.photon.controller.model.adapterapi.DiskInstanceRequest;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.util.PhotonModelUriUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationSequence;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState;

/**
 * Handles disk related operations for vsphere endpoint.
 */
public class VSphereDiskService extends StatelessService {
    public static final String SELF_LINK = VSphereUriPaths.DISK_SERVICE;
    private static final String MOCK_VALUE = "mock";

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }

        DiskInstanceRequest request = op.getBody(DiskInstanceRequest.class);
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

        DiskContext.populateContextThen(this, createInitialContext(taskManager, request), ctx -> {
            switch (request.requestType) {
            case CREATE:
                handleCreateDisk(ctx);
                break;
            case DELETE:
                handleDeleteDisk(ctx);
                break;
            default:
                Throwable error = new IllegalStateException(
                        "Unsupported requestType " + request.requestType);
                ctx.fail(error);
            }
        });
    }

    /**
     * Perform creation of disk in the server and then updates the disk reference with the details
     * of the created disk.
     */
    private void handleCreateDisk(DiskContext ctx) {
        if (ctx.diskInstanceRequest.isMockRequest) {
            updateMockDiskState(ctx.diskState);
            finishDiskCreateOperation(ctx);
            return;
        }
        ctx.pool.submit(this, ctx.adapterManagementReference, ctx.vSphereCredentials,
                (connection, ce) -> {
                    if (ctx.fail(ce)) {
                        return;
                    }

                    DiskClient diskClient = new DiskClient(connection, ctx);
                    try {
                        diskClient.createVirtualDisk();
                        finishDiskCreateOperation(ctx);
                    } catch (Exception e) {
                        ctx.fail(e);
                    }
                });
    }

    private void updateMockDiskState(DiskService.DiskStateExpanded diskState) {
        CustomProperties.of(diskState)
                .put(DISK_FULL_PATH, MOCK_VALUE)
                .put(DISK_PARENT_DIRECTORY, MOCK_VALUE)
                .put(DISK_DATASTORE_NAME, MOCK_VALUE);
        diskState.status = DiskService.DiskStatus.AVAILABLE;
    }

    /**
     * Performs deletion of the request disk from the server
     */
    private void handleDeleteDisk(DiskContext ctx) {
        if (ctx.diskInstanceRequest.isMockRequest) {
            finishDiskDeleteOperation(ctx);
            return;
        }
        ctx.pool.submit(this, ctx.adapterManagementReference, ctx.vSphereCredentials,
                (connection, ce) -> {
                    if (ctx.fail(ce)) {
                        return;
                    }

                    DiskClient diskClient = new DiskClient(connection, ctx);
                    try {
                        diskClient.deleteVirtualDisk();

                        // Call delete on the disk resource and finish the request.
                        finishDiskDeleteOperation(ctx);
                    } catch (Exception e) {
                        ctx.fail(e);
                    }
                });
    }

    /**
     * Finish the disk create operation by updating the details of the disk
     */
    private void finishDiskCreateOperation(DiskContext ctx) {
        // Call patch on the disk to update the details of the disk and then
        // finish the task
        OperationSequence.create(createDiskPatch(ctx.diskState))
                .next(ctx.mgr.createTaskPatch(TaskState.TaskStage.FINISHED))
                .setCompletion(ctx.failTaskOnError())
                .sendWith(this);
    }

    /**
     * Finish the disk delete operation by cleaning up the disk reference in the system.
     */
    private void finishDiskDeleteOperation(DiskContext ctx) {
        // Clean up disk description link if it is present.
        String diskDescLink = CustomProperties.of(ctx.diskState).getString(
                PhotonModelConstants.TEMPLATE_DISK_LINK);
        OperationSequence seq = null;
        if (diskDescLink != null && !diskDescLink.isEmpty()) {
            seq = OperationSequence.create(deleteDisk(diskDescLink));
        }
        if (seq == null) {
            seq = OperationSequence.create(deleteDisk(ctx.diskState.documentSelfLink));
        } else {
            seq.next(deleteDisk(ctx.diskState.documentSelfLink));
        }
        seq.next(ctx.mgr.createTaskPatch(TaskState.TaskStage.FINISHED))
                .setCompletion(ctx.failTaskOnError())
                .sendWith(this);
    }

    /**
     * Creates initial disk context and gets a thread from the pool to execute the remote request.
     */
    private DiskContext createInitialContext(TaskManager taskManager, DiskInstanceRequest request) {
        DiskContext initialContext = new DiskContext(taskManager, request);

        initialContext.pool = VSphereIOThreadPoolAllocator.getPool(this);
        return initialContext;
    }

    /**
     * Validate input request before performing the disk operation.
     */
    private void validateInputRequest(DiskInstanceRequest request) {
        if (request.resourceReference == null) {
            throw new IllegalArgumentException("Disk description cannot be empty");
        }
    }

    /**
     * Create disk patch request to update the details of the disk.
     */
    private Operation createDiskPatch(DiskService.DiskState ds) {
        return Operation.createPatch(PhotonModelUriUtils.createDiscoveryUri(getHost(), ds.documentSelfLink))
                .setBody(ds);
    }

    /**
     * Delete disk request to remove the reference from the system as the disk is deleted on the
     * server.
     */
    private Operation deleteDisk(String documentSelfLink) {
        return Operation.createDelete(
                PhotonModelUriUtils.createDiscoveryUri(getHost(), documentSelfLink));
    }
}
