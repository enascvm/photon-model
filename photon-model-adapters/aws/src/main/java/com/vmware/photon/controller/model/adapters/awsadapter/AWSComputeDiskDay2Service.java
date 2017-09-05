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

package com.vmware.photon.controller.model.adapters.awsadapter;

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_EBS_DEVICE_NAMES;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_INSTANCE_ID_PREFIX;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_VOLUME_ID_PREFIX;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.DEVICE_NAME;

import java.util.ArrayList;
import java.util.List;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.AttachVolumeRequest;
import com.amazonaws.services.ec2.model.AttachVolumeResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceBlockDeviceMapping;
import com.amazonaws.services.ec2.model.Reservation;

import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSAsyncHandler;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperation;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationRequest;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationUtils;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext.BaseAdapterStage;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext.DefaultAdapterContext;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * This service is responsible for attaching and detaching a disk to a vm provisioned on aws.
 */
public class AWSComputeDiskDay2Service extends StatelessService {
    public static final String SELF_LINK = AWSUriPaths.AWS_DISK_DAY2_ADAPTER;

    private AWSClientManager clientManager;

    private static class DiskContext {

        protected DefaultAdapterContext baseAdapterContext;
        protected ResourceOperationRequest request;
        protected ComputeState computeState;
        protected DiskState diskState;
        protected AmazonEC2AsyncClient amazonEC2Client;
    }

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
                .registerResourceOperation(this, handler, createResourceOperationSpecs());
        this.clientManager = AWSClientManagerFactory
                .getClientManager(AWSConstants.AwsClientType.EC2);
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }

        ResourceOperationRequest request = op.getBody(ResourceOperationRequest.class);

        try {
            validateRequest(request);
            op.complete();
        } catch (Exception e) {
            op.fail(e);
            return;
        }

        //initialize context with baseAdapterContext and request information
        DeferredResult<DiskContext> drDiskContext = new DefaultAdapterContext(this, request)
                .populateBaseContext(BaseAdapterStage.VMDESC)
                .thenApply(c -> {
                    DiskContext context = new DiskContext();
                    context.baseAdapterContext = c;
                    context.request = request;
                    return context;
                });

        // handle resource operation
        if (request.operation.equals(ResourceOperation.ATTACH_DISK.operation)) {
            drDiskContext.thenCompose(this::setComputeState)
                    .thenCompose(this::setDiskState)
                    .thenCompose(this::setClient)
                    .thenCompose(this::performAttachOperation)
                    .whenComplete((ctx, e) -> {
                        if (e != null) {
                            ctx.baseAdapterContext.taskManager.patchTaskToFailure(e);
                            return;
                        }
                        ctx.baseAdapterContext.taskManager.finishTask();
                    });
        } else if (request.operation.equals(ResourceOperation.DETACH_DISK.operation)) {
            //TODO: detach a disk from its vm
        } else {
            drDiskContext.thenCompose(c -> {
                Throwable err = new IllegalArgumentException(
                        String.format("Unknown Operation %s for a disk", request.operation));
                c.baseAdapterContext.taskManager.patchTaskToFailure(err);
                return DeferredResult.failed(err);
            });
        }
    }

    private void validateRequest(ResourceOperationRequest request) {
        if (request.resourceReference == null) {
            throw new IllegalArgumentException("Compute description cannot be empty");
        }

        if (request.operation == null || request.operation.isEmpty()) {
            throw new IllegalArgumentException("Operation cannot be empty");
        }

        if (request.payload == null
                || request.payload.get(PhotonModelConstants.DISK_LINK) == null) {
            throw new IllegalArgumentException("disk reference cannot be empty");
        }
    }

    /**
     * create and add the amazon client to the context
     */
    private DeferredResult<DiskContext> setClient(DiskContext context) {
        context.amazonEC2Client = this.clientManager
                .getOrCreateEC2Client(context.baseAdapterContext.parentAuth,
                        context.baseAdapterContext.child.description.regionId, this,
                        (t) -> context.baseAdapterContext.taskManager.patchTaskToFailure(t));
        return DeferredResult.completed(context);
    }

    /**
     *  get the compute state and set it in context
     */
    private DeferredResult<DiskContext> setComputeState(DiskContext context) {
        return this.sendWithDeferredResult(
                Operation.createGet(context.baseAdapterContext.resourceReference),
                ComputeState.class)
                .thenApply(computeState -> {
                    context.computeState = computeState;
                    return context;
                });
    }

    /**
     * get the disk state and set it in context
     */
    private DeferredResult<DiskContext> setDiskState(DiskContext context) {
        return this.sendWithDeferredResult(Operation.createGet(this.getHost(),
                context.request.payload.get(PhotonModelConstants.DISK_LINK)), DiskState.class)
                .thenApply(diskState -> {
                    context.diskState = diskState;
                    return context;
                });
    }

    private DeferredResult<DiskContext> performAttachOperation(DiskContext context) {
        DeferredResult<DiskContext> dr = new DeferredResult();

        if (context.request.isMockRequest) {
            updateComputeAndDiskState(dr, context);
            return dr;
        }

        String instanceId = context.computeState.id;
        if (instanceId == null || !instanceId.startsWith(AWS_INSTANCE_ID_PREFIX)) {
            String message = "compute id cannot be empty";
            this.logSevere("[AWSComputeDiskDay2Service] " + message);
            return DeferredResult.failed(new IllegalArgumentException(message));
        }

        String diskId = context.diskState.id;
        if (diskId == null || !diskId.startsWith(AWS_VOLUME_ID_PREFIX)) {
            String message = "disk id cannot be empty";
            this.logSevere("[AWSComputeDiskDay2Service] " + message);
            return DeferredResult.failed(new IllegalArgumentException(message));
        }

        String deviceName = getAvailableDeviceName(context, instanceId);
        if (deviceName == null) {
            String message = "No device name is available for attaching new disk";
            this.logSevere("[AWSComputeDiskDay2Service] " + message);
            return DeferredResult.failed(new IllegalArgumentException(message));
        }

        context.diskState.customProperties.put(DEVICE_NAME, deviceName);

        AttachVolumeRequest attachVolumeRequest = new AttachVolumeRequest()
                .withInstanceId(instanceId)
                .withVolumeId(diskId)
                .withDevice(deviceName);

        AWSAsyncHandler<AttachVolumeRequest, AttachVolumeResult> attachDiskHandler = new AWSAttachDiskHandler(
                this, dr, context);

        context.amazonEC2Client.attachVolumeAsync(attachVolumeRequest, attachDiskHandler);

        return dr;
    }

    /**
     *  Async handler for updating the disk and vm state after attaching the disk to a vm.
     */
    public class AWSAttachDiskHandler
            extends AWSAsyncHandler<AttachVolumeRequest, AttachVolumeResult> {

        private StatelessService service;
        private DeferredResult<DiskContext> dr;
        private DiskContext context;

        private AWSAttachDiskHandler(StatelessService service, DeferredResult<DiskContext> dr,
                DiskContext context) {
            this.service = service;
            this.dr = dr;
            this.context = context;
        }

        @Override
        protected void handleError(Exception exception) {
            this.dr.fail(exception);
        }

        @Override
        protected void handleSuccess(AttachVolumeRequest request, AttachVolumeResult result) {
            updateComputeAndDiskState(this.dr, this.context);
        }
    }

    /**
     * Update photon-model disk state and compute state to reflect the attached disk.
     *
     */
    private void updateComputeAndDiskState(DeferredResult<DiskContext> dr,
            DiskContext context) {
        List<DeferredResult<Operation>> patchDRs = new ArrayList<>();

        patchDRs.add(updateDiskState(context));
        patchDRs.add(updateComputeState(context));

        DeferredResult.allOf(patchDRs)
                .whenComplete((c, e) -> {
                    if (e != null) {
                        String message = String
                                .format("Error updating vm state. %s", Utils.toString(e));
                        this.logSevere(() -> "[AWSComputeDiskDay2Service] " + message);
                        dr.fail(e);
                        return;
                    }
                    String message = String
                            .format("[AWSComputeDiskDay2Service] Successfully attached "
                                            + "volume(%s) to instance(%s):", context.diskState.id,
                                    context.computeState.id);
                    this.logInfo(() -> message);
                    dr.complete(context);
                });
    }

    /**
     * Update status of disk
     */
    private DeferredResult<Operation> updateDiskState(DiskContext context) {
        DiskState diskState = context.diskState;
        diskState.status = DiskService.DiskStatus.ATTACHED;

        Operation diskPatchOp = Operation.createPatch(this.getHost(),
                diskState.documentSelfLink)
                .setBody(diskState)
                .setReferer(this.getUri());

        return this.sendWithDeferredResult(diskPatchOp);
    }

    /**
     * Add diskLink to ComputeState
     */
    private DeferredResult<Operation> updateComputeState(DiskContext context) {
        ComputeState computeState = context.computeState;
        if (computeState.diskLinks == null) {
            computeState.diskLinks = new ArrayList<>();
        }

        computeState.diskLinks.add(context.diskState.documentSelfLink);
        Operation computeStatePatchOp = Operation.createPatch(UriUtils.buildUri(this.getHost(),
                computeState.documentSelfLink))
                .setBody(computeState)
                .setReferer(this.getUri());

        return this.sendWithDeferredResult(computeStatePatchOp);
    }

    private String getAvailableDeviceName(DiskContext context, String instanceId) {
        DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest()
                .withInstanceIds(instanceId);
        DescribeInstancesResult instancesResult = context.amazonEC2Client
                .describeInstances(describeInstancesRequest);

        List<InstanceBlockDeviceMapping> blockDeviceMappings = null;
        for (Reservation reservation : instancesResult.getReservations()) {
            for (Instance instance : reservation.getInstances()) {
                if (instance.getInstanceId().equals(instanceId)) {
                    blockDeviceMappings = instance.getBlockDeviceMappings();
                    break;
                }
            }
        }

        String deviceName = null;
        if (blockDeviceMappings != null) {
            List<String> usedDeviceNames = getUsedDeviceNames(blockDeviceMappings);
            List<String> availableDiskNames = getAvailableDeviceNames(usedDeviceNames,
                    AWS_EBS_DEVICE_NAMES);
            deviceName = availableDiskNames.get(0);
        }
        return deviceName;
    }

    private List<String> getUsedDeviceNames(List<InstanceBlockDeviceMapping> blockDeviceMappings) {
        List<String> usedDeviceNames = new ArrayList<>();
        for (InstanceBlockDeviceMapping blockDeviceMapping : blockDeviceMappings) {
            usedDeviceNames.add(blockDeviceMapping.getDeviceName());
        }
        return usedDeviceNames;
    }

    /**
     *
     * Returns the list of device names that can be used for provisioning additional disks
     */
    private List<String> getAvailableDeviceNames(List<String> usedDeviceNames,
            List<String> supportedDeviceNames) {
        List<String> availableDeviceNames = new ArrayList<>();
        for (String availableDeviceName : supportedDeviceNames) {
            for (String usedDeviceName : usedDeviceNames) {
                if (usedDeviceName.contains(availableDeviceName) ||
                        availableDeviceName.contains(usedDeviceName)) {
                    availableDeviceName = null;
                    break;
                }
            }
            if (availableDeviceName != null) {
                availableDeviceNames.add(availableDeviceName);
            }
        }
        return availableDeviceNames;
    }

    private ResourceOperationSpecService.ResourceOperationSpec[] createResourceOperationSpecs() {
        ResourceOperationSpecService.ResourceOperationSpec attachDiskSpec = createResourceOperationSpec(
                ResourceOperation.ATTACH_DISK);
        ResourceOperationSpecService.ResourceOperationSpec detachDiskSpec = createResourceOperationSpec(
                ResourceOperation.DETACH_DISK);
        return new ResourceOperationSpecService.ResourceOperationSpec[] { attachDiskSpec,
                detachDiskSpec };
    }

    private ResourceOperationSpecService.ResourceOperationSpec createResourceOperationSpec(
            ResourceOperation operationType) {
        ResourceOperationSpecService.ResourceOperationSpec spec = new ResourceOperationSpecService.ResourceOperationSpec();
        spec.adapterReference = AdapterUriUtil.buildAdapterUri(getHost(), SELF_LINK);
        spec.endpointType = PhotonModelConstants.EndpointType.aws.name();
        spec.resourceType = ResourceOperationSpecService.ResourceType.COMPUTE;
        spec.operation = operationType.operation;
        spec.name = operationType.displayName;
        spec.description = operationType.description;
        return spec;
    }
}
