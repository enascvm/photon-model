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
import java.util.UUID;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;

import com.vmware.photon.controller.model.ServiceUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.constants.ReleaseConstants;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.DocumentIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

/**
 * Represents a snapshot resource.
 */
public class SnapshotService extends StatefulService {
    public static final String FACTORY_LINK = UriPaths.RESOURCES + "/snapshots";

    /**
     * This class represents the document state associated with a
     * {@link SnapshotService} task.
     */
    public static class SnapshotState extends ResourceState {

        public static final String FIELD_NAME_PARENT_LINK = "parentLink";
        public static final String FIELD_NAME_COMPUTE_LINK = "computeLink";
        public static final String FIELD_NAME_IS_CURRENT = "isCurrent";

        /**
         * Description of this snapshot.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String description;

        /**
         * Compute link for this snapshot.
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @PropertyOptions(indexing = {ServiceDocumentDescription.PropertyIndexingOption.EXPAND})
        public String computeLink;

        /**
         * Parent snapshot link for this snapshot.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_23)
        public String parentLink;

        /**
         * Identify this snapshot as the current state for Compute. At a given point amongst all
         * the snapshots for a Compute only one at max may have this flag set.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_23)
        @PropertyOptions(indexing = {ServiceDocumentDescription.PropertyIndexingOption.EXPAND})
        public Boolean isCurrent;
    }

    public SnapshotService() {
        super(SnapshotState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handleCreate(Operation start) {
        SnapshotState state = processInput(start);
        ResourceUtils.populateTags(this, state)
                .whenCompleteNotify(start);
    }

    @Override
    public void handleDelete(Operation delete) {
        ResourceUtils.handleDelete(delete, this);
    }


    @Override
    public void handlePut(Operation put) {
        SnapshotState returnState = processInput(put);
        ResourceUtils.populateTags(this, returnState)
                .thenAccept(__ -> setState(put, returnState))
                .whenCompleteNotify(put);
    }

    private SnapshotState processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        SnapshotState state = op.getBody(SnapshotState.class);
        Utils.validateState(getStateDescription(), state);
        if (state.name == null) {
            throw new IllegalArgumentException("name is required.");
        }
        return state;
    }

    @Override
    public void handlePatch(Operation patch) {
        SnapshotState currentState = getState(patch);
        ResourceUtils.handlePatch(this, patch, currentState, getStateDescription(),
                SnapshotState.class, null);
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument td = super.getDocumentTemplate();
        // enable metadata indexing
        td.documentDescription.documentIndexingOptions =
                EnumSet.of(DocumentIndexingOption.INDEX_METADATA);
        ServiceUtils.setRetentionLimit(td);
        SnapshotState template = (SnapshotState) td;

        template.id = UUID.randomUUID().toString();
        template.name = "snapshot01";
        template.description = "";
        return template;
    }

    public enum SnapshotRequestType {
        CREATE,
        DELETE,
        REVERT;

        public static SnapshotRequestType fromString(String name) {
            SnapshotRequestType result = null;
            for (SnapshotRequestType type: SnapshotRequestType.values()) {
                if (type.name().equalsIgnoreCase(name)) {
                    result = type;
                    return result;
                }
            }
            return result;
        }
    }
}
