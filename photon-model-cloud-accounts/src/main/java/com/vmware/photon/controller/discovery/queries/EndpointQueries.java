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

import java.util.Collection;
import java.util.List;

import com.vmware.photon.controller.discovery.cloudaccount.utils.QueryHelper.QueryCompletionHandler;
import com.vmware.photon.controller.discovery.cloudaccount.utils.QueryUtils;
import com.vmware.photon.controller.discovery.common.services.OrganizationService.OrganizationState;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;

public class EndpointQueries {

    /**
     * Returns a list of {@link OrganizationState} objects that are associated with
     * {@code orgSelfLinks}
     *
     * @param service The service executing the query
     * @param tenantLinks The tenant/auth links to use on the QueryTask
     * @param orgSelfLinks The list of documentSelfLinks to look up
     * @param completionHandler The completion handler
     */
    public static void getOrgDetails(Service service, List<String> tenantLinks,
            Collection<String> orgSelfLinks,
            QueryCompletionHandler<ServiceDocumentQueryResult> completionHandler) {
        // Orgs will have to be queried with system auth context
        getDocuments(true, service, OrganizationState.class, tenantLinks, orgSelfLinks, completionHandler);
    }

    /**
     * Returns a list of {@code documentClass} objects that are associated with
     * {@code docSelfLinks}
     *
     * @param isSysAuth Should the documents be queried using System Authorization
     * @param service The service executing the query
     * @param documentClass The document class to query
     * @param tenantLinks The tenant/auth links to use on the QueryTask
     * @param docSelfLinks The list of documentSelfLinks to look up
     * @param completionHandler The completion handler
     */
    public static void getDocuments(boolean isSysAuth, Service service,
            Class<? extends ServiceDocument> documentClass, List<String> tenantLinks,
            Collection<String> docSelfLinks,
            QueryCompletionHandler<ServiceDocumentQueryResult> completionHandler) {
        Query query = Builder.create()
                .addKindFieldClause(documentClass)
                .addInClause(ServiceDocument.FIELD_NAME_SELF_LINK, docSelfLinks)
                .build();

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .setQuery(query)
                .build();
        queryTask.tenantLinks = tenantLinks;

        if (isSysAuth) {
            QueryUtils.startQueryTaskWithSystemAuth(service, queryTask, completionHandler);
        } else {
            QueryUtils.startQueryTask(service, queryTask, completionHandler);
        }
    }
}
