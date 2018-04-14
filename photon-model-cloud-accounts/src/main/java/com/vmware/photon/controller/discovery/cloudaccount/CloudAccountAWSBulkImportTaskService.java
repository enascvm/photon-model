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

package com.vmware.photon.controller.discovery.cloudaccount;

import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountAWSBulkImportTaskService.SubStage.COMPLETED;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountAWSBulkImportTaskService.SubStage.CREATE_CLOUD_ACCOUNTS;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountAWSBulkImportTaskService.SubStage.TRIGGER_DATA_INITIALIZATION;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountApiService.createEndpointTaskState;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_ALREADY_EXISTS;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_NAME_REQUIRED;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.AWS_BULK_IMPORT_TASK_DATA_INITIALIZATION_BATCH_SIZE_PROP;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.AWS_BULK_IMPORT_TASK_EXPIRATION_MINUTES;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.COMMA;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.EQUALS;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.SEMICOLON;
import static com.vmware.photon.controller.discovery.common.authn.AuthContextService.getAuthContextOrgId;
import static com.vmware.photon.controller.discovery.common.utils.OnboardingUtils.assumeIdentity;
import static com.vmware.photon.controller.discovery.endpoints.EndpointCreationTaskService.triggerDataInitOnEndpoint;
import static com.vmware.photon.controller.model.UriPaths.AWS_BULK_IMPORT_TASK_SERVICE;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.ARN_KEY;
import static com.vmware.photon.controller.model.adapters.util.AdapterConstants.PHOTON_MODEL_ADAPTER_UNAUTHORIZED_MESSAGE;
import static com.vmware.xenon.common.Operation.CR_LF;
import static com.vmware.xenon.common.Operation.STATUS_CODE_INTERNAL_ERROR;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SERVICE_USE;
import static com.vmware.xenon.common.TaskState.TaskStage.FAILED;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import com.vmware.photon.controller.discovery.cloudaccount.CloudAccountAWSBulkImportTaskService.AWSBulkImportTaskState;
import com.vmware.photon.controller.discovery.cloudaccount.CloudAccountApiService.CloudAccountCreateRequest;
import com.vmware.photon.controller.discovery.common.TagViewState;
import com.vmware.photon.controller.discovery.common.utils.OnboardingUtils;
import com.vmware.photon.controller.discovery.endpoints.Credentials;
import com.vmware.photon.controller.discovery.endpoints.EndpointCreationTaskService;
import com.vmware.photon.controller.discovery.endpoints.EndpointCreationTaskService.EndpointCreationTaskState;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.TaskService;

/**
 * A task service to handle bulk importing of CSV files into the {@link CloudAccountApiService}.
 *
 * This service includes the following steps -
 *
 * 1. Parse a CSV file into individual rows
 * 2. Invoke a POST to {@link CloudAccountApiService} with details ready for cloud account creation.
 * 3. Store the response - positive or negative.
 * 4. Once all rows are completed, return the complete list of positive & negative csvResponseStates.
 */
public class CloudAccountAWSBulkImportTaskService extends
        TaskService<AWSBulkImportTaskState> {

    public static final String FACTORY_LINK = AWS_BULK_IMPORT_TASK_SERVICE;

    // Stats Tracking
    public static final String DATA_INITIALIZATION_INVOCATION_COUNT = "dataInitializationInvocationCountStat";

    // Maximum state size for the service document description. Upon migration, this needs to be
    // reset to enable these documents to be migrated.
    private static final int MAX_STATE_SIZE = 1024 * 1024;

    private static final String UNABLE_TO_VALIDATE_CREDENTIALS_IN_ANY_AWS_REGION =
            "Unable to validate credentials in any AWS region!";

    public static final int MAX_ENDPOINT_CREATION_ATTEMPTS = 3;
    public static final String MAXIMUM_ENDPOINT_CREATION_ATTEMPTS_MESSAGE =
            "Maximum endpoint creation attempts reached";

    private static final int DEFAULT_DATA_INITIALIZATION_BATCH_SIZE = 100;
    private final Integer DATA_INITIALIZATION_BATCH_SIZE =
            Integer.getInteger(AWS_BULK_IMPORT_TASK_DATA_INITIALIZATION_BATCH_SIZE_PROP, DEFAULT_DATA_INITIALIZATION_BATCH_SIZE) > 0 ?
                    Integer.getInteger(AWS_BULK_IMPORT_TASK_DATA_INITIALIZATION_BATCH_SIZE_PROP,
                            DEFAULT_DATA_INITIALIZATION_BATCH_SIZE) : DEFAULT_DATA_INITIALIZATION_BATCH_SIZE;

    private static final String AWS_BULK_IMPORT_CSV_HEADER_IDENTIFIER = "Identifier";
    private static final String AWS_BULK_IMPORT_CSV_HEADER_NICKNAME = "Nickname";
    private static final String AWS_BULK_IMPORT_CSV_HEADER_DESCRIPTION = "Description";
    private static final String AWS_BULK_IMPORT_CSV_HEADER_OWNERS = "Owners";
    private static final String AWS_BULK_IMPORT_CSV_HEADER_TAGS = "Tags";
    private static final String AWS_BULK_IMPORT_CSV_HEADER_ERROR = "Error";
    private static final String AWS_BULK_IMPORT_CSV_HEADER_IS_MOCK = "Is Mock";

    private static final List<String> CLOUD_ACCOUNT_AWS_BULK_IMPORT_CSV_HEADERS_LIST =
            Collections.unmodifiableList(new ArrayList<>(Arrays.asList(
                    AWS_BULK_IMPORT_CSV_HEADER_IDENTIFIER,
                    AWS_BULK_IMPORT_CSV_HEADER_NICKNAME, AWS_BULK_IMPORT_CSV_HEADER_DESCRIPTION,
                    AWS_BULK_IMPORT_CSV_HEADER_OWNERS, AWS_BULK_IMPORT_CSV_HEADER_TAGS)));
    private static final List<String> CLOUD_ACCOUNT_AWS_BULK_IMPORT_CSV_HEADERS_LIST_WITH_ERROR_COLUMN =
            Collections.unmodifiableList(concatenatedArrayList(
                    new ArrayList<>(CLOUD_ACCOUNT_AWS_BULK_IMPORT_CSV_HEADERS_LIST),
                    AWS_BULK_IMPORT_CSV_HEADER_ERROR));
    private static final List<String> CLOUD_ACCOUNT_AWS_BULK_IMPORT_CSV_HEADERS_LIST_WITH_IS_MOCK_COLUMN =
            Collections.unmodifiableList(concatenatedArrayList(
                    new ArrayList<>(CLOUD_ACCOUNT_AWS_BULK_IMPORT_CSV_HEADERS_LIST),
                    AWS_BULK_IMPORT_CSV_HEADER_IS_MOCK));

    // Have the default time to expire be 8 days (just over 1 week).
    public static final int DEFAULT_TASK_EXPIRATION_MINUTES = (int) TimeUnit.DAYS.toMinutes(8);
    public final Integer TASK_EXPIRATION_MINUTES =
            Integer.getInteger(AWS_BULK_IMPORT_TASK_EXPIRATION_MINUTES, DEFAULT_TASK_EXPIRATION_MINUTES) > 0 ?
                    Integer.getInteger(AWS_BULK_IMPORT_TASK_EXPIRATION_MINUTES, DEFAULT_TASK_EXPIRATION_MINUTES) :
                    DEFAULT_TASK_EXPIRATION_MINUTES;

    /**
     * The task state for AWS bulk import task service.
     */
    public static class AWSBulkImportTaskState extends TaskService.TaskServiceState {

        public static final String GENERIC_ERROR_RESPONSE =
                "An internal error occurred. Please retry this request";
        public static final String AWS_WAS_UNABLE_TO_VALIDATE_CREDENTIALS_RESPONSE =
                "AWS was unable to validate the provided credentials";

        public static final Map<Integer, String> ERROR_CODE_MATCHER = new HashMap<>();

        static {
            ERROR_CODE_MATCHER.put(ENDPOINT_NAME_REQUIRED.getErrorCode(),
                    "Nickname is required");
            ERROR_CODE_MATCHER.put(ENDPOINT_ALREADY_EXISTS.getErrorCode(),
                    "A duplicate account was found at: %s");
        }

        @Documentation(description = "The download link to the resulting CSV file.")
        @UsageOption(option = AUTO_MERGE_IF_NOT_NULL)
        public String csvDownloadLink;

        /**
         * The CSV file (in String format). If in invalid format, this will be rejected and the task
         * will fail.
         */
        @UsageOptions(value = {
                @UsageOption(option = AUTO_MERGE_IF_NOT_NULL),
                @UsageOption(option = SERVICE_USE) })
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY)
        public String csv;

        /**
         * Successful Cloud Account Creation responses mapped with CSV rows
         */
        @UsageOptions(value = {
                @UsageOption(option = AUTO_MERGE_IF_NOT_NULL),
                @UsageOption(option = SERVICE_USE) })
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY)
        public Set<SuccessfulImportState> cloudAccountImportSuccesses;

        /**
         * Failed Cloud Account Creation responses mapped with CSV rows
         */
        @UsageOptions(value = {
                @UsageOption(option = AUTO_MERGE_IF_NOT_NULL),
                @UsageOption(option = SERVICE_USE) })
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY)
        public Set<FailedImportState> cloudAccountImportFailures;

        /**
         * The substage to track task progress.
         */
        @UsageOptions(value = {
                @UsageOption(option = AUTO_MERGE_IF_NOT_NULL),
                @UsageOption(option = PropertyUsageOption.INFRASTRUCTURE) })
        public SubStage subStage;

        /**
         * The list of rows found in the CSV file.
         */
        @UsageOptions(value = {
                @UsageOption(option = SERVICE_USE),
                @UsageOption(option = AUTO_MERGE_IF_NOT_NULL),
                @UsageOption(option = PropertyUsageOption.SINGLE_ASSIGNMENT) })
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY)
        public Set<CloudAccountRecordState> requestsToProcess;

        @UsageOptions(value = {
                @UsageOption(option = SERVICE_USE),
                @UsageOption(option = AUTO_MERGE_IF_NOT_NULL) })
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY)
        public LinkedList<String> endpointLinksToEnumerate;

        @Documentation(description = "The tenant links.")
        @UsageOptions({
                @UsageOption(option = SERVICE_USE),
                @UsageOption(option = AUTO_MERGE_IF_NOT_NULL),
                @UsageOption(option = PropertyUsageOption.SINGLE_ASSIGNMENT) })
        public Set<String> tenantLinks;

        @Documentation(description = "The user link who started this service")
        @UsageOptions({
                @UsageOption(option = SERVICE_USE),
                @UsageOption(option = AUTO_MERGE_IF_NOT_NULL),
                @UsageOption(option = PropertyUsageOption.SINGLE_ASSIGNMENT) })
        public String userLink;

        @Documentation(description = "The user's organization ID")
        @UsageOptions({
                @UsageOption(option = SERVICE_USE),
                @UsageOption(option = AUTO_MERGE_IF_NOT_NULL),
                @UsageOption(option = PropertyUsageOption.SINGLE_ASSIGNMENT) })
        public String orgId;

        @Documentation(description = "Enable the 'is mock' header to be used in CSV parsing.")
        @UsageOptions({
                @UsageOption(option = SERVICE_USE),
                @UsageOption(option = AUTO_MERGE_IF_NOT_NULL) })
        public boolean isMock = false;

        /**
         * Converts this document into a CSV file (string) that can be downloaded as a CSV file.
         * @param includeSuccess Includes the successful rows in the final CSV file.
         * @param includeErrors Includes the error rows in the final CSV file.
         * @return A string that can be downloaded as a CSV file.
         */
        public String convertToCsv(boolean includeSuccess, boolean includeErrors) throws IOException {
            // Add \r\n as line separator per RFC spec (https://tools.ietf.org/html/rfc4180)
            try (StringWriter writer = new StringWriter();
                    CSVPrinter csvPrinter = new CSVPrinter(writer,
                            CSVFormat.DEFAULT.withRecordSeparator(CR_LF))) {

                List<String> csvHeaders = includeErrors ?
                        CLOUD_ACCOUNT_AWS_BULK_IMPORT_CSV_HEADERS_LIST_WITH_ERROR_COLUMN :
                        CLOUD_ACCOUNT_AWS_BULK_IMPORT_CSV_HEADERS_LIST;

                csvPrinter.printRecord(csvHeaders);

                if (includeErrors) {
                    for (FailedImportState failure : this.cloudAccountImportFailures) {
                        List<String> csvRecord = new ArrayList<>(
                                convertToCsvRecordColumns(failure.csvRecord));
                        csvRecord.add(reconstructFailureMessage(failure));
                        csvPrinter.printRecord(csvRecord);
                    }
                }

                if (includeSuccess) {
                    for (SuccessfulImportState success : this.cloudAccountImportSuccesses) {
                        csvPrinter.printRecord(new ArrayList<>(
                                convertToCsvRecordColumns(success.csvRecord)));
                    }
                }

                // Flush contents to the writer
                csvPrinter.flush();
                return writer.toString();
            }
        }

        /**
         * Method to reconstruct the failure message of a {@link FailedImportState} object to
         * a message that may be a bit more helpful to the end user.
         *
         * Translates:
         *
         * - Duplicate account messages to be:
         *      "A duplicate account was found at: /api/cloud-accounts/{id}"
         * - Invalid credential messages to be:
         *      {@link #AWS_WAS_UNABLE_TO_VALIDATE_CREDENTIALS_RESPONSE}
         * - Any error message with an error code (either parsed from the message, or from the
         *   messageId:
         *      A potential remapping of the provided error code message (based on
         *      {@link #ERROR_CODE_MATCHER}). Otherwise, the error code message itself.
         * - All other errors:
         *      {@link #GENERIC_ERROR_RESPONSE}
         *
         * @param failure The failed import state object
         * @return A reconstructed failure message suitable for an end-user.
         */
        private String reconstructFailureMessage(FailedImportState failure) {

            // 1. Check errors where an error code is thrown (parse out the error code) or extract
            //    from the ServiceErrorResponse
            Matcher errorMatcher = FIND_SERVICE_ERROR_RESPONSE_MESSAGE.matcher(failure.error.message);
            if (errorMatcher.matches()) {
                return reconstructErrorCodeMessage(errorMatcher.group(1), errorMatcher.group(2));
            }

            if (failure.error.messageId != null) {
                return reconstructErrorCodeMessage(failure.error.messageId, failure.error.message);
            }

            // 2. Check errors for invalid credentials
            if (failure.error.message.contains(PHOTON_MODEL_ADAPTER_UNAUTHORIZED_MESSAGE) ||
                    failure.error.message.contains(UNABLE_TO_VALIDATE_CREDENTIALS_IN_ANY_AWS_REGION)) {
                return AWS_WAS_UNABLE_TO_VALIDATE_CREDENTIALS_RESPONSE;
            }

            // 3. Return a generic error for any other errors
            return GENERIC_ERROR_RESPONSE;
        }

        /**
         * Helper method to reconstruct an error code message (if it needs to be reconstructed).
         * If no match in {@link #ERROR_CODE_MATCHER} is found, then just return the passed in
         * baseErrorMessage.
         *
         * @param errorMessageId The error code.
         * @param baseErrorMessage The base error message (will be returned if no reconversion is
         *                         found).
         * @return A potentially reconstructed error code message if needed, otherwise the passed in
         *         baseErrorMessage.
         */
        private String reconstructErrorCodeMessage(String errorMessageId, String baseErrorMessage) {
            Integer errorMessageIdInt = Integer.valueOf(errorMessageId);
            if (ERROR_CODE_MATCHER.containsKey(errorMessageIdInt)) {

                String errorMessageFmt = ERROR_CODE_MATCHER.get(errorMessageIdInt);
                if (errorMessageFmt.contains("%s")) {
                    return String.format(errorMessageFmt, baseErrorMessage);
                }

                // Else, return the reformatted error.
                return errorMessageFmt;
            }

            return baseErrorMessage;
        }

        /**
         * Helper method to convert a {@link CloudAccountRecordState} object into the base common row
         * for AWS Bulk Import row states.
         * @param record The record to convert.
         * @return A list of strings corresponding to the primary columns of a response CSV record.
         */
        private List<String> convertToCsvRecordColumns(CloudAccountRecordState record) {
            List<String> csvRecord = new ArrayList<>();
            csvRecord.add(record.identifier);
            csvRecord.add(record.name);
            csvRecord.add(record.description);
            csvRecord.add(record.owners);
            csvRecord.add(record.tags);
            return csvRecord;
        }
    }

    public enum SubStage {
        /**
         * Stage to create cloud accounts from the parsed CSV
         */
        CREATE_CLOUD_ACCOUNTS,

        /**
         * Stage to trigger data initialization in batches.
         */
        TRIGGER_DATA_INITIALIZATION,

        /**
         * Stage to return completed results of create cloud account requests.
         */
        COMPLETED
    }

    public CloudAccountAWSBulkImportTaskService() {
        super(AWSBulkImportTaskState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    protected AWSBulkImportTaskState validateStartPost(Operation op) {
        AWSBulkImportTaskState state = super.validateStartPost(op);

        if (state == null) {
            op.fail(new IllegalArgumentException("State cannot be null"));
            return null;
        }

        if (state.csv == null) {
            op.fail(new IllegalArgumentException("'csv' cannot be null."));
            return null;
        }

        return state;
    }

    @Override
    protected void initializeState(AWSBulkImportTaskState state, Operation op) {
        state.subStage = CREATE_CLOUD_ACCOUNTS;
        state.cloudAccountImportSuccesses = new HashSet<>();
        state.cloudAccountImportFailures = new HashSet<>();
        state.endpointLinksToEnumerate = new LinkedList<>();
        if (state.taskInfo == null) {
            state.taskInfo = TaskState.create();
        }
        setExpiration(state, this.TASK_EXPIRATION_MINUTES, TimeUnit.MINUTES);
        super.initializeState(state, op);
    }

    @Override
    public void handleStart(Operation op) {
        final AWSBulkImportTaskState state = op.getBody(AWSBulkImportTaskState.class);

        // If the task was migrated or restarted and the task is in a non-created stage, allow the
        // task to simply finish. Short-circuiting the logic introduces at TaskService#handleStart,
        //
        if (!ServiceHost.isServiceCreate(op) ||
                (state.taskInfo != null && !TaskState.isCreated(state.taskInfo))) {
            logInfo("Completing task '%s' as it was migrated in an already completed state.",
                    state.documentSelfLink);
            op.complete();
            return;
        }

        try {
            state.requestsToProcess = parseCSV(state);
            if (state.requestsToProcess == null || state.requestsToProcess.size() == 0) {
                op.fail(new Exception("No CSV records found to process."));
                return;
            }
        } catch (Exception e) {
            op.fail(e);
            return;
        }

        state.userLink = OperationContext.getAuthorizationContext().getClaims().getSubject();
        state.orgId = getAuthContextOrgId(OperationContext.getAuthorizationContext());

        if (state.tenantLinks != null && !state.tenantLinks.isEmpty()) {
            super.handleStart(op);
            return;
        }

        OnboardingUtils.getProjectLinks(this, (projectLinks, f) -> {
            try {
                if (f != null) {
                    throw f;
                }
                state.tenantLinks = new HashSet<>(projectLinks);
                op.setBody(state);
                super.handleStart(op);
            } catch (Throwable t) {
                logSevere("Failed during creation: %s", Utils.toString(t));
                op.fail(t);
            }
        });
    }

    @Override
    public void handlePatch(Operation patch) {
        AWSBulkImportTaskState body = getBody(patch);
        AWSBulkImportTaskState currentState = getState(patch);

        if (!validateTransition(patch, currentState, body)) {
            return;
        }
        updateState(currentState, body);

        currentState.requestsToProcess = body.requestsToProcess;
        currentState.endpointLinksToEnumerate = body.endpointLinksToEnumerate;

        patch.complete();

        switch (currentState.taskInfo.stage) {
        case STARTED:
            handleStagePatch(currentState);
            break;
        case FINISHED:
            logFine("Task finished successfully");
            break;
        case FAILED:
            logWarning("Task failed: %s", (body.failureMessage == null ? "No reason given"
                    : body.failureMessage));
            break;
        default:
            logWarning("Unexpected stage: %s", body.taskInfo.stage);
            break;
        }
    }

    private void handleStagePatch(AWSBulkImportTaskState currentState) {
        switch (currentState.subStage) {
        case CREATE_CLOUD_ACCOUNTS:
            createAwsEndpoints(currentState, TRIGGER_DATA_INITIALIZATION);
            break;
        case TRIGGER_DATA_INITIALIZATION:
            triggerBatchedDataInitialization(currentState, COMPLETED);
            break;
        case COMPLETED:
            sendSelfFinishedPatch(currentState);
            break;
        default:
            break;
        }
    }

    /**
     * Method to create a cloud account with the current corresponding CSV record index. Instead
     * of calling {@link CloudAccountApiService} directly, the task will bypass straight to the
     * {@link EndpointCreationTaskService} call with an
     * additional flag (`skipInitialDataInitialization`) that is unavailable to the public API.
     *
     * This stage will loop until each account has been attempted to have been added.
     *
     * @param currentState The current task state.
     * @param nextStage The next stage to achieve once all cloud accounts have attempted to have
     *                  been added.
     */
    private void createAwsEndpoints(AWSBulkImportTaskState currentState, SubStage nextStage) {

        // Declare a stageHandler for this method as it recurses into its own stage.
        Consumer<Void> stageHandler = (aVoid) -> {
            if (currentState.requestsToProcess.isEmpty()) {

                // Now that all requests have been processed, present the download link
                // (crafted by the CloudAccountApiService).
                currentState.csvDownloadLink =
                        CloudAccountApiService.createAwsBulkImportCsvLink(currentState);
                currentState.subStage = nextStage;
            }

            sendSelfPatch(currentState);
        };

        Optional<CloudAccountRecordState> cloudAccountRecordOptional =
                currentState.requestsToProcess.stream().findFirst();

        // If there are no more values in the iterator, then return. This can occur if the last
        // CSV row was invalid (could not be translated to a CloudAccountCreateRequest object.
        if (!cloudAccountRecordOptional.isPresent()) {
            stageHandler.accept(null);
            return;
        }

        CloudAccountRecordState cloudAccountRecordState = cloudAccountRecordOptional.get();
        currentState.requestsToProcess.remove(cloudAccountRecordState);

        cloudAccountRecordState.endpointCreationAttempts++;
        if (cloudAccountRecordState.endpointCreationAttempts >= MAX_ENDPOINT_CREATION_ATTEMPTS) {
            currentState.cloudAccountImportFailures.add(
                    new FailedImportState()
                    .withCsvRecord(cloudAccountRecordState)
                    .withError(ServiceErrorResponse.create(
                            new Exception(MAXIMUM_ENDPOINT_CREATION_ATTEMPTS_MESSAGE),
                            Operation.STATUS_CODE_INTERNAL_ERROR)));
            stageHandler.accept(null);
            return;
        }

        EndpointCreationTaskState endpointCreationTaskState;
        try {
            endpointCreationTaskState = createEndpointTaskState(
                    convertCsvRecordToCloudAccountCreateRequest(cloudAccountRecordState),
                    currentState.orgId);
        } catch (IllegalArgumentException e) {
            // If the cloud account request was improper, then mark as a failure and move on.
            currentState.cloudAccountImportFailures.add(
                    new FailedImportState()
                            .withCsvRecord(cloudAccountRecordState)
                            .withError(ServiceErrorResponse.create(e, Operation.STATUS_CODE_BAD_REQUEST)));
            stageHandler.accept(null);
            return;
        }

        // Skip the initial data initialization task in favour of doing in the future stage
        // TRIGGER_DATA_INITIALIZATION.
        endpointCreationTaskState.skipInitialDataInitialization = true;

        Operation createEndpoint =
                Operation.createPost(this, EndpointCreationTaskService.FACTORY_LINK)
                        .setBody(endpointCreationTaskState);

        // Assume the identity of the user (in the context of the original org) for this request.
        try {
            assumeIdentity(this, createEndpoint, currentState.userLink, currentState.orgId);
        } catch (GeneralSecurityException e) {
            sendSelfFailurePatch(currentState, e.getMessage());
            return;
        }

        // Send the request.
        sendWithDeferredResult(createEndpoint)
                .whenComplete((op, t) -> {
                    FailedImportState failedImportState = getImportFailure(op, t);
                    if (failedImportState != null) {

                        // If the error that occurred is not an expected, known error (could involve
                        // an Inventory restart, a temporary network outage, a temporary factory
                        // outage, etc.), let this cloud account attempt try again.
                        if (!isKnownError(failedImportState.error)) {
                            currentState.requestsToProcess.add(cloudAccountRecordState);
                            stageHandler.accept(null);
                            return;
                        }

                        // Mark the failure and move on.
                        currentState.cloudAccountImportFailures.add(
                                failedImportState.withCsvRecord(cloudAccountRecordState));
                    } else {

                        // Mark the record as successful with its corresponding cloud account link,
                        // and add the endpoint to a list of endpoints to be enumerated.
                        EndpointCreationTaskState state = op.getBody(EndpointCreationTaskState.class);
                        currentState.cloudAccountImportSuccesses.add(new SuccessfulImportState()
                                .withCloudAccountLink(
                                        UriUtils.buildUriPath(CloudAccountApiService.SELF_LINK,
                                                UriUtils.getLastPathSegment(state.endpointLink)))
                                .withCsvRecord(cloudAccountRecordState)
                        );
                        currentState.endpointLinksToEnumerate.add(state.endpointLink);
                    }

                    stageHandler.accept(null);
                });
    }

    /**
     * Method to trigger (in batches) the {@link DataInitializationTaskService}
     * and other services that are (by default) started by {@link EndpointCreationTaskService}
     * that the bulk import task has otherwise deferred until this stage.
     *
     * This stage will loop until each account has been attempted to have been added.
     *
     * @param currentState The current task state.
     * @param nextStage The next stage to achieve once all data initialization tasks have completed.
     */
    private void triggerBatchedDataInitialization(AWSBulkImportTaskState currentState, SubStage nextStage) {

        // If no more endpointLinks to cycle through and no operations to handle, then continue in
        // the task.
        if (currentState.endpointLinksToEnumerate.isEmpty()) {
            logInfo("Completed data initialization tasks for bulk import.");
            currentState.subStage = nextStage;
            sendSelfPatch(currentState);
            return;
        }

        // For each endpoint link within the batch size (the minimum between the remaining
        // `endpointLinksToEnumerate` size or `AWS_BULK_IMPORT_TASK_DATA_INITIALIZATION_BATCH_SIZE_PROP`), trigger data
        // initialization and wait for all the parallel operations in the batch to complete before
        // re-running this stage.
        logInfo("Running batched set of data initialization tasks for bulk import.");
        OnboardingUtils.adjustStat(this, DATA_INITIALIZATION_INVOCATION_COUNT, 1.0);

        // Assume the identity of the user (in the context of the original org) for this request.
        Operation getOpBase = new Operation().setAction(Action.GET).setReferer(getUri());
        try {
            assumeIdentity(this, getOpBase, currentState.userLink, currentState.orgId);
        } catch (GeneralSecurityException e) {
            sendSelfFailurePatch(currentState, e.getMessage());
            return;
        }

        DeferredResult.allOf(
                IntStream.range(0, Math.min(this.DATA_INITIALIZATION_BATCH_SIZE,
                        currentState.endpointLinksToEnumerate.size()))
                        .boxed()
                        .map(i -> currentState.endpointLinksToEnumerate.removeFirst())
                        .map(endpointLink ->
                                sendWithDeferredResult(getOpBase.clone()
                                        .setUri(UriUtils.buildUri(getHost(), endpointLink)))
                                .thenApply(o -> o.getBody(EndpointState.class))
                                .thenApply(endpoint ->
                                        triggerDataInitOnEndpoint(this, endpoint,
                                                currentState.tenantLinks, TaskState.createDirect())))
                        .collect(Collectors.toList()))
                .whenComplete((ops, t) -> sendSelfPatch(currentState));
    }

    /**
     * Stage handler to parse the CSV file. If parsed successfully, sets the current states
     * `requestsToProcess` field. Otherwise, fails the task.
     *
     * @param currentState The current task state.
     */
    private Set<CloudAccountRecordState> parseCSV(AWSBulkImportTaskState currentState)
            throws IOException {
        Reader csvReader = new StringReader(currentState.csv);

        List<String> csvHeaders = currentState.isMock ?
                CLOUD_ACCOUNT_AWS_BULK_IMPORT_CSV_HEADERS_LIST_WITH_IS_MOCK_COLUMN :
                CLOUD_ACCOUNT_AWS_BULK_IMPORT_CSV_HEADERS_LIST;

        try (CSVParser csvParser =
                new CSVParser(csvReader, CSVFormat.DEFAULT
                        .withHeader(csvHeaders.toArray(new String[csvHeaders.size()]))
                        .withIgnoreHeaderCase()
                        .withIgnoreEmptyLines()
                        .withIgnoreSurroundingSpaces()
                        .withTrim())) {

            return csvParser.getRecords().stream()
                    .filter(csvRecord -> !isHeaderRow(csvRecord, currentState.isMock))
                    .filter(csvRecord -> !isEmptyRow(csvRecord, currentState.isMock))
                    .map(csvRecord -> {
                        CloudAccountRecordState cloudAccountRecordState = new CloudAccountRecordState()
                                .withIdentifier(csvRecord.get(AWS_BULK_IMPORT_CSV_HEADER_IDENTIFIER))
                                .withName(csvRecord.get(AWS_BULK_IMPORT_CSV_HEADER_NICKNAME))
                                .withDescription(csvRecord.get(
                                        AWS_BULK_IMPORT_CSV_HEADER_DESCRIPTION))
                                .withOwners(csvRecord.get(AWS_BULK_IMPORT_CSV_HEADER_OWNERS))
                                .withTags(csvRecord.get(AWS_BULK_IMPORT_CSV_HEADER_TAGS));

                        cloudAccountRecordState.isMock = currentState.isMock ?
                                Boolean.valueOf(csvRecord.get(AWS_BULK_IMPORT_CSV_HEADER_IS_MOCK)) :
                                false;
                        return cloudAccountRecordState;
                    }).collect(Collectors.toSet());
        }
    }

    /**
     * Helper method to determine if the error returned is considered "known" or not. That is,
     * determines if the error message has easily identifiable values that map to well-known
     * errors or if they relate to deeper, more difficult-to-verify errors.
     *
     * @param failure The {@link FailedImportState} to verify.
     * @return True if a known error, false otherwise.
     */
    private boolean isKnownError(ServiceErrorResponse failure) {
        Matcher errorMatcher = FIND_SERVICE_ERROR_RESPONSE_MESSAGE.matcher(failure.message);

        // If the error maps to a known error code message or a credential validation error,
        // then it is considered known and will return true. Otherwise, return false.
        return errorMatcher.matches() || failure.messageId != null ||
                failure.message.contains(PHOTON_MODEL_ADAPTER_UNAUTHORIZED_MESSAGE) ||
                failure.message.contains(UNABLE_TO_VALIDATE_CREDENTIALS_IN_ANY_AWS_REGION);

    }

    /**
     * Determines if a specific {@link CSVRecord} includes the header row or not.
     *
     * @param csvRecord           The {@link CSVRecord} object to check.
     * @param isMockHeaderEnabled If set to true, will include the header
     *                            {@link #AWS_BULK_IMPORT_CSV_HEADER_IS_MOCK} for parsing.
     * @return True if the row matches the expected header, false otherwise.
     */
    private boolean isHeaderRow(CSVRecord csvRecord, boolean isMockHeaderEnabled) {
        boolean baseHeaderMatch = csvRecord.get(AWS_BULK_IMPORT_CSV_HEADER_IDENTIFIER)
                .equalsIgnoreCase(AWS_BULK_IMPORT_CSV_HEADER_IDENTIFIER) &&
                csvRecord.get(AWS_BULK_IMPORT_CSV_HEADER_NICKNAME)
                        .equalsIgnoreCase(AWS_BULK_IMPORT_CSV_HEADER_NICKNAME) &&
                csvRecord.get(AWS_BULK_IMPORT_CSV_HEADER_DESCRIPTION)
                        .equalsIgnoreCase(AWS_BULK_IMPORT_CSV_HEADER_DESCRIPTION) &&
                csvRecord.get(AWS_BULK_IMPORT_CSV_HEADER_OWNERS)
                        .equalsIgnoreCase(AWS_BULK_IMPORT_CSV_HEADER_OWNERS) &&
                csvRecord.get(AWS_BULK_IMPORT_CSV_HEADER_TAGS)
                        .equalsIgnoreCase(AWS_BULK_IMPORT_CSV_HEADER_TAGS);
        if (isMockHeaderEnabled) {
            baseHeaderMatch &= csvRecord.get(AWS_BULK_IMPORT_CSV_HEADER_IS_MOCK) != null &&
                    csvRecord.get(AWS_BULK_IMPORT_CSV_HEADER_IS_MOCK)
                            .equalsIgnoreCase(AWS_BULK_IMPORT_CSV_HEADER_IS_MOCK);
        }
        return baseHeaderMatch;
    }

    /**
     * Determines if the row is completely empty.
     *
     * @param csvRecord           The {@link CSVRecord} object to check.
     * @param isMockHeaderEnabled If set to true, will include the header
     *                            {@link #AWS_BULK_IMPORT_CSV_HEADER_IS_MOCK} for parsing.
     * @return True if the row is completely empty, false otherwise.
     */
    private boolean isEmptyRow(CSVRecord csvRecord, boolean isMockHeaderEnabled) {
        boolean baseRowCheck = csvRecord.get(AWS_BULK_IMPORT_CSV_HEADER_IDENTIFIER).isEmpty() &&
                csvRecord.get(AWS_BULK_IMPORT_CSV_HEADER_NICKNAME).isEmpty() &&
                csvRecord.get(AWS_BULK_IMPORT_CSV_HEADER_DESCRIPTION).isEmpty() &&
                csvRecord.get(AWS_BULK_IMPORT_CSV_HEADER_OWNERS).isEmpty() &&
                csvRecord.get(AWS_BULK_IMPORT_CSV_HEADER_TAGS).isEmpty();
        if (isMockHeaderEnabled) {
            baseRowCheck &= csvRecord.get(AWS_BULK_IMPORT_CSV_HEADER_IS_MOCK).isEmpty();
        }
        return baseRowCheck;
    }

    /**
     * Helper method to convert a {@link CloudAccountRecordState} to a {@link CloudAccountCreateRequest}
     * object.
     *
     * @param cloudAccountRecord The CSV row with cloud account information to request with.
     * @return A {@link CloudAccountCreateRequest} object.
     */
    private CloudAccountCreateRequest convertCsvRecordToCloudAccountCreateRequest(
            CloudAccountRecordState cloudAccountRecord) throws IllegalArgumentException {
        CloudAccountCreateRequest cloudAccountCreateRequest = new CloudAccountCreateRequest();
        cloudAccountCreateRequest.type = EndpointType.aws.name();

        cloudAccountCreateRequest.name = cloudAccountRecord.name;
        cloudAccountCreateRequest.description = cloudAccountRecord.description;

        // Create Credentials Object
        cloudAccountCreateRequest.credentials = getCredentialsFromIdentifierColumn(
                cloudAccountRecord.identifier);

        // Get org link from the auth context
        cloudAccountCreateRequest.orgId = getAuthContextOrgId(
                OperationContext.getAuthorizationContext());

        // Separate semicolon-delimited owners
        cloudAccountCreateRequest.owners = splitCsvRecordColumn(cloudAccountRecord.owners, SEMICOLON);

        // Separate semicolon-delimited tags
        splitCsvRecordColumn(cloudAccountRecord.tags, SEMICOLON)
                .stream()
                .map(tag -> {
                    int equalsIndex = tag.indexOf(EQUALS);

                    // If there is no "=", then treat it as a key-value where the value is empty
                    if (equalsIndex == -1) {
                        return new HashSet<>(Collections.singleton(new TagViewState(tag, "")));
                    }

                    String key = tag.substring(0, equalsIndex);
                    String[] valuesInd = tag.substring(tag.indexOf(EQUALS) + 1).split(COMMA);
                    return Arrays.stream(valuesInd)
                            .map(value -> new TagViewState(key, value))
                            .collect(Collectors.toSet());
                })
                .reduce((a, b) -> {
                    a.addAll(b);
                    return a;
                })
                .ifPresent(tagViewStates -> cloudAccountCreateRequest.tags = tagViewStates);

        // Set mock-mode if desired
        cloudAccountCreateRequest.isMock = cloudAccountRecord.isMock;

        return cloudAccountCreateRequest;
    }

    /**
     * Helper method to separate an array via a delimiter and return a Set of Strings. Filters out
     * empty rows and trims surrounding whitespace before returning each string back to the set.
     *
     * @param column    The column string to split
     * @param delimiter The delimiter to split in the column string
     * @return A set of the split individual strings.
     */
    private Set<String> splitCsvRecordColumn(String column, String delimiter) {
        return Arrays.stream(column.split(delimiter))
                .filter(s -> !s.isEmpty())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Helper method to get a Credentials object from a CSV `identifier` column.
     *
     * @param csvIdentifier The identifier column in an AWS Bulk Import CSV row.
     * @return A new {@link Credentials} object.
     */
    private Credentials getCredentialsFromIdentifierColumn(String csvIdentifier)
            throws IllegalArgumentException {
        Set<String> splitIdentifiers = splitCsvRecordColumn(csvIdentifier, SEMICOLON);
        String[] identifiers = splitIdentifiers.toArray(new String[splitIdentifiers.size()]);

        Credentials credentials;
        switch (identifiers.length) {
        case 0:
            throw new IllegalArgumentException("CSV row is missing an identifier.");
        case 1:
            Map<String, String> endpointProperties = new HashMap<>();
            endpointProperties.put(ARN_KEY, identifiers[0]);
            credentials = Credentials.createCredentials(EndpointType.aws.name(), null,
                    endpointProperties);
            break;
        case 2:
            AuthCredentialsServiceState authCredentials = new AuthCredentialsServiceState();
            authCredentials.privateKeyId = identifiers[0];
            authCredentials.privateKey = identifiers[1];
            credentials = Credentials.createCredentials(EndpointType.aws.name(),
                    authCredentials, null);
            break;
        default:
            throw new IllegalArgumentException(String.format("Cannot have more than 1 '%s' " +
                    "delimiter in 'identifier' column.", SEMICOLON));
        }

        return credentials;
    }

    /**
     * Modification of
     * {@link EndpointUtils#handleCompletion(Operation, Operation, Throwable)}
     * to be able to properly handle endpoint creation failures.
     *
     * @param op The completed {@link EndpointCreationTaskService} operation.
     * @param t  An error if one was thrown.
     * @return A new {@link FailedImportState} object with appropriate error response, or null if no
     * error is thrown.
     */
    private FailedImportState getImportFailure(Operation op, Throwable t) {
        if (t != null) {
            if (op != null && op.hasBody()) {
                ServiceErrorResponse err = op.getBody(ServiceErrorResponse.class);
                return new FailedImportState().withError(err);
            }

            return new FailedImportState().withError(recreateServiceErrorResponse(t));
        }

        TaskServiceState state = op.getBody(EndpointCreationTaskState.class);
        TaskState taskInfo = state.taskInfo;

        if (taskInfo != null && taskInfo.stage == FAILED) {
            if (taskInfo.failure != null) {
                return new FailedImportState().withError(taskInfo.failure);
            }
        }
        return null;
    }

    private static final Pattern SERVICE_ERROR_MESSAGE_PATTERN = Pattern.compile("^Service.*error\\ (\\d{3})\\ for.*message\\ (.*)$");
    private static final Pattern FIND_SERVICE_ERROR_RESPONSE_MESSAGE = Pattern.compile("^.*(\\d{5})\\:\\s(.*)$");

    private ServiceErrorResponse recreateServiceErrorResponse(Throwable t) {
        String errorMessage = t.getMessage();

        Matcher serviceErrorMatcher = SERVICE_ERROR_MESSAGE_PATTERN.matcher(errorMessage);

        // In the case of no match to the expected template, just return the error
        if (!serviceErrorMatcher.matches()) {
            return ServiceErrorResponse.create(t, STATUS_CODE_INTERNAL_ERROR);
        }

        ServiceErrorResponse errorResponse = new ServiceErrorResponse();

        errorResponse.statusCode = Integer.parseInt(serviceErrorMatcher.group(1));
        errorMessage = serviceErrorMatcher.group(2);

        // Search for error messages produced with an error code.
        Matcher errorMatcher = FIND_SERVICE_ERROR_RESPONSE_MESSAGE.matcher(errorMessage);
        if (errorMatcher.matches()) {
            errorResponse.messageId = errorMatcher.group(1);
            errorResponse.message = errorMatcher.group(2);
            return errorResponse;
        }

        // In the case where no error code was thrown, just return the message.
        errorResponse.messageId = errorMessage;
        return errorResponse;
    }

    /**
     * Data object to store successfully imported cloud account objects.
     */
    public static class SuccessfulImportState {

        /**
         * Link to the created cloud account in {@link CloudAccountApiService}.
         */
        public String cloudAccountLink;

        /**
         * The related {@link CloudAccountRecordState} that was used for the creation request in
         * {@link CloudAccountApiService}.
         */
        public CloudAccountRecordState csvRecord;

        public SuccessfulImportState withCloudAccountLink(String cloudAccountLink) {
            this.cloudAccountLink = cloudAccountLink;
            return this;
        }

        public SuccessfulImportState withCsvRecord(CloudAccountRecordState cloudAccountRecordState) {
            this.csvRecord = cloudAccountRecordState;
            return this;
        }

        @Override
        public int hashCode() {
            int result = 17;
            result = 31 * result + this.cloudAccountLink.hashCode();
            result = 31 * result + this.csvRecord.hashCode();
            return result;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof SuccessfulImportState &&
                    this.cloudAccountLink.equals(((SuccessfulImportState) other).cloudAccountLink) &&
                    this.csvRecord.equals(((SuccessfulImportState) other).csvRecord);
        }
    }

    /**
     * Data object to store failed import requests to the {@link CloudAccountApiService}.
     */
    public static class FailedImportState {

        /**
         * The related {@link ServiceErrorResponse} to the creation request in
         * {@link CloudAccountApiService}.
         */
        public ServiceErrorResponse error;

        /**
         * The related {@link CloudAccountRecordState} that was used for the creation request in
         * {@link CloudAccountApiService}.
         */
        public CloudAccountRecordState csvRecord;

        public FailedImportState withError(ServiceErrorResponse error) {
            this.error = error;
            return this;
        }

        public FailedImportState withCsvRecord(CloudAccountRecordState csvRecord) {
            this.csvRecord = csvRecord;
            return this;
        }

        @Override
        public int hashCode() {
            int result = 17;
            result = 31 * result + this.error.message.hashCode();
            result = 31 * result + this.error.statusCode;
            result = 31 * result + this.csvRecord.hashCode();
            return result;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof FailedImportState &&
                    this.error.message.equals(((FailedImportState) other).error.message) &&
                    this.error.statusCode == ((FailedImportState) other).error.statusCode &&
                    this.csvRecord.equals(((FailedImportState) other).csvRecord);
        }
    }

    /**
     * Class to help represent the {@link org.apache.commons.csv.CSVRecord} objects with their
     * expected properties, and easily mappable into a {@link CloudAccountCreateRequest} object.
     */
    public static class CloudAccountRecordState {

        /**
         * The Cloud Account identifier. See {@link Credentials.AwsCredential#arn}.
         */
        public String identifier;

        /**
         * The Cloud Account name. See {@link CloudAccountCreateRequest#name}.
         */
        public String name;

        /**
         * The Cloud Account description. See {@link CloudAccountCreateRequest#description}.
         */
        public String description;

        /**
         * The Cloud Account owners. See {@link CloudAccountCreateRequest#owners}.
         *
         * Should be in a semicolon-delimited form, such as "user@company.com" or "a@bc.com;x@yz.com"
         */
        public String owners;

        /*
         * The Cloud Account tags. See {@link CloudAccountCreateRequest#tags}.
         *
         * Should be in a semicolon-delimited form, such as "key=value", or "key=value;key2=value2".
         * Additionally, to remain consistent with the UI, a comma-delimiter will be used to set
         * two tags to be under the same key - i.e. "key=value1,value2" will be split to
         * "key=value1;key=value2".
         */
        public String tags;

        /**
         * The number of attempts this specific record state has attempted to have been retried in
         * being added to the system.
         */
        public int endpointCreationAttempts;

        /**
         * isMock for local testing
         */
        public boolean isMock = false;

        public CloudAccountRecordState() {
            this.endpointCreationAttempts = 0;
        }

        /**
         * Sets the name of the current {@link CloudAccountRecordState} object.
         *
         * @param name See {@link CloudAccountRecordState#name}.
         * @return The {@link CloudAccountRecordState} object.
         */
        public CloudAccountRecordState withName(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the description of the current {@link CloudAccountRecordState} object.
         *
         * @param description See {@link CloudAccountRecordState#description}.
         * @return The {@link CloudAccountRecordState} object.
         */
        public CloudAccountRecordState withDescription(String description) {
            this.description = description;
            return this;
        }

        /**
         * Sets the identifier of the current {@link CloudAccountRecordState} object.
         *
         * @param identifer The identifier of this cloud account.
         *                  See {@link Credentials.AwsCredential#arn}.
         * @return The {@link CloudAccountRecordState} object.
         */
        public CloudAccountRecordState withIdentifier(String identifer) {
            this.identifier = identifer;
            return this;
        }

        /**
         * Sets the set of owners of the current {@link CloudAccountRecordState} object.
         *
         * @param owners See {@link CloudAccountRecordState#owners}.
         * @return The {@link CloudAccountRecordState} object.
         */
        public CloudAccountRecordState withOwners(String owners) {
            this.owners = owners;
            return this;
        }

        /**
         * Sets the set of tags of the current {@link CloudAccountRecordState} object.
         *
         * @param tags See {@link CloudAccountRecordState#tags}.
         * @return The {@link CloudAccountRecordState} object.
         */
        public CloudAccountRecordState withTags(String tags) {
            this.tags = tags;
            return this;
        }

        public CloudAccountRecordState withMock(String isMock) {
            this.isMock = Boolean.valueOf(isMock);
            return this;
        }

        @Override
        public int hashCode() {
            int result = 17;
            result = 31 * result + this.identifier.hashCode();
            result = 31 * result + this.name.hashCode();
            result = 31 * result + this.description.hashCode();
            result = 31 * result + this.owners.hashCode();
            result = 31 * result + this.tags.hashCode();
            result += this.endpointCreationAttempts;
            result += this.isMock ? 1 : 0;
            return result;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof CloudAccountRecordState)) {
                return false;
            }

            CloudAccountRecordState otherCloudAccountRecordState = ((CloudAccountRecordState) other);
            return this.identifier.equals(otherCloudAccountRecordState.identifier) &&
                    this.name.equals(otherCloudAccountRecordState.name) &&
                    this.description.equals(otherCloudAccountRecordState.description) &&
                    this.owners.equalsIgnoreCase(otherCloudAccountRecordState.owners) &&
                    this.tags.equalsIgnoreCase(otherCloudAccountRecordState.tags) &&
                    this.endpointCreationAttempts == otherCloudAccountRecordState.endpointCreationAttempts &&
                    this.isMock == otherCloudAccountRecordState.isMock;

        }
    }

    /**
     * Helper method to extend an array list with extra parameters.
     *
     * @param originalList The original list
     * @param extra        The extra arguments (of the same type) to extend the {@link List} with.
     * @return The concatenated list.
     */
    @SafeVarargs
    private static <T> List<T> concatenatedArrayList(List<T> originalList, T... extra) {
        originalList.addAll(Arrays.asList(extra));
        return originalList;
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument d = super.getDocumentTemplate();
        d.documentDescription.serializedStateSizeLimit = MAX_STATE_SIZE;
        return d;
    }

}
