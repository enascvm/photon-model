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

package com.vmware.photon.controller.model.tasks;

import static com.vmware.photon.controller.model.util.StartServicesHelper.ServiceMetadata.factoryService;
import static com.vmware.photon.controller.model.util.StartServicesHelper.ServiceMetadata.service;

import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsAggregationTaskService;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService;
import com.vmware.photon.controller.model.tasks.monitoring.StatsAggregationTaskService;
import com.vmware.photon.controller.model.tasks.monitoring.StatsCollectionTaskService;
import com.vmware.photon.controller.model.util.StartServicesHelper;
import com.vmware.photon.controller.model.util.StartServicesHelper.ServiceMetadata;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.services.common.TaskFactoryService;

/**
 * Helper class that starts all the photon model Task services
 */
public class PhotonModelTaskServices {

    private static final ServiceMetadata[] SERVICES_METADATA = {
            factoryService(SshCommandTaskService.class,
                    SshCommandTaskService::createFactory),
            factoryService(ResourceAllocationTaskService.class,
                    () -> TaskFactoryService.create(ResourceAllocationTaskService.class)),
            factoryService(ResourceEnumerationTaskService.class,
                    ResourceEnumerationTaskService::createFactory),
            factoryService(ImageEnumerationTaskService.class,
                    ImageEnumerationTaskService::createFactory),
            factoryService(ScheduledTaskService.class,
                    () -> TaskFactoryService.create(ScheduledTaskService.class)),
            factoryService(ResourceRemovalTaskService.class,
                    () -> TaskFactoryService.create(ResourceRemovalTaskService.class)),
            factoryService(ResourceIPDeallocationTaskService.class,
                    () -> TaskFactoryService.create(ResourceIPDeallocationTaskService.class)),
            factoryService(ProvisionComputeTaskService.class,
                    () -> TaskFactoryService.create(ProvisionComputeTaskService.class)),
            factoryService(ProvisionNetworkTaskService.class,
                    () -> TaskFactoryService.create(ProvisionNetworkTaskService.class)),
            factoryService(ProvisionDiskTaskService.class,
                    () -> TaskFactoryService.create(ProvisionDiskTaskService.class)),
            factoryService(IPAddressAllocationTaskService.class,
                    () -> TaskFactoryService.create(IPAddressAllocationTaskService.class)),
            factoryService(ProvisionSubnetTaskService.class,
                    () -> TaskFactoryService.create(ProvisionSubnetTaskService.class)),
            factoryService(ProvisionLoadBalancerTaskService.class,
                    () -> TaskFactoryService.create(ProvisionLoadBalancerTaskService.class)),
            factoryService(SnapshotTaskService.class,
                    () -> TaskFactoryService.create(SnapshotTaskService.class)),
            factoryService(ProvisionSecurityGroupTaskService.class,
                    () -> TaskFactoryService.create(ProvisionSecurityGroupTaskService.class)),
            factoryService(EndpointAllocationTaskService.class,
                    () -> TaskFactoryService.create(EndpointAllocationTaskService.class)),
            factoryService(EndpointRemovalTaskService.class,
                    () -> TaskFactoryService.create(EndpointRemovalTaskService.class)),
            factoryService(SingleResourceStatsAggregationTaskService.class,
                    SingleResourceStatsAggregationTaskService::createFactory),
            factoryService(StatsAggregationTaskService.class,
                    StatsAggregationTaskService::createFactory),
            factoryService(SingleResourceStatsCollectionTaskService.class,
                    SingleResourceStatsCollectionTaskService::createFactory),
            factoryService(StatsCollectionTaskService.class,
                    StatsCollectionTaskService::createFactory),
            factoryService(SubTaskService.class,
                    () -> TaskFactoryService.create(SubTaskService.class)),
            factoryService(NicSecurityGroupsTaskService.class,
                    () -> TaskFactoryService
                            .create(NicSecurityGroupsTaskService.class)),
            factoryService(TagGroomerTaskService.class,
                    TagGroomerTaskService::createFactory),

            service(IPAddressReleaseTaskService.class)
    };

    public static final String[] LINKS = StartServicesHelper.getServiceLinks(SERVICES_METADATA);

    public static void startServices(ServiceHost host) throws Throwable {

        StartServicesHelper.startServices(host, SERVICES_METADATA);
    }
}
