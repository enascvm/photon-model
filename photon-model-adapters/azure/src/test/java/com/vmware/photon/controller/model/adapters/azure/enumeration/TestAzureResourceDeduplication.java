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

package com.vmware.photon.controller.model.adapters.azure.enumeration;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;

import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.azure.base.AzureBaseTest;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.ImageService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.ResourceGroupService;
import com.vmware.photon.controller.model.resources.SecurityGroupService;
import com.vmware.photon.controller.model.resources.StorageDescriptionService;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.tasks.EndpointRemovalTaskService;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;

public class TestAzureResourceDeduplication extends AzureBaseTest {

    private static final int DEFAULT_TIMEOUT_SECONDS = 200;
    EndpointService.EndpointState endpointState2;
    public int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;

    public static final String ENDPOINT_LINKS_FIELD_CLAUSE = "endpointLinks.item";

    @Test
    public void testResourceDeduplication() throws Throwable {
        this.host.log("Running test: " + this.currentTestName);
        this.host.setTimeoutSeconds(this.timeoutSeconds);

        // if mock, simply return.
        if (this.isMock) {
            return;
        }

        this.host.waitForResponse(Operation.createPatch(this.host, endpointState.documentSelfLink)
                .setBody(endpointState));

        // perform resource enumeration on given AWS endpoint
        runEnumeration(this.endpointState);

        validateEndpointLinks(this.endpointState.documentSelfLink, this.computeHost.documentSelfLink);

        // Add duplicate end point and verify the endpoints again.
        this.endpointState2 = createEndpointState();
        this.endpointState2.computeLink = this.computeHost.documentSelfLink;
        this.host.waitForResponse(Operation.createPatch(this.host, this.endpointState2.documentSelfLink)
                .setBody(this.endpointState2));
        runEnumeration(this.endpointState2);
        validateEndpointLinks(this.endpointState.documentSelfLink, this.computeHost.documentSelfLink);

        // delete endpoint
        EndpointRemovalTaskService.EndpointRemovalTaskState endpointRemovalTaskState =
                new EndpointRemovalTaskService.EndpointRemovalTaskState();
        endpointRemovalTaskState.endpointLink = this.endpointState.documentSelfLink;
        endpointRemovalTaskState.tenantLinks = this.endpointState.tenantLinks;
        endpointRemovalTaskState.disableGroomer = true;

        EndpointRemovalTaskService.EndpointRemovalTaskState returnState = TestUtils
                .doPost(host, endpointRemovalTaskState,
                        EndpointRemovalTaskService.EndpointRemovalTaskState.class,
                        UriUtils.buildUri(host, EndpointRemovalTaskService.FACTORY_LINK));

        host.waitForFinishedTask(EndpointRemovalTaskService.EndpointRemovalTaskState.class,
                returnState.documentSelfLink);
        Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), this.endpointState.documentSelfLink))
                .setReferer(this.host.getUri());
        Operation response = this.host.waitForResponse(op);
        assertTrue("Endpoint was expected to be deleted",
                response.getStatusCode() == 404);
        validateEndpointLinks(this.endpointState2.documentSelfLink, this.computeHost.documentSelfLink);

        // delete endpoint
        endpointRemovalTaskState =
                new EndpointRemovalTaskService.EndpointRemovalTaskState();
        endpointRemovalTaskState.endpointLink = this.endpointState2.documentSelfLink;
        endpointRemovalTaskState.tenantLinks = this.endpointState2.tenantLinks;
        endpointRemovalTaskState.disableGroomer = true;

        returnState = TestUtils
                .doPost(host, endpointRemovalTaskState,
                        EndpointRemovalTaskService.EndpointRemovalTaskState.class,
                        UriUtils.buildUri(host, EndpointRemovalTaskService.FACTORY_LINK));

        host.waitForFinishedTask(EndpointRemovalTaskService.EndpointRemovalTaskState.class,
                returnState.documentSelfLink);
        op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), this.endpointState2.documentSelfLink))
                .setReferer(this.host.getUri());
        response = this.host.waitForResponse(op);
        assertTrue("Endpoint was expected to be deleted",
                response.getStatusCode() == 404);
        validateEndpointLinks("", this.computeHost.documentSelfLink);

        this.endpointState2 = createEndpointState();
        this.endpointState2.computeLink = this.computeHost.documentSelfLink;
        this.host.waitForResponse(Operation.createPatch(this.host, this.endpointState2.documentSelfLink)
                .setBody(this.endpointState2));
        runEnumeration(this.endpointState2);
        validateEndpointLinks(this.endpointState2.documentSelfLink, this.computeHost.documentSelfLink);
    }

    private void validateEndpointLinks(String expectedEndpoint, String computeHostLink) {

        List<String> computeDocLinks = getDocumentLinks(ComputeService.ComputeState.class, expectedEndpoint);
        this.host.log("ComputeState docs size: " + computeDocLinks.size());
        for (String docLink : computeDocLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            Assert.assertTrue("Error retrieving state",
                    response.getStatusCode() == 200);
            ComputeService.ComputeState doc = response.getBody(ComputeService.ComputeState.class);

            if (!ComputeDescriptionService.ComputeDescription.ComputeType.ENDPOINT_HOST.equals(doc.type)) {

                Assert.assertEquals(computeHostLink, doc.computeHostLink);
                assertNotNull(doc.documentCreationTimeMicros);
                assertNotNull(doc.endpointLink);
                if (StringUtils.isEmpty(expectedEndpoint)) {
                    Assert.assertEquals("EndpointLink should be empty", expectedEndpoint, doc.endpointLink);
                } else {
                    assertThat(String.format("EndpointLinks should have: %s", expectedEndpoint),
                            doc.endpointLinks, CoreMatchers.hasItem(expectedEndpoint));
                }
            }
        }

        List<String> diskDocLinks = getDocumentLinks(DiskService.DiskState.class, expectedEndpoint);
        this.host.log("DiskState docs size: " + diskDocLinks.size());
        for (String docLink : diskDocLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            Assert.assertTrue("Error retrieving state",
                    response.getStatusCode() == 200);
            DiskService.DiskState doc = response.getBody(DiskService.DiskState.class);

            Assert.assertEquals(computeHostLink, doc.computeHostLink);
            assertNotNull(doc.documentCreationTimeMicros);
            assertNotNull(doc.endpointLink);
            if (StringUtils.isEmpty(expectedEndpoint)) {
                Assert.assertEquals("EndpointLink should be empty", expectedEndpoint, doc.endpointLink);
            } else {
                assertThat(String.format("EndpointLinks should have: %s", expectedEndpoint),
                        doc.endpointLinks, CoreMatchers.hasItem(expectedEndpoint));
            }
        }

        List<String> storageLinks = getDocumentLinks(StorageDescriptionService.StorageDescription.class, expectedEndpoint);
        this.host.log("StorageDescription docs size: " + storageLinks.size());
        for (String docLink : storageLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            Assert.assertTrue("Error retrieving state",
                    response.getStatusCode() == 200);
            StorageDescriptionService.StorageDescription doc = response.getBody(StorageDescriptionService.StorageDescription.class);

            Assert.assertEquals(computeHostLink, doc.computeHostLink);
            assertNotNull(doc.documentCreationTimeMicros);
            assertNotNull(doc.endpointLink);
            if (StringUtils.isEmpty(expectedEndpoint)) {
                Assert.assertEquals("EndpointLink should be empty", expectedEndpoint, doc.endpointLink);
            } else {
                assertThat(String.format("EndpointLinks should have: %s", expectedEndpoint),
                        doc.endpointLinks, CoreMatchers.hasItem(expectedEndpoint));
            }
        }

        List<String> networkDocLinks = getDocumentLinks(NetworkService.NetworkState.class, expectedEndpoint);
        this.host.log("NetworkState docs size: " + networkDocLinks.size());
        for (String docLink : networkDocLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            Assert.assertTrue("Error retrieving state",
                    response.getStatusCode() == 200);
            NetworkService.NetworkState doc = response.getBody(NetworkService.NetworkState.class);

            Assert.assertEquals(computeHostLink, doc.computeHostLink);
            assertNotNull(doc.documentCreationTimeMicros);
            assertNotNull(doc.endpointLink);
            if (StringUtils.isEmpty(expectedEndpoint)) {
                Assert.assertEquals("EndpointLink should be empty", expectedEndpoint, doc.endpointLink);
            } else {
                assertThat(String.format("EndpointLinks should have: %s", expectedEndpoint),
                        doc.endpointLinks, CoreMatchers.hasItem(expectedEndpoint));
            }
        }

        List<String> subnetDocLinks = getDocumentLinks(SubnetService.SubnetState.class, expectedEndpoint);
        this.host.log("SubnetState docs size: " + subnetDocLinks.size());
        for (String docLink : subnetDocLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            Assert.assertTrue("Error retrieving state",
                    response.getStatusCode() == 200);
            SubnetService.SubnetState doc = response.getBody(SubnetService.SubnetState.class);

            Assert.assertEquals(computeHostLink, doc.computeHostLink);
            assertNotNull(doc.documentCreationTimeMicros);
            assertNotNull(doc.endpointLink);
            if (StringUtils.isEmpty(expectedEndpoint)) {
                Assert.assertEquals("EndpointLink should be empty", expectedEndpoint, doc.endpointLink);
            } else {
                assertThat(String.format("EndpointLinks should have: %s", expectedEndpoint),
                        doc.endpointLinks, CoreMatchers.hasItem(expectedEndpoint));
            }
        }

        List<String> nicDocLinks = getDocumentLinks(
                NetworkInterfaceService.NetworkInterfaceState.class, expectedEndpoint);
        this.host.log("NetworkInterfaceState docs size: " + nicDocLinks.size());
        for (String docLink : nicDocLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            Assert.assertTrue("Error retrieving state",
                    response.getStatusCode() == 200);
            NetworkInterfaceService.NetworkInterfaceState doc = response
                    .getBody(NetworkInterfaceService.NetworkInterfaceState.class);

            Assert.assertEquals(computeHostLink, doc.computeHostLink);
            assertNotNull(doc.documentCreationTimeMicros);
            assertNotNull(doc.endpointLink);
            if (StringUtils.isEmpty(expectedEndpoint)) {
                Assert.assertEquals("EndpointLink should be empty", expectedEndpoint, doc.endpointLink);
            } else {
                assertThat(String.format("EndpointLinks should have: %s", expectedEndpoint),
                        doc.endpointLinks, CoreMatchers.hasItem(expectedEndpoint));
            }
        }

        List<String> nicDescDocLinks = getDocumentLinks(
                NetworkInterfaceDescriptionService.NetworkInterfaceDescription.class, expectedEndpoint);
        this.host.log("NetworkInterfaceDescription docs size: " + nicDescDocLinks.size());
        for (String docLink : nicDescDocLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            Assert.assertTrue("Error retrieving state",
                    response.getStatusCode() == 200);
            NetworkInterfaceDescriptionService.NetworkInterfaceDescription doc = response
                    .getBody(NetworkInterfaceDescriptionService.NetworkInterfaceDescription.class);

            Assert.assertEquals(computeHostLink, doc.computeHostLink);
            assertNotNull(doc.documentCreationTimeMicros);
            assertNotNull(doc.endpointLink);
            if (StringUtils.isEmpty(expectedEndpoint)) {
                Assert.assertEquals("EndpointLink should be empty", expectedEndpoint, doc.endpointLink);
            } else {
                assertThat(String.format("EndpointLinks should have: %s", expectedEndpoint),
                        doc.endpointLinks, CoreMatchers.hasItem(expectedEndpoint));
            }
        }

        List<String> sgDocLinks = getDocumentLinks(SecurityGroupService.SecurityGroupState.class, expectedEndpoint);
        this.host.log("SecurityGroupState docs size: " + sgDocLinks.size());
        for (String docLink : sgDocLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            Assert.assertTrue("Error retrieving state",
                    response.getStatusCode() == 200);
            SecurityGroupService.SecurityGroupState doc = response
                    .getBody(SecurityGroupService.SecurityGroupState.class);

            Assert.assertEquals(computeHostLink, doc.computeHostLink);
            assertNotNull(doc.documentCreationTimeMicros);
            assertNotNull(doc.endpointLink);
            if (StringUtils.isEmpty(expectedEndpoint)) {
                Assert.assertEquals("EndpointLink should be empty", expectedEndpoint, doc.endpointLink);
            } else {
                assertThat(String.format("EndpointLinks should have: %s", expectedEndpoint),
                        doc.endpointLinks, CoreMatchers.hasItem(expectedEndpoint));
            }
        }

        List<String> imageDocLinks = getDocumentLinks(ImageService.ImageState.class, expectedEndpoint);
        this.host.log("ImageState docs size: " + imageDocLinks.size());
        for (String docLink : imageDocLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            Assert.assertTrue("Error retrieving state",
                    response.getStatusCode() == 200);
            ImageService.ImageState doc = response
                    .getBody(ImageService.ImageState.class);

            Assert.assertEquals(computeHostLink, doc.computeHostLink);
            assertNotNull(doc.documentCreationTimeMicros);
            assertNotNull(doc.endpointLink);
            if (StringUtils.isEmpty(expectedEndpoint)) {
                Assert.assertEquals("EndpointLink should be empty", expectedEndpoint, doc.endpointLink);
            } else {
                assertThat(String.format("EndpointLinks should have: %s", expectedEndpoint),
                        doc.endpointLinks, CoreMatchers.hasItem(expectedEndpoint));
            }
        }

        List<String> resGrpLinks = getDocumentLinks(ResourceGroupService.ResourceGroupState.class, expectedEndpoint);
        this.host.log("ResourceGroupState docs size: " + resGrpLinks.size());
        for (String docLink : resGrpLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            Assert.assertTrue("Error retrieving state",
                    response.getStatusCode() == 200);
            ResourceGroupService.ResourceGroupState doc = response
                    .getBody(ResourceGroupService.ResourceGroupState.class);

            Assert.assertEquals(computeHostLink, doc.computeHostLink);
            assertNotNull(doc.documentCreationTimeMicros);
            if (StringUtils.isEmpty(expectedEndpoint)) {
                Assert.assertEquals("EndpointLink should be empty", expectedEndpoint, doc.endpointLink);
            } else {
                assertThat(String.format("EndpointLinks should have: %s", expectedEndpoint),
                        doc.endpointLinks, CoreMatchers.hasItem(expectedEndpoint));
            }
        }
    }

    private List<String> getDocumentLinks(Class resourceState, String endpointLink) {
        QueryTask.Query.Builder qBuilder = QueryTask.Query.Builder.create()
                .addKindFieldClause(resourceState);

        if (endpointLink != null && !endpointLink.equals("")) {
            qBuilder.addFieldClause(ENDPOINT_LINKS_FIELD_CLAUSE, endpointLink);
        } else {
            qBuilder.addFieldClause(ENDPOINT_LINKS_FIELD_CLAUSE, "*",
                    QueryTask.QueryTerm.MatchType.WILDCARD,
                    QueryTask.Query.Occurance.MUST_NOT_OCCUR);
        }

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .addOption(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT)
                .setQuery(qBuilder.build())
                .build();

        this.host.createQueryTaskService(queryTask, false, true, queryTask, null);
        return queryTask.results.documentLinks;
    }

    private void runEnumeration(EndpointService.EndpointState endpointState) throws Throwable {
        ResourceEnumerationTaskService.ResourceEnumerationTaskState enumerationTaskState =
                new ResourceEnumerationTaskService.ResourceEnumerationTaskState();

        enumerationTaskState.endpointLink = endpointState.documentSelfLink;
        enumerationTaskState.tenantLinks = endpointState.tenantLinks;
        enumerationTaskState.parentComputeLink = computeHost.documentSelfLink;
        enumerationTaskState.enumerationAction = EnumerationAction.START;
        enumerationTaskState.adapterManagementReference = UriUtils
                .buildUri(AzureEnumerationAdapterService.SELF_LINK);
        enumerationTaskState.resourcePoolLink = computeHost.resourcePoolLink;
        if (this.isMock) {
            enumerationTaskState.options = EnumSet.of(TaskOption.IS_MOCK);
        }

        ResourceEnumerationTaskService.ResourceEnumerationTaskState enumTask = TestUtils
                .doPost(this.host, enumerationTaskState, ResourceEnumerationTaskService.ResourceEnumerationTaskState.class,
                        UriUtils.buildUri(this.host, ResourceEnumerationTaskService.FACTORY_LINK));

        this.host.waitFor("Error waiting for enumeration task", () -> {
            try {
                ResourceEnumerationTaskService.ResourceEnumerationTaskState state = this.host
                        .waitForFinishedTask(ResourceEnumerationTaskService.ResourceEnumerationTaskState.class,
                                enumTask.documentSelfLink);
                if (state != null) {
                    return true;
                }
            } catch (Throwable e) {
                return false;
            }
            return false;
        });
    }
}
