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
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEYID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEY_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.REGION_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.ZONE_KEY;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService.EndpointAllocationTaskState;
import com.vmware.photon.controller.model.tasks.MockAdapter.MockSuccessEndpointAdapter;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

/**
 * This class implements tests for the {@link EndpointAllocationTaskService} class.
 */
@RunWith(EndpointAllocationTaskServiceTest.class)
@SuiteClasses({
        EndpointAllocationTaskServiceTest.ConstructorTest.class,
        EndpointAllocationTaskServiceTest.HandleStartTest.class,
        EndpointAllocationTaskServiceTest.EndToEndTest.class })
public class EndpointAllocationTaskServiceTest extends Suite {

    public EndpointAllocationTaskServiceTest(Class<?> klass,
            RunnerBuilder builder) throws InitializationError {
        super(klass, builder);
    }

    private static void startFactoryServices(BaseModelTest test) throws Throwable {
        PhotonModelTaskServices.startServices(test.getHost());
        MockAdapter.startFactories(test);
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {

        private EndpointAllocationTaskService endpointAllocationTaskService;

        @Before
        public void setUpTest() {
            this.endpointAllocationTaskService = new EndpointAllocationTaskService();
        }

        @Test
        public void testServiceOptions() {

            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.REPLICATION,
                    Service.ServiceOption.OWNER_SELECTION,
                    Service.ServiceOption.INSTRUMENTATION);

            assertThat(this.endpointAllocationTaskService.getOptions(),
                    is(expected));
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleStartTest extends BaseModelTest {

        @Override
        protected void startRequiredServices() throws Throwable {
            EndpointAllocationTaskServiceTest.startFactoryServices(this);
            super.startRequiredServices();
        }

        @Test
        public void testMissingEndpointState() throws Throwable {
            EndpointAllocationTaskState startState = createEndpointAllocationRequest(null);

            this.postServiceSynchronously(
                    EndpointAllocationTaskService.FACTORY_LINK, startState,
                    EndpointAllocationTaskState.class,
                    IllegalArgumentException.class);
        }

        @Test
        public void testMissingEndpointType() throws Throwable {
            EndpointState endpoint = createEndpointState();
            endpoint.endpointType = null;
            EndpointAllocationTaskState startState = createEndpointAllocationRequest(endpoint);

            this.postServiceSynchronously(
                    EndpointAllocationTaskService.FACTORY_LINK, startState,
                    EndpointAllocationTaskState.class,
                    IllegalArgumentException.class);
        }

    }

    /**
     * This class implements EndToEnd tests for {@link EndpointAllocationTaskService}.
     */
    public static class EndToEndTest extends BaseModelTest {
        @Override
        protected void startRequiredServices() throws Throwable {
            EndpointAllocationTaskServiceTest.startFactoryServices(this);
            super.startRequiredServices();
        }

        @Test
        public void testSuccess() throws Throwable {
            EndpointState endpoint = createEndpointState();
            EndpointAllocationTaskState startState = createEndpointAllocationRequest(endpoint);
            startState.adapterReference = UriUtils.buildUri(getHost(),
                    MockSuccessEndpointAdapter.SELF_LINK);

            EndpointAllocationTaskState returnState = this
                    .postServiceSynchronously(
                            EndpointAllocationTaskService.FACTORY_LINK,
                            startState, EndpointAllocationTaskState.class);

            EndpointAllocationTaskState completeState = this
                    .waitForServiceState(
                            EndpointAllocationTaskState.class,
                            returnState.documentSelfLink,
                            state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                                    .ordinal());

            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FINISHED));
            assertNotNull(completeState.endpointState);
            assertNotNull(completeState.endpointState.computeLink);
            assertNotNull(completeState.endpointState.computeDescriptionLink);
            ComputeDescription endpointDescr = getServiceSynchronously(
                    completeState.endpointState.computeDescriptionLink, ComputeDescription.class);
            assertNull(endpointDescr.zoneId);

            ResourcePoolState poolState = getServiceSynchronously(
                    completeState.endpointState.resourcePoolLink, ResourcePoolState.class);
            assertNotNull(poolState.customProperties);
            assertEquals(completeState.endpointState.documentSelfLink,
                    poolState.customProperties.get(ComputeProperties.ENDPOINT_LINK_PROP_NAME));
        }

        @Test
        public void testSuccessWithExplicitZoneSpecified() throws Throwable {
            EndpointState endpoint = createEndpointState();
            endpoint.endpointProperties.put(ZONE_KEY, "test-zoneId");
            EndpointAllocationTaskState startState = createEndpointAllocationRequest(endpoint);
            startState.adapterReference = UriUtils.buildUri(getHost(),
                    MockSuccessEndpointAdapter.SELF_LINK);

            EndpointAllocationTaskState returnState = this
                    .postServiceSynchronously(
                            EndpointAllocationTaskService.FACTORY_LINK,
                            startState, EndpointAllocationTaskState.class);

            EndpointAllocationTaskState completeState = this
                    .waitForServiceState(
                            EndpointAllocationTaskState.class,
                            returnState.documentSelfLink,
                            state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                                    .ordinal());

            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FINISHED));

            assertNotNull(completeState.endpointState);
            assertNotNull(completeState.endpointState.computeLink);
            assertNotNull(completeState.endpointState.computeDescriptionLink);
            ComputeDescription endpointDescr = getServiceSynchronously(
                    completeState.endpointState.computeDescriptionLink, ComputeDescription.class);
            assertEquals("test-zoneId", endpointDescr.zoneId);
        }

        @Test
        public void testShouldCreateNewEndpointWithPredefinedSelfLink() throws Throwable {
            EndpointState endpoint = createEndpointState();
            endpoint.documentSelfLink = UriUtils.buildUriPath(EndpointService.FACTORY_LINK,
                    "test-endpoint");

            // Check endpoint doesn't exists
            this.host.sendAndWaitExpectFailure(
                    Operation.createGet(UriUtils.buildUri(this.host, endpoint.documentSelfLink)));

            EndpointAllocationTaskState startState = createEndpointAllocationRequest(endpoint);
            startState.adapterReference = UriUtils.buildUri(getHost(),
                    MockSuccessEndpointAdapter.SELF_LINK);

            EndpointAllocationTaskState returnState = this
                    .postServiceSynchronously(
                            EndpointAllocationTaskService.FACTORY_LINK,
                            startState, EndpointAllocationTaskState.class);

            EndpointAllocationTaskState completeState = this
                    .waitForServiceState(
                            EndpointAllocationTaskState.class,
                            returnState.documentSelfLink,
                            state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                                    .ordinal());

            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FINISHED));

            assertEquals(endpoint.documentSelfLink, completeState.endpointState.documentSelfLink);

            // Check endpoint was created
            this.host.sendAndWaitExpectSuccess(
                    Operation.createGet(UriUtils.buildUri(this.host, endpoint.documentSelfLink)));
        }

        @Test
        public void testSuccessWithEnumeration() throws Throwable {
            EndpointState endpoint = createEndpointState();
            EndpointAllocationTaskState startState = createEndpointAllocationRequest(endpoint);
            startState.adapterReference = UriUtils.buildUri(getHost(),
                    MockSuccessEndpointAdapter.SELF_LINK);
            startState.enumerationRequest = new EndpointAllocationTaskService.ResourceEnumerationRequest();
            startState.enumerationRequest.resourcePoolLink = UriUtils
                    .buildUriPath(ResourcePoolService.FACTORY_LINK, "fake-pool");
            startState.enumerationRequest.refreshIntervalMicros = TimeUnit.MILLISECONDS.toMicros(250);

            EndpointAllocationTaskState returnState = this
                    .postServiceSynchronously(
                            EndpointAllocationTaskService.FACTORY_LINK,
                            startState, EndpointAllocationTaskState.class);

            EndpointAllocationTaskState completeState = this
                    .waitForServiceState(
                            EndpointAllocationTaskState.class,
                            returnState.documentSelfLink,
                            state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                                    .ordinal());

            ComputeState computeState = getServiceSynchronously(
                    completeState.endpointState.computeLink, ComputeState.class);
            assertEquals(startState.enumerationRequest.resourcePoolLink,
                    computeState.resourcePoolLink);

            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FINISHED));
        }
    }

    private static EndpointState createEndpointState() {
        EndpointState endpoint = new EndpointState();
        endpoint.endpointType = "aws";
        endpoint.name = "aws_endpoint";
        endpoint.endpointProperties = new HashMap<>();
        endpoint.endpointProperties.put(REGION_KEY, "test-regionId");
        endpoint.endpointProperties.put(PRIVATE_KEY_KEY, "test-secreteKey");
        endpoint.endpointProperties.put(PRIVATE_KEYID_KEY, "test-accessKey");
        endpoint.tenantLinks = Collections.singletonList("tenant-1");
        return endpoint;
    }

    private static EndpointAllocationTaskState createEndpointAllocationRequest(
            EndpointState endpoint) {
        EndpointAllocationTaskState endpointAllocationTaskState = new EndpointAllocationTaskState();
        endpointAllocationTaskState.endpointState = endpoint;
        if (endpoint != null) {
            endpointAllocationTaskState.tenantLinks = endpoint.tenantLinks;
        }
        return endpointAllocationTaskState;
    }
}
