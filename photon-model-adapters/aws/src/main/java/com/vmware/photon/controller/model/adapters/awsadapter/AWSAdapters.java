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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;

import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSEnumerationAdapterService;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSImageEnumerationAdapterService;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryService;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryService.PhotonModelAdapterConfig;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.adapters.util.EndpointAdapterUtils;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Helper class that starts provisioning adapters
 */
public class AWSAdapters {

    public static final String[] LINKS = {
            AWSInstanceService.SELF_LINK,
            AWSNetworkService.SELF_LINK,
            AWSStatsService.SELF_LINK,
            AWSCostStatsService.SELF_LINK,
            AWSEnumerationAdapterService.SELF_LINK,
            AWSImageEnumerationAdapterService.SELF_LINK,
            AWSEndpointAdapterService.SELF_LINK,
            AWSPowerService.SELF_LINK,
            AWSFirewallService.SELF_LINK };

    public static String CONFIG_LINK = UriUtils
            .buildUriPath(PhotonModelAdaptersRegistryService.FACTORY_LINK, EndpointType.aws.name());

    public static void startServices(ServiceHost host) throws Throwable {

        try {
            host.startService(new AWSInstanceService());
            host.startService(new AWSNetworkService());
            host.startService(new AWSStatsService());
            host.startService(new AWSCostStatsService());
            host.startService(new AWSEnumerationAdapterService());
            host.startService(new AWSImageEnumerationAdapterService());
            host.startService(new AWSEndpointAdapterService());
            host.startService(new AWSPowerService());
            host.startService(new AWSFirewallService());

            registerEndpointAdapters(host);

        } catch (Exception e) {
            host.log(Level.WARNING, "Exception starting AWS adapters: %s",
                    Utils.toString(e));
        }
    }

    /**
     * Enhance AWS end-point config with all public AWS adapters that are be published/registered to
     * End-point Adapters Registry.
     *
     * @see EndpointAdapterUtils#handleEndpointRegistration(ServiceHost, String, Consumer)
     */
    private static void registerEndpointAdapters(ServiceHost host) {

        // Count all adapters - both FAILED and STARTED
        AtomicInteger awsAdaptersCountDown = new AtomicInteger(AWSAdapters.LINKS.length);

        // Keep started adapters only...
        Map<String, String> startedAwsAdapters = new ConcurrentHashMap<>();

        host.registerForServiceAvailability((op, ex) -> {

            if (ex != null) {
                String servicePath = op.getUri().getPath();
                host.log(Level.WARNING, "Starting AWS adapter [%s]: FAILED - %s",
                        servicePath, Utils.toString(ex));
            } else {
                String servicePath = op.getUri().getPath();
                host.log(Level.FINE, "Starting AWS adapter [%s]: SUCCESS", servicePath);

                if (AWSUriPaths.AWS_ADAPTER_LINK_TYPES.containsKey(servicePath)) {
                    startedAwsAdapters.put(
                            AWSUriPaths.AWS_ADAPTER_LINK_TYPES.get(servicePath).key,
                            AdapterUriUtil.buildAdapterUri(host, servicePath).toString());
                }
            }

            if (awsAdaptersCountDown.decrementAndGet() == 0) {
                // Once ALL Adapters are started register them into End-point Adapters Registry

                host.log(Level.INFO, "Starting %d AWS adapters: SUCCESS",
                        startedAwsAdapters.size());

                Consumer<PhotonModelAdapterConfig> endpointConfigEnhancer = ep -> ep.adapterEndpoints
                        .putAll(startedAwsAdapters);

                EndpointAdapterUtils.handleEndpointRegistration(
                        host, EndpointType.aws.name(), endpointConfigEnhancer);
            }

        }, AWSAdapters.LINKS);
    }

}
