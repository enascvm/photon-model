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

package com.vmware.photon.controller.discovery.onboarding.user;

import static com.vmware.photon.controller.discovery.onboarding.OnboardingUtils.patchUserService;
import static com.vmware.xenon.services.common.ServiceUriPaths.CORE_AUTHZ_SYSTEM_USER;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.function.Consumer;

import com.vmware.photon.controller.discovery.common.PhotonControllerCloudAccountUtils;
import com.vmware.photon.controller.discovery.common.services.OrganizationService;
import com.vmware.photon.controller.discovery.common.services.UserService;
import com.vmware.photon.controller.discovery.common.services.UserService.UserState;
import com.vmware.photon.controller.discovery.onboarding.OnboardingUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.RequestRouter.Route;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocument.Documentation;
import com.vmware.xenon.common.ServiceDocument.UsageOption;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceStateCollectionUpdateRequest;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.GuestUserService;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.UserGroupService;

/**
 * Service to update user info and associate users with organizations,
 *  projects and environments
 */
public class UserUpdateService extends StatelessService {

    public static final String SELF_LINK = UriPaths.PROVISIONING_USER_SERVICE + "/update";

    /**
     * Data object for updating user info
     */
    public static class UserUpdateRequest {
        @Documentation(description = "User email address")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String email;
        @Documentation(description = "Flag to indicate if the user should be an admin on the entity")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public boolean isAdmin;
        @Documentation(description = "The entity to associate the user with. This can be an organization, project or environment")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String entityLink;
        @Documentation(description = "Update action to perform - Can be one of ADD_USER or REMOVE_USER")
        public UserUpdateAction action = UserUpdateAction.ADD_USER;
    }

    public static enum UserUpdateAction {
        ADD_USER, REMOVE_USER;
    }

    @Override
    public void handleRequest(Operation op) {
        switch (op.getAction()) {
        case POST:
            handlePost(op);
            break;
        default:
            super.handleRequest(op);
        }
    }

    @Override
    public void authorizeRequest(Operation op) {
        if (op.getAction().equals(Action.POST)) {
            if (op.getAuthorizationContext() != null &&
                    !op.getAuthorizationContext().getClaims().getSubject()
                            .equals(GuestUserService.SELF_LINK)) {
                op.complete();
                return;
            }
        }
        op.fail(Operation.STATUS_CODE_FORBIDDEN);
    }

    @Override
    public void handlePost(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalStateException("No body specified"));
            return;
        }
        UserUpdateRequest userData = op.getBody(UserUpdateRequest.class);
        Consumer<Operation> onSuccess = (successOp) -> {
            updateUserService(op, userData);
        };
        Consumer<Throwable> onFailure = (failureEx) -> {
            op.fail(Operation.STATUS_CODE_FORBIDDEN);
        };
        checkIfAuthorized(op, this, userData, true,
                onSuccess, onFailure);

    }

    private void checkIfAuthorized(Operation op, Service service, UserUpdateRequest userData, boolean checkForAdmin,
            Consumer<Operation> onSuccess, Consumer<Throwable> onFailure) {
        String userLink = op.getAuthorizationContext().getClaims().getSubject();

        // Allow the system-user to have full access to adding or removing users.
        if (userLink.equals(CORE_AUTHZ_SYSTEM_USER)) {
            onSuccess.accept(op);
            return;
        }

        if (userData.action.equals(UserUpdateAction.ADD_USER)) {
            OnboardingUtils.checkIfUserIsValid(this, userLink, userData.entityLink, true,
                    onSuccess, onFailure);
        } else {
            // check the operation is for self, then no validity checks are needed as user can remove self,
            // else check to see if the user is an admin
            if (userLink.equals(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                    PhotonControllerCloudAccountUtils.computeHashWithSHA256(userData.email)))) {
                onSuccess.accept(op);
            } else {
                OnboardingUtils.checkIfUserIsValid(this, userLink, userData.entityLink, true,
                        onSuccess, onFailure);
            }
        }
    }

    private void processOrg(Operation op, UserUpdateRequest userData) {
        Query q = Query.Builder.create().addCollectionItemClause(
                ServiceDocumentDescription.FIELD_NAME_TENANT_LINKS, userData.entityLink).build();
        QueryTask qTask = QueryTask.Builder.createDirectTask().setQuery(q).build();
        String userLink = UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(userData.email));
        sendRequest(Operation.createPost(this, ServiceUriPaths.CORE_LOCAL_QUERY_TASKS)
                .setBody(qTask)
                .setConnectionSharing(true)
                .setCompletion((queryOp, queryEx) -> {
                    if (queryEx != null) {
                        op.fail(queryEx);
                        return;
                    }
                    QueryTask rsp = queryOp.getBody(QueryTask.class);
                    Collection<Object> userGroupLinks = new HashSet<>();
                    for (String entityLink: rsp.results.documentLinks) {
                        userGroupLinks.add(UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                                OnboardingUtils.buildAuthzArtifactLink(UriUtils
                                        .getLastPathSegment(entityLink), true)));
                        userGroupLinks.add(UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                                OnboardingUtils.buildAuthzArtifactLink(UriUtils
                                        .getLastPathSegment(entityLink), false)));
                    }
                    // add entries for the org itself
                    userGroupLinks.add(UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                            OnboardingUtils.buildAuthzArtifactLink(UriUtils
                                    .getLastPathSegment(userData.entityLink), true)));
                    userGroupLinks.add(UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                            OnboardingUtils.buildAuthzArtifactLink(UriUtils
                                    .getLastPathSegment(userData.entityLink), false)));
                    ServiceStateCollectionUpdateRequest updateRequest = ServiceStateCollectionUpdateRequest.create
                            (null, Collections.singletonMap(UserState.FIELD_NAME_USER_GROUP_LINKS, userGroupLinks));
                    patchUserService(this, userLink, updateRequest, (o, t) -> {
                        if (t != null) {
                            op.fail(t);
                            return;
                        }
                        op.complete();
                    });
                }));
    }

    private void updateUserService(Operation op, UserUpdateRequest userData) {
        String userLink = UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(userData.email));
        switch (userData.action)  {
        case ADD_USER:
            UserState userState = new UserState();
            userState.userGroupLinks = new HashSet<String>();
            if (userData.isAdmin) {
                userState.userGroupLinks.add(UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                        OnboardingUtils.buildAuthzArtifactLink(UriUtils
                                .getLastPathSegment(userData.entityLink), true)));
            }
            userState.userGroupLinks.add(UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                    OnboardingUtils.buildAuthzArtifactLink(UriUtils
                            .getLastPathSegment(userData.entityLink), false)));
            // patch a userState with the links set for this case. If we pass in a generic
            // collection object using a ServiceStateCollectionUpdateRequest, the type
            // information is lost on a multi-node env whent he PODO is sent over the wire
            // causing the assignment to fail
            patchUserService(this, userLink, userState, (o, t) -> {
                if (t != null) {
                    op.fail(t);
                    return;
                }
                op.complete();
            });
            break;
        case REMOVE_USER:
            if (userData.entityLink.startsWith(OrganizationService.FACTORY_LINK)) {
                processOrg(op, userData);
            } else {
                Collection<Object> userGroupLinks = new HashSet<>();
                ServiceStateCollectionUpdateRequest updateRequest = null;
                userGroupLinks.add(UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                        OnboardingUtils.buildAuthzArtifactLink(UriUtils
                                .getLastPathSegment(userData.entityLink), true)));
                userGroupLinks.add(UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                        OnboardingUtils.buildAuthzArtifactLink(UriUtils
                                .getLastPathSegment(userData.entityLink), false)));
                updateRequest = ServiceStateCollectionUpdateRequest.create
                        (null, Collections.singletonMap(UserState.FIELD_NAME_USER_GROUP_LINKS, userGroupLinks));
                patchUserService(this, userLink, updateRequest, (o, t) -> {
                    if (t != null) {
                        op.fail(t);
                        return;
                    }
                    op.complete();
                });
            }
            break;
        default:
            break;
        }
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument d = super.getDocumentTemplate();
        d.documentDescription.serviceRequestRoutes = new HashMap<>();

        Route route = new Route();
        route.action = Action.POST;
        route.description = "Updates user profile information";
        route.requestType = UserUpdateRequest.class;

        d.documentDescription.serviceRequestRoutes
                .put(route.action, Collections.singletonList(route));

        return d;
    }
}
