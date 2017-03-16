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

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.concurrent.TimeUnit.MINUTES;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.UriPaths.AdapterTypePath;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryService;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryService.PhotonModelAdapterConfig;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.tasks.ImageEnumerationTaskService.ImageEnumerationTaskState;
import com.vmware.photon.controller.model.tasks.MockAdapter.MockCancelledImageEnumerationAdapter;
import com.vmware.photon.controller.model.tasks.MockAdapter.MockFailOperationImageEnumerationAdapter;
import com.vmware.photon.controller.model.tasks.MockAdapter.MockFailureImageEnumerationAdapter;
import com.vmware.photon.controller.model.tasks.MockAdapter.MockSuccessImageEnumerationAdapter;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * This class implements tests for the {@link ImageEnumerationTaskService} class.
 */
@RunWith(ImageEnumerationTaskServiceTest.class)
@SuiteClasses({ ImageEnumerationTaskServiceTest.ConstructorTest.class,
        ImageEnumerationTaskServiceTest.HandleStartTest.class,
        ImageEnumerationTaskServiceTest.EndToEndTest.class })
public class ImageEnumerationTaskServiceTest extends Suite {

    public ImageEnumerationTaskServiceTest(Class<?> klass, RunnerBuilder builder) throws Throwable {
        super(klass, builder);
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {

        @Test
        public void testServiceOptions() {
            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.IDEMPOTENT_POST,
                    Service.ServiceOption.INSTRUMENTATION,
                    Service.ServiceOption.OWNER_SELECTION,
                    Service.ServiceOption.REPLICATION);

            Assert.assertEquals(expected, new ImageEnumerationTaskService().getOptions());
        }
    }

    /**
     * Enhance BaseModelTest with func required by this family of tests.
     */
    static class BaseModelTest extends com.vmware.photon.controller.model.helpers.BaseModelTest {

        static ImageEnumerationTaskState newImageEnumerationRequest(EndpointState endpointState) {

            ImageEnumerationTaskState taskState = new ImageEnumerationTaskState();

            if (endpointState != null) {
                taskState.endpointLink = endpointState.documentSelfLink;
                taskState.tenantLinks = endpointState.tenantLinks;
            }

            return taskState;
        }

        @Override
        protected final void startRequiredServices() throws Throwable {

            super.startRequiredServices();

            PhotonModelTaskServices.startServices(getHost());
            getHost().waitForServiceAvailable(PhotonModelTaskServices.LINKS);

            PhotonModelAdaptersRegistryAdapters.startServices(getHost());
            getHost().waitForServiceAvailable(PhotonModelAdaptersRegistryAdapters.LINKS);

            MockAdapter.startFactories(this);
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleStartTest extends BaseModelTest {

        /**
         * @see ImageEnumerationTaskService#validateStartPost(com.vmware.xenon.common.Operation)
         */
        @Test
        public void testValidation() throws Throwable {

            // Create task state with missing required 'endpoinLink'.
            ImageEnumerationTaskState taskState = newImageEnumerationRequest(null);

            postServiceSynchronously(
                    ImageEnumerationTaskService.FACTORY_LINK,
                    taskState,
                    ImageEnumerationTaskState.class,
                    IllegalArgumentException.class);
        }
    }

    /**
     * This class implements end-to-end tests, including POST and PATCH.
     */
    @RunWith(Parameterized.class)
    public static class EndToEndTest extends BaseModelTest {

        // Run the same test using different COMPLETE adapters
        @Parameterized.Parameters
        public static Collection<Object[]> parameters() {
            return Arrays.asList(new Object[][] {
                    { MockSuccessImageEnumerationAdapter.class,
                            MockSuccessImageEnumerationAdapter.COMPLETE_STATE.stage,
                            EndpointType.aws.name() },
                    { MockCancelledImageEnumerationAdapter.class,
                            MockCancelledImageEnumerationAdapter.COMPLETE_STATE.stage,
                            EndpointType.azure.name() },
                    { MockFailureImageEnumerationAdapter.class,
                            MockFailureImageEnumerationAdapter.COMPLETE_STATE.stage,
                            EndpointType.vsphere.name() },
                    { MockFailOperationImageEnumerationAdapter.class,
                            TaskStage.FAILED,
                            EndpointType.gpc.name() },

                    { null /* no adapter at all */, TaskStage.FAILED,
                            EP_WITH_CONFIG_AND_NO_ADAPTER },
                    { null /* does not matter */, TaskStage.FAILED, EP_WITHOUT_CONFIG }
            });
        }

        static final String EP_WITHOUT_CONFIG = "EP_WITHOUT_CONFIG";
        static final String EP_WITH_CONFIG_AND_NO_ADAPTER = "EP_WITH_CONFIG_AND_NO_ADAPTER";
        static final Class<? extends Service> NO_ADAPTER = null;

        private final Class<? extends Service> adapterClass;
        private final TaskStage expectedCompletedStage;
        private final String endpointType;

        public EndToEndTest(
                Class<? extends Service> adapterClass,
                TaskStage expectedCompletedStage,
                String endpointType) {

            this.adapterClass = adapterClass;
            this.expectedCompletedStage = expectedCompletedStage;
            this.endpointType = endpointType;
        }

        private EndpointState createEndpointState(String endpointType) throws Throwable {

            EndpointState endpoint = new EndpointState();

            endpoint.id = endpointType + "Id";
            endpoint.name = endpointType + "Name";
            endpoint.endpointType = endpointType;
            endpoint.tenantLinks = singletonList(endpointType + "Tenant");

            return postServiceSynchronously(
                    EndpointService.FACTORY_LINK,
                    endpoint,
                    EndpointState.class);
        }

        private EndpointState endpointState;
        private int tasksCountBeforeRun;

        @Before
        public void beforeTest() throws Throwable {

            this.endpointState = createEndpointState(this.endpointType);

            registerEndpointConfig();

            // Get BEFORE tasks count
            this.tasksCountBeforeRun = countServiceDocuments();
        }

        private void registerEndpointConfig() throws Throwable {

            if (this.endpointType == EP_WITHOUT_CONFIG) {
                // No end-point config registration at all.
                return;
            }

            PhotonModelAdapterConfig config = new PhotonModelAdapterConfig();

            config.id = this.endpointState.endpointType;
            config.documentSelfLink = config.id;
            config.name = this.endpointState.name;

            if (this.adapterClass != NO_ADAPTER) {
                // Register adapter only if presented
                config.adapterEndpoints = singletonMap(
                        AdapterTypePath.IMAGE_ENUMERATION_ADAPTER.key,
                        UriUtils.buildUri(getHost(), this.adapterClass).toString());
            }

            postServiceSynchronously(
                    PhotonModelAdaptersRegistryService.FACTORY_LINK,
                    config,
                    PhotonModelAdapterConfig.class);
        }

        /**
         * @see ImageEnumerationTaskService#handleStart(com.vmware.xenon.common.Operation)
         * @see ImageEnumerationTaskService#handlePatch(com.vmware.xenon.common.Operation)
         */
        @Test
        public void testCompleteTask() throws Throwable {

            ImageEnumerationTaskState startedState = postServiceSynchronously(
                    ImageEnumerationTaskService.FACTORY_LINK,
                    newImageEnumerationRequest(this.endpointState),
                    ImageEnumerationTaskState.class);

            // Verify ImageEnumerationTaskService.initializeState (part of handleStart)
            {
                Assert.assertEquals("ImageEnumerationTaskState.taskInfo",
                        TaskState.TaskStage.CREATED,
                        startedState.taskInfo.stage);

                Assert.assertEquals("ImageEnumerationTaskState.options",
                        EnumSet.noneOf(TaskOption.class),
                        startedState.options);

                // Calculate expected with a tolerance
                long expectedExpMicros = Utils.fromNowMicrosUtc(MINUTES.toMicros(
                        ImageEnumerationTaskService.DEFAULT_EXPIRATION_MINUTES));
                expectedExpMicros -= TimeUnit.MILLISECONDS.toMicros(100);

                Assert.assertTrue("ImageEnumerationTaskState.documentExpirationTimeMicros",
                        startedState.documentExpirationTimeMicros > expectedExpMicros);
            }

            // Wait for task to complete with a state depending on used Mock Adapter
            waitForServiceState(
                    ImageEnumerationTaskState.class,
                    startedState.documentSelfLink,
                    liveState -> this.expectedCompletedStage == liveState.taskInfo.stage);

            Assert.assertEquals(this.tasksCountBeforeRun + 1,  countServiceDocuments());
        }

        @Test
        public void testCompleteTaskAndSelfDelete() throws Throwable {
            ImageEnumerationTaskState adapterReq = newImageEnumerationRequest(this.endpointState);
            adapterReq.options = EnumSet.of(TaskOption.SELF_DELETE_ON_COMPLETION);

            postServiceSynchronously(
                    ImageEnumerationTaskService.FACTORY_LINK,
                    adapterReq,
                    ImageEnumerationTaskState.class);

            getHost().waitFor("Timeout waiting for image enum task to self-delete", () ->
                    this.tasksCountBeforeRun == countServiceDocuments());
        }

        private int countServiceDocuments() {
            return getHost().getFactoryState(UriUtils.buildFactoryUri(getHost(),
                    ImageEnumerationTaskService.class)).documentLinks.size();
        }
    }
}
