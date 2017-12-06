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

import static com.vmware.photon.controller.model.UriPaths.IAAS_API_ENABLED;

import com.vmware.photon.controller.model.adapterapi.SubnetInstanceRequest;
import com.vmware.photon.controller.model.adapters.vsphere.CustomProperties;
import com.vmware.photon.controller.model.adapters.vsphere.VSphereIOThreadPool.ConnectionCallback;
import com.vmware.photon.controller.model.adapters.vsphere.VimUtils;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.SessionUtil;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.util.PhotonModelUriUtils;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.TaskInfoState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState.TaskStage;

public class DeletePortgroupFlow extends BaseVsphereNetworkProvisionFlow {

    private SubnetState subnetState;

    private NetworkState networkState;

    private Operation operation;

    public DeletePortgroupFlow(StatelessService service, Operation op, SubnetInstanceRequest req) {
        this(service, req);
        this.operation = op;
    }

    public DeletePortgroupFlow(StatelessService service, SubnetInstanceRequest req) {
        super(service, req);
    }

    @Override
    public DeferredResult<Void> prepare(DeferredResult<Void> start) {
        return start
                .thenCompose(this::fetchSubnet)
                .thenCompose(this::fetchNetwork)
                .thenCompose(this::deletePortgroup)
                .thenCompose(this::deleteSubnet);
    }

    private DeferredResult<Operation> fetchNetwork(Operation stateOp) {
        this.subnetState = stateOp.getBody(SubnetState.class);
        String dvsLink = this.subnetState.networkLink;
        if (dvsLink == null) {
            return DeferredResult.failed(new IllegalArgumentException("Portgroup must be linked to a parent DVS"));
        }

        Operation op = Operation.createGet(
                PhotonModelUriUtils.createInventoryUri(getService().getHost(), dvsLink));
        return getService().sendWithDeferredResult(op);
    }

    private DeferredResult<Void> deletePortgroup(Operation stateOp) {
        this.networkState = stateOp.getBody(NetworkState.class);

        DeferredResult<Void> res = new DeferredResult<>();

        ManagedObjectReference pgRef = CustomProperties.of(this.subnetState)
                .getMoRef(CustomProperties.MOREF);

        if (pgRef == null) {
            // a provisioning has failed mid-flight, just proceed, skipping actual vc call
            res.complete(null);
            return res;
        }

        if (IAAS_API_ENABLED) {
            if (this.operation != null) {
                return DeferredResult.failed(new IllegalArgumentException("Unable to authenticate"));
            }
            SessionUtil.retrieveExternalToken(getService(), this.operation
                    .getAuthorizationContext()).whenComplete((authCredentialsServiceState,
                    throwable) -> {
                        if (throwable != null) {
                            res.fail(throwable);
                            return;
                        }
                        getVsphereIoPool().submit(this.networkState.adapterManagementReference,
                                authCredentialsServiceState, deletePortgroupInVsphere(res));
                    });
        } else {
            getVsphereIoPool().submit(getService(),
                    this.networkState.adapterManagementReference,
                    this.networkState.authCredentialsLink,
                    deletePortgroupInVsphere(res));
        }

        return res;
    }

    private ConnectionCallback deletePortgroupInVsphere(DeferredResult<Void> result) {
        return (connection, error) -> {
            if (error != null) {
                result.fail(error);
                return;
            }

            ManagedObjectReference pgRef = CustomProperties.of(this.subnetState).getMoRef(CustomProperties.MOREF);

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

    private DeferredResult<Operation> fetchSubnet(Void start) {
        Operation op = Operation.createGet(
                PhotonModelUriUtils.createInventoryUri(getService().getHost(),getRequest()
                        .resourceReference));
        return getService().sendWithDeferredResult(op);
    }

    private DeferredResult<Void> deleteSubnet(Void start) {
        Operation op = Operation.createDelete(
                PhotonModelUriUtils.createInventoryUri(getService().getHost(),getRequest()
                .resourceReference));
        return getService().sendWithDeferredResult(op).thenAccept(__ -> { });
    }
}
