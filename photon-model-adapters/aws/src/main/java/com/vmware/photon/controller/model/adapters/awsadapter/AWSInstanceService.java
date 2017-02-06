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

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_DEPENDENCY_VIOLATION_ERROR_CODE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_TAG_NAME;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.CLOUD_CONFIG_DEFAULT_FILE_INDEX;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.SOURCE_TASK_LINK;
import static com.vmware.xenon.common.Operation.STATUS_CODE_UNAUTHORIZED;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.ArrayList;
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
import com.amazonaws.services.ec2.model.DeleteSubnetRequest;
import com.amazonaws.services.ec2.model.DeleteSubnetResult;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesRequest;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceNetworkInterface;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.TerminateInstancesResult;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest.InstanceRequestType;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSInstanceContext.AWSNicContext;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSAsyncHandler;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSDeferredResultAsyncHandler;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSNetworkUtils;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext.BaseAdapterStage;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.LifecycleState;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.DiskService.DiskType;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState;
import com.vmware.photon.controller.model.tasks.QueryUtils.QueryTop;
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

    private static final String AWS_RUNNING_NAME = "running";
    private static final String AWS_TERMINATED_NAME = "terminated";

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
                    AdapterUtils.sendPatchToProvisioningTask(this,
                            ctx.computeRequest.taskReference);
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
        logInfo("Transition to: %s", context.stage);
        switch (context.stage) {
        case PROVISIONTASK:
            getProvisioningTaskReference(context, AWSInstanceStage.CLIENT);
            break;
        case CLIENT:
            context.amazonEC2Client = this.clientManager.getOrCreateEC2Client(context.parentAuth,
                    getRequestRegionId(context), this,
                    context.computeRequest.taskReference, false);
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
                handleError(context, new IllegalStateException("Unknown AWS provisioning stage: "
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
    }

    private void finishExceptionally(AWSInstanceContext context, Throwable failure) {
        context.error = failure;
        finishExceptionally(context);
    }

    private void finishExceptionally(AWSInstanceContext context) {
        if (context.computeRequest.taskReference != null) {
            AdapterUtils.sendFailurePatchToProvisioningTask(this,
                    context.computeRequest.taskReference, context.error);
        }
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
            AdapterUtils.sendPatchToProvisioningTask(this,
                    aws.computeRequest.taskReference);
            return;
        }

        DiskState bootDisk = aws.childDisks.get(DiskType.HDD);
        if (bootDisk == null) {
            AdapterUtils.sendFailurePatchToProvisioningTask(this,
                    aws.computeRequest.taskReference,
                    new IllegalStateException("AWS bootDisk not specified"));
            return;
        }

        if (bootDisk.bootConfig != null && bootDisk.bootConfig.files.length > 1) {
            AdapterUtils.sendFailurePatchToProvisioningTask(this,
                    aws.computeRequest.taskReference,
                    new IllegalStateException("Only 1 configuration file allowed"));
            return;
        }

        URI imageId = bootDisk.sourceImageReference;
        if (imageId == null) {
            handleError(aws, new IllegalStateException("AWS ImageId not specified"));
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
                .withImageId(imageId.toString()).withInstanceType(instanceType)
                .withMinCount(1).withMaxCount(1)
                .withMonitoring(true);

        if (aws.nics.isEmpty()) {
            runInstancesRequest.withSecurityGroupIds(AWSUtils.getOrCreateSecurityGroups(aws, null));
        } else {
            runInstancesRequest.withNetworkInterfaces(aws.getAWSNicSpecs());
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

        // handler invoked once the EC2 runInstancesAsync commands completes
        AsyncHandler<RunInstancesRequest, RunInstancesResult> creationHandler = new AWSCreationHandler(
                this, aws);
        aws.amazonEC2Client.runInstancesAsync(runInstancesRequest, creationHandler);
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
            AdapterUtils.sendFailurePatchToProvisioningTask(this.service,
                    this.context.computeRequest.taskReference, exception);
        }

        @Override
        protected void handleSuccess(RunInstancesRequest request, RunInstancesResult result) {

            // consumer to be invoked once a VM is in the running state
            Consumer<Instance> consumer = instance -> {
                List<Operation> patchOperations = new ArrayList<Operation>();
                if (instance == null) {
                    AdapterUtils.sendFailurePatchToProvisioningTask(this.service,
                            this.context.computeRequest.taskReference,
                            new IllegalStateException("Error getting instance EC2 instance"));
                    return;
                }

                ComputeState cs = new ComputeState();
                cs.id = instance.getInstanceId();
                cs.type = ComputeType.VM_GUEST;
                cs.environmentName = ComputeDescription.ENVIRONMENT_NAME_AWS;
                cs.address = instance.getPublicIpAddress();
                cs.powerState = AWSUtils.mapToPowerState(instance.getState());
                if (this.context.child.customProperties == null) {
                    cs.customProperties = new HashMap<String, String>();
                } else {
                    cs.customProperties = this.context.child.customProperties;
                }
                cs.customProperties.put(SOURCE_TASK_LINK,
                        ProvisionComputeTaskService.FACTORY_LINK);
                cs.customProperties.put(AWSConstants.AWS_VPC_ID,
                        instance.getVpcId());
                cs.lifecycleState = LifecycleState.READY;

                patchOperations.addAll(createPatchNICStatesOperations(this.context.nics, instance));

                Operation patchState = Operation
                        .createPatch(this.context.computeRequest.resourceReference)
                        .setBody(cs)
                        .setReferer(this.service.getHost().getUri());
                patchOperations.add(patchState);

                OperationJoin.JoinedCompletionHandler joinCompletion = (ox,
                        exc) -> {
                    if (exc != null) {
                        this.service.logSevere("Error updating VM state. %s", Utils.toString(exc));
                        AdapterUtils.sendFailurePatchToProvisioningTask(this.service,
                                this.context.computeRequest.taskReference,
                                new IllegalStateException("Error updating VM state"));
                        return;
                    }
                    AdapterUtils.sendPatchToProvisioningTask(this.service,
                            this.context.computeRequest.taskReference);
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
                NetworkInterfaceState nicStateWithDesc = AWSNetworkUtils
                        .getNICStateByDeviceId(nicStates, instanceNic.getAttachment()
                                .getDeviceIndex());

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

        private void tagInstanceAndStartStatusChecker(String instanceId, Set<String> tagLinks,
                Consumer<Instance> consumer) {

            List<Tag> tagsToCreate = new ArrayList<>();
            tagsToCreate.add(new Tag().withKey(AWS_TAG_NAME).withValue(this.context.child.name));

            Runnable proceed = () -> {
                AWSUtils.tagResources(this.context.amazonEC2Client, tagsToCreate, instanceId);

                AWSTaskStatusChecker
                        .create(this.context.amazonEC2Client, instanceId,
                                AWSInstanceService.AWS_RUNNING_NAME, consumer,
                                this.context.computeRequest,
                                this.service, this.context.taskExpirationMicros)
                        .start();
            };

            // add the name tag only if there are no tag links
            if (tagLinks == null || tagLinks.isEmpty()) {
                proceed.run();
                return;
            }

            // if there are tag links get the TagStates and convert to amazon Tags
            OperationJoin.JoinedCompletionHandler joinedCompletionHandler = (ops, exs) -> {
                if (exs != null && !exs.isEmpty()) {
                    AdapterUtils.sendFailurePatchToProvisioningTask(this.service,
                            this.context.computeRequest.taskReference,
                            exs.values().iterator().next());
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
            AdapterUtils.sendPatchToProvisioningTask(this, aws.computeRequest.taskReference);
            return;
        }

        final String instanceId = aws.child.id;

        if (instanceId == null) {
            aws.error = new IllegalStateException("AWS InstanceId not available");
            aws.stage = AWSInstanceStage.ERROR;
            handleAllocation(aws);
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

            AWSInstanceService.this.logWarning("Error deleting instances received from AWS: %s",
                    exception.getMessage());

            AdapterUtils.sendFailurePatchToProvisioningTask(AWSInstanceService.this,
                    this.context.computeRequest.taskReference, exception);
        }

        @Override
        public void onSuccess(TerminateInstancesRequest request,
                TerminateInstancesResult result) {

            Consumer<Instance> postTerminationCallback = (instance) -> {

                OperationContext.restoreOperationContext(AWSTerminateHandler.this.opContext);

                if (instance == null) {
                    AdapterUtils.sendFailurePatchToProvisioningTask(
                            AWSInstanceService.this,
                            this.context.computeRequest.taskReference,
                            new IllegalStateException("Error getting instance"));
                    return;
                }

                deleteConstructsReferredByInstance()
                        .whenComplete((aVoid, exc) -> {
                            if (exc != null) {
                                AdapterUtils.sendFailurePatchToProvisioningTask(
                                        AWSInstanceService.this,
                                        this.context.computeRequest.taskReference,
                                        new IllegalStateException(
                                                "Error deleting AWS subnet", exc));
                            } else {
                                AWSInstanceService.this.logInfo("Deleting Subnets 'created-by' [%s]: SUCCESS",
                                        this.context.computeRequest.resourceLink());

                                AdapterUtils.sendPatchToProvisioningTask(AWSInstanceService.this,
                                        this.context.computeRequest.taskReference);
                            }
                        });
            };

            AWSTaskStatusChecker.create(this.context.amazonEC2Client, this.instanceId,
                    AWSInstanceService.AWS_TERMINATED_NAME, postTerminationCallback,
                    this.context.computeRequest, AWSInstanceService.this, this.context.taskExpirationMicros).start();
        }

        private DeferredResult<List<ResourceState>> deleteConstructsReferredByInstance() {

            AWSInstanceService.this.logInfo("Get all states to delete 'created-by' [%s]",
                    this.context.computeRequest.resourceLink());

            // Query all states that are usedBy/createdBy the VM state we are deleting
            Query query = Builder.create()
                    .addCompositeFieldClause(
                            ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                            ComputeProperties.CREATE_CONTEXT_PROP_NAME,
                            this.context.computeRequest.resourceLink())
                    .build();

            QueryTop<ResourceState> statesToDeleteQuery = new QueryTop<>(
                    getHost(), query, ResourceState.class, this.context.parent.tenantLinks, null /*endpointLink*/);

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

            AWSInstanceService.this.logInfo("Deleting AWS Subnet [%s] 'created-by' [%s]", stateToDelete.id,
                    this.context.computeRequest.resourceLink());

            DeleteSubnetRequest req = new DeleteSubnetRequest().withSubnetId(stateToDelete.id);

            String msg = "Delete AWS subnet + [" + stateToDelete.name + "]";

            AWSDeferredResultAsyncHandler<DeleteSubnetRequest, DeleteSubnetResult>
                    deleteAWSSubnet = new AWSDeferredResultAsyncHandler<DeleteSubnetRequest,
                    DeleteSubnetResult>(AWSInstanceService.this, msg) {

                        @Override
                        protected DeferredResult<DeleteSubnetResult> consumeSuccess(
                                DeleteSubnetRequest request,
                                DeleteSubnetResult result) {
                            return DeferredResult.completed(result);
                        }

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

            AWSInstanceService.this.logInfo("Deleting Subnet state [%s] 'created-by' [%s]",
                    stateToDelete.documentSelfLink, this.context.computeRequest.resourceLink());

            Operation delOp = Operation.createDelete(AWSInstanceService.this, stateToDelete.documentSelfLink);

            return AWSInstanceService.this.sendWithDeferredResult(delOp).thenApply(ignore -> stateToDelete);
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

        aws.amazonEC2Client = this.clientManager.getOrCreateEC2Client(aws.parentAuth,
                getRequestRegionId(aws), this,
                aws.computeRequest.taskReference, false);

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
}
