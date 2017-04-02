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

import static java.util.Collections.singletonList;

import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.REGION_KEY;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import com.microsoft.azure.CloudException;
import com.microsoft.azure.management.compute.VirtualMachineImagesOperations;
import com.microsoft.azure.management.compute.models.PurchasePlan;
import com.microsoft.azure.management.compute.models.VirtualMachineImage;
import com.microsoft.azure.management.compute.models.VirtualMachineImageResource;

import org.apache.commons.lang3.StringUtils;

import com.vmware.photon.controller.model.adapterapi.ImageEnumerateRequest;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureSdkClients;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.adapters.util.enums.EndpointEnumerationProcess;
import com.vmware.photon.controller.model.resources.ImageService;
import com.vmware.photon.controller.model.resources.ImageService.ImageState;
import com.vmware.photon.controller.model.tasks.ImageEnumerationTaskService.ImageEnumerationTaskState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;

/**
 * Azure image enumeration adapter responsible to enumerate Azure {@link ImageState}s. It handles
 * {@link ImageEnumerateRequest} as send/initiated by {@code ImageEnumerationTaskService}.
 */
public class AzureImageEnumerationAdapterService extends StatelessService {

    public static final String SELF_LINK = AzureUriPaths.AZURE_IMAGE_ENUMERATION_ADAPTER;

    /**
     * {@link EndpointEnumerationProcess} specialization that loads Azure
     * {@link VirtualMachineImage}s into {@link ImageState} store.
     */
    private static class AzureImageEnumerationContext
            extends
            EndpointEnumerationProcess<AzureImageEnumerationContext, ImageState, VirtualMachineImage> {

        /**
         * The underlying image-enum request.
         */
        final ImageEnumerateRequest request;

        /**
         * The image-enum task that triggered this request.
         */
        ImageEnumerationTaskState imageEnumTaskState;

        /**
         * The canonized image filter (of imageEnumTaskState.filter) in the form of
         * 'publisher:offer:sku:version'. Empty VirtualMachineImageResource.name means 'no filter'.
         */
        VirtualMachineImageResource[] imageFilter;

        AzureSdkClients azureClient;

        String regionId;

        ImageTraversingIterator azureImages;

        TaskManager taskManager;

        AzureImageEnumerationContext(
                AzureImageEnumerationAdapterService service,
                ImageEnumerateRequest request) {

            super(service, request.resourceReference, ImageState.class, ImageService.FACTORY_LINK);
            this.taskManager = new TaskManager(this.service, request.taskReference,
                    request.resourceLink());
            this.request = request;
        }

        /**
         * <ul>
         * <li>Extract calling image-enum task state prior end-point loading.</li>
         * <li>Extract end-point region id once end-point state is loaded.</li>
         * </ul>
         */
        @Override
        protected DeferredResult<AzureImageEnumerationContext> getEndpointState(
                AzureImageEnumerationContext context) {

            return DeferredResult.completed(context)
                    .thenCompose(this::getImageEnumTaskState)
                    .thenCompose(ctx -> super.getEndpointState(ctx))
                    .thenApply(this::getEndpointRegion);
        }

        /**
         * Extract {@link ImageEnumerationTaskState} from {@code request.taskReference} and set it
         * to {@link #imageEnumTaskState}.
         */
        private DeferredResult<AzureImageEnumerationContext> getImageEnumTaskState(
                AzureImageEnumerationContext context) {

            Operation op = Operation.createGet(context.request.taskReference);

            return context.service
                    .sendWithDeferredResult(op, ImageEnumerationTaskState.class)
                    .thenApply(state -> {
                        context.imageEnumTaskState = state;
                        context.imageFilter = createImageFilter(context.imageEnumTaskState.filter);

                        return context;
                    });
        }

        private AzureImageEnumerationContext getEndpointRegion(
                AzureImageEnumerationContext context) {

            context.regionId = context.endpointState.endpointProperties.getOrDefault(
                    REGION_KEY, "westus");

            return context;
        }

        /**
         * Create Azure client prior core page-by-page enumeration.
         */
        @Override
        protected DeferredResult<AzureImageEnumerationContext> enumeratePageByPage(
                AzureImageEnumerationContext context) {

            return DeferredResult.completed(context)
                    .thenApply(this::createAzureClient)
                    .thenCompose(ctx -> super.enumeratePageByPage(ctx));
        }

        protected AzureImageEnumerationContext createAzureClient(
                AzureImageEnumerationContext context) {

            context.azureClient = new AzureSdkClients(
                    ((AzureImageEnumerationAdapterService) context.service).executorService,
                    context.endpointAuthState);

            return context;
        }

        @Override
        protected DeferredResult<RemoteResourcesPage> getExternalResources(String nextPageLink) {
            String msg = "Enumerating Azure images by [" +
                    Arrays.asList(this.imageFilter).stream()
                            .map(VirtualMachineImageResource::getName)
                            .collect(Collectors.joining(":"))
                    + "]";

            if (this.azureImages == null) {
                this.service.logInfo(() -> msg + ": STARTING");

                this.azureImages = new ImageTraversingIterator(
                        this, ImageTraversingIterator.DEFAULT_PAGE_SIZE);
            }

            RemoteResourcesPage page = new RemoteResourcesPage();

            if (this.azureImages.hasNext()) {
                for (VirtualMachineImage image : this.azureImages.next()) {
                    page.resourcesPage.put(image.getId(), image);
                }
            }

            // Return a non-null nextPageLink to the parent so we are called back.
            if (this.azureImages.hasNext()) {
                page.nextPageLink = "azureImages_" + (this.azureImages.pageNumber() + 1);
            } else {
                this.service
                        .logFine(() -> msg + ": TOTAL number " + this.azureImages.totalNumber());
            }

            return DeferredResult.completed(page);
        }

        @Override
        protected DeferredResult<LocalStateHolder> buildLocalResourceState(
                VirtualMachineImage remoteImage, ImageState existingImageState) {

            LocalStateHolder holder = new LocalStateHolder();

            holder.localState = new ImageState();

            if (existingImageState == null) {
                // Create flow
                holder.localState.regionId = this.regionId;
            } else {
                // Update flow: do nothing
            }

            // Both flows - populate from remote Image

            final PurchasePlan plan = remoteImage.getPlan();

            // publisher:offer:sku:version
            holder.localState.name = plan.getPublisher()
                    + ":" + plan.getProduct()
                    + ":" + plan.getName()
                    + ":" + remoteImage.getName();
            holder.localState.description = holder.localState.name;
            if (remoteImage.getOsDiskImage() != null) {
                holder.localState.osFamily = remoteImage.getOsDiskImage().getOperatingSystem();
            }

            if (remoteImage.getTags() != null) {
                holder.remoteTags.putAll(remoteImage.getTags());
            }

            return DeferredResult.completed(holder);
        }

        @Override
        protected void customizeLocalStatesQuery(Builder qBuilder) {
            // No need to customize image query. Default impl is enough.
        }

        static VirtualMachineImageResource[] createImageFilter(String filter) {

            // publisher:offer:sku:version
            String[] strFilters = { "", "", "", "" };

            if (filter != null && !filter.isEmpty()) {
                String[] tokens = StringUtils.splitPreserveAllTokens(filter, ":");
                if (tokens.length == 4) {
                    strFilters = tokens;
                }
            }

            VirtualMachineImageResource[] posv = new VirtualMachineImageResource[4];

            for (int i = 0; i < strFilters.length; i++) {
                // Create dummy VirtualMachineImageResource with just name being set.
                posv[i] = new VirtualMachineImageResource();
                posv[i].setName(strFilters[i]);
            }

            return posv;
        }
    }

    private ExecutorService executorService;

    public AzureImageEnumerationAdapterService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handleStart(Operation startPost) {
        this.executorService = getHost().allocateExecutor(this);

        super.handleStart(startPost);
    }

    @Override
    public void handleStop(Operation delete) {
        this.executorService.shutdown();
        AdapterUtils.awaitTermination(this.executorService);

        super.handleStop(delete);
    }

    @Override
    public void handlePatch(Operation op) {

        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }

        // Immediately complete the Operation from calling task.
        op.complete();

        AzureImageEnumerationContext ctx = new AzureImageEnumerationContext(
                this, op.getBody(ImageEnumerateRequest.class));

        if (ctx.request.isMockRequest) {
            // Complete the task with FINISHED
            completeWithSuccess(ctx);
            return;
        }

        // Start enumeration process...
        ctx.enumerate()
                .whenComplete((o, e) -> {
                    // Once done patch the calling task with correct stage.
                    if (e == null) {
                        completeWithSuccess(ctx);
                    } else {
                        completeWithFailure(ctx, e);
                    }
                });
    }

    private void completeWithFailure(AzureImageEnumerationContext ctx, Throwable exc) {
        ctx.taskManager.patchTaskToFailure(exc);

        if (ctx.azureClient != null) {
            ctx.azureClient.close();
        }
    }

    private void completeWithSuccess(AzureImageEnumerationContext ctx) {
        // Report the success back to the caller
        ctx.taskManager.finishTask();

        if (ctx.azureClient != null) {
            ctx.azureClient.close();
        }
    }

    private static class ImageTraversingIterator implements Iterator<List<VirtualMachineImage>> {

        static final int DEFAULT_PAGE_SIZE = 100;

        final AzureImageEnumerationContext ctx;
        final int pageSize;

        final VirtualMachineImagesOperations imagesOp;

        private Iterator<VirtualMachineImageResource> publishersIt;
        private VirtualMachineImageResource currentPublisher;

        // IMPORTANT: Do not use Collections.emptyIterator! We need brand new instance.
        private Iterator<VirtualMachineImageResource> offersIt = new ArrayList<VirtualMachineImageResource>()
                .iterator();
        private VirtualMachineImageResource currentOffer;

        private Iterator<VirtualMachineImageResource> skusIt = new ArrayList<VirtualMachineImageResource>()
                .iterator();
        private VirtualMachineImageResource currentSku;

        private Iterator<VirtualMachineImageResource> versionsIt = new ArrayList<VirtualMachineImageResource>()
                .iterator();
        private VirtualMachineImageResource currentVersion;

        private int pageNumber = -1;
        private int totalNumber = 0;

        ImageTraversingIterator(AzureImageEnumerationContext ctx, int pageSize) {

            this.ctx = ctx;
            this.pageSize = pageSize;

            this.imagesOp = ctx.azureClient
                    .getComputeManagementClient()
                    .getVirtualMachineImagesOperations();
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

        @Override
        public boolean hasNext() {
            try {
                loadPublishers();
            } catch (Exception e) {
                throw new RuntimeException("An error while traversing Azure images.", e);
            }

            return hasNext(this.publishersIt);
        }

        /**
         * Checks whether passed iterator or any of its underlying iterators has more elements.
         */
        private boolean hasNext(Iterator<VirtualMachineImageResource> it) {
            if (it == this.publishersIt) {
                return it.hasNext() || hasNext(this.offersIt);
            }
            if (it == this.offersIt) {
                return it.hasNext() || hasNext(this.skusIt);
            }
            if (it == this.skusIt) {
                return it.hasNext() || hasNext(this.versionsIt);
            }
            if (it == this.versionsIt) {
                return it.hasNext();
            }
            throw new IllegalStateException("unexpected code flow");
        }

        @Override
        public List<VirtualMachineImage> next() {

            if (!hasNext()) {
                throw new NoSuchElementException(
                        getClass().getSimpleName() + " has already been consumed.");
            }

            List<VirtualMachineImage> page = new ArrayList<>();

            try {
                while (hasNext(this.publishersIt)) {

                    // Check whether underlying iterator is exhausted
                    if (hasNext(this.offersIt)) {
                        // Continue from last stop/exit point: use current 'publisher'
                    } else {
                        // Move to next 'publisher'
                        this.currentPublisher = this.publishersIt.next();

                        loadOffers();
                    }

                    while (hasNext(this.offersIt)) {

                        // Check whether underlying iterator is exhausted
                        if (hasNext(this.skusIt)) {
                            // Continue from last stop/exit point: use current 'offer'
                        } else {
                            // Move to next 'offer'
                            this.currentOffer = this.offersIt.next();

                            loadSkus();
                        }

                        while (hasNext(this.skusIt)) {

                            // Check whether underlying iterator is exhausted
                            if (hasNext(this.versionsIt)) {
                                // Continue from last stop/exit point: use current 'sku'
                            } else {
                                // Move to next 'sku'
                                this.currentSku = this.skusIt.next();

                                loadVersions();
                            }

                            while (hasNext(this.versionsIt)) {

                                this.currentVersion = this.versionsIt.next();

                                VirtualMachineImage image = loadImage();

                                page.add(image);

                                if (page.size() == this.pageSize) {
                                    this.pageNumber++;
                                    this.totalNumber += page.size();
                                    return page;
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("An error while traversing Azure images.", e);
            }

            this.pageNumber++;
            this.totalNumber += page.size();
            return page;
        }

        /**
         * Load {@code #publishersIt}.
         */
        private void loadPublishers() throws CloudException, IllegalArgumentException, IOException {

            if (this.publishersIt != null) {
                // already loaded
                return;
            }

            VirtualMachineImageResource publisherFilter = this.ctx.imageFilter[0];

            if (publisherFilter.getName().isEmpty()) {
                // Get ALL publishers
                List<VirtualMachineImageResource> publishers = this.imagesOp
                        .listPublishers(this.ctx.regionId)
                        .getBody();

                this.publishersIt = publishers.iterator();

                this.ctx.service.logFine(() -> "publishers = " + publishers.size());
            } else {
                // Filter publishers
                this.publishersIt = singletonList(publisherFilter).iterator();

                this.ctx.service
                        .logFine(() -> "publisher-filter = " + publisherFilter.getName());
            }
        }

        /**
         * Load {@code #offersIt}.
         */
        private void loadOffers() throws CloudException, IOException {

            VirtualMachineImageResource offerFilter = this.ctx.imageFilter[1];

            if (offerFilter.getName().isEmpty()) {
                // Get ALL offers
                List<VirtualMachineImageResource> offers = this.imagesOp.listOffers(
                        this.ctx.regionId,
                        this.currentPublisher.getName()).getBody();

                this.offersIt = offers.iterator();

                this.ctx.service.logFine(
                        () -> "offers (per " + this.currentPublisher.getName() + ") = "
                                + offers.size());
            } else {
                // Filter offers
                this.offersIt = singletonList(offerFilter).iterator();

                this.ctx.service.logFine(
                        () -> "offer-filter (per " + this.currentPublisher.getName()
                                + ") = " + offerFilter.getName());
            }
        }

        /**
         * Load {@code #skusIt}.
         */
        private void loadSkus() throws CloudException, IOException {

            VirtualMachineImageResource skuFilter = this.ctx.imageFilter[2];

            if (skuFilter.getName().isEmpty()) {
                // Get ALL skus
                List<VirtualMachineImageResource> skus = this.imagesOp.listSkus(
                        this.ctx.regionId,
                        this.currentPublisher.getName(),
                        this.currentOffer.getName()).getBody();

                this.skusIt = skus.iterator();

                this.ctx.service.logFine(
                        () -> "skus (per " + this.currentPublisher.getName() + "/"
                                + this.currentOffer.getName()
                                + ") = " + skus.size());
            } else {
                // Filter sku
                this.skusIt = singletonList(skuFilter).iterator();

                this.ctx.service.logFine(
                        () -> "sku-filter (per " + this.currentPublisher.getName()
                                + "/" + this.currentOffer.getName() + ") = "
                                + skuFilter.getName());
            }
        }

        private void loadVersions() throws CloudException, IOException {

            VirtualMachineImageResource versionFilter = this.ctx.imageFilter[3];

            if (versionFilter.getName().isEmpty()) {
                // Get ALL versions
                List<VirtualMachineImageResource> versions = this.imagesOp.list(
                        this.ctx.regionId,
                        this.currentPublisher.getName(),
                        this.currentOffer.getName(),
                        this.currentSku.getName(),
                        null, null, null).getBody();

                this.versionsIt = versions.iterator();

                this.ctx.service.logFine(() -> "versions (per "
                        + this.currentPublisher.getName() + "/"
                        + this.currentOffer.getName() + "/"
                        + this.currentSku.getName()
                        + ") = " + versions.size());
            } else {
                // Filter versions
                this.versionsIt = singletonList(versionFilter).iterator();

                this.ctx.service.logFine(
                        () -> "version-filter (per "
                                + this.currentPublisher.getName() + "/"
                                + this.currentOffer.getName() + "/"
                                + this.currentSku.getName()
                                + ") = " + versionFilter.getName());
            }
        }

        private VirtualMachineImage loadImage() throws CloudException, IOException {

            VirtualMachineImage image = this.imagesOp.get(
                    this.ctx.regionId,
                    this.currentPublisher.getName(),
                    this.currentOffer.getName(),
                    this.currentSku.getName(),
                    this.currentVersion.getName()).getBody();

            if (image.getPlan() == null) {
                // For some reason some images does not have Plan
                // so we create one
                PurchasePlan plan = new PurchasePlan();
                plan.setPublisher(this.currentPublisher.getName());
                plan.setProduct(this.currentOffer.getName());
                plan.setName(this.currentSku.getName());

                image.setPlan(plan);
            }

            return image;
        }

    }

}
