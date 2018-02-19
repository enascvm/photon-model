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

import static com.vmware.photon.controller.model.adapters.util.AdapterServiceMetadata.adapter;
import static com.vmware.photon.controller.model.adapters.util.AdapterServiceMetadata.getPublicAdapters;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

import com.vmware.photon.controller.model.UriPaths.AdapterTypePath;
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
            adapter(AzureEnumerationAdapterService.class, AdapterTypePath.ENUMERATION_ADAPTER),
            adapter(AzureImageEnumerationAdapterService.class, AdapterTypePath.IMAGE_ENUMERATION_ADAPTER),
            adapter(AzureInstanceTypeService.class),
            adapter(AzureInstanceService.class, AdapterTypePath.INSTANCE_ADAPTER),
            adapter(AzureDiskService.class, AdapterTypePath.DISK_ADAPTER),
            adapter(AzureComputeDiskDay2Service.class, AdapterTypePath.DISK_DAY2_ADAPTER),
            adapter(AzureSubnetService.class, AdapterTypePath.SUBNET_ADAPTER),
            adapter(AzureSecurityGroupService.class, AdapterTypePath.SECURITY_GROUP_ADAPTER),
            adapter(AzureLoadBalancerService.class, AdapterTypePath.LOAD_BALANCER_ADAPTER),
            adapter(AzureStatsService.class, AdapterTypePath.STATS_ADAPTER),
            adapter(AzureComputeStatsGatherer.class),
            adapter(AzureComputeHostStatsGatherer.class),
            adapter(AzureComputeHostStorageStatsGatherer.class),
            adapter(AzureEndpointAdapterService.class, AdapterTypePath.ENDPOINT_CONFIG_ADAPTER),
            adapter(AzurePowerService.class, AdapterTypePath.POWER_ADAPTER),
            adapter(AzureLifecycleOperationService.class),
            adapter(AzureRegionEnumerationAdapterService.class, AdapterTypePath.REGION_ENUMERATION_ADAPTER)
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
                    host, EndpointType.azure, LINKS, getPublicAdapters(SERVICES_METADATA));

        } catch (Exception e) {
            host.log(Level.WARNING, "Exception staring Azure adapters: %s",
                    Utils.toString(e));
        }
    }

    /**
     * API to define the list of adapter Uris to be excluded from swagger documentation generation.
     * The service SELF_LINK need to be specified here.
     *
     * @return list of self links whose swagger generation needs to be excluded.
     */
    public static List<String> swaggerExcludedPrefixes() {
        return Arrays.asList(new String[]{});
    }
}
