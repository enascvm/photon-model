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

import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultAuthCredentials;
import static com.vmware.photon.controller.model.tasks.ProvisioningUtils.queryDocumentsAndAssertExpectedCount;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;

import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.vmware.photon.controller.model.adapters.azure.AzureAdapters;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.enumeration.AzureImageEnumerationAdapterService.ImagesLoadMode;
import com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.query.QueryUtils.QueryByPages;
import com.vmware.photon.controller.model.query.QueryUtils.QueryTemplate;
import com.vmware.photon.controller.model.query.QueryUtils.QueryTop;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ImageService;
import com.vmware.photon.controller.model.resources.ImageService.ImageState;
import com.vmware.photon.controller.model.resources.ImageService.ImageState.DiskConfiguration;
import com.vmware.photon.controller.model.tasks.ImageEnumerationTaskService;
import com.vmware.photon.controller.model.tasks.ImageEnumerationTaskService.ImageEnumerationTaskState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;

public class TestAzureImageEnumerationTask extends BaseModelTest {

    // Populated from command line props {{
    public String clientID = "clientID";
    public String clientKey = "clientKey";
    public String subscriptionId = "subscriptionId";
    public String tenantId = "tenantId";
    public boolean isMock = true;

    public boolean enableLongRunning = false;
    // }}

    // Format is 'publisher:offer:sku:version' {{

    // Uniquely identify a SINGLE Azure image.
    private static final String AZURE_SINGLE_IMAGE_FILTER = "cognosys:secured-wordpress-on-windows-2012-r2:secured-wordpress-on-windows-basic-lic:1.3.0";

    private static final String AZURE_MULTI_IMAGES_FILTER = "CoreOS:::";

    private static final String AZURE_DEFAULT_IMAGES_FILTER = "default";

    private static final String AZURE_ALL_IMAGES_FILTER = null;
    // }}

    private static final int DEFAULT_IMAGES = 11;

    private static final String PRIVATE_IMAGE_NAME = "PhModelLinuxImage";

    private static final boolean EXACT_COUNT = true;

    private static final boolean PUBLIC = true;
    private static final boolean PRIVATE = false;

    private static final String OS_TYPE_WINDOWS_NAME = "Windows";
    private static final String OS_TYPE_LINUX_NAME = "Linux";

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

        AzureAdapters.startServices(getHost());

        getHost().waitForServiceAvailable(AzureAdapters.CONFIG_LINK);
    }

    /**
     * That's the only private image:
     * {@link /resourceGroups/group1496675321811/providers/Microsoft.Compute/images/PhModelLinuxImage/overview}
     * created as described here
     * {@link https://docs.microsoft.com/en-us/azure/virtual-machines/linux/capture-image}
     */
    @Test
    @Ignore("For now run the test manually. Will enable it once the image is created programatically, but not hardcoded")
    public void testPrivateImageEnumeration_single() throws Throwable {

        Assume.assumeFalse(this.isMock);

        EndpointState endpointState = createEndpointState();

        kickOffImageEnumeration(endpointState, PRIVATE, AZURE_ALL_IMAGES_FILTER);

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

        Assert.assertTrue("Image.id is invalid", image.id.endsWith(PRIVATE_IMAGE_NAME));
        Assert.assertEquals("Image.name is invalid", PRIVATE_IMAGE_NAME, image.name);
        Assert.assertEquals("Image.description is invalid", PRIVATE_IMAGE_NAME, image.description);

        Assert.assertNotNull("Image.diskConfigs", image.diskConfigs);
        Assert.assertEquals("Image.diskConfigs.size", 1, image.diskConfigs.size());

        DiskConfiguration diskConfig = image.diskConfigs.get(0);

        Assert.assertNotNull("Image.diskConfig.properties", diskConfig.properties);
        Assert.assertNotNull("Image.diskConfig.properties.blobUri",
                diskConfig.properties.get(AzureConstants.AZURE_OSDISK_BLOB_URI));
    }

    @Test
    public void testPublicImageEnumeration_singleAndDefaults() throws Throwable {

        // Important: MUST share same Endpoint between the two enum runs.
        final EndpointState endpointState = createEndpointState();

        ServiceDocumentQueryResult imagesAfterFirstEnum = null;
        ImageState imageAfterFirstEnum = null;

        ServiceDocumentQueryResult imagesAfterSecondEnum = null;

        final Function<Collection<Object>, ImageState> imageFinder = collection -> collection
                .stream()
                .map(imageStateAsObj -> Utils.fromJson(imageStateAsObj, ImageState.class))
                .filter(imageState -> imageState.id.startsWith(AZURE_SINGLE_IMAGE_FILTER))
                .findFirst()
                .get();

        {
            getHost().log(Level.INFO,
                    "=== First enumeration should create a single '%s' image",
                    AZURE_SINGLE_IMAGE_FILTER);

            kickOffImageEnumeration(endpointState, PUBLIC, AZURE_SINGLE_IMAGE_FILTER);

            if (!this.isMock) {
                // Validate 1 image state is CREATED (in addition of 11 default)
                imagesAfterFirstEnum = queryDocumentsAndAssertExpectedCount(
                        getHost(),
                        1 + DEFAULT_IMAGES,
                        ImageService.FACTORY_LINK,
                        EXACT_COUNT);

                imageAfterFirstEnum = imageFinder.apply(imagesAfterFirstEnum.documents.values());

                // Validate created image is correctly populated
                Assert.assertNotNull("Public image must have endpointType set.",
                        imageAfterFirstEnum.endpointType);
                Assert.assertNull("Public image must NOT have endpointLink set.",
                        imageAfterFirstEnum.endpointLink);
                Assert.assertNull("Public image must NOT have tenantLinks set.",
                        imageAfterFirstEnum.tenantLinks);

                Assert.assertEquals("Image.name is different from the id",
                        imageAfterFirstEnum.id, imageAfterFirstEnum.name);
                Assert.assertEquals("Image.description is different from the id",
                        imageAfterFirstEnum.id, imageAfterFirstEnum.description);
                Assert.assertEquals("Image.region is invalid",
                        "westus", imageAfterFirstEnum.regionId);
            }
        }

        {
            getHost().log(Level.INFO,
                    "=== Second enumeration should update the single '%s' image",
                    AZURE_SINGLE_IMAGE_FILTER);

            if (!this.isMock) {
                // Update local image state
                updateImageState(imageAfterFirstEnum.documentSelfLink);
            }

            kickOffImageEnumeration(endpointState, PUBLIC, AZURE_SINGLE_IMAGE_FILTER);

            if (!this.isMock) {
                // Validate 1 image state is UPDATED (and the local update above is overridden)
                imagesAfterSecondEnum = queryDocumentsAndAssertExpectedCount(
                        getHost(),
                        1 + DEFAULT_IMAGES,
                        ImageService.FACTORY_LINK,
                        EXACT_COUNT);

                Assert.assertEquals("Images should be the same after the two enums",
                        imagesAfterFirstEnum.documents.keySet(),
                        imagesAfterSecondEnum.documents.keySet());

                ImageState imageAfterSecondEnum = imageFinder.apply(
                        imagesAfterSecondEnum.documents.values());

                Assert.assertNotEquals("Images timestamp should differ after the two enums",
                        imageAfterFirstEnum.documentUpdateTimeMicros,
                        imageAfterSecondEnum.documentUpdateTimeMicros);

                Assert.assertTrue("Image name is not updated correctly after second enum.",
                        !imageAfterSecondEnum.name.contains("OVERRIDE"));
            }
        }
    }

    @Test
    public void testPublicImageEnumeration_singleNoDefaults() throws Throwable {

        Assume.assumeFalse(this.isMock);

        setImagesLoadMode(ImagesLoadMode.STANDARD);

        try {
            kickOffImageEnumeration(createEndpointState(), PUBLIC, AZURE_SINGLE_IMAGE_FILTER);

            queryDocumentsAndAssertExpectedCount(getHost(), 1, ImageService.FACTORY_LINK,
                    EXACT_COUNT);
        } finally {
            setImagesLoadMode(ImagesLoadMode.ALL);
        }
    }

    /**
     * Validate that during enum only images of this 'endpointType' are deleted.
     */
    @Test
    public void testPublicImageEnumeration_delete() throws Throwable {

        Assume.assumeFalse(this.isMock);

        setImagesLoadMode(ImagesLoadMode.STANDARD);

        try {
            // Pre-create public and private image in different endpoint {{
            // Those images should not be touched by this image enum.
            EndpointState vSphereEndpointState = createEndpointState(EndpointType.vsphere);
            ImageState vSpherePublicImageState = createImageState(vSphereEndpointState, PUBLIC);
            ImageState vSpherePrivateImageState = createImageState(vSphereEndpointState, PRIVATE);
            // }}

            EndpointState endpointState = createEndpointState();

            // Create one stale image that should be deleted by this enumeration
            ImageState staleImageState = createImageState(endpointState, PUBLIC);

            // Validate the 3 image states are preCREATED: 1 stale and 2 vSphere
            int preCreatedCount = 1 + 2;
            queryDocumentsAndAssertExpectedCount(
                    getHost(), preCreatedCount, ImageService.FACTORY_LINK, EXACT_COUNT);

            // Under TESTING
            kickOffImageEnumeration(endpointState, PUBLIC, AZURE_SINGLE_IMAGE_FILTER);

            // Validate 1 image state is CREATED and the 2 vSphere are UNtouched
            int postEnumCount = 1 + 2;
            ServiceDocumentQueryResult imagesAfterEnum = queryDocumentsAndAssertExpectedCount(
                    getHost(),
                    postEnumCount,
                    ImageService.FACTORY_LINK,
                    EXACT_COUNT);

            // Validate 1 stale image state is DELETED
            Assert.assertFalse("Dummy image should have been deleted.",
                    imagesAfterEnum.documentLinks.contains(staleImageState.documentSelfLink));

            // Validate vSphere images are untouched
            Assert.assertTrue("Private images from other endpoints should not have been deleted.",
                    imagesAfterEnum.documentLinks
                            .contains(vSpherePrivateImageState.documentSelfLink));
            Assert.assertTrue("Public images from other endpoints should not have been deleted.",
                    imagesAfterEnum.documentLinks
                            .contains(vSpherePublicImageState.documentSelfLink));
        } finally {
            setImagesLoadMode(ImagesLoadMode.ALL);
        }
    }

    @Test
    public void testPublicImageEnumeration_defaultThroughFilter() throws Throwable {

        Assume.assumeFalse(this.isMock);

        ImageEnumerationTaskState task = kickOffImageEnumeration(
                createEndpointState(), PUBLIC, AZURE_DEFAULT_IMAGES_FILTER);

        // Validate 11 image states are created.
        assertDefaultImages(task);
    }

    @Test
    public void testPublicImageEnumeration_defaultThroughMode() throws Throwable {

        Assume.assumeFalse(this.isMock);

        setImagesLoadMode(ImagesLoadMode.DEFAULT);

        try {
            ImageEnumerationTaskState task = kickOffImageEnumeration(
                    createEndpointState(), PUBLIC, AZURE_ALL_IMAGES_FILTER);

            assertDefaultImages(task);
        } finally {
            setImagesLoadMode(ImagesLoadMode.ALL);
        }
    }

    /**
     * Validate 11 image states are created.
     */
    private void assertDefaultImages(ImageEnumerationTaskState task) {

        QueryTop<ImageState> queryAll = new QueryTop<ImageState>(
                getHost(),
                Builder.create().addKindFieldClause(ImageState.class).build(),
                ImageState.class,
                task.tenantLinks);

        Map<String, List<ImageState>> imagesByOsFamily = QueryByPages.waitToComplete(
                queryAll.collectDocuments(
                        Collectors.groupingBy(imageState -> imageState.osFamily)));

        Assert.assertEquals("The OS families of default images enumerated is incorrect",
                Sets.newHashSet(OS_TYPE_LINUX_NAME, OS_TYPE_WINDOWS_NAME),
                imagesByOsFamily.keySet());

        Assert.assertEquals("The count of default Linux images enumerated is incorrect",
                7, imagesByOsFamily.get(OS_TYPE_LINUX_NAME).size());

        for (ImageState imageState : imagesByOsFamily.get(OS_TYPE_LINUX_NAME)) {
            Assert.assertEquals(StringUtils.split(imageState.id, ":").length, 4);
            Assert.assertTrue(imageState.id.endsWith(":latest"));
            Assert.assertNotNull(imageState.regionId);
            Assert.assertNotNull(imageState.name);
            Assert.assertNotNull(imageState.description);
        }

        Assert.assertEquals("The count of default Windows images enumerated is incorrect",
                4, imagesByOsFamily.get(OS_TYPE_WINDOWS_NAME).size());

        for (ImageState imageState : imagesByOsFamily.get(OS_TYPE_WINDOWS_NAME)) {
            Assert.assertEquals(StringUtils.split(imageState.id, ":").length, 4);
            Assert.assertTrue(imageState.id.endsWith(":latest"));
            Assert.assertNotNull(imageState.regionId);
            Assert.assertNotNull(imageState.name);
            Assert.assertNotNull(imageState.description);
        }
    }

    @Test
    public void testPublicImageEnumeration_multi() throws Throwable {

        Assume.assumeFalse(this.isMock);

        // This test takes about less than 2 mins!
        getHost().setTimeoutSeconds((int) TimeUnit.MINUTES.toSeconds(2));

        ImageEnumerationTaskState task = kickOffImageEnumeration(
                createEndpointState(), PUBLIC, AZURE_MULTI_IMAGES_FILTER);

        // Validate at least 200+ image states are created.

        QueryByPages<ImageState> queryAll = new QueryByPages<ImageState>(
                getHost(),
                Builder.create().addKindFieldClause(ImageState.class).build(),
                ImageState.class,
                task.tenantLinks);
        queryAll.setMaxPageSize(QueryUtils.DEFAULT_MAX_RESULT_LIMIT);

        Long imagesCount = QueryByPages.waitToComplete(
                queryAll.collectLinks(Collectors.counting()));

        Assert.assertTrue("Expected at least " + 200 + " images, but found only " + imagesCount,
                imagesCount > 200);
    }

    @Test
    public void testPublicImageEnumeration_all() throws Throwable {

        Assume.assumeFalse(this.isMock);
        Assume.assumeTrue(this.enableLongRunning);

        // This test takes about 30 mins!
        getHost().setTimeoutSeconds((int) TimeUnit.MINUTES.toSeconds(40));

        ImageEnumerationTaskState task = kickOffImageEnumeration(createEndpointState(), PUBLIC,
                null);

        // Validate at least 4.5K image states are created

        QueryByPages<ImageState> queryAll = new QueryByPages<ImageState>(
                getHost(),
                Builder.create().addKindFieldClause(ImageState.class).build(),
                ImageState.class,
                task.tenantLinks);
        queryAll.setMaxPageSize(QueryUtils.DEFAULT_MAX_RESULT_LIMIT);

        Long imagesCount = QueryByPages.waitToComplete(
                queryAll.collectLinks(Collectors.counting()));

        Assert.assertTrue("Expected at least " + 4_500 + " images, but found only " + imagesCount,
                imagesCount > 4_500);
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
     * Create Azure endpoint.
     */
    private EndpointState createEndpointState() throws Throwable {

        return AzureTestUtil.createDefaultEndpointState(
                host, createAuthCredentialsState().documentSelfLink);
    }

    /**
     * Create arbitrary endpoint.
     */
    private EndpointState createEndpointState(EndpointType endpointType) throws Throwable {

        return AzureTestUtil.createEndpointState(
                host, createAuthCredentialsState().documentSelfLink, endpointType);
    }

    private ImageState createImageState(EndpointState endpoint, boolean isPublic) throws Throwable {

        ImageState image = new ImageState();

        if (isPublic == PUBLIC) {
            image.endpointType = endpoint.endpointType;
        } else {
            image.endpointLink = endpoint.documentSelfLink;
            image.tenantLinks = endpoint.tenantLinks;
        }

        image.id = "dummy-" + this.currentTestName.getMethodName();

        return postServiceSynchronously(
                ImageService.FACTORY_LINK,
                image,
                ImageState.class);
    }

    private ImageState updateImageState(String imageToUpdateSelfLink) throws Throwable {

        ImageState toUpdate = new ImageState();
        toUpdate.name = "OVERRIDE";

        return patchServiceSynchronously(
                imageToUpdateSelfLink,
                toUpdate,
                ImageState.class);
    }

    private AuthCredentialsServiceState createAuthCredentialsState() throws Throwable {

        return createDefaultAuthCredentials(
                this.host,
                this.clientID,
                this.clientKey,
                this.subscriptionId,
                this.tenantId);
    }

    private static void setImagesLoadMode(ImagesLoadMode mode) {
        System.setProperty(
                AzureImageEnumerationAdapterService.IMAGES_LOAD_MODE_PROPERTY, mode.name());
    }

}
