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

import java.net.URI;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.vmware.photon.controller.discovery.cloudaccount.CustomQueryPageForwardingService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.GraphQueryTask;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Utility class for querying.
 */
public class QueryHelper {

    /**
     * Interface to handle query completion results.
     *
     * @param <T> The type of the result object.
     */
    @FunctionalInterface
    public interface QueryCompletionHandler<T> {
        void handle(T result, Throwable failure);
    }

    /**
     * Query to lookup compute hosts.
     */
    public static Query getComputeParentQuery() {
        return Query.Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK, "*",
                        MatchType.WILDCARD,
                        Occurance.MUST_NOT_OCCUR)
                .build();
    }

    /**
     * Query to lookup compute hosts filtered by descriptionLinks.
     */
    public static Query getComputeParentByDescriptionsQuery(Set<String> descriptionLinks) {
        Query query = getComputeParentQuery();
        query.addBooleanClause(Builder.create()
                .addInClause(ComputeState.FIELD_NAME_DESCRIPTION_LINK, descriptionLinks)
                .build());
        return query;
    }

    /**
     * Query to lookup compute descriptions by given environment names.
     */
    public static Query getComputeDescriptionsByEnvironmentQuery(Set<String> environmentNames) {
        Query query = Builder.create()
                .addKindFieldClause(ComputeDescription.class)
                .build();

        if (environmentNames != null && !environmentNames.isEmpty()) {
            query.addBooleanClause(Builder.create()
                    .addInClause(ComputeDescription.FIELD_NAME_ENVIRONMENT_NAME, environmentNames)
                    .build());
        }

        return query;
    }

    /**
     * Returns query task to look up compute host.
     */
    public static QueryTask buildComputeHostWithDescriptionQueryTask(List<String> tenantLinks,
            int resultLimit) {
        Query query = getComputeParentQuery();

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .addOption(QueryOption.EXPAND_LINKS)
                .addOption(QueryOption.INDEXED_METADATA)
                .addOption(QueryOption.SELECT_LINKS)
                .addLinkTerm(ComputeState.FIELD_NAME_DESCRIPTION_LINK)
                .setQuery(query)
                .build();

        if (resultLimit > 0) {
            queryTask.querySpec.resultLimit = resultLimit;
        }

        queryTask.tenantLinks = tenantLinks;

        return queryTask;
    }

    /**
     * Returns query task to look up compute host by given description links.
     */
    public static QueryTask buildComputeParentByDescriptionsQueryTask(Set<String> descriptionLinks,
            List<String> tenantLinks, int resultLimit) {
        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .addOption(QueryOption.EXPAND_LINKS)
                .addOption(QueryOption.INDEXED_METADATA)
                .addOption(QueryOption.SELECT_LINKS)
                .addLinkTerm(ComputeState.FIELD_NAME_DESCRIPTION_LINK)
                .setQuery(getComputeParentByDescriptionsQuery(descriptionLinks))
                .setResultLimit(resultLimit)
                .build();

        queryTask.tenantLinks = tenantLinks;
        return queryTask;
    }

    /**
     * Returns query task to look up compute host.
     */
    public static QueryTask buildStageOneComputeParentQueryTask(List<String> tenantLinks) {
        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.SELECT_LINKS)
                .addOption(QueryOption.INDEXED_METADATA)
                .addLinkTerm(ComputeState.FIELD_NAME_DESCRIPTION_LINK)
                .setQuery(getComputeParentQuery())
                // We set a higher paging limit here to handle larger dataset for graph query.
                .setResultLimit(1000)
                .build();

        queryTask.tenantLinks = tenantLinks;
        return queryTask;
    }

    /**
     * Executes a graph query to get a filtered list of compute description links.
     */
    public static void findComputeDescriptions(Service service, String pageLink,
            List<String> tenantLinks, Set<String> environmentNames,
            QueryCompletionHandler<Set<String>> queryCompletionHandler) {
        findComputeDescriptionsHelper(
                service,
                pageLink,
                tenantLinks,
                environmentNames,
                new LinkedHashSet<>(), // store discovered description links here
                queryCompletionHandler);
    }

    /**
     * Recursive helper to get compute descriptions and filter them by environment names.
     */
    private static void findComputeDescriptionsHelper(Service service, String pageLink,
            List<String> tenantLinks, Set<String> environmentNames, Set<String> descriptionLinks,
            QueryCompletionHandler<Set<String>> queryCompletionHandler) {
        // base case
        if (pageLink == null) {
            queryCompletionHandler.handle(descriptionLinks, null);
            return;
        }

        // get initial query page results for compute hosts
        getPage(service, pageLink, (CompletionHandler) (o, e) -> {
            if (e != null) {
                queryCompletionHandler.handle(descriptionLinks, e);
                return;
            }

            QueryTask queryTaskResult = o.getBody(QueryTask.class);

            // handle last page
            if (queryTaskResult.results != null && queryTaskResult.results.documentCount == 0) {
                findComputeDescriptionsHelper(
                        service,
                        queryTaskResult.results.nextPageLink,
                        tenantLinks,
                        environmentNames,
                        descriptionLinks,
                        queryCompletionHandler);
                return;
            }

            // stage two query task
            QueryTask queryDescriptionsTask = QueryTask.Builder.createDirectTask()
                    .setQuery(getComputeDescriptionsByEnvironmentQuery(environmentNames))
                    .build();

            queryDescriptionsTask.tenantLinks = tenantLinks;

            // graph query
            GraphQueryTask graphQueryTask = GraphQueryTask.Builder.create(2)
                    .setDirect(true)
                    .addQueryStage(queryTaskResult)
                    .addQueryStage(queryDescriptionsTask)
                    .build();

            graphQueryTask.tenantLinks = new HashSet<>(tenantLinks);

            startGraphQueryTask(service, graphQueryTask, (op, ex) -> {
                if (ex != null) {
                    queryCompletionHandler.handle(descriptionLinks, ex);
                    return;
                }

                GraphQueryTask graphQueryTaskResult = op.getBody(GraphQueryTask.class);
                QueryTask stageTwoResults = graphQueryTaskResult.stages.get(1);

                if (stageTwoResults != null && stageTwoResults.results != null) {
                    descriptionLinks.addAll(stageTwoResults.results.documentLinks);
                }

                findComputeDescriptionsHelper(
                        service,
                        queryTaskResult.results.nextPageLink,
                        tenantLinks,
                        environmentNames,
                        descriptionLinks,
                        queryCompletionHandler);
            });
        });
    }

    /**
     * Executes the given query task and extracts the {@link ServiceDocumentQueryResult} from it.
     *
     * @param service The service executing the query task.
     * @param task    The query task.
     * @param success The success callback handler.
     * @param failure The failure callback handler.
     */
    public static void startQueryTask(Service service, QueryTask task,
            Consumer<ServiceDocumentQueryResult> success, Consumer<Throwable> failure) {
        Operation.createPost(UriUtils.buildUri(service.getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS))
                .setBody(task)
                .setConnectionSharing(true)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failure.accept(e);
                        return;
                    }

                    QueryTask body = o.getBody(QueryTask.class);
                    success.accept(body.results);
                })
                .sendWith(service);
    }

    /**
     * Executes the given graph query task.
     *
     * @param service           The service executing the query task.
     * @param task              The query task.
     * @param completionHandler The completion handler.
     */
    public static void startGraphQueryTask(Service service, GraphQueryTask task,
            CompletionHandler completionHandler) {
        Operation.createPost(
                UriUtils.buildUri(service.getHost(), ServiceUriPaths.CORE_GRAPH_QUERIES))
                .setBody(task)
                .setConnectionSharing(true)
                .setCompletion(completionHandler)
                .sendWith(service);
    }

    /**
     * Gets the page for the given page link and extract the {@link ServiceDocumentQueryResult} from
     * it.
     *
     * @param service  The service executing the request.
     * @param pageLink The page link.
     * @param success  The success callback handler.
     * @param failure  The failure callback handler.
     */
    public static void getPage(Service service, String pageLink,
            Consumer<ServiceDocumentQueryResult> success, Consumer<Throwable> failure) {
        Operation.createGet(service.getHost(), pageLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failure.accept(e);
                        return;
                    }

                    QueryTask body = o.getBody(QueryTask.class);
                    success.accept(body.results);
                }).sendWith(service);
    }

    /**
     * Gets the page for the given page link.
     *
     * @param service           The service executing the request.
     * @param pageLink          The page link.
     * @param completionHandler The completion handler.
     */
    public static void getPage(Service service, String pageLink,
            CompletionHandler completionHandler) {
        Operation.createGet(service.getHost(), pageLink)
                .setCompletion(completionHandler)
                .sendWith(service);
    }

    /**
     * Gets the page for the given page link.
     *
     * @param service           The service executing the request.
     * @param pageLink          The page link.
     * @param completionHandler The completion handler.
     */
    public static void getPage(Service service, String pageLink,
            QueryCompletionHandler<ServiceDocumentQueryResult> completionHandler) {
        Operation.createGet(service.getHost(), pageLink)
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

    /**
     * Starts a new stateless page service instance.
     *
     * @param service         The service executing the query task.
     * @param pageServiceLink The link to the service that needs to be started.
     * @param serviceInstance The service to be started
     * @param failure         The failure callback handler.
     * @return The query page link.
     */
    public static String startStatelessPageService(Service service, String pageServiceLink,
            StatelessService serviceInstance, long expirationTimeMicros,
            Consumer<Throwable> failure) {
        URI pageServiceURI = buildServiceURI(service, pageServiceLink);

        ServiceDocument postBody = new ServiceDocument();
        postBody.documentSelfLink = pageServiceURI.getPath();
        postBody.documentExpirationTimeMicros = expirationTimeMicros;

        Operation startPost = Operation
                .createPost(pageServiceURI)
                .setBody(postBody)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failure.accept(e);
                    }
                });
        service.getHost().startService(startPost, serviceInstance);

        URI queryPageURI = UriUtils
                .buildUri(service.getHost(), CustomQueryPageForwardingService.SELF_LINK);
        // Use a local host id, the id in the page link might be from a different cluster.
        String peer = service.getHost().getId();
        queryPageURI = UriUtils.extendUriWithQuery(queryPageURI,
                UriUtils.FORWARDING_URI_PARAM_NAME_PEER, peer,
                UriUtils.FORWARDING_URI_PARAM_NAME_PATH, pageServiceURI.getPath());

        return queryPageURI.getPath() + UriUtils.URI_QUERY_CHAR + queryPageURI.getQuery();
    }

    /**
     * Creates the URI of a service based on the passed service link.
     *
     * @param service     The service from which this method is invoked.
     * @param serviceLink The service link for which the URI is to be created.
     * @return The fully formed URI of the service to be invoked.
     */
    public static URI buildServiceURI(Service service, String serviceLink) {
        return UriUtils.buildUri(service.getHost(), UriUtils.buildUriPath(serviceLink,
                String.valueOf(Utils.getNowMicrosUtc())));

    }
}