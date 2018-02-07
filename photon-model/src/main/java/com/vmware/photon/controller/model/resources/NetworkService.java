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

import static com.vmware.photon.controller.model.constants.PhotonModelConstants.NETWORK_SUBTYPE_NETWORK_STATE;

import java.net.URI;
import java.util.EnumSet;
import java.util.UUID;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;
import org.apache.commons.net.util.SubnetUtils;

import com.vmware.photon.controller.model.ServiceUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.constants.ReleaseConstants;
import com.vmware.photon.controller.model.resources.util.PhotonModelUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.DocumentIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

/**
 * Represents a network resource.
 */
public class NetworkService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.RESOURCES_NETWORKS;

    /**
     * Network State document.
     */
    public static class NetworkState extends ResourceState {
        public static final String FIELD_NAME_ADAPTER_MANAGEMENT_REFERENCE = "adapterManagementReference";
        public static final String FIELD_NAME_AUTH_CREDENTIALS_LINK = "authCredentialsLink";

        /**
         * Subnet CIDR
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String subnetCIDR;

        /**
         * Link to secrets. Required
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String authCredentialsLink;

        /**
         * The pool which this resource is a part of. Required
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String resourcePoolLink;

        /**
         * The network adapter to use to create the network. Required
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public URI instanceAdapterReference;

        /**
         * Reference to the management endpoint of the compute provider.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public URI adapterManagementReference;

        /**
         * Link to the cloud account endpoint the network belongs to.
         * @deprecated Use {@link #endpointLinks} instead.
         */
        @Since(ReleaseConstants.RELEASE_VERSION_0_5_7)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @Deprecated
        public String endpointLink;

        /**
         * Network resource sub-type
         */
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_18)
        @UsageOption(option = PropertyUsageOption.SINGLE_ASSIGNMENT)
        public String type = NETWORK_SUBTYPE_NETWORK_STATE;
    }

    public NetworkService() {
        super(NetworkState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handleCreate(Operation start) {
        NetworkState state = processInput(start);
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
        NetworkState returnState = validatePut(put);
        ResourceUtils.populateTags(this, returnState)
                .thenAccept(__ -> setState(put, returnState))
                .whenCompleteNotify(put);
    }

    private NetworkState processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        NetworkState state = op.getBody(NetworkState.class);
        validateState(state);
        return state;
    }

    private NetworkState validatePut(Operation op) {
        NetworkState state = processInput(op);
        NetworkState currentState = getState(op);
        ResourceUtils.validatePut(state, currentState);
        return state;
    }

    public void validateState(NetworkState state) {
        Utils.validateState(getStateDescription(), state);
        PhotonModelUtils.validateRegionId(state);

        // do we have a subnet in CIDR notation
        // creating new SubnetUtils to validate
        if (state.subnetCIDR != null) {
            new SubnetUtils(state.subnetCIDR);
        }
    }

    @Override
    public void handlePost(Operation post) {
        try {
            NetworkState returnState = processInput(post);
            setState(post, returnState);
            post.complete();
        } catch (Throwable t) {
            post.fail(t);
        }
    }

    @Override
    public void handlePatch(Operation patch) {
        NetworkState currentState = getState(patch);
        ResourceUtils.handlePatch(this, patch, currentState, getStateDescription(),
                NetworkState.class, t -> {
                    NetworkState patchBody = patch.getBody(NetworkState.class);
                    boolean hasStateChanged = false;
                    if (patchBody.endpointLink != null && currentState.endpointLink == null) {
                        currentState.endpointLink = patchBody.endpointLink;
                        hasStateChanged = true;
                    }
                    return hasStateChanged;
                });
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument td = super.getDocumentTemplate();
        // enable metadata indexing
        td.documentDescription.documentIndexingOptions =
                EnumSet.of(DocumentIndexingOption.INDEX_METADATA);
        ServiceUtils.setRetentionLimit(td);
        NetworkState template = (NetworkState) td;

        template.id = UUID.randomUUID().toString();
        template.subnetCIDR = "10.1.0.0/16";
        template.name = "cell-network";

        return template;
    }
}
