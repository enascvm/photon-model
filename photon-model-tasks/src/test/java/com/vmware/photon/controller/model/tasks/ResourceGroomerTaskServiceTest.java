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
import java.util.Arrays;
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
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Tests to validate resource groomer task.
 */
public class ResourceGroomerTaskServiceTest extends BasicTestCase {
    public static final String VALID_ENDPOINT_LINK_1_TENANT_1 = "/resources/endpoints/valid-endpoint-1-tenant-1";
    public static final String VALID_ENDPOINT_LINK_2_TENANT_1 = "/resources/endpoints/valid-endpoint-2-tenant-1";
    public static final String VALID_ENDPOINT_LINK_1_TENANT_2 = "/resources/endpoints/valid-endpoint-1-tenant-2";
    public static final String VALID_ENDPOINT_LINK_2_TENANT_2 = "/resources/endpoints/valid-endpoint-2-tenant-2";
    public static final String DELETED_ENDPOINT_LINK_1_TENANT_1 = "/resources/endpoints/deleted-endpoint-1-tenant-1";
    public static final String DELETED_ENDPOINT_LINK_2_TENANT_1 = "/resources/endpoints/deleted-endpoint-2-tenant-1";
    public static final String DELETED_ENDPOINT_LINK_1_TENANT_2 = "/resources/endpoints/deleted-endpoint-1-tenant-2";
    public static final String TENANT_LINK_1 = "/tenants/project/valid-tenant-1";
    public static final String TENANT_LINK_2 = "/tenants/project/valid-tenant-2";

    public static final List<String> ENDPOINTS_TO_BE_CREATED = Arrays.asList(VALID_ENDPOINT_LINK_1_TENANT_1,
            VALID_ENDPOINT_LINK_2_TENANT_1, VALID_ENDPOINT_LINK_1_TENANT_2, VALID_ENDPOINT_LINK_2_TENANT_2,
            DELETED_ENDPOINT_LINK_1_TENANT_1, DELETED_ENDPOINT_LINK_1_TENANT_2);

    public static final List<String> ENDPOINTS_TO_BE_DELETED = Arrays.asList(DELETED_ENDPOINT_LINK_1_TENANT_1,
            DELETED_ENDPOINT_LINK_1_TENANT_2);

    @Before
    public void setUp() throws Throwable {
        // Lower the query result limit to test pagination with small number of resources.
        try {
            System.setProperty(QueryUtils.MAX_RESULT_LIMIT_PROPERTY, "10000");

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

    @Test
    public void testCleanupMultipleTenants() throws Throwable {
        if (!createDeleteEndpoints()) {
            return;
        }

        List<String> validComputeLinksTenant1 = createComputes(100,
                Collections.singleton(VALID_ENDPOINT_LINK_1_TENANT_1), TENANT_LINK_1, VALID_ENDPOINT_LINK_1_TENANT_1);
        List<String> validComputeLinksTenant2 = createComputes(100,
                Collections.singleton(VALID_ENDPOINT_LINK_1_TENANT_2), TENANT_LINK_2, VALID_ENDPOINT_LINK_1_TENANT_2);
        List<String> invalidComputeLinksTenant1 = createComputes(100,
                Collections.singleton(DELETED_ENDPOINT_LINK_1_TENANT_1), TENANT_LINK_1, DELETED_ENDPOINT_LINK_1_TENANT_1);
        List<String> invalidComputeLinksTenant2 = createComputes(100,
                Collections.singleton(DELETED_ENDPOINT_LINK_1_TENANT_2), TENANT_LINK_2, DELETED_ENDPOINT_LINK_1_TENANT_2);

        String taskLink = executeResourceGroomerTask(null);

        long computeCountValidEndpoint1Tenant1 = getComputeCount(Collections
                .singleton(VALID_ENDPOINT_LINK_1_TENANT_1), TENANT_LINK_1);
        long computeCountDeletedEndpoint1Tenant1 = getComputeCount(Collections
                .singleton(DELETED_ENDPOINT_LINK_1_TENANT_1), TENANT_LINK_1);
        long computeCountDeletedEndpoint2Tenant2 = getComputeCount(Collections
                .singleton(DELETED_ENDPOINT_LINK_1_TENANT_2), TENANT_LINK_2);
        long computeCountValidEndpoint2Tenant2 = getComputeCount(Collections
                .singleton(VALID_ENDPOINT_LINK_1_TENANT_2), TENANT_LINK_2);

        assertEquals(validComputeLinksTenant1.size(), computeCountValidEndpoint1Tenant1);
        assertEquals(0, computeCountDeletedEndpoint1Tenant1);
        assertEquals(validComputeLinksTenant2.size(), computeCountValidEndpoint2Tenant2);
        //assertEquals(invalidComputeLinksTenant2.size(), computeCountDeletedEndpoint2Tenant2);

        assertStats(100, 0, 0, taskLink);
    }

    @Test
    public void testCleanupPatchEndpointLinks() throws Throwable {
        if (!createDeleteEndpoints()) {
            return;
        }

        Set<String> endpointLinks = new HashSet<>();
        endpointLinks.add(VALID_ENDPOINT_LINK_1_TENANT_1);
        endpointLinks.add(DELETED_ENDPOINT_LINK_1_TENANT_1);

        List<String> validEndpointLinkComputeLinks = createComputes(100, endpointLinks, TENANT_LINK_1, VALID_ENDPOINT_LINK_1_TENANT_1);
        List<String> invalidComputeLinks = createComputes(100,
                Collections.singleton(DELETED_ENDPOINT_LINK_1_TENANT_1), TENANT_LINK_1, DELETED_ENDPOINT_LINK_1_TENANT_1);

        String taskLink = executeResourceGroomerTask(null);

        long computeCountValidEndpointLinks = getComputeCount(Collections.singleton(VALID_ENDPOINT_LINK_1_TENANT_1), TENANT_LINK_1);
        long computeCountValidEndpointLink = getComputeCount(VALID_ENDPOINT_LINK_1_TENANT_1, TENANT_LINK_1);
        long computeCountInvalidEndpointLinks = getComputeCount(Collections.singleton(DELETED_ENDPOINT_LINK_1_TENANT_1), TENANT_LINK_1);

        assertEquals(validEndpointLinkComputeLinks.size(), computeCountValidEndpointLinks);
        assertEquals(validEndpointLinkComputeLinks.size(), computeCountValidEndpointLink);
        assertEquals(0, computeCountInvalidEndpointLinks);

        assertStats(100, 100, 0, taskLink);
    }

    @Test
    public void testCleanupPatchEndpointLinksAndEndpointLink() throws Throwable {
        if (!createDeleteEndpoints()) {
            return;
        }

        Set<String> endpointLinks = new HashSet<>();
        endpointLinks.add(VALID_ENDPOINT_LINK_1_TENANT_1);
        endpointLinks.add(DELETED_ENDPOINT_LINK_1_TENANT_1);

        List<String> validEndpointLinkComputeLinks = createComputes(100, endpointLinks, TENANT_LINK_1, VALID_ENDPOINT_LINK_1_TENANT_1);
        List<String> invalidEndpointLinkComputeLinks = createComputes(100, endpointLinks, TENANT_LINK_1, DELETED_ENDPOINT_LINK_1_TENANT_1);
        List<String> invalidComputeLinks = createComputes(100,
                Collections.singleton(DELETED_ENDPOINT_LINK_1_TENANT_1), TENANT_LINK_1, DELETED_ENDPOINT_LINK_1_TENANT_1);

        String taskLink = executeResourceGroomerTask(null);

        long computeCountValidEndpointLinks = getComputeCount(Collections.singleton(VALID_ENDPOINT_LINK_1_TENANT_1), TENANT_LINK_1);
        long computeCountValidEndpointLink = getComputeCount(VALID_ENDPOINT_LINK_1_TENANT_1, TENANT_LINK_1);
        long computeCountInvalidEndpointLink = getComputeCount(DELETED_ENDPOINT_LINK_1_TENANT_1, TENANT_LINK_1);
        long computeCountInvalidEndpointLinks = getComputeCount(Collections.singleton(DELETED_ENDPOINT_LINK_1_TENANT_1), TENANT_LINK_1);

        assertEquals(validEndpointLinkComputeLinks.size() + invalidEndpointLinkComputeLinks.size(), computeCountValidEndpointLink);
        assertEquals(validEndpointLinkComputeLinks.size() + invalidEndpointLinkComputeLinks.size(), computeCountValidEndpointLinks);
        assertEquals(0, computeCountInvalidEndpointLink);
        assertEquals(0, computeCountInvalidEndpointLinks);

        assertStats(100, 200, 100, taskLink);
    }

    @Test
    public void testCleanupEndpointSpecifiedMultipleEndpoints() throws Throwable {
        if (!createDeleteEndpoints()) {
            return;
        }

        List<String> computeLinksEndpoint1 = createComputes(100, Collections.singleton(DELETED_ENDPOINT_LINK_1_TENANT_1),
                TENANT_LINK_1, DELETED_ENDPOINT_LINK_1_TENANT_1);
        List<String> computeLinksEndpoint2 = createComputes(100, Collections.singleton(DELETED_ENDPOINT_LINK_2_TENANT_1),
                TENANT_LINK_1, DELETED_ENDPOINT_LINK_2_TENANT_1);

        String taskLink = executeResourceGroomerTask(DELETED_ENDPOINT_LINK_1_TENANT_1);


        long computeCountEndpointBeingDeleted = getComputeCount(Collections.singleton(DELETED_ENDPOINT_LINK_1_TENANT_1), TENANT_LINK_1);
        long computeCountEndpointNotBeingDeleted = getComputeCount(Collections.singleton(DELETED_ENDPOINT_LINK_2_TENANT_1), TENANT_LINK_1);


        assertEquals(0, computeCountEndpointBeingDeleted);
        assertEquals(computeLinksEndpoint2.size(), computeCountEndpointNotBeingDeleted);

        assertStats(100, 0, 0, taskLink);
    }

    @Test
    public void testCleanupEndpointSpecifiedPatchEndpointLinksAndEndpointLink() throws Throwable {
        if (!createDeleteEndpoints()) {
            return;
        }

        Set<String> endpointLinks = new HashSet<>();
        endpointLinks.add(DELETED_ENDPOINT_LINK_1_TENANT_1);
        endpointLinks.add(DELETED_ENDPOINT_LINK_2_TENANT_1);

        List<String> computeLinksEndpoint1 = createComputes(100, endpointLinks,
                TENANT_LINK_1, DELETED_ENDPOINT_LINK_1_TENANT_1);
        List<String> computeLinksEndpoint2 = createComputes(100, Collections.singleton(DELETED_ENDPOINT_LINK_2_TENANT_1),
                TENANT_LINK_1, DELETED_ENDPOINT_LINK_2_TENANT_1);

        String taskLink = executeResourceGroomerTask(DELETED_ENDPOINT_LINK_1_TENANT_1);

        long computeCountEndpointLinksNotBeingDeleted = getComputeCount(Collections.singleton(DELETED_ENDPOINT_LINK_2_TENANT_1), TENANT_LINK_1);
        long computeCountEndpointLinkNotBeingDeleted = getComputeCount(DELETED_ENDPOINT_LINK_2_TENANT_1, TENANT_LINK_1);
        long computeCountEndpointLinksBeingDeleted = getComputeCount(Collections.singleton(DELETED_ENDPOINT_LINK_1_TENANT_1), TENANT_LINK_1);
        long computeCountEndpointLinkBeingDeleted = getComputeCount(DELETED_ENDPOINT_LINK_1_TENANT_1, TENANT_LINK_1);

        assertEquals(200, computeCountEndpointLinksNotBeingDeleted);
        assertEquals(200, computeCountEndpointLinkNotBeingDeleted);
        assertEquals(0, computeCountEndpointLinkBeingDeleted);
        assertEquals(0, computeCountEndpointLinksBeingDeleted);

        assertStats(0, 100, 100, taskLink);
    }

    @Test
    public void testCleanupEndpointSpecifiedPatchEndpointLinks() throws Throwable {
        if (!createDeleteEndpoints()) {
            return;
        }

        Set<String> endpointLinks = new HashSet<>();
        endpointLinks.add(DELETED_ENDPOINT_LINK_1_TENANT_1);
        endpointLinks.add(DELETED_ENDPOINT_LINK_2_TENANT_1);

        List<String> computeLinksEndpoint1 = createComputes(100, endpointLinks,
                TENANT_LINK_1, DELETED_ENDPOINT_LINK_2_TENANT_1);
        List<String> computeLinksEndpoint2 = createComputes(100, Collections.singleton(DELETED_ENDPOINT_LINK_2_TENANT_1),
                TENANT_LINK_1, DELETED_ENDPOINT_LINK_2_TENANT_1);

        String taskLink = executeResourceGroomerTask(DELETED_ENDPOINT_LINK_1_TENANT_1);

        long computeCountEndpointLinksNotBeingDeleted = getComputeCount(Collections.singleton(DELETED_ENDPOINT_LINK_2_TENANT_1), TENANT_LINK_1);
        long computeCountEndpointLinkNotBeingDeleted = getComputeCount(DELETED_ENDPOINT_LINK_2_TENANT_1, TENANT_LINK_1);
        long computeCountEndpointLinksBeingDeleted = getComputeCount(Collections.singleton(DELETED_ENDPOINT_LINK_1_TENANT_1), TENANT_LINK_1);
        long computeCountEndpointLinkBeingDeleted = getComputeCount(DELETED_ENDPOINT_LINK_1_TENANT_1, TENANT_LINK_1);

        assertEquals(200, computeCountEndpointLinksNotBeingDeleted);
        assertEquals(200, computeCountEndpointLinkNotBeingDeleted);
        assertEquals(0, computeCountEndpointLinkBeingDeleted);
        assertEquals(0, computeCountEndpointLinksBeingDeleted);

        assertStats(0, 100, 0, taskLink);
    }

    @Test
    public void testValidEndpointCleanup() throws Throwable {
        if (!createDeleteEndpoints()) {
            return;
        }

        EndpointResourceDeletionRequest state = new EndpointResourceDeletionRequest();
        state.documentSelfLink = UriUtils.buildUriPath(ResourceGroomerTaskService.FACTORY_LINK,
                UUID.randomUUID().toString());
        state.tenantLinks = Collections.singleton(TENANT_LINK_1);
        state.endpointLink = VALID_ENDPOINT_LINK_1_TENANT_1;
        state.taskInfo = TaskState.createDirect();

        Operation postOp = Operation.createPost(UriUtils.buildUri(this.host, ResourceGroomerTaskService.FACTORY_LINK))
                .setBody(state)
                .setReferer(this.host.getUri());

        Operation postResponse = this.host.waitForResponse(postOp);
        EndpointResourceDeletionRequest response = postResponse.getBody(EndpointResourceDeletionRequest.class);

        assertEquals(200, postResponse.getStatusCode());
        assertEquals(response.taskInfo.stage, TaskStage.FAILED);
        assertEquals(response.failureMessage, "Deletion/Disassociation of documents for valid "
                + "endpoints is not supported.");
    }

    @Test
    public void testMalformedEndpointCleanup() throws Throwable {
        EndpointResourceDeletionRequest state = new EndpointResourceDeletionRequest();
        state.documentSelfLink = UriUtils.buildUriPath(ResourceGroomerTaskService.FACTORY_LINK,
                UUID.randomUUID().toString());
        state.tenantLinks = Collections.singleton(TENANT_LINK_1);
        state.endpointLink = "some-random-link";
        state.taskInfo = TaskState.createDirect();

        Operation postOp = Operation.createPost(UriUtils.buildUri(this.host, ResourceGroomerTaskService.FACTORY_LINK))
                .setBody(state)
                .setReferer(this.host.getUri());

        Operation postResponse = this.host.waitForResponse(postOp);

        assertEquals(400, postResponse.getStatusCode());
    }

    @Test
    public void testNoTenantSpecified() throws Throwable {
        if (!createDeleteEndpoints()) {
            return;
        }

        EndpointResourceDeletionRequest state = new EndpointResourceDeletionRequest();
        state.documentSelfLink = UriUtils.buildUriPath(ResourceGroomerTaskService.FACTORY_LINK,
                UUID.randomUUID().toString());
        state.taskInfo = TaskState.createDirect();

        Operation postOp = Operation.createPost(UriUtils.buildUri(this.host, ResourceGroomerTaskService.FACTORY_LINK))
                .setBody(state)
                .setReferer(this.host.getUri());

        Operation postResponse = this.host.waitForResponse(postOp);

        assertEquals(400, postResponse.getStatusCode());
    }

    /**
     * Create and delete endpoint documents.
     */
    private boolean createDeleteEndpoints() {
        EndpointState state = new EndpointState();
        state.endpointType = "Amazon Web Services";
        state.id = UUID.randomUUID().toString();
        state.name = state.id;

        for (String endpointLink : ENDPOINTS_TO_BE_CREATED) {
            state.documentSelfLink = endpointLink;
            if (endpointLink.contains("tenant-1")) {
                state.tenantLinks = Collections.singletonList(TENANT_LINK_1);
            } else {
                state.tenantLinks = Collections.singletonList(TENANT_LINK_2);
            }

            Operation postEp = Operation
                    .createPost(this.host, EndpointService.FACTORY_LINK)
                    .setBody(state)
                    .setReferer(this.host.getUri());

            Operation postResponse = this.host.waitForResponse(postEp);

            if (postResponse.getStatusCode() != 200) {
                return false;
            }
        }

        for (String endpointLink : ENDPOINTS_TO_BE_DELETED) {
            Operation deleteEp = Operation
                    .createDelete(this.host, endpointLink)
                    .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FROM_MIGRATION_TASK)
                    .setReferer(this.host.getUri());

            Operation deleteResponse = this.host.waitForResponse(deleteEp);

            if (deleteResponse.getStatusCode() != 200) {
                return false;
            }
        }

        return true;
    }

    /**
     * Create given number of computes with given endpointLinks and tenantLink.
     */
    private List<String> createComputes(int count, Set<String> endpointLinks, String tenantLink,
            String endpointLink) {
        List<String> computeLinks = new ArrayList<>();

        ComputeState computeState = new ComputeState();
        computeState.descriptionLink = "description-link";
        computeState.id = UUID.randomUUID().toString();
        computeState.name = computeState.id;
        computeState.tenantLinks = Collections.singletonList(tenantLink);
        computeState.endpointLinks = endpointLinks;
        computeState.endpointLink = endpointLink;

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
     * Query count of computes with all given endpointLinks in a set and tenantLink.
     */
    public long getComputeCount(String endpointLink, String tenantLink) {
        Query.Builder query = Query.Builder.create()
                .addFieldClause(ResourceState.FIELD_NAME_ENDPOINT_LINK, endpointLink)
                .addCollectionItemClause(ResourceState.FIELD_NAME_TENANT_LINKS, tenantLink);

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
    public String executeResourceGroomerTask(String endpointLink) {
        EndpointResourceDeletionRequest state = new EndpointResourceDeletionRequest();
        state.tenantLinks = Collections.singleton(TENANT_LINK_1);
        state.documentSelfLink = UriUtils.buildUriPath(ResourceGroomerTaskService.FACTORY_LINK,
                UUID.randomUUID().toString());
        state.endpointLink = endpointLink;

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
    public void assertStats(double deletedDocumentCount, double endpointLinksPatchedCount,
            double endpointLinkPatchedCount, String taskLink) {

        URI taskStatsUri = UriUtils.buildStatsUri(this.host, taskLink);
        Operation getStatsOp = Operation.createGet(taskStatsUri).setReferer(this.host.getUri());
        Operation response = this.host.waitForResponse(getStatsOp);
        ServiceStats stats = response.getBody(ServiceStats.class);

        assertEquals(deletedDocumentCount, stats.entries.get(ResourceGroomerTaskService
                .STAT_NAME_DOCUMENTS_DELETED).latestValue, 0);
        assertEquals(endpointLinksPatchedCount, stats.entries.get(ResourceGroomerTaskService
                .STAT_NAME_ENDPOINT_LINKS_PATCHED).latestValue, 0);
        assertEquals(endpointLinkPatchedCount, stats.entries.get(ResourceGroomerTaskService
                .STAT_NAME_ENDPOINT_LINK_PATCHED).latestValue, 0);
    }
}