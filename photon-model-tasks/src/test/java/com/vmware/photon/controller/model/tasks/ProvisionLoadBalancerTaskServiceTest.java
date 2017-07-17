/*
 * Copyright (c) 2015-2017 VMware, Inc. All Rights Reserved.
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

import java.util.EnumSet;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.adapterapi.LoadBalancerInstanceRequest;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.LoadBalancerService;
import com.vmware.photon.controller.model.resources.LoadBalancerService.LoadBalancerState;
import com.vmware.photon.controller.model.resources.LoadBalancerServiceTest;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

/**
 * This class implements tests for the {@link ProvisionLoadBalancerTaskService} class.
 */
@RunWith(ProvisionLoadBalancerTaskServiceTest.class)
@SuiteClasses({ ProvisionLoadBalancerTaskServiceTest.ConstructorTest.class,
        ProvisionLoadBalancerTaskServiceTest.HandleStartTest.class,
        ProvisionLoadBalancerTaskServiceTest.HandlePatchTest.class })
public class ProvisionLoadBalancerTaskServiceTest extends Suite {

    public ProvisionLoadBalancerTaskServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    private static ProvisionLoadBalancerTaskService.ProvisionLoadBalancerTaskState buildValidStartState(
            BaseModelTest test,
            LoadBalancerInstanceRequest.InstanceRequestType requestType,
            boolean success) throws Throwable {

        LoadBalancerState state = LoadBalancerServiceTest.buildValidStartState();

        if (success) {
            state.instanceAdapterReference = UriUtils.buildUri(test.getHost(),
                    MockAdapter.MockLoadBalancerInstanceSuccessAdapter.SELF_LINK);
        } else {
            state.instanceAdapterReference = UriUtils.buildUri(test.getHost(),
                    MockAdapter.MockLoadBalancerInstanceFailureAdapter.SELF_LINK);
        }

        LoadBalancerState returnState = test.postServiceSynchronously(
                LoadBalancerService.FACTORY_LINK, state, LoadBalancerState.class);

        ProvisionLoadBalancerTaskService.ProvisionLoadBalancerTaskState startState =
                new ProvisionLoadBalancerTaskService.ProvisionLoadBalancerTaskState();
        startState.requestType = requestType;
        startState.loadBalancerLink = returnState.documentSelfLink;
        startState.isMockRequest = true;
        return startState;
    }

    private static ProvisionLoadBalancerTaskService.ProvisionLoadBalancerTaskState postAndWaitForService(
            BaseModelTest test,
            ProvisionLoadBalancerTaskService.ProvisionLoadBalancerTaskState startState)
            throws Throwable {
        ProvisionLoadBalancerTaskService.ProvisionLoadBalancerTaskState returnState = test
                .postServiceSynchronously(
                        ProvisionLoadBalancerTaskService.FACTORY_LINK,
                        startState,
                        ProvisionLoadBalancerTaskService.ProvisionLoadBalancerTaskState.class);

        ProvisionLoadBalancerTaskService.ProvisionLoadBalancerTaskState completeState = test
                .waitForServiceState(
                        ProvisionLoadBalancerTaskService.ProvisionLoadBalancerTaskState.class,
                        returnState.documentSelfLink,
                        state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                                .ordinal());

        return completeState;
    }

    private static void startFactoryServices(BaseModelTest test) throws Throwable {
        PhotonModelTaskServices.startServices(test.getHost());
        MockAdapter.startFactories(test);
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {

        private ProvisionLoadBalancerTaskService ProvisionLoadBalancerTaskService;

        @Before
        public void setUpTest() {
            this.ProvisionLoadBalancerTaskService = new ProvisionLoadBalancerTaskService();
        }

        @Test
        public void testServiceOptions() {

            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.REPLICATION,
                    Service.ServiceOption.OWNER_SELECTION);

            assertThat(this.ProvisionLoadBalancerTaskService.getOptions(),
                    is(expected));
            assertThat(this.ProvisionLoadBalancerTaskService.getProcessingStage(),
                    is(Service.ProcessingStage.CREATED));
        }
    }

    /**
     * This class implements tests for the
     * {@link ProvisionLoadBalancerTaskService#handleStart} method.
     */
    public static class HandleStartTest extends BaseModelTest {
        @Override
        protected void startRequiredServices() throws Throwable {
            ProvisionLoadBalancerTaskServiceTest.startFactoryServices(this);
            super.startRequiredServices();
        }

        @Test
        public void testValidateLoadBalancerService() throws Throwable {
            ProvisionLoadBalancerTaskService.ProvisionLoadBalancerTaskState startState = buildValidStartState(
                    this, LoadBalancerInstanceRequest.InstanceRequestType.CREATE,
                    true);
            ProvisionLoadBalancerTaskService.ProvisionLoadBalancerTaskState completeState = postAndWaitForService(
                    this, startState);
            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FINISHED));
        }

        @Test
        public void testMissingValue() throws Throwable {
            ProvisionLoadBalancerTaskService.ProvisionLoadBalancerTaskState invalidRequestType = buildValidStartState(
                    this,
                    LoadBalancerInstanceRequest.InstanceRequestType.CREATE, true);
            ProvisionLoadBalancerTaskService.ProvisionLoadBalancerTaskState invalidLoadBalancerLink = buildValidStartState(
                    this,
                    LoadBalancerInstanceRequest.InstanceRequestType.CREATE, true);

            invalidRequestType.requestType = null;
            invalidLoadBalancerLink.loadBalancerLink = null;

            postServiceSynchronously(
                            ProvisionLoadBalancerTaskService.FACTORY_LINK,
                            invalidRequestType,
                            ProvisionLoadBalancerTaskService.ProvisionLoadBalancerTaskState.class,
                            IllegalArgumentException.class);
            postServiceSynchronously(
                            ProvisionLoadBalancerTaskService.FACTORY_LINK,
                            invalidLoadBalancerLink,
                            ProvisionLoadBalancerTaskService.ProvisionLoadBalancerTaskState.class,
                            IllegalArgumentException.class);
        }

        @Test
        public void testDeletionOfMissingLoadBalancer() throws Throwable {
            ProvisionLoadBalancerTaskService.ProvisionLoadBalancerTaskState startState =
                    new ProvisionLoadBalancerTaskService.ProvisionLoadBalancerTaskState();
            startState.requestType = LoadBalancerInstanceRequest.InstanceRequestType.DELETE;
            startState.loadBalancerLink = "dummyLbLink";
            startState.isMockRequest = true;

            ProvisionLoadBalancerTaskService.ProvisionLoadBalancerTaskState completeState =
                    postAndWaitForService(this, startState);
            assertThat(completeState.taskInfo.stage, is(TaskState.TaskStage.FINISHED));
        }
    }

    /**
     * This class implements tests for the
     * {@link ProvisionLoadBalancerTaskService#handlePatch} method.
     */
    public static class HandlePatchTest extends BaseModelTest {

        @Override
        protected void startRequiredServices() throws Throwable {
            ProvisionLoadBalancerTaskServiceTest.startFactoryServices(this);
            super.startRequiredServices();
        }

        @Test
        public void testCreateLoadBalancerSuccess() throws Throwable {

            ProvisionLoadBalancerTaskService.ProvisionLoadBalancerTaskState startState = buildValidStartState(
                    this, LoadBalancerInstanceRequest.InstanceRequestType.CREATE,
                    true);

            ProvisionLoadBalancerTaskService.ProvisionLoadBalancerTaskState completeState = postAndWaitForService(
                    this, startState);

            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FINISHED));
        }

        @Test
        public void testDeleteLoadBalancerSuccess() throws Throwable {
            ProvisionLoadBalancerTaskService.ProvisionLoadBalancerTaskState startState = buildValidStartState(
                    this, LoadBalancerInstanceRequest.InstanceRequestType.DELETE,
                    true);

            ProvisionLoadBalancerTaskService.ProvisionLoadBalancerTaskState completeState = postAndWaitForService(
                    this, startState);

            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FINISHED));
        }

        @Test
        public void testCreateLoadBalancerServiceAdapterFailure() throws Throwable {
            ProvisionLoadBalancerTaskService.ProvisionLoadBalancerTaskState startState = buildValidStartState(
                    this, LoadBalancerInstanceRequest.InstanceRequestType.CREATE,
                    false);

            ProvisionLoadBalancerTaskService.ProvisionLoadBalancerTaskState completeState = postAndWaitForService(
                    this, startState);

            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FAILED));
        }

    }
}
