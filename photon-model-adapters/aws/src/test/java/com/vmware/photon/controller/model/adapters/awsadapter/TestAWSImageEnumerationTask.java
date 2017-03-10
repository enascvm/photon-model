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

package com.vmware.photon.controller.model.adapters.awsadapter;

import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEYID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEY_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.REGION_KEY;
import static com.vmware.photon.controller.model.tasks.ProvisioningUtils.queryDocumentsAndAssertExpectedCount;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.amazonaws.regions.Regions;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSImageEnumerationAdapterService.PartitionedIterator;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ImageService;
import com.vmware.photon.controller.model.resources.ImageService.ImageState;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService.EndpointAllocationTaskState;
import com.vmware.photon.controller.model.tasks.ImageEnumerationTaskService;
import com.vmware.photon.controller.model.tasks.ImageEnumerationTaskService.ImageEnumerationTaskState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.QueryUtils;
import com.vmware.photon.controller.model.tasks.QueryUtils.QueryByPages;
import com.vmware.photon.controller.model.tasks.QueryUtils.QueryTemplate;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;

public class TestAWSImageEnumerationTask extends BaseModelTest {

    // Populated from command line props {{
    public String accessKey = "accessKey";
    public String secretKey = "secretKey";
    public boolean isMock = true;

    public boolean enableLongRunning = false;
    // }}

    private static final String AMAZON_PRIVATE_IMAGE_FILTER = null;
    // As of now uniquely identify a SINGLE AWS image.
    private static final String AMAZON_PUBLIC_IMAGE_FILTER = "Amazon-Linux_WordPress";

    private static final boolean EXACT_COUNT = true;

    @Rule
    public TestName currentTestName = new TestName();

    @Before
    public final void beforeTest() throws Throwable {

        CommandLineArgumentParser.parseFromProperties(this);
    }

    @After
    public final void afterTest() throws Throwable {

        QueryByPages<ImageState> queryAll = new QueryByPages<ImageState>(
                getHost(),
                Builder.create().addKindFieldClause(ImageState.class).build(),
                ImageState.class,
                null);
        queryAll.setMaxPageSize(QueryUtils.DEFAULT_MAX_RESULT_LIMIT);

        AtomicInteger counter = new AtomicInteger(0);

        QueryTemplate.waitToComplete(queryAll.queryLinks(imageLink -> {
            try {
                deleteServiceSynchronously(imageLink);
                counter.incrementAndGet();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }));

        getHost().log(Level.INFO,
                "[" + this.currentTestName.getMethodName() + "] Deleted " + counter
                        + " ImageStates");
    }

    @Override
    protected void startRequiredServices() throws Throwable {

        // Start PhotonModelServices
        super.startRequiredServices();

        PhotonModelTaskServices.startServices(getHost());
        getHost().waitForServiceAvailable(PhotonModelTaskServices.LINKS);

        PhotonModelAdaptersRegistryAdapters.startServices(getHost());

        AWSAdapters.startServices(getHost());

        getHost().waitForServiceAvailable(AWSAdapters.CONFIG_LINK);
    }

    /*
     * The image that must be returned:
     * https://console.aws.amazon.com/ec2/v2/home?region=us-east-1#Images:visibility=owned-
     * by-me;imageId=ami-2929923f;sort=name
     */
    @Test
    public void testPrivateImageEnumeration_single() throws Throwable {

        Assume.assumeFalse(this.isMock);

        final EndpointState endpointState = createEndpointState();

        ImageState staleImageState = createImageState(endpointState, false);

        kickOffImageEnumeration(endpointState, false, AMAZON_PRIVATE_IMAGE_FILTER);

        // Validate 1 image state is CREATED
        ServiceDocumentQueryResult images = queryDocumentsAndAssertExpectedCount(
                getHost(), 1,
                ImageService.FACTORY_LINK,
                EXACT_COUNT);

        // Validate 1 stale image state is DELETED
        Assert.assertTrue("Dummy image should have been deleted.",
                !images.documentLinks
                        .contains(staleImageState.documentSelfLink));

        ImageState image = Utils.fromJson(
                images.documents.values().iterator().next(),
                ImageState.class);

        // Validate created image is correctly populated
        Assert.assertNull("Private image must NOT have endpointType set.",
                image.endpointType);
        Assert.assertEquals("Private image must have endpointLink set.",
                endpointState.documentSelfLink, image.endpointLink);
        Assert.assertEquals("Private image must have tenantLinks set.",
                endpointState.tenantLinks, image.tenantLinks);

        Assert.assertEquals("Private image id is incorrect.",
                "ami-2929923f", image.id);
        Assert.assertEquals("Private image name is incorrect.",
                "alexs-test", image.name);

        Assert.assertEquals("Private image 'Name' tag is missing.", 1, image.tagLinks.size());

        TagState nameTag = getServiceSynchronously(
                image.tagLinks.iterator().next(),
                TagState.class);

        Assert.assertEquals("photon-model-test-doNOTdelete", nameTag.value);
    }

    @Test
    public void testPublicImageEnumeration_single() throws Throwable {

        // Important: MUST share same Endpoint between the two enum runs.
        final EndpointState endpointState = createEndpointState();

        ServiceDocumentQueryResult imagesAfterFirstEnum = null;
        ImageState imageAfterFirstEnum = null;

        ServiceDocumentQueryResult imagesAfterSecondEnum = null;

        {
            getHost().log(Level.INFO,
                    "=== First enumeration should create a single '%s' image",
                    AMAZON_PUBLIC_IMAGE_FILTER);

            ImageState staleImageState = createImageState(endpointState, true);

            kickOffImageEnumeration(endpointState, true, AMAZON_PUBLIC_IMAGE_FILTER);

            if (!this.isMock) {
                // Validate 1 image state is CREATED
                imagesAfterFirstEnum = queryDocumentsAndAssertExpectedCount(getHost(), 1,
                        ImageService.FACTORY_LINK,
                        EXACT_COUNT);

                // Validate 1 stale image state is DELETED
                Assert.assertTrue("Dummy image should have been deleted.",
                        !imagesAfterFirstEnum.documentLinks
                                .contains(staleImageState.documentSelfLink));

                imageAfterFirstEnum = Utils.fromJson(
                        imagesAfterFirstEnum.documents.values().iterator().next(),
                        ImageState.class);

                // Validate created image is correctly populated
                Assert.assertNotNull("Public image must have endpointType set.",
                        imageAfterFirstEnum.endpointType);
                Assert.assertNull("Public image must NOT have endpointLink set.",
                        imageAfterFirstEnum.endpointLink);
                Assert.assertNull("Public image must NOT have tenantLinks set.",
                        imageAfterFirstEnum.tenantLinks);
            }
        }

        {
            getHost().log(Level.INFO,
                    "=== Second enumeration should update the single '%s' image",
                    AMAZON_PUBLIC_IMAGE_FILTER);

            if (!this.isMock) {
                // Update local image state
                updateImageState(imagesAfterFirstEnum.documentLinks.get(0));
            }

            kickOffImageEnumeration(endpointState, true, AMAZON_PUBLIC_IMAGE_FILTER);

            if (!this.isMock) {
                // Validate 1 image state is UPDATED (and the local update above is overridden)
                imagesAfterSecondEnum = queryDocumentsAndAssertExpectedCount(getHost(), 1,
                        ImageService.FACTORY_LINK,
                        EXACT_COUNT);

                Assert.assertEquals("Images should be the same after the two enums",
                        imagesAfterFirstEnum.documentLinks,
                        imagesAfterSecondEnum.documentLinks);

                ImageState imageAfterSecondEnum = Utils.fromJson(
                        imagesAfterSecondEnum.documents.values().iterator().next(),
                        ImageState.class);

                Assert.assertNotEquals("Images timestamp should differ after the two enums",
                        imageAfterFirstEnum.documentUpdateTimeMicros,
                        imageAfterSecondEnum.documentUpdateTimeMicros);

                Assert.assertTrue("Image name is not updated correctly after second enum.",
                        !imageAfterSecondEnum.name.contains("OVERRIDE"));
            }
        }
    }

    @Test
    public void testPublicImageEnumeration_all() throws Throwable {

        Assume.assumeFalse(this.isMock);
        Assume.assumeTrue(this.enableLongRunning);

        getHost().setTimeoutSeconds((int) TimeUnit.MINUTES.toSeconds(5));

        // Important: MUST share same Endpoint between the two enum runs.
        final EndpointState endpointState = createEndpointState();

        ImageEnumerationTaskState task = kickOffImageEnumeration(endpointState, true, null);

        // Validate at least 50K image states are created

        // NOTE: do not use queryDocumentsAndAssertExpectedCount
        // since it fails with 'Query returned large number of results'

        QueryByPages<ImageState> queryAll = new QueryByPages<ImageState>(
                getHost(),
                Builder.create().addKindFieldClause(ImageState.class).build(),
                ImageState.class,
                task.tenantLinks);
        queryAll.setMaxPageSize(QueryUtils.DEFAULT_MAX_RESULT_LIMIT);

        Long imagesCount = QueryByPages.waitToComplete(
                queryAll.collectLinks(Collectors.counting()));

        Assert.assertTrue("Expected at least " + 58_000 + " images, but found only " + imagesCount,
                imagesCount > 58_000);
    }

    @Test
    public void testPartitionedIterator() {
        {
            List<String> original = Arrays.asList("1", "2", "3", "4");

            PartitionedIterator<String> pIter = new PartitionedIterator<>(original, 3);

            Assert.assertTrue(pIter.hasNext());

            Assert.assertEquals(Arrays.asList("1", "2", "3"), pIter.next());

            Assert.assertEquals(Arrays.asList("4"), pIter.next());

            Assert.assertFalse(pIter.hasNext());
        }

        {
            List<String> original = Arrays.asList("1", "2", "3", "4");

            PartitionedIterator<String> pIter = new PartitionedIterator<>(original, 2);

            Assert.assertTrue(pIter.hasNext());

            Assert.assertEquals(Arrays.asList("1", "2"), pIter.next());

            Assert.assertEquals(Arrays.asList("3", "4"), pIter.next());

            Assert.assertFalse(pIter.hasNext());
        }

        {
            List<String> original = Arrays.asList();

            PartitionedIterator<String> pIter = new PartitionedIterator<>(original, 3);

            Assert.assertFalse(pIter.hasNext());
        }
    }

    private ImageEnumerationTaskState kickOffImageEnumeration(
            EndpointState endpointState, boolean isPublic, String filter) throws Throwable {

        ImageEnumerationTaskState taskState = new ImageEnumerationTaskState();

        if (isPublic) {
            taskState.endpointType = endpointState.endpointType;
        } else {
            taskState.endpointLink = endpointState.documentSelfLink;
            taskState.tenantLinks = endpointState.tenantLinks;
        }

        taskState.filter = filter;
        taskState.options = this.isMock
                ? EnumSet.of(TaskOption.IS_MOCK)
                : EnumSet.noneOf(TaskOption.class);

        // Start/Post image-enumeration task
        taskState = postServiceSynchronously(
                ImageEnumerationTaskService.FACTORY_LINK,
                taskState,
                ImageEnumerationTaskState.class);

        // Wait for image-enumeration task to complete
        return getHost().waitForFinishedTask(
                ImageEnumerationTaskState.class,
                taskState.documentSelfLink);
    }

    private ImageState createImageState(EndpointState endpoint, boolean isPublic) throws Throwable {

        ImageState image = new ImageState();

        if (isPublic) {
            image.endpointType = endpoint.endpointType;
        } else {
            image.endpointLink = endpoint.documentSelfLink;
            image.tenantLinks = endpoint.tenantLinks;
        }

        image.id = "dummy-" + this.currentTestName.getMethodName();

        image = postServiceSynchronously(
                ImageService.FACTORY_LINK,
                image,
                ImageState.class);

        return image;
    }

    private ImageState updateImageState(String imageToUpdateSelfLink) throws Throwable {

        ImageState toUpdate = new ImageState();
        toUpdate.name = "OVERRIDE";

        toUpdate = patchServiceSynchronously(
                imageToUpdateSelfLink,
                toUpdate,
                ImageState.class);

        return toUpdate;
    }

    private EndpointState createEndpointState() throws Throwable {

        final EndpointState endpoint;
        {
            endpoint = new EndpointState();

            endpoint.endpointType = EndpointType.aws.name();
            endpoint.id = EndpointType.aws.name() + "-id";
            endpoint.name = EndpointType.aws.name() + "-name";

            endpoint.tenantLinks = new ArrayList<>();
            endpoint.tenantLinks.add(EndpointType.aws.name() + "-tenant");

            endpoint.endpointProperties = new HashMap<>();
            endpoint.endpointProperties.put(PRIVATE_KEY_KEY, this.secretKey);
            endpoint.endpointProperties.put(PRIVATE_KEYID_KEY, this.accessKey);
            // IMPORTANT: Private image enum does exist/work only in US_EAST_1!
            endpoint.endpointProperties.put(REGION_KEY, Regions.US_EAST_1.getName());
        }

        EndpointAllocationTaskState allocateEndpoint = new EndpointAllocationTaskState();
        allocateEndpoint.endpointState = endpoint;
        allocateEndpoint.options = this.isMock ? EnumSet.of(TaskOption.IS_MOCK) : null;
        allocateEndpoint.taskInfo = new TaskState();
        allocateEndpoint.taskInfo.isDirect = true;

        allocateEndpoint = TestUtils.doPost(this.host, allocateEndpoint,
                EndpointAllocationTaskState.class,
                UriUtils.buildUri(this.host, EndpointAllocationTaskService.FACTORY_LINK));

        return allocateEndpoint.endpointState;
    }

}
