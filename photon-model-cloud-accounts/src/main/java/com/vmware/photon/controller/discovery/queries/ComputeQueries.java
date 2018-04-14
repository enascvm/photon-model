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

package com.vmware.photon.controller.discovery.queries;


import static com.vmware.photon.controller.discovery.common.utils.InventoryQueryUtils.startInventoryQueryTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.vmware.photon.controller.discovery.cloudaccount.utils.QueryHelper;
import com.vmware.photon.controller.discovery.cloudaccount.utils.QueryHelper.QueryCompletionHandler;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;

/**
 * The compute service related queries
 */
public class ComputeQueries {

    /**
     * Retrieves the compute count for the given project link.
     *
     * @param service The service executing the query
     * @param projectLink The project link
     * @param completionHandler The completion handler
     */
    public static void getComputeCountForProject(Service service, String projectLink,
            QueryCompletionHandler<Long> completionHandler) {
        QueryTask task = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.COUNT)
                .setQuery(Query.Builder.create()
                        .addKindFieldClause(ComputeState.class)
                        .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK, "*",
                                MatchType.WILDCARD)
                        .addCollectionItemClause(ComputeState.FIELD_NAME_TENANT_LINKS, projectLink)
                        .build())
                .build();

        task.tenantLinks = Collections.singletonList(projectLink);

        startInventoryQueryTask(service, task)
                .whenComplete((qt, e) -> {
                    if (e != null) {
                        completionHandler.handle(0L, e);
                        return;
                    }

                    completionHandler.handle(qt.results.documentCount, null);
                });
    }

    /**
     * Retrieves the cloud endpoints for a given projects. Gets both the compute description
     * and the compute state corresponding to the cloud endpoint.
     *
     * @param service The service executing the query
     * @param projectLink The project link
     * @param completionHandler The completion handler
     */
    public static void getEndpointsByProject(Service service, String projectLink,
            QueryCompletionHandler<List<ComputeStateWithDescription>> completionHandler) {

        QueryTask queryTask = QueryHelper
                .buildComputeHostWithDescriptionQueryTask(Collections.singletonList(projectLink),
                        0);

        startInventoryQueryTask(service, queryTask)
                .whenComplete((qt, e) -> {
                    if (e != null) {
                        completionHandler.handle(null, e);
                        return;
                    }

                    ServiceDocumentQueryResult results = qt.results;

                    if (results.documentCount == 0) {
                        completionHandler.handle(null, null);
                        return;
                    }

                    List<ComputeStateWithDescription> cloudEndpoints = new ArrayList<>();
                    for (Object o : results.documents.values()) {
                        ComputeStateWithDescription state = Utils
                                .fromJson(o, ComputeStateWithDescription.class);
                        state.description = Utils
                                .fromJson(results.selectedDocuments.get(state.descriptionLink),
                                        ComputeDescription.class);
                        cloudEndpoints.add(state);
                    }
                    completionHandler.handle(cloudEndpoints, null);
                });
    }

    /**
     * Retrieves the computes for the given project link.
     *
     * @param service The service executing the query
     * @param projectLink The project link
     */
    public static DeferredResult<ServiceDocumentQueryResult> getComputesForProject(Service service,
            String projectLink, int resultLimit) {
        QueryTask task = QueryTask.Builder.createDirectTask()
                .setQuery(Query.Builder.create()
                        .addKindFieldClause(ComputeState.class)
                        .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK, "*",
                                MatchType.WILDCARD)
                        .addCollectionItemClause(ComputeState.FIELD_NAME_TENANT_LINKS, projectLink)
                        .build())
                .setResultLimit(resultLimit)
                .build();

        task.tenantLinks = Collections.singletonList(projectLink);

        DeferredResult<ServiceDocumentQueryResult> deferred = new DeferredResult<>();

        startInventoryQueryTask(service, task)
                .whenComplete((qt, e) -> {
                    if (e != null) {
                        deferred.fail(e);
                        return;
                    }

                    deferred.complete(qt.results);
                });
        return deferred;
    }
}
