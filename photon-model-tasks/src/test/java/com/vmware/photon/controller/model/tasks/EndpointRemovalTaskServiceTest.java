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
import static org.junit.Assert.assertNotNull;

import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEYID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEY_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.REGION_KEY;

import java.util.EnumSet;
import java.util.HashMap;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService.EndpointAllocationTaskState;
import com.vmware.photon.controller.model.tasks.EndpointRemovalTaskService.EndpointRemovalTaskState;
import com.vmware.photon.controller.model.tasks.MockAdapter.MockSuccessEndpointAdapter;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

/**
 * This class implements tests for the {@link EndpointRemovalTaskService} class.
 */
@RunWith(EndpointAllocationTaskServiceTest.class)
@SuiteClasses({
        EndpointRemovalTaskServiceTest.ConstructorTest.class,
        EndpointRemovalTaskServiceTest.HandleStartTest.class,
        EndpointRemovalTaskServiceTest.EndToEndTest.class })
public class EndpointRemovalTaskServiceTest extends Suite {

    public EndpointRemovalTaskServiceTest(Class<?> klass,
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

        private EndpointRemovalTaskService endpointRemovalTaskService;

        @Before
        public void setUpTest() {
            this.endpointRemovalTaskService = new EndpointRemovalTaskService();
        }

        @Test
        public void testServiceOptions() {

            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.REPLICATION,
                    Service.ServiceOption.OWNER_SELECTION,
                    Service.ServiceOption.INSTRUMENTATION);

            assertThat(this.endpointRemovalTaskService.getOptions(),
                    is(expected));
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleStartTest extends BaseModelTest {

        @Override
        protected void startRequiredServices() throws Throwable {
            EndpointRemovalTaskServiceTest.startFactoryServices(this);
            super.startRequiredServices();
        }

        @Test
        public void testMissingEndpointState() throws Throwable {
            EndpointRemovalTaskState startState = new EndpointRemovalTaskState();

            this.postServiceSynchronously(
                    EndpointRemovalTaskService.FACTORY_LINK, startState,
                    EndpointRemovalTaskState.class,
                    IllegalArgumentException.class);
        }

    }

    /**
     * This class implements EndToEnd tests for {@link EndpointRemovalTaskService}.
     */
    public static class EndToEndTest extends BaseModelTest {
        @Override
        protected void startRequiredServices() throws Throwable {
            EndpointRemovalTaskServiceTest.startFactoryServices(this);
            super.startRequiredServices();
        }

        @Test
        public void testSuccess() throws Throwable {
            EndpointState endpoint = createEndpoint(this);

            EndpointRemovalTaskState removalTaskState = createEndpointRemovalTaskState(endpoint);

            EndpointRemovalTaskState returnState = this
                    .postServiceSynchronously(
                            EndpointRemovalTaskService.FACTORY_LINK,
                            removalTaskState, EndpointRemovalTaskState.class);

            EndpointRemovalTaskState completeState = this
                    .waitForServiceState(
                            EndpointRemovalTaskState.class,
                            returnState.documentSelfLink,
                            state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                                    .ordinal());

            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FINISHED));

        }

        @Test
        public void testFailOnMissingEndpointToDelete() throws Throwable {
            EndpointState endpoint = createEndpointState();
            endpoint.documentSelfLink = UriUtils.buildUriPath(EndpointService.FACTORY_LINK,
                    "fake-endpoint");

            EndpointRemovalTaskState removalTaskState = createEndpointRemovalTaskState(endpoint);

            EndpointRemovalTaskState returnState = this
                    .postServiceSynchronously(
                            EndpointRemovalTaskService.FACTORY_LINK,
                            removalTaskState, EndpointRemovalTaskState.class);

            EndpointRemovalTaskState completeState = this
                    .waitForServiceState(
                            EndpointRemovalTaskState.class,
                            returnState.documentSelfLink,
                            state -> TaskState.TaskStage.FAILED.ordinal() <= state.taskInfo.stage
                                    .ordinal());

            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FAILED));

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
        return endpoint;
    }

    private static EndpointAllocationTaskState createEndpointAllocationRequest(
            EndpointState endpoint) {
        EndpointAllocationTaskState endpointAllocationTaskState = new EndpointAllocationTaskState();
        endpointAllocationTaskState.endpointState = endpoint;
        return endpointAllocationTaskState;
    }

    private static EndpointRemovalTaskState createEndpointRemovalTaskState(
            EndpointState endpoint) {
        EndpointRemovalTaskState endpointRemovalTaskState = new EndpointRemovalTaskState();
        endpointRemovalTaskState.endpointLink = endpoint.documentSelfLink;
        endpointRemovalTaskState.tenantLinks = endpoint.tenantLinks;
        return endpointRemovalTaskState;
    }

    private static EndpointState createEndpoint(BaseModelTest test) throws Throwable {
        EndpointState endpoint = createEndpointState();

        // Create endpoint
        EndpointAllocationTaskState startState = createEndpointAllocationRequest(endpoint);
        startState.adapterReference = UriUtils.buildUri(test.getHost(),
                MockSuccessEndpointAdapter.SELF_LINK);

        EndpointAllocationTaskState returnState = test
                .postServiceSynchronously(
                        EndpointAllocationTaskService.FACTORY_LINK,
                        startState, EndpointAllocationTaskState.class);

        EndpointAllocationTaskState completeState = test
                .waitForServiceState(
                        EndpointAllocationTaskState.class,
                        returnState.documentSelfLink,
                        state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                                .ordinal());

        assertThat(completeState.taskInfo.stage,
                is(TaskState.TaskStage.FINISHED));

        EndpointAllocationTaskState taskState = getServiceSynchronously(test,
                completeState.documentSelfLink,
                EndpointAllocationTaskState.class);
        assertNotNull(taskState);
        assertNotNull(taskState.endpointState);

        ServiceDocument endpointState = taskState.endpointState;
        assertNotNull(endpointState.documentSelfLink);

        return getServiceSynchronously(test, endpointState.documentSelfLink,
                EndpointState.class);
    }

    private static <T extends ServiceDocument> T getServiceSynchronously(BaseModelTest test,
            String serviceLink, Class<T> type) throws Throwable {
        return test.getHost().getServiceState(null, type,
                UriUtils.buildUri(test.getHost(), serviceLink));
    }
}
