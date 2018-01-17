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

package com.vmware.photon.controller.model.resources;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;

import com.vmware.photon.controller.model.Constraint;
import com.vmware.photon.controller.model.ServiceUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.constants.ReleaseConstants;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.DocumentIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Describes a disk instance.
 */
public class DiskService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.RESOURCES + "/disks";

    /**
     * Status of disk.
     */
    public static enum DiskStatus {
        DETACHED, ATTACHED, AVAILABLE
    }

    /**
     * Types of disk.
     */
    public static enum DiskType {
        SSD, HDD, CDROM, FLOPPY, NETWORK
    }

    /**
     * This class represents the document state associated with a
     * {@link com.vmware.photon.controller.model.resources.DiskService} task.
     */
    public static class DiskState extends ResourceState {
        public static final String FIELD_NAME_RESOURCE_POOL_LINK = "resourcePoolLink";
        public static final String FIELD_NAME_AUTH_CREDENTIALS_LINK = "authCredentialsLink";
        public static final String FIELD_NAME_STORAGE_TYPE = "storageType";
        public static final String FIELD_NAME_STATUS = "status";

        /**
         * Identifier of the zone associated with this disk service instance.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String zoneId;

        /**
         * URI reference to corresponding DiskDescription.
         */
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_11)
        public String descriptionLink;

        /**
         * Link to the Storage description associated with the disk.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String storageDescriptionLink;

        /**
         * Identifier of the resource pool associated with this disk service
         * instance.
         */
        public String resourcePoolLink;

        /**
         * Self-link to the AuthCredentialsService used to access this disk
         * service instance.
         */
        public String authCredentialsLink;

        /**
         * URI reference to the source image used to create an instance of this
         * disk service.
         *
         * <p>Set either this or {@link #imageLink} property. If both are set {@link #imageLink} has
         * precedence.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public URI sourceImageReference;

        /**
         * Self-link to the {@link com.vmware.photon.controller.model.resources.ImageService.ImageState image}
         * used to create an instance of this disk service.
         *
         * <p>Set either this or {@link #sourceImageReference} property. If both are set this
         * property has precedence.
         */
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_16)
        public String imageLink;

        /**
         * Type of this disk service instance.
         */
        public DiskType type;

        /**
         * Cloud storage type the disk service instance represents.
         */
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_18)
        public String storageType;

        /**
         * Status of this disk service instance.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public DiskStatus status;

        /**
         * Capacity (in MB) of this disk service instance.
         */
        public long capacityMBytes;

        /**
         * Indicates whether the contents of the disk will survive power off / reboot.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_13)
        public Boolean persistent;

        /**
         * Indicates whether the contents of the disk should be encrypted or not.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_13)
        public Boolean encrypted;

        /**
         * Constraint that this disk should satisfy. For ex: Requested disk should
         * support THIN_PROVISION as HARD condition, HA as SOFT, CRITICAL as HARD etc.,
         */
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_13)
        public Constraint constraint;


        /**
         * URI reference to the adapter used to manage a disk.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_36)
        public URI diskAdapterReference;

        /**
         * If set, disks will be connected in ascending order by the
         * provisioning services.
         */
        public Integer bootOrder;

        /**
         * A list of arguments used when booting from this disk.
         */
        public String[] bootArguments;

        /**
         * The bootConfig field, if set, will trigger a PATCH request to the
         * sourceImageReference with bootConfig set as the request body. The
         * sourceImageReference in this case is expected to respond with image
         * in fat (DiskType.FLOPPY) or iso (DiskType.CDROM) format, with the
         * BootConfig.template rendered to a file on the image named by
         * bootConfig.fileName. This image can then be used for configuration by
         * a live CD, such as CoreOS' cloud-config.
         */
        public BootConfig bootConfig;

        /**
         * Reference to service that customizes this disk for a particular
         * compute. This service accepts a POST with a DiskCustomizationRequest
         * body and streams back the resulting artifact.
         * <p>
         * It is up to the caller to cache this result and make it available
         * through this service's sourceImageReference.
         */
        public URI customizationServiceReference;

        /**
         * Currency unit used for pricing.
         */
        public String currencyUnit;

        /**
         * Link to the cloud account endpoint the disk belongs to.
         * @deprecated Use {@link #endpointLinks} instead.
         */
        @Since(ReleaseConstants.RELEASE_VERSION_0_5_7)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @Deprecated
        public String endpointLink;

        /**
         * This class represents the boot configuration for the disk service
         * instance.
         */

        public static class BootConfig {
            /**
             * Label of the disk.
             */
            public String label;

            /**
             * Data on the disk.
             */
            public Map<String, String> data;

            /**
             * Files on the disk.
             */
            public FileEntry[] files;

            /**
             * This class represents a file on the disk.
             */
            public static class FileEntry {
                /**
                 * The path of the file.
                 */
                public String path;

                /**
                 * Raw contents for this file.
                 */
                public String contents;

                /**
                 * Reference to contents for this file. If non-empty, this takes
                 * precedence over the contents field.
                 */
                public URI contentsReference;
            }
        }

        @Override
        public void copyTo(ResourceState target) {
            super.copyTo(target);
            if (target instanceof DiskState) {
                DiskState targetState = (DiskState) target;
                targetState.zoneId = this.zoneId;
                targetState.regionId = this.regionId;
                targetState.descriptionLink = this.descriptionLink;
                targetState.storageDescriptionLink = this.storageDescriptionLink;
                targetState.resourcePoolLink = this.resourcePoolLink;
                targetState.authCredentialsLink = this.authCredentialsLink;
                targetState.sourceImageReference = this.sourceImageReference;
                targetState.imageLink = this.imageLink;
                targetState.type = this.type;
                targetState.status = this.status;
                targetState.capacityMBytes = this.capacityMBytes;
                targetState.persistent = this.persistent;
                targetState.encrypted = this.encrypted;
                targetState.constraint = this.constraint;
                targetState.bootOrder = this.bootOrder;
                targetState.bootArguments = this.bootArguments;
                targetState.bootConfig = this.bootConfig;
                targetState.customizationServiceReference = this.customizationServiceReference;
                targetState.currencyUnit = this.currencyUnit;
                targetState.computeHostLink = this.computeHostLink;
                targetState.endpointLink = this.endpointLink;
                targetState.endpointLinks = this.endpointLinks;
                targetState.diskAdapterReference = this.diskAdapterReference;
            }
        }
    }

    public static class DiskStateExpanded extends DiskState {
        /**
         * Storage Description instance related to this disk. It will be not null, if there is a
         * valid {@link #storageDescriptionLink}.
         */
        public StorageDescription storageDescription;
        /**
         * Set of resource group states to which this storage description belongs to.
         */
        public Set<ResourceGroupState> resourceGroupStates;

        public static URI buildUri(URI diskStateUri) {
            return UriUtils.buildExpandLinksQueryUri(diskStateUri);
        }
    }

    public DiskService() {
        super(DiskState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handleDelete(Operation delete) {
        ResourceUtils.handleDelete(delete, this);
    }

    @Override
    public void handleStart(Operation start) {
        try {
            processInput(start);
            start.complete();
        } catch (Throwable t) {
            start.fail(t);
        }
    }

    @Override
    public void handlePut(Operation put) {
        try {
            DiskState returnState = processInput(put);
            setState(put, returnState);
            put.complete();
        } catch (Throwable t) {
            put.fail(t);
        }
    }

    @Override
    public void handleGet(Operation get) {
        DiskState currentState = getState(get);
        boolean doExpand = get.getUri().getQuery() != null &&
                UriUtils.hasODataExpandParamValue(get.getUri());

        if (!doExpand) {
            get.setBody(currentState).complete();
            return;
        }

        DiskStateExpanded dsExpanded = new DiskStateExpanded();
        currentState.copyTo(dsExpanded);

        List<Operation> getOps = new ArrayList<>();
        if (currentState.storageDescriptionLink != null) {
            getOps.add(Operation.createGet(this, currentState.storageDescriptionLink)
                    .setCompletion((o, e) -> {
                        if (e == null) {
                            dsExpanded.storageDescription = o.getBody(StorageDescription.class);
                        } else {
                            logFine("Could not fetch storage description %s due to %s",
                                    currentState.storageDescriptionLink, e.getMessage());
                        }
                    }));
        }
        if (currentState.groupLinks != null) {
            dsExpanded.resourceGroupStates = new HashSet<>(currentState.groupLinks.size());
            currentState.groupLinks.stream().forEach(rgLink -> {
                getOps.add(Operation.createGet(this, rgLink));
            });
        }
        if (!getOps.isEmpty()) {
            OperationJoin.create(getOps)
                    .setCompletion((ops, exs) -> {
                        if (exs != null) {
                            get.fail(new IllegalStateException(Utils.toString(exs)));
                        } else {
                            // Now update the map with the results of gets
                            ops.values().stream().forEach((op) -> {
                                if (op.getUri().toString().contains(ResourceGroupService
                                        .FACTORY_LINK)) {
                                    dsExpanded.resourceGroupStates
                                            .add(op.getBody(ResourceGroupState.class));
                                }
                            });
                            get.setBody(dsExpanded).complete();
                        }
                    }).sendWith(this);
        } else {
            get.setBody(dsExpanded).complete();
        }
    }

    private DiskState processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        DiskState state = op.getBody(DiskState.class);
        validateState(state);
        return state;
    }

    private void validateState(DiskState state) {
        Utils.validateState(getStateDescription(), state);

        if (state.name == null) {
            throw new IllegalArgumentException("name is required.");
        }

        if (state.status == null) {
            state.status = DiskStatus.DETACHED;
        }

        if (state.bootConfig != null) {
            for (DiskState.BootConfig.FileEntry entry : state.bootConfig.files) {
                if (entry.path == null || entry.path.length() == 0) {
                    throw new IllegalArgumentException(
                            "FileEntry.path is required");
                }
            }
        }
    }

    @Override
    public void handlePatch(Operation patch) {
        // If patch adds a null in tagLinks, fail the patch.
        DiskState patchState = patch.getBody(DiskState.class);
        if (patchState.tagLinks != null && patchState.tagLinks.contains(null)) {
            patch.fail(new IllegalArgumentException("tagLink cannot have null elements. "
                    + "diskLink = " + patchState.documentSelfLink));
            logWarning("Failing attempt to PATCH null tagLinks element to DiskState "
                    + "[Referrer:%s], [DiskLink:%s]", patch.getReferer(),
                    patchState.documentSelfLink);
            return;
        }

        DiskState currentState = getState(patch);
        Function<Operation, Boolean> customPatchHandler = new Function<Operation, Boolean>() {
            @Override
            public Boolean apply(Operation t) {
                DiskState patchBody = patch.getBody(DiskState.class);
                boolean hasStateChanged = false;
                if (patchBody.capacityMBytes != 0
                        && patchBody.capacityMBytes != currentState.capacityMBytes) {
                    currentState.capacityMBytes = patchBody.capacityMBytes;
                    hasStateChanged = true;
                }
                return hasStateChanged;
            }
        };
        ResourceUtils.handlePatch(patch, currentState, getStateDescription(), DiskState.class,
                customPatchHandler);
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument td = super.getDocumentTemplate();
        // enable metadata indexing
        td.documentDescription.documentIndexingOptions =
                EnumSet.of(DocumentIndexingOption.INDEX_METADATA);
        ServiceUtils.setRetentionLimit(td);
        DiskState template = (DiskState) td;

        template.id = UUID.randomUUID().toString();
        template.type = DiskType.SSD;
        template.status = DiskStatus.DETACHED;
        template.capacityMBytes = 2 ^ 32L;
        template.name = "disk01";

        return template;
    }
}
