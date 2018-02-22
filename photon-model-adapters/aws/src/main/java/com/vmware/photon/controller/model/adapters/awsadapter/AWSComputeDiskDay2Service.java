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

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_DISK_OPERATION_TIMEOUT_MINUTES;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_INSTANCE_ID_PREFIX;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_VOLUME_ID_PREFIX;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.DEVICE_NAME;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.AttachVolumeRequest;
import com.amazonaws.services.ec2.model.AttachVolumeResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DetachVolumeRequest;
import com.amazonaws.services.ec2.model.DetachVolumeResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceBlockDeviceMapping;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.StartInstancesRequest;
import com.amazonaws.services.ec2.model.StartInstancesResult;
import com.amazonaws.services.ec2.model.StopInstancesRequest;
import com.amazonaws.services.ec2.model.StopInstancesResult;
import com.amazonaws.services.ec2.model.Volume;

import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWSStorageType;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWSSupportedOS;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWSSupportedVirtualizationTypes;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSAsyncHandler;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSBlockDeviceNameMapper;
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
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
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
        public long taskExpirationMicros;

        DiskContext() {
            this.taskExpirationMicros = Utils.getNowMicrosUtc() + TimeUnit.MINUTES.toMicros(
                    AWS_DISK_OPERATION_TIMEOUT_MINUTES);
        }
    }

    @Override
    public void handleStart(Operation startPost) {
        this.clientManager = AWSClientManagerFactory
                .getClientManager(AWSConstants.AwsClientType.EC2);

        Operation.CompletionHandler handler = (op, exc) -> {
            if (exc != null) {
                startPost.fail(exc);
            } else {
                startPost.complete();
            }
        };
        ResourceOperationUtils
                .registerResourceOperation(this, handler, createResourceOperationSpecs());
    }

    @Override
    public void handleStop(Operation op) {
        AWSClientManagerFactory.returnClientManager(this.clientManager,
                AWSConstants.AwsClientType.EC2);

        super.handleStop(op);
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

        Function<DiskContext, DeferredResult<DiskContext>> fn;
        // handle resource operation
        if (request.operation.equals(ResourceOperation.ATTACH_DISK.operation)) {
            fn = this::performAttachOperation;
        } else if (request.operation.equals(ResourceOperation.DETACH_DISK.operation)) {
            fn = this::performDetachOperation;
        } else {
            drDiskContext.thenApply(c -> {
                Throwable err = new IllegalArgumentException(
                        String.format("Unknown Operation %s for a disk", request.operation));
                c.baseAdapterContext.taskManager.patchTaskToFailure(err);
                return c;
            });
            return;
        }

        drDiskContext.thenCompose(this::setComputeState)
                .thenCompose(this::setDiskState)
                .thenCompose(this::setClient)
                .thenCompose(fn)
                .whenComplete((ctx, e) -> {
                    if (e == null) {
                        ctx.baseAdapterContext.taskManager.finishTask();
                    } else {
                        ctx.baseAdapterContext.taskManager.patchTaskToFailure(e);
                    }
                });
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
        DeferredResult<DiskContext> dr = new DeferredResult<>();
        this.clientManager.getOrCreateEC2ClientAsync(context.baseAdapterContext.endpointAuth,
                context.baseAdapterContext.child.description.regionId, this)
                .whenComplete((ec2Client, t) -> {
                    if (t != null) {
                        dr.fail(t);
                        return;
                    }

                    context.amazonEC2Client = ec2Client;
                    dr.complete(context);
                });
        return dr;
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
        DeferredResult<DiskContext> dr = new DeferredResult<>();
        try {
            if (context.request.isMockRequest) {
                updateComputeAndDiskState(dr, context, null);
                return dr;
            }

            String instanceId = context.computeState.id;
            if (instanceId == null || !instanceId.startsWith(AWS_INSTANCE_ID_PREFIX)) {
                return logAndGetFailedDr("compute id cannot be empty");
            }

            String diskId = context.diskState.id;
            if (diskId == null || !diskId.startsWith(AWS_VOLUME_ID_PREFIX)) {
                return logAndGetFailedDr("disk id cannot be empty");
            }

            String deviceName = getAvailableDeviceName(context, instanceId);
            if (deviceName == null) {
                return logAndGetFailedDr("No device name is available for attaching new disk");
            }

            context.diskState.customProperties.put(DEVICE_NAME, deviceName);

            AttachVolumeRequest attachVolumeRequest = new AttachVolumeRequest()
                    .withInstanceId(instanceId)
                    .withVolumeId(diskId)
                    .withDevice(deviceName);

            AWSAsyncHandler<AttachVolumeRequest, AttachVolumeResult> attachDiskHandler = new AWSAttachDiskHandler(
                    dr, context);

            context.amazonEC2Client.attachVolumeAsync(attachVolumeRequest, attachDiskHandler);

        } catch (Exception e) {
            return DeferredResult.failed(e);
        }

        return dr;
    }

    private DeferredResult<DiskContext> logAndGetFailedDr(String message) {
        this.logSevere("[AWSComputeDiskDay2Service] " + message);
        Throwable e = new IllegalArgumentException(message);
        return DeferredResult.failed(e);
    }

    private DeferredResult<DiskContext> performDetachOperation(DiskContext context) {
        DeferredResult<DiskContext> dr = new DeferredResult<>();

        try {
            validateDetachInfo(context.diskState);

            if (context.request.isMockRequest) {
                updateComputeAndDiskState(dr, context, null);
                return dr;
            }

            String instanceId = context.computeState.id;
            if (instanceId == null || !instanceId.startsWith(AWS_INSTANCE_ID_PREFIX)) {
                return logAndGetFailedDr("compute id cannot be empty");
            }

            String diskId = context.diskState.id;
            if (diskId == null || !diskId.startsWith(AWS_VOLUME_ID_PREFIX)) {
                return logAndGetFailedDr("disk id cannot be empty");
            }

            //TODO: Ideally the volume must be unmounted before detaching the disk. Currently
            // we don't have a way to unmount the disk. The solution is to stop the instance,
            // detach the disk and then start the instance

            //stop the instance, detach the disk and then start the instance.
            if (context.baseAdapterContext.child.powerState.equals(ComputeService.PowerState.ON)) {
                StopInstancesRequest stopRequest = new StopInstancesRequest();
                stopRequest.withInstanceIds(context.baseAdapterContext.child.id);
                context.amazonEC2Client.stopInstancesAsync(stopRequest,
                        new AWSAsyncHandler<StopInstancesRequest, StopInstancesResult>() {
                            @Override
                            protected void handleError(Exception e) {
                                service.logSevere(() -> String.format(
                                        "[AWSComputeDiskDay2Service] Failed to start compute. %s",
                                        Utils.toString(e)));
                                OperationContext.restoreOperationContext(this.opContext);
                                dr.fail(e);
                            }

                            @Override
                            protected void handleSuccess(StopInstancesRequest request,
                                    StopInstancesResult result) {
                                OperationContext.restoreOperationContext(this.opContext);
                                AWSUtils.waitForTransitionCompletion(getHost(),
                                        result.getStoppingInstances(), "stopped",
                                        context.amazonEC2Client, (is, e) -> {
                                            if (e != null) {
                                                service.logSevere(() -> String.format(
                                                        "[AWSComputeDiskDay2Service] Failed to stop "
                                                                + "the compute. %s", Utils.toString(e)));
                                                dr.fail(e);
                                                return;
                                            }
                                            logInfo(() -> String.format(
                                                    "[AWSComputeDiskDay2Service] Successfully stopped "
                                                            + "the instance %s", instanceId));
                                            //detach disk from the instance.
                                            detachVolume(context, dr, instanceId, diskId, true);
                                        });
                            }
                        });
            } else {
                detachVolume(context, dr, instanceId, diskId, false);
            }
        } catch (Exception e) {
            return DeferredResult.failed(e);
        }

        return dr;
    }

    /**
     *  Verifies if the disk is in attached to a vm and is not boot disk.
     */
    private void validateDetachInfo(DiskState diskState) {
        if (diskState.bootOrder != null) {
            throw new IllegalArgumentException("Boot disk cannot be detached from the vm");
        }

        //Detached or available disk cannot be detached.
        if (diskState.status != null && diskState.status != DiskService.DiskStatus.ATTACHED) {
            throw new IllegalArgumentException(String.format("Cannot perform detach operation on "
                    + "disk with id %s. The disk has to be in attached state.", diskState.id));
        }
    }

    /**
     * Send detach request to aws using amazon ec2 client.
     */
    private void detachVolume(DiskContext context, DeferredResult<DiskContext> dr,
            String instanceId, String diskId, boolean startInstance) {
        DetachVolumeRequest detachVolumeRequest = new DetachVolumeRequest()
                .withInstanceId(instanceId)
                .withVolumeId(diskId);

        AWSAsyncHandler<DetachVolumeRequest, DetachVolumeResult> detachDiskHandler =
                new AWSDetachDiskHandler(this, dr, context, startInstance);

        context.amazonEC2Client.detachVolumeAsync(detachVolumeRequest, detachDiskHandler);
    }

    /**
     * start the instance and on success updates the disk and compute state to reflect the detach information.
     */
    private void startInstance(AmazonEC2AsyncClient client, DiskContext c, DeferredResult<DiskContext> dr) {
        StartInstancesRequest startRequest  = new StartInstancesRequest();
        startRequest.withInstanceIds(c.baseAdapterContext.child.id);
        client.startInstancesAsync(startRequest,
                new AWSAsyncHandler<StartInstancesRequest, StartInstancesResult>() {

                    @Override
                    protected void handleError(Exception e) {
                        service.logSevere(() -> String.format(
                                "[AWSComputeDiskDay2Service] Failed to start the instance %s. %s",
                                c.baseAdapterContext.child.id, Utils.toString(e)));
                        dr.fail(e);
                    }

                    @Override
                    protected void handleSuccess(StartInstancesRequest request, StartInstancesResult result) {
                        AWSUtils.waitForTransitionCompletion(getHost(),
                                result.getStartingInstances(), "running",
                                client, (is, e) -> {
                                    if (e != null) {
                                        service.logSevere(() -> String.format(
                                                "[AWSComputeDiskDay2Service] Instance %s failed to reach "
                                                        + "running state. %s",c.baseAdapterContext.child.id,
                                                Utils.toString(e)));
                                        dr.fail(e);
                                        return;
                                    }

                                    logInfo(() -> String.format(
                                            "[AWSComputeDiskDay2Service] Successfully started the "
                                                    + "instance %s",
                                            result.getStartingInstances().get(0).getInstanceId()));
                                    updateComputeAndDiskState(dr, c, this.opContext);
                                });
                    }
                });
    }

    /**
     *  Async handler for updating the disk and vm state after attaching the disk to a vm.
     */
    public class AWSDetachDiskHandler
            extends AWSAsyncHandler<DetachVolumeRequest, DetachVolumeResult> {

        private StatelessService service;
        private DeferredResult<DiskContext> dr;
        private DiskContext context;
        Boolean performNextInstanceOp;

        private AWSDetachDiskHandler(StatelessService service, DeferredResult<DiskContext> dr,
                DiskContext context, Boolean performNextInstanceOp) {
            this.opContext = OperationContext.getOperationContext();
            this.service = service;
            this.dr = dr;
            this.context = context;
            this.performNextInstanceOp = performNextInstanceOp;
        }

        @Override
        protected void handleError(Exception exception) {
            OperationContext.restoreOperationContext(this.opContext);
            this.dr.fail(exception);
        }

        @Override
        protected void handleSuccess(DetachVolumeRequest request, DetachVolumeResult result) {

            //consumer to be invoked once a volume is detached
            Consumer<Object> consumer = volume -> {
                this.service.logInfo(
                        () -> String.format("[AWSComputeDiskDay2Service] Successfully detached "
                                        + "the volume %s from instance %s for task reference :%s",
                                this.context.diskState.documentSelfLink,
                                this.context.computeState.documentSelfLink,
                                this.context.request.taskLink()));
                if (this.performNextInstanceOp) {
                    //Instance will be started only if the disk is succesfully detached from the instance
                    startInstance(this.context.amazonEC2Client, this.context, this.dr);
                } else {
                    updateComputeAndDiskState(this.dr, this.context, this.opContext);
                }
            };

            String volumeId = this.context.diskState.id;
            startStatusChecker(this.context, volumeId, AWSTaskStatusChecker.AWS_AVAILABLE_NAME,
                    consumer);
        }
    }

    private void startStatusChecker(DiskContext context, String volumeId, String status, Consumer<Object> consumer) {

        Runnable proceed = () -> {
            AWSTaskStatusChecker
                    .create(context.amazonEC2Client, volumeId, status, consumer, context.baseAdapterContext.taskManager,
                            this, context.taskExpirationMicros)
                    .start(new Volume());
        };

        proceed.run();
    }

    /**
     *  Async handler for updating the disk and vm state after attaching the disk to a vm.
     */
    public class AWSAttachDiskHandler
            extends AWSAsyncHandler<AttachVolumeRequest, AttachVolumeResult> {

        private DeferredResult<DiskContext> dr;
        private DiskContext context;

        private AWSAttachDiskHandler(DeferredResult<DiskContext> dr, DiskContext context) {
            this.opContext = OperationContext.getOperationContext();
            this.dr = dr;
            this.context = context;
        }

        @Override
        protected void handleError(Exception exception) {
            this.dr.fail(exception);
        }

        @Override
        protected void handleSuccess(AttachVolumeRequest request, AttachVolumeResult result) {
            updateComputeAndDiskState(this.dr, this.context, this.opContext);
        }
    }

    /**
     * Update photon-model disk state and compute state to reflect the attached/detached disk.
     *
     */
    private void updateComputeAndDiskState(DeferredResult<DiskContext> dr,
            DiskContext context, OperationContext opCtx) {
        if (opCtx != null) {
            OperationContext.restoreOperationContext(opCtx);
        }

        List<DeferredResult<Operation>> patchDRs = new ArrayList<>();

        patchDRs.add(updateDiskState(context));
        patchDRs.add(updateComputeState(context));

        DeferredResult.allOf(patchDRs)
                .whenComplete((c, e) -> {
                    if (e != null) {
                        this.logSevere(() -> String.format(
                                "[AWSComputeDiskDay2Service] Updating computeState and "
                                        + "diskState for %s failed. %s",
                                context.request.operation, Utils.toString(e)));
                        dr.fail(e);
                        return;
                    }
                    this.logInfo(() -> String
                            .format("[AWSComputeDiskDay2Service] Updating DiskState %s and "
                                            + "ComputeState %s for %s : SUCCESS",
                                    context.diskState.documentSelfLink,
                                    context.computeState.documentSelfLink,
                                    context.request.operation));
                    dr.complete(context);
                });
    }

    /**
     * Update attach status of disk
     */
    private DeferredResult<Operation> updateDiskState(DiskContext context) {
        DiskState diskState = context.diskState;
        Operation diskOp = null;

        if (context.request.operation.equals(ResourceOperation.ATTACH_DISK.operation)) {
            diskState.status = DiskService.DiskStatus.ATTACHED;
            diskOp = Operation.createPatch(this.getHost(),
                    diskState.documentSelfLink)
                    .setBody(diskState)
                    .setReferer(this.getUri());
        } else if (context.request.operation.equals(ResourceOperation.DETACH_DISK.operation)) {
            diskState.status = DiskService.DiskStatus.AVAILABLE;
            diskState.customProperties.remove(DEVICE_NAME);
            diskOp = Operation.createPut(UriUtils.buildUri(this.getHost(), diskState
                    .documentSelfLink))
                    .setBody(diskState)
                    .setReferer(this.getUri());
        }

        return this.sendWithDeferredResult(diskOp);
    }

    /**
     * Add/remove diskLink to/from ComputeState
     */
    private DeferredResult<Operation> updateComputeState(DiskContext context) {
        ComputeState computeState = context.computeState;
        Operation computeStateOp = null;

        if (context.request.operation.equals(ResourceOperation.ATTACH_DISK.operation)) {
            if (computeState.diskLinks == null) {
                computeState.diskLinks = new ArrayList<>();
            }

            computeState.diskLinks.add(context.diskState.documentSelfLink);
            computeStateOp = Operation.createPatch(UriUtils.buildUri(this.getHost(),
                    computeState.documentSelfLink))
                    .setBody(computeState)
                    .setReferer(this.getUri());

        } else if (context.request.operation.equals(ResourceOperation.DETACH_DISK.operation)) {
            computeState.diskLinks.remove(context.diskState.documentSelfLink);
            computeStateOp = Operation.createPut(UriUtils.buildUri(this.getHost(),
                    computeState.documentSelfLink))
                    .setBody(computeState)
                    .setReferer(this.getUri());
        }
        return this.sendWithDeferredResult(computeStateOp);
    }

    private String getAvailableDeviceName(DiskContext context, String instanceId) {
        DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest()
                .withInstanceIds(instanceId);
        DescribeInstancesResult instancesResult = context.amazonEC2Client
                .describeInstances(describeInstancesRequest);

        List<InstanceBlockDeviceMapping> blockDeviceMappings = null;
        AWSSupportedOS platform = null;
        AWSSupportedVirtualizationTypes virtualizationTypes = null;
        String instanceType = null;
        for (Reservation reservation : instancesResult.getReservations()) {
            for (Instance instance : reservation.getInstances()) {
                if (instance.getInstanceId().equals(instanceId)) {
                    blockDeviceMappings = instance.getBlockDeviceMappings();
                    platform = AWSSupportedOS.get(instance.getPlatform());
                    virtualizationTypes = AWSSupportedVirtualizationTypes.get(instance.getVirtualizationType());
                    instanceType = instance.getInstanceType();
                    break;
                }
            }
        }

        String deviceName = null;
        if (blockDeviceMappings != null) {
            List<String> usedDeviceNames = getUsedDeviceNames(blockDeviceMappings);
            List<String> availableDiskNames = AWSBlockDeviceNameMapper.getAvailableNames(
                    platform, virtualizationTypes, AWSStorageType.EBS, instanceType, usedDeviceNames);
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
