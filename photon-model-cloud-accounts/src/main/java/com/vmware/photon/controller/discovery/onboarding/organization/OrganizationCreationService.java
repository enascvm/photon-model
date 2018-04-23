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

package com.vmware.photon.controller.discovery.onboarding.organization;

import static com.vmware.photon.controller.discovery.onboarding.OnboardingUtils.RESOURCE_SUFFIX;
import static com.vmware.photon.controller.discovery.onboarding.OnboardingUtils.TASK_SUFFIX;
import static com.vmware.photon.controller.discovery.onboarding.OnboardingUtils.callOperations;
import static com.vmware.photon.controller.discovery.onboarding.OnboardingUtils.cloneResourceGroupState;
import static com.vmware.photon.controller.discovery.onboarding.OnboardingUtils.cloneRoleState;
import static com.vmware.photon.controller.discovery.onboarding.OnboardingUtils.cloneUserGroupState;
import static com.vmware.photon.controller.discovery.onboarding.OnboardingUtils.createRollBackOperation;
import static com.vmware.photon.controller.discovery.onboarding.OnboardingUtils.generateGroupLinkUpdateRequestBody;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.vmware.photon.controller.discovery.common.services.OrganizationService;
import com.vmware.photon.controller.discovery.common.services.OrganizationService.OrganizationState;
import com.vmware.photon.controller.discovery.common.services.ProjectService;
import com.vmware.photon.controller.discovery.common.services.UserService.UserState;
import com.vmware.photon.controller.discovery.common.utils.ErrorUtil;
import com.vmware.photon.controller.discovery.onboarding.OnboardingErrorCode;
import com.vmware.photon.controller.discovery.onboarding.OnboardingUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationSequence;
import com.vmware.xenon.common.RequestRouter.Route;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocument.Documentation;
import com.vmware.xenon.common.ServiceDocument.UsageOption;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.GuestUserService;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;
import com.vmware.xenon.services.common.ResourceGroupService;
import com.vmware.xenon.services.common.ResourceGroupService.ResourceGroupState;
import com.vmware.xenon.services.common.RoleService;
import com.vmware.xenon.services.common.RoleService.RoleState;
import com.vmware.xenon.services.common.SystemUserService;
import com.vmware.xenon.services.common.UserGroupService;
import com.vmware.xenon.services.common.UserGroupService.UserGroupState;

/**
 * Creates an organization within symphony. This is a top level construct that
 * ties to a billing account
 */
public class OrganizationCreationService extends StatelessService {

    public static final String SELF_LINK =
            UriPaths.PROVISIONING_ORGANIZATION_SERVICE + "/creation";

    /**
     * Data object to create an organization
     */
    public static class OrganizationCreationRequest {
        @Documentation(description = "Organization id")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String orgId;

        @Documentation(description = "The name for the organization")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String organizationName;

        @Documentation(description = "The display name for the organization")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String displayName;

        @Documentation(description = "User who will be part of the organization")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String userLink;
    }

    @Override
    public void handleRequest(Operation op) {
        switch (op.getAction()) {
        case POST:
            handlePost(op);
            break;
        default:
            super.handleRequest(op);
        }
    }

    @Override
    public void authorizeRequest(Operation op) {
        // TODO VSYM-958: any authenticated user can create an org today;
        // This will be streamlined and an approval process put in soon
        if (op.getAuthorizationContext() != null &&
                !op.getAuthorizationContext().getClaims().getSubject()
                        .equals(GuestUserService.SELF_LINK)) {
            op.complete();
            return;
        }
        op.fail(Operation.STATUS_CODE_FORBIDDEN);
    }

    public void handlePost(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalStateException("No body specified"));
            return;
        }

        OrganizationCreationRequest orgData = op.getBody(OrganizationCreationRequest.class);
        if (!isValid(op, orgData)) {
            return;
        }

        // record list of operation associate to the process of user creation for cache.
        Set<Operation> transactionSet = new HashSet<>();
        OrganizationState organizationState = buildOrgState(orgData);

        // all of this needs to happen as a transaction
        Operation userOp = Operation
                .createPost(getHost(), OrganizationService.FACTORY_LINK)
                .setBody(organizationState)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        if (Operation.STATUS_CODE_CONFLICT == o.getStatusCode()) {
                            op.setStatusCode(o.getStatusCode());
                            op.fail(e);
                            return;
                        }
                    }
                    OrganizationState updatedOrgState = o.getBody(OrganizationState.class);
                    transactionSet.add(createRollBackOperation(this, updatedOrgState.documentSelfLink,
                            null, new OrganizationState()));
                    // TODO: postOrganizationTelemetry(this, updatedOrgState);
                    createAuthServicesForOrg(op, updatedOrgState, orgData, transactionSet);
                });
        setAuthorizationContext(OnboardingUtils.addReplicationQuorumHeader(userOp),
                getSystemAuthorizationContext());
        sendRequest(userOp);
    }

    private OrganizationState buildOrgState(OrganizationCreationRequest request) {
        OrganizationState orgState = new OrganizationState();
        orgState.name = request.organizationName;

        if (request.orgId == null || request.orgId.isEmpty()) {
            orgState.id = UUID.randomUUID().toString();
        } else {
            orgState.id = request.orgId;
        }

        if (request.displayName == null || request.displayName.isEmpty()) {
            orgState.displayName = request.organizationName;
        } else {
            orgState.displayName = request.displayName;
        }

        orgState.documentSelfLink = Utils.computeHash(orgState.id);
        return orgState;
    }

    private boolean isValid(Operation op, OrganizationCreationRequest request) {
        if (request.organizationName == null || request.organizationName.isEmpty()) {
            op.setBody(ErrorUtil.create(OnboardingErrorCode.ORG_NAME_NULL));
            op.fail(Operation.STATUS_CODE_BAD_REQUEST);
            return false;
        }
        return true;
    }

    private void createAuthServicesForOrg(Operation op, OrganizationState organizationState,
            OrganizationCreationRequest orgData, Set<Operation> transactionSet) {

        // create user groups for the org - create one for the org admin
        // and another for a non-admin user
        String tenantLink = UriUtils.getLastPathSegment(organizationState.documentSelfLink);
        Set<Operation> userGroupOps = OnboardingUtils.createUserGroups(this, tenantLink);

        OperationSequence opSequence =
                OperationSequence.create(userGroupOps.stream().toArray(Operation[]::new));
        transactionSet.addAll(userGroupOps.stream()
                .map(userGroupOpLink -> userGroupOpLink.getBody(UserGroupState.class))
                .map(userGroupOpState -> createRollBackOperation(this,
                        UriUtils.buildUriPath(UserGroupService.FACTORY_LINK, userGroupOpState.documentSelfLink),
                        null, cloneUserGroupState(userGroupOpState)))
                .collect(Collectors.toSet()));

        // Create resource group for all resources owned directly and indirectly(via projects) by this org.
        Set<Operation> resourceGroupOps = new HashSet<Operation>();
        String orgAdminResourceGroupSelfLink = OnboardingUtils.buildAuthzArtifactLink(tenantLink, true);
        ResourceGroupState adminResourceGroupState = new ResourceGroupState();
        adminResourceGroupState.documentSelfLink = orgAdminResourceGroupSelfLink;
        adminResourceGroupState.query = Query.Builder.create()
                .addCollectionItemClause(ServiceDocumentDescription.FIELD_NAME_TENANT_LINKS,
                        UriUtils.buildUriPath(OrganizationService.FACTORY_LINK, tenantLink), Occurance.SHOULD_OCCUR)
                .addFieldClause(QuerySpecification.buildCollectionItemName(ServiceDocumentDescription.FIELD_NAME_TENANT_LINKS),
                        UriUtils.buildUriPath(ProjectService.FACTORY_LINK, tenantLink), MatchType.PREFIX,
                                Occurance.SHOULD_OCCUR)
                .build();
        Operation orgAdminResourceGroup = Operation
                .createPost(getHost(), ResourceGroupService.FACTORY_LINK)
                .setBody(adminResourceGroupState);
        setAuthorizationContext(orgAdminResourceGroup, getSystemAuthorizationContext());
        resourceGroupOps.add(OnboardingUtils.addReplicationQuorumHeader(orgAdminResourceGroup));

        // add a resource group for resources owned by the org - these are projects and environments.
        String resourceLink = new StringBuilder(tenantLink).append(OnboardingUtils.SEPARATOR).append(RESOURCE_SUFFIX).toString();
        ResourceGroupState resourceResourceGroupState = new ResourceGroupState();
        resourceResourceGroupState.documentSelfLink = resourceLink;
        resourceResourceGroupState.query = Query.Builder.create().addCollectionItemClause(ServiceDocumentDescription.FIELD_NAME_TENANT_LINKS,
                UriUtils.buildUriPath(
                        OrganizationService.FACTORY_LINK, tenantLink)).build();
        Operation resourceResourceGroup = Operation
                .createPost(getHost(), ResourceGroupService.FACTORY_LINK)
                .setBody(resourceResourceGroupState);
        setAuthorizationContext(resourceResourceGroup, getSystemAuthorizationContext());
        resourceGroupOps.add(OnboardingUtils.addReplicationQuorumHeader((resourceResourceGroup)));

        // add a resource group for tasks owned by the org - these are currently all project tasks.
        String taskLink = new StringBuilder(tenantLink).append(OnboardingUtils.SEPARATOR).append(TASK_SUFFIX).toString();
        ResourceGroupState taskResourceGroupState = new ResourceGroupState();
        taskResourceGroupState.documentSelfLink = taskLink;
        Query taskClause = Query.Builder.create()
                .addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                        UriPaths.PROVISIONING_PROJECT_TASKS_PREFIX,
                        MatchType.PREFIX, Occurance.SHOULD_OCCUR)
                 .build();
        taskResourceGroupState.query = Query.Builder.create()
                .addFieldClause(QuerySpecification
                        .buildCollectionItemName(ServiceDocumentDescription.FIELD_NAME_TENANT_LINKS),  UriUtils.buildUriPath(
                        OrganizationService.FACTORY_LINK, tenantLink))
                        .addClause(taskClause)
                .build();
        Operation taskResourceGroup = Operation
                .createPost(getHost(), ResourceGroupService.FACTORY_LINK)
                .setBody(taskResourceGroupState);
        setAuthorizationContext(taskResourceGroup, getSystemAuthorizationContext());
        resourceGroupOps.add(OnboardingUtils.addReplicationQuorumHeader((taskResourceGroup)));
        transactionSet.addAll(resourceGroupOps.stream()
                .map(resourceGroupOp -> resourceGroupOp.getBody(ResourceGroupState.class))
                .map(resourceGroupState -> createRollBackOperation(this,
                        UriUtils.buildUriPath(ResourceGroupService.FACTORY_LINK, resourceGroupState.documentSelfLink),
                        null, cloneResourceGroupState(resourceGroupState)))
                .collect(Collectors.toSet()));
        opSequence = opSequence.next(resourceGroupOps.stream().toArray(Operation[]::new));

        // create a role tying the org admin group to the org resource group
        // create roles for the user
        Set<Operation> roleOps = new HashSet<Operation>();
        String adminUserGroupLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                OnboardingUtils.buildAuthzArtifactLink(
                        tenantLink, true));
        String userGroupLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                OnboardingUtils.buildAuthzArtifactLink(
                        tenantLink, false));
        String adminRoleLink = OnboardingUtils.buildAuthzArtifactLink(
                        tenantLink, true);
        String orgAdminResourceGroupLink = UriUtils.buildUriPath(
                ResourceGroupService.FACTORY_LINK,
                orgAdminResourceGroupSelfLink);
        Operation orgAdminRole = OnboardingUtils.createRole(this, adminRoleLink, adminUserGroupLink,
                orgAdminResourceGroupLink);
        roleOps.add(OnboardingUtils.addReplicationQuorumHeader(orgAdminRole));
        String orgTaskResourceGroupLink = UriUtils.buildUriPath(
                ResourceGroupService.FACTORY_LINK, taskLink);
        Operation orgTaskRole = OnboardingUtils.createRole(this, taskLink, userGroupLink,
                orgTaskResourceGroupLink);
        roleOps.add(OnboardingUtils.addReplicationQuorumHeader(orgTaskRole));
        String orgResourceResourceGroupLink = UriUtils.buildUriPath(
                ResourceGroupService.FACTORY_LINK, resourceLink);
        Set<Action> verbs = new HashSet<Action>();
        verbs.add(Action.GET);
        Operation orgResourceRole = OnboardingUtils.createRole(this, resourceLink, userGroupLink,
                orgResourceResourceGroupLink, verbs);
        roleOps.add(OnboardingUtils.addReplicationQuorumHeader(orgResourceRole));

        // allow a predefined set of stateless services to be accessible
        // to org users
        String statelessServiceResourceGroupLink = UriUtils.buildUriPath(
                ResourceGroupService.FACTORY_LINK,
                StatelessServiceAccessSetupService.STATELESS_SERVICES_FOR_USER_RESOURCE_GROUP);
        Operation statelessServiceRole = OnboardingUtils.createRole(this, tenantLink, userGroupLink,
                statelessServiceResourceGroupLink);
        roleOps.add(OnboardingUtils.addReplicationQuorumHeader(statelessServiceRole));
        transactionSet.addAll(roleOps.stream()
                .map(roleOp -> roleOp.getBody(RoleState.class))
                .map(roleState -> createRollBackOperation(this,
                        UriUtils.buildUriPath(RoleService.FACTORY_LINK, roleState.documentSelfLink),
                        null, cloneRoleState(roleState)))
                .collect(Collectors.toSet()));
        opSequence = opSequence.next(roleOps.stream().toArray(Operation[]::new));

        // patch UserService and add the userGroups created above to the user
        // who invoked this operation
        String loggedInUserSelfLink = op.getAuthorizationContext().getClaims().getSubject();
        String userLink = (loggedInUserSelfLink.equals(SystemUserService.SELF_LINK) &&
                orgData.userLink != null) ? orgData.userLink : loggedInUserSelfLink;
        UserState userState = new UserState();
        userState.userGroupLinks = new HashSet<>();
        userState.userGroupLinks.add(UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                OnboardingUtils.buildAuthzArtifactLink(
                        tenantLink, false)));
        userState.userGroupLinks.add(UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                OnboardingUtils.buildAuthzArtifactLink(
                        tenantLink, true)));
        Operation userStateOp = Operation.createPatch(this, userLink)
                .setBody(userState);
        transactionSet.add(createRollBackOperation(this, userLink,
                generateGroupLinkUpdateRequestBody(null, userState.userGroupLinks), null));

        setAuthorizationContext(userStateOp, getSystemAuthorizationContext());
        opSequence = opSequence.next(OnboardingUtils.addReplicationQuorumHeader(userStateOp));

        opSequence.setCompletion(
                (ops, exc) -> {
                    if (exc != null) {
                        callOperations(this, transactionSet, (operations, failures) -> {
                            if (failures != null) {
                                logWarning("Failed to roll back document %s",
                                        Utils.toString(failures.values().iterator().next()));
                            }
                            op.fail(exc.values().iterator().next());
                        });
                        return;
                    }
                    op.complete();
                });
        opSequence.sendWith(this);
    }


    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument d = super.getDocumentTemplate();
        d.documentDescription.serviceRequestRoutes = new HashMap<>();
        Route route = new Route();
        route.action = Action.POST;
        route.description = "Create a new organization and configure relevant auth services";
        route.requestType = OrganizationCreationRequest.class;
        d.documentDescription.serviceRequestRoutes
                .put(route.action, Collections.singletonList(route));
        return d;
    }
}
