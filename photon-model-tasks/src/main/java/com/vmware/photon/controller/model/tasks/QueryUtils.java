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

import static java.util.stream.Collector.Characteristics.IDENTITY_FINISH;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collector;
import java.util.stream.Stream;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
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
    private static final int MAX_RESULT_LIMIT = Integer
            .getInteger(MAX_RESULT_LIMIT_PROPERTY, DEFAULT_MAX_RESULT_LIMIT);

    /**
     * Executes the given query task.
     *
     * @param service   The service executing the query task.
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
                Operation.createPost(service, ServiceUriPaths.CORE_QUERY_TASKS).setBody(queryTask)
                        .setConnectionSharing(true),
                QueryTask.class);
    }

    /**
     * Query for all documents which satisfy passed query criteria. The results are
     * processed/handled page-by-page.
     */
    public static class QueryByPages<T extends ServiceDocument> {

        /**
         * Wait\Block for query logic to complete.
         * <p>
         * <b>Note</b>: Use with care, for example within tests.
         */
        public static <S> S waitToComplete(DeferredResult<S> dr) {
            return ((CompletableFuture<S>) dr.toCompletionStage()).join();
        }

        /**
         * Value is {@code photon-model.QueryByPages.maxPageSize}.
         */
        public static final String PROPERTY_NAME_MAX_PAGE_SIZE = UriPaths.PROPERTY_PREFIX
                + QueryByPages.class.getSimpleName()
                + ".maxPageSize";
        protected static final int DEFAULT_MAX_PAGE_SIZE = 100;

        /**
         * Get system default max number of documents per page from
         * {@link #PROPERTY_NAME_MAX_PAGE_SIZE} property. If not specified fallback to
         * {@value #DEFAULT_MAX_PAGE_SIZE}.
         */
        public static int getDefaultMaxPageSize() {
            return Integer.getInteger(PROPERTY_NAME_MAX_PAGE_SIZE, DEFAULT_MAX_PAGE_SIZE);
        }

        protected final ServiceHost host;
        protected final Query query;
        protected final Class<T> documentClass;
        protected final List<String> tenantLinks;

        protected Level level = Level.INFO;
        protected String msg;

        protected int maxPageSize = getDefaultMaxPageSize();

        /**
         * Default constructor.
         *
         * @param host          The host initiating the query.
         * @param documentClass The class of documents to query for.
         * @param tenantLinks
         */
        public QueryByPages(ServiceHost host,
                Query query,
                Class<T> documentClass,
                List<String> tenantLinks) {

            this.host = host;
            this.documentClass = documentClass;
            this.query = query;
            this.tenantLinks = tenantLinks;

            this.msg = "Query for " + documentClass.getSimpleName() + "s";
        }

        /**
         * Configure the number of max documents per page.
         * <p>
         * <p>
         * If not explicitly specified use the system default as returned by
         * {@link #getDefaultMaxPageSize()}.
         */
        public QueryByPages<T> setMaxPageSize(int maxPageSize) {
            this.maxPageSize = maxPageSize;
            return this;
        }

        /**
         * Query for all documents which satisfy passed query.
         *
         * @param documentConsumer The callback interface of documents consumer.
         */
        public DeferredResult<Void> queryDocuments(Consumer<T> documentConsumer) {

            QueryTask queryTask = QueryTask.Builder.createDirectTask()
                    .setQuery(this.query)
                    .addOption(QueryOption.EXPAND_CONTENT)
                    .setResultLimit(this.maxPageSize)
                    .build();
            queryTask.tenantLinks = this.tenantLinks;

            return queryImpl(queryTask, documentConsumer);
        }

        /**
         * Query for all document links which satisfy passed query.
         *
         * @param linkConsumer The callback interface of document links consumer.
         */
        public DeferredResult<Void> queryLinks(Consumer<String> linkConsumer) {

            QueryTask queryTask = QueryTask.Builder.createDirectTask()
                    .setQuery(this.query)
                    .setResultLimit(this.maxPageSize)
                    .build();
            queryTask.tenantLinks = this.tenantLinks;

            return queryImpl(queryTask, linkConsumer);
        }

        /**
         * Performs a mutable reduction operation on the elements of this query using a
         * {@code Collector}. The method is inspired by {@link Stream#collect(Collector)}.
         *
         * @see {@link #queryDocuments(Consumer)}
         */
        @SuppressWarnings("unchecked")
        public <R, A> DeferredResult<R> collectDocuments(Collector<T, A, R> collector) {

            A container = collector.supplier().get();
            BiConsumer<A, T> accumulator = collector.accumulator();

            return queryDocuments(referrerDoc -> {
                accumulator.accept(container, referrerDoc);
            }).thenApply(ignore -> collector.characteristics().contains(IDENTITY_FINISH)
                    ? (R) container
                    : collector.finisher().apply(container));
        }

        /**
         * Performs a mutable reduction operation on the elements of this query using a
         * {@code Collector}. The method is inspired by {@link Stream#collect(Collector)}.
         *
         * @see {@link #queryLinks(Consumer)}
         */
        @SuppressWarnings("unchecked")
        public <R, A> DeferredResult<R> collectLinks(Collector<String, A, R> collector) {

            A container = collector.supplier().get();
            BiConsumer<A, String> accumulator = collector.accumulator();

            return queryLinks(referrerLink -> {
                accumulator.accept(container, referrerLink);
            }).thenApply(ignore -> collector.characteristics().contains(IDENTITY_FINISH)
                    ? (R) container
                    : collector.finisher().apply(container));
        }

        @SuppressWarnings("rawtypes")
        private DeferredResult<Void> queryImpl(QueryTask queryTask, Consumer resultConsumer) {

            Operation queryTaskOp = Operation
                    .createPost(this.host, ServiceUriPaths.CORE_QUERY_TASKS)
                    .setReferer(getClass().getSimpleName())
                    .setBody(queryTask);

            this.host.log(this.level, this.msg + ": STARTED");

            return this.host
                    .sendWithDeferredResult(queryTaskOp, QueryTask.class)
                    .thenCompose(qt -> handlePages(qt, resultConsumer));
        }

        /**
         * Get the next page of results and pass them to resultConsumer.
         */
        @SuppressWarnings({ "rawtypes", "unchecked" })
        // NOTE: If needed might enhance to stop page iteration.
        private DeferredResult<Void> handlePages(
                QueryTask queryTask,
                Consumer resultConsumer) {

            final String pageLink = queryTask.results.nextPageLink;

            if (pageLink == null) {
                this.host.log(this.level, this.msg + ": FINISHED");

                return DeferredResult.completed(null);
            }

            this.host.log(this.level, this.msg + ": PAGE %s", pageLink);

            Operation queryTaskOp = Operation
                    .createGet(this.host, pageLink)
                    .setReferer(getClass().getSimpleName());

            return this.host
                    .sendWithDeferredResult(queryTaskOp, QueryTask.class)
                    .thenApply(qt -> {
                        this.host.log(this.level, this.msg + ": PROCESS %s docs",
                                qt.results.documentCount);
                        return qt;
                    })
                    // Handle current page of results
                    .thenApply(qt -> {
                        if (qt.results.documentCount == 0) {
                            return qt;
                        }
                        final Stream resultsStream;
                        if (qt.results.documents != null && !qt.results.documents.isEmpty()) {
                            // Get document states as Stream<T>, if QueryOption.EXPAND_CONTENT
                            resultsStream = qt.results.documents.values().stream()
                                    .map(json -> Utils.fromJson(json, this.documentClass));
                        } else {
                            // Get document links as Stream<String>
                            resultsStream = qt.results.documentLinks.stream();
                        }
                        // Delegate to passed callback one-by-one
                        resultsStream.forEach(resultConsumer);
                        return qt;
                    })
                    // Handle next page of results
                    .thenCompose(qt -> handlePages(qt, resultConsumer));
        }
    }

    /**
     * Query for all "referrer" documents which refer a "referred" document.
     */
    public static class QueryForReferrers<T extends ServiceDocument> extends QueryByPages<T> {

        /**
         * Default constructor.
         *
         * @param host                     The host initiating the query.
         * @param referredDocumentSelfLink All referrers point to this document.
         * @param referrerClass            The class of referrers that we want to query for.
         * @param referrerLinkFieldName    The name of the "foreign key link" field of the referrer class.
         * @param tenantLinks
         */
        public QueryForReferrers(ServiceHost host,
                String referredDocumentSelfLink,
                Class<T> referrerClass,
                String referrerLinkFieldName,
                List<String> tenantLinks) {

            super(host,
                    Query.Builder.create()
                            .addKindFieldClause(referrerClass)
                            .addFieldClause(referrerLinkFieldName, referredDocumentSelfLink)
                            .build(),
                    referrerClass,
                    tenantLinks);

            this.msg = this.msg + " referring " + referredDocumentSelfLink;
        }
    }

}