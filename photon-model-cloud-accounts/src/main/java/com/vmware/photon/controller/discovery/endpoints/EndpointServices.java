/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.discovery.endpoints;

import java.util.function.Consumer;

import com.vmware.photon.controller.discovery.vsphere.VsphereRDCSyncTaskService;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;

/**
 * Helper class that starts endpoint services.
 */
public class EndpointServices {

    /**
     * Starts the endpoint services.
     *
     * @param host                 The host to use to start the services
     * @param addPrivilegedService A consumer to help accept services that require service user
     *                             access.
     */
    public static void startServices(ServiceHost host,
            Consumer<Class<? extends Service>> addPrivilegedService) throws Throwable {
        addPrivilegedService.accept(DataInitializationTaskService.class);
        host.startFactory(DataInitializationTaskService.class,
                DataInitializationTaskService::createFactory);
        addPrivilegedService.accept(EndpointCreationTaskService.class);
        host.startFactory(EndpointCreationTaskService.class,
                EndpointCreationTaskService::createFactory);
        host.startFactory(VsphereRDCSyncTaskService.class,
                VsphereRDCSyncTaskService::createFactory);
        host.startFactory(EndpointDeletionTaskService.class,
                EndpointDeletionTaskService::createFactory);
        host.startFactory(EndpointUpdateTaskService.class,
                EndpointUpdateTaskService::createFactory);
        addPrivilegedService.accept(EndpointValidationTaskService.class);
        addPrivilegedService.accept(EndpointUpdateTaskService.class);
        host.startFactory(EndpointValidationTaskService.class,
                EndpointValidationTaskService::createFactory);
        host.startFactory(AwsEndpointS3ValidationTaskService.class,
                AwsEndpointS3ValidationTaskService::createFactory);
        host.startFactory(AwsCostUsageReportTaskService.class,
                AwsCostUsageReportTaskService::createFactory);

        host.startService(new OptionalAdapterSchedulingService());
        addPrivilegedService.accept(OptionalAdapterSchedulingService.class);
    }

}
