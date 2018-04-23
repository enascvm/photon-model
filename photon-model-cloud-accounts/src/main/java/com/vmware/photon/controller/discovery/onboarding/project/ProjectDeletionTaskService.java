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

package com.vmware.photon.controller.discovery.onboarding.project;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.vmware.photon.controller.discovery.common.PhotonControllerCloudAccountUtils;
import com.vmware.photon.controller.discovery.common.services.ProjectService.ProjectState;
import com.vmware.photon.controller.discovery.common.services.ProjectService.ProjectStatus;
import com.vmware.photon.controller.discovery.common.services.ResourcePoolConfigurationService;
import com.vmware.photon.controller.discovery.common.services.ResourcePoolConfigurationService.ResourcePoolConfigurationRequest;
import com.vmware.photon.controller.discovery.onboarding.OnboardingUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.tasks.ScheduledTaskService;
import com.vmware.photon.controller.model.tasks.TaskUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.RequestRouter.Route;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceStateCollectionUpdateRequest;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.ResourceGroupService;
import com.vmware.xenon.services.common.RoleService;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskService;
import com.vmware.xenon.services.common.UserGroupService;

/**
 * Task service to delete projects
 */
public class ProjectDeletionTaskService
        extends TaskService<ProjectDeletionTaskService.ProjectDeletionTaskState> {
    public static final String FACTORY_LINK =
            UriPaths.PROVISIONING_PROJECT_TASKS_PREFIX + "/delete-tasks";

    private static final int QUERY_RESULT_LIMIT = 100;

    public enum SubStage {
        VALIDATE_CREDENTIALS,
        GET_PROJECT,
        DELETE_PROJECT,
        INITIALIZE_RESOURCE_QUERY,
        GET_RESOURCES,
        UPDATE_AUTHZ_ARTIFACTS
    }

    /**
     * project update task state.
     */
    public static class ProjectDeletionTaskState extends TaskService.TaskServiceState {

        @Documentation(description = "Link of the project to delete")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String projectLink;

        /**
         * authz link for the org hosting this project
         */
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Set<String> tenantLinks;
        /**
         * Tracks the task's substage.
         */
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public SubStage taskSubStage;

        /**
         * Subject that invoked this task
         */
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String userLink;

        /**
         * The org the project belongs to
         */
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String orgLink;

        /**
         * Current status of the project
         */
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public ProjectStatus projectStatus;

        /**
         * cursor for obtaining compute services - this is set for the first time based on
         * the result of a query task and updated on every patch thereafter based on the result
         * object obtained when a GET is issued on the link
         */
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String queryResultLink;
    }

    public ProjectDeletionTaskService() {
        super(ProjectDeletionTaskState.class);
    }

    @Override
    protected ProjectDeletionTaskState validateStartPost(Operation postOp) {
        ProjectDeletionTaskState state = super.validateStartPost(postOp);

        String userLink = null;
        if (state != null) {
            userLink = state.userLink;
        }
        // TODO: Telemetry
//        postProjectTelemetry(this, createProjectDeleteTableColumns(
//                getActiveUserLink(userLink, postOp), state,
//                TelemetryConstants.TelemetryProjectActionState.REQUESTED));

        if (state == null) {
            return null;
        }
        if (state.projectLink == null) {
            postOp.fail(new IllegalArgumentException("projectLink needs to be specified"));
            return null;
        }
        // set the tenantLinks for the service to the project. This is needed
        // at this stage to ensure the service has the right authz context links
        // to startup
        state.tenantLinks = new HashSet<String>();
        state.tenantLinks.add(state.projectLink);
        return state;
    }

    @Override
    protected void initializeState(ProjectDeletionTaskState state, Operation postOp) {
        super.initializeState(state, postOp);
        // extract info for the user who initiated this task
        state.userLink = postOp.getAuthorizationContext().getClaims().getSubject();
        state.taskSubStage = SubStage.VALIDATE_CREDENTIALS;
    }

    @Override
    public void handlePatch(Operation patch) {
        ProjectDeletionTaskState body = getBody(patch);
        ProjectDeletionTaskState currentState = getState(patch);

        if (!validateTransition(patch, currentState, body)) {
            return;
        }
        Utils.mergeWithState(getStateDescription(), currentState, body);
        patch.complete();

        switch (currentState.taskInfo.stage) {
        case STARTED:
            handleStagePatch(currentState);
            break;
        default:
            break;
        }
    }

    private void handleStagePatch(ProjectDeletionTaskState currentState) {
        switch (currentState.taskSubStage) {
        case VALIDATE_CREDENTIALS:
            checkUserCreds(currentState);
            break;
        case GET_PROJECT:
            getProject(currentState);
            break;
        case DELETE_PROJECT:
            deleteProject(currentState);
            break;
        case INITIALIZE_RESOURCE_QUERY:
            initializeQuery(currentState);
            break;
        case GET_RESOURCES:
            getResources(currentState);
            break;
        case UPDATE_AUTHZ_ARTIFACTS:
            updateAuthzArtifacts(currentState);
            break;
        default:
            break;
        }
    }

    private void checkUserCreds(ProjectDeletionTaskState currentState) {
        // check to see if the project service is valid and the user is a valid admin for the project
        PhotonControllerCloudAccountUtils.checkIfExists(this, currentState.projectLink, (existsOp) -> {
            Consumer<Operation> onSuccess = (successOp) -> {
                sendSelfPatch(currentState, TaskStage.STARTED, (patchState) -> {
                    patchState.taskSubStage = SubStage.GET_PROJECT;
                });
            };
            Consumer<Throwable> onFailure = (failureEx) -> {
                sendSelfFailurePatch(currentState, failureEx.getMessage());
            };

            Consumer<Throwable> onAuthFailure = ProjectUtil.createFailureConsumerToCheckOrgAdminAccess(
                    this, currentState.projectLink, currentState.userLink,
                    onSuccess, onFailure);

            OnboardingUtils.checkIfUserIsValid(this, currentState.userLink,
                    currentState.projectLink, true, onSuccess, onAuthFailure);
        }, (notExistsOp) -> {
                sendSelfFailurePatch(currentState, "Invalid projectLink specified");
            }, true);
    }

    private void getProject(ProjectDeletionTaskState currentState) {
        Operation getProjectOp = Operation
                .createGet(UriUtils.buildUri(getHost(), currentState.projectLink))
                .setReferer(getHost().getUri())
                .setCompletion((getOp, getEx) -> {
                    if (getEx != null) {
                        sendSelfFailurePatch(currentState, getEx.getMessage());
                        return;
                    }
                    ProjectState projectState =
                            getOp.getBody(ProjectState.class);
                    sendSelfPatch(currentState, TaskStage.STARTED, (patchState) -> {
                        patchState.orgLink =
                                PhotonControllerCloudAccountUtils.getOrgId(projectState.tenantLinks);
                        patchState.projectStatus = projectState.status;
                        patchState.taskSubStage = SubStage.DELETE_PROJECT;
                    });
                });
        setAuthorizationContext(getProjectOp, getSystemAuthorizationContext());
        sendRequest(getProjectOp);
    }

    private void deleteProject(ProjectDeletionTaskState currentState) {
        if (currentState.projectStatus != ProjectStatus.RETIRED) {
            sendSelfFailurePatch(currentState, "Project must be retired before deleting");
            return;
        }
        List<Operation> deleteOperations = new ArrayList<Operation>();

        // Delete project operation
        Operation deleteProjectOp = Operation
                .createDelete(UriUtils.buildUri(getHost(), currentState.projectLink))
                .setReferer(getHost().getUri());
        setAuthorizationContext(deleteProjectOp, getSystemAuthorizationContext());
        deleteOperations.add(deleteProjectOp);

        // Delete project stats aggregation task
        deleteOperations.add(Operation.createDelete(this,
                UriUtils.buildUriPath(ScheduledTaskService.FACTORY_LINK,
                        ProjectUtil.getCostAggregationTaskId(currentState.projectLink)))
                .setReferer(getHost().getUri()));
        ResourcePoolConfigurationRequest request = new ResourcePoolConfigurationRequest();
        request.requestType = ResourcePoolConfigurationService.ConfigurationRequestType.TEARDOWN;
        request.projectId = UriUtils.getLastPathSegment(currentState.projectLink);
        deleteOperations.add(Operation.createPost(this, ResourcePoolConfigurationService.SELF_LINK)
                .setBody(request).setReferer(getHost().getUri()));

        OperationJoin.JoinedCompletionHandler joinCompletion = (ops, exc) -> {
            if (exc != null) {
                sendSelfFailurePatch(currentState, exc.values().iterator().next().getMessage());
                return;
            }
            sendSelfPatch(currentState, TaskStage.STARTED, (patchState) -> {
                patchState.taskSubStage = SubStage.INITIALIZE_RESOURCE_QUERY;
            });
        };
        OperationJoin joinOp = OperationJoin.create(deleteOperations);
        joinOp.setCompletion(joinCompletion);
        joinOp.sendWith(getHost());
    }

    private void initializeQuery(ProjectDeletionTaskState currentState) {
        Query resourceQuery = Query.Builder.create()
                .addCollectionItemClause(
                        ServiceDocumentDescription.FIELD_NAME_TENANT_LINKS,
                        currentState.projectLink)
                .build();

        QueryTask qTask = QueryTask.Builder.createDirectTask()
                .setQuery(resourceQuery)
                .setResultLimit(QUERY_RESULT_LIMIT)
                .build();

        sendRequest(Operation
                .createPost(this, ServiceUriPaths.CORE_LOCAL_QUERY_TASKS)
                .setBody(qTask)
                .setConnectionSharing(true)
                .setCompletion((queryOp, queryEx) -> {
                    if (queryEx != null) {
                        sendSelfFailurePatch(currentState, queryEx.getMessage());
                        return;
                    }
                    QueryTask rsp = queryOp.getBody(QueryTask.class);
                    ProjectDeletionTaskState patchBody = new ProjectDeletionTaskState();
                    if (rsp.results.nextPageLink == null) {
                        patchBody.taskInfo = TaskUtils.createTaskState(TaskStage.STARTED);
                        patchBody.taskSubStage = SubStage.UPDATE_AUTHZ_ARTIFACTS;
                    } else {
                        patchBody.taskInfo = TaskUtils.createTaskState(TaskStage.STARTED);
                        patchBody.taskSubStage = SubStage.GET_RESOURCES;
                        patchBody.queryResultLink = rsp.results.nextPageLink;
                    }
                    sendSelfPatch(patchBody);
                }));
    }

    private void getResources(ProjectDeletionTaskState currentState) {
        sendRequest(Operation
                .createGet(UriUtils.buildUri(getHost(), currentState.queryResultLink))
                .setCompletion(
                        (getOp, getEx) -> {
                            if (getEx != null) {
                                sendSelfFailurePatch(currentState, getEx.getMessage());
                                return;
                            }
                            QueryTask page = getOp.getBody(QueryTask.class);
                            if (page.results.documentLinks.size() == 0) {
                                sendSelfPatch(currentState, TaskStage.STARTED, (patchState) -> {
                                    patchState.taskSubStage = SubStage.UPDATE_AUTHZ_ARTIFACTS;
                                });
                                return;
                            }
                            // process resources
                            List<Operation> patchOperations = new ArrayList<Operation>();
                            Map<String, Collection<Object>> collectionsMap = new HashMap<>();
                            Collection<Object> tenantLinksToRemove = new ArrayList<>(Arrays.asList(
                                    currentState.projectLink));
                            collectionsMap.put(ServiceDocumentDescription.FIELD_NAME_TENANT_LINKS,
                                    tenantLinksToRemove);
                            ServiceStateCollectionUpdateRequest collectionRemovalBody =
                                    ServiceStateCollectionUpdateRequest
                                            .create(null, collectionsMap);
                            for (String computeLink : page.results.documentLinks) {
                                patchOperations.add(Operation
                                        .createPatch(UriUtils.buildUri(getHost(), computeLink))
                                        .setBody(collectionRemovalBody)
                                        .setReferer(getHost().getUri()));
                            }
                            OperationJoin.JoinedCompletionHandler joinCompletion = (ops, exc) -> {
                                if (exc != null) {
                                    sendSelfFailurePatch(currentState,
                                            exc.values().iterator().next().getMessage());
                                    return;
                                }
                                ProjectDeletionTaskState patchBody = new ProjectDeletionTaskState();
                                if (page.results.nextPageLink == null) {
                                    patchBody.taskInfo = TaskUtils
                                            .createTaskState(TaskStage.STARTED);
                                    patchBody.taskSubStage = SubStage.UPDATE_AUTHZ_ARTIFACTS;
                                } else {
                                    patchBody.taskInfo = TaskUtils
                                            .createTaskState(TaskStage.STARTED);
                                    patchBody.taskSubStage = SubStage.GET_RESOURCES;
                                    patchBody.queryResultLink = page.results.nextPageLink;
                                }
                                sendSelfPatch(patchBody);
                            };
                            OperationJoin joinOp = OperationJoin.create(patchOperations);
                            joinOp.setCompletion(joinCompletion);
                            joinOp.sendWith(getHost());
                        }));
    }

    private void updateAuthzArtifacts(ProjectDeletionTaskState currentState) {
        List<String> serviceLinks = new ArrayList<String>();
        String projectId = UriUtils.getLastPathSegment(currentState.projectLink);
        serviceLinks.add(
                OnboardingUtils.buildAuthzArtifactLink(
                        UriUtils.buildUriPath(UserGroupService.FACTORY_LINK, projectId), true));
        serviceLinks.add(
                OnboardingUtils.buildAuthzArtifactLink(
                        UriUtils.buildUriPath(UserGroupService.FACTORY_LINK, projectId), false));
        serviceLinks.add(
                UriUtils.buildUriPath(ResourceGroupService.FACTORY_LINK, projectId));
        serviceLinks.add(
                UriUtils.buildUriPath(RoleService.FACTORY_LINK, projectId));
        List<Operation> authzOperations = new ArrayList<Operation>();
        for (String serviceLink : serviceLinks) {
            Operation op = Operation.createDelete(UriUtils.buildUri(getHost(), serviceLink))
                    .setReferer(getHost().getUri());
            setAuthorizationContext(op, getSystemAuthorizationContext());
            authzOperations.add(op);
        }
        OperationJoin.JoinedCompletionHandler joinCompletion = (ops, exc) -> {
            if (exc != null) {
                sendSelfFailurePatch(currentState, exc.values().iterator().next().getMessage());
                return;
            }

            // TODO: Telemetry
//            postProjectTelemetry(this, createProjectDeleteTableColumns(
//                    currentState.userLink, currentState,
//                    TelemetryConstants.TelemetryProjectActionState.SUCCEEDED));

            sendSelfPatch(currentState, TaskStage.FINISHED, null);
        };
        OperationJoin joinOp = OperationJoin.create(authzOperations);
        joinOp.setCompletion(joinCompletion);
        joinOp.sendWith(getHost());
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument d = super.getDocumentTemplate();
        d.documentDescription.serviceRequestRoutes = new HashMap<>();
        Route route = new Route();
        route.action = Action.POST;
        route.description = "Deletes an existing project";
        route.requestType = ProjectDeletionTaskState.class;
        d.documentDescription.serviceRequestRoutes
                .put(route.action, Collections.singletonList(route));
        return d;
    }
}
