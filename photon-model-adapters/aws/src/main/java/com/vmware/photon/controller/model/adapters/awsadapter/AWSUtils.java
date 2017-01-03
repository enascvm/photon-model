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

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_GROUP_NAME_FILTER;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_TAG_NAME;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_VPC_ID_FILTER;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.INSTANCE_STATE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.INSTANCE_STATE_PENDING;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.INSTANCE_STATE_RUNNING;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.INSTANCE_STATE_SHUTTING_DOWN;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.INSTANCE_STATE_STOPPED;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.INSTANCE_STATE_STOPPING;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsyncClient;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest;
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest;
import com.amazonaws.services.ec2.model.CreateSecurityGroupResult;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult;
import com.amazonaws.services.ec2.model.DescribeTagsRequest;
import com.amazonaws.services.ec2.model.DescribeTagsResult;
import com.amazonaws.services.ec2.model.DescribeVpcsResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceState;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TagDescription;
import com.amazonaws.services.ec2.model.Vpc;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.transfer.TransferManager;

import com.vmware.photon.controller.model.adapters.awsadapter.AWSInstanceContext.AWSNicContext;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSCsvBillParser;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState.Rule;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * AWS utils.
 */
public class AWSUtils {
    public static final String AWS_FILTER_RESOURCE_ID = "resource-id";
    public static final String AWS_FILTER_VPC_ID = "vpc-id";
    public static final String NO_VALUE = "no-value";
    public static final String TILDA = "~";
    private static final int EXECUTOR_SHUTDOWN_INTERVAL_MINUTES = 5;
    public static final String DEFAULT_SECURITY_GROUP_NAME = "photon-model-sg";
    public static final String DEFAULT_SECURITY_GROUP_DESC = "VMware Photon model security group";
    public static final int[] DEFAULT_ALLOWED_PORTS = { 22, 443, 80, 8080,
            2376, 2375, 1 };
    public static final String DEFAULT_ALLOWED_NETWORK = "0.0.0.0/0";
    public static final String DEFAULT_PROTOCOL = "tcp";
    public static final String AWS_EC2_ENDPOINT = "/aws-mock/ec2-endpoint/";
    public static final String AWS_CLOUDWATCH_ENDPOINT = "/aws-mock/cloudwatch/";

    /**
     * Flag to use aws-mock, will be set in test files.
     * Aws-mock is a open-source tool for testing AWS services in a mock EC2 environment.
     *
     * @see <a href="https://github.com/treelogic-swe/aws-mock">aws-mock</a>
     */
    private static boolean IS_AWS_CLIENT_MOCK = false;

    /**
     * Mock Host and port http://<ip-address>:<port> of aws-mock, will be set in test files.
     */
    private static String awsMockHost = null;

    public static void setAwsClientMock(boolean isAwsClientMock) {
        IS_AWS_CLIENT_MOCK = isAwsClientMock;
    }

    public static void setAwsMockHost(String mockHost) {
        awsMockHost = mockHost;
    }

    public static boolean isAwsClientMock() {
        return System.getProperty("awsMockHost") == null ? IS_AWS_CLIENT_MOCK : true;
    }

    private static String getAWSMockHost() {
        return System.getProperty("awsMockHost") == null ? awsMockHost
                : System.getProperty("awsMockHost");

    }

    public static AmazonEC2AsyncClient getAsyncClient(
            AuthCredentialsServiceState credentials, String region,
            ExecutorService executorService) {
        AmazonEC2AsyncClient ec2AsyncClient = new AmazonEC2AsyncClient(
                new BasicAWSCredentials(credentials.privateKeyId,
                        credentials.privateKey),
                executorService);

        if (isAwsClientMock()) {
            ec2AsyncClient.setEndpoint(getAWSMockHost() + AWS_EC2_ENDPOINT);
            return ec2AsyncClient;
        }

        ec2AsyncClient.setRegion(Region.getRegion(Regions.fromName(region)));

        return ec2AsyncClient;

    }

    public static AmazonCloudWatchAsyncClient getStatsAsyncClient(
            AuthCredentialsServiceState credentials, String region,
            ExecutorService executorService, boolean isMockRequest) {
        AmazonCloudWatchAsyncClient client = new AmazonCloudWatchAsyncClient(
                new BasicAWSCredentials(credentials.privateKeyId,
                        credentials.privateKey),
                executorService);

        if (isAwsClientMock()) {
            client.setEndpoint(getAWSMockHost() + AWS_CLOUDWATCH_ENDPOINT);
            return client;
        }

        client.setRegion(Region.getRegion(Regions.fromName(region)));
        // make a call to validate credentials
        if (!isMockRequest) {
            client.describeAlarms();
        }
        return client;
    }

    public static TransferManager getS3AsyncClient(AuthCredentialsServiceState credentials,
            String region,
            ExecutorService executorService) {
        //Ignoring the region parameter for now.
        AmazonS3Client amazonS3Client = new AmazonS3Client(
                new BasicAWSCredentials(credentials.privateKeyId, credentials.privateKey));
        return new TransferManager(amazonS3Client, executorService);
    }

    /**
     * Synchronous Tagging of one or many AWS resources with the provided tags.
     */
    public static void tagResources(AmazonEC2AsyncClient client,
            Collection<Tag> tags, String... resourceIds) {
        if (isAwsClientMock()) {
            return;
        }

        CreateTagsRequest req = new CreateTagsRequest()
                .withResources(resourceIds).withTags(tags);

        client.createTags(req);
    }

    /**
     * Synchronous Tagging of one or many AWS resources with the provided name.
     */
    public static void tagResourcesWithName(AmazonEC2AsyncClient client, String name,
            String... resourceIds) {
        Tag awsNameTag = new Tag().withKey(AWS_TAG_NAME).withValue(name);
        tagResources(client, Collections.singletonList(awsNameTag), resourceIds);
    }

    /*
     * Return the tags for a giving resource
     */
    public static List<TagDescription> getResourceTags(String resourceID,
            AmazonEC2AsyncClient client) {
        Filter resource = new Filter().withName(AWS_FILTER_RESOURCE_ID)
                .withValues(resourceID);
        DescribeTagsRequest req = new DescribeTagsRequest()
                .withFilters(resource);
        DescribeTagsResult result = client.describeTags(req);
        return result.getTags();
    }

    public static Filter getFilter(String name, String value) {
        return new Filter().withName(name).withValues(value);
    }

    /**
     * Returns the region Id for the AWS instance
     *
     * @return the region id
     */
    public static String getRegionId(Instance i) {
        // Drop the zone suffix "a" ,"b" etc to get the region Id.
        String zoneId = i.getPlacement().getAvailabilityZone();
        String regiondId = zoneId.substring(0, zoneId.length() - 1);
        return regiondId;
    }

    /**
     * Maps the Aws machine state to {@link PowerState}
     *
     * @param state
     * @return the {@link PowerState} of the machine
     */
    public static PowerState mapToPowerState(InstanceState state) {
        PowerState powerState = PowerState.UNKNOWN;
        switch (state.getCode()) {
        case 16:
            powerState = PowerState.ON;
            break;
        case 80:
            powerState = PowerState.OFF;
            break;
        default:
            break;
        }
        return powerState;
    }

    /**
     * Creates a filter for the instances that are in non terminated state on the AWS endpoint.
     *
     * @return
     */
    public static Filter getAWSNonTerminatedInstancesFilter() {
        // Create a filter to only get non terminated instances from the remote instance.
        List<String> stateValues = new ArrayList<String>(Arrays.asList(INSTANCE_STATE_RUNNING,
                INSTANCE_STATE_PENDING, INSTANCE_STATE_STOPPING, INSTANCE_STATE_STOPPED,
                INSTANCE_STATE_SHUTTING_DOWN));
        Filter runningInstanceFilter = new Filter();
        runningInstanceFilter.setName(INSTANCE_STATE);
        runningInstanceFilter.setValues(stateValues);
        return runningInstanceFilter;
    }

    /**
     * Waits for termination of given executor service.
     */
    public static void awaitTermination(Logger logger, ExecutorService executor) {
        try {
            if (!executor.awaitTermination(EXECUTOR_SHUTDOWN_INTERVAL_MINUTES, TimeUnit.MINUTES)) {
                logger.log(Level.WARNING,
                        "Executor service can't be shutdown for AWS. Trying to shutdown now...");
                executor.shutdownNow();
            }
            logger.log(Level.FINE, "Executor service shutdown for AWS");
        } catch (InterruptedException e) {
            logger.log(Level.SEVERE, Utils.toString(e));
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.log(Level.SEVERE, Utils.toString(e));
        }
    }

    public static List<String> getOrCreateSecurityGroups(AWSInstanceContext aws) {
        if (aws.nics.size() > 0) {
            return getOrCreateSecurityGroups(aws.getPrimaryNic(), aws);
        } else {
            return getOrCreateSecurityGroups(null, aws);
        }
    }

    /*
     * method will create new or validate existing security group has the necessary settings for CM
     * to function. It will return the security group id that is required during instance
     * provisioning.
     * for each nicContext element provided, for each of its securityGroupStates, security group is
     * discovered from AWS
     * in case that there are no securityGroupStates, security group ID is obtained from the custom
     * properties
     * in case that none of the above methods discover a security group, the default one is discovered from AWS
     * in case that none of the above method discover a security group, a new security group is created
     */
    public static List<String> getOrCreateSecurityGroups(AWSNicContext nicCtx,
            AWSInstanceContext aws) {
        String groupId;
        SecurityGroup group;

        List<String> groupIds = new ArrayList<>();

        if (nicCtx != null) {
            if (nicCtx.securityGroupStates != null && !nicCtx.securityGroupStates.isEmpty()) {
                List<SecurityGroup> securityGroups = getSecurityGroups(aws.amazonEC2Client,
                        new ArrayList<>(nicCtx.securityGroupStates.keySet()),
                        nicCtx.vpc.getVpcId());
                for (SecurityGroup securityGroup : securityGroups) {
                    groupIds.add(securityGroup.getGroupId());
                }
                return groupIds;
            }
        }

        // use the security group provided in the description properties
        String sgId = getFromCustomProperties(aws.child.description,
                AWSConstants.AWS_SECURITY_GROUP_ID);
        if (sgId != null) {
            return Arrays.asList(sgId);
        }

        // in case no group is configured in the properties, attempt to discover the default one
        if (nicCtx != null && nicCtx.vpc != null) {
            try {
                group = getSecurityGroup(aws.amazonEC2Client, DEFAULT_SECURITY_GROUP_NAME,
                        nicCtx.vpc.getVpcId());
                if (group != null) {
                    return Arrays.asList(group.getGroupId());
                }
            } catch (AmazonServiceException t) {
                if (!t.getMessage().contains(
                        DEFAULT_SECURITY_GROUP_NAME)) {
                    throw t;
                }
            }
        }

        // if the group doesn't exist an exception is thrown. We won't throw a
        // missing group exception
        // we will continue and create the group
        groupId = createAWSSecurityGroup(aws);

        return Arrays.asList(groupId);
    }

    // method create a security group in the VPC from custom properties or the default VPC
    private static String createAWSSecurityGroup(AWSInstanceContext aws) {
        String groupId;
        try {
            String vpcId = null;
            // get the subnet cidr (if any)
            String subnetCidr = null;
            // in case subnet will be obtained from the default vpc, the security group should
            // as well be created there
            Vpc defaultVPC = getDefaultVPC(aws);
            if (defaultVPC != null) {
                vpcId = defaultVPC.getVpcId();
                subnetCidr = defaultVPC.getCidrBlock();
            }

            // no subnet or no vpc is not an option...
            if (subnetCidr == null || vpcId == null) {
                throw new AmazonServiceException("default VPC not found");
            }

            groupId = createSecurityGroup(aws.amazonEC2Client, vpcId);
            updateIngressRules(aws.amazonEC2Client, groupId,
                    getDefaultRules(subnetCidr));
        } catch (AmazonServiceException t) {
            if (t.getMessage().contains(
                    DEFAULT_SECURITY_GROUP_NAME)) {
                groupId = getSecurityGroup(aws.amazonEC2Client).getGroupId();
            } else {
                throw t;
            }
        }
        return groupId;
    }

    public static List<SecurityGroup> getSecurityGroups(AmazonEC2AsyncClient client,
            List<String> names, String vpcId) {

        DescribeSecurityGroupsRequest req = new DescribeSecurityGroupsRequest();

        req.withFilters(new Filter(AWS_GROUP_NAME_FILTER, names));
        if (vpcId != null) {
            req.withFilters(new Filter(AWS_VPC_ID_FILTER, Collections.singletonList(vpcId)));
        }

        DescribeSecurityGroupsResult groups = client
                .describeSecurityGroups(req);
        return groups != null ? groups.getSecurityGroups() : Collections.emptyList();
    }

    public static String getFromCustomProperties(
            ComputeDescriptionService.ComputeDescription description,
            String key) {
        if (description == null || description.customProperties == null) {
            return null;
        }

        return description.customProperties.get(key);
    }

    public static String createSecurityGroup(AmazonEC2AsyncClient client, String vpcId) {
        return createSecurityGroup(client, DEFAULT_SECURITY_GROUP_NAME,
                DEFAULT_SECURITY_GROUP_DESC, vpcId);
    }

    public static String createSecurityGroup(AmazonEC2AsyncClient client, String name,
            String description, String vpcId) {

        CreateSecurityGroupRequest req = new CreateSecurityGroupRequest()
                .withDescription(description)
                .withGroupName(name);

        // set vpc for the security group if provided
        if (vpcId != null) {
            req = req.withVpcId(vpcId);
        }

        CreateSecurityGroupResult result = client.createSecurityGroup(req);

        return result.getGroupId();
    }

    public static SecurityGroup getSecurityGroup(AmazonEC2AsyncClient client) {
        return getSecurityGroup(client, DEFAULT_SECURITY_GROUP_NAME);
    }

    public static SecurityGroup getSecurityGroup(AmazonEC2AsyncClient client,
            String name) {
        return getSecurityGroup(client, name, null);
    }

    public static SecurityGroup getSecurityGroup(AmazonEC2AsyncClient client,
            String name, String vpcId) {
        SecurityGroup cellGroup = null;

        DescribeSecurityGroupsRequest req = new DescribeSecurityGroupsRequest()
                .withFilters(new Filter("group-name", Collections.singletonList(name)));
        if (vpcId != null) {
            req.withFilters(new Filter("vpc-id", Collections.singletonList(vpcId)));
        }
        DescribeSecurityGroupsResult cellGroups = client
                .describeSecurityGroups(req);
        if (cellGroups != null && !cellGroups.getSecurityGroups().isEmpty()) {
            cellGroup = cellGroups.getSecurityGroups().get(0);
        }
        return cellGroup;
    }

    public static List<IpPermission> getDefaultRules(String subnet) {
        List<IpPermission> rules = new ArrayList<>();
        for (int port : DEFAULT_ALLOWED_PORTS) {
            if (port > 1) {
                rules.add(createRule(port));
            } else {
                rules.add(createRule(1, 65535, subnet, DEFAULT_PROTOCOL));
            }
        }
        return rules;
    }

    public static void updateIngressRules(AmazonEC2AsyncClient client,
            List<Rule> rules, String groupId) {
        updateIngressRules(client, groupId, buildRules(rules));
    }

    public static void updateIngressRules(AmazonEC2AsyncClient client, String groupId,
            List<IpPermission> rules) {
        AuthorizeSecurityGroupIngressRequest req = new AuthorizeSecurityGroupIngressRequest()
                .withGroupId(groupId).withIpPermissions(rules);
        client.authorizeSecurityGroupIngress(req);
    }

    public static IpPermission createRule(int port) {
        return createRule(port, port, DEFAULT_ALLOWED_NETWORK, DEFAULT_PROTOCOL);
    }

    public static IpPermission createRule(int fromPort, int toPort, String subnet,
            String protocol) {

        return new IpPermission().withIpProtocol(protocol)
                .withFromPort(fromPort).withToPort(toPort).withIpRanges(subnet);
    }

    /**
     * Builds the white list rules for the firewall
     */
    public static List<IpPermission> buildRules(List<Rule> allowRules) {
        ArrayList<IpPermission> awsRules = new ArrayList<>();
        for (Rule rule : allowRules) {
            int fromPort;
            int toPort;
            if (rule.ports.contains("-")) {
                String[] ports = rule.ports.split("-");
                fromPort = Integer.parseInt(ports[0]);
                toPort = Integer.parseInt(ports[1]);
            } else {
                fromPort = Integer.parseInt(rule.ports);
                toPort = fromPort;
            }
            awsRules.add(createRule(fromPort, toPort, rule.ipRangeCidr,
                    rule.protocol));
        }
        return awsRules;
    }

    /**
     * Gets the default VPC
     */
    public static Vpc getDefaultVPC(AWSInstanceContext aws) {
        DescribeVpcsResult result = aws.amazonEC2Client.describeVpcs();
        List<Vpc> vpcs = result.getVpcs();
        for (Vpc vpc : vpcs) {
            if (vpc.isDefault()) {
                return vpc;
            }
        }
        return null;
    }

    /**
     * Gets the subnet associated with the default VPC.
     */
    public static String getDefaultVPCSubnet(AWSInstanceContext aws) {
        Vpc defaultVpc = getDefaultVPC(aws);
        if (defaultVpc != null) {
            return defaultVpc.getCidrBlock();
        }
        return null;
    }

    /**
     * Calculate the average burn rate, given a list of datapoints from Amazon AWS.
     */
    public static Double calculateAverageBurnRate(List<Datapoint> dpList) {
        if (dpList.size() <= 1) {
            return null;
        }
        Datapoint oldestDatapoint = dpList.get(0);
        Datapoint latestDatapoint = dpList.get(dpList.size() - 1);

        // Adjust oldest datapoint to account for billing cycle when the estimated charges is reset to 0.
        // Iterate over the sublist from the oldestDatapoint element + 1 to the latestDatapoint element (excluding).
        // If the oldestDatapoint value is greater than the latestDatapoint value,
        // move the oldestDatapoint pointer until the oldestDatapoint value is less than the latestDatapoint value.
        // Eg: 4,5,6,7,0,1,2,3 -> 4 is greater than 3. Move the pointer until 0.
        // OldestDatapoint value is 0 and the latestDatapoint value is 3.
        for (Datapoint datapoint : dpList.subList(1, dpList.size() - 1)) {
            if (latestDatapoint.getAverage() > oldestDatapoint.getAverage()) {
                break;
            }
            oldestDatapoint = datapoint;
        }

        double averageBurnRate = (latestDatapoint.getAverage()
                - oldestDatapoint.getAverage())
                / getDateDifference(oldestDatapoint.getTimestamp(),
                latestDatapoint.getTimestamp(), TimeUnit.HOURS);
        // If there are only 2 datapoints and the oldestDatapoint is greater than the latestDatapoint, value will be negative.
        // Eg: oldestDatapoint = 5 and latestDatapoint = 0, when the billing cycle is reset.
        // In such cases, set the burn rate value to 0
        averageBurnRate = (averageBurnRate < 0 ? 0 : averageBurnRate);
        return averageBurnRate;
    }

    /**
     * Calculate the current burn rate, given a list of datapoints from Amazon AWS.
     */
    public static Double calculateCurrentBurnRate(List<Datapoint> dpList) {
        if (dpList.size() <= 7) {
            return null;
        }
        Datapoint dayOldDatapoint = dpList.get(dpList.size() - 7);
        Datapoint latestDatapoint = dpList.get(dpList.size() - 1);

        // Adjust the dayOldDatapoint to account for billing cycle when the estimated charges is reset to 0.
        // Iterate over the sublist from the oldestDatapoint element + 1 to the latestDatapoint element.
        // If the oldestDatapoint value is greater than the latestDatapoint value,
        // move the oldestDatapoint pointer until the oldestDatapoint value is less than the latestDatapoint value.
        // Eg: 4,5,6,7,0,1,2,3 -> 4 is greater than 3. Move the pointer until 0.
        // OldestDatapoint value is 0 and the latestDatapoint value is 3.
        for (Datapoint datapoint : dpList.subList(dpList.size() - 6, dpList.size() - 1)) {
            if (latestDatapoint.getAverage() > dayOldDatapoint.getAverage()) {
                break;
            }
            dayOldDatapoint = datapoint;
        }

        double currentBurnRate = (latestDatapoint.getAverage()
                - dayOldDatapoint.getAverage())
                / getDateDifference(dayOldDatapoint.getTimestamp(),
                latestDatapoint.getTimestamp(), TimeUnit.HOURS);
        // If there are only 2 datapoints and the oldestDatapoint is greater than the latestDatapoint, value will be negative.
        // Eg: oldestDatapoint = 5 and latestDatapoint = 0, when the billing cycle is reset.
        // In such cases, set the burn rate value to 0
        currentBurnRate = (currentBurnRate < 0 ? 0 : currentBurnRate);
        return currentBurnRate;
    }

    private static long getDateDifference(Date oldDate, Date newDate, TimeUnit timeUnit) {
        long differenceInMillies = newDate.getTime() - oldDate.getTime();
        return timeUnit.convert(differenceInMillies, TimeUnit.MILLISECONDS);
    }

    public static String autoDiscoverBillsBucketName(AmazonS3 s3Client, String awsAccountId) {
        String billFilePrefix = awsAccountId + AWSCsvBillParser.AWS_DETAILED_BILL_CSV_FILE_NAME_MID;
        for (Bucket bucket : s3Client.listBuckets()) {
            // For each bucket accessible to this client, try to search for files with the 'billFilePrefix'
            ObjectListing objectListing = s3Client.listObjects(bucket.getName(), billFilePrefix);
            if (!objectListing.getObjectSummaries().isEmpty()) {
                // This means that this bucket contains zip files representing the detailed csv bills.
                return bucket.getName();
            }
        }
        return null;
    }
}