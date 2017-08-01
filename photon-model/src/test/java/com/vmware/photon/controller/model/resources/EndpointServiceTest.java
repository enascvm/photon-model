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

package com.vmware.photon.controller.model.resources;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;

import static com.vmware.photon.controller.model.resources.EndpointService.ENDPOINT_REMOVAL_REQUEST_REFERRER_NAME;
import static com.vmware.photon.controller.model.resources.EndpointService.ENDPOINT_REMOVAL_REQUEST_REFERRER_VALUE;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.TenantService;

/**
 * This class implements tests for the {@link NetworkService} class.
 */
@RunWith(EndpointServiceTest.class)
@SuiteClasses({ EndpointServiceTest.ConstructorTest.class,
        EndpointServiceTest.HandleStartTest.class,
        EndpointServiceTest.HandlePatchTest.class,
        EndpointServiceTest.HandlePatchValidationsTest.class,
        EndpointServiceTest.HandleDeleteTest.class,
        EndpointServiceTest.QueryTest.class })
public class EndpointServiceTest extends Suite {

    public EndpointServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    private static EndpointService.EndpointState buildValidStartState() {
        EndpointService.EndpointState endpointState = new EndpointService.EndpointState();
        endpointState.id = UUID.randomUUID().toString();
        endpointState.endpointProperties = new HashMap<>();
        endpointState.endpointProperties.put("regionId", "us-west-1");
        endpointState.endpointProperties.put("privateKeyId", "privateKeyId");
        endpointState.endpointProperties.put("privateKey", "privateKey");
        endpointState.endpointType = "aws";
        endpointState.name = "aws-test-endpoint";
        endpointState.desc = "aws-test-endpoint description";
        endpointState.tenantLinks = new ArrayList<>();
        endpointState.tenantLinks.add("tenant-linkA");
        endpointState.authCredentialsLink = "http://authCredentialsLink";
        endpointState.resourcePoolLink = "http://resourcePoolLink";
        return endpointState;
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {
        private EndpointService endpointService = new EndpointService();

        @Before
        public void setupTest() {
            this.endpointService = new EndpointService();
        }

        @Test
        public void testServiceOptions() {
            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.CONCURRENT_GET_HANDLING,
                    Service.ServiceOption.PERSISTENCE,
                    Service.ServiceOption.REPLICATION,
                    Service.ServiceOption.OWNER_SELECTION,
                    Service.ServiceOption.IDEMPOTENT_POST);
            assertThat(this.endpointService.getOptions(), is(expected));
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleStartTest extends BaseModelTest {
        @Test
        public void testValidStartState() throws Throwable {
            EndpointService.EndpointState startState = buildValidStartState();
            EndpointService.EndpointState returnState = postServiceSynchronously(
                    EndpointService.FACTORY_LINK,
                    startState, EndpointService.EndpointState.class);
            assertNotNull(returnState);
            assertThat(returnState.id, is(startState.id));
            assertThat(returnState.name, is(startState.name));
            assertThat(returnState.desc, is(startState.desc));
            assertThat(returnState.tenantLinks.get(0),
                    is(startState.tenantLinks.get(0)));
            assertThat(returnState.authCredentialsLink,
                    is(startState.authCredentialsLink));
            assertThat(returnState.resourcePoolLink,
                    is(startState.resourcePoolLink));
        }

        @Test
        public void testDuplicatePost() throws Throwable {
            EndpointService.EndpointState startState = buildValidStartState();
            EndpointService.EndpointState returnState = postServiceSynchronously(
                    EndpointService.FACTORY_LINK,
                    startState, EndpointService.EndpointState.class);

            assertNotNull(returnState);
            assertThat(returnState.name, is(startState.name));
            startState.name = "new-name";
            returnState = postServiceSynchronously(EndpointService.FACTORY_LINK,
                    startState, EndpointService.EndpointState.class);
            assertThat(returnState.name, is(startState.name));
        }
    }

    /**
     * This class implements tests for the handlePatch method.
     * Updates to regionIds of existing endpoints are not supported.
     */
    public static class HandlePatchValidationsTest extends BaseModelTest {
        @Test(expected = UnsupportedOperationException.class)
        public void testPatch() throws Throwable {
            EndpointService.EndpointState startState = buildValidStartState();
            EndpointService.EndpointState returnState = postServiceSynchronously(
                    EndpointService.FACTORY_LINK,
                    startState, EndpointService.EndpointState.class);

            // this should raise a validation exception.
            EndpointService.EndpointState patchState = new EndpointService.EndpointState();
            patchState.id = UUID.randomUUID().toString();
            patchState.endpointProperties = new HashMap<>();
            patchState.endpointProperties.put("regionId", "us-east1");
            patchState.endpointProperties.put("privateKeyId", "privateKeyId");
            patchState.endpointProperties.put("privateKey", "privateKey");
            patchState.endpointType = "aws";
            patchState.name = "aws-test-endpoint";
            patchState.name = "aws-test-endpoint description";
            patchState.tenantLinks = new ArrayList<>();
            patchState.tenantLinks.add("tenant-linkA");
            patchState.authCredentialsLink = "http://authCredentialsLink";
            patchState.resourcePoolLink = "http://resourcePoolLink";
            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);
        }
    }

    /**
     * This class implements tests for the handlePatch method.
     * Updates to regionIds of existing endpoints are not supported.
     */
    public static class HandlePatchTest extends BaseModelTest {
        @Test
        public void testPatch() throws Throwable {
            EndpointService.EndpointState startState = buildValidStartState();
            EndpointService.EndpointState returnState = postServiceSynchronously(
                    EndpointService.FACTORY_LINK,
                    startState, EndpointService.EndpointState.class);
            EndpointService.EndpointState patchState = new EndpointService.EndpointState();
            patchState.id = UUID.randomUUID().toString();
            patchState.name = "aws-test-endpoint-updatedName";
            patchState.desc = "aws-test-endpoint description updated";
            patchState.tenantLinks = new ArrayList<>();
            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);
            EndpointService.EndpointState updatedReturnState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    EndpointService.EndpointState.class);

            assertThat(updatedReturnState.name, is(patchState.name));
            assertThat(updatedReturnState.desc, is(patchState.desc));
        }
    }

    public static class HandleDeleteTest extends BaseModelTest {
        @Test
        public void testDeleteRequestHeader() throws Throwable {
            EndpointService.EndpointState startState = buildValidStartState();
            EndpointService.EndpointState returnState = postServiceSynchronously(
                    EndpointService.FACTORY_LINK,
                    startState, EndpointState.class);

            Operation deleteOperation = Operation
                    .createDelete(UriUtils.buildUri(this.host, returnState.documentSelfLink));
            // custom header is not set in delete request, expect failure
            this.host.sendAndWaitExpectFailure(deleteOperation);

            // set custom header
            deleteOperation.addRequestHeader(ENDPOINT_REMOVAL_REQUEST_REFERRER_NAME,
                    ENDPOINT_REMOVAL_REQUEST_REFERRER_VALUE);
            // custom header is set in delete request, expect success
            this.host.sendAndWaitExpectSuccess(deleteOperation);
        }

        @Test
        public void testDeletePragmaDirective() throws Throwable {
            EndpointService.EndpointState startState = buildValidStartState();
            EndpointService.EndpointState returnState = postServiceSynchronously(
                    EndpointService.FACTORY_LINK,
                    startState, EndpointState.class);

            Operation deleteOperation = Operation
                    .createDelete(UriUtils.buildUri(this.host, returnState.documentSelfLink));
            // custom header is not set in delete request, expect failure
            this.host.sendAndWaitExpectFailure(deleteOperation);

            // set custom header
            deleteOperation.addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FROM_MIGRATION_TASK);
            // custom header is set in delete request, expect success
            this.host.sendAndWaitExpectSuccess(deleteOperation);

        }
    }

    /**
     * This class implements tests for query.
     */
    public static class QueryTest extends BaseModelTest {
        @Test
        public void testTenantLinksQuery() throws Throwable {
            EndpointService.EndpointState endpointState = buildValidStartState();
            URI tenantUri = UriUtils.buildFactoryUri(this.host, TenantService.class);
            endpointState.tenantLinks = new ArrayList<>();
            endpointState.tenantLinks.add(UriUtils.buildUriPath(
                    tenantUri.getPath(), "tenantA"));
            EndpointService.EndpointState startState = postServiceSynchronously(
                    EndpointService.FACTORY_LINK,
                    endpointState, EndpointService.EndpointState.class);

            String kind = Utils.buildKind(EndpointService.EndpointState.class);
            String propertyName = QueryTask.QuerySpecification
                    .buildCollectionItemName(ResourceState.FIELD_NAME_TENANT_LINKS);

            QueryTask q = createDirectQueryTask(kind, propertyName,
                    endpointState.tenantLinks.get(0));
            q = querySynchronously(q);
            assertNotNull(q.results.documentLinks);
            assertThat(q.results.documentCount, is(1L));
            assertThat(q.results.documentLinks.get(0),
                    is(startState.documentSelfLink));
        }
    }

}
