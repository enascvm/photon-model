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

import java.util.UUID;

import io.netty.util.internal.StringUtil;
import org.apache.commons.lang3.StringUtils;

import com.vmware.photon.controller.model.ServiceUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.resources.IPAddressService.IPAddressState.IPAddressStatus;
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
        public static final String FIELD_NAME_IP_ADDRESS_STATUS = "ipAddressStatus";
        public static final String FIELD_NAME_IP_ADDRESS = "ipAddress";
        public static final String FIELD_NAME_CONNECTED_RESOURCE_LINK = "connectedResourceLink";

        // Default values for non-required fields
        public static final IPVersion DEFAULT_IP_VERSION = IPVersion.IPv4;

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
         * Link to the resource this IP is assigned to.
         */
        @Documentation(description = "Link to the resource this IP is assigned to.")
        @PropertyOptions(usage = {
                ServiceDocumentDescription.PropertyUsageOption.OPTIONAL,
                ServiceDocumentDescription.PropertyUsageOption.LINK,
                ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL
                })
        public String connectedResourceLink;

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
                ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT
                })
        public IPVersion ipVersion;

        /**
         * The state of the IP address.
         */
        @Documentation(description = "IP address status: ALLOCATED, RELEASED or AVAILABLE")
        @PropertyOptions(usage = {
                ServiceDocumentDescription.PropertyUsageOption.REQUIRED
                })
        public IPAddressStatus ipAddressStatus;

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("name: ").append(this.name);
            sb.append(", id: ").append(this.id);
            sb.append(", subnet range link: ").append(this.subnetRangeLink);
            sb.append(", resource link: ").append(this.connectedResourceLink);
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
    public void handleCreate(Operation start) {
        IPAddressState state = processInput(start);
        ResourceUtils.populateTags(this, state)
                .whenCompleteNotify(start);
    }

    @Override
    public void handleDelete(Operation delete) {
        ResourceUtils.handleDelete(delete, this);
    }

    @Override
    public void handlePost(Operation post) {
        IPAddressState returnState = processInput(post);
        logInfo("Resource %s was granted the ip %s with the status %s", returnState.connectedResourceLink,
                returnState.ipAddress, returnState.ipAddressStatus);
        ResourceUtils.populateTags(this, returnState)
                .thenAccept(__ -> setState(post, returnState))
                .whenCompleteNotify(post);
    }

    @Override
    public void handlePut(Operation put) {
        if (!put.hasBody()) {
            put.fail(new IllegalArgumentException("body is required"));
        }

        try {
            IPAddressState newState = put.getBody(IPAddressState.class);

            // Verify valid status changes
            IPAddressState currentState = getState(put);
            if (isNoOperation(currentState, newState)) {
                put.addPragmaDirective(Operation.PRAGMA_DIRECTIVE_STATE_NOT_MODIFIED);
                put.setBody(currentState);
                put.complete();
                return;
            }
            // Clear connected resource when releasing the ip address
            if (IPAddressStatus.RELEASED.equals(newState.ipAddressStatus)) {
                newState.connectedResourceLink = null;
            }

            validateState(newState);
            logInfo("Validating transition for put request from current ip %s "
                            + "current status %s to new ip %s new status %s",
                    currentState.ipAddress, currentState.ipAddressStatus,
                    newState.ipAddress, newState.ipAddressStatus);
            validateIPAddressStatusTransition(currentState, newState);
            logInfo("Resource %s was granted the ip %s with the status %s", newState.connectedResourceLink,
                    newState.ipAddress, newState.ipAddressStatus);

            ResourceUtils.populateTags(this, newState)
                    .thenAccept(__ -> setState(put, newState))
                    .whenCompleteNotify(put);
        } catch (Throwable t) {
            put.fail(t);
        }
    }

    @Override
    public void handlePatch(Operation patch) {
        if (!patch.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }

        IPAddressState currentState = getState(patch);
        IPAddressState patchState = patch.getBody(IPAddressState.class);

        if (isNoOperation(currentState, patchState)) {
            patch.addPragmaDirective(Operation.PRAGMA_DIRECTIVE_STATE_NOT_MODIFIED);
            patch.setBody(currentState);
            patch.complete();
            return;
        }
        if (IPAddressStatus.RELEASED.equals(patchState.ipAddressStatus)) {
            patchState.connectedResourceLink = ResourceUtils.NULL_LINK_VALUE;
        }
        ResourceUtils.handlePatch(this, patch, currentState, getStateDescription(),
                IPAddressState.class, op -> {
                    boolean hasChanged = false;

                    // Verify valid status changes
                    if (patchState.ipAddressStatus != null
                            && patchState.ipAddressStatus != currentState.ipAddressStatus) {
                        logInfo("Validating transition for patch request from current ip %s "
                                + "current status %s to new ip %s new status %s",
                                currentState.ipAddress, currentState.ipAddressStatus,
                                patchState.ipAddress, patchState.ipAddressStatus);
                        validateIPAddressStatusTransition(currentState, patchState);
                        currentState.ipAddressStatus = patchState.ipAddressStatus;
                        validateIPAddressStatusWithConnectedResource(currentState);
                        logInfo("Resource %s was granted the ip %s with the status %s", patchState.connectedResourceLink,
                                patchState.ipAddress, patchState.ipAddressStatus);
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
        logInfo("Attempting to create IP %s for resource %s",state.ipAddress, state.connectedResourceLink);
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

        if (!SubnetValidator.isValidIPAddress(state.ipAddress, state.ipVersion)) {
            throw new LocalizableValidationException(String.format("Invalid IP address: %s",
                    state.ipAddress),
                    "ip.address.invalid", state.ipAddress);
        }

        validateIPAddressStatusWithConnectedResource(state);

        logFine("Completed validation of IPAddressState: " + state);
    }

    /**
     * @param currentState current IP address
     * @param desiredState requested IP address
     * @throws IllegalArgumentException if an invalid transition
     */
    private void validateIPAddressStatusTransition(IPAddressState currentState,
            IPAddressState desiredState) {
        AssertUtil.assertTrue(IPAddressStatus
                        .isValidTransition(currentState.ipAddressStatus, desiredState.ipAddressStatus),
                String.format("Invalid IP address status transition from [%s] to [%s]",
                        currentState.ipAddressStatus, desiredState.ipAddressStatus));
    }

    /**
     * Validate connectedResourceLink is set if IP address is ALLOCATED and not set otherwise
     *
     * @param ipAddressState
     */
    private void validateIPAddressStatusWithConnectedResource(IPAddressState ipAddressState) {
        AssertUtil.assertFalse(ipAddressState.ipAddressStatus == IPAddressStatus.ALLOCATED
                        && StringUtil.isNullOrEmpty(ipAddressState.connectedResourceLink),
                "ConnectedResourceLink is required if IP address status is ALLOCATED");
        AssertUtil.assertFalse((ipAddressState.ipAddressStatus == IPAddressStatus.RELEASED
                        || ipAddressState.ipAddressStatus == IPAddressStatus.AVAILABLE)
                        && linkHasValue(ipAddressState.connectedResourceLink),
                "ConnectedResourceLink must be null if IP address status is AVAILABLE or RELEASED");
    }

    /**
     * Checks if a link has value.
     *
     * @param link
     * @return true when a link field has a value
     */
    private boolean linkHasValue(String link) {
        return (!ResourceUtils.NULL_LINK_VALUE.equals(link) &&
                !StringUtil.isNullOrEmpty(link));
    }

    /**
     * Used for PUT or PATCH. Handle the case of two consecutive release calls of the same IP address document.
     * Avoid modifying currentResourceLink.
     *
     * @param currentState
     * @param newState
     * @return
     */
    private boolean isNoOperation(IPAddressState currentState, IPAddressState newState) {
        // Avoid changing status if the connected resource has changed
        // Since deallocate can be called twice (for retry) - make sure
        // it is ignored if the resource has changed
        if (linkHasValue(newState.connectedResourceLink) &&
                linkHasValue(currentState.connectedResourceLink) &&
                !newState.connectedResourceLink.equals(currentState.connectedResourceLink)) {
            logWarning("Cannot modify IP address [%s] and change the connected resource link. Operation ignored (current state: [%s] new state: [%s]).",
                    currentState.documentSelfLink, currentState, newState);
            return true;
        }

        // Ignore change from AVAILABLE to RELEASED, can happen when deallocation called twice
        if (IPAddressStatus.RELEASED.equals(newState.ipAddressStatus) &&
                IPAddressStatus.AVAILABLE.equals(currentState.ipAddressStatus)) {
            logInfo("IP address [%s] is already available, and need not be released. Operation ignored.",
                    currentState.documentSelfLink);
            return true;
        }

        if (StringUtils.equals(currentState.connectedResourceLink, newState.connectedResourceLink)
                &&
                StringUtils.equals(currentState.ipAddress, newState.ipAddress) &&
                StringUtils.equals(currentState.subnetRangeLink, newState.subnetRangeLink) &&
                currentState.ipAddressStatus.equals(newState.ipAddressStatus)) {
            return true;
        }

        return false;
    }
}