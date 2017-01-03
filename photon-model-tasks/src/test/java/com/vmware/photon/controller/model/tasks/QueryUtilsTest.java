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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;

import static com.vmware.photon.controller.model.tasks.QueryUtils.QueryByPages.waitToComplete;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

import org.junit.Test;

import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.tasks.QueryUtils.QueryByPages;
import com.vmware.photon.controller.model.tasks.QueryUtils.QueryForReferrers;
import com.vmware.xenon.common.DeferredResult;

/**
 * Tests for {@link QueryUtils} class.
 */
public class QueryUtilsTest extends BaseModelTest {

    static {
        // Set the size of returned pages
        System.setProperty(QueryByPages.PROPERTY_NAME_MAX_PAGE_SIZE, Integer.toString(2));
    }

    /**
     * Test for {@link QueryForReferrers#collectDocuments(java.util.stream.Collector)} and
     * {@link QueryForReferrers#collectLinks(java.util.stream.Collector)}.
     *
     * <p>
     * It effectively tests {@link QueryForReferrers#queryDocuments(java.util.function.Consumer)},
     * {@link QueryForReferrers#queryLinks(java.util.function.Consumer)} and {@link QueryByPages}.
     */
    @Test
    public void testQueryReferrers() throws Throwable {

        final ComputeDescription cd = ModelUtils.createComputeDescription(this, null, null);

        final Set<String> expected = new HashSet<>(Arrays.asList(
                ModelUtils.createCompute(this, cd).documentSelfLink,
                ModelUtils.createCompute(this, cd).documentSelfLink,
                ModelUtils.createCompute(this, cd).documentSelfLink,
                ModelUtils.createCompute(this, cd).documentSelfLink,
                ModelUtils.createCompute(this, cd).documentSelfLink));

        // The class under testing
        QueryForReferrers<ComputeState> queryReferrers = new QueryForReferrers<>(
                getHost(),
                cd.documentSelfLink,
                ComputeState.class,
                ComputeState.FIELD_NAME_DESCRIPTION_LINK,
                Collections.emptyList());

        {
            // The method under testing
            DeferredResult<Set<String>> documentLinksDR = queryReferrers.collectDocuments(
                    Collectors.mapping(cs -> cs.documentSelfLink, Collectors.toSet()));
            Set<String> actual = waitToComplete(documentLinksDR);

            assertThat(actual, equalTo(expected));
        }

        {
            // The method under testing
            DeferredResult<Set<String>> linkDocumentsDR = queryReferrers
                    // Configure custom page size
                    .setMaxPageSize(3)
                    .collectLinks(Collectors.toSet());

            Set<String> actual = waitToComplete(linkDocumentsDR);
            assertThat(actual, equalTo(expected));
        }
    }

    @Test
    public void testQueryReferrers_noResults() throws Throwable {

        ComputeDescription cd = ModelUtils.createComputeDescription(this, null, null);

        Set<String> expected = Collections.emptySet();

        // The class under testing
        QueryForReferrers<ComputeState> queryReferrers = new QueryForReferrers<>(
                getHost(),
                cd.documentSelfLink,
                ComputeState.class,
                ComputeState.FIELD_NAME_DESCRIPTION_LINK,
                Collections.emptyList());

        {
            // The method under testing
            DeferredResult<Set<String>> documentLinksDR = queryReferrers.collectDocuments(
                    Collectors.mapping(cs -> cs.documentSelfLink, Collectors.toSet()));

            Set<String> actual = waitToComplete(documentLinksDR);

            assertThat(actual, equalTo(expected));
        }

        {
            // The method under testing
            DeferredResult<Set<String>> documentLinksDR = queryReferrers.collectLinks(Collectors.toSet());
            Set<String> actual = waitToComplete(documentLinksDR);

            assertThat(actual, equalTo(expected));
        }
    }

    @Test
    public void testQueryReferrers_error() throws Throwable {

        ComputeDescription cd = ModelUtils.createComputeDescription(this, null, null);
        ModelUtils.createCompute(this, cd);
        ModelUtils.createCompute(this, cd);

        // The class under testing
        QueryForReferrers<ComputeState> queryReferrers = new QueryForReferrers<>(
                getHost(),
                cd.documentSelfLink,
                ComputeState.class,
                ComputeState.FIELD_NAME_DESCRIPTION_LINK,
                Collections.emptyList());

        Set<String> actual = new HashSet<>();

        // The method under testing
        waitToComplete(
                queryReferrers.queryDocuments(cs -> {
                    if (actual.isEmpty()) {
                        actual.add(cs.documentSelfLink);
                    } else {
                        throw new RuntimeException("consume error");
                    }
                }).handle((v, e) -> {
                    // Validate processed docs
                    assertThat(actual.size(), equalTo(1));
                    // Validate exception propagation
                    assertThat(e, notNullValue());
                    assertThat(e, instanceOf(CompletionException.class));
                    assertThat(e.getCause().getMessage(), equalTo("consume error"));

                    // Recover from exception to prevent its propagation to outer test method
                    return (Void) null;
                }));
    }

}
