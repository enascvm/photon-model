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
import static org.hamcrest.Matchers.is;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.adapterapi.NicSecurityGroupsRequest;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.SecurityGroupService;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.tasks.NicSecurityGroupsTaskService.NicSecurityGroupsTaskState;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

/**
 * This class implements tests for the {@link NicSecurityGroupsTaskService}
 * class.
 */
@RunWith(NicSecurityGroupsTaskServiceTest.class)
@SuiteClasses({ NicSecurityGroupsTaskServiceTest.ConstructorTest.class,
        NicSecurityGroupsTaskServiceTest.HandleStartTest.class,
        NicSecurityGroupsTaskServiceTest.HandlePatchTest.class})
public class NicSecurityGroupsTaskServiceTest extends Suite {

    public NicSecurityGroupsTaskServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    enum InstanceAdapterTestTypes {
        SUCCESS, FAILURE, MISSING
    }

    private static NicSecurityGroupsTaskState buildStartState(
            BaseModelTest test,
            NicSecurityGroupsRequest.OperationRequestType requestType,
            InstanceAdapterTestTypes instanceAdapterType) throws Throwable {
        NicSecurityGroupsTaskState state = new NicSecurityGroupsTaskState();

        state.requestType = requestType;
        state.networkInterfaceLink = createNetworkInterface(test).documentSelfLink;
        state.securityGroupLinks = Arrays
                .asList(createSecurityGroup(test, UUID.randomUUID().toString()).documentSelfLink);

        switch (instanceAdapterType) {
        case SUCCESS:
            state.adapterReference = UriUtils
                    .buildUri(test.getHost(),
                            MockAdapter.MockNetworkInterfaceSecurityGroupsSuccessAdapter.SELF_LINK);
            break;
        case FAILURE:
            state.adapterReference = UriUtils
                    .buildUri(test.getHost(),
                            MockAdapter.MockNetworkInterfaceSecurityGroupsFailureAdapter.SELF_LINK);
            break;
        default:
            state.adapterReference = null;
        }

        return state;
    }

    private static SecurityGroupState createSecurityGroup(BaseModelTest test, String endpointLink) throws Throwable {
        SecurityGroupState securityGroupState = new SecurityGroupState();
        securityGroupState.authCredentialsLink = "authCredentialsLink";
        securityGroupState.name = UUID.randomUUID().toString();
        securityGroupState.regionId = "regionId";
        securityGroupState.endpointLink = endpointLink;
        securityGroupState.resourcePoolLink = "/resourcePoolLink";
        securityGroupState.instanceAdapterReference = UriUtils.buildUri(test.getHost(),
                MockAdapter.MockSecurityGroupInstanceSuccessAdapter.SELF_LINK);
        securityGroupState.id = UUID.randomUUID().toString();
        ArrayList<SecurityGroupState.Rule> rules = new ArrayList<>();
        SecurityGroupState.Rule ssh = new SecurityGroupState.Rule();
        ssh.name = "ssh";
        ssh.protocol = "tcp";
        ssh.ipRangeCidr = "0.0.0.0/0";
        ssh.ports = "22";
        rules.add(ssh);
        securityGroupState.ingress = rules;
        securityGroupState.egress = rules;

        return test.postServiceSynchronously(SecurityGroupService.FACTORY_LINK, securityGroupState,
                SecurityGroupState.class);
    }

    private static NetworkInterfaceState createNetworkInterface(BaseModelTest test) throws Throwable {
        NetworkInterfaceState networkInterfaceState = new NetworkInterfaceState();
        networkInterfaceState.subnetLink = "subnetLink";
        networkInterfaceState.name = UUID.randomUUID().toString();
        networkInterfaceState.regionId = "regionId";
        networkInterfaceState.id = UUID.randomUUID().toString();

        return test.postServiceSynchronously(NetworkInterfaceService.FACTORY_LINK,
                networkInterfaceState, NetworkInterfaceState.class);
    }

    private static NicSecurityGroupsTaskState postAndWaitForService(
            BaseModelTest test,
            NicSecurityGroupsTaskState startState)
            throws Throwable {
        NicSecurityGroupsTaskState returnState = test
                .postServiceSynchronously(
                        NicSecurityGroupsTaskService.FACTORY_LINK,
                        startState,
                        NicSecurityGroupsTaskState.class);

        NicSecurityGroupsTaskState completeState = test
                .waitForServiceState(
                        NicSecurityGroupsTaskState.class,
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

        private NicSecurityGroupsTaskService nicSecurityGroupsTaskService;

        @Before
        public void setUpTest() {
            this.nicSecurityGroupsTaskService = new NicSecurityGroupsTaskService();
        }

        @Test
        public void testServiceOptions() {

            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.REPLICATION,
                    Service.ServiceOption.OWNER_SELECTION);

            assertThat(this.nicSecurityGroupsTaskService.getOptions(),
                    is(expected));
            assertThat(this.nicSecurityGroupsTaskService.getProcessingStage(),
                    is(Service.ProcessingStage.CREATED));
        }
    }

    /**
     * This class implements tests for the
     * {@link NicSecurityGroupsTaskService#handleStart} method.
     */
    public static class HandleStartTest extends BaseModelTest {
        @Override
        protected void startRequiredServices() throws Throwable {
            NicSecurityGroupsTaskServiceTest.startFactoryServices(this);
            super.startRequiredServices();
        }

        @Test
        public void testValidateSubnetService() throws Throwable {
            NicSecurityGroupsTaskState startState = buildStartState(
                    this, NicSecurityGroupsRequest.OperationRequestType.ADD,
                    InstanceAdapterTestTypes.SUCCESS);
            NicSecurityGroupsTaskState completeState =
                    postAndWaitForService(
                            this, startState);
            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FINISHED));
        }

        @Test
        public void testValidationLogic() throws Throwable {
            {
                NicSecurityGroupsTaskState invalidRequestType =
                        buildStartState(
                                this,
                                NicSecurityGroupsRequest.OperationRequestType.ADD,
                                InstanceAdapterTestTypes.SUCCESS);

                invalidRequestType.requestType = null;

                postServiceSynchronously(
                        NicSecurityGroupsTaskService.FACTORY_LINK,
                        invalidRequestType,
                        NicSecurityGroupsTaskState.class,
                        IllegalArgumentException.class);
            }
            {
                NicSecurityGroupsTaskState invalidNetworkInterfaceLink = buildStartState(
                        this,
                        NicSecurityGroupsRequest.OperationRequestType.ADD,
                        InstanceAdapterTestTypes.SUCCESS);
                invalidNetworkInterfaceLink.networkInterfaceLink = null;

                postServiceSynchronously(
                        NicSecurityGroupsTaskService.FACTORY_LINK,
                        invalidNetworkInterfaceLink,
                        NicSecurityGroupsTaskState.class,
                        IllegalArgumentException.class);
            }
            {
                NicSecurityGroupsTaskState invalidSecurityGroupLinks = buildStartState(
                        this,
                        NicSecurityGroupsRequest.OperationRequestType.ADD,
                        InstanceAdapterTestTypes.SUCCESS);
                invalidSecurityGroupLinks.securityGroupLinks = null;

                postServiceSynchronously(
                        NicSecurityGroupsTaskService.FACTORY_LINK,
                        invalidSecurityGroupLinks,
                        NicSecurityGroupsTaskState.class,
                        IllegalArgumentException.class);
            }
            {
                NicSecurityGroupsTaskState missingAdapterLink = buildStartState(
                        this,
                        NicSecurityGroupsRequest.OperationRequestType.ADD,
                        InstanceAdapterTestTypes.SUCCESS);
                missingAdapterLink.adapterReference = null;

                postServiceSynchronously(
                        NicSecurityGroupsTaskService.FACTORY_LINK,
                        missingAdapterLink,
                        NicSecurityGroupsTaskState.class,
                        IllegalArgumentException.class);
            }
            {
                NicSecurityGroupsTaskState securityGroupDifferentEndpoints = buildStartState(
                        this,
                        NicSecurityGroupsRequest.OperationRequestType.ADD,
                        InstanceAdapterTestTypes.SUCCESS);
                securityGroupDifferentEndpoints.securityGroupLinks = Arrays.asList(
                        createSecurityGroup(this, UUID.randomUUID().toString()).documentSelfLink,
                        createSecurityGroup(this, UUID.randomUUID().toString()).documentSelfLink);

                postServiceSynchronously(
                        NicSecurityGroupsTaskService.FACTORY_LINK,
                        securityGroupDifferentEndpoints,
                        NicSecurityGroupsTaskState.class,
                        CompletionException.class);
            }
            {
                NicSecurityGroupsTaskState securityGroupDifferentEndpoints = buildStartState(
                        this,
                        NicSecurityGroupsRequest.OperationRequestType.ADD,
                        InstanceAdapterTestTypes.SUCCESS);
                securityGroupDifferentEndpoints.securityGroupLinks = Arrays.asList(
                        createSecurityGroup(this, UUID.randomUUID().toString()).documentSelfLink,
                        createSecurityGroup(this, null).documentSelfLink);

                postServiceSynchronously(
                        NicSecurityGroupsTaskService.FACTORY_LINK,
                        securityGroupDifferentEndpoints,
                        NicSecurityGroupsTaskState.class,
                        CompletionException.class);
            }
            {
                NicSecurityGroupsTaskState securityGroupNullEndpoint = buildStartState(
                        this,
                        NicSecurityGroupsRequest.OperationRequestType.ADD,
                        InstanceAdapterTestTypes.SUCCESS);
                securityGroupNullEndpoint.securityGroupLinks = Arrays.asList(
                        createSecurityGroup(this, null).documentSelfLink);

                postServiceSynchronously(
                        NicSecurityGroupsTaskService.FACTORY_LINK,
                        securityGroupNullEndpoint,
                        NicSecurityGroupsTaskState.class,
                        CompletionException.class);
            }
        }
    }

    /**
     * This class implements tests for the
     * {@link NicSecurityGroupsTaskService#handlePatch} method.
     */
    public static class HandlePatchTest extends BaseModelTest {

        @Override
        protected void startRequiredServices() throws Throwable {
            NicSecurityGroupsTaskServiceTest.startFactoryServices(this);
            super.startRequiredServices();
        }

        @Test
        public void testAddNetworkInterfaceToSecurityGroupsSuccess() throws Throwable {
            NicSecurityGroupsTaskState startState = buildStartState(
                    this, NicSecurityGroupsRequest.OperationRequestType.ADD,
                    InstanceAdapterTestTypes.SUCCESS);

            NicSecurityGroupsTaskState completeState = postAndWaitForService(
                    this, startState);

            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FINISHED));
        }

        @Test
        public void testRemoveNetworkInterfaceSecurityGroupsSuccess() throws Throwable {
            NicSecurityGroupsTaskState startState = buildStartState(
                    this, NicSecurityGroupsRequest.OperationRequestType.REMOVE,
                    InstanceAdapterTestTypes.SUCCESS);

            NicSecurityGroupsTaskState completeState = postAndWaitForService(
                    this, startState);

            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FINISHED));
        }

        @Test
        public void testAddNetworkInterfaceToSecurityGroupsFailure() throws Throwable {
            NicSecurityGroupsTaskState startState =
                    buildStartState(
                            this, NicSecurityGroupsRequest.OperationRequestType.ADD,
                            InstanceAdapterTestTypes.FAILURE);

            NicSecurityGroupsTaskState completeState = postAndWaitForService(
                    this, startState);

            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FAILED));
        }

    }
}
