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

import com.vmware.photon.controller.model.monitoring.ResourceMetricsService;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.UriUtils;

/**
 * Helper class that starts all the photon model provisioning services
 */
public class PhotonModelMetricServices {

    public static final String[] LINKS = {
            ResourceMetricsService.FACTORY_LINK};

    public static void startServices(ServiceHost host) throws Throwable {
        host.startFactory(ResourceMetricsService.class, ResourceMetricsService::createFactory);
        setFactoryToAvailable(host, ResourceMetricsService.FACTORY_LINK);
    }

    /** @see #setFactoryToAvailable(ServiceHost, String, Operation.CompletionHandler) */
    public static void setFactoryToAvailable(ServiceHost host, String factoryPath) {
        setFactoryToAvailable(host, factoryPath, null);
    }

    /**
     * Helper method to explicitly set a factory to be "available". This is usually unnecessary,
     * but currently factories that create {@code ON_DEMAND_LOAD} services are not being set to
     * available... and currently require this work-around.
     *
     * @param host the host
     * @param factoryPath the path of the factory to explicitly set to be available
     * @param handler an optional completion handler
     */
    public static void setFactoryToAvailable(ServiceHost host, String factoryPath, Operation.CompletionHandler handler) {
        ServiceStats.ServiceStat body = new ServiceStats.ServiceStat();
        body.name = Service.STAT_NAME_AVAILABLE;
        body.latestValue = Service.STAT_VALUE_TRUE;

        Operation put = Operation.createPut(UriUtils.buildAvailableUri(host, factoryPath))
                .setBody(body)
                .setCompletion(handler)
                .setReferer(host.getUri());
        host.sendRequest(put);
    }
}
