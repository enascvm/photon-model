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

import static com.amazonaws.auth.policy.Statement.Effect.Allow;

import static com.vmware.photon.controller.discovery.cloudaccount.awsaccesspolicy.AwsActions.AUTO_SCALING_DESCRIBE_ALL;
import static com.vmware.photon.controller.discovery.cloudaccount.awsaccesspolicy.AwsActions.CLOUD_WATCH_DESCRIBE_ALL;
import static com.vmware.photon.controller.discovery.cloudaccount.awsaccesspolicy.AwsActions.CLOUD_WATCH_GET_ALL;
import static com.vmware.photon.controller.discovery.cloudaccount.awsaccesspolicy.AwsActions.CLOUD_WATCH_GET_METRICS_STATISTICS;
import static com.vmware.photon.controller.discovery.cloudaccount.awsaccesspolicy.AwsActions.CLOUD_WATCH_LIST_ALL;
import static com.vmware.photon.controller.discovery.cloudaccount.awsaccesspolicy.AwsActions.CLOUD_WATCH_LIST_METRICS;
import static com.vmware.photon.controller.discovery.cloudaccount.awsaccesspolicy.AwsActions.CUR_DESCRIBE_REPORT_DEFINITION;
import static com.vmware.photon.controller.discovery.cloudaccount.awsaccesspolicy.AwsActions.DESCRIBE_AVAILABILITY_ZONES;
import static com.vmware.photon.controller.discovery.cloudaccount.awsaccesspolicy.AwsActions.DESCRIBE_INSTANCES;
import static com.vmware.photon.controller.discovery.cloudaccount.awsaccesspolicy.AwsActions.DESCRIBE_INTERNET_GATEWAYS;
import static com.vmware.photon.controller.discovery.cloudaccount.awsaccesspolicy.AwsActions.DESCRIBE_ROUTE_TABLES;
import static com.vmware.photon.controller.discovery.cloudaccount.awsaccesspolicy.AwsActions.DESCRIBE_SECURITY_GROUPS;
import static com.vmware.photon.controller.discovery.cloudaccount.awsaccesspolicy.AwsActions.DESCRIBE_SUBNETS;
import static com.vmware.photon.controller.discovery.cloudaccount.awsaccesspolicy.AwsActions.DESCRIBE_VOLUMES;
import static com.vmware.photon.controller.discovery.cloudaccount.awsaccesspolicy.AwsActions.DESCRIBE_VPCS;
import static com.vmware.photon.controller.discovery.cloudaccount.awsaccesspolicy.AwsActions.EC2_DESCRIBE_ALL;
import static com.vmware.photon.controller.discovery.cloudaccount.awsaccesspolicy.AwsActions.ELASTIC_LOAD_BALANCING_DESCRIBE_ALL;
import static com.vmware.photon.controller.discovery.cloudaccount.awsaccesspolicy.AwsActions.S3_GET_ALL;
import static com.vmware.photon.controller.discovery.cloudaccount.awsaccesspolicy.AwsActions.S3_LIST_ALL;

import java.util.List;

import com.amazonaws.auth.policy.Policy;
import com.amazonaws.auth.policy.Resource;
import com.amazonaws.auth.policy.Statement.Effect;
import com.google.gson.annotations.SerializedName;

import com.vmware.xenon.common.ServiceDocument.Documentation;
import com.vmware.xenon.common.Utils;

/**
 * A representation of the AWS Access Policy JSON structure. Constructs from JSON re-serialization
 * from the {@link Policy} data structure.
 *
 * This is a more minimal structure just to serialize what is available in
 * {@link #DEFAULT_AWS_ACCESS_POLICY}.
 */
public class AwsAccessPolicy {

    /**
     * The default AWS Access Policy recommended by Discovery, per
     * {@url https://jira.eng.vmware.com/browse/VSYM-875}.
     */
    public static final AwsAccessPolicy DEFAULT_AWS_ACCESS_POLICY = AwsAccessPolicy.create(
            new Policy().withStatements(new com.amazonaws.auth.policy.Statement(Allow)
                    .withActions(
                            AUTO_SCALING_DESCRIBE_ALL,
                            CLOUD_WATCH_DESCRIBE_ALL, CLOUD_WATCH_GET_ALL, CLOUD_WATCH_LIST_ALL,
                            EC2_DESCRIBE_ALL,
                            ELASTIC_LOAD_BALANCING_DESCRIBE_ALL,
                            S3_GET_ALL, S3_LIST_ALL)
                    .withResources(new Resource("*"))));

    /**
     * The default AWS Access Policy recommended by CI for Cost and Usage Report (CUR)
     */
    public static final AwsAccessPolicy DEFAULT_AWS_CUR_ACCESS_POLICY = AwsAccessPolicy.create(
            new Policy().withStatements(new com.amazonaws.auth.policy.Statement(Allow)
                            .withActions(
                                    DESCRIBE_AVAILABILITY_ZONES,
                                    DESCRIBE_INSTANCES,
                                    DESCRIBE_INTERNET_GATEWAYS,
                                    DESCRIBE_ROUTE_TABLES,
                                    DESCRIBE_SECURITY_GROUPS,
                                    DESCRIBE_SUBNETS,
                                    DESCRIBE_VOLUMES,
                                    DESCRIBE_VPCS)
                            .withResources(new Resource("*")),
                    new com.amazonaws.auth.policy.Statement(Allow)
                            .withActions(
                                    CLOUD_WATCH_LIST_METRICS,
                                    CLOUD_WATCH_GET_METRICS_STATISTICS)
                            .withResources(new Resource("*")),
                    new com.amazonaws.auth.policy.Statement(Allow)
                            .withActions(
                                    S3_GET_ALL,
                                    S3_LIST_ALL)
                            .withResources(new Resource("*")),
                    new com.amazonaws.auth.policy.Statement(Allow)
                            .withActions(
                                    CUR_DESCRIBE_REPORT_DEFINITION)
                            .withResources(new Resource("*"))));

    /**
     * See
     * {@url https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_policies_elements_version.html}
     * for more information.
     */
    @Documentation(
            name = "Version",
            description = "The version of the AWS policy language.",
            exampleString = "2012-10-17")
    @SerializedName("Version")
    public String version;

    @Documentation(
            name = "Statement",
            description = "Defines the Effects, Actions, and Resources that this policy may utilize.")
    @SerializedName("Statement")
    public List<Statement> statement;

    /**
     * See
     * {@url https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_policies_elements_statement.html}
     * for more information.
     */
    public static class Statement {

        /**
         * See
         * {@url https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_policies_elements_sid.html}
         * for more information.
         */
        @Documentation(
                name = "Statement ID",
                description = "An optional policy identifier.",
                exampleString = "1")
        @SerializedName("Sid")
        public String sid;

        /**
         * See
         * {@url https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_policies_elements_effect.html}
         * for more information.
         */
        @Documentation(
                name = "Effect",
                description = "Specifies whether or not this statement is allowed or denied.",
                exampleString = "Allow")
        @SerializedName("Effect")
        public Effect effect;

        /**
         * See
         * {@url https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_policies_elements_action.html}
         * for more information.
         */
        @Documentation(
                name = "Action",
                description = "Describes a specific action this statement may or may not do.",
                exampleString = "ec2:Describe*")
        @SerializedName("Action")
        public List<String> action;

        /**
         * See
         * {@url https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_policies_elements_resource.html}
         * for more information.
         */
        @Documentation(
                name = "Resource",
                description = "Specifies the object(s) the statement covers.",
                exampleString = "*")
        @SerializedName("Resource")
        public List<String> resource;
    }

    /**
     * Helper method to construct a serializable {@link AwsAccessPolicy} from a {@link Policy}
     * object.
     *
     * @param policy The policy object.
     * @return An {@link AwsAccessPolicy} object.
     */
    public static AwsAccessPolicy create(Policy policy) {
        return Utils.fromJson(policy.toJson(), AwsAccessPolicy.class);
    }

}