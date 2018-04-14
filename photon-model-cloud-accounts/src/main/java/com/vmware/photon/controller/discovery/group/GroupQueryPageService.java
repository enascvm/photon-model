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

package com.vmware.photon.controller.discovery.group;

import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.GROUP_DESCRIPTION;
import static com.vmware.photon.controller.discovery.common.utils.InventoryQueryUtils.getInventoryQueryPage;
import static com.vmware.photon.controller.model.UriPaths.GROUPS_API_SERVICE;
import static com.vmware.photon.controller.model.UriPaths.GROUP_QUERY_PAGE_SERVICE;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.vmware.photon.controller.discovery.cloudaccount.utils.QueryHelper;
import com.vmware.photon.controller.discovery.common.utils.StringUtil;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.QueryResultsProcessor;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask.Query;

/**
 * Service to handle page requests generated via {@link GroupQueryTaskService}. This service
 * takes care of processing {@link ResourceGroupState} service documents into an API-friendly
 * {@link GroupViewState} format.
 */
public class GroupQueryPageService extends StatelessService {
    public static final String SELF_LINK_PREFIX = GROUP_QUERY_PAGE_SERVICE;

    /** The next/prev page link associated with the query task. */
    public final String pageLink;

    /** The time after which the stateless service expires. */
    public final long expirationTimeMicros;

    /** The tenant links context. */
    public final List<String> tenantLinks;


    /** Different stages for processing the groups results page. */
    enum Stages {
        /** Stage to get groups from QueryTask results. */
        QUERY_GROUPS,

        /** Stage to build the {@link GroupViewState} PODO. */
        BUILD_PODO,

        /** Stage to build result. */
        BUILD_RESULT,

        /** Stage to indicate success. */
        SUCCESS
    }

    /** Local context object to pass around during callbacks. */
    public static class GroupsQueryPageContext {
        Stages stage;
        Operation inputOp;
        String nextPageLink;
        String prevPageLink;
        ServiceDocumentQueryResult results;
        Long documentCount;
        Throwable error;

        /** This is the photon-model representation of the resource group - not API-friendly. */
        List<ResourceGroupState> resourceGroupStates;

        /** This represents the API-friendly model of a resource group . */
        List<GroupViewState> groupViewStates;

        GroupsQueryPageContext() {
            this.stage = Stages.QUERY_GROUPS;
        }
    }

    /**
     * An API-friendly representation of a resource group object, constructed by consulting
     * {@code photon-model} internal service documents and transforming it (and its associated
     * links) into this class.
     */
    public static class GroupViewState extends ServiceDocument {
        @Documentation(description = "The name of the resource group.")
        public String name;

        @Documentation(description = "A user-supplied description of the resource group.")
        public String description;

        @Documentation(description = "The query that defines the criteria for creating the "
                + "grouping for resources.")
        public Query query;

        @Documentation(description = "The documentSelfLink of the user that created the resource group.")
        public String createdBy;

        @Documentation(description = "The creation time in micros of this resource group.")
        public Long creationTimeMicros;

        @Documentation(description = "Custom property bag that can be used to store group specific properties.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Map<String, String> customProperties;

    }

    public GroupQueryPageService(String pageLink, long expMicros, List<String> tenantLinks) {
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
    public void handleMaintenance(Operation op) {
        op.complete();
        getHost().stopService(this);
    }

    @Override
    public void handleGet(Operation op) {
        GroupsQueryPageContext ctx = new GroupsQueryPageContext();
        ctx.inputOp = op;
        handleStages(ctx);
    }

    private void handleStages(GroupsQueryPageContext ctx) {
        logFine("handleStages: %s", ctx.stage);
        switch (ctx.stage) {
        case QUERY_GROUPS:
            getGroups(ctx, Stages.BUILD_PODO);
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
     * Executes the query for the given page link.
     */
    private void getGroups(GroupsQueryPageContext ctx, Stages nextStage) {
        getInventoryQueryPage(this, this.pageLink)
                .whenComplete((body, e) -> {
                    if (e != null) {
                        ctx.error = e;
                        handleError(ctx);
                        return;
                    }

                    ServiceDocumentQueryResult results = body.results;
                    ctx.documentCount = results.documentCount;
                    if (results.documentCount == 0) {
                        ctx.results = new ServiceDocumentQueryResult();
                        ctx.results.documentCount = ctx.documentCount;
                        ctx.stage = Stages.SUCCESS;
                        handleSuccess(ctx);
                        return;
                    }
                    extractQueryResults(ctx, results);
                    ctx.stage = nextStage;
                    handleStages(ctx);
                });
    }

    /**
     * Extract the query results and start page services as needed.
     */
    private void extractQueryResults(GroupsQueryPageContext ctx,
            ServiceDocumentQueryResult result) {
        ctx.resourceGroupStates = new ArrayList<>();
        QueryResultsProcessor processor = QueryResultsProcessor.create(result);

        for (String documentLink : result.documentLinks) {
            ResourceGroupState resourceGroupState = processor.document(documentLink,
                    ResourceGroupState.class);
            ctx.resourceGroupStates.add(resourceGroupState);
        }

        if (!StringUtil.isEmpty(result.nextPageLink)) {
            GroupQueryPageService pageService = new GroupQueryPageService(
                    result.nextPageLink, this.expirationTimeMicros, this.tenantLinks);

            ctx.nextPageLink = QueryHelper
                    .startStatelessPageService(this, SELF_LINK_PREFIX,
                            pageService,
                            this.expirationTimeMicros,
                            failure -> {
                                ctx.error = failure;
                                handleError(ctx);
                            });
        }

        if (!StringUtil.isEmpty(result.prevPageLink)) {
            GroupQueryPageService pageService = new GroupQueryPageService(
                    result.prevPageLink, this.expirationTimeMicros, this.tenantLinks);

            ctx.prevPageLink = QueryHelper
                    .startStatelessPageService(this, SELF_LINK_PREFIX,
                            pageService,
                            this.expirationTimeMicros,
                            failure -> {
                                ctx.error = failure;
                                handleError(ctx);
                            });
        }
    }

    /** Build {@link GroupViewState} PODOs. */
    void buildPODO(GroupsQueryPageContext ctx, Stages nextStage) {
        List<GroupViewState> groupViewStates = new ArrayList<>();
        for (ResourceGroupState groupState : ctx.resourceGroupStates) {
            GroupViewState groupViewState = new GroupViewState();
            groupViewState.documentSelfLink = UriUtils
                    .buildUriPath(GROUPS_API_SERVICE,
                            UriUtils.getLastPathSegment(groupState.documentSelfLink));
            groupViewState.name = groupState.name;
            groupViewState.description = groupState.customProperties
                    .get(GROUP_DESCRIPTION);
            groupViewState.createdBy = groupState.documentAuthPrincipalLink;
            groupViewState.documentExpirationTimeMicros = this.expirationTimeMicros;
            groupViewState.query = groupState.query;
            groupViewState.customProperties = groupState.customProperties;
            groupViewStates.add(groupViewState);
        }
        ctx.groupViewStates = groupViewStates;
        ctx.stage = nextStage;
        handleStages(ctx);
    }

    /**
     * Save {@link GroupViewState} PODOs and build {@link ServiceDocumentQueryResult}.
     */
    private void buildResult(GroupsQueryPageContext ctx, Stages nextStage) {
        ServiceDocumentQueryResult result = new ServiceDocumentQueryResult();
        result.documentLinks = new ArrayList<>();
        result.documents = new LinkedHashMap<>();
        for (GroupViewState groupState : ctx.groupViewStates) {
            result.documentLinks.add(groupState.documentSelfLink);
            result.documents.put(groupState.documentSelfLink, groupState);
        }
        result.nextPageLink = ctx.nextPageLink;
        result.prevPageLink = ctx.prevPageLink;
        result.documentCount = ctx.documentCount;
        ctx.results = result;
        ctx.stage = nextStage;
        handleStages(ctx);
    }

    private void handleSuccess(GroupsQueryPageContext ctx) {
        ctx.inputOp.setBody(ctx.results);
        ctx.inputOp.complete();
    }

    private void handleError(GroupsQueryPageContext ctx) {
        logWarning("Failed at stage %s with exception: %s", ctx.stage, Utils.toString(ctx.error));
        ctx.inputOp.fail(ctx.error);
    }
}
