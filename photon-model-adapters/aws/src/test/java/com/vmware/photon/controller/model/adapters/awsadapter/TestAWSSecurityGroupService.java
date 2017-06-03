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

package com.vmware.photon.controller.model.adapters.awsadapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSSecurityGroupClient.DEFAULT_ALLOWED_NETWORK;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSSecurityGroupClient.DEFAULT_ALLOWED_PORTS;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSSecurityGroupClient.DEFAULT_PROTOCOL;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.IpRange;
import com.amazonaws.services.ec2.model.Ipv6Range;
import com.amazonaws.services.ec2.model.SecurityGroup;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.vmware.photon.controller.model.PhotonModelMetricServices;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSSecurityGroupClient;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSSecurityGroupUtils;
import com.vmware.photon.controller.model.resources.SecurityGroupService;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState.Rule;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.VerificationHost;

public class TestAWSSecurityGroupService {

    /*
    * This test requires the following four command line variables.
    * If they are not present the tests will be ignored
    * Pass them into the test with the -Dxenon.variable=value syntax
    * i.e -Dxenon.subnet="10.1.0.0/16"
    *
    *
    * privateKey & privateKeyId are credentials to an AWS VPC account
    * region is the ec2 region where the tests should be run (us-east-1)
    * subnet is the RFC-1918 subnet of the default VPC
    *
    * Test assumes the default CM Security group is NOT present in the provided
    * AWS account / zone -- if it is present the tests will fail
    */
    public String privateKey;
    public String privateKeyId;
    public String region;
    public String subnet;

    VerificationHost host;

    AWSSecurityGroupService svc;
    AWSInstanceContext aws;
    AWSSecurityGroupClient client;

    @org.junit.Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        CommandLineArgumentParser.parseFromProperties(this);
        // ignore if any of the required properties are missing
        org.junit.Assume.assumeTrue(TestUtils.isNull(this.privateKey, this.privateKeyId, this.region, this.subnet));
        this.host = VerificationHost.create(0);
        try {
            this.host.start();
            PhotonModelServices.startServices(this.host);
            PhotonModelMetricServices.startServices(this.host);
            PhotonModelTaskServices.startServices(this.host);
            this.svc = new AWSSecurityGroupService();
            this.host.startService(
                    Operation.createPost(UriUtils.buildUri(this.host,
                            AWSSecurityGroupService.class)),
                    this.svc);
            this.client = new AWSSecurityGroupClient(
                    TestUtils.getClient(this.privateKeyId,this.privateKey,this.region,false));
        } catch (Throwable e) {
            throw new Exception(e);
        }
    }

    @After
    public void tearDown() throws InterruptedException {
        if (this.host == null) {
            return;
        }
        this.host.tearDownInProcessPeers();
        this.host.toggleNegativeTestMode(false);
        this.host.tearDown();
    }

    @Test
    public void testInvalidGetSecurityGroup() throws Throwable {
        assertNull(this.client.getSecurityGroup("foo-bar", null));
    }

    /*
     * Create the default CM group, get or describe the default group
     * and then delete the group
     */
    @Test
    public void testDefaultSecurityGroup() throws Throwable {
        String sgId = this.client.createDefaultSecurityGroup(null);
        this.client.getDefaultSecurityGroup(null);
        this.client.deleteSecurityGroup(sgId);
    }

    /*
     * Create the default CM group, get the group, verify the default
       permissions are in place.  Then delete the default group
     */

    @Test
    public void testDefaultSecurityGroupPorts() throws Throwable {
        // create the group
        String groupId = this.client.createDefaultSecurityGroup(null);

        // allow the default ports
        this.client.addIngressRules(groupId, this.client.getDefaultRules(this.subnet));

        // get the updated CM group
        SecurityGroup group = this.client.getDefaultSecurityGroup(null);

        List<IpPermission> rules = group.getIpPermissions();

        assertTrue(rules.size() > 0);
        validateDefaultRules(rules);

        // lets delete the default CM group
        this.client.deleteSecurityGroup(groupId);
    }

    /*
     * Negative test attempting to delete the non-existent
     * default CM security group
     */
    @Test
    public void testDeleteMissingGroup() throws Throwable {
        this.expectedEx.expect(AmazonServiceException.class);
        this.expectedEx.expectMessage("Invalid id: \"non-existing-security-group\"");

        this.client.deleteSecurityGroup("non-existing-security-group");
    }

    /*
     * create a new security group via the allocation method
     */
    @Test
    public void testAllocateSecurityGroup() throws Throwable {
        this.client.createDefaultSecurityGroup(null);
        SecurityGroup group = this.client.getDefaultSecurityGroup(null);
        validateDefaultRules(group.getIpPermissions());
        this.client.deleteSecurityGroup(group.getGroupId());
    }

    /*
     * update an existing security group to the required default ports
     */
    @Test
    public void testAllocateSecurityGroupUpdate() throws Throwable {
        String groupId = this.client.createDefaultSecurityGroup(null);

        List<IpPermission> rules = new ArrayList<>();
        IpRange ipRange = new IpRange().withCidrIp(DEFAULT_ALLOWED_NETWORK);
        rules.add(new IpPermission()
                .withIpProtocol(DEFAULT_PROTOCOL)
                .withFromPort(22)
                .withToPort(22)
                .withIpv4Ranges(ipRange));
        this.client.addIngressRules(groupId, rules);
        SecurityGroup updatedGroup = this.client.getDefaultSecurityGroup(null);
        validateDefaultRules(updatedGroup.getIpPermissions());
        this.client.deleteSecurityGroup(groupId);
    }

    /*
     * Test conversion of Allow rules to AWS IpPermssions
     */

    @Test
    public void testBuildRules() throws Throwable {
        ArrayList<Rule> rules = TestUtils.getAllowIngressRules();
        List<IpPermission> awsRules = this.client.buildRules(rules);

        for (IpPermission rule : awsRules) {
            assertDefaultRules(rule);
        }
    }


    /*
     * Test updating ingress rules with the Security Group Service Allow
     * object
     */

    @Test
    public void testUpdateIngressRules() throws Throwable {
        String groupID = this.client.createDefaultSecurityGroup(null);
        ArrayList<Rule> rules = TestUtils.getAllowIngressRules();
        this.client.addIngressRules(groupID, this.client.buildRules(rules));
        SecurityGroup awsSG = this.client.getSecurityGroupById(groupID);

        List<IpPermission> ingress = awsSG.getIpPermissions();

        for (IpPermission rule : ingress) {
            assertDefaultRules(rule);
        }

        this.client.deleteSecurityGroup(groupID);
    }

    /*
     * Test calculating Rule range and Cidr based on the AWS IpPermission configuration
     */

    @Test
    public void testSecurityGroupRulesCalculation() throws Throwable {

        IpRange ipv4Range = new IpRange();
        ipv4Range.setCidrIp("0.0.0.0/0");

        Ipv6Range ipv6Range = new Ipv6Range();
        ipv6Range.setCidrIpv6("::/0");


        //{IpProtocol: -1,UserIdGroupPairs: [],Ipv6Ranges: [],PrefixListIds: [], Ipv4Ranges: [{CidrIp: 0.0.0.0/0}]}
        //expected output: cidr = "0.0.0.0/0"  range = "1-65535"
        IpPermission mockPermission1 = new IpPermission();
        mockPermission1.setFromPort(-1);
        mockPermission1.setToPort(-1);
        mockPermission1.setIpProtocol("-1");
        mockPermission1.setIpv4Ranges(Collections.singleton(ipv4Range));

        Rule outputRule1 = new Rule();
        outputRule1.protocol = AWSSecurityGroupUtils.calculateProtocol(outputRule1, mockPermission1.getIpProtocol());
        outputRule1.ports = AWSSecurityGroupUtils.calculatePorts(outputRule1, mockPermission1);
        outputRule1.ipRangeCidr = AWSSecurityGroupUtils.calculateIpRangeCidr(outputRule1, mockPermission1);
        assertEquals("wrong ports range:", SecurityGroupService.ALL_PORTS, outputRule1.ports);
        assertEquals("wrong cidr:", "0.0.0.0/0", outputRule1.ipRangeCidr);

        //{IpProtocol: icmpv6,FromPort: -1,ToPort: -1,UserIdGroupPairs: [], Ipv6Ranges: [{CidrIpv6: ::/0}],
        //PrefixListIds: [],Ipv4Ranges: []}
        //expected output: cidr = "::/0"  range = "1-65535"
        IpPermission mockPermission2 = new IpPermission();
        mockPermission2.setFromPort(-1);
        mockPermission2.setToPort(-1);
        mockPermission2.setIpProtocol("icmpv6");
        mockPermission2.setIpv6Ranges(Collections.singleton(ipv6Range));

        Rule outputRule2 = new Rule();
        outputRule2.protocol = AWSSecurityGroupUtils.calculateProtocol(outputRule2, mockPermission2.getIpProtocol());
        outputRule2.ports = AWSSecurityGroupUtils.calculatePorts(outputRule2, mockPermission2);
        outputRule2.ipRangeCidr = AWSSecurityGroupUtils.calculateIpRangeCidr(outputRule2, mockPermission2);
        assertEquals("wrong ports range:", SecurityGroupService.ALL_PORTS, outputRule2.ports);
        assertEquals("wrong cidr:", "::/0", outputRule2.ipRangeCidr);

        //{IpProtocol: icmp,FromPort: -1,ToPort: -1,UserIdGroupPairs: [], Ipv6Ranges: [{CidrIpv6: ::/0}],
        //PrefixListIds: [],Ipv4Ranges: []}
        //expected output: cidr = "::/0"  range = "1-65535"
        IpPermission mockPermission3 = new IpPermission();
        mockPermission3.setFromPort(-1);
        mockPermission3.setToPort(-1);
        mockPermission3.setIpProtocol("icmp");
        mockPermission3.setIpv6Ranges(Collections.singleton(ipv6Range));

        Rule outputRule3 = new Rule();
        outputRule3.protocol = AWSSecurityGroupUtils.calculateProtocol(outputRule3, mockPermission3.getIpProtocol());
        outputRule3.ports = AWSSecurityGroupUtils.calculatePorts(outputRule3, mockPermission3);
        outputRule3.ipRangeCidr = AWSSecurityGroupUtils.calculateIpRangeCidr(outputRule3, mockPermission3);
        assertEquals("wrong ports range:", SecurityGroupService.ALL_PORTS, outputRule3.ports);
        assertEquals("wrong cidr:", "::/0", outputRule3.ipRangeCidr);

        //{IpProtocol: icmp,FromPort: 0,ToPort: -1,UserIdGroupPairs: [], Ipv6Ranges: [],
        //PrefixListIds: [],Ipv4Ranges: [{CidrIp: 0.0.0.0/0}]}
        //expected output: cidr = "0.0.0.0/0" range = "0"
        IpPermission mockPermission4 = new IpPermission();
        mockPermission4.setFromPort(0);
        mockPermission4.setToPort(-1);
        mockPermission4.setIpProtocol("icmp");
        mockPermission4.setIpv4Ranges(Collections.singleton(ipv4Range));

        Rule outputRule4 = new Rule();
        outputRule4.protocol = AWSSecurityGroupUtils.calculateProtocol(outputRule4, mockPermission4.getIpProtocol());
        outputRule4.ports = AWSSecurityGroupUtils.calculatePorts(outputRule4, mockPermission4);
        outputRule4.ipRangeCidr = AWSSecurityGroupUtils.calculateIpRangeCidr(outputRule4, mockPermission4);
        assertEquals("wrong ports range:", "0", outputRule4.ports);
        assertEquals("wrong cidr:", "0.0.0.0/0", outputRule4.ipRangeCidr);

        //{IpProtocol: icmpv6,FromPort: -1,ToPort: -1,UserIdGroupPairs: [], Ipv6Ranges: [],PrefixListIds: [],
        //Ipv4Ranges: [{CidrIp: 0.0.0.0/0}]}
        //expected output: cidr = "*" range = "1-65535"
        IpPermission mockPermission5 = new IpPermission();
        mockPermission5.setFromPort(-1);
        mockPermission5.setToPort(-1);
        mockPermission5.setIpProtocol("icmpv6");
        mockPermission5.setIpv4Ranges(Collections.singleton(ipv4Range));

        Rule outputRule5 = new Rule();
        outputRule5.protocol = AWSSecurityGroupUtils.calculateProtocol(outputRule5, mockPermission5.getIpProtocol());
        outputRule5.ports = AWSSecurityGroupUtils.calculatePorts(outputRule5, mockPermission5);
        outputRule5.ipRangeCidr = AWSSecurityGroupUtils.calculateIpRangeCidr(outputRule5, mockPermission5);
        assertEquals("wrong ports range:", SecurityGroupService.ALL_PORTS, outputRule5.ports);
        assertEquals("wrong cidr:", SecurityGroupService.ANY, outputRule5.ipRangeCidr);

        //{IpProtocol: icmp,FromPort: 3,ToPort: 0,UserIdGroupPairs: [], Ipv6Ranges: [],PrefixListIds: [],
        //Ipv4Ranges: [{CidrIp: 0.0.0.0/0}]}
        //expected output: protocol = "ICMPv4" cidr = "0.0.0.0/0" range = "3-0"
        IpPermission mockPermission6 = new IpPermission();
        mockPermission6.setFromPort(3);
        mockPermission6.setToPort(0);
        mockPermission6.setIpProtocol("icmp");
        mockPermission6.setIpv4Ranges(Collections.singleton(ipv4Range));

        Rule outputRule6 = new Rule();
        outputRule6.protocol = AWSSecurityGroupUtils.calculateProtocol(outputRule6, mockPermission6.getIpProtocol());
        outputRule6.ports = AWSSecurityGroupUtils.calculatePorts(outputRule6, mockPermission6);
        outputRule6.ipRangeCidr = AWSSecurityGroupUtils.calculateIpRangeCidr(outputRule6, mockPermission6);
        assertEquals("wrong ports range:", "3-0", outputRule6.ports);
        assertEquals("wrong cidr:", "0.0.0.0/0", outputRule6.ipRangeCidr);

        //{IpProtocol: icmp,FromPort: 8,ToPort: -1,UserIdGroupPairs: [], Ipv6Ranges: [],PrefixListIds: [],
        //Ipv4Ranges: [{CidrIp: 0.0.0.0/0}]}
        //expected output: cidr = "0.0.0.0/0" range = "8"
        IpPermission mockPermission7 = new IpPermission();
        mockPermission7.setFromPort(8);
        mockPermission7.setToPort(-1);
        mockPermission7.setIpProtocol("icmp");
        mockPermission7.setIpv4Ranges(Collections.singleton(ipv4Range));

        Rule outputRule7 = new Rule();
        outputRule7.protocol = AWSSecurityGroupUtils.calculateProtocol(outputRule7, mockPermission7.getIpProtocol());
        outputRule7.ports = AWSSecurityGroupUtils.calculatePorts(outputRule7, mockPermission7);
        outputRule7.ipRangeCidr = AWSSecurityGroupUtils.calculateIpRangeCidr(outputRule7, mockPermission7);
        assertEquals("wrong ports range:",  "8", outputRule7.ports);
        assertEquals("wrong cidr:", "0.0.0.0/0", outputRule7.ipRangeCidr);
    }

    private void assertDefaultRules(IpPermission rule) {
        assertTrue(rule.getIpProtocol().equalsIgnoreCase(DEFAULT_PROTOCOL));
        assertTrue(rule.getIpv4Ranges().get(0).getCidrIp().equalsIgnoreCase(DEFAULT_ALLOWED_NETWORK));
        assertTrue(rule.getFromPort() == 22 || rule.getFromPort() == 80 || rule.getFromPort() == 41000);
        assertTrue(rule.getToPort() == 22 || rule.getToPort() == 80 || rule.getToPort() == 42000);
    }

    private void validateDefaultRules(List<IpPermission> rules) throws Throwable {
        ArrayList<Integer> ports = new ArrayList<>();
        for (int port : DEFAULT_ALLOWED_PORTS) {
            ports.add(port);
        }

        for (IpPermission rule : rules) {
            assertTrue(rule.getIpProtocol().equalsIgnoreCase(DEFAULT_PROTOCOL));
            if (rule.getFromPort() == 1) {
                assertTrue(rule.getIpv4Ranges().get(0).getCidrIp()
                        .equalsIgnoreCase(this.subnet));
                assertTrue(rule.getToPort() == 65535);
            } else {
                assertTrue(rule.getIpv4Ranges().get(0).getCidrIp()
                        .equalsIgnoreCase(DEFAULT_ALLOWED_NETWORK));
                assertEquals(rule.getFromPort(), rule.getToPort());
                assertTrue(ports.contains(rule.getToPort()));
            }
        }
    }

}
