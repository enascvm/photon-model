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
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;
import org.apache.commons.net.util.SubnetUtils;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.constants.ReleaseConstants;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState.Rule;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

/**
 * Represents a security group resource.
 */
public class SecurityGroupService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.RESOURCES_SECURITY_GROUPS;

    public static enum Protocol {

        ANY(SecurityGroupService.ANY, 0), TCP("tcp", 6), UDP("udp", 17), ICMP("icmp", 1);

        private final int protocolNumber;
        private final String name;

        private Protocol(String name, int protocolNumber) {
            this.protocolNumber = protocolNumber;
            this.name = name;
        }

        public int getProtocolNumber() {
            return this.protocolNumber;
        }

        public String getName() {
            return this.name;
        }
        /**
         * Obtain the enumeration choice that corresponds to the provided String
         * which either equal the name or the ProtocolNumber of the choice
         */
        public static Protocol fromString(String s) {
            for (Protocol choice : values()) {
                if (s.equals(choice.getName()) || s.equals(String.format("{0}", choice.getProtocolNumber()))) {
                    return choice;
                }
            }
            return null;
        }
    }

    /**
     * ANY can be used when when specifying protocol or IP range in a security group rule.
     * <ul>
     * <li>protocol - the role is applicable to all protocols</li>
     * <li>IP range - the rule is applicable to any IP.</li>
     * </ul>
     */
    public static final String ANY = "*";

    /**
     * Security Group State document.
     */
    public static class SecurityGroupState extends ResourceState {
        public static final String FIELD_NAME_AUTH_CREDENTIAL_LINK = "authCredentialsLink";
        public static final String FIELD_NAME_ENDPOINT_LINK = "endpointLink";

        /**
         * Region identifier of this security group service instance.
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String regionId;

        /**
         * Link to secrets. Required
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String authCredentialsLink;

        /**
         * The pool which this resource is a part of.
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String resourcePoolLink;

        /**
         * The adapter to use to create the security group.
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public URI instanceAdapterReference;

        /**
         * incoming rules
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public List<Rule> ingress;

        /**
         * outgoing rules
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public List<Rule> egress;


        /**
         * Link to the cloud account endpoint the disk belongs to.
         */
        @Since(ReleaseConstants.RELEASE_VERSION_0_5_7)
        public String endpointLink;

        /**
         * Represents a security group rule.
         */
        public static class Rule {
            public String name;
            public String protocol;
            /**
             * IP range that rule will be applied to expressed in CIDR notation
             */
            public String ipRangeCidr;

            /**
             * port or port range for rule ie. "22", "80", "1-65535"
             * -1 means all the ports for the particular protocol
             */
            public String ports;

            /**
             * Gets or sets network traffic is allowed or denied.
             * Default is Allow.
             */
            public Access access = Access.Allow;

            public enum Access {
                Allow,
                Deny
            }
        }
    }

    public SecurityGroupService() {
        super(SecurityGroupState.class);
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
        SecurityGroupState returnState = processInput(put);
        setState(put, returnState);
        put.complete();
    }

    @Override
    public void handlePatch(Operation patch) {
        SecurityGroupState currentState = getState(patch);
        Function<Operation, Boolean> customPatchHandler = t -> {
            SecurityGroupState patchBody = patch.getBody(SecurityGroupState.class);
            boolean hasStateChanged = false;
            // rules are overwritten -- it's not a merge
            // will result in a new version of the service on every call
            if (patchBody.ingress != null) {
                currentState.ingress = patchBody.ingress;
                hasStateChanged = true;
            }

            if (patchBody.egress != null) {
                currentState.egress = patchBody.egress;
                hasStateChanged = true;
            }
            return hasStateChanged;
        };
        ResourceUtils
                .handlePatch(patch, currentState, getStateDescription(), SecurityGroupState.class,
                        customPatchHandler);
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument td = super.getDocumentTemplate();
        SecurityGroupState template = (SecurityGroupState) td;
        template.id = UUID.randomUUID().toString();
        template.name = "security-group-one";

        return template;
    }

    private SecurityGroupState processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        SecurityGroupState state = op.getBody(SecurityGroupState.class);
        validateState(state);
        return state;
    }

    private void validateState(SecurityGroupState state) {
        Utils.validateState(getStateDescription(), state);

        if (state.regionId.isEmpty()) {
            throw new IllegalArgumentException("regionId required");
        }

        if (state.authCredentialsLink.isEmpty()) {
            throw new IllegalArgumentException("authCredentialsLink required");
        }

        if (state.resourcePoolLink.isEmpty()) {
            throw new IllegalArgumentException("resourcePoolLink required");
        }

        validateRules(state.ingress);
        validateRules(state.egress);
    }

    /**
     * Ensure that the rules conform to standard security group practices.
     */
    private static void validateRules(List<Rule> rules) {
        for (Rule rule : rules) {
            validateRuleName(rule.name);
            // validate protocol and convert to lower case
            rule.protocol = validateProtocol(rule.protocol);

            // IP range must be in CIDR notation or "*".
            // creating new SubnetUtils to validate
            if (!ANY.equals(rule.ipRangeCidr)) {
                new SubnetUtils(rule.ipRangeCidr);
            }
            validatePorts(rule.ports);
        }
    }

    /*
     * validate ports
     */
    private static void validatePorts(String ports) {
        if (ports == null) {
            throw new IllegalArgumentException(
                    "an allow rule requires a minimum of one port, none supplied");
        }
        String[] pp = ports.split("-");
        if (pp.length > 2) {
            // invalid port range
            throw new IllegalArgumentException(
                    "invalid allow rule port range supplied");
        }
        int previousPort = 0;
        if (pp.length > 0) {
            for (String aPp : pp) {
                try {
                    int iPort = Integer.parseInt(aPp);
                    if (iPort < 0 || iPort > 65535) {
                        throw new IllegalArgumentException(
                                "allow rule port numbers must be between 0 and 65535");
                    }
                    if (previousPort > 0 && previousPort > iPort) {
                        throw new IllegalArgumentException(
                                "allow rule from port is greater than to port");
                    }
                    previousPort = iPort;
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            "allow rule port numbers must be between 0 and 65535");
                }
            }
        }
    }

    /*
     * Ensure rule name is populated
     */
    private static void validateRuleName(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("a rule name is required");
        }
    }

    /*
     * Protocol must be tcp, udp or icmpi
     */
    private static String validateProtocol(String protocol) {

        if (protocol == null || protocol.isEmpty()) {
            throw new IllegalArgumentException(
                    "only tcp, udp or icmp protocols are supported, none supplied");
        }

        String proto = protocol.toLowerCase();

        if (Protocol.fromString(proto) == null) {
            throw new IllegalArgumentException(
                    "only tcp, udp or icmp protocols are supported, provide a supported protocol");
        }
        return proto;
    }

}
