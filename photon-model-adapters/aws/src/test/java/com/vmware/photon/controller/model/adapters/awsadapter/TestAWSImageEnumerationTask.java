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

import static com.vmware.photon.controller.model.tasks.ProvisioningUtils.queryDocumentsAndAssertExpectedCount;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSImageEnumerationAdapterService.PartitionedIterator;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ImageService;
import com.vmware.photon.controller.model.resources.ImageService.ImageState;
import com.vmware.photon.controller.model.tasks.ImageEnumerationTaskService;
import com.vmware.photon.controller.model.tasks.ImageEnumerationTaskService.ImageEnumerationTaskState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.QueryUtils;
import com.vmware.photon.controller.model.tasks.QueryUtils.QueryByPages;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;

public class TestAWSImageEnumerationTask extends BaseModelTest {

    // Populated from command line props {{
    public String accessKey = "accessKey";
    public String secretKey = "secretKey";
    public boolean isMock = true;

    public boolean enableLongRunning = true;
    // }}

    // As of now uniquely identify a SINGLE AWS image.
    private static final String AMAZON_IMAGE_NAME = "Amazon-Linux_WordPress";

    @Before
    public final void beforeTest() throws Throwable {

        CommandLineArgumentParser.parseFromProperties(this);
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

    @Test
    public void testImageEnumeration() throws Throwable {

        boolean EXACT_COUNT = true;

        // Important: MUST share same Endpoint between the two enum runs.
        final EndpointState endpointState = createEndpointState();

        ServiceDocumentQueryResult imagesAfterFirstEnum = null;
        ServiceDocumentQueryResult imagesAfterSecondEnum = null;

        {
            getHost().log(Level.INFO,
                    "=== First enumeration should create a single '%s' image", AMAZON_IMAGE_NAME);

            ImageState staleImageState = createImageState(endpointState);

            kickOffImageEnumeration(endpointState, false /* all */);

            if (!this.isMock) {
                // Validate 1 image state is CREATED
                imagesAfterFirstEnum = queryDocumentsAndAssertExpectedCount(getHost(), 1,
                        ImageService.FACTORY_LINK,
                        EXACT_COUNT);

                // Validate 1 stale image state is DELETED
                Assert.assertTrue("Dummy image should have been deleted.",
                        !imagesAfterFirstEnum.documentLinks.contains(staleImageState.documentSelfLink));
            }
        }

        {
            getHost().log(Level.INFO,
                    "=== Second enumeration should update the single '%s' image",
                    AMAZON_IMAGE_NAME);

            if (!this.isMock) {
                // Update local image state
                updateImageState(imagesAfterFirstEnum.documentLinks.get(0));
            }

            kickOffImageEnumeration(endpointState, false /* all */);

            if (!this.isMock) {
                // Validate 1 image state is UPDATED (and the local update above is overridden)
                imagesAfterSecondEnum = queryDocumentsAndAssertExpectedCount(getHost(), 1,
                        ImageService.FACTORY_LINK,
                        EXACT_COUNT);

                Assert.assertEquals("Images should be the same after the two enums",
                        imagesAfterFirstEnum.documentLinks,
                        imagesAfterSecondEnum.documentLinks);

                ImageState imageAfterFirstEnum = Utils.fromJson(imagesAfterFirstEnum.documents.values().iterator().next(), ImageState.class);
                ImageState imageAfterSecondEnum = Utils.fromJson(imagesAfterSecondEnum.documents.values().iterator().next(), ImageState.class);

                Assert.assertNotEquals("Images timestamp should differ after the two enums",
                        imageAfterFirstEnum.documentUpdateTimeMicros,
                        imageAfterSecondEnum.documentUpdateTimeMicros);

                Assert.assertTrue("Image name is not updated correctly after second enum.", !imageAfterSecondEnum.name.contains("OVERRIDE"));
            }
        }
    }

    @Test
    public void testImageEnumeration_all() throws Throwable {

        Assume.assumeFalse(this.isMock);
        Assume.assumeTrue(this.enableLongRunning);

        getHost().setTimeoutSeconds((int) TimeUnit.MINUTES.toSeconds(5));

        // Important: MUST share same Endpoint between the two enum runs.
        final EndpointState endpointState = createEndpointState();

        kickOffImageEnumeration(endpointState, true /* all */);

        // Validate at least 50K image states are created

        // NOTE: do not use queryDocumentsAndAssertExpectedCount
        // since it fails with 'Query returned large number of results'

        QueryByPages<ImageState> queryAll = new QueryByPages<ImageState>(
                getHost(),
                Builder.create().addKindFieldClause(ImageState.class).build(),
                ImageState.class,
                null);
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

    private ImageEnumerationTaskState kickOffImageEnumeration(EndpointState endpointState,
            boolean all) throws Throwable {

        ImageEnumerationTaskState taskState = new ImageEnumerationTaskState();

        taskState.filter = all ? null : AMAZON_IMAGE_NAME;
        taskState.endpointLink = endpointState.documentSelfLink;
        taskState.tenantLinks = endpointState.tenantLinks;
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

    private EndpointState createEndpointState() throws Throwable {

        EndpointState endpoint = new EndpointState();

        endpoint.endpointType = EndpointType.aws.name();
        endpoint.id = EndpointType.aws.name() + "-id";
        endpoint.name = EndpointType.aws.name() + "-name";

        endpoint.authCredentialsLink = createAuthCredentialsState().documentSelfLink;

        // Passing non-existing region should fall back to default region
        endpoint.endpointProperties = Collections.singletonMap(
                EndpointConfigRequest.REGION_KEY, "non-existing");

        endpoint.tenantLinks = Collections.singletonList(EndpointType.aws.name() + "-tenant");

        return postServiceSynchronously(
                EndpointService.FACTORY_LINK,
                endpoint,
                EndpointState.class);
    }

    private ImageState createImageState(EndpointState endpoint) throws Throwable {

        ImageState image = new ImageState();

        image.id = "dummy-image-id";
        image.endpointLink = endpoint.documentSelfLink;
        image.tenantLinks = endpoint.tenantLinks;

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

    private AuthCredentialsServiceState createAuthCredentialsState() throws Throwable {

        AuthCredentialsServiceState creds = new AuthCredentialsServiceState();

        creds.privateKey = this.secretKey;
        creds.privateKeyId = this.accessKey;

        return postServiceSynchronously(
                AuthCredentialsService.FACTORY_LINK,
                creds,
                AuthCredentialsServiceState.class);
    }

}
