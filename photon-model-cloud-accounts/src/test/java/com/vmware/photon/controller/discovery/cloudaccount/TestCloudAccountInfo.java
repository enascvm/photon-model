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

package com.vmware.photon.controller.discovery.cloudaccount;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountApiService.CLOUD_ACCOUNT_INFO_PATH_TEMPLATE;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountApiService.getAwsExternalId;
import static com.vmware.photon.controller.discovery.cloudaccount.awsaccesspolicy.AwsAccessPolicy.DEFAULT_AWS_ACCESS_POLICY;
import static com.vmware.photon.controller.discovery.cloudaccount.awsaccesspolicy.AwsAccessPolicy.DEFAULT_AWS_CUR_ACCESS_POLICY;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.AWS_MASTER_ACCOUNT_ID;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.CLOUD_ACCOUNT_SERVICE_TAG_COSTINSIGHT;
import static com.vmware.photon.controller.discovery.common.authn.TestAuthContextService.ORG_ID;
import static com.vmware.photon.controller.discovery.common.authn.TestAuthContextService.USER;
import static com.vmware.photon.controller.discovery.common.authn.TestAuthContextService.userLink;
import static com.vmware.xenon.common.Operation.REQUEST_AUTH_TOKEN_HEADER;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.amazonaws.auth.policy.Action;
import com.amazonaws.auth.policy.Policy;
import com.amazonaws.auth.policy.Resource;
import com.amazonaws.auth.policy.Statement;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.discovery.cloudaccount.awsaccesspolicy.AwsAccessPolicy;
import com.vmware.photon.controller.discovery.cloudaccount.awsaccesspolicy.AwsActions;
import com.vmware.photon.controller.discovery.common.authn.AuthContextService;
import com.vmware.photon.controller.discovery.common.authn.TestAuthContextService.CustomAuthnService;
import com.vmware.photon.controller.discovery.common.services.UserService;
import com.vmware.xenon.common.BasicTestCase;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.AuthorizationHelper;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.QueryTask;

public class TestCloudAccountInfo extends BasicTestCase {

    @Override
    public void beforeHostStart(VerificationHost h) {
        h.setAuthorizationEnabled(true);
        h.setAuthorizationService(new AuthContextService());
        h.setAuthenticationService(new CustomAuthnService());
    }

    @Before
    public void setup() throws Throwable {
        System.setProperty(AWS_MASTER_ACCOUNT_ID, "sample-master-account-id");
        this.host.setSystemAuthorizationContext();
        this.host.startServiceAndWait(new CloudAccountApiService(),
                CloudAccountApiService.SELF_LINK, null);
        this.host.resetSystemAuthorizationContext();
    }

    @Test
    public void testGetUnauthorized() throws Throwable {
        Throwable[] response = new Throwable[1];
        TestContext testContext = new TestContext(1, Duration.ofSeconds(30));
        this.host.sendWithDeferredResult(Operation.createGet(this.host,
                CLOUD_ACCOUNT_INFO_PATH_TEMPLATE))
                .whenComplete((o, e) -> {
                    response[0] = e;
                    testContext.complete();
                });
        testContext.await();

        Assert.assertNotNull(response[0]);
        assertEquals("forbidden", response[0].getMessage());
    }

    @Test
    public void testGet() throws Throwable {
        this.host.setSystemAuthorizationContext();
        AuthorizationHelper authHelper = new AuthorizationHelper(this.host);
        userLink = authHelper.createUserService(this.host, USER);
        String userGroupLink = authHelper.createUserGroup(this.host,
                Utils.computeHash(ORG_ID) + "-userGroup",
                QueryTask.Query.Builder.create()
                        .addFieldClause(UserService.UserState.FIELD_NAME_EMAIL, USER)
                        .build());
        String resourceGroupLink = authHelper.createResourceGroup(this.host,
                "resGroup", QueryTask.Query.Builder.create()
                        .addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                                "*", QueryTask.QueryTerm.MatchType.WILDCARD)
                        .build());
        authHelper.createRole(this.host, userGroupLink, resourceGroupLink,
                EnumSet.allOf(Service.Action.class));

        this.host.resetSystemAuthorizationContext();
        Operation.AuthorizationContext authCtx = this.host.assumeIdentity(userLink);
        // reset the auth context as we want it to be created via the auth service
        this.host.setAuthorizationContext(null);

        Operation[] response = new Operation[1];
        TestContext testContext = new TestContext(1, Duration.ofSeconds(30));
        this.host.sendWithDeferredResult(
                Operation.createGet(this.host, CLOUD_ACCOUNT_INFO_PATH_TEMPLATE)
                        .addRequestHeader(REQUEST_AUTH_TOKEN_HEADER, authCtx.getToken()))
                .thenAccept(o -> {
                    response[0] = o;
                    testContext.complete();
                });
        testContext.await();

        CloudAccountInfoViewState cloudAccountInfoViewState =
                response[0].getBody(CloudAccountInfoViewState.class);

        Assert.assertNotNull(cloudAccountInfoViewState);
        assertEquals(getAwsExternalId(ORG_ID), cloudAccountInfoViewState.aws.externalId);
        assertEquals("sample-master-account-id",
                cloudAccountInfoViewState.aws.accountId);
        assertAccessPolicy(DEFAULT_AWS_ACCESS_POLICY, cloudAccountInfoViewState.aws.accessPolicy);
        assertAccessPolicy(DEFAULT_AWS_ACCESS_POLICY,
                cloudAccountInfoViewState.aws.accessPolicies.get("default"));
        assertAccessPolicy(DEFAULT_AWS_CUR_ACCESS_POLICY,
                cloudAccountInfoViewState.aws.accessPolicies.get(CLOUD_ACCOUNT_SERVICE_TAG_COSTINSIGHT));
    }

    @Test
    public void testAwsAccessPolicySerialization() {
        String testAction = "test:action" + UUID.randomUUID().toString();

        // Test policy conversion with just one statement
        Policy policy = new Policy()
                .withStatements(new Statement(Statement.Effect.Allow)
                        .withActions(AwsActions.EC2_DESCRIBE_ALL)
                        .withResources(new Resource("*")));
        assertAccessPolicy(policy, AwsAccessPolicy.create(policy));

        policy = new Policy()
                .withStatements(
                        new Statement(Statement.Effect.Allow)
                                .withActions(AwsActions.EC2_DESCRIBE_ALL)
                                .withResources(new Resource("*")),
                        new Statement(Statement.Effect.Deny)
                                .withActions(AwsActions.EC2_DESCRIBE_ALL,
                                        AwsActions.AUTO_SCALING_DESCRIBE_ALL)
                                .withResources(new Resource("test:resource"),
                                        new Resource("test:resource2")));
        assertAccessPolicy(policy, AwsAccessPolicy.create(policy));
    }

    @SuppressWarnings("unchecked")
    private void assertAccessPolicy(Policy original, AwsAccessPolicy transformed) {
        assertEquals(original.getVersion(), transformed.version);
        assertEquals(original.getStatements().size(), transformed.statement.size());

        // Because the serialization can occur in any order, we cannot rely on the index of the
        // arrays.

        List<AwsAccessPolicy.Statement> validatedStatements =
                original.getStatements().stream()
                        .map(statement -> transformed.statement.stream()
                                .filter(transformedStatement ->
                                        transformedStatement.sid.equals(statement.getId()))
                                .peek(transformedStatement -> {
                                    if (!(transformedStatement.action == null &&
                                            (statement.getActions() == null ||
                                                    statement.getActions().size() == 0))) {

                                        // Validate that the actions are the same
                                        assertEquals(statement.getActions().size(),
                                                transformedStatement.action.size());

                                        assertTrue(transformedStatement.action.containsAll(
                                                statement.getActions().stream()
                                                        .map(Action::getActionName)
                                                        .collect(Collectors.toList())));
                                    }

                                    if (!(transformedStatement.resource == null &&
                                            (statement.getResources() == null ||
                                                    statement.getResources().size() == 0))) {

                                        // Validate that the resources are the same
                                        assertEquals(statement.getResources().size(),
                                                transformedStatement.resource.size());

                                        assertTrue(transformedStatement.resource.containsAll(
                                                statement.getResources().stream()
                                                        .map(Resource::getId)
                                                        .collect(Collectors.toList())));
                                    }
                                }).collect(Collectors.toList()))
                        .map(policyStatements -> policyStatements.get(0))
                        .collect(Collectors.toList());

        // Validate that we iterated through the complete number of statements
        assertEquals(validatedStatements.size(), original.getStatements().size());
    }

    private void assertAccessPolicy(AwsAccessPolicy expected, AwsAccessPolicy actual) {
        assertEquals(expected.version, actual.version);
        assertEquals(expected.statement.size(), actual.statement.size());

        for (int i = 0; i < expected.statement.size(); i++) {
            AwsAccessPolicy.Statement expectedStatement = expected.statement.get(i);
            AwsAccessPolicy.Statement actualStatement = actual.statement.get(i);

            assertEquals(expectedStatement.sid, actualStatement.sid);
            assertEquals(expectedStatement.effect, actualStatement.effect);

            assertEquals(expectedStatement.action.size(), actualStatement.action.size());

            for (int j = 0; j < expectedStatement.action.size(); j++) {
                assertEquals(expectedStatement.action.get(j), actualStatement.action.get(j));
            }

            assertEquals(expectedStatement.resource.size(), actualStatement.resource.size());

            for (int j = 0; j < expectedStatement.resource.size(); j++) {
                assertEquals(expectedStatement.resource.get(j), actualStatement.resource.get(j));
            }
        }
    }

}
