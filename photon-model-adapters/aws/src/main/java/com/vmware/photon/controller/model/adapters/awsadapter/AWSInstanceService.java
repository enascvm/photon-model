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
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_DEPENDENCY_VIOLATION_ERROR_CODE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_EBS_DEVICE_NAMES;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_INSTANCE_ID_PREFIX;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_INSTANCE_STORE_DEVICE_NAMES;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_TAG_NAME;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_VIRTUAL_NAMES;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.DEVICE_NAME;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.DEVICE_TYPE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.DISK_IOPS;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.MAX_IOPS_PER_GiB;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.VOLUME_TYPE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.VOLUME_TYPE_GENERAL_PURPOSED_SSD;
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
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.services.ec2.model.AmazonEC2Exception;
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
import com.amazonaws.services.ec2.model.InstanceNetworkInterface;
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
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSDeferredResultAsyncHandler;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext.BaseAdapterStage;
import com.vmware.photon.controller.model.query.QueryUtils.QueryTop;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.LifecycleState;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.photon.controller.model.support.InstanceTypeList.InstanceType;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState;
import com.vmware.photon.controller.model.util.AssertUtil;
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

    // The security group specifies things such as the ports to be open,
    // firewall rules etc and is
    // specific to an instance and should come from the compute desc for the VM

    public AWSInstanceService() {
        this.clientManager = AWSClientManagerFactory
                .getClientManager(AWSConstants.AwsClientType.EC2);
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
     * Shortcut method that sets the next stage into the context and delegates to
     * {@link #handleAllocation(AWSInstanceContext)}.
     */
    private void handleAllocation(AWSInstanceContext context, AWSInstanceStage nextStage) {
        context.stage = nextStage;
        handleAllocation(context);
    }

    /**
     * Shortcut method that stores the error into context, sets next stage to
     * {@link AWSInstanceStage#ERROR} and delegates to
     * {@link #handleAllocation(AWSInstanceContext)}.
     */
    private void handleError(AWSInstanceContext context, Throwable e) {
        context.error = e;
        context.stage = AWSInstanceStage.ERROR;
        handleAllocation(context);
    }

    /**
     * {@code handleAllocation} version suitable for chaining to
     * {@code DeferredResult.whenComplete}.
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
     *
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
                        .getOrCreateEC2Client(context.parentAuth,
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

            List<BlockDeviceMapping> blockDeviceMappings = image.getBlockDeviceMappings();
            if (bootDisk.capacityMBytes > 0 || bootDisk.customProperties != null) {
                try {
                    String rootDeviceType = image.getRootDeviceType();
                    validateBootDiskConfiguration(bootDisk, rootDeviceType);
                    customizeBootDiskProperties(bootDisk, rootDeviceType, image.getRootDeviceName(),
                            blockDeviceMappings, aws);
                    if (!aws.dataDisks.isEmpty()) {

                        AssertUtil.assertNotNull(aws.instanceTypeInfo,
                                "instanceType cannot be null");
                        List<DiskState> instanceStoreDisks = new ArrayList<>();
                        List<DiskState> ebsDisks = new ArrayList<>();
                        if (!rootDeviceType.equals(AWSStorageType.EBS.name().toLowerCase())) {
                            instanceStoreDisks = aws.dataDisks;
                            validateSupportForInstanceStoreDisks(instanceStoreDisks, blockDeviceMappings,
                                    aws.instanceTypeInfo, rootDeviceType);
                            validateInstanceStoreDiskConfiguration(instanceStoreDisks, aws.bootDiskImageNativeId);
                        } else {
                            splitExistingDisks(aws.dataDisks, instanceStoreDisks, ebsDisks);
                            addDefaultPropertiesIfMissing(ebsDisks);
                            if (!instanceStoreDisks.isEmpty()) {
                                validateSupportForInstanceStoreDisks(instanceStoreDisks,
                                        blockDeviceMappings, aws.instanceTypeInfo, rootDeviceType);
                            }
                        }
                        addDeviceMappings(ebsDisks, instanceStoreDisks, runInstancesRequest,
                                blockDeviceMappings, aws.instanceTypeInfo);
                    }
                } catch (Exception e) {
                    aws.error = e;
                    aws.stage = AWSInstanceStage.ERROR;
                    handleAllocation(aws);
                    return;
                }
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
     * Splits the set of existing disks into instance-store disks and ebs disks
     */
    private void splitExistingDisks(List<DiskState> dataDisks, List<DiskState> instanceStoreDisks,
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
    private void addDefaultPropertiesIfMissing(List<DiskState> dataDisks) {
        for (DiskState diskState : dataDisks) {
            if (diskState.customProperties == null) {
                diskState.customProperties = new HashMap<>();
                diskState.customProperties.put(DEVICE_TYPE, AWSStorageType.EBS.getName());
                diskState.customProperties.put(VOLUME_TYPE, VOLUME_TYPE_GENERAL_PURPOSED_SSD);
            }
        }
    }

    private class AWSCreationHandler
            extends AWSAsyncHandler<RunInstancesRequest, RunInstancesResult> {

        private StatelessService service;
        private AWSInstanceContext context;

        private AWSCreationHandler(StatelessService service, AWSInstanceContext context) {
            this.service = service;
            this.context = context;
        }

        @Override
        protected void handleError(Exception exception) {
            this.context.taskManager.patchTaskToFailure(exception);
        }

        @Override
        protected void handleSuccess(RunInstancesRequest request, RunInstancesResult result) {

            String message = "[AWSInstanceService] Successfully provisioned AWS instance for "
                    + "task reference: " + this.context.computeRequest.taskReference;
            this.service.logInfo(() -> message);

            // consumer to be invoked once a VM is in the running state
            Consumer<Object> consumer = instance -> {
                List<Operation> patchOperations = new ArrayList<>();
                if (instance == null) {
                    this.context.taskManager.patchTaskToFailure(
                            new IllegalStateException("Error getting instance EC2 instance"));
                    return;
                }

                ComputeState cs = new ComputeState();
                cs.id = ((Instance)instance).getInstanceId();
                cs.type = ComputeType.VM_GUEST;
                cs.environmentName = ComputeDescription.ENVIRONMENT_NAME_AWS;
                cs.address = ((Instance)instance).getPublicIpAddress();
                cs.regionId = getRequestRegionId(this.context);
                cs.zoneId = ((Instance)instance).getPlacement().getAvailabilityZone();
                cs.powerState = AWSUtils.mapToPowerState(((Instance)instance).getState());
                if (this.context.child.customProperties == null) {
                    cs.customProperties = new HashMap<>();
                } else {
                    cs.customProperties = this.context.child.customProperties;
                }
                cs.customProperties.put(SOURCE_TASK_LINK,
                        ProvisionComputeTaskService.FACTORY_LINK);
                cs.customProperties.put(AWSConstants.AWS_VPC_ID,
                        ((Instance)instance).getVpcId());
                cs.lifecycleState = LifecycleState.READY;

                patchOperations.addAll(createPatchNICStatesOperations(this.context.nics,
                        ((Instance)instance)));
                //update boot disk size of instance-store AMI's
                patchOperations.addAll(createPatchDiskStatesOperations(
                        Arrays.asList(this.context.bootDisk)));
                //update disk size and device names of the data disks
                patchOperations.addAll(createPatchDiskStatesOperations(this.context.dataDisks));

                Operation patchState = Operation
                        .createPatch(this.context.computeRequest.resourceReference)
                        .setBody(cs)
                        .setReferer(this.service.getHost().getUri());
                patchOperations.add(patchState);

                OperationJoin.JoinedCompletionHandler joinCompletion = (ox,
                        exc) -> {
                    if (exc != null) {
                        this.service.logSevere(() -> String.format("Error updating VM state. %s",
                                Utils.toString(exc)));
                        this.context.taskManager.patchTaskToFailure(
                                new IllegalStateException("Error updating VM state"));
                        return;
                    }
                    this.context.taskManager.finishTask();
                };
                OperationJoin joinOp = OperationJoin.create(patchOperations);
                joinOp.setCompletion(joinCompletion);
                joinOp.sendWith(this.service.getHost());
            };

            String instanceId = result.getReservation().getInstances().get(0)
                    .getInstanceId();

            tagInstanceAndStartStatusChecker(instanceId, this.context.child.tagLinks, consumer);
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
        private List<Operation> createPatchDiskStatesOperations(List<DiskState> additionalDiskStates) {
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

        private void tagInstanceAndStartStatusChecker(String instanceId, Set<String> tagLinks,
                Consumer<Object> consumer) {

            List<Tag> tagsToCreate = new ArrayList<>();
            tagsToCreate.add(new Tag().withKey(AWS_TAG_NAME).withValue(this.context.child.name));

            Runnable proceed = () -> {
                AWSUtils.tagResources(this.context.amazonEC2Client, tagsToCreate, instanceId);

                AWSTaskStatusChecker
                        .create(this.context.amazonEC2Client, instanceId,
                                AWSTaskStatusChecker.AWS_RUNNING_NAME, consumer,
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
            String rootDeviceName, List<BlockDeviceMapping> blockDeviceMappings,
            AWSInstanceContext aws) {
        if (rootDeviceType.equals(AWSStorageType.EBS.name().toLowerCase())) {
            BlockDeviceMapping rootDeviceMapping = blockDeviceMappings.stream()
                    .filter(blockDeviceMapping -> blockDeviceMapping.getDeviceName()
                            .equals(rootDeviceName))
                    .findAny()
                    .orElse(null);

            EbsBlockDevice ebsRootDeviceMapping = rootDeviceMapping.getEbs();
            if (bootDisk.capacityMBytes > 0) {
                ebsRootDeviceMapping.setVolumeSize((int) bootDisk.capacityMBytes / 1024);
                ebsRootDeviceMapping.setEncrypted(null);
            }
            if (bootDisk.customProperties != null &&
                    bootDisk.customProperties.containsKey(VOLUME_TYPE) &&
                    bootDisk.customProperties.get(VOLUME_TYPE) != null) {
                String rootVolumeType = ebsRootDeviceMapping.getVolumeType();
                if (!rootVolumeType
                        .equals(bootDisk.customProperties.get(VOLUME_TYPE))) {
                    ebsRootDeviceMapping.setVolumeType(
                            bootDisk.customProperties.get(VOLUME_TYPE));
                }
            }
            if (bootDisk.customProperties != null) {
                String diskIops = bootDisk.customProperties.get(DISK_IOPS);
                if (diskIops != null && !diskIops.isEmpty()) {
                    ebsRootDeviceMapping.setIops(Integer.parseInt(diskIops));
                }
            }
        } else if (bootDisk.capacityMBytes > 0) {
            this.logWarning(() -> "[AWSInstanceService] Instance-store boot disk cannot be "
                    + "resized. Uses the default size supported by instance-type.");
            if (aws.instanceTypeInfo.dataDiskSizeInMB != null && !rootDeviceType
                    .equals(AWSStorageType.EBS.name().toLowerCase())) {
                bootDisk.capacityMBytes = aws.instanceTypeInfo.dataDiskSizeInMB;
            }
        }
    }

    private void validateBootDiskConfiguration(DiskState bootDisk, String rootDeviceType) {
        if (bootDisk.constraint == null) {
            return;
        }
        List<Condition> bootDiskConditions = bootDisk.constraint.conditions;
        if (bootDiskConditions != null) {
            if (bootDiskConditions.stream()
                    .anyMatch(condition -> condition.enforcement == Enforcement.HARD)) {
                String deviceType = bootDisk.customProperties.get(DEVICE_TYPE);
                if (deviceType != null && !rootDeviceType.equals(deviceType)) {
                    String message = String.format("Found hard constraint on boot disk. %s type "
                            + "cannot be changed to %s type.", rootDeviceType, deviceType);
                    this.logSevere("[AWSInstanceService] " + message);
                    throw new IllegalArgumentException(message);
                }
            }
        }
    }

    /**
     * creates the device mappings for each of the data disks and adds them to the runInstancesRequest.
     */
    private void addDeviceMappings(List<DiskState> ebsDiskStates, List<DiskState> instanceStoreDiskStates,
            RunInstancesRequest runInstancesRequest, List<BlockDeviceMapping> blockDeviceMappings,
            InstanceType instanceType) {

        List<BlockDeviceMapping> additionalDiskMappings = new ArrayList<>();

        List<String> usedDeviceNames = getUsedDeviceNames(blockDeviceMappings);

        if (!instanceStoreDiskStates.isEmpty()) {
            List<String> usedVirtualNames = getUsedVirtualNames(blockDeviceMappings);
            additionalDiskMappings
                    .addAll(createInstanceStoreMappings(instanceStoreDiskStates, usedDeviceNames,
                            usedVirtualNames, instanceType.id, instanceType.dataDiskSizeInMB));
        }

        additionalDiskMappings.addAll(createEbsDeviceMappings(ebsDiskStates, usedDeviceNames));
        blockDeviceMappings.addAll(additionalDiskMappings);
        runInstancesRequest.withBlockDeviceMappings(blockDeviceMappings);
    }

    private void validateSupportForInstanceStoreDisks(List<DiskState> disks,
            List<BlockDeviceMapping> blockDeviceMappings, InstanceType type, String rootDeviceType) {
        AssertUtil.assertTrue(
                EnumUtils.isValidEnum(AWSConstants.AWSInstanceStoreTypes.class, type.storageType),
                String.format("%s does not support instance-store volumes", type));
        if (!rootDeviceType.equals(AWSStorageType.EBS.name().toLowerCase())) {
            AssertUtil.assertTrue(
                    !type.storageType.equals(AWSConstants.AWSInstanceStoreTypes.NVMe_SSD.name()),
                    String.format(
                            "%s supports only NVMe_SSD instance-store disks and NVMe disks can be "
                                    + "attached only to ebs AMI", type));
        }
        int numExistingInstanceStoreDisks = countExistingInstanceStoreDisks(blockDeviceMappings);
        int totalRequestedInstanceStoreDisks = numExistingInstanceStoreDisks + disks.size();
        AssertUtil.assertTrue(totalRequestedInstanceStoreDisks <= type.dataDiskMaxCount,
                String.format("%s does not support more than %s additional instance-store disks", type,
                        type.dataDiskMaxCount - numExistingInstanceStoreDisks));
    }

    /**
     * validate if the instance-store disks are matched only to instance-store items from the profile
     */
    private void validateInstanceStoreDiskConfiguration(List<DiskState> instanceStoreDisks,
            String imageId) {
        String instanceStoreType = AWSStorageType.INSTANCE_STORE.getName();
        for (DiskState instanceStoreDisk : instanceStoreDisks) {
            if (instanceStoreDisk.customProperties == null) {
                continue;
            }

            String configuredDeviceType = instanceStoreDisk.customProperties.get(DEVICE_TYPE);
            if (configuredDeviceType != null) {
                AssertUtil.assertTrue(instanceStoreType.equals(configuredDeviceType),
                        String.format("%s disks cannot be attached to %s AMI %s.",
                                configuredDeviceType, configuredDeviceType, instanceStoreType,
                                imageId));
            }
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

    private int countExistingInstanceStoreDisks(List<BlockDeviceMapping> blockDeviceMappings) {
        return (int) blockDeviceMappings.stream()
                .filter(blockDeviceMapping -> blockDeviceMapping.getDeviceName() != null
                        && blockDeviceMapping.getEbs() == null)
                .count();
    }

    /**
     * Creates the device mappings for the ebs disks.
     */
    private List<BlockDeviceMapping> createEbsDeviceMappings(List<DiskState> ebsDisks,
            List<String> usedDeviceNames) {
        List<BlockDeviceMapping> additionalDiskMappings = new ArrayList<>();
        if (!ebsDisks.isEmpty()) {
            List<String> availableDiskNames = getAvailableDeviceNames(usedDeviceNames,
                    AWS_EBS_DEVICE_NAMES);
            if (availableDiskNames.size() >= ebsDisks.size()) {
                for (DiskState diskState : ebsDisks) {
                    if (diskState.capacityMBytes > 0) {
                        BlockDeviceMapping blockDeviceMapping = new BlockDeviceMapping();
                        EbsBlockDevice ebsBlockDevice = new EbsBlockDevice();
                        int diskSize = (int) diskState.capacityMBytes / 1024;
                        ebsBlockDevice.setVolumeSize(diskSize);

                        if (diskState.customProperties != null) {
                            //If volume type is null standard volume is provisioned.
                            if (diskState.customProperties.containsKey(VOLUME_TYPE)) {
                                ebsBlockDevice
                                        .setVolumeType(diskState.customProperties.get(VOLUME_TYPE));
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
                        usedDeviceNames.add(deviceName);
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
        }
        return additionalDiskMappings;
    }

    /**
     *
     * Creates device mappings for the instance-store disks.
     */
    private List<BlockDeviceMapping> createInstanceStoreMappings(List<DiskState> instanceStoreDisks,
            List<String> usedDeviceNames, List<String> usedVirtualNames, String instanceType, Integer capacityMBytes) {
        List<BlockDeviceMapping> deviceMappings = new ArrayList<>();
        if (!instanceStoreDisks.isEmpty()) {
            this.logInfo(
                    () -> String.format("[AWSInstanceService] Ignores the size provided for "
                            + "additional disk. Instance-store disks are provisioned with the "
                            + "capacity supported by %s", instanceType));
            List<String> availableDeviceNames = getAvailableDeviceNames(usedDeviceNames,
                    AWS_INSTANCE_STORE_DEVICE_NAMES);
            List<String> availableVirtualNames = getAvailableVirtualNames(usedVirtualNames);
            if (availableDeviceNames.size() >= instanceStoreDisks.size()
                    && availableVirtualNames.size() >= instanceStoreDisks.size()) {
                for (DiskState diskState : instanceStoreDisks) {

                    BlockDeviceMapping blockDeviceMapping = new BlockDeviceMapping();

                    String deviceName = availableDeviceNames.get(0);
                    blockDeviceMapping.setDeviceName(deviceName);
                    availableDeviceNames.remove(0);

                    String virtualName = availableVirtualNames.get(0);
                    blockDeviceMapping.setVirtualName(virtualName);
                    availableVirtualNames.remove(0);

                    deviceMappings.add(blockDeviceMapping);
                    if (diskState.customProperties == null) {
                        diskState.customProperties = new HashMap<>();
                    }
                    diskState.customProperties.put(DEVICE_NAME, deviceName);
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

    /**
     * Returns the list of virtual names that can be used for additional instance-store disks.
     * @param usedVirtualNames virtual names used by the existing disks.
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
