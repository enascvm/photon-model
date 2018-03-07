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

import static com.vmware.photon.controller.model.adapters.util.AdapterServiceMetadataBuilder.createAdapter;
import static com.vmware.photon.controller.model.adapters.util.AdapterServiceMetadataBuilder.getPublicAdapters;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

import com.vmware.photon.controller.model.UriPaths.AdapterTypePath;
import com.vmware.photon.controller.model.adapters.azure.d2o.AzureLifecycleOperationService;
import com.vmware.photon.controller.model.adapters.azure.d2o.AzureLifecycleOperationService.AzureLifecycleOperationFactoryService;
import com.vmware.photon.controller.model.adapters.azure.endpoint.AzureEndpointAdapterService;
import com.vmware.photon.controller.model.adapters.azure.enumeration.AzureEnumerationAdapterService;
import com.vmware.photon.controller.model.adapters.azure.enumeration.AzureImageEnumerationAdapterService;
import com.vmware.photon.controller.model.adapters.azure.enumeration.AzureInstanceTypeService;
import com.vmware.photon.controller.model.adapters.azure.enumeration.AzureRegionEnumerationAdapterService;
import com.vmware.photon.controller.model.adapters.azure.instance.AzureComputeDiskDay2Service;
import com.vmware.photon.controller.model.adapters.azure.instance.AzureComputeDiskDay2Service.AzureComputeDiskDay2FactoryService;
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
            //Public Adapters
            createAdapter(AzureEnumerationAdapterService.class)
                    .withAdapterType(AdapterTypePath.ENUMERATION_ADAPTER)
                    .build(),
            createAdapter(AzureImageEnumerationAdapterService.class)
                    .withAdapterType(AdapterTypePath.IMAGE_ENUMERATION_ADAPTER)
                    .build(),
            createAdapter(AzureInstanceService.class)
                    .withAdapterType(AdapterTypePath.INSTANCE_ADAPTER)
                    .build(),
            createAdapter(AzureDiskService.class)
                    .withAdapterType(AdapterTypePath.DISK_ADAPTER)
                    .build(),
            createAdapter(AzureSubnetService.class)
                    .withAdapterType(AdapterTypePath.SUBNET_ADAPTER)
                    .build(),
            createAdapter(AzureSecurityGroupService.class)
                    .withAdapterType(AdapterTypePath.SECURITY_GROUP_ADAPTER)
                    .build(),
            createAdapter(AzureLoadBalancerService.class)
                    .withAdapterType(AdapterTypePath.LOAD_BALANCER_ADAPTER)
                    .build(),
            createAdapter(AzureStatsService.class)
                    .withAdapterType(AdapterTypePath.STATS_ADAPTER)
                    .build(),
            createAdapter(AzureEndpointAdapterService.class)
                    .withAdapterType(AdapterTypePath.ENDPOINT_CONFIG_ADAPTER)
                    .build(),
            createAdapter(AzurePowerService.class)
                    .withAdapterType(AdapterTypePath.POWER_ADAPTER)
                    .build(),
            createAdapter(AzureRegionEnumerationAdapterService.class)
                    .withAdapterType(AdapterTypePath.REGION_ENUMERATION_ADAPTER)
                    .build(),

            //Resource Operation Adapters
            createAdapter(AzureComputeDiskDay2Service.class)
                    .withAdapterType(AdapterTypePath.DISK_DAY2_ADAPTER)
                    .withFactoryCreator(() -> new AzureComputeDiskDay2FactoryService(true))
                    .withResourceOperationSpecs(
                            AzureComputeDiskDay2Service.getResourceOperationSpecs())
                    .build(),
            createAdapter(AzureLifecycleOperationService.class)
                    .withFactoryCreator(() -> new AzureLifecycleOperationFactoryService(true))
                    .withResourceOperationSpecs(
                            AzureLifecycleOperationService.getResourceOperationSpecs())
                    .build(),

            //Helper Adapter Services
            createAdapter(AzureComputeStatsGatherer.class).build(),
            createAdapter(AzureInstanceTypeService.class).build(),
            createAdapter(AzureComputeHostStatsGatherer.class).build(),
            createAdapter(AzureComputeHostStorageStatsGatherer.class).build()

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
