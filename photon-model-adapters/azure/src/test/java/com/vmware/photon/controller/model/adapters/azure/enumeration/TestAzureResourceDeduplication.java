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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import com.vmware.photon.controller.model.tasks.EndpointRemovalTaskService;
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
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;

import static com.vmware.photon.controller.model.constants.PhotonModelConstants.CUSTOM_PROP_ENDPOINT_LINK;

public class TestAzureResourceDeduplication extends AzureBaseTest {

    EndpointService.EndpointState endpointState2;

    @Test
    public void testResourceDeduplication() throws Throwable {
        this.host.log("Running test: " + this.currentTestName);

        // if mock, simply return.
        if (this.isMock) {
            return;
        }

        // perform resource enumeration on given AWS endpoint
        runEnumeration(this.endpointState);

        List<String> expectedEndpoints = new ArrayList<>();
        expectedEndpoints.add(this.endpointState.documentSelfLink);
        System.out.println("Expected endpoint links: " + expectedEndpoints);
        validateEndpointLinks(1, expectedEndpoints);

        // Add duplicate end point and verify the endpoints again.
        this.endpointState2 = createEndpointState();
        runEnumeration(this.endpointState2);
        expectedEndpoints.add(this.endpointState2.documentSelfLink);
        System.out.println("Expected endpoint links: " + expectedEndpoints);
        validateEndpointLinks(2, expectedEndpoints);
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
        System.out.println("Expected endpoint links: " + expectedEndpoints);
        validateEndpointLinks(1, expectedEndpoints);

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
        validateEndpointLinks(0, expectedEndpoints);
    }


    @Test
    public void testEndpointDisassociationOnEndpointDeletionWithMultipleEndpoints() throws Throwable {
        this.host.log("Running test: " + this.currentTestName);

        // if mock, simply return.
        if (this.isMock) {
            return;
        }

        // perform resource enumeration on given AWS endpoint
        runEnumeration(this.endpointState);

        List<String> expectedEndpoints = new ArrayList<>();
        expectedEndpoints.add(this.endpointState.documentSelfLink);
        System.out.println("Expected endpoint links: " + expectedEndpoints);
        validateEndpointLinks(1, expectedEndpoints);

        // Add duplicate end point and verify the endpoints again.
        this.endpointState2 = createEndpointState();
        runEnumeration(this.endpointState2);
        expectedEndpoints.add(this.endpointState2.documentSelfLink);
        System.out.println("Expected endpoint links: " + expectedEndpoints);
        validateEndpointLinks(2, expectedEndpoints);

        endpointState.computeLink = this.computeHost.documentSelfLink;
        ComputeService.ComputeState computeHost2 = createComputeHostWithDescription();
        endpointState2.computeLink = computeHost2.documentSelfLink;

        this.host.waitForResponse(Operation.createPatch(this.host, endpointState.documentSelfLink)
                .setBody(endpointState));
        this.host.waitForResponse(Operation.createPatch(this.host, endpointState2.documentSelfLink)
                .setBody(endpointState2));

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
        validateEndpointLinks(1, expectedEndpoints);
    }



    private void validateEndpointLinks(int size, List<String> expectedEndpoints) {

        List<String> computeDocLinks = getDocumentLinks(ComputeService.ComputeState.class);
        System.out.println("ComputeState docs size: " + computeDocLinks.size());
        for (String docLink : computeDocLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            Assert.assertTrue("Error retrieving state",
                    response.getStatusCode() == 200);
            ComputeService.ComputeState doc = response.getBody(ComputeService.ComputeState.class);
            Assert.assertNotNull(doc.endpointLink);

            // TODO Remove the if after 1 compute host for n endpoints.
            if (!ComputeDescriptionService.ComputeDescription.ComputeType.VM_HOST.equals(doc.type)) {
                Assert.assertEquals(size, doc.endpointLinks.size());

                for (String endpointLink : expectedEndpoints) {
                    Assert.assertTrue(doc.endpointLinks.contains(endpointLink));
                }
            }
        }

        List<String> computeDescDocLinks = getDocumentLinks(ComputeDescriptionService.ComputeDescription.class);
        System.out.println("ComputeDescription docs size: " + computeDescDocLinks.size());
        for (String docLink : computeDescDocLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            Assert.assertTrue("Error retrieving state",
                    response.getStatusCode() == 200);
            ComputeDescriptionService.ComputeDescription doc = response.getBody(ComputeDescriptionService.ComputeDescription.class);

            // TODO Remove the if after 1 compute host for n endpoints.
            if (doc.zoneId != null) {
                Assert.assertNotNull(doc.endpointLink);
                Assert.assertEquals(size, doc.endpointLinks.size());
                for (String endpointLink : expectedEndpoints) {
                    Assert.assertTrue(doc.endpointLinks.contains(endpointLink));
                }
            }
        }

        List<String> diskDocLinks = getDocumentLinks(DiskService.DiskState.class);
        System.out.println("DiskState docs size: " + diskDocLinks.size());
        for (String docLink : diskDocLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            Assert.assertTrue("Error retrieving state",
                    response.getStatusCode() == 200);
            DiskService.DiskState doc = response.getBody(DiskService.DiskState.class);
            Assert.assertNotNull(doc.endpointLink);

            Assert.assertEquals(size, doc.endpointLinks.size());
            for (String endpointLink : expectedEndpoints) {
                Assert.assertTrue(doc.endpointLinks.contains(endpointLink));
            }
        }

        List<String> storageLinks = getDocumentLinks(StorageDescriptionService.StorageDescription.class);
        System.out.println("StorageDescription docs size: " + storageLinks.size());
        for (String docLink : storageLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            Assert.assertTrue("Error retrieving state",
                    response.getStatusCode() == 200);
            StorageDescriptionService.StorageDescription doc = response.getBody(StorageDescriptionService.StorageDescription.class);
            Assert.assertNotNull(doc.endpointLink);

            Assert.assertEquals(size, doc.endpointLinks.size());
            for (String endpointLink : expectedEndpoints) {
                Assert.assertTrue(doc.endpointLinks.contains(endpointLink));
            }
        }

        List<String> networkDocLinks = getDocumentLinks(NetworkService.NetworkState.class);
        System.out.println("NetworkState docs size: " + networkDocLinks.size());
        for (String docLink : networkDocLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            Assert.assertTrue("Error retrieving state",
                    response.getStatusCode() == 200);
            NetworkService.NetworkState doc = response.getBody(NetworkService.NetworkState.class);
            Assert.assertNotNull(doc.endpointLink);

            Assert.assertEquals(size, doc.endpointLinks.size());
            for (String endpointLink : expectedEndpoints) {
                Assert.assertTrue(doc.endpointLinks.contains(endpointLink));
            }
        }

        List<String> subnetDocLinks = getDocumentLinks(SubnetService.SubnetState.class);
        System.out.println("SubnetState docs size: " + subnetDocLinks.size());
        for (String docLink : subnetDocLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            Assert.assertTrue("Error retrieving state",
                    response.getStatusCode() == 200);
            SubnetService.SubnetState doc = response.getBody(SubnetService.SubnetState.class);
            Assert.assertNotNull(doc.endpointLink);

            Assert.assertEquals(size, doc.endpointLinks.size());
            for (String endpointLink : expectedEndpoints) {
                Assert.assertTrue(doc.endpointLinks.contains(endpointLink));
            }
        }

        List<String> nicDocLinks = getDocumentLinks(
                NetworkInterfaceService.NetworkInterfaceState.class);
        System.out.println("NetworkInterfaceState docs size: " + nicDocLinks.size());
        for (String docLink : nicDocLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            Assert.assertTrue("Error retrieving state",
                    response.getStatusCode() == 200);
            NetworkInterfaceService.NetworkInterfaceState doc = response
                    .getBody(NetworkInterfaceService.NetworkInterfaceState.class);
            Assert.assertNotNull(doc.endpointLink);

            Assert.assertEquals(size, doc.endpointLinks.size());
            for (String endpointLink : expectedEndpoints) {
                Assert.assertTrue(doc.endpointLinks.contains(endpointLink));
            }
        }

        List<String> nicDescDocLinks = getDocumentLinks(
                NetworkInterfaceDescriptionService.NetworkInterfaceDescription.class);
        System.out.println("NetworkInterfaceDescription docs size: " + nicDescDocLinks.size());
        for (String docLink : nicDescDocLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            Assert.assertTrue("Error retrieving state",
                    response.getStatusCode() == 200);
            NetworkInterfaceDescriptionService.NetworkInterfaceDescription doc = response
                    .getBody(NetworkInterfaceDescriptionService.NetworkInterfaceDescription.class);
            Assert.assertNotNull(doc.endpointLink);

            Assert.assertEquals(size, doc.endpointLinks.size());
            for (String endpointLink : expectedEndpoints) {
                Assert.assertTrue(doc.endpointLinks.contains(endpointLink));
            }
        }

        List<String> sgDocLinks = getDocumentLinks(SecurityGroupService.SecurityGroupState.class);
        System.out.println("SecurityGroupState docs size: " + sgDocLinks.size());
        for (String docLink : sgDocLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            Assert.assertTrue("Error retrieving state",
                    response.getStatusCode() == 200);
            SecurityGroupService.SecurityGroupState doc = response
                    .getBody(SecurityGroupService.SecurityGroupState.class);
//            Assert.assertNotNull(doc.endpointLink);
//
//            Assert.assertEquals(size, doc.endpointLinks.size());
//            for (String endpointLink : expectedEndpoints) {
//                Assert.assertTrue(doc.endpointLinks.contains(endpointLink));
//            }
        }

        List<String> imageDocLinks = getDocumentLinks(ImageService.ImageState.class);
        System.out.println("ImageState docs size: " + imageDocLinks.size());
        for (String docLink : imageDocLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            Assert.assertTrue("Error retrieving state",
                    response.getStatusCode() == 200);
            ImageService.ImageState doc = response
                    .getBody(ImageService.ImageState.class);
            Assert.assertNotNull(doc.endpointLink);

            Assert.assertEquals(size, doc.endpointLinks.size());
            for (String endpointLink : expectedEndpoints) {
                Assert.assertTrue(doc.endpointLinks.contains(endpointLink));
            }
        }


        List<String> resourceGroupLinks = getDocumentLinks(ResourceGroupService.ResourceGroupState.class);
        System.out.println("ResourceGroup docs size: " + resourceGroupLinks.size());
        for (String docLink : resourceGroupLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            Assert.assertTrue("Error retrieving state",
                    response.getStatusCode() == 200);
            ResourceGroupService.ResourceGroupState doc = response
                    .getBody(ResourceGroupService.ResourceGroupState.class);

            if (doc.customProperties != null &&
                    doc.customProperties
                            .get(CUSTOM_PROP_ENDPOINT_LINK) != null) {
                String customEndpointLink =
                        doc.customProperties
                                .get(CUSTOM_PROP_ENDPOINT_LINK);
                Assert.assertNotNull(customEndpointLink);
            }

//            Assert.assertEquals(size, doc.endpointLinks.size());
//            for (String endpointLink : expectedEndpoints) {
//                Assert.assertTrue(doc.endpointLinks.contains(endpointLink));
//            }
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

//    private void validateDocumentLinks(Class resourceState, List<String> docLinks, int size, List<String> expectedEndpoints) {
//        for (String docLink : docLinks) {
//            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), docLink))
//                    .setReferer(this.host.getUri());
//            Operation response = this.host.waitForResponse(op);
//            Assert.assertTrue("Error retrieving state",
//                    response.getStatusCode() == 200);
//            ResourceState doc = (ResourceState) response.getBody(resourceState);
//            //Assert.assertNotNull(doc.endpointLink);
//
//            resourceState.
//
//            Assert.assertEquals(size, doc.endpointLinks.size());
//            for (String endpointLink : expectedEndpoints) {
//                Assert.assertTrue(doc.endpointLinks.contains(endpointLink));
//            }
//        }
//    }

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
