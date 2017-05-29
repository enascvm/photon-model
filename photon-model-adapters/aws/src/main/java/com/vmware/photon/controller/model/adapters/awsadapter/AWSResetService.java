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

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.StartInstancesRequest;
import com.amazonaws.services.ec2.model.StartInstancesResult;
import com.amazonaws.services.ec2.model.StopInstancesRequest;
import com.amazonaws.services.ec2.model.StopInstancesResult;

import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSAsyncHandler;
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

public class AWSResetService extends StatelessService {
    public static final String SELF_LINK = ResourceOperationSpecService.buildDefaultAdapterLink(
            EndpointType.aws.name(), ResourceType.COMPUTE,
            ResourceOperation.RESET.name());

    private AWSClientManager clientManager;

    public AWSResetService() {
        this.clientManager = AWSClientManagerFactory
                .getClientManager(AWSConstants.AwsClientType.EC2);
    }

    @Override
    public void handleStart(Operation startPost) {
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

    private void reset(AmazonEC2AsyncClient client, ResourceOperationRequest pr,
                        DefaultAdapterContext c) {
        if (!c.child.powerState.equals(ComputeService.PowerState.ON)) {
            logWarning(() -> String.format("Cannot perform a reset on this EC2 instance. " +
                            "The machine should be in powered on state"));
            c.taskManager.patchTaskToFailure(new IllegalStateException("Incorrect power state. Expected the machine " +
                    "to be powered on "));
            return;
        }

        // The stop action for reset is a force stop. So we use the withForce method to set the force parameter to TRUE
        // This is similar to unplugging the machine from the power circuit.
        // The OS and the applications are forcefully stopped.
        StopInstancesRequest stopRequest  = new StopInstancesRequest();
        stopRequest.withInstanceIds(c.child.id).withForce(Boolean.TRUE);
        client.stopInstancesAsync(stopRequest,
                new AWSAsyncHandler<StopInstancesRequest, StopInstancesResult>() {
                    @Override
                    protected void handleError(Exception e) {
                        c.taskManager.patchTaskToFailure(e);
                    }

                    @Override
                    protected void handleSuccess(StopInstancesRequest request, StopInstancesResult result) {

                        AWSUtils.waitForTransitionCompletion(getHost(),
                                result.getStoppingInstances(), "stopped", client, (is, e) -> {
                                    if (e != null) {
                                        onError(e);
                                        return;
                                    }
                                    //Instances will be started only if they're successfully stopped
                                    startInstance(client,c);
                                });
                    }
                });
    }

    private void startInstance(AmazonEC2AsyncClient client, DefaultAdapterContext c) {
        StartInstancesRequest startRequest  = new StartInstancesRequest();
        startRequest.withInstanceIds(c.child.id);
        client.startInstancesAsync(startRequest,
                new AWSAsyncHandler<StartInstancesRequest, StartInstancesResult>() {

                    @Override
                    protected void handleError(Exception e) {
                        c.taskManager.patchTaskToFailure(e);
                    }

                    @Override
                    protected void handleSuccess(StartInstancesRequest request, StartInstancesResult result) {
                        AWSUtils.waitForTransitionCompletion(getHost(),
                                result.getStartingInstances(), "running",
                                client, (is, e) -> {
                                    if (e == null) {
                                        c.taskManager.finishTask();
                                    } else {
                                        c.taskManager.patchTaskToFailure(e);
                                    }
                                });
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

        logInfo(() -> String.format("Handle operation %s for compute %s.",
                request.operation, request.resourceLink()));

        if (request.isMockRequest) {
            updateComputeState(new DefaultAdapterContext(this, request));
        } else {
            new DefaultAdapterContext(this, request)
                    .populateBaseContext(BaseAdapterStage.VMDESC)
                    .whenComplete((c, e) -> {
                        AmazonEC2AsyncClient client = this.clientManager.getOrCreateEC2Client(
                                c.parentAuth, c.child.description.regionId, this,
                                (t) -> c.taskManager.patchTaskToFailure(t));
                        if (client != null) {
                            reset(client,request,c);
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
        spec.operation = ResourceOperation.RESET.operation;
        spec.name = ResourceOperation.RESET.displayName;
        spec.description = ResourceOperation.RESET.description;
        spec.targetCriteria = ResourceOperationUtils.TargetCriteria.RESOURCE_POWER_STATE_ON.getCriteria();
        return spec;
    }

    private void updateComputeState(DefaultAdapterContext c) {
        ComputeState state = new ComputeState();
        state.powerState = ComputeService.PowerState.ON;
        Operation.createPatch(c.resourceReference)
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
