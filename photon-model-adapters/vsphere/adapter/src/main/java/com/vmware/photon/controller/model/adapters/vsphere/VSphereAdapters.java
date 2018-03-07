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

import static com.vmware.photon.controller.model.adapters.util.AdapterServiceMetadataBuilder.createAdapter;
import static com.vmware.photon.controller.model.adapters.util.AdapterServiceMetadataBuilder.createFactoryAdapter;
import static com.vmware.photon.controller.model.adapters.util.AdapterServiceMetadataBuilder.getPublicAdapters;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

import com.vmware.photon.controller.model.UriPaths.AdapterTypePath;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryService;
import com.vmware.photon.controller.model.adapters.util.EndpointAdapterUtils;
import com.vmware.photon.controller.model.adapters.vsphere.VSphereAdapterD2PowerOpsService.VSphereAdapterD2PowerOpsFactoryService;
import com.vmware.photon.controller.model.adapters.vsphere.VSphereAdapterResizeComputeService.VSphereAdapterResizeComputeFactoryService;
import com.vmware.photon.controller.model.adapters.vsphere.VSphereAdapterSnapshotService.VSphereAdapterSnapshotFactoryService;
import com.vmware.photon.controller.model.adapters.vsphere.VSphereComputeDiskManagementService.VSphereComputeDiskManagementFactoryService;
import com.vmware.photon.controller.model.adapters.vsphere.network.DvsNetworkService;
import com.vmware.photon.controller.model.adapters.vsphere.ovf.OvfImporterService;
import com.vmware.photon.controller.model.adapters.vsphere.stats.VSphereAdapterStatsService;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.util.StartServicesHelper;
import com.vmware.photon.controller.model.util.StartServicesHelper.ServiceMetadata;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Facade for starting all vSphere adapters on a host.
 */
public class VSphereAdapters {

    private static final ServiceMetadata[] SERVICES_METADATA = {
            //Public Adapters
            createAdapter(VSphereDiskService.class)
                    .withAdapterType(AdapterTypePath.DISK_ADAPTER)
                    .build(),
            createAdapter(VSphereEndpointAdapterService.class)
                    .withAdapterType(AdapterTypePath.ENDPOINT_CONFIG_ADAPTER)
                    .build(),
            createAdapter(VSphereAdapterResourceEnumerationService.class)
                    .withAdapterType(AdapterTypePath.ENUMERATION_ADAPTER)
                    .build(),
            createAdapter(VSphereAdapterImageEnumerationService.class)
                    .withAdapterType(AdapterTypePath.IMAGE_ENUMERATION_ADAPTER)
                    .build(),
            createAdapter(VSphereAdapterInstanceService.class)
                    .withAdapterType(AdapterTypePath.INSTANCE_ADAPTER)
                    .build(),
            createAdapter(VSphereAdapterPowerService.class)
                    .withAdapterType(AdapterTypePath.POWER_ADAPTER)
                    .build(),
            createAdapter(VSphereRegionEnumerationAdapterService.class)
                    .withAdapterType(AdapterTypePath.REGION_ENUMERATION_ADAPTER)
                    .build(),
            createAdapter(VSphereAdapterStatsService.class)
                    .withAdapterType(AdapterTypePath.STATS_ADAPTER)
                    .build(),
            createAdapter(DvsNetworkService.class)
                    .withAdapterType(AdapterTypePath.SUBNET_ADAPTER)
                    .build(),

            //Resource Operation Adapters
            createAdapter(VSphereAdapterSnapshotService.class)
                    .withFactoryCreator(() -> new VSphereAdapterSnapshotFactoryService(true))
                    .withResourceOperationSpecs(
                            VSphereAdapterSnapshotService.getResourceOperationSpecs())
                    .build(),
            createAdapter(VSphereAdapterD2PowerOpsService.class)
                    .withFactoryCreator(() -> new VSphereAdapterD2PowerOpsFactoryService(true))
                    .withResourceOperationSpecs(
                            VSphereAdapterD2PowerOpsService.getResourceOperationSpecs())
                    .build(),
            createAdapter(VSphereAdapterResizeComputeService.class)
                    .withFactoryCreator(() -> new VSphereAdapterResizeComputeFactoryService(true))
                    .withResourceOperationSpecs(
                            VSphereAdapterResizeComputeService.getResourceOperationSpecs())
                    .build(),
            createAdapter(VSphereComputeDiskManagementService.class)
                    .withFactoryCreator(() -> new VSphereComputeDiskManagementFactoryService(true))
                    .withResourceOperationSpecs(
                            VSphereComputeDiskManagementService.getResourceOperationSpecs())
                    .build(),

            //Helper Adapter Services
            createAdapter(VSphereListComputeSnapshotService.class).build(),
            createAdapter(OvfImporterService.class).build(),
            createAdapter(DatacenterEnumeratorService.class).build(),
            createAdapter(VsphereResourceCleanerService.class).build(),
            createFactoryAdapter(VSphereIncrementalEnumerationService.class).build()

    };

    public static final String[] LINKS = StartServicesHelper.getServiceLinks(SERVICES_METADATA);

    /**
     * The link of vSphere configuration registered in {@link PhotonModelAdaptersRegistryService
     * End-point Adapters Registry}.
     */
    public static final String CONFIG_LINK = UriUtils.buildUriPath(
            PhotonModelAdaptersRegistryService.FACTORY_LINK,
            EndpointType.vsphere.name());

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
                    host,
                    EndpointType.vsphere,
                    LINKS,
                    getPublicAdapters(SERVICES_METADATA));
        } catch (Exception e) {
            host.log(Level.WARNING, "Exception staring vSphere adapters: %s",
                    Utils.toString(e));
        }
    }

    /**
     * API to define the list of adapter Uris to be excluded from swagger documentation generation.
     * The service SELF_LINK need to be specified here.
     * @return list of self links whose swagger generation needs to be excluded.
     */
    public static List<String> swaggerExcludedPrefixes() {
        return Arrays.asList(new String[] { VSphereIncrementalEnumerationService.FACTORY_LINK });
    }
}
