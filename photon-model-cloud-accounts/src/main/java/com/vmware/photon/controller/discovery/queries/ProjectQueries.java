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

import static com.vmware.photon.controller.model.constants.PhotonModelConstants.ESTIMATED_CHARGES;

import java.util.Set;

import com.vmware.photon.controller.discovery.cloudaccount.utils.QueryHelper.QueryCompletionHandler;
import com.vmware.photon.controller.discovery.cloudaccount.utils.QueryUtils;
import com.vmware.photon.controller.discovery.common.services.ProjectService.ProjectState;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService.ResourceMetrics;
import com.vmware.photon.controller.model.util.ClusterUtil.ServiceTypeCluster;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocumentDescription.TypeName;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.NumericRange;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;

/**
 * Project related queries.
 */
public class ProjectQueries {

    /**
     * Returns a list of projects for given set of project links. We make this query in order to
     * do pagination.
     *
     * @param service The service executing the query
     * @param projectLinks The set of project links
     * @param resultLimit The result limit
     * @param completionHandler The completion handler
     */
    public static void getProjects(Service service, Set<String> projectLinks, int resultLimit,
            QueryCompletionHandler<ServiceDocumentQueryResult> completionHandler) {
        Query query = Builder.create()
                .addKindFieldClause(ProjectState.class)
                .addInClause(ProjectState.FIELD_NAME_SELF_LINK, projectLinks)
                .build();

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .setQuery(query)
                .setResultLimit(resultLimit)
                .build();

        // projects have to be queried in system auth context
        QueryUtils.startQueryTaskWithSystemAuth(service, queryTask, completionHandler);
    }

    /**
     * Return the spend of the project for given project link.
     *
     * @param service The service executing the query
     * @param projectLink The set of project link
     * @param completionHandler The completion handler
     */
    public static void getProjectSpend(Service service, String projectLink,
            QueryCompletionHandler<ResourceMetrics> completionHandler) {
        String metricSelfLink = UriUtils.buildUriPath(ResourceMetricsService.FACTORY_LINK,
                UriUtils.getLastPathSegment(projectLink));

        Query query = Query.Builder.create()
                .addKindFieldClause(ResourceMetrics.class)
                .addFieldClause(ResourceMetrics.FIELD_NAME_SELF_LINK, metricSelfLink,
                        MatchType.PREFIX)
                .addRangeClause(QuerySpecification
                        .buildCompositeFieldName(ResourceMetrics.FIELD_NAME_ENTRIES, ESTIMATED_CHARGES),
                        NumericRange.createDoubleRange(0.0, Double.MAX_VALUE, true, true))
                .build();

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.SORT)
                .addOption(QueryOption.EXPAND_CONTENT)
                .addOption(QueryOption.TOP_RESULTS)
                // No-op in Symphony. Required for special handling of immutable documents.
                // This will prevent Lucene from holding the full result set in memory.
                .addOption(QueryOption.INCLUDE_ALL_VERSIONS)
                .setResultLimit(1)
                .orderDescending(ResourceMetrics.FIELD_NAME_SELF_LINK, TypeName.STRING)
                .setQuery(query)
                .build();

        QueryCompletionHandler<ServiceDocumentQueryResult> nestedCompletion = (results, e) -> {
            if (e != null) {
                completionHandler.handle(null, e);
                return;
            }

            if (results.documentCount == 0) {
                completionHandler.handle(null, null);
                return;
            }

            ResourceMetrics metric = Utils
                    .fromJson(results.documents.values().iterator().next(),
                            ResourceMetrics.class);
            completionHandler.handle(metric, null);
        };

        QueryUtils.startQueryTaskWithSystemAuth(service, queryTask,
                ServiceTypeCluster.METRIC_SERVICE, nestedCompletion);
    }
}
