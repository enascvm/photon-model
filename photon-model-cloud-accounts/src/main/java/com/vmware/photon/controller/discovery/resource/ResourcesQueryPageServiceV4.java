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

package com.vmware.photon.controller.discovery.resource;

import static com.vmware.photon.controller.discovery.common.utils.InventoryQueryUtils.getInventoryQueryPage;
import static com.vmware.photon.controller.model.UriPaths.RESOURCE_QUERY_PAGE_SERVICE_V4;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType.vsphere;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.TAG_KEY_TYPE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.vmware.photon.controller.discovery.cloudaccount.utils.QueryHelper;
import com.vmware.photon.controller.discovery.common.utils.StringUtil;
import com.vmware.photon.controller.discovery.resource.ResourcesApiService.ResourceViewState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;

public class ResourcesQueryPageServiceV4 extends StatelessService {
    public static final String SELF_LINK_PREFIX = RESOURCE_QUERY_PAGE_SERVICE_V4;
    public static final String UNKNOWN_RESOURCE_TYPE = "UNKNOWN";
    public static final String COLON_CHAR = ":";
    public static final String V_SPHERE_ON_PREM = "vsphere-on-prem";
    public static final String RESOURCE_VIEW_DEFAULT_VALUE = "";

    // The next/prev page link associated with the query task.
    public final String pageLink;

    // The time after which the stateless service expires.
    public final long expirationTimeMicros;

    // The tenant links context.
    public final List<String> tenantLinks;

    // Stages for processing the resource results page.
    public enum Stages {
        // Stage to get resource from ResourceQueryTask results.
        GET_RESOURCES,

        // Stage to build the {@link ResourcesApiService.ResourceViewState} PODO.
        BUILD_PODO,

        // Stage to build result.
        BUILD_RESULT,

        // Stage to indicate success.
        SUCCESS
    }

    // Local context object to pass around during callbacks.
    public static class Context {
        public Stages stage;
        public Operation op;
        public String nextPageLink;
        public String prevPageLink;
        public ServiceDocumentQueryResult results;
        public Long documentCount;
        public Throwable error;

        // Raw photo-model object for all resource types we are collecting
        public List<ServiceDocument> resources;

        // Map of expanded documents <documentSelfLink, ServiceDocument>
        public Map<String, ServiceDocument> associatedDocuments;

        // API-friendly model of a resource.
        public List<ResourceViewState> resourceViewStates;

        public Context() {
            this.stage = Stages.GET_RESOURCES;
        }
    }

    public ResourcesQueryPageServiceV4(String pageLink, long expMicros, List<String> tenantLinks) {
        this.pageLink = pageLink;
        this.expirationTimeMicros = expMicros;
        this.tenantLinks = tenantLinks;
    }

    @Override
    public void handleStart(Operation post) {
        ServiceDocument initState = post.getBody(ServiceDocument.class);

        long interval = initState.documentExpirationTimeMicros - Utils.getNowMicrosUtc();
        if (interval <= 0) {
            logWarning("Task expiration is in the past, extending it");
            interval = TimeUnit.SECONDS.toMicros(getHost().getMaintenanceIntervalMicros() * 2);
        }

        super.toggleOption(ServiceOption.PERIODIC_MAINTENANCE, true);
        super.setMaintenanceIntervalMicros(interval);

        post.complete();
    }

    @Override
    public void handleMaintenance(Operation op) {
        op.complete();
        getHost().stopService(this);
    }

    @Override
    public void handleGet(Operation op) {
        Context ctx = new Context();
        ctx.op = op;
        try {
            handleStages(ctx);
        } catch (Throwable e) {
            ctx.error = e;
            handleError(ctx);
        }
    }

    private void handleStages(Context ctx) throws Throwable {
        logFine("handleStages: %s", ctx.stage);
        switch (ctx.stage) {
        case GET_RESOURCES:
            getResources(ctx, Stages.BUILD_PODO);
            break;
        case BUILD_PODO:
            buildPODO(ctx, Stages.BUILD_RESULT);
            break;
        case BUILD_RESULT:
            buildResult(ctx, Stages.SUCCESS);
            break;
        case SUCCESS:
            handleSuccess(ctx);
            break;
        default:
            throw new IllegalStateException("Unknown stage encountered: " + ctx.stage);
        }
    }

    /**
     * Gets results for the given page link.
     */
    private void getResources(Context ctx, Stages nextStage) throws Throwable {
        getInventoryQueryPage(this, this.pageLink)
                .whenComplete((body, e) -> {
                    if (e != null) {
                        ctx.error = e;
                        handleError(ctx);
                        return;
                    }

                    ServiceDocumentQueryResult results = body.results;
                    ctx.documentCount = results.documentCount;
                    if (results.documentLinks.size() == 0) {
                        ctx.results = new ServiceDocumentQueryResult();
                        ctx.results.documentCount = ctx.documentCount;
                        ctx.stage = Stages.SUCCESS;
                        handleSuccess(ctx);
                        return;
                    }

                    ctx.stage = nextStage;
                    try {
                        extractQueryResults(ctx, results);
                    } catch (Throwable t) {
                        ctx.error = t;
                        handleError(ctx);
                        return;
                    }
                });
    }

    /**
     * Extract the query results and start page services as needed.
     */
    private void extractQueryResults(Context ctx, ServiceDocumentQueryResult results) throws Throwable {
        ctx.resources = new ArrayList<>();
        ctx.associatedDocuments = new LinkedHashMap<>();
        // get all core resources. Since the documentLinks have the results in the sorted order;
        // iterating over the documentLinks to preserve the sorted order
        for (String documentLink : results.documentLinks) {
            ServiceDocument document = Utils
                    .fromJson(results.documents.get(documentLink),
                            ServiceDocument.class);
            String documentKind = document.documentKind;

            if (documentKind.equals(Utils.buildKind(ComputeState.class))) {
                ComputeState compute = Utils.fromJson(results.documents.get(documentLink),
                        ComputeState.class);
                if (compute.endpointLinks != null && !compute.endpointLinks.isEmpty()) {
                    compute.endpointLinks.forEach(endpointLink -> ctx.associatedDocuments
                            .put(endpointLink, null));
                }
                ctx.resources.add(compute);
            } else if (documentKind.equals(Utils.buildKind(DiskState.class))) {
                DiskState disk = Utils.fromJson(results.documents.get(documentLink),
                        DiskState.class);
                if (disk.endpointLinks != null && !disk.endpointLinks.isEmpty()) {
                    disk.endpointLinks.forEach(endpointLink -> ctx.associatedDocuments
                            .put(endpointLink, null));
                }
                ctx.resources.add(disk);
            } else if (documentKind.equals(Utils.buildKind(NetworkInterfaceState.class))) {
                NetworkInterfaceState nic = Utils.fromJson(results.documents.get(documentLink),
                        NetworkInterfaceState.class);
                if (nic.endpointLinks != null && !nic.endpointLinks.isEmpty()) {
                    nic.endpointLinks.forEach(endpointLink -> ctx.associatedDocuments
                            .put(endpointLink, null));
                }
                ctx.resources.add(nic);
            } else if (documentKind.equals(Utils.buildKind(NetworkState.class))) {
                NetworkState network = Utils.fromJson(results.documents.get(documentLink),
                        NetworkState.class);
                if (network.endpointLinks != null && !network.endpointLinks.isEmpty()) {
                    network.endpointLinks.forEach(endpointLink -> ctx.associatedDocuments
                            .put(endpointLink, null));
                }
                ctx.resources.add(network);
            } else if (documentKind.equals(Utils.buildKind(SecurityGroupState.class))) {
                SecurityGroupState securityGroup = Utils.fromJson(results.documents.get(documentLink),
                        SecurityGroupState.class);
                if (securityGroup.endpointLinks != null && !securityGroup.endpointLinks.isEmpty()) {
                    ctx.associatedDocuments.put(securityGroup.endpointLink, null);
                }
                ctx.resources.add(securityGroup);
            } else if (documentKind.equals(Utils.buildKind(SubnetState.class))) {
                SubnetState subnet = Utils.fromJson(results.documents.get(documentLink),
                        SubnetState.class);
                if (subnet.endpointLinks != null && !subnet.endpointLinks.isEmpty()) {
                    subnet.endpointLinks.forEach(endpointLink -> ctx.associatedDocuments
                            .put(endpointLink, null));
                }
                ctx.resources.add(subnet);
            } else {
                this.logWarning("Unknown documentKind: " + documentKind);
                continue;
            }
        }

        // get all associated documents
        for (Map.Entry<String, Object> entry : results.selectedDocuments.entrySet()) {
            // if entry is ServiceErrorResponse then the selected document does not exist in
            // the index (temporary fix - fix is pending from xenon)
            // https://www.pivotaltracker.com/story/show/145620427
            if (entry.getValue() instanceof ServiceErrorResponse) {
                ServiceErrorResponse errorResponse = Utils.fromJson(entry.getValue(),
                        ServiceErrorResponse.class);

                this.logWarning(String.format("Failed collecting document %s with error: %s",
                        entry.getKey().toString(), errorResponse.message));
                continue;
            }

            String documentKind = Utils
                    .fromJson(entry.getValue(), ServiceDocument.class).documentKind;
            if (documentKind.equals(Utils.buildKind(EndpointState.class))) {
                EndpointState endpoint = Utils.fromJson(entry.getValue(), EndpointState.class);
                ctx.associatedDocuments.put(entry.getKey(), endpoint);
            } else if (documentKind.equals(Utils.buildKind(TagState.class))) {
                TagState tag = Utils.fromJson(entry.getValue(), TagState.class);
                ctx.associatedDocuments.put(entry.getKey(), tag);
            } else if (documentKind.equals(Utils.buildKind(SubnetState.class))) {
                SubnetState subnet = Utils.fromJson(entry.getValue(), SubnetState.class);
                ctx.associatedDocuments.put(entry.getKey(), subnet);
            } else {
                this.logWarning("Unknown documentKind: " + documentKind);
                continue;
            }
        }

        // populate nextPageLink and prevPageLink
        if (!StringUtil.isEmpty(results.nextPageLink)) {
            ResourcesQueryPageServiceV4 pageService = new ResourcesQueryPageServiceV4(
                    results.nextPageLink, this.expirationTimeMicros, this.tenantLinks);

            ctx.nextPageLink = QueryHelper
                    .startStatelessPageService(this, SELF_LINK_PREFIX,
                            pageService,
                            this.expirationTimeMicros,
                            failure -> {
                                ctx.error = failure;
                                handleError(ctx);
                            });
        }

        if (!StringUtil.isEmpty(results.prevPageLink)) {
            ResourcesQueryPageServiceV4 pageService = new ResourcesQueryPageServiceV4(
                    results.prevPageLink, this.expirationTimeMicros, this.tenantLinks);

            ctx.prevPageLink = QueryHelper
                    .startStatelessPageService(this, SELF_LINK_PREFIX,
                            pageService,
                            this.expirationTimeMicros,
                            failure -> {
                                ctx.error = failure;
                                handleError(ctx);
                            });
        }

        handleStages(ctx);
    }

    /**
     * Build {@link ResourceViewState} PODOs.
     */
    public void buildPODO(Context ctx, Stages nextStage) throws Throwable {
        List<ResourceViewState> resourceViewStates = new ArrayList<>();
        for (int i = 0; i < ctx.resources.size(); i++) {
            ResourceViewState resourceViewState = new ResourceViewState();
            resourceViewState.documentSelfLink = ctx.resources.get(i).documentSelfLink;

            // each resource type has different properties - map them according to documentKind
            String category = stripCategoryString(ctx.resources.get(i).documentKind);
            if (category.equals(stripCategoryString(Utils.buildKind(ComputeState.class)))) {
                ComputeState compute = (ComputeState) ctx.resources.get(i);
                long creationTimeMicros = (compute.creationTimeMicros != null) ? compute.creationTimeMicros : 0;
                compute.address = compute.address == null ?
                        RESOURCE_VIEW_DEFAULT_VALUE : compute.address;
                compute.instanceType = compute.instanceType == null ?
                        RESOURCE_VIEW_DEFAULT_VALUE : compute.instanceType;
                populateResourceView(ctx, resourceViewState, compute.id, compute.name,
                        category, compute.address, creationTimeMicros,
                        compute.documentUpdateTimeMicros, compute.endpointLink,
                        compute.endpointLinks, compute.regionId, compute.tagLinks,
                        compute.instanceType);
            } else if (category.equals(stripCategoryString(Utils.buildKind(DiskState.class)))) {
                DiskState disk = (DiskState) ctx.resources.get(i);
                long creationTimeMicros = (disk.creationTimeMicros != null) ? disk.creationTimeMicros : 0;
                populateResourceView(ctx, resourceViewState, disk.id, disk.name,
                        category, null, creationTimeMicros, disk.documentUpdateTimeMicros,
                        disk.endpointLink, disk.endpointLinks, disk.regionId, disk.tagLinks, null);
            } else if (category.equals(stripCategoryString(Utils.buildKind(NetworkInterfaceState.class)))) {
                NetworkInterfaceState nic = (NetworkInterfaceState) ctx.resources.get(i);
                nic.address = nic.address == null ? RESOURCE_VIEW_DEFAULT_VALUE : nic.address;
                populateResourceView(ctx, resourceViewState, nic.id, nic.name,
                        stripCategoryString(Utils.buildKind(NetworkState.class)),
                        nic.address, 0, nic.documentUpdateTimeMicros, nic.endpointLink,
                        nic.endpointLinks, nic.regionId, nic.tagLinks, null);
            } else if (category.equals(stripCategoryString(Utils.buildKind(NetworkState.class)))) {
                NetworkState network = (NetworkState) ctx.resources.get(i);
                populateResourceView(ctx, resourceViewState, network.id, network.name,
                        stripCategoryString(Utils.buildKind(NetworkState.class)),
                        null, 0, network.documentUpdateTimeMicros, network.endpointLink,
                        network.endpointLinks, network.regionId, network.tagLinks, null);
            } else if (category.equals(stripCategoryString(Utils.buildKind(SecurityGroupState.class)))) {
                SecurityGroupState securityGroup = (SecurityGroupState) ctx.resources.get(i);
                populateResourceView(ctx, resourceViewState, securityGroup.id, securityGroup.name,
                        stripCategoryString(Utils.buildKind(NetworkState.class)),
                        null, 0, securityGroup.documentUpdateTimeMicros, securityGroup.endpointLink,
                        securityGroup.endpointLinks, securityGroup.regionId, securityGroup.tagLinks,
                        null);
            } else if (category.equals(stripCategoryString(Utils.buildKind(SubnetState.class)))) {
                SubnetState subnet = (SubnetState) ctx.resources.get(i);
                populateResourceView(ctx, resourceViewState, subnet.id, subnet.name,
                        stripCategoryString(Utils.buildKind(NetworkState.class)),
                        null, 0, subnet.documentUpdateTimeMicros, subnet.endpointLink,
                        subnet.endpointLinks, subnet.regionId, subnet.tagLinks, null);
            } else {
                this.logWarning("Unknown documentKind: " + ctx.resources.get(i).documentKind);
                continue;
            }

            resourceViewStates.add(resourceViewState);
        }

        ctx.resourceViewStates = resourceViewStates;
        ctx.stage = nextStage;
        handleStages(ctx);
    }

    private void populateResourceView(Context ctx, ResourceViewState resourceView, String id,
            String name, String category, String address, long creationTime,
            long updateTime, String endpointLink, Set<String> endpointLinks, String regionId,
            Set<String> tagLinks, String instanceType) {
        resourceView.id = id;
        resourceView.name = name == null ? RESOURCE_VIEW_DEFAULT_VALUE : name;
        resourceView.category = category;
        resourceView.address = address;
        resourceView.creationTimeMicros = creationTime;
        resourceView.lastUpdatedTimeMicros = updateTime;
        resourceView.regionId = regionId;
        resourceView.instanceType = instanceType;
        // set cloudAccountView
        if (endpointLink != null && ctx.associatedDocuments.containsKey(endpointLink)) {
            setCloudAccountView(ctx, resourceView, endpointLink);
        }

        if (endpointLinks != null && !endpointLinks.isEmpty()) {
            for (String endpointSelfLink : endpointLinks) {
                if (endpointSelfLink != null && !endpointLink.equals(endpointSelfLink) &&
                        ctx.associatedDocuments.containsKey(endpointSelfLink)) {
                    setCloudAccountView(ctx, resourceView, endpointSelfLink);
                }
            }
        }

        // set type and tags
        if (tagLinks != null && tagLinks.size() > 0) {
            // we currently have 1 internal tag only for type
            resourceView.type = setType(ctx, tagLinks);
            resourceView.tags = getTagNames(ctx, tagLinks);
        } else {
            resourceView.type = UNKNOWN_RESOURCE_TYPE;
        }
    }

    private void setCloudAccountView(Context ctx, ResourceViewState resourceView, String endpointLink) {
        // we assume there is only one endpoint link
        EndpointState endpoint = (EndpointState) ctx.associatedDocuments.get(endpointLink);
        if (endpoint != null) {
            // transform the endpoint type to be consistent with what the resourceCountSummary API
            // provides as input
            resourceView.cloudType = (endpoint.endpointType.equals(V_SPHERE_ON_PREM)) ?
                    vsphere.name() : endpoint.endpointType;
            resourceView.cloudAccounts.add(endpoint.name);
        }
    }

    // we assume that resources currently have one internal tag only
    private String setType(Context ctx, Set<String> tagLinks) {
        for (String tag : tagLinks) {
            if (ctx.associatedDocuments != null && ctx.associatedDocuments.size() > 0
                    && ctx.associatedDocuments.containsKey(tag)) {
                TagState t = (TagState) ctx.associatedDocuments.get(tag);
                if (t != null && t.external != null && t.external == false
                        && t.key.equals(TAG_KEY_TYPE) && t.value != null) {
                    return t.value;
                }
            }
        }
        return UNKNOWN_RESOURCE_TYPE;
    }

    private Map<String, String> getTagNames(Context ctx, Set<String> tagLinks) {
        Map<String, String> tags = new HashMap<>();
        for (String tag : tagLinks) {
            if (ctx.associatedDocuments != null && ctx.associatedDocuments.size() > 0
                    && ctx.associatedDocuments.containsKey(tag)) {
                TagState t = (TagState) ctx.associatedDocuments.get(tag);
                if (t != null && t.external != null && t.external && t.value != null) {
                    tags.put(t.key, t.value);
                }
            }
        }
        return tags;
    }

    /**
     * Save {@link ResourceViewState} PODOs and build {@link ServiceDocumentQueryResult}.
     */
    private void buildResult(Context ctx, Stages nextStage) throws Throwable {
        ServiceDocumentQueryResult result = new ServiceDocumentQueryResult();
        result.documentLinks = new ArrayList<>();
        result.documents = new LinkedHashMap<>();
        for (ResourceViewState resource : ctx.resourceViewStates) {
            result.documentLinks.add(resource.documentSelfLink);
            result.documents.put(resource.documentSelfLink, resource);
        }

        result.nextPageLink = ctx.nextPageLink;
        result.prevPageLink = ctx.prevPageLink;
        result.documentCount = ctx.documentCount;
        ctx.results = result;
        ctx.stage = nextStage;
        handleStages(ctx);
    }

    private void handleSuccess(Context ctx) {
        ctx.op.setBody(ctx.results);
        ctx.op.complete();
    }

    private void handleError(Context ctx) {
        logWarning("Failed at stage %s with exception: %s", ctx.stage, Utils.toString(ctx.error));
        ctx.op.fail(ctx.error);
    }

    private String stripCategoryString(String documentKind) {
        List<String> segments = Arrays.asList(documentKind.split(COLON_CHAR));
        String category = (segments.size() > 0) ? segments.get(segments.size() - 2)
                + COLON_CHAR + segments.get(segments.size() - 1) : documentKind;
        return category;
    }
}
