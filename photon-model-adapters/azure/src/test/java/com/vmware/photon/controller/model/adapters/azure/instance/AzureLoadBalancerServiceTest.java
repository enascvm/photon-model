/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.model.adapters.azure.instance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.AZURE_ADMIN_PASSWORD;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.AZURE_ADMIN_USERNAME;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.AZURE_RESOURCE_GROUP_LOCATION;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.NO_PUBLIC_IP_NIC_SPEC;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.buildComputeDescription;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultComputeHost;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultResourceGroupState;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultResourcePool;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultVMResource;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createSecurityGroupState;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.generateName;
import static com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService.LoadBalancerDescription.HealthCheckConfiguration;
import static com.vmware.photon.controller.model.tasks.ProvisionLoadBalancerTaskService.FACTORY_LINK;
import static com.vmware.photon.controller.model.tasks.ProvisionLoadBalancerTaskService.ProvisionLoadBalancerTaskState;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.Lists;
import com.microsoft.azure.management.network.AddressSpace;
import com.microsoft.azure.management.network.implementation.LoadBalancerInner;
import com.microsoft.azure.management.network.implementation.LoadBalancersInner;
import com.microsoft.azure.management.network.implementation.VirtualNetworkInner;
import com.microsoft.azure.management.network.implementation.VirtualNetworksInner;
import com.microsoft.azure.management.resources.implementation.ResourceGroupInner;
import com.microsoft.azure.management.resources.implementation.ResourceGroupsInner;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.PhotonModelMetricServices;
import com.vmware.photon.controller.model.adapterapi.LoadBalancerInstanceRequest;
import com.vmware.photon.controller.model.adapterapi.SecurityGroupInstanceRequest;
import com.vmware.photon.controller.model.adapterapi.SubnetInstanceRequest.InstanceRequestType;
import com.vmware.photon.controller.model.adapters.azure.AzureAsyncCallback;
import com.vmware.photon.controller.model.adapters.azure.base.AzureBaseTest;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.ResourceGroupStateType;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService.LoadBalancerDescription.RouteConfiguration;
import com.vmware.photon.controller.model.resources.LoadBalancerService;
import com.vmware.photon.controller.model.resources.LoadBalancerService.LoadBalancerState;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.support.LifecycleState;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState.SubStage;
import com.vmware.photon.controller.model.tasks.ProvisionSecurityGroupTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionSecurityGroupTaskService.ProvisionSecurityGroupTaskState;
import com.vmware.photon.controller.model.tasks.ProvisionSubnetTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionSubnetTaskService.ProvisionSubnetTaskState;
import com.vmware.photon.controller.model.tasks.ResourceRemovalTaskService;
import com.vmware.photon.controller.model.tasks.ResourceRemovalTaskService.ResourceRemovalTaskState;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;

/**
 * Tests for {@link AzureLoadBalancerService}.
 */
public class AzureLoadBalancerServiceTest extends AzureBaseTest {

    private static final String AZURE_DEFAULT_VPC_CIDR = "172.16.0.0/16";
    private static final String AZURE_NON_EXISTING_SUBNET_CIDR = "172.16.12.0/22";

    public String regionId = AZURE_RESOURCE_GROUP_LOCATION;

    private String azurePrefix = generateName("lbtest-");
    private String rgName = this.azurePrefix + "-rg";
    private String vNetName = this.azurePrefix + "-vNet";
    private String subnetName = this.azurePrefix + "-sNet";
    private String loadBalancerName = this.azurePrefix + "-lb";
    private String securityGroupName = this.azurePrefix + "-sg";

    private static ComputeState computeHost;
    private static EndpointState endpointState;
    private static NetworkState networkState;
    private static SecurityGroupState securityGroupState;

    private static final int poolSize = 4;
    private static List<ComputeState> vmStates;
    private static SubnetState subnetState;

    private ResourceGroupsInner rgOpsClient;
    private LoadBalancersInner loadBalancerClient;

    private enum LoadBalancerTargetType {
        COMPUTE,
        NIC,
        BOTH,
        INVALID
    }

    @Before
    public void setUpTests() throws Throwable {
        ResourcePoolState resourcePool = createDefaultResourcePool(this.host);
        endpointState = this.createEndpointState();
        // create a compute host for the Azure
        computeHost = createDefaultComputeHost(this.host, resourcePool.documentSelfLink, endpointState);

        AuthCredentialsServiceState azureVMAuth = new AuthCredentialsServiceState();
        azureVMAuth.userEmail = AZURE_ADMIN_USERNAME;
        azureVMAuth.privateKey = AZURE_ADMIN_PASSWORD;
        azureVMAuth = TestUtils.doPost(host, azureVMAuth, AuthCredentialsServiceState.class,
                UriUtils.buildUri(host, AuthCredentialsService.FACTORY_LINK));

        ComputeDescription compDesc = buildComputeDescription(host, computeHost, endpointState, azureVMAuth);

        this.rgName = AzureUtils.DEFAULT_GROUP_PREFIX + compDesc.documentCreationTimeMicros /
                1000;

        ResourceGroupState rgState = createDefaultResourceGroupState(
                this.host, this.rgName, computeHost, endpointState,
                ResourceGroupStateType.AzureResourceGroup);
        networkState = createNetworkState(rgState.documentSelfLink);
        vmStates = new ArrayList<>();

        for (int i = 0; i < poolSize; i++) {
            ComputeState vmState = createDefaultVMResource(getHost(), this.rgName + "VM" + i,
                    computeHost, endpointState, NO_PUBLIC_IP_NIC_SPEC, null,0, compDesc, this.rgName);
            vmStates.add(vmState);
        }
        subnetState = createSubnetState(this.subnetName);

        if (!this.isMock) {
            this.loadBalancerClient = getAzureSdkClients().getNetworkManagementClientImpl()
                    .loadBalancers();
            this.rgOpsClient = getAzureSdkClients().getResourceManagementClientImpl()
                    .resourceGroups();

            ResourceGroupInner rg = new ResourceGroupInner();
            rg.withName(this.rgName);
            rg.withLocation(this.regionId);
            this.rgOpsClient.createOrUpdate(this.rgName, rg);

            VirtualNetworkInner vNet = new VirtualNetworkInner();
            AddressSpace addressSpace = new AddressSpace();
            addressSpace.withAddressPrefixes(Collections.singletonList(AZURE_DEFAULT_VPC_CIDR));
            vNet.withAddressSpace(addressSpace);
            vNet.withLocation(this.regionId);
            VirtualNetworksInner vNetClient = getAzureSdkClients().getNetworkManagementClientImpl()
                    .virtualNetworks();
            vNetClient.createOrUpdate(this.rgName, this.vNetName, vNet);

            kickOffComputeProvision();
            kickOffSubnetProvision(InstanceRequestType.CREATE, subnetState, TaskStage.FINISHED);
        }
    }

    @Override
    protected void startRequiredServices() throws Throwable {
        super.startRequiredServices();
        //AzureAdapters.startServices(this.host);
        PhotonModelMetricServices.startServices(getHost());

        // TODO: VSYM-992 - improve test/fix arbitrary timeout
        getHost().setTimeoutSeconds(1200);
    }

    @After
    public void tearDown() {
        if (this.isMock) {
            return;
        }

        // delete VMs
        deleteVirtualMachines();

        // delete load balancer
        this.rgOpsClient.deleteAsync(this.rgName, new AzureAsyncCallback<Void>() {
            @Override
            protected void onError(Throwable e) {
                AzureLoadBalancerServiceTest.this.host.log(Level.WARNING, "Error deleting resource "
                        + "group: " + ExceptionUtils.getMessage(e));
            }

            @Override
            protected void onSuccess(Void result) {
                // Do nothing.
            }
        });
    }

    private void deleteVirtualMachines() {
        List<ResourceRemovalTaskState> removeTaskStates = this.vmStates.stream()
                .map(vmState -> {
                    getHost().log(Level.INFO, "%s: Deleting [%s] VM",
                            this.currentTestName.getMethodName(), vmState.name);

                    try {
                        QuerySpecification resourceQuerySpec = new QuerySpecification();
                        resourceQuerySpec.query
                                .setTermPropertyName(ServiceDocument.FIELD_NAME_SELF_LINK)
                                .setTermMatchValue(vmState.documentSelfLink);

                        ResourceRemovalTaskState deletionState = new ResourceRemovalTaskState();
                        deletionState.resourceQuerySpec = resourceQuerySpec;
                        deletionState.isMockRequest = isMock;

                        // Post/Start the ResourceRemovalTaskState...
                        deletionState = TestUtils
                                .doPost(host, deletionState, ResourceRemovalTaskState.class,
                                        UriUtils.buildUri(host, ResourceRemovalTaskService.FACTORY_LINK));
                        return deletionState;
                    } catch (Throwable deleteExc) {
                        // just log and move on
                        getHost().log(Level.WARNING, "%s: Deleting [%s] VM: FAILED. Details: %s",
                                this.currentTestName.getMethodName(), vmState.name,
                                deleteExc.getMessage());
                        return null;
                    }
                })
                .collect(Collectors.toList());

        removeTaskStates.forEach(deletionState -> {
            if (deletionState != null) {
                getHost().waitForFinishedTask(
                        ResourceRemovalTaskState.class, deletionState.documentSelfLink);
            }
        });
    }

    @Test
    public void testCreateLoadBalancerWithComputeTarget() throws Throwable {
        securityGroupState = createSecurityGroupState(this.host, endpointState,
                this.securityGroupName, Lists.newArrayList(), Lists.newArrayList());
        kickOffSecurityGroupProvision(SecurityGroupInstanceRequest.InstanceRequestType.CREATE,
                TaskStage.FINISHED);

        LoadBalancerState loadBalancerState = provisionLoadBalancer(TaskStage.FINISHED,
                LoadBalancerTargetType.COMPUTE);

        assertNotNull(loadBalancerState.id);
        assertNotEquals(loadBalancerState.id, this.loadBalancerName);

        if (!this.isMock) {
            // Verify that the load balancer was created.
            LoadBalancerInner lbResponse = this.loadBalancerClient.getByResourceGroup(
                    this.rgName,
                    this.loadBalancerName);

            assertEquals(this.loadBalancerName, lbResponse.name());
            assertEquals(loadBalancerState.id, lbResponse.id());
            assertNotNull(loadBalancerState.address);

            // delete the load balancer
            startLoadBalancerProvisioning(LoadBalancerInstanceRequest.InstanceRequestType.DELETE,
                    loadBalancerState,
                    TaskStage.FINISHED);
        }
    }

    @Test
    public void testCreateLoadBalancerWithNICTarget() throws Throwable {
        LoadBalancerState loadBalancerState = provisionLoadBalancer(TaskStage.FINISHED,
                LoadBalancerTargetType.NIC);

        assertNotNull(loadBalancerState.id);
        assertNotEquals(loadBalancerState.id, this.loadBalancerName);

        if (!this.isMock) {
            // Verify that the load balancer was created.
            LoadBalancerInner lbResponse = this.loadBalancerClient
                    .getByResourceGroup(this.rgName, this.loadBalancerName);

            assertEquals(this.loadBalancerName, lbResponse.name());
            assertEquals(loadBalancerState.id, lbResponse.id());

            // delete the load balancer
            startLoadBalancerProvisioning(LoadBalancerInstanceRequest.InstanceRequestType.DELETE,
                    loadBalancerState, TaskStage.FINISHED);
        }
    }

    @Test
    public void testCreateLoadBalancerWithMixedTarget() throws Throwable {
        LoadBalancerState loadBalancerState = provisionLoadBalancer(TaskStage.FINISHED,
                LoadBalancerTargetType.BOTH);

        assertNotNull(loadBalancerState.id);
        assertNotEquals(loadBalancerState.id, this.loadBalancerName);

        if (!this.isMock) {
            // Verify that the load balancer was created.
            LoadBalancerInner lbResponse = this.loadBalancerClient
                    .getByResourceGroup(this.rgName, this.loadBalancerName);

            assertEquals(this.loadBalancerName, lbResponse.name());
            assertEquals(loadBalancerState.id, lbResponse.id());

            // delete the load balancer
            startLoadBalancerProvisioning(LoadBalancerInstanceRequest.InstanceRequestType.DELETE,
                    loadBalancerState, TaskStage.FINISHED);
        }
    }

    @Test
    public void testCreateLoadBalancerWithInvalidNICTarget() throws Throwable {
        if (!this.isMock) {
            LoadBalancerState loadBalancerState = createLoadBalancerState(this.loadBalancerName,
                    LoadBalancerTargetType.INVALID);
            ProvisionLoadBalancerTaskState provisionLoadBalancerTaskState = startLoadBalancerProvisioning(
                    LoadBalancerInstanceRequest.InstanceRequestType.CREATE, loadBalancerState,
                    TaskStage.FAILED);
            assertTrue(provisionLoadBalancerTaskState.taskInfo.stage == TaskStage.FAILED);
            assertEquals("java.lang.IllegalArgumentException: Invalid target type specified",
                    provisionLoadBalancerTaskState.taskInfo.failure.message);
        }
    }

    @Test
    public void testDeleteLoadBalancer() throws Throwable {
        LoadBalancerState loadBalancerState = provisionLoadBalancer(TaskStage.FINISHED, null);

        startLoadBalancerProvisioning(LoadBalancerInstanceRequest.InstanceRequestType.DELETE,
                loadBalancerState, TaskStage.FINISHED);

        // verify load balancer state was deleted
        try {
            getLoadBalancerState(this.host, loadBalancerState.documentSelfLink);
        } catch (Throwable e) {
            assertTrue(e instanceof ServiceHost.ServiceNotFoundException);
        }

        if (!this.isMock) {
            // Verify that the load balancer was deleted from Azure.
            LoadBalancerInner sgResponse = this.loadBalancerClient.getByResourceGroup(
                    this.rgName, this.loadBalancerName);

            if (sgResponse != null) {
                fail("Load Balancer should not exist in Azure.");
            }
        }
    }

    private LoadBalancerState getLoadBalancerState(VerificationHost host,
            String loadBalancerLink) throws Throwable {
        Operation response = new Operation();
        getLoadBalancerState(host, loadBalancerLink, response);
        return response.getBody(LoadBalancerState.class);
    }

    private static void getLoadBalancerState(VerificationHost host,
            String loadBalancerLink, Operation response) throws Throwable {
        host.testStart(1);
        URI loadBalancerURI = UriUtils.buildUri(host, loadBalancerLink);
        Operation startGet = Operation.createGet(loadBalancerURI)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                        return;
                    }
                    response.setBody(o.getBody(LoadBalancerState.class));
                    host.completeIteration();
                });
        host.send(startGet);
        host.testWait();
    }

    private LoadBalancerState provisionLoadBalancer(TaskStage stage, LoadBalancerTargetType
            loadBalancerTargetType)
            throws Throwable {
        LoadBalancerState loadBalancerState = createLoadBalancerState(this.loadBalancerName,
                loadBalancerTargetType);
        startLoadBalancerProvisioning(LoadBalancerInstanceRequest.InstanceRequestType.CREATE,
                loadBalancerState, stage);
        return getHost().getServiceState(null, LoadBalancerState.class, UriUtils.buildUri(getHost(),
                loadBalancerState.documentSelfLink));
    }

    private RouteConfiguration createRouteConfiguration() {
        RouteConfiguration route = new RouteConfiguration();

        route.protocol = "tcp";
        route.port = "80";
        route.instanceProtocol = "tcp";
        route.instancePort = "80";

        HealthCheckConfiguration healthChkConfig = new HealthCheckConfiguration();
        healthChkConfig.port = "8080";
        healthChkConfig.protocol = "tcp";
        healthChkConfig.intervalSeconds = 10;
        healthChkConfig.urlPath = "dummyPath";
        healthChkConfig.healthyThreshold = 1;
        healthChkConfig.unhealthyThreshold = 2;

        route.healthCheckConfiguration = healthChkConfig;

        return route;
    }

    private LoadBalancerState createLoadBalancerState(String name,
            LoadBalancerTargetType loadBalancerTargetType) throws Throwable {
        LoadBalancerState loadBalancerState = new LoadBalancerState();
        loadBalancerState.id = name;
        loadBalancerState.name = name;
        loadBalancerState.instanceAdapterReference = UriUtils.buildUri(this.host,
                AzureLoadBalancerService.SELF_LINK);
        loadBalancerState.endpointLink = endpointState.documentSelfLink;
        loadBalancerState.tenantLinks = endpointState.tenantLinks;
        loadBalancerState.regionId = this.regionId;
        loadBalancerState.internetFacing = true;
        loadBalancerState.subnetLinks = Stream.of(subnetState.documentSelfLink)
                .collect(Collectors.toSet());

        RouteConfiguration route = createRouteConfiguration();
        loadBalancerState.routes = Stream.of(route)
                .collect(Collectors.toList());

        loadBalancerState.computeLinks = vmStates.stream()
                .map(cs -> cs.documentSelfLink)
                .collect(Collectors.toSet());

        if (loadBalancerTargetType != null) {
            switch (loadBalancerTargetType) {
            case COMPUTE:
                loadBalancerState.targetLinks = vmStates.stream()
                        .map(cs -> cs.documentSelfLink)
                        .collect(Collectors.toSet());
                break;
            case NIC:
                loadBalancerState.targetLinks = vmStates.stream()
                        .map(cs -> cs.networkInterfaceLinks.stream().findFirst().get())
                        .collect(Collectors.toSet());
                break;
            case BOTH:
                // use boolean to toggle between compute link and NIC link
                AtomicBoolean typeToggle = new AtomicBoolean(true);
                loadBalancerState.targetLinks = vmStates.stream()
                        .map(cs -> {
                            typeToggle.set(!typeToggle.get());
                            if (typeToggle.get()) {
                                return cs.documentSelfLink;
                            } else {
                                return cs.networkInterfaceLinks.stream().findFirst().get();
                            }
                        })
                        .collect(Collectors.toSet());
                break;
            case INVALID:
                loadBalancerState.targetLinks = new HashSet<>();
                loadBalancerState.targetLinks.add("123");
                break;
            default:
                break;
            }
        }
        if (securityGroupState != null) {
            loadBalancerState.securityGroupLinks = Stream.of(securityGroupState.documentSelfLink)
                    .collect(Collectors.toList());
        }
        loadBalancerState.address = "1.1.1.1";
        return postServiceSynchronously(
                LoadBalancerService.FACTORY_LINK, loadBalancerState, LoadBalancerState.class);
    }

    private ProvisionLoadBalancerTaskState startLoadBalancerProvisioning(
            LoadBalancerInstanceRequest.InstanceRequestType requestType,
            LoadBalancerState loadBalancerState,
            TaskStage expectedTaskState) throws Throwable {

        ProvisionLoadBalancerTaskState taskState = new ProvisionLoadBalancerTaskState();
        taskState.requestType = requestType;
        taskState.loadBalancerLink = loadBalancerState.documentSelfLink;
        taskState.isMockRequest = this.isMock;

        // Start/Post lb provisioning task
        taskState = postServiceSynchronously(
                FACTORY_LINK,
                taskState,
                ProvisionLoadBalancerTaskState.class);

        // Wait for provisioning task to complete
        return waitForServiceState(
                ProvisionLoadBalancerTaskState.class,
                taskState.documentSelfLink,
                liveState -> testServiceState(liveState, expectedTaskState));
    }

    private boolean testServiceState(ProvisionLoadBalancerTaskState liveState,
            TaskStage expectedTaskState) {
        TaskStage actualState = liveState.taskInfo.stage;
        if (expectedTaskState == actualState) {
            return true;
        } else {
            if (actualState == TaskStage.FAILED || actualState == TaskStage.CANCELLED) {
                fail(liveState.failureMessage);
            }
        }
        return false;
    }

    private SubnetState createSubnetState(String id) throws Throwable {
        SubnetState subnetState = new SubnetState();
        subnetState.id = id;
        subnetState.name = this.subnetName;
        subnetState.lifecycleState = LifecycleState.PROVISIONING;
        subnetState.subnetCIDR = AZURE_NON_EXISTING_SUBNET_CIDR;
        subnetState.networkLink = networkState.documentSelfLink;
        subnetState.instanceAdapterReference = UriUtils.buildUri(this.host,
                AzureSubnetService.SELF_LINK);
        subnetState.endpointLink = endpointState.documentSelfLink;
        subnetState.tenantLinks = endpointState.tenantLinks;

        return postServiceSynchronously(
                SubnetService.FACTORY_LINK, subnetState, SubnetState.class);
    }

    private NetworkState createNetworkState(String resourceGroupLink) throws Throwable {
        NetworkState networkState = new NetworkState();
        networkState.id = this.vNetName;
        networkState.name = this.vNetName;
        networkState.subnetCIDR = AZURE_DEFAULT_VPC_CIDR;
        networkState.tenantLinks = endpointState.tenantLinks;
        networkState.endpointLink = endpointState.documentSelfLink;
        networkState.resourcePoolLink = resourcePool.documentSelfLink; // ws "dummy"
        networkState.groupLinks = Collections.singleton(resourceGroupLink);
        networkState.regionId = this.regionId;
        networkState.instanceAdapterReference =
                UriUtils.buildUri(this.host, AzureInstanceService.SELF_LINK);

        return postServiceSynchronously(
                NetworkService.FACTORY_LINK, networkState, NetworkState.class);
    }

    private ProvisionSubnetTaskState kickOffSubnetProvision(InstanceRequestType requestType,
            SubnetState subnetState, TaskStage expectedTaskState) throws Throwable {

        ProvisionSubnetTaskState taskState = new ProvisionSubnetTaskState();
        taskState.requestType = requestType;
        taskState.subnetLink = subnetState.documentSelfLink;
        taskState.options = this.isMock
                ? EnumSet.of(TaskOption.IS_MOCK)
                : EnumSet.noneOf(TaskOption.class);

        // Start/Post subnet provisioning task
        taskState = postServiceSynchronously(
                ProvisionSubnetTaskService.FACTORY_LINK,
                taskState,
                ProvisionSubnetTaskState.class);

        // Wait for subnet task to complete
        return waitForServiceState(
                ProvisionSubnetTaskState.class,
                taskState.documentSelfLink,
                liveState -> expectedTaskState == liveState.taskInfo.stage);
    }

    // kick off a provision task to do the actual VM creation
    private void kickOffComputeProvision() {

        this.vmStates = vmStates.parallelStream().map(computeState -> {
            ProvisionComputeTaskState provisionTask = new ProvisionComputeTaskState();

            provisionTask.computeLink = computeState.documentSelfLink;
            provisionTask.isMockRequest = this.isMock;
            provisionTask.taskSubStage = SubStage.CREATING_HOST;

            try {
                provisionTask = postServiceSynchronously(ProvisionComputeTaskService
                        .FACTORY_LINK, provisionTask, ProvisionComputeTaskState.class);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
            getHost().waitForFinishedTask(
                    ProvisionComputeTaskState.class,
                    provisionTask.documentSelfLink);
            return getHost().getServiceState(null,
                    ComputeState.class,
                    UriUtils.buildUri(getHost(), computeState.documentSelfLink));
        }).collect(Collectors.toList());
    }

    private ProvisionSecurityGroupTaskState kickOffSecurityGroupProvision(
            SecurityGroupInstanceRequest.InstanceRequestType requestType,
            TaskStage expectedTaskState) throws Throwable {
        ProvisionSecurityGroupTaskState taskState = new ProvisionSecurityGroupTaskState();
        taskState.requestType = requestType;
        taskState.securityGroupDescriptionLinks = Stream.of(securityGroupState.documentSelfLink)
                .collect(Collectors.toSet());
        taskState.isMockRequest = this.isMock;

        // Start/Post security group provisioning task
        taskState = postServiceSynchronously(
                ProvisionSecurityGroupTaskService.FACTORY_LINK,
                taskState,
                ProvisionSecurityGroupTaskState.class);

        // Wait for security group provisioning task to complete
        return waitForServiceState(
                ProvisionSecurityGroupTaskState.class,
                taskState.documentSelfLink,
                liveState -> expectedTaskState == liveState.taskInfo.stage);
    }
}