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

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.apache.commons.validator.routines.InetAddressValidator;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Represents a re-usable description for a network interface instance.
 */
public class NetworkInterfaceDescriptionService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.RESOURCES_NETWORK_INTERFACES;

    public enum IpAssignment {
        /**
         * Static networking allows you to specify a subnet, from which to choose available IP. By
         * default an automatically IP reservation is used. But one can use also a static IP
         * reservation.
         */
        STATIC,
        /**
         * Dynamic networking defers IP selection to the Infrastructure layer. The IPs are
         * automatically reservation.
         */
        DYNAMIC
    }

    /**
     * Represents the state of a network interface description.
     */
    public static class NetworkInterfaceDescription extends ResourceState {

        public static final String FIELD_NAME_NETWORK_LINK = "networkLink";

        /**
         * The static IP of the interface. Optional.
         */
        public String address;

        /**
         * IP assignment type the use. By default dynamic s used.
         */
        public IpAssignment assignment = IpAssignment.DYNAMIC;

        /**
         * Firewalls with which this compute instance is associated.
         */
        @PropertyOptions(usage = { PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL,
                PropertyUsageOption.LINKS })
        public List<String> firewallLinks;

        /**
         * Link to the network this nic is connected to.
         */
        public String networkLink;

        /**
         * Subnet in which the network interface will be created.
         */
        public String subnetLink;
    }

    public NetworkInterfaceDescriptionService() {
        super(NetworkInterfaceDescription.class);
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
        NetworkInterfaceDescription returnState = processInput(put);
        setState(put, returnState);
        put.complete();
    }

    private NetworkInterfaceDescription processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        NetworkInterfaceDescription state = op.getBody(NetworkInterfaceDescription.class);
        validateState(state);
        return state;
    }

    @Override
    public void handlePatch(Operation patch) {
        NetworkInterfaceDescription currentState = getState(patch);
        ResourceUtils.handlePatch(patch, currentState, getStateDescription(),
                NetworkInterfaceDescription.class, null);
    }

    private void validateState(NetworkInterfaceDescription state) {
        Utils.validateState(getStateDescription(), state);

        if (state.address != null) {
            if (state.assignment != IpAssignment.STATIC) {
                throw new IllegalArgumentException(
                        "IP can be reseved can be assign only when assignment is STATIC");
            }
            if (!InetAddressValidator.getInstance().isValidInet4Address(
                    state.address)) {
                throw new IllegalArgumentException("IP address is invalid");
            }

        }
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument td = super.getDocumentTemplate();
        NetworkInterfaceDescription template = (NetworkInterfaceDescription) td;

        template.id = UUID.randomUUID().toString();
        template.name = "my-nic";
        template.subnetLink = UriUtils.buildUriPath(SubnetService.FACTORY_LINK,
                "sub-network");
        template.assignment = IpAssignment.STATIC;
        template.address = "10.1.0.12";
        template.firewallLinks = Arrays
                .asList(UriUtils.buildUriPath(FirewallService.FACTORY_LINK, "firewall-one"));

        return template;
    }
}