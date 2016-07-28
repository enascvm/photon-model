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

import static com.vmware.photon.controller.model.tasks.TaskUtils.getAdapterUri;
import static com.vmware.photon.controller.model.tasks.TaskUtils.sendFailurePatch;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.UriPaths.AdapterTypePath;
import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest;
import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.RequestType;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationSequence;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.TaskService;

/**
 * Endpoint allocation task service, an entry point to configure endpoints.
 */
public class EndpointAllocationTaskService
        extends TaskService<EndpointAllocationTaskService.EndpointAllocationTaskState> {
    public static final String FACTORY_LINK = UriPaths.PROVISIONING + "/endpoint-tasks";

    public static final String FIELD_NAME_CUSTOM_PROP_TYPE = "__endpointType";

    /**
     * SubStage.
     */
    public enum SubStage {
        VALIDATE_CREDENTIALS,
        CREATE_UPDATE_ENDPOINT,
        INVOKE_ADAPTER,
        PROVISIONING_CONTAINERS,
        COMPLETED,
        FAILED
    }

    /**
     * Endpoint allocation task state.
     */
    public static class EndpointAllocationTaskState extends TaskService.TaskServiceState {

        /**
         * Endpoint payload to use to create Endpoint.
         */
        public EndpointState endpointState;

        /**
         * URI reference to the adapter used to validate and enhance the endpoint data.
         */
        public URI adapterReference;

        /**
         * If set to {@code true} the adapter will only validate the data, now state will be created
         * or modified.
         */
        public boolean validateOnly;

        /**
         * Tracks the task's substage.
         */
        public SubStage taskSubStage;

        /**
         * Mock requests are used for testing.
         */
        public boolean isMockRequest = false;
    }

    public static final long DEFAULT_TIMEOUT_MICROS = TimeUnit.MINUTES
            .toMicros(10);

    public EndpointAllocationTaskService() {
        super(EndpointAllocationTaskState.class);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handleStart(Operation start) {
        if (!start.hasBody()) {
            start.fail(new IllegalArgumentException("body is required"));
            return;
        }

        EndpointAllocationTaskState state = getBody(start);

        validateAndCompleteStart(start, state);
    }

    @Override
    public void handlePatch(Operation patch) {
        EndpointAllocationTaskState body = getBody(patch);
        EndpointAllocationTaskState currentState = getState(patch);

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
            logInfo("task is complete");
            break;
        case FAILED:
        case CANCELLED:
            break;
        default:
            break;
        }
    }

    private void validateAndCompleteStart(Operation start,
            EndpointAllocationTaskState state) {
        try {
            validateState(state);
        } catch (Exception e) {
            start.fail(e);
            return;
        }

        start.complete();

        sendSelfPatch(createUpdateSubStageTask(state.taskSubStage));
    }

    private void handleStagePatch(EndpointAllocationTaskState currentState) {

        adjustStat(currentState.taskSubStage.toString(), 1);

        switch (currentState.taskSubStage) {
        case VALIDATE_CREDENTIALS:
            validateCredentials(currentState, currentState.validateOnly
                    ? SubStage.COMPLETED : SubStage.CREATE_UPDATE_ENDPOINT);
            break;
        case CREATE_UPDATE_ENDPOINT:
            createOrUpdateEndpoint(currentState);
            break;
        case INVOKE_ADAPTER:
            invokeAdapter(currentState, SubStage.COMPLETED);
            break;
        case FAILED:
            break;
        case COMPLETED:
            complete(currentState, SubStage.COMPLETED);
            break;
        default:
            break;
        }
    }

    private void invokeAdapter(EndpointAllocationTaskState currentState, SubStage next) {
        EndpointConfigRequest req = new EndpointConfigRequest();
        req.isMockRequest = currentState.isMockRequest;
        req.requestType = RequestType.ENHANCE;
        req.resourceReference = UriUtils.buildUri(getHost(),
                currentState.endpointState.documentSelfLink);
        req.taskReference = UriUtils.buildUri(getHost(), getSelfLink());
        sendRequest(Operation
                .createPatch(currentState.adapterReference)
                .setBody(req)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                logWarning(
                                        "PATCH to endpoint config adapter service %s, failed: %s",
                                        o.getUri(), e.toString());
                                sendFailurePatch(this, currentState, e);
                                return;
                            }
                        }));
    }

    private void createOrUpdateEndpoint(EndpointAllocationTaskState currentState) {

        EndpointState es = currentState.endpointState;

        Operation op;
        if (es.documentSelfLink != null) {
            op = Operation.createPut(this, es.documentSelfLink);
        } else {
            op = Operation.createPost(this, EndpointService.FACTORY_LINK);
        }

        ComputeDescription computeDescription = configureDescription(es);
        ComputeState computeState = configureCompute(es);
        Operation cdOp = Operation.createPost(this, ComputeDescriptionService.FACTORY_LINK);
        Operation compOp = Operation.createPost(this, ComputeService.FACTORY_LINK);

        OperationSequence sequence;
        if (es.authCredentialsLink == null) {
            AuthCredentialsServiceState auth = configureAuth(es);
            Operation authOp = Operation.createPost(this, AuthCredentialsService.FACTORY_LINK)
                    .setBody(auth);
            sequence = OperationSequence.create(authOp)
                    .setCompletion((ops, exs) -> {
                        if (exs != null) {
                            long firstKey = exs.keySet().iterator().next();
                            exs.values()
                                    .forEach(ex -> logWarning("Error: %s", ex.getMessage()));
                            sendFailurePatch(this, currentState, exs.get(firstKey));
                            return;
                        }

                        Operation o = ops.get(authOp.getId());
                        AuthCredentialsServiceState authState = o
                                .getBody(AuthCredentialsServiceState.class);
                        computeDescription.authCredentialsLink = authState.documentSelfLink;
                        es.authCredentialsLink = authState.documentSelfLink;
                        cdOp.setBody(computeDescription);
                    })
                    .next(cdOp);
        } else {
            sequence = OperationSequence.create(cdOp);
        }

        sequence
                .setCompletion((ops, exs) -> {
                    if (exs != null) {
                        long firstKey = exs.keySet().iterator().next();
                        exs.values()
                                .forEach(ex -> logWarning("Error: %s", ex.getMessage()));
                        sendFailurePatch(this, currentState, exs.get(firstKey));
                        return;
                    }

                    Operation o = ops.get(cdOp.getId());
                    ComputeDescription desc = o.getBody(ComputeDescription.class);
                    computeState.descriptionLink = desc.documentSelfLink;
                    es.computeDescriptionLink = desc.documentSelfLink;
                    compOp.setBody(computeState);
                })
                .next(compOp)
                .setCompletion(
                        (ops, exs) -> {
                            if (exs != null) {
                                long firstKey = exs.keySet().iterator().next();
                                exs.values().forEach(
                                        ex -> logWarning("Error: %s", ex.getMessage()));
                                sendFailurePatch(this, currentState, exs.get(firstKey));
                                return;
                            }
                            Operation csOp = ops.get(compOp.getId());
                            ComputeState c = csOp.getBody(ComputeState.class);
                            es.computeLink = c.documentSelfLink;
                            op.setBody(es);
                        })
                .next(op)
                .setCompletion((ops, exs) -> {
                    if (exs != null) {
                        long firstKey = exs.keySet().iterator().next();
                        exs.values().forEach(
                                ex -> logWarning("Error: %s", ex.getMessage()));
                        sendFailurePatch(this, currentState, exs.get(firstKey));
                        return;
                    }
                    Operation esOp = ops.get(op.getId());
                    EndpointState endpoint = esOp.getBody(EndpointState.class);
                    EndpointAllocationTaskState state = createUpdateSubStageTask(
                            SubStage.INVOKE_ADAPTER);
                    state.endpointState = endpoint;
                    sendSelfPatch(state);
                }).sendWith(this);
    }

    private void validateCredentials(EndpointAllocationTaskState currentState, SubStage next) {
        EndpointConfigRequest req = new EndpointConfigRequest();
        req.requestType = RequestType.VALIDATE;
        req.endpointProperties = currentState.endpointState.endpointProperties;
        req.isMockRequest = currentState.isMockRequest;

        Operation
                .createPatch(currentState.adapterReference)
                .setBody(req)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning(e.getMessage());
                        sendFailurePatch(this, currentState, e);
                        return;
                    }

                    sendSelfPatch(createUpdateSubStageTask(next));
                }).sendWith(this);
    }

    private void validateState(EndpointAllocationTaskState state) {
        if (state.endpointState == null) {
            throw new IllegalArgumentException("endpointState is required");
        }

        if (state.endpointState.endpointType == null) {
            throw new IllegalArgumentException("endpointType is required");
        }

        if (state.taskInfo == null || state.taskInfo.stage == null) {
            state.taskInfo = new TaskState();
            state.taskInfo.stage = TaskStage.CREATED;
        }

        if (state.adapterReference == null) {
            state.adapterReference = getAdapterUri(this, AdapterTypePath.ENDPOINT_CONFIG_ADAPTER,
                    state.endpointState.endpointType);
        }

        if (state.taskSubStage == null) {
            state.taskSubStage = SubStage.VALIDATE_CREDENTIALS;
        }

        if (state.documentExpirationTimeMicros == 0) {
            state.documentExpirationTimeMicros = Utils.getNowMicrosUtc()
                    + DEFAULT_TIMEOUT_MICROS;
        }
    }

    private boolean validateTransitionAndUpdateState(Operation patch,
            EndpointAllocationTaskState body,
            EndpointAllocationTaskState currentState) {

        TaskStage currentStage = currentState.taskInfo.stage;
        SubStage currentSubStage = currentState.taskSubStage;
        boolean isUpdate = false;

        if (body.endpointState != null) {
            currentState.endpointState = body.endpointState;
            isUpdate = true;
        }

        if (body.adapterReference != null) {
            currentState.adapterReference = body.adapterReference;
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
            logWarning("Referer %s is patching us to failure: %s",
                    patch.getReferer(), Utils.toJsonHtml(body.taskInfo.failure));
            currentState.taskInfo.failure = body.taskInfo.failure;
            currentState.taskInfo.stage = body.taskInfo.stage;
            currentState.taskSubStage = SubStage.FAILED;
            return false;
        }

        if (body.taskSubStage != null) {
            if (currentSubStage.ordinal() > body.taskSubStage.ordinal()) {
                patch.fail(new IllegalArgumentException(
                        "subStage can not move backwards:" + body.taskSubStage));
                return true;
            }
        }

        currentState.taskInfo.stage = body.taskInfo.stage;
        currentState.taskSubStage = body.taskSubStage;

        logInfo("Moving from %s(%s) to %s(%s)", currentSubStage, currentStage,
                body.taskSubStage, body.taskInfo.stage);

        return false;
    }

    private AuthCredentialsServiceState configureAuth(EndpointState state) {
        AuthCredentialsServiceState authState = new AuthCredentialsServiceState();
        authState.tenantLinks = state.tenantLinks;
        authState.customProperties = new HashMap<String, String>();
        if (state.customProperties != null) {
            authState.customProperties.putAll(state.customProperties);
        }
        authState.customProperties.put(FIELD_NAME_CUSTOM_PROP_TYPE, state.endpointType);

        return authState;
    }

    private ComputeDescription configureDescription(EndpointState state) {

        // setting up a host, so all have VM_HOST as a child
        ComputeDescription cd = new ComputeDescription();
        List<String> children = new ArrayList<>();
        // TODO: switch to VM_HOST once we introduce hosts discovery
        // children.add(ComputeDescriptionService.ComputeDescription.ComputeType.VM_HOST.toString());
        children.add(ComputeDescriptionService.ComputeDescription.ComputeType.VM_GUEST.toString());
        cd.supportedChildren = children;
        cd.tenantLinks = state.tenantLinks;
        cd.authCredentialsLink = state.authCredentialsLink;
        cd.name = state.name;
        cd.id = UUID.randomUUID().toString();
        cd.customProperties = new HashMap<String, String>();
        if (state.customProperties != null) {
            cd.customProperties.putAll(state.customProperties);
        }
        cd.customProperties.put(FIELD_NAME_CUSTOM_PROP_TYPE, state.endpointType);

        return cd;
    }

    private ComputeState configureCompute(EndpointState state) {
        ComputeState computeHost = new ComputeState();
        computeHost.id = UUID.randomUUID().toString();
        computeHost.tenantLinks = state.tenantLinks;
        computeHost.customProperties = new HashMap<>();
        if (state.customProperties != null) {
            computeHost.customProperties.putAll(state.customProperties);
        }
        computeHost.customProperties.put(FIELD_NAME_CUSTOM_PROP_TYPE, state.endpointType);
        return computeHost;
    }

    private EndpointAllocationTaskState createUpdateSubStageTask(SubStage subStage) {
        return createUpdateSubStageTask(null, subStage);
    }

    private EndpointAllocationTaskState createUpdateSubStageTask(TaskStage stage,
            SubStage subStage) {
        return createUpdateSubStageTask(stage, subStage, null);
    }

    private EndpointAllocationTaskState createUpdateSubStageTask(TaskStage stage, SubStage subStage,
            Throwable e) {
        EndpointAllocationTaskState body = new EndpointAllocationTaskState();
        body.taskInfo = new TaskState();
        if (e == null) {
            body.taskInfo.stage = stage == null ? TaskStage.STARTED : stage;
            body.taskSubStage = subStage;
        } else {
            body.taskInfo.stage = TaskStage.FAILED;
            body.taskInfo.failure = Utils.toServiceErrorResponse(e);
            logWarning("Patching to failed: %s", Utils.toString(e));
        }

        return body;
    }

    private void complete(EndpointAllocationTaskState state, SubStage completeSubStage) {
        if (!isFailedOrCancelledTask(state)) {
            state.taskInfo.stage = TaskStage.FINISHED;
            state.taskSubStage = completeSubStage;
            sendSelfPatch(state);
        }
    }

    private boolean isFailedOrCancelledTask(EndpointAllocationTaskState state) {
        return state.taskInfo != null &&
                (TaskStage.FAILED == state.taskInfo.stage ||
                        TaskStage.CANCELLED == state.taskInfo.stage);
    }
}
