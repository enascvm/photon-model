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

package com.vmware.photon.controller.discovery.onboarding.organization;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import com.vmware.photon.controller.discovery.common.PhotonControllerCloudAccountUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.ResourceGroupService;

public class StatelessServiceAccessSetupService extends StatelessService {

    public static String SELF_LINK = UriPaths.PROVISIONING_STATELESS_SERVICE_ACCESS_SETUP_SERVICE;
    public static final String STATELESS_SERVICES_FOR_USER_RESOURCE_GROUP = "stateless-services-for-user";

    private Consumer<Operation> resourceGroupHandler;

    public StatelessServiceAccessSetupService(Consumer<Operation> resourceGroupHandler) {
        super();
        this.resourceGroupHandler = resourceGroupHandler;
    }

    @Override
    public void handleStart(Operation op) {
        Set<URI> factoryUris = new HashSet<>();
        factoryUris.add(UriUtils.buildUri(getHost(), ResourceGroupService.FACTORY_LINK));
        PhotonControllerCloudAccountUtils.checkFactoryAvailability(getHost(), op, factoryUris,
                this.resourceGroupHandler);
    }
}
