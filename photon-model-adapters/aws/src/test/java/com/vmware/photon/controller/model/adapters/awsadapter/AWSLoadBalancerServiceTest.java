/*
 * Copyright (c) 2015-2017 VMware, Inc. All Rights Reserved.
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

import static com.vmware.photon.controller.model.adapters.awsadapter.TestUtils.getExecutor;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingAsyncClient;
import com.amazonaws.services.elasticloadbalancing.model.DeleteLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest;
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersResult;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.PhotonModelMetricServices;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapterapi.LoadBalancerInstanceRequest.InstanceRequestType;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSSecurityGroupClient;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.LoadBalancerService;
import com.vmware.photon.controller.model.resources.LoadBalancerService.LoadBalancerState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.ProvisionLoadBalancerTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionLoadBalancerTaskService.ProvisionLoadBalancerTaskState;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Tests for the {@link AWSLoadBalancerService} class.
 */
public class AWSLoadBalancerServiceTest extends BaseModelTest {
    private static final int DEFAULT_TIMEOUT_SECONDS = 200;
    private static final String TEST_LOAD_BALANCER_NAME_PREFIX = "pm-test-";

    public String secretKey = "test123";
    public String accessKey = "blas123";
    public String regionId = TestAWSSetupUtils.regionId;
    public String subnetId = "subnet123";
    private int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
    public boolean isMock = true;
    private AmazonElasticLoadBalancingAsyncClient client;
    private AWSSecurityGroupClient securityGroupClient;

    private String lbName;
    private String sgId;

    private EndpointState endpointState;

    @Before
    public void setUp() throws Throwable {
        CommandLineArgumentParser.parseFromProperties(this);

        try {
            PhotonModelServices.startServices(this.host);
            PhotonModelMetricServices.startServices(this.host);
            PhotonModelTaskServices.startServices(this.host);
            PhotonModelAdaptersRegistryAdapters.startServices(this.host);
            AWSAdapters.startServices(this.host);

            AuthCredentialsServiceState creds = new AuthCredentialsServiceState();
            creds.privateKey = this.secretKey;
            creds.privateKeyId = this.accessKey;
            this.client = AWSUtils.getLoadBalancingAsyncClient(creds, this.regionId,
                    TestUtils.getExecutor());
            this.securityGroupClient  = new AWSSecurityGroupClient(
                    AWSUtils.getAsyncClient(creds, this.regionId, getExecutor()));

            this.host.setTimeoutSeconds(this.timeoutSeconds);
            this.host.waitForServiceAvailable(PhotonModelAdaptersRegistryAdapters.LINKS);
            this.host.waitForServiceAvailable(AWSAdapters.LINKS);

            this.endpointState = createEndpointState();

        } catch (Throwable e) {
            this.host.log("Error starting up services for the test %s", e.getMessage());
            throw new Exception(e);
        }
    }

    @After
    public void tearDown() {
        if (this.lbName != null) {
            deleteAwsLoadBalancer(this.lbName);
        }
        if (this.sgId != null) {
            deleteAwsSecurityGroup(this.sgId);
        }
    }

    @Test
    public void testCreateDeleteLoadBalancer() throws Throwable {
        this.lbName = generateLbName();
        LoadBalancerState lb = createLoadBalancerState(this.lbName);
        kickOffLoadBalancerProvision(InstanceRequestType.CREATE, lb.documentSelfLink,
                TaskStage.FINISHED);

        lb = getServiceSynchronously(lb.documentSelfLink, LoadBalancerState.class);

        if (!this.isMock) {
            LoadBalancerDescription awsLoadBalancer = getAwsLoadBalancer(this.lbName);
            String securityGroupDocumentSelfLink = lb.customProperties.get(AWSLoadBalancerService
                    .SECURITY_GROUP_DOCUMENT_SELF_LINK);

            assertNotNull(securityGroupDocumentSelfLink);

            SecurityGroupState sgs = getServiceSynchronously(securityGroupDocumentSelfLink,
                    SecurityGroupState.class);

            this.sgId = sgs.id;

            SecurityGroup securityGroup = getAwsSecurityGroup(sgs.id);
            assertNotNull(awsLoadBalancer);
            assertEquals(awsLoadBalancer.getDNSName(), lb.address);
            assertEquals("internet-facing", awsLoadBalancer.getScheme());

            assertNotNull(securityGroup);

            String lbSecGroupId = awsLoadBalancer.getSecurityGroups().stream().findFirst()
                    .orElse(null);
            assertEquals(securityGroup.getGroupId(), lbSecGroupId);
        }

        kickOffLoadBalancerProvision(InstanceRequestType.DELETE, lb.documentSelfLink,
                TaskStage.FINISHED);

        if (!this.isMock) {
            assertNull(getAwsLoadBalancer(this.lbName));
            assertNull(getAwsSecurityGroup(this.sgId));
        }

        this.lbName = null;
        this.sgId = null;
    }

    private EndpointState createEndpointState() throws Throwable {

        EndpointState endpoint = new EndpointState();

        String endpointType = EndpointType.aws.name();
        endpoint.id = endpointType + "Id";
        endpoint.name = endpointType + "Name";
        endpoint.endpointType = endpointType;
        endpoint.tenantLinks = Collections.singletonList(endpointType + "Tenant");
        endpoint.authCredentialsLink = createAuthCredentialsState().documentSelfLink;
        endpoint.resourcePoolLink = "dummy";

        return postServiceSynchronously(
                EndpointService.FACTORY_LINK,
                endpoint,
                EndpointState.class);
    }

    private AuthCredentialsServiceState createAuthCredentialsState() throws Throwable {

        AuthCredentialsServiceState creds = new AuthCredentialsServiceState();

        creds.privateKey = this.secretKey;
        creds.privateKeyId = this.accessKey;

        return postServiceSynchronously(
                AuthCredentialsService.FACTORY_LINK,
                creds,
                AuthCredentialsServiceState.class);
    }

    private SubnetState createSubnetState(String name) throws Throwable {
        SubnetState subnetState = new SubnetState();
        subnetState.name = name;
        subnetState.id = name;
        subnetState.endpointLink = this.endpointState.documentSelfLink;
        subnetState.networkLink = "dummy";
        subnetState.subnetCIDR = "10.10.10.10/24";
        subnetState.tenantLinks = this.endpointState.tenantLinks;

        return postServiceSynchronously(SubnetService.FACTORY_LINK, subnetState,
              SubnetState.class);
    }

    private LoadBalancerState createLoadBalancerState(String name) throws Throwable {
        LoadBalancerState state = new LoadBalancerState();
        state.name = name;
        state.endpointLink = this.endpointState.documentSelfLink;
        state.regionId = this.regionId;
        state.computeLinks = new HashSet<>();
        state.subnetLinks = new HashSet<>();
        state.subnetLinks.add(createSubnetState(this.subnetId).documentSelfLink);
        state.protocol = "HTTP";
        state.port = 80;
        state.instanceProtocol = "HTTP";
        state.instancePort = 80;
        state.instanceAdapterReference = UriUtils.buildUri(this.host,
                AWSLoadBalancerService.SELF_LINK);
        state.internetFacing = Boolean.TRUE;

        return postServiceSynchronously(LoadBalancerService.FACTORY_LINK, state,
                LoadBalancerState.class);
    }

    private static String generateLbName() {
        return TEST_LOAD_BALANCER_NAME_PREFIX + System.currentTimeMillis();
    }

    private ProvisionLoadBalancerTaskState kickOffLoadBalancerProvision(
            InstanceRequestType requestType, String loadBalancerLink, TaskStage expectedTaskState)
            throws Throwable {
        ProvisionLoadBalancerTaskState taskState = new ProvisionLoadBalancerTaskState();
        taskState.requestType = requestType;
        taskState.loadBalancerLink = loadBalancerLink;
        taskState.isMockRequest = this.isMock;

        // Start/Post load balancer provisioning task
        taskState = postServiceSynchronously(
                ProvisionLoadBalancerTaskService.FACTORY_LINK,
                taskState,
                ProvisionLoadBalancerTaskState.class);

        // Wait for the task to complete
        taskState = waitForFinishedTask(ProvisionLoadBalancerTaskState.class,
                taskState.documentSelfLink);
        assertEquals(expectedTaskState, taskState.taskInfo.stage);
        return taskState;
    }

    private LoadBalancerDescription getAwsLoadBalancer(String name) {
        DescribeLoadBalancersRequest describeRequest = new DescribeLoadBalancersRequest()
                .withLoadBalancerNames(name);

        DescribeLoadBalancersResult describeResult = null;

        try {
            describeResult = this.client.describeLoadBalancers(describeRequest);
        } catch (Exception e) {
            this.host.log("Exception describing load balancers with name '%s': %s", name,
                    e.toString());
        }

        Collection<LoadBalancerDescription> lbs = describeResult != null
                ? describeResult.getLoadBalancerDescriptions() : null;
        if (lbs == null || lbs.isEmpty()) {
            return null;
        }
        if (lbs.size() > 1) {
            throw new IllegalStateException(
                    "More than one load balancers found with name '" + name + "'.");
        }
        return lbs.iterator().next();
    }

    private void deleteAwsLoadBalancer(String name) {
        if (this.isMock) {
            return;
        }

        try {
            DeleteLoadBalancerRequest deleteRequest = new DeleteLoadBalancerRequest(name);
            this.client.deleteLoadBalancer(deleteRequest);
        } catch (Exception e) {
            this.host.log("Exception deleting a load balancer '%s': %s", name, e.toString());
        }
    }

    private SecurityGroup getAwsSecurityGroup(String id) {
        try {
            return this.securityGroupClient.getSecurityGroupById(id);
        } catch (Exception e) {
            this.host.log("Exception describing security group with id '%s': %s", id,
                    e.toString());
        }
        return null;
    }

    private void deleteAwsSecurityGroup(String id) {
        if (this.isMock) {
            return;
        }

        try {
            this.securityGroupClient.deleteSecurityGroup(id);
        } catch (Exception e) {
            this.host.log("Exception deleting security group '%s': %s", id, e.toString());
        }
    }

}
