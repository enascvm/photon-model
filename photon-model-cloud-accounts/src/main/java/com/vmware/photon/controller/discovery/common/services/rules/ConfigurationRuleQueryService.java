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
import static com.vmware.photon.controller.model.UriPaths.SERVICE_QUERY_CONFIG_RULES;
import static com.vmware.photon.controller.model.UriPaths.USER_CONTEXT_QUERY_SERVICE;
import static com.vmware.xenon.common.UriUtils.URI_PARAM_ODATA_EXPAND;
import static com.vmware.xenon.common.UriUtils.appendQueryParam;
import static com.vmware.xenon.common.UriUtils.buildUri;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.vmware.photon.controller.discovery.common.services.OrganizationService.OrganizationState;
import com.vmware.photon.controller.discovery.common.services.UserContextQueryService.UserContext;
import com.vmware.photon.controller.discovery.common.services.UserService.UserState;
import com.vmware.photon.controller.discovery.common.services.rules.ConfigurationRuleService.ConfigurationRuleState;
import com.vmware.photon.controller.discovery.common.services.rules.ConfigurationRuleService.TenantLinkSpec;
import com.vmware.photon.controller.discovery.csp.CspAuthenticationUtils;
import com.vmware.photon.controller.discovery.csp.CspTokenData;
import com.vmware.xenon.common.ODataQueryVisitor;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.RequestRouter.Route;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryFilter;
import com.vmware.xenon.services.common.QueryTask.Query;

/**
 * Return Rule Context for the current user.
 */
public class ConfigurationRuleQueryService extends StatelessService {

    public static final String SELF_LINK = SERVICE_QUERY_CONFIG_RULES;

    public static final Pattern ODATA_PATTERN = Pattern
            .compile("([a-z0-9\\/']+) (eq|ne|lt|gt|le|ge|any|all) ([a-z0-9\\/']+)",
                    Pattern.CASE_INSENSITIVE);

    public static final Pattern SERVICE_PATTERN = Pattern.compile("^.*:");

    /**
     * Evaluation context document used to evaluate rule values expressed in
     * the form of an odata expression.
     * This enable expression against computed values such as 'userPercentage'.
     * e.g. "userPercentage lt 10"
     */
    public static class EvalCtxDocument extends ServiceDocument {
        public static final String FIELD_NAME_USER_LINK = "userLink";
        public static final String FIELD_NAME_USER_PERCENTAGE = "userPercentage";
        public static final String FIELD_NAME_USER_EMAIL = "userEmail";
        public static final String FIELD_NAME_USER_GROUP_LINKS = "userGroupLinks";

        /**
         * Current user link.
         */
        public String userLink;

        /**
         * Current user email.
         */
        public String userEmail;

        /**
         * Deterministic and evenly distributed user percentage calculated from
         * the current userLink.
         */
        public int userPercentage;

        /**
         * Current user group links.
         */
        public Set<String> userGroupLinks;
    }

    @Override
    public void handleRequest(Operation op) {
        if (op.getAction() != Action.GET) {
            Operation.failActionNotSupported(op);
            return;
        }
        super.handleRequest(op);
    }

    @Override
    public void handleGet(Operation get) {
        Operation queryContextOp = Operation
                .createGet(getHost(), USER_CONTEXT_QUERY_SERVICE)
                .setCompletion((o, f) -> handleQueryUserContextCompletion(o, f, get));
        sendRequest(queryContextOp);
    }

    private void handleQueryUserContextCompletion(Operation op, Throwable failure,
            Operation original) {
        try {
            if (failure != null) {
                throw failure;
            }
            if (op.getStatusCode() != Operation.STATUS_CODE_OK) {
                throw new IllegalStateException("Invalid status code:" + op.getStatusCode());
            }
            UserContext userContext = op.getBody(UserContext.class);
            URI uri = appendQueryParam(
                    buildUri(getHost(), SERVICE_CONFIG_RULES),
                    URI_PARAM_ODATA_EXPAND, Boolean.TRUE.toString());

            Operation ruleOp = Operation
                    .createGet(uri)
                    .setCompletion(
                            (o, f) -> handleRuleLookupCompletion(o, f, original, userContext));

            setAuthorizationContext(ruleOp, getSystemAuthorizationContext());
            sendRequest(ruleOp);
        } catch (Throwable t) {
            logSevere(t);
            original.fail(t);
        }
    }

    private void handleRuleLookupCompletion(Operation o, Throwable f, Operation original,
            UserContext userContext) {
        try {
            if (f != null) {
                throw f;
            }
            ServiceDocumentQueryResult results = o.getBody(ServiceDocumentQueryResult.class);
            if (results.documents == null || results.documents.size() <= 0) {
                original.setBody(Operation.EMPTY_JSON_BODY);
                original.complete();
                return;
            }

            ConfigContext configContext = new ConfigContext();
            CspTokenData cspTokenData = CspAuthenticationUtils
                    .decodeCspJwtToken(original.getAuthorizationContext().getToken());

            EvalCtxDocument evalCtxDocument = buildEvalCxtDocument(userContext.user);
            ServiceDocumentDescription evalCtxDocumentDesc = ServiceDocumentDescription.Builder
                    .create().buildDescription(EvalCtxDocument.class);


            // Reconstruct the rules while overriding all service-specific feature flags
            // (of pattern "service:rule") to replace any global "rule" flags.
            // If a feature flag is in the context of a service that the user is not a part of,
            // filter it out.
            Map<String, ConfigurationRuleState> ruleStateMap = new HashMap<>();
            for (Object json : results.documents.values()) {
                ConfigurationRuleState rule = Utils.fromJson(json, ConfigurationRuleState.class);

                String prefix = String.format("%s:", cspTokenData.azp);
                Matcher matcher = SERVICE_PATTERN.matcher(rule.id);

                if (matcher.find()) {
                    if (matcher.group(0).equals(prefix)
                            && cspTokenData.azp != null) {
                        rule.id = rule.id.substring(prefix.length());
                        ruleStateMap.put(rule.id, rule);
                    }
                } else if (!ruleStateMap.containsKey(rule.id)) {
                    ruleStateMap.put(rule.id, rule);
                }
            }

            for (ConfigurationRuleState rule : ruleStateMap.values()) {

                // Check if rule.value has any override rules based on tenantLink and update
                // accordingly.
                String ruleValue = rule.value;

                // check if `tenantLinkOverrides` is set, and if so,
                // check if the user's selfLink or organization is related.
                // If related, override with the specified value.
                if (rule.tenantLinkOverrides != null && !rule.tenantLinkOverrides.isEmpty()) {
                    Map<String, TenantLinkSpec> tenantLinkSpecMap = rule.tenantLinkOverrides
                            .stream()
                            .collect(Collectors
                                    .toMap(TenantLinkSpec::getTenantLink, Function.identity()));

                    // check if user's self link is set first. If not,
                    // check if the user's organization is currently set.
                    if (tenantLinkSpecMap.containsKey(userContext.user.documentSelfLink)) {
                        ruleValue = tenantLinkSpecMap.get(userContext.user.documentSelfLink).value;
                    } else {
                        for (OrganizationState organizationState : userContext.organizations) {
                            if (tenantLinkSpecMap.containsKey(organizationState.documentSelfLink)) {
                                ruleValue = tenantLinkSpecMap
                                        .get(organizationState.documentSelfLink).value;
                                break;
                            }
                        }
                    }
                }

                // check if the rule.value is OdataExpression or not.
                if (isOdataExpression(ruleValue)) {
                    Query query = new ODataQueryVisitor().toQuery(ruleValue);
                    QueryFilter queryFilter = QueryFilter.create(query);
                    boolean value = queryFilter.evaluate(evalCtxDocument, evalCtxDocumentDesc);
                    configContext.rules.put(rule.id, value);
                } else {
                    if (isBoolean(ruleValue)) {
                        configContext.rules.put(rule.id, Boolean.valueOf(ruleValue));
                    } else {
                        configContext.rules.put(rule.id, ruleValue);
                    }
                }
            }
            original.setBody(configContext);
            original.complete();
        } catch (Throwable t) {
            logSevere(t);
            original.fail(t);
        }

    }

    private boolean isBoolean(String value) {
        return value != null && Arrays.stream(new String[]{"true", "false"})
                .anyMatch(b -> b.equalsIgnoreCase(value));
    }

    private boolean isOdataExpression(String value) {
        if (value == null || value.length() <= 0) {
            return false;
        }
        // this is a bit naive but should be good enough.
        Matcher m = ODATA_PATTERN.matcher(value);
        return m.find();
    }

    private EvalCtxDocument buildEvalCxtDocument(UserState userState) {
        EvalCtxDocument ctx = new EvalCtxDocument();
        ctx.userLink = userState.documentSelfLink;
        ctx.userEmail = userState.email;
        ctx.userPercentage = Math.abs(ctx.userLink.hashCode() % 100);
        ctx.userGroupLinks = userState.userGroupLinks;
        return ctx;
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();
        // swagger doc
        template.documentDescription.serviceRequestRoutes = new HashMap<>();
        Route route = new Route();
        route.action = Action.GET;
        route.description =
                "Retrieves configuration rule (AKA Feature flag) values for the current user. " +
                "Resolve non-literal rule values to boolean values based on the current user " +
                "context (e.g. userEmail eq 'foo@bar.com' => true). If tenantLinkOverrides is " +
                "set, the resultant value of that overridden feature flag value will be set. " +
                "Similarly, a service-specific feature flag (such as 'discovery:featureFlag') " +
                "will override the global value when in the context of that service.";

        route.requestType = ConfigurationRuleState.class;
        template.documentDescription.serviceRequestRoutes
                .put(route.action, Collections.singletonList(route));
        return template;
    }

}
