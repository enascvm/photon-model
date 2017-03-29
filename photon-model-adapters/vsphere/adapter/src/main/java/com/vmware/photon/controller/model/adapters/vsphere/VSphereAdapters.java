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

import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryService;
import com.vmware.photon.controller.model.adapters.util.EndpointAdapterUtils;
import com.vmware.photon.controller.model.adapters.vsphere.network.DvsNetworkService;
import com.vmware.photon.controller.model.adapters.vsphere.ovf.OvfImporterService;
import com.vmware.photon.controller.model.adapters.vsphere.stats.VSphereAdapterStatsService;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;

/**
 * Facade for starting all vSphere adapters on a host.
 */
public class VSphereAdapters {

    public static final String[] LINKS = {
            VSphereAdapterInstanceService.SELF_LINK,
            VSphereAdapterPowerService.SELF_LINK,
            VSphereAdapterSnapshotService.SELF_LINK,
            VSphereAdapterResourceEnumerationService.SELF_LINK,
            VSphereAdapterStatsService.SELF_LINK,
            OvfImporterService.SELF_LINK,
            DatacenterEnumeratorService.SELF_LINK,
            VSphereEndpointAdapterService.SELF_LINK,
            DvsNetworkService.SELF_LINK,
            VSphereAdapterImageEnumerationService.SELF_LINK
    };

    /**
     * The link of vSphere configuration registered in {@link PhotonModelAdaptersRegistryService
     * End-point Adapters Registry}.
     */
    public static String CONFIG_LINK = UriUtils.buildUriPath(
            PhotonModelAdaptersRegistryService.FACTORY_LINK,
            EndpointType.vsphere.name());

    public static void startServices(ServiceHost host) throws Throwable {
        host.startService(new VSphereAdapterInstanceService());
        host.startService(new VSphereAdapterPowerService());
        host.startService(new VSphereAdapterSnapshotService());
        host.startService(new VSphereAdapterResourceEnumerationService());
        host.startService(new VSphereAdapterStatsService());
        host.startService(new OvfImporterService());
        host.startService(new DatacenterEnumeratorService());
        host.startService(new VSphereEndpointAdapterService());
        host.startService(new DvsNetworkService());
        host.startService(new VSphereAdapterImageEnumerationService());

        EndpointAdapterUtils.registerEndpointAdapters(
                host,
                EndpointType.vsphere.name(),
                LINKS,
                VSphereUriPaths.VSPHERE_ADAPTER_LINK_TYPES);
    }
}
