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

package com.vmware.photon.controller.model.adapters.awsadapter;

import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.StartInstancesRequest;
import com.amazonaws.services.ec2.model.StartInstancesResult;
import com.amazonaws.services.ec2.model.StopInstancesRequest;
import com.amazonaws.services.ec2.model.StopInstancesResult;

import com.vmware.photon.controller.model.adapterapi.ComputePowerRequest;
import com.vmware.photon.controller.model.adapterapi.ResourceOperationResponse;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext.BaseAdapterStage;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext.DefaultAdapterContext;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.StatelessService;

/**
 * Adapter to manage power state of an instance.
 */
public class AWSPowerService extends StatelessService {
    public static final String SELF_LINK = AWSUriPaths.AWS_POWER_ADAPTER;

    private AWSClientManager clientManager;

    public AWSPowerService() {
        this.clientManager = AWSClientManagerFactory
                .getClientManager(AWSConstants.AwsClientType.EC2);
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        ComputePowerRequest pr = op.getBody(ComputePowerRequest.class);
        op.complete();
        if (pr.isMockRequest) {
            updateComputeState(pr);
        } else {
            new DefaultAdapterContext(this, pr.resourceReference)
                    .populateContext(BaseAdapterStage.VMDESC)
                    .whenComplete((c, e) -> {
                        AmazonEC2AsyncClient client = this.clientManager.getOrCreateEC2Client(
                                c.parentAuth, c.child.description.regionId, this, pr.taskReference,
                                false);
                        applyPowerOperation(client, pr, c);
                    });
        }

    }

    private void applyPowerOperation(AmazonEC2AsyncClient client, ComputePowerRequest pr,
            DefaultAdapterContext c) {
        switch (pr.powerState) {
        case OFF:
            powerOff(client, pr, c);
            break;
        case ON:
            powerOn(client, pr, c);
            break;
        case SUSPEND:
            // TODO: Not supported yet, so simply patch the state with requested power state.
            updateComputeState(pr);
            break;
        case UNKNOWN:
        default:
            AdapterUtils.sendPatchToTask(this, pr.taskReference, ResourceOperationResponse.fail(
                    pr.resourceLink(),
                    new IllegalArgumentException("Unsupported power state transition requested.")));
        }

    }

    private void powerOn(AmazonEC2AsyncClient client, ComputePowerRequest pr,
            DefaultAdapterContext c) {
        AWSPowerService powerService = this;
        OperationContext opContext = OperationContext.getOperationContext();

        StartInstancesRequest request = new StartInstancesRequest();
        request.withInstanceIds(c.child.id);
        client.startInstancesAsync(request,
                new AsyncHandler<StartInstancesRequest, StartInstancesResult>() {
                    @Override
                    public void onSuccess(StartInstancesRequest request,
                            StartInstancesResult result) {
                        OperationContext.restoreOperationContext(opContext);
                        updateComputeState(pr);
                    }

                    @Override
                    public void onError(Exception e) {
                        OperationContext.restoreOperationContext(opContext);
                        AdapterUtils.sendPatchToTask(powerService, pr.taskReference,
                                ResourceOperationResponse.fail(pr.resourceLink(), e));
                    }
                });
    }

    private void powerOff(AmazonEC2AsyncClient client, ComputePowerRequest pr,
            DefaultAdapterContext c) {
        AWSPowerService powerService = this;
        OperationContext opContext = OperationContext.getOperationContext();

        StopInstancesRequest request = new StopInstancesRequest();
        request.withInstanceIds(c.child.id);
        client.stopInstancesAsync(request,
                new AsyncHandler<StopInstancesRequest, StopInstancesResult>() {
                    @Override
                    public void onSuccess(StopInstancesRequest request,
                            StopInstancesResult result) {
                        OperationContext.restoreOperationContext(opContext);
                        updateComputeState(pr);
                    }

                    @Override
                    public void onError(Exception e) {
                        OperationContext.restoreOperationContext(opContext);
                        AdapterUtils.sendPatchToTask(powerService, pr.taskReference,
                                ResourceOperationResponse.fail(pr.resourceLink(), e));
                    }
                });
    }

    private void updateComputeState(ComputePowerRequest pr) {
        ComputeState state = new ComputeState();
        state.powerState = pr.powerState;
        Operation.createPatch(pr.resourceReference)
                .setBody(state)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        AdapterUtils.sendPatchToTask(this, pr.taskReference,
                                ResourceOperationResponse.fail(pr.resourceLink(), e));
                        return;
                    }
                    AdapterUtils.sendPatchToTask(this, pr.taskReference,
                            ResourceOperationResponse.finish(pr.resourceLink()));
                })
                .sendWith(this);
    }

}
