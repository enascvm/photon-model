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

package com.vmware.photon.controller.model.adapters.azure.ea.enumeration;


import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEYID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEY_KEY;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.PhotonModelMetricServices;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.ea.AzureEaAdapters;
import com.vmware.photon.controller.model.adapters.azure.ea.enumeration.AzureSubscriptionEndpointsEnumerationService.AzureSubscriptionEndpointsEnumerationRequest;
import com.vmware.photon.controller.model.adapters.azure.model.cost.AzureSubscription;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService.EndpointAllocationTaskState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.TestRequestSender;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class AzureSubscriptionEndpointsEnumerationServiceTest {

    private VerificationHost host;
    private boolean isMock;
    private String computeLink;
    private String endpointLink;
    private Collection<String> createdEndpointLinks;
    private ComputeState compute;

    private static final String SUBSCRIPTION_ID_1 = "subscriptionId1";
    private static final String SUBSCRIPTION_ID_2 = "subscriptionId2";
    private static final String ACCOUNT_ID_1 = "accountId1";
    private static final String ACCOUNT_ID_2 = "accountId2";
    // Azure EA account enrollment number
    private static final String ENROLLMENT_NUMNBER = "100";
    private static final String API_KEY = "clientKey";
    private static final String TENANT_ID = "tenantId";

    @Before
    public void setUp() throws Exception {
        this.host = VerificationHost.create(0);
        this.isMock = true;
        this.createdEndpointLinks = new ArrayList<>();
        try {
            this.host.start();
            PhotonModelServices.startServices(this.host);
            PhotonModelMetricServices.startServices(this.host);
            PhotonModelAdaptersRegistryAdapters.startServices(this.host);
            PhotonModelTaskServices.startServices(this.host);
            AzureEaAdapters.startServices(this.host);
            this.host.startService(new AzureSubscriptionEndpointsEnumerationService());

            this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelMetricServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelAdaptersRegistryAdapters.LINKS);
            this.host.waitForServiceAvailable(AzureEaAdapters.LINKS);
            this.host.waitForServiceAvailable(
                    AzureSubscriptionEndpointsEnumerationService.SELF_LINK);

            this.host.setTimeoutSeconds(600);

            // Create an Azure endpoint which will act as Azure EA endpoint
            EndpointState ep = createEndpointState();

            EndpointAllocationTaskState validateEndpoint = new EndpointAllocationTaskState();
            validateEndpoint.endpointState = ep;
            validateEndpoint.options = this.isMock ? EnumSet.of(TaskOption.IS_MOCK) : null;
            validateEndpoint.tenantLinks = Collections.singletonList(TENANT_ID);
            EndpointAllocationTaskState outTask = TestUtils
                    .doPost(this.host, validateEndpoint,
                            EndpointAllocationTaskState.class,
                            UriUtils.buildUri(this.host,
                                    EndpointAllocationTaskService.FACTORY_LINK));

            this.host.waitForFinishedTask(
                    EndpointAllocationTaskState.class,
                    outTask.documentSelfLink);

            EndpointAllocationTaskState taskState = getServiceSynchronously(
                    outTask.documentSelfLink,
                    EndpointAllocationTaskState.class);

            this.endpointLink = taskState.endpointState.documentSelfLink;
            this.computeLink = taskState.endpointState.computeLink;
            this.compute = getServiceSynchronously(this.computeLink, ComputeState.class);

        } catch (Throwable e) {
            throw new Exception(e);
        }
    }

    private EndpointService.EndpointState createEndpointState() {
        EndpointService.EndpointState endpoint = new EndpointService.EndpointState();
        endpoint.endpointType = EndpointType.azure_ea.name();
        endpoint.name = EndpointType.azure_ea.name();

        endpoint.endpointProperties = new HashMap<>();
        endpoint.endpointProperties.put(PRIVATE_KEYID_KEY, ENROLLMENT_NUMNBER);
        endpoint.endpointProperties.put(PRIVATE_KEY_KEY, API_KEY);
        return endpoint;
    }

    protected <T extends ServiceDocument> T getServiceSynchronously(
            String serviceLink, Class<T> type) throws Throwable {
        return this.host.getServiceState(null, type,
                UriUtils.buildUri(this.host, serviceLink));
    }

    @Test
    public void testSubscriptionEndpointsEnumerationService() throws Throwable {

        // Fail if this.computeLink is null
        Assert.assertNotNull("Root computeLink of Azure EA account is null",
                this.computeLink);

        // Fail if this.compute is null
        Assert.assertNotNull("Root compute of Azure EA account is null", this.compute);

        testAddFirstAzureSubscription();

        testAddSecondAzureSubscription();

        testAddSameAzureSubscriptions();
    }

    private void testAddSameAzureSubscriptions() throws  Throwable {
        // Request for creating computes for existing Azure Subscriptions
        AzureSubscription subscription1 = getAzureSubscription(SUBSCRIPTION_ID_1, ACCOUNT_ID_1);
        AzureSubscription subscription2 = getAzureSubscription(SUBSCRIPTION_ID_2, ACCOUNT_ID_2);
        Collection<AzureSubscription> subscriptions = new ArrayList<>();
        subscriptions.add(subscription1);
        subscriptions.add(subscription2);
        createAzureEndpointsForSubscriptions(subscriptions);

        // Query the Endpoints to assert
        ServiceDocumentQueryResult result = this.host.getExpandedFactoryState(
                UriUtils.buildUri(this.host, EndpointService.FACTORY_LINK));
        Assert.assertEquals(3, result.documents.size());
    }

    private void testAddSecondAzureSubscription() throws Throwable {
        // Request for creating computes for 1 Azure Subscriptions
        AzureSubscription subscription = getAzureSubscription(SUBSCRIPTION_ID_2, ACCOUNT_ID_2);
        createAzureEndpointsForSubscriptions(Collections.singletonList(subscription));

        // Query the Endpoints to assert
        ServiceDocumentQueryResult result = this.host.getExpandedFactoryState(
                UriUtils.buildUri(this.host, EndpointService.FACTORY_LINK));
        Assert.assertEquals(3, result.documents.size());

        // Assert the created Endpoint and other resources
        result.documents.remove(this.endpointLink);
        // Remove the endpoints created earlier
        this.createdEndpointLinks.forEach(endpoint ->  result.documents.remove(endpoint));

        EndpointState endpointStateCreated = Utils.fromJson(
                result.documents.values().iterator().next(), EndpointState.class);
        assertCreatedEndpoint(endpointStateCreated, SUBSCRIPTION_ID_2);
        this.createdEndpointLinks.add(endpointStateCreated.documentSelfLink);

        // Assert the root compute under the endpoint
        ComputeState computeStateCreated = getServiceSynchronously(endpointStateCreated.computeLink,
                ComputeState.class);
        assertCreatedComputeState(computeStateCreated, SUBSCRIPTION_ID_2, ACCOUNT_ID_2);

        // Assert the partial AuthCredentialsState
        AuthCredentialsServiceState authCreated = getServiceSynchronously(
                endpointStateCreated.authCredentialsLink, AuthCredentialsServiceState.class);
        assertAuthCredentialState(authCreated, SUBSCRIPTION_ID_2);
    }

    private void testAddFirstAzureSubscription() throws Throwable {
        // Request for creating computes for 1 Azure Subscriptions
        AzureSubscription subscription = getAzureSubscription(SUBSCRIPTION_ID_1, ACCOUNT_ID_1);
        createAzureEndpointsForSubscriptions(Collections.singletonList(subscription));

        // Query the Endpoints to assert
        ServiceDocumentQueryResult result = this.host.getExpandedFactoryState(
                UriUtils.buildUri(this.host, EndpointService.FACTORY_LINK));
        Assert.assertEquals(2, result.documents.size());

        // Assert the created Endpoint and other resources
        result.documents.remove(this.endpointLink);
        EndpointState endpointStateCreated = Utils.fromJson(
                result.documents.values().iterator().next(), EndpointState.class);
        assertCreatedEndpoint(endpointStateCreated, SUBSCRIPTION_ID_1);
        this.createdEndpointLinks.add(endpointStateCreated.documentSelfLink);

        // Assert the root compute under the endpoint
        ComputeState computeStateCreated = getServiceSynchronously(endpointStateCreated.computeLink,
                ComputeState.class);
        assertCreatedComputeState(computeStateCreated, SUBSCRIPTION_ID_1, ACCOUNT_ID_1);

        // Assert the partial AuthCredentialsState
        AuthCredentialsServiceState authCreated = getServiceSynchronously(
                endpointStateCreated.authCredentialsLink, AuthCredentialsServiceState.class);
        assertAuthCredentialState(authCreated, SUBSCRIPTION_ID_1);
    }

    private void assertAuthCredentialState(AuthCredentialsServiceState authCreated,
                                           String subscriptionId) {
        Assert.assertEquals(subscriptionId, authCreated.userLink);
        Assert.assertNull(authCreated.privateKey);
        Assert.assertNull(authCreated.privateKeyId);
        Assert.assertEquals(this.compute.tenantLinks, authCreated.tenantLinks);
    }

    private void assertCreatedComputeState(ComputeState computeStateCreated, String subscriptionId,
                                           String accountId) {
        Assert.assertNotNull(computeStateCreated.customProperties);
        Assert.assertEquals(subscriptionId, computeStateCreated.customProperties
                .get(AzureConstants.AZURE_SUBSCRIPTION_ID_KEY));
        Assert.assertEquals(accountId, computeStateCreated.customProperties
                .get(AzureConstants.AZURE_ACCOUNT_ID));
        Assert.assertEquals(this.compute.resourcePoolLink, computeStateCreated.resourcePoolLink);
        Assert.assertEquals(this.compute.tenantLinks, computeStateCreated.tenantLinks);
    }

    private void assertCreatedEndpoint(EndpointState endpointStateCreated, String subscriptionId) {
        Assert.assertNotNull(endpointStateCreated.endpointProperties);
        Assert.assertEquals(subscriptionId, endpointStateCreated.endpointProperties
                .get(EndpointConfigRequest.USER_LINK_KEY));
        Assert.assertEquals(this.compute.resourcePoolLink, endpointStateCreated.resourcePoolLink);
        Assert.assertEquals(this.compute.endpointLink, endpointStateCreated.parentLink);
        Assert.assertEquals(this.compute.tenantLinks, endpointStateCreated.tenantLinks);
        Assert.assertEquals(EndpointType.azure.name(), endpointStateCreated.endpointType);
    }

    private void createAzureEndpointsForSubscriptions(Collection<AzureSubscription> subscriptions) {
        AzureSubscriptionEndpointsEnumerationRequest request =
                new AzureSubscriptionEndpointsEnumerationRequest();
        request.resourceReference = UriUtils.buildUri(this.host, this.computeLink);
        request.azureSubscriptions = subscriptions;
        TestRequestSender sender = new TestRequestSender(this.host);
        Operation op = Operation.createPatch(this.host,
                AzureSubscriptionEndpointsEnumerationService.SELF_LINK)
                .setBody(request);
        sender.sendAndWait(op);
    }

    private AzureSubscription getAzureSubscription(String subscriptionId, String accountId) {
        AzureSubscription subscription = new AzureSubscription();
        subscription.entityId = subscriptionId;
        subscription.parentEntityId = accountId;
        return subscription;
    }

    @After
    public void tearDown() {
        if (this.host == null) {
            return;
        }
        this.host.tearDown();
    }
}
