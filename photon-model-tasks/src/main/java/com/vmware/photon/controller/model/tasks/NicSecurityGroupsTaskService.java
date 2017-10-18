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

import static com.vmware.photon.controller.model.util.PhotonModelUriUtils.createInventoryUri;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.EXPAND;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.FIXED_ITEM_NAME;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.LINK;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.OPTIONAL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapterapi.NicSecurityGroupsRequest;
import com.vmware.photon.controller.model.adapterapi.NicSecurityGroupsRequest.OperationRequestType;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.tasks.NicSecurityGroupsTaskService.NicSecurityGroupsTaskState;
import com.vmware.photon.controller.model.util.AssertUtil;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.TaskService;

/**
 * Assign or remove security groups to a network interface
 */
public class NicSecurityGroupsTaskService extends TaskService<NicSecurityGroupsTaskState> {
    public static final String FACTORY_LINK =
            UriPaths.TASKS + "/network-interface-security-groups-tasks";

    /**
     * Substages of the tasks.
     */
    public enum SubStage {
        CREATED, ADDING_SECURITY_GROUPS, REMOVING_SECURITY_GROUPS, FINISHED, FAILED
    }

    /**
     * Represents state of a network interface security groups task.
     */
    public static class NicSecurityGroupsTaskState
            extends TaskService.TaskServiceState {
        @UsageOption(option = REQUIRED)
        public OperationRequestType requestType;

        /**
         * The link to the network interface that is being assigned/removed to security groups
         */
        @PropertyOptions(usage = { REQUIRED, LINK })
        public String networkInterfaceLink;

        /**
         * Links to security groups to be assigned to network interface
         */
        @UsageOption(option = REQUIRED)
        public List<String> securityGroupLinks;

        /**
         * Link to the network interface security group adapter.
         */
        @UsageOption(option = REQUIRED)
        public URI adapterReference;

        /**
         * Tracks the sub stage (creating network or security group). Set by the
         * run-time.
         */
        public SubStage taskSubStage;

        /**
         * For testing. If set, the request will not actuate any computes
         * directly but will patch back success.
         */
        public boolean isMockRequest = false;

        /**
         * A list of tenant links which can access this task.
         */
        public List<String> tenantLinks;

        /**
         * Custom properties associated with the task
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @PropertyOptions(usage = { OPTIONAL }, indexing = { EXPAND, FIXED_ITEM_NAME })
        public Map<String, String> customProperties;

        /**
         * A callback to the initiating task.
         */
        public ServiceTaskCallback<?> serviceTaskCallback;
    }

    public NicSecurityGroupsTaskService() {
        super(NicSecurityGroupsTaskState.class);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handleStart(Operation start) {
        if (!start.hasBody()) {
            start.fail(new IllegalArgumentException("body is required"));
            return;
        }

        NicSecurityGroupsTaskState state = start
                .getBody(NicSecurityGroupsTaskState.class);
        try {
            Utils.validateState(getStateDescription(), state);
        } catch (Exception e) {
            start.fail(e);
            return;
        }

        validateSecurityGroupsEndpoint(state)
                .whenComplete((ignore, e) -> {
                    if (e != null) {
                        start.fail(e);
                        return;
                    }
                    state.taskInfo = new TaskState();
                    state.taskInfo.stage = TaskState.TaskStage.CREATED;
                    state.taskSubStage = SubStage.CREATED;
                    start.complete();

                    // start the task
                    sendSelfPatch(TaskState.TaskStage.CREATED, null);
                });
    }

    @Override
    public void handlePatch(Operation patch) {
        if (!patch.hasBody()) {
            patch.fail(new IllegalArgumentException("body is required"));
            return;
        }

        NicSecurityGroupsTaskState currState = getState(patch);
        NicSecurityGroupsTaskState patchState = patch
                .getBody(NicSecurityGroupsTaskState.class);

        if (TaskState.isFailed(patchState.taskInfo)) {
            currState.taskInfo = patchState.taskInfo;
        }

        switch (patchState.taskInfo.stage) {
        case CREATED:
            currState.taskSubStage = nextStage(currState);

            handleSubStages(currState);
            logInfo(() -> String.format("%s security groups for %s started",
                    currState.requestType.toString(), currState.networkInterfaceLink));
            break;

        case STARTED:
            currState.taskInfo.stage = TaskState.TaskStage.STARTED;
            break;
        case FINISHED:
            SubStage nextStage = nextStage(currState);
            if (nextStage == SubStage.FINISHED) {
                currState.taskInfo.stage = TaskState.TaskStage.FINISHED;
                logInfo(() -> "Task is complete");
                ServiceTaskCallback.sendResponse(currState.serviceTaskCallback, this, currState);
            } else {
                sendSelfPatch(TaskState.TaskStage.CREATED, null);
            }
            break;
        case FAILED:
            logWarning(() -> String.format("Task failed with %s",
                    Utils.toJsonHtml(currState.taskInfo.failure)));
            ServiceTaskCallback.sendResponse(currState.serviceTaskCallback, this, currState);
            break;
        case CANCELLED:
            break;
        default:
            break;
        }

        patch.complete();
    }

    /**
     * Validate that all security groups belong to the same endpoint
     */
    private DeferredResult<Void> validateSecurityGroupsEndpoint(NicSecurityGroupsTaskState state) {
        QueryTask.Query query = QueryTask.Query.Builder.create()
                .addKindFieldClause(SecurityGroupState.class)
                .addInClause(ServiceDocument.FIELD_NAME_SELF_LINK, state.securityGroupLinks)
                .build();
        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .setQuery(query)
                .addSelectTerm(SecurityGroupState.FIELD_NAME_ENDPOINT_LINK)
                .addOption(QueryTask.QuerySpecification.QueryOption.EXPAND_SELECTED_FIELDS)
                .build();

        return QueryUtils.startInventoryQueryTask(this, queryTask)
                .thenApply(qrt -> {
                    AssertUtil.assertTrue(qrt != null && qrt.results.documentCount > 0,
                            String.format("Could not find security groups with links %s",
                                    state.securityGroupLinks));

                    Set<String> endpointLinks = qrt.results.documents.values()
                            .stream()
                            .map(o -> Utils.fromJson(o, SecurityGroupState.class).endpointLink)
                            .collect(Collectors.toSet());

                    // we only support security groups from the same endpoint for the same request
                    if (endpointLinks.size() != 1) {
                        throw new IllegalArgumentException(
                                "All security groups must belong to the same endpoint.");
                    }
                    if (endpointLinks.iterator().next() == null) {
                        throw new IllegalArgumentException(
                                "All security groups must have endpoint link set.");
                    }
                    return null;
                });
    }

    private SubStage nextStage(NicSecurityGroupsTaskState state) {
        return state.requestType == OperationRequestType.ADD
                ? nextSubStageOnCreate(state.taskSubStage)
                : nextSubstageOnDelete(state.taskSubStage);
    }

    private SubStage nextSubStageOnCreate(SubStage currStage) {
        if (currStage == SubStage.CREATED) {
            return SubStage.ADDING_SECURITY_GROUPS;
        } else if (currStage == SubStage.ADDING_SECURITY_GROUPS) {
            return SubStage.FINISHED;
        } else {
            return SubStage.values()[currStage.ordinal() + 1];
        }
    }

    private SubStage nextSubstageOnDelete(SubStage currStage) {
        if (currStage == SubStage.CREATED) {
            return SubStage.REMOVING_SECURITY_GROUPS;
        } else if (currStage == SubStage.REMOVING_SECURITY_GROUPS) {
            return SubStage.FINISHED;
        } else {
            return SubStage.values()[currStage.ordinal() + 1];
        }
    }

    private void handleSubStages(NicSecurityGroupsTaskState currState) {
        switch (currState.taskSubStage) {
        case ADDING_SECURITY_GROUPS:
        case REMOVING_SECURITY_GROUPS:
            patchAdapter(currState);
            break;
        case FINISHED:
            sendSelfPatch(TaskState.TaskStage.FINISHED, null);
            break;
        case FAILED:
            break;
        default:
            break;
        }
    }

    private NicSecurityGroupsRequest toReq(
            NicSecurityGroupsTaskState taskState,
            SecurityGroupState securityGroupState) {
        NicSecurityGroupsRequest req = new NicSecurityGroupsRequest();
        req.requestType = taskState.requestType;
        req.resourceReference = createInventoryUri(this.getHost(), taskState.networkInterfaceLink);
        req.securityGroupLinks = taskState.securityGroupLinks;
        req.authCredentialsLink = securityGroupState.authCredentialsLink;
        req.taskReference = this.getUri();
        req.isMockRequest = taskState.isMockRequest;
        req.customProperties = taskState.customProperties;

        return req;
    }

    private void patchAdapter(NicSecurityGroupsTaskState taskState) {
        // use the first security group
        sendRequest(Operation.createGet(
                UriUtils.buildUri(this.getHost(), taskState.securityGroupLinks.get(0)))
                .setCompletion((o, e) -> {
                    if (e != null) {
                        sendSelfPatch(TaskState.TaskStage.FAILED, e);
                        return;
                    }
                    SecurityGroupState securityGroupState = o.getBody(SecurityGroupState.class);
                    NicSecurityGroupsRequest req = toReq(taskState,
                            securityGroupState);

                    sendRequest(Operation
                            .createPatch(
                                    taskState.adapterReference)
                            .setBody(req)
                            .setCompletion(
                                    (oo, ee) -> {
                                        if (ee != null) {
                                            sendSelfPatch(TaskState.TaskStage.FAILED, ee);
                                            return;
                                        }
                                    }));
                }));
    }


    private void sendSelfPatch(TaskState.TaskStage stage, Throwable e) {
        NicSecurityGroupsTaskState body = new NicSecurityGroupsTaskState();
        body.taskInfo = new TaskState();
        if (e == null) {
            body.taskInfo.stage = stage;
        } else {
            body.taskInfo.stage = TaskState.TaskStage.FAILED;
            body.taskInfo.failure = Utils.toServiceErrorResponse(e);
            logWarning(() -> String.format("Patching to failed: %s", Utils.toString(e)));
        }

        sendSelfPatch(body);
    }
}
