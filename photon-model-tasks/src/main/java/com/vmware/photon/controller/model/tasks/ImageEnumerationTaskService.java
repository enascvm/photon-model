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
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.LINKS;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.OPTIONAL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT;
import static com.vmware.xenon.common.UriUtils.buildUri;
import static com.vmware.xenon.common.UriUtils.buildUriPath;

import java.net.URI;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.UriPaths.AdapterTypePath;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapterapi.ImageEnumerateRequest;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryService;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryService.PhotonModelAdapterConfig;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.util.PhotonModelUtils;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskFactoryService;
import com.vmware.xenon.services.common.TaskService;

/**
 * Task used to enumerate images on a given end-point.
 */
public class ImageEnumerationTaskService
        extends TaskService<ImageEnumerationTaskService.ImageEnumerationTaskState> {

    public static final String FACTORY_LINK = UriPaths.PROVISIONING + "/image-enumeration-tasks";

    public static FactoryService createFactory() {
        TaskFactoryService fs = new TaskFactoryService(ImageEnumerationTaskState.class) {
            @Override
            public Service createServiceInstance() throws Throwable {
                return new ImageEnumerationTaskService();
            }
        };
        fs.setPeerNodeSelectorPath(ServiceUriPaths.DEFAULT_1X_NODE_SELECTOR);
        return fs;
    }

    /**
     * This class defines the task state associated with a single
     * {@link ImageEnumerationTaskService} instance.
     */
    public static class ImageEnumerationTaskState extends TaskService.TaskServiceState {

        @Documentation(description = "Link to the end-point for which the enumeration"
                + " should be triggered.")
        @PropertyOptions(usage = { REQUIRED, SINGLE_ASSIGNMENT, LINK }, indexing = STORE_ONLY)
        public String endpointLink;

        @Documentation(description = "The type of image enumeration: start, stop, refresh.")
        @PropertyOptions(usage = { OPTIONAL, SINGLE_ASSIGNMENT }, indexing = STORE_ONLY)
        public EnumerationAction enumerationAction;

        @Documentation(description = "List of tenants that can access this task.")
        @PropertyOptions(usage = { OPTIONAL, SINGLE_ASSIGNMENT, LINKS }, indexing = STORE_ONLY)
        public List<String> tenantLinks;

        @Documentation(description = "Options used to configure specific aspects of"
                + " this task execution.")
        @PropertyOptions(usage = OPTIONAL, indexing = STORE_ONLY)
        public EnumSet<TaskOption> options;
    }

    /**
     * Defaults to expire a task instance if not completed in 10 mins.
     */
    public static final long DEFAULT_EXPIRATION_MINUTES = 10;

    public ImageEnumerationTaskService() {
        super(ImageEnumerationTaskState.class);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handleStart(Operation startOp) {
        try {
            ImageEnumerationTaskState taskState = validateStartPost(startOp);
            if (taskState == null) {
                return;
            }

            initializeState(taskState, startOp);

            // Send completion to the caller (with a CREATED state)
            startOp.setBody(taskState).complete();

            // And then start internal state machine
            sendSelfPatch(taskState, TaskStage.STARTED, null);

        } catch (Throwable e) {
            startOp.fail(e);
        }
    }

    /**
     * Customize the validation logic that's part of initial {@code POST} creating the task service.
     *
     * @see #handleStart(Operation)
     */
    @Override
    protected ImageEnumerationTaskState validateStartPost(Operation startOp) {

        // NOTE: This MUST go first!
        // Delegate to default/parent validation
        ImageEnumerationTaskState taskState = super.validateStartPost(startOp);

        // Do annotation based validation
        if (taskState != null) {
            try {
                Utils.validateState(getStateDescription(), taskState);
            } catch (Throwable t) {
                startOp.fail(t);
                taskState = null;
            }
        }
        return taskState;
    }

    /**
     * Customize the initialization logic (set the task with default values) that's part of initial
     * {@code POST} creating the task service.
     *
     * @see #handleStart(Operation)
     */
    @Override
    protected void initializeState(ImageEnumerationTaskState startState, Operation startOp) {

        if (startState.options == null) {
            startState.options = EnumSet.noneOf(TaskOption.class);
        }

        if (startState.taskInfo == null || startState.taskInfo.stage == null) {
            startState.taskInfo = new TaskState();
            startState.taskInfo.stage = TaskState.TaskStage.CREATED;
        }

        if (startState.documentExpirationTimeMicros <= 0) {
            setExpiration(startState, DEFAULT_EXPIRATION_MINUTES, TimeUnit.MINUTES);
        }
    }

    @Override
    public void handlePut(Operation putOp) {
        PhotonModelUtils.handleIdempotentPut(this, putOp);
    }

    @Override
    public void handlePatch(Operation patchOp) {

        ImageEnumerationTaskState currentState = getState(patchOp);
        ImageEnumerationTaskState patchState = getBody(patchOp);

        try {
            if (!validateTransition(patchOp, currentState, patchState)) {
                return;
            }

            logFine(() -> String.format("Moving from %s to %s (referrer: %s)",
                    currentState.taskInfo.stage,
                    patchState.taskInfo.stage,
                    patchOp.getReferer().getPath()));

            updateState(currentState, patchState);
            patchOp.complete();

            switch (patchState.taskInfo.stage) {
            case STARTED:
                sendImageEnumerationAdapterRequest(currentState);
                break;
            case FINISHED:
                handleTaskCompleted(currentState);
                break;
            case FAILED:
            case CANCELLED:
                logSevere(() -> String.format("%s with [%s]",
                        currentState.taskInfo.stage,
                        currentState.failureMessage));
                handleTaskCompleted(currentState);
                break;
            default:
                logWarning(() -> String.format("unknown stage: %s", currentState.taskInfo.stage));
                break;
            }
        } catch (Throwable e) {
            if (TaskState.isInProgress(currentState.taskInfo)) {
                sendSelfFailurePatch(currentState, e.getMessage());
            }
        }
    }

    /**
     * Customize the validation transition logic that's part of {@code PATCH} request.
     */
    @Override
    protected boolean validateTransition(
            Operation patchOp,
            ImageEnumerationTaskState currentState,
            ImageEnumerationTaskState patchState) {

        boolean ok = super.validateTransition(patchOp, currentState, patchState);

        if (ok) {
            if (currentState.taskInfo.stage == TaskStage.STARTED
                    && patchState.taskInfo.stage == TaskStage.STARTED) {
                patchOp.fail(new IllegalArgumentException("Cannot start task again"));
                return false;
            }
        }

        return ok;
    }

    /**
     * Self delete this task if specified through {@link TaskOption#SELF_DELETE_ON_COMPLETION}
     * option.
     */
    private void handleTaskCompleted(ImageEnumerationTaskState taskState) {
        if (taskState.options.contains(TaskOption.SELF_DELETE_ON_COMPLETION)) {
            logFine(() -> "Self-delete upon " + taskState.taskInfo.stage + " stage");
            sendRequest(Operation.createDelete(getUri()));
        }
    }

    /**
     * Delegate image enumeration to the adapter.
     */
    private void sendImageEnumerationAdapterRequest(ImageEnumerationTaskState taskState) {

        DeferredResult.completed(taskState)

                // get the EndpointState ImageEnumerationTaskState#endpointLink
                .thenCompose(this::getEndpointState)
                .thenApply(endpointState -> {
                    logFine(() -> String.format("SUCCESS: getEndpointState [%s]",
                            endpointState.name));

                    return endpointState;
                })

                // get the 'image-enumeration' URI for passed end-point
                .thenCompose(this::getImageEnumerationAdapterReference)
                .thenApply(adapterRef -> {
                    logFine(() -> String.format("SUCCESS: getImageEnumerationAdapterReference [%s]",
                            adapterRef));

                    return adapterRef;
                })

                // call 'image-enumeration' adapter (if registered)
                .thenCompose(adapterRef -> callImageEnumerationAdapter(taskState, adapterRef))
                .thenApply(callAdapterOp -> {
                    logFine("SUCCESS: callImageEnumerationAdapter");

                    return callAdapterOp;
                })

                .whenComplete((op, exc) -> {
                    if (exc != null) {
                        // If any of above steps failed sendSelfFailurePatch
                        logFine(() -> String.format(
                                "FAILED: sendImageEnumerationAdapterRequest [%s]",
                                exc.getMessage()));

                        sendSelfFailurePatch(taskState, exc.getMessage());
                    } else {
                        logFine("SUCCESS: sendImageEnumerationAdapterRequest");
                    }
                });
    }

    /**
     * Get the {@link EndpointState} from {@link ImageEnumerationTaskState#endpointLink}.
     */
    private DeferredResult<EndpointState> getEndpointState(ImageEnumerationTaskState taskState) {

        Operation op = Operation.createGet(this, taskState.endpointLink);

        return sendWithDeferredResult(op, EndpointState.class);
    }

    /**
     * Go to {@link PhotonModelAdaptersRegistryService Service Registry} and get the
     * 'image-enumeration' URI for passed end-point, if registered.
     *
     * @see PhotonModelAdapterConfig
     * @see AdapterTypePath#IMAGE_ENUMERATION_ADAPTER
     */
    private DeferredResult<URI> getImageEnumerationAdapterReference(EndpointState endpointState) {

        // We use 'endpointType' (such as aws, azure) as AdapterConfig id!
        String uri = buildUriPath(
                PhotonModelAdaptersRegistryService.FACTORY_LINK, endpointState.endpointType);

        Operation getEndpointConfigOp = Operation.createGet(this, uri);

        return sendWithDeferredResult(getEndpointConfigOp, PhotonModelAdapterConfig.class)
                .thenApply(endpointConfig -> {
                    if (endpointConfig.adapterEndpoints == null) {
                        return (URI) null;
                    }

                    // Lookup the 'image-enumeration' URI for passed end-point
                    String uriStr = endpointConfig.adapterEndpoints.get(
                            AdapterTypePath.IMAGE_ENUMERATION_ADAPTER.key);

                    return uriStr == null || uriStr.isEmpty() ? (URI) null : URI.create(uriStr);
                });
    }

    /**
     * Call 'image-enumeration' adapter (if registered by the end-point).
     */
    private DeferredResult<Operation> callImageEnumerationAdapter(
            ImageEnumerationTaskState taskState, URI adapterRef) {

        if (adapterRef == null) {
            // No 'image-enumeration' URI registered for passed end-point
            return DeferredResult.completed(null);
        }

        // Create 'image-enumeration' adapter request
        ImageEnumerateRequest adapterReq = new ImageEnumerateRequest();

        // Set ImageEnumerateRequest specific params
        adapterReq.enumerationAction = taskState.enumerationAction;
        // Set generic ResourceRequest params
        adapterReq.resourceReference = buildUri(getHost(), taskState.endpointLink);
        adapterReq.taskReference = buildUri(getHost(), taskState.documentSelfLink);
        adapterReq.isMockRequest = taskState.options.contains(TaskOption.IS_MOCK);

        Operation callAdapterOp = Operation.createPatch(adapterRef).setBody(adapterReq);

        return sendWithDeferredResult(callAdapterOp);
    }

}
