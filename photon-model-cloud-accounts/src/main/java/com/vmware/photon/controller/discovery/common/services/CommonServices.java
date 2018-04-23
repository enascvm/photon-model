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

package com.vmware.photon.controller.discovery.common.services;

import java.util.function.Consumer;

import com.vmware.photon.controller.discovery.common.services.rules.ConfigurationRuleQueryService;
import com.vmware.photon.controller.discovery.common.services.rules.ConfigurationRuleService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;

/**
 * Helper class that starts common services for the Discovery services. Starts up the AuthZ services
 * and the ConfigurationRuleService.
 */
public class CommonServices {

    /**
     * Starts the common services.
     *
     * @param host                 The host to use to start the services
     * @param addPrivilegedService A consumer to help accept services that require service user
     *                             access.
     */
    public static void startServices(ServiceHost host,
            Consumer<Class<? extends Service>> addPrivilegedService) throws Throwable {
        addPrivilegedService.accept(UserService.class);
        host.startFactory(new UserService());
        host.startFactory(new OrganizationService());
        host.startFactory(new ProjectService());
        addPrivilegedService.accept(UserContextQueryService.class);
        host.startService(new UserContextQueryService());
        host.startService(new ResourceEnumerationService());
        addPrivilegedService.accept(ResourceEnumerationService.class);

        host.startService(Operation.createPost(host,
                ConfigurationRuleService.FACTORY_LINK),
                ConfigurationRuleService.createFactory());
        host.startService(new ConfigurationRuleQueryService());
        addPrivilegedService.accept(ConfigurationRuleQueryService.class);
    }

}
