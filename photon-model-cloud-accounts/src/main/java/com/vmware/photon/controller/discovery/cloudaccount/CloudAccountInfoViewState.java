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

package com.vmware.photon.controller.discovery.cloudaccount;

import java.util.Map;

import com.vmware.photon.controller.discovery.cloudaccount.awsaccesspolicy.AwsAccessPolicy;
import com.vmware.xenon.common.ServiceDocument.Documentation;


/**
 * A view state to show relevant cloud account information relevant for users when creating certain
 * cloud account types.
 * <p>
 * For example, to create an Amazon Resource Name (ARN) in AWS, an account ID and external ID from
 * the provider is necessary to relay to the customer in order to properly authorize Discovery to
 * authenticate.
 */
public class CloudAccountInfoViewState {

    @Documentation(description = "AWS Account Information")
    public AwsInfo aws;

    /**
     * AWS-related information.
     */
    public static class AwsInfo {

        @Documentation(
                name = "Account ID",
                description = "Discovery's Master AWS Account ID")
        public String accountId;

        @Documentation(
                name = "External ID",
                description = "External ID for logged in customer")
        public String externalId;

        @Documentation(
                name = "Access Policy",
                description = "The default access policy recommended for AWS accounts enabled in Discovery")
        @Deprecated
        public AwsAccessPolicy accessPolicy;

        @Documentation(
                name = "Map of Access Policies",
                description = "The access policy collection recommended for AWS accounts enabled " +
                        "based on service type")
        public Map<String, AwsAccessPolicy> accessPolicies;
    }

}
