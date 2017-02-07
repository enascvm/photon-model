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

package com.vmware.photon.controller.model.adapters.azure.instance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultAuthCredentials;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultComputeHost;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultResourceGroupState;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultResourcePool;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultVMResource;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.deleteVMs;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.generateName;

import java.util.UUID;
import java.util.logging.Level;

import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.credentials.AzureEnvironment;
import com.microsoft.azure.management.compute.ComputeManagementClient;
import com.microsoft.azure.management.compute.ComputeManagementClientImpl;
import com.microsoft.azure.management.network.NetworkManagementClient;
import com.microsoft.azure.management.network.NetworkManagementClientImpl;
import com.microsoft.azure.management.resources.ResourceManagementClient;
import com.microsoft.azure.management.resources.ResourceManagementClientImpl;
import com.microsoft.azure.management.resources.models.ResourceGroup;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse;
import com.vmware.photon.controller.model.adapters.azure.AzureAdapters;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.ResourceGroupStateType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class TestAzureProvisionTask extends BasicReusableHostTestCase {

    // SHARED Compute Host / End-point between test runs. {{
    private static ComputeState computeHost;
    private static String resourcePoolLink;
    private static String authLink;
    // Every test in addition might change it.
    private static String azureVMName = generateName("testProv-");
    // }}

    public String clientID = "clientID";
    public String clientKey = "clientKey";
    public String subscriptionId = "subscriptionId";
    public String tenantId = "tenantId";

    public boolean isMock = true;
    public boolean skipStats = true;

    // fields that are used across method calls, stash them as private fields
    private ComputeManagementClient computeManagementClient;
    private ResourceManagementClient resourceManagementClient;
    private NetworkManagementClient networkManagementClient;

    private ComputeState vmState;

    @Rule
    public TestName currentTestName = new TestName();

    @Before
    public void setUp() throws Exception {
        try {
            /*
             * Init Class-specific (shared between test runs) vars.
             *
             * NOTE: Ultimately this should go to @BeforeClass, BUT BasicReusableHostTestCase.HOST
             * is not accessible.
             */
            if (computeHost == null) {
                PhotonModelServices.startServices(this.host);
                PhotonModelTaskServices.startServices(this.host);
                AzureAdapters.startServices(this.host);

                this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
                this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
                this.host.waitForServiceAvailable(AzureAdapters.LINKS);

                // TODO: VSYM-992 - improve test/fix arbitrary timeout
                this.host.setTimeoutSeconds(1200);

                // Create a resource pool where the VM will be housed
                ResourcePoolState outPool = createDefaultResourcePool(this.host);
                resourcePoolLink = outPool.documentSelfLink;

                AuthCredentialsServiceState authCredentials = createDefaultAuthCredentials(
                        this.host,
                        this.clientID,
                        this.clientKey,
                        this.subscriptionId,
                        this.tenantId);
                authLink = authCredentials.documentSelfLink;

                // create a compute host for the Azure
                computeHost = createDefaultComputeHost(this.host, resourcePoolLink, authLink);
            }

            if (!this.isMock) {
                ApplicationTokenCredentials credentials = new ApplicationTokenCredentials(
                        this.clientID, this.tenantId, this.clientKey, AzureEnvironment.AZURE);

                this.computeManagementClient = new ComputeManagementClientImpl(credentials);
                this.computeManagementClient.setSubscriptionId(this.subscriptionId);

                this.resourceManagementClient = new ResourceManagementClientImpl(credentials);
                this.resourceManagementClient.setSubscriptionId(this.subscriptionId);

                this.networkManagementClient = new NetworkManagementClientImpl(credentials);
                this.networkManagementClient.setSubscriptionId(this.subscriptionId);
            }


        } catch (Throwable e) {
            throw new Exception(e);
        }
    }

    @After
    public void tearDown() throws Exception {
        if (this.vmState != null) {
            try {
                this.host.log(Level.INFO, "%s: Deleting [%s] VM",
                        this.currentTestName.getMethodName(), this.vmState.name);

                // ONLY computeHost MUST remain!
                int computeStatesToRemain = 1;

                deleteVMs(this.host, this.vmState.documentSelfLink, this.isMock,
                        computeStatesToRemain);

            } catch (Throwable deleteExc) {
                // just log and move on
                this.host.log(Level.WARNING, "%s: Deleting [%s] VM: FAILED. Details: %s",
                        this.currentTestName.getMethodName(), this.vmState.name,
                        deleteExc.getMessage());
            }
        }
    }

    /**
     * Creates a Azure instance via a provision task.
     */
    @Test
    public void testProvision() throws Throwable {

        // create a Azure VM compute resoruce
        this.vmState = createDefaultVMResource(this.host, azureVMName,
                computeHost.documentSelfLink,
                resourcePoolLink, authLink);

        kickOffProvisionTask();

        assertVmNetworksConfiguration();

        // Stats on individual VM is currently broken.
        if (!this.skipStats) {
            this.host.setTimeoutSeconds(60);
            this.host.waitFor("Error waiting for stats", () -> {
                try {
                    issueStatsRequest(this.vmState);
                } catch (Throwable t) {
                    return false;
                }
                return true;
            });
        }
    }

    /**
     * Creates Azure instance that uses shared/existing Network via a provision task.
     * <p>
     * It duplicates {@link #testProvision()} and just points to an external/shared Network.
     */
    @Test
    @Ignore("Since azure build timeouts due to the time consuming provision-decomission VM executed"
            + "by the tests. So far we sacrifice this test.")
    public void testProvisionVMUsingSharedNetwork() throws Throwable {

        // The test is only suitable for real (non-mocking env).
        Assume.assumeFalse(this.isMock);

        /*
         * Create SHARED vNet-Subnets in a separate RG.
         *
         * VERY IMPORTANT NOTE1: The names of the vNet and Subnets MUST be equal to the ones set by
         * AzureTestUtil.createDefaultNicStates.
         *
         * NOTE2: Since this is SHARED vNet it's not deleted after the test.
         *
         * The idea here is that we are provisioning a VM and linking it to an already existing
         * subnet/network. That's why the network is with a !!!FIXED!!! name, and that SAME name is
         * being used by standard provisioning!
         */
        final ResourceGroup sharedNetworkRG = AzureTestUtil.createResourceGroupWithSharedNetwork
                (this.resourceManagementClient, this.networkManagementClient);

        // Create corresponding ResourceGroupState
        ResourceGroupState sharedNetworkRGState = createDefaultResourceGroupState(
                this.host, sharedNetworkRG.getName(), computeHost.documentSelfLink,
                ResourceGroupStateType.AzureResourceGroup);

        // END of prepare phase.

        // In this scenario mark the VM name (and its corresponding RG) with "-withSharedNW"
        String vmName = azureVMName + "-withSharedNW";

        // create a Azure VM compute resource
        this.vmState = createDefaultVMResource(this.host, vmName,
                computeHost.documentSelfLink,
                resourcePoolLink, authLink,
                // In addition to standard provisioning pass the RG of the shared network
                sharedNetworkRGState.documentSelfLink);

        kickOffProvisionTask();

        assertVmNetworksConfiguration();
    }

    // kick off a provision task to do the actual VM creation
    private void kickOffProvisionTask() throws Throwable {

        ProvisionComputeTaskState provisionTask = new ProvisionComputeTaskState();

        provisionTask.computeLink = this.vmState.documentSelfLink;
        provisionTask.isMockRequest = this.isMock;
        provisionTask.taskSubStage = ProvisionComputeTaskState.SubStage.CREATING_HOST;

        provisionTask = TestUtils.doPost(this.host,
                provisionTask,
                ProvisionComputeTaskState.class,
                UriUtils.buildUri(this.host, ProvisionComputeTaskService.FACTORY_LINK));

        this.host.waitForFinishedTask(
                ProvisionComputeTaskState.class,
                provisionTask.documentSelfLink);
    }

    private void issueStatsRequest(ComputeState vm) throws Throwable {
        // spin up a stateless service that acts as the parent link to patch back to
        StatelessService parentService = new StatelessService() {
            @Override
            public void handleRequest(Operation op) {
                if (op.getAction() == Action.PATCH) {
                    if (!TestAzureProvisionTask.this.isMock) {
                        ComputeStatsResponse resp = op.getBody(ComputeStatsResponse.class);
                        if (resp.statsList.size() != 1) {
                            TestAzureProvisionTask.this.host.failIteration(
                                    new IllegalStateException("response size was incorrect."));
                            return;
                        }
                        if (resp.statsList.get(0).statValues.size() == 0) {
                            TestAzureProvisionTask.this.host
                                    .failIteration(new IllegalStateException(
                                            "incorrect number of metrics received."));
                            return;
                        }
                        if (!resp.statsList.get(0).computeLink.equals(vm.documentSelfLink)) {
                            TestAzureProvisionTask.this.host.failIteration(
                                    new IllegalStateException(
                                            "Incorrect computeReference returned."));
                            return;
                        }
                    }
                    TestAzureProvisionTask.this.host.completeIteration();
                }
            }
        };
        String servicePath = UUID.randomUUID().toString();
        Operation startOp = Operation.createPost(UriUtils.buildUri(this.host, servicePath));
        this.host.startService(startOp, parentService);
        ComputeStatsRequest statsRequest = new ComputeStatsRequest();
        statsRequest.resourceReference = UriUtils.buildUri(this.host, vm.documentSelfLink);
        statsRequest.isMockRequest = this.isMock;
        statsRequest.taskReference = UriUtils.buildUri(this.host, servicePath);
        this.host.sendAndWait(Operation.createPatch(UriUtils.buildUri(
                this.host, AzureUriPaths.AZURE_STATS_ADAPTER))
                .setBody(statsRequest)
                .setReferer(this.host.getUri()));
    }

    private void assertVmNetworksConfiguration() {

        // This assert is only suitable for real (non-mocking env).
        if (this.isMock) {
            return;
        }

        this.host.log(Level.INFO, "%s: Assert network configuration for [%s] VM",
                this.currentTestName.getMethodName(), this.vmState.name);

        ComputeState vm = this.host.getServiceState(null,
                ComputeState.class,
                UriUtils.buildUri(this.host, this.vmState.documentSelfLink));

        assertNotNull("VM address should be set.", vm.address);

        NetworkInterfaceState primaryNicState = this.host.getServiceState(null,
                NetworkInterfaceState.class,
                UriUtils.buildUri(this.host, vm.networkInterfaceLinks.get(0)));

        assertNotNull("Primary NIC public IP should be set.", primaryNicState.address);
        assertNotNull("Primary NIC security group should be set.",
                primaryNicState.securityGroupLinks != null
                        && primaryNicState.securityGroupLinks.size() == 1);

        assertEquals("VM address should be the same as primary NIC public IP.", vm.address,
                primaryNicState.address);

        for (int i = 1; i < vm.networkInterfaceLinks.size(); i++) {
            NetworkInterfaceState nonPrimaryNicState = this.host.getServiceState(null,
                    NetworkInterfaceState.class,
                    UriUtils.buildUri(this.host, vm.networkInterfaceLinks.get(i)));

            assertNull("Non-primary NIC" + i + " public IP should not be set.",
                    nonPrimaryNicState.address);
            assertNull("Non-primary NIC" + i + " security group should not be set.",
                    nonPrimaryNicState.securityGroupLinks);
        }
    }
}
