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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountQueryTaskService.PROPERTY_NAME_QUERY_RESULT_LIMIT;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountTestHelper.ENDPOINT_NAME_PREFIX;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountTestHelper.ORG_1_ID;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountTestHelper.ORG_2_ID;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountTestHelper.USER_1_ID;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountTestHelper.USER_2_ID;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountTestHelper.createMockAwsEndpointArn;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountTestHelper.createMockVsphereEndpoint;
import static com.vmware.photon.controller.discovery.endpoints.Credentials.AwsCredential.AuthType.ARN;
import static com.vmware.photon.controller.discovery.endpoints.Credentials.AwsCredential.AuthType.KEYS;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_AUTH_TYPE;
import static com.vmware.photon.controller.discovery.onboarding.OnboardingUtils.SERVICE_USER_LINK;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType.aws;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.vmware.photon.controller.discovery.cloudaccount.CloudAccountApiService.CloudAccountViewState;
import com.vmware.photon.controller.discovery.cloudaccount.CloudAccountQueryTaskService.CloudAccountQueryTaskState;
import com.vmware.photon.controller.discovery.common.PhotonControllerCloudAccountUtils;
import com.vmware.photon.controller.discovery.common.TagViewState;
import com.vmware.photon.controller.discovery.common.services.UserService;
import com.vmware.photon.controller.discovery.onboarding.OnBoardingTestUtils;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.TestRequestSender;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;
import com.vmware.xenon.services.common.ServiceUriPaths;

public class TestCloudAccountQueryTaskService {
    public CloudAccountTestHelper accountTestHelper;
    public int pageSize = 3;

    private VerificationHost host;
    private URI peerUri;
    private TestRequestSender sender;

    @Before
    public void setUp() throws Throwable {
        System.setProperty(PROPERTY_NAME_QUERY_RESULT_LIMIT, String.valueOf(this.pageSize));
        // TODO: Handle Client Credentials
//        System.setProperty(SymphonyConstants.OAUTH_CLIENT_IDS, "test");
        this.accountTestHelper = CloudAccountTestHelper.create();
        this.accountTestHelper.createSecondUser();
        this.host = this.accountTestHelper.host;
        this.sender = this.host.getTestRequestSender();
        this.peerUri = this.accountTestHelper.peerUri;
    }

    @After
    public void tearDown() throws Throwable {
        this.accountTestHelper.tearDown();
    }

    private CloudAccountQueryTaskState sendQuery() throws Throwable {
        return sendQuery(new CloudAccountQueryTaskState());
    }

    private CloudAccountQueryTaskState sendQuery(CloudAccountQueryTaskState body) throws Throwable {
        CloudAccountQueryTaskState response = postQuery(body);
        response = this.host.waitForFinishedTask(CloudAccountQueryTaskState.class,
                UriUtils.buildUri(this.peerUri, response.documentSelfLink));
        return response;
    }

    private CloudAccountQueryTaskState sendQueryExpectFailure(CloudAccountQueryTaskState body)
            throws Throwable {
        CloudAccountQueryTaskState response = postQuery(body);
        response = this.host.waitForFailedTask(CloudAccountQueryTaskState.class,
                UriUtils.buildUri(this.peerUri, response.documentSelfLink).toString());
        return response;
    }

    private CloudAccountQueryTaskState postQuery(CloudAccountQueryTaskState body) {
        Operation post = Operation.createPost(UriUtils.buildUri(this.peerUri,
                CloudAccountQueryTaskService.FACTORY_LINK)).setBody(body);
        return this.sender.sendAndWait(post, CloudAccountQueryTaskState.class);
    }

    @Test
    public void listSinglePage() throws Throwable {
        int numEndpoints = this.pageSize - 1;
        this.host.assumeIdentity(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_1_ID)));
        List<String> endpointLinks = this.accountTestHelper.createMockAwsEndpoints(numEndpoints, null, null, null);
        CloudAccountQueryTaskState response = sendQuery();

        URI nextPageUri = UriUtils.buildUri(this.peerUri, response.results.nextPageLink);
        assertThat(nextPageUri.getPath(), not(containsString(ServiceUriPaths.DEFAULT_NODE_SELECTOR)));
        ServiceDocumentQueryResult result = this.host.getServiceState(null, ServiceDocumentQueryResult.class, nextPageUri);
        this.accountTestHelper.verifyEndpointsPage(endpointLinks, result);

        assertThat(result.nextPageLink, nullValue());
        assertThat(result.prevPageLink, nullValue());
    }

    @Test
    public void listMultiplePages() throws Throwable {
        int numEndpoints = this.pageSize + 1;
        this.host.assumeIdentity(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_1_ID)));
        this.accountTestHelper.createMockAwsEndpoints(numEndpoints, null, null, null);
        CloudAccountQueryTaskState response = sendQuery();

        // assert page 1
        URI nextPage = UriUtils.buildUri(this.peerUri, response.results.nextPageLink);
        ServiceDocumentQueryResult result = this.host.getServiceState(null, ServiceDocumentQueryResult.class, nextPage);
        // assert page 2 - can't do further asserts due to intermittent Xenon paging bug:
        // https://www.pivotaltracker.com/n/projects/1471320/stories/137039623

        assertThat(result.prevPageLink, nullValue());
        assertThat(result.nextPageLink, notNullValue());

        // assert page 2 - can't do further asserts due to intermittent Xenon paging bug:
        nextPage = UriUtils.buildUri(this.peerUri, result.nextPageLink);
        assertThat(nextPage, notNullValue());
    }


    private CloudAccountQueryTaskState sendCountQuery() throws Throwable {
        CloudAccountQueryTaskState state = new CloudAccountQueryTaskState();
        state.taskInfo = TaskState.createDirect();
        state.filter = new QuerySpecification();
        state.filter.options.add(QueryOption.COUNT);
        return sendQuery(state);
    }

    @Test
    public void count() throws Throwable {
        int numEndpoints = this.pageSize * 3;
        this.host.assumeIdentity(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_1_ID)), null);
        this.accountTestHelper.createMockAwsEndpoints(numEndpoints, null,  null, null);
        CloudAccountQueryTaskState response = sendCountQuery();

        assertThat(response.results, notNullValue());
        assertThat(response.results.documentCount, equalTo(Long.valueOf(numEndpoints)));
    }

    @Test
    public void listSinglePageMultipleUser() throws Throwable {
        this.host.assumeIdentity(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_1_ID)));
        String user1EndpointLink = this.accountTestHelper
                .createMockAwsEndpoint("endpoint-user1", "privateKey-user1", "keyId-user1", null, null, null);
        this.host.resetAuthorizationContext();

        this.host.assumeIdentity(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_2_ID)));
        String user2EndpointLink = this.accountTestHelper
                .createMockAwsEndpoint("endpoint-user2", "privateKey-user2", "keyId-user2", null, null, null);

        List<String> endpointLinks = Arrays.asList(user1EndpointLink, user2EndpointLink);
        CloudAccountQueryTaskState response = sendQuery();

        // assert page 1
        URI nextPage = UriUtils.buildUri(this.peerUri, response.results.nextPageLink);
        ServiceDocumentQueryResult result = this.host.getServiceState(null, ServiceDocumentQueryResult.class, nextPage);
        assertThat(result, notNullValue());
        assertThat(result.documents, notNullValue());
        assertThat(result.documents.size(), equalTo(endpointLinks.size()));
        this.accountTestHelper.verifyEndpointsPage(endpointLinks, result);
    }

    private CloudAccountQueryTaskState createQueryRequestBody(Query query) {
        CloudAccountQueryTaskState state = new CloudAccountQueryTaskState();
        state.filter = new QuerySpecification();
        state.filter.query = query;
        return state;
    }

    private CloudAccountQueryTaskState createQueryRequestBody(QueryTask queryTask) {
        CloudAccountQueryTaskState state = new CloudAccountQueryTaskState();
        state.filter = queryTask.querySpec;
        return state;
    }

    @Test
    public void listWildcardFilterOnUserEmail() throws Throwable {
        this.host.assumeIdentity(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_1_ID)));
        String user1EndpointLink = this.accountTestHelper.createMockAwsEndpoint("endpoint-user1", "privateKey-user1", "keyId-user1", null, null, null);
        this.host.resetAuthorizationContext();

        this.host.assumeIdentity(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_2_ID)));
        this.accountTestHelper.createMockAwsEndpoint("endpoint-user2", "privateKey-user2", "keyId-user2", null, null, null);

        Query filter = Query.Builder.create()
                .addFieldClause(
                        QuerySpecification.buildCompositeFieldName(
                                CloudAccountViewState.FIELD_NAME_CREATED_BY,
                                UserViewState.FIELD_NAME_EMAIL),
                        "*USer1*",
                        MatchType.WILDCARD,
                        Occurance.MUST_OCCUR).build();
        CloudAccountQueryTaskState response = sendQuery(createQueryRequestBody(filter));

        URI nextPageUri = UriUtils.buildUri(this.peerUri, response.results.nextPageLink);
        ServiceDocumentQueryResult result = this.host.getServiceState(null, ServiceDocumentQueryResult.class, nextPageUri);
        assertThat(result, notNullValue());
        assertThat(result.documents, notNullValue());
        assertThat(result.documents.size(), equalTo(1));
        this.accountTestHelper.verifyEndpointsPage(Arrays.asList(user1EndpointLink), result);
    }

    @Test
    public void listTermFilterOnName() throws Throwable {
        int numEndpoints = this.pageSize + 1;
        this.host.assumeIdentity(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_1_ID)));
        List<String> endpointLinks = this.accountTestHelper.createMockAwsEndpoints(numEndpoints, null, null, null);
        int filterOnNdx = ThreadLocalRandom.current().nextInt(0, numEndpoints);
        this.host.log(Level.INFO, "Chose random [ndx=%s] on endpointLinks: %s", filterOnNdx, endpointLinks);

        Query filter = Query.Builder.create()
                .addFieldClause(
                        CloudAccountViewState.FIELD_NAME_NAME,
                        ENDPOINT_NAME_PREFIX + filterOnNdx,
                        MatchType.TERM,
                        Occurance.MUST_OCCUR).build();
        CloudAccountQueryTaskState response = sendQuery(createQueryRequestBody(filter));

        URI nextPageUri = UriUtils.buildUri(this.peerUri, response.results.nextPageLink);
        ServiceDocumentQueryResult result = this.host.getServiceState(null, ServiceDocumentQueryResult.class, nextPageUri);
        assertThat(result, notNullValue());
        assertThat(result.documents, notNullValue());
        assertThat(result.documents.size(), equalTo(1));
        this.accountTestHelper.verifyEndpointsPage(Arrays.asList(endpointLinks.get(filterOnNdx)), result);
    }

    @Test
    public void listTermFilterOnAuthType() throws Throwable {
        int numAwsKeysEndpoints = this.pageSize - 1;
        this.host.assumeIdentity(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_1_ID)));
        this.accountTestHelper.createMockAwsEndpoints(numAwsKeysEndpoints, null, null, null);

        // Create an AWS ARN-based account
        createMockAwsEndpointArn(this.host, this.peerUri, this.accountTestHelper.orgLink,
                "arn-endpoint", "arn:aws:iam::123456789123:role/test-role",
                "test-bucket-arn", false, null, null, null);

        // Create a non-AWS account (vSphere & Azure EA)
        this.accountTestHelper
                .createMockAzureEAEndpoint("my-azure-ea", "foo", "bar");

        createMockVsphereEndpoint(this.host, this.peerUri, ORG_1_ID,
                "vc-endpoint-1", "root", "password",
                "10.112.107.120", "pc-1",
                "vc-dc-name", "vc-dc-id");

        // Check to see if we can filter all the KEYS-based accounts
        Query filter = Query.Builder.create()
                .addFieldClause(
                        QuerySpecification.buildCompositeFieldName(
                                CloudAccountViewState.FIELD_NAME_CREDENTIALS, aws.name(),
                                ENDPOINT_CUSTOM_PROPERTY_NAME_AUTH_TYPE),
                        KEYS.name(),
                        MatchType.TERM,
                        Occurance.MUST_OCCUR)
                .build();
        CloudAccountQueryTaskState response = sendQuery(createQueryRequestBody(filter));

        URI nextPageUri = UriUtils.buildUri(this.peerUri, response.results.nextPageLink);
        ServiceDocumentQueryResult result = this.host.getServiceState(null, ServiceDocumentQueryResult.class, nextPageUri);
        assertThat(result, notNullValue());
        assertThat(result.documents, notNullValue());
        assertThat(result.documents.size(), equalTo(numAwsKeysEndpoints));
        result.documents.values().stream()
                .map(value -> Utils.fromJson(value, CloudAccountViewState.class))
                .forEach(es -> assertThat(EndpointType.aws.name(), equalTo(es.type)));

        // Check to see if we can filter just a single ARN endpoint
        filter = Query.Builder.create()
                .addFieldClause(
                        QuerySpecification.buildCompositeFieldName(
                                CloudAccountViewState.FIELD_NAME_CREDENTIALS, aws.name(),
                                ENDPOINT_CUSTOM_PROPERTY_NAME_AUTH_TYPE),
                        ARN.name(),
                        MatchType.TERM,
                        Occurance.MUST_OCCUR)
                .build();
        response = sendQuery(createQueryRequestBody(filter));

        nextPageUri = UriUtils.buildUri(this.peerUri, response.results.nextPageLink);
        result = this.host.getServiceState(null, ServiceDocumentQueryResult.class, nextPageUri);
        assertThat(result, notNullValue());
        assertThat(result.documents, notNullValue());
        assertThat(result.documents.size(), equalTo(1));

        // Validate that these are the only two authTypes available
        filter = Query.Builder.create()
                .addFieldClause(
                        QuerySpecification.buildCompositeFieldName(
                                CloudAccountViewState.FIELD_NAME_CREDENTIALS, aws.name(),
                                ENDPOINT_CUSTOM_PROPERTY_NAME_AUTH_TYPE),
                        "invalid",
                        MatchType.TERM,
                        Occurance.MUST_OCCUR)
                .build();
        response = sendQuery(createQueryRequestBody(filter));

        assertThat(response.results.nextPageLink, nullValue());
        result.documents.values().stream()
                .map(value -> Utils.fromJson(value, CloudAccountViewState.class))
                .forEach(es -> assertThat(EndpointType.aws.name(), equalTo(es.type)));
    }

    @Test
    public void listTermFilterOnType() throws Throwable {
        int numEndpoints = this.pageSize - 1;
        this.host.assumeIdentity(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_1_ID)));
        List<String> endpointLinks = this.accountTestHelper.createMockAwsEndpoints(numEndpoints, null, null, null);
        Query awsFilter = Query.Builder.create()
                .addCaseInsensitiveFieldClause(
                        CloudAccountViewState.FIELD_NAME_TYPE,
                        EndpointType.aws.name(),
                        MatchType.TERM,
                        Occurance.MUST_OCCUR).build();
        CloudAccountQueryTaskState response = sendQuery(createQueryRequestBody(awsFilter));

        assertThat(response.results.nextPageLink, notNullValue());
        URI nextPageUri = UriUtils.buildUri(this.peerUri, response.results.nextPageLink);
        ServiceDocumentQueryResult result = this.host.getServiceState(null, ServiceDocumentQueryResult.class, nextPageUri);
        assertThat(result, notNullValue());
        assertThat(result.documents, notNullValue());
        assertThat(result.documents.size(), equalTo(numEndpoints));
        this.accountTestHelper.verifyEndpointsPage(endpointLinks, result);

        Query azureFilter = Query.Builder.create()
                .addCaseInsensitiveFieldClause(
                        CloudAccountViewState.FIELD_NAME_TYPE,
                        EndpointType.azure.name(),
                        MatchType.TERM,
                        Occurance.MUST_OCCUR).build();
        response = sendQuery(createQueryRequestBody(azureFilter));

        assertThat(response.results.nextPageLink, nullValue());
        assertThat(response.results.documentCount, equalTo(0L));
    }

    @Test
    public void listTermFilterOnTypeWithOrClause() throws Throwable {
        this.host.assumeIdentity(
                UriUtils.buildUriPath(UserService.FACTORY_LINK, PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_1_ID)));
        List<String> endpointLinks = this.accountTestHelper.createMockAwsEndpoints(1, null,null, null);
        String vSphereEndpointLink = createMockVsphereEndpoint(this.host,
                this.peerUri, ORG_1_ID,
                "vc-local-test-endpoint-2", "root", "password",
                "10.112.107.120", "privateCloud2",
                "vc-dc-name", "vc-dc-id");
        EndpointState azureEndpointState = this.accountTestHelper
                .createMockAzureEAEndpoint("my-azure-ea", "foo", "bar");

        //the results should include vSphere and AWS endpoints.
        endpointLinks.add(vSphereEndpointLink);

        Query awsORvSphereFilter = Query.Builder.create()
                .addCaseInsensitiveFieldClause(
                        CloudAccountViewState.FIELD_NAME_TYPE,
                        EndpointType.aws.name(),
                        MatchType.TERM,
                        Occurance.SHOULD_OCCUR)
                .addCaseInsensitiveFieldClause(
                        CloudAccountViewState.FIELD_NAME_TYPE,
                        EndpointType.vsphere.name(),
                        MatchType.TERM,
                        Occurance.SHOULD_OCCUR)
                .build();
        CloudAccountQueryTaskState response = sendQuery(createQueryRequestBody(awsORvSphereFilter));

        assertThat(response.results.nextPageLink, notNullValue());
        URI nextPageUri = UriUtils.buildUri(this.peerUri, response.results.nextPageLink);
        ServiceDocumentQueryResult result = this.host.getServiceState(null,
                ServiceDocumentQueryResult.class, nextPageUri);
        assertThat(result, notNullValue());
        assertThat(result.documents, notNullValue());
        assertThat(result.documents.size(), equalTo(2));
        this.accountTestHelper.verifyEndpointsPage(endpointLinks, result);

        //the results should include Azure EA and AWS endpoints.
        endpointLinks.remove(vSphereEndpointLink);
        endpointLinks.add(azureEndpointState.documentSelfLink);

        Query awsORvAzureFilter = Query.Builder.create()
                .addCaseInsensitiveFieldClause(
                        CloudAccountViewState.FIELD_NAME_TYPE,
                        EndpointType.aws.name(),
                        MatchType.TERM,
                        Occurance.SHOULD_OCCUR)
                .addCaseInsensitiveFieldClause(
                        CloudAccountViewState.FIELD_NAME_TYPE,
                        EndpointType.azure_ea.name(),
                        MatchType.TERM,
                        Occurance.SHOULD_OCCUR)
                .build();
        CloudAccountQueryTaskState response2 = sendQuery(createQueryRequestBody(awsORvAzureFilter));

        assertThat(response2.results.nextPageLink, notNullValue());
        URI nextPageUri2 = UriUtils.buildUri(this.peerUri, response2.results.nextPageLink);
        ServiceDocumentQueryResult result2 = this.host.getServiceState(null,
                ServiceDocumentQueryResult.class, nextPageUri2);
        assertThat(result2, notNullValue());
        assertThat(result2.documents, notNullValue());
        assertThat(result2.documents.size(), equalTo(2));
        this.accountTestHelper.verifyEndpointsPage(endpointLinks, result2);
    }

    @Test
    public void testFilterOnServiceTag() throws Throwable {
        this.host.assumeIdentity(
                UriUtils.buildUriPath(UserService.FACTORY_LINK, PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_1_ID)));

        Set<String> services1 = new HashSet<>();
        services1.add("service_A");
        services1.add("service_b");

        Set<String> services2 = new HashSet<>();
        services2.add("service_b");
        services2.add("service_c");

        String awsEndpointLink1 = this.accountTestHelper.createMockAwsEndpoint("aws-endpoint-1", "foo", "bar", services1, null, null);
        String awsEndpointLink2 = this.accountTestHelper.createMockAwsEndpoint("aws-endpoint-2", "foo", "bar", services2, null, null);
        this.accountTestHelper.createMockAwsEndpoint("aws-endpoint-3", "foo", "bar", null, null, null);

        List<String> endpointLinks = new ArrayList<>();
        endpointLinks.add(awsEndpointLink1);

        Query awsFilter = Query.Builder.create()
                .addFieldClause(
                        CloudAccountViewState.FIELD_NAME_SERVICES,
                        "service_A",
                        MatchType.TERM,
                        Occurance.MUST_OCCUR)
                .build();

        CloudAccountQueryTaskState response = sendQuery(createQueryRequestBody(awsFilter));

        assertThat(response.results.nextPageLink, notNullValue());
        URI nextPageUri = UriUtils.buildUri(this.peerUri, response.results.nextPageLink);
        ServiceDocumentQueryResult result = this.host.getServiceState(null, ServiceDocumentQueryResult.class, nextPageUri);
        assertThat(result, notNullValue());
        assertThat(result.documents, notNullValue());
        assertThat(result.documents.size(), equalTo(1));
        this.accountTestHelper.verifyEndpointsPage(endpointLinks, result);

        endpointLinks.add(awsEndpointLink2);

        awsFilter = Query.Builder.create()
                .addFieldClause(
                        CloudAccountViewState.FIELD_NAME_SERVICES,
                        "service_b",
                        MatchType.TERM,
                        Occurance.MUST_OCCUR)
                .build();

        response = sendQuery(createQueryRequestBody(awsFilter));

        assertThat(response.results.nextPageLink, notNullValue());
        nextPageUri = UriUtils.buildUri(this.peerUri, response.results.nextPageLink);
        result = this.host.getServiceState(null, ServiceDocumentQueryResult.class, nextPageUri);
        assertThat(result, notNullValue());
        assertThat(result.documents, notNullValue());
        assertThat(result.documents.size(), equalTo(2));
        this.accountTestHelper.verifyEndpointsPage(endpointLinks, result);

        awsFilter = Query.Builder.create()
                .addFieldClause(
                        CloudAccountViewState.FIELD_NAME_SERVICES,
                        "service_A",
                        MatchType.TERM,
                        Occurance.SHOULD_OCCUR)
                .addFieldClause(
                        CloudAccountViewState.FIELD_NAME_SERVICES,
                        "service_c",
                        MatchType.TERM,
                        Occurance.SHOULD_OCCUR)
                .build();

        response = sendQuery(createQueryRequestBody(awsFilter));

        assertThat(response.results.nextPageLink, notNullValue());
        nextPageUri = UriUtils.buildUri(this.peerUri, response.results.nextPageLink);
        result = this.host.getServiceState(null, ServiceDocumentQueryResult.class, nextPageUri);
        assertThat(result, notNullValue());
        assertThat(result.documents, notNullValue());
        assertThat(result.documents.size(), equalTo(2));
        this.accountTestHelper.verifyEndpointsPage(endpointLinks, result);

        awsFilter = Query.Builder.create()
                .addFieldClause(
                        CloudAccountViewState.FIELD_NAME_SERVICES,
                        "service_d",
                        MatchType.TERM,
                        Occurance.MUST_OCCUR)
                .build();

        response = sendQuery(createQueryRequestBody(awsFilter));
        assertThat(response.results.nextPageLink, nullValue());
    }

    @Test
    public void testMustNotOccurServiceTag() throws Throwable {
        this.host.assumeIdentity(
                UriUtils.buildUriPath(UserService.FACTORY_LINK,
                        PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_1_ID)));

        Set<String> services1 = new HashSet<>();
        services1.add("service_A");
        services1.add("service_b");

        Set<String> services2 = new HashSet<>();
        services2.add("service_b");
        services2.add("service_c");

        Set<String> services4 = new HashSet<>();
        services2.add("service_c");

        Set<String> services5 = new HashSet<>();
        services2.add("service_A");

        this.accountTestHelper.createMockAwsEndpoint("aws-endpoint-1", "foo", "bar", services1, null, null);
        this.accountTestHelper.createMockAwsEndpoint("aws-endpoint-2", "foo", "bar", services2, null, null);
        String awsEndpointLink3 = this.accountTestHelper.createMockAwsEndpoint("aws-endpoint-3", "foo", "bar", null, null, null);
        String awsEndpointLink4 = this.accountTestHelper.createMockAwsEndpoint("aws-endpoint-4", "foo", "bar", services4, null, null);
        String awsEndpointLink5 = this.accountTestHelper.createMockAwsEndpoint("aws-endpoint-5", "foo", "bar", services5, null, null);

        List<String> endpointLinks = new ArrayList<>();
        endpointLinks.add(awsEndpointLink3);
        endpointLinks.add(awsEndpointLink4);
        endpointLinks.add(awsEndpointLink5);

        Query awsFilter = Query.Builder.create()
                .addFieldClause(
                        CloudAccountViewState.FIELD_NAME_SERVICES,
                        "service_b",
                        MatchType.TERM,
                        Occurance.MUST_NOT_OCCUR)

                .build();

        CloudAccountQueryTaskState response = sendQuery(createQueryRequestBody(awsFilter));

        assertThat(response.results.nextPageLink, notNullValue());
        URI nextPageUri = UriUtils.buildUri(this.peerUri, response.results.nextPageLink);
        ServiceDocumentQueryResult result = this.host.getServiceState(null, ServiceDocumentQueryResult.class, nextPageUri);
        assertThat(result, notNullValue());
        assertThat(result.documents, notNullValue());
        assertThat(result.documents.size(), equalTo(3));
        this.accountTestHelper.verifyEndpointsPage(endpointLinks, result);
    }

    @Test
    public void listMultipleFilters() throws Throwable {
        int numEndpoints = this.pageSize - 1;

        this.host.assumeIdentity(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_1_ID)));
        List<String> user1Endpoints = this.accountTestHelper.createMockAwsEndpoints(numEndpoints, null, null, null);
        int filterOnNdx = ThreadLocalRandom.current().nextInt(0, numEndpoints);
        this.host.log(Level.INFO, "Chose random [ndx=%s] on user1Endpoints: %s", filterOnNdx, user1Endpoints);
        this.host.resetAuthorizationContext();

        this.host.assumeIdentity(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_2_ID)));
        List<String> user2Endpoints = this.accountTestHelper.createMockAwsEndpoints(numEndpoints,
                numEndpoints + user1Endpoints.size(), null, null, null);

        Query filter = Query.Builder.create()
                .addFieldClause(
                        CloudAccountViewState.FIELD_NAME_NAME,
                        "*" + ENDPOINT_NAME_PREFIX + filterOnNdx + "*",
                        MatchType.WILDCARD,
                        Occurance.MUST_OCCUR)
                .addFieldClause(
                        QuerySpecification.buildCompositeFieldName(
                                CloudAccountViewState.FIELD_NAME_CREATED_BY,
                                UserViewState.FIELD_NAME_EMAIL),
                        USER_1_ID,
                        MatchType.TERM,
                        Occurance.MUST_OCCUR).build();
        CloudAccountQueryTaskState response = sendQuery(createQueryRequestBody(filter));

        URI nextPageUri = UriUtils.buildUri(this.peerUri, response.results.nextPageLink);
        ServiceDocumentQueryResult result = this.host.getServiceState(null,
                ServiceDocumentQueryResult.class, nextPageUri);
        assertThat(result, notNullValue());
        assertThat(result.documents, notNullValue());
        assertThat(result.documents.size(), equalTo(1));
        this.accountTestHelper.verifyEndpointsPage(Arrays.asList(user1Endpoints.get(filterOnNdx)), result);

        filter = Query.Builder.create()
                .addCaseInsensitiveFieldClause(
                        CloudAccountViewState.FIELD_NAME_TYPE,
                        "*" + EndpointType.aws.name() + "*",
                        MatchType.WILDCARD,
                        Occurance.MUST_OCCUR)
                .addFieldClause(
                        QuerySpecification.buildCompositeFieldName(
                                CloudAccountViewState.FIELD_NAME_CREATED_BY,
                                UserViewState.FIELD_NAME_EMAIL),
                        "*USER2*",
                        MatchType.WILDCARD,
                        Occurance.MUST_OCCUR).build();
        response = sendQuery(createQueryRequestBody(filter));

        nextPageUri = UriUtils.buildUri(this.peerUri, response.results.nextPageLink);
        result = this.host.getServiceState(null, ServiceDocumentQueryResult.class, nextPageUri);
        assertThat(result, notNullValue());
        assertThat(result.documents, notNullValue());
        assertThat(result.documents.size(), equalTo(user2Endpoints.size()));
        this.accountTestHelper.verifyEndpointsPage(user2Endpoints, result);
    }

    @Test
    public void listSinglePageWithServiceUser() throws Throwable {
        int numEndpoints = this.pageSize - 1;
        this.host.assumeIdentity(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_1_ID)));
        this.host.log(Level.INFO, "USER_1 Link: %s", UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_1_ID)));
        this.host.log(Level.INFO, OperationContext.getAuthorizationContext().getClaims().getSubject());
        List<String> endpointLinks = this.accountTestHelper.createMockAwsEndpoints(numEndpoints, null, null, null);

        this.host.resetAuthorizationContext();
        this.host.assumeIdentity(SERVICE_USER_LINK);
        this.host.log(Level.INFO, "SERVICE_USER_LINK: %s", SERVICE_USER_LINK);
        Operation.AuthorizationContext authorizationContext = OperationContext.getAuthorizationContext();
        this.host.log(Level.INFO, "claims subject: %s", authorizationContext.getClaims().getSubject());
        CloudAccountQueryTaskState response = sendQuery();

        URI nextPageUri = UriUtils.buildUri(this.peerUri, response.results.nextPageLink);
        ServiceDocumentQueryResult result = this.host.getServiceState(null,
                ServiceDocumentQueryResult.class, nextPageUri);
        this.accountTestHelper.verifyEndpointsPage(endpointLinks, result);

        assertThat(result.nextPageLink, nullValue());
        assertThat(result.prevPageLink, nullValue());
    }

    // TODO: Client Credentials
//    @Test
//    public void testSinglePageWithClientCredentialsUser() throws Throwable {
//        int numEndpoints = this.pageSize - 1;
//        this.host.assumeIdentity(UriUtils.buildUriPath(UserService.FACTORY_LINK,
//                PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_1_ID)));
//        this.host.log(Level.INFO, "USER_1 Link: %s", UriUtils.buildUriPath(UserService.FACTORY_LINK,
//                PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_1_ID)));
//        this.host.log(Level.INFO, OperationContext.getAuthorizationContext().getClaims().getSubject());
//        List<String> endpointLinks = this.accountTestHelper.createMockAwsEndpoints(numEndpoints, null, null, null);
//
//        this.host.resetAuthorizationContext();
//        Set<String> clientIdLinks = PhotonControllerCloudAccountUtils.getSystemOauthClientIdLinks();
//
//        String clientIdLink = (String) clientIdLinks.toArray()[0];
//        this.host.assumeIdentity(clientIdLink);
//        this.host.log(Level.INFO, "client id link: %s", clientIdLink);
//        Operation.AuthorizationContext authorizationContext = OperationContext
//                .getAuthorizationContext();
//        this.host.log(Level.INFO, "claims subject: %s",
//                authorizationContext.getClaims().getSubject());
//        CloudAccountQueryTaskState response = sendQuery();
//
//        URI nextPageUri = UriUtils.buildUri(this.peerUri, response.results.nextPageLink);
//        ServiceDocumentQueryResult result = this.host
//                .getServiceState(null, ServiceDocumentQueryResult.class, nextPageUri);
//        this.accountTestHelper.verifyEndpointsPage(endpointLinks, result);
//
//        assertThat(result.nextPageLink, nullValue());
//        assertThat(result.prevPageLink, nullValue());
//    }

    @Test
    public void listSinglePageSortNameDefault() throws Throwable {
        int numEndpoints = this.pageSize - 1;
        this.host.assumeIdentity(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_1_ID)));

        // Create the endpointLinks in descending order
        List<String> endpointLinks = this.accountTestHelper.createMockAwsEndpointsDescending(numEndpoints, null, null, null);

        // Test sorting on 'name' in ascending order by default
        QueryTask ascendQueryTask = QueryTask.Builder.createDirectTask().build();
        CloudAccountQueryTaskState ascendResponse = sendQuery(createQueryRequestBody(ascendQueryTask));

        URI nextPageUri = UriUtils.buildUri(this.peerUri, ascendResponse.results.nextPageLink);
        ServiceDocumentQueryResult ascendPage1 = this.host.getServiceState(null, ServiceDocumentQueryResult.class, nextPageUri);

        // We expect the response to give us a list of endpoints in sorted, ascending order based on
        // the name. Since the endpointLinks were created in descending order, reverse them to check
        // if the sorting occurred properly.
        Collections.reverse(endpointLinks);
        this.accountTestHelper.verifyEndpointsPageSorted(endpointLinks, ascendPage1);

        assertThat(ascendPage1.nextPageLink, nullValue());
        assertThat(ascendPage1.prevPageLink, nullValue());
    }

    @Test
    public void listSinglePageSortName() throws Throwable {
        int numEndpoints = this.pageSize - 1;
        this.host.assumeIdentity(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_1_ID)));
        List<String> endpointLinks = this.accountTestHelper.createMockAwsEndpoints(numEndpoints, null, null, null);

        // Test sorting on 'name' in ascending order
        QueryTask ascendQueryTask = QueryTask.Builder.createDirectTask()
                .orderAscending(CloudAccountViewState.FIELD_NAME_NAME, ServiceDocumentDescription.TypeName.STRING)
                .build();
        CloudAccountQueryTaskState ascendResponse = sendQuery(createQueryRequestBody(ascendQueryTask));

        URI nextPageUri = UriUtils.buildUri(this.peerUri, ascendResponse.results.nextPageLink);
        ServiceDocumentQueryResult ascendPage1 = this.host.getServiceState(null, ServiceDocumentQueryResult.class, nextPageUri);
        this.accountTestHelper.verifyEndpointsPageSorted(endpointLinks, ascendPage1);

        assertThat(ascendPage1.nextPageLink, nullValue());
        assertThat(ascendPage1.prevPageLink, nullValue());

        // Test sorting on 'name' in descending order
        Collections.reverse(endpointLinks);
        QueryTask descendQueryTask = QueryTask.Builder.createDirectTask()
                .orderDescending(CloudAccountViewState.FIELD_NAME_NAME, ServiceDocumentDescription.TypeName.STRING)
                .build();
        CloudAccountQueryTaskState descendResponse = sendQuery(createQueryRequestBody(descendQueryTask));

        nextPageUri = UriUtils.buildUri(this.peerUri, descendResponse.results.nextPageLink);
        ServiceDocumentQueryResult descendPage1 = this.host.getServiceState(null, ServiceDocumentQueryResult.class, nextPageUri);
        this.accountTestHelper.verifyEndpointsPageSorted(endpointLinks, descendPage1);

        assertThat(descendPage1.nextPageLink, nullValue());
        assertThat(descendPage1.prevPageLink, nullValue());
    }

    @Test
    public void listFilterByOrgId() throws Throwable {
        this.accountTestHelper.createSecondOrg();

        OnBoardingTestUtils.assumeIdentityWithOrgContext(this.host, USER_1_ID, ORG_1_ID);
        String org1EndpointLink = this.accountTestHelper
                .createMockAwsEndpointInOrg(this.accountTestHelper.orgLink, "endpoint-user1",
                        "privateKey-user1", "keyId-user1", null, null, null);
        OnBoardingTestUtils.assumeIdentityWithOrgContext(this.host, USER_1_ID, ORG_2_ID);
        List<String> org2EndpointLinks = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            String idx = "-" + i;
            String org2EndpointLink = this.accountTestHelper
                    .createMockAwsEndpointInOrg(this.accountTestHelper.org2Link, "endpoint-user2" + idx,
                            "privateKey-user2" + idx, "keyId-user2" + idx, null, null, null);
            org2EndpointLinks.add(org2EndpointLink);
        }

        Query filter = Query.Builder.create()
                .addCaseInsensitiveFieldClause(
                        QuerySpecification.buildCompositeFieldName(
                                CloudAccountViewState.FIELD_NAME_ORG,
                                OrganizationViewState.FIELD_NAME_ORG_ID),
                        ORG_1_ID,
                        MatchType.TERM,
                        Occurance.MUST_OCCUR).build();
        CloudAccountQueryTaskState response = sendQuery(createQueryRequestBody(filter));

        assertNotNull(response.results.nextPageLink);
        URI nextPageUri = UriUtils.buildUri(this.peerUri, response.results.nextPageLink);
        ServiceDocumentQueryResult result = this.host.getServiceState(null,
                ServiceDocumentQueryResult.class, nextPageUri);
        assertThat(result, notNullValue());
        assertThat(result.documents, notNullValue());
        assertThat(result.documents.size(), equalTo(1));
        this.accountTestHelper.verifyEndpointsPage(Arrays.asList(org1EndpointLink), result);

        filter = Query.Builder.create()
                .addCaseInsensitiveFieldClause(
                        QuerySpecification.buildCompositeFieldName(
                                CloudAccountViewState.FIELD_NAME_ORG,
                                OrganizationViewState.FIELD_NAME_ORG_ID),
                        ORG_2_ID,
                        MatchType.TERM,
                        Occurance.MUST_OCCUR).build();
        response = sendQuery(createQueryRequestBody(filter));

        nextPageUri = UriUtils.buildUri(this.peerUri, response.results.nextPageLink);
        result = this.host.getServiceState(null, ServiceDocumentQueryResult.class, nextPageUri);
        assertThat(result, notNullValue());
        assertThat(result.documents, notNullValue());
        assertThat(result.documents.size(), equalTo(2));
        this.accountTestHelper.verifyEndpointsPage(org2EndpointLinks, result);
    }

    @Test
    public void listFilterByOrgLinkAsServiceUser() throws Throwable {
        this.accountTestHelper.createSecondOrg();

        OnBoardingTestUtils.assumeIdentityWithOrgContext(this.host, USER_1_ID, ORG_1_ID);
        this.accountTestHelper.createMockAwsEndpointInOrg(this.accountTestHelper.orgLink, "endpoint-user1",
                "privateKey-user1", "keyId-user1", null, null, null);
        this.accountTestHelper.createMockVsphereEndpointOldApi(this.host, this.peerUri, ORG_1_ID,
                "vsphere-old-api-ep1", "uname", "pwd", "this.hostname",
                "vsphere","dcname", "dcid");
        createMockVsphereEndpoint(this.host, this.peerUri, ORG_1_ID,
                "vsphere-new-api-ep1", "uname", "pwd", "this.hostname",
                "vsphere","dcname", "dcid");

        OnBoardingTestUtils.assumeIdentityWithOrgContext(this.host, USER_1_ID, ORG_2_ID);
        List<String> org2EndpointLinks = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            String idx = "-" + i;
            String org2EndpointLink = this.accountTestHelper
                    .createMockAwsEndpointInOrg(this.accountTestHelper.org2Link, "endpoint-user2" + idx,
                            "privateKey-user2" + idx, "keyId-user2" + idx, null, null, null);
            org2EndpointLinks.add(org2EndpointLink);
        }

        this.host.assumeIdentity(SERVICE_USER_LINK);
        Query filter = Query.Builder.create()
                .addCaseInsensitiveFieldClause(
                        QuerySpecification.buildCompositeFieldName(
                                CloudAccountViewState.FIELD_NAME_ORG,
                                OrganizationViewState.FIELD_NAME_ORG_LINK),
                        this.accountTestHelper.orgLink,
                        MatchType.TERM,
                        Occurance.MUST_OCCUR).build();
        CloudAccountQueryTaskState response = sendQuery(createQueryRequestBody(filter));

        assertThat(response.results.nextPageLink, notNullValue());
        URI nextPageUri = UriUtils.buildUri(this.peerUri, response.results.nextPageLink);
        ServiceDocumentQueryResult result = this.host.getServiceState(null, ServiceDocumentQueryResult.class, nextPageUri);
        assertThat(result, notNullValue());
        assertThat(result.documents, notNullValue());
        assertThat(result.documents.size(), equalTo(3));

        filter = Query.Builder.create()
                .addCaseInsensitiveFieldClause(
                        QuerySpecification.buildCompositeFieldName(
                                CloudAccountViewState.FIELD_NAME_ORG,
                                OrganizationViewState.FIELD_NAME_ORG_LINK),
                        this.accountTestHelper.org2Link,
                        MatchType.TERM,
                        Occurance.MUST_OCCUR).build();
        response = sendQuery(createQueryRequestBody(filter));

        assertThat(response.results.nextPageLink, notNullValue());
        nextPageUri = UriUtils.buildUri(this.peerUri, response.results.nextPageLink);
        result = this.host.getServiceState(null, ServiceDocumentQueryResult.class, nextPageUri);
        assertThat(result, notNullValue());
        assertThat(result.documents, notNullValue());
        assertThat(result.documents.size(), equalTo(2));
        this.accountTestHelper.verifyEndpointsPage(org2EndpointLinks, result);
    }

    @Test
    public void listOldAndNewWithFilterByOrgLink() throws Throwable {
        this.accountTestHelper.createSecondOrg();

        OnBoardingTestUtils.assumeIdentityWithOrgContext(this.host, USER_1_ID, ORG_1_ID);
        List<String> org1EndpointLinks = new ArrayList<>();
        String org1EndpointLink1 = this.accountTestHelper
                .createMockAwsEndpointInOrg(this.accountTestHelper.orgLink, "endpoint-user1",
                        "privateKey-user1", "keyId-user1", null, null, null);
        String org1EndpointLink2 = this.accountTestHelper.createMockVsphereEndpointOldApi(this.host, this.peerUri, ORG_1_ID,
                "vc-local-test-endpoint-1", "root", "password",
                "10.112.107.120", "privateCloud1",
                "vc-dc-name", "vc-dc-id");
        org1EndpointLinks.add(org1EndpointLink1);
        org1EndpointLinks.add(org1EndpointLink2);

        OnBoardingTestUtils.assumeIdentityWithOrgContext(this.host, USER_1_ID, ORG_2_ID);
        List<String> org2EndpointLinks = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            String idx = "-" + i;
            String org2EndpointLink = this.accountTestHelper
                    .createMockAwsEndpointInOrg(this.accountTestHelper.org2Link, "endpoint-user2" + idx,
                            "privateKey-user2" + idx, "keyId-user2" + idx, null, null, null);
            org2EndpointLinks.add(org2EndpointLink);
        }
        String org2EndpointLink2 = this.accountTestHelper.createMockVsphereEndpointOldApi(this.host, this.peerUri, ORG_2_ID,
                "vc-local-test-endpoint-2", "root", "password",
                "10.112.107.120", "privateCloud1",
                "vc-dc-name", "vc-dc-id");
        org2EndpointLinks.add(org2EndpointLink2);

        Query filter = Query.Builder.create()
                .addCaseInsensitiveFieldClause(
                        QuerySpecification.buildCompositeFieldName(
                                CloudAccountViewState.FIELD_NAME_ORG,
                                OrganizationViewState.FIELD_NAME_ORG_LINK),
                        this.accountTestHelper.orgLink,
                        MatchType.TERM,
                        Occurance.MUST_OCCUR).build();
        CloudAccountQueryTaskState response = sendQuery(createQueryRequestBody(filter));

        assertThat(response.results.nextPageLink, notNullValue());
        URI nextPageUri = UriUtils.buildUri(this.peerUri, response.results.nextPageLink);
        ServiceDocumentQueryResult result = this.host.getServiceState(null, ServiceDocumentQueryResult.class, nextPageUri);
        assertThat(result, notNullValue());
        assertThat(result.documents, notNullValue());
        assertThat(result.documents.size(), equalTo(2));
        this.accountTestHelper.verifyEndpointsPage(org1EndpointLinks, result);

        filter = Query.Builder.create()
                .addCaseInsensitiveFieldClause(
                        QuerySpecification.buildCompositeFieldName(
                                CloudAccountViewState.FIELD_NAME_ORG,
                                OrganizationViewState.FIELD_NAME_ORG_LINK),
                        this.accountTestHelper.org2Link,
                        MatchType.TERM,
                        Occurance.MUST_OCCUR).build();
        response = sendQuery(createQueryRequestBody(filter));

        assertThat(response.results.nextPageLink, notNullValue());
        nextPageUri = UriUtils.buildUri(this.peerUri, response.results.nextPageLink);
        result = this.host.getServiceState(null, ServiceDocumentQueryResult.class, nextPageUri);
        assertThat(result, notNullValue());
        assertThat(result.documents, notNullValue());
        assertThat(result.documents.size(), equalTo(3));
        this.accountTestHelper.verifyEndpointsPage(org2EndpointLinks, result);
    }

    @Test
    public void listOldAndNewVsphereEndpoints() throws Throwable {
        this.host.assumeIdentity(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_1_ID)));
        List<String> endpointLinks = new ArrayList<>();
        String endpointLink1 = this.accountTestHelper.createMockVsphereEndpointOldApi(this.host, this.peerUri, ORG_1_ID,
                "vc-local-test-endpoint-1", "root", "password",
                "10.112.107.120", "privateCloud1",
                "vc-dc-name", "vc-dc-id");
        String endpointLink2 = createMockVsphereEndpoint(this.host, this.peerUri, ORG_1_ID,
                "vc-local-test-endpoint-2", "root", "password",
                "10.112.107.120", "privateCloud2",
                "vc-dc-name", "vc-dc-id");

        endpointLinks.add(endpointLink1);
        endpointLinks.add(endpointLink2);

        CloudAccountQueryTaskState response = sendQuery();

        URI nextPageUri = UriUtils.buildUri(this.peerUri, response.results.nextPageLink);
        assertThat(nextPageUri.getPath(), not(containsString(ServiceUriPaths.DEFAULT_NODE_SELECTOR)));
        ServiceDocumentQueryResult result = this.host.getServiceState(null, ServiceDocumentQueryResult.class, nextPageUri);
        assertThat(result, notNullValue());
        assertThat(result.documents, notNullValue());
        assertThat(result.documents.size(), equalTo(2));
        this.accountTestHelper.verifyEndpointsPage(endpointLinks, result);

    }

    @Ignore
    // TODO: This cannot be enabled, until all the private cloud accounts are migrated to new format
    public void testUserRemovalFromOrg() throws Throwable {
        this.accountTestHelper.createSecondOrg();

        List<String> allEndpointLinks = new ArrayList<>();
        List<String> org1EndpointLinks = new ArrayList<>();
        this.host.assumeIdentity(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_1_ID)));
        for (int i = 0; i < 3; i++) {
            String idx = "-" + i;
            String org1EndpointLink = this.accountTestHelper
                    .createMockAwsEndpointInOrg(this.accountTestHelper.orgLink, "endpoint-user1" + idx,
                            "privateKey-user1" + idx, "keyId-user1" + idx, null, null, null);
            org1EndpointLinks.add(org1EndpointLink);
        }

        List<String> org2EndpointLinks = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            String idx = "-" + i;
            String org2EndpointLink = this.accountTestHelper
                    .createMockAwsEndpointInOrg(this.accountTestHelper.org2Link, "endpoint-user2" + idx,
                            "privateKey-user2" + idx, "keyId-user2" + idx, null, null, null);
            org2EndpointLinks.add(org2EndpointLink);
        }

        allEndpointLinks.addAll(org1EndpointLinks);
        allEndpointLinks.addAll(org2EndpointLinks);

        URI getAllEndpoints = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK);
        ServiceDocumentQueryResult result = this.host.getServiceState(null, ServiceDocumentQueryResult.class, getAllEndpoints);
        assertThat(result.documents.size(), equalTo(5));
        this.accountTestHelper.verifyEndpointsPage(allEndpointLinks, result);

        this.host.setSystemAuthorizationContext();
        this.accountTestHelper.removeUserFromOrg(USER_1_ID, ORG_2_ID);

        // Since user-1 is not part of org-2, he should see only org-1 endpoints
        this.host.assumeIdentity(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_1_ID)));
        getAllEndpoints = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK);
        result = this.host.getServiceState(null, ServiceDocumentQueryResult.class, getAllEndpoints);
        assertThat(result.documents.size(), equalTo(3));
        this.accountTestHelper.verifyEndpointsPage(org1EndpointLinks, result);

        // service-user should continue to see all endpoints
        this.host.assumeIdentity(SERVICE_USER_LINK);
        getAllEndpoints = UriUtils.buildUri(this.peerUri, CloudAccountApiService.SELF_LINK);
        result = this.host.getServiceState(null, ServiceDocumentQueryResult.class, getAllEndpoints);
        assertThat(result.documents.size(), equalTo(5));
        this.accountTestHelper.verifyEndpointsPage(allEndpointLinks, result);
    }

    @Test
    public void listSinglePageMultipleUserSortEmail() throws Throwable {
        this.host.assumeIdentity(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_1_ID)));
        String user1EndpointLink = this.accountTestHelper
                .createMockAwsEndpoint( "endpoint-user1", "privateKey-user1", "keyId-user1", null, null, null);
        this.host.resetAuthorizationContext();

        this.host.assumeIdentity(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_2_ID)));
        String user2EndpointLink = this.accountTestHelper
                .createMockAwsEndpoint("endpoint-user2", "privateKey-user2", "keyId-user2", null, null, null);

        List<String> endpointLinks = Arrays.asList(user1EndpointLink, user2EndpointLink);
        String createdByField = QuerySpecification.buildCompositeFieldName(
                CloudAccountViewState.FIELD_NAME_CREATED_BY, UserViewState.FIELD_NAME_EMAIL);

        // Test sorting on 'createdBy.email' in ascending order
        QueryTask ascendQueryTask = QueryTask.Builder.createDirectTask()
                .orderAscending(createdByField, ServiceDocumentDescription.TypeName.STRING)
                .build();
        CloudAccountQueryTaskState ascendResponse = sendQuery(createQueryRequestBody(
                ascendQueryTask));

        URI nextPageUri = UriUtils.buildUri(this.peerUri, ascendResponse.results.nextPageLink);
        ServiceDocumentQueryResult ascendPage1 = this.host.getServiceState(null,
                ServiceDocumentQueryResult.class, nextPageUri);
        this.accountTestHelper.verifyEndpointsPageSorted(endpointLinks, ascendPage1);

        // Test sorting on 'createdBy.email' in descending order
        Collections.reverse(endpointLinks);
        QueryTask descendQueryTask = QueryTask.Builder.createDirectTask()
                .orderDescending(createdByField, ServiceDocumentDescription.TypeName.STRING)
                .build();
        CloudAccountQueryTaskState descendResponse = sendQuery(createQueryRequestBody(
                descendQueryTask));

        nextPageUri = UriUtils.buildUri(this.peerUri, descendResponse.results.nextPageLink);
        ServiceDocumentQueryResult descendPage1 = this.host.getServiceState(null,
                ServiceDocumentQueryResult.class, nextPageUri);
        this.accountTestHelper.verifyEndpointsPageSorted(endpointLinks, descendPage1);

        assertThat(ascendPage1.nextPageLink, nullValue());
        assertThat(ascendPage1.prevPageLink, nullValue());
    }

    @Test
    public void testTagsFilteringIvalidInput() throws Throwable {
        this.host.assumeIdentity(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_1_ID)));

        Set<TagViewState> tagSet1 = new HashSet<>();
        tagSet1.add(new TagViewState("dummyKey", "dummyVal"));
        Set<TagViewState> tagSet2 = new HashSet<>();
        tagSet2.add(new TagViewState("dummyKey2", "dummyVal2"));

        String endpoint1 = this.accountTestHelper.createMockAwsEndpoint("aws-endpoint-1", "foo",
                "bar", null, null, tagSet1);
        String endpoint2 = this.accountTestHelper.createMockAwsEndpoint("aws-endpoint-2", "foo",
                "bar", null, null, tagSet2);
        this.accountTestHelper.createMockAwsEndpoint("aws-endpoint-3", "foo", "bar", null, null, null);

        // query term with missing value
        Query filter = Query.Builder.create()
                .addFieldClause(
                        CloudAccountViewState.FIELD_NAME_TAGS,
                        "dummyKey=",
                        MatchType.TERM,
                        Occurance.MUST_OCCUR).build();
        sendQueryExpectFailure(createQueryRequestBody(filter));

        // query term with missing delimiter
        filter = Query.Builder.create()
                .addFieldClause(
                        CloudAccountViewState.FIELD_NAME_TAGS,
                        "dummyKey",
                        MatchType.TERM,
                        Occurance.MUST_OCCUR).build();
        sendQueryExpectFailure(createQueryRequestBody(filter));

        // query term with missing key
        filter = Query.Builder.create()
                .addFieldClause(
                        CloudAccountViewState.FIELD_NAME_TAGS,
                        "=dummyValue",
                        MatchType.TERM,
                        Occurance.MUST_OCCUR).build();
        sendQueryExpectFailure(createQueryRequestBody(filter));

        // query term with null matchValue
        filter = Query.Builder.create()
                .addFieldClause(
                        CloudAccountViewState.FIELD_NAME_TAGS,
                        null,
                        MatchType.TERM,
                        Occurance.MUST_OCCUR).build();
        sendQueryExpectFailure(createQueryRequestBody(filter));
    }

    @Test
    public void testTagsFilteringByExactTag() throws Throwable {
        this.host.assumeIdentity(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_1_ID)));

        Set<TagViewState> tagSet1 = new HashSet<>();
        tagSet1.add(new TagViewState("dummyKey", "dummyVal"));
        Set<TagViewState> tagSet2 = new HashSet<>();
        tagSet2.add(new TagViewState("dummyKey2", "dummyVal2"));

        String endpoint1 = this.accountTestHelper.createMockAwsEndpoint("aws-endpoint-1", "foo",
                "bar", null, null, tagSet1);
        String endpoint2 = this.accountTestHelper.createMockAwsEndpoint("aws-endpoint-2", "foo", "bar",
                null, null, tagSet2);
        this.accountTestHelper.createMockAwsEndpoint("aws-endpoint-3", "foo", "bar", null, null, null);

        // query for endpoint with tag1
        Query filter = Query.Builder.create()
                .addFieldClause(
                        CloudAccountViewState.FIELD_NAME_TAGS,
                        "dummyKey=dummyVal",
                        MatchType.TERM,
                        Occurance.MUST_OCCUR).build();
        CloudAccountQueryTaskState response = sendQuery(createQueryRequestBody(filter));

        assertNotNull(response.results.nextPageLink);
        URI nextPageUri = UriUtils.buildUri(this.peerUri, response.results.nextPageLink);
        ServiceDocumentQueryResult result = this.host.getServiceState(null,
                ServiceDocumentQueryResult.class, nextPageUri);
        assertNotNull(result);
        assertNotNull(result.documents);
        assertEquals(1, result.documents.size());
        this.accountTestHelper.verifyEndpointsPage(Arrays.asList(endpoint1), result);

        // query for endpoint with tag2
        filter = Query.Builder.create()
                .addFieldClause(
                        CloudAccountViewState.FIELD_NAME_TAGS,
                        "dummyKey2=dummyVal2",
                        MatchType.TERM,
                        Occurance.MUST_OCCUR).build();
        response = sendQuery(createQueryRequestBody(filter));

        assertNotNull(response.results.nextPageLink);
        nextPageUri = UriUtils.buildUri(this.peerUri, response.results.nextPageLink);
        result = this.host.getServiceState(null, ServiceDocumentQueryResult.class, nextPageUri);
        assertNotNull(result);
        assertNotNull(result.documents);
        assertEquals(1, result.documents.size());
        this.accountTestHelper.verifyEndpointsPage(Arrays.asList(endpoint2), result);

        // query for endpoint with no matching tag - expect 0 results hence null nextPageLink
        filter = Query.Builder.create()
                .addFieldClause(
                        CloudAccountViewState.FIELD_NAME_TAGS,
                        "dummyKey3=dummyVal3",
                        MatchType.TERM,
                        Occurance.MUST_OCCUR).build();
        response = sendQuery(createQueryRequestBody(filter));
        assertNull(response.results.nextPageLink);

        // query for endpoint with tag1 with lowercase tag input
        filter = Query.Builder.create()
                .addFieldClause(
                        CloudAccountViewState.FIELD_NAME_TAGS,
                        "dummykey=dummyval",
                        MatchType.TERM,
                        Occurance.MUST_OCCUR).build();
        response = sendQuery(createQueryRequestBody(filter));

        assertNotNull(response.results.nextPageLink);
        nextPageUri = UriUtils.buildUri(this.peerUri, response.results.nextPageLink);
        result = this.host.getServiceState(null,
                ServiceDocumentQueryResult.class, nextPageUri);
        assertNotNull(result);
        assertNotNull(result.documents);
        assertEquals(1, result.documents.size());
        this.accountTestHelper.verifyEndpointsPage(Arrays.asList(endpoint1), result);
    }

    @Test
    public void testTagsFilteringByWildCardTag() throws Throwable {
        this.host.assumeIdentity(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_1_ID)));

        TagViewState tagState1 = new TagViewState("dummyKey", "dummy-Val");
        TagViewState tagState2 = new TagViewState("dummyKey", "dummy-Val-2");
        TagViewState tagState3 = new TagViewState("testKey", "test-Val");

        Set<TagViewState> tagSet1 = new HashSet<>();
        Collections.addAll(tagSet1, tagState1, tagState2, tagState3);
        Set<TagViewState> tagSet2 = new HashSet<>();
        tagSet2.add(new TagViewState("dummyKey2", "dummyVal2"));
        tagSet2.add(tagState3);

        String endpoint1 = this.accountTestHelper.createMockAwsEndpoint("aws-endpoint-1", "foo", "bar",
                null, null, tagSet1);
        String endpoint2 = this.accountTestHelper.createMockAwsEndpoint("aws-endpoint-2", "foo", "bar",
                null, null, tagSet2);
        this.accountTestHelper.createMockAwsEndpoint("aws-endpoint-3", "foo", "bar", null, null, null);

        // tag1 is the only tag that matches the combined criteria of tag where dummyKey=dum* and
        // dummyKey=*Val
        Query filter = Query.Builder.create()
                .addFieldClause(CloudAccountViewState.FIELD_NAME_TAGS,
                        "dummyKey=dum*",
                        MatchType.WILDCARD,
                        Occurance.MUST_OCCUR)
                .addFieldClause(CloudAccountViewState.FIELD_NAME_TAGS,
                        "dummyKey=*Val",
                        MatchType.WILDCARD,
                        Occurance.MUST_OCCUR)
                .build();

        CloudAccountQueryTaskState response = sendQuery(createQueryRequestBody(filter));

        assertNotNull(response.results.nextPageLink);
        URI nextPageUri = UriUtils.buildUri(this.peerUri, response.results.nextPageLink);
        ServiceDocumentQueryResult result = this.host.getServiceState(null,
                ServiceDocumentQueryResult.class, nextPageUri);
        assertNotNull(result);
        assertNotNull(result.documents);
        assertEquals(1, result.documents.size());
        this.accountTestHelper.verifyEndpointsPage(Arrays.asList(endpoint1), result);

        // query by multiple tags
        // endpoint1 is the only account that matches the combined criteria of tag being
        // dummyKey=dum* and testKey=test-Val
        filter = Query.Builder.create()
                .addFieldClause(CloudAccountViewState.FIELD_NAME_TAGS,
                        "dummyKey=dum*",
                        MatchType.WILDCARD,
                        Occurance.MUST_OCCUR)
                .addFieldClause(CloudAccountViewState.FIELD_NAME_TAGS,
                        "testKey=test-Val",
                        MatchType.WILDCARD,
                        Occurance.MUST_OCCUR)
                .build();
        response = sendQuery(createQueryRequestBody(filter));

        assertNotNull(response.results.nextPageLink);
        nextPageUri = UriUtils.buildUri(this.peerUri, response.results.nextPageLink);
        result = this.host.getServiceState(null, ServiceDocumentQueryResult.class, nextPageUri);
        assertNotNull(result);
        assertNotNull(result.documents);
        assertEquals(1, result.documents.size());
        this.accountTestHelper.verifyEndpointsPage(Arrays.asList(endpoint1), result);
    }

    @Test
    public void testTagsFilteringByTagAndName() throws Throwable {
        this.host.assumeIdentity(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_1_ID)));

        TagViewState tagState1 = new TagViewState("dummyKey", "dummy-Val");
        TagViewState tagState2 = new TagViewState("dummyKey", "dummy-Val-2");
        TagViewState tagState3 = new TagViewState("testKey", "test-Val");
        TagViewState tagState4 = new TagViewState("testKey", "test-Val-2");

        Set<TagViewState> tagSet1 = new HashSet<>();
        Collections.addAll(tagSet1, tagState1, tagState2, tagState3);
        Set<TagViewState> tagSet2 = new HashSet<>();
        tagSet2.add(new TagViewState("dummyKey2", "dummyVal2"));
        tagSet2.add(tagState3);
        tagSet2.add(tagState4);

        this.accountTestHelper.createMockAwsEndpoint("aws-endpoint-1", "foo", "bar", null, null, tagSet1);
        String endpoint2 = this.accountTestHelper.createMockAwsEndpoint("aws-endpoint-2", "foo", "bar",
                null, null, tagSet2);
        this.accountTestHelper.createMockAwsEndpoint("aws-endpoint-3", "foo", "bar", null, null, null);

        Query filter = Query.Builder.create()
                .addFieldClause(
                        CloudAccountViewState.FIELD_NAME_NAME,
                        "*-2",
                        MatchType.WILDCARD,
                        Occurance.MUST_OCCUR)
                .addFieldClause(CloudAccountViewState.FIELD_NAME_TAGS,
                        "testKey=test-Val-2",
                        MatchType.WILDCARD,
                        Occurance.MUST_OCCUR)
                .build();
        CloudAccountQueryTaskState response = sendQuery(createQueryRequestBody(filter));

        assertNotNull(response.results.nextPageLink);
        URI nextPageUri = UriUtils.buildUri(this.peerUri, response.results.nextPageLink);
        ServiceDocumentQueryResult result = this.host.getServiceState(null,
                ServiceDocumentQueryResult.class, nextPageUri);
        assertNotNull(result);
        assertNotNull(result.documents);
        assertEquals(1, result.documents.size());
        this.accountTestHelper.verifyEndpointsPage(Arrays.asList(endpoint2), result);
    }

    @Test
    public void testTagsFilteringByTagWildCardORClause() throws Throwable {
        this.host.assumeIdentity(UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(USER_1_ID)));

        TagViewState tagState1 = new TagViewState("dummyKey", "dummyVal");
        TagViewState tagState2 = new TagViewState("dummyKey", "dummyVal-2");
        TagViewState tagState3 = new TagViewState("testKey", "dummyVal-2");
        TagViewState tagState4 = new TagViewState("testKey", "testVal");
        TagViewState tagState5 = new TagViewState("testKey", "testVal-2");

        Set<TagViewState> tagSet1 = new HashSet<>();
        Collections.addAll(tagSet1, tagState1, tagState2, tagState4);
        Set<TagViewState> tagSet2 = new HashSet<>();
        Collections.addAll(tagSet2, tagState3, tagState4);
        Set<TagViewState> tagSet3 = new HashSet<>();
        Collections.addAll(tagSet3, tagState5);

        String endpoint1 = this.accountTestHelper.createMockAwsEndpoint("aws-endpoint-1", "foo", "bar",
                null, null, tagSet1);
        String endpoint2 = this.accountTestHelper.createMockAwsEndpoint("aws-endpoint-2", "foo", "bar",
                null, null, tagSet2);
        this.accountTestHelper.createMockAwsEndpoint("aws-endpoint-3", "foo","bar", null, null, null);

        // Only endpoint1 fulfills the criteria of tag where dummyKey=dum* or dummyKey=*Val
        // name=*master
        Query filter = Query.Builder.create()
                .addFieldClause(CloudAccountViewState.FIELD_NAME_TAGS,
                        "dummyKey=dum*",
                        MatchType.WILDCARD,
                        Occurance.SHOULD_OCCUR)
                .addFieldClause(CloudAccountViewState.FIELD_NAME_TAGS,
                        "dummyKey=*Val",
                        MatchType.WILDCARD,
                        Occurance.SHOULD_OCCUR)
                .build();
        CloudAccountQueryTaskState response = sendQuery(createQueryRequestBody(filter));

        assertNotNull(response.results.nextPageLink);
        URI nextPageUri = UriUtils.buildUri(this.peerUri, response.results.nextPageLink);
        ServiceDocumentQueryResult result = this.host.getServiceState(null,
                ServiceDocumentQueryResult.class, nextPageUri);
        assertNotNull(result);
        assertNotNull(result.documents);
        assertEquals(1, result.documents.size());
        this.accountTestHelper.verifyEndpointsPage(Arrays.asList(endpoint1), result);

        String endpoint3 = this.accountTestHelper.createMockAwsEndpoint("aws-endpoint-3", "foo", "bar",
                null, null, tagSet3);
        // All 3 endpoints match the combined criteria of tag where
        // (tag dummyKey=dum* OR tag testKey=*2)
        filter = Query.Builder.create()
                .addFieldClause(CloudAccountViewState.FIELD_NAME_TAGS,
                        "dummyKey=dum*",
                        MatchType.WILDCARD,
                        Occurance.SHOULD_OCCUR)
                .addFieldClause(CloudAccountViewState.FIELD_NAME_TAGS,
                        "testKey=*2",
                        MatchType.WILDCARD,
                        Occurance.SHOULD_OCCUR)
                .build();
        response = sendQuery(createQueryRequestBody(filter));

        assertNotNull(response.results.nextPageLink);
        nextPageUri = UriUtils.buildUri(this.peerUri, response.results.nextPageLink);
        result = this.host.getServiceState(null, ServiceDocumentQueryResult.class, nextPageUri);
        assertNotNull(result);
        assertNotNull(result.documents);
        assertEquals(3, result.documents.size());
        this.accountTestHelper.verifyEndpointsPage(Arrays.asList(endpoint1, endpoint2, endpoint3), result);
    }

    @Test
    public void testTagsFilteringORConditionOnDifferentTagsAndAdditionalAndCondition()
            throws Throwable {
        TagViewState ownerTag1 = new TagViewState("owner", "user-foo");
        TagViewState envTag1 = new TagViewState("env", "prod");
        TagViewState ownerTag2 = new TagViewState("owner", "user-bar");
        TagViewState envTag2 = new TagViewState("env", "dev");
        TagViewState owner3 = new TagViewState("owner", "test-foo");

        Set<TagViewState> tagSet1 = new HashSet<>();
        Collections.addAll(tagSet1, ownerTag1, envTag1);
        Set<TagViewState> tagSet2 = new HashSet<>();
        Collections.addAll(tagSet2, ownerTag2, envTag1);
        Set<TagViewState> tagSet3 = new HashSet<>();
        Collections.addAll(tagSet3, envTag2, owner3);

        this.accountTestHelper.createMockAwsEndpoint("aws-endpoint-1", "foo", "bar", null, null, tagSet1);
        String endpoint2 = this.accountTestHelper.createMockAwsEndpoint("aws-endpoint-2", "foo", "bar",
                null, null, tagSet2);
        String endpoint3 = this.accountTestHelper.createMockAwsEndpoint("aws-endpoint-3", "foo", "bar",
                null, null, tagSet3);

        // endpoint2 and endpoint3 are the only ones that satisfy the criteria of tag
        // (owner=user-* OR env=dev)AND owner=*bar
        Query filter = Query.Builder.create()
                .addFieldClause(CloudAccountViewState.FIELD_NAME_TAGS,
                        "owner=user-*",
                        MatchType.WILDCARD,
                        Occurance.SHOULD_OCCUR)
                .addFieldClause(CloudAccountViewState.FIELD_NAME_TAGS,
                        "env=dev",
                        MatchType.WILDCARD,
                        Occurance.SHOULD_OCCUR)
                .addFieldClause(CloudAccountViewState.FIELD_NAME_TAGS,
                        "owner=*-bar",
                        MatchType.WILDCARD,
                        Occurance.MUST_OCCUR)
                .build();
        CloudAccountQueryTaskState response = sendQuery(createQueryRequestBody(filter));

        assertNotNull(response.results.nextPageLink);
        URI nextPageUri = UriUtils.buildUri(this.peerUri, response.results.nextPageLink);
        ServiceDocumentQueryResult result = this.host.getServiceState(null,
                ServiceDocumentQueryResult.class, nextPageUri);
        assertNotNull(result);
        assertNotNull(result.documents);
        assertEquals(2, result.documents.size());
        this.accountTestHelper.verifyEndpointsPage(Arrays.asList(endpoint2, endpoint3), result);
    }
}
