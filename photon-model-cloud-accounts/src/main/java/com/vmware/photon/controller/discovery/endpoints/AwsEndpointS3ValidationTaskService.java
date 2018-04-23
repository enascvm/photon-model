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
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.INVALID_S3_BUCKETNAME;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.S3_BUCKET_PERMISSIONS_ERROR;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.failOperation;
import static com.vmware.photon.controller.model.UriPaths.AWS_ENDPOINT_S3_VALIDATION_TASK_SERVICE;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.ARN_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.EXTERNAL_ID_KEY;

import java.util.HashMap;
import java.util.concurrent.ExecutorService;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.model.AmazonS3Exception;

import org.apache.commons.lang3.StringUtils;

import com.vmware.photon.controller.discovery.common.ErrorCode;
import com.vmware.photon.controller.discovery.common.TaskHelper;
import com.vmware.photon.controller.discovery.endpoints.AwsEndpointS3ValidationTaskService.S3ValidationTaskState;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskFactoryService;
import com.vmware.xenon.services.common.TaskService;


/**
 * Task to validate S3 bucket access permissions for AWS credentials.
 */
public class AwsEndpointS3ValidationTaskService extends TaskService<S3ValidationTaskState> {

    public static final String FACTORY_LINK = AWS_ENDPOINT_S3_VALIDATION_TASK_SERVICE;

    private ExecutorService executorService;

    /**
     * The Aws endpoint s3 bucket validation task state.
     */
    public static class S3ValidationTaskState extends TaskService.TaskServiceState {

        @Documentation(description = "Endpoint's credentials")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.OPTIONAL),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public Credentials credentials;

        @Documentation(description = "AWS s3 bucket name to be validated")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.REQUIRED),
                @UsageOption(option = PropertyUsageOption.SINGLE_ASSIGNMENT) })
        public String s3bucketName;

        @Documentation(description = "Indicates a mock request")
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public boolean isMock = false;

        @Documentation(description = "This is only for internal usage. It keeps track of internal stages.")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public SubStage subStage;

    }

    /**
     * S3 access permissions validation sub-stages.
     */
    public enum SubStage {
        /**
         * Validate if the AWS credentials have listObjects and putObject
         * access permissions to the provided S3 bucket
         */
        VALIDATE_S3_BUCKET_PERMISSIONS,

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
        TaskFactoryService fs = new TaskFactoryService(S3ValidationTaskState.class) {
            @Override
            public Service createServiceInstance() throws Throwable {
                return new AwsEndpointS3ValidationTaskService();
            }
        };
        fs.setPeerNodeSelectorPath(ServiceUriPaths.DEFAULT_1X_NODE_SELECTOR);
        return fs;
    }

    public AwsEndpointS3ValidationTaskService() {
        super(S3ValidationTaskState.class);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handleStart(Operation taskOperation) {
        final S3ValidationTaskState state;
        // Initialize an executor with fixed thread pool.
        this.executorService = getHost().allocateExecutor(this);

        if (taskOperation.hasBody()) {
            state = taskOperation.getBody(S3ValidationTaskState.class);
        } else {
            state = new S3ValidationTaskState();
        }

        taskOperation.setBody(state);
        super.handleStart(taskOperation);
    }

    @Override
    public void handleStop(Operation op) {
        this.executorService.shutdown();
        AdapterUtils.awaitTermination(this.executorService);
        super.handleStop(op);
    }

    @Override
    protected void initializeState(S3ValidationTaskState task, Operation taskOperation) {
        task.subStage = SubStage.VALIDATE_S3_BUCKET_PERMISSIONS;
        super.initializeState(task, taskOperation);
    }

    @Override
    protected S3ValidationTaskState validateStartPost(Operation op) {
        S3ValidationTaskState task = super.validateStartPost(op);
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

        if (task.credentials == null || task.credentials.aws == null ||
                ((StringUtils.isEmpty(task.credentials.aws.accessKeyId) ||
                StringUtils.isEmpty(task.credentials.aws.secretAccessKey)) &&
                        (StringUtils.isEmpty(task.credentials.aws.arn)))) {
            failOperation(this.getHost(), op, CREDENTIALS_REQUIRED, Operation.STATUS_CODE_BAD_REQUEST);
            return null;
        }

        if (StringUtils.isEmpty(task.s3bucketName)) {
            failOperation(this.getHost(), op, INVALID_S3_BUCKETNAME, Operation
                    .STATUS_CODE_BAD_REQUEST, task.s3bucketName);
            return null;
        }

        return task;
    }

    @Override
    protected boolean validateTransition(Operation patch, S3ValidationTaskState currentTask,
            S3ValidationTaskState patchBody) {
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
        S3ValidationTaskState currentTask = getState(patch);
        S3ValidationTaskState patchBody = getBody(patch);

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
     * State machine for s3 validation.
     */
    private void handleSubStage(S3ValidationTaskState task) {
        switch (task.subStage) {
        case VALIDATE_S3_BUCKET_PERMISSIONS:
            validateS3BucketPermissions(task, SubStage.SUCCESS);
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
     * This method calls AWS listObjects() to determine S3 access
     * AWS SDK does not support async call to listObjects, so we use the synchronous method
     * in a fixed thread pool.
     */
    private void validateS3BucketPermissions(S3ValidationTaskState task, SubStage nextStage) {
        if (task.isMock) {
            task.subStage = nextStage;
            sendSelfPatch(task);
            return;
        }

        String s3bucketName = task.s3bucketName;
        OperationContext origCtx = OperationContext.getOperationContext();

        this.executorService.submit(new Runnable() {

            private AWSClientManager s3ClientManager = AWSClientManagerFactory
                    .getClientManager(AWSConstants.AwsClientType.S3_TRANSFER_MANAGER);

            @Override
            public void run() {
                OperationContext.restoreOperationContext(origCtx);
                AuthCredentialsServiceState authCredentialState = new AuthCredentialsServiceState();

                if (task.credentials.aws.arn != null) {
                    authCredentialState.customProperties = new HashMap<>();
                    authCredentialState.customProperties.put(ARN_KEY, task.credentials.aws.arn);
                    authCredentialState.customProperties.put(EXTERNAL_ID_KEY,
                            task.credentials.aws.externalId);
                } else {
                    authCredentialState.privateKeyId = task.credentials.aws.accessKeyId;
                    authCredentialState.privateKey = task.credentials.aws.secretAccessKey;
                }

                AWSUtils.getS3TransferManagerAsync(authCredentialState,
                        Regions.DEFAULT_REGION.getName(), s3ClientManager.getExecutor())
                        .whenComplete((s3Client, e) -> {
                            if (e != null) {
                                handleError(task, e.getMessage(),
                                        Operation.STATUS_CODE_BAD_REQUEST,
                                        S3_BUCKET_PERMISSIONS_ERROR);
                                return;
                            }

                            try {
                                s3Client.getAmazonS3Client().listObjects(s3bucketName);

                                task.subStage = nextStage;
                                sendSelfPatch(task);
                            } catch (AmazonS3Exception ex) {
                                handleError(task, ex.getMessage(),
                                        Operation.STATUS_CODE_BAD_REQUEST, S3_BUCKET_PERMISSIONS_ERROR);
                            } catch (Exception ex) {
                                handleError(task, ex.getMessage(),
                                        Operation.STATUS_CODE_INTERNAL_ERROR, null);
                            }
                        });
            }
        });
    }


    private void handleError(S3ValidationTaskState taskState, String failureMessage, int statusCode,
            ErrorCode errorCode) {
        logWarning("Task failed at sub-stage %s with failure: %s", taskState.subStage,
                failureMessage);

        taskState.subStage = SubStage.ERROR;
        ServiceErrorResponse e = new ServiceErrorResponse();
        if (errorCode != null) {
            e.messageId = String.valueOf(errorCode.getErrorCode());
        }
        e.message = failureMessage;
        e.statusCode = statusCode;
        taskState.taskInfo.failure = e;
        sendSelfPatch(taskState);
    }
}