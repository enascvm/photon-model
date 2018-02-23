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

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_DISK_REQUEST_TIMEOUT_MINUTES;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_TAG_NAME;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_VOLUME_ID_PREFIX;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.DEVICE_TYPE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.DISK_IOPS;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.MAX_IOPS_PER_GiB;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.VOLUME_TYPE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.validateSizeSupportedByVolumeType;
import static com.vmware.photon.controller.model.util.PhotonModelUriUtils.createInventoryUri;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.AvailabilityZone;
import com.amazonaws.services.ec2.model.CreateVolumeRequest;
import com.amazonaws.services.ec2.model.CreateVolumeResult;
import com.amazonaws.services.ec2.model.DeleteVolumeRequest;
import com.amazonaws.services.ec2.model.DeleteVolumeResult;
import com.amazonaws.services.ec2.model.Volume;

import com.vmware.photon.controller.model.adapterapi.DiskInstanceRequest;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSAsyncHandler;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AwsDiskClient;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Adapter to create a new ebs volume.
 */
public class AWSDiskService extends StatelessService {

    public static final String SELF_LINK = AWSUriPaths.AWS_DISK_ADAPTER;

    private AWSClientManager clientManager;

    /**
     * Stages for disk provisioning.
     */
    private enum AwsDiskStage {
        DISK_STATE,
        CREDENTIALS,
        CLIENT,
        CREATE,
        DELETE,
        FINISHED,
        FAILED
    }

    /**
     * Extend default 'start' logic with loading AWS client.
     */
    @Override
    public void handleStart(Operation op) {

        this.clientManager = AWSClientManagerFactory
                .getClientManager(AWSConstants.AwsClientType.EC2);

        super.handleStart(op);
    }

    /**
     * Context to manage disk.
     */
    private static class AWSDiskContext {

        public final DiskInstanceRequest diskRequest;
        public AuthCredentialsServiceState credentials;
        public DiskState disk;
        public AwsDiskStage stage;
        public AwsDiskClient client;
        public TaskManager taskManager;
        public Operation operation;
        public long taskExpirationMicros;
        public Throwable error;

        AWSDiskContext(StatelessService service, DiskInstanceRequest diskRequest) {
            this.diskRequest = diskRequest;
            this.taskManager = new TaskManager(service, diskRequest.taskReference,
                    diskRequest.resourceLink());
            this.taskExpirationMicros = Utils.getNowMicrosUtc() + TimeUnit.MINUTES.toMicros(
                    AWS_DISK_REQUEST_TIMEOUT_MINUTES);
        }
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

        DiskInstanceRequest request = op.getBody(DiskInstanceRequest.class);

        if (request.resourceReference == null) {
            op.fail(new IllegalArgumentException("Disk description cannot be empty"));
            return;
        }

        //initialize context
        AWSDiskContext ctx = new AWSDiskContext(this, request);

        op.complete();

        handleStages(ctx, AwsDiskStage.DISK_STATE);
    }

    private void handleStages(AWSDiskContext context, Throwable exc) {
        context.error = exc;
        handleStages(context, AwsDiskStage.FAILED);
    }

    private void handleStages(AWSDiskContext context, AwsDiskStage next) {
        context.stage = next;
        handleStages(context);
    }

    private void handleStages(AWSDiskContext context) {
        try {
            switch (context.stage) {
            case DISK_STATE:
                getDiskState(context, AwsDiskStage.CREDENTIALS);
                break;
            case CREDENTIALS:
                getCredentials(context, AwsDiskStage.CLIENT);
                break;
            case CLIENT:
                BiConsumer<AmazonEC2AsyncClient, Throwable> getEc2Handler = (ec2Client, t) -> {
                    if (t != null) {
                        context.stage = AwsDiskStage.FAILED;
                        context.error = t;
                        handleStages(context);
                        return;
                    }

                    context.client = new AwsDiskClient(ec2Client);
                    switch (context.diskRequest.requestType) {
                    case CREATE:
                        context.stage = AwsDiskStage.CREATE;
                        break;
                    case DELETE:
                        context.stage = AwsDiskStage.DELETE;
                        break;
                    default:
                        break;
                    }

                    handleStages(context);
                };

                this.clientManager.getOrCreateEC2ClientAsync(context.credentials,
                        context.disk.regionId, this).whenComplete(getEc2Handler);
                break;
            case CREATE:
                createDisk(context);
                break;
            case DELETE:
                deleteDisk(context);
                break;
            case FINISHED:
                context.taskManager.finishTask();
                break;
            case FAILED:
                context.taskManager.patchTaskToFailure(context.error);
                break;
            default:
                context.error = new IllegalArgumentException(
                        "Unknown AWS Disk Provisioning stage: " + context.diskRequest.requestType);
                handleStages(context);
                break;
            }
        } catch (Throwable error) {
            // Same as FAILED stage
            context.taskManager.patchTaskToFailure(error);
        }
    }

    /**
     * Deletes the diskstate and the corresponding volume on aws.
     */
    private void deleteDisk(AWSDiskContext context) {
        if (context.diskRequest.isMockRequest) {
            deleteDiskState(context, AwsDiskStage.FINISHED);
            return;
        }

        DiskState diskState = context.disk;
        String diskId = diskState.id;
        if (diskId == null || !diskId.startsWith(AWS_VOLUME_ID_PREFIX)) {
            String message = "disk Id cannot be empty";
            this.logSevere("[AWSDiskService] " + message);
            throw new IllegalArgumentException(message);
        }

        if (diskState.status != DiskService.DiskStatus.AVAILABLE) {
            String message = String.format("disk cannot be deleted. Current status is %s",
                    diskState.status.name());
            this.logSevere("[AWSDiskService] " + message);
            throw new IllegalArgumentException(message);
        }

        AsyncHandler<DeleteVolumeRequest, DeleteVolumeResult> deletionHandler =
                new AWSDiskDeletionHandler(this, context);

        DeleteVolumeRequest deleteVolumeRequest = new DeleteVolumeRequest()
                .withVolumeId(diskId);

        context.client.deleteVolume(deleteVolumeRequest, deletionHandler);
    }

    /**
     * Create a volume on aws that represents the requested disk.
     */
    private void createDisk(AWSDiskContext context) {

        if (context.diskRequest.isMockRequest) {
            Volume vol = getMockVolume();
            updateDiskState(vol, context, AwsDiskStage.FINISHED);
            return;
        }

        DiskState diskState = context.disk;

        //add endpointLinks
        AdapterUtils.addToEndpointLinks(diskState, context.disk.endpointLink);

        if (diskState.capacityMBytes <= 0) {
            String message = "Disk size has to be positive";
            this.logWarning(() -> "[AWSDiskService] " + message);
            throw new IllegalArgumentException(message);
        }

        if (diskState.customProperties != null &&
                diskState.customProperties.get(DEVICE_TYPE) != null &&
                diskState.customProperties.get(DEVICE_TYPE).equals(
                AWSConstants.AWSStorageType.INSTANCE_STORE.getName())) {
            String message = "Independent Instance Store disk cannot be created.";
            this.logWarning(() -> "[AWSDiskService] " + message);
            throw new IllegalArgumentException(message);
        }

        CreateVolumeRequest req = new CreateVolumeRequest();

        String zoneId = diskState.zoneId;
        if (zoneId == null) {
            List<AvailabilityZone> availabilityZoneList = context.client.getAvailabilityZones();
            if (availabilityZoneList.isEmpty()) {
                String message = String
                        .format("No zones are available in the region %s:", diskState.regionId);
                this.logSevere(() -> "[AWSDiskService] " + message);
                throw new IllegalArgumentException(message);
            }
            zoneId = availabilityZoneList.get(0).getZoneName();
        }

        //set availability zone
        req.withAvailabilityZone(zoneId);

        //set volume size
        int diskSize = (int) diskState.capacityMBytes / 1024;
        req.withSize(diskSize);

        //set encrypted field
        Boolean encrypted = diskState.encrypted == null ? false : diskState.encrypted;
        req.withEncrypted(encrypted);

        AWSUtils.setEbsDefaultsIfNotSet(diskState, Boolean.TRUE);

        validateSizeSupportedByVolumeType(diskSize, diskState.customProperties.get(VOLUME_TYPE));

        //set volume type
        if (diskState.customProperties.containsKey(VOLUME_TYPE)) {
            req.withVolumeType(diskState.customProperties.get(VOLUME_TYPE));
        }

        //set iops
        String diskIops = diskState.customProperties.get(DISK_IOPS);
        if (diskIops != null && !diskIops.isEmpty()) {
            int iops = Integer.parseInt(diskIops);
            if (iops > diskSize * MAX_IOPS_PER_GiB) {

                String info = String.format("[AWSDiskService] Requested IOPS (%s) exceeds"
                                + " the maximum value supported by %sGiB disk. Continues "
                                + "provisioning the disk with %s iops", iops, diskSize,
                        diskSize * MAX_IOPS_PER_GiB);

                this.logInfo(() -> info);
                iops = diskSize * MAX_IOPS_PER_GiB;
            }
            req.withIops(iops);
        }

        AsyncHandler<CreateVolumeRequest, CreateVolumeResult> creationHandler =
                new AWSDiskCreationHandler(this, context);

        context.client.createVolume(req, creationHandler);
    }

    private Volume getMockVolume() {
        return new Volume()
                .withVolumeId("i-" + UUID.randomUUID())
                .withEncrypted(false)
                .withAvailabilityZone("")
                .withCreateTime(new Date());
    }

    /**
     * Update photon-model disk state with the properties of ebs volume.
     *
     */
    private void updateDiskState(Volume volume, AWSDiskContext context, AwsDiskStage next) {

        DiskState diskState = context.disk;

        diskState.id = volume.getVolumeId();

        if (volume.getCreateTime() != null) {
            diskState.creationTimeMicros = TimeUnit.MILLISECONDS
                    .toMicros(volume.getCreateTime().getTime());
        }

        diskState.status = DiskService.DiskStatus.AVAILABLE;

        diskState.encrypted = volume.getEncrypted();

        // calculate disk name, default to volume-id if 'Name' tag is not present
        if (diskState.name == null) {
            if (volume.getTags() == null) {
                diskState.name = volume.getVolumeId();
            } else {
                diskState.name = volume.getTags().stream()
                        .filter(tag -> tag.getKey().equals(AWS_TAG_NAME))
                        .map(tag -> tag.getValue()).findFirst()
                        .orElse(volume.getVolumeId());
            }
        }

        diskState.zoneId = volume.getAvailabilityZone();

        sendRequest(
                Operation.createPatch(this.getHost(),
                        context.diskRequest.resourceLink())
                        .setBody(diskState)
                        .setCompletion((o, e) -> {
                            if (e != null) {
                                handleStages(context, e);
                                return;
                            }
                            handleStages(context, next);
                        }));
    }

    private void getCredentials(AWSDiskContext context, AwsDiskStage next) {

        sendRequest(Operation
                .createGet(createInventoryUri(this.getHost(), context.disk.authCredentialsLink))
                .setCompletion((o, e) -> {
                    if (e != null) {
                        handleStages(context, e);
                        return;
                    }
                    context.credentials = o.getBody(AuthCredentialsServiceState.class);
                    handleStages(context, next);
                }));
    }

    private void getDiskState(AWSDiskContext context, AwsDiskStage next) {

        sendRequest(Operation.createGet(context.diskRequest.resourceReference)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        handleStages(context, e);
                        return;
                    }
                    context.disk = o.getBody(DiskState.class);
                    handleStages(context, next);
                }));
    }

    /**
     *  Async handler that will be used to handle the response for create volume request.
     */
    public class AWSDiskCreationHandler
            extends AWSAsyncHandler<CreateVolumeRequest, CreateVolumeResult> {

        private StatelessService service;
        private final OperationContext opContext;
        private AWSDiskContext context;

        private AWSDiskCreationHandler(StatelessService service, AWSDiskContext context) {
            this.opContext = OperationContext.getOperationContext();
            this.service = service;
            this.context = context;
        }

        @Override
        protected void handleError(Exception exception) {
            OperationContext.restoreOperationContext(this.opContext);
            this.context.taskManager.patchTaskToFailure(exception);
        }

        @Override
        protected void handleSuccess(CreateVolumeRequest request, CreateVolumeResult result) {
            String message = "[AWSDiskService] Successfully Provisioned volume for task reference:"
                    + this.context.diskRequest.taskReference;

            this.service.logInfo(() -> message);

            //consumer to be invoked once a volume is available
            Consumer<Object> consumer = volume -> {
                OperationContext.restoreOperationContext(this.opContext);
                updateDiskState((Volume) volume, this.context, AwsDiskStage.FINISHED);
            };

            Volume volume = result.getVolume();
            String volumeId = volume.getVolumeId();
            startStatusChecker(this.context, volumeId, AWSTaskStatusChecker.AWS_AVAILABLE_NAME,
                    consumer);
        }
    }

    private void startStatusChecker(AWSDiskContext context, String volumeId, String status, Consumer<Object> consumer) {

        Runnable proceed = () -> {
            AWSTaskStatusChecker
                    .create(context.client.client, volumeId, status, consumer, context.taskManager,
                            this, context.taskExpirationMicros)
                    .start(new Volume());
        };

        proceed.run();
    }

    /**
     *  Async handler that will be used during the delete volume request.
     */
    public class AWSDiskDeletionHandler
            extends AWSAsyncHandler<DeleteVolumeRequest, DeleteVolumeResult> {

        private StatelessService service;
        private final OperationContext opContext;
        private AWSDiskContext context;

        private AWSDiskDeletionHandler(StatelessService service, AWSDiskContext context) {
            this.opContext = OperationContext.getOperationContext();
            this.service = service;
            this.context = context;
        }

        @Override
        protected void handleError(Exception exception) {
            OperationContext.restoreOperationContext(this.opContext);
            handleStages(this.context, AwsDiskStage.FAILED);
        }

        @Override
        protected void handleSuccess(DeleteVolumeRequest request, DeleteVolumeResult result) {

            //consumer to be invoked once a volume is deleted
            Consumer<Object> consumer1 = volume -> {
                OperationContext.restoreOperationContext(this.opContext);
                String message =
                        "[AWSDiskService] Successfully deleted the volume from aws for task reference:"
                                + this.context.diskRequest.taskReference;
                this.service.logInfo(() -> message);
                deleteDiskState(this.context, AwsDiskStage.FINISHED);
            };

            //consumer to be invoked once a volume is in deleting stage.
            Consumer<Object> consumer = volume -> {
                OperationContext.restoreOperationContext(this.opContext);
                if (volume == null) {
                    consumer1.accept(null);
                    return;
                }
                String message =
                        "[AWSDiskService] aws volume is in deleting state. Task reference:"
                                + this.context.diskRequest.taskReference;
                this.service.logInfo(() -> message);
                startStatusChecker(this.context, ((Volume) volume).getVolumeId(),
                        AWSTaskStatusChecker.AWS_DELETED_NAME, consumer1);
            };

            String volumeId = this.context.disk.id;
            startStatusChecker(this.context, volumeId, AWSTaskStatusChecker.AWS_DELETING_NAME,
                    consumer);
        }
    }

    /**
     * Finish the disk delete operation by cleaning up the disk reference in the system.
     */
    private void deleteDiskState(AWSDiskContext ctx, AwsDiskStage next) {
        List<Operation> ops = new ArrayList<>();

        Operation op1 = Operation.createDelete(this, ctx.disk.documentSelfLink);
        ops.add(op1);

        // Clean up disk description link if it is present.
        if (ctx.disk.customProperties != null && !ctx.disk.customProperties.isEmpty()) {
            String diskDescLink = ctx.disk.customProperties.get(PhotonModelConstants.TEMPLATE_DISK_LINK);
            if (diskDescLink != null) {
                Operation op2 = Operation.createDelete(this, diskDescLink);
                ops.add(op2);
            }
        }
        OperationJoin.create(ops)
                .setCompletion((o, e) -> {
                    if (e != null && !e.isEmpty()) {
                        handleStages(ctx, new Throwable(Utils.toString(e)));
                        return;
                    }
                    handleStages(ctx, next);
                })
                .sendWith(this);
    }

}
