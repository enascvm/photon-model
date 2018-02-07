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
import java.util.Set;
import java.util.function.Function;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;

import com.vmware.photon.controller.model.ServiceUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.constants.ReleaseConstants;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.DocumentIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class StorageDescriptionService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.RESOURCES + "/storage-descriptions";

    public StorageDescriptionService() {
        super(StorageDescription.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    /**
     * This class represents the document state associated with a
     * {@link com.vmware.photon.controller.model.resources.StorageDescriptionService} task.
     */
    public static class StorageDescription extends ResourceState {
        public static final String FIELD_NAME_ADAPTER_REFERENCE = "adapterManagementReference";

        /**
         * Type of Storage.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String type;

        /**
         * Self-link to the AuthCredentialsService used to access this compute
         * host.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String authCredentialsLink;

        /**
         * The pool which this resource is a part of.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String resourcePoolLink;

        /**
         * Reference to the management endpoint of the compute provider.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public URI adapterManagementReference;

        /**
         * Total capacity of the storage.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Long capacityBytes;

        /**
         * Link to the cloud account endpoint the disk belongs to.
         * @deprecated Use {@link #endpointLinks} instead.
         */
        @Since(ReleaseConstants.RELEASE_VERSION_0_5_7)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @Deprecated
        public String endpointLink;

        /**
         * Indicates whether this storage description supports encryption or not.
         */
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_16)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Boolean supportsEncryption;

        @Override
        public void copyTo(ResourceState target) {
            super.copyTo(target);
            if (target instanceof StorageDescription) {
                StorageDescription targetState = (StorageDescription) target;
                targetState.type = this.type;
                targetState.authCredentialsLink = this.authCredentialsLink;
                targetState.resourcePoolLink = this.resourcePoolLink;
                targetState.adapterManagementReference = this.adapterManagementReference;
                targetState.capacityBytes = this.capacityBytes;
                targetState.computeHostLink = this.computeHostLink;
                targetState.endpointLink = this.endpointLink;
                targetState.supportsEncryption = this.supportsEncryption;
            }
        }
    }

    /**
     * Expanded storage description along with its resource group states.
     */
    public static class StorageDescriptionExpanded extends StorageDescription {
        /**
         * Set of resource group states to which this storage description belongs to.
         */
        public Set<ResourceGroupState> resourceGroupStates;

        public static URI buildUri(URI sdUri) {
            return UriUtils.buildExpandLinksQueryUri(sdUri);
        }
    }

    @Override
    public void handleCreate(Operation start) {
        StorageDescription state = processInput(start);
        state.documentCreationTimeMicros = Utils.getNowMicrosUtc();
        ResourceUtils.populateTags(this, state)
                .whenCompleteNotify(start);
    }

    @Override
    public void handleDelete(Operation delete) {
        ResourceUtils.handleDelete(delete, this);
    }

    @Override
    public void handlePut(Operation put) {
        StorageDescription returnState = validatePut(put);
        ResourceUtils.populateTags(this, returnState)
                .thenAccept(__ -> setState(put, returnState))
                .whenCompleteNotify(put);
    }

    private StorageDescription validatePut(Operation op) {
        StorageDescription state = processInput(op);
        StorageDescription currentState = getState(op);
        ResourceUtils.validatePut(state, currentState);
        return state;
    }

    private StorageDescription processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        StorageDescription state = op.getBody(StorageDescription.class);
        Utils.validateState(getStateDescription(), state);
        if (state.name == null) {
            throw new IllegalArgumentException("name is required.");
        }
        return state;
    }

    @Override
    public void handlePatch(Operation patch) {
        StorageDescription currentState = getState(patch);
        Function<Operation, Boolean> customPatchHandler = t -> {
            boolean hasStateChanged = false;
            StorageDescription patchBody = patch.getBody(StorageDescription.class);
            if (patchBody.creationTimeMicros != null && currentState.creationTimeMicros == null &&
                    currentState.creationTimeMicros != patchBody.creationTimeMicros) {
                currentState.creationTimeMicros = patchBody.creationTimeMicros;
                hasStateChanged = true;
            }
            return hasStateChanged;
        };
        ResourceUtils.handlePatch(this, patch, currentState, getStateDescription(),
                StorageDescription.class, customPatchHandler);
    }

    @Override
    public void handleGet(Operation get) {
        StorageDescription currentState = getState(get);
        boolean doExpand = get.getUri().getQuery() != null &&
                UriUtils.hasODataExpandParamValue(get.getUri());

        if (!doExpand) {
            get.setBody(currentState).complete();
            return;
        }

        StorageDescriptionExpanded sdExpanded = new StorageDescriptionExpanded();
        currentState.copyTo(sdExpanded);

        List<Operation> getOps = new ArrayList<>();
        if (currentState.groupLinks != null) {
            sdExpanded.resourceGroupStates = new HashSet<>(currentState.groupLinks.size());
            currentState.groupLinks.stream().forEach(rgLink -> {
                getOps.add(Operation.createGet(this, rgLink)
                        .setReferer(this.getUri())
                        .setCompletion((o, e) -> {
                            if (e == null) {
                                sdExpanded.resourceGroupStates.add(o.getBody(ResourceGroupState.class));
                            } else {
                                logFine("Could not fetch resource group state %s due to %s",
                                        rgLink, e.getMessage());
                            }
                        }));
            });
            if (!getOps.isEmpty()) {
                OperationJoin.create(getOps)
                        .setCompletion((ops, exs) -> {
                            if (exs != null) {
                                get.fail(new IllegalStateException(Utils.toString(exs)));
                            } else {
                                get.setBody(sdExpanded).complete();
                            }
                        }).sendWith(this);
            } else {
                get.setBody(sdExpanded).complete();
            }
        } else {
            get.setBody(sdExpanded).complete();
        }
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
}
