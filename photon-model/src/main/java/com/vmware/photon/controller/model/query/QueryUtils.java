/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.model.query;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Stream;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.util.PhotonModelUtils;
import com.vmware.photon.controller.model.util.AssertUtil;
import com.vmware.photon.controller.model.util.ClusterUtil;
import com.vmware.photon.controller.model.util.ClusterUtil.ServiceTypeCluster;
import com.vmware.photon.controller.model.util.ServiceEndpointLocator;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Query utilities.
 */
public class QueryUtils {

    public static final String MAX_RESULT_LIMIT_PROPERTY = UriPaths.PROPERTY_PREFIX
            + "query.maxResultLimit";
    public static final int DEFAULT_MAX_RESULT_LIMIT = 10000;
    public static final int MAX_RESULT_LIMIT = Integer
            .getInteger(MAX_RESULT_LIMIT_PROPERTY, DEFAULT_MAX_RESULT_LIMIT);

    public static final String DEFAULT_RESULT_LIMIT_PROPERTY = UriPaths.PROPERTY_PREFIX
            + "query.defaultResultLimit";
    public static final int DEFAULT_RESULT_LIMIT = Integer
            .getInteger(DEFAULT_RESULT_LIMIT_PROPERTY, 100);

    public static final String QUERY_TASK_RETRY_INTERVAL_MILLIS_PROPERTY = UriPaths.PROPERTY_PREFIX
            + "query.retryIntervalMillis";
    public static final long QUERY_TASK_RETRY_INTERVAL_MILLIS = Long
            .getLong(QUERY_TASK_RETRY_INTERVAL_MILLIS_PROPERTY, 300);

    public static final long MINUTE_IN_MICROS = TimeUnit.MINUTES.toMicros(1);
    public static final long TEN_MINUTES_IN_MICROS = TimeUnit.MINUTES.toMicros(10);

    /**
     * Executes the given query task on a Cluster.
     *
     * @param service
     *            The service executing the query task.
     * @param queryTask
     *            The query task.
     * @param cluster
     *            The cluster, the query runs against.
     */
    public static DeferredResult<QueryTask> startQueryTask(
            Service service,
            QueryTask queryTask,
            ServiceTypeCluster cluster) {
        return startQueryTask(service, queryTask, cluster, true);
    }

    /**
     * Executes the given query task on a Cluster.
     *
     * @param service
     *            The service executing the query task.
     * @param queryTask
     *            The query task.
     * @param cluster
     *            The cluster, the query runs against.
     * @param isLocal
     *            If true, use local query task. If false, use query task.
     */
    public static DeferredResult<QueryTask> startQueryTask(
            Service service,
            QueryTask queryTask,
            ServiceTypeCluster cluster,
            boolean isLocal) {

        Operation createQueryTaskOp = createQueryTaskOperation(service, queryTask, cluster,
                isLocal);

        return service.sendWithDeferredResult(createQueryTaskOp, QueryTask.class);
    }

    /**
     * Utility method to execute the given query task on inventory cluster.
     *
     * @param service
     *            The service executing the query task.
     * @param queryTask
     *            The query task.
     */
    public static DeferredResult<QueryTask> startInventoryQueryTask(
            Service service,
            QueryTask queryTask) {
        return startInventoryQueryTask(service, queryTask, true);
    }

    /**
     * Utility method to execute the given query task on inventory cluster.
     *
     * @param service
     *            The service executing the query task.
     * @param queryTask
     *            The query task.
     * @param isLocal
     *            If true, use local query task. If false, use query task.
     */
    public static DeferredResult<QueryTask> startInventoryQueryTask(
            Service service,
            QueryTask queryTask, boolean isLocal) {

        if (!queryTask.querySpec.options.contains(QueryOption.INDEXED_METADATA)) {
            queryTask.querySpec.options.add(QueryOption.INDEXED_METADATA);
        }

        return startQueryTask(service, queryTask, ServiceTypeCluster.INVENTORY_SERVICE, isLocal);
    }

    /**
     * Executes the given query task.
     *
     * @param service
     *            The service executing the query task.
     * @param queryTask
     *            The query task.
     */
    public static DeferredResult<QueryTask> startQueryTask(Service service, QueryTask queryTask) {

        if (!queryTask.querySpec.options.contains(QueryOption.INDEXED_METADATA)) {
            queryTask.querySpec.options.add(QueryOption.INDEXED_METADATA);
        }

        return startQueryTask(service, queryTask, null);
    }

    /**
     * Create a query queryTask operation with a URI
     *
     * @param service
     *            The service executing the query queryTask.
     * @param queryTask
     *            The query queryTask.
     * @param serviceEndpointLocator
     *            The Service Endpoint Locator.
     */
    public static Operation createQueryTaskOperation(
            Service service, QueryTask queryTask, ServiceEndpointLocator serviceEndpointLocator) {

        return createQueryTaskOperation(service, queryTask, serviceEndpointLocator, true);
    }

    /**
     * Create a query queryTask operation with a URI
     *
     * @param service
     *            The service executing the query queryTask.
     * @param queryTask
     *            The query queryTask.
     * @param serviceEndpointLocator
     *            The Service Endpoint Locator.
     * @param isLocal
     *            If true, use local query task. If false, use query task.
     */
    public static Operation createQueryTaskOperation(
            Service service, QueryTask queryTask, ServiceEndpointLocator serviceEndpointLocator,
            boolean isLocal) {

        return createQueryTaskOperation(service.getHost(), queryTask, serviceEndpointLocator,
                isLocal);
    }

    /**
     * Create a query queryTask operation with a URI
     *
     * @param serviceHost
     *            The service host executing the query queryTask.
     * @param queryTask
     *            The query queryTask.
     * @param serviceEndpointLocator
     *            The Service Endpoint Locator.
     */
    public static Operation createQueryTaskOperation(
            ServiceHost serviceHost,
            QueryTask queryTask,
            ServiceEndpointLocator serviceEndpointLocator) {
        return createQueryTaskOperation(serviceHost, queryTask, serviceEndpointLocator, true);
    }

    /**
     * Create a query queryTask operation with a URI
     *
     * @param serviceHost
     *            The service host executing the query queryTask.
     * @param queryTask
     *            The query queryTask.
     * @param serviceEndpointLocator
     *            The Service Endpoint Locator.
     * @param isLocal
     *            If true, use local query task. If false, use query task.
     */
    public static Operation createQueryTaskOperation(
            ServiceHost serviceHost,
            QueryTask queryTask,
            ServiceEndpointLocator serviceEndpointLocator,
            boolean isLocal) {

        boolean isCountQuery = queryTask.querySpec.options.contains(QueryOption.COUNT);

        if (!isCountQuery) {
            // We don't want any unbounded queries. By default, we cap the query results to 10000.
            if (queryTask.querySpec.resultLimit == null) {
                serviceHost.log(Level.WARNING,
                        "No result limit set on the query: %s. Defaulting to %d",
                        Utils.toJson(queryTask), MAX_RESULT_LIMIT);

                queryTask.querySpec.options.add(QueryOption.TOP_RESULTS);
                queryTask.querySpec.resultLimit = MAX_RESULT_LIMIT;

            } else if (queryTask.querySpec.resultLimit > MAX_RESULT_LIMIT) {
                serviceHost.log(Level.WARNING,
                        "The result limit set on the query is too high: %s. Defaulting to %d",
                        Utils.toJson(queryTask), MAX_RESULT_LIMIT);

                queryTask.querySpec.resultLimit = MAX_RESULT_LIMIT;
            }
        }

        if (queryTask.documentExpirationTimeMicros == 0) {
            queryTask.documentExpirationTimeMicros = Utils.getNowMicrosUtc()
                    + QueryUtils.TEN_MINUTES_IN_MICROS;
        }

        URI createQueryTaskUri = UriUtils.buildUri(
                ClusterUtil.getClusterUri(serviceHost, serviceEndpointLocator),
                isLocal ? ServiceUriPaths.CORE_LOCAL_QUERY_TASKS
                        : ServiceUriPaths.CORE_QUERY_TASKS);

        return Operation
                .createPost(createQueryTaskUri)
                .setBody(queryTask)
                .setConnectionSharing(true);
    }

    /**
     * Add {@code endpointLink} constraint to passed query builder depending on document class.
     */
    public static Query.Builder addEndpointLink(
            Query.Builder qBuilder,
            Class<? extends ServiceDocument> stateClass,
            String endpointLink) {

        if (endpointLink == null || endpointLink.isEmpty()) {
            return qBuilder;
        }

        if (PhotonModelUtils.ENDPOINT_LINK_EXPLICIT_SUPPORT.contains(stateClass)) {
            qBuilder.addClause(Query.Builder.create()
                    .addFieldClause(
                            PhotonModelConstants.FIELD_NAME_ENDPOINT_LINK,
                            endpointLink, Query.Occurance.SHOULD_OCCUR)
                    .addCollectionItemClause(
                            PhotonModelConstants.FIELD_NAME_ENDPOINT_LINKS,
                            endpointLink, Query.Occurance.SHOULD_OCCUR)
                    .build());
        } else if (PhotonModelUtils.ENDPOINT_LINK_CUSTOM_PROP_SUPPORT.contains(stateClass)) {

            qBuilder.addCompositeFieldClause(
                    ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                    PhotonModelConstants.CUSTOM_PROP_ENDPOINT_LINK,
                    endpointLink);
        }

        return qBuilder;
    }

    /**
     * Add {@code tenantLinks} constraint to passed query builder, if present.
     */
    public static Query.Builder addTenantLinks(Query.Builder qBuilder, List<String> tenantLinks) {
        if (tenantLinks != null) {
            // all given tenant links must be present in the document
            tenantLinks.forEach(link -> qBuilder
                    .addCollectionItemClause(ResourceState.FIELD_NAME_TENANT_LINKS, link));
        }
        return qBuilder;
    }

    /**
     * Query strategy template.
     *
     * @param <DESC>
     *            the type of descendant class
     * @param <T>
     *            the type of ServiceDocuments returned by this instance
     *
     * @see QueryTop
     * @see QueryByPages
     */
    public abstract static class QueryTemplate<DESC extends QueryTemplate<DESC, T>, T extends ServiceDocument>
            implements QueryStrategy<T> {

        /**
         * Wait\Block for query logic to complete.
         * <p>
         * <b>Note</b>: Use with care, for example within tests.
         *
         * @deprecated Use com.vmware.photon.controller.model.resources.util.PhotonModelUtils.waitToComplete(DeferredResult<T>)
         */
        @Deprecated
        public static <S> S waitToComplete(DeferredResult<S> dr) {
            return ((CompletableFuture<S>) dr.toCompletionStage()).join();
        }

        public final Class<T> documentClass;

        protected final ServiceHost host;
        protected final Query query;
        protected final List<String> tenantLinks;

        protected boolean isDirectQuery = true;
        protected URI referer;
        protected ServiceEndpointLocator serviceLocator;

        protected Level level = Level.FINE;
        protected String msg;

        /**
         * Default constructor.
         * <p>
         * Note: The client is off-loaded from setting the {@code tenantLinks} to the query.
         *
         * @param host
         *            The host initiating the query.
         * @param query
         *            The query criteria.
         * @param documentClass
         *            The class of documents to query for.
         * @param tenantLinks
         *            The scope of the query.
         */
        public QueryTemplate(ServiceHost host,
                Query query,
                Class<T> documentClass,
                List<String> tenantLinks) {

            this(host, query, documentClass, tenantLinks, null /* endpointLink */);
        }

        /**
         * Default constructor.
         * <p>
         * Note: The client is off-loaded from setting the {@code tenantLinks} and
         * {@code ednpointLink} to the query.
         *
         * @param host
         *            The host initiating the query.
         * @param query
         *            The query criteria.
         * @param documentClass
         *            The class of documents to query for.
         * @param tenantLinks
         *            The scope of the query.
         * @param endpointLink
         *            The scope of the query.
         */
        public QueryTemplate(ServiceHost host,
                Query query,
                Class<T> documentClass,
                List<String> tenantLinks,
                String endpointLink) {
            this(host, query, documentClass, tenantLinks, endpointLink, null /* computeHostLink */);
        }

        /**
         * Default constructor.
         * <p>
         * Note: The client is off-loaded from setting the {@code tenantLinks} and
         * {@code ednpointLink} to the query.
         *
         * @param host
         *            The host initiating the query.
         * @param query
         *            The query criteria.
         * @param documentClass
         *            The class of documents to query for.
         * @param tenantLinks
         *            The scope of the query.
         * @param endpointLink
         *            The scope of the query.
         * @param computeHostLink
         *            The scope of the query, based on computeHost on each resource.
         */
        public QueryTemplate(ServiceHost host,
                Query query,
                Class<T> documentClass,
                List<String> tenantLinks,
                String endpointLink,
                String computeHostLink) {

            this.host = host;
            this.documentClass = documentClass;
            this.tenantLinks = tenantLinks;

            setReferer(this.host.getUri());

            {
                // Wrap original query...
                Query.Builder qBuilder = Query.Builder.create().addClause(query);
                if (computeHostLink != null) {
                    qBuilder.addFieldClause(ResourceState.FIELD_NAME_COMPUTE_HOST_LINK,
                            computeHostLink);
                }
                // ...and extend with TENANT_LINKS
                QueryUtils.addTenantLinks(qBuilder, this.tenantLinks);
                // ...and extend with ENDPOINT_LINK
                QueryUtils.addEndpointLink(qBuilder, this.documentClass, endpointLink);

                this.query = qBuilder.build();
            }

            this.msg = getClass().getSimpleName() + " for " + documentClass.getSimpleName() + "s";
        }

        /**
         * Isolate all cases when this instance should be cast to DESC. For internal use only.
         */
        @SuppressWarnings("unchecked")
        private DESC self() {
            return (DESC) this;
        }

        /**
         * Get the maximum number of results to return. The value is set to
         * {@code QueryTask.Builder.setResultLimit(int)} by {@link #newQueryTaskBuilder()} while
         * creating the {@link QueryTask}.
         */
        protected abstract int getResultLimit();

        /**
         * Set custom referrer to use for REST operations.
         * <p>
         * Default value, if not set, is {@code host.getUri()}. Callers are recommended to set more
         * pertinent value for better traceability, e.g {@link Service#getUri()}.
         */
        public DESC setReferer(URI referer) {
            AssertUtil.assertNotNull(referer, "'referer' must be set.");
            this.referer = referer;

            return self();
        }

        /**
         * Set the cluster URI to query against.
         * <p>
         * Default value, if not set, is {@code null}.
         *
         * @see ClusterUtil#getClusterUri(ServiceHost, ServiceEndpointLocator)
         */
        public DESC setClusterType(ServiceEndpointLocator serviceLocator) {
            this.serviceLocator = serviceLocator;

            return self();
        }

        /**
         * Set whether to create synchronous or asynchronous query task.
         * <p>
         * Default value, if not set, is {@code true}.
         *
         * @see TaskState#isDirect
         */
        public DESC setDirect(boolean isDirect) {
            this.isDirectQuery = isDirect;

            return self();
        }

        /**
         * Each query strategy should provide its own {@link QueryTask} processing logic.
         *
         * @param queryTaskOp
         *            The QueryTask instance as returned by post-QueryTask-operation.
         */
        @SuppressWarnings("rawtypes")
        protected abstract DeferredResult<Void> handleQueryTask(
                Operation queryTaskOp, Consumer resultConsumer);

        /*
         * (non-Javadoc)
         *
         * @see
         * com.vmware.photon.controller.model.tasks.QueryStrategy#queryDocuments(java.util.function.
         * Consumer)
         */
        @Override
        public DeferredResult<Void> queryDocuments(Consumer<T> documentConsumer) {

            QueryTask.Builder queryTaskBuilder = newQueryTaskBuilder()
                    .addOption(QueryOption.EXPAND_CONTENT);

            return queryImpl(queryTaskBuilder, documentConsumer);
        }

        /*
         * (non-Javadoc)
         *
         * @see
         * com.vmware.photon.controller.model.tasks.QueryStrategy#queryLinks(java.util.function.
         * Consumer)
         */
        @Override
        public DeferredResult<Void> queryLinks(Consumer<String> linkConsumer) {

            QueryTask.Builder queryTaskBuilder = newQueryTaskBuilder();

            return queryImpl(queryTaskBuilder, linkConsumer);
        }

        /**
         * Descendants might override this method to customize the default query task build logic.
         * For example add option such as {@link QueryOption#TOP_RESULTS}.
         */
        protected QueryTask.Builder newQueryTaskBuilder() {
            QueryTask.Builder qtBuilder = this.isDirectQuery
                    ? QueryTask.Builder.createDirectTask()
                    : QueryTask.Builder.create();

            return qtBuilder
                    .setQuery(this.query)
                    .setResultLimit(getResultLimit());
        }

        @SuppressWarnings("rawtypes")
        private DeferredResult<Void> queryImpl(
                QueryTask.Builder queryTaskBuilder,
                Consumer resultConsumer) {

            // Prepare QueryTask
            final QueryTask queryTask = queryTaskBuilder.build();
            queryTask.tenantLinks = this.tenantLinks;

            // Prepare 'query-task' create/POST request
            final Operation createQueryTaskOp = createQueryTaskOperation(
                    this.host, queryTask, this.serviceLocator);

            createQueryTaskOp.setReferer(this.referer);

            this.host.log(this.level,
                    this.msg + ": STARTED with QT = " + Utils.toJsonHtml(queryTask));

            // Initiate the query
            return this.host.sendWithDeferredResult(createQueryTaskOp)
                    // Wait for QT to complete, if not direct
                    .thenCompose(this::waitForQueryTaskToComplete)
                    // Delegate to descendant to actually do QT processing
                    .thenCompose(qtOp -> handleQueryTask(qtOp, resultConsumer));
        }

        /**
         * Waits for query task to complete. It polls periodically for query task status.
         */
        private DeferredResult<Operation> waitForQueryTaskToComplete(Operation qtOp) {

            DeferredResult<Operation> completionDR = new DeferredResult<>();

            waitForQueryTaskToCompleteRecursively(qtOp, completionDR, new AtomicInteger(0));

            return completionDR;
        }

        /**
         * @param qtCompletionDR
         *            The DR returned to signal completion. It's internally completed upon query
         *            task completion.
         */
        private void waitForQueryTaskToCompleteRecursively(
                Operation qtOp,
                DeferredResult<Operation> qtCompletionDR,
                AtomicInteger retries) {

            final QueryTask qt = qtOp.getBody(QueryTask.class);

            if (TaskState.isFailed(qt.taskInfo)) {
                qtCompletionDR.fail(new IllegalStateException(qt.taskInfo.failure.message));
                return;
            }

            // If QT is 'direct' OR 'completed' then continue with QT results processing
            if (qt.taskInfo.isDirect || TaskState.isFinished(qt.taskInfo)) {
                qtCompletionDR.complete(qtOp);
                return;
            }

            // For any subsequent get-queryTask call we use the Host of previous Op!
            final Operation getQueryTaskOp = Operation
                    .createGet(UriUtils.buildUri(qtOp.getUri(), qt.documentSelfLink))
                    .setReferer(this.referer);

            this.host.sendWithDeferredResult(getQueryTaskOp).thenAccept(getQtOp -> {

                final QueryTask getQt = getQtOp.getBody(QueryTask.class);

                if (TaskState.isFinished(getQt.taskInfo)) {
                    // finally the task is done
                    qtCompletionDR.complete(getQtOp);
                } else {
                    this.host.log(this.level, "Query task [%s] not completed yet. Retry: %s",
                            getQt.documentSelfLink,
                            retries.incrementAndGet());

                    // check status again in a while
                    this.host.schedule(
                            () -> waitForQueryTaskToCompleteRecursively(
                                    getQtOp, qtCompletionDR, retries),
                            QUERY_TASK_RETRY_INTERVAL_MILLIS,
                            TimeUnit.MILLISECONDS);
                }
            });
        }

        /**
         * Helper method to be used by descendants to consume the QT results (in case of pagination
         * it's just current page).
         *
         * <p>
         * NOTE: the results are passed to the resultConsumer sequentially.
         */
        @SuppressWarnings({ "rawtypes", "unchecked" })
        protected void handleQueryTaskResults(QueryTask qt, Consumer resultConsumer) {

            this.host.log(this.level, "%s: PROCESS %s docs",
                    this.msg, qt.results.documentCount);

            if (qt.results.documentCount == 0) {
                return;
            }

            Stream resultsStream = Stream.empty();

            if (qt.querySpec.options.contains(QueryOption.EXPAND_CONTENT)) {
                // Get document STATEs as Stream<T>
                if (qt.results.documents != null && !qt.results.documents.isEmpty()) {
                    resultsStream = qt.results.documents
                            .values()
                            .stream()
                            .map(json -> Utils.fromJson(json, this.documentClass));
                }
            } else {
                // Get document LINKs as Stream<String>
                // NOTE: documentLinks is non-NULL, so it's safe to use without check.
                resultsStream = qt.results.documentLinks.stream();
            }

            consumeQueryTaskResults(resultsStream, resultConsumer);
        }

        /**
         * Encapsulate actual consume of QueryTask results stream. Might be overridden by
         * descendants for example to consume results in parallel or apply some before/after
         * behaviour. By default, results are passed sequentially to the consumer.
         */
        protected <M> void consumeQueryTaskResults(Stream<M> resultsStream, Consumer<M> resultConsumer) {
            // Delegate to passed callback one-by-one
            resultsStream.forEach(resultConsumer);
        }

    }

    /**
     * Query TOP documents/links which satisfy passed criteria.
     */
    public static class QueryTop<T extends ServiceDocument> extends QueryTemplate<QueryTop<T>, T> {

        private int maxResultsLimit = MAX_RESULT_LIMIT;

        public QueryTop(ServiceHost host,
                Query query,
                Class<T> documentClass,
                List<String> tenantLinks) {
            super(host, query, documentClass, tenantLinks);
        }

        public QueryTop(ServiceHost host,
                Query query,
                Class<T> documentClass,
                List<String> tenantLinks,
                String endpointLink) {
            super(host, query, documentClass, tenantLinks, endpointLink);
        }

        public QueryTop(ServiceHost host,
                Query query,
                Class<T> documentClass,
                List<String> tenantLinks,
                String endpointLink,
                String computeHostLink) {
            super(host, query, documentClass, tenantLinks, endpointLink, computeHostLink);
        }

        /**
         * Configure the number of top results.
         * <p>
         * If not explicitly specified use the system default.
         */
        public QueryTop<T> setMaxResultsLimit(int maxResultsLimit) {
            this.maxResultsLimit = maxResultsLimit;
            return this;
        }

        /**
         * Return the number of top results.
         */
        @Override
        protected int getResultLimit() {
            return this.maxResultsLimit;
        }

        /**
         * Add {@link QueryOption#TOP_RESULTS} since this is 'query TOP documents/links'.
         */
        @Override
        protected QueryTask.Builder newQueryTaskBuilder() {
            return super.newQueryTaskBuilder().addOption(QueryOption.TOP_RESULTS);
        }

        @Override
        @SuppressWarnings({ "rawtypes" })
        protected DeferredResult<Void> handleQueryTask(
                Operation queryTaskOp,
                Consumer resultConsumer) {

            return DeferredResult.completed(queryTaskOp.getBody(QueryTask.class))
                    // Handle TOP results
                    .thenAccept(qt -> handleQueryTaskResults(qt, resultConsumer));
        }
    }

    /**
     * Query all documents/links which satisfy passed query criteria. The results are processed
     * page-by-page.
     */
    public static class QueryByPages<T extends ServiceDocument>
            extends QueryTemplate<QueryByPages<T>, T> {

        /**
         * Value is {@code photon-model.QueryByPages.maxPageSize}.
         */
        public static final String PROPERTY_NAME_MAX_PAGE_SIZE = UriPaths.PROPERTY_PREFIX
                + QueryByPages.class.getSimpleName()
                + ".maxPageSize";
        public static final int DEFAULT_MAX_PAGE_SIZE = DEFAULT_RESULT_LIMIT;

        /**
         * Get system default max number of documents per page from
         * {@link #PROPERTY_NAME_MAX_PAGE_SIZE} property. If not specified fallback to
         * {@link #DEFAULT_MAX_PAGE_SIZE}.
         */
        public static int getDefaultMaxPageSize() {
            return Integer.getInteger(PROPERTY_NAME_MAX_PAGE_SIZE, DEFAULT_MAX_PAGE_SIZE);
        }

        private int maxPageSize = getDefaultMaxPageSize();

        public QueryByPages(ServiceHost host,
                Query query,
                Class<T> documentClass,
                List<String> tenantLinks) {

            super(host, query, documentClass, tenantLinks);
        }

        public QueryByPages(ServiceHost host,
                Query query,
                Class<T> documentClass,
                List<String> tenantLinks,
                String endpointLink) {

            super(host, query, documentClass, tenantLinks, endpointLink);
        }

        public QueryByPages(ServiceHost host,
                Query query,
                Class<T> documentClass,
                List<String> tenantLinks,
                String endpointLink,
                String computeHostLink) {

            super(host, query, documentClass, tenantLinks, endpointLink, computeHostLink);
        }

        /**
         * Configure the number of max documents per page.
         * <p>
         * If not explicitly specified use the system default as returned by
         * {@link #getDefaultMaxPageSize()}.
         */
        public QueryByPages<T> setMaxPageSize(int maxPageSize) {
            this.maxPageSize = maxPageSize;
            return this;
        }

        /**
         * Return the number of max documents per page.
         */
        @Override
        protected int getResultLimit() {
            return this.maxPageSize;
        }

        /**
         * Get the next page of results and pass them to resultConsumer.
         */
        @Override
        @SuppressWarnings({ "rawtypes" })
        protected DeferredResult<Void> handleQueryTask(
                Operation queryTaskOp,
                Consumer resultConsumer) {

            final QueryTask queryTask = queryTaskOp.getBody(QueryTask.class);

            final String pageLink = queryTask.results.nextPageLink;

            if (pageLink == null) {
                this.host.log(this.level, this.msg + ": FINISHED");

                return DeferredResult.completed((Void) null);
            }

            this.host.log(this.level, this.msg + ": PAGE %s", pageLink);

            // For any subsequent get-page call we use the Host of previous Op!
            Operation getQueryTaskOp = Operation
                    .createGet(UriUtils.buildUri(queryTaskOp.getUri(), pageLink))
                    .setReferer(this.referer);

            return this.host.sendWithDeferredResult(getQueryTaskOp)
                    // Handle CURRENT page of results
                    .thenApply(qtOp -> {
                        final QueryTask qt = qtOp.getBody(QueryTask.class);

                        handleQueryTaskResults(qt, resultConsumer);

                        return qtOp;
                    })
                    // Handle NEXT page of results
                    .thenCompose(qtOp -> handleQueryTask(qtOp, resultConsumer));
        }
    }

    /**
     * Query for all "referrer" documents which refer a "referred" document.
     *
     * @param referredDocumentSelfLink
     *            All referrers point to this document.
     * @param referrerClass
     *            The class of referrers that we want to query for.
     * @param referrerLinkFieldName
     *            The name of the "foreign key link" field of the referrer class.
     */
    public static Query queryForReferrers(
            String referredDocumentSelfLink,
            Class<? extends ServiceDocument> referrerClass,
            String referrerLinkFieldName) {

        return Query.Builder.create()
                .addKindFieldClause(referrerClass)
                .addFieldClause(referrerLinkFieldName, referredDocumentSelfLink)
                .build();
    }

    /**
     * Create a QueryTask to fetch an endpoint based on account ID for the given account type.
     */
    public static QueryTask createAccountQuery(String accountId, String accountType,
            List<String> tenantLinks) {
        Query.Builder qBuilder = Query.Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_TYPE, ComputeType.VM_HOST)
                .addCompositeFieldClause(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                        "__endpointType", accountType)
                .addCompositeFieldClause(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                        PhotonModelConstants.CLOUD_ACCOUNT_ID, accountId);

        if (tenantLinks != null) {
            qBuilder.addInCollectionItemClause(ComputeState.FIELD_NAME_TENANT_LINKS, tenantLinks);
        }

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .setQuery(qBuilder.build())
                .addOption(QueryOption.EXPAND_CONTENT)
                .addOption(QueryOption.TOP_RESULTS)
                .setResultLimit(100)
                .build();

        return queryTask;
    }

}