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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.UriPaths.AdapterTypePath;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;

/**
 * URI definitions for AWS adapters.
 */
public class AWSUriPaths {
    public static final String AWS = "/aws";
    public static final String PROVISIONING_AWS = UriPaths.PROVISIONING
            + AWS;

    public static final String AWS_INSTANCE_ADAPTER = AdapterTypePath.INSTANCE_ADAPTER
            .adapterLink(EndpointType.aws.name());
    public static final String AWS_NETWORK_ADAPTER = AdapterTypePath.NETWORK_ADAPTER
            .adapterLink(EndpointType.aws.name());
    public static final String AWS_SUBNET_ADAPTER = AdapterTypePath.SUBNET_ADAPTER
            .adapterLink(EndpointType.aws.name());
    public static final String AWS_FIREWALL_ADAPTER = AdapterTypePath.FIREWALL_ADAPTER
            .adapterLink(EndpointType.aws.name());
    public static final String AWS_STATS_ADAPTER = AdapterTypePath.STATS_ADAPTER
            .adapterLink(EndpointType.aws.name());
    public static final String AWS_COST_STATS_ADAPTER = PROVISIONING_AWS
            + "/cost-stats-adapter";
    public static final String AWS_RESERVED_INSTANCE_PLANS_ADAPTER = PROVISIONING_AWS
            + "/reserved-instance-plans-enumeration-adapter";
    public static final String AWS_ENUMERATION_ADAPTER = AdapterTypePath.ENUMERATION_ADAPTER
            .adapterLink(EndpointType.aws.name());
    public static final String AWS_IMAGE_ENUMERATION_ADAPTER = AdapterTypePath.IMAGE_ENUMERATION_ADAPTER
            .adapterLink(EndpointType.aws.name());
    public static final String AWS_ENUMERATION_CREATION_ADAPTER = AdapterTypePath.ENUMERATION_CREATION_ADAPTER
            .adapterLink(EndpointType.aws.name());
    public static final String AWS_ENUMERATION_DELETION_ADAPTER = AdapterTypePath.ENUMERATION_DELETION_ADAPTER
            .adapterLink(EndpointType.aws.name());
    public static final String AWS_STORAGE_ENUMERATION_ADAPTER_SERVICE = PROVISIONING_AWS
            + "/storage-enumeration-adapter";
    public static final String AWS_COMPUTE_DESCRIPTION_CREATION_ADAPTER = AdapterTypePath.COMPUTE_DESCRIPTION_CREATION_ADAPTER
            .adapterLink(EndpointType.aws.name());
    public static final String AWS_COMPUTE_STATE_CREATION_ADAPTER = AdapterTypePath.COMPUTE_STATE_CREATION_ADAPTER
            .adapterLink(EndpointType.aws.name());
    public static final String AWS_NETWORK_STATE_CREATION_ADAPTER = PROVISIONING_AWS
            + "/network-state-creation-adapter";
    public static final String AWS_SECURITY_GROUP_ENUMERATION_ADAPTER = PROVISIONING_AWS
            + "/security-group-enumeration-adapter";
    public static final String AWS_ENDPOINT_CONFIG_ADAPTER = AdapterTypePath.ENDPOINT_CONFIG_ADAPTER
            .adapterLink(EndpointType.aws.name());

    public static final String AWS_POWER_ADAPTER = AdapterTypePath.POWER_ADAPTER
            .adapterLink(EndpointType.aws.name());

    /**
     * Map an adapter link to its {@link AdapterTypePath adapter type}.
     */
    public static final Map<String, AdapterTypePath> AWS_ADAPTER_LINK_TYPES;

    static {
        Map<String, AdapterTypePath> adapterLinksByType = new HashMap<>();

        adapterLinksByType.put(AWS_INSTANCE_ADAPTER, AdapterTypePath.INSTANCE_ADAPTER);
        adapterLinksByType.put(AWS_NETWORK_ADAPTER, AdapterTypePath.NETWORK_ADAPTER);
        adapterLinksByType.put(AWS_SUBNET_ADAPTER, AdapterTypePath.SUBNET_ADAPTER);
        adapterLinksByType.put(AWS_FIREWALL_ADAPTER, AdapterTypePath.FIREWALL_ADAPTER);
        adapterLinksByType.put(AWS_STATS_ADAPTER, AdapterTypePath.STATS_ADAPTER);
        adapterLinksByType.put(AWS_ENUMERATION_ADAPTER, AdapterTypePath.ENUMERATION_ADAPTER);
        adapterLinksByType.put(AWS_IMAGE_ENUMERATION_ADAPTER, AdapterTypePath.IMAGE_ENUMERATION_ADAPTER);
        adapterLinksByType.put(AWS_ENDPOINT_CONFIG_ADAPTER, AdapterTypePath.ENDPOINT_CONFIG_ADAPTER);
        adapterLinksByType.put(AWS_POWER_ADAPTER, AdapterTypePath.POWER_ADAPTER);

        AWS_ADAPTER_LINK_TYPES = Collections.unmodifiableMap(adapterLinksByType);
    }
}
