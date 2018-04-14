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

import static com.vmware.photon.controller.discovery.common.utils.OnboardingUtils.SEPARATOR;
import static com.vmware.photon.controller.model.UriPaths.USER_SERVICE;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.DocumentIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthorizationCacheUtils;
import com.vmware.xenon.services.common.UserGroupService;

public class UserService extends StatefulService {
    public static final String FACTORY_LINK = USER_SERVICE;

    private static final int VERSION_LIMIT = 10000;
    private static final int STATE_SIZE_LIMIT = 1024 * 1024 * 10;

    /**
     * The {@link UserState} represents a single user's identity.
     */
    @ServiceDocument.IndexingParameters(serializedStateSize = STATE_SIZE_LIMIT, versionRetention = VERSION_LIMIT)
    public static class UserState extends ServiceDocument {
        public static final String FIELD_NAME_EMAIL = "email";
        public static final String FIELD_NAME_USER_GROUP_LINKS = "userGroupLinks";
        public static final String FIELD_NAME_CUSTOM_PROPERTIES = "customProperties";
        public static final String FIRST_NAME_PROPERTY_NAME = "firstName";
        public static final String LAST_NAME_PROPERTY_NAME = "lastName";

        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @PropertyOptions(indexing = { PropertyIndexingOption.SORT,
                PropertyIndexingOption.CASE_INSENSITIVE })
        public String email;

        @PropertyOptions(indexing = { PropertyIndexingOption.EXPAND,
                PropertyIndexingOption.CASE_INSENSITIVE })
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Map<String, String> customProperties;

        @PropertyOptions(indexing = { PropertyIndexingOption.EXPAND,
                PropertyIndexingOption.CASE_INSENSITIVE })
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Set<String> userGroupLinks;
    }

    public UserService() {
        super(UserState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void processCompletionStageUpdateAuthzArtifacts(Operation op) {
        if (AuthorizationCacheUtils.isAuthzCacheClearApplicableOperation(op)) {
            AuthorizationCacheUtils.clearAuthzCacheForUser(this, op);
        }
        op.complete();
    }

    @Override
    public void handleStart(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }

        UserState state = op.getBody(UserState.class);
        if (!validate(op, state)) {
            return;
        }

        op.complete();
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        UserState currentState = getState(op);
        Set<String> currentOrgLinks = extractUsersOrgLinks(currentState);
        try {
            if (!Utils.mergeWithState(currentState, op)) {
                UserState newState = getBody(op);
                if (newState.email != null && !validateEmail(op, newState.email)) {
                    op.fail(new IllegalArgumentException("Invalid email address"));
                    return;
                }
                Utils.mergeWithState(getStateDescription(), currentState, newState);
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            op.fail(e);
            return;
        }

        Set<String> newOrgLinks = extractUsersOrgLinks(currentState);
        if (!currentOrgLinks.equals(newOrgLinks)) {
            // TODO: postUserTelemetry(this, currentState);
        }

        op.setBody(currentState).complete();
    }

    private boolean validate(Operation op, UserState state) {
        if (state.email == null) {
            op.fail(new IllegalArgumentException("email is required"));
            return false;
        }

        if (state.userGroupLinks == null) {
            state.userGroupLinks = new HashSet<>();
        }

        return validateEmail(op, state.email);
    }

    private boolean validateEmail(Operation op, String email) {
        int firstAtIndex = email.indexOf('@');
        int lastAtIndex = email.lastIndexOf('@');
        if (firstAtIndex == -1 || (firstAtIndex != lastAtIndex)) {
            op.fail(new IllegalArgumentException("email is invalid"));
            return false;
        }
        return true;
    }

    /**
     * Extract the list of distinct organizations a user is a part of.
     * @param userState A Symphony user.
     * @return A Set of distinct organization links the user is a part of.
     */
    public static Set<String> extractUsersOrgLinks(UserState userState) {
        if (userState == null || userState.userGroupLinks == null) {
            return Collections.emptySet();
        }

        Set<String> orgLinks = new HashSet<>();
        for (String userGroupLink : userState.userGroupLinks) {
            if (userGroupLink.startsWith(UserGroupService.FACTORY_LINK)) {
                String orgId = userGroupLink.substring(UserGroupService.FACTORY_LINK.length() + 1);
                if (orgId.contains(SEPARATOR)) {
                    orgId = orgId.substring(0, orgId.indexOf(SEPARATOR));
                }
                String orgLink = UriUtils.buildUriPath(OrganizationService.FACTORY_LINK, orgId);
                orgLinks.add(orgLink);
            }
        }
        return orgLinks;
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument td = super.getDocumentTemplate();
        // enable metadata indexing
        td.documentDescription.documentIndexingOptions =
                EnumSet.of(DocumentIndexingOption.INDEX_METADATA);

        UserState template = (UserState) td;
        return template;
    }
}
