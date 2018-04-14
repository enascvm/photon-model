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

package com.vmware.photon.controller.discovery.group;

import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.IS_USER_CREATED;
import static com.vmware.photon.controller.discovery.common.utils.InventoryQueryUtils.startInventoryQueryTask;
import static com.vmware.photon.controller.model.UriPaths.GROUP_QUERY_TASK_SERVICE;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.vmware.photon.controller.discovery.cloudaccount.utils.FilterUtils;
import com.vmware.photon.controller.discovery.cloudaccount.utils.QueryHelper;
import com.vmware.photon.controller.discovery.cloudaccount.utils.QueryUtils;
import com.vmware.photon.controller.discovery.common.services.UserContextQueryService;
import com.vmware.photon.controller.discovery.common.services.UserContextQueryService.UserContext;
import com.vmware.photon.controller.discovery.group.GroupQueryTaskService.GroupQueryTaskState;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.RequestRouter.Route;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskFactoryService;
import com.vmware.xenon.services.common.TaskService;

/**
 * Task service to retrieve group information in an API-friendly way. The task service processes
 * incoming requests and executes appropriate query. A successful task completion populates the
 * results field in {@link GroupQueryTaskState}. The clients can do GET operation on the
 * next/prev page links to get more results.
 */
public class GroupQueryTaskService extends TaskService<GroupQueryTaskState> {
    public static final String FACTORY_LINK = GROUP_QUERY_TASK_SERVICE;

    public static final String PROPERTY_NAME_QUERY_RESULT_LIMIT =
            UriPaths.PROPERTY_PREFIX + "GroupQueryTaskService.QUERY_RESULT_LIMIT";

    /**
     * The keys to this map are the supported filter propertyNames (based off the API-friendly
     * model).
     */
    public static final Set<String> SUPPORTED_FILTER_SET;

    /**
     * The keys to this map are the supported sort propertyNames (based off the API-friendly
     * model), in similar fashion to {@link #SUPPORTED_FILTER_SET}.
     */
    public static final Set<String> SUPPORTED_SORT_SET;

    static {
        Set<String> filterSet = new HashSet<>();
        filterSet.add(ResourceState.FIELD_NAME_NAME);

        // GroupApiService uses a filter on documentSelfLink to support a direct GET
        filterSet.add(ServiceDocument.FIELD_NAME_SELF_LINK);
        filterSet.add(getIsUserCreatedCustomProp());

        SUPPORTED_FILTER_SET = Collections.unmodifiableSet(filterSet);

        Set<String> sortSet = new HashSet<>();
        sortSet.add(ResourceState.FIELD_NAME_NAME);

        SUPPORTED_SORT_SET = Collections.unmodifiableSet(sortSet);
    }

    /** The state for the group query task service. */
    public static class GroupQueryTaskState extends TaskService.TaskServiceState {

        @Documentation(description = "The query specification for filtering groups")
        public QuerySpecification filter;

        @Documentation(description = "The tenantLink to filter by")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String tenantLink;

        @Documentation(description = "This is only for internal usage. It keeps track of internal stages")
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public SubStage subStage;

        @Documentation(description = "The groups result set")
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public ServiceDocumentQueryResult results;

        @Documentation(description = "The authorization links associated with this request.")
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public List<String> tenantLinks;
    }

    /** Substages for task processing. */
    enum SubStage {
        /** Stage to query the internal {@link ResourceGroupState} service documents. */
        QUERY_GROUPS,

        /** Stage to indicate success. */
        SUCCESS
    }

    public static FactoryService createFactory() {
        TaskFactoryService fs = new TaskFactoryService(GroupQueryTaskState.class) {
            @Override
            public Service createServiceInstance() throws Throwable {
                return new GroupQueryTaskService();
            }
        };
        fs.setPeerNodeSelectorPath(ServiceUriPaths.DEFAULT_1X_NODE_SELECTOR);
        return fs;
    }

    public GroupQueryTaskService() {
        super(GroupQueryTaskState.class);
        toggleOption(ServiceOption.REPLICATION, true);
        toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handleStart(Operation taskOperation) {
        GroupQueryTaskState state;
        if (!taskOperation.hasBody()) {
            state = new GroupQueryTaskState();
            taskOperation.setBody(state);
        }

        GroupQueryTaskState body = taskOperation.getBody(GroupQueryTaskState.class);
        if (body.tenantLinks != null && !body.tenantLinks.isEmpty()) {
            super.handleStart(taskOperation);
            return;
        }
        if (taskOperation.getAuthorizationContext().isSystemUser()) {
            super.handleStart(taskOperation);
            return;
        } else {
            Operation.createGet(this, UserContextQueryService.SELF_LINK)
                    .setCompletion((op, ex) -> {
                        if (ex != null) {
                            taskOperation.fail(ex);
                            return;
                        }
                        UserContext userContext = op.getBody(UserContext.class);
                        body.tenantLinks = userContext.projects.stream()
                                .map(project -> project.documentSelfLink)
                                .collect(Collectors.toList());
                        taskOperation.setBody(body);
                        super.handleStart(taskOperation);
                    })
                    .sendWith(this);
        }
    }

    @Override
    protected void initializeState(GroupQueryTaskState task, Operation taskOperation) {
        task.subStage = SubStage.QUERY_GROUPS;
        if (task.taskInfo == null) {
            task.taskInfo = TaskState.create();
        }
        super.initializeState(task, taskOperation);
    }

    @Override
    public void handlePatch(Operation patch) {
        GroupQueryTaskState currentTask = getState(patch);
        GroupQueryTaskState patchBody = getBody(patch);

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
    private void handleSubStage(GroupQueryTaskState state) {
        switch (state.subStage) {
        case QUERY_GROUPS:
            queryGroups(state, SubStage.SUCCESS);
            break;
        case SUCCESS:
            sendSelfPatch(state, TaskStage.FINISHED, null);
            break;
        default:
            throw new IllegalStateException("Unknown stage encountered: " + state.subStage);
        }
    }

    /**
     * If {@code state.filter} was provided by the user, then use it as a basis for the query we
     * send in our QueryTask.
     */
    private QuerySpecification buildQuerySpecification(GroupQueryTaskState state) {
        QuerySpecification querySpec;
        if (state.filter != null) {
            querySpec = state.filter;
            Query.Builder transformedQueryBuilder = Query.Builder.create();

            FilterUtils.findPropertyNameMatch(querySpec.query, SUPPORTED_FILTER_SET, query -> {
                query.setTermPropertyName(query.term.propertyName);
                transformedQueryBuilder.addClause(query);
            });
            querySpec.query = transformedQueryBuilder.build();
        } else {
            querySpec = new QuerySpecification();
        }
        return querySpec;
    }

    private QueryTask buildGroupsQuery(GroupQueryTaskState state) {
        QuerySpecification querySpec = buildQuerySpecification(state);

        // We always want to expand UNLESS the client specifically is only interested in a count
        if (!querySpec.options.contains(QueryOption.COUNT)) {
            querySpec.options.add(QueryOption.EXPAND_CONTENT);
            querySpec.resultLimit = QueryUtils.getResultLimit(state.filter,
                    PROPERTY_NAME_QUERY_RESULT_LIMIT);
        }
        if (!querySpec.options.contains(QueryOption.INDEXED_METADATA)) {
            querySpec.options.add(QueryOption.INDEXED_METADATA);
        }
        querySpec.query.addBooleanClause(Query.Builder.create()
                .addKindFieldClause(ResourceGroupState.class)
                .build());

        // the request is for getting list by specific tenant
        if (state.tenantLink != null) {
            querySpec.query.addBooleanClause(Query.Builder.create()
                    .addCollectionItemClause(ResourceState.FIELD_NAME_TENANT_LINKS,
                            state.tenantLink)
                    .build());
        }

        if (querySpec.sortTerm != null && querySpec.sortTerm.propertyName != null) {
            if (!SUPPORTED_SORT_SET.contains(querySpec.sortTerm.propertyName)) {
                logWarning("Sort term '%s' not supported by groups. "
                                + "The query will try to proceed, but sorting might not be applied.",
                        querySpec.sortTerm.propertyName);
            }
        }

        QueryTask task = QueryTask.create(querySpec).setDirect(true);
        task.tenantLinks = state.tenantLinks;
        return task;
    }

    private void queryGroups(GroupQueryTaskState state, SubStage nextStage) {
        QueryTask queryTask = null;
        try {
            queryTask = buildGroupsQuery(state);
        } catch (Exception e) {
            logWarning("Error %s encountered in building the groups query task", e.getMessage());
            handleError(state, e);
        }

        startInventoryQueryTask(this, queryTask)
                .whenComplete((task, err) -> {
                    if (err != null) {
                        handleError(state, err);
                        return;
                    }

                    ServiceDocumentQueryResult queryResult = task.results;

                    ServiceDocumentQueryResult results = new ServiceDocumentQueryResult();
                    results.documentCount = queryResult.documentCount;

                    if (queryResult.nextPageLink != null && !queryResult.nextPageLink.isEmpty()) {
                        GroupQueryPageService pageService = new GroupQueryPageService(
                                queryResult.nextPageLink,
                                state.documentExpirationTimeMicros,
                                state.tenantLinks);

                        results.nextPageLink = QueryHelper.startStatelessPageService(this,
                                GroupQueryPageService.SELF_LINK_PREFIX,
                                pageService,
                                state.documentExpirationTimeMicros,
                                failure -> handleError(state, failure));
                    }

                    if (queryResult.prevPageLink != null && !queryResult.prevPageLink.isEmpty()) {
                        GroupQueryPageService pageService = new GroupQueryPageService(
                                queryResult.prevPageLink,
                                state.documentExpirationTimeMicros,
                                state.tenantLinks);

                        results.prevPageLink = QueryHelper
                                .startStatelessPageService(this,
                                        GroupQueryPageService.SELF_LINK_PREFIX,
                                        pageService,
                                        state.documentExpirationTimeMicros,
                                        failure -> handleError(state, failure));
                    }
                    state.results = results;
                    state.subStage = nextStage;
                    sendSelfPatch(state);
                });
    }

    private void handleError(GroupQueryTaskState state, Throwable ex) {
        logWarning("Failed at sub-stage %s with exception: %s", state.subStage,
                Utils.toString(ex));
        sendSelfFailurePatch(state, ex.getLocalizedMessage());
    }

    private static String getIsUserCreatedCustomProp() {
        return QuerySpecification.buildCompositeFieldName(
                ResourceState.FIELD_NAME_CUSTOM_PROPERTIES, IS_USER_CREATED);
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument d = super.getDocumentTemplate();
        d.documentDescription.serviceRequestRoutes = new HashMap<>();
        Route route = new Route();
        route.action = Action.POST;
        route.description = "Query groups information";
        route.requestType = GroupQueryTaskService.class;
        d.documentDescription.serviceRequestRoutes
                .put(route.action, Collections.singletonList(route));
        return d;
    }
}