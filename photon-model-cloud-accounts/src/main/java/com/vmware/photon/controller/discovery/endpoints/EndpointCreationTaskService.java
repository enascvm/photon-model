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

package com.vmware.photon.controller.discovery.endpoints;

import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.CREDENTIALS_REQUIRED;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_NAME_REQUIRED;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_ORG_LINK_REQUIRED;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_OWNER_ADDITIONAL_FAILED;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_TAG_NULL_EMPTY;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_TYPE_REQUIRED;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.INVALID_COST_USAGE_PARAMS_ERROR;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.INVALID_ENDPOINT_OWNER;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.INVALID_ENDPOINT_TYPE;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.INVALID_ORG_LINK;
import static com.vmware.photon.controller.discovery.cloudaccount.utils.ClusterUtils.createInventoryUri;
import static com.vmware.photon.controller.discovery.common.utils.InventoryQueryUtils.startInventoryQueryTask;
import static com.vmware.photon.controller.discovery.common.utils.StringUtil.isEmpty;
import static com.vmware.photon.controller.discovery.endpoints.CostUsageReportCreationUtils.deleteCostAndUsageConfiguration;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BUCKET_PREFIX;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_CI_COSTUSAGE_REPORT_NAME;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.buildEndpointAdapterUri;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.buildEndpointAuthzArtifactSelfLink;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.buildTagsQuery;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.failOperation;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.initializeDataInitializationTaskService;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.isOperationCredentialsSet;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.reconstructCustomProperties;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.reconstructEndpointProperties;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.triggerOptionalAdapterSchedulingService;
import static com.vmware.photon.controller.discovery.onboarding.OnboardingUtils.addReplicationQuorumHeader;
import static com.vmware.photon.controller.discovery.onboarding.OnboardingUtils.getDefaultProjectSelfLink;
import static com.vmware.photon.controller.model.UriPaths.ENDPOINT_CREATION_TASK_SERVICE;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;

import com.vmware.photon.controller.discovery.common.CloudAccountConstants.UpdateAction;
import com.vmware.photon.controller.discovery.common.CollectionStringFieldUpdate;
import com.vmware.photon.controller.discovery.common.TagViewState;
import com.vmware.photon.controller.discovery.common.TaskHelper;
import com.vmware.photon.controller.discovery.common.services.OrganizationService.OrganizationState;
import com.vmware.photon.controller.discovery.common.services.UserContextQueryService;
import com.vmware.photon.controller.discovery.common.services.UserContextQueryService.UserContext;
import com.vmware.photon.controller.discovery.common.services.UserService;
import com.vmware.photon.controller.discovery.common.services.UserService.UserState;
import com.vmware.photon.controller.discovery.common.utils.ErrorUtil;
import com.vmware.photon.controller.discovery.endpoints.EndpointCreationTaskService.EndpointCreationTaskState;
import com.vmware.photon.controller.discovery.onboarding.OnboardingUtils;
import com.vmware.photon.controller.model.adapters.util.TagsUtil;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService.EndpointAllocationTaskState;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.xenon.common.AuthUtils;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.ResourceGroupService;
import com.vmware.xenon.services.common.ResourceGroupService.ResourceGroupState;
import com.vmware.xenon.services.common.RoleService;
import com.vmware.xenon.services.common.RoleService.Policy;
import com.vmware.xenon.services.common.RoleService.RoleState;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskFactoryService;
import com.vmware.xenon.services.common.TaskService;
import com.vmware.xenon.services.common.UserGroupService;
import com.vmware.xenon.services.common.UserGroupService.UserGroupState;

/**
 * Task to create endpoints.
 */
public class EndpointCreationTaskService extends TaskService<EndpointCreationTaskState> {
    public static final String FACTORY_LINK = ENDPOINT_CREATION_TASK_SERVICE;

    /**
     * The endpoint creation task state.
     */
    public static class EndpointCreationTaskState extends TaskService.TaskServiceState {

        @Documentation(description = "The name of the endpoint.")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.REQUIRED),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public String name;

        @Documentation(description = "The description of the endpoint.")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.OPTIONAL),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public String description;

        @Documentation(description = "Endpoint type of the endpoint instance - aws, azure, etc.")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.REQUIRED),
                @UsageOption(option = PropertyUsageOption.SINGLE_ASSIGNMENT) })
        public String type;

        @Documentation(description = "The org link.")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.REQUIRED),
                @UsageOption(option = PropertyUsageOption.SINGLE_ASSIGNMENT) })
        public String orgLink;

        @Documentation(description = "Endpoint specific properties.")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.OPTIONAL),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public Map<String, String> endpointProperties;

        @Documentation(description = "Stores custom, service-related properties about the cloud account")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.OPTIONAL),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public Map<String, String> customProperties;

        @Documentation(description = "Endpoint's credentials")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.OPTIONAL),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public Credentials credentials;

        @Documentation(
                description = "URI reference to the adapter used to validate and enhance the endpoint data.")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.OPTIONAL),
                @UsageOption(option = PropertyUsageOption.SINGLE_ASSIGNMENT),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)})
        public URI adapterReference;

        @Documentation(description = "The created endpoint link")
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String endpointLink;

        @Documentation(description = "The created endpoint state")
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public EndpointState endpointState;

        @Documentation(description = "Indicates a mock request")
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public boolean isMock = false;

        @Documentation(description = "This is only for internal usage. It keeps track of internal stages.")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public SubStage subStage;

        @Documentation(description = "The user invoking this task.")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.SINGLE_ASSIGNMENT),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public String userLink;

        @Documentation(description = "The tenant links.")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public Set<String> tenantLinks;

        @Documentation(description = "Created By User")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL),
                @UsageOption(option = PropertyUsageOption.SERVICE_USE) })
        public UserState createdByUser;

        @Documentation(description = "Set of services using the endpoint (case insensitive)")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.OPTIONAL),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public Set<String> services;

        @Documentation(description = "Tags to be associated with the endpoint.")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.OPTIONAL),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public Set<TagViewState> tags;

        @Documentation(description = "Set of tagLinks to associate with the endpoint.")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public Set<String> tagLinks;

        @Documentation(description = "Map of tagLinks and tag states to associate with the endpoint.")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public Map<String, TagState> tagsMap;

        @Documentation(description = "Emails of owners.")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.OPTIONAL),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public Set<String> ownerEmails;

        @Documentation(description = "SelfLinks of owners.")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public Set<String> ownerLinks;

        @Documentation(description = "An optional flag to disable resource discovery immediately after endpoint creation")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.OPTIONAL),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL),
                @UsageOption(option = PropertyUsageOption.SERVICE_USE) })
        public Boolean skipInitialDataInitialization = false;
    }

    /**
     * Endpoint creation sub-stages.
     */
    public enum SubStage {
        /**
         * Verify org access.
         */
        VERIFY_ACCESS,

        /**
         * Validate Endpoint before creating
         */
        VALIDATE_ENDPOINT,

        /**
         * If the endpoint creation contains a s3bucketName, s3prefix and reportName, this stage
         * configures the cost and usage report on the endpoint bucket
         */
        CREATE_COST_USAGE_REPORT,

        /**
         * Validate that endpoint owners are valid users and part of the org.
         */
        VALIDATE_OWNERS,

        /**
         * Each user will have a user group, resource group and role that identifies all endpoints
         * a user has ownership of. This sub stage creates those authz artifacts if they don't
         * already exist.
         */
        CREATE_PERMISSIONS,

        /**
         * If the authz artifacts were just created, this stage ensures the User has the up-to-date
         * references to all their user group links.
         */
        UPDATE_USERS,

        /**
         * If the endpoint creation request contains tags, this stage creates all tags that
         * will get associated to the endpoint. Some of the tags may already be present while others
         * will need to get created.
         */
        FIND_CREATE_TAGS,

        /**
         * Create endpoint based on resource group link as authz context.
         */
        CREATE_ENDPOINT,

        /**
         * Pass the data to TelemetryUtils for cloud endpoint addition
         */
        POST_TO_TELEMETRY,

        /**
         * Initialize Data
         */
        INITIALIZE_DATA,

        /**
         * Successful creation of endpoint.
         */
        SUCCESS,

        /**
         * Error while creating endpoint.
         */
        ERROR
    }

    public static FactoryService createFactory() {
        TaskFactoryService fs = new TaskFactoryService(EndpointCreationTaskState.class) {
            @Override
            public Service createServiceInstance() throws Throwable {
                return new EndpointCreationTaskService();
            }
        };
        fs.setPeerNodeSelectorPath(ServiceUriPaths.DEFAULT_1X_NODE_SELECTOR);
        return fs;
    }

    public EndpointCreationTaskService() {
        super(EndpointCreationTaskState.class);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handleStart(Operation taskOperation) {
        final EndpointCreationTaskState state;
        if (taskOperation.hasBody()) {
            state = taskOperation.getBody(EndpointCreationTaskState.class);
        } else {
            state = new EndpointCreationTaskState();
            taskOperation.setBody(state);
        }

        if (state.tenantLinks != null && !state.tenantLinks.isEmpty()) {
            super.handleStart(taskOperation);
            return;
        }

        OnboardingUtils.getProjectLinks(this, (projectLinks, f) -> {
            try {
                if (f != null) {
                    throw f;
                }
                if (projectLinks != null) {
                    state.tenantLinks = new HashSet<>(projectLinks);
                }
                taskOperation.setBody(state);
                super.handleStart(taskOperation);
            } catch (Throwable t) {
                logSevere("Failed during creation: %s", Utils.toString(t));
                taskOperation.fail(t);
            }
        });
    }

    @Override
    protected void initializeState(EndpointCreationTaskState task, Operation taskOperation) {
        task.subStage = SubStage.VERIFY_ACCESS;
        task.userLink = taskOperation.getAuthorizationContext().getClaims().getSubject();
        task.adapterReference = buildEndpointAdapterUri(this.getHost(), task.type);
        super.initializeState(task, taskOperation);
    }

    @Override
    protected EndpointCreationTaskState validateStartPost(Operation op) {
        EndpointCreationTaskState task = super.validateStartPost(op);
        if (task == null) {
            return null;
        }

        if (TaskState.isCancelled(task.taskInfo)
                || TaskState.isFailed(task.taskInfo)
                || TaskState.isFinished(task.taskInfo)) {
            return null;
        }

        if (!ServiceHost.isServiceCreate(op)) {
            return task;
        }

        if (task.name == null || task.name.isEmpty()) {
            failOperation(this.getHost(), op, ENDPOINT_NAME_REQUIRED,
                    Operation.STATUS_CODE_BAD_REQUEST);
            return null;
        }

        if (task.type == null) {
            failOperation(this.getHost(), op, ENDPOINT_TYPE_REQUIRED,
                    Operation.STATUS_CODE_BAD_REQUEST);
            return null;
        }

        if (!EndpointUtils.isSupportedEndpointType(task.type)) {
            failOperation(this.getHost(), op, INVALID_ENDPOINT_TYPE,
                    Operation.STATUS_CODE_BAD_REQUEST);
            return null;
        }

        if (task.orgLink == null || task.orgLink.isEmpty()) {
            failOperation(this.getHost(), op, ENDPOINT_ORG_LINK_REQUIRED,
                    Operation.STATUS_CODE_BAD_REQUEST);
            return null;
        }

        if (!isOperationCredentialsSet(task.endpointProperties, task.credentials)) {
            failOperation(this.getHost(), op, CREDENTIALS_REQUIRED, Operation.STATUS_CODE_BAD_REQUEST);
            return null;
        }

        if (task.tags != null) {
            String key;
            String value;
            for (TagViewState tagViewState: task.tags) {
                if (tagViewState == null || tagViewState.isEmpty()) {
                    key = tagViewState != null ? tagViewState.key : null;
                    value = tagViewState != null ? tagViewState.value : null;
                    failOperation(this.getHost(), op, ENDPOINT_TAG_NULL_EMPTY,
                            Operation.STATUS_CODE_BAD_REQUEST, key, value);
                }
            }
        }
        return task;
    }

    @Override
    protected boolean validateTransition(Operation patch, EndpointCreationTaskState currentTask,
            EndpointCreationTaskState patchBody) {
        super.validateTransition(patch, currentTask, patchBody);
        if (patchBody.taskInfo.stage == TaskStage.STARTED && patchBody.subStage == null) {
            patch.fail(new IllegalArgumentException("Missing sub-stage"));
            return false;
        }

        if (!TaskHelper.validateTransition(currentTask.subStage, patchBody.subStage)) {
            patch.fail(new IllegalArgumentException(
                    String.format("Task subStage cannot be moved from '%s' to '%s'",
                            currentTask.subStage, patchBody.subStage)));
            return false;
        }

        return true;
    }

    @Override
    public void handlePatch(Operation patch) {
        EndpointCreationTaskState currentTask = getState(patch);
        EndpointCreationTaskState patchBody = getBody(patch);

        if (!validateTransition(patch, currentTask, patchBody)) {
            return;
        }
        updateState(currentTask, patchBody);
        patch.complete();

        switch (currentTask.taskInfo.stage) {
        case STARTED:
            handleSubStage(currentTask);
            break;
        case FINISHED:
            logFine("Task finished successfully");
            break;
        case FAILED:
            logWarning("Task failed: %s", (currentTask.failureMessage == null ? "No reason given"
                    : currentTask.failureMessage));
            break;
        default:
            logWarning("Unexpected stage: %s", currentTask.taskInfo.stage);
            break;
        }
    }

    /**
     * State machine for endpoint creation.
     */
    private void handleSubStage(EndpointCreationTaskState task) {
        switch (task.subStage) {
        case VERIFY_ACCESS:
            verifyOrgAccess(task, SubStage.VALIDATE_ENDPOINT);
            break;
        case VALIDATE_ENDPOINT:
            validateEndpoint(task, SubStage.CREATE_COST_USAGE_REPORT);
            break;
        case CREATE_COST_USAGE_REPORT:
            createCostUsageReport(task, SubStage.VALIDATE_OWNERS);
            break;
        case VALIDATE_OWNERS:
            validateEndpointOwners(task, SubStage.CREATE_PERMISSIONS);
            break;
        case CREATE_PERMISSIONS:
            createPermissions(task, SubStage.UPDATE_USERS);
            break;
        case UPDATE_USERS:
            updateUsers(task, SubStage.FIND_CREATE_TAGS);
            break;
        case FIND_CREATE_TAGS:
            findCreateTags(task, SubStage.CREATE_ENDPOINT);
            break;
        case CREATE_ENDPOINT:
            createEndpoint(task, SubStage.POST_TO_TELEMETRY);
            break;
        case POST_TO_TELEMETRY:
            postTelemetry(task, SubStage.INITIALIZE_DATA);
            break;
        case INITIALIZE_DATA:
            initializeData(task, SubStage.SUCCESS);
            break;
        case SUCCESS:
            sendSelfFinishedPatch(task);
            break;
        case ERROR:
            sendSelfFailurePatch(task, task.taskInfo.failure.message);
            break;
        default:
            sendSelfFailurePatch(task, "Unknown stage encountered: " + task.subStage);
        }
    }

    /**
     * Validate the endpoint.
     */
    private void validateEndpoint(EndpointCreationTaskState task, SubStage nextStage) {
        if (task.isMock) {
            task.subStage = nextStage;
            handleSubStage(task);
            return;
        }

        EndpointValidationTaskService.EndpointValidationTaskState validationTaskState = new
                EndpointValidationTaskService.EndpointValidationTaskState();
        validationTaskState.type = task.type;
        validationTaskState.endpointProperties = task.endpointProperties;
        filterEmptyPropertyValues(task.endpointProperties);
        validationTaskState.customProperties = task.customProperties;
        validationTaskState.credentials = task.credentials;
        validationTaskState.adapterReference = task.adapterReference;
        validationTaskState.isMock = task.isMock;
        validationTaskState.taskInfo = TaskState.createDirect();

        sendWithDeferredResult(Operation.createPost(this, EndpointValidationTaskService
                .FACTORY_LINK)
                .setBody(validationTaskState))
                .whenComplete((operation, throwable) -> {
                    if (throwable != null) {
                        handleError(task, throwable, operation.getStatusCode());
                        return;
                    }

                    if (handleValidationResponse(task, operation)) {
                        return;
                    }

                    task.subStage = nextStage;
                    handleSubStage(task);
                });
    }

    private boolean handleValidationResponse(EndpointCreationTaskState task, Operation operation) {
        boolean failedValidation = false;

        EndpointValidationTaskService.EndpointValidationTaskState response =
                operation.getBody(EndpointValidationTaskService.EndpointValidationTaskState.class);
        if (response.taskInfo != null && response.taskInfo.stage == TaskStage.FAILED) {
            ServiceErrorResponse failure = response.taskInfo.failure;
            if (failure != null) {
                handleError(task, failure.message, failure.statusCode, failure.messageId);
                failedValidation = true;
            }
        }
        return failedValidation;
    }

    private void filterEmptyPropertyValues(Map<String, String> properties) {
        if (properties != null) {
            properties.entrySet().removeIf(e -> (e.getValue() == null) || e.getValue().isEmpty());
        }
    }

    /**
     * Validate that endpoint owners are valid users and part of the org.
     */
    private void validateEndpointOwners(EndpointCreationTaskState task, SubStage nextStage) {
        if (task.ownerEmails == null || task.ownerEmails.isEmpty()) {
            task.subStage = nextStage;
            handleSubStage(task);
            return;
        }

        OperationContext origCtx = OperationContext.getOperationContext();

        Consumer<Operation> onSuccess = o -> {
            OperationContext.restoreOperationContext(origCtx);
            QueryTask response = o.getBody(QueryTask.class);
            if (response.results != null && response.results.documentLinks != null) {
                task.ownerLinks = new HashSet<>(response.results.documentLinks);
            }
            task.subStage = nextStage;
            handleSubStage(task);
        };

        Consumer<Throwable> onFailure = e -> {
            OperationContext.restoreOperationContext(origCtx);
            handleError(task, ErrorUtil.message(INVALID_ENDPOINT_OWNER),
                    Operation.STATUS_CODE_BAD_REQUEST, INVALID_ENDPOINT_OWNER.getErrorCode());
        };

        EndpointUtils.validateUsersForEndpointOwnership(task.orgLink, this,
                task.ownerEmails, onSuccess, onFailure);
    }

    /**
     * Verify if the user has access to the org.
     */
    private void verifyOrgAccess(EndpointCreationTaskState task, SubStage nextStage) {
        String orgLink = task.orgLink;

        sendWithDeferredResult(Operation.createGet(this, UserContextQueryService.SELF_LINK))
                .whenComplete((operation, throwable) -> {
                    if (throwable != null) {
                        handleError(task, throwable, operation.getStatusCode());
                        return;
                    }

                    UserContext userCtx = operation.getBody(UserContext.class);

                    if (userCtx.organizations != null) {
                        for (OrganizationState organization : userCtx.organizations) {
                            if (organization.documentSelfLink.equals(orgLink)) {
                                task.subStage = nextStage;
                                handleSubStage(task);
                                return;
                            }
                        }
                    }

                    handleError(task, ErrorUtil.message(INVALID_ORG_LINK, orgLink),
                            Operation.STATUS_CODE_FORBIDDEN);
                });
    }

    /**
     * Create endpoint owner role for the user.
     */
    private void createPermissions(EndpointCreationTaskState task, SubStage nextStage) {
        task.endpointLink = buildEndpointLink(task.orgLink, task.userLink);;

        List<Operation> ops = new ArrayList<>();
        ops.add(createOwnerUserGroup(task.endpointLink, task.orgLink, task.tenantLinks));
        ops.add(createOwnerResourceGroup(task.endpointLink, task.tenantLinks));
        ops.add(createOwnerRole(task.endpointLink, task.tenantLinks));

        OperationJoin.create(ops)
                .setCompletion((operations, exs) -> {
                    if (exs != null) {
                        Entry<Long, Throwable> ex = exs.entrySet().iterator()
                                .next();
                        handleError(task, ex.getValue(),
                                operations.get(ex.getKey()).getStatusCode());
                        return;
                    }
                    task.subStage = nextStage;
                    sendSelfPatch(task);
                }).sendWith(this);
    }

    /**
     * Create user group for owners of the endpoint.
     * By default, all org admins and creator of the endpoint are part of this user group.
     */
    private Operation createOwnerUserGroup(String endpointLink, String orgLink, Set<String> tenantLinks) {
        String orgId = UriUtils.getLastPathSegment(orgLink);
        String userGroupLink = buildEndpointAuthzArtifactSelfLink(
                UserGroupService.FACTORY_LINK, endpointLink, tenantLinks);
        String orgAdminUserGroupLink =  UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                OnboardingUtils.buildAuthzArtifactLink(orgId, true));

        List<String> ownerUserGroupLinks = new ArrayList<>();
        ownerUserGroupLinks.add(userGroupLink);
        ownerUserGroupLinks.add(orgAdminUserGroupLink);

        UserGroupState group = UserGroupState.Builder.create()
                .withQuery(Query.Builder.create()
                        .addInCollectionItemClause(UserState.FIELD_NAME_USER_GROUP_LINKS,
                                ownerUserGroupLinks)
                        .build())
                .withSelfLink(userGroupLink)
                .build();

        URI userGroupFactoryUri = AuthUtils.buildAuthProviderHostUri(this.getHost(),
                ServiceUriPaths.CORE_AUTHZ_USER_GROUPS);

        Operation userGroupOp = Operation.createPost(userGroupFactoryUri)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
                .setReferer(this.getUri())
                .setBody(group);
        setAuthorizationContext(userGroupOp, getSystemAuthorizationContext());
        addReplicationQuorumHeader(userGroupOp);

        return userGroupOp;
    }

    /**
     * Create resource group for given endpoint.
     * By default, the endpoint itself and all resources belonging to that endpoint will be in
     * this resource group.
     */
    private Operation createOwnerResourceGroup(String endpointLink, Set<String> tenantLinks) {
        String resourceGroupLink = buildEndpointAuthzArtifactSelfLink(
                ResourceGroupService.FACTORY_LINK, endpointLink, tenantLinks);

        ResourceGroupState resourceGroup = ResourceGroupState.Builder.create()
                .withQuery(Query.Builder.create(Occurance.MUST_OCCUR)
                        .addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK, endpointLink,
                                Occurance.SHOULD_OCCUR)
                        .addFieldClause(ResourceState.FIELD_NAME_ENDPOINT_LINK, endpointLink,
                                Occurance.SHOULD_OCCUR)
                        .addCollectionItemClause(ResourceState.FIELD_NAME_ENDPOINT_LINKS, endpointLink,
                                Occurance.SHOULD_OCCUR)
                        .build())
                .withSelfLink(resourceGroupLink)
                .build();

        URI resourceGroupFactoryUri = AuthUtils.buildAuthProviderHostUri(this.getHost(),
                ServiceUriPaths.CORE_AUTHZ_RESOURCE_GROUPS);

        Operation resourceGroupOp = Operation.createPost(resourceGroupFactoryUri)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
                .setReferer(this.getUri())
                .setBody(resourceGroup);
        setAuthorizationContext(resourceGroupOp, getSystemAuthorizationContext());
        addReplicationQuorumHeader(resourceGroupOp);
        return resourceGroupOp;
    }

    /**
     * Create role for endpoint owners that ties the user group and resource group and gives
     * full access to the endpoint.
     */
    private Operation createOwnerRole(String endpointLink, Set<String> tenantLinks) {
        String roleLink = buildEndpointAuthzArtifactSelfLink(
                RoleService.FACTORY_LINK, endpointLink, tenantLinks);
        String userGroupLink = buildEndpointAuthzArtifactSelfLink(
                UserGroupService.FACTORY_LINK, endpointLink, tenantLinks);
        String resourceGroupLink = buildEndpointAuthzArtifactSelfLink(
                ResourceGroupService.FACTORY_LINK, endpointLink, tenantLinks);

        RoleState role = RoleState.Builder.create()
                .withUserGroupLink(userGroupLink)
                .withResourceGroupLink(resourceGroupLink)
                .withVerbs(EnumSet.allOf(Action.class))
                .withPolicy(Policy.ALLOW)
                .withSelfLink(roleLink)
                .build();

        URI roleFactoryUri = AuthUtils.buildAuthProviderHostUri(this.getHost(),
                ServiceUriPaths.CORE_AUTHZ_ROLES);

        Operation roleOp = Operation.createPost(roleFactoryUri)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
                .setReferer(this.getUri())
                .setBody(role);
        setAuthorizationContext(roleOp, getSystemAuthorizationContext());
        addReplicationQuorumHeader(roleOp);
        return roleOp;
    }

    /**
     * Add endpoint owner userGroupLink to the creator's UserStates and endpoint owner UserStates.
     */
    private void updateUsers(EndpointCreationTaskState task, SubStage nextStage) {
        URI userUri = AuthUtils.buildAuthProviderHostUri(this.getHost(), task.userLink);
        Operation userOp = Operation.createGet(userUri);
        setAuthorizationContext(userOp, getSystemAuthorizationContext());
        OperationContext origCtx = OperationContext.getOperationContext();

        sendWithDeferredResult(userOp)
                .whenComplete((operation, throwable) -> {
                    OperationContext.restoreOperationContext(origCtx);
                    if (throwable != null) {
                        handleError(task, throwable, operation.getStatusCode());
                        return;
                    }

                    UserState user = operation.getBody(UserState.class);
                    task.createdByUser = user;

                    List<CollectionStringFieldUpdate> updateActionByUserSelfLinks = new ArrayList<>();
                    updateActionByUserSelfLinks.add(CollectionStringFieldUpdate.create(UpdateAction.ADD,
                            task.userLink));

                    if (task.ownerLinks != null) {
                        task.ownerLinks.stream()
                                .forEach(link -> {
                                    updateActionByUserSelfLinks.add(CollectionStringFieldUpdate.create(
                                            UpdateAction.ADD, link));
                                });
                    }

                    Consumer<Void> onSuccess = aVoid -> {
                        OperationContext.restoreOperationContext(origCtx);
                        task.subStage = nextStage;
                        handleSubStage(task);
                    };

                    Consumer<Throwable> onError = e -> {
                        OperationContext.restoreOperationContext(origCtx);
                        handleError(task, ErrorUtil.message(ENDPOINT_OWNER_ADDITIONAL_FAILED),
                                Operation.STATUS_CODE_INTERNAL_ERROR,
                                ENDPOINT_OWNER_ADDITIONAL_FAILED.getErrorCode());
                    };

                    EndpointUtils.updateOwnersForEndpoint(task.endpointLink,
                            new ArrayList<String>(task.tenantLinks), this,
                            updateActionByUserSelfLinks, onSuccess, onError);
                });
    }

    /**
     * Find or create tags and associate them with the endpoint.
     * First issue a query to find all existing tags. For tags that were not found, proceed with
     * creation.
     */
    private void findCreateTags(EndpointCreationTaskState task, SubStage nextStage) {
        // if no tags were provided proceed with endpoint creation
        if (task.tags == null || task.tags.isEmpty()) {
            task.subStage = nextStage;
            sendSelfPatch(task);
            return;
        }

        task.tagsMap = new HashMap<>();
        for (TagViewState tagView : task.tags) {
            TagState tempTag = TagsUtil.newTagState(tagView.key, tagView.value,
                    EnumSet.of(TagState.TagOrigin.USER_DEFINED), new ArrayList<>(task.tenantLinks));
            task.tagsMap.put(tempTag.documentSelfLink, tempTag);
        }

        QueryTask queryTask;
        try {
            queryTask = buildTagsQuery(task.tagsMap, task.tenantLinks);
        } catch (Exception e) {
            handleError(task, e.getMessage());
            return;
        }

        startInventoryQueryTask(this, queryTask)
                .whenComplete((response, err) -> {
                    if (err != null) {
                        handleError(task, err.getMessage());
                        return;
                    }

                    List<TagState> tagsToCreate = new ArrayList<>();
                    List<TagState> tagsToUpdate = new ArrayList<>();

                    // if no results were returned we need to create all the tags provided
                    if (response.results.documents == null || response.results.documents.isEmpty()) {
                        tagsToCreate.addAll(task.tagsMap.values());
                        createUpdateTags(task, tagsToCreate, tagsToUpdate, nextStage);
                        return;
                    }

                    // todo convert to utility that handles multi-page results
                    /* Process the tagsMap as following:
                     * * if the tag was found in the results:
                     *  - if the origin field doesn't contain the USER_DEFINED value, the tag needs
                     *    to be updated to include it.
                     *  - else: no edits needed, add tag to taglinks as is
                     * * if the tag was not found: tag needs to be created and origin needs to be
                     *   USER_DEFINED
                     */
                    TagState tag;
                    task.tagLinks = new HashSet<>();
                    for (Entry<String, TagState> tagEntry : task.tagsMap.entrySet()) {
                        if (response.results.documents.get(tagEntry.getKey()) != null) {
                            tag = Utils.fromJson(response.results.documents.get(tagEntry.getKey()),
                                    TagState.class);
                            if (!tag.origins.contains(TagState.TagOrigin.USER_DEFINED)) {
                                tag.origins.add(TagState.TagOrigin.USER_DEFINED);
                                tagsToUpdate.add(tag);
                            } else {
                                task.tagLinks.add(tag.documentSelfLink);
                            }

                        } else {
                            tag = Utils.fromJson((tagEntry.getValue()), TagState.class);
                            tag.origins = EnumSet.of(TagState.TagOrigin.USER_DEFINED);
                            tagsToCreate.add(tag);
                        }
                    }
                    createUpdateTags(task, tagsToCreate, tagsToUpdate, nextStage);
                });
    }

    private void createUpdateTags(EndpointCreationTaskState task, List<TagState> tagsToCreate,
            List<TagState> tagsToUpdate, SubStage nextStage) {
        if ((tagsToCreate == null || tagsToCreate.isEmpty())
                && (tagsToUpdate == null || tagsToUpdate.isEmpty())) {
            task.subStage = nextStage;
            sendSelfPatch(task);
            return;
        }

        List<Operation> tagsOp = new ArrayList<>();
        for (TagState t : tagsToCreate) {
            Operation op = Operation.createPost(
                    createInventoryUri(this.getHost(), TagService.FACTORY_LINK))
                    .setBody(t);
            tagsOp.add(op);
        }

        for (TagState t : tagsToUpdate) {
            Operation op = Operation.createPatch(
                    createInventoryUri(this.getHost(), t.documentSelfLink))
                    .setBody(t);
            tagsOp.add(op);
        }

        OperationJoin.create(tagsOp)
                .setCompletion((ops, failures) -> {
                    if (failures != null) {
                        // if a failure occurs during tag creation, proceed to endpoint creation
                        logWarning("Failure creating one or more tags: %s",
                                failures.values().iterator().next());
                        task.subStage = nextStage;
                        sendSelfPatch(task);
                        return;
                    }
                    task.tagLinks = (task.tagLinks == null) ? new HashSet<>() : task.tagLinks;
                    for (Operation op : ops.values()) {
                        TagState tagState = op.getBody(TagState.class);
                        task.tagLinks.add(tagState.documentSelfLink);
                    }
                    task.subStage = nextStage;
                    sendSelfPatch(task);
                    return;
                }).sendWith(this);
    }

    /**
     * Create a cost usage report
     */
    private void createCostUsageReport(EndpointCreationTaskState task, SubStage nextStage) {
        if (task.isMock) {
            task.subStage = nextStage;
            sendSelfPatch(task);
            return;
        }

        // if not AWS endpoint/account, move to next stage
        if (!task.type.equals(EndpointType.aws.name()) || task.credentials.aws == null) {
            task.subStage = nextStage;
            sendSelfPatch(task);
            return;
        }

        // skip this stage if s3 bucketName is empty
        if (task.customProperties == null
                || isEmpty(task.customProperties.get(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET))
                // if s3bucketName is present, then we need other 2 properties to be set.
                || (!isEmpty(task.customProperties.get(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET))
                    && (isEmpty(task.customProperties.get(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BUCKET_PREFIX))
                    && isEmpty(task.customProperties.get(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_COSTUSAGE_REPORT_NAME))))) {

            task.subStage = nextStage;
            sendSelfPatch(task);
            return;
        }

        String s3bucketName = task.customProperties.get(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET);
        String bucketPrefix = task.customProperties.get(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BUCKET_PREFIX);
        String costAndUsageReportName =
                task.customProperties.get(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_COSTUSAGE_REPORT_NAME);

        // if s3bucketName is present, but one of the other 2 properties is empty
        // throw an error
        if (!isEmpty(s3bucketName)
                && (isEmpty(bucketPrefix) || isEmpty(costAndUsageReportName))) {

            handleError(task, INVALID_COST_USAGE_PARAMS_ERROR.getMessage(),
                    Operation.STATUS_CODE_BAD_REQUEST,
                    INVALID_COST_USAGE_PARAMS_ERROR.getErrorCode());
            return;
        }

        // all 3 properties are not null or empty
        AwsCostUsageReportTaskService.AwsCostUsageReportTaskState awsCostUsageReportTaskState =
                new AwsCostUsageReportTaskService.AwsCostUsageReportTaskState();

        awsCostUsageReportTaskState.credentials = task.credentials;
        awsCostUsageReportTaskState.s3bucketName = s3bucketName;
        awsCostUsageReportTaskState.s3bucketPrefix = bucketPrefix;
        awsCostUsageReportTaskState.costUsageReportName = costAndUsageReportName;

        if (!task.isMock) {
            awsCostUsageReportTaskState.options = EnumSet.of(TaskOption.SELF_DELETE_ON_COMPLETION);
        }
        awsCostUsageReportTaskState.taskInfo = TaskState.createDirect();

        Operation reportValidateOp = Operation
                .createPost(this, AwsCostUsageReportTaskService.FACTORY_LINK)
                .setBody(awsCostUsageReportTaskState).setReferer(getUri());

        setAuthorizationContext(reportValidateOp, getSystemAuthorizationContext());
        OperationContext origCtx = OperationContext.getOperationContext();

        sendWithDeferredResult(reportValidateOp)
                .whenComplete((o, e) -> {
                    OperationContext.restoreOperationContext(origCtx);
                    if (e != null) {
                        logWarning(Utils.toString(e));
                        handleError(task, e.getMessage(), o.getStatusCode());
                        return;
                    }

                    AwsCostUsageReportTaskService.AwsCostUsageReportTaskState  body =
                            o.getBody(AwsCostUsageReportTaskService.AwsCostUsageReportTaskState.class);
                    if (body.taskInfo != null && body.taskInfo.stage == TaskStage.FAILED) {
                        if (body.taskInfo.failure != null) {
                            handleError(task, body.taskInfo.failure.message,
                                    body.taskInfo.failure.statusCode,
                                    body.taskInfo.failure.messageId != null ?
                                            Integer.parseInt(body.taskInfo.failure.messageId) :
                                            Integer.MIN_VALUE);
                            return;
                        }
                        handleError(task, task.failureMessage, Operation.STATUS_CODE_INTERNAL_ERROR);
                        return;
                    }
                    task.subStage = nextStage;
                    sendSelfPatch(task);
                });
    }

    /**
     * Creates the endpoint.
     */
    private void createEndpoint(EndpointCreationTaskState task, SubStage nextStage) {
        EndpointState endpointState = new EndpointState();
        endpointState.name = task.name;
        endpointState.desc = task.description;
        endpointState.endpointType = task.type;
        endpointState.creationTimeMicros = Utils.getSystemNowMicrosUtc();
        if (task.type.equals(EndpointType.vsphere.name())) {
            endpointState.endpointType = EndpointUtils.VSPHERE_ON_PREM_ADAPTER;
        }

        endpointState.resourcePoolLink = getResourcePoolLink(task);
        List<String> tenantLinks = new ArrayList<>();
        tenantLinks.add(getDefaultProjectSelfLink(task.orgLink));
        endpointState.tenantLinks = tenantLinks;
        endpointState.documentSelfLink = task.endpointLink;

        endpointState.customProperties = reconstructCustomProperties(task.customProperties,
                task.createdByUser.email, task.services, task.credentials);

        endpointState.endpointProperties = reconstructEndpointProperties(endpointState.endpointType,
                task.credentials, task.endpointProperties, task.customProperties);

        EndpointAllocationTaskState endpointTaskState = new EndpointAllocationTaskState();
        endpointTaskState.adapterReference = task.adapterReference;
        endpointTaskState.endpointState = endpointState;
        if (task.isMock) {
            endpointTaskState.options = EnumSet.of(TaskOption.IS_MOCK);
        }
        if (task.tagLinks != null && !task.tagLinks.isEmpty()) {
            endpointState.tagLinks = new HashSet(task.tagLinks);
        }
        endpointTaskState.tenantLinks = new ArrayList<>(task.tenantLinks);
        endpointTaskState.taskInfo = TaskState.createDirect();

        sendWithDeferredResult(Operation
                .createPost(this, EndpointAllocationTaskService.FACTORY_LINK)
                .setBody(endpointTaskState))
                .whenComplete((o, e) -> {
                    if (e != null) {
                        logWarning(Utils.toString(e));
                        // Reverting the change done in CREATE_COST_USAGE_REPORT stage
                        if (task.credentials != null && task.customProperties != null) {
                            deleteCostAndUsageConfiguration(task.credentials,
                                    task.customProperties
                                            .get(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_COSTUSAGE_REPORT_NAME));
                        }

                        handleError(task, e.getMessage(), o.getStatusCode());
                        return;
                    }

                    EndpointAllocationTaskState body = o.getBody(EndpointAllocationTaskState.class);
                    if (body.taskInfo != null && body.taskInfo.stage == TaskStage.FAILED) {
                        if (body.taskInfo.failure != null) {
                            handleError(task, body.taskInfo.failure.message,
                                    body.taskInfo.failure.statusCode);
                            return;
                        }
                        handleError(task, task.failureMessage,
                                Operation.STATUS_CODE_INTERNAL_ERROR);
                        return;
                    }
                    task.subStage = nextStage;
                    task.endpointLink = body.endpointState.documentSelfLink;
                    task.endpointState = body.endpointState;
                    sendSelfPatch(task);
                });
    }

    private void postTelemetry(EndpointCreationTaskState task, SubStage nextStage) {
        String userId = null;
        String userLink = task.userLink;
        if (userLink != null && userLink.startsWith(UserService.FACTORY_LINK)) {
            userId = UriUtils.getLastPathSegment(userLink);
        }
//      TODO:  EndpointTelemetryUtils.postTelemetry(this.getHost(), task, userId,
//                TelemetryConstants.TelemetryCloudActionState.ADDED);

        task.subStage = nextStage;
        sendSelfPatch(task);
    }


    private void initializeData(EndpointCreationTaskState task, SubStage nextStage) {
        // If enumeration is not disabled, then trigger.
        if (!task.skipInitialDataInitialization && !task.isMock) {
            triggerDataInitOnEndpoint(this, task.endpointState, task.tenantLinks, null);
        }

        task.subStage = nextStage;
        sendSelfPatch(task);
    }

    /**
     * Helper function to trigger data initialization on a created endpoint. Executes in parallel
     * {@link DataInitializationTaskService} and
     * {@link OptionalAdapterSchedulingService}.
     *
     * @param sender The service that will be sending the request.
     * @param endpoint The endpoint to trigger data initialization on.
     * @param tenantLinks tenantLinks for authz purposes
     * @param dataInitializationTaskInfo Task info for the
     * {@link DataInitializationTaskService}. May be `null`.
     * @return A DeferredResult containing an array of the operations ran.
     */
    public static DeferredResult<Operation[]> triggerDataInitOnEndpoint(Service sender,
            EndpointState endpoint, Set<String> tenantLinks, TaskState dataInitializationTaskInfo) {
        return initializeDataInitializationTaskService(sender, endpoint, tenantLinks,
                dataInitializationTaskInfo)
                .thenCombine(triggerOptionalAdapterSchedulingService(sender, endpoint),
                        (dataInitTaskOp, optionalStatsAdapterOp) ->
                                new Operation[] { dataInitTaskOp, optionalStatsAdapterOp });
    }

    /**
     * Create resource pool link.
     * <p>
     * Format: /resources/pools/orgid-projectid
     */
    private String getResourcePoolLink(EndpointCreationTaskState task) {
        // The resource id is the same as the auth context id.
        String resourcePoolId = UriUtils
                .getLastPathSegment(getDefaultProjectSelfLink(task.orgLink));
        return UriUtils.buildUriPath(ResourcePoolService.FACTORY_LINK, resourcePoolId);
    }

    /**
     * Builds endpoint link prefix.
     * <p>
     * Format: /resources/endpoints/orgid-projectid-userid-
     */
    private String getEndpointLinkPrefix(String orgLink, String userLink) {
        String defaultProjectId = UriUtils
                .getLastPathSegment(getDefaultProjectSelfLink(orgLink));
        String userId = UriUtils.getLastPathSegment(userLink);
        String endpointPrefix = defaultProjectId + "-" + userId + "-";
        return UriUtils.buildUriPath(EndpointService.FACTORY_LINK, endpointPrefix);
    }

    /**
     * Builds endpoint link.
     * <p>
     * Format: /resources/endpoints/orgid-projectid-userid-uuid
     */
    private String buildEndpointLink(String orgLink, String userLink) {
        return getEndpointLinkPrefix(orgLink, userLink) + this.getHost().nextUUID();
    }

    private void handleError(EndpointCreationTaskState taskState, Throwable ex, int statusCode) {
        logWarning(Utils.toString(ex));
        handleError(taskState, ex.getMessage(), statusCode);
    }

    private void handleError(EndpointCreationTaskState taskState, String failureMessage) {
        logWarning("Task failed at sub-stage %s with failure: %s", taskState.subStage,
                failureMessage);

        taskState.subStage = SubStage.ERROR;
        ServiceErrorResponse e = new ServiceErrorResponse();
        e.message = failureMessage;
        taskState.taskInfo.failure = e;
        sendSelfPatch(taskState);
    }

    private void handleError(EndpointCreationTaskState taskState, String failureMessage,
            int statusCode) {
        logWarning("Task failed at sub-stage %s with failure: %s", taskState.subStage,
                failureMessage);

        taskState.subStage = SubStage.ERROR;
        ServiceErrorResponse e = new ServiceErrorResponse();
        e.message = failureMessage;
        e.statusCode = statusCode;
        taskState.taskInfo.failure = e;
        sendSelfPatch(taskState);
    }

    private void handleError(EndpointCreationTaskState taskState,
            String failureMessage, int statusCode, String errorCode) {
        logWarning("Task failed at sub-stage %s with failure: %s", taskState.subStage,
                failureMessage);

        taskState.subStage = SubStage.ERROR;
        ServiceErrorResponse e = new ServiceErrorResponse();
        e.message = failureMessage;
        e.statusCode = statusCode;
        e.messageId = errorCode;
        taskState.taskInfo.failure = e;
        sendSelfPatch(taskState);
    }

    private void handleError(EndpointCreationTaskState taskState,
            String failureMessage, int statusCode, int errorCode) {
        logWarning("Task failed at sub-stage %s with failure: %s", taskState.subStage,
                failureMessage);

        taskState.subStage = SubStage.ERROR;
        ServiceErrorResponse e = new ServiceErrorResponse();
        e.message = failureMessage;
        e.statusCode = statusCode;
        e.messageId = String.valueOf(errorCode);
        taskState.taskInfo.failure = e;
        sendSelfPatch(taskState);
    }
}