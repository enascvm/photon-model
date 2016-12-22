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
import static org.hamcrest.Matchers.notNullValue;

import java.util.EnumSet;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.tasks.ResourceRemovalTaskService.ResourceRemovalTaskState;
import com.vmware.photon.controller.model.tasks.ResourceRemovalTaskService.SubStage;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

/**
 * This class implements tests for the {@link ResourceRemovalTaskService} class.
 */
@RunWith(ResourceRemovalTaskServiceTest.class)
@SuiteClasses({ ResourceRemovalTaskServiceTest.ConstructorTest.class,
        ResourceRemovalTaskServiceTest.HandleStartTest.class,
        ResourceRemovalTaskServiceTest.EndToEndTest.class })
public class ResourceRemovalTaskServiceTest extends Suite {

    public ResourceRemovalTaskServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    private static ResourceRemovalTaskState buildValidStartState() {
        ResourceRemovalTaskState startState = new ResourceRemovalTaskState();

        startState.resourceQuerySpec = new QueryTask.QuerySpecification();
        QueryTask.Query kindClause = new QueryTask.Query().setTermPropertyName(
                ServiceDocument.FIELD_NAME_KIND).setTermMatchValue(
                        Utils.buildKind(ComputeService.ComputeState.class));
        startState.resourceQuerySpec.query.addBooleanClause(kindClause);
        startState.isMockRequest = true;

        return startState;
    }

    private static void startFactoryServices(BaseModelTest test) throws Throwable {
        PhotonModelTaskServices.startServices(test.getHost());
        MockAdapter.startFactories(test);
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {

        private ResourceRemovalTaskService resourceRemovalTaskService;

        @Before
        public void setUpTest() {
            this.resourceRemovalTaskService = new ResourceRemovalTaskService();
        }

        @Test
        public void testServiceOptions() {

            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.INSTRUMENTATION,
                    Service.ServiceOption.REPLICATION,
                    Service.ServiceOption.OWNER_SELECTION);

            assertThat(this.resourceRemovalTaskService.getOptions(),
                    is(expected));
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleStartTest extends BaseModelTest {
        @Override
        protected void startRequiredServices() throws Throwable {
            ResourceRemovalTaskServiceTest.startFactoryServices(this);
            super.startRequiredServices();
        }

        @Test
        public void testMissingResourceQuerySpec() throws Throwable {
            ResourceRemovalTaskState startState = buildValidStartState();
            startState.resourceQuerySpec = null;

            postServiceSynchronously(
                    ResourceRemovalTaskService.FACTORY_LINK, startState,
                    ResourceRemovalTaskState.class,
                    IllegalArgumentException.class);
        }

        @Test
        public void testMissingTaskInfo() throws Throwable {
            ResourceRemovalTaskState startState = buildValidStartState();
            startState.taskInfo = null;

            ResourceRemovalTaskState returnState = postServiceSynchronously(
                    ResourceRemovalTaskService.FACTORY_LINK,
                    startState,
                    ResourceRemovalTaskState.class);

            assertThat(returnState.taskInfo, notNullValue());
            assertThat(returnState.taskInfo.stage,
                    is(TaskState.TaskStage.CREATED));
        }

        @Test
        public void testMissingTaskSubStage() throws Throwable {
            ResourceRemovalTaskState startState = buildValidStartState();
            startState.taskSubStage = null;

            ResourceRemovalTaskState returnState = postServiceSynchronously(
                    ResourceRemovalTaskService.FACTORY_LINK,
                    startState,
                    ResourceRemovalTaskState.class);

            assertThat(returnState.taskSubStage, notNullValue());
            assertThat(
                    returnState.taskSubStage,
                    is(SubStage.WAITING_FOR_QUERY_COMPLETION));
        }
    }

    /**
     * This class implements EndToEnd tests for
     * {@link ResourceRemovalTaskService}.
     */
    public static class EndToEndTest extends BaseModelTest {
        @Override
        protected void startRequiredServices() throws Throwable {
            ResourceRemovalTaskServiceTest.startFactoryServices(this);
            super.startRequiredServices();
        }

        @Test
        public void testQueryResourceReturnZeroDocument() throws Throwable {
            ResourceRemovalTaskState startState = buildValidStartState();

            ResourceRemovalTaskState returnState = this
                    .postServiceSynchronously(
                            ResourceRemovalTaskService.FACTORY_LINK,
                            startState,
                            ResourceRemovalTaskState.class);

            returnState = this
                    .waitForServiceState(
                            ResourceRemovalTaskState.class,
                            returnState.documentSelfLink,
                            state -> state.taskInfo.stage == TaskState.TaskStage.FINISHED);

            assertThat(returnState.taskSubStage,
                    is(SubStage.FINISHED));
        }

        @Test
        public void testResourceRemovalSuccess() throws Throwable {
            ResourceRemovalTaskState startState = buildValidStartState();
            ComputeService.ComputeStateWithDescription cs = ModelUtils
                    .createComputeWithDescription(this,
                            MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
                            null);

            ResourceRemovalTaskState returnState = this
                    .postServiceSynchronously(
                            ResourceRemovalTaskService.FACTORY_LINK,
                            startState,
                            ResourceRemovalTaskState.class);

            returnState = this
                    .waitForServiceState(
                            ResourceRemovalTaskState.class,
                            returnState.documentSelfLink,
                            state -> state.taskInfo.stage == TaskState.TaskStage.FINISHED);

            assertThat(returnState.taskSubStage,
                    is(SubStage.FINISHED));

            // Clean up the compute and description documents
            this.deleteServiceSynchronously(cs.documentSelfLink);
            this.deleteServiceSynchronously(cs.descriptionLink);
            // Stop factory service.
            this.deleteServiceSynchronously(ResourceRemovalTaskService.FACTORY_LINK);

            // stop the removal task
            this.stopServiceSynchronously(returnState.documentSelfLink);
        }

        @Test
        public void testLocalResourceRemovalOnlySuccess() throws Throwable {
            ResourceRemovalTaskState startState = buildValidStartState();
            startState.options = EnumSet.of(TaskOption.DOCUMENT_CHANGES_ONLY);

            ComputeService.ComputeStateWithDescription cs = ModelUtils
                    .createComputeWithDescription(this,
                            MockAdapter.MockFailOnInvokeInstanceAdapter.SELF_LINK,
                            null);

            ResourceRemovalTaskState returnState = this
                    .postServiceSynchronously(
                            ResourceRemovalTaskService.FACTORY_LINK,
                            startState,
                            ResourceRemovalTaskState.class);

            returnState = this
                    .waitForServiceState(
                            ResourceRemovalTaskState.class,
                            returnState.documentSelfLink,
                            state -> state.taskInfo.stage == TaskState.TaskStage.FINISHED);

            assertThat(returnState.taskSubStage,
                    is(SubStage.FINISHED));

            // Clean up the compute and description documents
            this.deleteServiceSynchronously(cs.documentSelfLink);
            this.deleteServiceSynchronously(cs.descriptionLink);

            // Stop factory service.
            this.deleteServiceSynchronously(ResourceRemovalTaskService.FACTORY_LINK);

            // stop the removal task
            this.stopServiceSynchronously(returnState.documentSelfLink);

        }

        @Test
        public void testResourceRemovalFailure() throws Throwable {
            ResourceRemovalTaskState startState = buildValidStartState();
            ComputeService.ComputeStateWithDescription cs = ModelUtils
                    .createComputeWithDescription(this,
                            MockAdapter.MockFailureInstanceAdapter.SELF_LINK,
                            null);

            ResourceRemovalTaskState returnState = this
                    .postServiceSynchronously(
                            ResourceRemovalTaskService.FACTORY_LINK,
                            startState,
                            ResourceRemovalTaskState.class);

            this.waitForServiceState(
                    ResourceRemovalTaskState.class,
                    returnState.documentSelfLink,
                    state -> state.taskInfo.stage == TaskState.TaskStage.FAILED);

            // Clean up the compute and description documents
            this.deleteServiceSynchronously(cs.documentSelfLink);
            this.deleteServiceSynchronously(cs.descriptionLink);
        }
    }
}
