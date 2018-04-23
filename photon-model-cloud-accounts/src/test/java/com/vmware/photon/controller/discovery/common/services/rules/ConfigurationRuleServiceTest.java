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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import static com.vmware.photon.controller.model.UriPaths.SERVICE_CONFIG_RULES;
import static com.vmware.xenon.common.UriUtils.extendUri;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.discovery.common.services.OrganizationService;
import com.vmware.photon.controller.discovery.common.services.rules.ConfigurationRuleService.ConfigurationRuleState;
import com.vmware.xenon.common.BasicTestCase;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;

public class ConfigurationRuleServiceTest extends BasicTestCase {

    private static final int NUM_NODES = 3;

    private static final String RULE_ID = "fooFeature";
    private static final String RULE_VAL = "foo eq /link/123";
    private static final String RULE_VAL_NEW = "foo eq /link/234";
    private static final String ORG_ID = "org-1";
    private static final String ERROR_RULE_ID = "foo1:foo2:fooFeature";

    @Before
    public void before() throws Throwable {
        CommandLineArgumentParser.parseFromProperties(this);
        this.host.setUpPeerHosts(NUM_NODES);
        this.host.joinNodesAndVerifyConvergence(NUM_NODES, NUM_NODES, true);
        this.host.setNodeGroupQuorum(NUM_NODES);

        this.host.setSystemAuthorizationContext();
        for (VerificationHost h : this.host.getInProcessHostMap().values()) {
            h.startService(Operation.createPost(h,
                    ConfigurationRuleService.FACTORY_LINK),
                    ConfigurationRuleService.createFactory());
        }

        this.host.waitForReplicatedFactoryServiceAvailable(
                UriUtils.buildUri(this.host.getPeerHost(), ConfigurationRuleService.FACTORY_LINK));
    }

    @After
    public void tearDown() throws Throwable {
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
    public void testPost() throws Throwable {
        ConfigurationRuleState rule = createTestRule();
        Operation response = this.host
                .waitForResponse(Operation.createPost(getUri()).setBody(rule));

        assertNotNull(response);
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        ConfigurationRuleState result = response.getBody(ConfigurationRuleState.class);
        assertNotNull(result);
        assertEquals(RULE_ID, result.id);
        assertEquals(RULE_VAL, result.value);
        assertRuleCount(1);
    }

    @Test
    public void testDuplicateLinks() throws Throwable {
        List<ConfigurationRuleService.TenantLinkSpec> tenantLinks = new ArrayList<>();
        tenantLinks.add(new ConfigurationRuleService.TenantLinkSpec(UriUtils
                .buildUriPath(OrganizationService.FACTORY_LINK, Utils.computeHash(ORG_ID)), RULE_VAL));
        tenantLinks.add(new ConfigurationRuleService.TenantLinkSpec(UriUtils
                .buildUriPath(OrganizationService.FACTORY_LINK, Utils.computeHash(ORG_ID)), RULE_VAL));
        this.host.setSystemAuthorizationContext();
        ConfigurationRuleState rule = new ConfigurationRuleState();
        rule.id = RULE_ID;
        rule.value = RULE_VAL_NEW;
        if (tenantLinks != null && tenantLinks.size() > 0) {
            rule.tenantLinkOverrides = new ArrayList<>(tenantLinks);
        }
        URI uri = UriUtils.buildUri(this.host.getPeerHostUri(), SERVICE_CONFIG_RULES);
        Operation response = this.host.waitForResponse(Operation.createPost(uri).setBody(rule));
        Assert.assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        this.host.resetAuthorizationContext();
    }


    @Test
    public void testPostWithExistingId() throws Throwable {
        ConfigurationRuleState rule = createTestRule();

        this.host.waitForResponse(Operation.createPost(getUri()).setBody(rule));
        Operation response = this.host
                .waitForResponse(Operation.createPost(getUri()).setBody(rule));

        assertNotNull(response);
        assertEquals(Operation.STATUS_CODE_CONFLICT, response.getStatusCode());
        assertRuleCount(1); // not created twice
    }

    @Test
    public void testPostWithMultipleSignId() throws Throwable {
        ConfigurationRuleState rule = new ConfigurationRuleState();
        rule.id = ERROR_RULE_ID;
        rule.value = RULE_VAL;
        Operation response = this.host
                .waitForResponse(Operation.createPost(getUri()).setBody(rule));

        assertNotNull(response);
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
    }

    @Test
    public void testPutWithUpdatedVal() throws Throwable {
        // Xenon do not support PUT for creation, need to create a rule with
        // POST first.
        ConfigurationRuleState rule = createTestRule();
        Operation response = this.host
                .waitForResponse(Operation.createPost(getUri()).setBody(rule));
        assertNotNull(response);
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        rule.value = RULE_VAL_NEW;

        response = this.host
                .waitForResponse(Operation.createPut(extendUri(getUri(), RULE_ID)).setBody(rule));
        assertNotNull(response);
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        ConfigurationRuleState result = response.getBody(ConfigurationRuleState.class);
        assertNotNull(result);
        assertEquals(RULE_ID, result.id);
        assertEquals(RULE_VAL_NEW, result.value);
        assertRuleCount(1);

        // double check by loading state directly (PUT response happen to not be
        // reliable)
        result = getById(RULE_ID);
        assertNotNull(result);
        assertEquals(RULE_ID, result.id);
        assertEquals(RULE_VAL_NEW, result.value);
    }

    @Test
    public void testPutWithUpdatedId() throws Throwable {
        // Xenon do not support PUT for creation, need to create a rule with
        // POST first.
        ConfigurationRuleState rule = createTestRule();
        Operation response = this.host
                .waitForResponse(Operation.createPost(getUri()).setBody(rule));
        assertNotNull(response);
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        rule.id = rule.id + "updated";

        response = this.host
                .waitForResponse(Operation.createPut(extendUri(getUri(), RULE_ID)).setBody(rule));
        assertNotNull(response);
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, response.getStatusCode());
        assertRuleCount(1);

        // double check by loading state directly
        ConfigurationRuleState result = getById(RULE_ID);
        assertNotNull(result);
        assertEquals(RULE_ID, result.id);
        assertEquals(RULE_VAL, result.value);
    }

    private void assertRuleCount(long expected) throws Throwable {
        Operation response = this.host
                .waitForResponse(Operation.createGet(getUri()));
        assertNotNull(response);
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        ServiceDocumentQueryResult result = response.getBody(ServiceDocumentQueryResult.class);
        assertEquals(expected, result.documentCount.longValue());
    }

    private ConfigurationRuleState getById(String id) {
        Operation response = this.host
                .waitForResponse(Operation.createGet(extendUri(getUri(), id)));
        assertNotNull(response);
        assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());

        return response.getBody(ConfigurationRuleState.class);
    }

    private ConfigurationRuleState createTestRule() {
        ConfigurationRuleState rule = new ConfigurationRuleState();
        rule.id = RULE_ID;
        rule.value = RULE_VAL;
        return rule;
    }

    private URI getUri() {
        return UriUtils.buildUri(this.host.getPeerHost(), SERVICE_CONFIG_RULES);
    }

}
