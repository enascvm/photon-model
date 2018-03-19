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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.DeleteInternetGatewayRequest;
import com.amazonaws.services.ec2.model.DeleteNatGatewayRequest;
import com.amazonaws.services.ec2.model.DeleteNetworkAclRequest;
import com.amazonaws.services.ec2.model.DeleteNetworkInterfaceRequest;
import com.amazonaws.services.ec2.model.DeleteSecurityGroupRequest;
import com.amazonaws.services.ec2.model.DeleteSubnetRequest;
import com.amazonaws.services.ec2.model.DeleteVolumeRequest;
import com.amazonaws.services.ec2.model.DeleteVpcRequest;
import com.amazonaws.services.ec2.model.DeleteVpnGatewayRequest;
import com.amazonaws.services.ec2.model.DescribeAddressesRequest;
import com.amazonaws.services.ec2.model.DescribeAddressesResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeInternetGatewaysRequest;
import com.amazonaws.services.ec2.model.DescribeInternetGatewaysResult;
import com.amazonaws.services.ec2.model.DescribeNatGatewaysRequest;
import com.amazonaws.services.ec2.model.DescribeNatGatewaysResult;
import com.amazonaws.services.ec2.model.DescribeNetworkAclsRequest;
import com.amazonaws.services.ec2.model.DescribeNetworkAclsResult;
import com.amazonaws.services.ec2.model.DescribeNetworkInterfacesRequest;
import com.amazonaws.services.ec2.model.DescribeNetworkInterfacesResult;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult;
import com.amazonaws.services.ec2.model.DescribeSubnetsRequest;
import com.amazonaws.services.ec2.model.DescribeSubnetsResult;
import com.amazonaws.services.ec2.model.DescribeVolumesResult;
import com.amazonaws.services.ec2.model.DescribeVpcsResult;
import com.amazonaws.services.ec2.model.DescribeVpnGatewaysRequest;
import com.amazonaws.services.ec2.model.DescribeVpnGatewaysResult;
import com.amazonaws.services.ec2.model.DetachInternetGatewayRequest;
import com.amazonaws.services.ec2.model.DetachVpnGatewayRequest;
import com.amazonaws.services.ec2.model.DisassociateAddressRequest;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.ReleaseAddressRequest;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.TerminateInstancesResult;
import com.amazonaws.services.ec2.model.Volume;
import com.amazonaws.services.ec2.model.Vpc;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.Bucket;
import org.junit.Before;
import org.junit.Test;

import com.vmware.xenon.common.BasicTestCase;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class AWSRemoteCleanup extends BasicTestCase {
    public String accessKey = "accessKey";
    public String secretKey = "secretKey";
    public boolean isMock = true;
    public Map<String, AmazonS3Client> s3Clients = new HashMap<>();
    public Map<String, AmazonEC2> ec2Clients = new HashMap<>();
    public List<String> vpcTagsNotToBeDeleted = new ArrayList<>();

    public static final String US_EAST_1_TAG = "us-east-1";
    public static final String NAME_TAG_KEY = "name";
    public static final String ENUMTEST_VPC_TAG = "enumtest-vpc";
    public static final String DEFAULT_TAG = "default";
    public static final String DO_NOT_DELETE_TAG = "DoNotDelete";

    public static final String VPC_KEY = "vpc-id";
    public static final String ATTACHMENT_VPC_KEY = "attachment.vpc-id";
    public static final String NETWORK_INTERFACE_KEY = "network-interface-id";

    public static final String ENUMTEST_BUCKET = "enumtest-bucket";
    public static final String ENUMTEST_BUCKET_TAG  = "enumtest-bucket-do-not-delete";

    @Before
    public void setUp() {
        CommandLineArgumentParser.parseFromProperties(this);
        this.host.setTimeoutSeconds(600);

        AuthCredentialsServiceState creds = new AuthCredentialsServiceState();
        creds.privateKey = this.secretKey;
        creds.privateKeyId = this.accessKey;

        this.vpcTagsNotToBeDeleted.add(ENUMTEST_VPC_TAG);

        for (Regions region : Regions.values()) {
            try {
                this.s3Clients.put(region.getName(), AWSUtils.getS3Client(creds, region.getName()));
            } catch (Exception e) {
                continue;
            }
        }

        for (Regions region : Regions.values()) {
            try {
                this.ec2Clients.put(region.getName(), TestUtils.getEC2SynchronousClient(creds, region.getName()));
            } catch (Exception e) {
                continue;
            }
        }
    }

    @Test
    public void cleanUpAWSS3() {
        if (this.isMock) {
            return;
        }

        List<Bucket> buckets = this.s3Clients.get(Regions.DEFAULT_REGION.getName()).listBuckets();

        for (Bucket bucket : buckets) {
            long bucketCreationTimeMicros = TimeUnit
                    .MILLISECONDS
                    .toMicros(bucket.getCreationDate().getTime());

            long timeDifference = Utils.getNowMicrosUtc() - bucketCreationTimeMicros;

            if (bucket.getName().contains(ENUMTEST_BUCKET)
                    && timeDifference > TimeUnit.HOURS.toMicros(1)
                    && !bucket.getName().contains(ENUMTEST_BUCKET_TAG)) {
                for (AmazonS3Client s3Client : this.s3Clients.values()) {
                    try {
                        s3Client.deleteBucket(bucket.getName());
                        this.host.log(Level.INFO, "Deleting stale bucket %s", bucket.getName());
                    } catch (Exception e) {
                        continue;
                    }
                }
            }
        }
    }

    /**
     * Cleaning all VPC's that are not tagged with a name: enumtest-vpc or a default VPC in US_EAST_1 region
     * Deleting a VPC would require its dependencies to be deleted in the following order:
     * 1) EC2 Instances
     * 2) NAT Gateway
     * 3) Internet Gateway
     * 4) VPN Gateway
     * 5) Network ACL's
     * 6) Security Group ( not deleting default SG)
     * 7) Subnets
     * NOTE: Not deleting RouteTables currently
     */

    @Test
    public void cleanUpVpc() {
        if (this.isMock) {
            return;
        }

        AmazonEC2 usEastEc2Client = this.ec2Clients.get(US_EAST_1_TAG);
        DescribeVpcsResult vpcsResult = usEastEc2Client.describeVpcs();
        List<Vpc> vpcs = vpcsResult.getVpcs();
        List<String> vpcIdsToBeDeleted = new ArrayList<>();
        List<String> enumTestVpcIds = new ArrayList<>();

        try {
            vpcs.stream()
                    .forEach(vpc -> {
                        vpc.getTags().stream()
                                .filter(tag -> tag.getKey().equalsIgnoreCase(NAME_TAG_KEY)
                                        && this.vpcTagsNotToBeDeleted.contains(tag.getValue().toLowerCase()))
                                .forEach(tag -> enumTestVpcIds.add(vpc.getVpcId()));
                        if (!vpc.getIsDefault()) {
                            vpcIdsToBeDeleted.add(vpc.getVpcId());
                        }
                    });
            vpcIdsToBeDeleted.removeAll(enumTestVpcIds);

            vpcIdsToBeDeleted.stream()
                    .forEach(vpcId -> {
                        DescribeInstancesRequest instancesRequest = new DescribeInstancesRequest()
                                .withFilters(new Filter(VPC_KEY, Collections.singletonList(vpcId)));
                        DescribeInstancesResult instancesResult = usEastEc2Client.describeInstances(instancesRequest);
                        deleteAwsEc2instances(vpcIdsToBeDeleted, instancesResult, usEastEc2Client);
                        deleteNATGateway(vpcId, usEastEc2Client);
                        deleteNetworkInterfaces(vpcId, usEastEc2Client);
                        deleteInternetGateways(vpcId, usEastEc2Client);
                        deleteVirtualPrivateGateways(vpcId, usEastEc2Client);
                        disassociateAndDeleteNetworkACLs(vpcId, usEastEc2Client);
                        deleteSecurityGroups(vpcId, usEastEc2Client);
                        deleteSubnets(vpcId, usEastEc2Client);
                        DeleteVpcRequest deleteVpcRequest = new DeleteVpcRequest()
                                .withVpcId(vpcId);
                        this.host.log("Terminating stale vpc: %s", vpcId);
                        usEastEc2Client.deleteVpc(deleteVpcRequest);
                    });
        } catch (Exception e) {
            this.host.log(Level.INFO, e.getMessage());
        }
    }

    private void deleteAwsEc2instances(List<String> vpcIdsToBeDeleted, DescribeInstancesResult describeInstancesResult, AmazonEC2 ec2Client) {
        List<String> instanceIdsToBeDeleted = new ArrayList<>();
        List<Reservation> reservations = describeInstancesResult.getReservations();
        for (Reservation reservation : reservations) {
            List<Instance> instances = reservation.getInstances();
            for (Instance instance : instances) {
                long instanceLaunchTimeMicros = TimeUnit.MILLISECONDS
                        .toMicros(instance.getLaunchTime().getTime());
                long timeDifference = Utils.getNowMicrosUtc() - instanceLaunchTimeMicros;

                if (timeDifference > TimeUnit.HOURS.toMicros(1)
                        && vpcIdsToBeDeleted.contains(instance.getVpcId())
                        && shouldDelete(instance)) {
                    this.host.log(Level.INFO, "Marking %s instance for deletion", instance.getInstanceId());
                    instanceIdsToBeDeleted.add(instance.getInstanceId());
                }
            }
        }

        triggerEC2Deletion(instanceIdsToBeDeleted, ec2Client);
    }

    private void triggerEC2Deletion(List<String> instanceIdsToBeDeleted, AmazonEC2 ec2Client) {
        if (instanceIdsToBeDeleted.isEmpty()) {
            return;
        }

        TerminateInstancesRequest terminateInstancesRequest = new
                TerminateInstancesRequest(instanceIdsToBeDeleted);
        TerminateInstancesResult terminateInstancesResult = ec2Client
                .terminateInstances(terminateInstancesRequest);

        terminateInstancesResult.getTerminatingInstances().stream()
                .forEach(instanceStateChange -> {
                    this.host.log("Terminating stale instance: %s",
                            instanceStateChange.getInstanceId());
                });
    }

    /**
     * Delete stale AWS EC2 Instances.
     */
    @Test
    public void deleteStaleAwsEc2instances() {
        AmazonEC2 usEastEc2Client = this.ec2Clients.get(US_EAST_1_TAG);
        List<String> instanceIdsToBeDeleted = new ArrayList<>();
        DescribeInstancesResult instancesResult = usEastEc2Client.describeInstances();
        List<Reservation> reservations = instancesResult.getReservations();

        for (Reservation reservation : reservations) {
            List<Instance> instances = reservation.getInstances();
            for (Instance instance : instances) {
                long instanceLaunchTimeMicros = TimeUnit.MILLISECONDS
                        .toMicros(instance.getLaunchTime().getTime());
                long timeDifference = Utils.getNowMicrosUtc() - instanceLaunchTimeMicros;

                if (timeDifference > TimeUnit.HOURS.toMicros(1)
                        && shouldDelete(instance)) {
                    this.host.log(Level.INFO, "Marking %s instance for deletion", instance.getInstanceId());
                    instanceIdsToBeDeleted.add(instance.getInstanceId());
                }
            }
        }

        triggerEC2Deletion(instanceIdsToBeDeleted, usEastEc2Client);
    }

    @Test
    public void deleteStaleAwsVolumes() {
        AmazonEC2 usEastEc2Client = this.ec2Clients.get(US_EAST_1_TAG);
        DescribeVolumesResult volumesResult = usEastEc2Client.describeVolumes();
        List<Volume> volumeList = volumesResult.getVolumes();

        for (Volume volume : volumeList) {
            long volumeCreationTimeMicros = TimeUnit.MILLISECONDS
                    .toMicros(volume.getCreateTime().getTime());
            long timeDifference = Utils.getNowMicrosUtc() - volumeCreationTimeMicros;

            if (timeDifference > TimeUnit.HOURS.toMicros(1) && volume.getState()
                    .equalsIgnoreCase("available")) {
                this.host.log("Terminating stale volume: %s",
                        volume.getVolumeId());
                DeleteVolumeRequest deleteVolumeRequest = new DeleteVolumeRequest()
                        .withVolumeId(volume.getVolumeId());
                usEastEc2Client.deleteVolume(deleteVolumeRequest);
            }
        }
    }

    private static boolean shouldDelete(Instance instance) {
        for (Tag tag : instance.getTags()) {
            if (tag.getKey().equalsIgnoreCase(NAME_TAG_KEY)
                    && tag.getValue().equalsIgnoreCase(DO_NOT_DELETE_TAG)) {
                return false;
            }
        }
        return true;
    }


    private void disassociateAndDeleteNetworkACLs(String vpcId, AmazonEC2 usEastEc2Client) {
        DescribeNetworkAclsRequest networkAclsRequest = new DescribeNetworkAclsRequest()
                .withFilters(new Filter(VPC_KEY, Collections.singletonList(vpcId)));
        DescribeNetworkAclsResult networkAclsResult = usEastEc2Client.describeNetworkAcls(networkAclsRequest);
        networkAclsResult.getNetworkAcls().stream()
                .filter(networkAcl -> !(networkAcl.getIsDefault()))
                .forEach(networkAcl -> {
                    DeleteNetworkAclRequest deleteNetworkAclRequest = new DeleteNetworkAclRequest()
                            .withNetworkAclId(networkAcl.getNetworkAclId());
                    this.host.log("Terminating stale network acl: %s",
                            networkAcl.getNetworkAclId());
                    usEastEc2Client.deleteNetworkAcl(deleteNetworkAclRequest);
                });
    }

    private void deleteNetworkInterfaces(String vpcId, AmazonEC2 usEastEc2Client) {
        DescribeNetworkInterfacesRequest networkInterfacesRequest = new DescribeNetworkInterfacesRequest()
                .withFilters(new Filter(VPC_KEY,Collections.singletonList(vpcId)));
        DescribeNetworkInterfacesResult networkInterfacesResult = usEastEc2Client.describeNetworkInterfaces(networkInterfacesRequest);
        networkInterfacesResult.getNetworkInterfaces().forEach(networkInterface -> {
            DescribeAddressesRequest addressesRequest = new DescribeAddressesRequest()
                    .withFilters(new Filter(NETWORK_INTERFACE_KEY, Collections.singletonList(networkInterface.getNetworkInterfaceId())));
            DescribeAddressesResult addressResult =  usEastEc2Client.describeAddresses(addressesRequest);
            addressResult.getAddresses().forEach(address -> {
                // There is no hardcore dependency on EIP, but we may run out of addresses and
                // would be good to disassociate followed by releasing them.
                DisassociateAddressRequest disassociateAddressRequest = new DisassociateAddressRequest()
                        .withAssociationId(address.getAssociationId());
                usEastEc2Client.disassociateAddress(disassociateAddressRequest);
                ReleaseAddressRequest releaseAddressRequest = new ReleaseAddressRequest()
                        .withAllocationId(address.getAllocationId());
                usEastEc2Client.releaseAddress(releaseAddressRequest);
            });
            // Deleting Network Interfaces
            DeleteNetworkInterfaceRequest deleteNetworkInterfaceRequest = new DeleteNetworkInterfaceRequest()
                    .withNetworkInterfaceId(networkInterface.getNetworkInterfaceId());
            this.host.log("Terminating stale NIC: %s",
                    networkInterface.getNetworkInterfaceId());
            usEastEc2Client.deleteNetworkInterface(deleteNetworkInterfaceRequest);
        } );
    }

    private void deleteInternetGateways(String vpcId, AmazonEC2 usEastEc2Client) {
        DescribeInternetGatewaysRequest internetGatewaysRequest = new DescribeInternetGatewaysRequest()
                .withFilters(new Filter(ATTACHMENT_VPC_KEY, Collections.singletonList(vpcId)));
        DescribeInternetGatewaysResult internetGatewaysResult = usEastEc2Client.describeInternetGateways(internetGatewaysRequest);
        internetGatewaysResult.getInternetGateways().forEach(internetGateway -> {
            DetachInternetGatewayRequest detachInternetGatewayRequest = new DetachInternetGatewayRequest()
                    .withInternetGatewayId(internetGateway.getInternetGatewayId());
            detachInternetGatewayRequest.setVpcId(vpcId);
            usEastEc2Client.detachInternetGateway(detachInternetGatewayRequest);
            DeleteInternetGatewayRequest deleteInternetGatewayRequest = new DeleteInternetGatewayRequest()
                    .withInternetGatewayId(internetGateway.getInternetGatewayId());
            this.host.log("Terminating stale internet gateway: %s",
                    internetGateway.getInternetGatewayId());
            usEastEc2Client.deleteInternetGateway(deleteInternetGatewayRequest);
        });
    }

    private void deleteVirtualPrivateGateways(String vpcId, AmazonEC2 usEastEc2Client) {
        DescribeVpnGatewaysRequest vpnGatewaysRequest = new DescribeVpnGatewaysRequest()
                .withFilters(new Filter(ATTACHMENT_VPC_KEY, Collections.singletonList(vpcId)));
        DescribeVpnGatewaysResult vpnGatewaysResult = usEastEc2Client.describeVpnGateways(vpnGatewaysRequest);
        vpnGatewaysResult.getVpnGateways().forEach(vpnGateway -> {
            DetachVpnGatewayRequest detachVpnGatewayRequest = new DetachVpnGatewayRequest()
                    .withVpnGatewayId(vpnGateway.getVpnGatewayId());
            detachVpnGatewayRequest.setVpcId(vpcId);
            usEastEc2Client.detachVpnGateway(detachVpnGatewayRequest);
            DeleteVpnGatewayRequest deleteVpnGatewayRequest = new DeleteVpnGatewayRequest()
                    .withVpnGatewayId(detachVpnGatewayRequest.getVpnGatewayId());
            this.host.log("Terminating stale virtual private gateway: %s",
                    detachVpnGatewayRequest.getVpnGatewayId());
            usEastEc2Client.deleteVpnGateway(deleteVpnGatewayRequest);
        });
    }

    private void deleteNATGateway(String vpcId, AmazonEC2 usEastEc2Client) {
        DescribeNatGatewaysRequest natGatewaysRequest =  new DescribeNatGatewaysRequest()
                .withFilter(new Filter(VPC_KEY,Collections.singletonList(vpcId)));
        DescribeNatGatewaysResult natGatewaysResult = usEastEc2Client.describeNatGateways(natGatewaysRequest);
        natGatewaysResult.getNatGateways().forEach(natGateway -> {
            DeleteNatGatewayRequest deleteNatGatewayRequest = new DeleteNatGatewayRequest()
                    .withNatGatewayId(natGateway.getNatGatewayId());
            this.host.log("Terminating stale NAT gateway: %s",
                    natGateway.getNatGatewayId());
            usEastEc2Client.deleteNatGateway(deleteNatGatewayRequest);
        });
    }

    private void deleteSecurityGroups(String vpcId, AmazonEC2 usEastEc2Client) {
        DescribeSecurityGroupsRequest securityGroupsRequest = new DescribeSecurityGroupsRequest()
                .withFilters(new Filter(VPC_KEY, Collections.singletonList(vpcId)));
        DescribeSecurityGroupsResult securityGroupsResult = usEastEc2Client.describeSecurityGroups(securityGroupsRequest);
        securityGroupsResult.getSecurityGroups().forEach(securityGroup -> {
            if (!(securityGroup.getGroupName().equalsIgnoreCase(DEFAULT_TAG))) {
                DeleteSecurityGroupRequest deleteSecurityGroupRequest = new DeleteSecurityGroupRequest()
                        .withGroupId(securityGroup.getGroupId());
                this.host.log("Terminating stale security group: %s",
                        securityGroup.getGroupId());
                usEastEc2Client.deleteSecurityGroup(deleteSecurityGroupRequest);
            }
        });
    }

    private void deleteSubnets(String vpcId, AmazonEC2 usEastEc2Client) {
        DescribeSubnetsRequest subnetsRequest = new DescribeSubnetsRequest()
                .withFilters(new Filter(VPC_KEY, Collections.singletonList(vpcId)));
        DescribeSubnetsResult securityGroupsResult = usEastEc2Client.describeSubnets(subnetsRequest);
        securityGroupsResult.getSubnets().forEach(subnet -> {
            DeleteSubnetRequest deleteSubnetRequest = new DeleteSubnetRequest()
                    .withSubnetId(subnet.getSubnetId());
            this.host.log("Terminating stale subnet: %s",
                    subnet.getSubnetId());
            usEastEc2Client.deleteSubnet(deleteSubnetRequest);
        });
    }

}
