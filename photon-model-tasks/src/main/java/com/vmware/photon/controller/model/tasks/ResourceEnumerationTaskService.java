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

import static com.vmware.photon.controller.model.tasks.TaskUtils.getResourceExpirationMicros;
import static com.vmware.photon.controller.model.util.PhotonModelUriUtils.createInventoryUri;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.constants.ReleaseConstants;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.util.PhotonModelUtils;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceStateMapUpdateRequest;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskFactoryService;
import com.vmware.xenon.services.common.TaskService;

/**
 * Task used to Enumerate resources on a given compute host.
 */
public class ResourceEnumerationTaskService extends TaskService<ResourceEnumerationTaskService.ResourceEnumerationTaskState> {
    public static final String FACTORY_LINK = UriPaths.PROVISIONING + "/resource-enumeration-tasks";
    /**
     * key for the endpoint state custom property, holding resource enumeration task state for
     * the given endpoint
     */
    public static final String EP_CP_ENUMERATION_TASK_STATE = "enumerationTaskState";
    /**
     * key for the endpoint state custom property, showing the error message if the resource
     * enumeration task failed for the given endpoint
     */
    public static final String EP_CP_ENUMERATION_TASK_MESSAGE = "enumerationTaskMessage";
    /**
     * key for the endpoint state custom property, showing the error message id, if the resource
     * enumeration task failed for the given endpoint
     */
    public static final String EP_CP_ENUMERATION_TASK_MESSAGE_ID = "enumerationTaskMessageId";

    public static FactoryService createFactory() {
        TaskFactoryService fs =  new TaskFactoryService(ResourceEnumerationTaskState.class) {
            @Override
            public Service createServiceInstance() throws Throwable {
                return new ResourceEnumerationTaskService();
            }
        };
        fs.setPeerNodeSelectorPath(ServiceUriPaths.DEFAULT_1X_NODE_SELECTOR);
        return fs;
    }

    public static final long DEFAULT_TIMEOUT_MICROS = TimeUnit.MINUTES
            .toMicros(10);


    /**
     * Specifies when deleted remote resource documents should be expired from the system.
     * Defaults to EXPIRE_AFTER_ONE_MONTH, which sets expiry to micros after 31 days.
     */
    public enum ResourceExpirationPolicy {
        EXPIRE_NEVER, EXPIRE_AFTER_ONE_MONTH, EXPIRE_NOW
    }

    /**
     * This class defines the document state associated with a single
     * ResourceEnumerationTaskService instance.
     */
    public static class ResourceEnumerationTaskState extends TaskService.TaskServiceState {
        public static final String FIELD_NAME_RESOURCE_POOL_LINK = "resourcePoolLink";
        public static final String FIELD_NAME_PARENT_COMPUTE_LINK = "parentComputeLink";

        /**
         * Reference URI to the resource pool.
         */
        public String resourcePoolLink;

        /**
         * Reference URI to the parent Compute instance.
         */
        public String parentComputeLink;

        /**
         * Enumeration Action Start, stop, refresh.
         */
        public EnumerationAction enumerationAction;

        /**
         * URI reference to resource pool management site.
         */
        public URI adapterManagementReference;

        /**
         * A list of tenant links which can access this task.
         */
        public List<String> tenantLinks;

        /**
         * Task options
         */
        public EnumSet<TaskOption> options;

        /**
         * Link to the cloud account endpoint.
         */
        public String endpointLink;

        /**
         * Specifies when deleted resource documents should be expired.
         * Defaults to EXPIRE_NEVER.
         */
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_31)
        public ResourceExpirationPolicy expirationPolicy;
    }

    public ResourceEnumerationTaskService() {
        super(ResourceEnumerationTaskState.class);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handleStart(Operation start) {
        try {
            if (!start.hasBody()) {
                start.fail(new IllegalArgumentException("body is required"));
                return;
            }

            ResourceEnumerationTaskState state = getBody(start);
            validateState(state);
            start.setBody(state).complete();

            sendSelfPatch(state, TaskStage.STARTED, null);
        } catch (Throwable e) {
            start.fail(e);
        }
    }

    @Override
    public void handlePatch(Operation patch) {
        ResourceEnumerationTaskState body = getBody(patch);
        ResourceEnumerationTaskState currentState = getState(patch);

        if (!validateTransition(patch, currentState, body)) {
            return;
        }

        logFine(() -> String.format("Moving from %s to %s", currentState.taskInfo.stage.toString(),
                body.taskInfo.stage.toString()));

        currentState.taskInfo = body.taskInfo;
        // go-dcp will actuate the state. When the document is created, the
        // enumeration service in
        // go-dcp will be PATCH'ed with the enumeration request, then it will
        // PATCH back CREATED
        // followed by FINISHED or FAILED when complete
        switch (currentState.taskInfo.stage) {
        case CREATED:
            logFine(() -> "Created enum task");
            break;
        case STARTED:
            logFine(() -> "Started enum task");
            currentState.taskInfo.stage = TaskStage.STARTED;
            updateEndpointState(currentState).thenApply(v -> {
                sendEnumRequest(patch, currentState);
                return null;
            });
            break;
        case FINISHED:
            logFine(() -> "Task is complete");
            if (currentState.options.contains(TaskOption.SELF_DELETE_ON_COMPLETION)) {
                sendRequest(Operation
                        .createDelete(getUri()));
            }
            updateEndpointState(currentState);
            break;
        case FAILED:
        case CANCELLED:
            if (currentState.taskInfo.stage == TaskStage.CANCELLED) {
                logWarning(() -> "Task was cancelled");
            } else {
                logWarning(() -> String.format("Task failed: %s",
                        Utils.toJsonHtml(currentState.taskInfo.failure)));
            }
            if (currentState.options.contains(TaskOption.SELF_DELETE_ON_COMPLETION)) {
                sendRequest(Operation
                        .createDelete(getUri()));
            }
            updateEndpointState(currentState);
            break;
        default:
            logWarning(() -> "Unknown stage");
            break;
        }
        patch.setBody(currentState).complete();
    }

    private DeferredResult<Operation> updateEndpointState(
            ResourceEnumerationTaskState currentState) {
        TaskStage taskStage = currentState.taskInfo.stage;
        String stageName = taskStage.name();
        String message = null;
        String messageId = null;
        if (currentState.taskInfo.failure != null) {
            message = currentState.taskInfo.failure.message;
            messageId = currentState.taskInfo.failure.messageId;
        }
        Map<Object, Object> cpToAdd = new HashMap<>();
        cpToAdd.put(EP_CP_ENUMERATION_TASK_STATE, stageName);
        Collection<String> cpToRemove = new LinkedList<>();
        if (message != null && message.length() > 0) {
            cpToAdd.put(EP_CP_ENUMERATION_TASK_MESSAGE, message);
        } else {
            cpToRemove.add(EP_CP_ENUMERATION_TASK_MESSAGE);
        }
        if (messageId != null && messageId.length() > 0) {
            cpToAdd.put(EP_CP_ENUMERATION_TASK_MESSAGE_ID, messageId);
        } else {
            cpToRemove.add(EP_CP_ENUMERATION_TASK_MESSAGE_ID);
        }

        Map<String, Map<Object, Object>> entriesToAdd =
                Collections.singletonMap(
                        ResourceState.FIELD_NAME_CUSTOM_PROPERTIES, cpToAdd);
        Map<String, Collection<Object>> keysToRemove = Collections.singletonMap(
                EndpointState.FIELD_NAME_CUSTOM_PROPERTIES, new HashSet<>(cpToRemove));

        ServiceStateMapUpdateRequest mapUpdateRequest = ServiceStateMapUpdateRequest.create(
                entriesToAdd, keysToRemove
        );
        return sendWithDeferredResult(
                Operation.createPatch(this, currentState.endpointLink)
                        .setBody(mapUpdateRequest)
        ).exceptionally(err -> {
            logWarning("Cannot patch custom properties of"
                            + " endpoint '%s' to stageName '%s'. Cause: %s",
                    currentState.endpointLink, stageName, Utils.toString(err));
            return null;
        });
    }

    @Override
    public void handlePut(Operation put) {
        PhotonModelUtils.handleIdempotentPut(this, put);
    }

    private void sendEnumRequest(Operation start, ResourceEnumerationTaskState state) {
        ComputeEnumerateResourceRequest req = new ComputeEnumerateResourceRequest();
        req.resourcePoolLink = state.resourcePoolLink;
        req.adapterManagementReference = state.adapterManagementReference;
        req.resourceReference = createInventoryUri(this.getHost(), state.parentComputeLink);
        req.endpointLinkReference = createInventoryUri(this.getHost(), state.endpointLink);
        req.enumerationAction = state.enumerationAction;
        req.taskReference = UriUtils.buildUri(getHost(),
                state.documentSelfLink);
        req.isMockRequest = state.options.contains(TaskOption.IS_MOCK);
        req.preserveMissing = state.options.contains(TaskOption.PRESERVE_MISSING_RESOUCES);
        req.endpointLink = state.endpointLink;
        req.deletedResourceExpirationMicros = getResourceExpirationMicros(state.expirationPolicy
                == null ? ResourceExpirationPolicy.EXPIRE_AFTER_ONE_MONTH : state.expirationPolicy);

        // Patch the enumerate service URI from the CHD
        CompletionHandler descriptionCompletion = (o, ex) -> {
            if (ex != null) {
                TaskUtils.sendFailurePatch(this, state, ex);
                start.fail(ex);
                return;
            }

            ComputeStateWithDescription csd = o
                    .getBody(ComputeStateWithDescription.class);

            if (csd.description.enumerationAdapterReference == null) {
                // no enumeration adapter associated with this resource, just patch completion
                sendSelfFinishedPatch(state);
                return;
            }
            sendRequest(Operation
                    .createPatch(csd.description.enumerationAdapterReference)
                    .setBody(req));
        };

        URI computeUri = UriUtils
                .extendUriWithQuery(
                        UriUtils.buildUri(this.getHost(), state.parentComputeLink),
                        UriUtils.URI_PARAM_ODATA_EXPAND,
                        Boolean.TRUE.toString());

        sendRequest(Operation.createGet(computeUri)
                .setCompletion(descriptionCompletion));
    }

    @Override
    protected boolean validateTransition(Operation patch,
            ResourceEnumerationTaskState currentTask, ResourceEnumerationTaskState patchBody) {
        boolean ok = super.validateTransition(patch, currentTask, patchBody);

        if (ok) {
            if (currentTask.taskInfo.stage == TaskStage.STARTED && patchBody.taskInfo.stage == TaskStage.STARTED) {
                patch.fail(new IllegalArgumentException("Cannot start task again"));
                return false;
            }
        }

        return ok;
    }

    public static void validateState(ResourceEnumerationTaskState state) {
        if (state.resourcePoolLink == null) {
            throw new IllegalArgumentException("resourcePoolLink is required.");
        }

        if (state.adapterManagementReference == null) {
            throw new IllegalArgumentException(
                    "adapterManagementReference is required.");
        }

        if (state.options == null) {
            state.options = EnumSet.noneOf(TaskOption.class);
        }

        if (state.taskInfo == null || state.taskInfo.stage == null) {
            state.taskInfo = new TaskState();
            state.taskInfo.stage = TaskState.TaskStage.CREATED;
        }

        if (state.documentExpirationTimeMicros == 0) {
            state.documentExpirationTimeMicros = Utils.getNowMicrosUtc()
                    + DEFAULT_TIMEOUT_MICROS;
        }
    }
}
