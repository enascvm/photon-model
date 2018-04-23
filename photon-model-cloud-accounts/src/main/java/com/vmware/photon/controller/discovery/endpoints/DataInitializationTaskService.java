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

import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.DISABLE_STATS_COLLECTION;
import static com.vmware.photon.controller.discovery.endpoints.DataInitializationTaskService.SubStage.CHECK_CONCURRENT_ENUMERATION;
import static com.vmware.photon.controller.discovery.endpoints.DataInitializationTaskService.SubStage.ENUMERATION;
import static com.vmware.photon.controller.discovery.endpoints.DataInitializationTaskService.SubStage.GET_COMPUTE_HOST;
import static com.vmware.photon.controller.discovery.endpoints.DataInitializationTaskService.SubStage.OPTIONAL_STATS_COLLECTION;
import static com.vmware.photon.controller.discovery.endpoints.DataInitializationTaskService.SubStage.STATS_COLLECTION;
import static com.vmware.photon.controller.discovery.endpoints.DataInitializationTaskService.SubStage.SUCCESS;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.VSPHERE_ON_PREM_ADAPTER;
import static com.vmware.photon.controller.discovery.onboarding.OnboardingUtils.getAutomationUserLink;
import static com.vmware.photon.controller.discovery.onboarding.OnboardingUtils.getOrgId;
import static com.vmware.photon.controller.discovery.onboarding.OnboardingUtils.subscribeToNotifications;
import static com.vmware.photon.controller.discovery.onboarding.OnboardingUtils.unsubscribeNotifications;
import static com.vmware.photon.controller.model.UriPaths.DATA_INIT_TASK_SERVICE;
import static com.vmware.xenon.common.Service.Action.PATCH;
import static com.vmware.xenon.common.TaskState.TaskStage.FAILED;
import static com.vmware.xenon.common.TaskState.TaskStage.FINISHED;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;

import com.vmware.photon.controller.discovery.common.utils.DataCollectionTaskUtil;
import com.vmware.photon.controller.discovery.endpoints.DataInitializationTaskService.DataInitializationState;
import com.vmware.photon.controller.discovery.endpoints.OptionalAdapterSchedulingService.OptionalAdapterSchedulingRequest;
import com.vmware.photon.controller.discovery.endpoints.OptionalAdapterSchedulingService.RequestType;
import com.vmware.photon.controller.discovery.notification.NotificationUtils;
import com.vmware.photon.controller.discovery.notification.event.EnumerationCompleteEvent;
import com.vmware.photon.controller.discovery.onboarding.OnboardingUtils;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService.ResourceEnumerationTaskState;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.photon.controller.model.tasks.TaskUtils;
import com.vmware.photon.controller.model.tasks.monitoring.StatsCollectionTaskService;
import com.vmware.photon.controller.model.tasks.monitoring.StatsCollectionTaskService.StatsCollectionTaskState;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskFactoryService;
import com.vmware.xenon.services.common.TaskService;

/**
 * Task to do initial data collection when a endpoint is added or update data when an enpdoint is
 * updated.
 */
public class DataInitializationTaskService extends TaskService<DataInitializationState> {

    // A stat key to track the amount of POSTs have occurred for individual documents.
    public static final String INVOCATION_COUNT = "invocationCount";

    public static final String FACTORY_LINK = DATA_INIT_TASK_SERVICE;

    public static final Set<String> IGNORED_ENDPOINT_ADAPTER_TYPES = new HashSet<>(
            Arrays.asList(PhotonModelConstants.EndpointType.vsphere.name(),
                    VSPHERE_ON_PREM_ADAPTER));

    protected static final String PROPERTY_WAIT_COUNTS = "DataInitializationTaskService.WAIT_COUNTS";
    protected static final String PROPERTY_INTERVAL_SECS = "DataInitializationTaskService.INTERVAL_SECS";

    public static final Integer DEFAULT_WAIT_COUNTS = Integer.getInteger(PROPERTY_WAIT_COUNTS, 6);
    public static final int DEFAULT_WAIT_INTERVAL_SECS = Integer.getInteger(PROPERTY_INTERVAL_SECS, 30);

    public static class DataInitializationState extends TaskService.TaskServiceState {
        @Documentation(description = "Endpoint for which the data needs to be collected")
        public EndpointState endpoint;

        @Documentation(description = "The tenant links")
        public Set<String> tenantLinks;

        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public SubStage subStage;

        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public ComputeStateWithDescription computeHostWithDesc;

        /** The number of retries remaining. If this reaches zero, the enumeration task will be skipped. */
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public Integer retriesRemaining;

        public String toString() {
            return "DataInitializationState{" +
                    "endpoint=" + this.endpoint.documentSelfLink +
                    ", tenantLinks=" + this.tenantLinks +
                    ", subStage=" + this.subStage +
                    ", computeHostWithDesc=" + this.computeHostWithDesc.documentSelfLink +
                    ", retriesRemaining=" + this.retriesRemaining +
                    '}';
        }
    }

    public static FactoryService createFactory() {
        TaskFactoryService fs = new TaskFactoryService(DataInitializationState.class) {
            @Override
            public Service createServiceInstance() throws Throwable {
                return new DataInitializationTaskService();
            }
        };
        fs.setPeerNodeSelectorPath(ServiceUriPaths.DEFAULT_1X_NODE_SELECTOR);
        return fs;
    }

    public DataInitializationTaskService() {
        super(DataInitializationState.class);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    /**
     * Substages for task processing.
     */
    enum SubStage {
        /**
         * Stage to get compute host,
         */
        GET_COMPUTE_HOST,

        /**
         * Stage to check concurrent enumerations.
         */
        CHECK_CONCURRENT_ENUMERATION,

        /**
         * Stage to trigger enumeration.
         */
        ENUMERATION,

        /**
         * Stage to do stats collection.
         */
        STATS_COLLECTION,

        /**
         * Stage to do stats aggregation.
         */
        STATS_AGGREGATION,

        /**
         * Stage to do cost aggregation.
         */
        COST_AGGREGATION,

        /**
         * Stage to trigger optional stats collection.
         */
        OPTIONAL_STATS_COLLECTION,

        /**
         * Stage to trigger optional stats aggregation.
         */
        OPTIONAL_STATS_AGGREGATION,

        /**
         * Stage to indicate success.
         */
        SUCCESS
    }

    private static final Collection<TaskStage> ERROR_STAGES = Collections
            .unmodifiableList(Arrays.asList(
                    TaskStage.CANCELLED, FAILED));

    @Override
    public void handlePut(Operation put) {
        if (put.hasPragmaDirective(Operation.PRAGMA_DIRECTIVE_POST_TO_PUT)) {
            // converted PUT due to IDEMPOTENT_POST option
            logInfo("Service %s has already started. Ignoring converted PUT.", put.getUri());
            put.complete();
            return;
        }

        // normal PUT is not supported
        Operation.failActionNotSupported(put);
    }

    @Override
    public void handlePatch(Operation patch) {
        DataInitializationState currentTask = getState(patch);
        DataInitializationState patchBody = getBody(patch);

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
            logWarning("Task failed: %s", (patchBody.failureMessage == null ? "No reason given"
                    : patchBody.failureMessage));
            break;
        default:
            logWarning("Unexpected stage: %s", patchBody.taskInfo.stage);
            break;
        }
    }

    @Override
    protected void initializeState(DataInitializationState task, Operation taskOperation) {

        task.subStage = GET_COMPUTE_HOST;
        if (task.retriesRemaining == null) {
            task.retriesRemaining = DEFAULT_WAIT_COUNTS;
        }

        super.initializeState(task, taskOperation);
    }

    @Override
    protected DataInitializationState validateStartPost(Operation taskOperation) {
        OnboardingUtils.adjustStat(this, INVOCATION_COUNT, 1.0);
        DataInitializationState task = super.validateStartPost(taskOperation);

        if (task != null) {
            if (task.subStage != null) {
                taskOperation.setStatusCode(Operation.STATUS_CODE_BAD_REQUEST);
                taskOperation.fail(
                        new IllegalArgumentException("Do not specify subStage: internal use only"));
                return null;
            }

            if (task.endpoint == null) {
                taskOperation.setStatusCode(Operation.STATUS_CODE_BAD_REQUEST);
                taskOperation.fail(
                        new IllegalArgumentException("'endpoint' needs to be specified"));
                return null;
            }
        }

        return task;
    }

    /**
     * State machine for the task service.
     */
    private void handleSubStage(DataInitializationState state) {
        switch (state.subStage) {
        case GET_COMPUTE_HOST:
            getComputeHost(state, CHECK_CONCURRENT_ENUMERATION);
            break;
        case CHECK_CONCURRENT_ENUMERATION:
            if (state.retriesRemaining > 0) {
                checkConcurrentEnumeration(state, ENUMERATION);
            } else {
                log(Level.WARNING, "Exhausted while waiting for concurrent enumeration to finish. Skipping" +
                        " the current enumeration and moving to next stage for: %s ", state);
                if (DISABLE_STATS_COLLECTION) {
                    state.subStage = OPTIONAL_STATS_COLLECTION; // next stage
                } else {
                    state.subStage = STATS_COLLECTION; // next stage
                }
                sendSelfPatch(state);
            }
            break;
        case ENUMERATION:
            if (DISABLE_STATS_COLLECTION) {
                enumerate(state, OPTIONAL_STATS_COLLECTION);
            } else {
                enumerate(state, STATS_COLLECTION);
            }
            break;
        case STATS_COLLECTION:
            collectStats(state, OPTIONAL_STATS_COLLECTION);
            break;
        case OPTIONAL_STATS_COLLECTION:
            triggerOptionalStatsAdapters(state, SUCCESS);
            break;
        case SUCCESS:
            sendSelfPatch(state, FINISHED, null);
            break;
        default:
            throw new IllegalStateException("Unknown stage encountered: " + state.subStage);
        }
    }

    /**
     * Retrieves the compute host.
     */
    private void getComputeHost(DataInitializationState state, SubStage nextStage) {
        URI uri = ComputeStateWithDescription
                .buildUri(UriUtils.buildUri(getHost(), state.endpoint.computeLink));
        Operation get = Operation.createGet(uri);
        sendWithDeferredResult(get, ComputeStateWithDescription.class)
                .whenComplete((computeHostWithDesc, e) -> {
                    if (e != null) {
                        log(Level.WARNING, "Error while retrieving compute host:",
                                e.getMessage());
                        sendSelfFailurePatch(state, Utils.toString(e));
                        return;
                    }
                    state.computeHostWithDesc = computeHostWithDesc;
                    state.subStage = nextStage;
                    sendSelfPatch(state);
                });
    }

    /**
     * Check the concurrent enumerations running any for the same compute host and delay the request, if required.z
     */
    private void checkConcurrentEnumeration(DataInitializationState state, SubStage nextStage) {
        // Enumerations run based on computeHost name.
        String id = UriUtils.getLastPathSegment(state.computeHostWithDesc.documentSelfLink);

        // Try to get running enumeration tasks (based on computeHost name)
        Operation operation =
                Operation.createGet(getHost(), UriUtils.buildUriPath(ResourceEnumerationTaskService.FACTORY_LINK, id));

        sendWithDeferredResult(operation)
                .whenComplete((op, ex) -> {
                    if (ex != null) {
                        // It means, no concurrent enumeration is running. Trigger the current one
                        state.subStage = nextStage;
                        sendSelfPatch(state);
                        return;
                    }

                    // Otherwise, delay the current enumeration and recheck
                    state.retriesRemaining--; // decrement the retries count
                    log(Level.INFO, "Concurrent enumeration running for common parent host. Waiting for few secs" +
                            " before triggering it again for: %s ", state);
                    getHost().schedule(() -> sendSelfPatch(state), DEFAULT_WAIT_INTERVAL_SECS, TimeUnit.SECONDS);
                    return;
                });
    }

    /**
     * Kick off the enumeration for given endpoint.
     */
    private void enumerate(DataInitializationState state, SubStage nextStage) {
        // use the id based on computeLink to catch concurrent enumerations.
        String id = UriUtils.getLastPathSegment(state.computeHostWithDesc.documentSelfLink);
        ResourceEnumerationTaskState enumTaskState = new ResourceEnumerationTaskState();
        enumTaskState.parentComputeLink = state.endpoint.computeLink;
        enumTaskState.resourcePoolLink = state.endpoint.resourcePoolLink;
        enumTaskState.adapterManagementReference = state.computeHostWithDesc.adapterManagementReference;
        enumTaskState.endpointLink = state.endpoint.documentSelfLink;
        enumTaskState.tenantLinks = state.endpoint.tenantLinks;
        enumTaskState.documentSelfLink = id;
        enumTaskState.options = EnumSet.of(TaskOption.SELF_DELETE_ON_COMPLETION);

        createTask(state, enumTaskState, ResourceEnumerationTaskService.FACTORY_LINK, nextStage, true);
    }

    /**
     * Kick off stats collection.
     */
    private void collectStats(DataInitializationState state, SubStage nextStage) {
        StatsCollectionTaskState taskState = DataCollectionTaskUtil
                .createStatsCollectionTask(state.endpoint.resourcePoolLink);

        createTask(state, taskState, StatsCollectionTaskService.FACTORY_LINK, nextStage, false);
    }

    /**
     * Creates a task.
     */
    private void createTask(DataInitializationState state, TaskServiceState taskState,
            String factoryLink, SubStage nextStage, boolean notify) {

        // Prevent starting a data initialization task on ignored endpoint types.
        if (state != null && state.endpoint != null && state.endpoint.endpointType != null &&
                IGNORED_ENDPOINT_ADAPTER_TYPES.contains(state.endpoint.endpointType)) {
            logInfo("Skipping DataInitializationTaskService for endpoint %s of type: %s.",
                    state.endpoint.documentSelfLink, state.endpoint.endpointType);
            state.subStage = SUCCESS;
            sendSelfPatch(state);
            return;
        }

        Operation post = Operation.createPost(this, factoryLink).setBody(taskState);
        // TODO VSYM-3390: Remove this once stats collection and aggregation support tenant links.
        OperationContext opCtx = OperationContext.getOperationContext();

        try {
            TaskUtils.assumeIdentity(this, post, getAutomationUserLink());
        } catch (Exception e) {
            logWarning("Failed to assume identity: %s", e.getMessage());
            sendSelfFailurePatch(state, Utils.toString(e));
            return;
        }

        sendWithDeferredResult(post, ServiceDocument.class)
                .whenComplete((sd, e) -> {
                    OperationContext.restoreOperationContext(opCtx);

                    if (e != null) {
                        logWarning("Error triggering task, reason: %s", e.getMessage());
                        sendSelfFailurePatch(state, Utils.toString(e));
                        return;
                    }

                    // Enumeration taks is a sync op, no need of subscription
                    if (notify) {
                        EnumerationCompleteEvent changeEvent =
                                new EnumerationCompleteEvent(
                                        NotificationUtils.getCloudAccountLink(state.endpoint.documentSelfLink),
                                        EnumerationCompleteEvent.EnumerationStatus.FINISHED);
                        NotificationUtils.sendNotification(this, getUri(), changeEvent,
                                getOrgId(state.tenantLinks));
                    }
                    subscribeForFinishedTask(state, sd.documentSelfLink, nextStage, new AtomicInteger(1));
                });
    }

    /**
     * Subscribe for task completion notification.
     */
    private void subscribeForFinishedTask(DataInitializationState state, String taskLink,
                                          SubStage nextStage, AtomicInteger currentStageTaskCount) {
        AtomicBoolean patchedToNextStage = new AtomicBoolean(false);

        Consumer<Operation> notificationTarget = operation -> {
            operation.complete();

            // We only care about listening to PATCH updates...
            if (!PATCH.equals(operation.getAction())) {
                return;
            }

            TaskServiceState taskState = operation.getBody(ConcreteTaskServiceState.class);
            if (taskState.taskInfo == null) {
                logWarning("Invalid task info for: %s", taskState.documentSelfLink);
                return;
            }

            if (taskState.taskInfo.stage == FINISHED
                    && patchedToNextStage.compareAndSet(false, true)) {
                logInfo("Task completed successfully: %s", taskState.documentSelfLink);
                if (currentStageTaskCount.decrementAndGet() == 0) {
                    // Proceed to next stage only if all the tasks in current stage have completed.
                    state.subStage = nextStage;
                    sendSelfPatch(state);
                }
                unsubscribeNotifications(getHost(), taskLink, operation.getUri());
            } else if (ERROR_STAGES.contains(taskState.taskInfo.stage)) {
                String msg = String.format("Task unsuccessful: %s", taskState.documentSelfLink);
                logWarning(msg);
                sendSelfFailurePatch(state, msg);
                unsubscribeNotifications(getHost(), taskLink, operation.getUri());
            }
        };

        subscribeToNotifications(getHost(), notificationTarget,
                e -> {
                    log(Level.WARNING, Utils.toString(e));
                    state.subStage = nextStage;
                    sendSelfPatch(state);
                }, taskLink);
    }

    /**
     * Dummy task state to deal with generic response.
     */
    private static class ConcreteTaskServiceState extends TaskServiceState {
    }

    private void triggerOptionalStatsAdapters(DataInitializationState state, SubStage nextStage) {
        OptionalAdapterSchedulingRequest request = new OptionalAdapterSchedulingRequest();
        request.endpoint = state.endpoint;
        request.requestType = RequestType.TRIGGER_IMMEDIATE;

        Operation patch = Operation.createPatch(getHost(), OptionalAdapterSchedulingService.SELF_LINK)
                .setBody(request);

        OperationContext opCtx = OperationContext.getOperationContext();

        try {
            TaskUtils.assumeIdentity(this, patch, getAutomationUserLink());
        } catch (Exception e) {
            logWarning("Failed to assume identity: %s", e.getMessage());
            sendSelfFailurePatch(state, Utils.toString(e));
            return;
        }

        sendWithDeferredResult(patch, ServiceDocumentQueryResult.class)
                .whenComplete((result, e) -> {
                    OperationContext.restoreOperationContext(opCtx);

                    if (e != null) {
                        logWarning("Could not trigger optional stats adapters for endpoint: %s due to %s.",
                                state.endpoint.computeLink, e.getMessage());
                        state.subStage = nextStage;
                        sendSelfPatch(state);
                        return;
                    }

                    if (result.documentLinks == null || result.documentLinks.isEmpty()) {
                        logInfo("No optional stats adapters triggered. Skipping to next stage.");
                        state.subStage = nextStage;
                        sendSelfPatch(state);
                        return;
                    }

                    AtomicInteger currentStageTaskCount = new AtomicInteger(result.documentLinks.size());
                    result.documentLinks.forEach(taskLink -> subscribeForFinishedTask(state, taskLink, nextStage,
                            currentStageTaskCount));
                });
    }
}
