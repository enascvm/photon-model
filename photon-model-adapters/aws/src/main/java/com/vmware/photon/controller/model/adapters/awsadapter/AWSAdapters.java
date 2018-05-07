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

import static com.vmware.photon.controller.model.adapters.util.AdapterServiceMetadataBuilder.createAdapter;
import static com.vmware.photon.controller.model.adapters.util.AdapterServiceMetadataBuilder.getPublicAdapters;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

import com.vmware.photon.controller.model.UriPaths.AdapterTypePath;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSComputeDiskDay2Service.AWSComputeDiskDay2FactoryService;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSRebootService.AWSRebootFactoryService;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSResetService.AWSResetServiceFactoryService;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSEnumerationAdapterService;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSImageEnumerationAdapterService;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSMissingResourcesEnumerationService;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSRegionEnumerationAdapterService;
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
 * Helper class that starts provisioning adapters
 */
public class AWSAdapters {

    public static final ServiceMetadata[] SERVICES_METADATA = {

            //Public Adapters
            createAdapter(AWSInstanceService.class)
                    .withAdapterType(AdapterTypePath.INSTANCE_ADAPTER)
                    .build(),
            createAdapter(AWSNetworkService.class)
                    .withAdapterType(AdapterTypePath.NETWORK_ADAPTER)
                    .build(),
            createAdapter(AWSDiskService.class)
                    .withAdapterType(AdapterTypePath.DISK_ADAPTER)
                    .build(),
            createAdapter(AWSSubnetService.class)
                    .withAdapterType(AdapterTypePath.SUBNET_ADAPTER)
                    .build(),
            createAdapter(AWSLoadBalancerService.class)
                    .withAdapterType(AdapterTypePath.LOAD_BALANCER_ADAPTER)
                    .build(),
            createAdapter(AWSStatsService.class)
                    .withAdapterType(AdapterTypePath.STATS_ADAPTER)
                    .build(),
            createAdapter(AWSCostStatsService.class)
                    .withAdapterType(AdapterTypePath.COST_STATS_ADAPTER)
                    .build(),
            createAdapter(AWSEnumerationAdapterService.class)
                    .withAdapterType(AdapterTypePath.ENUMERATION_ADAPTER)
                    .build(),
            createAdapter(AWSImageEnumerationAdapterService.class)
                    .withAdapterType(AdapterTypePath.IMAGE_ENUMERATION_ADAPTER)
                    .build(),
            createAdapter(AWSEndpointAdapterService.class)
                    .withAdapterType(AdapterTypePath.ENDPOINT_CONFIG_ADAPTER)
                    .build(),
            createAdapter(AWSPowerService.class)
                    .withAdapterType(AdapterTypePath.POWER_ADAPTER)
                    .build(),
            createAdapter(AWSSecurityGroupService.class)
                    .withAdapterType(AdapterTypePath.SECURITY_GROUP_ADAPTER)
                    .build(),
            createAdapter(AWSRegionEnumerationAdapterService.class)
                    .withAdapterType(AdapterTypePath.REGION_ENUMERATION_ADAPTER)
                    .build(),

            //Resource Operation Adapters
            createAdapter(AWSRebootService.class)
                    .withAdapterType(AdapterTypePath.BOOT_ADAPTER)
                    .withFactoryCreator(() -> new AWSRebootFactoryService(true))
                    .withResourceOperationSpecs(AWSRebootService.getResourceOperationSpec())
                    .build(),
            createAdapter(AWSComputeDiskDay2Service.class)
                    .withAdapterType(AdapterTypePath.DISK_DAY2_ADAPTER)
                    .withFactoryCreator(() -> new AWSComputeDiskDay2FactoryService(true))
                    .withResourceOperationSpecs(AWSComputeDiskDay2Service.getResourceOperationSpecs())
                    .build(),
            createAdapter(AWSResetService.class)
                    .withFactoryCreator(() -> new AWSResetServiceFactoryService(true))
                    .withResourceOperationSpecs(AWSResetService.getResourceOperationSpec())
                    .build(),

            //Helper Adapter Services
            createAdapter(AWSMissingResourcesEnumerationService.class).build(),
            createAdapter(AWSInstanceTypeService.class).build(),
            createAdapter(AWSReservedInstancePlanService.class).build()
    };

    public static final String[] LINKS = StartServicesHelper.getServiceLinks(SERVICES_METADATA);

    /**
     * The link of AWS configuration registered in {@link PhotonModelAdaptersRegistryService
     * End-point Adapters Registry}.
     */
    public static String CONFIG_LINK = UriUtils.buildUriPath(
            PhotonModelAdaptersRegistryService.FACTORY_LINK,
            EndpointType.aws.name());

    public static DeferredResult<List<Operation>> startServices(ServiceHost host) throws Throwable {
        DeferredResult<List<Operation>> dr = StartServicesHelper.startServices(host,
                SERVICES_METADATA);
        EndpointAdapterUtils.registerEndpointAdapters(
                host, EndpointType.aws, LINKS, getPublicAdapters(SERVICES_METADATA));
        return dr;
    }

    public static void startServices(ServiceHost host, boolean isSynchronousStart) throws Throwable {
        try {
            DeferredResult<List<Operation>> dr = startServices(host);
            if (isSynchronousStart) {
                PhotonModelUtils.waitToComplete(dr);
            }
        } catch (Exception e) {
            host.log(Level.WARNING, "Exception starting AWS adapters: %s",
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
