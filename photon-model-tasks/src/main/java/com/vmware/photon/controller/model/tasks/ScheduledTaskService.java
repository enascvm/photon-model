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

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.constants.ReleaseConstants;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.TaskService;

/**
 * Task service to invoke other tasks on a schedule. The interval
 * to invoke the tasks is controlled by the maintenance interval
 * set for the service.
 */
public class ScheduledTaskService extends TaskService<ScheduledTaskService.ScheduledTaskState> {
    public static final String FACTORY_LINK = UriPaths.PROVISIONING + "/scheduled-tasks";
    public static final String INVOCATION_COUNT = "invocationCount";

    public static class ScheduledTaskState
            extends com.vmware.xenon.services.common.TaskService.TaskServiceState {
        /**
         * Link to the service factory
         */
        public String factoryLink;

        /**
         * JSON payload to be used for creating the service instance
         */
        public String initialStateJson;

        /**
         * Interval for task execution
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Long intervalMicros;

        @Documentation(description = "The user in whose context the task will be executed")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        @Since(ReleaseConstants.RELEASE_VERSION_0_5_1)
        public String userLink;

        /**
         * A list of tenant links which can access this task.
         */
        public List<String> tenantLinks;

        /**
         * Custom properties associated with the task
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @PropertyOptions(indexing = { PropertyIndexingOption.EXPAND,
                PropertyIndexingOption.FIXED_ITEM_NAME })
        public Map<String, String> customProperties;

        /**
         * delay before kicking off the task
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Long delayMicros;

        /**
         * Flag to disable a scheduled task
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_10)
        public Boolean enabled = Boolean.TRUE;

        /**
         * Whether to execute the task immediately on creation even if {@link #delayMicros} is
         * given. This has no effect on periodic executions.
         */
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_45)
        public Boolean noDelayOnInitialExecution;

        /**
         * Records the time for last successful execution for scheduled task.
         */
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_49_2)
        public Long expectedNextMaintenanceTimeMicros;
    }

    public ScheduledTaskService() {
        super(ScheduledTaskState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.toggleOption(ServiceOption.PERIODIC_MAINTENANCE, true);
    }

    @Override
    public void handleStart(Operation start) {
        if (!ServiceHost.isServiceCreate(start)) {
            // Skip if this is a restart operation, but make sure we set the maintenanceInterval.
            if (start.hasBody()) {
                ScheduledTaskState state = getBody(start);
                this.setMaintenanceIntervalMicros(state.intervalMicros);
            }
            setStat(INVOCATION_COUNT, 0);
            start.complete();
            return;
        }
        try {
            if (!start.hasBody()) {
                start.fail(new IllegalArgumentException("body is required"));
                return;
            }
            ScheduledTaskState state = getBody(start);
            if (state.factoryLink == null) {
                throw new IllegalArgumentException("factoryLink cannot be null");
            }
            if (state.initialStateJson == null) {
                throw new IllegalArgumentException("initialStateJson cannot be null");
            }
            if (state.intervalMicros != null) {
                this.setMaintenanceIntervalMicros(state.intervalMicros);
            }
            state.delayMicros = state.delayMicros != null ? state.delayMicros
                    : new Random().longs(1, 0, state.intervalMicros).findFirst().getAsLong();
            invokeTask(state,
                    !start.hasPragmaDirective(Operation.PRAGMA_DIRECTIVE_FROM_MIGRATION_TASK));
            start.complete();
        } catch (Throwable e) {
            start.fail(e);
        }
    }

    @Override
    public void handlePeriodicMaintenance(Operation maintenanceOp) {
        if (getProcessingStage() != ProcessingStage.AVAILABLE) {
            logFine(() -> String.format("Skipping maintenance since service is not available: %s ",
                    getUri()));
            return;
        }
        sendRequest(Operation.createGet(getUri())
                .setCompletion((getOp, getEx) -> {
                    if (getEx != null) {
                        maintenanceOp.fail(getEx);
                        return;
                    }
                    ScheduledTaskState state = getOp.getBody(ScheduledTaskState.class);
                    invokeTask(state, false);
                    maintenanceOp.complete();
                }));
    }

    @Override
    public void handlePatch(Operation patch) {
        try {
            ScheduledTaskState patchBody = getBody(patch);
            Utils.validateState(getStateDescription(), patchBody);
            ScheduledTaskState currentState = getState(patch);
            boolean hasStateChanged = Utils.mergeWithState(getStateDescription(),
                    currentState, patchBody);

            if (patchBody.expectedNextMaintenanceTimeMicros != null) {
                currentState.expectedNextMaintenanceTimeMicros = patchBody.expectedNextMaintenanceTimeMicros;
                hasStateChanged = true;
            }

            if (!hasStateChanged) {
                patch.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
            } else {
                patch.setBody(currentState);
            }
            patch.complete();
        } catch (Throwable e) {
            patch.fail(e);
        }
    }

    private void invokeTask(ScheduledTaskState state, boolean isInitial) {
        if (!state.enabled) {
            return;
        }
        logInfo("Invoke request received for schedule task: %s", state.documentSelfLink);
        if (state.expectedNextMaintenanceTimeMicros != null) {
            // setting permissible time to be 1/10th of interval time.
            Long permissibleInterval = state.intervalMicros / 10;
            Long currentTimeMicros = Utils.getNowMicrosUtc();

            if (state.expectedNextMaintenanceTimeMicros - permissibleInterval > currentTimeMicros) {
                logWarning(() -> String
                        .format("Recently finished previous run, will skip current run for scheduled task: %s",
                                state.documentSelfLink));
                return;
            }
        }

        // determine the delay
        final long delayMicros;
        if (isInitial && state.noDelayOnInitialExecution == Boolean.TRUE) {
            delayMicros = 0;
        } else {
            delayMicros = state.delayMicros;
        }

        // set the time for next schedule run
        state.expectedNextMaintenanceTimeMicros = Utils.fromNowMicrosUtc(state.intervalMicros);
        Operation.createPatch(this, state.documentSelfLink)
                .setBody(state)
                .setCompletion((operation, exception) -> {
                    if (exception == null) {
                        logInfo(() -> String
                                .format("Scheduled task %s successfully updated with next maintenance time: %d",
                                        state.documentSelfLink, state.expectedNextMaintenanceTimeMicros));
                    } else {
                        logWarning(
                                () -> String.format("Scheduled task update"
                                                + " failed: %s", exception.getMessage()));
                    }
                }).sendWith(this);

        getHost().schedule(() -> {
            logInfo("Invoking schedule task: %s", state.documentSelfLink);
            Operation op = Operation.createPost(this, state.factoryLink);

            if (getHost().isAuthorizationEnabled()) {
                if (state.userLink != null) {
                    try {
                        TaskUtils.assumeIdentity(this, op, state.userLink);
                    } catch (Exception e) {
                        logWarning(() -> String.format("Unhandled exception while assuming identity"
                                + " for %s: %s", state.userLink, e.getMessage()));
                        return;
                    }
                } else if (isInitial) {
                    // make sure system authz context is used for the initial execution so that
                    // it does not differ from periodic ones
                    setAuthorizationContext(op, getSystemAuthorizationContext());
                }
            }

            sendRequest(op.setBody(state.initialStateJson)
                    .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
                    .setCompletion(
                            (o, e) -> {
                                adjustStat(INVOCATION_COUNT, 1);
                                // if a task instance is already running, just log the fact
                                if (o.getStatusCode() == Operation.STATUS_CODE_NOT_MODIFIED) {
                                    logFine(() -> "Service instance already running.");
                                } else if (e != null) {
                                    logWarning(() -> String.format("Scheduled task invocation"
                                                    + " failed: %s", e.getMessage()));
                                    return;
                                }
                            }));
        }, delayMicros, TimeUnit.MICROSECONDS);
    }
}
