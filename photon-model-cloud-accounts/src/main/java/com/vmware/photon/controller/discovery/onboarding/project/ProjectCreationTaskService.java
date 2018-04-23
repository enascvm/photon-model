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

import static com.vmware.photon.controller.discovery.onboarding.OnboardingUtils.callOperations;
import static com.vmware.photon.controller.discovery.onboarding.OnboardingUtils.cloneResourceGroupState;
import static com.vmware.photon.controller.discovery.onboarding.OnboardingUtils.cloneRoleState;
import static com.vmware.photon.controller.discovery.onboarding.OnboardingUtils.cloneUserGroupState;
import static com.vmware.photon.controller.discovery.onboarding.OnboardingUtils.createRollBackOperation;
import static com.vmware.photon.controller.discovery.onboarding.OnboardingUtils.generateGroupLinkUpdateRequestBody;
import static com.vmware.photon.controller.discovery.onboarding.OnboardingUtils.getActiveUserLink;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.vmware.photon.controller.discovery.common.PhotonControllerCloudAccountUtils;
import com.vmware.photon.controller.discovery.common.UtilizationThreshold;
import com.vmware.photon.controller.discovery.common.services.OrganizationService.OrganizationState;
import com.vmware.photon.controller.discovery.common.services.ProjectService;
import com.vmware.photon.controller.discovery.common.services.ProjectService.ProjectState;
import com.vmware.photon.controller.discovery.common.services.ProjectService.ProjectStatus;
import com.vmware.photon.controller.discovery.common.services.ResourcePoolConfigurationService;
import com.vmware.photon.controller.discovery.common.services.ResourcePoolConfigurationService.ResourcePoolConfigurationRequest;
import com.vmware.photon.controller.discovery.common.services.UserService.UserState;
import com.vmware.photon.controller.discovery.common.utils.ErrorUtil;
import com.vmware.photon.controller.discovery.onboarding.OnboardingErrorCode;
import com.vmware.photon.controller.discovery.onboarding.OnboardingUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.OperationSequence;
import com.vmware.xenon.common.RequestRouter.Route;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.ResourceGroupService;
import com.vmware.xenon.services.common.ResourceGroupService.ResourceGroupState;
import com.vmware.xenon.services.common.RoleService;
import com.vmware.xenon.services.common.RoleService.RoleState;
import com.vmware.xenon.services.common.TaskService;
import com.vmware.xenon.services.common.UserGroupService;
import com.vmware.xenon.services.common.UserGroupService.UserGroupState;

/**
 * Task service to create projects
 */
public class ProjectCreationTaskService
        extends TaskService<ProjectCreationTaskService.ProjectCreationTaskState> {
    public static final String FACTORY_LINK =
            UriPaths.PROVISIONING_PROJECT_TASKS_PREFIX + "/creation-tasks";

    public enum SubStage {
        VALIDATE_CREDENTIALS,
        CREATE_PROJECT_SERVICE,
        CREATE_AUTH_SERVICES,
        START_AUX_SERVICES
    }

    /**
     * project creation task state.
     */
    public static class ProjectCreationTaskState extends TaskService.TaskServiceState {

        @Documentation(description = "Project name")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String projectName;

        @Documentation(description = "The organization the project belongs to")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String organizationLink;

        @Documentation(description = "Project budget")
        public BigDecimal budget;

        @Documentation(description = "Project description")
        public String description;

        @Documentation(description = "Link to the resulting project. This is set by the service and should not be set by the caller")
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String projectLink;

        @Documentation(description = "Project Utilization thresholds by photon-model metrics")
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Map<String, UtilizationThreshold> utilizationThresholds;

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
         * The Org ID for the user creating the project.
         */
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String orgId;

        /**
         * The set to record all rollback operations.
         */
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Set<Operation> transactionSet;
    }

    public ProjectCreationTaskService() {
        super(ProjectCreationTaskState.class);
    }

    @Override
    protected ProjectCreationTaskState validateStartPost(Operation postOp) {
        ProjectCreationTaskState state = super.validateStartPost(postOp);

        String userLink = null;
        if (state != null) {
            userLink = state.userLink;
        }
        // TODO: Telemetry
//        postProjectTelemetry(this, createProjectCreationTableColumns(
//                getActiveUserLink(userLink, postOp), state,
//                TelemetryConstants.TelemetryProjectActionState.REQUESTED));

        if (state == null) {
            return null;
        }
        if (state.organizationLink == null || state.projectName == null) {
            String msg = null;
            if (state.organizationLink == null) {
                msg = "organizationLink needs to be specified";
            } else {
                msg = "projectName needs to be specified";
            }
            postOp.fail(new IllegalArgumentException(msg));
            return null;
        }
        // set the tenantLinks for the service to the org. This is needed
        // at this stage to ensure the service has the right authz context links
        // to startup
        state.tenantLinks = new HashSet<>();
        state.tenantLinks.add(state.organizationLink);

        return state;
    }

    @Override
    protected void initializeState(ProjectCreationTaskState state, Operation postOp) {
        super.initializeState(state, postOp);
        // extract info for the user who initiated this task
        state.userLink = getActiveUserLink(state.userLink, postOp);
        state.taskSubStage = SubStage.VALIDATE_CREDENTIALS;
    }

    @Override
    public void handlePatch(Operation patch) {
        ProjectCreationTaskState body = getBody(patch);
        ProjectCreationTaskState currentState = getState(patch);

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

    private void handleStagePatch(ProjectCreationTaskState currentState) {
        switch (currentState.taskSubStage) {
        case VALIDATE_CREDENTIALS:
            checkUserCreds(currentState);
            break;
        case CREATE_PROJECT_SERVICE:
            createProjectService(currentState);
            break;
        case CREATE_AUTH_SERVICES:
            createAuthServicesForProject(currentState);
            break;
        case START_AUX_SERVICES:
            startAuxServices(currentState);
            break;
        default:
            break;
        }
    }

    private void checkUserCreds(ProjectCreationTaskState currentState) {
        // check to see if the org service is valid and the user is a valid user in the org
        PhotonControllerCloudAccountUtils.checkIfExists(this, currentState.organizationLink, (existsOp) -> {
            OrganizationState organizationState = existsOp.getBody(OrganizationState.class);
            currentState.orgId = organizationState.id;
            Consumer<Operation> onSuccess = (successOp) -> {
                sendSelfPatch(currentState, TaskStage.STARTED, (patchState) -> {
                    patchState.taskSubStage = SubStage.CREATE_PROJECT_SERVICE;
                });
            };
            Consumer<Throwable> onFailure = (failureEx) -> {
                sendSelfFailurePatch(currentState, failureEx.getMessage());
            };
            OnboardingUtils.checkIfUserIsValid(this, currentState.userLink,
                    currentState.organizationLink, false, onSuccess, onFailure);
        }, (notExistsOp) ->
                sendSelfFailurePatch(currentState, "Invalid organizationLink specified"),
                true);
    }

    private void createProjectService(ProjectCreationTaskState currentState) {
        ProjectState projectState = new ProjectState();
        projectState.name = currentState.projectName;
        projectState.budget = currentState.budget;
        projectState.description = currentState.description;
        projectState.status = ProjectStatus.DRAFT;
        projectState.documentSelfLink = OnboardingUtils
                .getProjectSelfLink(currentState.organizationLink,
                        currentState.projectName);
        projectState.tenantLinks = Collections.singleton(currentState.organizationLink);
        projectState.utilizationThresholds = currentState.utilizationThresholds;
        Operation projectOp = Operation
                .createPost(getHost(), ProjectService.FACTORY_LINK)
                .setBody(projectState)
                .setCompletion((o, e) -> {
                    if (o.getStatusCode() == Operation.STATUS_CODE_CONFLICT) {
                        ServiceErrorResponse serviceErrorResponse = ErrorUtil.create(
                                OnboardingErrorCode.DUPLICATE_PROJECT_NAME);
                        currentState.taskInfo.failure = serviceErrorResponse;
                        sendSelfFailurePatch(currentState, ErrorUtil
                                .message(OnboardingErrorCode.DUPLICATE_PROJECT_NAME));
                        return;
                    }
                    if (e != null) {
                        sendSelfFailurePatch(currentState, e.getMessage());
                        return;
                    }
                    ProjectCreationTaskState patchState = new ProjectCreationTaskState();
                    patchState.transactionSet = new HashSet<>();
                    patchState.transactionSet.add(createRollBackOperation(this,
                            projectState.documentSelfLink, null, new ProjectState()));
                    patchState.projectLink =
                            o.getBody(ProjectState.class).documentSelfLink;
                    patchState.taskInfo = new TaskState();
                    patchState.taskInfo.stage = TaskStage.STARTED;
                    patchState.taskSubStage = SubStage.CREATE_AUTH_SERVICES;
                    sendSelfPatch(patchState);
                });
        setAuthorizationContext(OnboardingUtils.addReplicationQuorumHeader(projectOp),
                getSystemAuthorizationContext());
        sendRequest(projectOp);
    }

    private void createAuthServicesForProject(ProjectCreationTaskState currentState) {

        // create user groups for the project - create one for the project admin
        // and another for a non-admin user
        String projectId = UriUtils.getLastPathSegment(currentState.projectLink);
        Set<Operation> userGroupOps = OnboardingUtils.createUserGroups(this, projectId);
        OperationSequence opSequence =
                OperationSequence.create(userGroupOps.stream().toArray(Operation[]::new));
        currentState.transactionSet.addAll(userGroupOps
                .stream()
                .map(userGroupOp -> userGroupOp.getBody(UserGroupState.class))
                .map(userGroupState -> createRollBackOperation(this,
                        UriUtils.buildUriPath(UserGroupService.FACTORY_LINK, userGroupState.documentSelfLink),
                        null, cloneUserGroupState(userGroupState)))
                .collect(Collectors.toSet()));

        // Create xenon authz resource group for all resources scoped to this project
        Operation projectResourceGroup = OnboardingUtils.createResourceGroup(this,
                projectId, QuerySpecification.buildCollectionItemName(
                        ServiceDocumentDescription.FIELD_NAME_TENANT_LINKS),
                UriUtils.buildUriPath(ProjectService.FACTORY_LINK, projectId));
        opSequence = opSequence.next(projectResourceGroup);
        currentState.transactionSet.add(createRollBackOperation(this,
                UriUtils.buildUriPath(ResourceGroupService.FACTORY_LINK, projectId), null,
                cloneResourceGroupState(projectResourceGroup.getBody(ResourceGroupState.class))));

        // create a role tying the project user group to the project resource group
        String userGroupLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                OnboardingUtils.buildAuthzArtifactLink(projectId, false));
        String projectResourceGroupLink = UriUtils.buildUriPath(
                ResourceGroupService.FACTORY_LINK, projectId);
        Operation projectRole = OnboardingUtils.createRole(this, projectId, userGroupLink,
                projectResourceGroupLink);
        opSequence = opSequence.next(projectRole);
        currentState.transactionSet.add(createRollBackOperation(this,
                UriUtils.buildUriPath(RoleService.FACTORY_LINK, projectId), null,
                cloneRoleState(projectRole.getBody(RoleState.class))));

        // patch UserService and add the userGroups created above to the user
        // who invoked this operation
        UserState userState = new UserState();
        userState.userGroupLinks = new HashSet<>();
        userState.userGroupLinks.add(UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                OnboardingUtils.buildAuthzArtifactLink(
                        projectId, false)));
        userState.userGroupLinks.add(UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                OnboardingUtils.buildAuthzArtifactLink(
                        projectId, true)));
        Operation userStateOp = Operation.createPatch(this, currentState.userLink)
                .setBody(userState);
        setAuthorizationContext(userStateOp, getSystemAuthorizationContext());
        opSequence = opSequence.next(OnboardingUtils.addReplicationQuorumHeader(userStateOp));
        currentState.transactionSet.add(createRollBackOperation(this, currentState.userLink,
                generateGroupLinkUpdateRequestBody(null, userState.userGroupLinks), null));

        opSequence.setCompletion(
                (ops, exc) -> {
                    if (exc != null) {
                        callOperations(this, currentState.transactionSet,
                                (operations, failures) -> {
                                    if (failures != null) {
                                        logWarning("Failed to roll back document %s",
                                                Utils.toString(failures.values().iterator().next()));
                                    }
                                    ServiceErrorResponse serviceErrorResponse = ErrorUtil.create(
                                            OnboardingErrorCode.PROJECT_CREATION_TASK_FAILURE);
                                    currentState.taskInfo.failure = serviceErrorResponse;
                                    sendSelfFailurePatch(currentState, ErrorUtil
                                            .message(OnboardingErrorCode.PROJECT_CREATION_TASK_FAILURE));
                                });
                        return;
                    }
                    sendSelfPatch(currentState, TaskStage.STARTED, (patchState) -> {
                        patchState.taskSubStage = SubStage.START_AUX_SERVICES;
                    });
                });
        opSequence.sendWith(this);
    }

    /**
     * Create auxiliary services for this project.
     */
    private void startAuxServices(ProjectCreationTaskState state) {
        List<Operation> ops = new ArrayList<>();

        ops.add(createResourcePool(state));
        OperationJoin.create(ops)
                .setCompletion((o, exs) -> {
                    if (exs != null) {
                        sendSelfFailurePatch(state, exs.values().iterator().next().getMessage());
                        return;
                    }

                    // TODO: Telemetry
//                    postProjectTelemetry(this, createProjectCreationTableColumns(
//                            state.userLink, state,
//                            TelemetryConstants.TelemetryProjectActionState.SUCCEEDED));

                    sendSelfPatch(state, TaskStage.FINISHED, null);
                })
                .sendWith(this);
    }

    private Operation createResourcePool(ProjectCreationTaskState currentState) {
        ResourcePoolConfigurationRequest request = new ResourcePoolConfigurationRequest();
        request.requestType = ResourcePoolConfigurationService.ConfigurationRequestType.CREATE;
        request.projectId = UriUtils.getLastPathSegment(currentState.projectLink);
        request.orgId = currentState.orgId;
        return Operation.createPost(this, ResourcePoolConfigurationService.SELF_LINK)
                .setBody(request);
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument d = super.getDocumentTemplate();
        d.documentDescription.serviceRequestRoutes = new HashMap<>();
        Route route = new Route();
        route.action = Action.POST;
        route.description = "Create a new project and configures the relevant auth services";
        route.requestType = ProjectCreationTaskState.class;
        d.documentDescription.serviceRequestRoutes
                .put(route.action, Collections.singletonList(route));
        return d;
    }
}
