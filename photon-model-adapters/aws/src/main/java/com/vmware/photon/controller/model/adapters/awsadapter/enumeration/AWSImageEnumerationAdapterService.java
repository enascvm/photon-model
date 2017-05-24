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

package com.vmware.photon.controller.model.adapters.awsadapter.enumeration;

import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.REGION_KEY;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.VOLUME_TYPE;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory.returnClientManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.BlockDeviceMapping;
import com.amazonaws.services.ec2.model.DescribeImagesRequest;
import com.amazonaws.services.ec2.model.DescribeImagesResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Image;
import com.amazonaws.services.ec2.model.Tag;

import com.vmware.photon.controller.model.adapterapi.ImageEnumerateRequest;
import com.vmware.photon.controller.model.adapterapi.ImageEnumerateRequest.ImageEnumerateRequestType;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUriPaths;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSDeferredResultAsyncHandler;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.adapters.util.enums.EndpointEnumerationProcess;
import com.vmware.photon.controller.model.resources.ImageService;
import com.vmware.photon.controller.model.resources.ImageService.ImageState;
import com.vmware.photon.controller.model.tasks.ImageEnumerationTaskService.ImageEnumerationTaskState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;

/**
 * AWS image enumeration adapter responsible to enumerate AWS {@link ImageState}s. It handles
 * {@link ImageEnumerateRequest} as send/initiated by {@code ImageEnumerationTaskService}.
 */
public class AWSImageEnumerationAdapterService extends StatelessService {

    public static final String SELF_LINK = AWSUriPaths.AWS_IMAGE_ENUMERATION_ADAPTER;

    /**
     * {@link EndpointEnumerationProcess} specialization that loads AWS {@link Image}s into
     * {@link ImageState} store.
     */
    private static class AWSImageEnumerationContext
            extends EndpointEnumerationProcess<AWSImageEnumerationContext, ImageState, Image> {

        /**
         * The underlying image-enum request.
         */
        final ImageEnumerateRequest request;

        /**
         * The image-enum task that triggered this request.
         */
        ImageEnumerationTaskState imageEnumTaskState;

        AmazonEC2AsyncClient awsClient;

        String regionId;

        PartitionedIterator<Image> awsImages;

        TaskManager taskManager;

        AWSImageEnumerationContext(
                AWSImageEnumerationAdapterService service,
                ImageEnumerateRequest request) {

            super(service, request.resourceReference, ImageState.class, ImageService.FACTORY_LINK);

            this.taskManager = new TaskManager(this.service,
                    request.taskReference,
                    request.resourceLink());

            this.request = request;

            if (request.requestType == ImageEnumerateRequestType.PUBLIC) {
                // Public/Shared images should NOT consider tenantLinks and endpointLink
                setApplyInfraFields(false);
            }
        }

        /**
         * <ul>
         * <li>Extract calling image-enum task state prior end-point loading.</li>
         * <li>Extract end-point region id once end-point state is loaded.</li>
         * </ul>
         */
        @Override
        protected DeferredResult<AWSImageEnumerationContext> getEndpointState(
                AWSImageEnumerationContext context) {

            return DeferredResult.completed(context)
                    .thenCompose(this::getImageEnumTaskState)
                    .thenCompose(ctx -> super.getEndpointState(ctx))
                    .thenApply(this::getEndpointRegion);
        }

        /**
         * Extract {@link ImageEnumerationTaskState} from {@code request.taskReference} and set it
         * to {@link #imageEnumTaskState}.
         */
        private DeferredResult<AWSImageEnumerationContext> getImageEnumTaskState(
                AWSImageEnumerationContext context) {

            Operation op = Operation.createGet(context.request.taskReference);

            return context.service
                    .sendWithDeferredResult(op, ImageEnumerationTaskState.class)
                    .thenApply(state -> {
                        context.imageEnumTaskState = state;
                        return context;
                    });
        }

        private AWSImageEnumerationContext getEndpointRegion(AWSImageEnumerationContext context) {

            String regionId = context.endpointState.endpointProperties.getOrDefault(
                    REGION_KEY, Regions.DEFAULT_REGION.getName());

            Regions awsRegion;
            try {
                awsRegion = Regions.fromName(regionId);
            } catch (IllegalArgumentException exc) {
                awsRegion = Regions.DEFAULT_REGION;

                context.service.logWarning("Unsupported AWS region: %s. Fallback to default: %s",
                        regionId, awsRegion.getName());
            }

            context.regionId = awsRegion.getName();

            return context;
        }

        /**
         * Create Amazon client prior core page-by-page enumeration.
         */
        @Override
        protected DeferredResult<AWSImageEnumerationContext> enumeratePageByPage(
                AWSImageEnumerationContext context) {

            return DeferredResult.completed(context)
                    .thenCompose(this::createAmazonClient)
                    .thenCompose(ctx -> super.enumeratePageByPage(ctx));
        }

        protected DeferredResult<AWSImageEnumerationContext> createAmazonClient(
                AWSImageEnumerationContext context) {
            DeferredResult<AWSImageEnumerationContext> r = new DeferredResult<>();
            context.awsClient = ((AWSImageEnumerationAdapterService) context.service).clientManager
                    .getOrCreateEC2Client(
                            context.endpointAuthState,
                            context.regionId,
                            context.service,
                            t -> r.fail(t));
            if (context.awsClient != null) {
                r.complete(context);
            }
            return r;
        }

        @Override
        protected DeferredResult<RemoteResourcesPage> getExternalResources(String nextPageLink) {

            // AWS does not support pagination of images so we internally partition all results thus
            // simulating paging
            return loadExternalResources().thenApply(imagesIterator -> {

                RemoteResourcesPage page = new RemoteResourcesPage();

                if (imagesIterator.hasNext()) {
                    for (Image image : imagesIterator.next()) {
                        page.resourcesPage.put(image.getImageId(), image);
                    }
                }

                // Return a non-null nextPageLink to the parent so we are called back.
                if (imagesIterator.hasNext()) {
                    page.nextPageLink = "awsImages_" + (imagesIterator.pageNumber() + 1);
                } else {
                    this.service.logFine(() -> "Enumerating AWS images: TOTAL number "
                            + imagesIterator.totalNumber());
                }

                return page;
            });
        }

        private DeferredResult<PartitionedIterator<Image>> loadExternalResources() {

            if (this.awsImages != null) {
                return DeferredResult.completed(this.awsImages);
            }

            boolean isPublic = this.request.requestType == ImageEnumerateRequestType.PUBLIC;

            DescribeImagesRequest request = new DescribeImagesRequest()
                    .withFilters(new Filter(AWSConstants.AWS_IMAGE_STATE_FILTER)
                            .withValues(AWSConstants.AWS_IMAGE_STATE_AVAILABLE))
                    .withFilters(new Filter(AWSConstants.AWS_IMAGE_IS_PUBLIC_FILTER)
                            .withValues(Boolean.toString(isPublic)));

            if (this.imageEnumTaskState.filter != null
                    && !this.imageEnumTaskState.filter.isEmpty()) {
                // Apply filtering to AWS images
                request.withFilters(
                        new Filter(AWSConstants.AWS_IMAGE_NAME_FILTER)
                                .withValues("*" + this.imageEnumTaskState.filter + "*"));
            }

            String msg = "Enumerating AWS images by " + request;

            // ALL AWS images are returned with a single call, NO pagination!
            AWSDeferredResultAsyncHandler<DescribeImagesRequest, DescribeImagesResult> handler = new AWSDeferredResultAsyncHandler<DescribeImagesRequest, DescribeImagesResult>(
                    this.service, msg) {

                @Override
                protected DeferredResult<DescribeImagesResult> consumeSuccess(
                        DescribeImagesRequest req, DescribeImagesResult res) {

                    return DeferredResult.completed(res);
                }
            };

            this.awsClient.describeImagesAsync(request, handler);

            return handler.toDeferredResult().thenApply(imagesResult -> {

                return this.awsImages = new PartitionedIterator<>(
                        imagesResult.getImages(),
                        PartitionedIterator.DEFAULT_PARTITION_SIZE);
            });
        }

        @Override
        protected DeferredResult<LocalStateHolder> buildLocalResourceState(
                Image remoteImage, ImageState existingImageState) {

            LocalStateHolder holder = new LocalStateHolder();

            holder.localState = new ImageState();

            if (existingImageState == null) {
                // Create flow
                holder.localState.regionId = this.regionId;

                if (this.request.requestType == ImageEnumerateRequestType.PUBLIC) {
                    holder.localState.endpointType = this.endpointState.endpointType;
                }
            } else {
                // Update flow: do nothing
            }

            // Both flows - populate from remote Image
            holder.localState.name = remoteImage.getName();
            holder.localState.description = remoteImage.getDescription();
            holder.localState.osFamily = remoteImage.getPlatform();

            holder.localState.diskConfigs = new ArrayList<>();
            for (BlockDeviceMapping blockDeviceMapping : remoteImage.getBlockDeviceMappings()) {
                ImageState.DiskConfiguration diskConfig = new ImageState.DiskConfiguration();
                diskConfig.id = blockDeviceMapping.getDeviceName();
                diskConfig.capacityMBytes = blockDeviceMapping.getEbs().getVolumeSize() * 1024;
                diskConfig.encrypted = blockDeviceMapping.getEbs().getEncrypted();
                diskConfig.persistent = true;
                diskConfig.properties = new HashMap<>();
                diskConfig.properties.put(VOLUME_TYPE, blockDeviceMapping.getEbs().getVolumeType());
                holder.localState.diskConfigs.add(diskConfig);
            }

            for (Tag remoteImageTag : remoteImage.getTags()) {
                holder.remoteTags.put(remoteImageTag.getKey(), remoteImageTag.getValue());
            }

            return DeferredResult.completed(holder);
        }

        /**
         * <ul>
         * <li>During PUBLIC image enum explicitly set {@code imageType}.</li>
         * <li>During PRIVATE image enum setting of {@code tenantLinks} and {@code endpointType} (by
         * default logic) is enough.</li>
         * </ul>
         */
        @Override
        protected void customizeLocalStatesQuery(Builder qBuilder) {
            if (this.request.requestType == ImageEnumerateRequestType.PUBLIC) {
                qBuilder.addFieldClause(
                        ImageState.FIELD_NAME_ENDPOINT_TYPE,
                        this.endpointState.endpointType);
            }
        }
    }

    private AWSClientManager clientManager;

    public AWSImageEnumerationAdapterService() {

        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    /**
     * Extend default 'start' logic with loading AWS client.
     */
    @Override
    public void handleStart(Operation op) {

        this.clientManager = AWSClientManagerFactory
                .getClientManager(AWSConstants.AwsClientType.EC2);

        super.handleStart(op);
    }

    /**
     * Extend default 'stop' logic with releasing AWS client.
     */
    @Override
    public void handleStop(Operation op) {

        returnClientManager(this.clientManager, AWSConstants.AwsClientType.EC2);

        super.handleStop(op);
    }

    @Override
    public void handlePatch(Operation op) {

        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }

        // Immediately complete the Operation from calling task.
        op.complete();

        AWSImageEnumerationContext ctx = new AWSImageEnumerationContext(
                this, op.getBody(ImageEnumerateRequest.class));

        if (ctx.request.isMockRequest) {
            // Complete the task with FINISHED
            completeWithSuccess(ctx);
            return;
        }

        logFine(() -> ctx.request.requestType + " image enumeration: STARTED");
        // Start enumeration process...
        ctx.enumerate()
                .whenComplete((o, e) -> {
                    // Once done patch the calling task with correct stage.
                    if (e == null) {
                        logFine(() -> ctx.request.requestType + " image enumeration: COMPLETED");
                        completeWithSuccess(ctx);
                    } else {
                        logSevere(() -> String.format("%s image enumeration: FAILED with %s", ctx.request.requestType, Utils.toString(e)));
                        completeWithFailure(ctx, e);
                    }
                });
    }

    private void completeWithFailure(AWSImageEnumerationContext ctx, Throwable exc) {
        ctx.taskManager.patchTaskToFailure(exc);
    }

    private void completeWithSuccess(AWSImageEnumerationContext ctx) {
        ctx.taskManager.finishTask();
    }

    /**
     * An iterator of sublists of a list, each of the same size (the final list may be smaller).
     */
    public static final class PartitionedIterator<T> implements Iterator<List<T>> {

        public static final int DEFAULT_PARTITION_SIZE = 1000;

        private final List<T> originalList;

        private final int partitionSize;

        private int lastIndex = 0;

        private int pageNumber;

        private int totalNumber;

        public PartitionedIterator(List<T> originalList, int partitionSize) {
            // we are tolerant to null values
            this.originalList = originalList == null ? Collections.emptyList() : originalList;
            this.partitionSize = partitionSize;
        }

        @Override
        public boolean hasNext() {
            return this.lastIndex < this.originalList.size();
        }

        /**
         * Returns the next partition from original list.
         */
        @Override
        public List<T> next() {
            if (!hasNext()) {
                throw new NoSuchElementException(
                        getClass().getSimpleName() + " has already been consumed.");
            }

            int beginIndex = this.lastIndex;

            this.lastIndex = Math.min(beginIndex + this.partitionSize, this.originalList.size());

            List<T> page = this.originalList.subList(beginIndex, this.lastIndex);

            this.pageNumber++;
            this.totalNumber += page.size();

            return page;
        }

        /**
         * Return the number of pages returned by {@code next} so far.
         */
        public int pageNumber() {
            return this.pageNumber;
        }

        /**
         * Return the total number of elements returned by {@code next} so far.
         */
        public int totalNumber() {
            return this.totalNumber;
        }

    }
}
