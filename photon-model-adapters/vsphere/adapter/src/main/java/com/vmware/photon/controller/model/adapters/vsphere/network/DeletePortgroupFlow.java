/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.model.adapters.vsphere.network;

import com.vmware.photon.controller.model.adapterapi.NetworkInstanceRequest;
import com.vmware.photon.controller.model.adapters.vsphere.CustomProperties;
import com.vmware.photon.controller.model.adapters.vsphere.VSphereIOThreadPool.ConnectionCallback;
import com.vmware.photon.controller.model.adapters.vsphere.VimUtils;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.TaskInfoState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState.TaskStage;

public class DeletePortgroupFlow extends BaseVsphereNetworkProvisionFlow {

    private NetworkState networkState;

    public DeletePortgroupFlow(StatelessService service, NetworkInstanceRequest req) {
        super(service, req);
    }

    @Override
    public DeferredResult<Void> prepare(DeferredResult<Void> start) {
        return start
                .thenCompose(this::fetchState)
                .thenCompose(this::deletePortgroup);
    }

    private DeferredResult<Void> deletePortgroup(Operation stateOp) {
        this.networkState = stateOp.getBody(NetworkState.class);

        DeferredResult<Void> res = new DeferredResult<>();

        ManagedObjectReference pgRef = CustomProperties.of(this.networkState).getMoRef(CustomProperties.MOREF);
        if (pgRef == null) {
            // a provisioning has failed mid-flight, just proceed, skipping actual vc call
            res.complete(null);
            return res;
        }

        getVsphereIoPool().submit(getService(),
                this.networkState.adapterManagementReference,
                this.networkState.authCredentialsLink,
                deletePortgroupInVsphere(res));

        return res;
    }

    private ConnectionCallback deletePortgroupInVsphere(DeferredResult<Void> result) {
        return (connection, error) -> {
            if (error != null) {
                result.fail(error);
                return;
            }

            ManagedObjectReference pgRef = CustomProperties.of(this.networkState).getMoRef(CustomProperties.MOREF);

            ManagedObjectReference task;
            try {
                task = connection.getVimPort().destroyTask(pgRef);
            } catch (Exception e) {
                result.fail(e);
                return;
            }

            TaskInfo taskInfo;
            try {
                taskInfo = VimUtils.waitTaskEnd(connection, task);
            } catch (Exception e) {
                result.fail(e);
                return;
            }

            if (taskInfo.getState() != TaskInfoState.SUCCESS) {
                IllegalStateException e = new IllegalStateException(taskInfo.getError().getLocalizedMessage());
                result.fail(e);
                return;
            }

            // finish task
            getTaskManager().patchTask(TaskStage.FINISHED);
            result.complete(null);
        };
    }

    private DeferredResult<Operation> fetchState(Void start) {
        Operation op = Operation.createGet(getRequest().resourceReference);
        return getService().sendWithDeferredResult(op);
    }
}
