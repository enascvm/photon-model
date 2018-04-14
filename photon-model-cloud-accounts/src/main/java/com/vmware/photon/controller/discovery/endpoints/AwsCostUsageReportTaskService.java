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
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.DUPLICATE_COST_USAGE_REPORT_DIFFERENT_PREFIX_ERROR;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.INVALID_COST_AND_USAGE_REPORT_NAME;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.INVALID_S3_BUCKETNAME;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.INVALID_S3_BUCKET_PREFIX;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.S3_COST_USAGE_EXCEPTION;
import static com.vmware.photon.controller.discovery.endpoints.CostUsageReportCreationUtils.checkIfReportAlreadyExists;
import static com.vmware.photon.controller.discovery.endpoints.CostUsageReportCreationUtils.createCostAndUsageReportOnAws;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.failOperation;
import static com.vmware.photon.controller.model.UriPaths.AWS_COST_USAGE_REPORT_TASK_SERVICE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.getS3TransferManagerAsync;

import java.util.EnumSet;
import java.util.concurrent.ExecutorService;

import com.amazonaws.services.costandusagereport.model.AWSCostAndUsageReportException;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.HeadBucketRequest;
import com.amazonaws.services.s3.transfer.TransferManager;

import org.apache.commons.lang3.StringUtils;

import com.vmware.photon.controller.discovery.common.ErrorCode;
import com.vmware.photon.controller.discovery.common.TaskHelper;
import com.vmware.photon.controller.discovery.endpoints.AwsCostUsageReportTaskService.AwsCostUsageReportTaskState;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskFactoryService;
import com.vmware.xenon.services.common.TaskService;


/**
 * Task to create AWS Cost usage report.
 */
public class AwsCostUsageReportTaskService extends
        TaskService<AwsCostUsageReportTaskState> {

    public static final String FACTORY_LINK = AWS_COST_USAGE_REPORT_TASK_SERVICE;

    public static final String AWS_DUPLICATE_REPORT_NAME_EXCEPTION = "DuplicateReportNameException";

    private ExecutorService executorService;

    public static class AwsCostUsageReportTaskState extends TaskService.TaskServiceState {

        @Documentation(description = "This is only for internal usage. It keeps track of internal stages.")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)})
        public SubStage subStage;

        @Documentation(description = "Credentials")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)})
        public Credentials credentials;

        @Documentation(description = "AWS s3 bucket name to be validated")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.REQUIRED),
                @UsageOption(option = PropertyUsageOption.SINGLE_ASSIGNMENT) })
        public String s3bucketName;

        @Documentation(description = "AWS s3 bucket prefix")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.REQUIRED),
                @UsageOption(option = PropertyUsageOption.SINGLE_ASSIGNMENT) })
        public String s3bucketPrefix;

        @Documentation(description = "Cost and Report Name")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.REQUIRED),
                @UsageOption(option = PropertyUsageOption.SINGLE_ASSIGNMENT) })
        public String costUsageReportName;

        @Documentation(description = "Indicates a mock request")
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public boolean isMock = false;

        /**
         * Task options
         */
        public EnumSet<TaskOption> options;
    }

    public enum SubStage {

        /**
         *  Create the report
         */
        CREATE_REPORT,

        /**
         * Successfully created the report
         */
        SUCCESS,

        /**
         * Error while creating the report
         */
        ERROR
    }


    public AwsCostUsageReportTaskService() {
        super(AwsCostUsageReportTaskState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.toggleOption(ServiceOption.PERIODIC_MAINTENANCE, true);
    }

    public static FactoryService createFactory() {
        TaskFactoryService fs = new TaskFactoryService(AwsCostUsageReportTaskState.class) {
            @Override
            public Service createServiceInstance() throws Throwable {
                return new AwsCostUsageReportTaskService();
            }
        };
        fs.setPeerNodeSelectorPath(ServiceUriPaths.DEFAULT_1X_NODE_SELECTOR);
        return fs;
    }

    @Override
    public void handleStart(Operation taskOperation) {
        final AwsCostUsageReportTaskState state;
        // Initialize an executor with fixed thread pool.
        this.executorService = getHost().allocateExecutor(this);

        if (taskOperation.hasBody()) {
            state = taskOperation.getBody(AwsCostUsageReportTaskState.class);
        } else {
            state = new AwsCostUsageReportTaskState();
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
    protected void initializeState(AwsCostUsageReportTaskState task, Operation taskOperation) {
        task.subStage = SubStage.CREATE_REPORT;
        if (task.options == null) {
            task.options = EnumSet.noneOf(TaskOption.class);
        }
        super.initializeState(task, taskOperation);
    }

    @Override
    protected AwsCostUsageReportTaskState validateStartPost(Operation op) {

        AwsCostUsageReportTaskState task = super.validateStartPost(op);
        if (task == null) {
            return null;
        }

        if (task.taskInfo != null && (TaskState.isCancelled(task.taskInfo)
                || TaskState.isFailed(task.taskInfo)
                || TaskState.isFinished(task.taskInfo))) {
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

        if (StringUtils.isEmpty(task.s3bucketPrefix)) {
            failOperation(this.getHost(), op, INVALID_S3_BUCKET_PREFIX, Operation
                    .STATUS_CODE_BAD_REQUEST, task.s3bucketPrefix);
            return null;
        }

        if (StringUtils.isEmpty(task.costUsageReportName)) {
            failOperation(this.getHost(), op, INVALID_COST_AND_USAGE_REPORT_NAME, Operation
                    .STATUS_CODE_BAD_REQUEST, task.costUsageReportName);
            return null;
        }

        return task;
    }

    @Override
    protected boolean validateTransition(Operation patch, AwsCostUsageReportTaskState currentTask,
                                         AwsCostUsageReportTaskState patchBody) {
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
        AwsCostUsageReportTaskState patchBody = getBody(patch);
        AwsCostUsageReportTaskState currentTask = getState(patch);

        if (!validateTransition(patch, currentTask, patchBody)) {
            return;
        }
        updateState(currentTask, patchBody);
        patch.complete();

        switch (currentTask.taskInfo.stage) {
        case STARTED:
            handleSubStage(currentTask, patch);
            break;
        case FINISHED:
            logFine("Cost and Usage Report Task finished successfully");
            handleTaskCompleted(currentTask);
            break;
        case FAILED:
            logWarning("Task failed: %s", (currentTask.failureMessage == null ? "No reason given"
                    : currentTask.failureMessage));
            handleTaskCompleted(currentTask);
            break;
        default:
            logWarning("Unexpected stage: %s", currentTask.taskInfo.stage);
            break;
        }
    }


    private void handleSubStage(AwsCostUsageReportTaskState task, Operation patch) {
        switch (task.subStage) {
        case CREATE_REPORT:
            createCostUsageReport(task, SubStage.SUCCESS);
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
     * This method creates cost and usage configuration report for cloud endpoint addition/update
     */
    private void createCostUsageReport(AwsCostUsageReportTaskState task, SubStage nextStage) {
        if (task.isMock) {
            task.subStage = nextStage;
            sendSelfPatch(task);
            return;
        }

        AuthCredentialsService.AuthCredentialsServiceState authCredentialState =
                EndpointUtils.getAwsAuthCredentialsServiceState(task.credentials);

        OperationContext origCtx = OperationContext.getOperationContext();

        this.executorService.submit(new Runnable() {
            private AWSClientManager s3ClientManager = AWSClientManagerFactory
                    .getClientManager(AWSConstants.AwsClientType.S3);

            @Override
            public void run() {
                OperationContext.restoreOperationContext(origCtx);
                getS3TransferManagerAsync(authCredentialState, null,
                        this.s3ClientManager.getExecutor())
                        .whenComplete((s3Client, e) -> {
                            if (e != null) {
                                handleError(task, e.getMessage(), Operation
                                        .STATUS_CODE_BAD_REQUEST, null);
                                return;
                            }
                            try {

                                invokePutReportDefinitionAPI(task, s3Client);

                            } catch (AWSCostAndUsageReportException ex) {

                                String errorCode = ex.getErrorCode();
                                if (errorCode != null // duplicate cost and usage report exists
                                        && AWS_DUPLICATE_REPORT_NAME_EXCEPTION.equalsIgnoreCase(errorCode)) {

                                    handleError(task, ex.getMessage(), Operation.STATUS_CODE_BAD_REQUEST,
                                            DUPLICATE_COST_USAGE_REPORT_DIFFERENT_PREFIX_ERROR);
                                    return;

                                } else {
                                    // for e.g. if AWS throws AccessDeniedException to change
                                    // costReport because its a linkedAccount and not master account
                                    handleError(task, ex.getMessage(),
                                            Operation.STATUS_CODE_BAD_REQUEST, S3_COST_USAGE_EXCEPTION);
                                    return;
                                }
                            } catch (AmazonS3Exception ex) {
                                handleError(task, ex.getMessage(),
                                        Operation.STATUS_CODE_BAD_REQUEST, S3_COST_USAGE_EXCEPTION);
                                return;

                            } catch (Exception ex) {
                                handleError(task, ex.getMessage(),
                                        Operation.STATUS_CODE_INTERNAL_ERROR, S3_COST_USAGE_EXCEPTION);
                                return;
                            }

                            task.subStage = nextStage;
                            sendSelfPatch(task);
                        });
            }
        });
    }


    private void invokePutReportDefinitionAPI(AwsCostUsageReportTaskState task,
            TransferManager s3Client) throws com.amazonaws.services.costandusagereport.model.AWSCostAndUsageReportException {

        Credentials credentials = task.credentials;
        boolean foundReport = checkIfReportAlreadyExists(credentials, task.s3bucketName,
                task.s3bucketPrefix, task.costUsageReportName);

        // skip steps to create report on AWS
        if (foundReport) {
            return;
        }

        String bucketRegion = s3Client.getAmazonS3Client()
                .headBucket(new HeadBucketRequest(task.s3bucketName)).getBucketRegion();

        createCostAndUsageReportOnAws(credentials, bucketRegion, task.s3bucketName, task
                .s3bucketPrefix, task.costUsageReportName);
    }


    private void handleError(AwsCostUsageReportTaskState taskState, String failureMessage,
             int statusCode, ErrorCode errorCode) {
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

    /**
     * Self delete this task if specified through {@link TaskOption#SELF_DELETE_ON_COMPLETION}
     * option.
     */
    private void handleTaskCompleted(AwsCostUsageReportTaskState taskState) {
        if (taskState.options != null &&
                taskState.options.contains(TaskOption.SELF_DELETE_ON_COMPLETION)) {
            sendWithDeferredResult(Operation.createDelete(getUri()))
                    .whenComplete((o, e) -> {
                        if (e != null) {
                            logSevere(() -> String.format(
                                    "Self-delete upon %s stage: FAILED with %s at subStage: %s",
                                    taskState.taskInfo.stage, Utils.toString(e), taskState.subStage));
                        } else {
                            logInfo(() -> String.format(
                                    "Self-delete upon %s stage: SUCCESS", taskState.taskInfo.stage));
                        }
                    });
        }
    }
}
