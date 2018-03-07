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

import static com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationUtils.handleAdapterResourceOperationRegistration;

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
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceOperationSpec;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceType;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationUtils;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext.BaseAdapterStage;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext.DefaultAdapterContext;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;

public class AWSResetService extends StatelessService {
    public static final String SELF_LINK = AWSUriPaths.AWS_RESET_ADAPTER;

    private AWSClientManager clientManager;
    private boolean registerResourceOperation;

    public static class AWSResetServiceFactoryService extends FactoryService {

        private boolean registerResourceOperation;

        public AWSResetServiceFactoryService(boolean registerResourceOperation) {
            super(ServiceDocument.class);
            this.registerResourceOperation = registerResourceOperation;
        }

        @Override
        public Service createServiceInstance() {
            return new AWSResetService(this.registerResourceOperation);
        }
    }

    public AWSResetService() {
        this(true);
    }

    public AWSResetService(boolean registerResourceOperation) {
        this.registerResourceOperation = registerResourceOperation;
    }

    @Override
    public void handleStart(Operation startPost) {
        this.clientManager = AWSClientManagerFactory
                .getClientManager(AWSConstants.AwsClientType.EC2);

        handleAdapterResourceOperationRegistration(this, startPost,
                this.registerResourceOperation,
                getResourceOperationSpec());
    }


    @Override
    public void handleStop(Operation op) {
        AWSClientManagerFactory.returnClientManager(this.clientManager,
                AWSConstants.AwsClientType.EC2);

        super.handleStop(op);
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
                        this.clientManager.getOrCreateEC2ClientAsync(c.endpointAuth,
                                c.child.description.regionId, this)
                                .whenComplete((client, t) -> {
                                    if (t != null) {
                                        c.taskManager.patchTaskToFailure(t);
                                        return;
                                    }

                                    reset(client, request, c);
                                });
                    });
        }
    }

    public static ResourceOperationSpec getResourceOperationSpec() {
        ResourceOperationSpec spec = new ResourceOperationSpec();
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
