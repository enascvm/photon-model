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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountAWSBulkImportTaskService.AWSBulkImportTaskState.AWS_WAS_UNABLE_TO_VALIDATE_CREDENTIALS_RESPONSE;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountAWSBulkImportTaskService.AWSBulkImportTaskState.ERROR_CODE_MATCHER;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountAWSBulkImportTaskService.AWSBulkImportTaskState.GENERIC_ERROR_RESPONSE;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountAWSBulkImportTaskService.DATA_INITIALIZATION_INVOCATION_COUNT;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountAWSBulkImportTaskService.DEFAULT_TASK_EXPIRATION_MINUTES;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountAWSBulkImportTaskService.MAXIMUM_ENDPOINT_CREATION_ATTEMPTS_MESSAGE;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountAWSBulkImportTaskService.MAX_ENDPOINT_CREATION_ATTEMPTS;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountApiService.createEndpointTaskState;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_NAME_REQUIRED;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_TAG_NULL_EMPTY;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.INVALID_ENDPOINT_OWNER;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.AWS_BULK_IMPORT_TASK_DATA_INITIALIZATION_BATCH_SIZE_PROP;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.CONTENT_TYPE_MULTIPART_FORM_DATA;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.CONTENT_TYPE_TEXT_CSV;
import static com.vmware.photon.controller.discovery.common.authn.TestAuthContextService.CustomAuthnService.createBaseClaimsBuilder;
import static com.vmware.photon.controller.discovery.common.authn.TestAuthContextService.ORG_ID;
import static com.vmware.photon.controller.discovery.common.authn.TestAuthContextService.USER;
import static com.vmware.photon.controller.discovery.common.authn.TestAuthContextService.userLink;
import static com.vmware.photon.controller.discovery.common.utils.TestMultipartFormDataParser.constructMultipartFormDataOperation;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.ARN_KEY;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.AWS_MASTER_ACCOUNT_ACCESS_KEY_PROPERTY;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.AWS_MASTER_ACCOUNT_SECRET_KEY_PROPERTY;
import static com.vmware.xenon.common.Operation.CR_LF;
import static com.vmware.xenon.common.Operation.REQUEST_AUTH_TOKEN_HEADER;
import static com.vmware.xenon.common.Service.Action.POST;
import static com.vmware.xenon.common.Utils.computeHash;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.vmware.photon.controller.discovery.cloudaccount.CloudAccountAWSBulkImportTaskService.AWSBulkImportTaskState;
import com.vmware.photon.controller.discovery.cloudaccount.CloudAccountAWSBulkImportTaskService.CloudAccountRecordState;
import com.vmware.photon.controller.discovery.cloudaccount.CloudAccountAWSBulkImportTaskService.FailedImportState;
import com.vmware.photon.controller.discovery.cloudaccount.CloudAccountAWSBulkImportTaskService.SuccessfulImportState;
import com.vmware.photon.controller.discovery.cloudaccount.CloudAccountApiService.CloudAccountCreateRequest;
import com.vmware.photon.controller.discovery.cloudaccount.CloudAccountApiService.CloudAccountViewState;
import com.vmware.photon.controller.discovery.common.SymphonyCommonTestUtils;
import com.vmware.photon.controller.discovery.common.TagViewState;
import com.vmware.photon.controller.discovery.common.authn.AuthContextService;
import com.vmware.photon.controller.discovery.common.authn.TestAuthContextService.CustomAuthnService;
import com.vmware.photon.controller.discovery.common.services.OrganizationService;
import com.vmware.photon.controller.discovery.common.services.UserService;
import com.vmware.photon.controller.discovery.common.services.rules.ConfigurationRuleQueryService;
import com.vmware.photon.controller.discovery.common.services.rules.ConfigurationRuleService;
import com.vmware.photon.controller.discovery.common.utils.ErrorUtil;
import com.vmware.photon.controller.discovery.common.utils.MultipartFormDataParser.FormData;
import com.vmware.photon.controller.discovery.common.utils.MultipartFormDataParser.FormData.FormDataBuilder;
import com.vmware.photon.controller.discovery.endpoints.Credentials;
import com.vmware.photon.controller.discovery.endpoints.Credentials.AwsCredential.AuthType;
import com.vmware.photon.controller.discovery.endpoints.EndpointCreationTaskService;
import com.vmware.photon.controller.discovery.endpoints.EndpointCreationTaskService.EndpointCreationTaskState;
import com.vmware.photon.controller.discovery.endpoints.EndpointServices;
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
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils;
import com.vmware.photon.controller.model.adapters.azure.ea.AzureEaAdapters;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.xenon.common.BasicTestCase;
import com.vmware.xenon.common.Claims;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.AuthorizationContext;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.AuthorizationHelper;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.RoleService;

public class TestCloudAccountAWSBulkImportTaskService extends BasicTestCase {

    public String arn = "arn:aws:iam::123456789123:role/test-role";
    public String awsMasterAccountAccessKey;
    public String awsMasterAccountSecretKey;
    public boolean isMock = true;

    // This value must be > 1.
    private static final Integer batchSize = 2;

    private static final String SET_TOKEN_EXPIRATION_HEADER = "set-token-expiration";

    /**
     * Create the custom claims for this custom authentication service
     *
     * @param op The operation to intercept
     * @return Custom claims (with an extra expiration header).
     */
    private Claims createCustomClaims(Operation op) {
        Claims.Builder claimsBuilder = createBaseClaimsBuilder(userLink, ORG_ID);

        if (op.getRequestHeader(SET_TOKEN_EXPIRATION_HEADER) != null) {
            claimsBuilder.setExpirationTime(
                    Long.valueOf(op.getRequestHeader(SET_TOKEN_EXPIRATION_HEADER)));
        }

        return claimsBuilder.getResult();
    }

    @BeforeClass
    public static void setupSystemProperties() {
        System.setProperty(AWSUtils.AWS_MAX_ERROR_RETRY_PROPERTY, "0");
        System.setProperty(AWS_BULK_IMPORT_TASK_DATA_INITIALIZATION_BATCH_SIZE_PROP, String.valueOf(batchSize));
    }

    @Override
    public void beforeHostStart(VerificationHost h) {
        h.setAuthorizationEnabled(true);
        h.setAuthorizationService(new AuthContextService());
        h.setAuthenticationService(new CustomAuthnService(this::createCustomClaims));
        h.setTimeoutSeconds(600);
    }

    @Before
    public void setup() throws Throwable {
        if (!this.isMock) {
            System.setProperty(AWS_MASTER_ACCOUNT_ACCESS_KEY_PROPERTY, this.awsMasterAccountAccessKey);
            System.setProperty(AWS_MASTER_ACCOUNT_SECRET_KEY_PROPERTY, this.awsMasterAccountSecretKey);
        }

        // ignore if any of the required properties are missing
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
        EndpointServices.startServices(host, host::addPrivilegedService);
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
        factories.add(CloudAccountAWSBulkImportTaskService.FACTORY_LINK);
        SymphonyCommonTestUtils.waitForFactoryAvailability(host, host.getUri(), factories);

        this.host.setSystemAuthorizationContext();
        // Setup test user
        AuthorizationHelper authHelper = new AuthorizationHelper(this.host);
        userLink = authHelper.createUserService(this.host, USER);
        String userGroupLink = authHelper.createUserGroup(this.host,
                computeHash(ORG_ID) + "-user",
                QueryTask.Query.Builder.create()
                        .addFieldClause(UserService.UserState.FIELD_NAME_EMAIL, USER)
                        .build());
        String adminGroupLink = authHelper.createUserGroup(this.host,
                computeHash(ORG_ID) + "-admin",
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
        authHelper.createRole(this.host, adminGroupLink, resourceGroupLink,
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
        AuthorizationContext authCtx = this.host.assumeIdentity(userLink);
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
    public void testBulkImportExpirationTime() throws Throwable {
        AuthorizationContext authCtx = this.host.assumeIdentity(userLink);
        // reset the auth context as we want it to be created via the auth service
        this.host.setAuthorizationContext(null);

        long initTimeMicros = System.currentTimeMillis() * 1000;
        AWSBulkImportTaskState task = bulkImportCloudAccounts(
                csvLine("test", "", "", "ak", "sk", true),
                authCtx.getToken())
                .getBody(AWSBulkImportTaskState.class);

        long diffMins = TimeUnit.MICROSECONDS.toMinutes(task.documentExpirationTimeMicros - initTimeMicros);

        // For this size, we expect the expiration to be equal (or slightly greater than) the default
        // expiration time, but not much longer. Thus, the difference in minutes should be roughly
        // 8 days (11520 minutes).
        assertTrue(diffMins >= DEFAULT_TASK_EXPIRATION_MINUTES &&
                diffMins < DEFAULT_TASK_EXPIRATION_MINUTES + 2);
    }

    @Test
    public void testBulkImportInvalidCsv() throws Throwable {
        AuthorizationContext authCtx = this.host.assumeIdentity(userLink);
        // reset the auth context as we want it to be created via the auth service
        this.host.setAuthorizationContext(null);

        Operation taskOp = bulkImportCloudAccounts("invalid", authCtx.getToken());
        assertNull(taskOp);

        ServiceDocumentQueryResult cloudAccountResponse =
                getCloudAccountsResponse(authCtx.getToken());
        assertEquals(0, cloudAccountResponse.documentLinks.size());
    }

    @Test
    public void testBulkImportInvalidAccount() throws Throwable {
        AuthorizationContext authCtx = this.host.assumeIdentity(userLink);
        // reset the auth context as we want it to be created via the auth service
        this.host.setAuthorizationContext(null);

        AWSBulkImportTaskState task = bulkImportCloudAccounts(
                csvLine("invalid-this.arn", "invalid", "", "", "", false),
                authCtx.getToken()).getBody(AWSBulkImportTaskState.class);

        assertEquals(0, task.requestsToProcess.size());
        assertEquals(0, task.cloudAccountImportSuccesses.size());
        assertEquals(1, task.cloudAccountImportFailures.size());

        FailedImportState failedImportState =
                task.cloudAccountImportFailures.stream().findFirst().get();
        assertEquals("invalid-this.arn", failedImportState.csvRecord.identifier);
        assertEquals("invalid", failedImportState.csvRecord.name);
        assertEquals("", failedImportState.csvRecord.description);
        assertEquals("", failedImportState.csvRecord.owners);
        assertEquals("", failedImportState.csvRecord.tags);
        assertEquals("Unable to validate the provided access credentials",
                failedImportState.error.message);

        ServiceDocumentQueryResult cloudAccountResponse =
                getCloudAccountsResponse(authCtx.getToken());
        assertEquals(0, cloudAccountResponse.documentLinks.size());

        assertStats(task.documentSelfLink, DATA_INITIALIZATION_INVOCATION_COUNT, 0);
    }

    @Test
    public void testBulkImportInvalidOwner() throws Throwable {
        AuthorizationContext authCtx = this.host.assumeIdentity(userLink);
        // reset the auth context as we want it to be created via the auth service
        this.host.setAuthorizationContext(null);

        AWSBulkImportTaskState task = bulkImportCloudAccounts(
                csvLine("valid-this.arn", "invalid", "", "invalid@invalid.test", "", true),
                authCtx.getToken()).getBody(AWSBulkImportTaskState.class);

        assertEquals(task.requestsToProcess.size(), 0);
        assertEquals(task.cloudAccountImportSuccesses.size(), 0);
        assertEquals(task.cloudAccountImportFailures.size(), 1);

        FailedImportState failedImportState =
                task.cloudAccountImportFailures.stream().findFirst().get();
        assertEquals("valid-this.arn", failedImportState.csvRecord.identifier);
        assertEquals("invalid", failedImportState.csvRecord.name);
        assertEquals("", failedImportState.csvRecord.description);
        assertEquals("invalid@invalid.test", failedImportState.csvRecord.owners);
        assertEquals("", failedImportState.csvRecord.tags);
        assertEquals("40085: Invalid endpoint owners", failedImportState.error.message);

        ServiceDocumentQueryResult cloudAccountResponse =
                getCloudAccountsResponse(authCtx.getToken());
        assertEquals(0, cloudAccountResponse.documentLinks.size());

        assertStats(task.documentSelfLink, DATA_INITIALIZATION_INVOCATION_COUNT, 0);
    }

    @Test
    public void testBulkImportValidOwners() throws Throwable {
        this.host.setSystemAuthorizationContext();

        // Setup two users that are valid and part of the same organization
        OnBoardingTestUtils.setupUser(this.host, this.host.getUri(),
                OnBoardingTestUtils.createUserData("user1@test.com", "test"));
        OnBoardingTestUtils.addUserToOrgOrProject(this.host, this.host.getUri(),
                UriUtils.buildUriPath(OrganizationService.FACTORY_LINK, Utils.computeHash(ORG_ID)),
                "user1@test.com", "user1@test.com", "test");

        OnBoardingTestUtils.setupUser(this.host, this.host.getUri(),
                OnBoardingTestUtils.createUserData("user2@test.com", "test"));
        OnBoardingTestUtils.addUserToOrgOrProject(this.host, this.host.getUri(),
                UriUtils.buildUriPath(OrganizationService.FACTORY_LINK, Utils.computeHash(ORG_ID)),
                "user2@test.com", "user2@test.com", "test");

        AuthorizationContext authCtx = this.host.assumeIdentity(userLink);
        // reset the auth context as we want it to be created via the auth service
        this.host.setAuthorizationContext(null);

        AWSBulkImportTaskState task = bulkImportCloudAccounts(

                // Create two valid lines - one with a single user, one with two valid users
                csvLine("valid-this.arn-1", "valid1", "", "user1@test.com", "", true) + CR_LF +
                        csvLine("valid-this.arn-2", "valid3", "", "user1@test.com;user2@test.com", "", true) + CR_LF +

                        // Create one invalid line where it is invalid due to a single invalid user
                        // (mixed with one valid user)
                        csvLine("valid-this.arn-3", "invalid", "Invalid because of one of the requested owners", "user1@test.com;user3@test.com", "", true),
                authCtx.getToken()).getBody(AWSBulkImportTaskState.class);

        assertEquals(0, task.requestsToProcess.size());
        assertEquals(2, task.cloudAccountImportSuccesses.size());
        assertEquals(1, task.cloudAccountImportFailures.size());

        // As user3 is not valid to become an endpoint owner, that line request failed
        FailedImportState failedImportState =
                task.cloudAccountImportFailures.stream().findFirst().get();
        assertEquals("valid-this.arn-3", failedImportState.csvRecord.identifier);
        assertEquals("invalid", failedImportState.csvRecord.name);
        assertEquals("Invalid because of one of the requested owners",
                failedImportState.csvRecord.description);
        assertEquals("user1@test.com;user3@test.com", failedImportState.csvRecord.owners);
        assertEquals("", failedImportState.csvRecord.tags);
        assertEquals("40085: Invalid endpoint owners", failedImportState.error.message);

        ServiceDocumentQueryResult cloudAccountResponse =
                getCloudAccountsResponse(authCtx.getToken());
        assertEquals(2, cloudAccountResponse.documentLinks.size());

        assertStats(task.documentSelfLink, DATA_INITIALIZATION_INVOCATION_COUNT, 1);
    }

    @Test
    public void testBulkImportSkipHeaderSkipEmptyLineOneValid() throws Throwable {
        AuthorizationContext authCtx = this.host.assumeIdentity(userLink);
        // reset the auth context as we want it to be created via the auth service
        this.host.setAuthorizationContext(null);

        // Test a header line with mixed case to validate it gets ignored, an empty line
        // (blank strings), a valid line, and an extra line with an extra column that should be
        // ignored. Additionally a mixing of `\n` and `\r\n` for newline testing.
        String headerLine = "IdEntifier,nickname,Description,owners,Tags";
        if (this.isMock) {
            headerLine += ", is mock";
        }
        headerLine += "\n";
        AWSBulkImportTaskState task = bulkImportCloudAccounts(headerLine +
                        ",,,,,\n" +
                        csvLine(this.arn, "valid", "sample description", "", "", this.isMock) + CR_LF +
                        csvLine("\"invalid-this.arn\"", "a", "b", "", "", false) + ",ignored column",
                authCtx.getToken()).getBody(AWSBulkImportTaskState.class);

        assertEquals(0, task.requestsToProcess.size());
        assertEquals(1, task.cloudAccountImportSuccesses.size());
        assertEquals(1, task.cloudAccountImportFailures.size());

        FailedImportState failedImportState = task.cloudAccountImportFailures.stream().findFirst().get();
        assertEquals("invalid-this.arn", failedImportState.csvRecord.identifier);
        assertEquals("a", failedImportState.csvRecord.name);
        assertEquals("b", failedImportState.csvRecord.description);
        assertEquals("", failedImportState.csvRecord.owners);
        assertEquals("", failedImportState.csvRecord.tags);
        assertEquals("Unable to validate the provided access credentials", failedImportState.error.message);

        ServiceDocumentQueryResult cloudAccountResponse =
                getCloudAccountsResponse(authCtx.getToken());
        assertEquals(1, cloudAccountResponse.documentLinks.size());

        assertStats(task.documentSelfLink, DATA_INITIALIZATION_INVOCATION_COUNT, 1);
    }

    @Test
    public void testBulkImportOneAccount() throws Throwable {
        AuthorizationContext authCtx = this.host.assumeIdentity(userLink);
        // reset the auth context as we want it to be created via the auth service
        this.host.setAuthorizationContext(null);

        AWSBulkImportTaskState task = bulkImportCloudAccounts(
                csvLine(this.arn, "valid", "sample description", "", "", this.isMock),
                authCtx.getToken()).getBody(AWSBulkImportTaskState.class);

        assertEquals(0, task.requestsToProcess.size());
        assertEquals(1, task.cloudAccountImportSuccesses.size());
        assertEquals(0, task.cloudAccountImportFailures.size());

        SuccessfulImportState successfulImportState =
                task.cloudAccountImportSuccesses.stream().findFirst().get();
        assertNotNull(successfulImportState.cloudAccountLink);
        assertEquals(this.arn, successfulImportState.csvRecord.identifier);
        assertEquals("valid", successfulImportState.csvRecord.name);
        assertEquals("sample description", successfulImportState.csvRecord.description);

        ServiceDocumentQueryResult cloudAccountResponse =
                getCloudAccountsResponse(authCtx.getToken());

        CloudAccountViewState arnAccount = CloudAccountViewState.class.cast(
                cloudAccountResponse.documents.values().stream().findFirst().get());
        assertEquals("valid", arnAccount.name);
        assertEquals(EndpointType.aws.name(), arnAccount.type);
        assertEquals(this.arn, arnAccount.credentials.aws.arn);

        assertStats(task.documentSelfLink, DATA_INITIALIZATION_INVOCATION_COUNT, 1);
    }

    @Test
    public void testBulkImportBatchCounts() throws Throwable {
        AuthorizationContext authCtx = this.host.assumeIdentity(userLink);
        // reset the auth context as we want it to be created via the auth service
        this.host.setAuthorizationContext(null);

        // The multiplier is arbitrary (> 1).
        // Adding 1 to add an extra batch cycle of < batchSize.
        int numOfAccounts = batchSize * 3 + 1;

        String csv = IntStream.range(0, numOfAccounts).boxed()
                .map(i -> csvLine("arn" + i, "v" + i, "", "", "", true))
                .collect(Collectors.joining("\n"));

        AWSBulkImportTaskState task = bulkImportCloudAccounts(csv, authCtx.getToken())
                .getBody(AWSBulkImportTaskState.class);

        assertEquals(0, task.requestsToProcess.size());
        assertEquals(numOfAccounts, task.cloudAccountImportSuccesses.size());
        assertEquals(0, task.cloudAccountImportFailures.size());

        ServiceDocumentQueryResult cloudAccountResponse =
                getCloudAccountsResponse(authCtx.getToken());
        assertEquals(numOfAccounts, cloudAccountResponse.documentLinks.size());

        // `numOfAccounts` / batchSize, + 1
        assertStats(task.documentSelfLink, DATA_INITIALIZATION_INVOCATION_COUNT,
                (numOfAccounts / batchSize) + 1);
    }

    @Test
    public void testBulkImportOneValidOneInvalid() throws Throwable {
        AuthorizationContext authCtx = this.host.assumeIdentity(userLink);
        // reset the auth context as we want it to be created via the auth service
        this.host.setAuthorizationContext(null);

        AWSBulkImportTaskState task = bulkImportCloudAccounts(
                csvLine(this.arn, "valid", "sample description", "", "", this.isMock) + "\n" +
                        csvLine("", "invalid", "", "", "", false),
                authCtx.getToken()).getBody(AWSBulkImportTaskState.class);

        assertEquals(0, task.requestsToProcess.size());
        assertEquals(1, task.cloudAccountImportSuccesses.size());
        assertEquals(1, task.cloudAccountImportFailures.size());

        SuccessfulImportState successfulImportState =
                task.cloudAccountImportSuccesses.stream().findFirst().get();
        assertEquals(this.arn, successfulImportState.csvRecord.identifier);
        assertEquals("valid", successfulImportState.csvRecord.name);
        assertEquals("sample description", successfulImportState.csvRecord.description);

        FailedImportState failedImportState = task.cloudAccountImportFailures.stream().findFirst().get();
        assertEquals("invalid", failedImportState.csvRecord.name);
        assertEquals("CSV row is missing an identifier.", failedImportState.error.message);

        ServiceDocumentQueryResult cloudAccountResponse = getCloudAccountsResponse(authCtx.getToken());

        assertEquals(1, cloudAccountResponse.documentCount.intValue());
        CloudAccountViewState arnAccount = CloudAccountViewState.class.cast(
                cloudAccountResponse.documents.values().stream().findFirst().get());
        Assert.assertEquals("valid", arnAccount.name);
        Assert.assertEquals(EndpointType.aws.name(), arnAccount.type);
        Assert.assertEquals(this.arn, arnAccount.credentials.aws.arn);

        assertStats(task.documentSelfLink, DATA_INITIALIZATION_INVOCATION_COUNT, 1);
    }

    @Test
    public void testBulkImportDuplicateEntries() throws Throwable {
        AuthorizationContext authCtx = this.host.assumeIdentity(userLink);
        // reset the auth context as we want it to be created via the auth service
        this.host.setAuthorizationContext(null);

        AWSBulkImportTaskState task = bulkImportCloudAccounts(
                csvLine(this.arn, "valid", "sample description",
                        "", "", this.isMock) + "\n" +
                        csvLine(this.arn, "valid", "sample description",
                                "", "", this.isMock) + "\n" +
                        csvLine("", "invalid", "", "", "", false),
                authCtx.getToken()).getBody(AWSBulkImportTaskState.class);

        // The CSV parser will automatically strip out duplicate entries, so we only expect two.
        // Duplicate entries are only denoted when multiple lines are *exactly* the same.
        assertEquals(0, task.requestsToProcess.size());
        assertEquals(1, task.cloudAccountImportSuccesses.size());
        assertEquals(1, task.cloudAccountImportFailures.size());

        SuccessfulImportState successfulImportState =
                task.cloudAccountImportSuccesses.stream().findFirst().get();
        assertEquals(this.arn, successfulImportState.csvRecord.identifier);
        assertEquals("valid", successfulImportState.csvRecord.name);
        assertEquals("sample description", successfulImportState.csvRecord.description);

        FailedImportState failedImportState =
                task.cloudAccountImportFailures.stream().findFirst().get();
        assertEquals("invalid", failedImportState.csvRecord.name);
        assertEquals("CSV row is missing an identifier.", failedImportState.error.message);

        ServiceDocumentQueryResult cloudAccountResponse =
                getCloudAccountsResponse(authCtx.getToken());

        assertEquals(1, cloudAccountResponse.documentCount.intValue());
        CloudAccountViewState arnAccount = CloudAccountViewState.class.cast(
                cloudAccountResponse.documents.values().stream().findFirst().get());
        Assert.assertEquals("valid", arnAccount.name);
        Assert.assertEquals(EndpointType.aws.name(), arnAccount.type);
        Assert.assertEquals(this.arn, arnAccount.credentials.aws.arn);

        assertStats(task.documentSelfLink, DATA_INITIALIZATION_INVOCATION_COUNT, 1);
    }

    @Test
    public void testBulkImportDuplicateKeys() throws Throwable {
        // This test can only run with real keys, as we need to test valid accounts being
        // created.
        assumeFalse(this.isMock);

        AuthorizationContext authCtx = this.host.assumeIdentity(userLink);
        // reset the auth context as we want it to be created via the auth service
        this.host.setAuthorizationContext(null);

        AWSBulkImportTaskState task = bulkImportCloudAccounts(
                this.arn + ",valid,sample description,,," + false + "\n"
                        + this.arn + "duplicate,,,," + false,
                authCtx.getToken()).getBody(AWSBulkImportTaskState.class);

        assertEquals(0, task.requestsToProcess.size());
        assertEquals(1, task.cloudAccountImportSuccesses.size());
        assertEquals(1, task.cloudAccountImportFailures.size());

        ServiceDocumentQueryResult cloudAccountResponse =
                getCloudAccountsResponse(authCtx.getToken());
        assertEquals(1, cloudAccountResponse.documentCount.intValue());
        CloudAccountViewState cloudAccount = CloudAccountViewState.class.cast(
                cloudAccountResponse.documents.values().stream().findFirst().get());
        Assert.assertEquals(EndpointType.aws.name(), cloudAccount.type);
        Assert.assertEquals(this.arn, cloudAccount.credentials.aws.arn);

        FailedImportState failedImportState =
                task.cloudAccountImportFailures.stream().findFirst().get();
        assertEquals(cloudAccount.documentSelfLink, failedImportState.error.message);

        assertStats(task.documentSelfLink, DATA_INITIALIZATION_INVOCATION_COUNT, 1);
    }

    @Test
    public void testBulkImportWithTags() throws Throwable {
        AuthorizationContext authCtx = this.host.assumeIdentity(userLink);
        // reset the auth context as we want it to be created via the auth service
        this.host.setAuthorizationContext(null);

        // Test:
        // a1 -> typical key=value
        // a2 -> two k-vs
        // a3 -> two k-vs, but one is delimited by a comma, ending up with 3 k-v pairs (key1=value, key2=value, key2=value2)
        // a4 -> two k-vs, with one delimited by a comma. However, its the same value, so ends with 2 k-v pairs (key1=value, key2=samevalue).
        // a5 -> one k-v with no value after the =, which should result in being understood as a key with no value, aka (key="")
        // a6 -> one k-v with no =, which should result in being understood as a key with no value, aka (key="")
        // a7 -> no k-v
        // a8 -> kv with multiple '=' characters. All after the first will just be treated as a value
        // a9 -> kv with multiple excessive semicolons. Should be 2 k-vs, (key=value, key3=value)
        // a10 -> kv with no key -> should be 1 k-v, (key="", value="value")
        AWSBulkImportTaskState task = bulkImportCloudAccounts(
                csvLine("arn-1", "a1", "", "", "key=value", true) + CR_LF +
                        csvLine("arn-2", "a2", "", "", "key1=value;key2=value2", true) + CR_LF +
                        csvLine("arn-3", "a3", "", "", "key1=value;key2=value,value2", true) + CR_LF +
                        csvLine("arn-4", "a4", "", "", "key1=value;key2=samevalue,samevalue", true) + CR_LF +
                        csvLine("arn-5", "a5", "", "", "key=", true) + CR_LF +
                        csvLine("arn-6", "a6", "", "", "key", true) + CR_LF +
                        csvLine("arn-7", "a7", "", "", "", true) + CR_LF +
                        csvLine("arn-8", "a8", "", "", "key==;key2=value", true) + CR_LF +
                        csvLine("arn-9", "a9", "", "", "key=value;;key3=value3;", true) + CR_LF +
                        csvLine("arn-10", "a10", "", "", "=value", true),
                authCtx.getToken()).getBody(AWSBulkImportTaskState.class);

        assertEquals(0, task.requestsToProcess.size());
        assertEquals(7, task.cloudAccountImportSuccesses.size());
        assertEquals(3, task.cloudAccountImportFailures.size());

        ServiceDocumentQueryResult cloudAccountResponse = getCloudAccountsResponse(authCtx.getToken());
        assertEquals(task.cloudAccountImportSuccesses.size(), cloudAccountResponse.documentCount.intValue());

        Map<String, CloudAccountViewState> expectedCloudAccounts = new HashMap<>();
        expectedCloudAccounts.put("a1",
                createExpectedCloudAccountViewState("a1", "arn-1",
                        new HashSet<>(Collections.singletonList(
                                new TagViewState("key", "value")))));
        expectedCloudAccounts.put("a2",
                createExpectedCloudAccountViewState("a2", "arn-2",
                        new HashSet<>(Arrays.asList(
                                new TagViewState("key1", "value"),
                                new TagViewState("key2", "value2")))));
        expectedCloudAccounts.put("a3",
                createExpectedCloudAccountViewState("a3", "arn-3",
                        new HashSet<>(Arrays.asList(
                                new TagViewState("key1", "value"),
                                new TagViewState("key2", "value"),
                                new TagViewState("key2", "value2")))));
        expectedCloudAccounts.put("a4",
                createExpectedCloudAccountViewState("a4", "arn-4",
                        new HashSet<>(Arrays.asList(
                                new TagViewState("key1", "value"),
                                new TagViewState("key2", "samevalue")))));
        expectedCloudAccounts.put("a7",
                createExpectedCloudAccountViewState("a7", "arn-7",
                        null));
        expectedCloudAccounts.put("a8",
                createExpectedCloudAccountViewState("a8", "arn-8",
                        new HashSet<>(Arrays.asList(
                                new TagViewState("key", "="),
                                new TagViewState("key2", "value")))));
        expectedCloudAccounts.put("a9",
                createExpectedCloudAccountViewState("a9", "arn-9",
                        new HashSet<>(Arrays.asList(
                                new TagViewState("key", "value"),
                                new TagViewState("key3", "value3")))));

        validateExpectedCloudAccounts(cloudAccountResponse, expectedCloudAccounts);

        assertStats(task.documentSelfLink, DATA_INITIALIZATION_INVOCATION_COUNT, 4);
    }

    @Test
    public void testDownloadCsv() throws Throwable {
        AuthorizationContext authCtx = this.host.assumeIdentity(userLink);
        // reset the auth context as we want it to be created via the auth service
        this.host.setAuthorizationContext(null);

        AWSBulkImportTaskState task = bulkImportCloudAccounts(
                csvLine("valid-arn", "valid", "", "", "key=value", true) + "\n" +
                        csvLine("invalid-arn", "invalid", "", "a@b.c;c@d.e", "a=b", false),
                authCtx.getToken()).getBody(AWSBulkImportTaskState.class);

        assertEquals(0, task.requestsToProcess.size());
        assertEquals(1, task.cloudAccountImportSuccesses.size());
        assertEquals(1, task.cloudAccountImportFailures.size());

        ServiceDocumentQueryResult cloudAccountResponse =
                getCloudAccountsResponse(authCtx.getToken());
        assertEquals(1, cloudAccountResponse.documentCount.intValue());

        assertStats(task.documentSelfLink, DATA_INITIALIZATION_INVOCATION_COUNT, 1);

        assertEquals(task.csvDownloadLink, CloudAccountApiService.createAwsBulkImportCsvLink(task));

        String csvErrors = task.convertToCsv(false, true);
        assertEquals("Identifier,Nickname,Description,Owners,Tags,Error" + CR_LF
                        + String.format("invalid-arn,invalid,,a@b.c;c@d.e,a=b,%s", AWS_WAS_UNABLE_TO_VALIDATE_CREDENTIALS_RESPONSE) + CR_LF,
                csvErrors);

        String csvSuccesses = task.convertToCsv(true, false);
        assertEquals("Identifier,Nickname,Description,Owners,Tags" + CR_LF
                + "valid-arn,valid,,,key=value" + CR_LF, csvSuccesses);

        // Errors always precede successes
        String csvBoth = task.convertToCsv(true, true);
        assertEquals("Identifier,Nickname,Description,Owners,Tags,Error" + CR_LF
                        + String.format("invalid-arn,invalid,,a@b.c;c@d.e,a=b,%s", AWS_WAS_UNABLE_TO_VALIDATE_CREDENTIALS_RESPONSE) + CR_LF
                        + "valid-arn,valid,,,key=value" + CR_LF,
                csvBoth);

        String csvNeither = task.convertToCsv(false, false);
        assertEquals("Identifier,Nickname,Description,Owners,Tags" + CR_LF, csvNeither);
    }

    @Test
    public void testDownloadCSVErrorTypes() throws Throwable {
        AuthorizationContext authCtx = this.host.assumeIdentity(userLink);
        // reset the auth context as we want it to be created via the auth service
        this.host.setAuthorizationContext(null);

        // Setup an account for duplicate-account-testing
        CloudAccountCreateRequest cloudAccountCreateRequest = new CloudAccountCreateRequest();
        cloudAccountCreateRequest.type = EndpointType.aws.name();
        cloudAccountCreateRequest.name = "acc";
        cloudAccountCreateRequest.orgId = ORG_ID;
        Map<String, String> endpointProperties = new HashMap<>();
        endpointProperties.put(ARN_KEY, "arn:aws:iam::123456789123:role/test-role");
        cloudAccountCreateRequest.credentials = Credentials.createCredentials(
                EndpointType.aws.name(), null, endpointProperties);
        cloudAccountCreateRequest.isMock = true;
        EndpointCreationTaskState endpointCreationTaskState =
                createEndpointTaskState(cloudAccountCreateRequest, ORG_ID);

        final String[] createdEndpointLink = new String[1];
        TestContext testContext = new TestContext(1, Duration.ofSeconds(30));
        host.sendWithDeferredResult(
                Operation.createPost(host, EndpointCreationTaskService.FACTORY_LINK)
                        .setBody(endpointCreationTaskState)
                        .addRequestHeader(REQUEST_AUTH_TOKEN_HEADER, authCtx.getToken()))
                .whenComplete((o, e) -> {
                    if (e != null) {
                        testContext.fail(e);
                        return;
                    }

                    createdEndpointLink[0] = UriUtils.buildUriPath(CloudAccountApiService.SELF_LINK,
                            UriUtils.getLastPathSegment(o.getBody(EndpointCreationTaskState.class).endpointLink));
                    testContext.complete();
                });
        testContext.await();

        // Tests:
        // acc1 - Invalid ARN
        // acc2 - Invalid Access Key / Secret key combinations
        // acc3 - Invalid owner
        // acc4 - Invalid tags
        // acc5 - Duplicate account
        // (no name) - No name
        AWSBulkImportTaskState task = bulkImportCloudAccounts(
                csvLine("invalid-arn", "acc1", "", "", "", false) + CR_LF +
                        csvLine("invalid-ak1;invalid-sk1", "acc2", "", "", "", false) + CR_LF +
                        csvLine("valid-arn-no-name", "", "", "", "", true) + CR_LF +
                        csvLine("valid-arn-2", "acc3", "sample", "invaliduser@test.local", "", true) + CR_LF +
                        csvLine("valid-arn-3", "acc4", "", "", "invalid", true) + CR_LF +
                        csvLine("arn:aws:iam::123456789123:role/test-role", "acc5", "", "", "", false),
                authCtx.getToken()).getBody(AWSBulkImportTaskState.class);

        assertEquals(0, task.cloudAccountImportSuccesses.size());
        assertEquals(6, task.cloudAccountImportFailures.size());

        String csvErrors = task.convertToCsv(false, true);
        assertNotNull(csvErrors);

        assertTrue(csvErrors.contains(String.format("invalid-arn,acc1,,,,%s",
                AWS_WAS_UNABLE_TO_VALIDATE_CREDENTIALS_RESPONSE)));
        assertTrue(csvErrors.contains(String.format("invalid-ak1;invalid-sk1,acc2,,,,%s",
                AWS_WAS_UNABLE_TO_VALIDATE_CREDENTIALS_RESPONSE)));
        assertTrue(csvErrors.contains(String.format("valid-arn-no-name,,,,,%s",
                ERROR_CODE_MATCHER.get(ENDPOINT_NAME_REQUIRED.getErrorCode()))));
        assertTrue(csvErrors.contains(String.format("valid-arn-2,acc3,sample,invaliduser@test.local,,%s",
                INVALID_ENDPOINT_OWNER.getMessage())));
        assertTrue(csvErrors.contains(String.format("valid-arn-3,acc4,,,invalid,\"%s\"",
                String.format(ENDPOINT_TAG_NULL_EMPTY.getMessage(), "invalid", ""))));
        assertTrue(csvErrors.contains(String.format("arn:aws:iam::123456789123:role/test-role,acc5,,,,%s",
                String.format("A duplicate account was found at: %s", createdEndpointLink[0]))));

        // The following are additional errors that *can* happen in a multi-node setup, but are
        // difficult to replicate in a unit-test fashion. These tests validate the end-result
        // CSV file.
        AWSBulkImportTaskState separateErrors = new AWSBulkImportTaskState();
        separateErrors.cloudAccountImportFailures = new HashSet<>();

        String otherNodePrefix = "Service " +
                "http://symphony-service.default.svc.cluster.local:8283/provisioning/aws/endpoint-config-adapter " +
                "returned error 500 for PATCH. id 477481 message ";

        ServiceErrorResponse internalError = new ServiceErrorResponse();
        internalError.message = "Service http://127.0.0.1:8283/core/authz/roles returned error 500 " +
                "for POST. id 596138 message (Original id: 503784) POST to " +
                "/core/authz/roles failed. Success: 2,  Fail: 1, quorum: 3, failure threshold: 1";
        internalError.statusCode = 500;
        separateErrors.cloudAccountImportFailures.add(
                new FailedImportState()
                        .withCsvRecord(new CloudAccountRecordState()
                                .withIdentifier("fake")
                                .withDescription("").withTags("").withOwners("")
                                .withName("Internal error record"))
                        .withError(internalError));

        ServiceErrorResponse invalidCredentialsOtherNode = new ServiceErrorResponse();
        invalidCredentialsOtherNode.message = otherNodePrefix +
                "Unable to validate the provided access credentials";
        invalidCredentialsOtherNode.statusCode = 500;
        separateErrors.cloudAccountImportFailures.add(
                new FailedImportState()
                        .withCsvRecord(new CloudAccountRecordState()
                                .withIdentifier("fake")
                                .withDescription("").withTags("").withOwners("")
                                .withName("Invalid credentials"))
                        .withError(invalidCredentialsOtherNode));

        ServiceErrorResponse invalidAccessKeyCredentialsOtherNode = new ServiceErrorResponse();
        invalidAccessKeyCredentialsOtherNode.message = otherNodePrefix +
                "Unable to validate credentials in any AWS region!";
        invalidAccessKeyCredentialsOtherNode.statusCode = 500;
        separateErrors.cloudAccountImportFailures.add(
                new FailedImportState()
                        .withCsvRecord(new CloudAccountRecordState()
                                .withIdentifier("fake")
                                .withDescription("").withTags("").withOwners("")
                                .withName("Invalid credentials - access key"))
                        .withError(invalidAccessKeyCredentialsOtherNode));

        ServiceErrorResponse noNameError = new ServiceErrorResponse();
        noNameError.message = ErrorUtil.message(ENDPOINT_NAME_REQUIRED);
        noNameError.statusCode = 500;
        separateErrors.cloudAccountImportFailures.add(
                new FailedImportState()
                        .withCsvRecord(new CloudAccountRecordState()
                                .withIdentifier("fake")
                                .withDescription("").withTags("").withOwners("")
                                .withName(""))
                        .withError(noNameError));

        ServiceErrorResponse noNameErrorReformatted = new ServiceErrorResponse();
        noNameErrorReformatted.message = ENDPOINT_NAME_REQUIRED.getMessage();
        noNameErrorReformatted.messageId = String.valueOf(ENDPOINT_NAME_REQUIRED.getErrorCode());
        noNameErrorReformatted.statusCode = 500;
        separateErrors.cloudAccountImportFailures.add(
                new FailedImportState()
                        .withCsvRecord(new CloudAccountRecordState()
                                .withIdentifier("fake-2")
                                .withDescription("").withTags("").withOwners("")
                                .withName(""))
                        .withError(noNameErrorReformatted));

        String csvError = separateErrors.convertToCsv(false, true);

        assertTrue(csvError.contains(String.format("fake,Invalid credentials,,,,%s",
                AWS_WAS_UNABLE_TO_VALIDATE_CREDENTIALS_RESPONSE)));
        assertTrue(csvError.contains(String.format("fake,Invalid credentials - access key,,,,%s",
                AWS_WAS_UNABLE_TO_VALIDATE_CREDENTIALS_RESPONSE)));
        assertTrue(csvError.contains(String.format("fake,Internal error record,,,,%s",
                GENERIC_ERROR_RESPONSE)));
        assertTrue(csvError.contains(String.format("fake,,,,,%s",
                ERROR_CODE_MATCHER.get(ENDPOINT_NAME_REQUIRED.getErrorCode()))));
        assertTrue(csvError.contains(String.format("fake-2,,,,,%s",
                ERROR_CODE_MATCHER.get(ENDPOINT_NAME_REQUIRED.getErrorCode()))));
    }

    @Test
    public void testImportWithMultipleAuthTypes() throws Throwable {
        AuthorizationContext authCtx = this.host.assumeIdentity(userLink);
        // reset the auth context as we want it to be created via the auth service
        this.host.setAuthorizationContext(null);

        // Test
        // a1 -> A valid, access key/secret key pair
        // a2 -> An invalid access key/secret key pair
        // a3 -> A valid ARN
        // a4 -> A valid access key/secret key pair, but invalid input type (extra argument)
        // a5 -> An empty identifier
        AWSBulkImportTaskState task = bulkImportCloudAccounts(
                csvLine("ak1;sk1", "a1", "", "", "", true) + CR_LF +
                        csvLine("invalid-ak1;invalid-sk1", "a2", "", "", "",
                                false) + CR_LF +
                        csvLine(this.arn, "a3", "", "", "", true) + CR_LF +
                        csvLine("ak2;sk2;extra", "a4", "", "", "", true) + CR_LF +
                        csvLine("", "a5", "", "", "", true) + CR_LF +
                        csvLine("invalid-arn", "a6", "", "", "", false),
                authCtx.getToken()).getBody(AWSBulkImportTaskState.class);

        assertEquals(0, task.requestsToProcess.size());
        assertEquals(2, task.cloudAccountImportSuccesses.size());
        assertEquals(4, task.cloudAccountImportFailures.size());

        ServiceDocumentQueryResult cloudAccountResponse = getCloudAccountsResponse(authCtx.getToken());
        assertEquals(task.cloudAccountImportSuccesses.size(), cloudAccountResponse.documentCount.intValue());

        Map<String, CloudAccountViewState> expectedCloudAccounts = new HashMap<>();
        expectedCloudAccounts.put("a1",
                createExpectedCloudAccountViewState("a1", "ak1", "sk1", null));
        expectedCloudAccounts.put("a3", createExpectedCloudAccountViewState("a3", this.arn, null));
        validateExpectedCloudAccounts(cloudAccountResponse, expectedCloudAccounts);

        Map<String, FailedImportState> expectedFailures = new HashMap<>();
        expectedFailures.put("a2", new FailedImportState()
                .withCsvRecord(new CloudAccountRecordState()
                        .withName("a2")
                        .withIdentifier("invalid-ak1;invalid-sk1")
                        .withDescription("").withOwners("").withTags("").withMock("false"))
                .withError(ServiceErrorResponse.create(
                        new Exception("Unable to validate credentials in any AWS region!"), 0)));
        expectedFailures.put("a4", new FailedImportState()
                .withCsvRecord(new CloudAccountRecordState()
                        .withName("a4")
                        .withIdentifier("ak2;sk2;extra")
                        .withDescription("").withOwners("").withTags("").withMock("true"))
                .withError(ServiceErrorResponse.create(
                        new Exception("Cannot have more than 1 ';' delimiter in 'identifier' column."), 0)));
        expectedFailures.put("a5", new FailedImportState()
                .withCsvRecord(new CloudAccountRecordState()
                        .withName("a5")
                        .withIdentifier("")
                        .withDescription("").withOwners("").withTags("").withMock("true"))
                .withError(ServiceErrorResponse.create(
                        new Exception("CSV row is missing an identifier."), 0)));
        expectedFailures.put("a6", new FailedImportState()
                .withCsvRecord(new CloudAccountRecordState()
                        .withName("a6")
                        .withIdentifier("invalid-arn")
                        .withDescription("").withOwners("").withTags("").withMock("false"))
                .withError(ServiceErrorResponse.create(
                        new Exception("Unable to validate the provided access credentials"), 0)));

        // Set the expected endpoint creation attempts to be 1 for each record
        expectedFailures.forEach((k, importState) ->
                importState.csvRecord.endpointCreationAttempts = 1);

        validateExpectedFailures(task.cloudAccountImportFailures, expectedFailures);
    }

    @Test
    public void testBulkImportExpiringToken() throws Throwable {
        AuthorizationContext authCtx = this.host.assumeIdentity(userLink);
        // reset the auth context as we want it to be created via the auth service
        this.host.setAuthorizationContext(null);

        final int accountsToAdd = 50;

        StringBuilder csv = new StringBuilder();
        for (int i = 0; i < accountsToAdd; i++) {
            csv.append(csvLine(String.format("a%d;s%d", i, i),
                    String.format("a%d", i), "", "", "", true))
                    .append(CR_LF);
        }

        // Set the expiration to be not much time after the current time. Essentially, just enough
        // time to kick off the initial task but expire right after.
        long tokenExpirationTime = System.currentTimeMillis() + 2000L;
        Operation bulkImportOp = bulkImportCloudAccounts(csv.toString(), authCtx.getToken(),
                tokenExpirationTime);

        AWSBulkImportTaskState response = bulkImportOp.getBody(AWSBulkImportTaskState.class);

        // Validate the task actually still finished
        assertEquals(0, response.requestsToProcess.size());
        assertEquals(accountsToAdd, response.cloudAccountImportSuccesses.size());

        // Validate that the task finished after the token expired
        assertTrue(response.documentUpdateTimeMicros > tokenExpirationTime * 1000);
    }

    @Test
    public void testBulkImportCloudAccountApiService() throws Throwable {
        AuthorizationContext authCtx = this.host.assumeIdentity(userLink);
        // reset the auth context as we want it to be created via the auth service
        this.host.setAuthorizationContext(null);

        String csv = String.format("%s%s%s%s",
                "Identifier,Nickname,Description,Owners,Tags", CR_LF,
                csvLine(this.arn, "My Account", "", "", "", false), CR_LF);

        FormData requestBody = new FormDataBuilder()
                .withName("data")
                .withContentType(CONTENT_TYPE_TEXT_CSV)
                .withFilename("test.csv")
                .withContent(csv)
                .build();

        // Adding as an additional method to show that irrelevant form body's are simply ignored
        // when it comes to the actual task invocation.
        FormData irrelevantFormBody = new FormDataBuilder()
                .withName("ignored")
                .withContent("ignored content")
                .build();

        CloudAccountsApiBulkUploadResponse response = uploadFormDataToCloudAccountsApi(authCtx, requestBody, irrelevantFormBody);
        assertNotNull(response);

        AWSBulkImportTaskState initResponse = response.operation.getBody(AWSBulkImportTaskState.class);
        assertEquals(csv, initResponse.csv);

        // From this point forward, it's general validation of the AWS Bulk Import Task Service
        authCtx = this.host.assumeIdentity(userLink);
        // reset the auth context as we want it to be created via the auth service
        this.host.setAuthorizationContext(null);
        AWSBulkImportTaskState finishedState =
                getAwsBulkImportTaskResponse(initResponse.documentSelfLink, authCtx.getToken());
        assertEquals(0, finishedState.requestsToProcess.size());

        // If these properties were set, we should expect the import to actually be successful.
        // Otherwise, it should fail as expected.
        CloudAccountRecordState recordState;
        if (this.awsMasterAccountAccessKey != null && this.awsMasterAccountSecretKey != null) {
            assertEquals(1, finishedState.cloudAccountImportSuccesses.size());
            assertEquals(0, finishedState.cloudAccountImportFailures.size());
            recordState = finishedState.cloudAccountImportSuccesses.iterator().next().csvRecord;

            // Validate E2E by validating the end CSV file downloaded is proper. In the success
            // case, only a header should be returned with no error response.
            assertEquals("Identifier,Nickname,Description,Owners,Tags,Error" + CR_LF,
                    downloadResponseCSV(finishedState.csvDownloadLink, authCtx));
        } else {
            assertEquals(0, finishedState.cloudAccountImportSuccesses.size());
            assertEquals(1, finishedState.cloudAccountImportFailures.size());

            FailedImportState failedImportState = finishedState.cloudAccountImportFailures.iterator().next();
            assertEquals("Unable to validate the provided access credentials", failedImportState.error.message);
            recordState = failedImportState.csvRecord;

            // Validate E2E by validating the end CSV file downloaded is proper. In the error case,
            // the uploaded row should be returned (with the error condition).
            assertEquals("Identifier,Nickname,Description,Owners,Tags,Error" + CR_LF
                    + String.format("%s,My Account,,,,%s%s", this.arn, AWS_WAS_UNABLE_TO_VALIDATE_CREDENTIALS_RESPONSE, CR_LF),
                    downloadResponseCSV(finishedState.csvDownloadLink, authCtx));
        }

        assertEquals(this.arn, recordState.identifier);
        assertEquals("My Account", recordState.name);
        assertEquals("", recordState.description);
        assertEquals("", recordState.owners);
        assertEquals("", recordState.tags);
    }

    @Test
    public void testBulkImportCloudAccountApiServiceImproperContentType() throws Throwable {
        AuthorizationContext authCtx = this.host.assumeIdentity(userLink);
        // reset the auth context as we want it to be created via the auth service
        this.host.setAuthorizationContext(null);

        FormData requestBody = new FormDataBuilder()
                .withName("data")
                .withContent("")
                .build();

        CloudAccountsApiBulkUploadResponse response =
                uploadFormDataToCloudAccountsApi(authCtx, "invalid", null, requestBody);
        assertNotNull(response);
        assertNotNull(response.failure);
        assertEquals("40130: Invalid content request type. Expecting multipart/form-data",
                response.failure.getMessage());
    }

    @Test
    public void testBulkImportCloudAccountApiServiceTooLargeDataRequest() throws Throwable {
        AuthorizationContext authCtx = this.host.assumeIdentity(userLink);
        // reset the auth context as we want it to be created via the auth service
        this.host.setAuthorizationContext(null);

        char[] chars = new char[1024 * 1024];
        FormData requestBody = new FormDataBuilder()
                .withName("data")
                .withContent(new String(chars))
                .build();

        CloudAccountsApiBulkUploadResponse response = uploadFormDataToCloudAccountsApi(authCtx, requestBody);
        assertNotNull(response);
        assertNotNull(response.failure);
        assertEquals("40220: Request is too large. Request must not exceed 1048576 bytes, but request was 1048707 bytes.",
                response.failure.getMessage());
    }

    @Test
    public void testBulkImportCloudAccountApiServiceUserHasNoOrgs() throws Throwable {
        FormData requestBody = new FormDataBuilder()
                .withName("data")
                .withContent("")
                .build();

        host.setSystemAuthorizationContext();
        CloudAccountsApiBulkUploadResponse response =
                uploadFormDataToCloudAccountsApi(OperationContext.getAuthorizationContext(),
                        requestBody);
        assertNotNull(response);
        assertNotNull(response.failure);
        assertEquals("40125: Could not access user's organization", response.failure.getMessage());
    }

    @Test
    public void testBulkImportCloudAccountApiServiceImproperFormData() throws Throwable {
        AuthorizationContext authCtx = this.host.assumeIdentity(userLink);
        // reset the auth context as we want it to be created via the auth service
        this.host.setAuthorizationContext(null);

        TestContext testContext = new TestContext(1, Duration.ofSeconds(30));
        Throwable[] response = new Throwable[1];
        host.sendWithDeferredResult(
                Operation.createPost(UriUtils.buildUri(this.host,
                        CloudAccountApiService.CLOUD_ACCOUNT_AWS_BULK_IMPORT_PATH_TEMPLATE))
                        .addRequestHeader(REQUEST_AUTH_TOKEN_HEADER, authCtx.getToken())
                        .setContentType(String.format("%s; invalid-boundary=...", CONTENT_TYPE_MULTIPART_FORM_DATA))
                        .setBody(String.format("--...%ssome data", CR_LF).getBytes()))
                .whenComplete((o, e) -> {
                    response[0] = e;
                    testContext.complete();
                });
        testContext.await();
        assertNotNull(response[0]);
        assertEquals("40215: Error parsing multipart/form-data body: Illegal content-type header. Missing valid 'boundary' parameter.",
                response[0].getMessage());
    }

    @Test
    public void testBulkImportCloudAccountApiServiceMissingDataField() throws Throwable {
        AuthorizationContext authCtx = this.host.assumeIdentity(userLink);
        // reset the auth context as we want it to be created via the auth service
        this.host.setAuthorizationContext(null);

        FormData requestBody = new FormDataBuilder()
                .withName("other-name")
                .withContent("")
                .build();

        CloudAccountsApiBulkUploadResponse response =
                uploadFormDataToCloudAccountsApi(authCtx, requestBody);
        assertNotNull(response);
        assertNotNull(response.failure);
        assertEquals("40200: Missing 'data' field in form data", response.failure.getMessage());
    }

    @Test
    public void testBulkImportCloudAccountApiServiceMultipleFiles() throws Throwable {
        AuthorizationContext authCtx = this.host.assumeIdentity(userLink);
        // reset the auth context as we want it to be created via the auth service
        this.host.setAuthorizationContext(null);

        FormData requestBody = new FormDataBuilder()
                .withName("data")
                .withContent("content-1")
                .build();

        FormData requestBody2 = new FormDataBuilder()
                .withName("data")
                .withContent("content-2")
                .build();

        CloudAccountsApiBulkUploadResponse response =
                uploadFormDataToCloudAccountsApi(authCtx, requestBody, requestBody2);
        assertNotNull(response);
        assertNotNull(response.failure);
        assertEquals("40205: Too many files uploaded", response.failure.getMessage());
    }

    @Test
    public void testBulkImportCloudAccountApiServiceInvalidFileExtension() throws Throwable {
        AuthorizationContext authCtx = this.host.assumeIdentity(userLink);
        // reset the auth context as we want it to be created via the auth service
        this.host.setAuthorizationContext(null);

        FormData requestBody = new FormDataBuilder()
                .withName("data")
                .withContent("content")
                .withFilename("file.invalid-ext")
                .build();

        CloudAccountsApiBulkUploadResponse response =
                uploadFormDataToCloudAccountsApi(authCtx, requestBody);
        assertNotNull(response);
        assertNotNull(response.failure);
        assertEquals("40210: Filename must end with '.csv'", response.failure.getMessage());
    }

    @Test
    public void testBulkImportCloudAccountApiServiceInvalidCsvFileUploaded() throws Throwable {
        AuthorizationContext authCtx = this.host.assumeIdentity(userLink);
        // reset the auth context as we want it to be created via the auth service
        this.host.setAuthorizationContext(null);

        FormData requestBody = new FormDataBuilder()
                .withName("data")
                .withContent("invalid-content")
                .withFilename("file.csv")
                .build();

        CloudAccountsApiBulkUploadResponse response =
                uploadFormDataToCloudAccountsApi(authCtx, requestBody);
        assertNotNull(response);
        assertNotNull(response.failure);
        assertEquals("40135: Invalid CSV file for bulk import", response.failure.getMessage());
    }

    @Test
    public void testEndpointCreationRetry() throws Throwable {
        // Delete the Role service, which should cause downstream errors in
        // EndpointCreationTaskService
        host.setSystemAuthorizationContext();
        host.sendWithDeferredResult(Operation.createDelete(host, RoleService.FACTORY_LINK));
        host.resetAuthorizationContext();

        AuthorizationContext authCtx = host.assumeIdentity(userLink);
        // reset the auth context as we want it to be created via the auth service
        host.setAuthorizationContext(null);

        AWSBulkImportTaskState task = bulkImportCloudAccounts(
                csvLine("valid", "valid", "", "", "", true),
                authCtx.getToken()).getBody(AWSBulkImportTaskState.class);

        assertNotNull(task);
        assertEquals(0, task.cloudAccountImportSuccesses.size());
        assertEquals(1, task.cloudAccountImportFailures.size());
        assertEquals(0, task.endpointLinksToEnumerate.size());
        assertEquals(0, task.requestsToProcess.size());

        task.cloudAccountImportFailures.forEach(failedImportState -> {
            assertEquals(MAXIMUM_ENDPOINT_CREATION_ATTEMPTS_MESSAGE,
                    failedImportState.error.message);
            assertEquals(MAX_ENDPOINT_CREATION_ATTEMPTS,
                    failedImportState.csvRecord.endpointCreationAttempts);
        });
    }

    /**
     * Helper class to capture an operation and error response.
     */
    class CloudAccountsApiBulkUploadResponse {
        Operation operation;
        Throwable failure;
    }

    /**
     * Helper function to upload form data to the CloudAccountsApiService.
     *
     * @param authCtx     The authorization context of the sender.
     * @param requestBody The data being sent.
     * @return The response of the import attempt.
     */
    private CloudAccountsApiBulkUploadResponse uploadFormDataToCloudAccountsApi(
            AuthorizationContext authCtx, FormData... requestBody) {
        return uploadFormDataToCloudAccountsApi(authCtx, null, requestBody);
    }

    /**
     * Helper function to upload form data to the CloudAccountsApiService.
     *
     * @param authCtx     The authorization context of the sender.
     * @param contentType A parameter that can be set to override the default content-type.
     * @param requestBody The data being sent.
     * @return The response of the import attempt.
     */
    private CloudAccountsApiBulkUploadResponse uploadFormDataToCloudAccountsApi(
            AuthorizationContext authCtx, String contentType, FormData... requestBody) {
        TestContext testContext = new TestContext(1, Duration.ofSeconds(30));
        CloudAccountsApiBulkUploadResponse[] response = new CloudAccountsApiBulkUploadResponse[1];

        Operation request = constructMultipartFormDataOperation(requestBody)
                .setUri(UriUtils.buildUri(this.host,
                        CloudAccountApiService.CLOUD_ACCOUNT_AWS_BULK_IMPORT_PATH_TEMPLATE))
                .setAction(POST)
                .addRequestHeader(REQUEST_AUTH_TOKEN_HEADER, authCtx.getToken());
        request.setContentLength(request.getBody(byte[].class).length);

        if (contentType != null) {
            request.setContentType(contentType);
        }

        host.sendWithDeferredResult(request)
                .whenComplete((o, e) -> {
                    response[0] = new CloudAccountsApiBulkUploadResponse();
                    response[0].operation = o;
                    response[0].failure = e;
                    testContext.complete();
                });
        testContext.await();
        return response[0];
    }

    /**
     * Helper method to validate the failed import response from a bulk import task against a
     * set of expected failed imports.
     *
     * @param failedImports         The set of failed imports from the bulk import task.
     * @param expectedFailedImports The mapping of expected failed imports.
     */
    private void validateExpectedFailures(Set<FailedImportState> failedImports,
            Map<String, FailedImportState> expectedFailedImports) {
        failedImports
                .forEach(failedImport -> {
                    FailedImportState expectedFailure =
                            expectedFailedImports.get(failedImport.csvRecord.name);
                    assertEquals(expectedFailure.csvRecord, failedImport.csvRecord);
                    assertEquals(expectedFailure.error.message, failedImport.error.message);
                    expectedFailedImports.remove(failedImport.csvRecord.name);
                });

        assertEquals(0, expectedFailedImports.size());
    }

    /**
     * Helper method to validate Cloud Account View query results against a set of expected cloud
     * account (and their properties).
     *
     * @param cloudAccountQueryResult The cloud account query result
     * @param expectedCloudAccounts   The expected cloud account properties to find
     */
    private void validateExpectedCloudAccounts(ServiceDocumentQueryResult cloudAccountQueryResult,
            Map<String, CloudAccountViewState> expectedCloudAccounts) {
        cloudAccountQueryResult.documents.values().stream()
                .map(obj -> Utils.fromJson(obj, CloudAccountViewState.class))
                .forEach(cloudAccountViewState -> {
                    CloudAccountViewState expected = expectedCloudAccounts.get(cloudAccountViewState.name);
                    assertEquals(expected.name, cloudAccountViewState.name);
                    assertEquals(expected.credentials.aws.accessKeyId,
                            cloudAccountViewState.credentials.aws.accessKeyId);
                    assertEquals(expected.credentials.aws.arn,
                            cloudAccountViewState.credentials.aws.arn);
                    assertEquals(expected.tags, cloudAccountViewState.tags);
                    assertEquals(expected.credentials.aws.authType,
                            cloudAccountViewState.credentials.aws.authType);
                    expectedCloudAccounts.remove(cloudAccountViewState.name);
                });

        // Validate that all entries in the expected cloud accounts were found and removed
        assertEquals(0, expectedCloudAccounts.size());
    }

    /**
     * Helper operation to invoke the Cloud Account AWS Bulk Import API.
     *
     * @param csv       The CSV request file (String).
     * @param authToken The user's authorization token.
     * @return The operation of the request
     */
    private Operation bulkImportCloudAccounts(String csv, String authToken) {
        return bulkImportCloudAccounts(csv, authToken, null);
    }

    private Operation bulkImportCloudAccounts(String csv, String authToken, Long expirationMillis) {
        AWSBulkImportTaskState awsBulkImportTaskState = new AWSBulkImportTaskState();
        awsBulkImportTaskState.csv = csv;
        awsBulkImportTaskState.isMock = this.isMock;
        awsBulkImportTaskState.taskInfo = TaskState.createDirect();

        Operation bulkImportOp = Operation.createPost(host, CloudAccountAWSBulkImportTaskService.FACTORY_LINK)
                .addRequestHeader(REQUEST_AUTH_TOKEN_HEADER, authToken)
                .setBody(awsBulkImportTaskState);

        if (expirationMillis != null) {
            bulkImportOp.addRequestHeader(SET_TOKEN_EXPIRATION_HEADER,
                    String.valueOf(TimeUnit.MILLISECONDS.toSeconds(expirationMillis)));
        }

        Operation[] response = new Operation[1];
        TestContext testContext = new TestContext(1, Duration.ofMinutes(10));
        host.sendWithDeferredResult(bulkImportOp)
                .whenComplete((o, t) -> {
                    if (o != null) {
                        response[0] = o;
                    }
                    testContext.complete();
                });
        testContext.await();
        return response[0];
    }

    /**
     * Helper method to retrieve the AWS Bulk Import Task. Waits until the task is in FINISHED
     * state.
     *
     * @param requestLink The task link
     * @param authToken   The user's authorization token.
     * @return The completed task state.
     */
    private AWSBulkImportTaskState getAwsBulkImportTaskResponse(String requestLink,
            String authToken) {
        AWSBulkImportTaskState[] taskResponse = new AWSBulkImportTaskState[1];
        this.host.waitFor("Task not yet completed.", () -> {
            TestContext nextContext = new TestContext(1, Duration.ofMinutes(10));
            this.host.sendWithDeferredResult(
                    Operation.createGet(this.host, requestLink)
                            .addRequestHeader(REQUEST_AUTH_TOKEN_HEADER, authToken))
                    .whenComplete((op, t) -> {
                        if (t != null) {
                            nextContext.fail(t);
                            return;
                        }

                        taskResponse[0] = op.getBody(AWSBulkImportTaskState.class);
                        nextContext.complete();
                    });
            nextContext.await();

            assertNotNull(taskResponse[0]);

            return taskResponse[0].taskInfo.stage.equals(TaskState.TaskStage.FINISHED);
        });

        return taskResponse[0];
    }

    /**
     * Helper method to retrieve the current user's cloud account list.
     *
     * @param authToken The user's authorization token.
     * @return The cloud account API response.
     */
    private ServiceDocumentQueryResult getCloudAccountsResponse(String authToken) {
        Operation[] response = new Operation[1];
        TestContext nextContext = new TestContext(1, Duration.ofSeconds(30));
        this.host.sendWithDeferredResult(
                Operation.createGet(this.host, CloudAccountApiService.SELF_LINK)
                        .addRequestHeader(REQUEST_AUTH_TOKEN_HEADER, authToken))
                .whenComplete((o, e) -> {
                    if (e != null) {
                        nextContext.fail(e);
                        return;
                    }

                    response[0] = o;
                    nextContext.complete();
                });
        nextContext.await();
        return response[0].getBody(ServiceDocumentQueryResult.class);
    }

    /**
     * Helper method to create some expected properties in a cloud account view state.
     *
     * @param name          The name of the cloud account
     * @param arn           The AWS this.arn
     * @param tagViewStates The expected tag view states.
     * @return The expected {@link CloudAccountViewState}.
     */
    private CloudAccountViewState createExpectedCloudAccountViewState(String name, String arn,
            Set<TagViewState> tagViewStates) {
        Map<String, String> endpointProperties = new HashMap<>();
        endpointProperties.put(ARN_KEY, arn);
        return createExpectedCloudAccountViewState(name,
                Credentials.createCredentials(EndpointType.aws.name(), null,
                        endpointProperties), tagViewStates, AuthType.KEYS);
    }

    /**
     * Helper method to create some expected properties in a cloud account view state.
     *
     * @param name          The name of the cloud account.
     * @param accessKey     The AWS access key.
     * @param secretKey     The AWS secret key.
     * @param tagViewStates The expected tag view states.
     * @return The expected {@link CloudAccountViewState}.
     */
    private CloudAccountViewState createExpectedCloudAccountViewState(String name, String accessKey,
            String secretKey, Set<TagViewState> tagViewStates) {
        AuthCredentialsServiceState authCredentialsServiceState = new AuthCredentialsServiceState();
        authCredentialsServiceState.privateKeyId = accessKey;
        authCredentialsServiceState.privateKey = secretKey;

        return createExpectedCloudAccountViewState(name,
                Credentials.createCredentials(EndpointType.aws.name(), authCredentialsServiceState, null),
                tagViewStates, AuthType.ARN);
    }

    /**
     * Helper method to create some expected properties in a cloud account view state.
     *
     * @param name          The name of the cloud account.
     * @param credentials   The credentials object.
     * @param tagViewStates The expected tag view states.
     * @return The expected {@link CloudAccountViewState}.
     */
    private CloudAccountViewState createExpectedCloudAccountViewState(String name,
            Credentials credentials, Set<TagViewState> tagViewStates, AuthType authType) {
        CloudAccountViewState cloudAccountViewState = new CloudAccountViewState();
        cloudAccountViewState.name = name;
        cloudAccountViewState.credentials = credentials;
        cloudAccountViewState.tags = tagViewStates;
        cloudAccountViewState.type = authType.name();
        return cloudAccountViewState;
    }

    /**
     * Helper to construct a CSV-line.
     *
     * @param identifier  The cloud account identifier (ARN).
     * @param name        The cloud account name.
     * @param description The cloud account description.
     * @param owners      The cloud account owners.
     * @param tags        The cloud account custom tags.
     * @return A comma-delimited line ready for CSV.
     */
    private String csvLine(String identifier, String name, String description, String owners,
            String tags, boolean isMock) {
        String csvLine = String.format("%s, %s, %s, \"%s\", \"%s\"", identifier, name,
                description, owners, tags);
        if (this.isMock) {
            csvLine += String.format(",%s", isMock);
        }
        return csvLine;
    }

    /**
     * Helper method to retrieve a generated CSV file from a provided download link.
     *
     * @param downloadLink         The generated download link from a bulk import task service.
     * @param authorizationContext The authorization context.
     * @return The downloaded CSV.
     */
    private String downloadResponseCSV(String downloadLink, AuthorizationContext authorizationContext) {
        TestContext testContext = new TestContext(1, Duration.ofSeconds(30));
        Operation[] csvDownloadResponse = new Operation[1];
        host.sendWithDeferredResult(Operation.createGet(UriUtils.buildUri(host, downloadLink))
                .addRequestHeader(REQUEST_AUTH_TOKEN_HEADER, authorizationContext.getToken()))
                .whenComplete((o, e) -> {
                    if (e != null) {
                        testContext.fail(e);
                        return;
                    }

                    csvDownloadResponse[0] = o;
                    testContext.complete();
                });
        testContext.await();
        return csvDownloadResponse[0].getBody(String.class);
    }

    /**
     * Helper method to assert a stat on the supplied link.
     *
     * @param documentLink  The document which the stats are present on.
     * @param stat          The stat to check
     * @param expectedValue The expected value of the stat
     */
    private void assertStats(String documentLink, String stat, double expectedValue) {
        AuthorizationContext originalContext = OperationContext.getAuthorizationContext();
        host.setSystemAuthorizationContext();
        Operation[] response = new Operation[1];
        TestContext testContext = new TestContext(1, Duration.ofSeconds(10));
        host.sendWithDeferredResult(Operation.createGet(UriUtils.buildStatsUri(UriUtils.buildUri(host, documentLink))))
                .whenComplete((o, e) -> {
                    if (e != null) {
                        testContext.fail(e);
                        return;
                    }

                    response[0] = o;
                    testContext.complete();
                });
        testContext.await();
        host.setAuthorizationContext(originalContext);

        ServiceStats stats = response[0].getBody(ServiceStats.class);
        Assert.assertNotNull(stats.entries);

        if (expectedValue == 0) {
            assertNull(stats.entries.get(stat));
        } else {
            assertEquals(expectedValue, stats.entries.get(stat).latestValue, 0);
        }
    }
}
