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

package com.vmware.photon.controller.discovery.cloudusage;

import static com.vmware.photon.controller.discovery.cloudaccount.utils.QueryHelper.startQueryTask;
import static com.vmware.photon.controller.model.UriPaths.CLOUD_USAGE_PAGE_SERVICE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.vmware.photon.controller.discovery.cloudaccount.utils.QueryHelper;
import com.vmware.photon.controller.discovery.cloudusage.CloudUsageReportService.CloudUsageReport;
import com.vmware.photon.controller.discovery.common.services.ProjectService.ProjectState;
import com.vmware.photon.controller.discovery.common.services.UserContextQueryService;
import com.vmware.photon.controller.discovery.common.services.UserContextQueryService.UserContext;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.RequestRouter.Route;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;

/**
 * Service to handle page requests generated via {@link CloudUsageReportTaskService}. This service
 * takes care of gathering the cloud usage information through various services depending on the
 * query. In this case, the query's pivot point is the compute host. In case of a different pivot
 * point like stats, we would have to design a new page service like this which goes off the stats
 * data for the purpose of filtering, paging and sorting.
 */
public class CloudUsagePageService extends StatelessService {
    public static final String SELF_LINK_PREFIX = CLOUD_USAGE_PAGE_SERVICE;

    /**
     * The next/prev page link associated with the query task.
     */
    public final String pageLink;

    /**
     * The time after which the stateless service expires.
     */
    public final long expirationTimeMicros;

    /**
     * The tenant links context.
     */
    public final List<String> tenantLinks;

    /**
     * Different stages for getting the public cloud usage report.
     */
    enum Stages {
        /**
         * Stage to get user context.
         */
        GET_USER_CONTEXT,

        /**
         * Stage to get compute host.
         */
        GET_COMPUTE_HOST,

        /**
         * Stage to query compute resources count.
         */
        QUERY_COMPUTE_RESOURCES_COUNT,

        /**
         * Stage to extract project information.
         */
        GET_PROJECT_STATES,

        /**
         * Stage to build the public cloud usage PODO.
         */
        BUILD_PODO,

        /**
         * Stage to build result.
         */
        BUILD_RESULT,

        /**
         * Stage to indicate success.
         */
        SUCCESS
    }

    /**
     * Context object to pass around during callbacks.
     */
    public static class Context {
        UserContext userContext;
        Stages stage;
        Operation inputOp;
        List<ComputeState> computeStates;
        Map<String, ComputeDescription> computeDescriptions;
        String nextPageLink;
        String prevPageLink;
        Map<String, List<ProjectState>> projects;
        ServiceDocumentQueryResult result;
        Long documentCount;
        Throwable error;

        Map<String, CloudUsageReport> cloudUsages;
        Map<String, Long> computeResourcesCount;

        Context() {
            this.stage = Stages.GET_USER_CONTEXT;
        }
    }

    public CloudUsagePageService(String pageLink, long expMicros, List<String> tenantLinks) {
        this.pageLink = pageLink;
        this.expirationTimeMicros = expMicros;
        this.tenantLinks = tenantLinks;
    }

    @Override
    public void handleStart(Operation post) {
        ServiceDocument initState = post.getBody(ServiceDocument.class);

        long interval = initState.documentExpirationTimeMicros - Utils.getNowMicrosUtc();
        if (interval <= 0) {
            logWarning("Task expiration is in the past, extending it");
            interval = TimeUnit.SECONDS.toMicros(getHost().getMaintenanceIntervalMicros() * 2);
        }

        super.toggleOption(ServiceOption.PERIODIC_MAINTENANCE, true);
        super.setMaintenanceIntervalMicros(interval);

        post.complete();
    }

    @Override
    public void handleGet(Operation op) {
        Context ctx = new Context();
        ctx.inputOp = op;
        handleStages(ctx);
    }

    @Override
    public void handleMaintenance(Operation op) {
        op.complete();
        getHost().stopService(this);
    }

    private void handleStages(Context ctx) {
        switch (ctx.stage) {
        case GET_USER_CONTEXT:
            getUserContext(ctx, Stages.GET_COMPUTE_HOST);
            break;
        case GET_COMPUTE_HOST:
            getComputeHost(ctx, Stages.QUERY_COMPUTE_RESOURCES_COUNT);
            break;
        case QUERY_COMPUTE_RESOURCES_COUNT:
            getResourceCountByHost(ctx, Stages.GET_PROJECT_STATES);
            break;
        case GET_PROJECT_STATES:
            getProjectStates(ctx, Stages.BUILD_PODO);
            break;
        case BUILD_PODO:
            buildPODO(ctx, Stages.BUILD_RESULT);
            break;
        case BUILD_RESULT:
            buildResult(ctx, Stages.SUCCESS);
            break;
        case SUCCESS:
            handleSuccess(ctx);
            break;
        default:
            throw new IllegalStateException("Unknown stage encountered: " + ctx.stage);
        }
    }

    /**
     * Retrieves the current user context.
     */
    private void getUserContext(Context ctx, Stages nextStage) {
        // TODO VSYM-1135: Extract to common utility method for getting tenant links
        Operation.createGet(this.getHost(), UserContextQueryService.SELF_LINK)
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        ctx.error = ex;
                        handleError(ctx);
                        return;
                    }
                    ctx.userContext = op.getBody(UserContext.class);

                    if (ctx.userContext.projects == null
                            || ctx.userContext.projects.isEmpty()) {
                        ctx.error = new IllegalStateException("User not part of any project");
                        handleError(ctx);
                        return;
                    }
                    ctx.stage = nextStage;
                    handleStages(ctx);
                }).sendWith(this);
    }

    /**
     * Executes the query for the given page link.
     */
    private void getComputeHost(Context ctx, Stages nextStage) {
        Consumer<ServiceDocumentQueryResult> onSuccess = result -> {
            if (result.documentCount == 0) {
                ctx.result = new ServiceDocumentQueryResult();
                ctx.stage = Stages.SUCCESS;
                handleSuccess(ctx);
                return;
            }

            extractQueryResults(ctx, result);
            ctx.stage = nextStage;
            handleStages(ctx);
        };

        Consumer<Throwable> onFailure = failure -> {
            ctx.error = failure;
            handleError(ctx);
        };

        QueryHelper.getPage(this, this.pageLink, onSuccess, onFailure);
    }

    /**
     * Extract the query results and start page services as needed.
     */
    private void extractQueryResults(Context ctx, ServiceDocumentQueryResult result) {
        List<ComputeState> computeStates = new ArrayList<>();
        for (Object o : result.documents.values()) {
            ComputeState computeState = Utils.fromJson(o, ComputeState.class);
            computeStates.add(computeState);
        }
        ctx.computeStates = computeStates;
        ctx.documentCount = result.documentCount;

        ctx.computeDescriptions = new HashMap<>();
        for (String key : result.selectedDocuments.keySet()) {
            Object o = result.selectedDocuments.get(key);
            ComputeDescription computeDescription = Utils.fromJson(o, ComputeDescription.class);
            ctx.computeDescriptions.put(key, computeDescription);
        }

        if (result.nextPageLink != null && !result.nextPageLink.isEmpty()) {
            CloudUsagePageService pageService = new CloudUsagePageService(
                    result.nextPageLink, this.expirationTimeMicros,
                    this.tenantLinks);

            ctx.nextPageLink = QueryHelper
                    .startStatelessPageService(this, CloudUsagePageService.SELF_LINK_PREFIX,
                            pageService,
                            this.expirationTimeMicros,
                            failure -> {
                                ctx.error = failure;
                                handleError(ctx);
                            });
        }

        if (result.prevPageLink != null && !result.prevPageLink.isEmpty()) {
            CloudUsagePageService pageService = new CloudUsagePageService(
                    result.prevPageLink, this.expirationTimeMicros,
                    this.tenantLinks);

            ctx.prevPageLink = QueryHelper
                    .startStatelessPageService(this, CloudUsagePageService.SELF_LINK_PREFIX,
                            pageService,
                            this.expirationTimeMicros,
                            failure -> {
                                ctx.error = failure;
                                handleError(ctx);
                            });
        }
    }

    /**
     * Filter and map resource group state to project.
     */
    private void getProjectStates(Context ctx, Stages nextStage) {
        Map<String, List<ProjectState>> projectsPerCompute = new HashMap<>();
        // local cache to lookup projects by tenant links.
        Map<String, ProjectState> projectByProjectLinks = new HashMap<>();

        for (ComputeState computeState : ctx.computeStates) {
            List<ProjectState> projects = new ArrayList<>();

            for (String projectLink : computeState.tenantLinks) {
                if (projectByProjectLinks.get(projectLink) != null) {
                    projects.add(projectByProjectLinks.get(projectLink));
                } else if (this.tenantLinks.contains(projectLink)) {
                    for (ProjectState projectState : ctx.userContext.projects) {
                        if (projectLink.equals(projectState.documentSelfLink)) {
                            projects.add(projectState);
                            projectByProjectLinks.put(projectLink, projectState);
                            break;
                        }
                    }
                }
            }
            projectsPerCompute.put(computeState.id, projects);
        }
        ctx.projects = projectsPerCompute;
        ctx.stage = nextStage;
        handleStages(ctx);
    }

    /**
     * Get the count of resources associated with compute hosts.
     */
    private void getResourceCountByHost(Context ctx, Stages nextStage) {
        ctx.computeResourcesCount = new ConcurrentHashMap<>();
        AtomicInteger count = new AtomicInteger(ctx.computeStates.size());
        for (ComputeState computeState : ctx.computeStates) {
            QueryTask task = QueryTask.Builder.createDirectTask()
                    .addOption(QueryOption.COUNT)
                    .addOption(QueryOption.INDEXED_METADATA)
                    .setQuery(Query.Builder.create()
                            .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK,
                                    computeState.documentSelfLink)
                            .build())
                    .build();

            task.tenantLinks = this.tenantLinks;

            Consumer<ServiceDocumentQueryResult> onSuccess = result -> {
                ctx.computeResourcesCount.put(computeState.documentSelfLink, result.documentCount);
                if (count.decrementAndGet() == 0) {
                    ctx.stage = nextStage;
                    handleStages(ctx);
                }
            };

            Consumer<Throwable> onFailure = failure -> {
                ctx.computeResourcesCount.put(computeState.documentSelfLink, 0L);
                count.decrementAndGet();
                ctx.error = failure;
                handleError(ctx);
            };

            startQueryTask(this, task, onSuccess, onFailure);
        }

    }

    /**
     * Build cloud usage PODOs.
     */
    private void buildPODO(Context ctx, Stages nextStage) {
        Map<String, CloudUsageReport> cloudUsages = new LinkedHashMap<>();
        for (ComputeState computeState : ctx.computeStates) {
            ComputeDescription computeDescription = ctx.computeDescriptions
                    .get(computeState.descriptionLink);

            CloudUsageReport pcu = new CloudUsageReport();
            pcu.id = UUID.randomUUID().toString();
            pcu.computeName = computeDescription.name;

            if (pcu.computeName == null || pcu.computeName.isEmpty()) {
                pcu.computeName = computeState.id;
            }

            pcu.projects = ctx.projects.get(computeState.id);
            pcu.tenantLinks = pcu.projects.stream()
                    .map(project -> project.documentSelfLink).collect(Collectors.toList());
            pcu.environmentName = computeDescription.environmentName;
            pcu.resourceCount = ctx.computeResourcesCount.get(computeState.documentSelfLink);
            pcu.documentExpirationTimeMicros = this.expirationTimeMicros;
            cloudUsages.put(pcu.id, pcu);
        }
        ctx.cloudUsages = cloudUsages;
        ctx.stage = nextStage;
        handleStages(ctx);
    }

    /**
     * Save cloud usage PODOs and build {@link ServiceDocumentQueryResult}.
     */
    private void buildResult(Context ctx, Stages nextStage) {
        List<Operation> operations = new ArrayList<>();
        for (CloudUsageReport cloudUsageReport : ctx.cloudUsages.values()) {
            Operation operation = Operation
                    .createPost(getHost(), CloudUsageReportService.FACTORY_LINK)
                    .setBody(cloudUsageReport);
            operations.add(operation);
        }

        OperationJoin.create(operations)
                .setCompletion((ops, failures) -> {
                    if (failures != null) {
                        ctx.error = failures.values().iterator().next();
                        handleError(ctx);
                        return;
                    }

                    for (Operation op : ops.values()) {
                        CloudUsageReport cloudUsageReport = op.getBody(CloudUsageReport.class);
                        CloudUsageReport prevCloudUsageReport = ctx.cloudUsages.get(
                                cloudUsageReport.id);

                        if (prevCloudUsageReport == null) {
                            ctx.error = new IllegalStateException(
                                    "Cannot look up cloud usage with id: " + cloudUsageReport.id);
                            handleError(ctx);
                            return;
                        }

                        ctx.cloudUsages.put(cloudUsageReport.id, cloudUsageReport);
                    }

                    ServiceDocumentQueryResult result = new ServiceDocumentQueryResult();
                    result.documentLinks = new ArrayList<>();
                    result.documents = new LinkedHashMap<>();
                    for (CloudUsageReport cloudUsageReport : ctx.cloudUsages.values()) {
                        result.documentLinks.add(cloudUsageReport.documentSelfLink);
                        result.documents.put(cloudUsageReport.documentSelfLink, cloudUsageReport);
                    }

                    result.nextPageLink = ctx.nextPageLink;
                    result.prevPageLink = ctx.prevPageLink;
                    result.documentCount = ctx.documentCount;
                    ctx.result = result;
                    ctx.stage = nextStage;
                    handleStages(ctx);
                })
                .sendWith(this);

    }

    private void handleSuccess(Context ctx) {
        ctx.inputOp.setBody(ctx.result);
        ctx.inputOp.complete();
    }

    private void handleError(Context ctx) {
        logSevere("Failed at stage %s with exception: %s", ctx.stage, Utils.toString(ctx.error));
        ctx.inputOp.fail(ctx.error);
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument d = super.getDocumentTemplate();
        d.documentDescription.serviceRequestRoutes = new HashMap<>();
        Route route = new Route();
        route.action = Action.GET;
        route.description = "Generates the public cloud usage report for the logged in user.";
        route.responseType = ServiceDocumentQueryResult.class;
        d.documentDescription.serviceRequestRoutes
                .put(route.action, Collections.singletonList(route));
        return d;
    }
}