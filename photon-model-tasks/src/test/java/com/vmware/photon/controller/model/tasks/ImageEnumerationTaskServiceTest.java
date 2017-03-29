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

import static com.vmware.xenon.common.UriUtils.buildUriPath;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest;
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
            // Either should be set
            {
                ImageEnumerationTaskState invalidState = new ImageEnumerationTaskState();

                postServiceSynchronously(
                        ImageEnumerationTaskService.FACTORY_LINK,
                        invalidState,
                        ImageEnumerationTaskState.class,
                        IllegalArgumentException.class);
            }

            // Both cannot be set
            {
                ImageEnumerationTaskState invalidState = new ImageEnumerationTaskState();
                invalidState.endpointLink = "someEndpointLink";
                invalidState.endpointType = "someEndpointType";

                postServiceSynchronously(
                        ImageEnumerationTaskService.FACTORY_LINK,
                        invalidState,
                        ImageEnumerationTaskState.class,
                        IllegalArgumentException.class);
            }
        }

    }

    /**
     * This class implements end-to-end tests, including POST and PATCH.
     */
    @RunWith(Parameterized.class)
    public static class EndToEndTest extends BaseModelTest {

        // Run the same test using different Configurations
        @Parameterized.Parameters(name = "{2}")
        public static Collection<Object[]> parameters() {

            return Arrays.asList(new Object[][] {

                    // PRIVATE/End-point-specific image enumeration

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

                    { NO_ADAPTER /* no adapter at all */,
                            TaskStage.FAILED,
                            EP_WITH_CONFIG_AND_NO_ADAPTER },
                    { NO_ADAPTER /* does not matter */,
                            TaskStage.FAILED,
                            EP_WITHOUT_CONFIG },
                    { NO_ADAPTER /* no adapter at all, since no EP */,
                            TaskStage.FAILED,
                            EP_NONE },

                    // PUBLIC/End-point agnostic image enumeration

                    { NO_ADAPTER /* no adapter at all */,
                            TaskStage.FINISHED,
                            EPT_NONE_EP },
                    { MockSuccessImageEnumerationAdapter.class,
                            MockSuccessImageEnumerationAdapter.COMPLETE_STATE.stage,
                            EPT_SINGLE_EP },

                    // Tests covering Region handling

                    // Regions match -> should go to the adapter
                    { MockSuccessImageEnumerationAdapter.class,
                            MockSuccessImageEnumerationAdapter.COMPLETE_STATE.stage,
                            EPT_EP_WITH_REGION_REQ_WITH_REGION },
                    // Regions do not match -> should not go to the adapter
                    { MockFailOperationImageEnumerationAdapter.class,
                            TaskStage.FINISHED,
                            EPT_EP_WITH_REGION_REQ_WITH_REGION1 },
                    // Request does not provide Region -> adapter with region is a match
                    { MockSuccessImageEnumerationAdapter.class,
                            MockSuccessImageEnumerationAdapter.COMPLETE_STATE.stage,
                            EPT_EP_WITH_REGION_REQ_WITHOUT_REGION },
                    // Request does provide Region -> adapter without a region is not a match
                    { MockFailOperationImageEnumerationAdapter.class,
                            TaskStage.FINISHED,
                            EPT_EP_WITHOUT_REGION_REQ_WITH_REGION }
            });
        }

        // Instruct PRIVATE/End-point-specific image enumeration {{
        // No end-point at all
        static final String EP_NONE = "EP_NONE";
        // End-point wit NO config
        static final String EP_WITHOUT_CONFIG = "EP_WITHOUT_CONFIG";
        // End-point with config and NO adapter
        static final String EP_WITH_CONFIG_AND_NO_ADAPTER = "EP_WITH_CONFIG_AND_NO_ADAPTER";
        // }}

        // Instruct PUBLIC/End-point-agnostic image enumeration {{
        // No end-point for End-point type
        static final String EPT_NONE_EP = "EPT_NONE_EP";
        // Single end-point for End-point type
        static final String EPT_SINGLE_EP = "EPT_SINGLE_EP";

        // End-point with region AND request with same region
        static final String EPT_EP_WITH_REGION_REQ_WITH_REGION = RegionTestConfig.EPT_EP_WITH_REGION_REQ_WITH_REGION
                .name();
        // End-point with region AND request with different region
        static final String EPT_EP_WITH_REGION_REQ_WITH_REGION1 = RegionTestConfig.EPT_EP_WITH_REGION_REQ_WITH_REGION1
                .name();
        // End-point with region AND request with no region
        static final String EPT_EP_WITH_REGION_REQ_WITHOUT_REGION = RegionTestConfig.EPT_EP_WITH_REGION_REQ_WITHOUT_REGION
                .name();
        // End-point with no region AND request with region
        static final String EPT_EP_WITHOUT_REGION_REQ_WITH_REGION = RegionTestConfig.EPT_EP_WITHOUT_REGION_REQ_WITH_REGION
                .name();
        // }}

        static boolean isPublicImageEnumeration(String endpoinType) {
            return endpoinType.startsWith("EPT_");
        }

        private enum RegionTestConfig {

            EPT_EP_WITH_REGION_REQ_WITH_REGION("REGION", "REGION"),
            EPT_EP_WITH_REGION_REQ_WITH_REGION1("REGION", "REGION_1"),
            EPT_EP_WITH_REGION_REQ_WITHOUT_REGION("REGION", null),
            EPT_EP_WITHOUT_REGION_REQ_WITH_REGION(null, "REGION");

            public final String epRegion;
            public final String requestRegion;

            private RegionTestConfig(String epRegion, String requestRegion) {
                this.epRegion = epRegion;
                this.requestRegion = requestRegion;
            }
        }

        static final Class<? extends Service> NO_ADAPTER = null;

        // The adapter that should respond to task requests
        private final Class<? extends Service> adapterClass;
        // The expected stage of the image-enum task
        private final TaskStage expectedCompletedStage;
        private final String endpointType;

        private RegionTestConfig regionConfig;

        public EndToEndTest(
                Class<? extends Service> adapterClass,
                TaskStage expectedCompletedStage,
                String endpointType) {

            this.adapterClass = adapterClass;
            this.expectedCompletedStage = expectedCompletedStage;
            this.endpointType = endpointType;

            try {
                this.regionConfig = RegionTestConfig.valueOf(endpointType);
            } catch (IllegalArgumentException e) {
            }
        }

        private EndpointState endpointState;
        private int tasksCountBeforeRun;

        @Before
        public void beforeTest() throws Throwable {

            createEndpointState();

            registerEndpointConfig();

            // Get BEFORE tasks count
            this.tasksCountBeforeRun = countImageEnumerationTaskDocuments();
        }

        private void createEndpointState() throws Throwable {

            if (this.endpointType == EP_NONE || this.endpointType == EPT_NONE_EP) {
                // No end-point registered at all.
                return;
            }

            final EndpointState endpointToCreate = new EndpointState();

            endpointToCreate.id = this.endpointType + "-id";
            endpointToCreate.name = this.endpointType + "-name";
            endpointToCreate.endpointType = this.endpointType;
            endpointToCreate.tenantLinks = singletonList(this.endpointType + "-tenant");

            if (this.regionConfig != null && this.regionConfig.epRegion != null) {
                endpointToCreate.endpointProperties = Collections.singletonMap(
                        EndpointConfigRequest.REGION_KEY, this.regionConfig.epRegion);
            }

            this.endpointState = postServiceSynchronously(
                    EndpointService.FACTORY_LINK,
                    endpointToCreate,
                    EndpointState.class);
        }

        private void registerEndpointConfig() throws Throwable {

            if (this.endpointState == null || this.endpointType == EP_WITHOUT_CONFIG) {
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

        private ImageEnumerationTaskState newImageEnumerationRequest() {

            final ImageEnumerationTaskState taskState = new ImageEnumerationTaskState();

            if (isPublicImageEnumeration(this.endpointType)) {

                taskState.endpointType = this.endpointType;

            } else if (this.endpointState != null) {

                // Private image-enum

                taskState.endpointLink = this.endpointState.documentSelfLink;

                taskState.tenantLinks = this.endpointState.tenantLinks;

            } else if (this.endpointType == EP_NONE) {

                // No EP created at all; Request/Refer non-existing EP.
                taskState.endpointLink = buildUriPath(EndpointService.FACTORY_LINK, EP_NONE);
            }

            if (this.regionConfig != null && this.regionConfig.requestRegion != null) {
                taskState.regionId = this.regionConfig.requestRegion;
            }

            return taskState;
        }

        /**
         * @see ImageEnumerationTaskService#handleStart(com.vmware.xenon.common.Operation)
         * @see ImageEnumerationTaskService#handlePatch(com.vmware.xenon.common.Operation)
         */
        @Test
        public void testCompleteTask() throws Throwable {

            ImageEnumerationTaskState startedState = postServiceSynchronously(
                    ImageEnumerationTaskService.FACTORY_LINK,
                    newImageEnumerationRequest(),
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

            Assert.assertEquals(this.tasksCountBeforeRun + 1, countImageEnumerationTaskDocuments());
        }

        @Test
        public void testCompleteTaskAndSelfDelete() throws Throwable {

            ImageEnumerationTaskState adapterReq = newImageEnumerationRequest();
            adapterReq.options = EnumSet.of(TaskOption.SELF_DELETE_ON_COMPLETION);

            postServiceSynchronously(
                    ImageEnumerationTaskService.FACTORY_LINK,
                    adapterReq,
                    ImageEnumerationTaskState.class);

            getHost().waitFor("Timeout waiting for image enum task to self-delete",
                    () -> this.tasksCountBeforeRun == countImageEnumerationTaskDocuments());
        }

        private int countImageEnumerationTaskDocuments() {
            URI uri = UriUtils.buildFactoryUri(getHost(), ImageEnumerationTaskService.class);

            return getHost().getFactoryState(uri).documentLinks.size();
        }
    }
}
