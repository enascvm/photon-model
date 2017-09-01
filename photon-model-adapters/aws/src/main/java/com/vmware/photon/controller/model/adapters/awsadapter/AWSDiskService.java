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

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_TAG_NAME;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.DISK_IOPS;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.MAX_IOPS_PER_GiB;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.VOLUME_TYPE;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.services.ec2.model.AvailabilityZone;
import com.amazonaws.services.ec2.model.CreateVolumeRequest;
import com.amazonaws.services.ec2.model.CreateVolumeResult;
import com.amazonaws.services.ec2.model.Volume;

import com.vmware.photon.controller.model.adapterapi.DiskInstanceRequest;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSAsyncHandler;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AwsDiskClient;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
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
        FINISHED,
        FAILED
    }

    public AWSDiskService() {
        this.clientManager = AWSClientManagerFactory
                .getClientManager(AWSConstants.AwsClientType.EC2);
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
        public Throwable error;

        AWSDiskContext(StatelessService service, DiskInstanceRequest diskRequest) {
            this.diskRequest = diskRequest;
            this.taskManager = new TaskManager(service, diskRequest.taskReference,
                    diskRequest.resourceLink());
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

        if (ctx.diskRequest.isMockRequest) {
            ctx.taskManager.finishTask();
            return;
        }

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
                context.client = new AwsDiskClient(this.clientManager.getOrCreateEC2Client(
                        context.credentials, context.disk.regionId, this, (t) -> {
                            context.stage = AwsDiskStage.FAILED;
                            context.error = t;
                        }
                ));
                if (context.error != null) {
                    handleStages(context);
                    return;
                }
                if (context.diskRequest.requestType == DiskInstanceRequest.DiskRequestType.CREATE) {
                    context.stage = AwsDiskStage.CREATE;
                }

                handleStages(context);
                break;
            case CREATE:
                createDisk(context);
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
     * Create a volume on aws that represents the requested disk.
     */
    private void createDisk(AWSDiskContext context) {

        DiskState diskState = context.disk;

        if (diskState.capacityMBytes <= 0) {
            String message = "Disk size has to be positive";
            this.logWarning(() -> "[AWSDiskService] " + message);
            throw new IllegalArgumentException(message);
        }

        CreateVolumeRequest req = new CreateVolumeRequest();

        //set availability zone
        List<AvailabilityZone> availabilityZoneList = context.client.getAvailabilityZones();
        if (availabilityZoneList.isEmpty()) {
            String message = String.format("No zones are available in the region %s:", diskState.regionId);
            this.logSevere(() -> "[AWSDiskService] " + message);
            throw new IllegalArgumentException(message);
        }

        req.withAvailabilityZone(availabilityZoneList.get(0).getZoneName());

        //set volume size
        int diskSize = (int) diskState.capacityMBytes / 1024;
        req.withSize(diskSize);

        //enable encryption on disk
        Boolean encrypted = diskState.encrypted == null ? false : diskState.encrypted;
        req.withEncrypted(encrypted);

        if (diskState.customProperties != null) {

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
        }

        AsyncHandler<CreateVolumeRequest, CreateVolumeResult> creationHandler =
                new AWSDiskCreationHandler(this, context);

        context.client.createVolume(req, creationHandler);

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

        sendRequest(Operation.createGet(this.getHost(), context.disk.authCredentialsLink)
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
        private AWSDiskContext context;

        private AWSDiskCreationHandler(StatelessService service, AWSDiskContext context) {
            this.service = service;
            this.context = context;
        }

        @Override
        protected void handleError(Exception exception) {
            this.context.taskManager.patchTaskToFailure(exception);
        }

        @Override
        protected void handleSuccess(CreateVolumeRequest request, CreateVolumeResult result) {
            String message = "[AWSDiskService] Successfully Provisioned volume for task reference:"
                    + this.context.diskRequest.taskReference;

            this.service.logInfo(() -> message);

            updateDiskState(result.getVolume(), this.context,
                    AwsDiskStage.FINISHED);

        }
    }
}
