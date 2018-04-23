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

package com.vmware.photon.controller.discovery.endpoints;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static com.vmware.photon.controller.discovery.endpoints.Credentials.AwsCredential.AuthType.ARN;
import static com.vmware.photon.controller.discovery.endpoints.Credentials.AwsCredential.AuthType.KEYS;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_AUTH_TYPE;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.getEndpointUniqueId;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.getPrivateKeyIdFromCredentials;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.isSupportedEndpointType;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.putIfValueNotNull;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.reconstructEndpointProperties;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.ARN_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.EXTERNAL_ID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEYID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEY_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.USER_LINK_KEY;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_TENANT_ID;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import com.vmware.photon.controller.discovery.common.CloudAccountConstants.UpdateAction;
import com.vmware.photon.controller.discovery.common.CollectionStringFieldUpdate;
import com.vmware.photon.controller.discovery.common.PhotonControllerCloudAccountUtils;
import com.vmware.photon.controller.discovery.common.services.OrganizationService;
import com.vmware.photon.controller.discovery.common.services.ProjectService;
import com.vmware.photon.controller.discovery.common.services.UserService;
import com.vmware.photon.controller.discovery.common.services.UserService.UserState;
import com.vmware.photon.controller.discovery.onboarding.OnBoardingTestUtils;
import com.vmware.photon.controller.discovery.onboarding.OnboardingServices;
import com.vmware.photon.controller.discovery.onboarding.OnboardingUtils;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.xenon.common.BasicTestCase;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.UserGroupService;

public class TestEndpointUtils extends BasicTestCase {

    public int numNodes = 3;

    private static final String USER_1_ID = "user1@org-1.com";
    private static final String USER_1_PASSWORD = "passwordforuser1";

    private static final String USER_2_ID = "user2@org-1.com";
    private static final String USER_2_PASSWORD = "passwordforuser2";

    private static final String USER_3_ID = "user3@org-2.com";
    private static final String USER_3_PASSWORD = "passwordforuser3";

    private static final String ORG_1_ID = "org-1";
    private static final String PROJECT_1_ID = OnboardingUtils.getDefaultProjectName();

    private static final String ORG_2_ID = "org-2";
    private static final String PROJECT_2_ID = OnboardingUtils.getDefaultProjectName();

    private URI peerUri;
    private String orgLink;

    private DummyService dummyService;

    public class DummyService extends StatelessService {
        public static final String SELF_LINK = "dummy-service";
    }

    @Override
    public void beforeHostStart(VerificationHost h) {
        h.setAuthorizationEnabled(true);
    }

    public void setUp() throws Throwable {
        CommandLineArgumentParser.parseFromProperties(this);
        host.setUpPeerHosts(this.numNodes);
        host.joinNodesAndVerifyConvergence(this.numNodes, this.numNodes, true);
        host.setNodeGroupQuorum(this.numNodes);

        // start provisioning services on all the hosts
        host.setSystemAuthorizationContext();
        for (VerificationHost h : host.getInProcessHostMap().values()) {
            PhotonModelServices.startServices(h);
            h.waitForServiceAvailable(PhotonModelServices.LINKS);
            h.addPrivilegedService(DummyService.class);
            this.dummyService = new DummyService();
            h.startService(this.dummyService);
        }
        host.resetSystemAuthorizationContext();

        for (VerificationHost h : this.host.getInProcessHostMap().values()) {
            OnBoardingTestUtils.startCommonServices(h);
            h.setSystemAuthorizationContext();
            OnboardingServices.startServices(h, h::addPrivilegedService);
            h.resetAuthorizationContext();
        }

        OnBoardingTestUtils.waitForCommonServicesAvailability(host, host.getPeerHostUri());
        this.peerUri = this.host.getPeerHostUri();
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

    @Test
    public void maskCredentialId() {
        String masked = EndpointUtils.maskCredentialId(EndpointType.aws.name(), "0123456789");
        assertThat(masked, equalTo("***6789"));
    }

    @Test
    public void maskShortCredentialId() {
        String masked = EndpointUtils.maskCredentialId(EndpointType.aws.name(), "0");
        assertThat(masked, equalTo("***0"));
    }

    @Test
    public void maskNullCredentialId() {
        String masked = EndpointUtils.maskCredentialId(EndpointType.aws.name(), null);
        assertThat(masked, nullValue());
    }

    @Test
    public void maskPrivateKey() {
        String masked = EndpointUtils.maskPrivateKey();
        assertThat(masked, equalTo("*********"));
    }

    @Test
    public void testCredentialsEmpty() {
        Credentials credentials = createAwsCredentials("foo", "bar");
        Assert.assertFalse(credentials.isEmpty());

        credentials = createAzureCredentials("foo", "bar", "sub-foo", "tenant-bar");
        Assert.assertFalse(credentials.isEmpty());

        credentials = createVSphereCredentials("foo", "bar");
        Assert.assertFalse(credentials.isEmpty());

        credentials.aws = null;
        credentials.azure = null;
        credentials.vsphere = null;
        Assert.assertTrue(credentials.isEmpty());
    }

    @Test
    public void testAwsCredentialsMappings() {
        Credentials awsCredentials = createAwsCredentials("aws-foo", "aws-bar");
        Map<String, String> awsCredentialEndpointProperties =
                reconstructEndpointProperties(EndpointType.aws.name(), awsCredentials, null, null);
        assertEquals(3, awsCredentialEndpointProperties.size());
        assertEquals("aws-foo", awsCredentialEndpointProperties.get(PRIVATE_KEYID_KEY));
        assertEquals("aws-bar", awsCredentialEndpointProperties.get(PRIVATE_KEY_KEY));
        assertEquals(KEYS.name(), awsCredentialEndpointProperties.get(ENDPOINT_CUSTOM_PROPERTY_NAME_AUTH_TYPE));
    }

    @Test
    public void testAwsArnCredentialsMappings() {
        Credentials awsCredentials = TestEndpointUtils.createAwsCredentialsArn("aws-arn");
        Map<String, String> awsCredentialEndpointProperties =
                reconstructEndpointProperties(EndpointType.aws.name(), awsCredentials, null, null);
        assertEquals(4, awsCredentialEndpointProperties.size());
        assertEquals("", awsCredentialEndpointProperties.get(PRIVATE_KEYID_KEY));
        assertEquals("", awsCredentialEndpointProperties.get(PRIVATE_KEY_KEY));
        assertEquals("aws-arn", awsCredentialEndpointProperties.get(ARN_KEY));
        assertEquals(ARN.name(), awsCredentialEndpointProperties.get(ENDPOINT_CUSTOM_PROPERTY_NAME_AUTH_TYPE));
    }

    @Test
    public void testAzureCredentialsMappings() {
        Credentials azureCredentials = createAzureCredentials("azure-foo", "azure-bar",
                "azure-sub-foo", "azure-tenant-bar");
        Map<String, String> azureCredentialEndpointProperties =
                reconstructEndpointProperties(EndpointType.azure.name(), azureCredentials, null,
                        null);
        assertEquals(4, azureCredentialEndpointProperties.size());
        assertEquals("azure-foo", azureCredentialEndpointProperties.get(PRIVATE_KEYID_KEY));
        assertEquals("azure-bar", azureCredentialEndpointProperties.get(PRIVATE_KEY_KEY));
        assertEquals("azure-sub-foo", azureCredentialEndpointProperties.get(USER_LINK_KEY));
        assertEquals("azure-tenant-bar",
                azureCredentialEndpointProperties.get(AZURE_TENANT_ID));
    }

    @Test
    public void testVSphereCredentialsMapping() {
        Credentials vsphereCredentials = createVSphereCredentials("vsphere-foo", "vsphere-bar");
        Map<String, String> vsphereCredentialEndpointProperties =
                reconstructEndpointProperties(EndpointUtils.VSPHERE_ON_PREM_ADAPTER,
                        vsphereCredentials, null, null);
        assertEquals(2, vsphereCredentialEndpointProperties.size());
        assertEquals("vsphere-foo", vsphereCredentialEndpointProperties.get(PRIVATE_KEYID_KEY));
        assertEquals("vsphere-bar", vsphereCredentialEndpointProperties.get(PRIVATE_KEY_KEY));
    }

    @Test
    public void testGetAwsPrivateKeyIdFromCredentialsReturnsNullForNullEndpointType() {
        Credentials awsCredentials = createAwsCredentials("aws-foo", "aws-bar");
        assertNull(getPrivateKeyIdFromCredentials(null, awsCredentials));
    }

    @Test
    public void testGetAwsPrivateKeyIdFromCredentialsReturnsNullForNullOrEmptyCredentials() {
        Credentials emptyAwsCredentials = createAwsCredentials(null, "aws-bar");
        assertNull(getPrivateKeyIdFromCredentials(EndpointType.aws.name(), null));
        assertNull(getPrivateKeyIdFromCredentials(EndpointType.aws.name(), emptyAwsCredentials));
        assertNull(getPrivateKeyIdFromCredentials(EndpointType.aws.name(), createAwsCredentials
                (null, null)));
        assertNull(getPrivateKeyIdFromCredentials(EndpointType.aws.name(), createAwsCredentials
                (null, "")));
        assertNull(getPrivateKeyIdFromCredentials(EndpointType.aws.name(), createAwsCredentials
                ("", null)));
    }

    @Test
    public void testGetAwsPrivateKeyIdFromCredentials() {
        Credentials awsCredentials = createAwsCredentials("aws-foo", "aws-bar");

        //assertNull if wrong type is passed
        assertNull(getPrivateKeyIdFromCredentials(EndpointType.azure.name(), awsCredentials));
        assertEquals("aws-foo",getPrivateKeyIdFromCredentials(EndpointType.aws.name(),
                awsCredentials));
    }

    @Test
    public void testGetAzurePrivateKeyIdFromCredentials() {

        Credentials azureCredentials = createAzureCredentials("azure-foo", "azure-bar",
                "azure-sub-foo", "azure-tenant-bar");
        Credentials azureEaCredentials = createAzureEACredentials("azure-ea-foo",
                "azure-ea-bar");

        //assertNull if wrong type is passed
        assertNull(getPrivateKeyIdFromCredentials(EndpointType.aws.name(),
                azureCredentials));
        assertEquals("azure-foo",getPrivateKeyIdFromCredentials(EndpointType.azure
                        .name(), azureCredentials));
        assertEquals("azure-ea-foo",getPrivateKeyIdFromCredentials(EndpointType.azure_ea
                        .name(), azureEaCredentials));
    }

    @Test
    public void testGetVspherePrivateKeyIdFromCredentials() {
        Credentials vsphereCredentials = createVSphereCredentials("vsphere-foo",
                "vsphere-bar");

        //assertNull if wrong type is passed
        assertNull(getPrivateKeyIdFromCredentials(EndpointType.aws.name(), vsphereCredentials));
        assertEquals("vsphere-foo",getPrivateKeyIdFromCredentials(EndpointType.vsphere.name(),
                vsphereCredentials));
    }

    @Test
    public void testCredentialsWithEmptyEndpointProperties() {
        Credentials awsCredentials = createAwsCredentials("aws-foo", "aws-bar");
        Map<String, String> awsCredentialEndpointProperties =
                reconstructEndpointProperties(EndpointType.aws.name(), awsCredentials, new
                        HashMap<>(), null);
        assertEquals(3, awsCredentialEndpointProperties.size());
        assertEquals("aws-foo", awsCredentialEndpointProperties.get(PRIVATE_KEYID_KEY));
        assertEquals("aws-bar", awsCredentialEndpointProperties.get(PRIVATE_KEY_KEY));
        assertEquals(KEYS.name(), awsCredentialEndpointProperties.get(ENDPOINT_CUSTOM_PROPERTY_NAME_AUTH_TYPE));
    }

    @Test
    public void testAwsCredentialsWithOverwritableEndpointEntries() {
        Map<String, String> awsCredentialEndpointProperties = new HashMap<>();
        awsCredentialEndpointProperties.put("testProp", "testVal");
        awsCredentialEndpointProperties.put(PRIVATE_KEYID_KEY, "overwrite-foo");
        awsCredentialEndpointProperties.put(PRIVATE_KEY_KEY, "overwrite-bar");

        Credentials awsCredentials = createAwsCredentials("aws-foo", "aws-bar");
        awsCredentialEndpointProperties =
                reconstructEndpointProperties(EndpointType.aws.name(), awsCredentials,
                        awsCredentialEndpointProperties, null);
        assertEquals(4, awsCredentialEndpointProperties.size());
        assertEquals("testVal", awsCredentialEndpointProperties.get("testProp"));
        assertEquals("aws-foo", awsCredentialEndpointProperties.get(PRIVATE_KEYID_KEY));
        assertEquals("aws-bar", awsCredentialEndpointProperties.get(PRIVATE_KEY_KEY));
        assertEquals(KEYS.name(), awsCredentialEndpointProperties.get(ENDPOINT_CUSTOM_PROPERTY_NAME_AUTH_TYPE));
    }

    @Test
    public void testAzureCredentialsWithOverwritableEndpointEntries() {
        Map<String, String> azureCredentialEndpointProperties = new HashMap<>();
        azureCredentialEndpointProperties.put("testProp", "testVal");
        azureCredentialEndpointProperties.put(PRIVATE_KEYID_KEY, "overwrite-foo");
        azureCredentialEndpointProperties.put(PRIVATE_KEY_KEY, "overwrite-bar");
        azureCredentialEndpointProperties.put(USER_LINK_KEY, "overwrite-sub-foo");
        azureCredentialEndpointProperties.put(AZURE_TENANT_ID, "overwrite-tenant-bar");

        Credentials azureCredentials = createAzureCredentials("azure-foo", "azure-bar",
                "azure-sub-foo", "azure-tenant-bar");
        azureCredentialEndpointProperties =
                reconstructEndpointProperties(EndpointType.azure.name(), azureCredentials,
                        azureCredentialEndpointProperties, null);
        assertEquals(5, azureCredentialEndpointProperties.size());
        assertEquals("testVal", azureCredentialEndpointProperties.get("testProp"));
        assertEquals("azure-foo", azureCredentialEndpointProperties.get(PRIVATE_KEYID_KEY));
        assertEquals("azure-bar", azureCredentialEndpointProperties.get(PRIVATE_KEY_KEY));
        assertEquals("azure-sub-foo", azureCredentialEndpointProperties.get(USER_LINK_KEY));
        assertEquals("azure-tenant-bar", azureCredentialEndpointProperties.get(AZURE_TENANT_ID));
    }

    @Test
    public void testVSphereCredentialsWithOverwritableEndpointEntries() {
        Map<String, String> vSphereCredentialEndpointProperties = new HashMap<>();
        vSphereCredentialEndpointProperties.put("testProp", "testVal");
        vSphereCredentialEndpointProperties.put(PRIVATE_KEYID_KEY, "overwrite-foo");
        vSphereCredentialEndpointProperties.put(PRIVATE_KEY_KEY, "overwrite-bar");

        Credentials vSphereCredentials = createVSphereCredentials("vsphere-foo", "vsphere-bar");
        vSphereCredentialEndpointProperties =
                reconstructEndpointProperties(EndpointUtils.VSPHERE_ON_PREM_ADAPTER, vSphereCredentials,
                        vSphereCredentialEndpointProperties,null);
        assertEquals(3, vSphereCredentialEndpointProperties.size());
        assertEquals("testVal", vSphereCredentialEndpointProperties.get("testProp"));
        assertEquals("vsphere-foo", vSphereCredentialEndpointProperties.get(PRIVATE_KEYID_KEY));
        assertEquals("vsphere-bar", vSphereCredentialEndpointProperties.get(PRIVATE_KEY_KEY));
    }

    @Test
    public void testCredentialsIncorrectTypeMapping() {
        // Test AWS credentials, but pass in Azure endpoint type.
        Credentials awsCredentials = createAwsCredentials("aws-foo", "aws-bar");
        Map<String, String> wrongEndpointTypeEndpointProperties =
                reconstructEndpointProperties(EndpointType.azure.name(), awsCredentials, null,null);
        assertEquals(0, wrongEndpointTypeEndpointProperties.size());

        // Test AWS credentials, but pass in an invalid endpoint type.
        Map<String, String> invalidEndpointTypeProperties =
                reconstructEndpointProperties("invalid-endpoint-type", awsCredentials, null,null);
        assertEquals(0, invalidEndpointTypeProperties.size());

        // Test AWS credentials, but pass in a null endpoint type.
        Map<String, String> nullEndpointTypeEndpointProperties =
                reconstructEndpointProperties(null, awsCredentials, null,null);
        assertEquals(0, nullEndpointTypeEndpointProperties.size());
    }

    @Test
    public void testNullCredentials() {
        Map<String, String> nullCredentialsEndpointProperties =
                reconstructEndpointProperties(EndpointType.aws.name(), null, null,null);
        assertEquals(0, nullCredentialsEndpointProperties.size());

        nullCredentialsEndpointProperties =
                reconstructEndpointProperties(EndpointType.azure.name(), null, null,null);
        assertEquals(0, nullCredentialsEndpointProperties.size());

        nullCredentialsEndpointProperties =
                reconstructEndpointProperties(EndpointUtils.VSPHERE_ON_PREM_ADAPTER, null, null,null);
        assertEquals(0, nullCredentialsEndpointProperties.size());

        nullCredentialsEndpointProperties =
                reconstructEndpointProperties(null, null, null,null);
        assertEquals(0, nullCredentialsEndpointProperties.size());
    }

    @Test
    public void testPutValueIfNotNull() {
        Map<String, String> map = new HashMap<>();
        putIfValueNotNull(map, "foo", null);
        assertEquals(0, map.size());

        putIfValueNotNull(map, "foo", "bar");
        assertEquals(1, map.size());
        assertEquals("bar", map.get("foo"));

        putIfValueNotNull(map, "foo", "bar2");
        assertEquals(1, map.size());
        assertEquals("bar2", map.get("foo"));
    }

    @Test
    public void testIsSupportedEndpointType() {
        Assert.assertTrue(isSupportedEndpointType("aws"));
        Assert.assertTrue(isSupportedEndpointType("azure"));
        Assert.assertTrue(isSupportedEndpointType("vsphere"));
        Assert.assertTrue(isSupportedEndpointType("azure_ea"));
        Assert.assertFalse(isSupportedEndpointType("vsphere-on-prem"));
    }

    @Test
    public void testValidateUsersForEndpointOwnershipValidUser() throws Throwable {
        setUp();
        setUpOrgsProjectsAndUsers();
        this.host.setSystemAuthorizationContext();

        Set<String> userEmails = new HashSet<>();
        userEmails.add(USER_1_ID);
        userEmails.add(USER_2_ID);

        TestContext testContext = TestContext.create(1, TimeUnit.MINUTES.toMicros(1));

        Consumer<Operation> onSuccess = o -> {
            QueryTask response = o.getBody(QueryTask.class);
            if (!response.results.documentLinks.contains(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                    PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_1_ID)))
                    || !response.results.documentLinks.contains(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                    PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_1_ID)))) {
                testContext.fail(new Throwable());
            }
            testContext.complete();
        };

        Consumer<Throwable> onFailure = e -> {
            testContext.fail(new Throwable());
        };

        EndpointUtils.validateUsersForEndpointOwnership(this.orgLink, this.dummyService, userEmails, onSuccess, onFailure);
        this.host.testWait(testContext);
    }

    @Test
    public void testValidateUsersForEndpointOwnershipInvalidUser() throws Throwable {
        setUp();
        setUpOrgsProjectsAndUsers();
        this.host.setSystemAuthorizationContext();

        TestContext testContext = TestContext.create(1, TimeUnit.MINUTES.toMicros(1));

        Consumer<Operation> onSuccess = o -> {
            testContext.fail(new Throwable());
        };

        Consumer<Throwable> onFailure = e -> {
            testContext.complete();
        };

        EndpointUtils.validateUsersForEndpointOwnership(this.orgLink, this.dummyService, Collections.singleton(USER_3_ID),
                onSuccess, onFailure);
        this.host.testWait(testContext);
    }

    @Test
    public void testUpdateOwnersForEndpoint() throws Throwable {
        setUp();
        String tenantLink = UriUtils.buildUriPath(ProjectService.FACTORY_LINK, UUID.randomUUID().toString());
        String endpointLink = UriUtils.buildUriPath(EndpointService.FACTORY_LINK, UUID.randomUUID().toString());
        UserState user1State = new UserState();
        user1State.email = "user1@vmware.com";
        user1State.documentSelfLink = UriUtils.buildUriPath(UserService.FACTORY_LINK, PhotonControllerCloudAccountUtils.computeHashWithSHA256("user1@vmware.com"));
        user1State.userGroupLinks = Collections.singleton(EndpointUtils.buildEndpointAuthzArtifactSelfLink(
                UserGroupService.FACTORY_LINK, endpointLink, tenantLink));

        UserState user2State = new UserState();
        user2State.email = "user2@vmware.com";
        user2State.documentSelfLink = UriUtils.buildUriPath(UserService.FACTORY_LINK, PhotonControllerCloudAccountUtils.computeHashWithSHA256("user2@vmware.com"));
        user2State.userGroupLinks = new HashSet<>();

        this.host.setSystemAuthorizationContext();
        Operation postUser1 = this.host.waitForResponse(Operation.createPost(UriUtils.buildUri(this.peerUri, UserService.FACTORY_LINK))
                .setBody(user1State).setReferer(this.host.getUri()));
        Operation postUser2 = this.host.waitForResponse(Operation.createPost(UriUtils.buildUri(this.peerUri, UserService.FACTORY_LINK))
                .setBody(user2State).setReferer(this.host.getUri()));

        assertTrue(postUser1.getStatusCode() == Operation.STATUS_CODE_OK);
        assertTrue(postUser2.getStatusCode() == Operation.STATUS_CODE_OK);

        List<CollectionStringFieldUpdate> updateActionByUserSelfLinks = new ArrayList<>();
        updateActionByUserSelfLinks.add(CollectionStringFieldUpdate.create(UpdateAction.ADD, user2State.documentSelfLink));
        updateActionByUserSelfLinks.add(CollectionStringFieldUpdate.create(UpdateAction.REMOVE, user1State.documentSelfLink));

        TestContext testContext = TestContext.create(1, TimeUnit.MINUTES.toMicros(1));

        Consumer<Void> onSuccess = aVoid -> {
            Operation getUser1 = this.host.waitForResponse(Operation.createGet(UriUtils.buildUri(this.peerUri, user1State.documentSelfLink))
                    .setReferer(this.host.getUri()));
            Operation getUser2 = this.host.waitForResponse(Operation.createGet(UriUtils.buildUri(this.peerUri, user2State.documentSelfLink))
                    .setReferer(this.host.getUri()));

            UserState user1 = getUser1.getBody(UserState.class);
            UserState user2 = getUser2.getBody(UserState.class);

            if (user1.userGroupLinks.contains(EndpointUtils.buildEndpointAuthzArtifactSelfLink(
                    UserGroupService.FACTORY_LINK, endpointLink, tenantLink))) {
                testContext.fail(new Throwable());
            }
            if (!user2.userGroupLinks.contains(EndpointUtils.buildEndpointAuthzArtifactSelfLink(
                    UserGroupService.FACTORY_LINK, endpointLink, tenantLink))) {
                testContext.fail(new Throwable());
            }
            testContext.complete();
        };

        Consumer<Throwable> onFailure = e -> {
            testContext.fail(new Throwable());
        };

        EndpointUtils.updateOwnersForEndpoint(endpointLink, Collections.singletonList(tenantLink), this.dummyService, updateActionByUserSelfLinks, onSuccess, onFailure);
        this.host.testWait(testContext);
    }

    /**
     * Creates a Credentials Object suitable for AWS accounts that are ARN-based.
     *
     * @param arn The Amazon Resource name.
     */
    public static Credentials createAwsCredentialsArn(String arn) {
        Map<String, String> endpointProperties = new HashMap<>();
        endpointProperties.put(ARN_KEY, arn);
        return Credentials.createCredentials(EndpointType.aws.name(), null, endpointProperties);
    }

    /**
     * Creates a Credentials Object suitable for AWS accounts that are ARN-based.
     *
     * @param arn The Amazon Resource name.
     */
    public static Credentials createAwsCredentialsArn(String arn, String externalId) {
        Map<String, String> endpointProperties = new HashMap<>();
        endpointProperties.put(ARN_KEY, arn);
        endpointProperties.put(EXTERNAL_ID_KEY, externalId);
        return Credentials.createCredentials(EndpointType.aws.name(), null, endpointProperties);
    }

    /**
     * Creates a Credentials object suitable for AWS Accounts.
     *
     * @param accessKeyId The AWS access key ID.
     * @param secretAccessKey The AWS secret access key.
     * @return An AWS-suitable Credentials object.
     */
    public static Credentials createAwsCredentials(String accessKeyId, String secretAccessKey) {
        return Credentials.createCredentials(EndpointType.aws.name(),
                createAuthCredentialsServiceState(accessKeyId, secretAccessKey), null);
    }

    /**
     * Creates a Credentials object suitable for Azure Enterprise Agreement (EA) Accounts.
     *
     * @param enrollmentNumber The enrollment number.
     * @param accessKey The secret API access key.
     * @return An Azure EA Credentials object.
     */
    public static Credentials createAzureEACredentials(String enrollmentNumber, String accessKey) {
        return Credentials.createCredentials(EndpointType.azure_ea.name(),
                createAuthCredentialsServiceState(enrollmentNumber, accessKey), null);
    }

    /**
     * Creates a Credentials object suitable for Azure Accounts.
     *
     * @param clientId Azure client ID.
     * @param clientKey Azure client key.
     * @param subscriptionId Azure subscription ID.
     * @param tenantId Azure tenant ID.
     * @return An Azure-suitable Credentials object.
     */
    public static Credentials createAzureCredentials(String clientId, String clientKey,
            String subscriptionId, String tenantId) {
        Map<String, String> endpointProperties = new HashMap<>();
        endpointProperties.put(USER_LINK_KEY, subscriptionId);
        endpointProperties.put(AZURE_TENANT_ID, tenantId);

        return Credentials.createCredentials(EndpointType.azure.name(),
                createAuthCredentialsServiceState(clientId, clientKey), endpointProperties);
    }

    /**
     * Creates a Credentials object suitable for vSphere Accounts.
     *
     * @param username vSphere username.
     * @param password vSphere password.
     * @return A vSphere-suitable Credentials object.
     */
    public static Credentials createVSphereCredentials(String username, String password) {
        return Credentials.createCredentials(EndpointUtils.VSPHERE_ON_PREM_ADAPTER,
                createAuthCredentialsServiceState(username, password), null);
    }

    /**
     * Constructs the base xenon Auth Credentials state.
     *
     * @param privateKeyId Private key ID.
     * @param privateKey Private key.
     * @return Xenon Auth-Credentials document.
     */
    private static AuthCredentialsService.AuthCredentialsServiceState createAuthCredentialsServiceState(
            String privateKeyId, String privateKey) {
        AuthCredentialsService.AuthCredentialsServiceState authCredentialsServiceState =
                new AuthCredentialsService.AuthCredentialsServiceState();
        authCredentialsServiceState.privateKeyId = privateKeyId;
        authCredentialsServiceState.privateKey = privateKey;
        return authCredentialsServiceState;
    }

    private void setUpOrgsProjectsAndUsers() throws Throwable {
        OnBoardingTestUtils.setupUser(this.host, this.peerUri,
                OnBoardingTestUtils.createUserData(USER_1_ID, USER_1_PASSWORD));
        OnBoardingTestUtils
                .setupOrganization(this.host, this.peerUri, ORG_1_ID, USER_1_ID, USER_1_PASSWORD);
        OnBoardingTestUtils.setupProject(this.host, this.peerUri, PROJECT_1_ID, ORG_1_ID, USER_1_ID,
                USER_1_PASSWORD);

        this.orgLink = UriUtils.buildUriPath(OrganizationService.FACTORY_LINK,
                Utils.computeHash(ORG_1_ID));

        OnBoardingTestUtils.setupUser(this.host, this.peerUri,
                OnBoardingTestUtils.createUserData(USER_2_ID, USER_2_PASSWORD));
        OnBoardingTestUtils
                .addUserToOrgOrProject(this.host, this.peerUri, this.orgLink, USER_2_ID,
                        USER_2_ID, USER_2_PASSWORD);

        String org2Link = UriUtils.buildUriPath(OrganizationService.FACTORY_LINK,
                Utils.computeHash(ORG_2_ID));

        OnBoardingTestUtils.setupUser(this.host, this.peerUri,
                OnBoardingTestUtils.createUserData(USER_3_ID, USER_3_PASSWORD));
        OnBoardingTestUtils
                .setupOrganization(this.host, this.peerUri, ORG_2_ID, USER_3_ID, USER_3_PASSWORD);
        OnBoardingTestUtils.setupProject(this.host, this.peerUri, PROJECT_2_ID, ORG_2_ID, USER_3_ID,
                USER_3_PASSWORD);
        OnBoardingTestUtils
                .addUserToOrgOrProject(this.host, this.peerUri, org2Link, USER_3_ID,
                        USER_3_ID, USER_3_PASSWORD);
    }

    @Test
    public void testGetEndpointUniqueId() throws Throwable {
        String endpointLinkNewApi = "/resources/endpoints/orgid-projectid-userid-endpointid";
        String endpointLinkOldApi = "/resources/endpoints/endpointid";

        String endpointIdNewApi = getEndpointUniqueId(endpointLinkNewApi);
        String endpointIdOldApi = getEndpointUniqueId(endpointLinkOldApi);

        assertTrue(endpointIdNewApi.equals("endpointid"));
        assertTrue(endpointIdOldApi.equals("endpointid"));

    }
}
