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
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.CUSTOM_PROP_ENDPOINT_LINK;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.DiskService.DiskType;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ImageService;
import com.vmware.photon.controller.model.resources.ImageService.ImageState;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.RouterService;
import com.vmware.photon.controller.model.resources.RouterService.RouterState;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService.EndpointAllocationTaskState;
import com.vmware.photon.controller.model.tasks.EndpointRemovalTaskService.EndpointRemovalTaskState;
import com.vmware.photon.controller.model.tasks.MockAdapter.MockSuccessEndpointAdapter;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;

/**
 * This class implements tests for the {@link EndpointRemovalTaskService} class.
 */
@RunWith(EndpointAllocationTaskServiceTest.class)
@SuiteClasses({
        EndpointRemovalTaskServiceTest.ConstructorTest.class,
        EndpointRemovalTaskServiceTest.HandleStartTest.class,
        EndpointRemovalTaskServiceTest.EndToEndTest.class })
public class EndpointRemovalTaskServiceTest extends Suite {
    public static final String FIELD_NAME_ENDPOINT_LINK = "endpointLink";

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
            createAssociatedDocuments(this, endpoint.documentSelfLink, endpoint.tenantLinks);
            String endpointLink = endpoint.documentSelfLink;
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

            // the associated documents should still be found, but with endpointLink removed
            long assocDocCount = getAssociatedDocumentsCount(this, endpoint.documentSelfLink,
                    endpoint.tenantLinks);

            assertThat("Documents count should be greater than 0", assocDocCount > 0);

            Map<String, Object> documents = getAssociatedDocuments(this, endpoint.documentSelfLink,
                    endpoint.tenantLinks).documents;

            for (String docLink : documents.keySet()) {
                ServiceDocument document = Utils
                        .fromJson(documents.get(docLink),
                                ServiceDocument.class);
                String documentKind = document.documentKind;
                if (documentKind.equals(Utils.buildKind(DiskState.class))) {
                    DiskState disk = Utils.fromJson(documents.get(docLink),
                            DiskState.class);
                    assertThat("Endpoint should not exist", !disk.endpointLinks
                            .contains(endpointLink));
                } else if (documentKind.equals(Utils.buildKind(ComputeState.class))) {
                    ComputeState computeState = Utils.fromJson(documents.get(docLink),
                            ComputeState.class);

                    Assert.assertEquals(0, computeState.endpointLinks.size());
                    assertThat("Endpoint should not exist", !computeState.endpointLinks
                            .contains(endpointLink));
                } else {
                    ResourceState resourceState = Utils.fromJson(documents.get(docLink),
                            ResourceState.class);

                    Assert.assertEquals(0, resourceState.endpointLinks.size());
                    assertThat("Endpoint should not exist", !resourceState.endpointLinks
                            .contains(endpointLink));
                }

            }

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
        endpoint.tenantLinks = Collections.singletonList("tenant-1");
        return endpoint;
    }

    private static EndpointAllocationTaskState createEndpointAllocationRequest(
            EndpointState endpoint) {
        EndpointAllocationTaskState endpointAllocationTaskState = new EndpointAllocationTaskState();
        endpointAllocationTaskState.endpointState = endpoint;
        endpointAllocationTaskState.tenantLinks = endpoint.tenantLinks;
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

    private static void createAssociatedDocuments(BaseModelTest test, String endpointLink,
                                                  List<String> tenantLinks) throws Throwable {
        createComputeState(test, endpointLink, tenantLinks);
        createDiskState(test, endpointLink, tenantLinks);
        createPrivateImageState(test, endpointLink, tenantLinks);
        createNetworkState(test, endpointLink, tenantLinks);
        createRouterState(test, endpointLink, tenantLinks);
        createAuthCredentials(test, endpointLink, tenantLinks);
    }

    private static void createComputeState(BaseModelTest test, String endpointLink,
                                           List<String> tenantLinks) throws Throwable {
        ComputeState cs = new ComputeState();
        cs.id = UUID.randomUUID().toString();
        cs.name = "computeState";
        cs.descriptionLink = "descriptionLink";
        cs.tenantLinks = tenantLinks;
        cs.endpointLink = endpointLink;
        cs.endpointLinks = new HashSet<String>();
        cs.endpointLinks.add(endpointLink);

        test.postServiceSynchronously(ComputeService.FACTORY_LINK, cs,
                ComputeService.ComputeState.class);
    }

    private static void createDiskState(BaseModelTest test, String endpointLink,
                                        List<String> tenantLinks) throws Throwable {
        DiskState d = new DiskState();
        d.id = UUID.randomUUID().toString();
        d.type = DiskType.HDD;
        d.name = "disk";
        d.capacityMBytes = 100L;
        d.tenantLinks = tenantLinks;
        d.endpointLink = endpointLink;
        d.endpointLinks = new HashSet<String>();
        d.endpointLinks.add(endpointLink);
        test.postServiceSynchronously(DiskService.FACTORY_LINK, d, DiskState.class);
    }

    private static void createPrivateImageState(BaseModelTest test, String endpointLink,
                                                List<String> tenantLinks) throws Throwable {
        ImageState image = new ImageState();
        image.name = "disk";
        image.tenantLinks = tenantLinks;
        image.endpointLink = endpointLink;
        image.endpointLinks = new HashSet<String>();
        image.endpointLinks.add(endpointLink);
        test.postServiceSynchronously(ImageService.FACTORY_LINK, image, ImageState.class);
    }

    private static void createRouterState(BaseModelTest test, String endpointLink,
                                          List<String> tenantLinks) throws Throwable {
        RouterState router = new RouterState();
        router.name = "router";
        router.tenantLinks = tenantLinks;
        router.endpointLink = endpointLink;
        router.endpointLinks = new HashSet<String>();
        router.endpointLinks.add(endpointLink);
        router.type = "type";

        test.postServiceSynchronously(RouterService.FACTORY_LINK, router, RouterState.class);
    }

    private static void createNetworkState(BaseModelTest test, String endpointLink,
                                           List<String> tenantLinks) throws Throwable {
        NetworkState net = new NetworkState();
        net.name = "network";
        net.subnetCIDR = "0.0.0.0/0";
        net.tenantLinks = tenantLinks;
        net.endpointLink = endpointLink;
        net.endpointLinks = new HashSet<String>();
        net.endpointLinks.add(endpointLink);
        net.authCredentialsLink = "authCredsLink";
        net.resourcePoolLink = "resourcePoolLink";
        net.regionId = "region-id";
        net.instanceAdapterReference = UriUtils.buildUri(test.getHost(), "instance-adapter");
        test.postServiceSynchronously(NetworkService.FACTORY_LINK, net, NetworkState.class);
    }

    private static void createAuthCredentials(BaseModelTest test, String endpointLink,
                                              List<String> tenantLinks) throws Throwable {
        AuthCredentialsServiceState auth = new AuthCredentialsServiceState();
        auth.userEmail = "email";
        auth.privateKey = "pass";
        auth.customProperties = new HashMap<>();
        auth.tenantLinks = tenantLinks;
        auth.customProperties.put(CUSTOM_PROP_ENDPOINT_LINK, endpointLink);
        test.postServiceSynchronously(AuthCredentialsService.FACTORY_LINK, auth,
                AuthCredentialsServiceState.class);
    }

    private static long getAssociatedDocumentsCount(BaseModelTest test, String endpointLink,
                                                    List<String> tenantLinks) throws Throwable {
        QueryTask.Query resourceQuery = QueryTask.Query.Builder.create().build();
        QueryTask.Query endpointFilter = new QueryTask.Query();
        endpointFilter.occurance = QueryTask.Query.Occurance.MUST_OCCUR;
        //query for document that have the endpointLink field as a primary property
        QueryTask.Query endpointLinkFilter = new QueryTask.Query()
                .setTermPropertyName(FIELD_NAME_ENDPOINT_LINK)
                .setTermMatchValue(endpointLink);
        endpointLinkFilter.occurance = QueryTask.Query.Occurance.SHOULD_OCCUR;
        endpointFilter.addBooleanClause(endpointLinkFilter);

        // query for document that have the endpointLink field as a custom property
        String computeHostCompositeField = QueryTask.QuerySpecification
                .buildCompositeFieldName(CUSTOM_PROP_ENDPOINT_LINK,
                        ComputeProperties.ENDPOINT_LINK_PROP_NAME);
        endpointLinkFilter = new QueryTask.Query()
                .setTermPropertyName(computeHostCompositeField)
                .setTermMatchValue(endpointLink);
        endpointLinkFilter.occurance = QueryTask.Query.Occurance.SHOULD_OCCUR;
        endpointFilter.addBooleanClause(endpointLinkFilter);

        resourceQuery.addBooleanClause(endpointFilter);
        QueryTask resourceQueryTask = QueryTask.Builder.createDirectTask()
                .setQuery(resourceQuery)
                .addOption(QueryOption.EXPAND_CONTENT)
                .build();
        resourceQueryTask.tenantLinks = tenantLinks;

        QueryTask queryTask = test.querySynchronously(resourceQueryTask);

        return queryTask.results.documentCount;
    }

    private static ServiceDocumentQueryResult getAssociatedDocuments(BaseModelTest test,
                         String endpointLink, List<String> tenantLinks) throws Throwable {
        QueryTask.Query resourceQuery = QueryTask.Query.Builder.create().build();
        QueryTask.Query endpointFilter = new QueryTask.Query();
        endpointFilter.occurance = QueryTask.Query.Occurance.MUST_OCCUR;
        //query for document that have the endpointLink field as a primary property
        QueryTask.Query endpointLinkFilter = new QueryTask.Query()
                .setTermPropertyName(FIELD_NAME_ENDPOINT_LINK)
                .setTermMatchValue(endpointLink);
        endpointLinkFilter.occurance = QueryTask.Query.Occurance.SHOULD_OCCUR;
        endpointFilter.addBooleanClause(endpointLinkFilter);

        // query for document that have the endpointLink field as a custom property
        String computeHostCompositeField = QueryTask.QuerySpecification
                .buildCompositeFieldName(CUSTOM_PROP_ENDPOINT_LINK,
                        ComputeProperties.ENDPOINT_LINK_PROP_NAME);
        endpointLinkFilter = new QueryTask.Query()
                .setTermPropertyName(computeHostCompositeField)
                .setTermMatchValue(endpointLink);
        endpointLinkFilter.occurance = QueryTask.Query.Occurance.SHOULD_OCCUR;
        endpointFilter.addBooleanClause(endpointLinkFilter);

        resourceQuery.addBooleanClause(endpointFilter);
        QueryTask resourceQueryTask = QueryTask.Builder.createDirectTask()
                .setQuery(resourceQuery)
                .addOption(QueryOption.EXPAND_CONTENT)
                .build();
        resourceQueryTask.tenantLinks = tenantLinks;

        QueryTask queryTask = test.querySynchronously(resourceQueryTask);

        return queryTask.results;
    }


    private static <T extends ServiceDocument> T getServiceSynchronously(BaseModelTest test,
                                                                         String serviceLink, Class<T> type) throws Throwable {
        return test.getHost().getServiceState(null, type,
                UriUtils.buildUri(test.getHost(), serviceLink));
    }
}
