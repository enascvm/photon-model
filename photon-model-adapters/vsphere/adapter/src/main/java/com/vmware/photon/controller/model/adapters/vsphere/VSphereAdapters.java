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

import static com.vmware.photon.controller.model.util.StartServicesHelper.ServiceMetadata.service;

import java.util.logging.Level;

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
            service(VSphereAdapterInstanceService.class),
            service(VSphereAdapterPowerService.class),
            service(VSphereAdapterSnapshotService.class),
            service(VSphereListComputeSnapshotService.class),
            service(VSphereAdapterResourceEnumerationService.class),
            service(VSphereAdapterStatsService.class),
            service(OvfImporterService.class),
            service(DatacenterEnumeratorService.class),
            service(VsphereResourceCleanerService.class),
            service(VSphereEndpointAdapterService.class),
            service(DvsNetworkService.class),
            service(VSphereAdapterImageEnumerationService.class),
            service(VSphereAdapterD2PowerOpsService.class),
            service(VSphereAdapterResizeComputeService.class),
            service(VSphereDiskService.class),
            service(VSphereComputeDiskManagementService.class),
            service(VSphereRegionEnumerationAdapterService.class)
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
                    VSphereUriPaths.VSPHERE_ADAPTER_LINK_TYPES);
        } catch (Exception e) {
            host.log(Level.WARNING, "Exception staring vSphere adapters: %s",
                    Utils.toString(e));
        }
    }
}
