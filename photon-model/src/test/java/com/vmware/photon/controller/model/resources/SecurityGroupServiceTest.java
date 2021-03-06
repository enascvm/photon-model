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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.SecurityGroupService.Protocol;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState.Rule;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState.Rule.Access;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.TenantService;

/**
 * This class implements tests for the {@link SecurityGroupService} class.
 */
@RunWith(SecurityGroupServiceTest.class)
@SuiteClasses({ SecurityGroupServiceTest.ConstructorTest.class,
        SecurityGroupServiceTest.HandleStartTest.class,
        SecurityGroupServiceTest.HandlePatchTest.class,
        SecurityGroupServiceTest.HandlePutTest.class,
        SecurityGroupServiceTest.QueryTest.class })
public class SecurityGroupServiceTest extends Suite {

    public SecurityGroupServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    private static SecurityGroupService.SecurityGroupState buildValidStartState(boolean assignHost) {
        SecurityGroupService.SecurityGroupState securityGroupState =
                new SecurityGroupService.SecurityGroupState();
        securityGroupState.id = UUID.randomUUID().toString();
        securityGroupState.name = securityGroupState.id;
        securityGroupState.tenantLinks = new ArrayList<>();
        securityGroupState.tenantLinks.add("tenant-linkA");
        securityGroupState.ingress = getAllowIngressRules();
        securityGroupState.egress = getAllowEgressRules();
        securityGroupState.regionId = "regionId";
        securityGroupState.authCredentialsLink = "/link/to/auth";
        securityGroupState.resourcePoolLink = "/link/to/rp";
        if (assignHost) {
            securityGroupState.computeHostLink = "host-1";
        }
        try {
            securityGroupState.instanceAdapterReference = new URI(
                    "http://instanceAdapterReference");
        } catch (Exception e) {
            securityGroupState.instanceAdapterReference = null;
        }
        return securityGroupState;
    }

    public static ArrayList<Rule> getAllowIngressRules() {
        ArrayList<Rule> rules = new ArrayList<>();
        Rule ssh = new Rule();
        ssh.name = "ssh";
        ssh.protocol = "tcp";
        ssh.ipRangeCidr = "0.0.0.0/0";
        ssh.ports = "22";
        ssh.access = Access.Allow;
        rules.add(ssh);
        return rules;
    }

    public static ArrayList<Rule> getAllowEgressRules() {
        ArrayList<Rule> rules = new ArrayList<>();
        Rule out = new Rule();
        out.name = "out";
        out.protocol = SecurityGroupService.ANY;
        out.ipRangeCidr = "0.0.0.0/0";
        out.ports = "1-65535";
        out.access = Access.Deny;
        rules.add(out);

        Rule outWithProtocolNumber = new Rule();
        outWithProtocolNumber.name = "outWithProtocolNumber";
        outWithProtocolNumber.protocol = String.valueOf(Protocol.ANY.getProtocolNumber());
        outWithProtocolNumber.ipRangeCidr = "0.0.0.0/0";
        outWithProtocolNumber.ports = "1-65535";
        outWithProtocolNumber.access = Access.Deny;
        rules.add(outWithProtocolNumber);
        return rules;
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {
        private SecurityGroupService securityGroupService = new SecurityGroupService();

        @Before
        public void setupTest() {
            this.securityGroupService = new SecurityGroupService();
        }

        @Test
        public void testServiceOptions() {
            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.CONCURRENT_GET_HANDLING,
                    Service.ServiceOption.PERSISTENCE,
                    Service.ServiceOption.REPLICATION,
                    Service.ServiceOption.OWNER_SELECTION,
                    Service.ServiceOption.IDEMPOTENT_POST);

            assertThat(this.securityGroupService.getOptions(), is(expected));
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleStartTest extends BaseModelTest {
        @Test
        public void testValidStartState() throws Throwable {
            SecurityGroupService.SecurityGroupState startState = buildValidStartState(false);
            SecurityGroupService.SecurityGroupState returnState = postServiceSynchronously(
                    SecurityGroupService.FACTORY_LINK,
                            startState, SecurityGroupService.SecurityGroupState.class);

            assertNotNull(returnState);
            assertThat(returnState.id, is(startState.id));
            assertThat(returnState.regionId, is(startState.regionId));
            assertThat(returnState.authCredentialsLink,
                    is(startState.authCredentialsLink));
            assertThat(returnState.resourcePoolLink,
                    is(startState.resourcePoolLink));
            assertThat(returnState.instanceAdapterReference,
                    is(startState.instanceAdapterReference));
            assertThat(returnState.ingress.get(0).name,
                    is(getAllowIngressRules().get(0).name));
            assertThat(returnState.egress.get(0).name, is(getAllowEgressRules()
                    .get(0).name));
            assertThat(returnState.egress.get(1).name, is(getAllowEgressRules()
                    .get(1).name));
        }

        @Test
        public void testValidStartStateWithHost() throws Throwable {
            SecurityGroupService.SecurityGroupState startState = buildValidStartState(true);
            SecurityGroupService.SecurityGroupState returnState = postServiceSynchronously(
                    SecurityGroupService.FACTORY_LINK,
                    startState, SecurityGroupService.SecurityGroupState.class);

            assertNotNull(returnState);
            assertThat(returnState.id, is(startState.id));
            assertThat(returnState.regionId, is(startState.regionId));
            assertThat(returnState.authCredentialsLink,
                    is(startState.authCredentialsLink));
            assertThat(returnState.resourcePoolLink,
                    is(startState.resourcePoolLink));
            assertThat(returnState.instanceAdapterReference,
                    is(startState.instanceAdapterReference));
            assertThat(returnState.ingress.get(0).name,
                    is(getAllowIngressRules().get(0).name));
            assertThat(returnState.egress.get(0).name, is(getAllowEgressRules()
                    .get(0).name));
            assertThat(returnState.egress.get(1).name, is(getAllowEgressRules()
                    .get(1).name));
            assertThat(returnState.computeHostLink, is(startState.computeHostLink));
        }

        @Test
        public void testDuplicatePost() throws Throwable {
            SecurityGroupService.SecurityGroupState startState = buildValidStartState(false);
            SecurityGroupService.SecurityGroupState returnState = postServiceSynchronously(
                    SecurityGroupService.FACTORY_LINK,
                            startState, SecurityGroupService.SecurityGroupState.class);

            assertNotNull(returnState);
            assertThat(returnState.regionId, is(startState.regionId));
            startState.regionId = "new-regionId";
            returnState = postServiceSynchronously(SecurityGroupService.FACTORY_LINK,
                    startState, SecurityGroupService.SecurityGroupState.class);
            assertThat(returnState.regionId, is(startState.regionId));

        }

        @Test
        public void testDuplicatePostAssignComputeHost() throws Throwable {
            SecurityGroupService.SecurityGroupState startState = buildValidStartState(false);
            SecurityGroupService.SecurityGroupState returnState = postServiceSynchronously(
                    SecurityGroupService.FACTORY_LINK,
                    startState, SecurityGroupService.SecurityGroupState.class);

            assertNotNull(returnState);
            assertNull(returnState.computeHostLink);
            assertThat(returnState.regionId, is(startState.regionId));
            startState.regionId = "new-regionId";
            startState.computeHostLink = "host-1";
            returnState = postServiceSynchronously(SecurityGroupService.FACTORY_LINK,
                    startState, SecurityGroupService.SecurityGroupState.class);
            assertThat(returnState.regionId, is(startState.regionId));
            assertNotNull(returnState.computeHostLink);
            assertThat(returnState.computeHostLink, is(startState.computeHostLink));
        }

        @Test
        public void testDuplicatePostModifyComputeHost() throws Throwable {
            SecurityGroupService.SecurityGroupState startState = buildValidStartState(true);
            SecurityGroupService.SecurityGroupState returnState = postServiceSynchronously(
                    SecurityGroupService.FACTORY_LINK,
                    startState, SecurityGroupService.SecurityGroupState.class);

            assertNotNull(returnState);
            assertNotNull(returnState.computeHostLink);
            assertThat(returnState.regionId, is(startState.regionId));

            returnState.computeHostLink = "host-2";
            postServiceSynchronously(SecurityGroupService.FACTORY_LINK,
                    returnState, SecurityGroupService.SecurityGroupState.class,
                    IllegalArgumentException.class);
        }

        @Test
        public void testDuplicatePostModifyCreationTime() throws Throwable {
            SecurityGroupService.SecurityGroupState startState = buildValidStartState(false);
            SecurityGroupService.SecurityGroupState returnState = postServiceSynchronously(
                    SecurityGroupService.FACTORY_LINK,
                    startState, SecurityGroupService.SecurityGroupState.class);

            assertNotNull(returnState);
            assertNotNull(returnState.documentCreationTimeMicros);

            long originalTime = returnState.documentCreationTimeMicros;
            returnState.documentCreationTimeMicros = originalTime;

            returnState = postServiceSynchronously(SecurityGroupService.FACTORY_LINK,
                    returnState, SecurityGroupService.SecurityGroupState.class);
            assertThat(originalTime, is(returnState.documentCreationTimeMicros));
        }

        @Test
        public void testInvalidValues() throws Throwable {
            SecurityGroupState missingIngressRuleName = buildValidStartState(false);
            SecurityGroupState missingEgressRuleName = buildValidStartState(false);

            SecurityGroupState missingIngressProtocol = buildValidStartState(false);
            SecurityGroupState missingEgressProtocol = buildValidStartState(false);

            SecurityGroupState invalidIngressProtocol = buildValidStartState(false);
            SecurityGroupState invalidEgressProtocol = buildValidStartState(false);

            SecurityGroupState invalidIngressIpRangeNoSubnet = buildValidStartState(false);
            SecurityGroupState invalidIngressIpRangeInvalidIP = buildValidStartState(false);
            SecurityGroupState invalidIngressIpRangeInvalidSubnet = buildValidStartState(false);

            SecurityGroupState invalidEgressIpRangeNoSubnet = buildValidStartState(false);
            SecurityGroupState invalidEgressIpRangeInvalidIP = buildValidStartState(false);
            SecurityGroupState invalidEgressIpRangeInvalidSubnet = buildValidStartState(false);

            SecurityGroupService.SecurityGroupState invalidIngressPorts0 = buildValidStartState(false);
            SecurityGroupService.SecurityGroupState invalidIngressPorts1 = buildValidStartState(false);
            SecurityGroupService.SecurityGroupState invalidIngressPorts2 = buildValidStartState(false);
            SecurityGroupService.SecurityGroupState invalidIngressPorts3 = buildValidStartState(false);
            SecurityGroupService.SecurityGroupState invalidIngressPorts4 = buildValidStartState(false);

            SecurityGroupService.SecurityGroupState invalidEgressPorts0 = buildValidStartState(false);
            SecurityGroupService.SecurityGroupState invalidEgressPorts1 = buildValidStartState(false);
            SecurityGroupService.SecurityGroupState invalidEgressPorts2 = buildValidStartState(false);
            SecurityGroupService.SecurityGroupState invalidEgressPorts3 = buildValidStartState(false);
            SecurityGroupService.SecurityGroupState invalidEgressPorts4 = buildValidStartState(false);

            missingIngressRuleName.ingress.get(0).name = null;
            missingEgressRuleName.egress.get(0).name = null;

            missingIngressProtocol.ingress.get(0).protocol = null;
            missingEgressProtocol.egress.get(0).protocol = null;

            invalidIngressProtocol.ingress.get(0).protocol = "not-tcp-udp-icmp-protocol";
            invalidEgressProtocol.egress.get(0).protocol = "not-tcp-udp-icmp-protocol";

            invalidIngressIpRangeNoSubnet.ingress.get(0).ipRangeCidr = "10.0.0.0";
            invalidIngressIpRangeInvalidIP.ingress.get(0).ipRangeCidr = "10.0.0.FOO";
            invalidIngressIpRangeInvalidSubnet.ingress.get(0).ipRangeCidr = "10.0.0.0/33";

            invalidEgressIpRangeNoSubnet.ingress.get(0).ipRangeCidr = "10.0.0.0";
            invalidEgressIpRangeInvalidIP.ingress.get(0).ipRangeCidr = "10.0.0.FOO";
            invalidEgressIpRangeInvalidSubnet.ingress.get(0).ipRangeCidr = "10.0.0.0/33";

            invalidIngressPorts0.ingress.get(0).ports = null;
            invalidIngressPorts1.ingress.get(0).ports = "1-1024-6535";
            invalidIngressPorts2.ingress.get(0).ports = "-1";
            invalidIngressPorts3.ingress.get(0).ports = "badString";
            invalidIngressPorts4.ingress.get(0).ports = "100-1";

            invalidEgressPorts0.ingress.get(0).ports = null;
            invalidEgressPorts1.ingress.get(0).ports = "1-1024-6535";
            invalidEgressPorts2.ingress.get(0).ports =  "-1";
            invalidEgressPorts3.ingress.get(0).ports = "badString";
            invalidEgressPorts4.ingress.get(0).ports = "100-1";

            SecurityGroupService.SecurityGroupState[] stateArray = {
                    missingIngressRuleName,
                    missingEgressRuleName, missingIngressProtocol,
                    missingEgressProtocol, invalidIngressProtocol,
                    invalidEgressProtocol, invalidIngressIpRangeNoSubnet,
                    invalidIngressIpRangeInvalidIP,
                    invalidIngressIpRangeInvalidSubnet,
                    invalidEgressIpRangeInvalidSubnet,
                    invalidEgressIpRangeNoSubnet,
                    invalidEgressIpRangeInvalidIP,
                    invalidIngressPorts0,
                    invalidIngressPorts1, invalidIngressPorts2,
                    invalidIngressPorts3, invalidIngressPorts4,
                    invalidEgressPorts0, invalidEgressPorts1,
                    invalidEgressPorts2, invalidEgressPorts3,
                    invalidEgressPorts4 };
            for (SecurityGroupService.SecurityGroupState state : stateArray) {
                postServiceSynchronously(SecurityGroupService.FACTORY_LINK,
                        state, SecurityGroupService.SecurityGroupState.class,
                        IllegalArgumentException.class);
            }

        }
    }

    /**
     * This class implements tests for the handlePatch method.
     */
    public static class HandlePatchTest extends BaseModelTest {
        @Test
        public void testPatch() throws Throwable {
            SecurityGroupService.SecurityGroupState startState = buildValidStartState(false);

            SecurityGroupService.SecurityGroupState returnState = postServiceSynchronously(
                    SecurityGroupService.FACTORY_LINK,
                            startState, SecurityGroupService.SecurityGroupState.class);
            assertNull(returnState.computeHostLink);
            assertNotNull(returnState.documentCreationTimeMicros);

            Rule newIngressrule1 = new Rule();
            newIngressrule1.name = "ssh";
            newIngressrule1.protocol = "tcp";
            newIngressrule1.ipRangeCidr = "10.10.10.10/10";
            newIngressrule1.ports = "44";
            newIngressrule1.access = Access.Allow;

            Rule newIngressrule2 = new Rule();
            newIngressrule2.name = "ssh";
            newIngressrule2.protocol = "tcp";
            newIngressrule2.ipRangeCidr = "10.10.10.10/10";
            newIngressrule2.ports = "45";
            newIngressrule2.access = Access.Allow;

            Rule newEgressRule = new Rule();
            newEgressRule.name = "out";
            newEgressRule.protocol = "tcp";
            newEgressRule.ipRangeCidr = SecurityGroupService.ANY;
            newEgressRule.ports = "1-65535";

            SecurityGroupService.SecurityGroupState patchState = new SecurityGroupService.SecurityGroupState();
            patchState.name = "newName";
            patchState.ingress = new ArrayList<>();
            patchState.egress = new ArrayList<>();
            patchState.ingress.add(0, newIngressrule1);
            patchState.ingress.add(1, newIngressrule2);
            patchState.egress.add(0, newEgressRule);
            patchState.customProperties = new HashMap<>();
            patchState.customProperties.put("customKey", "customValue");

            patchState.regionId = "patchRregionID";
            patchState.authCredentialsLink = "http://patchAuthCredentialsLink";
            patchState.resourcePoolLink = "http://patchResourcePoolLink";
            patchState.computeHostLink = "host-1";
            try {
                patchState.instanceAdapterReference = new URI(
                        "http://patchInstanceAdapterReference");
            } catch (Exception e) {
                patchState.instanceAdapterReference = null;
            }
            patchState.tenantLinks = new ArrayList<>();
            patchState.tenantLinks.add("tenant1");
            patchState.groupLinks = new HashSet<>();
            patchState.groupLinks.add("group1");
            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);

            returnState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    SecurityGroupService.SecurityGroupState.class);

            // region ID should not be updated
            assertThat(returnState.regionId, is(startState.regionId));
            assertThat(returnState.authCredentialsLink,
                    is(patchState.authCredentialsLink));
            assertThat(returnState.resourcePoolLink,
                    is(patchState.resourcePoolLink));
            assertThat(returnState.instanceAdapterReference,
                    is(patchState.instanceAdapterReference));

            assertThat(returnState.name, is(patchState.name));
            assertThat(returnState.ingress.get(0).name,
                    is(patchState.ingress.get(0).name));
            assertThat(returnState.ingress.get(0).protocol,
                    is(patchState.ingress.get(0).protocol));
            assertThat(returnState.ingress.get(0).ports,
                    is(patchState.ingress.get(0).ports));
            assertThat(returnState.egress.get(0).name,
                    is(patchState.egress.get(0).name));
            assertThat(returnState.egress.get(0).protocol,
                    is(patchState.egress.get(0).protocol));
            assertThat(returnState.egress.get(0).ports,
                    is(patchState.egress.get(0).ports));
            assertThat(returnState.customProperties.get("customKey"),
                    is("customValue"));
            assertEquals(returnState.tenantLinks.size(), 2);
            assertEquals(returnState.groupLinks, patchState.groupLinks);

            patchState.ingress.clear();
            patchState.ingress.add(0, newIngressrule2);
            patchState.ingress.add(1, newIngressrule1);
            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);

            SecurityGroupService.SecurityGroupState newReturnState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    SecurityGroupService.SecurityGroupState.class);
            assertEquals(returnState.documentVersion, newReturnState.documentVersion);
            assertNotNull(returnState.computeHostLink);
            assertThat(returnState.computeHostLink, is(patchState.computeHostLink));
        }

        @Test
        public void testPatchAssignHost() throws Throwable {
            SecurityGroupService.SecurityGroupState startState = buildValidStartState(false);

            SecurityGroupService.SecurityGroupState returnState = postServiceSynchronously(
                    SecurityGroupService.FACTORY_LINK,
                    startState, SecurityGroupService.SecurityGroupState.class);
            assertNull(returnState.computeHostLink);

            SecurityGroupService.SecurityGroupState patchState = new SecurityGroupService.SecurityGroupState();
            patchState.computeHostLink = "host-1";

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);

            returnState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    SecurityGroupService.SecurityGroupState.class);
            assertNotNull(returnState.computeHostLink);
            assertThat(returnState.computeHostLink, is(patchState.computeHostLink));
        }

        @Test(expected = IllegalArgumentException.class)
        public void testPatchModifyHost() throws Throwable {
            SecurityGroupService.SecurityGroupState startState = buildValidStartState(true);

            SecurityGroupService.SecurityGroupState returnState = postServiceSynchronously(
                    SecurityGroupService.FACTORY_LINK,
                    startState, SecurityGroupService.SecurityGroupState.class);

            SecurityGroupService.SecurityGroupState patchState = new SecurityGroupService.SecurityGroupState();
            patchState.computeHostLink = "host-2";
            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);
        }

        @Test
        public void testPatchModifyCreationTime() throws Throwable {
            SecurityGroupService.SecurityGroupState startState = buildValidStartState(false);

            SecurityGroupService.SecurityGroupState returnState = postServiceSynchronously(
                    SecurityGroupService.FACTORY_LINK,
                    startState, SecurityGroupService.SecurityGroupState.class);
            assertNotNull(returnState.documentCreationTimeMicros);
            long originalCreationTime = returnState.documentCreationTimeMicros;

            SecurityGroupService.SecurityGroupState patchState = new SecurityGroupService.SecurityGroupState();
            long currentCreationTime = Utils.getNowMicrosUtc();
            patchState.documentCreationTimeMicros = currentCreationTime;

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);

            returnState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    SecurityGroupService.SecurityGroupState.class);
            assertNotNull(returnState.documentCreationTimeMicros);
            assertThat(returnState.documentCreationTimeMicros, is(originalCreationTime));
        }
    }

    /**
     * This class implements tests for the handlePut method.
     */
    public static class HandlePutTest extends BaseModelTest {

        @Test
        public void testPut() throws Throwable {
            SecurityGroupState startState = buildValidStartState(false);

            SecurityGroupState returnState = postServiceSynchronously(
                    SecurityGroupService.FACTORY_LINK,
                    startState, SecurityGroupState.class);

            assertNotNull(returnState);
            SecurityGroupService.SecurityGroupState newState =
                    new SecurityGroupService.SecurityGroupState();
            newState.id = UUID.randomUUID().toString();
            newState.name = newState.id;
            newState.tenantLinks = new ArrayList<>();
            newState.tenantLinks.add("tenant-linkA");
            newState.ingress = getAllowIngressRules();
            newState.egress = getAllowEgressRules();
            newState.regionId = "regionId";
            newState.authCredentialsLink = "/link/to/auth";
            newState.resourcePoolLink = "/link/to/rp";
            newState.documentCreationTimeMicros = returnState.documentCreationTimeMicros;

            try {
                newState.instanceAdapterReference = new URI(
                        "http://instanceAdapterReference");
            } catch (Exception e) {
                newState.instanceAdapterReference = null;
            }

            putServiceSynchronously(returnState.documentSelfLink,
                    newState);

            SecurityGroupState getState = getServiceSynchronously(returnState.documentSelfLink,
                    SecurityGroupState.class);
            assertThat(getState.id, is(newState.id));
            assertThat(getState.name, is(newState.name));
            assertEquals(getState.tenantLinks, newState.tenantLinks);
            assertEquals(getState.groupLinks, newState.groupLinks);
            // make sure launchTimeMicros was preserved
            assertEquals(getState.creationTimeMicros, returnState.creationTimeMicros);
            assertEquals(getState.documentCreationTimeMicros, returnState.documentCreationTimeMicros);
        }

        @Test
        public void testPutModifyCreationTime() throws Throwable {
            SecurityGroupState startState = buildValidStartState(false);
            SecurityGroupState returnState = postServiceSynchronously(
                    SecurityGroupService.FACTORY_LINK,
                    startState, SecurityGroupState.class);

            assertNotNull(returnState);

            SecurityGroupState newState = new SecurityGroupState();
            newState.id = UUID.randomUUID().toString();
            newState.name = newState.id;
            newState.tenantLinks = new ArrayList<>();
            newState.tenantLinks.add("tenant-linkA");
            newState.ingress = getAllowIngressRules();
            newState.egress = getAllowEgressRules();
            newState.regionId = "regionId";
            newState.authCredentialsLink = "/link/to/auth";
            newState.resourcePoolLink = "/link/to/rp";

            long currentTime = Utils.getNowMicrosUtc();
            newState.documentCreationTimeMicros = currentTime;

            try {
                newState.instanceAdapterReference = new URI(
                        "http://instanceAdapterReference");
            } catch (Exception e) {
                newState.instanceAdapterReference = null;
            }

            putServiceSynchronously(returnState.documentSelfLink,
                    newState);

            SecurityGroupState getState = getServiceSynchronously(
                    returnState.documentSelfLink, SecurityGroupState.class);
            assertThat(getState.documentCreationTimeMicros, is(returnState.documentCreationTimeMicros));
        }

        @Test(expected = IllegalArgumentException.class)
        public void testPutModifyHost() throws Throwable {
            SecurityGroupState startState = buildValidStartState(true);
            SecurityGroupState returnState = postServiceSynchronously(
                    SecurityGroupService.FACTORY_LINK,
                    startState, SecurityGroupState.class);

            assertNotNull(returnState);

            SecurityGroupState newState = new SecurityGroupState();
            newState.id = UUID.randomUUID().toString();
            newState.name = newState.id;
            newState.tenantLinks = new ArrayList<>();
            newState.tenantLinks.add("tenant-linkA");
            newState.ingress = getAllowIngressRules();
            newState.egress = getAllowEgressRules();
            newState.regionId = "regionId";
            newState.authCredentialsLink = "/link/to/auth";
            newState.resourcePoolLink = "/link/to/rp";
            newState.documentCreationTimeMicros = returnState.documentCreationTimeMicros;

            newState.computeHostLink = "host-2";

            try {
                newState.instanceAdapterReference = new URI(
                        "http://instanceAdapterReference");
            } catch (Exception e) {
                newState.instanceAdapterReference = null;
            }

            putServiceSynchronously(returnState.documentSelfLink,
                    newState);
        }
    }

    /**
     * This class implements tests for query.
     */
    public static class QueryTest extends BaseModelTest {
        @Test
        public void testTenantLinksQuery() throws Throwable {
            SecurityGroupService.SecurityGroupState securityGroupState = buildValidStartState(false);
            URI tenantUri = UriUtils.buildFactoryUri(this.host, TenantService.class);
            securityGroupState.tenantLinks = new ArrayList<>();
            securityGroupState.tenantLinks.add(UriUtils.buildUriPath(
                    tenantUri.getPath(), "tenantA"));
            SecurityGroupService.SecurityGroupState startState = postServiceSynchronously(
                    SecurityGroupService.FACTORY_LINK,
                            securityGroupState, SecurityGroupService.SecurityGroupState.class);

            String kind = Utils.buildKind(SecurityGroupService.SecurityGroupState.class);
            String propertyName = QueryTask.QuerySpecification
                    .buildCollectionItemName(ResourceState.FIELD_NAME_TENANT_LINKS);

            QueryTask q = createDirectQueryTask(kind, propertyName,
                    securityGroupState.tenantLinks.get(0));
            q = querySynchronously(q);
            assertNotNull(q.results.documentLinks);
            assertThat(q.results.documentCount, is(1L));
            assertThat(q.results.documentLinks.get(0),
                    is(startState.documentSelfLink));
        }
    }
}