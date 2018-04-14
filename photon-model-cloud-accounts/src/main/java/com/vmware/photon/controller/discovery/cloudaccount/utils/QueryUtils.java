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

package com.vmware.photon.controller.discovery.cloudaccount.utils;

import static com.vmware.photon.controller.discovery.cloudaccount.utils.ClusterUtils.createClusterQueryUri;
import static com.vmware.photon.controller.discovery.cloudaccount.utils.ClusterUtils.createClusterUri;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import com.vmware.photon.controller.discovery.cloudaccount.utils.QueryHelper.QueryCompletionHandler;
import com.vmware.photon.controller.model.util.ClusterUtil.ServiceTypeCluster;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * The query utilities.
 */
public class QueryUtils {

    public static final int QUERY_RESULT_LIMIT = 50;
    public static final int MAX_QUERY_RESULT_LIMIT = 1000;
    public static final long TEN_MINUTES_IN_MICROS = TimeUnit.MINUTES.toMicros(10);

    /**
     * Executes the given query task and extracts the {@link ServiceDocumentQueryResult} from it.
     *
     * @param service The service executing the query task.
     * @param task The query task.
     * @param completionHandler The completion handler.
     */
    public static void startQueryTask(Service service, QueryTask task,
            QueryCompletionHandler<ServiceDocumentQueryResult> completionHandler) {
        startQueryTask(service, task, true, completionHandler);
    }

    /**
     * Executes the given query task and extracts the {@link ServiceDocumentQueryResult} from it.
     *
     * @param service The service executing the query task.
     * @param task The query task.
     * @param setIndexedMetadata Boolean to determine if the {@link QueryOption#INDEXED_METADATA}
     *                           query option should be set.
     * @param completionHandler The completion handler.
     */
    public static void startQueryTask(Service service, QueryTask task, boolean setIndexedMetadata,
            QueryCompletionHandler<ServiceDocumentQueryResult> completionHandler) {
        if (!task.querySpec.options.contains(QueryOption.INDEXED_METADATA) && setIndexedMetadata) {
            task.querySpec.options.add(QueryOption.INDEXED_METADATA);
        }
        if (task.documentExpirationTimeMicros == 0) {
            task.documentExpirationTimeMicros = Utils.getNowMicrosUtc() + QueryUtils.TEN_MINUTES_IN_MICROS;
        }
        Operation.createPost(UriUtils.buildUri(service.getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS))
                .setBody(task)
                .setConnectionSharing(true)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        completionHandler.handle(null, e);
                        return;
                    }

                    QueryTask body = o.getBody(QueryTask.class);
                    completionHandler.handle(body.results, null);
                })
                .sendWith(service);
    }

    private static void runQueryTaskWithSystemAuth(Service service, QueryTask task,
            ServiceTypeCluster cluster,
            QueryCompletionHandler<ServiceDocumentQueryResult> completionHandler) {
        OperationContext operationContext = OperationContext.getOperationContext();
        Operation operation = Operation
                .createPost(createClusterQueryUri(service.getHost(), cluster))
                .setBody(task)
                .setConnectionSharing(true)
                .setCompletion((o, e) -> {
                    OperationContext.restoreOperationContext(operationContext);

                    if (e != null) {
                        completionHandler.handle(null, e);
                        return;
                    }

                    QueryTask body = o.getBody(QueryTask.class);
                    completionHandler.handle(body.results, null);
                });
        service.setAuthorizationContext(operation, service.getSystemAuthorizationContext());
        service.sendRequest(operation);
    }

    /**
     * Executes the given query task in system context and extracts the
     * {@link ServiceDocumentQueryResult} from it.
     *
     * @param service The service executing the query task.
     * @param task The query task.
     * @param completionHandler The completion handler.
     */
    public static void startQueryTaskWithSystemAuth(Service service, QueryTask task,
            QueryCompletionHandler<ServiceDocumentQueryResult> completionHandler) {
        if (!task.querySpec.options.contains(QueryOption.INDEXED_METADATA)) {
            task.querySpec.options.add(QueryOption.INDEXED_METADATA);
        }
        runQueryTaskWithSystemAuth(service, task, null, completionHandler);
    }

    /**
     * Executes the given query task in system context and extracts the
     * {@link ServiceDocumentQueryResult} from it.
     * This query runs on the specified cluster.
     *
     * @param service The service executing the query task.
     * @param task The query task.
     * @param completionHandler The completion handler.
     * @param cluster The service cluster to run on.
     */
    public static void startQueryTaskWithSystemAuth(Service service, QueryTask task,
            ServiceTypeCluster cluster,
            QueryCompletionHandler<ServiceDocumentQueryResult> completionHandler) {
        runQueryTaskWithSystemAuth(service, task, cluster, completionHandler);
    }

    /**
     * Gets the page for the given page link.
     *
     * @param service The service executing the request.
     * @param pageLink The page link.
     * @param isSystemAuth Boolean to specify whether to use system auth.
     */
    public static DeferredResult<QueryTask> getPage(Service service, String pageLink,
            boolean isSystemAuth) {
        DeferredResult<QueryTask> deferred = new DeferredResult<>();

        Operation operation = Operation.createGet(service.getHost(), pageLink);

        OperationContext ctx = null;
        if (isSystemAuth) {
            ctx = OperationContext.getOperationContext();
            service.setAuthorizationContext(operation, service.getSystemAuthorizationContext());
        }

        OperationContext finalCtx = ctx;
        service.sendWithDeferredResult(operation, QueryTask.class)
                .whenComplete((qt, ex) -> {
                    if (isSystemAuth) {
                        OperationContext.restoreOperationContext(finalCtx);
                    }

                    if (ex != null) {
                        deferred.fail(ex);
                        return;
                    }

                    deferred.complete(qt);
                });
        return deferred;
    }

    /**
     * Extracts the result limit from {@link QuerySpecification}. The result limit is bounded to
     * 1000 defined by MAX_QUERY_RESULT_LIMIT.
     *
     * @param filter the query specification
     * @param systemPropertyOverride the system property to look for result limit.
     * @return The result limit
     */
    public static int getResultLimit(QuerySpecification filter, String systemPropertyOverride) {
        if (systemPropertyOverride == null) {
            systemPropertyOverride = "";
        }

        int resultLimit = (filter == null || filter.resultLimit == null) ?
                Integer.getInteger(systemPropertyOverride, QUERY_RESULT_LIMIT) : filter.resultLimit;

        if (resultLimit > MAX_QUERY_RESULT_LIMIT) {
            return MAX_QUERY_RESULT_LIMIT;
        }

        return resultLimit;
    }

    @FunctionalInterface
    public interface QueryTaskCompletionHandler {
        void handle(QueryTask queryTask);
    }

    @FunctionalInterface
    public interface UserIdentitySwitcher {
        void assumeIdentity (Operation op);
    }

    /**
     * Executes the given {@link QueryTask} from the context of
     * the passed in {@link Service} and waits for the completion
     * of the query. After completion it will perform the followup
     * actions specified in {@link QueryTaskCompletionHandler}.
     * If the followup actions needs a specific user access, then
     * it will be specified in {@link UserIdentitySwitcher}.
     *
     * @param service                caller service instance
     * @param query                  query task to be executed
     * @param queryCompletionHandler followup actions after query completion
     * @param identitySwitcher       provided if the followup actions require specific user
     */
    public static void asyncQueryTask(Service service, QueryTask query, ServiceTypeCluster clusterType,
            QueryTaskCompletionHandler queryCompletionHandler,
            UserIdentitySwitcher identitySwitcher) {

        Operation queryOp = Operation
                .createPost(createClusterQueryUri(service.getHost(), clusterType))
                .setBody(query)
                .setReferer(service.getUri())
                .setConnectionSharing(true)
                .setCompletion((postOp, postFailure) -> {
                    if (postFailure != null) {
                        service.getHost().log(Level.SEVERE, () -> Utils.toString(postFailure));
                        postOp.fail(postFailure);
                        return;
                    }

                    QueryTask postRsp = postOp.getBody(QueryTask.class);
                    if (postRsp.documentSelfLink == null) {
                        service.getHost().log(Level.SEVERE, "Failed to create query task.");
                        postOp.fail(Operation.STATUS_CODE_INTERNAL_ERROR);
                        return;
                    }

                    Operation get = Operation.createGet(createClusterUri(service.getHost(),
                            postRsp.documentSelfLink, clusterType));
                    get.setReferer(service.getUri())
                            .setCompletion((getOp, getFailure) -> {
                                if (getFailure != null) {
                                    service.getHost().log(Level.SEVERE, () -> Utils.toString(getFailure));
                                    getOp.fail(getFailure);
                                    return;
                                }

                                QueryTask getRsp = getOp.getBody(QueryTask.class);
                                if (getRsp.taskInfo == null || getRsp.taskInfo.stage == null ||
                                        getRsp.taskInfo.stage == TaskState.TaskStage.FAILED ||
                                        getRsp.taskInfo.stage == TaskState.TaskStage.CANCELLED) {
                                    service.getHost().log(Level.SEVERE, "Query task failed or cancelled.");
                                    getOp.fail(Operation.STATUS_CODE_INTERNAL_ERROR);
                                    return;
                                }
                                if (getRsp.taskInfo.stage == TaskState.TaskStage.CREATED ||
                                        getRsp.taskInfo.stage == TaskState.TaskStage.STARTED) {
                                    // query has not finished yet, keep polling
                                    service.getHost().log(Level.FINE, "Waiting for query task to finish.");
                                    service.getHost().sendRequest(get);
                                    getOp.complete();
                                    return;
                                }

                                // query task has FINISHED
                                if (getRsp.results == null || getRsp.results.documentCount == null) {
                                    service.getHost().log(Level.SEVERE, "No results returned from the query.");
                                    getOp.complete();
                                    return;
                                }

                                queryCompletionHandler.handle(getRsp);
                                getOp.complete();
                            });
                    if (identitySwitcher != null) {
                        identitySwitcher.assumeIdentity(get);
                    }
                    service.getHost().sendRequest(get);
                });
        if (identitySwitcher != null) {
            identitySwitcher.assumeIdentity(queryOp);
        }
        service.getHost().sendRequest(queryOp);
    }

    public static QueryTask.Query createQuery(String propertyName, String propertyValue,
            QueryTask.QueryTerm.MatchType matchType, QueryTask.Query.Occurance occurance) {
        QueryTask.Query query = QueryTask.Query.Builder.create().build();
        query.setTermPropertyName(propertyName);
        query.setCaseInsensitiveTermMatchValue(propertyValue);
        query.setTermMatchType(matchType);
        query.setOccurance(occurance);

        return query;
    }
}
