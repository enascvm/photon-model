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

package com.vmware.photon.controller.model.tasks.helpers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;

import java.util.Arrays;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionServiceTest;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeServiceTest;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.ResourcePoolServiceTest;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.common.test.VerificationHost;

/**
 * Tests for the {@link ResourcePoolQueryHelper} class.
 */
@RunWith(ResourcePoolQueryHelperTest.class)
@SuiteClasses({ ResourcePoolQueryHelperTest.NoResourcePoolsTest.class,
        ResourcePoolQueryHelperTest.QueryTest.class })
public class ResourcePoolQueryHelperTest extends Suite {

    public ResourcePoolQueryHelperTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    public static class NoResourcePoolsTest extends BaseModelTest {
        @Test
        public void testNoResourcePoolsNoComputes() {
            ResourcePoolQueryHelper helper = ResourcePoolQueryHelper.create(getHost());
            ResourcePoolQueryHelper.QueryResult qr = runHelperSynchronously(getHost(), helper);

            assertThat(qr.resourcesPools.size(), is(0));
            assertThat(qr.computesByLink.size(), is(0));
            assertThat(qr.rpLinksByComputeLink.size(), is(0));
        }

        public void testNoResourcePoolsWithComputes() throws Throwable {
            ComputeDescription cd = ComputeDescriptionServiceTest.createComputeDescription(this);
            ComputeState cs = createCompute(this, cd, null);

            ResourcePoolQueryHelper helper = ResourcePoolQueryHelper.create(getHost());
            ResourcePoolQueryHelper.QueryResult qr = runHelperSynchronously(getHost(), helper);

            assertThat(qr.resourcesPools.size(), is(0));
            assertThat(qr.computesByLink.size(), is(1));
            assertThat(qr.computesByLink.keySet(), contains(cs.documentSelfLink));
            assertThat(qr.computesByLink.get(cs.documentSelfLink), is(nullValue()));
            assertThat(qr.rpLinksByComputeLink.size(), is(1));
            assertThat(qr.rpLinksByComputeLink.get(cs.documentOwner), is(empty()));
        }
    }

    public static class QueryTest extends BaseModelTest {
        // references to the test model:
        //     rp1   rp2   rp3    (none)
        //     /\     |     |       |
        //    /  \    |     |       |
        //   c1  c2   c3  (none)    c4
        private ResourcePoolState rp1;
        private ResourcePoolState rp2;
        private ResourcePoolState rp3;
        private ComputeDescription cd1;
        private ComputeState c1;
        private ComputeState c2;
        private ComputeState c3;
        private ComputeState c4;

        @Before
        /**
         * Creates the test model.
         */
        public void createResources() throws Throwable {
            this.rp1 = createRp();
            this.rp2 = createRp();
            this.rp3 = createRp();

            this.cd1 = ComputeDescriptionServiceTest.createComputeDescription(this);

            this.c1 = createCompute(this, this.cd1, this.rp1);
            this.c2 = createCompute(this, this.cd1, this.rp1);
            this.c3 = createCompute(this, this.cd1, this.rp2);
            this.c4 = createCompute(this, this.cd1, null);
        }

        @After
        /**
         * Deletes the created test model resources.
         */
        public void deleteResources() throws Throwable {
            for (ServiceDocument resource : Arrays.asList(this.rp1, this.rp2, this.rp3, this.cd1,
                    this.c1, this.c2, this.c3, this.c4)) {
                deleteServiceSynchronously(resource.documentSelfLink);
            }
        }

        @Test
        public void testAllResourcePools() throws Throwable {
            ResourcePoolQueryHelper helper = ResourcePoolQueryHelper.create(getHost());
            ResourcePoolQueryHelper.QueryResult qr = runHelperSynchronously(getHost(), helper);

            assertThat(qr.resourcesPools.size(), is(3));
            assertThat(qr.resourcesPools.get(this.rp1.documentSelfLink).computeStateLinks,
                    hasSize(2));
            assertThat(qr.resourcesPools.get(this.rp2.documentSelfLink).computeStateLinks,
                    hasSize(1));
            assertThat(qr.resourcesPools.get(this.rp3.documentSelfLink).computeStateLinks,
                    is(empty()));

            assertThat(qr.computesByLink.size(), is(4));
            assertThat(qr.computesByLink.keySet(),
                    containsInAnyOrder(this.c1.documentSelfLink, this.c2.documentSelfLink,
                            this.c3.documentSelfLink, this.c4.documentSelfLink));
            assertThat(qr.computesByLink.get(this.c1.documentSelfLink), is(nullValue()));

            assertThat(qr.rpLinksByComputeLink.size(), is(4));
            assertThat(qr.rpLinksByComputeLink.get(this.c1.documentSelfLink),
                    contains(this.rp1.documentSelfLink));
            assertThat(qr.rpLinksByComputeLink.get(this.c2.documentSelfLink),
                    contains(this.rp1.documentSelfLink));
            assertThat(qr.rpLinksByComputeLink.get(this.c3.documentSelfLink),
                    contains(this.rp2.documentSelfLink));
            assertThat(qr.rpLinksByComputeLink.get(this.c4.documentSelfLink), is(empty()));
        }

        @Test
        public void testAllResourcePoolsWithComputesExpanded() throws Throwable {
            ResourcePoolQueryHelper helper = ResourcePoolQueryHelper.create(getHost());
            helper.setExpandComputes(true);
            ResourcePoolQueryHelper.QueryResult qr = runHelperSynchronously(getHost(), helper);

            assertThat(qr.resourcesPools.size(), is(3));

            assertThat(qr.computesByLink.size(), is(4));
            assertThat(qr.computesByLink.keySet(),
                    containsInAnyOrder(this.c1.documentSelfLink, this.c2.documentSelfLink,
                            this.c3.documentSelfLink, this.c4.documentSelfLink));
            assertThat(qr.computesByLink.get(this.c1.documentSelfLink), is(not(nullValue())));
            assertThat(qr.computesByLink.get(this.c2.documentSelfLink), is(not(nullValue())));
            assertThat(qr.computesByLink.get(this.c3.documentSelfLink), is(not(nullValue())));
            assertThat(qr.computesByLink.get(this.c4.documentSelfLink), is(not(nullValue())));
        }

        @Test
        public void testForResourcePool() throws Throwable {
            ResourcePoolQueryHelper helper = ResourcePoolQueryHelper.createForResourcePool(getHost(),
                    this.rp2.documentSelfLink);
            ResourcePoolQueryHelper.QueryResult qr = runHelperSynchronously(getHost(), helper);

            assertThat(qr.resourcesPools.size(), is(1));
            assertThat(qr.resourcesPools.get(this.rp2.documentSelfLink).computeStateLinks,
                    hasSize(1));

            assertThat(qr.computesByLink.size(), is(1));

            assertThat(qr.rpLinksByComputeLink.size(), is(1));
            assertThat(qr.rpLinksByComputeLink.get(this.c3.documentSelfLink),
                    contains(this.rp2.documentSelfLink));
        }

        @Test
        public void testForComputes() throws Throwable {
            ResourcePoolQueryHelper helper = ResourcePoolQueryHelper.createForComputes(getHost(),
                    Arrays.asList(this.c1.documentSelfLink, this.c4.documentSelfLink));
            ResourcePoolQueryHelper.QueryResult qr = runHelperSynchronously(getHost(), helper);

            assertThat(qr.resourcesPools.size(), is(1));
            assertThat(qr.resourcesPools.get(this.rp1.documentSelfLink).computeStateLinks,
                    hasSize(1));

            assertThat(qr.computesByLink.size(), is(2));

            assertThat(qr.rpLinksByComputeLink.size(), is(2));
            assertThat(qr.rpLinksByComputeLink.get(this.c1.documentSelfLink),
                    contains(this.rp1.documentSelfLink));
            assertThat(qr.rpLinksByComputeLink.get(this.c4.documentSelfLink), is(empty()));
        }

        @Test
        public void testWithAdditionalQuery() throws Throwable {
            ResourcePoolQueryHelper helper = ResourcePoolQueryHelper.create(getHost());
            helper.setAdditionalQueryClausesProvider(qb -> {
                qb.addInClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                        Arrays.asList(this.c1.documentSelfLink, this.c4.documentSelfLink));
            });
            ResourcePoolQueryHelper.QueryResult qr = runHelperSynchronously(getHost(), helper);

            assertThat(qr.resourcesPools.size(), is(3));
            assertThat(qr.resourcesPools.get(this.rp1.documentSelfLink).computeStateLinks,
                    hasSize(1));
            assertThat(qr.resourcesPools.get(this.rp2.documentSelfLink).computeStateLinks,
                    is(empty()));
            assertThat(qr.resourcesPools.get(this.rp3.documentSelfLink).computeStateLinks,
                    is(empty()));

            assertThat(qr.computesByLink.size(), is(2));

            assertThat(qr.rpLinksByComputeLink.size(), is(2));
            assertThat(qr.rpLinksByComputeLink.get(this.c1.documentSelfLink),
                    contains(this.rp1.documentSelfLink));
            assertThat(qr.rpLinksByComputeLink.get(this.c4.documentSelfLink), is(empty()));
        }

        /**
         * Creates a RP resource.
         */
        private ResourcePoolState createRp() throws Throwable {
            ResourcePoolState rp = ResourcePoolServiceTest.buildValidStartState();
            return postServiceSynchronously(ResourcePoolService.FACTORY_LINK, rp,
                    ResourcePoolState.class);
        }
    }

    /**
     * Synchronously executes the given ResourcePoolQueryHelper instance.
     */
    private static ResourcePoolQueryHelper.QueryResult runHelperSynchronously(
            VerificationHost host, ResourcePoolQueryHelper helper) {
        ResourcePoolQueryHelper.QueryResult[] resultHolder = { null };
        TestContext ctx = host.testCreate(1);
        helper.query(qr -> {
            if (qr.error != null) {
                ctx.failIteration(qr.error);
                return;
            }
            resultHolder[0] = qr;
            ctx.completeIteration();
        });
        host.testWait(ctx);

        return resultHolder[0];
    }

    /**
     * Creates a compute resource.
     */
    private static ComputeState createCompute(BaseModelTest test, ComputeDescription cd,
            ResourcePoolState rp)
            throws Throwable {
        ComputeState compute = ComputeServiceTest.buildValidStartState(cd);
        compute.resourcePoolLink = rp != null ? rp.documentSelfLink : null;
        return test.postServiceSynchronously(ComputeService.FACTORY_LINK, compute,
                ComputeState.class);
    }
}
