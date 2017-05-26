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
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.microsoft.azure.CloudException;
import com.microsoft.azure.management.compute.OSDiskImage;
import com.microsoft.azure.management.compute.OperatingSystemTypes;
import com.microsoft.azure.management.compute.PurchasePlan;
import com.microsoft.azure.management.compute.implementation.VirtualMachineImageInner;
import com.microsoft.azure.management.compute.implementation.VirtualMachineImageResourceInner;

import com.microsoft.azure.management.compute.implementation.VirtualMachineImagesInner;
import org.apache.commons.lang3.StringUtils;

import com.vmware.photon.controller.model.adapterapi.ImageEnumerateRequest;
import com.vmware.photon.controller.model.adapterapi.ImageEnumerateRequest.ImageEnumerateRequestType;
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
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;

/**
 * Azure image enumeration adapter responsible to enumerate Azure {@link ImageState}s. It handles
 * {@link ImageEnumerateRequest} as send/initiated by {@code ImageEnumerationTaskService}.
 */
public class AzureImageEnumerationAdapterService extends StatelessService {

    public static final String SELF_LINK = AzureUriPaths.AZURE_IMAGE_ENUMERATION_ADAPTER;

    public static final String DEFAUL_IMAGES_SOURCE_PROPERTY = "photon-model.adapter.azure.images.default.source";

    /**
     * Public JSON file of default Azure images.
     */
    public static final String DEFAUL_IMAGES_SOURCE_VALUE = "https://raw.githubusercontent.com/Azure/azure-rest-api-specs/master/arm-compute/quickstart-templates/aliases.json";

    public static String getDefaultImagesSource() {
        return System.getProperty(DEFAUL_IMAGES_SOURCE_PROPERTY, DEFAUL_IMAGES_SOURCE_VALUE);
    }

    public static final String IMAGES_LOAD_MODE_PROPERTY = "photon-model.adapter.azure.images.load.mode";

    public static enum ImagesLoadMode {
        STANDARD, DEFAULT, ALL;
    }

    public static ImagesLoadMode getImagesLoadMode() {
        String imagesLoadMode = System.getProperty(
                IMAGES_LOAD_MODE_PROPERTY,
                ImagesLoadMode.ALL.name());

        try {
            return ImagesLoadMode.valueOf(imagesLoadMode);
        } catch (Exception exc) {
            return ImagesLoadMode.ALL;
        }
    }

    /**
     * {@link EndpointEnumerationProcess} specialization that loads Azure
     * {@link com.microsoft.azure.management.compute.implementation.VirtualMachineImageInner}s into
     * {@link ImageState} store.
     */
    private static class AzureImageEnumerationContext extends
            EndpointEnumerationProcess<AzureImageEnumerationContext, ImageState, VirtualMachineImageInner> {

        static final String DEFAULT_FILTER_VALUE = "default";

        static final VirtualMachineImageResourceInner[] DEFAULT_IMAGES_FILTER = toImageFilter(
                new String[] { DEFAULT_FILTER_VALUE,
                        DEFAULT_FILTER_VALUE,
                        DEFAULT_FILTER_VALUE,
                        DEFAULT_FILTER_VALUE });

        static final String NEXT_PAGE_LINK = "azureImages_";

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
         * 'publisher:offer:sku:version'. Special cases:
         * <ul>
         * <li>empty VirtualMachineImageResource.name means 'no filter' at specific position</li>
         * <li>'default' string filter is mapped to DEFAULT_IMAGES_FILTER and means load default
         * images ONLY</li>
         * </ul>
         */
        VirtualMachineImageResourceInner[] imageFilter;

        AzureSdkClients azureClient;

        String regionId;

        DefaultImagesLoader azureDefaultImages;

        StandardImagesLoader azureStandardImages;

        TaskManager taskManager;

        AzureImageEnumerationContext(
                AzureImageEnumerationAdapterService service,
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

            // First load default images to speed up overall images enumeration.

            if (nextPageLink == null) {
                DeferredResult<RemoteResourcesPage> defaultImagesPage = loadDefaultImagesPage();

                if (defaultImagesPage != null) {
                    return defaultImagesPage;
                }
            }

            // Second load standard images which might be time consuming.

            DeferredResult<RemoteResourcesPage> standardImagesPage = loadStandardImagesPage();

            if (standardImagesPage != null) {
                return standardImagesPage;
            }

            return DeferredResult.completed(new RemoteResourcesPage());
        }

        /**
         * @return <code>null</code> to indicate that default image loading is not applicable
         *         (either disabled or already loaded)
         */
        private DeferredResult<RemoteResourcesPage> loadDefaultImagesPage() {

            final String msg = "Enumerating Default Azure images";

            final ImagesLoadMode imagesLoadMode = getImagesLoadMode();
            if (!(imagesLoadMode == ImagesLoadMode.DEFAULT
                    || imagesLoadMode == ImagesLoadMode.ALL)) {
                this.service.logFine(() -> msg + ": DISABLED");

                return null;
            }

            if (this.azureDefaultImages != null) {
                // Already loaded.
                return null;
            }

            this.azureDefaultImages = new DefaultImagesLoader(this);

            this.service.logFine(() -> msg + ": STARTING");

            final RemoteResourcesPage page = new RemoteResourcesPage();

            page.nextPageLink = this.imageFilter == DEFAULT_IMAGES_FILTER
                    // Load ONLY default images so NULL is returned as next page.
                    // This FORCE-stop image loading chain.
                    ? null
                    // Otherwise continue with standard images
                    : NEXT_PAGE_LINK + 0;

            return this.azureDefaultImages.load().handle((defaultImages, exc) -> {

                if (exc != null) {
                    this.service.logWarning(
                            () -> String.format(msg + ": FAILED with %s", Utils.toString(exc)));
                } else {
                    for (VirtualMachineImageInner image : defaultImages) {
                        page.resourcesPage.put(toImageReference(image), image);
                    }

                    this.service.logFine(() -> msg + ": TOTAL number " + defaultImages.size());
                }

                return page;
            });
        }

        /**
         * @return <code>null</code> to indicate that standard image loading is not applicable (is
         *         disabled)
         */
        private DeferredResult<RemoteResourcesPage> loadStandardImagesPage() {

            final String msg = "Enumerating Azure images by [" +
                    Arrays.asList(this.imageFilter).stream()
                            .map(VirtualMachineImageResourceInner::name)
                            .collect(Collectors.joining(":"))
                    + "]";

            final ImagesLoadMode imagesLoadMode = getImagesLoadMode();
            if (!(imagesLoadMode == ImagesLoadMode.STANDARD
                    || imagesLoadMode == ImagesLoadMode.ALL)) {
                this.service.logFine(() -> msg + ": DISABLED");

                return null;
            }

            if (this.azureStandardImages == null) {
                this.service.logInfo(() -> msg + ": STARTING");

                this.azureStandardImages = new StandardImagesLoader(
                        this, StandardImagesLoader.DEFAULT_PAGE_SIZE);
            }

            final RemoteResourcesPage page = new RemoteResourcesPage();

            if (this.azureStandardImages.hasNext()) {
                // Consume this page from underlying Iterator
                for (VirtualMachineImageInner image : this.azureStandardImages.next()) {
                    page.resourcesPage.put(toImageReference(image), image);
                }
            }

            if (this.azureStandardImages.hasNext()) {
                // Return a non-null nextPageLink to the parent so we are called back.
                page.nextPageLink = NEXT_PAGE_LINK + (this.azureStandardImages.pageNumber() + 1);
            } else {
                // Return null nextPageLink to the parent so we are NOT called back any more.
                this.service.logFine(
                        () -> msg + ": TOTAL number = " + this.azureStandardImages.totalNumber());
            }

            return DeferredResult.completed(page);
        }

        @Override
        protected DeferredResult<LocalStateHolder> buildLocalResourceState(
                VirtualMachineImageInner remoteImage, ImageState existingImageState) {

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

            // publisher:offer:sku:version
            holder.localState.name = toImageReference(remoteImage);
            holder.localState.description = toImageReference(remoteImage);

            if (remoteImage.osDiskImage() != null
                    && remoteImage.osDiskImage().operatingSystem() != null) {
                holder.localState.osFamily = remoteImage.osDiskImage().operatingSystem().name();
            }

            if (remoteImage.tags() != null) {
                holder.remoteTags.putAll(remoteImage.tags());
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

        static VirtualMachineImageResourceInner[] createImageFilter(String filter) {

            if (DEFAULT_FILTER_VALUE.equalsIgnoreCase(filter)) {
                return DEFAULT_IMAGES_FILTER;
            }

            // publisher:offer:sku:version
            String[] strFilters = { "", "", "", "" };

            if (filter != null && !filter.isEmpty()) {
                String[] tokens = StringUtils.splitPreserveAllTokens(filter, ":");
                if (tokens.length == strFilters.length) {
                    strFilters = tokens;
                } else {
                    return DEFAULT_IMAGES_FILTER;
                }
            }

            return toImageFilter(strFilters);
        }

        static VirtualMachineImageResourceInner[] toImageFilter(String[] strFilters) {

            VirtualMachineImageResourceInner[] posv = new VirtualMachineImageResourceInner[strFilters.length];

            for (int i = 0; i < strFilters.length; i++) {
                // Create dummy VirtualMachineImageResource with just name being set.
                posv[i] = new VirtualMachineImageResourceInner();
                posv[i].withName(strFilters[i]);
            }

            return posv;
        }

        static String toImageReference(VirtualMachineImageInner azureImage) {

            final PurchasePlan plan = azureImage.plan();

            return toImageReference(
                    plan.publisher(), plan.product(), plan.name(), azureImage.name());
        }

        static String toImageReference(String publisher, String offer, String sku, String version) {
            return publisher + ":" + offer + ":" + sku + ":" + version;
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

        if (ctx.request.requestType == ImageEnumerateRequestType.PRIVATE) {
            // So far PRIVATE image enumeration is not supported.
            // Complete the task with FINISHED
            logFine(() -> ctx.request.requestType + " image enumeration: SKIPPED");
            completeWithSuccess(ctx);
            return;
        }

        logFine(() -> ctx.request.requestType + " image enumeration: STARTED");
        // Start PUBLIC image enumeration process...
        ctx.enumerate()
                .whenComplete((o, e) -> {
                    // Once done patch the calling task with correct stage.
                    if (e == null) {
                        logFine(() -> ctx.request.requestType + " image enumeration: COMPLETED");
                        completeWithSuccess(ctx);
                    } else {
                        logSevere(() -> String.format("%s image enumeration: FAILED with %s",
                                ctx.request.requestType, Utils.toString(e)));
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

    /**
     * An Azure <b>default</b> images loader that reads pre-defined images from a file and exposes
     * them as {@code VirtualMachineImage}s.
     *
     * <pre>
     * {
          "$schema":"http://schema.management.azure.com/schemas/2015-01-01/deploymentTemplate.json",
          "contentVersion":"1.0.0.0",
          "parameters":{},
          "variables":{},
          "resources":[],

          "outputs":{
            "aliases":{
              "type":"object",
              "value":{

                "Linux":{
                  "CentOS":{
                    "publisher":"OpenLogic",
                    "offer":"CentOS",
                    "sku":"7.3",
                    "version":"latest"
                  },
                  "CoreOS":{
                    "publisher":"CoreOS",
                    "offer":"CoreOS",
                    "sku":"Stable",
                    "version":"latest"
                  },
                  "Debian":{
                    "publisher":"credativ",
                    "offer":"Debian",
                    "sku":"8",
                    "version":"latest"
                  },
                  "openSUSE-Leap": {
                    "publisher":"SUSE",
                    "offer":"openSUSE-Leap",
                    "sku":"42.2",
                    "version": "latest"
                  },
                  "RHEL":{
                    "publisher":"RedHat",
                    "offer":"RHEL",
                    "sku":"7.3",
                    "version":"latest"
                  },
                  "SLES":{
                    "publisher":"SUSE",
                    "offer":"SLES",
                    "sku":"12-SP2",
                    "version":"latest"
                  },
                  "UbuntuLTS":{
                    "publisher":"Canonical",
                    "offer":"UbuntuServer",
                    "sku":"16.04-LTS",
                    "version":"latest"
                  }
                },

                "Windows":{
                  "Win2016Datacenter":{
                    "publisher":"MicrosoftWindowsServer",
                    "offer":"WindowsServer",
                    "sku":"2016-Datacenter",
                    "version":"latest"
                  },
                  "Win2012R2Datacenter":{
                    "publisher":"MicrosoftWindowsServer",
                    "offer":"WindowsServer",
                    "sku":"2012-R2-Datacenter",
                    "version":"latest"
                  },
                  "Win2012Datacenter":{
                    "publisher":"MicrosoftWindowsServer",
                    "offer":"WindowsServer",
                    "sku":"2012-Datacenter",
                    "version":"latest"
                  },
                  "Win2008R2SP1":{
                    "publisher":"MicrosoftWindowsServer",
                    "offer":"WindowsServer",
                    "sku":"2008-R2-SP1",
                    "version":"latest"
                  }
                }
              }
            }
          }
        }
     * </pre>
     */    private static class DefaultImagesLoader {

        /**
         * Represents the inner most JSON node in the JSON file.
         */
        private static class ImageRef {

            String osFamily;

            String publisher;
            String offer;
            String sku;
            String version;

            @Override
            public String toString() {
                return this.getClass().getSimpleName()
                        + " [osFamily=" + this.osFamily
                        + ", publisher=" + this.publisher
                        + ", offer=" + this.offer
                        + ", sku=" + this.sku
                        + ", version=" + this.version + "]";
            }
        }

        final AzureImageEnumerationContext ctx;

        DefaultImagesLoader(AzureImageEnumerationContext ctx) {
            this.ctx = ctx;
        }

        /**
         * Download aliases.json file, parse it and convert image entries presented to
         * {@code VirtualMachineImage}s.
         *
         * <p>
         * The return type is designed to be consistent with {@code StandardImagesLoader}.
         */
        public DeferredResult<List<VirtualMachineImageInner>> load() {

            URI defaultImagesSource = URI.create(getDefaultImagesSource());

            return this.ctx.service
                    .sendWithDeferredResult(Operation.createGet(defaultImagesSource), String.class)
                    .thenApply(this::parseJson)
                    .thenApply(this::toVirtualMachineImages);
        }

        /**
         * Parse aliases.json file to {@code ImageRef}s.
         */
        private List<ImageRef> parseJson(String jsonText) {

            JsonObject rootJson = Utils.fromJson(jsonText, JsonObject.class);

            Set<Entry<String, JsonElement>> byOsFamily = rootJson
                    .getAsJsonObject("outputs")
                    .getAsJsonObject("aliases")
                    .getAsJsonObject("value")
                    .entrySet();

            List<DefaultImagesLoader.ImageRef> imageRefs = new ArrayList<>();

            for (Entry<String, JsonElement> byOsFamilyEntry : byOsFamily) {

                for (Entry<String, JsonElement> byOsEntry : byOsFamilyEntry.getValue()
                        .getAsJsonObject()
                        .entrySet()) {

                    ImageRef imageRef = Utils.fromJson(
                            byOsEntry.getValue().getAsJsonObject(), ImageRef.class);

                    imageRef.osFamily = byOsFamilyEntry.getKey();

                    imageRefs.add(imageRef);
                }
            }

            return imageRefs;
        }

        /**
         * Convert {@code ImageRef}s to {@code VirtualMachineImage}s.
         */
        private List<VirtualMachineImageInner> toVirtualMachineImages(List<ImageRef> imageRefs) {

            return imageRefs.stream().map(this::toVirtualMachineImage).collect(Collectors.toList());
        }

        /**
         * Convert {@code ImageRef} to {@code VirtualMachineImage}.
         */
        private VirtualMachineImageInner toVirtualMachineImage(ImageRef imageRef) {

            // Create artificial Azure image object
            final VirtualMachineImageInner image = new VirtualMachineImageInner();

            image.withLocation(this.ctx.regionId);

            image.withName(imageRef.version);

            {
                final PurchasePlan plan = new PurchasePlan();
                plan.withPublisher(imageRef.publisher);
                plan.withProduct(imageRef.offer);
                plan.withName(imageRef.sku);

                image.withPlan(plan);
            }

            {
                final OSDiskImage osDiskImage = new OSDiskImage();
                osDiskImage.withOperatingSystem(
                        OperatingSystemTypes.fromString(imageRef.osFamily.toUpperCase()));

                image.withOsDiskImage(osDiskImage);
            }

            return image;
        }
    }

    /**
     * An Azure images loader that traverses (in depth) 'publisher-offer-sku-version' hierarchy and
     * exposes {@code VirtualMachineImage}s through an {@code Iterator} interface.
     */
    private static class StandardImagesLoader implements Iterator<List<VirtualMachineImageInner>> {

        static final int DEFAULT_PAGE_SIZE = 100;

        final AzureImageEnumerationContext ctx;
        final int pageSize;

        final VirtualMachineImagesInner imagesOp;

        private Iterator<VirtualMachineImageResourceInner> publishersIt;
        private VirtualMachineImageResourceInner currentPublisher;

        // IMPORTANT: Do not use Collections.emptyIterator! We need brand new instance.
        private Iterator<VirtualMachineImageResourceInner> offersIt = new ArrayList<VirtualMachineImageResourceInner>()
                .iterator();
        private VirtualMachineImageResourceInner currentOffer;

        private Iterator<VirtualMachineImageResourceInner> skusIt = new ArrayList<VirtualMachineImageResourceInner>()
                .iterator();
        private VirtualMachineImageResourceInner currentSku;

        private Iterator<VirtualMachineImageResourceInner> versionsIt = new ArrayList<VirtualMachineImageResourceInner>()
                .iterator();
        private VirtualMachineImageResourceInner currentVersion;

        private int pageNumber = -1;
        private int totalNumber = 0;

        StandardImagesLoader(AzureImageEnumerationContext ctx, int pageSize) {

            this.ctx = ctx;
            this.pageSize = pageSize;

            this.imagesOp = ctx.azureClient
                    .getComputeManagementClientImpl()
                    .virtualMachineImages();
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
        private boolean hasNext(Iterator<VirtualMachineImageResourceInner> it) {
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
        public List<VirtualMachineImageInner> next() {

            if (!hasNext()) {
                throw new NoSuchElementException(
                        getClass().getSimpleName() + " has already been consumed.");
            }

            List<VirtualMachineImageInner> page = new ArrayList<>();

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

                                VirtualMachineImageInner image = loadImage();

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

            VirtualMachineImageResourceInner publisherFilter = this.ctx.imageFilter[0];

            if (publisherFilter.name().isEmpty()) {
                // Get ALL publishers
                List<VirtualMachineImageResourceInner> publishers = this.imagesOp
                        .listPublishers(this.ctx.regionId);

                this.publishersIt = publishers.iterator();

                this.ctx.service.logFine(() -> "publishers = " + publishers.size());
            } else {
                // Filter publishers
                this.publishersIt = singletonList(publisherFilter).iterator();

                this.ctx.service
                        .logFine(() -> "publisher-filter = " + publisherFilter.name());
            }
        }

        /**
         * Load {@code #offersIt}.
         */
        private void loadOffers() throws CloudException, IOException {

            VirtualMachineImageResourceInner offerFilter = this.ctx.imageFilter[1];

            if (offerFilter.name().isEmpty()) {
                // Get ALL offers
                List<VirtualMachineImageResourceInner> offers = this.imagesOp.listOffers(
                        this.ctx.regionId,
                        this.currentPublisher.name());

                this.offersIt = offers.iterator();

                this.ctx.service.logFine(
                        () -> "offers (per " + this.currentPublisher.name() + ") = "
                                + offers.size());
            } else {
                // Filter offers
                this.offersIt = singletonList(offerFilter).iterator();

                this.ctx.service.logFine(
                        () -> "offer-filter (per " + this.currentPublisher.name()
                                + ") = " + offerFilter.name());
            }
        }

        /**
         * Load {@code #skusIt}.
         */
        private void loadSkus() throws CloudException, IOException {

            VirtualMachineImageResourceInner skuFilter = this.ctx.imageFilter[2];

            if (skuFilter.name().isEmpty()) {
                // Get ALL skus
                List<VirtualMachineImageResourceInner> skus = this.imagesOp.listSkus(
                        this.ctx.regionId,
                        this.currentPublisher.name(),
                        this.currentOffer.name());

                this.skusIt = skus.iterator();

                this.ctx.service.logFine(
                        () -> "skus (per " + this.currentPublisher.name() + "/"
                                + this.currentOffer.name()
                                + ") = " + skus.size());
            } else {
                // Filter sku
                this.skusIt = singletonList(skuFilter).iterator();

                this.ctx.service.logFine(
                        () -> "sku-filter (per " + this.currentPublisher.name()
                                + "/" + this.currentOffer.name() + ") = "
                                + skuFilter.name());
            }
        }

        private void loadVersions() throws CloudException, IOException {

            VirtualMachineImageResourceInner versionFilter = this.ctx.imageFilter[3];

            if (versionFilter.name().isEmpty()) {
                // Get ALL versions
                List<VirtualMachineImageResourceInner> versions = this.imagesOp.list(
                        this.ctx.regionId,
                        this.currentPublisher.name(),
                        this.currentOffer.name(),
                        this.currentSku.name(),
                        null, null, null);

                this.versionsIt = versions.iterator();

                this.ctx.service.logFine(() -> "versions (per "
                        + this.currentPublisher.name() + "/"
                        + this.currentOffer.name() + "/"
                        + this.currentSku.name()
                        + ") = " + versions.size());
            } else {
                // Filter versions
                this.versionsIt = singletonList(versionFilter).iterator();

                this.ctx.service.logFine(
                        () -> "version-filter (per "
                                + this.currentPublisher.name() + "/"
                                + this.currentOffer.name() + "/"
                                + this.currentSku.name()
                                + ") = " + versionFilter.name());
            }
        }

        private VirtualMachineImageInner loadImage() throws CloudException, IOException {

            VirtualMachineImageInner image = this.imagesOp.get(
                    this.ctx.regionId,
                    this.currentPublisher.name(),
                    this.currentOffer.name(),
                    this.currentSku.name(),
                    this.currentVersion.name());

            if (image.plan() == null) {
                // For some reason some images does not have Plan
                // so we create one
                PurchasePlan plan = new PurchasePlan();
                plan.withPublisher(this.currentPublisher.name());
                plan.withProduct(this.currentOffer.name());
                plan.withName(this.currentSku.name());

                image.withPlan(plan);
            }

            return image;
        }

    }

}
