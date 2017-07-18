/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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

import static com.vmware.photon.controller.model.resources.IPAddressService.IPAddressState.DEFAULT_IP_VERSION;
import static com.vmware.photon.controller.model.resources.IPAddressService.IPAddressState.DEFAULT_STATUS;

import java.util.UUID;

import com.vmware.photon.controller.model.ServiceUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.support.IPVersion;
import com.vmware.photon.controller.model.util.AssertUtil;
import com.vmware.photon.controller.model.util.SubnetValidator;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

/**
 * Represents a statically assigned ip address from a pre-defined subnet range.
 *
 * @see SubnetRangeService.SubnetRangeState
 */
public class IPAddressService extends StatefulService {
    public static final String FACTORY_LINK = UriPaths.RESOURCES + "/ip-addresses";

    /**
     * Represents the state of an ip address.
     */
    public static class IPAddressState extends ResourceState {

        public static final String FIELD_NAME_SUBNET_RANGE_LINK = "subnetRangeLink";
        public static final String FIELD_NAME_NETWORK_INTERFACE_LINK = "networkInterfaceLink";
        public static final String FIELD_NAME_IP_ADDRESS_STATUS = "ipAddressStatus";

        // Default values for non-required fields
        public static final IPVersion DEFAULT_IP_VERSION = IPVersion.IPv4;
        public static final IPAddressStatus DEFAULT_STATUS = IPAddressStatus.AVAILABLE;

        public enum IPAddressStatus {
            ALLOCATED, // IP is allocated
            RELEASED,  // IP is no longer allocated, but still not available to be re-allocated
            AVAILABLE;  // IP is available for allocation, this is an intermediate state before the IPAddressState is being deleted

            /**
             * Allocated IPs should be in 'released' state before becoming 'available' again for allocation.
             * This method validates the status transitions.
             *
             * @param currentStatus current IPAddressStatus
             * @param newStatus     IPAddressStatus to transition to
             * @return true if the transition is valid
             */
            static boolean isValidTransition(IPAddressStatus currentStatus,
                    IPAddressStatus newStatus) {
                return (currentStatus != null && currentStatus.equals(newStatus) ||
                        (AVAILABLE.equals(currentStatus) && ALLOCATED.equals(newStatus)) ||
                        (ALLOCATED.equals(currentStatus) && RELEASED.equals(newStatus)) ||
                        (RELEASED.equals(currentStatus) && AVAILABLE.equals(newStatus)));
            }
        }

        /**
         * Link to the subnet range this IP belongs to.
         */
        @Documentation(description = "Link to the parent subnet range.")
        @PropertyOptions(usage = {
                ServiceDocumentDescription.PropertyUsageOption.OPTIONAL,
                ServiceDocumentDescription.PropertyUsageOption.LINK,
                ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT
                })
        public String subnetRangeLink;

        /**
         * Link to the network interface this IP is assigned to.
         */
        @Documentation(description = "Link to the network interface.")
        @PropertyOptions(usage = {
                ServiceDocumentDescription.PropertyUsageOption.OPTIONAL,
                ServiceDocumentDescription.PropertyUsageOption.LINK
                })
        public String networkInterfaceLink;

        /**
         * Ip address.
         */
        @Documentation(description = "IP address")
        @PropertyOptions(usage = {
                ServiceDocumentDescription.PropertyUsageOption.REQUIRED,
                ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT
                })
        public String ipAddress;

        /**
         * Whether the start and end ip address is IPv4 or IPv6.
         * If not set, default to IPv4.
         */
        @Documentation(description = "IP address version: IPv4 or IPv6. Default: IPv4")
        @PropertyOptions(usage = {
                ServiceDocumentDescription.PropertyUsageOption.OPTIONAL,
                ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL
                })
        public IPVersion ipVersion;

        /**
         * The state of the IP address.
         */
        @Documentation(description = "IP address status: ALLOCATED, RELEASED or AVAILABLE")
        @PropertyOptions(usage = {
                ServiceDocumentDescription.PropertyUsageOption.REQUIRED,
                ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL
                })
        public IPAddressStatus ipAddressStatus;

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("name: ").append(this.name);
            sb.append(", id: ").append(this.id);
            sb.append(", subnet range link: ").append(this.subnetRangeLink);
            sb.append(", network interface link: ").append(this.networkInterfaceLink);
            sb.append(", IP address: ").append(this.ipAddress);
            sb.append(", IP version: ").append(this.ipVersion);
            sb.append(", status: ").append(this.ipAddressStatus);

            return sb.toString();
        }
    }

    public IPAddressService() {
        super(IPAddressState.class);
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
    public void handlePost(Operation post) {
        IPAddressState returnState = processInput(post);
        setState(post, returnState);
        post.complete();
    }

    @Override
    public void handlePut(Operation put) {
        IPAddressState newState = processInput(put);

        // Verify valid status changes
        IPAddressState currentState = getState(put);
        validateIPAddressStatusTransition(currentState.ipAddressStatus, newState.ipAddressStatus);
        setState(put, newState);
        put.complete();
    }

    @Override
    public void handlePatch(Operation patch) {
        if (!patch.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }

        IPAddressState currentState = getState(patch);

        ResourceUtils.handlePatch(patch, currentState, getStateDescription(),
                IPAddressState.class, op -> {
                    IPAddressState patchState = patch.getBody(IPAddressState.class);
                    boolean hasChanged = false;

                    // Verify valid status changes
                    if (patchState.ipAddressStatus != null
                            && patchState.ipAddressStatus != currentState.ipAddressStatus) {
                        validateIPAddressStatusTransition(currentState.ipAddressStatus,
                                patchState.ipAddressStatus);
                        hasChanged = true;
                    }

                    return Boolean.valueOf(hasChanged);
                });
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument td = super.getDocumentTemplate();
        ServiceUtils.setRetentionLimit(td);
        IPAddressState template = (IPAddressState) td;

        template.id = UUID.randomUUID().toString();
        template.name = "ip-address";

        return template;
    }

    /**
     * @param op operation
     * @return a valid IPAddressState
     * @throws IllegalArgumentException if input invalid
     */
    private IPAddressState processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        IPAddressState state = op.getBody(IPAddressState.class);
        validateState(state);
        return state;
    }

    /**
     * Validation upon creation of an IP address object.
     * - valid IP address
     * No need to validate that the IP is within the range.
     *
     * @param state IpAddressState to validate
     * @throws IllegalArgumentException for invalid state
     */
    private void validateState(IPAddressState state) {
        // Verify values based on the document description
        Utils.validateState(getStateDescription(), state);

        if (state.ipVersion == null) {
            state.ipVersion = DEFAULT_IP_VERSION;
        }
        if (state.ipAddressStatus == null) {
            state.ipAddressStatus = DEFAULT_STATUS;
        }

        if (!SubnetValidator.isValidIPAddress(state.ipAddress, state.ipVersion)) {
            throw new LocalizableValidationException(String.format("Invalid IP address: %s",
                    state.ipAddress),
                    "ip.address.invalid", state.ipAddress);
        }

        logFine("Completed validation of IPAddressState: " + state);
    }

    /**
     * @param currentStatus current IP address status
     * @param desiredStatus requested IP address status
     * @throws IllegalArgumentException if an invalid transition
     */
    private void validateIPAddressStatusTransition(IPAddressState.IPAddressStatus currentStatus,
            IPAddressState.IPAddressStatus desiredStatus) {
        AssertUtil.assertTrue(IPAddressState.IPAddressStatus
                        .isValidTransition(currentStatus, desiredStatus),
                String.format("Invalid IP address status transition from [%s] to [%s]",
                        currentStatus, desiredStatus));
    }
}