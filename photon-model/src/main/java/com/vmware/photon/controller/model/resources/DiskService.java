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
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;

import com.vmware.photon.controller.model.ServiceUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.constants.ReleaseConstants;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
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
        DETACHED, ATTACHED
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
        public static final String FIELD_NAME_COMPUTE_HOST_LINK = "computeHostLink";

        /**
         * Identifier of the zone associated with this disk service instance.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String zoneId;

        /**
         * Identifier of the region associated with this disk service
         * instance. Interpretation of the regionId is left to the InstanceAdapter.
         */
        public String regionId;

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
         */
        public URI sourceImageReference;

        /**
         * Type of this disk service instance.
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public DiskType type;

        /**
         * Status of this disk service instance.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public DiskStatus status = DiskStatus.DETACHED;

        /**
         * Capacity (in MB) of this disk service instance.
         */
        public long capacityMBytes;

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
         * Disk creation time in micros since epoch.
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public Long creationTimeMicros;

        /**
         * Link to the compute host the disk belongs to. This property is not used to associate the
         * diskState with it's compute (VM). That association happens through the compute's
         * diskLinks property.
         */
        public String computeHostLink;

        /**
         * Link to the cloud account endpoint the disk belongs to.
         */
        @Since(ReleaseConstants.RELEASE_VERSION_0_5_7)
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
        logInfo("Deleting Disk, Path: %s, Operation ID: %d, Referrer: %s",
                delete.getUri().getPath(), delete.getId(),
                delete.getRefererAsString());
        super.handleDelete(delete);
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

    private DiskState processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        DiskState state = op.getBody(DiskState.class);
        validateState(state);
        return state;
    }

    private void validateState(DiskState state) {
        if (state.creationTimeMicros == null) {
            state.creationTimeMicros = Utils.getNowMicrosUtc();
        }

        Utils.validateState(getStateDescription(), state);

        if (state.name == null) {
            throw new IllegalArgumentException("name is required.");
        }

        if (state.capacityMBytes <= 1 && state.sourceImageReference == null
                && state.customizationServiceReference == null) {
            throw new IllegalArgumentException(
                    "capacityMBytes, sourceImageReference, or customizationServiceReference is required");
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
