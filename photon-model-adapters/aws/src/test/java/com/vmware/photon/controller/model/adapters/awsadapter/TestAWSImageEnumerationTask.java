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

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.model.Filter;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Stopwatch;
import org.junit.rules.TestName;
import org.junit.runner.Description;

import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSImageEnumerationAdapterService.PartitionedIterator;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.query.QueryUtils.QueryByPages;
import com.vmware.photon.controller.model.query.QueryUtils.QueryTemplate;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ImageService;
import com.vmware.photon.controller.model.resources.ImageService.ImageState;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService.EndpointAllocationTaskState;
import com.vmware.photon.controller.model.tasks.ImageEnumerationTaskService;
import com.vmware.photon.controller.model.tasks.ImageEnumerationTaskService.ImageEnumerationTaskState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
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

    // Unfortunately for some reason on Jenkins JOB aws ALL images enum times out.
    // On my local machine it runs in less than 20 secs.
    public boolean enableLongRunning = false;
    // }}

    private static final String AMAZON_PRIVATE_IMAGE_FILTER = null;

    // As of now uniquely identify a SINGLE AWS image.
    private static final String AMAZON_PUBLIC_IMAGE_FILTER_SINGLE;

    static {
        Filter nameFilter = new Filter("name").withValues("*" + "Amazon-Linux_WordPress" + "*");

        // Serialize the list of filters to JSON string
        AMAZON_PUBLIC_IMAGE_FILTER_SINGLE = Utils.toJson(Arrays.asList(nameFilter));
    }

    // As of now uniquely identify ~10K AWS images out of ~85K.
    private static final String AMAZON_PUBLIC_IMAGE_FILTER_ALL;

    static {
        Filter platformFilter = new Filter("platform").withValues("windows");

        // Serialize the list of filters to JSON string
        AMAZON_PUBLIC_IMAGE_FILTER_ALL = Utils.toJson(Arrays.asList(platformFilter));
    }

    // The expected number of images where platform = Windows (out of ~85K)
    private static final int AMAZON_PUBLIC_IMAGES_ALL_COUNT = 10_000;

    private static final boolean EXACT_COUNT = true;

    private static final boolean PUBLIC = true;
    private static final boolean PRIVATE = false;

    @Rule
    public TestName currentTestName = new TestName();

    @Rule
    public Stopwatch stopwatch = new Stopwatch() {

        @Override
        protected void finished(long nanos, Description description) {
            getHost().log(Level.INFO,
                    "Test %s finished: %d seconds",
                    description.getMethodName(), TimeUnit.NANOSECONDS.toSeconds(nanos));
        }
    };

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
        getHost().waitForServiceAvailable(PhotonModelAdaptersRegistryAdapters.LINKS);
        AWSAdapters.startServices(getHost());

        getHost().waitForServiceAvailable(AWSAdapters.CONFIG_LINK);
    }

    /*
     * The image that must be returned:
     * https://console.aws.amazon.com/ec2/v2/home?region=us-east-1#Images:visibility=owned-
     * by-me;imageId=ami-2929923f;sort=name
     */
    @Test
    @Ignore("https://jira-hzn.eng.vmware.com/browse/VCOM-832")
    public void testPrivateImageEnumeration_single() throws Throwable {

        Assume.assumeFalse(this.isMock);

        final EndpointState endpointState = createEndpointState();

        kickOffImageEnumeration(endpointState, PRIVATE, AMAZON_PRIVATE_IMAGE_FILTER);

        // Validate 1 image state is CREATED
        ServiceDocumentQueryResult images = queryDocumentsAndAssertExpectedCount(
                getHost(), 1,
                ImageService.FACTORY_LINK,
                EXACT_COUNT);

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

    /**
     * Validate that during enum only images from 'endpointLink' are deleted.
     */
    @Test
    @Ignore("https://jira-hzn.eng.vmware.com/browse/VCOM-832")
    public void testPrivateImageEnumeration_delete() throws Throwable {

        testImageEnumeration_delete(PRIVATE, AMAZON_PRIVATE_IMAGE_FILTER);
    }

    private void testImageEnumeration_delete(boolean isPublic, String imageFilter)
            throws Throwable {

        Assume.assumeFalse(this.isMock);

        EndpointState endpointState = createEndpointState();

        // Those images should not be touched by this image enum. {{
        //
        // Pre-create public and private image in different end-point
        EndpointState azureEndpointState = createDummyEndpointState(EndpointType.azure);
        ImageState publicImageState_diffEP = createImageState(azureEndpointState, true, PUBLIC);
        ImageState privateImageState_diffEP = createImageState(azureEndpointState, true, PRIVATE);

        // Pre-create public and private image in same end-point but different region
        ImageState publicImageState_diffRegion = createImageState(endpointState, false, PUBLIC);
        ImageState privateImageState_diffRegion = createImageState(endpointState, false, PRIVATE);
        // }}

        // Create one stale image that should be deleted by this enumeration
        ImageState staleImageState = createImageState(endpointState, true, isPublic);

        // Validate the 3 image states are preCREATED: 1 stale and 2 vSphere
        int preCreatedCount = 1 + 2 + 2;
        queryDocumentsAndAssertExpectedCount(
                getHost(), preCreatedCount, ImageService.FACTORY_LINK, EXACT_COUNT);

        // Under TESTING
        kickOffImageEnumeration(endpointState, isPublic, imageFilter);

        // Validate 1 image state is CREATED and the 2 vSphere are UNtouched
        int postEnumCount = 1 + 2 + 2;
        postEnumCount++; //since we are not deleting stale resource anymore, just disassociating
        // them
        ServiceDocumentQueryResult imagesAfterEnum = queryDocumentsAndAssertExpectedCount(
                getHost(),
                postEnumCount,
                ImageService.FACTORY_LINK,
                EXACT_COUNT);

        // Validate 1 stale image state is DISASSOCIATED
        ImageState staleImage = Utils.fromJson(
                imagesAfterEnum.documents.get(staleImageState.documentSelfLink),
                ImageState.class);
        Assert.assertTrue("Dummy image should have been disassociated.",
                staleImage.endpointLinks.isEmpty());

        // Validate vSphere images are untouched
        Assert.assertTrue("Private images from other endpoints should not have been deleted.",
                imagesAfterEnum.documentLinks
                        .contains(privateImageState_diffEP.documentSelfLink));
        Assert.assertTrue("Public images from other endpoints should not have been deleted.",
                imagesAfterEnum.documentLinks
                        .contains(publicImageState_diffEP.documentSelfLink));

        Assert.assertTrue(
                "Private images from same endpoints but different region should not have been deleted.",
                imagesAfterEnum.documentLinks
                        .contains(privateImageState_diffRegion.documentSelfLink));
        Assert.assertTrue("Public images from other endpoints should not have been deleted.",
                imagesAfterEnum.documentLinks
                        .contains(publicImageState_diffRegion.documentSelfLink));
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
                    AMAZON_PUBLIC_IMAGE_FILTER_SINGLE);

            kickOffImageEnumeration(endpointState, PUBLIC, AMAZON_PUBLIC_IMAGE_FILTER_SINGLE);

            if (!this.isMock) {
                // Validate 1 image state is CREATED
                imagesAfterFirstEnum = queryDocumentsAndAssertExpectedCount(getHost(), 1,
                        ImageService.FACTORY_LINK,
                        EXACT_COUNT);

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

                Assert.assertNotNull("Disk configurations should not be null",
                        imageAfterFirstEnum.diskConfigs);

                Assert.assertTrue("There should be at least one disk configuration for boot disk",
                        imageAfterFirstEnum.diskConfigs.size() > 0);
            }
        }

        {
            getHost().log(Level.INFO,
                    "=== Second enumeration should update the single '%s' image",
                    AMAZON_PUBLIC_IMAGE_FILTER_SINGLE);

            if (!this.isMock) {
                // Update local image state
                updateImageState(imagesAfterFirstEnum.documentLinks.get(0));
            }

            kickOffImageEnumeration(endpointState, PUBLIC, AMAZON_PUBLIC_IMAGE_FILTER_SINGLE);

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

                Assert.assertNotNull("Disk configurations should not be null",
                        imageAfterSecondEnum.diskConfigs);

                Assert.assertTrue("There should be at least one disk configuration for boot disk",
                        imageAfterSecondEnum.diskConfigs.size() > 0);
            }
        }
    }

    /**
     * Validate that during enum only images of this 'endpointType' are deleted.
     */
    @Test
    public void testPublicImageEnumeration_delete() throws Throwable {

        testImageEnumeration_delete(PUBLIC, AMAZON_PUBLIC_IMAGE_FILTER_SINGLE);
    }

    @Test
    public void testPublicImageEnumeration_all() throws Throwable {

        Assume.assumeFalse(this.isMock);
        Assume.assumeTrue(this.enableLongRunning);

        getHost().setTimeoutSeconds((int) TimeUnit.MINUTES.toSeconds(20));

        // Important: MUST share same Endpoint between the two enum runs.
        final EndpointState endpointState = createEndpointState();

        ImageEnumerationTaskState task = kickOffImageEnumeration(
                endpointState, PUBLIC, AMAZON_PUBLIC_IMAGE_FILTER_ALL);

        // Validate at least 10K image states are created

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

        Assert.assertTrue("Expected at least " + AMAZON_PUBLIC_IMAGES_ALL_COUNT
                + " images, but found only " + imagesCount,
                imagesCount > AMAZON_PUBLIC_IMAGES_ALL_COUNT);
    }

    @Test
    public void testPartitionedIterator() {
        {
            List<String> original = Arrays.asList("1", "2", "3", "4");

            PartitionedIterator<String> pIter = new PartitionedIterator<>(original, 3);

            Assert.assertTrue(pIter.hasNext());

            Assert.assertEquals(0, pIter.pageNumber());

            Assert.assertEquals(Arrays.asList("1", "2", "3"), pIter.next());

            Assert.assertEquals(1, pIter.pageNumber());

            Assert.assertEquals(Arrays.asList("4"), pIter.next());

            Assert.assertEquals(2, pIter.pageNumber());

            Assert.assertFalse(pIter.hasNext());

            Assert.assertEquals(original.size(), pIter.totalNumber());
        }

        {
            List<String> original = Arrays.asList("1", "2", "3", "4");

            PartitionedIterator<String> pIter = new PartitionedIterator<>(original, 2);

            Assert.assertTrue(pIter.hasNext());

            Assert.assertEquals(0, pIter.pageNumber());

            Assert.assertEquals(Arrays.asList("1", "2"), pIter.next());

            Assert.assertEquals(1, pIter.pageNumber());

            Assert.assertEquals(Arrays.asList("3", "4"), pIter.next());

            Assert.assertEquals(2, pIter.pageNumber());

            Assert.assertFalse(pIter.hasNext());

            Assert.assertEquals(original.size(), pIter.totalNumber());
        }

        {
            List<String> original = Arrays.asList();

            PartitionedIterator<String> pIter = new PartitionedIterator<>(original, 3);

            Assert.assertFalse(pIter.hasNext());

            Assert.assertEquals(original.size(), pIter.totalNumber());
        }
    }

    private ImageEnumerationTaskState kickOffImageEnumeration(
            EndpointState endpointState, boolean isPublic, String filter) throws Throwable {

        ImageEnumerationTaskState taskState = new ImageEnumerationTaskState();

        if (isPublic == PUBLIC) {
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

    /**
     * @param endpoint
     *            the end-point of the image to create
     * @param epRegion
     *            a flag indicating whether to create the image in the region of the end-point or in
     *            a different region
     * @param isPublic
     *            a flag indicating whether to create public or private image
     * @return the actual image created
     */
    private ImageState createImageState(EndpointState endpoint, boolean epRegion, boolean isPublic)
            throws Throwable {

        ImageState image = new ImageState();

        if (isPublic == PUBLIC) {
            image.endpointType = endpoint.endpointType;
        } else {
            image.endpointLink = endpoint.documentSelfLink;
            image.tenantLinks = endpoint.tenantLinks;
        }

        image.endpointLinks = new HashSet<>();
        image.endpointLinks.add(endpoint.documentSelfLink);
        image.id = "dummy-" + this.currentTestName.getMethodName();

        image.regionId = epRegion
                ? endpoint.endpointProperties.get(EndpointConfigRequest.REGION_KEY)
                : endpoint.endpointProperties.get(EndpointConfigRequest.REGION_KEY) + "_diff";

        return postServiceSynchronously(
                ImageService.FACTORY_LINK,
                image,
                ImageState.class);
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

        EndpointType endpointType = EndpointType.aws;

        EndpointState endpoint;
        {
            endpoint = new EndpointState();

            endpoint.endpointType = endpointType.name();
            endpoint.id = endpointType.name() + "-id";
            endpoint.name = endpointType.name() + "-name";

            endpoint.endpointProperties = new HashMap<>();
            endpoint.endpointProperties.put(PRIVATE_KEY_KEY, this.secretKey);
            endpoint.endpointProperties.put(PRIVATE_KEYID_KEY, this.accessKey);
            endpoint.endpointProperties.put(REGION_KEY, Regions.US_EAST_1.getName());
        }

        EndpointAllocationTaskState allocateEndpoint = new EndpointAllocationTaskState();
        allocateEndpoint.endpointState = endpoint;
        allocateEndpoint.options = this.isMock ? EnumSet.of(TaskOption.IS_MOCK) : null;
        allocateEndpoint.taskInfo = new TaskState();
        allocateEndpoint.taskInfo.isDirect = true;

        allocateEndpoint.tenantLinks = Arrays.asList(endpointType.name() + "-tenant");

        allocateEndpoint = TestUtils.doPost(this.host, allocateEndpoint,
                EndpointAllocationTaskState.class,
                UriUtils.buildUri(this.host, EndpointAllocationTaskService.FACTORY_LINK));

        return allocateEndpoint.endpointState;
    }

    private EndpointState createDummyEndpointState(EndpointType endpointType) throws Throwable {

        EndpointState endpoint = new EndpointState();

        endpoint.endpointType = endpointType.name();
        endpoint.id = endpointType.name() + "-id";
        endpoint.name = endpointType.name() + "-name";

        endpoint.endpointProperties = new HashMap<>();
        endpoint.endpointProperties.put(REGION_KEY, endpointType.name() + "-region");

        return TestUtils.doPost(host, endpoint, EndpointState.class,
                UriUtils.buildUri(host, EndpointService.FACTORY_LINK));
    }

}
