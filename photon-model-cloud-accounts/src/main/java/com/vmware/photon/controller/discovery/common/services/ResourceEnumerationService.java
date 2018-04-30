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

package com.vmware.photon.controller.discovery.common.services;

import static com.vmware.photon.controller.discovery.common.utils.InventoryQueryUtils.getInventoryQueryPage;
import static com.vmware.photon.controller.discovery.common.utils.InventoryQueryUtils.startInventoryQueryTask;

import java.net.URI;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import com.vmware.photon.controller.discovery.common.PhotonControllerCloudAccountUtils;
import com.vmware.photon.controller.discovery.notification.NotificationUtils;
import com.vmware.photon.controller.discovery.notification.event.EnumerationCompleteEvent;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService.ResourceEnumerationTaskState;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;

/**
 * Service to do enumeration for given resource pool
 */
public class ResourceEnumerationService extends StatelessService {
    public static final String SELF_LINK = UriPaths.RESOURCE_ENUMERATION_SERVICE;

    public static final String PROPERTY_NAME_ENUM_QUERY_RESULT_LIMIT = UriPaths.SYMPHONY_PROPERTY_PREFIX
            + "ResourceEnumerationService.QUERY_RESULT_LIMIT";
    private static final int DEFAULT_QUERY_RESULT_LIMIT = 100;
    private static final int QUERY_RESULT_LIMIT =
            Integer.getInteger(PROPERTY_NAME_ENUM_QUERY_RESULT_LIMIT, DEFAULT_QUERY_RESULT_LIMIT);
    public static final String PROPERTY_NAME_NEXT_ENUMERATION_INTERVAL_SECS = UriPaths.SYMPHONY_PROPERTY_PREFIX
            + "ResourceEnumerationService.NEXT_ENUMERATION_INTERVAL_SECS";
    private static final int DEFAULT_NEXT_ENUMERATION_INTERVAL_SECS = 15;
    private static final int NEXT_ENUMERATION_INTERVAL_SECS = Integer.getInteger(
            PROPERTY_NAME_NEXT_ENUMERATION_INTERVAL_SECS, DEFAULT_NEXT_ENUMERATION_INTERVAL_SECS);

    /**
     * Enumeration request.
     */
    public static class ResourceEnumerationRequest {
        public ResourcePoolState resourcePoolState;
    }

    @Override
    public void handlePost(Operation post) {
        ResourceEnumerationRequest request = post.getBody(ResourceEnumerationRequest.class);
        validateRequest(request);
        post.complete(); // don't wait for processing all pages

        Query query = Query.Builder.create()
                .addKindFieldClause(EndpointState.class)
                .addInCollectionItemClause(EndpointState.FIELD_NAME_TENANT_LINKS,
                        request.resourcePoolState.tenantLinks)
                .build();

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .addOption(QueryOption.INDEXED_METADATA)
                .setQuery(query)
                .setResultLimit(QUERY_RESULT_LIMIT)
                .build();
        queryTask.tenantLinks = request.resourcePoolState.tenantLinks;

        startInventoryQueryTask(this, queryTask)
                .whenComplete((qt, ex) -> {
                    if (ex != null) {
                        logWarning("Unable to get endpoint for: %s",
                                request.resourcePoolState.documentSelfLink);
                        return;
                    }

                    if (qt.results == null || qt.results.nextPageLink == null) {
                        logInfo("No endpoint for enumeration under: %s",
                                request.resourcePoolState.documentSelfLink);
                        return;
                    }
                    String nextPageLink = qt.results.nextPageLink;
                    processNextPage(nextPageLink);
                });
    }

    private void processNextPage(String nextPage) {
        if (nextPage == null) {
            logInfo("Successfully finished enumeration");
            return;
        }

        getInventoryQueryPage(this, nextPage)
                .whenComplete((nextPageResponse, e) -> {
                    if (e != null) {
                        logWarning("Unable to get endpoint for page: %s", nextPage);
                        return;
                    }

                    if (nextPageResponse.results == null) {
                        logWarning("nextPageResponse.results are null");
                        return;
                    }

                    if (nextPageResponse.results.documents == null) {
                        logWarning("nextPageResponse.results.documents are null");
                        return;
                    }

                    Iterator<Object> iterator = nextPageResponse.results.documents.values().iterator();
                    String nextPageLink = nextPageResponse.results.nextPageLink;
                    getComputeHost(iterator, nextPageLink);
                });
    }

    private void validateRequest(ResourceEnumerationRequest request) {
        if (request.resourcePoolState == null) {
            throw new IllegalArgumentException("resourcePoolState is required");
        }

        if (request.resourcePoolState.tenantLinks == null ||
                request.resourcePoolState.tenantLinks.isEmpty()) {
            throw new IllegalArgumentException("resourcePoolState.tenantLinks is required");
        }
    }

    /**
     * Retrieves the compute host.
     */
    private void getComputeHost(Iterator<Object> endpointIterator, String nextPage) {
        if (!endpointIterator.hasNext()) {
            // all items from current page have been processed
            processNextPage(nextPage);
            return;
        }

        // process the next endpoint from current page
        EndpointState endpoint = Utils.fromJson(endpointIterator.next(), EndpointState.class);
        URI uri = ComputeStateWithDescription
                .buildUri(UriUtils.buildUri(getHost(), endpoint.computeLink));
        Operation get = Operation.createGet(uri);
        sendWithDeferredResult(get, ComputeStateWithDescription.class)
                .whenComplete((computeHostWithDesc, e) -> {
                    if (e != null) {
                        logWarning("Error while retrieving compute host for " +
                                "endpoint %s: %s", endpoint.documentSelfLink, e.getMessage());
                        getComputeHost(endpointIterator, nextPage);
                        return;
                    }

                    if (computeHostWithDesc == null ||
                            computeHostWithDesc.adapterManagementReference == null) {
                        // don't enumerate
                        logWarning("computeHostWithDesc.adapterManagementReference" +
                                        " was null for endpoint %s, skipping enumeration",
                                endpoint.documentSelfLink);
                        getComputeHost(endpointIterator, nextPage);
                        return;
                    }

                    enumerate(endpointIterator, nextPage, computeHostWithDesc, endpoint);
                });
    }

    /**
     * Kick off the enumeration for given endpoint.
     */
    private void enumerate(Iterator<Object> endpointIterator,
            String nextPage,
            ComputeStateWithDescription computeHostWithDesc,
            EndpointState endpoint) {
        // use the id based on computeLink to catch concurrent enumerations.
        String id = UriUtils.getLastPathSegment(endpoint.computeLink);
        ResourceEnumerationTaskState enumTaskState = new ResourceEnumerationTaskState();
        enumTaskState.parentComputeLink = endpoint.computeLink;
        enumTaskState.resourcePoolLink = endpoint.resourcePoolLink;
        enumTaskState.endpointLink = endpoint.documentSelfLink;
        enumTaskState.adapterManagementReference = computeHostWithDesc.adapterManagementReference;
        enumTaskState.tenantLinks = endpoint.tenantLinks;
        enumTaskState.documentSelfLink = id;
        enumTaskState.taskInfo = TaskState.createDirect();
        enumTaskState.options = EnumSet.of(TaskOption.SELF_DELETE_ON_COMPLETION);

        Operation operation = Operation
                .createPost(this, ResourceEnumerationTaskService.FACTORY_LINK)
                .setBody(enumTaskState)
                .setCompletion((op, ex) -> {

                    boolean notify = true;
                    EnumerationCompleteEvent.EnumerationStatus enumerationStatus =
                            EnumerationCompleteEvent.EnumerationStatus.FINISHED;

                    if (op != null && op.getStatusCode() == Operation.STATUS_CODE_CONFLICT) {
                        logInfo("Enumeration already running for endpoint: %s",
                                endpoint.documentSelfLink);
                        notify = false;
                    } else if (ex != null) {
                        logWarning("There was error in enumeration for endpoint %s : %s",
                                endpoint.documentSelfLink, ex.getMessage());
                        enumerationStatus = EnumerationCompleteEvent.EnumerationStatus.FAILED;
                    } else {
                        logInfo("Successfully enumerated resources for endpoint: %s",
                                endpoint.documentSelfLink);
                    }

                    if (notify) {
                        // send enumeration complete event
                        EnumerationCompleteEvent changeEvent =
                                new EnumerationCompleteEvent(
                                        NotificationUtils.getCloudAccountLink(enumTaskState.endpointLink),
                                        enumerationStatus);
                        NotificationUtils.sendNotification(this, getUri(), changeEvent,
                                PhotonControllerCloudAccountUtils.getOrgId(endpoint.tenantLinks));
                    }
                    // get next compute host
                    getHost().schedule(() -> {
                        getComputeHost(endpointIterator, nextPage);
                    }, NEXT_ENUMERATION_INTERVAL_SECS, TimeUnit.SECONDS);
                });

        logInfo("Creating enumeration task for endpoint : %s", endpoint.documentSelfLink);
        sendRequest(operation);
    }
}
