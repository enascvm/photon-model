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
import com.microsoft.azure.management.compute.VirtualMachineImagesOperations;
import com.microsoft.azure.management.compute.models.OSDiskImage;
import com.microsoft.azure.management.compute.models.PurchasePlan;
import com.microsoft.azure.management.compute.models.VirtualMachineImage;
import com.microsoft.azure.management.compute.models.VirtualMachineImageResource;

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

    public static final String DEFAUL_IMAGES_ENABLED_PROPERTY = "photon-model.adapter.azure.images.default.enabled";

    public static String getDefaultImagesSource() {
        return System.getProperty(DEFAUL_IMAGES_SOURCE_PROPERTY, DEFAUL_IMAGES_SOURCE_VALUE);
    }

    public static Boolean getDefaultImagesEnabled() {
        String enabled = System.getProperty(DEFAUL_IMAGES_ENABLED_PROPERTY,
                Boolean.TRUE.toString());

        return Boolean.valueOf(enabled);
    }

    /**
     * {@link EndpointEnumerationProcess} specialization that loads Azure
     * {@link VirtualMachineImage}s into {@link ImageState} store.
     */
    private static class AzureImageEnumerationContext extends
            EndpointEnumerationProcess<AzureImageEnumerationContext, ImageState, VirtualMachineImage> {

        static final String DEFAULT_FILTER_VALUE = "default";

        static final VirtualMachineImageResource[] DEFAULT_IMAGES_FILTER = toImageFilter(
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
        VirtualMachineImageResource[] imageFilter;

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

            DeferredResult<RemoteResourcesPage> defaultImagesPage = loadDefaultImagesPage();

            return defaultImagesPage;


            // TODO: For now return only "default images".
            // A flag that controls when to return the "standard" images will be introduced.
            // Jira VCOM-911? is tracking this.
        }

        private DeferredResult<RemoteResourcesPage> loadDefaultImagesPage() {

            final String msg = "Enumerating Default Azure images";

            if (getDefaultImagesEnabled() == false) {
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
                    // Load ONLY default images so NULL is returned as next page
                    ? null
                    // Otherwise continue with standard images
                    : NEXT_PAGE_LINK + 0;

            return this.azureDefaultImages.load().handle((defaultImages, exc) -> {

                if (exc != null) {
                    this.service.logWarning(
                            () -> String.format(msg + ": FAILED - %s", Utils.toString(exc)));
                } else {
                    for (VirtualMachineImage image : defaultImages) {
                        page.resourcesPage.put(toImageReference(image), image);
                    }

                    this.service.logFine(() -> msg + ": TOTAL number " + defaultImages.size());
                }

                return page;
            });
        }

        private DeferredResult<RemoteResourcesPage> loadStandardImagesPage() {

            final String msg = "Enumerating Azure images by [" +
                    Arrays.asList(this.imageFilter).stream()
                            .map(VirtualMachineImageResource::getName)
                            .collect(Collectors.joining(":"))
                    + "]";

            if (this.azureStandardImages == null) {
                this.service.logInfo(() -> msg + ": STARTING");

                this.azureStandardImages = new StandardImagesLoader(
                        this, StandardImagesLoader.DEFAULT_PAGE_SIZE);
            }

            final RemoteResourcesPage page = new RemoteResourcesPage();

            if (this.azureStandardImages.hasNext()) {
                // Consume this page from underlying Iterator
                for (VirtualMachineImage image : this.azureStandardImages.next()) {
                    page.resourcesPage.put(toImageReference(image), image);
                }
            }

            if (this.azureStandardImages.hasNext()) {
                // Return a non-null nextPageLink to the parent so we are called back.
                page.nextPageLink = NEXT_PAGE_LINK + (this.azureStandardImages.pageNumber() + 1);
            } else {
                // Return null nextPageLink to the parent so we are NOT called back any more.
                this.service.logFine(
                        () -> msg + ": TOTAL number " + this.azureStandardImages.totalNumber());
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

            if (remoteImage.getOsDiskImage() != null) {
                holder.localState.osFamily = remoteImage.getOsDiskImage().getOperatingSystem();
            }

            if (remoteImage.getTags() != null) {
                holder.remoteTags.putAll(remoteImage.getTags());
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

        static VirtualMachineImageResource[] createImageFilter(String filter) {

            if (DEFAULT_FILTER_VALUE.equalsIgnoreCase(filter)) {
                return DEFAULT_IMAGES_FILTER;
            }

            // publisher:offer:sku:version
            String[] strFilters = { "", "", "", "" };

            if (filter != null && !filter.isEmpty()) {
                String[] tokens = StringUtils.splitPreserveAllTokens(filter, ":");
                if (tokens.length == strFilters.length) {
                    strFilters = tokens;
                }
            }

            return toImageFilter(strFilters);
        }

        static VirtualMachineImageResource[] toImageFilter(String[] strFilters) {

            VirtualMachineImageResource[] posv = new VirtualMachineImageResource[strFilters.length];

            for (int i = 0; i < strFilters.length; i++) {
                // Create dummy VirtualMachineImageResource with just name being set.
                posv[i] = new VirtualMachineImageResource();
                posv[i].setName(strFilters[i]);
            }

            return posv;
        }

        static String toImageReference(VirtualMachineImage azureImage) {

            final PurchasePlan plan = azureImage.getPlan();

            return toImageReference(
                    plan.getPublisher(), plan.getProduct(), plan.getName(), azureImage.getName());
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
     *
     * An Azure <b>default</b> images loader that reads pre-defined images from a file and exposes
     * them as {@code VirtualMachineImage}s.
     *
     * <p>
     * Here's the content of the file, located at
     * {@link https://raw.githubusercontent.com/Azure/azure-rest-api-specs/master/arm-compute/quickstart-templates/aliases.json}.
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
     */
    private static class DefaultImagesLoader {

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
        public DeferredResult<List<VirtualMachineImage>> load() {

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
        private List<VirtualMachineImage> toVirtualMachineImages(List<ImageRef> imageRefs) {

            return imageRefs.stream().map(this::toVirtualMachineImage).collect(Collectors.toList());
        }

        /**
         * Convert {@code ImageRef} to {@code VirtualMachineImage}.
         */
        private VirtualMachineImage toVirtualMachineImage(ImageRef imageRef) {

            // Create artificial Azure image object
            final VirtualMachineImage image = new VirtualMachineImage();

            image.setLocation(this.ctx.regionId);

            image.setName(imageRef.version);

            {
                final PurchasePlan plan = new PurchasePlan();
                plan.setPublisher(imageRef.publisher);
                plan.setProduct(imageRef.offer);
                plan.setName(imageRef.sku);

                image.setPlan(plan);
            }

            {
                final OSDiskImage osDiskImage = new OSDiskImage();
                osDiskImage.setOperatingSystem(imageRef.osFamily);

                image.setOsDiskImage(osDiskImage);
            }

            return image;
        }
    }

    /**
     * An Azure images loader that traverses (in depth) 'publisher-offer-sku-version' hierarchy and
     * exposes {@code VirtualMachineImage}s through an {@code Iterator} interface.
     */
    private static class StandardImagesLoader implements Iterator<List<VirtualMachineImage>> {

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

        StandardImagesLoader(AzureImageEnumerationContext ctx, int pageSize) {

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
