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

import java.util.logging.Level;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Query utilities.
 */
public class QueryUtils {

    public static final String MAX_RESULT_LIMIT_PROPERTY =
            UriPaths.PROPERTY_PREFIX + "query.maxResultLimit";
    public static final int DEFAULT_MAX_RESULT_LIMIT = 10000;
    private static final int MAX_RESULT_LIMIT = Integer
            .getInteger(MAX_RESULT_LIMIT_PROPERTY, DEFAULT_MAX_RESULT_LIMIT);

    /**
     * Executes the given query task.
     *
     * @param service The service executing the query task.
     * @param queryTask The query task.
     */
    public static DeferredResult<QueryTask> startQueryTask(Service service, QueryTask queryTask) {
        // We don't want any unbounded queries. By default, we cap the query results to 10000.
        if (queryTask.querySpec.resultLimit == null) {
            service.getHost().log(Level.WARNING,
                    "No result limit set on the query: %s. Defaulting to %d",
                    Utils.toJson(queryTask), MAX_RESULT_LIMIT);
            queryTask.querySpec.options.add(QueryOption.TOP_RESULTS);
            queryTask.querySpec.resultLimit = MAX_RESULT_LIMIT;
        }

        return service.sendWithDeferredResult(
                Operation
                        .createPost(UriUtils.buildUri(service.getHost(),
                                ServiceUriPaths.CORE_QUERY_TASKS))
                        .setBody(queryTask)
                        .setConnectionSharing(true),
                QueryTask.class);
    }
}