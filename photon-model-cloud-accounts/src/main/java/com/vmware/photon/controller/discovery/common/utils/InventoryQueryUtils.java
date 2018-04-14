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

package com.vmware.photon.controller.discovery.common.utils;

import static com.vmware.photon.controller.discovery.cloudaccount.utils.ClusterUtils.createInventoryUri;

import java.net.URI;
import java.util.logging.Level;

import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * The query utilities for inventory service.
 */
public class InventoryQueryUtils {

    public static final int DEFAULT_MAX_RESULT_LIMIT = 10000;

    /**
     * Gets the page from inventory service for the given page link.
     *
     * @param service The service executing the request.
     * @param pageLink The page link for the query task.
     */
    public static DeferredResult<QueryTask> getInventoryQueryPage(Service service,
            String pageLink) {
        DeferredResult<QueryTask> deferred = new DeferredResult<>();

        URI uri = createInventoryUri(service.getHost(), pageLink);

        Operation operation = Operation.createGet(uri);
        service.sendWithDeferredResult(operation)
                .whenComplete((op, ex) -> {
                    if (ex != null) {
                        deferred.fail(ex);
                        return;
                    }

                    QueryTask body = op.getBody(QueryTask.class);
                    deferred.complete(body);
                });
        return deferred;
    }

    /**
     * Utility method to execute the given query task on inventory cluster.
     *
     * @param service The service executing the query task.
     * @param queryTask The query task.
     */
    public static DeferredResult<QueryTask> startInventoryQueryTask(Service service,
            QueryTask queryTask) {
        return startInventoryQueryTask(service, queryTask, false,
                true);
    }

    /**
     * Utility method to execute the given query task on inventory cluster with an option to toggle
     * QueryOption.INDEXED_METADATA.
     *
     * for e.g. QueryOption.INDEXED_METADATA is required to be false for AuthCredentialsState
     *
     * @param service The service executing the query task.
     * @param queryTask The query task.
     */
    public static DeferredResult<QueryTask> startInventoryQueryTask(Service service, QueryTask
            queryTask, boolean setIndexedMetadata) {
        return startInventoryQueryTask(service, queryTask, false,
                setIndexedMetadata);
    }

    /**
     * Utility method to execute the given query task on inventory cluster.
     *
     * @param service The service executing the query task.
     * @param queryTask The query task.
     */
    public static DeferredResult<QueryTask> startInventoryQueryTask(Service service,
            QueryTask queryTask, boolean withSystemAuth, boolean setIndexedMetadata) {
        if (setIndexedMetadata && !queryTask.querySpec.options.contains(QueryOption
                .INDEXED_METADATA)) {
            queryTask.querySpec.options.add(QueryOption.INDEXED_METADATA);
        }

        Operation queryOp = createInventoryQueryTaskOperation(service, queryTask);

        DeferredResult<QueryTask> deferred = new DeferredResult<>();

        OperationContext[] ctx = new OperationContext[1];
        if (withSystemAuth) {
            ctx[0] = OperationContext.getOperationContext();
            service.setAuthorizationContext(queryOp, service.getSystemAuthorizationContext());
        }

        service.sendWithDeferredResult(queryOp, QueryTask.class)
                .whenComplete((qt, e) -> {
                    if (withSystemAuth) {
                        OperationContext.restoreOperationContext(ctx[0]);
                    }
                    if (e != null) {
                        deferred.fail(e);
                        return;
                    }

                    deferred.complete(qt);
                });

        return deferred;
    }

    /**
     * Creates a query task operation for inventory service.
     *
     * @param service The service executing the query task.
     * @param queryTask The query task.
     */

    public static Operation createInventoryQueryTaskOperation(Service service,
            QueryTask queryTask) {
        boolean isCountQuery = queryTask.querySpec.options.contains(QueryOption.COUNT);

        if (!isCountQuery) {
            // We don't want any unbounded queries. By default, we cap the query results to 10000.
            if (queryTask.querySpec.resultLimit == null) {
                service.getHost().log(Level.WARNING,
                        "No result limit set on the query: %s. Defaulting to %d",
                        Utils.toJson(queryTask), DEFAULT_MAX_RESULT_LIMIT);
                queryTask.querySpec.options.add(QueryOption.TOP_RESULTS);
                queryTask.querySpec.resultLimit = DEFAULT_MAX_RESULT_LIMIT;
            } else if (queryTask.querySpec.resultLimit > DEFAULT_MAX_RESULT_LIMIT) {
                service.getHost().log(Level.WARNING,
                        "The result limit set on the query is too high: %s. Defaulting to %d",
                        Utils.toJson(queryTask), DEFAULT_MAX_RESULT_LIMIT);
                queryTask.querySpec.resultLimit = DEFAULT_MAX_RESULT_LIMIT;
            }
        }

        if (queryTask.documentExpirationTimeMicros == 0) {
            queryTask.documentExpirationTimeMicros = Utils.getNowMicrosUtc()
                    + QueryUtils.TEN_MINUTES_IN_MICROS;
        }

        URI buildUri = createInventoryUri(service.getHost(),
                ServiceUriPaths.CORE_LOCAL_QUERY_TASKS);

        return Operation
                .createPost(buildUri)
                .setBody(queryTask)
                .setConnectionSharing(true);
    }
}
