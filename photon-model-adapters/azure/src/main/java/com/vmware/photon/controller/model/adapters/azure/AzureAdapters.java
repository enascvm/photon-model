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

import static com.vmware.photon.controller.model.util.StartServicesHelper.ServiceMetadata.service;

import java.util.logging.Level;

import com.vmware.photon.controller.model.adapters.azure.d2o.AzureLifecycleOperationService;
import com.vmware.photon.controller.model.adapters.azure.endpoint.AzureEndpointAdapterService;
import com.vmware.photon.controller.model.adapters.azure.enumeration.AzureEnumerationAdapterService;
import com.vmware.photon.controller.model.adapters.azure.enumeration.AzureImageEnumerationAdapterService;
import com.vmware.photon.controller.model.adapters.azure.enumeration.AzureInstanceTypeService;
import com.vmware.photon.controller.model.adapters.azure.enumeration.AzureRegionEnumerationAdapterService;
import com.vmware.photon.controller.model.adapters.azure.instance.AzureComputeDiskDay2Service;
import com.vmware.photon.controller.model.adapters.azure.instance.AzureDiskService;
import com.vmware.photon.controller.model.adapters.azure.instance.AzureInstanceService;
import com.vmware.photon.controller.model.adapters.azure.instance.AzureLoadBalancerService;
import com.vmware.photon.controller.model.adapters.azure.instance.AzureSecurityGroupService;
import com.vmware.photon.controller.model.adapters.azure.instance.AzureSubnetService;
import com.vmware.photon.controller.model.adapters.azure.power.AzurePowerService;
import com.vmware.photon.controller.model.adapters.azure.stats.AzureComputeHostStatsGatherer;
import com.vmware.photon.controller.model.adapters.azure.stats.AzureComputeHostStorageStatsGatherer;
import com.vmware.photon.controller.model.adapters.azure.stats.AzureComputeStatsGatherer;
import com.vmware.photon.controller.model.adapters.azure.stats.AzureStatsService;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryService;
import com.vmware.photon.controller.model.adapters.util.EndpointAdapterUtils;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.util.StartServicesHelper;
import com.vmware.photon.controller.model.util.StartServicesHelper.ServiceMetadata;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Helper class that starts provisioning adapters
 */
public class AzureAdapters {

    public static final ServiceMetadata[] SERVICES_METADATA = {
            service(AzureEnumerationAdapterService.class),
            service(AzureImageEnumerationAdapterService.class),
            service(AzureInstanceTypeService.class),
            service(AzureInstanceService.class),
            service(AzureDiskService.class),
            service(AzureComputeDiskDay2Service.class),
            service(AzureSubnetService.class),
            service(AzureSecurityGroupService.class),
            service(AzureLoadBalancerService.class),
            service(AzureStatsService.class),
            service(AzureComputeStatsGatherer.class),
            service(AzureComputeHostStatsGatherer.class),
            service(AzureComputeHostStorageStatsGatherer.class),
            service(AzureEndpointAdapterService.class),
            service(AzurePowerService.class),
            service(AzureLifecycleOperationService.class),
            service(AzureRegionEnumerationAdapterService.class)
    };

    public static final String[] LINKS = StartServicesHelper.getServiceLinks(SERVICES_METADATA);

    /**
     * The link of Azure configuration registered in {@link PhotonModelAdaptersRegistryService
     * End-point Adapters Registry}.
     */
    public static String CONFIG_LINK = UriUtils.buildUriPath(
            PhotonModelAdaptersRegistryService.FACTORY_LINK,
            EndpointType.azure.name());

    public static void startServices(ServiceHost host) throws Throwable {
        startServices(host, false);
    }

    public static void startServices(ServiceHost host, boolean isSynchronousStart) throws Throwable {
        try {
            if (isSynchronousStart) {
                StartServicesHelper.startServicesSynchronously(host, SERVICES_METADATA);
            } else {
                StartServicesHelper.startServices(host, SERVICES_METADATA);
            }

            EndpointAdapterUtils.registerEndpointAdapters(
                    host, EndpointType.azure, LINKS, AzureUriPaths.AZURE_ADAPTER_LINK_TYPES);

        } catch (Exception e) {
            host.log(Level.WARNING, "Exception staring Azure adapters: %s",
                    Utils.toString(e));
        }
    }
}
