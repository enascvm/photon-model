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

package com.vmware.photon.controller.model.adapters.azure.ea;

import static com.vmware.photon.controller.model.adapters.util.AdapterServiceMetadataBuilder.createAdapter;
import static com.vmware.photon.controller.model.adapters.util.AdapterServiceMetadataBuilder.getPublicAdapters;

import java.util.List;
import java.util.logging.Level;

import com.vmware.photon.controller.model.UriPaths.AdapterTypePath;
import com.vmware.photon.controller.model.adapters.azure.ea.endpoint.AzureEaEndpointAdapterService;
import com.vmware.photon.controller.model.adapters.azure.ea.enumeration.AzureSubscriptionsEnumerationService;
import com.vmware.photon.controller.model.adapters.azure.ea.stats.AzureCostStatsService;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryService;
import com.vmware.photon.controller.model.adapters.util.EndpointAdapterUtils;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.util.PhotonModelUtils;
import com.vmware.photon.controller.model.util.StartServicesHelper;
import com.vmware.photon.controller.model.util.StartServicesHelper.ServiceMetadata;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Helper class that starts azure ea adapters
 */
public class AzureEaAdapters {

    public static final ServiceMetadata[] SERVICES_METADATA = {
            createAdapter(AzureCostStatsService.class)
                    .withAdapterType(AdapterTypePath.COST_STATS_ADAPTER)
                    .build(),
            createAdapter(AzureEaEndpointAdapterService.class)
                    .withAdapterType(AdapterTypePath.ENDPOINT_CONFIG_ADAPTER)
                    .build(),
            createAdapter(AzureSubscriptionsEnumerationService.class).build()
    };

    public static final String[] LINKS = StartServicesHelper.getServiceLinks(SERVICES_METADATA);

    /**
     * The link of Azure EA configuration registered in {@link PhotonModelAdaptersRegistryService
     * End-point Adapters Registry}.
     */
    public static String CONFIG_LINK = UriUtils.buildUriPath(
            PhotonModelAdaptersRegistryService.FACTORY_LINK,
            EndpointType.azure_ea.name());

    public static DeferredResult<List<Operation>> startServices(ServiceHost host) throws Throwable {
        DeferredResult<List<Operation>> dr = StartServicesHelper.startServices(host,
                SERVICES_METADATA);
        EndpointAdapterUtils.registerEndpointAdapters(
                host, EndpointType.azure_ea, LINKS, getPublicAdapters(SERVICES_METADATA));
        return dr;
    }

    public static void startServices(ServiceHost host, boolean isSynchronousStart) throws Throwable {
        try {
            DeferredResult<List<Operation>> dr = startServices(host);
            if (isSynchronousStart) {
                PhotonModelUtils.waitToComplete(dr);
            }
        } catch (Exception e) {
            host.log(Level.WARNING, "Exception staring Azure ea adapters: %s",
                    Utils.toString(e));
        }
    }
}
