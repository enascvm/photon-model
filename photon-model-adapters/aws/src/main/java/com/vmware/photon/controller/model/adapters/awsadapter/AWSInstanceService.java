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

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.getOrCreateSecurityGroups;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSNetworkUtils.mapInstanceIPAddressToNICCreationOperations;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.CLOUD_CONFIG_DEFAULT_FILE_INDEX;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.SOURCE_TASK_LINK;
import static com.vmware.xenon.common.Operation.STATUS_CODE_UNAUTHORIZED;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesRequest;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult;
import com.amazonaws.services.ec2.model.DescribeSubnetsRequest;
import com.amazonaws.services.ec2.model.DescribeSubnetsResult;
import com.amazonaws.services.ec2.model.DescribeVpcsRequest;
import com.amazonaws.services.ec2.model.DescribeVpcsResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceNetworkInterfaceSpecification;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.TerminateInstancesResult;
import com.amazonaws.services.ec2.model.Vpc;

import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSAllocation.NicAllocationContext;
import com.vmware.photon.controller.model.adapters.awsadapter.BaseAwsContext.BaseAwsStages;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.ComputeService.LifecycleState;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.DiskService.DiskType;
import com.vmware.photon.controller.model.resources.FirewallService.FirewallState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceStateWithDescription;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Adapter to create an EC2 instance on AWS.
 *
 */
public class AWSInstanceService extends StatelessService {

    public static final String SELF_LINK = AWSUriPaths.AWS_INSTANCE_ADAPTER;

    public static final String AWS_ENVIRONMENT_NAME = "AWS_EC2";

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

        ComputeInstanceRequest request = op.getBody(ComputeInstanceRequest.class);

        switch (request.requestType) {
        case VALIDATE_CREDENTIALS:
            BaseAwsContext.populateContextThen(new AWSAllocation(this, request,
                    UriUtils.buildUri(this.getHost(), request.authCredentialsLink)),
                    BaseAwsStages.PARENTAUTH, (context, t) -> {
                        if (t != null) {
                            handleError(context, t);
                        } else {
                            context.awsOperation = op;
                            handleAllocation(context, AWSStages.CLIENT);
                        }
                    });
            break;
        default:
            op.complete();
            if (request.isMockRequest
                    && request.requestType == ComputeInstanceRequest.InstanceRequestType.CREATE) {
                AdapterUtils.sendPatchToProvisioningTask(this, request.taskReference);
                return;
            }
            BaseAwsContext.populateContextThen(new AWSAllocation(this, request),
                    BaseAwsStages.VMDESC, (context, t) -> {
                        if (t != null) {
                            handleError(context, t);
                            return;
                        }
                        handleAllocation(context);
                    });
        }

    }

    /**
     * Shortcut method that sets the next stage into the context and delegates to
     * {@link #handleAllocation(AWSAllocation)}.
     */
    private void handleAllocation(AWSAllocation aws, AWSStages nextStage) {
        aws.stage = nextStage;
        handleAllocation(aws);
    }

    /**
     * Shortcut method that stores the error into context, sets next stage to
     * {@link AWSStages#ERROR} and delegates to {@link #handleAllocation(AWSAllocation)}.
     */
    private void handleError(AWSAllocation aws, Throwable e) {
        aws.error = e;
        aws.stage = AWSStages.ERROR;
        handleAllocation(aws);
    }

    /**
     * State machine to handle different stages of VM creation/deletion.
     *
     * Method will act much like handlePatch, but without persistence as this is a stateless
     * service. Each call to the service will result in a synchronous execution of the stages below
     * Each stage is responsible for setting the next stage on success -- the next stage is passed
     * into action methods
     *
     * @see #handleError(AWSAllocation, Throwable)
     * @see #handleAllocation(AWSAllocation, AWSStages)
     */
    private void handleAllocation(AWSAllocation aws) {
        switch (aws.stage) {
        case PROVISIONTASK:
            getProvisioningTaskReference(aws, AWSStages.CLIENT);
            break;
        case CLIENT:
            aws.amazonEC2Client = this.clientManager.getOrCreateEC2Client(aws.parentAuth,
                    getRequestRegionId(aws), this,
                    aws.computeRequest.taskReference, false);
            // now that we have a client lets move onto the next step
            switch (aws.computeRequest.requestType) {
            case CREATE:
                handleAllocation(aws, AWSStages.VM_DISKS);
                break;
            case DELETE:
                handleAllocation(aws, AWSStages.DELETE);
                break;
            case VALIDATE_CREDENTIALS:
                validateAWSCredentials(aws);
                break;
            default:
                handleError(aws, new Exception("Unknown AWS provisioning stage"));
            }

            break;
        case DELETE:
            deleteInstance(aws);
            break;
        case VM_DISKS:
            getVMDisks(aws, AWSStages.GET_NIC_STATES);
            break;
        case GET_NIC_STATES:
            getNICStates(aws, AWSStages.GET_SUBNET_STATES);
            break;
        case GET_SUBNET_STATES:
            getNICSubnetStates(aws, AWSStages.GET_NETWORK_STATES);
            break;
        case GET_NETWORK_STATES:
            getNICNetworkStates(aws, AWSStages.GET_FIREWALL_STATES);
            break;
        case GET_FIREWALL_STATES:
            getNICFirewallStates(aws, AWSStages.GET_NETWORKS);
            break;
        case GET_NETWORKS:
            // Should be handled in advance by AWSNetworkService
            getAWSNetworks(aws, AWSStages.GET_SUBNETS);
            break;
        case GET_SUBNETS:
            // Should be handled in advance by AWSNetworkService
            getAWSSubnets(aws, AWSStages.GET_FIREWALLS);
            break;
        case GET_FIREWALLS:
            getAWSFirewalls(aws, AWSStages.CREATE_INSTANCE_NICs);
            break;
        case CREATE_INSTANCE_NICs:
            // One VPC for all VMs, one Subnet per VM
            createAWSNICSpecs(aws, AWSStages.CREATE);
            break;
        case CREATE:
            createInstance(aws);
            break;
        case ERROR:
            if (aws.computeRequest.taskReference != null) {
                AdapterUtils.sendFailurePatchToProvisioningTask(this,
                        aws.computeRequest.taskReference, aws.error);
            } else {
                aws.awsOperation.fail(aws.error);
            }
            break;
        case DONE:
            break;
        default:
            logSevere("Unhandled stage: %s", aws.stage.toString());
            break;
        }
    }

    private void getAWSSubnets(AWSAllocation aws, AWSStages next) {
        if (aws.nics.isEmpty()) {
            handleAllocation(aws, next);
            return;
        }

        DescribeSubnetsRequest subnetRequest = new DescribeSubnetsRequest();

        subnetRequest.getFilters().add(
                new Filter(AWSConstants.AWS_VPC_FILTER, Collections.singletonList(aws
                        .getVmPrimaryNic().vpc.getVpcId())));
        subnetRequest.getFilters().add(
                new Filter(AWSConstants.AWS_SUBNET_FILTER, Collections.singletonList(aws
                        .getVmPrimaryNic().subnetState.id)));

        aws.amazonEC2Client.describeSubnetsAsync(subnetRequest,
                new AsyncHandler<DescribeSubnetsRequest, DescribeSubnetsResult>() {

                    @Override
                    public void onError(Exception e) {
                        AWSInstanceService.this.handleError(aws, new IllegalStateException("Error obtaining AWS subnets.", e));
                    }

                    @Override
                    public void onSuccess(DescribeSubnetsRequest request,
                            DescribeSubnetsResult result) {

                        if (!result.getSubnets().isEmpty()) {
                            for (NicAllocationContext nicCtx : aws.nics) {
                                nicCtx.subnet = result.getSubnets().get(0);
                            }
                            handleAllocation(aws, next);
                        } else {
                            AWSInstanceService.this.handleError(aws, new IllegalStateException("Error obtaining AWS subnets."));
                        }
                    }

                });
    }

    /**
     * Obtains the VPC ID from the network state name and discovers the Virtual Private Cloud object
     * from AWS.
     */
    private void getAWSNetworks(AWSAllocation aws, AWSStages next) {
        if (aws.nics.isEmpty()) {
            handleAllocation(aws, next);
            return;
        }

        DescribeVpcsRequest vpcRequest = new DescribeVpcsRequest();

        vpcRequest.getFilters().add(
                new Filter(AWSConstants.AWS_VPC_FILTER, Collections.singletonList(aws
                        .getVmPrimaryNic().networkState.id)));

        aws.amazonEC2Client.describeVpcsAsync(vpcRequest,
                new AsyncHandler<DescribeVpcsRequest, DescribeVpcsResult>() {

                    @Override
                    public void onError(Exception e) {
                        AWSInstanceService.this.handleError(aws, new IllegalStateException("Error obtaining VPC.", e));
                    }

                    @Override
                    public void onSuccess(DescribeVpcsRequest request, DescribeVpcsResult result) {
                        if (!result.getVpcs().isEmpty()) {
                            Vpc vpc = result.getVpcs().get(0);
                            for (NicAllocationContext nicCtx : aws.nics) {
                                nicCtx.vpc = vpc;
                            }
                        }
                        handleAllocation(aws, next);
                    }

                });
    }

    private void getAWSFirewalls(AWSAllocation aws, AWSStages next) {
        if (aws.nics.isEmpty()) {
            handleAllocation(aws, next);
            return;
        }

        List<String> securityGroupIds = getOrCreateSecurityGroups(aws.getVmPrimaryNic(), aws);
        for (NicAllocationContext nicCtx : aws.nics) {
            nicCtx.groupIds = securityGroupIds;
        }
        handleAllocation(aws, next);
    }

    private void createAWSNICSpecs(AWSAllocation aws, AWSStages next) {

        int index = 0;
        for (NicAllocationContext nicCtx : aws.nics) {
            createInstanceNetworkInterfaceSpecification(aws, nicCtx, index++);
        }
        handleAllocation(aws, next);
    }

    private void createInstanceNetworkInterfaceSpecification(
            AWSAllocation aws, NicAllocationContext nicCtx, int deviceIndex) {

        if (nicCtx.subnet != null) {
            nicCtx.nicSpec = new InstanceNetworkInterfaceSpecification()
                    .withDeviceIndex(deviceIndex)
                    .withSubnetId(nicCtx.subnet.getSubnetId())
                    .withGroups(nicCtx.groupIds);
        }

        if (aws.nics.size() == 1) {
            aws.nics.get(0).nicSpec.withAssociatePublicIpAddress(true);
        }

    }

    /**
     * Get {@link NetworkState}s containing the {@link SubnetState}s
     * {@link NetworkInterfaceState#subnetLink assigned} to the NICs.
     *
     * @see #getSubnetStates(AWSAllocation, AWSStages)
     */
    private void getNICNetworkStates(AWSAllocation aws, AWSStages next) {
        if (aws.nics.isEmpty()) {
            handleAllocation(aws, next);
            return;
        }

        Stream<Operation> getNetworksOps = aws.nics.stream()
                .map(nicCtx -> Operation.createGet(this.getHost(), nicCtx.subnetState.networkLink)
                        .setCompletion((op, ex) -> {
                            if (ex == null) {
                                nicCtx.networkState = op.getBody(NetworkState.class);
                            }
                        }));

        OperationJoin operationJoin = OperationJoin.create(getNetworksOps)
                .setCompletion((ops, excs) -> {
                    if (excs != null && excs.size() > 0) {
                        handleError(aws, new IllegalStateException(
                                "Error getting network states.", excs.get(0)));
                        return;
                    }
                    handleAllocation(aws, next);
                });
        operationJoin.sendWith(this);
    }

    /**
     * Get {@link SubnetState}s {@link NetworkInterfaceState#subnetLink assigned} to the NICs.
     */
    private void getNICSubnetStates(AWSAllocation aws, AWSStages next) {
        if (aws.nics.isEmpty()) {
            handleAllocation(aws, next);
            return;
        }

        Stream<Operation> getSubnetsOps = aws.nics.stream()
                .map(nicCtx -> Operation
                        .createGet(this.getHost(), nicCtx.nicStateWithDesc.subnetLink)
                        .setCompletion((op, ex) -> {
                            if (ex == null) {
                                nicCtx.subnetState = op.getBody(SubnetState.class);
                            }
                        }));

        OperationJoin operationJoin = OperationJoin.create(getSubnetsOps)
                .setCompletion((ops, excs) -> {
                    if (excs != null && excs.size() > 0) {
                        handleError(aws, new IllegalStateException(
                                "Error getting subnet states.", excs.get(0)));
                        return;
                    }
                    handleAllocation(aws, next);
                });
        operationJoin.sendWith(this);
    }

    private void getNICStates(AWSAllocation aws, AWSStages next) {
        if (aws.child.networkInterfaceLinks == null
                || aws.child.networkInterfaceLinks.size() == 0) {
            handleAllocation(aws, next);
            return;
        }

        List<Operation> getNICsOps = new ArrayList<>();

        for (String nicStateLink : aws.child.networkInterfaceLinks) {

            NicAllocationContext nicCtx = new NicAllocationContext();

            aws.nics.add(nicCtx);

            URI nicStateUri = NetworkInterfaceStateWithDescription
                    .buildUri(UriUtils.buildUri(getHost(), nicStateLink));

            Operation getNicOp = Operation.createGet(nicStateUri).setCompletion((op, exc) -> {
                if (exc == null) {
                    // Handle SUCCESS; error is handled by the join handler.
                    nicCtx.nicStateWithDesc = op
                            .getBody(NetworkInterfaceStateWithDescription.class);
                }
            });

            getNICsOps.add(getNicOp);
        }

        OperationJoin operationJoin = OperationJoin.create(getNICsOps)
                .setCompletion(
                        (ops, excs) -> {
                            if (excs != null && excs.size() > 0) {
                                handleError(aws, new IllegalStateException(
                                        "Error getting network interface states.", excs.get(0)));
                                return;
                            }
                            handleAllocation(aws, next);
                        });
        operationJoin.sendWith(this);
    }

    /*
     * Gets the provisioning task reference for this operation. Sets the task expiration time in the
     * context to be used for bounding the status checks for creation and termination requests.
     */
    private void getProvisioningTaskReference(AWSAllocation aws, AWSStages next) {
        Consumer<Operation> onSuccess = (op) -> {
            ProvisionComputeTaskState provisioningTaskState = op
                    .getBody(ProvisionComputeTaskState.class);
            aws.taskExpirationMicros = provisioningTaskState.documentExpirationTimeMicros;
            handleAllocation(aws, next);
        };
        AdapterUtils.getServiceState(this, aws.computeRequest.taskReference, onSuccess,
                getFailureConsumer(aws));
    }

    private Consumer<Throwable> getFailureConsumer(AWSAllocation aws) {
        return (t) -> handleError(aws, t);
    }

    /*
     * method will retrieve disks for targeted image
     */
    private void getVMDisks(AWSAllocation aws, AWSStages next) {
        if (aws.child.diskLinks == null || aws.child.diskLinks.size() == 0) {
            aws.error = new IllegalStateException(
                    "a minimum of 1 disk is required");
            aws.stage = AWSStages.ERROR;
            handleAllocation(aws);
            return;
        }
        Collection<Operation> operations = new ArrayList<>();
        // iterate thru disks and create operations
        for (String disk : aws.child.diskLinks) {
            operations.add(Operation.createGet(this.getHost(), disk));
        }

        OperationJoin operationJoin = OperationJoin.create(operations)
                .setCompletion(
                        (ops, exc) -> {
                            if (exc != null && exc.size() > 0) {
                                aws.error = new IllegalStateException(
                                        "Error getting disk information", exc.get(0));
                                aws.stage = AWSStages.ERROR;
                                handleAllocation(aws);
                                return;
                            }

                            aws.childDisks = new HashMap<>();
                            for (Operation op : ops.values()) {
                                DiskState disk = op.getBody(DiskState.class);
                                aws.childDisks.put(disk.type, disk);
                            }
                            aws.stage = next;
                            handleAllocation(aws);
                        });
        operationJoin.sendWith(this);
    }

    /*
     * method will retrieve firewalls for targeted image and set it to the primary NIC
     */
    private void getNICFirewallStates(AWSAllocation aws, AWSStages next) {
        if ( aws.nics.isEmpty() || aws.getVmPrimaryNic().nicStateWithDesc.firewallLinks == null ) {
            handleAllocation(aws, next);
            return;
        }

        Stream<Operation> firewallOps = aws.getVmPrimaryNic().nicStateWithDesc.firewallLinks
                .stream()
                .map(link -> Operation.createGet(this.getHost(), link));

        OperationJoin operationJoin = OperationJoin.create(firewallOps)
                .setCompletion(
                        (ops, exc) -> {
                            if (exc != null && exc.size() > 0) {
                                handleError(aws, new IllegalStateException("Error getting NIC Firewall States.", exc.get(0)));
                                return;
                            }

                            for (Operation op : ops.values()) {
                                FirewallState firewall = op.getBody(FirewallState.class);
                                // Populate all NICs with same Firewall state.
                                for (NicAllocationContext nicCtx : aws.nics) {
                                    nicCtx.firewallStates.put(firewall.name, firewall);
                                }
                            }
                            handleAllocation(aws, next);
                        });
        operationJoin.sendWith(this);
    }

    private void createInstance(AWSAllocation aws) {
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
            aws.error = new IllegalStateException(
                    "AWS Instance type not specified");
            aws.stage = AWSStages.ERROR;
            handleAllocation(aws);
            return;
        }

        RunInstancesRequest runInstancesRequest = new RunInstancesRequest()
                .withImageId(imageId.toString()).withInstanceType(instanceType)
                .withMinCount(1).withMaxCount(1)
                .withMonitoring(true)
                .withNetworkInterfaces(aws.getAWSNicSpecs());

        if (cloudConfig != null) {
            try {
                runInstancesRequest.setUserData(Base64.getEncoder()
                        .encodeToString(cloudConfig.getBytes(Utils.CHARSET)));
            } catch (UnsupportedEncodingException e) {
                aws.error = new IllegalStateException(
                        "Error encoding user data");
                aws.stage = AWSStages.ERROR;
                handleAllocation(aws);
                return;
            }
        }

        // handler invoked once the EC2 runInstancesAsync commands completes
        AsyncHandler<RunInstancesRequest, RunInstancesResult> creationHandler = buildCreationCallbackHandler(
                this, aws.computeRequest, aws.child, aws.amazonEC2Client,
                aws.taskExpirationMicros);
        aws.amazonEC2Client.runInstancesAsync(runInstancesRequest, creationHandler);
    }

    private static class AWSCreationHandler implements
            AsyncHandler<RunInstancesRequest, RunInstancesResult> {

        private StatelessService service;
        private ComputeInstanceRequest computeReq;
        private ComputeStateWithDescription computeDesc;
        private AmazonEC2AsyncClient amazonEC2Client;
        private OperationContext opContext;
        private long taskExpirationTimeMicros;

        private AWSCreationHandler(StatelessService service,
                ComputeInstanceRequest computeReq,
                ComputeStateWithDescription computeDesc,
                AmazonEC2AsyncClient amazonEC2Client, long taskExpirationTimeMicros) {
            this.service = service;
            this.computeReq = computeReq;
            this.computeDesc = computeDesc;
            this.amazonEC2Client = amazonEC2Client;
            this.opContext = OperationContext.getOperationContext();
            this.taskExpirationTimeMicros = taskExpirationTimeMicros;
        }

        @Override
        public void onError(Exception exception) {
            OperationContext.restoreOperationContext(this.opContext);
            AdapterUtils.sendFailurePatchToProvisioningTask(this.service,
                    this.computeReq.taskReference, exception);
        }

        @Override
        public void onSuccess(RunInstancesRequest request,
                RunInstancesResult result) {
            List<Operation> createOperations = new ArrayList<Operation>();
            // consumer to be invoked once a VM is in the running state
            Consumer<Instance> consumer = instance -> {
                OperationContext.restoreOperationContext(this.opContext);
                if (instance == null) {
                    AdapterUtils.sendFailurePatchToProvisioningTask(this.service,
                            this.computeReq.taskReference,
                            new IllegalStateException("Error getting instance EC2 instance"));
                    return;
                }

                ComputeState cs = new ComputeState();
                cs.id = instance.getInstanceId();
                cs.address = instance.getPublicIpAddress();
                cs.powerState = AWSUtils.mapToPowerState(instance.getState());
                if (this.computeDesc.customProperties == null) {
                    cs.customProperties = new HashMap<String, String>();
                } else {
                    cs.customProperties = this.computeDesc.customProperties;
                }
                cs.customProperties.put(SOURCE_TASK_LINK,
                        ProvisionComputeTaskService.FACTORY_LINK);
                cs.customProperties.put(AWSConstants.AWS_VPC_ID,
                        instance.getVpcId());
                cs.lifecycleState = LifecycleState.READY;
                // Create operations
                List<Operation> networkOperations = mapInstanceIPAddressToNICCreationOperations(
                        instance, cs, this.computeDesc.tenantLinks, this.service);
                if (networkOperations != null && !networkOperations.isEmpty()) {
                    createOperations.addAll(networkOperations);
                }
                Operation patchState = Operation
                        .createPatch(this.computeReq.resourceReference)
                        .setBody(cs)
                        .setReferer(this.service.getHost().getUri());
                createOperations.add(patchState);

                OperationJoin.JoinedCompletionHandler joinCompletion = (ox,
                        exc) -> {
                    if (exc != null) {
                        this.service.logSevere("Error updating VM state. %s", Utils.toString(exc));
                        AdapterUtils.sendFailurePatchToProvisioningTask(this.service,
                                this.computeReq.taskReference,
                                new IllegalStateException("Error updating VM state"));
                        return;
                    }
                    AdapterUtils.sendPatchToProvisioningTask(this.service,
                            this.computeReq.taskReference);
                };
                OperationJoin joinOp = OperationJoin.create(createOperations);
                joinOp.setCompletion(joinCompletion);
                joinOp.sendWith(this.service.getHost());
            };

            String instanceId = result.getReservation().getInstances().get(0)
                    .getInstanceId();

            tagInstanceAndStartStatusChecker(instanceId, this.computeDesc.tagLinks, consumer);
        }

        private void tagInstanceAndStartStatusChecker(String instanceId, Set<String> tagLinks,
                Consumer<Instance> consumer) {

            final List<Tag> tagsToCreate = new ArrayList<>();
            Tag nameTag = new Tag(AWSConstants.AWS_TAG_NAME, this.computeDesc.name);
            tagsToCreate.add(nameTag);

            Runnable proceed = () -> {
                AWSUtils.tagResources(this.amazonEC2Client, tagsToCreate, instanceId);

                AWSTaskStatusChecker
                        .create(this.amazonEC2Client, instanceId,
                                AWSInstanceService.AWS_RUNNING_NAME, consumer, this.computeReq,
                                this.service, this.taskExpirationTimeMicros)
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
                            this.computeReq.taskReference, exs.values().iterator().next());
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

    // callback to be invoked when a VM creation operation returns
    private AsyncHandler<RunInstancesRequest, RunInstancesResult> buildCreationCallbackHandler(
            StatelessService service, ComputeInstanceRequest computeReq,
            ComputeStateWithDescription computeDesc,
            AmazonEC2AsyncClient amazonEC2Client, long taskExpirationTimeMicros) {
        return new AWSCreationHandler(service, computeReq, computeDesc,
                amazonEC2Client, taskExpirationTimeMicros);
    }

    private void deleteInstance(AWSAllocation aws) {

        if (aws.computeRequest.isMockRequest) {
            AdapterUtils.sendPatchToProvisioningTask(this, aws.computeRequest.taskReference);
            return;
        }

        String instanceId = aws.child.id;
        if (instanceId == null) {
            aws.error = new IllegalStateException(
                    "AWS InstanceId not available");
            aws.stage = AWSStages.ERROR;
            handleAllocation(aws);
            return;
        }

        List<String> instanceIdList = new ArrayList<String>();
        instanceIdList.add(instanceId);
        TerminateInstancesRequest termRequest = new TerminateInstancesRequest(
                instanceIdList);
        StatelessService service = this;
        AsyncHandler<TerminateInstancesRequest, TerminateInstancesResult> terminateHandler = buildTerminationCallbackHandler(
                service, aws.computeRequest, aws.amazonEC2Client, instanceId,
                aws.taskExpirationMicros);
        aws.amazonEC2Client.terminateInstancesAsync(termRequest,
                terminateHandler);
    }

    private class AWSTerminateHandler implements
            AsyncHandler<TerminateInstancesRequest, TerminateInstancesResult> {

        private StatelessService service;
        private ComputeInstanceRequest computeReq;
        private AmazonEC2AsyncClient amazonEC2Client;
        private OperationContext opContext;
        private String instanceId;
        private long taskExpirationTimeMicros;

        private AWSTerminateHandler(StatelessService service,
                ComputeInstanceRequest computeReq,
                AmazonEC2AsyncClient amazonEC2Client, String instanceId,
                long taskExpirationTimeMicros) {
            this.service = service;
            this.computeReq = computeReq;
            this.amazonEC2Client = amazonEC2Client;
            this.opContext = OperationContext.getOperationContext();
            this.instanceId = instanceId;
            this.taskExpirationTimeMicros = taskExpirationTimeMicros;
        }

        @Override
        public void onError(Exception exception) {
            OperationContext.restoreOperationContext(this.opContext);
            AdapterUtils.sendFailurePatchToProvisioningTask(this.service,
                    this.computeReq.taskReference, exception);
        }

        @Override
        public void onSuccess(TerminateInstancesRequest request,
                TerminateInstancesResult result) {
            Consumer<Instance> consumer = new Consumer<Instance>() {

                @Override
                public void accept(Instance instance) {
                    OperationContext.restoreOperationContext(AWSTerminateHandler.this.opContext);
                    if (instance == null) {
                        AdapterUtils.sendFailurePatchToProvisioningTask(
                                AWSTerminateHandler.this.service,
                                AWSTerminateHandler.this.computeReq.taskReference,
                                new IllegalStateException("Error getting instance"));
                        return;
                    }
                    AdapterUtils.sendPatchToProvisioningTask(AWSTerminateHandler.this.service,
                            AWSTerminateHandler.this.computeReq.taskReference);
                }
            };
            AWSTaskStatusChecker.create(this.amazonEC2Client, this.instanceId,
                    AWSInstanceService.AWS_TERMINATED_NAME, consumer,
                    this.computeReq, this.service, this.taskExpirationTimeMicros).start();
        }
    }

    // callback handler to be invoked once a aws terminate calls returns
    private AsyncHandler<TerminateInstancesRequest, TerminateInstancesResult> buildTerminationCallbackHandler(
            StatelessService service, ComputeInstanceRequest computeReq,
            AmazonEC2AsyncClient amazonEC2Client, String instanceId,
            long taskExpirationTimeMicros) {
        return new AWSTerminateHandler(service, computeReq, amazonEC2Client, instanceId,
                taskExpirationTimeMicros);
    }

    /*
     * Simple helper method to get the region id from either the compute request or the child
     * description. The child description will be null during a credential validation operation.
     */
    private String getRequestRegionId(AWSAllocation aws) {
        String regionId;
        if (aws.child == null) {
            regionId = aws.computeRequest.regionId;
        } else {
            regionId = aws.child.description.regionId;
        }
        return regionId;
    }

    private void validateAWSCredentials(final AWSAllocation aws) {
        if (aws.computeRequest.isMockRequest || AWSUtils.isAwsClientMock()) {
            aws.awsOperation.complete();
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
                                        aws.awsOperation.fail(e, r);
                                        return;
                                    }
                                }

                                aws.awsOperation.fail(e);
                            }

                            @Override
                            public void onSuccess(DescribeAvailabilityZonesRequest request,
                                    DescribeAvailabilityZonesResult describeAvailabilityZonesResult) {
                                aws.awsOperation.complete();
                            }
                        });
    }
}
