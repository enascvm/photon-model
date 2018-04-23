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

import static java.lang.String.format;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import static com.vmware.photon.controller.discovery.common.services.rules.ConfigurationRuleQueryService.EvalCtxDocument.FIELD_NAME_USER_EMAIL;
import static com.vmware.photon.controller.discovery.common.services.rules.ConfigurationRuleQueryService.EvalCtxDocument.FIELD_NAME_USER_GROUP_LINKS;
import static com.vmware.photon.controller.discovery.common.services.rules.ConfigurationRuleQueryService.EvalCtxDocument.FIELD_NAME_USER_PERCENTAGE;
import static com.vmware.photon.controller.model.UriPaths.SERVICE_CONFIG_RULES;
import static com.vmware.photon.controller.model.UriPaths.SERVICE_QUERY_CONFIG_RULES;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.vmware.photon.controller.discovery.common.PhotonControllerCloudAccountUtils;
import com.vmware.photon.controller.discovery.common.services.OrganizationService;
import com.vmware.photon.controller.discovery.common.services.UserContextQueryService;
import com.vmware.photon.controller.discovery.common.services.UserService;
import com.vmware.photon.controller.discovery.common.services.rules.ConfigurationRuleService.ConfigurationRuleState;
import com.vmware.photon.controller.discovery.common.services.rules.ConfigurationRuleService.TenantLinkSpec;
import com.vmware.photon.controller.discovery.onboarding.OnBoardingTestUtils;
import com.vmware.photon.controller.discovery.onboarding.user.UserCreationService.UserCreationRequest;
import com.vmware.xenon.common.BasicTestCase;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.AuthorizationHelper;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;
import com.vmware.xenon.services.common.UserService.UserState;

public class ConfigurationRuleQueryServiceTest extends BasicTestCase {

    private static final int NUM_NODES = 3;

    private static final String USER_ID = "foo@bar.com";
    private static final String USER_LINK = "/core/authz/users/foo@bar.com";

    private static final String RULE_LITERAL = "literalRule";
    private static final String RULE_LITERAL_VAL = "www.vmware.com";

    private static final String RULE_PERCENT_TRUE_EXP = "userPercentageTrueExp";
    private static final String RULE_PERCENT_TRUE_EXP_VAL = format("%s lt 75",
            FIELD_NAME_USER_PERCENTAGE);

    private static final String RULE_PERCENT_FALSE_EXP = "userPercentageFalseExp";
    private static final String RULE_PERCENT_FALSE_EXP_VAL = format("%s ge 75",
            FIELD_NAME_USER_PERCENTAGE);

    private static final String RULE_USER_FALSE_EXP = "userEmailFalse";
    private static final String RULE_USER_FALSE_EXP_VAL = format(
            "((%s eq 'bar@gmail.com') or %s eq 'foor@gmail.com')",
            FIELD_NAME_USER_EMAIL, FIELD_NAME_USER_EMAIL);

    private static final String RULE_USER_TRUE_EXP = "userEmailTrue";
    private static final String RULE_USER_TRUE_EXP_VAL = format(
            "((%s eq 'bar@gmail.com') or %s eq '%s')",
            FIELD_NAME_USER_EMAIL, FIELD_NAME_USER_EMAIL, USER_ID);

    private static final String RULE_USER_GROUP_FALSE_EXP = "userGroupFalse";
    private static final String RULE_USER_GROUP_FALSE_EXP_VAL = format(
            "%s.item any 'do-not-exist')",
            FIELD_NAME_USER_GROUP_LINKS);

    private static final String RULE_USER_GROUP_TRUE_EXP = "userGroupTrue";
    private static final String RULE_USER_GROUP_TRUE_EXP_VAL = format(
            "%s.item any '/core/authz/user-groups/guest-user-group')",
            FIELD_NAME_USER_GROUP_LINKS);

    private static final Boolean STR_TRUE = Boolean.TRUE;
    private static final Boolean STR_FALSE = Boolean.FALSE;
    private static final String STR_DEFAULT = "DEFAULT_RULE_VALUE";
    private static final String DISCOVERY_SERVICE_DEFAULT_VALUE = "DISCOVERY_DEFAULT_SERVICE_RULE_VALUE";
    private static final String FOO_SERVICE_DEFAULT_VALUE = "FOO_DEFAULT_SERVICE_RULE_VALUE";

    private final String USER1_EMAIL = "user01@test.com";
    private final String USER1_PSW = "psw1";
    private final String USER1_ORG = "orgA";

    private final String USER2_EMAIL = "user02@test.com";
    private final String USER2_PSW = "psw2";
    private final String USER2_ORG = "orgB";

    private final String USER3_EMAIL = "user03@test.com";
    private final String USER3_PSW = "psw3";

    private final String USER4_EMAIL = "user04@test.com";
    private final String USER4_ORG = "orgC";
    private final String USER4_PSW = "psw4";

    private final String CSP_HEADER_ENABLED = "CSP_HEADER_ENABLED";
    private final String TELEMETRY_BACKEND = "TELEMETRY_BACKEND";

    private final String DISCOVERY_SERVICE_TAG = "discovery:";
    private final String FOO_SERVICE_TAG = "foo:";

    @Before
    public void before() throws Throwable {
        CommandLineArgumentParser.parseFromProperties(this);
        this.host.setUpPeerHosts(NUM_NODES);
        this.host.joinNodesAndVerifyConvergence(NUM_NODES, NUM_NODES, true);
        this.host.setNodeGroupQuorum(NUM_NODES);

        for (VerificationHost h : this.host.getInProcessHostMap().values()) {
            OnBoardingTestUtils.startCommonServices(h);
        }

        this.host.setSystemAuthorizationContext();
        for (VerificationHost h : this.host.getInProcessHostMap().values()) {
            h.startService(Operation.createPost(h,
                    ConfigurationRuleService.FACTORY_LINK),
                    ConfigurationRuleService.createFactory());
            h.startService(new ConfigurationRuleQueryService());
            h.addPrivilegedService(ConfigurationRuleQueryService.class);
            h.addPrivilegedService(UserContextQueryService.class);
            h.startService(new UserContextQueryService());

            h.waitForServiceAvailable(ConfigurationRuleQueryService.SELF_LINK);
            h.waitForServiceAvailable(UserContextQueryService.SELF_LINK);
        }

        this.host.waitForReplicatedFactoryServiceAvailable(
                UriUtils.buildUri(this.host.getPeerHost(), ConfigurationRuleService.FACTORY_LINK));

        this.host.resetSystemAuthorizationContext();

        setUpFeatureFlagTest();

        assertEquals(USER_LINK, createUser(USER_ID));
    }

    @After
    public void tearDown() throws InterruptedException {
        if (this.host == null) {
            return;
        }
        this.host.tearDownInProcessPeers();
        this.host.toggleNegativeTestMode(false);
        this.host.tearDown();
    }

    @Override
    public void beforeHostStart(VerificationHost h) {
        h.setAuthorizationEnabled(true);
    }

    @Test
    public void testGetWhenEmpty() throws Throwable {
        this.host.assumeIdentity(USER_LINK);

        Operation response = this.host
                .waitForResponse(Operation.createGet(getUri()));

        assertNotNull(response);
        // if 200 must return an empty json
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        assertEquals(Operation.EMPTY_JSON_BODY, response.getBodyRaw());
    }

    @Test
    public void testGet() throws Throwable {
        createRules();
        this.host.assumeIdentity(USER_LINK);

        Operation response = this.host
                .waitForResponse(Operation.createGet(getUri()));

        assertNotNull(response);
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        ConfigContext configCtx = response.getBody(ConfigContext.class);
        assertNotNull(configCtx);
        assertEquals(7, configCtx.rules.size());
        assertEquals(RULE_LITERAL_VAL, configCtx.rules.get(RULE_LITERAL));
        assertEquals(STR_TRUE,
                configCtx.rules.get(RULE_PERCENT_TRUE_EXP));
        assertEquals(STR_FALSE,
                configCtx.rules.get(RULE_PERCENT_FALSE_EXP));
        assertEquals(STR_FALSE,
                configCtx.rules.get(RULE_USER_FALSE_EXP));
        assertEquals(STR_TRUE,
                configCtx.rules.get(RULE_USER_TRUE_EXP));
        assertEquals(STR_FALSE,
                configCtx.rules.get(RULE_USER_GROUP_FALSE_EXP));
        assertEquals(STR_TRUE,
                configCtx.rules.get(RULE_USER_GROUP_TRUE_EXP));
    }

    @Test
    public void testGlobalScope() throws Throwable {
        this.host.setSystemAuthorizationContext();
        createRule(this.CSP_HEADER_ENABLED, STR_TRUE.toString(), null);
        this.host.resetAuthorizationContext();
        checkUserRule(this.USER1_EMAIL, this.CSP_HEADER_ENABLED, STR_TRUE.toString());
        checkUserRule(this.USER2_EMAIL, this.CSP_HEADER_ENABLED, STR_TRUE.toString());
        checkUserRule(this.USER3_EMAIL, this.CSP_HEADER_ENABLED, STR_TRUE.toString());

        this.host.setSystemAuthorizationContext();
        createRule(this.TELEMETRY_BACKEND, STR_FALSE.toString(), new ArrayList<>());
        this.host.resetAuthorizationContext();
        checkUserRule(this.USER1_EMAIL, this.CSP_HEADER_ENABLED, STR_TRUE.toString());
        checkUserRule(this.USER2_EMAIL, this.CSP_HEADER_ENABLED, STR_TRUE.toString());
        checkUserRule(this.USER3_EMAIL, this.CSP_HEADER_ENABLED, STR_TRUE.toString());
        checkUserRule(this.USER1_EMAIL, this.TELEMETRY_BACKEND, STR_FALSE.toString());
        checkUserRule(this.USER2_EMAIL, this.TELEMETRY_BACKEND, STR_FALSE.toString());
        checkUserRule(this.USER3_EMAIL, this.TELEMETRY_BACKEND, STR_FALSE.toString());
    }

    @Test
    public void testUserScope() throws Throwable {
        // set rules to include only user1
        List<TenantLinkSpec> tenantLinks = new ArrayList<>();
        tenantLinks.add(new TenantLinkSpec(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(this.USER1_EMAIL)), STR_FALSE.toString()));
        this.host.setSystemAuthorizationContext();
        createRule(this.CSP_HEADER_ENABLED, STR_DEFAULT, tenantLinks);
        this.host.resetAuthorizationContext();
        checkUserRule(this.USER1_EMAIL, this.CSP_HEADER_ENABLED, STR_FALSE.toString());
        checkUserRule(this.USER2_EMAIL, this.CSP_HEADER_ENABLED, STR_DEFAULT);
        checkUserRule(this.USER3_EMAIL, this.CSP_HEADER_ENABLED, STR_DEFAULT);

        // set rules to include user1 and user2
        tenantLinks.add(new TenantLinkSpec(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(this.USER2_EMAIL)), STR_TRUE.toString()));
        this.host.setSystemAuthorizationContext();
        updateRule(this.CSP_HEADER_ENABLED, STR_DEFAULT, tenantLinks);
        this.host.resetAuthorizationContext();
        checkUserRule(this.USER1_EMAIL, this.CSP_HEADER_ENABLED, STR_FALSE.toString());
        checkUserRule(this.USER2_EMAIL, this.CSP_HEADER_ENABLED, STR_TRUE.toString());
        checkUserRule(this.USER3_EMAIL, this.CSP_HEADER_ENABLED, STR_DEFAULT);

        // set rules to include user4 only
        tenantLinks = new ArrayList<>();
        tenantLinks.add(new TenantLinkSpec(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(this.USER4_EMAIL)), STR_FALSE.toString()));
        this.host.setSystemAuthorizationContext();
        updateRule(this.CSP_HEADER_ENABLED, STR_DEFAULT, tenantLinks);
        this.host.resetAuthorizationContext();
        checkUserRule(this.USER1_EMAIL, this.CSP_HEADER_ENABLED, STR_DEFAULT);
        checkUserRule(this.USER2_EMAIL, this.CSP_HEADER_ENABLED, STR_DEFAULT);
        checkUserRule(this.USER3_EMAIL, this.CSP_HEADER_ENABLED, STR_DEFAULT);
        checkUserRule(this.USER4_EMAIL, this.CSP_HEADER_ENABLED, STR_FALSE.toString());
    }

    @Test
    public void testOrgScope() throws Throwable {
        // set rules to include orgA only
        List<TenantLinkSpec> tenantLinks = new ArrayList<>();
        tenantLinks.add(new TenantLinkSpec(UriUtils.buildUriPath(OrganizationService.FACTORY_LINK,
                Utils.computeHash(this.USER1_ORG)), STR_FALSE.toString()));
        this.host.setSystemAuthorizationContext();
        createRule(this.CSP_HEADER_ENABLED, STR_DEFAULT, tenantLinks);
        this.host.resetAuthorizationContext();
        checkUserRule(this.USER1_EMAIL, this.CSP_HEADER_ENABLED, STR_FALSE.toString());
        checkUserRule(this.USER2_EMAIL, this.CSP_HEADER_ENABLED, STR_DEFAULT);
        checkUserRule(this.USER3_EMAIL, this.CSP_HEADER_ENABLED, STR_FALSE.toString());

        // set rules to include orgA and orgB
        tenantLinks.add(new TenantLinkSpec(UriUtils.buildUriPath(OrganizationService.FACTORY_LINK,
                Utils.computeHash(this.USER2_ORG)), STR_TRUE.toString()));
        this.host.setSystemAuthorizationContext();
        updateRule(this.CSP_HEADER_ENABLED, STR_DEFAULT, tenantLinks);
        this.host.resetAuthorizationContext();
        checkUserRule(this.USER1_EMAIL, this.CSP_HEADER_ENABLED, STR_FALSE.toString());
        checkUserRule(this.USER2_EMAIL, this.CSP_HEADER_ENABLED, STR_TRUE.toString());
        checkUserRule(this.USER3_EMAIL, this.CSP_HEADER_ENABLED, STR_FALSE.toString());
        checkUserRule(this.USER4_EMAIL, this.CSP_HEADER_ENABLED, STR_DEFAULT);

        // set rules to include orgC only
        tenantLinks = new ArrayList<>();
        tenantLinks.add(new TenantLinkSpec(UriUtils.buildUriPath(OrganizationService.FACTORY_LINK,
                Utils.computeHash(this.USER4_ORG)), STR_FALSE.toString()));
        this.host.setSystemAuthorizationContext();
        updateRule(this.CSP_HEADER_ENABLED, STR_DEFAULT, tenantLinks);
        this.host.resetAuthorizationContext();
        checkUserRule(this.USER1_EMAIL, this.CSP_HEADER_ENABLED, STR_DEFAULT);
        checkUserRule(this.USER2_EMAIL, this.CSP_HEADER_ENABLED, STR_DEFAULT);
        checkUserRule(this.USER3_EMAIL, this.CSP_HEADER_ENABLED, STR_DEFAULT);
        checkUserRule(this.USER4_EMAIL, this.CSP_HEADER_ENABLED, STR_FALSE.toString());
    }

    @Test
    public void testMixScope() throws Throwable {
        // set rules to include user2 and orgA
        List<TenantLinkSpec> tenantLinks = new ArrayList<>();
        tenantLinks.add(new TenantLinkSpec(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(this.USER2_EMAIL)), STR_FALSE.toString()));
        tenantLinks.add(new TenantLinkSpec(UriUtils.buildUriPath(OrganizationService.FACTORY_LINK,
                Utils.computeHash(this.USER1_ORG)), STR_TRUE.toString()));
        this.host.setSystemAuthorizationContext();
        createRule(this.CSP_HEADER_ENABLED, STR_DEFAULT, tenantLinks);
        this.host.resetAuthorizationContext();
        checkUserRule(this.USER1_EMAIL, this.CSP_HEADER_ENABLED, STR_TRUE.toString());
        checkUserRule(this.USER2_EMAIL, this.CSP_HEADER_ENABLED, STR_FALSE.toString());
        checkUserRule(this.USER3_EMAIL, this.CSP_HEADER_ENABLED, STR_TRUE.toString());
        checkUserRule(this.USER4_EMAIL, this.CSP_HEADER_ENABLED, STR_DEFAULT);

        // set CSP_HEADER_ENABLED to include orgA and TELEMETRY_BACKEND to global
        tenantLinks = new ArrayList<>();
        tenantLinks.add(new TenantLinkSpec(UriUtils.buildUriPath(OrganizationService.FACTORY_LINK,
                Utils.computeHash(this.USER1_ORG)), STR_FALSE.toString()));

        this.host.setSystemAuthorizationContext();
        updateRule(this.CSP_HEADER_ENABLED, STR_DEFAULT, tenantLinks);
        createRule(this.TELEMETRY_BACKEND, STR_DEFAULT);
        this.host.resetAuthorizationContext();
        checkUserRule(this.USER1_EMAIL, this.CSP_HEADER_ENABLED, STR_FALSE.toString());
        checkUserRule(this.USER1_EMAIL, this.TELEMETRY_BACKEND, STR_DEFAULT);
        checkUserRule(this.USER2_EMAIL, this.CSP_HEADER_ENABLED, STR_DEFAULT);
        checkUserRule(this.USER2_EMAIL, this.TELEMETRY_BACKEND, STR_DEFAULT);
        checkUserRule(this.USER3_EMAIL, this.CSP_HEADER_ENABLED, STR_FALSE.toString());
        checkUserRule(this.USER3_EMAIL, this.TELEMETRY_BACKEND, STR_DEFAULT);
    }

    @Test
    public void testPriorityLevel() throws Throwable {
        // config tenantLinkOverrides to set global to default, user2 to false, orgB to false
        List<TenantLinkSpec> tenantLinks = new ArrayList<>();
        tenantLinks.add(new TenantLinkSpec(UriUtils.buildUriPath(OrganizationService.FACTORY_LINK,
                Utils.computeHash(this.USER2_ORG)), STR_FALSE.toString()));
        tenantLinks.add(new TenantLinkSpec(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(this.USER2_EMAIL)), STR_FALSE.toString()));
        this.host.setSystemAuthorizationContext();
        createRule(this.CSP_HEADER_ENABLED, STR_DEFAULT, tenantLinks);
        this.host.resetAuthorizationContext();
        checkUserRule(this.USER2_EMAIL, this.CSP_HEADER_ENABLED, STR_FALSE.toString());

        // config tenantLinkOverrides to set global to default, orgB to false
        tenantLinks = new ArrayList<>();
        tenantLinks.add(new TenantLinkSpec(UriUtils.buildUriPath(OrganizationService.FACTORY_LINK,
                Utils.computeHash(this.USER2_ORG)), STR_FALSE.toString()));
        this.host.setSystemAuthorizationContext();
        updateRule(this.CSP_HEADER_ENABLED, STR_DEFAULT, tenantLinks);
        this.host.resetAuthorizationContext();
        checkUserRule(this.USER2_EMAIL, this.CSP_HEADER_ENABLED, STR_FALSE.toString());
    }

    @Test
    public void testRemoveOverridesWithEmptyList() throws Throwable {
        List<TenantLinkSpec> tenantLinks = new ArrayList<>();
        tenantLinks.add(new TenantLinkSpec(UriUtils.buildUriPath(OrganizationService.FACTORY_LINK,
                Utils.computeHash(this.USER1_ORG)), STR_FALSE.toString()));
        tenantLinks.add(new TenantLinkSpec(UriUtils.buildUriPath(OrganizationService.FACTORY_LINK,
                Utils.computeHash(this.USER2_ORG)), STR_FALSE.toString()));
        this.host.setSystemAuthorizationContext();
        createRule(this.CSP_HEADER_ENABLED, STR_DEFAULT, tenantLinks);
        this.host.resetAuthorizationContext();
        checkUserRule(this.USER1_EMAIL, this.CSP_HEADER_ENABLED, STR_FALSE.toString());
        checkUserRule(this.USER2_EMAIL, this.CSP_HEADER_ENABLED, STR_FALSE.toString());
        checkUserRule(this.USER3_EMAIL, this.CSP_HEADER_ENABLED, STR_FALSE.toString());
        checkUserRule(this.USER4_EMAIL, this.CSP_HEADER_ENABLED, STR_DEFAULT);

        // update the rule with empty list
        this.host.setSystemAuthorizationContext();
        updateRule(this.CSP_HEADER_ENABLED, STR_TRUE.toString(), new ArrayList<>());
        this.host.resetAuthorizationContext();
        checkUserRule(this.USER1_EMAIL, this.CSP_HEADER_ENABLED, STR_TRUE.toString());
        checkUserRule(this.USER2_EMAIL, this.CSP_HEADER_ENABLED, STR_TRUE.toString());
        checkUserRule(this.USER3_EMAIL, this.CSP_HEADER_ENABLED, STR_TRUE.toString());
        checkUserRule(this.USER4_EMAIL, this.CSP_HEADER_ENABLED, STR_TRUE.toString());
    }

    @Test
    public void testRemoveOverridesWithNull() throws Throwable {
        List<TenantLinkSpec> tenantLinks = new ArrayList<>();
        tenantLinks.add(new TenantLinkSpec(UriUtils.buildUriPath(OrganizationService.FACTORY_LINK,
                Utils.computeHash(this.USER1_ORG)), STR_FALSE.toString()));
        tenantLinks.add(new TenantLinkSpec(UriUtils.buildUriPath(OrganizationService.FACTORY_LINK,
                Utils.computeHash(this.USER2_ORG)), STR_FALSE.toString()));
        this.host.setSystemAuthorizationContext();
        createRule(this.CSP_HEADER_ENABLED, STR_DEFAULT, tenantLinks);
        this.host.resetAuthorizationContext();
        checkUserRule(this.USER1_EMAIL, this.CSP_HEADER_ENABLED, STR_FALSE.toString());
        checkUserRule(this.USER2_EMAIL, this.CSP_HEADER_ENABLED, STR_FALSE.toString());
        checkUserRule(this.USER3_EMAIL, this.CSP_HEADER_ENABLED, STR_FALSE.toString());
        checkUserRule(this.USER4_EMAIL, this.CSP_HEADER_ENABLED, STR_DEFAULT);

        // update the rule with null
        this.host.setSystemAuthorizationContext();
        updateRule(this.CSP_HEADER_ENABLED, STR_TRUE.toString(), null);
        this.host.resetAuthorizationContext();
        checkUserRule(this.USER1_EMAIL, this.CSP_HEADER_ENABLED, STR_TRUE.toString());
        checkUserRule(this.USER2_EMAIL, this.CSP_HEADER_ENABLED, STR_TRUE.toString());
        checkUserRule(this.USER3_EMAIL, this.CSP_HEADER_ENABLED, STR_TRUE.toString());
        checkUserRule(this.USER4_EMAIL, this.CSP_HEADER_ENABLED, STR_TRUE.toString());
    }

    // This is currently blocked by an inability to pass the `azp` claim to the authorization
    // context. See https://jira-hzn.eng.vmware.com/browse/VSYM-9791
    @Ignore
    @Test
    public void testOverridesInServiceLevel() throws Throwable {
        // create CSP_HEADER_ENABLED with global scope
        this.host.setSystemAuthorizationContext();
        createRule(this.CSP_HEADER_ENABLED, STR_DEFAULT, null);
        this.host.resetAuthorizationContext();
        checkUserRule(this.USER1_EMAIL, this.CSP_HEADER_ENABLED, STR_DEFAULT);
        checkUserRule(this.USER2_EMAIL, this.CSP_HEADER_ENABLED, STR_DEFAULT);
        checkUserRule(this.USER3_EMAIL, this.CSP_HEADER_ENABLED, STR_DEFAULT);

        // add foo service level feature flag w/ CSP_HEADER_ENABLED
        this.host.setSystemAuthorizationContext();
        createRule(this.FOO_SERVICE_TAG + this.CSP_HEADER_ENABLED, FOO_SERVICE_DEFAULT_VALUE, null);
        this.host.resetAuthorizationContext();
        checkUserRule(this.USER2_EMAIL, this.CSP_HEADER_ENABLED, STR_DEFAULT);
        checkUserRule(this.USER3_EMAIL, this.CSP_HEADER_ENABLED, STR_DEFAULT);
        checkUserRule(this.USER1_EMAIL, this.CSP_HEADER_ENABLED, STR_DEFAULT);

        // add discovery service level feature flag w/ CSP_HEADER_ENABLED and with org scope
        List<TenantLinkSpec> tenantLinks = new ArrayList<>();
        tenantLinks.add(new TenantLinkSpec(UriUtils
                .buildUriPath(OrganizationService.FACTORY_LINK, Utils.computeHash(this.USER1_ORG)),
                STR_FALSE.toString()));
        this.host.setSystemAuthorizationContext();
        createRule(this.DISCOVERY_SERVICE_TAG + this.CSP_HEADER_ENABLED, DISCOVERY_SERVICE_DEFAULT_VALUE, tenantLinks);
        this.host.resetAuthorizationContext();

        checkUserRule(this.USER1_EMAIL, this.CSP_HEADER_ENABLED, DISCOVERY_SERVICE_DEFAULT_VALUE);
        checkUserRule(this.USER2_EMAIL, this.CSP_HEADER_ENABLED, STR_DEFAULT);
        checkUserRule(this.USER3_EMAIL, this.CSP_HEADER_ENABLED, DISCOVERY_SERVICE_DEFAULT_VALUE);
    }

    @Test
    public void testMatchPattern() throws Throwable {
        Pattern pattern = ConfigurationRuleQueryService.SERVICE_PATTERN;

        // check edge case: [:rule]
        String ruleId = ":rule";
        Assert.assertEquals(pattern.matcher(ruleId).find(), true);

        // check service case: [discovery:rule]
        ruleId = "discovery:rule";
        Assert.assertEquals(pattern.matcher(ruleId).find(), true);

        // check wrong format: [discovery.rule]
        ruleId = "discovery.rule";
        Assert.assertEquals(pattern.matcher(ruleId).find(), false);

        // check normal case: [rule]
        ruleId = "rule";
        Assert.assertEquals(pattern.matcher(ruleId).find(), false);
    }

    private void createRules() {
        this.host.setSystemAuthorizationContext();
        createRule(RULE_LITERAL, RULE_LITERAL_VAL);
        createRule(RULE_PERCENT_TRUE_EXP, RULE_PERCENT_TRUE_EXP_VAL);
        createRule(RULE_PERCENT_FALSE_EXP, RULE_PERCENT_FALSE_EXP_VAL);
        createRule(RULE_USER_FALSE_EXP, RULE_USER_FALSE_EXP_VAL);
        createRule(RULE_USER_TRUE_EXP, RULE_USER_TRUE_EXP_VAL);
        createRule(RULE_USER_GROUP_FALSE_EXP, RULE_USER_GROUP_FALSE_EXP_VAL);
        createRule(RULE_USER_GROUP_TRUE_EXP, RULE_USER_GROUP_TRUE_EXP_VAL);
        this.host.resetAuthorizationContext();
    }

    private ConfigurationRuleState createRule(String id, String value) {
        ConfigurationRuleState rule = new ConfigurationRuleState();
        rule.id = id;
        rule.value = value;

        URI uri = UriUtils.buildUri(this.host.getPeerHost(), SERVICE_CONFIG_RULES);
        Operation response = this.host.waitForResponse(Operation.createPost(uri).setBody(rule));
        assertNotNull(response);
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        return response.getBody(ConfigurationRuleState.class);
    }

    private URI getUri() {
        return UriUtils
                .buildUri(this.host.getPeerHost(), SERVICE_QUERY_CONFIG_RULES);
    }

    private String createUser(String userId) throws Throwable {
        this.host.setSystemAuthorizationContext();

        AuthorizationHelper authHelper = new AuthorizationHelper(this.host);
        String userLink = authHelper.createUserService(this.host.getPeerHost(), userId);

        // Create user group for guest user
        String userGroupLink = authHelper.createUserGroup(this.host.getPeerHost(),
                "guest-user-group",
                Builder.create()
                        .addFieldClause(
                                ServiceDocument.FIELD_NAME_SELF_LINK,
                                userLink)
                        .build());
        // Create resource group for example service state
        String exampleServiceResourceGroupLink = authHelper.createResourceGroup(
                this.host.getPeerHost(),
                "guest-resource-group", Builder.create()
                        .addFieldClause(
                                ServiceDocument.FIELD_NAME_SELF_LINK,
                                SERVICE_QUERY_CONFIG_RULES)
                        .build());

        // Create roles tying these together
        authHelper.createRole(this.host.getPeerHost(), userGroupLink,
                exampleServiceResourceGroupLink,
                new HashSet<>(Arrays.asList(Action.GET)));

        // Tag the user as a member of the group
        UserState user = new UserState();
        user.userGroupLinks = new HashSet<>();
        user.userGroupLinks.add(userGroupLink);

        authHelper.patchUserService(this.host.getPeerHost(), userLink, user);

        this.host.resetAuthorizationContext();
        return userLink;
    }

    /**
     * This method is to set up the basic prerequisite for feature flag test.
     * @throws Throwable
     */
    private void setUpFeatureFlagTest() throws Throwable {
        URI peerUri = this.host.getPeerHostUri();
        // setup user1 and associate with orgA
        UserCreationRequest userData01 = OnBoardingTestUtils
                .createUserData(this.USER1_EMAIL, this.USER1_PSW);
        OnBoardingTestUtils.setupUser(this.host, peerUri, userData01);
        this.host.assumeIdentity(UriUtils
                .buildUriPath(UserService.FACTORY_LINK, PhotonControllerCloudAccountUtils.computeHashWithSHA256(this.USER1_EMAIL)), null);
        OnBoardingTestUtils.setupOrganization(this.host, peerUri, this.USER1_ORG, this.USER1_EMAIL, this.USER1_PSW);

        // setup user2 and associate with orgB
        UserCreationRequest userData02 = OnBoardingTestUtils
                .createUserData(this.USER2_EMAIL, this.USER2_PSW);
        OnBoardingTestUtils.setupUser(this.host, peerUri, userData02);
        this.host.assumeIdentity(UriUtils
                .buildUriPath(UserService.FACTORY_LINK, PhotonControllerCloudAccountUtils.computeHashWithSHA256(this.USER2_EMAIL)), null);
        OnBoardingTestUtils.setupOrganization(this.host, peerUri, this.USER2_ORG, this.USER2_EMAIL, this.USER2_PSW);

        // setup user3 and add the user to orgA
        UserCreationRequest userData03 = OnBoardingTestUtils
                .createUserData(this.USER3_EMAIL, this.USER3_PSW);
        OnBoardingTestUtils.setupUser(this.host, peerUri, userData03);
        this.host.assumeIdentity(UriUtils
                .buildUriPath(UserService.FACTORY_LINK, PhotonControllerCloudAccountUtils.computeHashWithSHA256(this.USER1_EMAIL)), null);
        String orgLink = UriUtils.buildUriPath(OrganizationService.FACTORY_LINK,
                Utils.computeHash(this.USER1_ORG));
        OnBoardingTestUtils
                .addUserToOrgOrProject(this.host, peerUri, orgLink, this.USER3_EMAIL, this.USER3_EMAIL, this.USER3_PSW);

        // setup user4 and associate with orgC
        UserCreationRequest userData04 = OnBoardingTestUtils
                .createUserData(this.USER4_EMAIL, this.USER4_PSW);
        OnBoardingTestUtils.setupUser(this.host, peerUri, userData04);
        this.host.assumeIdentity(UriUtils
                .buildUriPath(UserService.FACTORY_LINK, PhotonControllerCloudAccountUtils.computeHashWithSHA256(this.USER4_EMAIL)), null);
        OnBoardingTestUtils.setupOrganization(this.host, peerUri, this.USER4_ORG, this.USER4_EMAIL, this.USER4_PSW);
    }

    /**
     * This method assumes user has the rule and check if the rule value is right or not.
     * @param userEmail : user's email
     * @param ruleName : the rule id
     * @param ruleValue : the rule value
     * @throws Throwable
     */
    private void checkUserRule(String userEmail, String ruleName, String ruleValue) throws Throwable {
        this.host.assumeIdentity(UriUtils
                .buildUriPath(UserService.FACTORY_LINK, PhotonControllerCloudAccountUtils.computeHashWithSHA256(userEmail)), null);
        Operation response = this.host.waitForResponse(
                Operation.createGet(
                        UriUtils.buildUri(this.host.getPeerHost(), SERVICE_QUERY_CONFIG_RULES))
        );
        ConfigContext configContext = response.getBody(ConfigContext.class);
        assertNotNull(configContext);
        assertEquals(ruleValue, configContext.rules.get(ruleName).toString());
    }

    /**
     * This method is to create the rule for feature flag test
     * @param id : rule id
     * @param value : rule value
     * @param tenantLinks : rule's tenant link
     * @return
     */
    private ConfigurationRuleState createRule(String id, String value, List<TenantLinkSpec> tenantLinks) {
        ConfigurationRuleState rule = new ConfigurationRuleState();
        rule.id = id;
        rule.value = value;
        if (tenantLinks != null && tenantLinks.size() > 0) {
            rule.tenantLinkOverrides = new ArrayList<>(tenantLinks);
        }
        URI uri = UriUtils.buildUri(this.host.getPeerHostUri(), SERVICE_CONFIG_RULES);
        Operation response = this.host.waitForResponse(Operation.createPost(uri).setBody(rule));
        assertNotNull(response);
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        return response.getBody(ConfigurationRuleState.class);
    }

    /**
     * Update the rule's attributes
     * @param id
     * @param value
     * @param tenantLinks
     */
    private void updateRule(String id, String value, List<TenantLinkSpec> tenantLinks) {
        ConfigurationRuleState rule = new ConfigurationRuleState();
        rule.id = id;
        rule.value = value;
        if (tenantLinks != null && tenantLinks.size() > 0) {
            rule.tenantLinkOverrides = new ArrayList<>(tenantLinks);
        }
        URI uri = UriUtils.buildUri(this.host.getPeerHostUri(), SERVICE_CONFIG_RULES + "/" + id);
        Operation response = this.host.waitForResponse(Operation.createPut(uri).setBody(rule));
        assertNotNull(response);
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
    }
}
