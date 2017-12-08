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

import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;

import static com.vmware.photon.controller.model.query.QueryUtils.QueryTemplate.waitToComplete;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionException;

import org.junit.Test;

import com.vmware.photon.controller.model.ModelUtils;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.query.QueryUtils.QueryByPages;
import com.vmware.photon.controller.model.query.QueryUtils.QueryTemplate;
import com.vmware.photon.controller.model.query.QueryUtils.QueryTop;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.services.common.QueryTask.Query;

/**
 * Tests for {@link QueryByPages} and {@link QueryTop} classes.
 */
public class QueryUtilsTest extends BaseModelTest {

    static {
        // Set the size of returned pages
        System.setProperty(QueryByPages.PROPERTY_NAME_MAX_PAGE_SIZE,
                Integer.toString(2));

        // Minimize retry interval
        System.setProperty(QueryUtils.QUERY_TASK_RETRY_INTERVAL_MILLIS_PROPERTY,
                Integer.toString(20));
    }

    @Test
    public void testQueryReferrers() throws Throwable {

        final ComputeDescription cd = ModelUtils.createComputeDescription(this, null, null);

        final Set<String> expected = new HashSet<>(Arrays.asList(
                ModelUtils.createCompute(this, cd).documentSelfLink,
                ModelUtils.createCompute(this, cd).documentSelfLink,
                ModelUtils.createCompute(this, cd).documentSelfLink,
                ModelUtils.createCompute(this, cd).documentSelfLink,
                ModelUtils.createCompute(this, cd).documentSelfLink));

        doTest(cd, expected, null);
        doTest(cd, expected, Arrays.asList());
        doTest(cd, expected, Arrays.asList("http://tenant"));
        doTest(cd, new HashSet<>(), Arrays.asList("http://tenant", "http://non-present-tenant"));
        doTest(cd, new HashSet<>(), Arrays.asList("http://non-present-tenant"));
    }

    @Test
    public void testQueryReferrers_noResults() throws Throwable {

        ComputeDescription cd = ModelUtils.createComputeDescription(this, null, null);

        Set<String> expected = Collections.emptySet();

        doTest(cd, expected, Collections.singletonList("http://tenant"));
    }

    private void doTest(ComputeDescription cd, Set<String> expected, List<String> tenantLinks) {

        Query queryForReferrers = QueryUtils.queryForReferrers(
                cd.documentSelfLink,
                ComputeState.class,
                ComputeState.FIELD_NAME_DESCRIPTION_LINK);

        // The classes under testing: QueryByPages and QueryTop
        List<QueryTemplate<?, ComputeState>> queryStrategies = Arrays.asList(
                new QueryByPages<>(
                        getHost(),
                        queryForReferrers,
                        ComputeState.class,
                        tenantLinks),
                new QueryTop<>(
                        getHost(),
                        queryForReferrers,
                        ComputeState.class,
                        tenantLinks));

        // Test collectDocuments/queryDocuments/collectLinks/queryLinks per strategy
        for (QueryTemplate<?, ComputeState> queryStrategy : queryStrategies) {

            for (boolean isDirect : Arrays.asList(true, false)) {

                final String msg = queryStrategy.getClass().getSimpleName() + ":" + isDirect;

                {
                    // Test collectDocuments, which internally also tests queryDocuments
                    DeferredResult<Set<String>> documentLinksDR = queryStrategy
                            .setDirect(isDirect)
                            .collectDocuments(mapping(cs -> cs.documentSelfLink, toSet()));

                    Set<String> actual = waitToComplete(documentLinksDR);

                    assertThat(msg, actual, equalTo(expected));
                }

                {
                    // Test collectLinks, which internally also tests queryLinks
                    DeferredResult<Set<String>> documentLinksDR = queryStrategy
                            .setDirect(isDirect)
                            .collectLinks(toSet());

                    Set<String> actual = waitToComplete(documentLinksDR);

                    assertThat(msg, actual, equalTo(expected));
                }
            }
        }
    }

    @Test
    public void testQueryReferrers_error() throws Throwable {

        ComputeDescription cd = ModelUtils.createComputeDescription(this, null, null);
        ModelUtils.createCompute(this, cd);
        ModelUtils.createCompute(this, cd);

        Query queryForReferrers = QueryUtils.queryForReferrers(
                cd.documentSelfLink,
                ComputeState.class,
                ComputeState.FIELD_NAME_DESCRIPTION_LINK);

        // The class under testing
        QueryStrategy<ComputeState> queryStrategy = new QueryByPages<>(
                getHost(),
                queryForReferrers,
                ComputeState.class,
                Collections.emptyList(),
                null /* endpointLink */);

        Set<String> actual = new HashSet<>();

        // The method under testing
        waitToComplete(
                queryStrategy.queryDocuments(cs -> {
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
