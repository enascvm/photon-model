/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.discovery.endpoints;

import static com.vmware.photon.controller.discovery.common.utils.DataCollectionTaskHelper.getOptionalStatsAdapterTaskQuery;
import static com.vmware.photon.controller.discovery.onboarding.OnboardingUtils.assumeIdentity;
import static com.vmware.photon.controller.discovery.onboarding.OnboardingUtils.getAutomationUserLink;
import static com.vmware.photon.controller.discovery.onboarding.OnboardingUtils.subscribeToNotifications;
import static com.vmware.photon.controller.model.UriPaths.OPTIONAL_ADAPTER_SCHEDULER;
import static com.vmware.photon.controller.model.UriPaths.SERVICE_CONFIG_RULES;
import static com.vmware.photon.controller.model.UriPaths.SERVICE_QUERY_CONFIG_RULES;
import static com.vmware.xenon.common.UriUtils.URI_PARAM_ODATA_EXPAND;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.vmware.photon.controller.discovery.common.services.rules.ConfigContext;
import com.vmware.photon.controller.discovery.common.services.rules.ConfigurationRuleService;
import com.vmware.photon.controller.discovery.common.services.rules.ConfigurationRuleService.ConfigurationRuleState;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.tasks.ScheduledTaskService;
import com.vmware.photon.controller.model.tasks.ScheduledTaskService.ScheduledTaskState;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.photon.controller.model.tasks.monitoring.StatsCollectionTaskService;
import com.vmware.photon.controller.model.tasks.monitoring.StatsCollectionTaskService.StatsCollectionTaskState;
import com.vmware.photon.controller.model.tasks.monitoring.StatsUtil;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceHost.ServiceNotFoundException;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.SystemUserService;

public class OptionalAdapterSchedulingService extends StatelessService {

    public static String SELF_LINK = OPTIONAL_ADAPTER_SCHEDULER;

    public static final long DEFAULT_OPTIONAL_ADAPTER_INTERVAL_SEC = TimeUnit.MINUTES
            .toSeconds(120);

    public static final String ADAPTER_FEATURE_FLAG_PREFIX = "isAdapterEnabled";

    public static final String ADAPTER_TIME_INTERVAL_PROPERTY_PREFIX = "adapterTimeIntervalSeconds";

    private static class AdapterDetails {
        private String adapterReference;
        private String featureFlagKey;
        private String adapterIntervalPropertyKey;
    }

    public enum RequestType {
        SCHEDULE, UNSCHEDULE, TRIGGER_IMMEDIATE;
    }

    public static class OptionalAdapterSchedulingRequest {

        public RequestType requestType;

        public EndpointState endpoint;

        public String resourcePoolLink;
    }

    @Override
    public void handleStart(Operation startPost) {
        subscribeToRelevantNotifications();
        super.handleStart(startPost);
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        OptionalAdapterSchedulingRequest request = op
                .getBody(OptionalAdapterSchedulingRequest.class);
        switch (request.requestType) {
        case SCHEDULE:
            if (request.endpoint == null) {
                op.fail(new IllegalArgumentException("body is required"));
                return;
            }
            scheduleAdaptersForEndpoints(Collections.singletonList(request.endpoint))
                    .whenComplete((result, e) -> {
                        if (e != null) {
                            op.fail(e);
                            return;
                        }
                        op.complete();
                    });
            break;
        case TRIGGER_IMMEDIATE:
            triggerAdaptersImmediateForEndpoint(request.endpoint, op);
            break;
        case UNSCHEDULE:
            unscheduleAllAdaptersForResourcePool(request.resourcePoolLink, op);
            break;
        default:
            this.logWarning("Unsupported request type: %s.", request.requestType);
            op.complete();
        }
    }

    private void subscribeToRelevantNotifications() {
        String[] servicesToWaitFor = new String[] { ConfigurationRuleService.FACTORY_LINK };
        AtomicInteger completionCount = new AtomicInteger(0);
        getHost().registerForServiceAvailability((o, e) -> {
            if (e != null) {
                logSevere(e);
                return;
            }
            if (completionCount.incrementAndGet() == servicesToWaitFor.length) {
                subscribeToAdapterFeatureFlagNotifications();
                logInfo("Subscribed for relevant notifications.");
            }
        }, true, servicesToWaitFor);
    }

    /**
     * Method subscribes to updates on the configuration rules representing optional adapter
     * scheduling.
     */
    protected void subscribeToAdapterFeatureFlagNotifications() {

        // Subscribe to creation of rules
        Consumer<Operation> configRuleCreationConsumer = (notificationOp) -> {
            notificationOp.complete();
            ConfigurationRuleState configRule = notificationOp
                    .getBody(ConfigurationRuleState.class);

            // Check if the created config rule corresponds to any feature flag and
            // subscribe to the updates on the individual feature flag.
            AdapterDetails adapterDetails = getAdapterDetailsFromConfigRule(configRule.id);

            if (adapterDetails != null) {
                String adapterFeatureFlagLink =
                        ConfigurationRuleService.FACTORY_LINK + "/" + adapterDetails.featureFlagKey;
                Consumer<Operation> adapterFeatureFlagUpdateConsumer = (adapterFeatureFlagUpdateOp) -> {
                    logInfo("Adapter feature flag updated with %s action",
                            adapterFeatureFlagUpdateOp.getAction());
                    adapterFeatureFlagUpdateOp.complete();

                    if (notificationOp.getAuthorizationContext().isSystemUser()) {
                        logInfo("Skipping adapter scheduling under system context.");
                        return;
                    }
                    scheduleAdaptersForAllEndpoints();
                };
                subscribeToNotifications(getHost(), adapterFeatureFlagUpdateConsumer,
                        (e) -> logWarning(Utils.toString(e)),
                        adapterFeatureFlagLink);
            }
        };
        subscribeToNotifications(getHost(), configRuleCreationConsumer,
                (e) -> logWarning(Utils.toString(e)),
                ConfigurationRuleService.FACTORY_LINK);
    }

    private AdapterDetails getAdapterDetailsFromConfigRule(String configRuleId) {
        if ((configRuleId == null) || !configRuleId.startsWith(ADAPTER_FEATURE_FLAG_PREFIX)) {
            return null;
        }
        AdapterDetails adapterDetails = new AdapterDetails();
        adapterDetails.featureFlagKey = configRuleId;
        String adapterName = configRuleId.replaceFirst(ADAPTER_FEATURE_FLAG_PREFIX, "");
        adapterDetails.adapterReference = adapterName.replaceAll("\\.", "/");
        adapterDetails.adapterIntervalPropertyKey =
                ADAPTER_TIME_INTERVAL_PROPERTY_PREFIX + adapterName;
        return adapterDetails;
    }

    /**
     * Method queries the feature flags and schedules/ unschedules optional adapters for all
     * endpoints.
     */
    private void scheduleAdaptersForAllEndpoints() {
        URI endPointFactoryUri = UriUtils.buildUri(getHost(), EndpointService.FACTORY_LINK);
        endPointFactoryUri = UriUtils
                .extendUriWithQuery(endPointFactoryUri, UriUtils.URI_PARAM_ODATA_EXPAND,
                        Boolean.TRUE.toString());
        Operation.createGet(endPointFactoryUri).setCompletion((o, e) -> {
            if (e != null) {
                logSevere(e);
                return;
            }
            ServiceDocumentQueryResult result = o.getBody(ServiceDocumentQueryResult.class);
            List<EndpointState> endPointStates = result.documents.values().stream()
                    .map(obj -> Utils.fromJson(obj, EndpointState.class))
                    .collect(Collectors.toList());
            scheduleAdaptersForEndpoints(endPointStates);
        }).sendWith(this);
    }

    /**
     * Method queries the feature flags and schedules/ unschedules optional adapters for the
     * specified endpoints.
     */
    private DeferredResult<Void> scheduleAdaptersForEndpoints(List<EndpointState> endPoints) {
        DeferredResult<Void> deferredResult = new DeferredResult<>();

        Operation.createGet(getHost(), SERVICE_QUERY_CONFIG_RULES)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere(e);
                        deferredResult.fail(e);
                        return;
                    }
                    // Get the values of all feature flags
                    ConfigContext configContext = o.getBody(ConfigContext.class);

                    // Filter out applicable rules, so that we can appropriate mark the operation
                    // complete or failed.
                    Map<String, Object> applicableRules = new HashMap<>();
                    for (String ruleId : configContext.rules.keySet()) {
                        AdapterDetails adapterDetails = getAdapterDetailsFromConfigRule(ruleId);

                        if (adapterDetails == null) {
                            continue;
                        }

                        applicableRules.put(ruleId, configContext.rules.get(ruleId));
                    }

                    AtomicInteger count = new AtomicInteger(applicableRules.size());
                    List<Throwable> exs = new ArrayList<>();

                    if (count.get() == 0) {
                        deferredResult.complete(null);
                        return;
                    }

                    for (Map.Entry<String, Object> ruleEntry : applicableRules.entrySet()) {
                        String ruleId = ruleEntry.getKey();
                        Object value = ruleEntry.getValue();
                        AdapterDetails adapterDetails = getAdapterDetailsFromConfigRule(ruleId);

                        DeferredResult<Void> deferred;

                        if (value != null && value instanceof Boolean && value.equals(Boolean.TRUE)) {
                            deferred = scheduleSpecifiedAdapterForEndpoints(adapterDetails,
                                    endPoints);
                        } else {
                            deferred = unscheduleAdapterForAllEndpoints(adapterDetails);
                        }

                        deferred.whenComplete(
                                (result, ex) -> handleCallbacks(deferredResult, count, exs, ex));
                    }
                }).sendWith(this);
        return deferredResult;
    }

    private DeferredResult<Void> scheduleSpecifiedAdapterForEndpoints(AdapterDetails adapterDetails,
            List<EndpointState> endPoints) {
        DeferredResult<Void> deferredResult = new DeferredResult<>();

        AtomicInteger count = new AtomicInteger(endPoints.size());
        List<Throwable> exs = new ArrayList<>();

        if (count.get() == 0) {
            deferredResult.complete(null);
            return deferredResult;
        }

        for (EndpointState endPoint : endPoints) {
            String taskLink = createAdapterRpLink(adapterDetails, endPoint.resourcePoolLink);
            String taskUri = UriUtils.buildUriPath(ScheduledTaskService.FACTORY_LINK, taskLink);

            // Check if the scheduled task exists already or not.
            Query query = Query.Builder.create()
                    .addKindFieldClause(ScheduledTaskState.class)
                    .addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK, taskUri)
                    .build();

            QueryTask queryTask = QueryTask.Builder.createDirectTask()
                    .setQuery(query)
                    .build();

            OperationContext opCtx = OperationContext.getOperationContext();
            Operation op = Operation.createPost(getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS)
                    .setBody(queryTask)
                    .setConnectionSharing(true)
                    .setCompletion((o, e) -> {
                        OperationContext.restoreOperationContext(opCtx);
                        if (e != null) {
                            exs.add(e);
                            if (count.decrementAndGet() == 0) {
                                deferredResult.fail(e);
                            }
                            return;
                        }

                        if (o != null) {
                            QueryTask task = o.getBody(QueryTask.class);
                            if (!task.results.documentLinks.isEmpty()) {
                                logInfo("Adapter already scheduled for %s", taskUri);
                                // This means that the scheduled task exists already.
                                if (count.decrementAndGet() == 0) {
                                    if (!exs.isEmpty()) {
                                        deferredResult.fail(exs.iterator().next());
                                        return;
                                    }
                                    deferredResult.complete(null);
                                }
                                return;
                            }
                        }

                        scheduleAdapterForResourcePool(
                                adapterDetails,
                                endPoint.resourcePoolLink,
                                endPoint.tenantLinks)
                                .whenComplete((result, ex) ->
                                        handleCallbacks(deferredResult, count, exs, ex));
                    });
            invokeAsAutomationUser(op);
        }
        return deferredResult;
    }

    private void invokeAsAutomationUser(Operation op) {
        try {
            assumeIdentity(this, op, getAutomationUserLink());
            this.sendRequest(op);
        } catch (Exception e) {
            logWarning("Could not invoke operation '%s' with automation user.", op.getUri());
            op.fail(e);
        }
    }

    /**
     * Method deletes the schedules all adpaters of the specified adapter type.
     *
     * @param adapterDetails
     */
    private DeferredResult<Void> unscheduleAdapterForAllEndpoints(AdapterDetails adapterDetails) {
        DeferredResult<Void> deferredResult = new DeferredResult<>();
        queryScheduledTasksForAdapter(adapterDetails, (taskLinks) -> {

            AtomicInteger count = new AtomicInteger(taskLinks.size());
            List<Throwable> exs = new ArrayList<>();

            if (count.get() == 0) {
                deferredResult.complete(null);
                return;
            }

            for (String taskLink : taskLinks) {
                deleteTask(taskLink)
                        .whenComplete((aVoid, e) -> handleCallbacks(deferredResult, count, exs, e));
            }
        });
        return deferredResult;
    }

    private DeferredResult<Void> deleteTask(String taskLink) {
        DeferredResult<Void> deferredResult = new DeferredResult<>();

        // Expire the task with current timestamp. We want the document to be hard deleted.
        ScheduledTaskState scheduledTaskState = new ScheduledTaskState();
        scheduledTaskState.documentExpirationTimeMicros = Utils.getNowMicrosUtc();
        Operation.createPut(getHost(), taskLink)
                .setBody(scheduledTaskState)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        if (e instanceof ServiceNotFoundException) {
                            logWarning(
                                    "Could not unschedule adapter '%s', because it doesn't exist.",
                                    taskLink);
                            deferredResult.complete(null);
                            return;
                        }
                        logSevere(e);
                        deferredResult.fail(e);
                        return;
                    }
                    logInfo("Unscheduled adapter '%s'.", taskLink);
                    deferredResult.complete(null);
                }).sendWith(this);
        return deferredResult;
    }

    /**
     * Method schedules adapter of the specified type on the specified resource pool. The scheduled
     * adapter is linked with specified tenant links.
     *
     * @param adapterDetails
     * @param rpLink
     * @param tenantLinks
     */
    private DeferredResult<Void> scheduleAdapterForResourcePool(AdapterDetails adapterDetails,
            String rpLink, List<String> tenantLinks) {
        DeferredResult<Void> deferredResult = new DeferredResult<>();

        Long intervalSeconds = Long.getLong(adapterDetails.adapterIntervalPropertyKey,
                DEFAULT_OPTIONAL_ADAPTER_INTERVAL_SEC);

        StatsCollectionTaskState statCollectionState = createAdapterTaskState(adapterDetails,
                rpLink);

        ScheduledTaskState scheduledTaskState = new ScheduledTaskState();
        scheduledTaskState.factoryLink = StatsCollectionTaskService.FACTORY_LINK;
        scheduledTaskState.initialStateJson = Utils.toJson(statCollectionState);
        scheduledTaskState.intervalMicros = TimeUnit.SECONDS.toMicros(intervalSeconds);
        scheduledTaskState.documentSelfLink = createAdapterRpLink(adapterDetails, rpLink);
        scheduledTaskState.tenantLinks = tenantLinks;
        scheduledTaskState.userLink = SystemUserService.SELF_LINK;
        OperationContext opCtx = OperationContext.getOperationContext();
        Operation op = Operation.createPost(getHost(), ScheduledTaskService.FACTORY_LINK)
                .setBody(scheduledTaskState)
                .setCompletion((o, e) -> {
                    OperationContext.restoreOperationContext(opCtx);
                    if (e != null) {
                        logSevere(e);
                        deferredResult.fail(e);
                        return;
                    }
                    logInfo("Scheduled adapter '%s'", scheduledTaskState.documentSelfLink);
                    deferredResult.complete(null);
                });
        invokeAsAutomationUser(op);
        return deferredResult;
    }

    /**
     * Method queries scheduled tasks of the specified adapter and invokes the specified query
     * result consumer.
     *
     * @param adapterDetails
     * @param queryResultConsumer
     */
    private void queryScheduledTasksForAdapter(AdapterDetails adapterDetails,
            Consumer<List<String>> queryResultConsumer) {

        Query query = Query.Builder.create()
                .addKindFieldClause(ScheduledTaskState.class)
                .addFieldClause(ScheduledTaskState.FIELD_NAME_SELF_LINK,
                        ScheduledTaskService.FACTORY_LINK + "/" + adapterDetails.featureFlagKey
                                .replaceAll(ADAPTER_FEATURE_FLAG_PREFIX + ".", ""),
                        QueryTask.QueryTerm.MatchType.PREFIX, Query.Occurance.MUST_OCCUR)
                .build();

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .setQuery(query)
                .build();
        queryTask.documentSelfLink = UUID.randomUUID().toString();
        Operation op = Operation.createPost(getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS)
                .setBody(queryTask)
                .setConnectionSharing(true)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere(e);
                        return;
                    }
                    QueryTask responseTask = o.getBody(QueryTask.class);
                    queryResultConsumer.accept(responseTask.results.documentLinks);
                });

        setAuthorizationContext(op, getSystemAuthorizationContext());
        sendRequest(op);
    }

    private void triggerAdaptersImmediateForEndpoint(EndpointState endpointState, Operation op) {
        queryEnabledAdapterDetails().thenAccept(allAdapterDetails -> {

            OperationContext opCtx = OperationContext.getOperationContext();
            List<Operation> taskOps = new ArrayList<>();
            allAdapterDetails.forEach(adapterDetails -> {
                if (!adapterDetails.adapterReference.contains(endpointState.endpointType)) {
                    return;
                }
                StatsCollectionTaskState taskState = createAdapterTaskState(adapterDetails,
                        endpointState.resourcePoolLink);

                Operation taskOp = Operation
                        .createPost(getHost(), StatsCollectionTaskService.FACTORY_LINK)
                        .setBody(taskState);
                try {
                    assumeIdentity(this, taskOp, getAutomationUserLink());
                    taskOps.add(taskOp);
                } catch (Exception e) {
                    logWarning("Could not set auth context to automation user for task %s.",
                            taskState.documentSelfLink);
                    op.fail(e);
                }
            });

            if (taskOps.isEmpty()) {
                op.setBody(new ServiceDocumentQueryResult());
                op.complete();
                return;
            }

            OperationJoin.create(taskOps).setCompletion((ops, exps) -> {
                OperationContext.restoreOperationContext(opCtx);
                if ((exps != null) && !exps.isEmpty()) {
                    op.fail(exps.values().iterator().next());
                    return;
                }
                List<String> taskLinks = ops.values().stream()
                        .map(resultOp -> resultOp.getBody(ServiceDocument.class).documentSelfLink)
                        .collect(Collectors.toList());
                logInfo("Triggered immediate run of adapters [%s].", String.join(",", taskLinks));

                ServiceDocumentQueryResult result = new ServiceDocumentQueryResult();
                result.documentLinks = taskLinks;
                op.setBody(result);
                op.complete();
            }).sendWith(this);
        });
    }

    private void unscheduleAllAdaptersForResourcePool(String rpLink, Operation op) {
        op.complete();
        queryConfiguredAdapterDetails().thenAccept(allAdapterDetails -> {
            allAdapterDetails.forEach(adapterDetails -> {
                String taskLink = UriUtils.buildUriPath(ScheduledTaskService.FACTORY_LINK,
                        createAdapterRpLink(adapterDetails, rpLink));
                deleteTask(taskLink);
            });
        });
    }

    private CompletableFuture<List<AdapterDetails>> queryEnabledAdapterDetails() {
        CompletableFuture<List<AdapterDetails>> future = new CompletableFuture<>();
        Operation op = Operation.createGet(getHost(), SERVICE_QUERY_CONFIG_RULES)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere(e);
                        future.completeExceptionally(e);
                        return;
                    }
                    // Get the values of all feature flags
                    ConfigContext configContext = o.getBody(ConfigContext.class);

                    List<AdapterDetails> allAdapterDetails = new ArrayList<>();
                    for (Map.Entry<String, Object> ruleEntry : configContext.rules.entrySet()) {
                        String ruleId = ruleEntry.getKey();
                        Object value = ruleEntry.getValue();
                        AdapterDetails adapterDetails = getAdapterDetailsFromConfigRule(ruleId);
                        if (adapterDetails != null
                                && value != null
                                && value instanceof Boolean
                                && value.equals(Boolean.TRUE)) {
                            allAdapterDetails.add(adapterDetails);
                        }
                    }
                    future.complete(allAdapterDetails);
                });
        this.sendRequest(op);
        return future;
    }

    private CompletableFuture<List<AdapterDetails>> queryConfiguredAdapterDetails() {
        CompletableFuture<List<AdapterDetails>> future = new CompletableFuture<>();
        URI configRulesUri = UriUtils.buildUri(getHost(), SERVICE_CONFIG_RULES);
        configRulesUri = UriUtils
                .appendQueryParam(configRulesUri, URI_PARAM_ODATA_EXPAND, Boolean.TRUE.toString());
        Operation op = Operation.createGet(configRulesUri).setCompletion((o, e) -> {
            if (e != null) {
                logSevere(e);
                future.completeExceptionally(e);
                return;
            }
            ServiceDocumentQueryResult results = o.getBody(ServiceDocumentQueryResult.class);
            if (results.documents == null) {
                future.complete(Collections.emptyList());
            }
            List<AdapterDetails> allAdapterDetails = results.documents.values().stream()
                    .map(obj -> Utils.fromJson(obj, ConfigurationRuleState.class))
                    .map(rule -> getAdapterDetailsFromConfigRule(rule.id))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            future.complete(allAdapterDetails);
        });
        this.setAuthorizationContext(op, this.getSystemAuthorizationContext());
        this.sendRequest(op);
        return future;
    }

    private String createAdapterRpLink(AdapterDetails adapterDetails, String rpLink) {
        return adapterDetails.featureFlagKey.replaceAll(ADAPTER_FEATURE_FLAG_PREFIX + ".", "")
                + StatsUtil.SEPARATOR
                + UriUtils.getLastPathSegment(rpLink);
    }

    private StatsCollectionTaskState createAdapterTaskState(AdapterDetails adapterDetails,
            String rpLink) {
        StatsCollectionTaskState taskState = new StatsCollectionTaskState();
        taskState.resourcePoolLink = rpLink;
        taskState.options = EnumSet.of(TaskOption.SELF_DELETE_ON_COMPLETION);
        taskState.documentSelfLink = UriUtils.buildUriPath(StatsCollectionTaskService.FACTORY_LINK,
                createAdapterRpLink(adapterDetails, rpLink));
        taskState.statsAdapterReference = UriUtils
                .buildUri(ServiceHost.LOCAL_HOST, getHost().getPort(),
                        adapterDetails.adapterReference, null);
        taskState.customizationClauses = new ArrayList<>();
        taskState.customizationClauses.add(getOptionalStatsAdapterTaskQuery());

        return taskState;
    }

    /**
     * Handle multiple callbacks.
     *
     * @param deferredResult The deferred result to callback into
     * @param numOfCallbacks The expected number of callbacks
     * @param exs The list to capture all exceptions
     * @param ex The exception in the current operation
     */
    private void handleCallbacks(DeferredResult<Void> deferredResult, AtomicInteger numOfCallbacks,
            List<Throwable> exs, Throwable ex) {
        if (ex != null) {
            exs.add(ex);
        }

        if (numOfCallbacks.decrementAndGet() == 0) {
            if (!exs.isEmpty()) {
                deferredResult.fail(exs.iterator().next());
                return;
            }
            deferredResult.complete(null);
        }
    }
}
