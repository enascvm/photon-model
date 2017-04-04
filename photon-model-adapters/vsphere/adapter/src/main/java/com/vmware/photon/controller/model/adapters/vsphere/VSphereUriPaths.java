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

package com.vmware.photon.controller.model.adapters.vsphere;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.UriPaths.AdapterTypePath;

/**
 * URI definitions for vSphere adapters.
 */
public class VSphereUriPaths {

    public static final String PROVISIONING = UriPaths.PROVISIONING + "/vsphere";

    public static final String INSTANCE_SERVICE = PROVISIONING + "/instance-adapter";
    public static final String OVF_IMPORTER = PROVISIONING + "/ovf-importer";
    public static final String DC_ENUMERATOR_SERVICE = PROVISIONING + "/dc-enumerator";

    public static final String BOOT_SERVICE = PROVISIONING + "/boot-adapter";
    public static final String POWER_SERVICE = PROVISIONING + "/power-adapter";
    public static final String SNAPSHOT_SERVICE = PROVISIONING + "/snapshot-adapter";
    public static final String HEALTH_SERVICE = PROVISIONING + "/health-adapter";
    public static final String ENUMERATION_SERVICE = PROVISIONING + "/enumeration-adapter";
    public static final String IMAGE_ENUMERATION_SERVICE = PROVISIONING + "/image-enumeration-adapter";
    public static final String STATS_SERVICE = PROVISIONING + "/stats-adapter";
    public static final String ENDPOINT_CONFIG_ADAPTER = PROVISIONING + "/endpoint-config-adapter";
    public static final String DVS_NETWORK_SERVICE = PROVISIONING + "/dvs-network-adapter";

    /**
     * Map an adapter link to its {@link AdapterTypePath adapter type}.
     */
    public static final Map<String, AdapterTypePath> VSPHERE_ADAPTER_LINK_TYPES;

    static {
        Map<String, AdapterTypePath> adapterLinksByType = new HashMap<>();

        adapterLinksByType.put(INSTANCE_SERVICE, AdapterTypePath.INSTANCE_ADAPTER);
        adapterLinksByType.put(DVS_NETWORK_SERVICE, AdapterTypePath.SUBNET_ADAPTER);
        adapterLinksByType.put(STATS_SERVICE, AdapterTypePath.STATS_ADAPTER);
        adapterLinksByType.put(ENUMERATION_SERVICE, AdapterTypePath.ENUMERATION_ADAPTER);
        adapterLinksByType.put(IMAGE_ENUMERATION_SERVICE, AdapterTypePath.IMAGE_ENUMERATION_ADAPTER);
        adapterLinksByType.put(ENDPOINT_CONFIG_ADAPTER, AdapterTypePath.ENDPOINT_CONFIG_ADAPTER);
        adapterLinksByType.put(POWER_SERVICE, AdapterTypePath.POWER_ADAPTER);

        VSPHERE_ADAPTER_LINK_TYPES = Collections.unmodifiableMap(adapterLinksByType);
    }
}
