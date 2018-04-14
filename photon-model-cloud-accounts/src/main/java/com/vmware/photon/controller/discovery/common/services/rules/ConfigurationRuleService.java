/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.discovery.common.services.rules;

import static com.vmware.photon.controller.model.UriPaths.SERVICE_CONFIG_RULES;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.RequestRouter.Route;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;

/**
 * Rule Definition Repository.
 *
 * Rules are defined by a globally unique identifier and a value expressed
 * as a literal (e.g. true) or as an Odata expression (e.g. userPercentage
 * lt
 * 10). Non-literal values are evaluated at runtime against the evaluation
 * context.
 *
 * Rules typical use cases include but are not limited to Feature toggles.
 */
public class ConfigurationRuleService extends StatefulService {

    public static final String FACTORY_LINK = SERVICE_CONFIG_RULES;

    public static FactoryService createFactory() {
        FactoryService fs = new FactoryService(ConfigurationRuleState.class) {

            @Override
            public Service createServiceInstance() throws Throwable {
                return new ConfigurationRuleService();
            }

            @Override
            public void handlePost(Operation op) {
                if (op.hasPragmaDirective(Operation.PRAGMA_DIRECTIVE_SYNCH_OWNER)
                        || op.hasPragmaDirective(Operation.PRAGMA_DIRECTIVE_SYNCH_PEER)) {
                    // ignore special Xenon POSTs without any valid body.
                    op.complete();
                    return;
                }

                ConfigurationRuleState rule = null;
                try {
                    if (!op.hasBody()) {
                        throw new IllegalArgumentException(
                                "attempt to initialize service with an empty state");
                    }

                    rule = op.getBody(ConfigurationRuleState.class);
                    if (rule.id == null || rule.id.length() == 0) {
                        throw new IllegalArgumentException("id cannot be null or empty");
                    }

                    verifyState(rule);

                    // aims to have selfLink equals to "/mgmt/config/rules/{id}"
                    if (rule.documentSelfLink == null || rule.documentSelfLink.length() == 0) {
                        rule.documentSelfLink = rule.id;
                    } else if (!UriUtils.normalizeUriPath(rule.documentSelfLink)
                            .equals(UriUtils.normalizeUriPath(rule.id))) {
                        throw new IllegalArgumentException(
                                String.format("documentSelfLink should be null or equals to '%s'",
                                        rule.id));
                    }
                } catch (Exception e) {
                    op.fail(e);
                    return;
                }

                op.setBody(rule);
                op.complete();
            }

        };
        return fs;
    }

    public static class ConfigurationRuleState extends ServiceDocument {
        public static final String FIELD_NAME_ID = "id";

        /**
         * id of the rule
         */
        @Documentation(description = "Globally unique identifier.")
        public String id;
        /**
         * literal value or an Odata expression
         */
        @Documentation(description = "Value expressed as literal (e.g. true) or as an Odata expression (e.g. userEmail eq 'foo@bar.com').")
        public String value;

        @Documentation(description = "A set of tenantLinks with overridden values. When specified," +
                "users associated with the tenant links will be returned the specific tenantLink's value instead of the default")
        @UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.OPTIONAL)
        public List<TenantLinkSpec> tenantLinkOverrides;

    }

    /**
     * This class structures the format for overriding a feature flag for a specified tenantLink.
     */
    public static class TenantLinkSpec {

        /**
         * tenantLink for specific user, org
         */
        public String tenantLink;

        /**
         * the rule value
         */
        public String value;

        /**
         * Construct method
         * @param tenantLink
         * @param value
         */
        public TenantLinkSpec(String tenantLink, String value) {
            this.tenantLink = tenantLink;
            this.value = value;
        }

        public String getTenantLink() {
            return this.tenantLink;
        }

    }

    public ConfigurationRuleService() {
        super(ConfigurationRuleState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handlePut(Operation op) {
        ConfigurationRuleState update = null;
        try {
            if (!op.hasBody()) {
                throw new IllegalArgumentException(
                        "attempt to initialize service with an empty state");
            }

            ConfigurationRuleState current = getState(op);
            update = op.getBody(ConfigurationRuleState.class);

            if (!current.id.equals(update.id)) {
                throw new IllegalArgumentException("Rule id cannot be updated");
            }

            verifyState(update);

        } catch (Exception e) {
            op.fail(e);
            return;
        }
        setState(op, update);
        op.complete();
    }

    @Override
    public void handlePatch(Operation put) {
        Operation.failActionNotSupported(put);
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();
        // swagger doc
        template.documentDescription.serviceRequestRoutes = new HashMap<>();
        Route route1 = new Route();
        route1.action = Action.GET;
        route1.description = "Retrieves configuration rules (AKA Feature flags) definitions. "
                + "Meant to be used at configuration time (do not resolve non-literal rule values to literal).";
        route1.requestType = ConfigurationRuleState.class;
        template.documentDescription.serviceRequestRoutes
                .put(route1.action, Collections.singletonList(route1));

        Route route2 = new Route();
        route2.action = Action.POST;
        route2.description = "Create configuration rules (AKA Feature flags) definitions.";
        route2.requestType = ConfigurationRuleState.class;
        template.documentDescription.serviceRequestRoutes
                .put(route2.action, Collections.singletonList(route2));

        Route route3 = new Route();
        route3.action = Action.PUT;
        route3.description = "Update configuration rules (AKA Feature flags) definitions.";
        route3.requestType = ConfigurationRuleState.class;
        template.documentDescription.serviceRequestRoutes
                .put(route3.action, Collections.singletonList(route3));
        return template;
    }


    /**
     * This method is to check the value and tenantLinkOverrides in the ConfigurationRuleState
     * @param ruleState : the rule state needs to check
     * @throws Exception
     */
    private static void verifyState(ConfigurationRuleState ruleState) throws Exception {
        if (ruleState.value == null
                || ruleState.value.length() == 0) {
            throw new IllegalArgumentException("value cannot be null or empty");
        }

        if (ruleState.id.indexOf(":") != ruleState.id.lastIndexOf(":")) {
            throw new IllegalArgumentException("the rule id has multiple semicolon sign");
        }

        if (ruleState.tenantLinkOverrides != null
                && !ruleState.tenantLinkOverrides.isEmpty()) {
            Set<String> uniqueTenantLinkSpec = new HashSet<>();
            for (TenantLinkSpec tenantLinkSpec : ruleState.tenantLinkOverrides) {
                if (!uniqueTenantLinkSpec.add(tenantLinkSpec.tenantLink)) {
                    throw new IllegalArgumentException(
                            String.format("Duplicate tenantLink found: %s. Specified tenantLink overrides must be unique",
                                    tenantLinkSpec.tenantLink));
                }
            }
        }
    }
}
