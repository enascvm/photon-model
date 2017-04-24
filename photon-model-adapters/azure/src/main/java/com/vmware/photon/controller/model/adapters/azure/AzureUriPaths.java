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

package com.vmware.photon.controller.model.adapters.azure;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.UriPaths.AdapterTypePath;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;

/**
 * URI definitions for Azure adapter.
 */
public class AzureUriPaths {

    public static final String PROVISIONING_AZURE = UriPaths.PROVISIONING
            + "/azure";
    public static final String AZURE_INSTANCE_ADAPTER = AdapterTypePath.INSTANCE_ADAPTER
            .adapterLink(EndpointType.azure.name());
    public static final String AZURE_SUBNET_ADAPTER = AdapterTypePath.SUBNET_ADAPTER
            .adapterLink(EndpointType.azure.name());
    public static final String AZURE_STATS_ADAPTER = AdapterTypePath.STATS_ADAPTER
            .adapterLink(EndpointType.azure.name());
    public static final String AZURE_COMPUTE_STATS_GATHERER = PROVISIONING_AZURE
            + "/compute-stats-gatherer";
    public static final String AZURE_COMPUTE_HOST_STATS_GATHERER = PROVISIONING_AZURE
            + "/compute-host-stats-gatherer";
    public static final String AZURE_COMPUTE_HOST_STORAGE_STATS_GATHERER = PROVISIONING_AZURE
            + "/compute-host-storage-stats-gatherer";
    public static final String AZURE_ENUMERATION_ADAPTER = AdapterTypePath.ENUMERATION_ADAPTER
            .adapterLink(EndpointType.azure.name());
    public static final String AZURE_IMAGE_ENUMERATION_ADAPTER = AdapterTypePath.IMAGE_ENUMERATION_ADAPTER
            .adapterLink(EndpointType.azure.name());
    public static final String AZURE_COMPUTE_ENUMERATION_ADAPTER = PROVISIONING_AZURE
            + "/compute-enumeration-adapter";
    public static final String AZURE_STORAGE_ENUMERATION_ADAPTER = PROVISIONING_AZURE
            + "/storage-enumeration-adapter";
    public static final String AZURE_NETWORK_ENUMERATION_ADAPTER = PROVISIONING_AZURE
            + "/network-enumeration-adapter";
    public static final String AZURE_RESOURCE_GROUP_ENUMERATION_ADAPTER = PROVISIONING_AZURE
            + "/resource-group-enumeration-adapter";
    public static final String AZURE_FIREWALL_ENUMERATION_ADAPTER = PROVISIONING_AZURE
            + "/firewall-enumeration-adapter";
    public static final String AZURE_SUBSCRIPTIONS_ENUMERATOR = PROVISIONING_AZURE
            + "/subscriptions-enumerator";

    public static final String AZURE_ENDPOINT_CONFIG_ADAPTER = AdapterTypePath.ENDPOINT_CONFIG_ADAPTER
            .adapterLink(EndpointType.azure.name());

    public static final String AZURE_FIREWALL_ADAPTER = AdapterTypePath.FIREWALL_ADAPTER
            .adapterLink(EndpointType.azure.name());

    /**
     * Map an adapter link to its {@link AdapterTypePath adapter type}.
     */
    public static final Map<String, AdapterTypePath> AZURE_ADAPTER_LINK_TYPES;

    static {
        Map<String, AdapterTypePath> adapterLinksByType = new HashMap<>();

        adapterLinksByType.put(AZURE_INSTANCE_ADAPTER, AdapterTypePath.INSTANCE_ADAPTER);
        adapterLinksByType.put(AZURE_STATS_ADAPTER, AdapterTypePath.STATS_ADAPTER);
        adapterLinksByType.put(AZURE_ENUMERATION_ADAPTER, AdapterTypePath.ENUMERATION_ADAPTER);
        adapterLinksByType.put(AZURE_IMAGE_ENUMERATION_ADAPTER, AdapterTypePath.IMAGE_ENUMERATION_ADAPTER);
        adapterLinksByType.put(AZURE_ENDPOINT_CONFIG_ADAPTER, AdapterTypePath.ENDPOINT_CONFIG_ADAPTER);
        adapterLinksByType.put(AZURE_FIREWALL_ADAPTER, AdapterTypePath.FIREWALL_ADAPTER);
        adapterLinksByType.put(AZURE_SUBNET_ADAPTER, AdapterTypePath.SUBNET_ADAPTER);

        AZURE_ADAPTER_LINK_TYPES = Collections.unmodifiableMap(adapterLinksByType);
    }

}
