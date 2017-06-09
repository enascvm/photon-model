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

package com.vmware.photon.controller.model.tasks;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import java.util.EnumSet;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.adapterapi.SubnetInstanceRequest;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.tasks.ProvisionSubnetTaskService.ProvisionSubnetTaskState;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

/**
 * This class implements tests for the {@link ProvisionSubnetTaskService}
 * class.
 */
@RunWith(ProvisionSubnetTaskServiceTest.class)
@SuiteClasses({ ProvisionSubnetTaskServiceTest.ConstructorTest.class,
        ProvisionSubnetTaskServiceTest.HandleStartTest.class })
public class ProvisionSubnetTaskServiceTest extends Suite {

    public ProvisionSubnetTaskServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    enum InstanceAdapterTestTypes {
        SUCCESS, FAILURE, MISSING
    }

    private static ProvisionSubnetTaskState buildStartState(
            BaseModelTest test,
            SubnetInstanceRequest.InstanceRequestType requestType,
            InstanceAdapterTestTypes instanceAdapterType) throws Throwable {

        SubnetState state = new SubnetState();
        state.name = "subnet1-name";
        state.networkLink = "networkLink";
        state.gatewayAddress = "192.168.21.1";
        state.subnetCIDR = "192.168.21.0/28";

        switch (instanceAdapterType) {
        case SUCCESS:
            state.instanceAdapterReference = UriUtils.buildUri(test.getHost(),
                    MockAdapter.MockSubnetInstanceSuccessAdapter.SELF_LINK);
            break;
        case FAILURE:
            state.instanceAdapterReference = UriUtils.buildUri(test.getHost(),
                    MockAdapter.MockSubnetInstanceFailureAdapter.SELF_LINK);
            break;
        default:
            state.instanceAdapterReference = null;
        }
        state.id = UUID.randomUUID().toString();

        SubnetState returnState = test.postServiceSynchronously(
                SubnetService.FACTORY_LINK, state, SubnetState.class);
        ProvisionSubnetTaskState startState = new ProvisionSubnetTaskState();

        startState.requestType = requestType;
        startState.subnetLink = returnState.documentSelfLink;
        startState.options = EnumSet.of(TaskOption.IS_MOCK);

        return startState;
    }

    private static ProvisionSubnetTaskState postAndWaitForService(
            BaseModelTest test,
            ProvisionSubnetTaskState startState)
            throws Throwable {
        ProvisionSubnetTaskState returnState = test
                .postServiceSynchronously(
                        ProvisionSubnetTaskService.FACTORY_LINK,
                        startState,
                        ProvisionSubnetTaskState.class);

        ProvisionSubnetTaskState completeState = test
                .waitForServiceState(
                        ProvisionSubnetTaskState.class,
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

        private ProvisionSubnetTaskService provisionSubnetTaskService;

        @Before
        public void setUpTest() {
            this.provisionSubnetTaskService = new ProvisionSubnetTaskService();
        }

        @Test
        public void testServiceOptions() {

            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.REPLICATION,
                    Service.ServiceOption.OWNER_SELECTION);

            assertThat(this.provisionSubnetTaskService.getOptions(),
                    is(expected));
            assertThat(this.provisionSubnetTaskService.getProcessingStage(),
                    is(Service.ProcessingStage.CREATED));
        }
    }

    /**
     * This class implements tests for the
     * {@link ProvisionSubnetTaskService#handleStart} method.
     */
    public static class HandleStartTest extends BaseModelTest {
        @Override
        protected void startRequiredServices() throws Throwable {
            ProvisionSubnetTaskServiceTest.startFactoryServices(this);
            super.startRequiredServices();
        }

        @Test
        public void testValidateSubnetService() throws Throwable {
            ProvisionSubnetTaskState startState = buildStartState(
                    this, SubnetInstanceRequest.InstanceRequestType.CREATE,
                    InstanceAdapterTestTypes.SUCCESS);
            ProvisionSubnetTaskState completeState =
                    postAndWaitForService(
                            this, startState);
            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FINISHED));
        }

        @Test
        public void testMissingValue() throws Throwable {
            {
                ProvisionSubnetTaskState invalidRequestType =
                        buildStartState(
                                this,
                                SubnetInstanceRequest.InstanceRequestType.CREATE,
                                InstanceAdapterTestTypes.SUCCESS);

                invalidRequestType.requestType = null;

                postServiceSynchronously(
                        ProvisionSubnetTaskService.FACTORY_LINK,
                        invalidRequestType,
                        ProvisionSubnetTaskState.class,
                        IllegalArgumentException.class);
            }
            {
                ProvisionSubnetTaskState invalidNetworkDescriptionLink = buildStartState(
                        this,
                        SubnetInstanceRequest.InstanceRequestType.CREATE,
                        InstanceAdapterTestTypes.SUCCESS);
                invalidNetworkDescriptionLink.subnetLink = null;

                postServiceSynchronously(
                        ProvisionSubnetTaskService.FACTORY_LINK,
                        invalidNetworkDescriptionLink,
                        ProvisionSubnetTaskState.class,
                        IllegalArgumentException.class);
            }
        }
    }

    /**
     * This class implements tests for the
     * {@link ProvisionSubnetTaskService#handlePatch} method.
     */
    public static class HandlePatchTest extends BaseModelTest {

        @Override
        protected void startRequiredServices() throws Throwable {
            ProvisionSubnetTaskServiceTest.startFactoryServices(this);
            super.startRequiredServices();
        }

        @Test
        public void testCreateNetworkSuccess() throws Throwable {

            ProvisionSubnetTaskState startState = buildStartState(
                    this, SubnetInstanceRequest.InstanceRequestType.CREATE,
                    InstanceAdapterTestTypes.SUCCESS);

            ProvisionSubnetTaskState completeState = postAndWaitForService(
                    this, startState);

            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FINISHED));
        }

        @Test
        public void testDeleteNetworkSuccess() throws Throwable {
            ProvisionSubnetTaskState startState = buildStartState(
                    this, SubnetInstanceRequest.InstanceRequestType.DELETE,
                    InstanceAdapterTestTypes.SUCCESS);

            ProvisionSubnetTaskState completeState = postAndWaitForService(
                    this, startState);

            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FINISHED));
        }

        @Test
        public void testCreateNetworkServiceAdapterFailure() throws Throwable {
            ProvisionSubnetTaskState startState =
                    buildStartState(
                            this, SubnetInstanceRequest.InstanceRequestType.CREATE,
                            InstanceAdapterTestTypes.FAILURE);

            ProvisionSubnetTaskState completeState = postAndWaitForService(
                    this, startState);

            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FAILED));
        }

        @Test
        public void testCreateNetworkServiceNoInstanceAdapter() throws Throwable {
            ProvisionSubnetTaskState startState = buildStartState(
                    this,
                    SubnetInstanceRequest.InstanceRequestType.CREATE,
                    InstanceAdapterTestTypes.MISSING);

            ProvisionSubnetTaskState completeState = postAndWaitForService(
                    this, startState);

            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FAILED));
            assertThat(completeState.taskInfo.failure.message,
                    containsString("instanceAdapterReference required"));
        }

    }
}
