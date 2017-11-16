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
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.USER_LINK_KEY;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_TENANT_ID;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.COMPUTE_NAME_SEPARATOR;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.PhotonModelMetricServices;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapters.azure.AzureAdapters;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.ea.AzureEaAdapters;
import com.vmware.photon.controller.model.adapters.azure.ea.enumeration
        .AzureSubscriptionsEnumerationService.AzureSubscriptionsEnumerationRequest;
import com.vmware.photon.controller.model.adapters.azure.model.cost.AzureSubscription;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources
        .ComputeDescriptionService.ComputeDescription.ComputeType;
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
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.TestRequestSender;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;


public class AzureSubscriptionsEnumerationServiceTest {

    private VerificationHost host;
    private boolean isMock;
    private String computeLink;
    private Collection<String> createdComputeLinks;
    private ComputeState compute;
    private String existingSubsComputeLink1;
    private String existingSubsComputeLink2;
    private String existingSubsComputeLink3;

    private static final String SUBSCRIPTION_EXISTING_1 = "subscriptionExisting1";
    private static final String SUBSCRIPTION_EXISTING_2 = "subscriptionExisting2";
    private static final String SUBSCRIPTION_ID_1 = "subscriptionId1";
    private static final String SUBSCRIPTION_ID_2 = "subscriptionId2";
    private static final String ACCOUNT_EMAIL_ID_1 = "accountId1";
    private static final String ACCOUNT_EMAIL_ID_2 = "accountId2";
    // Azure EA account enrollment number
    private static final String ENROLLMENT_NUMNBER = "100";
    private static final String API_KEY = "clientKey";
    private static final String TENANT_ID = "tenantId";
    private static final String REGION = "westus";
    private static final String CLIENT_ID = "clientId";
    private static final String CLIET_KEY = "clientKey";

    @Before
    public void setUp() throws Exception {
        this.host = VerificationHost.create(0);
        this.isMock = true;
        this.createdComputeLinks = new ArrayList<>();
        try {
            this.host.start();
            PhotonModelServices.startServices(this.host);
            PhotonModelMetricServices.startServices(this.host);
            PhotonModelAdaptersRegistryAdapters.startServices(this.host);
            PhotonModelTaskServices.startServices(this.host);
            AzureAdapters.startServices(this.host);
            AzureEaAdapters.startServices(this.host);

            this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelMetricServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelAdaptersRegistryAdapters.LINKS);
            this.host.waitForServiceAvailable(AzureAdapters.LINKS);
            this.host.waitForServiceAvailable(AzureEaAdapters.LINKS);

            this.host.setTimeoutSeconds(600);

            // Create Azure non-ea endpoints
            EndpointState nonEaEp1 = createNonEaEndpointState(SUBSCRIPTION_EXISTING_1);
            EndpointAllocationTaskState state1 = createEndpoint(nonEaEp1);
            this.existingSubsComputeLink1 = state1.endpointState.computeLink;
            EndpointState nonEaEp2 = createNonEaEndpointState(SUBSCRIPTION_EXISTING_2);
            EndpointAllocationTaskState state2 = createEndpoint(nonEaEp2);
            this.existingSubsComputeLink2 = state2.endpointState.computeLink;

            // Create an Azure endpoint which will act as Azure EA endpoint
            EndpointState ep = createEaEndpointState();
            EndpointAllocationTaskState taskState = createEndpoint(ep);
            this.computeLink = taskState.endpointState.computeLink;
            this.compute = getServiceSynchronously(this.computeLink, ComputeState.class);

        } catch (Throwable e) {
            throw new Exception(e);
        }
    }

    private EndpointAllocationTaskState createEndpoint(
            EndpointState endpointState) throws Throwable {
        EndpointAllocationTaskState validateEndpoint = new EndpointAllocationTaskState();
        validateEndpoint.endpointState = endpointState;
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
        return taskState;
    }

    private EndpointService.EndpointState createEaEndpointState() {
        EndpointService.EndpointState endpoint = new EndpointService.EndpointState();
        endpoint.endpointType = EndpointType.azure_ea.name();
        endpoint.name = EndpointType.azure_ea.name();

        endpoint.endpointProperties = new HashMap<>();
        endpoint.endpointProperties.put(PRIVATE_KEYID_KEY, ENROLLMENT_NUMNBER);
        endpoint.endpointProperties.put(PRIVATE_KEY_KEY, API_KEY);
        return endpoint;
    }

    public EndpointService.EndpointState createNonEaEndpointState(String subscriptionId) {
        EndpointService.EndpointState endpoint = new EndpointService.EndpointState();
        endpoint.endpointType = PhotonModelConstants.EndpointType.azure.name();
        endpoint.name = PhotonModelConstants.EndpointType.azure.name();
        endpoint.regionId = REGION;

        endpoint.endpointProperties = new HashMap<>();
        endpoint.endpointProperties.put(PRIVATE_KEYID_KEY, CLIENT_ID);
        endpoint.endpointProperties.put(PRIVATE_KEY_KEY, CLIET_KEY);
        endpoint.endpointProperties.put(AZURE_TENANT_ID, TENANT_ID);
        endpoint.endpointProperties.put(USER_LINK_KEY, subscriptionId);
        return endpoint;
    }

    protected <T extends ServiceDocument> T getServiceSynchronously(
            String serviceLink, Class<T> type) throws Throwable {
        return this.host.getServiceState(null, type,
                UriUtils.buildUri(this.host, serviceLink));
    }

    @Test
    public void test() throws Throwable {


        /*
         * Tests the case of adding a Azure subscription when there are no subscriptions already
         * under the EA account
         */
        testAddFirstAzureSubscription();

        /*
         * Create a new Azure subscription
         */
        testAddMoreAzureSubscriptions();

        /**
         * Create existing subscriptions again
         */
        testAddSameAzureSubscriptions();

        /**
         * Add a non ea azure subscription now and check if it has cost
         */
        testAddNonEaAzureSubscriptionLater();
    }


    @Test
    public void testConstructSubscriptionName() {
        String subzId = "subscriptionId";
        String accountOwnerId = "account@ownerId.com";
        String accountName = "Account Name";
        String subzName = "Subscription Name";

        AzureSubscription subz = new AzureSubscription();
        subz.entityId = subzId;
        subz.parentEntityId = accountOwnerId;
        subz.parentEntityName = accountName;
        subz.entityName = subzName;
        String constructedSubzName = AzureUtils.constructSubscriptionName(subz);
        String correctSubzName = accountName + COMPUTE_NAME_SEPARATOR + subzName;
        Assert.assertEquals("Subscription name is not correct.", correctSubzName,
                constructedSubzName);

        // if account name is not available, subscription ID should be displayed
        subz.parentEntityName = null;
        correctSubzName = subzId;
        constructedSubzName = AzureUtils.constructSubscriptionName(subz);
        Assert.assertEquals("Subscription name is not correct.", correctSubzName,
                constructedSubzName);

        // if account name is not available, subscription ID should be displayed
        subz.parentEntityName = " ";
        correctSubzName = subzId;
        constructedSubzName = AzureUtils.constructSubscriptionName(subz);
        Assert.assertEquals("Subscription name is not correct.", correctSubzName,
                constructedSubzName);

        // if subscription name is not available, subscription ID should be displayed
        subz.entityName = " ";
        correctSubzName = subzId;
        constructedSubzName = AzureUtils.constructSubscriptionName(subz);
        Assert.assertEquals("Subscription name is not correct.", correctSubzName,
                constructedSubzName);

    }

    private void  testAddNonEaAzureSubscriptionLater() throws Throwable {
        // Create Azure non-ea endpoints
        EndpointState nonEaEp3 = createNonEaEndpointState(SUBSCRIPTION_ID_2);
        EndpointAllocationTaskState state3 = createEndpoint(nonEaEp3);
        this.existingSubsComputeLink3 = state3.endpointState.computeLink;

        AzureSubscription subscription1 = getAzureSubscription(SUBSCRIPTION_ID_1,
                ACCOUNT_EMAIL_ID_1);
        AzureSubscription subscription2 = getAzureSubscription(SUBSCRIPTION_ID_2,
                ACCOUNT_EMAIL_ID_2);
        AzureSubscription existingSubscription2 = getAzureSubscription(SUBSCRIPTION_EXISTING_2,
                ACCOUNT_EMAIL_ID_2);
        createAzureCostComputesForSubscriptions(Arrays.asList(subscription1, subscription2,
                existingSubscription2));

        // Query for Azure Computes created with CLIENT_ID as enrollment Number
        QueryTask task = createQueryTaskForAzureComputes(ENROLLMENT_NUMNBER,
                Collections.singletonList(TENANT_ID));
        QueryTask queryTaskResponse = executQuerySynchronously(task);

        assertQueryTaskResponse(queryTaskResponse, 7);
    }

    private void testAddSameAzureSubscriptions() throws  Throwable {
        // Request for creating computes for existing Azure Subscriptions
        AzureSubscription subscription1 = getAzureSubscription(SUBSCRIPTION_ID_1,
                ACCOUNT_EMAIL_ID_1);
        AzureSubscription subscription2 = getAzureSubscription(SUBSCRIPTION_ID_2,
                ACCOUNT_EMAIL_ID_2);
        AzureSubscription existingSubscription2 = getAzureSubscription(SUBSCRIPTION_EXISTING_2,
                ACCOUNT_EMAIL_ID_2);
        createAzureCostComputesForSubscriptions(Arrays.asList(subscription1, subscription2,
                existingSubscription2));

        // Query for Azure Computes created with CLIENT_ID as enrollment Number
        QueryTask task = createQueryTaskForAzureComputes(ENROLLMENT_NUMNBER,
                Collections.singletonList(TENANT_ID));
        QueryTask queryTaskResponse = executQuerySynchronously(task);

        assertQueryTaskResponse(queryTaskResponse, 6);

        // Remove compute for existing subscription
        ComputeState existingSubsCs1 = Utils.fromJson(queryTaskResponse.results
                .documents.remove(this.existingSubsComputeLink1), ComputeState.class);
        Assert.assertNotNull(existingSubsCs1);
        Assert.assertNull(existingSubsCs1
                .customProperties.get(PhotonModelConstants.AUTO_DISCOVERED_ENTITY));

        ComputeState existingSubsCs2 = Utils.fromJson(queryTaskResponse.results
                .documents.remove(this.existingSubsComputeLink2), ComputeState.class);
        Assert.assertNotNull(existingSubsCs2);
        Assert.assertNull(existingSubsCs1
                .customProperties.get(PhotonModelConstants.AUTO_DISCOVERED_ENTITY));

        // Remove the already asserted computes
        this.createdComputeLinks.stream().forEach(computeLnk -> {
            queryTaskResponse.results.documents.remove(computeLnk);
        });

        // Get and assert the returned compute, which is created newly
        ComputeState cs = Utils
                .fromJson(queryTaskResponse.results.documents.values().iterator().next(),
                        ComputeState.class);
        assertPropertiesOfComputeState(cs,
                ENROLLMENT_NUMNBER, SUBSCRIPTION_EXISTING_2,
                ACCOUNT_EMAIL_ID_2, this.compute.endpointLink, this.compute.tenantLinks);

        this.createdComputeLinks.add(cs.documentSelfLink);
    }

    private void testAddMoreAzureSubscriptions() throws Throwable {
        // Request for creating computes for another Azure Subscriptions
        AzureSubscription subscription = getAzureSubscription(SUBSCRIPTION_ID_2, ACCOUNT_EMAIL_ID_2);
        createAzureCostComputesForSubscriptions(Collections.singletonList(subscription));

        // Query for Azure Computes created with CLIENT_ID as enrollment Number
        QueryTask task = createQueryTaskForAzureComputes(ENROLLMENT_NUMNBER,
                Collections.singletonList(TENANT_ID));
        QueryTask queryTaskResponse = executQuerySynchronously(task);

        assertQueryTaskResponse(queryTaskResponse, 4);

        // Remove compute for existing subscription
        ComputeState existingSubsCs = Utils.fromJson(queryTaskResponse.results
                .documents.remove(this.existingSubsComputeLink1), ComputeState.class);
        Assert.assertNotNull(existingSubsCs);
        Assert.assertNull(existingSubsCs
                .customProperties.get(PhotonModelConstants.AUTO_DISCOVERED_ENTITY));

        // Remove the already asserted computes
        this.createdComputeLinks.stream().forEach(computeLnk -> {
            queryTaskResponse.results.documents.remove(computeLnk);
        });

        // Get and assert the returned compute
        ComputeState cs = Utils
                .fromJson(queryTaskResponse.results.documents.values().iterator().next(),
                        ComputeState.class);
        assertPropertiesOfComputeState(cs,
                ENROLLMENT_NUMNBER, SUBSCRIPTION_ID_2,
                ACCOUNT_EMAIL_ID_2, this.compute.endpointLink, this.compute.tenantLinks);

        this.createdComputeLinks.add(cs.documentSelfLink);

    }


    private void testAddFirstAzureSubscription() throws Throwable {
        // Fail if this.computeLink is null
        Assert.assertNotNull("Root computeLink of Azure EA account is null",
                this.computeLink);

        // Fail if this.compute is null
        Assert.assertNotNull("Root compute of Azure EA account is null", this.compute);

        // Request for creating computes for 1 Azure Subscriptions
        AzureSubscription subscription = getAzureSubscription(SUBSCRIPTION_ID_1,
                ACCOUNT_EMAIL_ID_1);
        AzureSubscription existingSubscription1 = getAzureSubscription(SUBSCRIPTION_EXISTING_1,
                ACCOUNT_EMAIL_ID_1);
        createAzureCostComputesForSubscriptions(Arrays.asList(subscription, existingSubscription1));

        // Query for Azure Computes created with CLIENT_ID as enrollment Number
        QueryTask task = createQueryTaskForAzureComputes(ENROLLMENT_NUMNBER,
                Collections.singletonList(TENANT_ID));
        QueryTask queryTaskResponse = executQuerySynchronously(task);
        assertQueryTaskResponse(queryTaskResponse, 3);

        // Remove compute for existing subscription
        ComputeState existingSubsCs = Utils.fromJson(queryTaskResponse.results
                .documents.remove(this.existingSubsComputeLink1), ComputeState.class);
        Assert.assertNotNull(existingSubsCs);
        Assert.assertNull(existingSubsCs
                .customProperties.get(PhotonModelConstants.AUTO_DISCOVERED_ENTITY));

        // Get and assert the returned compute
        queryTaskResponse.results.documents.values().forEach(resultCompute -> {
            ComputeState cs = Utils
                    .fromJson(resultCompute, ComputeState.class);
            assertPropertiesOfComputeState(cs,
                    ENROLLMENT_NUMNBER,
                    cs.customProperties.get(AzureConstants.AZURE_SUBSCRIPTION_ID_KEY),
                    ACCOUNT_EMAIL_ID_1, this.compute.endpointLink, this.compute.tenantLinks);
            this.createdComputeLinks.add(cs.documentSelfLink);
        });
    }

    private void createAzureCostComputesForSubscriptions(
            Collection<AzureSubscription> subscriptions) {
        AzureSubscriptionsEnumerationRequest request =
                getAzureCostComputeEnumRequestForSubscriptions(subscriptions);
        TestRequestSender sender = new TestRequestSender(this.host);
        Operation op = Operation.createPatch(this.host,
                AzureSubscriptionsEnumerationService.SELF_LINK)
                .setBody(request);
        sender.sendAndWait(op);
    }

    private void assertQueryTaskResponse(QueryTask queryTaskResponse, int expectedDocumentNumber) {
        Assert.assertNotNull(queryTaskResponse);
        Assert.assertNotNull(queryTaskResponse.results );
        Assert.assertNotNull(queryTaskResponse.results.documents != null);
        Assert.assertEquals(expectedDocumentNumber, queryTaskResponse.results.documents.size());
    }

    private QueryTask executQuerySynchronously(QueryTask task) throws Throwable {

        task.documentExpirationTimeMicros = Utils
                .fromNowMicrosUtc(TimeUnit.DAYS.toMicros(1));

        URI queryTaskUri = this.host.createQueryTaskService(
                UriUtils.buildUri(this.host.getUri(), ServiceUriPaths.CORE_QUERY_TASKS),
                task, false, false, task, null);

        // Wait for query task to have data
        QueryTask queryTaskResponse = null;
        Date exp = this.host.getTestExpiration();
        while (new Date().before(exp)) {
            queryTaskResponse = this.host.getServiceState(
                    null, QueryTask.class, queryTaskUri);
            if (queryTaskResponse.results != null
                    && queryTaskResponse.results.documentLinks != null
                    && !queryTaskResponse.results.documentLinks.isEmpty()) {
                break;
            }
            Thread.sleep(100);
        }
        return queryTaskResponse;
    }

    private QueryTask createQueryTaskForAzureComputes(String azureEnrollmentNumber,
                                                      List<String> tenantLinks) {
        //Fetch ComputeStates having custom property endPointType as Azure, Type as ENDPOINT_HOST
        Query azureEndpointsQuery = Query.Builder.create().addKindFieldClause(ComputeState.class)
                .addCompositeFieldClause(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                        EndpointAllocationTaskService.CUSTOM_PROP_ENPOINT_TYPE,
                        EndpointType.azure.name())
                .addCompositeFieldClause(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                        AzureConstants.AZURE_ENROLLMENT_NUMBER_KEY, azureEnrollmentNumber)
                .addFieldClause(ComputeState.FIELD_NAME_TYPE, ComputeType.ENDPOINT_HOST)
                .build();
        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .setQuery(azureEndpointsQuery).build();
        queryTask.tenantLinks = tenantLinks;
        queryTask.setDirect(true);
        return queryTask;
    }

    private void assertPropertiesOfComputeState(ComputeState cs, String expectedEnrollmentNumber,
                                                String expectedSubscriptionUuid,
                                                String expectedAccountEmailId,
                                                String expectedEndpointLink,
                                                List<String> expectedTenantLinks) {
        Assert.assertNotNull(cs.customProperties);
        Assert.assertEquals(expectedEnrollmentNumber,
                cs.customProperties.get(AzureConstants.AZURE_ENROLLMENT_NUMBER_KEY));
        Assert.assertEquals(expectedAccountEmailId,
                cs.customProperties.get(AzureConstants.AZURE_ACCOUNT_OWNER_EMAIL_ID));
        Assert.assertEquals(expectedSubscriptionUuid,
                cs.customProperties.get(AzureConstants.AZURE_SUBSCRIPTION_ID_KEY));
        Assert.assertEquals(Boolean.TRUE.toString(),
                cs.customProperties.get(PhotonModelConstants.AUTO_DISCOVERED_ENTITY));
        Assert.assertEquals(expectedEndpointLink, cs.endpointLink);
        Assert.assertEquals(ComputeType.ENDPOINT_HOST, cs.type);
        Assert.assertEquals(ComputeDescription.ENVIRONMENT_NAME_AZURE, cs.environmentName);
        Assert.assertEquals(expectedTenantLinks, cs.tenantLinks);
        Assert.assertNotNull(cs.creationTimeMicros);
    }

    private AzureSubscription getAzureSubscription(String subscriptionId, String accountId) {
        AzureSubscription subscription = new AzureSubscription();
        subscription.entityId = subscriptionId;
        subscription.parentEntityId = accountId;
        return subscription;
    }

    private AzureSubscriptionsEnumerationRequest getAzureCostComputeEnumRequestForSubscriptions(
            Collection<AzureSubscription> azureSubscriptions) {
        AzureSubscriptionsEnumerationRequest request = new AzureSubscriptionsEnumerationRequest();
        request.resourceReference = UriUtils.buildUri(this.host, this.computeLink);
        request.azureSubscriptions = azureSubscriptions;
        return request;
    }

    @After
    public void tearDown() {
        if (this.host == null) {
            return;
        }
        this.host.tearDown();
    }
}
