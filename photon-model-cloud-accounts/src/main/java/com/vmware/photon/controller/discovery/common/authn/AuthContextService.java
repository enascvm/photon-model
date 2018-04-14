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

import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.CSP_ORG_ID;
import static com.vmware.photon.controller.discovery.common.PhotonControllerCloudAccountUtils.failOperation;
import static com.vmware.photon.controller.discovery.endpoints.EndpointConstants.SEPARATOR;
import static com.vmware.photon.controller.model.resources.util.PhotonModelUtils.PARTITION_ID_HEADER;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.xenon.common.Claims;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.AuthorizationContext;
import com.vmware.xenon.common.ServiceHost.ServiceNotFoundException;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthorizationContextServiceHelper;
import com.vmware.xenon.services.common.AuthorizationContextServiceHelper.AuthServiceContext;
import com.vmware.xenon.services.common.GuestUserService;
import com.vmware.xenon.services.common.QueryFilter;
import com.vmware.xenon.services.common.QueryTask.Query;

/**
 * The authorization context service takes an operation's authorization context and
 * populates it with the user's roles and the associated resource queries.
 *
 * The service is also responsible for determining if an incoming request should be
 * processed in-line as part of the service dispatch workflow
 *
 * This class filters the resource queries based on the the organization the user
 * is logged into. The CSP ordId is assumed to be available as a property in the
 * Claims object. All UserGroup objects in symphony are keyed based on the CSP
 * orgId. This service uses filters out all user groups that do not belong to
 * the logged in org
 */
public class AuthContextService extends StatelessService {

    public static final String SELF_LINK = UriPaths.AUTH_CONTEXT_SERVICE;

    public AuthContextService() {
        this.context = new AuthServiceContext(this,
                // BiFunction that is responsible for filtering userGroups
                (op, userGroupLinks) -> {
                    String orgId = getAuthContextOrgId(op.getAuthorizationContext());
                    // disable filtering when no orgLink is specified as an
                    // interim step till we enforce org specific logins
                    if (orgId == null) {
                        return userGroupLinks;
                    }
                    String orgHash = Utils.computeHash(orgId);
                    List<String> filteredUserGroupLinks = new ArrayList<>();
                    for (String userGroupLink : userGroupLinks) {
                        if (UriUtils.getLastPathSegment(userGroupLink).startsWith(orgHash)) {
                            filteredUserGroupLinks.add(userGroupLink);
                        }
                    }
                    return filteredUserGroupLinks;
                },
                // Consumer to be invoked on a cache clear request
                (requestBody) -> {
                    synchronized (this.pendingOperationsBySubjectAndOrg) {
                        Map<String, Collection<Operation>> pendingRequestsForKey =
                                getEntriesWithPrefix(requestBody.subjectLink);
                        if (!pendingRequestsForKey.isEmpty()) {
                            this.cacheClearRequests.addAll(pendingRequestsForKey.keySet());
                        }
                    }
                });
    }

    // Collection of pending operations as we already have a request
    private final NavigableMap<String, Collection<Operation>> pendingOperationsBySubjectAndOrg = new TreeMap<>();
    // auth service context used to pass the context of this service to AuthorizationContextServiceHelper
    private final AuthServiceContext context;
    // Collection of requests to clear the cache for a subject
    private final Set<String> cacheClearRequests = Collections.synchronizedSet(new HashSet<>());

    /**
     * The service host will invoke this method to allow a service to handle
     * the request in-line or indicate it should be scheduled by service host.
     *
     * @return True if the request has been completed in-line.
     *         False if the request should be scheduled for execution by the service host.
     */
    @Override
    public boolean queueRequest(Operation op) {
        return AuthorizationContextServiceHelper.queueRequest(op, this.context);
    }

    /**
     * Helper utility to retrieve the `orgId` claim property from an authorization context.
     * @param authorizationContext An authorization context.
     * @return An `orgId` value if present. null, otherwise.
     */
    public static String getAuthContextOrgId(AuthorizationContext authorizationContext) {
        if (authorizationContext == null || authorizationContext.getClaims() == null ||
                authorizationContext.getClaims().getProperties() == null) {
            return null;
        }

        return authorizationContext.getClaims().getProperties().get(CSP_ORG_ID);
    }

    private Map<String, Collection<Operation>> getEntriesWithPrefix(String prefix) {
        return this.pendingOperationsBySubjectAndOrg.subMap(prefix, prefix + Character.MAX_VALUE);
    }

    private String qualifySubjectWithOrg(Claims claims) {
        StringBuffer returnStr = new StringBuffer(claims.getSubject());
        if (claims.getProperties().containsKey(CSP_ORG_ID)) {
            returnStr.append(SEPARATOR);
            returnStr.append(claims.getProperties().get(CSP_ORG_ID));
        }
        return returnStr.toString();
    }

    @Override
    public void handleRequest(Operation op) {
        if (op.getAction() == Action.DELETE && op.getUri().getPath().equals(getSelfLink())) {
            super.handleRequest(op);
            return;
        }
        AuthorizationContext ctx = op.getAuthorizationContext();
        if (ctx == null) {
            op.fail(new IllegalArgumentException("no authorization context"));
            return;
        }

        Claims claims = ctx.getClaims();
        if (claims == null) {
            op.fail(new IllegalArgumentException("no claims"));
            return;
        }

        // Workaround for a nullpointer in Xenon when stateless service is not loaded in memory.
        String userLink = claims.getSubject();
        if (userLink != null && userLink.equals(GuestUserService.SELF_LINK)
                && !this.getHost().checkServiceAvailable(GuestUserService.SELF_LINK)) {
            failOperation(op, Operation.STATUS_CODE_UNAVAILABLE,
                    "Service is currently unavailable.");
            return;
        }
        //if (!claims.getProperties().containsKey(SymphonyConstants.CSP_ORG_ID)) {
        //    op.fail(new IllegalArgumentException("Claims does not have an associated organization"));
        //    return;
        //}

        // Add operation to collection of operations for this user.
        // Only if there was no collection for this subject will the routine
        // to gather state and roles for the subject be kicked off.
        String qualifiedSubject = qualifySubjectWithOrg(claims);
        synchronized (this.pendingOperationsBySubjectAndOrg) {
            Collection<Operation> pendingOperations = this.pendingOperationsBySubjectAndOrg.get(qualifiedSubject);
            if (pendingOperations != null) {
                pendingOperations.add(op);
                return;
            }

            // Nothing in flight for this subject yet, add new collection
            pendingOperations = new LinkedList<>();
            pendingOperations.add(op);
            this.pendingOperationsBySubjectAndOrg.put(qualifiedSubject, pendingOperations);
        }
        handlePopulateAuthContextCompletion(op);
        AuthorizationContextServiceHelper.populateAuthContext(op, this.context);
    }

    // this method nests a completion that will be invoked once the call to populateAuthContext
    // marks the input Operation complete. If the populated auth context can be used all
    // pending operations are notified; else we retry to populate the auth context again
    private void handlePopulateAuthContextCompletion(Operation op) {
        String qualifiedSubject = qualifySubjectWithOrg(op.getAuthorizationContext().getClaims());
        op.nestCompletion((nestOp, nestEx) -> {
            if (nestEx != null) {
                failThrowable(qualifiedSubject, nestEx, this.context);
                return;
            }
            if (this.cacheClearRequests.remove(qualifiedSubject)) {
                handlePopulateAuthContextCompletion(op);
                AuthorizationContextServiceHelper.populateAuthContext(op, this.context);
                return;
            }
            try {
                addCspOrgIdToOperation(nestOp);
            } catch (Exception e) {
                logSevere("Failed to add CSP org ID to operation: %s", e.getMessage());
                failThrowable(qualifiedSubject, e, this.context);
                return;
            }
            AuthorizationContext populatedAuthContext = nestOp.getAuthorizationContext();
            getHost().cacheAuthorizationContext(this, populatedAuthContext);
            completePendingOperations(qualifiedSubject, populatedAuthContext, this.context);
        });
    }

    private Collection<Operation> getPendingOperations(String subjectWithOrg, AuthServiceContext context) {
        Collection<Operation> operations;
        synchronized (this.pendingOperationsBySubjectAndOrg) {
            operations = this.pendingOperationsBySubjectAndOrg.remove(subjectWithOrg);
        }
        if (operations == null) {
            return Collections.emptyList();
        }

        return operations;
    }

    private void completePendingOperations(String subjectWithOrg, AuthorizationContext ctx,
            AuthServiceContext context) {
        for (Operation op : getPendingOperations(subjectWithOrg, context)) {
            this.setAuthorizationContext(op, ctx);
            op.complete();
        }
    }

    private void failThrowable(String subjectWithOrg, Throwable e, AuthServiceContext context) {
        if (e instanceof ServiceNotFoundException) {
            failNotFound(subjectWithOrg, context);
            return;
        }
        for (Operation op : getPendingOperations(subjectWithOrg, context)) {
            op.fail(e);
        }
    }

    private void failNotFound(String subjectWithOrg, AuthServiceContext context) {
        for (Operation op : getPendingOperations(subjectWithOrg, context)) {
            op.fail(Operation.STATUS_CODE_NOT_FOUND);
        }
    }

    private void addCspOrgIdToOperation(Operation op) throws GeneralSecurityException {
        if (op == null) {
            return;
        }

        AuthorizationContext authCtx = op.getAuthorizationContext();
        if (authCtx == null || authCtx.getClaims() == null) {
            return;
        }

        // Operation already has the csp org ID in its claims, do nothing
        String cspOrgId = authCtx.getClaims().getProperties().get(CSP_ORG_ID);
        if (cspOrgId != null && !cspOrgId.isEmpty()) {
            return;
        }

        // Check if csp org id has been passed as an header, else do nothing
        String cspOrgIdFromHeader = op.getRequestHeader(PARTITION_ID_HEADER);
        if (cspOrgIdFromHeader == null || cspOrgIdFromHeader.isEmpty()) {
            return;
        }

        Map<String, String> extraProperties = new HashMap<>();
        extraProperties.put(CSP_ORG_ID, cspOrgIdFromHeader);

        AuthorizationContext newAuthCtx = cloneAuthorizationContext(authCtx, extraProperties);
        op.setAuthorizationContext(newAuthCtx);
    }

    private AuthorizationContext cloneAuthorizationContext(AuthorizationContext authorizationContext,
            Map<String, String> extraProperties) {

        if (authorizationContext == null) {
            return authorizationContext;
        }

        Claims claims = cloneClaims(authorizationContext.getClaims(), extraProperties);

        AuthorizationContext.Builder ab = AuthorizationContext.Builder.create();
        ab.setClaims(claims);
        ab.setToken(authorizationContext.getToken());

        Map<Action, Query> queryByAction = new HashMap<>(Action.values().length);
        Map<Action, QueryFilter> queryFilterByAction = new HashMap<>(
                Action.values().length);
        for (Action action : Action.values()) {
            Query query = authorizationContext.getResourceQuery(action);
            if (query != null) {
                queryByAction.put(action, query);
            }

            QueryFilter queryFilter = authorizationContext.getResourceQueryFilter(action);
            if (queryFilter != null) {
                queryFilterByAction.put(action, queryFilter);
            }
        }

        if (queryByAction.isEmpty()) {
            queryByAction = null;
        }

        if (queryFilterByAction.isEmpty()) {
            queryFilterByAction = null;
        }

        ab.setResourceQueryMap(queryByAction);
        ab.setResourceQueryFilterMap(queryFilterByAction);

        return ab.getResult();
    }

    private Claims cloneClaims(Claims claims, Map<String, String> extraProperties) {
        if (claims == null) {
            return claims;
        }

        Claims.Builder builder = new Claims.Builder();

        builder.setIssuer(claims.getIssuer());
        builder.setSubject(claims.getSubject());
        // claims.getAudience() throws a NPE if it is empty. Needs a xenon change
        // builder.setAudience(new HashSet<>(claims.getAudience()));
        builder.setExpirationTime(claims.getExpirationTime());
        builder.setNotBefore(claims.getNotBefore());
        builder.setIssuedAt(claims.getIssuedAt());
        builder.setJwtId(claims.getJwtId());

        Map<String, String> properties = new HashMap<>(claims.getProperties());

        if (extraProperties != null) {
            properties.putAll(extraProperties);
        }

        builder.setProperties(properties);

        return builder.getResult();
    }
}
