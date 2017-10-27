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

package com.vmware.photon.controller.model.tasks;

import static org.junit.Assert.assertEquals;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.tasks.ResourceGroomerTaskService.EndpointResourceDeletionRequest;
import com.vmware.xenon.common.BasicTestCase;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Tests to validate resource groomer task.
 */
public class ResourceGroomerTaskServiceTest extends BasicTestCase {
    public static final String VALID_ENDPOINT_LINK_1 = "/resources/endpoints/valid-endpoint-1";
    public static final String VALID_ENDPOINT_LINK_2 = "/resources/endpoints/valid-endpoint-2";
    public static final String INVALID_ENDPOINT_LINK_1 = "/resources/endpoints/invalid-endpoint-1";
    public static final String INVALID_ENDPOINT_LINK_2 = "/resources/endpoints/invalid-endpoint-2";
    public static final String TENANT_LINK_1 = "/tenants/project/valid-tenant-1";
    public static final String TENANT_LINK_2 = "/tenants/project/valid-tenant-2";

    @Before
    public void setUp() throws Throwable {
        // Lower the query result limit to test pagination with small number of resources.
        try {
            System.setProperty(QueryUtils.MAX_RESULT_LIMIT_PROPERTY, "1");

            PhotonModelServices.startServices(this.host);
            PhotonModelTaskServices.startServices(this.host);

            this.host.setTimeoutSeconds(600);

            this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
        } catch (Throwable e) {
            this.host.log("Error starting up services for the test %s", e.getMessage());
            throw new Exception(e);
        }
    }

    /**
     * Create some resources with valid endpointLinks and some resources with only invalid
     * endpointLinks. Validate that groomer task only deletes the resources with invalid
     * endpointLinks from the tenant for which it ran.
     */
    @Test
    public void testResourceGroomerTaskDeleteDocumentsMultiTenant() throws Throwable {
        if (!createValidEndpoints()) {
            return;
        }

        List<String> validComputeLinksTenant1 = createComputes(100,
                Collections.singleton(VALID_ENDPOINT_LINK_1), TENANT_LINK_1);
        List<String> validComputeLinksTenant2 = createComputes(100,
                Collections.singleton(VALID_ENDPOINT_LINK_2), TENANT_LINK_2);
        List<String> invalidComputeLinksTenant1 = createComputes(100,
                Collections.singleton(INVALID_ENDPOINT_LINK_1), TENANT_LINK_1);
        List<String> invalidComputeLinksTenant2 = createComputes(100,
                Collections.singleton(INVALID_ENDPOINT_LINK_1), TENANT_LINK_2);

        String taskLink = executeResourceGroomerTask();

        long computeCountValidEndpoint1Tenant1 = getComputeCount(Collections
                .singleton(VALID_ENDPOINT_LINK_1), TENANT_LINK_1);
        long computeCountInvalidEndpoint1Tenant1 = getComputeCount(Collections
                .singleton(INVALID_ENDPOINT_LINK_1), TENANT_LINK_1);
        long computeCountInvalidEndpoint1Tenant2 = getComputeCount(Collections
                .singleton(INVALID_ENDPOINT_LINK_1), TENANT_LINK_2);
        long computeCountValidEndpoint2Tenant2 = getComputeCount(Collections
                .singleton(VALID_ENDPOINT_LINK_2), TENANT_LINK_2);

        assertEquals(validComputeLinksTenant1.size(), computeCountValidEndpoint1Tenant1);
        assertEquals(0, computeCountInvalidEndpoint1Tenant1);
        assertEquals(validComputeLinksTenant2.size(), computeCountValidEndpoint2Tenant2);
        assertEquals(invalidComputeLinksTenant2.size(), computeCountInvalidEndpoint1Tenant2);

        assertStats(100, 0, taskLink);
    }

    /**
     * Create resources with valid as well as invalid endpointLinks and validate that groomer task
     * disassociates the resources from invalid endpointLinks only for the tenant for which the task
     * has run.
     */
    @Test
    public void testResourceGroomerTaskDisassociateDocumentsMultiTenant() throws Throwable {
        if (!createValidEndpoints()) {
            return;
        }

        Set<String> endpointLinksTenant1 = new HashSet<>();
        endpointLinksTenant1.add(VALID_ENDPOINT_LINK_1);
        endpointLinksTenant1.add(INVALID_ENDPOINT_LINK_1);
        endpointLinksTenant1.add(INVALID_ENDPOINT_LINK_2);

        Set<String> endpointLinksTenant2 = new HashSet<>();
        endpointLinksTenant2.add(VALID_ENDPOINT_LINK_2);
        endpointLinksTenant2.add(INVALID_ENDPOINT_LINK_1);
        endpointLinksTenant2.add(INVALID_ENDPOINT_LINK_2);

        List<String> validInvalidComputeLinksTenant1 = createComputes(100,
                endpointLinksTenant1, TENANT_LINK_1);
        List<String> validInvalidComputeLinksTenant2 = createComputes(100,
                endpointLinksTenant2, TENANT_LINK_2);

        String taskLink = executeResourceGroomerTask();

        long computeCountTenant2 = getComputeCount(endpointLinksTenant2, TENANT_LINK_2);

        long computeCountAllEndpointLinksTenant1 = getComputeCount(endpointLinksTenant1,
                TENANT_LINK_1);

        endpointLinksTenant1.remove(INVALID_ENDPOINT_LINK_1);
        long computeCountInvalidEndpointLink1Tenant1 = getComputeCount(endpointLinksTenant1,
                TENANT_LINK_1);

        endpointLinksTenant1.add(INVALID_ENDPOINT_LINK_1);
        endpointLinksTenant1.remove(INVALID_ENDPOINT_LINK_2);
        long computeCountInvalidEndpointLink2Tenant1 = getComputeCount(endpointLinksTenant1,
                TENANT_LINK_1);

        endpointLinksTenant1.remove(INVALID_ENDPOINT_LINK_1);
        long computeCountValidEndpointLinkTenant1 = getComputeCount(endpointLinksTenant1,
                TENANT_LINK_1);


        assertEquals(validInvalidComputeLinksTenant2.size(), computeCountTenant2);
        assertEquals(0, computeCountAllEndpointLinksTenant1);
        assertEquals(0, computeCountInvalidEndpointLink1Tenant1);
        assertEquals(0, computeCountInvalidEndpointLink2Tenant1);
        assertEquals(validInvalidComputeLinksTenant1.size(), computeCountValidEndpointLinkTenant1);

        assertStats(0, 100, taskLink);
    }

    /**
     * Create only valid resources and validate that groomer task does not disassociate or delete any
     * of the resources in any tenant.
     */
    @Test
    public void testResourceGroomerTaskNoInvalidDocumentsMultiTenant() throws Throwable {
        if (!createValidEndpoints()) {
            return;
        }

        List<String> validComputeLinksTenant1 = createComputes(100,
                Collections.singleton(VALID_ENDPOINT_LINK_1), TENANT_LINK_1);
        List<String> validComputeLinksTenant2 = createComputes(100,
                Collections.singleton(VALID_ENDPOINT_LINK_2), TENANT_LINK_2);
        List<String> invalidComputeLinksTenant2 = createComputes(100,
                Collections.singleton(INVALID_ENDPOINT_LINK_1), TENANT_LINK_2);

        String taskLink = executeResourceGroomerTask();

        long computeCountValidEndpoint1Tenant1 = getComputeCount(Collections
                .singleton(VALID_ENDPOINT_LINK_1), TENANT_LINK_1);
        long computeCountInvalidEndpoint1Tenant2 = getComputeCount(Collections
                .singleton(INVALID_ENDPOINT_LINK_1), TENANT_LINK_2);
        long computeCountValidEndpoint2Tenant2 = getComputeCount(Collections
                .singleton(VALID_ENDPOINT_LINK_2), TENANT_LINK_2);

        assertEquals(validComputeLinksTenant1.size(), computeCountValidEndpoint1Tenant1);
        assertEquals(validComputeLinksTenant2.size(), computeCountValidEndpoint2Tenant2);
        assertEquals(invalidComputeLinksTenant2.size(), computeCountInvalidEndpoint1Tenant2);

        assertStats(0, 0, taskLink);
    }

    /**
     * Create some resources with only invalid endpointLinks and some resources with a valid endpoint
     * link and some invalid endpoint links. Validate that groomer task appropriately diassociates
     * and deletes the resources only for the tenant for which it ran.
     */
    @Test
    public void testResourceGroomerTaskDeleteDisassociateDocuments() throws Throwable {
        if (!createValidEndpoints()) {
            return;
        }

        Set<String> invalidEndpointLinksTenant1 = new HashSet<>();
        invalidEndpointLinksTenant1.add(INVALID_ENDPOINT_LINK_1);
        invalidEndpointLinksTenant1.add(INVALID_ENDPOINT_LINK_2);

        Set<String> endpointLinksTenant1 = new HashSet<>();
        endpointLinksTenant1.add(VALID_ENDPOINT_LINK_1);
        endpointLinksTenant1.add(INVALID_ENDPOINT_LINK_1);
        endpointLinksTenant1.add(INVALID_ENDPOINT_LINK_2);

        Set<String> endpointLinksTenant2 = new HashSet<>();
        endpointLinksTenant2.add(VALID_ENDPOINT_LINK_2);
        endpointLinksTenant2.add(INVALID_ENDPOINT_LINK_1);
        endpointLinksTenant2.add(INVALID_ENDPOINT_LINK_2);

        List<String> validInvalidComputeLinksTenant1 = createComputes(100,
                endpointLinksTenant1, TENANT_LINK_1);
        List<String> validInvalidComputeLinksTenant2 = createComputes(100,
                endpointLinksTenant2, TENANT_LINK_2);
        List<String> invalidComputeLinksTenant1 = createComputes(100,
                invalidEndpointLinksTenant1, TENANT_LINK_1);

        String taskLink = executeResourceGroomerTask();

        long computeCountTenant2 = getComputeCount(endpointLinksTenant2, TENANT_LINK_2);

        long computeCountAllEndpointLinksTenant1 = getComputeCount(endpointLinksTenant1,
                TENANT_LINK_1);

        endpointLinksTenant1.remove(INVALID_ENDPOINT_LINK_1);
        long computeCountInvalidEndpointLink1Tenant1 = getComputeCount(endpointLinksTenant1,
                TENANT_LINK_1);

        endpointLinksTenant1.add(INVALID_ENDPOINT_LINK_1);
        endpointLinksTenant1.remove(INVALID_ENDPOINT_LINK_2);
        long computeCountInvalidEndpointLink2Tenant1 = getComputeCount(endpointLinksTenant1,
                TENANT_LINK_1);

        endpointLinksTenant1.remove(INVALID_ENDPOINT_LINK_1);
        long computeCountValidEndpointLinkTenant1 = getComputeCount(endpointLinksTenant1,
                TENANT_LINK_1);

        long computeCountInvalidEndpointLinksTenant1 = getComputeCount(invalidEndpointLinksTenant1,
                TENANT_LINK_1);

        assertEquals(validInvalidComputeLinksTenant2.size(), computeCountTenant2);
        assertEquals(0, computeCountAllEndpointLinksTenant1);
        assertEquals(0, computeCountInvalidEndpointLink1Tenant1);
        assertEquals(0, computeCountInvalidEndpointLink2Tenant1);
        assertEquals(0, computeCountInvalidEndpointLinksTenant1);
        assertEquals(validInvalidComputeLinksTenant1.size(), computeCountValidEndpointLinkTenant1);
        assertEquals(0, computeCountInvalidEndpointLinksTenant1);

        assertStats(100, 100, taskLink);
    }

    /**
     * POST to groomer task service without a tenantLink and expect illegalArgumentException.
     */
    @Test
    public void testResourceGroomerTaskNoTenantSpecified() throws Throwable {
        EndpointResourceDeletionRequest state = new EndpointResourceDeletionRequest();
        state.documentSelfLink = UriUtils.buildUriPath(ResourceGroomerTaskService.FACTORY_LINK,
                UUID.randomUUID().toString());

        Operation postOp = Operation.createPost(UriUtils.buildUri(this.host, ResourceGroomerTaskService.FACTORY_LINK))
                .setBody(state)
                .setReferer(this.host.getUri());

        Operation postResponse = this.host.waitForResponse(postOp);

        assertEquals(400, postResponse.getStatusCode());
    }

    /**
     * Create valid endpoint documents.
     */
    private boolean createValidEndpoints() {
        EndpointState state = new EndpointState();
        state.endpointType = "Amazon Web Services";
        state.id = UUID.randomUUID().toString();
        state.name = state.id;
        state.tenantLinks = Collections.singletonList(TENANT_LINK_1);

        state.documentSelfLink = VALID_ENDPOINT_LINK_1;

        Operation postEp1 = Operation
                .createPost(this.host, EndpointService.FACTORY_LINK)
                .setBody(state)
                .setReferer(this.host.getUri());

        state.documentSelfLink = VALID_ENDPOINT_LINK_2;

        Operation postEp2 = Operation
                .createPost(this.host, EndpointService.FACTORY_LINK)
                .setBody(state)
                .setReferer(this.host.getUri());

        Operation postResponse1 = this.host.waitForResponse(postEp1);
        Operation postResponse2 = this.host.waitForResponse(postEp2);

        if (postResponse1.getStatusCode() == 200 && postResponse2.getStatusCode() == 200) {
            return true;
        }
        return false;
    }

    /**
     * Create given number of computes with given endpointLinks and tenantLink.
     */
    private List<String> createComputes(int count, Set<String> endpointLinks, String tenantLink) {
        List<String> computeLinks = new ArrayList<>();

        ComputeState computeState = new ComputeState();
        computeState.descriptionLink = "description-link";
        computeState.id = UUID.randomUUID().toString();
        computeState.name = computeState.id;
        computeState.tenantLinks = Collections.singletonList(tenantLink);
        computeState.endpointLinks = endpointLinks;

        for (int i = 0; i < count; i++) {
            Operation op = Operation
                    .createPost(UriUtils.buildUri(this.host, ComputeService.FACTORY_LINK))
                    .setBody(computeState);

            Operation response = this.host.waitForResponse(op);

            if (response.getStatusCode() == Operation.STATUS_CODE_OK) {
                computeLinks.add(response.getBody(ComputeState.class).documentSelfLink);
            }
        }
        return computeLinks;
    }

    /**
     * Query count of computes with all given endpointLinks in a set and tenantLink.
     */
    public long getComputeCount(Set<String> endpointLinks, String tenantLink) {
        Query.Builder query = Query.Builder.create()
                .addCollectionItemClause(ResourceState.FIELD_NAME_TENANT_LINKS, tenantLink);

        for (String endpointLink : endpointLinks) {
            query.addCollectionItemClause(ResourceState.FIELD_NAME_ENDPOINT_LINKS, endpointLink);
        }

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .setQuery(query.build())
                .addOption(QueryOption.COUNT)
                .build();

        Operation postQuery = Operation
                .createPost(UriUtils.buildUri(this.host, ServiceUriPaths.CORE_LOCAL_QUERY_TASKS))
                .setBody(queryTask)
                .setReferer(this.host.getUri());

        Operation queryResponse = this.host.waitForResponse(postQuery);

        if (queryResponse.getStatusCode() != 200) {
            return -1;
        }

        QueryTask response = queryResponse.getBody(QueryTask.class);

        return response.results.documentCount;
    }

    /**
     * Executes the groomer task on TENANT_LINK_1 and waits for it to finish. Returns the selfLink
     * of the task after it finishes.
     */
    public String executeResourceGroomerTask() {
        EndpointResourceDeletionRequest state = new EndpointResourceDeletionRequest();
        state.tenantLinks = Collections.singleton(TENANT_LINK_1);
        state.documentSelfLink = UriUtils.buildUriPath(ResourceGroomerTaskService.FACTORY_LINK,
                UUID.randomUUID().toString());

        Operation postOp = Operation.createPost(UriUtils.buildUri(this.host, ResourceGroomerTaskService.FACTORY_LINK))
                .setBody(state)
                .setReferer(this.host.getUri());
        this.host.waitForResponse(postOp);
        this.host.waitForFinishedTask(EndpointResourceDeletionRequest.class, state.documentSelfLink);

        return state.documentSelfLink;
    }

    /**
     * Assert deletedDocumentCount and patchedDocumentCount stats of the groomer task that ran.
     */
    public void assertStats(double deletedDocumentCount, double patchedDocumentCount,
            String taskLink) {

        URI taskStatsUri = UriUtils.buildStatsUri(this.host, taskLink);
        Operation getStatsOp = Operation.createGet(taskStatsUri).setReferer(this.host.getUri());
        Operation response = this.host.waitForResponse(getStatsOp);
        ServiceStats stats = response.getBody(ServiceStats.class);

        assertEquals(deletedDocumentCount, stats.entries.get(ResourceGroomerTaskService
                .STAT_NAME_DOCUMENTS_DELETED).latestValue, 0);
        assertEquals(patchedDocumentCount, stats.entries.get(ResourceGroomerTaskService
                .STAT_NAME_ENDPOINT_LINKS_PATCHED).latestValue, 0);
    }
}
