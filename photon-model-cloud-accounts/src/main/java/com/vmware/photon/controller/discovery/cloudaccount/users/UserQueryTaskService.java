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

package com.vmware.photon.controller.discovery.cloudaccount.users;

import static com.vmware.photon.controller.discovery.cloudaccount.users.UserQueryTaskService.SubStage.BUILD_PODO;
import static com.vmware.photon.controller.discovery.cloudaccount.users.UserQueryTaskService.SubStage.BUILD_RESULTS;
import static com.vmware.photon.controller.discovery.cloudaccount.users.UserQueryTaskService.SubStage.GET_PAGE;
import static com.vmware.photon.controller.discovery.cloudaccount.users.UserQueryTaskService.SubStage.SUCCESS;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.PAGE_TOKEN;
import static com.vmware.photon.controller.discovery.common.utils.OnboardingUtils.ADMIN_SUFFIX;
import static com.vmware.photon.controller.discovery.common.utils.OnboardingUtils.USER_SUFFIX;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.SEPARATOR;
import static com.vmware.photon.controller.model.UriPaths.USERS_QUERY_TASK_SERVICE;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

import com.vmware.photon.controller.discovery.cloudaccount.UserViewState;
import com.vmware.photon.controller.discovery.cloudaccount.UserViewState.Role;
import com.vmware.photon.controller.discovery.cloudaccount.users.UserQueryTaskService.UserQueryTaskState;
import com.vmware.photon.controller.discovery.cloudaccount.utils.FilterUtils;
import com.vmware.photon.controller.discovery.cloudaccount.utils.QueryUtils;
import com.vmware.photon.controller.discovery.common.services.UserContextQueryService;
import com.vmware.photon.controller.discovery.common.services.UserContextQueryService.UserContext;
import com.vmware.photon.controller.discovery.common.services.UserService;
import com.vmware.photon.controller.discovery.common.services.UserService.UserState;
import com.vmware.photon.controller.discovery.common.utils.OnboardingUtils;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceHost.ServiceNotFoundException;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskFactoryService;
import com.vmware.xenon.services.common.TaskService;
import com.vmware.xenon.services.common.UserGroupService;

/**
 * Task service to retrieve users information in an API-friendly way. The task service processes
 * incoming requests and executes appropriate query. A successful task completion populates the
 * results field in {@link UserQueryTaskState}. The clients can do GET operation on the
 * next/prev page links to get more results.
 */
public class UserQueryTaskService extends TaskService<UserQueryTaskState> {
    public static final String FACTORY_LINK = USERS_QUERY_TASK_SERVICE;

    public static final List<String> USER_ROLE_NAMES = Arrays.stream(Role.values())
            .map(value -> value.name())
            .collect(Collectors.toList());

    public static final String GROUP_LINKS_FILED_NAME = QuerySpecification
            .buildCollectionItemName(UserState.FIELD_NAME_USER_GROUP_LINKS);
    /**
     * The state for the user query task service.
     */
    public static class UserQueryTaskState extends TaskService.TaskServiceState {
        @Documentation(description = "The filter criteria.")
        public QuerySpecification filter;

        @Documentation(description = "The filter override to pass query. This takes precedence over filter.")
        public QuerySpecification filterOverride;

        @Documentation(description = "The page token.")
        public String pageToken;

        @Documentation(description = "The user link.")
        public String userLink;

        @Documentation(description = "The user result set")
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public ServiceDocumentQueryResult results;

        @Documentation(description = "This is only for internal usage. It keeps track of internal stages")
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public SubStage subStage;

        @Documentation(description = "The org link.")
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public String orgLink;

        @Documentation(description = "The user states.")
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public List<UserState> userStates;

        @Documentation(description = "The transformed user view states.")
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public List<UserViewState> userViewStates;

        @Documentation(description = "The next page link.")
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public String nextPageLink;

        @Documentation(description = "The prev page link.")
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public String prevPageLink;

        @Documentation(description = "The tenant links")
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public List<String> tenantLinks;
    }

    /**
     * Substages for task processing.
     */
    enum SubStage {
        /**
         * Stage to query users.
         */
        QUERY_USERS,

        /**
         * Stage to get page of users.
         */
        GET_PAGE,

        /**
         * Stage to get a specific user.
         */
        GET_USER,

        /**
         * Stage to transfer user states into user view states.
         */
        BUILD_PODO,

        /**
         * Stage to build final results.
         */
        BUILD_RESULTS,

        /**
         * Stage to indicate success.
         */
        SUCCESS
    }

    public static FactoryService createFactory() {
        TaskFactoryService fs = new TaskFactoryService(UserQueryTaskState.class) {
            @Override
            public Service createServiceInstance() {
                return new UserQueryTaskService();
            }
        };
        fs.setPeerNodeSelectorPath(ServiceUriPaths.DEFAULT_1X_NODE_SELECTOR);
        return fs;
    }

    public UserQueryTaskService() {
        super(UserQueryTaskState.class);
        toggleOption(ServiceOption.REPLICATION, true);
        toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handleStart(Operation taskOperation) {
        UserQueryTaskState state;
        if (!taskOperation.hasBody()) {
            state = new UserQueryTaskState();
            taskOperation.setBody(state);
        }

        if (taskOperation.getAuthorizationContext().isSystemUser()) {
            String msg = "System user is unauthorized";
            taskOperation.fail(Operation.STATUS_CODE_FORBIDDEN, new Exception(msg), msg);
            return;
        }

        UserQueryTaskState body = taskOperation.getBody(UserQueryTaskState.class);
        Operation.createGet(this, UserContextQueryService.SELF_LINK)
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        taskOperation.fail(ex);
                        return;
                    }

                    UserContext userContext = op.getBody(UserContext.class);
                    List<String> projectLinks = userContext.projects.stream()
                            .map(project -> project.documentSelfLink)
                            .collect(Collectors.toList());

                    if (body.tenantLinks != null && !body.tenantLinks.isEmpty()) {
                        if (!projectLinks.containsAll(body.tenantLinks)) {
                            taskOperation.fail(new IllegalArgumentException(
                                    "User does not have access to all 'tenantLinks' provided"));
                            return;
                        }
                    } else {
                        body.tenantLinks = projectLinks;
                    }

                    if (userContext.organizations == null || userContext.organizations.isEmpty()
                            || userContext.organizations.size() != 1) {
                        taskOperation.fail(new IllegalArgumentException(
                                "Incorrect user organization membership"));
                        return;
                    }
                    body.orgLink = userContext.organizations.iterator().next().documentSelfLink;

                    taskOperation.setBody(body);
                    super.handleStart(taskOperation);
                })
                .sendWith(this);

    }

    @Override
    protected void initializeState(UserQueryTaskState task, Operation taskOperation) {
        if (task.pageToken != null && !task.pageToken.isEmpty()) {
            task.subStage = GET_PAGE;
        } else if (task.userLink != null && !task.userLink.isEmpty()) {
            task.subStage = SubStage.GET_USER;
        } else {
            task.subStage = SubStage.QUERY_USERS;
        }

        super.initializeState(task, taskOperation);
    }

    @Override
    protected UserQueryTaskState validateStartPost(Operation taskOperation) {
        UserQueryTaskState task = super.validateStartPost(taskOperation);
        if (task == null) {
            return null;
        }

        if (TaskState.isCancelled(task.taskInfo)
                || TaskState.isFailed(task.taskInfo)
                || TaskState.isFinished(task.taskInfo)) {
            return null;
        }

        if (!ServiceHost.isServiceCreate(taskOperation)) {
            return task;
        }

        if (task.filter != null) {
            if ((task.pageToken != null && !task.pageToken.isEmpty()) ||
                    (task.userLink != null && !task.userLink.isEmpty())) {
                taskOperation.fail(
                        new IllegalArgumentException(
                                "Do not specify pageToken or userLink along with filter"));
                return null;
            }
        }

        if (task.pageToken != null && !task.pageToken.isEmpty()) {
            if (task.userLink != null && !task.userLink.isEmpty()) {
                taskOperation.fail(
                        new IllegalArgumentException(
                                "Do not specify userLink along with pageToken"));
                return null;
            }
        }

        if (task.subStage != null) {
            taskOperation.fail(
                    new IllegalArgumentException("Do not specify subStage: internal use only"));
            return null;
        }

        if (task.userStates != null) {
            taskOperation.fail(
                    new IllegalArgumentException("Do not specify userStates: internal use only"));
            return null;
        }

        if (task.userViewStates != null) {
            taskOperation.fail(
                    new IllegalArgumentException(
                            "Do not specify userViewStates: internal use only"));
            return null;
        }

        if (task.nextPageLink != null) {
            taskOperation.fail(
                    new IllegalArgumentException("Do not specify nextPageLink: internal use only"));
            return null;
        }

        if (task.prevPageLink != null) {
            taskOperation.fail(
                    new IllegalArgumentException("Do not specify prevPageLink: internal use only"));
            return null;
        }

        if (task.results != null) {
            taskOperation.fail(
                    new IllegalArgumentException("Do not specify results: internal use only"));
            return null;
        }

        return task;
    }

    @Override
    protected boolean validateTransition(Operation patch, UserQueryTaskState currentTask,
            UserQueryTaskState patchBody) {
        super.validateTransition(patch, currentTask, patchBody);
        if (patchBody.taskInfo.stage == TaskStage.STARTED && patchBody.subStage == null) {
            patch.fail(new IllegalArgumentException("Missing sub-stage"));
            return false;
        }

        return true;
    }

    @Override
    public void handlePatch(Operation patch) {
        UserQueryTaskState currentTask = getState(patch);
        UserQueryTaskState patchBody = getBody(patch);

        if (!validateTransition(patch, currentTask, patchBody)) {
            return;
        }
        updateState(currentTask, patchBody);
        patch.complete();

        switch (patchBody.taskInfo.stage) {
        case STARTED:
            handleSubStage(patchBody);
            break;
        case FINISHED:
            logFine("Task finished successfully");
            break;
        case FAILED:
            logWarning("Task failed: %s", (patchBody.failureMessage == null ? "No reason given"
                    : patchBody.failureMessage));
            break;
        default:
            logWarning("Unexpected stage: %s", patchBody.taskInfo.stage);
            break;
        }
    }

    /**
     * State machine for the task service.
     */
    private void handleSubStage(UserQueryTaskState state) {
        switch (state.subStage) {
        case QUERY_USERS:
            queryUsers(state, GET_PAGE);
            break;
        case GET_PAGE:
            getPage(state, BUILD_PODO);
            break;
        case GET_USER:
            getUser(state, BUILD_PODO);
            break;
        case BUILD_PODO:
            buildPODO(state, BUILD_RESULTS);
            break;
        case BUILD_RESULTS:
            buildResults(state, SUCCESS);
            break;
        case SUCCESS:
            sendSelfPatch(state, TaskStage.FINISHED, null);
            break;
        default:
            throw new IllegalStateException("Unknown stage encountered: " + state.subStage);
        }
    }

    private void queryUsers(UserQueryTaskState state, SubStage nextStage) {
        // TODO : Add support for filtering
        // TODO : Add support for sorting
        QuerySpecification querySpec = new QuerySpecification();
        if (state.orgLink != null && !state.orgLink.isEmpty()) {
            String orgId = UriUtils.getLastPathSegment(state.orgLink);
            String orgPrefix = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK, orgId);

            querySpec = buildQuerySpecification(state, orgPrefix);

            querySpec.query.addBooleanClause(Query.Builder.create()
                    .addKindFieldClause(UserState.class).build());

            querySpec.query.addBooleanClause(Query.Builder.create()
                    .addFieldClause(GROUP_LINKS_FILED_NAME, orgPrefix, MatchType.PREFIX)
                    .build());
        }

        QuerySpecification filter = state.filter;
        if (state.filterOverride != null) {
            filter = state.filterOverride;
            querySpec.query.addBooleanClause(filter.query);
        }

        if (filter != null && filter.resultLimit != null) {
            querySpec.resultLimit = filter.resultLimit;
        } else {
            querySpec.resultLimit = QueryUtils.QUERY_RESULT_LIMIT;
        }

        querySpec.options.add(QueryOption.EXPAND_CONTENT);

        QueryTask qt = QueryTask.create(querySpec).setDirect(true);

        QueryUtils.startQueryTaskWithSystemAuth(this, qt,
                (response, failure) -> {
                    if (failure != null) {
                        handleError(state, failure);
                        return;
                    }

                    if (response == null || response.nextPageLink == null) {
                        state.results = new ServiceDocumentQueryResult();
                        state.subStage = SUCCESS;
                        sendSelfPatch(state);
                        return;
                    }

                    state.pageToken = encodePageLink(response.nextPageLink);
                    state.subStage = nextStage;
                    sendSelfPatch(state);
                });
    }

    private void getPage(UserQueryTaskState state, SubStage nextStage) {
        if (state.pageToken == null || state.pageToken.isEmpty()) {
            handleError(state, new RuntimeException("The 'pageToken' must be specified"));
            return;
        }

        String pageLink = decodePageToken(state.pageToken);

        QueryUtils.getPage(this, pageLink, true)
                .whenComplete((queryTask, failure) -> {
                    if (failure != null) {
                        Throwable cause = failure.getCause();
                        if (cause instanceof ServiceNotFoundException) {
                            handleError(state, "Page not found", Operation.STATUS_CODE_NOT_FOUND);
                            return;
                        }
                        handleError(state, failure);
                        return;
                    }

                    ServiceDocumentQueryResult results = queryTask.results;

                    if (results.documentCount == 0) {
                        state.results = new ServiceDocumentQueryResult();
                        state.subStage = SUCCESS;
                        sendSelfPatch(state);
                        return;
                    }

                    List<UserState> userStates = new ArrayList<>();
                    for (String documentLink : results.documentLinks) {
                        UserState user = Utils
                                .fromJson(results.documents.get(documentLink), UserState.class);
                        userStates.add(user);
                    }

                    state.nextPageLink = queryTask.results.nextPageLink;
                    state.prevPageLink = queryTask.results.prevPageLink;
                    state.userStates = userStates;
                    state.subStage = nextStage;
                    sendSelfPatch(state);
                });
    }

    private void getUser(UserQueryTaskState state, SubStage nextStage) {
        if (state.userLink == null || state.userLink.isEmpty()) {
            handleError(state, new RuntimeException("The 'userLink' must be specified"));
            return;
        }

        String userLink = UriUtils.buildUriPath(UserService.FACTORY_LINK,
                UriUtils.getLastPathSegment(state.userLink));

        OperationContext ctx = OperationContext.getOperationContext();
        Operation op = Operation.createGet(getHost(), userLink)
                .setCompletion((operation, failure) -> {
                    OperationContext.restoreOperationContext(ctx);
                    if (failure != null) {
                        handleError(state, failure.getMessage(), operation.getStatusCode());
                        return;
                    }
                    state.userStates = Collections
                            .singletonList(operation.getBody(UserState.class));
                    state.subStage = nextStage;
                    sendSelfPatch(state);
                });
        setAuthorizationContext(op, getSystemAuthorizationContext());
        sendRequest(op);
    }

    private void buildPODO(UserQueryTaskState state, SubStage nextStage) {
        String orgOwnerGroupLink =
                OnboardingUtils.buildAuthzArtifactLink(
                        UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                        UriUtils.getLastPathSegment(state.orgLink)), true);
        List<UserViewState> userViewStates = new ArrayList<>();
        for (UserState userState : state.userStates) {
            UserViewState userViewState = UserViewState.createUserView(userState);
            userViewState.roles = new HashSet<>();
            if (userState.userGroupLinks != null && !userState.userGroupLinks.isEmpty()
                    && userState.userGroupLinks.contains(orgOwnerGroupLink)) {
                userViewState.roles.add(Role.ORG_OWNER);
            } else {
                userViewState.roles.add(Role.ORG_USER);
            }
            userViewStates.add(userViewState);
        }
        state.userViewStates = userViewStates;
        state.subStage = nextStage;
        sendSelfPatch(state);
    }

    private void buildResults(UserQueryTaskState state, SubStage nextStage) {
        ServiceDocumentQueryResult result = new ServiceDocumentQueryResult();
        result.documentLinks = new ArrayList<>();
        result.documents = new LinkedHashMap<>();
        for (UserViewState userViewState : state.userViewStates) {
            result.documentLinks.add(userViewState.documentSelfLink);
            result.documents.put(userViewState.documentSelfLink, userViewState);
        }

        result.nextPageLink = buildPageLink(state.nextPageLink);
        result.prevPageLink = buildPageLink(state.prevPageLink);
        result.documentCount = (long) state.userViewStates.size();
        state.results = result;
        state.subStage = nextStage;
        sendSelfPatch(state);
    }

    private String buildPageLink(String pageLink) {
        if (pageLink == null || pageLink.isEmpty()) {
            return null;
        }

        return UsersApiService.SELF_LINK + UriUtils.URI_QUERY_CHAR + UriUtils
                .buildUriQuery(PAGE_TOKEN, encodePageLink(pageLink));
    }

    private String encodePageLink(String pageLink) {
        if (pageLink == null || pageLink.isEmpty()) {
            logWarning("The pageLink must be provided.");
            return null;
        }

        String encodedPageLink = null;
        try {
            encodedPageLink = Base64.getEncoder().encodeToString(pageLink.getBytes(Utils.CHARSET));
        } catch (UnsupportedEncodingException e) {
            logWarning("Unable to create pageToken for %s", pageLink);
        }
        return encodedPageLink;
    }

    private String decodePageToken(String pageToken) {
        if (pageToken == null || pageToken.isEmpty()) {
            return null;
        }

        String pageLink = null;
        try {
            pageLink = new String(Base64.getDecoder().decode(pageToken), Utils.CHARSET);
        } catch (UnsupportedEncodingException e) {
            logWarning("Unable to create pageLink for %s", pageToken);
        }
        return pageLink;
    }

    private QuerySpecification buildQuerySpecification(UserQueryTaskState state, String orgPrefix) {
        QuerySpecification querySpec;
        if (state.filter != null) {
            querySpec = state.filter;
            querySpec.query = transformQuery(querySpec.query, orgPrefix);
        } else {
            querySpec = new QuerySpecification();
        }
        return querySpec;
    }

    private Query transformQuery(Query query, String orgPrefix) {
        Query.Builder transformedQueryBuilder = Query.Builder.create();
        String orgAdminGroupLink = orgPrefix + SEPARATOR + ADMIN_SUFFIX;
        String orgUserGroupLink = orgPrefix + SEPARATOR + USER_SUFFIX;
        Boolean[] isUserRoleSpecified = {false};
        Boolean[] isAdminRoleSpecified = {false};

        FilterUtils.findPropertyNameMatch(query, Collections.singleton(UserViewState.FIELD_NAME_ROLES),
                result -> {
                    String matchValue = result.term.matchValue;
                    if (matchValue.equals(Role.ORG_OWNER.name())) {
                        isAdminRoleSpecified[0] = true;
                        transformedQueryBuilder.addFieldClause(GROUP_LINKS_FILED_NAME,
                                orgAdminGroupLink, result.occurance);
                    } else if (matchValue.equals(Role.ORG_USER.name())) {
                        isUserRoleSpecified[0] = true;
                        transformedQueryBuilder.addFieldClause(GROUP_LINKS_FILED_NAME,
                                orgUserGroupLink, result.occurance);
                    }
                });

        if (isUserRoleSpecified[0] && !isAdminRoleSpecified[0]) {
            transformedQueryBuilder.addFieldClause(GROUP_LINKS_FILED_NAME,
                    orgAdminGroupLink, Occurance.MUST_NOT_OCCUR);
        }

        return transformedQueryBuilder.build();
    }

    private void handleError(UserQueryTaskState state, Throwable ex) {
        logSevere("Failed at sub-stage %s with exception: %s", state.subStage,
                Utils.toString(ex));
        handleError(state, ex.getLocalizedMessage(), Operation.STATUS_CODE_INTERNAL_ERROR);
    }

    private void handleError(UserQueryTaskState state, String failureMessage,
            int statusCode) {
        logWarning("Task failed at sub-stage %s with failure: %s", state.subStage,
                failureMessage);

        ServiceErrorResponse e = new ServiceErrorResponse();
        e.message = failureMessage;
        e.statusCode = statusCode;
        state.taskInfo.failure = e;
        sendSelfFailurePatch(state, failureMessage);
    }
}
