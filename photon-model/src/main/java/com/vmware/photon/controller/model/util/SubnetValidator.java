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

package com.vmware.photon.controller.model.util;

import io.netty.util.internal.StringUtil;
import org.apache.commons.validator.routines.InetAddressValidator;

import com.vmware.photon.controller.model.support.IPVersion;

/**
 * Helper class for subnet range and IP address validations.
 */

public class SubnetValidator {

    /**
     * Method to validate an IP address
     *
     * @param ipAddress IP address to be validated
     * @param ipVersion IPv4 or IPv6
     */
    public static boolean isValidIPAddress(String ipAddress, IPVersion ipVersion) {
        AssertUtil.assertTrue(!StringUtil.isNullOrEmpty(ipAddress), "IP address must be specified");
        switch (ipVersion) {
        case IPv6:
            return InetAddressValidator.getInstance().isValidInet6Address(ipAddress);
        case IPv4:
        default:
            return InetAddressValidator.getInstance().isValidInet4Address(ipAddress);
        }
    }

    /**
     * Method to compare two IP addresses and check if one is greater than the other.
     * Assuming the IP addresses are valid
     *
     * @param startAddress he start IP address
     * @param endAddress   The end IP address
     * @param ipVersion    IPv4 or IPv6
     * @return true if start IP is greater than end IP, false otherwise
     */
    public static boolean isStartIPGreaterThanEndIP(String startAddress,
            String endAddress,
            IPVersion ipVersion) {
        boolean isStartIPGreaterThanEndIP = false;
        if (startAddress != null && endAddress != null) {
            if (IPVersion.IPv4.equals(ipVersion)) {
                try {
                    isStartIPGreaterThanEndIP = (IpHelper.ipStringToLong(startAddress) - IpHelper.ipStringToLong(endAddress)) > 0;
                } catch (IllegalArgumentException e) {
                }
            } else {
                throw new UnsupportedOperationException(
                        "Support for IPv6 IP address is not yet implemented");
            }
        }
        return isStartIPGreaterThanEndIP;
    }
}