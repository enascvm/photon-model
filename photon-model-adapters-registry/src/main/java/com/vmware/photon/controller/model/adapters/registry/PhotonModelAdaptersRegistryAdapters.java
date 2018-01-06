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

import java.util.logging.Level;

import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationService;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecFactoryService;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService;
import com.vmware.photon.controller.model.util.StartServicesHelper;
import com.vmware.photon.controller.model.util.StartServicesHelper.ServiceMetadata;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.Utils;

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
    };

    public static final String[] LINKS = StartServicesHelper.getServiceLinks(SERVICES_METADATA);

    public static void startServices(ServiceHost host) {
        startServices(host, false);
    }

    public static void startServices(ServiceHost host, boolean isSynchronousStart) {
        try {
            if (isSynchronousStart) {
                StartServicesHelper.startServicesSynchronously(host, SERVICES_METADATA);
            } else {
                StartServicesHelper.startServices(host, SERVICES_METADATA);
            }
        } catch (Exception e) {
            host.log(Level.WARNING, "Error on start adapter registry related services. %s",
                    Utils.toString(e));
            throw e;
        }
    }
}
