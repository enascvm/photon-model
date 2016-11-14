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

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.net.util.SubnetUtils;

import com.vmware.photon.controller.model.UriPaths;
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

        /**
         * Link to the network this subnet is part of.
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String networkLink;

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
                SubnetState.class, null);
    }

    private void validateState(SubnetState state) {
        Utils.validateState(getStateDescription(), state);

        // do we have a subnet in CIDR notation
        // creating new SubnetUtils to validate
        new SubnetUtils(state.subnetCIDR);
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument td = super.getDocumentTemplate();
        SubnetState template = (SubnetState) td;

        template.id = UUID.randomUUID().toString();
        template.subnetCIDR = "10.1.0.0/16";
        template.name = "sub-network";
        template.networkLink = UriUtils.buildUriPath(NetworkService.FACTORY_LINK,
                "on-prem-network");
        template.dnsServerAddresses = new HashSet<>();
        template.dnsServerAddresses.add("10.12.14.12");
        template.gatewayAddress = "10.1.0.1";

        return template;
    }
}
