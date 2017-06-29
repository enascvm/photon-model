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

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import com.vmware.photon.controller.model.ServiceUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.support.IPVersion;
import com.vmware.photon.controller.model.util.AssertUtil;
import com.vmware.photon.controller.model.util.SubnetValidator;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

/**
 * Represents a range of IP addresses, assigned statically or by DHCP.
 * Reserved IP addresses should not be part of the range (for example, broadcast IP)
 *
 * @see SubnetService.SubnetState
 */
public class SubnetRangeService extends StatefulService {
    public static final String FACTORY_LINK = UriPaths.RESOURCES + "/subnet-ranges";

    /**
     * Represents the state of a subnet.
     */
    public static class SubnetRangeState extends ResourceState {

        public static final String FIELD_NAME_SUBNET_LINK = "subnetLink";

        /**
         * Link to the subnet this subnet range is part of.
         */
        @Documentation(description = "Link to the parent subnet.")
        @PropertyOptions(usage = {
                ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT,
                ServiceDocumentDescription.PropertyUsageOption.LINK
                })
        public String subnetLink;

        /**
         * Start IP address.
         */
        @Documentation(description = "Start ip address of the range")
        @PropertyOptions(usage = {
                ServiceDocumentDescription.PropertyUsageOption.REQUIRED,
                ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL
                })
        public String startIPAddress;

        /**
         * End IP address.
         */
        @Documentation(description = "End IP address of the range")
        @PropertyOptions(usage = {
                ServiceDocumentDescription.PropertyUsageOption.REQUIRED,
                ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL
                })
        public String endIPAddress;

        /**
         * Whether the start and end ip address is IPv4 or IPv6.
         * Default value IPv4.
         */
        @Documentation(description = "IP address version: IPv4 or IPv6. Default: IPv4")
        @PropertyOptions(usage = {
                ServiceDocumentDescription.PropertyUsageOption.OPTIONAL,
                ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL
                })
        public IPVersion ipVersion;

        /**
         * Whether this ip range is managed by a DHCP server or static allocation.
         * If not set, default to false.
         */
        @Documentation(description = "Indication if the range is managed by DHCP. Default: false.")
        @PropertyOptions(usage = {
                ServiceDocumentDescription.PropertyUsageOption.OPTIONAL,
                ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL
                })
        public Boolean isDHCP;

        /**
         * DNS IP addresses for this subnet range.
         * May override the SubnetState values.
         */
        @Documentation(description = "DNS server addresses")
        @PropertyOptions(
                usage = ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Set<String> dnsServerAddresses;

        /**
         * DNS domain of the subnet range.
         * May override the SubnetState values.
         */
        @PropertyOptions(usage = ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String domain;

        /**
         * DNS domain search.
         * May override the SubnetState values.
         */
        @PropertyOptions(
                usage = ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Set<String> dnsSearchDomains;

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("name: ").append(this.name);
            sb.append(", id: ").append(this.id);
            sb.append(", subnet: ").append(this.subnetLink);
            sb.append(", start IP address: ").append(this.startIPAddress);
            sb.append(", end IP address: ").append(this.endIPAddress);
            sb.append(", IP version: ").append(this.ipVersion);
            sb.append(", is DHCP: ").append(this.isDHCP);
            sb.append(", domain: ").append(this.domain);

            return sb.toString();
        }
    }

    public SubnetRangeService() {
        super(SubnetRangeState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handleStart(Operation start) {
        try {
            processInput(start);
            start.complete();
        } catch (Throwable t) {
            start.fail(t);
        }
    }

    @Override
    public void handlePut(Operation put) {
        try {
            SubnetRangeState returnState = processInput(put);
            setState(put, returnState);
            put.complete();
        } catch (Exception e) {
            this.logSevere(String.format("SubnetRangeService: failed to perform put [%s]",
                    e.getMessage()));
            put.fail(e);
        }
    }

    @Override
    public void handlePost(Operation post) {
        try {
            SubnetRangeState returnState = processInput(post);
            setState(post, returnState);
            post.complete();
        } catch (Exception e) {
            this.logSevere(String.format("SubnetRangeService: failed to perform post [%s]",
                    e.getMessage()));
            post.fail(e);
        }
    }

    @Override
    public void handlePatch(Operation patch) {
        if (!patch.hasBody()) {
            patch.fail(new IllegalArgumentException("Patch body is required"));
            return;
        }

        try {
            SubnetRangeState currentState = getState(patch);

            // Merge the patch values to current state
            // In order to validate the merged result
            EnumSet<Utils.MergeResult> mergeResult =
                    Utils.mergeWithStateAdvanced(getStateDescription(), currentState,
                            SubnetRangeState.class, patch);

            boolean hasStateChanged = mergeResult.contains(Utils.MergeResult.STATE_CHANGED);

            if (hasStateChanged) {
                validateState(currentState);
                setState(patch, currentState);
            } else {
                patch.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
            }
            patch.complete();

        } catch (Exception e) {
            this.logSevere(String.format("SubnetRangeService: failed to perform patch [%s]",
                    e.getMessage()));
            patch.fail(e);
        }
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument td = super.getDocumentTemplate();
        ServiceUtils.setRetentionLimit(td);
        SubnetRangeState template = (SubnetRangeState) td;

        template.id = UUID.randomUUID().toString();
        template.name = "subnet-range";

        return template;
    }

    /**
     * @param op operation
     * @return a valid SubnetRangeState
     * @throws IllegalArgumentException if input invalid
     */
    private SubnetRangeState processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        SubnetRangeState state = op.getBody(SubnetRangeState.class);
        validateState(state);
        return state;
    }

    /**
     * Validate:
     * - valid start IP address
     * - valid end IP address
     * - valid range
     *
     * @param state SubnetRangeState to validate
     */
    // TODO validate against subnet mask and default gateway in SubnetState
    private void validateState(SubnetRangeState state) {
        Utils.validateState(getStateDescription(), state);

        AssertUtil.assertTrue(
                SubnetValidator.isValidIPAddress(state.startIPAddress, state.ipVersion),
                "Invalid start IP address: " + state.startIPAddress);
        AssertUtil.assertTrue(
                SubnetValidator.isValidIPAddress(state.endIPAddress, state.ipVersion),
                "Invalid end IP address: " + state.endIPAddress);
        AssertUtil.assertTrue(!SubnetValidator
                        .isStartIPGreaterThanEndIP(state.startIPAddress, state.endIPAddress,
                                state.ipVersion),
                "Subnet range is invalid. Start IP address must be less than end IP address");
    }
}