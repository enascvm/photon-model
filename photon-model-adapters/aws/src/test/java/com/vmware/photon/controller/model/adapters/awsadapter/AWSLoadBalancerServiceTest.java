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

import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.EC2_LINUX_AMI;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.deleteVMsUsingEC2Client;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.provisionAWSVMWithEC2Client;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestUtils.getExecutor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletionException;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingAsyncClient;
import com.amazonaws.services.elasticloadbalancing.model.DeleteLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest;
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersResult;
import com.amazonaws.services.elasticloadbalancing.model.HealthCheck;
import com.amazonaws.services.elasticloadbalancing.model.Listener;
import com.amazonaws.services.elasticloadbalancing.model.ListenerDescription;
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
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService.LoadBalancerDescription.HealthCheckConfiguration;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService.LoadBalancerDescription.Protocol;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService.LoadBalancerDescription.RouteConfiguration;
import com.vmware.photon.controller.model.resources.LoadBalancerService;
import com.vmware.photon.controller.model.resources.LoadBalancerService.LoadBalancerState;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.ProvisionLoadBalancerTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionLoadBalancerTaskService.ProvisionLoadBalancerTaskState;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;
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
    public String vpcId = "vpc123";
    private int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
    public boolean isMock = true;
    private AmazonElasticLoadBalancingAsyncClient client;
    private AWSSecurityGroupClient securityGroupClient;

    private String lbName;
    private String sgId;
    private ComputeState cs1;
    private ComputeState cs2;
    private List<String> instancesToCleanUp = new ArrayList<>();

    private EndpointState endpointState;
    private AmazonEC2AsyncClient ec2client;

    @Override
    @Before
    public void setUp() throws Throwable {
        CommandLineArgumentParser.parseFromProperties(this);

        try {
            PhotonModelServices.startServices(this.host);
            PhotonModelMetricServices.startServices(this.host);
            PhotonModelTaskServices.startServices(this.host);
            PhotonModelAdaptersRegistryAdapters.startServices(this.host);
            AWSAdaptersTestUtils.startServicesSynchronously(this.host);

            AuthCredentialsServiceState creds = new AuthCredentialsServiceState();
            creds.privateKey = this.secretKey;
            creds.privateKeyId = this.accessKey;

            TestContext lbWaitContext = new TestContext(1,  Duration.ofSeconds(30L));
            AWSUtils.getAwsLoadBalancingAsyncClient(creds, this.regionId, getExecutor())
                    .exceptionally(t -> {
                        lbWaitContext.fail(t);
                        throw new CompletionException(t);
                    })
                    .thenAccept(ec2Client -> {
                        this.client = ec2Client;
                        lbWaitContext.complete();
                    });
            lbWaitContext.await();

            TestContext ec2WaitContext = new TestContext(1,  Duration.ofSeconds(30L));
            AWSUtils.getEc2AsyncClient(creds, this.regionId, getExecutor())
                    .exceptionally(t -> {
                        ec2WaitContext.fail(t);
                        throw new CompletionException(t);
                    })
                    .thenAccept(ec2Client -> {
                        this.ec2client = ec2Client;
                        ec2WaitContext.complete();
                    });
            ec2WaitContext.await();

            TestContext secGroupWaitContext = new TestContext(1,  Duration.ofSeconds(30L));
            AWSUtils.getEc2AsyncClient(creds, this.regionId, getExecutor())
                    .exceptionally(t -> {
                        secGroupWaitContext.fail(t);
                        throw new CompletionException(t);
                    })
                    .thenAccept(ec2Client -> {
                        this.securityGroupClient = new AWSSecurityGroupClient(ec2Client);
                        secGroupWaitContext.complete();
                    });
            secGroupWaitContext.await();

            this.host.setTimeoutSeconds(this.timeoutSeconds);

            this.endpointState = createEndpointState();

            String vm1 = "vm1";
            String vm2 = "vm2";

            if (!this.isMock) {
                vm1 = provisionAWSVMWithEC2Client(this.host, this.ec2client, EC2_LINUX_AMI,
                        this.subnetId, null);
                this.instancesToCleanUp.add(vm1);
                vm2 = provisionAWSVMWithEC2Client(this.host, this.ec2client, EC2_LINUX_AMI,
                        this.subnetId, null);
                this.instancesToCleanUp.add(vm2);
            }
            this.cs1 = createComputeState(vm1);
            this.cs2 = createComputeState(vm2);

        } catch (Throwable e) {
            this.host.log("Error starting up services for the test %s", e.getMessage());
            throw new Exception(e);
        }
    }

    @After
    public void tearDown() throws Throwable {
        if (this.lbName != null) {
            deleteAwsLoadBalancer(this.lbName);
        }
        if (!this.instancesToCleanUp.isEmpty()) {
            deleteVMsUsingEC2Client(this.ec2client, this.host, this.instancesToCleanUp);
        }
        if (this.sgId != null) {
            deleteAwsSecurityGroup(this.sgId);
        }
    }

    @Test
    public void testCreateUpdateDeleteLoadBalancer() throws Throwable {
        // set name with invalid characters and more than 32 characters
        this.lbName = generateLbName() + "-1234567890-1234567890-1234567890_;.,/";
        LoadBalancerState lb = createLoadBalancerState(this.lbName);

        // Provision load balancer
        kickOffLoadBalancerProvision(InstanceRequestType.CREATE, lb.documentSelfLink,
                TaskStage.FINISHED);

        lb = getServiceSynchronously(lb.documentSelfLink, LoadBalancerState.class);
        this.lbName = lb.name;
        if (!this.isMock) {
            assertNotNull(lb.securityGroupLinks);
            String securityGroupDocumentSelfLink = lb.securityGroupLinks.iterator().next();
            assertNotNull(securityGroupDocumentSelfLink);

            SecurityGroupState sgs = getServiceSynchronously(securityGroupDocumentSelfLink,
                    SecurityGroupState.class);

            this.sgId = sgs.id;

            LoadBalancerDescription awsLoadBalancer = getAwsLoadBalancer(this.lbName);
            assertNotNull(awsLoadBalancer);
            assertEquals(awsLoadBalancer.getDNSName(), lb.address);
            assertEquals("internet-facing", awsLoadBalancer.getScheme());
            assertEquals(1, awsLoadBalancer.getInstances().size());

            List<ListenerDescription> listeners = awsLoadBalancer.getListenerDescriptions();
            assertEquals(2, listeners.size());
            verifyListeners(lb.routes, listeners);
            verifyHealthCheckConfiguration(lb.routes.get(0), awsLoadBalancer.getHealthCheck());

            SecurityGroup securityGroup = getAwsSecurityGroup(sgs.id);
            assertNotNull(securityGroup);

            String lbSecGroupId = awsLoadBalancer.getSecurityGroups().stream().findFirst()
                    .orElse(null);
            assertEquals(securityGroup.getGroupId(), lbSecGroupId);

        }

        // Update load balancer from 1 machines to 2 to simulate scale-out
        if (!this.isMock) {
            lb.computeLinks = new HashSet<>(
                    Arrays.asList(this.cs1.documentSelfLink, this.cs2.documentSelfLink));
            putServiceSynchronously(lb.documentSelfLink, lb);
        }

        kickOffLoadBalancerProvision(InstanceRequestType.UPDATE, lb.documentSelfLink,
                TaskStage.FINISHED);

        if (!this.isMock) {
            LoadBalancerDescription awsLoadBalancer = getAwsLoadBalancer(this.lbName);

            assertNotNull(awsLoadBalancer);
            assertEquals(2, awsLoadBalancer.getInstances().size());

            // Update load balancer from 2 machines to 1 to simulate scale-in
            lb.computeLinks = Collections.singleton(this.cs1.documentSelfLink);
            putServiceSynchronously(lb.documentSelfLink, lb);

            kickOffLoadBalancerProvision(InstanceRequestType.UPDATE, lb.documentSelfLink,
                    TaskStage.FINISHED);

            awsLoadBalancer = getAwsLoadBalancer(this.lbName);

            assertNotNull(awsLoadBalancer);
            assertEquals(1, awsLoadBalancer.getInstances().size());
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

    private void verifyListeners(List<RouteConfiguration> routeConfigurations,
            List<ListenerDescription> listenerDescriptions) {

        routeConfigurations.forEach(route -> {
            Listener listener = getListenerByPort(Integer.valueOf(route.port),
                    listenerDescriptions);

            assertEquals(Integer.valueOf(route.port), listener.getLoadBalancerPort());
            assertEquals(Integer.valueOf(route.instancePort), listener.getInstancePort());

            // Load Balancer https protocol is translated to tcp
            if (Protocol.HTTPS.name().equalsIgnoreCase(route.protocol)) {
                assertEquals(Protocol.TCP.name(), listener.getProtocol());
                assertEquals(Protocol.TCP.name(), listener.getInstanceProtocol());
            } else {
                assertEquals(route.protocol, listener.getProtocol());
                assertEquals(route.instanceProtocol, listener.getInstanceProtocol());
            }

        });
    }

    private Listener getListenerByPort(Integer port, List<ListenerDescription> descriptions) {
        ListenerDescription listenerDescription = descriptions.stream()
                .filter(ld -> ld.getListener().getLoadBalancerPort().equals(port)).findFirst()
                .orElse(null);

        assertNotNull(listenerDescription);
        return listenerDescription.getListener();
    }

    private void verifyHealthCheckConfiguration(RouteConfiguration routeConfiguration,
            HealthCheck healthCheck) {

        HealthCheckConfiguration healthCheckConfiguration = routeConfiguration.healthCheckConfiguration;

        assertEquals(healthCheckConfiguration.timeoutSeconds, healthCheck.getTimeout());
        assertEquals(healthCheckConfiguration.healthyThreshold, healthCheck.getHealthyThreshold());
        assertEquals(healthCheckConfiguration.intervalSeconds, healthCheck.getInterval());
        assertEquals(healthCheckConfiguration.unhealthyThreshold,
                healthCheck.getUnhealthyThreshold());

        String target = healthCheckConfiguration.protocol + ":" + healthCheckConfiguration.port
                + healthCheckConfiguration.urlPath;
        assertEquals(target, healthCheck.getTarget());
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
        endpoint.regionId = this.regionId;

        return postServiceSynchronously(EndpointService.FACTORY_LINK, endpoint,
                EndpointState.class);
    }

    private AuthCredentialsServiceState createAuthCredentialsState() throws Throwable {

        AuthCredentialsServiceState creds = new AuthCredentialsServiceState();

        creds.privateKey = this.secretKey;
        creds.privateKeyId = this.accessKey;

        return postServiceSynchronously(AuthCredentialsService.FACTORY_LINK, creds,
                AuthCredentialsServiceState.class);
    }

    private NetworkState createNetworkState(String id) throws Throwable {
        NetworkState networkState = new NetworkState();
        networkState.name = id;
        networkState.id = id;
        networkState.endpointLink = this.endpointState.documentSelfLink;
        networkState.endpointLinks = new HashSet<String>();
        networkState.endpointLinks.add(this.endpointState.documentSelfLink);
        networkState.tenantLinks = this.endpointState.tenantLinks;
        networkState.instanceAdapterReference = UriUtils
                .buildUri(this.host, AWSNetworkService.SELF_LINK);
        networkState.resourcePoolLink = this.endpointState.resourcePoolLink;
        networkState.regionId = this.endpointState.regionId;

        return postServiceSynchronously(NetworkService.FACTORY_LINK, networkState,
                NetworkState.class);
    }

    private SubnetState createSubnetState(String id, String networkLink) throws Throwable {
        SubnetState subnetState = new SubnetState();
        subnetState.name = id;
        subnetState.id = id;
        subnetState.endpointLink = this.endpointState.documentSelfLink;
        subnetState.endpointLinks = new HashSet<String>();
        subnetState.endpointLinks.add(this.endpointState.documentSelfLink);
        subnetState.networkLink = networkLink;
        subnetState.subnetCIDR = "10.10.10.10/24";
        subnetState.tenantLinks = this.endpointState.tenantLinks;

        return postServiceSynchronously(SubnetService.FACTORY_LINK, subnetState, SubnetState.class);
    }

    private LoadBalancerState createLoadBalancerState(String name) throws Throwable {
        LoadBalancerState state = new LoadBalancerState();
        state.name = name;
        state.endpointLink = this.endpointState.documentSelfLink;
        state.endpointLinks = new HashSet<String>();
        state.endpointLinks.add(this.endpointState.documentSelfLink);
        state.regionId = this.regionId;
        state.computeLinks = Collections.singleton(this.cs1.documentSelfLink);
        state.subnetLinks = new HashSet<>();
        state.subnetLinks.add(
                createSubnetState(this.subnetId,
                        createNetworkState(this.vpcId).documentSelfLink).documentSelfLink);

        RouteConfiguration route1 = new RouteConfiguration();
        route1.protocol = Protocol.HTTP.name();
        route1.port = "80";
        route1.instanceProtocol = Protocol.HTTP.name();
        route1.instancePort = "80";
        route1.healthCheckConfiguration = new HealthCheckConfiguration();
        route1.healthCheckConfiguration.protocol = Protocol.HTTP.name();
        route1.healthCheckConfiguration.port = "80";
        route1.healthCheckConfiguration.urlPath = "/test.html";
        route1.healthCheckConfiguration.intervalSeconds = 60;
        route1.healthCheckConfiguration.healthyThreshold = 2;
        route1.healthCheckConfiguration.unhealthyThreshold = 5;
        route1.healthCheckConfiguration.timeoutSeconds = 5;

        RouteConfiguration route2 = new RouteConfiguration();
        route2.protocol = Protocol.HTTPS.name();
        route2.port = "443";
        route2.instanceProtocol = Protocol.HTTPS.name();
        route2.instancePort = "443";

        state.routes = Arrays.asList(route1, route2);
        state.internetFacing = Boolean.TRUE;
        state.instanceAdapterReference = UriUtils
                .buildUri(this.host, AWSLoadBalancerService.SELF_LINK);

        return postServiceSynchronously(LoadBalancerService.FACTORY_LINK, state,
                LoadBalancerState.class);
    }

    private static String generateLbName() {
        return TEST_LOAD_BALANCER_NAME_PREFIX + System.currentTimeMillis();
    }

    private ComputeState createComputeState(String id) throws Throwable {
        ComputeDescription computeDescription = new ComputeDescription();
        computeDescription.id = id;

        computeDescription = postServiceSynchronously(ComputeDescriptionService.FACTORY_LINK,
                computeDescription, ComputeDescription.class);

        ComputeState computeState = new ComputeState();
        computeState.descriptionLink = computeDescription.documentSelfLink;
        computeState.id = id;

        return postServiceSynchronously(ComputeService.FACTORY_LINK, computeState,
                ComputeState.class);
    }

    private ProvisionLoadBalancerTaskState kickOffLoadBalancerProvision(
            InstanceRequestType requestType, String loadBalancerLink, TaskStage expectedTaskState)
            throws Throwable {
        ProvisionLoadBalancerTaskState taskState = new ProvisionLoadBalancerTaskState();
        taskState.requestType = requestType;
        taskState.loadBalancerLink = loadBalancerLink;
        taskState.isMockRequest = this.isMock;

        // Start/Post load balancer provisioning task
        taskState = postServiceSynchronously(ProvisionLoadBalancerTaskService.FACTORY_LINK,
                taskState, ProvisionLoadBalancerTaskState.class);

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

        Collection<LoadBalancerDescription> lbs =
                describeResult != null ? describeResult.getLoadBalancerDescriptions() : null;
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
            this.host.log("Exception describing security group with id '%s': %s", id, e.toString());
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
