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

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;

import static com.vmware.photon.controller.model.ComputeProperties.CREATE_CONTEXT_PROP_NAME;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_GROUP_NAME_FILTER;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_SUBNET_ID_FILTER;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_VPC_ID_FILTER;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.buildRules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupEgressRequest;
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupEgressResult;
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest;
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressResult;
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest;
import com.amazonaws.services.ec2.model.CreateSecurityGroupResult;
import com.amazonaws.services.ec2.model.CreateSubnetRequest;
import com.amazonaws.services.ec2.model.CreateSubnetResult;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult;
import com.amazonaws.services.ec2.model.DescribeSubnetsRequest;
import com.amazonaws.services.ec2.model.DescribeSubnetsResult;
import com.amazonaws.services.ec2.model.DescribeVpcsRequest;
import com.amazonaws.services.ec2.model.DescribeVpcsResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.InstanceNetworkInterfaceSpecification;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Vpc;

import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSDeferredResultAsyncHandler;
import com.vmware.photon.controller.model.adapters.util.instance.BaseComputeInstanceContext;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.DiskService.DiskType;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;

/**
 * AWS context used by {@link AWSInstanceService} during VM provisioning.
 */
public class AWSInstanceContext
        extends BaseComputeInstanceContext<AWSInstanceContext, AWSInstanceContext.AWSNicContext> {

    /**
     * The class encapsulates NIC related data (both Photon Model and AWS model) used during
     * provisioning.
     */
    public static class AWSNicContext extends BaseComputeInstanceContext.BaseNicContext {

        /**
         * The AWS VPC this NIC is associated to. It is looked up by this service.
         */
        public Vpc vpc;

        /**
         * The AWS subnet this NIC is associated to. It is either looked up from AWS or created by
         * this service.
         */
        public Subnet subnet;

        /**
         * The Map of security group names by Ids that will be assigned to {@link #nicSpec}.
         */
        public Map<String, String> securityGroupNamesByIds = new HashMap<>();

        /**
         * The NIC spec that should be created by AWS.
         */
        public InstanceNetworkInterfaceSpecification nicSpec;
    }

    public AWSInstanceStage stage;

    public AmazonEC2AsyncClient amazonEC2Client;

    public Map<DiskType, DiskState> childDisks = new HashMap<>();

    public long taskExpirationMicros;

    public AWSInstanceContext(StatelessService service, ComputeInstanceRequest computeRequest) {
        super(service, computeRequest, AWSNicContext::new);
    }

    /**
     * Get the effective list of AWS NIC Specs that should be created during VM provisioning.
     */
    public List<InstanceNetworkInterfaceSpecification> getAWSNicSpecs() {
        return this.nics.stream()
                .map(nic -> nic.nicSpec)
                .collect(Collectors.toList());
    }

    /**
     * Populate context with VPC, Subnet and Security Group objects from AWS.
     *
     * @see #getVPCs(AWSInstanceContext)
     * @see #getSubnets(AWSInstanceContext)
     * @see #getSecurityGroups(AWSInstanceContext)
     * @see #createSecurityGroupsIfNotExist(AWSInstanceContext)
     * @see #createNicSpecs(AWSInstanceContext)
     */
    @Override
    protected DeferredResult<AWSInstanceContext> customizeContext(AWSInstanceContext context) {
        // The order of population is important!
        return DeferredResult.completed(context)

                .thenCompose(this::getDiskStates).thenApply(log("getDiskStates"))

                .thenCompose(this::getVPCs).thenApply(log("getVPCs"))

                .thenCompose(this::getSubnets).thenApply(log("getSubnets"))
                .thenCompose(this::createSubnetsIfNotExist)
                .thenApply(log("createSubnetsIfNotExist"))

                .thenCompose(this::getSecurityGroups).thenApply(log("getSecurityGroups"))
                .thenCompose(this::createSecurityGroupsIfNotExist)
                .thenApply(log("createSecurityGroupsIfNotExist"))

                .thenCompose(this::createNicSpecs).thenApply(log("createNicSpecs"));
    }

    /**
     * For every NIC lookup associated AWS VPC as specified by
     * {@code AWSNicContext.networkState.id}. If any of the VPCs is not found then complete with an
     * exception.
     */
    private DeferredResult<AWSInstanceContext> getVPCs(AWSInstanceContext context) {
        if (context.nics.isEmpty()) {
            return DeferredResult.completed(context);
        }

        List<DeferredResult<DescribeVpcsResult>> getVpcDRs = new ArrayList<>();

        for (AWSNicContext nicCtx : context.nics) {

            DescribeVpcsRequest vpcRequest = new DescribeVpcsRequest()
                    .withFilters(
                            new Filter(AWS_VPC_ID_FILTER, singletonList(nicCtx.networkState.id)));

            String msg = "Getting AWS VPC ["
                    + nicCtx.networkState.id + "/"
                    + nicCtx.networkState.name + "/"
                    + "] for [" + nicCtx.nicStateWithDesc.name + "] NIC for ["
                    + context.child.name
                    + "] VM";

            AWSDeferredResultAsyncHandler<DescribeVpcsRequest, DescribeVpcsResult> handler = new AWSDeferredResultAsyncHandler<DescribeVpcsRequest, DescribeVpcsResult>(
                    this.service, msg) {

                @Override
                protected DeferredResult<DescribeVpcsResult> consumeSuccess(
                        DescribeVpcsRequest request,
                        DescribeVpcsResult result) {

                    if (result.getVpcs().isEmpty()) {
                        String msg = String.format(
                                "VPC with [%s] id is not found in AWS for [%s] NIC of [%s] VM.",
                                nicCtx.networkState.id,
                                nicCtx.nicStateWithDesc.name,
                                context.child.name);
                        return DeferredResult.failed(new IllegalStateException(msg));
                    }

                    nicCtx.vpc = result.getVpcs().get(0);
                    return DeferredResult.completed(result);
                }
            };
            context.amazonEC2Client.describeVpcsAsync(vpcRequest, handler);

            getVpcDRs.add(handler.toDeferredResult());
        }

        return DeferredResult.allOf(getVpcDRs).handle((all, exc) -> {
            if (exc != null) {
                String msg = String.format(
                        "Error getting VPCs from AWS for [%s] VM.",
                        context.child.name);
                throw new IllegalStateException(msg, exc);
            }
            return context;
        });
    }

    /**
     * For every NIC lookup associated AWS Subnet as specified by
     * {@code AWSNicContext.subnetState.id}. If any of the subnets is not found then
     * {@code AWSNicContext.subnet} is not populated. That's an indicator the subnet should be
     * created.
     */
    private DeferredResult<AWSInstanceContext> getSubnets(AWSInstanceContext context) {

        if (context.nics.isEmpty()) {
            return DeferredResult.completed(context);
        }

        List<DeferredResult<DescribeSubnetsResult>> getSubnetDRs = new ArrayList<>();

        for (AWSNicContext nicCtx : context.nics) {

            DescribeSubnetsRequest subnetRequest = new DescribeSubnetsRequest()
                    .withFilters(
                            new Filter(AWS_VPC_ID_FILTER, singletonList(nicCtx.networkState.id)))
                    .withFilters(
                            new Filter(AWS_SUBNET_ID_FILTER, singletonList(nicCtx.subnetState.id)));

            String msg = "Getting AWS Subnet ["
                    + nicCtx.networkState.id + "/"
                    + nicCtx.subnetState.id
                    + "] for [" + nicCtx.nicStateWithDesc.name + "] NIC for ["
                    + context.child.name
                    + "] VM";

            AWSDeferredResultAsyncHandler<DescribeSubnetsRequest, DescribeSubnetsResult> subnetHandler = new AWSDeferredResultAsyncHandler<DescribeSubnetsRequest, DescribeSubnetsResult>(
                    this.service, msg) {

                @Override
                protected DeferredResult<DescribeSubnetsResult> consumeSuccess(
                        DescribeSubnetsRequest request,
                        DescribeSubnetsResult result) {

                    // The subnet specified might not exist. It's OK cause it will be created.
                    if (!result.getSubnets().isEmpty()) {
                        nicCtx.subnet = result.getSubnets().get(0);
                    }

                    return DeferredResult.completed(result);
                }
            };
            context.amazonEC2Client.describeSubnetsAsync(subnetRequest, subnetHandler);

            getSubnetDRs.add(subnetHandler.toDeferredResult());
        }

        return DeferredResult.allOf(getSubnetDRs).handle((all, exc) -> {
            if (exc != null) {
                String msg = String.format(
                        "Error getting Subnets from AWS for [%s] VM.",
                        context.child.name);
                throw new IllegalStateException(msg, exc);
            }
            return context;
        });
    }

    /**
     * For every NIC create AWS Subnet (as specified by {@code AWSNicContext.subnetState}) if it
     * does not exist.
     *
     * @see #getSubnets(AWSInstanceContext)
     */
    private DeferredResult<AWSInstanceContext> createSubnetsIfNotExist(AWSInstanceContext context) {

        if (context.nics.isEmpty()) {
            return DeferredResult.completed(context);
        }

        List<DeferredResult<Void>> createSubnetDRs = new ArrayList<>();

        for (AWSNicContext nicCtx : context.nics) {

            if (nicCtx.subnet != null) {
                // No need to create
                continue;
            }

            // Create AWS subnet and set it to nicCtx.subnet {{
            CreateSubnetRequest subnetRequest = new CreateSubnetRequest()
                    .withVpcId(nicCtx.vpc.getVpcId())
                    .withCidrBlock(nicCtx.subnetState.subnetCIDR);

            if (nicCtx.subnetState.zoneId != null) {
                subnetRequest.withAvailabilityZone(nicCtx.subnetState.zoneId);
            }

            String msg = "Create AWS subnet + [" + nicCtx.subnetState.name + "]";
            AWSDeferredResultAsyncHandler<CreateSubnetRequest, CreateSubnetResult> createAWSSubnet = new AWSDeferredResultAsyncHandler<CreateSubnetRequest, CreateSubnetResult>(
                    this.service, msg) {

                @Override
                protected DeferredResult<CreateSubnetResult> consumeSuccess(
                        CreateSubnetRequest request,
                        CreateSubnetResult result) {

                    nicCtx.subnet = result.getSubnet();

                    AWSUtils.tagResourcesWithName(
                            context.amazonEC2Client,
                            nicCtx.subnetState.name,
                            nicCtx.subnet.getSubnetId());

                    return DeferredResult.completed(result);
                }
            };
            context.amazonEC2Client.createSubnetAsync(subnetRequest, createAWSSubnet);
            // }}

            // Once AWS subnet creation is done PATCH SubnetState.id {{
            Function<CreateSubnetResult, DeferredResult<Void>> patchSubnetState = (ignore) -> {

                SubnetState patchSubnet = new SubnetState();
                patchSubnet.id = nicCtx.subnet.getSubnetId();
                patchSubnet.documentSelfLink = nicCtx.subnetState.documentSelfLink;
                patchSubnet.customProperties = singletonMap(CREATE_CONTEXT_PROP_NAME,
                        context.computeRequest.resourceLink());

                Operation op = Operation.createPatch(
                        context.service.getHost(),
                        patchSubnet.documentSelfLink).setBody(patchSubnet);

                return context.service.sendWithDeferredResult(op, SubnetState.class)
                        // Update NicContext with patched SubnetState
                        .thenAccept(patchedSubnet -> nicCtx.subnetState = patchedSubnet);
            };
            // }}

            // Chain AWS subnet creation with SubnetState patching
            createSubnetDRs.add(createAWSSubnet.toDeferredResult().thenCompose(patchSubnetState));
        }

        return DeferredResult.allOf(createSubnetDRs).handle((all, exc) -> {
            if (exc != null) {
                String msg = String.format(
                        "Error creating Subnets in AWS for [%s] VM.",
                        context.child.name);
                throw new IllegalStateException(msg, exc);
            }
            return context;
        });
    }

    /**
     * For every NIC creates the {@link InstanceNetworkInterfaceSpecification NIC spec} that should
     * be created by AWS during provisioning.
     */
    private DeferredResult<AWSInstanceContext> createNicSpecs(AWSInstanceContext context) {

        /*
         * Important AWS note: When you add a second network interface, the AWS can no longer
         * auto-assign a public IPv4 address. You will not be able to connect to the instance over
         * IPv4 unless you assign an Elastic IP address to the primary network interface (eth0). You
         * can assign the Elastic IP address after you complete the Launch wizard.
         *
         * http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/MultipleIP.html
         */

        // For now create InstanceNetworkInterfaceSpecification _ONLY_ for primary NIC.
        // Other NICs are just _IGNORED_.
        AWSNicContext primaryNic = getPrimaryNic();

        if (primaryNic != null) {
            // For now if not specified default to TRUE!
            if (primaryNic.nicStateWithDesc.description.assignPublicIpAddress == null) {
                primaryNic.nicStateWithDesc.description.assignPublicIpAddress = Boolean.TRUE;
            }

            primaryNic.nicSpec = new InstanceNetworkInterfaceSpecification()
                    .withDeviceIndex(primaryNic.nicStateWithDesc.deviceIndex)
                    .withSubnetId(primaryNic.subnet.getSubnetId())
                    .withGroups(primaryNic.securityGroupNamesByIds.keySet())
                    .withAssociatePublicIpAddress(
                            primaryNic.nicStateWithDesc.description.assignPublicIpAddress);
        }

        return DeferredResult.completed(context);
    }

    /**
     * For every NIC's security group states obtain existing SecurityGroup objects from AWS and
     * store their IDs and Names in context.
     */
    private DeferredResult<AWSInstanceContext> getSecurityGroups(AWSInstanceContext context) {

        if (context.nics.isEmpty()) {
            return DeferredResult.completed(context);
        }

        List<DeferredResult<DescribeSecurityGroupsResult>> getSecurityGroupsDRs = new ArrayList<>();

        for (AWSNicContext nicCtx : context.nics) {
            getSecurityGroupsDRs.add(getSecurityGroupsPerNIC(context, nicCtx));
        }

        return DeferredResult.allOf(getSecurityGroupsDRs).handle((all, exc) -> {
            if (exc != null) {
                String msg = String.format(
                        "Error getting SecurityGroups from AWS for [%s] VM.",
                        context.child.name);
                throw new IllegalStateException(msg, exc);
            }
            return context;
        });
    }

    /**
     * Utility method for obtaining AWS SecurityGroup objects from AWS based on SecurityGroupStates.
     */
    private DeferredResult<DescribeSecurityGroupsResult> getSecurityGroupsPerNIC(
            AWSInstanceContext context, AWSNicContext nicCtx) {

        if (nicCtx.securityGroupStates == null || nicCtx.securityGroupStates.isEmpty()) {
            return DeferredResult.completed(null);
        }

        List<String> securityGroupNames = nicCtx.securityGroupStates.stream()
                .map(securityGroupState -> securityGroupState.name)
                .collect(Collectors.toList());

        DescribeSecurityGroupsRequest req = new DescribeSecurityGroupsRequest()
                .withFilters(new Filter(AWS_GROUP_NAME_FILTER, securityGroupNames))
                .withFilters(new Filter(AWS_VPC_ID_FILTER, singletonList(nicCtx.vpc.getVpcId())));

        String msg = "Getting AWS Security Groups by name ["
                + securityGroupNames
                + "] for [" + nicCtx.nicStateWithDesc.name + "] NIC for ["
                + context.child.name
                + "] VM";

        AWSDeferredResultAsyncHandler<DescribeSecurityGroupsRequest, DescribeSecurityGroupsResult> asyncHandler = new AWSDeferredResultAsyncHandler<DescribeSecurityGroupsRequest, DescribeSecurityGroupsResult>(
                this.service, msg) {

            @Override
            protected DeferredResult<DescribeSecurityGroupsResult> consumeSuccess(
                    DescribeSecurityGroupsRequest request,
                    DescribeSecurityGroupsResult result) {

                nicCtx.securityGroupNamesByIds = result.getSecurityGroups()
                        .stream()
                        .collect(Collectors.toMap(sg -> sg.getGroupId(), sg -> sg.getGroupName()));

                return DeferredResult.completed(result);
            }
        };

        context.amazonEC2Client.describeSecurityGroupsAsync(req, asyncHandler);

        return asyncHandler.toDeferredResult();
    }

    /**
     * When there are SecurityGroupStates for the new VM to be provisioned, for which there are no
     * corresponding existing SecurityGroups in AWS, the missing SecurityGroups are created
     */
    private DeferredResult<AWSInstanceContext> createSecurityGroupsIfNotExist(
            AWSInstanceContext context) {

        if (context.nics.isEmpty()) {
            return DeferredResult.completed(context);
        }

        List<DeferredResult<Void>> createSecurityGroupsDRs = new ArrayList<>();

        for (AWSNicContext nicCtx : context.nics) {
            if (nicCtx.securityGroupStates == null) {
                continue;
            }

            Collection<String> foundNames = nicCtx.securityGroupNamesByIds.values();

            List<SecurityGroupState> missingSecurityGroupStates = nicCtx.securityGroupStates
                    .stream()
                    .filter(sgState -> sgState.name != null && !foundNames.contains(sgState.name))
                    .collect(Collectors.toList());

            for (SecurityGroupState missingSGState : missingSecurityGroupStates) {

                DeferredResult<CreateSecurityGroupResult> createSGDR = createSecurityGroup(
                        context, nicCtx, missingSGState);

                DeferredResult<AuthorizeSecurityGroupIngressResult> createIngressRulesDR = createIngressRules(
                        context, nicCtx, missingSGState);

                DeferredResult<AuthorizeSecurityGroupEgressResult> createEgressRulesDR = createEgressRules(
                        context, nicCtx, missingSGState);

                DeferredResult<Void> createSGWithRulesDR = createSGDR
                        .thenCompose(ignore -> createIngressRulesDR)
                        .thenCompose(ignore -> createEgressRulesDR)
                        .thenApply(ignore -> (Void) null);

                createSecurityGroupsDRs.add(createSGWithRulesDR);
            }
        }

        return DeferredResult.allOf(createSecurityGroupsDRs).handle((all, exc) -> {
            if (exc != null) {
                String msg = String.format(
                        "Error creating SecurityGroups in AWS for [%s] VM.",
                        context.child.name);
                throw new IllegalStateException(msg, exc);
            }
            return context;
        });
    }

    /**
     * For the provided SecurityGroupState, create corresponding SecurityGroup on AWS.
     */
    private DeferredResult<CreateSecurityGroupResult> createSecurityGroup(
            AWSInstanceContext context,
            AWSNicContext nicCtx,
            SecurityGroupState missingSecurityGroupState) {

        CreateSecurityGroupRequest req = new CreateSecurityGroupRequest()
                .withDescription(missingSecurityGroupState.name)
                .withGroupName(missingSecurityGroupState.name)
                .withVpcId(nicCtx.vpc.getVpcId());

        String msg = "Create AWS Security Group [" + missingSecurityGroupState.name + "]";

        AWSDeferredResultAsyncHandler<CreateSecurityGroupRequest, CreateSecurityGroupResult> asyncHandler = new AWSDeferredResultAsyncHandler<CreateSecurityGroupRequest, CreateSecurityGroupResult>(
                this.service, msg) {

            @Override
            protected DeferredResult<CreateSecurityGroupResult> consumeSuccess(
                    CreateSecurityGroupRequest request,
                    CreateSecurityGroupResult result) {

                nicCtx.securityGroupNamesByIds.put(result.getGroupId(), request.getGroupName());

                return DeferredResult.completed(result);
            }
        };

        context.amazonEC2Client.createSecurityGroupAsync(req, asyncHandler);

        return asyncHandler.toDeferredResult();
    }

    private DeferredResult<AuthorizeSecurityGroupIngressResult> createIngressRules(
            AWSInstanceContext context, AWSNicContext nicCtx, SecurityGroupState missingSGState) {

        String provisinedGroupId = nicCtx.securityGroupNamesByIds
                .entrySet().stream()
                .filter(idToName -> idToName.getValue().equals(missingSGState.name))
                .map(idToName -> idToName.getKey())
                .findFirst().orElse(null);

        if (provisinedGroupId == null) {
            return DeferredResult.completed(null);
        }

        List<IpPermission> ingressRules = buildRules(missingSGState.ingress);

        AuthorizeSecurityGroupIngressRequest req = new AuthorizeSecurityGroupIngressRequest()
                .withGroupId(provisinedGroupId)
                .withIpPermissions(ingressRules);

        String msg = "Create AWS Ingress rules for [" + missingSGState.name + "] Security Group";

        AWSDeferredResultAsyncHandler<AuthorizeSecurityGroupIngressRequest, AuthorizeSecurityGroupIngressResult> asyncHandler = new AWSDeferredResultAsyncHandler<AuthorizeSecurityGroupIngressRequest, AuthorizeSecurityGroupIngressResult>(
                this.service, msg) {

            @Override
            protected DeferredResult<AuthorizeSecurityGroupIngressResult> consumeSuccess(
                    AuthorizeSecurityGroupIngressRequest request,
                    AuthorizeSecurityGroupIngressResult result) {

                return DeferredResult.completed(result);
            }
        };

        context.amazonEC2Client.authorizeSecurityGroupIngressAsync(req, asyncHandler);

        return asyncHandler.toDeferredResult();
    }

    private DeferredResult<AuthorizeSecurityGroupEgressResult> createEgressRules(
            AWSInstanceContext context, AWSNicContext nicCtx, SecurityGroupState missingSGState) {

        String sgId = nicCtx.securityGroupNamesByIds
                .entrySet().stream()
                .filter(idToName -> idToName.getValue().equals(missingSGState.name))
                .map(idToName -> idToName.getKey())
                .findFirst().orElse(null);

        if (sgId == null) {
            return DeferredResult.completed(null);
        }

        List<IpPermission> egressRules = buildRules(missingSGState.egress);

        AuthorizeSecurityGroupEgressRequest req = new AuthorizeSecurityGroupEgressRequest()
                .withGroupId(sgId)
                .withIpPermissions(egressRules);

        String msg = "Create AWS Egress rules for [" + missingSGState.name + "] Security Group";

        AWSDeferredResultAsyncHandler<AuthorizeSecurityGroupEgressRequest, AuthorizeSecurityGroupEgressResult> asyncHandler = new AWSDeferredResultAsyncHandler<AuthorizeSecurityGroupEgressRequest, AuthorizeSecurityGroupEgressResult>(
                this.service, msg) {

            @Override
            protected DeferredResult<AuthorizeSecurityGroupEgressResult> consumeSuccess(
                    AuthorizeSecurityGroupEgressRequest request,
                    AuthorizeSecurityGroupEgressResult result) {

                return DeferredResult.completed(result);
            }
        };

        context.amazonEC2Client.authorizeSecurityGroupEgressAsync(req, asyncHandler);

        return asyncHandler.toDeferredResult();
    }

    /**
     * Get Disk states assigned to the compute state we are provisioning.
     */
    private DeferredResult<AWSInstanceContext> getDiskStates(AWSInstanceContext context) {

        if (context.child.diskLinks == null || context.child.diskLinks.isEmpty()) {
            String msg = String.format(
                    "A minimum of 1 disk is required for [%s] VM.",
                    context.child.name);
            return DeferredResult.failed(new IllegalStateException(msg));
        }

        List<DeferredResult<Void>> getStatesDR = context.child.diskLinks.stream()
                .map(diskStateLink -> {
                    Operation op = Operation.createGet(context.service.getHost(), diskStateLink);

                    return context.service
                            .sendWithDeferredResult(op, DiskState.class)
                            .thenAccept(
                                    diskState -> context.childDisks.put(diskState.type, diskState));
                })
                .collect(Collectors.toList());

        return DeferredResult.allOf(getStatesDR).handle((all, exc) -> {
            if (exc != null) {
                String msg = String.format(
                        "Error getting Disk states for [%s] VM.",
                        context.child.name);
                throw new IllegalStateException(msg, exc);
            }
            return context;
        });
    }

}
