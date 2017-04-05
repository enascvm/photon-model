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

import java.util.logging.Level;

import com.vmware.photon.controller.model.adapters.azure.endpoint.AzureEndpointAdapterService;
import com.vmware.photon.controller.model.adapters.azure.enumeration.AzureEnumerationAdapterService;
import com.vmware.photon.controller.model.adapters.azure.enumeration.AzureImageEnumerationAdapterService;
import com.vmware.photon.controller.model.adapters.azure.instance.AzureInstanceService;
import com.vmware.photon.controller.model.adapters.azure.instance.AzureSubnetService;
import com.vmware.photon.controller.model.adapters.azure.stats.AzureComputeHostStatsGatherer;
import com.vmware.photon.controller.model.adapters.azure.stats.AzureComputeHostStorageStatsGatherer;
import com.vmware.photon.controller.model.adapters.azure.stats.AzureComputeStatsGatherer;
import com.vmware.photon.controller.model.adapters.azure.stats.AzureStatsService;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryService;
import com.vmware.photon.controller.model.adapters.util.EndpointAdapterUtils;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Helper class that starts provisioning adapters
 */
public class AzureAdapters {

    public static final String[] LINKS = {
            AzureEnumerationAdapterService.SELF_LINK,
            AzureImageEnumerationAdapterService.SELF_LINK,
            AzureInstanceService.SELF_LINK,
            AzureSubnetService.SELF_LINK,
            AzureStatsService.SELF_LINK,
            AzureComputeStatsGatherer.SELF_LINK,
            AzureComputeHostStatsGatherer.SELF_LINK,
            AzureComputeHostStorageStatsGatherer.SELF_LINK,
            AzureEndpointAdapterService.SELF_LINK };

    /**
     * The link of Azure configuration registered in {@link PhotonModelAdaptersRegistryService
     * End-point Adapters Registry}.
     */
    public static String CONFIG_LINK = UriUtils.buildUriPath(
            PhotonModelAdaptersRegistryService.FACTORY_LINK,
            EndpointType.azure.name());

    public static void startServices(ServiceHost host) throws Throwable {
        try {
            host.startService(new AzureEnumerationAdapterService());
            host.startService(new AzureImageEnumerationAdapterService());
            host.startService(new AzureInstanceService());
            host.startService(new AzureSubnetService());
            host.startService(new AzureStatsService());
            host.startService(new AzureComputeStatsGatherer());
            host.startService(new AzureComputeHostStatsGatherer());
            host.startService(new AzureComputeHostStorageStatsGatherer());
            host.startService(new AzureEndpointAdapterService());

            EndpointAdapterUtils.registerEndpointAdapters(
                    host, EndpointType.azure.name(), LINKS, AzureUriPaths.AZURE_ADAPTER_LINK_TYPES);

        } catch (Exception e) {
            host.log(Level.WARNING, "Exception staring Azure adapters: %s",
                    Utils.toString(e));
        }
    }
}
