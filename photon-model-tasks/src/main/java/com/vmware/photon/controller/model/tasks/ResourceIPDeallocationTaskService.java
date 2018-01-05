/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SERVICE_USE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.tasks.IPAddressAllocationTaskService.IPAddressAllocationTaskState;
import com.vmware.photon.controller.model.tasks.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.TaskService;

/**
 * Task implementing the de-allocation of IP addresses from a compute resource.
 * The allocated ip addresses are recorded on the network interfaces.
 */
public class ResourceIPDeallocationTaskService
        extends TaskService<ResourceIPDeallocationTaskService.ResourceIPDeallocationTaskState> {

    public static final String FACTORY_LINK = UriPaths.TASKS + "/resource-ip-deallocation-tasks";

    /**
     * Represents the state of resource deallocation task.
     */
    public static class ResourceIPDeallocationTaskState extends TaskService.TaskServiceState {

        /**
         * SubStage.
         */
        public enum SubStage {
            CREATED, DEALLOCATE_IP_ADDRESSES, DEALLOCATE_IP_ADDRESSES_IN_PROGRESS, FINISHED, FAILED
        }

        /**
         * (Internal) Describes task sub-stage.
         */
        @ServiceDocument.Documentation(description = "Describes task sub-stage.")
        @ServiceDocument.PropertyOptions(usage = { SERVICE_USE }, indexing = STORE_ONLY)
        public SubStage taskSubStage;

        /**
         * Connected resource that is associated with IP address(es)
         */
        @ServiceDocument.Documentation(description = "The connected resource associated with IP address(es).")
        @ServiceDocument.PropertyOptions(usage = {
                ServiceDocumentDescription.PropertyUsageOption.REQUIRED,
                ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT
                })
        public String resourceLink;

        /**
         * Callback link and response from the service initiated this task.
         */
        @ServiceDocument.Documentation(description = "Callback link and response from the service initiated this task.")
        @ServiceDocument.PropertyOptions(usage = { PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL,
                PropertyUsageOption.SINGLE_ASSIGNMENT }, indexing = PropertyIndexingOption.STORE_ONLY)
        public ServiceTaskCallback serviceTaskCallback;

        /**
         * A list of tenant links which can access this task.
         */
        @ServiceDocument.Documentation(description = "A list of tenant links which can access this task.")
        @ServiceDocument.PropertyOptions(usage = { PropertyUsageOption.SINGLE_ASSIGNMENT,
                PropertyUsageOption.OPTIONAL }, indexing = PropertyIndexingOption.STORE_ONLY)
        public List<String> tenantLinks;

        public String toString() {
            String sb = "Deallocation for resource: " + this.resourceLink;
            return sb;
        }
    }

    public ResourceIPDeallocationTaskService() {
        super(ResourceIPDeallocationTaskState.class);
        super.toggleOption(Service.ServiceOption.PERSISTENCE, true);
        super.toggleOption(Service.ServiceOption.REPLICATION, true);
        super.toggleOption(Service.ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(Service.ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handlePatch(Operation patch) {
        if (!patch.hasBody()) {
            patch.fail(new IllegalArgumentException("body is required"));
            return;
        }

        ResourceIPDeallocationTaskState body = getBody(patch);
        ResourceIPDeallocationTaskState currentState = getState(patch);

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
            logInfo(() -> "Task is complete");
            sendCallbackResponse(currentState);
            break;
        case FAILED:
            logInfo(() -> "Task failed");
            sendCallbackResponse(currentState);
            break;
        default:
            break;
        }
    }

    protected void handleStagePatch(ResourceIPDeallocationTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            deallocateIPAddressesForResource(state);
            break;

        case DEALLOCATE_IP_ADDRESSES_IN_PROGRESS:
            break;

        case FAILED:
            logWarning(() -> String.format("Task failed with %s",
                    Utils.toJsonHtml(state.taskInfo.failure)));
            sendSelfPatch(state, TaskState.TaskStage.FAILED, null);
            break;

        case FINISHED:
            sendSelfPatch(state, TaskState.TaskStage.FINISHED, null);
            break;

        default:
            break;
        }
    }

    @Override
    public void handleStart(Operation startOp) {
        try {
            if (!startOp.hasBody()) {
                startOp.fail(new IllegalArgumentException("body is required"));
                return;
            }

            ResourceIPDeallocationTaskState taskState = startOp.getBody(ResourceIPDeallocationTaskState.class);
            initializeState(taskState, startOp);

            // Send completion to the caller (with a CREATED state)
            startOp.setBody(taskState).complete();

            // And then start internal state machine
            sendSelfPatch(taskState, TaskState.TaskStage.STARTED, null);
        } catch (Throwable e) {
            logSevere(e);
            startOp.fail(e);
        }
    }

    /**
     * Customize the initialization logic (set the task with default values) that's part of initial
     * {@code POST} creating the task service.
     *
     * @see #handleStart(Operation)
     */
    @Override
    protected void initializeState(ResourceIPDeallocationTaskState startState, Operation startOp) {
        if (startState.taskInfo == null || startState.taskInfo.stage == null) {
            startState.taskInfo = new TaskState();
            startState.taskInfo.stage = TaskState.TaskStage.CREATED;
        }

        startState.taskSubStage = ResourceIPDeallocationTaskState.SubStage.CREATED;
    }

    /**
     * Validates patch transition and updates it to the requested state
     *
     * @param patch        Patch operation
     * @param body         Body of the patch request
     * @param currentState Current state of patch request
     * @return True if transition is invalid. False otherwise.
     */
    private boolean validateTransitionAndUpdateState(Operation patch,
            ResourceIPDeallocationTaskState body,
            ResourceIPDeallocationTaskState currentState) {

        TaskState.TaskStage currentStage = currentState.taskInfo.stage;
        ResourceIPDeallocationTaskState.SubStage currentSubStage = currentState.taskSubStage;
        boolean isUpdate = false;

        if (body.resourceLink != null) {
            currentState.resourceLink = body.resourceLink;
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

        if (body.taskInfo.failure != null) {
            logWarning(() -> String.format("Referrer %s is patching us to failure: %s",
                    patch.getReferer(), Utils.toJsonHtml(body.taskInfo.failure)));
            currentState.taskInfo.failure = body.taskInfo.failure;
            currentState.taskInfo.stage = body.taskInfo.stage;
            currentState.taskSubStage = ResourceIPDeallocationTaskState.SubStage.FAILED;
            return false;
        }

        if (TaskState.isFinished(body.taskInfo)) {
            currentState.taskInfo.stage = body.taskInfo.stage;
            currentState.taskSubStage = ResourceIPDeallocationTaskState.SubStage.FINISHED;
            return false;
        }

        if (currentSubStage != null && body.taskSubStage != null
                && currentSubStage.ordinal() > body.taskSubStage.ordinal()) {
            patch.fail(new IllegalArgumentException(
                    "subStage can not move backwards:" + body.taskSubStage));
            return true;
        }

        currentState.taskInfo.stage = body.taskInfo.stage;
        currentState.taskSubStage = body.taskSubStage;

        logFine(() -> String.format("Moving from %s(%s) to %s(%s)", currentSubStage, currentStage,
                body.taskSubStage, body.taskInfo.stage));

        return false;
    }

    private void deallocateIPAddressesForResource(ResourceIPDeallocationTaskState state) {
        try {
            ResourceIPDeallocationContext ctx = new ResourceIPDeallocationContext();
            ctx.resourceLink = state.resourceLink;

            DeferredResult.completed(ctx)
                    .thenCompose(this::getComputeResource)
                    .thenCompose(this::getNetworkInterfacesForCompute)
                    .whenComplete((op, exc) -> {
                        if (exc != null) {
                            failTask(exc, "FAILED: deallocateIPAddressesForResource - %s", Utils.toString(exc.getCause()));
                        } else {
                            deallocateIPAddresses(state, ctx);
                        }
                    });
        } catch (Throwable t) {
            failTask(t, t.getMessage());
        }
    }

    private DeferredResult<ResourceIPDeallocationContext> getComputeResource(
            ResourceIPDeallocationContext ctx) {
        Operation op = Operation.createGet(ComputeService.ComputeStateWithDescription
                .buildUri(UriUtils.buildUri(getHost(), ctx.resourceLink)));

        return sendWithDeferredResult(op, ComputeState.class)
                .thenApply(computeState -> {
                    ctx.computeResource = computeState;

                    logFine("Retrieved resource [%s]",
                            ctx.resourceLink);

                    return ctx;
                });
    }

    private DeferredResult<ResourceIPDeallocationContext> getNetworkInterfacesForCompute(
            ResourceIPDeallocationContext ctx) {
        if (ctx.computeResource == null) {
            logWarning("Compute resource is null");
            return DeferredResult.completed(ctx);
        }

        if (ctx.computeResource.networkInterfaceLinks == null
                || ctx.computeResource.networkInterfaceLinks.isEmpty()) {
            logWarning("No network interfaces configured for compute instance %s."
                            + " No IP addresses should be deallocated.",
                    ctx.resourceLink);
            return DeferredResult.completed(ctx);
        }

        return DeferredResult.allOf(ctx.computeResource.networkInterfaceLinks.stream()
                .map(nisLink -> sendWithDeferredResult(Operation.createGet(this, nisLink),
                        NetworkInterfaceState.class))
                .collect((Collectors.toList())))
                .thenApply(networkInterfaceStates -> {
                    for (NetworkInterfaceState nis : networkInterfaceStates) {
                        if (nis != null && nis.addressLink != null) {
                            ctx.networkInterfaceToIPAddressMap
                                    .put(nis.documentSelfLink, nis.addressLink);
                        }
                    }
                    return ctx;
                });
    }

    private void deallocateIPAddresses(ResourceIPDeallocationTaskState state,
            ResourceIPDeallocationContext ctx) {

        if (ctx.networkInterfaceToIPAddressMap.isEmpty()) {
            logFine("No network interfaces with IP addresses to deallocate for compute resource [%s]",
                    state.resourceLink);
            proceedTo(ResourceIPDeallocationTaskState.SubStage.FINISHED,
                    null);
        } else {
            startDeallocatingIPs(state, ctx, null);
            proceedTo(ResourceIPDeallocationTaskState.SubStage.DEALLOCATE_IP_ADDRESSES_IN_PROGRESS,
                    null);
        }
    }

    private void startDeallocatingIPs(ResourceIPDeallocationTaskState state,
            ResourceIPDeallocationContext ctx,
            String subTaskLink) {

        if (subTaskLink == null) {
            createSubTaskCallback(state, ctx, link -> startDeallocatingIPs(state, ctx, link));
            return;
        }

        logFine("Starting deallocate of (%d) IPs for compute resource [%d] using sub task %s",
                ctx.networkInterfaceToIPAddressMap.size(), ctx.resourceLink, subTaskLink);

        List<DeferredResult<Operation>> deallocateOperationDRs = new ArrayList<>();

        ctx.networkInterfaceToIPAddressMap.keySet().forEach(networkInterfaceLink -> {
            String ipAddressLink = ctx.networkInterfaceToIPAddressMap.get(networkInterfaceLink);

            IPAddressAllocationTaskState deallocationTaskState = new IPAddressAllocationTaskState();
            deallocationTaskState.serviceTaskCallback = ServiceTaskCallback
                    .create(UriUtils.buildUri(
                            getHost(), subTaskLink));
            deallocationTaskState.serviceTaskCallback.onSuccessFinishTask();
            // Similar to instance deletes - a failure for one network interface deallocate will fail the task
            deallocationTaskState.serviceTaskCallback.onErrorFailTask();

            deallocationTaskState.requestType = IPAddressAllocationTaskState.RequestType.DEALLOCATE;
            deallocationTaskState.connectedResourceLink = networkInterfaceLink;
            deallocationTaskState.ipAddressLinks = new ArrayList<>();
            deallocationTaskState.ipAddressLinks.add(ipAddressLink);

            Operation deallocateNisOperation = Operation.createPost(this,
                    IPAddressAllocationTaskService.FACTORY_LINK)
                    .setBody(deallocationTaskState);

            DeferredResult<Operation> deallocateOperationDR = sendWithDeferredResult(
                    deallocateNisOperation);

            deallocateOperationDRs.add(deallocateOperationDR);
        });

        DeferredResult.allOf(deallocateOperationDRs)
                .exceptionally(t -> {
                    String msg = "Failure deallocating IP addresses for a computeResource: [%s]";
                    logWarning(msg, state.resourceLink, t.getMessage());
                    failTask(t, msg, state.resourceLink);
                    return null;
                });
    }

    private void createSubTaskCallback(ResourceIPDeallocationTaskState state,
            ResourceIPDeallocationContext ctx,
            Consumer<String> subTaskLinkConsumer) {
        // Callback will patch this service with a Result object after completion of subtasks
        ServiceTaskCallback<ResourceRemovalTaskService.SubStage> callback = ServiceTaskCallback
                .create(UriUtils.buildPublicUri(getHost(), getSelfLink()));
        SubTaskService.SubTaskState<ResourceRemovalTaskService.SubStage> subTaskInitState = new SubTaskService.SubTaskState<>();

        // tell the sub task with what to patch us, on completion
        subTaskInitState.serviceTaskCallback = callback;
        callback.onErrorFailTask();
        callback.onSuccessFinishTask();
        subTaskInitState.completionsRemaining = ctx.networkInterfaceToIPAddressMap.size();
        subTaskInitState.documentExpirationTimeMicros = state.documentExpirationTimeMicros;
        Operation startPost = Operation
                .createPost(this, SubTaskService.FACTORY_LINK)
                .setBody(subTaskInitState)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                failTask(e, "Failure creating sub task: %s", e.getMessage());
                                return;
                            }
                            SubTaskService.SubTaskState<?> body = o
                                    .getBody(SubTaskService.SubTaskState.class);

                            subTaskLinkConsumer.accept(body.documentSelfLink);
                        });
        sendRequest(startPost);
    }

    private void failTask(Throwable e, String messageFormat, Object... args) {
        String message = String.format(messageFormat, args);
        logWarning(() -> message);

        ResourceIPDeallocationTaskState body = new ResourceIPDeallocationTaskState();
        body.taskInfo = new TaskState();
        body.taskInfo.stage = TaskState.TaskStage.FAILED;
        body.taskInfo.failure = Utils.toServiceErrorResponse(e);

        sendSelfPatch(body);
    }

    private void proceedTo(ResourceIPDeallocationTaskState.SubStage nextSubstage,
            Consumer<ResourceIPDeallocationTaskState> patchBodyConfigurator) {
        ResourceIPDeallocationTaskState state = new ResourceIPDeallocationTaskState();
        state.taskInfo = new TaskState();
        state.taskSubStage = nextSubstage;
        sendSelfPatch(state, TaskState.TaskStage.STARTED, patchBodyConfigurator);
    }

    private void sendCallbackResponse(ResourceIPDeallocationTaskState state) {
        ServiceTaskCallbackResponse result;
        if (state.taskInfo.stage == TaskState.TaskStage.FAILED) {
            result = state.serviceTaskCallback.getFailedResponse(state.taskInfo.failure);
        } else {
            result = state.serviceTaskCallback.getFinishedResponse();
        }

        logInfo("Calling back at the end of dealloaction of IP addresses for resource [%s]",
                state.resourceLink);

        sendRequest(Operation.createPatch(state.serviceTaskCallback.serviceURI).setBody(result));
    }

    /**
     * Service context that is created for passing subnet and ip range information between async calls.
     * Used only during allocation.
     */
    private static class ResourceIPDeallocationContext {
        /**
         * Link to compute resource
         */
        String resourceLink;

        /**
         * Compute resource state.
         */
        ComputeState computeResource;

        /**
         * Map network interface link to IP address link, for allocated network interfaces
         */
        HashMap<String, String> networkInterfaceToIPAddressMap = new HashMap<>();
    }
}