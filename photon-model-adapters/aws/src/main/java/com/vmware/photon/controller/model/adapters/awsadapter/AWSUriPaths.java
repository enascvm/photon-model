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

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.UriPaths.AdapterTypePath;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperation;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceType;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.xenon.common.UriUtils;

/**
 * URI definitions for AWS adapters.
 */
public class AWSUriPaths {

    public static final String PROVISIONING_AWS = UriUtils.buildUriPath(
            UriPaths.PROVISIONING, EndpointType.aws.name());

    public static final String ADAPTER_AWS = UriUtils.buildUriPath(
            UriPaths.ADAPTER, EndpointType.aws.name());

    // End-point management Adapters {{
    public static final String AWS_ENDPOINT_CONFIG_ADAPTER = AdapterTypePath.ENDPOINT_CONFIG_ADAPTER
            .adapterLink(EndpointType.aws.name());
    // }}

    // Provisioning related Adapters {{
    public static final String AWS_INSTANCE_ADAPTER = AdapterTypePath.INSTANCE_ADAPTER
            .adapterLink(EndpointType.aws.name());
    public static final String AWS_NETWORK_ADAPTER = AdapterTypePath.NETWORK_ADAPTER
            .adapterLink(EndpointType.aws.name());
    public static final String AWS_SUBNET_ADAPTER = AdapterTypePath.SUBNET_ADAPTER
            .adapterLink(EndpointType.aws.name());
    public static final String AWS_DISK_ADAPTER = AdapterTypePath.DISK_ADAPTER
            .adapterLink(EndpointType.aws.name());
    public static final String AWS_SECURITY_GROUP_ADAPTER = AdapterTypePath.SECURITY_GROUP_ADAPTER
            .adapterLink(EndpointType.aws.name());
    public static final String AWS_LOAD_BALANCER_ADAPTER = AdapterTypePath.LOAD_BALANCER_ADAPTER
            .adapterLink(EndpointType.aws.name());
    // }}

    public static final String AWS_STATS_ADAPTER = AdapterTypePath.STATS_ADAPTER
            .adapterLink(EndpointType.aws.name());

    // D2O related Adapters {{
    public static final String AWS_POWER_ADAPTER = AdapterTypePath.POWER_ADAPTER
            .adapterLink(EndpointType.aws.name());

    public static final String AWS_DISK_DAY2_ADAPTER = ResourceOperationSpecService
            .buildDefaultAdapterLink(
                    EndpointType.aws.name(),
                    ResourceType.COMPUTE,
                    "disk-day2-adapter");

    public static final String AWS_REBOOT_ADAPTER = ResourceOperationSpecService
            .buildDefaultAdapterLink(
                    EndpointType.aws.name(),
                    ResourceType.COMPUTE,
                    ResourceOperation.REBOOT.name());

    public static final String AWS_RESET_ADAPTER = ResourceOperationSpecService
            .buildDefaultAdapterLink(
                    EndpointType.aws.name(),
                    ResourceType.COMPUTE,
                    ResourceOperation.RESET.name());
    // }}

    // Enumeration related Adapters {{
    public static final String AWS_REGION_ENUMERATION_ADAPTER_SERVICE = AdapterTypePath.REGION_ENUMERATION_ADAPTER
            .adapterLink(EndpointType.aws.name());

    public static final String AWS_ENUMERATION_ADAPTER = AdapterTypePath.ENUMERATION_ADAPTER
            .adapterLink(EndpointType.aws.name());

    public static final String AWS_IMAGE_ENUMERATION_ADAPTER = AdapterTypePath.IMAGE_ENUMERATION_ADAPTER
            .adapterLink(EndpointType.aws.name());

    public static final String AWS_ENUMERATION_CREATION_ADAPTER = AdapterTypePath.ENUMERATION_CREATION_ADAPTER
            .adapterLink(EndpointType.aws.name());
    public static final String AWS_ENUMERATION_DELETION_ADAPTER = AdapterTypePath.ENUMERATION_DELETION_ADAPTER
            .adapterLink(EndpointType.aws.name());
    public static final String AWS_COMPUTE_DESCRIPTION_CREATION_ADAPTER = AdapterTypePath.COMPUTE_DESCRIPTION_CREATION_ADAPTER
            .adapterLink(EndpointType.aws.name());
    public static final String AWS_COMPUTE_STATE_CREATION_ADAPTER = AdapterTypePath.COMPUTE_STATE_CREATION_ADAPTER
            .adapterLink(EndpointType.aws.name());
    // }}

    public static final String AWS_INSTANCE_TYPE_ADAPTER = UriUtils.buildUriPath(
            ADAPTER_AWS, "instance-type-adapter");

    // Those adapters ARE NOT REGISTERED into Adapters Registry {{
    public static final String AWS_EBS_STORAGE_ENUMERATION_ADAPTER_SERVICE = PROVISIONING_AWS
            + "/ebs-storage-enumeration-adapter";
    public static final String AWS_S3_STORAGE_ENUMERATION_ADAPTER_SERVICE = PROVISIONING_AWS
            + "/s3-storage-enumeration-adapter";
    public static final String AWS_COST_STATS_ADAPTER = PROVISIONING_AWS
            + "/cost-stats-adapter";
    public static final String AWS_RESERVED_INSTANCE_PLANS_ADAPTER = PROVISIONING_AWS
            + "/reserved-instance-plans-enumeration-adapter";
    public static final String AWS_NETWORK_STATE_CREATION_ADAPTER = PROVISIONING_AWS
            + "/network-state-creation-adapter";
    public static final String AWS_SECURITY_GROUP_ENUMERATION_ADAPTER = PROVISIONING_AWS
            + "/security-group-enumeration-adapter";
    public static final String AWS_LOAD_BALANCER_ENUMERATION_ADAPTER = PROVISIONING_AWS
            + "/load-balancer-enumeration-adapter";
    public static final String AWS_MISSING_RESOURCES_SERVICE = PROVISIONING_AWS
            + "/missing_resources_enumeration";
    public static final String AWS_VOLUME_TYPE_ENUMERATION_ADAPTER_SERVICE = PROVISIONING_AWS
            + "/volume-type-enumeration-adapter";
    // }}
}
