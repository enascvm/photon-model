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

import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.PAGE_TOKEN;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.handleCompletion;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import com.vmware.photon.controller.discovery.cloudaccount.UserViewState;
import com.vmware.photon.controller.discovery.cloudaccount.users.UserQueryTaskService.UserQueryTaskState;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.RequestRouter.Route.RouteDocumentation;
import com.vmware.xenon.common.RequestRouter.Route.RouteDocumentation.ApiResponse;
import com.vmware.xenon.common.RequestRouter.Route.RouteDocumentation.PathParam;
import com.vmware.xenon.common.RequestRouter.Route.RouteDocumentation.QueryParam;
import com.vmware.xenon.common.RequestRouter.Route.SupportLevel;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocument.Documentation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;

/**
 * APIs for retrieving users.
 */
public class UsersApiService extends StatelessService {
    public static final String SELF_LINK = UriPaths.USERS_API_SERVICE;
    private static final String URL_PARAM_MATCH_REGEX = "([-a-zA-Z0-9:_]+)";

    public static final String USERS_PATH_WITH_ID_TEMPLATE = UriUtils
            .buildUriPath(SELF_LINK, "{id}");
    public static final Pattern USERS_PATH_WITH_ID_PATTERN = Pattern.compile(UriUtils
            .buildUriPath(SELF_LINK, URL_PARAM_MATCH_REGEX));

    public static final String USERS_QUERY_PATH_TEMPLATE = UriUtils
            .buildUriPath(SELF_LINK, "search");

    /**
     * Request to filter users.
     */
    public static class UserSearchRequest {
        @Documentation(description = "The search criteria")
        public QuerySpecification filter;
    }

    public UsersApiService() {
        super();
        this.toggleOption(ServiceOption.URI_NAMESPACE_OWNER, true);
    }

    @RouteDocumentation(
            description = "Retrieve a paged list of users",
            queryParams = {
                    @QueryParam(name = PAGE_TOKEN, description = "The token for retrieving next page", type = "String")
            },
            responses = {
                    @ApiResponse(statusCode = 200, description = "Success", response = ServiceDocumentQueryResult.class),
                    @ApiResponse(statusCode = 404, description = "Not found", response = ServiceErrorResponse.class)
            })
    @RouteDocumentation(
            path =  "/{id}",
            description = "Retrieves a user for the given id",
            pathParams = {
                    @PathParam(name = "id", description = "The user id")
            },
            responses = {
                    @ApiResponse(statusCode = 200, description = "Success", response = UserViewState.class),
                    @ApiResponse(statusCode = 404, description = "User not found", response = ServiceErrorResponse.class)
            })
    @Override
    public void handleGet(Operation get) {
        if (get.getUri().getPath().equals(SELF_LINK)) {
            Map<String, String> params = UriUtils.parseUriQueryParams(get.getUri());
            if (params == null || params.isEmpty()) {
                getUsers(get, null);
                return;
            }

            // Check if there is a page token on the request
            String pageToken = params.get(PAGE_TOKEN);
            if (pageToken != null && !pageToken.isEmpty()) {
                getUserPage(get, pageToken);
                return;
            }

            Operation.failActionNotSupported(get);
            return;
        }

        if (USERS_PATH_WITH_ID_PATTERN.matcher(get.getUri().getPath()).matches()) {
            getUser(get);
            return;
        }

        Operation.failActionNotSupported(get);
    }

    @RouteDocumentation(
            path = "/search",
            description = "Retrieves a filtered list of users.",
            requestBodyType = UserSearchRequest.class,
            responses = {
                    @ApiResponse(statusCode = 200, description = "Success", response = ServiceDocumentQueryResult.class),
                    @ApiResponse(statusCode = 403, description = "Forbidden request")
            })
    @Override
    public void handlePost(Operation post) {
        if (post.getUri().getPath().equals(USERS_QUERY_PATH_TEMPLATE)) {
            QuerySpecification filter = null;
            if (post.hasBody()) {
                filter = post.getBody(UserSearchRequest.class).filter;
            }
            getUsers(post, filter);
            return;
        }
        Operation.failActionNotSupported(post);
    }

    @RouteDocumentation(supportLevel = SupportLevel.NOT_SUPPORTED)
    @Override
    public void handlePatch(Operation patch) {
        Operation.failActionNotSupported(patch);
    }

    @RouteDocumentation(supportLevel = SupportLevel.NOT_SUPPORTED)
    @Override
    public void handlePut(Operation put) {
        Operation.failActionNotSupported(put);
    }

    @RouteDocumentation(supportLevel = SupportLevel.NOT_SUPPORTED)
    @Override
    public void handleDelete(Operation delete) {
        Operation.failActionNotSupported(delete);
    }

    private void getUser(Operation get) {
        Map<String, String> params = UriUtils
                .parseUriPathSegments(get.getUri(), USERS_PATH_WITH_ID_TEMPLATE);
        String userId = params.get("id");
        if (userId == null || userId.isEmpty()) {
            Operation.failActionNotSupported(get);
            return;
        }

        UserQueryTaskState task = new UserQueryTaskState();
        task.taskInfo = TaskState.createDirect();
        task.userLink = get.getUri().getPath();

        sendRequest(Operation.createPost(getHost(), UserQueryTaskService.FACTORY_LINK)
                .setBody(task)
                .setCompletion((op, ex) -> {
                    if (!handleCompletion(get, op, ex)) {
                        return;
                    }

                    UserQueryTaskState result = op.getBody(UserQueryTaskState.class);
                    if (result.results == null || Objects
                            .equals(result.results.documentCount, 0L)) {
                        get.fail(Operation.STATUS_CODE_NOT_FOUND);
                        return;
                    } else if (!Objects.equals(result.results.documentCount, 1L)) {
                        get.fail(Operation.STATUS_CODE_INTERNAL_ERROR);
                        return;
                    }
                    get.setBody(result.results.documents.values().iterator().next());
                    get.complete();
                }));
    }

    private void getUserPage(Operation get, String pageToken) {
        UserQueryTaskState task = new UserQueryTaskState();
        task.taskInfo = TaskState.createDirect();
        task.pageToken = pageToken;

        sendRequest(Operation.createPost(getHost(), UserQueryTaskService.FACTORY_LINK)
                .setBody(task)
                .setCompletion((op, ex) -> {
                    if (!handleCompletion(get, op, ex)) {
                        return;
                    }

                    UserQueryTaskState state = op.getBody(UserQueryTaskState.class);
                    if (state.taskInfo.stage == TaskState.TaskStage.FAILED) {
                        logWarning("UserQueryTask failed!\n%s", Utils.toJson(state));
                        get.fail(new RuntimeException(state.failureMessage));
                        return;
                    }

                    if (state.results == null) {
                        logInfo("No results found in response: %s", Utils.toJson(state));
                        ServiceDocumentQueryResult emptyResults = new ServiceDocumentQueryResult();
                        emptyResults.documentCount = 0L;
                        get.setBody(emptyResults);
                        get.complete();
                        return;
                    }

                    get.setBody(state.results);
                    get.complete();
                }));
        return;
    }

    private void getUsers(Operation parentOp, QuerySpecification filter) {
        UserQueryTaskState userQuery = new UserQueryTaskState();
        userQuery.taskInfo = TaskState.createDirect();
        userQuery.filter = filter;

        sendRequest(Operation.createPost(getHost(), UserQueryTaskService.FACTORY_LINK)
                .setBody(userQuery)
                .setCompletion((op, ex) -> {
                    if (!handleCompletion(parentOp, op, ex)) {
                        return;
                    }

                    UserQueryTaskState result = op.getBody(UserQueryTaskState.class);
                    parentOp.setBody(result.results);
                    parentOp.complete();
                }));
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument d = super.getDocumentTemplate();
        d.documentDescription.name = "Users";
        d.documentDescription.description = "Retrieve users";
        return d;
    }
}