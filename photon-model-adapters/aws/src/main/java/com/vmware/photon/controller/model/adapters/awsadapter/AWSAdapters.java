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

import static com.vmware.photon.controller.model.util.StartServicesHelper.ServiceMetadata.service;

import java.util.logging.Level;

import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSEnumerationAdapterService;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSImageEnumerationAdapterService;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSMissingResourcesEnumerationService;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSRegionEnumerationAdapterService;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryService;
import com.vmware.photon.controller.model.adapters.util.EndpointAdapterUtils;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.util.StartServicesHelper;
import com.vmware.photon.controller.model.util.StartServicesHelper.ServiceMetadata;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Helper class that starts provisioning adapters
 */
public class AWSAdapters {

    private static final ServiceMetadata[] SERVICES_METADATA = {
            service(AWSInstanceService.class),
            service(AWSNetworkService.class),
            service(AWSDiskService.class),
            service(AWSSubnetService.class),
            service(AWSLoadBalancerService.class),
            service(AWSStatsService.class),
            service(AWSCostStatsService.class),
            service(AWSReservedInstancePlanService.class),
            service(AWSEnumerationAdapterService.class),
            service(AWSImageEnumerationAdapterService.class),
            service(AWSInstanceTypeService.class),
            service(AWSEndpointAdapterService.class),
            service(AWSPowerService.class),
            service(AWSSecurityGroupService.class),
            service(AWSMissingResourcesEnumerationService.class),
            service(AWSRebootService.class),
            service(AWSComputeDiskDay2Service.class),
            service(AWSResetService.class),
            service(AWSRegionEnumerationAdapterService.class)
    };

    public static final String[] LINKS = StartServicesHelper.getServiceLinks(SERVICES_METADATA);

    /**
     * The link of AWS configuration registered in {@link PhotonModelAdaptersRegistryService
     * End-point Adapters Registry}.
     */
    public static String CONFIG_LINK = UriUtils.buildUriPath(
            PhotonModelAdaptersRegistryService.FACTORY_LINK,
            EndpointType.aws.name());

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
                    host, EndpointType.aws, LINKS, AWSUriPaths.AWS_ADAPTER_LINK_TYPES);

        } catch (Exception e) {
            host.log(Level.WARNING, "Exception starting AWS adapters: %s",
                    Utils.toString(e));
        }
    }

}
