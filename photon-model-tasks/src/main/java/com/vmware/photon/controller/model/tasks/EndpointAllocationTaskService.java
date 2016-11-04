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

import static com.vmware.photon.controller.model.ComputeProperties.ENDPOINT_LINK_PROP_NAME;
import static com.vmware.photon.controller.model.tasks.TaskUtils.getAdapterUri;
import static com.vmware.photon.controller.model.tasks.TaskUtils.sendFailurePatch;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.OPTIONAL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SERVICE_USE;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
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
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService.ResourceEnumerationTaskState;
import com.vmware.photon.controller.model.tasks.ScheduledTaskService.ScheduledTaskState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.OperationSequence;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceHost;
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

    public static final String CUSTOM_PROP_ENPOINT_TYPE = "__endpointType";

    private static final Long DEFAULT_SCHEDULED_TASK_INTERVAL_MICROS = TimeUnit.MINUTES.toMicros(5);

    public static final long DEFAULT_TIMEOUT_MICROS = TimeUnit.MINUTES.toMicros(10);

    /**
     * SubStage.
     */
    public enum SubStage {
        VALIDATE_CREDENTIALS,
        CREATE_UPDATE_ENDPOINT,
        INVOKE_ADAPTER,
        TRIGGER_ENUMERATION,
        COMPLETED,
        FAILED
    }

    /**
     * Endpoint allocation task state.
     */
    public static class EndpointAllocationTaskState extends TaskService.TaskServiceState {

        @Documentation(description = "Endpoint payload to use to create/update Endpoint.")
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT }, indexing = STORE_ONLY)
        public EndpointState endpointState;

        @Documentation(description = "URI reference to the adapter used to validate and enhance the endpoint data.")
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, OPTIONAL }, indexing = STORE_ONLY)
        public URI adapterReference;

        @Documentation(description = " List of tenants that can access this task.")
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, OPTIONAL }, indexing = {
                PropertyIndexingOption.EXPAND })
        public List<String> tenantLinks;

        @Documentation(description = "Task's options")
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, OPTIONAL }, indexing = STORE_ONLY)
        public EnumSet<TaskOption> options = EnumSet.noneOf(TaskOption.class);

        @Documentation(description = "If specified a Resource enumeration will be scheduled.")
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, OPTIONAL }, indexing = STORE_ONLY)
        public ResourceEnumerationRequest enumerationRequest;

        @Documentation(description = "Describes a service task sub stage.")
        @PropertyOptions(usage = { SERVICE_USE }, indexing = STORE_ONLY)
        public SubStage taskSubStage;
    }

    public static class ResourceEnumerationRequest {
        // resource pool the compute instance will be placed under
        public String resourcePoolLink;

        // time interval (in microseconds) between syncing state between
        // infra provider and the symphony server
        public Long refreshIntervalMicros;

        /**
         * delay before kicking off the task
         */
        public Long delayMicros;

    }

    public EndpointAllocationTaskService() {
        super(EndpointAllocationTaskState.class);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handleStart(Operation post) {
        EndpointAllocationTaskState initialState = validateStartPost(post);
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

    private void handleStagePatch(EndpointAllocationTaskState currentState) {

        adjustStat(currentState.taskSubStage.toString(), 1);

        switch (currentState.taskSubStage) {
        case VALIDATE_CREDENTIALS:
            validateCredentials(currentState,
                    currentState.options.contains(TaskOption.VALIDATE_ONLY)
                            ? SubStage.COMPLETED : SubStage.CREATE_UPDATE_ENDPOINT);
            break;
        case CREATE_UPDATE_ENDPOINT:
            if (currentState.endpointState.documentSelfLink != null) {
                updateOrCreateEndpoint(currentState);
            } else {
                createEndpoint(currentState);
            }
            break;
        case INVOKE_ADAPTER:
            invokeAdapter(currentState, currentState.enumerationRequest != null
                    ? SubStage.TRIGGER_ENUMERATION : SubStage.COMPLETED);
            break;
        case TRIGGER_ENUMERATION:
            triggerEnumeration(currentState, SubStage.COMPLETED);
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
        CompletionHandler c = (o, e) -> {
            if (e != null) {
                sendFailurePatch(this, currentState, e);
                return;
            }

            EndpointConfigRequest req = new EndpointConfigRequest();
            req.isMockRequest = currentState.options.contains(TaskOption.IS_MOCK);
            req.requestType = RequestType.ENHANCE;
            req.resourceReference = UriUtils.buildUri(getHost(),
                    currentState.endpointState.documentSelfLink);
            req.taskReference = o.getUri();
            sendEnhanceRequest(req, currentState);
        };

        createSubTask(c, next, currentState);
    }

    private void createSubTask(CompletionHandler c, SubStage nextStage,
            EndpointAllocationTaskState currentState) {
        EndpointAllocationTaskState patchBody = new EndpointAllocationTaskState();
        patchBody.taskInfo = new TaskState();
        patchBody.taskInfo.stage = TaskStage.STARTED;
        patchBody.taskSubStage = nextStage;

        SubTaskService.SubTaskState subTaskInitState = new SubTaskService.SubTaskState();
        subTaskInitState.parentPatchBody = Utils.toJson(patchBody);
        subTaskInitState.errorThreshold = 0;
        subTaskInitState.parentTaskLink = getSelfLink();
        subTaskInitState.tenantLinks = currentState.endpointState.tenantLinks;
        subTaskInitState.documentExpirationTimeMicros = currentState.documentExpirationTimeMicros;
        Operation startPost = Operation
                .createPost(this, UUID.randomUUID().toString())
                .setBody(subTaskInitState).setCompletion(c);
        getHost().startService(startPost, new SubTaskService());
    }

    private void sendEnhanceRequest(Object body, EndpointAllocationTaskState currentState) {
        sendRequest(Operation.createPatch(currentState.adapterReference).setBody(body)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning(
                                "PATCH to endpoint config adapter service %s, failed: %s",
                                o.getUri(), e.toString());
                        sendFailurePatch(this, currentState, e);
                        return;
                    }
                }));
    }

    private void createEndpoint(EndpointAllocationTaskState currentState) {

        EndpointState es = currentState.endpointState;
        if (es.documentSelfLink == null) {
            es.documentSelfLink = UriUtils.buildUriPath(EndpointService.FACTORY_LINK,
                    UUID.randomUUID().toString());
        }

        if (es.tenantLinks == null || es.tenantLinks.isEmpty()) {
            es.tenantLinks = currentState.tenantLinks;
        }

        Operation endpointOp = Operation.createPost(this, EndpointService.FACTORY_LINK);

        ComputeDescription computeDescription = configureDescription(es);
        ComputeState computeState = configureCompute(es);

        // TODO VSYM-2484: EndpointAllocationTaskService doesn't tag the compute host with resource
        // pool link
        if (currentState.enumerationRequest != null
                && currentState.enumerationRequest.resourcePoolLink != null) {
            es.resourcePoolLink = currentState.enumerationRequest.resourcePoolLink;
            computeState.resourcePoolLink = es.resourcePoolLink;
        }

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
            cdOp.setBody(computeDescription);
            sequence = OperationSequence.create(cdOp);
        }

        sequence = sequence
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
                });

        // Don't create resource pool, if a resource pool link was passed.
        if (es.resourcePoolLink == null) {
            Operation poolOp = createResourcePoolOp(es);
            sequence = sequence.next(poolOp)
                    .setCompletion((ops, exs) -> {
                        if (exs != null) {
                            long firstKey = exs.keySet().iterator().next();
                            exs.values().forEach(
                                    ex -> logWarning("Error creating resource pool: %s",
                                            ex.getMessage()));
                            sendFailurePatch(this, currentState, exs.get(firstKey));
                            return;
                        }
                        Operation o = ops.get(poolOp.getId());
                        ResourcePoolState poolState = o.getBody(ResourcePoolState.class);
                        es.resourcePoolLink = poolState.documentSelfLink;
                        computeState.resourcePoolLink = es.resourcePoolLink;

                        compOp.setBody(computeState);
                    });
        } else {

            Operation getPoolOp = Operation.createGet(this, es.resourcePoolLink);
            sequence = sequence.next(getPoolOp)
                    .setCompletion((ops, exs) -> {
                        if (exs != null) {
                            long firstKey = exs.keySet().iterator().next();
                            exs.values().forEach(
                                    ex -> logWarning("Error retrieving resource pool: %s",
                                            ex.getMessage()));
                            sendFailurePatch(this, currentState, exs.get(firstKey));
                            return;
                        }
                        Operation o = ops.get(getPoolOp.getId());
                        ResourcePoolState poolState = o.getBody(ResourcePoolState.class);
                        if (poolState.customProperties != null) {
                            String endpointLink = poolState.customProperties
                                    .get(ENDPOINT_LINK_PROP_NAME);
                            if (endpointLink != null && endpointLink.equals(es.documentSelfLink)) {
                                sendFailurePatch(this, currentState, new IllegalStateException(
                                        "Passed resource pool is associated with a different endpoint."));
                                return;
                            }
                        }
                        es.resourcePoolLink = poolState.documentSelfLink;
                        computeState.resourcePoolLink = es.resourcePoolLink;

                        compOp.setBody(computeState);
                    });
        }

        sequence.next(compOp)
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
                            endpointOp.setBody(es);
                        })
                .next(endpointOp)
                .setCompletion((ops, exs) -> {
                    if (exs != null) {
                        long firstKey = exs.keySet().iterator().next();
                        exs.values().forEach(
                                ex -> logWarning("Error: %s", ex.getMessage()));
                        sendFailurePatch(this, currentState, exs.get(firstKey));
                        return;
                    }
                    Operation esOp = ops.get(endpointOp.getId());
                    EndpointState endpoint = esOp.getBody(EndpointState.class);
                    EndpointAllocationTaskState state = createUpdateSubStageTask(
                            SubStage.INVOKE_ADAPTER);
                    state.endpointState = endpoint;
                    sendSelfPatch(state);
                }).sendWith(this);
    }

    private void updateOrCreateEndpoint(EndpointAllocationTaskState currentState) {

        EndpointState es = currentState.endpointState;

        Operation.createPatch(this, es.documentSelfLink)
                .setBody(es)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        if (o.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND) {
                            logInfo("Endpoint %s not found, creating new.", es.documentSelfLink);
                            createEndpoint(currentState);
                        } else {
                            logSevere("Failed to update endpoint %s : %s",
                                    es.documentSelfLink, e.getMessage());
                            sendFailurePatch(this, currentState, e);
                        }
                        return;
                    }
                    // update endpoint, but first stop current enumeration.
                    doUpdateEndpoint(o.getBody(EndpointState.class), currentState);
                })
                .sendWith(this);
    }

    private void doUpdateEndpoint(EndpointState loadedState,
            EndpointAllocationTaskState currentState) {
        loadedState.endpointProperties = currentState.endpointState.endpointProperties;

        String id = UriUtils.getLastPathSegment(loadedState.documentSelfLink);
        logInfo("Stopping any scheduled task for endpoint %s", loadedState.documentSelfLink);
        Operation.createDelete(this, UriUtils.buildUriPath(ScheduledTaskService.FACTORY_LINK, id))
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logInfo("Unable to delete ScheduleTaskState for endpoint %s : %s",
                                loadedState.documentSelfLink, e.getMessage());
                    }

                    EndpointAllocationTaskState state = createUpdateSubStageTask(
                            SubStage.INVOKE_ADAPTER);
                    state.endpointState = loadedState;
                    sendSelfPatch(state);
                }).sendWith(this);

    }

    private void validateCredentials(EndpointAllocationTaskState currentState, SubStage next) {
        EndpointConfigRequest req = new EndpointConfigRequest();
        req.requestType = RequestType.VALIDATE;
        req.endpointProperties = currentState.endpointState.endpointProperties;
        req.isMockRequest = currentState.options.contains(TaskOption.IS_MOCK);

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

    private void triggerEnumeration(EndpointAllocationTaskState currentState, SubStage next) {

        Operation.createGet(this, currentState.endpointState.computeLink)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                sendFailurePatch(this, currentState, e);
                                return;
                            }
                            if (currentState.enumerationRequest.resourcePoolLink == null) {
                                currentState.enumerationRequest.resourcePoolLink = currentState.endpointState.resourcePoolLink;
                            }
                            doTriggerEnumeration(currentState, next,
                                    o.getBody(ComputeState.class).adapterManagementReference);
                        })
                .sendWith(this);
    }

    private Operation createResourcePoolOp(EndpointState state) {
        ResourcePoolState poolState = new ResourcePoolState();
        poolState.customProperties = new HashMap<>();
        poolState.customProperties.put(ENDPOINT_LINK_PROP_NAME, state.documentSelfLink);
        String name = String.format("%s-%s", state.endpointType, state.name);
        poolState.name = name;
        poolState.id = poolState.name;
        poolState.tenantLinks = state.tenantLinks;
        return Operation.createPost(this, ResourcePoolService.FACTORY_LINK).setBody(poolState);
    }

    private void doTriggerEnumeration(EndpointAllocationTaskState currentState, SubStage next,
            URI adapterManagementReference) {
        EndpointState endpoint = currentState.endpointState;

        long intervalMicros = currentState.enumerationRequest.refreshIntervalMicros != null
                ? currentState.enumerationRequest.refreshIntervalMicros
                : DEFAULT_SCHEDULED_TASK_INTERVAL_MICROS;

        // Use endpooint documentSelfLink's last part as convention, so that we are able to stop
        // enumeration during endpoint update.
        String id = UriUtils.getLastPathSegment(endpoint.documentSelfLink);

        ResourceEnumerationTaskState enumTaskState = new ResourceEnumerationTaskState();
        enumTaskState.parentComputeLink = endpoint.computeLink;
        enumTaskState.resourcePoolLink = currentState.enumerationRequest.resourcePoolLink;
        enumTaskState.adapterManagementReference = adapterManagementReference;
        enumTaskState.tenantLinks = endpoint.tenantLinks;
        enumTaskState.documentSelfLink = id;
        enumTaskState.options = EnumSet.of(TaskOption.SELF_DELETE_ON_COMPLETION);
        if (currentState.options.contains(TaskOption.IS_MOCK)) {
            enumTaskState.options.add(TaskOption.IS_MOCK);
        }
        if (currentState.options.contains(TaskOption.PRESERVE_MISSING_RESOUCES)) {
            enumTaskState.options.add(TaskOption.PRESERVE_MISSING_RESOUCES);
        }

        ScheduledTaskState scheduledTaskState = new ScheduledTaskState();
        scheduledTaskState.documentSelfLink = id;
        scheduledTaskState.factoryLink = ResourceEnumerationTaskService.FACTORY_LINK;
        scheduledTaskState.initialStateJson = Utils.toJson(enumTaskState);
        scheduledTaskState.intervalMicros = intervalMicros;
        scheduledTaskState.delayMicros = currentState.enumerationRequest.delayMicros;
        scheduledTaskState.tenantLinks = endpoint.tenantLinks;
        scheduledTaskState.customProperties = new HashMap<>();
        scheduledTaskState.customProperties.put(ENDPOINT_LINK_PROP_NAME,
                endpoint.documentSelfLink);

        Operation.createPost(this, ScheduledTaskService.FACTORY_LINK)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
                .setBody(scheduledTaskState)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("Error triggering Enumeration task, reason: %s", e.getMessage());
                    }
                    sendSelfPatch(createUpdateSubStageTask(next));
                })
                .sendWith(this);
    }

    @Override
    protected void initializeState(EndpointAllocationTaskState state, Operation taskOperation) {
        if (state.taskSubStage == null) {
            state.taskSubStage = SubStage.VALIDATE_CREDENTIALS;
        }

        if (state.options == null) {
            state.options = EnumSet.noneOf(TaskOption.class);
        }

        if (state.adapterReference == null) {
            state.adapterReference = getAdapterUri(this, AdapterTypePath.ENDPOINT_CONFIG_ADAPTER,
                    state.endpointState.endpointType);
        }

        if (state.documentExpirationTimeMicros == 0) {
            state.documentExpirationTimeMicros = Utils.getNowMicrosUtc()
                    + DEFAULT_TIMEOUT_MICROS;
        }
        super.initializeState(state, taskOperation);
    }

    @Override
    protected EndpointAllocationTaskState validateStartPost(Operation taskOperation) {
        EndpointAllocationTaskState task = super.validateStartPost(taskOperation);
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

        if (task.endpointState == null) {
            taskOperation.fail(new IllegalArgumentException("endpointState is required"));
            return null;
        }

        if (task.endpointState.endpointType == null) {
            taskOperation.fail(new IllegalArgumentException("endpointType is required"));
            return null;
        }

        return task;
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

        if (body.enumerationRequest != null) {
            currentState.enumerationRequest = body.enumerationRequest;
            isUpdate = true;
        }

        if (body.adapterReference != null) {
            currentState.adapterReference = body.adapterReference;
            isUpdate = true;
        }

        if (body.options != null && !body.options.isEmpty()) {
            currentState.options = body.options;
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

        if (currentState.taskInfo.stage.ordinal() > body.taskInfo.stage.ordinal()) {
            patch.fail(new IllegalArgumentException(
                    "stage can not move backwards:" + body.taskInfo.stage));
            return true;
        }

        if (body.taskSubStage != null) {
            if (currentSubStage.ordinal() > body.taskSubStage.ordinal()) {
                patch.fail(new IllegalArgumentException(
                        "subStage can not move backwards:" + body.taskSubStage));
                return true;
            }
        }

        if (body.taskInfo.failure != null) {
            logWarning("Referer %s is patching us to failure: %s",
                    patch.getReferer(), Utils.toJsonHtml(body.taskInfo.failure));

            currentState.taskInfo.failure = body.taskInfo.failure;
            currentState.taskInfo.stage = body.taskInfo.stage;
            currentState.taskSubStage = SubStage.FAILED;
            return false;
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
        authState.customProperties.put(CUSTOM_PROP_ENPOINT_TYPE, state.endpointType);

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
        cd.customProperties.put(CUSTOM_PROP_ENPOINT_TYPE, state.endpointType);

        return cd;
    }

    private ComputeState configureCompute(EndpointState state) {
        ComputeState computeHost = new ComputeState();
        computeHost.id = UUID.randomUUID().toString();
        computeHost.name = state.name;
        computeHost.tenantLinks = state.tenantLinks;
        computeHost.customProperties = new HashMap<>();
        if (state.customProperties != null) {
            computeHost.customProperties.putAll(state.customProperties);
        }
        computeHost.customProperties.put(CUSTOM_PROP_ENPOINT_TYPE, state.endpointType);
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
        if (!TaskUtils.isFailedOrCancelledTask(state)) {
            state.taskInfo.stage = TaskStage.FINISHED;
            state.taskSubStage = completeSubStage;
            sendSelfPatch(state);
        }
    }
}
