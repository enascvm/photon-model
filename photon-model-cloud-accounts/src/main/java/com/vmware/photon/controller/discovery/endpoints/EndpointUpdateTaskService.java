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

import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.CANNOT_REMOVE_CREATOR_FROM_OWNERS;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_INVALID_UPDATE_ACTION;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_LINK_REQUIRED;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_NAME_REQUIRED;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_NOTHING_TO_UPDATE;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_TAG_NULL_EMPTY;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.INVALID_COST_USAGE_PARAMS_ERROR;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.INVALID_USER_FOR_OWNERS_UPDATE;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.OWNERS_UPDATE_FAILED;
import static com.vmware.photon.controller.discovery.cloudaccount.utils.ClusterUtils.createInventoryUri;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.PROPERTY_NAME_CUSTOM_PROP_UPDATE;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.PROPERTY_NAME_PROP_UPDATE;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.PROPERTY_NAME_TAG_UPDATES;
import static com.vmware.photon.controller.discovery.common.utils.InventoryQueryUtils.startInventoryQueryTask;
import static com.vmware.photon.controller.discovery.common.utils.OnboardingUtils.getOrgId;
import static com.vmware.photon.controller.discovery.common.utils.StringUtil.isEmpty;
import static com.vmware.photon.controller.discovery.endpoints.Credentials.AwsCredential.AuthType.ARN;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_AUTH_TYPE;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BUCKET_PREFIX;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_CI_COSTUSAGE_REPORT_NAME;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_CREATEDBY_EMAIL;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_PREFIX_SERVICE_TAG;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_STATUS;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_VALUE_SERVICE_TAG;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.buildEndpointAdapterUri;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.buildTagsQuery;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.failOperation;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.isPatchCredentialBodyValid;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.reconstructEndpointProperties;
import static com.vmware.photon.controller.model.UriPaths.ENDPOINT_UPDATE_TASK_SERVICE;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.ARN_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.EXTERNAL_ID_KEY;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType.aws;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

import com.vmware.photon.controller.discovery.common.CloudAccountConstants.UpdateAction;
import com.vmware.photon.controller.discovery.common.CollectionStringFieldUpdate;
import com.vmware.photon.controller.discovery.common.TagFieldUpdate;
import com.vmware.photon.controller.discovery.common.TaskHelper;
import com.vmware.photon.controller.discovery.common.services.OrganizationService;
import com.vmware.photon.controller.discovery.common.services.UserService.UserState;
import com.vmware.photon.controller.discovery.common.utils.ErrorUtil;
import com.vmware.photon.controller.discovery.common.utils.OnboardingUtils;
import com.vmware.photon.controller.discovery.endpoints.EndpointUpdateTaskService.EndpointUpdateTaskState;
import com.vmware.photon.controller.discovery.endpoints.EndpointValidationTaskService.EndpointValidationTaskState;
import com.vmware.photon.controller.discovery.endpoints.EndpointValidationTaskService.ValidationOption;
import com.vmware.photon.controller.discovery.vsphere.VsphereRDCSyncTaskService;
import com.vmware.photon.controller.model.adapters.util.TagsUtil;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService.EndpointAllocationTaskState;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceStateCollectionUpdateRequest;
import com.vmware.xenon.common.ServiceStateMapUpdateRequest;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskFactoryService;
import com.vmware.xenon.services.common.TaskService;

/**
 * Task to update endpoints.
 */
public class EndpointUpdateTaskService extends TaskService<EndpointUpdateTaskState> {
    public static final String FACTORY_LINK = ENDPOINT_UPDATE_TASK_SERVICE;

    /**
     * The endpoint update task state.
     */
    public static class EndpointUpdateTaskState extends TaskService.TaskServiceState {
        @Documentation(description = "The endpoint to be updated")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String endpointLink;

        @Documentation(description = "The name of the endpoint.")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.OPTIONAL),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public String name;

        @Documentation(description = "The description of the endpoint.")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.OPTIONAL),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public String description;

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

        @Documentation(description = "Indicates a mock request")
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public boolean isMock = false;

        @Documentation(description = "Indicates a flag to trigger Data Initialization for Mock Request")
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public boolean isMockRunDataInit = false;

        @Documentation(description = "Existing endpoint")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public EndpointState endpointState;

        @Documentation(description = "AuthCredentials document")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public AuthCredentialsServiceState authState;

        @Documentation(description = "This is only for internal usage. It keeps track of internal stages.")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public SubStage subStage;

        @Documentation(description = "The tenant links.")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public Set<String> tenantLinks;

        @Documentation(description = "The service tag to add/remove, on the cloud account (Will be converted to lowercase values implicitly)")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.OPTIONAL),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public CollectionStringFieldUpdate serviceUpdate;

        @Documentation(description = "The custom properties to add/update/remove, on cloud account")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.OPTIONAL),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public Map<String, CollectionStringFieldUpdate> customPropertyUpdates;

        @Documentation(description = "The properties to add/update/remove, on cloud account. Currently, only removal of" +
                " VSphere password is supported")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.OPTIONAL),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public Map<String, CollectionStringFieldUpdate> propertyUpdates;

        @Documentation(description = "The property is to store the new fields to be added into " +
                "customProperties.")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public  Map<String, String> customPropertiesToAdd;

        @Documentation(description = "The property is to store the fields to be removed from " +
                "customProperties.")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public  Set<String> customPropertiesToRemove;

        @Documentation(description = "Flag to skip credential validation. For Internal use only")
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public boolean skipCredentialValidation;

        @Documentation(description = "Flag to skip AWS s3 validations. For Internal use only")
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public boolean skipS3Validation;

        @Documentation(description = "Indicates if forcing of data sync is required(valid only " +
                "for vsphere credentials")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public boolean forceDataSync = false;

        @Documentation(description = "List of owners emails to add/remove.")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.OPTIONAL),
                @UsageOption(option = PropertyUsageOption.SINGLE_ASSIGNMENT) })
        public List<CollectionStringFieldUpdate> ownerUpdates;

        @Documentation(description = "The tags to add to/remove from the cloud account")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.OPTIONAL),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public Set<TagFieldUpdate> tagUpdates;

        @Documentation(description = "Set of tagLinks to associate with the endpoint.")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public Set<String> tagLinksToAdd;

        @Documentation(description = "Maintain the tagLinks to dis-associate from the cloud account")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public Set<String> tagLinksToRemove;

        @Documentation(description = "Map of tagLinks and tag states to associate with the endpoint.")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public Map<String, TagState> tagsMap;
    }

    /**
     * Endpoint update sub-stages.
     */
    public enum SubStage {
        /**
         * Lookup endpoint.
         */
        QUERY_ENDPOINT,

        /**
         * Lookup auth credentials
         */
        QUERY_AUTH_CREDENTIALS,

        /**
         * Validate the endpoint
         */
        VALIDATE_ENDPOINT,

        /**
         * Configure cost and usage report on a s3bucket
         */
        CREATE_COST_USAGE_REPORT,

        /**
         * Update endpoint.
         */
        UPDATE_ENDPOINT,

        /**
         * Update service tag.
         */
        UPDATE_SERVICE_TAG,

        /**
         * Update properties.
         */
        UPDATE_PROPERTIES,

        /**
         * Update custom properties.
         */
        UPDATE_CUSTOM_PROPERTIES,

        /**
         * If the endpoint update request contains tags, this stage creates all tags that
         * will get associated to the endpoint. Some of the tags may already be present while others
         * will need to get created.
         */
        FIND_CREATE_TAGS,

        /**
         * Update tags.
         */
        UPDATE_TAGLINKS,

        /**
         * Force vSphere data collector sync.
         */
        FORCE_DATA_SYNC,

        /**
         * Validate and update endpoint owners.
         */
        VALIDATE_AND_UPDATE_OWNERS,

        /**
         * Trigger Data Initialization
         */
        TRIGGER_DATA_COLLECTION,

        /**
         * Successful update of endpoint.
         */
        SUCCESS,

        /**
         * Error while updating endpoint.
         */
        ERROR
    }

    public static FactoryService createFactory() {
        TaskFactoryService fs = new TaskFactoryService(EndpointUpdateTaskState.class) {
            @Override
            public Service createServiceInstance() throws Throwable {
                return new EndpointUpdateTaskService();
            }
        };
        fs.setPeerNodeSelectorPath(ServiceUriPaths.DEFAULT_1X_NODE_SELECTOR);
        return fs;
    }

    public EndpointUpdateTaskService() {
        super(EndpointUpdateTaskState.class);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handleStart(Operation taskOperation) {
        final EndpointUpdateTaskState state;
        if (taskOperation.hasBody()) {
            state = taskOperation.getBody(EndpointUpdateTaskState.class);
        } else {
            state = new EndpointUpdateTaskState();
            taskOperation.setBody(state);
        }

        if (state.tenantLinks != null && !state.tenantLinks.isEmpty()) {
            super.handleStart(taskOperation);
            return;
        }

        if (taskOperation.getAuthorizationContext().isSystemUser()) {
            super.handleStart(taskOperation);
            return;
        } else {
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
    }

    @Override
    protected void initializeState(EndpointUpdateTaskState task, Operation taskOperation) {
        task.subStage = SubStage.QUERY_ENDPOINT;

        if (task.customPropertyUpdates == null || (task.customPropertyUpdates.get
                (ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET) == null)
                || (task.customPropertyUpdates.get(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET)
                .action == UpdateAction.REMOVE)) {
            task.skipS3Validation = true;
        }

        if (isVsphereCredentialRemoval(task)) {
            task.skipCredentialValidation = true;
        }

        // only properties that need credential validation
        // Can skip credential validation for all other updates
        if (task.credentials == null && task.skipS3Validation) {
            task.skipCredentialValidation = true;
        } else {
            task.skipCredentialValidation = false;
        }

        super.initializeState(task, taskOperation);
    }

    @Override
    protected EndpointUpdateTaskState validateStartPost(Operation op) {
        EndpointUpdateTaskState task = super.validateStartPost(op);
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

        if (task.endpointLink == null || task.endpointLink.isEmpty()) {
            failOperation(this.getHost(), op, ENDPOINT_LINK_REQUIRED,
                    Operation.STATUS_CODE_BAD_REQUEST);
            return null;
        }

        if ((task.name == null || task.name.isEmpty())
                // description is an optional field, so can be empty
                && (task.description == null)
                && (task.endpointProperties == null || task.endpointProperties.isEmpty())
                && (task.customPropertyUpdates == null || task.customPropertyUpdates.isEmpty())
                && (task.propertyUpdates == null || task.propertyUpdates.isEmpty())
                && (task.serviceUpdate == null)
                && (task.credentials == null)
                && (task.ownerUpdates == null || task.ownerUpdates.isEmpty())
                && (task.tagUpdates == null || task.tagUpdates.isEmpty())) {
            failOperation(this.getHost(), op, ENDPOINT_NOTHING_TO_UPDATE,
                    Operation.STATUS_CODE_BAD_REQUEST);
            return null;
        }

        // name cannot be updated to empty
        if (task.name != null && task.name.isEmpty()) {
            failOperation(this.getHost(), op, ENDPOINT_NAME_REQUIRED,
                    Operation.STATUS_CODE_BAD_REQUEST);
            return null;
        }

        if (task.customPropertyUpdates != null) {
            for (String key : task.customPropertyUpdates.keySet()) {
                CollectionStringFieldUpdate update = task.customPropertyUpdates.get(key);
                if (update == null || update.action == null) {
                    // make sure valid action is passed in
                    // it will be null for any value other than ADD & REMOVE
                    failOperation(this.getHost(), op, ENDPOINT_INVALID_UPDATE_ACTION,
                            Operation.STATUS_CODE_BAD_REQUEST,
                            PROPERTY_NAME_CUSTOM_PROP_UPDATE, key);
                    return null;
                }
            }
        }

        if (task.propertyUpdates != null) {
            for (String key : task.propertyUpdates.keySet()) {
                CollectionStringFieldUpdate update = task.propertyUpdates.get(key);
                if (!(key.equalsIgnoreCase(EndpointUtils.ENDPOINT_PROPERTY_VSPHERE_PASSWORD) ||
                        key.equalsIgnoreCase(EndpointUtils.ENDPOINT_PROPERTY_VSPHERE_USERNAME)) ||
                        update == null || update.action == null || !UpdateAction.REMOVE.equals(update.action)) {
                    // Currently, only removal of credentials.vsphere.username/password is supported
                    failOperation(this.getHost(), op, ENDPOINT_INVALID_UPDATE_ACTION,
                            Operation.STATUS_CODE_BAD_REQUEST, PROPERTY_NAME_PROP_UPDATE, key);
                    return null;
                }
            }
        }

        if (task.tagUpdates != null) {
            for (TagFieldUpdate tagOp : task.tagUpdates) {
                if (tagOp == null || tagOp.action == null || tagOp.value == null) {
                    // make sure valid action is passed in
                    // it will be null for any value other than ADD & REMOVE
                    failOperation(this.getHost(), op, ENDPOINT_INVALID_UPDATE_ACTION,
                            Operation.STATUS_CODE_BAD_REQUEST, PROPERTY_NAME_TAG_UPDATES);
                    return null;
                } else if (tagOp.value.isEmpty()) {
                    // make sure the key and the value are not empty or null
                    failOperation(this.getHost(), op, ENDPOINT_TAG_NULL_EMPTY,
                            Operation.STATUS_CODE_BAD_REQUEST,
                            tagOp.value.key, tagOp.value.value);
                    return null;
                }
            }
        }
        return task;
    }

    @Override
    protected boolean validateTransition(Operation patch, EndpointUpdateTaskState currentTask,
            EndpointUpdateTaskState patchBody) {
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
        EndpointUpdateTaskState currentTask = getState(patch);
        EndpointUpdateTaskState patchBody = getBody(patch);

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
            logWarning("Task failed: %s", (currentTask.failureMessage == null ?
                    "No reason given" : currentTask.failureMessage));
            break;
        default:
            logWarning("Unexpected stage: %s", currentTask.taskInfo.stage);
            break;
        }
    }

    @Override
    protected void updateState(EndpointUpdateTaskState currentTask, EndpointUpdateTaskState patchBody) {
        currentTask.skipCredentialValidation = patchBody.skipCredentialValidation;
        currentTask.skipS3Validation = patchBody.skipS3Validation;
        super.updateState(currentTask, patchBody);
    }

    /**
     * State machine for endpoint update.
     */
    private void handleSubStage(EndpointUpdateTaskState task) {
        switch (task.subStage) {
        case QUERY_ENDPOINT:
            queryEndpoint(task, SubStage.QUERY_AUTH_CREDENTIALS);
            break;
        case QUERY_AUTH_CREDENTIALS:
            queryAuthCredentials(task, SubStage.VALIDATE_ENDPOINT);
            break;
        case VALIDATE_ENDPOINT:
            validateEndpoint(task, SubStage.CREATE_COST_USAGE_REPORT);
            break;
        case CREATE_COST_USAGE_REPORT:
            createCostUsageReportUpdate(task, SubStage.UPDATE_ENDPOINT);
            break;
        case UPDATE_ENDPOINT:
            updateEndpoint(task, SubStage.UPDATE_SERVICE_TAG);
            break;
        case UPDATE_SERVICE_TAG:
            updateServiceTag(task, SubStage.UPDATE_PROPERTIES);
            break;
        case UPDATE_PROPERTIES:
            updateProperties(task, SubStage.UPDATE_CUSTOM_PROPERTIES);
            break;
        case UPDATE_CUSTOM_PROPERTIES:
            updateCustomProperties(task, SubStage.FIND_CREATE_TAGS);
            break;
        case FIND_CREATE_TAGS:
            findCreateTags(task, SubStage.UPDATE_TAGLINKS);
            break;
        case UPDATE_TAGLINKS:
            updateTagLinks(task, task.forceDataSync ? SubStage.FORCE_DATA_SYNC :
                    SubStage.VALIDATE_AND_UPDATE_OWNERS);
            break;
        case FORCE_DATA_SYNC:
            startDataCollectorSyncTask(task, SubStage.VALIDATE_AND_UPDATE_OWNERS);
            break;
        case VALIDATE_AND_UPDATE_OWNERS:
            validateAndUpdateOwners(task, SubStage.TRIGGER_DATA_COLLECTION);
            break;
        case TRIGGER_DATA_COLLECTION:
            triggerDataCollection(task, SubStage.SUCCESS);
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

    private void queryEndpoint(EndpointUpdateTaskState task, SubStage nextStage) {
        Operation.createGet(this, task.endpointLink)
                .setCompletion((operation, throwable) -> {
                    if (throwable != null) {
                        handleError(task, throwable.getMessage(), operation.getStatusCode());
                        return;
                    }

                    task.endpointState = operation.getBody(EndpointState.class);
                    task.adapterReference = buildEndpointAdapterUri(this.getHost(),
                            task.endpointState.endpointType);
                    task.subStage = nextStage;
                    sendSelfPatch(task);
                }).sendWith(this);
    }

    private void queryAuthCredentials(EndpointUpdateTaskState task, SubStage nextStage) {
        if (task.credentials != null) {
            String errorMsg = isPatchCredentialBodyValid(task);
            if (errorMsg != null) {
                handleError(task, errorMsg, Operation.STATUS_CODE_BAD_REQUEST);
                return;
            }
            if (!task.credentials.isEmpty()) {
                task.subStage = nextStage;
                sendSelfPatch(task);
                return;
            }
            // if task.credential is partial completed, then we need to merge the credential to
            // create a complete one.
            task.credentials = Credentials.mergeCredentials(task.credentials, task.endpointState.endpointProperties);
            task.subStage = nextStage;
            sendSelfPatch(task);
            return;
        }

        if ((task.endpointState.authCredentialsLink == null || task.endpointState.authCredentialsLink.isEmpty())) {
            task.subStage = nextStage;
            sendSelfPatch(task);
            return;
        }

        Operation.createGet(
                createInventoryUri(this.getHost(), task.endpointState.authCredentialsLink))
                .setCompletion((operation, throwable) -> {
                    if (throwable != null) {
                        handleError(task, throwable.getMessage(), operation.getStatusCode());
                        return;
                    }
                    try {
                        task.authState = operation.getBody(AuthCredentialsServiceState.class);
                        Map<String, String> endpointProperties = new HashMap<>();
                        if (task.endpointState.endpointProperties != null) {
                            endpointProperties.putAll(task.endpointState.endpointProperties);
                        }

                        // Re-construct the ARN endpoint properties when fetching credentials if
                        // this is an AWS ARN-based account.
                        String authType = task.authState.customProperties
                                .get(ENDPOINT_CUSTOM_PROPERTY_NAME_AUTH_TYPE);
                        if (task.endpointState.endpointType.equals(aws.name()) && authType != null
                                && authType.equals(ARN.name())) {
                            endpointProperties.put(ARN_KEY,
                                    task.authState.customProperties.get(ARN_KEY));
                            endpointProperties.put(EXTERNAL_ID_KEY,
                                    task.authState.customProperties.get(EXTERNAL_ID_KEY));
                        }

                        task.credentials = Credentials.createCredentials(
                                task.endpointState.endpointType, task.authState,
                                endpointProperties);
                        task.subStage = nextStage;
                        sendSelfPatch(task);
                    } catch (Exception e) {
                        logWarning("Error %s encountered while creating credentials ",
                                e.getMessage());
                        handleError(task, e.getMessage(), operation.getStatusCode());
                    }
                }).sendWith(this);
    }

    /**
     * Validate the endpoint
     */
    private void validateEndpoint(EndpointUpdateTaskState task, SubStage nextStage) {

        getUpdatedFieldsInCustomProperties(task);
        updateEndpointState(task);

        if (task.isMock) {
            task.subStage = nextStage;
            handleSubStage(task);
            return;
        }

        EndpointValidationTaskState validationTaskState = new EndpointValidationTaskState();
        validationTaskState.type = task.endpointState.endpointType;

        if (EndpointUtils.VSPHERE_ON_PREM_ADAPTER.equals(task.endpointState.endpointType)) {
            validationTaskState.type = EndpointType.vsphere.name();
        }

        validationTaskState.customProperties = task.endpointState.customProperties;
        validationTaskState.endpointProperties = task.endpointState.endpointProperties;
        validationTaskState.credentials = task.credentials;
        validationTaskState.adapterReference = task.adapterReference;
        validationTaskState.endpointLink = task.endpointLink;

        if (task.skipCredentialValidation) {
            validationTaskState.options.add(ValidationOption.SKIP_DUPLICATE_ENDPOINT_VALIDATION);
            validationTaskState.options.add(ValidationOption.SKIP_CREDENTIAL_VALIDATION);
        }

        if (task.skipS3Validation) {
            validationTaskState.options.add(ValidationOption.SKIP_S3_DUPLICATE_VALIDATION);
            validationTaskState.options.add(ValidationOption.SKIP_S3_PERMISSION_VALIDATION);
        }

        validationTaskState.isMock = task.isMock;
        validationTaskState.taskInfo = TaskState.createDirect();

        sendWithDeferredResult(Operation.createPost(this, EndpointValidationTaskService
                .FACTORY_LINK)
                .setBody(validationTaskState))
                .whenComplete((operation, throwable) -> {
                    if (throwable != null) {
                        handleError(task, throwable.getMessage(), operation.getStatusCode());
                        return;
                    }

                    if (handleValidationResponse(task, operation)) {
                        return;
                    }

                    task.subStage = nextStage;
                    handleSubStage(task);
                });
    }

    private boolean handleValidationResponse(EndpointUpdateTaskState task, Operation operation) {
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

    /**
     * Updates the endpoint.
     */
    private void updateEndpoint(EndpointUpdateTaskState task, SubStage nextStage) {

        if (task.skipCredentialValidation) {
            sendWithDeferredResult(Operation
                    .createPatch(this, task.endpointState.documentSelfLink)
                    .setBody(task.endpointState))
                    .whenComplete((o, e) -> {
                        if (e != null) {
                            handleError(task, e.getMessage(), o.getStatusCode());
                            return;
                        }

                        if (o.getStatusCode() != Operation.STATUS_CODE_OK
                                && o.getStatusCode() != Operation.STATUS_CODE_NOT_MODIFIED) {
                            handleError(task, task.failureMessage,
                                    Operation.STATUS_CODE_INTERNAL_ERROR);
                            return;
                        }

                        task.subStage = nextStage;
                        sendSelfPatch(task);
                    });
            return;
        }

        if (task.credentials != null && !task.credentials.isEmpty()) {
            if (task.credentials.vsphere != null) {
                task.forceDataSync = true;

                // remove the status field in customProperties. If the status field appears again
                // with "SUCCESS" value then we can force RDC sync.
                if (task.endpointState.customProperties.containsKey(ENDPOINT_CUSTOM_PROPERTY_NAME_STATUS)) {
                    if (task.customPropertiesToRemove == null) {
                        task.customPropertiesToRemove = new HashSet<>();
                    }
                    task.customPropertiesToRemove.add(ENDPOINT_CUSTOM_PROPERTY_NAME_STATUS);
                }
            }
            task.endpointState.endpointProperties =
                    reconstructEndpointProperties(task.endpointState.endpointType, task.credentials,
                            task.endpointState.endpointProperties, task.endpointState.customProperties);
        }

        EndpointAllocationTaskState endpointTaskState = new EndpointAllocationTaskState();
        endpointTaskState.endpointState = task.endpointState;
        endpointTaskState.adapterReference = task.adapterReference;

        if (task.isMock) {
            endpointTaskState.options = EnumSet.of(TaskOption.IS_MOCK);
        }

        endpointTaskState.tenantLinks = new ArrayList<>(task.tenantLinks);
        endpointTaskState.taskInfo = TaskState.createDirect();

        sendWithDeferredResult(Operation
                .createPost(this, EndpointAllocationTaskService.FACTORY_LINK)
                .setBody(endpointTaskState))
                .whenComplete((o, e) -> {
                    if (e != null) {
                        handleError(task, e.getMessage(), o.getStatusCode());
                        return;
                    }

                    EndpointAllocationTaskState body = o
                            .getBody(EndpointAllocationTaskState.class);
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
                    sendSelfPatch(task);
                });
    }

    /**
     * Update the service tags on the endpoint. Since service tags are stored in custom
     * properties which is a Map, they cannot be handled via regular PATCH on EndpointState.
     * Instead, a ServiceStateMapUpdateRequest has to be sent on the PATCH to update the
     * custom properties
     */
    private void updateServiceTag(EndpointUpdateTaskState task, SubStage nextStage) {
        if (task.serviceUpdate == null || task.serviceUpdate.value == null) {
            task.subStage = nextStage;
            sendSelfPatch(task);
            return;
        }

        Map<String, Map<Object, Object>> entriesToAdd = null;
        Map<String, Collection<Object>> keysToRemove = null;

        String key = ENDPOINT_CUSTOM_PROPERTY_NAME_PREFIX_SERVICE_TAG + task.serviceUpdate.value.toLowerCase();

        switch (task.serviceUpdate.action) {
        case ADD:
            Map<String, String> serviceTagToAdd = new HashMap<>();
            serviceTagToAdd.put(key, ENDPOINT_CUSTOM_PROPERTY_VALUE_SERVICE_TAG);
            entriesToAdd = Collections.singletonMap(EndpointState.FIELD_NAME_CUSTOM_PROPERTIES, new HashMap<>(serviceTagToAdd));
            break;
        case REMOVE:
            List<String> serviceTagToRemove = new ArrayList<>();
            serviceTagToRemove.add(key);
            keysToRemove = Collections.singletonMap(EndpointState.FIELD_NAME_CUSTOM_PROPERTIES, new HashSet<>(serviceTagToRemove));
            break;
        default:
            logSevere("Invalid Update Action [%s] received", task.serviceUpdate.action);
            break;
        }

        if (entriesToAdd == null && keysToRemove == null) {
            task.subStage = nextStage;
            sendSelfPatch(task);
            return;
        }

        ServiceStateMapUpdateRequest serviceStateMapUpdateRequest = ServiceStateMapUpdateRequest.create(entriesToAdd, keysToRemove);

        sendWithDeferredResult(Operation
                .createPatch(this, task.endpointState.documentSelfLink)
                .setBody(serviceStateMapUpdateRequest))
                .whenComplete((o, e) -> {
                    if (e != null) {
                        handleError(task, e.getMessage(), o.getStatusCode());
                        return;
                    }

                    task.subStage = nextStage;
                    sendSelfPatch(task);
                });
    }

    private void updateProperties(EndpointUpdateTaskState task, SubStage nextStage) {
        if (task.propertyUpdates == null || task.propertyUpdates.isEmpty() || task.authState == null) {
            task.subStage = nextStage;
            sendSelfPatch(task);
            return;
        }

        // remove the username & password from auth credentials link (by updating it to empty string).
        AuthCredentialsServiceState updateAuthState = new AuthCredentialsServiceState();

        CollectionStringFieldUpdate passwdUpdate = task.propertyUpdates.get(EndpointUtils.ENDPOINT_PROPERTY_VSPHERE_PASSWORD);
        if (passwdUpdate != null && UpdateAction.REMOVE.equals(passwdUpdate.action)) {
            updateAuthState.privateKey = EndpointUtils.EMPTY_STRING;
        }

        CollectionStringFieldUpdate userNameUpdate = task.propertyUpdates.get(EndpointUtils.ENDPOINT_PROPERTY_VSPHERE_USERNAME);
        if (userNameUpdate != null && UpdateAction.REMOVE.equals(userNameUpdate.action)) {
            updateAuthState.privateKeyId = EndpointUtils.EMPTY_STRING;
        }

        sendWithDeferredResult(Operation
                .createPatch(createInventoryUri(this.getHost(), task.authState.documentSelfLink))
                .setBody(updateAuthState))
                .whenComplete((o, e) -> {
                    if (e != null) {
                        handleError(task, e.getMessage(), o != null ? o.getStatusCode() : Operation.STATUS_CODE_INTERNAL_ERROR);
                        return;
                    }

                    task.subStage = nextStage;
                    sendSelfPatch(task);
                });
    }

    /**
     * Update the custom properties defined on the endpoint.
     */
    private void updateCustomProperties(EndpointUpdateTaskState task, SubStage nextStage) {
        if (task.customPropertiesToAdd == null && task.customPropertiesToRemove == null) {
            task.subStage = nextStage;
            sendSelfPatch(task);
            return;
        }

        Map<String, Map<Object, Object>> entriesToAdd = null;
        Map<String, Collection<Object>> keysToRemove = null;

        if (task.customPropertiesToAdd != null && !task.customPropertiesToAdd.isEmpty()) {
            entriesToAdd = Collections.singletonMap(
                    EndpointState.FIELD_NAME_CUSTOM_PROPERTIES, new HashMap<>(task.customPropertiesToAdd));
        }

        if (task.customPropertiesToRemove != null && !task.customPropertiesToRemove.isEmpty()) {
            keysToRemove = Collections.singletonMap(
                    EndpointState.FIELD_NAME_CUSTOM_PROPERTIES, new HashSet<>(task.customPropertiesToRemove));
        }

        if (entriesToAdd == null && keysToRemove == null) {
            task.subStage = nextStage;
            sendSelfPatch(task);
            return;
        }

        ServiceStateMapUpdateRequest serviceStateMapUpdateRequest =
                ServiceStateMapUpdateRequest.create(entriesToAdd, keysToRemove);

        sendWithDeferredResult(Operation
                .createPatch(this, task.endpointState.documentSelfLink)
                .setBody(serviceStateMapUpdateRequest))
                .whenComplete((o, e) -> {
                    if (e != null) {
                        handleError(task, e.getMessage(), o.getStatusCode());
                        return;
                    }

                    task.subStage = nextStage;
                    sendSelfPatch(task);
                });
    }

    /**
     * Force vSphere data collector sync if we change the credential of vSphere account.
     */
    private void startDataCollectorSyncTask(EndpointUpdateTaskState task, SubStage nextStage) {
        VsphereRDCSyncTaskService.VsphereRDCSyncTaskState
                syncTaskState = new VsphereRDCSyncTaskService.VsphereRDCSyncTaskState();

        syncTaskState.endpointLink = task.endpointLink;
        if (!task.isMock) {
            syncTaskState.options = EnumSet.of(TaskOption.SELF_DELETE_ON_COMPLETION);
        }
        syncTaskState.documentExpirationTimeMicros = Utils.fromNowMicrosUtc(TimeUnit.MINUTES
                .toMicros(30));
        Operation createPost = Operation.createPost(this,
                VsphereRDCSyncTaskService.FACTORY_LINK)
                .setBody(syncTaskState)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning(() -> String.format("Error triggering vsphere data sync task, " +
                                "reason: %s", e.getMessage()));
                    }
                    this.setAuthorizationContext(o, null);
                    task.subStage = nextStage;
                    sendSelfPatch(task);
                });
        this.setAuthorizationContext(createPost, getSystemAuthorizationContext());
        createPost.sendWith(this);
    }

    /**
     * Validate and update endpoint owners.
     */
    private void validateAndUpdateOwners(EndpointUpdateTaskState task, SubStage nextStage) {
        if (task.ownerUpdates == null || task.ownerUpdates.isEmpty()) {
            task.subStage = nextStage;
            sendSelfPatch(task);
            return;
        }

        String orgLink = UriUtils.buildUriPath(OrganizationService.FACTORY_LINK,
                getOrgId(task.endpointState));

        Set<String> userEmailsToAddAsOwners = new HashSet<>();
        Set<String> userEmailsToRemoveAsOwners = new HashSet<>();
        Set<String> userEmails = new HashSet<>();

        task.ownerUpdates.stream()
                .forEach(collectionStringFieldUpdate -> {
                    if (collectionStringFieldUpdate.action.equals(UpdateAction.ADD)) {
                        userEmailsToAddAsOwners.add(collectionStringFieldUpdate.value);
                        userEmails.add(collectionStringFieldUpdate.value);
                    } else if (collectionStringFieldUpdate.action.equals(UpdateAction.REMOVE)) {
                        userEmailsToRemoveAsOwners.add(collectionStringFieldUpdate.value);
                        userEmails.add(collectionStringFieldUpdate.value);
                    }
                });

        // Endpoint creator cannot be removed from owners.
        if (task.endpointState != null && task.endpointState.customProperties != null) {
            String createdByEmail = task.endpointState.customProperties
                    .get(ENDPOINT_CUSTOM_PROPERTY_NAME_CREATEDBY_EMAIL);

            if (createdByEmail != null && userEmailsToRemoveAsOwners.contains(createdByEmail)) {
                handleError(task, ErrorUtil.message(CANNOT_REMOVE_CREATOR_FROM_OWNERS),
                        Operation.STATUS_CODE_BAD_REQUEST,
                        CANNOT_REMOVE_CREATOR_FROM_OWNERS.getErrorCode());
                return;
            }
        }

        OperationContext origCtx = OperationContext.getOperationContext();

        Consumer<Throwable> onOwnerValidateError = e -> {
            OperationContext.restoreOperationContext(origCtx);
            handleError(task, ErrorUtil.message(INVALID_USER_FOR_OWNERS_UPDATE),
                    Operation.STATUS_CODE_BAD_REQUEST,
                    INVALID_USER_FOR_OWNERS_UPDATE.getErrorCode());
        };

        Consumer<Throwable> onOwnerUpdateError = e -> {
            OperationContext.restoreOperationContext(origCtx);
            handleError(task, ErrorUtil.message(OWNERS_UPDATE_FAILED),
                    Operation.STATUS_CODE_INTERNAL_ERROR, OWNERS_UPDATE_FAILED.getErrorCode());
        };

        Consumer<Void> onOwnerUpdateSuccess = aVoid -> {
            OperationContext.restoreOperationContext(origCtx);
            task.subStage = nextStage;
            sendSelfPatch(task);
        };

        Consumer<Operation> onOwnerValidateSuccess = o -> {
            OperationContext.restoreOperationContext(origCtx);
            List<CollectionStringFieldUpdate> updateActionByUserSelfLinks = new ArrayList<>();
            QueryTask response = o.getBody(QueryTask.class);

            if (response.results != null && response.results.documents != null) {
                response.results.documents.values().stream()
                        .forEach(doc -> {
                            UserState userState = Utils.fromJson(doc, UserState.class);
                            if (userEmailsToAddAsOwners.contains(userState.email)) {
                                updateActionByUserSelfLinks.add(CollectionStringFieldUpdate.create(
                                        UpdateAction.ADD, userState.documentSelfLink));
                            }
                            if (userEmailsToRemoveAsOwners.contains(userState.email)) {
                                updateActionByUserSelfLinks.add(CollectionStringFieldUpdate.create(
                                        UpdateAction.REMOVE, userState.documentSelfLink));
                            }
                        });
            }

            EndpointUtils.updateOwnersForEndpoint(task.endpointLink,
                    task.endpointState.tenantLinks, this,
                    updateActionByUserSelfLinks, onOwnerUpdateSuccess, onOwnerUpdateError);
        };

        EndpointUtils.validateUsersForEndpointOwnership(orgLink, this,
                userEmails, onOwnerValidateSuccess, onOwnerValidateError);
    }

    /**
     * This method is to get the updated fields in the customProperties.
     * @param task : updateTask
     */
    private void getUpdatedFieldsInCustomProperties(EndpointUpdateTaskState task) {
        if (task.customPropertyUpdates == null) {
            return;
        }
        for (String key : task.customPropertyUpdates.keySet()) {
            CollectionStringFieldUpdate update = task.customPropertyUpdates.get(key);
            switch (update.action) {
            case ADD:
                if (update.value == null) {
                    // no-op
                    // don't allow null value on ADD action
                    // if you wan't to clear a property, then use value of empty string ""
                    // if you want to delete a property, use REMOVE action
                    logWarning("Custom property with key '%s' and ADD action has null value", key);
                    break;
                }
                if (task.customPropertiesToAdd == null) {
                    task.customPropertiesToAdd = new HashMap<>();
                }
                task.customPropertiesToAdd.put(key, update.value);
                break;
            case REMOVE:
                if (task.customPropertiesToRemove == null) {
                    task.customPropertiesToRemove = new HashSet<>();
                }
                task.customPropertiesToRemove.add(key);
                break;
            default:
                logSevere("Invalid Update Action [%s] received", task.serviceUpdate.action);
                break;
            }
        }
    }

    /**
     * Consolidates and sets the property values that need to be updated to the state object
     */
    private void updateEndpointState(EndpointUpdateTaskState task) {
        task.endpointState.name = task.name;
        task.endpointState.desc = task.description;

        if (task.endpointProperties != null) {
            task.endpointState.endpointProperties.putAll(task.endpointProperties);
        }
        if (task.customProperties != null) {
            if (task.endpointState.customProperties == null) {
                task.endpointState.customProperties = new HashMap<>();
            }
            task.endpointState.customProperties.putAll(task.customProperties);
        }

        if (task.endpointState.customProperties != null) {

            if (task.customPropertiesToAdd != null && !task.customPropertiesToAdd.isEmpty()) {
                for (Entry<String, String> entry : task.customPropertiesToAdd.entrySet()) {
                    task.endpointState.customProperties.put(entry.getKey(), entry.getValue());
                }
            }

            if (task.customPropertiesToRemove != null && !task.customPropertiesToRemove.isEmpty()) {
                for (String key : task.customPropertiesToRemove) {
                    task.endpointState.customProperties.remove(key);
                }
            }
        }

        if (task.credentials != null && !task.credentials.isEmpty()) {
            task.endpointState.endpointProperties =
                    reconstructEndpointProperties(task.endpointState.endpointType, task.credentials,
                            task.endpointState.endpointProperties, task.endpointState.customProperties);
        }
    }

    private void createCostUsageReportUpdate(EndpointUpdateTaskService.EndpointUpdateTaskState task,
                                             SubStage nextStage) {
        if (task.isMock) {
            task.subStage = nextStage;
            sendSelfPatch(task);
            return;
        }

        // if not AWS endpoint/account, move to next stage
        if (!EndpointType.aws.name().equalsIgnoreCase(task.endpointState.endpointType)) {
            task.subStage = nextStage;
            sendSelfPatch(task);
            return;
        }

        Map<String, String> customProperties = task.endpointState.customProperties;

        // skip if there are no updates or if all 3 Cost report related properties are null or empty
        // also, skip if s3bucketName is null or empty
        if (task.customPropertyUpdates == null
                || customProperties == null
                || isEmpty(customProperties.get(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET))
                || (!isEmpty(customProperties.get(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET))
                && isEmpty(customProperties.get(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BUCKET_PREFIX))
                && isEmpty(customProperties.get(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_COSTUSAGE_REPORT_NAME)))) {

            task.subStage = nextStage;
            sendSelfPatch(task);
            return;
        }

        String s3bucketName = customProperties.get(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET);
        String bucketPrefix = customProperties.get(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BUCKET_PREFIX);
        String costAndUsageReportName = customProperties.get(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_COSTUSAGE_REPORT_NAME);

        // if s3bucketName is present, but one of the other 2 properties is empty
        // throw an error
        if (!isEmpty(s3bucketName)
                && (isEmpty(bucketPrefix) || isEmpty(costAndUsageReportName))) {

            handleError(task, INVALID_COST_USAGE_PARAMS_ERROR.getMessage(), Operation
                            .STATUS_CODE_BAD_REQUEST, INVALID_COST_USAGE_PARAMS_ERROR.getErrorCode());
            return;
        }

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
     * Find or create tags and associate them with the endpoint.
     * First issue a query to find all existing tags. For tags that were not found, proceed with
     * creation.
     */
    private void findCreateTags(EndpointUpdateTaskState task, SubStage nextStage) {
        // if no tags were provided proceed with endpoint update
        if ((task.tagUpdates == null || task.tagUpdates.isEmpty())) {
            task.subStage = nextStage;
            sendSelfPatch(task);
            return;
        }

        task.tagsMap = new HashMap<>();
        task.tagLinksToRemove = new HashSet<>();
        for (TagFieldUpdate tagUpdate : task.tagUpdates) {
            TagState tempTag = TagsUtil.newTagState(tagUpdate.value.key, tagUpdate.value.value,
                    EnumSet.of(TagState.TagOrigin.USER_DEFINED), new ArrayList<>(task.tenantLinks));
            if (tagUpdate.action.equals(UpdateAction.ADD)) {
                task.tagsMap.put(tempTag.documentSelfLink, tempTag);
            } else if (tagUpdate.action.equals(UpdateAction.REMOVE)) {
                task.tagLinksToRemove.add(tempTag.documentSelfLink);
            }
        }

        // we only need to query for tags that are getting added/updated, since we can construct the
        // selfLink for tags to be removed
        if (task.tagsMap != null && !task.tagsMap.isEmpty()) {
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
                        task.tagLinksToAdd = new HashSet<>();
                        for (Entry<String, TagState> tagEntry : task.tagsMap.entrySet()) {
                            if (response.results.documents.get(tagEntry.getKey()) != null) {
                                tag = Utils.fromJson(response.results.documents.get(tagEntry.getKey()),
                                        TagState.class);
                                if (!tag.origins.contains(TagState.TagOrigin.USER_DEFINED)) {
                                    tag.origins.add(TagState.TagOrigin.USER_DEFINED);
                                    tagsToUpdate.add(tag);
                                } else {
                                    task.tagLinksToAdd.add(tag.documentSelfLink);
                                }

                            } else {
                                tag = Utils.fromJson((tagEntry.getValue()), TagState.class);
                                tag.origins = EnumSet.of(TagState.TagOrigin.USER_DEFINED);
                                tagsToCreate.add(tag);
                            }
                        }
                        createUpdateTags(task, tagsToCreate, tagsToUpdate, nextStage);
                    });
        } else {
            task.subStage = nextStage;
            handleSubStage(task);
            return;
        }
    }

    private void createUpdateTags(EndpointUpdateTaskState task, List<TagState> tagsToCreate,
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
                        // if a failure occurs during tag creation, proceed to endpoint update
                        logWarning("Failure creating one or more tags: %s",
                                failures.values().iterator().next());
                        task.subStage = nextStage;
                        handleSubStage(task);
                        return;
                    }
                    task.tagLinksToAdd = (task.tagLinksToAdd == null) ? new HashSet<>() : task.tagLinksToAdd;
                    for (Operation op : ops.values()) {
                        TagState tagState = op.getBody(TagState.class);
                        task.tagLinksToAdd.add(tagState.documentSelfLink);
                    }
                    task.subStage = nextStage;
                    sendSelfPatch(task);
                    return;
                }).sendWith(this);
    }

    /**
     * Update the tags defined on the endpoint.
     */
    private void updateTagLinks(EndpointUpdateTaskState task, SubStage nextStage) {
        if ((task.tagLinksToAdd == null || task.tagLinksToAdd.isEmpty())
                && (task.tagLinksToRemove == null || task.tagLinksToRemove.isEmpty())) {
            task.subStage = nextStage;
            sendSelfPatch(task);
            return;
        }

        // Do not process tags that have been provided for both addition and removal at the same time
        // This scenario cannot happen through the ui, but it's possible to happen through direct
        // use of the API
        if (task.tagLinksToAdd != null && !task.tagLinksToAdd.isEmpty()) {
            Set<String> common = new HashSet<>(task.tagLinksToAdd);
            common.retainAll(task.tagLinksToRemove);
            if (!common.isEmpty()) {
                task.tagLinksToAdd.removeAll(common);
                task.tagLinksToRemove.removeAll(common);
            }
        }

        Map<String, Collection<Object>> tagLinksToAdd = null;
        Map<String, Collection<Object>> tagLinksToRemove = null;

        if (task.tagLinksToAdd != null && !task.tagLinksToAdd.isEmpty()) {
            tagLinksToAdd = Collections.singletonMap(EndpointState.FIELD_NAME_TAG_LINKS,
                    new HashSet<>(task.tagLinksToAdd));
        }

        if (task.tagLinksToRemove != null && !task.tagLinksToRemove.isEmpty()) {
            tagLinksToRemove = Collections.singletonMap(EndpointState.FIELD_NAME_TAG_LINKS,
                    new HashSet<>(task.tagLinksToRemove));
        }

        if (tagLinksToAdd == null && tagLinksToRemove == null) {
            task.subStage = nextStage;
            sendSelfPatch(task);
            return;
        }

        ServiceStateCollectionUpdateRequest serviceStateCollectionUpdateRequest =
                ServiceStateCollectionUpdateRequest.create(tagLinksToAdd, tagLinksToRemove);

        sendWithDeferredResult(Operation
                .createPatch(this, task.endpointState.documentSelfLink)
                .setBody(serviceStateCollectionUpdateRequest))
                .whenComplete((o, e) -> {
                    if (e != null) {
                        handleError(task, e.getMessage(), o.getStatusCode());
                        return;
                    }

                    task.subStage = nextStage;
                    sendSelfPatch(task);
                });
    }

    private void triggerDataCollection(EndpointUpdateTaskState task, SubStage nextStage) {
        if (task.isMock && !task.isMockRunDataInit) {
            task.subStage = nextStage;
            sendSelfPatch(task);
            return;
        }

        if (task.skipCredentialValidation) {
            task.subStage = nextStage;
            sendSelfPatch(task);
            return;
        }

        DataInitializationTaskService.DataInitializationState state = new
                DataInitializationTaskService.DataInitializationState();
        state.endpoint = task.endpointState;
        state.tenantLinks = new HashSet<>(task.tenantLinks);

        if (task.endpointState != null
                && task.endpointState.documentSelfLink != null) {
            state.documentSelfLink = UriUtils
                    .getLastPathSegment(task.endpointState.documentSelfLink);
        }

        Operation post = Operation
                .createPost(this, DataInitializationTaskService.FACTORY_LINK)
                .setBody(state)
                .setReferer(this.getUri());
        sendWithDeferredResult(post)
                .whenComplete((op, ex) -> {
                    if (ex != null) {
                        log(Level.WARNING,
                                "Error while creating data initialization task while updating " +
                                        "endpoint : %s", ex.getMessage());
                    }
                });

        task.subStage = nextStage;
        sendSelfPatch(task);
    }

    private boolean isVsphereCredentialRemoval(EndpointUpdateTaskState task) {
        if (task.propertyUpdates != null) {
            for (String key : task.propertyUpdates.keySet()) {
                CollectionStringFieldUpdate update = task.propertyUpdates.get(key);
                if ((EndpointUtils.ENDPOINT_PROPERTY_VSPHERE_PASSWORD.equalsIgnoreCase(key) ||
                        EndpointUtils.ENDPOINT_PROPERTY_VSPHERE_USERNAME.equalsIgnoreCase(key))
                        && update != null && UpdateAction.REMOVE.equals(update.action)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void handleError(EndpointUpdateTaskState taskState, String failureMessage) {
        handleError(taskState, failureMessage, Operation.STATUS_CODE_BAD_METHOD);
    }

    private void handleError(EndpointUpdateTaskState taskState, String failureMessage,
            int statusCode) {
        handleError(taskState, failureMessage, statusCode, null);
    }

    private void handleError(EndpointUpdateTaskState taskState,
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

    private void handleError(EndpointUpdateTaskState taskState,
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