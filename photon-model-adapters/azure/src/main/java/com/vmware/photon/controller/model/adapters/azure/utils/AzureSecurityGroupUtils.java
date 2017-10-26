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

package com.vmware.photon.controller.model.adapters.azure.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.microsoft.azure.management.network.SecurityRuleAccess;
import com.microsoft.azure.management.network.SecurityRuleDirection;
import com.microsoft.azure.management.network.SecurityRuleProtocol;
import com.microsoft.azure.management.network.implementation.NetworkSecurityGroupInner;
import com.microsoft.azure.management.network.implementation.NetworkSecurityGroupsInner;
import com.microsoft.azure.management.network.implementation.SecurityRuleInner;
import com.microsoft.rest.ServiceCallback;

import com.vmware.photon.controller.model.adapters.azure.utils.AzureDeferredResultServiceCallback.Default;
import com.vmware.photon.controller.model.resources.SecurityGroupService;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState.Rule;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState.Rule.Access;
import com.vmware.photon.controller.model.util.AssertUtil;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.StatelessService;

/**
 * Utility methods for creating an Azure Network Security Group.
 */
public class AzureSecurityGroupUtils {

    public static final String ALL_TRAFFIC = "*";
    public static final String ANY_RANGE = "0.0.0.0/0";

    public static DeferredResult<NetworkSecurityGroupInner> createSecurityGroup(
            StatelessService service, NetworkSecurityGroupsInner azureClient,
            SecurityGroupState securityGroupState, String resourceGroupName,
            String location, String msg) {

        return createOrUpdateSecurityGroup(service, azureClient, resourceGroupName,
                securityGroupState.name, buildSecurityGroup(securityGroupState, location), msg);
    }

    public static DeferredResult<NetworkSecurityGroupInner> addSecurityRules(
            StatelessService service, NetworkSecurityGroupsInner azureClient,
            SecurityGroupState securityGroupState, String resourceGroupName,
            NetworkSecurityGroupInner securityGroup, String msg) {

        addSecurityRules(securityGroup, securityGroupState);
        if (securityGroup.securityRules() != null && securityGroup.securityRules().size() > 0) {
            return createOrUpdateSecurityGroup(service, azureClient,
                    resourceGroupName, securityGroupState.name,
                    securityGroup, msg);
        } else {
            return DeferredResult.completed(securityGroup);
        }
    }

    public static DeferredResult<NetworkSecurityGroupInner> createOrUpdateSecurityGroup(
            StatelessService service, NetworkSecurityGroupsInner azureClient,
            String resourceGroupName, String sgName, NetworkSecurityGroupInner securityGroup,
            String msg) {

        service.logInfo(() -> msg);

        AzureProvisioningCallback<NetworkSecurityGroupInner> handler =
                new AzureProvisioningCallback<NetworkSecurityGroupInner>(service, msg) {
                    @Override
                    protected DeferredResult<NetworkSecurityGroupInner> consumeProvisioningSuccess(
                            NetworkSecurityGroupInner securityGroup) {

                        return DeferredResult.completed(securityGroup);
                    }

                    @Override
                    protected Runnable checkProvisioningStateCall(
                            ServiceCallback<NetworkSecurityGroupInner> checkProvisioningStateCallback) {
                        return () -> azureClient.getByResourceGroupAsync(
                                resourceGroupName,
                                sgName,
                                null /* expand */,
                                checkProvisioningStateCallback);
                    }

                    @Override
                    protected String getProvisioningState(NetworkSecurityGroupInner body) {
                        return body.provisioningState();
                    }
                };

        azureClient.createOrUpdateAsync(resourceGroupName, sgName,
                securityGroup, handler);

        return handler.toDeferredResult();
    }

    public static DeferredResult<NetworkSecurityGroupInner> getSecurityGroup(
            StatelessService service, NetworkSecurityGroupsInner azureClient,
            String resourceGroupName, String securityGroupName, String msg) {

        service.logInfo(() -> msg);

        AzureDeferredResultServiceCallback<NetworkSecurityGroupInner> handler =
                new Default<>(service, msg);

        azureClient.getByResourceGroupAsync(
                resourceGroupName,
                securityGroupName,
                null /* expand */,
                handler);

        return handler.toDeferredResult();
    }

    private static NetworkSecurityGroupInner buildSecurityGroup(SecurityGroupState sg,
            String location) {
        AssertUtil.assertNotNull(sg, "SecurityGroup state should not be null.");

        NetworkSecurityGroupInner nsg = new NetworkSecurityGroupInner();
        nsg.withLocation(location);

        return nsg;
    }

    private static NetworkSecurityGroupInner addSecurityRules(
            NetworkSecurityGroupInner securityGroupInner, SecurityGroupState sgState) {
        AssertUtil.assertNotNull(sgState, "SecurityGroup state should not be null.");
        AssertUtil.assertNotNull(securityGroupInner, "NetworkSecurityGroupInner should not be null.");

        List<SecurityRuleInner> securityRules = new ArrayList<>();

        List<SecurityRuleInner> defaultSecurityRules = securityGroupInner.defaultSecurityRules();
        final AtomicInteger priority = new AtomicInteger(1000);
        if (sgState.ingress != null) {
            sgState.ingress.forEach(rule -> {
                SecurityRuleInner sgRule = buildSecurityRule(rule,
                        SecurityRuleDirection.INBOUND, priority.getAndIncrement());
                if (!isDefaultRule(defaultSecurityRules, sgRule)) {
                    securityRules.add(sgRule);
                }
            });
        }

        priority.set(1000);
        if (sgState.egress != null) {
            sgState.egress.forEach(rule -> {
                SecurityRuleInner sgInner = buildSecurityRule(rule,
                        SecurityRuleDirection.OUTBOUND, priority.getAndIncrement());
                if (!isDefaultRule(defaultSecurityRules, sgInner)) {
                    securityRules.add(sgInner);
                }
            });
        }

        if (securityRules.size() > 0) {
            securityGroupInner.withSecurityRules(securityRules);
        }

        return securityGroupInner;
    }

    private static SecurityRuleInner buildSecurityRule(Rule rule, SecurityRuleDirection
            direction, int priority) {
        SecurityRuleInner sr = new SecurityRuleInner();
        sr.withPriority(priority);
        sr.withAccess(rule.access == Access.Allow ?
                SecurityRuleAccess.ALLOW :
                SecurityRuleAccess.DENY);
        sr.withDirection(direction);
        String addressPrefix = rule.ipRangeCidr.equals(ANY_RANGE) ?
                SecurityGroupService.ANY : rule.ipRangeCidr;
        String portRange = rule.ports.equals(SecurityGroupService.ALL_PORTS) ?
                SecurityGroupService.ANY : rule.ports;
        sr.withName(rule.name);
        sr.withProtocol(rule.protocol.equals(ALL_TRAFFIC) ?
                SecurityRuleProtocol.ASTERISK :
                new SecurityRuleProtocol(rule.protocol));
        if (SecurityRuleDirection.INBOUND.equals(direction)) {
            sr.withSourceAddressPrefix(addressPrefix);
            sr.withDestinationAddressPrefix(SecurityGroupService.ANY);

            sr.withSourcePortRange(portRange);
            sr.withDestinationPortRange(SecurityGroupService.ANY);
        } else {
            sr.withSourceAddressPrefix(SecurityGroupService.ANY);
            sr.withDestinationAddressPrefix(addressPrefix);

            sr.withSourcePortRange(SecurityGroupService.ANY);
            sr.withDestinationPortRange(portRange);
        }

        return sr;
    }

    private static boolean isDefaultRule(List<SecurityRuleInner> defaultRules, SecurityRuleInner
            sgRule) {
        for (SecurityRuleInner defaultRule : defaultRules) {
            if (defaultRule.access().equals(sgRule.access()) &&
                    defaultRule.direction().equals(sgRule.direction()) &&
                    defaultRule.protocol().equals(sgRule.protocol()) &&
                    defaultRule.sourcePortRange().equals(sgRule.sourcePortRange()) &&
                    defaultRule.destinationPortRange().equals(sgRule.destinationPortRange()) &&
                    defaultRule.sourceAddressPrefix().equals(sgRule.sourcePortRange()) &&
                    defaultRule.destinationAddressPrefix().equals(sgRule.destinationAddressPrefix())) {
                return true;
            }
        }
        return false;
    }
}