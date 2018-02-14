/*
 * Copyright (c) 2015-2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.model.adapters.awsadapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.ARN_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.EXTERNAL_ID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEYID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEY_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.SESSION_TOKEN_KEY;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.setAwsClientMockInfo;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.vmware.photon.controller.model.PhotonModelMetricServices;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.tasks.EndpointServiceTests;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.xenon.common.BasicTestCase;
import com.vmware.xenon.common.QueryResultsProcessor;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;

public class TestAWSEndpointService extends BasicTestCase {

    public String accessKey = "access-key";
    public String secretKey = "secret-key";
    public String regionId = "us-east-1";
    public String sessionToken = "session-token";
    public String arn = "mock-arn";
    public String externalId = "mock-external-id";
    public boolean isMock = true;

    // The actual test Endpoint Specific runner
    private EndpointServiceTests endpointTestsRunner;

    private EndpointState endpointState;

    @Before
    public void setUp() throws Throwable {

        this.host.setMaintenanceIntervalMicros(TimeUnit.MILLISECONDS.toMicros(10));
        PhotonModelServices.startServices(this.host);
        PhotonModelMetricServices.startServices(this.host);
        PhotonModelTaskServices.startServices(this.host);
        PhotonModelAdaptersRegistryAdapters.startServices(this.host);
        AWSAdaptersTestUtils.startServicesSynchronously(this.host);

        this.host.setTimeoutSeconds(300);

        this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
        this.host.waitForServiceAvailable(PhotonModelMetricServices.LINKS);
        this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);

        this.endpointTestsRunner = new EndpointServiceTests(
                this.host, this.regionId, this.isMock, ComputeDescription.ENVIRONMENT_NAME_AWS);

        this.endpointState = newEndpointState();

        this.host.log(Level.INFO, "Executing test with isMock = %s", this.isMock);
    }

    @After
    public void tearDown() {
        setAwsClientMockInfo(false, null);
    }

    @Test
    public void testValidateCredentials() throws Throwable {

        this.endpointTestsRunner.testValidateCredentials(this.endpointState);
    }

    @Test
    @Ignore("https://jira.eng.vmware.com/browse/VCOM-3360")
    public void testValidateSessionCredentials() throws Throwable {

        this.endpointState.endpointProperties.put(SESSION_TOKEN_KEY, this.sessionToken);
        this.endpointState.endpointProperties.put(ARN_KEY, this.arn);
        this.endpointState.endpointProperties.put(EXTERNAL_ID_KEY, this.externalId);

        this.endpointTestsRunner.testValidateCredentials(this.endpointState);
    }

    @Test
    public void testCreateEndpoint() throws Throwable {

        this.endpointTestsRunner.testCreateEndpoint(this.endpointState);
    }

    @Test
    public void testCreateAndThenValidate() throws Throwable {

        this.endpointTestsRunner.testCreateAndThenValidate(this.endpointState);

        // Tests that EndpointService QueryTasks can use SELECT_LINKS + EXPAND_LINKS
        Query query = Builder.create()
                .addKindFieldClause(EndpointState.class)
                .build();
        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .addOptions(EnumSet.of(QueryOption.EXPAND_CONTENT, QueryOption.SELECT_LINKS,
                        QueryOption.EXPAND_LINKS))
                .addLinkTerm(EndpointState.FIELD_NAME_AUTH_CREDENTIALS_LINK)
                .setQuery(query)
                .build();
        this.host.createQueryTaskService(queryTask, false, true, queryTask, null);
        ServiceDocumentQueryResult results = queryTask.results;
        assertEquals(Long.valueOf(1), results.documentCount);
        assertEquals(1, results.selectedLinks.size());
        assertEquals(1, results.selectedDocuments.size());

        QueryResultsProcessor processor = QueryResultsProcessor.create(results);
        for (EndpointState endpoint : processor.documents(EndpointState.class)) {
            String authCredentialSelfLink = endpoint.authCredentialsLink;
            assertNotNull(authCredentialSelfLink);
            assertNotNull(
                    processor.selectedDocument(authCredentialSelfLink,
                            AuthCredentialsServiceState.class));
        }
    }

    @Test
    public void testShouldFailOnMissingData() throws Throwable {

        this.endpointTestsRunner.testShouldFailOnMissingData(this.endpointState);
    }

    @Test
    public void testShouldFailOnDuplicateEndpoint() throws Throwable {

        this.endpointTestsRunner.testShouldFailOnCreatingDuplicateEndpoint(this.endpointState);
    }

    private EndpointState newEndpointState() {
        EndpointState endpoint = new EndpointState();
        endpoint.endpointType = EndpointType.aws.name();
        endpoint.name = EndpointType.aws.name();
        endpoint.regionId = this.regionId;
        endpoint.endpointProperties = new HashMap<>();
        endpoint.endpointProperties.put(PRIVATE_KEY_KEY, this.secretKey);
        endpoint.endpointProperties.put(PRIVATE_KEYID_KEY, this.accessKey);
        return endpoint;
    }

}
