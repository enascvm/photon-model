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

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_SUBNET_ID_FILTER;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_VPC_ID_FILTER;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.DescribeSubnetsRequest;
import com.amazonaws.services.ec2.model.DescribeSubnetsResult;
import com.amazonaws.services.ec2.model.DescribeVpcsRequest;
import com.amazonaws.services.ec2.model.DescribeVpcsResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.InstanceNetworkInterfaceSpecification;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Vpc;

import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSDeferredResultAsyncHandler;
import com.vmware.photon.controller.model.adapters.util.instance.BaseComputeInstanceContext;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.DiskService.DiskType;
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
         * The list of security group ids that will be assigned to {@link #nicSpec}.
         */
        public List<String> securityGroupIds;

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
     * @see {@link #getVPCs(AWSInstanceContext)}
     * @see {@link #getSubnets(AWSInstanceContext)}
     * @see {@link #getOrCreateSecurityGroups(AWSInstanceContext)}
     * @see {@link #createNicSpecs(AWSInstanceContext)}
     */
    @Override
    protected DeferredResult<AWSInstanceContext> customizeContext(AWSInstanceContext context) {
        // The order of population is important!
        return DeferredResult.completed(context)

                .thenCompose(this::getDiskStates).thenApply(log("getDiskStates"))

                .thenCompose(this::getVPCs).thenApply(log("getVPCs"))
                .thenCompose(this::getSubnets).thenApply(log("getSubnets"))
                .thenCompose(this::getOrCreateSecurityGroups).thenApply(log("getOrCreateSecurityGroups"))
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

            AWSDeferredResultAsyncHandler<DescribeVpcsRequest, DescribeVpcsResult> handler =
                    new AWSDeferredResultAsyncHandler<DescribeVpcsRequest, DescribeVpcsResult>() {

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

            AWSDeferredResultAsyncHandler<DescribeSubnetsRequest, DescribeSubnetsResult> subnetHandler =
                    new AWSDeferredResultAsyncHandler<DescribeSubnetsRequest, DescribeSubnetsResult>() {

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
     * For every NIC {@link AWSUtils#getOrCreateSecurityGroups(AWSNicContext, AWSInstanceContext)
     * get or create security groups}.
     */
    private DeferredResult<AWSInstanceContext> getOrCreateSecurityGroups(
            AWSInstanceContext context) {

        for (AWSNicContext nicCtx : context.nics) {
            nicCtx.securityGroupIds = AWSUtils.getOrCreateSecurityGroups(
                    context, nicCtx);
        }

        return DeferredResult.completed(context);
    }

    /**
     * For every NIC creates the {@link InstanceNetworkInterfaceSpecification NIC spec} that should
     * be created by AWS during provisioning.
     */
    private DeferredResult<AWSInstanceContext> createNicSpecs(AWSInstanceContext context) {

        if (context.nics.isEmpty()) {
            return DeferredResult.completed(context);
        }

        for (AWSNicContext nicCtx : context.nics) {
            nicCtx.nicSpec = new InstanceNetworkInterfaceSpecification()
                    .withDeviceIndex(nicCtx.nicStateWithDesc.deviceIndex)
                    .withSubnetId(nicCtx.subnet.getSubnetId())
                    .withGroups(nicCtx.securityGroupIds);
        }

        /*
         * Important: When you add a second network interface, the system can no longer
         * auto-assign a public IPv4 address. You will not be able to connect to the instance
         * over IPv4 unless you assign an Elastic IP address to the primary network interface
         * (eth0). You can assign the Elastic IP address after you complete the Launch wizard.
         *
         * http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/MultipleIP.html
         */
        // For now assign public IP (to primary NIC) only for SIGLE-NIC VMs!
        if (context.nics.size() == 1) {
            context.getPrimaryNic().nicSpec.withAssociatePublicIpAddress(true);
        }

        return DeferredResult.completed(context);
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
                            .thenAccept(diskState -> context.childDisks.put(diskState.type, diskState));
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
