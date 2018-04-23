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

import static com.vmware.photon.controller.discovery.common.utils.InventoryQueryUtils.createInventoryQueryTaskOperation;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.VSPHERE_ON_PREM_ADAPTER;
import static com.vmware.photon.controller.model.UriPaths.RESOURCE_QUERY_TASK_SERVICE_V3;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType.vsphere;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.TAG_KEY_TYPE;
import static com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType.ENDPOINT_HOST;
import static com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType.VM_HOST;
import static com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType.ZONE;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.vmware.photon.controller.discovery.cloudaccount.utils.FilterUtils;
import com.vmware.photon.controller.discovery.cloudaccount.utils.QueryHelper;
import com.vmware.photon.controller.discovery.common.TaskHelper;
import com.vmware.photon.controller.discovery.onboarding.OnboardingUtils;
import com.vmware.photon.controller.discovery.resource.ResourcesQueryPageServiceV3.ResourceViewState;
import com.vmware.photon.controller.discovery.resource.ResourcesQueryTaskServiceV3.ResourceQueryTaskState;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.SecurityGroupService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.SortOrder;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskFactoryService;
import com.vmware.xenon.services.common.TaskService;

/**
 * Query Task service returning a list of resources.
 * <p>
 * Resources include: ComputeState, DiskState, NetworkState, NetworkInterfaceState, SubnetState,
 * SecurityGroupState
 */
public class ResourcesQueryTaskServiceV3 extends TaskService<ResourceQueryTaskState> {
    public static final String FACTORY_LINK = RESOURCE_QUERY_TASK_SERVICE_V3;

    private static final String PROPERTY_NAME_QUERY_RESULT_LIMIT = UriPaths.PROPERTY_PREFIX
            + "ResourcesQueryTaskServiceV3.QUERY_RESULT_LIMIT";
    private static final int QUERY_RESULT_LIMIT = Integer
            .getInteger(PROPERTY_NAME_QUERY_RESULT_LIMIT, 50);
    private static final String ABBREVIATED_SUBNET_DOC_KIND = "SubnetService:SubnetState";
    private static final String ABBREVIATED_NET_INTERF_DOC_KIND = "NetworkInterfaceService:NetworkInterfaceState";
    private static final String ABBREVIATED_NETWORK_DOC_KIND = "NetworkService:NetworkState";
    private static final String ABBREVIATED_SECURITY_GROUP_DOC_KIND = "SecurityGroupService:SecurityGroupState";
    private static final String FIELD_NAME_DOCUMENT_KIND = "documentKind";
    private static final String DOC_KIND_PREFIX = "com:vmware:photon:controller:model:resources:";
    public static final String TAG_DELIMITER = "=";
    public static final String TAG_VALUE = "value";
    public static final String TAG_KEY = "key";
    public static final String TAG_PATTERN = TAG_KEY + TAG_DELIMITER + TAG_VALUE;
    public static final String EMPTY_STRING = "";
    public static final String CUSTOM_PROPERTY_IS_DELETED = "isDeleted";

    /**
     * Substages for task processing.
     */
    public enum AdvancedFilteringOption {
        /**
         * Option to build a query to filter based on endpoint attributes.
         */
        ENDPOINT,

        /**
         * Option to build a query to filter based on tags attributes.
         */
        TAGS,

        /**
         * Option to build a query to filter based on type tag attributes.
         */
        TYPE
    }

    private static final Collection<String> RESOURCE_TYPES = Arrays.asList(
            Utils.buildKind(DiskState.class),
            Utils.buildKind(ComputeState.class),
            Utils.buildKind(NetworkState.class),
            Utils.buildKind(NetworkInterfaceState.class),
            Utils.buildKind(SubnetState.class),
            Utils.buildKind(SecurityGroupService.SecurityGroupState.class));

    /**
     * The keys to this map are the supported filter propertyNames (based off the API-friendly
     * model). The values are the photon-model related propertyNames that we should use when
     * executing the "actual" query task against the Xenon index.
     */
    public static final Map<String, String> SUPPORTED_FILTER_MAP;

    static {
        Map<String, String> filterMap = new HashMap<>();
        String name = ResourceViewState.FIELD_NAME_NAME;
        String nameTranslate = ResourceState.FIELD_NAME_NAME;
        filterMap.put(name, nameTranslate);

        String category = ResourceViewState.FIELD_NAME_CATEGORY;
        String categoryTranslate = ComputeState.FIELD_NAME_KIND;
        filterMap.put(category, categoryTranslate);

        String address = ResourceViewState.FIELD_NAME_ADDRESS;
        String addressTranslate = ComputeState.FIELD_NAME_ADDRESS;
        filterMap.put(address, addressTranslate);

        String regionId = ResourceViewState.FIELD_NAME_REGION_ID;
        String regionIdTranslate = ResourceState.FIELD_NAME_REGION_ID;
        filterMap.put(regionId, regionIdTranslate);

        String instanceType = ResourceViewState.FIELD_NAME_INSTANCE_TYPE;
        String instanceTypeTranslate = ComputeState.FIELD_NAME_INSTANCE_TYPE;
        filterMap.put(instanceType, instanceTypeTranslate);

        SUPPORTED_FILTER_MAP = Collections.unmodifiableMap(filterMap);
    }

    public static class AdvancedFilterDefinition {
        // The second level entity on which the filter will be applied
        public Class<? extends ServiceDocument> className;
        // The attribute on which the filtering will be performed
        public String attributeName;

        public AdvancedFilterDefinition(Class<? extends ServiceDocument> className,
                String attributeName) {
            this.className = className;
            this.attributeName = attributeName;
        }
    }

    public static final Map<String, String> SUPPORTED_ENDPOINT_FIELDS_FILTER_MAP;

    static {
        Map<String, String> endpointFilterMap = new HashMap<>();
        endpointFilterMap.put(ResourceViewState.FIELD_NAME_CLOUD_ACCOUNT_NAME,
                ResourceState.FIELD_NAME_NAME);
        endpointFilterMap.put(ResourceViewState.FIELD_NAME_CLOUD_ACCOUNT_TYPE,
                EndpointState.FIELD_NAME_ENDPOINT_TYPE);
        SUPPORTED_ENDPOINT_FIELDS_FILTER_MAP = Collections.unmodifiableMap(endpointFilterMap);
    }

    public static final Map<String, String> SUPPORTED_TYPE_FILTER_MAP;

    static {
        Map<String, String> typeFilterMap = new HashMap<>();
        typeFilterMap.put(ResourceViewState.FIELD_NAME_TYPE,
                TAG_VALUE);
        SUPPORTED_TYPE_FILTER_MAP = Collections.unmodifiableMap(typeFilterMap);
    }

    public static final Map<String, AdvancedFilterDefinition> SUPPORTED_TAGS_FIELDS_FILTER_MAP;

    static {
        Map<String, AdvancedFilterDefinition> tagFilterMap = new HashMap<>();
        String tagsAttribute = ResourceViewState.FIELD_NAME_TAGS;
        AdvancedFilterDefinition tagFilterDefinition = new AdvancedFilterDefinition(TagState.class,
                TAG_PATTERN);
        tagFilterMap.put(tagsAttribute, tagFilterDefinition);
        SUPPORTED_TAGS_FIELDS_FILTER_MAP = Collections.unmodifiableMap(tagFilterMap);
    }

    /**
     * The items in this list are properties that need to be case insensitive when filtering
     */
    public static final List<String> CASE_INSENSITIVE_FIELDS_LIST;

    static {
        List<String> fields = new ArrayList<>();
        fields.add(ResourceViewState.FIELD_NAME_NAME);
        fields.add(ResourceViewState.FIELD_NAME_INSTANCE_TYPE);

        CASE_INSENSITIVE_FIELDS_LIST = Collections.unmodifiableList(fields);
    }

    public static class ResourceQueryTaskState extends TaskService.TaskServiceState {
        @Documentation(description = "The query specification for filtering the resources result set")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public QuerySpecification filter;

        @Documentation(description = "The tenantLink to filter by")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.OPTIONAL),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public String tenantLink;

        @Documentation(description = "This is only for internal usage. It keeps track of internal stages")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public SubStage subStage;

        @Documentation(description = "The public cloud usage result set")
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public ServiceDocumentQueryResult results;

        @Documentation(description = "The authorization links associated with this request.")
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public List<String> tenantLinks;

        @Documentation(description = "This is only for internal usage. It keeps track of internal filtering logic.")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public Set<String> endpointLinks;

        @Documentation(description = "This is only for internal usage. It keeps track of internal filtering logic.")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public List<Set<String>> tagLinks;

        @Documentation(description = "This is only for internal usage. It keeps track of internal filtering logic.")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public Set<String> tagLinksWithORCondition;

        @Documentation(description = "This is only for internal usage. It keeps track of internal filtering logic.")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public Set<String> tagKeysWithORCondition;

        @Documentation(description = "This is only for internal usage. It keeps track of internal filtering logic.")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public List<Set<String>> typeTagLinks;
    }

    /**
     * Substages for task processing.
     */
    public enum SubStage {
        /**
         * Stage to process the request.
         */
        PROCESS_REQUEST,

        /**
         * Stage to retrieve resources.
         */
        QUERY_RESOURCES
    }

    public static FactoryService createFactory() {
        TaskFactoryService fs = new TaskFactoryService(ResourceQueryTaskState.class) {
            @Override
            public Service createServiceInstance() throws Throwable {
                return new ResourcesQueryTaskServiceV3();
            }
        };
        fs.setPeerNodeSelectorPath(ServiceUriPaths.DEFAULT_1X_NODE_SELECTOR);
        return fs;
    }

    public ResourcesQueryTaskServiceV3() {
        super(ResourceQueryTaskState.class);
        toggleOption(ServiceOption.REPLICATION, true);
        toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    /**
     * Populates the tenant links (aka project links) from the user context if not supplied
     * externally.
     */
    @Override
    public void handleStart(Operation taskOperation) {
        final ResourceQueryTaskState state;
        if (taskOperation.hasBody()) {
            state = taskOperation.getBody(ResourceQueryTaskState.class);
        } else {
            state = new ResourceQueryTaskState();
        }
        taskOperation.setBody(state);

        if (taskOperation.getAuthorizationContext().isSystemUser()) {
            super.handleStart(taskOperation);
            return;
        } else {
            OnboardingUtils.getProjectLinks(this, (projectLinks, f) -> {
                try {
                    if (f != null) {
                        throw f;
                    }
                    state.tenantLinks = projectLinks;
                    taskOperation.setBody(state);
                    super.handleStart(taskOperation);
                } catch (Throwable t) {
                    logSevere("Failed during creation: %s", Utils.toString(t));
                    taskOperation.fail(t);
                }
            });
        }
    }

    @Override
    protected void initializeState(ResourceQueryTaskState task, Operation taskOperation) {
        task.subStage = SubStage.PROCESS_REQUEST;
        task.tagLinks = new ArrayList<>();
        task.typeTagLinks = new ArrayList<>();
        task.tagLinksWithORCondition = new HashSet<>();
        task.tagKeysWithORCondition = new HashSet<>();
        if (task.taskInfo == null) {
            task.taskInfo = TaskState.create();
        }
        super.initializeState(task, taskOperation);
    }

    @Override
    protected ResourceQueryTaskState validateStartPost(Operation taskOperation) {
        ResourceQueryTaskState task = super.validateStartPost(taskOperation);

        if (task != null) {
            if (task.subStage != null) {
                taskOperation.fail(
                        new IllegalArgumentException("Do not specify subStage: internal use only"));
                return null;
            }
        }

        return task;
    }

    @Override
    protected boolean validateTransition(Operation patch, ResourceQueryTaskState currentTask,
            ResourceQueryTaskState patchBody) {
        super.validateTransition(patch, currentTask, patchBody);
        if (patchBody.taskInfo.stage != TaskStage.STARTED) {
            return true;
        }

        if (!TaskHelper.validateTransition(currentTask.subStage, patchBody.subStage)) {
            patch.fail(new IllegalArgumentException(
                    String.format("Task subStage cannot be moved from '%s' to '%s'",
                            currentTask.subStage, patchBody.subStage)));
            return false;
        }

        return true;
    }

    @Override
    public void handlePatch(Operation patch) {
        ResourceQueryTaskState currentTask = getState(patch);
        ResourceQueryTaskState patchBody = getBody(patch);
        if (!validateTransition(patch, currentTask, patchBody)) {
            return;
        }
        updateState(currentTask, patchBody);
        patch.complete();

        switch (patchBody.taskInfo.stage) {
        case STARTED:
            handleSubStage(patchBody);
            break;
        case FINISHED:
            logFine("Task finished successfully");
            break;
        case FAILED:
            logWarning("Task failed: %s", (patchBody.failureMessage == null ? "No reason given"
                    : patchBody.failureMessage));
            break;
        default:
            logWarning("Unexpected stage: %s", patchBody.taskInfo.stage);
            break;
        }
    }

    /**
     * State machine for the task service.
     */
    private void handleSubStage(ResourceQueryTaskState state) {
        switch (state.subStage) {
        case PROCESS_REQUEST:
            processRequest(state, SubStage.QUERY_RESOURCES);
            break;
        case QUERY_RESOURCES:
            queryResources(state);
            break;
        default:
            throw new IllegalStateException("Unknown stage encountered: " + state.subStage);
        }
    }

    /**
     * Method to process the incoming querying filters and sort terms.
     */
    private void processRequest(ResourceQueryTaskState state, SubStage nextStage) {
        // if any of the advanced filter parameters are supplied in the passed in query
        // spec then create a query task object, get the results corresponding to the
        // supplied query and enhance the resources query to honor the additional
        // filtering requirement.

        try {
            List<Operation> joinOperations = new ArrayList<>();

            if (state.filter == null || state.filter.query == null) {
                state.subStage = nextStage;
                sendSelfPatch(state);
                return;
            }
            // Handle endpoint transformation
            if (FilterUtils.doesQueryMatchGivenFilters(state.filter.query,
                    SUPPORTED_ENDPOINT_FIELDS_FILTER_MAP.keySet())) {
                List<QueryTask> queryTaskList = buildQueryForAdvancedFiltering(
                        state, AdvancedFilteringOption.ENDPOINT, null);
                Operation endpointQueryOp = createInventoryQueryTaskOperation(this,
                        queryTaskList.get(0));
                joinOperations.add(endpointQueryOp);
            }
            // Handle tags transformation
            Map<String, String> tagsMapForKeysAndQueryTaskLink = new HashMap<>();
            Map<String, String> typeTagsMapForQueryTaskLink = new HashMap<>();
            if (FilterUtils.doesQueryMatchGivenFilters(state.filter.query,
                    SUPPORTED_TAGS_FIELDS_FILTER_MAP.keySet())) {
                // Special handling for OR clause between certain tags.
                //1) Extract all the query clauses that have SHOULD_OCCUR for some of the tag conditions.
                //2) Find the tag key that has the OR supplied. When we create and evaluate the queryTask
                //associated with this tag key we will apply the OR condition with the other results.
                Set<Query> tagsWithORClause = new HashSet<>();
                Set<String> tagKeysWithORClause = new HashSet<>();
                FilterUtils.extractORClausesBasedOnGivenFilters(state.filter.query,
                        SUPPORTED_TAGS_FIELDS_FILTER_MAP.keySet(), tagsWithORClause);
                try {
                    for (Query query : tagsWithORClause) {
                        validateTagParametersInFilter(query);
                        String[] tagValues = query.term.matchValue
                                .split(TAG_DELIMITER);
                        tagKeysWithORClause.add(tagValues[0]);
                    }
                } catch (Exception e) {
                    handleError(state, e);
                    return;
                }
                state.tagKeysWithORCondition = tagKeysWithORClause;
                List<QueryTask> tagsQueryTaskList = buildQueryForAdvancedFiltering(state,
                        AdvancedFilteringOption.TAGS, tagsMapForKeysAndQueryTaskLink);
                // For each kind of tag a separate query is executed and the result is added to the
                // state.
                for (QueryTask tagsTask : tagsQueryTaskList) {
                    Operation tagsQueryOp = createInventoryQueryTaskOperation(this, tagsTask);
                    joinOperations.add(tagsQueryOp);
                }
            }

            //Segregate all the tag queryTasks that have an OR clause
            List<String> ORedTagQueryTaskLinks = new ArrayList<>();
            for (String ORedKey : state.tagKeysWithORCondition) {
                String queryTaskUUID = tagsMapForKeysAndQueryTaskLink
                        .get(ORedKey);
                ORedTagQueryTaskLinks.add(UriUtils.buildUriPath(
                        ServiceUriPaths.CORE_LOCAL_QUERY_TASKS,
                        queryTaskUUID));
            }

            // Handle type tag transformation
            if (FilterUtils.doesQueryMatchGivenFilters(state.filter.query,
                    SUPPORTED_TYPE_FILTER_MAP.keySet())) {
                List<QueryTask> typeTagQueryTaskList = buildQueryForAdvancedFiltering(state,
                        AdvancedFilteringOption.TYPE, typeTagsMapForQueryTaskLink);
                // For each kind of type tag a separate query is executed and the result is added to the
                // state.
                for (QueryTask tagsTask : typeTagQueryTaskList) {
                    Operation typeTagQueryOp = createInventoryQueryTaskOperation(this, tagsTask);
                    joinOperations.add(typeTagQueryOp);
                }
            }

            // Segregate the self links of the query task that correspond to the type queries
            List<String> typeQueryTaskLinks = new ArrayList<>();
            for (String taskLink : typeTagsMapForQueryTaskLink.values()) {
                typeQueryTaskLinks.add(UriUtils.buildUriPath(
                        ServiceUriPaths.CORE_LOCAL_QUERY_TASKS,
                        taskLink));
            }
            // If any of the properties: type, tags or endpoint are supplied then process accordingly;
            // else move to the resource query directly.
            if (joinOperations.size() > 0) {
                OperationJoin.create(joinOperations)
                        .setCompletion((ops, exs) -> {
                            if (exs != null) {
                                exs.values()
                                        .forEach(ex -> logWarning(() -> String.format("Error: %s",
                                                ex.getMessage())));
                                handleError(state, exs.values().iterator().next());
                                return;
                            }
                            for (Operation o : ops.values()) {
                                QueryTask resultTask = o.getBody(QueryTask.class);

                                // since no results are returned for a given query task and it is not one of the ORed conditions
                                //then return 0 results and exit the state machine.
                                if (resultTask.results.documentCount == 0L
                                        && !ORedTagQueryTaskLinks
                                        .contains(resultTask.documentSelfLink)) {
                                    ServiceDocumentQueryResult results = new ServiceDocumentQueryResult();
                                    results.documentCount = resultTask.results.documentCount;
                                    state.results = results;
                                    state.taskInfo.stage = TaskStage.FINISHED;
                                    sendSelfPatch(state);
                                    break;
                                } else {
                                    // If some results are returned check what kind of advanced
                                    // query it corresponds to and set the appropriate links to
                                    // be used downstream.
                                    // If queryTask is for a type filter and the results are of TagState,
                                    // proceed with type filtering.
                                    // If only results are TagState, proceed with tag filtering
                                    // else proceed with endpoint filtering
                                    if (typeQueryTaskLinks.contains(resultTask.documentSelfLink)
                                            && Utils.toJson(resultTask.querySpec).contains(
                                            TagState.class.getSimpleName())) {
                                        state.typeTagLinks.add(new HashSet<>(
                                                resultTask.results.documentLinks));
                                    } else if (Utils.toJson(resultTask.querySpec)
                                            .contains(TagState.class.getSimpleName())) {
                                        if (ORedTagQueryTaskLinks
                                                .contains(resultTask.documentSelfLink)) {
                                            state.tagLinksWithORCondition
                                                    .addAll(resultTask.results.documentLinks);
                                        } else {
                                            state.tagLinks.add(new HashSet<>(
                                                    resultTask.results.documentLinks));
                                        }

                                    } else if (Utils.toJson(resultTask.querySpec)
                                            .contains(EndpointState.class.getSimpleName())) {
                                        state.endpointLinks = new HashSet<>(
                                                resultTask.results.documentLinks);
                                    }
                                }
                            }
                            state.subStage = nextStage;
                            sendSelfPatch(state);
                            return;
                        })
                        .sendWith(this);
                return;
            }
            // If no filter is supplied simply move to the next stage
            state.subStage = nextStage;
            sendSelfPatch(state);
        } catch (Exception e) {
            logWarning("Error %s encountered while processing options for advanced filtering",
                    e.getMessage());
            handleError(state, e);
        }

    }

    private void queryResources(ResourceQueryTaskState state) {
        QueryTask queryTask = null;
        try {
            queryTask = buildResourcesQueryTask(state);
        } catch (Exception e) {
            logWarning("Error %s encountered in building resources query task ", e.getMessage());
            handleError(state, e);
        }
        URI queryUri = UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS);
        Operation queryOp = Operation
                .createPost(queryUri)
                .setBody(queryTask)
                .setConnectionSharing(true)
                .setCompletion((o, f) -> {
                    try {
                        if (f != null) {
                            throw f;
                        }
                        QueryTask task = o.getBody(QueryTask.class);

                        ServiceDocumentQueryResult queryResult = task.results;
                        ServiceDocumentQueryResult results = new ServiceDocumentQueryResult();
                        results.documentCount = queryResult.documentCount;
                        results.documentLinks = queryResult.documentLinks;

                        if (queryResult.nextPageLink != null && !queryResult.nextPageLink
                                .isEmpty()) {
                            ResourcesQueryPageServiceV3 pageService = new ResourcesQueryPageServiceV3(
                                    queryResult.nextPageLink,
                                    state.documentExpirationTimeMicros,
                                    state.tenantLinks);

                            results.nextPageLink = QueryHelper.startStatelessPageService(this,
                                    ResourcesQueryPageServiceV3.SELF_LINK_PREFIX,
                                    pageService,
                                    state.documentExpirationTimeMicros,
                                    failure -> handleError(state, failure));
                        }

                        if (queryResult.prevPageLink != null && !queryResult.prevPageLink
                                .isEmpty()) {
                            ResourcesQueryPageServiceV3 pageService = new ResourcesQueryPageServiceV3(
                                    queryResult.prevPageLink,
                                    state.documentExpirationTimeMicros,
                                    state.tenantLinks);

                            results.prevPageLink = QueryHelper.startStatelessPageService(this,
                                    ResourcesQueryPageServiceV3.SELF_LINK_PREFIX,
                                    pageService,
                                    state.documentExpirationTimeMicros,
                                    failure -> handleError(state, failure));
                        }

                        state.results = results;
                        state.taskInfo.stage = TaskStage.FINISHED;
                        sendSelfPatch(state);
                    } catch (Throwable t) {
                        handleError(state, t);
                    }
                });
        sendRequest(queryOp);
    }

    private QueryTask buildResourcesQueryTask(ResourceQueryTaskState state) {
        QuerySpecification querySpec;
        querySpec = buildQuerySpecification(state);

        // Don't set result limit for count queries
        if (querySpec.resultLimit == null
                && !querySpec.options.contains(QueryOption.COUNT)) {
            querySpec.resultLimit = QUERY_RESULT_LIMIT;
        }
        querySpec.options.add(QueryOption.INDEXED_METADATA);

        querySpec.query.addBooleanClause(
                Query.Builder.create()
                        .addInClause(ServiceDocument.FIELD_NAME_KIND, RESOURCE_TYPES)
                        .build());

        // Add filtering based on endpoint links based on the provided filtering criteria.
        // If the filtering criteria was supplied but no match was found; then the main
        // resource query needs to account for that while building the additional filtering
        // clause.
        if (state.endpointLinks != null) {
            querySpec.query.addBooleanClause(Query.Builder.create()
                    .addInClause(ComputeState.FIELD_NAME_ENDPOINT_LINK, state.endpointLinks)
                    .build());
        }

        // Add the tags criteria if supplied. Each set of tags in the list correspond to a
        // particular condition.
        if (state.tagLinks != null) {
            for (Set<String> tagSet : state.tagLinks) {
                querySpec.query.addBooleanClause(Query.Builder.create()
                        .addInCollectionItemClause(ComputeState.FIELD_NAME_TAG_LINKS, tagSet,
                                Occurance.MUST_OCCUR)
                        .build());
            }
        }
        //If the tagLinks with the OR condition exist add them to the final query for evaluation.
        if (state.tagLinksWithORCondition != null && state.tagLinksWithORCondition.size() > 0) {
            querySpec.query.addBooleanClause(Query.Builder.create()
                    .addInCollectionItemClause(ComputeState.FIELD_NAME_TAG_LINKS,
                            state.tagLinksWithORCondition,
                            Occurance.MUST_OCCUR)
                    .build());
        }
        if (state.typeTagLinks != null) {
            for (Set<String> type : state.typeTagLinks) {
                querySpec.query.addBooleanClause(Query.Builder.create()
                        .addInCollectionItemClause(ComputeState.FIELD_NAME_TAG_LINKS, type,
                                Occurance.MUST_OCCUR)
                        .build());
            }
        }
        // Filter out the compute types of ZONE, VM_HOST, ENDPOINT_HOST
        // todo remove VM_HOST clause when VSYM-10679 is resolved
        Query hostTypeQuery = Query.Builder
                .create(Occurance.SHOULD_OCCUR)
                .addFieldClause(ComputeState.FIELD_NAME_TYPE, VM_HOST.name())
                .build();

        Query zoneTypeQuery = Query.Builder
                .create(Occurance.SHOULD_OCCUR)
                .addFieldClause(ComputeState.FIELD_NAME_TYPE, ZONE.name())
                .build();

        Query endpointHostTypeQuery = Query.Builder
                .create(Occurance.SHOULD_OCCUR)
                .addFieldClause(ComputeState.FIELD_NAME_TYPE, ENDPOINT_HOST.name())
                .build();

        Query typeQuery = Query.Builder
                .create(Occurance.MUST_NOT_OCCUR)
                .addClauses(hostTypeQuery, zoneTypeQuery, endpointHostTypeQuery)
                .build();

        querySpec.query.addBooleanClause(typeQuery);

        // the request is for getting list by specific tenant
        if (state.tenantLink != null) {
            querySpec.query.addBooleanClause(Query.Builder.create()
                    .addCollectionItemClause(ResourceState.FIELD_NAME_TENANT_LINKS, state.tenantLink)
                    .build());
        }

        // Exclude compute types of with the customProperty "isDeleted" set to true
        // (applies only for private cloud resources)
        Query customPropertyQuery = Query.Builder
                .create(Occurance.MUST_NOT_OCCUR)
                .addCompositeFieldClause(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                        CUSTOM_PROPERTY_IS_DELETED, Boolean.TRUE.toString())
                .build();
        querySpec.query.addBooleanClause(customPropertyQuery);

        QueryTask.Builder builder = QueryTask.Builder.createDirectTask().setQuery(querySpec.query);

        // We always want to expand UNLESS the client specifically is only interested in a count
        if (querySpec.options.contains(QueryOption.COUNT)) {
            builder.addOption(QueryOption.COUNT);
        } else {
            builder.addOption(QueryOption.EXPAND_CONTENT)
                    .addOption(QueryOption.SELECT_LINKS)
                    .addOption(QueryOption.INDEXED_METADATA)
                    .addOption(QueryOption.EXPAND_LINKS)
                    .addLinkTerm(ComputeState.FIELD_NAME_ENDPOINT_LINK)
                    .addLinkTerm(ResourceState.FIELD_NAME_TAG_LINKS)
                    .addLinkTerm(NetworkInterfaceState.FIELD_NAME_SUBNET_LINK);
        }

        if (querySpec.resultLimit != null) {
            builder.setResultLimit(querySpec.resultLimit);
        }

        if (state.filter != null
                && (state.filter.sortOrder != null || state.filter.sortTerm != null)) {
            try {
                validateSortingParametersInFilter(state.filter);
            } catch (Exception e) {
                handleError(state, e);
            }
            if (state.filter.sortOrder.equals(
                    SortOrder.ASC)) {
                builder.orderAscending(state.filter.sortTerm.propertyName,
                        state.filter.sortTerm.propertyType);
            } else {
                builder.orderDescending(state.filter.sortTerm.propertyName,
                        state.filter.sortTerm.propertyType);
            }
        }

        QueryTask task = builder.build();
        task.tenantLinks = state.tenantLinks;
        return task;
    }

    /**
     * Validates that the passed in filter parameter has all the required sort parameters set
     * correctly
     */
    private void validateSortingParametersInFilter(QuerySpecification filter) throws Exception {
        if ((filter.sortOrder == null) || (filter.sortTerm == null)
                || (filter.sortTerm.propertyName == null)
                || (filter.sortTerm.propertyType == null)) {
            throw new IllegalArgumentException("The provided sort options are incorrect.");
        }
    }

    /**
     * If {@code state.filter} was provided by the user, then use it as a basis for the query we
     * send in our QueryTask. NOTE: We do not forward the query supplied by the user verbatim; we
     * extract supported filter fields and only send those. In case there is a match found for the
     * advanced query filter we build the appropriate query to filter on the attribute in the
     * related document.
     *
     * @param tagsMap
     */
    private List<QueryTask> buildQueryForAdvancedFiltering(ResourceQueryTaskState state,
            AdvancedFilteringOption option, Map<String, String> tagsMap) {
        List<QueryTask> advancedQueryTasks = new ArrayList<QueryTask>();
        if (state.filter != null) {
            switch (option) {
            case ENDPOINT:
                createEndpointQueryTask(state, advancedQueryTasks);
                break;
            case TAGS:
                createTagsQueryTasks(state, advancedQueryTasks, tagsMap);
                break;
            case TYPE:
                createTypeQueryTask(state, advancedQueryTasks);
                break;
            default:
                logWarning("Unexpected advanced filter option: %s", option);
                break;
            }
        }
        return advancedQueryTasks;
    }

    /**
     * Creates query tasks to support the filtering criteria for tags.For every tag clause that is
     * for a different type of tag that is detected fire off a separate query else append that to
     * the same query being generated. For e.g. name=some* and name = *thing and category=random
     * will be translated into two separate queries. One with name=some* and name = *thing while the
     * other one will be a separate query for category=random. This logic is implemented by keeping
     * track of every tag type that is encountered in a hashmap and lookup is performed to determine
     * if the created query clause needs to be appended to an existing query task or a new query
     * task needs to be created.
     *
     * @param state
     * @param advancedQueryTasks
     * @param tagsMap
     * @param tagsQueryMap
     */
    private void createTagsQueryTasks(ResourceQueryTaskState state,
            List<QueryTask> advancedQueryTasks,
            Map<String, String> tagsMap) {

        QueryTask advancedFilterQueryTask;
        Map<String, Query.Builder> tagAttributeAndQueryBuilderMap = new HashMap<String, Query.Builder>();
        FilterUtils.findPropertyNameMatch(state.filter.query,
                SUPPORTED_TAGS_FIELDS_FILTER_MAP.keySet(),
                query -> {
                    try {
                        validateTagParametersInFilter(query);
                    } catch (Exception e) {
                        handleError(state, e);
                        return;
                    }
                    AdvancedFilterDefinition filterDefinition = SUPPORTED_TAGS_FIELDS_FILTER_MAP
                            .get(query.term.propertyName);
                    String[] tagAttributes = filterDefinition.attributeName
                            .split(TAG_DELIMITER);
                    String[] tagValues = query.term.matchValue
                            .split(TAG_DELIMITER);
                    // The filter format for tags is defined as "key=value".The passed in value for
                    // tags filter is expected to be of type "tagName=tagValue". The format and
                    // values are split with the logic below to create a query task that
                    // tries to perform a lookup on Tag states with the
                    // criteria "key=tagName" and "value=tagValue". The value query is created here
                    // and the key query is created once for the entire context.

                    // Create a query term for the value instead of a query with boolean clauses to
                    // simplify the occurances in the net query.
                    Query constructedQuery = createQuery(tagAttributes[1], tagValues[1],
                            query.term.matchType,
                            query.occurance);

                    // Logic to determine if a given query clause needs to be appended to an
                    // existing QueryTask or warrants a new one.
                    Query.Builder transformedTagsQueryBuilder = null;
                    if (tagAttributeAndQueryBuilderMap.containsKey(tagValues[0])) {
                        transformedTagsQueryBuilder = tagAttributeAndQueryBuilderMap
                                .get(tagValues[0]);
                        transformedTagsQueryBuilder.addClause(constructedQuery);
                    } else {
                        transformedTagsQueryBuilder = Query.Builder.create();
                        // kind clause needs to be appended only once
                        transformedTagsQueryBuilder.addKindFieldClause(TagState.class);
                        // add clause to filter internal tag states - this is added only once
                        Query externalTagQuery = createQuery(TagState.FIELD_NAME_EXTERNAL,
                                Boolean.TRUE.toString(),
                                null, Occurance.MUST_OCCUR);
                        transformedTagsQueryBuilder.addClause(externalTagQuery);
                        // key clause needs to be appended only once and should be a must occur
                        Query keyConstructedQuery = createQuery(tagAttributes[0], tagValues[0],
                                query.term.matchType,
                                Occurance.MUST_OCCUR);
                        transformedTagsQueryBuilder.addClause(keyConstructedQuery);
                        // Add the clause for the value constructed above
                        transformedTagsQueryBuilder.addClause(constructedQuery);
                        tagAttributeAndQueryBuilderMap.put(tagValues[0],
                                transformedTagsQueryBuilder);
                    }
                });
        for (Map.Entry<String, Query.Builder> entry : tagAttributeAndQueryBuilderMap.entrySet()) {
            String key = entry.getKey();
            Query.Builder queryBuilder = entry.getValue();
            advancedFilterQueryTask = QueryTask.Builder.createDirectTask()
                    .setQuery(queryBuilder.build()).build();
            if (advancedFilterQueryTask != null) {
                advancedFilterQueryTask.tenantLinks = state.tenantLinks;
                advancedFilterQueryTask.documentSelfLink = UUID.randomUUID().toString();
                // In the generated query look for all occurrences of SHOULD_OCCUR clauses and have
                // that surrounded by a MUST_OCCUR
                FilterUtils.transformOrClauses(advancedFilterQueryTask.querySpec);
                advancedQueryTasks.add(advancedFilterQueryTask);
                tagsMap.put(key, advancedFilterQueryTask.documentSelfLink);
            }
        }
    }

    /**
     * Creates a query task to support filtering for type. As the type information is embedded
     * in a tag whose link is part of the resource; an additional query is fired to check for
     * tags that match the given type.
     *
     * @param state
     * @param advancedQueryTasks
     */
    private void createTypeQueryTask(ResourceQueryTaskState state,
            List<QueryTask> advancedQueryTasks) {
        QueryTask advancedFilterQueryTask;
        Query.Builder transformedQueryBuilder = Query.Builder.create();

        // Processes the filter spec to replace the filter strings with only
        // the supported filter strings. In addition honors the passed in grouping
        // of the SHOULD_OCCUR and MUST_OCCUR clauses.
        FilterUtils.transformQuerySpec(state.filter.query, SUPPORTED_TYPE_FILTER_MAP,
                new ArrayList<>(SUPPORTED_TYPE_FILTER_MAP.keySet()), null, query -> {
                    transformedQueryBuilder.addClause(query);
                });
        transformedQueryBuilder.addKindFieldClause(TagState.class);
        Query internalTagQuery = createQuery(TagState.FIELD_NAME_EXTERNAL,
                Boolean.FALSE.toString(), null, Occurance.MUST_OCCUR);
        transformedQueryBuilder.addClause(internalTagQuery);
        // key clause needs to be appended only once and should be a must occur
        Query keyConstructedQuery = createQuery(TAG_KEY, TAG_KEY_TYPE,
                MatchType.TERM, Occurance.MUST_OCCUR);
        transformedQueryBuilder.addClause(keyConstructedQuery);
        advancedFilterQueryTask = QueryTask.Builder.createDirectTask()
                .setQuery(transformedQueryBuilder.build()).build();
        if (advancedFilterQueryTask != null) {
            advancedFilterQueryTask.tenantLinks = state.tenantLinks;
        }
        advancedQueryTasks.add(advancedFilterQueryTask);
    }

    /**
     * Creates a query task to support filtering for endpoint attributes.
     *
     * @param state
     * @param advancedQueryTasks
     */
    private void createEndpointQueryTask(ResourceQueryTaskState state,
            List<QueryTask> advancedQueryTasks) {
        QueryTask advancedFilterQueryTask;
        Query.Builder transformedQueryBuilder = Query.Builder.create();
        FilterUtils.findPropertyNameMatch(state.filter.query,
                SUPPORTED_ENDPOINT_FIELDS_FILTER_MAP.keySet(),
                query -> {
                    if (query.term.propertyName
                            .equals(ResourceViewState.FIELD_NAME_CLOUD_ACCOUNT_TYPE)
                            && query.term.matchValue.equalsIgnoreCase(vsphere.name())) {
                        query.term.matchValue = VSPHERE_ON_PREM_ADAPTER;
                    }
                });
        // transform to map the resource view fields to the actual document fields

        // Processes the filter spec to replace the filter strings with only
        // the supported filter strings. In addition honors the passed in grouping
        // of the SHOULD_OCCUR and MUST_OCCUR clauses.
        FilterUtils.transformQuerySpec(state.filter.query, SUPPORTED_ENDPOINT_FIELDS_FILTER_MAP,
                new ArrayList<>(SUPPORTED_ENDPOINT_FIELDS_FILTER_MAP.keySet()), null, query -> {
                    transformedQueryBuilder.addClause(query);
                });
        transformedQueryBuilder.addKindFieldClause(EndpointState.class);
        advancedFilterQueryTask = QueryTask.Builder.createDirectTask()
                .setQuery(transformedQueryBuilder.build()).build();
        if (advancedFilterQueryTask != null) {
            advancedFilterQueryTask.tenantLinks = state.tenantLinks;
        }
        advancedQueryTasks.add(advancedFilterQueryTask);
    }

    /**
     * Validates that the passed in filter parameter has all the required tag filtering parameters
     * set correctly
     */
    public void validateTagParametersInFilter(Query query) throws Exception {
        if (query.term.matchValue == null) {
            throw new IllegalArgumentException("The tag match value needs to be specified");
        }
        if (!query.term.matchValue.contains(TAG_DELIMITER)) {
            throw new IllegalArgumentException(
                    "The provided tag value does not contain the required delimiter: "
                            + TAG_DELIMITER);
        } else {
            String[] split = query.term.matchValue.split(TAG_DELIMITER);
            if (split.length != 2 || split[0].equals(EMPTY_STRING)
                    || split[1].equals(EMPTY_STRING)) {
                throw new IllegalArgumentException(
                        "The provided tag value is not in the correct format. Expected format is: "
                                + TAG_PATTERN);
            }
        }
    }

    /**
     * If {@code state.filter} was provided by the user, then use it as a basis for the query we
     * send in our QueryTask. NOTE: We do not forward the query supplied by the user verbatim; we
     * extract supported filter fields and only send those.
     */
    private QuerySpecification buildQuerySpecification(ResourceQueryTaskState state) {
        QuerySpecification querySpec;
        if (state.filter != null) {
            // use the provided querySpec as a base, we will overwrite sensitive
            // field anyway.
            querySpec = state.filter;
            Query.Builder transformedQueryBuilder = Query.Builder.create();
            // Processes the filter spec to replace the filter strings with only
            // the supported filter strings. In addition honors the passed in grouping
            // of the SHOULD_OCCUR and MUST_OCCUR clauses.
            FilterUtils.transformQuerySpec(querySpec.query, SUPPORTED_FILTER_MAP,
                    CASE_INSENSITIVE_FIELDS_LIST, null, query -> {
                        transformedQueryBuilder.addClause(query);
                    });
            Query transformedQuery = transformedQueryBuilder.build();
            querySpec.query = transformedQuery;

            // Since we show one category for Network, Subnet, and NetworkInterface we need
            // to adjust the category clause to that
            adjustDocumentKindClause(querySpec);
        } else {
            querySpec = new QuerySpecification();
        }
        return querySpec;

    }

    private void handleError(ResourceQueryTaskState state, Throwable ex) {
        logSevere("Failed at sub-stage %s with exception: %s", state.subStage,
                Utils.toString(ex));
        sendSelfFailurePatch(state, ex.getLocalizedMessage());
    }

    private void adjustDocumentKindClause(QuerySpecification spec) {
        FilterUtils.findPropertyNameMatch(spec.query, Arrays.asList(FIELD_NAME_DOCUMENT_KIND),
                query -> {
                    if (query.term.matchValue.equals(ABBREVIATED_NETWORK_DOC_KIND)) {
                        Query.Builder queryBuilder = Query.Builder.create();

                        // networkState clause
                        queryBuilder.addFieldClause(FIELD_NAME_DOCUMENT_KIND,
                                getFullDocKindValue(ABBREVIATED_NETWORK_DOC_KIND),
                                QueryTask.QueryTerm.MatchType.TERM,
                                Occurance.SHOULD_OCCUR);

                        // SecurityGroupState clause
                        queryBuilder.addFieldClause(FIELD_NAME_DOCUMENT_KIND,
                                getFullDocKindValue(ABBREVIATED_SECURITY_GROUP_DOC_KIND),
                                QueryTask.QueryTerm.MatchType.TERM,
                                Occurance.SHOULD_OCCUR);

                        // subnetState clause
                        queryBuilder.addFieldClause(FIELD_NAME_DOCUMENT_KIND,
                                getFullDocKindValue(ABBREVIATED_SUBNET_DOC_KIND),
                                QueryTask.QueryTerm.MatchType.TERM,
                                Occurance.SHOULD_OCCUR);

                        // networkInterfaceState clause
                        queryBuilder.addFieldClause(FIELD_NAME_DOCUMENT_KIND,
                                getFullDocKindValue(ABBREVIATED_NET_INTERF_DOC_KIND),
                                QueryTask.QueryTerm.MatchType.TERM,
                                Occurance.SHOULD_OCCUR);

                        query.addBooleanClause(queryBuilder.build());
                    } else {
                        query.term.matchValue = getFullDocKindValue(query.term.matchValue);
                    }
                });
    }

    private String getFullDocKindValue(String shortKind) {
        if (!shortKind.startsWith(DOC_KIND_PREFIX)) {
            return DOC_KIND_PREFIX + shortKind;
        }

        return shortKind;
    }

    private Query createQuery(String propertyName, String propertyValue,
            QueryTask.QueryTerm.MatchType matchType, Occurance occurance) {
        Query query = Query.Builder.create().build();
        query.setTermPropertyName(propertyName);
        query.setCaseInsensitiveTermMatchValue(propertyValue);
        query.setTermMatchType(matchType);
        query.setOccurance(occurance);

        return query;
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument d = super.getDocumentTemplate();
        d.documentDescription.name = "Resources List";
        d.documentDescription.description = "Query for lists of resources";

        return d;
    }
}
