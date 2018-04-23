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

import static com.vmware.photon.controller.discovery.common.PhotonControllerErrorCode.GENERIC_ERROR;
import static com.vmware.photon.controller.discovery.common.utils.InventoryQueryUtils.createInventoryQueryTaskOperation;
import static com.vmware.photon.controller.model.UriPaths.RESOURCE_SUMMARY_API_SERVICE;
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
import com.vmware.photon.controller.discovery.onboarding.OnboardingUtils;
import com.vmware.photon.controller.discovery.resource.ResourcesQueryPageServiceV3.ResourceViewState;
import com.vmware.photon.controller.discovery.resource.ResourcesQueryTaskServiceV3.ResourceQueryTaskState;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWSResourceType;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AzureResourceType;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.OperationJoin.JoinedCompletionHandler;
import com.vmware.xenon.common.RequestRouter;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocument.Documentation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;

/**
 * API for getting a summary of resources count by cloud account type and resource type.
 */
public class ResourcesCountSummaryService extends StatelessService {
    public static final String SELF_LINK = RESOURCE_SUMMARY_API_SERVICE;

    public static final String VSPHERE_PREFIX = "vsphere";
    public static final String UNDERSCORE_DELIMITER = "_";
    private static final String CUSTOM_PROP_TYPE = "type";
    private static final String CUSTOM_PROP_IS_DELETED = "isDeleted";

    // stores a mapping for resource summary operation ID and 'type' tag,
    // for example: 7912:type=ec2_instance.
    private static Map<Long, String> typeTagMap = new HashMap<>();

    /**
     * Hardcoded types for resourceTypes - these will eventually come from photon-model
     * Depends on ticket VSYM-7506
     */

    public static enum VSphereResourceType {
        vm("vm"),
        cluster("cluster"),
        server("server"),
        virtualDisk("virtualDisk");

        private final String value;

        private VSphereResourceType(String value) {
            this.value = value;
        }

        // Append VsphereResourceType enum entities with vsphere prefix which to construct valid
        // API response which UI expects.
        public String toString() {
            return VSPHERE_PREFIX + UNDERSCORE_DELIMITER + this.value;
        }
    }

    /** Local context object to pass around. */
    public static class SummaryContext {
        Operation postOp;
        List<String> cloudAccountTypes;

        SummaryContext(List<String> opArgs) {
            this.cloudAccountTypes = (opArgs == null || opArgs.size() == 0) ?
                    Arrays.asList(aws.name(), azure.name(), vsphere.name()) : opArgs;
        }
    }

    List<String> computeResourceTypes = Arrays.asList(
            AWSResourceType.ec2_instance.name(),
            VSphereResourceType.vm.name(),
            VSphereResourceType.server.name(),
            VSphereResourceType.cluster.name(),
            AzureResourceType.azure_vm.name());

    List<String> storageResourceTypes = Arrays.asList(
            AWSResourceType.ebs_block.name(),
            AWSResourceType.s3_bucket.name(),
            AzureResourceType.azure_vhd.name(),
            AzureResourceType.azure_managed_disk.name(),
            VSphereResourceType.virtualDisk.name());

    List<String> networkResourceTypes = Arrays.asList(
            AWSResourceType.ec2_net_interface.name(),
            AWSResourceType.ec2_subnet.name(),
            AWSResourceType.ec2_vpc.name(),
            AWSResourceType.ec2_security_group.name(),
            AzureResourceType.azure_vnet.name(),
            AzureResourceType.azure_net_interface.name(),
            AzureResourceType.azure_subnet.name());


    /** Request to get a summary of resources by cloud account. */
    public static class ResourcesSummaryRequest {
        @Documentation(description = "The list of cloud account types - aws, azure, vsphere.")
        public List<String> cloudAccountTypes;
    }

    /**
     * An API-friendly representation of resource counts by cloud account.
     * Each entry will be for a specific cloud account type - aws, azure, etc,
     * based on what was provided in the input.
     *
     * In addition it also provides by type: compute, storage, network
     */
    public static class ResourceSummaryViewState extends ServiceDocument {
        @Documentation(description = "Cloud account type specific summaries.")
        public Map<String, Map<String, Long>> typeSummary;

        @Documentation(description = "Total count of compute resources.")
        public Long totalComputeCount = 0L;

        @Documentation(description = "Total count of storage resources.")
        public Long totalStorageCount = 0L;

        @Documentation(description = "Total count of networking resources.")
        public Long totalNetworkCount = 0L;
    }

    @Override
    public void handlePost(Operation post) {
        if (!post.hasBody()) {
            throw new IllegalArgumentException("body is required");
        }

        ResourcesSummaryRequest body = post.getBody(ResourcesSummaryRequest.class);
        SummaryContext context = new SummaryContext(body.cloudAccountTypes);
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
                        typeSummaryOperations.add(createResourceSummaryQuery(aws.toString(), awsType.name()));
                    }
                    break;
                case azure:
                    for (AzureResourceType azureType : AzureResourceType.values()) {
                        if (!azureType.equals(AzureResourceType.azure_blob)) {
                            typeSummaryOperations.add(createResourceSummaryQuery(azure.toString(), azureType.name()));
                        }
                    }
                    break;
                case vsphere:
                    // TODO: https://jira-hzn.eng.vmware.com/browse/VSYM-7615
                    OnboardingUtils.getProjectLinks(this, (projectLinks, e) -> {
                        if (e != null) {
                            logWarning("Failure getting tenantLinks for current user context %s",
                                    e.getMessage());
                            return;
                        }
                        for (VSphereResourceType vsphereType : VSphereResourceType.values()) {
                            typeSummaryOperations.add(createResourceSummaryQueryForPrivateCloud(vsphereType,
                                    projectLinks));
                        }

                        sendSummaryOperations(typeSummaryOperations, context);
                    });
                    break;
                default:
                    break;
                }
            }
        }

        if (!context.cloudAccountTypes.contains(EndpointType.vsphere.name())) {
            sendSummaryOperations(typeSummaryOperations, context);
        }
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
     * Given a resource type, frame the query to {@link ResourcesQueryTaskServiceV3} and
     * return the {@link Operation}
     * @param typeTag The tag value in the format {key=value}.
     * @return The {@link Operation}.
     */
    private Operation createResourceSummaryQuery(String cloudType, String typeTag) {
        ResourceQueryTaskState task = new ResourceQueryTaskState();
        task.taskInfo = TaskState.createDirect();
        task.filter = new QuerySpecification();
        task.filter.query = Query.Builder.create()
                .addFieldClause(ResourceViewState.FIELD_NAME_CLOUD_ACCOUNT_TYPE,
                        cloudType,
                        MatchType.TERM,
                        Occurance.MUST_OCCUR)
                .addCaseInsensitiveFieldClause(ResourceViewState.FIELD_NAME_TYPE,
                        typeTag,
                        MatchType.TERM,
                        Occurance.MUST_OCCUR).build();
        task.filter.options.add(QueryOption.COUNT);
        task.filter.options.add(QueryOption.INDEXED_METADATA);

        Operation operation = Operation.createPost(this, ResourcesQueryTaskServiceV3.FACTORY_LINK)
                .setBody(task);
        typeTagMap.put(operation.getId(), typeTag);
        return operation;
    }

    /** Temporary fix for private cloud counts against customProperties.type until
     * https://jira-hzn.eng.vmware.com/browse/VSYM-7615 is completed.
     * Constructs a COUNT query with tenantLinks, documentKind and customProperty.type to
     * get private cloud resource counts.
     **/
    private Operation createResourceSummaryQueryForPrivateCloud(VSphereResourceType vSphereType,
            List<String> tenantLinks) {

        Class documentKind = ComputeState.class;
        Query query;

        if (vSphereType.equals(VSphereResourceType.virtualDisk)) {
            documentKind = DiskState.class;
        }

        query = Query.Builder.create()
                .addKindFieldClause(documentKind)
                .addCompositeFieldClause(ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                        CUSTOM_PROP_TYPE, vSphereType.name())
                .addCompositeFieldClause(ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                        CUSTOM_PROP_IS_DELETED, Boolean.TRUE.toString(), Occurance.MUST_NOT_OCCUR)
                .build();

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.COUNT)
                .setQuery(query)
                .build();

        queryTask.tenantLinks = tenantLinks;

        Operation operation = createInventoryQueryTaskOperation(this, queryTask);

        typeTagMap.put(operation.getId(), vSphereType.name());

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

            ResourceSummaryViewState summaryViewState = new ResourceSummaryViewState();
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
                String resourceType = typeTagMap.get(taskOp.getId());

                ResourceQueryTaskState publicCloudState = null;
                QueryTask privateCloudState = null;

                if (EnumUtils.isValidEnum(VSphereResourceType.class,
                        typeTagMap.get(taskOp.getId()))) {
                    privateCloudState = taskOp.getBody(QueryTask.class);
                } else {
                    publicCloudState = taskOp.getBody(ResourceQueryTaskState.class);
                }

                if (publicCloudState != null) {
                    if (publicCloudState.taskInfo.stage == TaskStage.FAILED
                            || resourceType == null) {
                        continue;
                    }
                }

                Long count = 0L;
                if (privateCloudState != null) {
                    if (privateCloudState.results != null
                            && privateCloudState.results.documentCount != null) {
                        count = privateCloudState.results.documentCount;
                    }
                } else {
                    if (publicCloudState.results != null
                            && publicCloudState.results.documentCount != null) {
                        count = publicCloudState.results.documentCount;
                    }
                }

                if (EnumUtils.isValidEnum(AWSResourceType.class, resourceType)) {
                    summaryViewState.typeSummary.get(aws.name()).put(resourceType, count);
                } else if (EnumUtils.isValidEnum(AzureResourceType.class, resourceType)) {
                    if (!resourceType.equals(AzureResourceType.azure_blob)) {
                        summaryViewState.typeSummary.get(azure.name()).put(resourceType, count);
                    }
                } else if (EnumUtils.isValidEnum(VSphereResourceType.class, resourceType)) {
                    summaryViewState.typeSummary.get(vsphere.name()).put(Enum.valueOf
                            (VSphereResourceType.class, resourceType).toString(), count);
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
