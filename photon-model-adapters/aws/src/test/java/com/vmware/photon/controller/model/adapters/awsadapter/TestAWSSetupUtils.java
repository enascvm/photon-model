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

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.getAWSNonTerminatedInstancesFilter;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.getRegionId;
import static com.vmware.photon.controller.model.tasks.ProvisioningUtils.createServiceURI;
import static com.vmware.photon.controller.model.tasks.ProvisioningUtils.getVMCount;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.StopInstancesRequest;
import com.amazonaws.services.ec2.model.StopInstancesResult;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.TerminateInstancesResult;

import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSBlockStorageEnumerationAdapterService;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSComputeDescriptionCreationAdapterService;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSComputeStateCreationAdapterService;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSEnumerationAdapterService;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSEnumerationAndCreationAdapterService;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSEnumerationAndDeletionAdapterService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.DiskService.DiskType;
import com.vmware.photon.controller.model.resources.FirewallService;
import com.vmware.photon.controller.model.resources.FirewallService.FirewallState;
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
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService.ResourceEnumerationTaskState;
import com.vmware.photon.controller.model.tasks.ResourceRemovalTaskService;
import com.vmware.photon.controller.model.tasks.ResourceRemovalTaskService.ResourceRemovalTaskState;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.photon.controller.model.tasks.TestUtils;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceStats;
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
    public static final String zoneId = "us-east-1";
    public static final String userData = null;
    public static final String aws = "Amazon Web Services";

    private static final String AWS_DEFAULT_VPC_ID = "vpc-95a29bf1";
    private static final String AWS_DEFAULT_VPC_CIDR = "172.31.0.0/16";
    private static final String AWS_DEFAULT_SUBNET_NAME = "default";
    private static final String AWS_DEFAULT_SUBNET_ID = "subnet-1f82fe22";
    private static final String AWS_DEFAULT_SUBNET_CIDR = "172.31.32.0/20";
    private static final String AWS_DEFAULT_GROUP_NAME = "cell-manager-security-group";
    private static final String AWS_DEFAULT_GROUP_ID = "sg-d46efeac";
    public static final int NUMBER_OF_NICS = 1;

    public static final String DEFAULT_AUTH_TYPE = "PublicKey";
    public static final String DEFAULT_ROOT_DISK_NAME = "CoreOS root disk";
    public static final String DEFAULT_CONFIG_LABEL = "cidata";
    public static final String DEFAULT_CONFIG_PATH = "user-data";
    public static final String DEFAULT_USER_DATA_FILE = "cloud_config_coreos.yml";
    public static final String DEFAULT_COREOS_USER = "core";
    public static final String DEFAULT_COREOS_PRIVATE_KEY_FILE = "private_coreos.key";
    public static final String SAMPLE_AWS_BILL = "123456789-aws-billing-detailed-line-items-with-resources-and-tags-2016-09.csv.zip";

    public static final String T2_NANO_INSTANCE_TYPE = "t2.nano";
    public static final String DEFAULT_SECURITY_GROUP_NAME = "cell-manager-security-group";
    public static final String BASELINE_INSTANCE_COUNT = "Baseline Instance Count ";
    public static final String BASELINE_COMPUTE_DESCRIPTION_COUNT = " Baseline Compute Description Count ";
    private static final float HUNDERED = 100.0f;
    public static final int AWS_VM_REQUEST_TIMEOUT_MINUTES = 5;
    public static final String AWS_INSTANCE_PREFIX = "i-";

    public static final String EC2_LINUX_AMI = "ami-0d4cfd66";
    public static final String EC2_WINDOWS_AMI = "ami-30540427";

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

    /**
     * Create a compute host description for an AWS instance
     */
    public static ComputeService.ComputeState createAWSComputeHost(VerificationHost host,
            String resourcePoolLink,
            String accessKey, String secretKey, boolean isAwsClientMock,
            String awsMockEndpointReference, Set<String> tagLinks)
            throws Throwable {

        AuthCredentialsServiceState auth = new AuthCredentialsServiceState();
        auth.type = DEFAULT_AUTH_TYPE;
        auth.privateKeyId = accessKey;
        auth.privateKey = secretKey;
        auth.documentSelfLink = UUID.randomUUID().toString();
        TestUtils.doPost(host, auth, AuthCredentialsService.AuthCredentialsServiceState.class,
                UriUtils.buildUri(host, AuthCredentialsService.FACTORY_LINK));
        String authLink = UriUtils.buildUriPath(AuthCredentialsService.FACTORY_LINK,
                auth.documentSelfLink);

        ComputeDescriptionService.ComputeDescription awshostDescription = new ComputeDescriptionService.ComputeDescription();

        awshostDescription.id = UUID.randomUUID().toString();
        awshostDescription.name = aws;
        awshostDescription.environmentName = aws;
        awshostDescription.documentSelfLink = awshostDescription.id;
        awshostDescription.supportedChildren = new ArrayList<String>();
        awshostDescription.supportedChildren.add(ComputeType.VM_GUEST.name());
        awshostDescription.instanceAdapterReference = UriUtils.buildUri(host,
                AWSUriPaths.AWS_INSTANCE_ADAPTER);
        awshostDescription.enumerationAdapterReference = UriUtils.buildUri(host,
                AWSUriPaths.AWS_ENUMERATION_ADAPTER);
        awshostDescription.statsAdapterReference = UriUtils.buildUri(host,
                AWSUriPaths.AWS_STATS_ADAPTER);

        awshostDescription.zoneId = zoneId;
        awshostDescription.regionId = zoneId;
        awshostDescription.authCredentialsLink = authLink;
        TestUtils.doPost(host, awshostDescription,
                ComputeDescriptionService.ComputeDescription.class,
                UriUtils.buildUri(host, ComputeDescriptionService.FACTORY_LINK));

        ComputeService.ComputeState awsComputeHost = new ComputeService.ComputeState();

        awsComputeHost.id = UUID.randomUUID().toString();
        awsComputeHost.name = awshostDescription.name;
        awsComputeHost.documentSelfLink = awsComputeHost.id;
        awsComputeHost.descriptionLink = UriUtils.buildUriPath(
                ComputeDescriptionService.FACTORY_LINK, awshostDescription.id);
        awsComputeHost.resourcePoolLink = resourcePoolLink;
        awsComputeHost.tagLinks = tagLinks;

        if (isAwsClientMock) {
            awsComputeHost.adapterManagementReference = UriUtils.buildUri(awsMockEndpointReference);
        } else {
            awsComputeHost.adapterManagementReference = UriUtils.buildUri(awsEndpointReference);
        }

        ComputeService.ComputeState returnState = TestUtils.doPost(host, awsComputeHost,
                ComputeService.ComputeState.class,
                UriUtils.buildUri(host, ComputeService.FACTORY_LINK));
        return returnState;
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
            String parentLink, String resourcePoolLink, @SuppressWarnings("rawtypes") Class clazz,
            Set<String> tagLinks)
            throws Throwable {
        return createAWSVMResource(host, parentLink, resourcePoolLink, clazz,
                instanceType_t2_micro,
                tagLinks);
    }

    /**
     * Create a compute resource for an AWS instance
     */
    public static ComputeService.ComputeState createAWSVMResource(VerificationHost host,
            String parentLink, String resourcePoolLink, @SuppressWarnings("rawtypes") Class clazz,
            String vmName,
            Set<String> tagLinks)
            throws Throwable {

        // Step 1: Create an auth credential to login to the VM
        AuthCredentialsServiceState auth = new AuthCredentialsServiceState();
        auth.type = DEFAULT_AUTH_TYPE;
        auth.userEmail = DEFAULT_COREOS_USER;
        auth.privateKey = TestUtils.loadTestResource(clazz,
                DEFAULT_COREOS_PRIVATE_KEY_FILE);
        auth.documentSelfLink = UUID.randomUUID().toString();
        TestUtils.doPost(host, auth, AuthCredentialsService.AuthCredentialsServiceState.class,
                UriUtils.buildUri(host, AuthCredentialsService.FACTORY_LINK));
        String authCredentialsLink = UriUtils.buildUriPath(AuthCredentialsService.FACTORY_LINK,
                auth.documentSelfLink);

        // Step 2: Create a VM desc
        ComputeDescriptionService.ComputeDescription awsVMDesc = new ComputeDescriptionService.ComputeDescription();

        awsVMDesc.id = instanceType_t2_micro;
        awsVMDesc.name = vmName;
        awsVMDesc.instanceType = instanceType_t2_micro;

        awsVMDesc.supportedChildren = new ArrayList<>();
        awsVMDesc.supportedChildren.add(ComputeType.DOCKER_CONTAINER.name());

        awsVMDesc.customProperties = new HashMap<>();
        awsVMDesc.customProperties
                .put(AWSConstants.AWS_SECURITY_GROUP, securityGroup);
        awsVMDesc.environmentName = AWSInstanceService.AWS_ENVIRONMENT_NAME;

        // set zone to east
        awsVMDesc.zoneId = zoneId;
        awsVMDesc.regionId = zoneId;

        awsVMDesc.authCredentialsLink = authCredentialsLink;

        // set the create service to the aws instance service
        awsVMDesc.instanceAdapterReference = UriUtils.buildUri(host,
                AWSUriPaths.AWS_INSTANCE_ADAPTER);
        awsVMDesc.statsAdapterReference = UriUtils.buildUri(host,
                AWSUriPaths.AWS_STATS_ADAPTER);

        ComputeDescriptionService.ComputeDescription vmComputeDesc = TestUtils.doPost(host,
                awsVMDesc,
                ComputeDescriptionService.ComputeDescription.class,
                UriUtils.buildUri(host, ComputeDescriptionService.FACTORY_LINK));
        // Step 3: create boot disk
        List<String> vmDisks = new ArrayList<>();
        DiskState rootDisk = new DiskState();
        rootDisk.id = UUID.randomUUID().toString();
        rootDisk.documentSelfLink = rootDisk.id;
        rootDisk.name = DEFAULT_ROOT_DISK_NAME;
        rootDisk.type = DiskType.HDD;
        rootDisk.sourceImageReference = URI.create(imageId);
        rootDisk.bootConfig = new DiskState.BootConfig();
        rootDisk.bootConfig.label = DEFAULT_CONFIG_LABEL;
        DiskState.BootConfig.FileEntry file = new DiskState.BootConfig.FileEntry();
        file.path = DEFAULT_CONFIG_PATH;
        file.contents = TestUtils.loadTestResource(clazz, DEFAULT_USER_DATA_FILE);
        rootDisk.bootConfig.files = new DiskState.BootConfig.FileEntry[] { file };

        TestUtils.doPost(host, rootDisk,
                DiskService.DiskState.class,
                UriUtils.buildUri(host, DiskService.FACTORY_LINK));
        vmDisks.add(UriUtils.buildUriPath(DiskService.FACTORY_LINK, rootDisk.id));

        // Create network state.
        NetworkState netState;
        {
            netState = new NetworkState();
            netState.id = AWS_DEFAULT_VPC_ID;
            netState.name = AWS_DEFAULT_VPC_ID;
            netState.documentSelfLink = netState.id;
            netState.authCredentialsLink = authCredentialsLink;
            netState.resourcePoolLink = resourcePoolLink;
            netState.subnetCIDR = AWS_DEFAULT_VPC_CIDR;
            netState.regionId = zoneId;
            netState.instanceAdapterReference = UriUtils.buildUri(host,
                    AWSUriPaths.AWS_NETWORK_ADAPTER);

            netState = TestUtils.doPost(host, netState, NetworkState.class,
                    UriUtils.buildUri(host, NetworkService.FACTORY_LINK));
        }

        // Create subnet state.
        SubnetState subnetState;
        {
            subnetState = new SubnetState();
            subnetState.id = AWS_DEFAULT_SUBNET_ID;
            subnetState.name = AWS_DEFAULT_SUBNET_NAME;
            subnetState.documentSelfLink = subnetState.id;
            subnetState.networkLink = UriUtils.buildUriPath(
                    NetworkService.FACTORY_LINK,
                    netState.id);
            subnetState.subnetCIDR = AWS_DEFAULT_SUBNET_CIDR;

            TestUtils.doPost(host, subnetState, SubnetState.class,
                    UriUtils.buildUri(host, SubnetService.FACTORY_LINK));
        }

        // Create network interface descriptions.
        NetworkInterfaceDescription nicDescription;
        {
            nicDescription = new NetworkInterfaceDescription();
            nicDescription.id = UUID.randomUUID().toString();
            nicDescription.assignment = IpAssignment.DYNAMIC;

            nicDescription = TestUtils.doPost(host, nicDescription, NetworkInterfaceDescription.class,
                    UriUtils.buildUri(host, NetworkInterfaceDescriptionService.FACTORY_LINK));
        }

        // Create firewall state
        FirewallState firewallState;
        {
            firewallState = new FirewallState();
            firewallState.authCredentialsLink = authCredentialsLink;
            firewallState.id = AWS_DEFAULT_GROUP_ID;
            firewallState.documentSelfLink = firewallState.id;
            firewallState.name = AWS_DEFAULT_GROUP_NAME;
            firewallState.networkDescriptionLink = UriUtils.buildUriPath(
                    NetworkService.FACTORY_LINK,
                    nicDescription.id);
            firewallState.tenantLinks = new ArrayList<>();
            firewallState.tenantLinks.add("tenant-linkA");
            ArrayList<FirewallService.FirewallState.Allow> ingressRules = new ArrayList<>();

            FirewallService.FirewallState.Allow ssh = new FirewallService.FirewallState.Allow();
            ssh.name = "ssh";
            ssh.protocol = "tcp";
            ssh.ipRange = "0.0.0.0/0";
            ssh.ports = new ArrayList<>();
            ssh.ports.add("22");
            ingressRules.add(ssh);
            firewallState.ingress = ingressRules;

            ArrayList<FirewallService.FirewallState.Allow> egressRules = new ArrayList<>();
            FirewallService.FirewallState.Allow out = new FirewallService.FirewallState.Allow();
            out.name = "out";
            out.protocol = "tcp";
            out.ipRange = "0.0.0.0/0";
            out.ports = new ArrayList<>();
            out.ports.add("1-65535");
            egressRules.add(out);
            firewallState.egress = egressRules;

            firewallState.regionId = "regionId";
            firewallState.resourcePoolLink = "/link/to/rp";
            firewallState.instanceAdapterReference = new URI(
                    "http://instanceAdapterReference");

            TestUtils.doPost(host, firewallState, FirewallState.class,
                    UriUtils.buildUri(host, FirewallService.FACTORY_LINK));
        }

        // Create nics
        List<String> nics = new ArrayList<>();
        for (int i = 0; i < NUMBER_OF_NICS; i++) {
            NetworkInterfaceState nicState;
            {
                nicState = new NetworkInterfaceState();
                nicState.id = UUID.randomUUID().toString();
                nicState.documentSelfLink = nicState.id;
                nicState.name = awsVMDesc.name + "-nic-" + i;

                nicState.networkLink = UriUtils.buildUriPath(
                        NetworkService.FACTORY_LINK,
                        netState.id);
                nicState.subnetLink = UriUtils.buildUriPath(
                        SubnetService.FACTORY_LINK,
                        subnetState.id);
                nicState.networkInterfaceDescriptionLink = nicDescription.documentSelfLink;

                String firewallLink = UriUtils.buildUriPath(
                        FirewallService.FACTORY_LINK,
                        firewallState.id);
                nicState.firewallLinks = new ArrayList<>();
                nicState.firewallLinks.add(firewallLink);

                nicState = TestUtils.doPost(host, nicState, NetworkInterfaceState.class,
                        UriUtils.buildUri(host, NetworkInterfaceService.FACTORY_LINK));

                nics.add(UriUtils.buildUriPath(NetworkInterfaceService.FACTORY_LINK, nicState.id));
            }
        }

        // Create compute state
        ComputeService.ComputeState resource;
        {
            resource = new ComputeService.ComputeState();
            resource.id = UUID.randomUUID().toString();
            resource.name = awsVMDesc.name;
            resource.parentLink = parentLink;
            resource.descriptionLink = vmComputeDesc.documentSelfLink;
            resource.resourcePoolLink = resourcePoolLink;
            resource.networkInterfaceLinks = nics;
            resource.diskLinks = vmDisks;
            resource.tagLinks = tagLinks;
        }

        ComputeService.ComputeState vmComputeState = TestUtils.doPost(host, resource,
                ComputeService.ComputeState.class,
                UriUtils.buildUri(host, ComputeService.FACTORY_LINK));
        return vmComputeState;
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
            VerificationHost host, int numberOfInstance, String instanceType)
            throws Throwable {
        host.log("Provisioning %d instances on the AWS endpoint using the EC2 client.",
                numberOfInstance);

        RunInstancesRequest runInstancesRequest = new RunInstancesRequest()
                .withImageId(EC2_LINUX_AMI).withInstanceType(instanceType)
                .withMinCount(numberOfInstance).withMaxCount(numberOfInstance)
                .withSecurityGroupIds(DEFAULT_SECURITY_GROUP_NAME);

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
            String ami) {

        RunInstancesRequest runInstancesRequest = new RunInstancesRequest()
                .withImageId(ami)
                .withInstanceType(instanceType_t2_micro)
                .withMinCount(1).withMaxCount(1)
                .withSecurityGroupIds(DEFAULT_SECURITY_GROUP_NAME);

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
     * @see <a href="https://github.com/treelogic-swe/aws-mock">aws-mock</a>
     * @param isAwsClientMock
     *            flag to use aws-mock
     * @param awsMockEndpointReference
     *            ec2 endpoint of aws-mock
     */
    public static void setAwsClientMockInfo(boolean isAwsClientMock,
            String awsMockEndpointReference) {
        AWSUtils.setAwsClientMock(isAwsClientMock);
        AWSUtils.setAwsMockHost(awsMockEndpointReference);
    }

    /**
     * Handler class to spawn off instances on the AWS EC2 endpoint.
     *
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
    public static void enumerateResourcesPreserveMissing(VerificationHost host, boolean isMock,
            String resourcePoolLink, String computeHostLinkDescription, String computeHostLink,
            String testCase) throws Throwable {
        EnumSet<TaskOption> options = EnumSet.of(TaskOption.PRESERVE_MISSING_RESOUCES);
        if (isMock) {
            options.add(TaskOption.IS_MOCK);
        }
        enumerateResources(host, null, options, resourcePoolLink,
                computeHostLinkDescription,
                computeHostLink, testCase, null);
    }

    /**
     * Enumerates resources on the AWS endpoint.
     */
    public static void enumerateResources(VerificationHost host, boolean isMock,
            String resourcePoolLink, String computeHostLinkDescription, String computeHostLink,
            String testCase) throws Throwable {
        enumerateResources(host, null, isMock ? EnumSet.of(TaskOption.IS_MOCK) : null,
                resourcePoolLink,
                computeHostLinkDescription,
                computeHostLink, testCase, null);
    }

    /**
     * Enumerates resources on the AWS endpoint. Expects a peerURI and the tenantLinks to be set.
     */
    public static void enumerateResources(VerificationHost host, URI peerURI,
            EnumSet<TaskOption> options, String resourcePoolLink,
            String computeHostLinkDescription,
            String computeHostLink,
            String testCase, List<String> tenantLinks) throws Throwable {
        // Perform resource enumeration on the AWS end point. Pass the references to the AWS compute
        // host.
        host.log("Performing resource enumeration");
        ResourceEnumerationTaskService.ResourceEnumerationTaskState enumTask = performResourceEnumeration(
                host, peerURI, options, resourcePoolLink,
                computeHostLinkDescription,
                computeHostLink,
                tenantLinks);
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
                                AWSComputeDescriptionCreationAdapterService.SELF_LINK)));
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
        host.log("\n==Total Time Spent in Storage Enumeration Workflow==\n");
        ServiceStats storageEnumerationStats = host.getServiceState(null, ServiceStats.class,
                UriUtils
                        .buildStatsUri(createServiceURI(host, peerURI,
                                AWSBlockStorageEnumerationAdapterService.SELF_LINK)));
        host.log(Utils.toJsonHtml(storageEnumerationStats));
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
    public static ResourceEnumerationTaskService.ResourceEnumerationTaskState performResourceEnumeration(
            VerificationHost host, URI peerURI, EnumSet<TaskOption> options,
            String resourcePoolLink, String computeDescriptionLink, String parentComputeLink,
            List<String> tenantLinks) throws Throwable {
        // Kick of a Resource Enumeration task to enumerate the instances on the AWS endpoint
        ResourceEnumerationTaskState enumerationTaskState = new ResourceEnumerationTaskService.ResourceEnumerationTaskState();

        enumerationTaskState.parentComputeLink = parentComputeLink;
        enumerationTaskState.enumerationAction = EnumerationAction.START;
        enumerationTaskState.adapterManagementReference = UriUtils
                .buildUri(AWSEnumerationAdapterService.SELF_LINK);

        enumerationTaskState.resourcePoolLink = resourcePoolLink;
        enumerationTaskState.options = EnumSet.noneOf(TaskOption.class);
        if (options != null) {
            enumerationTaskState.options = options;
        }

        if (tenantLinks != null) {
            enumerationTaskState.tenantLinks = tenantLinks;
        }
        URI uri = createServiceURI(host, peerURI, ResourceEnumerationTaskService.FACTORY_LINK);
        ResourceEnumerationTaskService.ResourceEnumerationTaskState enumTask = TestUtils.doPost(
                host, enumerationTaskState, ResourceEnumerationTaskState.class, uri);
        return enumTask;

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
        ArrayList<Boolean> deletionFlags = new ArrayList<Boolean>(instanceIdsToDelete.size());
        for (int i = 0; i < instanceIdsToDelete.size(); i++) {
            deletionFlags.add(i, Boolean.FALSE);
        }
        host.waitFor("Error waiting for EC2 client delete instances in test ", () -> {
            return computeInstancesTerminationState(client,
                    host, instanceIdsToDelete, deletionFlags);
        });

    }

    /**
     * Async handler for the deletion of instances from the AWS endpoint.
     *
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
     *
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
            CHECK_START,
            CHECK_TERMINATION,
            CHECK_STOP,
            GET_COUNT
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

}
