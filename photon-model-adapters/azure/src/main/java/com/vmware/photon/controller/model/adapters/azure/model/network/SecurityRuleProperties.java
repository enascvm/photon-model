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

package com.vmware.photon.controller.model.adapters.azure.model.network;

/**
 * Network security rule properties.
 */
public class SecurityRuleProperties {
    /**
     * Gets or sets Network protocol this rule applies to. Can be Tcp, Udp or
     * All(*). Possible values include: 'Tcp', 'Udp', '*'.
     */
    public String protocol;

    /**
     * Gets or sets source address prefix. CIDR or source IP range. Asterix
     * '*' can also be used to match all source IPs. Default tags such as
     * 'VirtualNetwork', 'AzureLoadBalancer' and 'Internet' can also be used.
     * If this is an ingress rule, specifies where network traffic originates
     * from.
     */
    public String sourceAddressPrefix;

    /**
     * Gets or sets destination address prefix. CIDR or source IP range.
     * Asterix '*' can also be used to match all source IPs. Default tags
     * such as 'VirtualNetwork', 'AzureLoadBalancer' and 'Internet' can also
     * be used.
     */
    public String destinationAddressPrefix;

    /**
     * Gets or sets Source Port or Range. Integer or range between 0 and
     * 65535. Asterix '*' can also be used to match all ports.
     */
    public String sourcePortRange;

    /**
     * Gets or sets Destination Port or Range. Integer or range between 0 and
     * 65535. Asterix '*' can also be used to match all ports.
     */
    public String destinationPortRange;

    /**
     * Gets or sets network traffic is allowed or denied. Possible values are
     * 'Allow' and 'Deny'. Possible values include: 'Allow', 'Deny'.
     */
    public String access;

    /**
     * Gets or sets the direction of the rule.InBound or Outbound. The
     * direction specifies if rule will be evaluated on incoming or outcoming
     * traffic. Possible values include: 'Inbound', 'Outbound'.
     */
    public String direction;
}
