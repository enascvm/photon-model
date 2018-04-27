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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertTrue;

import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountQueryPageService.maskCredentials;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_CREATEDBY_EMAIL;
import static com.vmware.photon.controller.discovery.onboarding.OnboardingUtils.SERVICE_USER_LINK;
import static com.vmware.photon.controller.discovery.onboarding.OnboardingUtils.buildUserIdFromClientId;
import static com.vmware.photon.controller.discovery.onboarding.OnboardingUtils.getDefaultProjectSelfLink;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEYID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEY_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.USER_LINK_KEY;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_TENANT_ID;
import static com.vmware.xenon.services.common.ServiceUriPaths.CORE_AUTHZ_USERS;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.discovery.cloudaccount.CloudAccountApiService.CloudAccountViewState;
import com.vmware.photon.controller.discovery.cloudaccount.CloudAccountApiService.Permission;
import com.vmware.photon.controller.discovery.cloudaccount.CloudAccountQueryPageService.Context;
import com.vmware.photon.controller.discovery.cloudaccount.CloudAccountQueryPageService.Stages;
import com.vmware.photon.controller.discovery.common.CloudAccountConstants;
import com.vmware.photon.controller.discovery.common.PhotonControllerCloudAccountUtils;
import com.vmware.photon.controller.discovery.common.services.OrganizationService.OrganizationState;
import com.vmware.photon.controller.discovery.common.services.UserContextQueryService.UserContext;
import com.vmware.photon.controller.discovery.common.services.UserService.UserState;
import com.vmware.photon.controller.discovery.endpoints.EndpointUtils;
import com.vmware.photon.controller.discovery.onboarding.OnboardingUtils;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.xenon.common.Claims;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.AuthorizationContext;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.UserGroupService;
import com.vmware.xenon.services.common.UserService;

public class TestCloudAccountQueryPageService {

    private static final String MOCK_PAGE_LINK = "/dummy/page";
    private static final String TEST_CLIENT = "test";
    private static final long MOCK_EXPIRATION = 0L;
    private static final List<String> MOCK_TENANTS = Collections.emptyList();

    private CloudAccountQueryPageService pageService = new CloudAccountQueryPageService(
            MOCK_PAGE_LINK, MOCK_EXPIRATION, MOCK_TENANTS);

    private Context context = new Context();
    private AuthCredentialsServiceState mockCredential = new AuthCredentialsServiceState();

    @Before
    public void setUp() {
        System.setProperty(CloudAccountConstants.OAUTH_CLIENT_IDS, TEST_CLIENT);
        this.context.inputOp = Operation.createGet(null);
        this.context.endpointStates = new ArrayList<>();
        this.context.credentialsMap = new LinkedHashMap<>();

        this.mockCredential.documentSelfLink = UriUtils.buildUriPath(
                AuthCredentialsService.FACTORY_LINK, UUID.randomUUID().toString());
        this.mockCredential.privateKey = "private key";
        this.mockCredential.privateKeyId = "private key id";
        this.mockCredential.userEmail = "user@test.com";

        OrganizationState org = new OrganizationState();
        org.id = "test";
        this.context.orgMap = Collections.singletonMap("test", org);

        this.context.userContext = new UserContext();
        UserState userState = new UserState();
        userState.email = this.mockCredential.userEmail;
        userState.documentSelfLink = OnboardingUtils.normalizeLink(UserService.FACTORY_LINK, userState.email);
        userState.userGroupLinks = new HashSet<>();
        String orgAdminUserGroupLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                OnboardingUtils.buildAuthzArtifactLink(org.id, true));
        userState.userGroupLinks.add(orgAdminUserGroupLink);
        this.context.userContext.user = userState;


    }

    /**
     * Builds endpoint link.
     * <p>
     * Format: /resources/endpoints/orgid-projectid-userid-uuid
     */
    private String buildEndpointLink(String orgId, String userEmail) {
        String defaultProjectId = UriUtils
                .getLastPathSegment(getDefaultProjectSelfLink(orgId));
        String endpointId =
                defaultProjectId + "-" + PhotonControllerCloudAccountUtils.computeHashWithSHA256(userEmail) + "-" + UUID.randomUUID();
        return UriUtils.buildUriPath(EndpointService.FACTORY_LINK, endpointId);
    }

    private void createMockEndpoint(String name, String type) {
        EndpointState state = new EndpointState();
        state.name = name;
        state.endpointType = type;
        state.documentSelfLink = buildEndpointLink("test", this.context.userContext.user.email);
        state.authCredentialsLink = this.mockCredential.documentSelfLink;
        this.context.credentialsMap.put(state.authCredentialsLink, this.mockCredential);

        // For testing purposes, just create endpoint properties to support all types
        state.endpointProperties = new HashMap<>();
        state.endpointProperties.put(PRIVATE_KEYID_KEY, this.mockCredential.privateKeyId);
        state.endpointProperties.put(PRIVATE_KEY_KEY, this.mockCredential.privateKey);
        state.endpointProperties.put(AZURE_TENANT_ID, "azure tenant id");
        state.endpointProperties.put(USER_LINK_KEY, "azure user link");

        state.tenantLinks = new ArrayList<>();
        state.tenantLinks.add("/tenants/project/test-project");

        state.customProperties = new HashMap<>();
        state.customProperties.put(ENDPOINT_CUSTOM_PROPERTY_NAME_CREATEDBY_EMAIL, this.mockCredential.userEmail);

        this.context.endpointStates.add(state);
    }

    @Test
    public void buildPODOtranslateVsphere() {
        createMockEndpoint("AWS account", EndpointType.aws.name());
        createMockEndpoint("Azure account", EndpointType.azure.name());
        createMockEndpoint("vSphere account", EndpointType.vsphere.name());
        createMockEndpoint("vSphere on prem", EndpointUtils.VSPHERE_ON_PREM_ADAPTER);
        List<Integer> vSphereIndexes = Arrays.asList(2, 3);

        this.pageService.buildPODO(this.context, Stages.BUILD_RESULT);

        assertThat(this.context.cloudAccountViewStates, hasSize(this.context.endpointStates.size()));
        for (int vSphereIndex : vSphereIndexes) {
            CloudAccountViewState cloudAccount = this.context.cloudAccountViewStates.get(vSphereIndex);
            assertThat(cloudAccount.type, equalTo(EndpointType.vsphere.name()));
            assertThat(cloudAccount.credentials.aws, nullValue());
            assertThat(cloudAccount.credentials.azure, nullValue());

            assertThat(cloudAccount.credentials.vsphere, notNullValue());
            assertThat(cloudAccount.credentials.vsphere.username, equalTo(this.mockCredential.privateKeyId));
            assertThat(cloudAccount.credentials.vsphere.password, equalTo(this.mockCredential.privateKey));

            assertThat(cloudAccount.createdBy.email, equalTo(this.mockCredential.userEmail));
        }

        for (CloudAccountViewState cloudAccount : this.context.cloudAccountViewStates) {
            assertThat(cloudAccount.permissions, hasSize(3));
            assertThat(cloudAccount.permissions,
                    containsInAnyOrder(Permission.READ, Permission.EDIT, Permission.DELETE));
        }

        this.context.cloudAccountViewStates = new ArrayList<>();

        this.context.userContext = new UserContext();
        UserState userState = new UserState();
        userState.email = "foo@test.com";
        userState.documentSelfLink = OnboardingUtils
                .normalizeLink(UserService.FACTORY_LINK, userState.email);
        userState.userGroupLinks = Collections.singleton(
                UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                        OnboardingUtils.buildAuthzArtifactLink("test", true)));
        this.context.userContext.user = userState;

        this.pageService.buildPODO(this.context, Stages.BUILD_RESULT);

        for (CloudAccountViewState cloudAccount : this.context.cloudAccountViewStates) {
            assertThat(cloudAccount.permissions, hasSize(3));
            assertThat(cloudAccount.permissions,
                    containsInAnyOrder(Permission.READ, Permission.EDIT, Permission.DELETE));
        }

        this.context.cloudAccountViewStates = new ArrayList<>();

        this.context.userContext = new UserContext();
        userState = new UserState();
        userState.email = "bar@test.com";
        userState.documentSelfLink = OnboardingUtils
                .normalizeLink(UserService.FACTORY_LINK, userState.email);
        userState.userGroupLinks = Collections.singleton(
                UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                        OnboardingUtils.buildAuthzArtifactLink("test", false)));
        this.context.userContext.user = userState;

        this.pageService.buildPODO(this.context, Stages.BUILD_RESULT);

        for (CloudAccountViewState cloudAccount : this.context.cloudAccountViewStates) {
            assertThat(cloudAccount.permissions, hasSize(1));
            assertThat(cloudAccount.permissions, contains(Permission.READ));
        }
    }

    @Test
    public void buildPODOnoCreatedByCustomProperty() {
        createMockEndpoint("AWS account", EndpointType.aws.name());
        createMockEndpoint("Azure account", EndpointType.azure.name());
        createMockEndpoint("vSphere account", EndpointType.vsphere.name());
        createMockEndpoint("vSphere on prem", EndpointUtils.VSPHERE_ON_PREM_ADAPTER);

        // If an endpoint is created using /provisioning/endpoint-tasks, the custom property for
        // createdBy.email will not be set.
        for (EndpointState endpointState : this.context.endpointStates) {
            endpointState.customProperties = null;
        }

        this.pageService.buildPODO(this.context, Stages.BUILD_RESULT);

        assertThat(this.context.cloudAccountViewStates, hasSize(this.context.endpointStates.size()));
        for (CloudAccountViewState cloudAccount : this.context.cloudAccountViewStates) {
            assertThat(cloudAccount.createdBy, notNullValue());
            assertThat(cloudAccount.createdBy.email, nullValue());

            assertThat(cloudAccount.permissions, hasSize(3));
            assertThat(cloudAccount.permissions, containsInAnyOrder(Permission.READ, Permission.EDIT,
                    Permission.DELETE));
        }
    }

    @Test
    public void testMaskCredentials() {

        // Null auth this.context should mask credentials
        assertTrue(maskCredentials(null));

        // Claims with no subject should mask credentials
        Claims noSubjectClaims = new Claims.Rfc7519Builder<>(Claims.class)
                .getResult();

        AuthorizationContext noSubjectAuthContext = AuthorizationContext.Builder
                .create()
                .setClaims(noSubjectClaims)
                .getResult();

        assertTrue(maskCredentials(noSubjectAuthContext));

        // Any typical user should mask credentials.
        Claims regularUserClaims = new Claims.Rfc7519Builder<>(Claims.class)
                .setSubject("general-user-link")
                .getResult();

        AuthorizationContext regularUserAuthContext = AuthorizationContext.Builder
                .create()
                .setClaims(regularUserClaims)
                .getResult();

        assertTrue(maskCredentials(regularUserAuthContext));

        // The service user should have unmasked credentials.
        Claims serviceUserClaims = new Claims.Rfc7519Builder<>(Claims.class)
                .setSubject(SERVICE_USER_LINK)
                .getResult();

        AuthorizationContext serviceUserAuthContext = AuthorizationContext.Builder
                .create()
                .setClaims(serviceUserClaims)
                .getResult();

        Assert.assertFalse(maskCredentials(serviceUserAuthContext));

        // Client Credential users should have unmasked credentials.
        Claims clientCredentialClaims = new Claims.Rfc7519Builder<>(Claims.class)
                .setSubject(UriUtils.buildUriPath(CORE_AUTHZ_USERS,
                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(
                                buildUserIdFromClientId(TEST_CLIENT))))
                .getResult();

        AuthorizationContext clientCredentialsAuthContext = AuthorizationContext.Builder
                .create()
                .setClaims(clientCredentialClaims)
                .getResult();

        Assert.assertFalse(maskCredentials(clientCredentialsAuthContext));
    }
}
