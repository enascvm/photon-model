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

import static com.vmware.photon.controller.discovery.onboarding.OnboardingUtils.buildUserIdFromClientId;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.vmware.photon.controller.discovery.common.PhotonControllerCloudAccountUtils;
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
import com.vmware.xenon.services.common.UserService;
import com.vmware.xenon.services.common.UserService.UserState;

/**
 * Service to setup client credentials user access.
 */
public class ClientCredentialsUserSetupService extends StatelessService {
    public static final String SELF_LINK = UriPaths.PROVISIONING_CLIENT_CREDS_USER_SETUP_SERVICE;

    public static final String USER_GROUP_SUFFIX = "-discovery-client-user-group";
    public static final String RESOURCE_GROUP_SUFFIX = "-discovery-client-resource-group";
    public static final String ROLE_SUFFIX = "-discovery-client-role";

    public static class ClientCredentialsUserSetupRequest {
        public Set<String> clientIds;
    }

    @Override
    public void handleStart(Operation op) {
        List<String> factories = new ArrayList<>();

        factories.add(ResourceGroupService.FACTORY_LINK);
        factories.add(RoleService.FACTORY_LINK);
        factories.add(UserGroupService.FACTORY_LINK);
        factories.add(UserService.FACTORY_LINK);
        factories.add(AuthCredentialsService.FACTORY_LINK);

        Set<URI> factoryUris = Collections.synchronizedSet(new HashSet<URI>());

        factoryUris.addAll(factories.stream()
                .map(factory -> UriUtils.buildUri(getHost(), factory))
                .collect(Collectors.toList()));

        Consumer<Operation> onSuccess = (resultOp) -> {
            logInfo("Client Credentials Setup Service is started");
            op.complete();
        };

        PhotonControllerCloudAccountUtils.checkFactoryAvailability(getHost(), op, factoryUris, onSuccess);
    }

    @Override
    public void handlePost(Operation post) {
        if (!post.hasBody()) {
            throw new IllegalArgumentException("body is required");
        }

        ClientCredentialsUserSetupRequest request = post
                .getBody(ClientCredentialsUserSetupRequest.class);

        if (request.clientIds == null || request.clientIds.isEmpty()) {
            throw new IllegalArgumentException("At least one client id needs to specified");
        }

        AtomicInteger clientIdCount = new AtomicInteger(request.clientIds.size());
        for (String clientId : request.clientIds) {
            String userId = buildUserIdFromClientId(clientId);

            setupClientCredentialsUser(post, clientId, userId, clientIdCount);
        }
    }

    private void setupClientCredentialsUser(Operation op, String clientId, String userId, AtomicInteger clientIdCount) {
        setAuthorizationContext(op, getSystemAuthorizationContext());

        AuthorizationSetupHelper.AuthSetupCompletion authCompletion = (ex) -> {
            if (ex != null) {
                logWarning("Error setting up client id %s: %s", clientId, ex.getMessage());
                clientIdCount.decrementAndGet();
                op.fail(ex);
                return;
            }
            logInfo("Setup complete for user %s with clientId %s", userId, clientId);
            if (clientIdCount.decrementAndGet() == 0) {
                op.complete();
            }
        };

        AuthorizationSetupHelper.create()
                .setHost(this.getHost())
                .setUserEmail(userId)
                .setUserPassword(UUID.randomUUID().toString())
                .setUserSelfLink(PhotonControllerCloudAccountUtils.computeHashWithSHA256(userId))
                .setUserGroupName(buildUserGroupNameFromClientId(clientId))
                .setUserGroupQuery(Query.Builder.create()
                        .addCollectionItemClause(UserState.FIELD_NAME_EMAIL, userId)
                        .build())
                .setResourceGroupName(buildResourceGroupNameFromClientId(clientId))
                .setResourceQuery(Query.Builder.create()
                        .addFieldClause(
                                ServiceDocument.FIELD_NAME_SELF_LINK, "*", MatchType.WILDCARD)
                        .build())
                .setRoleName(buildRoleNameFromClientId(clientId))
                .setCompletion(authCompletion)
                .start();
    }

    public static String buildUserGroupNameFromClientId(String clientId) {
        return clientId + USER_GROUP_SUFFIX;
    }

    public static String buildResourceGroupNameFromClientId(String clientId) {
        return clientId + RESOURCE_GROUP_SUFFIX;
    }

    public static String buildRoleNameFromClientId(String clientId) {
        return clientId + ROLE_SUFFIX;
    }
}
