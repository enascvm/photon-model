/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.model.adapters.azure.model.permission;

import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AUTHORIZATION_NAMESPACE;

import java.util.Arrays;
import java.util.List;

public class Permission {

    private static final String ALL_PERMISSIONS = "*";
    private static final String READ_PERMISSIONS = "*/Read";

    private static final String AUTHORIZATION_DELETE_PERMISSIONS = AUTHORIZATION_NAMESPACE +
            "/*/Delete";
    private static final String AUTHORIZATION_WRITE_PERMISSIONS = AUTHORIZATION_NAMESPACE +
            "/*/Write";
    private static final String AUTHORIZATION_ELEVATE_ACCESS_PERMISSIONS =
            AUTHORIZATION_NAMESPACE + "/elevateAccess/Action";

    public List<String> actions;
    public List<String> notActions;

    // Role permissions as described in
    // https://docs.microsoft.com/en-us/azure/active-directory/role-based-access-built-in-roles
    public boolean isOwner() {
        return this.actions != null &&
                this.notActions != null &&
                this.notActions.isEmpty() &&
                this.actions.size() == 1 &&
                this.actions.stream().anyMatch(ALL_PERMISSIONS::equals);
    }

    public boolean isContributor() {
        return this.actions != null &&
                this.notActions != null &&
                this.actions.size() == 1 &&
                this.notActions.size() == 3 &&
                this.actions.stream().anyMatch(ALL_PERMISSIONS::equals) &&
                this.notActions.containsAll(Arrays.asList(AUTHORIZATION_DELETE_PERMISSIONS,
                        AUTHORIZATION_WRITE_PERMISSIONS, AUTHORIZATION_ELEVATE_ACCESS_PERMISSIONS));
    }

    public boolean isReader() {
        return this.actions != null &&
                this.notActions != null &&
                this.notActions.isEmpty() &&
                this.actions.size() == 1 &&
                this.actions.stream().anyMatch(READ_PERMISSIONS::equalsIgnoreCase);
    }

}
