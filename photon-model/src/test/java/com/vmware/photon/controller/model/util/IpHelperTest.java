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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

/**
 * Tests {@link IpHelper}.
 */
@RunWith(IpHelperTest.class)
@SuiteClasses({ IpHelperTest.IpAddresses.class,
        IpHelperTest.ValidIpRanges.class,
        IpHelperTest.ValidCidr.class,
        IpHelperTest.InvalidIpRanges.class,
        IpHelperTest.InvalidCidr.class })
public class IpHelperTest extends Suite {

    public IpHelperTest(Class<?> klass, RunnerBuilder builder) throws InitializationError {
        super(klass, builder);
    }

    @RunWith(Parameterized.class)
    public static class IpAddresses {
        private final String ipAddressAsString;
        private final long ipAddressAsLong;

        @Parameterized.Parameters(name = "IP:{0}")
        public static Collection<Object[]> data() {
            return Arrays.asList(new Object[][] {
                    { "127.0.0.1", 0x7F000001L },
                    { "192.168.0.1", 0xC0A80001L },
                    { "0.0.0.0", 0x0L },
                    { "255.255.255.255", 0xFFFFFFFFL },
            });
        }

        public IpAddresses(String ipAddressAsString, long ipAddressAsLong) {
            this.ipAddressAsString = ipAddressAsString;
            this.ipAddressAsLong = ipAddressAsLong;
        }

        @Test
        public void testIpToLong() throws UnknownHostException {
            InetAddress in = InetAddress.getByName(this.ipAddressAsString);
            assertThat(in, is(instanceOf(Inet4Address.class)));
            long ip = IpHelper.ipToLong((Inet4Address) in);
            assertThat(ip, is(this.ipAddressAsLong));
        }

        @Test
        public void testLongToIp() {
            InetAddress out = IpHelper.longToIp(this.ipAddressAsLong);
            assertThat(out.getHostAddress(), is(equalTo(this.ipAddressAsString)));
        }

        @Test
        public void testLongToIpString() {
            String out = IpHelper.longToIpString(this.ipAddressAsLong);
            assertThat(out, is(equalTo(this.ipAddressAsString)));
        }

        @Test
        public void testIpStringToLong() {
            long ip = IpHelper.ipStringToLong(this.ipAddressAsString);
            assertThat(ip, is(this.ipAddressAsLong));
        }
    }

    @RunWith(Parameterized.class)
    public static class ValidIpRanges {
        private final String startAddress;
        private final String endAddress;
        private final String expectedCidr;

        @Parameterized.Parameters(name = "Start:{0}; End:{1}; CIRD:{2}")
        public static Collection<Object[]> data() {
            return Arrays.asList(new Object[][] {
                    { "5.10.64.0", "5.10.127.255", "5.10.64.0/18" },
                    { "5.10.64.0", "5.10.64.255", "5.10.64.0/24" },
                    { "5.10.127.0", "5.10.127.255", "5.10.127.0/24" },
                    { "127.0.0.1", "127.0.0.1", "127.0.0.1/32" },
                    { "192.168.0.0", "192.168.0.127", "192.168.0.0/25" },
                    { "192.168.0.128", "192.168.0.255", "192.168.0.128/25" },
                    { "192.168.0.64", "192.168.0.127", "192.168.0.64/26" },
                    { "192.168.0.0", "192.168.0.255", "192.168.0.0/24" },
                    { "192.168.0.0", "192.168.255.255", "192.168.0.0/16" },
                    { "10.0.0.0", "10.255.255.255", "10.0.0.0/8" },
                    { "172.16.0.0", "172.31.255.255", "172.16.0.0/12" },
                    { "0.0.0.0", "0.0.0.0", "0.0.0.0/32" },
                    { "255.255.255.255", "255.255.255.255", "255.255.255.255/32" },
                    { "0.0.0.0", "127.255.255.255", "0.0.0.0/1" },
                    { "128.0.0.0", "255.255.255.255", "128.0.0.0/1" },
                    { "128.0.0.0", "191.255.255.255", "128.0.0.0/2" },
                    { "0.0.0.0", "255.255.255.255", "0.0.0.0/0" },
            });
        }

        public ValidIpRanges(String startAddress, String endAddress, String expectedCidr) {
            this.startAddress = startAddress;
            this.endAddress = endAddress;
            this.expectedCidr = expectedCidr;
        }

        @Test
        public void testCidrCalculationSuccess() {
            long lowIp = IpHelper.ipStringToLong(this.startAddress);
            long highIp = IpHelper.ipStringToLong(this.endAddress);
            String cidr = IpHelper.calculateCidrFromIpV4Range(lowIp, highIp);
            assertThat(cidr, is(equalTo(this.expectedCidr)));
        }
    }

    @RunWith(Parameterized.class)
    public static class InvalidIpRanges {
        private final String startAddress;
        private final String endAddress;

        @Parameterized.Parameters(name = "Start:{0}; End:{1}")
        public static Collection<Object[]> data() {
            return Arrays.asList(new Object[][] {
                    { "5.10.64.0", "5.10.255.255" },
                    { "5.10.127.0", "5.10.127.64" },
                    { "0.0.0.1", "0.0.0.0" },
                    { "127.0.0.1", "127.0.0.0" },
                    { "255.255.255.255", "255.255.255.254" },
                    { "192.168.0.0", "192.168.0.192" },
                    { "192.168.0.64", "192.168.0.192" },
                    { "192.168.0.64", "512.0.0.0" },
                    { "512.0.0.0", "192.168.0.192" },
                    { "512.0.0.0", "512.0.0.0" },
            });
        }

        public InvalidIpRanges(String startAddress, String endAddress) {
            this.startAddress = startAddress;
            this.endAddress = endAddress;
        }

        @Test(expected = Exception.class)
        public void testCidrCalculationFailure() {
            long lowIp = IpHelper.ipStringToLong(this.startAddress);
            long highIp = IpHelper.ipStringToLong(this.endAddress);
            IpHelper.calculateCidrFromIpV4Range(lowIp, highIp);
        }
    }

    @RunWith(Parameterized.class)
    public static class ValidCidr {
        private final String cidr;
        private final String expectedNetmask;

        @Parameterized.Parameters(name = "CIDR:{0}; Netmask:{1}")
        public static Collection<Object[]> data() {
            return Arrays.asList(new Object[][] {
                    { "192.168.1.0/1", "128.0.0.0" },
                    { "192.168.1.0/2", "192.0.0.0" },
                    { "192.168.1.0/3", "224.0.0.0" },
                    { "192.168.1.0/4", "240.0.0.0" },
                    { "192.168.1.0/5", "248.0.0.0" },
                    { "192.168.1.0/6", "252.0.0.0" },
                    { "192.168.1.0/7", "254.0.0.0" },
                    { "192.168.1.0/8", "255.0.0.0" },
                    { "192.168.1.0/9", "255.128.0.0" },
                    { "192.168.1.0/10", "255.192.0.0" },
                    { "192.168.1.0/11", "255.224.0.0" },
                    { "192.168.1.0/12", "255.240.0.0" },
                    { "192.168.1.0/13", "255.248.0.0" },
                    { "192.168.1.0/14", "255.252.0.0" },
                    { "192.168.1.0/15", "255.254.0.0" },
                    { "192.168.1.0/16", "255.255.0.0" },
                    { "192.168.1.0/17", "255.255.128.0" },
                    { "192.168.1.0/18", "255.255.192.0" },
                    { "192.168.1.0/19", "255.255.224.0" },
                    { "192.168.1.0/20", "255.255.240.0" },
                    { "192.168.1.0/21", "255.255.248.0" },
                    { "192.168.1.0/22", "255.255.252.0" },
                    { "192.168.1.0/23", "255.255.254.0" },
                    { "192.168.1.0/24", "255.255.255.0" },
                    { "192.168.1.0/25", "255.255.255.128" },
                    { "192.168.1.0/26", "255.255.255.192" },
                    { "192.168.1.0/27", "255.255.255.224" },
                    { "192.168.1.0/28", "255.255.255.240" },
                    { "192.168.1.0/29", "255.255.255.248" },
                    { "192.168.1.0/30", "255.255.255.252" },
                    { "192.168.1.0/31", "255.255.255.254" },
                    { "192.168.1.0/32", "255.255.255.255" },
            });
        }

        public ValidCidr(String cidr, String expectedNetmask) {
            this.cidr = cidr;
            this.expectedNetmask = expectedNetmask;
        }

        @Test
        public void testNetmaskStringCalculationSuccess() {
            String actualNetmask = IpHelper.calculateNetmaskStringFromCidr(this.cidr);
            assertThat(actualNetmask, equalTo(this.expectedNetmask));
        }
    }

    @RunWith(Parameterized.class)
    public static class InvalidCidr {
        private final String cidr;

        @Parameterized.Parameters(name = "CIDR:{0}")
        public static Collection<Object[]> data() {
            return Arrays.asList(new Object[][] {
                    { "" },
                    { "192.168.1.5" },
                    { "192.168.1.5/-1" },
                    { "192.168.1.0/0" },
                    { "192.168.1.5/33" },
                    { "192.168.1.5/128" },
                    { "192.168.1.5/abc" },
                    { "192.168.1.5/2/3" }
            });
        }

        public InvalidCidr(String cidr) {
            this.cidr = cidr;
        }

        @Test(expected = Exception.class)
        public void testNetmaskStringCalculationFailure() {
            IpHelper.calculateNetmaskStringFromCidr(this.cidr);
        }
    }
}