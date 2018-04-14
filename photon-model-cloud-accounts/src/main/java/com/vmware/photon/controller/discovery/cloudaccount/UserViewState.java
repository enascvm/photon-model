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

import static com.vmware.photon.controller.discovery.common.services.UserService.UserState.FIRST_NAME_PROPERTY_NAME;
import static com.vmware.photon.controller.discovery.common.services.UserService.UserState.LAST_NAME_PROPERTY_NAME;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_CREATEDBY_EMAIL;

import java.util.Map;
import java.util.Set;

import com.vmware.photon.controller.discovery.cloudaccount.users.UsersApiService;
import com.vmware.photon.controller.discovery.common.services.UserService.UserState;
import com.vmware.xenon.common.ServiceDocument.Documentation;
import com.vmware.xenon.common.UriUtils;

/**
 * The API friendly representation of the user state object.
 */
public class UserViewState {
    public static final String FIELD_NAME_EMAIL = "email";
    public static final String FIELD_NAME_ROLES = "roles";

    private UserViewState() {}

    @Documentation(description = "First name of the user")
    public String firstName;

    @Documentation(description = "Last name of the user")
    public String lastName;

    @Documentation(description = "Email ID of user")
    public String email;

    @Documentation(description = "Set of user roles")
    public Set<Role> roles;

    @Documentation(description = "The self link for the user")
    public String documentSelfLink;

    public static UserViewState createUserView(Map<String, String> customProperties) {
        UserViewState userViewState = new UserViewState();
        if (customProperties != null && customProperties.containsKey(ENDPOINT_CUSTOM_PROPERTY_NAME_CREATEDBY_EMAIL)) {
            userViewState.email = customProperties.get(ENDPOINT_CUSTOM_PROPERTY_NAME_CREATEDBY_EMAIL);
        }
        return userViewState;
    }

    /**
     * TODO : Move this class to common module
     */
    public static UserViewState createUserView(UserState userState) {
        UserViewState userViewState = new UserViewState();
        userViewState.email = userState.email;
        userViewState.firstName = userState.customProperties.get(FIRST_NAME_PROPERTY_NAME);
        userViewState.lastName = userState.customProperties.get(LAST_NAME_PROPERTY_NAME);
        userViewState.documentSelfLink = UriUtils.buildUriPath(UsersApiService.SELF_LINK,
                UriUtils.getLastPathSegment(userState.documentSelfLink));
        return userViewState;
    }

    /**
     * User roles.
     */
    public enum Role {
        @Documentation(description = "Org owner role")
        ORG_OWNER,

        @Documentation(description = "Org user role")
        ORG_USER,
    }
}
