/*
 * Copyright (c) 2015-2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.model.tasks;

import static com.vmware.photon.controller.model.UriPaths.RESOURCE_GROOMER_SCHEDULE_DELAY_SECONDS_PROPERTY_NAME;
import static com.vmware.photon.controller.model.resources.EndpointService.ENDPOINT_REMOVAL_REQUEST_REFERRER_NAME;
import static com.vmware.photon.controller.model.resources.EndpointService.ENDPOINT_REMOVAL_REQUEST_REFERRER_VALUE;
import static com.vmware.photon.controller.model.tasks.monitoring.StatsUtil.SEPARATOR;
import static com.vmware.photon.controller.model.util.PhotonModelUriUtils.createInventoryUri;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.LINK;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.OPTIONAL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SERVICE_USE;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ImageService.ImageState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.RouterService.RouterState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.SnapshotService.SnapshotState;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.TaskService;

/**
 * Task removes the endpoint service instances.
 */
public class EndpointRemovalTaskService
        extends TaskService<EndpointRemovalTaskService.EndpointRemovalTaskState> {
    public static final String FACTORY_LINK = UriPaths.PROVISIONING + "/endpoint-removal-tasks";

    public static final long DEFAULT_TIMEOUT_MICROS = TimeUnit.MINUTES
            .toMicros(10);

    private static final long DEFAULT_RESOURCE_GROOMER_DELAY_SECONDS = TimeUnit.MINUTES.toSeconds(2);
    private static final long RESOURCE_GROOMER_DELAY_SECONDS = Long.getLong(RESOURCE_GROOMER_SCHEDULE_DELAY_SECONDS_PROPERTY_NAME,
            DEFAULT_RESOURCE_GROOMER_DELAY_SECONDS);

    public static final String FIELD_NAME_ENDPOINT_LINKS = "endpointLinks";
    public static final String FIELD_NAME_CUSTOM_PROPERTIES = "customProperties";
    public static final String RESOURCE_GROOMER_TASK_POSTFIX = "document-deletion";
    public static final String DEFERRED = "deferred";

    public static final Collection<String> RESOURCE_TYPES_TO_DELETE = Arrays.asList(
            Utils.buildKind(AuthCredentialsServiceState.class),
            Utils.buildKind(DiskState.class),
            Utils.buildKind(ComputeState.class),
            Utils.buildKind(ComputeDescription.class),
            Utils.buildKind(NetworkState.class),
            Utils.buildKind(NetworkInterfaceState.class),
            Utils.buildKind(NetworkInterfaceDescription.class),
            Utils.buildKind(SecurityGroupState.class),
            Utils.buildKind(SubnetState.class),
            Utils.buildKind(StorageDescription.class),
            Utils.buildKind(ImageState.class),
            Utils.buildKind(RouterState.class),
            Utils.buildKind(SnapshotState.class)
    );

    /**
     * SubStage.
     */
    public static enum SubStage {


        /**
         * Load endpoint data.
         */
        LOAD_ENDPOINT,

        /**
         * Stop scheduled enumeration task
         */
        STOP_ENUMERATION,

        /**
         * Delete the endpoint documents
         */
        ISSUE_ENDPOINT_DELETE,

        /**
         * Remove resources that are not associated with any endpoints
         */
        REMOVE_ASSOCIATED_STALE_DOCUMENTS,

        FINISHED,
        FAILED
    }

    /**
     * Represents the state of the removal task.
     */
    public static class EndpointRemovalTaskState extends TaskService.TaskServiceState {

        @Documentation(description = "A link to endpoint to be deleted.")
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, LINK }, indexing = STORE_ONLY)
        public String endpointLink;

        @Documentation(description = "Describes a service task sub stage.")
        @PropertyOptions(usage = { SERVICE_USE }, indexing = STORE_ONLY)
        public SubStage taskSubStage;

        @Documentation(description = "A list of tenant links which can access this task.")
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, OPTIONAL }, indexing = {
                PropertyIndexingOption.EXPAND })
        public List<String> tenantLinks;

        /**
         * Task options
         */
        public EnumSet<TaskOption> options = EnumSet.noneOf(TaskOption.class);

        /**
         * The error threshold.
         */
        public double errorThreshold;

        @Documentation(description = "EndpointState to delete. Set by the run-time.")
        @PropertyOptions(usage = { SERVICE_USE }, indexing = STORE_ONLY)
        public EndpointState endpoint;

        @Documentation(description = "Indicates a mock request")
        @UsageOption(option = SERVICE_USE)
        public boolean isMock = false;

        @Documentation(description = "Enable/Disable groomer for tests.")
        @UsageOption(option = SERVICE_USE)
        public boolean disableGroomer = false;
    }

    public EndpointRemovalTaskService() {
        super(EndpointRemovalTaskState.class);
        super.toggleOption(Service.ServiceOption.REPLICATION, true);
        super.toggleOption(Service.ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(Service.ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handleStart(Operation post) {
        EndpointRemovalTaskState initialState = validateStartPost(post);
        if (initialState == null) {
            return;
        }

        if (!ServiceHost.isServiceCreate(post)) {
            return;
        }

        initializeState(initialState, post);
        initialState.taskInfo.stage = TaskStage.CREATED;
        post.setBody(initialState)
                .setStatusCode(Operation.STATUS_CODE_ACCEPTED)
                .complete();

        // self patch to start state machine
        sendSelfPatch(initialState, TaskStage.STARTED, null);
    }

    @Override
    public void handlePatch(Operation patch) {
        EndpointRemovalTaskState body = patch
                .getBody(EndpointRemovalTaskState.class);
        EndpointRemovalTaskState currentState = getState(patch);

        if (validateTransitionAndUpdateState(patch, body, currentState)) {
            return;
        }

        patch.complete();

        switch (currentState.taskInfo.stage) {
        case CREATED:
            break;
        case STARTED:
            handleStagePatch(currentState);
            break;
        case FINISHED:
            logInfo(() -> "Task was completed");
            break;
        case FAILED:
        case CANCELLED:
            break;
        default:
            break;
        }
    }

    private void handleStagePatch(EndpointRemovalTaskState currentState) {

        adjustStat(currentState.taskSubStage.toString(), 1);

        switch (currentState.taskSubStage) {
        case LOAD_ENDPOINT:
            getEndpoint(currentState, SubStage.STOP_ENUMERATION);
            break;
        case STOP_ENUMERATION:
            stopEnumeration(currentState, SubStage.ISSUE_ENDPOINT_DELETE);
            break;
        case ISSUE_ENDPOINT_DELETE:
            doInstanceDelete(currentState, SubStage.REMOVE_ASSOCIATED_STALE_DOCUMENTS);
            break;
        case REMOVE_ASSOCIATED_STALE_DOCUMENTS:
            removeAssociatedStaleDocuments(currentState, SubStage.FINISHED);
            break;
        case FAILED:
            break;
        case FINISHED:
            complete(currentState, SubStage.FINISHED);
            break;
        default:
            break;
        }
    }

    private void stopEnumeration(EndpointRemovalTaskState currentState, SubStage next) {
        String id = UriUtils.getLastPathSegment(currentState.endpointLink);
        logFine(() -> String.format("Stopping any scheduled task for endpoint %s",
                currentState.endpointLink));
        Operation.createDelete(this, UriUtils.buildUriPath(ScheduledTaskService.FACTORY_LINK, id))
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning(() -> String.format("Unable to delete ScheduleTaskState for"
                                        + " endpoint %s : %s", currentState.endpointLink,
                                e.getMessage()));
                    }

                    sendSelfPatch(TaskStage.STARTED, next);
                }).sendWith(this);
    }

    /**
     * Delete endpoint
     *
     */
    private void doInstanceDelete(EndpointRemovalTaskState currentState, SubStage next) {
        EndpointState endpoint = currentState.endpoint;

        Operation crdOp = Operation.createDelete(createInventoryUri(this.getHost(),
                endpoint.authCredentialsLink));
        Operation epOp = Operation.createDelete(createInventoryUri(this.getHost(),
                endpoint.documentSelfLink));
        // custom header identifier for endpoint service to validate before deleting endpoint
        epOp.addRequestHeader(ENDPOINT_REMOVAL_REQUEST_REFERRER_NAME,
                ENDPOINT_REMOVAL_REQUEST_REFERRER_VALUE);

        OperationJoin.create(crdOp, epOp).setCompletion((ops, exc) -> {
            if (exc != null) {
                // failing to delete the endpoint itself is considered a critical error
                Throwable endpointRemovalException = exc.get(epOp.getId());
                if (endpointRemovalException != null) {
                    sendFailureSelfPatch(endpointRemovalException);
                    return;
                }

                // other removal exceptions are just warnings
                logFine(() -> String.format("Failed delete some of the associated resources,"
                        + " reason %s", Utils.toString(exc)));
            }
            // Endpoint and AuthCredentials are deleted; mark the operation as complete
            sendSelfPatch(TaskStage.STARTED, next);
        }).sendWith(this);
    }

    /**
     * Start stale endpoint document deletion task for the deleted endpoint to ensure all documents
     * associated with the deleted endpoint are deleted.
     */
    private void removeAssociatedStaleDocuments(EndpointRemovalTaskState task, SubStage nextStage) {
        ResourceGroomerTaskService.EndpointResourceDeletionRequest state = new
                ResourceGroomerTaskService.EndpointResourceDeletionRequest();
        state.tenantLinks = task.tenantLinks != null ? new HashSet<>(task.tenantLinks) : null;
        state.documentSelfLink = getResourceGroomerTaskUri(task.endpointLink, false);
        state.endpointLink = task.endpointLink;

        if (task.isMock || task.disableGroomer) {
            task.taskSubStage = nextStage;
            sendSelfPatch(task);
            return;
        }

        sendWithDeferredResult(Operation.createPost(this, ResourceGroomerTaskService.FACTORY_LINK)
                .setBody(state))
                .whenComplete((o, e) -> {
                    if (e != null) {
                        logWarning(() -> String.format("Failure removing stale documents :%s", e
                                .toString()));
                        sendFailureSelfPatch(e);
                        return;
                    }

                    scheduleDeferredGroomer(task);
                    task.taskSubStage = nextStage;
                    sendSelfPatch(task);
                });
    }

    public void getEndpoint(EndpointRemovalTaskState currentState, SubStage next) {
        sendRequest(Operation.createGet(this, currentState.endpointLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        // the task might have expired, with no results every
                        // becoming available
                        logWarning(() -> String.format("Failure retrieving endpoint: %s, reason:"
                                + " %s", currentState.endpointLink, e.toString()));
                        sendFailureSelfPatch(e);
                        return;
                    }

                    EndpointState rsp = o.getBody(EndpointState.class);
                    EndpointRemovalTaskState state = createPatchSubStageTask(TaskStage.STARTED,
                            next, null);
                    state.endpoint = rsp;
                    sendSelfPatch(state);
                }));
    }

    private boolean validateTransitionAndUpdateState(Operation patch, EndpointRemovalTaskState
            body, EndpointRemovalTaskState currentState) {

        TaskState.TaskStage currentStage = currentState.taskInfo.stage;
        SubStage currentSubStage = currentState.taskSubStage;

        boolean isUpdate = false;

        if (body.endpoint != null) {
            currentState.endpoint = body.endpoint;
            isUpdate = true;
        }

        if (body.taskInfo == null || body.taskInfo.stage == null) {
            if (isUpdate) {
                patch.complete();
                return true;
            }
            patch.fail(new IllegalArgumentException("taskInfo and stage are required"));
            return true;
        }

        if (currentStage.ordinal() > body.taskInfo.stage.ordinal()) {
            patch.fail(new IllegalArgumentException("stage can not move backwards:"
                    + body.taskInfo.stage));
            return true;
        }

        if (currentStage.ordinal() == body.taskInfo.stage.ordinal()
                && (body.taskSubStage == null || currentSubStage.ordinal() > body.taskSubStage
                .ordinal())) {
            patch.fail(new IllegalArgumentException("subStage can not move backwards:"
                    + body.taskSubStage));
            return true;
        }

        currentState.taskInfo.stage = body.taskInfo.stage;
        currentState.taskSubStage = body.taskSubStage;

        logFine(() -> String.format("Moving from %s(%s) to %s(%s)", currentSubStage, currentStage,
                body.taskSubStage, currentState.taskInfo.stage));

        return false;
    }

    private void sendFailureSelfPatch(Throwable e) {
        sendSelfPatch(createPatchSubStageTask(TaskState.TaskStage.FAILED, SubStage.FAILED, e));
    }

    private void sendSelfPatch(TaskState.TaskStage stage, SubStage subStage) {
        sendSelfPatch(createPatchSubStageTask(stage, subStage, null));
    }

    private EndpointRemovalTaskState createPatchSubStageTask(TaskState.TaskStage stage,
                                                             SubStage subStage,
                                                             Throwable e) {
        EndpointRemovalTaskState body = new EndpointRemovalTaskState();
        body.taskInfo = new TaskState();
        body.taskInfo.stage = stage;
        body.taskSubStage = subStage;
        if (e != null) {
            body.taskInfo.failure = Utils.toServiceErrorResponse(e);
            logWarning(() -> String.format("Patching to failed: %s", Utils.toString(e)));
        }
        return body;
    }

    @Override
    protected EndpointRemovalTaskState validateStartPost(Operation taskOperation) {
        EndpointRemovalTaskState task = super.validateStartPost(taskOperation);
        if (task == null) {
            return null;
        }

        if (TaskState.isCancelled(task.taskInfo)
                || TaskState.isFailed(task.taskInfo)
                || TaskState.isFinished(task.taskInfo)) {
            return null;
        }

        if (!ServiceHost.isServiceCreate(taskOperation)) {
            return task;
        }

        if (task.endpointLink == null) {
            taskOperation.fail(new IllegalArgumentException("endpointLink is required"));
            return null;
        }

        return task;
    }

    @Override
    protected void initializeState(EndpointRemovalTaskState state, Operation taskOperation) {
        if (state.taskSubStage == null) {
            state.taskSubStage = SubStage.LOAD_ENDPOINT;
        }

        if (state.options == null) {
            state.options = EnumSet.noneOf(TaskOption.class);
        }

        if (state.documentExpirationTimeMicros == 0) {
            state.documentExpirationTimeMicros = Utils.getNowMicrosUtc()
                    + DEFAULT_TIMEOUT_MICROS;
        }
        super.initializeState(state, taskOperation);
    }

    private void complete(EndpointRemovalTaskState state, SubStage completeSubStage) {
        if (!TaskUtils.isFailedOrCancelledTask(state)) {
            state.taskInfo.stage = TaskStage.FINISHED;
            state.taskSubStage = completeSubStage;
            sendSelfPatch(state);
        }
    }

    /**
     * Generates a selfLink for stale resource document deletion task based on
     * given endpointLink.
     *
     * @param endpointLink SelfLink of endpoint being deleted.
     * @return SelfLink of stale resource document deletion task for the given endpointLink.
     */
    public static String getResourceGroomerTaskUri(String endpointLink, boolean isDeferred) {
        String selfLink =  UriUtils.buildUriPath(ResourceGroomerTaskService.FACTORY_LINK,
                UriUtils.getLastPathSegment(endpointLink) + SEPARATOR + RESOURCE_GROOMER_TASK_POSTFIX);

        if (isDeferred) {
            selfLink = selfLink + SEPARATOR + DEFERRED;
        }

        return selfLink;
    }

    /**
     * Immediate groomer task run might not delete/disassociate all resources belonging to the endpoint
     * being removed due to race with enumeration. We schedule a groomer task again for the same endpoint
     * after 3 minutes to ensure all stale resources are deleted.
     */
    private void scheduleDeferredGroomer(EndpointRemovalTaskState task) {
        logInfo(() -> String.format("Scheduling a resource groomer in %s seconds for tenant '%s' "
                        + "upon removal of endpoint '%s'", RESOURCE_GROOMER_DELAY_SECONDS,
                task.endpoint.tenantLinks, task.endpoint.documentSelfLink));
        ResourceGroomerTaskService.EndpointResourceDeletionRequest
                groomerRequest = new ResourceGroomerTaskService
                .EndpointResourceDeletionRequest();
        groomerRequest.tenantLinks = new HashSet<>(task.tenantLinks);
        groomerRequest.endpointLink = task.endpoint.documentSelfLink;
        groomerRequest.documentSelfLink = getResourceGroomerTaskUri(task.endpoint.documentSelfLink, true);

        getHost().schedule(() -> {
            sendWithDeferredResult(Operation.createPost(this, ResourceGroomerTaskService.FACTORY_LINK)
                    .setBody(groomerRequest))
                    .whenComplete((o, e) -> {
                        if (e != null) {
                            logWarning("Error running resource groomer for tenant '%s' during "
                                            + "removal of endpoint '%s': %s", task.endpoint.tenantLinks,
                                    task.endpoint.documentSelfLink, Utils.toString(e));
                        }
                    });
        }, RESOURCE_GROOMER_DELAY_SECONDS, TimeUnit.SECONDS);
    }
}
