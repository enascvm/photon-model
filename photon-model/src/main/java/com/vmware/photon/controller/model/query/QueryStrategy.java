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

import static java.util.stream.Collector.Characteristics.IDENTITY_FINISH;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collector;

import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.ServiceDocument;

/**
 * Represents a query strategy, such as query-by-pages and query-top-results.
 */
public interface QueryStrategy<T extends ServiceDocument> {

    /**
     * Query for all documents which satisfy passed query.
     *
     * @param documentConsumer
     *            The callback interface of documents consumer.
     */
    DeferredResult<Void> queryDocuments(Consumer<T> documentConsumer);

    /**
     * Query for all document links which satisfy passed query.
     *
     * @param linkConsumer
     *            The callback interface of document links consumer.
     */
    DeferredResult<Void> queryLinks(Consumer<String> linkConsumer);

    /**
     * Performs a mutable reduction operation on the elements of this query using a
     * {@code Collector}. The method is inspired by {@code Stream#collect(Collector)}.
     *
     * @see #queryDocuments(Consumer)
     */
    @SuppressWarnings("unchecked")
    default <R, A> DeferredResult<R> collectDocuments(Collector<T, A, R> collector) {

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
     * {@code Collector}. The method is inspired by {@code Stream#collect(Collector)}.
     *
     * @see #queryLinks(Consumer)
     */
    @SuppressWarnings("unchecked")
    default <R, A> DeferredResult<R> collectLinks(Collector<String, A, R> collector) {

        A container = collector.supplier().get();
        BiConsumer<A, String> accumulator = collector.accumulator();

        return queryLinks(referrerLink -> {
            accumulator.accept(container, referrerLink);
        }).thenApply(ignore -> collector.characteristics().contains(IDENTITY_FINISH)
                ? (R) container
                : collector.finisher().apply(container));
    }
}