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

import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.AZURE_RESOURCE_GROUP_LOCATION;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.generateName;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.getSecurityGroupState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.microsoft.azure.management.network.SecurityRuleAccess;
import com.microsoft.azure.management.network.SecurityRuleDirection;
import com.microsoft.azure.management.network.SecurityRuleProtocol;
import com.microsoft.azure.management.network.implementation.NetworkSecurityGroupInner;
import com.microsoft.azure.management.network.implementation.NetworkSecurityGroupsInner;
import com.microsoft.azure.management.network.implementation.SecurityRuleInner;
import com.microsoft.azure.management.resources.implementation.ResourceGroupsInner;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.PhotonModelMetricServices;
import com.vmware.photon.controller.model.adapterapi.SecurityGroupInstanceRequest.InstanceRequestType;
import com.vmware.photon.controller.model.adapters.azure.AzureAsyncCallback;
import com.vmware.photon.controller.model.adapters.azure.base.AzureBaseTest;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.SecurityGroupService;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState.Rule;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState.Rule.Access;
import com.vmware.photon.controller.model.tasks.ProvisionSecurityGroupTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionSecurityGroupTaskService.ProvisionSecurityGroupTaskState;
import com.vmware.xenon.common.ServiceHost.ServiceNotFoundException;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;

/**
 * Tests for {@link AzureSecurityGroupService}.
 */
public class AzureSecurityGroupServiceTest extends AzureBaseTest {

    public String regionId = AZURE_RESOURCE_GROUP_LOCATION;

    private String azurePrefix = generateName("securitygrouptest-");
    private String securityGroupName = this.azurePrefix + "-sg";
    private String rgName = this.securityGroupName + "-rg";

    private ResourceGroupsInner rgOpsClient;
    private NetworkSecurityGroupsInner securityGroupsClient;

    @Override
    protected void startRequiredServices() throws Throwable {

        super.startRequiredServices();

        PhotonModelMetricServices.startServices(getHost());

        // TODO: VSYM-992 - improve test/fix arbitrary timeout
        getHost().setTimeoutSeconds(1200);
    }

    @Before
    public void setUpTests() throws Throwable {
        if (!this.isMock) {
            this.rgOpsClient = getAzureSdkClients()
                    .getResourceManagementClientImpl().resourceGroups();
            this.securityGroupsClient = getAzureSdkClients()
                    .getNetworkManagementClientImpl().networkSecurityGroups();
        }
    }

    @After
    public void tearDown() {
        if (this.isMock) {
            return;
        }

        this.rgOpsClient.deleteAsync(this.rgName, new AzureAsyncCallback<Void>() {
            @Override
            protected void onError(Throwable e) {
                AzureSecurityGroupServiceTest.this.host.log(Level.WARNING, "Error deleting resource "
                        + "group: " + ExceptionUtils.getMessage(e));
            }

            @Override
            protected void onSuccess(Void result) {
                // Do nothing.
            }
        });
    }

    @Test
    public void testCreateSecurityGroupNoRules() throws Throwable {
        SecurityGroupState securityGroupState = provisionSecurityGroup(
                new ArrayList<>(), new ArrayList<>(), TaskStage.FINISHED);

        assertNotNull(securityGroupState.id);
        assertNotEquals(securityGroupState.id, this.securityGroupName);

        if (!this.isMock) {
            // Verify that the security group was created.
            NetworkSecurityGroupInner sgResponse = this.securityGroupsClient.getByResourceGroup(
                    this.rgName, this.securityGroupName);

            assertEquals(this.securityGroupName, sgResponse.name());
            assertEquals(securityGroupState.id, sgResponse.id());
            assertEquals(sgResponse.securityRules().size(), 0);

            // delete the security group
            startSecurityGroupProvisioning(InstanceRequestType.DELETE, securityGroupState,
                    TaskStage.FINISHED);
        }
    }

    @Test
    public void testCreateSecurityGroupWithNonDefaultRules() throws Throwable {
        SecurityGroupState securityGroupState = provisionSecurityGroup(
                buildNonDefaultRules(), buildNonDefaultRules(), TaskStage.FINISHED);

        assertNotNull(securityGroupState.id);
        assertNotEquals(securityGroupState.id, this.securityGroupName);

        if (!this.isMock) {
            // Verify that the security group was created.
            NetworkSecurityGroupInner sgResponse = this.securityGroupsClient.getByResourceGroup(
                    this.rgName, this.securityGroupName);

            assertEquals(this.securityGroupName, sgResponse.name());
            assertEquals(securityGroupState.id, sgResponse.id());
            assertEquals(sgResponse.securityRules().size(), 2 * securityGroupState.ingress.size());
            validateAzureSecurityRules(sgResponse.securityRules(), securityGroupState.ingress.size());

            // delete the security group
            startSecurityGroupProvisioning(InstanceRequestType.DELETE, securityGroupState,
                    TaskStage.FINISHED);
        }
    }

    @Test
    public void testCreateSecurityGroupWithDefaultRules() throws Throwable {
        SecurityGroupState securityGroupState = provisionSecurityGroup(
                buildDefaultRules(), buildDefaultRules(), TaskStage.FINISHED);

        assertNotNull(securityGroupState.id);
        assertNotEquals(securityGroupState.id, this.securityGroupName);

        if (!this.isMock) {
            // Verify that the security group was created.
            NetworkSecurityGroupInner sgResponse = this.securityGroupsClient.getByResourceGroup(
                    this.rgName, this.securityGroupName);

            assertEquals(this.securityGroupName, sgResponse.name());
            assertEquals(securityGroupState.id, sgResponse.id());
            assertEquals(sgResponse.securityRules().size(), securityGroupState.ingress.size());
            validateAzureSecurityRules(sgResponse.securityRules(), securityGroupState.ingress
                    .size() - 1);

            // delete the security group
            startSecurityGroupProvisioning(InstanceRequestType.DELETE, securityGroupState,
                    TaskStage.FINISHED);
        }
    }

    @Test
    public void testCreateSecurityGroupsWithSameRuleNames() throws Throwable {
        SecurityGroupState securityGroupState1 = createSecurityGroupState(this.securityGroupName
                        + "-1",
                Arrays.asList(
                        buildRule("rule-1", SecurityGroupService.ANY, "0.0.0.0/0",
                                Access.Deny, SecurityGroupService.ALL_PORTS)),
                Arrays.asList(
                        buildRule("rule-2", SecurityGroupService.ANY, "0.0.0.0/0",
                                Access.Deny, SecurityGroupService.ALL_PORTS)));

        startSecurityGroupProvisioning(InstanceRequestType.CREATE, securityGroupState1,
                TaskStage.FINISHED);

        securityGroupState1 = getServiceSynchronously(securityGroupState1.documentSelfLink,
                SecurityGroupState.class);

        assertNotNull(securityGroupState1.id);
        assertNotEquals(securityGroupState1.id, this.securityGroupName + "-1");

        SecurityGroupState securityGroupState2 = createSecurityGroupState(this.securityGroupName
                        + "-2",
                Arrays.asList(
                        buildRule("rule-1", SecurityGroupService.ANY, "0.0.0.0/0",
                                Access.Deny, SecurityGroupService.ALL_PORTS)),
                Arrays.asList(
                        buildRule("rule-2", SecurityGroupService.ANY, "0.0.0.0/0",
                                Access.Deny, SecurityGroupService.ALL_PORTS)));

        startSecurityGroupProvisioning(InstanceRequestType.CREATE, securityGroupState2,
                TaskStage.FINISHED);

        securityGroupState2 = getServiceSynchronously(securityGroupState2.documentSelfLink,
                SecurityGroupState.class);

        assertNotNull(securityGroupState2.id);
        assertNotEquals(securityGroupState2.id, this.securityGroupName + "-2");

        if (!this.isMock) {
            // delete the security groups
            startSecurityGroupProvisioning(InstanceRequestType.DELETE, securityGroupState1,
                    TaskStage.FINISHED);
            startSecurityGroupProvisioning(InstanceRequestType.DELETE, securityGroupState2,
                    TaskStage.FINISHED);
        }
    }

    @Test
    public void testCreateSecurityGroupFailures() throws Throwable {
        if (!this.isMock) {
            // test invalid security rule name
            SecurityGroupState securityGroupState = provisionSecurityGroup(
                    buildInvalidNameRules(), buildNonDefaultRules(), TaskStage.FAILED);

            assertNotNull(securityGroupState.id);
            assertNotEquals(securityGroupState.id, this.securityGroupName);

            // Verify that the security group was created without any rules.
            NetworkSecurityGroupInner sgResponse = this.securityGroupsClient.getByResourceGroup(
                    this.rgName, this.securityGroupName);

            assertEquals(this.securityGroupName, sgResponse.name());
            assertEquals(securityGroupState.id, sgResponse.id());
            assertEquals(sgResponse.securityRules().size(), 0);

            // delete the security group
            startSecurityGroupProvisioning(InstanceRequestType.DELETE, securityGroupState,
                    TaskStage.FINISHED);
        }
    }

    @Test
    public void testDeleteSecurityGroup() throws Throwable {
        SecurityGroupState securityGroupState = provisionSecurityGroup(
                new ArrayList<>(), new ArrayList<>(), TaskStage.FINISHED);

        startSecurityGroupProvisioning(InstanceRequestType.DELETE, securityGroupState, TaskStage.FINISHED);

        // verify security group state was deleted
        try {
            getSecurityGroupState(this.host, securityGroupState.documentSelfLink);
        } catch (Exception e) {
            assertTrue(e instanceof ServiceNotFoundException);
        }

        if (!this.isMock) {
            // Verify that the security group was deleted from Azure.
            NetworkSecurityGroupInner sgResponse = this.securityGroupsClient.getByResourceGroup(
                    this.rgName, this.securityGroupName);

            if (sgResponse != null) {
                fail("Security group should not exist in Azure.");
            }
        }
    }

    @Test
    public void testDeleteMissingSecurityGroup() throws Throwable {
        SecurityGroupState securityGroupState = createSecurityGroupState(this.securityGroupName,
                new ArrayList<>(), new ArrayList<>());

        // attempt to delete the missing SG
        startSecurityGroupProvisioning(InstanceRequestType.DELETE, securityGroupState, TaskStage.FINISHED);

        // verify security group state was deleted
        try {
            getSecurityGroupState(this.host, securityGroupState.documentSelfLink);
        } catch (Exception e) {
            assertTrue(e instanceof ServiceNotFoundException);
        }
    }

    private SecurityGroupState provisionSecurityGroup(List<Rule> inboundRules,
            List<Rule> outboundRules, TaskStage taskStage) throws Throwable {
        SecurityGroupState securityGroupState = createSecurityGroupState(this.securityGroupName,
                inboundRules, outboundRules);

        startSecurityGroupProvisioning(InstanceRequestType.CREATE, securityGroupState, taskStage);

        return getServiceSynchronously(securityGroupState.documentSelfLink,
                SecurityGroupState.class);
    }

    private SecurityGroupState createSecurityGroupState(String name,
            List<Rule> inboundRules, List<Rule> outboudRules) throws Throwable {
        SecurityGroupState securityGroupState = new SecurityGroupState();
        securityGroupState.id = name;
        securityGroupState.name = name;
        securityGroupState.instanceAdapterReference = UriUtils.buildUri(this.host,
                AzureSecurityGroupService.SELF_LINK);
        securityGroupState.endpointLink = endpointState.documentSelfLink;
        securityGroupState.tenantLinks = endpointState.tenantLinks;
        securityGroupState.ingress = inboundRules;
        securityGroupState.egress = outboudRules;
        securityGroupState.authCredentialsLink = endpointState.authCredentialsLink;
        securityGroupState.resourcePoolLink = "test-resource-pool-link";
        securityGroupState.regionId = this.regionId;

        return postServiceSynchronously(
                SecurityGroupService.FACTORY_LINK, securityGroupState, SecurityGroupState.class);
    }

    private ProvisionSecurityGroupTaskState startSecurityGroupProvisioning(
            InstanceRequestType requestType, SecurityGroupState securityGroupState,
            TaskStage expectedTaskState) throws Throwable {

        ProvisionSecurityGroupTaskState taskState = new ProvisionSecurityGroupTaskState();
        taskState.requestType = requestType;
        taskState.securityGroupDescriptionLinks = Stream.of(securityGroupState.documentSelfLink)
                .collect(Collectors.toSet());
        taskState.isMockRequest = this.isMock;

        // Start/Post subnet provisioning task
        taskState = postServiceSynchronously(
                ProvisionSecurityGroupTaskService.FACTORY_LINK,
                taskState,
                ProvisionSecurityGroupTaskState.class);

        // Wait for provisioning task to complete
        return waitForServiceState(
                ProvisionSecurityGroupTaskState.class,
                taskState.documentSelfLink,
                liveState -> expectedTaskState == liveState.taskInfo.stage);
    }

    private List<Rule> buildDefaultRules() {
        return Arrays.asList(
                buildRule("rule-" + UUID.randomUUID().toString(),
                        SecurityGroupService.ANY, "0.0.0.0/0",
                        Access.Deny, SecurityGroupService.ALL_PORTS),
                buildRule("rule-" + UUID.randomUUID().toString(),
                        "Tcp", "0.0.0.0/0",
                        Access.Allow, SecurityGroupService.ALL_PORTS));
    }

    private List<Rule> buildNonDefaultRules() {
        return Arrays.asList(
                buildRule("rule-" + UUID.randomUUID().toString(),
                        "Udp", "0.0.0.0/0",
                        Access.Deny, SecurityGroupService.ALL_PORTS),
                buildRule("rule-" + UUID.randomUUID().toString(),
                        "Tcp", "0.0.0.0/0",
                        Access.Allow, SecurityGroupService.ALL_PORTS));
    }

    private Rule buildRule(String name, String protocol, String ipRange, Access access,
            String ports) {
        Rule rule = new Rule();
        rule.name = name;
        rule.protocol = protocol;
        rule.ipRangeCidr = ipRange;
        rule.access = access;
        rule.ports = ports;

        return rule;
    }

    private List<Rule> buildInvalidNameRules() {
        Rule isolationRule = new Rule();
        // space in rule name is invalid
        isolationRule.name = "Rule " + UUID.randomUUID().toString();
        isolationRule.protocol = SecurityGroupService.ANY;
        isolationRule.ipRangeCidr = "0.0.0.0/0";
        isolationRule.access = Access.Deny;
        isolationRule.ports = "1-65535";

        Rule allowRule = new Rule();
        allowRule.name = "Rule " + UUID.randomUUID().toString();
        allowRule.protocol = "Tcp";
        allowRule.ipRangeCidr = "0.0.0.0/0";
        allowRule.access = Access.Allow;
        allowRule.ports = "1-65535";

        return Arrays.asList(isolationRule, allowRule);
    }

    private void validateAzureSecurityRules(
            List<SecurityRuleInner> actualRules, int expectedNumberOfRules) {
        assertEquals(expectedNumberOfRules, actualRules
                .stream()
                .filter(r -> r.direction().equals(SecurityRuleDirection.INBOUND)).count());
        assertEquals(expectedNumberOfRules, actualRules
                .stream()
                .filter(r -> r.direction().equals(SecurityRuleDirection.OUTBOUND)).count());

        for (SecurityRuleInner rule : actualRules) {
            assertTrue(rule.sourceAddressPrefix().equals(SecurityGroupService.ANY));
            assertTrue(rule.destinationAddressPrefix().equals(SecurityGroupService.ANY));
            assertTrue(rule.sourcePortRange().equals(SecurityGroupService.ANY));
            assertTrue(rule.destinationPortRange().equals(SecurityGroupService.ANY));

            if (rule.access().equals(SecurityRuleAccess.ALLOW)) {
                assertTrue(rule.protocol().equals(SecurityRuleProtocol.TCP));
            } else {
                assertTrue(rule.protocol().equals(SecurityRuleProtocol.UDP));
            }
        }
    }

    private NetworkState createNetworkState(String resourceGroupLink) throws Throwable {
        NetworkState networkState = new NetworkState();
        networkState.id = UUID.randomUUID().toString();
        networkState.name = networkState.id;
        networkState.subnetCIDR = "0.0.0.0/24";
        networkState.tenantLinks = endpointState.tenantLinks;
        networkState.endpointLink = endpointState.documentSelfLink;
        networkState.resourcePoolLink = "dummyResourcePoolLink";
        networkState.groupLinks = Collections.singleton(resourceGroupLink);
        networkState.regionId = this.regionId;
        networkState.instanceAdapterReference =
                UriUtils.buildUri(this.host, AzureInstanceService.SELF_LINK);

        return postServiceSynchronously(
                NetworkService.FACTORY_LINK, networkState, NetworkState.class);
    }

}
