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

package com.vmware.photon.controller.model;

import static com.vmware.photon.controller.model.util.StartServicesHelper.ServiceMetadata.factoryService;

import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ContentService;
import com.vmware.photon.controller.model.resources.DeploymentService;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.FirewallService;
import com.vmware.photon.controller.model.resources.IPAddressService;
import com.vmware.photon.controller.model.resources.ImageService;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService;
import com.vmware.photon.controller.model.resources.LoadBalancerService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.ResourceDescriptionService;
import com.vmware.photon.controller.model.resources.ResourceGroupService;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.RouterService;
import com.vmware.photon.controller.model.resources.SecurityGroupService;
import com.vmware.photon.controller.model.resources.SessionFactoryService;
import com.vmware.photon.controller.model.resources.SessionService;
import com.vmware.photon.controller.model.resources.SnapshotService;
import com.vmware.photon.controller.model.resources.StorageDescriptionService;
import com.vmware.photon.controller.model.resources.SubnetRangeService;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.TagFactoryService;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.util.StartServicesHelper;
import com.vmware.photon.controller.model.util.StartServicesHelper.ServiceMetadata;
import com.vmware.xenon.common.ServiceHost;

/**
 * Helper class that starts all the photon model provisioning services
 */
public class PhotonModelServices {

    public static final ServiceMetadata[] SERVICES_METADATA = {
            factoryService(ComputeDescriptionService.class),
            factoryService(ComputeService.class),
            factoryService(DeploymentService.class),
            factoryService(ResourcePoolService.class),
            factoryService(ResourceDescriptionService.class),
            factoryService(DiskService.class),
            factoryService(SnapshotService.class),
            factoryService(NetworkInterfaceService.class),
            factoryService(NetworkInterfaceDescriptionService.class),
            factoryService(ResourceGroupService.class),
            factoryService(NetworkService.class),
            factoryService(SubnetService.class),
            factoryService(SubnetRangeService.class),
            factoryService(IPAddressService.class),
            factoryService(SecurityGroupService.class),
            factoryService(FirewallService.class),
            factoryService(StorageDescriptionService.class),
            factoryService(EndpointService.class),
            factoryService(ImageService.class),
            factoryService(TagService.class, TagFactoryService::new),
            factoryService(LoadBalancerDescriptionService.class),
            factoryService(LoadBalancerService.class),
            factoryService(RouterService.class),
            factoryService(ContentService.class),
            factoryService(SessionService.class, SessionFactoryService::new)
    };

    public static final String[] LINKS = StartServicesHelper.getServiceLinks(SERVICES_METADATA);

    public static void startServices(ServiceHost host) throws Throwable {
        startServices(host, false);
    }

    public static void startServices(ServiceHost host, boolean isSynchronousStart)
            throws Throwable {

        if (isSynchronousStart) {
            StartServicesHelper.startServicesSynchronously(host, SERVICES_METADATA);
        } else {
            StartServicesHelper.startServices(host, SERVICES_METADATA);
        }
    }
}
