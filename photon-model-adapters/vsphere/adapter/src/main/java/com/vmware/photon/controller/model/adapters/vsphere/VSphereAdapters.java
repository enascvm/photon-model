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

import static com.vmware.photon.controller.model.adapters.util.AdapterServiceMetadata.adapter;
import static com.vmware.photon.controller.model.adapters.util.AdapterServiceMetadata.getPublicAdapters;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

import com.vmware.photon.controller.model.UriPaths.AdapterTypePath;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryService;
import com.vmware.photon.controller.model.adapters.util.EndpointAdapterUtils;
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
            adapter(VSphereAdapterInstanceService.class, AdapterTypePath.INSTANCE_ADAPTER),
            adapter(VSphereAdapterPowerService.class, AdapterTypePath.POWER_ADAPTER),
            adapter(VSphereAdapterSnapshotService.class),
            adapter(VSphereListComputeSnapshotService.class),
            adapter(VSphereAdapterResourceEnumerationService.class, AdapterTypePath.ENUMERATION_ADAPTER),
            ServiceMetadata.factoryService(VSphereIncrementalEnumerationService.class),
            adapter(VSphereAdapterStatsService.class, AdapterTypePath.STATS_ADAPTER),
            adapter(OvfImporterService.class),
            adapter(DatacenterEnumeratorService.class),
            adapter(VsphereResourceCleanerService.class),
            adapter(VSphereEndpointAdapterService.class, AdapterTypePath.ENDPOINT_CONFIG_ADAPTER),
            adapter(DvsNetworkService.class, AdapterTypePath.SUBNET_ADAPTER),
            adapter(VSphereAdapterImageEnumerationService.class, AdapterTypePath.IMAGE_ENUMERATION_ADAPTER),
            adapter(VSphereAdapterD2PowerOpsService.class),
            adapter(VSphereAdapterResizeComputeService.class),
            adapter(VSphereDiskService.class, AdapterTypePath.DISK_ADAPTER),
            adapter(VSphereComputeDiskManagementService.class),
            adapter(VSphereRegionEnumerationAdapterService.class, AdapterTypePath.REGION_ENUMERATION_ADAPTER)
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
     *
     * @return list of self links whose swagger generation needs to be excluded.
     */
    public static List<String> swaggerExcludedPrefixes() {
        return Arrays.asList(new String[]{VSphereIncrementalEnumerationService.FACTORY_LINK});
    }
}
