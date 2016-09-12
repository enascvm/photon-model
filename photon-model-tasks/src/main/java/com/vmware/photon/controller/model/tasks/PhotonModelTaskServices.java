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

import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsAggregationTaskService;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService;
import com.vmware.photon.controller.model.tasks.monitoring.StatsAggregationTaskService;
import com.vmware.photon.controller.model.tasks.monitoring.StatsCollectionTaskService;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.services.common.TaskFactoryService;

/**
 * Helper class that starts all the photon model Task services
 */
public class PhotonModelTaskServices {

    public static final String[] LINKS = {
            SshCommandTaskService.FACTORY_LINK,
            ResourceAllocationTaskService.FACTORY_LINK,
            ResourceEnumerationTaskService.FACTORY_LINK,
            ScheduledTaskService.FACTORY_LINK,
            ResourceRemovalTaskService.FACTORY_LINK,
            ProvisionComputeTaskService.FACTORY_LINK,
            ProvisionNetworkTaskService.FACTORY_LINK,
            SnapshotTaskService.FACTORY_LINK,
            ProvisionFirewallTaskService.FACTORY_LINK,
            StatsCollectionTaskService.FACTORY_LINK,
            SingleResourceStatsCollectionTaskService.FACTORY_LINK,
            StatsAggregationTaskService.FACTORY_LINK,
            EndpointAllocationTaskService.FACTORY_LINK,
            SingleResourceStatsAggregationTaskService.FACTORY_LINK };

    public static void startServices(ServiceHost host) throws Throwable {

        host.startService(Operation.createPost(host,
                SshCommandTaskService.FACTORY_LINK),
                SshCommandTaskService.createFactory());
        host.startFactory(ResourceAllocationTaskService.class,
                () -> TaskFactoryService.create(ResourceAllocationTaskService.class));
        host.startFactory(ResourceEnumerationTaskService.class,
                () -> TaskFactoryService.create(ResourceEnumerationTaskService.class));
        host.startFactory(ScheduledTaskService.class,
                () -> TaskFactoryService.create(ScheduledTaskService.class));
        host.startFactory(ResourceRemovalTaskService.class,
                () -> TaskFactoryService.create(ResourceRemovalTaskService.class));
        host.startFactory(ProvisionComputeTaskService.class,
                () -> TaskFactoryService.create(ProvisionComputeTaskService.class));
        host.startFactory(ProvisionNetworkTaskService.class,
                () -> TaskFactoryService.create(ProvisionNetworkTaskService.class));
        host.startFactory(SnapshotTaskService.class,
                () -> TaskFactoryService.create(SnapshotTaskService.class));
        host.startFactory(ProvisionFirewallTaskService.class,
                () -> TaskFactoryService.create(ProvisionFirewallTaskService.class));
        host.startFactory(StatsCollectionTaskService.class,
                () -> TaskFactoryService.create(StatsCollectionTaskService.class));
        host.startFactory(StatsAggregationTaskService.class,
                () -> TaskFactoryService.create(StatsAggregationTaskService.class));
        host.startFactory(SingleResourceStatsCollectionTaskService.class,
                () -> TaskFactoryService.create(SingleResourceStatsCollectionTaskService.class));
        host.startFactory(EndpointAllocationTaskService.class,
                () -> TaskFactoryService.create(EndpointAllocationTaskService.class));
        host.startFactory(EndpointRemovalTaskService.class,
                () -> TaskFactoryService.create(EndpointRemovalTaskService.class));
        host.startFactory(SingleResourceStatsAggregationTaskService.class,
                () -> TaskFactoryService.create(SingleResourceStatsAggregationTaskService.class));
    }
}