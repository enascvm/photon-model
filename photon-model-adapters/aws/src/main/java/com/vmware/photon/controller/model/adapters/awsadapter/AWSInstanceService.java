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

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWSStorageType;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWSSupportedOS;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWSSupportedVirtualizationTypes;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_DEPENDENCY_VIOLATION_ERROR_CODE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_INSTANCE_ID_PREFIX;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_INVALID_INSTANCE_ID_ERROR_CODE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_TAG_NAME;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_VIRTUAL_NAMES;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.DEVICE_NAME;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.DEVICE_TYPE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.DISK_IOPS;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.MAX_IOPS_PER_GiB;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.VOLUME_TYPE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.VOLUME_TYPE_PROVISIONED_SSD;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.setDefaultVolumeTypeIfNotSet;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.validateSizeSupportedByVolumeType;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.CLOUD_CONFIG_DEFAULT_FILE_INDEX;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.CUSTOM_PROP_SSH_KEY_NAME;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.SOURCE_TASK_LINK;
import static com.vmware.xenon.common.Operation.STATUS_CODE_UNAUTHORIZED;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.AmazonEC2Exception;
import com.amazonaws.services.ec2.model.AttachVolumeRequest;
import com.amazonaws.services.ec2.model.AttachVolumeResult;
import com.amazonaws.services.ec2.model.BlockDeviceMapping;
import com.amazonaws.services.ec2.model.DeleteSubnetRequest;
import com.amazonaws.services.ec2.model.DeleteSubnetResult;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesRequest;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult;
import com.amazonaws.services.ec2.model.DescribeImagesRequest;
import com.amazonaws.services.ec2.model.DescribeImagesResult;
import com.amazonaws.services.ec2.model.EbsBlockDevice;
import com.amazonaws.services.ec2.model.Image;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceBlockDeviceMapping;
import com.amazonaws.services.ec2.model.InstanceNetworkInterface;
import com.amazonaws.services.ec2.model.Placement;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.TerminateInstancesResult;

import org.apache.commons.lang3.EnumUtils;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.Constraint.Condition;
import com.vmware.photon.controller.model.Constraint.Condition.Enforcement;
import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest.InstanceRequestType;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSInstanceContext.AWSNicContext;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSAsyncHandler;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSBlockDeviceNameMapper;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSDeferredResultAsyncHandler;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext.BaseAdapterStage;
import com.vmware.photon.controller.model.adapters.util.Pair;
import com.vmware.photon.controller.model.query.QueryUtils.QueryTop;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.LifecycleState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.photon.controller.model.support.InstanceTypeList.InstanceType;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState;
import com.vmware.photon.controller.model.util.AssertUtil;
import com.vmware.photon.controller.model.util.ClusterUtil.ServiceTypeCluster;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;

/**
 * Adapter to create an EC2 instance on AWS.
 */
public class AWSInstanceService extends StatelessService {

    public static final String SELF_LINK = AWSUriPaths.AWS_INSTANCE_ADAPTER;

    private AWSClientManager clientManager;

    /**
     * Extend default 'start' logic with loading AWS client.
     */
    @Override
    public void handleStart(Operation op) {

        this.clientManager = AWSClientManagerFactory
                .getClientManager(AWSConstants.AwsClientType.EC2);

        super.handleStart(op);
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

        AWSInstanceContext ctx = new AWSInstanceContext(this,
                op.getBody(ComputeInstanceRequest.class));

        try {
            final BaseAdapterStage startingStage;
            final AWSInstanceStage nextStage;

            switch (ctx.computeRequest.requestType) {
            case VALIDATE_CREDENTIALS:
                ctx.operation = op;
                startingStage = BaseAdapterStage.PARENTAUTH;
                nextStage = AWSInstanceStage.CLIENT;
                break;
            default:
                op.complete();
                if (ctx.computeRequest.isMockRequest
                        && ctx.computeRequest.requestType == InstanceRequestType.CREATE) {
                    ctx.taskManager.finishTask();
                    return;
                }
                startingStage = BaseAdapterStage.VMDESC;
                nextStage = AWSInstanceStage.PROVISIONTASK;
                break;
            }

            // Populate BaseAdapterContext and then continue with this state machine
            ctx.populateBaseContext(startingStage).whenComplete(thenAllocation(ctx, nextStage));

        } catch (Throwable t) {
            finishExceptionally(ctx, t);
        }
    }

    /**
     * Shortcut method that sets the next stage into the context and delegates to {@link
     * #handleAllocation(AWSInstanceContext)}.
     */
    private void handleAllocation(AWSInstanceContext context, AWSInstanceStage nextStage) {
        context.stage = nextStage;
        handleAllocation(context);
    }

    /**
     * Shortcut method that stores the error into context, sets next stage to {@link
     * AWSInstanceStage#ERROR} and delegates to {@link #handleAllocation(AWSInstanceContext)}.
     */
    private void handleError(AWSInstanceContext context, Throwable e) {
        context.error = e;
        context.stage = AWSInstanceStage.ERROR;
        handleAllocation(context);
    }

    /**
     * {@code handleAllocation} version suitable for chaining to {@code
     * DeferredResult.whenComplete}.
     */
    private BiConsumer<AWSInstanceContext, Throwable> thenAllocation(AWSInstanceContext context,
            AWSInstanceStage next) {
        // NOTE: In case of error 'ignoreCtx' is null so use passed context!
        return (ignoreCtx, exc) -> {
            if (exc != null) {
                handleError(context, exc);
                return;
            }
            handleAllocation(context, next);
        };
    }

    /**
     * State machine to handle different stages of VM creation/deletion.
     * <p>
     * Method will act much like handlePatch, but without persistence as this is a stateless
     * service. Each call to the service will result in a synchronous execution of the stages below
     * Each stage is responsible for setting the next stage on success -- the next stage is passed
     * into action methods
     * @see #handleError(AWSInstanceContext, Throwable)
     * @see #handleAllocation(AWSInstanceContext, AWSInstanceStage)
     */
    private void handleAllocation(AWSInstanceContext context) {
        logFine(() -> String.format("Transition to: %s", context.stage));
        try {
            switch (context.stage) {
            case PROVISIONTASK:
                getProvisioningTaskReference(context, AWSInstanceStage.CLIENT);
                break;
            case CLIENT:
                Consumer<Throwable> c = t -> {
                    if (context.computeRequest.requestType
                            == InstanceRequestType.VALIDATE_CREDENTIALS) {
                        context.operation.fail(t);
                    } else {
                        context.taskManager.patchTaskToFailure(t);
                    }
                };
                context.amazonEC2Client = this.clientManager
                        .getOrCreateEC2Client(context.endpointAuth,
                                getRequestRegionId(context), this, c);
                if (context.amazonEC2Client == null) {
                    return;
                }
                // now that we have a client lets move onto the next step
                switch (context.computeRequest.requestType) {
                case CREATE:
                    handleAllocation(context, AWSInstanceStage.POPULATE_CONTEXT);
                    break;
                case DELETE:
                    handleAllocation(context, AWSInstanceStage.DELETE);
                    break;
                case VALIDATE_CREDENTIALS:
                    validateAWSCredentials(context);
                    break;
                default:
                    handleError(context,
                            new IllegalStateException("Unknown AWS provisioning stage: "
                                    + context.computeRequest.requestType));
                }
                break;
            case DELETE:
                deleteInstance(context);
                break;
            case POPULATE_CONTEXT:
                context.populateContext()
                        .whenComplete(thenAllocation(context, AWSInstanceStage.CREATE));
                break;
            case CREATE:
                createInstance(context);
                break;
            case ERROR:
                finishExceptionally(context);
                break;
            default:
                handleError(context,
                        new IllegalStateException("Unknown AWS context stage: " + context.stage));
                break;
            }
        } catch (Throwable e) {
            // NOTE: Do not use handleError(err) cause that might result in endless recursion.
            finishExceptionally(context, e);
        }
    }

    private void finishExceptionally(AWSInstanceContext context, Throwable failure) {
        context.error = failure;
        finishExceptionally(context);
    }

    private void finishExceptionally(AWSInstanceContext context) {
        String errorMessage = context.error != null ? context.error.getMessage() : "no error set";
        this.logWarning(() -> "[AWSInstanceService] finished exceptionally. " + errorMessage);

        context.taskManager.patchTaskToFailure(context.error);
        if (context.operation != null) {
            context.operation.fail(context.error);
        }
    }

    /*
     * Gets the provisioning task reference for this operation. Sets the task expiration time in the
     * context to be used for bounding the status checks for creation and termination requests.
     */
    private void getProvisioningTaskReference(AWSInstanceContext aws, AWSInstanceStage next) {
        Consumer<Operation> onSuccess = (op) -> {
            ProvisionComputeTaskState provisioningTaskState = op
                    .getBody(ProvisionComputeTaskState.class);
            aws.taskExpirationMicros = provisioningTaskState.documentExpirationTimeMicros;
            handleAllocation(aws, next);
        };
        AdapterUtils.getServiceState(this, aws.computeRequest.taskReference, onSuccess,
                getFailureConsumer(aws));
    }

    private Consumer<Throwable> getFailureConsumer(AWSInstanceContext aws) {
        return (t) -> handleError(aws, t);
    }

    private void createInstance(AWSInstanceContext aws) {
        if (aws.computeRequest.isMockRequest) {
            aws.taskManager.finishTask();
            return;
        }

        final DiskState bootDisk = aws.bootDisk;
        if (bootDisk == null) {
            aws.taskManager
                    .patchTaskToFailure(new IllegalStateException("AWS bootDisk not specified"));
            return;
        }

        if (bootDisk.bootConfig != null && bootDisk.bootConfig.files.length > 1) {
            aws.taskManager.patchTaskToFailure(
                    new IllegalStateException("Only 1 configuration file allowed"));
            return;
        }

        // This a single disk state with a bootConfig. There's no expectation
        // that it does exists, but if it does, we only support cloud configs at
        // this point.
        String cloudConfig = null;
        if (bootDisk.bootConfig != null
                && bootDisk.bootConfig.files.length > CLOUD_CONFIG_DEFAULT_FILE_INDEX) {
            cloudConfig = bootDisk.bootConfig.files[CLOUD_CONFIG_DEFAULT_FILE_INDEX].contents;
        }

        String instanceType = aws.child.description.instanceType;
        if (instanceType == null) { // fallback to legacy usage of name
            instanceType = aws.child.description.name;
        }
        if (instanceType == null) {
            aws.error = new IllegalStateException("AWS Instance type not specified");
            aws.stage = AWSInstanceStage.ERROR;
            handleAllocation(aws);
            return;
        }

        RunInstancesRequest runInstancesRequest = new RunInstancesRequest()
                .withImageId(aws.bootDiskImageNativeId)
                .withInstanceType(instanceType)
                .withMinCount(1)
                .withMaxCount(1)
                .withMonitoring(true);

        if (aws.placement != null) {
            runInstancesRequest.withPlacement(new Placement(aws.placement));
        }

        if (aws.child.customProperties != null &&
                aws.child.customProperties.containsKey(CUSTOM_PROP_SSH_KEY_NAME)) {
            runInstancesRequest = runInstancesRequest.withKeyName(aws.child.customProperties
                    .get(CUSTOM_PROP_SSH_KEY_NAME));
        }

        if (!aws.dataDisks.isEmpty() || bootDisk.capacityMBytes > 0 ||
                bootDisk.customProperties != null) {
            DescribeImagesRequest imagesDescriptionRequest = new DescribeImagesRequest();
            imagesDescriptionRequest.withImageIds(aws.bootDiskImageNativeId);
            DescribeImagesResult imagesDescriptionResult =
                    aws.amazonEC2Client.describeImages(imagesDescriptionRequest);

            if (imagesDescriptionResult.getImages().size() != 1) {
                handleError(aws, new IllegalStateException("AWS ImageId is not available"));
                return;
            }

            Image image = imagesDescriptionResult.getImages().get(0);

            AssertUtil.assertNotNull(aws.instanceTypeInfo, "instanceType cannot be null");

            List<BlockDeviceMapping> blockDeviceMappings = image.getBlockDeviceMappings();
            String rootDeviceType = image.getRootDeviceType();

            String bootDiskType = bootDisk.customProperties.get(DEVICE_TYPE);
            boolean hasHardConstraint = containsHardConstraint(bootDisk);
            BlockDeviceMapping rootDeviceMapping = null;
            try {
                // The number of instance-store disks that will be provisioned is limited by the instance-type.
                suppressExcessInstanceStoreDevices(blockDeviceMappings, aws.instanceTypeInfo);

                for (BlockDeviceMapping blockDeviceMapping : blockDeviceMappings) {
                    EbsBlockDevice ebs = blockDeviceMapping.getEbs();
                    String diskType = getDeviceType(ebs);

                    if (hasHardConstraint) {
                        validateIfDeviceTypesAreMatching(diskType, bootDiskType);
                    }

                    if (blockDeviceMapping.getNoDevice() != null) {
                        continue;
                    }

                    if (rootDeviceType.equals(AWSStorageType.EBS.getName()) &&
                            blockDeviceMapping.getDeviceName().equals(image.getRootDeviceName())) {
                        rootDeviceMapping = blockDeviceMapping;
                        continue;
                    }

                    DiskState diskState = new DiskState();

                    copyCustomProperties(diskState, bootDisk);

                    addMandatoryProperties(diskState, blockDeviceMapping, aws.instanceTypeInfo);

                    updateDeviceMapping(diskType, bootDiskType, blockDeviceMapping.getDeviceName(),
                            ebs,
                            diskState);

                    //update disk state with final volume-type and iops
                    if (diskType.equals(AWSStorageType.EBS.getName())) {
                        diskState.customProperties.put(VOLUME_TYPE, ebs.getVolumeType());
                        diskState.customProperties.put(DISK_IOPS, String.valueOf(ebs.getIops()));
                    }

                    aws.imageDisks.add(diskState);
                }

                customizeBootDiskProperties(bootDisk, rootDeviceType, rootDeviceMapping,
                        hasHardConstraint, aws);

                List<DiskState> ebsDisks = new ArrayList<>();
                List<DiskState> instanceStoreDisks = new ArrayList<>();
                if (!aws.dataDisks.isEmpty()) {
                    if (!rootDeviceType.equals(AWSStorageType.EBS.name().toLowerCase())) {
                        instanceStoreDisks = aws.dataDisks;
                        validateSupportForAdditionalInstanceStoreDisks(instanceStoreDisks,
                                blockDeviceMappings, aws.instanceTypeInfo, rootDeviceType);
                    } else {
                        splitDataDisks(aws.dataDisks, instanceStoreDisks, ebsDisks);
                        setDefaultVolumeTypeIfNotSpecified(ebsDisks);
                        if (!instanceStoreDisks.isEmpty()) {
                            validateSupportForAdditionalInstanceStoreDisks(instanceStoreDisks,
                                    blockDeviceMappings, aws.instanceTypeInfo, rootDeviceType);
                        }
                    }
                }

                //get the available attach paths for new disks and external disks
                List<String> usedDeviceNames = null;
                if (!instanceStoreDisks.isEmpty() || !ebsDisks.isEmpty() ||
                        !aws.externalDisks.isEmpty()) {
                    usedDeviceNames = getUsedDeviceNames(blockDeviceMappings);
                }

                if (!instanceStoreDisks.isEmpty()) {
                    List<String> usedVirtualNames = getUsedVirtualNames(blockDeviceMappings);
                    blockDeviceMappings.addAll(createInstanceStoreMappings(instanceStoreDisks,
                            usedDeviceNames, usedVirtualNames, aws.instanceTypeInfo.id,
                            aws.instanceTypeInfo.dataDiskSizeInMB,
                            image.getPlatform(),
                            image.getVirtualizationType()));
                }

                if (!ebsDisks.isEmpty() || !aws.externalDisks.isEmpty()) {
                    aws.availableEbsDiskNames = AWSBlockDeviceNameMapper.getAvailableNames(
                            AWSSupportedOS.get(image.getPlatform()),
                            AWSSupportedVirtualizationTypes.get(image.getVirtualizationType()),
                            AWSStorageType.EBS, instanceType, usedDeviceNames);
                }

                if (!ebsDisks.isEmpty()) {
                    blockDeviceMappings.addAll(createEbsDeviceMappings(ebsDisks,
                            aws.availableEbsDiskNames));
                }

                runInstancesRequest.withBlockDeviceMappings(blockDeviceMappings);
            } catch (Exception e) {
                aws.error = e;
                aws.stage = AWSInstanceStage.ERROR;
                handleAllocation(aws);
                return;
            }
        }

        AWSNicContext primaryNic = aws.getPrimaryNic();
        if (primaryNic != null && primaryNic.nicSpec != null) {
            runInstancesRequest.withNetworkInterfaces(primaryNic.nicSpec);
        } else {
            runInstancesRequest.withSecurityGroupIds(AWSUtils.getOrCreateSecurityGroups(aws, null));
        }

        if (cloudConfig != null) {
            try {
                runInstancesRequest.setUserData(Base64.getEncoder()
                        .encodeToString(cloudConfig.getBytes(Utils.CHARSET)));
            } catch (UnsupportedEncodingException e) {
                handleError(aws, new IllegalStateException("Error encoding user data"));
                return;
            }
        }

        String message = "[AWSInstanceService] Sending run instance request for instance id: "
                + aws.bootDiskImageNativeId
                + ", instance type: " + instanceType
                + ", parent task id: " + aws.computeRequest.taskReference;
        this.logInfo(() -> message);
        // handler invoked once the EC2 runInstancesAsync commands completes
        AsyncHandler<RunInstancesRequest, RunInstancesResult> creationHandler = new AWSCreationHandler(
                this, aws);
        aws.amazonEC2Client.runInstancesAsync(runInstancesRequest, creationHandler);
    }

    /**
     * Instance Type(like r3.large etc) supports a limited number of instance-store disks. All the
     * extra instance-store mappings that are more than those supported by the instance-type are
     * termed as excess. Excess mappings does not result in provisioning a disk.
     * <p>
     * Suppress the excess instance-store mappings in the image by setting NoDevice to 'true'.
     */
    private void suppressExcessInstanceStoreDevices(List<BlockDeviceMapping> deviceMappings,
            InstanceType type) {

        List<BlockDeviceMapping> unsuppressedInstanceStoreMappings =
                getUnsuppressedInstanceStoreMappings(deviceMappings);

        int imageInstanceStoreCount = unsuppressedInstanceStoreMappings != null ?
                unsuppressedInstanceStoreMappings.size() : 0;

        int maxSupported = type.dataDiskMaxCount != null ? type.dataDiskMaxCount : 0;

        if (imageInstanceStoreCount > maxSupported) {
            for (int i = 0; i < imageInstanceStoreCount; i++) {
                if (i >= maxSupported) {
                    unsuppressedInstanceStoreMappings.get(i).setNoDevice("");
                }
            }
        }
    }

    private boolean containsHardConstraint(DiskState bootDisk) {
        boolean hasHardConstraint = false;
        if (bootDisk.constraint != null) {
            List<Condition> bootDiskConditions = bootDisk.constraint.conditions;
            if (bootDiskConditions != null) {
                if (bootDiskConditions.stream()
                        .anyMatch(condition -> condition.enforcement == Enforcement.HARD)) {
                    hasHardConstraint = true;
                }
            }
        }
        return hasHardConstraint;
    }

    /**
     * update the deviceMappings for ebs devices.
     */
    private void updateDeviceMapping(String currentDiskType, String requestedDiskType,
            String deviceName, EbsBlockDevice ebs, DiskState diskState) {
        if (requestedDiskType != null) {
            String ebsType = AWSStorageType.EBS.getName();
            String instanceStoreType = AWSStorageType.INSTANCE_STORE.getName();
            if (requestedDiskType.equals(ebsType) && currentDiskType.equals(ebsType)) {
                updateEbsBlockDeviceMapping(ebs, diskState);
            } else if (requestedDiskType.equals(instanceStoreType) && currentDiskType
                    .equals(instanceStoreType)) {
                String message = String.format("[AWSInstanceService] No customization is applied"
                        + " to image disk at %s", deviceName);
                this.logInfo(message);
            } else {
                String message = String
                        .format("[AWSInstanceService] Image disk at %s is of type %s and cannot be "
                                        + "changed to %s type. Ignoring the request to change the type.",
                                deviceName, currentDiskType, requestedDiskType);
                this.logWarning(message);
            }
        }
        if (ebs != null && ebs.getSnapshotId() != null) {
            ebs.setEncrypted(null);
        }
    }

    private void updateEbsBlockDeviceMapping(EbsBlockDevice ebs, DiskState diskState) {

        String requestedVolumeType = diskState.customProperties.get(VOLUME_TYPE);
        if (requestedVolumeType == null) {
            return;
        }

        String requestedIops = diskState.customProperties.get(DISK_IOPS);
        String currentVolumeType = ebs.getVolumeType();
        if (!requestedVolumeType.equals(currentVolumeType)) {
            if (currentVolumeType.equals(VOLUME_TYPE_PROVISIONED_SSD)) {
                //converting io1 volume to one of {gp2, st1, sc1, magnetic} type.
                ebs.setIops(0);
            } else if (requestedVolumeType.equals(VOLUME_TYPE_PROVISIONED_SSD)) {
                //converting from one of {gp2, st1, sc1, magnetic} type to io1 type.
                int iops = Math.min(ebs.getVolumeSize() * MAX_IOPS_PER_GiB,
                        Integer.parseInt(requestedIops));
                ebs.setIops(iops);
            } else {
                //converting from one of {gp2, st1, sc1, magnetic} type to another one in the same set.
                //No need to set iops for this type of volume conversion.
            }
        } else if (currentVolumeType.equals(VOLUME_TYPE_PROVISIONED_SSD)) {
            //Changing the iops value of the the volume.
            int iops = Math.min(ebs.getIops(), Integer.parseInt(requestedIops));
            ebs.setIops(iops);
        }

        ebs.setVolumeType(requestedVolumeType);
    }

    /**
     * Fail the request in case of hard constraints and device type mismatch.
     */
    private void validateIfDeviceTypesAreMatching(String currentDeviceType,
            String requestedDeviceType) {
        if (requestedDeviceType != null && !currentDeviceType.equals(requestedDeviceType)) {
            String message = String.format("Found hard constraint on existing disk. %s type "
                    + "cannot be changed to %s type.", currentDeviceType, requestedDeviceType);
            this.logSevere("[AWSInstanceService] " + message);
            throw new IllegalArgumentException(message);
        }
    }

    private String getDeviceType(EbsBlockDevice ebs) {
        return ebs != null ? AWSStorageType.EBS.getName() : AWSStorageType.INSTANCE_STORE.getName();
    }

    /**
     * copy the custom properties from the boot disk to the existing disk.
     */
    private void copyCustomProperties(DiskState diskState, DiskState bootDisk) {

        Map<String, String> bootDiskCustomProperties = bootDisk.customProperties;
        Map<String, String> customProperties = new HashMap<>();

        if (bootDiskCustomProperties.containsKey(DEVICE_TYPE)) {
            customProperties.put(DEVICE_TYPE, bootDiskCustomProperties.get(DEVICE_TYPE));
        }

        if (bootDiskCustomProperties.containsKey(VOLUME_TYPE)) {
            customProperties.put(VOLUME_TYPE, bootDiskCustomProperties.get(VOLUME_TYPE));
        }

        if (bootDiskCustomProperties.containsKey(DISK_IOPS)) {
            customProperties.put(DISK_IOPS, bootDiskCustomProperties.get(DISK_IOPS));
        }

        diskState.customProperties = customProperties.size() > 0 ? customProperties : null;
    }

    /**
     * Add the disk information to disk state so that the disk state reflects the volume
     * information
     */
    private void addMandatoryProperties(DiskState diskState, BlockDeviceMapping deviceMapping,
            InstanceType instanceType) {

        if (diskState.customProperties == null) {
            diskState.customProperties = new HashMap<>();
        }
        String deviceName = deviceMapping.getDeviceName();
        diskState.customProperties.put(DEVICE_NAME, deviceName);

        EbsBlockDevice ebs = deviceMapping.getEbs();
        if (ebs != null) {
            diskState.capacityMBytes = ebs.getVolumeSize() * 1024;
            diskState.customProperties.put(DEVICE_TYPE, AWSStorageType.EBS.getName());
        } else {
            diskState.capacityMBytes = instanceType.dataDiskSizeInMB;
            diskState.customProperties.put(DEVICE_TYPE, AWSStorageType.INSTANCE_STORE.getName());
        }
    }

    /**
     * Splits the set of existing disks into instance-store disks and ebs disks
     */
    private void splitDataDisks(List<DiskState> dataDisks, List<DiskState> instanceStoreDisks,
            List<DiskState> ebsDisks) {
        dataDisks.forEach(diskState -> {
            if (diskState.customProperties != null &&
                    diskState.customProperties.get(DEVICE_TYPE) != null &&
                    diskState.customProperties.get(DEVICE_TYPE)
                            .equals(AWSStorageType.INSTANCE_STORE.getName())) {
                instanceStoreDisks.add(diskState);
            } else {
                ebsDisks.add(diskState);
            }
        });
    }

    /**
     * Set gp2 volumes as default for ebs disks.
     */
    private void setDefaultVolumeTypeIfNotSpecified(List<DiskState> dataDisks) {
        for (DiskState diskState : dataDisks) {
            setDefaultVolumeTypeIfNotSet(diskState);
        }
    }



    private class AWSCreationHandler
            extends AWSAsyncHandler<RunInstancesRequest, RunInstancesResult> {

        private StatelessService service;
        private final OperationContext opContext;
        private AWSInstanceContext context;

        private AWSCreationHandler(StatelessService service, AWSInstanceContext context) {
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
        protected void handleSuccess(RunInstancesRequest request, RunInstancesResult result) {

            String message = "[AWSInstanceService] Successfully provisioned AWS instance for "
                    + "task reference: " + this.context.computeRequest.taskReference;
            this.service.logInfo(() -> message);

            // consumer to be invoked once a VM is in the running state
            Consumer<Object> consumer = instance -> {
                OperationContext.restoreOperationContext(this.opContext);

                List<Operation> patchOperations = new ArrayList<>();
                if (instance == null) {
                    this.context.taskManager.patchTaskToFailure(
                            new IllegalStateException("Error getting instance EC2 instance"));
                    return;
                }

                ComputeState cs = new ComputeState();
                cs.id = ((Instance) instance).getInstanceId();
                cs.type = ComputeType.VM_GUEST;
                cs.environmentName = ComputeDescription.ENVIRONMENT_NAME_AWS;
                cs.address = ((Instance) instance).getPublicIpAddress();
                cs.regionId = getRequestRegionId(this.context);
                cs.zoneId = ((Instance) instance).getPlacement().getAvailabilityZone();
                cs.powerState = AWSUtils.mapToPowerState(((Instance) instance).getState());
                cs.computeHostLink = this.context.endpoint.computeHostLink;
                if (this.context.child.customProperties == null) {
                    cs.customProperties = new HashMap<>();
                } else {
                    cs.customProperties = this.context.child.customProperties;
                }
                cs.customProperties.put(SOURCE_TASK_LINK,
                        ProvisionComputeTaskService.FACTORY_LINK);
                cs.customProperties.put(AWSConstants.AWS_VPC_ID,
                        ((Instance) instance).getVpcId());
                cs.lifecycleState = LifecycleState.READY;
                cs.diskLinks = new ArrayList<>();

                patchOperations.addAll(createPatchNICStatesOperations(this.context.nics,
                        ((Instance) instance)));

                updateAndTagDisks(this.context.bootDisk, this.context.imageDisks,
                        this.context.dataDisks, ((Instance) instance).getBlockDeviceMappings());

                DeferredResult<ComputeState> dr = new DeferredResult<>();
                if (this.context.imageDisks != null && !this.context.imageDisks.isEmpty()) {

                    DeferredResult.allOf(this.context.imageDisks.stream()
                            .map(diskState ->
                                    sendWithDeferredResult(
                                            Operation.createPost(this.service.getHost(),
                                                    DiskService.FACTORY_LINK)
                                                    .setReferer(this.context.service.getHost()
                                                            .getUri())
                                                    .setBody(diskState), DiskState.class)
                            ).collect(Collectors.toList()))
                            .thenApply(diskStates -> {
                                for (DiskState diskState : diskStates) {
                                    cs.diskLinks.add(diskState.documentSelfLink);
                                }
                                return cs;
                            })
                            .whenComplete((o, exc) -> {
                                if (exc != null) {
                                    dr.fail(exc);
                                    return;
                                }
                                dr.complete(cs);
                            });
                } else {
                    dr.complete(cs);
                }

                //update boot disk size of instance-store AMI's
                patchOperations.addAll(createPatchDiskStatesOperations(
                        Arrays.asList(this.context.bootDisk)));
                //update disk size and device names of the data disks
                patchOperations.addAll(createPatchDiskStatesOperations(this.context.dataDisks));

                cs.diskLinks.add(this.context.bootDisk.documentSelfLink);
                for (DiskState dataDisk : this.context.dataDisks) {
                    cs.diskLinks.add(dataDisk.documentSelfLink);
                }

                Operation patchState = Operation
                        .createPatch(this.context.computeRequest.resourceReference)
                        .setBody(cs)
                        .setReferer(this.service.getHost().getUri());
                patchOperations.add(patchState);

                OperationJoin joinOp = OperationJoin.create(patchOperations);
                joinOp.setCompletion((ox, exc) -> {
                    if (exc != null) {
                        this.service.logSevere(() -> String.format("Error updating VM state. %s",
                                Utils.toString(exc)));
                        this.context.taskManager.patchTaskToFailure(
                                new IllegalStateException("Error updating VM state"));
                        return;
                    }

                    //attach external ebs volumes to the instance if any.
                    if (!this.context.externalDisks.isEmpty()) {
                        if (this.context.availableEbsDiskNames.size() < this.context.externalDisks
                                .size()) {
                            //fail if sufficient number of attach paths are not available.
                            String error = "External disks cannot be attached. Insufficient device names.";
                            logSevere(() -> "[AWSInstanceService] " + error);
                            this.context.taskManager.patchTaskToFailure(
                                    new IllegalArgumentException(error));
                            return;
                        }

                        Operation computeOp = ox.values().stream().filter(resourceOp ->
                                resourceOp.getUri().getPath().contains(ComputeService.FACTORY_LINK)
                        ).findFirst().orElse(null);

                        ComputeState provisionedComputeState = computeOp
                                .getBody(ComputeState.class);
                        DeferredResult<DiskState> attachDr = attachExternalDisks(
                                provisionedComputeState.id, this.context.externalDisks,
                                this.context.availableEbsDiskNames, this.context.amazonEC2Client);

                        attachDr.whenComplete((diskState, throwable) -> {
                            if (throwable != null) {
                                String error = String .format("Error in attaching external disks. %s.",
                                                throwable.getCause());
                                logSevere(() -> "[AWSInstanceService] " + error);
                                this.context.taskManager.patchTaskToFailure(
                                        new IllegalArgumentException(error));
                                return;
                            }
                            // patch all the externally attached disks.
                            DeferredResult<List<Operation>> externalDisksDr =
                                    updateComputeAndDiskStates(this.context.externalDisks,
                                            provisionedComputeState, this.service);
                            externalDisksDr.whenComplete((c, e) -> {
                                if (e != null) {
                                    String error = String.format("Error updating computeState and "
                                                    + "diskStates of external disks. %s",
                                            Utils.toString(e));
                                    logSevere(() -> "[AWSInstanceService] " + error);
                                    this.context.taskManager.patchTaskToFailure(
                                            new IllegalArgumentException(error));
                                    return;
                                }
                                this.context.taskManager.finishTask();
                            });
                        });
                    } else {
                        this.context.taskManager.finishTask();
                    }
                });

                dr.whenComplete((computeState, throwable) -> {
                    if (throwable != null) {
                        this.service.logSevere(() -> String.format(" [AWSInstanceService] Error "
                                + "updating VM state. %s", Utils.toString(throwable)));
                        this.context.taskManager.patchTaskToFailure(
                                new IllegalStateException("Error updating VM state"));
                        return;
                    }
                    joinOp.sendWith(this.service.getHost());
                });
            };

            String instanceId = result.getReservation().getInstances().get(0)
                    .getInstanceId();

            tagInstanceAndStartStatusChecker(instanceId, this.context.child.tagLinks, consumer);
        }

        private DeferredResult<DiskState> attachExternalDisks( String id,
                List<DiskState> externalDisks, List<String> availableEbsDiskNames,
                AmazonEC2AsyncClient client) {

            List<DeferredResult<Pair<DiskState, Throwable>>> diskStateResults =
                    externalDisks.stream().map(externalDisk -> {
                        String deviceName = availableEbsDiskNames.get(0);
                        availableEbsDiskNames.remove(0);
                        externalDisk.customProperties.put(DEVICE_NAME, deviceName);

                        AttachVolumeRequest attachVolumeRequest = new AttachVolumeRequest()
                                .withInstanceId(id)
                                .withVolumeId(externalDisk.id)
                                .withDevice(deviceName);

                        DeferredResult<DiskState> diskDr = new DeferredResult<>();

                        AWSAsyncHandler<AttachVolumeRequest, AttachVolumeResult> attachDiskHandler =
                                new AWSAsyncHandler<AttachVolumeRequest, AttachVolumeResult>() {
                                    @Override protected void handleError(
                                            Exception exception) {
                                        diskDr.fail(exception);
                                    }

                                    @Override protected void handleSuccess(
                                            AttachVolumeRequest request,
                                            AttachVolumeResult result) {
                                        diskDr.complete(externalDisk);
                                    }
                                };
                        client.attachVolumeAsync(attachVolumeRequest, attachDiskHandler);
                        return diskDr;
                    }).map(diskDr -> diskDr
                            .thenApply(
                                    diskState -> Pair.of(diskState, (Throwable) null))
                            .exceptionally(ex -> Pair.of(null, ex.getCause())))
                            .collect(Collectors.toList());

            return DeferredResult.allOf(diskStateResults)
                    .thenCompose(pairs -> {
                        // Collect error messages if any for all the external disks.
                        StringJoiner stringJoiner = new StringJoiner(",");
                        pairs.stream().filter(p -> p.left == null)
                                .forEach(p -> stringJoiner.add(p.right.getMessage()));
                        if (stringJoiner.length() > 0) {
                            return DeferredResult.failed(new Throwable(stringJoiner
                                    .toString()));
                        } else {
                            return DeferredResult.completed(new DiskState());
                        }
                    });
        }

        /**
         * Update photon-model disk states and compute state to reflect the attached disks.
         *
         */
        private DeferredResult<List<Operation>> updateComputeAndDiskStates(
                List<DiskState> externalDisks, ComputeState computeState, StatelessService service) {
            List<DeferredResult<Operation>> patchDRs = new ArrayList<>();

            patchDRs.addAll(updateDiskStates(externalDisks, service));
            patchDRs.add(updateComputeState(computeState, externalDisks, service));

            return DeferredResult.allOf(patchDRs);
        }

        /**
         * Add diskLinks to computeState
         */
        private DeferredResult<Operation> updateComputeState(ComputeState computeState,
                List<DiskState> externalDisks, StatelessService service) {

            if (computeState.diskLinks == null) {
                computeState.diskLinks = new ArrayList<>();
            }

            for (DiskState diskState : externalDisks) {
                computeState.diskLinks.add(diskState.documentSelfLink);
            }

            Operation computeStateOp = Operation.createPatch(service.getHost(),
                    computeState.documentSelfLink)
                    .setBody(computeState)
                    .setReferer(service.getHost().getUri());

            return service.sendWithDeferredResult(computeStateOp);
        }

        /**
         * Update attach status of each disk
         */
        private List<DeferredResult<Operation>> updateDiskStates(List<DiskState> diskStates, StatelessService service) {
            List<Operation> diskOps = new ArrayList<>();

            for (DiskState diskState : diskStates) {
                diskState.status = DiskService.DiskStatus.ATTACHED;
                diskOps.add(Operation.createPatch(service.getHost(), diskState.documentSelfLink)
                        .setBody(diskState)
                        .setReferer(service.getHost().getUri()));
            }

            return diskOps.stream().map(diskOp -> service.sendWithDeferredResult(diskOp))
                    .collect(Collectors.toList());
        }

        /**
         * updates status, Id and name of the disk. Also tags the corresponding AWS volume with its name.
         */
        private void updateAndTagDisks(DiskState bootDisk, List<DiskState> imageDisks,
                List<DiskState> additionalDisks,
                List<InstanceBlockDeviceMapping> blockDeviceMappings) {
            List<DiskState> diskStateList = new ArrayList<>();
            diskStateList.add(bootDisk);
            diskStateList.addAll(imageDisks);
            diskStateList.addAll(additionalDisks);

            for (DiskState diskState : diskStateList) {
                diskState.status = DiskService.DiskStatus.ATTACHED;
                String deviceType = diskState.customProperties.get(DEVICE_TYPE);
                if (deviceType.equals(AWSStorageType.EBS.getName())) {
                    String deviceName = diskState.customProperties.get(DEVICE_NAME);
                    for (InstanceBlockDeviceMapping blockDeviceMapping : blockDeviceMappings) {
                        if (blockDeviceMapping.getDeviceName().equals(deviceName)) {
                            diskState.id = blockDeviceMapping.getEbs().getVolumeId();
                            if (diskState.name == null) {
                                diskState.name = diskState.id;
                            } else {
                                tagDisk(diskState.id, diskState.name);
                            }
                            break;
                        }
                    }
                } else {
                    diskState.id = String.format("%s_%s", AWSStorageType.INSTANCE_STORE.getName(),
                            UUID.randomUUID().toString());
                    diskState.name = diskState.id;
                }
            }
        }

        private List<Operation> createPatchNICStatesOperations(List<AWSNicContext> nics,
                Instance instance) {

            List<Operation> patchOperations = new ArrayList<>();
            for (InstanceNetworkInterface instanceNic : instance.getNetworkInterfaces()) {
                List<NetworkInterfaceState> nicStates = nics.stream().map(
                        nicCtx -> {
                            return nicCtx.nicStateWithDesc;
                        }).collect(Collectors.toList());
                NetworkInterfaceState nicStateWithDesc = nicStates.stream()
                        .filter(nicState -> nicState != null)
                        .filter(nicState -> nicState.deviceIndex == instanceNic.getAttachment()
                                .getDeviceIndex())
                        .findFirst()
                        .orElse(null);

                if (nicStateWithDesc != null) {
                    NetworkInterfaceState updateNicState = new NetworkInterfaceState();
                    updateNicState.address = instanceNic.getPrivateIpAddress();

                    patchOperations.add(Operation.createPatch(this.service.getHost(),
                            nicStateWithDesc.documentSelfLink)
                            .setBody(updateNicState)
                            .setReferer(this.service.getHost().getUri()));
                }
            }

            return patchOperations;
        }

        /**
         * creates patch operations to update each diskstate with the latest information.
         */
        private List<Operation> createPatchDiskStatesOperations(
                List<DiskState> additionalDiskStates) {
            List<Operation> patchOperations = new ArrayList<>();
            for (DiskState diskState : additionalDiskStates) {
                patchOperations.add(Operation.createPatch(this.service.getHost(),
                        diskState.documentSelfLink)
                        .setBody(diskState)
                        .setReferer(this.service.getHost().getUri())
                );
            }

            return patchOperations;
        }

        private void tagDisk(String diskId, String tagValue) {
            List<Tag> tagsToCreate = new ArrayList<>();
            tagsToCreate.add(new Tag().withKey(AWS_TAG_NAME).withValue(tagValue));
            AWSUtils.tagResources(this.context.amazonEC2Client, tagsToCreate, diskId);
        }

        private void tagInstanceAndStartStatusChecker(String instanceId, Set<String> tagLinks,
                Consumer<Object> consumer) {

            List<Tag> tagsToCreate = new ArrayList<>();
            tagsToCreate.add(new Tag().withKey(AWS_TAG_NAME).withValue(this.context.child.name));

            Runnable proceed = () -> {
                AWSUtils.tagResources(this.context.amazonEC2Client, tagsToCreate, instanceId);

                AWSTaskStatusChecker
                        .create(this.context.amazonEC2Client, instanceId,
                                AWSTaskStatusChecker.AWS_RUNNING_NAME,
                                Arrays.asList(AWSTaskStatusChecker.AWS_TERMINATED_NAME), consumer,
                                this.context.taskManager,
                                this.service, this.context.taskExpirationMicros)
                        .start(new Instance());
            };

            // add the name tag only if there are no tag links
            if (tagLinks == null || tagLinks.isEmpty()) {
                proceed.run();
                return;
            }

            // if there are tag links get the TagStates and convert to amazon Tags
            OperationJoin.JoinedCompletionHandler joinedCompletionHandler = (ops, exs) -> {
                if (exs != null && !exs.isEmpty()) {
                    this.context.taskManager.patchTaskToFailure(exs.values().iterator().next());
                    return;
                }

                tagsToCreate.addAll(ops.values().stream()
                        .map(op -> op.getBody(TagState.class))
                        .map(tagState -> new Tag(tagState.key, tagState.value))
                        .collect(Collectors.toList()));

                proceed.run();
            };

            Stream<Operation> getTagOperations = tagLinks.stream()
                    .map(tagLink -> Operation.createGet(this.service, tagLink));
            OperationContext.restoreOperationContext(this.opContext);
            OperationJoin.create(getTagOperations).setCompletion(joinedCompletionHandler)
                    .sendWith(this.service);
        }

    }

    private void deleteInstance(AWSInstanceContext aws) {

        if (aws.computeRequest.isMockRequest) {
            aws.taskManager.finishTask();
            return;
        }

        final String instanceId = aws.child.id;

        if (instanceId == null || !instanceId.startsWith(AWS_INSTANCE_ID_PREFIX)) {
            // nothing to delete
            aws.taskManager.finishTask();
            return;
        }

        List<String> instanceIdList = new ArrayList<>();
        instanceIdList.add(instanceId);

        TerminateInstancesRequest termRequest = new TerminateInstancesRequest(instanceIdList);
        AWSTerminateHandler terminateHandler = new AWSTerminateHandler(aws, instanceId);

        aws.amazonEC2Client.terminateInstancesAsync(termRequest,
                terminateHandler);
    }

    private class AWSTerminateHandler implements
            AsyncHandler<TerminateInstancesRequest, TerminateInstancesResult> {

        private final AWSInstanceContext context;
        private final OperationContext opContext;
        private final String instanceId;

        private AWSTerminateHandler(AWSInstanceContext context, String instanceId) {
            this.opContext = OperationContext.getOperationContext();
            this.context = context;
            this.instanceId = instanceId;
        }

        @Override
        public void onError(Exception exception) {
            OperationContext.restoreOperationContext(this.opContext);

            if (exception instanceof AmazonServiceException
                    && ((AmazonServiceException) exception).getErrorCode()
                    .equalsIgnoreCase(AWS_INVALID_INSTANCE_ID_ERROR_CODE)) {
                AWSInstanceService.this.logWarning(
                        "Could not delete instance with id %s. Continuing... Exception on AWS"
                                + " is: %s",
                        this.instanceId, exception);
                deleteConstructsReferredByInstance()
                        .whenComplete((aVoid, exc) -> {
                            if (exc != null) {
                                this.context.taskManager.patchTaskToFailure(
                                        new IllegalStateException("Error deleting AWS subnet",
                                                exc));
                            } else {
                                AWSInstanceService.this.logInfo(() -> String.format("Deleting"
                                                + " subnets 'created-by' [%s]: SUCCESS",
                                        this.context.computeRequest.resourceLink()));

                                this.context.taskManager.finishTask();
                            }
                        });
                return;
            }
            AWSInstanceService.this.logWarning(() -> String.format("Error deleting instances"
                    + " received from AWS: %s", exception.getMessage()));

            this.context.taskManager.patchTaskToFailure(exception);
        }

        @Override
        public void onSuccess(TerminateInstancesRequest request,
                TerminateInstancesResult result) {

            Consumer<Object> postTerminationCallback = (instance) -> {

                OperationContext.restoreOperationContext(AWSTerminateHandler.this.opContext);

                if (instance == null) {
                    this.context.taskManager.patchTaskToFailure(
                            new IllegalStateException("Error getting instance"));
                    return;
                }

                deleteConstructsReferredByInstance()
                        .whenComplete((aVoid, exc) -> {
                            if (exc != null) {
                                this.context.taskManager.patchTaskToFailure(
                                        new IllegalStateException("Error deleting AWS subnet",
                                                exc));
                            } else {
                                AWSInstanceService.this.logInfo(() -> String.format("Deleting"
                                                + " subnets 'created-by' [%s]: SUCCESS",
                                        this.context.computeRequest.resourceLink()));

                                this.context.taskManager.finishTask();
                            }
                        });
            };

            AWSTaskStatusChecker.create(this.context.amazonEC2Client, this.instanceId,
                    AWSTaskStatusChecker.AWS_TERMINATED_NAME, postTerminationCallback,
                    this.context.taskManager, AWSInstanceService.this,
                    this.context.taskExpirationMicros).start(new Instance());
        }

        private DeferredResult<List<ResourceState>> deleteConstructsReferredByInstance() {

            AWSInstanceService.this.logInfo(() -> String.format("Get all states to delete"
                    + " 'created-by' [%s]", this.context.computeRequest.resourceLink()));

            // Query all states that are usedBy/createdBy the VM state we are deleting
            Query query = Builder.create()
                    .addCompositeFieldClause(
                            ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                            ComputeProperties.CREATE_CONTEXT_PROP_NAME,
                            this.context.computeRequest.resourceLink())
                    .build();

            QueryTop<ResourceState> statesToDeleteQuery = new QueryTop<>(
                    getHost(), query, ResourceState.class, this.context.parent.tenantLinks,
                    null /* endpointLink */);
            statesToDeleteQuery.setClusterType(ServiceTypeCluster.INVENTORY_SERVICE);

            // Once got states to delete process with actual deletion
            return statesToDeleteQuery.collectDocuments(Collectors.toList())
                    .thenCompose(this::handleStatesToDelete);
        }

        private DeferredResult<List<ResourceState>> handleStatesToDelete(
                List<ResourceState> statesToDelete) {

            List<DeferredResult<ResourceState>> statesToDeleteDR = statesToDelete.stream()
                    // NOTE: For now only Subnets are handled
                    .filter(rS -> rS.documentKind.endsWith(SubnetState.class.getSimpleName()))
                    // First delete AWS subnet, then delete Subnet state
                    .map(rS -> deleteAWSSubnet(rS).thenCompose(this::deleteSubnetState))
                    .collect(Collectors.toList());

            return DeferredResult.allOf(statesToDeleteDR);
        }

        // Do AWS subnet deletion
        private DeferredResult<ResourceState> deleteAWSSubnet(ResourceState stateToDelete) {

            AWSInstanceService.this.logInfo(() -> String.format("Deleting AWS Subnet [%s]"
                            + " 'created-by' [%s]", stateToDelete.id,
                    this.context.computeRequest.resourceLink()));

            DeleteSubnetRequest req = new DeleteSubnetRequest().withSubnetId(stateToDelete.id);

            String msg = "Delete AWS subnet + [" + stateToDelete.name + "]";

            AWSDeferredResultAsyncHandler<DeleteSubnetRequest, DeleteSubnetResult> deleteAWSSubnet = new AWSDeferredResultAsyncHandler<DeleteSubnetRequest, DeleteSubnetResult>(
                    AWSInstanceService.this, msg) {

                @Override
                protected Exception consumeError(Exception exception) {
                    if (exception instanceof AmazonEC2Exception) {
                        AmazonEC2Exception amazonExc = (AmazonEC2Exception) exception;
                        if (AWS_DEPENDENCY_VIOLATION_ERROR_CODE.equals(amazonExc.getErrorCode())) {
                            // AWS subnet is being used by other AWS Instances.
                            return RECOVERED;
                        }
                    }
                    return exception;
                }
            };

            this.context.amazonEC2Client.deleteSubnetAsync(req, deleteAWSSubnet);

            return deleteAWSSubnet.toDeferredResult().thenApply(ignore -> stateToDelete);
        }

        // Do Subnet state deletion
        private DeferredResult<ResourceState> deleteSubnetState(ResourceState stateToDelete) {

            if (stateToDelete == null) {
                // The AWS subnet deletion has failed. See deleteAWSSubnet method.
                // Do not delete SubnetState and continue with Instance deletion.
                return DeferredResult.completed(stateToDelete);
            }

            AWSInstanceService.this.logInfo(() -> String.format("Deleting Subnet state [%s]"
                            + " 'created-by' [%s]", stateToDelete.documentSelfLink,
                    this.context.computeRequest.resourceLink()));

            Operation delOp = Operation.createDelete(AWSInstanceService.this,
                    stateToDelete.documentSelfLink);

            return AWSInstanceService.this.sendWithDeferredResult(delOp)
                    .thenApply(ignore -> stateToDelete);
        }
    }

    /*
     * Simple helper method to get the region id from either the compute request or the child
     * description. The child description will be null during a credential validation operation.
     */
    private String getRequestRegionId(AWSInstanceContext aws) {
        String regionId;
        if (aws.child == null) {
            regionId = aws.computeRequest.regionId;
        } else {
            regionId = aws.child.description.regionId;
        }
        return regionId;
    }

    private void validateAWSCredentials(final AWSInstanceContext aws) {
        if (aws.computeRequest.isMockRequest || AWSUtils.isAwsClientMock()) {
            aws.operation.complete();
            return;
        }

        // make a call to validate credentials
        aws.amazonEC2Client
                .describeAvailabilityZonesAsync(
                        new DescribeAvailabilityZonesRequest(),
                        new AsyncHandler<DescribeAvailabilityZonesRequest, DescribeAvailabilityZonesResult>() {
                            @Override
                            public void onError(Exception e) {
                                if (e instanceof AmazonServiceException) {
                                    AmazonServiceException ase = (AmazonServiceException) e;
                                    if (ase.getStatusCode() == STATUS_CODE_UNAUTHORIZED) {
                                        ServiceErrorResponse r = Utils.toServiceErrorResponse(e);
                                        r.statusCode = STATUS_CODE_UNAUTHORIZED;
                                        aws.operation.fail(e, r);
                                        return;
                                    }
                                }

                                aws.operation.fail(e);
                            }

                            @Override
                            public void onSuccess(DescribeAvailabilityZonesRequest request,
                                    DescribeAvailabilityZonesResult describeAvailabilityZonesResult) {
                                aws.operation.complete();
                            }
                        });
    }

    private void customizeBootDiskProperties(DiskState bootDisk, String rootDeviceType,
            BlockDeviceMapping rootDeviceMapping, boolean hasHardConstraint,
            AWSInstanceContext aws) {
        if (rootDeviceType.equals(AWSStorageType.EBS.name().toLowerCase())) {
            String requestedType = bootDisk.customProperties.get(DEVICE_TYPE);
            EbsBlockDevice ebs = rootDeviceMapping.getEbs();
            if (hasHardConstraint) {
                validateIfDeviceTypesAreMatching(rootDeviceType, requestedType);
            }
            bootDisk.capacityMBytes = ebs.getVolumeSize() * 1024;
            updateDeviceMapping(rootDeviceType, requestedType, rootDeviceMapping.getDeviceName(),
                    ebs, bootDisk);
            bootDisk.customProperties.put(DEVICE_TYPE, AWSStorageType.EBS.getName());
            bootDisk.customProperties.put(DEVICE_NAME, rootDeviceMapping.getDeviceName());
            bootDisk.customProperties.put(VOLUME_TYPE, ebs.getVolumeType());
            bootDisk.customProperties.put(DISK_IOPS, String.valueOf(ebs.getIops()));
        } else {
            if (aws.instanceTypeInfo.dataDiskSizeInMB != null) {
                this.logInfo(
                        () -> "[AWSInstanceService] Instance-Store boot disk size is set to the "
                                + "value supported by instance-type.");
                bootDisk.capacityMBytes = aws.instanceTypeInfo.dataDiskSizeInMB;
                bootDisk.customProperties.put(DEVICE_TYPE, AWSStorageType.INSTANCE_STORE.getName());
            }
        }
    }

    /**
     * creates the device mapping for each of the data disk and adds it to the runInstancesRequest.
     */
    private void addInstanceStoreDeviceMappings(List<DiskState> instanceStoreDiskStates,
            List<BlockDeviceMapping> blockDeviceMappings, List<String> usedDeviceNames,
            InstanceType instanceType) {


    }

    /**
     * creates the device mapping for each of the data disk and adds it to the runInstancesRequest.
     */
    private void addEbsDeviceMappings(List<DiskState> ebsDiskStates,
            List<BlockDeviceMapping> blockDeviceMappings, List<String> usedDeviceNames) {

    }

    private void validateSupportForAdditionalInstanceStoreDisks(List<DiskState> disks,
            List<BlockDeviceMapping> blockDeviceMappings, InstanceType type,
            String rootDeviceType) {
        validateImageAndInstanceTypeCompatibility(type, rootDeviceType);
        int numInstanceStoreDisksInImage = countInstanceStoreDisksInImage(blockDeviceMappings);
        int totalInstanceStoreDisks = numInstanceStoreDisksInImage + disks.size();
        AssertUtil.assertTrue(totalInstanceStoreDisks <= type.dataDiskMaxCount,
                String.format("%s does not support %s additional instance-store disks", type,
                        disks.size()));
    }

    private void validateImageAndInstanceTypeCompatibility(InstanceType type,
            String rootDeviceType) {
        AssertUtil.assertTrue(
                EnumUtils.isValidEnum(AWSConstants.AWSInstanceStoreTypes.class, type.storageType),
                String.format("%s does not support instance-store volumes", type.id));
        if (!rootDeviceType.equals(AWSStorageType.EBS.name().toLowerCase())) {
            AssertUtil.assertFalse(
                    type.storageType.equals(AWSConstants.AWSInstanceStoreTypes.NVMe_SSD.name()),
                    String.format(
                            "%s supports only NVMe_SSD instance-store disks and NVMe disks cannot be "
                                    + "attached to %s AMI", type.id, rootDeviceType));
        }
    }

    private List<String> getUsedDeviceNames(List<BlockDeviceMapping> blockDeviceMappings) {
        List<String> usedDeviceNames = new ArrayList<>();
        for (BlockDeviceMapping blockDeviceMapping : blockDeviceMappings) {
            usedDeviceNames.add(blockDeviceMapping.getDeviceName());
        }
        return usedDeviceNames;
    }

    private List<String> getUsedVirtualNames(List<BlockDeviceMapping> blockDeviceMappings) {
        List<String> usedVirtualNames = new ArrayList<>();
        for (BlockDeviceMapping blockDeviceMapping : blockDeviceMappings) {
            if (blockDeviceMapping.getEbs() == null) {
                usedVirtualNames.add(blockDeviceMapping.getVirtualName());
            }
        }
        return usedVirtualNames;
    }

    /**
     * Count the number of instance-store disks configured in the image that are marked for
     * provisioning.
     */
    private int countInstanceStoreDisksInImage(List<BlockDeviceMapping> blockDeviceMappings) {
        List<BlockDeviceMapping> unsuppressedInstanceStoreMappings =
                getUnsuppressedInstanceStoreMappings(blockDeviceMappings);
        return unsuppressedInstanceStoreMappings != null ?
                unsuppressedInstanceStoreMappings.size() : 0;
    }

    /**
     * Get the list of instance store mappings available in the image and are mapped for
     * provisioning. If noDevice != null then that device is suppressed and cannot provision a
     * disk.
     */
    private List<BlockDeviceMapping> getUnsuppressedInstanceStoreMappings(
            List<BlockDeviceMapping> blockDeviceMappings) {
        return blockDeviceMappings.stream()
                .filter(blockDeviceMapping -> blockDeviceMapping.getDeviceName() != null &&
                        blockDeviceMapping.getEbs() == null &&
                        blockDeviceMapping.getNoDevice() == null
                ).collect(Collectors.toList());
    }

    /**
     * Creates the device mappings for the ebs disks.
     */
    private List<BlockDeviceMapping> createEbsDeviceMappings(List<DiskState> ebsDisks,
            List<String> availableDiskNames) {
        List<BlockDeviceMapping> additionalDiskMappings = new ArrayList<>();
        if (availableDiskNames.size() >= ebsDisks.size()) {
            for (DiskState diskState : ebsDisks) {
                if (diskState.capacityMBytes > 0) {
                    BlockDeviceMapping blockDeviceMapping = new BlockDeviceMapping();
                    EbsBlockDevice ebsBlockDevice = new EbsBlockDevice();
                    int diskSize = (int) diskState.capacityMBytes / 1024;
                    ebsBlockDevice.setVolumeSize(diskSize);

                    if (diskState.customProperties != null) {
                        String requestedVolumeType = diskState.customProperties.get(VOLUME_TYPE);
                        if (requestedVolumeType != null) {
                            validateSizeSupportedByVolumeType(diskSize, requestedVolumeType);
                            ebsBlockDevice.setVolumeType(requestedVolumeType);
                        }
                        String diskIops = diskState.customProperties.get(DISK_IOPS);
                        if (diskIops != null && !diskIops.isEmpty()) {
                            int iops = Integer.parseInt(diskIops);
                            if (iops > diskSize * MAX_IOPS_PER_GiB) {
                                String info = String.format("[AWSInstanceService] Requested "
                                                + "IOPS (%s) exceeds the maximum value supported"
                                                + " by %sGiB disk. Continues provisioning the "
                                                + "disk with %s iops", iops, diskSize,
                                        diskSize * MAX_IOPS_PER_GiB);
                                this.logInfo(() -> info);
                                iops = diskSize * MAX_IOPS_PER_GiB;
                            }
                            ebsBlockDevice.setIops(iops);
                        }
                    }

                    diskState.encrypted =
                            diskState.encrypted == null ? false : diskState.encrypted;
                    ebsBlockDevice.setEncrypted(diskState.encrypted);

                    String deviceName = availableDiskNames.get(0);
                    availableDiskNames.remove(0);
                    blockDeviceMapping.setDeviceName(deviceName);
                    blockDeviceMapping.setEbs(ebsBlockDevice);
                    additionalDiskMappings.add(blockDeviceMapping);
                    if (diskState.customProperties == null) {
                        diskState.customProperties = new HashMap<>();
                    }
                    diskState.customProperties.put(DEVICE_NAME, deviceName);
                } else {
                    String message = "Additional disk size cannot be zero";
                    this.logWarning(() -> "[AWSInstanceService] " + message);
                    throw new IllegalArgumentException(message);
                }
            }
        } else {
            String message = "Additional ebs disks cannot be attached. Not sufficient "
                    + "device names are available.";
            this.logWarning(() -> "[AWSInstanceService] " + message);
            throw new IllegalArgumentException(message);
        }
        return additionalDiskMappings;
    }

    /**
     * Creates device mappings for the instance-store disks.
     */
    private List<BlockDeviceMapping> createInstanceStoreMappings(List<DiskState> instanceStoreDisks,
                                                                 List<String> usedDeviceNames,
                                                                 List<String> usedVirtualNames,
                                                                 String instanceType,
                                                                 Integer capacityMBytes,
                                                                 String platform,
                                                                 String virtualizationType) {
        List<BlockDeviceMapping> deviceMappings = new ArrayList<>();
        if (!instanceStoreDisks.isEmpty()) {
            this.logInfo(
                    () -> String.format("[AWSInstanceService] Ignores the size and type of the "
                            + "additional disk. Instance-store type of additional disks are "
                            + "provisioned with the capacity supported by %s", instanceType));
            List<String> availableDeviceNames = AWSBlockDeviceNameMapper.getAvailableNames(AWSSupportedOS.get(platform),
                    AWSSupportedVirtualizationTypes.get(virtualizationType), AWSStorageType.INSTANCE_STORE, instanceType, usedDeviceNames);

            List<String> availableVirtualNames = getAvailableVirtualNames(usedVirtualNames);
            if (availableDeviceNames.size() >= instanceStoreDisks.size()
                    && availableVirtualNames.size() >= instanceStoreDisks.size()) {
                for (DiskState diskState : instanceStoreDisks) {

                    BlockDeviceMapping blockDeviceMapping = new BlockDeviceMapping();

                    String deviceName = availableDeviceNames.get(0);
                    blockDeviceMapping.setDeviceName(deviceName);
                    availableDeviceNames.remove(0);
                    usedDeviceNames.add(deviceName);

                    String virtualName = availableVirtualNames.get(0);
                    blockDeviceMapping.setVirtualName(virtualName);
                    availableVirtualNames.remove(0);

                    deviceMappings.add(blockDeviceMapping);
                    if (diskState.customProperties == null) {
                        diskState.customProperties = new HashMap<>();
                    }
                    diskState.customProperties.put(DEVICE_NAME, deviceName);
                    diskState.customProperties
                            .put(DEVICE_TYPE, AWSStorageType.INSTANCE_STORE.getName());
                    diskState.capacityMBytes = capacityMBytes;
                }
            } else {
                String message = "Additional instance-store disks cannot be attached. "
                        + "Not sufficient device names are available.";
                this.logWarning(() -> "[AWSInstanceService] " + message);
                throw new IllegalArgumentException(message);
            }
        }
        return deviceMappings;
    }

    /**
     * Returns the list of virtual names that can be used for additional instance-store disks.
     * @param usedVirtualNames
     *         virtual names used by the existing disks.
     * @return The list of virtual names available for additional disks.
     */
    private List<String> getAvailableVirtualNames(List<String> usedVirtualNames) {
        List<String> availableVirtualNames = new ArrayList<>();
        for (String availableVirtualName : AWS_VIRTUAL_NAMES) {
            for (String usedVirtualName : usedVirtualNames) {
                if (usedVirtualName.equals(availableVirtualName)) {
                    availableVirtualName = null;
                    break;
                }
            }
            if (availableVirtualName != null) {
                availableVirtualNames.add(availableVirtualName);
            }
        }
        return availableVirtualNames;
    }
}
