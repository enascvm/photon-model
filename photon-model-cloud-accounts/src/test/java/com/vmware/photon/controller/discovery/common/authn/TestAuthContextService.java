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

package com.vmware.photon.controller.discovery.common.authn;

import static com.vmware.xenon.common.Operation.REQUEST_AUTH_TOKEN_HEADER;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.junit.Test;

import com.vmware.photon.controller.discovery.common.CloudAccountConstants;
import com.vmware.photon.controller.discovery.common.services.UserService;
import com.vmware.photon.controller.discovery.common.services.UserService.UserState;
import com.vmware.xenon.common.BasicTestCase;
import com.vmware.xenon.common.Claims;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.AuthorizationContext;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.AuthorizationHelper;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;

public class TestAuthContextService extends BasicTestCase {
    public static final String USER = "jane@doe.com";
    public static final String ORG_ID = "dummyOrg";

    public static String userLink = null;

    /**
     * custom auth handler that adds the orgId as a property
     * in the claims object
     */
    public static class CustomAuthnService extends SymphonyBasicAuthenticationService {

        /**
         * A function that can override the default behaviour to create custom claims on-the-fly
         * instead of relying on the base claims that get generated.
         */
        private Function<Operation, Claims> customClaimsBuilder;

        public CustomAuthnService() {
            this.customClaimsBuilder = (ignored) ->
                    createBaseClaimsBuilder(userLink, ORG_ID).getResult();
        }

        public CustomAuthnService(Function<Operation, Claims> customClaimsBuilder) {
            this.customClaimsBuilder = customClaimsBuilder;
        }

        @Override
        public void handlePost(Operation op) {
            if (op.hasPragmaDirective(Operation.PRAGMA_DIRECTIVE_VERIFY_TOKEN)) {
                op.removePragmaDirective(Operation.PRAGMA_DIRECTIVE_VERIFY_TOKEN);
                AuthorizationContext.Builder ab = AuthorizationContext.Builder.create();
                ab.setClaims(this.customClaimsBuilder.apply(op));
                ab.setToken(op.getRequestHeader(REQUEST_AUTH_TOKEN_HEADER));
                op.setBody(ab.getResult());
                op.complete();
                return;
            }
            super.handlePost(op);
        }

        /**
         * Helper method to create base claims for tests utilizing this service.
         * @return A base set of claims
         */
        public static Claims.Builder createBaseClaimsBuilder(String userLink, String orgId) {
            Claims.Builder claimsBuilder = new Claims.Builder();
            claimsBuilder.setSubject(userLink);
            Map<String, String> propMap = new HashMap<>();
            propMap.put(CloudAccountConstants.CSP_ORG_ID, orgId);
            claimsBuilder.setProperties(propMap);
            return claimsBuilder;
        }
    }

    @Override
    public void beforeHostStart(VerificationHost h) {
        h.setAuthorizationEnabled(true);
        h.setAuthorizationService(new AuthContextService());
        h.setAuthenticationService(new CustomAuthnService());
    }

    @Test
    public void testCustomAuthzService() throws Throwable {
        this.host.setSystemAuthorizationContext();
        AuthorizationHelper authHelper = new AuthorizationHelper(this.host);
        userLink = authHelper.createUserService(this.host, USER);
        // create 2 user groups - one with a org perfix and the other without
        String userGroupLink1 = authHelper.createUserGroup(this.host,
                Utils.computeHash(ORG_ID) + "-userGroup1",
                Builder.create()
                        .addFieldClause(UserState.FIELD_NAME_EMAIL, USER)
                        .build());
        String userGroupLink2 = authHelper.createUserGroup(this.host,
                "userGroup2",
                Builder.create()
                        .addFieldClause(UserState.FIELD_NAME_EMAIL, USER)
                        .build());
        String resourceGroupLink = authHelper.createResourceGroup(this.host,
                "resGroup1", Builder.create()
                    .addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                            "*", MatchType.WILDCARD)
                    .build());
        authHelper.createRole(this.host,
                userGroupLink1,
                resourceGroupLink,
                EnumSet.allOf(Action.class));
        authHelper.createRole(this.host,
                userGroupLink2,
                resourceGroupLink,
                EnumSet.allOf(Action.class));
        this.host.resetSystemAuthorizationContext();
        AuthorizationContext authCtx = this.host.assumeIdentity(userLink);
        // reset the auth context as we want it to be created via the auth service
        this.host.setAuthorizationContext(null);
        TestContext ctx = this.host.testCreate(1);
        // the completed GET op should see just one resource query spec
        this.host.send(Operation.createGet(this.host, UserService.FACTORY_LINK)
                .addRequestHeader(REQUEST_AUTH_TOKEN_HEADER, authCtx.getToken())
                .setCompletion((getOp, getEx) -> {
                    AuthorizationContext authContext = getOp.getAuthorizationContext();
                    Query q = authContext.getResourceQuery(Action.GET);
                    if (q.booleanClauses.size() == 1) {
                        ctx.completeIteration();
                        return;
                    }
                    ctx.failIteration(new IllegalStateException("Invalid resource query"));
                }));
        ctx.await();
        this.host.setSystemAuthorizationContext();
        // create a third user group with a name that matches the org prefix
        String userGroupLink3 = authHelper.createUserGroup(this.host,
                Utils.computeHash(ORG_ID) + "-userGroup3",
                Builder.create()
                    .addFieldClause(UserState.FIELD_NAME_EMAIL, USER)
                    .build());
        authHelper.createRole(this.host,
                userGroupLink3,
                resourceGroupLink,
                EnumSet.allOf(Action.class));
        this.host.resetSystemAuthorizationContext();
        this.host.assumeIdentity(userLink);
        this.host.setAuthorizationContext(null);
        TestContext newCtx = this.host.testCreate(1);
        // we should now see two resource query spec objects
        this.host.send(Operation.createGet(this.host, UserService.FACTORY_LINK)
                .addRequestHeader(REQUEST_AUTH_TOKEN_HEADER, authCtx.getToken())
                .setCompletion((getOp, getEx) -> {
                    AuthorizationContext authContext = getOp.getAuthorizationContext();
                    Query q = authContext.getResourceQuery(Action.GET);
                    if (q.booleanClauses.size() == 2) {
                        newCtx.completeIteration();
                        return;
                    }
                    newCtx.failIteration(new IllegalStateException("Invalid resource query"));
                }));
        newCtx.await();
    }
}
