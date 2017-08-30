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

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.ModelUtils;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.IPAddressService.IPAddressState;
import com.vmware.photon.controller.model.resources.IPAddressService.IPAddressState.IPAddressStatus;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.tasks.ResourceRemovalTaskService.ResourceRemovalTaskState;
import com.vmware.photon.controller.model.tasks.ResourceRemovalTaskService.SubStage;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;

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

        @Test
        public void testPaginatedResourceRemovalSuccess() throws Throwable {
            testPaginatedResourceRemoval(null);
        }

        @Test
        public void testPaginatedLocalResourceRemovalSuccess() throws Throwable {
            testPaginatedResourceRemoval(EnumSet.of(TaskOption.DOCUMENT_CHANGES_ONLY), false);
        }

        @Test
        public void testResourceRemovalFailureWithPagination() throws Throwable {
            final int pageSize = 2;
            final int totalComputeCount = 10;

            ResourceRemovalTaskState startState = buildValidStartState();
            startState.resourceQuerySpec.resultLimit = pageSize;

            List<ComputeService.ComputeStateWithDescription> computes =
                    new ArrayList<>(totalComputeCount);
            for (int i = 0; i < totalComputeCount; i++) {
                if (i == 3) {
                    computes.add(ModelUtils
                            .createComputeWithDescription(this,
                                    MockAdapter.MockFailureInstanceAdapter.SELF_LINK,
                                    null));
                } else {
                    computes.add(ModelUtils.createComputeWithDescription(this,
                            MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
                            null));
                }
            }

            assertDocumentCount(totalComputeCount,
                    computes.stream().map(cs -> cs.documentSelfLink).collect(Collectors.toList()));

            ResourceRemovalTaskState returnState = this
                    .postServiceSynchronously(
                            ResourceRemovalTaskService.FACTORY_LINK,
                            startState,
                            ResourceRemovalTaskState.class);

            returnState = this
                    .waitForServiceState(
                            ResourceRemovalTaskState.class,
                            returnState.documentSelfLink,
                            //state -> state.taskInfo.stage == TaskStage.FAILED);
                            state -> state.taskInfo.stage == TaskState.TaskStage.FAILED);

            assertDocumentCount(totalComputeCount,
                    computes.stream().map(cs -> cs.documentSelfLink).collect(Collectors.toList()));

            // Clean up the compute and description documents
            for (ComputeState computeState : computes) {
                this.deleteServiceSynchronously(computeState.documentSelfLink);
                this.deleteServiceSynchronously(computeState.descriptionLink);
            }

            // Stop factory service.
            this.deleteServiceSynchronously(ResourceRemovalTaskService.FACTORY_LINK);

            // stop the removal task
            this.stopServiceSynchronously(returnState.documentSelfLink);
        }

        @Test
        public void testNegativeIpReleaseWithPagination() throws Throwable {
            final int pageSize = 2;
            final int totalComputeCount = 10;

            ResourceRemovalTaskState startState = buildValidStartState();
            startState.resourceQuerySpec.resultLimit = pageSize;

            List<ComputeService.ComputeStateWithDescription> computes =
                    new ArrayList<>(totalComputeCount);
            for (int i = 0; i < totalComputeCount; i++) {
                computes.add(ModelUtils.createComputeWithDescription(this,
                        MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
                        null));
            }

            // Make one of the ip address links invalid, deallocation should fail
            modifyToInvalidAddressLink(computes.get(9));

            assertDocumentCount(totalComputeCount,
                    computes.stream().map(cs -> cs.documentSelfLink).collect(Collectors.toList()));

            ResourceRemovalTaskState returnState = this
                    .postServiceSynchronously(
                            ResourceRemovalTaskService.FACTORY_LINK,
                            startState,
                            ResourceRemovalTaskState.class);

            // Should be fail instead of finish !!!
            // Even with an error we finish the task
            returnState = this
                    .waitForServiceState(
                            ResourceRemovalTaskState.class,
                            returnState.documentSelfLink,
                            state -> state.taskInfo.stage == TaskState.TaskStage.FAILED); // SHOULD BE FAILED

            assertDocumentCount(totalComputeCount,
                    computes.stream().map(cs -> cs.documentSelfLink).collect(Collectors.toList()));

            // Clean up the compute and description documents
            for (ComputeState computeState : computes) {
                this.deleteServiceSynchronously(computeState.documentSelfLink);
                this.deleteServiceSynchronously(computeState.descriptionLink);
            }

            // Stop factory service.
            this.deleteServiceSynchronously(ResourceRemovalTaskService.FACTORY_LINK);

            // stop the removal task
            this.stopServiceSynchronously(returnState.documentSelfLink);
        }

        private void testPaginatedResourceRemoval(EnumSet<TaskOption> taskOptions)
                throws Throwable {
            testPaginatedResourceRemoval(taskOptions, true);
        }

        private void testPaginatedResourceRemoval(EnumSet<TaskOption> taskOptions,
                boolean verifyIpRelease) throws Throwable {
            final int pageSize = 2;
            final int totalComputeCount = 10;

            ResourceRemovalTaskState startState = buildValidStartState();
            startState.resourceQuerySpec.resultLimit = pageSize;
            startState.options = taskOptions;

            List<ComputeService.ComputeStateWithDescription> computes =
                    new ArrayList<>(totalComputeCount);
            for (int i = 0; i < totalComputeCount; i++) {
                computes.add(ModelUtils.createComputeWithDescription(this,
                        MockAdapter.MockSuccessInstanceAdapter.SELF_LINK,
                        null));
            }
            assertDocumentCount(totalComputeCount,
                    computes.stream().map(cs -> cs.documentSelfLink).collect(Collectors.toList()));

            // Get the list of allocated IP addresses, to verify those are released as part of the resource removal
            List<String> ipAddressLinks = new ArrayList<>();
            if (verifyIpRelease) {
                ipAddressLinks = getAllocatedIpAddressLinks(computes);
            }

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

            assertDocumentCount(0,
                    computes.stream().map(cs -> cs.documentSelfLink).collect(Collectors.toList()));

            if (verifyIpRelease) {
                assertReleasedIpAddresses(ipAddressLinks);
            }

            // Clean up the compute and description documents
            for (ComputeState computeState : computes) {
                this.deleteServiceSynchronously(computeState.documentSelfLink);
                this.deleteServiceSynchronously(computeState.descriptionLink);
            }

            // Stop factory service.
            this.deleteServiceSynchronously(ResourceRemovalTaskService.FACTORY_LINK);

            // stop the removal task
            this.stopServiceSynchronously(returnState.documentSelfLink);
        }

        private void assertDocumentCount(long expectedCount, Collection<String> documentLinks)
                throws Throwable {
            Query query = Query.Builder.create()
                    .addInClause(ServiceDocument.FIELD_NAME_SELF_LINK, documentLinks).build();
            QueryTask queryTask = QueryTask.Builder.createDirectTask().setQuery(query)
                    .addOption(QueryOption.COUNT).build();
            QueryTask returnedTask = querySynchronously(queryTask);
            assertThat(returnedTask.results.documentCount, is(expectedCount));
        }

        private List<String> getAllocatedIpAddressLinks(
                Collection<ComputeStateWithDescription> computes) throws Throwable {
            List<String> ipAddressLinks = new ArrayList<>();
            for (ComputeState compute : computes) {
                for (String networkInterfaceLink : compute.networkInterfaceLinks) {
                    NetworkInterfaceState nis = getServiceSynchronously(networkInterfaceLink,
                            NetworkInterfaceState.class);
                    if (nis != null && nis.addressLink != null) {
                        ipAddressLinks.add(nis.addressLink);
                    }
                }
            }
            return ipAddressLinks;
        }

        private void modifyToInvalidAddressLink(ComputeService.ComputeStateWithDescription compute)
                throws Throwable {

            NetworkInterfaceState nis = getServiceSynchronously(
                    compute.networkInterfaceLinks.get(0), NetworkInterfaceState.class);
            assertThat("Network interface cannot be null", nis != null);
            nis.addressLink = "InvalidAddressLink";
            putServiceSynchronously(nis.documentSelfLink, nis);
        }

        private void assertReleasedIpAddresses(Collection<String> ipAddressLinks)
                throws Throwable {
            for (String ipAddressLink : ipAddressLinks) {
                IPAddressState ipAddressState = getServiceSynchronously(ipAddressLink,
                        IPAddressState.class);
                assertThat("IP address cannot be null", ipAddressState != null);
                assertThat("IP address should be released", ipAddressState.ipAddressStatus,
                        is(IPAddressStatus.RELEASED));
                assertThat("No connected resource for released IP address", ipAddressState.connectedResourceLink == null);

            }
        }
    }
}
