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
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_ALREADY_EXISTS;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_TYPE_REQUIRED;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_WITH_S3BUCKETNAME_ALREADY_EXISTS;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.INVALID_ENDPOINT_OWNER;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.INVALID_ENDPOINT_TYPE;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.VSPHERE_DCID_REQUIRED;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.VSPHERE_HOSTNAME_REQUIRED;
import static com.vmware.photon.controller.discovery.cloudaccount.utils.ClusterUtils.createInventoryUri;
import static com.vmware.photon.controller.discovery.cloudaccount.utils.QueryUtils.QUERY_RESULT_LIMIT;
import static com.vmware.photon.controller.discovery.common.utils.InventoryQueryUtils.startInventoryQueryTask;
import static com.vmware.photon.controller.discovery.common.utils.OnboardingUtils.getOrgId;
import static com.vmware.photon.controller.discovery.common.utils.StringUtil.isEmpty;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.buildEndpointAdapterUri;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.failOperation;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.isOperationCredentialsSet;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.reconstructEndpointProperties;
import static com.vmware.photon.controller.discovery.vsphere.VsphereOnPremEndpointAdapterService.ENDPOINT_PROPERTIES_KEY;
import static com.vmware.photon.controller.discovery.vsphere.VsphereOnPremEndpointAdapterService.HOST_NAME_KEY;
import static com.vmware.photon.controller.model.UriPaths.CLOUD_ACCOUNT_API_SERVICE;
import static com.vmware.photon.controller.model.UriPaths.ENDPOINT_VALIDATION_TASK_SERVICE;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.ARN_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEYID_KEY;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType.aws;
import static com.vmware.photon.controller.model.resources.EndpointService.EndpointState.FIELD_NAME_ENDPOINT_TYPE;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.OPTIONAL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SERVICE_USE;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT;
import static com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState.FIELD_NAME_CUSTOM_PROPERTIES;
import static com.vmware.xenon.services.common.QueryTask.QuerySpecification.buildCompositeFieldName;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.vmware.photon.controller.discovery.common.TaskHelper;
import com.vmware.photon.controller.discovery.common.services.OrganizationService;
import com.vmware.photon.controller.discovery.common.utils.ErrorUtil;
import com.vmware.photon.controller.discovery.common.utils.OnboardingUtils;
import com.vmware.photon.controller.discovery.endpoints.EndpointValidationTaskService.EndpointValidationTaskState;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService.EndpointAllocationTaskState;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskFactoryService;
import com.vmware.xenon.services.common.TaskService;


/**
 * Task to validate endpoints.
 */
public class EndpointValidationTaskService extends TaskService<EndpointValidationTaskState> {
    public static final String FACTORY_LINK = ENDPOINT_VALIDATION_TASK_SERVICE;
    public static final String FIELD_NAME_DOCUMENT_SELF_LINK = ServiceDocument.FIELD_NAME_SELF_LINK;

    /**
     * The endpoint validation task state.
     */
    public static class EndpointValidationTaskState extends TaskService.TaskServiceState {
        @Documentation(description = "Endpoint type of the endpoint instance - aws, azure, etc.")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.REQUIRED),
                @UsageOption(option = PropertyUsageOption.SINGLE_ASSIGNMENT) })
        public String type;

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

        @Documentation(description = "This is only for internal usage. It keeps track of internal stages.")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public SubStage subStage;

        @Documentation(description = "The tenant links.")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public List<String> tenantLinks;

        @Documentation(description = "The authCredential links.")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public List<String> authCredentialLinks;

        @Documentation(description = "Endpoint link")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.OPTIONAL),
                @UsageOption(option = PropertyUsageOption.SINGLE_ASSIGNMENT) })
        public String endpointLink;

        @Documentation(description = "Validation options")
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, OPTIONAL, SERVICE_USE })
        public EnumSet<ValidationOption> options = EnumSet.noneOf(ValidationOption.class);

        @Documentation(description = "List of owners to validate for endpoint.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Set<String> owners;
    }

    /**
     * Validation options.
     */
    public enum ValidationOption {

        /**
         * Skips duplicate endpoint validation check
         */
        SKIP_DUPLICATE_ENDPOINT_VALIDATION,

        /**
         * Skips credential validation check
         */
        SKIP_CREDENTIAL_VALIDATION,

        /**
         * Skips S3 duplicate validation check
         */
        SKIP_S3_DUPLICATE_VALIDATION,

        /**
         * Skips S3 Permission validation check
         */
        SKIP_S3_PERMISSION_VALIDATION
    }

    /**
     * Endpoint validation sub-stages.
     */
    public enum SubStage {
        /**
         * Get all the Auth Credentials for the given private key Id
         * Note: Private key Id conceptually represents IAM user for AWS for e.g.
         */
        GET_AUTH_CREDENTIALS,

        /**
         * Check if there are any endpointLinks with retrieved Auth Credentials
         */
        CHECK_DUPLICATE_ENDPOINT,

        /**
         * Check if there is any endpointLink with this s3bucketName already
         */
        VALIDATE_DUPLICATE_S3BUCKET,

        /**
         * Validate endpoint based on resource group link as authz context.
         */
        VALIDATE_ENDPOINT,

        /**
         * Validate if the AWS credentials have access permissions to the provided S3 bucket
         */
        VALIDATE_S3_BUCKET_PERMISSIONS,

        /**
         * Validate whether all requested additional owners are part of the given organization.
         */
        VALIDATE_OWNERS,

        /**
         * Successful validation of endpoint.
         */
        SUCCESS,

        /**
         * Error while validating endpoint.
         */
        ERROR
    }

    public static FactoryService createFactory() {
        TaskFactoryService fs = new TaskFactoryService(EndpointValidationTaskState.class) {
            @Override
            public Service createServiceInstance() throws Throwable {
                return new EndpointValidationTaskService();
            }
        };
        fs.setPeerNodeSelectorPath(ServiceUriPaths.DEFAULT_1X_NODE_SELECTOR);
        return fs;
    }

    public EndpointValidationTaskService() {
        super(EndpointValidationTaskState.class);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handleStart(Operation taskOperation) {
        final EndpointValidationTaskState state;
        if (taskOperation.hasBody()) {
            state = taskOperation.getBody(EndpointValidationTaskState.class);
        } else {
            state = new EndpointValidationTaskState();
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
                    state.tenantLinks = projectLinks;
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
    protected void initializeState(EndpointValidationTaskState task, Operation taskOperation) {
        task.subStage = SubStage.GET_AUTH_CREDENTIALS;
        task.adapterReference = buildEndpointAdapterUri(this.getHost(), task.type);
        if (task.options == null) {
            task.options = EnumSet.noneOf(ValidationOption.class);
        }
        super.initializeState(task, taskOperation);
    }

    @Override
    protected EndpointValidationTaskState validateStartPost(Operation op) {
        EndpointValidationTaskState task = super.validateStartPost(op);
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

        if (!isOperationCredentialsSet(task.endpointProperties, task.credentials)) {
            failOperation(this.getHost(), op, CREDENTIALS_REQUIRED, Operation.STATUS_CODE_BAD_REQUEST);
            return null;
        }

        if (!task.options.contains(ValidationOption.SKIP_CREDENTIAL_VALIDATION)) {
            if (EndpointType.vsphere.name().equals(task.type)) {
                if (!EndpointUtils.isValidVsphereHostName(task.customProperties)) {
                    failOperation(this.getHost(), op, VSPHERE_HOSTNAME_REQUIRED,
                            Operation.STATUS_CODE_BAD_REQUEST);
                    return null;
                }

                if (!EndpointUtils.isValidVsphereDcId(task.customProperties)) {
                    failOperation(this.getHost(), op, VSPHERE_DCID_REQUIRED,
                            Operation.STATUS_CODE_BAD_REQUEST);
                    return null;
                }
            }
        }

        return task;
    }

    @Override
    protected boolean validateTransition(Operation patch, EndpointValidationTaskState currentTask,
            EndpointValidationTaskState patchBody) {
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
        EndpointValidationTaskState currentTask = getState(patch);
        EndpointValidationTaskState patchBody = getBody(patch);

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
     * State machine for endpoint validation.
     */
    private void handleSubStage(EndpointValidationTaskState task) {
        switch (task.subStage) {
        case GET_AUTH_CREDENTIALS:
            getAuthCredentialsForPrivateId(task, SubStage.CHECK_DUPLICATE_ENDPOINT);
            break;
        case CHECK_DUPLICATE_ENDPOINT:
            checkEndpointExistsWithExistingAuth(task, SubStage.VALIDATE_DUPLICATE_S3BUCKET);
            break;
        case VALIDATE_DUPLICATE_S3BUCKET:
            validateDuplicateS3Bucket(task, SubStage.VALIDATE_ENDPOINT);
            break;
        case VALIDATE_ENDPOINT:
            validateEndpoint(task, SubStage.VALIDATE_S3_BUCKET_PERMISSIONS);
            break;
        case VALIDATE_S3_BUCKET_PERMISSIONS:
            validateAwsS3BucketPermissions(task, SubStage.VALIDATE_OWNERS);
            break;
        case VALIDATE_OWNERS:
            validateOwners(task, SubStage.SUCCESS);
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

    private void getAuthCredentialsForPrivateId(EndpointValidationTaskState task, SubStage
            nextStage) {
        QueryTask queryTask;
        try {
            queryTask = buildAuthCredentialQueryTask(task);
        } catch (IllegalAccessException e) {
            task.subStage = SubStage.ERROR;
            task.failureMessage = e.getMessage();
            sendSelfPatch(task);
            return;
        }

        startInventoryQueryTask(this, queryTask, false)
                .whenComplete((response, e) -> {
                    if (e != null) {
                        logWarning(Utils.toString(e));
                        task.subStage = SubStage.ERROR;
                        task.failureMessage = e.getMessage();
                        task.taskInfo.failure.message = e.getMessage();
                        sendSelfPatch(task);
                        return;
                    }

                    if (response.results == null || response.results.nextPageLink == null) {
                        task.subStage = nextStage;
                        sendSelfPatch(task);
                        return;
                    }

                    collectAuthCredentialsByQueryPage(task, response.results.nextPageLink,
                            nextStage);
                });

    }

    /**
     * Collects pages of auth credentials.
     */
    private void collectAuthCredentialsByQueryPage(EndpointValidationTaskState task,
                                                   String nextPageLink, SubStage nextStage) {
        if (nextPageLink == null) {
            task.subStage = nextStage;
            sendSelfPatch(task);
            return;
        }

        sendWithDeferredResult(Operation.createGet(createInventoryUri(this.getHost(), nextPageLink)))
                .whenComplete((o, e) -> {
                    if (e != null) {
                        task.failureMessage = e.getMessage();
                        task.subStage = SubStage.ERROR;
                        sendSelfPatch(task);
                        return;
                    }

                    QueryTask response = o.getBody(QueryTask.class);

                    if (response.results != null && response.results.documentLinks != null) {
                        if (task.authCredentialLinks == null) {
                            task.authCredentialLinks = new ArrayList<>();
                        }
                        task.authCredentialLinks.addAll(response.results.documentLinks);
                    }

                    if (response.results != null) {
                        collectAuthCredentialsByQueryPage(task, response.results.nextPageLink,
                                nextStage);
                        return;
                    }

                    task.subStage = nextStage;
                    sendSelfPatch(task);
                });
    }


    /**
     * Checks if there are any endpoints whose authCredential matches with the authCredentials
     * found for the given private Key Id.
     */
    private void checkEndpointExistsWithExistingAuth(EndpointValidationTaskState task,
            SubStage nextStage) {

        if (task.authCredentialLinks == null || task.authCredentialLinks.isEmpty()) {
            task.subStage = nextStage;
            sendSelfPatch(task);
            return;
        }

        if (task.options.contains(ValidationOption.SKIP_DUPLICATE_ENDPOINT_VALIDATION)) {
            task.subStage = nextStage;
            sendSelfPatch(task);
            return;
        }

        QueryTask queryTask = buildEndpointQueryTask(task);
        DeferredResult<QueryTask> startQueryDR = startInventoryQueryTask(this, queryTask);

        startQueryDR.exceptionally(e -> {
            logWarning(Utils.toString(e));
            task.subStage = SubStage.ERROR;
            task.failureMessage = e.getMessage();
            sendSelfPatch(task);
            return null;
        });

        startQueryDR.thenAccept(response -> {
            if (response.results == null || response.results.nextPageLink == null) {
                task.subStage = nextStage;
                sendSelfPatch(task);
                return;
            }

            Operation nextPageOp = Operation
                    .createGet(createInventoryUri(this.getHost(), response.results.nextPageLink))
                    .setReferer(this.getUri());

            retrieveEndpointNextPageResults(task, nextPageOp);
        });
    }

    private void retrieveEndpointNextPageResults(EndpointValidationTaskState task, Operation nextPageOp) {
        sendWithDeferredResult(nextPageOp, QueryTask.class)
                .thenAccept(epResponse -> {

                    String message = "Unknown EndpointLink!";
                    if (epResponse.results != null
                            && epResponse.results.documentLinks != null
                            && !epResponse.results.documentLinks.isEmpty()) {
                        String endpointId = UriUtils.getLastPathSegment(
                                epResponse.results.documentLinks.get(0));
                        String cloudAccountLink = UriUtils.buildUriPath(
                                CLOUD_ACCOUNT_API_SERVICE, endpointId);
                        // We want to pass the found endpointLink as the message, so the client can
                        // show it
                        message = cloudAccountLink;
                    }
                    logWarning(message);
                    handleError(task, message,
                            Operation.STATUS_CODE_BAD_REQUEST,
                            ENDPOINT_ALREADY_EXISTS.getErrorCode());
                })
                .exceptionally(throwable -> {
                    logWarning(() -> String.format("Failed to retrieve the " +
                                    "duplicate endpoint: %s ",
                            throwable.getLocalizedMessage()));
                    task.failureMessage = throwable.getMessage();
                    task.subStage = SubStage.ERROR;
                    sendSelfPatch(task);
                    return null;
                });
    }

    /**
     * Builds QueryTask for Auth Credentials that have the provided privateKeyId.
     * If tenantLinks are specified, adds clauses for that and creates a
     * QueryTask to be executed in proper context.
     */
    public static QueryTask buildAuthCredentialQueryTask(EndpointValidationTaskState task)
            throws IllegalAccessException {
        Query.Builder qBuilder = QueryTask.Query.Builder.create()
                .addKindFieldClause(AuthCredentialsServiceState.class);

        if (task.credentials.aws != null && task.credentials.aws.arn != null) {
            qBuilder.addFieldClause(
                    buildCompositeFieldName(FIELD_NAME_CUSTOM_PROPERTIES, ARN_KEY),
                    task.credentials.aws.arn);
        } else {
            String privateKey = EndpointUtils.getPrivateKeyIdFromCredentials(task.type,
                    task.credentials);

            if (privateKey == null) {
                throw new IllegalAccessException("Could not retrieve private key from endpoint.");
            }

            qBuilder.addFieldClause(PRIVATE_KEYID_KEY, privateKey);
        }

        QueryUtils.addTenantLinks(qBuilder, task.tenantLinks);

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .setQuery(qBuilder.build())
                .addOption(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT)
                .setResultLimit(QUERY_RESULT_LIMIT)
                .build();

        if (task.tenantLinks != null && !task.tenantLinks.isEmpty()) {
            queryTask.tenantLinks = new ArrayList<>(task.tenantLinks);
        }

        return queryTask;
    }


    /**
     * Builds QueryTask for endpoint query.
     * If tenantLinks are specified, adds clauses for that and creates a
     * QueryTask to be executed in proper context.
     */
    private static QueryTask buildEndpointQueryTask(EndpointValidationTaskState task) {

        QueryTask.Query.Builder qBuilder = QueryTask.Query.Builder.create()
                .addKindFieldClause(EndpointState.class)
                .addInClause(EndpointState.FIELD_NAME_AUTH_CREDENTIALS_LINK,
                        task.authCredentialLinks);

        // endpointLink is not null in the update flow, exclude finding itself
        if (!isEmpty(task.endpointLink)) {
            qBuilder.addFieldClause(FIELD_NAME_DOCUMENT_SELF_LINK,
                    task.endpointLink, Query.Occurance.MUST_NOT_OCCUR);
        }

        QueryUtils.addTenantLinks(qBuilder, task.tenantLinks);

        if (EndpointType.vsphere.name().equals(task.type) &&
                task.credentials.vsphere != null) {
            qBuilder.addCompositeFieldClause(ENDPOINT_PROPERTIES_KEY,
                    HOST_NAME_KEY, task.customProperties.get(HOST_NAME_KEY));
        }

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .setQuery(qBuilder.build())
                .setResultLimit(QUERY_RESULT_LIMIT)
                .build();

        if (task.tenantLinks != null && !task.tenantLinks.isEmpty()) {
            queryTask.tenantLinks = new ArrayList<>(task.tenantLinks);
        }

        return queryTask;
    }


    /**
     * Checks if there are any existing endpoints whose s3 Bucket Name is same as the one being
     * validated.
     * Note: This validation is expected to happen only for CI_SERVICE
     */
    private void validateDuplicateS3Bucket(EndpointValidationTaskState task, SubStage nextStage) {

        // if not AWS endpoint/account, move to next stage
        if (!task.type.equals(aws.name()) || task.credentials.aws == null) {
            task.subStage = nextStage;
            sendSelfPatch(task);
            return;
        }

        if (task.customProperties == null || isEmpty(task.customProperties.get
                (ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET))) {
            task.subStage = nextStage;
            sendSelfPatch(task);
            return;
        }

        if (task.options.contains(ValidationOption.SKIP_S3_DUPLICATE_VALIDATION)) {
            task.subStage = nextStage;
            sendSelfPatch(task);
            return;
        }

        String s3bucketName = task.customProperties.get
                (ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET);

        //build endpointQuery whose 'customProperties.cost_insight:s3bucketName'=task.s3bucketName
        QueryTask queryTask = buildEndpointQueryforAwsS3BucketName(task);
        DeferredResult<QueryTask> startQueryDR = QueryUtils
                .startInventoryQueryTask(this, queryTask);

        startQueryDR.exceptionally(e -> {
            logWarning(Utils.toString(e));
            task.subStage = SubStage.ERROR;
            task.failureMessage = e.getMessage();
            sendSelfPatch(task);
            return null;
        });

        startQueryDR.thenAccept(response -> {
            if (response.results == null || response.results.nextPageLink == null) {
                task.subStage = nextStage;
                sendSelfPatch(task);
                return;
            }

            Operation nextPageOp = Operation
                    .createGet(createInventoryUri(this.getHost(), response.results.nextPageLink))
                    .setReferer(this.getUri());

            retrieveNextPageResultsOfEndpointsWithS3Bucket(task, s3bucketName, nextPageOp);
        });
    }

    private void retrieveNextPageResultsOfEndpointsWithS3Bucket(
            EndpointValidationTaskState task, String s3bucketName, Operation nextPageOp) {

        sendWithDeferredResult(nextPageOp, QueryTask.class)
                .thenAccept(epResponse -> {
                    String errorMsg = null;
                    if (epResponse.results != null
                            && epResponse.results.documentLinks != null
                            && !epResponse.results.documentLinks.isEmpty()) {
                        String endpointId = UriUtils.getLastPathSegment(
                                epResponse.results.documentLinks.get(0));
                        String cloudAccountLink = UriUtils.buildUriPath(
                                CLOUD_ACCOUNT_API_SERVICE, endpointId);
                        errorMsg = cloudAccountLink;
                    }
                    logWarning(errorMsg);
                    handleError(task, errorMsg,
                            Operation.STATUS_CODE_BAD_REQUEST,
                            ENDPOINT_WITH_S3BUCKETNAME_ALREADY_EXISTS.getErrorCode());
                })
                .exceptionally(throwable -> {
                    logWarning(() -> String.format("Failed to retrieve the " +
                                            "endpoint whose s3bucketName matches " +
                                            "with %s : %s ", s3bucketName,
                                    throwable.getLocalizedMessage()));
                    task.failureMessage = throwable.getMessage();
                    task.subStage = SubStage.ERROR;
                    sendSelfPatch(task);
                    return null;
                });
    }


    /**
     * Builds QueryTask for endpoint query.
     * If tenantLinks are specified, adds clauses for that and creates a
     * QueryTask to be executed in proper context.
     */
    private static QueryTask buildEndpointQueryforAwsS3BucketName(EndpointValidationTaskState task) {

        QueryTask.Query.Builder qBuilder = QueryTask.Query.Builder.create()
                .addKindFieldClause(EndpointState.class)
                .addFieldClause(FIELD_NAME_ENDPOINT_TYPE, aws.name().toLowerCase());

        // endpointLink is not null in the update flow, exclude finding itself
        if (!isEmpty(task.endpointLink)) {
            qBuilder.addFieldClause(FIELD_NAME_DOCUMENT_SELF_LINK,
                    task.endpointLink, Query.Occurance.MUST_NOT_OCCUR);
        }

        QueryUtils.addTenantLinks(qBuilder, task.tenantLinks);

        qBuilder.addCompositeFieldClause(EndpointState.FIELD_NAME_CUSTOM_PROPERTIES,
                ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET,
                task.customProperties.get(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET));

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .setQuery(qBuilder.build())
                .setResultLimit(QUERY_RESULT_LIMIT)
                .build();

        if (task.tenantLinks != null && !task.tenantLinks.isEmpty()) {
            queryTask.tenantLinks = new ArrayList<>(task.tenantLinks);
        }

        return queryTask;
    }

    /**
     * Validates the Aws S3 bucket Permissions
     */
    private void validateAwsS3BucketPermissions(EndpointValidationTaskState task,
                                                SubStage nextStage) {

        if (task.customProperties == null || isEmpty(task.customProperties.get
                (ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET))) {
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

        if (task.options.contains(ValidationOption.SKIP_S3_PERMISSION_VALIDATION)) {
            task.subStage = nextStage;
            sendSelfPatch(task);
            return;
        }

        AwsEndpointS3ValidationTaskService.S3ValidationTaskState s3ValidationTaskState = new
                AwsEndpointS3ValidationTaskService.S3ValidationTaskState();
        s3ValidationTaskState.credentials = task.credentials;
        s3ValidationTaskState.s3bucketName = task.customProperties.get
                    (ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET);
        s3ValidationTaskState.isMock = task.isMock;
        s3ValidationTaskState.taskInfo = TaskState.createDirect();

        Operation s3validateOp = Operation
                .createPost(this, AwsEndpointS3ValidationTaskService.FACTORY_LINK)
                .setBody(s3ValidationTaskState);
        setAuthorizationContext(s3validateOp, getSystemAuthorizationContext());
        OperationContext origCtx = OperationContext.getOperationContext();
        sendWithDeferredResult(s3validateOp)
                .whenComplete((o, e) -> {
                    OperationContext.restoreOperationContext(origCtx);
                    if (e != null) {
                        logWarning(Utils.toString(e));
                        handleError(task, e.getMessage(), o.getStatusCode());
                        return;
                    }

                    AwsEndpointS3ValidationTaskService.S3ValidationTaskState body =
                            o.getBody(AwsEndpointS3ValidationTaskService.S3ValidationTaskState.class);
                    if (body.taskInfo != null && body.taskInfo.stage == TaskStage.FAILED) {
                        if (body.taskInfo.failure != null) {
                            handleError(task, body.taskInfo.failure.message,
                                    body.taskInfo.failure.statusCode,
                                    body.taskInfo.failure.messageId != null ?
                                    Integer.parseInt(body.taskInfo.failure.messageId) :
                                            Integer.MIN_VALUE);
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
     * Validates the endpoint.
     */
    private void validateEndpoint(EndpointValidationTaskState task, SubStage nextStage) {

        if (task.options.contains(ValidationOption.SKIP_CREDENTIAL_VALIDATION)) {
            task.subStage = nextStage;
            sendSelfPatch(task);
            return;
        }

        EndpointState endpointState = new EndpointState();
        endpointState.name = task.documentSelfLink;
        endpointState.endpointType = task.type;
        if (EndpointType.vsphere.name().equals(task.type)) {
            endpointState.endpointType = EndpointUtils.VSPHERE_ON_PREM_ADAPTER;
        }
        endpointState.endpointProperties = reconstructEndpointProperties(endpointState.endpointType,
                task.credentials, task.endpointProperties, task.customProperties);
        endpointState.customProperties = task.customProperties;
        endpointState.tenantLinks = task.tenantLinks;

        EndpointAllocationTaskState endpointTaskState = new EndpointAllocationTaskState();
        endpointTaskState.adapterReference = task.adapterReference;
        endpointTaskState.endpointState = endpointState;
        endpointTaskState.options = EnumSet.of(TaskOption.VALIDATE_ONLY);
        if (task.isMock) {
            endpointTaskState.options.add(TaskOption.IS_MOCK);
        }

        endpointTaskState.tenantLinks = task.tenantLinks;
        endpointTaskState.taskInfo = TaskState.createDirect();
        sendWithDeferredResult(Operation
                .createPost(this, EndpointAllocationTaskService.FACTORY_LINK)
                .setBody(endpointTaskState))
                .whenComplete((o, e) -> {
                    if (e != null) {
                        logWarning(Utils.toString(e));
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
                    sendSelfPatch(task);
                });
    }

    /**
     * Validate whether all requested additional owners are part of the given organization.
     */
    private void validateOwners(EndpointValidationTaskState task, SubStage nextStage) {
        if (task.owners == null || task.owners.isEmpty()) {
            task.subStage = nextStage;
            sendSelfPatch(task);
            return;
        }

        Consumer<Throwable> onError = e -> {
            handleError(task, ErrorUtil.message(INVALID_ENDPOINT_OWNER),
                    Operation.STATUS_CODE_OK, INVALID_ENDPOINT_OWNER.getErrorCode());
        };

        Consumer<Operation> onSuccess = aVoid -> {
            task.subStage = nextStage;
            sendSelfPatch(task);
        };

        String orgId = getOrgId(task.tenantLinks);
        String orgLink = UriUtils.buildUriPath(OrganizationService.FACTORY_LINK, orgId);
        EndpointUtils.validateUsersForEndpointOwnership(orgLink, this, task.owners,
                onSuccess, onError);
    }

    private void handleError(EndpointValidationTaskState taskState, String failureMessage,
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

    private void handleError(EndpointValidationTaskState taskState, String failureMessage,
                             int statusCode, int errorCode) {
        logWarning("Task failed at sub-stage %s with failure: %s", taskState.subStage,
                failureMessage);

        taskState.subStage = SubStage.ERROR;
        ServiceErrorResponse e = new ServiceErrorResponse();
        e.message = failureMessage;
        e.statusCode = statusCode;
        e.messageId = (errorCode != Integer.MIN_VALUE) ? String.valueOf(errorCode) : null;
        taskState.taskInfo.failure = e;
        sendSelfPatch(taskState);
    }
}