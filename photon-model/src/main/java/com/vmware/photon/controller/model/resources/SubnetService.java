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
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;

import org.apache.commons.net.util.SubnetUtils;

import com.vmware.photon.controller.model.ServiceUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.constants.ReleaseConstants;
import com.vmware.photon.controller.model.support.LifecycleState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Represents a subnet.
 */
public class SubnetService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.RESOURCES_SUBNETS;

    /**
     * Represents the state of a subnet.
     */
    public static class SubnetState extends ResourceState {

        public static final String FIELD_NAME_NETWORK_LINK = "networkLink";
        public static final String FIELD_NAME_ENDPOINT_LINK = PhotonModelConstants.FIELD_NAME_ENDPOINT_LINK;
        public static final String FIELD_NAME_LIFECYCLE_STATE = "lifecycleState";

        /**
         * Link to the network this subnet is part of.
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String networkLink;

        /**
         * Optional zone identifier of this subnet.
         */
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @Since(ReleaseConstants.RELEASE_VERSION_0_5_8)
        public String zoneId;

        /**
         * Subnet CIDR
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String subnetCIDR;

        /**
         * Subnet gatewayAddress IP
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String gatewayAddress;

        /**
         * DNS IP addresses for this subnet
         */
        @PropertyOptions(usage = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL, indexing = PropertyIndexingOption.EXPAND)
        public Set<String> dnsServerAddresses;

        /**
         * DNS domain of the this subnet
         */
        @PropertyOptions(usage = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String domain;

        /**
         * Domains search in
         */
        @PropertyOptions(usage = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL, indexing = PropertyIndexingOption.EXPAND)
        public Set<String> dnsSearchDomains;

        /**
         * Indicates whether the sub-network supports public IP assignment.
         */
        @Since(ReleaseConstants.RELEASE_VERSION_0_5_9)
        @PropertyOptions(usage = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Boolean supportPublicIpAddress;

        /**
         * Indicates whether this is the default subnet for the zone.
         */
        @Since(ReleaseConstants.RELEASE_VERSION_0_5_9)
        @PropertyOptions(usage = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Boolean defaultForZone;

        /**
         * Link to the cloud account endpoint the sub-network belongs to.
         */
        @Since(ReleaseConstants.RELEASE_VERSION_0_5_7)
        public String endpointLink;

        /**
         * The subnet adapter to use to create the subnet.
         */
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_6)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public URI instanceAdapterReference;

        /** Lifecycle state indicating runtime state of a resource instance. */
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_6)
        @Documentation(description = "Lifecycle state indicating runtime state of a resource instance.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public LifecycleState lifecycleState;
    }

    public SubnetService() {
        super(SubnetState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handleStart(Operation start) {
        processInput(start);
        start.complete();
    }

    @Override
    public void handlePut(Operation put) {
        SubnetState returnState = processInput(put);
        setState(put, returnState);
        put.complete();
    }

    private SubnetState processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        SubnetState state = op.getBody(SubnetState.class);
        validateState(state);
        return state;
    }

    @Override
    public void handlePatch(Operation patch) {
        SubnetState currentState = getState(patch);
        ResourceUtils.handlePatch(patch, currentState, getStateDescription(),
                SubnetState.class, t -> {
                    SubnetState patchBody = patch.getBody(SubnetState.class);
                    boolean hasStateChanged = false;
                    if (patchBody.endpointLink != null && currentState.endpointLink == null) {
                        currentState.endpointLink = patchBody.endpointLink;
                        hasStateChanged = true;
                    }
                    return hasStateChanged;
                });
    }

    private void validateState(SubnetState state) {
        if (state.lifecycleState == null) {
            state.lifecycleState = LifecycleState.READY;
        }

        Utils.validateState(getStateDescription(), state);

        // do we have a subnet in CIDR notation
        // creating new SubnetUtils to validate
        new SubnetUtils(state.subnetCIDR);
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument td = super.getDocumentTemplate();
        ServiceUtils.setRetentionLimit(td);
        SubnetState template = (SubnetState) td;

        template.id = UUID.randomUUID().toString();
        template.subnetCIDR = "10.1.0.0/16";
        template.name = "sub-network";
        template.networkLink = UriUtils.buildUriPath(NetworkService.FACTORY_LINK,
                "on-prem-network");
        template.dnsServerAddresses = new HashSet<>();
        template.dnsServerAddresses.add("10.12.14.12");
        template.gatewayAddress = "10.1.0.1";
        template.supportPublicIpAddress = Boolean.TRUE;
        template.defaultForZone = Boolean.TRUE;

        return template;
    }
}
