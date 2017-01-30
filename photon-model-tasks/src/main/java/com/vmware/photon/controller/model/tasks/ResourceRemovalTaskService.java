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

import java.net.URI;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.adapterapi.ResourceOperationResponse;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskService;

/**
 * Task removes the compute service instances.
 */
public class ResourceRemovalTaskService
        extends TaskService<ResourceRemovalTaskService.ResourceRemovalTaskState> {
    public static final String FACTORY_LINK = UriPaths.PROVISIONING + "/resource-removal-tasks";

    public static final long DEFAULT_TIMEOUT_MICROS = TimeUnit.MINUTES
            .toMicros(10);

    /**
     * SubStage.
     */
    public static enum SubStage {
        WAITING_FOR_QUERY_COMPLETION,
        ISSUE_ADAPTER_DELETES,
        DELETE_DOCUMENTS,
        FINISHED,
        FAILED
    }

    /**
     * Represents the state of the removal task.
     */
    public static class ResourceRemovalTaskState extends TaskService.TaskServiceState {

        /**
         * Task sub stage.
         */
        public SubStage taskSubStage;

        /**
         * Query specification used to find the compute resources for removal.
         */
        public QueryTask.QuerySpecification resourceQuerySpec;

        /**
         * Set by service. Link to resource query task.
         */
        public String resourceQueryLink;

        /**
         * For testing instance service deletion.
         */
        public boolean isMockRequest;

        /**
         * A list of tenant links which can access this task.
         */
        public List<String> tenantLinks;

        /**
         * The error threshold.
         */
        public double errorThreshold;

        /**
         * Task options
         */
        public EnumSet<TaskOption> options;
    }

    public ResourceRemovalTaskService() {
        super(ResourceRemovalTaskState.class);
        super.toggleOption(Service.ServiceOption.REPLICATION, true);
        super.toggleOption(Service.ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(Service.ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handleStart(Operation start) {
        try {
            if (!start.hasBody()) {
                start.fail(new IllegalArgumentException("body is required"));
                return;
            }

            ResourceRemovalTaskState state = start
                    .getBody(ResourceRemovalTaskState.class);
            validateState(state);

            if (TaskState.isCancelled(state.taskInfo)
                    || TaskState.isFailed(state.taskInfo)
                    || TaskState.isFinished(state.taskInfo)) {
                start.complete();
                return;
            }

            QueryTask q = new QueryTask();
            q.documentExpirationTimeMicros = state.documentExpirationTimeMicros;
            q.querySpec = state.resourceQuerySpec;
            // make sure we expand the content
            if (!q.querySpec.options.contains(QueryOption.EXPAND_CONTENT)) {
                q.querySpec.options.add(QueryOption.EXPAND_CONTENT);
            }
            q.documentSelfLink = UUID.randomUUID().toString();
            q.tenantLinks = state.tenantLinks;
            // create the query to find resources
            sendRequest(Operation
                    .createPost(this, ServiceUriPaths.CORE_LOCAL_QUERY_TASKS)
                    .setBody(q)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            // the task might have expired, with no results
                            // every becoming available
                            logWarning("Failure retrieving query results: %s",
                                    e.toString());
                            sendFailureSelfPatch(e);
                            return;
                        }
                    }));

            // we do not wait for the query task creation to know its URI, the
            // URI is created
            // deterministically. The task itself is not complete but we check
            // for that in our state
            // machine
            state.resourceQueryLink = UriUtils.buildUriPath(
                    ServiceUriPaths.CORE_LOCAL_QUERY_TASKS, q.documentSelfLink);
            start.complete();

            sendSelfPatch(TaskState.TaskStage.STARTED, state.taskSubStage, null);
        } catch (Throwable e) {
            start.fail(e);
        }
    }

    @Override
    public void handlePatch(Operation patch) {
        ResourceRemovalTaskState body = patch
                .getBody(ResourceRemovalTaskState.class);
        ResourceRemovalTaskState currentState = getState(patch);

        if (validateTransitionAndUpdateState(patch, body, currentState)) {
            return;
        }

        patch.complete();

        switch (currentState.taskInfo.stage) {
        case CREATED:
            break;
        case STARTED:
            handleStagePatch(currentState, null);
            break;
        case FINISHED:
            logInfo("task is complete");
            break;
        case FAILED:
        case CANCELLED:
            break;
        default:
            break;
        }
    }

    private void handleStagePatch(ResourceRemovalTaskState currentState,
            QueryTask queryTask) {

        if (queryTask == null) {
            getQueryResults(currentState);
            return;
        }
        adjustStat(currentState.taskSubStage.toString(), 1);

        switch (currentState.taskSubStage) {
        case WAITING_FOR_QUERY_COMPLETION:
            if (TaskState.isFailed(queryTask.taskInfo)) {
                logWarning("query task failed: %s", Utils.toJsonHtml(queryTask.taskInfo.failure));
                currentState.taskInfo.stage = TaskState.TaskStage.FAILED;
                currentState.taskInfo.failure = queryTask.taskInfo.failure;
                sendSelfPatch(currentState);
                return;
            }
            if (TaskState.isFinished(queryTask.taskInfo)) {

                ResourceRemovalTaskState newState = new ResourceRemovalTaskState();
                newState.taskInfo = new TaskState();
                if (queryTask.results.documentLinks.size() == 0) {
                    newState.taskInfo.stage = TaskState.TaskStage.FINISHED;
                    newState.taskSubStage = SubStage.FINISHED;
                } else {
                    newState.taskInfo.stage = TaskState.TaskStage.STARTED;

                    newState.taskSubStage = currentState.options != null
                            && currentState.options.contains(TaskOption.DOCUMENT_CHANGES_ONLY)
                                    ? SubStage.DELETE_DOCUMENTS : SubStage.ISSUE_ADAPTER_DELETES;
                }
                sendSelfPatch(newState);
                return;
            }

            logFine("Resource query not complete yet, retrying");
            getHost().schedule(() -> {
                getQueryResults(currentState);
            }, 1, TimeUnit.SECONDS);
            break;
        case ISSUE_ADAPTER_DELETES:
            doInstanceDeletes(currentState, queryTask, null);
            break;
        case DELETE_DOCUMENTS:
            deleteDocuments(currentState, queryTask);
            break;
        case FAILED:
            break;
        case FINISHED:
            break;
        default:
            break;
        }
    }

    private void deleteDocuments(ResourceRemovalTaskState currentState, QueryTask queryTask) {
        Stream<Operation> deletes = queryTask.results.documents.values().stream()
                .map(d -> Utils.fromJson(d, ComputeState.class))
                .flatMap(c -> {
                    Stream<Operation> ops = Stream
                            .of(Operation.createDelete(this, c.documentSelfLink));
                    if (c.diskLinks != null && !c.diskLinks.isEmpty()) {
                        ops = Stream.concat(ops,
                                c.diskLinks.stream().map(l -> Operation.createDelete(this, l)));
                    }
                    if (c.networkInterfaceLinks != null && !c.networkInterfaceLinks.isEmpty()) {
                        ops = Stream.concat(ops, c.networkInterfaceLinks.stream()
                                .map(l -> Operation.createDelete(this, l)));
                    }
                    return ops;
                });
        OperationJoin.create(deletes)
                .setCompletion((ox, exc) -> {
                    // delete query
                    sendRequest(Operation.createDelete(this, currentState.resourceQueryLink));
                    if (exc != null) {
                        logSevere("Failure deleting compute states from the local system",
                                Utils.toString(exc));
                        sendFailureSelfPatch(exc.values().iterator().next());
                        return;
                    }
                    sendSelfPatch(TaskState.TaskStage.FINISHED, SubStage.FINISHED, null);
                })
                .sendWith(this);
    }

    private void doInstanceDeletes(ResourceRemovalTaskState currentState,
            QueryTask queryTask, String subTaskLink) {

        int resourceCount = queryTask.results.documentLinks.size();
        if (subTaskLink == null) {
            createSubTaskForDeleteCallbacks(currentState, resourceCount,
                    queryTask);
            return;
        }

        logFine("Starting delete of %d compute resources using sub task %s",
                resourceCount, subTaskLink);
        // for each compute resource link in the results, expand it with the
        // description, and issue
        // a DELETE request to its associated instance service.

        for (String resourceLink : queryTask.results.documentLinks) {
            URI u = ComputeStateWithDescription
                    .buildUri(UriUtils.buildUri(getHost(), resourceLink));
            sendRequest(Operation
                    .createGet(u)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            // we do not fail task if one delete failed ...
                            // send a FINISHED patch which is what a sub task
                            // would do. Since the
                            // current state
                            // is still at REMOVING_RESOURCES, we will just
                            // increment a counter
                            ResourceOperationResponse subTaskPatchBody = ResourceOperationResponse
                                    .fail(resourceLink, e);
                            sendPatch(subTaskLink, subTaskPatchBody);
                            return;
                        }
                        sendInstanceDelete(resourceLink, subTaskLink, o,
                                currentState);
                    }));
        }
    }

    /**
     * Before we proceed with issuing DELETE requests to the instance services we must create a sub
     * task that will track the DELETE completions. The instance service will issue a PATCH with
     * TaskStage.FINISHED, for every PATCH we send it, to delete the compute resource
     */
    private void createSubTaskForDeleteCallbacks(
            ResourceRemovalTaskState currentState, int resourceCount,
            QueryTask queryTask) {

        ServiceTaskCallback<SubStage> callback = ServiceTaskCallback.create(getSelfLink());
        callback.onSuccessTo(SubStage.DELETE_DOCUMENTS);

        SubTaskService.SubTaskState<SubStage> subTaskInitState = new SubTaskService.SubTaskState<SubStage>();

        // tell the sub task with what to patch us, on completion
        subTaskInitState.serviceTaskCallback = callback;
        subTaskInitState.completionsRemaining = resourceCount;
        subTaskInitState.errorThreshold = currentState.errorThreshold;
        subTaskInitState.tenantLinks = currentState.tenantLinks;
        subTaskInitState.documentExpirationTimeMicros = currentState.documentExpirationTimeMicros;
        Operation startPost = Operation
                .createPost(this, UUID.randomUUID().toString())
                .setBody(subTaskInitState)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                logWarning("Failure creating sub task: %s",
                                        Utils.toString(e));
                                this.sendSelfPatch(TaskState.TaskStage.FAILED,
                                        SubStage.FAILED, e);
                                return;
                            }
                            SubTaskService.SubTaskState<?> body = o
                                    .getBody(SubTaskService.SubTaskState.class);
                            // continue with deletes, passing the sub task link
                            doInstanceDeletes(currentState, queryTask,
                                    body.documentSelfLink);
                        });
        getHost().startService(startPost, new SubTaskService<SubStage>());
    }

    private void sendInstanceDelete(String resourceLink, String subTaskLink,
            Operation o, ResourceRemovalTaskState currentState) {
        ComputeStateWithDescription chd = o.getBody(ComputeStateWithDescription.class);
        if (chd.description.instanceAdapterReference != null) {
            ComputeInstanceRequest deleteReq = new ComputeInstanceRequest();
            deleteReq.resourceReference = UriUtils.buildUri(getHost(), resourceLink);
            deleteReq.taskReference = UriUtils.buildUri(getHost(),
                    subTaskLink);
            deleteReq.requestType = ComputeInstanceRequest.InstanceRequestType.DELETE;
            deleteReq.isMockRequest = currentState.isMockRequest;
            sendRequest(Operation
                    .createPatch(chd.description.instanceAdapterReference)
                    .setBody(deleteReq)
                    .setCompletion(
                            (deleteOp, e) -> {
                                if (e != null) {
                                    logWarning("PATCH to instance service %s, failed: %s",
                                            deleteOp.getUri(), e.toString());
                                    ResourceOperationResponse fail = ResourceOperationResponse
                                            .fail(resourceLink, e);
                                    sendPatch(subTaskLink, fail);
                                    return;
                                }
                            }));
        } else {
            logWarning("Compute instance %s doesn't not have configured instanceAdapter. Only "
                            + "local resource will be deleted.",
                    resourceLink);
            ResourceOperationResponse subTaskPatchBody = ResourceOperationResponse
                    .finish(resourceLink);
            sendPatch(subTaskLink, subTaskPatchBody);
        }
    }

    public void getQueryResults(ResourceRemovalTaskState currentState) {
        sendRequest(Operation.createGet(this, currentState.resourceQueryLink)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        // the task might have expired, with no results every
                        // becoming available
                        logWarning("Failure retrieving query results: %s", e.toString());
                        sendFailureSelfPatch(e);
                        return;
                    }

                    QueryTask rsp = o.getBody(QueryTask.class);
                    handleStagePatch(currentState, rsp);
                }));
    }

    private boolean validateTransitionAndUpdateState(Operation patch,
            ResourceRemovalTaskState body, ResourceRemovalTaskState currentState) {

        TaskState.TaskStage currentStage = currentState.taskInfo.stage;
        SubStage currentSubStage = currentState.taskSubStage;

        if (body.taskInfo == null || body.taskInfo.stage == null) {
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

        logFine("Moving from %s(%s) to %s(%s)", currentSubStage, currentStage,
                body.taskSubStage, currentState.taskInfo.stage);

        return false;
    }

    private void sendFailureSelfPatch(Throwable e) {
        sendSelfPatch(TaskState.TaskStage.FAILED, SubStage.FAILED, e);
    }

    private void sendSelfPatch(TaskState.TaskStage stage, SubStage subStage,
            Throwable e) {
        ResourceRemovalTaskState body = new ResourceRemovalTaskState();
        body.taskInfo = new TaskState();
        body.taskInfo.stage = stage;
        body.taskSubStage = subStage;
        if (e != null) {
            body.taskInfo.failure = Utils.toServiceErrorResponse(e);
            logWarning("Patching to failed: %s", Utils.toString(e));
        }
        sendSelfPatch(body);
    }

    private void sendPatch(String link, Object body) {
        Operation patch = Operation
                .createPatch(this, link)
                .setBody(body)
                .setCompletion(
                        (o, ex) -> {
                            if (ex != null) {
                                logWarning("Self patch failed: %s", Utils.toString(ex));
                            }
                        });
        sendRequest(patch);
    }

    public static void validateState(ResourceRemovalTaskState state) {
        if (state.resourceQuerySpec == null) {
            throw new IllegalArgumentException("resourceQuerySpec is required");
        }

        if (state.taskInfo == null || state.taskInfo.stage == null) {
            state.taskInfo = new TaskState();
            state.taskInfo.stage = TaskState.TaskStage.CREATED;
        }

        if (state.taskSubStage == null) {
            state.taskSubStage = SubStage.WAITING_FOR_QUERY_COMPLETION;
        }

        if (state.documentExpirationTimeMicros == 0) {
            state.documentExpirationTimeMicros = Utils.getNowMicrosUtc()
                    + DEFAULT_TIMEOUT_MICROS;
        }
    }
}
