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

package com.vmware.photon.controller.discovery.cloudaccount;

import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountQueryTaskService.SubStage.PROCESS_TAGS_FILTER;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountQueryTaskService.SubStage.QUERY_ENDPOINTS;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountQueryTaskService.SubStage.SUCCESS;
import static com.vmware.photon.controller.discovery.cloudaccount.utils.QueryUtils.createQuery;
import static com.vmware.photon.controller.discovery.common.utils.InventoryQueryUtils.createInventoryQueryTaskOperation;
import static com.vmware.photon.controller.discovery.common.utils.InventoryQueryUtils.startInventoryQueryTask;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_VALUE_SERVICE_TAG;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.VSPHERE_ON_PREM_ADAPTER;
import static com.vmware.photon.controller.model.UriPaths.CLOUD_ACCOUNT_QUERY_TASK_SERVICE;
import static com.vmware.photon.controller.model.UriPaths.PROPERTY_PREFIX;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType.vsphere;
import static com.vmware.photon.controller.model.resources.TagService.TagState.TagOrigin.DISCOVERED;
import static com.vmware.photon.controller.model.resources.TagService.TagState.TagOrigin.SYSTEM;
import static com.vmware.photon.controller.model.resources.TagService.TagState.TagOrigin.USER_DEFINED;
import static com.vmware.xenon.services.common.QueryTask.QuerySpecification.buildCompositeFieldName;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.vmware.photon.controller.discovery.cloudaccount.CloudAccountApiService.CloudAccountViewState;
import com.vmware.photon.controller.discovery.cloudaccount.CloudAccountQueryTaskService.CloudAccountQueryTaskState;
import com.vmware.photon.controller.discovery.cloudaccount.utils.FilterUtils;
import com.vmware.photon.controller.discovery.cloudaccount.utils.QueryHelper;
import com.vmware.photon.controller.discovery.cloudaccount.utils.QueryUtils;
import com.vmware.photon.controller.discovery.common.ResourceProperties;
import com.vmware.photon.controller.discovery.common.ResourceProperty;
import com.vmware.photon.controller.discovery.common.services.OrganizationService;
import com.vmware.photon.controller.discovery.common.services.UserContextQueryService;
import com.vmware.photon.controller.discovery.common.services.UserContextQueryService.UserContext;
import com.vmware.photon.controller.discovery.onboarding.OnboardingUtils;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.RequestRouter.Route;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceDocumentDescription.TypeName;
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
import com.vmware.xenon.services.common.QueryTask.QueryTerm;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskFactoryService;
import com.vmware.xenon.services.common.TaskService;

/**
 * Task service to retrieve cloud account information in an API-friendly way. The task service processes
 * incoming requests and executes appropriate query. A successful task completion populates the
 * results field in {@link CloudAccountQueryTaskState}. The clients can do GET operation on the
 * next/prev page links to get more results.
 */
public class CloudAccountQueryTaskService extends TaskService<CloudAccountQueryTaskState> {
    public static final String FACTORY_LINK = CLOUD_ACCOUNT_QUERY_TASK_SERVICE;

    public static final String PROPERTY_NAME_QUERY_RESULT_LIMIT =
            PROPERTY_PREFIX + "CloudAccountQueryTaskService.QUERY_RESULT_LIMIT";

    public static final String TAG_DELIMITER = "=";
    public static final String TAG_VALUE = "value";
    public static final String TAG_KEY = "key";
    public static final String TAG_PATTERN = TAG_KEY + TAG_DELIMITER + TAG_VALUE;
    public static final String EMPTY_STRING = "";

    private static final String QUERY_FIELD_NAME_ORG_ID = buildCompositeFieldName(
            CloudAccountViewState.FIELD_NAME_ORG, OrganizationViewState.FIELD_NAME_ORG_ID);

    private static final String QUERY_FIELD_NAME_ORG_LINK = buildCompositeFieldName(
            CloudAccountViewState.FIELD_NAME_ORG, OrganizationViewState.FIELD_NAME_ORG_LINK);

    /**
     * The keys to this map are the supported filter propertyNames (based off the API-friendly
     * model). The values are the photon-model related propertyNames that we should use when
     * executing the "actual" query task against the Xenon index.
     */
    public static final Map<String, String> SUPPORTED_FILTER_MAP;

    /**
     * The keys to this map are the supported sort propertyNames (based off the API-friendly
     * model), in similar fashion to {@link #SUPPORTED_FILTER_MAP}.
     */
    public static final Map<String, String> SUPPORTED_SORT_MAP;

    /**
     * The values in the set are those propertyNames that will require special handling
     */
    public static final Set<String> SUPPORTED_SPECIAL_HANDLING_SET;

    /**
     * The values in the set are those propertyNames that will require case insensitive handling
     */
    public static final Set<String> SUPPORTED_CASE_INSENSITIVE_SET;

    public static final ResourceProperties CLOUD_ACCOUNTS_PROPERTIES;

    public static final Query.Builder ORIGIN_CLAUSE_BUILDER = Query.Builder.create();

    static {
        Map<String, String> filterMap = new HashMap<>();
        Map<String, String> sortMap = new HashMap<>();
        Set<String> specialHandlingSet = new HashSet<>();
        Set<String> caseInsensitiveSet = new HashSet<>();
        List<ResourceProperty> propertiesList = new ArrayList<>();

        for (CloudAccountProperty cloudAccountsProperty : CloudAccountProperty.values()) {
            ResourceProperty cloudAccountResourceProperty = new ResourceProperty(
                    cloudAccountsProperty.getFieldName(), cloudAccountsProperty.getType());

            cloudAccountResourceProperty.isFilterable = cloudAccountsProperty.isFilterable();
            if (cloudAccountResourceProperty.isFilterable) {
                filterMap.put(cloudAccountsProperty.getFieldName(), cloudAccountsProperty.getTranslatedName());
            }

            cloudAccountResourceProperty.isSortable = cloudAccountsProperty.isSortable();
            if (cloudAccountResourceProperty.isSortable) {
                sortMap.put(cloudAccountsProperty.getFieldName(), cloudAccountsProperty.getTranslatedName());
            }

            if (cloudAccountsProperty.isSpecialHandling()) {
                specialHandlingSet.add(cloudAccountsProperty.getFieldName());
            }

            if (cloudAccountsProperty.isCaseInsensitive()) {
                caseInsensitiveSet.add(cloudAccountsProperty.getFieldName());
            }

            propertiesList.add(cloudAccountResourceProperty);
        }

        SUPPORTED_FILTER_MAP = Collections.unmodifiableMap(filterMap);
        SUPPORTED_SORT_MAP = Collections.unmodifiableMap(sortMap);
        SUPPORTED_SPECIAL_HANDLING_SET = Collections.unmodifiableSet(specialHandlingSet);
        SUPPORTED_CASE_INSENSITIVE_SET = Collections.unmodifiableSet(caseInsensitiveSet);

        CLOUD_ACCOUNTS_PROPERTIES = new ResourceProperties();
        CLOUD_ACCOUNTS_PROPERTIES.results = Collections.unmodifiableList(propertiesList);
        CLOUD_ACCOUNTS_PROPERTIES.documentCount = CLOUD_ACCOUNTS_PROPERTIES.results.size();

        // only include tags that have origin "DISCOVERED" and "USER_DEFINED",
        // ignore "SYSTEM" ones
        Map<String, Occurance> origin = new HashMap<>();
        origin.put(DISCOVERED.toString(), Occurance.SHOULD_OCCUR);
        origin.put(USER_DEFINED.toString(), Occurance.SHOULD_OCCUR);
        origin.put(SYSTEM.toString(), Occurance.MUST_NOT_OCCUR);

        for (Map.Entry<String, Occurance> entry : origin.entrySet()) {
            Occurance occurance = entry.getValue() == null ? Occurance.MUST_OCCUR : entry.getValue();
            if (entry.getKey() != null) {
                ORIGIN_CLAUSE_BUILDER.addCollectionItemClause(TagService.TagState.FIELD_NAME_ORIGIN,
                        entry.getKey(), occurance);
            }
        }
    }

    /** The state for the endpoint query task service. */
    public static class CloudAccountQueryTaskState extends TaskService.TaskServiceState {

        @Documentation(description = "The query specification for filtering cloud accounts")
        public QuerySpecification filter;

        @Documentation(description = "This is only for internal usage. It keeps track of internal stages")
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public SubStage subStage;

        @Documentation(description = "The cloud account result set")
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public ServiceDocumentQueryResult results;

        @Documentation(description = "The authorization links associated with this request.")
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public List<String> tenantLinks;

        @Documentation(description = "Helper property to maintain internal filtering logic.")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public List<Set<String>> tagLinks;

        @Documentation(description = "Helper property to maintain internal filtering logic.")
        @UsageOptions({
                @UsageOption(option = PropertyUsageOption.SERVICE_USE),
                @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) })
        public Set<String> tagLinksWithORCondition;
    }

    /** Substages for task processing. */
    enum SubStage {
        /** stage to perform necessary queries for collecting tagLinks that apply to the query terms*/
        PROCESS_TAGS_FILTER,

        /** Stage to query the internal {@link EndpointState} service documents. */
        QUERY_ENDPOINTS,

        /** Stage to indicate success. */
        SUCCESS
    }

    public static FactoryService createFactory() {
        TaskFactoryService fs = new TaskFactoryService(CloudAccountQueryTaskState.class) {
            @Override
            public Service createServiceInstance() throws Throwable {
                return new CloudAccountQueryTaskService();
            }
        };
        fs.setPeerNodeSelectorPath(ServiceUriPaths.DEFAULT_1X_NODE_SELECTOR);
        return fs;
    }

    public CloudAccountQueryTaskService() {
        super(CloudAccountQueryTaskState.class);
        toggleOption(ServiceOption.REPLICATION, true);
        toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handleStart(Operation taskOperation) {
        CloudAccountQueryTaskState state;
        if (!taskOperation.hasBody()) {
            state = new CloudAccountQueryTaskState();
            taskOperation.setBody(state);
        }

        CloudAccountQueryTaskState body = taskOperation.getBody(CloudAccountQueryTaskState.class);

        if (taskOperation.getAuthorizationContext().isSystemUser()) {
            super.handleStart(taskOperation);
            return;
        } else {
            Operation.createGet(this, UserContextQueryService.SELF_LINK)
                    .setCompletion((op, ex) -> {
                        if (ex != null) {
                            taskOperation.fail(ex);
                            return;
                        }

                        UserContext userContext = op.getBody(UserContext.class);
                        List<String> userTenantLinks = userContext.projects.stream()
                                .map(project -> project.documentSelfLink)
                                .collect(Collectors.toList());

                        if (body.tenantLinks != null && !body.tenantLinks.isEmpty()) {
                            if (!userTenantLinks.containsAll(body.tenantLinks)) {
                                taskOperation.fail(new IllegalArgumentException("User does not" +
                                        " have access to all 'tenantLinks' provided"));
                                return;
                            }
                        } else {
                            body.tenantLinks = userTenantLinks;
                        }

                        taskOperation.setBody(body);
                        super.handleStart(taskOperation);
                    })
                    .sendWith(this);
        }
    }

    @Override
    protected void initializeState(CloudAccountQueryTaskState task, Operation taskOperation) {
        task.subStage = PROCESS_TAGS_FILTER;
        task.tagLinks = new ArrayList<>();
        task.tagLinksWithORCondition = new HashSet<>();
        if (task.taskInfo == null) {
            task.taskInfo = TaskState.create();
        }
        super.initializeState(task, taskOperation);
    }

    @Override
    public void handlePatch(Operation patch) {
        CloudAccountQueryTaskState currentTask = getState(patch);
        CloudAccountQueryTaskState patchBody = getBody(patch);

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
    private void handleSubStage(CloudAccountQueryTaskState state) {
        switch (state.subStage) {
        case PROCESS_TAGS_FILTER:
            processTagsFilter(state, QUERY_ENDPOINTS);
            break;
        case QUERY_ENDPOINTS:
            queryEndpoints(state, SUCCESS);
            break;
        case SUCCESS:
            sendSelfPatch(state, TaskStage.FINISHED, null);
            break;
        default:
            throw new IllegalStateException("Unknown stage encountered: " + state.subStage);
        }
    }

    /**
     * Method to process the incoming tag querying filters.
     */
    private void processTagsFilter(CloudAccountQueryTaskState state, SubStage nextStage) {
        try {
            List<Operation> joinOperations = new ArrayList<>();

            if (state.filter == null || state.filter.query == null
                    || !FilterUtils.doesQueryMatchGivenFilters(state.filter.query,
                    Collections.singletonList(CloudAccountViewState.FIELD_NAME_TAGS))) {
                state.subStage = nextStage;
                sendSelfPatch(state);
                return;
            }

            // Handle tags transformation
            Map<String, String> tagsMapForKeysAndQueryTaskLink = new HashMap<>();

            // Special handling for OR clause between certain tags.
            //1) Extract all the query clauses that have SHOULD_OCCUR for some of the tag conditions.
            //2) Find the tag key that has the OR supplied. When we create and evaluate the queryTask
            //associated with this tag key we will apply the OR condition with the other results.
            Set<Query> tagsWithORClause = new HashSet<>();
            Set<String> tagKeysWithORClause = new HashSet<>();

            FilterUtils.extractORClausesBasedOnGivenFilters(state.filter.query,
                    Arrays.asList(CloudAccountViewState.FIELD_NAME_TAGS), tagsWithORClause);
            try {
                for (Query clause : tagsWithORClause) {
                    validateTagParametersInFilter(clause);
                    String[] tagValues = clause.term.matchValue.split(TAG_DELIMITER);
                    tagKeysWithORClause.add(tagValues[0]);
                }
            } catch (Exception e) {
                handleError(state, e);
                return;
            }

            List<QueryTask> tagsQueryTaskList = createTagsQueryTasks(state,
                    tagsMapForKeysAndQueryTaskLink);
            // For each kind of tag a separate query is executed and the result is added to the
            // state.
            for (QueryTask tagsTask : tagsQueryTaskList) {
                Operation tagsQueryOp = createInventoryQueryTaskOperation(this, tagsTask);
                joinOperations.add(tagsQueryOp);
            }

            //Segregate all the tag queryTasks that have an OR clause
            List<String> ORedTagQueryTaskLinks = new ArrayList<>();
            for (String ORedKey : tagKeysWithORClause) {
                String queryTaskUUID = tagsMapForKeysAndQueryTaskLink
                        .get(ORedKey);
                ORedTagQueryTaskLinks.add(UriUtils.buildUriPath(
                        ServiceUriPaths.CORE_LOCAL_QUERY_TASKS,
                        queryTaskUUID));
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
                                // since no results are returned for a given query task and it is
                                // not one of the ORed conditions then return 0 results and exit
                                // the state machine.
                                if (resultTask.results.documentCount == 0L
                                        && !ORedTagQueryTaskLinks.contains(resultTask.documentSelfLink)) {
                                    ServiceDocumentQueryResult results = new ServiceDocumentQueryResult();
                                    results.documentCount = resultTask.results.documentCount;
                                    state.results = results;
                                    state.taskInfo.stage = TaskStage.FINISHED;
                                    sendSelfPatch(state);
                                    break;
                                } else {
                                    // if the task is stored in the ORedTagQueryTaskLinks then process
                                    // as an OR term, else it's an AND
                                    if (ORedTagQueryTaskLinks.contains(resultTask.documentSelfLink)) {
                                        state.tagLinksWithORCondition.addAll(resultTask.results.documentLinks);
                                    } else {
                                        state.tagLinks.add(new HashSet<>(resultTask.results.documentLinks));
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
            logWarning("Error %s encountered while processing tag queries",
                    e.getMessage());
            handleError(state, e);
        }
    }

    /**
     * If {@code state.filter} was provided by the user, then use it as a basis for the query we
     * send in our QueryTask. NOTE: We do not forward the query supplied by the user verbatim; we
     * extract supported filter fields and only send those.
     */
    private QuerySpecification buildQuerySpecification(CloudAccountQueryTaskState state) {
        QuerySpecification querySpec;
        if (state.filter != null) {
            querySpec = state.filter;
            logFine(() -> String.format("Initial QuerySpec = %s", Utils.toJson(querySpec)));
            querySpec.query = transformQuery(querySpec.query);
            logFine(() -> String.format("Transformed QuerySpec = %s", Utils.toJson(querySpec)));
        } else {
            querySpec = new QuerySpecification();
        }
        return querySpec;
    }

    private Query transformQuery(Query query) {
        Query.Builder transformedQueryBuilder = Query.Builder.create();
        FilterUtils.findPropertyNameMatch(query, SUPPORTED_FILTER_MAP.keySet(), queryItem -> {
            Query specialHandlingQuery = transformQuerySpecialHandling(queryItem);
            if (specialHandlingQuery != null) {
                transformedQueryBuilder.addClause(specialHandlingQuery);
                return;
            }
            transformMatchValue(queryItem);
            // don't process tags in place - tags clauses need to be reconstructed
            String key = queryItem.term.propertyName;
            if (!key.equals(CloudAccountViewState.FIELD_NAME_TAGS)) {
                queryItem.setTermPropertyName(SUPPORTED_FILTER_MAP.get(key));
            }
            transformedQueryBuilder.addClause(queryItem);
        });
        return transformedQueryBuilder.build();
    }

    private Query transformQuerySpecialHandling(Query query) {
        if (SUPPORTED_SPECIAL_HANDLING_SET.contains(query.term.propertyName)) {
            /*
            For private cloud accounts created with old API, EndpointState.tenantLink has orgLink
            and for public cloud accounts and private cloud accounts created with new API,
            EndpointState.tenantLink has projectLink. For compatibility with old API, we query with
            both orgLink and tenantLink.
             */
            if (query.term.propertyName.equals(QUERY_FIELD_NAME_ORG_LINK)) {
                String matchValue = query.term.matchValue;
                String orgHash = UriUtils.getLastPathSegment(matchValue);
                String orgLink = UriUtils
                        .buildUriPath(OrganizationService.FACTORY_LINK, orgHash);
                String projectLink = OnboardingUtils.getDefaultProjectSelfLink(orgLink);
                List<String> tenantLinks = Arrays.asList(orgLink, projectLink);

                Query.Builder queryBuilder = Query.Builder.create();
                queryBuilder.addInCollectionItemClause(ResourceState.FIELD_NAME_TENANT_LINKS, tenantLinks);
                return queryBuilder.build();
            } else if (query.term.propertyName.equals(CloudAccountViewState.FIELD_NAME_SERVICES)) {
                // Custom property keys are indexed as case insensitive. So implicitly convert the
                // service name to lowercase.
                String serviceName = query.term.matchValue != null ? query.term.matchValue.toLowerCase() : null;

                // Form a key like "customProperties.service_tag:<service_name>"
                String transformedKey = SUPPORTED_FILTER_MAP.get(query.term.propertyName) + serviceName;
                query.setTermPropertyName(transformedKey);
                query.setTermMatchValue(ENDPOINT_CUSTOM_PROPERTY_VALUE_SERVICE_TAG);
                query.setTermMatchType(MatchType.TERM);
                return query;
            } else if (query.term.propertyName.equals(CloudAccountViewState.FIELD_NAME_TAGS)) {
                // In the case of tags we cannot do in place transformation. The term needs to be
                // removed  and then reconstructed.
                query.term = null;
                return query;
            }
        }
        return null;
    }

    /**
     * Creates query tasks to support the filtering criteria for tags. For every tag clause that is
     * for a different type of tag that is detected, fire off a separate query else append that to
     * the same query being generated. For e.g. name=some* and name = *thing and category=random
     * will be translated into two separate queries. One with name=some* and name = *thing while the
     * other one will be a separate query for category=random. This logic is implemented by keeping
     * track of every tag type that is encountered in a hashmap and lookup is performed to determine
     * if the created query clause needs to be appended to an existing query task or a new query
     * task needs to be created.
     * @param state
     * @param tagsMap
     */
    private List<QueryTask> createTagsQueryTasks(CloudAccountQueryTaskState state, Map<String, String> tagsMap) {
        List<QueryTask> advancedQueryTasks = new ArrayList<>();
        QueryTask advancedFilterQueryTask;
        Map<String, Query.Builder> tagAttributeAndQueryBuilderMap = new HashMap<>();
        FilterUtils.findPropertyNameMatch(state.filter.query,
                Arrays.asList(CloudAccountViewState.FIELD_NAME_TAGS),
                query -> {
                    try {
                        validateTagParametersInFilter(query);
                    } catch (Exception e) {
                        handleError(state, e);
                        return;
                    }

                    String[] tagValues = query.term.matchValue.split(TAG_DELIMITER);
                    // The filter format for tags is defined as "key=value". The passed in value for
                    // tags filter is expected to be of type "tagName=tagValue". The format and
                    // values are split with the logic below to create a query task that tries to
                    // perform a lookup on Tag states with the criteria "key=tagName" and
                    // "value=tagValue". The value query is created here and the key query is
                    // created once for the entire context.

                    // Create a query term for the value instead of a query with boolean clauses to
                    // simplify the occurances in the net query.
                    Query constructedQuery = createQuery(TagState.FIELD_NAME_VALUE, tagValues[1],
                            query.term.matchType, query.occurance);

                    // Logic to determine if a given query clause needs to be appended to an
                    // existing QueryTask or warrants a new one.
                    Query.Builder transformedTagsQueryBuilder;
                    if (tagAttributeAndQueryBuilderMap.containsKey(tagValues[0])) {
                        transformedTagsQueryBuilder = tagAttributeAndQueryBuilderMap
                                .get(tagValues[0]);
                        transformedTagsQueryBuilder.addClause(constructedQuery);
                    } else {
                        transformedTagsQueryBuilder = Query.Builder.create();
                        // kind clause needs to be appended only once
                        transformedTagsQueryBuilder.addKindFieldClause(TagState.class);

                        Query originQuery = ORIGIN_CLAUSE_BUILDER.build();
                        transformedTagsQueryBuilder.addClause(originQuery);

                        // key clause needs to be appended only once and should be a must occur
                        Query keyConstructedQuery = createQuery(TagState.FIELD_NAME_KEY,
                                tagValues[0], query.term.matchType,
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
        return advancedQueryTasks;
    }

    private void transformMatchValue(Query query) {
        if (query.term.propertyName.equals(QUERY_FIELD_NAME_ORG_ID)) {
            String matchValue = query.term.matchValue;
            String transformedMatchValue = UriUtils.buildUriPath(EndpointService.FACTORY_LINK,
                    Utils.computeHash(matchValue));
            query.setTermMatchValue(transformedMatchValue);
            query.setTermMatchType(MatchType.PREFIX);
        } else if (query.term.propertyName.equals(CloudAccountViewState.FIELD_NAME_TYPE)
                && query.term.matchValue.equalsIgnoreCase(vsphere.name())) {
            query.term.matchValue = VSPHERE_ON_PREM_ADAPTER;
        } else if (SUPPORTED_CASE_INSENSITIVE_SET.contains(query.term.propertyName)) {
            query.setCaseInsensitiveTermMatchValue(query.term.matchValue);
        }
    }

    private QueryTask buildEndpointsQuery(CloudAccountQueryTaskState state) {
        QuerySpecification querySpec = buildQuerySpecification(state);

        // Add a 'linkTerm' for auth credentials link and tagLinks so we don't have to look it up later
        QueryTerm authCredLinkTerm = new QueryTerm();
        authCredLinkTerm.propertyName = EndpointState.FIELD_NAME_AUTH_CREDENTIALS_LINK;
        authCredLinkTerm.propertyType = TypeName.STRING;
        QueryTerm tagLinksTerm = new QueryTerm();
        tagLinksTerm.propertyName = ResourceState.FIELD_NAME_TAG_LINKS;
        tagLinksTerm.propertyType = TypeName.COLLECTION;
        querySpec.linkTerms = new ArrayList<>();
        querySpec.linkTerms.addAll(Arrays.asList(authCredLinkTerm, tagLinksTerm));

        // We always want to expand UNLESS the client specifically is only interested in a count
        if (!querySpec.options.contains(QueryOption.INDEXED_METADATA)) {
            querySpec.options.add(QueryOption.INDEXED_METADATA);
        }
        if (!querySpec.options.contains(QueryOption.COUNT)) {
            querySpec.options.addAll(
                    Arrays.asList(QueryOption.EXPAND_CONTENT,
                    QueryOption.SELECT_LINKS,
                    QueryOption.EXPAND_LINKS));
            querySpec.resultLimit = QueryUtils.getResultLimit(state.filter, PROPERTY_NAME_QUERY_RESULT_LIMIT);
        }
        querySpec.query.addBooleanClause(
                Query.Builder.create()
                    .addKindFieldClause(EndpointState.class)
                    .build());

        // Add the tags criteria if supplied. Each set of tags in the list correspond to a
        // particular condition.
        if (state.tagLinks != null && !state.tagLinks.isEmpty()) {
            for (Set<String> tagSet : state.tagLinks) {
                querySpec.query.addBooleanClause(Query.Builder.create()
                        .addInCollectionItemClause(EndpointState.FIELD_NAME_TAG_LINKS, tagSet,
                                Occurance.MUST_OCCUR)
                        .build());
            }
        }

        //If the tagLinks with the OR condition exist add them to the final query for evaluation.
        if (state.tagLinksWithORCondition != null && !state.tagLinksWithORCondition.isEmpty()) {
            querySpec.query.addBooleanClause(Query.Builder.create()
                    .addInCollectionItemClause(EndpointState.FIELD_NAME_TAG_LINKS,
                            state.tagLinksWithORCondition,
                            Occurance.MUST_OCCUR)
                    .build());
        }

        if (querySpec.sortTerm != null && querySpec.sortTerm.propertyName != null) {
            if (SUPPORTED_SORT_MAP.containsKey(querySpec.sortTerm.propertyName)) {
                querySpec.sortTerm.propertyName = SUPPORTED_SORT_MAP.get(querySpec.sortTerm.propertyName);
            } else {
                logWarning("Sort term '%s' not supported by cloud accounts. The query will try to proceed, but sorting might not be applied.", querySpec.sortTerm.propertyName);
            }
        }

        // If no sorting was specified by client, default to sorting by endpoint name.
        if (querySpec.sortTerm == null) {
            querySpec.sortTerm = new QueryTask.QueryTerm();
            querySpec.sortTerm.propertyName = EndpointState.FIELD_NAME_NAME;
            querySpec.sortTerm.propertyType = ServiceDocumentDescription.TypeName.STRING;
            querySpec.sortOrder = QuerySpecification.SortOrder.ASC;
            querySpec.options.add(QueryOption.SORT);
        }

        // Scope the query to only those projects that the user is part of
        // TODO: This cannot be enabled, until all the private cloud accounts are migrated to new format
        /*
        if (state.tenantLinks != null && !state.tenantLinks.isEmpty()) {
            querySpec.query.addBooleanClause(
                    Query.Builder.create()
                        .addInCollectionItemClause(
                                EndpointState.FIELD_NAME_TENANT_LINKS,
                                state.tenantLinks)
                            .build());
        }
        */

        logFine(() -> String.format("endpoint QuerySpec = %s", Utils.toJson(querySpec)));
        // In the generated query look for all occurrences of SHOULD_OCCUR clauses and have
        // that surrounded by a MUST_OCCUR
        FilterUtils.transformOrClauses(querySpec);
        QueryTask task = QueryTask.create(querySpec).setDirect(true);
        task.tenantLinks = state.tenantLinks;
        logFine(() -> String.format("endpoint QueryTask = %s", Utils.toJson(task)));
        return task;
    }

    private void queryEndpoints(CloudAccountQueryTaskState state, SubStage nextStage) {
        QueryTask queryTask = null;
        try {
            queryTask = buildEndpointsQuery(state);
        } catch (Exception e) {
            logWarning("Error %s encountered in building the endpoints query task", e.getMessage());
            handleError(state, e);
        }

        startInventoryQueryTask(this, queryTask)
                .whenComplete((task, err) -> {
                    if (err != null) {
                        handleError(state, err);
                        return;
                    }

                    ServiceDocumentQueryResult queryResult = task.results;

                    ServiceDocumentQueryResult results = new ServiceDocumentQueryResult();
                    results.documentCount = queryResult.documentCount;

                    if (queryResult.nextPageLink != null && !queryResult.nextPageLink.isEmpty()) {
                        CloudAccountQueryPageService pageService = new CloudAccountQueryPageService(
                                queryResult.nextPageLink,
                                state.documentExpirationTimeMicros,
                                state.tenantLinks);

                        results.nextPageLink = QueryHelper.startStatelessPageService(this,
                                CloudAccountQueryPageService.SELF_LINK_PREFIX,
                                pageService,
                                state.documentExpirationTimeMicros,
                                failure -> handleError(state, failure));
                    }

                    if (queryResult.prevPageLink != null && !queryResult.prevPageLink.isEmpty()) {
                        CloudAccountQueryPageService pageService = new CloudAccountQueryPageService(
                                queryResult.prevPageLink,
                                state.documentExpirationTimeMicros,
                                state.tenantLinks);

                        results.prevPageLink = QueryHelper
                                .startStatelessPageService(this,
                                        CloudAccountQueryPageService.SELF_LINK_PREFIX,
                                        pageService,
                                        state.documentExpirationTimeMicros,
                                        failure -> handleError(state, failure));
                    }

                    state.results = results;
                    state.subStage = nextStage;
                    sendSelfPatch(state);
                });
    }

    private void handleError(CloudAccountQueryTaskState state, Throwable ex) {
        logWarning("Failed at sub-stage %s with exception: %s", state.subStage,
                Utils.toString(ex));
        sendSelfFailurePatch(state, ex.getLocalizedMessage());
    }

    /**
     * Validates that the passed in filter parameter has all the required tag filtering parameters set
     * correctly
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

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument d = super.getDocumentTemplate();
        d.documentDescription.serviceRequestRoutes = new HashMap<>();
        Route route = new Route();
        route.action = Action.POST;
        route.description = "Query cloud account information";
        route.requestType = CloudAccountQueryTaskService.class;
        d.documentDescription.serviceRequestRoutes
                .put(route.action, Collections.singletonList(route));
        return d;
    }
}