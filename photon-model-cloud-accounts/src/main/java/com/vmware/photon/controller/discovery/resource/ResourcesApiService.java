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

import static com.vmware.photon.controller.model.UriPaths.RESOURCE_LIST_API_SERVICE;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.vmware.photon.controller.discovery.common.PhotonControllerErrorCode;
import com.vmware.photon.controller.discovery.common.ResourceProperties;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.RequestRouter.Route.RouteDocumentation;
import com.vmware.xenon.common.RequestRouter.Route.RouteDocumentation.ApiResponse;
import com.vmware.xenon.common.RequestRouter.Route.SupportLevel;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocument.Documentation;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;

/**
 * <p>API layer for summary count, listing and filtering discovered resources</p>
 * <p>
 * <p>
 * <p>
 * This class (and it's supporting QueryTask/Page services) takes care of abstracting any necessary
 * transformations so that the symphony API user is none-the-wiser.
 * </p>
 */
public class ResourcesApiService extends StatelessService {
    public static final String SELF_LINK = RESOURCE_LIST_API_SERVICE;

    // API routes
    public static final String RESOURCES_SUMMARY_PATH_TEMPLATE = UriUtils
            .buildUriPath(SELF_LINK, "summary");
    public static final String RESOURCES_PROPERTIES_PATH_TEMPLATE = UriUtils
            .buildUriPath(SELF_LINK, "properties");
    /**
     * Request to get a summary of resources.
     */
    public static class ResourcesSummaryRequest {
        @Documentation(description = "List of cloud account types - aws, azure, azure_ea, vsphere.")
        public List<String> cloudAccountTypes;

        @Documentation(description = "The service the resources belong to - discovery, cost_insight, etc.")
        public String service;
    }

    /**
     * An API-friendly resource object, populated by collecting the information from the raw object
     * and its associated links and combining it all in this object.
     */
    public static class ResourceViewState extends ServiceDocument {
        public static final String FIELD_NAME_NAME = "name";
        public static final String FIELD_NAME_TYPE = "type";
        public static final String FIELD_NAME_CATEGORY = "category";
        public static final String FIELD_NAME_ADDRESS = "address";
        public static final String FIELD_NAME_CREATION_TIME = "creationTimeMicros";
        public static final String FIELD_NAME_LAST_UPDATED_TIME = "lastUpdatedTimeMicros";
        public static final String FIELD_NAME_CLOUD_ACCOUNT_NAME = "cloudAccounts";
        public static final String FIELD_NAME_CLOUD_ACCOUNT_NAME_LEGACY = "cloudAccount.name";
        public static final String FIELD_NAME_CLOUD_TYPE = "cloudType";
        public static final String FIELD_NAME_CLOUD_TYPE_LEGACY = "cloudAccount.type";
        public static final String FIELD_NAME_TAGS = "tags";
        public static final String FIELD_NAME_REGION_ID = "regionId";
        public static final String FIELD_NAME_INSTANCE_TYPE = "instanceType";

        @Documentation(description = "The id of the resource.")
        public String id;

        @Documentation(description = "The name of the resource.")
        public String name;

        @Documentation(description = "The ip address of the resource.")
        public String address;

        @Documentation(description = "The category of the resource - derived from documentKind.")
        public String category;

        @Documentation(description = "The type of the resource - ec2_instance, vsphere_vm, etc.")
        public String type;

        @Documentation(description = "The cloud type for the cloud account")
        public String cloudType;

        @Documentation(description = "The set of endpoints the resource belongs to.")
        public Set<String> cloudAccounts = new TreeSet<>();

        @Documentation(description = "The region Id the resource is located at.")
        public String regionId;

        @Documentation(description = "The instanceType of the resource - Instance Type of AWS EC2 instance : t2.micro, t2.nano, etc")
        public String instanceType;

        @Documentation(description = "The tags associated to the resource.")
        public Map<String, String> tags = new HashMap<>();

        @Documentation(description = "The time the resource was created in the provider.")
        public long creationTimeMicros;

        @Documentation(description = "The time the resource was last updated in the lucene index.")
        public long lastUpdatedTimeMicros;
    }

    /**
     * An API-friendly representation of resource type counts by cloud account.
     * Each entry will be for a specific cloud account type - aws, azure, etc,
     * based on what was provided in the input.
     *
     * In addition it also provides by type: compute, storage, network
     */
    public static class ResourcesSummaryViewState extends ServiceDocument {
        @Documentation(description = "Cloud account type specific summaries.")
        public Map<String, Map<String, Long>> typeSummary;

        @Documentation(description = "Total count of compute resources.")
        public Long totalComputeCount = 0L;

        @Documentation(description = "Total count of storage resources.")
        public Long totalStorageCount = 0L;

        @Documentation(description = "Total count of networking resources.")
        public Long totalNetworkCount = 0L;
    }

    public ResourcesApiService() {
        super();
        this.toggleOption(ServiceOption.URI_NAMESPACE_OWNER, true);
    }

    @RouteDocumentation(
            path = "/properties",
            description = "Retrieve sortable and filterable resource properties",
            responses = {
                    @ApiResponse(statusCode = 200, description = "Success",
                            response = ResourceProperties.class),
            })
    @Override
    public void handleGet(Operation get) {
        if (get.getUri().getPath().equals(RESOURCES_PROPERTIES_PATH_TEMPLATE)) {
            Operation.createGet(UriUtils.buildUri(this.getHost(),
                    ResourcePropertiesQueryServiceV2.SELF_LINK))
                    .setCompletion(handlePropertiesResult(get))
                    .sendWith(this);
            return;
        }
    }

    @Override
    @RouteDocumentation(
            path = "/summary",
            description = "Resource summary of given set of cloud account types",
            requestBodyType = ResourcesSummaryRequest.class,
            responses = {
                    @ApiResponse(statusCode = 200, description = "Success",
                            response = ResourcesSummaryViewState.class),
                    @ApiResponse(statusCode = 400, description = "Bad Request, Invalid Parameter",
                            response = ServiceErrorResponse.class)
            })
    public void handlePost(Operation post) {
        if (!post.hasBody()) {
            throw new IllegalArgumentException("body is required");
        }

        if (post.getUri().getPath().equals(RESOURCES_SUMMARY_PATH_TEMPLATE)) {
            try {
                getResourcesSummary(post);
            } catch (Exception e) {
                post.fail(e, PhotonControllerErrorCode.GENERIC_ERROR);
                logSevere("Exception encountered while computing resource count summary", e);
            }
            return;
        }
    }

    /**
     * For a given set of cloud types, compute the summary values of all resource types.
     */
    private void getResourcesSummary(Operation post) {
        ResourcesSummaryRequest body = post.getBody(ResourcesSummaryRequest.class);

        Operation.createPost(UriUtils.buildUri(this.getHost(), ResourcesCountSummaryServiceV2.SELF_LINK))
                .setBody(body)
                .setCompletion(handleSummaryResults(post))
                .sendWith(this);
        return;
    }

    /**
     * {@link CompletionHandler} for handling resources summary {@link Operation} results
     */
    private CompletionHandler handleSummaryResults(Operation post) {
        return (op, failure) -> {
            if (failure != null) {
                logSevere("Resource Summary Operation failed with error [%s]", failure.getMessage());
                post.fail(failure);
                return;
            }

            ResourcesSummaryViewState resourcesSummaryViewState = op.getBody(ResourcesSummaryViewState.class);
            post.setBody(resourcesSummaryViewState);
            post.complete();
        };
    }

    /**
     * {@link CompletionHandler} for handling resource properties {@link Operation} results
     */
    private CompletionHandler handlePropertiesResult(Operation get) {
        return (op, failure) -> {
            if (failure != null) {
                logSevere("Resource properties operation failed with error [%s]",
                        failure.getMessage());
                get.fail(failure);
                return;
            }

            ResourceProperties resourceProperties = op.getBody(ResourceProperties.class);
            get.setBody(resourceProperties);
            get.complete();
        };
    }

    @RouteDocumentation(supportLevel = SupportLevel.NOT_SUPPORTED)
    @Override
    public void handleDelete(Operation delete) {
        Operation.failActionNotSupported(delete);
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument d = super.getDocumentTemplate();
        d.documentDescription.name = "Resources";
        d.documentDescription.description = "Manage resources";
        return d;
    }

}
