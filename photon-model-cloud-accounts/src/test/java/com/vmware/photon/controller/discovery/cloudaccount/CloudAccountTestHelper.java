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

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_CREATEDBY_EMAIL;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_LEGACY_ID;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_PREFIX_SERVICE_TAG;
import static com.vmware.photon.controller.discovery.endpoints.TestEndpointUtils.createAwsCredentials;
import static com.vmware.photon.controller.discovery.endpoints.TestEndpointUtils.createAwsCredentialsArn;
import static com.vmware.photon.controller.discovery.endpoints.TestEndpointUtils.createAzureCredentials;
import static com.vmware.photon.controller.discovery.endpoints.TestEndpointUtils.createAzureEACredentials;
import static com.vmware.photon.controller.discovery.endpoints.TestEndpointUtils.createVSphereCredentials;
import static com.vmware.photon.controller.discovery.onboarding.OnboardingUtils.SERVICE_USER_LINK;
import static com.vmware.photon.controller.discovery.onboarding.OnboardingUtils.addReplicationQuorumHeader;
import static com.vmware.photon.controller.discovery.vsphere.VsphereOnPremEndpointAdapterService.DC_ID_KEY;
import static com.vmware.photon.controller.discovery.vsphere.VsphereOnPremEndpointAdapterService.DC_NAME_KEY;
import static com.vmware.photon.controller.discovery.vsphere.VsphereOnPremEndpointAdapterService.HOST_NAME_KEY;
import static com.vmware.photon.controller.discovery.vsphere.VsphereOnPremEndpointAdapterService.PRIVATE_CLOUD_NAME_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEYID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEY_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.REGION_KEY;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.vmware.photon.controller.discovery.cloudaccount.CloudAccountApiService.CloudAccountCreateRequest;
import com.vmware.photon.controller.discovery.cloudaccount.CloudAccountApiService.CloudAccountViewState;
import com.vmware.photon.controller.discovery.cloudaccount.CloudAccountApiService.Permission;
import com.vmware.photon.controller.discovery.common.PhotonControllerCloudAccountUtils;
import com.vmware.photon.controller.discovery.common.SymphonyCommonTestUtils;
import com.vmware.photon.controller.discovery.common.TagViewState;
import com.vmware.photon.controller.discovery.common.services.OrganizationService;
import com.vmware.photon.controller.discovery.common.services.OrganizationService.OrganizationState;
import com.vmware.photon.controller.discovery.common.services.UserService;
import com.vmware.photon.controller.discovery.common.services.UserService.UserState;
import com.vmware.photon.controller.discovery.endpoints.Credentials;
import com.vmware.photon.controller.discovery.endpoints.EndpointCreationTaskService;
import com.vmware.photon.controller.discovery.endpoints.EndpointServices;
import com.vmware.photon.controller.discovery.endpoints.EndpointUtils;
import com.vmware.photon.controller.discovery.onboarding.ClientCredentialsUserSetupService;
import com.vmware.photon.controller.discovery.onboarding.ClientCredentialsUserSetupService.ClientCredentialsUserSetupRequest;
import com.vmware.photon.controller.discovery.onboarding.OnBoardingTestUtils;
import com.vmware.photon.controller.discovery.onboarding.OnboardingServices;
import com.vmware.photon.controller.discovery.onboarding.OnboardingUtils;
import com.vmware.photon.controller.discovery.vsphere.TestVsphereRDCSyncTaskService;
import com.vmware.photon.controller.discovery.vsphere.VsphereOnPremEndpointAdapterService;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSAdapters;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants;
import com.vmware.photon.controller.model.adapters.azure.AzureAdapters;
import com.vmware.photon.controller.model.adapters.azure.ea.AzureEaAdapters;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService.EndpointAllocationTaskState;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService.SubStage;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.AuthorizationContext;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceStateCollectionUpdateRequest;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.TestRequestSender;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.UserGroupService;

/**
 * This is a reusable component that cloud-account-related unit tests can leverage to facilitate:
 * <ul>
 * <li>Setting up a test cluster of {@link VerificationHost}s, with cloud-account services started
 * <li>Easily creating a mock cloud account
 * <li>Sorted and non-sorted verification of cloud-account results
 * </ul>
 */
public class CloudAccountTestHelper {

    public static final int DEFAULT_NODE_COUNT = 3;
    public static final String ACCOUNT_DESCRIPTION = "Account for unit tests";
    public static final String CUSTOM_PROP_KEY = "cost_insight:s3bucketName";
    public static final String CUSTOM_PROP_VALUE = "testBucket";
    public static final String ENDPOINT_NAME_PREFIX = "Test-Endpoint";
    public static final String PRIVATE_KEY_ID_PREFIX = "test-keyId";
    public static final String PRIVATE_KEY_PREFIX = "test-key";

    public static final String USER_1_ID = "user1@example.com";
    public static final String USER_1_PASSWORD = "passwordforuser1";
    public static final String USER_2_ID = "user2@example.com";
    public static final String USER_2_PASSWORD = "passwordforuser2";
    public static final String ORG_1_ID = "org-1";
    public static final String ORG_2_ID = "org-2";
    public static final String DEFAULT_PROJECT_ID = OnboardingUtils.getDefaultProjectName();

    public static final int DEFAULT_CLOUD_ACCOUNT_PROPERTIES_LIST_SIZE = 9;
    public static final int DEFAULT_CLOUD_ACCOUNT_FILTER_LIST_SIZE = 9;
    public static final int DEFAULT_CLOUD_ACCOUNT_SORT_LIST_SIZE = 3;

    public VerificationHost host;
    public URI peerUri;
    public String orgLink;
    public String org2Link;
    public String defaultProjectLink;

    private CloudAccountTestHelper() {}

    public static CloudAccountTestHelper create() {
        return create(true, DEFAULT_NODE_COUNT);
    }

    public static CloudAccountTestHelper create(boolean authEnabled, int nodeCount) {
        CloudAccountTestHelper helper = new CloudAccountTestHelper();
        try {
            VerificationHost host = VerificationHost.create(0);
            helper.host = host;

            host.setAuthorizationEnabled(authEnabled);
            host.start();
            host.setStressTest(host.isStressTest);
            host.setPeerSynchronizationEnabled(true);
            host.setUpPeerHosts(nodeCount);
            host.setNodeGroupQuorum(nodeCount);
            host.joinNodesAndVerifyConvergence(nodeCount, true);

            // start provisioning services on all the hosts
            host.setSystemAuthorizationContext();
            host.startService(new TestVsphereRDCSyncTaskService.MockCCSValidateService());
            host.waitForServiceAvailable(TestVsphereRDCSyncTaskService.MockCCSValidateService
                    .SELF_LINK);
            for (VerificationHost h : host.getInProcessHostMap().values()) {
                PhotonModelServices.startServices(h);
                PhotonModelTaskServices.startServices(h);
                AWSAdapters.startServices(h);
                PhotonModelAdaptersRegistryAdapters.startServices(h);
                AzureEaAdapters.startServices(h);
                AzureAdapters.startServices(h);
            }
            host.resetSystemAuthorizationContext();

            for (VerificationHost h : host.getInProcessHostMap().values()) {
                OnBoardingTestUtils.startCommonServices(h);
                h.setSystemAuthorizationContext();
                OnboardingServices.startServices(h, h::addPrivilegedService);
                CloudAccountServices.startServices(h, h::addPrivilegedService);
                EndpointServices.startServices(h, h::addPrivilegedService);
                h.waitForServiceAvailable(CloudAccountApiService.SELF_LINK);
                h.startService(
                        Operation.createPost(UriUtils.buildUri(h,
                                VsphereOnPremEndpointAdapterService.class)),
                        new VsphereOnPremEndpointAdapterService());
                h.waitForServiceAvailable(VsphereOnPremEndpointAdapterService.SELF_LINK);
                h.waitForServiceAvailable(ClientCredentialsUserSetupService.SELF_LINK);
                ClientCredentialsUserSetupRequest req = new ClientCredentialsUserSetupRequest();
                req.clientIds = OnboardingUtils.getSystemOauthClientIds();
                if (!req.clientIds.isEmpty()) {
                    h.sendAndWaitExpectSuccess(
                            Operation.createPost(h, ClientCredentialsUserSetupService.SELF_LINK)
                                    .setBody(req));
                }
                h.resetAuthorizationContext();
            }

            OnBoardingTestUtils.waitForCommonServicesAvailability(host, host.getPeerHostUri());

            List<String> factories = new ArrayList<>();
            factories.add(EndpointCreationTaskService.FACTORY_LINK);
            factories.add(EndpointAllocationTaskService.FACTORY_LINK);
            factories.add(CloudAccountQueryTaskService.FACTORY_LINK);
            factories.add(CloudAccountMaintenanceService.FACTORY_LINK);
            SymphonyCommonTestUtils
                    .waitForFactoryAvailability(host, host.getPeerHostUri(), factories);

            helper.peerUri = host.getPeerHostUri();

            OnBoardingTestUtils.setupUser(host, helper.peerUri,
                    OnBoardingTestUtils.createUserData(USER_1_ID, USER_1_PASSWORD));
            OnBoardingTestUtils
                    .setupOrganization(host, helper.peerUri, ORG_1_ID, USER_1_ID, USER_1_PASSWORD);
            helper.defaultProjectLink = OnBoardingTestUtils
                    .setupProject(host, helper.peerUri, DEFAULT_PROJECT_ID, ORG_1_ID, USER_1_ID,
                            USER_1_PASSWORD);

            helper.orgLink = UriUtils.buildUriPath(OrganizationService.FACTORY_LINK,
                    Utils.computeHash(ORG_1_ID));
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }

        return helper;
    }

    public void tearDown() {
        if (this.host == null) {
            return;
        }
        this.host.tearDownInProcessPeers();
        this.host.toggleNegativeTestMode(false);
        this.host.tearDown();
    }

    public void createUser(String userId, String userPassword) throws Throwable {
        OnBoardingTestUtils.setupUser(this.host, this.peerUri,
                OnBoardingTestUtils.createUserData(userId, userPassword));
    }

    public void createOrg(String orgId, String projectId, String userId, String userPassword) throws Throwable {
        OnBoardingTestUtils
                .setupOrganization(this.host, this.peerUri, orgId, userId, userPassword);
        OnBoardingTestUtils.setupProject(this.host, this.peerUri, projectId, orgId, userId,
                userPassword);
    }

    public void addUserToOrgOrProject(String email, String userId, String userPassword, String entityLink, boolean isAdmin) throws Throwable {
        OnBoardingTestUtils
                .addUserToOrgOrProject(this.host, this.peerUri, entityLink, email,
                        userId, userPassword, isAdmin);
    }

    public void createSecondOrg() throws Throwable {
        createOrg(ORG_2_ID, DEFAULT_PROJECT_ID, USER_1_ID, USER_1_PASSWORD);
        this.org2Link = UriUtils.buildUriPath(OrganizationService.FACTORY_LINK,
                Utils.computeHash(ORG_2_ID));
    }

    public void createSecondUser() throws Throwable {
        createUser(USER_2_ID, USER_2_PASSWORD);
        addUserToOrgOrProject(USER_2_ID, USER_1_ID, USER_1_PASSWORD, this.orgLink, false);
        addUserToOrgOrProject(USER_2_ID, USER_1_ID, USER_1_PASSWORD, this.defaultProjectLink, false);
    }

    public void removeUserFromOrg(String userId, String orgId) throws Throwable {
        URI userURI = UriUtils.buildUri(this.peerUri, UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(userId));
        String orgLink = Utils.computeHash(orgId);
        String defaultProjectLink = orgLink
                .concat(OnboardingUtils.SEPARATOR)
                .concat(Utils.computeHash(OnboardingUtils.getDefaultProjectName()));
        List<String> userGroupsToRemove = new ArrayList<>();
        userGroupsToRemove.add(UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                OnboardingUtils.buildAuthzArtifactLink(
                        orgLink, false)));
        userGroupsToRemove.add(UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                OnboardingUtils.buildAuthzArtifactLink(
                        orgLink, true)));
        userGroupsToRemove.add(UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                OnboardingUtils.buildAuthzArtifactLink(
                        defaultProjectLink, false)));
        userGroupsToRemove.add(UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                OnboardingUtils.buildAuthzArtifactLink(
                        defaultProjectLink, true)));

        UserState userState = this.host.waitForResponse(
                Operation.createGet(userURI)).getBody(UserState.class);

        if (userState.userGroupLinks == null || userState.userGroupLinks.isEmpty()) {
            return;
        }

        userGroupsToRemove.removeIf(link -> !userState.userGroupLinks.contains(link));

        if (userGroupsToRemove.isEmpty()) {
            return;
        }

        Map<String, Collection<Object>> itemsToRemove = Collections
                .singletonMap(UserState.FIELD_NAME_USER_GROUP_LINKS,
                        new HashSet<>(userGroupsToRemove));
        ServiceStateCollectionUpdateRequest groupLinksUpdateRequest = ServiceStateCollectionUpdateRequest
                .create(null, itemsToRemove);

        Operation userPatchReq = Operation.createPatch(userURI)
                .setBody(groupLinksUpdateRequest);
        addReplicationQuorumHeader(userPatchReq);

        Operation patchOperation = this.host.waitForResponse(userPatchReq);
        assertEquals(Operation.STATUS_CODE_OK, patchOperation.getStatusCode());
        this.host.log("Successfully patched user [" + userId + "]");
    }

    private static EndpointState createEndpoint(VerificationHost host, URI peerUri,
            CloudAccountCreateRequest createBody) {
        Operation response = host.waitForResponse(Operation.createPost(
                UriUtils.buildUri(peerUri, CloudAccountApiService.SELF_LINK))
                .setBody(createBody));
        assertEquals(Operation.STATUS_CODE_CREATED, response.getStatusCode());
        String cloudAccountLink = response.getResponseHeader(Operation.LOCATION_HEADER);
        assertNotNull(cloudAccountLink);
        String endpointLink = UriUtils.buildUriPath(EndpointService.FACTORY_LINK, UriUtils.getLastPathSegment(cloudAccountLink));

        Operation getResp = host.waitForResponse(
                Operation.createGet(UriUtils.buildUri(peerUri, endpointLink)));
        EndpointState createdEndpoint = getResp.getBody(EndpointState.class);
        assertNotNull(createdEndpoint);

        return createdEndpoint;
    }

    public static String createMockVsphereEndpoint(VerificationHost host, URI peerUri,
            String orgId, String endpointName, String userName, String password, String hostName,
            String privateCloudName, String dataCenterName, String dataCenterID) {
        CloudAccountCreateRequest task = new CloudAccountCreateRequest();
        task.name = endpointName;
        task.orgId = orgId;
        task.description = ACCOUNT_DESCRIPTION;
        task.type = EndpointType.vsphere.name();

        Map<String, String> endpointProperties = new HashMap<>();
        endpointProperties.put(HOST_NAME_KEY, hostName);
        endpointProperties.put(PRIVATE_CLOUD_NAME_KEY, privateCloudName);
        endpointProperties.put(DC_ID_KEY, dataCenterID);
        endpointProperties.put(DC_NAME_KEY, dataCenterName);
        task.endpointProperties = endpointProperties;

        Map<String, String> customProperties = new HashMap<>();
        customProperties.put(HOST_NAME_KEY, hostName);
        customProperties.put(PRIVATE_CLOUD_NAME_KEY, privateCloudName);
        customProperties.put(DC_ID_KEY, dataCenterID);
        customProperties.put(DC_NAME_KEY, dataCenterName);
        task.customProperties = customProperties;
        task.credentials = createVSphereCredentials(userName, password);
        task.isMock = true;

        EndpointState createdEndpoint = createEndpoint(host, peerUri, task);
        assertNotNull(createdEndpoint);
        assertEquals(4, createdEndpoint.endpointProperties.size());

        assertNull(createdEndpoint.endpointProperties.get(PRIVATE_KEYID_KEY));
        assertNull(createdEndpoint.endpointProperties.get(PRIVATE_KEY_KEY));
        assertEquals(hostName, createdEndpoint.endpointProperties.get(HOST_NAME_KEY));
        assertEquals(privateCloudName, createdEndpoint.endpointProperties.get(PRIVATE_CLOUD_NAME_KEY));
        assertEquals(dataCenterID, createdEndpoint.endpointProperties.get(DC_ID_KEY));
        assertEquals(dataCenterName, createdEndpoint.endpointProperties.get(DC_NAME_KEY));

        return createdEndpoint.documentSelfLink;
    }

    public static CloudAccountCreateRequest createMockVsphereEndpointWithoutEndpointProperties(
            String orgId, String endpointName, String userName, String password, String hostName,
            String privateCloudName, String dataCenterName, String dataCenterID) {
        CloudAccountCreateRequest task = new CloudAccountCreateRequest();
        task.name = endpointName;
        task.orgId = orgId;
        task.description = ACCOUNT_DESCRIPTION;
        task.type = EndpointType.vsphere.name();
        Map<String, String> endpointProperties = new HashMap<>();

        Map<String, String> customProperties = new HashMap<>();
        customProperties.put(HOST_NAME_KEY, hostName);
        customProperties.put(PRIVATE_CLOUD_NAME_KEY, privateCloudName);
        customProperties.put(DC_ID_KEY, dataCenterID);
        customProperties.put(DC_NAME_KEY, dataCenterName);

        task.endpointProperties = endpointProperties;
        task.customProperties = customProperties;
        task.credentials = createVSphereCredentials(userName, password);
        task.isMock = true;

        return task;
    }

    public static CloudAccountCreateRequest createMockVsphereEndpointWithCustomProperties(
            String orgId, String endpointName, String userName, String password, String hostName,
            String privateCloudName, String dataCenterName, String dataCenterID) {
        CloudAccountCreateRequest task = new CloudAccountCreateRequest();
        task.name = endpointName;
        task.orgId = orgId;
        task.description = ACCOUNT_DESCRIPTION;
        task.type = EndpointType.vsphere.name();
        Map<String, String> endpointProperties = new HashMap<>();
        endpointProperties.put(HOST_NAME_KEY, hostName);
        endpointProperties.put(PRIVATE_CLOUD_NAME_KEY, privateCloudName);
        endpointProperties.put(DC_ID_KEY, dataCenterID);
        endpointProperties.put(DC_NAME_KEY, dataCenterName);

        Map<String, String> customProperties = new HashMap<>();
        customProperties.put(HOST_NAME_KEY, hostName);
        customProperties.put(PRIVATE_CLOUD_NAME_KEY, privateCloudName);
        customProperties.put(DC_ID_KEY, dataCenterID);
        customProperties.put(DC_NAME_KEY, dataCenterName);

        task.endpointProperties = endpointProperties;
        task.customProperties = customProperties;
        task.credentials = createVSphereCredentials(userName, password);
        task.isMock = true;

        return task;
    }

    public String createMockVsphereEndpointOldApi(VerificationHost host, URI peerUri,
            String orgId, String endpointName, String userName, String password, String hostName,
            String privateCloudName, String dataCenterName, String dataCenterID) {
        EndpointState endpointState = new EndpointState();
        endpointState.name = endpointName;
        endpointState.desc = ACCOUNT_DESCRIPTION;
        endpointState.endpointType = EndpointUtils.VSPHERE_ON_PREM_ADAPTER;

        endpointState.tenantLinks = Arrays.asList(UriUtils.buildUriPath(OrganizationService.FACTORY_LINK,
                Utils.computeHash(orgId)));

        Map<String, String> endpointProperties = new HashMap<>();
        endpointProperties.put(PRIVATE_KEYID_KEY, userName);
        endpointProperties.put(PRIVATE_KEY_KEY, password);
        endpointProperties.put(HOST_NAME_KEY, hostName);
        endpointProperties.put(PRIVATE_CLOUD_NAME_KEY, privateCloudName);
        endpointProperties.put(DC_ID_KEY, dataCenterID);
        endpointProperties.put(DC_NAME_KEY, dataCenterName);
        endpointState.endpointProperties = endpointProperties;

        Map<String, String> customProperties = new HashMap<>();
        customProperties.put(CUSTOM_PROP_KEY, CUSTOM_PROP_VALUE);
        endpointState.customProperties = customProperties;

        EndpointAllocationTaskState oldTask = new EndpointAllocationTaskState();
        oldTask.endpointState = endpointState;
        oldTask.options = EnumSet.of(TaskOption.IS_MOCK);
        oldTask.tenantLinks = endpointState.tenantLinks;
        oldTask.taskInfo = TaskState.createDirect();
        oldTask.taskSubStage = SubStage.CREATE_UPDATE_ENDPOINT;
        oldTask.adapterReference = UriUtils.buildUri(this.peerUri,
                VsphereOnPremEndpointAdapterService.SELF_LINK);

        TestRequestSender sender = host.getTestRequestSender();
        EndpointAllocationTaskState endpointTask = sender.sendAndWait(
                Operation.createPost(UriUtils.buildUri(this.peerUri, EndpointAllocationTaskService.FACTORY_LINK))
                        .setBody(oldTask),
                EndpointAllocationTaskState.class);
        assertNotNull(endpointTask);
        assertNotNull(endpointTask.endpointState);

        EndpointState createdEndpoint = endpointTask.endpointState;
        assertEquals(4, createdEndpoint.endpointProperties.size());

        assertNull(createdEndpoint.endpointProperties.get(PRIVATE_KEYID_KEY));
        assertNull(createdEndpoint.endpointProperties.get(PRIVATE_KEY_KEY));
        assertEquals(hostName, createdEndpoint.endpointProperties.get(HOST_NAME_KEY));
        assertEquals(privateCloudName, createdEndpoint.endpointProperties.get(PRIVATE_CLOUD_NAME_KEY));
        assertEquals(dataCenterID, createdEndpoint.endpointProperties.get(DC_ID_KEY));
        assertEquals(dataCenterName, createdEndpoint.endpointProperties.get(DC_NAME_KEY));

        return createdEndpoint.documentSelfLink;
    }

    public EndpointState createMockAzureEndpoint(String endpointName, String clientKey, String
            clientId, String tenantId, String subscriptionId) {
        CloudAccountApiService.CloudAccountCreateRequest task = new CloudAccountApiService.CloudAccountCreateRequest();
        task.name = endpointName;
        task.description = ACCOUNT_DESCRIPTION;
        task.orgLink = this.orgLink;
        task.type = EndpointType.azure.name();
        task.isMock = true;
        task.credentials = createAzureCredentials(clientId, clientKey, subscriptionId, tenantId);

        EndpointState createdEndpoint = createEndpoint(this.host, this.peerUri, task);
        assertNotNull(createdEndpoint);
        return createdEndpoint;
    }


    public EndpointState createMockAzureEAEndpoint(String endpointName, String privateKey, String privateKeyId) {
        CloudAccountCreateRequest task = new CloudAccountCreateRequest();
        task.name = endpointName;
        task.description = ACCOUNT_DESCRIPTION;
        task.orgLink = this.orgLink;
        task.type = EndpointType.azure_ea.name();

        Map<String, String> customProperties = new HashMap<>();
        task.customProperties = customProperties;

        task.credentials = createAzureEACredentials(privateKeyId, privateKey);
        task.isMock = true;

        EndpointState createdEndpoint = createEndpoint(this.host, this.peerUri, task);
        assertNotNull(createdEndpoint);
        assertEquals(privateKeyId, createdEndpoint.endpointProperties.get(PRIVATE_KEYID_KEY));
        return createdEndpoint;
    }

    public static EndpointState createMockAzureEAEndpoint(VerificationHost host, String endpointName,
            String orgLink, String privateKey, String privateKeyId, URI peerUri) {
        CloudAccountCreateRequest task = new CloudAccountCreateRequest();
        task.name = endpointName;
        task.description = ACCOUNT_DESCRIPTION;
        task.orgLink = orgLink;
        task.type = EndpointType.azure_ea.name();

        Map<String, String> customProperties = new HashMap<>();
        customProperties.put(CUSTOM_PROP_KEY, CUSTOM_PROP_VALUE);
        task.customProperties = customProperties;

        task.credentials = createAzureEACredentials(privateKeyId, privateKey);
        task.isMock = true;

        EndpointState createdEndpoint = createEndpoint(host, peerUri, task);
        assertNotNull(createdEndpoint);
        assertEquals(privateKeyId, createdEndpoint.endpointProperties.get(PRIVATE_KEYID_KEY));
        return createdEndpoint;
    }

    public String createMockAwsEndpoint(String endpointName, String privateKey, String privateKeyId,
            Set<String> services, Set<String> owners, Set<TagViewState> tags) {
        return createMockAwsEndpoint(this.host, this.peerUri, this.orgLink, endpointName,
                privateKey, privateKeyId, CUSTOM_PROP_VALUE, false, services, owners, tags);
    }

    public String createMockAwsEndpoint(String endpointName, String privateKey, String
            privateKeyId, String bucketName, boolean isPaying, Set<String> services,
            Set<String> owners, Set<TagViewState> tags) {
        return createMockAwsEndpoint(this.host, this.peerUri, this.orgLink, endpointName,
                privateKey, privateKeyId, bucketName, isPaying, services, owners, tags);
    }

    public String createMockPayingAwsEndpoint(String endpointName, String privateKey,
            String privateKeyId, Set<String> services, Set<String> owners, Set<TagViewState> tags) {
        return createMockAwsEndpoint(this.host, this.peerUri, this.orgLink, endpointName,
                privateKey, privateKeyId, CUSTOM_PROP_VALUE, true, services, owners, tags);
    }

    public String createMockAwsEndpointInOrg(String orgLink, String endpointName, String privateKey,
            String privateKeyId, Set<String> services, Set<String> owners, Set<TagViewState> tags) {
        return createMockAwsEndpoint(this.host, this.peerUri, orgLink, endpointName, privateKey,
                privateKeyId, services, owners, tags);
    }

    public static String createMockAwsEndpoint(VerificationHost host, URI peerUri,
            String orgLink, String endpointName, String privateKey, String privateKeyId,
            Set<String> services, Set<String> owners, Set<TagViewState> tags) {
        return createMockAwsEndpoint(host, peerUri, orgLink, endpointName, privateKey,
                privateKeyId, CUSTOM_PROP_VALUE, false, services, owners, tags);
    }

    // Create this because contract tests set the billbucketName.
    public static String createMockAwsEndpointForContractTestcreateMockAwsEndpoint(VerificationHost host,
            URI peerUri, String orgLink, String endpointName, String privateKey,
            String privateKeyId, Set<String> services, Set<String> owners, Set<TagViewState> tags) {
        return createMockAwsEndpoint(host, peerUri, orgLink, endpointName, privateKey,
                privateKeyId, CUSTOM_PROP_VALUE, true, services, owners, tags);
    }

    private static String createMockAwsEndpoint(VerificationHost host, URI peerUri,
            String orgLink, String endpointName, Credentials credentials,
            String bucketName, boolean isPayingAccount, Set<String> services, Set<String> owners,
            Set<TagViewState> tags) {
        CloudAccountCreateRequest task = new CloudAccountCreateRequest();
        task.name = endpointName;
        task.description = ACCOUNT_DESCRIPTION;
        task.orgLink = orgLink;
        task.type = EndpointType.aws.name();
        Map<String, String> endpointProperties = new HashMap<>();
        endpointProperties.put(REGION_KEY, "test-regionId");
        Map<String, String> customProperties = new HashMap<>();

        if (isPayingAccount) {
            endpointProperties.put(AWSConstants.AWS_BILLS_S3_BUCKET_NAME_KEY, bucketName);
            customProperties.put(CUSTOM_PROP_KEY, bucketName);
        } else {
            endpointProperties.put(AWSConstants.AWS_BILLS_S3_BUCKET_NAME_KEY, "");
            customProperties.put(CUSTOM_PROP_KEY, "");
        }

        task.endpointProperties = endpointProperties;
        task.customProperties = customProperties;

        task.credentials = credentials;
        task.services = services;
        task.tags = tags;
        task.isMock = true;
        task.owners = owners;

        EndpointState createdEndpoint = createEndpoint(host, peerUri, task);
        assertEquals(3, createdEndpoint.endpointProperties.size());
        assertEquals("test-regionId", createdEndpoint.endpointProperties.get(REGION_KEY));
        assertEquals("true", createdEndpoint.endpointProperties.get("supportPublicImages"));

        // Assert root compute properties
        ComputeState rootCompute = host.waitForResponse(Operation.createGet(UriUtils.buildUri
                (peerUri, createdEndpoint.computeLink))).getBody(ComputeState.class);
        String billBucketName = rootCompute.customProperties.get(AWSConstants
                .AWS_BILLS_S3_BUCKET_NAME_KEY);
        if (isPayingAccount) {
            assertTrue(billBucketName.contains(CUSTOM_PROP_VALUE));
        } else {
            assertEquals("", billBucketName);
        }
        return createdEndpoint.documentSelfLink;
    }

    private static String createMockAwsEndpoint(VerificationHost host, URI peerUri,
            String orgLink, String endpointName, String privateKey, String privateKeyId,
            String bucketName, boolean isPayingAccount, Set<String> services, Set<String> owners, Set<TagViewState> tags) {
        return createMockAwsEndpoint(host, peerUri, orgLink, endpointName,
                createAwsCredentials(privateKeyId, privateKey), bucketName, isPayingAccount,
                services, owners, tags);
    }

    public static String createMockAwsEndpointArn(VerificationHost host, URI peerUri,
            String orgLink, String endpointName, String arn,
            String bucketName, boolean isPayingAccount, Set<String> services, Set<String> owners, Set<TagViewState> tags) {
        return createMockAwsEndpoint(host, peerUri, orgLink, endpointName,
                createAwsCredentialsArn(arn), bucketName, isPayingAccount, services, owners, tags);
    }

    public List<String> createMockAwsEndpoints(int numEndpoints, Set<String> services,
            Set<String> owners, Set<TagViewState> tags) throws Throwable {
        return createMockAwsEndpoints(numEndpoints, numEndpoints, services, owners, tags);
    }

    public List<String> createMockAwsEndpointsDescending(int numEndpoints, Set<String> services,
            Set<String> owners, Set<TagViewState> tags) throws Throwable {
        return createMockAwsEndpointsDescending(numEndpoints, numEndpoints, services, owners, tags);
    }

    public EndpointState createMockAwsEndpointOldApi(String endpointName, String privateKey, String privateKeyId) {
        EndpointState endpointState = new EndpointState();
        endpointState.name = endpointName;
        endpointState.desc = ACCOUNT_DESCRIPTION;
        endpointState.endpointType = EndpointType.aws.name();
        endpointState.tenantLinks = Arrays.asList(this.defaultProjectLink);
        endpointState.resourcePoolLink = UriUtils.buildUriPath(
                ResourcePoolService.FACTORY_LINK, UriUtils.getLastPathSegment(this.defaultProjectLink));

        Map<String, String> endpointProperties = new HashMap<>();
        endpointProperties.put(REGION_KEY, "test-regionId");
        endpointProperties.put(AWSConstants.AWS_BILLS_S3_BUCKET_NAME_KEY, CUSTOM_PROP_VALUE);
        endpointProperties.put(EndpointConfigRequest.PRIVATE_KEY_KEY, privateKey);
        endpointProperties.put(EndpointConfigRequest.PRIVATE_KEYID_KEY, privateKeyId);
        endpointState.endpointProperties = endpointProperties;

        Map<String, String> customProperties = new HashMap<>();
        customProperties.put(CUSTOM_PROP_KEY, CUSTOM_PROP_VALUE);
        endpointState.customProperties = customProperties;

        EndpointAllocationTaskState oldTask = new EndpointAllocationTaskState();
        oldTask.endpointState = endpointState;
        oldTask.options = EnumSet.of(TaskOption.IS_MOCK);
        oldTask.tenantLinks = Arrays.asList(this.defaultProjectLink);
        oldTask.taskInfo = TaskState.createDirect();

        TestRequestSender sender = this.host.getTestRequestSender();
        EndpointAllocationTaskState endpointTask = sender.sendAndWait(
                Operation.createPost(UriUtils.buildUri(this.peerUri, EndpointAllocationTaskService.FACTORY_LINK))
                        .setBody(oldTask),
                EndpointAllocationTaskState.class);
        assertNotNull(endpointTask);
        assertNotNull(endpointTask.endpointState);

        EndpointState createdEndpoint = endpointTask.endpointState;
        assertEquals(3, createdEndpoint.endpointProperties.size());
        assertEquals("test-regionId", createdEndpoint.endpointProperties.get(REGION_KEY));
        assertEquals(privateKeyId, createdEndpoint.endpointProperties.get(PRIVATE_KEYID_KEY));
        assertEquals("true", createdEndpoint.endpointProperties.get("supportPublicImages"));

        return createdEndpoint;
    }

    public List<String> createMockAwsEndpoints(int numEndpoints, int expectedTotalEndpoints,
            Set<String> services, Set<String> owners, Set<TagViewState> tags) throws Throwable {
        return createMockAwsEndpoints(numEndpoints, expectedTotalEndpoints, false, services, owners, tags);
    }

    public List<String> createMockPayingAwsEndpoints(int numEndpoints, Set<String> services,
            Set<String> owners, Set<TagViewState> tags) throws Throwable {
        return createMockAwsEndpoints(numEndpoints, numEndpoints, true, services, owners, tags);
    }

    private List<String> createMockAwsEndpoints(int numEndpoints, int expectedTotalEndpoints,
            boolean isPaying, Set<String> services, Set<String> owners, Set<TagViewState> tags) throws Throwable {
        List<String> endpointLinks = new ArrayList<>();
        for (int ndx = 0; ndx < numEndpoints; ndx++) {
            String endpointLink = createMockAwsEndpoint(ENDPOINT_NAME_PREFIX + ndx, PRIVATE_KEY_PREFIX + ndx,
                    PRIVATE_KEY_ID_PREFIX + ndx, CUSTOM_PROP_VALUE + ndx, isPaying, services, owners, tags);
            endpointLinks.add(endpointLink);
        }
        assertThat(endpointLinks.size(), equalTo(numEndpoints));

        assertEndpointCount(expectedTotalEndpoints);

        return endpointLinks;
    }

    public List<String> createMockAwsEndpointsDescending(int numEndpoints, int expectedTotalEndpoints,
            Set<String> services, Set<String> owners, Set<TagViewState> tags) throws Throwable {
        return createMockAwsEndpointsDescending(numEndpoints, expectedTotalEndpoints, false,
                services, owners, tags);
    }

    private List<String> createMockAwsEndpointsDescending(int numEndpoints,
            int expectedTotalEndpoints, boolean isPaying, Set<String> services, Set<String> owners, Set<TagViewState> tags)
            throws Throwable {
        List<String> endpointLinks = new ArrayList<>();
        for (int ndx = numEndpoints - 1; ndx >= 0; ndx--) {
            String endpointLink = createMockAwsEndpoint(ENDPOINT_NAME_PREFIX + ndx, PRIVATE_KEY_PREFIX + ndx,
                    PRIVATE_KEY_ID_PREFIX + ndx, CUSTOM_PROP_VALUE + ndx, isPaying, services, owners, tags);
            endpointLinks.add(endpointLink);
        }
        assertThat(endpointLinks.size(), equalTo(numEndpoints));

        assertEndpointCount(expectedTotalEndpoints);

        return endpointLinks;
    }

    private void assertEndpointCount(int expectedTotalEndpoints) {
        this.host.setSystemAuthorizationContext();
        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .addOption(QueryTask.QuerySpecification.QueryOption.COUNT)
                .addOption(QueryTask.QuerySpecification.QueryOption.INDEXED_METADATA)
                .setQuery(
                        QueryTask.Query.Builder.create().addKindFieldClause(EndpointState.class).build())
                .build();
        URI queryTaskURI = UriUtils.buildUri(this.peerUri, ServiceUriPaths.CORE_QUERY_TASKS);
        this.host.createQueryTaskService(queryTaskURI, queryTask, false, true, queryTask, null);
        assertThat(queryTask.results, notNullValue());
        assertThat(queryTask.results.documentCount, equalTo(Long.valueOf(expectedTotalEndpoints)));
        this.host.resetAuthorizationContext();
    }

    public List<String> createPayingAccountsWithoutCustomProperties(int numEndpoints) throws Throwable {
        List<String> endpointLinks = createMockPayingAwsEndpoints(numEndpoints, null, null, null);

        // Remove customProperties from EndpointState to simulate being created from photon-model API
        for (String endpointLink : endpointLinks) {
            // Xenon serializes a Map with null values as an empty JSON object... so send String
            String patchBody = "{'customProperties': {"
                    + "'createdByEmail': null,"
                    + "'cost_insight:s3bucketName': null"
                    + "}}";
            this.host.sendAndWaitExpectSuccess(Operation.createPatch(UriUtils.buildUri(this.peerUri, endpointLink))
                    .setBody(patchBody));
        }

        String firstEndpointLink = endpointLinks.get(0);
        URI firstApiFriendlyLink = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK,
                UriUtils.getLastPathSegment(firstEndpointLink));

        // If the first endpoint lacks custom properties, we can assume others do too.
        CloudAccountViewState result = this.host.getServiceState(null, CloudAccountViewState.class, firstApiFriendlyLink);
        assertThat(result.createdBy.email, nullValue());
        assertThat(result.customProperties.get(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET), nullValue());
        return endpointLinks;
    }

    /**
     * <p>Helper method that verifies that {@code result} contains expected values associated with
     * a page of expected {@code EndpointState#documentSelfLink}s, provided by {@code endpointLinks}.
     * <p>We expect that for each {@link EndpointState} {@code documentSelfLink} provided in
     * {@code endpointLinks}:
     * <ul>
     * <li>There exists an expanded, non-null {@link CloudAccountViewState} instance in {@code result.documents}
     * <li>The last path segment in {@code EndpointState.documentSelfLink} matches the last path
     * segment in {@code CloudAccountViewState.documentSelfLink}
     * <li>The {@code CloudAccountViewState.name}, {@code CloudAccountViewState.type} fields match the
     * corresponding values in {@code EndpointState}
     * <li>The {@code CloudAccountViewState.credentials} is populated and matches associated values
     * associated with {@code EndpointState.authCredentialsLink}
     * <li>Sizes of {@code CloudAccountViewState.endpointProperties} and {@code CloudAccountViewState.customProperties}
     * match their {@code EndpointState} counterparts
     * </ul>
     * </p>
     *
     * @param endpointLinks a list of expected {@link EndpointState} {@code documentSelfLinks}
     * @param result the response to verify. Ensure that each {@link CloudAccountViewState} in
     *               {@code result.documents} matches {@link EndpointState} objects
     */
    public void verifyEndpointsPage(List<String> endpointLinks, ServiceDocumentQueryResult result) {
        assertThat(result, notNullValue());
        assertThat(result.documents, notNullValue());
        assertThat(result.documents.size(), equalTo(endpointLinks.size()));
        assertThat(result.documentCount, equalTo(Long.valueOf(endpointLinks.size())));
        for (String modelEndpointLink : endpointLinks) {
            String modelId = UriUtils.getLastPathSegment(modelEndpointLink);
            String expectedViewId = UriUtils.buildUriPath(CloudAccountApiService.SELF_LINK, modelId);
            CloudAccountViewState viewState = Utils.fromJson(result.documents.get(expectedViewId), CloudAccountViewState.class);
            verifyEndpoint(modelEndpointLink, viewState);
        }
    }

    /**
     * Similar to {@link #verifyEndpointsPage(List, ServiceDocumentQueryResult)},
     * but this version ensures the {@code result.documentLinks} is returned in the same order as
     * {@code endpointLinks}
     *
     * @see #verifyEndpointsPage(List, ServiceDocumentQueryResult)
     */
    public void verifyEndpointsPageSorted(List<String> endpointLinks, ServiceDocumentQueryResult result) {
        assertThat(result, notNullValue());
        assertThat(result.documents, notNullValue());
        assertThat(result.documentLinks.size(), equalTo(endpointLinks.size()));
        assertThat(result.documents.size(), equalTo(endpointLinks.size()));
        assertThat(result.documentCount, equalTo(Long.valueOf(endpointLinks.size())));

        for (int ndx = 0; ndx < endpointLinks.size(); ndx++) {
            String endpointLink = endpointLinks.get(ndx);
            String id = UriUtils.getLastPathSegment(endpointLink);
            String expectedViewLink = UriUtils.buildUriPath(CloudAccountApiService.SELF_LINK, id);
            String actualViewLink = result.documentLinks.get(ndx);
            assertThat(expectedViewLink, equalTo(actualViewLink));

            CloudAccountViewState viewState = Utils.fromJson(result.documents.get(actualViewLink), CloudAccountViewState.class);
            verifyEndpoint(endpointLink, viewState);
        }
    }

    /** @see #verifyEndpointsPage(List, ServiceDocumentQueryResult) */
    public void verifyEndpoint(String modelEndpointLink, CloudAccountViewState viewState) {
        EndpointState modelState = this.host.getServiceState(
                null, EndpointState.class, UriUtils.buildUri(this.peerUri, modelEndpointLink));
        assertThat(modelState, notNullValue());
        assertThat(viewState, notNullValue());

        Operation getModelEndpointState = this.host.waitForResponse(Operation.createGet(UriUtils
                .buildUri(this.peerUri, modelEndpointLink))
                .setReferer(this.peerUri));

        EndpointState modelEndpointState = getModelEndpointState.getBody(EndpointState.class);

        String endpointOrgId = PhotonControllerCloudAccountUtils.getOrgId(modelEndpointState);
        boolean oldStyleApi = false;

        if (endpointOrgId == null) {
            // For endpoints created using the old style, get it from tenantLinks
            endpointOrgId = PhotonControllerCloudAccountUtils.getOrgId(modelEndpointState.tenantLinks);
            oldStyleApi = true;
        }
        assertThat(endpointOrgId, notNullValue());
        URI orgURI = UriUtils.buildUri(this.peerUri, OrganizationService.FACTORY_LINK, endpointOrgId);

        AuthorizationContext authorizationContext = OperationContext.getAuthorizationContext();
        this.host.setSystemAuthorizationContext();
        OrganizationState organizationState = this.host.getServiceState(null, OrganizationState.class,orgURI);
        this.host.setAuthorizationContext(authorizationContext);
        assertThat(organizationState, notNullValue());

        String modelId = UriUtils.getLastPathSegment(modelEndpointLink);
        String expectedViewId = UriUtils.buildUriPath(CloudAccountApiService.SELF_LINK, modelId);
        assertThat(viewState.documentSelfLink, equalTo(expectedViewId));
        assertThat(viewState.name, equalTo(modelState.name));
        assertThat(viewState.description, equalTo(ACCOUNT_DESCRIPTION));
        // According to fix for VSYM-6291, for vsphere, the type will not match
        if (!viewState.type.equals(EndpointType.vsphere.name())) {
            assertThat(viewState.type, equalTo(modelState.endpointType));
        }

        assertThat(viewState.customProperties.size(), greaterThan(0));

        if (!viewState.type.equals(EndpointType.vsphere.name())) {
            assertThat(viewState.customProperties.get(CUSTOM_PROP_KEY), equalTo(viewState
                    .endpointProperties.get(AWSConstants.AWS_BILLS_S3_BUCKET_NAME_KEY)));
        }

        Set<String> serviceTags = modelState.customProperties.entrySet()
                .stream()
                .filter(entry -> entry.getKey().startsWith(ENDPOINT_CUSTOM_PROPERTY_NAME_PREFIX_SERVICE_TAG))
                .map(entry -> entry.getKey().split(ENDPOINT_CUSTOM_PROPERTY_NAME_PREFIX_SERVICE_TAG)[1])
                .collect(Collectors.toSet());

        if (serviceTags != null && !serviceTags.isEmpty()) {
            assertThat(viewState.services, containsInAnyOrder(serviceTags.toArray()));
        }

        /**
         * verify the exact tags are present
         */
        if (modelState.tagLinks != null && viewState.tags != null) {
            Boolean tagExists;
            assertEquals(modelState.tagLinks.size(), viewState.tags.size());
            for (String link : modelState.tagLinks) {
                tagExists = false;
                TagService.TagState tag = this.host.getServiceState(
                        null, TagService.TagState.class, UriUtils.buildUri(this.peerUri, link));
                for (TagViewState tagView : viewState.tags) {
                    if (tagView.key.equals(tag.key) && tagView.value.equals(tag.value)) {
                        tagExists = true;
                        break;
                    }
                }
                assertTrue(tagExists);
            }
        }

        Credentials creds = viewState.credentials;
        assertThat(creds, notNullValue());
        String privateKeyId;
        String privateKey;
        String endpointPrivateKeyId;
        String endpointPrivateKey = null;
        boolean isEndpointPrivateKeyExpected = false;

        switch (EndpointType.valueOf(viewState.type)) {
        case aws:
            assertThat(creds.aws, notNullValue());
            assertThat(creds.azure, nullValue());
            assertThat(creds.azure_ea, nullValue());
            assertThat(creds.vsphere, nullValue());
            privateKeyId = creds.aws.accessKeyId;
            privateKey = creds.aws.secretAccessKey;
            if (oldStyleApi) {
                assertThat(viewState.customProperties.get(ENDPOINT_CUSTOM_PROPERTY_NAME_LEGACY_ID), nullValue());
            }
            break;
        case vsphere:
            assertThat(creds.aws, nullValue());
            assertThat(creds.azure, nullValue());
            assertThat(creds.azure_ea, nullValue());
            assertThat(creds.vsphere, notNullValue());
            privateKeyId = creds.vsphere.username;
            privateKey = creds.vsphere.password;
            endpointPrivateKey = viewState.endpointProperties.get(PRIVATE_KEY_KEY);

            assertThat(viewState.endpointProperties.get(HOST_NAME_KEY), equalTo(modelState.endpointProperties.get(HOST_NAME_KEY)));
            assertThat(viewState.endpointProperties.get(PRIVATE_CLOUD_NAME_KEY), equalTo(modelState.endpointProperties.get(PRIVATE_CLOUD_NAME_KEY)));
            assertThat(viewState.endpointProperties.get(DC_ID_KEY), equalTo(modelState.endpointProperties.get(DC_ID_KEY)));
            assertThat(viewState.endpointProperties.get(DC_NAME_KEY), equalTo(modelState.endpointProperties.get(DC_NAME_KEY)));
            if (oldStyleApi) {
                // For old style API, endpointState.id will be sent as a custom property, "legacyId"
                assertThat(viewState.customProperties.get(ENDPOINT_CUSTOM_PROPERTY_NAME_LEGACY_ID), equalTo(modelState.id));
            }
            break;
        case azure_ea:
            assertThat(creds.aws, nullValue());
            assertThat(creds.azure, nullValue());
            assertThat(creds.azure_ea, notNullValue());
            assertThat(creds.vsphere, nullValue());
            privateKeyId = creds.azure_ea.enrollmentNumber;
            privateKey = creds.azure_ea.accessKey;
            if (oldStyleApi) {
                assertThat(viewState.customProperties.get(ENDPOINT_CUSTOM_PROPERTY_NAME_LEGACY_ID), nullValue());
            }
            break;
        default:
            assertThat(creds.azure, nullValue());
            assertThat(creds.vsphere, nullValue());
            assertThat(creds.aws, nullValue());
            privateKeyId = null;
            privateKey = null;
            break;
        }

        AuthCredentialsServiceState modelAuth = this.host.getServiceState(
                null, AuthCredentialsServiceState.class, UriUtils.buildUri(this.peerUri, modelState.authCredentialsLink));
        assertThat(modelAuth, notNullValue());

        endpointPrivateKeyId = viewState.endpointProperties.get(PRIVATE_KEYID_KEY);

        if (authorizationContext != null && (authorizationContext.getClaims().getSubject().equals(SERVICE_USER_LINK) ||
                OnboardingUtils.getSystemOauthClientIdLinks().contains(authorizationContext.getClaims().getSubject()))) {
            assertThat(privateKey, equalTo(modelAuth.privateKey));

            assertThat(viewState.endpointProperties.get(PRIVATE_KEYID_KEY), equalTo(modelState.endpointProperties.get(PRIVATE_KEYID_KEY)));
            if (isEndpointPrivateKeyExpected) {
                assertThat(endpointPrivateKey, equalTo(modelState.endpointProperties.get(PRIVATE_KEY_KEY)));
            }

            assertNotNull(viewState.permissions);
            assertThat(viewState.permissions, hasSize(1));
            assertThat(viewState.permissions, hasItem(Permission.READ));
        } else {

            assertThat(endpointPrivateKeyId, equalTo(EndpointUtils
                    .maskCredentialId(viewState.type, modelState.endpointProperties.get
                            (PRIVATE_KEYID_KEY)
                    )));

            if (isEndpointPrivateKeyExpected) {
                assertThat(endpointPrivateKey, equalTo(EndpointUtils.maskPrivateKey()));
            }
            assertThat(privateKeyId, equalTo(modelAuth.privateKeyId));
            assertThat(privateKey, equalTo(EndpointUtils.maskPrivateKey()));
            assertNotNull(viewState.permissions);
            assertFalse(viewState.permissions.isEmpty());
        }

        assertThat(viewState.createdBy.email, equalTo(modelState.customProperties.get(ENDPOINT_CUSTOM_PROPERTY_NAME_CREATEDBY_EMAIL)));
        assertThat(viewState.org, notNullValue());
        assertThat(viewState.org.id, equalTo(organizationState.id));
        assertThat(viewState.creationTimeMicros, equalTo(modelState.creationTimeMicros));
    }
}
