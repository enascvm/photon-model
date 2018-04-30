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

package com.vmware.photon.controller.discovery.cloudaccount;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Consumer;

import com.vmware.photon.controller.discovery.cloudaccount.users.UserQueryTaskService;
import com.vmware.photon.controller.discovery.cloudaccount.users.UsersApiService;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskFactoryService;

/**
 * Helper class that starts cloud account services.
 */
public class CloudAccountServices {

    /**
     * Starts the Cloud Account services, with the minimal set of required classes to start the
     * {@link CustomQueryPageForwardingService}.
     *
     * @param host                 The host to use to start the services
     * @param addPrivilegedService A consumer to help accept services that require service user
     *                             access.
     */
    public static void startServices(ServiceHost host,
            Consumer<Class<? extends Service>> addPrivilegedService) {
        startServices(host, addPrivilegedService, Collections.unmodifiableCollection(Arrays.asList(
                CloudAccountQueryPageService.class
        )));
    }

    /**
     * Starts the Cloud Account services.
     *
     * @param host                            The host to use to start the services
     * @param addPrivilegedService            A consumer to help accept services that require service user
     *                                        access.
     * @param supportedCustomQueryPageClasses A collection of classes that should utilize the
     *                                        {@link CustomQueryPageForwardingService}.
     */
    public static void startServices(ServiceHost host,
            Consumer<Class<? extends Service>> addPrivilegedService,
            Collection<Class> supportedCustomQueryPageClasses) {

        // The user services
        addPrivilegedService.accept(UserQueryTaskService.class);
        host.startFactory(UserQueryTaskService.class,
                () -> TaskFactoryService.create(UserQueryTaskService.class));
        host.startService(new UsersApiService());

        addPrivilegedService.accept(CloudAccountQueryTaskService.class);
        host.startFactory(CloudAccountQueryTaskService.class,
                CloudAccountQueryTaskService::createFactory);
        addPrivilegedService.accept(CloudAccountApiService.class);
        host.startService(new CloudAccountApiService());
        addPrivilegedService.accept(CloudAccountQueryPageService.class);

        addPrivilegedService.accept(CloudAccountAWSBulkImportTaskService.class);
        host.startFactory(CloudAccountAWSBulkImportTaskService.class,
                () -> TaskFactoryService.create(CloudAccountAWSBulkImportTaskService.class));

        host.startService(
                new CustomQueryPageForwardingService(ServiceUriPaths.DEFAULT_NODE_SELECTOR,
                        supportedCustomQueryPageClasses));

        addPrivilegedService.accept(CloudAccountMaintenanceService.class);
        host.startFactory(CloudAccountMaintenanceService.class,
                CloudAccountMaintenanceService::createFactory);
    }
}