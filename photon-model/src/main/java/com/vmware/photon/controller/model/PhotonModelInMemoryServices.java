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

import com.vmware.photon.controller.model.monitoring.InMemoryResourceMetricService;
import com.vmware.photon.controller.model.util.StartServicesHelper;
import com.vmware.photon.controller.model.util.StartServicesHelper.ServiceMetadata;

import com.vmware.xenon.common.ServiceHost;

/**
 * Helper class that starts all the photon model in memory services
 */
public class PhotonModelInMemoryServices {

    private static final ServiceMetadata[] SERVICES_METADATA = {
            factoryService(InMemoryResourceMetricService.class, InMemoryResourceMetricService::createFactory)
    };

    public static final String[] LINKS = StartServicesHelper.getServiceLinks(SERVICES_METADATA);

    public static void startServices(ServiceHost host) throws Throwable {
        StartServicesHelper.startServices(host, SERVICES_METADATA);
    }
}
