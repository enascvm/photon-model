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

import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.LINK;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.OPTIONAL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SERVICE_USE;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.tasks.ResourceRemovalTaskService.ResourceRemovalTaskState;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.OperationJoin.JoinedCompletionHandler;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskService;

/**
 * Task removes the endpoint service instances.
 */
public class EndpointRemovalTaskService
        extends TaskService<EndpointRemovalTaskService.EndpointRemovalTaskState> {
    public static final String FACTORY_LINK = UriPaths.PROVISIONING + "/endpoint-removal-tasks";

    public static final long DEFAULT_TIMEOUT_MICROS = TimeUnit.MINUTES
            .toMicros(10);

    /**
     * SubStage.
     */
    public static enum SubStage {
        LOAD_ENDPOINT,
        DELETE_ASSOCIATED,
        DELETE_RESOURCES,
        ISSUING_DELETES,
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
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, OPTIONAL }, indexing = { PropertyIndexingOption.EXPAND })
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
            logInfo("Task was completed");
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
            getEndpoint(currentState, SubStage.DELETE_ASSOCIATED);
            break;
        case DELETE_ASSOCIATED:
            deleteAssociatedDocuments(currentState, SubStage.DELETE_RESOURCES);
            break;
        case DELETE_RESOURCES:
            deleteResources(currentState, SubStage.ISSUING_DELETES);
            break;
        case ISSUING_DELETES:
            doInstanceDeletes(currentState, SubStage.FINISHED);
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

    /**
     * Delete all top level objects, which are representing this endpoint, including endpoint
     * itself.
     */
    private void doInstanceDeletes(EndpointRemovalTaskState currentState, SubStage next) {
        EndpointState endpoint = currentState.endpoint;

        Operation crdOp = Operation.createDelete(this, endpoint.authCredentialsLink);
        Operation cdsOp = Operation.createDelete(this, endpoint.computeDescriptionLink);
        Operation csOp = Operation.createDelete(this, endpoint.computeLink);
        Operation epOp = Operation.createDelete(this, endpoint.documentSelfLink);

        OperationJoin.create(crdOp, cdsOp, csOp, epOp).setCompletion((ops, exc) -> {
            if (exc != null) {
                logFine("Failed delete some of the associated resources, reason %s",
                        Utils.toString(exc));
            }
            // all resources deleted; mark the operation as complete
            sendSelfPatch(TaskStage.STARTED, next);
        }).sendWith(this);
    }

    /**
     * Delete associated resource, e.g. enumeration task if started.
     */
    private void deleteAssociatedDocuments(EndpointRemovalTaskState state, SubStage next) {
        Query resourceQuery = Query.Builder
                .create()
                .addFieldClause(
                        QuerySpecification
                                .buildCompositeFieldName(
                                        ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                                        EndpointAllocationTaskService.CUSTOM_PROP_ENPOINT_LINK),
                        state.endpoint.documentSelfLink)
                .build();
        QueryTask resourceQueryTask = QueryTask.Builder.createDirectTask().setQuery(resourceQuery)
                .build();
        resourceQueryTask.tenantLinks = state.tenantLinks;

        Operation.createPost(UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_QUERY_TASKS))
                .setBody(resourceQueryTask)
                .setCompletion(
                        (queryOp, throwable) -> {
                            if (throwable != null) {
                                logWarning(throwable.getMessage());
                                sendSelfPatch(TaskStage.STARTED, next);
                                return;
                            }

                            QueryTask rsp = queryOp.getBody(QueryTask.class);
                            if (rsp.results.documentLinks.isEmpty()) {
                                sendSelfPatch(TaskStage.STARTED, next);
                                return;
                            }

                            Stream<Operation> deleteOps = rsp.results.documentLinks.stream()
                                    .map(resultDoc -> Operation.createDelete(
                                            UriUtils.buildUri(getHost(), resultDoc))
                                            .setReferer(getUri()));
                            OperationJoin joinOp = OperationJoin.create(deleteOps);
                            JoinedCompletionHandler joinHandler = (ops, exc) -> {
                                if (exc != null) {
                                    logFine("Failed delete some of the associated resources, reason %s",
                                            Utils.toString(exc));
                                }
                                // all resources deleted;
                                sendSelfPatch(TaskStage.STARTED, next);
                            };
                            joinOp.setCompletion(joinHandler);
                            joinOp.sendWith(getHost());
                        })
                .sendWith(this);
    }

    /**
     * Delete computes discovered with this endpoint.
     */
    private void deleteResources(EndpointRemovalTaskState state, SubStage next) {
        Query resourceQuery = Query.Builder
                .create()
                .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK, state.endpoint.computeLink,
                        Occurance.SHOULD_OCCUR)
                .build();
        QuerySpecification qSpec = new QuerySpecification();
        qSpec.query = resourceQuery;

        ResourceRemovalTaskState removalServiceState = new ResourceRemovalTaskState();
        removalServiceState.documentSelfLink = UUID.randomUUID().toString();
        removalServiceState.resourceQuerySpec = qSpec;
        removalServiceState.options = EnumSet.of(TaskOption.DOCUMENT_CHANGES_ONLY);
        removalServiceState.isMockRequest = state.options.contains(TaskOption.IS_MOCK);
        removalServiceState.tenantLinks = state.tenantLinks;

        StatefulService service = this;

        Operation
                .createPost(UriUtils.buildUri(getHost(),
                        ResourceRemovalTaskService.FACTORY_LINK))
                .setBody(removalServiceState)
                .setCompletion((resourcePostOp, resourcePostEx) -> {
                    Consumer<Operation> onSuccess = new Consumer<Operation>() {
                        Set<String> finishedTaskLinks = new HashSet<>();

                        @Override
                        public void accept(Operation op) {
                            ResourceRemovalTaskState deletionState = op
                                    .getBody(ResourceRemovalTaskState.class);

                            TaskUtils.handleSubscriptionNotifications(service, op,
                                    deletionState.documentSelfLink, deletionState.taskInfo,
                                    1, createPatchSubStageTask(TaskStage.STARTED, next, null),
                                    finishedTaskLinks,
                                    false);
                        }
                    };
                    TaskUtils.subscribeToNotifications(this, onSuccess,
                            UriUtils.buildUriPath(ResourceRemovalTaskService.FACTORY_LINK,
                                    removalServiceState.documentSelfLink));
                }).sendWith(this);
    }

    public void getEndpoint(EndpointRemovalTaskState currentState, SubStage next) {
        sendRequest(Operation.createGet(this, currentState.endpointLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        // the task might have expired, with no results every
                        // becoming available
                        logWarning("Failure retrieving endpoint: %s, reason: %s",
                                currentState.endpointLink, e.toString());
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

    private boolean validateTransitionAndUpdateState(Operation patch,
            EndpointRemovalTaskState body, EndpointRemovalTaskState currentState) {

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
            patch.fail(new IllegalArgumentException(
                    "taskInfo and stage are required"));
            return true;
        }

        if (currentStage.ordinal() > body.taskInfo.stage.ordinal()) {
            patch.fail(new IllegalArgumentException(
                    "stage can not move backwards:" + body.taskInfo.stage));
            return true;
        }

        if (currentStage.ordinal() == body.taskInfo.stage.ordinal()
                && (body.taskSubStage == null || currentSubStage.ordinal() > body.taskSubStage
                        .ordinal())) {
            patch.fail(new IllegalArgumentException(
                    "subStage can not move backwards:" + body.taskSubStage));
            return true;
        }

        currentState.taskInfo.stage = body.taskInfo.stage;
        currentState.taskSubStage = body.taskSubStage;

        logInfo("Moving from %s(%s) to %s(%s)", currentSubStage, currentStage,
                body.taskSubStage, currentState.taskInfo.stage);

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
            logWarning("Patching to failed: %s", Utils.toString(e));
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
}
