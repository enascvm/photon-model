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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.adapterapi.SecurityGroupInstanceRequest;
import com.vmware.photon.controller.model.helpers.BaseModelTest;

import com.vmware.photon.controller.model.resources.SecurityGroupService;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState.Rule;
import com.vmware.photon.controller.model.tasks.MockAdapter.MockSecurityGroupInstanceFailureAdapter;
import com.vmware.photon.controller.model.tasks.MockAdapter.MockSecurityGroupInstanceSuccessAdapter;
import com.vmware.photon.controller.model.tasks.ProvisionSecurityGroupTaskService.ProvisionSecurityGroupTaskState;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

/**
 * This class implements tests for the {@link ProvisionSecurityGroupTaskService}
 * class.
 */
@RunWith(ProvisionSecurityGroupTaskServiceTest.class)
@SuiteClasses({ ProvisionSecurityGroupTaskServiceTest.ConstructorTest.class,
        ProvisionSecurityGroupTaskServiceTest.HandleStartTest.class,
        ProvisionSecurityGroupTaskServiceTest.HandlePatchTest.class })
public class ProvisionSecurityGroupTaskServiceTest extends Suite {

    public ProvisionSecurityGroupTaskServiceTest(Class<?> klass,
            RunnerBuilder builder) throws InitializationError {
        super(klass, builder);
    }

    private static ProvisionSecurityGroupTaskState buildValidStartState(
            BaseModelTest test,
            SecurityGroupInstanceRequest.InstanceRequestType requestType,
            boolean success) throws Throwable {
        ProvisionSecurityGroupTaskState startState = new ProvisionSecurityGroupTaskState();

        SecurityGroupState securityGroupState1 = createSecurityGroupState(test, success);
        SecurityGroupState returnState1 = test.postServiceSynchronously(
                SecurityGroupService.FACTORY_LINK, securityGroupState1, SecurityGroupState.class);
        SecurityGroupState securityGroupState2 = createSecurityGroupState(test, success);
        SecurityGroupState returnState2 = test.postServiceSynchronously(
                SecurityGroupService.FACTORY_LINK, securityGroupState2, SecurityGroupState.class);
        startState.requestType = requestType;
        startState.securityGroupDescriptionLinks = Stream.of(returnState1.documentSelfLink,
                returnState2.documentSelfLink).collect(Collectors.toSet());

        startState.isMockRequest = true;

        return startState;
    }

    private static ProvisionSecurityGroupTaskState postAndWaitForService(
            BaseModelTest test,
            ProvisionSecurityGroupTaskState startState)
            throws Throwable {
        ProvisionSecurityGroupTaskState returnState = test
                .postServiceSynchronously(
                        ProvisionSecurityGroupTaskService.FACTORY_LINK,
                        startState,
                        ProvisionSecurityGroupTaskState.class);

        ProvisionSecurityGroupTaskState completeState = test
                .waitForServiceState(
                        ProvisionSecurityGroupTaskState.class,
                        returnState.documentSelfLink,
                        state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                                .ordinal());

        return completeState;
    }

    private static void startFactoryServices(BaseModelTest test) throws Throwable {
        PhotonModelTaskServices.startServices(test.getHost());
        MockAdapter.startFactories(test);
    }

    private static SecurityGroupState createSecurityGroupState(BaseModelTest test,
            boolean success) {
        SecurityGroupState securityGroupState = new SecurityGroupState();
        securityGroupState.authCredentialsLink = "authCredentialsLink";
        securityGroupState.name = UUID.randomUUID().toString();
        securityGroupState.regionId = "regionId";
        securityGroupState.resourcePoolLink = "/resourcePoolLink";
        if (success) {
            securityGroupState.instanceAdapterReference = UriUtils.buildUri(test.getHost(),
                    MockSecurityGroupInstanceSuccessAdapter.SELF_LINK);
        } else {
            securityGroupState.instanceAdapterReference = UriUtils.buildUri(test.getHost(),
                    MockSecurityGroupInstanceFailureAdapter.SELF_LINK);
        }
        securityGroupState.id = UUID.randomUUID().toString();
        ArrayList<Rule> rules = new ArrayList<>();
        Rule ssh = new Rule();
        ssh.name = "ssh";
        ssh.protocol = "tcp";
        ssh.ipRangeCidr = "0.0.0.0/0";
        ssh.ports = "22";
        rules.add(ssh);
        securityGroupState.ingress = rules;
        securityGroupState.egress = rules;

        return securityGroupState;
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {
        private ProvisionSecurityGroupTaskService provisionSecurityGroupTaskService;

        @Before
        public void setupTest() {
            this.provisionSecurityGroupTaskService = new ProvisionSecurityGroupTaskService();
        }

        @Test
        public void testServiceOptions() {
            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.REPLICATION,
                    Service.ServiceOption.OWNER_SELECTION);

            assertThat(this.provisionSecurityGroupTaskService.getOptions(),
                    is(expected));
            assertThat(this.provisionSecurityGroupTaskService.getProcessingStage(),
                    is(Service.ProcessingStage.CREATED));
        }
    }

    /**
     * This class implements tests for the
     * {@link ProvisionSecurityGroupTaskService#handleStart} method.
     */
    public static class HandleStartTest extends BaseModelTest {
        @Override
        protected void startRequiredServices() throws Throwable {
            ProvisionSecurityGroupTaskServiceTest.startFactoryServices(this);
            super.startRequiredServices();
        }

        @Test
        public void testValidateProvisionSecurityGroupTaskService() throws Throwable {
            ProvisionSecurityGroupTaskState startState = buildValidStartState(
                    this, SecurityGroupInstanceRequest.InstanceRequestType.CREATE,
                    true);
            ProvisionSecurityGroupTaskState completeState = postAndWaitForService(
                    this, startState);
            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FINISHED));
        }

        @Test
        public void testMissingValue() throws Throwable {
            ProvisionSecurityGroupTaskState invalidRequestType = buildValidStartState(
                    this,
                    SecurityGroupInstanceRequest.InstanceRequestType.CREATE, true);
            ProvisionSecurityGroupTaskState invalidSecurityGroupDescriptionLink = buildValidStartState(
                    this,
                    SecurityGroupInstanceRequest.InstanceRequestType.CREATE, true);

            invalidRequestType.requestType = null;
            invalidSecurityGroupDescriptionLink.securityGroupDescriptionLinks = null;

            this.postServiceSynchronously(
                            ProvisionSecurityGroupTaskService.FACTORY_LINK,
                            invalidRequestType,
                            ProvisionSecurityGroupTaskState.class,
                            IllegalArgumentException.class);
            this.postServiceSynchronously(
                            ProvisionSecurityGroupTaskService.FACTORY_LINK,
                            invalidSecurityGroupDescriptionLink,
                            ProvisionSecurityGroupTaskState.class,
                            IllegalArgumentException.class);
        }
    }

    /**
     * This class implements tests for the
     * {@link ProvisionSecurityGroupTaskService#handlePatch} method.
     */
    public static class HandlePatchTest extends BaseModelTest {
        @Override
        protected void startRequiredServices() throws Throwable {
            ProvisionSecurityGroupTaskServiceTest.startFactoryServices(this);
            super.startRequiredServices();
        }

        @Test
        public void testCreateSecurityGroupSuccess() throws Throwable {
            ProvisionSecurityGroupTaskState startState = buildValidStartState(
                    this, SecurityGroupInstanceRequest.InstanceRequestType.CREATE,
                    true);

            ProvisionSecurityGroupTaskState completeState = postAndWaitForService(
                    this, startState);

            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FINISHED));
        }

        @Test
        public void testDeleteSecurityGroupSuccess() throws Throwable {
            ProvisionSecurityGroupTaskState startState = buildValidStartState(
                    this, SecurityGroupInstanceRequest.InstanceRequestType.DELETE,
                    true);

            ProvisionSecurityGroupTaskState completeState = postAndWaitForService(
                    this, startState);

            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FINISHED));
        }

        @Test
        public void testCreateSecurityGroupServiceAdapterFailure() throws Throwable {
            ProvisionSecurityGroupTaskState startState = buildValidStartState(
                    this, SecurityGroupInstanceRequest.InstanceRequestType.CREATE,
                    false);

            ProvisionSecurityGroupTaskState completeState = postAndWaitForService(
                    this, startState);
            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FAILED));
        }

        @Test
        public void testCreateSecurityGroupServiceAdapterPartialFailure() throws Throwable {
            ProvisionSecurityGroupTaskState startState = buildValidStartState(
                    this, SecurityGroupInstanceRequest.InstanceRequestType.CREATE,
                    true);
            SecurityGroupState securityGroupState = createSecurityGroupState(this, false);
            SecurityGroupState returnState = this.postServiceSynchronously(
                    SecurityGroupService.FACTORY_LINK, securityGroupState, SecurityGroupState.class);
            startState.securityGroupDescriptionLinks.add(returnState.documentSelfLink);

            ProvisionSecurityGroupTaskState completeState = postAndWaitForService(
                    this, startState);
            assertThat(completeState.taskInfo.stage,
                    is(TaskState.TaskStage.FAILED));
        }
    }
}
