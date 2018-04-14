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

import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.CLOUD_ACCOUNT_SERVICE_TAG_DISCOVERY;
import static com.vmware.photon.controller.discovery.common.PhotonControllerErrorCode.GENERIC_ERROR;
import static com.vmware.photon.controller.model.UriPaths.RESOURCE_SUMMARY_API_SERVICE_V2;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType.aws;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType.azure;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType.vsphere;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.commons.lang3.EnumUtils;

import com.vmware.photon.controller.discovery.endpoints.EndpointUtils;
import com.vmware.photon.controller.discovery.resource.ResourcesApiService.ResourceViewState;
import com.vmware.photon.controller.discovery.resource.ResourcesApiService.ResourcesSummaryRequest;
import com.vmware.photon.controller.discovery.resource.ResourcesApiService.ResourcesSummaryViewState;
import com.vmware.photon.controller.discovery.resource.ResourcesQueryTaskServiceV4.ResourceQueryTaskState;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWSResourceType;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AzureResourceType;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.OperationJoin.JoinedCompletionHandler;
import com.vmware.xenon.common.RequestRouter;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;

/**
 * API for getting a summary of resources count by cloud account type and resource type.
 */
public class ResourcesCountSummaryServiceV2 extends StatelessService {
    public static final String SELF_LINK = RESOURCE_SUMMARY_API_SERVICE_V2;

    public static final String TAG_DELIMITER = "=";

    // stores a mapping for resource summary operation ID and 'type' tag,
    // for example: 7912:type=ec2_instance.
    private static Map<Long, String> typeTagMap = new HashMap<>();

    /**
     * Hardcoded types for resourceTypes - these will eventually come from photon-model
     * Depends on ticket VSYM-7506
     */

    public static enum VSphereResourceType {
        vsphere_vm("vsphere_vm"),
        vsphere_cluster("vsphere_cluster"),
        vsphere_server("vsphere_server"),
        vsphere_virtualDisk("vsphere_virtualDisk"),
        vsphere_disk("vsphere_disk");

        private final String value;

        private VSphereResourceType(String value) {
            this.value = value;
        }
    }

    /** Local context object to pass around. */
    public static class SummaryContext {
        Operation postOp;
        List<String> cloudAccountTypes;
        String service;

        SummaryContext(List<String> cloudTypes, String service) {
            this.cloudAccountTypes = (cloudTypes == null || cloudTypes.size() == 0) ?
                    Arrays.asList(aws.name(), azure.name(), vsphere.name()) : cloudTypes;
            this.service = (service == null || service.isEmpty()) ?
                    CLOUD_ACCOUNT_SERVICE_TAG_DISCOVERY : service;
        }
    }

    List<String> computeResourceTypes = Arrays.asList(
            AWSResourceType.ec2_instance.name(),
            VSphereResourceType.vsphere_vm.name(),
            VSphereResourceType.vsphere_server.name(),
            VSphereResourceType.vsphere_cluster.name(),
            AzureResourceType.azure_vm.name());

    List<String> storageResourceTypes = Arrays.asList(
            AWSResourceType.ebs_block.name(),
            AWSResourceType.s3_bucket.name(),
            AzureResourceType.azure_vhd.name(),
            AzureResourceType.azure_managed_disk.name(),
            VSphereResourceType.vsphere_virtualDisk.name(),
            VSphereResourceType.vsphere_disk.name());

    List<String> networkResourceTypes = Arrays.asList(
            AWSResourceType.ec2_net_interface.name(),
            AWSResourceType.ec2_subnet.name(),
            AWSResourceType.ec2_vpc.name(),
            AWSResourceType.ec2_security_group.name(),
            AzureResourceType.azure_vnet.name(),
            AzureResourceType.azure_net_interface.name(),
            AzureResourceType.azure_subnet.name());

    @Override
    public void handlePost(Operation post) {
        if (!post.hasBody()) {
            throw new IllegalArgumentException("body is required");
        }

        ResourcesSummaryRequest body = post.getBody(ResourcesSummaryRequest.class);
        SummaryContext context = new SummaryContext(body.cloudAccountTypes, body.service);
        context.postOp = post;
        try {
            computeResourcesSummary(context);
        } catch (Exception e) {
            post.fail(e, GENERIC_ERROR);
            logSevere("Exception encountered while computing resource count summary", e);
        }
        return;
    }

    /**
     * For each resource type, compute the various counts.
     * Each resource type maps the internal type tag it is associated to.
     */
    private void computeResourcesSummary(SummaryContext context) {
        if (context.cloudAccountTypes == null || context.cloudAccountTypes.isEmpty()) {
            context.cloudAccountTypes = Arrays.stream(EndpointType.values())
                    .map(EndpointType::name).collect(Collectors.toList());
        }

        List<Operation> typeSummaryOperations = new ArrayList<>();

        for (String cloudAccountType : context.cloudAccountTypes) {
            if (EndpointUtils.isSupportedEndpointType(cloudAccountType)) {
                switch (EndpointType.valueOf(cloudAccountType)) {
                case aws:
                    for (AWSResourceType awsType : AWSResourceType.values()) {
                        typeSummaryOperations.add(createResourceSummaryQuery(aws.toString(),
                                awsType.name(), context.service));
                    }
                    break;
                case azure:
                    for (AzureResourceType azureType : AzureResourceType.values()) {
                        if (!azureType.equals(AzureResourceType.azure_blob)) {
                            typeSummaryOperations.add(createResourceSummaryQuery(azure.toString(),
                                    azureType.name(), context.service));
                        }
                    }
                    break;
                case vsphere:
                    for (VSphereResourceType vsphereType : VSphereResourceType.values()) {
                        typeSummaryOperations.add(createResourceSummaryQuery(cloudAccountType,
                                vsphereType.name(), context.service));
                    }
                    break;
                default:
                    break;
                }
            }
        }

        sendSummaryOperations(typeSummaryOperations, context);
    }

    private void sendSummaryOperations(List<Operation> typeSummaryOperations,
            SummaryContext context) {

        if (typeSummaryOperations.size() > 0) {
            OperationJoin.create(typeSummaryOperations)
                    .setCompletion(handleSummaryResults(context))
                    .sendWith(this);
            return;
        }
        throw new IllegalArgumentException("Incorrect arguments. Please check.");
    }

    /**
     * Given a resource type, frame the query to {@link ResourcesQueryTaskServiceV4} and
     * return the {@link Operation}
     * @param cloudType The cloud type value, eg., aws, azure, etc.
     * @param typeTag The tag value in the format {key=value}.
     * @param service The service the resources are part of.
     * @return The {@link Operation}.
     */
    private Operation createResourceSummaryQuery(String cloudType, String typeTag, String service) {
        ResourceQueryTaskState task = new ResourceQueryTaskState();
        task.taskInfo = TaskState.createDirect();
        task.filter = new QuerySpecification();
        task.filter.query = Query.Builder.create()
                .addFieldClause(ResourceViewState.FIELD_NAME_CLOUD_TYPE,
                        cloudType,
                        MatchType.TERM,
                        Occurance.MUST_OCCUR)
                .addFieldClause(ResourcesQueryTaskServiceV4.CLOUDACCOUNT_SERVICE,
                        service,
                        MatchType.TERM,
                        Occurance.MUST_OCCUR)
                .addCaseInsensitiveFieldClause(ResourceViewState.FIELD_NAME_TYPE,
                        typeTag,
                        MatchType.TERM,
                        Occurance.MUST_OCCUR).build();
        task.filter.options.add(QueryOption.COUNT);
        task.filter.options.add(QueryOption.INDEXED_METADATA);
        Operation operation = Operation.createPost(this, ResourcesQueryTaskServiceV4.FACTORY_LINK)
                .setBody(task);
        typeTagMap.put(operation.getId(), typeTag);
        return operation;
    }

    /**
     * {@link com.vmware.xenon.common.OperationJoin.JoinedCompletionHandler} to use for handling
     * Resource Summary {@link OperationJoin} results
     */
    private JoinedCompletionHandler handleSummaryResults(SummaryContext context) {
        JoinedCompletionHandler joinedCompletionHandler = (ops, failures) -> {
            if (failures != null) {
                for (Entry<Long, Throwable> entry : failures.entrySet()) {
                    Operation operation = ops.get(entry.getKey());
                    logSevere("Type summary operation [%s] failed with error [%s]", operation.getUri().getPath(),
                            failures.get(entry.getKey()).getMessage());
                }
                EndpointUtils.failOperation(
                        getHost(), context.postOp, GENERIC_ERROR,
                        Operation.STATUS_CODE_INTERNAL_ERROR, context.postOp.getUri().getPath());
                return;
            }

            ResourcesSummaryViewState summaryViewState = new ResourcesSummaryViewState();
            summaryViewState.typeSummary = new HashMap<>();

            // initialize cloud account blocks
            for (String cloudAccountType : context.cloudAccountTypes) {
                if (!summaryViewState.typeSummary.containsKey(cloudAccountType)
                        && EndpointUtils.isSupportedEndpointType(cloudAccountType)) {
                    Map<String, Long> countsByResourceType = new HashMap<>();
                    if (cloudAccountType.equals(EndpointUtils.VSPHERE_ON_PREM_ADAPTER)) {
                        cloudAccountType = EndpointType.vsphere.name();
                    }
                    summaryViewState.typeSummary.put(cloudAccountType, countsByResourceType);
                }
            }

            for (Operation taskOp : ops.values()) {
                ResourceQueryTaskState state = taskOp.getBody(ResourceQueryTaskState.class);
                String[] tag = typeTagMap.get(taskOp.getId()).split(TAG_DELIMITER);
                String resourceType = tag[0];

                if (state.taskInfo.stage == TaskStage.FAILED || resourceType == null) {
                    continue;
                }

                Long count = 0L;
                if (state.results != null && state.results.documentCount != null) {
                    count = state.results.documentCount;
                }

                if (EnumUtils.isValidEnum(AWSResourceType.class, resourceType)) {
                    summaryViewState.typeSummary.get(aws.name()).put(resourceType, count);
                } else if (EnumUtils.isValidEnum(AzureResourceType.class, resourceType)) {
                    if (!resourceType.equals(AzureResourceType.azure_blob)) {
                        summaryViewState.typeSummary.get(azure.name()).put(resourceType, count);
                    }
                } else if (EnumUtils.isValidEnum(VSphereResourceType.class, resourceType)) {
                    summaryViewState.typeSummary.get(vsphere.name()).put(resourceType, count);
                } else {
                    logFine("Resource type [%s] not supported", resourceType);
                }

                if (this.networkResourceTypes.contains(resourceType)) {
                    summaryViewState.totalNetworkCount += count;
                } else if (this.computeResourceTypes.contains(resourceType)) {
                    summaryViewState.totalComputeCount += count;
                } else if (this.storageResourceTypes.contains(resourceType)) {
                    summaryViewState.totalStorageCount += count;
                } else {
                    logFine("Resource type [%s] not supported", resourceType);
                }
            }

            context.postOp.setBody(summaryViewState);
            context.postOp.complete();
        };
        return joinedCompletionHandler;
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument d = super.getDocumentTemplate();
        d.documentDescription.name = "Resource Summary";
        d.documentDescription.description = "Query for resource summary";
        d.documentDescription.serviceRequestRoutes = new HashMap<>();

        // POST
        RequestRouter.Route getRoute = new RequestRouter.Route();
        getRoute.action = Action.POST;
        getRoute.responseType = ResourcesSummaryRequest.class;
        d.documentDescription.serviceRequestRoutes.put(getRoute.action,
                Collections.singletonList(getRoute));

        return d;
    }
}
