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

import static java.util.Collections.singletonList;

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_GROUP_ID_FILTER;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_GROUP_NAME_FILTER;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_VPC_ID_FILTER;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.AmazonEC2Exception;
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupEgressRequest;
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupEgressResult;
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest;
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressResult;
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest;
import com.amazonaws.services.ec2.model.CreateSecurityGroupResult;
import com.amazonaws.services.ec2.model.DeleteSecurityGroupRequest;
import com.amazonaws.services.ec2.model.DeleteSecurityGroupResult;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.IpRange;
import com.amazonaws.services.ec2.model.RevokeSecurityGroupEgressRequest;
import com.amazonaws.services.ec2.model.RevokeSecurityGroupEgressResult;
import com.amazonaws.services.ec2.model.RevokeSecurityGroupIngressRequest;
import com.amazonaws.services.ec2.model.RevokeSecurityGroupIngressResult;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.Vpc;

import org.apache.commons.collections.CollectionUtils;

import com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState.Rule;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState.Rule.Access;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;

/**
 * This client abstracts the communication with Amazon Network service.
 */
public class AWSSecurityGroupClient {

    public static final String DEFAULT_SECURITY_GROUP_NAME = "photon-model-sg";
    public static final String DEFAULT_SECURITY_GROUP_DESC = "VMware Photon model security group";

    public static final String SECURITY_GROUP_RULE_NOT_FOUND = "InvalidPermission.NotFound";
    public static final String SECURITY_GROUP_RULE_DUPLICATE = "InvalidPermission.Duplicate";

    public static final String ALL_TRAFFIC = "*";
    public static final String DEFAULT_PROTOCOL = "tcp";
    public static final int[] DEFAULT_ALLOWED_PORTS = { 22, 443, 80, 8080,
            2376, 2375, 1 };
    public static final String DEFAULT_ALLOWED_NETWORK = "0.0.0.0/0";

    private final AmazonEC2AsyncClient client;
    private StatelessService service;

    public AWSSecurityGroupClient(AmazonEC2AsyncClient client) {
        this.client = client;
    }

    public AWSSecurityGroupClient(StatelessService service, AmazonEC2AsyncClient client) {
        this(client);
        this.service = service;
    }

    public DeferredResult<String> createSecurityGroupAsync(String name, String description,
            String vpcId) {

        CreateSecurityGroupRequest req = new CreateSecurityGroupRequest()
                .withDescription(description)
                .withGroupName(name);

        // set vpc for the security group if provided
        if (vpcId != null) {
            req = req.withVpcId(vpcId);
        }

        String message = "Create AWS Security Group with name [" + name
                + "] on VPC [" + vpcId + "].";

        AWSDeferredResultAsyncHandler<CreateSecurityGroupRequest, CreateSecurityGroupResult>
                handler = new AWSDeferredResultAsyncHandler<>(this.service, message);

        this.client.createSecurityGroupAsync(req, handler);

        return handler.toDeferredResult()
                .thenApply(CreateSecurityGroupResult::getGroupId);
    }

    public String createSecurityGroup(String name, String description, String vpcId) {
        CreateSecurityGroupRequest req = new CreateSecurityGroupRequest()
                .withDescription(description)
                .withGroupName(name);

        // set vpc for the security group if provided
        if (vpcId != null) {
            req = req.withVpcId(vpcId);
        }

        CreateSecurityGroupResult result = this.client.createSecurityGroup(req);

        return result.getGroupId();
    }

    public String createDefaultSecurityGroup(String vpcId) {
        String groupId;
        try {
            groupId = createSecurityGroup(DEFAULT_SECURITY_GROUP_NAME,
                    DEFAULT_SECURITY_GROUP_DESC, vpcId);
        } catch (AmazonServiceException t) {
            if (t.getMessage().contains(
                    DEFAULT_SECURITY_GROUP_NAME)) {
                groupId = getSecurityGroup(DEFAULT_SECURITY_GROUP_NAME,
                        vpcId).getGroupId();
            } else {
                throw t;
            }
        }
        return groupId;
    }

    public String createDefaultSecurityGroupWithDefaultRules(Vpc vpc) {
        String groupId;
        try {
            groupId = createDefaultSecurityGroup(vpc.getVpcId());
            addIngressRules(groupId,
                    getDefaultRules(vpc.getCidrBlock()));
        } catch (AmazonServiceException t) {
            if (t.getMessage().contains(
                    DEFAULT_SECURITY_GROUP_NAME)) {
                groupId = getSecurityGroup(DEFAULT_SECURITY_GROUP_NAME,
                        vpc.getVpcId()).getGroupId();
            } else {
                throw t;
            }
        }
        return groupId;
    }

    public DeferredResult<Void> updateIngressRules(List<Rule> rules, String groupId) {
        return addIngressRulesAsync(groupId, buildRules(rules.stream().filter(r ->
                r.access.equals(Access.Allow)).collect(Collectors.toList())))
                .thenCompose(r -> removeIngressRules(groupId,
                        buildRules(rules.stream().filter(ri -> ri.access.equals(Access.Deny))
                                .collect(Collectors.toList()))))
                .thenApply(r -> (Void)null);
    }

    public DeferredResult<Void> addIngressRulesAsync(String groupId, List<IpPermission> rules) {
        if (CollectionUtils.isNotEmpty(rules)) {
            AuthorizeSecurityGroupIngressRequest req = new AuthorizeSecurityGroupIngressRequest()
                    .withGroupId(groupId).withIpPermissions(rules);

            String message = "Create Ingress Rules on AWS Security Group with id [" + groupId +
                    "].";

            AWSDeferredResultAsyncHandler<AuthorizeSecurityGroupIngressRequest,
                    AuthorizeSecurityGroupIngressResult>
                    handler = new AWSDeferredResultAsyncHandler<AuthorizeSecurityGroupIngressRequest,
                    AuthorizeSecurityGroupIngressResult>(this.service, message) {

                        @Override
                        protected Exception consumeError(Exception e) {
                            if (e instanceof AmazonEC2Exception &&
                                    ((AmazonEC2Exception)e).getErrorCode().equals
                                            (SECURITY_GROUP_RULE_DUPLICATE)) {
                                Utils.log(AWSUtils.class, AWSUtils.class.getSimpleName(),
                                        Level.WARNING, () -> String
                                                .format("Ingress rules already exist: %s",
                                                        Utils.toString(e)));
                                return null;
                            } else {
                                return e;
                            }
                        }
                    };
            this.client.authorizeSecurityGroupIngressAsync(req, handler);
            return handler.toDeferredResult()
                    .thenApply(r -> (Void)null);
        } else {
            return DeferredResult.completed(null);
        }
    }

    public void addIngressRules(String groupId, List<IpPermission> rules) {
        if (CollectionUtils.isNotEmpty(rules)) {
            AuthorizeSecurityGroupIngressRequest req = new AuthorizeSecurityGroupIngressRequest()
                    .withGroupId(groupId).withIpPermissions(rules);
            try {
                this.client.authorizeSecurityGroupIngress(req);
            } catch (AmazonEC2Exception e) {
                if (e.getErrorCode().equals(SECURITY_GROUP_RULE_DUPLICATE)) {
                    Utils.log(AWSUtils.class, AWSUtils.class.getSimpleName(),
                            Level.WARNING, () -> String
                                    .format("Ingress rules already exist: %s", Utils.toString(e)));
                } else {
                    throw e;
                }
            }
        }
    }

    public DeferredResult<Void> removeIngressRules(String groupId, List<IpPermission> rules) {
        if (CollectionUtils.isNotEmpty(rules)) {
            RevokeSecurityGroupIngressRequest req = new RevokeSecurityGroupIngressRequest()
                    .withGroupId(groupId).withIpPermissions(rules);

            String message = "Remove Ingress Rules from AWS Security Group with id [" + groupId +
                    "].";

            AWSDeferredResultAsyncHandler<RevokeSecurityGroupIngressRequest,
                    RevokeSecurityGroupIngressResult>
                    handler = new AWSDeferredResultAsyncHandler<RevokeSecurityGroupIngressRequest,
                    RevokeSecurityGroupIngressResult>(this.service, message) {

                        @Override
                        protected Exception consumeError(Exception e) {
                            if (e instanceof AmazonEC2Exception &&
                                    ((AmazonEC2Exception)e).getErrorCode().equals
                                            (SECURITY_GROUP_RULE_NOT_FOUND)) {
                                Utils.log(AWSUtils.class, AWSUtils.class.getSimpleName(),
                                        Level.WARNING, () -> String
                                                .format("Ingress rules cannot be removed because "
                                                        + "they do not exist: %s",
                                                        Utils.toString(e)));
                                return null;
                            } else {
                                return e;
                            }
                        }
                    };
            this.client.revokeSecurityGroupIngressAsync(req, handler);
            return handler.toDeferredResult()
                    .thenApply(r -> (Void)null);
        } else {
            return DeferredResult.completed(null);
        }
    }

    public DeferredResult<Void> updateEgressRules(List<Rule> rules, String groupId) {
        return addEgressRules(groupId, buildRules(rules.stream().filter(r -> r.access.equals
                (Access.Allow)).collect(Collectors.toList())))
                .thenCompose(r -> removeEgressRules(groupId,
                        buildRules(rules.stream().filter(ri -> ri.access.equals(Access.Deny))
                                .collect(Collectors.toList()))))
                .thenApply(r -> (Void)null);
    }

    public DeferredResult<Void> addEgressRules(String groupId, List<IpPermission> rules) {
        if (CollectionUtils.isNotEmpty(rules)) {
            AuthorizeSecurityGroupEgressRequest req = new AuthorizeSecurityGroupEgressRequest()
                    .withGroupId(groupId).withIpPermissions(rules);

            String message = "Create Egress Rules on AWS Security Group with id [" + groupId +
                    "].";

            AWSDeferredResultAsyncHandler<AuthorizeSecurityGroupEgressRequest,
                    AuthorizeSecurityGroupEgressResult>
                    handler = new AWSDeferredResultAsyncHandler<AuthorizeSecurityGroupEgressRequest,
                    AuthorizeSecurityGroupEgressResult>(this.service, message) {

                        @Override
                        protected Exception consumeError(Exception e) {
                            if (e instanceof AmazonEC2Exception &&
                                    ((AmazonEC2Exception)e).getErrorCode().equals
                                            (SECURITY_GROUP_RULE_DUPLICATE)) {
                                Utils.log(AWSUtils.class, AWSUtils.class.getSimpleName(),
                                        Level.WARNING, () -> String
                                                .format("Egress rules already exist: %s",
                                                        Utils.toString(e)));
                                return null;
                            } else {
                                return e;
                            }
                        }
                    };
            this.client.authorizeSecurityGroupEgressAsync(req, handler);
            return handler.toDeferredResult()
                    .thenApply(r -> (Void)null);
        } else {
            return DeferredResult.completed(null);
        }
    }

    public DeferredResult<Void> removeEgressRules(String groupId, List<IpPermission> rules) {
        if (CollectionUtils.isNotEmpty(rules)) {
            RevokeSecurityGroupEgressRequest req = new RevokeSecurityGroupEgressRequest()
                    .withGroupId(groupId).withIpPermissions(rules);

            String message = "Remove Egress Rules from AWS Security Group with id [" + groupId +
                    "].";

            AWSDeferredResultAsyncHandler<RevokeSecurityGroupEgressRequest,
                    RevokeSecurityGroupEgressResult>
                    handler = new AWSDeferredResultAsyncHandler<RevokeSecurityGroupEgressRequest,
                    RevokeSecurityGroupEgressResult>(this.service, message) {

                        @Override
                        protected Exception consumeError(Exception e) {
                            if (e instanceof AmazonEC2Exception &&
                                    ((AmazonEC2Exception)e).getErrorCode().equals
                                            (SECURITY_GROUP_RULE_NOT_FOUND)) {
                                Utils.log(AWSUtils.class, AWSUtils.class.getSimpleName(),
                                        Level.WARNING, () -> String
                                                .format("Egress rules cannot be removed because "
                                                        + "they do not exist: %s",
                                                        Utils.toString(e)));
                                return null;
                            } else {
                                return e;
                            }
                        }
                    };
            this.client.revokeSecurityGroupEgressAsync(req, handler);
            return handler.toDeferredResult()
                    .thenApply(r -> (Void)null);
        } else {
            return DeferredResult.completed(null);
        }
    }

    public DeferredResult<Void> deleteSecurityGroupAsync(String securityGroupId) {
        DeleteSecurityGroupRequest req = new DeleteSecurityGroupRequest()
                .withGroupId(securityGroupId);
        String message = "Delete AWS Security Group with id [" + securityGroupId + "].";

        AWSDeferredResultAsyncHandler<DeleteSecurityGroupRequest, DeleteSecurityGroupResult>
                handler = new AWSDeferredResultAsyncHandler<>(this.service, message);

        this.client.deleteSecurityGroupAsync(req, handler);

        return handler.toDeferredResult()
                .thenApply(result -> (Void) null);
    }

    public void deleteSecurityGroup(String securityGroupId) {
        DeleteSecurityGroupRequest req = new DeleteSecurityGroupRequest()
                .withGroupId(securityGroupId);

        this.client.deleteSecurityGroup(req);
    }

    public SecurityGroup getSecurityGroupById(String groupId) {
        SecurityGroup cellGroup = null;

        DescribeSecurityGroupsRequest req = new DescribeSecurityGroupsRequest()
                .withGroupIds(groupId);
        DescribeSecurityGroupsResult cellGroups = this.client.describeSecurityGroups(req);
        if (cellGroups != null) {
            cellGroup = cellGroups.getSecurityGroups().get(0);
        }
        return cellGroup;
    }

    public List<SecurityGroup> getSecurityGroups(List<String> names, String vpcId) {

        DescribeSecurityGroupsRequest req = new DescribeSecurityGroupsRequest();

        req.withFilters(new Filter(AWS_GROUP_NAME_FILTER, names));
        if (vpcId != null) {
            req.withFilters(new Filter(AWS_VPC_ID_FILTER, Collections.singletonList(vpcId)));
        }

        DescribeSecurityGroupsResult groups = this.client.describeSecurityGroups(req);
        return groups != null ? groups.getSecurityGroups() : Collections.emptyList();
    }

    public DeferredResult<DescribeSecurityGroupsResult> getSecurityGroups(List<String> secGroupIds,
            String vpcId, String nicName, String vmName) {
        DescribeSecurityGroupsRequest req = new DescribeSecurityGroupsRequest()
                .withFilters(new Filter(AWS_GROUP_ID_FILTER, secGroupIds))
                .withFilters(new Filter(AWS_VPC_ID_FILTER, singletonList(vpcId)));

        String msg = "Getting AWS Security Groups by id ["
                + secGroupIds
                + "] for [" + nicName + "] NIC for ["
                + vmName
                + "] VM";

        AWSDeferredResultAsyncHandler<DescribeSecurityGroupsRequest, DescribeSecurityGroupsResult>
                handler = new AWSDeferredResultAsyncHandler<>(this.service, msg);

        this.client.describeSecurityGroupsAsync(req, handler);

        return handler.toDeferredResult();

    }

    public SecurityGroup getSecurityGroup(String name, String vpcId) {
        SecurityGroup cellGroup = null;

        DescribeSecurityGroupsRequest req = new DescribeSecurityGroupsRequest()
                .withFilters(new Filter("group-name", Collections.singletonList(name)));
        if (vpcId != null) {
            req.withFilters(new Filter("vpc-id", Collections.singletonList(vpcId)));
        }
        DescribeSecurityGroupsResult cellGroups = this.client.describeSecurityGroups(req);
        if (cellGroups != null && !cellGroups.getSecurityGroups().isEmpty()) {
            cellGroup = cellGroups.getSecurityGroups().get(0);
        }
        return cellGroup;
    }

    public SecurityGroup getDefaultSecurityGroup(String vpcId) {
        SecurityGroup cellGroup = null;

        DescribeSecurityGroupsRequest req = new DescribeSecurityGroupsRequest()
                .withFilters(new Filter("group-name",
                        Collections.singletonList(DEFAULT_SECURITY_GROUP_NAME)));
        if (vpcId != null) {
            req.withFilters(new Filter("vpc-id", Collections.singletonList(vpcId)));
        }
        DescribeSecurityGroupsResult cellGroups = this.client.describeSecurityGroups(req);
        if (cellGroups != null && !cellGroups.getSecurityGroups().isEmpty()) {
            cellGroup = cellGroups.getSecurityGroups().get(0);
        }
        return cellGroup;
    }

    public List<IpPermission> getDefaultRules(String subnet) {
        List<IpPermission> rules = new ArrayList<>();
        for (int port : DEFAULT_ALLOWED_PORTS) {
            if (port > 1) {
                rules.add(createRule(port));
            } else {
                rules.add(createRule(1, 65535, subnet, DEFAULT_PROTOCOL));
            }
        }
        return rules;
    }

    private IpPermission createRule(int port) {
        return createRule(port, port, DEFAULT_ALLOWED_NETWORK, DEFAULT_PROTOCOL);
    }

    private IpPermission createRule(int fromPort, int toPort, String subnet,
            String protocol) {

        IpRange ipRange = new IpRange().withCidrIp(subnet);

        protocol = protocol.equals(ALL_TRAFFIC) ? "-1" : protocol;

        return new IpPermission()
                .withIpProtocol(protocol)
                .withFromPort(fromPort)
                .withToPort(toPort)
                .withIpv4Ranges(ipRange);
    }

    /**
     * Builds the white list rules for the firewall
     */
    public List<IpPermission> buildRules(List<Rule> allowRules) {
        ArrayList<IpPermission> awsRules = new ArrayList<>();
        for (Rule rule : allowRules) {
            int fromPort;
            int toPort;
            if (rule.ports.contains("-")) {
                String[] ports = rule.ports.split("-");
                fromPort = Integer.parseInt(ports[0]);
                toPort = Integer.parseInt(ports[1]);
            } else {
                fromPort = Integer.parseInt(rule.ports);
                toPort = fromPort;
            }
            awsRules.add(createRule(fromPort, toPort, rule.ipRangeCidr,
                    rule.protocol));
        }
        return awsRules;
    }
}
