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
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.GetMoRef;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.vim25.DVPortgroupConfigSpec;
import com.vmware.vim25.DistributedVirtualPortgroupPortgroupType;
import com.vmware.vim25.InvalidPropertyFaultMsg;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.TaskInfoState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState.TaskStage;

public class CreatePortgroupFlow extends BaseVsphereNetworkProvisionFlow {

    private NetworkState networkState;
    private NetworkState dvsState;

    public CreatePortgroupFlow(StatelessService service, NetworkInstanceRequest req) {
        super(service, req);
    }

    @Override
    public DeferredResult<?> prepare(DeferredResult<Void> start) {
        return start
                .thenCompose(this::fetchNetworkState)
                .thenCompose(this::fetchDvs)
                .thenCompose(this::createPortgroup);
    }

    private DeferredResult<Operation> fetchDvs(Operation stateOp) {
        this.networkState = stateOp.getBody(NetworkState.class);
        String dvsLink = CustomProperties.of(this.networkState).getString(DvsProperties.PARENT_DVS_LINK);
        if (dvsLink == null) {
            return DeferredResult.failed(new IllegalArgumentException("Portgroup must be linked to a parent DVS"));
        }

        Operation op = Operation.createGet(getService().getHost(), dvsLink);
        return getService().sendWithDeferredResult(op);
    }

    private DeferredResult<Void> createPortgroup(Operation dvsOp) {
        this.dvsState = dvsOp.getBody(NetworkState.class);

        DeferredResult<Void> res = new DeferredResult<>();

        getVsphereIoPool().submit(getService(),
                this.networkState.adapterManagementReference,
                this.networkState.authCredentialsLink,
                createPortgroupInVsphere(res));

        return res;
    }

    private ConnectionCallback createPortgroupInVsphere(DeferredResult<Void> result) {
        return (connection, error) -> {
            if (error != null) {
                result.fail(error);
                return;
            }

            // extract moref of the parent DVS switch
            ManagedObjectReference dvsRef = CustomProperties.of(this.dvsState)
                    .getMoRef(CustomProperties.MOREF);

            DVPortgroupConfigSpec pgSpec = createDefaultPortgroupSpec();

            ManagedObjectReference task;
            try {
                task = connection.getVimPort().createDVPortgroupTask(dvsRef, pgSpec);
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

            ManagedObjectReference pg = (ManagedObjectReference) taskInfo.getResult();

            String pgKey = null;
            try {
                pgKey = new GetMoRef(connection).entityProp(pg, DvsProperties.PORT_GROUP_KEY);
            } catch (InvalidPropertyFaultMsg | RuntimeFaultFaultMsg ignore) {
                getService().logWarning("Cannot retrieve porgroup key of %s", VimUtils.convertMoRefToString(pg));
            }

            // store the moref as custom property
            CustomProperties.of(this.networkState)
                    .put(CustomProperties.MOREF, pg)
                    .put(DvsProperties.PORT_GROUP_KEY, pgKey);

            OperationContext.setFrom(getOperationContext());
            Operation.createPatch(getService().getHost(), this.networkState.documentSelfLink)
                    .setBody(this.networkState)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            result.fail(e);
                            return;
                        }

                        result.complete(null);
                        getTaskManager().patchTask(TaskStage.FINISHED);
                    })
                    .sendWith(getService());
        };
    }

    private DVPortgroupConfigSpec createDefaultPortgroupSpec() {
        DVPortgroupConfigSpec res = new DVPortgroupConfigSpec();
        res.setName(this.networkState.name);
        res.setDescription("Created from " + this.networkState.documentSelfLink);
        res.setType(DistributedVirtualPortgroupPortgroupType.EPHEMERAL.value());
        return res;
    }

    private DeferredResult<Operation> fetchNetworkState(Void start) {
        Operation op = Operation.createGet(getRequest().resourceReference);
        return getService().sendWithDeferredResult(op);
    }
}
