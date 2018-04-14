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

import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.CSP_ORG_ID;
import static com.vmware.photon.controller.model.UriPaths.USER_CONTEXT_QUERY_SERVICE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.vmware.photon.controller.discovery.common.services.OrganizationService.OrganizationState;
import com.vmware.photon.controller.discovery.common.services.ProjectService.ProjectState;
import com.vmware.photon.controller.discovery.common.services.UserService.UserState;
import com.vmware.xenon.common.Claims;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.OperationJoin.JoinedCompletionHandler;
import com.vmware.xenon.common.RequestRouter.Route;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.GuestUserService;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.UserGroupService;

/**
 * This service returns the user info, team and organiation info for the logged in user
 */
public class UserContextQueryService extends StatelessService {

    public static final String SELF_LINK = USER_CONTEXT_QUERY_SERVICE;
    private static final String ADMIN_SUFFIX = "admin";

    private enum QueryContext {
        ORG_QUERY, PROJECT_QUERY
    }

    private static final String SEPERATOR = "-";

    @Override
    public void authorizeRequest(Operation op) {
        if (op.getAuthorizationContext() != null &&
                !op.getAuthorizationContext().getClaims().getSubject()
                        .equals(GuestUserService.SELF_LINK)) {
            op.complete();
            return;
        }
        op.fail(Operation.STATUS_CODE_FORBIDDEN);
    }

    public static class UserContext {
        public Set<ProjectState> projects = new HashSet<>();
        public Set<OrganizationState> organizations = new HashSet<>();
        public UserState user;
    }

    @Override
    public void handleGet(Operation op) {
        Claims claims = op.getAuthorizationContext().getClaims();
        Operation userOp = Operation
                .createGet(getHost(), claims.getSubject())
                .setCompletion((o, t) -> {
                    try {
                        if (t != null) {
                            op.fail(t);
                            return;
                        }
                        UserContext returnContext = new UserContext();
                        UserState userState = o.getBody(UserState.class);
                        returnContext.user = userState;
                        if (userState.userGroupLinks == null) {
                            op.setBody(returnContext);
                            op.complete();
                            return;
                        }
                        // filter orgs and projects user belongs to based on the logged in org
                        Set<String> filteredUserGroups = filterGroupsForOrg(userState.userGroupLinks,
                                claims.getProperties().get(CSP_ORG_ID));
                        Set<String> orgAndTeamSet =
                                extractOrgAndTeamServiceNames(filteredUserGroups);
                        if (orgAndTeamSet == null || orgAndTeamSet.size() == 0) {
                            op.setBody(returnContext);
                            op.complete();
                            return;
                        }
                        List<Operation> joinedOperations = new ArrayList<>();
                        Operation orgOp = buildQueryOperation(
                                extractEntityIds(orgAndTeamSet, true),
                                QueryContext.ORG_QUERY, returnContext);
                        joinedOperations.add(orgOp);
                        Set<String> projectSet = extractEntityIds(orgAndTeamSet, false);
                        addAdminOrgs(projectSet, filteredUserGroups);
                        if (!projectSet.isEmpty()) {
                            Operation projectOp = buildQueryOperation(projectSet,
                                    QueryContext.PROJECT_QUERY, returnContext);
                            joinedOperations.add(projectOp);
                        }
                        JoinedCompletionHandler joinHandler = (ops, exc) -> {
                            if (exc != null) {
                                op.fail(exc.values().iterator().next());
                                return;
                            }
                            op.setBody(returnContext);
                            op.complete();
                        };
                        OperationJoin joinOp = OperationJoin.create(joinedOperations);
                        joinOp.setCompletion(joinHandler);
                        joinOp.sendWith(getHost());
                    } catch (Exception e) {
                        op.fail(e);
                    }
                });
        setAuthorizationContext(userOp, getSystemAuthorizationContext());
        sendRequest(userOp);

    }

    /**
     * If the user is an org admin ; return the set of orgs for which he is admin;
     * All the projects in those orgs will become a part of the user context.
     *
     */
    private void addAdminOrgs(Set<String> projectSet,
            Set<String> userGroupLinks) {
        Set<String> orgsWhereUserIsAdmin = extractOrgsWhereUserIsAdmin(
                userGroupLinks);
        if (orgsWhereUserIsAdmin != null && !orgsWhereUserIsAdmin.isEmpty()) {
            projectSet.addAll(orgsWhereUserIsAdmin);
        }
    }

    private Operation buildQueryOperation(Set<String> orgAndTeamNames, QueryContext queryContext, UserContext userContext) {
        Query.Builder queryDocBuilder = Query.Builder.create();
        Query.Builder queryBuilder = Query.Builder.create();
        switch (queryContext) {
        case ORG_QUERY:
            queryDocBuilder = createPrefixClauseForMatching(orgAndTeamNames, queryDocBuilder,
                    OrganizationService.FACTORY_LINK);
            queryBuilder.addKindFieldClause(OrganizationState.class);
            break;
        case PROJECT_QUERY:
            queryDocBuilder = createPrefixClauseForMatching(orgAndTeamNames, queryDocBuilder,
                    ProjectService.FACTORY_LINK);
            queryBuilder.addKindFieldClause(ProjectState.class);
            break;
        default:
            break;
        }
        queryBuilder.addClause(queryDocBuilder.build());
        QueryTask task = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .addOption(QueryOption.INDEXED_METADATA)
                .setQuery(queryBuilder.build()).build();
        Operation queryOp = Operation
                .createPost(getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS)
                .setBody(task)
                .setReferer(getHost().getUri())
                .setConnectionSharing(true)
                .setCompletion((getOp, getEx) -> {
                    if (getEx != null) {
                        logWarning("Error encountered in executing query  " + getEx);
                        return;
                    }
                    QueryTask responseTask = getOp.getBody(QueryTask.class);
                    for (Object object : responseTask.results.documents.values()) {
                        switch (queryContext) {
                        case ORG_QUERY:
                            userContext.organizations
                                    .add(Utils.fromJson(object, OrganizationState.class));
                            break;
                        case PROJECT_QUERY:
                            userContext.projects.add(Utils.fromJson(object, ProjectState.class));
                            break;
                        default:
                            break;
                        }
                    }
                });
        return queryOp;
    }

    private Query.Builder createPrefixClauseForMatching(Set<String> orgAndTeamNames,
            Query.Builder queryDocBuilder,
            String factoryLink) {
        for (String name: orgAndTeamNames) {
            queryDocBuilder.addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                    UriUtils.buildUriPath(factoryLink, name),
                    MatchType.PREFIX, Occurance.SHOULD_OCCUR);
        }
        return queryDocBuilder;
    }

    private Set<String> filterGroupsForOrg(Set<String> userGroupLinks, String orgId) {
        if (orgId == null) {
            return userGroupLinks;
        }
        orgId = Utils.computeHash(orgId);
        Set<String> filteredList = new HashSet<>();
        for (String userGroupLink : userGroupLinks) {
            if (UriUtils.getLastPathSegment(userGroupLink).startsWith(orgId)) {
                filteredList.add(userGroupLink);
            }
        }
        return filteredList;
    }

    private Set<String> extractOrgAndTeamServiceNames(Set<String> userGroupLinks) {
        Set<String> orgAndTeamSet = new HashSet<>();
        for (String userGroupLink : userGroupLinks) {
            orgAndTeamSet.add((userGroupLink.substring(
                        (UserGroupService.FACTORY_LINK.length() + 1), userGroupLink.lastIndexOf(SEPERATOR))));
        }
        return orgAndTeamSet;
    }

    /**
     * Return the set of projects or organizations identifiers that are part of the user links based on the passed in flag.
     */
    private Set<String> extractEntityIds(Set<String> orgAndTeamSet, boolean orgsOnly) {
        Set<String> entitySet = new HashSet<>();
        for (String entity : orgAndTeamSet) {
            if (entity.contains(SEPERATOR) && !orgsOnly) {
                entitySet.add(entity);
            } else if (orgsOnly) {
                entitySet.add(entity);
            }
        }
        return entitySet;
    }

    private int countOccurrencesOfCharInString(String str, char c) {
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == c) {
                count++;
            }
        }
        return count;
    }

    /**
     * Parses the user group link of type "/core/authz/user-groups/6a4e3e1e845323c5-cbf32efa2f08ee95-admin"
     * to determine the organizations that the user is an admin for.
     *
     * The order followed is
     * from all the user group links
     * - Find all the links with "admin"
     * - Remove the prefix "/user-groups"
     * - Find only the links that are of type "xxx-admin" vs the projects admin are of the type "xxx-xxx-admin"
     * - Find the identifier of the organization from "xxx-admin".
     */
    private Set<String> extractOrgsWhereUserIsAdmin(Set<String> userGroupLinks) {
        return userGroupLinks.stream().filter(link -> link.contains(ADMIN_SUFFIX))
                .map(userGroupLink -> userGroupLink
                        .substring(UserGroupService.FACTORY_LINK.length() + 1))
                .filter(adminLink -> countOccurrencesOfCharInString(adminLink, '-') == 1)
                .map(userGroupIdentifier -> userGroupIdentifier.substring(0,
                        userGroupIdentifier.indexOf(SEPERATOR)))
                .collect(Collectors.toSet());

    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument d = super.getDocumentTemplate();
        d.documentDescription.serviceRequestRoutes = new HashMap<>();
        Route route = new Route();
        route.action = Action.GET;
        route.description = "Return  user, team and organization info for the current user";
        route.responseType = UserContext.class;
        d.documentDescription.serviceRequestRoutes
                .put(route.action, Collections.singletonList(route));
        return d;
    }
}
