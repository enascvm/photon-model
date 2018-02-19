/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.model.adapters.registry;

import static com.vmware.photon.controller.model.util.StartServicesHelper.ServiceMetadata.factoryService;
import static com.vmware.photon.controller.model.util.StartServicesHelper.ServiceMetadata.service;

import java.util.function.Consumer;

import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationService;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecFactoryService;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService;
import com.vmware.photon.controller.model.util.StartServicesHelper;
import com.vmware.photon.controller.model.util.StartServicesHelper.ServiceMetadata;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;

/**
 * Helper class to start Photon model adapter registry related services
 */
public class PhotonModelAdaptersRegistryAdapters {

    public static final ServiceMetadata[] SERVICES_METADATA = {

            factoryService(PhotonModelAdaptersRegistryService.class,
                    PhotonModelAdaptersRegistryFactoryService::new),

            factoryService(ResourceOperationSpecService.class,
                    ResourceOperationSpecFactoryService::new),

            service(ResourceOperationService.class)
                    .requirePrivileged(true),

            service(PhotonModelAdaptersConfigAccessService.class)
                    .requirePrivileged(true)
    };

    public static final String[] LINKS = StartServicesHelper.getServiceLinks(SERVICES_METADATA);

    /**
     * Used mostly by tests.
     */
    public static void startServices(ServiceHost host) {

        StartServicesHelper.startServices(host, SERVICES_METADATA);
    }

    /**
     * Use this method by specific Xenon hosts, such a Symphony and Provisioning.
     */
    public static void startServices(
            ServiceHost host,
            Consumer<Class<? extends Service>> addPrivilegedService) {

        StartServicesHelper.startServices(host, addPrivilegedService, SERVICES_METADATA);
    }

}
