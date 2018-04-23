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

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.vmware.photon.controller.discovery.common.PhotonControllerCloudAccountUtils;
import com.vmware.photon.controller.discovery.common.UtilizationThreshold;
import com.vmware.photon.controller.discovery.common.services.ProjectService;
import com.vmware.photon.controller.discovery.common.services.ProjectService.ProjectState;
import com.vmware.photon.controller.discovery.common.services.ProjectService.ProjectStatus;
import com.vmware.photon.controller.discovery.onboarding.OnboardingUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.RequestRouter.Route;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.TaskService;

/**
 * Task service to update projects
 */
public class ProjectUpdateTaskService extends TaskService<ProjectUpdateTaskService.ProjectUpdateTaskState> {

    public static final String FACTORY_LINK = UriPaths.PROVISIONING_PROJECT_TASKS_PREFIX + "/update-tasks";


    public enum SubStage {
        VALIDATE_CREDENTIALS,
        GET_PROJECT_SERVICE,
        UPDATE_PROJECT_SERVICE
    }

    /**
     * project update task state.
     */
    public static class ProjectUpdateTaskState extends TaskService.TaskServiceState {

        @Documentation(description = "Link of the project to update")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String projectLink;

        @Documentation(description = "Project name")
        public String projectName;

        @Documentation(description = "Project budget")
        public BigDecimal budget;

        @Documentation(description = "Project description")
        public String description;

        @Documentation(description = "Project status")
        public ProjectStatus status;

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
         * Current status of the project
         */
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public ProjectStatus projectStatus;
    }

    public ProjectUpdateTaskService() {
        super(ProjectUpdateTaskState.class);
    }

    @Override
    protected ProjectUpdateTaskState validateStartPost(Operation postOp) {
        ProjectUpdateTaskState state = super.validateStartPost(postOp);

        String userLink = null;
        if (state != null) {
            userLink = state.userLink;
        }

        // TODO: Telemetry
//        postProjectTelemetry(this,
//                createProjectUpdateTableColumns(getActiveUserLink(userLink, postOp), state,
//                        TelemetryConstants.TelemetryProjectActionState.REQUESTED));

        if (state == null) {
            return null;
        }
        if (state.projectLink == null) {
            postOp.fail(new IllegalArgumentException("projectLink needs to be specified"));
            return null;
        }
        // set the tenantLinks for the servie to the project. This is needed
        // at this stage to ensure the service has the right authz context links
        // to startup
        state.tenantLinks = new HashSet<>();
        state.tenantLinks.add(state.projectLink);
        return state;
    }

    @Override
    protected void initializeState(ProjectUpdateTaskState state, Operation postOp) {
        super.initializeState(state, postOp);
        // extract info for the user who initiated this task
        state.userLink = postOp.getAuthorizationContext().getClaims().getSubject();
        state.taskSubStage = SubStage.VALIDATE_CREDENTIALS;
    }

    @Override
    public void handlePatch(Operation patch) {
        ProjectUpdateTaskState body = getBody(patch);
        ProjectUpdateTaskState currentState = getState(patch);

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

    private void handleStagePatch(ProjectUpdateTaskState currentState) {
        switch (currentState.taskSubStage) {
        case VALIDATE_CREDENTIALS:
            checkUserCreds(currentState);
            break;
        case GET_PROJECT_SERVICE:
            getProjectService(currentState);
            break;
        case UPDATE_PROJECT_SERVICE:
            updateProjectService(currentState);
            break;
        default:
            break;
        }
    }

    private void checkUserCreds(ProjectUpdateTaskState currentState) {
        // check to see if the project service is valid and the user is a valid admin for the project
        PhotonControllerCloudAccountUtils.checkIfExists(this, currentState.projectLink, (existsOp) -> {
            Consumer<Operation> onSuccess = (successOp) -> {
                sendSelfPatch(currentState, TaskStage.STARTED, (patchState) -> {
                    patchState.taskSubStage = SubStage.GET_PROJECT_SERVICE;
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

    private void getProjectService(ProjectUpdateTaskState currentState) {
        Operation getOp = Operation
                .createGet(getHost(), currentState.projectLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        sendSelfFailurePatch(currentState, e.getMessage());
                        return;
                    }
                    ProjectState projectState = o.getBody(ProjectState.class);
                    sendSelfPatch(currentState, TaskStage.STARTED, (patchState) -> {
                        patchState.taskSubStage = SubStage.UPDATE_PROJECT_SERVICE;
                        patchState.projectStatus = projectState.status;
                    });
                });
        setAuthorizationContext(OnboardingUtils.addReplicationQuorumHeader(getOp), getSystemAuthorizationContext());
        sendRequest(getOp);
    }

    private void updateProjectService(ProjectUpdateTaskState currentState) {
        ProjectState projectState = new ProjectState();
        projectState.name = currentState.projectName;
        projectState.budget = currentState.budget;
        projectState.description = currentState.description;
        if (currentState.status != null) {
            if (!ProjectService.isValidStatusUpdate(currentState.projectStatus, currentState.status)) {
                sendSelfFailurePatch(currentState, "Invalid status transition specified");
                return;
            }
            projectState.status = currentState.status;
        }
        projectState.utilizationThresholds = currentState.utilizationThresholds;
        Operation userOp = Operation
                .createPatch(getHost(), currentState.projectLink)
                .setBody(projectState)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        sendSelfFailurePatch(currentState, e.getMessage());
                        return;
                    }

                    // TODO: Telemetry
//                    postProjectTelemetry(this, createProjectUpdateTableColumns(
//                            currentState.userLink, currentState,
//                            TelemetryConstants.TelemetryProjectActionState.SUCCEEDED));

                    sendSelfPatch(currentState, TaskStage.FINISHED, null);
                });
        setAuthorizationContext(OnboardingUtils.addReplicationQuorumHeader(userOp), getSystemAuthorizationContext());
        sendRequest(userOp);
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument d = super.getDocumentTemplate();
        d.documentDescription.serviceRequestRoutes = new HashMap<>();
        Route route = new Route();
        route.action = Action.POST;
        route.description = "Updates an existing project";
        route.requestType = ProjectUpdateTaskState.class;
        d.documentDescription.serviceRequestRoutes
                .put(route.action, Collections.singletonList(route));
        return d;
    }
}
