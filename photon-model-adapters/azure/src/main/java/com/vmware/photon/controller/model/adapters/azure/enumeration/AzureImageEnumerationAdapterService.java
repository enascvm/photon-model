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

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.microsoft.azure.CloudException;
import com.microsoft.azure.management.compute.ImageDataDisk;
import com.microsoft.azure.management.compute.ImageOSDisk;
import com.microsoft.azure.management.compute.PurchasePlan;
import com.microsoft.azure.management.compute.VirtualMachineCustomImage;
import com.microsoft.azure.management.compute.implementation.VirtualMachineImageInner;
import com.microsoft.azure.management.compute.implementation.VirtualMachineImageResourceInner;
import com.microsoft.azure.management.compute.implementation.VirtualMachineImagesInner;

import org.apache.commons.lang3.StringUtils;

import com.vmware.photon.controller.model.adapterapi.ImageEnumerateRequest;
import com.vmware.photon.controller.model.adapterapi.ImageEnumerateRequest.ImageEnumerateRequestType;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
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
        /**
         * Load standard images (in case of public and private images enumeration).
         */
        STANDARD,

        /**
         * Load default images (in case of public images enumeration).
         */
        DEFAULT,

        /**
         * Load standard and default images.
         */
        ALL;
    }

    /**
     * Get {@code ImagesLoadMode} from {@value #IMAGES_LOAD_MODE_PROPERTY} system property.
     *
     * @return by default return {@link ImagesLoadMode#ALL}
     */
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

    public static final String IMAGES_PAGE_SIZE_PROPERTY = "photon-model.adapter.azure.images.page.size";

    /**
     * Get images page size from {@value #IMAGES_PAGE_SIZE_PROPERTY} system property.
     *
     * @return by default return 100
     */
    public static int getImagesPageSize() {

        final int DEFAULT_IMAGES_PAGE_SIZE = 100;

        String imagesPageSizeStr = System.getProperty(
                IMAGES_PAGE_SIZE_PROPERTY,
                String.valueOf(DEFAULT_IMAGES_PAGE_SIZE));

        try {
            return Integer.parseInt(imagesPageSizeStr);

        } catch (NumberFormatException exc) {

            return DEFAULT_IMAGES_PAGE_SIZE;
        }
    }

    /**
     * {@link EndpointEnumerationProcess} specialization that loads Azure {@link AzureImageData}s
     * into {@link ImageState} store.
     */
    private static class AzureImageEnumerationContext extends
            EndpointEnumerationProcess<AzureImageEnumerationContext, ImageState, AzureImageData> {

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

        AzureImagesLoader azureImagesLoader;

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

        ImagesLoadMode perRequestImagesLoadMode() {
            return this.imageFilter == DEFAULT_IMAGES_FILTER
                    ? ImagesLoadMode.DEFAULT
                    : getImagesLoadMode();
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
                    .thenCompose(ctx -> super.getEndpointState(ctx));
        }

        @Override
        public String getEndpointRegion() {
            return this.request.regionId;
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

        /**
         * Get next page of Azure images from AzureImagesLoader.
         */
        @Override
        protected DeferredResult<RemoteResourcesPage> getExternalResources(String nextPageLink) {

            return getAzureImagesPage(getAzureImagesLoader());
        }

        /**
         * Initialize correct images loader depending on {@code ImageEnumerateRequestType} and
         * {@code ImagesLoadMode}.
         */
        private AzureImagesLoader getAzureImagesLoader() {

            if (this.azureImagesLoader == null) {

                // Initialize correct images loader...

                ImagesLoadMode imagesLoadMode = perRequestImagesLoadMode();

                if (this.request.requestType == ImageEnumerateRequestType.PUBLIC) {

                    // PUBLIC images enum

                    if (imagesLoadMode == ImagesLoadMode.DEFAULT) {

                        this.azureImagesLoader = new DefaultImagesLoader(this);

                    } else if (imagesLoadMode == ImagesLoadMode.STANDARD) {

                        this.azureImagesLoader = new StandardImagesLoader(
                                this, getImagesPageSize());

                    } else if (imagesLoadMode == ImagesLoadMode.ALL) {

                        this.azureImagesLoader = new ConcatenatedAzureImagesLoader(this,
                                Arrays.asList(
                                        new DefaultImagesLoader(this),
                                        new StandardImagesLoader(this, getImagesPageSize())));
                    }
                } else {
                    // PRIVATE images enumeration
                    this.azureImagesLoader = new PrivateImagesLoader(this, getImagesPageSize());
                }
            }

            return this.azureImagesLoader;
        }

        /**
         * Read Azure images page-by-page as served by passed {@link AzureImagesLoader} and convert
         * to {@link RemoteResourcesPage}.
         */
        private DeferredResult<RemoteResourcesPage> getAzureImagesPage(
                AzureImagesLoader azureImagesLoader) {

            final RemoteResourcesPage page = new RemoteResourcesPage();

            if (azureImagesLoader.hasNext()) {
                // Consume this page from underlying Iterator
                for (AzureImageData image : azureImagesLoader.next()) {
                    page.resourcesPage.put(image.id, image);
                }
            }

            if (azureImagesLoader.hasNext()) {
                // Return a non-null nextPageLink to the parent so we are called back.
                page.nextPageLink = NEXT_PAGE_LINK
                        + azureImagesLoader.getClass().getSimpleName()
                        + "_"
                        + azureImagesLoader.pageNumber;
            }

            return DeferredResult.completed(page);
        }

        @Override
        protected DeferredResult<LocalStateHolder> buildLocalResourceState(
                AzureImageData azureImageData, ImageState existingImageState) {

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

            holder.localState.name = azureImageData.name;
            holder.localState.description = azureImageData.description;
            holder.localState.osFamily = azureImageData.osFamily;
            holder.localState.diskConfigs = azureImageData.diskConfigs;

            holder.remoteTags.putAll(azureImageData.tags);

            return DeferredResult.completed(holder);
        }

        /**
         * <ul>
         * <li>During PUBLIC image enum explicitly set {@code endpointType}.</li>
         * <li>During PRIVATE image enum setting of {@code tenantLinks} and {@code endpointLink} (by
         * default logic) is enough.</li>
         * <li>During BOTH image enum explicitly set {@code regionId}.</li>
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

        final String msg = ctx.request.requestType + " images enumeration";

        logFine(() -> msg + ": STARTED");

        // Start image enumeration process...
        ctx.enumerate()
                .whenComplete((o, e) -> {
                    // Once done patch the calling task with correct stage.
                    if (e == null) {
                        logFine(() -> msg + ": COMPLETED");
                        completeWithSuccess(ctx);
                    } else {
                        logSevere(() -> msg + ": FAILED with " + Utils.toString(e));
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
     */
    private static class DefaultImagesLoader extends AzureImagesLoader {

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
                return getClass().getSimpleName()
                        + " [osFamily=" + this.osFamily
                        + ", publisher=" + this.publisher
                        + ", offer=" + this.offer
                        + ", sku=" + this.sku
                        + ", version=" + this.version + "]";
            }
        }

        DefaultImagesLoader(AzureImageEnumerationContext ctx) {
            super(ctx);
        }

        @Override
        public String toString() {
            return "Enumerating " + ctx.request.requestType + " default images";
        }

        /**
         * All default images are returned within a <b>single</b> page, so return {@code true} just
         * once, prior consuming {@link #next()}.
         */
        @Override
        public boolean hasNext() {
            return this.pageNumber == 0;
        }

        @Override
        List<AzureImageData> nextPage() {
            return ((CompletableFuture<List<AzureImageData>>) load().toCompletionStage()).join();
        }

        /**
         * Download aliases.json file, parse it and convert image entries presented to
         * {@code AzureImageData}s.
         */
        private DeferredResult<List<AzureImageData>> load() {

            URI defaultImagesSource = URI.create(getDefaultImagesSource());

            return this.ctx.service
                    .sendWithDeferredResult(Operation.createGet(defaultImagesSource), String.class)
                    .thenApply(this::parseJson)
                    .thenApply(this::toAzureImageDatas);
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
         * Convert {@code ImageRef}s to {@code AzureImageData}s.
         *
         * @see #toImageState(ImageRef)
         */
        private List<AzureImageData> toAzureImageDatas(List<ImageRef> imageRefs) {

            return imageRefs.stream().map(this::toAzureImageData).collect(Collectors.toList());
        }

        /**
         * Convert {@code ImageRef} to {@code AzureImageData}.
         */
        private AzureImageData toAzureImageData(ImageRef imageRef) {

            final AzureImageData azureImageData = new AzureImageData();

            azureImageData.id = AzureImageEnumerationContext.toImageReference(
                    imageRef.publisher, imageRef.offer, imageRef.sku, imageRef.version);

            azureImageData.name = azureImageData.id;

            azureImageData.description = azureImageData.id;

            azureImageData.osFamily = imageRef.osFamily;

            return azureImageData;
        }

    }

    /**
     * An Azure images loader that traverses (in depth) 'publisher-offer-sku-version' hierarchy and
     * exposes {@code ImageState}s through an {@code Iterator} interface.
     */
    private static class StandardImagesLoader extends AzureImagesLoader {

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

        final int pageSize;

        StandardImagesLoader(AzureImageEnumerationContext ctx, int pageSize) {

            super(ctx);

            this.pageSize = pageSize;

            this.imagesOp = ctx.azureClient
                    .getComputeManagementClientImpl()
                    .virtualMachineImages();
        }

        @Override
        public String toString() {
            return "Enumerating " + this.ctx.request.requestType + " standard images by [" +
                    Arrays.asList(this.ctx.imageFilter).stream()
                            .map(VirtualMachineImageResourceInner::name)
                            .collect(Collectors.joining(":"))
                    + "]";
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
        List<AzureImageData> nextPage() {

            List<AzureImageData> page = new ArrayList<>();

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

                                VirtualMachineImageInner azureImage = loadVirtualMachineImage();

                                AzureImageData azureImageData = toAzureImageData(
                                        azureImage);

                                page.add(azureImageData);

                                if (page.size() == this.pageSize) {
                                    return page;
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("An error while traversing Azure images.", e);
            }

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
                        .listPublishers(this.ctx.getEndpointRegion());

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
                        this.ctx.getEndpointRegion(),
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
                        this.ctx.getEndpointRegion(),
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
                        this.ctx.getEndpointRegion(),
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

        private VirtualMachineImageInner loadVirtualMachineImage()
                throws CloudException, IOException {

            VirtualMachineImageInner image = this.imagesOp.get(
                    this.ctx.getEndpointRegion(),
                    this.currentPublisher.name(),
                    this.currentOffer.name(),
                    this.currentSku.name(),
                    this.currentVersion.name());

            if (image.plan() == null) {
                // For some reason some images does not have Plan so we create one
                PurchasePlan plan = new PurchasePlan();
                plan.withPublisher(this.currentPublisher.name());
                plan.withProduct(this.currentOffer.name());
                plan.withName(this.currentSku.name());

                image.withPlan(plan);
            }

            return image;
        }

        /**
         * Convert {@code VirtualMachineImageInner} to {@code AzureImageData}.
         */
        private AzureImageData toAzureImageData(VirtualMachineImageInner azureImage) {

            final AzureImageData azureImageData = new AzureImageData();

            azureImageData.id = AzureImageEnumerationContext.toImageReference(
                    azureImage.plan().publisher(),
                    azureImage.plan().product(),
                    azureImage.plan().name(),
                    azureImage.name());

            azureImageData.name = azureImageData.id;

            azureImageData.description = azureImageData.id;

            if (azureImage.osDiskImage() != null
                    && azureImage.osDiskImage().operatingSystem() != null) {
                azureImageData.osFamily = azureImage.osDiskImage().operatingSystem().name();
            }

            if (azureImage.tags() != null) {
                azureImageData.tags.putAll(azureImage.tags());
            }

            return azureImageData;
        }

    }

    /**
     * An Azure private/custom images loader that traverses custom images under current subscription
     * and exposes {@code VirtualMachineImage}s through an {@code Iterator} interface.
     */
    private static class PrivateImagesLoader extends AzureImagesLoader {

        final int pageSize;

        // Ref to underlying Azure paged images
        final Iterator<VirtualMachineCustomImage> azurePagedImagesIt;

        PrivateImagesLoader(AzureImageEnumerationContext ctx, int pageSize) {

            super(ctx);

            this.pageSize = pageSize;

            this.azurePagedImagesIt = ctx.azureClient
                    .getComputeManager()
                    .virtualMachineCustomImages()
                    .list()
                    .iterator();
        }

        @Override
        public String toString() {
            return "Enumerating " + this.ctx.request.requestType + " images";
        }

        @Override
        public boolean hasNext() {
            return this.azurePagedImagesIt.hasNext();
        }

        @Override
        List<AzureImageData> nextPage() {

            List<AzureImageData> page = new ArrayList<>();

            for (int idx = 0; idx < this.pageSize && this.azurePagedImagesIt.hasNext(); idx++) {

                VirtualMachineCustomImage azureImage = this.azurePagedImagesIt.next();

                AzureImageData azureImageData = toAzureImageData(azureImage);

                page.add(azureImageData);
            }

            return page;
        }

        private AzureImageData toAzureImageData(VirtualMachineCustomImage azureCustomImage) {

            final AzureImageData azureImageData = new AzureImageData();

            azureImageData.id = azureCustomImage.id();

            azureImageData.name = azureCustomImage.name();

            azureImageData.description = azureImageData.name;

            // Configure Disks

            azureImageData.diskConfigs = new ArrayList<>();

            // Configure OS Disk

            final ImageOSDisk azureOsDiskImage = azureCustomImage.osDiskImage();
            if (azureOsDiskImage != null) {
                if (azureOsDiskImage.osType() != null) {
                    azureImageData.osFamily = azureOsDiskImage.osType().name();
                }

                ImageState.DiskConfiguration osDiskConfig = new ImageState.DiskConfiguration();
                osDiskConfig.properties = new HashMap<>();

                if (azureOsDiskImage.diskSizeGB() != null) {
                    osDiskConfig.capacityMBytes = azureOsDiskImage.diskSizeGB() * 1024;
                }

                if (azureOsDiskImage.caching() != null) {
                    osDiskConfig.properties.put(
                            AzureConstants.AZURE_OSDISK_CACHING,
                            azureOsDiskImage.caching().name());
                }

                if (azureOsDiskImage.blobUri() != null) {
                    osDiskConfig.properties.put(
                            AzureConstants.AZURE_OSDISK_BLOB_URI,
                            azureOsDiskImage.blobUri());
                }

                azureImageData.diskConfigs.add(osDiskConfig);
            }

            // Configure Data Disk

            for (ImageDataDisk azureDataDisk : azureCustomImage.dataDiskImages().values()) {

                ImageState.DiskConfiguration dataDiskConfig = new ImageState.DiskConfiguration();
                dataDiskConfig.properties = new HashMap<>();

                dataDiskConfig.properties.put(
                        AzureConstants.AZURE_DISK_LUN,
                        Integer.toString(azureDataDisk.lun()));

                if (azureDataDisk.diskSizeGB() != null) {
                    dataDiskConfig.capacityMBytes = azureDataDisk.diskSizeGB() * 1024;
                }

                if (azureDataDisk.caching() != null) {
                    dataDiskConfig.properties.put(
                            AzureConstants.AZURE_DISK_CACHING,
                            azureDataDisk.caching().name());
                }

                if (azureDataDisk.blobUri() != null) {
                    dataDiskConfig.properties.put(
                            AzureConstants.AZURE_DISK_BLOB_URI,
                            azureDataDisk.blobUri());
                }

                azureImageData.diskConfigs.add(dataDiskConfig);
            }

            // Configure tags

            if (azureCustomImage.tags() != null) {
                azureImageData.tags.putAll(azureCustomImage.tags());
            }

            return azureImageData;
        }

    }

    /**
     * A generic abstraction of Azure image, such as standard (VirtualMachineImageInner), default
     * (ImageRef) and private (VirtualMachineCustomImage).
     *
     * @see StandardImagesLoader#toAzureImageData(VirtualMachineImageInner)
     * @see DefaultImagesLoader#toAzureImageData(DefaultImagesLoader.ImageRef)
     * @see PrivateImagesLoader#toAzureImageData(VirtualMachineCustomImage)
     */
    // NOTE: reuse ImageState in order not to duplicate all existing props.
    private static class AzureImageData extends ImageState {

        Map<String, String> tags = new HashMap<>();
    }

    /**
     * Defines the contract of a loader capable to load Azure images, such as standard, default and
     * private.
     */
    private abstract static class AzureImagesLoader implements Iterator<List<AzureImageData>> {

        final AzureImageEnumerationContext ctx;

        /**
         * Return the number of pages returned by {@link #next()} so far.
         */
        int pageNumber = 0;

        /**
         * Return the total number of elements returned by {@link #next()} so far.
         */
        int totalNumber = 0;

        AzureImagesLoader(AzureImageEnumerationContext ctx) {
            this.ctx = ctx;
        }

        /**
         * Provides common functionality (such as 1) hasNext validation, 2) pageNumber and
         * totalNumber manipulation and 3) logging) so descendants should focus only on providing
         * {@link #nextPage() next page}.
         */
        @Override
        public final List<AzureImageData> next() {

            if (!hasNext()) {
                throw new NoSuchElementException(
                        getClass().getSimpleName() + " has already been consumed.");
            }

            if (this.pageNumber == 0) {
                this.ctx.service.logFine(() -> toString() + ": STARTING");
            }

            List<AzureImageData> page = nextPage();

            this.pageNumber++;
            this.totalNumber += page.size();

            if (!hasNext()) {
                this.ctx.service.logFine(
                        () -> toString() + ": TOTAL number = " + this.totalNumber);
            }

            return page;
        }

        /**
         * Descendants should focus on just loading next page.
         */
        abstract List<AzureImageData> nextPage();

        /**
         * Used for logging purposes, so 'enforce' descendants to provide user-friendly message.
         */
        @Override
        public abstract String toString();

    }

    /**
     * Combines multiple {@link AzureImagesLoader} into a single {@code AzureImagesLoader}. The
     * returned loader iterates across the elements of each loader and the loaders are not polled
     * until necessary.
     */
    private static final class ConcatenatedAzureImagesLoader extends AzureImagesLoader {

        private final Queue<AzureImagesLoader> delegates;

        ConcatenatedAzureImagesLoader(
                AzureImageEnumerationContext ctx,
                Collection<AzureImagesLoader> azureImagesLoaders) {

            super(ctx);

            this.delegates = new LinkedList<>(azureImagesLoaders);
        }

        @Override
        public String toString() {
            return "Enumerating " + this.ctx.request.requestType + " images with "
                    + getClass().getSimpleName();
        }

        @Override
        public boolean hasNext() {
            while (!this.delegates.isEmpty()) {
                if (this.delegates.peek().hasNext()) {
                    return true;
                }
                this.delegates.poll();
            }
            return false;
        }

        @Override
        List<AzureImageData> nextPage() {
            return this.delegates.peek().next();
        }
    }

}
