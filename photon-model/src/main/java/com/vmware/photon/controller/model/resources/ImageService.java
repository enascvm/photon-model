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

import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;

import static com.vmware.xenon.common.UriUtils.buildUriPath;

import com.vmware.photon.controller.model.ServiceUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.TenantService;

/**
 * Represents an image.
 */
public class ImageService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.RESOURCES_IMAGES;

    /**
     * Represents the state of an image.
     */
    public static class ImageState extends ResourceState {

        public static final String FIELD_NAME_DESCRIPTION = "description";
        public static final String FIELD_NAME_REGION_ID = "regionId";
        public static final String FIELD_NAME_OS_FAMILY = "osFamily";
        public static final String FIELD_NAME_ENDPOINT_LINK = PhotonModelConstants.FIELD_NAME_ENDPOINT_LINK;

        /**
         * User-friendly description of the image.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @PropertyOptions(indexing = PropertyIndexingOption.CASE_INSENSITIVE)
        public String description;

        /**
         * The OS family of the image. The value, if provided, might be 'Linux', 'Windows', etc.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @PropertyOptions(indexing = PropertyIndexingOption.CASE_INSENSITIVE)
        public String osFamily;

        /**
         * Optional region identifier of the image indicating where the image is available.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String regionId;

        /**
         * Optional link to the {@code EndpointState} the image belongs to. Leave blank to indicate
         * the image is public/shared or provide an end-point link to indicate it is private
         * (requires explicit launch permissions).
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @UsageOption(option = PropertyUsageOption.LINK)
        public String endpointLink;
    }

    public ImageService() {
        super(ImageState.class);

        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);

        super.toggleOption(ServiceOption.ON_DEMAND_LOAD, true);
    }

    @Override
    public void handleCreate(Operation createOp) {
        if (checkForValid(createOp)) {
            super.handleCreate(createOp);
        }
    }

    @Override
    public void handlePut(Operation putOp) {
        if (checkForValid(putOp)) {
            super.handlePut(putOp);
        }
    }

    @Override
    public void handlePatch(Operation patchOp) {
        ResourceUtils.handlePatch(
                patchOp, getState(patchOp), getStateDescription(), ImageState.class, null);
    }

    /**
     * Common validation login.
     */
    private boolean checkForValid(Operation op) {
        if (checkForBody(op)) {
            try {
                Utils.validateState(getStateDescription(), op.getBody(ImageState.class));
                return true;
            } catch (Throwable t) {
                op.fail(t);
                return false;
            }
        }
        return false;
    }

    @Override
    public ImageState getDocumentTemplate() {

        ImageState image = (ImageState) super.getDocumentTemplate();
        ServiceUtils.setRetentionLimit(image);

        image.id = "endpoint-specific-image-id";
        image.name = "endpoint-specific-image-name";
        image.description = "user-friendly-image-description";
        image.osFamily = "Linux";
        image.regionId = "endpoint-specific-image-region-id";

        image.endpointLink = buildUriPath(EndpointService.FACTORY_LINK, "the-A-cloud");
        image.groupLinks = singleton(
                buildUriPath(ResourceGroupService.FACTORY_LINK, "the-A-folder"));
        image.tenantLinks = singletonList(buildUriPath(TenantService.FACTORY_LINK, "the-A-tenant"));

        return image;
    }
}
