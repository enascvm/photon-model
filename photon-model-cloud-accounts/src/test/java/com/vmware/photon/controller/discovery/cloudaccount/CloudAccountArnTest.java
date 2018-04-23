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

import static org.junit.Assume.assumeTrue;

import static com.vmware.photon.controller.discovery.common.authn.TestAuthContextService.ORG_ID;
import static com.vmware.photon.controller.discovery.common.authn.TestAuthContextService.USER;
import static com.vmware.photon.controller.discovery.common.authn.TestAuthContextService.userLink;
import static com.vmware.photon.controller.discovery.endpoints.TestEndpointUtils.createAwsCredentialsArn;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.AWS_ARN_DEFAULT_SESSION_DURATION_SECONDS_PROPERTY;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.AWS_MASTER_ACCOUNT_ACCESS_KEY_PROPERTY;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.AWS_MASTER_ACCOUNT_SECRET_KEY_PROPERTY;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType.aws;
import static com.vmware.xenon.common.Operation.REQUEST_AUTH_TOKEN_HEADER;
import static com.vmware.xenon.common.Utils.computeHash;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.discovery.cloudaccount.CloudAccountApiService.CloudAccountCreateRequest;
import com.vmware.photon.controller.discovery.cloudaccount.CloudAccountApiService.CloudAccountViewState;
import com.vmware.photon.controller.discovery.common.SymphonyCommonTestUtils;
import com.vmware.photon.controller.discovery.common.authn.AuthContextService;
import com.vmware.photon.controller.discovery.common.authn.TestAuthContextService.CustomAuthnService;
import com.vmware.photon.controller.discovery.common.services.OrganizationService;
import com.vmware.photon.controller.discovery.common.services.UserService;
import com.vmware.photon.controller.discovery.common.services.rules.ConfigurationRuleQueryService;
import com.vmware.photon.controller.discovery.common.services.rules.ConfigurationRuleService;
import com.vmware.photon.controller.discovery.endpoints.Credentials;
import com.vmware.photon.controller.discovery.endpoints.EndpointCreationTaskService;
import com.vmware.photon.controller.discovery.onboarding.OnBoardingTestUtils;
import com.vmware.photon.controller.discovery.onboarding.OnboardingServices;
import com.vmware.photon.controller.discovery.onboarding.OnboardingUtils;
import com.vmware.photon.controller.discovery.onboarding.organization.OrganizationCreationService;
import com.vmware.photon.controller.discovery.onboarding.organization.OrganizationCreationService.OrganizationCreationRequest;
import com.vmware.photon.controller.discovery.onboarding.project.ProjectCreationTaskService;
import com.vmware.photon.controller.discovery.onboarding.project.ProjectCreationTaskService.ProjectCreationTaskState;
import com.vmware.photon.controller.discovery.vsphere.VsphereOnPremEndpointAdapterService;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSAdapters;
import com.vmware.photon.controller.model.adapters.azure.ea.AzureEaAdapters;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.xenon.common.BasicTestCase;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.AuthorizationHelper;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.QueryTask;

public class CloudAccountArnTest extends BasicTestCase {

    public String awsMasterAccountAccessKey;
    public String awsMasterAccountSecretKey;
    public String arn;

    @Override
    public void beforeHostStart(VerificationHost h) {
        h.setAuthorizationEnabled(true);
        h.setAuthorizationService(new AuthContextService());
        h.setAuthenticationService(new CustomAuthnService());
    }

    @Before
    public void setup() throws Throwable {
        // ignore if any of the required properties are missing
        assumeTrue(SymphonyCommonTestUtils.isNull(this.awsMasterAccountAccessKey,
                this.awsMasterAccountSecretKey, this.arn));

        System.setProperty(AWS_MASTER_ACCOUNT_ACCESS_KEY_PROPERTY, this.awsMasterAccountAccessKey);
        System.setProperty(AWS_MASTER_ACCOUNT_SECRET_KEY_PROPERTY, this.awsMasterAccountSecretKey);
        System.setProperty(AWS_ARN_DEFAULT_SESSION_DURATION_SECONDS_PROPERTY, String.valueOf(900));
        this.host.setSystemAuthorizationContext();
        PhotonModelServices.startServices(host);
        PhotonModelTaskServices.startServices(host);
        AWSAdapters.startServices(host);
        PhotonModelAdaptersRegistryAdapters.startServices(host);
        AzureEaAdapters.startServices(host);

        OnBoardingTestUtils.startCommonServices(host);
        host.setSystemAuthorizationContext();
        OnboardingServices.startServices(host, host::addPrivilegedService);
        CloudAccountServices.startServices(host, host::addPrivilegedService);
        host.waitForServiceAvailable(CloudAccountApiService.SELF_LINK);
        host.startService(
                Operation.createPost(UriUtils.buildUri(host,
                        VsphereOnPremEndpointAdapterService.class)),
                new VsphereOnPremEndpointAdapterService());
        host.waitForServiceAvailable(VsphereOnPremEndpointAdapterService.SELF_LINK);
        host.addPrivilegedService(ConfigurationRuleQueryService.class);
        host.startServiceAndWait(new ConfigurationRuleQueryService(), ConfigurationRuleQueryService.SELF_LINK, null);
        host.startFactory(new ConfigurationRuleService());

        OnBoardingTestUtils.waitForCommonServicesAvailability(host, host.getUri());

        List<String> factories = new ArrayList<>();
        factories.add(EndpointCreationTaskService.FACTORY_LINK);
        factories.add(EndpointAllocationTaskService.FACTORY_LINK);
        factories.add(CloudAccountQueryTaskService.FACTORY_LINK);
        factories.add(CloudAccountMaintenanceService.FACTORY_LINK);
        SymphonyCommonTestUtils
                .waitForFactoryAvailability(host, host.getUri(), factories);

        // Setup test user
        AuthorizationHelper authHelper = new AuthorizationHelper(this.host);
        userLink = authHelper.createUserService(this.host, USER);
        String userGroupLink = authHelper.createUserGroup(this.host,
                computeHash(ORG_ID) + "-userGroup",
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

        // Create Organization
        OrganizationCreationRequest orgRequest = new OrganizationCreationRequest();
        orgRequest.orgId = ORG_ID;
        orgRequest.userLink = userLink;
        orgRequest.organizationName = "test";
        orgRequest.displayName = "test-display";
        TestContext testContext = new TestContext(1, Duration.ofSeconds(30));
        this.host.sendWithDeferredResult(
                Operation.createPost(this.host, OrganizationCreationService.SELF_LINK)
                        .setBody(orgRequest))
                .whenComplete((o, e) -> {
                    if (e != null) {
                        testContext.fail(e);
                        return;
                    }

                    testContext.complete();
                });
        testContext.await();

        this.host.resetSystemAuthorizationContext();

        // Create the project
        Operation.AuthorizationContext authCtx = this.host.assumeIdentity(userLink);
        // reset the auth context as we want it to be created via the auth service
        this.host.setAuthorizationContext(null);

        ProjectCreationTaskState projectCreationTaskState = new ProjectCreationTaskState();
        projectCreationTaskState.organizationLink =
                UriUtils.buildUriPath(OrganizationService.FACTORY_LINK, Utils.computeHash(ORG_ID));
        projectCreationTaskState.projectName = OnboardingUtils.getDefaultProjectName();
        projectCreationTaskState.userLink = userLink;
        projectCreationTaskState.taskInfo = TaskState.createDirect();
        TestContext nxtContext = new TestContext(1, Duration.ofSeconds(30));
        this.host.sendWithDeferredResult(
                Operation.createPost(this.host, ProjectCreationTaskService.FACTORY_LINK)
                        .setBody(projectCreationTaskState)
                        .addRequestHeader(REQUEST_AUTH_TOKEN_HEADER, authCtx.getToken()))
                .whenComplete((o, e) -> {
                    if (e != null) {
                        nxtContext.fail(e);
                        return;
                    }

                    nxtContext.complete();
                });
        nxtContext.await();
        this.host.resetSystemAuthorizationContext();
    }


    @Test
    public void testValidateArn() throws Throwable {
        Operation.AuthorizationContext authCtx = this.host.assumeIdentity(userLink);
        // reset the auth context as we want it to be created via the auth service
        this.host.setAuthorizationContext(null);

        Operation[] response = new Operation[1];
        TestContext testContext = new TestContext(1, Duration.ofSeconds(30));
        CloudAccountCreateRequest createAccount = new CloudAccountCreateRequest();
        createAccount.orgLink = computeHash(ORG_ID);
        createAccount.type = "aws";
        createAccount.name = "test-arn";
        createAccount.endpointProperties = new HashMap<>();
        createAccount.credentials = createAwsCredentialsArn(this.arn);
        this.host.sendWithDeferredResult(
                Operation.createPost(this.host, CloudAccountApiService.CLOUD_ACCOUNT_VALIDATION_PATH_TEMPLATE)
                        .addRequestHeader(REQUEST_AUTH_TOKEN_HEADER, authCtx.getToken())
                        .setBody(createAccount))
                .whenComplete((o, e) -> {
                    if (e != null) {
                        testContext.fail(e);
                        return;
                    }

                    response[0] = o;
                    testContext.complete();
                });
        testContext.await();

        Assert.assertNotNull(response[0]);
        Assert.assertEquals(Operation.STATUS_CODE_OK, response[0].getStatusCode());
    }

    @Test
    public void testValidateInvalidArn() throws Throwable {
        Operation.AuthorizationContext authCtx = this.host.assumeIdentity(userLink);
        // reset the auth context as we want it to be created via the auth service
        this.host.setAuthorizationContext(null);

        Throwable[] response = new Throwable[1];
        TestContext testContext = new TestContext(1, Duration.ofSeconds(30));
        CloudAccountCreateRequest createAccount = new CloudAccountCreateRequest();
        createAccount.orgLink = computeHash(ORG_ID);
        createAccount.type = "aws";
        createAccount.name = "test-arn";
        createAccount.endpointProperties = new HashMap<>();
        createAccount.credentials = createAwsCredentialsArn(this.arn + "-invalid");
        this.host.sendWithDeferredResult(
                Operation.createPost(this.host, CloudAccountApiService.CLOUD_ACCOUNT_VALIDATION_PATH_TEMPLATE)
                        .addRequestHeader(REQUEST_AUTH_TOKEN_HEADER, authCtx.getToken())
                        .setBody(createAccount))
                .whenComplete((o, e) -> {
                    if (e != null) {
                        response[0] = e;
                        testContext.complete();
                        return;
                    }

                    testContext.fail(new Exception("ARN validation should have failed."));
                });
        testContext.await();

        Assert.assertNotNull(response[0]);
    }

    @Test
    public void testAddArnAccount() throws Throwable {
        Operation.AuthorizationContext authCtx = this.host.assumeIdentity(userLink);
        // reset the auth context as we want it to be created via the auth service
        this.host.setAuthorizationContext(null);

        Operation[] response = new Operation[1];
        TestContext testContext = new TestContext(1, Duration.ofSeconds(30));
        CloudAccountCreateRequest createAccount = new CloudAccountCreateRequest();
        createAccount.orgLink =
                UriUtils.buildUriPath(OrganizationService.FACTORY_LINK, computeHash(ORG_ID));
        createAccount.type = "aws";
        createAccount.name = "test-arn";
        createAccount.endpointProperties = new HashMap<>();
        createAccount.credentials = createAwsCredentialsArn(this.arn);
        this.host.sendWithDeferredResult(
                Operation.createPost(this.host, CloudAccountApiService.SELF_LINK)
                        .addRequestHeader(REQUEST_AUTH_TOKEN_HEADER, authCtx.getToken())
                        .setBody(createAccount))
                .whenComplete((o, e) -> {
                    if (e != null) {
                        testContext.fail(e);
                        return;
                    }

                    response[0] = o;
                    testContext.complete();
                });
        testContext.await();

        Assert.assertNotNull(response[0]);
        Assert.assertEquals(Operation.STATUS_CODE_CREATED, response[0].getStatusCode());

        TestContext nextContext = new TestContext(1, Duration.ofSeconds(30));
        this.host.sendWithDeferredResult(
                Operation.createGet(this.host, CloudAccountApiService.SELF_LINK)
                        .addRequestHeader(REQUEST_AUTH_TOKEN_HEADER, authCtx.getToken()))
                .whenComplete((o, e) -> {
                    if (e != null) {
                        nextContext.fail(e);
                        return;
                    }

                    response[0] = o;
                    nextContext.complete();
                });
        nextContext.await();

        Assert.assertNotNull(response[0]);

        CloudAccountViewState arnAccount = CloudAccountViewState.class.cast(
                response[0].getBody(ServiceDocumentQueryResult.class).documents.values().stream()
                        .findFirst().get());
        Assert.assertEquals("test-arn", arnAccount.name);
        Assert.assertEquals(aws.name(), arnAccount.type);
        Assert.assertEquals(this.arn, arnAccount.credentials.aws.arn);
        Assert.assertEquals(Credentials.AwsCredential.AuthType.ARN,
                arnAccount.credentials.aws.authType);
    }
}
