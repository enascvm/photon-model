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

package com.vmware.photon.controller.discovery.onboarding;

import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.AUTOMATION_USER_EMAIL;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.CLIENT_ID_EMAIL_SUFFIX;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.CSP_ORG_ID;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.OAUTH_CLIENT_IDS;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.SERVICE_USER_EMAIL;
import static com.vmware.photon.controller.discovery.common.PhotonControllerCloudAccountUtils.computeHashWithSHA256;

import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.vmware.photon.controller.discovery.common.services.OrganizationService;
import com.vmware.photon.controller.discovery.common.services.ProjectService;
import com.vmware.photon.controller.discovery.common.services.UserContextQueryService;
import com.vmware.photon.controller.discovery.common.services.UserContextQueryService.UserContext;
import com.vmware.photon.controller.discovery.common.services.UserService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.xenon.common.Claims;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.AuthorizationContext;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.OperationJoin.JoinedCompletionHandler;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceStateCollectionUpdateRequest;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.ServiceSubscriptionState.ServiceSubscriber;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;
import com.vmware.xenon.services.common.ReliableSubscriptionService;
import com.vmware.xenon.services.common.ResourceGroupService;
import com.vmware.xenon.services.common.ResourceGroupService.ResourceGroupState;
import com.vmware.xenon.services.common.RoleService;
import com.vmware.xenon.services.common.RoleService.Policy;
import com.vmware.xenon.services.common.RoleService.RoleState;
import com.vmware.xenon.services.common.SystemUserService;
import com.vmware.xenon.services.common.TenantService;
import com.vmware.xenon.services.common.UserGroupService;
import com.vmware.xenon.services.common.UserGroupService.UserGroupState;
import com.vmware.xenon.services.common.UserService.UserState;

public class OnboardingUtils {

    public static final String FORBIDDEN = "forbidden";
    public static final String SEPARATOR = "-";
    public static final String ADMIN_SUFFIX = "admin";
    public static final String USER_SUFFIX = "user";
    public static final String TASK_SUFFIX = "task";
    public static final String RESOURCE_SUFFIX = "resource";
    public static final String DEFAULT_PROJECT_NAME = "Default Project";

    public static final String SERVICE_USER_LINK = normalizeLink(
            com.vmware.xenon.services.common.UserService.FACTORY_LINK,
            computeHashWithSHA256(SERVICE_USER_EMAIL));

    /**
     * Returns the automation user link.
     */
    public static String getAutomationUserLink() {
        return UriUtils.buildUriPath(com.vmware.xenon.services.common.UserService.FACTORY_LINK,
                computeHashWithSHA256(AUTOMATION_USER_EMAIL));
    }

    public static Operation createResourceGroup(Service service,
            String resourceGroupSelfLink, String queryPropertyName,
            String queryPropertyValue) {
        ResourceGroupState resourceGroupState = new ResourceGroupState();
        resourceGroupState.documentSelfLink = resourceGroupSelfLink;
        resourceGroupState.query = new Query();

        resourceGroupState.query.setTermPropertyName(queryPropertyName);
        resourceGroupState.query.setTermMatchValue(queryPropertyValue);
        resourceGroupState.query.setTermMatchType(MatchType.TERM);
        Operation resourceGroupOp = Operation
                .createPost(service.getHost(), ResourceGroupService.FACTORY_LINK)
                .setBody(resourceGroupState);
        service.setAuthorizationContext(resourceGroupOp, service.getSystemAuthorizationContext());
        return addReplicationQuorumHeader(resourceGroupOp);
    }

    public static Operation createRole(Service service, String roleSelfLink, String userGroupLink,
            String resourceGroupLink) {
        return createRole(service, roleSelfLink, userGroupLink, resourceGroupLink, EnumSet.allOf(Action.class));
    }

    public static Operation createRole(Service service, String roleSelfLink, String userGroupLink,
            String resourceGroupLink, Set<Action> verbs) {
        RoleState roleState = new RoleState();
        roleState.documentSelfLink = roleSelfLink;
        roleState.userGroupLink = userGroupLink;
        roleState.resourceGroupLink = resourceGroupLink;
        roleState.verbs = verbs;
        roleState.policy = Policy.ALLOW;

        Operation roleOp = Operation
                .createPost(service.getHost(), RoleService.FACTORY_LINK)
                .setBody(roleState);
        service.setAuthorizationContext(roleOp, service.getSystemAuthorizationContext());
        return addReplicationQuorumHeader(roleOp);
    }

    /**
     * Helper to build the documentSelfLink for a symphony authz artifact like UserGroup and ResourceGroup
     * @param entityName the entity name
     * @param isAdmin is the link for an admin
     * @return
     */
    public static String buildAuthzArtifactLink(String entityName, boolean isAdmin) {
        if (isAdmin) {
            return new StringBuffer(entityName)
                    .append(SEPARATOR).append(ADMIN_SUFFIX).toString();
        } else {
            return new StringBuffer(entityName)
                    .append(SEPARATOR).append(USER_SUFFIX).toString();
        }
    }

    /**
     * This method makes sure that the documentSelfLink is always associated with factory as the
     * prefix.
     */
    public static String normalizeLink(String factoryLink, String documentLink) {
        if (UriUtils.isChildPath(documentLink, factoryLink)) {
            return documentLink;
        }

        String lastPathSegment = UriUtils.getLastPathSegment(documentLink);
        return UriUtils.buildUriPath(factoryLink, lastPathSegment);
    }

    /**
     * Check to see if the specified user is an a valid user in the system
     * @param service service invoking this operation. This service has to be a privileged service
     * @param userLink userLink of the user to check for
     * @param tenantLink tenant to check against
     * @param checkForAdmin check if the user is an admin
     * @param success Consumer to be invoked on success
     * @param failure Consumer to be invoked on failire
     */
    public static void checkIfUserIsValid(Service service, String userLink, String tenantLink, boolean checkForAdmin,
            Consumer<Operation> success, Consumer<Throwable> failure) {
        Operation getUserOp = Operation.createGet(service, userLink)
                .setCompletion(
                        (getOp, getEx) -> {
                            if (getEx != null) {
                                failure.accept(getEx);
                                return;
                            }
                            UserState userState = getOp.getBody(UserState.class);
                            String tenantAdminLink = UriUtils.buildUriPath(
                                    UserGroupService.FACTORY_LINK,
                                    buildAuthzArtifactLink(UriUtils
                                            .getLastPathSegment(tenantLink), true));
                            String tenantUserLink = UriUtils.buildUriPath(
                                    UserGroupService.FACTORY_LINK,
                                    buildAuthzArtifactLink(UriUtils
                                            .getLastPathSegment(tenantLink), false));
                            if (checkForAdmin) {
                                if (userState.userGroupLinks != null &&
                                        userState.userGroupLinks.contains(tenantAdminLink)) {
                                    success.accept(getOp);
                                    return;
                                }
                            } else {
                                if (userState.userGroupLinks != null &&
                                        (userState.userGroupLinks.contains(tenantAdminLink)
                                                || userState.userGroupLinks.contains(tenantUserLink))) {
                                    success.accept(getOp);
                                    return;
                                }
                            }
                            failure.accept(new IllegalAccessError(FORBIDDEN));
                            return;
                        });
        service.setAuthorizationContext(addReplicationQuorumHeader(getUserOp), service.getSystemAuthorizationContext());
        service.sendRequest(getUserOp);
    }

    /**
     * Parse the system {@link SymphonyConstants#OAUTH_CLIENT_IDS} system property to convert to a
     * {@link Set} of unique client ids.
     */
    public static Set<String> getSystemOauthClientIds() {
        String clientIds = System.getProperty(OAUTH_CLIENT_IDS);
        if (clientIds == null || clientIds.isEmpty()) {
            return new HashSet<>();
        }

        String[] parsedClientIds = clientIds.split(",");
        return new HashSet<>(Arrays.asList(parsedClientIds));
    }

    public static String buildUserIdFromClientId(String clientId) {
        return clientId + CLIENT_ID_EMAIL_SUFFIX;
    }

    /**
     * Build the factory links for each client credential user in the system and return in a set.
     */
    public static Set<String> getSystemOauthClientIdLinks() {
        Set<String> clientLinks = new HashSet<>();
        for (String clientId : getSystemOauthClientIds()) {
            String clientUserId = buildUserIdFromClientId(clientId);
            clientLinks.add(normalizeLink(com.vmware.xenon.services.common.UserService.FACTORY_LINK,
                    computeHashWithSHA256(clientUserId)));
        }
        return clientLinks;
    }

    /**
     * Build a tenantLink based on the userGroupLink
     * Symphony creates userGroups based on the roles for a user
     * @param userGroupLink
     * @return tenantLink
     */
    public static String getTenantLinkFromUserGroup(String userGroupLink) {
        String groupName = userGroupLink.substring(
                (UserGroupService.FACTORY_LINK.length() + 1), userGroupLink.lastIndexOf(SEPARATOR));
        return UriUtils.buildUriPath(TenantService.FACTORY_LINK, groupName);
    }

    public static Set<Operation> createUserGroups(Service service, String teamLink) {
        Set<Operation> operations = new HashSet<Operation>();
        UserGroupState adminUserGroupState = new UserGroupState();
        String teamId = UriUtils.getLastPathSegment(teamLink);
        adminUserGroupState.documentSelfLink = OnboardingUtils.buildAuthzArtifactLink(
                teamId, true);
        adminUserGroupState.query = new Query();
        adminUserGroupState.query.setTermPropertyName(QuerySpecification
                .buildCollectionItemName(UserState.FIELD_NAME_USER_GROUP_LINKS));
        adminUserGroupState.query.setTermMatchType(MatchType.TERM);
        adminUserGroupState.query.setTermMatchValue(UriUtils.buildUriPath(
                UserGroupService.FACTORY_LINK,
                adminUserGroupState.documentSelfLink));
        Operation adminUserGroupOp = Operation
                .createPost(service.getHost(), UserGroupService.FACTORY_LINK)
                .setBody(adminUserGroupState);
        service.setAuthorizationContext(adminUserGroupOp, service.getSystemAuthorizationContext());
        operations.add(addReplicationQuorumHeader(adminUserGroupOp));

        UserGroupState nonAdminUserGroupState = new UserGroupState();
        nonAdminUserGroupState.documentSelfLink = OnboardingUtils.buildAuthzArtifactLink(
                teamId, false);
        nonAdminUserGroupState.query = new Query();
        nonAdminUserGroupState.query.setTermPropertyName(QuerySpecification
                .buildCollectionItemName(UserState.FIELD_NAME_USER_GROUP_LINKS));
        nonAdminUserGroupState.query.setTermMatchType(MatchType.TERM);
        nonAdminUserGroupState.query.setTermMatchValue(UriUtils.buildUriPath(
                UserGroupService.FACTORY_LINK,
                nonAdminUserGroupState.documentSelfLink));
        Operation nonAdminUserGroupOp = Operation
                .createPost(service.getHost(), UserGroupService.FACTORY_LINK)
                .setBody(nonAdminUserGroupState);
        service.setAuthorizationContext(nonAdminUserGroupOp, service.getSystemAuthorizationContext());
        operations.add(addReplicationQuorumHeader(nonAdminUserGroupOp));
        return operations;
    }

    public static Operation addReplicationQuorumHeader(Operation inputOp) {
        inputOp.addRequestHeader(Operation.REPLICATION_QUORUM_HEADER,
                Operation.REPLICATION_QUORUM_HEADER_VALUE_ALL);
        return inputOp;
    }

    /**
     * Returns the project links from the user context.
     *
     * @param service The service executing the call.
     * @param completionHandler The completion handler
     */
    public static void getProjectLinks(Service service,
            BiConsumer<List<String>, Throwable> completionHandler) {
        Operation.createGet(service.getHost(), UserContextQueryService.SELF_LINK)
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        completionHandler.accept(null, ex);
                        return;
                    }

                    UserContext userContext = op.getBody(UserContext.class);
                    List<String> projectLinks = userContext.projects.stream()
                            .map(resourceGroupState -> resourceGroupState.documentSelfLink)
                            .collect(Collectors.toList());
                    completionHandler.accept(projectLinks, null);
                })
                .sendWith(service);
    }

    /**
     * Returns the active user link based on operation context and current state userlink.
     *
     * @param userLink A userLink to defer to if the logged in user is a system user.
     * @param op An operation.
     * @return The `userLink` if the operation's authorization context is logged in as the system
     * user and the `userLink` is valid, otherwise the operation's authorization user (presumed to
     * be the logged in user).
     */
    public static String getActiveUserLink(String userLink, Operation op) {
        String loggedInUserLink = op.getAuthorizationContext().getClaims().getSubject();
        if (userLink != null && userLink.startsWith(UserService.FACTORY_LINK)
                && loggedInUserLink.equals(SystemUserService.SELF_LINK)) {
            return userLink;
        } else {
            return loggedInUserLink;
        }
    }

    /**
     * Obtain the documentSelfLink for a project with a specified name
     * @param orgLink documentSelfLink for the org
     * @param projectName Name of the project
     * @return
     */
    public static final String getProjectSelfLink(String orgLink, String projectName) {
        return UriUtils.buildUriPath(ProjectService.FACTORY_LINK,
                UriUtils.getLastPathSegment(orgLink).concat(OnboardingUtils.SEPARATOR)
                        .concat(Utils.computeHash(projectName)));
    }

    /**
     * Obtain the documentSelfLink for a project with a specified name
     * @param orgLink documentSelfLink for the org
     */
    public static final String getDefaultProjectSelfLink(String orgLink) {
        return getProjectSelfLink(orgLink, DEFAULT_PROJECT_NAME);
    }

    /**
     * Obtain the name of the default project
     */
    public static final String getDefaultProjectName() {
        return DEFAULT_PROJECT_NAME;
    }

    /**
     * Inject user identity into operation context.
     *
     * @param service         the service invoking the operation
     * @param op              operation for which the auth context needs to be set
     * @param userServicePath user document link
     * @throws GeneralSecurityException any generic security exception
     */
    public static AuthorizationContext assumeIdentity(Service service, Operation op,
            String userServicePath)
            throws GeneralSecurityException {
        return assumeIdentity(service, op, userServicePath, null);
    }

    /**
     * Inject user identity into operation context.
     *
     * @param service         the service invoking the operation
     * @param op              operation for which the auth context needs to be set
     * @param userServicePath user document link
     * @param orgId           The user's organization ID (CSP org ID)
     * @throws GeneralSecurityException any generic security exception
     */
    public static AuthorizationContext assumeIdentity(Service service, Operation op,
            String userServicePath, String orgId) throws GeneralSecurityException {
        Map<String, String> properties = new HashMap<>();
        if (orgId != null) {
            properties.put(CSP_ORG_ID, orgId);
        }

        Claims.Builder builder = new Claims.Builder();
        builder.setSubject(userServicePath);
        builder.setProperties(properties);
        Claims claims = builder.getResult();
        String token;
        // The following is a workaround because the token signer method is not
        // present in the service base class.
        if (service instanceof StatelessService) {
            token = ((StatelessService) service).getTokenSigner().sign(claims);
        } else if (service instanceof StatefulService) {
            token = ((StatefulService) service).getTokenSigner().sign(claims);
        } else {
            throw new IllegalArgumentException("Unsupported service type.");
        }
        AuthorizationContext.Builder ab = AuthorizationContext.Builder.create();
        ab.setClaims(claims);
        ab.setToken(token);

        // Associate resulting authorization context with this thread
        AuthorizationContext authContext = ab.getResult();
        service.setAuthorizationContext(op, authContext);
        return authContext;
    }

    /**
     * Sets a metric with unit and value in the ServiceStat associated with the service
     */
    public static void setStat(Service service, String name, String unit, double value) {
        service.getHost().log(Level.INFO, "Setting stat [service=%s] [name=%s] [unit=%s] [value=%f]",
                service.getClass(), name, unit, value);
        ServiceStats.ServiceStat stat = new ServiceStats.ServiceStat();
        stat.name = name;
        stat.unit = unit;
        service.setStat(stat, value);
    }

    /**
     * Adjusts a metric with value in the ServiceStat associated with the service
     */
    public static void adjustStat(Service service, String name, double value) {
        service.getHost().log(Level.INFO, "Adjusting stat [service=%s] [name=%s] [value=%f]",
                service.getClass(), name, value);
        service.adjustStat(name, value);
    }

    /**
     * Returns orgId based on projectLink or orgLink.
     */
    public static String getOrgId(String link) {
        String linkLastPath = UriUtils.getLastPathSegment(link);
        if (link.startsWith(OrganizationService.FACTORY_LINK)) {
            return linkLastPath;
        } else if (link.startsWith(ProjectService.FACTORY_LINK)) {
            String[] tokens = linkLastPath.split(SEPARATOR);
            if (tokens.length > 1) {
                return tokens[0];
            }
        }
        return null;
    }

    /**
     * Returns org ID based on a list of tenantLinks.
     */
    public static String getOrgId(Collection<String> tenantLinks) {
        for (String link : tenantLinks) {
            String orgId = getOrgId(link);
            if (orgId != null) {
                return orgId;
            }
        }
        return null;
    }

    /**
     * Returns orgId based on endpoint state.
     */
    public static String getOrgId(EndpointState endpointState) {
        if (endpointState.tenantLinks != null && !endpointState.tenantLinks.isEmpty()) {
            return getOrgId(endpointState.tenantLinks);
        }
        return null;
    }

    /**
     * Helper routine to subscribe to notifications
     * @param host service host to invoke the operation
     * @param onSuccessConsumer consumer callback to invoke on notification
     * @param onFailureConsumer consumer callback to invoke on failure
     * @param taskLink link to the task to subscribe to
     */
    public static void subscribeToNotifications(ServiceHost host,
            Consumer<Operation> onSuccessConsumer,
            Consumer<Throwable> onFailureConsumer,
            String taskLink) {
        ServiceSubscriber subscribeBody = new ServiceSubscriber();
        subscribeBody.replayState = true;
        subscribeBody.usePublicUri = true;
        Operation subscribeOp = Operation
                .createPost(host, taskLink)
                .setReferer(host.getUri())
                .setCompletion(
                        (regOp, regEx) -> {
                            if (regEx != null) {
                                onFailureConsumer.accept(regEx);
                            }
                        });
        ReliableSubscriptionService notificationTarget = ReliableSubscriptionService.create(
                subscribeOp, subscribeBody, onSuccessConsumer);
        host.startSubscriptionService(subscribeOp, notificationTarget, subscribeBody);
    }

    /**
     * Generate the update request body for userGroupLinks.
     * @param groupLinksToAdd : groupLinks to added
     * @param groupLinksToRemove : groupLinks to removed
     * @return : request body
     */
    public static ServiceStateCollectionUpdateRequest generateGroupLinkUpdateRequestBody(Set<String> groupLinksToAdd,
            Set<String> groupLinksToRemove) {
        Map<String, Collection<Object>> itemsToAdd = null;
        Map<String, Collection<Object>> itemsToRemove = null;
        if (groupLinksToAdd != null) {
            itemsToAdd = Collections
                    .singletonMap(UserService.UserState.FIELD_NAME_USER_GROUP_LINKS,
                            new HashSet<>(groupLinksToAdd));
        }

        if (groupLinksToRemove != null) {
            itemsToRemove = Collections
                    .singletonMap(UserService.UserState.FIELD_NAME_USER_GROUP_LINKS,
                            new HashSet<>(groupLinksToRemove));
        }

        return ServiceStateCollectionUpdateRequest.create(itemsToAdd, itemsToRemove);
    }

    /**
     * Util method to clone RoleState.
     * @param originalState
     * @return
     */
    public static RoleState cloneRoleState(RoleState originalState) {
        return RoleState.Builder
                .create()
                .withPolicy(originalState.policy)
                .withResourceGroupLink(originalState.resourceGroupLink)
                .withVerbs(originalState.verbs)
                .withUserGroupLink(originalState.userGroupLink)
                .build();
    }

    /**
     * Util method to clone ResourceGroupState.
     * @param originalState
     * @return
     */
    public static ResourceGroupState cloneResourceGroupState(ResourceGroupState originalState) {
        return ResourceGroupState.Builder
                .create()
                .withQuery(originalState.query)
                .build();
    }

    /**
     * Util method to clone UserGroupState.
     * @param originalState
     * @return
     */
    public static UserGroupState cloneUserGroupState(UserGroupState originalState) {
        return UserGroupState.Builder
                .create()
                .withQuery(originalState.query)
                .build();
    }

    /**
     * Util method to call set of operations and send results to joinCompletionHandler
     */
    public static void callOperations(Service service, Set<Operation> operationSet,
            JoinedCompletionHandler jh) {
        if (operationSet.isEmpty()) {
            jh.handle(null, null);
            return;
        }
        OperationContext opCtx = OperationContext.getOperationContext();
        operationSet.forEach(op -> service.setAuthorizationContext(op, service.getSystemAuthorizationContext()));
        OperationJoin.create(operationSet).setCompletion((ops, exc) -> {
            OperationContext.restoreOperationContext(opCtx);
            jh.handle(ops, exc);
        }).sendWith(service);
    }

    /**
     * Constructs a generic roll-back operation for a particular service. If a rollbackBody is
     * specified, then a PATCH operation will be constructed against the service. Otherwise,
     * a DELETE will be created.
     * @param service : service to call
     * @param documentLink : Service State documentSelfLink or Service SelfLink
     * @param rollbackBody : roll back body
     * @param cd : original Service document
     * @return
     */
    public static Operation createRollBackOperation(Service service, String documentLink, Object
            rollbackBody, ServiceDocument cd) {
        if (rollbackBody == null) {
            cd.documentExpirationTimeMicros = System.currentTimeMillis();
            return Operation.createPut(service, documentLink).setBody(cd);
        }

        return Operation.createPatch(service, documentLink).setBody(rollbackBody);
    }

    /**
     * Unsubscribe notifications.
     *
     * @param host service host to invoke the operation
     * @param publisherLink the notification publisher link
     * @param notificationTarget the notification target link
     */
    public static void unsubscribeNotifications(ServiceHost host, String publisherLink,
            URI notificationTarget) {
        host.stopSubscriptionService(
                Operation.createDelete(host, publisherLink)
                        .setReferer(host.getUri()),
                notificationTarget);
    }

    /**
     * Util function to call `Patch` operation to UserService
     * @param service : the service
     * @param userLink : user link
     * @param patchBody : request patch body
     * @param patchUserConsumer : Consumer to handler the results
     */
    public static void patchUserService(Service service, String userLink, Object patchBody,
            BiConsumer<Operation, Throwable> patchUserConsumer) {
        OperationContext ctx = OperationContext.getOperationContext();
        Operation userStateOp = Operation.createPatch(service, userLink)
                .setBody(patchBody)
                .setCompletion((userPatchOp, userPatchEx) -> {
                    OperationContext.restoreOperationContext(ctx);
                    if (userPatchEx != null) {
                        patchUserConsumer.accept(null, userPatchEx);
                        return;
                    }
                    patchUserConsumer.accept(userPatchOp, null);
                });
        service.setAuthorizationContext(OnboardingUtils.addReplicationQuorumHeader(userStateOp),
                service.getSystemAuthorizationContext());
        service.sendRequest(userStateOp);
    }

}

