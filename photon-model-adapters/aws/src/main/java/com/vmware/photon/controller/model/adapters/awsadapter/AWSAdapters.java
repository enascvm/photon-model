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

import java.util.logging.Level;

import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSEnumerationAdapterService;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSImageEnumerationAdapterService;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryService;
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
            AWSSubnetService.SELF_LINK,
            AWSLoadBalancerService.SELF_LINK,
            AWSStatsService.SELF_LINK,
            AWSCostStatsService.SELF_LINK,
            AWSReservedInstancePlanService.SELF_LINK,
            AWSEnumerationAdapterService.SELF_LINK,
            AWSImageEnumerationAdapterService.SELF_LINK,
            AWSEndpointAdapterService.SELF_LINK,
            AWSPowerService.SELF_LINK,
            AWSFirewallService.SELF_LINK };

    /**
     * The link of AWS configuration registered in {@link PhotonModelAdaptersRegistryService
     * End-point Adapters Registry}.
     */
    public static String CONFIG_LINK = UriUtils.buildUriPath(
            PhotonModelAdaptersRegistryService.FACTORY_LINK,
            EndpointType.aws.name());

    public static void startServices(ServiceHost host) throws Throwable {
        try {
            host.startService(new AWSInstanceService());
            host.startService(new AWSNetworkService());
            host.startService(new AWSSubnetService());
            host.startService(new AWSLoadBalancerService());
            host.startService(new AWSStatsService());
            host.startService(new AWSCostStatsService());
            host.startService(new AWSReservedInstancePlanService());
            host.startService(new AWSEnumerationAdapterService());
            host.startService(new AWSImageEnumerationAdapterService());
            host.startService(new AWSEndpointAdapterService());
            host.startService(new AWSPowerService());
            host.startService(new AWSFirewallService());

            EndpointAdapterUtils.registerEndpointAdapters(
                    host, EndpointType.aws, LINKS, AWSUriPaths.AWS_ADAPTER_LINK_TYPES);

        } catch (Exception e) {
            host.log(Level.WARNING, "Exception starting AWS adapters: %s",
                    Utils.toString(e));
        }
    }

}
