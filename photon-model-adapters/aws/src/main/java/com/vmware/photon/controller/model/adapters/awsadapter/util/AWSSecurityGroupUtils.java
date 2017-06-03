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

package com.vmware.photon.controller.model.adapters.awsadapter.util;

import static com.vmware.photon.controller.model.resources.SecurityGroupService.ANY;

import java.util.List;
import java.util.UUID;

import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.IpRange;
import com.amazonaws.services.ec2.model.Ipv6Range;

import com.vmware.photon.controller.model.resources.SecurityGroupService;
import com.vmware.photon.controller.model.resources.SecurityGroupService.Protocol;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState.Rule;
import com.vmware.photon.controller.model.util.AssertUtil;

/**
 * Utility class to hold methods used for calculating Photon-model security group
 * and Rule properties, based on the AWS API classes.
 */
public class AWSSecurityGroupUtils {

    /**
     * Returns Rule based on a provided Amazon IpPermission
     */
    public static Rule generateSecurityRuleFromAWSIpPermission(IpPermission ipPermission,
            Rule.Access access) {

        AssertUtil.assertNotNull(ipPermission, "AWS ipPermission is null.");

        Rule rule = new Rule();
        rule.name = UUID.randomUUID().toString();
        rule.access = access;

        rule.protocol = AWSSecurityGroupUtils.calculateProtocol(rule, ipPermission.getIpProtocol());

        rule.ipRangeCidr = AWSSecurityGroupUtils.calculateIpRangeCidr(rule, ipPermission);

        rule.ports = AWSSecurityGroupUtils.calculatePorts(rule, ipPermission);

        return rule;
    }

    public static String calculateProtocol(Rule rule, String protocolName) {
        Protocol enumProtocol = Protocol.fromString(protocolName);
        if (enumProtocol == null) {
            return Protocol.ANY.getName();
        }
        return enumProtocol.getName();
    }

    /**
     * Calculate the port range based on the input ipPermission from and to port values
     */
    public static String calculatePorts(Rule rule, IpPermission ipPermission) {

        String lowerCaseIpProtocol = (ipPermission.getIpProtocol() != null) ? ipPermission.getIpProtocol().toLowerCase() : null;
        Protocol ruleProtocol = Protocol.fromString(lowerCaseIpProtocol);
        String protocolName = (ruleProtocol != null) ? ruleProtocol.getName().toLowerCase() : null;

        if (protocolName != null && (protocolName.equals(Protocol.ICMPv6.getName())
                || protocolName.equals(Protocol.ICMPv4.getName()))) {

            if (ipPermission.getFromPort() != null && ipPermission.getFromPort() != -1) {

                if (ipPermission.getToPort() != null && ipPermission.getToPort() != -1) {
                    return ipPermission.getFromPort() + "-" + ipPermission.getToPort();
                }
                // only from port is provided
                return ipPermission.getFromPort().toString();
            }
            if (ipPermission.getToPort() != null && ipPermission.getToPort() != -1) {
                return ipPermission.getToPort().toString();
            }
            return rule.ports = SecurityGroupService.ALL_PORTS;
        }

        // range is all ports
        if (isAllPorts(ipPermission)) {
            return SecurityGroupService.ALL_PORTS;
        }

        // from port is provided
        if (ipPermission.getFromPort() != null) {

            // both from and to ports are provided
            if (ipPermission.getToPort() != null) {
                // and they have different values
                if (ipPermission.getFromPort().intValue() != ipPermission.getToPort()
                        .intValue()) {

                    return ipPermission.getFromPort() + "-" + ipPermission.getToPort();
                }
            }
            // only from port is provided
            return ipPermission.getFromPort().toString();
        }

        // only to port is provided
        if (ipPermission.getToPort() != null) {
            return ipPermission.getToPort().toString();
        }

        return SecurityGroupService.ALL_PORTS;
    }

    public static String calculateIpRangeCidr(Rule rule, IpPermission ipPermission) {
        List<IpRange> ipv4Ranges = ipPermission.getIpv4Ranges();
        List<Ipv6Range> ipv6Ranges = ipPermission.getIpv6Ranges();

        if (rule.protocol.equals(Protocol.ICMPv6.getName())) {

            return ipv6Ranges.size() > 0 ? ipv6Ranges.get(0).getCidrIpv6() : ANY;

        }
        if (rule.protocol.equals(Protocol.ICMPv4.getName())) {

            // it is possible to specify Ipv6Range for IPv4 ICMP protocol
            return ipv4Ranges.size() > 0 ? ipv4Ranges.get(0).getCidrIp() :
            // in case there is no ipv4 cidr, try to obtain ipv6 one
                    ipv6Ranges.size() > 0 ? ipv6Ranges.get(0).getCidrIpv6() : ANY;

        }

        return ipv4Ranges.size() > 0 ? ipv4Ranges.get(0).getCidrIp() : ANY;
    }

    private static boolean isAllPorts(IpPermission ipPermission) {
        Integer minusOne = -1;

        if (ipPermission.getFromPort() == null && ipPermission.getToPort() == null) {
            return true;
        }

        if (minusOne.equals(ipPermission.getFromPort())
                && minusOne.equals(ipPermission.getToPort())) {
            return true;
        }

        return false;
    }

}
