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

package com.vmware.photon.controller.discovery.onboarding;

import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.AUTOMATION_USER_EMAIL;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.AUTOMATION_USER_GROUP_NAME;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.AUTOMATION_USER_RESOURCE_GROUP_NAME;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.AUTOMATION_USER_ROLE_NAME;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.vmware.photon.controller.discovery.common.PhotonControllerCloudAccountUtils;
import com.vmware.photon.controller.discovery.common.services.UserService;
import com.vmware.photon.controller.discovery.common.services.UserService.UserState;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.xenon.common.AuthorizationSetupHelper;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;
import com.vmware.xenon.services.common.ResourceGroupService;
import com.vmware.xenon.services.common.RoleService;
import com.vmware.xenon.services.common.UserGroupService;

/**
 * Service to setup automation user access.
 */
public class AutomationUserSetupService extends StatelessService {
    public static String SELF_LINK = UriPaths.PROVISIONING_SYSTEM_USER_SETUP_SERVICE;

    @Override
    public void handleStart(Operation op) {
        List<String> factories = new ArrayList<>();
        factories.add(ResourceGroupService.FACTORY_LINK);
        factories.add(RoleService.FACTORY_LINK);
        factories.add(UserGroupService.FACTORY_LINK);
        factories.add(UserService.FACTORY_LINK);
        factories.add(AuthCredentialsService.FACTORY_LINK);
        Set<URI> factoryUris = Collections.synchronizedSet(new HashSet<URI>());
        factoryUris.addAll(
                factories.stream().map(
                        factory -> UriUtils.buildUri(getHost(), factory))
                        .collect(Collectors.toList()));
        Consumer<Operation> onSuccess = (resultOp) -> {
            setupAutomationUser(op);
        };
        PhotonControllerCloudAccountUtils.checkFactoryAvailability(getHost(), op, factoryUris, onSuccess);
    }

    /**
     * Create AuthZ roles for the automation user.
     */
    private void setupAutomationUser(Operation op) {
        setAuthorizationContext(op, getSystemAuthorizationContext());

        AuthorizationSetupHelper.AuthSetupCompletion authCompletion = (ex) -> {
            if (ex != null) {
                logWarning("Error setting up symphony system role: %s", ex.getMessage());
                op.fail(ex);
                return;
            }
            op.complete();
        };

        AuthorizationSetupHelper.create()
                .setHost(this.getHost())
                .setUserEmail(AUTOMATION_USER_EMAIL)
                .setUserPassword(UUID.randomUUID().toString())
                .setUserSelfLink(PhotonControllerCloudAccountUtils.computeHashWithSHA256(AUTOMATION_USER_EMAIL))
                .setCredentialsSelfLink(PhotonControllerCloudAccountUtils.computeHashWithSHA256(AUTOMATION_USER_EMAIL))
                .setUserGroupName(AUTOMATION_USER_GROUP_NAME)
                .setUserGroupQuery(Query.Builder.create()
                        .addCollectionItemClause(UserState.FIELD_NAME_EMAIL,
                                AUTOMATION_USER_EMAIL)
                        .build())
                .setResourceGroupName(AUTOMATION_USER_RESOURCE_GROUP_NAME)
                .setResourceQuery(Query.Builder.create()
                        .addFieldClause(ServiceDocument.FIELD_NAME_KIND, "*",
                                MatchType.WILDCARD).build())
                .setRoleName(AUTOMATION_USER_ROLE_NAME)
                .setCompletion(authCompletion)
                .start();
    }
}
