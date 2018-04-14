/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.discovery.cloudaccount.awsaccesspolicy;

import com.amazonaws.auth.policy.Action;

/**
 * Custom AWS Actions to satisfy Discovery Access Policy Requirements.
 */
public enum AwsActions implements Action {
    AUTO_SCALING_DESCRIBE_ALL("autoscaling:Describe*"),
    CLOUD_WATCH_DESCRIBE_ALL("cloudwatch:Describe*"),
    CLOUD_WATCH_GET_ALL("cloudwatch:Get*"),
    CLOUD_WATCH_LIST_ALL("cloudwatch:List*"),
    EC2_DESCRIBE_ALL("ec2:Describe*"),
    ELASTIC_LOAD_BALANCING_DESCRIBE_ALL("elasticloadbalancing:Describe*"),
    S3_GET_ALL("s3:Get*"),
    S3_LIST_ALL("s3:List*"),
    DESCRIBE_AVAILABILITY_ZONES("ec2:DescribeAvailabilityZones"),
    DESCRIBE_INSTANCES("ec2:DescribeInstances"),
    DESCRIBE_INTERNET_GATEWAYS("ec2:DescribeInternetGateways"),
    DESCRIBE_ROUTE_TABLES("ec2:DescribeRouteTables"),
    DESCRIBE_SECURITY_GROUPS("ec2:DescribeSecurityGroups"),
    DESCRIBE_SUBNETS("ec2:DescribeSubnets"),
    DESCRIBE_VOLUMES("ec2:DescribeVolumes"),
    DESCRIBE_VPCS("ec2:DescribeVpcs"),
    CLOUD_WATCH_LIST_METRICS("cloudwatch:ListMetrics"),
    CLOUD_WATCH_GET_METRICS_STATISTICS("cloudwatch:GetMetricStatistics"),
    CUR_DESCRIBE_REPORT_DEFINITION("cur:DescribeReportDefinition"),
    CUR_PUT_REPORT_DEFINITIONS("cur:PutReportDefinitions");

    private final String action;

    AwsActions(String action) {
        this.action = action;
    }

    public String getActionName() {
        return this.action;
    }
}
