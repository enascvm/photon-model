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

import static com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationUtils.TargetCriteria;

import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.RebootInstancesRequest;
import com.amazonaws.services.ec2.model.RebootInstancesResult;

import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperation;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationRequest;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceOperationSpec;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceType;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationUtils;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext.BaseAdapterStage;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext.DefaultAdapterContext;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.StatelessService;

/**
 * Adapter to Restart EC2 instance.
 * An EC2 instance reboot is equivalent to an operating system reboot. In most cases,
 * it takes only a few minutes to reboot your instance. When you reboot an instance,
 * it remains on the same physical host, so your instance keeps its public DNS name
 * (IPv4), private IPv4 address, IPv6 address (if applicable), and any data on its
 * instance store volumes
 */

public class AWSRebootService extends StatelessService {
    public static final String SELF_LINK = ResourceOperationSpecService.buildDefaultAdapterLink(
            EndpointType.aws.name(), ResourceType.COMPUTE,
            ResourceOperation.REBOOT.name());

    private AWSClientManager clientManager;

    @Override
    public void handleStart(Operation startPost) {
        this.clientManager = AWSClientManagerFactory
                .getClientManager(AWSConstants.AwsClientType.EC2);

        CompletionHandler completionHandler = (op, ex) -> {
            if (ex != null) {
                startPost.fail(ex);
            } else {
                startPost.complete();
            }
        };
        ResourceOperationUtils.registerResourceOperation(this,
                completionHandler, getResourceOperationSpec());
    }

    @Override
    public void handleStop(Operation op) {
        AWSClientManagerFactory.returnClientManager(this.clientManager,
                AWSConstants.AwsClientType.EC2);

        super.handleStop(op);
    }

    private void reboot(AmazonEC2AsyncClient client, ResourceOperationRequest pr,
                        DefaultAdapterContext c) {
        if (!c.child.powerState.equals(ComputeService.PowerState.ON)) {
            logInfo("Cannot Reboot an EC2 instance in powered off state." +
                    "The machine should be powered on first");
            c.taskManager.patchTaskToFailure(new IllegalStateException("Incorrect power state. Expected the machine " +
                    "to be powered on "));
            return;
        }

        RebootInstancesRequest request  = new RebootInstancesRequest();
        request.withInstanceIds(c.child.id);
        client.rebootInstancesAsync(request,
                new AsyncHandler<RebootInstancesRequest, RebootInstancesResult>() {
                    @Override
                    public void onSuccess(RebootInstancesRequest request,
                                          RebootInstancesResult result) {
                        c.taskManager.finishTask();
                    }

                    @Override
                    public void onError(Exception e) {
                        c.taskManager.patchTaskToFailure(e);
                    }
                });
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        ResourceOperationRequest request = op.getBody(ResourceOperationRequest.class);
        op.complete();

        logInfo("Handle operation %s for compute %s.",
                request.operation, request.resourceLink());

        if (request.isMockRequest) {
            updateComputeState(request, new DefaultAdapterContext(this, request));
        } else {
            new DefaultAdapterContext(this, request)
                    .populateBaseContext(BaseAdapterStage.VMDESC)
                    .whenComplete((c, e) -> {
                        AmazonEC2AsyncClient client = this.clientManager.getOrCreateEC2Client(
                                c.endpointAuth, c.child.description.regionId, this,
                                (t) -> c.taskManager.patchTaskToFailure(t));
                        if (client != null) {
                            reboot(client,request,c);
                        }
                        // if the client is found to be null, it implies the task is already patched to
                        // failure in the catch block of getOrCreateEC2Client method (failConsumer.accept()).
                        // So it is not required to patch it again.
                    });
        }
    }

    private ResourceOperationSpec getResourceOperationSpec() {
        ResourceOperationSpec spec = new ResourceOperationSpec();
        spec.adapterReference = AdapterUriUtil.buildAdapterUri(getHost(), SELF_LINK);
        spec.endpointType = EndpointType.aws.name();
        spec.resourceType = ResourceType.COMPUTE;
        spec.operation = ResourceOperation.REBOOT.operation;
        spec.name = ResourceOperation.REBOOT.displayName;
        spec.description = ResourceOperation.REBOOT.description;
        spec.targetCriteria = TargetCriteria.RESOURCE_POWER_STATE_ON.getCriteria();
        return spec;
    }

    private void updateComputeState(ResourceOperationRequest ror, DefaultAdapterContext c) {
        ComputeState state = new ComputeState();
        state.powerState = ComputeService.PowerState.ON;
        Operation.createPatch(ror.resourceReference)
                .setBody(state)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        c.taskManager.patchTaskToFailure(e);
                        return;
                    }
                    c.taskManager.finishTask();
                })
                .sendWith(this);
    }
}
