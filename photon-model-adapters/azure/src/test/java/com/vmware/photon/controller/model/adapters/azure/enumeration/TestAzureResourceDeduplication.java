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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
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

    EndpointService.EndpointState endpointState2;

    @Test
    public void testResourceDeduplication() throws Throwable {
        this.host.log("Running test: " + this.currentTestName);

        // if mock, simply return.
        if (this.isMock) {
            return;
        }
        endpointState.computeLink = this.computeHost.documentSelfLink;
        this.host.waitForResponse(Operation.createPatch(this.host, endpointState.documentSelfLink)
                .setBody(endpointState));

        // perform resource enumeration on given AWS endpoint
        runEnumeration(this.endpointState);

        List<String> expectedEndpoints = new ArrayList<>();
        expectedEndpoints.add(this.endpointState.documentSelfLink);
        this.host.log("Expected endpoint links: " + expectedEndpoints);
        validateEndpointLinks(1, expectedEndpoints, this.endpointState.documentSelfLink, this.computeHost.documentSelfLink);

        // Add duplicate end point and verify the endpoints again.
        this.endpointState2 = createEndpointState();
        this.endpointState2.computeLink = this.computeHost.documentSelfLink;
        this.host.waitForResponse(Operation.createPatch(this.host, this.endpointState2.documentSelfLink)
                .setBody(this.endpointState2));
        runEnumeration(this.endpointState2);
        expectedEndpoints.add(this.endpointState2.documentSelfLink);
        this.host.log("Expected endpoint links: " + expectedEndpoints);
        validateEndpointLinks(2, expectedEndpoints, this.endpointState.documentSelfLink, this.computeHost.documentSelfLink);
    }

    @Test
    public void testEndpointDisassociationOnEndpointDeletion() throws Throwable {
        this.host.log("Running test: " + this.currentTestName);

        // if mock, simply return.
        if (this.isMock) {
            return;
        }

        // perform resource enumeration on given AWS endpoint
        runEnumeration(this.endpointState);

        List<String> expectedEndpoints = new ArrayList<>();
        expectedEndpoints.add(this.endpointState.documentSelfLink);
        this.host.log("Expected endpoint links: " + expectedEndpoints);
        validateEndpointLinks(1, expectedEndpoints, this.endpointState.documentSelfLink, this.computeHost.documentSelfLink);

        endpointState.computeLink = this.computeHost.documentSelfLink;
        this.host.waitForResponse(Operation.createPatch(this.host, endpointState.documentSelfLink)
                .setBody(endpointState));

        // delete endpoint
        EndpointRemovalTaskService.EndpointRemovalTaskState endpointRemovalTaskState =
                new EndpointRemovalTaskService.EndpointRemovalTaskState();
        endpointRemovalTaskState.endpointLink = endpointState.documentSelfLink;
        endpointRemovalTaskState.tenantLinks = endpointState.tenantLinks;
        endpointRemovalTaskState.disableGroomer = true;

        EndpointRemovalTaskService.EndpointRemovalTaskState returnState = TestUtils
                .doPost(host, endpointRemovalTaskState,
                        EndpointRemovalTaskService.EndpointRemovalTaskState.class,
                        UriUtils.buildUri(host, EndpointRemovalTaskService.FACTORY_LINK));

        host.waitForFinishedTask(EndpointRemovalTaskService.EndpointRemovalTaskState.class,
                returnState.documentSelfLink);
        //        assertTrue(returnState.taskInfo.stage == TaskState.TaskStage.FINISHED);

        expectedEndpoints.remove(this.endpointState.documentSelfLink);
        validateEndpointLinks(0, expectedEndpoints, "", this.computeHost.documentSelfLink);

        // Add duplicate end point and verify the endpoints again.
        this.endpointState2 = createEndpointState();
        this.computeHost.endpointLink = this.endpointState2.documentSelfLink;
        this.computeHost.endpointLinks.add(this.endpointState2.documentSelfLink);
        this.host.waitForResponse(Operation.createPatch(this.host, this.computeHost.documentSelfLink)
                .setBody(this.computeHost));

        this.endpointState2.computeLink = this.computeHost.documentSelfLink;
        this.host.waitForResponse(Operation.createPatch(this.host, this.endpointState2.documentSelfLink)
                .setBody(this.endpointState2));
        runEnumeration(this.endpointState2);
        expectedEndpoints.clear();
        expectedEndpoints.add(this.endpointState2.documentSelfLink);
        this.host.log("Expected endpoint links: %s", expectedEndpoints.toString());
        validateEndpointLinks(1, expectedEndpoints, this.endpointState2.documentSelfLink, this.computeHost.documentSelfLink);
    }


    @Test
    public void testEndpointDisassociationOnEndpointDeletionWithMultipleEndpoints() throws Throwable {
        this.host.log("Running test: " + this.currentTestName);

        // if mock, simply return.
        if (this.isMock) {
            return;
        }

        endpointState.computeLink = this.computeHost.documentSelfLink;
        this.host.waitForResponse(Operation.createPatch(this.host, endpointState.documentSelfLink)
                .setBody(endpointState));

        // perform resource enumeration on given AWS endpoint
        runEnumeration(this.endpointState);

        List<String> expectedEndpoints = new ArrayList<>();
        expectedEndpoints.add(this.endpointState.documentSelfLink);
        this.host.log("Expected endpoint links: " + expectedEndpoints);
        validateEndpointLinks(1, expectedEndpoints, this.endpointState.documentSelfLink, this.computeHost.documentSelfLink);

        // Add duplicate end point and verify the endpoints again.
        this.endpointState2 = createEndpointState();
        this.endpointState2.computeLink = computeHost.documentSelfLink;
        this.host.waitForResponse(Operation.createPatch(this.host, this.endpointState2.documentSelfLink)
                .setBody(this.endpointState2));
        runEnumeration(this.endpointState2);
        expectedEndpoints.add(this.endpointState2.documentSelfLink);
        this.host.log("Expected endpoint links: " + expectedEndpoints);
        validateEndpointLinks(2, expectedEndpoints, this.endpointState.documentSelfLink, this.computeHost.documentSelfLink);

        // delete endpoint
        EndpointRemovalTaskService.EndpointRemovalTaskState endpointRemovalTaskState =
                new EndpointRemovalTaskService.EndpointRemovalTaskState();
        endpointRemovalTaskState.endpointLink = endpointState.documentSelfLink;
        endpointRemovalTaskState.tenantLinks = endpointState.tenantLinks;
        endpointRemovalTaskState.disableGroomer = true;

        EndpointRemovalTaskService.EndpointRemovalTaskState returnState = TestUtils
                .doPost(host, endpointRemovalTaskState,
                        EndpointRemovalTaskService.EndpointRemovalTaskState.class,
                        UriUtils.buildUri(host, EndpointRemovalTaskService.FACTORY_LINK));

        host.waitForFinishedTask(EndpointRemovalTaskService.EndpointRemovalTaskState.class,
                returnState.documentSelfLink);
        //        assertTrue(returnState.taskInfo.stage == TaskState.TaskStage.FINISHED);

        expectedEndpoints.remove(this.endpointState.documentSelfLink);
        validateEndpointLinks(1, expectedEndpoints, this.endpointState.documentSelfLink, this.computeHost.documentSelfLink);
    }

    private void validateEndpointLinks(int size, List<String> expectedEndpoints, String expectedEndpoint, String computeHostLink) {

        List<String> computeDocLinks = getDocumentLinks(ComputeService.ComputeState.class);
        this.host.log("ComputeState docs size: " + computeDocLinks.size());
        for (String docLink : computeDocLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            Assert.assertTrue("Error retrieving state",
                    response.getStatusCode() == 200);
            ComputeService.ComputeState doc = response.getBody(ComputeService.ComputeState.class);

            if (!ComputeDescriptionService.ComputeDescription.ComputeType.VM_HOST.equals(doc.type)) {

                Assert.assertEquals(computeHostLink, doc.computeHostLink);
                assertNotNull(doc.endpointLink);
                if (StringUtils.isEmpty(expectedEndpoint)) {
                    Assert.assertEquals(expectedEndpoint, doc.endpointLink);
                }

                Assert.assertEquals(size, doc.endpointLinks.size());
                for (String endpointLink : expectedEndpoints) {
                    Assert.assertTrue(doc.endpointLinks.contains(endpointLink));
                }
            }
        }

        List<String> computeDescDocLinks = getDocumentLinks(ComputeDescriptionService.ComputeDescription.class);
        this.host.log("ComputeDescription docs size: " + computeDescDocLinks.size());
        for (String docLink : computeDescDocLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            Assert.assertTrue("Error retrieving state",
                    response.getStatusCode() == 200);
            ComputeDescriptionService.ComputeDescription doc = response.getBody(ComputeDescriptionService.ComputeDescription.class);

            if (doc.instanceType != null) {
                Assert.assertEquals(computeHostLink, doc.computeHostLink);
                assertNotNull(doc.endpointLink);
                if (StringUtils.isEmpty(expectedEndpoint)) {
                    Assert.assertEquals(expectedEndpoint, doc.endpointLink);
                }
                Assert.assertEquals(size, doc.endpointLinks.size());
                for (String endpointLink : expectedEndpoints) {
                    Assert.assertTrue(doc.endpointLinks.contains(endpointLink));
                }
            }
        }

        List<String> diskDocLinks = getDocumentLinks(DiskService.DiskState.class);
        this.host.log("DiskState docs size: " + diskDocLinks.size());
        for (String docLink : diskDocLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            Assert.assertTrue("Error retrieving state",
                    response.getStatusCode() == 200);
            DiskService.DiskState doc = response.getBody(DiskService.DiskState.class);
            Assert.assertEquals(computeHostLink, doc.computeHostLink);
            assertNotNull(doc.endpointLink);
            if (StringUtils.isEmpty(expectedEndpoint)) {
                Assert.assertEquals(expectedEndpoint, doc.endpointLink);
            }

            Assert.assertEquals(size, doc.endpointLinks.size());
            for (String endpointLink : expectedEndpoints) {
                Assert.assertTrue(doc.endpointLinks.contains(endpointLink));
            }
        }

        List<String> storageLinks = getDocumentLinks(StorageDescriptionService.StorageDescription.class);
        this.host.log("StorageDescription docs size: " + storageLinks.size());
        for (String docLink : storageLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            Assert.assertTrue("Error retrieving state",
                    response.getStatusCode() == 200);
            StorageDescriptionService.StorageDescription doc = response.getBody(StorageDescriptionService.StorageDescription.class);
            Assert.assertEquals(computeHostLink, doc.computeHostLink);
            assertNotNull(doc.endpointLink);
            if (StringUtils.isEmpty(expectedEndpoint)) {
                Assert.assertEquals(expectedEndpoint, doc.endpointLink);
            }

            Assert.assertEquals(size, doc.endpointLinks.size());
            for (String endpointLink : expectedEndpoints) {
                Assert.assertTrue(doc.endpointLinks.contains(endpointLink));
            }
        }

        List<String> networkDocLinks = getDocumentLinks(NetworkService.NetworkState.class);
        this.host.log("NetworkState docs size: " + networkDocLinks.size());
        for (String docLink : networkDocLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            Assert.assertTrue("Error retrieving state",
                    response.getStatusCode() == 200);
            NetworkService.NetworkState doc = response.getBody(NetworkService.NetworkState.class);
            Assert.assertEquals(computeHostLink, doc.computeHostLink);
            assertNotNull(doc.endpointLink);
            if (StringUtils.isEmpty(expectedEndpoint)) {
                Assert.assertEquals(expectedEndpoint, doc.endpointLink);
            }

            Assert.assertEquals(size, doc.endpointLinks.size());
            for (String endpointLink : expectedEndpoints) {
                Assert.assertTrue(doc.endpointLinks.contains(endpointLink));
            }
        }

        List<String> subnetDocLinks = getDocumentLinks(SubnetService.SubnetState.class);
        this.host.log("SubnetState docs size: " + subnetDocLinks.size());
        for (String docLink : subnetDocLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            Assert.assertTrue("Error retrieving state",
                    response.getStatusCode() == 200);
            SubnetService.SubnetState doc = response.getBody(SubnetService.SubnetState.class);
            Assert.assertEquals(computeHostLink, doc.computeHostLink);
            assertNotNull(doc.endpointLink);
            if (StringUtils.isEmpty(expectedEndpoint)) {
                Assert.assertEquals(expectedEndpoint, doc.endpointLink);
            }

            Assert.assertEquals(size, doc.endpointLinks.size());
            for (String endpointLink : expectedEndpoints) {
                Assert.assertTrue(doc.endpointLinks.contains(endpointLink));
            }
        }

        List<String> nicDocLinks = getDocumentLinks(
                NetworkInterfaceService.NetworkInterfaceState.class);
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
            assertNotNull(doc.endpointLink);
            if (StringUtils.isEmpty(expectedEndpoint)) {
                Assert.assertEquals(expectedEndpoint, doc.endpointLink);
            }

            Assert.assertEquals(size, doc.endpointLinks.size());
            for (String endpointLink : expectedEndpoints) {
                Assert.assertTrue(doc.endpointLinks.contains(endpointLink));
            }
        }

        List<String> nicDescDocLinks = getDocumentLinks(
                NetworkInterfaceDescriptionService.NetworkInterfaceDescription.class);
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
            assertNotNull(doc.endpointLink);
            if (StringUtils.isEmpty(expectedEndpoint)) {
                Assert.assertEquals(expectedEndpoint, doc.endpointLink);
            }

            Assert.assertEquals(size, doc.endpointLinks.size());
            for (String endpointLink : expectedEndpoints) {
                Assert.assertTrue(doc.endpointLinks.contains(endpointLink));
            }
        }

        List<String> sgDocLinks = getDocumentLinks(SecurityGroupService.SecurityGroupState.class);
        this.host.log("SecurityGroupState docs size: " + sgDocLinks.size());
        for (String docLink : sgDocLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            Assert.assertTrue("Error retrieving state",
                    response.getStatusCode() == 200);
            SecurityGroupService.SecurityGroupState doc = response
                    .getBody(SecurityGroupService.SecurityGroupState.class);

            this.host.log("Id: " + doc.id + " ,endpointLinks: " + doc.endpointLinks);
            Assert.assertEquals(computeHostLink, doc.computeHostLink);
            assertNotNull(doc.endpointLink);
            if (StringUtils.isEmpty(expectedEndpoint)) {
                Assert.assertEquals(expectedEndpoint, doc.endpointLink);
            }

            /*Assert.assertEquals(size, doc.endpointLinks.size());
            for (String endpointLink : expectedEndpoints) {
                Assert.assertTrue(doc.endpointLinks.contains(endpointLink));
            }*/
        }

        List<String> imageDocLinks = getDocumentLinks(ImageService.ImageState.class);
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
            assertNotNull(doc.endpointLink);
            if (StringUtils.isEmpty(expectedEndpoint)) {
                Assert.assertEquals(expectedEndpoint, doc.endpointLink);
            }

            Assert.assertEquals(size, doc.endpointLinks.size());
            for (String endpointLink : expectedEndpoints) {
                Assert.assertTrue(doc.endpointLinks.contains(endpointLink));
            }
        }

        List<String> resGrpLinks = getDocumentLinks(ResourceGroupService.ResourceGroupState.class);
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
            Assert.assertEquals(size, doc.endpointLinks.size());
            for (String endpointLink : expectedEndpoints) {
                Assert.assertTrue(doc.endpointLinks.contains(endpointLink));
            }
        }
    }

    private List<String> getDocumentLinks(Class resourceState) {
        QueryTask.Query.Builder qBuilder = QueryTask.Query.Builder.create()
                .addKindFieldClause(resourceState);

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
