/*
 * Copyright (c) 2015-2017 VMware, Inc. All Rights Reserved.
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

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_IMAGE_VIRTUALIZATION_TYPE_FILTER;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_IMAGE_VIRTUALIZATION_TYPE_HVM;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_IMAGE_VIRTUALIZATION_TYPE_PARAVIRTUAL;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.VOLUME_TYPE;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory.returnClientManager;
import static com.vmware.photon.controller.model.resources.util.PhotonModelUtils.waitToComplete;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.BlockDeviceMapping;
import com.amazonaws.services.ec2.model.DescribeImagesRequest;
import com.amazonaws.services.ec2.model.DescribeImagesResult;
import com.amazonaws.services.ec2.model.DeviceType;
import com.amazonaws.services.ec2.model.EbsBlockDevice;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Image;
import com.amazonaws.services.ec2.model.Tag;

import com.google.gson.reflect.TypeToken;

import com.vmware.photon.controller.model.adapterapi.ImageEnumerateRequest;
import com.vmware.photon.controller.model.adapterapi.ImageEnumerateRequest.ImageEnumerateRequestType;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AwsClientType;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUriPaths;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSDeferredResultAsyncHandler;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.adapters.util.enums.EndpointEnumerationProcess;
import com.vmware.photon.controller.model.resources.ImageService;
import com.vmware.photon.controller.model.resources.ImageService.ImageState;
import com.vmware.photon.controller.model.resources.ImageService.ImageState.DiskConfiguration;
import com.vmware.photon.controller.model.resources.util.PhotonModelUtils;
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
     * @see #getImagesPageSize()
     */
    public static final String IMAGES_PAGE_SIZE_PROPERTY = "photon-model.adapter.aws.images.page.size";

    /**
     * @see #getImagesMaxConcurrentEnums()
     */
    public static final String IMAGES_MAX_CONCURRENT_ENUMS_PROPERTY = "photon-model.adapter.aws.images.max.concurrent.enums";

    /**
     * Get images page size from {@value #IMAGES_PAGE_SIZE_PROPERTY} system property. The value is
     * used to partition original AWS images list.
     *
     * @return by default return 1000
     */
    public static int getImagesPageSize() {

        final int DEFAULT = 1000;

        return Integer.getInteger(IMAGES_PAGE_SIZE_PROPERTY, DEFAULT);
    }

    /**
     * Get max number of concurrent images enumerations from
     * {@value #IMAGES_MAX_CONCURRENT_ENUMS_PROPERTY} system property.
     *
     * @return by default return 1, which implies sequential enumerations
     */
    public static int getImagesMaxConcurrentEnums() {
        final int DEFAULT = 1;

        return Math.max(DEFAULT, Integer.getInteger(IMAGES_MAX_CONCURRENT_ENUMS_PROPERTY, DEFAULT));
    }

    /**
     * {@link EndpointEnumerationProcess} specialization that loads AWS {@link Image}s into
     * {@link ImageState} store.
     */
    private static class AWSImageEnumerationContext
            extends EndpointEnumerationProcess<AWSImageEnumerationContext, ImageState, Image> {

        /**
         * As of now there are AWS regions with 90K+ images and their loading in a single call is
         * memory and time consuming. To overcome the memory leap we split images loading using
         * <i>virtualizationType</i> as partitioning criteria.
         *
         * <p>
         * This class represents an iterator on top of images partitions.
         *
         * <p>
         * The iterator is sync. Still its loading done by
         * {@link AWSImageEnumerationContext#loadAwsImages()} is async.
         */
        private static class PartitioningIterator extends PaginatingIterator<Image> {

            /**
             * The AWS images criteria (as {@link Filter}) used to partition all images during
             * enumeration. The sum of all partitions represents all AWS images and is evenly
             * distributed.
             *
             * <p>
             * As of now we are using partitioning by virtualizationType which results in two
             * buckets: <i>paravirtual</i> = 40968 images, <i>hvm</i> = 54800 images.
             */
            final Iterator<Filter> partitioningCriteria = Arrays.asList(
                    new Filter(AWS_IMAGE_VIRTUALIZATION_TYPE_FILTER)
                            .withValues(AWS_IMAGE_VIRTUALIZATION_TYPE_PARAVIRTUAL),
                    new Filter(AWS_IMAGE_VIRTUALIZATION_TYPE_FILTER)
                            .withValues(AWS_IMAGE_VIRTUALIZATION_TYPE_HVM))
                    .iterator();

            /**
             * Current partition. Internally it is being paginated.
             */
            PaginatingIterator<Image> partition = PaginatingIterator.empty();

            PartitioningIterator withPartition(PaginatingIterator<Image> partition) {
                this.partition = partition;
                return this;
            }

            /**
             * <ul>
             * <li>current partition is fully iterated</li>
             * <li>there are more partitions to iterate</li>
             * </ul>
             */
            boolean shouldLoadNextPartition() {
                return !this.partition.hasNext() && this.partitioningCriteria.hasNext();
            }

            /**
             * From client perspective either current partition has more pages or there are more
             * partitions to iterate.
             */
            @Override
            public boolean hasNext() {
                return this.partition.hasNext() || this.partitioningCriteria.hasNext();
            }

            /**
             * Just delegate to current partition.
             *
             * <p>
             * It is up to the {@link AWSImageEnumerationContext#loadAwsImages()} to load next
             * partition through {@link #withPartition(PaginatingIterator)}.
             */
            @Override
            public List<Image> next() {
                List<Image> delegatedPage = this.partition.next();

                this.pageNumber++;
                this.totalNumber += delegatedPage.size();

                return delegatedPage;
            }
        }

        /**
         * The underlying image-enum request.
         */
        final ImageEnumerateRequest request;

        /**
         * The image-enum task that triggered this request.
         */
        ImageEnumerationTaskState imageEnumTaskState;

        AmazonEC2AsyncClient awsClient;

        PartitioningIterator awsImages = new PartitioningIterator();

        TaskManager taskManager;

        AWSImageEnumerationContext(
                AWSImageEnumerationAdapterService service,
                ImageEnumerateRequest request) {

            // TODO: set "computeHostLink" value for AWS image resources.
            super(service, request.resourceReference,
                    null, ImageState.class, ImageService.FACTORY_LINK);

            this.taskManager = new TaskManager(this.service,
                    request.taskReference,
                    request.resourceLink());

            this.request = request;

            if (request.requestType == ImageEnumerateRequestType.PUBLIC) {
                // Public/Shared images should NOT consider tenantLinks and endpointLink
                setApplyInfraFields(false);
                setApplyEndpointLink(false);
            }
        }

        @Override
        public String getEndpointRegion() {
            return this.request.regionId;
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
                    .thenCompose(ctx -> super.getEndpointState(ctx));
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
            ((AWSImageEnumerationAdapterService) context.service).clientManager
                    .getOrCreateEC2ClientAsync(context.endpointAuthState,
                            context.getEndpointRegion(), context.service)
                    .whenComplete((ec2Client, t) -> {
                        if (t != null) {
                            r.fail(t);
                            return;
                        }

                        context.awsClient = ec2Client;
                        r.complete(context);
                    });
            return r;
        }

        @Override
        protected DeferredResult<RemoteResourcesPage> getExternalResources(String nextPageLink) {

            // AWS does not support pagination of images so we internally partition
            // all results thus simulating paging
            return loadAwsImages().thenApply(imagesIterator -> {

                RemoteResourcesPage page = new RemoteResourcesPage();

                if (imagesIterator.hasNext()) {
                    final List<Image> awsImagesPage = imagesIterator.next();
                    for (Image awsImage : awsImagesPage) {
                        page.resourcesPage.put(awsImage.getImageId(), awsImage);
                    }
                }

                // Return a non-null nextPageLink to the parent so we are called back.
                if (imagesIterator.hasNext()) {
                    page.nextPageLink = "awsImages_" + (imagesIterator.pageNumber() + 1);
                } else {
                    this.service.logInfo("Enumerating AWS images: TOTAL number %s",
                            imagesIterator.totalNumber());
                }

                return page;
            });
        }

        static final class ListOfFilters extends TypeToken<List<Filter>> {

            static Type asType() {
                return new ListOfFilters().getType();
            }
        }

        /**
         *
         */
        private DeferredResult<PaginatingIterator<Image>> loadAwsImages() {

            if (!this.awsImages.shouldLoadNextPartition()) {
                return DeferredResult.completed(this.awsImages);
            }

            // Otherwise load next partition of AWS images

            boolean isPublic = this.request.requestType == ImageEnumerateRequestType.PUBLIC;

            DescribeImagesRequest request = new DescribeImagesRequest()
                    .withFilters(new Filter(AWSConstants.AWS_IMAGE_STATE_FILTER)
                            .withValues(AWSConstants.AWS_IMAGE_STATE_AVAILABLE))
                    .withFilters(new Filter(AWSConstants.AWS_IMAGE_IS_PUBLIC_FILTER)
                            .withValues(Boolean.toString(isPublic)))
                    // The filter used as partitioning criteria
                    .withFilters(this.awsImages.partitioningCriteria.next());

            // Apply additional filtering to AWS images (used by tests)
            if (this.imageEnumTaskState.filter != null
                    && !this.imageEnumTaskState.filter.isEmpty()) {

                // Deserialize the JSON string to a list of AWS Filters
                List<Filter> filters = Utils.fromJson(
                        this.imageEnumTaskState.filter, ListOfFilters.asType());

                // NOTE: use withFilters(Filter...) to append NOT withFilter(List<>)
                request.withFilters(filters.toArray(new Filter[0]));
            }

            final String msg = "Enumerating AWS images by partition " + request;

            // ALL AWS images are returned with a single call, NO pagination!
            AWSDeferredResultAsyncHandler<DescribeImagesRequest, DescribeImagesResult> handler = new AWSDeferredResultAsyncHandler<>(
                    this.service, msg);

            this.awsClient.describeImagesAsync(request, handler);

            return handler.toDeferredResult().thenCompose(awsImagesResult -> {

                this.service.logInfo("%s: TOTAL number %s",
                        msg, awsImagesResult.getImages().size());

                if (awsImagesResult.getImages().isEmpty()) {
                    // Current partition is empty. Try recursively with next one.
                    return loadAwsImages();
                }

                // "artificially" paginate images once we load them all
                PaginatingIterator<Image> partition = new PaginatingIterator<>(
                        awsImagesResult.getImages(), getImagesPageSize());

                // Use loaded images as current partition
                this.awsImages.withPartition(partition);

                return DeferredResult.completed(this.awsImages);
            });
        }

        @Override
        protected DeferredResult<LocalStateHolder> buildLocalResourceState(
                Image remoteImage, ImageState existingImageState) {

            LocalStateHolder holder = new LocalStateHolder();

            holder.localState = new ImageState();

            if (existingImageState == null) {
                // Create flow
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
            if (DeviceType.Ebs == DeviceType.fromValue(remoteImage.getRootDeviceType())) {
                for (BlockDeviceMapping blockDeviceMapping : remoteImage.getBlockDeviceMappings()) {
                    // blockDeviceMapping can be with noDevice
                    EbsBlockDevice ebs = blockDeviceMapping.getEbs();
                    if (ebs != null) {
                        DiskConfiguration diskConfig = new DiskConfiguration();
                        diskConfig.id = blockDeviceMapping.getDeviceName();
                        diskConfig.encrypted = ebs.getEncrypted();
                        diskConfig.persistent = true;
                        if (ebs.getVolumeSize() != null) {
                            diskConfig.capacityMBytes = ebs.getVolumeSize() * 1024;
                        }
                        diskConfig.properties = Collections.singletonMap(
                                VOLUME_TYPE, ebs.getVolumeType());

                        holder.localState.diskConfigs.add(diskConfig);
                    }
                }
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

    private ExecutorService executorService;

    public AWSImageEnumerationAdapterService() {

        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    /**
     * Extend default 'start' logic with loading AWS client.
     */
    @Override
    public void handleStart(Operation op) {

        this.clientManager = AWSClientManagerFactory.getClientManager(AwsClientType.EC2);

        this.executorService = allocateExecutor();

        super.handleStart(op);
    }

    /**
     * Extend default 'stop' logic with releasing AWS client.
     */
    @Override
    public void handleStop(Operation op) {

        returnClientManager(this.clientManager, AwsClientType.EC2);

        this.executorService.shutdown();
        AdapterUtils.awaitTermination(this.executorService);

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

        // Encapsulate core images enum code as Supplier, so we can manipulate and pass it.
        final Supplier<DeferredResult<AWSImageEnumerationContext>> imagesEnum = () -> {

            final String msg = ctx.request.requestType + " images enum";

            logInfo(() -> msg + ": STARTED");

            // Start image enumeration process...
            DeferredResult<AWSImageEnumerationContext> imagesEnumDR = ctx.enumerate()
                    .whenComplete((o, e) -> {
                        // Once done patch the calling task with correct stage.
                        if (e == null) {
                            logInfo(() -> msg + ": COMPLETED");
                            completeWithSuccess(ctx);
                        } else {
                            logSevere(() -> msg + ": FAILED with " + Utils.toString(e));
                            completeWithFailure(ctx, e);
                        }
                    });

            return imagesEnumDR;
        };

        // Apply different execution strategies depending on enum type

        if (ctx.request.requestType == ImageEnumerateRequestType.PRIVATE) {
            // PRIVATE enums are handled immediately
            imagesEnum.get();
        } else {
            // PUBLIC enums are executed sequentially in a dedicated Thread Pool
            Runnable publicImagesEnum = () -> waitToComplete(imagesEnum.get());

            PhotonModelUtils.runInExecutor(
                    this.executorService,
                    publicImagesEnum,
                    exc -> completeWithFailure(ctx, exc));
        }
    }

    private void completeWithFailure(AWSImageEnumerationContext ctx, Throwable exc) {
        ctx.taskManager.patchTaskToFailure(exc);
    }

    private void completeWithSuccess(AWSImageEnumerationContext ctx) {
        ctx.taskManager.finishTask();
    }

    /**
     * Creates an executor specific to AWS public images enum, which by default is single threaded
     * with a queue of size 20.
     */
    private ExecutorService allocateExecutor() {

        final int corePoolSize = 1;

        // By default returns 1, so effectively we have single threaded executor;
        // otherwise the pool size varies
        final int imagesMaxConcurrentEnums = getImagesMaxConcurrentEnums();

        final long keepAliveTime = 0L;
        final TimeUnit unit = TimeUnit.MILLISECONDS;

        // Use queue with size 20, which is close to AWS regions count
        final BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(20);

        ThreadFactory tFactory = r -> new Thread(r,
                "/" + getUri() + "/" + Utils.getSystemNowMicrosUtc());

        return new ThreadPoolExecutor(
                corePoolSize, imagesMaxConcurrentEnums, keepAliveTime, unit, workQueue, tFactory);
    }

    /**
     * An iterator of pages of a list, each of the same size (the final page may be smaller).
     */
    public static class PaginatingIterator<T> implements Iterator<List<T>> {

        public static <T> PaginatingIterator<T> empty() {
            return new PaginatingIterator<>();
        }

        private final List<T> originalList;

        final int pageSize;

        private int lastIndex = 0;

        int pageNumber = 0;

        int totalNumber = 0;

        private List<T> page = null;

        /**
         * For internal use only!
         */
        private PaginatingIterator() {
            this(null, 0);
        }

        public PaginatingIterator(List<T> originalList, int pageSize) {
            // we are tolerant to null values
            this.originalList = originalList == null ? Collections.emptyList() : originalList;
            this.pageSize = pageSize;
        }

        @Override
        public boolean hasNext() {
            try {
                return this.lastIndex < this.originalList.size();
            } finally {
                // Since AWS serves all images as single List,
                // we do our best to release it as soon as possible.
                clearLastPage();
            }
        }

        /**
         * Returns the next page from original list.
         */
        @Override
        public List<T> next() {
            if (!hasNext()) {
                throw new NoSuchElementException(
                        getClass().getSimpleName() + " has already been consumed.");
            }

            // Store prev lastIndex as beginIndex
            final int beginIndex = this.lastIndex;

            // Calculate current lastIndex
            this.lastIndex = Math.min(beginIndex + this.pageSize, this.originalList.size());

            // Get a subList
            this.page = this.originalList.subList(beginIndex, this.lastIndex);

            this.pageNumber++;
            this.totalNumber += this.page.size();

            return this.page;
        }

        /**
         * Clear consumed page to let GC do its work.
         */
        private void clearLastPage() {
            if (this.page != null) {
                for (int i = 0, size = this.page.size(); i < size; i++) {
                    this.page.set(i, null);
                }
            }
            this.page = null;
        }

        /**
         * Return the number of pages returned by {@link #next()} so far.
         */
        public int pageNumber() {
            return this.pageNumber;
        }

        /**
         * Return the total number of elements returned by {@link #next()} so far.
         */
        public int totalNumber() {
            return this.totalNumber;
        }
    }
}
