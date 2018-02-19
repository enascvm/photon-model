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

import java.util.EnumSet;

import com.vmware.photon.controller.model.ServiceUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.resources.util.PhotonModelUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.DocumentIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;


/**
 * Describes the resource that is used by a compute type.
 */
public class ResourceDescriptionService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.RESOURCES
            + "/resource-descriptions";

    /**
     * This class represents the document state associated with a
     * {@link ResourceDescriptionService} task.
     */
    public static class ResourceDescription extends ResourceState {

        /**
         * Type of compute to create. Used to find Computes which can create
         * this child.
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String computeType;

        /**
         * The compute description that defines the resource instances.
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String computeDescriptionLink;
    }

    public ResourceDescriptionService() {
        super(ResourceDescription.class);
        super.toggleOption(Service.ServiceOption.PERSISTENCE, true);
        super.toggleOption(Service.ServiceOption.REPLICATION, true);
        super.toggleOption(Service.ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handleCreate(Operation start) {
        if (PhotonModelUtils.isFromMigration(start)) {
            start.complete();
            return;
        }

        ResourceDescription state = processInput(start);
        ResourceUtils.populateTags(this, state)
                .whenCompleteNotify(start);
    }

    @Override
    public void handleDelete(Operation delete) {
        ResourceUtils.handleDelete(delete, this);
    }

    @Override
    public void handlePut(Operation put) {
        if (PhotonModelUtils.isFromMigration(put)) {
            super.handlePut(put);
            return;
        }

        ResourceDescription returnState = processInput(put);
        ResourceUtils.populateTags(this, returnState)
                .thenAccept(__ -> setState(put, returnState))
                .whenCompleteNotify(put);
    }

    @Override
    public void handlePatch(Operation patch) {
        ResourceDescription currentState = getState(patch);
        ResourceUtils.handlePatch(this, patch, currentState, getStateDescription(),
                ResourceDescription.class, null);
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();
        // enable metadata indexing
        template.documentDescription.documentIndexingOptions =
                EnumSet.of(DocumentIndexingOption.INDEX_METADATA);
        ServiceUtils.setRetentionLimit(template);
        return template;
    }

    private ResourceDescription processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        ResourceDescription state = op.getBody(ResourceDescription.class);
        Utils.validateState(getStateDescription(), state);
        return state;
    }
}
