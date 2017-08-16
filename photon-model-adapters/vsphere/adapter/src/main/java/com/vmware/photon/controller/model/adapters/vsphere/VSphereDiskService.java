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

import com.vmware.photon.controller.model.adapterapi.DiskInstanceRequest;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationSequence;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState;

/**
 * Handles disk related operations for vsphere endpoint.
 */
public class VSphereDiskService extends StatelessService {
    public static final String SELF_LINK = VSphereUriPaths.DISK_SERVICE;

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

        if (request.isMockRequest
                && request.requestType != DiskInstanceRequest.DiskRequestType.DELETE) {
            handleMockRequest(taskManager);
            return;
        }

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
        ctx.pool.submit(this, ctx.adapterManagementReference, ctx.vSphereCredentials,
                (connection, ce) -> {
                    if (ctx.fail(ce)) {
                        return;
                    }

                    DiskClient diskClient = new DiskClient(connection, ctx);
                    try {
                        DiskService.DiskState resultDiskState = diskClient.createVirtualDisk();

                        // Call patch on the disk to update the details of the disk and then
                        // finish the task
                        OperationSequence.create(createDiskPatch(resultDiskState))
                                .next(ctx.mgr.createTaskPatch(TaskState.TaskStage.FINISHED))
                                .setCompletion(ctx.failTaskOnError())
                                .sendWith(this);
                    } catch (Exception e) {
                        ctx.fail(e);
                    }
                });
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
     * Finish the disk delete operation by cleaning up the disk reference in the system.
     */
    private void finishDiskDeleteOperation(DiskContext ctx) {
        OperationSequence.create(deleteDisk(ctx.diskState.documentSelfLink))
                .next(ctx.mgr.createTaskPatch(TaskState.TaskStage.FINISHED))
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
        return Operation.createPatch(this, ds.documentSelfLink)
                .setBody(ds);
    }

    /**
     * Delete disk request to remove the reference from the system as the disk is deleted on the
     * server.
     */
    private Operation deleteDisk(String documentSelfLink) {
        return Operation.createDelete(this, documentSelfLink);
    }

    private void handleMockRequest(TaskManager mgr) {
        mgr.patchTask(TaskState.TaskStage.FINISHED);
    }
}