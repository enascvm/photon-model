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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_VPC_ID_FILTER;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.DISK_IOPS;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.getQueryResultLimit;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.getAWSNonTerminatedInstancesFilter;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.getRegionId;
import static com.vmware.photon.controller.model.tasks.ProvisioningUtils.createServiceURI;
import static com.vmware.photon.controller.model.tasks.ProvisioningUtils.getVMCount;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.AttachInternetGatewayRequest;
import com.amazonaws.services.ec2.model.AttachNetworkInterfaceRequest;
import com.amazonaws.services.ec2.model.AttachNetworkInterfaceResult;
import com.amazonaws.services.ec2.model.BlockDeviceMapping;
import com.amazonaws.services.ec2.model.CreateNetworkInterfaceRequest;
import com.amazonaws.services.ec2.model.CreateNetworkInterfaceResult;
import com.amazonaws.services.ec2.model.CreateSnapshotRequest;
import com.amazonaws.services.ec2.model.CreateSnapshotResult;
import com.amazonaws.services.ec2.model.CreateSubnetRequest;
import com.amazonaws.services.ec2.model.CreateSubnetResult;
import com.amazonaws.services.ec2.model.CreateVolumeRequest;
import com.amazonaws.services.ec2.model.CreateVolumeResult;
import com.amazonaws.services.ec2.model.CreateVpcRequest;
import com.amazonaws.services.ec2.model.DeleteInternetGatewayRequest;
import com.amazonaws.services.ec2.model.DeleteNetworkInterfaceRequest;
import com.amazonaws.services.ec2.model.DeleteSecurityGroupRequest;
import com.amazonaws.services.ec2.model.DeleteSnapshotRequest;
import com.amazonaws.services.ec2.model.DeleteSubnetRequest;
import com.amazonaws.services.ec2.model.DeleteVolumeRequest;
import com.amazonaws.services.ec2.model.DeleteVpcRequest;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusRequest;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult;
import com.amazonaws.services.ec2.model.DescribeSnapshotsRequest;
import com.amazonaws.services.ec2.model.DescribeSnapshotsResult;
import com.amazonaws.services.ec2.model.DescribeSubnetsRequest;
import com.amazonaws.services.ec2.model.DescribeSubnetsResult;
import com.amazonaws.services.ec2.model.DescribeVolumesRequest;
import com.amazonaws.services.ec2.model.DescribeVolumesResult;
import com.amazonaws.services.ec2.model.DetachInternetGatewayRequest;
import com.amazonaws.services.ec2.model.DetachNetworkInterfaceRequest;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceNetworkInterface;
import com.amazonaws.services.ec2.model.ModifyNetworkInterfaceAttributeRequest;
import com.amazonaws.services.ec2.model.NetworkInterfaceAttachmentChanges;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.Snapshot;
import com.amazonaws.services.ec2.model.StopInstancesRequest;
import com.amazonaws.services.ec2.model.StopInstancesResult;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.TerminateInstancesResult;
import com.amazonaws.services.ec2.model.Volume;
import com.amazonaws.services.ec2.model.Vpc;

import org.joda.time.LocalDateTime;

import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.AwsNicSpecs.NetSpec;
import com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.AwsNicSpecs.NicSpec;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSComputeDescriptionEnumerationAdapterService;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSComputeStateCreationAdapterService;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSEBSStorageEnumerationAdapterService;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSEnumerationAdapterService;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSEnumerationAndCreationAdapterService;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSEnumerationAndDeletionAdapterService;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSS3StorageEnumerationAdapterService;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSSecurityGroupClient;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ImageService;
import com.vmware.photon.controller.model.resources.ImageService.ImageState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.IpAssignment;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.SecurityGroupService;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState.Rule;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService.ResourceEnumerationTaskState;
import com.vmware.photon.controller.model.tasks.ResourceRemovalTaskService;
import com.vmware.photon.controller.model.tasks.ResourceRemovalTaskService.ResourceRemovalTaskState;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.photon.controller.model.tasks.monitoring.StatsAggregationTaskService;
import com.vmware.photon.controller.model.tasks.monitoring.StatsAggregationTaskService.StatsAggregationTaskState;
import com.vmware.photon.controller.model.tasks.monitoring.StatsCollectionTaskService;
import com.vmware.photon.controller.model.tasks.monitoring.StatsCollectionTaskService.StatsCollectionTaskState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;

public class TestAWSSetupUtils {

    public static final String awsEndpointReference = "http://ec2.us-east-1.amazonaws.com";
    public static final String imageId = "ami-0d4cfd66";
    public static final String securityGroup = "aws-security-group";
    public static final String instanceType_t2_micro = "t2.micro";
    public static final String regionId = "us-east-1";
    public static final String zoneId = "us-east-1";
    public static final String avalabilityZoneIdentifier = "a";
    public static final String userData = null;

    public static final long BOOT_DISK_SIZE_IN_MEBI_BYTES = 16 * 1024L;
    public static final long ADDITIONAL_DISK_SIZE_IN_MEBI_BYTES = 16 * 1024L;
    private static final String DEVICE_TYPE = "deviceType";
    private static final String VOLUME_TYPE = "volumeType";
    private static final String IOPS = "iops";

    // VPC/subnet details are copy-pasted from AWS, region N.Virginia, Availability Zone: us-east-1a
    // {{
    public static final String AWS_DEFAULT_VPC_ID = "vpc-95a29bf1";
    public static final String AWS_DEFAULT_VPC_CIDR = "172.31.0.0/16";

    // Default Subnet; auto-assign public IP
    public static final String AWS_DEFAULT_SUBNET_ID = "subnet-ce01b5e4";
    public static final String AWS_DEFAULT_SUBNET_NAME = "default";
    public static final String AWS_DEFAULT_SUBNET_CIDR = "172.31.48.0/20";

    // Non-default Subnet; auto-assign public IP = false
    private static final String AWS_SECONDARY_SUBNET_ID = "subnet-e91b87c4";
    private static final String AWS_SECONDARY_SUBNET_NAME = "secondary";
    private static final String AWS_SECONDARY_SUBNET_CIDR = "172.31.64.0/20";
    // }}

    // Used to create and delete after that to test subnet states enumeration
    public static final String AWS_SUBNET_TO_DELETE_CIDR = "172.31.96.0/20";

    public static final String AWS_NON_EXISTING_SUBNET_CIDR = "172.31.80.0/20";
    public static final String AWS_NON_EXISTING_SUBNET_NAME = "nonexisting";

    public static final String VPC_KEY = "vpc-id";
    public static final String SUBNET_KEY = "subnet-id";
    public static final String INTERNET_GATEWAY_KEY = "internet-gateway";
    public static final String NIC_SPECS_KEY = "nicSpecs";
    public static final String SECURITY_GROUP_KEY = "security-group";

    public static final String SNAPSHOT_KEY = "snapshot-id";
    public static final String DISK_KEY = "disk-id";

    public static final String VOLUME_ID_ATTRIBUTE = "volume-id";
    public static final String SNAPSHOT_ID_ATTRIBUTE = "snapshot-id";
    public static final String VOLUME_STATUS_AVAILABLE = "available";
    public static final String SNAPSHOT_STATUS_COMPLETE = "completed";

    /**
     * Return two-NIC spec where first NIC should be assigned to 'secondary' subnet and second NIC
     * should be assigned to a randomly generated subnet that should be created.
     * <p>
     * For a matrix of maximum supported NICs per Instance type check:
     * http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/using-eni.html#AvailableIpPerENI
     *
     * @see {@value #instanceType_t2_micro} instance type
     */
    public static AwsNicSpecs multiNicSpecs() throws UnknownHostException {

        String nextSubnetIp;
        {
            // 172.31.1000 0000.0000 0000
            final String startingSubnetIp = "172.31.128.0";

            Random r = new Random();

            // Convert IP as String -to-> byte[4] -to-> IP as integer
            int startingIpAsInt = ByteBuffer
                    .wrap(InetAddress.getByName(startingSubnetIp).getAddress())
                    .getInt();

            /*
             * The random subnet generated is between 172.31.1[000 0000.0000] 0000 and 172.31.1[111
             * 1111.1111] 0000. The last 4 zero bits are for the IPs within the subnet generated.
             */
            int nextSubnetIpAsInt = startingIpAsInt | (r.nextInt(1 << 11) << 4);

            // Convert IP as integer -to-> byte[4] -to-> IP as String
            nextSubnetIp = InetAddress
                    .getByAddress(ByteBuffer.allocate(4).putInt(nextSubnetIpAsInt).array())
                    .getHostAddress();
        }

        NetSpec network = new NetSpec(
                AWS_DEFAULT_VPC_ID,
                AWS_DEFAULT_VPC_ID,
                AWS_DEFAULT_VPC_CIDR);

        // Configure with TWO NICS.
        List<NetSpec> subnets = new ArrayList<>();

        // 'secondary' subnet which is existing
        subnets.add(new NetSpec(AWS_SECONDARY_SUBNET_ID,
                AWS_SECONDARY_SUBNET_NAME,
                AWS_SECONDARY_SUBNET_CIDR,
                zoneId + avalabilityZoneIdentifier));

        // Random generated subnet with /28 mask to be created
        subnets.add(new NetSpec(null,
                "third",
                nextSubnetIp + "/28",
                zoneId + avalabilityZoneIdentifier));

        NicSpec nicSpec = NicSpec.create()
                .withSubnetSpec(subnets.get(0))
                .withStaticIpAssignment();

        return new AwsNicSpecs(network, Collections.singletonList(nicSpec));
    }

    /**
     * Single-NIC spec where the NIC should be assigned to 'default' subnet.
     */
    public static final AwsNicSpecs SINGLE_NIC_SPEC;

    static {
        NetSpec network = new NetSpec(
                AWS_DEFAULT_VPC_ID,
                AWS_DEFAULT_VPC_ID,
                AWS_DEFAULT_VPC_CIDR);

        List<NetSpec> subnets = new ArrayList<>();

        subnets.add(new NetSpec(AWS_DEFAULT_SUBNET_ID,
                AWS_DEFAULT_SUBNET_NAME,
                AWS_DEFAULT_SUBNET_CIDR,
                zoneId + avalabilityZoneIdentifier));

        NicSpec nicSpec = NicSpec.create()
                .withSubnetSpec(subnets.get(0))
                .withDynamicIpAssignment();

        SINGLE_NIC_SPEC = new AwsNicSpecs(network, Collections.singletonList(nicSpec));
    }

    // Next change: use this also by AzureTestUtil.createDefaultNicStates.
    public static class AwsNicSpecs {

        public static class NetSpec {

            public final String id;
            public final String name;
            public final String cidr;
            public final String zoneId;

            public NetSpec(String id, String name, String cidr, String zoneId) {
                this.id = id;
                this.name = name;
                this.cidr = cidr;
                this.zoneId = zoneId;
            }

            public NetSpec(String id, String name, String cidr) {
                this(id, name, cidr, null);
            }
        }

        public static class NicSpec {

            private IpAssignment ipAssignment;
            private NetSpec subnetSpec;
            private String staticIp;

            public static NicSpec create() {
                return new NicSpec();
            }

            public NicSpec withSubnetSpec(NetSpec subnetSpec) {
                this.subnetSpec = subnetSpec;
                return this;
            }

            public NicSpec withStaticIpAssignment() {

                if (this.subnetSpec == null) {
                    throw new IllegalArgumentException("'subnetSpec' can not be empty!");
                }

                this.ipAssignment = IpAssignment.STATIC;

                try {
                    this.staticIp = generateBaseOnCIDR();
                } catch (UnknownHostException e) {
                    throw new IllegalArgumentException(e);
                }
                return this;
            }

            public NicSpec withDynamicIpAssignment() {
                this.ipAssignment = IpAssignment.DYNAMIC;
                this.staticIp = null;
                return this;
            }

            public String staticIp() {
                return this.staticIp;
            }

            public IpAssignment getIpAssignment() {
                return this.ipAssignment;
            }

            private String generateBaseOnCIDR() throws UnknownHostException {
                assertNotNull(this.subnetSpec.cidr);
                String[] startingIpAndRange = this.subnetSpec.cidr.split("/");
                assertEquals(2, startingIpAndRange.length);
                String startingIp = startingIpAndRange[0];
                String range = startingIpAndRange[1];
                String staticIp = generateIp(startingIp, range);
                return staticIp;
            }

            private String generateIp(String startingIp, String range) throws UnknownHostException {
                Random r = new Random();
                int startingIpAsInt = ByteBuffer
                        .wrap(InetAddress.getByName(startingIp).getAddress())
                        .getInt();

                int min = 3;
                int max = Integer.valueOf(range);
                // To prevent Address 172.31.48.0 is in subnet's reserved address range
                int nextSubnetIpAsInt = startingIpAsInt | (r.nextInt((max - 1) - (min + 1)) + min);
                return InetAddress
                        .getByAddress(ByteBuffer.allocate(4).putInt(nextSubnetIpAsInt).array())
                        .getHostAddress();
            }

            private NicSpec() {
                // Leave the initialization to create() method.
            }
        }

        public final NetSpec network;
        public final List<NicSpec> nicSpecs;

        public AwsNicSpecs(NetSpec network, List<NicSpec> nicSpecs) {
            this.network = network;
            this.nicSpecs = nicSpecs;
        }

        public final int numberOfNics() {
            return this.nicSpecs.size();
        }
    }

    public static void setUpTestVolume(VerificationHost host, AmazonEC2AsyncClient client,
            Map<String, Object> awsTestContext, boolean isMock) {
        if (!isMock) {
            String volumeId = createVolume(host, client);
            awsTestContext.put(DISK_KEY, volumeId);
            String snapshotId = createSnapshot(host, client, volumeId);
            awsTestContext.put(SNAPSHOT_KEY, snapshotId);
        }
    }

    public static void setUpTestVpc(AmazonEC2AsyncClient client, Map<String, Object> awsTestContext, boolean isMock) {
        awsTestContext.put(VPC_KEY, AWS_DEFAULT_VPC_ID);
        awsTestContext.put(NIC_SPECS_KEY, SINGLE_NIC_SPEC);
        awsTestContext.put(SUBNET_KEY, AWS_DEFAULT_SUBNET_ID);
        awsTestContext.put(SECURITY_GROUP_KEY, AWS_DEFAULT_GROUP_ID);

        // create new VPC, Subnet, InternetGateway if the default VPC doesn't exist
        if (!isMock && !vpcIdExists(client, AWS_DEFAULT_VPC_ID)) {
            String vpcId = createVPC(client, AWS_DEFAULT_VPC_CIDR);
            awsTestContext.put(VPC_KEY, vpcId);
            String subnetId = createOrGetSubnet(client, AWS_DEFAULT_VPC_CIDR, vpcId);
            awsTestContext.put(SUBNET_KEY, subnetId);

            String internetGatewayId = createInternetGateway(client);
            awsTestContext.put(INTERNET_GATEWAY_KEY, internetGatewayId);
            attachInternetGateway(client, vpcId, internetGatewayId);
            awsTestContext.put(SECURITY_GROUP_KEY, new AWSSecurityGroupClient(client)
                    .createDefaultSecurityGroup(vpcId));

            NetSpec network = new NetSpec(vpcId, vpcId, AWS_DEFAULT_VPC_CIDR);

            List<NetSpec> subnets = new ArrayList<>();

            subnets.add(new NetSpec(subnetId,
                    AWS_DEFAULT_SUBNET_NAME,
                    AWS_DEFAULT_SUBNET_CIDR,
                    zoneId + avalabilityZoneIdentifier));

            NicSpec nicSpec = NicSpec.create()
                    .withSubnetSpec(subnets.get(0))
                    .withDynamicIpAssignment();

            awsTestContext.put(NIC_SPECS_KEY, new AwsNicSpecs(network, Collections.singletonList(nicSpec)));
        }
    }

    public static void tearDownTestVpc(
            AmazonEC2AsyncClient client, VerificationHost host,
            Map<String, Object> awsTestContext, boolean isMock) {
        if (!isMock && !vpcIdExists(client, AWS_DEFAULT_VPC_ID)) {
            final String vpcId = (String) awsTestContext.get(VPC_KEY);
            final String subnetId = (String) awsTestContext.get(SUBNET_KEY);
            final String internetGatewayId = (String) awsTestContext.get(INTERNET_GATEWAY_KEY);
            final String securityGroupId = (String) awsTestContext.get(SECURITY_GROUP_KEY);
            // clean up VPC and all its dependencies if creating one at setUp
            deleteSecurityGroupUsingEC2Client(client, host, securityGroupId);
            SecurityGroup securityGroup = new AWSSecurityGroupClient(client)
                    .getSecurityGroup(AWS_DEFAULT_GROUP_NAME, vpcId);
            if (securityGroup != null) {
                deleteSecurityGroupUsingEC2Client(client, host, securityGroup.getGroupId());
            }
            deleteSubnet(client, subnetId);
            detachInternetGateway(client, vpcId, internetGatewayId);
            deleteInternetGateway(client, internetGatewayId);
            deleteVPC(client, vpcId);
        }
    }

    public static void tearDownTestDisk(
            AmazonEC2AsyncClient client, VerificationHost host,
            Map<String, Object> awsTestContext, boolean isMock) {
        if (awsTestContext.containsKey(DISK_KEY)) {
            String volumeId = awsTestContext.get(DISK_KEY).toString();
            if (!isMock) {
                deleteVolume(client, volumeId);
            }
            awsTestContext.remove(DISK_KEY);
        }
        if (awsTestContext.containsKey(SNAPSHOT_KEY)) {
            String snapshotId = awsTestContext.get(SNAPSHOT_KEY).toString();
            if (!isMock) {
                deleteSnapshot(client, snapshotId);
            }
            awsTestContext.remove(SNAPSHOT_KEY);
        }
    }

    /**
     * Creates a VPC and returns the VPC id.
     */
    public static String createVPC(AmazonEC2AsyncClient client, String subnetCidr) {
        return client.createVpc(new CreateVpcRequest().withCidrBlock(subnetCidr)).getVpc().getVpcId();
    }

    /**
     * Delete a VPC
     */
    public static void deleteVPC(AmazonEC2AsyncClient client, String vpcId) {
        client.deleteVpc(new DeleteVpcRequest().withVpcId(vpcId));
    }

    /**
     * Return true if vpcId exists.
     */
    public static boolean vpcIdExists(AmazonEC2AsyncClient client, String vpcId) {
        List<Vpc> vpcs = client.describeVpcs()
                .getVpcs()
                .stream()
                .filter(vpc -> vpc.getVpcId().equals(vpcId))
                .collect(Collectors.toList());
        return vpcs != null && !vpcs.isEmpty();
    }

    /**
     * Return true if volumeId exists.
     */
    public static boolean volumeIdExists(AmazonEC2AsyncClient client, String volumeId) {
        List<Volume> volumes = client.describeVolumes()
                .getVolumes()
                .stream()
                .filter(volume -> volume.getVolumeId().equals(volumeId))
                .collect(Collectors.toList());
        return volumes != null && !volumes.isEmpty();
    }

    /**
     * Return true if snapshotId exists.
     */
    public static boolean snapshotIdExists(AmazonEC2AsyncClient client, String snapshotId) {
        List<Snapshot> snapshots = client.describeSnapshots()
                .getSnapshots()
                .stream()
                .filter(snapshot -> snapshot.getSnapshotId().equals(snapshotId))
                .collect(Collectors.toList());
        return snapshots != null && !snapshots.isEmpty();
    }

    /**
     * Creates a Subnet if not exist and return the Subnet id.
     */
    public static String createOrGetSubnet(AmazonEC2AsyncClient client, String subnetCidr,
            String vpcId) {
        Filter cidrBlockFilter = new Filter();
        cidrBlockFilter.withName("cidrBlock");
        cidrBlockFilter.withValues(subnetCidr);
        DescribeSubnetsResult result = client.describeSubnets(new DescribeSubnetsRequest()
                .withFilters(cidrBlockFilter));
        if (result.getSubnets() != null && !result.getSubnets().isEmpty()) {
            return result.getSubnets().get(0).getSubnetId();
        } else {
            CreateSubnetRequest req = new CreateSubnetRequest()
                    .withCidrBlock(subnetCidr)
                    .withVpcId(vpcId);
            CreateSubnetResult res = client.createSubnet(req);
            return res.getSubnet().getSubnetId();
        }
    }

    /**
     * Creates a volume and return the volume id.
     */
    public static String createVolume(VerificationHost host, AmazonEC2Client client) {
        CreateVolumeRequest req = new CreateVolumeRequest()
                .withAvailabilityZone(zoneId + avalabilityZoneIdentifier)
                .withSize(1);
        CreateVolumeResult res = client.createVolume(req);
        String volumeId = res.getVolume().getVolumeId();
        Filter filter = new Filter().withName(VOLUME_ID_ATTRIBUTE).withValues(volumeId);

        DescribeVolumesRequest volumesRequest = new DescribeVolumesRequest()
                .withVolumeIds(volumeId)
                .withFilters(filter);

        host.waitFor("Timeout waiting for creating volume", () -> {
            DescribeVolumesResult volumesResult = client.describeVolumes(volumesRequest);
            String state = volumesResult.getVolumes().get(0).getState();
            if (state.equalsIgnoreCase(VOLUME_STATUS_AVAILABLE)) {
                return true;
            }
            return false;
        });
        return volumeId;
    }

    /**
     * Creates a snapshot and return the snapshot id.
     */
    public static String createSnapshot(VerificationHost host, AmazonEC2Client client, String volumeId) {
        CreateSnapshotRequest req = new CreateSnapshotRequest()
                .withVolumeId(volumeId);
        CreateSnapshotResult res = client.createSnapshot(req);
        String snapshotId = res.getSnapshot().getSnapshotId();
        Filter filter = new Filter().withName(SNAPSHOT_ID_ATTRIBUTE).withValues(snapshotId);

        DescribeSnapshotsRequest snapshotsRequest = new DescribeSnapshotsRequest()
                .withSnapshotIds(snapshotId)
                .withFilters(filter);
        host.waitFor("Timeout waiting for creating snapshot", () -> {
            DescribeSnapshotsResult snapshotsResult = client.describeSnapshots(snapshotsRequest);
            String state = snapshotsResult.getSnapshots().get(0).getState();
            if (state.equalsIgnoreCase(SNAPSHOT_STATUS_COMPLETE)) {
                return true;
            }
            return false;
        });
        return snapshotId;
    }

    /**
     * Delete a Subnet
     */
    public static void deleteSubnet(AmazonEC2AsyncClient client, String subnetId) {
        if (subnetId != null) {
            client.deleteSubnet(new DeleteSubnetRequest().withSubnetId(subnetId));
        }
    }

    /**
     * Delete a Volume
     */
    public static void deleteVolume(AmazonEC2AsyncClient client, String diskId) {
        client.deleteVolume(new DeleteVolumeRequest().withVolumeId(diskId));
    }

    /**
     * Delete a Snapshot
     */
    public static void deleteSnapshot(AmazonEC2AsyncClient client, String snapshotId) {
        client.deleteSnapshot(new DeleteSnapshotRequest().withSnapshotId(snapshotId));
    }

    /**
     * Creates an Internet Gateway and return the Internet Gateway id.
     */
    public static String createInternetGateway(AmazonEC2AsyncClient client) {
        return client.createInternetGateway().getInternetGateway().getInternetGatewayId();
    }

    /**
     * Attach an Internet Gateway to a VPC.
     */
    public static void attachInternetGateway(AmazonEC2AsyncClient client, String vpcId, String internetGatewayId) {
        client.attachInternetGateway(
                new AttachInternetGatewayRequest()
                        .withVpcId(vpcId)
                        .withInternetGatewayId(internetGatewayId));
    }

    /**
     * Delete an Internet Gateway.
     */
    public static void deleteInternetGateway(AmazonEC2AsyncClient client, String internetGatewayId) {
        client.deleteInternetGateway(new DeleteInternetGatewayRequest().withInternetGatewayId(internetGatewayId));
    }

    /**
     * Detach an Internet Gateway to a VPC.
     */
    public static void detachInternetGateway(AmazonEC2AsyncClient client, String vpcId, String internetGatewayId) {
        client.detachInternetGateway(new DetachInternetGatewayRequest()
                .withVpcId(vpcId)
                .withInternetGatewayId(internetGatewayId));
    }

    /**
     * Get a list of all EC2 instance ids associated with a given VPC id.
     */
    public static List<String> getEC2InstanceIdsAssociatedWithVpcId(AmazonEC2AsyncClient client, String vpcId) {
        DescribeInstancesRequest req = new DescribeInstancesRequest();
        if (vpcId != null) {
            req.withFilters(new Filter(AWS_VPC_ID_FILTER, Collections.singletonList(vpcId)));
        }

        DescribeInstancesResult instancesResult = client.describeInstances(req);
        return instancesResult == null ? Collections.emptyList()
                : instancesResult.getReservations().get(0).getInstances().stream()
                .map(instance -> instance.getInstanceId())
                .collect(Collectors.toList());

    }

    public static final String AWS_DEFAULT_GROUP_NAME = "cell-manager-security-group";
    public static final String AWS_DEFAULT_GROUP_ID = "sg-2616c559";
    public static final String AWS_NEW_GROUP_PREFIX = "test-new-";

    public static final String DEFAULT_AUTH_TYPE = "PublicKey";
    public static final String DEFAULT_ROOT_DISK_NAME = "CoreOS root disk";
    public static final String DEFAULT_CONFIG_LABEL = "cidata";
    public static final String DEFAULT_CONFIG_PATH = "user-data";
    public static final String DEFAULT_USER_DATA_FILE = "cloud_config_coreos.yml";
    public static final String DEFAULT_COREOS_USER = "core";
    public static final String DEFAULT_COREOS_PRIVATE_KEY_FILE = "private_coreos.key";
    public static final String SAMPLE_AWS_BILL = "123456789-aws-billing-detailed-line-items-with-resources-and-tags-2016-09.csv.zip";

    public static final String T2_NANO_INSTANCE_TYPE = "t2.nano";
    public static final String BASELINE_INSTANCE_COUNT = "Baseline Instance Count ";
    public static final String BASELINE_COMPUTE_DESCRIPTION_COUNT = " Baseline Compute Description Count ";
    private static final float HUNDERED = 100.0f;
    public static final int AWS_VM_REQUEST_TIMEOUT_MINUTES = 5;
    public static final String AWS_INSTANCE_PREFIX = "i-";

    public static final String EC2_LINUX_AMI = "ami-0d4cfd66";
    public static final String EC2_WINDOWS_AMI = "ami-b6af04a0";

    /**
     * Class to hold the baseline counts for the compute states and the compute descriptions that
     * are present on the AWS endpoint before enumeration starts.
     */
    public static class BaseLineState {
        public int baselineVMCount;
        public int baselineComputeDescriptionCount;
        public boolean isCountPopulated;

        public BaseLineState() {
            this.baselineVMCount = 0;
            this.baselineComputeDescriptionCount = 0;
            this.isCountPopulated = false;
        }

        @Override
        public String toString() {
            StringBuffer sb = new StringBuffer();
            sb.append(BASELINE_INSTANCE_COUNT).append(this.baselineVMCount)
                    .append(BASELINE_COMPUTE_DESCRIPTION_COUNT)
                    .append(this.baselineComputeDescriptionCount);
            return sb.toString();
        }
    }


    public static AuthCredentialsServiceState createAWSAuthentication(VerificationHost host, String accessKey, String secretKey) throws Throwable {

        AuthCredentialsServiceState auth = new AuthCredentialsServiceState();
        auth.type = DEFAULT_AUTH_TYPE;
        auth.privateKeyId = accessKey;
        auth.privateKey = secretKey;

        return TestUtils.doPost(host, auth, AuthCredentialsService.AuthCredentialsServiceState.class,
                UriUtils.buildUri(host, AuthCredentialsService.FACTORY_LINK));
    }

    /**
     * Create a compute host description for an AWS instance
     */
    public static ComputeState createAWSComputeHost(VerificationHost host,
            EndpointState endpointState, String zoneId, String regionId,
            boolean isAwsClientMock,
            String awsMockEndpointReference, Set<String> tagLinks)
            throws Throwable {

        ComputeDescription awsComputeHostDesc = new ComputeDescription();

        awsComputeHostDesc.id = UUID.randomUUID().toString();
        awsComputeHostDesc.documentSelfLink = awsComputeHostDesc.id;
        awsComputeHostDesc.name = ComputeDescription.ENVIRONMENT_NAME_AWS;
        awsComputeHostDesc.environmentName = ComputeDescription.ENVIRONMENT_NAME_AWS;
        awsComputeHostDesc.supportedChildren = new ArrayList<String>();
        awsComputeHostDesc.supportedChildren.add(ComputeType.VM_GUEST.name());
        awsComputeHostDesc.instanceAdapterReference = UriUtils.buildUri(host,
                AWSUriPaths.AWS_INSTANCE_ADAPTER);
        awsComputeHostDesc.enumerationAdapterReference = UriUtils.buildUri(host,
                AWSUriPaths.AWS_ENUMERATION_ADAPTER);
        awsComputeHostDesc.statsAdapterReference = UriUtils.buildUri(host,
                AWSUriPaths.AWS_STATS_ADAPTER);

        awsComputeHostDesc.zoneId = zoneId;
        awsComputeHostDesc.regionId = regionId;
        awsComputeHostDesc.authCredentialsLink = endpointState.authCredentialsLink;
        awsComputeHostDesc.tenantLinks = endpointState.tenantLinks;

        awsComputeHostDesc = TestUtils.doPost(host, awsComputeHostDesc,
                ComputeDescriptionService.ComputeDescription.class,
                UriUtils.buildUri(host, ComputeDescriptionService.FACTORY_LINK));

        ComputeState awsComputeHost = new ComputeState();

        awsComputeHost.id = UUID.randomUUID().toString();
        awsComputeHost.documentSelfLink = awsComputeHost.id;
        awsComputeHost.name = awsComputeHostDesc.name;
        awsComputeHost.type = ComputeType.VM_HOST;
        awsComputeHost.environmentName = ComputeDescription.ENVIRONMENT_NAME_AWS;
        awsComputeHost.descriptionLink = awsComputeHostDesc.documentSelfLink;
        awsComputeHost.tagLinks = tagLinks;
        awsComputeHost.resourcePoolLink = endpointState.resourcePoolLink;

        awsComputeHost.regionId = regionId;
        awsComputeHost.endpointLink = endpointState.documentSelfLink;
        awsComputeHost.tenantLinks = endpointState.tenantLinks;

        if (isAwsClientMock) {
            awsComputeHost.adapterManagementReference = UriUtils.buildUri(awsMockEndpointReference);
        } else {
            awsComputeHost.adapterManagementReference = UriUtils.buildUri(awsEndpointReference);
        }

        return TestUtils.doPost(host, awsComputeHost,
                ComputeService.ComputeState.class,
                UriUtils.buildUri(host, ComputeService.FACTORY_LINK));
    }

    public static EndpointState createAWSEndpointState(VerificationHost host, String authLink, String resPoolLink)
            throws Throwable {

        EndpointState endpoint = new EndpointState();

        endpoint.endpointType = EndpointType.aws.name();
        endpoint.id = EndpointType.aws.name() + "-id";
        endpoint.name = EndpointType.aws.name() + "-name";

        endpoint.authCredentialsLink = authLink;

        endpoint.endpointProperties = Collections.emptyMap();

        endpoint.tenantLinks = Collections.singletonList(EndpointType.aws.name() + "-tenant");

        endpoint.resourcePoolLink = resPoolLink;

        return TestUtils.doPost(host, endpoint, EndpointState.class,
                UriUtils.buildUri(host, EndpointService.FACTORY_LINK));
    }


    public static ResourcePoolState createAWSResourcePool(VerificationHost host)
            throws Throwable {
        ResourcePoolState inPool = new ResourcePoolState();
        inPool.name = UUID.randomUUID().toString();
        inPool.id = inPool.name;

        inPool.minCpuCount = 1L;
        inPool.minMemoryBytes = 1024L;

        ResourcePoolState returnPool = TestUtils.doPost(host, inPool, ResourcePoolState.class,
                UriUtils.buildUri(host, ResourcePoolService.FACTORY_LINK));

        return returnPool;
    }

    /**
     * Create a compute resource for an AWS instance
     */
    public static ComputeService.ComputeState createAWSVMResource(VerificationHost host,
            ComputeState computeHost, EndpointState endpointState, @SuppressWarnings("rawtypes") Class clazz,
            String zoneId, String regionId,
            Set<String> tagLinks, AwsNicSpecs nicSpec)
            throws Throwable {
        return createAWSVMResource(host, computeHost, endpointState, clazz,
                instanceType_t2_micro, zoneId, regionId,
                tagLinks, nicSpec, /* add new security group */ false);
    }

    /**
     * Create a compute resource for an AWS instance
     */
    public static ComputeService.ComputeState createAWSVMResource(VerificationHost host,
            ComputeState computeHost, EndpointState endpointState,
            @SuppressWarnings("rawtypes") Class clazz,
            String vmName, String zoneId, String regionId,
            Set<String> tagLinks,
            AwsNicSpecs nicSpecs,
            boolean addNewSecurityGroup)
            throws Throwable {

        // Step 1: Create an auth credential to login to the VM
        AuthCredentialsServiceState auth = new AuthCredentialsServiceState();
        auth.type = DEFAULT_AUTH_TYPE;
        auth.userEmail = DEFAULT_COREOS_USER;
        auth.privateKey = TestUtils.loadTestResource(clazz, DEFAULT_COREOS_PRIVATE_KEY_FILE);

        auth = TestUtils.doPost(host, auth, AuthCredentialsServiceState.class,
                UriUtils.buildUri(host, AuthCredentialsService.FACTORY_LINK));

        // Step 2: Create a VM desc
        ComputeDescription awsVMDesc = new ComputeDescription();

        awsVMDesc.id = instanceType_t2_micro;
        awsVMDesc.name = vmName;
        awsVMDesc.environmentName = ComputeDescription.ENVIRONMENT_NAME_AWS;
        awsVMDesc.instanceType = instanceType_t2_micro;

        awsVMDesc.supportedChildren = new ArrayList<>();
        awsVMDesc.supportedChildren.add(ComputeType.DOCKER_CONTAINER.name());

        awsVMDesc.customProperties = new HashMap<>();
        awsVMDesc.customProperties.put(AWSConstants.AWS_SECURITY_GROUP, securityGroup);

        // set zone to east
        awsVMDesc.zoneId = zoneId;
        awsVMDesc.regionId = regionId;

        awsVMDesc.authCredentialsLink = auth.documentSelfLink;
        awsVMDesc.tenantLinks = endpointState.tenantLinks;
        awsVMDesc.endpointLink = endpointState.documentSelfLink;

        // set the create service to the aws instance service
        awsVMDesc.instanceAdapterReference = UriUtils.buildUri(host,
                AWSUriPaths.AWS_INSTANCE_ADAPTER);
        awsVMDesc.statsAdapterReference = UriUtils.buildUri(host,
                AWSUriPaths.AWS_STATS_ADAPTER);

        awsVMDesc = TestUtils.doPost(host,
                awsVMDesc,
                ComputeDescription.class,
                UriUtils.buildUri(host, ComputeDescriptionService.FACTORY_LINK));

        // Step 3: create boot disk
        List<String> vmDisks = new ArrayList<>();

        ImageState bootImage;
        {
            // Create PUBLIC image state
            bootImage = new ImageState();
            bootImage.id = imageId;
            bootImage.endpointType = endpointState.endpointType;
            bootImage.regionId = regionId;

            bootImage = TestUtils.doPost(host, bootImage, ImageState.class,
                    UriUtils.buildUri(host, ImageService.FACTORY_LINK));
        }

        DiskState rootDisk = new DiskState();
        rootDisk.id = UUID.randomUUID().toString();
        rootDisk.documentSelfLink = rootDisk.id;
        rootDisk.name = DEFAULT_ROOT_DISK_NAME;
        rootDisk.bootOrder = 1;
        rootDisk.sourceImageReference = URI.create(imageId);
        rootDisk.imageLink = bootImage.documentSelfLink;
        rootDisk.bootConfig = new DiskState.BootConfig();
        rootDisk.bootConfig.label = DEFAULT_CONFIG_LABEL;
        DiskState.BootConfig.FileEntry file = new DiskState.BootConfig.FileEntry();
        file.path = DEFAULT_CONFIG_PATH;
        file.contents = TestUtils.loadTestResource(clazz, DEFAULT_USER_DATA_FILE);
        rootDisk.bootConfig.files = new DiskState.BootConfig.FileEntry[] { file };
        rootDisk.capacityMBytes = BOOT_DISK_SIZE_IN_MEBI_BYTES;

        //add custom properties to root disk from profile
        rootDisk.customProperties = new HashMap<>();
        rootDisk.customProperties.put(DEVICE_TYPE,"ebs");
        rootDisk.customProperties.put(VOLUME_TYPE,"io1");
        rootDisk.customProperties.put(IOPS,"500");

        rootDisk.regionId = regionId;
        rootDisk.endpointLink = endpointState.documentSelfLink;
        rootDisk.tenantLinks = endpointState.tenantLinks;

        rootDisk = TestUtils.doPost(host, rootDisk,
                DiskService.DiskState.class,
                UriUtils.buildUri(host, DiskService.FACTORY_LINK));

        vmDisks.add(rootDisk.documentSelfLink);
        List<DiskState> additionalDisks = getAdditionalDiskConfiguration(host, endpointState);
        for (DiskState additionalDisk : additionalDisks) {
            vmDisks.add(additionalDisk.documentSelfLink);
        }

        // Create NIC States
        List<String> nicLinks = createAWSNicStates(
                host, computeHost, endpointState, awsVMDesc.name, nicSpecs, addNewSecurityGroup)
                        .stream()
                        .map(nic -> nic.documentSelfLink)
                        .collect(Collectors.toList());

        // Create compute state
        ComputeState resource;
        {
            resource = new ComputeState();
            resource.id = UUID.randomUUID().toString();
            resource.name = awsVMDesc.name;
            resource.type = ComputeType.VM_GUEST;
            resource.environmentName = ComputeDescription.ENVIRONMENT_NAME_AWS;
            resource.descriptionLink = awsVMDesc.documentSelfLink;
            resource.parentLink = computeHost.documentSelfLink;
            resource.resourcePoolLink = computeHost.resourcePoolLink;
            resource.networkInterfaceLinks = nicLinks;
            resource.diskLinks = vmDisks;
            resource.tagLinks = tagLinks;

            resource.regionId = awsVMDesc.regionId;
            resource.endpointLink = endpointState.documentSelfLink;
            resource.tenantLinks = endpointState.tenantLinks;
        }

        return TestUtils.doPost(host, resource,
                ComputeService.ComputeState.class,
                UriUtils.buildUri(host, ComputeService.FACTORY_LINK));
    }

    private static List<DiskState> getAdditionalDiskConfiguration(VerificationHost host,
            EndpointState endpointState) throws Throwable {
        List<String[]> additionalDiskConfigs = new ArrayList<>();
        String[] disk1properties = { "ebs", "gp2" };
        String[] disk2properties = { "ebs", "io1", "900" };
        additionalDiskConfigs.add(disk1properties);
        additionalDiskConfigs.add(disk2properties);

        List<DiskState> disks = new ArrayList<>();
        for (int i = 0; i < additionalDiskConfigs.size(); i++) {
            DiskState disk = new DiskState();
            disk.id = UUID.randomUUID().toString();
            disk.documentSelfLink = disk.id;
            disk.bootOrder = i + 2;
            disk.name = "Test Volume" + i;
            disk.sourceImageReference = URI.create(imageId);
            disk.capacityMBytes = ADDITIONAL_DISK_SIZE_IN_MEBI_BYTES;

            //add custom properties to root disk from profile
            disk.customProperties = new HashMap<>();
            disk.customProperties.put(DEVICE_TYPE, additionalDiskConfigs.get(i)[0]);
            disk.customProperties.put(VOLUME_TYPE, additionalDiskConfigs.get(i)[1]);
            if (additionalDiskConfigs.get(i).length > 2) {
                disk.customProperties.put(DISK_IOPS, additionalDiskConfigs.get(i)[2]);
            }

            disk.regionId = regionId;
            disk.endpointLink = endpointState.documentSelfLink;
            disk.tenantLinks = endpointState.tenantLinks;

            disk.encrypted = (i == 0) ? true : null;

            disk = TestUtils.doPost(host, disk, DiskService.DiskState.class,
                    UriUtils.buildUri(host, DiskService.FACTORY_LINK));
            disks.add(disk);
        }
        return disks;
    }

    /*
     * NOTE: It is highly recommended to keep this method in sync with its Azure counterpart:
     * AzureTestUtil.createDefaultNicStates
     */
    public static List<NetworkInterfaceState> createAWSNicStates(
            VerificationHost host,
            ComputeState computeHost,
            EndpointState endpointState,
            String vmName,
            AwsNicSpecs nicSpecs,
            boolean addNewSecurityGroup) throws Throwable {

        // Create network state.
        NetworkState networkState;
        {
            networkState = new NetworkState();
            networkState.id = nicSpecs.network.id;
            networkState.name = nicSpecs.network.name;
            networkState.subnetCIDR = nicSpecs.network.cidr;
            networkState.authCredentialsLink = endpointState.authCredentialsLink;
            networkState.resourcePoolLink = computeHost.resourcePoolLink;
            networkState.instanceAdapterReference = UriUtils.buildUri(host,
                    AWSUriPaths.AWS_NETWORK_ADAPTER);

            networkState.regionId = regionId;
            networkState.endpointLink = endpointState.documentSelfLink;
            networkState.tenantLinks = endpointState.tenantLinks;

            networkState = TestUtils.doPost(host, networkState,
                    NetworkState.class,
                    UriUtils.buildUri(host, NetworkService.FACTORY_LINK));
        }

        // Create NIC states.
        List<NetworkInterfaceState> nics = new ArrayList<>();

        for (int i = 0; i < nicSpecs.nicSpecs.size(); i++) {

            // Create subnet state per NIC.
            SubnetState subnetState;
            {
                subnetState = new SubnetState();

                subnetState.id = nicSpecs.nicSpecs.get(i).subnetSpec.id;
                subnetState.name = nicSpecs.nicSpecs.get(i).subnetSpec.name;
                subnetState.subnetCIDR = nicSpecs.nicSpecs.get(i).subnetSpec.cidr;
                subnetState.zoneId = nicSpecs.nicSpecs.get(i).subnetSpec.zoneId;
                subnetState.networkLink = networkState.documentSelfLink;

                subnetState.regionId = regionId;
                subnetState.endpointLink = endpointState.documentSelfLink;
                subnetState.tenantLinks = endpointState.tenantLinks;

                subnetState = TestUtils.doPost(host, subnetState,
                        SubnetState.class,
                        UriUtils.buildUri(host, SubnetService.FACTORY_LINK));
            }

            // Create NIC description.
            NetworkInterfaceDescription nicDescription;

            NicSpec nicSpec = nicSpecs.nicSpecs.get(0);

            {
                nicDescription = new NetworkInterfaceDescription();

                nicDescription.id = "nicDesc" + i;
                nicDescription.name = "nicDesc" + i;
                nicDescription.deviceIndex = i;
                nicDescription.assignment = nicSpec.getIpAssignment();

                nicDescription.regionId = regionId;
                nicDescription.endpointLink = endpointState.documentSelfLink;
                nicDescription.tenantLinks = endpointState.tenantLinks;

                nicDescription = TestUtils.doPost(host, nicDescription,
                        NetworkInterfaceDescription.class,
                        UriUtils.buildUri(host, NetworkInterfaceDescriptionService.FACTORY_LINK));
            }

            // Create security group state for an existing security group
            SecurityGroupState existingSecurityGroupState = createSecurityGroupState(host,
                    computeHost, endpointState, true);

            NetworkInterfaceState nicState = new NetworkInterfaceState();

            nicState.id = UUID.randomUUID().toString();
            nicState.name = vmName + "-nic-" + i;
            nicState.deviceIndex = nicDescription.deviceIndex;

            nicState.networkLink = networkState.documentSelfLink;
            nicState.subnetLink = subnetState.documentSelfLink;
            nicState.networkInterfaceDescriptionLink = nicDescription.documentSelfLink;

            nicState.regionId = regionId;
            nicState.endpointLink = endpointState.documentSelfLink;
            nicState.tenantLinks = endpointState.tenantLinks;

            nicState.securityGroupLinks = new ArrayList<>();
            nicState.securityGroupLinks.add(existingSecurityGroupState.documentSelfLink);

            if (addNewSecurityGroup) {
                // Create security group state for a new security group
                SecurityGroupState newSecurityGroupState = createSecurityGroupState(host,
                        computeHost, endpointState, false);
                nicState.securityGroupLinks.add(newSecurityGroupState.documentSelfLink);
            }

            nicState = TestUtils.doPost(host, nicState,
                    NetworkInterfaceState.class,
                    UriUtils.buildUri(host, NetworkInterfaceService.FACTORY_LINK));

            nics.add(nicState);
        }

        return nics;
    }

    public static SecurityGroupState createSecurityGroupState(VerificationHost host,
            ComputeState computeHost,
            EndpointState endpointState, boolean existing) throws Throwable {

        SecurityGroupState securityGroupState = new SecurityGroupState();

        if (existing) {
            securityGroupState.id = AWS_DEFAULT_GROUP_ID;
            securityGroupState.name = AWS_DEFAULT_GROUP_NAME;
        } else {
            securityGroupState.id = "sg-" + UUID.randomUUID().toString().substring(0, 8);
            securityGroupState.name = AWS_NEW_GROUP_PREFIX + securityGroupState.id;
        }
        securityGroupState.authCredentialsLink = endpointState.authCredentialsLink;

        securityGroupState.regionId = regionId;
        securityGroupState.endpointLink = endpointState.documentSelfLink;
        securityGroupState.tenantLinks = endpointState.tenantLinks;

        Rule ssh = new Rule();
        ssh.name = "ssh";
        ssh.protocol = "tcp";
        ssh.ipRangeCidr = "0.0.0.0/0";
        ssh.ports = "22";

        securityGroupState.ingress = new ArrayList<>();
        securityGroupState.ingress.add(ssh);

        Rule out = new Rule();
        out.name = "out";
        out.protocol = "tcp";
        out.ipRangeCidr = "0.0.0.0/0";
        out.ports = "1-65535";

        securityGroupState.egress = new ArrayList<>();
        securityGroupState.egress.add(out);

        securityGroupState.regionId = "regionId";
        securityGroupState.resourcePoolLink = "/link/to/rp";
        securityGroupState.instanceAdapterReference = new URI(
                "http://instanceAdapterReference");

        return TestUtils.doPost(host, securityGroupState,
                SecurityGroupState.class,
                UriUtils.buildUri(host, SecurityGroupService.FACTORY_LINK));
    }

    /**
     * Deletes the VM that is present on an endpoint and represented by the passed in ID.
     *
     * @param documentSelfLink
     * @param isMock
     * @param host
     * @throws Throwable
     */
    public static void deleteVMs(String documentSelfLink, boolean isMock, VerificationHost host)
            throws Throwable {
        deleteVMs(documentSelfLink, isMock, host, false);
    }

    /**
     * Deletes the VM that is present on an endpoint and represented by the passed in ID.
     *
     * @param documentSelfLink
     * @param isMock
     * @param host
     * @param deleteDocumentOnly
     * @throws Throwable
     */
    public static void deleteVMs(String documentSelfLink, boolean isMock, VerificationHost host,
            boolean deleteDocumentOnly)
            throws Throwable {
        host.testStart(1);
        ResourceRemovalTaskState deletionState = new ResourceRemovalTaskState();
        QuerySpecification resourceQuerySpec = new QueryTask.QuerySpecification();
        // query all ComputeState resources for the cluster
        resourceQuerySpec.query
                .setTermPropertyName(ServiceDocument.FIELD_NAME_SELF_LINK)
                .setTermMatchValue(documentSelfLink);
        deletionState.resourceQuerySpec = resourceQuerySpec;
        deletionState.isMockRequest = isMock;
        // Waiting for default request timeout in minutes for the machine to be turned OFF on AWS.
        deletionState.documentExpirationTimeMicros = Utils.getNowMicrosUtc()
                + TimeUnit.MINUTES.toMicros(AWS_VM_REQUEST_TIMEOUT_MINUTES);
        if (deleteDocumentOnly) {
            deletionState.options = EnumSet.of(TaskOption.DOCUMENT_CHANGES_ONLY);
        }
        host.send(Operation
                .createPost(
                        UriUtils.buildUri(host,
                                ResourceRemovalTaskService.FACTORY_LINK))
                .setBody(deletionState)
                .setCompletion(host.getCompletion()));
        host.testWait();
        // check that the VMs are gone
        ProvisioningUtils.queryComputeInstances(host, 1);
    }

    /**
     * A utility method that deletes the VMs on the specified endpoint filtered by the instanceIds
     * that are passed in.
     *
     * @throws Throwable
     */
    public static void deleteVMsOnThisEndpoint(VerificationHost host, boolean isMock,
            String parentComputeLink, List<String> instanceIdsToDelete) throws Throwable {
        deleteVMsOnThisEndpoint(host, null, isMock, parentComputeLink, instanceIdsToDelete, null);
    }

    /**
     * A utility method that deletes the VMs on the specified endpoint filtered by the instanceIds
     * that are passed in. It expects peerURI and tenantLinks to be populated.
     *
     * @throws Throwable
     */
    public static void deleteVMsOnThisEndpoint(VerificationHost host, URI peerURI, boolean isMock,
            String parentComputeLink, List<String> instanceIdsToDelete, List<String> tenantLinks)
            throws Throwable {
        host.testStart(1);
        ResourceRemovalTaskState deletionState = new ResourceRemovalTaskState();
        deletionState.tenantLinks = tenantLinks;

        // All AWS Compute States AND Ids in (Ids to delete)
        QuerySpecification compositeQuery = new QueryTask.QuerySpecification();

        // Document Kind = Compute State AND Parent Compute Link = AWS
        QueryTask.Query awsComputeStatesQuery = new QueryTask.Query();
        awsComputeStatesQuery = Query.Builder.create()
                .addKindFieldClause(ComputeService.ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK,
                        parentComputeLink)
                .build();
        compositeQuery.query.addBooleanClause(awsComputeStatesQuery);

        if (instanceIdsToDelete != null && instanceIdsToDelete.size() > 0) {
            // Instance Ids in List of instance Ids to delete
            QueryTask.Query instanceIdFilterParentQuery = new QueryTask.Query();
            for (String instanceId : instanceIdsToDelete) {
                if (!instanceId.startsWith(AWS_INSTANCE_PREFIX)) {
                    continue;
                }
                QueryTask.Query instanceIdFilter = new QueryTask.Query()
                        .setTermPropertyName(ComputeState.FIELD_NAME_ID)
                        .setTermMatchValue(instanceId);
                instanceIdFilter.occurance = QueryTask.Query.Occurance.SHOULD_OCCUR;
                instanceIdFilterParentQuery.addBooleanClause(instanceIdFilter);
            }
            instanceIdFilterParentQuery.occurance = Occurance.MUST_OCCUR;
            compositeQuery.query.addBooleanClause(instanceIdFilterParentQuery);
        }

        deletionState.resourceQuerySpec = compositeQuery;
        deletionState.isMockRequest = isMock;
        host.send(Operation
                .createPost(
                        createServiceURI(host, peerURI, ResourceRemovalTaskService.FACTORY_LINK))
                .setBody(deletionState)
                .setCompletion(host.getCompletion()));
        // Re-setting the test timeout value so that it clean up spawned instances even if it has
        // timed out based on the original value.
        host.setTimeoutSeconds(500);
        host.testWait();
    }

    /**
     * Method for deleting a document with the said identifier.
     *
     * @param host
     *            The verification host
     * @param documentToDelete
     *            The identifier of the document to be deleted.
     * @throws Throwable
     */
    public static void deleteDocument(VerificationHost host, String documentToDelete)
            throws Throwable {
        host.testStart(1);
        host.send(Operation
                .createDelete(
                        UriUtils.buildUri(host,
                                documentToDelete))
                .setBody(new ServiceDocument())
                .setCompletion(host.getCompletion()));
        host.testWait();

    }

    /**
     * Provisions a machine for which the state was created.
     */
    public static ComputeState provisionMachine(VerificationHost host, ComputeState vmState,
            boolean isMock,
            List<String> instancesToCleanUp)
            throws InterruptedException, TimeoutException, Throwable {
        return provisionMachine(host, null, vmState, isMock, instancesToCleanUp);
    }

    /**
     * Provisions a machine for which the state was created.Expects the peerURI for the location of
     * the service.
     */
    public static ComputeState provisionMachine(VerificationHost host, URI peerURI,
            ComputeState vmState,
            boolean isMock,
            List<String> instancesToCleanUp)
            throws Throwable, InterruptedException, TimeoutException {
        host.log("Provisioning a single virtual machine on AWS.");
        // kick off a provision task to do the actual VM creation
        ProvisionComputeTaskState provisionTask = new ProvisionComputeTaskService.ProvisionComputeTaskState();

        provisionTask.computeLink = vmState.documentSelfLink;
        provisionTask.isMockRequest = isMock;
        provisionTask.taskSubStage = ProvisionComputeTaskState.SubStage.CREATING_HOST;
        ProvisionComputeTaskService.ProvisionComputeTaskState outTask = TestUtils.doPost(host,
                provisionTask,
                ProvisionComputeTaskState.class,
                createServiceURI(host, peerURI,
                        ProvisionComputeTaskService.FACTORY_LINK));

        host.waitForFinishedTask(ProvisionComputeTaskState.class, outTask.documentSelfLink);

        ComputeState provisionCompute = getCompute(host, vmState.documentSelfLink);
        assertNotNull(provisionCompute);

        host.log("Sucessfully provisioned a machine %s ", provisionCompute.id);
        instancesToCleanUp.add(provisionCompute.id);

        return provisionCompute;
    }

    public static ComputeState getCompute(VerificationHost host, String computeLink)
            throws Throwable {
        Operation response = host
                .waitForResponse(Operation.createGet(host, computeLink));
        return response.getBody(ComputeState.class);
    }

    /**
     * Method to get ResourceState.
     *
     * @throws Throwable
     */
    private static ResourceState getResourceState(VerificationHost host, String resourceLink)
            throws Throwable {
        Operation response = host
                .waitForResponse(Operation.createGet(host, resourceLink));
        return response.getBody(ResourceState.class);
    }

    /**
     * Validates the documents of ResourceState have been removed.
     *
     * @throws Throwable
     */
    public static void verifyRemovalOfResourceState(VerificationHost host,
            List<String> resourceStateLinks) throws Throwable {
        for (String resourceLink : resourceStateLinks) {
            ResourceState resourceState = getResourceState(host, resourceLink);
            assertNotNull(resourceState);
            // make sure the document has been removed.
            assertNull(resourceState.documentSelfLink);
        }
    }

    /**
     * Method to directly provision instances on the AWS endpoint without the knowledge of the local
     * system. This is used to spawn instances and to test that the discovery of items not
     * provisioned by Xenon happens correctly.
     *
     * @throws Throwable
     */
    public static List<String> provisionAWSVMWithEC2Client(AmazonEC2AsyncClient client,
            VerificationHost host, int numberOfInstance, String instanceType, String subnetId,
            String securityGroupId) throws Throwable {
        host.log("Provisioning %d instances on the AWS endpoint using the EC2 client.",
                numberOfInstance);

        RunInstancesRequest runInstancesRequest = new RunInstancesRequest()
                .withSubnetId(subnetId)
                .withImageId(EC2_LINUX_AMI).withInstanceType(instanceType)
                .withMinCount(numberOfInstance).withMaxCount(numberOfInstance)
                .withSecurityGroupIds(securityGroupId);

        // handler invoked once the EC2 runInstancesAsync commands completes
        AWSRunInstancesAsyncHandler creationHandler = new AWSRunInstancesAsyncHandler(
                host);
        client.runInstancesAsync(runInstancesRequest, creationHandler);
        host.waitFor("Waiting for instanceIds to be retured from AWS", () -> {
            return checkInstanceIdsReturnedFromAWS(numberOfInstance, creationHandler.instanceIds);

        });

        return creationHandler.instanceIds;
    }

    /**
     * Create a new AWS NIC in the default subnet
     */
    public static String createNICDirectlyWithEC2Client (AmazonEC2Client client,
            VerificationHost host, String subnetId) {
        // create the new AWS NIC
        CreateNetworkInterfaceRequest createNewNic = new CreateNetworkInterfaceRequest()
                .withSubnetId(subnetId);
        CreateNetworkInterfaceResult createNewNicResult = client
                .createNetworkInterface(createNewNic);

        return createNewNicResult.getNetworkInterface().getNetworkInterfaceId();
    }

    /**
     * Attach a provided AWS NIC to a given AWS VM with deviceIndex = number of NICs + 1
     * returns the attachment ID of the newly created and attached NIC. This is necessary for
     * removing it later for the goals of the test. The NIC is as well configured to be deleted on
     * instance termination for sanity purposes.
     */
    public static String addNICDirectlyWithEC2Client(ComputeState vm, AmazonEC2Client client,
            VerificationHost host, String newNicId) {

        // attach the new AWS NIC to the AWS VM
        AttachNetworkInterfaceRequest attachNewNic = new AttachNetworkInterfaceRequest()
                .withInstanceId(vm.id)
                .withDeviceIndex(vm.networkInterfaceLinks.size())
                .withNetworkInterfaceId(newNicId);

        AttachNetworkInterfaceResult attachmetnResult = client.attachNetworkInterface(attachNewNic);
        String attachmentId = attachmetnResult.getAttachmentId();

        // ensure the new NIC is deleted when the VM is terminated
        NetworkInterfaceAttachmentChanges attachTerm = new NetworkInterfaceAttachmentChanges()
                .withAttachmentId(attachmentId)
                .withDeleteOnTermination(true);
        ModifyNetworkInterfaceAttributeRequest setDeleteOnTerm = new ModifyNetworkInterfaceAttributeRequest()
                .withAttachment(attachTerm)
                .withNetworkInterfaceId(newNicId);
        client.modifyNetworkInterfaceAttribute(setDeleteOnTerm);
        host.log("Created new NIC with id: %s to vm id: %s with attachment id: %s", newNicId,
                vm.id, attachmentId);
        return attachmentId;
    }

    /**
     * Removes a specified AWS NIC from the VM it is currently attached to
     */
    public static void detachNICDirectlyWithEC2Client(String instanceId, String nicAttachmentId, String nicId,
            AmazonEC2Client client, VerificationHost host) {

        // detach the new AWS NIC to the AWS VM
        DetachNetworkInterfaceRequest detachNic = new DetachNetworkInterfaceRequest();
        detachNic.withAttachmentId(nicAttachmentId);

        host.log("Detaching NIC with id: %s and attachment id: %s", nicId, nicAttachmentId);
        client.detachNetworkInterface(detachNic);

        host.waitFor("Timeout waiting for AWS to detach a NIC from " + instanceId
                + " with attachment id: " + nicAttachmentId, () -> {
                    DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest()
                            .withFilters(new Filter("instance-id", Collections.singletonList(instanceId)));
                    DescribeInstancesResult result = client.describeInstances(describeInstancesRequest);

                    Instance currentInstance = result.getReservations().get(0).getInstances().get(0);
                    for (InstanceNetworkInterface awsNic : currentInstance.getNetworkInterfaces()) {
                        if (awsNic.getNetworkInterfaceId().equals(nicId)) {
                            //Requested NIC was not detached from the instance
                            return false;
                        }
                    }
                    host.log("Detached NIC with attachment id: %s", nicAttachmentId);
                    return true;
                });
    }

    /**
     * Delete an AWS Nic by id
     */
    public static void deleteNICDirectlyWithEC2Client(AmazonEC2Client client,
            VerificationHost host, String nicId) {
        if (nicId == null) {
            return;
        }

        DeleteNetworkInterfaceRequest deleteNicRequest = new DeleteNetworkInterfaceRequest()
                .withNetworkInterfaceId(nicId);
        host.log("Clean-up NIC with id: %s", nicId);
        client.deleteNetworkInterface(deleteNicRequest);
    }

    /**
     * Method to get Instance details directly from Amazon
     *
     * @throws Throwable
     */
    public static List<Instance> getAwsInstancesByIds(AmazonEC2AsyncClient client,
            VerificationHost host, List<String> instanceIds)
            throws Throwable {
        host.log("Getting instances with ids " + instanceIds
                + " from the AWS endpoint using the EC2 client.");

        DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest()
                .withInstanceIds(instanceIds);

        DescribeInstancesResult describeInstancesResult = client
                .describeInstances(describeInstancesRequest);

        return describeInstancesResult.getReservations().stream()
                .flatMap(r -> r.getInstances().stream()).collect(Collectors.toList());
    }

    public static String provisionAWSVMWithEC2Client(VerificationHost host, AmazonEC2Client client,
            String ami, String subnetId, String securityGroupId) {

        RunInstancesRequest runInstancesRequest = new RunInstancesRequest()
                .withSubnetId(subnetId)
                .withImageId(ami)
                .withInstanceType(instanceType_t2_micro)
                .withMinCount(1).withMaxCount(1)
                .withSecurityGroupIds(securityGroupId);

        // handler invoked once the EC2 runInstancesAsync commands completes
        RunInstancesResult result = null;
        try {
            result = client.runInstances(runInstancesRequest);
        } catch (Exception e) {
            host.log(Level.SEVERE, "Error encountered in provisioning machine on AWS",
                    Utils.toString(e));
        }
        assertNotNull(result);
        assertNotNull(result.getReservation());
        assertNotNull(result.getReservation().getInstances());
        assertEquals(1, result.getReservation().getInstances().size());

        return result.getReservation().getInstances().get(0).getInstanceId();
    }

    public static String provisionAWSEBSVMWithEC2Client(VerificationHost host, AmazonEC2Client client,
            String ami, String subnetId, String securityGroupId, BlockDeviceMapping blockDeviceMapping) {

        RunInstancesRequest runInstancesRequest = new RunInstancesRequest()
                .withSubnetId(subnetId)
                .withImageId(ami)
                .withInstanceType(instanceType_t2_micro)
                .withMinCount(1).withMaxCount(1)
                .withSecurityGroupIds(securityGroupId)
                .withBlockDeviceMappings(blockDeviceMapping);

        // handler invoked once the EC2 runInstancesAsync commands completes
        RunInstancesResult result = null;
        try {
            result = client.runInstances(runInstancesRequest);
        } catch (Exception e) {
            host.log(Level.SEVERE, "Error encountered in provisioning machine on AWS",
                    Utils.toString(e));
        }
        assertNotNull(result);
        assertNotNull(result.getReservation());
        assertNotNull(result.getReservation().getInstances());
        assertEquals(1, result.getReservation().getInstances().size());

        return result.getReservation().getInstances().get(0).getInstanceId();
    }

    /**
     * Checks if the required number of instanceIds have been returned from AWS for the requested
     * number of resources to be provisioned.
     */
    private static boolean checkInstanceIdsReturnedFromAWS(int numberOfInstance,
            List<String> instanceIds) {
        if (instanceIds == null || instanceIds.size() == 0) {
            return false;
        }
        return (instanceIds.size() == numberOfInstance);
    }

    /**
     * Waits for the instances to be in running state that were provisioned on AWS.
     */
    public static void waitForProvisioningToComplete(List<String> instanceIds,
            VerificationHost host, AmazonEC2AsyncClient client, int errorRate) throws Throwable {
        // Wait for the machine provisioning to be completed.
        host.waitFor("Error waiting for EC2 client provisioning in test ", () -> {
            return computeInstancesStartedStateWithAcceptedErrorRate(client, host, instanceIds,
                    errorRate);
        });
    }

    /**
     * Method that sets basic information in {@link AWSUtils} for aws-mock. Aws-mock is a
     * open-source tool for testing AWS services in a mock EC2 environment.
     *
     * @param isAwsClientMock
     *            flag to use aws-mock
     * @param awsMockEndpointReference
     *            ec2 endpoint of aws-mock
     * @see <a href="https://github.com/treelogic-swe/aws-mock">aws-mock</a>
     */
    public static void setAwsClientMockInfo(boolean isAwsClientMock,
            String awsMockEndpointReference) {
        AWSUtils.setAwsClientMock(isAwsClientMock);
        AWSUtils.setAwsMockHost(awsMockEndpointReference);
    }

    /**
     * Handler class to spawn off instances on the AWS EC2 endpoint.
     */
    public static class AWSRunInstancesAsyncHandler implements
            AsyncHandler<RunInstancesRequest, RunInstancesResult> {

        public VerificationHost host;
        public List<String> instanceIds;

        AWSRunInstancesAsyncHandler(VerificationHost host) {
            this.host = host;
            this.instanceIds = new ArrayList<String>();
        }

        @Override
        public void onError(Exception exception) {
            this.host.log("Error creating instance{s} on AWS endpoint %s", exception);
        }

        @Override
        public void onSuccess(RunInstancesRequest request, RunInstancesResult result) {
            for (Instance i : result.getReservation().getInstances()) {
                this.instanceIds.add(i.getInstanceId());
                this.host.log("Successfully created instances on AWS endpoint %s",
                        i.getInstanceId());
            }
        }
    }

    /**
     * Checks if all the instances represented by the list of passed in instanceIds have been turned
     * ON.
     *
     * @return
     */
    public static void checkInstancesStarted(VerificationHost host, AmazonEC2AsyncClient client,
            List<String> instanceIds, List<Boolean> provisioningFlags) throws Throwable {
        AWSEnumerationAsyncHandler enumerationHandler = new AWSEnumerationAsyncHandler(host,
                AWSEnumerationAsyncHandler.MODE.CHECK_START, provisioningFlags, null, null, null,
                null);
        DescribeInstancesRequest request = new DescribeInstancesRequest()
                .withInstanceIds(instanceIds);
        client.describeInstancesAsync(request, enumerationHandler);
        host.waitFor("Waiting to get response from AWS ", () -> {
            return enumerationHandler.responseReceived;
        });
    }

    /**
     * Get the state of the EC2 instance.
     */
    public static String getVMState(AmazonEC2Client client,String id) {
        DescribeInstanceStatusRequest request = new DescribeInstanceStatusRequest();
        request.withInstanceIds(id);
        request.withIncludeAllInstances(Boolean.TRUE);
        DescribeInstanceStatusResult result = client.describeInstanceStatus(request);
        return result.getInstanceStatuses().get(0).getInstanceState().getName();
    }

    /**
     * Method that polls to see if the instances provisioned have turned ON.This method accepts an
     * error count to allow some room for errors in case all the requested resources are not
     * provisioned correctly.
     *
     * @return boolean if the required instances have been turned ON on AWS with some acceptable
     *         error rate.
     */
    public static boolean computeInstancesStartedStateWithAcceptedErrorRate(
            AmazonEC2AsyncClient client,
            VerificationHost host, List<String> instanceIds, int errorRate) throws Throwable {
        // If there are no instanceIds set then return false
        if (instanceIds.size() == 0) {
            return false;
        }
        ArrayList<Boolean> provisioningFlags = new ArrayList<Boolean>(instanceIds.size());
        for (int i = 0; i < instanceIds.size(); i++) {
            provisioningFlags.add(i, Boolean.FALSE);
        }
        // Calls the describe instances API to get the latest state of each machine being
        // provisioned.
        checkInstancesStarted(host, client, instanceIds, provisioningFlags);
        int totalCount = instanceIds.size();
        int passCount = (int) Math.ceil((((100 - errorRate) / HUNDERED) * totalCount));
        int poweredOnCount = 0;
        for (boolean startedFlag : provisioningFlags) {
            if (startedFlag) {
                poweredOnCount++;
            }
        }
        return (poweredOnCount >= passCount);
    }

    /**
     * Gets the instance count of non-terminated instances on the AWS endpoint. This is used to run
     * the asserts and validate the results for the data that is collected during enumeration.This
     * also calculates the compute descriptions that will be used to represent the instances that
     * were discovered on the AWS endpoint. Further factoring in the
     *
     * @throws Throwable
     */
    public static BaseLineState getBaseLineInstanceCount(VerificationHost host,
            AmazonEC2AsyncClient client,
            List<String> testComputeDescriptions)
            throws Throwable {
        BaseLineState baseLineState = new BaseLineState();
        AWSEnumerationAsyncHandler enumerationHandler = new AWSEnumerationAsyncHandler(host,
                AWSEnumerationAsyncHandler.MODE.GET_COUNT, null, null, null,
                testComputeDescriptions,
                baseLineState);
        DescribeInstancesRequest request = new DescribeInstancesRequest();
        Filter runningInstanceFilter = getAWSNonTerminatedInstancesFilter();
        request.getFilters().add(runningInstanceFilter);
        client.describeInstancesAsync(request, enumerationHandler);
        host.waitFor("Error waiting to get base line instance count from AWS in test ", () -> {
            return baseLineState.isCountPopulated;
        });
        return baseLineState;
    }

    /**
     * Enumerates resources on the AWS endpoint.
     */
    public static void enumerateResourcesPreserveMissing(VerificationHost host, ComputeState computeHost, EndpointState endpointState, boolean isMock,
            String testCase) throws Throwable {
        EnumSet<TaskOption> options = EnumSet.of(TaskOption.PRESERVE_MISSING_RESOUCES);
        if (isMock) {
            options.add(TaskOption.IS_MOCK);
        }
        enumerateResources(host, computeHost, endpointState, null, options, testCase);
    }

    /**
     * Enumerates resources on the AWS endpoint.
     */
    public static void enumerateResources(VerificationHost host, ComputeState computeHost, EndpointState endpointState, boolean isMock,
            String testCase) throws Throwable {

        enumerateResources(host, computeHost, endpointState, null, isMock ? EnumSet.of(TaskOption.IS_MOCK) : null,
                testCase);
    }

    /**
     * Enumerates resources on the AWS endpoint. Expects a peerURI and the tenantLinks to be set.
     */
    public static void enumerateResources(VerificationHost host,
            ComputeState computeHost, EndpointState endpointState,
            URI peerURI,
            EnumSet<TaskOption> options,
            String testCase) throws Throwable {
        // Perform resource enumeration on the AWS end point. Pass the references to the AWS compute
        // host.
        host.log("Performing resource enumeration");
        ResourceEnumerationTaskService.ResourceEnumerationTaskState enumTask = performResourceEnumeration(
                host, computeHost, endpointState, peerURI, options);
        // Wait for the enumeration task to be completed.
        host.waitForFinishedTask(ResourceEnumerationTaskState.class,
                createServiceURI(host, peerURI, enumTask.documentSelfLink));

        host.log("\n==%s==Total Time Spent in Enumeration==\n",
                testCase + getVMCount(host, peerURI));
        ServiceStats enumerationStats = host.getServiceState(null, ServiceStats.class, UriUtils
                .buildStatsUri(createServiceURI(host, peerURI,
                        AWSEnumerationAdapterService.SELF_LINK)));
        host.log(Utils.toJsonHtml(enumerationStats));
        host.log("\n==Total Time Spent in Creation Workflow==\n");
        ServiceStats enumerationCreationStats = host.getServiceState(null, ServiceStats.class,
                UriUtils
                        .buildStatsUri(createServiceURI(host, peerURI,
                                AWSEnumerationAndCreationAdapterService.SELF_LINK)));
        host.log(Utils.toJsonHtml(enumerationCreationStats));
        host.log("\n==Time spent in individual creation services==\n");
        ServiceStats computeDescriptionCreationStats = host.getServiceState(null,
                ServiceStats.class, UriUtils
                        .buildStatsUri(createServiceURI(host, peerURI,
                                AWSComputeDescriptionEnumerationAdapterService.SELF_LINK)));
        host.log(Utils.toJsonHtml(computeDescriptionCreationStats));
        ServiceStats computeStateCreationStats = host.getServiceState(null, ServiceStats.class,
                UriUtils
                        .buildStatsUri(createServiceURI(host, peerURI,
                                AWSComputeStateCreationAdapterService.SELF_LINK)));
        host.log(Utils.toJsonHtml(computeStateCreationStats));
        host.log("\n==Total Time Spent in Deletion Workflow==\n");
        ServiceStats deletionEnumerationStats = host.getServiceState(null, ServiceStats.class,
                UriUtils
                        .buildStatsUri(createServiceURI(host, peerURI,
                                AWSEnumerationAndDeletionAdapterService.SELF_LINK)));
        host.log(Utils.toJsonHtml(deletionEnumerationStats));
        host.log("\n==Total Time Spent in EBS Storage Enumeration Workflow==\n");
        ServiceStats ebsStorageEnumerationStats = host.getServiceState(null, ServiceStats.class,
                UriUtils
                        .buildStatsUri(createServiceURI(host, peerURI,
                                AWSEBSStorageEnumerationAdapterService.SELF_LINK)));
        host.log(Utils.toJsonHtml(ebsStorageEnumerationStats));
        host.log("\n==Total Time Spent in S3 Storage Enumeration Workflow==\n");
        ServiceStats s3StorageEnumerationStats = host.getServiceState(null, ServiceStats.class,
                UriUtils
                        .buildStatsUri(createServiceURI(host, peerURI,
                                AWSS3StorageEnumerationAdapterService.SELF_LINK)));
        host.log(Utils.toJsonHtml(s3StorageEnumerationStats));
    }

    /**
     * Method to perform compute resource enumeration on the AWS endpoint.
     *
     * @param resourcePoolLink
     *            The link to the AWS resource pool.
     * @param computeDescriptionLink
     *            The link to the compute description for the AWS host.
     * @param parentComputeLink
     *            The compute state associated with the AWS host.
     * @return
     * @throws Throwable
     */
    public static ResourceEnumerationTaskState performResourceEnumeration(
            VerificationHost host, ComputeState computeHost, EndpointState endpointState, URI peerURI, EnumSet<TaskOption> options) throws Throwable {
        // Kick of a Resource Enumeration task to enumerate the instances on the AWS endpoint
        ResourceEnumerationTaskState enumerationTaskState = new ResourceEnumerationTaskState();

        enumerationTaskState.parentComputeLink = computeHost.documentSelfLink;
        enumerationTaskState.enumerationAction = EnumerationAction.START;
        enumerationTaskState.adapterManagementReference = UriUtils
                .buildUri(AWSEnumerationAdapterService.SELF_LINK);

        enumerationTaskState.resourcePoolLink = computeHost.resourcePoolLink;
        if (options != null) {
            enumerationTaskState.options = options;
        } else {
            enumerationTaskState.options = EnumSet.noneOf(TaskOption.class);
        }
        enumerationTaskState.tenantLinks = endpointState.tenantLinks;
        enumerationTaskState.endpointLink = endpointState.documentSelfLink;

        URI uri = createServiceURI(host, peerURI, ResourceEnumerationTaskService.FACTORY_LINK);

        return TestUtils.doPost(
                host, enumerationTaskState, ResourceEnumerationTaskState.class, uri);
    }

    /**
     * Deletes instances on the AWS endpoint for the set of instance Ids that are passed in.
     *
     * @param instanceIdsToDelete
     * @throws Throwable
     */
    public static void deleteVMsUsingEC2Client(AmazonEC2AsyncClient client, VerificationHost host,
            List<String> instanceIdsToDelete) throws Throwable {
        TerminateInstancesRequest termRequest = new TerminateInstancesRequest(instanceIdsToDelete);
        AsyncHandler<TerminateInstancesRequest, TerminateInstancesResult> terminateHandler = new AWSTerminateHandlerAsync(
                host);
        client.terminateInstancesAsync(termRequest, terminateHandler);
        waitForInstancesToBeTerminated(client, host, instanceIdsToDelete);

    }

    public static void waitForInstancesToBeTerminated(AmazonEC2AsyncClient client,
            VerificationHost host, List<String> instanceIdsToDelete) throws Throwable {
        if (instanceIdsToDelete.size() == 0) {
            return;
        }
        ArrayList<Boolean> deletionFlags = new ArrayList<>(instanceIdsToDelete.size());
        for (int i = 0; i < instanceIdsToDelete.size(); i++) {
            deletionFlags.add(i, Boolean.FALSE);
        }
        host.waitFor("Error waiting for EC2 client delete instances in test ", () -> {
            boolean isDeleted = computeInstancesTerminationState(client,
                    host, instanceIdsToDelete, deletionFlags);
            if (isDeleted) {
                return true;
            }

            host.log(Level.INFO, "Waiting for EC2 instance deletion");
            Thread.sleep(TimeUnit.SECONDS.toMillis(10));
            return false;
        });

    }

    /**
     * Async handler for the deletion of instances from the AWS endpoint.
     */
    public static class AWSTerminateHandlerAsync implements
            AsyncHandler<TerminateInstancesRequest, TerminateInstancesResult> {

        VerificationHost host;

        AWSTerminateHandlerAsync(VerificationHost host) {
            this.host = host;
        }

        @Override
        public void onError(Exception exception) {
            this.host.log("Error deleting instance{s} from AWS %s", exception);
        }

        @Override
        public void onSuccess(TerminateInstancesRequest request,
                TerminateInstancesResult result) {
            this.host.log("Successfully deleted instances from the AWS endpoint %s",
                    result.getTerminatingInstances().toString());
        }
    }

    /**
     * Method that polls to see if the instances provisioned have been terminated on the AWS
     * endpoint.
     *
     * @param deletionFlags
     */
    public static boolean computeInstancesTerminationState(AmazonEC2AsyncClient client,
            VerificationHost host, List<String> instanceIdsToDelete,
            ArrayList<Boolean> deletionFlags) throws Throwable {
        checkInstancesDeleted(client, host, instanceIdsToDelete, deletionFlags);
        Boolean finalState = true;
        for (Boolean b : deletionFlags) {
            finalState = finalState & b;
        }
        return finalState;
    }

    /**
     * Checks if a newly deleted instance has its status set to terminated.
     *
     * @return
     */
    public static void checkInstancesDeleted(AmazonEC2AsyncClient client,
            VerificationHost host, List<String> instanceIdsToDelete,
            ArrayList<Boolean> deletionFlags) throws Throwable {
        AWSEnumerationAsyncHandler enumerationHandler = new AWSEnumerationAsyncHandler(host,
                AWSEnumerationAsyncHandler.MODE.CHECK_TERMINATION, null, deletionFlags, null, null,
                null);
        DescribeInstancesRequest request = new DescribeInstancesRequest()
                .withInstanceIds(instanceIdsToDelete);
        client.describeInstancesAsync(request, enumerationHandler);
        // Waiting to get a response from AWS before the state computation is done for the list of
        // VMs.
        host.waitFor("Waiting to get response from AWS ", () -> {
            return enumerationHandler.responseReceived;
        });
    }

    /**
     * Method that polls to see if the instances provisioned have been stopped on the AWS endpoint.
     *
     * @param client
     * @param host
     * @param instanceIdsToStop
     * @param stopFlags
     * @throws Throwable
     */
    public static boolean computeInstancesStopState(AmazonEC2AsyncClient client,
            VerificationHost host, List<String> instanceIdsToStop,
            ArrayList<Boolean> stopFlags) throws Throwable {
        checkInstancesStopped(client, host, instanceIdsToStop, stopFlags);
        Boolean finalState = true;
        for (Boolean b : stopFlags) {
            finalState = finalState & b;
        }
        return finalState;
    }

    /**
     * Checks if instances have their status set to stopped.
     *
     * @param client
     * @param host
     * @param instanceIdsToStop
     * @param stopFlags
     * @throws Throwable
     */
    public static void checkInstancesStopped(AmazonEC2AsyncClient client,
            VerificationHost host, List<String> instanceIdsToStop,
            ArrayList<Boolean> stopFlags) throws Throwable {
        AWSEnumerationAsyncHandler enumerationHandler = new AWSEnumerationAsyncHandler(host,
                AWSEnumerationAsyncHandler.MODE.CHECK_STOP, null, null, stopFlags, null, null);
        DescribeInstancesRequest request = new DescribeInstancesRequest()
                .withInstanceIds(instanceIdsToStop);
        client.describeInstancesAsync(request, enumerationHandler);
        // Waiting to get a response from AWS before the state computation is done for the list of
        // VMs.
        host.waitFor("Waiting to get response from AWS ", () -> {
            return enumerationHandler.responseReceived;
        });
    }

    /**
     * Stop instances on the AWS endpoint for the set of instance Ids that are passed in.
     *
     * @param client
     * @param host
     * @param instanceIdsToStop
     * @throws Throwable
     */
    public static void stopVMsUsingEC2Client(AmazonEC2AsyncClient client, VerificationHost host,
            List<String> instanceIdsToStop) throws Throwable {
        StopInstancesRequest stopRequest = new StopInstancesRequest(instanceIdsToStop);
        AsyncHandler<StopInstancesRequest, StopInstancesResult> stopHandler = new AWSStopHandlerAsync(
                host);
        client.stopInstancesAsync(stopRequest, stopHandler);
        waitForInstancesToBeStopped(client, host, instanceIdsToStop);

    }

    /**
     * Wait for the instances have their status set to stopped.
     *
     * @param client
     * @param host
     * @param instanceIdsToStop
     * @throws Throwable
     */
    public static void waitForInstancesToBeStopped(AmazonEC2AsyncClient client,
            VerificationHost host, List<String> instanceIdsToStop) throws Throwable {
        ArrayList<Boolean> stopFlags = new ArrayList<>(instanceIdsToStop.size());
        for (int i = 0; i < instanceIdsToStop.size(); i++) {
            stopFlags.add(i, Boolean.FALSE);
        }
        host.waitFor("Error waiting for EC2 client stop instances in test ", () -> {
            return computeInstancesStopState(client,
                    host, instanceIdsToStop, stopFlags);
        });

    }

    /**
     * Async handler for the stop of instances from the AWS endpoint.
     */
    public static class AWSStopHandlerAsync implements
            AsyncHandler<StopInstancesRequest, StopInstancesResult> {

        VerificationHost host;

        AWSStopHandlerAsync(VerificationHost host) {
            this.host = host;
        }

        @Override
        public void onError(Exception exception) {
            this.host.log("Error stopping instance{s} from AWS %s", exception);
        }

        @Override
        public void onSuccess(StopInstancesRequest request,
                StopInstancesResult result) {
            this.host.log("Successfully stopped instances from the AWS endpoint %s",
                    result.getStoppingInstances().toString());
        }
    }

    /**
     * Handler to get the state of a provisioned machine. It takes in different mode parameters to
     * arrive at different values 1) Checks if all the instances with the passed in instance Ids
     * have been powered ON. 2) Checks if all the instances with the passed in instance Ids have
     * been terminated. 3) Gets the baseline count of instances on the AWS endpoint before the
     * enumeration algorithm kicks in. This count is used to keep track of the final expected number
     * of compute states in the system once enumeration has completed successfully.
     */
    public static class AWSEnumerationAsyncHandler implements
            AsyncHandler<DescribeInstancesRequest, DescribeInstancesResult> {

        private static final int AWS_TERMINATED_CODE = 48;
        private static final int AWS_STARTED_CODE = 16;
        private static final int AWS_STOPPED_CODE = 80;
        public VerificationHost host;
        public MODE mode;
        public List<Boolean> provisioningFlags;
        public List<Boolean> deletionFlags;
        public List<Boolean> stopFlags;
        public List<String> testComputeDescriptions;
        public BaseLineState baseLineState;
        public boolean responseReceived = false;

        // Flag to indicate whether you want to check if instance has started or stopped.
        public static enum MODE {
            CHECK_START, CHECK_TERMINATION, CHECK_STOP, GET_COUNT
        }

        AWSEnumerationAsyncHandler(VerificationHost host, MODE mode,
                List<Boolean> provisioningFlags, List<Boolean> deletionFlags,
                List<Boolean> stopFlags, List<String> testComputeDescriptions,
                BaseLineState baseLineState) {
            this.host = host;
            this.mode = mode;
            this.provisioningFlags = provisioningFlags;
            this.deletionFlags = deletionFlags;
            this.stopFlags = stopFlags;
            this.testComputeDescriptions = testComputeDescriptions;
            this.baseLineState = baseLineState;
            this.responseReceived = false;
        }

        @Override
        public void onError(Exception exception) {
            this.responseReceived = true;
            this.host.log("Error describing instances on AWS. The exception encounterd is %s",
                    exception);
        }

        @Override
        public void onSuccess(DescribeInstancesRequest request,
                DescribeInstancesResult result) {
            int counter = 0;
            switch (this.mode) {
            case CHECK_START:
                for (Reservation r : result.getReservations()) {
                    for (Instance i : r.getInstances()) {
                        if (i.getState().getCode() == AWS_STARTED_CODE) {
                            this.provisioningFlags.set(counter, Boolean.TRUE);
                            counter++;
                        }
                    }
                }
                break;
            case CHECK_TERMINATION:
                for (Reservation r : result.getReservations()) {
                    for (Instance i : r.getInstances()) {
                        if (i.getState().getCode() == AWS_TERMINATED_CODE) {
                            this.deletionFlags.set(counter, Boolean.TRUE);
                            counter++;
                        }
                    }
                }
                break;
            case CHECK_STOP:
                for (Reservation r : result.getReservations()) {
                    for (Instance i : r.getInstances()) {
                        if (i.getState().getCode() == AWS_STOPPED_CODE) {
                            this.stopFlags.set(counter, Boolean.TRUE);
                            counter++;
                        }
                    }
                }
                break;
            case GET_COUNT:
                Set<String> computeDescriptionSet = new HashSet<String>();
                for (Reservation r : result.getReservations()) {
                    for (Instance i : r.getInstances()) {
                        // Do not add information about terminated instances to the local system.
                        if (i.getState().getCode() != AWS_TERMINATED_CODE) {
                            computeDescriptionSet
                                    .add(getRegionId(i).concat("~")
                                            .concat(i.getInstanceType()));
                            this.baseLineState.baselineVMCount++;
                        }
                    }
                }
                // If the discovered resources on the endpoint already map to a test compute
                // description then we will not be creating a new CD for it.
                if (this.testComputeDescriptions != null) {
                    for (String testCD : this.testComputeDescriptions) {
                        if (computeDescriptionSet.contains(testCD)) {
                            computeDescriptionSet.remove(testCD);
                        }
                    }
                }
                this.baseLineState.baselineComputeDescriptionCount = computeDescriptionSet.size();

                this.host.log("The baseline instance count on AWS is %d ",
                        this.baseLineState.baselineVMCount);
                this.host.log("These instances will be represented by %d additional compute "
                        + "descriptions ", this.baseLineState.baselineComputeDescriptionCount);
                this.baseLineState.isCountPopulated = true;
                break;
            default:
                this.host.log("Invalid stage %s for describing AWS instances", this.mode);
            }
            this.responseReceived = true;
        }
    }

    /**
     * Lookup a Compute by aws Id
     */
    public static ComputeState getComputeByAWSId(VerificationHost host, String awsId)
            throws Throwable {

        URI computesURI = UriUtils.buildUri(host, ComputeService.FACTORY_LINK);
        computesURI = UriUtils.buildExpandLinksQueryUri(computesURI);
        computesURI = UriUtils.appendQueryParam(computesURI, "$filter",
                String.format("id eq %s", awsId));

        Operation op = host.waitForResponse(Operation.createGet(computesURI));
        ServiceDocumentQueryResult result = op.getBody(ServiceDocumentQueryResult.class);
        assertNotNull(result);
        assertNotNull(result.documents);
        assertEquals(1, result.documents.size());

        return Utils.fromJson(result.documents.values().iterator().next(), ComputeState.class);
    }

    /**
     * Lookup a NIC State by aws Id
     */
    public static NetworkInterfaceState getNICByAWSId(VerificationHost host, String awsId)
            throws Throwable {

        URI networkInterfacesURI = UriUtils.buildUri(host, NetworkInterfaceService.FACTORY_LINK);
        networkInterfacesURI = UriUtils.buildExpandLinksQueryUri(networkInterfacesURI);
        networkInterfacesURI = UriUtils.appendQueryParam(networkInterfacesURI, "$filter",
                String.format("id eq %s", awsId));

        Operation op = host.waitForResponse(Operation.createGet(networkInterfacesURI));
        ServiceDocumentQueryResult result = op.getBody(ServiceDocumentQueryResult.class);
        assertNotNull(result);
        assertNotNull(result.documents);
        if (result.documents.size() == 0) {
            return null;
        }
        return Utils.fromJson(result.documents.values().iterator().next(), NetworkInterfaceState.class);
    }

    public static SecurityGroup getSecurityGroupsIdUsingEC2Client(AmazonEC2AsyncClient client, String awsGroupId) {
        if (awsGroupId == null) {
            return null;
        }

        DescribeSecurityGroupsRequest describeSGsRequest = new DescribeSecurityGroupsRequest()
                .withFilters(new Filter(AWSConstants.AWS_GROUP_ID_FILTER,Collections.singletonList(awsGroupId)));
        DescribeSecurityGroupsResult describeSGResult = client.describeSecurityGroups(describeSGsRequest);

        if (describeSGResult.getSecurityGroups().size() > 0) {
            return describeSGResult.getSecurityGroups().get(0);
        } else {
            return null;
        }
    }

    public static void deleteSecurityGroupUsingEC2Client(AmazonEC2AsyncClient client,
            VerificationHost host, String awsGroupId) {
        host.log(Level.INFO, "Starting to delete aws Security group with id %s", awsGroupId);
        if (awsGroupId == null) {
            return;
        }

        try {
            DeleteSecurityGroupRequest deleteSecurityGroupRequest = new DeleteSecurityGroupRequest()
                    .withGroupId(awsGroupId);
            client.deleteSecurityGroup(deleteSecurityGroupRequest);

            host.waitFor(
                    "Timeout waiting for AWS to delete a SecurityGroup with name " + awsGroupId,
                    () -> {
                        // Check if the SG is actually not present on AWS after the delete operation
                        SecurityGroup discoveredSGOnAWS = getSecurityGroupsIdUsingEC2Client(client, awsGroupId);

                        if (discoveredSGOnAWS != null) {
                            // Requested SG was not deleted from AWS
                            return false;
                        }

                        host.log("Deleted SG with id: %s", awsGroupId);
                        return true;
                    });
        } catch (Exception e) {
            String message = e.getMessage();
            if (!message.contains("The security group '" + awsGroupId + "' already exists")) {
                throw e;
            }
        }
    }

    public static Path getCurrentMonthsSampleBillFilePath() {
        LocalDateTime dateToday = com.vmware.photon.controller.model.adapters.awsadapter.TestUtils.getDateTimeToday();
        String newDate = com.vmware.photon.controller.model.adapters.awsadapter.TestUtils.getDateForBillName(dateToday);
        return  Paths.get("123456789-aws-billing-detailed-line-items-with-resources-and-tags-" + newDate + ".csv.zip");

    }

    public static void resourceStatsCollection(VerificationHost host,
            boolean isMock, String resourcePoolLink) throws Throwable {
        resourceStatsCollection(host, null,
                isMock ? EnumSet.of(TaskOption.IS_MOCK) : null, resourcePoolLink);
    }

    public static void resourceStatsCollection(VerificationHost host, URI peerURI,
            EnumSet<TaskOption> options, String resourcePoolLink) throws Throwable {
        StatsCollectionTaskState statsTask = performResourceStatsCollection(
                host, options, resourcePoolLink);

        // Wait for the stats collection task to be completed.
        host.waitForFinishedTask(StatsCollectionTaskState.class,
                createServiceURI(host, peerURI, statsTask.documentSelfLink));
    }

    /**
     * Performs stats collection for given resourcePoolLink
     *
     */
    public static StatsCollectionTaskState performResourceStatsCollection(
            VerificationHost host, EnumSet<TaskOption> options, String resourcePoolLink)
            throws Throwable {

        StatsCollectionTaskState statsCollectionTaskState =
                new StatsCollectionTaskState();

        statsCollectionTaskState.resourcePoolLink = resourcePoolLink;
        statsCollectionTaskState.options = EnumSet.noneOf(TaskOption.class);
        if (options != null) {
            statsCollectionTaskState.options = options;
        }

        URI uri = UriUtils.buildUri(host, StatsCollectionTaskService.FACTORY_LINK);
        StatsCollectionTaskState statsTask = TestUtils.doPost(
                host, statsCollectionTaskState,
                StatsCollectionTaskState.class, uri);

        return statsTask;
    }

    /**
     * Performs stats aggregation for given resourcePoolLink
     */
    public static void resourceStatsAggregation(VerificationHost host,
            String resourcePoolLink) throws Throwable {
        host.testStart(1);
        StatsAggregationTaskState statsAggregationTaskState = new StatsAggregationTaskState();
        Query taskQuery = Query.Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_RESOURCE_POOL_LINK, resourcePoolLink).build();
        statsAggregationTaskState.query =  taskQuery;
        statsAggregationTaskState.taskInfo = TaskState.createDirect();
        statsAggregationTaskState.metricNames = getMetricNames();

        host.send(Operation
                .createPost(
                        UriUtils.buildUri(host,
                                StatsAggregationTaskService.FACTORY_LINK))
                .setBody(statsAggregationTaskState)
                .setCompletion(host.getCompletion()));
        host.testWait();
    }

    /**
     * Define the metric names that should be aggregated.
     */
    public static Set<String> getMetricNames() {
        Set<String> metricNames = new HashSet<>();
        // CPU
        metricNames.add(PhotonModelConstants.CPU_UTILIZATION_PERCENT);

        // Memory
        metricNames.add(PhotonModelConstants.MEMORY_USED_PERCENT);

        // Cost
        metricNames.add(PhotonModelConstants.ESTIMATED_CHARGES);
        metricNames.add(PhotonModelConstants.COST);

        metricNames.addAll(getServiceMetrics());

        return metricNames;
    }

    public static final Set<String> SERVICES;

    static {
        Set<String> services = new HashSet<>();
        //AWS service/product names
        services.add("AWSCloudTrail");
        services.add("AmazonRoute53");
        services.add("AmazonSimpleDB");
        services.add("AmazonDynamoDB");
        services.add("AmazonCloudWatch");
        services.add("AmazonCloudFront");
        services.add("AmazonRDSService");
        services.add("AmazonElastiCache");
        services.add("AWSKeyManagementService");
        services.add("AmazonSimpleEmailService");
        services.add("AmazonSimpleQueueService");
        services.add("AmazonElasticComputeCloud");
        services.add("AmazonVirtualPrivateCloud");
        services.add("AmazonSimpleStorageService");
        services.add("AmazonSimpleNotificationService");
        SERVICES = Collections.unmodifiableSet(services);
    }

    /**
     * Returns set of service metrics that should be aggregated
     */
    private static Set<String> getServiceMetrics() {
        Set<String> metrics = new HashSet<>();
        for (String s : SERVICES) {
            metrics.add(String.format(PhotonModelConstants.SERVICE_RESOURCE_COST, s));
            metrics.add(String.format(PhotonModelConstants.SERVICE_OTHER_COST, s));
        }
        return metrics;
    }

    public static ServiceDocumentQueryResult getInternalTagsByType (VerificationHost host, String type) {
        Query query = Query.Builder.create()
                .addKindFieldClause(TagService.TagState.class)
                .addFieldClause(TagService.TagState.FIELD_NAME_KEY,
                        PhotonModelConstants.TAG_KEY_TYPE)
                .addFieldClause(TagService.TagState.FIELD_NAME_EXTERNAL, false)
                .addFieldClause(TagService.TagState.FIELD_NAME_VALUE, type)
                .build();

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .setQuery(query)
                .addOption(QuerySpecification.QueryOption.EXPAND_CONTENT)
                .addOption(QuerySpecification.QueryOption.TOP_RESULTS)
                .setResultLimit(getQueryResultLimit())
                .build();

        queryTask.documentSelfLink = UUID.randomUUID().toString();

        host.createQueryTaskService(queryTask, false, true, queryTask, null);
        ServiceDocumentQueryResult results = queryTask.results;
        return  results;
    }
}
